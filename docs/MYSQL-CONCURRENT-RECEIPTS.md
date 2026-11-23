# MySQL 승인 이벤트 동시 수신

동일한 승인 이벤트를 두 HTTP 요청으로 겹쳐 보냈습니다. 두 요청은 기존 PHP 수신기인 `receive.php`를 실행하고 같은 MySQL 8.4.13 InnoDB에 연결했습니다. 승인 금액은 200,000원이었습니다. 두 요청이 끝난 뒤 수신 이력과 포털 요약은 각 1건이었습니다. [수신 코드](../portal/receive.php) · [MySQL 스키마](../portal/mysql/V001__projection.sql) · [측정 원자료](measurements/mysql-concurrent-receipts.json)

## 실행

Python 3.10 이상, MySQL 8.4의 `mysql`·`mysqld`, PDO MySQL 확장이 있는 PHP를 준비하고 각 실행 파일을 PATH에 둡니다. 저장소 루트에서 다음 명령을 실행합니다.

```sh
python ops/test_portal.py --concurrency
```

시험은 임시 디렉터리에 실제 MySQL을 초기화하고, 두 루프백 포트에서 같은 PHP 수신 파일을 실행합니다. PHP 내장 서버 한 개는 요청을 직렬로 처리할 수 있으므로 프로세스를 두 개 사용했습니다. MySQL과 PHP의 포트, 시험 계정과 인증 토큰은 실행마다 별도로 만듭니다. 종료 시 임시 서버를 내립니다. [시험 환경](../ops/test_portal.py) · [PHP 내장 서버 동작](https://www.php.net/manual/en/features.commandline.webserver.php)

## 한 번만 반영되는 과정

| 순서 | 첫 요청 | 두 번째 요청 |
| --- | --- | --- |
| 1 | 트랜잭션을 열고 `event_id`로 수신 이력을 INSERT합니다. 포털 요약 INSERT 직전에 시험용 트리거가 이 연결을 잠시 멈춥니다. | 같은 이벤트 ID와 같은 내용으로 수신 이력 INSERT를 시도합니다. |
| 2 | `budget_event_receipt.PRIMARY`의 해당 레코드에 배타 잠금 `X,REC_NOT_GAP`을 보유합니다. | 기본 키가 이미 사용됐는지 확인하는 중 공유 잠금 `S,REC_NOT_GAP`을 기다립니다. 다른 읽기 세션에서 확인한 커밋된 수신 이력과 요약은 둘 다 0건입니다. |
| 3 | 대기가 관측되면 시험용 잠금을 풀어 줍니다. 요약을 INSERT하고 두 행을 한 트랜잭션으로 COMMIT한 뒤 HTTP 204를 반환합니다. | 첫 요청이 커밋한 뒤 중복 키 오류 1062를 받습니다. 자신의 트랜잭션을 ROLLBACK한 뒤 이미 저장된 내용 해시를 읽습니다. 같은 해시면 HTTP 204를 반환합니다. 이 경로는 요약 INSERT까지 가지 않습니다. |

테스트용 트리거는 첫 요청을 대기 상태로 재현하기 위해 임시 DB에만 설치합니다. 실제 경쟁은 두 번째 요청이 수신 이력의 기본 키를 INSERT할 때 발생합니다. 대기 중에는 `performance_schema.data_lock_waits`와 `data_locks`로 테이블·인덱스·잠금 모드를 확인하고, `INNODB_TRX`에서 대기 중인 INSERT와 각 트랜잭션의 MySQL 연결 ID를 확인합니다. [관측 쿼리와 검증 코드](../ops/test_portal_concurrency.py) · [MySQL 잠금 대기 표](https://dev.mysql.com/doc/refman/8.4/en/performance-schema-data-lock-waits-table.html)

## 검증 결과

Windows x64에서 Python 3.10.4·PHP 8.4.28·MySQL 8.4.13으로 세 번 실행했습니다. 각 회차는 새로운 DB와 합성 이벤트를 사용했습니다. 아래 결과는 세 회차에서 모두 같았습니다.

| 검사 | 실제 결과 |
| --- | --- |
| 같은 ID와 같은 금액으로 겹쳐 전송 | 두 요청 모두 HTTP 204 |
| 같은 ID로 금액을 199,999원으로 바꿔 재전송 | HTTP 409 |
| 최종 수신 이력과 포털 요약 | 각각 1건 |
| 남은 포털 요약 | 차수 1, `APPROVAL`, 200,000원 |
| 대기한 곳 | `budget_event_receipt`의 `PRIMARY` 인덱스, 같은 이벤트 ID의 레코드 |

첫 관측 쿼리는 `data_lock_waits`의 스레드 ID를 기준으로 첫 요청을 식별했습니다. 이 환경에서 대기·차단 두 스레드 필드가 모두 54로 표시돼 대기 쌍을 놓쳤습니다. 엔진 트랜잭션 ID 두 개를 `INNODB_TRX.trx_mysql_thread_id`와 연결하니 대기 연결 18과 차단 연결 16이 확인됐습니다. 차단 연결은 트리거에서 기다리던 첫 요청의 연결 ID와 일치했습니다. 원자료에는 두 종류의 식별자를 모두 남겼습니다. [측정 원자료](measurements/mysql-concurrent-receipts.json) · [INNODB_TRX 열 정의](https://dev.mysql.com/doc/refman/8.4/en/information-schema-innodb-trx-table.html)

`secondSubmissionToObservationMs`는 두 번째 요청을 실행기에 넘긴 뒤 관측 쿼리에서 대기를 확인하기까지 걸린 시간입니다. 세 회차에서 각각 47.0ms, 47.0ms, 31.0ms였습니다. 수치에는 시험 실행기의 스케줄링과 관측 쿼리 실행 시간이 포함됩니다. 잠금이 풀릴 때까지의 시간과 HTTP 응답 시간은 이 시험의 측정 범위 밖입니다.
