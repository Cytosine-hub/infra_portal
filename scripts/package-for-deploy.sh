#!/bin/bash
# 一键打包生产部署包
set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VERSION=$(date +%Y%m%d)
PACKAGE_NAME="middleware-resource-manager-${VERSION}"
OUTPUT_DIR="${PROJECT_DIR}/release/${PACKAGE_NAME}"

echo "=== 打包生产部署包: ${PACKAGE_NAME} ==="

# 1. 构建后端
echo "[1/4] 构建后端..."
cd "$PROJECT_DIR/backend"
mvn clean package -DskipTests -q
echo "  网关 JAR: backend/api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar"
echo "  论坛 JAR: backend/community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar"
echo "  AI JAR: backend/ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar"
echo "  平台核心 JAR: backend/core-service/target/core-service-0.0.1-SNAPSHOT-exec.jar"
echo "  中间件岗位 JAR: backend/middleware-service/target/middleware-service-0.0.1-SNAPSHOT-exec.jar"
echo "  数据库岗位 JAR: backend/database-service/target/database-service-0.0.1-SNAPSHOT-exec.jar"
echo "  主机岗位 JAR: backend/host-service/target/host-service-0.0.1-SNAPSHOT-exec.jar"
echo "  网络岗位 JAR: backend/network-service/target/network-service-0.0.1-SNAPSHOT-exec.jar"
echo "  网络安全岗位 JAR: backend/security-service/target/security-service-0.0.1-SNAPSHOT-exec.jar"

# 2. 构建前端
echo "[2/4] 构建前端..."
cd "$PROJECT_DIR/frontend"
npm install -q 2>/dev/null
npm run build
echo "  前端产物: frontend/dist/"

# 3. 组装部署包
echo "[3/4] 组装部署包..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/backend" "$OUTPUT_DIR/frontend/dist" "$OUTPUT_DIR/db" "$OUTPUT_DIR/docs" "$OUTPUT_DIR/systemd"

# 后端
cp "$PROJECT_DIR/backend/api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/core-service/target/core-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/middleware-service/target/middleware-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/database-service/target/database-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/host-service/target/host-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/network-service/target/network-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"
cp "$PROJECT_DIR/backend/security-service/target/security-service-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"

# 前端
cp -r "$PROJECT_DIR/frontend/dist/"* "$OUTPUT_DIR/frontend/dist/"

# DDL
cp "$PROJECT_DIR/backend/knowledge/src/main/resources/db/knowledge_ddl.sql" "$OUTPUT_DIR/db/"

# 文档
cp "$PROJECT_DIR/docs/production-deploy.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/docs/startup-manual.md" "$OUTPUT_DIR/docs/" 2>/dev/null || true
cp "$PROJECT_DIR/docs/microservices-stage1-gateway-nacos.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/docs/microservices-stage2-community-service.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/docs/microservices-stage3-ai-service.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/docs/microservices-stage4-core-service.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/docs/microservices-stage5-gateway-authentication.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/docs/microservices-stage6-job-services.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/scripts/api-gateway.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/community-service.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/ai-service.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/core-service.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/middleware-service.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/database-service.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/host-service.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/network-service.service" "$OUTPUT_DIR/systemd/"
cp "$PROJECT_DIR/scripts/security-service.service" "$OUTPUT_DIR/systemd/"

# 默认配置（供参考）
cp "$PROJECT_DIR/backend/api-gateway/src/main/resources/application.yml" "$OUTPUT_DIR/backend/gateway-application.yml.example"
cp "$PROJECT_DIR/backend/api-gateway/src/main/resources/application-cloud.yml" "$OUTPUT_DIR/backend/gateway-application-cloud.yml.example"
cp "$PROJECT_DIR/backend/community-service/src/main/resources/application.yml" "$OUTPUT_DIR/backend/community-application.yml.example"
cp "$PROJECT_DIR/backend/community-service/src/main/resources/application-cloud.yml" "$OUTPUT_DIR/backend/community-application-cloud.yml.example"
cp "$PROJECT_DIR/backend/ai-service/src/main/resources/application.yml" "$OUTPUT_DIR/backend/ai-application.yml.example"
cp "$PROJECT_DIR/backend/ai-service/src/main/resources/application-cloud.yml" "$OUTPUT_DIR/backend/ai-application-cloud.yml.example"
cp "$PROJECT_DIR/backend/core-service/src/main/resources/application.yml" "$OUTPUT_DIR/backend/core-application.yml.example"
cp "$PROJECT_DIR/backend/core-service/src/main/resources/application-cloud.yml" "$OUTPUT_DIR/backend/core-application-cloud.yml.example"
cp "$PROJECT_DIR/scripts/services.env.example" "$OUTPUT_DIR/backend/services.env.example"
for service in middleware database host network security; do
    cp "$PROJECT_DIR/backend/${service}-service/src/main/resources/application.yml" "$OUTPUT_DIR/backend/${service}-application.yml.example"
    cp "$PROJECT_DIR/backend/${service}-service/src/main/resources/application-cloud.yml" "$OUTPUT_DIR/backend/${service}-application-cloud.yml.example"
done

# 4. 压缩
echo "[4/4] 压缩..."
cd "$PROJECT_DIR/release"
tar czf "${PACKAGE_NAME}.tar.gz" "$PACKAGE_NAME"
echo ""
echo "=== 完成 ==="
echo "部署包: release/${PACKAGE_NAME}.tar.gz"
echo ""
echo "包内容:"
find "$OUTPUT_DIR" -type f | sed "s|$OUTPUT_DIR/||" | sort
