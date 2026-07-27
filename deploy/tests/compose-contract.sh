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

assert_no_text() {
    if grep -Eq "$2" "$ROOT_DIR/$1"; then
        fail "$3 unexpected pattern $2"
    fi
    pass "$3"
}

printf '%s\n' '+ Task start: Docker Compose contract'
assert_file deploy/compose.env.example 'TC-DOCKER-001'
assert_file deploy/Dockerfile.frontend 'TC-DOCKER-002'
assert_file deploy/nginx.conf 'TC-DOCKER-003'
assert_file deploy/nacos-init.sh 'TC-DOCKER-004'
assert_text deploy/Dockerfile.nacos-init '^COPY deploy/nacos-config /config$' 'TC-DOCKER-029'
assert_no_text deploy/docker-compose.yml 'nacos-config:/config' 'TC-DOCKER-030'
assert_text deploy/docker-compose.yml '^  frontend:' 'TC-DOCKER-005'
assert_text deploy/docker-compose.yml '^  nacos-init:' 'TC-DOCKER-006'
assert_text deploy/docker-compose.yml 'condition: service_completed_successfully' 'TC-DOCKER-007'
assert_text deploy/docker-compose.yml '../db/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro' 'TC-DOCKER-008'
assert_text deploy/docker-compose.yml '../db/seed.sql:/docker-entrypoint-initdb.d/02-seed.sql:ro' 'TC-DOCKER-009'

for service in api-gateway core-service ai-service community-service middleware-service \
    database-service host-service network-service security-service; do
    assert_file "deploy/nacos-config/$service.properties" "TC-DOCKER-010 $service"
done

assert_text deploy/nginx.conf 'proxy_pass http://api-gateway:8080' 'TC-DOCKER-011'
assert_text .gitignore '^/deploy/compose.env$' 'TC-DOCKER-012'
assert_text deploy/docker-compose.yml 'http://127\.0\.0\.1/' 'TC-DOCKER-025'
assert_text deploy/smoke-test.sh 'entrypoint jq' 'TC-DOCKER-026'
assert_text deploy/docker-compose.yml '127\.0\.0\.1:\$\{MILVUS_PORT:-19530\}:19530' 'TC-DOCKER-027 milvus'
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
