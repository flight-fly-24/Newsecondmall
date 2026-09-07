#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT
mkdir -p "$test_root/bin" "$test_root/artifacts"

cat > "$test_root/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -e
if [[ "$1" == "login" ]]; then exit 0; fi
if [[ "$1" == "pull" ]]; then exit 1; fi
if [[ "$1" == "build" ]]; then exit 0; fi
if [[ "$1" == "push" ]]; then exit 0; fi
if [[ "$1" == "inspect" ]]; then
  printf '%s\n' 'swr.cn-north-4.myhuaweicloud.com/songguo/shop-backend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  exit 0
fi
echo "Unexpected fake docker call: $*" >&2
exit 90
EOF
chmod +x "$test_root/bin/docker"

export PATH="$test_root/bin:$PATH"
export IMAGE_TAG="release-27-abc12345"
export PIPELINE_NUMBER="27"
export COMMIT_ID="abc1234567890def"
export SOURCE_BRANCH="master"
export CI_ARTIFACT_DIR="$test_root/artifacts"
export SWR_USERNAME="test-user"
export SWR_PASSWORD="test-password"

bash "$repo_root/scripts/ci-publish-images.sh"

kustomization="$test_root/artifacts/release/k8s/kustomization.yaml"
[[ "$(grep -c 'newTag: release-27-abc12345' "$kustomization")" -eq 6 ]]
grep -q 'songguo.dev/image-tag: "release-27-abc12345"' "$kustomization"
grep -q 'songguo.dev/commit-id: "abc1234567890def"' "$kustomization"
grep -q 'songguo.dev/pipeline-number: "27"' "$kustomization"
grep -q '"imageTag": "release-27-abc12345"' "$test_root/artifacts/release/release-metadata.json"
grep -q 'shop-backend:release-27-abc12345' "$test_root/artifacts/release/release-metadata.json"
grep -q 'shop-frontend:release-27-abc12345' "$test_root/artifacts/release/release-metadata.json"
grep -q 'shop-user-service:release-27-abc12345' "$test_root/artifacts/release/release-metadata.json"
grep -q 'shop-catalog-service:release-27-abc12345' "$test_root/artifacts/release/release-metadata.json"
grep -q 'trade-service:release-27-abc12345' "$test_root/artifacts/release/release-metadata.json"
grep -q 'interaction-service:release-27-abc12345' "$test_root/artifacts/release/release-metadata.json"
[[ -f "$test_root/artifacts/release-release-27-abc12345.tgz" ]]

unset IMAGE_TAG
export COMMIT_ID_SHORT="manual00"
export PIPELINE_NUMBER="1"
export COMMIT_ID="854bf9990721a83e8f807ae3351a96ee7a861740"
export CI_ARTIFACT_DIR="$test_root/artifacts-auto"
mkdir -p "$CI_ARTIFACT_DIR"
bash "$repo_root/scripts/ci-prepare-release.sh"
grep -q 'newTag: release-1-854bf999' "$CI_ARTIFACT_DIR/release/k8s/kustomization.yaml"
[[ "$(tr -d '[:space:]' < "$CI_ARTIFACT_DIR/image-tag.txt")" == "release-1-854bf999" ]]

export CI_ARTIFACT_DIR="$test_root/artifacts-shorter"
mkdir -p "$CI_ARTIFACT_DIR"
export COMMIT_ID_SHORTER="deadbeef"
bash "$repo_root/scripts/ci-prepare-release.sh"
grep -q 'newTag: release-1-deadbeef' "$CI_ARTIFACT_DIR/release/k8s/kustomization.yaml"
[[ "$(tr -d '[:space:]' < "$CI_ARTIFACT_DIR/image-tag.txt")" == "release-1-deadbeef" ]]
unset COMMIT_ID_SHORTER

export IMAGE_TAG="wrong-tag"
if bash "$repo_root/scripts/ci-publish-images.sh" >/dev/null 2>&1; then
  echo "Invalid tag unexpectedly passed validation" >&2
  exit 1
fi

echo "CI publish contract tests: OK"
