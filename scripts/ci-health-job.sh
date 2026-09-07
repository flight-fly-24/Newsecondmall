#!/usr/bin/env bash
set -euo pipefail

write_status() {
  mkdir -p ci-artifacts
  printf '%s\n' "$1" > ci-artifacts/health-status
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=ci-cd-common.sh
source "$repo_root/scripts/ci-cd-common.sh"
ci_enter_repo
ci_release_env

if ! command -v ssh >/dev/null 2>&1 || ! command -v scp >/dev/null 2>&1; then
  echo "当前执行器没有 ssh/scp" >&2
  write_status 2
  exit 0
fi

mkdir -p ci-artifacts
set +e
bash "$repo_root/scripts/ci-k8s-health.sh" 2>&1 | tee ci-artifacts/kubernetes-health.log
status=${PIPESTATUS[0]}
set -e
write_status "$status"
exit 0
