#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
SCRIPT="$ROOT_DIR/deploy/nacos-init.sh"
TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/nacos-init-test.XXXXXX")
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

pass() {
    printf '| Subtask success: %s\n' "$1"
}

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

[ -f "$SCRIPT" ] || fail 'TC-DOCKER-013 missing deploy/nacos-init.sh'

mkdir -p "$TMP_ROOT/bin" "$TMP_ROOT/config"

for service in api-gateway core-service ai-service community-service middleware-service \
    database-service host-service network-service security-service; do
    printf 'logging.level.root=INFO\n' > "$TMP_ROOT/config/$service.properties"
done

cat > "$TMP_ROOT/bin/curl" <<'EOF'
#!/bin/sh
set -eu

method=GET
output_file=
url=
get_with_data=false

while [ "$#" -gt 0 ]; do
    case "$1" in
        -X|--request)
            method=$2
            shift 2
            ;;
        -o|--output)
            output_file=$2
            shift 2
            ;;
        -w|--write-out)
            shift 2
            ;;
        -G|--get)
            get_with_data=true
            shift
            ;;
        --data|--data-urlencode|-d)
            shift 2
            ;;
        -*)
            shift
            ;;
        *)
            url=$1
            shift
            ;;
    esac
done

printf '%s %s\n' "$method" "$url" >> "$MOCK_CURL_LOG"

case "$url" in
    */nacos/v1/auth/users/login)
        printf '{"accessToken":"test-token"}\n'
        ;;
    */nacos/v1/console/namespaces)
        if [ "$method" = POST ]; then
            printf 'true\n'
        elif [ "${MOCK_NAMESPACE_STATE:-missing}" = existing ]; then
            printf '{"data":[{"namespace":"test-namespace"}]}\n'
        else
            printf '{"data":[]}\n'
        fi
        ;;
    */nacos/v1/cs/configs)
        if [ "$method" = POST ]; then
            if [ "${MOCK_PUBLISH_FAILURE:-false}" = true ]; then
                exit 22
            fi
            printf 'true\n'
        elif [ "$get_with_data" = true ]; then
            if [ "${MOCK_CONFIG_STATE:-missing}" = existing ]; then
                if [ -n "$output_file" ]; then
                    if [ "${MOCK_CONFIG_CONTENT:-same}" = same ]; then
                        printf 'logging.level.root=INFO\n' > "$output_file"
                    else
                        printf 'logging.level.root=WARN\n' > "$output_file"
                    fi
                fi
                printf '200'
            else
                [ -z "$output_file" ] || : > "$output_file"
                printf '404'
            fi
        fi
        ;;
    *)
        exit 22
        ;;
esac
EOF
chmod +x "$TMP_ROOT/bin/curl"

run_initializer() {
    output_file=$1
    shift
    env \
        PATH="$TMP_ROOT/bin:$PATH" \
        MOCK_CURL_LOG="$TMP_ROOT/curl.log" \
        NACOS_URL=http://nacos:8848 \
        NACOS_USERNAME=nacos \
        NACOS_PASSWORD=nacos \
        NACOS_NAMESPACE=test-namespace \
        NACOS_CONFIG_GROUP=DEFAULT_GROUP \
        NACOS_CONFIG_DIR="$TMP_ROOT/config" \
        "$@" \
        sh "$SCRIPT" > "$output_file" 2>&1
}

printf '%s\n' '+ Task start: Nacos initializer'

: > "$TMP_ROOT/curl.log"
run_initializer "$TMP_ROOT/missing.out" \
    MOCK_NAMESPACE_STATE=missing MOCK_CONFIG_STATE=missing
namespace_posts=$(grep -c '^POST http://nacos:8848/nacos/v1/console/namespaces$' "$TMP_ROOT/curl.log" || true)
[ "$namespace_posts" -eq 1 ] || fail 'TC-DOCKER-013 namespace was not created exactly once'
pass 'TC-DOCKER-013'

config_posts=$(grep -c '^POST http://nacos:8848/nacos/v1/cs/configs$' "$TMP_ROOT/curl.log" || true)
[ "$config_posts" -eq 9 ] || fail 'TC-DOCKER-014 missing Data IDs were not all published'
pass 'TC-DOCKER-014'

: > "$TMP_ROOT/curl.log"
run_initializer "$TMP_ROOT/existing.out" \
    MOCK_NAMESPACE_STATE=existing MOCK_CONFIG_STATE=existing
config_posts=$(grep -c '^POST http://nacos:8848/nacos/v1/cs/configs$' "$TMP_ROOT/curl.log" || true)
[ "$config_posts" -eq 0 ] || fail 'TC-DOCKER-015 existing Data IDs were overwritten'
skip_count=$(grep -c 'SKIP dataId=' "$TMP_ROOT/existing.out" || true)
[ "$skip_count" -eq 9 ] || fail 'TC-DOCKER-015 existing Data IDs did not log SKIP'
pass 'TC-DOCKER-015'

: > "$TMP_ROOT/curl.log"
run_initializer "$TMP_ROOT/changed.out" \
    MOCK_NAMESPACE_STATE=existing MOCK_CONFIG_STATE=existing MOCK_CONFIG_CONTENT=changed
config_posts=$(grep -c '^POST http://nacos:8848/nacos/v1/cs/configs$' "$TMP_ROOT/curl.log" || true)
[ "$config_posts" -eq 9 ] || fail 'TC-DOCKER-032 changed Data IDs were not all updated'
update_count=$(grep -c 'Updated dataId=' "$TMP_ROOT/changed.out" || true)
[ "$update_count" -eq 9 ] || fail 'TC-DOCKER-032 changed Data IDs did not log updates'
pass 'TC-DOCKER-032'

if run_initializer "$TMP_ROOT/failure.out" \
    MOCK_NAMESPACE_STATE=existing MOCK_CONFIG_STATE=missing MOCK_PUBLISH_FAILURE=true; then
    fail 'TC-DOCKER-016 publish failure returned success'
fi
pass 'TC-DOCKER-016'

config_gets=$(grep -c '^GET http://nacos:8848/nacos/v1/cs/configs$' "$TMP_ROOT/curl.log" || true)
[ "$config_gets" -ge 1 ] || fail 'TC-DOCKER-017 initializer did not process property files'
: > "$TMP_ROOT/curl.log"
run_initializer "$TMP_ROOT/all.out" \
    MOCK_NAMESPACE_STATE=existing MOCK_CONFIG_STATE=existing
config_gets=$(grep -c '^GET http://nacos:8848/nacos/v1/cs/configs$' "$TMP_ROOT/curl.log" || true)
[ "$config_gets" -eq 9 ] || fail 'TC-DOCKER-017 initializer did not process all nine Data IDs'
pass 'TC-DOCKER-017'

printf '%s\n' '* Task complete: Nacos initializer'
