import {Claim,won} from "./model";
export function ClaimList({claims,selected,select,busy,run}:{claims:Claim[];selected:string|undefined;
  select:(id:string)=>void;busy:boolean;run:(fn:()=>PromiseLike<unknown>)=>void}) {
  const desk=window.employeeInbox;
  return <div className="inbox-list"><label>상태로 찾기<select disabled={busy} value={desk.filter.value}
    onChange={e=>{const value=e.target.value;run(()=>desk.filterBy(value));}}>
    <option value="">전체 상태</option>{Object.entries(desk.labels).map(([key,name])=><option key={key} value={key}>{name}</option>)}</select></label>
    <ul>{claims.map(c=><li key={c.claimId}><button type="button" disabled={busy} id={"inbox-item-"+c.claimId} aria-current={selected===c.claimId?"true":undefined}
      onClick={()=>select(c.claimId)}><strong>{c.title}</strong><span>{desk.labels[c.status]} · {won(c.estimated)}</span></button></li>)}</ul>
    {!claims.length&&<p>{desk.filter.value?"이 상태의 신청이 없습니다.":"아직 신청한 비용이 없습니다."}</p>}
    {desk.newer&&<button disabled={busy} onClick={()=>run(()=>desk.page(false))}>이전 페이지</button>}
    {desk.older&&<button disabled={busy} onClick={()=>run(()=>desk.page(true))}>다음 페이지</button>}
  </div>;
}
