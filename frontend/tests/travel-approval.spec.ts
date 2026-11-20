import { expect, test } from "@playwright/test";

test("출장 지원액 30만 원은 재무의 최종 사전승인에서 약정한다", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button",{name:"개인 데모 시작"}).click();
  const form = page.locator("#submit");
  await form.getByLabel("비목").selectOption("TRAVEL");
  await form.getByLabel("기간(일)").fill("6");
  await page.getByRole("button",{name:"지원 예정액 확인"}).click();
  await expect(page.locator("#estimate-result")).toContainText("부서장 → 재무");
  await form.getByRole("button",{name:"신청 제출"}).click();
  await expect(page.locator(".claim")).toContainText("결재 대기");
  await page.locator("#role").selectOption("MANAGER");
  await page.getByRole("button",{name:"재무로 결재 요청"}).click();
  await expect(page.locator(".claim")).toContainText("재무 사전승인 대기");
  await expect(page.locator("#committed")).toHaveText("0원");
  await page.locator("#role").selectOption("FINANCE");
  await page.getByRole("button",{name:"최종 사전승인"}).click();
  await expect(page.locator("#committed")).toHaveText("300,000원");
  await expect(page.locator("#available")).toHaveText("700,000원");
});
