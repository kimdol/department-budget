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

`GET /api/claims/{id}/calculation`의 `initialSteps`와 `actualSteps`는 신청에 저장된 평가기와 수식으로 다시 계산한 순서다. 각 단계의 `operation`은 입력 변수나 연산, `value`는 십진 문자열이다. 실비를 제출하기 전에는 실비 단계가 비어 있다. 예를 들어 예정액 300,000원은 지원율을 곱하면 240,000원이고 행사 한도 적용 후 200,000원이다. 실비 225,000원은 같은 규정에서 180,000원이 된다.

신청 응답은 `claimId`, `category`, `policyGeneration`, `evaluatorVersion`을 반환한다. 교육비 300,000원을 초기 규정으로 접수하면 세대 0과 `decimal-v1`이 기록된다. 이후 새 규정을 발행해도 같은 요청 키와 입력을 다시 보내면 최초 접수 응답을 받는다. 새 요청 키로 이미 바뀐 세대를 지정하면 409로 거절한다.

결재함은 `GET /api/workspace?status=APPROVED`로 현재 부서의 약정 완료 신청만 조회할 수 있다. 상태는 DB에서 페이지를 나누기 전에 적용한다. 다음 페이지에는 마지막 행의 `inboxOrder`를 `before`로 보낸다. 응답의 예산 잔액은 부서 전체 금액이다. 화면에서 상태를 바꾸면 첫 페이지부터 표시하고, 결재 이력을 열고 닫으면 같은 신청의 이력 버튼으로 포커스가 돌아온다.

결재 이력은 같은 부서의 세 역할이 `GET /api/claims/{id}/history`로 조회한다. 대표 사례는 직원의 접수 300,000원, 부서장의 최종 사전승인 200,000원, 직원의 실비 제출 225,000원, 재무의 정산 확정 180,000원 순서다. 각 행에는 처리 시각·역할·업무 차수가 있고 보완 요청에는 사유가 있다. 과거 신청은 당시 저장한 이벤트만 표시한다. 환입과 귀속 월은 전표 조회에서 확인한다.

신청 응답을 받기 전에 연결이 끊겼으면 기존 `requestKey`와 동일한 신청 내용으로 다시 요청한다. 새 신청인데 확인 이후 다른 규정이 발행됐으면 서버가 409를 반환한다. 이 경우 지원 예정액과 세대를 다시 조회한다. 정산 재시도에는 같은 신청번호와 차수를 사용한다.

증빙이 부족해 정산이 422로 거절되면 `GET /api/claims/{id}/evidence/assessment`에서 필요한 증빙과 평가 근거를 본다. 재무가 `POST /api/claims/{id}/return`에 현재 차수와 보완 사유 `reason`을 보내면 직원이 보완할 수 있다. 재제출 뒤에는 목록의 최신 차수로 정산한다.

목록에는 화면 요약을 반환한다. 접수 당시 수식·평가기와 입력별 인정액은 `GET /api/claims/{id}/calculation`에서 확인한다. [HTTP 처리 코드](../src/main/java/kr/budget/web/DemoPortal.java) · [브라우저 인수 시험](../frontend/tests/claims.spec.ts) · [재시도·보완 계약 시험](../src/test/java/kr/budget/claims/PostingContract.java) · [Oracle 원장 대조](ORACLE-WALKTHROUGH.md)
## 다음 회계 기간의 규정 발행 예약

재무가 초안을 제출하면 부서장은 `GET /api/policies?category=CONFERENCE`의 `drafts[].reviewHash`와 `nextPeriod`를 확인한다. 검토한 값으로 `POST /api/policies/drafts/{id}/schedule`에 `reviewHash`와 `targetPeriod`를 보낸다. 조회 결과의 `scheduled`에 예약이 나타나며 현재 적용 버전은 그대로다. 같은 초안·해시·기간을 재전송할 수 있다. 다른 초안을 즉시 발행하거나 예약하려면 기존 예약을 `POST /api/policies/drafts/{id}/unschedule`로 취소한다. 취소 요청에도 검토한 `targetPeriod`가 필요하다.

재무가 마감을 확정한 뒤 **다음 달 열기**를 누르면 해당 기간의 예약들이 같은 트랜잭션에서 발행된다. 검토 해시가 달라지면 서버가 409를 반환하고 기존 마감 상태와 예약을 보존한다. [기간 개시](../src/main/java/kr/budget/closing/PeriodClosing.java) · [검토 해시와 발행](../src/main/java/kr/budget/policy/PolicyService.java)
## 보완 중 신청 수정

동일 비목에서 제목·예정 금액·일수를 수정할 때 직원은 `POST /api/claims/{id}/revise`에 `round`, `requestKey`, `title`, `estimated`, `days`를 보낸다. 서버는 이전 약정을 해제하고 새 버전을 첫 결재 단계에 접수한다. 이전 입력, 증빙, 전표는 이전 버전의 신청번호로 계속 조회한다. 동일 차수·요청 키·내용의 재전송은 이미 생성한 신청번호를 반환한다.

현재 방문자의 업무 공간에서 `GET /api/claims/{id}/versions`를 호출하면 어느 버전의 신청번호를 사용해도 같은 비목의 버전들이 오름차순으로 반환된다. `seriesId`는 최초 신청번호, `versionNo`는 1부터 시작하는 버전, `claimId`는 해당 버전의 결재와 전표를 찾는 키다. 각 행에는 `title`, `estimated`, `days`, `status`도 포함된다. 비목을 바꾸면 새 신청번호로 별도 계열을 시작한다.

직원은 `POST /api/claims/{id}/replace-category`에 보완 차수 `round`, 새 비목 `category`, 예정 금액 `estimated`, 일수 `days`, 요청 식별자 `requestKey`를 보낸다. 성공 응답의 `claimId`는 새 신청번호다. 두 신청의 결재 이력에 상대 신청번호가 남는다. 같은 내용과 식별자로 재전송하면 같은 결과를 받는다. 같은 원신청에서 서로 다른 대체 신청을 만들려 하면 409가 반환된다. [원장 계약](../src/test/java/kr/budget/claims/PostingContract.java)
## 신청자가 결재 대행을 맡은 경우

조직 데모의 구성원 식별자는 방문자·부서별 업무 공간 안에서 해석한다. 직원은 `STAFF_01`, 부서장은 `HEAD_01`, 재무 담당자는 `FIN_01`이다. 부서장 화면에서 **신청 직원의 부서장 대행 체험**을 누르면 서버가 시연 세션에 직원 신원과 대행 권한을 함께 설정한다. 사전승인·증액·보완 판단은 잠근 신청 행의 신청자와 결재자의 식별자가 같으면 403을 반환한다. 결재 요청 본문으로 처리자 신원을 지정할 수 없다. 결재 이력의 `actorMemberId`는 해당 사건의 실제 시연 구성원이다. V024 이전 이력에는 역할만 보존된다. [서비스의 결재 검증](../src/main/java/kr/budget/claims/ClaimService.java) · [대행 체험 시험](../frontend/tests/employee-form.spec.ts)
## 포털 메시지 재처리

포털 운영자는 원송신함에서 추출한 JSON 또는 헤더 없는 CSV 한 행을 Apache Camel 변환기에 입력한다. CSV 필드는 차례로 이벤트 ID, 부서 업무 공간 ID, 신청번호, 차수, 유형, 원 단위 금액이다. 차수 1은 APPROVAL, 차수 2는 SETTLEMENT 또는 CANCELLATION이다. 같은 메시지를 복구할 때는 원송신함의 이벤트 ID를 보존한다.

```sh
mvn -f portal-importer/pom.xml verify
java -jar portal-importer/target/portal-importer-0.1.0.jar csv < original.csv > replay.json
```

원본이 JSON이면 마지막 명령의 인수를 `json`으로 지정한다. 변환 종료 코드가 0이면 기존 HTTPS 수신기의 POST 본문으로 `replay.json`을 전송한다. 수신기는 기존 연계 자격 증명을 검사하고 같은 이벤트 ID·내용의 재처리에 204, 같은 ID의 금액 변경에는 409를 반환한다. 변환기는 독립된 운영 도구이며 애플리케이션 WAR에 포함되지 않는다. [변환 경로](../portal-importer/src/main/java/kr/budget/portal/PortalImport.java) · [실제 수신기 재시험](../ops/test_portal.py)
