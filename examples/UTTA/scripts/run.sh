#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
DEPS_DIR="$ROOT_DIR/deps"
WORK_DIR="$ROOT_DIR/.build/run"

mkdir -p "$WORK_DIR"

total=0
passed=0
failed=0
crashed=0
results=""

extract_diag_code() {
  printf '%s\n' "$1" | grep -E '^\{"level":' | tail -n 1 | sed -n 's/.*"code":"\([^"]*\)".*/\1/p'
}

run_test() {
  local test_file="$1"
  local category="$2"
  local test_name
  test_name="$(basename "$test_file" .ts)"
  local out_dir="$WORK_DIR/$category/$test_name"
  mkdir -p "$out_dir"

  total=$((total + 1))

  local args="run $test_file --out $out_dir"

  if [ "$category" = "interop" ]; then
    args="$args --jar $DEPS_DIR/utta.jar --interop-policy broad --ack-interop-risk"
  fi

  local raw
  raw="$(cd "$REPO_ROOT" && mvn -B -ntp -q -f cli/pom.xml exec:java \
    -Dexec.mainClass=dev.tsj.cli.TsjCli \
    -Dexec.args="$args" 2>&1 || true)"

  local diag_code
  diag_code="$(extract_diag_code "$raw")"

  # Check for crash/error diagnostics
  if printf '%s\n' "$raw" | grep -qE '"level":"ERROR"'; then
    local error_msg
    error_msg="$(printf '%s\n' "$raw" | grep -E '"level":"ERROR"' | tail -1 | head -c 200)"
    printf '  ❌ CRASH  %s/%s\n' "$category" "$test_name"
    printf '           %s\n' "${diag_code:-UNKNOWN}: $(printf '%s' "$error_msg" | sed -n 's/.*"message":"\([^"]*\)".*/\1/p' | head -c 120)"
    crashed=$((crashed + 1))
    failed=$((failed + 1))
    results="${results}CRASH|${category}/${test_name}|${diag_code:-UNKNOWN}\n"
    return
  fi

  # Check for Maven errors (dependency resolution, etc.)
  if printf '%s\n' "$raw" | grep -qE '^\[ERROR\]'; then
    printf '  ❌ CRASH  %s/%s\n' "$category" "$test_name"
    printf '           MVN_ERROR\n'
    crashed=$((crashed + 1))
    failed=$((failed + 1))
    results="${results}CRASH|${category}/${test_name}|MVN_ERROR\n"
    return
  fi

  local stdout_lines
  stdout_lines="$(printf '%s\n' "$raw" | grep -v '^\{' || true)"
  local false_count
  false_count="$(printf '%s\n' "$stdout_lines" | grep -c ':false$' || true)"
  local true_count
  true_count="$(printf '%s\n' "$stdout_lines" | grep -c ':true$' || true)"
  local total_checks=$((true_count + false_count))

  if [ "$false_count" -gt 0 ]; then
    printf '  ⚠️  FAIL   %s/%s  (%s/%s checks passed)\n' "$category" "$test_name" "$true_count" "$total_checks"
    local failed_checks
    failed_checks="$(printf '%s\n' "$stdout_lines" | grep ':false$' | sed 's/:false$//' | tr '\n' ', ')"
    printf '           failed: %s\n' "$failed_checks"
    failed=$((failed + 1))
    results="${results}FAIL|${category}/${test_name}|${failed_checks}\n"
    return
  fi

  if [ "$total_checks" -eq 0 ]; then
    printf '  ⚠️  EMPTY  %s/%s  (no check output)\n' "$category" "$test_name"
    failed=$((failed + 1))
    results="${results}EMPTY|${category}/${test_name}|\n"
    return
  fi

  printf '  ✅ PASS   %s/%s  (%s checks)\n' "$category" "$test_name" "$total_checks"
  passed=$((passed + 1))
  results="${results}PASS|${category}/${test_name}|${total_checks}\n"
}

echo "UTTA — Ultimate TypeScript Torture App"
echo "======================================="
echo

echo "--- Grammar Tests ---"
for f in "$ROOT_DIR"/src/grammar/*.ts; do
  [ -f "$f" ] && run_test "$f" "grammar"
done
echo

echo "--- Interop Tests ---"
if [ -f "$DEPS_DIR/utta.jar" ]; then
  for f in "$ROOT_DIR"/src/interop/*.ts; do
    [ -f "$f" ] && run_test "$f" "interop"
  done
else
  echo "  SKIP (run build-fixtures.sh first)"
fi
echo

echo "--- Stress Tests ---"
for f in "$ROOT_DIR"/src/stress/*.ts; do
  [ -f "$f" ] && run_test "$f" "stress"
done
echo

echo "======================================="
printf 'TOTAL: %s | PASS: %s | FAIL: %s | CRASH: %s\n' "$total" "$passed" "$failed" "$crashed"
echo

if [ "$failed" -gt 0 ] || [ "$crashed" -gt 0 ]; then
  echo "FAILURES:"
  printf '%b' "$results" | grep -v '^PASS' | while IFS='|' read -r status name detail; do
    printf '  %-7s %s  %s\n' "$status" "$name" "$detail"
  done
  exit 1
fi
