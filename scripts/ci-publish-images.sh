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
docker_hub="$DOCKER_HUB"
swr_username="${SWR_USERNAME:?SWR_USERNAME is required}"
swr_password="${SWR_PASSWORD:?SWR_PASSWORD is required}"

[[ -f "$catalog" ]] || { echo "Missing service catalog: $catalog" >&2; exit 2; }
bash "$repo_root/scripts/ci-prepare-release.sh"

printf '%s' "$swr_password" | docker login "$registry" --username "$swr_username" --password-stdin

images_json=""
while IFS='|' read -r service dockerfile image_name deployment container module_dir jar_name; do
  [[ -z "$service" || "$service" == \#* ]] && continue
  service="${service%%$'\r'}"
  dockerfile="${dockerfile%%$'\r'}"
  image_name="${image_name%%$'\r'}"
  deployment="${deployment%%$'\r'}"
  container="${container%%$'\r'}"
  module_dir="${module_dir%%$'\r'}"
  jar_name="${jar_name%%$'\r'}"
  full_name="${registry}/${organization}/${image_name}"
  image_ref="${full_name}:${image_tag}"

  # CodeArts docker 插件不允许 manifest/inspect。本地用 pull 探测，已存在则拒绝覆盖。
  set +e
  docker pull "$image_ref" >/dev/null 2>&1
  pull_status=$?
  set -e
  if [[ "$pull_status" -eq 0 ]]; then
    echo "Refusing to overwrite existing image: $image_ref" >&2
    exit 3
  fi

  build_args=(
    --file "$dockerfile"
    --build-arg "DOCKER_HUB=${docker_hub}"
    --tag "$image_ref"
  )
  if [[ -n "${module_dir:-}" && -n "${jar_name:-}" ]]; then
    build_args+=(--build-arg "MODULE_DIR=${module_dir}" --build-arg "JAR_NAME=${jar_name}")
  fi
  docker build "${build_args[@]}" .
  docker push "$image_ref"

  digest="unavailable"
  digest_line="$(docker inspect --format='{{index .RepoDigests 0}}' "$image_ref" 2>/dev/null || true)"
  if [[ "$digest_line" == *@sha256:* ]]; then
    digest="${digest_line##*@}"
  fi
  mkdir -p "$release_dir/image-metadata"
  printf '%s\n' "{\"image\":\"$image_ref\",\"digest\":\"$digest\"}" > "$release_dir/image-metadata/${service}.json"
  item="{\"service\":\"$service\",\"image\":\"$image_ref\",\"digest\":\"$digest\",\"deployment\":\"$deployment\",\"container\":\"$container\"}"
  images_json="${images_json}${images_json:+,}${item}"
done < "$catalog"

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
echo "Published immutable release: $image_tag"
