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
