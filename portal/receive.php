<?php
declare(strict_types=1);

function finish(int $status): never {
    http_response_code($status);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') finish(405);
$token = getenv('BUDGET_PORTAL_TOKEN');
if (!$token || !hash_equals('Bearer ' . $token, $_SERVER['HTTP_AUTHORIZATION'] ?? '')) finish(403);

try {
    $raw = file_get_contents('php://input', false, null, 0, 8193);
    if (strlen($raw) > 8192) finish(413);
    $event = json_decode($raw, true, 8, JSON_THROW_ON_ERROR);
    foreach (['eventId', 'workspaceId', 'claimId'] as $field) {
        if (!is_string($event[$field] ?? null) || !preg_match('/^[a-f0-9-]{36}$/D', $event[$field])) finish(422);
    }
    $sequence = $event['sequenceNo'] ?? 0;
    if (!in_array($sequence, [1, 2], true)) finish(422);
    $types = $sequence === 1 ? ['APPROVAL'] : ['SETTLEMENT', 'CANCELLATION'];
    if (!in_array($event['eventType'] ?? '', $types, true)) finish(422);
    if (!is_int($event['amount'] ?? null) || $event['amount'] < 0 || $event['amount'] > 999999999999) finish(422);
    $db = new PDO(getenv('PORTAL_MYSQL_DSN'), getenv('PORTAL_MYSQL_USER'), getenv('PORTAL_MYSQL_PASSWORD'),
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES => false]);
    $db->beginTransaction();
    $hash = hash('sha256', json_encode([$event['workspaceId'], $event['claimId'], $sequence, $event['eventType'], $event['amount']]));
    $receipt = $db->prepare('INSERT INTO budget_event_receipt(event_id,payload_hash) VALUES (?,?)');
    try {
        $receipt->execute([$event['eventId'], $hash]);
    } catch (PDOException $duplicate) {
        if (($duplicate->errorInfo[1] ?? 0) !== 1062) throw $duplicate;
        $db->rollBack();
        $prior = $db->prepare('SELECT payload_hash FROM budget_event_receipt WHERE event_id=?');
        $prior->execute([$event['eventId']]);
        finish($prior->fetchColumn() === $hash ? 204 : 409);
    }
    $scope = [$event['workspaceId'], $event['claimId']];
    if ($sequence === 1) {
        $insert = $db->prepare('INSERT INTO budget_claim_summary(workspace_id,claim_id,sequence_no,event_type,amount) VALUES (?,?,?,?,?)');
        $insert->execute([...$scope, $sequence, $event['eventType'], $event['amount']]);
    } else {
        $update = $db->prepare('UPDATE budget_claim_summary SET sequence_no=?,event_type=?,amount=? WHERE workspace_id=? AND claim_id=? AND sequence_no=1');
        $update->execute([$sequence, $event['eventType'], $event['amount'], ...$scope]);
        if ($update->rowCount() !== 1) {
            $db->rollBack();
            finish(409);
        }
    }
    $db->commit();
    finish(204);
} catch (JsonException) {
    finish(400);
} catch (Throwable) {
    if (isset($db) && $db->inTransaction()) $db->rollBack();
    finish(503);
}
