#!/usr/bin/env bash
# =============================================================================
# reset.sh - 松果集市 数据库重置（开发环境专用，⚠️ 危险）
# 用法:
#   ./tools/reset.sh              # 交互确认
#   ./tools/reset.sh --yes        # 跳过确认
#   ./tools/reset.sh --keep-data  # 只重建结构，不删卷（保留数据但跑 init/）
# 行为:
#   默认: 停 mysql 容器 + 删卷 + 重启 → entrypoint 自动跑 init/ 重新建库
#   --keep-data: 不删卷，只重置 schema_migrations 后手动跑 init/
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."
DB_CONTAINER="${DB_CONTAINER:-deploy-mysql-1}"
DB_NAME="${DB_NAME:-shop_db}"

ASSUME_YES=""
KEEP_DATA=""
for arg in "$@"; do
    case "${arg}" in
        --yes)        ASSUME_YES="--yes" ;;
        --keep-data)  KEEP_DATA="--keep-data" ;;
    esac
done

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

if [[ -z "${ASSUME_YES}" ]]; then
    echo "=================================================================="
    echo "  ⚠️  警告：即将重置 ${DB_NAME}"
    if [[ -z "${KEEP_DATA}" ]]; then
        echo "  - 将删除 mysql_data 卷（全部数据丢失）"
    else
        echo "  - 保留数据卷，仅重建结构"
    fi
    echo "=================================================================="
    read -r -p "确认继续？(输入 YES 继续): " CONFIRM
    if [[ "${CONFIRM}" != "YES" ]]; then
        log "用户取消"
        exit 0
    fi
fi

cd "${PROJECT_ROOT}"

if [[ -n "${KEEP_DATA}" ]]; then
    log "[reset] 保留数据卷，重建结构"
    docker exec -i "${DB_CONTAINER}" sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'DROP DATABASE IF EXISTS ${DB_NAME}; CREATE DATABASE ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;'" \
        || { log "ERROR: 删除并重建库失败"; exit 1; }
    log "[reset] 重新跑 init/ 脚本"
    for f in deploy/db/init/*.sql; do
        log "  -> $(basename "$f")"
        docker exec -i "${DB_CONTAINER}" sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME}" < "$f"
    done
    log "[reset] 完成（数据已重建）"
    exit 0
fi

# 完整重置：删卷 + 重建
log "[reset] 停 mysql 容器"
docker compose --env-file deploy/.env -f deploy/docker-compose.yml stop mysql 2>&1 | sed 's/^/  /'

log "[reset] 删卷（deploy_mysql_data）"
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v mysql 2>&1 | sed 's/^/  /'

log "[reset] 重启服务 → entrypoint 自动跑 init/ 重新建库"
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d 2>&1 | sed 's/^/  /'

log "[reset] 等待 mysql healthy..."
retries=30
while (( retries > 0 )); do
    if docker exec "${DB_CONTAINER}" sh -c "mysqladmin ping -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --silent" >/dev/null 2>&1; then
        log "[reset] mysql 已就绪"
        log "[reset] 验证表数量"
        count=$(docker exec "${DB_CONTAINER}" sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME} -N -B -e 'SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=\"${DB_NAME}\";'" 2>/dev/null | head -1)
        log "[reset] 当前 ${DB_NAME} 共 ${count} 张表"
        log "[reset] 完成"
        exit 0
    fi
    sleep 2
    retries=$((retries - 1))
done
log "ERROR: mysql 启动超时"
exit 1
