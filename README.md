# 부서예산

은행 경영지원팀·병원 행정부서·일반 기업의 직원이 교육비와 출장비를 신청하고 부서장과 재무 담당자가 승인·정산하는 경영지원 웹 애플리케이션이다. 같은 신청번호로 **승인 금액과 실제 집행액의 차이**를 추적한다.

**[공개 데모 열기](https://129-225-133-96.sslip.io/)** · [3분 체험 순서](#3분-체험)

별도 가입 없이 방문자마다 독립된 부서 예산 1,000,000원이 배정된다. 교육비 300,000원을 신청하고 부서장과 재무 담당자로 역할을 바꿔 승인과 정산을 끝낼 수 있다. 아래는 공개 서비스에서 정산한 화면이다. 화면, Oracle 원장, 결산표의 가용 예산은 820,000원이었고 사내 포털은 MySQL에 정산액 180,000원을 기록했다. 체험에는 합성 증빙을 사용한다. [정산과 재전송](docs/measurements/deployment-settlement.json) · [Oracle·MySQL 대사](docs/measurements/deployment-ledger.txt)

Java 21·Spring Boot·MyBatis·Oracle이 원장을 처리한다. JSP·jQuery는 신청·결재함, React·TypeScript는 직원 신청과 규정 비교·발행, PHP·MySQL은 사내 포털 수신에 사용한다. 첫 화면의 **내 신청함 시작**을 누르면 React 직원 신청함이 열린다. 기존 JSP 신청서에서 전환할 때도 작성 중인 값을 이어받는다. [두 화면의 신청 계약 시험](frontend/tests/employee-form.spec.ts) · [화면에서 원장까지 소스 따라가기](#소스-따라가기)

## 원장과 화면 검증

| 영역 | 검증 결과 |
| --- | --- |
| 결재함 첫 표시 | 실제 Oracle을 연결한 CI에서 부서장의 50건 결재함을 열었다. 최초 방문 10회 중앙값은 제목 LCP 56.0ms, 목록 표시 근사 시점 144.9ms였다. 같은 탐색에서 목록 API 응답 후 표시까지 64.8ms, API의 SQL 세 건에 7.22ms가 걸렸다. [요청별 분석과 원자료](docs/measurements/workspace-timing.md) |
| 결재함 조회 | 합성 신청 100만 건을 100개 부서에 나눠 넣었다. 한 부서의 결재함에서 앞선 9,000건 이후 같은 51건을 조회할 때 Oracle 논리 읽기는 OFFSET 307회, 키셋 13회였다. 실행 순서를 교대한 4회 모두 같은 결과였다. [실행 계획 원자료](docs/measurements/oracle-inbox-44781386053.zip) |
| 예산 원장과 복원 | 실제 Oracle 원장 시험 28건을 통과했다. 다른 세션이 신청을 수정하는 동안 계산 근거 조회는 직전 커밋의 금액을 읽었다. 같은 본부 예산을 동시에 배정해도 잔액을 초과하지 않았다. Data Pump로 별도 PDB에 복원한 결과, 전표 49건과 포털 송신 메시지 47건을 포함한 대사 결과 547행이 원본과 일치했다. [원장 시험](docs/measurements/oracle-cases-44809221277.json) · [복원 대사 원자료](docs/measurements/oracle-restore-44896984081.zip) |
| 정적 자산 캐시 | 자체 JS·CSS의 내용 해시를 URL에 반영했다. 로컬 Chrome 결재함 재방문 때 정적 자산 3개의 전송량은 22,034B에서 0B로 줄었고, 첫 방문 전송량은 101B 증가했다. [전후 5회 측정](docs/measurements/README.md) |
| 학회비 지원 규정 | 학회비 지원율을 80%에서 90%로 바꾼 뒤에도 이전 신청의 인정액 144,000원을 재현했다. 같은 실비 180,000원의 신규 신청은 162,000원, 이전 규정 복구 후 접수한 신청은 144,000원이었다. [발행·복구 계약 시험](src/test/java/kr/budget/claims/PostingContract.java) |

![재무 담당자의 교육비 정산 결과와 월 결산표](docs/images/settlement.png)

## 3분 체험

기본 조직인 **일반 기업 경영지원팀**에서 **내 신청함 시작**을 누른다. 같은 부서와 신청번호를 유지하고 **시연 역할**만 아래 순서대로 바꾼다.

| 역할 | 동작 | 원장 기준 결과 |
| --- | --- | --- |
| 직원 | 교육비 300,000원에서 **신청 제출**을 누른다. | 배정 1,000,000원 |
| 부서장 | 같은 신청에서 **사전승인**을 누른다. 지원율 80%와 한도 200,000원이 적용된다. | 약정 200,000원 |
| 직원 | **실제 지출액(원)** 225,000원, 증빙 종류 **지출 영수증**을 확인한다. **합성 증빙 첨부**를 누른 뒤 **정산 신청**을 누른다. 병원 학회비에는 참석 확인서도 첨부한다. | 재무 검토 대기 |
| 재무 담당자 | **정산 확정**을 누른다. | 약정 0원 · 집행 180,000원 · 가용 820,000원 |

신청 내역에서 실비 225,000원 중 직원이 부담한 45,000원과, 승인 약정 200,000원 중 집행하지 않아 가용 예산으로 돌아간 20,000원을 각각 확인할 수 있다.

직원으로 돌아가 **내 신청함**에서 같은 신청을 열면 정산액과 현재 가용 예산을 확인할 수 있다. 재무에서 보완을 요청한 건에는 보완 사유가 표시된다. 영수증을 첨부하면 서버가 확인한 종류와 금액이 표시되고 같은 신청에서 다시 제출할 수 있다. [개인 장부](docs/images/personal-ledger.png) · [PC 신청함](docs/images/employee-inbox.png) · [모바일 증빙 보완](docs/images/employee-receipt-mobile.png) · [화면 크기별 통합 시험](frontend/tests/employee-inbox.spec.ts)

개발자 도구를 열면 같은 신청번호로 화면과 HTTP 응답의 금액을 대조할 수 있다. [역할별 요청·응답과 재시도](docs/API.md)

### 다른 업무 체험

- 은행 데모에서 본부 재무 담당자가 지점에 300,000원을 배정하면 본부 가용액은 700,000원, 지점 가용액은 1,300,000원이다. **체험 부서**를 지점으로 바꿔 잔액을 확인한다. [지점 예산 배정 시험](frontend/tests/bank-allocation.spec.ts)
- 병원 행정부서에서 학회비 지원 규정을 바꿔 발행한 뒤 과거 신청과 새 신청의 지원액을 비교한다. [학회비 발행·복구 시험](frontend/tests/hospital-policy.spec.ts)
- **내 자격시험·교육·도서 장부**에서는 실제 지원받은 금액을 뺀 본인 부담액과 다음 교육 후 예상 잔액을 확인한다. 기록은 현재 브라우저 프로필에 보관되며 사이트 데이터를 지우면 삭제된다. [개인 장부 시험](frontend/tests/personal-ledger.spec.ts)

재무 담당자로 **전표 조회**와 **결산표 조회**를 열면 같은 신청의 약정 해제, 실제 집행액, 부서 가용액을 대조할 수 있다. [Chrome 회귀 시험](frontend/tests/claims.spec.ts)

## 브라우저 시험

공개 HTTPS에서 Chrome 시험 24건을 통과했다. 직원 신청함은 390·768·1440px에서 영수증 보완 후 정산 결과까지 확인했다. 교육비 월마감, 출장 결재, 학회비 규정 발행·복구, 지점 예산 배정, 방문자·부서 격리도 점검했다. 별도 부하·장애 조건을 쓰는 3건은 이 실행에서 건너뛰었다. TLS 1.3, Secure·HttpOnly·SameSite=Lax 쿠키, 업무 응답의 no-store, 비목표의 ETag 재검증과 정적 파일 재사용을 확인했다. [직원 신청함과 정산 결과](docs/measurements/deployment-workspace-sections.json) · [공개 전송 원자료](docs/measurements/deployment-https.json) · [재현 명령](docs/HTTPS.md#java-서비스-설치와-중지)

첫 방문 시 접수 결과와 실제 지출액 입력칸을 찾기 어려웠다. 폼에 접수 상태를 표시하고 정산 입력과 첨부·신청 버튼을 한 영역에 배치했다. 모바일 크기의 Chrome에서 영수증이 없는 정산을 차단한 뒤, 보완 요청과 재제출을 거쳐 180,000원을 정산하는 흐름을 확인했다. [배포 전후 원자료](docs/measurements/deployment-claim-ux.json)

CI에서는 실제 Oracle 원장과 송신함, HTTPS를 경유한 PHP 수신기, MySQL 수신 요약을 한 신청번호로 대조했다. Chrome에서 교육비 정산을 확정한 뒤 포털의 2차 수신액을 조회하고, 두 DB에서 같은 업무 공간·신청번호·차수·종류·금액(180,000원)을 확인했다. [실행 결과](https://github.com/kimdol/department-budget/actions/runs/44774362675/job/136420076607) · [재현 스크립트](ops/ci_https_proxy.sh)

원장과 실제 MySQL 포털 수신액을 한 화면에서 대조하려면 아래 HTTPS·포털 시험의 도구를 설치한 뒤 저장소 루트에서 `python ops/test_portal.py --browser`를 실행한다. 시험용 인증서를 신뢰하는 Java 송신기가 PHP로 정산을 전달하고 Chrome이 포털의 2차 수신액 180,000원을 읽는다. 이 로컬 시연에서 Java 업무 DB는 H2다.

로컬 HTTPS 재현은 프런트엔드 빌드 후 저장소 루트에서 `python ops/test_https.py`로 실행한다. Java 21·Maven·Chrome·Node.js·OpenSSL이 필요하다. 시험 도구는 하루짜리 인증서를 임시 생성하고 루프백 Tomcat에 연결한다. 쿠키 값과 비밀 키는 원자료에 저장하지 않는다. [TLS 및 캐시 실제 응답](docs/measurements/loopback-https.json)

포털 수신 시험은 PHP 8.1 이상과 PDO MySQL 확장, MySQL 8.4의 mysql·mysqld를 PATH에 둔 뒤 저장소 루트에서 `python ops/test_portal.py`로 실행한다. 별도 임시 데이터 디렉터리와 루프백 포트를 사용하며 시험 종료 시 서버를 닫는다. 승인과 정산을 각각 두 번 보내 실제 수신 이력 2건과 정산액 180,000원을 확인했다. [수신 시험 원자료](docs/measurements/portal-receipts.json)

Chrome, Java 21, Maven과 Node.js가 설치된 환경에서 `frontend` 디렉터리의 `npm ci`, `npm run build`, `npx playwright test`를 차례로 실행한다. 시험은 루프백 주소에서 전용 메모리 DB와 Tomcat을 시작한다.

## 실행과 검증

배포용 WAR에서 추출한 설치 SQL을 빈 Oracle 스키마에 적용했다. 같은 SQL을 다시 실행하면 ORA-20001로 중단되는 것을 확인했다. [설치 시험](https://github.com/kimdol/department-budget/actions/runs/44765732583) · [신청 한 건으로 Oracle 직접 실습](docs/ORACLE-WALKTHROUGH.md)

Java 21과 Maven을 사용한다. `mvn test`는 계산 규칙과 로컬 H2 계약 시험을 실행한다. 실제 Oracle 통합 시험은 **비어 있는 전용 시험 스키마**를 준비하고 `BUDGET_TEST_EMPTY_SCHEMA=yes`, `BUDGET_DB_URL`, `BUDGET_DB_USER`, `BUDGET_DB_PASSWORD`를 환경 변수로 설정한 뒤 같은 명령으로 실행한다. 이 모드는 시험 시작 시 스키마를 생성한다.

frontend 디렉터리에서 npm ci와 npm run build를 실행한다. 저장소 루트로 돌아와 애플리케이션 DB 접속 환경 변수를 설정하고 `mvn package`, `java -jar target/department-budget-0.1.0.war`를 실행한다. 최초 기동 전에 같은 WAR에서 업무 테이블과 배치 메타데이터를 추출해 설치한다. [Oracle 설치·마이그레이션·백업 및 복원](docs/ORACLE.md)을 참조한다. 로컬 HTTP 시험에 한해 `HTTPS_ONLY=false`를 지정한다. 공개 배포와 실측 성능은 [검증 현황](docs/PROGRESS.md)에서 확인할 수 있다.

## 소스 따라가기

[아키텍처·ERD와 업무별 소스 안내](docs/ARCHITECTURE.md)에서 신청 한 건의 전표와 병원 학회비 규칙 변경을 파일·메서드별로 따라갈 수 있다.

1. [결재함](src/main/webapp/WEB-INF/views/portal.jsp)에서 제출하면 [화면 요청 코드](src/main/resources/static/assets/portal.js)가 요청 키를 보존한다.
2. [DemoPortal](src/main/java/kr/budget/web/DemoPortal.java)은 세션에서 방문자 공간과 역할을 결정한다.
3. [ClaimService](src/main/java/kr/budget/claims/ClaimService.java)는 신청 재전송을 판별하고 승인·정산 단계에서 잠금, 상태 변경, 전표, 잔액을 한 트랜잭션으로 처리한다.
4. [BudgetMapper](src/main/java/kr/budget/claims/BudgetMapper.java)의 바인딩 SQL이 같은 공간의 신청과 전표를 연결한다. [원장 계약 시험](src/test/java/kr/budget/claims/PostingContract.java)은 중복 요청·재시도와 경합 후 금액을 검사한다.
5. [지원액 계산기](src/main/java/kr/budget/policy/ExpenseFormula.java)의 입력은 원 단위 금액·지원율·한도·일수다. [비교 사례](src/test/java/kr/budget/policy/ExpenseFormulaTest.java)에서 학회비 후보 수식만 바꾸고 일반 교육비의 기존 결과를 대조한다. [규정 편집기](frontend/src/policy.tsx)에서 학회 참가비를 선택하고 재무 담당자로 검토 요청, 부서장으로 발행한다. [PolicyService](src/main/java/kr/budget/policy/PolicyService.java)에서 기존 규정의 세대와 비교한다. 원래 수식 복구도 같은 절차를 거친다.
