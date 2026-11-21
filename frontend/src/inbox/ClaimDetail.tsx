import {Claim,won} from "./model";
import {SettlementForm} from "./SettlementForm";

export function ClaimDetail({claim,available}:{claim:Claim;available:number}) {
  const {labels,nextSteps}=window.employeeInbox;
  return <article className="claim inbox-detail" aria-label="신청 상세">
    <strong role="status">{claim.title} · {labels[claim.status]}</strong>
    <small>신청번호 <span className="claim-number">{claim.claimId}</span></small>
    {nextSteps[claim.status]&&<p>다음 담당자 · {nextSteps[claim.status]}</p>}
    {claim.status==="NEEDS_RECEIPT"&&<p className="review-note"><b>보완 사유</b><br/>{claim.reviewNote}</p>}
    <dl><dt>신청 금액</dt><dd>{won(claim.estimated)}</dd><dt>승인 금액</dt><dd>{won(claim.approved)}</dd>
    {claim.actual!==null&&<><dt>실제 지출액</dt><dd>{won(claim.actual)}</dd></>}
    {claim.status==="SETTLED"&&<><dt>정산 금액</dt><dd>{won(claim.settled)}</dd><dt>본인 부담</dt><dd>{won((claim.actual??0)-claim.settled)}</dd>
      <dt>현재 부서 가용 예산</dt><dd>{won(available)}</dd></>}</dl>
    {["APPROVED","NEEDS_RECEIPT"].includes(claim.status)&&<SettlementForm key={claim.claimId+"/"+claim.reviewRound} claim={claim}/>}
  </article>;
}
