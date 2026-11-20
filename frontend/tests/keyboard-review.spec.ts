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
  const actual = claim.getByRole("spinbutton",{name:"실제 지출액(원)"});
  await tabTo(page,actual);
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.insertText("125000");
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
  await expect(actual).toHaveValue("125000");
  await tabTo(page,actual);
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.insertText("150000");
  await pressButton("합성 증빙 첨부");
  await expect(heading).toBeFocused();
  await expect(actual).toHaveValue("150000");
  await pressButton("정산 신청");
  await expect(heading).toContainText("정산 검토");
  await role("End","FINANCE");
  await pressButton("정산 확정");
  await expect(heading).toBeFocused();
  await expect(page.locator("#available")).toHaveText("880,000원");
});
for (const revise of [false,true]) test(revise ? "같은 비목의 변경 전후 금액과 기간을 최초 번호로 조회한다" : "보완 요청에서 비목을 바꾸면 두 신청의 이력이 서로 연결된다",async ({page})=>{
  await page.goto("/");
  await page.locator("#start").click();
  await page.getByRole("button",{name:"신청 제출"}).click();
  const first = page.locator(".claim").first();
  const original = await first.locator("small").innerText();
  await page.locator("#role").selectOption("MANAGER");
  await first.getByRole("button",{name:"사전승인"}).click();
  await expect(page.locator("#committed")).toHaveText("200,000원");
  await page.locator("#role").selectOption("EMPLOYEE");
  await first.getByRole("button",{name:"정산 신청"}).click();
  await expect(first).toContainText("정산 검토");
  await page.locator("#role").selectOption("FINANCE");
  await first.getByLabel("보완 사유").fill("출장비로 재신청하세요.");
  await first.getByRole("button",{name:"보완 요청"}).click();
  await expect(first).toContainText("출장비로 재신청하세요.");
  await page.locator("#role").selectOption("EMPLOYEE");
  await first.getByText("신청 내용 변경",{exact:true}).click();
  await expect(first.getByLabel("변경 기간(일)")).toHaveValue("1");
  if (revise) await first.getByLabel("변경 신청명").fill("심화 교육");
  else await first.getByLabel("변경 비목").selectOption("TRAVEL");
  await first.getByLabel("변경 예정 금액(원)").fill("225000");
  await first.getByLabel("변경 기간(일)").fill("3");
  await first.getByRole("button",{name:revise ? "변경 버전 재상신" : "원신청 철회 후 재신청"}).click();
  await expect(page.locator(".claim")).toHaveCount(2);
  await expect(page.locator("#committed")).toHaveText("0원");
  const replacement = await first.locator("small").innerText();
  expect(replacement).not.toBe(original);
  await first.getByRole("button",{name:"결재 이력"}).click();
  await expect(first).toContainText("원신청 "+original);
  const previous = page.locator(".claim").last();
  await previous.getByRole("button",{name:"결재 이력"}).click();
  await expect(previous).toContainText("새 신청 "+replacement);
  if (revise) {
    await previous.getByRole("button",{name:"신청 버전 조회"}).click();
    await expect(previous).toContainText("최초 신청번호 "+original);
    await expect(previous).toContainText("버전 1 · 직무 교육 참가 · 300,000원 · 1일");
    await expect(previous).toContainText("버전 2 · 심화 교육 · 225,000원 · 3일");
  }
  await page.locator("#role").selectOption("MANAGER");
  await first.getByRole("button",{name:"사전승인"}).click();
  await expect(page.locator("#available")).toHaveText(revise ? "820,000원" : "850,000원");
});
