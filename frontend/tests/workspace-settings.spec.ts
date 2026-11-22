import {expect, test, type Locator} from "@playwright/test";

async function bounds(locator: Locator) {
  const box = await locator.boundingBox();
  expect(box).not.toBeNull();
  return box!;
}

for (const width of [320, 390, 768, 1024, 1440]) {
  test(`${width}px 체험 설정에서 제목, 안내, 조작 영역이 정렬된다`, async ({page}) => {
    await page.setViewportSize({width, height: 900});
    await page.goto("/");
    const settings = page.getByRole("region", {name: "체험 설정"});
    const heading = await bounds(settings.getByRole("heading"));
    const retention = await bounds(settings.getByText(/마지막 이용 후 30일/));
    const organization = await bounds(settings.getByRole("combobox", {name: /체험 조직/}));
    const start = settings.getByRole("button", {name: "내 신청함 시작", exact: true});
    const alternate = settings.getByRole("button", {name: "개인 데모 시작", exact: true});
    const primary = await bounds(start);
    const secondary = await bounds(alternate);
    const notice = settings.getByRole("status");
    const initialNotice = await bounds(notice);

    expect(retention.x).toBe(heading.x);
    expect(retention.y).toBeGreaterThanOrEqual(heading.y + heading.height);
    expect(organization.x).toBe(heading.x);
    expect(secondary.x).toBeGreaterThanOrEqual(primary.x + primary.width);
    expect(primary.y).toBe(secondary.y);
    expect(primary.height).toBe(secondary.height);
    expect(initialNotice.x).toBe(heading.x);
    expect(initialNotice.y).toBeGreaterThanOrEqual(primary.y + primary.height);
    if (width < 576) {
      expect(primary.x).toBe(organization.x);
      expect(primary.y).toBeGreaterThanOrEqual(organization.y + organization.height);
      expect(Math.abs(primary.width - secondary.width)).toBeLessThanOrEqual(1);
    } else {
      expect(primary.x).toBeGreaterThanOrEqual(organization.x + organization.width);
      expect(primary.y + primary.height).toBe(organization.y + organization.height);
    }
    for (const button of [start, alternate]) {
      const lines = await button.evaluate(element => {
        const range = document.createRange();
        range.selectNodeContents(element);
        return range.getClientRects().length;
      });
      expect(lines).toBe(1);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);

    await start.click();
    await expect(settings.getByLabel("체험 부서")).toBeVisible();
    await expect(settings.getByLabel("시연 역할")).toHaveValue("EMPLOYEE");
    await expect(alternate).toBeHidden();
    await expect(settings.getByRole("button", {name: "내 체험 종료"})).toBeVisible();
    expect((await bounds(notice)).x).toBe(heading.x);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
  });
}
