import {useRef,useState} from "react";
import {Claim,claimRequest,ClaimRequestError,Evidence,won} from "./model";
import {IntentInterpreter,koreanCommands} from "./assistantIntent";

export function InboxAssistant({claim,available,interpreter=koreanCommands}:{
  claim?:Claim;available:number;interpreter?:IntentInterpreter;
}) {
  const [open,setOpen]=useState(false);
  const [text,setText]=useState("");
  const [message,setMessage]=useState("");
  const [confirm,setConfirm]=useState<"attachReceipt"|"resubmit"|null>(null);
  const [busy,setBusy]=useState(false);
  const lock=useRef(false);
  const canAttach=claim?.status==="NEEDS_RECEIPT" && !!claim.actual;

  async function ask() {
    if(lock.current) return;
    lock.current=true;setBusy(true);setConfirm(null);
    try {
      const intent=await interpreter.interpret(text);
      if(intent==="balance") setMessage("부서 가용 예산은 "+won(available)+"입니다.");
      else if(intent==="status" && claim) setMessage(claim.title+" · "+window.employeeInbox.labels[claim.status]);
      else if(intent==="attachReceipt" && claim && canAttach) {
        setMessage(claim.title+"에 실비 "+won(claim.actual!)+"의 합성 지출 영수증을 첨부할까요?");
        setConfirm(intent);
      } else if(intent==="resubmit" && claim && canAttach) {
        const docs=await claimRequest<Evidence[]>(claim.claimId,"evidence");
        if(docs.some(d=>d.evidenceType==="RECEIPT" && d.receiptAmount===claim.actual)) {
          setMessage(claim.title+"의 실비 "+won(claim.actual!)+"를 재무팀에 다시 제출할까요?");
          setConfirm(intent);
        } else setMessage("실비와 같은 금액의 영수증이 필요합니다. ‘영수증 첨부해줘’라고 입력해 주세요.");
      } else if(intent==="reviewerOnly") setMessage("현재 역할은 직원입니다. 결재는 부서장, 정산 확정은 재무 담당자가 해당 결재함에서 처리합니다.");
      else if(intent!=="unknown") setMessage("선택한 신청에서 처리할 수 없는 요청입니다. 보완 요청 상태와 담당자를 확인해 주세요.");
      else setMessage("예산·상태 조회, 영수증 첨부, 보완 내용 재제출을 지원합니다.");
    } catch {setMessage("요청을 해석하지 못했습니다. 다시 입력해 주세요.");}
    finally {lock.current=false;setBusy(false);}
  }

  async function execute() {
    if(lock.current || !claim || !canAttach || !confirm) return;
    lock.current=true;setBusy(true);setConfirm(null);
    try {
      if(confirm==="resubmit") {
        try {await claimRequest(claim.claimId,"review",{actual:String(claim.actual),round:String(claim.reviewRound)});}
        catch(error) {if(error instanceof ClaimRequestError && error.status<500) throw error;}
        await window.employeeInbox.refresh();
        if(window.employeeInbox.state?.claims.find(item=>item.claimId===claim.claimId)?.status!=="IN_REVIEW") throw new Error();
        setMessage("재제출됐습니다. 재무팀이 정산을 검토합니다.");
        return;
      }
      // A lost POST response can follow a successful commit. Read the recorded document before reporting success.
      try {await claimRequest(claim.claimId,"evidence",{type:"RECEIPT",amount:String(claim.actual),correctionRound:String(claim.reviewRound)});}
      catch(error) {if(error instanceof ClaimRequestError && error.status<500) throw error;}
      const docs=await claimRequest<Evidence[]>(claim.claimId,"evidence");
      if(!docs.some(d=>d.evidenceType==="RECEIPT" && d.receiptAmount===claim.actual)) throw new Error();
      document.dispatchEvent(new CustomEvent("employee-evidence-confirmed",{detail:{id:claim.claimId,docs}}));
      await window.employeeInbox.refresh();
      setMessage("지출 영수증 · "+won(claim.actual!)+" 첨부됨. ‘다시 제출해줘’라고 입력할 수 있습니다.");
    } catch {setMessage("처리 결과를 확인할 수 없습니다. 신청 상세에서 상태와 증빙을 확인해 주세요.");}
    finally {lock.current=false;setBusy(false);}
  }
  return <section aria-label="업무 도우미"><button type="button" aria-expanded={open} onClick={()=>{setOpen(!open);setConfirm(null);}}>대화형 업무 도우미</button>
    {open&&<><p>가용 예산 알려줘 · 신청 상태 알려줘 · 영수증 첨부해줘 · 다시 제출해줘</p>
    {claim&&<p>선택한 신청 · {claim.title}</p>}
    <form onSubmit={e=>{e.preventDefault();void ask();}}><label>업무 요청<input maxLength={160} required value={text} disabled={busy}
      onChange={e=>{setText(e.target.value);setConfirm(null);}}/></label><button disabled={busy}>요청 확인</button></form>
    <p role="status">{message}</p>{confirm&&<><button type="button" disabled={busy} onClick={()=>void execute()}>{confirm==="resubmit"?"이 신청 재제출":"이 영수증 첨부"}</button>
    <button type="button" onClick={()=>{setConfirm(null);setMessage("요청을 취소했습니다.");}}>취소</button></>}</>}
  </section>;
}
