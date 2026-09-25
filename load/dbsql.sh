#!/usr/bin/env bash
# SQL 파일을 운영 RDS에서 실행하고 결과를 그대로 출력한다.
# dbshell(ECS Exec)에 긴 SQL을 붙여넣다 실패하는 걸 피하려고, 일회성 Fargate 태스크에 파일 내용을 넘겨 실행하고
# CloudWatch 로그에서 결과를 읽어 온다. 태스크는 SQL이 끝나면 스스로 종료된다(비용 거의 0).
#
#   export AWS_PROFILE=yufesta
#   ./load/dbsql.sh load/status.sql
#   ./load/dbsql.sh load/seed.sql
#
# 대화형으로 직접 쿼리를 두드리고 싶으면 기존 방식(infra/README.md 3-1)을 쓴다.
set -euo pipefail

FILE="${1:?실행할 .sql 파일 경로}"
[ -f "$FILE" ] || { echo "파일이 없다: $FILE" >&2; exit 1; }

ROOT="$(git rev-parse --show-toplevel)"
CLUSTER="${CLUSTER:-yufesta-cluster}"
FAMILY="${FAMILY:-yufesta-dbshell}"

NET="$(terraform -chdir="${ROOT}/infra/terraform" output -raw dbshell_network)"
LOG_GROUP="$(terraform -chdir="${ROOT}/infra/terraform" output -raw log_group)"

OVERRIDES_FILE="$(mktemp -t yufesta-dbsql)"
trap 'rm -f "$OVERRIDES_FILE"' EXIT

# 컨테이너 명령을 파이썬으로 만들어 임시 파일에 둔다. SQL 안의 따옴표·개행·한글이 있어도 JSON이 깨지지 않고,
# heredoc(SQLEOF)으로 넘기므로 셸이 SQL을 해석하지 않는다. mysql -t는 결과를 표로 출력한다.
# (명령 치환 안에서 heredoc을 쓰면 macOS 기본 bash 3.2가 파싱하지 못해 파일로 우회한다)
python3 - "$FILE" > "$OVERRIDES_FILE" <<'PY'
import json, sys

sql = open(sys.argv[1], encoding='utf-8').read()
script = (
    'exec mysql -h "$DB_HOST" -u"$DB_USER" -p"$DB_PASSWORD" '
    '--default-character-set=utf8mb4 -t "$DB_NAME" <<' + "'SQLEOF'" + '\n'
    + sql + '\nSQLEOF\n'
)
json.dump({"containerOverrides": [{"name": "dbshell", "command": ["sh", "-c", script]}]}, sys.stdout)
PY

echo "▶ ${FILE} 실행 중… (태스크 시작에 20~40초)"
TASK="$(aws ecs run-task \
  --cluster "$CLUSTER" --task-definition "$FAMILY" --launch-type FARGATE \
  --propagate-tags TASK_DEFINITION --network-configuration "$NET" \
  --overrides "file://${OVERRIDES_FILE}" \
  --query 'tasks[0].taskArn' --output text)"
TASK_ID="${TASK##*/}"

aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$TASK"
EXIT_CODE="$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$TASK" \
  --query 'tasks[0].containers[0].exitCode' --output text)"

echo "── 결과 (task ${TASK_ID}, exit ${EXIT_CODE})"
OUT=""
for _ in 1 2 3 4 5 6; do
  OUT="$(aws logs get-log-events \
    --log-group-name "$LOG_GROUP" --log-stream-name "dbshell/dbshell/${TASK_ID}" \
    --limit 500 --query 'events[].message' --output text 2>/dev/null || true)"
  if [ -n "$OUT" ]; then break; fi
  sleep 5
done

if [ -n "$OUT" ]; then
  printf '%s\n' "$OUT"
else
  echo "(로그가 아직 없다. 잠시 뒤: aws logs tail ${LOG_GROUP} --filter-pattern ${TASK_ID})"
fi
if [ "$EXIT_CODE" != "0" ]; then
  echo "⚠ mysql이 오류로 끝났다. 위 출력을 확인할 것" >&2
fi
