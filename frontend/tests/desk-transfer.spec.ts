import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

test("같은 결재함을 다시 열 때의 정적 자산 전송량", async ({ browser, baseURL }) => {
  const samples = [];
  for (let iteration = 1; iteration <= 5; iteration++) {
    const context = await browser.newContext({ baseURL, viewport: { width: 1280, height: 900 } });
    try {
      const page = await context.newPage();
      for (const visit of ["cold", "warm"]) {
        await page.goto("/");
        await expect(page.getByRole("button", { name: "개인 데모 시작" })).toBeVisible();
        const resources = await page.evaluate(() => performance.getEntriesByType("resource")
          .map(entry => {
            const resource = entry as PerformanceResourceTiming;
            return { path: new URL(resource.name).pathname, transferSize: resource.transferSize,
              encodedBodySize: resource.encodedBodySize };
          }).filter(resource => ["/portal.css","/portal.js","/jquery.min.js"].some(name=>resource.path.endsWith(name))));
        expect(resources).toHaveLength(3);
        samples.push({ iteration, visit, resources });
        await page.goto("about:blank");
      }
    } finally {
      await context.close();
    }
  }
  const label = process.env.MEASURE_DESK;
  if (label && ["before","after","assets-before","assets-after"].includes(label)) {
    const directory = path.resolve(__dirname, "../../docs/measurements");
    await mkdir(directory, { recursive: true });
    const metadata = { revision: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
      chromium: browser.version(), platform: os.platform(), arch: os.arch(), cpu: os.cpus()[0].model,
      logicalCpus: os.cpus().length, memoryGiB: Math.round(os.totalmem() / 1024 ** 3), samples };
    await writeFile(path.join(directory, label + ".json"), JSON.stringify(metadata, null, 2) + "\n");
  }
});
