#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${BASE_DIR}"

if [[ -f "${BASE_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${BASE_DIR}/.env"
  set +a
fi

DATA_DIR="${MILVUS_DATA_DIR:-/data/milvus}"
if [[ "${MINIO_ROOT_PASSWORD:-}" == "ChangeThisMinioPassword_2026" ]]; then
  echo "ERROR: Please change MINIO_ROOT_PASSWORD in .env before starting Milvus." >&2
  exit 1
fi

mkdir -p "${DATA_DIR}/etcd" "${DATA_DIR}/minio" "${DATA_DIR}/milvus"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

compose up -d etcd minio

echo "Waiting for etcd and MinIO to initialize..."
sleep 10

compose up -d standalone

echo "Milvus is starting. Run: ./scripts/healthcheck.sh"
