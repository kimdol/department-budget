"use strict";

const budgetForm = document.getElementById("personal-budget");
const expenseForm = document.getElementById("expense");
const projection = document.getElementById("projection");
const money = value => value.toLocaleString("ko-KR")+"원";
const message = value => document.getElementById("personal-status").textContent = value;
const openLedger = new Promise((resolve,reject)=>{
  const request = indexedDB.open("personal-learning-ledger",1);
  request.onupgradeneeded = ()=>{
    request.result.createObjectStore("settings");
    request.result.createObjectStore("expenses",{keyPath:"id",autoIncrement:true});
  };
  request.onsuccess = ()=>resolve(request.result);
  request.onerror = ()=>reject(request.error);
});
const changes = typeof BroadcastChannel === "function" ? new BroadcastChannel("personal-learning-ledger") : null;
let balance = 0n;
let projected = false;
let revision = 0;

function calculate() {
  const fields = projection.elements;
  const cost = BigInt(fields.cost.valueAsNumber);
  const fraction = cost * BigInt(fields.rate.valueAsNumber) / 100n;
  const cap = BigInt(fields.cap.valueAsNumber);
  const support = fraction < cap ? fraction : cap;
  const paid = cost-support;
  document.getElementById("forecast").textContent = "지원 예정 "+money(support)+" · 본인 부담 "+money(paid)+" · 지출 후 잔액 "+money(balance-paid);
}

async function refreshLedger() {
  const sequence = ++revision;
  const db = await openLedger;
  const snapshot = db.transaction(["settings","expenses"]);
  const budget = snapshot.objectStore("settings").get("budget");
  const expenses = snapshot.objectStore("expenses").getAll();
  await finished(snapshot);
  if (revision !== sequence) return;
  const total = BigInt(budget.result ?? 0);
  const paid = expenses.result.reduce((sum,item)=>sum+BigInt(item.amount)-BigInt(item.support),0n);
  balance = total-paid;
  if (document.activeElement !== budgetForm.elements.budget) budgetForm.elements.budget.value = total.toString();
  document.getElementById("budget-total").textContent = money(total);
  document.getElementById("paid-total").textContent = money(paid);
  document.getElementById("remaining").textContent = money(balance);
  const list = document.getElementById("expenses");
  list.replaceChildren();
  for (const item of expenses.result.sort((a,b)=>b.date.localeCompare(a.date) || b.id-a.id)) {
    const row = document.createElement("li");
    row.textContent = item.date+" · "+item.category+" · "+item.title+" · 지출 "+money(item.amount)+" · 지원 "+money(item.support)+" ";
    const remove = document.createElement("button");
    remove.textContent = "삭제";
    remove.setAttribute("aria-label",item.title+" 삭제");
    remove.onclick = ()=>{
      if (window.confirm(item.title+" 기록을 삭제할까요?")) save(remove,"expenses",store=>store.delete(item.id),"지출을 삭제했습니다.");
    };
    row.append(remove);
    list.append(row);
  }
  if (projected && projection.checkValidity()) calculate();
}

function finished(transaction) {
  return new Promise((resolve,reject)=>{
    transaction.oncomplete = resolve;
    transaction.onabort = ()=>reject(transaction.error);
  });
}

async function save(button,storeName,write,success) {
  if (button.disabled) return;
  button.disabled = true;
  try {
    const db = await openLedger;
    const transaction = db.transaction(storeName,"readwrite");
    write(transaction.objectStore(storeName));
    await finished(transaction);
    changes?.postMessage("updated");
    await refreshLedger();
    message(success);
    return true;
  } catch {
    message("장부를 저장할 수 없습니다. 이 브라우저의 사이트 저장 공간을 확인하세요.");
  } finally {
    button.disabled = false;
  }
}

budgetForm.onsubmit = event=>{
  event.preventDefault();
  const budget = budgetForm.elements.budget.valueAsNumber;
  save(budgetForm.querySelector("button"),"settings",store=>store.put(budget,"budget"),"개인 예산을 저장했습니다.");
};
expenseForm.onsubmit = async event=>{
  event.preventDefault();
  const fields = expenseForm.elements;
  if (fields.support.valueAsNumber > fields.amount.valueAsNumber || !fields.title.value.trim()) {
    message("항목명을 입력하고 지원액을 지출액 이하로 설정하세요.");
    return;
  }
  const item = {date:fields.date.value,category:fields.category.value,title:fields.title.value.trim(),
    amount:fields.amount.valueAsNumber,support:fields.support.valueAsNumber};
  if (await save(expenseForm.querySelector("button"),"expenses",store=>store.add(item),"지출을 저장했습니다.")) {
    fields.title.value = "";
    fields.amount.value = "";
    fields.support.value = "0";
  }
};
projection.onsubmit = event=>{event.preventDefault(); projected = true; calculate();};
projection.oninput = ()=>{projected = false; document.getElementById("forecast").textContent = "";};
const today = new Date();
expenseForm.elements.date.value = new Date(today.getTime()-today.getTimezoneOffset()*60000).toISOString().slice(0,10);
function reloadLedger() {
  refreshLedger().catch(()=>message("이 브라우저에서 개인 장부를 열 수 없습니다."));
}
if (changes) changes.onmessage = reloadLedger;
window.addEventListener("focus",reloadLedger);
reloadLedger();
