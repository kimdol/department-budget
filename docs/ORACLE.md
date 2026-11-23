# Oracle 설치와 원장 복원

## 최초 설치

업무 스키마와 Spring Batch 메타데이터는 같은 Oracle 사용자 소유입니다. Oracle PDB에 데이터 테이블스페이스 할당량과 CREATE SESSION, CREATE TABLE, CREATE SEQUENCE 권한이 있는 빈 사용자를 준비합니다. 개발·시험·공개 배포는 각각 다른 사용자로 접속합니다. Oracle Autonomous Database 19c의 계정 비밀번호는 12~30바이트 제한에 맞춥니다. 아래 명령은 저장소 루트 기준입니다. BUDGET_APP과 BUDGET_PDB는 준비한 사용자와 접속 별칭으로 바꾸며 비밀번호는 프롬프트에 입력합니다. [Oracle 19c 비밀번호 규칙](https://docs.oracle.com/en/database/oracle/oracle-database/19/dbseg/guidelines-securing-passwords.html)

```text
python ops/oracle_install.py target/department-budget-0.1.0.war target/install.sql
sqlplus -L BUDGET_APP@BUDGET_PDB @target/install.sql
```

[설치 파일 생성기](../ops/oracle_install.py)는 배포할 WAR에 포함된 V001 이후의 업무 SQL과 같은 WAR의 Spring Batch Core에서 추출한 Oracle 전용 SQL을 묶습니다. 실제 WAR에서 V001~V009, Spring Batch 5.2.5의 테이블 정의 및 시퀀스 3개를 추출했습니다. CI에서는 Spring의 SQL 초기화로 실제 Oracle 설치를 검증했고 Data Pump로 별도 스키마 복원에 성공했습니다. 배포용 WAR에서 생성한 설치 SQL을 빈 Oracle 스키마에 적용하고 같은 SQL의 재실행이 ORA-20001로 차단됨을 확인했습니다. 이 스키마에 WAR를 연결해 Chrome에서 두 방문자의 교육비 처리와 월마감, 병원 학회비 규정 발행·복구, 출장 사전승인, 결재함 페이지 이동 시험 4건을 통과했습니다. [실행 결과](https://github.com/kimdol/department-budget/actions/runs/44765732583) [복원 결과](https://github.com/kimdol/department-budget/actions/runs/44764720608)

## OCI 배포 VM에서 JDBC 연결 확인

Autonomous Database를 사용할 때는 배포 VM에서 접근 가능한 네트워크를 설정합니다. 지갑 없이 TLS를 사용하는 구성에서는 DB에 TLS 접속을 허용하고 접근 제어 목록에 VM의 송신 주소 또는 해당 VCN을 등록합니다. DB 콘솔의 연결 화면에서 TLS용 접속 설명자를 받아 `jdbc:oracle:thin:@` 뒤에 붙인 값을 `BUDGET_DB_URL`로 설정합니다. 사용자·비밀번호는 앱 전용 계정을 씁니다. [Oracle JDBC Thin TLS 연결](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/connect-jdbc-thin-tls.html) · [네트워크 및 인증 설정](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/support-tls-mtls-authentication.html)

업무 및 배치 스키마를 설치한 뒤 서비스와 동일한 VM·접속 환경 변수로 다음 검사를 수행합니다. 저장소 루트에서 배포할 WAR를 사용합니다. JDBC 연결 후 Oracle이 보고한 세션 전송 방식과 원장·규정·배치 테이블 접근을 검사합니다. 결과에는 드라이버 버전, 전송 방식과 검사 건수만 출력합니다.

```sh
driver_dir=$(mktemp -d)
unzip -j target/department-budget-0.1.0.war 'WEB-INF/lib/ojdbc*.jar' -d "$driver_dir"
release_id=$(python3 ops/oracle_install.py target/department-budget-0.1.0.war --release-id)
java -cp "$driver_dir/*" ops/CheckRuntimeDatabase.java TCPS "$release_id"
```

검사기는 배포할 WAR에서 계산한 버전 번호와 정의 해시를 설치된 스키마의 값과 비교합니다. 설치가 끝나 READY 상태이고 두 값이 일치해야 업무 테이블 검사를 진행합니다. CI는 정상 연결, 다른 버전 번호 거절, TCP 세션에서 TCPS 요구 거절을 각각 검사합니다. OCI 오사카 배포 VM에서 Autonomous Database 19c에 접속해 TCPS 세션과 V025 스키마를 확인했습니다. [접속 검사](../ops/CheckRuntimeDatabase.java) · [배포 WAR 시험](../ops/ci_oracle_release.sh)

Oracle Free의 같은 PDB 내 별도 스키마에 복원한 CI 실행에서 Data Pump 내보내기는 51초, 가져오기는 33초였습니다. 원본·복원 대사 파일은 각각 169행이며 내용이 일치했습니다. 대사 단계의 초 단위 계측값은 0초였습니다. 이 수치는 합성 계약 시험 데이터에 대한 1회 관측이며 VM 재구축, 외부 전송, 업무 재개 시간은 포함하지 않습니다. [시간 원자료](measurements/oracle-restore-time-44784441705.txt) · [실행 기록](https://github.com/kimdol/department-budget/actions/runs/44784441705)

## 기존 버전에서 올리기

Oracle Free에서 V017 WAR의 정산 신청과 전표 두 건을 넣은 스키마를 V024로 올렸습니다. 업그레이드 후 이전 신청의 약정 잔액은 0원, 집행액은 180,000원, 가용액은 820,000원이었습니다. 스키마 상태는 READY였고 같은 업그레이드의 재실행은 ORA-20002로 거절됐습니다. [결과 원자료](measurements/oracle-upgrade-44799267812.txt) · [시험 실행](https://github.com/kimdol/department-budget/actions/runs/44799267812/job/136506837479)

현재 생성기로 설치한 스키마에서는 배포 중인 WAR와 새 WAR를 모두 보관합니다. 요청과 배치 쓰기를 중지하고 백업한 후 다음 명령으로 차이 SQL을 생성합니다.

```sh
python ops/oracle_install.py target/new.war target/upgrade.sql --upgrade-from target/previous.war
sqlplus -L BUDGET_APP@BUDGET_PDB @target/upgrade.sql
```

생성기는 이전 업무 SQL 전체와 Spring Batch Oracle 정의가 새 WAR와 일치하는지 확인합니다. 실행 시 `budget_schema_release`의 마지막 버전·정의 해시·READY 상태가 이전 WAR와 모두 일치해야 새 V파일을 적용합니다. 동시 실행이나 이미 적용한 스크립트의 재실행은 첫 DDL 전에 거절합니다. DDL 실패 시 상태는 UPGRADING이며 `last_version`은 마지막으로 끝난 파일을 가리킵니다. 실패한 파일 안의 일부 DDL도 커밋됐을 수 있으므로 실행 로그와 DB를 대조하거나 검증된 백업을 복원합니다. 메타데이터가 없는 기존 설치는 아래 수동 절차를 따릅니다.

현재 배포 리비전과 적용한 마지막 V번호를 확인하고 아래 절차로 백업합니다. 요청 및 배치 실행을 중지한 뒤 새 WAR에 추가된 V번호만 순서대로 SQL*Plus에서 실행합니다. 적용 파일명, 체크섬과 결과를 배포 기록에 보관합니다. Spring Batch 라이브러리 버전도 변경됐다면 해당 버전의 Oracle 메타데이터 마이그레이션을 함께 검토합니다. Oracle DDL은 암묵적 커밋을 수행하므로 중간 실패 시 마지막 성공 문장과 실제 제약·열 상태를 대조합니다. 최초 설치 스크립트는 기존 객체가 하나라도 있으면 작업을 중단합니다.

## 백업과 별도 스키마 복원

GitHub Actions의 Verify Oracle Ledger 작업은 로컬·Oracle 계약 시험 이름이 일치하고 모두 실행됐는지 검사합니다. 원본 `FREEPDB1.BUDGET`을 Data Pump로 내보내고 같은 CDB에 새로 만든 `BUDGETVERIFY.BUDGET_RESTORE`로 가져옵니다. 두 연결의 PDB와 스키마 이름을 각각 검사합니다. 전표 49건, 포털 송신 메시지 47건을 포함한 대사 결과 547행이 바이트 단위로 일치했습니다. 내보내기는 36초, 가져오기는 26초였습니다. 별도 인스턴스 재해 복구는 추가 검증 대상입니다. [PDB와 대사 원자료](measurements/oracle-restore-44896984081.zip) · [CI 실행](https://github.com/kimdol/department-budget/actions/runs/44896984081) · [복원 절차](../ops/ci_oracle_restore.sh)

아래는 자체 관리 Oracle PDB에서 DBA가 Data Pump 디렉터리 BUDGET_BACKUP을 만들고 읽기·쓰기 권한과 서버 디스크 공간을 제공한 경우의 절차입니다. 같은 Oracle 버전·문자 집합의 별도 검증 PDB에 비어 있는 대상 스키마를 준비합니다. 같은 서비스로 전달할 외부 연계와 예약 작업을 복원 환경에서 비활성화합니다. Autonomous Database의 객체 스토리지 내보내기와 복구는 해당 서비스 운영 절차가 필요합니다.

1. 원본 앱과 배치·연계 쓰기를 멈추고 처리 중 트랜잭션이 끝나면 대사 파일을 만듭니다. 서버에서 내보내기가 끝날 때까지 쓰기 중지를 유지합니다.
2. 원본에서 아래처럼 스키마를 내보냅니다. 운영 때는 시점별로 고유한 덤프·로그 이름을 사용합니다. 덤프는 DB 서버의 디렉터리에 생깁니다.
3. 덤프를 검증 PDB에서 접근할 수 있는 별도 Data Pump 디렉터리로 옮기고 파일 체크섬을 대조합니다. 복원 작업은 대상 스키마로 허가받은 계정으로 실행합니다.
4. 두 Data Pump 로그에서 오류를 확인하고 대상에 같은 대사 SQL을 실행합니다. 같은 SQL*Plus 버전과 인코딩으로 출력 파일을 비교합니다. 전표별 귀속 월·금액·원전표 참조, 공간별 약정·집행 합계 및 마감 스냅샷의 차이가 없어야 합니다. POLICY와 CLAIM 행에서 수식·참석 조건의 SHA-256 및 평가기 버전을 대조하고, 같은 신청번호의 EVIDENCE 행에서 증빙 유형·금액을 확인합니다. 이후 복원 DB에 연결한 앱에서 원신청을 조회합니다.

```text
sqlplus -L -S BUDGET_APP@BUDGET_PDB @ops/oracle_reconcile.sql > before.txt
expdp BUDGET_APP@BUDGET_PDB SCHEMAS=BUDGET_APP DIRECTORY=BUDGET_BACKUP DUMPFILE=budget_backup.dmp LOGFILE=budget_export.log
impdp BUDGET_RESTORE@VERIFY_PDB REMAP_SCHEMA=BUDGET_APP:BUDGET_RESTORE DIRECTORY=BUDGET_BACKUP DUMPFILE=budget_backup.dmp LOGFILE=budget_import.log
sqlplus -L -S BUDGET_RESTORE@VERIFY_PDB @ops/oracle_reconcile.sql > restored.txt
git diff --no-index -- before.txt restored.txt
```

각 명령의 종료 코드를 확인하고 두 파일 모두 WORKSPACE 및 시연용 POSTING 행을 포함하는지 확인합니다. 운영용 덤프·로그·대사 파일은 공개 저장소 외부에 보관합니다. [대사 SQL](../ops/oracle_reconcile.sql) · [Oracle Data Pump Export](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/oracle-data-pump-export-utility.html) · [Oracle Data Pump Import](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/datapump-import-utility.html)
