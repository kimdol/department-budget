# 결재함 재방문 전송량

로컬 Chrome에서 새 방문자가 결재함을 열고 빈 탭으로 이동한 뒤 같은 주소를 다시 열었다. 첫 방문에 필요한 세 정적 자산 중 jQuery 하나가 87,833바이트를 차지했다. 버전이 URL에 들어 있는 해당 라이브러리에 1년 공개 캐시를 적용했다. JSP, 업무 API와 같은 이름으로 갱신하는 자체 스크립트에는 no-store를 유지했다.

| Chrome Resource Timing 합계 | 첫 방문 | 같은 세션 재방문 |
| --- | ---: | ---: |
| 변경 전 5회 중앙값 | 97,383 B | 97,383 B |
| 변경 후 5회 중앙값 | 97,383 B | 9,550 B |

재방문 시 세 자산의 전송량이 87,833바이트(90.2%) 줄었다. 첫 방문 전송량은 같았다. jQuery 파일은 만료 전까지 브라우저 저장 공간을 사용하며 교체 시 URL의 버전도 변경해야 한다. 측정 범위는 정적 파일 세 개다. 전체 페이지의 표시 시간, WAN 지연 및 Oracle 응답 시간은 이 시험으로 측정하지 않았다.

## 실험 조건

Windows x64, Ryzen 5 3600, 논리 CPU 12개, 메모리 16GiB, Chrome 157.0.8103.79, 로컬 HTTP Tomcat·H2를 사용했다. 네트워크 제한 없이 한 번에 한 탭이 조회했다. 각 조건에서 독립적인 신규 브라우저 컨텍스트 5개를 만들었다. 각 컨텍스트에서 개인 데모를 시작하기 전 동일한 빈 결재함을 두 번 열었다. 최초 요청에 캐시가 비어 있고 두 번째 탐색은 캐시를 유지한다. 각 파일의 transferSize는 브라우저가 보고한 응답 본문과 헤더 추정치의 합이다.

[기준선 aebe4cf](before.json) · [변경 후 b5c3bbd](after.json) · [측정 코드](../../frontend/tests/desk-transfer.spec.ts) · [HTTP 캐시 회귀 시험](../../src/test/java/kr/budget/web/PortalHttpTest.java)

두 리비전의 앱에 같은 측정 스크립트를 사용한다. 기준선의 앱과 시험 코드는 37d3247에서 함께 체크아웃할 수 있다. Java 21, Maven, Chrome 설치 후 frontend에서 npm ci와 npm run build를 실행한다. PowerShell에서 아래처럼 실행하면 원자료를 다시 생성한다.

```powershell
$env:MEASURE_DESK='before'
npx playwright test desk-transfer
```

변경 후에는 b5c3bbd에서 같은 명령의 MEASURE_DESK 값을 after로 지정한다.

## 수식 입력 후 결과 표시

Chrome 한 세션에서 실비 180,000원의 기본 신청 공간을 열고 수식을 `eligible_cost`로 설정한다. 결과가 갱신된 뒤 ` * 0.9` 여섯 글자를 40ms 간격으로 입력한다. 마지막 input 이벤트부터 후보 지원액 162,000원이 DOM에 반영될 때까지 브라우저 시계로 측정했다. 실제 화면 페인트와 외부 네트워크 지연은 측정 범위 밖이다.

로컬 H2·Tomcat HTTPS, 동일 장비·Chrome, 동시 사용자 1명, 예열 1회 후 같은 페이지에서 5회 측정했다. 변경 전 중앙값은 264.7ms(263.1~266.6), 변경 후는 157.0ms(156.9~158.2)였다. 두 조건 모두 매회 요청 1건이다. 150~250ms 동안 입력을 멈추면 새 설정에서는 중간 수식 요청이 추가될 수 있다. 이 입력 속도 구간의 비용은 측정하지 않았다.

재현 시 변경 전은 d6ff907, 변경 후는 f499d69를 사용한다. 각각 frontend에서 npm ci와 npm run build를 실행한 뒤 저장소 루트에서 환경 변수 MEASURE_FORMULA를 before 또는 after로 지정하고 `python ops/test_https.py`를 실행한다. Git 이력 d6ff907은 기준 리비전 9c94447에 측정기와 기초 원자료만 추가한 커밋이다. [측정 코드](../../frontend/tests/formula-latency.spec.ts) · [변경 전](formula-before.json) · [변경 후](formula-after.json)

## Oracle 결재함 100만 건 측정

합성 부서 100곳에 신청을 각각 10,000건씩 넣고 통계를 수집한다. 50번째 부서에서 최신순 9,000건을 건너뛰는 조회와 같은 경계를 키셋으로 조회한다. 두 SQL은 동일 매퍼 열을 반환하며, 결과의 신청번호 51개와 순서까지 검사한다. 첫 실행을 포함해 순서를 바꿔 네 번씩 실행한다. SQL별 실제 실행 계획과 논리 읽기는 CI 산출물 `oracle-inbox-plans/plans.txt`에 있다. 이 실험은 균등 분포이고 월 집계·동시 쓰기 지연은 별도 측정 대상이다. [실행 명령과 데이터 생성 SQL](../../ops/measure_oracle_inbox.py)

실행 계획 채취 후 같은 세션에서 두 커서를 각각 한 번 예열하고 순서를 번갈아 40회씩 실행한다. Oracle 서버 시계로 커서 열기부터 51개 행 전체 열의 일괄 인출과 커서 닫기까지 측정한다. 행 검증과 출력은 시간 구간에서 제외한다. 원자료 latency.json에는 장비 정보, 마이크로초 단위 표본 전체와 오름차순 38번째 값(p95)을 기록한다. 네트워크·JDBC·화면 표시 시간은 포함하지 않는다.
동일 조건에서 각 SQL을 40회 인출한 결과, 서버 조회 p95는 OFFSET 6.608ms, 키셋 0.137ms였다. 측정은 동시 사용자 1명과 예열된 동일 페이지에 한정했다. [전체 표본과 장비](oracle-latency-44781904882.json) · [실행 기록](https://github.com/kimdol/department-budget/actions/runs/44781904882)
