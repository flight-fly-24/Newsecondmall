ARG DOCKER_HUB=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io

# 本地 docker compose up --build 用的一键构建镜像：
# 构建阶段从源码编译成可执行 jar，运行阶段只保留 jre。
# MODULE_DIR 相对仓库根（例如 services/user-service），JAR_NAME 为打包产物文件名。
FROM ${DOCKER_HUB}/library/maven:3-eclipse-temurin-17 AS build
ARG MODULE_DIR
WORKDIR /build
COPY services/common/pom.xml ./common/pom.xml
COPY services/common/src ./common/src
RUN mvn -B -ntp -f common/pom.xml -DskipTests install
COPY ${MODULE_DIR}/pom.xml ./pom.xml
COPY ${MODULE_DIR}/src ./src
RUN mvn -B -ntp -DskipTests package

FROM ${DOCKER_HUB}/library/eclipse-temurin:17-jre-alpine
ARG JAR_NAME
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/${JAR_NAME} /app/app.jar
RUN chown -R app:app /app
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
