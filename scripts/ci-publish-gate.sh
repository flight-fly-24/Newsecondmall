#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=ci-cd-common.sh
source "$repo_root/scripts/ci-cd-common.sh"
ci_enter_repo
ci_release_env
meta="ci-artifacts/release/release-metadata.json"
kustomization="ci-artifacts/release/k8s/kustomization.yaml"
catalog="$repo_root/ops/services.conf"
grep -q "newTag: ${IMAGE_TAG}" "$kustomization"
while IFS='|' read -r service _ image_name _; do
  [[ -z "$service" || "$service" == \#* ]] && continue
  grep -q "${image_name}:${IMAGE_TAG}" "$meta"
done < "$catalog"
echo "publish-gate ok: ${IMAGE_TAG}"
