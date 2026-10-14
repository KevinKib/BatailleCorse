#!/bin/sh
# Runs `cypress run` N times in a row for flakiness series, with the machine kept awake for the whole loop and
# every run under the standby watchdog.
#
#   sh scripts/e2e-loop.sh <runs> [cypress run arguments...]
#   sh scripts/e2e-loop.sh 10 --browser electron --config baseUrl=http://localhost:5175 --spec cypress/specs/x.cy.ts
#
# Prints one line per run (PASS, FAIL, or INVALID when the machine slept) and a summary. INVALID runs do not
# count as failures; the exit code is 0 only when no run failed and none was invalid.
# Rules: plugged in, lid open (a closed lid overrides the keep-awake request).
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
RUNS="${1:-}"
case "$RUNS" in
  ''|*[!0-9]*) echo "Usage: e2e-loop.sh <runs> [cypress run arguments...]" >&2; exit 2 ;;
esac
shift

SENTINEL="$(mktemp)"
HOLDER=""
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    powershell.exe -NoProfile -NonInteractive -File "$(cygpath -w "$HERE/keep-awake.ps1")" \
      -Sentinel "$(cygpath -w "$SENTINEL")" >/dev/null 2>&1 &
    HOLDER=$!
    ;;
esac
release() {
  rm -f "$SENTINEL" 2>/dev/null || : > /dev/null
  [ -n "$HOLDER" ] && wait "$HOLDER" 2>/dev/null
}
trap release EXIT INT TERM

cd "$HERE/../frontend" || exit 2
pass=0; fail=0; invalid=0
i=1
while [ "$i" -le "$RUNS" ]; do
  sh "$HERE/e2e-watchdog.sh" -- npx cypress run "$@"
  case $? in
    0) pass=$((pass + 1)); echo "run $i/$RUNS: PASS" ;;
    3) invalid=$((invalid + 1)); echo "run $i/$RUNS: INVALID (machine suspended)" ;;
    *) fail=$((fail + 1)); echo "run $i/$RUNS: FAIL" ;;
  esac
  i=$((i + 1))
done

echo "Summary: $pass passed, $fail failed, $invalid invalid out of $RUNS."
[ "$fail" -eq 0 ] && [ "$invalid" -eq 0 ]
