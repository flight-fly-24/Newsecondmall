#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://127.0.0.1:8088}"
body="$(curl --fail --silent --show-error --max-time 15 "$base_url/api/products")"
printf '%s' "$body" | grep -q '"code"[[:space:]]*:[[:space:]]*0'
printf '[PASS] Product list\n'

if [ "${SMOKE_SKIP_FRONTEND:-false}" != "true" ]; then
  curl --fail --silent --show-error --max-time 15 "$base_url/" >/dev/null
  printf '[PASS] Frontend\n'
fi
printf 'Black-box smoke test passed against %s\n' "$base_url"
