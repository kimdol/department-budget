# 인수 현황

보완 중 교육비를 출장비로 바꾸면 원신청의 약정 200,000원을 해제하고 새 신청을 결재 대기로 접수한다. 3일 출장비 225,000원의 인정액은 150,000원으로 다시 계산된다. 두 신청에 서로의 신청번호와 당시 규정이 남는다. 같은 요청을 재전송해도 원신청의 전표는 승인·취소 두 건이고, 잘못된 금액으로 재신청하면 기존 약정이 유지됐다. [서비스](../src/main/java/kr/budget/claims/ClaimService.java) · [두 신청의 이력 확인](../frontend/tests/keyboard-review.spec.ts)

역할 전환을 보호한 뒤에도 Caddy 통합 시연에서 증빙 첨부 직후 열린 판정 근거가 사라졌다. 첨부 POST는 이미 끝났지만 결재함을 다시 읽는 GET은 진행 중이었다. 이전 구현은 첨부 버튼만 비활성화해 같은 신청의 조회 버튼을 누를 수 있었다. 변경 후 해당 신청 행은 목록 갱신까지 입력을 받지 않으며 실패하면 조작과 포커스를 복원한다. 회귀 시험은 결재함 GET을 보류한 상태에서 첨부 응답 성공과 행의 잠금을 각각 확인하고, 응답을 재개한 뒤 판정 근거를 열어 정산을 계속한다. [실패한 프록시 시연](https://github.com/kimdol/department-budget/actions/runs/44792613976/job/136483914075) · [브라우저 회귀 시험](../frontend/tests/claims.spec.ts)

V020에서 11월 마감 부서의 12월 규정을 예약해 둔 뒤 이전 버전의 기간 변경 SQL을 실행했다. H2에서 SQL은 한 행을 갱신했고 규정 예약은 처리되지 않았다. V021은 기존 예약의 원래 부서 기간을 채우고 외래 키로 연결한다. 같은 SQL이 무결성 오류로 거절되고 기존 초안·검토 해시와 대상 기간은 보존됐다. 서비스의 정상 개시 경로는 예약 발행 후에 기간을 갱신한다. [변경 전후 재현](../src/test/java/kr/budget/claims/ScheduledPolicyMigrationTest.java) · [정상 개시와 중복 요청 시험](../src/test/java/kr/budget/claims/PostingContract.java) · [복귀 시 호환 범위](HTTPS.md)

병원 학회비 지원율을 80%에서 90%로 바꾸는 규정을 부서장이 다음 회계 기간에 예약했다. HTTPS Chrome에서 예약 취소·재예약, 당월 버전 0 유지, 다음 달 개시 후 버전 1과 실비 180,000원의 지원 예정액 162,000원을 확인했다. H2 계약 시험에서는 예약 후 초안의 검토값을 바꾸자 기간 개시가 409로 거절되고 기존 마감 상태와 예약이 유지됐다. 값을 복원한 뒤 개시를 두 번 요청해도 새 발행은 한 건이었다. [브라우저 시험](../frontend/tests/hospital-policy.spec.ts) · [원장 계약](../src/test/java/kr/budget/claims/PostingContract.java)

Oracle·MySQL·Caddy 브라우저 시연에서 직원으로 전환한 직후 증빙 요건을 열면 ‘판정 근거’가 클릭 전에 화면에서 사라져 시험이 중단됐다. 역할 선택값은 즉시 바뀌지만 새 결재함은 비동기로 도착해 기존 화면을 교체했다. 업무 버튼을 누를 수 있는 시점을 새 역할의 결재함 표시 이후로 옮겼다. 회귀 시험은 역할 변경 응답을 직접 보류하고 이 구간에 이전 신청·결재 화면을 조작할 수 없는지 검사한다. [실패 실행](https://github.com/kimdol/department-budget/actions/runs/44790716638/job/136477325495) · [회귀 시험](../frontend/tests/employee-form.spec.ts)

직원은 비용 신청을 작성하다가 React 화면으로 전환해 입력 내용을 이어갈 수 있다. JSP와 React는 같은 제출·미리보기 처리기를 쓴다. 로컬 HTTPS Chrome에서 두 화면 모두 금액 0원 입력 시 금액란에 포커스가 남고, 다른 탭에서 역할을 바꾼 뒤 제출하면 권한 오류가 표시됐다. 직원 역할로 돌아와 Enter로 제출한 뒤 승인하면 약정 200,000원, 가용액 800,000원이었다. [React 신청 화면](../frontend/src/employee.tsx) · [화면 전환 계약 시험](../frontend/tests/employee-form.spec.ts)

수식은 원문 SHA-256과 평가기 버전으로 구문 트리를 찾아 매번 그 신청의 입력으로 계산한다. 최근 트리 256개를 보존한다. 같은 트리에 서로 다른 실비 500개를 병렬로 넣은 결과와, 캐시에서 빠진 이전 수식을 재해석한 결과를 확인했다. [평가기](../src/main/java/kr/budget/policy/ExpenseFormula.java) · [수식 시험](../src/test/java/kr/budget/policy/ExpenseFormulaTest.java)

비용 신청의 비목 선택과 재무 결산표가 공개 비목 코드표를 공유한다. 로컬 HTTPS 시험에서 최초 조회는 200, 응답 본문의 SHA-256과 일치하는 ETag를 보낸 조회는 본문 없는 304, 오래된 태그를 보낸 조회는 200이었다. 같은 시험에서 방문자 예산 응답의 `no-store`도 확인했다. [코드표 원본](../src/main/resources/catalog/expense-categories.json) · [응답 검증](../frontend/tests/https.spec.ts)

로컬 H2에서 서로 다른 요청번호로 본부 예산 1,000,000원에 각각 600,000원을 동시에 배정했다. 한 건은 성공하고 한 건은 가용액 초과로 거절됐으며, 이력에는 600,000원 한 건만 남았다. 본부 잔액 400,000원과 지점 잔액 1,600,000원의 합은 최초 합계와 일치했다. [동시 배정 계약 시험](../src/test/java/kr/budget/claims/PostingContract.java)

본부 재무가 지점에 예산을 배정하면 한 트랜잭션에서 두 부서의 배정액과 배정 이력을 갱신한다. 로컬 원장 시험에서 약정 200,000원이 있는 본부의 800,001원 배정을 거절하고, 300,000원 배정을 재전송해도 배정 이력이 한 건임을 확인했다. HTTPS Chrome에서 본부 가용액 700,000원과 지점 가용액 1,300,000원을 확인했다. [배정 서비스](../src/main/java/kr/budget/claims/BranchAllocation.java) · [브라우저 시험](../frontend/tests/bank-allocation.spec.ts)

Linux CI에서 Caddy의 HTTP 308 이동, 경로·쿼리 보존, TLS 1.3, 결재함·규정 편집기 요청의 동일 출처를 확인했다. 별도 CI에서 패키지 WAR의 JDBC 드라이버로 Oracle Free에 TCP 접속해 업무·배치 스키마 조회 5건을 통과했고 같은 세션에 TCPS를 요구하면 검사가 실패했다. OCI 배포 VM의 DB 전송 방식과 공개 인증서 검증은 대기 중이다. [웹 응답 원자료](measurements/proxy-transport-44788840479.json) · [JDBC 검사 실행](https://github.com/kimdol/department-budget/actions/runs/44789061101/job/136471567405)

재무 결산표에서 비목별 집행액을 펼치면 해당 기간에 합산된 원신청을 50건씩 조회한다. 신청을 펼치면 원전표와 다음 달 환입을 확인할 수 있다. 로컬 HTTPS Chrome에서 같은 신청번호가 최초 기간에는 180,000원, 다음 달에는 −30,000원으로 집계되는 것을 확인했다. [시연 시험](../frontend/tests/claims.spec.ts)

월마감에서 전표 합계와 부서 잔액의 약정·집행 차이를 금액으로 제공한다. Oracle은 MyBatis 호출형 매퍼의 PL/SQL 출력 매개변수를 사용하고 H2는 같은 계약의 집계 SQL을 사용한다. 로컬 계약 시험에서 정산 후 집행 잔액을 700원 낮추자 차이 +700원, 마감 요청 409, 확정 보고서 0건을 확인했다. 시험 데이터를 복원한 후에만 가용액 820,000원으로 마감됐다. [대사 코드](../src/main/java/kr/budget/closing/LedgerReconciliation.java) · [마감 인수 시험](../src/test/java/kr/budget/claims/PostingContract.java)

키보드로 교육비를 신청하고 역할을 바꿔 승인·보완·재제출·정산했다. 보완 사유가 비거나 공백뿐이면 입력란으로 포커스가 돌아간다. 승인·보완 후에는 갱신된 같은 신청의 제목으로 이동한다. Chrome에서 최종 가용액 820,000원까지 확인했고 로컬 HTTPS 브라우저 시험 8건이 통과했다. [키보드 인수 시험](../frontend/tests/keyboard-review.spec.ts)

재무 담당자가 규정 작업실에서 같은 부서·비목의 실제 신청을 50건씩 열어 사전승인 영향을 비교할 수 있다. 신청별 당시 수식·평가기·결재 경로를 사용하며 후보의 입력별 오류는 해당 행에 표시한다. 51건 계약 시험에서 이전 규정 신청 1건의 당시 인정액은 200,000원, 이후 50건은 150,000원, 후보는 모두 300,000원이었다. 타 부서·타 비목 제외와 원장 잔액·전표 수 보존을 확인했다. [규정 서비스](../src/main/java/kr/budget/policy/PolicyService.java) · [원장 계약](../src/test/java/kr/budget/claims/PostingContract.java)

직원이 지원 예정액을 확인하면 정산 때 필요한 증빙도 표시된다. 병원 학회비는 참석 확인서와 영수증, 같은 부서의 일반 교육비는 영수증을 안내했다. 계약 시험에서 일반 교육비에 참석 조건을 발행하고 복구하자 사전 안내도 함께 변경됐다. 로컬 Java 43건 및 HTTPS 브라우저 7건 통과. [병원 시연](../frontend/tests/hospital-policy.spec.ts)

최종 승인 이력 쓰기가 실패한 경우의 전체 롤백을 포함한 실제 Oracle 원장 계약 19건이 모두 통과했다. 로컬 HTTPS에서는 자체 CSS·JS·규정 편집기의 URL과 응답 바이트 해시를 대조했다. 결재함 재방문 시 정적 자산 3개의 전송량이 5회 모두 0B였고 민감 응답은 no-store였다. [Oracle 실행](https://github.com/kimdol/department-budget/actions/runs/44785908320) · [Chrome 측정 원자료](measurements/assets-after.json)

승인 업무의 마지막 SQL이 실패하는 상황을 원장 계약에 추가했다. 시험 전용 신청에 동일 차수의 승인 이력을 먼저 넣어 고유키 충돌을 유발했다. H2에서 예외 후 상태는 결재 대기, 가용액은 100만 원, 전표와 송신 대기는 모두 0건이었고 기존 이력은 보존됐다. 충돌용 행을 제거한 뒤 같은 승인을 두 번 호출해 가용액 80만 원, 약정 전표 1건, 송신 대기 1건을 확인했다. 로컬 Java 시험 43건 통과. [업무 트랜잭션 시험](../src/test/java/kr/budget/claims/PostingContract.java)

발행 API가 부서장의 검토 화면에 전달한 초안 해시를 요구한다. 검토 후 사례 일수만 달라진 경우 409, 기존 활성 세대 0을 확인했다. 변경된 사례로 다시 검토한 뒤에는 세대 1과 그때의 초안 번호·SHA-256이 함께 저장됐다. V018은 이전 발행 기록을 보존한다. 로컬 Java 시험 42건, HTTPS 브라우저 시험 7건 통과. [실습 SQL](ORACLE-WALKTHROUGH.md) · [발행 계약](../src/test/java/kr/budget/claims/PostingContract.java)

결재 이력 화면과 API에서 직원 접수 → 부서장 승인 → 실비 제출 → 재무 정산을 역할·차수·금액과 함께 조회한다. 이전에는 일반 교육비의 접수·단일 승인·정산 확정이 검토 이벤트에 남지 않았다. 세 업무 트랜잭션에 각각 이력 쓰기를 추가했다. 재요청 후에도 대표 신청의 이벤트는 4건이며 별도 방문자가 같은 이력을 요청하면 404다. 로컬 Java 41건 및 HTTPS 브라우저 7건 통과. [HTTP 계약](API.md) · [중복 처리 계약](../src/test/java/kr/budget/claims/PostingContract.java)

Oracle Free에서 현재 WAR(c7f477c)로 200,000원을 약정하고 실비 225,000원에 대해 180,000원을 정산한 뒤, 다음 달에 30,000원을 환입했다. 같은 스키마를 사용하는 이전 WAR(d45c847)로 복귀하고 새 Chrome 세션에서 출장비 300,000원을 승인했다. 원래 교육비 신청에는 전표 3건, 약정 잔액 0원, 환입 후 순집행 150,000원이 남았고 귀속 월까지 복귀 전후가 일치했다. 이 검증은 두 WAR가 같은 V017 스키마를 사용하는 Linux CI에서 수행했다. [실행 기록](https://github.com/kimdol/department-budget/actions/runs/44783938530) · [두 WAR 해시와 Oracle 대사 원자료](measurements/war-rollback-44783938530.zip) · [역할별 API 안내](API.md)

결재함에서 사용하지 않는 수식·증빙 조건 등 다섯 열을 목록 매퍼에서 제외했다. 50개 행을 반환하는 부서장 목록 본문이 22,454바이트에서 12,904바이트로 줄었다. 계산 근거 조회와 커서 이동 후 승인까지 로컬 HTTPS에서 확인했다. [전후 측정](measurements/README.md) · [목록 매퍼](../src/main/java/kr/budget/claims/BudgetMapper.java)

체험 시작 전후의 JSESSIONID를 Chrome에서 비교하는 시험이 실패했다. 시작 처리가 익명 세션에 업무 공간과 역할을 추가하면서 식별자를 그대로 사용했다. 최초 업무 공간을 할당할 때 서블릿 컨테이너가 식별자를 갱신하도록 바꿨다. 변경 후 Secure·HttpOnly·SameSite=Lax를 유지하며 값이 달라졌다. 체험 종료 직후 이전 영수증 URL은 401, 새 체험 시작 뒤에는 404였고 다른 방문자의 신청은 그대로였다. 로컬 Java 41건, HTTPS 브라우저 7건 통과. [세션 회귀 시험](../frontend/tests/https.spec.ts) · [이전 영수증 접근 시험](../frontend/tests/claims.spec.ts)

Oracle Free에서 신청 당시 규정, 증빙 보완, 1,000회 미리보기의 전표 보존을 포함한 원장 계약 17건을 실행했다. 실패·오류·건너뜀은 모두 0건이다. 합성 신청 100만 건에서 같은 부서의 깊은 페이지를 조회한 실행 계획은 OFFSET 307회, 키셋 13회의 논리 읽기를 기록했다. [Oracle 시험 결과](measurements/oracle-contracts-44781612255.txt) · [100만 건 실행 계획](measurements/oracle-inbox-44781386053.zip)

20만 원 약정 전표가 있는 부서에서 한도 25만 원의 후보 규정을 서로 다른 실비로 1,000번 미리 봤다. H2에서 매회 기존·후보 인정액이 기대값과 일치했고, 미리보기 전후 약정·집행·가용액과 전표 내역·규정 세대가 유지됐다. 로컬 Java 시험 41건 통과, Oracle 계약 17건은 CI에서 별도로 실행한다. [회계 계약 시험](../src/test/java/kr/budget/claims/PostingContract.java)

Oracle 복원 대사에 발행 규정, 접수 시점 수식·참석 조건, 증빙 유형·금액을 추가했다. 결재함 측정 데이터는 100개 부서 합계 100만 건으로 확장했으며 비교 대상은 한 부서의 동일 신청 51건이다. 측정값과 실행 계획은 해당 실행의 결과물을 확인한다. [대사 실행](https://github.com/kimdol/department-budget/actions/runs/44781206084) · [100만 건 측정 실행](https://github.com/kimdol/department-budget/actions/runs/44781386053)

재무 담당자가 후보 한도를 250,000원으로 편집하는 동안 다른 탭에서 규정을 먼저 발행하면 검토 요청은 HTTP 409로 거절된다. 이전 화면은 재요청해도 같은 세대를 전송했다. 이제 ‘최신 규정과 다시 비교’로 기존 규정을 갱신하고 편집 중인 수식과 실비를 보존한다. Chrome 두 탭에서 실비 300,000원에 기존 200,000원·후보 240,000원을 다시 확인한 후 새 세대의 검토 요청을 저장했다. 로컬 HTTPS 브라우저 시험 7건 통과, 선택 시험 2건 제외. 앞선 V017의 실제 Oracle 원장 계약과 별도 스키마 복원 대사도 통과했다. [브라우저 회귀 시험](../frontend/tests/hospital-policy.spec.ts) · [Oracle 검사](https://github.com/kimdol/department-budget/actions/runs/44780417194)

V017부터 참석 조건을 규정 초안·발행본과 신청서에 보존한다. 병원 교육비 신청 세 건을 기본 규정, 참석 확인서 필수 규정, 기본 규정 복구 직후에 각각 접수했다. 복구 후 같은 금액의 영수증으로 정산하자 첫째·셋째만 통과해 집행액이 360,000원이었다. 둘째는 증빙 보완 후 정산돼 최종 가용액이 460,000원이었다. 로컬 Java 40건과 HTTPS 브라우저 6건이 통과했다. 브라우저에서 부서장이 검토하는 조건과 복구 후보에 표시되는 조건을 확인했다. 로컬 실행에서 Oracle 시험 16건, 선택 브라우저 시험 2건은 제외했다. [정산 계약 시험](../src/test/java/kr/budget/claims/PostingContract.java) · [스키마 변경](../src/main/resources/db/V017__attendance_policy.sql)

병원 재무 담당자는 규정 작업실에서 조직·비목·참석 확인서 조건을 ALL·ANY로 중첩해 모의 판정할 수 있다. 등록된 잎과 중첩 그룹만 받으며 다른 노드 참조가 없어 순환 트리를 표현할 수 없다. 실비 정산의 기본 참석 요건도 같은 파서와 판정기를 쓴다. 브라우저에서 자료 부족·충족·미충족·분기 생략을 확인했다. 로컬 Java 39건과 HTTPS 브라우저 6건 통과. 후보 참석 조건을 발행본에 보존하고 이후 신청에 적용하는 연결은 남아 있다. [조건 파서](../src/main/java/kr/budget/claims/AttendanceRule.java) · [화면 시험](../frontend/tests/hospital-policy.spec.ts)

시연 역할은 방문자 세션에서 정하지만 Spring Boot가 별도 기본 계정까지 생성해 로컬 검증 로그에 비밀번호 생성 메시지가 3회 기록됐다. 기본 메모리 계정 저장소가 없어야 한다는 시험이 실패해 해당 자동 구성만 제외했다. 변경 후 생성 메시지는 0회였고 로컬 Java 시험 38건이 통과했다. 배포용 WAR 기동 시험도 동일 로그 표식을 검사한다. [회귀 시험](../src/test/java/kr/budget/web/ProxyTransportTest.java) · [WAR 기동 검사](../ops/ci_oracle_release.sh)

별도 DB 연결이 본부 예산 행을 잠근 상태에서 지점 승인 트랜잭션을 완료했다. 실제 Oracle에서 지점 약정 200,000원, 본부 약정 0원을 확인했다. H2와 Oracle의 원장 계약 각 15건이 통과했다. Data Pump로 같은 PDB의 별도 스키마에 복원해 본부·지점 식별자와 조직 유형 BANK를 포함한 대사 파일의 SHA-256이 일치함을 확인했다. [CI 실행](https://github.com/kimdol/department-budget/actions/runs/44777958172) · [원본·복원 대사 파일](measurements/oracle-departments-44777958172.zip)

한 방문자에게 같은 조직 유형의 두 시연 부서를 함께 생성한다. 각 부서는 별도의 예산·신청·규정·역할을 가지며 V016의 외래 키로 소속 관계를 보관한다. 부서 선택은 탭의 URL로 유지하고 서버가 세션에 배정된 두 식별자 중 하나를 고른다. 기업 인사팀의 영수증을 총무팀 부서장으로 열면 404였고, 총무팀에서 별도 200,000원을 약정한 뒤에도 인사팀 정산 가용액은 820,000원이었다. 로컬 Java 36건, 루프백 HTTPS 브라우저 6건 통과. 실제 사내 사용자 인증은 별도이며 공개 데모는 합성 역할 전환을 제공한다. [두 부서·두 방문자 시험](../frontend/tests/claims.spec.ts)

영수증 225,000원만 첨부하고 실비를 제출하지 않으면 ‘실제 지출 대조’가 충족으로 표시됐다. 두 금액이 있어야 비교할 수 있어 자료 부족을 기대한 회귀 시험에서 PASS가 반환되는 것을 재현했다. 영수증과 실비 중 하나라도 없으면 MISSING, 둘이 다르면 FAIL, 같으면 PASS로 판정하도록 수정했다. 조건 시험 3건과 루프백 HTTPS 브라우저 시험 6건 통과. 실비 제출 뒤 180,000원 정산까지 확인했다. [조건 회귀 시험](../src/test/java/kr/budget/policy/EligibilityRuleTest.java) · [대표 업무 브라우저 시험](../frontend/tests/claims.spec.ts)

ba94fe6에서 임시 PHP 수신 경로를 중단한 채 직원 신청을 부서장이 승인했다. Chrome 재무 화면에서 원신청과 HTTP 404를 확인하고 수신기 복원 후 같은 이벤트를 다시 보냈다. MySQL의 1차 수신액과 Oracle 약정은 모두 200,000원이었고 송신 실패 목록은 비었다. 별도 응답 유실 계약 시험에서는 수신 완료 뒤 재전송돼도 정산 2차가 한 번만 반영되고 다른 공간은 재처리 대상을 조회할 수 없음을 확인했다. [실제 Oracle·MySQL 장애 복구 실행](https://github.com/kimdol/department-budget/actions/runs/44775611695/job/136424489990) · [구현과 재현](ARCHITECTURE.md#포털-장애-중-승인-처리)

6da84c9에서 Oracle을 포함한 Java 시험 49건이 누락·실패 없이 통과했다. CI Chrome에서 Caddy를 거쳐 재무팀이 합성 신청 51cb5571-acfd-48b5-805d-2ef6c2f74359를 정산한 뒤, 서버가 PHP 포털에서 조회한 수신 차수 2와 180,000원을 확인했다. Oracle 전표·송신함 SQL과 MySQL 수신 요약 SQL이 같은 공간·신청·차수·종류·금액을 반환했다. 공개 호스트 접속은 OCI 배포 후 검증한다. [직접 대사한 실행](https://github.com/kimdol/department-budget/actions/runs/44774362675/job/136420076607)

재무 화면의 마감 완료 결산표에서 기간·비목·건수·집행액 형식의 합성 CSV를 확정 스냅샷과 비교한다. Chrome에서 180,000원 일치와 누락 금액을 확인했다. 단위 시험 2건에서 음수 정정액, 양쪽 비목 누락, 중복 비목과 다른 기간을 검증했다. H2 루프백 HTTPS 브라우저 시험 6건 통과. 실습 범위는 월 집계 대조이며 상용 원본 검수와 포털 CSV 메시지 변환은 별도 항목이다. [대조 구현](../src/main/java/kr/budget/closing/StatementComparison.java) · [경계 사례](../src/test/java/kr/budget/closing/StatementComparisonTest.java)

95a6d5f에서 같은 Caddy·Oracle·Chrome 시험이 통과했다. 체험 종료 후 같은 HTTPS 원점에서 새 예산을 열었고, 다른 방문자가 제출한 신청은 그대로 남았다. 테스트용 CA를 설치한 CI 루프백에서 검증했으며 공개 도메인과 OCI VM 기동은 남아 있다. [수정 후 실행](https://github.com/kimdol/department-budget/actions/runs/44772923282)

비표준 HTTPS 포트의 Caddy를 거친 Chrome 시험에서 TLS·쿠키·캐시 검사는 성공했지만 체험 종료 후 시작 버튼에 도달하지 못했다. 로그아웃 성공 응답이 프록시 뒤에서 외부 주소를 다시 구성하지 않도록 302 Location을 같은 원점의 `/`로 고정했다. Tomcat HTTP 회귀 시험에서 Location 값과 종료된 세션의 401을 확인했다. [재현 실행](https://github.com/kimdol/department-budget/actions/runs/44771940670) · [응답 회귀 시험](../src/test/java/kr/budget/web/PortalHttpTest.java)

기존 수식 평가기는 피연산자와 괄호 중첩을 세어 왼쪽 결합 연산이 긴 식의 트리 깊이를 제한하지 못했다. 같은 입력에 1을 32번 곱하면 이전 평가기는 225,000원을 계산한다. 신규 초안의 decimal-v2는 연산·함수·값의 노드와 높이를 세어 깊이 32, 노드 256 상한을 적용한다. 저장된 decimal-v1 신청은 이전 계약으로 재현한다. 복구 요청은 업무 공간·비목의 과거 세대와 수식을 대조하고 당시 평가기를 다시 발행한다. 로컬 Java 시험 33건과 루프백 Chrome 시험 6건 통과. [수식 경계값](../src/test/java/kr/budget/policy/ExpenseFormulaTest.java) · [결정 재현](../src/test/java/kr/budget/claims/PostingContract.java) · [복구 화면](../frontend/tests/hospital-policy.spec.ts)

다른 방문자가 증빙 목록을 열면 전용 SQL이 문서를 반환하지 않아 HTTP 200과 빈 목록이 내려왔다. 같은 신청의 증빙 요건과 파일은 404였다. 목록 조회도 세션 업무 공간 안에 신청이 있는지 확인하도록 변경했다. 직원·부서장·재무 역할 모두 다른 공간의 목록·요건·파일에서 404를 확인했다. 같은 공간의 재무 담당자는 Chrome으로 225,000원 영수증을 받았고 첨부 변경 요청은 403이었다. H2 HTTP 시험 2건과 루프백 HTTPS 브라우저 시험 6건 통과. [HTTP 회귀 시험](../src/test/java/kr/budget/web/PortalHttpTest.java) · [다운로드 검증](../frontend/tests/claims.spec.ts)

실제 Oracle에서 10,000건 중 9,001번째부터 51건을 읽었다. OFFSET과 커서 조회는 모든 회차에서 같은 신청번호를 반환했고 문장 전체 Buffers는 각각 200과 12였다. [CI 실행](https://github.com/kimdol/department-budget/actions/runs/44770051843) · [원자료](measurements/oracle-inbox-44770051843.zip)

공개 프록시용 Tomcat 프로필에서 루프백 HTTP와 HTTPS 전달 헤더를 비교했다. 실제 응답에서 HSTS, no-store, JSESSIONID의 Secure·HttpOnly·SameSite=Lax를 확인했다. [단일 VM 배포 절차와 남은 외부 검증](HTTPS.md)

규정 작업실에서 40ms 간격으로 여섯 글자를 입력했을 때 마지막 입력부터 결과가 DOM에 반영되기까지 중앙값은 264.7ms였다. 대기 타이머를 250ms에서 150ms로 줄인 뒤 같은 조건에서 157.0ms를 기록했다. 전후 각각 예열 1회와 측정 5회, 모든 회차에서 미리보기 요청은 1건이었다. [재현 조건과 원자료](measurements/README.md#수식-입력-후-결과-표시)

커밋 98cf644의 실제 Oracle CI에서 V015를 포함한 빈 스키마 설치, 계약 시험, Data Pump 복원·대사와 배포용 WAR의 Chrome 시험이 모두 통과했다. 병원 규정 검토 요청 당시의 비교 사례 보존을 배포본에서도 확인했다. [실행 단계](https://github.com/kimdol/department-budget/actions/runs/44768400881/job/136399149853) · [학습용 스키마에서 비교 사례 조회](ORACLE-WALKTHROUGH.md#규정-변경-후-재현)

재무 담당자가 규정 검토를 요청하면 서버가 해당 세대를 잠금 안에서 확인하고 실비·일수·두 지원액을 초안과 함께 저장한다. 부서장이 자기 화면의 비교 실비를 10,000원으로 바꿔도 검토 요청 당시 180,000원 사례의 144,000원 → 162,000원이 유지되는 것을 루프백 HTTPS Chrome에서 확인했다. V015 이전 초안은 당시 입력이 없어 비교 사례를 표시하지 않는다. [스키마 변경](../src/main/resources/db/V015__draft_comparison.sql) · [검토 화면 시험](../frontend/tests/hospital-policy.spec.ts)

규정 미리보기에서 기존·후보 인정액에 대해 신청 제출과 같은 [승인 경로 판정](../src/main/java/kr/budget/claims/ApprovalRoute.java)을 호출한다. 병원 학회비 전액 지원 후보를 입력하고 지원 예정액 299,999원에서는 부서장, 300,000원에서는 부서장 → 재무로 바뀜을 Chrome에서 확인했다. 루프백 HTTPS 시험 6건 통과. [경계값 시험](../frontend/tests/hospital-policy.spec.ts) · [재무 최종 사전승인 시 약정 시험](../frontend/tests/travel-approval.spec.ts)

병원 규정 작업실에서 학회비 180,000원에 대한 지원율을 80%에서 90%로 바꾸면 지원 예정액과 함께 직원 부담액 36,000원 → 18,000원을 비교할 수 있다. Chrome에서 잘못된 단위의 위치 표시, 검토 요청 비활성화, 수정 후 미리보기·검토·발행·복구까지 확인했다. 루프백 HTTPS 시험 6건 통과. [브라우저 시험](../frontend/tests/hospital-policy.spec.ts)

수식 편집에서 원화와 일수를 더하거나 `min`으로 비교하면 단위 오류만 반환돼 담당자가 수정할 위치를 알 수 없었다. `eligible_cost + days`와 `min(eligible_cost, days)`에서 두 번째 항의 시작 위치인 17·20번째 문자를 기대한 회귀 시험 2건이 이 사유로 실패했다. 파서가 두 번째 항을 읽기 전에 공백을 건너뛴 위치를 보관하고 단위 검사에 전달하도록 변경한 뒤 수식 시험 13건이 모두 통과했다. [수식 파서](../src/main/java/kr/budget/policy/ExpenseFormula.java) · [회귀 시험](../src/test/java/kr/budget/policy/ExpenseFormulaTest.java)

배포 후 외부 네트워크의 Chrome에서 다음 명령을 실행한다. `frontend` 디렉터리에서 `npm ci`를 실행한 뒤 환경 변수 `TEST_DEPLOYMENT_URL`에 실제 데모의 HTTPS 원점 주소를 설정하고 `RECORD_TLS=1`을 설정한다. `npx --no-install playwright test --reporter=list`는 방문자별 업무 처리와 초기화, TLS·세션·캐시를 확인한다. 응답 측정값은 `docs/measurements/deployment-https.json`에 기록한다. 배포 주소를 지정하면 시험용 서버를 기동하지 않는다. 루프백 HTTPS에서 Chrome 시험 6건을 통과했고, 공개 시험 설정이 HTTP 주소를 거부하는 것을 확인했다. 공개 호스트 검증은 대기 중이다.

배포용 WAR에서 생성한 설치 SQL을 빈 Oracle 스키마에 적용하고 같은 SQL의 재실행이 ORA-20001로 차단됨을 확인했다. 이 스키마에 WAR를 연결해 Chrome에서 두 방문자의 교육비 처리와 월마감, 병원 학회비 규정 발행·복구, 출장 사전승인, 결재함 페이지 이동 시험 4건을 통과했다. [실행 결과](https://github.com/kimdol/department-budget/actions/runs/44765732583) CI 접속 범위는 루프백 HTTP이며 외부 HTTPS 검증은 OCI 배포 후 진행한다.

75f463d에서 Oracle CI 전체 성공. 실제 Oracle 계약 14건 통과, 누락 0건. Data Pump 복원 후 두 대사 파일의 SHA-256은 A7330105D1E199A95CD1563812C9D1683582426590BCA36650E59167F077E175로 일치했다. [실행](https://github.com/kimdol/department-budget/actions/runs/44764720608) · [보관한 원자료](measurements/oracle-44764720608). 공개 앱 호스팅은 OCI 무료 계정 가입 진행 중이다.

공개 커밋 d0316d9에서 Oracle 원장 계약 14건과 전체 Java 시험 43건이 누락·실패 없이 통과했다. 이어지는 Data Pump 단계는 표준 입력 비밀번호를 받지 못해 ORA-01005로 종료됐다. 컨테이너 내부 임시 파라미터 파일을 소유자만 읽게 생성하고 명령 종료 시 제거하도록 수정했다. [실제 Oracle 실행](https://github.com/kimdol/department-budget/actions/runs/44764118942)

최초 공개 Oracle CI는 V001에서 ORA-01408로 실패했다. expense_claim의 (workspace_id, claim_id) 유일 제약이 동일 열 인덱스를 이미 생성했다. H2에서는 통과했던 명시적 인덱스를 제거했다. 빈 Oracle 스키마에 최초 설치가 실패한 단계이므로 V001의 중복 선언을 직접 정정했다. [실패한 실행](https://github.com/kimdol/department-budget/actions/runs/44763501353)

로컬의 H2 업무 원장 → Java HTTPS 송신 → PHP 수신 → 실제 MySQL 8.4.13 → 재무 담당자의 Chrome 조회를 한 번에 시험했다. 대표 신청에서 포털 2차 정산 180,000원과 결재함 집행액이 일치했다. 합성 예산 업무·브라우저 격리·포털을 함께 확인하는 명령은 `python ops/test_portal.py --browser`다. 공개 인터넷 배포 검증은 별도다.

포털 송신 설정을 활성화하면 재무 결재함에서 MySQL에 도착한 신청별 수신 차수·종류·금액을 확인할 수 있다. 조회용 비밀 값은 Java 서버에서 PHP로 전송한다. PHP·MySQL 직접 시험에서 정산 2차 180,000원, 타 공간 404, 비인증 403과 no-store를 확인했다. 외부 Java 서버와 실제 포털의 HTTPS 연결은 미검증이다.

## 현재 상태

| 구분 | 업무 | 확인된 범위 · 남은 검증 |
| --- | --- | --- |
| 필수 | 신청·약정·정산·정정 | 실제 Oracle에서 원장 계약 23건 통과. 은행 본부 예산의 동시 배정·재시도, 커밋 직전 승인 실패, 정산 재전송, 다음 달 환입, PL/SQL 집행액 차이 검출 포함. [시험 결과](measurements/oracle-contracts-44790716638.txt) · [실행 기록](https://github.com/kimdol/department-budget/actions/runs/44790716638) |
| 필수 | 규정 발행·복구·계산 재현 | 수식 시험 14건·조건 트리 2건 통과. 이전·새 평가기의 과거 결정 재현과 복구를 실제 Oracle과 Chrome에서 확인 |
| 필수 | 세션·증빙·웹 응답 | 세션 ID 교체, 두 방문자와 두 부서의 업무·증빙 격리, no-store, 해시 자산의 재사용을 로컬 HTTPS에서 확인. 외부 공개 도메인의 역할 시연 대기 |
| 필수 | 월마감·포털 | 실패한 배치 재시작과 원본 기간 보존을 H2·Oracle 원장 계약에서 확인. 실제 PHP·MySQL 수신 구간 검증. 외부 송신 경로 대기 |
| 심화 | 성능·복원 | 재방문 전송량, 수식 미리보기 지연, Oracle 깊은 페이지의 논리 읽기 측정 완료. 같은 PDB의 별도 스키마 복원 대사 완료. 승인 잠금 대기 시간 미측정 |
| 선택 | 상용 UI·리포팅 전환 | 제품 환경 미확보 |

| 설계서의 세부 인수 항목 | 구현한 범위 | 남은 확인·구현 |
| --- | --- | --- |
| 직원의 사전 결정과 결재 | JSP 신청함의 지원액·본인 부담액·역할 경로, 접수 당시 규정 및 증빙 조건 고정, 보완 중 비목 변경 시 약정 해제와 새 신청 연결 | 동일 비목의 범위·금액 변경 후 버전별 재상신, 개인 지출 장부 |
| 조직별 승인 주체 | 세션별 직원·부서장·재무 역할과 부서별 서버 인가, 은행 본부 재무의 지점 예산 배정 | 독립된 구성원 신원에 기반한 자기 승인 차단 |
| 규정 적용·영향 비교 | 수식·조건 편집, 검토 해시, 버전 발행·복구, 저장한 신청별 예정액·경로 비교, 버전별 불변 구문 트리 캐시, 다음 달 예약 발행 | 예약 발행의 외부 배포 검증 |
| 회계·포털 연계 | Spring Batch 월마감과 재시작, MyBatis·Oracle PL/SQL 대사, 실제 PHP·MySQL 송수신, 구 결산 CSV 대조 | Apache Camel 기반 JSON·CSV 공통 변환, 그누보드 연동 |
| UI·운영 검수 | JSP·React 직원 신청 화면의 동일 API·권한·키보드 검증, React 규정 작업실, 키보드 승인·보완과 오류 포커스, Linux CI의 WAR 복귀와 같은 PDB 내 별도 스키마 복원 | 최초 사용자 관찰, 공개 호스트 재기동 후 검증 |
| 성능 | 100만 신청의 깊은 목록 조회·논리 읽기·DB p95, 결재함 본문·정적 자산·수식 입력 측정 | 같은 데이터 규모의 월별 집계와 동시 승인 쓰기 지연, 공개 환경에서의 브라우저 측정 |

[원장 계약](../src/test/java/kr/budget/claims/PostingContract.java) · [브라우저 시험](../frontend/tests) · [정적 전송량](measurements/README.md) · [HTTPS](measurements/loopback-https.json) · [MySQL 수신](measurements/portal-receipts.json)

## 재개할 때

1. git status, git log -1과 git ls-remote origin refs/heads/main을 대조한다.
2. 다음 기능 변경에서도 Verify Oracle Ledger의 계약 시험과 복원 산출물 일치를 확인한다. 수식 평가기 교체를 포함한 [Oracle 기준 실행](https://github.com/kimdol/department-budget/actions/runs/44771763566)은 80ac4c6에 해당한다.
3. OCI 접근 권한이 확보되면 무료 자원 할당 가능 여부와 Oracle 연결을 확인하고 공개 URL에서 새 브라우저 두 개로 대표 업무와 격리를 재현한다. 그동안에는 위 세부 인수 항목에서 남은 구현과 검증을 이어간다.
4. 로컬 재현은 frontend에서 npm ci와 npm run build, 루트에서 mvn package, python ops/test_https.py 순서다. 포털 수신 시험은 python ops/test_portal.py로 실행한다.

## 외부 환경

공개 Oracle·Java 호스팅 계정은 아직 없다. 배포 후보는 OCI Always Free 컴퓨트와 Autonomous Database다. 무료 자원 할당이 가능한 리전·규격·사용량에 한해 예상 인프라 비용은 월 0원이다. 할당 용량이 부족할 수 있고 도메인은 별도다. DB는 7일 비활동 시 데이터가 보존된 상태로 정지되며 장기간 비활동 시 회수될 수 있다. 콘솔에서 재시작하고 별도 백업을 복원 검증한다. 유료 전환에는 사전 승인이 필요하다. [Oracle 무료 자원](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) · [DB 중지와 재시작](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/autonomous-always-free.html)

## 단계별 검증 기록

결재함은 업무 공간의 접수 순번을 기준으로 50건씩 조회한다. 51건을 접수한 Chrome 시험에서 두 번째 페이지의 최초 신청을 승인한 뒤 같은 위치에서 약정 완료를 확인했고, 이전 페이지에서 최신 신청을 다시 열었다. H2 시험은 페이지를 읽는 사이에 다른 신청이 접수돼도 다음 페이지로 넘어가는 경계가 유지됨을 검증했다. V014 이전 행에는 실제 접수 순서를 복원할 시각 정보가 없어 이관 때 고정 순번을 부여한다. Java 시험 29건, 로컬 HTTPS Chrome 시험 6건 통과. 실제 Oracle 계약 14건은 미실행이다. [브라우저 시험](../frontend/tests/inbox-pages.spec.ts)

사전승인 경로를 신청에 저장한다. 설계서에 금액 기준이 없어 합성 데모에서는 **지원 예정액 300,000원 이상**을 부서장 검토 후 재무 최종승인 대상으로 정했다. 직원이 요청한 금액이 300,000원이더라도 규정 적용 후 200,000원인 대표 교육비는 부서장 승인으로 약정한다. V013 이관 전 신청은 기존 부서장 경로를 유지한다. H2에서 299,999원과 300,000원 경계, 재무 선승인 거절, 중복 결재 후 전표 1건을 검증했다. [출장비 시연](../frontend/tests/travel-approval.spec.ts). 로컬 Java 28건 통과, Oracle 13건 미실행.

규정 초안·발행본·신청에 평가기 식별자를 고정했다. V012는 기존 행에 기존 십진 계산기 decimal-v1을 배정한다. ‘신청 당시 계산 근거’에서 예정액과 실비를 각각 재현한다. H2 시험에서 우대 규정 복구 후에도 과거 학회 신청의 최초 인정 162,000원과 실비 인정 90,000원이 유지됐으며 미등록 평가기는 재현을 거절했다.

GitHub 원격 main을 다시 조회한 결과 5e5c8e0이다. workflow 쓰기 권한 승인을 기다리고 있다. Oracle CI에 실제 시험 누락 차단, Data Pump 내보내기와 별도 스키마 복원, 정렬된 대사 결과 전체 비교를 추가했다. Bash 문법 검사와 Oracle 시험을 건너뛴 로컬 결과에 대한 차단 동작을 확인했다. 실제 CI 복원 성공 증거는 아직 없다.

증빙 요건을 [EvidencePolicy](../src/main/java/kr/budget/claims/EvidencePolicy.java)의 불변 조건 트리로 분리했다. 병원 학회비에만 참석 확인서를 요구하며, 다른 조직에서는 조직 조건에서 결과가 결정된다. 결재함의 ‘판정 근거’에서 실제 평가한 조건과 생략한 조건을 확인한다. [세 값의 조합 시험](../src/test/java/kr/budget/policy/EligibilityRuleTest.java)은 충족·미충족·자료 부족의 9가지 입력 쌍을 모두/하나 조건 각각에 적용한다. Java 27건, 로컬 HTTPS Chrome 4건 통과. 실제 Oracle 시험 12건 미실행.

루프백 Tomcat HTTPS에서 Chrome 시험 4건 통과. Node TLS 클라이언트는 시험 인증서를 신뢰 목록에 추가하고 호스트를 검증했다. Chrome은 해당 실행에서 생성한 공개 키 지문만 예외로 허용했다. TLS 1.3, HSTS, 세션 쿠키 Secure·HttpOnly·SameSite=Lax, 업무 API no-store 및 버전 고정 jQuery 재방문 전송량 0바이트를 확인했다. [응답 원자료](measurements/loopback-https.json) · [HTTPS 시험](../frontend/tests/https.spec.ts). 외부 호스트의 공개 인증서와 프록시 응답은 미검증이다.

PHP 8.4.28과 MySQL Community Server 8.4.13에서 실제 수신기를 실행했다. 정산 선도착 409, 승인·동일 승인·정산·동일 정산 모두 204, 같은 식별자의 금액 변경 409였다. MySQL에 수신 이력 2행과 최종 집행액 180,000원 1행이 남았다. Windows에서 서버 감시 프로세스가 임시 파일을 유지해 최초 실행의 정리가 실패했다. 시험 서버의 감시 기능을 끄고 SQL SHUTDOWN 후 종료를 기다리도록 변경해 연속 두 번 정상 종료했다. PHP HTTP 수신 구간은 루프백에서 시험했다. Java 송신기와 외부 HTTPS·Oracle의 통합 운용 검증은 남아 있다. [원자료](measurements/portal-receipts.json) · [재현 시험](../ops/test_portal.py)

정산 확정은 실비와 영수증 금액의 일치를 검사하며 병원 학회비에는 참석 확인서를 추가로 요구한다. 기존에는 증빙 없이도 집행할 수 있었고, 이미 첨부한 증빙의 금액을 고칠 수 없었다. 병원 학회 실비 225,000원에 220,000원 영수증만 제출한 H2 시험에서 두 가지 보완 사유와 집행액 0원을 확인했다. 보완 후 집행액은 180,000원, 같은 증빙 재전송 후 교체 이력은 1건이다. [계약 시험](../src/test/java/kr/budget/claims/PostingContract.java) · [증빙 판정](../src/main/java/kr/budget/claims/EvidenceService.java)

체험 시작 시 은행 경영지원팀·병원 행정부서·일반 기업 경영지원팀을 선택한다. 조직 유형은 업무 공간 생성 때 저장하며, 기존 공간은 V010에서 일반 기업으로 분류한다. 규정 발행 범위는 해당 방문자의 조직 공간과 비목이다. 병원 학회비 90% 발행 후 180,000원 신청에서 병원 학회비만 162,000원, 같은 병원의 교육비와 별도 은행·일반 기업 공간의 학회비는 모두 144,000원이었다. Chrome에서 비교·검토·발행·복구까지 확인했다. Java 24건과 Chrome 3건 통과, 실제 Oracle 11건 미실행이다.

실제 WAR에서 Oracle 설치 SQL을 생성해 마이그레이션 V001~V009, Spring Batch Core 5.2.5 스키마 및 시퀀스 3개의 포함을 확인했다. [설치·백업·복원 절차](ORACLE.md)와 원전표·마감 자료 대사 SQL을 추가했다. Oracle에서의 실행 결과와 두 PDB의 대사 원자료는 아직 없다.

직원 화면에서 교육비 300,000원의 지원 예정액 200,000원, 본인 부담 100,000원 및 적용 수식을 확인한 뒤 신청하는 Chrome 시험이 통과했다. H2 계약 시험에서는 미리보기와 제출 사이에 50% 규정을 발행한 경우 새 신청이 409로 거절되고, 이미 접수된 요청의 재전송은 최초 신청번호를 반환했다. 새 미리보기는 150,000원, 기존 신청의 실제 약정은 200,000원이었다. 로컬 Java 시험 24건과 Chrome 시험 2건 통과, Oracle 계약 11건 미실행.

V009부터 전표에 귀속 월을 기록한다. 재무 담당자가 다음 달을 열고 이전 정산의 전표 조회에서 30,000원을 환입하는 Chrome 시험이 통과했다. 이전 확정 가용액은 820,000원, 환입 후 가용액은 850,000원이었다. H2 계약 시험에서는 원전표를 참조하는 동일 차수의 재요청, 같은 차수의 금액 변경 거절, 잔여 환입액 초과 거절, 다음 달 결산표의 순집행 −30,000원까지 확인했다. 새 기간에는 같은 부서 예산의 누적 잔액을 이월한다. Java 23건과 Chrome 2건 통과, Oracle 10건 미실행. 다음 작업은 증빙 요건에 따른 결재 판정과 사전 확인이다.

마감 확정 시 비목별 집계와 배정·약정·집행 합계를 같은 트랜잭션에 보관한다. 재무 역할로 `GET /api/statements/{period}`를 호출하면 그 시점의 잔액을 조회한다. 이전 구현은 확정한 비목별 내역과 현재 부서 잔액을 조합했다. H2 계약 시험에서 마감 후 현재 배정액을 1,000원 늘려도 원래 보고서 전체가 동일하고 다른 업무 공간에서는 조회할 수 없음을 확인했다. 기존 단일 기간의 마감 자료는 V008 마이그레이션에서 이전한다. 실제 Oracle 적용은 미검증이다.

최근 변경은 직원의 철회·약정 취소다. 승인 전 철회는 전표 0건, 승인 후 취소는 최초 승인과 해제 전표를 합해 2건이었다. 같은 요청을 두 번 보낸 뒤 각각 가용액 100만 원과 처리 이력 1건을 확인했다. 먼저 승인이 끝난 신청에 이전 화면의 철회 요청을 보내면 충돌로 거절한다. 로컬 Java 시험은 22건 통과, Oracle 9건 미실행이다. 다음 원장 작업은 열린 기간에 원전표를 참조하는 마감 후 정정이다.
