#!/usr/bin/env bash
# =============================================================================
# restore.sh - 松果集市 数据库恢复
# 用法:
#   ./restore.sh <backup-file>           # 自动识别逻辑/物理备份
#   ./restore.sh <backup-file> --yes      # 跳过确认（CI/自动化用）
#   ./restore.sh --list                   # 列出可用的备份
# 风险:
#   逻辑恢复会 DROP 现有表后重新导入，会清空当前库
#   物理恢复会停止 mysql 容器并替换数据卷，会清空当前数据
#   默认要求输入 YES 才继续
# 环境变量:
#   DB_CONTAINER  Docker 容器名（默认 deploy-mysql-1）
#   DB_NAME       默认 shop_db
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_CONTAINER="${DB_CONTAINER:-deploy-mysql-1}"
DB_NAME="${DB_NAME:-shop_db}"
BACKUP_DIR="${SCRIPT_DIR}/backups"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# ---- 列表模式 ---------------------------------------------------------------
if [[ "${1:-}" == "--list" ]]; then
    echo "==== 可用备份（${BACKUP_DIR}）===="
    ls -lh "${BACKUP_DIR}"/shop_db_*.sql.gz 2>/dev/null | awk '{print "  logical: " $9 " (" $5 ")"}' || echo "  (无逻辑备份)"
    ls -lh "${BACKUP_DIR}"/mysql_data_*.tar.gz 2>/dev/null | awk '{print "  physical: " $9 " (" $5 ")"}' || echo "  (无物理备份)"
    exit 0
fi

# ---- 参数校验 ---------------------------------------------------------------
FILE="${1:-}"
ASSUME_YES="${2:-}"

if [[ -z "${FILE}" ]]; then
    echo "用法: $0 <backup-file> [--yes]" >&2
    echo "      $0 --list" >&2
    exit 1
fi

if [[ ! -f "${FILE}" ]]; then
    log "ERROR: 备份文件不存在: ${FILE}"
    exit 1
fi

# ---- 识别备份类型 -----------------------------------------------------------
case "${FILE}" in
    *.sql.gz)  KIND="logical" ;;
    *.sql)     KIND="logical" ;;
    *.tar.gz)  KIND="physical" ;;
    *)         log "ERROR: 无法识别备份类型（仅支持 .sql.gz / .sql / .tar.gz）"; exit 1 ;;
esac
log "备份类型: ${KIND}"
log "备份文件: ${FILE} ($(du -h "${FILE}" | awk '{print $1}'))"

# ---- 二次确认 ---------------------------------------------------------------
if [[ "${ASSUME_YES}" != "--yes" ]]; then
    echo
    echo "=================================================================="
    echo "  ⚠️  警告：本次恢复会清空 ${DB_NAME} 的当前数据"
    echo "  - 逻辑恢复会先 DROP 全部表再导入"
    echo "  - 物理恢复会停止 mysql 容器并替换数据卷"
    echo "=================================================================="
    read -r -p "确认继续？(输入 YES 继续): " CONFIRM
    if [[ "${CONFIRM}" != "YES" ]]; then
        log "用户取消"
        exit 0
    fi
fi

# ---- 容器检查 ---------------------------------------------------------------
if ! docker ps --format '{{.Names}}' | grep -qx "${DB_CONTAINER}"; then
    log "ERROR: 容器 ${DB_CONTAINER} 未运行"
    exit 1
fi

# ---- 1. 逻辑恢复 ------------------------------------------------------------
do_restore_logical() {
    local sql_file="$1"

    log "[logical] 准备：停用外键 + 清空表"
    docker exec "${DB_CONTAINER}" sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME} -e 'SET FOREIGN_KEY_CHECKS=0;'" >/dev/null

    log "[logical] 导入（gzip 解压后管道）"
    if [[ "${sql_file}" == *.gz ]]; then
        gunzip -c "${sql_file}" | docker exec -i "${DB_CONTAINER}" sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME}"
    else
        docker exec -i "${DB_CONTAINER}" sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME}" < "${sql_file}"
    fi

    log "[logical] 恢复：启用外键 + 刷新表数"
    docker exec "${DB_CONTAINER}" sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME} -e 'SET FOREIGN_KEY_CHECKS=1;'" >/dev/null

    local table_count
    table_count=$(docker exec "${DB_CONTAINER}" sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME} -e 'SHOW TABLES;'" 2>/dev/null | wc -l | awk '{print $1}')
    log "[logical] 完成: 当前 ${DB_NAME} 共 ${table_count} 张表"
}

# ---- 2. 物理恢复 ------------------------------------------------------------
do_restore_physical() {
    local tar_file="$1"

    log "[physical] 停 mysql 容器（其他服务同时停）"
    cd "${SCRIPT_DIR}/../.."
    docker compose --env-file deploy/.env -f deploy/docker-compose.yml stop mysql 2>&1 | sed 's/^/  /'

    log "[physical] 删除 mysql_data 卷（注意：已挂载的备份目录需先 umount）"
    docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v mysql 2>&1 | sed 's/^/  /'

    log "[physical] 解压备份到临时目录"
    local tmpdir
    tmpdir=$(mktemp -d)
    tar xzf "${tar_file}" -C "${tmpdir}"

    log "[physical] 用新卷挂载并恢复数据"
    docker volume create deploy_mysql_data >/dev/null
    docker run --rm \
        -v deploy_mysql_data:/var/lib/mysql \
        -v "${tmpdir}:/backup:ro" \
        alpine:3.20 \
        sh -c "rm -rf /var/lib/mysql/* && cp -a /backup/. /var/lib/mysql/ && chown -R 999:999 /var/lib/mysql || true" \
        2>&1 | sed 's/^/  /'

    rm -rf "${tmpdir}"
    log "[physical] 重新启动服务"
    docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d 2>&1 | sed 's/^/  /'

    log "[physical] 等待 mysql healthy"
    local retries=20
    while (( retries > 0 )); do
        if docker exec "${DB_CONTAINER}" sh -c "mysqladmin ping -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --silent" >/dev/null 2>&1; then
            log "[physical] mysql 已就绪"
            return 0
        fi
        sleep 2
        retries=$((retries - 1))
    done
    log "WARN: mysql 启动超时，请检查 docker logs ${DB_CONTAINER}"
}

# ---- 入口 -------------------------------------------------------------------
case "${KIND}" in
    logical)  do_restore_logical "${FILE}" ;;
    physical) do_restore_physical "${FILE}" ;;
esac

log "[done] 恢复完成"
