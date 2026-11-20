<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') exit(1);
[$script, $framework, $claim] = $argv;
define('_GNUBOARD_', true);
define('_INDEX_', true);
define('G5_PLUGIN_URL', '/plugin');
require $framework . '/lib/hook.lib.php';
require dirname(__DIR__) . '/gnuboard/extend/budget.extend.php';
require dirname(__DIR__) . '/gnuboard/plugin/budget/report.php';
foreach ([false,true] as $is_member) {
    ob_start();
    run_event('tail_sub');
    $menu = ob_get_clean();
    if (str_contains($menu, '/plugin/budget/statement.php') !== $is_member) throw new RuntimeException('메뉴 접근 오류');
}
$db = new PDO(getenv('PORTAL_MYSQL_DSN'), getenv('PORTAL_READER_USER'), getenv('PORTAL_READER_PASSWORD'),
    [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES => false]);
foreach (['',$claim] as $filter) {
    $own = budget_report($db, 'finance-demo', $filter);
    $other = budget_report($db, 'other-demo', $filter);
    if (!str_contains($own, $claim) || !str_contains($own, '180,000원') || !str_contains($own, '정산 확정')
        || !str_contains($other, '열람 가능한 승인·정산 내역이 없습니다.')) throw new RuntimeException('회원별 정산 조회 오류');
}
echo "GnuBoard hook and scoped settlement report verified\n";
