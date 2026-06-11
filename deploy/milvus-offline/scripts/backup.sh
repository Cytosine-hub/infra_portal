#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${1:-/backup}"
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "${BASE_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${BASE_DIR}/.env"
  set +a
fi

DATA_DIR="${MILVUS_DATA_DIR:-/data/milvus}"
TS="$(date +%Y%m%d_%H%M%S)"
OUT="${BACKUP_DIR}/milvus_${TS}.tar.gz"

mkdir -p "${BACKUP_DIR}"

echo "Stopping Milvus containers before filesystem backup..."
docker stop milvus-standalone milvus-etcd milvus-minio
tar -czf "${OUT}" -C "$(dirname "${DATA_DIR}")" "$(basename "${DATA_DIR}")"
docker start milvus-etcd milvus-minio milvus-standalone

echo "Backup created: ${OUT}"
