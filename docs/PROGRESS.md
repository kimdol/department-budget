# 인수 현황

| 구분 | 업무 | 인수 증거 |
| --- | --- | --- |
| 필수 | 신청·약정·실비 정산·중복 요청 | Oracle 전표와 브라우저에서 같은 신청번호 대조 예정 |
| 필수 | 수식·규정 변경·재현 | 정률·상한·출장 한도 계산과 과거 입력 회귀 시험 |
| 필수 | 증빙 보완·증액·월마감·포털 연계 | 상태 전이 및 실패 재처리 시험 예정 |
| 필수 | 권한·방문자 격리·HTTPS | 독립 브라우저 두 개로 확인 예정 |
| 심화 | 측정·재시작·복원 | 실제 데이터베이스 실행과 조건별 원자료 필요 |
| 선택 | 상용 UI·리포팅 전환 | 제품 환경 확보 후 진행 |

## 외부 환경

공개 Oracle·Java 호스팅 계정은 아직 없다. 배포 후보는 OCI Always Free 컴퓨트와 Autonomous Database다. 무료 자원 할당이 가능한 리전·규격·사용량에 한해 예상 인프라 비용은 월 0원이다. 할당 용량이 부족할 수 있고 도메인은 별도다. DB는 7일 비활동 시 데이터가 보존된 상태로 정지되며 장기간 비활동 시 회수될 수 있다. 콘솔에서 재시작하고 별도 백업을 복원 검증한다. 유료 전환에는 사전 승인이 필요하다. [Oracle 무료 자원](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) · [DB 중지와 재시작](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/autonomous-always-free.html)

## 최근 검증과 재개 지점

Java 21에서 Maven package 성공, 수식 시험 11건 통과, Oracle 시험 2건은 빈 Oracle 스키마 미제공으로 건너뛰었다. JSP와 서버 API의 기본 신청·승인·정산 연결은 0030045에서 패키징했고 브라우저 실거래는 미검증이다. 생성된 target/classes/application.properties의 Windows 읽기 전용 속성으로 재빌드가 중단된 경우 생성물 속성 해제 후 동일 package 명령이 통과했다.

공개 저장소에는 수식 커밋 5e5c8e0까지 반영됐다. 원장 및 UI 커밋은 로컬 main에 있다. 워크플로를 포함한 push에 GitHub가 workflow 권한을 요구한다. 권한 갱신 후 git push -u origin main을 실행하고 Verify Oracle Ledger의 실제 결과를 확인한다. 로컬 재검증은 JDK 21, Maven 3.9에서 mvn test 및 mvn package로 진행한다. 다음 구현은 신청 생성의 요청 키 중복 방지, 제출 버전별 규정 저장·발행과 증액·보완 경로다. 외부 데모 URL, 실측 성능, 백업 복원 결과는 아직 없다.
