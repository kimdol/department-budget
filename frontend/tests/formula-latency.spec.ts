import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

test("수식 연속 입력의 요청 수와 최종 결과 표시 시간을 측정한다", async ({page, browser}) => {
  const label = process.env.MEASURE_FORMULA;
  test.skip(label !== "before" && label !== "after");
  await page.goto("/");
  await page.getByRole("button", {name:"개인 데모 시작"}).click();
  await page.locator("#policy-panel summary").click();
  const editor = page.getByRole("region", {name:"비용 지원 규정"});
  const input = editor.getByLabel("인정액 수식");
  await expect(editor).toContainText("기존 144,000원");
  let requests = 0;
  page.on("request", request => {
    if (new URL(request.url()).pathname === "/api/policies/preview") requests++;
  });
  const samples = [];
  for (let run = 0; run < 6; run++) {
    await input.fill("eligible_cost");
    await expect(editor).toContainText("후보 180,000원");
    requests = 0;
    const displayed = await input.evaluateHandle(element => {
      let lastInput = performance.now();
      const onInput = () => { lastInput = performance.now(); };
      element.addEventListener("input", onInput);
      const section = element.closest("section")!;
      const timing = new Promise<number>(resolve => {
        const observer = new MutationObserver(() => {
          if (!section.textContent?.includes("후보 162,000원")) return;
          observer.disconnect();
          element.removeEventListener("input", onInput);
          resolve(performance.now() - lastInput);
        });
        observer.observe(section, {subtree:true, childList:true, characterData:true});
      });
      return {timing};
    });
    await input.press("End");
    await input.pressSequentially(" * 0.9", {delay:40});
    await expect(editor).toContainText("후보 162,000원");
    const milliseconds = await displayed.evaluate(sample => sample.timing);
    await displayed.dispose();
    samples.push({run, requests, milliseconds});
  }
  const result = {revision:execFileSync("git", ["rev-parse","HEAD"], {encoding:"utf8"}).trim(),
    chromium:browser.version(), platform:os.platform(), cpu:os.cpus()[0].model, memoryBytes:os.totalmem(),
    input:" * 0.9", keyDelayMs:40, warmupRuns:1, samples};
  await writeFile(path.resolve(__dirname, "../../docs/measurements/formula-"+label+".json"), JSON.stringify(result,null,2)+"\n");
});
