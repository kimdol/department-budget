import {useEffect,useLayoutEffect,useRef,useState} from "react";
import {ClaimDetail} from "./ClaimDetail";
import {ClaimList} from "./ClaimList";
export function EmployeeInbox(){
  const desk=window.employeeInbox;
  const [,paint]=useState(0);
  const [id,setId]=useState("");
  const [open,setOpen]=useState(false);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const scroll=useRef(0);
  const heading=useRef<HTMLHeadingElement>(null);
  useEffect(()=>{
    const update=()=>paint(n=>n+1);
    document.addEventListener("employee-inbox-update",update);
    return ()=>document.removeEventListener("employee-inbox-update",update);
  },[]);
  useLayoutEffect(()=>{if(open)heading.current?.focus();},[id,open]);
  const state=desk.state;
  if(!state||state.role!=="EMPLOYEE")return null;
  const claims=state.claims;
  const selected=claims.find(c=>c.claimId===id)??claims[0];
  async function run(fn:()=>PromiseLike<unknown>){
    setBusy(true);setError("");
    try{await fn();setOpen(false);}catch{setError("목록을 불러오지 못했습니다. 다시 시도해 주세요.");}
    finally{setBusy(false);}
  }
  function back(){
    setOpen(false);
    requestAnimationFrame(()=>{document.getElementById("inbox-item-"+selected?.claimId)?.focus({preventScroll:true});window.scrollTo(0,scroll.current);});
  }
  return <><h2>내 신청함</h2><div className={"inbox-layout"+(open&&selected?" detail-open":"")}>
    <ClaimList claims={claims} selected={selected?.claimId} busy={busy} run={run}
      select={key=>{scroll.current=window.scrollY;setId(key);setOpen(true);}}/>
    {selected&&<div className="inbox-pane"><button type="button" className="inbox-back" onClick={back}>목록으로 돌아가기</button>
      <h3 tabIndex={-1} ref={heading}>신청 상세</h3><ClaimDetail key={selected.claimId} claim={selected} available={state.balance.available}/></div>}
    </div><p role="status">{error}</p>
    <a href={location.pathname+"?"+new URLSearchParams({department:new URLSearchParams(location.search).get("department")??"PRIMARY",view:"classic"})}>신청 변경·철회 및 전체 이력</a></>;
}
