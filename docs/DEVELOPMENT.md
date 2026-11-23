# 개발·테스트·운영 안내

[프로젝트 개요](../README.md) · [아키텍처와 요구사항](ARCHITECTURE.md) · [문제 해결 사례](TECHNICAL-CASES.md)

## 신뢰성·보안·예외 처리

| 발생할 수 있는 문제 | 방어 설계와 확인 결과 |
| --- | --- |
| 금액·기간·규정 입력 오류 | 서버가 신청 가능한 금액과 기간을 검사하고, 수식은 허용한 변수·연산으로 평가합니다. 수식 단위 시험은 허용 범위를 넘는 입력과 잘못된 연산을 검사합니다. [신청 검증](../src/main/java/kr/budget/claims/ClaimService.java) · [수식 단위 시험](../src/test/java/kr/budget/policy/ExpenseFormulaTest.java) |
| 권한 없는 결재와 증빙 열람 | 화면과 별개로 서버가 세션 역할·부서·신청을 검사합니다. HTTP 시험에서 직원의 승인과 CSRF 토큰 없는 신청은 403, 타 방문자의 신청은 404, 체험 종료 뒤 기존 세션 조회는 401을 확인했습니다. 공개 Chrome에서 다른 부서의 증빙도 404였습니다. [세션 권한 코드](../src/main/java/kr/budget/web/DemoPortal.java) · [HTTP 시험](../src/test/java/kr/budget/web/PortalHttpTest.java#L98) · [공개 브라우저 시험](../frontend/tests/claims.spec.ts#L54) |
| 예산 초과·중복 집행 | 잔액과 신청을 잠근 상태에서 검사하고 상태·전표·송신 대기 행을 함께 기록합니다. 실제 Oracle의 동시 승인과 공개 HTTPS의 응답 유실 시험에서 중복 없이 지정 금액만 남는 것을 확인했습니다. [재시도와 동시 승인](TECHNICAL-CASES.md#정산-응답-유실과-중복-전표) |
| 외부 포털 중단 또는 응답 유실 | DB에 보낸 내용과 식별자를 저장하고 HTTP 실패를 기록합니다. 수신 성공 뒤 응답을 잃어도 같은 ID로 다시 처리할 수 있습니다. 포털 장애 시험은 실패 화면, 복구 후 재전송, 최종 수신 금액을 검사합니다. [송신기 로그와 재전송](../src/main/java/kr/budget/portal/PortalRelay.java) · [복구 시험](../frontend/tests/portal-retry.spec.ts) |
| 세션·업무 정보의 브라우저 노출 | HTTPS에서 Secure·HttpOnly·SameSite 세션 쿠키와 민감 응답의 `no-store`를 사용합니다. 정적 자산과 공개 비목 코드표에만 별도의 캐시 정책을 적용합니다. 공개 브라우저에서 실제 응답을 확인했습니다. [설정](../src/main/resources/application.properties) · [HTTPS 시험](../frontend/tests/https.spec.ts) · [공개 응답 기록](measurements/deployment-https.json) |

공개 체험은 합성 증빙과 별도 방문자 공간을 사용합니다. MyBatis 조회의 신청번호·부서 조건은 SQL 파라미터로 바인딩합니다. 시연의 역할 전환·열람 범위를 검증했으며 실제 직원 인증과 실물 증빙의 접근 정책은 도입 범위에 포함될 때 별도로 검증해야 합니다. [증빙 SQL](../src/main/java/kr/budget/claims/EvidenceMapper.java) · [체험 데이터 보관과 삭제](DEMO-OPERATIONS.md#보관-기준)

## 테스트와 검증

| 시험 환경 | 확인 범위와 증거 |
| --- | --- |
| 단위 시험 · JUnit | 수식과 조건 트리의 연산·입력 경계, 마감 비교 로직을 확인합니다. [수식](../src/test/java/kr/budget/policy/ExpenseFormulaTest.java) · [조건 트리](../src/test/java/kr/budget/policy/EligibilityRuleTest.java) · [결산표 비교](../src/test/java/kr/budget/closing/StatementComparisonTest.java) |
| 로컬 통합 시험 · H2, Tomcat | 접수, 승인·정산, 권한, 세션 종료, 취소·재시도 등을 빠르게 확인합니다. Oracle 특화 계약 시험의 결과는 별도 기록으로 확인합니다. [원장 계약](../src/test/java/kr/budget/claims/LocalPostingTest.java) · [HTTP 경로](../src/test/java/kr/budget/web/PortalHttpTest.java) |
| 실제 Oracle 통합 시험 · GitHub Actions | 실행 `44809221277`의 원장 계약 **28건**에 동시성·재시도·규정 버전·월마감 재시작을 포함합니다. 별도 복원 실행은 원본과 다른 PDB의 대사 결과를 비교했습니다. [시험별 결과](measurements/oracle-cases-44809221277.json) · [CI 실행](https://github.com/kimdol/department-budget/actions/runs/44809221277) · [PDB 복원 대사](measurements/oracle-restore-44896984081.zip) |
| 실제 MySQL 수신 · PHP/PDO | 독립 DB를 구성한 세 실행에서 같은 이벤트의 기본 키 대기, 응답 204·409와 수신 이력·요약 건수를 확인했습니다. [검증 명령과 결과](MYSQL-CONCURRENT-RECEIPTS.md) |
| 브라우저 E2E · Playwright/Chrome | 공개 HTTPS의 화면 정리 배포에서 **24건 통과·3건 건너뜀**으로 기록했습니다. 방문자 격리, 영수증 보완·재제출, 세 화면 폭, TLS·쿠키·캐시를 시험별로 확인할 수 있습니다. [공개 배포 결과](measurements/deployment-workspace-sections.json) · [시험 소스](../frontend/tests) · [HTTPS 기록](measurements/deployment-https.json) |
| 성능 검증 · Oracle 실행 계획, Chrome Resource Timing | 결재함 SQL의 논리 읽기·DB p95, 동시 승인 응답, 브라우저 재방문 전송량을 측정했습니다. 별도 탐색 시험은 응답의 요청 ID로 브라우저와 서버·SQL 소요 시간을 연결했습니다. [전후 조건과 원자료](measurements/README.md) · [요청별 계측](measurements/workspace-timing.md) |

공개 E2E, 로컬 H2, 실제 Oracle CI와 첫 방문자 체험은 서로 다른 조건입니다. 첫 방문자 한 명이 로컬 H2에서 안내 없이 영수증을 보완하고 다시 제출하는 데 약 40초가 걸렸다는 관찰 기록이 있습니다. [첫 방문자 체험 기록](EMPLOYEE-INBOX.md#첫-방문자-체험-결과)

## 로컬 실행과 설치

JDK 21, Maven, Node.js 22, Chrome을 준비합니다. Node.js 22는 CI에서 사용하는 버전입니다. Oracle을 직접 설치하거나 HTTPS 도구를 실행할 때는 Python 3과 각 안내에 명시한 SQL*Plus 또는 OpenSSL을 준비합니다.

```sh
git clone https://github.com/kimdol/department-budget.git
cd department-budget
cd frontend
npm ci
npm run build
npx --no-install playwright test
cd ..
mvn -B -ntp test
```

Playwright는 로컬 Tomcat과 임시 H2를 시작합니다. 브라우저를 사용하는 시험이 끝나면 테스트 서버가 종료됩니다. 이 흐름으로 합성 신청을 재현할 수 있습니다. 루프백 HTTPS가 필요하면 프론트엔드 빌드 후 저장소 루트에서 `python ops/test_https.py`를 실행합니다. [브라우저 설정](../frontend/playwright.config.ts) · [HTTPS 시험 도구](../ops/test_https.py)

### Oracle 설치와 WAR 실행

1. 위의 프론트엔드 빌드를 마치고 저장소 루트에서 `mvn -B -ntp package`로 WAR를 생성합니다.
2. [Oracle 설치 안내](ORACLE.md#최초-설치)에 따라 빈 전용 스키마에 WAR에서 생성한 설치 SQL을 적용합니다. 기존 DB는 [버전별 마이그레이션 안내](ORACLE.md#기존-버전에서-올리기)를 따릅니다.
3. 실행 환경에 `BUDGET_DB_URL`, `BUDGET_DB_USER`, `BUDGET_DB_PASSWORD`를 지정합니다. 자격 증명은 환경에서만 전달합니다. 로컬 PC에서 HTTP로만 실행할 때는 `SERVER_ADDRESS=127.0.0.1`, `HTTPS_ONLY=false`를 지정합니다. [환경 변수와 기본값](../src/main/resources/application.properties)
4. 저장소 루트에서 `java -jar target/department-budget-0.1.0.war`를 실행하고 `http://127.0.0.1:8080/`의 **내 신청함 시작**으로 체험합니다. 일반 기업의 기본 부서에는 합성 예산 1,000,000원이 배정됩니다.

실제 Oracle 계약 시험을 따로 실행할 때는 초기화 스크립트가 시험용 테이블을 만드는 **비어 있는 전용 스키마**를 준비합니다. 해당 접속 변수와 `BUDGET_TEST_EMPTY_SCHEMA=yes`를 설정해 `mvn -B -ntp -Dtest=OraclePostingTest test`를 실행합니다. 이 스키마는 위에서 설치한 체험용 DB와 분리합니다. [Oracle 시험 초기화 조건](../src/test/java/kr/budget/claims/OraclePostingTest.java) · [원장 시험 결과 검사](../ops/check_oracle_contract.py)

## 디렉터리 구조

```text
department-budget/
├── src/main/java/kr/budget/     Spring MVC, 업무 규칙, MyBatis 매퍼
├── src/main/resources/db/       버전별 Oracle 스키마
├── src/main/webapp/              JSP 화면
├── src/test/java/                JUnit·HTTP·Oracle 계약 시험
├── frontend/src/                 React 신청함과 규정 작업실
├── frontend/tests/               Playwright 브라우저 시험
├── portal/                       PHP 수신·조회와 MySQL 스키마
├── gnuboard/                     그누보드5 정산 조회 플러그인
├── portal-importer/              Apache Camel JSON·CSV 변환과 대조
├── ops/                          검증·패키징·배포·복구 도구
├── .github/workflows/            Oracle 및 ARM 검증 자동화
└── docs/                         아키텍처·사용 안내·측정 원자료
```

## 개발·운영 및 자동화

- 기능 변경과 회귀 시험은 관련 커밋에서 함께 확인할 수 있습니다. `main`의 변경 이력과 연계한 시험은 [Git 커밋](https://github.com/kimdol/department-budget/commits/main/) 및 [실행 기록](PROGRESS.md)에 남아 있습니다.
- `Verify Oracle Ledger`는 푸시 또는 수동 실행으로 원장 계약, Oracle 설치·복원, Caddy·Chrome과 MySQL 연계를 검사합니다. `main`이 통과하면 실험에 사용한 WAR·설치 SQL·해시를 배포 묶음에 남깁니다. 운영 VM에는 검증된 묶음을 선택해 배치합니다. [CI 정의](../.github/workflows/verify.yml) · [별도 ARM 검증](../.github/workflows/verify-arm.yml) · [WAR 배포와 복귀](HTTPS.md)
- 장애 시 Java·Caddy·PHP-FPM 서비스 상태를 확인하고 Oracle 전표와 MySQL 요약을 같은 신청번호로 대조합니다. 포털 실패는 이벤트 ID의 실패·성공 로그를 연결하고, 표시 지연은 요청 ID로 HTML·목록 API·SQL 구간을 대조합니다. [운영 점검](DEMO-OPERATIONS.md#운영-점검) · [요청 ID 계측](measurements/workspace-timing.md#같은-요청을-대조하는-방법) · [Oracle 백업과 복원](ORACLE.md#백업과-별도-스키마-복원)

## 회고와 향후 개선

- **첫 렌더링과 데이터 일치** — 첫 React 구현에서는 접수 성공 뒤에도 목록이 비어 보였습니다. 모듈 로딩을 지연하는 시험에서 재현하고 스냅샷과 구독을 한 계약으로 묶었습니다. 이 시험을 정상 로딩의 화면 시험과 분리해 표시 누락이 다시 생기는지 확인합니다. [원인·수정·회귀 시험](TECHNICAL-CASES.md#react-신청함의-첫-렌더링)
- **DB 수치와 화면 체감** — 깊은 조회의 논리 읽기와 DB p95는 줄었습니다. 별도의 결재함 표시 측정에서는 첫 방문 목록 표시 근사값이 144.90ms였고, 목록 API 응답 후 표시까지 64.80ms가 걸렸습니다. 다음 표시 성능 개선 후보는 그 구간의 목록 구성과 프레임 대기입니다. 원인 분리와 개선 후 전후 비교는 별도 실험이 필요합니다. [서버·화면 구간별 결과](measurements/workspace-timing.md)
- **공개 데모 운영** — 단일 무료 VM으로 운영 범위를 줄였고, 무료 자원의 유휴 중지나 용량 제약에 따라 서비스 재개가 필요할 수 있습니다. 실제 업무에 적용한다면 사용자 인증, 권한 변경 이력, 실물 증빙 저장 정책을 추가로 검증해야 합니다. 상용 UI·리포팅 전환은 별도 실습 범위입니다. [무료 자원 제약과 서비스 재개](HTTPS.md#무료-자원과-서비스-재개) · [화면 기록](EMPLOYEE-INBOX.md#공개-데모-화면-기록)
