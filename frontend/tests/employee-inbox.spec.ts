import {expect,test} from "@playwright/test";

for(const [width,height] of [[390,844],[768,1024],[1440,900]]){
  test(width+"px 직원 신청함에서 증빙 보완 후 결과를 조회한다",async({page})=>{
    await page.setViewportSize({width,height});
    await page.goto("/");
    await page.getByRole("button",{name:"내 신청함 시작"}).click();
    const inbox=page.locator("#employee-inbox");
    const detail=inbox.getByRole("article",{name:"신청 상세"});
    const role=page.getByRole("combobox",{name:"시연 역할"});
    async function openClaim(){
      const row=inbox.getByRole("button",{name:/직무 교육 참가/});
      if(await row.isVisible()){await row.focus();await row.press("Enter");}
      await expect(detail).toBeVisible();
    }
    const submit=page.locator("#employee-submit button");
    await expect(submit).toBeVisible();
    if(width===390)await page.route("**/api/claims",async route=>{
      expect((await route.fetch()).status()).toBe(200);
      await route.abort("connectionreset");
    },{times:1});
    await submit.click();
    await expect(inbox.getByRole("button",{name:/직무 교육 참가/})).toHaveCount(1);
    await expect(inbox.getByRole("heading",{name:"신청 목록"})).toBeVisible();
    await expect(inbox.getByRole("button",{name:/직무 교육 참가/})).toContainText("다음 담당자 · 부서장");
    await role.selectOption("MANAGER");
    await page.getByRole("button",{name:"사전승인",exact:true}).click();
    await expect(page.locator("#committed")).toHaveText("200,000원");
    await role.selectOption("EMPLOYEE");
    await expect(inbox).toBeVisible();await openClaim();
    await detail.getByRole("button",{name:"정산 신청",exact:true}).click();
    await expect(detail).toContainText("정산 검토");
    await role.selectOption("FINANCE");
    await page.getByLabel("보완 사유").fill("지출 영수증을 첨부해 주세요.");
    await page.getByRole("button",{name:"보완 요청",exact:true}).click();
    await expect(page.locator("#claims")).toContainText("지출 영수증을 첨부해 주세요.");
    await role.selectOption("EMPLOYEE");
    await expect(inbox).toBeVisible();await openClaim();
    await expect(detail).toContainText("보완 사유");
    await expect(detail).toContainText("‘첨부됨’을 확인한 뒤");
    let release!:()=>void;
    const gate=new Promise<void>(resolve=>{release=resolve;});
    await page.route("**/evidence",async route=>{
      if(route.request().method()!=="POST")return route.fallback();
      await gate;
      expect((await route.fetch()).status()).toBe(200);
      await route.abort("connectionreset");
    });
    const attach=detail.getByRole("button",{name:"합성 증빙 첨부"});
    await attach.focus();await attach.press("Enter");
    try {await expect(attach).toBeDisabled();} finally {release();}
    await expect(detail).toContainText("지출 영수증 · 225,000원 첨부됨");
    await expect(detail.getByLabel("실제 지출액(원)")).toHaveValue("225000");
    await page.route("**/review",async route=>{
      expect((await route.fetch()).status()).toBe(200);
      await route.abort("connectionreset");
    },{times:1});
    await detail.getByRole("button",{name:"보완 내용 다시 제출"}).click();
    await expect(detail).toContainText("정산 검토");
    await role.selectOption("FINANCE");
    await page.getByRole("button",{name:"정산 확정",exact:true}).click();
    await expect(page.locator("#available")).toHaveText("820,000원");
    await role.selectOption("EMPLOYEE");await expect(inbox).toBeVisible();
    if(width<833 && await inbox.getByRole("button",{name:"목록으로 돌아가기"}).isVisible())
      await inbox.getByRole("button",{name:"목록으로 돌아가기"}).click();
    let resumeFilter!:()=>void;
    const waitingForFilter=new Promise<void>(resolve=>{resumeFilter=resolve;});
    await page.route("**/api/workspace?*status=SETTLED*",async route=>{
      await waitingForFilter;
      await route.continue();
    },{times:1});
    await inbox.getByLabel("상태로 찾기").selectOption("SETTLED");
    try {await expect(inbox.getByRole("button",{name:/직무 교육 참가/})).toBeDisabled();} finally {resumeFilter();}
    await expect(inbox.getByRole("button",{name:/직무 교육 참가.*정산 완료/})).toBeEnabled();
    const compact=width<833;
    const settledRow=inbox.getByRole("button",{name:/직무 교육 참가.*정산 완료/});
    if(compact)await settledRow.scrollIntoViewIfNeeded();
    const listPosition=await page.evaluate(()=>window.scrollY);
    await openClaim();
    await expect(detail).toContainText("180,000원");
    await expect(detail).toContainText("820,000원");
    expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    if(compact){
      expect(listPosition).toBeGreaterThan(0);
      await inbox.getByRole("button",{name:"목록으로 돌아가기"}).click();
      await expect(inbox.getByLabel("상태로 찾기")).toHaveValue("SETTLED");
      await expect(inbox.getByRole("button",{name:/직무 교육 참가/})).toBeFocused();
      await expect.poll(()=>page.evaluate(()=>window.scrollY)).toBe(listPosition);
    }
    const history=inbox.getByRole("link",{name:"신청 변경·철회 및 전체 이력"});
    await history.focus();
    await expect(history).toBeFocused();
    await history.press("Enter");
    await expect(page).toHaveURL(/view=classic/);
  });
}

test("직원 모듈을 늦게 열어도 먼저 접수한 신청이 목록에 나타난다",async({page})=>{
  let release!:()=>void;
  const gate=new Promise<void>(resolve=>{release=resolve;});
  await page.route("**/employee.js",async route=>{await gate;await route.continue();},{times:1});
  await page.goto("/");
  await page.getByRole("button",{name:"내 신청함 시작"}).click();
  try {
    await page.locator("#submit button").click();
    await expect(page.locator("#submit-status")).toContainText("접수됐습니다");
    await expect(page.locator("#claims")).toContainText("직무 교육 참가");
  } finally {release();}
  const inbox=page.locator("#employee-inbox");
  await expect(inbox.getByRole("button",{name:/직무 교육 참가.*결재 대기/})).toHaveCount(1);
  await expect(inbox.getByText("아직 신청한 비용이 없습니다.")).toHaveCount(0);
});
