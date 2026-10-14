#!/usr/bin/env bash
# Tests for the e2e helper scripts: sh scripts/tests/run.sh
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPTS="$(dirname "$HERE")"
TMP="$(mktemp -d)"
fails=0

check() { # name expected actual
  if [[ "$2" == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fails=$((fails + 1)); fi
}

# --- free-ports.sh -----------------------------------------------------------
out=$(FREE_PORTS_BUSY="" sh "$SCRIPTS/free-ports.sh" --offset-only)
check "free-ports: nothing busy gives offset 0" "0" "$out"

out=$(FREE_PORTS_BUSY="5173 8080" sh "$SCRIPTS/free-ports.sh" --offset-only)
check "free-ports: default stack busy gives offset 1" "1" "$out"

out=$(FREE_PORTS_BUSY="5173 8080 5174 5007" sh "$SCRIPTS/free-ports.sh" --offset-only)
check "free-ports: any one busy port of the triple rejects the offset" "3" "$out"

out=$(FREE_PORTS_BUSY="5173" sh "$SCRIPTS/free-ports.sh" --offset-only)
check "free-ports: frontend port alone busy" "1" "$out"

FREE_PORTS_BUSY="5173" sh "$SCRIPTS/free-ports.sh" "$TMP/ports.yml" > "$TMP/out.txt"
check "free-ports: file has backend ports" "1" "$(grep -c '8081:8080' "$TMP/ports.yml")"
check "free-ports: file has debug port" "1" "$(grep -c '5006:5005' "$TMP/ports.yml")"
check "free-ports: file has frontend port" "1" "$(grep -c '5174:5173' "$TMP/ports.yml")"
check "free-ports: file sets the HMR client port" "1" "$(grep -c 'VITE_HMR_CLIENT_PORT=5174' "$TMP/ports.yml")"
check "free-ports: prints the base URL" "1" "$(grep -c '^URL: http://localhost:5174' "$TMP/out.txt")"

# --- e2e-watchdog.sh ---------------------------------------------------------
# A command that finishes quickly with a steady clock is valid and keeps its exit code.
echo 1000 > "$TMP/now"
WATCHDOG_TICK=1 WATCHDOG_FAKE_NOW_FILE="$TMP/now" sh "$SCRIPTS/e2e-watchdog.sh" -- sh -c 'sleep 1; exit 0' > /dev/null 2>&1
check "watchdog: steady clock and success exits 0" "0" "$?"

echo 1000 > "$TMP/now"
WATCHDOG_TICK=1 WATCHDOG_FAKE_NOW_FILE="$TMP/now" sh "$SCRIPTS/e2e-watchdog.sh" -- sh -c 'sleep 1; exit 1' > /dev/null 2>&1
check "watchdog: a real failure keeps exit 1" "1" "$?"

# The wall clock jumps by an hour while the machine sleeps: the run is invalid whatever its own result.
echo 1000 > "$TMP/now"
WATCHDOG_TICK=1 WATCHDOG_GAP=30 WATCHDOG_FAKE_NOW_FILE="$TMP/now" \
  sh "$SCRIPTS/e2e-watchdog.sh" -- sh -c "sleep 1; echo 5000 > $TMP/now; sleep 2; exit 0" > "$TMP/wd.txt" 2>&1
check "watchdog: clock jump marks the run invalid (exit 3)" "3" "$?"
check "watchdog: says why" "1" "$(grep -c 'INVALID' "$TMP/wd.txt")"

echo 1000 > "$TMP/now"
WATCHDOG_TICK=1 WATCHDOG_GAP=30 WATCHDOG_FAKE_NOW_FILE="$TMP/now" \
  sh "$SCRIPTS/e2e-watchdog.sh" -- sh -c "sleep 1; echo 5000 > $TMP/now; sleep 2; exit 1" > /dev/null 2>&1
check "watchdog: invalid wins over a failure" "3" "$?"

echo "$fails failure(s)"
[[ $fails -eq 0 ]]
