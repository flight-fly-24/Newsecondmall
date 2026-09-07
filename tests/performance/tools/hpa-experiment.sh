#!/usr/bin/env bash
# catalog-service HPA 扩缩容实验（服务器上执行，ClusterIP 直连）
# 用法: bash hpa-experiment.sh
set -u
OUT=/root/hpa-$(date +%Y%m%d-%H%M)
mkdir -p "$OUT"
CATALOG=10.43.147.22:8082

echo "=== T-0 初始状态 ===" | tee "$OUT/00-before.txt"
kubectl -n shop get hpa,pods -o wide | tee -a "$OUT/00-before.txt"
kubectl -n shop top pod 2>/dev/null | tee -a "$OUT/00-before.txt"

# 后台采集 HPA/Pod 快照（每 10 秒，共 12 分钟）
( for i in $(seq 1 72); do
    echo "--- T+$((i*10))s $(date +%H:%M:%S) ---"
    kubectl -n shop get hpa catalog-service --no-headers
    kubectl -n shop get pods -l app.kubernetes.io/name=catalog-service --no-headers
    kubectl -n shop top pod -l app.kubernetes.io/name=catalog-service --no-headers 2>/dev/null
    sleep 10
  done ) > "$OUT/01-watch.txt" &
WATCH_PID=$!

echo "=== 开始压测：100 线程 x 300s ==="
python3 /root/loadtest.py "http://$CATALOG/api/products" 100 300 "$OUT/hpa-load.csv" | tee "$OUT/02-load-summary.json"

echo "=== 压测结束，观察缩容（稳定窗口 300s，等 6 分钟）==="
sleep 360
kill $WATCH_PID 2>/dev/null
sleep 2
( for i in $(seq 1 6); do
    echo "--- 缩容观察 +$((i*30))s ---"
    kubectl -n shop get hpa catalog-service --no-headers
    kubectl -n shop get pods -l app.kubernetes.io/name=catalog-service --no-headers
    sleep 30
  done ) >> "$OUT/01-watch.txt"

echo "=== 结束状态 ===" | tee "$OUT/03-after.txt"
kubectl -n shop get hpa,pods | tee -a "$OUT/03-after.txt"
kubectl -n shop describe hpa catalog-service | tail -25 | tee "$OUT/04-hpa-events.txt"
echo "HPA DONE -> $OUT"
