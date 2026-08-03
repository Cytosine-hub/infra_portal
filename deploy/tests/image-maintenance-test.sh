#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/infra-portal-image-test.XXXXXX")
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

pass() {
    printf '| Subtask success: %s\n' "$1"
}

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

printf '%s\n' '+ Task start: business image maintenance'

grep -Eq '^ARG IMAGE_REVISION=unknown$' "$ROOT_DIR/deploy/Dockerfile" \
    || fail 'TC-CI-031 backend image revision argument is missing'
grep -Eq '^ARG IMAGE_VERSION=local$' "$ROOT_DIR/deploy/Dockerfile" \
    || fail 'TC-CI-031 backend image version argument is missing'
grep -Eq '^LABEL org\.opencontainers\.image\.revision="\$IMAGE_REVISION" \\$' \
    "$ROOT_DIR/deploy/Dockerfile" \
    || fail 'TC-CI-031 backend image revision label is missing'
grep -Eq '^      org\.opencontainers\.image\.version="\$IMAGE_VERSION"$' \
    "$ROOT_DIR/deploy/Dockerfile" \
    || fail 'TC-CI-031 backend image version label is missing'
grep -Eq '^ARG IMAGE_REVISION=unknown$' "$ROOT_DIR/deploy/Dockerfile.frontend" \
    || fail 'TC-CI-031 frontend image revision argument is missing'
grep -Eq '^ARG IMAGE_VERSION=local$' "$ROOT_DIR/deploy/Dockerfile.frontend" \
    || fail 'TC-CI-031 frontend image version argument is missing'
grep -Eq '^LABEL org\.opencontainers\.image\.revision="\$IMAGE_REVISION" \\$' \
    "$ROOT_DIR/deploy/Dockerfile.frontend" \
    || fail 'TC-CI-031 frontend image revision label is missing'
grep -Eq '^      org\.opencontainers\.image\.version="\$IMAGE_VERSION"$' \
    "$ROOT_DIR/deploy/Dockerfile.frontend" \
    || fail 'TC-CI-031 frontend image version label is missing'

backend_build_count=$(grep -c 'docker build --build-arg SERVICE=' \
    "$ROOT_DIR/.gitlab-ci.yml")
frontend_build_count=$(grep -c -- '--file deploy/Dockerfile.frontend' \
    "$ROOT_DIR/.gitlab-ci.yml")
application_build_count=$((backend_build_count + frontend_build_count))
revision_arg_count=$(grep -c -- '--build-arg IMAGE_REVISION="\$CI_COMMIT_SHA"' \
    "$ROOT_DIR/.gitlab-ci.yml")
version_arg_count=$(grep -c -- '--build-arg IMAGE_VERSION="\$IMAGE_TAG"' \
    "$ROOT_DIR/.gitlab-ci.yml")
[ "$backend_build_count" -gt 0 ] \
    || fail 'TC-CI-031 backend image build command is missing'
[ "$frontend_build_count" -gt 0 ] \
    || fail 'TC-CI-031 frontend image build command is missing'
[ "$revision_arg_count" -eq "$application_build_count" ] \
    || fail 'TC-CI-031 not every application build passes the image revision'
[ "$version_arg_count" -eq "$application_build_count" ] \
    || fail 'TC-CI-031 not every application build passes the image version'
pass 'TC-CI-031 unique application image config metadata'

actual_tag=$(sh "$ROOT_DIR/deploy/image-tag.sh" \
    0123456789abcdef0123456789abcdef01234567 2026-08-03T16:30:00Z)
[ "$actual_tag" = "20260804003000-0123456" ] \
    || fail "TC-CI-026 unexpected image tag: $actual_tag"
pass 'TC-CI-026 Asia/Shanghai timestamp and seven-character commit hash'

if sh "$ROOT_DIR/deploy/image-tag.sh" invalid-sha 2026-08-03T16:30:00Z \
    >/dev/null 2>&1; then
    fail 'TC-CI-027 invalid commit hash must be rejected'
fi
pass 'TC-CI-027 invalid commit hash rejection'

mkdir -p "$TMP_ROOT/bin"
cat > "$TMP_ROOT/bin/docker" <<'EOF'
#!/bin/sh
set -eu

if [ "$1 $2" = "image ls" ]; then
    cat <<'IMAGES'
2026-08-04 12:00:00 +0800 CST infra-portal/api-gateway:20260804120000-aaaaaaa
2026-08-03 12:00:00 +0800 CST infra-portal/api-gateway:20260803120000-bbbbbbb
2026-08-02 12:00:00 +0800 CST infra-portal/api-gateway:20260802120000-ccccccc
2026-08-01 12:00:00 +0800 CST infra-portal/api-gateway:20260801120000-ddddddd
2026-07-31 12:00:00 +0800 CST infra-portal/api-gateway:20260731120000-eeeeeee
2026-07-30 12:00:00 +0800 CST infra-portal/api-gateway:20260730120000-fffffff
2026-07-29 12:00:00 +0800 CST infra-portal/api-gateway:0123456789abcdef0123456789abcdef01234567
2026-07-28 12:00:00 +0800 CST infra-portal/nacos-init:20260728-9999999
IMAGES
    exit 0
fi

if [ "$1 $2" = "container ls" ]; then
    case "$*" in
        *infra-portal/api-gateway:20260801120000-ddddddd*)
            printf '%s\n' running-container-id
            ;;
    esac
    exit 0
fi

if [ "$1 $2" = "image rm" ]; then
    if [ "${MOCK_DOCKER_RM_FAIL:-}" = "$3" ]; then
        exit 1
    fi
    printf '%s\n' "$3" >> "$MOCK_DOCKER_LOG"
    exit 0
fi

printf 'unexpected docker arguments: %s\n' "$*" >&2
exit 1
EOF
chmod +x "$TMP_ROOT/bin/docker"

export MOCK_DOCKER_LOG="$TMP_ROOT/docker-rm.log"
PATH="$TMP_ROOT/bin:$PATH" \
    IMAGE_NAMESPACE=infra-portal \
    BUSINESS_IMAGE_SERVICES=api-gateway \
    BUSINESS_IMAGE_KEEP_COUNT=3 \
    sh "$ROOT_DIR/deploy/cleanup-business-images.sh"

cat > "$TMP_ROOT/expected-rm.log" <<'EOF'
infra-portal/api-gateway:20260731120000-eeeeeee
infra-portal/api-gateway:20260730120000-fffffff
EOF

cmp -s "$TMP_ROOT/expected-rm.log" "$MOCK_DOCKER_LOG" \
    || fail 'TC-CI-028 cleanup removed an active, retained, or dependency image'
pass 'TC-CI-028 keep newest three images and preserve images used by containers'

if PATH="$TMP_ROOT/bin:$PATH" \
    BUSINESS_IMAGE_SERVICES=api-gateway \
    BUSINESS_IMAGE_KEEP_COUNT=0 \
    sh "$ROOT_DIR/deploy/cleanup-business-images.sh" >/dev/null 2>&1; then
    fail 'TC-CI-029 non-positive retention count must be rejected'
fi
pass 'TC-CI-029 invalid retention count rejection'

if PATH="$TMP_ROOT/bin:$PATH" \
    IMAGE_NAMESPACE=infra-portal \
    BUSINESS_IMAGE_SERVICES=api-gateway \
    BUSINESS_IMAGE_KEEP_COUNT=3 \
    MOCK_DOCKER_RM_FAIL=infra-portal/api-gateway:20260731120000-eeeeeee \
    sh "$ROOT_DIR/deploy/cleanup-business-images.sh" >/dev/null 2>&1; then
    fail 'TC-CI-030 Docker image removal failures must fail the cleanup task'
fi
pass 'TC-CI-030 Docker image removal failure propagation'

printf '%s\n' '* Task complete: business image maintenance'
