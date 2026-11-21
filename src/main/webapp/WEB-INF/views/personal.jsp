<%@ page contentType="text/html; charset=UTF-8" %>
<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>개인 지출 장부 · 부서예산</title><link rel="stylesheet" href="${assetPaths['portal.css']}">
<script defer src="${assetPaths['personal.js']}"></script></head><body>
<header><strong>부서예산</strong><span>개인 지출 장부</span><a href="/">부서 결재함</a></header><main class="ledger">
<h1>내 학습 예산</h1>
<p>기록은 이 브라우저에 저장됩니다. 브라우저의 사이트 데이터를 지우면 장부도 삭제됩니다.</p>
<div class="metrics"><article>개인 예산<strong id="budget-total"></strong></article>
<article>누적 본인 부담<strong id="paid-total"></strong></article><article>잔여 예산<strong id="remaining"></strong></article></div>
<div class="ledger-layout"><div>
<section><h2>지출 기록</h2><form id="expense"><label>지출일<input type="date" name="date" required></label>
<label>분류<select name="category"><option>자격시험</option><option>교육</option><option>도서</option></select></label>
<label>항목명<input name="title" maxlength="100" required></label>
<label>지출액(원)<input name="amount" type="number" min="1" max="999999999999" required></label>
<label>실제 지원액(원)<input name="support" type="number" min="0" max="999999999999" value="0" required></label>
<button class="primary">지출 저장</button></form></section>
<section class="expense-book"><h2>지출 내역</h2><p id="empty-expenses" hidden>등록한 지출이 없습니다.</p><ul id="expenses"></ul></section>
</div><aside><section><h2>예산 설정</h2><form id="personal-budget"><label>개인 예산(원)<input name="budget" type="number" min="0" max="999999999999" required></label><button>예산 저장</button></form></section>
<section><h2>다음 교육 예상 비용</h2><form id="projection">
<label>교육비(원)<input name="cost" type="number" min="1" max="999999999999" required></label>
<label>예상 지원율(%)<input name="rate" type="number" min="0" max="100" value="80" required></label>
<label>행사당 한도(원)<input name="cap" type="number" min="0" max="999999999999" value="200000" required></label>
<button>예상 잔액 계산</button></form><p id="forecast" role="status"></p></section></aside></div>
<p id="personal-status" role="status"></p></main></body></html>
