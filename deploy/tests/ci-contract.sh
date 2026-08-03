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

assert_text() {
    grep -Eq -- "$2" "$ROOT_DIR/$1" || fail "$3 missing pattern $2"
    pass "$3"
}

assert_no_text() {
    if grep -Eq -- "$2" "$ROOT_DIR/$1"; then
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

assert_env_equals() {
    file=$1
    key=$2
    expected=$3
    value=$(env_value "$file" "$key")
    [ "$value" = "$expected" ] \
        || fail "TC-CI-002 unexpected generated value: $key=$value"
}

printf '%s\n' '+ Task start: GitLab CI contract'

sh "$ROOT_DIR/deploy/generate-test-env.sh" \
    "$TMP_ROOT/compose.test.env" "$TMP_ROOT/services.test.env"

for key in APP_DB_PASSWORD NACOS_PASSWORD NACOS_AUTH_TOKEN \
    NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE MINIO_ROOT_PASSWORD; do
    assert_nonempty_env "$TMP_ROOT/compose.test.env" "$key"
done
assert_env_equals "$TMP_ROOT/compose.test.env" COMPOSE_BUSINESS_PROJECT_NAME infra-portal-test
assert_env_equals "$TMP_ROOT/compose.test.env" COMPOSE_DEPENDENCIES_PROJECT_NAME \
    infra-portal-dependencies-test
assert_env_equals "$TMP_ROOT/compose.test.env" COMPOSE_NETWORK_NAME infra-portal-test-network
for key in GATEWAY_SIGNING_SECRET ADMIN_DEFAULT_PASSWORD AI_API_KEY \
    EMBEDDING_API_KEY WIKI_EXPORT_SIGNATURE_SECRET ZABBIX_PASSWORD; do
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

# assert_text .gitlab-ci.yml '^verify:deployment:' 'TC-CI-005 verify deployment job'
assert_text .gitlab-ci.yml 'sh deploy/generate-test-env\.sh' 'TC-CI-005 test env generation'
assert_text .gitlab-ci.yml 'docker compose .* config --quiet' 'TC-CI-005 compose validation'
assert_no_text .gitlab-ci.yml \
    '^    - docker compose --file deploy/docker-compose\.yml config --quiet$' \
    'TC-CI-005 redundant business Compose validation'
assert_no_text .gitlab-ci.yml \
    '^    - docker compose --file deploy/docker-compose\.dependencies\.yml config --quiet$' \
    'TC-CI-005 redundant dependency Compose validation'

assert_text .gitlab-ci.yml 'DEPLOY_COMPOSE_ENV_FILE:\?缺少' 'TC-CI-006 compose file variable guard'
assert_text .gitlab-ci.yml 'DEPLOY_SERVICES_ENV_FILE:\?缺少' 'TC-CI-006 services file variable guard'
assert_text .gitlab-ci.yml 'export BUSINESS_ENV_FILE=\./services\.env' 'TC-CI-006 services file path override'
assert_text .gitlab-ci.yml '旧变量 COMPOSE_PROJECT_NAME' \
    'TC-CI-006 reserved Compose project variable guard'
assert_text .gitlab-ci.yml '^  resource_group: staging-infra-portal$' 'TC-CI-007 deployment lock'
assert_text .gitlab-ci.yml 'up --detach --wait --no-build' 'TC-CI-008 deployment health wait'
assert_text .gitlab-ci.yml '^deploy:frontend:' 'TC-CI-009 frontend deployment job'
assert_text .gitlab-ci.yml '^  DEPLOY_STATE_DIR: "/app/infra-portal/deploy"$' \
    'TC-CI-011 stable deployment directory'
assert_text .gitlab-ci.yml 'cp deploy/docker-compose\.yml "\$DEPLOY_COMPOSE_FILE"' \
    'TC-CI-011 persisted compose file'
assert_text .gitlab-ci.yml \
    'cp deploy/docker-compose\.dependencies\.yml "\$DEPLOY_DEPENDENCIES_COMPOSE_FILE"' \
    'TC-CI-011 persisted dependency compose file'
assert_text .gitlab-ci.yml 'cp db/init\.sql "\$DEPLOY_STATE_DIR/\.\./db/init\.sql"' \
    'TC-CI-011 persisted database init script'
assert_text .gitlab-ci.yml 'sed -i .*BUSINESS_ENV_FILE=\./services\.env.*DEPLOY_COMPOSE_ENV' \
    'TC-CI-011 persisted services env path'
assert_text .gitlab-ci.yml 'docker compose --env-file "\$DEPLOY_COMPOSE_ENV" --file "\$DEPLOY_COMPOSE_FILE"' \
    'TC-CI-011 deployment uses persisted compose file'
assert_text .gitlab-ci.yml \
    'docker compose --env-file "\$DEPLOY_DEPENDENCIES_COMPOSE_ENV" --file "\$DEPLOY_DEPENDENCIES_COMPOSE_FILE"' \
    'TC-CI-011 deployment uses persisted dependency compose file'

assert_text .gitlab-ci.yml '^  - validate ' 'TC-CI-013 validate stage'
assert_text .gitlab-ci.yml 'sh deploy/tests/ci-contract\.sh' 'TC-CI-013 CI contract gate'
assert_text .gitlab-ci.yml 'sh deploy/tests/compose-contract\.sh' 'TC-CI-013 Compose contract gate'
assert_text .gitlab-ci.yml 'sh deploy/tests/nacos-init-test\.sh' 'TC-CI-013 Nacos contract gate'

assert_text .gitlab-ci.yml 'CI_OPEN_MERGE_REQUESTS.*CI_PIPELINE_SOURCE.*push' \
    'TC-CI-014 duplicate pipeline guard'
assert_text .gitlab-ci.yml '^  interruptible: true' 'TC-CI-014 stale pipeline cancellation'
assert_text .gitlab-ci.yml '^  interruptible: false' 'TC-CI-014 deployment cancellation guard'

assert_no_text .gitlab-ci.yml '--tag .*:latest' 'TC-CI-017 immutable application image tags'

assert_text .gitlab-ci.yml 'CI_PIPELINE_SOURCE == "schedule"' \
    'TC-CI-026 scheduled pipeline workflow'
assert_text .gitlab-ci.yml '^cleanup:business-images:' \
    'TC-CI-026 scheduled business image cleanup job'
assert_text .gitlab-ci.yml 'sh deploy/cleanup-business-images\.sh' \
    'TC-CI-026 business image cleanup command'
assert_text .gitlab-ci.yml '^    BUSINESS_IMAGE_KEEP_COUNT: "3"$' \
    'TC-CI-026 business image retention count'
assert_text .gitlab-ci.yml 'sh deploy/image-tag\.sh "\$CI_COMMIT_SHA" "\$CI_PIPELINE_CREATED_AT"' \
    'TC-CI-026 immutable image tag generation'
assert_no_text .gitlab-ci.yml ':\$CI_COMMIT_SHA' \
    'TC-CI-026 raw commit SHA image tags'

assert_text deploy/Dockerfile 'mvn .* clean verify$' 'TC-CI-018 full backend verification'
assert_text deploy/Dockerfile.frontend '^RUN npm test' 'TC-CI-019 frontend unit tests'

extract_job() {
    job=$1
    awk -v header="$job:" '
        $0 == header { in_job = 1; next }
        in_job && /^[^[:space:]#].*:$/ { exit }
        in_job { print }
    ' "$ROOT_DIR/.gitlab-ci.yml"
}

dependency_job=$(extract_job deploy:dependencies)
printf '%s\n' "$dependency_job" | grep -Eq 'extends: \.deploy-dependencies-common' \
    || fail 'TC-CI-020 dependency deployment must use dependency-only preparation'
printf '%s\n' "$dependency_job" | grep -Eq '\$DEPLOY_DEPENDENCIES_COMPOSE_FILE' \
    || fail 'TC-CI-020 dependency deployment must use dependency compose'
printf '%s\n' "$dependency_job" | grep -Eq '\$DEPLOY_COMPOSE_FILE' \
    && fail 'TC-CI-020 dependency deployment must not use business compose'
pass 'TC-CI-020 dependency-only deployment'

dependency_common=$(extract_job .deploy-dependencies-common)
printf '%s\n' "$dependency_common" | grep -Eq 'DEPLOY_SERVICES_ENV_FILE|DEPLOY_SERVICES_ENV' \
    && fail 'TC-CI-020 dependency preparation must not require business secrets'
printf '%s\n' "$dependency_common" | grep -Eq 'DEPLOY_DEPENDENCIES_COMPOSE_ENV' \
    || fail 'TC-CI-020 dependency preparation must persist an independent env file'
pass 'TC-CI-020 dependency-only preparation'

verify_all_job=$(extract_job verify:all-services)
printf '%s\n' "$verify_all_job" | grep -Eq '^  stage: build$' \
    || fail 'TC-CI-021 all-service verification must remain in build stage'
printf '%s\n' "$verify_all_job" | grep -Eq 'docker compose .* up ' \
    && fail 'TC-CI-021 verification job must not deploy services'
pass 'TC-CI-021 all-backend verification remains build-only'

all_services_job=$(extract_job deploy:all-services)
printf '%s\n' "$all_services_job" | grep -Eq \
    'api-gateway core-service ai-service community-service middleware-service database-service host-service network-service security-service' \
    || fail 'TC-CI-023 backend deployment must include all nine backend services'
printf '%s\n' "$all_services_job" | grep -Eq 'frontend' \
    && fail 'TC-CI-023 backend deployment must not include frontend'
printf '%s\n' "$all_services_job" | grep -Eq '\$DEPLOY_COMPOSE_FILE' \
    || fail 'TC-CI-023 backend deployment must use business compose'
pass 'TC-CI-023 all-backend deployment mode'

business_stack_job=$(extract_job deploy:business-stack)
printf '%s\n' "$business_stack_job" | grep -Eq \
    'api-gateway core-service ai-service community-service middleware-service database-service host-service network-service security-service' \
    || fail 'TC-CI-024 business deployment must include all nine backend services'
printf '%s\n' "$business_stack_job" | grep -Eq 'frontend' \
    || fail 'TC-CI-024 business deployment must include frontend'
printf '%s\n' "$business_stack_job" | grep -Eq \
    'DEPLOY_DEPENDENCIES_COMPOSE_FILE|nacos-init|mysql|milvus|minio|etcd' \
    && fail 'TC-CI-024 business deployment must not manage dependencies'
pass 'TC-CI-024 frontend and backend business deployment mode'

full_stack_job=$(extract_job deploy:full-stack)
printf '%s\n' "$full_stack_job" | grep -Eq \
    'DEPLOY_DEPENDENCIES_COMPOSE_FILE|nacos-init|mysql|milvus|minio|etcd' \
    || fail 'TC-CI-025 full-stack deployment must initialize dependencies'
printf '%s\n' "$full_stack_job" | grep -Eq '\$DEPLOY_COMPOSE_FILE' \
    || fail 'TC-CI-025 full-stack deployment must use business compose'
printf '%s\n' "$full_stack_job" | grep -Eq 'frontend' \
    || fail 'TC-CI-025 full-stack deployment must include frontend'
pass 'TC-CI-025 dependency and business full-stack deployment mode'

printf '%s\n' '* Task complete: GitLab CI contract'
