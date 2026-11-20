# 그누보드5에서 부서 정산 조회

사내 포털 회원이 첫 화면의 **부서 승인·정산 조회**에서 자기 계정에 배정된 부서의 수신 결과를 확인한다. 예산 시스템이 정산을 발행하면 포털 MySQL에는 해당 신청의 상태·차수·금액이 남는다. 조회 화면은 이 자료를 읽으며, 그누보드 회원 ID는 로그인 세션에서 가져온다. 첫 화면은 최대 50건을 표시하고 신청번호 검색은 전체 열람 범위에서 수행한다.

## 설치

기준 소스는 (주)에스아이알소프트의 [그누보드5 v5.6.54](https://github.com/gnuboard/gnuboard5/releases/tag/v5.6.54)다. 원본의 저작권·라이선스는 [태그의 LICENSE.txt](https://github.com/gnuboard/gnuboard5/blob/v5.6.54/LICENSE.txt)에 있다. 저장소에는 추가한 확장 파일만 배포한다.

1. 시험용 그누보드 설치의 루트에 [extend와 plugin 파일](../gnuboard)을 같은 디렉터리 구조로 복사한다. 기본 테마는 페이지 끝에서 `tail_sub` 이벤트를 호출한다.
2. 포털 수신기의 MySQL 스키마에 [V002](../portal/mysql/V002__gnuboard_reader.sql)를 적용한다. 관리자가 포털 회원 ID와 열람할 합성 부서의 `workspace_id`를 `budget_portal_reader`에 등록한다. 회원 한 명에게 여러 부서를 배정할 수 있다.
3. 포털 PHP 환경에 `PORTAL_MYSQL_DSN`, `PORTAL_READER_USER`, `PORTAL_READER_PASSWORD`를 설정한다. 조회 계정에는 `budget_portal_reader`와 `budget_claim_summary` 두 테이블의 SELECT 권한을 부여한다.
4. 등록된 회원으로 접속해 신청번호를 검색한다. 재무 화면에서 정산된 교육비라면 수신 차수 2, 정산 확정, 180,000원이 표시된다. 다른 회원에게 같은 신청번호로 조회를 요청해 부서 열람 범위도 확인한다.

## 재현

저장소 루트에서 PHP의 PDO MySQL 확장과 MySQL 8.4 서버·클라이언트를 준비하고 실행한다.

```sh
git clone --depth 1 --branch v5.6.54 --filter=blob:none --sparse https://github.com/gnuboard/gnuboard5.git target/gnuboard5
git -C target/gnuboard5 sparse-checkout add lib
python ops/test_portal.py --gnuboard
```

시험은 원본 커밋을 확인하고 공식 훅 디스패처에 확장을 등록한 뒤 비회원·회원 메뉴를 검사한다. 실제 PHP 수신기에서 정산한 MySQL 데이터를 읽기 전용 계정으로 조회하고, 부서 매핑이 없는 회원에게 목록과 신청번호 검색 결과가 비어 있는지 확인한다. 이 시험에서 로그인 신원은 합성 입력으로 제공한다. 설치된 포털에서의 로그인·로그아웃과 테마 화면은 별도 검증 항목이다. [회귀 시험](../ops/test_gnuboard.php) · [조회 쿼리](../gnuboard/plugin/budget/report.php)
