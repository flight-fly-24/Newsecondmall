#!/usr/bin/env bash
# 故障处理实验：停掉 catalog-service，验证依赖它的 trade-service 快速失败、其他服务不受影响
# 在服务器上执行。输出保存到 /root/fault-<时间戳>/
set -u
OUT=/root/fault-$(date +%Y%m%d-%H%M)
mkdir -p "$OUT"
USER_SVC=10.43.46.177:8081
TRADE=10.43.92.51:8083
INTERACTION=10.43.107.212:8084
CATALOG=10.43.147.22:8082

echo "=== 1. 故障前状态 ===" | tee "$OUT/01-before.txt"
kubectl -n shop get pods -o wide | tee -a "$OUT/01-before.txt"

TOKEN=$(curl -s --max-time 5 -X POST "http://$USER_SVC/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"demo","password":"demo123"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")
echo "login token ok: ${TOKEN:0:16}..."

echo "=== 2. 停止 catalog-service ===" | tee "$OUT/02-scaled-down.txt"
kubectl -n shop scale deployment catalog-service --replicas=0 | tee -a "$OUT/02-scaled-down.txt"
kubectl -n shop wait --for=delete pod -l app.kubernetes.io/name=catalog-service --timeout=60s 2>/dev/null
kubectl -n shop get pods | tee -a "$OUT/02-scaled-down.txt"

echo "=== 3. 故障期间各服务表现 ===" | tee "$OUT/03-during.txt"
echo "--- 3.1 话题列表（interaction，不依赖 catalog）---" | tee -a "$OUT/03-during.txt"
curl -s -o /dev/null -w "GET /api/topics -> %{http_code} (%{time_total}s)\n" --max-time 5 "http://$INTERACTION/api/topics" | tee -a "$OUT/03-during.txt"
echo "--- 3.2 重新登录（user，不依赖 catalog）---" | tee -a "$OUT/03-during.txt"
curl -s -o /dev/null -w "POST /api/auth/login -> %{http_code} (%{time_total}s)\n" --max-time 5 -X POST "http://$USER_SVC/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"demo","password":"demo123"}' | tee -a "$OUT/03-during.txt"
echo "--- 3.3 加购（trade 需调 catalog 校验商品，应快速返回设计好的错误，而不是长时间挂起）---" | tee -a "$OUT/03-during.txt"
curl -s -w "\nPOST /api/cart -> %{http_code} (%{time_total}s)\n" --max-time 10 -X POST "http://$TRADE/api/cart" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"goodsId":"1001","quantity":1}' | tee -a "$OUT/03-during.txt"
echo "--- 3.4 下单（同样依赖 catalog 商品快照）---" | tee -a "$OUT/03-during.txt"
curl -s -w "\nPOST /api/orders -> %{http_code} (%{time_total}s)\n" --max-time 10 -X POST "http://$TRADE/api/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"items":[{"goodsId":"1001","quantity":1}],"addressId":1}' | tee -a "$OUT/03-during.txt"
echo "--- 3.5 商品列表（catalog 已停，预期失败）---" | tee -a "$OUT/03-during.txt"
curl -s -o /dev/null -w "GET /api/products -> %{http_code} (%{time_total}s)\n" --max-time 8 "http://$CATALOG/api/products" | tee -a "$OUT/03-during.txt" || echo "GET /api/products -> 连接失败（预期内，catalog 副本为 0）" | tee -a "$OUT/03-during.txt"

echo "=== 4. trade-service 日志（可见跨服务调用异常被捕获）===" | tee "$OUT/04-trade-logs.txt"
kubectl -n shop logs deploy/trade-service --tail=15 | tee -a "$OUT/04-trade-logs.txt"

echo "=== 5. 恢复 catalog-service ===" | tee "$OUT/05-recovery.txt"
kubectl -n shop scale deployment catalog-service --replicas=1 | tee -a "$OUT/05-recovery.txt"
kubectl -n shop rollout status deployment/catalog-service --timeout=120s | tee -a "$OUT/05-recovery.txt"
sleep 5
curl -s -o /dev/null -w "恢复后 GET /api/products -> %{http_code} (%{time_total}s)\n" --max-time 8 "http://$CATALOG/api/products" | tee -a "$OUT/05-recovery.txt"
kubectl -n shop get pods | tee -a "$OUT/05-recovery.txt"
echo "FAULT DONE -> $OUT"
