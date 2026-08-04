#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
SCRIPT="$ROOT_DIR/deploy/validate-ai-auth.sh"
TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/infra-portal-ai-auth-test.XXXXXX")
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

pass() {
    printf '| Subtask success: %s\n' "$1"
}

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

printf '%s\n' '+ Task start: AI authentication deployment preflight'

[ -f "$SCRIPT" ] || fail 'TC-CI-035 missing deploy/validate-ai-auth.sh'

mkdir -p "$TMP_ROOT/bin"
cat > "$TMP_ROOT/bin/docker" <<'EOF'
#!/bin/sh
set -eu

printf '%s\n' "$*" >> "$MOCK_DOCKER_LOG"
exit "${MOCK_DOCKER_EXIT:-0}"
EOF
chmod +x "$TMP_ROOT/bin/docker"

cat > "$TMP_ROOT/services.env" <<'EOF'
AI_BASE_URL=http://model.example/v1
AI_API_KEY=private-test-key
EOF
cat > "$TMP_ROOT/compose.env" <<'EOF'
COMPOSE_NETWORK_NAME=infra-portal-test-network
EOF

export MOCK_DOCKER_LOG="$TMP_ROOT/docker.log"
PATH="$TMP_ROOT/bin:$PATH" \
    sh "$SCRIPT" "$TMP_ROOT/services.env" infra-portal/ai-service:test \
    "$TMP_ROOT/compose.env" > "$TMP_ROOT/success.out"

grep -Eq '^run --rm --network infra-portal-test-network --env-file .*/services\.env --entrypoint sh infra-portal/ai-service:test ' \
    "$MOCK_DOCKER_LOG" \
    || fail 'TC-CI-035 preflight did not use the target image, network, and services env'
grep -Eq '/models' "$MOCK_DOCKER_LOG" \
    || fail 'TC-CI-035 preflight did not check the model authentication endpoint'
pass 'TC-CI-035 valid model credential preflight'

if PATH="$TMP_ROOT/bin:$PATH" \
    MOCK_DOCKER_EXIT=42 \
    sh "$SCRIPT" "$TMP_ROOT/services.env" infra-portal/ai-service:test \
    "$TMP_ROOT/compose.env" > "$TMP_ROOT/failure.out" 2>&1; then
    fail 'TC-CI-036 failed model authentication did not stop deployment'
fi
pass 'TC-CI-036 invalid model credential rejection'

if grep -Fq 'private-test-key' "$TMP_ROOT/success.out" "$TMP_ROOT/failure.out"; then
    fail 'TC-CI-037 model credential leaked to preflight output'
fi
pass 'TC-CI-037 model credential redaction'

printf '%s\n' '* Task complete: AI authentication deployment preflight'
