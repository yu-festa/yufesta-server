# 부하·정합성 검증

- `verify.sql` — 배치 정합성 불변식을 실제 MySQL에서 확인한다. 자동 테스트(`MatchRoundBatchInvariantTest`)와 같은 규칙이며,
  1절의 `violations`가 모두 0이어야 한다. 2절은 회차별 요약(풀·매칭·미매칭·쌍·평균 점수·이월·배치 지연)으로 측정 기록용이다.

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
