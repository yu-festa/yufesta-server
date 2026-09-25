# 부하·정합성 검증

- `verify.sql` — 배치 정합성 불변식을 실제 MySQL에서 확인한다. 자동 테스트(`MatchRoundBatchInvariantTest`)와 같은 규칙이며,
  1절의 `violations`가 모두 0이어야 한다. 2절은 회차별 요약(풀·매칭·미매칭·쌍·평균 점수·이월·배치 지연)으로 측정 기록용이다.

- `dbsql.sh` — 아래 SQL 파일들을 운영 RDS에서 파일 그대로 실행하고 결과를 출력한다(붙여넣기 불필요).
- `sample.sql` — 매칭 결과를 눈으로 읽는다. 참가자 속성, 쌍별 점수 근거(공통 태그 수·같은 공연·나이대), 파트너 수 분포, 미매칭자.
  개인정보라 `instagram_id`는 뽑지 않는다.

```bash
# 로컬. -p와 비밀번호는 반드시 붙여 쓴다(-p yufesta 처럼 띄우면 yufesta가 DB 이름이 되고,
# 파일을 stdin으로 넣은 상태라 비밀번호 프롬프트를 읽지 못해 Access denied가 난다)
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -pyufesta yufesta < load/verify.sql
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -pyufesta yufesta < load/sample.sql

# 비밀번호를 바꿨다면 .env의 MYSQL_ROOT_PASSWORD 값을 -p 뒤에 붙인다
# 운영: infra/README.md 3-1의 dbshell로 붙은 mysql> 프롬프트에 파일 내용을 붙여넣는다
```

실행 시점: 배치(`close`) 직후, 발표(`publish`) 직후, 리허설 종료 후, 부하 테스트 종료 후.

눈으로 확인하는 또 다른 방법은 `MatchRoundBatchInvariantTest`의 "무작위 20명" 테스트다. 참가자 속성과 쌍별 점수 근거를 로그로 찍는다.

```bash
./gradlew test --tests '*MatchRoundBatchInvariantTest' -i | grep -A40 '── 참가자'
```

## 부하 테스트 (k6)

실사용자가 없는 기간에만, 운영 환경을 대상으로 돌린다. 로컬은 RDS·ALB·Fargate CPU 제한이 빠져 병목 위치를 못 잡는다.
도착률 기반(arrival-rate) 시나리오라 서버가 느려져도 부하가 줄지 않아 한계가 드러난다.

| 파일 | 유형 | 무엇을 보나 |
|---|---|---|
| `scenarios/smoke.js` | 연기 | 스크립트·토큰·엔드포인트가 동작하는지(항상 먼저) |
| `scenarios/read-mix.js` | 평균·장시간 | 당일 읽기 믹스. `RATE`·`DURATION`으로 load(300 req/s 10분)와 soak(150 req/s 30분) 겸용 |
| `scenarios/publish-spike.js` | 스파이크 | 발표 직후 1,000명 결과 조회 + 홈 폴링(NFR-PF-01) |
| `scenarios/stress.js` | 스트레스 | 300→600→1,200→2,400 req/s. 임계값이 처음 깨지는 지점 |
| `scenarios/breakpoint.js` | 한계점 | 결과 조회만 올려 한계 처리량. Redis 캐시 판단 근거 |
| `scenarios/write-apply.js` | 쓰기 | 마감 직전 신청 폭주. 유니크 제약·잠금 경합 |

### 준비

```bash
export AWS_PROFILE=yufesta
export BASE_URL=https://api.yufesta.com
export JWT_SECRET=$(aws ssm get-parameter --name /yufesta/prod/JWT_SECRET --with-decryption --query 'Parameter.Value' --output text)
```

비밀은 셸 변수로만 둔다(파일·git에 남기지 않는다). 터미널을 닫으면 사라진다.

### 세션으로 나눠 진행

한 번에 몰아서 하지 않는다. 세션마다 필요한 "회차 상태"만 맞으면 독립적으로 다시 돌릴 수 있고,
중간에 실패해도 그 세션만 반복하면 된다. 시작 전·후에 `status.sql`로 지금 상태를 확인한다.

SQL은 `dbsql.sh`로 **파일 그대로** 실행한다. 일회성 Fargate 태스크에 파일 내용을 넘겨 돌리고 결과를 CloudWatch 로그에서 받아오므로,
dbshell 프롬프트에 수십 줄을 붙여넣다 실패할 일이 없다(태스크는 끝나면 스스로 종료된다).

```bash
export AWS_PROFILE=yufesta
./load/dbsql.sh load/status.sql      # 지금 상태 (합성 회원 수, 회차, 세션별 준비 여부)
./load/dbsql.sh load/seed.sql        # 합성 회원 1,000명 + 신청
./load/dbsql.sh load/verify.sql      # 정합성 위반 0 확인
./load/dbsql.sh load/cleanup.sql     # 정리

# 로컬은 그대로 파이프
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -pyufesta yufesta < load/status.sql
```

직접 쿼리를 두드려야 할 때만 대화형 dbshell(`infra/README.md` 3-1)을 쓴다.

공통 환경 변수는 세션마다 다시 export 한다(터미널을 새로 열면 사라진다).

```bash
export AWS_PROFILE=yufesta
export BASE_URL=https://api.yufesta.com
export JWT_SECRET=$(aws ssm get-parameter --name /yufesta/prod/JWT_SECRET --with-decryption --query 'Parameter.Value' --output text)
export UID_FROM=<status.sql의 min_user_id>
K6="k6 run -e BASE_URL=$BASE_URL -e JWT_SECRET=$JWT_SECRET -e USER_ID_FROM=$UID_FROM -e USER_COUNT=1000"
```

| 세션 | 필요한 상태 | 하는 일 | 소요 | 실패하면 |
|---|---|---|---|---|
| **A. 준비·쓰기** | 회차 OPEN | `seed.sql` → `smoke.js` → `write-apply.js` | 5분 | A만 다시 |
| **B. 배치·발표** | A 완료, 회차 OPEN | `close` 시간 측정 → `verify.sql` → `publish` → `publish-spike.js` | 10분 | 회차 초기화 후 A부터 |
| **C. 평균 부하** | 시드 | `read-mix.js` (300 req/s 10분) | 10분 | C만 다시 |
| **D. 한계 탐색** | 시드, B 완료(결과 조회) | `stress.js` → `breakpoint.js` | 30분 | 해당 시나리오만 다시 |
| **E. 복원력** | 시드 | `read-mix.js` 도는 중 태스크 종료·RDS 장애 조치·스케줄러 확인 | 15분 | E만 다시 |
| **F. 장시간** | 시드 | `read-mix.js -e RATE=150 -e DURATION=30m` | 30분 | 생략 가능 |
| **G. 정리** | - | `cleanup.sql` → 회차 초기화 → `verify.sql` | 3분 | 반드시 실행 |

가장 값진 건 **A·B·C**(25분)다. 여기서 NFR-PF-01(발표 스파이크)과 NFR-PF-03(배치 시간), 평상시 여유를 얻는다.
D는 한계와 Redis 판단, E는 당일 장애 대응, F는 누수 확인용이라 시간이 날 때 따로 해도 된다.

**세션 A — 준비와 쓰기 부하** (회차가 OPEN이어야 한다)

```bash
# 1) 시드 → 출력된 min_user_id 메모
./load/dbsql.sh load/seed.sql
# 2) 스크립트·토큰 확인
$K6 load/scenarios/smoke.js
# 3) 마감 직전 신청 폭주(유니크 제약·잠금 경합). 대부분 409(이미 신청)가 정상이다
$K6 load/scenarios/write-apply.js
```

**세션 B — 배치와 발표 스파이크** (여기서 회차가 PUBLISHED로 바뀐다)

```bash
# 1) 지표 수집 시작(다른 터미널). 운영자 access_token 쿠키 값이 필요하다
BASE_URL=$BASE_URL ACCESS_TOKEN=<쿠키> ./load/collect-metrics.sh sessionB.csv
# 2) 운영자 Swagger 또는 curl로 마감 → 응답까지 걸린 시간과 로그의 "배치: 풀 N명, M쌍" 기록(NFR-PF-03)
#    POST /api/v1/admin/match/rounds/{id}/close
# 3) ./load/dbsql.sh load/verify.sql 로 위반 0 확인
# 4) 발표 후 곧바로 스파이크(발표 직후 1,000명 결과 조회, NFR-PF-01)
#    POST /api/v1/admin/match/rounds/{id}/publish
ulimit -n 10240
$K6 load/scenarios/publish-spike.js
```

**세션 C~F**

```bash
$K6 load/scenarios/read-mix.js                          # C. 평균 300 req/s 10분
$K6 load/scenarios/stress.js                            # D. 300→2,400 req/s 단계
$K6 load/scenarios/breakpoint.js                        # D. 결과 조회 한계점(임계값 깨지면 자동 중단)
$K6 -e RATE=150 -e DURATION=30m load/scenarios/read-mix.js   # F. soak
```

**세션 E — 복원력** (C의 `read-mix.js`가 도는 중에 실행)

```bash
# ① 태스크 1개 강제 종료 → k6 오류율·회복 시간, ALB HealthyHostCount
aws ecs list-tasks --cluster yufesta-cluster --service-name yufesta-api --query 'taskArns[0]' --output text
aws ecs stop-task --cluster yufesta-cluster --task <arn> --reason "resilience test" >/dev/null

# ② RDS 장애 조치(Multi-AZ) → 오류 창 길이. 1~2분 걸린다
aws rds reboot-db-instance --db-instance-identifier yufesta-mysql --force-failover >/dev/null

# ③ 스케줄러가 두 태스크에서 한 번만 전이하는지
aws logs tail /ecs/yufesta-api --follow --filter-pattern "회차"
```

**세션 G — 정리** (그날 작업을 끝낼 때 반드시)

```bash
# dbshell에서
#   cleanup.sql        합성 회원·신청·결과 삭제
#   cleanup.sql 하단 주석 블록  회차를 SCHEDULED로 되돌리기(실제 신청도 지우므로 내용 확인 후)
#   verify.sql         위반 0 확인
#   status.sql         합성 데이터 0, 회차 SCHEDULED 확인
```

세션을 나눠 여러 날에 걸쳐 할 때는 **그날 세션이 끝나면 G까지 하고 마친다.** 합성 회원을 남겨 두면 프론트 팀원이 보는 신청자 수가 부풀고,
회차가 PUBLISHED로 남으면 다음 세션 A(쓰기)를 못 한다.

### 주의사항 (실행 전 확인)

- **배포가 먼저**다. `/actuator/metrics`는 이 PR이 배포된 뒤에만 열린다(그전에는 404·403).
- **부하 중 배포·`terraform apply` 금지.** 태스크가 교체되면 측정이 섞인다.
- **파일 디스크립터**: 1,000 VU 이상을 쓰기 전에 `ulimit -n 10240`. 안 하면 "too many open files"가 난다.
- **클라이언트가 먼저 막힐 수 있다.** 2,400 req/s는 노트북의 TLS 처리와 회선을 먼저 소모한다.
  `http_req_connecting`·`tls_handshaking`이 커지거나 k6 프로세스 CPU가 100%면 서버가 아니라 이쪽 한계다. 그때 수치는 서버 판단에 쓰지 않는다.
- **알람 메일이 온다.** 스트레스 구간의 5xx와 태스크 종료 시 HealthyHostCount 알람이 SNS로 발송된다. 테스트 때문이니 놀라지 않아도 된다.
- **비용**: ALB LCU와 데이터 전송으로 1.5시간에 $1~2 수준.
- **토큰 만료는 걱정하지 않아도 된다.** 매 반복마다 새로 서명하며 유효 기간은 2시간이다. 다만 로컬 시계가 서버보다 1분 이상 빠르면 `exp` 검증이 어긋날 수 있으니 시계 자동 동기화는 켜 둔다.
- **회차 초기화 뒤 스케줄러가 1회차를 즉시 다시 연다**(`open_at`이 9/25 00:00로 이미 지났기 때문). 정상이다.

### 함께 볼 지표

- CloudWatch: ALB `TargetResponseTime`(p95)·`HTTPCode_Target_5XX_Count`·`RequestCount`, ECS `CPUUtilization`·`MemoryUtilization`, RDS `CPUUtilization`·`DatabaseConnections`
- `collect-metrics.sh` CSV: 힙 사용량, 프로세스 CPU, Hikari active/pending/idle, Tomcat busy/current, 요청 수·최대 응답 시간

### 병목 판독

| 증상 | 원인 | 조치 |
|---|---|---|
| ECS CPU 80%+ & p95 상승 | 앱 CPU(0.5 vCPU) 한계 | 태스크 수↑ 또는 1 vCPU |
| RDS CPU 80%+ 또는 `DatabaseConnections` 정체 | DB 병목 | 결과 조회 Redis 프리워밍, 쿼리 점검 |
| CPU 낮은데 p95 높고 `hikari_pending` > 0 | 커넥션 풀 대기 | 풀 크기↑ 또는 캐시 |
| `tomcat_busy`가 최대(200)에 붙음 | 스레드 포화 | 요청당 시간 단축(캐시), 태스크↑ |
| soak 중 힙 우상향 | 누수 | 힙 덤프 분석 |
| `/match/summary`가 트래픽의 대부분 | 폴링 자체가 부하 | SSE 전환 근거 |

### 결과 기록

| 날짜 | 시나리오 | 부하 | p50 / p95 / p99 | 오류율 | ECS CPU | RDS CPU / 연결 | Hikari pending | 판단 |
|---|---|---|---|---|---|---|---|---|
| (측정 후 채움) | | | | | | | | |

## 배치 정합성 측정값 (2026-09-25, 로컬 H2 · 고정 시드)

`MatchRoundBatchInvariantTest` 실행 결과다. 성비별 미매칭은 `MatchingEngineCapacityTest`에서 잰다.

| 시나리오 | 풀 | 쌍 | 1차 / 2차 | 미매칭 | 평균 점수 | 배치 소요 |
|---|---|---|---|---|---|---|
| 1회차 550남 : 450여, 차단 30쌍 | 1,000 | 550 | 450 / 100 | 0 | 2.45 | 151ms |
| 1회차 400남 : 100여 | 500 | 300 | 100 / 200 | 100 | - | - |
| 2회차(이월 100 + 재참여 40) | 140 | 60 | - | - | - | - |

- NFR-PF-03(500명 30초) 대비 1,000명 151ms로 한참 여유. 실제 MySQL에서는 저장 I/O가 더 걸리므로 운영 측정으로 확정한다.
- 미매칭은 성비가 N:1(기본 3:1)을 넘을 때의 다수 측 초과분과 제외 쌍(차단·이전 회차)에 막힌 경우뿐이다.

| 성비(총 600명) | 쌍 | 미매칭 |
|---|---|---|
| 300 : 300 (1:1) | 300 | 0 |
| 400 : 200 (2:1) | 400 | 0 |
| 450 : 150 (3:1) | 450 | 0 |
| 480 : 120 (4:1) | 360 | 120 |
| 540 : 60 (9:1) | 180 | 360 |

즉 성비가 3:1 이내면 전원 매칭이고 이월은 거의 없다. 4:1을 넘기면 `match.max_partners`를 올려 수용량(소수 × N)을 늘릴 수 있다.
