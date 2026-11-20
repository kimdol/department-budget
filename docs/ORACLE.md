# Oracle 설치와 원장 복원

## 최초 설치

업무 스키마와 Spring Batch 메타데이터는 같은 Oracle 사용자 소유다. Oracle PDB에 데이터 테이블스페이스 할당량과 CREATE SESSION, CREATE TABLE, CREATE SEQUENCE 권한이 있는 빈 사용자를 준비한다. 개발·시험·공개 배포는 각각 다른 사용자로 접속한다. 아래 명령은 저장소 루트 기준이다. BUDGET_APP과 BUDGET_PDB는 준비한 사용자와 접속 별칭으로 바꾸며 비밀번호는 프롬프트에 입력한다.

```text
python ops/oracle_install.py target/department-budget-0.1.0.war target/install.sql
sqlplus -L BUDGET_APP@BUDGET_PDB @target/install.sql
```

[설치 파일 생성기](../ops/oracle_install.py)는 배포할 WAR에 포함된 V001 이후의 업무 SQL과 같은 WAR의 Spring Batch Core에서 추출한 Oracle 전용 SQL을 묶는다. 실제 WAR에서 V001~V009, Spring Batch 5.2.5의 테이블 정의 및 시퀀스 3개를 추출했다. CI에서는 Spring의 SQL 초기화로 실제 Oracle 설치를 검증했고 Data Pump로 별도 스키마 복원에 성공했다. 생성기가 묶은 단일 SQL 파일 자체의 실행은 별도 검증 대상이다. [복원 결과](https://github.com/kimdol/department-budget/actions/runs/44764720608)

## 기존 버전에서 올리기

현재 배포 리비전과 적용한 마지막 V번호를 확인하고 아래 절차로 백업한다. 요청 및 배치 실행을 중지한 뒤 새 WAR에 추가된 V번호만 순서대로 SQL*Plus에서 실행한다. 적용 파일명, 체크섬과 결과를 배포 기록에 보관한다. Spring Batch 라이브러리 버전도 변경됐다면 해당 버전의 Oracle 메타데이터 마이그레이션을 함께 검토한다. Oracle DDL은 암묵적 커밋을 수행하므로 중간 실패 시 마지막 성공 문장과 실제 제약·열 상태를 대조한다. 최초 설치 스크립트는 기존 객체가 하나라도 있으면 작업을 중단한다.

## 백업과 별도 스키마 복원

GitHub Actions의 Verify Oracle Ledger 작업은 로컬·Oracle 계약 시험 이름이 일치하고 모두 실행됐는지 검사한다. 이어서 원본 스키마를 내보내 같은 시험 PDB의 별도 사용자 BUDGET_RESTORE에 가져온다. 공간·전표·마감 잔액·비목 행의 존재와 원본·복원의 전체 대사 결과 일치를 확인한 뒤 결과와 SHA-256을 보관한다. [CI 복원 절차](../ops/ci_oracle_restore.sh) · [시험 실행 확인](../ops/check_oracle_contract.py). 별도 인스턴스 재해 복구는 추가 검증 대상이다.

아래는 자체 관리 Oracle PDB에서 DBA가 Data Pump 디렉터리 BUDGET_BACKUP을 만들고 읽기·쓰기 권한과 서버 디스크 공간을 제공한 경우의 절차다. 같은 Oracle 버전·문자 집합의 별도 검증 PDB에 비어 있는 대상 스키마를 준비한다. 같은 서비스로 전달할 외부 연계와 예약 작업을 복원 환경에서 비활성화한다. Autonomous Database의 객체 스토리지 내보내기와 복구는 해당 서비스 운영 절차가 필요하다.

1. 원본 앱과 배치·연계 쓰기를 멈추고 처리 중 트랜잭션이 끝나면 대사 파일을 만든다. 서버에서 내보내기가 끝날 때까지 쓰기 중지를 유지한다.
2. 원본에서 아래처럼 스키마를 내보낸다. 운영 때는 시점별로 고유한 덤프·로그 이름을 사용한다. 덤프는 DB 서버의 디렉터리에 생긴다.
3. 덤프를 검증 PDB에서 접근할 수 있는 별도 Data Pump 디렉터리로 옮기고 파일 체크섬을 대조한다. 복원 작업은 대상 스키마로 허가받은 계정으로 실행한다.
4. 두 Data Pump 로그에서 오류를 확인하고 대상에 같은 대사 SQL을 실행한다. 같은 SQL*Plus 버전과 인코딩으로 출력 파일을 비교한다. 전표별 귀속 월·금액·원전표 참조, 공간별 약정·집행 합계 및 마감 스냅샷의 차이가 없어야 한다. 이후 복원 DB에 연결한 앱에서 원신청을 조회한다.

```text
sqlplus -L -S BUDGET_APP@BUDGET_PDB @ops/oracle_reconcile.sql > before.txt
expdp BUDGET_APP@BUDGET_PDB SCHEMAS=BUDGET_APP DIRECTORY=BUDGET_BACKUP DUMPFILE=budget_backup.dmp LOGFILE=budget_export.log
impdp BUDGET_RESTORE@VERIFY_PDB REMAP_SCHEMA=BUDGET_APP:BUDGET_RESTORE DIRECTORY=BUDGET_BACKUP DUMPFILE=budget_backup.dmp LOGFILE=budget_import.log
sqlplus -L -S BUDGET_RESTORE@VERIFY_PDB @ops/oracle_reconcile.sql > restored.txt
git diff --no-index -- before.txt restored.txt
```

각 명령의 종료 코드를 확인하고 두 파일 모두 WORKSPACE 및 시연용 POSTING 행을 포함하는지 확인한다. 운영용 덤프·로그·대사 파일은 공개 저장소 외부에 보관한다. [대사 SQL](../ops/oracle_reconcile.sql) · [Oracle Data Pump Export](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/oracle-data-pump-export-utility.html) · [Oracle Data Pump Import](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/datapump-import-utility.html)
