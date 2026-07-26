# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace/backend
COPY backend/ ./

ARG SERVICE
RUN --mount=type=cache,target=/root/.m2 \
    test -n "$SERVICE" \
    && mvn --batch-mode --no-transfer-progress -Dstyle.color=never -pl "$SERVICE" -am clean verify \
    && cp "$SERVICE"/target/*-exec.jar /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/data/skills /app/logs /app/storage
COPY --from=build /workspace/app.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
