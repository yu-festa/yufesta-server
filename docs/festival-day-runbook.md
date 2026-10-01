# 축제 당일 대응표 (2026-10-02)

당일에는 배포하지 않는다(NFR-AV-01). 아래는 "무엇이 보이면 무엇을 누르는가"만 적었다. 판단은 미리 끝냈다.

## 시각표

| 시각 | 자동으로 일어나는 일 | 확인 |
|---|---|---|
| 15:50 | 1회차 마감 + 매칭 배치 | 로그 `회차 1 배치: 풀 N명, M쌍` |
| 16:00 | 1회차 발표, 2회차 오픈, 자동 공지 | 로그 `회차 1 발표: K건 이월, 회차 2 OPEN`, 홈이 바뀜 |
| 19:50 | 2회차 마감 + 배치 | 로그 `회차 2 배치` |
| 20:00 | 2회차 발표 | 로그 `회차 2 발표` |

스케줄러는 10초마다 돌고 두 태스크 중 하나만 실행한다. 늦은 쪽의 `MATCH_ROUND_INVALID_STATUS`는 정상이다.

로그 보기:
```bash
export AWS_PROFILE=yufesta
aws logs tail /ecs/yufesta-api --since 10m --follow --format short | grep -v RequestLoggingFilter
```

## 운영자 API 부르기 (Swagger가 꺼져 있을 때)

```bash
export AWS_PROFILE=yufesta
export JWT_SECRET=$(aws ssm get-parameter --name /yufesta/prod/JWT_SECRET --with-decryption --query 'Parameter.Value' --output text)
export T=$(./load/token.sh <OWNER 회원 id>)
api() { curl -s -X "$1" "https://api.yufesta.com$2" -H "Cookie: access_token=$T; XSRF-TOKEN=x" -H "X-XSRF-TOKEN: x" -H 'Content-Type: application/json' "${@:3}"; echo; }
api GET /api/v1/admin/match/rounds
```
CSRF 때문에 쓰기 요청은 `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더 값이 같아야 한다(위 함수가 둘 다 `x`로 보낸다).

## 증상별 대응

| 증상 | 먼저 볼 것 | 누를 것 |
|---|---|---|
| 15:50이 지났는데 홈이 "접수 중" | 로그에 `회차 1 배치`가 없다 | `api POST /api/v1/admin/match/rounds/1/close` (STAFF) |
| 배치 로그에 예외 | 예외 내용 | 원인이 데이터면 고친 뒤 `api POST /api/v1/admin/match/rounds/1/rerun` (발표 전만 가능) |
| 16:00이 지났는데 결과가 안 보임 | `api GET /api/v1/admin/match/rounds` 의 `published_at` | `api POST /api/v1/admin/match/rounds/1/publish` (OWNER) |
| 발표 전에 결과를 미리 보고 싶다 | - | `api GET /api/v1/admin/match/rounds/1/result` (풀·성비·쌍·평균 점수) |
| 홈이 안 바뀌는데 로그는 정상 | 새로고침하면 바뀌는가 | 바뀌면 SSE 문제. 사용자에게 새로고침 안내. 서버는 정상 |
| 응답이 느리다, 5xx | CloudWatch ECS CPU, ALB 5xx | `python3 load/cloudwatch.py table "<시작>" "<끝>"`. CPU 90% 이상이면 아래 "과부하" |
| 과부하(캐시 타임아웃 경고 다수, 태스크 unhealthy) | `aws ecs describe-services ...` runningCount | 발표 뒤 1~2분이면 저절로 가라앉는다. 계속되면 `terraform apply -var desired_count=3` |
| 태스크가 계속 죽는다 | 기동 로그 | 직전 이미지로 롤백: infra/README.md 3절 |
| Redis 장애 | 로그 `응답 캐시 read 실패` | 아무것도 안 해도 된다(fail-open, DB 경로). 느려질 뿐 |
| 특정 사용자가 상대를 신고 | - | 운영자 화면 또는 `/api/v1/admin/match/reports/**` |
| 응원 메시지에 부적절한 글 | - | 운영자 화면에서 숨김. 익명 키 일괄 숨김·차단 가능 |

## 매칭 배치가 "실패"했다는 것의 의미

배치는 트랜잭션 하나다. 중간에 예외가 나면 저장된 것이 없고 회차는 CLOSED로 남는다. 다시 `close`가 아니라 `rerun`을 부른다.
배치가 끝났는데 결과가 이상하면(쌍 0, 한쪽 성별 전원 미매칭) `result`로 풀 성비를 보고, 발표 전이면 `rerun`.
발표 뒤에는 되돌릴 수 없다(상대 인스타 ID가 이미 공개됨). 발표는 결과 확인 뒤 누른다: 자동 발표를 막고 싶으면 15:55에 `PATCH /rounds/1/times`로 publish_at을 뒤로 미룬다.

## 미리 확인된 것 (10/1)

- 남 300·여 100, 태그 0~3개 합성 풀로 배치: 위반 0 (`load/verify.sql`)
- 1,000명 고정 시드 불변식 테스트(`MatchRoundBatchInvariantTest`), 운영 리허설 두 번
- SSE: 연결 1,000개, 전이 0.15초 안에 전원 수신, 재연결 복구
- 읽기 600 req/s 안정, 예상 부하(70~200 req/s)의 3배
