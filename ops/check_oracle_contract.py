from pathlib import Path
from xml.etree import ElementTree
import json


def main():
    folder = Path("target/surefire-reports")
    def results(suite):
        report = ElementTree.parse(folder / f"TEST-kr.budget.claims.{suite}.xml")
        cases = report.findall("testcase")
        if not cases or any(len(case.findall("skipped") + case.findall("failure") + case.findall("error")) for case in cases):
            raise SystemExit(f"{suite}: 실행되지 않았거나 실패한 원장 시험이 있습니다.")
        names = [case.attrib["name"] for case in cases]
        if len(set(names)) != len(cases):
            raise SystemExit(f"{suite}: 중복 시험 이름을 구분할 수 없습니다.")
        return {case.attrib["name"]:float(case.attrib["time"]) for case in cases}
    local = results("LocalPostingTest")
    oracle = results("OraclePostingTest")
    if local.keys() != oracle.keys():
        raise SystemExit("Oracle 시험 목록과 로컬 원장 계약이 다릅니다.")
    verified = {"suite":"kr.budget.claims.OraclePostingTest","passed":len(oracle),
                "cases":[{"name":name,"seconds":oracle[name]} for name in sorted(oracle)]}
    (folder/"oracle-cases.json").write_text(json.dumps(verified,indent=2)+"\n",encoding="utf-8")
    print(f"Oracle ledger contracts passed: {len(oracle)}")


if __name__ == "__main__":
    main()
