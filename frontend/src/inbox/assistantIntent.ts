export type InboxIntent = "balance" | "status" | "attachReceipt" | "resubmit" | "reviewerOnly" | "unknown";
export interface IntentInterpreter {
  interpret(text:string):Promise<InboxIntent>;
}

// A provider may propose a known intent. The caller owns selection, confirmation and execution.
export const koreanCommands:IntentInterpreter = {
  async interpret(text) {
    const input=text.trim().replace(/[?.!]+$/u,"").replace(/\s+/gu,"");
    if (/^(?:내|부서)?(?:가용)?(?:예산|잔액)(?:잔액)?(?:은|이|을)?(?:얼마(?:야|예요|인가요)?|알려(?:줘|주세요)|보여(?:줘|주세요)|조회)?$/u.test(input)) return "balance";
    if (/^(?:내|이)?(?:신청|신청건)?(?:의)?(?:진행)?상태(?:를|가)?(?:어때|뭐야|알려(?:줘|주세요)|보여(?:줘|주세요)|조회)?$/u.test(input)) return "status";
    if (/^(?:이신청에)?(?:합성|지출)?영수증(?:을)?첨부(?:해(?:줘|주세요)?|하기)?$/u.test(input)) return "attachReceipt";
    if (/^(?:보완(?:내용)?(?:을)?)?(?:다시제출|재제출)(?:해(?:줘|주세요)?|하기)?$/u.test(input)) return "resubmit";
    if (/승인|정산확정|권한/u.test(input)) return "reviewerOnly";
    return "unknown";
  }
};
