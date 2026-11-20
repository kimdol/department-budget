<?php
declare(strict_types=1);

header('Cache-Control: no-store');
header('Content-Type: application/json; charset=UTF-8');
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    exit;
}
$token = getenv('BUDGET_PORTAL_TOKEN');
if (!$token || !hash_equals('Bearer ' . $token, $_SERVER['HTTP_AUTHORIZATION'] ?? '')) {
    http_response_code(403);
    exit;
}
foreach (['workspace', 'claim'] as $key) {
    if (!is_string($_GET[$key] ?? null) || !preg_match('/^[0-9a-f-]{36}$/D', $_GET[$key])) {
        http_response_code(422);
        exit;
    }
}
try {
    $db = new PDO(getenv('PORTAL_MYSQL_DSN'), getenv('PORTAL_MYSQL_USER'), getenv('PORTAL_MYSQL_PASSWORD'),
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES => false]);
    $query = $db->prepare('SELECT sequence_no,event_type,amount FROM budget_claim_summary WHERE workspace_id=? AND claim_id=?');
    $query->execute([$_GET['workspace'], $_GET['claim']]);
    $row = $query->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        http_response_code(404);
        exit;
    }
    echo json_encode(['sequence' => (int)$row['sequence_no'], 'event' => $row['event_type'],
        'amount' => (int)$row['amount']], JSON_THROW_ON_ERROR);
} catch (Throwable) {
    http_response_code(503);
}
