#!/bin/sh
set -eu

log_info() {
    printf '| %s\n' "$1"
}

log_success() {
    printf '| Subtask success: %s\n' "$1"
}

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

require_value() {
    name=$1
    eval "value=\${$name:-}"
    [ -n "$value" ] || fail "$name is required"
}

require_value NACOS_URL
require_value NACOS_USERNAME
require_value NACOS_PASSWORD
require_value NACOS_CONFIG_GROUP
require_value NACOS_CONFIG_DIR

[ -d "$NACOS_CONFIG_DIR" ] || fail "Nacos config directory not found: $NACOS_CONFIG_DIR"

NACOS_URL=${NACOS_URL%/}
NACOS_NAMESPACE=${NACOS_NAMESPACE:-}
TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/nacos-init.XXXXXX")
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

printf '%s\n' '+ Task start: Nacos configuration initialization'

login_response=$(curl --fail --silent --show-error \
    --request POST \
    --data-urlencode "username=$NACOS_USERNAME" \
    --data-urlencode "password=$NACOS_PASSWORD" \
    "$NACOS_URL/nacos/v1/auth/users/login") || fail 'Nacos login failed'

access_token=$(printf '%s' "$login_response" | jq -er '.accessToken // empty') \
    || fail 'Nacos login response did not contain an access token'
log_success 'Nacos authentication'

if [ -n "$NACOS_NAMESPACE" ]; then
    namespace_response=$(curl --fail --silent --show-error --get \
        --data-urlencode "accessToken=$access_token" \
        "$NACOS_URL/nacos/v1/console/namespaces") \
        || fail 'Failed to query Nacos namespaces'

    if printf '%s' "$namespace_response" \
        | jq -e --arg namespace "$NACOS_NAMESPACE" \
            '(.data // []) | any(.namespace == $namespace)' >/dev/null; then
        log_info "SKIP namespace=$NACOS_NAMESPACE already exists"
    else
        namespace_result=$(curl --fail --silent --show-error \
            --request POST \
            --data-urlencode "accessToken=$access_token" \
            --data-urlencode "customNamespaceId=$NACOS_NAMESPACE" \
            --data-urlencode "namespaceName=$NACOS_NAMESPACE" \
            --data-urlencode 'namespaceDesc=infra-portal managed namespace' \
            "$NACOS_URL/nacos/v1/console/namespaces") \
            || fail "Failed to create Nacos namespace: $NACOS_NAMESPACE"
        [ "$namespace_result" = true ] \
            || fail "Nacos rejected namespace creation: $NACOS_NAMESPACE"
        log_success "Created namespace=$NACOS_NAMESPACE"
    fi
fi

config_count=0
for config_file in "$NACOS_CONFIG_DIR"/*.properties; do
    [ -f "$config_file" ] || continue
    config_count=$((config_count + 1))
    data_id=$(basename "$config_file")
    response_file="$TMP_ROOT/$data_id.response"

    status_code=$(curl --silent --show-error \
        --output "$response_file" \
        --write-out '%{http_code}' \
        --get \
        --data-urlencode "accessToken=$access_token" \
        --data-urlencode "dataId=$data_id" \
        --data-urlencode "group=$NACOS_CONFIG_GROUP" \
        --data-urlencode "tenant=$NACOS_NAMESPACE" \
        "$NACOS_URL/nacos/v1/cs/configs") \
        || fail "Failed to query Nacos Data ID: $data_id"

    case "$status_code" in
        200)
            log_info "SKIP dataId=$data_id already exists"
            ;;
        404)
            publish_result=$(curl --fail --silent --show-error \
                --request POST \
                --data-urlencode "accessToken=$access_token" \
                --data-urlencode "dataId=$data_id" \
                --data-urlencode "group=$NACOS_CONFIG_GROUP" \
                --data-urlencode "tenant=$NACOS_NAMESPACE" \
                --data-urlencode 'type=properties' \
                --data-urlencode "content@$config_file" \
                "$NACOS_URL/nacos/v1/cs/configs") \
                || fail "Failed to publish Nacos Data ID: $data_id"
            [ "$publish_result" = true ] \
                || fail "Nacos rejected Data ID publication: $data_id"
            log_success "Published dataId=$data_id"
            ;;
        *)
            fail "Unexpected HTTP status for dataId=$data_id status=$status_code"
            ;;
    esac
done

[ "$config_count" -gt 0 ] || fail "No .properties files found in $NACOS_CONFIG_DIR"
printf '%s\n' "* Task complete: processed $config_count Nacos Data IDs"
