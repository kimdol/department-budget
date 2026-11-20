# 신청번호로 확인하는 교육비 정산 API

브라우저 개발자 도구의 네트워크 탭에서 `/api/`로 필터링하고 데모를 시작한다. 화면에서 역할을 바꿔도 같은 신청번호를 사용한다. 금액 단위는 원이다. 아래 표의 `{id}`는 신청 응답의 `claimId`다.

| 순서·역할 | 요청 | 입력과 확인할 결과 |
| --- | --- | --- |
| 1 · 직원 | `GET /api/claims/estimate` | `category=EDUCATION`, `estimated=300000`, `days=1`로 지원 예정액 200,000원, 본인 부담액 100,000원, 적용 세대를 확인한다. |
| 2 · 직원 | `POST /api/claims` | 같은 비목·예정액·일수, 제목 `title`, 새 UUID인 `requestKey`, 미리보기의 `expectedGeneration`을 보낸다. 응답의 신청번호를 기록한다. |
| 3 · 부서장 | `POST /api/claims/{id}/approve` | 추가 입력 없이 승인한다. `GET /api/workspace`의 `balance.committed`는 200000이다. |
| 4 · 직원 | `POST /api/claims/{id}/evidence` | `type=RECEIPT`, `amount=225000`으로 합성 영수증을 첨부한다. |
| 5 · 직원 | `POST /api/claims/{id}/review` | 최초 제출은 `actual=225000`, `round=0`이다. 목록의 상태가 `IN_REVIEW`가 되고 `reviewRound`가 1로 바뀐다. |
| 6 · 재무 | `POST /api/claims/{id}/settle` | 목록에서 확인한 `round=1`을 보낸다. 같은 차수로 재전송해도 정산 전표는 하나다. |
| 7 · 재무 | `GET /api/claims/{id}/postings` | 약정 전표와 정산 전표를 대조한다. 정산 전표의 약정 증감은 −200000, 집행 증감은 180000이다. |
| 8 · 재무 | `GET /api/workspace`, `GET /api/statement` | 약정 잔액 0원, 집행 180,000원, 가용액 820,000원과 교육비 1건을 확인한다. |

직원·부서장·재무는 각각 `EMPLOYEE`, `MANAGER`, `FINANCE`다. 역할 전환은 `POST /api/demo/role`이다. POST 입력은 폼 인코딩이며 페이지의 CSRF 토큰을 `X-CSRF-TOKEN` 헤더로 보낸다. 세션 쿠키와 토큰은 브라우저 안에서만 사용한다. 같은 세션의 보조 부서는 쿼리 매개변수 `department=SECONDARY`로 선택한다.

## 재시도와 보완

신청 응답을 받기 전에 연결이 끊겼으면 기존 `requestKey`와 동일한 신청 내용으로 다시 요청한다. 새 신청인데 확인 이후 다른 규정이 발행됐으면 서버가 409를 반환한다. 이 경우 지원 예정액과 세대를 다시 조회한다. 정산 재시도에는 같은 신청번호와 차수를 사용한다.

증빙이 부족해 정산이 422로 거절되면 `GET /api/claims/{id}/evidence/assessment`에서 필요한 증빙과 평가 근거를 본다. 재무가 `POST /api/claims/{id}/return`에 현재 차수와 보완 사유 `reason`을 보내면 직원이 보완할 수 있다. 재제출 뒤에는 목록의 최신 차수로 정산한다.

목록에는 화면 요약을 반환한다. 접수 당시 수식·평가기와 입력별 인정액은 `GET /api/claims/{id}/calculation`에서 확인한다. [HTTP 처리 코드](../src/main/java/kr/budget/web/DemoPortal.java) · [브라우저 인수 시험](../frontend/tests/claims.spec.ts) · [재시도·보완 계약 시험](../src/test/java/kr/budget/claims/PostingContract.java) · [Oracle 원장 대조](ORACLE-WALKTHROUGH.md)
