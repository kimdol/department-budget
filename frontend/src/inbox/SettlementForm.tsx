import {useEffect,useRef,useState} from "react";
import {Claim,claimRequest,Evidence,won} from "./model";

export function SettlementForm({claim}:{claim:Claim}) {
  const [amount,setAmount]=useState(String(claim.actual??225000));
  const [type,setType]=useState("RECEIPT");
  const [documents,setDocuments]=useState<Evidence[]>([]);
  const [message,setMessage]=useState("");
  const [busy,setBusy]=useState(false);
  const lock=useRef(false);
  const edited=useRef(false);
  const alive=useRef(true);
  const id=claim.claimId;
  useEffect(()=>{
    alive.current=true;
    let current=true;
    claimRequest<Evidence[]>(id,"evidence").then(rows=>{if(current&&!edited.current)setDocuments(rows);})
      .catch(()=>{if(current)setMessage("첨부 목록을 불러오지 못했습니다. 새로 확인해 주세요.");});
    function confirmed(event:Event) {
      const detail=(event as CustomEvent<{id:string;docs:Evidence[]}>).detail;
      if(detail.id===id){edited.current=true;setDocuments(detail.docs);}
    }
    document.addEventListener("employee-evidence-confirmed",confirmed);
    return ()=>{current=false;alive.current=false;document.removeEventListener("employee-evidence-confirmed",confirmed);};
  },[id]);

  async function submit(attach:boolean) {
    if(lock.current)return;
    lock.current=true;
    edited.current=true;
    setBusy(true);
    setMessage(attach?"증빙을 첨부하고 있습니다.":"정산 신청을 접수하고 있습니다.");
    try {
      try {
        if(attach) await claimRequest(id,"evidence",{type,amount});
        else await claimRequest(id,"review",{actual:amount,round:String(claim.reviewRound)});
      } catch {
        if(!attach) {
          await window.employeeInbox.refresh();
          if(window.employeeInbox.state?.claims.find(row=>row.claimId===id)?.status==="IN_REVIEW")return;
          throw new Error();
        }
      }
      if(attach) {
        const rows=await claimRequest<Evidence[]>(id,"evidence");
        if(!alive.current)return;
        setDocuments(rows);
        if(!rows.some(row=>row.evidenceType===type&&row.receiptAmount===Number(amount))) throw new Error();
        setMessage("첨부가 확인됐습니다. 아래 버튼으로 정산을 제출하세요.");
      } else {
        await window.employeeInbox.refresh();
      }
    } catch {
      if(alive.current)setMessage("처리 결과를 확인할 수 없습니다. 입력값을 유지했습니다. 금액을 확인하고 다시 시도해 주세요.");
    } finally {
      lock.current=false;
      if(alive.current)setBusy(false);
    }
  }
  return <form className="receipt-form" onSubmit={event=>{event.preventDefault();void submit(false);}}>
    <fieldset><legend>{claim.status==="NEEDS_RECEIPT"?"증빙 보완":"실제 지출 제출"}</legend>
    {claim.status==="NEEDS_RECEIPT"&&<p>재무팀이 요청한 증빙을 첨부하고 ‘첨부됨’을 확인한 뒤 다시 제출하세요.</p>}
    <label>실제 지출액(원)<input type="number" min="1" max="999999999999" required value={amount} disabled={busy}
      onChange={e=>setAmount(e.target.value)}/></label>
    <div className="attachment-fields"><label>증빙 종류<select value={type} disabled={busy} onChange={e=>setType(e.target.value)}>
      <option value="RECEIPT">지출 영수증</option><option value="ATTENDANCE">참석 확인서</option></select></label>
    <button type="button" disabled={busy} onClick={e=>{if(e.currentTarget.form?.reportValidity())void submit(true);}}>합성 증빙 첨부</button></div>
    {documents.map(row=><p key={row.evidenceType}>{row.evidenceType==="RECEIPT"?"지출 영수증":"참석 확인서"} · {won(row.receiptAmount)} 첨부됨</p>)}
    <p role="status">{message}</p>
    </fieldset>
    <button disabled={busy}>{claim.status==="NEEDS_RECEIPT"?"보완 내용 다시 제출":"정산 신청"}</button>
  </form>;
}
