import { expect, test } from "@playwright/test";
import { mkdir } from "node:fs/promises";
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
    await page.getByRole("button", { name: "신청 제출" }).click();
    const claim = page.locator(".claim");
    await expect(claim).toContainText("결재 대기");
    const number = await claim.locator("small").innerText();
    await page.locator("#role").selectOption("MANAGER");
    await claim.getByRole("button", { name: "사전승인" }).click();
    await expect(page.locator("#committed")).toHaveText("200,000원");
    await page.locator("#role").selectOption("EMPLOYEE");
    const uploaded = page.waitForResponse(r => r.url().endsWith("/evidence") && r.request().method() === "POST");
    await claim.getByRole("button", { name: "합성 증빙 첨부" }).click();
    expect((await uploaded).ok()).toBe(true);
    await claim.getByRole("button", { name: "정산 신청" }).click();
    await expect(claim).toContainText("정산 검토");
    await page.locator("#role").selectOption("FINANCE");
    await claim.getByRole("button", { name: "정산 확정" }).click();
    await expect(page.locator("#available")).toHaveText("820,000원");
    await expect(page.locator("#spent")).toHaveText("180,000원");
    await expect(page.locator("#committed")).toHaveText("0원");
    await expect(claim.locator("small")).toHaveText(number);
    await page.getByRole("button", { name: "결산표 조회" }).click();
    await expect(page.locator("#statement")).toContainText("교육비 · 1건 · 180,000원");
    await claim.getByRole("button", { name: "전표 조회" }).click();
    await expect(claim).toContainText("집행 증감 180,000원");
    if (process.env.CAPTURE_DEMO === "1") {
      const folder = path.resolve(__dirname, "../../docs/images");
      await mkdir(folder, { recursive: true });
      await page.screenshot({ path: path.join(folder, "settlement.png"), fullPage: true });
    }
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
