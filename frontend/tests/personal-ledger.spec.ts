import {expect,test} from "@playwright/test";

test("두 탭에서 동시에 기록한 지출을 보존하고 선택한 기록만 삭제한다",async ({page,context})=>{
  const other = await context.newPage();
  await Promise.all([page.goto("/personal"),other.goto("/personal")]);
  await page.getByLabel("개인 예산(원)").fill("100000");
  await page.getByRole("button",{name:"예산 저장"}).click();
  await expect(other.locator("#remaining")).toHaveText("100,000원");
  for (const [tab,title,amount] of [[page,"교육 교재","30000"],[other,"시험 접수","40000"]] as const) {
    await tab.getByLabel("항목명").fill(title);
    await tab.getByLabel("지출액(원)",{exact:true}).fill(amount);
  }
  await Promise.all([page,other].map(tab=>tab.getByRole("button",{name:"지출 저장"}).click()));
  for (const tab of [page,other]) {
    await expect(tab.locator("#expenses li")).toHaveCount(2);
    await expect(tab.locator("#remaining")).toHaveText("30,000원");
  }
  page.once("dialog",dialog=>dialog.accept());
  await page.getByRole("button",{name:"교육 교재 삭제"}).click();
  await expect(other.locator("#expenses li")).toHaveCount(1);
  await expect(other.locator("#expenses")).toContainText("시험 접수");
  await other.reload();
  await expect(other.locator("#remaining")).toHaveText("60,000원");
});

test("개인 학습 지출이 브라우저에 보존되고 부서 예산과 분리된다",async ({page,browser,baseURL})=>{
  await page.goto("/");
  await page.getByRole("link",{name:"내 자격시험·교육·도서 장부"}).click();
  await expect(page.locator("#remaining")).toHaveText("0원");
  const requests:string[] = [];
  page.on("request",req=>requests.push(req.method()+" "+req.url()));
  await page.getByLabel("개인 예산(원)").fill("500000");
  await page.getByRole("button",{name:"예산 저장"}).click();
  await expect(page.locator("#remaining")).toHaveText("500,000원");
  await page.getByLabel("항목명").fill("자격시험 응시료");
  await page.getByLabel("지출액(원)",{exact:true}).fill("70000");
  await page.getByLabel("실제 지원액(원)").fill("20000");
  await page.getByRole("button",{name:"지출 저장"}).click();
  await expect(page.locator("#remaining")).toHaveText("450,000원");
  await page.getByLabel("교육비(원)").fill("300000");
  await page.getByRole("button",{name:"예상 잔액 계산"}).click();
  await expect(page.locator("#forecast")).toHaveText("지원 예정 200,000원 · 본인 부담 100,000원 · 지출 후 잔액 350,000원");
  expect(requests).toEqual([]);
  await page.reload();
  await expect(page.locator("#remaining")).toHaveText("450,000원");
  await expect(page.locator("#expenses")).toContainText("자격시험 응시료");
  const independent = await browser.newContext({baseURL});
  try {
    const other = await independent.newPage();
    await other.goto("/personal");
    await expect(other.locator("#remaining")).toHaveText("0원");
    await expect(other.locator("#expenses")).toBeEmpty();
  } finally {await independent.close();}
  await page.getByRole("link",{name:"부서 결재함"}).click();
  await page.locator("#start").click();
  await expect(page.locator("#available")).toHaveText("1,000,000원");
});
