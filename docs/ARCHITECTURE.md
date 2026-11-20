# 신청 한 건의 처리 경로

```mermaid
flowchart LR
    J[JSP 결재함과 jQuery] --> C[Spring MVC와 세션 권한 검사]
    R[React 규정 작업실] --> C
    C --> S[신청·결재·정산 서비스]
    C --> P[규정 검토·발행 서비스]
    S --> F[버전별 수식 평가와 증빙 판정]
    S --> M[MyBatis 매퍼]
    P --> M
    M --> O[(Oracle 업무 스키마)]
    B[Spring Batch 월마감] --> M
    O --> Q[송신함 재처리]
    Q --> H[HTTPS PHP 수신기]
    H --> D[(MySQL 수신 이력과 업무 요약)]
```

방문자 공간과 시연 역할은 서버 세션에서 결정한다. Oracle에 저장할 업무 전표와 포털 송신 대기 행은 한 트랜잭션에서 기록한다. 원격 수신은 나중에 실행하므로 포털 장애가 난 동안에도 이미 승인된 신청의 약정은 보존된다. [업무 트랜잭션](../src/main/java/kr/budget/claims/ClaimService.java) · [송신 재처리](../src/main/java/kr/budget/portal/PortalRelay.java) · [수신 이력 기록](../portal/receive.php)

## 교육비 300,000원

| 추적 단계 | 읽을 곳 | 같은 신청번호에서 확인할 값 |
| --- | --- | --- |
| 신청 버튼 | [JSP](../src/main/webapp/WEB-INF/views/portal.jsp), [제출 이벤트](../src/main/resources/static/assets/portal.js#L181) | 화면이 요청 키를 보관한다. 서버가 만든 신청번호를 응답으로 받는다. |
| 신청 접수 | [DemoPortal.submit](../src/main/java/kr/budget/web/DemoPortal.java#L90), [ClaimService](../src/main/java/kr/budget/claims/ClaimService.java) | 비목·예정액·일수·수식·발행 세대·평가기·결재 경로를 고정한다. 이 시점의 약정은 0원이다. |
| 부서장 승인 | [commitApproval](../src/main/java/kr/budget/claims/ClaimService.java#L119), [전표 INSERT](../src/main/java/kr/budget/claims/BudgetMapper.java#L78) | 예산 행과 신청을 잠그고 가용액을 검사한다. APPROVAL의 약정 증감은 +200,000원이다. |
| 실비 정산 | [settle](../src/main/java/kr/budget/claims/ClaimService.java#L174), [증빙 판정](../src/main/java/kr/budget/claims/EvidencePolicy.java) | 영수증과 실비 225,000원을 대조한다. SETTLEMENT의 약정 증감은 −200,000원, 집행 증감은 +180,000원이다. |
| 원장 대사 | [결산 SQL](../src/main/java/kr/budget/closing/ClosingMapper.java), [정산 브라우저 시험](../frontend/tests/claims.spec.ts) | 배정 1,000,000원에서 약정 잔액 0원과 누적 집행 180,000원을 차감하면 가용액은 820,000원이다. |

## 핵심 테이블 관계

```mermaid
erDiagram
    BUDGET_WORKSPACE ||--o{ EXPENSE_CLAIM : contains
    EXPENSE_CLAIM ||--o{ SUBMISSION_RECEIPT : identifies
    EXPENSE_CLAIM ||--o{ CLAIM_EVIDENCE : supports
    EXPENSE_CLAIM ||--o{ CLAIM_REVIEW_EVENT : records
    EXPENSE_CLAIM ||--o{ BUDGET_POSTING : posts
    EXPENSE_CLAIM ||--o{ PORTAL_OUTBOX : delivers
    BUDGET_WORKSPACE ||--o{ POLICY_PUBLICATION : scopes
    BUDGET_WORKSPACE ||--o{ BUDGET_STATEMENT_BALANCE : freezes
    BUDGET_WORKSPACE ||--o{ BUDGET_STATEMENT_LINE : summarizes
```

업무 공간의 키는 workspace_id, 신청 키는 claim_id다. 증빙·전표의 복합 외래 키는 공간과 신청의 조합을 검사한다. 전표의 기본 키는 신청번호와 전표 종류다. 정정 전표는 원래 정산 전표를 참조한다. 월마감 잔액과 비목별 집계는 공간·기간으로 조회한다. [버전별 스키마](../src/main/resources/db)

## 병원 학회비 규칙을 바꾸려면

병원 행정부서로 시작해 재무 담당자로 학회 참가비를 선택하고 인정액 수식에 `min(eligible_cost * 0.90, event_cap)`을 입력한다. 실비 180,000원에서 기존 144,000원과 후보 162,000원을 대조하고 검토를 요청한다. 부서장으로 바꿔 발행하면 이후 접수하는 학회 신청에 적용된다. 이전 수식을 복구하면 새 발행 세대가 생긴다. [규정 작업실](../frontend/src/policy.tsx) · [draft와 publish](../src/main/java/kr/budget/policy/PolicyService.java#L42)

이 변경에는 컨트롤러나 전표 SQL 수정이 필요하지 않다. 제출 시 복사한 수식과 평가기 식별자로 과거 신청을 계산하기 때문이다. 은행 학회비·병원 일반 교육비가 그대로인지와 복구 후에도 이전 우대 신청이 162,000원인지 검증한다. [원장 계약 시험](../src/test/java/kr/budget/claims/PostingContract.java#L202) · [발행·복구 화면 시험](../frontend/tests/hospital-policy.spec.ts)
