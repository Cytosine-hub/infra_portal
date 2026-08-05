#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/infra-portal-rollback-test.XXXXXX")
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

pass() {
    printf '| Subtask success: %s\n' "$1"
}

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

state_value() {
    key=$1
    sed -n "s/^${key}=//p" "$ROLLBACK_STATE_ROOT/api-gateway.state"
}

run_rollback() {
    PATH="$TMP_ROOT/bin:$PATH" \
        COMPOSE_ENV_FILE="$TMP_ROOT/compose.env" \
        COMPOSE_FILE="$TMP_ROOT/compose.yml" \
        IMAGE_NAMESPACE=infra-portal \
        sh "$ROOT_DIR/deploy/rollback.sh" "$@"
}

printf '%s\n' '+ Task start: deployment rollback'

mkdir -p "$TMP_ROOT/bin"
touch "$TMP_ROOT/compose.env" "$TMP_ROOT/compose.yml"
cat > "$TMP_ROOT/bin/docker" <<'EOF'
#!/bin/sh
set -eu

if [ "$1" = compose ]; then
    shift
    while [ "$1" = --env-file ] || [ "$1" = --file ]; do
        shift 2
    done
    case "$1 $2" in
        'ps --quiet')
            printf '%s\n' mock-container-id
            exit 0
            ;;
        'up --detach')
            printf 'up tag=%s %s\n' "${IMAGE_TAG:-}" "$*" >> "$MOCK_DOCKER_LOG"
            if [ "${MOCK_FAIL_IMAGE_TAG:-}" = "${IMAGE_TAG:-}" ]; then
                exit 1
            fi
            exit 0
            ;;
        'ps api-gateway')
            exit 0
            ;;
    esac
fi

if [ "$1" = inspect ]; then
    printf '%s\n' "$MOCK_RUNNING_IMAGE"
    exit 0
fi

if [ "$1 $2" = 'image inspect' ]; then
    exit 0
fi

printf 'unexpected docker arguments: %s\n' "$*" >&2
exit 1
EOF
chmod +x "$TMP_ROOT/bin/docker"

export ROLLBACK_STATE_ROOT="$TMP_ROOT/state"
export MOCK_DOCKER_LOG="$TMP_ROOT/docker.log"
export MOCK_RUNNING_IMAGE=infra-portal/api-gateway:20260801090000-aaaaaaa

IMAGE_TAG=20260802090000-bbbbbbb AUTO_ROLLBACK_ENABLED=true \
    run_rollback deploy api-gateway
[ "$(state_value CURRENT_IMAGE)" = \
    infra-portal/api-gateway:20260802090000-bbbbbbb ] \
    || fail 'TC-CI-040 successful deployment did not record the current image'
[ "$(state_value PREVIOUS_IMAGE)" = \
    infra-portal/api-gateway:20260801090000-aaaaaaa ] \
    || fail 'TC-CI-040 successful deployment did not record the previous image'
pass 'TC-CI-040 successful deployment history recording'

: > "$MOCK_DOCKER_LOG"
if IMAGE_TAG=20260803090000-ccccccc AUTO_ROLLBACK_ENABLED=true \
    MOCK_FAIL_IMAGE_TAG=20260803090000-ccccccc \
    run_rollback deploy api-gateway; then
    fail 'TC-CI-041 failed deployment must remain failed after automatic rollback'
fi
grep -Eq '^up tag=20260803090000-ccccccc ' "$MOCK_DOCKER_LOG" \
    || fail 'TC-CI-041 new image deployment was not attempted'
grep -Eq '^up tag=20260801090000-aaaaaaa ' "$MOCK_DOCKER_LOG" \
    || fail 'TC-CI-041 previous running image was not restored'
pass 'TC-CI-041 deployment failure automatic rollback'

: > "$MOCK_DOCKER_LOG"
if IMAGE_TAG=20260803090000-ccccccc AUTO_ROLLBACK_ENABLED=false \
    MOCK_FAIL_IMAGE_TAG=20260803090000-ccccccc \
    run_rollback deploy api-gateway; then
    fail 'TC-CI-042 failed deployment must return a failure status'
fi
[ "$(wc -l < "$MOCK_DOCKER_LOG" | tr -d ' ')" = 1 ] \
    || fail 'TC-CI-042 disabled automatic rollback still restored an image'
pass 'TC-CI-042 automatic rollback switch'

if (unset AUTO_ROLLBACK_ENABLED; \
    IMAGE_TAG=20260803090000-ccccccc run_rollback deploy api-gateway) \
    >/dev/null 2>&1; then
    fail 'TC-CI-048 missing automatic rollback project variable must be rejected'
fi
pass 'TC-CI-048 required automatic rollback project variable'

: > "$MOCK_DOCKER_LOG"
export MOCK_RUNNING_IMAGE=infra-portal/api-gateway:20260802090000-bbbbbbb
(unset AUTO_ROLLBACK_ENABLED; run_rollback manual api-gateway)
grep -Eq '^up tag=20260801090000-aaaaaaa ' "$MOCK_DOCKER_LOG" \
    || fail 'TC-CI-043 manual rollback did not deploy the previous image'
[ "$(state_value CURRENT_IMAGE)" = \
    infra-portal/api-gateway:20260801090000-aaaaaaa ] \
    || fail 'TC-CI-043 manual rollback did not update the current image'
[ "$(state_value PREVIOUS_IMAGE)" = \
    infra-portal/api-gateway:20260802090000-bbbbbbb ] \
    || fail 'TC-CI-043 manual rollback did not preserve the replaced image'
pass 'TC-CI-043 manual rollback without automatic rollback variable and history exchange'

if run_rollback manual invalid-service >/dev/null 2>&1; then
    fail 'TC-CI-044 unsupported rollback target must be rejected'
fi
pass 'TC-CI-044 rollback target validation'

printf '%s\n' '* Task complete: deployment rollback'
