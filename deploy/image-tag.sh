#!/bin/sh
set -eu

commit_sha=${1:-${CI_COMMIT_SHA:-}}
pipeline_created_at=${2:-${CI_PIPELINE_CREATED_AT:-}}

case "$commit_sha" in
    ''|*[!0-9a-fA-F]*)
        printf '%s\n' '- Subtask failure: commit SHA must be hexadecimal' >&2
        exit 1
        ;;
esac

if [ "${#commit_sha}" -lt 7 ]; then
    printf '%s\n' '- Subtask failure: commit SHA must contain at least 7 characters' >&2
    exit 1
fi

if [ -z "$pipeline_created_at" ]; then
    printf '%s\n' '- Subtask failure: pipeline creation time is required' >&2
    exit 1
fi

# GitLab timestamps are UTC RFC 3339 values. CST-8 is a POSIX TZ value and
# therefore does not depend on tzdata being installed in the Docker CLI image.
normalized_timestamp=$(printf '%s' "$pipeline_created_at" \
    | sed 's/\.[0-9][0-9]*Z$/Z/')

if timestamp_epoch=$(TZ=UTC date -D '%Y-%m-%dT%H:%M:%SZ' \
    -d "$normalized_timestamp" '+%s' 2>/dev/null); then
    image_date=$(TZ=CST-8 date -d "@$timestamp_epoch" '+%Y%m%d')
elif image_date=$(TZ=CST-8 date -d "$pipeline_created_at" \
    '+%Y%m%d' 2>/dev/null); then
    :
else
    timestamp_epoch=$(TZ=UTC date -j -f '%Y-%m-%dT%H:%M:%SZ' \
        "$normalized_timestamp" '+%s' 2>/dev/null) || {
        printf '%s\n' '- Subtask failure: invalid pipeline creation time' >&2
        exit 1
    }
    image_date=$(TZ=CST-8 date -r "$timestamp_epoch" '+%Y%m%d')
fi

short_sha=$(printf '%.7s' "$commit_sha" | tr 'A-F' 'a-f')
printf '%s-%s\n' "$image_date" "$short_sha"
