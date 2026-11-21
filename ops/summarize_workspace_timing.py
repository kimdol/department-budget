"""Print the cold/warm timing table from the browser and server trace sample."""
import json
import math
import statistics
import sys
from pathlib import Path


def intervals(sample):
    api = sample["workspace"]
    sql = [query["ms"] for query in sample["queries"]]
    catalog = next(r for r in sample["resources"] if r["name"] == "expense-categories")
    return {
        "LCP": sample["lcpCandidates"][-1]["ms"],
        "목록 프레임": sample["listFrameMs"],
        "탐색 시작 → API 요청 시작": api["requestStart"],
        "API 요청 시작 → 응답 끝": api["responseEnd"] - api["requestStart"],
        "API 응답 끝 → 목록 프레임": sample["listFrameMs"] - api["responseEnd"],
        "API 응답 끝 → 비목표 응답 끝": catalog["responseEnd"] - api["responseEnd"],
        "비목표 응답 끝 → 50행 DOM 관측": sample["listDomMs"] - catalog["responseEnd"],
        "DOM 관측 → 목록 프레임": sample["listFrameMs"] - sample["listDomMs"],
        "HTML 서버 필터 구간": sample["documentServer"]["dispatchMs"],
        "API TTFB": api["responseStart"] - api["requestStart"],
        "Spring 필터 구간": sample["dispatchMs"],
        "SQL 호출 합계": sum(sql),
        **{q["mapper"].rsplit(".", 1)[1]: q["ms"] for q in sample["queries"]},
    }


def summary(samples):
    rows = [intervals(sample) for sample in samples]
    for key in rows[0]:
        values = sorted(row[key] for row in rows)
        yield key, statistics.median(values), values[math.ceil(len(values) * 0.95) - 1]


if __name__ == "__main__":
    samples = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["samples"]
    for cache in ("cold", "warm"):
        group = [sample for sample in samples if sample["cache"] == cache]
        print(f"{cache} (n={len(group)})")
        print("| 구간 | 중앙값(ms) | p95(ms) |")
        print("| --- | ---: | ---: |")
        for key, median, p95 in summary(group):
            print(f"| {key} | {median:.2f} | {p95:.2f} |")
