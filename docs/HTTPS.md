# 단일 VM에서 HTTPS로 운영하기

Java 21과 Caddy 2가 설치된 Linux VM을 기준으로 한다. 홈 디렉터리에서 학습용으로 실행한 서버를 종료해도 동작하도록 Java와 Caddy를 각각 서비스로 관리한다. 공개 호스트명과 Oracle 접속 정보는 배포 계정에서 설정한다.

## 검증한 WAR 받기

메인 브랜치의 Oracle·Chrome·Caddy 검증을 통과하면 해당 Actions 실행에 `department-budget-release-<전체 커밋 SHA>`가 3일 동안 보관된다. WAR 해시는 기존 신청의 전표를 읽은 복귀 시험 기록과 대조하고, 설치 SQL은 같은 WAR에서 생성한 정의와 대조한다. 설치 SQL은 빈 스키마용이다. 기존 스키마의 업그레이드는 [Oracle 마이그레이션 절차](ORACLE.md)를 따른다.

GitHub에 로그인된 배포 작업 환경에서 성공한 실행 번호를 `RUN_ID`에, 배포하기로 정한 커밋의 40자리 SHA를 `RELEASE_SHA`에 지정한다. 새 디렉터리에서 다음 명령을 실행한다.

```sh
set -eu
: "${RUN_ID:?성공한 실행 번호 필요}" "${RELEASE_SHA:?배포 커밋 SHA 필요}"
gh run download "$RUN_ID" --repo kimdol/department-budget --name "department-budget-release-$RELEASE_SHA" --dir release
cd release
test "$(cat commit.txt)" = "$RELEASE_SHA"
sha256sum --check SHA256SUMS
```

검사가 끝난 `department-budget.war`를 아래 서비스 설치 경로에 배치한다. `Caddyfile`과 서비스 정의도 같은 묶음에 포함된다. 운영 계정·비밀번호·호스트명은 배포 VM에서 제공한다. 보관 기간이 지난 커밋은 해당 소스와 시험 결과를 대조하고 새 배포 묶음을 준비한다.

## 배포 순서

1. VM의 공인 주소를 가리키는 DNS 레코드를 준비한다. OCI 수신 규칙과 OS 방화벽에서 웹용 TCP 80·443을 허용한다. Java 포트는 같은 머신의 Caddy만 연결하도록 루프백에 바인딩한다.
2. [Oracle 설치 절차](ORACLE.md)에 따라 운영 스키마를 준비한다. 전용 OS 사용자로 실행할 Java 서비스에 `BUDGET_DB_URL`, `BUDGET_DB_USER`, `BUDGET_DB_PASSWORD`를 제공한다. 자격 증명 파일은 관리자만 읽도록 설정한다. 아래 Java 서비스 설치 절차에 따라 검증한 WAR를 설치한다. systemd가 proxy 프로필로 기동한다.
3. Caddy 서비스 환경에 실제 DNS 이름을 `BUDGET_PUBLIC_HOST`로 설정한다. 같은 변수가 설정된 셸에서 `caddy validate --config ./Caddyfile --adapter caddyfile`을 실행하고 [설정 파일](../ops/Caddyfile)을 서비스에 적용한다. 인증서 저장 공간은 재기동 후에도 보존한다. Caddy가 전달하는 원래 접속 프로토콜은 같은 VM의 루프백에서만 신뢰한다. [Caddy 프록시 헤더 동작](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#headers)
4. Java와 Caddy 서비스를 재기동한 다음 외부 네트워크에서 데모를 연다. 저장소의 `frontend`에서 환경 변수 `TEST_DEPLOYMENT_URL`에 공개 HTTPS 원점 주소를, `RECORD_TLS`에 `1`을 지정하고 `npx --no-install playwright test --reporter=list`를 실행한다. 외부 시험은 공개 인증서, 세션·캐시 헤더, 두 방문자의 독립 업무 처리를 함께 검사한다.

Ubuntu CI에서 실제 Caddy가 시험 CA로 발급한 인증서를 신뢰 목록에 등록한 뒤 Chrome으로 배포용 WAR와 Oracle에 접속했다. 두 방문자의 독립 신청·정산·초기화, HSTS·세션·캐시, 정적 자산 재사용을 확인했다. systemd 서비스 정의는 같은 실행에서 정적 검사를 통과했다. 실제 OCI의 공개 인증서 발급, VM 재기동과 외부 접속 검증은 배포 후 기록한다. [Caddy HTTPS 실행](https://github.com/kimdol/department-budget/actions/runs/44772923282/job/136415004389) · [시험 스크립트](../ops/ci_https_proxy.sh)

## 1GB VM의 Java 메모리 설정

AMD Micro에서는 Oracle 원장과 MySQL 수신 스키마를 각각 관리형 DB에 두고 Java·Caddy·PHP-FPM을 같은 VM에서 실행한다. Java 서비스 환경 파일의 후보 설정은 다음과 같다. 포털 CA용 Java 옵션이 있다면 같은 `JAVA_TOOL_OPTIONS` 값에 이어 붙인다.

```text
JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m"
SERVER_TOMCAT_THREADS_MAX=32
SERVER_TOMCAT_THREADS_MIN_SPARE=4
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1
```

Windows OpenJDK 21에서 동일 WAR와 매번 새로 만든 H2 스키마로 기본→제한→제한→기본 순서로 시험했다. Java의 활성 프로세서 수를 1로 지정했고 Chrome 작업자 1개가 독립 세션 두 개로 신청·정산·마감·초기화를 수행했다. 기동부터 브라우저 시험 종료까지 Java 프로세스 메모리를 0.1초 간격으로 샘플링했다.

| 설정 | 관측한 최대 작업 집합 | 관측한 최대 전용 커밋 메모리 | 첫 HTTP 200 |
| --- | ---: | ---: | ---: |
| JVM·연결 수 기본값 | 296.4~305.1 MiB | 428.7~437.8 MiB | 6.093~6.734초 |
| 1GB VM용 후보 | 243.4~244.7 MiB | 238.3~243.0 MiB | 6.141~7.313초 |

비교 항목은 Java 프로세스 단독이다. OCI Linux의 Java·Caddy·PHP 합계, 실제 Oracle 연결, 동시 요청 대기는 서버에서 별도로 측정한다. [측정 요약](measurements/micro-memory-summary.json) · [샘플 원자료](measurements/micro-memory-samples.csv)

Windows에서 Java 21 경로, H2 2.3.232 JAR 경로와 해시를 확인한 배포 ZIP을 지정한다. frontend에서 npm ci를 마치고 Chrome을 설치한 뒤 저장소 루트에서 실행한다. 18753 포트가 비어 있어야 한다.

```powershell
python ops/measure_micro_memory.py --bundle $bundle --java $java --h2 $h2
```

## 포털 수신 연결

배포 묶음의 `portal/mysql/V001__projection.sql`을 빈 MySQL 수신 스키마에 적용한다. Java와 PHP-FPM에서 사용할 Oracle과 MySQL은 같은 VM의 루프백 주소로 연결한다. Oracle Linux에 PHP-FPM과 PDO MySQL을 설치하고, 로그인 셸이 없는 시스템 사용자와 그룹 `department-budget-portal`을 준비한다.

```sh
sudo install -d -m 0755 /opt/department-budget/portal
sudo install -m 0644 portal/receive.php portal/status.php /opt/department-budget/portal/
sudo install -m 0644 portal-fpm.conf /etc/department-budget/portal-fpm.conf
sudo install -m 0644 department-budget-portal.service /etc/systemd/system/
```

root만 읽을 수 있는 `/etc/department-budget/portal.env`에 `PORTAL_MYSQL_DSN`, `PORTAL_MYSQL_USER`, `PORTAL_MYSQL_PASSWORD`, `BUDGET_PORTAL_TOKEN`을 설정한다. `sudo systemctl daemon-reload` 후 `sudo systemctl enable --now department-budget-portal`로 수신기를 기동한다. Caddy가 읽는 설정에 `Caddyfile.portal`을 가져오고 Caddy 서비스 환경에 `BUDGET_PORTAL_ROOT=/opt/department-budget/portal`을 설정한다. 이 설정은 127.0.0.1:18759에서 내부 인증서로 TLS를 종료하고 127.0.0.1:18760의 PHP-FPM으로 전달한다.

Java 서비스에 `BUDGET_PORTAL_URL=https://127.0.0.1:18759/receive.php`와 PHP-FPM에 설정한 동일한 `BUDGET_PORTAL_TOKEN`을 지정한다. Caddy가 생성한 로컬 CA의 공개 인증서 `pki/authorities/local/root.crt`를 Java 학습용 신뢰 저장소에 등록한다. 전체 경로의 기준은 [Caddy 데이터 디렉터리](https://caddyserver.com/docs/conventions#data-directory)이며, CA 개인 키를 복사할 필요가 없다.

```sh
sudo keytool -importcert -alias budget-portal -file "$CADDY_ROOT_CERT" \
  -keystore /etc/department-budget/portal-trust.p12 -storetype PKCS12 -storepass changeit
sudo chmod 0644 /etc/department-budget/portal-trust.p12
```

Java 서비스 환경 파일에 `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/etc/department-budget/portal-trust.p12 -Djavax.net.ssl.trustStorePassword=changeit"`을 추가하고 재기동한다. 이 저장소에는 로컬 CA 공개 인증서만 담는다. Java는 수신 주소와 같은 디렉터리의 `status.php`에서 수신액을 읽는다. 대표 신청을 정산한 뒤 포털 수신 차수 2와 수신액 180,000원을 확인한다. [CI의 Oracle·MySQL 대사](../ops/ci_https_proxy.sh)

## Java 서비스 설치와 중지

외부 배포 뒤 대표 정산과 TLS 원자료를 함께 남기려면 저장소의 `frontend`에서 아래 명령을 실행한다. 셸의 `TEST_DEPLOYMENT_URL`에는 이미 배포한 공개 HTTPS 원점을 지정한다. 새 Chrome 세션 두 개가 독립된 합성 업무를 생성한다. 정산 재전송 직후 같은 신청번호의 전표 두 건, 결재함·보고서 잔액, 포털 수신 차수 2와 180,000원을 대조한다. 두 방문자의 업무 격리와 초기화를 확인한 뒤 원자료를 기록한다. TLS 및 세션 쿠키의 속성은 별도 파일에 남긴다.

```sh
: "${TEST_DEPLOYMENT_URL:?공개 HTTPS 원점을 지정하세요}"
export TEST_DEPLOYMENT_URL
mkdir -p ../target
RECORD_SETTLEMENT="$PWD/../target/public-settlement.json" RECORD_TLS=1 \
  npx --no-install playwright test claims.spec.ts https.spec.ts --reporter=list
```

정산 결과는 `target/public-settlement.json`, 전송·캐시 응답은 `docs/measurements/deployment-https.json`에서 확인한다. CI 프록시의 결과는 해당 실행의 `proxy-transport` 아티팩트에 저장된다.

배포 검사 시 브라우저는 자동 이동을 끈 HTTP GET으로 경로·쿼리 보존 여부를 확인한 후 HTTPS에서 업무 공간과 규정 편집기를 연다. Linux CI의 Caddy가 308로 같은 자산 경로와 쿼리를 돌려줬고, 시험 CA를 신뢰한 클라이언트가 TLS 1.3으로 연결했다. 관찰한 고유 요청 주소 11개는 같은 HTTPS 출처였다. 공개 URL에서는 `TEST_DEPLOYMENT_URL`과 `RECORD_TLS=1`로 동일 시험을 다시 수행한다. [CI 실응답](measurements/proxy-transport-44788840479.json)

배포 전에는 직전 WAR의 커밋·SHA-256과 해당 버전이 읽을 수 있는 스키마 버전을 기록하고 WAR를 보관한다. 현재 스키마와 호환되는 버전으로 복귀할 때는 서비스를 중지하고 보관한 WAR의 SHA-256을 확인한 뒤 같은 설치 경로에 배치하고 서비스를 시작한다. 새 세션에서 신청·승인을 처리하고, 배포 전 기록한 신청번호의 원장과 비교한다. 이 절차는 서버 세션을 다시 시작하므로 데모 방문자는 새 업무 공간에서 시작한다. 대상 WAR가 현재 스키마와 호환되지 않으면 별도 스키마 복원 후 데이터 차이를 확인해야 한다. [Oracle 백업·복원](ORACLE.md)

CI는 현재 WAR로 18만 원 정산 후 다음 달에 3만 원을 환입한 신청을 준비한다. 같은 Oracle 스키마에 이전 커밋의 WAR를 기동해 새 방문자의 출장비 결재를 Chrome으로 실행하고, 기존 신청번호의 원래 정산액·귀속 월·전표별 금액을 다시 읽는다. 증거 파일에는 두 WAR의 커밋·해시와 복귀 전후 Oracle 결과를 저장한다. [WAR 복귀 시험](../ops/ci_war_rollback.sh)

새 VM에서 로그인 셸이 없는 시스템 사용자와 그룹을 `department-budget` 이름으로 만든다. `/opt/department-budget`에 검증한 WAR를 `department-budget.war`로 설치한다. 배포 디렉터리와 WAR는 root가 소유하고 서비스 사용자는 읽을 수 있게 한다. 환경 파일 `/etc/department-budget/runtime.env`는 root 소유, 권한 0600으로 생성하고 Oracle 연결 변수를 한 줄에 하나씩 지정한다. 환경 파일을 준비한 뒤 위에서 해시를 검증한 release 디렉터리 안에서 다음 명령을 실행한다.

```sh
sudo install -d -m 0755 /opt/department-budget
sudo install -m 0644 ./department-budget.war /opt/department-budget/department-budget.war
sudo install -m 0644 ./department-budget.service /etc/systemd/system/department-budget.service
sudo systemd-analyze verify /etc/systemd/system/department-budget.service
sudo systemctl daemon-reload
sudo systemctl enable --now department-budget
sudo systemctl status department-budget
```

일시 중지는 `sudo systemctl stop department-budget`, 재개는 `sudo systemctl start department-budget`로 실행한다. 부팅 시 자동 실행도 끄려면 `sudo systemctl disable --now department-budget`을 사용한다. 서비스 중지 후에도 Oracle의 전표가 유지되며, 새 WAR에 스키마 변경이 있으면 재시작 전에 백업과 마이그레이션 절차를 진행한다. [서비스 정의](../ops/department-budget.service) · [원장 복원 절차](ORACLE.md)
### 규정 예약이 남은 부서의 버전 복귀

V021부터 예약은 부서의 현재 회계 기간을 외래 키로 참조한다. 기간 개시 시 새 WAR는 한 트랜잭션에서 예약을 검증·발행하고 예약을 지운 뒤 부서의 기간을 변경한다. 예약을 처리하지 않는 이전 WAR에서 기간만 변경하면 DB가 거절한다. V020 WAR는 필수 열인 `source_period`를 채우지 않으므로 V021 스키마에서 신규 예약을 작성할 수 없다. 예약 업무를 사용 중이면 V021 호환 WAR로 복구한다. 불일치 상태에서 예약 행을 수동으로 삭제하면 부서장이 승인한 다음 달 규정이 유실된다. [V020에서 업그레이드하는 시험](../src/test/java/kr/budget/claims/ScheduledPolicyMigrationTest.java)
