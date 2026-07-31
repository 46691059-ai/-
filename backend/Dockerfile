# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21-alpine
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace

COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl backend -am dependency:go-offline

COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl backend -am clean package -DskipTests

FROM ${RUNTIME_IMAGE} AS runtime
LABEL org.opencontainers.image.title="enterprise-platform-backend" \
      org.opencontainers.image.description="国企数字化治理与经营赋能平台后端"

RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

WORKDIR /app
COPY --from=build --chown=app:app \
    /workspace/backend/target/enterprise-platform.jar /app/application.jar

USER 10001:10001
EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
