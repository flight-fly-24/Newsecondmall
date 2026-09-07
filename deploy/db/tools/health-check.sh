#!/usr/bin/env bash
# =============================================================================
# health-check.sh - 松果集市 数据库体检
# 用法:
#   ./tools/health-check.sh                    # 完整体检
#   ./tools/health-check.sh --quick            # 仅容器/库/连接
#   ./tools/health-check.sh --json             # 机器可读输出
# =============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INIT_DIR="${SCRIPT_DIR}/../init"
DB_CONTAINER="${DB_CONTAINER:-deploy-mysql-1}"
DB_NAME="${DB_NAME:-shop_db}"
MODE="full"
JSON_MODE="false"

for arg in "$@"; do
    case "${arg}" in
        --quick) MODE="quick" ;;
        --json)  JSON_MODE="true" ;;
        *) ;;
    esac
done

MYSQL_RUN() {
    # 输出完整结果（过滤 mysql 密码警告），调用方用 $(MYSQL_RUN "SQL" | head -N) 取值
    local sql="$1"
    docker exec -i -e "MYSQL_SQL=${sql}" "${DB_CONTAINER}" sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" '"${DB_NAME}"' -N -B -e "$MYSQL_SQL"' 2>&1 \
        | grep -v "Using a password" || true
}

ok()   { echo -e "  \033[32mOK\033[0m  $*"; }
warn() { echo -e "  \033[33mWARN\033[0m $*"; }
fail() { echo -e "  \033[31mFAIL\033[0m $*"; }
sec()  { echo; echo "==== $* ===="; }

# ---- 0. 容器可达性 ---------------------------------------------------------
sec "容器状态"
if docker ps --format '{{.Names}}' | grep -qx "${DB_CONTAINER}"; then
    STATUS=$(docker inspect "${DB_CONTAINER}" --format '{{.State.Status}}')
    ok "${DB_CONTAINER} 状态: ${STATUS}"
else
    fail "${DB_CONTAINER} 未运行"
    echo "  请先: docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d"
    exit 1
fi

# ---- 1. 端口/连接 ---------------------------------------------------------
sec "MySQL 连通性"
if docker exec "${DB_CONTAINER}" sh -c "mysqladmin ping -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --silent" 2>/dev/null; then
    ok "mysqladmin ping 成功"
else
    fail "mysqladmin ping 失败"
    exit 1
fi

# ---- 2. 数据库存在 ---------------------------------------------------------
sec "数据库存在性"
if MYSQL_RUN "SHOW DATABASES;" 2>/dev/null | grep -qx "${DB_NAME}"; then
    ok "${DB_NAME} 存在"
else
    fail "${DB_NAME} 不存在"
    exit 1
fi

# ---- quick 模式收尾 ---------------------------------------------------------
if [[ "${MODE}" == "quick" ]]; then
    echo
    ok "quick 体检完成"
    exit 0
fi

# ---- 3. 表数量与关键表行数 --------------------------------------------------
sec "表与行数"
TABLE_COUNT=$(MYSQL_RUN "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}';" | head -1)
echo "  表数量: ${TABLE_COUNT}"
[[ "${TABLE_COUNT}" -ge 20 ]] && ok "表数量符合预期 (>=20)" || warn "表数量偏少 (<20)"

echo "  关键表行数:"
for t in users goods store orders product_review community_topic cart_item user_address; do
    n=$(MYSQL_RUN "SELECT COUNT(*) FROM \`${t}\`;" 2>/dev/null | head -1)
    printf "    %-20s %s\n" "${t}:" "${n:-?}"
done

# ---- 4. collation 一致性 ---------------------------------------------------
sec "字符集 / Collation"
DIFF_COLL=$(MYSQL_RUN "SELECT COUNT(DISTINCT TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}';")
if [[ "${DIFF_COLL}" == "1" ]]; then
    ok "全部表 collation 一致"
    MYSQL_RUN "SELECT TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}' LIMIT 1;" | head -1 | awk '{print "  当前 collation: " $1}'
else
    fail "存在 ${DIFF_COLL} 种不同 collation"
    MYSQL_RUN "SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}' ORDER BY TABLE_COLLATION, TABLE_NAME;" | head -20
fi

# ---- 5. 关键唯一键 / 索引 ---------------------------------------------------
sec "关键索引"
for ix in "follow_store:uk_follow_user_store" "product_review:uk_review_order" "topic_post_like:uk_topic_post_like" "topic_post_action:uk_topic_post_action" "cart_item:uk_cart_user_goods"; do
    table="${ix%%:*}"
    name="${ix##*:}"
    n=$(MYSQL_RUN "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='${DB_NAME}' AND TABLE_NAME='${table}' AND INDEX_NAME='${name}';" | head -1)
    [[ "${n}" -ge 1 ]] && ok "${table}.${name} 存在" || fail "${table}.${name} 缺失"
done

# ---- 6. 外键一致性 ---------------------------------------------------------
sec "外键一致性（孤儿记录检查）"
ORPHAN=0
for q in \
    "orders LEFT JOIN users ON orders.buyer_id=users.user_id WHERE users.user_id IS NULL" \
    "orders LEFT JOIN users ON orders.seller_id=users.user_id WHERE users.user_id IS NULL" \
    "orders LEFT JOIN goods ON orders.goods_id=goods.goods_id WHERE goods.goods_id IS NULL" \
    "product_review LEFT JOIN orders ON product_review.order_id=orders.order_id WHERE orders.order_id IS NULL" \
    "topic_post LEFT JOIN community_topic ON topic_post.topic_id=community_topic.topic_id WHERE community_topic.topic_id IS NULL" \
    "cart_item LEFT JOIN goods ON cart_item.goods_id=goods.goods_id WHERE goods.goods_id IS NULL" \
    "user_address LEFT JOIN users ON user_address.user_id=users.user_id WHERE users.user_id IS NULL"
do
    n=$(MYSQL_RUN "SELECT COUNT(*) FROM ${q};" | head -1)
    [[ "${n}" -gt 0 ]] && fail "孤儿: ${q} -> ${n} 行" && ORPHAN=$((ORPHAN + n))
done
[[ "${ORPHAN}" -eq 0 ]] && ok "无外键孤儿记录"

# ---- 7. 关键账号存在性 -----------------------------------------------------
sec "默认账号"
for u in demo seller admin; do
    n=$(MYSQL_RUN "SELECT COUNT(*) FROM users WHERE username='${u}';" | head -1)
    [[ "${n}" -ge 1 ]] && ok "用户 ${u} 存在" || fail "用户 ${u} 缺失"
done

# ---- 8. 后端 API 探活 -------------------------------------------------------
sec "后端连通性（可选）"
if command -v curl >/dev/null 2>&1; then
    if curl -s --max-time 5 http://localhost/api/products >/dev/null 2>&1; then
        ok "前端 API 可访问"
    else
        warn "前端 API 不可访问（仅影响应用层，不影响 DB 体检）"
    fi
fi

echo
ok "体检完成"
