import {expect,test} from "@playwright/test";

test("본부 재무가 배정한 예산을 지점에서 확인한다",async ({page})=>{
  await page.setViewportSize({width:320,height:850});
  await page.goto("/");
  await page.getByLabel("체험 조직").selectOption("BANK");
  await page.getByRole("button",{name:"개인 데모 시작"}).click();
  await expect(page.locator("#available")).toHaveText("1,000,000원");
  const department = page.getByLabel("체험 부서");
  const role = page.getByLabel("시연 역할");
  const departmentBox = (await department.boundingBox())!;
  const roleBox = (await role.boundingBox())!;
  expect(roleBox.x).toBe(departmentBox.x);
  expect(roleBox.width).toBe(departmentBox.width);
  expect(roleBox.y).toBeGreaterThan(departmentBox.y + departmentBox.height);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(320);
  expect((await page.request.get("/api/branch-allocation")).status()).toBe(403);
  await page.locator("#role").selectOption("FINANCE");
  await page.getByLabel("지점에 배정할 금액(원)").fill("300000");
  await page.getByRole("button",{name:"지점 배정",exact:true}).click();
  await expect(page.locator("#branch-report")).toContainText("본부 배정액 700,000원 · 가용액 700,000원");
  await expect(page.locator("#branch-report")).toContainText("지점 배정액 1,300,000원 · 가용액 1,300,000원");
  await page.getByLabel("체험 부서").selectOption("SECONDARY");
  await expect(page.locator("#available")).toHaveText("1,300,000원");
  await page.locator("#role").selectOption("FINANCE");
  await expect(page.locator("#month-end")).toBeVisible();
  await expect(page.locator("#branch-allocation")).toBeHidden();
  expect((await page.request.get("/api/branch-allocation?department=SECONDARY")).status()).toBe(403);
});
