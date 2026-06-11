#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${BASE_DIR}/dist/milvus-offline-package"
ARCHIVE="${BASE_DIR}/dist/milvus-offline-package.tar.gz"

if [[ -f "${BASE_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${BASE_DIR}/.env"
  set +a
fi

DOCKER_PULL_RETRIES="${DOCKER_PULL_RETRIES:-3}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"
DOCKER_HUB_MIRROR_PREFIX="${DOCKER_HUB_MIRROR_PREFIX:-}"

is_docker_hub_image() {
  local image="$1"
  local first_part="${image%%/*}"
  [[ "${image}" != */* || ( "${first_part}" != *.* && "${first_part}" != *:* && "${first_part}" != "localhost" ) ]]
}

pull_once() {
  local image="$1"
  if [[ -n "${DOCKER_PLATFORM}" ]]; then
    docker pull --platform "${DOCKER_PLATFORM}" "${image}"
  else
    docker pull "${image}"
  fi
}

pull_with_retry() {
  local image="$1"
  local attempt=1
  while (( attempt <= DOCKER_PULL_RETRIES )); do
    echo "[pull] ${image} attempt=${attempt}/${DOCKER_PULL_RETRIES}"
    if pull_once "${image}"; then
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 3
  done
  return 1
}

pull_image() {
  local image="$1"
  if pull_with_retry "${image}"; then
    return 0
  fi

  if [[ -n "${DOCKER_HUB_MIRROR_PREFIX}" ]] && is_docker_hub_image "${image}"; then
    local mirror_image="${DOCKER_HUB_MIRROR_PREFIX%/}/${image}"
    echo "[mirror] official pull failed, trying ${mirror_image}"
    pull_with_retry "${mirror_image}"
    docker tag "${mirror_image}" "${image}"
    return 0
  fi

  echo "ERROR: failed to pull ${image}" >&2
  echo "If Docker Hub returns EOF, configure DOCKER_HUB_MIRROR_PREFIX in .env and retry." >&2
  return 1
}

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}/images" "${BASE_DIR}/dist"

cp "${BASE_DIR}/docker-compose.yml" "${OUT_DIR}/docker-compose.yml"
cp "${BASE_DIR}/.env.example" "${OUT_DIR}/.env"
cp "${BASE_DIR}/images.txt" "${OUT_DIR}/images.txt"
cp -R "${BASE_DIR}/scripts" "${OUT_DIR}/scripts"

while IFS= read -r image; do
  [[ -z "${image}" || "${image}" =~ ^# ]] && continue
  safe_name="$(echo "${image}" | tr '/:' '__')"
  pull_image "${image}"
  echo "[save] ${image}"
  docker save "${image}" -o "${OUT_DIR}/images/${safe_name}.tar"
done < "${BASE_DIR}/images.txt"

(
  cd "${OUT_DIR}"
  find images -type f -name "*.tar" -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)

tar -C "${BASE_DIR}/dist" -czf "${ARCHIVE}" milvus-offline-package
echo "Offline package created: ${ARCHIVE}"
