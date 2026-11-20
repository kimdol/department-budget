const statusName = {SUBMITTED:"결재 대기", APPROVED:"약정 완료", IN_REVIEW:"정산 검토", SETTLED:"정산 완료",
  INCREASE_REQUESTED:"증액 승인 대기", NEEDS_RECEIPT:"보완 요청", CANCELLED:"취소 완료"};
const money = value => new Intl.NumberFormat("ko-KR").format(value) + "원";
const notice = message => $("#notice").text(message);
const api = (path, data) => $.ajax({url:path, method:"POST", data, headers:{"X-CSRF-TOKEN":document.querySelector('meta[name="csrf-token"]').content}});
const reportError = error => notice(({403:"현재 역할로 처리할 수 없습니다.",409:"신청 상태가 바뀌었습니다.",422:"금액과 가용 예산을 확인하세요."})[error.status] || "처리 결과를 확인할 수 없습니다. 화면을 새로 열어 확인하세요.");
function render(state) {
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
              " · 집행 증감 " + money(item.spentDelta)).appendTo(documents);
        }).fail(reportError);
      }).appendTo(actions);
    }
    const documents = $("<div>").appendTo(row);
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
    if (state.role === "MANAGER" && claim.status === "SUBMITTED") action("사전승인","/approve",()=>({}));
    if (state.role === "EMPLOYEE" && ["SUBMITTED","APPROVED"].includes(claim.status)) {
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
    if (financeReview) action("정산 확정","/settle",()=>({round:claim.reviewRound}));
    if (managerReview || financeReview) {
      const reason = $("<input>").attr({"aria-label":"보완 사유",maxlength:240,placeholder:"보완 사유"}).appendTo(actions);
      action("보완 요청","/return",()=>({round:claim.reviewRound,reason:reason.val()}));
    }
  }
}
function refresh() { return $.getJSON("/api/workspace").then(render); }
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
}
$("#view-statement").on("click",()=>$.getJSON("/api/statement").done(showStatement).fail(reportError));
$("#close-period").on("click",function() {
  if (!window.confirm("마감하면 이 기간의 신규 신청과 집행을 종료합니다. 확정할까요?")) return;
  $(this).prop("disabled",true);
  api("/api/statement/close",{}).done(showStatement).fail(reportError).always(()=>$(this).prop("disabled",false));
});
$("#start").on("click",()=>api("/api/demo").then(render).fail(reportError));
$("#end-demo").on("submit", event => {
  if (!window.confirm("현재 체험을 종료할까요? 다음 체험은 새 예산 100만 원으로 시작합니다.")) {
    event.preventDefault();
    return;
  }
  sessionStorage.removeItem("pending-claim");
});
$("#role").on("change",event=>api("/api/demo/role",{role:event.target.value}).then(refresh).fail(reportError));
$("#submit").on("submit",event=>{
  event.preventDefault();
  const button = $(event.target).find("button").prop("disabled",true);
  const fields = $(event.target).serialize();
  const previous = JSON.parse(sessionStorage.getItem("pending-claim") || "null");
  const key = previous?.fields === fields ? previous.key : crypto.randomUUID();
  sessionStorage.setItem("pending-claim", JSON.stringify({fields, key}));
  api("/api/claims", fields + "&requestKey=" + encodeURIComponent(key)).then(() => {
    sessionStorage.removeItem("pending-claim");
    return refresh();
  }).fail(reportError).always(()=>button.prop("disabled",false));
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
