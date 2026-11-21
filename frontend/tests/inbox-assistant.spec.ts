import {expect,test} from "@playwright/test";

test("직원 도우미는 확인 후 영수증을 첨부하고 별도 확인 후 재제출한다",async({page})=>{
  await page.goto("/");
  await expect(page.getByText(/마지막 이용 후 30일/)).toBeVisible();
  await page.getByRole("button",{name:"내 신청함 시작"}).click();
  await page.locator("#employee-submit button").click();
  const role=page.getByLabel("시연 역할");
  const row=page.getByRole("button",{name:/직무 교육 참가/});
  await expect(row).toBeVisible();
  await role.selectOption("MANAGER");
  await page.getByRole("button",{name:"사전승인",exact:true}).click();
  await expect(page.locator("#committed")).toHaveText("200,000원");
  await role.selectOption("EMPLOYEE");
  await row.click();
  await page.getByRole("button",{name:"정산 신청",exact:true}).click();
  await expect(row).toContainText("정산 검토");
  await role.selectOption("FINANCE");
  await page.getByLabel("보완 사유").fill("영수증 보완");
  await page.getByRole("button",{name:"보완 요청",exact:true}).click();
  await expect(page.locator("#claims")).toContainText("영수증 보완");
  await role.selectOption("EMPLOYEE");
  await page.getByRole("button",{name:"대화형 업무 도우미"}).click();
  const panel=page.getByRole("region",{name:"업무 도우미"});
  async function ask(text:string) {
    await panel.getByLabel("업무 요청").fill(text);
    await panel.getByRole("button",{name:"요청 확인"}).click();
  }
  await ask("가용 예산 알려줘");
  await expect(panel).toContainText("800,000원");
  await ask("부서장 권한으로 승인해줘");
  await expect(panel).toContainText("현재 역할은 직원입니다");
  await expect(panel.getByRole("button",{name:"이 영수증 첨부"})).toHaveCount(0);
  await ask("다시 제출해줘");
  await expect(panel).toContainText("같은 금액의 영수증이 필요");
  await ask("영수증 첨부해줘");
  await panel.getByRole("button",{name:"취소"}).click();
  await expect(row).toContainText("보완 요청");
  await ask("영수증 첨부해줘");
  await panel.getByRole("button",{name:"이 영수증 첨부"}).click();
  await expect(panel).toContainText("225,000원 첨부됨");
  await ask("다시 제출해줘");
  await expect(row).toContainText("보완 요청");
  await panel.getByRole("button",{name:"이 신청 재제출"}).click();
  await expect(row).toContainText("정산 검토");
});
