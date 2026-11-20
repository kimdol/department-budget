# 단일 VM에서 HTTPS로 운영하기

Java 21과 Caddy 2가 설치된 Linux VM을 기준으로 한다. 홈 디렉터리에서 학습용으로 실행한 서버를 종료해도 동작하도록 Java와 Caddy를 각각 서비스로 관리한다. 공개 호스트명과 Oracle 접속 정보는 배포 계정에서 설정한다.

1. VM의 공인 주소를 가리키는 DNS 레코드를 준비한다. OCI 수신 규칙과 OS 방화벽에서 웹용 TCP 80·443을 허용한다. Java 포트는 같은 머신의 Caddy만 연결하도록 루프백에 바인딩한다.
2. [Oracle 설치 절차](ORACLE.md)에 따라 운영 스키마를 준비한다. 전용 OS 사용자로 실행할 Java 서비스에 `BUDGET_DB_URL`, `BUDGET_DB_USER`, `BUDGET_DB_PASSWORD`를 제공한다. 자격 증명 파일은 관리자만 읽도록 설정한다. 서비스의 작업 디렉터리에 WAR를 두고 `java -jar department-budget-0.1.0.war --spring.profiles.active=proxy`로 실행한다.
3. Caddy 서비스 환경에 실제 DNS 이름을 `BUDGET_PUBLIC_HOST`로 설정한다. 같은 변수가 설정된 셸에서 `caddy validate --config ops/Caddyfile --adapter caddyfile`을 실행하고 [설정 파일](../ops/Caddyfile)을 서비스에 적용한다. 인증서 저장 공간은 재기동 후에도 보존한다. Caddy가 전달하는 원래 접속 프로토콜은 같은 VM의 루프백에서만 신뢰한다. [Caddy 프록시 헤더 동작](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#headers)
4. Java와 Caddy 서비스를 재기동한 다음 외부 네트워크에서 데모를 연다. 저장소의 `frontend`에서 환경 변수 `TEST_DEPLOYMENT_URL`에 공개 HTTPS 원점 주소를, `RECORD_TLS`에 `1`을 지정하고 `npx --no-install playwright test --reporter=list`를 실행한다. 외부 시험은 공개 인증서, 세션·캐시 헤더, 두 방문자의 독립 업무 처리를 함께 검사한다.

Ubuntu CI에서 실제 Caddy가 시험 CA로 발급한 인증서를 신뢰 목록에 등록한 뒤 Chrome으로 배포용 WAR와 Oracle에 접속했다. 두 방문자의 독립 신청·정산·초기화, HSTS·세션·캐시, 정적 자산 재사용을 확인했다. systemd 서비스 정의는 같은 실행에서 정적 검사를 통과했다. 실제 OCI의 공개 인증서 발급, VM 재기동과 외부 접속 검증은 배포 후 기록한다. [Caddy HTTPS 실행](https://github.com/kimdol/department-budget/actions/runs/44772923282/job/136415004389) · [시험 스크립트](../ops/ci_https_proxy.sh)

## Java 서비스 설치와 중지

배포 검사 시 브라우저는 자동 이동을 끈 HTTP GET으로 경로·쿼리 보존 여부를 확인한 후 HTTPS에서 업무 공간과 규정 편집기를 연다. Linux CI의 Caddy가 308로 같은 자산 경로와 쿼리를 돌려줬고, 시험 CA를 신뢰한 클라이언트가 TLS 1.3으로 연결했다. 관찰한 고유 요청 주소 11개는 같은 HTTPS 출처였다. 공개 URL에서는 `TEST_DEPLOYMENT_URL`과 `RECORD_TLS=1`로 동일 시험을 다시 수행한다. [CI 실응답](measurements/proxy-transport-44788840479.json)

배포 전에는 직전 WAR의 커밋·SHA-256과 해당 버전이 읽을 수 있는 스키마 버전을 기록하고 WAR를 보관한다. 현재 스키마와 호환되는 버전으로 복귀할 때는 서비스를 중지하고 보관한 WAR의 SHA-256을 확인한 뒤 같은 설치 경로에 배치하고 서비스를 시작한다. 새 세션에서 신청·승인을 처리하고, 배포 전 기록한 신청번호의 원장과 비교한다. 이 절차는 서버 세션을 다시 시작하므로 데모 방문자는 새 업무 공간에서 시작한다. 대상 WAR가 현재 스키마와 호환되지 않으면 별도 스키마 복원 후 데이터 차이를 확인해야 한다. [Oracle 백업·복원](ORACLE.md)

CI는 현재 WAR로 18만 원 정산 후 다음 달에 3만 원을 환입한 신청을 준비한다. 같은 Oracle 스키마에 이전 커밋의 WAR를 기동해 새 방문자의 출장비 결재를 Chrome으로 실행하고, 기존 신청번호의 원래 정산액·귀속 월·전표별 금액을 다시 읽는다. 증거 파일에는 두 WAR의 커밋·해시와 복귀 전후 Oracle 결과를 저장한다. [WAR 복귀 시험](../ops/ci_war_rollback.sh)

새 VM에서 로그인 셸이 없는 시스템 사용자와 그룹을 `department-budget` 이름으로 만든다. `/opt/department-budget`에 검증한 WAR를 `department-budget.war`로 설치한다. 배포 디렉터리와 WAR는 root가 소유하고 서비스 사용자는 읽을 수 있게 한다. 환경 파일 `/etc/department-budget/runtime.env`는 root 소유, 권한 0600으로 생성하고 Oracle 연결 변수를 한 줄에 하나씩 지정한다. 이 파일을 Git이나 빌드 결과에 포함하지 않는다.

```sh
sudo install -m 0644 ops/department-budget.service /etc/systemd/system/department-budget.service
sudo systemd-analyze verify /etc/systemd/system/department-budget.service
sudo systemctl daemon-reload
sudo systemctl enable --now department-budget
sudo systemctl status department-budget
```

일시 중지는 `sudo systemctl stop department-budget`, 재개는 `sudo systemctl start department-budget`로 실행한다. 부팅 시 자동 실행도 끄려면 `sudo systemctl disable --now department-budget`을 사용한다. 서비스 중지 후에도 Oracle의 전표가 유지되며, 새 WAR에 스키마 변경이 있으면 재시작 전에 백업과 마이그레이션 절차를 진행한다. [서비스 정의](../ops/department-budget.service) · [원장 복원 절차](ORACLE.md)
### 규정 예약이 남은 부서의 버전 복귀

V021부터 예약은 부서의 현재 회계 기간을 외래 키로 참조한다. 기간 개시 시 새 WAR는 한 트랜잭션에서 예약을 검증·발행하고 예약을 지운 뒤 부서의 기간을 변경한다. 예약을 처리하지 않는 이전 WAR에서 기간만 변경하면 DB가 거절한다. V020 WAR는 필수 열인 `source_period`를 채우지 않으므로 V021 스키마에서 신규 예약을 작성할 수 없다. 예약 업무를 사용 중이면 V021 호환 WAR로 복구한다. 불일치 상태에서 예약 행을 수동으로 삭제하면 부서장이 승인한 다음 달 규정이 유실된다. [V020에서 업그레이드하는 시험](../src/test/java/kr/budget/claims/ScheduledPolicyMigrationTest.java)
