from pathlib import Path
from xml.etree import ElementTree


def main():
    folder = Path("target/surefire-reports")
    def results(suite):
        report = ElementTree.parse(folder / f"TEST-kr.budget.claims.{suite}.xml")
        cases = report.findall("testcase")
        if not cases or any(len(case.findall("skipped") + case.findall("failure") + case.findall("error")) for case in cases):
            raise SystemExit(f"{suite}: 실행되지 않았거나 실패한 원장 시험이 있습니다.")
        return {case.attrib["name"] for case in cases}
    local = results("LocalPostingTest")
    oracle = results("OraclePostingTest")
    if local != oracle:
        raise SystemExit("Oracle 시험 목록과 로컬 원장 계약이 다릅니다.")
    print(f"Oracle ledger contracts passed: {len(oracle)}")


if __name__ == "__main__":
    main()
