import { expect, test } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { writeFile } from "node:fs/promises";
import path from "node:path";

test("이전 신청을 다음 페이지에서 결재한 뒤 최신 신청으로 돌아온다", async ({page}) => {
  await page.goto("/");
  await page.getByRole("button",{name:"개인 데모 시작"}).click();
  await expect(page.locator("#available")).toHaveText("1,000,000원");
  const token = await page.locator('meta[name="csrf-token"]').getAttribute("content");
  for (let index=0;index<51;index++) {
    const result = await page.request.post("/api/claims",{
      headers:{"X-CSRF-TOKEN":token!},
      form:{requestKey:randomUUID(),title:"교육 신청 "+index,category:"EDUCATION",estimated:10000,days:1}
    });
    expect(result.ok()).toBe(true);
  }
  await page.reload();
  await page.locator("#role").selectOption("MANAGER");
  await expect(page.locator(".claim")).toHaveCount(50);
  const measurement = process.env.MEASURE_INBOX_PAYLOAD;
  if (measurement === "before" || measurement === "after") {
    const bytes = [];
    for (let run=0;run<5;run++) {
      const response = await page.request.get("/api/workspace");
      expect(response.ok()).toBe(true);
      expect((await response.json()).claims).toHaveLength(50);
      bytes.push((await response.body()).length);
    }
    const output = {environment:"loopback HTTPS", claims:51, pageSize:50,
      unit:"decoded response body bytes", role:"MANAGER", samples:bytes};
    await writeFile(path.resolve(__dirname,"../../docs/measurements/inbox-payload-"+measurement+".json"),
      JSON.stringify(output,null,2)+"\n");
  }
  await page.getByRole("button",{name:"다음 페이지"}).click();
  await expect(page.locator(".claim")).toHaveCount(1);
  await expect(page.locator(".claim")).toContainText("교육 신청 0");
  await page.getByRole("button",{name:"사전승인",exact:true}).click();
  await expect(page.locator(".claim")).toContainText("약정 완료");
  await page.getByRole("button",{name:"이전 페이지"}).click();
  await expect(page.locator(".claim").first()).toContainText("교육 신청 50");
});
