# 부서장 결재함의 첫 표시와 SQL 조회

신청 50건을 열었을 때 20회 모두 LCP 요소는 JSP의 제목 **부서 결재함**이었다. 이 HTML 요청에서 실행한 MyBatis 조회는 0건이었다. 목록 데이터는 별도 API로 도착했다. 최초 방문에서 목록 표시까지 걸린 시간의 중앙값은 144.90ms였고 LCP는 56.00ms였다. [표본 20건](workspace-timing.json) · [Oracle 실행](https://github.com/kimdol/department-budget/actions/runs/44919460412)

| 측정 구간 | 첫 방문 중앙값 | 첫 방문 p95 | 재방문 중앙값 | 재방문 p95 |
| --- | ---: | ---: | ---: | ---: |
| 제목 LCP | 56.00 | 80.00 | 28.00 | 32.00 |
| 목록 50행 표시 근사 시점 | 144.90 | 156.30 | 59.80 | 61.50 |
| 탐색 시작 → 목록 API 요청 시작 | 61.25 | 69.50 | 15.00 | 15.70 |
| 목록 API 요청 시작 → 응답 끝 | 14.10 | 22.50 | 9.85 | 13.70 |
| 목록 API 응답 끝 → 목록 표시 | 64.80 | 89.40 | 33.80 | 37.80 |
| 목록 API의 Spring 필터 구간 | 11.63 | 20.18 | 8.97 | 12.99 |
| 위 서버 구간에 포함된 SQL 3회 합계 | 7.22 | 17.74 | 6.37 | 11.15 |

단위는 ms다. 구간별로 중앙값과 p95를 각각 계산했다. 최초 방문에서는 API를 요청하기까지 61.25ms, 응답 뒤 목록을 표시하기까지 64.80ms가 걸려 두 구간 모두 목록 요청 자체보다 길었다. 응답 이후에는 비목 코드표 수신까지 4.85ms, 그 뒤 화면에 50행이 추가된 것을 관측하기까지 32.85ms, 이후 프레임 관측까지 26.40ms가 걸렸다. 같은 세션의 재방문에서도 API 응답 뒤의 구간이 가장 길었다. SQL 세 건 중 가장 긴 호출은 신청 목록 조회로 최초 방문 중앙값 5.34ms였다. 이 비교는 측정 당시 표시 지연의 위치를 설명하며, 코드를 변경했을 때 절감될 시간을 추정하지 않는다.

첫 방문 1회차에서 제목은 탐색 시작 후 **60.0ms**에 표시됐다. HTML 요청 `c8deec68-a628-474c-9983-139b4c6327c6`의 서버 구간은 6.223ms였다. 같은 탐색의 목록 요청 `92d9a024-ba2a-4a40-a6ea-5f4b885ee3c6`은 **69.5ms**에 전송됐고 **77.9ms**에 응답 수신을 마쳤다. 이 API의 서버 구간 7.373ms 안에서 신청 목록 4.066ms, 잔액 0.403ms, 조직 0.290ms를 사용했다. 50행은 **127.5ms**에 DOM에서 관측됐고 두 번째 프레임은 **153.6ms**였다. 원자료에서 두 요청 ID로 이 탐색을 찾을 수 있다.

## 측정 조건

합성 교육비 신청 50건이 있는 부서장의 결재함을 1280×900 Chrome에서 열었다. 세션 준비와 데이터 입력은 측정 전에 마쳤다. GitHub Actions의 Ubuntu 러너 한 대에서 Chrome, Spring Boot WAR와 Oracle Database Free 컨테이너를 실행했다. 브라우저는 루프백 HTTP, JDBC는 로컬 TCP를 사용했다. 동시 사용자는 1명이며 CPU나 네트워크에 별도 제한을 걸지 않았다. 공개 데모까지의 인터넷 경로와 TLS 비용은 이 실험의 범위 밖이다.

부서장 세션의 쿠키만 복사한 새 브라우저 컨텍스트에서 최초 방문을 측정하고, 빈 탭으로 이동했다가 같은 컨텍스트에서 같은 주소를 다시 열었다. 1쌍을 예열로 제외하고 10쌍을 기록했다. 최초·재방문은 브라우저 캐시 조건을 가리킨다. JVM과 DB 캐시는 방문 사이에 비우지 않았다. p95는 정렬한 표본의 최근접 순위로 구해 각 10회에서는 최댓값에 해당한다.

측정 커밋은 `1af49f2`다. 러너에 표시된 CPU는 Intel Xeon 6975P-C, 논리 CPU는 4개, 메모리는 15.33GiB였다. Chrome 버전은 157.0.8103.78이었다.

## 같은 요청을 대조하는 방법

1. Chrome이 탐색별로 LCP 후보의 표시 시각·요소와 `GET /api/workspace`의 요청 시작·응답 시작·끝을 기록한다. 목록 50행이 DOM에 생기면 연속된 두 번째 애니메이션 프레임의 시각도 기록한다. 이 시점은 목록 표시의 근사값으로, 레이아웃과 프레임 대기를 포함한다. 실제 픽셀 표시나 목록 렌더링만의 CPU 시간은 별도로 재지 않았다. [LCP 정의](https://www.w3.org/TR/largest-contentful-paint/)
2. 계측을 켠 서버는 HTML 응답과 목록 API 응답에 각각 고유한 요청 ID를 부여한다. 브라우저는 각 응답의 `X-Request-Id`와 Resource Timing의 `Server-Timing` 값을 대조하고, 서버 로그에서 같은 ID를 찾아 해당 요청의 쿼리를 연결한다. LCP는 문서 탐색 단위이고 목록 SQL은 그 탐색에서 일어난 API 요청 단위다. 시계가 다른 Java와 브라우저 사이에서는 ID와 소요 시간을 대조한다. [Server Timing 정의](https://www.w3.org/TR/server-timing/) · [서버 계측](../../src/main/java/kr/budget/web/WorkspaceTiming.java)
3. 서버 시간은 서블릿 필터가 다음 단계에 요청을 넘긴 때부터 반환받은 때까지다. 이 안에서 MyBatis의 `StatementHandler.query` 호출을 매퍼별로 측정한다. SQL 시간에는 JDBC 왕복·결과 인출·객체 매핑이 들어간다. 연결 획득과 SQL 준비는 이 호출보다 앞서며, DB 엔진 내부의 CPU 시간은 계측하지 않았다. 필터와 SQL 시간은 포함 관계다. 계측 로그에는 요청 ID·매퍼 이름·소요 시간만 기록한다.

## 재현

GitHub Actions의 `Verify Oracle Ledger`를 측정 커밋에서 실행한다. `Install packaged WAR schema and exercise Oracle in Chrome` 단계가 별도 Oracle 스키마에 WAR를 설치하고 부서장 세션을 준비한다. 브라우저 시험이 대조한 원자료는 `workspace-timing` 아티팩트의 `workspace-timing.json`이다. 일반 실행은 요청별 계측을 끈다. [워크플로](../../.github/workflows/verify.yml) · [서버 실행 조건](../../ops/ci_oracle_release.sh) · [브라우저 시험](../../frontend/tests/workspace-timing.spec.ts)

저장소 루트에서 아래 명령으로 원자료의 중앙값과 p95를 다시 계산한다.

```sh
python ops/summarize_workspace_timing.py docs/measurements/workspace-timing.json
```
