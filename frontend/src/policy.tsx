import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";

type Version = {attendanceRule:string; generation:number; formula:string; evaluatorVersion:string};
type Draft = {reviewHash:string; attendanceRule:string; draftId:string; baseGeneration:number; formula:string; sampleCost:number|null;
  sampleDays:number|null; originalSupport:number|null; candidateSupport:number|null};
const routes = {MANAGER:"부서장", MANAGER_FINANCE:"부서장 → 재무"};
type Preview = {generation:number; original:number; candidate:number; originalRoute:keyof typeof routes; candidateRoute:keyof typeof routes};
type History = {versions:Version[]; drafts:Draft[]};

const won = (n:number) => n.toLocaleString("ko-KR") + "원";

class PolicyConflict extends Error {}

async function request<T>(url:string, data?:Record<string,string>, signal?:AbortSignal):Promise<T> {
  const token = document.querySelector<HTMLMetaElement>('meta[name="csrf-token"]')!.content;
  const endpoint = new URL(url,location.origin);
  const department = new URLSearchParams(location.search).get("department");
  if (department) endpoint.searchParams.set("department",department);
  const response = await fetch(endpoint, {
    method:data ? "POST" : "GET", signal,
    headers:data ? {"X-CSRF-TOKEN":token} : undefined,
    body:data ? new URLSearchParams(data) : undefined
  });
  if (response.status === 409) throw new PolicyConflict("검토한 초안이나 적용 규정이 바뀌었습니다. 최신 내용을 다시 확인하세요.");
  if (!response.ok) {
    const problem = await response.json().catch(()=>({}));
    throw new Error(problem.message || "권한과 입력값을 확인하세요.");
  }
  return response.json();
}

type AttendanceDecision = {verdict:"PASS"|"FAIL"|"MISSING"; evaluated:{name:string; verdict:"PASS"|"FAIL"|"MISSING"}[]; skipped:string[]};
const verdicts = {PASS:"충족",FAIL:"미충족",MISSING:"자료 부족"};

function AttendancePreview({category,expression,setExpression}:{category:string;expression:string;setExpression:(value:string)=>void}) {

  const [attached,setAttached] = useState(false);
  const [result,setResult] = useState<AttendanceDecision>();
  const [error,setError] = useState("");
  useEffect(()=>{
    const controller = new AbortController();
    setResult(undefined);
    setError("");
    const timer = window.setTimeout(()=>{
      request<{attendance:AttendanceDecision}>("/api/policies/attendance-preview",
        {expression,category,attached:String(attached)},controller.signal)
        .then(value=>{if (!controller.signal.aborted) setResult(value.attendance);})
        .catch(reason=>{if (!controller.signal.aborted) setError(reason.message);});
    },250);
    return ()=>{window.clearTimeout(timer);controller.abort();};
  },[expression,category,attached]);
  return <section aria-label="참석 조건 미리보기">
    <h3>참석 조건 미리보기</h3>
    <label>비교할 참석 조건<textarea value={expression} maxLength={512} onChange={e=>setExpression(e.target.value)}/></label>
    <small>ALL은 모두 충족, ANY는 하나 이상 충족입니다. OUTSIDE_HOSPITAL은 병원 외 조직, OTHER_CATEGORY는 학회비 외 비목, ATTACHED는 참석 확인서 제출을 뜻합니다.</small>
    <label><input type="checkbox" checked={attached} onChange={e=>setAttached(e.target.checked)}/>비교 사례에 참석 확인서 포함</label>
    <p role="status">{error || (result ? "판정 결과 · "+verdicts[result.verdict] : "계산 중")}</p>
    {result && <ul>{[...result.evaluated.map(s=>s.name+" · "+verdicts[s.verdict]),
      ...result.skipped.map(name=>name+" · 평가 생략")].map((step,index)=><li key={index}>{step}</li>)}</ul>}
  </section>;
}

type Impact = {claimId:string; title:string; generation:number; estimated:number; original:number;
  originalRoute:keyof typeof routes; candidate:number|null; candidateRoute:keyof typeof routes|null; error:string|null};

function HistoricalImpact({parameters}:{parameters:Record<string,string>}) {
  const [pages,setPages] = useState<string[]>([]);
  const [result,setResult] = useState<{claims:Impact[];nextCursor:number|null}>();
  const [error,setError] = useState("");
  const before = pages.at(-1);
  const query = JSON.stringify(parameters);
  useEffect(()=>{
    setResult(undefined);
    setError("");
    if (before === undefined) return;
    const controller = new AbortController();
    const data:Record<string,string> = JSON.parse(query);
    if (before) data.before = before;
    request<{claims:Impact[];nextCursor:number|null}>("/api/policies/impact",data,controller.signal)
      .then(value=>{if (!controller.signal.aborted) setResult(value);})
      .catch(reason=>{if (!controller.signal.aborted) setError(reason.message);});
    return ()=>controller.abort();
  },[before,query]);
  return <section aria-label="과거 신청 영향 비교">
    <button onClick={()=>setPages(pages.length ? [] : [""])}>{pages.length ? "과거 비교 닫기" : "과거 신청과 비교"}</button>
    {pages.length>0 && <><p role="status">{error || (result ? result.claims.length ? "접수 당시와 후보 규정의 사전승인 결과" : "이 비목으로 접수한 신청이 없습니다." : "계산 중")}</p>
      {result?.claims.map(row=><p key={row.claimId}><strong>{row.title}</strong> · {row.claimId} · 예정 {won(row.estimated)} · 당시 규정 {row.generation} ·
        {" "}{won(row.original)} ({routes[row.originalRoute]}) → {row.error || (row.candidate!==null && row.candidateRoute && won(row.candidate)+" ("+routes[row.candidateRoute]+")")}</p>)}
      {pages.length>1 && <button onClick={()=>setPages(pages.slice(0,-1))}>앞선 비교 목록</button>}
      {result?.nextCursor!=null && <button onClick={()=>setPages([...pages,String(result.nextCursor)])}>다음 비교 목록</button>}
    </>}
  </section>;
}

function PolicyEditor() {
  const [category,setCategory] = useState("CONFERENCE");
  const [history,setHistory] = useState<History>();
  const [revision,reload] = useState(0);
  const [attendanceRule,setAttendanceRule] = useState("");
  const [formula,setFormula] = useState("");
  const [restoreGeneration,setRestoreGeneration] = useState<number>();
  const restoration:Record<string,string> = restoreGeneration === undefined ? {} : {restoreGeneration:String(restoreGeneration)};
  const [cost,setCost] = useState("180000");
  const [days,setDays] = useState("1");
  const [preview,setPreview] = useState<Preview>();
  const [diagnostic,setDiagnostic] = useState("");
  const [notice,setNotice] = useState("");
  const [busy,setBusy] = useState(false);
  const [conflict,setConflict] = useState(false);
  const [role,setRole] = useState(document.querySelector<HTMLSelectElement>("#role")!.value);
  useEffect(()=>{
    const sync = () => setRole(document.querySelector<HTMLSelectElement>("#role")!.value);
    document.addEventListener("budget-session",sync);
    return ()=>document.removeEventListener("budget-session",sync);
  },[]);
  useEffect(()=>{
    const controller = new AbortController();
    setHistory(undefined);
    request<History>("/api/policies?category="+category,undefined,controller.signal)
      .then(result=>{setHistory(result);setRestoreGeneration(undefined);setFormula(result.versions[0].formula);setAttendanceRule(result.versions[0].attendanceRule);})
      .catch(error=>{if (!controller.signal.aborted) setNotice(error.message);});
    return ()=>controller.abort();
  },[category,revision]);
  useEffect(()=>{
    const controller = new AbortController();
    setPreview(undefined);
    setDiagnostic("");
    if (!history) return;
    const timer = window.setTimeout(()=>{
      request<Preview>("/api/policies/preview",{category,formula,cost,days,attendanceRule,...restoration},controller.signal)
        .then(result=>{if (!controller.signal.aborted) setPreview(result);})
        .catch(error=>{if (!controller.signal.aborted) setDiagnostic(error.message);});
    },150);
    return ()=>{window.clearTimeout(timer);controller.abort();};
  },[category,formula,cost,days,history,restoreGeneration,attendanceRule]);
  async function change(url:string,data:Record<string,string>,message:string) {
    setBusy(true);
    try {
      await request(url,data);
      setNotice(message);
      reload(value=>value+1);
    } catch (error) {
      if (error instanceof PolicyConflict) setConflict(true);
      setNotice(error instanceof Error ? error.message : "요청을 확인하세요.");
    } finally {setBusy(false);}
  }
  async function compareLatest() {
    setBusy(true);
    try {
      const latest = await request<History>("/api/policies?category="+category);
      setPreview(undefined);
      setHistory(latest);
      setConflict(false);
      setNotice("최신 규정을 불러왔습니다. 비교 결과를 확인하세요.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "최신 규정을 불러올 수 없습니다.");
    } finally {setBusy(false);}
  }
  const current = history?.versions[0];
  return <section aria-label="비용 지원 규정"><h2>지원 규정 작업실</h2>
    <label>비목<select value={category} disabled={busy} onChange={e=>{setCategory(e.target.value);setNotice("");setConflict(false);}}>
      <option value="CONFERENCE">학회 참가비</option><option value="EDUCATION">일반 교육비</option><option value="TRAVEL">출장비</option>
    </select></label>
    {current && <><p>적용 버전 {current.generation}</p>
    <label>인정액 수식<textarea value={formula} rows={2} onChange={e=>{setRestoreGeneration(undefined);setFormula(e.target.value);}}/></label>
    <small>eligible_cost 실비 · support_rate 지원율 · event_cap 행사 한도 · days 일수 · daily_cap 일 한도</small>
    <div className="actions"><label>비교할 실비(원)<input type="number" value={cost} onChange={e=>setCost(e.target.value)}/></label>
    <label>일수<input type="number" value={days} onChange={e=>setDays(e.target.value)}/></label></div>
    <p role="status">{diagnostic || (preview ? "기존 "+won(preview.original)+" / 후보 "+won(preview.candidate)+" / 차이 "+won(preview.candidate-preview.original) : "계산 중")}</p>
    {preview && <p>직원 부담액 기존 {won(Number(cost)-preview.original)} / 후보 {won(Number(cost)-preview.candidate)}</p>}
    {preview && <p>승인 경로 기존 {routes[preview.originalRoute]} / 후보 {routes[preview.candidateRoute]}</p>}
    {conflict && <button disabled={busy} onClick={compareLatest}>최신 규정과 다시 비교</button>}
    {role==="FINANCE" && <button disabled={!preview || busy || conflict} onClick={()=>change("/api/policies/drafts",{category,formula,cost,days,attendanceRule,...restoration,generation:String(current.generation)},"부서장 검토를 요청했습니다.")}>검토 요청</button>}
    {role==="FINANCE" && <AttendancePreview category={category} expression={attendanceRule} setExpression={value=>{setRestoreGeneration(undefined);setAttendanceRule(value);}}/>}
    {role==="FINANCE" && preview && <HistoricalImpact key={JSON.stringify([category,formula,attendanceRule,current.generation,restoreGeneration])}
      parameters={{category,formula,attendanceRule,generation:String(current.generation),...restoration}}/>}
    <h3>검토 대기</h3>
    {history!.drafts.filter(d=>d.baseGeneration===current.generation).map(d=><div key={d.draftId}><code>{d.formula}</code><p>참석 조건 · <code>{d.attendanceRule}</code></p>
      {d.sampleCost!=null && d.originalSupport!=null && d.candidateSupport!=null &&
        <p>검토 요청 시 실비 {won(d.sampleCost)} · {d.sampleDays}일 · 지원액 {won(d.originalSupport)} → {won(d.candidateSupport)}</p>}
      {role==="MANAGER" && <button disabled={busy || conflict} onClick={()=>change("/api/policies/drafts/"+d.draftId+"/publish",{reviewHash:d.reviewHash},"새 규정을 발행했습니다.")}>발행</button>}</div>)}
    <h3>발행 이력</h3>
    {history!.versions.map(v=><p key={v.generation}>버전 {v.generation} · 평가기 {v.evaluatorVersion} · <code>{v.formula}</code> {role==="FINANCE" && v.generation!==current.generation && <button onClick={()=>{setRestoreGeneration(v.generation);setFormula(v.formula);setAttendanceRule(v.attendanceRule);}}>복구 후보로 선택</button>}</p>)}</>}
    <p role="status">{notice}</p></section>;
}

createRoot(document.getElementById("policy-editor")!).render(<PolicyEditor/>);
