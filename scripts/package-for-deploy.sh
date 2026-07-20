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
echo "  后端 JAR: backend/app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar"

# 2. 构建前端
echo "[2/4] 构建前端..."
cd "$PROJECT_DIR/frontend"
npm install -q 2>/dev/null
npm run build
echo "  前端产物: frontend/dist/"

# 3. 组装部署包
echo "[3/4] 组装部署包..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/backend" "$OUTPUT_DIR/frontend/dist" "$OUTPUT_DIR/db" "$OUTPUT_DIR/docs"

# 后端
cp "$PROJECT_DIR/backend/app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar" "$OUTPUT_DIR/backend/"

# 前端
cp -r "$PROJECT_DIR/frontend/dist/"* "$OUTPUT_DIR/frontend/dist/"

# DDL
cp "$PROJECT_DIR/backend/knowledge/src/main/resources/db/knowledge_ddl.sql" "$OUTPUT_DIR/db/"

# 文档
cp "$PROJECT_DIR/docs/production-deploy.md" "$OUTPUT_DIR/docs/"
cp "$PROJECT_DIR/docs/startup-manual.md" "$OUTPUT_DIR/docs/" 2>/dev/null || true

# 默认配置（供参考）
cp "$PROJECT_DIR/backend/app/src/main/resources/application.yml" "$OUTPUT_DIR/backend/application.yml.example"

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
