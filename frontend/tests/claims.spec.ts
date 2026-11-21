import { expect, test } from "@playwright/test";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

test("두 방문자가 별도 예산으로 교육비 신청과 월 결산을 체험한다", async ({ browser, page, baseURL }) => {
  const expectPortal = Boolean(process.env.TEST_DEPLOYMENT_URL || process.env.TEST_PHP_ADDRESS);
  test.slow(expectPortal, "포털의 약정·정산 메시지가 차례로 전달될 시간을 확보한다.");
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
    let resumeRefresh!:()=>void;
    const refreshGate = new Promise<void>(resolve=>{resumeRefresh=resolve;});
    await page.route("**/api/workspace",async route=>{await refreshGate; await route.continue();},{times:1});
    await claim.getByRole("button", { name: "합성 증빙 첨부" }).click();
    try {
      await expect(claim).toHaveJSProperty("inert",true);
      expect((await uploaded).ok()).toBe(true);
    } finally {
      resumeRefresh();
    }
    await claim.getByRole("button", { name: "증빙 요건 확인" }).click();
    await expect(claim).toContainText("실제 지출액을 제출해야 영수증 금액을 대조할 수 있습니다.");
    await claim.getByText("판정 근거", {exact:true}).click();
    await expect(claim.getByText("실제 지출 대조 · 자료 부족", {exact:true})).toBeVisible();
    await claim.getByRole("button", { name: "정산 신청" }).click();
    await expect(claim).toContainText("정산 검토");
    await claim.getByRole("button", { name: "증빙 요건 확인" }).click();
    await expect(claim).toContainText("필요한 증빙을 모두 제출했습니다.");
    await page.locator("#role").selectOption("FINANCE");
    await claim.getByRole("button", { name: "증빙 조회" }).click();
    const receiptLink = claim.getByRole("link", { name: "지출 영수증 · 225,000원" });
    const receiptUrl = await receiptLink.getAttribute("href");
    expect((await other.request.get(receiptUrl!)).status()).toBe(404);
    const branch = await page.context().newPage();
    try {
      await branch.goto("/");
      await branch.getByLabel("체험 부서").selectOption("SECONDARY");
      await expect(branch.locator("#role")).toHaveValue("EMPLOYEE");
      await expect(branch.locator("#claims")).toHaveText("아직 신청한 비용이 없습니다.");
      await expect(branch.locator("#available")).toHaveText("1,000,000원");
      await branch.locator('[name="title"]').fill("총무팀 교육");
      await branch.getByRole("button", {name:"신청 제출"}).click();
      await expect(branch.locator(".claim")).toContainText("총무팀 교육");
      await branch.locator("#role").selectOption("MANAGER");
      await expect(branch.locator("#notice")).toContainText("사전승인");
      expect((await branch.request.get(receiptUrl!+"?department=SECONDARY")).status()).toBe(404);
      await branch.getByRole("button", {name:"사전승인"}).click();
      await expect(branch.locator("#committed")).toHaveText("200,000원");
      await expect(page.locator("#role")).toHaveValue("FINANCE");
    } finally {
      await branch.close();
    }
    const downloading = page.waitForEvent("download");
    await receiptLink.click();
    const download = await downloading;
    expect(download.suggestedFilename()).toBe("expense-document.txt");
    expect(await readFile((await download.path())!, "utf8")).toContain("기재 금액 225000원");
    const claimApi = "/api/claims/"+number.trim();
    async function getJson(url:string) {
      const response = await page.request.get(url);
      expect(response.status()).toBe(200);
      return response.json();
    }
    const settleButton = claim.getByRole("button", { name: "정산 확정" });
    let droppedResponseStatus: number | undefined;
    await page.route("**"+claimApi+"/settle", async route => {
      try {
        droppedResponseStatus = (await route.fetch()).status();
      } finally {
        await route.abort("failed");
      }
    }, {times:1});
    await settleButton.click();
    await expect(page.locator("#notice")).toContainText("처리 결과를 확인할 수 없습니다.");
    expect(droppedResponseStatus).toBe(200);
    const afterDroppedResponse = await getJson(claimApi+"/postings");
    expect(afterDroppedResponse).toHaveLength(2);
    await expect(settleButton).toBeEnabled();
    await settleButton.click();
    await expect(page.locator("#available")).toHaveText("820,000원");
    await expect(page.locator("#spent")).toHaveText("180,000원");
    await expect(claim).toContainText("제출한 실비 225,000원 · 정산 당시 본인 부담 45,000원 · 예산으로 돌아간 약정 차액 20,000원");
    await expect(page.locator("#committed")).toHaveText("0원");
    await expect(claim.locator("small")).toHaveText(number);
    let portalReceipt: {sequence:number;event:string;amount:number} | undefined;
    if (expectPortal) {
      const receiptButton = claim.getByRole("button",{name:"사내 포털 수신 결과"});
      await expect(receiptButton).toBeVisible();
      await expect(async () => {
        const [response] = await Promise.all([
          page.waitForResponse(result => new URL(result.url()).pathname === claimApi+"/portal-receipt",{timeout:8000}),
          receiptButton.click()
        ]);
        expect(response.status()).toBe(200);
        const receipt = await response.json();
        expect(receipt).toEqual({sequence:2,event:"SETTLEMENT",amount:180000});
        await expect(claim).toContainText("포털 수신 차수 2 · 정산 확정 · 180,000원",{timeout:1000});
        portalReceipt = receipt;
      }).toPass({timeout:45000});
    }
    const before = await getJson(claimApi+"/postings");
    expect(before).toEqual(afterDroppedResponse);
    expect(before).toEqual(expect.arrayContaining([
      expect.objectContaining({claimId:number.trim(),kind:"APPROVAL",committedDelta:200000,spentDelta:0}),
      expect.objectContaining({claimId:number.trim(),kind:"SETTLEMENT",committedDelta:-200000,spentDelta:180000})
    ]));
    const csrf = await page.locator('meta[name="csrf-token"]').getAttribute("content");
    const retry = await page.request.post(claimApi+"/settle",{headers:{"X-CSRF-TOKEN":csrf!},form:{round:1}});
    expect(retry.status()).toBe(200);
    const after = await getJson(claimApi+"/postings");
    expect(after).toEqual(before);
    const statement = await getJson("/api/statement");
    const balance = (await getJson("/api/workspace")).balance;
    expect(balance).toEqual({allocation:1000000,committed:0,spent:180000,available:820000});
    expect(statement.balance).toEqual(balance);
    expect(statement.lines).toContainEqual({category:"EDUCATION",claimCount:1,spent:180000});
    await claim.getByRole("button",{name:"결재 이력"}).click();
    for (const decision of ["직원 · 신청 접수 · 300,000원","부서장 · 최종 사전승인 · 200,000원",
      "직원 · 실제 지출 제출 · 225,000원","재무 · 정산 확정 · 180,000원"]) {
      await expect(claim).toContainText(decision);
    }
    expect((await other.request.get("/api/claims/"+number.trim()+"/history")).status()).toBe(404);
    await claim.getByRole("button", { name: "신청 당시 계산 근거" }).click();
    await expect(claim).toContainText("평가기 decimal-v1");
    await claim.getByText("예정액 계산 과정",{exact:true}).click();
    const estimate = claim.locator("details").filter({has:page.getByText("예정액 계산 과정",{exact:true})});
    await expect(estimate.getByRole("listitem")).toHaveText(["인정 대상 비용 · 300000","지원율 · 0.8","곱셈 · 240000","행사 한도 · 200000","작은 값 · 200000","내림 · 200000"]);
    await claim.getByText("실비 계산 과정",{exact:true}).click();
    await expect(claim.getByText("곱셈 · 180000",{exact:true})).toBeVisible();
    await expect(claim).toContainText("실제 지출 225,000원 → 인정 180,000원");
    await page.getByRole("button", { name: "결산표 조회" }).click();
    await expect(page.locator("#statement")).toContainText("교육비 · 1건 · 180,000원");
    await page.locator("#statement").getByText("교육비 · 1건 · 180,000원",{exact:true}).click();
    const source = page.locator("#statement summary").filter({hasText:number.trim()});
    await expect(source).toContainText("당월 순집행 180,000원");
    await source.click();
    await expect(page.locator("#statement")).toContainText("SETTLEMENT · 집행 증감 180,000원");
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
    await page.getByRole("button",{name:"결산표 조회"}).click();
    await page.locator("#statement").getByText("교육비 · 1건 · -30,000원",{exact:true}).click();
    await expect(source).toContainText("당월 순집행 -30,000원");
    await claim.getByRole("button", { name: "전표 조회" }).click();
    await claim.getByRole("button", { name: "원래 기간 결산표" }).click();
    await expect(claim).toContainText("마감 당시 가용 예산 820,000원");
    if (process.env.RECORD_ROLLBACK_CLAIM) await writeFile(process.env.RECORD_ROLLBACK_CLAIM,number.trim());
    await other.reload();
    await expect(other.locator("#available")).toHaveText("1,000,000원");
    await expect(other.locator("#claims")).toHaveText("아직 신청한 비용이 없습니다.");
    await other.locator('[name="title"]').fill("다른 방문자의 교육 신청");
    await other.getByRole("button", { name: "신청 제출" }).click();
    await expect(other.locator(".claim")).toContainText("다른 방문자의 교육 신청");
    page.once("dialog", dialog => dialog.accept());
    await page.getByRole("button", { name: "내 체험 종료" }).click();
    await expect(page.getByRole("button", { name: "개인 데모 시작" })).toBeVisible();
    expect((await page.request.get(receiptUrl!)).status()).toBe(401);
    await page.getByRole("button", { name: "개인 데모 시작" }).click();
    await expect(page.locator("#available")).toHaveText("1,000,000원");
    expect((await page.request.get(receiptUrl!)).status()).toBe(404);
    await expect(page.locator("#claims")).toHaveText("아직 신청한 비용이 없습니다.");
    await other.reload();
    await expect(other.locator(".claim")).toContainText("다른 방문자의 교육 신청");
    if (process.env.RECORD_SETTLEMENT) {
      const result = {origin:baseURL,claimId:number.trim(),droppedResponseStatus,retryStatus:retry.status(),afterDroppedResponse,before,after,balance,portalReceipt,
        report:{period:statement.period,balance:statement.balance,lines:statement.lines}};
      await writeFile(process.env.RECORD_SETTLEMENT,JSON.stringify(result,null,2)+"\n");
    }
    if (portalReceipt && process.env.RECORD_PORTAL_CLAIM) {
      await writeFile(process.env.RECORD_PORTAL_CLAIM,number.trim());
    }
  } finally {
    await second.close();
  }
});
