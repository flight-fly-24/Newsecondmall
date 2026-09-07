#!/usr/bin/env bash
# =============================================================================
# apply.sh - 松果集市 数据库迁移应用工具
# 用法:
#   ./apply.sh                    # 应用所有未执行的迁移
#   ./apply.sh --to V003          # 应用到指定版本
#   ./apply.sh --status           # 查看当前已应用版本
#   ./apply.sh --force V002       # 强制重跑指定版本（谨慎使用）
#   ./apply.sh --init             # 空库初始化（与 docker entrypoint 等价）
# 环境变量:
#   DB_HOST       默认 127.0.0.1
#   DB_PORT       默认 3306
#   DB_NAME       默认 shop_db
#   DB_USER       默认 root
#   DB_PASSWORD   必填（来自 deploy/.env 或环境）
#   DB_CONTAINER  留空则直连；填 docker 容器名则用 docker exec 跑
# =============================================================================

set -euo pipefail

# ---- 路径与默认值 -----------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INIT_DIR="${SCRIPT_DIR}/../init"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-shop_db}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
DB_CONTAINER="${DB_CONTAINER:-}"

if [[ -z "${DB_PASSWORD}" && -z "${DB_CONTAINER}" ]]; then
    echo "[ERROR] DB_PASSWORD (或 DB_CONTAINER) 必须设置" >&2
    echo "        提示: source deploy/.env 后再执行" >&2
    exit 1
fi

# ---- MySQL 执行封装 ---------------------------------------------------------
run_sql() {
    local sql="$1"
    if [[ -n "${DB_CONTAINER}" ]]; then
        docker exec -i "${DB_CONTAINER}" sh -c "mysql --default-character-set=utf8mb4 -u${DB_USER} -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME}" <<< "${sql}"
    else
        mysql --default-character-set=utf8mb4 -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" <<< "${sql}"
    fi
}

run_sql_file() {
    local file="$1"
    # 把脚本里硬编码的 `shop_db` 替换为实际 DB_NAME（V001 通用化）
    local tmp
    tmp=$(mktemp)
    sed "s/\`shop_db\`/\`${DB_NAME}\`/g; s/DATABASE \`shop_db\`/DATABASE \`${DB_NAME}\`/g; s/USE \`shop_db\`/USE \`${DB_NAME}\`/g" "${file}" > "${tmp}"

    if [[ -n "${DB_CONTAINER}" ]]; then
        docker exec -i "${DB_CONTAINER}" sh -c "mysql --default-character-set=utf8mb4 -u${DB_USER} -p\"\${MYSQL_ROOT_PASSWORD}\" ${DB_NAME}" < "${tmp}"
    else
        mysql --default-character-set=utf8mb4 -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" < "${tmp}"
    fi
    rm -f "${tmp}"
}

# ---- 数据库与元数据表 ------------------------------------------------------
ensure_database() {
    if [[ -n "${DB_CONTAINER}" ]]; then
        docker exec -i "${DB_CONTAINER}" sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e \"CREATE DATABASE IF NOT EXISTS \\\`${DB_NAME}\\\` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;\""
    else
        mysql --default-character-set=utf8mb4 -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;"
    fi
}

ensure_meta_table() {
    run_sql "CREATE TABLE IF NOT EXISTS \`schema_migrations\` (
        \`version\`     VARCHAR(64)  NOT NULL,
        \`description\` VARCHAR(255) NOT NULL DEFAULT '',
        \`applied_at\`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        \`checksum\`    VARCHAR(64)  NOT NULL DEFAULT '',
        PRIMARY KEY (\`version\`)
    ) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;"
}

list_applied() {
    run_sql "SELECT \`version\`, \`applied_at\` FROM \`schema_migrations\` ORDER BY \`version\`;" 2>/dev/null | awk 'NR>1 {print $1}'
}

# 提取版本号：V001__xxx.sql -> V001
version_of() {
    basename "$1" | sed -E 's/^(V[0-9]+)__.*/\1/'
}

# 计算文件 sha256（用于 checksum 校验）
checksum_of() {
    shasum -a 256 "$1" 2>/dev/null | awk '{print $1}' | head -c 32
}

# ---- 子命令 -----------------------------------------------------------------
cmd_status() {
    ensure_database
    ensure_meta_table
    echo "==== 已应用的迁移 ===="
    list_applied || echo "(空)"
    echo ""
    echo "==== 可用的迁移脚本 ===="
    for f in "${SCRIPT_DIR}"/V*.sql; do
        [[ -f "$f" ]] || continue
        local v desc
        v=$(version_of "$f")
        desc=$(basename "$f" | sed -E 's/^V[0-9]+__//; s/\.sql$//')
        echo "  ${v}  ${desc}"
    done
}

cmd_apply() {
    local target_version="${1:-}"
    ensure_database
    ensure_meta_table

    local applied
    applied=$(list_applied || true)

    for f in $(ls "${SCRIPT_DIR}"/V*.sql | sort); do
        [[ -f "$f" ]] || continue
        local v desc
        v=$(version_of "$f")
        desc=$(basename "$f" | sed -E 's/^V[0-9]+__//; s/\.sql$//')

        # 已应用：跳过
        if echo "${applied}" | grep -qx "${v}"; then
            echo "  [SKIP] ${v}  ${desc}  (已应用)"
            continue
        fi

        # 指定了目标版本，没到就停
        if [[ -n "${target_version}" && "${v}" > "${target_version}" ]]; then
            echo "  [STOP] 目标版本 ${target_version} 已到达"
            break
        fi

        echo "  [APPLY] ${v}  ${desc}"
        run_sql_file "$f"

        local cs
        cs=$(checksum_of "$f")
        # 记录元数据（version 描述用 sql 文件名去掉前后缀；含特殊字符时手动转义）
        local safe_desc
        safe_desc=$(printf '%s' "${desc}" | sed "s/'/''/g")
        run_sql "INSERT IGNORE INTO \`schema_migrations\`(\`version\`, \`description\`, \`checksum\`) VALUES('${v}', '${safe_desc}', '${cs}');"
    done
    echo "==== 迁移完成 ===="
}

cmd_force() {
    local v="${1:-}"
    if [[ -z "${v}" ]]; then
        echo "[ERROR] --force 需要版本号" >&2
        exit 1
    fi
    local file="${SCRIPT_DIR}/${v}"__*.sql
    if [[ ! -f ${file} ]]; then
        echo "[ERROR] 找不到 ${v} 对应的脚本" >&2
        exit 1
    fi
    ensure_database
    ensure_meta_table
    echo "  [FORCE] ${v}（先回滚 schema_migrations 记录）"
    run_sql "DELETE FROM \`schema_migrations\` WHERE \`version\`='${v}';"
    run_sql_file "${file}"
    local cs
    cs=$(checksum_of "${file}")
    local desc
    desc=$(basename "${file}" | sed -E 's/^V[0-9]+__//; s/\.sql$//')
    run_sql "INSERT IGNORE INTO \`schema_migrations\`(\`version\`, \`description\`, \`checksum\`) VALUES('${v}', '${desc}', '${cs}');"
}

cmd_init() {
    echo "==== 空库初始化（与 docker entrypoint 等价）===="
    ensure_meta_table
    # 与 docker-compose 的 /docker-entrypoint-initdb.d 行为一致：
    # 仅在目标库不存在任何表时执行完整 init 脚本
    local count
    count=$(run_sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}';" 2>/dev/null | awk 'NR==2 {print $1}')
    if [[ "${count}" != "0" && -n "${count}" ]]; then
        echo "  [SKIP] ${DB_NAME} 已存在表（${count} 张），跳过空库初始化"
        return
    fi
    for f in $(ls "${INIT_DIR}"/*.sql | sort); do
        echo "  [INIT] $(basename "$f")"
        run_sql_file "$f"
    done
    echo "==== 空库初始化完成 ===="
}

# ---- 入口 -------------------------------------------------------------------
case "${1:-apply}" in
    --status|status)    cmd_status ;;
    --init|init)        cmd_init ;;
    --force)            shift; cmd_force "$@" ;;
    --to)               shift; cmd_apply "$@" ;;
    apply|"")           cmd_apply "" ;;
    -h|--help|help)
        sed -n '3,15p' "$0"
        ;;
    *)
        echo "[ERROR] 未知参数: $1" >&2
        echo "用法: $0 [--status|--init|--force V00X|--to V00X]" >&2
        exit 1
        ;;
esac
