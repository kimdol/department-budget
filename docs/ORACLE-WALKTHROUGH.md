# 교육비 한 건을 Oracle에서 직접 확인하기

공개 데모와 별도로 준비한 자신의 학습용 스키마에서 실행한다. 화면에서 일반 기업 조직을 선택하고 교육비 300,000원을 신청한다. 신청 카드에 표시된 UUID를 복사한다. 아래 쿼리는 SQL Developer에서 그대로 실행할 수 있고, 실행 시 표시되는 바인드 변수 창의 claim_id에 복사한 값을 입력한다.

## 약정이 생기는 시점

직원으로 신청한 직후와 부서장으로 사전승인한 직후에 같은 쿼리를 실행한다. 신청 직후에는 결과가 0행이고, 승인 후에는 APPROVAL 1행에 committed_delta 200000, spent_delta 0이 기록된다.

```sql
select period_key, kind, committed_delta, spent_delta, original_kind
from budget_posting
where claim_id = :claim_id
order by posted_at, kind
```

## 실비 정산 대조

직원으로 실제 지출 225,000원에 맞는 합성 영수증을 첨부해 정산을 신청한다. 재무 담당자로 정산을 확정하고 같은 SQL을 다시 실행한다. SETTLEMENT 행의 committed_delta는 −200000, spent_delta는 180000이다. 최초 승인 금액과 실비 인정액의 차이 20,000원은 가용 예산으로 돌아간다.

```sql
select c.title, c.status, c.estimated, c.approved, c.actual, c.settled,
       c.formula, c.policy_generation, c.evaluator_version, c.approval_route,
       w.allocation, w.committed, w.spent,
       w.allocation - w.committed - w.spent as available
from expense_claim c
join budget_workspace w on w.workspace_id = c.workspace_id
where c.claim_id = :claim_id
```

새 업무 공간에서 이 신청만 처리했다면 정산액·누적 집행액은 180,000원, 가용액은 820,000원이다. 같은 공간에서 다른 신청도 처리했다면 부서 합계에는 그 신청들의 전표도 반영된다.

## 규정 변경 후 재현

병원 조직에서 학회비를 신청하고 규정 작업실에서 우대 수식을 발행한다. 발행 전후의 학회 신청 두 건에 대해 위 SQL을 실행하여 formula와 policy_generation을 비교한다. 신청 카드에서 ‘신청 당시 계산 근거’를 열고 당시 입력과 실제 인정액이 Oracle 조회 결과와 일치하는지 확인한다. 기존 버전을 복구한 뒤 신규 학회 신청도 같은 순서로 비교한다.

재무 담당자로 학회비 실비 180,000원, 1일, 후보 수식 `min(eligible_cost * 0.90, event_cap)`을 넣고 검토를 요청한다. 아래 쿼리에는 같은 병원 업무 공간의 신청번호를 입력한다. 초기 규정에서 출발했다면 원래 지원액은 144,000원, 후보 지원액은 162,000원이다. 부서장 화면에서 비교 실비를 바꾼 뒤 다시 조회해 요청 당시의 입력이 보존되는지 확인한다.

```sql
select d.draft_id, d.base_generation, d.formula, d.sample_cost, d.sample_days,
       d.original_support, d.candidate_support,
       d.candidate_support - d.original_support as support_difference
from policy_draft d
join expense_claim c on c.workspace_id = d.workspace_id
where c.claim_id = :claim_id and d.category = 'CONFERENCE'
order by d.base_generation, d.draft_id
```

한 세대에 초안 여러 건을 요청할 수 있다. 결과에서는 수식과 비교 실비가 일치하는 행을 찾는다. V015 적용 이전 초안의 비교값은 NULL이다.

[금액 계산과 신청 처리](../src/main/java/kr/budget/claims/ClaimService.java) · [학회비 변경 회귀 시험](../src/test/java/kr/budget/claims/PostingContract.java) · [실제 스키마 복원 절차](ORACLE.md)
