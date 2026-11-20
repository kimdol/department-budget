import {expect,test} from "@playwright/test";
import {randomUUID} from "node:crypto";
import {writeFile} from "node:fs/promises";
import os from "node:os";

test("같은 부서 예산을 동시에 승인한 두 HTTP 응답과 전표를 측정한다",async ({browser,baseURL})=>{
  const output = process.env.RECORD_APPROVAL_CONTENTION;
  test.skip(!output,"동시 승인 측정 실행에서만 사용");
  test.setTimeout(180000);
  const origin = new URL(baseURL!);
  expect(origin.hostname).toBe("127.0.0.1");
  const samples = [];
  for (let pair=0;pair<22;pair++) {
    const context = await browser.newContext({baseURL});
    try {
      const page = await context.newPage();
      await page.goto("/");
      await page.getByLabel("체험 조직").selectOption("BANK");
      await page.locator("#start").click();
      await expect(page.locator("#available")).toHaveText("1,000,000원");
      const token = await page.locator('meta[name="csrf-token"]').getAttribute("content");
      const post = (path:string,form:Record<string,string|number>)=>
        context.request.post(path,{headers:{"X-CSRF-TOKEN":token!},form});
      const role = async (value:string)=>expect((await post("/api/demo/role",{role:value})).ok()).toBe(true);
      await role("FINANCE");
      expect((await post("/api/branch-allocation",{requestKey:randomUUID(),amount:900000})).ok()).toBe(true);
      await role("EMPLOYEE");
      const ids:string[] = [];
      for (const title of ["교육 A","교육 B"]) {
        const response = await post("/api/claims",{requestKey:randomUUID(),title,category:"EDUCATION",estimated:100000,days:1});
        expect(response.ok()).toBe(true);
        ids.push((await response.json()).claimId);
      }
      await role("MANAGER");
      const results = await Promise.all(ids.map(async claimId=>{
        const start = performance.now();
        const response = await post("/api/claims/"+claimId+"/approve",{});
        return {claimId,status:response.status(),milliseconds:performance.now()-start};
      }));
      expect(results.map(r=>r.status).sort()).toEqual([200,422]);
      await role("FINANCE");
      const snapshot = await (await context.request.get("/api/workspace")).json();
      expect(snapshot.balance).toEqual({allocation:100000,committed:80000,spent:0,available:20000});
      for (const result of results) {
        const response = await context.request.get("/api/claims/"+result.claimId+"/postings");
        expect(response.ok()).toBe(true);
        const postings = await response.json();
        if (result.status===200) {
          expect(postings).toHaveLength(1);
          expect(postings[0]).toMatchObject({kind:"APPROVAL",committedDelta:80000,spentDelta:0});
        } else expect(postings).toEqual([]);
        samples.push({pair,warmup:pair<2,...result,postings:postings.length,available:snapshot.balance.available});
      }
    } finally {
      await context.close();
    }
  }
  const p95 = (status:number)=>{
    const times = samples.filter(s=>!s.warmup && s.status===status).map(s=>s.milliseconds).sort((a,b)=>a-b);
    return times[Math.ceil(times.length*.95)-1];
  };
  await writeFile(output!,JSON.stringify({origin:origin.origin,db:process.env.TEST_EXTERNAL_RELEASE==="1"?"release":"local-test",
    commit:process.env.GITHUB_SHA,node:process.version,cpu:os.cpus()[0].model,logicalCpus:os.cpus().length,
    memoryBytes:os.totalmem(),platform:os.platform(),kernel:os.release(),p95Ms:{approved:p95(200),rejected:p95(422)},samples},null,2)+"\n");
});
