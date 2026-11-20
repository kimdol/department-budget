<?php
if (!defined('_GNUBOARD_')) exit;

add_event('tail_sub', static function () {
    global $is_member;
    if (defined('_INDEX_') && _INDEX_ && $is_member) {
        $url = htmlspecialchars(G5_PLUGIN_URL . '/budget/statement.php', ENT_QUOTES, 'UTF-8');
        echo '<nav aria-label="경영지원"><a href="' . $url . '">부서 승인·정산 조회</a></nav>';
    }
});
