import { expect, test, type Locator, type Page } from "@playwright/test";

async function tabTo(page:Page, control:Locator) {
  await expect(control).toBeVisible();
  for (let steps=0;steps<90;steps++) {
    if (await control.evaluate(node=>node===document.activeElement)) return;
    await page.keyboard.press("Tab");
  }
  await expect(control).toBeFocused();
}

test("키보드로 결재하고 보완 사유 입력 후 같은 신청에서 재제출한다",async ({page})=>{
  await page.goto("/");
  const claim = page.locator(".claim");
  const heading = claim.locator("strong").first();
  async function pressButton(name:string) {
    await tabTo(page,page.getByRole("button",{name,exact:true}));
    await page.keyboard.press("Enter");
  }
  async function role(key:string,value:string) {
    const select = page.getByRole("combobox",{name:"시연 역할",exact:true});
    await tabTo(page,select);
    await page.keyboard.press(key);
    await expect(select).toHaveValue(value);
  }
  await pressButton("개인 데모 시작");
  await pressButton("신청 제출");
  await expect(claim).toContainText("결재 대기");
  await role("ArrowDown","MANAGER");
  await pressButton("사전승인");
  await expect(heading).toBeFocused();
  await expect(heading).toContainText("약정 완료");
  await role("Home","EMPLOYEE");
  await pressButton("정산 신청");
  await expect(heading).toContainText("정산 검토");
  await role("End","FINANCE");
  const reason = claim.getByRole("textbox",{name:"보완 사유"});
  await pressButton("보완 요청");
  await expect(reason).toBeFocused();
  await page.keyboard.insertText("   ");
  await page.keyboard.press("Enter");
  await expect(reason).toBeFocused();
  expect(await reason.evaluate(input=>(input as HTMLInputElement).validationMessage)).toBe("보완 사유를 입력하세요.");
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.insertText("교육비 영수증을 첨부하세요.");
  await page.keyboard.press("Enter");
  await expect(heading).toBeFocused();
  await expect(claim).toContainText("보완 사유 · 교육비 영수증을 첨부하세요.");
  await role("Home","EMPLOYEE");
  await pressButton("합성 증빙 첨부");
  await expect(heading).toBeFocused();
  await pressButton("정산 신청");
  await expect(heading).toContainText("정산 검토");
  await role("End","FINANCE");
  await pressButton("정산 확정");
  await expect(heading).toBeFocused();
  await expect(page.locator("#available")).toHaveText("820,000원");
});
