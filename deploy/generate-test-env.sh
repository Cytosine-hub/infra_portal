#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
COMPOSE_TEMPLATE="$ROOT_DIR/deploy/compose.env.example"
SERVICES_TEMPLATE="$ROOT_DIR/deploy/services.env.example"
COMPOSE_OUTPUT=${1:-$ROOT_DIR/deploy/compose.test.env}
SERVICES_OUTPUT=${2:-$ROOT_DIR/deploy/services.test.env}

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

random_hex() {
    bytes=$1
    od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
}

random_base64() {
    random_hex 32 | base64 | tr -d '\n'
}

set_env_value() {
    file=$1
    key=$2
    value=$3
    temp_file=$(mktemp "${TMPDIR:-/tmp}/infra-portal-env.XXXXXX")

    awk -F= -v key="$key" -v value="$value" '
        BEGIN { found = 0 }
        $1 == key {
            print key "=" value
            found = 1
            next
        }
        { print }
        END { if (!found) exit 2 }
    ' "$file" > "$temp_file" || {
        rm -f "$temp_file"
        fail "Template variable not found: $key"
    }
    mv "$temp_file" "$file"
}

[ -f "$COMPOSE_TEMPLATE" ] || fail "Missing template: $COMPOSE_TEMPLATE"
[ -f "$SERVICES_TEMPLATE" ] || fail "Missing template: $SERVICES_TEMPLATE"
[ "$COMPOSE_OUTPUT" != "$COMPOSE_TEMPLATE" ] || fail 'Compose output must not overwrite its template'
[ "$SERVICES_OUTPUT" != "$SERVICES_TEMPLATE" ] || fail 'Services output must not overwrite its template'
[ -d "$(dirname "$COMPOSE_OUTPUT")" ] || fail "Output directory not found: $(dirname "$COMPOSE_OUTPUT")"
[ -d "$(dirname "$SERVICES_OUTPUT")" ] || fail "Output directory not found: $(dirname "$SERVICES_OUTPUT")"
[ ! -e "$COMPOSE_OUTPUT" ] || fail "Refusing to overwrite existing test env: $COMPOSE_OUTPUT"
[ ! -e "$SERVICES_OUTPUT" ] || fail "Refusing to overwrite existing test env: $SERVICES_OUTPUT"

cp "$COMPOSE_TEMPLATE" "$COMPOSE_OUTPUT"
cp "$SERVICES_TEMPLATE" "$SERVICES_OUTPUT"
chmod 600 "$COMPOSE_OUTPUT" "$SERVICES_OUTPUT"

set_env_value "$COMPOSE_OUTPUT" COMPOSE_PROJECT_NAME infra-portal-test
set_env_value "$COMPOSE_OUTPUT" IMAGE_TAG test
set_env_value "$COMPOSE_OUTPUT" BUSINESS_ENV_FILE "./$(basename "$SERVICES_OUTPUT")"
set_env_value "$COMPOSE_OUTPUT" DEPLOY_DATA_DIR /app/infra-portal-test
set_env_value "$COMPOSE_OUTPUT" FRONTEND_PORT 15173
set_env_value "$COMPOSE_OUTPUT" API_GATEWAY_PORT 18080
set_env_value "$COMPOSE_OUTPUT" COMMUNITY_SERVICE_PORT 18082
set_env_value "$COMPOSE_OUTPUT" AI_SERVICE_PORT 18083
set_env_value "$COMPOSE_OUTPUT" CORE_SERVICE_PORT 18084
set_env_value "$COMPOSE_OUTPUT" MIDDLEWARE_SERVICE_PORT 18085
set_env_value "$COMPOSE_OUTPUT" DATABASE_SERVICE_PORT 18086
set_env_value "$COMPOSE_OUTPUT" HOST_SERVICE_PORT 18087
set_env_value "$COMPOSE_OUTPUT" NETWORK_SERVICE_PORT 18088
set_env_value "$COMPOSE_OUTPUT" SECURITY_SERVICE_PORT 18089
set_env_value "$COMPOSE_OUTPUT" MYSQL_PORT 13306
set_env_value "$COMPOSE_OUTPUT" NACOS_PORT 18848
set_env_value "$COMPOSE_OUTPUT" MILVUS_PORT 29530
set_env_value "$COMPOSE_OUTPUT" MILVUS_WEBUI_PORT 19091
set_env_value "$COMPOSE_OUTPUT" MINIO_API_PORT 19000
set_env_value "$COMPOSE_OUTPUT" MINIO_CONSOLE_PORT 19001
set_env_value "$COMPOSE_OUTPUT" APP_DB_PASSWORD "$(random_hex 24)"
set_env_value "$COMPOSE_OUTPUT" NACOS_AUTH_TOKEN "$(random_base64)"
set_env_value "$COMPOSE_OUTPUT" NACOS_AUTH_IDENTITY_KEY "test-$(random_hex 16)"
set_env_value "$COMPOSE_OUTPUT" NACOS_AUTH_IDENTITY_VALUE "$(random_hex 24)"
set_env_value "$COMPOSE_OUTPUT" MINIO_ROOT_PASSWORD "$(random_hex 24)"

set_env_value "$SERVICES_OUTPUT" GATEWAY_SIGNING_SECRET "$(random_hex 32)"
set_env_value "$SERVICES_OUTPUT" ADMIN_DEFAULT_PASSWORD "$(random_hex 24)"
set_env_value "$SERVICES_OUTPUT" AI_API_KEY "test-$(random_hex 24)"
set_env_value "$SERVICES_OUTPUT" EMBEDDING_API_KEY "test-$(random_hex 24)"
set_env_value "$SERVICES_OUTPUT" WIKI_EXPORT_SIGNATURE_SECRET "$(random_hex 32)"
set_env_value "$SERVICES_OUTPUT" ZABBIX_PASSWORD "$(random_hex 24)"

printf '%s\n' "* Task complete: generated test env files"
printf '| Compose env: %s\n' "$COMPOSE_OUTPUT"
printf '| Services env: %s\n' "$SERVICES_OUTPUT"
