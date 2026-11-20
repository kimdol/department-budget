import { expect, test } from "@playwright/test";
import { mkdir, readFile } from "node:fs/promises";
import path from "node:path";

test("두 방문자가 별도 예산으로 교육비 신청과 월 결산을 체험한다", async ({ browser, page, baseURL }) => {
  const second = await browser.newContext({ baseURL, viewport: { width: 390, height: 844 } });
  const other = await second.newPage();
  try {
    for (const visit of [page, other]) {
      await visit.goto("/");
      await visit.getByRole("button", { name: "개인 데모 시작" }).click();
      await expect(visit.locator("#available")).toHaveText("1,000,000원");
    }
    await page.getByRole("button", { name: "지원 예정액 확인" }).click();
    await expect(page.locator("#estimate-result")).toContainText("지원 예정 200,000원 · 본인 부담 100,000원");
    await page.getByRole("button", { name: "신청 제출" }).click();
    const claim = page.locator(".claim");
    await expect(claim).toContainText("결재 대기");
    const number = await claim.locator("small").innerText();
    await page.locator("#role").selectOption("MANAGER");
    await claim.getByRole("button", { name: "사전승인" }).click();
    await expect(page.locator("#committed")).toHaveText("200,000원");
    await page.locator("#role").selectOption("EMPLOYEE");
    const uploaded = page.waitForResponse(r => r.url().endsWith("/evidence") && r.request().method() === "POST");
    await claim.getByRole("button", { name: "증빙 요건 확인" }).click();
    await expect(claim).toContainText("지출 영수증이 필요합니다.");
    await claim.getByText("판정 근거", { exact:true }).click();
    await expect(claim.getByText("영수증 제출 · 자료 부족", { exact:true })).toBeVisible();
    await expect(claim.getByText("참석 확인서 제출 · 앞선 조건으로 판정되어 생략", { exact:true })).toBeVisible();
    await claim.getByRole("button", { name: "합성 증빙 첨부" }).click();
    expect((await uploaded).ok()).toBe(true);
    await claim.getByRole("button", { name: "정산 신청" }).click();
    await expect(claim).toContainText("정산 검토");
    await claim.getByRole("button", { name: "증빙 요건 확인" }).click();
    await expect(claim).toContainText("필요한 증빙을 모두 제출했습니다.");
    await page.locator("#role").selectOption("FINANCE");
    await claim.getByRole("button", { name: "증빙 조회" }).click();
    const receiptLink = claim.getByRole("link", { name: "지출 영수증 · 225,000원" });
    const receiptUrl = await receiptLink.getAttribute("href");
    expect((await other.request.get(receiptUrl!)).status()).toBe(404);
    const downloading = page.waitForEvent("download");
    await receiptLink.click();
    const download = await downloading;
    expect(download.suggestedFilename()).toBe("expense-document.txt");
    expect(await readFile((await download.path())!, "utf8")).toContain("기재 금액 225000원");
    await claim.getByRole("button", { name: "정산 확정" }).click();
    await expect(page.locator("#available")).toHaveText("820,000원");
    await expect(page.locator("#spent")).toHaveText("180,000원");
    if (process.env.TEST_PHP_ADDRESS) {
      await expect(async () => {
        await claim.getByRole("button",{name:"사내 포털 수신 결과"}).click();
        await expect(claim).toContainText("포털 수신 차수 2 · 정산 확정 · 180,000원",{timeout:1000});
      }).toPass({timeout:15000});
    }
    await expect(page.locator("#committed")).toHaveText("0원");
    await expect(claim.locator("small")).toHaveText(number);
    await claim.getByRole("button", { name: "신청 당시 계산 근거" }).click();
    await expect(claim).toContainText("평가기 decimal-v1");
    await expect(claim).toContainText("실제 지출 225,000원 → 인정 180,000원");
    await page.getByRole("button", { name: "결산표 조회" }).click();
    await expect(page.locator("#statement")).toContainText("교육비 · 1건 · 180,000원");
    await claim.getByRole("button", { name: "전표 조회" }).click();
    await expect(claim).toContainText("집행 증감 180,000원");
    if (process.env.CAPTURE_DEMO === "1") {
      const folder = path.resolve(__dirname, "../../docs/images");
      await mkdir(folder, { recursive: true });
      await page.screenshot({ path: path.join(folder, "settlement.png"), fullPage: true });
    }
    page.once("dialog", dialog => dialog.accept());
    await page.getByRole("button", { name: "이 기간 마감 확정" }).click();
    await page.getByText("구 결산표 CSV 대조", {exact:true}).click();
    const csv = page.getByLabel("비목별 월 집계 CSV");
    const period = (await page.locator("#statement strong").innerText()).slice(0,7);
    const row = "period,category,count,spent\n"+period+",EDUCATION,1,";
    await csv.fill(row);
    await page.getByRole("button", {name:"확정 결산표와 비교"}).click();
    await expect(page.locator("#statement")).toContainText("금액 누락 / 확정 결산표 1건 · 180,000원");
    await csv.fill(row+"180000");
    await page.getByRole("button", {name:"확정 결산표와 비교"}).click();
    await expect(page.locator("#statement")).toContainText("모든 비목의 건수와 집행액이 일치합니다.");
    await page.getByRole("button", { name: "다음 달 열기" }).click();
    await expect(page.locator("#statement")).toContainText("마감 전");
    await claim.getByRole("button", { name: "전표 조회" }).click();
    await claim.getByLabel(/환입 금액/).fill("30000");
    page.once("dialog", dialog => dialog.accept());
    await claim.getByRole("button", { name: "정정 전표 발행" }).click();
    await expect(page.locator("#available")).toHaveText("850,000원");
    await claim.getByRole("button", { name: "전표 조회" }).click();
    await claim.getByRole("button", { name: "원래 기간 결산표" }).click();
    await expect(claim).toContainText("마감 당시 가용 예산 820,000원");
    await other.reload();
    await expect(other.locator("#available")).toHaveText("1,000,000원");
    await expect(other.locator("#claims")).toHaveText("아직 신청한 비용이 없습니다.");
    await other.locator('[name="title"]').fill("다른 방문자의 교육 신청");
    await other.getByRole("button", { name: "신청 제출" }).click();
    await expect(other.locator(".claim")).toContainText("다른 방문자의 교육 신청");
    page.once("dialog", dialog => dialog.accept());
    await page.getByRole("button", { name: "내 체험 종료" }).click();
    await page.getByRole("button", { name: "개인 데모 시작" }).click();
    await expect(page.locator("#available")).toHaveText("1,000,000원");
    await expect(page.locator("#claims")).toHaveText("아직 신청한 비용이 없습니다.");
    await other.reload();
    await expect(other.locator(".claim")).toContainText("다른 방문자의 교육 신청");
  } finally {
    await second.close();
  }
});
