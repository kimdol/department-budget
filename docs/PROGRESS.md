# 인수 현황

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
| 필수 | 신청·약정·정산·정정 | H2와 실제 Oracle에서 같은 원장 계약 각 14건 통과 |
| 필수 | 규정 발행·복구·계산 재현 | Java 수식 11건·조건 트리 2건 통과. Chrome에서 병원 학회비 발행 및 복구 확인 |
| 필수 | 세션·증빙·웹 응답 | Tomcat HTTP 시험 2건, 로컬 HTTPS Chrome 6건 통과. 외부 공개 도메인의 역할 시연 대기 |
| 필수 | 월마감·포털 | 실패한 배치 재시작과 원본 기간 보존을 H2에서 확인. 실제 PHP·MySQL 수신 구간 검증. 외부 송신 경로 대기 |
| 심화 | 성능·복원 | 재방문 전송량 측정과 실제 Oracle 별도 스키마 복원 대사 완료. Oracle 실행 계획·잠금 대기 측정 대기 |
| 선택 | 상용 UI·리포팅 전환 | 제품 환경 미확보 |

[원장 계약](../src/test/java/kr/budget/claims/PostingContract.java) · [브라우저 시험](../frontend/tests) · [정적 전송량](measurements/README.md) · [HTTPS](measurements/loopback-https.json) · [MySQL 수신](measurements/portal-receipts.json)

## 재개할 때

1. git status, git log -1과 git ls-remote origin refs/heads/main을 대조한다.
2. 다음 기능 변경에서도 Verify Oracle Ledger의 계약 시험과 복원 산출물 일치를 확인한다. 실제 Oracle 근거가 있는 기준 커밋은 75f463d다.
3. 독립 호스팅과 Oracle 연결 권한이 확보되면 공개 URL에서 새 브라우저 두 개로 대표 업무와 격리를 재현한다. 공개 전 대기 중인 설계 범위에는 포털 조회 화면·레거시 CSV, 별도 부서 구성원 인가, 운영자 규정 조건 편집이 포함된다.
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
