#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "${BASE_DIR}/SHA256SUMS" ]]; then
  (cd "${BASE_DIR}" && sha256sum -c SHA256SUMS)
fi

for image_tar in "${BASE_DIR}"/images/*.tar; do
  echo "[load] ${image_tar}"
  docker load -i "${image_tar}"
done

docker images | grep -E "milvus|etcd|minio" || true

