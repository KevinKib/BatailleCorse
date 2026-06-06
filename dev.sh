#!/usr/bin/env bash
set -euo pipefail

SETTINGS="deploy/docker/settings.xml"
if [[ ! -f "$SETTINGS" ]]; then
  echo "Error: missing $SETTINGS (required to resolve backend dependencies)." >&2
  exit 1
fi

docker compose -f docker-compose.dev.yml up --build
