#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${BASE_DIR}/dist/milvus-offline-package"
ARCHIVE="${BASE_DIR}/dist/milvus-offline-package.tar.gz"

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}/images" "${BASE_DIR}/dist"

cp "${BASE_DIR}/docker-compose.yml" "${OUT_DIR}/docker-compose.yml"
cp "${BASE_DIR}/.env.example" "${OUT_DIR}/.env"
cp "${BASE_DIR}/images.txt" "${OUT_DIR}/images.txt"
cp -R "${BASE_DIR}/scripts" "${OUT_DIR}/scripts"

while IFS= read -r image; do
  [[ -z "${image}" || "${image}" =~ ^# ]] && continue
  safe_name="$(echo "${image}" | tr '/:' '__')"
  echo "[pull] ${image}"
  docker pull "${image}"
  echo "[save] ${image}"
  docker save "${image}" -o "${OUT_DIR}/images/${safe_name}.tar"
done < "${BASE_DIR}/images.txt"

(
  cd "${OUT_DIR}"
  find images -type f -name "*.tar" -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)

tar -C "${BASE_DIR}/dist" -czf "${ARCHIVE}" milvus-offline-package
echo "Offline package created: ${ARCHIVE}"

