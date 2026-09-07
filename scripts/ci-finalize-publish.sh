#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=ci-cd-common.sh
source "$repo_root/scripts/ci-cd-common.sh"
ci_enter_repo
ci_release_env

image_tag="$IMAGE_TAG"
artifact_root="$CI_ARTIFACT_DIR"
release_dir="$artifact_root/release"
catalog="$repo_root/ops/services.conf"
registry="$SWR_REGISTRY"
organization="$SWR_ORGANIZATION"

[[ -d "$release_dir/k8s" && -f "$release_dir/release-metadata.json" ]] || {
  echo "Release artifacts are missing; run ci-prepare-release.sh first." >&2
  exit 2
}

images_json=""
if [[ -f "$catalog" ]]; then
  while IFS='|' read -r service dockerfile image_name deployment container; do
    [[ -z "$service" || "$service" == \#* ]] && continue
    image_ref="${registry}/${organization}/${image_name}:${image_tag}"
    digest="pushed"
    mkdir -p "$release_dir/image-metadata"
    printf '%s\n' "{\"image\":\"$image_ref\",\"digest\":\"$digest\"}" > "$release_dir/image-metadata/${service}.json"
    item="{\"service\":\"$service\",\"image\":\"$image_ref\",\"digest\":\"$digest\",\"deployment\":\"$deployment\",\"container\":\"$container\"}"
    images_json="${images_json}${images_json:+,}${item}"
  done < "$catalog"
fi

cat > "$release_dir/release-metadata.json" <<EOF
{
  "imageTag": "$image_tag",
  "pipelineNumber": "$PIPELINE_NUMBER",
  "commitId": "$COMMIT_ID",
  "commitIdShort": "$COMMIT_ID_SHORT",
  "sourceBranch": "$SOURCE_BRANCH",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "failureDemo": ${FAILURE_DEMO:-false},
  "images": [$images_json]
}
EOF

tar -C "$release_dir" -czf "$artifact_root/release-${image_tag}.tgz" k8s release-metadata.json image-metadata
echo "Recorded published images for $image_tag"
