# 단일 VM에서 HTTPS로 운영하기

Java 21과 Caddy 2가 설치된 Linux VM을 기준으로 한다. 홈 디렉터리에서 학습용으로 실행한 서버를 종료해도 동작하도록 Java와 Caddy를 각각 서비스로 관리한다. 공개 호스트명과 Oracle 접속 정보는 배포 계정에서 설정한다.

1. VM의 공인 주소를 가리키는 DNS 레코드를 준비한다. OCI 수신 규칙과 OS 방화벽에서 웹용 TCP 80·443을 허용한다. Java 포트는 같은 머신의 Caddy만 연결하도록 루프백에 바인딩한다.
2. [Oracle 설치 절차](ORACLE.md)에 따라 운영 스키마를 준비한다. 전용 OS 사용자로 실행할 Java 서비스에 `BUDGET_DB_URL`, `BUDGET_DB_USER`, `BUDGET_DB_PASSWORD`를 제공한다. 자격 증명 파일은 관리자만 읽도록 설정한다. 서비스의 작업 디렉터리에 WAR를 두고 `java -jar department-budget-0.1.0.war --spring.profiles.active=proxy`로 실행한다.
3. Caddy 서비스 환경에 실제 DNS 이름을 `BUDGET_PUBLIC_HOST`로 설정한다. 같은 변수가 설정된 셸에서 `caddy validate --config ops/Caddyfile --adapter caddyfile`을 실행하고 [설정 파일](../ops/Caddyfile)을 서비스에 적용한다. 인증서 저장 공간은 재기동 후에도 보존한다. Caddy가 전달하는 원래 접속 프로토콜은 같은 VM의 루프백에서만 신뢰한다. [Caddy 프록시 헤더 동작](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#headers)
4. Java와 Caddy 서비스를 재기동한 다음 외부 네트워크에서 데모를 연다. 저장소의 `frontend`에서 환경 변수 `TEST_DEPLOYMENT_URL`에 공개 HTTPS 원점 주소를, `RECORD_TLS`에 `1`을 지정하고 `npx --no-install playwright test --reporter=list`를 실행한다. 외부 시험은 공개 인증서, 세션·캐시 헤더, 두 방문자의 독립 업무 처리를 함께 검사한다.

로컬에서 [Tomcat 통합 시험](../src/test/java/kr/budget/web/ProxyTransportTest.java)으로 루프백 HTTP 요청과 HTTPS 전달 헤더를 받은 요청의 HSTS 응답 차이, 세션 속성, 민감 화면의 캐시 정책을 확인했다. 실제 Caddy 인증서 발급, VM 재기동과 외부 접속 검증은 배포 후 기록한다.
