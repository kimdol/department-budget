import { expect, test } from "@playwright/test";
import { get } from "node:https";
import type { TLSSocket } from "node:tls";
import { writeFile } from "node:fs/promises";
import path from "node:path";

test("TLS 인증서와 세션·캐시 응답을 검증한다", async ({ page, baseURL }) => {
  test.skip(!process.env.TEST_TLS_SPKI);
  const transport = await new Promise<{ trusted: boolean, protocol: string | null }>((resolve,reject) => {
    get(baseURL!, response => {
      const socket = response.socket as TLSSocket;
      resolve({ trusted: socket.authorized, protocol: socket.getProtocol() });
      response.resume();
    }).on("error",reject);
  });
  expect(transport.trusted).toBe(true);
  expect(transport.protocol).toMatch(/^TLSv1\.[23]$/);
  const landing = await page.goto("/");
  expect(await landing!.headerValue("strict-transport-security")).toMatch(/max-age=[1-9]/);
  expect(await landing!.headerValue("cache-control")).toContain("no-store");
  const cookie = (await page.context().cookies()).find(value => value.name === "JSESSIONID");
  expect(cookie).toMatchObject({ secure:true, httpOnly:true, sameSite:"Lax" });
  await page.getByRole("button",{name:"개인 데모 시작"}).click();
  await expect(page.locator("#available")).toHaveText("1,000,000원");
  const snapshot = await page.request.get("/api/workspace");
  expect(snapshot.status()).toBe(200);
  expect(snapshot.headers()["cache-control"]).toContain("no-store");
  const library = await page.request.get("/webjars/jquery/3.7.1/jquery.min.js");
  expect(library.headers()["cache-control"]).toContain("immutable");
  await page.goto("about:blank");
  await page.goto("/");
  const reused = await page.evaluate(() => {
    const url = new URL("/webjars/jquery/3.7.1/jquery.min.js",location.href);
    const entry = performance.getEntriesByName(url.href)[0] as PerformanceResourceTiming;
    return { transferSize:entry.transferSize, encodedBodySize:entry.encodedBodySize };
  });
  expect(reused.transferSize).toBe(0);
  expect(reused.encodedBodySize).toBeGreaterThan(0);
  if (process.env.RECORD_TLS === "1") {
    const result = { environment:"loopback", transport, hsts:await landing!.headerValue("strict-transport-security"),
      cookie:{ secure:cookie!.secure,httpOnly:cookie!.httpOnly,sameSite:cookie!.sameSite },
      workspaceCache:snapshot.headers()["cache-control"], libraryCache:library.headers()["cache-control"], reused };
    await writeFile(path.resolve(__dirname,"../../docs/measurements/loopback-https.json"),JSON.stringify(result,null,2)+"\n");
  }
});
