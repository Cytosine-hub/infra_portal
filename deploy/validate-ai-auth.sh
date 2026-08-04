#!/bin/sh
set -eu

services_env=${1:-}
image_ref=${2:-}
compose_env=${3:-}
docker_command=${DOCKER_COMMAND:-docker}

if [ -z "$services_env" ] || [ ! -f "$services_env" ]; then
    printf '%s\n' '- Subtask failure: services environment file is missing' >&2
    exit 1
fi
if [ -z "$image_ref" ]; then
    printf '%s\n' '- Subtask failure: ai-service image reference is required' >&2
    exit 1
fi

network_name=infra-portal-network
if [ -n "$compose_env" ] && [ -f "$compose_env" ]; then
    configured_network=$(sed -n 's/^COMPOSE_NETWORK_NAME=//p' "$compose_env" | tail -1)
    [ -z "$configured_network" ] || network_name=$configured_network
fi

printf '%s\n' '+ Task start: validate AI model authentication'
if "$docker_command" run --rm \
    --network "$network_name" \
    --env-file "$services_env" \
    --entrypoint sh \
    "$image_ref" \
    -c '
        set -eu
        case "${AI_API_KEY:-}" in
            ""|"\$AI_API_KEY"|"\${AI_API_KEY}"|changeme|test-*)
                echo "AI_API_KEY is empty or uses a placeholder" >&2
                exit 1
                ;;
        esac
        base_url=${AI_BASE_URL:-http://ai.tlb.shcj-s.com:8080/v1}
        http_code=$(curl --noproxy "*" --silent --show-error \
            --output /dev/null --write-out "%{http_code}" \
            --connect-timeout 10 --max-time 30 \
            --header "Authorization: Bearer ${AI_API_KEY}" \
            "${base_url%/}/models" || true)
        case "$http_code" in
            2??)
                exit 0
                ;;
            401|403)
                echo "AI model authentication was rejected with HTTP ${http_code}" >&2
                exit 1
                ;;
            *)
                echo "AI model preflight failed with HTTP ${http_code:-000}" >&2
                exit 1
                ;;
        esac
    '; then
    printf '%s\n' '| Subtask success: AI model authentication is valid'
else
    printf '%s\n' '- Subtask failure: AI model authentication preflight failed' >&2
    exit 1
fi
