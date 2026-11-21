import {expect, test} from "@playwright/test";
import {execFileSync} from "node:child_process";
import {randomUUID} from "node:crypto";
import {readFile, writeFile} from "node:fs/promises";
import os from "node:os";

test("결재함 탐색과 Oracle 조회의 요청별 시간을 대조한다", async ({browser, baseURL}) => {
  const output = process.env.WORKSPACE_TIMING_OUTPUT;
  test.skip(!output, "요청별 측정 실행에서만 사용");
  test.setTimeout(180000);
  expect(new URL(baseURL!).hostname).toBe("127.0.0.1");
  const setup = await browser.newContext({baseURL});
  let session;
  try {
    const page = await setup.newPage();
    await page.goto("/");
    await page.locator("#start").click();
    await expect(page.locator("#available")).toHaveText("1,000,000원");
    const csrf = await page.locator('meta[name="csrf-token"]').getAttribute("content");
    const headers = {"X-CSRF-TOKEN":csrf!};
    for (let n = 1; n <= 50; n++) {
      const response = await setup.request.post("/api/claims", {headers, form:{
        requestKey:randomUUID(), title:"교육 신청 " + n, category:"EDUCATION", estimated:10000, days:1
      }});
      expect(response.ok()).toBe(true);
    }
    expect((await setup.request.post("/api/demo/role",{headers,form:{role:"MANAGER"}})).ok()).toBe(true);
    session = await setup.storageState();
  } finally {
    await setup.close();
  }

  const samples = [];
  for (let pair = 0; pair <= 10; pair++) {
    const context = await browser.newContext({baseURL,storageState:session,viewport:{width:1280,height:900}});
    try {
      const page = await context.newPage();
      await page.addInitScript(() => {
        const trace = {lcp:[] as {ms:number,tag:string,region:string,size:number}[], listFrameMs:0};
        Object.assign(window,{workspaceTrace:trace});
        new PerformanceObserver(list => {
          for (const item of list.getEntries()) {
            const entry = item as PerformanceEntry & {element:Element|null,size:number};
            trace.lcp.push({ms:entry.startTime,tag:entry.element?.tagName ?? "",
              region:entry.element?.closest("section,h1,header")?.getAttribute("id") ||
                entry.element?.closest("section,h1,header")?.tagName || "",size:entry.size});
          }
        }).observe({type:"largest-contentful-paint", buffered:true});
        const observer = new MutationObserver(() => {
          if (document.querySelectorAll("#claims > .claim").length !== 50) return;
          observer.disconnect();
          requestAnimationFrame(() => requestAnimationFrame(() => {trace.listFrameMs = performance.now();}));
        });
        observer.observe(document,{childList:true,subtree:true});
      });
      for (const cache of ["cold","warm"]) {
        const workspaceResponse = page.waitForResponse(r => new URL(r.url()).pathname === "/api/workspace");
        await page.goto("/");
        const response = await workspaceResponse;
        expect(response.ok()).toBe(true);
        const requestId = await response.headerValue("x-request-id");
        expect(requestId).toMatch(/^[a-f0-9-]{36}$/);
        await expect(page.locator("#claims > .claim")).toHaveCount(50);
        await page.waitForFunction(() => (window as any).workspaceTrace.listFrameMs > 0);
        await page.waitForLoadState("networkidle");
        await page.keyboard.press("Shift");
        const timing = await page.evaluate(() => {
          const api = (performance.getEntriesByType("resource") as PerformanceResourceTiming[])
            .filter(entry => new URL(entry.name).pathname === "/api/workspace");
          if (api.length !== 1) throw new Error("Expected one workspace request per navigation");
          const resource = api[0];
          const nav = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming;
          const timings = (r:PerformanceResourceTiming) => ({
            start:r.startTime,requestStart:r.requestStart,responseStart:r.responseStart,responseEnd:r.responseEnd,
            transferBytes:r.transferSize
          });
          const trace = (window as any).workspaceTrace as {
            lcp:{ms:number,tag:string,region:string,size:number}[],listFrameMs:number
          };
          return {navigation:timings(nav), workspace:timings(resource),
            traceId:resource.serverTiming.find(s => s.name === "trace")?.description,
            lcpCandidates:trace.lcp, listFrameMs:trace.listFrameMs};
        });
        expect(timing.traceId).toBe(requestId);
        expect(timing.lcpCandidates.length).toBeGreaterThan(0);
        expect(timing.listFrameMs).toBeGreaterThanOrEqual(timing.workspace.responseEnd);
        if (pair > 0) samples.push({pair,cache,...timing});
        await page.goto("about:blank");
      }
    } finally {
      await context.close();
    }
  }
  const log = await readFile(process.env.WORKSPACE_TIMING_LOG!, "utf8");
  const joined = samples.map(s => {
    const lines = log.split("\n").filter(line => line.includes("workspace_timing " + s.traceId + " "));
    expect(lines).toHaveLength(1);
    const [duration, calls] = lines[0].trim().split("workspace_timing " + s.traceId + " ")[1].split(" ");
    const queries = calls.split(",").map(call => {
      const [mapper, us] = call.split("=");
      return {mapper,ms:Number(us)/1000};
    });
    expect(queries.map(q => q.mapper.split(".").pop())).toEqual(["inbox","balance","organization"]);
    const dispatchMs = Number(duration)/1000;
    expect(dispatchMs).toBeGreaterThanOrEqual(queries.reduce((total,q) => total + q.ms,0));
    return {...s,dispatchMs,queries};
  });
  expect(new Set(joined.map(s => s.traceId)).size).toBe(joined.length);
  await writeFile(output!, JSON.stringify({
    revision:execFileSync("git",["rev-parse","HEAD"],{encoding:"utf8"}).trim(),
    runId:process.env.GITHUB_RUN_ID ?? null,database:process.env.WORKSPACE_TIMING_DB,
    chromium:browser.version(),platform:os.platform(),cpu:os.cpus()[0].model,
    logicalCpus:os.cpus().length,memoryGiB:os.totalmem()/2**30,viewport:[1280,900],
    claims:50,concurrency:1,discardedWarmupPairs:1,samples:joined
  },null,2)+"\n");
});
