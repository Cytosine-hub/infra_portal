#!/bin/sh
set -eu

IMAGE_NAMESPACE=${IMAGE_NAMESPACE:-infra-portal}
BUSINESS_IMAGE_KEEP_COUNT=${BUSINESS_IMAGE_KEEP_COUNT:-3}
BUSINESS_IMAGE_SERVICES=${BUSINESS_IMAGE_SERVICES:-api-gateway core-service ai-service community-service middleware-service database-service host-service network-service security-service frontend}
DOCKER_COMMAND=${DOCKER_COMMAND:-docker}

case "$BUSINESS_IMAGE_KEEP_COUNT" in
    ''|*[!0-9]*|0)
        printf '%s\n' '- Subtask failure: BUSINESS_IMAGE_KEEP_COUNT must be a positive integer' >&2
        exit 1
        ;;
esac

TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/infra-portal-image-cleanup.XXXXXX")
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

printf '%s\n' '+ Task start: clean expired business images'

for service_name in $BUSINESS_IMAGE_SERVICES; do
    repository="$IMAGE_NAMESPACE/$service_name"
    listed_images="$TMP_ROOT/$service_name.list"
    sorted_images="$TMP_ROOT/$service_name.sorted"
    expired_images="$TMP_ROOT/$service_name.expired"

    "$DOCKER_COMMAND" image ls \
        --filter "reference=$repository:*" \
        --format '{{.CreatedAt}} {{.Repository}}:{{.Tag}}' > "$listed_images"

    awk -v prefix="$repository:" '
        {
            image_ref = $NF
            if (index(image_ref, prefix) != 1) {
                next
            }
            tag = substr(image_ref, length(prefix) + 1)
            if (tag ~ /^[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]$/) {
                print
            }
        }
    ' "$listed_images" | LC_ALL=C sort -r > "$sorted_images"

    awk -v keep_count="$BUSINESS_IMAGE_KEEP_COUNT" \
        'NR > keep_count { print $NF }' "$sorted_images" > "$expired_images"

    if [ ! -s "$expired_images" ]; then
        printf '| Task info: %s has no expired images\n' "$repository"
        continue
    fi

    while IFS= read -r image_ref; do
        referencing_containers=$("$DOCKER_COMMAND" container ls --all \
            --filter "ancestor=$image_ref" --quiet)
        if [ -n "$referencing_containers" ]; then
            printf '| Task info: retained image referenced by a container: %s\n' \
                "$image_ref"
            continue
        fi

        "$DOCKER_COMMAND" image rm "$image_ref"
        printf '| Subtask success: removed %s\n' "$image_ref"
    done < "$expired_images"
done

printf '%s\n' '* Task complete: expired business image cleanup'
