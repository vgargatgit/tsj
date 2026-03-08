#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
DEPS_DIR="$ROOT_DIR/deps"
LIB_DIR="$DEPS_DIR/lib"
OUT_DIR="$ROOT_DIR/.build/run"

bash "$SCRIPT_DIR/resolve-deps.sh"

mapfile -t spring_jars < <(find "$LIB_DIR" -maxdepth 1 -type f -name '*.jar' | sort)
if [ "${#spring_jars[@]}" -eq 0 ]; then
  echo "Missing Spring jars under $LIB_DIR"
  exit 1
fi

full_classpath="$(IFS=:; echo "${spring_jars[*]}")"

extract_diag_code() {
  printf '%s\n' "$1" | grep -E '^\{"level":' | tail -n 1 | sed -n 's/.*"code":"\([^"]*\)".*/\1/p'
}

run_cli() {
  local cli_args="$1"
  (cd "$REPO_ROOT" && mvn -B -ntp -q -f cli/pom.xml exec:java \
    -Dexec.mainClass=dev.tsj.cli.TsjCli \
    -Dexec.args="$cli_args" 2>&1 || true)
}

mkdir -p "$OUT_DIR"

build_raw="$(cd "$REPO_ROOT" && mvn -B -ntp -q -pl cli -am -DskipTests install 2>&1 || true)"
printf '%s\n' "$build_raw"
if printf '%s\n' "$build_raw" | grep -qE '^\[ERROR\]'; then
  echo
  echo "PET-CLINIC RESULT: FAIL (reactor install failed before CLI invocation)"
  exit 1
fi

compile_args="compile examples/pet-clinic/main.ts --out $OUT_DIR/compile --classpath $full_classpath --interop-policy broad --ack-interop-risk --mode jvm-strict"
compile_raw="$(run_cli "$compile_args")"
printf '%s\n' "$compile_raw"

if printf '%s\n' "$compile_raw" | grep -qE '"level":"ERROR"'; then
  code="$(extract_diag_code "$compile_raw")"
  echo
  echo "PET-CLINIC RESULT: FAIL (compile error: ${code:-UNKNOWN})"
  exit 1
fi
if printf '%s\n' "$compile_raw" | grep -qE '^\[ERROR\]'; then
  echo
  echo "PET-CLINIC RESULT: FAIL (compile invocation failed before TSJ diagnostics)"
  exit 1
fi

run_args="run examples/pet-clinic/main.ts --out $OUT_DIR/run --classpath $full_classpath --interop-policy broad --ack-interop-risk --mode jvm-strict"
run_raw="$(run_cli "$run_args")"
printf '%s\n' "$run_raw"

if printf '%s\n' "$run_raw" | grep -qE '"level":"ERROR"'; then
  code="$(extract_diag_code "$run_raw")"
  echo
  echo "PET-CLINIC RESULT: FAIL (run error: ${code:-UNKNOWN})"
  exit 1
fi
if printf '%s\n' "$run_raw" | grep -qE '^\[ERROR\]'; then
  echo
  echo "PET-CLINIC RESULT: FAIL (run invocation failed before TSJ diagnostics)"
  exit 1
fi

stdout_lines="$(printf '%s\n' "$run_raw" | grep -v '^\{' || true)"
boot_count="$(printf '%s\n' "$stdout_lines" | grep -c '^tsj-pet-clinic-boot$' || true)"

if [ "$boot_count" -ne 1 ]; then
  echo
  echo "PET-CLINIC RESULT: FAIL (missing boot marker tsj-pet-clinic-boot)"
  exit 1
fi

echo
echo "PET-CLINIC RESULT: PASS (strict compile + run)"
echo "Note: this script validates TS-only strict compile/run path."
echo "To start a live HTTP server on :8080, run: bash examples/pet-clinic/scripts/run-http.sh"
