#!/bin/bash
cat > /tmp/reg.json << 'EOF'
{"username":"deploycheck02","password":"test123","phone":"13800138002","role":"buyer"}
EOF
cat > /tmp/login.json << 'EOF'
{"username":"deploycheck02","password":"test123"}
EOF
POD=$(sudo kubectl -n shop get pods -l app.kubernetes.io/name=user-service -o jsonpath='{.items[0].metadata.name}')
echo "POD=$POD"
sudo kubectl -n shop cp /tmp/reg.json "$POD:/tmp/reg.json"
sudo kubectl -n shop cp /tmp/login.json "$POD:/tmp/login.json"
echo "=== HEALTH ==="
sudo kubectl -n shop exec "$POD" -- curl -s http://localhost:8081/actuator/health
echo ""
echo "=== REGISTER ==="
sudo kubectl -n shop exec "$POD" -- curl -s -X POST http://localhost:8081/api/auth/register -H 'Content-Type: application/json' -d @/tmp/reg.json
echo ""
echo "=== LOGIN ==="
sudo kubectl -n shop exec "$POD" -- curl -s -X POST http://localhost:8081/api/auth/login -H 'Content-Type: application/json' -d @/tmp/login.json
echo ""
