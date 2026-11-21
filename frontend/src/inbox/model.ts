export interface Claim {
  claimId:string; title:string; category:string; status:string;
  estimated:number; approved:number; actual:number|null; settled:number; reviewRound:number; reviewNote:string|null;
}

export interface Snapshot {
  role:string; memberId:string; claims:Claim[]; balance:{available:number};
}

export interface InboxBridge {
  state:Snapshot|null;
  labels:Record<string,string>;
  nextSteps:Record<string,string>;
  filter:HTMLSelectElement;
  older:boolean;
  newer:boolean;
  page:(older:boolean)=>PromiseLike<unknown>;
  filterBy:(value:string)=>PromiseLike<unknown>;
  refresh:()=>PromiseLike<unknown>;
}

declare global { interface Window { employeeInbox:InboxBridge } }

export const won=(value:number)=>new Intl.NumberFormat("ko-KR").format(value)+"원";
export interface Evidence {evidenceType:string;receiptAmount:number}
export async function claimRequest<T>(id:string,route:string,fields?:Record<string,string>):Promise<T> {
  const url=new URL("/api/claims/"+encodeURIComponent(id)+"/"+route,location.origin);
  const department=new URL(location.href).searchParams.get("department");
  if(department) url.searchParams.set("department",department);
  const response=await fetch(url,{method:fields?"POST":"GET",signal:AbortSignal.timeout(15000),
    headers:fields?{"X-CSRF-TOKEN":document.querySelector<HTMLMetaElement>('meta[name="csrf-token"]')!.content}:{},
    body:fields?new URLSearchParams(fields):undefined});
  if(!response.ok) throw new Error("HTTP "+response.status);
  return fields?undefined as T:await response.json() as T;
}
