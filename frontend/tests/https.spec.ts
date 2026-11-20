import { expect, test } from "@playwright/test";
import { get } from "node:https";
import type { TLSSocket } from "node:tls";
import { writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import path from "node:path";

test("TLS 인증서와 세션·캐시 응답을 검증한다", async ({ page, baseURL }) => {
  test.skip(!baseURL?.startsWith("https://"));
  const transport = await new Promise<{ trusted:boolean, protocol:string|null, validFrom:string, validTo:string }>((resolve,reject) => {
    get(baseURL!, response => {
      const socket = response.socket as TLSSocket;
      const cert = socket.getPeerCertificate();
      resolve({ trusted:socket.authorized, protocol:socket.getProtocol(), validFrom:cert.valid_from, validTo:cert.valid_to });
      response.resume();
    }).on("error",reject);
  });
  expect(transport.trusted).toBe(true);
  expect(transport.protocol).toMatch(/^TLSv1\.[23]$/);
  expect(Date.parse(transport.validFrom)).toBeLessThanOrEqual(Date.now());
  expect(Date.parse(transport.validTo)).toBeGreaterThan(Date.now());
  let redirect;
  if (process.env.TEST_DEPLOYMENT_URL) {
    const target = new URL("/assets/portal.css?visit=redirect",baseURL);
    const plain = new URL(target);
    plain.protocol = "http:";
    plain.port = "";
    const response = await page.request.get(plain.href,{maxRedirects:0});
    redirect = {status:response.status(),location:response.headers().location};
    expect([301,308]).toContain(redirect.status);
    expect(redirect.location).toBe(target.href);
  }
  const requests = new Set<string>();
  page.on("request",request=>requests.add(request.url()));
  const landing = await page.goto("/");
  expect(await landing!.headerValue("strict-transport-security")).toMatch(/max-age=[1-9]/);
  expect(await landing!.headerValue("cache-control")).toContain("no-store");
  const catalogUrl = "/api/catalog/expense-categories";
  const catalog = await page.request.get(catalogUrl);
  expect(catalog.status()).toBe(200);
  expect(await catalog.json()).toEqual({EDUCATION:"교육비",CONFERENCE:"학회 참가비",TRAVEL:"출장비"});
  expect(catalog.headers()["cache-control"]).toContain("public");
  expect(catalog.headers()["cache-control"]).toContain("no-cache");
  const tag = catalog.headers().etag;
  expect(tag).toBe('"'+createHash("sha256").update(await catalog.body()).digest("hex")+'"');
  const revalidated = await page.request.get(catalogUrl,{headers:{"If-None-Match":tag}});
  expect(revalidated.status()).toBe(304);
  expect((await revalidated.body()).length).toBe(0);
  expect((await page.request.get(catalogUrl,{headers:{"If-None-Match":'"older-version"'}})).status()).toBe(200);
  const styles = await page.locator('link[rel="stylesheet"]').getAttribute("href");
  const portal = await page.locator('script[src$="/portal.js"]').getAttribute("src");
  const editor = await page.locator("#policy-panel").getAttribute("data-module");
  for (const url of [styles!,portal!,editor!]) {
    const asset = await page.request.get(url);
    expect(asset.status()).toBe(200);
    const digest = createHash("sha256").update(await asset.body()).digest("hex");
    expect(url.split("/")[2]).toBe(digest);
    expect(asset.headers()["cache-control"]).toContain("immutable");
    expect((await page.request.get(url.replace(digest,"0".repeat(64)))).status()).toBe(404);
  }
  expect(await page.evaluate(url => performance.getEntriesByName(new URL(url,location.href).href).length,editor!)).toBe(0);
  const cookie = (await page.context().cookies()).find(value => value.name === "JSESSIONID");
  expect(cookie).toMatchObject({ secure:true, httpOnly:true, sameSite:"Lax" });
  await page.getByRole("button",{name:"개인 데모 시작"}).click();
  await expect(page.locator("#available")).toHaveText("1,000,000원");
  const activeCookie = (await page.context().cookies()).find(value => value.name === "JSESSIONID");
  expect(activeCookie).toMatchObject({ secure:true, httpOnly:true, sameSite:"Lax" });
  expect(activeCookie!.value !== cookie!.value).toBe(true);
  const snapshot = await page.request.get("/api/workspace");
  expect(snapshot.status()).toBe(200);
  expect(snapshot.headers()["cache-control"]).toContain("no-store");
  await page.locator("#role").selectOption("FINANCE");
  await page.locator("#policy-panel summary").click();
  await expect(page.getByRole("button",{name:"검토 요청"})).toBeEnabled();
  expect(requests.has(new URL(editor!,baseURL).href)).toBe(true);
  for (const address of requests) expect(new URL(address).origin).toBe(new URL(baseURL!).origin);
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
    const deployment = Boolean(process.env.TEST_DEPLOYMENT_URL);
    const proxyCI = Boolean(process.env.CI_PROXY_PROBE);
    const result = { environment:proxyCI ? "proxy-ci" : deployment ? "deployment" : "loopback", origin:baseURL, transport, redirect,
      sameOriginRequests:requests.size, hsts:await landing!.headerValue("strict-transport-security"),
      cookie:{ secure:cookie!.secure,httpOnly:cookie!.httpOnly,sameSite:cookie!.sameSite },
      workspaceCache:snapshot.headers()["cache-control"], libraryCache:library.headers()["cache-control"],
      catalog:{etag:tag,revalidation:revalidated.status()}, reused };
    const file = deployment ? "deployment-https.json" : "loopback-https.json";
    const output = proxyCI ? path.resolve(__dirname,"../../target/https-proxy/transport.json") : path.resolve(__dirname,"../../docs/measurements",file);
    await writeFile(output,JSON.stringify(result,null,2)+"\n");
  }
});
