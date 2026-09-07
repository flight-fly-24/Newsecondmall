# Shared helpers for CodeArts publish / deploy / health scripts.
# Source this file; do not execute it.

ci_enter_repo() {
  local here
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  cd "$here"
}

ci_release_env() {
  PIPELINE_NUMBER="${PIPELINE_NUMBER:-${BUILD_NUMBER:-1}}"
  export PIPELINE_NUMBER
  if [[ -z "${COMMIT_ID:-}" ]]; then
    COMMIT_ID="$(git rev-parse HEAD 2>/dev/null || true)"
  fi
  : "${COMMIT_ID:?COMMIT_ID is required}"
  export COMMIT_ID
  # CodeArts docker plugin can substitute ${COMMIT_ID_SHORTER} (first 8 chars) but cannot run $(cut).
  # Prefer the system param so bash IMAGE_TAG matches docker -t. Fall back to cut locally.
  COMMIT_ID_SHORT="${COMMIT_ID_SHORTER:-$(printf '%s' "$COMMIT_ID" | cut -c1-8)}"
  export COMMIT_ID_SHORT
  IMAGE_TAG="${IMAGE_TAG:-release-${PIPELINE_NUMBER}-${COMMIT_ID_SHORT}}"
  export IMAGE_TAG
  if [[ -n "${BUILDNUMBER:-}${JOB_ID:-}" && -z "${COMMIT_ID_SHORTER:-}" ]]; then
    echo "COMMIT_ID_SHORTER is empty. Docker YAML would tag release-${PIPELINE_NUMBER}- and miss kustomize." >&2
    echo "Do not create a custom parameter named COMMIT_ID_SHORTER. Use the CodeArts system parameter." >&2
    exit 1
  fi
  SOURCE_BRANCH="${SOURCE_BRANCH:-${codeBranch:-feature/lqy-first-stage}}"
  export SOURCE_BRANCH
  CI_ARTIFACT_DIR="${CI_ARTIFACT_DIR:-$PWD/ci-artifacts}"
  export CI_ARTIFACT_DIR
  DOCKER_HUB="${DOCKER_HUB:-swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io}"
  export DOCKER_HUB
  SWR_REGISTRY="${SWR_REGISTRY:-swr.cn-north-4.myhuaweicloud.com}"
  export SWR_REGISTRY
  SWR_ORGANIZATION="${SWR_ORGANIZATION:-songguo}"
  export SWR_ORGANIZATION
  mkdir -p "$CI_ARTIFACT_DIR"
  echo "Release env IMAGE_TAG=${IMAGE_TAG} PIPELINE_NUMBER=${PIPELINE_NUMBER} COMMIT_ID=${COMMIT_ID} COMMIT_ID_SHORTER=${COMMIT_ID_SHORTER:-} COMMIT_ID_SHORT=${COMMIT_ID_SHORT}"
}
