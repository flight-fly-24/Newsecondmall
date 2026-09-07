ARG DOCKER_HUB=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io
FROM ${DOCKER_HUB}/library/maven:3-eclipse-temurin-17 AS build

WORKDIR /build

COPY shopping_back/shopping_back/pom.xml ./pom.xml
COPY shopping_back/shopping_back/src ./src
# CodeArts docker 插件是经典 docker build，没有 BuildKit cache mount。
# 单元测试已在流水线前序卡片跑过，镜像构建只打包。
RUN mvn -B -ntp -DskipTests package

ARG DOCKER_HUB=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io
FROM ${DOCKER_HUB}/library/eclipse-temurin:17-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /build/target/shopping_back-*.jar /app/app.jar

RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/api/products >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
