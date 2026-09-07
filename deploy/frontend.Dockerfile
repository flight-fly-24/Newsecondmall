ARG DOCKER_HUB=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io
FROM ${DOCKER_HUB}/library/node:18-alpine AS build

WORKDIR /build

COPY shopping_front/package.json shopping_front/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY shopping_front/ ./
RUN npm run build:h5

ARG DOCKER_HUB=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io
FROM ${DOCKER_HUB}/library/nginx:1.27-alpine

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/build/h5/ /usr/share/nginx/html/
COPY shopping_front/static/ /usr/share/nginx/html/static/

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O - http://127.0.0.1/ >/dev/null || exit 1
