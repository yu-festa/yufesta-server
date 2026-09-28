#!/usr/bin/env bash
# 요청 한 건이 DB를 몇 번 왕복하는지 센다. 로컬 전용(compose의 MySQL general log를 잠깐 켠다).
# 응답 시간만 봐서는 "DB를 몇 번 다녀왔는지"가 보이지 않는다. 캐시가 맞았는데도 트랜잭션 제어문이 나가던 것,
# 로그인 요청마다 회원을 다시 읽던 것 모두 이 방법으로 찾았다(README 2026-09-28).
#
#   ./load/db-roundtrips.sh /api/v1/match/summary                       # 비로그인
#
#   USER_ID=3048 ./load/db-roundtrips.sh /api/v1/match/summary          # 로그인(3048 = 이 DB에 있는 회원 ID)
#
# 로그인 토큰은 스크립트가 직접 만든다. 서명 비밀을 지금 떠 있는 앱 컨테이너에서 읽어 바로 쓰므로
# 셸에 남아 있던 다른 환경(운영)의 JWT_SECRET·ACCESS_TOKEN과 섞일 일이 없다. 비밀과 토큰은 출력하지 않는다.
#
# 먼저 한 번 불러 캐시를 채운 뒤 COUNT건을 연달아 보내고 건당 평균을 낸다.
# 회차 스케줄러가 10초마다 도는데, 그 한 번이 "transaction control 5 + select match_rounds 1"로 잡힌다.
# 건당 값이 아니라 합계 6~9건이 보이면 그것이다. 값이 튀면 한 번 더 돌린다.
set -euo pipefail

REQUEST_PATH="${1:?경로가 필요하다 (예: /api/v1/match/summary)}"
COUNT="${2:-20}"
# 대상은 로컬로 고정한다. k6 준비 단계에서 export한 BASE_URL(운영 주소)이 셸에 남아 있으면
# 요청은 운영으로 가고 general log는 로컬 것을 읽어, 아무 관계 없는 두 값을 비교하게 된다(실제로 겪었다)
TARGET_URL="${LOCAL_URL:-http://localhost:8080}"
case "$TARGET_URL" in
  http://localhost:*|http://127.0.0.1:*) ;;
  *) echo "로컬 전용이다. 대상이 로컬이 아니다: ${TARGET_URL}" >&2; exit 1 ;;
esac
DB_PASSWORD="${MYSQL_ROOT_PASSWORD:-yufesta}"

mysql_exec() {
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot "-p${DB_PASSWORD}" yufesta -e "$1" 2>/dev/null
}

call() {
  if [ -n "${ACCESS_TOKEN:-}" ]; then
    curl -s -o /dev/null -w '%{http_code}\n' -H "Cookie: access_token=${ACCESS_TOKEN}" "${TARGET_URL}${REQUEST_PATH}"
  else
    curl -s -o /dev/null -w '%{http_code}\n' "${TARGET_URL}${REQUEST_PATH}"
  fi
}

# 끝나면(실패해도) 로그를 반드시 끈다. 켜 둔 채로 두면 모든 쿼리가 테이블에 쌓인다
trap 'mysql_exec "SET GLOBAL general_log = '"'"'OFF'"'"';"' EXIT

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -n "${USER_ID:-}" ]; then
  ACCESS_TOKEN="$(JWT_SECRET="$(docker compose exec -T app printenv JWT_SECRET)" "${SCRIPT_DIR}/token.sh" "$USER_ID")"
else
  # 셸에 남아 있던 토큰을 모르고 쓰는 일을 막는다. 로그인 측정은 USER_ID로만 한다
  ACCESS_TOKEN=""
fi

# 토큰이 받아들여지지 않으면 서버는 조용히 비로그인으로 처리하고 요약은 그래도 200을 준다.
# 그대로 재면 비로그인 수치를 로그인 수치로 착각하게 되므로 먼저 확인하고, 안 되면 이유를 찾아 알려 준다
if [ -n "$ACCESS_TOKEN" ]; then
  LOGIN_STATUS="$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: access_token=${ACCESS_TOKEN}" "${TARGET_URL}/api/v1/auth/me")"
  if [ "$LOGIN_STATUS" != "200" ]; then
    echo "로그인 확인 실패: /api/v1/auth/me -> ${LOGIN_STATUS}" >&2
    echo "  회원 ${USER_ID} 존재: $(mysql_exec "SELECT IF(COUNT(*) > 0, '있음', '없음 (seed.sql을 다시 넣었다면 min_user_id가 바뀐다)') FROM users WHERE id = ${USER_ID};" | tail -1)" >&2
    echo "  앱 기동 상태: $(curl -s -o /dev/null -w '%{http_code}' "${TARGET_URL}/actuator/health") (200이 아니면 기동 중이다. 잠시 뒤 다시)" >&2
    exit 1
  fi
fi

echo "대상: ${TARGET_URL}${REQUEST_PATH}"
echo "캐시 채우기: $(call)"
mysql_exec "SET GLOBAL log_output = 'TABLE'; SET GLOBAL general_log = 'ON'; TRUNCATE mysql.general_log;"
STATUSES="$(for _ in $(seq 1 "$COUNT"); do call; done | sort | uniq -c | tr '\n' ' ')"
sleep 0.3
mysql_exec "SET GLOBAL general_log = 'OFF';"

if [ -n "${USER_ID:-}" ]; then WHO="로그인, 회원 ${USER_ID}"; else WHO="비로그인"; fi
echo "요청 ${COUNT}건 (${WHO}) 응답 코드: ${STATUSES}"
mysql_exec "
SELECT kind AS 'statement', n AS total, ROUND(n / ${COUNT}, 1) AS per_request FROM (
  SELECT CASE
           WHEN q LIKE 'set %' OR q IN ('commit', 'rollback') THEN 'transaction control'
           WHEN q LIKE 'select%' THEN CONCAT('select ', SUBSTRING_INDEX(SUBSTRING_INDEX(q, ' from ', -1), ' ', 1))
           ELSE LEFT(q, 40)
         END AS kind, COUNT(*) AS n
  FROM (SELECT LOWER(CONVERT(argument USING utf8mb4)) AS q FROM mysql.general_log WHERE command_type = 'Query') t
  WHERE q NOT LIKE '%general_log%' AND q NOT LIKE '%version_comment%'
  GROUP BY kind
  UNION ALL
  SELECT '== TOTAL ==', COUNT(*)
  FROM (SELECT CONVERT(argument USING utf8mb4) AS q FROM mysql.general_log WHERE command_type = 'Query') t
  WHERE q NOT LIKE '%general_log%' AND q NOT LIKE '%version_comment%'
) s ORDER BY (kind = '== TOTAL ==') DESC, n DESC;"
