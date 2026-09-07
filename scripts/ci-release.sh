#!/usr/bin/env bash
set -Eeuo pipefail

# Combined local/Linux entry. CodeArts 拆成三张卡，不跑这个文件。
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
artifact_root="${CI_ARTIFACT_DIR:-$repo_root/ci-artifacts}"
mkdir -p "$artifact_root"

bash scripts/ci-prepare-release.sh
bash scripts/ci-publish-images.sh 2>&1 | tee "$artifact_root/image-publish.log"
bash scripts/ci-deploy-k8s.sh 2>&1 | tee "$artifact_root/kubernetes-deploy.log"
bash scripts/ci-k8s-health.sh 2>&1 | tee "$artifact_root/kubernetes-health.log"
