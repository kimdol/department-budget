const statusName = {SUBMITTED:"결재 대기", FINANCE_PENDING:"재무 사전승인 대기", APPROVED:"약정 완료", IN_REVIEW:"정산 검토", SETTLED:"정산 완료",
  INCREASE_REQUESTED:"증액 승인 대기", NEEDS_RECEIPT:"보완 요청", CANCELLED:"취소 완료"};
const pendingAction = {
  SUBMITTED:"부서장 · 사전 결재", FINANCE_PENDING:"재무 담당자 · 최종 사전승인",
  APPROVED:"신청 직원 · 실제 지출 제출", IN_REVIEW:"재무 담당자 · 정산 검토",
  INCREASE_REQUESTED:"부서장 · 증액 검토", NEEDS_RECEIPT:"신청 직원 · 보완 후 재제출"
};
const money = value => new Intl.NumberFormat("ko-KR").format(value) + "원";
const notice = message => $("#notice").text(message);
const api = (path, data) => $.ajax({url:path, method:"POST", data, headers:{"X-CSRF-TOKEN":document.querySelector('meta[name="csrf-token"]').content}});
const errorMessage = error => ({403:"현재 역할로 처리할 수 없습니다.",409:"신청 상태가 바뀌었습니다.",422:"금액과 가용 예산을 확인하세요."})[error.status] || "처리 결과를 확인할 수 없습니다. 화면을 새로 열어 확인하세요.";
const reportError = error => notice(errorMessage(error));
const cursors = [null];
const claimFilter = document.getElementById("claim-filter");
for (const [value,label] of Object.entries(statusName)) claimFilter.add(new Option(label,value));
let categories = {};
let catalogRequest;
function loadCategories() {
  if (!catalogRequest) {
    catalogRequest = $.getJSON("/api/catalog/expense-categories").then(labels=>{
      categories = labels;
      const select = document.querySelector("#submit select[name=category]");
      select.replaceChildren(...Object.entries(labels).map(([code,label])=>new Option(label,code)));
    }).fail(()=>{catalogRequest = null;});
  }
  return catalogRequest;
}
const department = new URLSearchParams(location.search).get("department") || "PRIMARY";
function departmentUrl(path) {
  if (department === "PRIMARY") return path;
  const url = new URL(path,location.origin);
  url.searchParams.set("department",department);
  return url.pathname + url.search;
}
$.ajaxPrefilter(options=>{
  if (options.url.startsWith("/api/") && !options.url.startsWith("/api/catalog/")) options.url = departmentUrl(options.url);
});
$("#department").on("change",event=>{
  const url = new URL(location.href);
  url.searchParams.set("department",event.target.value);
  location.assign(url);
});
let nextCursor = null;
let inboxReady = false;
const inbox = window.employeeInbox = {
  state:null,labels:statusName,nextSteps:pendingAction,filter:claimFilter,older:false,newer:false,
  page:changePage,refresh,filterBy(value) {
    claimFilter.value = value;
    return changeFilter();
  }
};
document.addEventListener("employee-inbox-ready",()=>{
  inboxReady = true;
  if (inbox.state) render(inbox.state);
});
function render(state) {
  claimFilter.dataset.applied = claimFilter.value;
  $("#department-choice").prop("hidden",false);
  const departmentNames = {BANK:["본부 경영지원팀","지점 경영지원팀"],HOSPITAL:["총무팀","교육수련팀"],COMPANY:["인사팀","총무팀"]}[state.organization];
  $("#department option").each((index,option)=>$(option).text(departmentNames[index]));
  $("#department").val(department);
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
  $("#branch-allocation").prop("hidden",state.organization !== "BANK" || department !== "PRIMARY" || state.role !== "FINANCE");
  $("#branch-report").empty();
  $("#statement").empty();
  $("#portal-failures").empty();
  $("#view-failures").prop("hidden", !state.portalEnabled || state.role !== "FINANCE");
  $("#close-period").prop("hidden",true);
  $("#start,#employee-start").prop("hidden", true);
  $("#desk").prop("hidden", false);
  for (const key of ["allocation","committed","spent","available"]) $("#" + key).text(money(state.balance[key]));
  $("#role").val(state.role);
  const delegated = state.persona === "ACTING_MANAGER";
  $("#member-name").text("시연 구성원 "+state.memberId+(delegated ? " · 본인 신청 결재 제한" : ""));
  $("#delegate").prop("hidden",state.role !== "MANAGER").data("next",delegated ? "MANAGER" : "ACTING_MANAGER")
    .text(delegated ? "부서장으로 복귀" : "신청 직원의 부서장 대행 체험");
  $("#policy-panel").prop("hidden",delegated);
  if (delegated) $("#policy-panel").prop("open",false);
  document.dispatchEvent(new Event("budget-session"));
  $("#entry").prop("hidden", state.role !== "EMPLOYEE");
  const list = $("#claims");
  const employeeMode = state.role === "EMPLOYEE" && inboxReady;
  Object.assign(inbox,{state,older:nextCursor !== null,newer:cursors.length > 1});
  $("#employee-inbox").prop("hidden",!employeeMode);
  $("#legacy-inbox").prop("hidden",employeeMode);
  document.dispatchEvent(new Event("employee-inbox-update"));
  if (employeeMode) {
    list.empty();
    const action = state.claims.find(claim=>claim.status === "NEEDS_RECEIPT");
    if (action) notice("보완을 요청받은 신청이 있습니다. 내 신청함에서 사유를 확인하세요.");
    else if (state.claims.length) notice("내 신청함에서 진행 상태와 다음 담당자를 확인하세요.");
    return;
  }
  if (state.role === "EMPLOYEE" && new URLSearchParams(location.search).get("view")==="employee") loadEmployee();
  const editingAmounts = new Map();
  for (const input of list[0].querySelectorAll("input[data-expense-round]")) {
    editingAmounts.set(input.dataset.expenseRound,input.value);
  }
  list.empty();
  if (!state.claims.length) list.text(claimFilter.value ? "해당 상태의 신청이 없습니다." : "아직 신청한 비용이 없습니다.");
  for (const claim of state.claims) {
    const row = $("<article>").addClass("claim").appendTo(list);
    const headingId = "claim-title-" + claim.claimId;
    $("<strong>").attr({id:headingId,tabindex:-1}).text(claim.title + " · " + statusName[claim.status]).appendTo(row);
    row.attr("aria-labelledby",headingId);
    const refreshClaim = () => refresh().then(()=>(document.getElementById(headingId) || claimFilter).focus());
    const reference = $("<small>").text("신청번호 ").appendTo(row);
    $("<span>").addClass("claim-number").text(claim.claimId).appendTo(reference);
    if (pendingAction[claim.status]) $("<p>").text("다음 담당자 · " + pendingAction[claim.status]).appendTo(row);
    if (claim.reviewNote) $("<p>").text("보완 사유 · " + claim.reviewNote).appendTo(row);
    $("<p>").text("예정 " + money(claim.estimated) + " / 승인 " + money(claim.approved) + " / 정산 " + money(claim.settled)).appendTo(row);
    if (claim.actual !== null) {
      let detail = "제출한 실비 " + money(claim.actual);
      if (claim.status === "SETTLED") detail += " · 정산 당시 본인 부담 " + money(claim.actual-claim.settled) +
        " · 예산으로 돌아간 약정 차액 " + money(claim.approved-claim.settled);
      $("<p>").text(detail).appendTo(row);
    }
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
    $("<button>").text("신청 버전 조회").on("click",()=>
      $.getJSON(path+"/versions").done(versions=>{
        documents.empty();
        $("<strong>").text("최초 신청번호 "+versions[0].seriesId).appendTo(documents);
        for (const v of versions) $("<p>").text("버전 "+v.versionNo+" · "+v.title+" · "+money(v.estimated)+" · "+
          v.days+"일 · "+statusName[v.status]+" · "+v.claimId).appendTo(documents);
      }).fail(reportError)).appendTo(actions);
    const historyButton = $("<button>").text("결재 이력").on("click", () =>
      $.getJSON(path+"/history").done(events => {
        const names = {SUBMIT:"신청 접수",FORWARD_FINANCE:"재무로 결재 요청",FINAL_APPROVAL:"최종 사전승인",
          SUBMIT_ACTUAL:"실제 지출 제출",APPROVE_INCREASE:"증액 승인",RETURN:"보완 요청",SETTLE:"정산 확정",
          WITHDRAW:"신청 철회",CANCEL_APPROVAL:"약정 취소",REPLACED:"비목 변경으로 철회",REPLACES:"비목 변경 재신청",
          REVISED:"이전 버전 승인 해제",REVISION:"변경 버전 재상신"};
        const roles = {EMPLOYEE:"직원",MANAGER:"부서장",FINANCE:"재무"};
        documents.empty();
        if (!events.length) documents.text("저장된 결재 이력이 없습니다.");
        for (const event of events) {
          const phase = event.reviewRound ? "정산 "+event.reviewRound+"차" : "사전 결재";
          $("<p>").text([event.recordedAt.replace("T"," "),phase,roles[event.actorRole],
            names[event.eventType] || event.eventType,money(event.amount),event.note,event.actorMemberId].filter(Boolean).join(" · ")).appendTo(documents);
        }
        $("<button>").text("결재 이력 닫기").on("click",()=>{
          documents.empty();
          historyButton.trigger("focus");
        }).appendTo(documents).trigger("focus");
      }).fail(reportError)).appendTo(actions);
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
          const labels = {eligible_cost:"인정 대상 비용",event_cap:"행사 한도",support_rate:"지원율",days:"기간",
            daily_cap:"일 한도",literal:"상수","*":"곱셈","+":"덧셈","-":"뺄셈","/":"나눗셈",min:"작은 값",max:"큰 값",floor:"내림"};
          for (const [title,steps] of [["예정액 계산 과정",calc.initialSteps],["실비 계산 과정",calc.actualSteps]]) {
            if (!steps.length) continue;
            const detail = $("<details>").appendTo(documents);
            $("<summary>").text(title).appendTo(detail);
            const list = $("<ol>").appendTo(detail);
            for (const step of steps) $("<li>").text(labels[step.operation]+" · "+step.value).appendTo(list);
          }
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
          $("<a>").attr("href",departmentUrl(path+"/evidence/"+encodeURIComponent(item.evidenceType)))
            .text((item.evidenceType==="RECEIPT" ? "지출 영수증" : "참석 확인서")+" · "+money(item.receiptAmount)).appendTo(documents);
        }
      }).fail(reportError);
    }).appendTo(actions);
    const send = (button, route, data) => {
      button.prop("disabled",true);
      row.prop("inert",true).attr("aria-busy","true");
      api(path + route,data).then(refreshClaim).fail(error=>{
        row.prop("inert",false).removeAttr("aria-busy");
        reportError(error);
        button.prop("disabled",false).trigger("focus");
      });
    };
    const action = (label, route, data, container = actions) => $("<button>").text(label).on("click", function() {
      send($(this),route,data());
    }).appendTo(container);
    if (state.role === "MANAGER" && claim.status === "SUBMITTED") action(claim.approvalRoute === "MANAGER_FINANCE" ? "재무로 결재 요청" : "사전승인","/approve",()=>({}));
    if (state.role === "FINANCE" && claim.status === "FINANCE_PENDING") action("최종 사전승인","/finance-approval",()=>({}));
    if (state.role === "EMPLOYEE" && ["SUBMITTED","FINANCE_PENDING","APPROVED"].includes(claim.status)) {
      const approved = claim.status === "APPROVED";
      action(approved ? "약정 취소" : "신청 철회", "/cancel", () => ({releaseApproval:approved}));
    }
    if (state.role === "EMPLOYEE" && ["APPROVED","NEEDS_RECEIPT"].includes(claim.status)) {
      const settlement = $("<fieldset>").addClass("settlement-entry").appendTo(row);
      $("<legend>").text("정산 제출").appendTo(settlement);
      if (claim.status === "NEEDS_RECEIPT") {
        $("<p>").text("증빙 종류를 선택해 ‘합성 증빙 첨부’를 누른 뒤 ‘정산 신청’으로 다시 제출하세요.").appendTo(settlement);
      }
      const settlementActions = $("<div>").addClass("actions").appendTo(settlement);
      const roundKey = claim.claimId + "/" + claim.reviewRound;
      const amountLabel = $("<label>").text("실제 지출액(원)").appendTo(settlementActions);
      const amount = $("<input>").attr({type:"number",min:1,"data-expense-round":roundKey})
        .val(editingAmounts.get(roundKey) ?? claim.actual ?? 225000).appendTo(amountLabel);
      const evidenceLabel = $("<label>").text("증빙 종류").appendTo(settlementActions);
      const type = $("<select>").appendTo(evidenceLabel);
      $("<option>").val("RECEIPT").text("지출 영수증").appendTo(type);
      $("<option>").val("ATTENDANCE").text("참석 확인서").appendTo(type);
      action("합성 증빙 첨부","/evidence",()=>({type:type.val(),amount:amount.val()}),settlementActions);
      action("정산 신청","/review",()=>({actual:amount.val(),round:claim.reviewRound}),settlementActions);
    }
    const managerReview = state.role === "MANAGER" && claim.status === "INCREASE_REQUESTED";
    if (state.role === "EMPLOYEE" && claim.status === "NEEDS_RECEIPT") {
      const details = $("<details>").appendTo(row);
      $("<summary>").text("신청 내용 변경").appendTo(details);
      $("<p>").text("기존 약정을 해제하고 사전승인을 다시 요청합니다. 비목을 유지하면 같은 신청의 다음 버전으로 기록됩니다.").appendTo(details);
      const form = $("<form>").appendTo(details);
      const category = $("<select>").appendTo($("<label>").text("변경 비목").appendTo(form));
      for (const [code,label] of Object.entries(categories)) {
        category.append(new Option(label,code));
      }
      category.val(claim.category);
      const title = $("<input>").attr({required:true,maxlength:100}).val(claim.title)
        .appendTo($("<label>").text("변경 신청명").appendTo(form));
      const estimated = $("<input>").attr({type:"number",min:1,required:true}).val(claim.estimated)
        .appendTo($("<label>").text("변경 예정 금액(원)").appendTo(form));
      const days = $("<input>").attr({type:"number",min:1,max:365,required:true})
        .appendTo($("<label>").text("변경 기간(일)").appendTo(form));
      let periodEdited = false;
      days.on("input",()=>{ periodEdited = true; });
      details.on("toggle",()=>{
        if (details.prop("open") && !periodEdited && !days.val()) {
          $.getJSON(path+"/calculation").done(calculation=>{
            if (!periodEdited) days.val(calculation.days);
          }).fail(reportError);
        }
      });
      const button = $("<button>").text("변경 버전 재상신").appendTo(form);
      category.on("change",()=>{
        const same = category.val() === claim.category;
        title.prop("disabled",!same);
        button.text(same ? "변경 버전 재상신" : "원신청 철회 후 재신청");
      });
      form.on("submit",event=>{
        event.preventDefault();
        const data = {round:claim.reviewRound,category:category.val(),estimated:estimated.val(),days:days.val()};
        const same = data.category === claim.category;
        if (same) data.title = title.val();
        const storageKey = "replace-"+claim.claimId;
        const fields = JSON.stringify(data);
        const previous = JSON.parse(sessionStorage.getItem(storageKey) || "null");
        const key = previous?.fields === fields ? previous.key : crypto.randomUUID();
        sessionStorage.setItem(storageKey,JSON.stringify({fields,key}));
        button.prop("disabled",true);
        api(path+(same ? "/revise" : "/replace-category"),{...data,requestKey:key}).then(result=>{
          sessionStorage.removeItem(storageKey);
          cursors.splice(0,cursors.length,null);
          return refresh().then(()=>document.getElementById("claim-title-"+result.claimId)?.focus());
        }).fail(error=>{reportError(error); button.prop("disabled",false);});
      });
    }
    const financeReview = state.role === "FINANCE" && claim.status === "IN_REVIEW";
    if (managerReview) action("증액 승인","/increase",()=>({round:claim.reviewRound}));
    if (financeReview) $("<button>").text("정산 확정").on("click",function() {
      const button = $(this).prop("disabled",true);
      api(path+"/settle",{round:claim.reviewRound}).then(refreshClaim).fail(error => {
        if (error.status === 422) $.getJSON(path+"/evidence/issues").done(issues =>
          issues.length ? documents.text(issues.join(" ")) : reportError(error)).fail(reportError);
        else reportError(error);
        button.prop("disabled",false);
      });
    }).appendTo(actions);
    if (managerReview || financeReview) {
      const form = $("<form>").appendTo(actions);
      const reason = $("<input>").attr({"aria-label":"보완 사유",required:true,maxlength:240,placeholder:"보완 사유"}).appendTo(form);
      const button = $("<button>").text("보완 요청").appendTo(form);
      reason.on("input",()=>reason[0].setCustomValidity(""));
      form.on("submit",event=>{
        event.preventDefault();
        const note = reason.val().trim();
        if (!note) {
          reason[0].setCustomValidity("보완 사유를 입력하세요.");
          reason[0].reportValidity();
          return;
        }
        send(button,"/return",{round:claim.reviewRound,reason:note});
      });
    }
  }
}
function refresh() {
  const before = cursors[cursors.length-1];
  const status = claimFilter.value;
  const params = before === null ? {} : {before};
  if (status) params.status = status;
  return $.getJSON("/api/workspace",params).then(state=>{
    if (status === claimFilter.value && before === cursors[cursors.length-1]) render(state);
  });
}
function changeFilter(){
  const previous = cursors.slice();
  claimFilter.disabled = true;
  $("#older,#newer").prop("disabled",true);
  cursors.splice(0,cursors.length,null);
  return refresh().fail(error=>{
    claimFilter.value = claimFilter.dataset.applied || "";
    cursors.splice(0,cursors.length,...previous);
    reportError(error);
  }).always(()=>{
    claimFilter.disabled = false;
    $("#older,#newer").prop("disabled",false);
  });
}
$(claimFilter).on("change",changeFilter);
function changePage(older) {
  const previous = cursors.slice();
  if (older && nextCursor !== null) cursors.push(nextCursor);
  else if (!older && cursors.length > 1) cursors.pop();
  else return;
  $("#older,#newer").prop("disabled",true);
  return refresh().fail(error => {cursors.splice(0,cursors.length,...previous); reportError(error);})
    .always(()=>$("#older,#newer").prop("disabled",false));
}
$("#older").on("click",()=>changePage(true));
$("#newer").on("click",()=>changePage(false));
function showBranchBalance() {
  return $.getJSON("/api/branch-allocation").done(report=>{
    if ($("#branch-allocation").prop("hidden")) return;
    const box = $("#branch-report").empty();
    for (const [label,balance] of [["본부",report.head],["지점",report.branch]]) {
      $("<p>").text(label+" 배정액 "+money(balance.allocation)+" · 가용액 "+money(balance.available)).appendTo(box);
    }
    for (const entry of report.recent) $("<p>").text(entry.periodKey+" · 지점 배정 "+money(entry.amount)).appendTo(box);
  }).fail(reportError);
}
$("#view-branch").on("click",showBranchBalance);
$("#allocate").on("submit",event=>{
  event.preventDefault();
  const form = $(event.target);
  const amount = form.find("input").val();
  const previous = JSON.parse(sessionStorage.getItem("pending-allocation") || "null");
  const key = previous?.amount === amount ? previous.key : crypto.randomUUID();
  sessionStorage.setItem("pending-allocation",JSON.stringify({amount,key}));
  const button = form.find("button").prop("disabled",true);
  api("/api/branch-allocation",{requestKey:key,amount}).then(()=>{
    sessionStorage.removeItem("pending-allocation");
    form[0].reset();
    return refresh().then(showBranchBalance);
  }).fail(reportError).always(()=>button.prop("disabled",false));
});
function statementSources(box, period, category, before) {
  const params = before == null ? {category} : {category,before};
  $.getJSON("/api/statements/"+encodeURIComponent(period)+"/claims",params).done(result=>{
    box.empty();
    for (const claim of result.claims) {
      const detail = $("<details>").appendTo(box);
      $("<summary>").text(claim.title+" · "+claim.claimId+" · 당월 순집행 "+money(claim.spent)).appendTo(detail);
      const entries = $("<div>").appendTo(detail);
      detail.on("toggle",()=>{
        if (!detail.prop("open")) return;
        $.getJSON("/api/claims/"+encodeURIComponent(claim.claimId)+"/postings").done(rows=>{
          entries.empty();
          for (const row of rows) $("<p>").text(row.periodKey+" · "+row.kind+" · 집행 증감 "+money(row.spentDelta)).appendTo(entries);
        }).fail(reportError);
      });
    }
    if (!result.claims.length) box.text("조회된 원신청이 없습니다.");
    if (result.nextCursor!=null) $("<button>").text("다음 원신청 목록").on("click",()=>statementSources(box,period,category,result.nextCursor)).appendTo(box);
  }).fail(reportError);
}
function showStatement(report) {
  if ($("#role").val() !== "FINANCE") return;
  const box = $("#statement").empty();
  $("<strong>").text(report.period + " · " + ({OPEN:"마감 전",CLOSING:"마감 진행 중",CLOSED:"마감 완료"})[report.state]).appendTo(box);
  for (const line of report.lines) {
    const label = categories[line.category] || line.category;
    const group = $("<details>").appendTo(box);
    $("<summary>").text(label + " · " + line.claimCount + "건 · " + money(line.spent)).appendTo(group);
    const sources = $("<div>").appendTo(group);
    group.on("toggle",()=>{if (group.prop("open")) statementSources(sources,report.period,line.category,null);});
  }
  $("<p>").text("누적 집행 " + money(report.balance.spent) + " / 가용 예산 " + money(report.balance.available)).appendTo(box);
  if (report.pending.length) $("<p>").text("진행 중인 신청 " + report.pending.join(", ")).appendTo(box);
  const mismatch = report.difference && !report.difference.balanced;
  if (mismatch) $("<p>").text("마감 보류 · 전표 합계에서 잔액을 뺀 차이 · 약정 " +
    money(report.difference.committed) + " / 집행 " + money(report.difference.spent)).appendTo(box);
  $("#close-period").prop("hidden", report.state === "CLOSED" || report.pending.length > 0 || mismatch || report.balance.committed !== 0);
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
      api("/api/statements/"+report.period+"/advance",{}).done(next=>{
        showStatement(next);
        document.dispatchEvent(new Event("budget-period-opened"));
      }).fail(reportError)).appendTo(box);
  }
}
$("#view-statement").on("click",()=>$.getJSON("/api/statement").done(showStatement).fail(reportError));
$("#view-failures").on("click",function() {
  const box = $("#portal-failures").empty();
  $.getJSON("/api/portal/failures").done(rows=>{
    if ($("#role").val() !== "FINANCE") return;
    box.text(rows.length ? "송신 실패 · 오래된 순 최대 50건" : "재처리할 송신 실패가 없습니다.");
    for (const row of rows) {
      const item = $("<div>").appendTo(box);
      $("<p>").text(row.title+" · 신청번호 "+row.claimId+" · "+row.sequenceNo+"차 · "+money(row.amount)
        +" · 시도 "+row.attempts+"회 · "+row.lastError).appendTo(item);
      $("<button>").text("다시 보내기").on("click",function() {
        $(this).prop("disabled",true);
        api("/api/portal/failures/"+encodeURIComponent(row.eventId)+"/retry",{}).done(()=>notice("포털에서 수신을 확인했습니다."))
          .fail(reportError).always(()=>$("#view-failures").trigger("click"));
      }).appendTo(item);
    }
  }).fail(reportError);
});
$("#close-period").on("click",function() {
  if (!window.confirm("마감하면 이 기간의 신규 신청과 집행을 종료합니다. 확정할까요?")) return;
  $(this).prop("disabled",true);
  api("/api/statement/close",{}).done(showStatement).fail(reportError).always(()=>$(this).prop("disabled",false));
});
$("#employee-start").on("click",()=>{
  const url = new URL(location.href);
  url.searchParams.set("view","employee");
  history.replaceState(null,"",url);
  $("#start").trigger("click");
});
$("#start").on("click",function() {
  const start = $("#start,#employee-start").prop("disabled",true);
  loadCategories().then(()=>api("/api/demo",{organization:$("#organization").val()})).then(render)
    .fail(reportError).always(()=>start.prop("disabled",false));
});
$("#end-demo").on("submit", event => {
  if (!window.confirm("현재 체험을 종료할까요? 다음 체험은 새 예산 100만 원으로 시작합니다.")) {
    event.preventDefault();
    return;
  }
  sessionStorage.removeItem("pending-claim");
});
function changeRole(role) {
  const selector = $("#role,#delegate").prop("disabled",true);
  const panels = $("#desk > section,#estimate-panel,#policy-panel");
  panels.prop("inert",true);
  claimFilter.value = "";
  cursors.splice(0,cursors.length,null);
  api("/api/demo/role",{role}).then(refresh).then(()=>{
    panels.prop("inert",false);
    selector.prop("disabled",false);
  }).fail(()=>location.reload());
}
$("#role").on("change",function(){changeRole(this.value);});
$("#delegate").on("click",function(){changeRole($(this).data("next"));});
let estimateRevision = 0;
let expectedGeneration = null;
function clearEstimate() {
  estimateRevision++;
  expectedGeneration = null;
  $("#estimate-result").empty();
}
$("#entry").on("input change","form",clearEstimate);
function loadEmployee() {
  if(document.getElementById("employee-module"))return;
  const trigger = $("#employee-view").prop("disabled",true);
  const script = document.createElement("script");
  script.id = "employee-module";
  script.src = trigger[0].dataset.module;
  script.onerror = () => {script.remove();trigger.prop("disabled",false); notice("직원 신청 화면을 불러오지 못했습니다. 다시 시도하세요.");};
  document.head.append(script);
}
$("#employee-view").on("click",()=>{
  const url = new URL(location.href);
  url.searchParams.set("view","employee");
  history.replaceState(null,"",url);
  loadEmployee();
});
$("#estimate").on("click", () => {
  const form = document.querySelector("#employee-submit") || document.querySelector("#submit");
  if (!form.reportValidity()) return;
  clearEstimate();
  const revision = estimateRevision;
  const data = $(form).find('[name="category"],[name="estimated"],[name="days"]').serialize();
  $.getJSON("/api/claims/estimate",data).done(result => {
    if (revision !== estimateRevision) return;
    expectedGeneration = result.generation;
    $("#estimate-result").text("지원 예정 " + money(result.supported) + " · 본인 부담 " + money(result.employeeShare) +
      " · 결재 경로 " + (result.route === "MANAGER_FINANCE" ? "부서장 → 재무" : "부서장") +
      " · 적용 규정 " + result.generation + " · " + result.formula);
    $("<span>").text(" 정산 준비 · " + result.evidenceRequirements.join(" ")).appendTo("#estimate-result");
  }).fail(error => {if (revision === estimateRevision) reportError(error);});
});
$("#entry").on("submit","form",event=>{
  event.preventDefault();
  const button = $(event.target).find("button").prop("disabled",true);
  const status = $("#submit-status").text("신청을 접수하고 있습니다.");
  const fields = $(event.target).serialize();
  const previous = JSON.parse(sessionStorage.getItem("pending-claim") || "null");
  const key = previous?.fields === fields ? previous.key : crypto.randomUUID();
  sessionStorage.setItem("pending-claim", JSON.stringify({fields, key}));
  const generation = expectedGeneration === null ? "" : "&expectedGeneration=" + expectedGeneration;
  const payload = fields + generation + "&requestKey=" + encodeURIComponent(key);
  api("/api/claims",payload).then(undefined,error=>{
    if (!inboxReady || error.status !== 0) throw error;
    status.text("서버에서 신청 접수 결과를 확인하고 있습니다.");
    return api("/api/claims",payload);
  }).then(() => {
    sessionStorage.removeItem("pending-claim");
    claimFilter.value = "";
    cursors.splice(0,cursors.length,null);
    status.text("신청이 접수됐습니다. 아래 신청 내역에서 확인하세요.");
    return refresh().then(undefined, () => {
      status.text("신청이 접수됐습니다. 화면을 새로 열어 신청 내역을 확인하세요.");
    });
  }).fail(error => {
    const message = error.status === 409 && generation
      ? "지원 규정이 변경되었습니다. 지원 예정액을 다시 확인하세요."
      : errorMessage(error);
    status.text(message);
    notice(message);
  }).always(()=>button.prop("disabled",false));
});
$.getJSON("/api/workspace").done(state=>loadCategories().then(()=>render(state)).fail(reportError));
document.getElementById("policy-panel").addEventListener("toggle", function() {
  if (this.open && !document.getElementById("policy-module")) {
    if (!this.dataset.module) {notice("규정 편집기 파일이 설치되지 않았습니다."); return;}
    const script = document.createElement("script");
    script.id = "policy-module";
    script.src = this.dataset.module;
    script.onerror = () => {script.remove(); notice("규정 편집기를 불러오지 못했습니다.");};
    document.head.append(script);
  }
});
