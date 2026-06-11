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

if docker compose version >/dev/null 2>&1; then
  docker compose ps
else
  docker-compose ps
fi

echo
echo "[milvus health]"
curl -fsS "http://127.0.0.1:${MILVUS_WEBUI_PORT:-9091}/healthz" && echo

echo
echo "[ports]"
docker ps --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
