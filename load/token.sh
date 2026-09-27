#!/usr/bin/env bash
# 운영자 access_token을 직접 서명해 출력한다.
# 지표 수집(collect-metrics.sh)에 쓰는 쿠키는 2시간이면 만료되는데, 그때마다 브라우저에서 복사하는 대신 여기서 만든다.
# 서버(JwtTokenProvider)와 같은 HS256·uid·exp 형식이라 그대로 통한다. JWT_SECRET이 필요하다.
#
#   export JWT_SECRET=$(aws ssm get-parameter --name /yufesta/prod/JWT_SECRET --with-decryption --query 'Parameter.Value' --output text)
#   export ACCESS_TOKEN=$(./load/token.sh 3)     # 3 = 내 회원 ID(OWNER)
#
# 회원 ID는 운영자 API 응답 헤더의 x-request-id로 로그를 찾으면 "user=3"처럼 찍혀 있다.
set -euo pipefail

USER_ID="${1:?회원 ID가 필요하다 (예: ./load/token.sh 3)}"
: "${JWT_SECRET:?JWT_SECRET 환경변수가 필요하다}"
TTL="${TTL:-7200}"

python3 - "$USER_ID" "$TTL" <<'PY'
import base64, hmac, hashlib, json, os, sys, time

secret = os.environ['JWT_SECRET'].encode()
uid, ttl = int(sys.argv[1]), int(sys.argv[2])
now = int(time.time())
b64 = lambda raw: base64.urlsafe_b64encode(raw).decode().rstrip('=')

header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(',', ':')).encode())
payload = b64(json.dumps({"iat": now, "exp": now + ttl, "uid": uid}, separators=(',', ':')).encode())
signature = b64(hmac.new(secret, f"{header}.{payload}".encode(), hashlib.sha256).digest())
print(f"{header}.{payload}.{signature}")
PY
