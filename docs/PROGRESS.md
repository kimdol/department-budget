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
