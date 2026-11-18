#!/usr/bin/env sh
set -eu
HOST="${1:-http://127.0.0.1:8087}"
printf '%s\n' 'Checking /health ...'
curl -fsS "$HOST/health" | grep -q '"ok":true'
printf '%s\n' 'Checking unauthorized feed is rejected ...'
code="$(curl -s -o /dev/null -w '%{http_code}' "$HOST/m/feed")"
[ "$code" = 401 ]
printf '%s\n' 'Smoke test PASS. Live Supabase integration needs real credentials and 2 test accounts.'
