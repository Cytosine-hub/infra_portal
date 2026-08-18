#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
COMPOSE_ENV_FILE=${COMPOSE_ENV_FILE:-$ROOT_DIR/deploy/compose.env}
COMPOSE_FILE=${COMPOSE_FILE:-$ROOT_DIR/deploy/docker-compose.yml}
DEPENDENCIES_COMPOSE_FILE=${DEPENDENCIES_COMPOSE_FILE:-$ROOT_DIR/deploy/docker-compose.dependencies.yml}
SMOKE_TIMEOUT_SECONDS=${SMOKE_TIMEOUT_SECONDS:-600}

pass() {
    printf '| Subtask success: %s\n' "$1"
}

fail() {
    service=${2:-}
    printf '%s\n' "- Subtask failure: $1" >&2
    business_compose ps >&2 || true
    dependency_compose ps --all >&2 || true
    if [ -n "$service" ]; then
        service_compose "$service" logs --tail 100 "$service" >&2 || true
    fi
    exit 1
}

business_compose() {
    docker compose --env-file "$COMPOSE_ENV_FILE" --file "$COMPOSE_FILE" "$@"
}

dependency_compose() {
    docker compose --env-file "$COMPOSE_ENV_FILE" --file "$DEPENDENCIES_COMPOSE_FILE" "$@"
}

service_compose() {
    service=$1
    shift
    case "$service" in
        mysql|nacos|nacos-init|ollama|ollama-init|etcd|minio|milvus)
            dependency_compose "$@"
            ;;
        *)
            business_compose "$@"
            ;;
    esac
}

json_query() {
    dependency_compose run --rm --no-deps --entrypoint jq -T nacos-init "$@"
}

wait_for_service() {
    service=$1
    deadline=$(( $(date +%s) + SMOKE_TIMEOUT_SECONDS ))

    while [ "$(date +%s)" -le "$deadline" ]; do
        container_id=$(service_compose "$service" ps --quiet "$service" 2>/dev/null || true)
        if [ -n "$container_id" ]; then
            state=$(docker inspect --format '{{.State.Status}}' "$container_id" 2>/dev/null || true)
            health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
                "$container_id" 2>/dev/null || true)
            if [ "$state" = running ] && { [ "$health" = healthy ] || [ "$health" = none ]; }; then
                return 0
            fi
        fi
        sleep 2
    done
    return 1
}

[ -f "$COMPOSE_ENV_FILE" ] || fail "Compose environment file not found: $COMPOSE_ENV_FILE"
[ -f "$COMPOSE_FILE" ] || fail "Compose file not found: $COMPOSE_FILE"
[ -f "$DEPENDENCIES_COMPOSE_FILE" ] \
    || fail "Dependency Compose file not found: $DEPENDENCIES_COMPOSE_FILE"

set -a
# shellcheck disable=SC1090
. "$COMPOSE_ENV_FILE"
set +a

FRONTEND_PORT=${FRONTEND_PORT:-5173}
NACOS_PORT=${NACOS_PORT:-8848}
NACOS_CONFIG_GROUP=${NACOS_CONFIG_GROUP:-DEFAULT_GROUP}
NACOS_DISCOVERY_GROUP=${NACOS_DISCOVERY_GROUP:-DEFAULT_GROUP}
NACOS_NAMESPACE=${NACOS_NAMESPACE:-}
NACOS_USERNAME=${NACOS_USERNAME:-nacos}
NACOS_PASSWORD=${NACOS_PASSWORD:-nacos}
OLLAMA_EMBEDDING_MODEL=${OLLAMA_EMBEDDING_MODEL:-bge-m3}

long_running_services='mysql nacos ollama etcd minio milvus api-gateway core-service ai-service community-service middleware-service database-service host-service network-service security-service frontend'
java_services='api-gateway core-service ai-service community-service middleware-service database-service host-service network-service security-service'

printf '%s\n' '+ Task start: Docker Compose full-stack smoke test'

for service in $long_running_services; do
    wait_for_service "$service" || fail "TC-DOCKER-018 service not healthy: $service" "$service"
done
pass 'TC-DOCKER-018'

dependency_compose exec -T ollama ollama show "$OLLAMA_EMBEDDING_MODEL" >/dev/null \
    || fail "TC-DOCKER-040 Ollama model is unavailable: $OLLAMA_EMBEDDING_MODEL" ollama
pass 'TC-DOCKER-040'

nacos_init_output=$(dependency_compose run --rm --no-deps nacos-init 2>&1) \
    || fail 'TC-DOCKER-019 nacos-init failed' nacos-init
pass 'TC-DOCKER-019'

nacos_url="http://127.0.0.1:$NACOS_PORT"
login_response=$(curl --fail --silent --show-error \
    --request POST \
    --data-urlencode "username=$NACOS_USERNAME" \
    --data-urlencode "password=$NACOS_PASSWORD" \
    "$nacos_url/nacos/v1/auth/users/login") \
    || fail 'TC-DOCKER-020 Nacos login failed' nacos
access_token=$(printf '%s' "$login_response" | json_query -er '.accessToken // empty') \
    || fail 'TC-DOCKER-020 Nacos login response missing access token' nacos

for service in $java_services; do
    curl --fail --silent --show-error --get \
        --data-urlencode "accessToken=$access_token" \
        --data-urlencode "dataId=$service.properties" \
        --data-urlencode "group=$NACOS_CONFIG_GROUP" \
        --data-urlencode "tenant=$NACOS_NAMESPACE" \
        "$nacos_url/nacos/v1/cs/configs" >/dev/null \
        || fail "TC-DOCKER-020 missing Data ID: $service.properties" nacos
done
pass 'TC-DOCKER-020'

service_list=$(curl --fail --silent --show-error --get \
    --data-urlencode "accessToken=$access_token" \
    --data-urlencode 'pageNo=1' \
    --data-urlencode 'pageSize=100' \
    --data-urlencode "groupName=$NACOS_DISCOVERY_GROUP" \
    --data-urlencode "namespaceId=$NACOS_NAMESPACE" \
    "$nacos_url/nacos/v1/ns/service/list") \
    || fail 'TC-DOCKER-021 failed to query Nacos services' nacos

for service in $java_services; do
    printf '%s' "$service_list" \
        | json_query -e --arg service "$service" \
            '(.doms // []) | any(. == $service or endswith("@@" + $service))' >/dev/null \
        || fail "TC-DOCKER-021 service not registered: $service" "$service"
done
pass 'TC-DOCKER-021'

curl --fail --silent --show-error "http://127.0.0.1:$FRONTEND_PORT/" >/dev/null \
    || fail 'TC-DOCKER-022 frontend root is unavailable' frontend
pass 'TC-DOCKER-022'

curl --fail --silent --show-error \
    "http://127.0.0.1:$FRONTEND_PORT/api/public/releases" >/dev/null \
    || fail 'TC-DOCKER-023 frontend API proxy is unavailable' frontend
pass 'TC-DOCKER-023'

repeat_output=$(dependency_compose run --rm --no-deps nacos-init 2>&1) \
    || fail 'TC-DOCKER-024 repeated nacos-init failed' nacos-init
skip_count=$(printf '%s\n' "$repeat_output" | grep -c 'SKIP dataId=' || true)
[ "$skip_count" -eq 9 ] \
    || fail "TC-DOCKER-024 expected 9 skipped Data IDs, got $skip_count" nacos-init
pass 'TC-DOCKER-024'

printf '%s\n' '* Task complete: Docker Compose full-stack smoke test'
