#!/usr/bin/env bash
set -euo pipefail

if [ ! -f shopping_back/shopping_back/pom.xml ]; then
  pom="$(find . /data /var /workspace /tmp -path '*/shopping_back/shopping_back/pom.xml' 2>/dev/null | head -n 1)"
  [ -n "${pom:-}" ] || { echo "找不到仓库根目录"; exit 1; }
  cd "$(dirname "$(dirname "$(dirname "$pom")")")"
fi

export DB_PASSWORD="${CI_DB_PASSWORD:?CI_DB_PASSWORD is required}"
export MYSQL_ROOT_PASSWORD="${CI_MYSQL_ROOT_PASSWORD:?CI_MYSQL_ROOT_PASSWORD is required}"
export DB_USERNAME="${DB_USERNAME:-shop_user}"
export PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-http://127.0.0.1:18080}"
export HTTP_PORT="${E2E_HTTP_PORT:-18080}"
export IMAGE_TAG="${IMAGE_TAG:-ci-e2e}"
export E2E_NETWORK_NAME="${E2E_NETWORK_NAME:-soft-shop-e2e-net}"
export COMPOSE_PROJECT_NAME="${E2E_PROJECT_NAME:-soft-shop-e2e}"
export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-1}"
export DOCKER_HUB="${DOCKER_HUB:-docker.m.daocloud.io}"
export MYSQL_IMAGE="${MYSQL_IMAGE:-${DOCKER_HUB}/library/mysql:8.4}"
export SELENIUM_IMAGE="${SELENIUM_IMAGE:-${DOCKER_HUB}/selenium/standalone-chrome:4.35.0}"
export E2E_MAVEN_IMAGE="${E2E_MAVEN_IMAGE:-${DOCKER_HUB}/library/maven:3.9.11-eclipse-temurin-17}"

e2e_status=1
ci_docker_dir="/tmp/ci-docker"
compose=()

archive_e2e_outputs() {
  mkdir -p e2e-tests/target/e2e-artifacts e2e-tests/target/surefire-reports tests/api/reports/microservices tests/e2e/results
  tar -czf e2e-surefire-reports.tgz -C e2e-tests/target surefire-reports || true
  tar -czf e2e-artifacts.tgz -C e2e-tests/target e2e-artifacts || true
  tar -czf e2e-api-reports.tgz -C tests/api/reports microservices || true
  tar -czf e2e-run-results.tgz -C tests/e2e results || true
  printf '%s\n' "${e2e_status}" > e2e-exit-code.txt
}

cleanup() {
  if [ "${#compose[@]}" -gt 0 ]; then
    "${compose[@]}" logs --no-color > e2e-tests/target/e2e-artifacts/compose.log 2>&1 || true
    "${compose[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  archive_e2e_outputs
}
trap cleanup EXIT
archive_e2e_outputs

download_file() {
  local dest="$1"
  local url="$2"
  echo "下载 ${url}"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 3 --retry-delay 2 -o "$dest" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$dest" "$url"
  else
    echo "没有 curl 或 wget，无法下载 ${url}"
    return 1
  fi
}

download_first_ok() {
  local dest="$1"
  shift
  local url
  for url in "$@"; do
    if download_file "$dest" "$url"; then
      return 0
    fi
    echo "下载失败: ${url}"
  done
  return 1
}

ensure_docker_socket() {
  local sock
  for sock in /var/run/docker.sock /run/docker.sock /var/run/docker/docker.sock; do
    if [ -S "$sock" ]; then
      export DOCKER_HOST="unix://${sock}"
      echo "使用 Docker socket: ${sock}"
      return 0
    fi
  done
  echo "未发现 docker.sock，稍后如需要会启动 dockerd"
  return 0
}

ensure_docker_daemon() {
  if docker info >/dev/null 2>&1; then
    echo "Docker 守护进程已可用"
    return 0
  fi
  if ! command -v dockerd >/dev/null 2>&1; then
    echo "没有 dockerd，也连不上 Docker 守护进程"
    docker info || true
    return 1
  fi
  echo "正在启动 dockerd"
  mkdir -p /var/run
  dockerd --host=unix:///var/run/docker.sock >/tmp/dockerd.log 2>&1 &
  local i
  for i in $(seq 1 40); do
    if docker info >/dev/null 2>&1; then
      echo "dockerd 已就绪"
      return 0
    fi
    sleep 1
  done
  echo "dockerd 未就绪："
  cat /tmp/dockerd.log || true
  return 1
}

ensure_docker_cli() {
  local candidate
  if command -v docker >/dev/null 2>&1; then
    echo "已有 docker: $(command -v docker)"
    return 0
  fi
  for candidate in /usr/bin/docker /usr/local/bin/docker /usr/libexec/docker/cli/docker; do
    if [ -x "$candidate" ]; then
      export PATH="$(dirname "$candidate"):${PATH}"
      echo "找到 docker: ${candidate}"
      return 0
    fi
  done

  echo "shell 镜像没有 docker，从华为云镜像下载静态客户端"
  mkdir -p "$ci_docker_dir"
  download_file /tmp/docker-static.tgz \
    "${DOCKER_STATIC_URL:-https://mirrors.huaweicloud.com/docker-ce/linux/static/stable/x86_64/docker-24.0.9.tgz}"
  tar -xzf /tmp/docker-static.tgz -C /tmp
  cp /tmp/docker/docker "${ci_docker_dir}/docker"
  chmod +x "${ci_docker_dir}/docker"
  export PATH="${ci_docker_dir}:${PATH}"
}

ensure_docker_compose() {
  export DOCKER_CONFIG="${DOCKER_CONFIG:-/tmp/ci-docker-config}"
  mkdir -p "$ci_docker_dir" "${DOCKER_CONFIG}/cli-plugins"
  if docker compose version >/dev/null 2>&1; then
    echo "已有 docker compose"
    return 0
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    echo "已有 docker-compose: $(command -v docker-compose)"
    return 0
  fi

  echo "下载 docker compose 插件"
  download_first_ok "${DOCKER_CONFIG}/cli-plugins/docker-compose" \
    "${COMPOSE_PLUGIN_URL:-https://files.m.daocloud.io/github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64}" \
    "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64"
  chmod +x "${DOCKER_CONFIG}/cli-plugins/docker-compose"
  cp "${DOCKER_CONFIG}/cli-plugins/docker-compose" "${ci_docker_dir}/docker-compose"
  chmod +x "${ci_docker_dir}/docker-compose"
  export PATH="${ci_docker_dir}:${PATH}"
}

select_compose() {
  if docker compose version >/dev/null 2>&1; then
    compose=(docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file deploy/.env -f deploy/docker-compose.yml -f tests/e2e/docker-compose.e2e.yml -f tests/e2e/docker-compose.ci.yml)
    echo "使用: docker compose"
    docker compose version
    return 0
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    compose=(docker-compose --project-name "$COMPOSE_PROJECT_NAME" --env-file deploy/.env -f deploy/docker-compose.yml -f tests/e2e/docker-compose.e2e.yml -f tests/e2e/docker-compose.ci.yml)
    echo "使用: docker-compose"
    docker-compose version
    return 0
  fi
  echo "没有可用的 docker compose"
  docker version || true
  return 1
}

echo "===== E2E Docker 诊断 ====="
id || true
command -v docker || true
command -v docker-compose || true
ls -l /var/run/docker.sock /run/docker.sock 2>&1 || true

ensure_docker_socket
ensure_docker_cli
ensure_docker_daemon
ensure_docker_compose
docker version
docker info

cat > deploy/.env <<EOF
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
DB_USERNAME=${DB_USERNAME}
DB_PASSWORD=${DB_PASSWORD}
PUBLIC_ORIGIN=${PUBLIC_ORIGIN}
HTTP_PORT=${HTTP_PORT}
IMAGE_TAG=${IMAGE_TAG}
DOCKER_HUB=${DOCKER_HUB}
MYSQL_IMAGE=${MYSQL_IMAGE}
SELENIUM_IMAGE=${SELENIUM_IMAGE}
EOF

select_compose

"${compose[@]}" up -d --build
frontend_ok=0
selenium_ok=0
for _ in $(seq 1 72); do
  frontend_ok=0
  selenium_ok=0
  curl -fsS --max-time 5 "http://127.0.0.1:${HTTP_PORT}/api/products" >/dev/null 2>&1 && frontend_ok=1
  "${compose[@]}" exec -T selenium curl -fsS http://127.0.0.1:4444/status 2>/dev/null | grep -q '"ready": true' && selenium_ok=1
  [ "$frontend_ok" -eq 1 ] && [ "$selenium_ok" -eq 1 ] && break
  sleep 5
done
if [ "${frontend_ok:-0}" -ne 1 ] || [ "${selenium_ok:-0}" -ne 1 ]; then
  echo "E2E 环境未就绪"
  e2e_status=1
  exit 1
fi

echo "===== 微服务 API E2E ====="
set +e
docker run --rm \
  --network "$E2E_NETWORK_NAME" \
  -v "$(pwd):/work" \
  -w /work \
  -e USER_SERVICE_URL=http://user-service:8081 \
  -e CATALOG_SERVICE_URL=http://catalog-service:8082 \
  -e TRADE_SERVICE_URL=http://trade-service:8083 \
  -e INTERACTION_SERVICE_URL=http://interaction-service:8084 \
  -e GATEWAY_URL=http://frontend \
  "${E2E_NODE_IMAGE:-${DOCKER_HUB}/library/node:18-bookworm-slim}" \
  sh -lc 'npm ci --no-audit --no-fund && node tests/api/build-microservices-collection.mjs && node tests/api/run-microservices-newman.mjs'
api_status=$?
set -e
if [ "$api_status" -ne 0 ]; then
  echo "微服务 API E2E 失败，跳过 UI E2E"
  e2e_status="$api_status"
  printf '# CI E2E result\n\nAPI failed with exit code %s. UI was skipped.\n' "$api_status" > tests/e2e/results/summary.md
  exit "$e2e_status"
fi

echo "===== 微服务 UI E2E ====="
set +e
docker run --rm \
  --network "$E2E_NETWORK_NAME" \
  -v "$(pwd):/work" \
  -v soft-shop-e2e-m2-cache:/root/.m2 \
  -w /work/e2e-tests \
  -e LANG=C.UTF-8 \
  -e JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 \
  "${E2E_MAVEN_IMAGE}" \
  mvn -B -ntp clean test \
  -De2e.remoteUrl=http://selenium:4444/wd/hub \
  -De2e.baseUrl=http://frontend \
  -De2e.apiUrl=http://frontend \
  -De2e.headless=true \
  -De2e.timeoutSeconds=20
e2e_status=$?
set -e
printf '# CI E2E result\n\nAPI: passed\nUI exit code: %s\n' "$e2e_status" > tests/e2e/results/summary.md
exit "${e2e_status}"
