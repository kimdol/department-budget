import {expect,test} from "@playwright/test";

test("규정 설명이 있는 견적도 기존 금액과 증빙을 유지하고 입력을 바꾸면 함께 지운다",async ({page})=>{
  await page.goto("/");
  await page.locator("#start").click();
  const estimate = page.locator("#estimate");
  const result = page.locator("#estimate-result");
  await estimate.click();
  await expect(result).toContainText("지출 영수증이 필요합니다.");
  const original = await result.innerText();
  await expect(result).not.toContainText("규정 설명");

  const explanation = "교육비에는 행사별 한도가 적용돼요. <b>계산 근거</b>";
  await page.route("**/api/claims/estimate?*",async route=>{
    const response = await route.fetch();
    expect(response.ok()).toBe(true);
    await route.fulfill({response,json:{...await response.json(),ruleExplanation:explanation}});
  },{times:1});
  await estimate.click();
  await expect(result).toHaveText(original + " 규정 설명 · " + explanation);
  await expect(result.locator("b")).toHaveCount(0);
  await page.locator('#submit [name="estimated"]').fill("150000");
  await expect(result).toBeEmpty();
  await estimate.click();
  await expect(result).toContainText("지원 예정 120,000원");
  await expect(result).not.toContainText("규정 설명");
});

test("역할 전환의 응답을 기다리는 동안 이전 업무 버튼을 잠근다",async ({page})=>{
  await page.goto("/");
  await page.locator("#start").click();
  await expect(page.locator("#available")).toHaveText("1,000,000원");
  let release!:()=>void;
  const gate = new Promise<void>(resolve=>{release=resolve;});
  await page.route("**/api/demo/role",async route=>{await gate; await route.continue();});
  await page.locator("#role").selectOption("MANAGER");
  try {
    await expect(page.locator("#role")).toBeDisabled();
    await expect(page.locator("#entry")).toHaveJSProperty("inert",true);
    await expect(page.locator("#claims").locator("..")).toHaveJSProperty("inert",true);
  } finally {
    release();
  }
  await expect(page.locator("#role")).toBeEnabled();
  await expect(page.locator("#entry")).toBeHidden();
  await expect(page.locator("#claims").locator("..")).toHaveJSProperty("inert",false);
});

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
    const submitStatus = page.locator("#submit-status");
    await expect(submitStatus).toHaveText("현재 역할로 처리할 수 없습니다.");
    await role("EMPLOYEE");
    let resumeSubmission!:()=>void;
    const submissionGate = new Promise<void>(resolve=>{resumeSubmission=resolve;});
    await page.route("**/api/claims",async route=>{await submissionGate; await route.continue();},{times:1});
    const submitted = page.waitForResponse(r=>r.request().method()==="POST" && new URL(r.url()).pathname==="/api/claims");
    await title.press("Enter");
    try {
      await expect(submitStatus).toHaveText("신청을 접수하고 있습니다.");
      await expect(form.getByRole("button")).toBeDisabled();
    } finally {
      resumeSubmission();
    }
    const response = await submitted;
    expect(response.status()).toBe(200);
    const payload = new URLSearchParams(response.request().postData()!);
    expect(payload.get("expectedGeneration")).toBe("0");
    expect(payload.get("estimated")).toBe("300000");
    const {claimId} = await response.json();
    await expect(submitStatus).toHaveText("신청이 접수됐습니다. 아래 신청 내역에서 확인하세요.");
    await expect(page.locator(".claim")).toContainText(claimId);
    await page.locator("#role").selectOption("MANAGER");
    await page.getByRole("button",{name:"사전승인"}).click();
    await expect(page.locator("#committed")).toHaveText("200,000원");
    await expect(page.locator("#available")).toHaveText("800,000원");
  });
}

test("목록을 불러오지 못해도 이미 접수된 신청을 안내한다",async ({page})=>{
  await page.goto("/");
  await page.getByRole("button", {name:"개인 데모 시작"}).click();
  await expect(page.locator("#available")).toHaveText("1,000,000원");
  await page.route("**/api/workspace",route=>route.fulfill({status:503}),{times:1});
  await page.getByRole("button", {name:"신청 제출"}).click();
  await expect(page.locator("#submit-status")).toHaveText("신청이 접수됐습니다. 화면을 새로 열어 신청 내역을 확인하세요.");
  await expect(page.getByRole("button",{name:"신청 제출"})).toBeEnabled();
  const response = await page.request.get("/api/workspace");
  expect(response.status()).toBe(200);
  const state = await response.json();
  expect(state.claims).toHaveLength(1);
  await page.reload();
  await expect(page.locator(".claim")).toContainText(state.claims[0].claimId);
});

test("부서장 대행도 본인의 신청을 승인할 수 없다",async ({page})=>{
  await page.goto("/");
  await page.locator("#start").click();
  await page.getByRole("button",{name:"신청 제출"}).click();
  await expect(page.locator(".claim")).toContainText("결재 대기");
  await page.locator("#role").selectOption("MANAGER");
  await page.getByRole("button",{name:"신청 직원의 부서장 대행 체험"}).click();
  await expect(page.locator("#member-name")).toContainText("STAFF_01 · 본인 신청 결재 제한");
  const attempt = page.waitForResponse(r=>r.url().endsWith("/approve"));
  await page.getByRole("button",{name:"사전승인"}).click();
  expect((await attempt).status()).toBe(403);
  await expect(page.locator("#committed")).toHaveText("0원");
  await page.getByRole("button",{name:"부서장으로 복귀"}).click();
  await expect(page.locator("#member-name")).toContainText("HEAD_01");
  await page.getByRole("button",{name:"사전승인"}).click();
  await expect(page.locator("#committed")).toHaveText("200,000원");
  await page.getByRole("button",{name:"결재 이력"}).click();
  await expect(page.locator(".claim")).toContainText("부서장 · 최종 사전승인 · 200,000원 · HEAD_01");
});
