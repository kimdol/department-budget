# 결재함 재방문 전송량

## 신청 50건의 목록 응답

동일 PC의 루프백 HTTPS·H2에서 실비 10,000원인 교육 신청 51건을 제출했습니다. 부서장으로 첫 페이지를 열고 예열된 상태에서 동시 사용자 1명이 `GET /api/workspace`를 5회 호출했습니다. 응답 전체의 디코딩된 JSON은 매회 22,454바이트에서 12,904바이트로 줄었습니다(42.5%). HTTP 헤더와 TLS·압축을 포함한 네트워크 전송량은 측정하지 않았습니다.

이전 목록은 신청마다 수식, 평가기 버전, 규정 세대, 일수, 참석 조건을 반복했습니다. 목록 전용 SQL은 이 다섯 개 열을 읽지 않습니다. 상세 계산 근거 조회는 유지했으며 이전 페이지 승인과 최신 목록 복귀를 같은 시험에서 검증했습니다. 기준 커밋 `11be150`에서 `MEASURE_INBOX_PAYLOAD=before`, 변경 후에는 `MEASURE_INBOX_PAYLOAD=after`를 설정해 `python ops/test_https.py`를 실행합니다. [측정 코드](../../frontend/tests/inbox-pages.spec.ts) · [변경 전](inbox-payload-before.json) · [변경 후](inbox-payload-after.json)

## 정적 자산 재사용

자체 CSS·JS를 SHA-256 주소로 제공한 뒤 같은 세션에서 결재함을 다시 열었습니다. 루프백 HTTPS에서 매번 새 Chrome 컨텍스트로 5회씩 비교했습니다. CSS·JS·jQuery의 전송량 합계는 첫 방문 109,867B → 109,968B, 재방문 22,034B → 0B였습니다. HTML과 업무 데이터는 이 합계에서 제외합니다. 규정 편집기 번들은 패널을 열 때 로드합니다. 서버는 패키지 내 세 파일의 내용을 메모리에 유지합니다. 해시와 바이트가 맞지 않는 주소는 404로 응답합니다. 기준선은 eeffcdf에 측정기만 추가한 136236e이고 사후 파일의 revision은 수정 중인 작업 트리의 부모를 나타냅니다. 재현하려면 각 버전에서 `MEASURE_DESK`를 `assets-before` 또는 `assets-after`로 설정한 뒤 `python ops/test_https.py`를 실행합니다. [기준 표본](assets-before.json) · [개선 후 표본](assets-after.json) · [HTTPS 검증](../../frontend/tests/https.spec.ts)

로컬 Chrome에서 새 방문자가 결재함을 열고 빈 탭으로 이동한 뒤 같은 주소를 다시 열었습니다. 첫 방문에 필요한 세 정적 자산 중 jQuery 하나가 87,833바이트를 차지했습니다. 버전이 URL에 들어 있는 해당 라이브러리에 1년 공개 캐시를 적용했습니다. JSP, 업무 API와 같은 이름으로 갱신하는 자체 스크립트에는 no-store를 유지했습니다.

| Chrome Resource Timing 합계 | 첫 방문 | 같은 세션 재방문 |
| --- | ---: | ---: |
| 변경 전 5회 중앙값 | 97,383 B | 97,383 B |
| 변경 후 5회 중앙값 | 97,383 B | 9,550 B |

재방문 시 세 자산의 전송량이 87,833바이트(90.2%) 줄었습니다. 첫 방문 전송량은 같았습니다. jQuery 파일은 만료 전까지 브라우저 저장 공간을 사용하며 교체 시 URL의 버전도 변경해야 합니다. 측정 범위는 정적 파일 세 개입니다. 전체 페이지의 표시 시간, WAN 지연 및 Oracle 응답 시간은 이 시험으로 측정하지 않았습니다.

## 실험 조건

Windows x64, Ryzen 5 3600, 논리 CPU 12개, 메모리 16GiB, Chrome 157.0.8103.79, 로컬 HTTP Tomcat·H2를 사용했습니다. 네트워크 제한 없이 한 번에 한 탭이 조회했습니다. 각 조건에서 독립적인 신규 브라우저 컨텍스트 5개를 만들었습니다. 각 컨텍스트에서 개인 데모를 시작하기 전 동일한 빈 결재함을 두 번 열었습니다. 최초 요청에 캐시가 비어 있고 두 번째 탐색은 캐시를 유지합니다. 각 파일의 transferSize는 브라우저가 보고한 응답 본문과 헤더 추정치의 합입니다.

[기준선 aebe4cf](before.json) · [변경 후 b5c3bbd](after.json) · [측정 코드](../../frontend/tests/desk-transfer.spec.ts) · [HTTP 캐시 회귀 시험](../../src/test/java/kr/budget/web/PortalHttpTest.java)

두 리비전의 앱에 같은 측정 스크립트를 사용합니다. 기준선의 앱과 시험 코드는 37d3247에서 함께 체크아웃할 수 있습니다. Java 21, Maven, Chrome 설치 후 frontend에서 npm ci와 npm run build를 실행합니다. PowerShell에서 아래처럼 실행하면 원자료를 다시 생성합니다.

```powershell
$env:MEASURE_DESK='before'
npx playwright test desk-transfer
```

변경 후에는 b5c3bbd에서 같은 명령의 MEASURE_DESK 값을 after로 지정합니다.

## 수식 입력 후 결과 표시

Chrome 한 세션에서 실비 180,000원의 기본 신청 공간을 열고 수식을 `eligible_cost`로 설정합니다. 결과가 갱신된 뒤 ` * 0.9` 여섯 글자를 40ms 간격으로 입력합니다. 마지막 input 이벤트부터 후보 지원액 162,000원이 DOM에 반영될 때까지 브라우저 시계로 측정했습니다. 실제 화면 페인트와 외부 네트워크 지연은 측정 범위 밖입니다.

로컬 H2·Tomcat HTTPS, 동일 장비·Chrome, 동시 사용자 1명, 예열 1회 후 같은 페이지에서 5회 측정했습니다. 변경 전 중앙값은 264.7ms(263.1~266.6), 변경 후는 157.0ms(156.9~158.2)였습니다. 두 조건 모두 매회 요청 1건입니다. 150~250ms 동안 입력을 멈추면 새 설정에서는 중간 수식 요청이 추가될 수 있습니다. 이 입력 속도 구간의 비용은 측정하지 않았습니다.

재현 시 변경 전은 d6ff907, 변경 후는 f499d69를 사용합니다. 각각 frontend에서 npm ci와 npm run build를 실행한 뒤 저장소 루트에서 환경 변수 MEASURE_FORMULA를 before 또는 after로 지정하고 `python ops/test_https.py`를 실행합니다. Git 이력 d6ff907은 기준 리비전 9c94447에 측정기와 기초 원자료만 추가한 커밋입니다. [측정 코드](../../frontend/tests/formula-latency.spec.ts) · [변경 전](formula-before.json) · [변경 후](formula-after.json)

## Oracle 결재함 100만 건 측정

합성 부서 100곳에 신청을 각각 10,000건씩 넣고 통계를 수집합니다. 50번째 부서에서 최신순 9,000건을 건너뛰는 조회와 같은 경계를 키셋으로 조회합니다. 두 SQL은 동일 매퍼 열을 반환하며, 결과의 신청번호 51개와 순서까지 검사합니다. 첫 실행을 포함해 순서를 바꿔 네 번씩 실행합니다. SQL별 실제 실행 계획과 논리 읽기는 CI 산출물 `oracle-inbox-plans/plans.txt`에 있습니다. 이 실험은 균등 분포이고 월 집계·동시 쓰기 지연은 별도 측정 대상입니다. [실행 명령과 데이터 생성 SQL](../../ops/measure_oracle_inbox.py)

실행 계획 채취 후 같은 세션에서 두 커서를 각각 한 번 예열하고 순서를 번갈아 40회씩 실행합니다. Oracle 서버 시계로 커서 열기부터 51개 행 전체 열의 일괄 인출과 커서 닫기까지 측정합니다. 행 검증과 출력은 시간 구간에서 제외합니다. 원자료 latency.json에는 장비 정보, 마이크로초 단위 표본 전체와 오름차순 38번째 값(p95)을 기록합니다. 네트워크·JDBC·화면 표시 시간은 포함하지 않습니다.
동일 조건에서 각 SQL을 40회 인출한 결과, 서버 조회 p95는 OFFSET 6.608ms, 키셋 0.137ms였습니다. 측정은 동시 사용자 1명과 예열된 동일 페이지에 한정했습니다. [전체 표본과 장비](oracle-latency-44781904882.json) · [실행 기록](https://github.com/kimdol/department-budget/actions/runs/44781904882)
## 같은 규정으로 신청 50,000건 계산

Windows의 Ryzen 5 3600(6코어, 논리 프로세서 12개), OpenJDK 21.0.10, 고정 힙 128 MiB에서 한 스레드로 교육비 기본 수식을 계산했습니다. 실비는 100,000원부터 149,999원까지 1원씩 증가하고 지원율은 80%, 한도는 200,000원입니다. 각 JVM에서 50,000건씩 5회 예열 후 10회를 기록했습니다. 기존·변경·변경·기존 순으로 별도 JVM을 실행해 버전당 20회 측정했습니다. 합계는 매회 4,999,960,000원이었습니다.

50,000건 소요 시간 중앙값은 기존 31.54ms, 변경 후 15.57ms였습니다. 동일 수식이 반복되는 예열 상태의 계산기 측정입니다. 내용 해시를 만들고 트리의 모든 연산을 방문하므로 원문 길이와 노드 수에 비례하는 처리 비용은 남습니다. 변경 후 최대 256개 트리를 메모리에 보관합니다. 서로 다른 규정이 계속 들어오는 부하와 웹 응답 시간은 이 시험에서 측정하지 않았습니다. [40회 원자료](formula-cache-abba.csv)

저장소 루트에서 JDK 21의 `JAVA_HOME`을 설정하고 Maven 빌드 후 아래 순서로 실행합니다. 각 행의 값은 측정 차수, 나노초, 정산 예정액 합계입니다.

```sh
python ops/measure_formula.py a59b125
python ops/measure_formula.py 18ad229
python ops/measure_formula.py 18ad229
python ops/measure_formula.py a59b125
```
## 월 결산의 부서·기간별 전표 집계

Oracle Free에서 정산 전표 100만 건을 부서 100개와 12개월로 나눴습니다. 부서 50의 11월 집계는 교육비·학회비 각 278건, 출장비 277건으로 총 833건이었습니다. 매번 비목별 건수와 집행액을 검사했습니다. 매퍼의 실제 결산 SQL에 같은 부서와 기간을 바인딩한 뒤, 동일 세션에서 후보 인덱스 `(workspace_id, period_key, claim_id)`의 사용 여부를 교대로 바꿨습니다. 두 실행 순서에서 SQL 전체의 논리 읽기는 기존 11,459회, 후보 765회였고 후보 계획은 인덱스 범위 스캔으로 해당 기간의 833건을 읽었습니다.

Intel Xeon 6975P-C, 논리 CPU 4개, 메모리 약 15.3 GiB의 Linux CI에서 조건별 1회 예열 후 20회의 서버 측 커서 실행·결과 순회 시간을 기록했습니다. p95는 기존 40.856ms에서 후보 4.635ms로 줄었습니다. 전표 입력 시 인덱스 유지 비용은 이 시험 범위 밖입니다. 후보는 CI의 비가시 인덱스로 생성했습니다. 재현 순서는 같은 CI Oracle에 `python3 ops/measure_oracle_inbox.py`를 실행한 다음 `python3 ops/measure_oracle_month.py`를 실행하는 것입니다. [실행 계획·원자료·장비 정보](oracle-month-44794959491.zip) · [측정 커밋](https://github.com/kimdol/department-budget/commit/0f6166d) · [CI 실행](https://github.com/kimdol/department-budget/actions/runs/44794959491)
## 전표 인덱스의 쓰기 비용과 승인 잠금

두 번째 100만 전표 실험에서도 월 집계의 DB p95는 51.203ms에서 5.893ms로 줄었습니다. 같은 실행에서 전표 500건을 한 건씩 삽입·동기 커밋했습니다. 인덱스를 실제로 생성·제거하고 후보·기존·기존·후보 순서로 블록별 1회 예열 후 10회씩 측정했습니다. 500건 묶음의 p95는 기존 260.655ms, 후보 171.916ms였지만 CI 디스크 부하의 영향은 분리할 수 없습니다. 세션 redo는 전표 한 건당 평균 1,021.66B에서 1,398.29B로 376.63B 증가했습니다. 읽기 이점과 쓰기 비용을 함께 고려해 부서·기간·신청번호 인덱스를 V023에 포함했습니다. [스키마](../../src/main/resources/db/V023__posting_month_index.sql) · [쓰기 시험](../../ops/measure_oracle_posting_writes.py)

같은 환경에서 두 JDBC 세션으로 승인에 사용하는 부서 예산 행을 조회했습니다. 첫 세션이 잠금을 잡고 두 번째 세션의 측정 시작 신호를 받은 뒤 100ms 후 해제했습니다. 동일 부서의 잠금 조회 p95는 101.134ms, 다른 부서는 0.660ms였습니다. 조건별 예열 1회와 측정 20회를 교대로 수행했습니다. 이 시간에는 JDBC 왕복이 포함됩니다. 위 월 집계 재현 명령에 이어 워크플로의 JDK 21·Oracle JDBC 명령과 `python3 ops/measure_oracle_posting_writes.py`를 실행하면 결과를 비교할 수 있습니다. [DB 실행 계획·쓰기 44회·잠금 CSV 원자료](oracle-posting-costs-44796949735.zip) · [실행 기록](https://github.com/kimdol/department-budget/actions/runs/44796949735)
## 동시 승인 HTTP 응답 시간

실제 Oracle·배포 WAR의 루프백 HTTP 경로에서 성공 응답 p95는 **13.844ms**, 예산 부족 응답 p95는 **19.678ms**였습니다. 22쌍 모두 승인 전표 1건과 잔액 20,000원을 확인했습니다. 두 값은 응답 상태별 관측치이며 잠금으로 생긴 시간만 분리한 값은 아니다. [Oracle 원자료 44개](oracle-approval-contention-44807797272.json) · [실행 기록](https://github.com/kimdol/department-budget/actions/runs/44807797272)

새 은행 업무 공간에서 본부가 지점에 900,000원을 배정해 가용액 100,000원을 남깁니다. 직원이 실비 예정 100,000원의 교육비 두 건을 제출하면 건별 지원액은 80,000원입니다. 같은 부서장 세션에서 두 승인 POST를 동시에 시작하고 응답 완료까지의 클라이언트 경과 시간을 각각 기록합니다. 준비·역할 전환·사후 조회는 시간에서 제외합니다. 매 쌍에서 응답 200·422가 하나씩인지, 성공한 신청에만 약정 전표가 있는지, 가용액이 20,000원인지 확인합니다.

한 서버 프로세스에서 새 방문자와 부서를 매번 생성합니다. 캐시는 비우지 않고 첫 2쌍을 예열로 별도 표시합니다. 뒤의 20쌍을 상태별로 정렬해 19번째 값을 p95로 씁니다. 이 측정은 동시 요청 두 건의 루프백 HTTP 왕복 시간이며 인터넷 지연, 화면 렌더링, DB 잠금 단독 시간과는 측정 범위가 다릅니다. 로컬 H2·HTTPS에서 측정 절차를 실행했을 때 승인 p95는 6.095ms, 예산 부족 응답 p95는 11.308ms였습니다. [로컬 44개 표본](approval-contention-loopback.json) · [측정 코드](../../frontend/tests/approval-contention.spec.ts)

실제 Oracle 측정은 Linux CI의 배포 WAR 브라우저 검증 직후 같은 Tomcat을 사용합니다. `frontend`에서 `TEST_EXTERNAL_RELEASE=1 RECORD_APPROVAL_CONTENTION=../target/approval-contention.json npx --no-install playwright test approval-contention.spec.ts --reporter=list`로 재현합니다. 시험용 WAR는 루프백 18753 포트에서 실행돼야 합니다. `oracle-approval-contention` 아티팩트에는 CPU·메모리·커널·커밋·개별 응답과 전표 수를 기록합니다.
