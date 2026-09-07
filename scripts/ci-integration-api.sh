#!/usr/bin/env bash
set -euo pipefail

if [ ! -f shopping_back/shopping_back/pom.xml ]; then
  pom="$(find . /data /var /workspace /tmp -path '*/shopping_back/shopping_back/pom.xml' 2>/dev/null | head -n 1)"
  if [ -z "${pom:-}" ]; then
    echo "找不到仓库根目录"
    exit 1
  fi
  cd "$(dirname "$(dirname "$(dirname "$pom")")")"
fi

report_dir="tests/api/reports/ci"
mkdir -p "$report_dir"
backend_pid=""
current_stage="prepare"

cleanup() {
  status=$?
  if [ -n "${backend_pid:-}" ]; then
    kill "$backend_pid" >/dev/null 2>&1 || true
  fi
  if [ -f /tmp/ci-mysql.pid ]; then
    kill "$(cat /tmp/ci-mysql.pid)" >/dev/null 2>&1 || true
  fi
  if [ "$status" -ne 0 ]; then
    printf 'FAILED_STAGE=%s\nEXIT_CODE=%s\n' "$current_stage" "$status" \
      > "$report_dir/failure-summary.txt"
    echo "集成测试失败阶段: $current_stage，退出码: $status"
    if [ -f "$report_dir/backend.log" ]; then
      echo "===== 后端日志最后 200 行 ====="
      tail -n 200 "$report_dir/backend.log" || true
    fi
  fi
}
trap cleanup EXIT
exec > >(tee "$report_dir/integration-api.log") 2>&1

export DB_PASSWORD="${CI_DB_PASSWORD:?CI_DB_PASSWORD is required}"
export MYSQL_ROOT_PASSWORD="${CI_MYSQL_ROOT_PASSWORD:?CI_MYSQL_ROOT_PASSWORD is required}"
export DB_USERNAME="${DB_USERNAME:-shop_user}"
export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:3306/shop_db?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false}"
export APP_CORS_PATTERNS="${APP_CORS_PATTERNS:-http://127.0.0.1:*,http://localhost:*}"
backend_port="${BACKEND_PORT:-8080}"
base_url="http://127.0.0.1:${backend_port}"
mysql_sock="/tmp/ci-mysql.sock"
mysql_data="/tmp/ci-mysql-data"
mysql_home="/tmp/ci-mysql-dist"
node_home="/tmp/ci-node"
mysql_tarball_url="${MYSQL_TARBALL_URL:-https://repo.huaweicloud.com/repository/toolkit/mysql/Downloads/MySQL-8.0/mysql-8.0.28-linux-glibc2.17-x86_64-minimal.tar.xz}"
ci_libs="/tmp/ci-libs"

mysql_cmd() {
  "${mysql_home}/bin/mysql" --socket="$mysql_sock" -uroot --protocol=SOCKET "$@"
}

enable_yum_mirrors() {
  mkdir -p /etc/yum.repos.d
  cat > /etc/yum.repos.d/ci-huawei.repo <<'EOF'
[ci-centos7]
name=ci-centos7
baseurl=https://mirrors.huaweicloud.com/centos/7/os/x86_64/
enabled=1
gpgcheck=0
skip_if_unavailable=1
[ci-centos7-extras]
name=ci-centos7-extras
baseurl=https://mirrors.huaweicloud.com/centos/7/extras/x86_64/
enabled=1
gpgcheck=0
skip_if_unavailable=1
EOF
}

ensure_mysqld_native_libs() {
  mkdir -p "$ci_libs"
  if [ -d ci/native ]; then
    cp -a ci/native/. "$ci_libs/"
    echo "已复制 ci/native 到 ${ci_libs}："
    ls -l "$ci_libs"
  fi
  export LD_LIBRARY_PATH="${ci_libs}:/usr/lib64:/lib64:${mysql_home}/lib:${LD_LIBRARY_PATH:-}"
  if [ ! -e "${ci_libs}/libaio.so.1" ]; then
    echo "缺少 ci/native/libaio.so.1"
    exit 1
  fi
  if [ ! -e "${ci_libs}/libnuma.so.1" ]; then
    echo "缺少 ci/native/libnuma.so.1"
    exit 1
  fi
}

install_mysqld_libs() {
  ensure_mysqld_native_libs
}

check_mysqld_libs() {
  export LD_LIBRARY_PATH="${ci_libs}:/usr/lib64:/lib64:${mysql_home}/lib:${LD_LIBRARY_PATH:-}"
  if ! command -v ldd >/dev/null 2>&1; then
    return 0
  fi
  echo "mysqld 动态库依赖："
  ldd "${mysql_home}/bin/mysqld" || true
  missing="$(ldd "${mysql_home}/bin/mysqld" | awk '/not found/ {print $1}' || true)"
  if [ -n "${missing:-}" ]; then
    echo "mysqld 还缺少这些库："
    printf '%s\n' "$missing"
    exit 1
  fi
}

extract_mysql_xz() {
  mkdir -p "$mysql_home"
  if tar -xJf /tmp/mysql.tar.xz -C "$mysql_home" --strip-components=1; then
    return 0
  fi
  if command -v xz >/dev/null 2>&1; then
    xz -dc /tmp/mysql.tar.xz | tar -x -C "$mysql_home" --strip-components=1
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import tarfile
tarfile.open("/tmp/mysql.tar.xz", "r:xz").extractall("/tmp/ci-mysql-unpack")
PY
    inner="$(find /tmp/ci-mysql-unpack -mindepth 1 -maxdepth 1 -type d | head -n 1)"
    cp -a "${inner}/." "$mysql_home/"
    return 0
  fi
  return 1
}

install_mysql() {
  if [ -x "${mysql_home}/bin/mysqld" ] && [ -x "${mysql_home}/bin/mysql" ]; then
    export PATH="${mysql_home}/bin:${PATH}"
    install_mysqld_libs
    return 0
  fi
  if command -v mysqld >/dev/null 2>&1 && command -v mysql >/dev/null 2>&1; then
    mysql_home="$(dirname "$(dirname "$(command -v mysqld)")")"
    install_mysqld_libs
    return 0
  fi

  echo "OS 信息："
  cat /etc/os-release 2>/dev/null || true
  echo "从华为云镜像下载 MySQL 二进制包（不走 yum、不走 Docker Hub）"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$mysql_tarball_url" -o /tmp/mysql.tar.xz
  elif command -v wget >/dev/null 2>&1; then
    wget -O /tmp/mysql.tar.xz "$mysql_tarball_url"
  else
    echo "没有 curl/wget，尝试用华为云 yum 源安装 curl"
    enable_yum_mirrors
    yum install -y curl
    curl -fL "$mysql_tarball_url" -o /tmp/mysql.tar.xz
  fi

  if ! extract_mysql_xz; then
    echo "解压 xz 失败，安装 xz/libaio 后重试"
    enable_yum_mirrors
    yum install -y xz libaio || yum install -y xz libaio-devel || true
    extract_mysql_xz
  fi
  if [ ! -x "${mysql_home}/bin/mysqld" ]; then
    echo "MySQL 二进制包解压失败"
    ls -la "$mysql_home" || true
    exit 1
  fi
  export PATH="${mysql_home}/bin:${PATH}"
  install_mysqld_libs
}

start_mysql() {
  mkdir -p "$mysql_data"
  export PATH="${mysql_home}/bin:${PATH}"
  install_mysqld_libs
  check_mysqld_libs
  if [ ! -d "$mysql_data/mysql" ]; then
    "${mysql_home}/bin/mysqld" --basedir="$mysql_home" --datadir="$mysql_data" --initialize-insecure --user="$(id -un)"
  fi
  "${mysql_home}/bin/mysqld" \
    --basedir="$mysql_home" \
    --datadir="$mysql_data" \
    --socket="$mysql_sock" \
    --pid-file=/tmp/ci-mysql.pid \
    --port=3306 \
    --bind-address=127.0.0.1 \
    --user="$(id -un)" &
  echo "等待 MySQL 就绪"
  ok=0
  for _ in $(seq 1 60); do
    if mysql_cmd -e "SELECT 1" >/dev/null 2>&1; then
      ok=1
      break
    fi
    sleep 2
  done
  if [ "$ok" -ne 1 ]; then
    echo "MySQL 未启动。若缺 libaio，会尝试从华为云 yum 源补依赖后再起一次。"
    enable_yum_mirrors
    yum install -y libaio numactl-libs ncurses-compat-libs || true
    "${mysql_home}/bin/mysqld" \
      --basedir="$mysql_home" \
      --datadir="$mysql_data" \
      --socket="$mysql_sock" \
      --pid-file=/tmp/ci-mysql.pid \
      --port=3306 \
      --bind-address=127.0.0.1 \
      --user="$(id -un)" &
    sleep 5
    if ! mysql_cmd -e "SELECT 1" >/dev/null 2>&1; then
      echo "MySQL 仍未启动"
      exit 1
    fi
  fi
  mysql_cmd <<SQL
CREATE DATABASE IF NOT EXISTS shop_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON shop_db.* TO '${DB_USERNAME}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON shop_db.* TO '${DB_USERNAME}'@'localhost';
FLUSH PRIVILEGES;
SQL
  mysql_cmd < shopping_back/shopping_back/doc/db.sql
}

install_node() {
  if command -v npm >/dev/null 2>&1; then
    return 0
  fi
  echo "安装 Node.js（华为云镜像）"
  node_ver="${NODE_VERSION:-v18.20.8}"
  tarball="node-${node_ver}-linux-x64.tar.gz"
  url="https://mirrors.huaweicloud.com/nodejs/${node_ver}/${tarball}"
  mkdir -p "$node_home"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url" -o "/tmp/${tarball}"
  else
    wget -q "$url" -O "/tmp/${tarball}"
  fi
  tar -xzf "/tmp/${tarball}" -C "$node_home" --strip-components=1
  export PATH="${node_home}/bin:${PATH}"
}

echo "===== 1/3 启动测试环境 ====="
current_stage="mysql-start"
install_mysql
start_mysql

current_stage="backend-build"
cd shopping_back/shopping_back
mvn -B -ntp -DskipTests package
jar="$(ls -1 target/shopping_back-*.jar | head -n 1)"
java -jar "$jar" > "../../$report_dir/backend.log" 2>&1 &
backend_pid=$!
cd - >/dev/null

echo "等待 ${base_url}/api/products"
current_stage="backend-readiness"
ok=0
for _ in $(seq 1 60); do
  if curl -fsS --max-time 5 "${base_url}/api/products" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 3
done
if [ "$ok" -ne 1 ]; then
  echo "后端未在时限内就绪"
  exit 1
fi

echo "===== 2/3 集成测试 ====="
current_stage="blackbox-smoke"
SMOKE_SKIP_FRONTEND=true bash tests/blackbox/smoke.sh "${base_url}"

echo "===== 3/3 接口测试 ====="
current_stage="api-fixture"
"${mysql_home}/bin/mysql" --socket="$mysql_sock" -u"$DB_USERNAME" -p"$DB_PASSWORD" --protocol=SOCKET shop_db \
  < tests/api/fixtures/setup.sql
current_stage="node-install"
install_node
if [ -d "${node_home}/bin" ]; then
  export PATH="${node_home}/bin:${PATH}"
fi
npm config set registry https://repo.huaweicloud.com/repository/npm/
npm ci
current_stage="newman-api"
SKIP_API_FIXTURE_SETUP=true API_BASE_URL="${base_url}" npm run test:api

current_stage="complete"
echo "集成测试和接口测试通过"
