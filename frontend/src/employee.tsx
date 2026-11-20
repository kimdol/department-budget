import {useLayoutEffect, useRef} from "react";
import {createRoot} from "react-dom/client";

const original = document.querySelector<HTMLFormElement>("#submit")!;
const fields = new FormData(original);
const categories = Array.from(original.querySelector<HTMLSelectElement>("[name=category]")!.options,
  option => ({code:option.value, label:option.text}));

function EmployeeForm() {
  const title = useRef<HTMLInputElement>(null);
  useLayoutEffect(()=>{
    original.hidden = true;
    document.querySelector<HTMLElement>("#employee-view")!.hidden = true;
    title.current?.focus();
  },[]);
  return <form id="employee-submit">
    <label>신청명<input ref={title} name="title" maxLength={100} required defaultValue={String(fields.get("title"))}/></label>
    <label>비목<select name="category" required defaultValue={String(fields.get("category"))}>
      {categories.map(item=><option key={item.code} value={item.code}>{item.label}</option>)}
    </select></label>
    <label>예정 금액(원)<input name="estimated" type="number" min="1" required defaultValue={String(fields.get("estimated"))}/></label>
    <label>기간(일)<input name="days" type="number" min="1" max="365" required defaultValue={String(fields.get("days"))}/></label>
    <button>신청 제출</button>
  </form>;
}

createRoot(document.getElementById("employee-root")!).render(<EmployeeForm/>);
