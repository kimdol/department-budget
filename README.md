# 부서예산

은행·병원·일반 기업에서 직원이 교육비와 출장비를 신청하고, 부서장과 재무 담당자가 같은 신청번호로 승인·정산하는 웹 서비스.

**[공개 데모 열기](https://129-225-133-96.sslip.io/)** · [3분 체험](#3분-체험) · [설계와 검증](#설계와-검증)

개인 프로젝트 · 2026년 11월 · Oracle Cloud HTTPS 배포<br>
Java 21 · Spring Boot · MyBatis · Oracle · JSP/jQuery · React/TypeScript

## 핵심 문제 해결

- **정산 응답 유실과 재시도** — 저장 직후 응답이 끊기면 사용자는 처리 결과를 알기 어렵다. 같은 신청번호와 처리 차수로 재시도할 때, 서버가 원장과 신청 행을 잠그고 상태를 확인해 이미 끝난 정산의 전표를 유지한다. 공개 데모에서 응답 유실 후 재전송해도 **정산 전표 1건, 집행액 180,000원**이 유지됐다. [정산 코드](src/main/java/kr/budget/claims/ClaimService.java#L270) · [공개 데모 시험 결과](docs/measurements/deployment-settlement-response-loss.json)
- **결재함의 깊은 페이지 조회** — OFFSET 조회는 뒤쪽 페이지를 열 때 앞 행을 계속 건너뛴다. 직전 페이지의 마지막 접수 순번을 기준으로 다음 구간을 읽도록 바꿨다. 실제 Oracle에 합성 신청 100만 건을 넣고 같은 51건을 조회했을 때 **논리 읽기 307회 → 13회**를 기록했다. [측정 조건과 실행 계획](docs/measurements/README.md#oracle-결재함-100만-건-측정)
- **재방문 시 CSS·JS 재전송** — 내용이 달라진 파일만 새 주소를 쓰도록 파일 해시를 URL에 넣었다. 로컬 Chrome 5회 비교에서 CSS·JS·jQuery의 재방문 전송량은 **22,034B → 0B**, 첫 방문 전송량은 **101B 증가**했다. [전후 측정](docs/measurements/README.md#정적-자산-재사용)

## 3분 체험

**일반 기업 경영지원팀 → 내 신청함 시작**을 누른다. 가입 없이 합성 데이터로 체험할 수 있다.

1. **직원** — 교육비 300,000원을 **신청 제출**한다.
2. **부서장** — 같은 신청을 **사전승인**한다. 승인 약정은 200,000원이다.
3. **직원** — 실제 지출액 225,000원에 **합성 증빙 첨부** 후 **정산 신청**을 누른다.
4. **재무 담당자** — **정산 확정** 후 **전표 조회**와 **결산표 조회**를 연다. 약정 잔액 **0원**, 집행액 **180,000원**, 가용 예산 **820,000원**을 확인한다.

![재무 담당자의 교육비 정산 결과와 월 결산표](docs/images/settlement.png)

[영수증 보완·대화형 도우미·은행 및 병원 체험 안내](docs/EMPLOYEE-INBOX.md)

## 설계와 검증

JSP·jQuery 결재함과 React·TypeScript 직원 신청함은 같은 Spring Boot API를 사용한다. 서버가 세션의 역할과 부서를 확인하고 MyBatis를 통해 신청 상태와 전표를 Oracle에 기록한다. 포털로 보낼 이벤트도 같은 트랜잭션에 저장한다. PHP·MySQL 포털에는 이후 결과가 전달되며, 전송 장애가 나면 저장된 이벤트로 재시도한다.

- [아키텍처·ERD와 핵심 소스](docs/ARCHITECTURE.md) · [신청번호로 API와 원장 대조](docs/API.md)
- [Oracle 통합 시험](docs/measurements/oracle-cases-44809221277.json) · [MySQL 동시 수신과 중복 처리 검증](docs/MYSQL-CONCURRENT-RECEIPTS.md)
- [Oracle 설치·복원](docs/ORACLE.md) · [HTTPS 배포·복구](docs/HTTPS.md)

<details>
<summary>로컬 실행과 테스트</summary>

Java 21, Maven, Node.js, Chrome을 준비한다. `frontend`에서 `npm ci`와 `npm run build`를 실행한다.

- **브라우저 체험과 시험** — `frontend`에서 `npx playwright test`를 실행한다. 테스트 설정이 Tomcat과 임시 H2를 시작한다.
- **실제 Oracle로 실행** — [Oracle 초기 설치](docs/ORACLE.md#최초-설치)를 마친 뒤 `BUDGET_DB_URL`, `BUDGET_DB_USER`, `BUDGET_DB_PASSWORD`를 환경 변수로 설정한다. 저장소 루트에서 `mvn package`, `java -jar target/department-budget-0.1.0.war`를 실행한다. 로컬 HTTP로 실행할 때는 `HTTPS_ONLY=false`를 설정한다.

</details>
