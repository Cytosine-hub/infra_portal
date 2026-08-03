# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace/backend
COPY backend/ ./

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -Dstyle.color=never clean verify

# SERVICE 在全量验证后声明，保证同一提交的 9 个服务复用上面的 Maven 构建层。
ARG SERVICE
RUN test -n "$SERVICE" \
    && cp "$SERVICE"/target/*-exec.jar /workspace/app.jar

FROM eclipse-temurin:17-jre

ARG IMAGE_REVISION=unknown
ARG IMAGE_VERSION=local

LABEL org.opencontainers.image.revision="$IMAGE_REVISION" \
      org.opencontainers.image.version="$IMAGE_VERSION"

WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/data/skills /app/logs /app/storage
COPY --from=build /workspace/app.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
