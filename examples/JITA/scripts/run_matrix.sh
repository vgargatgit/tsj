#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
DEPS_DIR="$ROOT_DIR/deps"
WORK_DIR="$ROOT_DIR/.build/matrix"
SEP="${PATH_SEPARATOR:-:}"

mkdir -p "$WORK_DIR"

total=0
passed=0
failed=0

extract_diag_code() {
  local raw="$1"
  printf '%s\n' "$raw" | grep -E '^\{"level":' | tail -n 1 | sed -n 's/.*"code":"\([^"]*\)".*/\1/p'
}

tsj_exec() {
  local args="$1"
  mvn -B -ntp -q -f "$REPO_ROOT/cli/pom.xml" exec:java \
    -Dexec.mainClass=dev.tsj.cli.TsjCli \
    -Dexec.args="$args" 2>&1 || true
}

discover_main_class() {
  local entry_file="$1"
  local out_dir="$2"
  local raw
  raw="$(tsj_exec "compile $entry_file --out $out_dir")"
  local diag
  diag="$(printf '%s\n' "$raw" | grep -E '^\{"level":' | tail -n 1)"
  printf '%s' "$diag" | sed -n 's/.*"className":"\([^"]*\)".*/\1/p'
}

check_result() {
  local scenario_name="$1"
  local phase="$2"
  local expected_code="$3"
  local raw="$4"
  local expect_file="$5"

  local actual_code
  actual_code="$(extract_diag_code "$raw")"

  if [ "$expected_code" = "PASS" ]; then
    if printf '%s\n' "$raw" | grep -qE '"level":"ERROR"'; then
      printf '  ❌ %s %s — expected pass but got error (code: %s)\n' "$scenario_name" "$phase" "${actual_code:-UNKNOWN}"
      return 1
    fi
    printf '  ✅ %s %s — pass\n' "$scenario_name" "$phase"
    return 0
  fi

  if [ "$actual_code" != "$expected_code" ]; then
    printf '  ❌ %s %s — expected %s but got %s\n' "$scenario_name" "$phase" "$expected_code" "${actual_code:-NO_CODE}"
    return 1
  fi

  # Check contains patterns from expect.json
  local contains_ok=true
  if command -v python3 >/dev/null 2>&1 && [ -f "$expect_file" ]; then
    local phase_key
    if [ "$phase" = "compile" ]; then
      phase_key="expectCompile"
    else
      phase_key="expectRun"
    fi
    local patterns
    patterns="$(python3 -c "
import json, sys
with open('$expect_file') as f:
    d = json.load(f)
node = d.get('$phase_key')
if node and 'contains' in node:
    for p in node['contains']:
        print(p)
" 2>/dev/null || true)"
    while IFS= read -r pattern; do
      if [ -n "$pattern" ] && ! printf '%s' "$raw" | grep -qF "$pattern"; then
        printf '  ❌ %s %s — code matched but missing: %s\n' "$scenario_name" "$phase" "$pattern"
        contains_ok=false
      fi
    done <<< "$patterns"
  fi

  if [ "$contains_ok" = true ]; then
    printf '  ✅ %s %s — blocked as expected (%s)\n' "$scenario_name" "$phase" "$expected_code"
    return 0
  fi
  return 1
}

run_scenario() {
  local scenario_dir="$1"
  local scenario_name
  scenario_name="$(basename "$scenario_dir")"
  local entry="$scenario_dir/main.ts"
  local expect_file="$scenario_dir/expect.json"
  local out_dir="$WORK_DIR/$scenario_name"

  if [ ! -f "$entry" ] || [ ! -f "$expect_file" ]; then
    printf 'SKIP | %s (missing main.ts or expect.json)\n' "$scenario_name"
    return
  fi

  total=$((total + 1))
  local scenario_passed=true

  printf '%s\n' "$scenario_name"

  # Parse expect.json
  local compile_code run_code
  compile_code="$(python3 -c "
import json, sys
with open('$expect_file') as f:
    d = json.load(f)
c = d.get('expectCompile')
if c is None:
    print('SKIP')
elif c.get('result') == 'pass':
    print('PASS')
else:
    print(c.get('diagnosticCode', 'UNKNOWN'))
" 2>/dev/null || echo "SKIP")"

  run_code="$(python3 -c "
import json, sys
with open('$expect_file') as f:
    d = json.load(f)
r = d.get('expectRun')
if r is None:
    print('SKIP')
elif r.get('result') == 'pass':
    print('PASS')
else:
    print(r.get('diagnosticCode', 'UNKNOWN'))
" 2>/dev/null || echo "SKIP")"

  # Scenario-specific classpath and flags
  local compile_args=""
  local run_args=""

  case "$scenario_name" in
    S1_missing_runtime_classpath)
      # Run without the jar — tests runtime class-not-found for interop targets
      run_args="run $entry --out $out_dir --interop-policy broad --ack-interop-risk"
      ;;
    S2_non_public_member)
      run_args="run $entry --out $out_dir --jar $DEPS_DIR/nonpublic.jar --interop-policy broad --ack-interop-risk"
      ;;
    S3_conflicting_versions)
      compile_args="compile $entry --out $out_dir --jar $DEPS_DIR/dupe-lib-1.0.jar --jar $DEPS_DIR/dupe-lib-2.0.jar"
      ;;
    S4_provided_scope_runtime)
      local cp="$DEPS_DIR/api-lib.jar${SEP}$DEPS_DIR/provided-only.jar"
      run_args="run $entry --out $out_dir --classpath $cp --interop-policy broad --ack-interop-risk"
      ;;
    S5_app_isolated_duplication)
      # Two-phase: discover generated main class, create conflicting jar, run
      local probe_out="$WORK_DIR/${scenario_name}-probe"
      local main_class
      main_class="$(discover_main_class "$entry" "$probe_out")"
      if [ -z "$main_class" ]; then
        printf '  ❌ %s — unable to discover generated main class\n' "$scenario_name"
        failed=$((failed + 1))
        return
      fi
      local main_class_simple="${main_class##*.}"
      local main_class_pkg="${main_class%.*}"
      local pkg_line=""
      if [ "$main_class_pkg" != "$main_class" ]; then
        pkg_line="package $main_class_pkg;"
      fi
      local dup_src="$WORK_DIR/${scenario_name}-dup-src"
      local dup_classes="$WORK_DIR/${scenario_name}-dup-classes"
      local dup_jar="$WORK_DIR/${scenario_name}-dup.jar"
      local java_file="$dup_src/${main_class//./\/}.java"
      rm -rf "$dup_src" "$dup_classes"
      mkdir -p "$(dirname "$java_file")" "$dup_classes"
      cat > "$java_file" <<EOF
$pkg_line

public final class $main_class_simple {
    private $main_class_simple() {
    }

    public static String marker() {
        return "dependency-shadow";
    }
}
EOF
      javac --release 21 -d "$dup_classes" "$java_file"
      jar --create --file "$dup_jar" -C "$dup_classes" .
      run_args="run $entry --out $out_dir --classpath $dup_jar --classloader-isolation app-isolated"
      ;;
    *)
      printf 'SKIP | %s (unknown scenario)\n' "$scenario_name"
      return
      ;;
  esac

  # Execute compile phase if applicable
  if [ "$compile_code" != "SKIP" ] && [ -n "$compile_args" ]; then
    local compile_raw
    compile_raw="$(tsj_exec "$compile_args")"
    if ! check_result "$scenario_name" "compile" "$compile_code" "$compile_raw" "$expect_file"; then
      scenario_passed=false
    fi
  fi

  # Execute run phase if applicable
  if [ "$run_code" != "SKIP" ] && [ -n "$run_args" ]; then
    local run_raw
    run_raw="$(tsj_exec "$run_args")"
    if ! check_result "$scenario_name" "run" "$run_code" "$run_raw" "$expect_file"; then
      scenario_passed=false
    fi
  fi

  if [ "$scenario_passed" = true ]; then
    passed=$((passed + 1))
  else
    failed=$((failed + 1))
  fi
}

echo "JITA — Jar Interop Torture App"
echo "=============================="
echo

# Loop through scenarios in sorted order
for scenario_dir in "$ROOT_DIR"/scenarios/S*; do
  if [ -d "$scenario_dir" ]; then
    run_scenario "$scenario_dir"
    echo
  fi
done

echo "=============================="
printf 'summary: total=%s passed=%s failed=%s\n' "$total" "$passed" "$failed"

if [ "$failed" -gt 0 ]; then
  exit 1
fi
