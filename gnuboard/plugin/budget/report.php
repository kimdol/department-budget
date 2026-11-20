<?php
declare(strict_types=1);
if (!defined('_GNUBOARD_')) exit;

function budget_report(PDO $db, string $memberId, string $claim = ''): string {
    $sql = 'SELECT s.claim_id,s.sequence_no,s.event_type,s.amount FROM budget_portal_reader r
        JOIN budget_claim_summary s ON s.workspace_id=r.workspace_id WHERE r.member_id=?';
    $parameters = [$memberId];
    if ($claim !== '') {
        $sql .= ' AND s.claim_id=?';
        $parameters[] = $claim;
    }
    $query = $db->prepare($sql.' ORDER BY s.workspace_id,s.claim_id LIMIT 51');
    $query->execute($parameters);
    $rows = $query->fetchAll(PDO::FETCH_ASSOC);
    if (!$rows) return '<p>열람 가능한 승인·정산 내역이 없습니다.</p>';
    $more = count($rows) > 50;
    if ($more) array_pop($rows);
    $types = ['APPROVAL' => '사전승인','SETTLEMENT' => '정산 확정','CANCELLATION' => '취소'];
    $html = '<table><caption>부서별 포털 수신 내역</caption><thead><tr><th>신청번호</th><th>차수</th><th>상태</th><th>금액</th></tr></thead><tbody>';
    foreach ($rows as $row) {
        $id = htmlspecialchars($row['claim_id'], ENT_QUOTES, 'UTF-8');
        $html .= '<tr><td>'.$id.'</td><td>'.(int)$row['sequence_no'].'</td><td>'
            .($types[$row['event_type']] ?? '미확인').'</td><td>'.number_format((int)$row['amount']).'원</td></tr>';
    }
    return $html.'</tbody></table>'.($more ? '<p>신청번호 순으로 최대 50건을 표시합니다.</p>' : '');
}
