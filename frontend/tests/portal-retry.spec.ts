import { expect, test } from "@playwright/test";
import { rename } from "node:fs/promises";

test("포털 장애 복구 후 재무 담당자가 원래 승인 메시지를 다시 보낸다", async ({page}) => {
  const receiver = process.env.TEST_PORTAL_RECEIVER;
  test.skip(!receiver);
  await page.goto("/");
  await page.getByRole("button", {name:"개인 데모 시작"}).click();
  await page.getByRole("button", {name:"신청 제출"}).click();
  const claim = page.locator(".claim");
  const id = await claim.locator(".claim-number").innerText();
  await page.locator("#role").selectOption("MANAGER");
  await rename(receiver!, receiver! + ".offline");
  try {
    await claim.getByRole("button", {name:"사전승인"}).click();
    await expect(page.locator("#committed")).toHaveText("200,000원");
    await page.locator("#role").selectOption("FINANCE");
    await expect(async () => {
      await page.getByRole("button", {name:"포털 송신 실패 조회"}).click();
      await expect(page.locator("#portal-failures")).toContainText("포털 HTTP 404", {timeout:500});
    }).toPass({timeout:15000});
    await expect(page.locator("#portal-failures")).toContainText(id);
  } finally {
    await rename(receiver! + ".offline", receiver!);
  }
  const response = page.waitForResponse(r => r.url().endsWith("/retry"));
  await page.getByRole("button", {name:"다시 보내기"}).click();
  expect((await response).status()).toBe(200);
  await expect(page.locator("#portal-failures")).toContainText("재처리할 송신 실패가 없습니다.");
  await claim.getByRole("button", {name:"사내 포털 수신 결과"}).click();
  await expect(claim).toContainText("200,000원");
  await expect(claim).toContainText("포털 수신 차수 1");
  await expect(page.locator("#committed")).toHaveText("200,000원");
});
