<?php
declare(strict_types=1);

require dirname(__DIR__, 2) . '/common.php';
header('Cache-Control: private, no-store');
if (!$is_member || empty($member['mb_id'])) {
    http_response_code(403);
    exit;
}
require __DIR__ . '/report.php';
$claim = $_GET['claim'] ?? '';
if (!is_string($claim) || ($claim !== '' && !preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D', $claim))) {
    http_response_code(422);
    exit('신청번호를 확인하세요.');
}
try {
    $db = new PDO(getenv('PORTAL_MYSQL_DSN'), getenv('PORTAL_READER_USER'), getenv('PORTAL_READER_PASSWORD'),
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES => false]);
    $table = budget_report($db, $member['mb_id'], $claim);
} catch (PDOException $e) {
    http_response_code(503);
    exit('포털 정산 자료를 조회할 수 없습니다.');
}
$g5['title'] = '부서 승인·정산 조회';
include G5_PATH . '/head.php';
echo '<form method="get"><label>신청번호 <input name="claim" maxlength="36" value="'
    .htmlspecialchars($claim, ENT_QUOTES, 'UTF-8').'"></label><button>조회</button></form>';
echo $table;
include G5_PATH . '/tail.php';
