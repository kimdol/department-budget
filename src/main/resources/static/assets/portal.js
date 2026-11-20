const statusName = {SUBMITTED:"결재 대기", FINANCE_PENDING:"재무 사전승인 대기", APPROVED:"약정 완료", IN_REVIEW:"정산 검토", SETTLED:"정산 완료",
  INCREASE_REQUESTED:"증액 승인 대기", NEEDS_RECEIPT:"보완 요청", CANCELLED:"취소 완료"};
const money = value => new Intl.NumberFormat("ko-KR").format(value) + "원";
const notice = message => $("#notice").text(message);
const api = (path, data) => $.ajax({url:path, method:"POST", data, headers:{"X-CSRF-TOKEN":document.querySelector('meta[name="csrf-token"]').content}});
const reportError = error => notice(({403:"현재 역할로 처리할 수 없습니다.",409:"신청 상태가 바뀌었습니다.",422:"금액과 가용 예산을 확인하세요."})[error.status] || "처리 결과를 확인할 수 없습니다. 화면을 새로 열어 확인하세요.");
const cursors = [null];
let nextCursor = null;
function render(state) {
  nextCursor = state.hasMore ? state.claims[state.claims.length-1].inboxOrder : null;
  $("#older").prop("hidden",nextCursor === null);
  $("#newer").prop("hidden",cursors.length <= 1);
  $("#organization-choice").prop("hidden",true);
  $("#workspace-organization").prop("hidden",false).text(({COMPANY:"일반 기업 경영지원팀",HOSPITAL:"병원 행정부서",BANK:"은행 경영지원팀"})[state.organization]);
  clearEstimate();
  $("#estimate-panel").prop("hidden",state.role !== "EMPLOYEE");
  const nextSteps = {EMPLOYEE:"교육비를 신청한 뒤 시연 역할을 부서장으로 바꾸세요. 약정 완료 후에는 실제 지출액으로 정산을 신청할 수 있습니다.",
    MANAGER:"신청을 사전승인하면 약정액이 반영됩니다. 직원 역할에서 실제 지출을 제출하세요.",
    FINANCE:"정산을 확정한 뒤 전표와 월 결산표에서 집행액을 확인하세요."};
  notice(nextSteps[state.role]);
  $("#month-end").prop("hidden", state.role !== "FINANCE");
  $("#statement").empty();
  $("#close-period").prop("hidden",true);
  $("#start").prop("hidden", true);
  $("#desk").prop("hidden", false);
  for (const key of ["allocation","committed","spent","available"]) $("#" + key).text(money(state.balance[key]));
  $("#role").val(state.role);
  document.dispatchEvent(new Event("budget-session"));
  $("#entry").prop("hidden", state.role !== "EMPLOYEE");
  const list = $("#claims").empty();
  if (!state.claims.length) list.text("아직 신청한 비용이 없습니다.");
  for (const claim of state.claims) {
    const row = $("<article>").addClass("claim").appendTo(list);
    $("<strong>").text(claim.title + " · " + statusName[claim.status]).appendTo(row);
    $("<small>").text(claim.claimId).appendTo(row);
    if (claim.reviewNote) $("<p>").text("보완 사유 · " + claim.reviewNote).appendTo(row);
    $("<p>").text("예정 " + money(claim.estimated) + " / 승인 " + money(claim.approved) + " / 정산 " + money(claim.settled)).appendTo(row);
    const actions = $("<div>").addClass("actions").appendTo(row);
    const path = "/api/claims/" + encodeURIComponent(claim.claimId);
    if (state.role === "FINANCE") {
      $("<button>").text("전표 조회").on("click", () => {
        $.getJSON(path + "/postings").done(entries => {
          documents.empty();
          if (!entries.length) documents.text("기록된 전표가 없습니다.");
          for (const item of entries) $("<p>").text(item.kind + " · 약정 증감 " + money(item.committedDelta) +
              " · 집행 증감 " + money(item.spentDelta) + " · " + item.periodKey).appendTo(documents);
          const original = entries.find(item => item.kind === "SETTLEMENT");
          if (!original) return;
          $("<button>").text("원래 기간 결산표").on("click", () =>
            $.getJSON("/api/statements/" + original.periodKey).done(report =>
              $("<p>").text(report.period + " 마감 당시 가용 예산 " + money(report.balance.available)).appendTo(documents)
            ).fail(() => notice("해당 기간의 확정 결산표가 없습니다."))).appendTo(documents);
          $.getJSON("/api/statement").done(report => {
            const remaining = entries.reduce((sum,item) => sum + item.spentDelta,0);
            if (report.state !== "OPEN" || report.period <= original.periodKey || remaining <= 0) return;
            const round = entries.filter(item => item.originalKind === "SETTLEMENT").length + 1;
            const form = $("<form>").appendTo(documents);
            const label = $("<label>").text("환입 금액(원) · 잔여 " + money(remaining)).appendTo(form);
            const amount = $("<input>").attr({type:"number",required:true,min:1,max:remaining}).appendTo(label);
            $("<button>").text("정정 전표 발행").appendTo(form);
            form.on("submit",event => {
              event.preventDefault();
              if (window.confirm(money(amount.val()) + "을 " + report.period + "에 환입할까요?"))
                api(path+"/reverse",{amount:amount.val(),round,period:report.period}).then(refresh).fail(reportError);
            });
          }).fail(reportError);
        }).fail(reportError);
      }).appendTo(actions);
    }
    const documents = $("<div>").appendTo(row);
    if (state.role === "FINANCE" && state.portalEnabled) $("<button>").text("사내 포털 수신 결과").on("click",() =>
      $.getJSON(path+"/portal-receipt").done(receipt => {
        if (!receipt) {documents.text("아직 포털에 도착한 결과가 없습니다."); return;}
        const kind = ({APPROVAL:"사전승인",SETTLEMENT:"정산 확정",CANCELLATION:"취소"})[receipt.event];
        documents.text("포털 수신 차수 " + receipt.sequence + " · " + kind + " · " + money(receipt.amount));
      }).fail(()=>documents.text("포털에서 결과를 조회할 수 없습니다. 잠시 후 다시 조회하세요."))).appendTo(actions);
    $("<button>").text("신청 당시 계산 근거").on("click",() =>
      $.getJSON(path+"/calculation").done(calc => {
        documents.empty();
        $("<p>").text("발행 버전 " + calc.generation + " · 평가기 " + calc.evaluatorVersion + " · " + calc.formula).appendTo(documents);
        $("<p>").text(calc.days + "일 · 예정 " + money(calc.estimated) + " → 인정 " + money(calc.initialSupport)).appendTo(documents);
        if (calc.actual !== null) $("<p>").text("실제 지출 " + money(calc.actual) + " → 인정 " + money(calc.actualSupport)).appendTo(documents);
      }).fail(reportError)).appendTo(actions);
    $("<button>").text("증빙 요건 확인").on("click",() =>
      $.getJSON(path + "/evidence/assessment").done(result => {
        documents.text(result.issues.length ? result.issues.join(" ") : "필요한 증빙을 모두 제출했습니다.");
        const detail = $("<details>").appendTo(documents);
        $("<summary>").text("판정 근거").appendTo(detail);
        for (const rule of [result.receipt,result.attendance]) {
          for (const step of rule.evaluated) $("<p>").text(step.name + " · " +
            ({PASS:"충족",FAIL:"미충족",MISSING:"자료 부족"})[step.verdict]).appendTo(detail);
          for (const name of rule.skipped) $("<p>").text(name + " · 앞선 조건으로 판정되어 생략").appendTo(detail);
        }
      }).fail(reportError)).appendTo(actions);
    $("<button>").text("증빙 조회").on("click", () => {
      $.getJSON(path + "/evidence").done(items => {
        documents.empty();
        if (!items.length) documents.text("제출한 증빙이 없습니다.");
        for (const item of items) {
          $("<a>").attr("href",path+"/evidence/"+encodeURIComponent(item.evidenceType))
            .text((item.evidenceType==="RECEIPT" ? "지출 영수증" : "참석 확인서")+" · "+money(item.receiptAmount)).appendTo(documents);
        }
      }).fail(reportError);
    }).appendTo(actions);
    const action = (label, route, data) => $("<button>").text(label).on("click", function() {
      $(this).prop("disabled", true);
      api(path + route, data()).then(refresh).fail(error => {reportError(error); $(this).prop("disabled",false);});
    }).appendTo(actions);
    if (state.role === "MANAGER" && claim.status === "SUBMITTED") action(claim.approvalRoute === "MANAGER_FINANCE" ? "재무로 결재 요청" : "사전승인","/approve",()=>({}));
    if (state.role === "FINANCE" && claim.status === "FINANCE_PENDING") action("최종 사전승인","/finance-approval",()=>({}));
    if (state.role === "EMPLOYEE" && ["SUBMITTED","FINANCE_PENDING","APPROVED"].includes(claim.status)) {
      const approved = claim.status === "APPROVED";
      action(approved ? "약정 취소" : "신청 철회", "/cancel", () => ({releaseApproval:approved}));
    }
    if (state.role === "EMPLOYEE" && ["APPROVED","NEEDS_RECEIPT"].includes(claim.status)) {
      const amount = $("<input>").attr({type:"number",min:1,"aria-label":"실제 지출액(원)"}).val(225000).appendTo(actions);
      const type = $("<select>").attr("aria-label","증빙 종류").appendTo(actions);
      $("<option>").val("RECEIPT").text("지출 영수증").appendTo(type);
      $("<option>").val("ATTENDANCE").text("참석 확인서").appendTo(type);
      action("합성 증빙 첨부","/evidence",()=>({type:type.val(),amount:amount.val()}));
      action("정산 신청","/review",()=>({actual:amount.val(),round:claim.reviewRound}));
    }
    const managerReview = state.role === "MANAGER" && claim.status === "INCREASE_REQUESTED";
    const financeReview = state.role === "FINANCE" && claim.status === "IN_REVIEW";
    if (managerReview) action("증액 승인","/increase",()=>({round:claim.reviewRound}));
    if (financeReview) $("<button>").text("정산 확정").on("click",function() {
      const button = $(this).prop("disabled",true);
      api(path+"/settle",{round:claim.reviewRound}).then(refresh).fail(error => {
        if (error.status === 422) $.getJSON(path+"/evidence/issues").done(issues =>
          issues.length ? documents.text(issues.join(" ")) : reportError(error)).fail(reportError);
        else reportError(error);
        button.prop("disabled",false);
      });
    }).appendTo(actions);
    if (managerReview || financeReview) {
      const reason = $("<input>").attr({"aria-label":"보완 사유",maxlength:240,placeholder:"보완 사유"}).appendTo(actions);
      action("보완 요청","/return",()=>({round:claim.reviewRound,reason:reason.val()}));
    }
  }
}
function refresh() {
  const before = cursors[cursors.length-1];
  return $.getJSON("/api/workspace",before === null ? {} : {before}).then(render);
}
function changePage(older) {
  const previous = cursors.slice();
  if (older && nextCursor !== null) cursors.push(nextCursor);
  else if (!older && cursors.length > 1) cursors.pop();
  else return;
  $("#older,#newer").prop("disabled",true);
  refresh().fail(error => {cursors.splice(0,cursors.length,...previous); reportError(error);})
    .always(()=>$("#older,#newer").prop("disabled",false));
}
$("#older").on("click",()=>changePage(true));
$("#newer").on("click",()=>changePage(false));
function showStatement(report) {
  if ($("#role").val() !== "FINANCE") return;
  const box = $("#statement").empty();
  $("<strong>").text(report.period + " · " + ({OPEN:"마감 전",CLOSING:"마감 진행 중",CLOSED:"마감 완료"})[report.state]).appendTo(box);
  for (const line of report.lines) {
    const label = ({EDUCATION:"교육비",TRAVEL:"출장비",CONFERENCE:"학회 참가비"})[line.category];
    $("<p>").text(label + " · " + line.claimCount + "건 · " + money(line.spent)).appendTo(box);
  }
  $("<p>").text("누적 집행 " + money(report.balance.spent) + " / 가용 예산 " + money(report.balance.available)).appendTo(box);
  if (report.pending.length) $("<p>").text("진행 중인 신청 " + report.pending.join(", ")).appendTo(box);
  $("#close-period").prop("hidden", report.state === "CLOSED" || report.pending.length > 0);
  if (report.state === "CLOSED") {
    const details = $("<details>").appendTo(box);
    $("<summary>").text("구 결산표 CSV 대조").appendTo(details);
    const label = $("<label>").text("비목별 월 집계 CSV").appendTo(details);
    const csv = $("<textarea>").attr({rows:4,maxlength:2048,placeholder:"period,category,count,spent\n"+report.period+",EDUCATION,1,180000"}).appendTo(label);
    const result = $("<div>").attr("role","status");
    $("<button>").text("확정 결산표와 비교").on("click",function() {
      const button = $(this).prop("disabled",true);
      const submitted = csv.val();
      api("/api/statements/"+report.period+"/compare",{csv:submitted}).done(rows=>{
        if (csv.val() !== submitted) return;
        result.text(rows.length ? "차이 "+rows.length+"건" : "모든 비목의 건수와 집행액이 일치합니다.");
        const values = (count,spent) => count == null ? "비목 없음" : count+"건 · "+(spent == null ? "금액 누락" : money(spent));
        for (const row of rows) $("<p>").text(row.category+" · 구 결산표 "+values(row.legacyCount,row.legacySpent)
          +" / 확정 결산표 "+values(row.ledgerCount,row.ledgerSpent)).appendTo(result);
      }).fail(reportError).always(()=>button.prop("disabled",false));
    }).appendTo(details);
    csv.on("input",()=>result.empty());
    result.appendTo(details);
    $("<button>").text("다음 달 열기").on("click", () =>
      api("/api/statements/"+report.period+"/advance",{}).done(showStatement).fail(reportError)).appendTo(box);
  }
}
$("#view-statement").on("click",()=>$.getJSON("/api/statement").done(showStatement).fail(reportError));
$("#close-period").on("click",function() {
  if (!window.confirm("마감하면 이 기간의 신규 신청과 집행을 종료합니다. 확정할까요?")) return;
  $(this).prop("disabled",true);
  api("/api/statement/close",{}).done(showStatement).fail(reportError).always(()=>$(this).prop("disabled",false));
});
$("#start").on("click",()=>api("/api/demo",{organization:$("#organization").val()}).then(render).fail(reportError));
$("#end-demo").on("submit", event => {
  if (!window.confirm("현재 체험을 종료할까요? 다음 체험은 새 예산 100만 원으로 시작합니다.")) {
    event.preventDefault();
    return;
  }
  sessionStorage.removeItem("pending-claim");
});
$("#role").on("change",event=>api("/api/demo/role",{role:event.target.value}).then(refresh).fail(reportError));
let estimateRevision = 0;
let expectedGeneration = null;
function clearEstimate() {
  estimateRevision++;
  expectedGeneration = null;
  $("#estimate-result").empty();
}
$("#submit").on("input change",clearEstimate);
$("#estimate").on("click", () => {
  if (!document.querySelector("#submit").reportValidity()) return;
  clearEstimate();
  const revision = estimateRevision;
  const data = $("#submit").find('[name="category"],[name="estimated"],[name="days"]').serialize();
  $.getJSON("/api/claims/estimate",data).done(result => {
    if (revision !== estimateRevision) return;
    expectedGeneration = result.generation;
    $("#estimate-result").text("지원 예정 " + money(result.supported) + " · 본인 부담 " + money(result.employeeShare) +
      " · 결재 경로 " + (result.route === "MANAGER_FINANCE" ? "부서장 → 재무" : "부서장") +
      " · 적용 규정 " + result.generation + " · " + result.formula);
  }).fail(error => {if (revision === estimateRevision) reportError(error);});
});
$("#submit").on("submit",event=>{
  event.preventDefault();
  const button = $(event.target).find("button").prop("disabled",true);
  const fields = $(event.target).serialize();
  const previous = JSON.parse(sessionStorage.getItem("pending-claim") || "null");
  const key = previous?.fields === fields ? previous.key : crypto.randomUUID();
  sessionStorage.setItem("pending-claim", JSON.stringify({fields, key}));
  const generation = expectedGeneration === null ? "" : "&expectedGeneration=" + expectedGeneration;
  api("/api/claims", fields + generation + "&requestKey=" + encodeURIComponent(key)).then(() => {
    sessionStorage.removeItem("pending-claim");
    cursors.splice(0,cursors.length,null);
    return refresh();
  }).fail(error => {
    if (error.status === 409 && generation) {
      notice("지원 규정이 변경되었습니다. 지원 예정액을 다시 확인하세요.");
    } else reportError(error);
  }).always(()=>button.prop("disabled",false));
});
$.getJSON("/api/workspace").done(render);
document.getElementById("policy-panel").addEventListener("toggle", function() {
  if (this.open && !document.getElementById("policy-module")) {
    const script = document.createElement("script");
    script.id = "policy-module";
    script.src = "/assets/policy-editor.js";
    script.onerror = () => {script.remove(); notice("규정 편집기를 불러오지 못했습니다.");};
    document.head.append(script);
  }
});
