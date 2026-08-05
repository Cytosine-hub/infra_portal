#!/bin/sh
set -eu

DOCKER_COMMAND=${DOCKER_COMMAND:-docker}
COMPOSE_ENV_FILE=${COMPOSE_ENV_FILE:-}
COMPOSE_FILE=${COMPOSE_FILE:-}
IMAGE_NAMESPACE=${IMAGE_NAMESPACE:-infra-portal}
IMAGE_TAG=${IMAGE_TAG:-}
AUTO_ROLLBACK_ENABLED=${AUTO_ROLLBACK_ENABLED:-}
ROLLBACK_STATE_ROOT=${ROLLBACK_STATE_ROOT:-/app/infra-portal/compose/rollback}
DEPLOY_NO_DEPS=${DEPLOY_NO_DEPS:-false}
DEPLOY_VERIFY_MODE=${DEPLOY_VERIFY_MODE:-none}
BUSINESS_SERVICES='api-gateway core-service ai-service community-service middleware-service database-service host-service network-service security-service frontend'

fail() {
    printf '%s\n' "- Subtask failure: $1" >&2
    exit 1
}

is_supported_service() {
    requested_service=$1
    for supported_service in $BUSINESS_SERVICES; do
        [ "$requested_service" = "$supported_service" ] && return 0
    done
    return 1
}

validate_services() {
    [ "$#" -gt 0 ] || fail 'at least one business service is required'
    for service_name in "$@"; do
        is_supported_service "$service_name" \
            || fail "unsupported business service: $service_name"
    done
}

compose() {
    "$DOCKER_COMMAND" compose --env-file "$COMPOSE_ENV_FILE" \
        --file "$COMPOSE_FILE" "$@"
}

running_image() {
    service_name=$1
    container_id=$(compose ps --quiet "$service_name" 2>/dev/null | sed -n '1p')
    if [ -z "$container_id" ]; then
        printf '%s\n' '-'
        return
    fi
    "$DOCKER_COMMAND" inspect --format '{{.Config.Image}}' "$container_id"
}

image_tag() {
    service_name=$1
    image_ref=$2
    image_prefix="$IMAGE_NAMESPACE/$service_name:"
    case "$image_ref" in
        "$image_prefix"*)
            tag=${image_ref#"$image_prefix"}
            [ -n "$tag" ] || return 1
            printf '%s\n' "$tag"
            ;;
        *)
            return 1
            ;;
    esac
}

state_value() {
    service_name=$1
    key=$2
    state_file="$ROLLBACK_STATE_ROOT/$service_name.state"
    [ -f "$state_file" ] || return 0
    sed -n "s/^${key}=//p" "$state_file" | sed -n '1p'
}

write_state() {
    service_name=$1
    current_image=$2
    previous_image=$3
    state_file="$ROLLBACK_STATE_ROOT/$service_name.state"
    state_tmp="$state_file.tmp.$$"
    {
        printf 'CURRENT_IMAGE=%s\n' "$current_image"
        printf 'PREVIOUS_IMAGE=%s\n' "$previous_image"
    } > "$state_tmp"
    chmod 600 "$state_tmp"
    mv "$state_tmp" "$state_file"
}

snapshot_services() {
    snapshot_file=$1
    shift
    : > "$snapshot_file"
    for service_name in "$@"; do
        image_ref=$(running_image "$service_name")
        if [ "$image_ref" != '-' ]; then
            image_tag "$service_name" "$image_ref" >/dev/null \
                || fail "running image does not match $IMAGE_NAMESPACE/$service_name: $image_ref"
        fi
        printf '%s|%s\n' "$service_name" "$image_ref" >> "$snapshot_file"
    done
}

snapshot_image() {
    snapshot_file=$1
    requested_service=$2
    sed -n "s/^${requested_service}|//p" "$snapshot_file" | sed -n '1p'
}

restore_service() {
    service_name=$1
    image_ref=$2
    if [ "$image_ref" = '-' ]; then
        compose rm --stop --force "$service_name"
        return
    fi

    tag=$(image_tag "$service_name" "$image_ref") \
        || fail "invalid rollback image for $service_name: $image_ref"
    IMAGE_TAG=$tag "$DOCKER_COMMAND" compose --env-file "$COMPOSE_ENV_FILE" \
        --file "$COMPOSE_FILE" up --detach --wait --no-deps \
        --no-build --pull never "$service_name"
}

preflight_snapshot() {
    snapshot_file=$1
    shift
    for service_name in "$@"; do
        image_ref=$(snapshot_image "$snapshot_file" "$service_name")
        [ -n "$image_ref" ] || fail "snapshot is missing service: $service_name"
        if [ "$image_ref" != '-' ]; then
            "$DOCKER_COMMAND" image inspect "$image_ref" >/dev/null 2>&1 \
                || fail "rollback image is unavailable: $image_ref"
        fi
    done
}

restore_snapshot() {
    snapshot_file=$1
    shift
    preflight_snapshot "$snapshot_file" "$@"
    rollback_failed=false
    for service_name in "$@"; do
        image_ref=$(snapshot_image "$snapshot_file" "$service_name")
        if restore_service "$service_name" "$image_ref"; then
            printf '| Subtask success: restored %s to %s\n' \
                "$service_name" "$image_ref"
        else
            printf '%s\n' \
                "- Subtask failure: failed to restore $service_name to $image_ref" >&2
            rollback_failed=true
        fi
    done
    [ "$rollback_failed" = false ]
}

perform_deploy() {
    case "$DEPLOY_NO_DEPS" in
        true)
            compose up --detach --wait --no-deps --no-build --pull never "$@" \
                || return $?
            ;;
        false)
            compose up --detach --wait --no-build --pull never "$@" \
                || return $?
            ;;
        *)
            fail 'DEPLOY_NO_DEPS must be true or false'
            ;;
    esac

    case "$DEPLOY_VERIFY_MODE" in
        none)
            ;;
        frontend)
            compose exec -T frontend wget --quiet --output-document=/dev/null \
                http://127.0.0.1/ || return $?
            ;;
        full)
            compose exec -T frontend wget --quiet --output-document=/dev/null \
                http://127.0.0.1/ || return $?
            compose exec -T frontend wget --quiet --output-document=/dev/null \
                http://127.0.0.1/api/public/releases || return $?
            ;;
        *)
            fail 'DEPLOY_VERIFY_MODE must be none, frontend, or full'
            ;;
    esac
    compose ps "$@"
}

record_success() {
    snapshot_file=$1
    shift
    for service_name in "$@"; do
        old_image=$(snapshot_image "$snapshot_file" "$service_name")
        current_image="$IMAGE_NAMESPACE/$service_name:$IMAGE_TAG"
        if [ "$old_image" = '-' ]; then
            previous_image=$(state_value "$service_name" PREVIOUS_IMAGE)
        elif [ "$old_image" = "$current_image" ]; then
            previous_image=$(state_value "$service_name" PREVIOUS_IMAGE)
        else
            previous_image=$old_image
        fi
        write_state "$service_name" "$current_image" "$previous_image"
    done
}

deploy_services() {
    validate_services "$@"
    [ -n "$IMAGE_TAG" ] || fail 'IMAGE_TAG is required for deployment'
    case "$AUTO_ROLLBACK_ENABLED" in
        true|false) ;;
        *) fail 'AUTO_ROLLBACK_ENABLED must be true or false' ;;
    esac

    for service_name in "$@"; do
        "$DOCKER_COMMAND" image inspect \
            "$IMAGE_NAMESPACE/$service_name:$IMAGE_TAG" >/dev/null 2>&1 \
            || fail "deployment image is unavailable: $IMAGE_NAMESPACE/$service_name:$IMAGE_TAG"
    done

    transaction_root=$(mktemp -d "${TMPDIR:-/tmp}/infra-portal-deploy.XXXXXX")
    snapshot_file="$transaction_root/before.snapshot"
    trap 'rm -rf "$transaction_root"' EXIT HUP INT TERM
    snapshot_services "$snapshot_file" "$@"

    printf '%s\n' '+ Task start: deploy business services'
    if perform_deploy "$@"; then
        record_success "$snapshot_file" "$@"
        printf '%s\n' '* Task complete: business service deployment'
        return
    else
        deploy_status=$?
    fi

    printf '%s\n' '- Subtask failure: business service deployment failed' >&2
    compose ps "$@" >&2 || true
    if [ "$AUTO_ROLLBACK_ENABLED" = true ]; then
        printf '%s\n' '| Task info: automatic rollback enabled'
        restore_snapshot "$snapshot_file" "$@" \
            || printf '%s\n' '- Subtask failure: automatic rollback incomplete' >&2
    else
        printf '%s\n' '| Task info: automatic rollback disabled'
    fi
    return "$deploy_status"
}

manual_rollback() {
    validate_services "$@"
    transaction_root=$(mktemp -d "${TMPDIR:-/tmp}/infra-portal-rollback.XXXXXX")
    current_snapshot="$transaction_root/current.snapshot"
    target_snapshot="$transaction_root/target.snapshot"
    trap 'rm -rf "$transaction_root"' EXIT HUP INT TERM
    snapshot_services "$current_snapshot" "$@"
    : > "$target_snapshot"

    for service_name in "$@"; do
        previous_image=$(state_value "$service_name" PREVIOUS_IMAGE)
        [ -n "$previous_image" ] \
            || fail "no previous successful image recorded for $service_name"
        image_tag "$service_name" "$previous_image" >/dev/null \
            || fail "invalid previous image recorded for $service_name: $previous_image"
        printf '%s|%s\n' "$service_name" "$previous_image" >> "$target_snapshot"
    done
    preflight_snapshot "$target_snapshot" "$@"

    printf '%s\n' '+ Task start: manual business service rollback'
    if ! restore_snapshot "$target_snapshot" "$@"; then
        printf '%s\n' '| Task info: restoring state from before manual rollback'
        restore_snapshot "$current_snapshot" "$@" || true
        fail 'manual rollback failed'
    fi

    for service_name in "$@"; do
        rolled_back_image=$(snapshot_image "$target_snapshot" "$service_name")
        replaced_image=$(snapshot_image "$current_snapshot" "$service_name")
        [ "$replaced_image" != '-' ] || replaced_image=''
        write_state "$service_name" "$rolled_back_image" "$replaced_image"
    done
    compose ps "$@"
    printf '%s\n' '* Task complete: manual business service rollback'
}

[ -n "$COMPOSE_ENV_FILE" ] || fail 'COMPOSE_ENV_FILE is required'
[ -n "$COMPOSE_FILE" ] || fail 'COMPOSE_FILE is required'
[ -f "$COMPOSE_ENV_FILE" ] || fail "Compose environment file not found: $COMPOSE_ENV_FILE"
[ -f "$COMPOSE_FILE" ] || fail "Compose file not found: $COMPOSE_FILE"
mkdir -p "$ROLLBACK_STATE_ROOT"
chmod 700 "$ROLLBACK_STATE_ROOT"

action=${1:-}
[ "$#" -gt 0 ] && shift
case "$action" in
    deploy) deploy_services "$@" ;;
    manual) manual_rollback "$@" ;;
    *) fail 'usage: rollback.sh deploy|manual <service>...' ;;
esac
