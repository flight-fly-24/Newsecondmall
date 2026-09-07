#!/usr/bin/env bash
# CodeArts E2E：在 docker build 容器内跑完整微服务拓扑。
# CodeArts 的 docker 插件只允许 build/tag/push/pull/save，不能 docker run / docker compose，
# 因此 MariaDB、旧单体、4 个微服务、Nginx 网关、Chromium 全部在同一个构建容器里启动。
set -euo pipefail

cd /work
if [ -f tests/e2e/.ci-secrets ]; then
  set -a
  # shellcheck disable=SC1091
  . tests/e2e/.ci-secrets
  set +a
fi

e2e_status=1
archive_e2e_outputs() {
  mkdir -p e2e-tests/target/e2e-artifacts e2e-tests/target/surefire-reports \
    tests/api/reports/microservices tests/e2e/results
  # 把各服务日志带进失败现场，替代 compose.log
  for log in /tmp/e2e-mysqld.log /tmp/shopping-back.log /tmp/user-service.log \
    /tmp/catalog-service.log /tmp/trade-service.log /tmp/interaction-service.log; do
    if [ -f "$log" ]; then
      cp -f "$log" e2e-tests/target/e2e-artifacts/ || true
    fi
  done
  tar -czf e2e-surefire-reports.tgz -C e2e-tests/target surefire-reports || true
  tar -czf e2e-artifacts.tgz -C e2e-tests/target e2e-artifacts || true
  tar -czf e2e-api-reports.tgz -C tests/api/reports microservices || true
  tar -czf e2e-run-results.tgz -C tests/e2e results || true
  printf '%s\n' "${e2e_status}" > e2e-exit-code.txt
}
trap archive_e2e_outputs EXIT
archive_e2e_outputs

export DB_PASSWORD="${CI_DB_PASSWORD:?CI_DB_PASSWORD is required}"
export MYSQL_ROOT_PASSWORD="${CI_MYSQL_ROOT_PASSWORD:?CI_MYSQL_ROOT_PASSWORD is required}"
export DB_USERNAME="${DB_USERNAME:-shop_user}"
export PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-http://127.0.0.1:18080}"
export APP_CORS_PATTERNS="${APP_CORS_PATTERNS:-http://127.0.0.1:*,http://localhost:*}"
node_home="/tmp/ci-node"

mkdir -p /root/.m2
cat > /root/.m2/settings.xml <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>huawei</id>
      <mirrorOf>*</mirrorOf>
      <url>https://repo.huaweicloud.com/repository/maven/</url>
    </mirror>
  </mirrors>
</settings>
EOF

echo "===== 启动 MariaDB ====="
mkdir -p /run/mysqld
chown mysql:mysql /run/mysqld
mysqld --user=mysql --bind-address=127.0.0.1 --port=3306 >/tmp/e2e-mysqld.log 2>&1 &
ok=0
for _ in $(seq 1 60); do
  if mysqladmin --protocol=SOCKET ping >/dev/null 2>&1 || mysql -uroot -e "SELECT 1" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 2
done
if [ "$ok" -ne 1 ]; then
  echo "MariaDB 未启动"
  tail -n 50 /tmp/e2e-mysqld.log || true
  exit 1
fi
# 单体库 + 4 个微服务库；微服务建表由各服务 spring.sql.init 完成
mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS shop_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS user_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS catalog_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS trade_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS interaction_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON shop_db.* TO '${DB_USERNAME}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON shop_db.* TO '${DB_USERNAME}'@'localhost';
GRANT ALL PRIVILEGES ON user_db.* TO '${DB_USERNAME}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON user_db.* TO '${DB_USERNAME}'@'localhost';
GRANT ALL PRIVILEGES ON catalog_db.* TO '${DB_USERNAME}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON catalog_db.* TO '${DB_USERNAME}'@'localhost';
GRANT ALL PRIVILEGES ON trade_db.* TO '${DB_USERNAME}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON trade_db.* TO '${DB_USERNAME}'@'localhost';
GRANT ALL PRIVILEGES ON interaction_db.* TO '${DB_USERNAME}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON interaction_db.* TO '${DB_USERNAME}'@'localhost';
FLUSH PRIVILEGES;
SQL
mysql -uroot shop_db < shopping_back/shopping_back/doc/db.sql

echo "===== 安装 Node.js ====="
if ! command -v npm >/dev/null 2>&1; then
  node_ver="${NODE_VERSION:-v18.20.8}"
  tarball="node-${node_ver}-linux-x64.tar.gz"
  curl -fsSL "https://mirrors.huaweicloud.com/nodejs/${node_ver}/${tarball}" -o "/tmp/${tarball}"
  mkdir -p "$node_home"
  tar -xzf "/tmp/${tarball}" -C "$node_home" --strip-components=1
  export PATH="${node_home}/bin:${PATH}"
fi
npm config set registry https://repo.huaweicloud.com/repository/npm/

echo "===== 构建前端 ====="
(
  cd shopping_front
  npm ci --no-audit --no-fund
  npm run build:h5
)
mkdir -p /usr/share/nginx/html
cp -a shopping_front/dist/build/h5/. /usr/share/nginx/html/
if [ -d shopping_front/static ]; then
  cp -a shopping_front/static/. /usr/share/nginx/html/static/ 2>/dev/null || true
fi
rm -f /etc/nginx/sites-enabled/default
cp tests/e2e/nginx.ci.conf /etc/nginx/conf.d/e2e.conf
nginx -t
nginx

wait_http() {
  local name="$1" url="$2" expected="$3" log="$4"
  local i code
  for i in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>/dev/null || true)"
    if [ "$code" = "$expected" ]; then
      echo "[ready] ${name}: ${url} (${code})"
      return 0
    fi
    sleep 3
  done
  echo "${name} 未就绪：${url} 期望 HTTP ${expected}，最后得到 ${code:-无响应}"
  if [ -n "$log" ] && [ -f "$log" ]; then
    echo "----- ${name} 日志尾部 -----"
    tail -n 60 "$log" || true
  fi
  return 1
}

echo "===== 构建微服务 ====="
mvn -B -ntp -f services/common/pom.xml -DskipTests install
for svc in user-service catalog-service trade-service interaction-service; do
  mvn -B -ntp -f "services/${svc}/pom.xml" -DskipTests package
done

start_service() {
  local name="$1" jar="$2" heap="$3"
  JAVA_TOOL_OPTIONS="-Xmx${heap} -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8" \
    java -jar "$jar" >"/tmp/${name}.log" 2>&1 &
  echo "已启动 ${name} (pid $!)"
}

echo "===== 启动微服务 ====="
# 各服务 application.properties 的默认值正好落在容器内拓扑：
# 端口 8081-8084、数据库 localhost:3306/<svc>_db、跨服务地址 127.0.0.1，无需额外覆盖。
for svc in user-service catalog-service trade-service interaction-service; do
  jar="$(ls -1 "services/${svc}/target/${svc}"-*.jar | head -n 1)"
  start_service "$svc" "$jar" 384m
done

echo "===== 构建并启动旧单体（未迁移接口兜底） ====="
(
  cd shopping_back/shopping_back
  mvn -B -ntp -DskipTests package
)
backend_jar="$(ls -1 shopping_back/shopping_back/target/shopping_back-*.jar | head -n 1)"
DB_URL="jdbc:mysql://127.0.0.1:3306/shop_db?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false" \
APP_UPLOAD_DIR=/tmp/shopping-back-upload \
AUTH_BASE_URL=http://127.0.0.1:8081 \
  start_service shopping-back "$backend_jar" 512m

echo "===== 等待服务就绪 ====="
wait_http user-service "http://127.0.0.1:8081/api/auth/search-users?keyword=" 200 /tmp/user-service.log
wait_http catalog-service "http://127.0.0.1:8082/api/products" 200 /tmp/catalog-service.log
wait_http trade-service "http://127.0.0.1:8083/api/cart" 401 /tmp/trade-service.log
wait_http interaction-service "http://127.0.0.1:8084/api/interaction/health" 200 /tmp/interaction-service.log
wait_http shopping-back "http://127.0.0.1:8080/api/products" 200 /tmp/shopping-back.log
wait_http gateway "http://127.0.0.1:18080/api/products" 200 ""

echo "===== 微服务 API E2E（Newman） ====="
npm ci --no-audit --no-fund
node tests/api/build-microservices-collection.mjs
set +e
USER_SERVICE_URL=http://127.0.0.1:8081 \
CATALOG_SERVICE_URL=http://127.0.0.1:8082 \
TRADE_SERVICE_URL=http://127.0.0.1:8083 \
INTERACTION_SERVICE_URL=http://127.0.0.1:8084 \
GATEWAY_URL=http://127.0.0.1:18080 \
  node tests/api/run-microservices-newman.mjs
api_status=$?
set -e
if [ "$api_status" -ne 0 ]; then
  echo "微服务 API E2E 失败，跳过 UI E2E"
  printf '# CI E2E result\n\nAPI failed with exit code %s. UI was skipped.\n' "$api_status" > tests/e2e/results/summary.md
  e2e_status="$api_status"
  exit "$e2e_status"
fi

chrome_bin=""
for candidate in /usr/bin/chromium /usr/bin/chromium-browser /usr/bin/google-chrome; do
  if [ -x "$candidate" ]; then
    chrome_bin="$candidate"
    break
  fi
done
if [ -z "$chrome_bin" ]; then
  echo "找不到 Chrome/Chromium"
  exit 1
fi
chromedriver_bin=""
for candidate in /usr/bin/chromedriver /usr/lib/chromium/chromedriver; do
  if [ -x "$candidate" ]; then
    chromedriver_bin="$candidate"
    break
  fi
done
if [ -z "$chromedriver_bin" ]; then
  echo "找不到 chromedriver"
  exit 1
fi
echo "使用浏览器: ${chrome_bin}"
echo "使用 ChromeDriver: ${chromedriver_bin}"
"${chrome_bin}" --version || true
"${chromedriver_bin}" --version || true

echo "===== 微服务 UI E2E（Selenium） ====="
export SE_OFFLINE=true
set +e
mvn -B -ntp -f e2e-tests/pom.xml test \
  -Dmaven.compiler.release=17 \
  -De2e.browser=chrome \
  -De2e.headless=true \
  -De2e.baseUrl=http://127.0.0.1:18080 \
  -De2e.apiUrl=http://127.0.0.1:18080 \
  -De2e.chromeBinary="${chrome_bin}" \
  -De2e.chromeDriver="${chromedriver_bin}" \
  -Dwebdriver.chrome.driver="${chromedriver_bin}" \
  -De2e.timeoutSeconds=20
e2e_status=$?
set -e
printf '# CI E2E result\n\nAPI: passed\nUI exit code: %s\n' "$e2e_status" > tests/e2e/results/summary.md
if [ "${e2e_status}" -ne 0 ]; then
  for log in /tmp/shopping-back.log /tmp/user-service.log /tmp/catalog-service.log \
    /tmp/trade-service.log /tmp/interaction-service.log; do
    echo "----- $(basename "$log") 尾部 -----"
    tail -n 60 "$log" || true
  done
fi
exit "${e2e_status}"
