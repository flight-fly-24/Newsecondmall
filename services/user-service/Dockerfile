FROM eclipse-temurin:17-jre
ARG JAR_FILE=target/user-service-1.0.0.jar
COPY ${JAR_FILE} /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
