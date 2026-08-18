#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)

pass() {
    printf '| Subtask success: %s\n' "$1"
}

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

assert_file() {
    [ -f "$ROOT_DIR/$1" ] || fail "$2 missing file $1"
    pass "$2"
}

assert_text() {
    grep -Eq "$2" "$ROOT_DIR/$1" || fail "$3 missing pattern $2"
    pass "$3"
}

assert_fixed_text() {
    grep -Fq "$2" "$ROOT_DIR/$1" || fail "$3 missing text $2"
    pass "$3"
}

assert_no_text() {
    if grep -Eq "$2" "$ROOT_DIR/$1"; then
        fail "$3 unexpected pattern $2"
    fi
    pass "$3"
}

printf '%s\n' '+ Task start: Docker Compose contract'
assert_no_text deploy/compose.env.example '^COMPOSE_PROJECT_NAME=' \
    'TC-DOCKER-036 reserved Compose project variable excluded'
assert_text deploy/Dockerfile.nacos-init '^COPY deploy/nacos-config /config$' 'TC-DOCKER-029'
assert_no_text deploy/docker-compose.dependencies.yml 'nacos-config:/config' 'TC-DOCKER-030'
assert_text deploy/docker-compose.yml '^  frontend:' 'TC-DOCKER-005'
assert_text deploy/docker-compose.yml '^x-shanghai-timezone: &shanghai-timezone$' \
    'TC-DOCKER-039 business timezone anchor'
assert_text deploy/docker-compose.yml '^  TZ: Asia/Shanghai$' \
    'TC-DOCKER-039 business Shanghai timezone'
assert_text deploy/docker-compose.dependencies.yml '^x-shanghai-timezone: &shanghai-timezone$' \
    'TC-DOCKER-039 dependency timezone anchor'
assert_text deploy/docker-compose.dependencies.yml '^  TZ: Asia/Shanghai$' \
    'TC-DOCKER-039 dependency Shanghai timezone'
assert_text deploy/milvus-offline/docker-compose.yml '^x-shanghai-timezone: &shanghai-timezone$' \
    'TC-DOCKER-039 offline Milvus timezone anchor'
assert_text deploy/milvus-offline/docker-compose.yml '^  TZ: Asia/Shanghai$' \
    'TC-DOCKER-039 offline Milvus Shanghai timezone'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    '/usr/share/zoneinfo/Asia/Shanghai:/usr/share/zoneinfo/Asia/Shanghai:ro' \
    'TC-DOCKER-039 dependency MinIO Shanghai zoneinfo'
assert_fixed_text deploy/milvus-offline/docker-compose.yml \
    '/usr/share/zoneinfo/Asia/Shanghai:/usr/share/zoneinfo/Asia/Shanghai:ro' \
    'TC-DOCKER-039 offline MinIO Shanghai zoneinfo'
assert_text scripts/services.env.example '^TZ=Asia/Shanghai$' \
    'TC-DOCKER-039 systemd service Shanghai timezone'
for service_path in "$ROOT_DIR"/scripts/*.service; do
    service_file=${service_path#"$ROOT_DIR"/}
    assert_text "$service_file" '^Environment=TZ=Asia/Shanghai$' \
        "TC-DOCKER-039 $service_file Shanghai timezone"
done
assert_no_text deploy/docker-compose.yml \
    '^  (mysql|nacos|nacos-init|ollama|ollama-init|etcd|minio|milvus):' \
    'TC-DOCKER-032 business compose excludes dependencies'
assert_text deploy/docker-compose.dependencies.yml '^  nacos-init:' 'TC-DOCKER-006'
assert_text deploy/docker-compose.dependencies.yml '^  ollama:' \
    'TC-DOCKER-040 Ollama dependency service'
assert_text deploy/docker-compose.dependencies.yml '^  ollama-init:' \
    'TC-DOCKER-040 Ollama model initializer'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    'ollama/ollama:${OLLAMA_VERSION:-0.24.0}' \
    'TC-DOCKER-040 pinned Ollama image'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    '${DEPLOY_DATA_ROOT:-./data}/dependencies/ollama:/root/.ollama' \
    'TC-DOCKER-040 Ollama model persistence'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    'OLLAMA_NUM_PARALLEL: ${OLLAMA_NUM_PARALLEL:-1}' \
    'TC-DOCKER-040 Ollama single request concurrency'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    'OLLAMA_MAX_LOADED_MODELS: ${OLLAMA_MAX_LOADED_MODELS:-1}' \
    'TC-DOCKER-040 Ollama single loaded model'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    'cpus: ${OLLAMA_CPUS:-6.0}' \
    'TC-DOCKER-040 Ollama CPU limit'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    'mem_limit: ${OLLAMA_MEMORY_LIMIT:-3g}' \
    'TC-DOCKER-040 Ollama memory limit'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    'OLLAMA_HOST: http://ollama:11434' \
    'TC-DOCKER-040 Ollama initializer endpoint'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    'command: ["pull", "${OLLAMA_EMBEDDING_MODEL:-bge-m3}"]' \
    'TC-DOCKER-040 bge-m3 model initialization'
assert_no_text deploy/docker-compose.dependencies.yml \
    '11434:11434' \
    'TC-DOCKER-040 Ollama has no host port exposure'
assert_fixed_text deploy/smoke-test.sh \
    'dependency_compose exec -T ollama ollama show "$OLLAMA_EMBEDDING_MODEL"' \
    'TC-DOCKER-040 Ollama model smoke check'
assert_no_text deploy/docker-compose.dependencies.yml \
    '^  (api-gateway|core-service|ai-service|community-service|middleware-service|database-service|host-service|network-service|security-service|frontend):' \
    'TC-DOCKER-032 dependency compose excludes business services'
assert_text deploy/docker-compose.dependencies.yml 'condition: service_healthy' 'TC-DOCKER-007'
assert_fixed_text deploy/docker-compose.dependencies.yml '${MYSQL_INIT_DIR:-../db}/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro' 'TC-DOCKER-008'
assert_fixed_text deploy/docker-compose.dependencies.yml '${MYSQL_INIT_DIR:-../db}/seed.sql:/docker-entrypoint-initdb.d/02-seed.sql:ro' 'TC-DOCKER-009'
assert_text deploy/docker-compose.dependencies.yml 'name: \$\{COMPOSE_NETWORK_NAME:-infra-portal-network\}' \
    'TC-DOCKER-033 dependency shared network'
assert_text deploy/docker-compose.yml \
    '^name: \$\{COMPOSE_BUSINESS_PROJECT_NAME:-infra-portal\}$' \
    'TC-DOCKER-036 independent business project name'
assert_text deploy/docker-compose.yml 'external: true' 'TC-DOCKER-033 business external network'
assert_no_text deploy/docker-compose.yml '\$\{[^}]+:\?' 'TC-DOCKER-034 business interpolation defaults'
assert_no_text deploy/docker-compose.dependencies.yml '\$\{[^}]+:\?' \
    'TC-DOCKER-034 dependency interpolation defaults'
assert_text deploy/docker-compose.yml 'required: false' 'TC-DOCKER-035 optional business env file'
assert_text deploy/compose.env.example '^DEPLOY_DATA_ROOT=/app/infra-portal/data$' \
    'TC-DOCKER-037 separated data root'
assert_no_text deploy/compose.env.example '^DEPLOY_DATA_DIR=' \
    'TC-DOCKER-037 retired data directory variable'
assert_fixed_text deploy/docker-compose.dependencies.yml \
    '${DEPLOY_DATA_ROOT:-./data}/dependencies/mysql:/var/lib/mysql' \
    'TC-DOCKER-037 dependency data hierarchy'
assert_fixed_text deploy/docker-compose.yml \
    '${DEPLOY_DATA_ROOT:-./data}/business/core-service/storage:/app/storage' \
    'TC-DOCKER-037 business data hierarchy'
assert_text db/init.sql '^SET NAMES utf8mb4;$' 'TC-DOCKER-031 init SQL character set'
assert_text db/seed.sql '^SET NAMES utf8mb4;$' 'TC-DOCKER-031 seed SQL character set'
assert_text db/init.sql '^CREATE TABLE `api_audit_log` \($' 'TC-DOCKER-033 API audit table'

for service in api-gateway core-service ai-service community-service middleware-service \
    database-service host-service network-service security-service; do
    assert_file "deploy/nacos-config/$service.properties" "TC-DOCKER-010 $service"
done

assert_text deploy/nginx.conf '^    set \$api_gateway_upstream http://api-gateway:8080;$' \
    'TC-DOCKER-011 dynamic Gateway upstream'
assert_text deploy/nginx.conf '^        proxy_pass \$api_gateway_upstream;$' \
    'TC-DOCKER-011 Gateway proxy usage'
assert_text .gitignore '^/deploy/compose.env$' 'TC-DOCKER-012'
assert_text deploy/docker-compose.yml 'http://127\.0\.0\.1/' 'TC-DOCKER-025'
assert_text deploy/docker-compose.dependencies.yml '127\.0\.0\.1:\$\{MILVUS_PORT:-19530\}:19530' 'TC-DOCKER-027 milvus'
assert_text deploy/docker-compose.dependencies.yml \
    '\$\{NACOS_BIND_ADDRESS:-0\.0\.0\.0\}:\$\{NACOS_PORT:-8848\}:8848' \
    'TC-DOCKER-037 Nacos LAN bind address'
assert_text deploy/compose.env.example '^NACOS_BIND_ADDRESS=0\.0\.0\.0$' \
    'TC-DOCKER-037 Nacos LAN bind env'
assert_text deploy/compose.env.example '^OLLAMA_VERSION=0\.24\.0$' \
    'TC-DOCKER-040 Ollama version env'
assert_text deploy/compose.env.example '^OLLAMA_EMBEDDING_MODEL=bge-m3$' \
    'TC-DOCKER-040 Ollama model env'
assert_text deploy/docker-compose.dependencies.yml \
    'milvusdb/milvus:\$\{MILVUS_VERSION:-v2\.5\.10\}' \
    'TC-DOCKER-038 Milvus hybrid-search compatible version'
assert_text deploy/milvus-offline/docker-compose.yml \
    'milvusdb/milvus:\$\{MILVUS_VERSION:-v2\.5\.10\}' \
    'TC-DOCKER-038 offline Milvus compatible version'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{COMMUNITY_SERVICE_PORT:-8082\}:8082' 'TC-DOCKER-027 community'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{AI_SERVICE_PORT:-8083\}:8083' 'TC-DOCKER-027 ai'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{CORE_SERVICE_PORT:-8084\}:8084' 'TC-DOCKER-027 core'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{MIDDLEWARE_SERVICE_PORT:-8085\}:8085' 'TC-DOCKER-027 middleware'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{DATABASE_SERVICE_PORT:-8086\}:8086' 'TC-DOCKER-027 database'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{HOST_SERVICE_PORT:-8087\}:8087' 'TC-DOCKER-027 host'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{NETWORK_SERVICE_PORT:-8088\}:8088' 'TC-DOCKER-027 network'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{SECURITY_SERVICE_PORT:-8089\}:8089' 'TC-DOCKER-027 security'
assert_text deploy/nacos-config/ai-service.properties '^langchain4j\.open-ai\.chat-model\.log-requests=false$' \
    'TC-DOCKER-028 requests'
assert_text deploy/nacos-config/ai-service.properties '^langchain4j\.open-ai\.chat-model\.log-responses=false$' \
    'TC-DOCKER-028 responses'
printf '%s\n' '* Task complete: Docker Compose contract'
