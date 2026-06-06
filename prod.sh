#!/usr/bin/env bash
set -euo pipefail

SETTINGS="deploy/docker/settings.xml"
if [[ ! -f "$SETTINGS" ]]; then
  echo "Error: missing $SETTINGS (required to build the backend)." >&2
  exit 1
fi

export DOCKER_BUILDKIT=1
docker compose -f docker-compose.prod.yml up --build -d
