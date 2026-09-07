#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=ci-cd-common.sh
source "$repo_root/scripts/ci-cd-common.sh"
ci_enter_repo
ci_release_env

image_tag="$IMAGE_TAG"
pipeline_number="$PIPELINE_NUMBER"
commit_id="$COMMIT_ID"
commit_short="$COMMIT_ID_SHORT"
source_branch="$SOURCE_BRANCH"
artifact_root="$CI_ARTIFACT_DIR"
release_dir="$artifact_root/release"

expected_tag="release-${pipeline_number}-${commit_short}"
if [[ "$image_tag" != "$expected_tag" ]]; then
  echo "IMAGE_TAG must be exactly $expected_tag" >&2
  exit 2
fi
if [[ ! "$image_tag" =~ ^[a-z0-9][a-z0-9._-]{0,127}$ ]] || [[ "$image_tag" == "latest" ]]; then
  echo "Invalid immutable image tag: $image_tag" >&2
  exit 2
fi

mkdir -p "$release_dir/image-metadata"
rm -rf "$release_dir/k8s"
cp -R "$repo_root/k8s" "$release_dir/k8s"

# Both image entries intentionally use the same immutable release tag.
sed -i -E "s#(^[[:space:]]*newTag:)[[:space:]].*#\\1 ${image_tag}#" "$release_dir/k8s/kustomization.yaml"
sed -i -E \
  -e "s#(^[[:space:]]*songguo.dev/image-tag:)[[:space:]].*#\\1 \"${image_tag}\"#" \
  -e "s#(^[[:space:]]*songguo.dev/commit-id:)[[:space:]].*#\\1 \"${commit_id}\"#" \
  -e "s#(^[[:space:]]*songguo.dev/pipeline-number:)[[:space:]].*#\\1 \"${pipeline_number}\"#" \
  "$release_dir/k8s/kustomization.yaml"

if [[ "${FAILURE_DEMO:-false}" == "true" ]]; then
  # Explicit, manual-only rollback demonstration. The second image is made unavailable.
  awk -v bad="${image_tag}-missing" '
    /^  - name: .*shop-frontend$/ { frontend=1 }
    frontend && /^    newTag:/ { sub(/newTag:.*/, "newTag: " bad); frontend=0 }
    { print }
  ' "$release_dir/k8s/kustomization.yaml" > "$release_dir/k8s/kustomization.yaml.tmp"
  mv "$release_dir/k8s/kustomization.yaml.tmp" "$release_dir/k8s/kustomization.yaml"
fi

cat > "$release_dir/release-metadata.json" <<EOF
{
  "imageTag": "$image_tag",
  "pipelineNumber": "$pipeline_number",
  "commitId": "$commit_id",
  "commitIdShort": "$commit_short",
  "sourceBranch": "$source_branch",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "failureDemo": ${FAILURE_DEMO:-false},
  "images": []
}
EOF

printf '%s\n' "$image_tag" > "$artifact_root/image-tag.txt"
echo "Prepared release artifacts for $image_tag"
