#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
image_tag="${IMAGE_TAG:?IMAGE_TAG is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

cd "$root_dir"
kubectl apply -f k8s/namespace.yaml
kubectl -n newsecondmall create configmap newsecondmall-db-schema \
  --from-file=db.sql=shopping_back/shopping_back/doc/db.sql \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n newsecondmall create secret generic newsecondmall-secrets \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/mysql.yaml -f k8s/backend.yaml -f k8s/frontend.yaml
kubectl -n newsecondmall set image deployment/backend backend="${BACKEND_IMAGE_REPOSITORY:-newsecondmall-backend}:$image_tag"
kubectl -n newsecondmall set image deployment/frontend frontend="${FRONTEND_IMAGE_REPOSITORY:-newsecondmall-frontend}:$image_tag"
kubectl -n newsecondmall rollout status deployment/mysql --timeout=180s
kubectl -n newsecondmall rollout status deployment/backend --timeout=180s
kubectl -n newsecondmall rollout status deployment/frontend --timeout=180s
kubectl -n newsecondmall get pods,svc
