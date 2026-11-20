import { expect, test } from "@playwright/test";

test.use({viewport:{width:360,height:800},isMobile:true,hasTouch:true});

test("모바일에서 출장 결재 담당자를 확인하고 재무 승인으로 30만 원을 약정한다", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button",{name:"개인 데모 시작"}).click();
  const form = page.locator("#submit");
  await form.getByLabel("비목").selectOption("TRAVEL");
  await form.getByLabel("기간(일)").fill("6");
  await page.getByRole("button",{name:"지원 예정액 확인"}).click();
  await expect(page.locator("#estimate-result")).toContainText("부서장 → 재무");
  await form.getByRole("button",{name:"신청 제출"}).click();
  await expect(page.locator(".claim")).toContainText("결재 대기");
  await expect(page.locator(".claim")).toContainText("다음 담당자 · 부서장 · 사전 결재");
  await page.locator("#role").selectOption("MANAGER");
  await page.getByRole("button",{name:"재무로 결재 요청"}).click();
  await expect(page.locator(".claim")).toContainText("재무 사전승인 대기");
  await page.locator("#role").selectOption("EMPLOYEE");
  await expect(page.locator(".claim")).toContainText("다음 담당자 · 재무 담당자 · 최종 사전승인");
  await expect(page.locator("#committed")).toHaveText("0원");
  await page.locator("#role").selectOption("FINANCE");
  await page.getByRole("button",{name:"최종 사전승인"}).click();
  await expect(page.locator("#committed")).toHaveText("300,000원");
  await expect(page.locator("#available")).toHaveText("700,000원");
  await page.locator("#role").selectOption("EMPLOYEE");
  await expect(page.locator("#member-name")).toContainText("STAFF_01");
  const next = page.getByText("다음 담당자 · 신청 직원 · 실제 지출 제출",{exact:true});
  await next.scrollIntoViewIfNeeded();
  await expect(next).toBeInViewport({ratio:1});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
});
