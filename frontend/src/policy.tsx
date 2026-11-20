import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";

type Version = {generation:number; formula:string; evaluatorVersion:string};
type Draft = {draftId:string; baseGeneration:number; formula:string; sampleCost:number|null;
  sampleDays:number|null; originalSupport:number|null; candidateSupport:number|null};
const routes = {MANAGER:"부서장", MANAGER_FINANCE:"부서장 → 재무"};
type Preview = {generation:number; original:number; candidate:number; originalRoute:keyof typeof routes; candidateRoute:keyof typeof routes};
type History = {versions:Version[]; drafts:Draft[]};

const won = (n:number) => n.toLocaleString("ko-KR") + "원";

async function request<T>(url:string, data?:Record<string,string>, signal?:AbortSignal):Promise<T> {
  const token = document.querySelector<HTMLMetaElement>('meta[name="csrf-token"]')!.content;
  const response = await fetch(url, {
    method:data ? "POST" : "GET", signal,
    headers:data ? {"X-CSRF-TOKEN":token} : undefined,
    body:data ? new URLSearchParams(data) : undefined
  });
  if (response.status === 409) throw new Error("다른 규정이 먼저 발행됐습니다. 최신 버전을 다시 확인하세요.");
  if (!response.ok) {
    const problem = await response.json().catch(()=>({}));
    throw new Error(problem.message || "권한과 입력값을 확인하세요.");
  }
  return response.json();
}

function PolicyEditor() {
  const [category,setCategory] = useState("CONFERENCE");
  const [history,setHistory] = useState<History>();
  const [revision,reload] = useState(0);
  const [formula,setFormula] = useState("");
  const [restoreGeneration,setRestoreGeneration] = useState<number>();
  const restoration:Record<string,string> = restoreGeneration === undefined ? {} : {restoreGeneration:String(restoreGeneration)};
  const [cost,setCost] = useState("180000");
  const [days,setDays] = useState("1");
  const [preview,setPreview] = useState<Preview>();
  const [diagnostic,setDiagnostic] = useState("");
  const [notice,setNotice] = useState("");
  const [busy,setBusy] = useState(false);
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
      .then(result=>{setHistory(result);setRestoreGeneration(undefined);setFormula(result.versions[0].formula);})
      .catch(error=>{if (!controller.signal.aborted) setNotice(error.message);});
    return ()=>controller.abort();
  },[category,revision]);
  useEffect(()=>{
    const controller = new AbortController();
    setPreview(undefined);
    setDiagnostic("");
    if (!history) return;
    const timer = window.setTimeout(()=>{
      request<Preview>("/api/policies/preview",{category,formula,cost,days,...restoration},controller.signal)
        .then(result=>{if (!controller.signal.aborted) setPreview(result);})
        .catch(error=>{if (!controller.signal.aborted) setDiagnostic(error.message);});
    },150);
    return ()=>{window.clearTimeout(timer);controller.abort();};
  },[category,formula,cost,days,history,restoreGeneration]);
  async function change(url:string,data:Record<string,string>,message:string) {
    setBusy(true);
    try {
      await request(url,data);
      setNotice(message);
      reload(value=>value+1);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "요청을 확인하세요.");
    } finally {setBusy(false);}
  }
  const current = history?.versions[0];
  return <section aria-label="비용 지원 규정"><h2>지원 규정 작업실</h2>
    <label>비목<select value={category} onChange={e=>{setCategory(e.target.value);setNotice("");}}>
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
    {role==="FINANCE" && <button disabled={!preview || busy} onClick={()=>change("/api/policies/drafts",{category,formula,cost,days,...restoration,generation:String(current.generation)},"부서장 검토를 요청했습니다.")}>검토 요청</button>}
    <h3>검토 대기</h3>
    {history!.drafts.filter(d=>d.baseGeneration===current.generation).map(d=><div key={d.draftId}><code>{d.formula}</code>
      {d.sampleCost!=null && d.originalSupport!=null && d.candidateSupport!=null &&
        <p>검토 요청 시 실비 {won(d.sampleCost)} · {d.sampleDays}일 · 지원액 {won(d.originalSupport)} → {won(d.candidateSupport)}</p>}
      {role==="MANAGER" && <button disabled={busy} onClick={()=>change("/api/policies/drafts/"+d.draftId+"/publish",{},"새 규정을 발행했습니다.")}>발행</button>}</div>)}
    <h3>발행 이력</h3>
    {history!.versions.map(v=><p key={v.generation}>버전 {v.generation} · 평가기 {v.evaluatorVersion} · <code>{v.formula}</code> {role==="FINANCE" && v.generation!==current.generation && <button onClick={()=>{setRestoreGeneration(v.generation);setFormula(v.formula);}}>복구 후보로 선택</button>}</p>)}</>}
    <p role="status">{notice}</p></section>;
}

createRoot(document.getElementById("policy-editor")!).render(<PolicyEditor/>);
