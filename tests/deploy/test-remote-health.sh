#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT
mkdir -p "$test_root/bin"

cat > "$test_root/bin/kubectl" <<'EOF'
#!/usr/bin/env bash
set -e
args="$*"
if [[ "$args" == *"get deployment "*" -o jsonpath="* ]]; then
  if [[ "$args" == *readyReplicas* ]]; then
    printf '1'
    exit 0
  fi
  if [[ "$args" == *spec.replicas* ]]; then
    printf '1'
    exit 0
  fi
  if [[ "$args" == *songguo.dev/image-tag* ]] || [[ "$args" == *annotations.songguo* ]]; then
    printf 'release-27-abc12345'
    exit 0
  fi
  if [[ "$args" == *"containers[0].image"* ]]; then
    if [[ "$args" == *"get deployment frontend"* ]]; then
      printf 'swr.cn-north-4.myhuaweicloud.com/songguo/shop-frontend:release-27-abc12345'
    elif [[ "$args" == *"get deployment trade-service"* ]]; then
      printf 'swr.cn-north-4.myhuaweicloud.com/songguo/trade-service:release-27-abc12345'
    elif [[ "$args" == *"get deployment interaction-service"* ]]; then
      printf 'swr.cn-north-4.myhuaweicloud.com/songguo/interaction-service:release-27-abc12345'
    elif [[ "$args" == *"get deployment user-service"* ]]; then
      printf 'swr.cn-north-4.myhuaweicloud.com/songguo/shop-user-service:release-27-abc12345'
    elif [[ "$args" == *"get deployment catalog-service"* ]]; then
      printf 'swr.cn-north-4.myhuaweicloud.com/songguo/shop-catalog-service:release-27-abc12345'
    else
      printf 'swr.cn-north-4.myhuaweicloud.com/songguo/shop-backend:release-27-abc12345'
    fi
    exit 0
  fi
fi
if [[ "$args" == *"exec "* ]]; then
  echo "  HTTP/1.1 401 " >&2
  exit 1
fi
exit 0
EOF
cat > "$test_root/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -e
# Support curl -w '%{http_code}' used by remote-health.sh
while [[ $# -gt 0 ]]; do
  case "$1" in
    -w)
      printf '%s' "200"
      exit 0
      ;;
    --write-out)
      printf '%s' "200"
      exit 0
      ;;
  esac
  shift
done
exit 0
EOF
chmod +x "$test_root/bin/kubectl" "$test_root/bin/curl"
export PATH="$test_root/bin:$PATH"

bash "$repo_root/ops/remote-health.sh" http://127.0.0.1 release-27-abc12345 "$test_root/health-out"
[[ -f "$test_root/health-out/health.log" ]]
grep -q "Health check succeeded" "$test_root/health-out/health.log"

echo "Remote health contract tests: OK"
