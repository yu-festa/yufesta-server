#!/usr/bin/env bash
# 부하 테스트 중 앱 내부 지표를 5초 간격으로 CSV에 적는다.
# CloudWatch는 태스크 CPU·메모리, RDS, ALB까지만 보여 준다. "CPU는 낮은데 느리다"의 원인(커넥션 풀 대기 vs 스레드 포화 vs GC)은
# 이 지표로 갈린다. /actuator/metrics는 OWNER만 볼 수 있으므로 운영자 쿠키가 필요하다.
#
#   BASE_URL=https://api.yufesta.com ACCESS_TOKEN=<운영자 access_token 쿠키 값> ./load/collect-metrics.sh out.csv
#   (쿠키 값은 브라우저 개발자도구 > Application > Cookies > access_token 에서 복사)
set -euo pipefail

BASE_URL="${BASE_URL:-https://api.yufesta.com}"
OUT="${1:-metrics.csv}"
INTERVAL="${INTERVAL:-5}"
: "${ACCESS_TOKEN:?운영자 access_token 쿠키 값이 필요하다}"

metric() { # $1 = 지표 이름, $2 = 통계(VALUE|MAX|COUNT)
  curl -s -b "access_token=${ACCESS_TOKEN}" "${BASE_URL}/actuator/metrics/$1" \
    | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    print(next((m['value'] for m in d.get('measurements',[]) if m['statistic']=='${2:-VALUE}'), ''))
except Exception:
    print('')
"
}

# 시작 전에 한 번 확인한다. 토큰이 만료(2시간)됐는데 그냥 돌면 빈 값(,,,,)만 쌓여 측정을 통째로 날린다
PREFLIGHT="$(curl -s -o /dev/null -w '%{http_code}' -b "access_token=${ACCESS_TOKEN}" "${BASE_URL}/actuator/metrics/jvm.memory.used")"
if [ "$PREFLIGHT" != "200" ]; then
  echo "지표 조회 실패 (HTTP ${PREFLIGHT}). 401이면 토큰 만료, 403이면 OWNER 계정이 아니다." >&2
  echo "  export JWT_SECRET=\$(aws ssm get-parameter --name /yufesta/prod/JWT_SECRET --with-decryption --query 'Parameter.Value' --output text)" >&2
  echo "  export ACCESS_TOKEN=\$(./load/token.sh <회원 ID>)" >&2
  exit 1
fi

echo "time,heap_used_mb,cpu,hikari_active,hikari_pending,hikari_idle,tomcat_busy,tomcat_current,http_count,http_max_sec" > "$OUT"
echo "수집 시작 → $OUT (Ctrl+C로 종료)"

while true; do
  HEAP=$(metric 'jvm.memory.used?tag=area:heap' VALUE)
  CPU=$(metric 'process.cpu.usage' VALUE)
  HA=$(metric 'hikaricp.connections.active' VALUE)
  HP=$(metric 'hikaricp.connections.pending' VALUE)
  HI=$(metric 'hikaricp.connections.idle' VALUE)
  TB=$(metric 'tomcat.threads.busy' VALUE)
  TC=$(metric 'tomcat.threads.current' VALUE)
  HC=$(metric 'http.server.requests' COUNT)
  HM=$(metric 'http.server.requests' MAX)
  HEAP_MB=$(python3 -c "print(round(float('${HEAP:-0}')/1048576, 1) if '${HEAP}' else '')")
  echo "$(date +%H:%M:%S),${HEAP_MB},${CPU},${HA},${HP},${HI},${TB},${TC},${HC},${HM}" | tee -a "$OUT"
  sleep "$INTERVAL"
done
