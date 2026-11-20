<%@ page contentType="text/html; charset=UTF-8" %>
<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="csrf-token" content="${_csrf.token}"><title>부서예산 · 결재함</title>
<link rel="stylesheet" href="/assets/portal.css"><script defer src="/webjars/jquery/3.7.1/jquery.min.js"></script><script defer src="/assets/portal.js"></script></head>
<body><header><strong>부서예산</strong><span>교육비 · 출장비</span></header><main>
<div class="heading"><div><h1>부서 결재함</h1><p>신청부터 정산까지, 부서 예산을 확인하세요.</p></div><label id="organization-choice">체험 조직<select id="organization"><option value="COMPANY">일반 기업 경영지원팀</option><option value="HOSPITAL">병원 행정부서</option><option value="BANK">은행 경영지원팀</option></select></label><button id="start">개인 데모 시작</button></div>
<p id="notice" role="status" aria-live="polite">데모를 시작하면 독립된 부서 예산 100만 원이 배정됩니다.</p>

<p id="workspace-organization" hidden></p>
<div id="desk" hidden><div class="metrics"><article>배정액<strong id="allocation"></strong></article><article>약정 잔액<strong id="committed"></strong></article><article>누적 집행<strong id="spent"></strong></article><article>가용 예산<strong id="available"></strong></article></div>
<nav aria-label="시연 역할"><label>시연 역할 <select id="role"><option value="EMPLOYEE">직원</option><option value="MANAGER">부서장</option><option value="FINANCE">재무 담당자</option></select></label>
<form id="end-demo" method="post" action="/api/demo/end"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><button>내 체험 종료</button></form></nav>
<section id="entry"><h2>비용 신청</h2><form id="submit"><label>신청명<input name="title" maxlength="100" required value="직무 교육 참가"></label><label>비목<select name="category"><option value="EDUCATION">교육비</option><option value="CONFERENCE">학회 참가비</option><option value="TRAVEL">출장비</option></select></label><label>예정 금액(원)<input type="number" name="estimated" min="1" required value="300000"></label><label>기간(일)<input type="number" name="days" min="1" max="365" required value="1"></label><button>신청 제출</button></form></section>
<div id="estimate-panel" hidden><button type="button" id="estimate">지원 예정액 확인</button><p id="estimate-result" role="status"></p></div>
<details id="policy-panel"><summary>지원 규정 비교 및 발행</summary><div id="policy-editor"></div></details>
<section id="month-end" hidden><h2>월 결산표</h2>
<button id="view-statement">결산표 조회</button>
<div id="statement" aria-live="polite"></div>
<button id="close-period" hidden>이 기간 마감 확정</button></section>
<section><h2>신청 내역</h2><div id="claims"></div></section></div></main></body></html>
