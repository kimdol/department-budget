const statusName = {SUBMITTED:"결재 대기", APPROVED:"약정 완료", IN_REVIEW:"정산 검토", SETTLED:"정산 완료"};
const money = value => new Intl.NumberFormat("ko-KR").format(value) + "원";
const notice = message => $("#notice").text(message);
const api = (path, data) => $.ajax({url:path, method:"POST", data, headers:{"X-CSRF-TOKEN":document.querySelector('meta[name="csrf-token"]').content}});
const reportError = error => notice(({403:"현재 역할로 처리할 수 없습니다.",409:"신청 상태가 바뀌었습니다.",422:"금액과 가용 예산을 확인하세요."})[error.status] || "처리 결과를 확인할 수 없습니다. 화면을 새로 열어 확인하세요.");
function render(state) {
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
    $("<p>").text("예정 " + money(claim.estimated) + " / 승인 " + money(claim.approved) + " / 정산 " + money(claim.settled)).appendTo(row);
    const actions = $("<div>").addClass("actions").appendTo(row);
    const path = "/api/claims/" + encodeURIComponent(claim.claimId);
    const action = (label, route, data) => $("<button>").text(label).on("click", function() {
      $(this).prop("disabled", true);
      api(path + route, data()).then(refresh).fail(error => {reportError(error); $(this).prop("disabled",false);});
    }).appendTo(actions);
    if (state.role === "MANAGER" && claim.status === "SUBMITTED") action("사전승인","/approve",()=>({}));
    if (state.role === "EMPLOYEE" && claim.status === "APPROVED") {
      const amount = $("<input>").attr({type:"number",min:1,"aria-label":"실제 지출액(원)"}).val(225000).appendTo(actions);
      action("정산 신청","/review",()=>({actual:amount.val()}));
    }
    if (state.role === "FINANCE" && claim.status === "IN_REVIEW") action("정산 확정","/settle",()=>({}));
  }
}
function refresh() { return $.getJSON("/api/workspace").then(render); }
$("#start").on("click",()=>api("/api/demo").then(render).fail(reportError));
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
