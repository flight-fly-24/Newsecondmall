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
release_dir="$artifact_root/release"
remote_incoming="/tmp/soft-shop-${image_tag}"

[[ "$image_tag" =~ ^release-[a-zA-Z0-9._-]+$ ]] || { echo "Invalid IMAGE_TAG" >&2; exit 2; }

[[ -d "$release_dir/k8s" && -f "$release_dir/release-metadata.json" ]] || {
  echo "Release artifacts are missing; prepare release first." >&2
  exit 2
}

command -v ssh >/dev/null 2>&1 || { echo "ssh is required on this executor" >&2; exit 2; }
command -v scp >/dev/null 2>&1 || { echo "scp is required on this executor" >&2; exit 2; }

ecs_ssh_setup
mkdir -p "$artifact_root/deploy"

ecs_ssh "rm -rf '$remote_incoming' && mkdir -p '$remote_incoming'"
ecs_scp -r "$release_dir/k8s" "$release_dir/release-metadata.json" "$repo_root/ops/remote-deploy.sh" "$REMOTE:$remote_incoming/"

set +e
ecs_ssh \
  "chmod 700 '$remote_incoming/remote-deploy.sh' && sudo '$remote_incoming/remote-deploy.sh' '$remote_incoming' '$image_tag' '$health_base_url'"
deploy_status=$?
set -e

ecs_scp -r "$REMOTE:/opt/soft-shop/releases/$image_tag/diagnostics" "$artifact_root/deploy/" 2>/dev/null || true
ecs_scp "$REMOTE:/opt/soft-shop/releases/$image_tag/deploy.log" "$artifact_root/deploy/deploy.log" 2>/dev/null || true
ecs_ssh "rm -rf '$remote_incoming'" || true

exit "$deploy_status"
