#!/usr/bin/env bash
set -euo pipefail

SETTINGS="deploy/docker/settings.xml"
if [[ ! -f "$SETTINGS" ]]; then
  echo "Error: missing $SETTINGS (required to resolve backend dependencies)." >&2
  exit 1
fi

if ! docker compose -f docker-compose.dev.yml up --build; then
  status=$?
  echo >&2
  echo "If the build failed at 'npm ci' with a lock file out-of-sync error" >&2
  echo "(e.g. 'Missing: <pkg> from lock file'), regenerate the lock with the" >&2
  echo "image's npm and retry:" >&2
  echo "    ./frontend/sync-lock.sh   # then commit frontend/package-lock.json" >&2
  exit $status
fi
