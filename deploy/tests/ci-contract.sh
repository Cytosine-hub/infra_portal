#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/infra-portal-ci-test.XXXXXX")
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

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

env_value() {
    file=$1
    key=$2
    sed -n "s/^${key}=//p" "$file" | tail -1
}

assert_nonempty_env() {
    file=$1
    key=$2
    value=$(env_value "$file" "$key")
    [ -n "$value" ] || fail "TC-CI-002 empty generated value: $key"
}

printf '%s\n' '+ Task start: GitLab CI contract'

assert_file deploy/generate-test-env.sh 'TC-CI-001'

sh "$ROOT_DIR/deploy/generate-test-env.sh" \
    "$TMP_ROOT/compose.test.env" "$TMP_ROOT/services.test.env"

for key in APP_DB_PASSWORD NACOS_PASSWORD NACOS_AUTH_TOKEN \
    NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE MINIO_ROOT_PASSWORD; do
    assert_nonempty_env "$TMP_ROOT/compose.test.env" "$key"
done
for key in GATEWAY_SIGNING_SECRET ADMIN_DEFAULT_PASSWORD AI_API_KEY \
    EMBEDDING_API_KEY EMBEDDING_BASE_URL EMBEDDING_MODEL EMBEDDING_MAX_TOKENS \
    WIKI_EXPORT_SIGNATURE_SECRET ZABBIX_PASSWORD; do
    assert_nonempty_env "$TMP_ROOT/services.test.env" "$key"
done
grep -Eq '^# .*测试' "$TMP_ROOT/compose.test.env" \
    || fail 'TC-CI-002 generated compose env lacks test-purpose comment'
grep -Eq '^# .*测试' "$TMP_ROOT/services.test.env" \
    || fail 'TC-CI-002 generated services env lacks test-purpose comment'
pass 'TC-CI-002'

gateway_secret=$(env_value "$TMP_ROOT/services.test.env" GATEWAY_SIGNING_SECRET)
[ "${#gateway_secret}" -ge 64 ] \
    || fail 'TC-CI-003 gateway signing secret is shorter than 64 characters'
pass 'TC-CI-003'

sh "$ROOT_DIR/deploy/generate-test-env.sh" \
    "$TMP_ROOT/compose.test.second.env" "$TMP_ROOT/services.test.second.env"
second_gateway_secret=$(env_value "$TMP_ROOT/services.test.second.env" GATEWAY_SIGNING_SECRET)
[ "$gateway_secret" != "$second_gateway_secret" ] \
    || fail 'TC-CI-004 repeated generation returned the same gateway secret'
pass 'TC-CI-004'

assert_text deploy/services.env.example '^EMBEDDING_BASE_URL=' \
    'TC-CI-013 embedding base URL env'
assert_text deploy/services.env.example '^EMBEDDING_MODEL=' \
    'TC-CI-013 embedding model env'
assert_text deploy/services.env.example '^EMBEDDING_MAX_TOKENS=' \
    'TC-CI-013 embedding token limit env'
assert_text deploy/nacos-config/ai-service.properties \
    '^langchain4j\.open-ai\.embedding-model\.base-url=\$\{EMBEDDING_BASE_URL:' \
    'TC-CI-013 Nacos embedding base URL override'
assert_text deploy/nacos-config/ai-service.properties \
    '^langchain4j\.open-ai\.embedding-model\.model-name=\$\{EMBEDDING_MODEL:' \
    'TC-CI-013 Nacos embedding model override'
assert_text deploy/nacos-config/ai-service.properties \
    '^app\.embedding\.max-tokens=\$\{EMBEDDING_MAX_TOKENS:' \
    'TC-CI-013 Nacos embedding token limit override'
assert_text backend/ai-service/src/main/resources/application.yml \
    '^    max-tokens: \$\{EMBEDDING_MAX_TOKENS:512\}$' \
    'TC-CI-014 application embedding token limit fallback'
assert_no_text backend/ai-service/src/main/resources/application.yml \
    'EMBEDDING_MAX_CHARS' \
    'TC-CI-014 no obsolete embedding character limit'

assert_text .gitlab-ci.yml '^verify:deployment:' 'TC-CI-005 verify deployment job'
assert_text .gitlab-ci.yml 'sh deploy/generate-test-env\.sh' 'TC-CI-005 test env generation'
assert_text .gitlab-ci.yml 'docker compose .* config --quiet' 'TC-CI-005 compose validation'

assert_text .gitlab-ci.yml 'DEPLOY_COMPOSE_ENV_FILE:\?缺少' 'TC-CI-006 compose file variable guard'
assert_text .gitlab-ci.yml 'DEPLOY_SERVICES_ENV_FILE:\?缺少' 'TC-CI-006 services file variable guard'
assert_text .gitlab-ci.yml 'export BUSINESS_ENV_FILE=\./services\.env' 'TC-CI-006 services file path override'
assert_text .gitlab-ci.yml '^  resource_group: staging-infra-portal$' 'TC-CI-007 deployment lock'
assert_text .gitlab-ci.yml 'up --detach --wait --no-build' 'TC-CI-008 deployment health wait'
assert_text .gitlab-ci.yml '^deploy:frontend:' 'TC-CI-009 frontend deployment job'
assert_text .gitlab-ci.yml 'Runner 缺少服务镜像，开始构建' 'TC-CI-010 service image fallback'
assert_text .gitlab-ci.yml 'Runner 缺少前端镜像，开始构建' 'TC-CI-010 frontend image fallback'
assert_text .gitlab-ci.yml '^  DEPLOY_STATE_DIR: "/app/infra-portal/deploy"$' \
    'TC-CI-011 stable deployment directory'
assert_text .gitlab-ci.yml 'cp deploy/docker-compose\.yml "\$DEPLOY_COMPOSE_FILE"' \
    'TC-CI-011 persisted compose file'
assert_text .gitlab-ci.yml 'cp db/init\.sql "\$DEPLOY_STATE_DIR/\.\./db/init\.sql"' \
    'TC-CI-011 persisted database init script'
assert_text .gitlab-ci.yml 'sed -i .*BUSINESS_ENV_FILE=\./services\.env.*DEPLOY_COMPOSE_ENV' \
    'TC-CI-011 persisted services env path'
assert_text .gitlab-ci.yml 'docker compose --env-file "\$DEPLOY_COMPOSE_ENV" --file "\$DEPLOY_COMPOSE_FILE"' \
    'TC-CI-011 deployment uses persisted compose file'
inspect_count=$(grep -Fc 'docker image inspect "$IMAGE"' "$ROOT_DIR/.gitlab-ci.yml" || true)
[ "$inspect_count" -ge 2 ] \
    || fail 'TC-CI-012 dependency image cache check must cover prepare and full-stack jobs'
pass 'TC-CI-012 dependency image cache checks'
assert_text .gitlab-ci.yml '依赖镜像已存在，跳过拉取' 'TC-CI-012 cache reuse log'
assert_no_text .gitlab-ci.yml 'docker compose .* pull mysql nacos etcd minio milvus' \
    'TC-CI-012 no unconditional compose pull'

printf '%s\n' '* Task complete: GitLab CI contract'
