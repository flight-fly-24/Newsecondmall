#!/usr/bin/env bash
# =============================================================================
# build-k8s-configmap.sh - 把 deploy/db/init/ 下的 SQL 一键生成 K8s ConfigMap
# 用法:
#   ./tools/build-k8s-configmap.sh                          # 输出到 stdout
#   ./tools/build-k8s-configmap.sh > k8s/mysql-init-configmap.yaml
#   ./tools/build-k8s-configmap.sh --out k8s/mysql-init-configmap.yaml
# 设计目标:
#   - 单一权威源：deploy/db/init/*.sql（与 V001/V002 内容一致）
#   - db.sql 改完重新跑这个脚本，K8s ConfigMap 自动更新
#   - 输出格式与 K8s kubectl apply -f 完全兼容
#   - 兼容 bash 3.2（macOS 默认）
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 脚本位于 deploy/db/tools/build-k8s-configmap.sh → 上溯三级到仓库根
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

INIT_DIR_DEFAULT="$REPO_ROOT/deploy/db/init"
OUT_DEFAULT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --source|-s) INIT_DIR="$2"; shift 2 ;;
        --out|-o)    OUT="$2"; shift 2 ;;
        --help|-h)
            sed -n '3,12p' "$0"; exit 0 ;;
        *) echo "Unknown: $1" >&2; exit 1 ;;
    esac
done

INIT_DIR="${INIT_DIR:-$INIT_DIR_DEFAULT}"
OUT="${OUT:-$OUT_DEFAULT}"

[[ -d "$INIT_DIR" ]] || { echo "ERROR: 源目录不存在: $INIT_DIR" >&2; exit 1; }

# ---- 收集 SQL 文件（按文件名排序，兼容 bash 3.2） -------------------------
SQL_FILES=()
while IFS= read -r f; do
    SQL_FILES+=("$f")
done < <(ls -1 "$INIT_DIR"/*.sql 2>/dev/null | sort)
[[ ${#SQL_FILES[@]} -gt 0 ]] || { echo "ERROR: $INIT_DIR 下无 *.sql 文件" >&2; exit 1; }

# ---- 拼装内容到临时文件，再输出 -------------------------------------------
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

{
    cat <<EOF
# Generated from ${INIT_DIR#$REPO_ROOT/}. Do not edit by hand.
# Re-generate: deploy/db/tools/build-k8s-configmap.sh > k8s/mysql-init-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: mysql-init
  namespace: shop
  labels:
    app.kubernetes.io/name: mysql
    app.kubernetes.io/part-of: songuo-shop
  annotations:
    songguo.dev/generated-by: deploy/db/tools/build-k8s-configmap.sh
    songguo.dev/source-path: ${INIT_DIR#$REPO_ROOT/}
data:
EOF

    for f in "${SQL_FILES[@]}"; do
        name=$(basename "$f")
        echo "  ${name}: |"
        sed 's/^/    /' "$f"
        echo ""
    done
} > "$TMP"

# 输出
if [[ -n "$OUT" ]]; then
    cp "$TMP" "$OUT"
    echo "已写入: $OUT ($(wc -l < "$OUT") 行, $(du -h "$OUT" | awk '{print $1}'))" >&2
else
    cat "$TMP"
fi
