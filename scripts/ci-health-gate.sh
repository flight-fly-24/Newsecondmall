#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=ci-cd-common.sh
source "$repo_root/scripts/ci-cd-common.sh"
ci_enter_repo
status="$(tr -d '[:space:]' < ci-artifacts/health-status)"
echo "health status: ${status}"
exit "${status}"
