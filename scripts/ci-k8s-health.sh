#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=ci-cd-common.sh
source "$repo_root/scripts/ci-cd-common.sh"
ci_enter_repo
ci_release_env
# shellcheck source=ci-ecs-ssh.sh
source "$repo_root/scripts/ci-ecs-ssh.sh"

image_tag="$IMAGE_TAG"
health_base_url="${HEALTHCHECK_BASE_URL:-http://127.0.0.1}"
artifact_root="$CI_ARTIFACT_DIR"
remote_incoming="/tmp/soft-shop-health-${image_tag}"

[[ "$image_tag" =~ ^release-[a-zA-Z0-9._-]+$ ]] || { echo "Invalid IMAGE_TAG" >&2; exit 2; }
command -v ssh >/dev/null 2>&1 || { echo "ssh is required on this executor" >&2; exit 2; }
command -v scp >/dev/null 2>&1 || { echo "scp is required on this executor" >&2; exit 2; }

ecs_ssh_setup
mkdir -p "$artifact_root/health"

ecs_ssh "rm -rf '$remote_incoming' && mkdir -p '$remote_incoming'"
ecs_scp "$repo_root/ops/remote-health.sh" "$REMOTE:$remote_incoming/remote-health.sh"

set +e
ecs_ssh \
  "chmod 700 '$remote_incoming/remote-health.sh' && sudo '$remote_incoming/remote-health.sh' '$health_base_url' '$image_tag' '$remote_incoming'"
health_status=$?
set -e

ecs_scp "$REMOTE:$remote_incoming/health.log" "$artifact_root/health/health.log" 2>/dev/null || true
ecs_ssh "rm -rf '$remote_incoming'" || true

exit "$health_status"
