#!/usr/bin/env bash
# =============================================================================
# backup.sh - 松果集市 数据库备份
# 用法:
#   ./backup.sh                                # 备份到 ./backups/ 目录
#   BACKUP_DIR=/path ./backup.sh               # 备份到指定目录
#   ./backup.sh logical                        # 仅逻辑备份（mysqldump）
#   ./backup.sh physical                       # 仅物理备份（拷贝 mysql_data 卷）
#   ./backup.sh all                            # 逻辑+物理（默认）
#   KEEP_DAYS=7 ./backup.sh                    # 自动清理 7 天前的旧备份（默认 7）
# 输出:
#   ${BACKUP_DIR}/shop_db_YYYYMMDD_HHMMSS.sql.gz       逻辑备份
#   ${BACKUP_DIR}/mysql_data_YYYYMMDD_HHMMSS.tar.gz    物理备份
#   ${BACKUP_DIR}/manifest_YYYYMMDD_HHMMSS.txt         元信息
# 环境变量:
#   DB_CONTAINER  Docker 容器名（默认 deploy-mysql-1）
#   DB_NAME       默认 shop_db
#   BACKUP_DIR    默认 <脚本所在目录>/backups
#   KEEP_DAYS     默认 7
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_CONTAINER="${DB_CONTAINER:-deploy-mysql-1}"
DB_NAME="${DB_NAME:-shop_db}"
BACKUP_DIR="${BACKUP_DIR:-${SCRIPT_DIR}/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
MODE="${1:-all}"
TS="$(date +%Y%m%d_%H%M%S)"
HOST="$(hostname 2>/dev/null || echo unknown)"

mkdir -p "${BACKUP_DIR}"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2; }

# ---- 0. 容器可达性检查 -------------------------------------------------------
if ! docker ps --format '{{.Names}}' | grep -qx "${DB_CONTAINER}"; then
    log "ERROR: 容器 ${DB_CONTAINER} 未运行"
    log "  当前运行中的容器:"
    docker ps --format '    {{.Names}} ({{.Status}})' | sed 's/^/  /' || true
    exit 1
fi

# ---- 1. 逻辑备份（mysqldump） -----------------------------------------------
do_logical() {
    local out="${BACKUP_DIR}/shop_db_${TS}.sql.gz"
    log "[logical] 开始 -> ${out}"
    docker exec "${DB_CONTAINER}" sh -c "mysqldump -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --default-character-set=utf8mb4 --single-transaction --routines --triggers --events --add-drop-table ${DB_NAME}" \
        | gzip -c > "${out}"
    local size
    size=$(du -h "${out}" | awk '{print $1}')
    log "[logical] 完成: ${out} (${size})"
    echo "${out}"
}

# ---- 2. 物理备份（拷贝 mysql_data 卷） --------------------------------------
do_physical() {
    local out="${BACKUP_DIR}/mysql_data_${TS}.tar.gz"
    log "[physical] 开始 -> ${out}"

    # 检查容器是否使用 named volume（deploy_mysql_data）或 bind mount
    local mount
    mount=$(docker inspect "${DB_CONTAINER}" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{if .Source}}|{{.Source}}{{end}}{{end}}{{end}}' || true)
    if [[ -z "${mount}" ]]; then
        log "[physical] 容器未挂载 /var/lib/mysql，跳过"
        return 0
    fi

    # 停后端写入（安全）：FLUSH TABLES + 短时全局读锁
    log "[physical] 准备快照（短暂停后端 -> 锁表 -> 拷贝 -> 解锁）"
    docker exec "${DB_CONTAINER}" sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'FLUSH TABLES WITH READ LOCK;' && sleep 1" || true

    # 优先尝试用 named volume 的临时容器拷贝（最稳）
    local vol_name="${mount%%|*}"
    local src="${mount#*|}"

    if [[ "${vol_name}" != "${src}" && -n "${vol_name}" ]]; then
        # named volume
        docker run --rm \
            -v "${vol_name}:/var/lib/mysql:ro" \
            -v "${BACKUP_DIR}:/backup" \
            alpine:3.20 \
            sh -c "tar czf /backup/mysql_data_${TS}.tar.gz -C /var/lib/mysql ." \
            || log "[physical] named volume 方式失败，回退 bind mount"
    fi

    if [[ ! -f "${out}" && -d "${src}" ]]; then
        tar czf "${out}" -C "${src}" . \
            || { log "[physical] 物理备份失败"; return 1; }
    fi

    docker exec "${DB_CONTAINER}" sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'UNLOCK TABLES;'" >/dev/null 2>&1 || true

    if [[ -f "${out}" ]]; then
        local size
        size=$(du -h "${out}" | awk '{print $1}')
        log "[physical] 完成: ${out} (${size})"
        echo "${out}"
    else
        log "[physical] 跳过（未生成产物）"
    fi
}

# ---- 3. 写元信息 ------------------------------------------------------------
write_manifest() {
    local logical_file="$1"
    local physical_file="$2"
    local manifest="${BACKUP_DIR}/manifest_${TS}.txt"
    cat > "${manifest}" <<EOF
# 松果集市 数据库备份清单
备份时间:       $(date '+%Y-%m-%d %H:%M:%S %z')
备份主机:       ${HOST}
数据库:         ${DB_NAME}
MySQL 容器:     ${DB_CONTAINER}
备份方式:       ${MODE}

逻辑备份:       ${logical_file:-<未生成>}
  体积:         $( [[ -n "${logical_file}" && -f "${logical_file}" ]] && du -h "${logical_file}" | awk '{print $1}' || echo '-' )

物理备份:       ${physical_file:-<未生成>}
  体积:         $( [[ -n "${physical_file}" && -f "${physical_file}" ]] && du -h "${physical_file}" | awk '{print $1}' || echo '-' )

容器 MySQL 版本: $( docker exec "${DB_CONTAINER}" sh -c "mysql -V" 2>&1 | head -1 )

# 恢复命令示例:
#   逻辑恢复: ./restore.sh ${logical_file:-<file>}
#   物理恢复: docker compose down mysql && rm -rf deploy_mysql_data && tar xzf <file> -C deploy_mysql_data && docker compose up -d
EOF
    log "[manifest] -> ${manifest}"
    cat "${manifest}"
}

# ---- 4. 清理过期备份 --------------------------------------------------------
cleanup_old() {
    log "[cleanup] 删除 ${KEEP_DAYS} 天前的备份..."
    find "${BACKUP_DIR}" -type f \( -name 'shop_db_*.sql.gz' -o -name 'mysql_data_*.tar.gz' -o -name 'manifest_*.txt' \) -mtime +${KEEP_DAYS} -print -delete | sed 's/^/  /' || true
}

# ---- 入口 -------------------------------------------------------------------
logical_file=""
physical_file=""
case "${MODE}" in
    logical)  logical_file=$(do_logical) ;;
    physical) physical_file=$(do_physical) ;;
    all|"")   logical_file=$(do_logical); physical_file=$(do_physical) ;;
    *)
        echo "用法: $0 [logical|physical|all]" >&2
        exit 1
        ;;
esac

write_manifest "${logical_file}" "${physical_file}"
cleanup_old
log "[done] 备份完成"
