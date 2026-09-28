#!/usr/bin/env bash
set -euo pipefail

SETTINGS="deploy/docker/settings.xml"
if [[ ! -f "$SETTINGS" ]]; then
  echo "Error: missing $SETTINGS (required to build the backend)." >&2
  exit 1
fi
if ! docker network inspect web > /dev/null 2>&1; then
  echo "Error: Docker network 'web' not found. Start the gateway first (github.com/KevinKib/gateway)." >&2
  exit 1
fi

export DOCKER_BUILDKIT=1
docker compose -f docker-compose.prod.yml up --build -d
