#!/usr/bin/env bash
set -Eeuo pipefail

release_source="${1:?release source directory is required}"
image_tag="${2:?image tag is required}"
health_base_url="${3:-http://127.0.0.1}"
namespace="${K8S_NAMESPACE:-shop}"
release_root="${RELEASE_ROOT:-/opt/soft-shop/releases}"
rollout_timeout="${ROLLOUT_TIMEOUT:-300s}"
current_link="$release_root/current"
release_dir="$release_root/$image_tag"
workload_deployments=(backend frontend user-service catalog-service trade-service interaction-service)

[[ "$image_tag" =~ ^release-[a-zA-Z0-9._-]+$ ]] || { echo "Invalid release tag: $image_tag" >&2; exit 2; }
[[ "$release_root" == /opt/soft-shop/releases ]] || { echo "Unexpected release root" >&2; exit 2; }
command -v kubectl >/dev/null || { echo "kubectl is required" >&2; exit 2; }
command -v flock >/dev/null || { echo "flock is required" >&2; exit 2; }

mkdir -p "$release_root" "$release_dir/diagnostics"
exec 9>"$release_root/.deploy.lock"
flock -n 9 || { echo "Another deployment is already running" >&2; exit 9; }

cp -R "$release_source/k8s" "$release_dir/k8s"
cp "$release_source/release-metadata.json" "$release_dir/release-metadata.json"
exec > >(tee "$release_dir/deploy.log") 2>&1

bootstrap_current_release() {
  local deployment image image_name tag bootstrap_dir
  bootstrap_dir="$release_root/release-bootstrap-$(date -u +%Y%m%d%H%M%S)"
  mkdir -p "$bootstrap_dir"
  cp -R "$release_dir/k8s" "$bootstrap_dir/k8s"
  for deployment in "${workload_deployments[@]}"; do
    image="$(kubectl -n "$namespace" get deployment "$deployment" -o jsonpath='{.spec.template.spec.containers[0].image}')"
    tag="${image##*:}"
    [[ -n "$tag" && "$tag" != "$image" ]] || {
      echo "Cannot discover current image tag for rollback bootstrap: $deployment" >&2
      return 1
    }
    image_name="${image%:*}"
    awk -v image_name="$image_name" -v tag="$tag" '
      $0 == "  - name: " image_name { target=1 }
      target && /^    newTag:/ { sub(/newTag:.*/, "newTag: " tag); target=0 }
      { print }
    ' "$bootstrap_dir/k8s/kustomization.yaml" > "$bootstrap_dir/k8s/kustomization.yaml.tmp"
    mv "$bootstrap_dir/k8s/kustomization.yaml.tmp" "$bootstrap_dir/k8s/kustomization.yaml"
  done
  sed -i -E \
    -e 's#(^[[:space:]]*songguo.dev/image-tag:)[[:space:]].*#\1 "bootstrap"#' \
    -e 's#(^[[:space:]]*songguo.dev/commit-id:)[[:space:]].*#\1 "unknown"#' \
    -e 's#(^[[:space:]]*songguo.dev/pipeline-number:)[[:space:]].*#\1 "manual"#' \
    "$bootstrap_dir/k8s/kustomization.yaml"
  printf '%s\n' "bootstrap from current workload images" > "$bootstrap_dir/release-metadata.txt"
  ln -sfn "$bootstrap_dir" "$current_link"
  echo "Registered existing cluster state as $bootstrap_dir"
}

if [[ ! -L "$current_link" ]]; then
  bootstrap_current_release
fi

previous_release=""
if [[ -L "$current_link" ]]; then
  previous_release="$(readlink -f "$current_link")"
fi

collect_diagnostics() {
  kubectl -n "$namespace" get all,ingress,pvc -o wide > "$release_dir/diagnostics/resources.txt" 2>&1 || true
  kubectl -n "$namespace" get events --sort-by=.lastTimestamp > "$release_dir/diagnostics/events.txt" 2>&1 || true
  for deployment in mysql "${workload_deployments[@]}"; do
    kubectl -n "$namespace" describe deployment "$deployment" > "$release_dir/diagnostics/${deployment}-describe.txt" 2>&1 || true
    kubectl -n "$namespace" logs deployment/"$deployment" --all-containers=true --tail=300 > "$release_dir/diagnostics/${deployment}.log" 2>&1 || true
  done
}

check_prerequisites() {
  kubectl get namespace "$namespace" >/dev/null || return $?
  kubectl -n "$namespace" get secret shop-db swr-secret >/dev/null || return $?
  kubectl -n "$namespace" get pvc mysql-data upload-data >/dev/null || return $?
}

check_rollout_and_health() {
  for deployment in "${workload_deployments[@]}"; do
    kubectl -n "$namespace" rollout status "deployment/$deployment" --timeout="$rollout_timeout" || return $?
  done
  kubectl -n "$namespace" get pods || return $?
  kubectl -n "$namespace" get deployment "${workload_deployments[@]}" \
    -o custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image,VERSION:.metadata.annotations.songguo\\.dev/image-tag || return $?
  curl -fsS --retry 5 --retry-delay 3 --max-time 10 "$health_base_url/" >/dev/null || return $?
  curl -fsS --retry 5 --retry-delay 3 --max-time 10 "$health_base_url/api/products" >/dev/null || return $?

  # 数据库层健康检查（戴坤廷交付）
  if [[ -x "$release_source/deploy/db/tools/health-check.sh" ]]; then
    echo "[health-check] running DB health check"
    if ! DB_CONTAINER="$(kubectl -n "$namespace" get pod -l app.kubernetes.io/name=mysql -o jsonpath='{.items[0].metadata.name}')" \
         DB_NAME=shop_db bash "$release_source/deploy/db/tools/health-check.sh" --quick; then
      echo "[health-check] FAILED" >&2
      return 1
    fi
  fi
}

rollback() {
  if [[ -n "$previous_release" && -d "$previous_release/k8s" ]]; then
    echo "Rolling back to $previous_release"
    kubectl apply -k "$previous_release/k8s"
    check_rollout_and_health
    ln -sfn "$previous_release" "$current_link"
    echo "Rollback completed; original deployment remains failed."
  else
    echo "No previous managed release is available for automatic rollback." >&2
  fi
}

status=0
check_prerequisites || status=$?
if [[ "$status" -eq 0 ]]; then
  kubectl apply -k "$release_dir/k8s" || status=$?
fi
if [[ "$status" -eq 0 ]]; then
  check_rollout_and_health || status=$?
fi

if [[ "$status" -ne 0 ]]; then
  echo "Deployment failed with status $status"
  collect_diagnostics
  rollback || true
  exit "$status"
fi

ln -sfn "$release_dir" "$current_link"
collect_diagnostics
printf '%s\n' "$image_tag" > "$release_dir/SUCCESS"

# Keep the newest ten immutable release directories. Never delete the current target.
mapfile -t all_releases < <(ls -1dt "$release_root"/release-* 2>/dev/null || true)
for old_release in "${all_releases[@]:10}"; do
  [[ -z "$old_release" || "$old_release" == "$(readlink -f "$current_link")" ]] && continue
  rm -rf -- "$old_release"
done

echo "Deployment succeeded: $image_tag"
