#!/usr/bin/env bash
# 单体 vs 微服务 性能对比实验：同机、同数据、同脚本，各 3 轮
# 目标接口：商品列表 /api/products、商品详情 /api/products/1001
# 单体入口 backend（shop_db），微服务入口 catalog-service（catalog_db）
# 注意：从宿主机直连 ClusterIP（k3s 节点上 ClusterIP 可路由；DNS 名仅集群内可解析）
BACKEND=10.43.13.11:8080
CATALOG=10.43.147.22:8082
set -u
OUT=/root/perf-$(date +%Y%m%d-%H%M)
mkdir -p "$OUT"
THREADS=20
DURATION=60

run_round() { # name target_url round
  local name="$1" url="$2" round="$3"
  echo "=== $name round $round: $url ==="
  ( sleep $((DURATION/2)); kubectl -n shop top pod --no-headers 2>/dev/null ) > "$OUT/${name}-r${round}-top.txt" &
  python3 /root/loadtest.py "$url" "$THREADS" "$DURATION" "$OUT/${name}-r${round}.csv" | tee "$OUT/${name}-r${round}.json"
  echo
  sleep 20  # 冷却
}

for round in 1 2 3; do
  run_round monolith-list "http://$BACKEND/api/products" "$round"
  run_round micro-list "http://$CATALOG/api/products" "$round"
  run_round monolith-detail "http://$BACKEND/api/products/1001" "$round"
  run_round micro-detail "http://$CATALOG/api/products/1001" "$round"
done
echo "ALL DONE -> $OUT"
ls -la "$OUT"
