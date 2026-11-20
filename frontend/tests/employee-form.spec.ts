import {expect,test} from "@playwright/test";

for (const mode of ["JSP","React"]) {
  test(mode+" 직원이 같은 신청 계약으로 교육비를 약정한다",async ({page})=>{
    await page.goto("/");
    await page.getByRole("button",{name:"개인 데모 시작"}).click();
    await expect(page.locator("#available")).toHaveText("1,000,000원");
    await page.getByLabel("신청명").fill("병원 교육 신청");
    if (mode==="React") {
      const switcher = page.getByRole("button",{name:"직원용 신청 화면으로 전환"});
      await switcher.focus();
      await page.keyboard.press("Enter");
    }
    const form = page.locator(mode==="React" ? "#employee-submit" : "#submit");
    const title = form.getByLabel("신청명");
    if (mode==="React") await expect(title).toBeFocused();
    await expect(title).toHaveValue("병원 교육 신청");
    await form.getByLabel("예정 금액(원)").fill("0");
    await form.getByRole("button").click();
    await expect(form.getByLabel("예정 금액(원)")).toBeFocused();
    await form.getByLabel("예정 금액(원)").fill("300000");
    await page.locator("#estimate").click();
    await expect(page.locator("#estimate-result")).toContainText("지원 예정 200,000원 · 본인 부담 100,000원 · 결재 경로 부서장");
    const csrf = await page.locator('meta[name="csrf-token"]').getAttribute("content");
    const role = async (value:string) => {
      const response = await page.request.post("/api/demo/role",{form:{role:value},headers:{"X-CSRF-TOKEN":csrf!}});
      expect(response.ok()).toBe(true);
    };
    await role("MANAGER");
    await title.press("Enter");
    await expect(page.locator("#notice")).toContainText("현재 역할로 처리할 수 없습니다.");
    await role("EMPLOYEE");
    const submitted = page.waitForResponse(r=>r.request().method()==="POST" && new URL(r.url()).pathname==="/api/claims");
    await title.press("Enter");
    const response = await submitted;
    expect(response.status()).toBe(200);
    const payload = new URLSearchParams(response.request().postData()!);
    expect(payload.get("expectedGeneration")).toBe("0");
    expect(payload.get("estimated")).toBe("300000");
    const {claimId} = await response.json();
    await expect(page.locator(".claim")).toContainText(claimId);
    await page.locator("#role").selectOption("MANAGER");
    await page.getByRole("button",{name:"사전승인"}).click();
    await expect(page.locator("#committed")).toHaveText("200,000원");
    await expect(page.locator("#available")).toHaveText("800,000원");
  });
}
