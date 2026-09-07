param(
    [string]$ImageTag = '20260826-test',
    [Parameter(Mandatory = $true)][string]$DbPassword,
    [Parameter(Mandatory = $true)][string]$MysqlRootPassword
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

kubectl create namespace newsecondmall --dry-run=client -o yaml | kubectl apply -f -
kubectl -n newsecondmall create configmap newsecondmall-db-schema --from-file=db.sql=shopping_back/shopping_back/doc/db.sql --dry-run=client -o yaml | kubectl apply -f -
kubectl -n newsecondmall create secret generic newsecondmall-secrets --from-literal=DB_PASSWORD=$DbPassword --from-literal=MYSQL_ROOT_PASSWORD=$MysqlRootPassword --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/mysql.yaml
kubectl apply -f k8s/backend.yaml
kubectl apply -f k8s/frontend.yaml
kubectl -n newsecondmall set image deployment/backend backend=newsecondmall-backend:$ImageTag
kubectl -n newsecondmall set image deployment/frontend frontend=newsecondmall-frontend:$ImageTag
kubectl -n newsecondmall rollout status deployment/mysql --timeout=180s
kubectl -n newsecondmall rollout status deployment/backend --timeout=180s
kubectl -n newsecondmall rollout status deployment/frontend --timeout=180s
kubectl -n newsecondmall get pods,svc
