#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
JAR_PATH="$ROOT_DIR/deps/rita-di-1.0.jar"
OUT_DIR="$ROOT_DIR/.build/run"

if [ ! -f "$JAR_PATH" ]; then
  echo "Missing $JAR_PATH"
  echo "Run: bash examples/RITA/scripts/build-fixtures.sh"
  exit 1
fi

mkdir -p "$OUT_DIR"

raw="$(cd "$REPO_ROOT" && mvn -B -ntp -q -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/RITA/src/main.ts --out $OUT_DIR --jar $JAR_PATH --interop-policy broad --ack-interop-risk" 2>&1 || true)"

printf '%s\n' "$raw"

if printf '%s\n' "$raw" | grep -qE '"level":"ERROR"'; then
  code="$(printf '%s\n' "$raw" | grep -E '^\{"level":' | tail -n 1 | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')"
  echo
  echo "RITA RESULT: FAIL (runtime/compile error: ${code:-UNKNOWN})"
  exit 1
fi

stdout_lines="$(printf '%s\n' "$raw" | grep -v '^\{' || true)"
false_count="$(printf '%s\n' "$stdout_lines" | grep -c ':false$' || true)"
true_count="$(printf '%s\n' "$stdout_lines" | grep -c ':true$' || true)"

if [ "$false_count" -gt 0 ]; then
  echo
  echo "RITA RESULT: FAIL ($false_count false checks)"
  exit 1
fi

if [ "$true_count" -lt 6 ]; then
  echo
  echo "RITA RESULT: FAIL (expected at least 6 true checks, saw $true_count)"
  exit 1
fi

echo
echo "RITA RESULT: PASS ($true_count checks)"
