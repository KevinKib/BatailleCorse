#!/bin/sh
# Runs a command and flags the run INVALID when the machine was suspended during it.
#
#   sh scripts/e2e-watchdog.sh -- npx cypress run --spec ...
#
# A monotonic timeout cannot see a standby: the clock that drives it stops with the machine (investigation
# 2026-10-14: `timeout 600` fired only after 3,739 s). The wall clock does not stop, so a heartbeat that wakes
# every WATCHDOG_TICK seconds (default 5) and finds the wall clock further ahead than WATCHDOG_GAP seconds
# (default 30) knows the machine slept. Exit code: the command's own, or 3 when a gap was seen (a run frozen by
# standby proves nothing about the app, whatever its result). WATCHDOG_FAKE_NOW_FILE replaces `date +%s` (tests).
set -u

TICK="${WATCHDOG_TICK:-5}"
GAP="${WATCHDOG_GAP:-30}"

[ "${1:-}" = "--" ] && shift
if [ "$#" -eq 0 ]; then
  echo "Usage: e2e-watchdog.sh -- <command...>" >&2
  exit 2
fi

now() {
  if [ -n "${WATCHDOG_FAKE_NOW_FILE:-}" ]; then cat "$WATCHDOG_FAKE_NOW_FILE"; else date +%s; fi
}

FLAG="$(mktemp)"

heartbeat() {
  last=$(now)
  while :; do
    sleep "$TICK"
    current=$(now)
    if [ $((current - last)) -gt "$GAP" ]; then
      echo "$((current - last))" >> "$FLAG"
    fi
    last=$current
  done
}

heartbeat &
HB=$!

"$@"
status=$?

kill "$HB" 2>/dev/null
wait "$HB" 2>/dev/null

if [ -s "$FLAG" ]; then
  gap=$(head -n 1 "$FLAG")
  echo "INVALID run: the wall clock jumped by ${gap}s (threshold ${GAP}s), the machine was suspended. Plug in, keep the lid open, rerun." >&2
  exit 3
fi
exit "$status"
