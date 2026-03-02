#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GRAMMAR_CASES_DIR="$REPO_ROOT/unsupported/grammar"
WORK_DIR="${TMPDIR:-/tmp}/tsj-unsupported-progress"
JARINTEROP_RUNNER="$REPO_ROOT/unsupported/jarinterop/run_progress.sh"

if [ ! -d "$GRAMMAR_CASES_DIR" ]; then
  echo "missing cases dir: $GRAMMAR_CASES_DIR" >&2
  exit 2
fi

mkdir -p "$WORK_DIR"

grammar_total=0
grammar_passed=0
grammar_failed=0

echo "running unsupported grammar progression suite from $GRAMMAR_CASES_DIR"
echo

while IFS= read -r case_file; do
  grammar_total=$((grammar_total + 1))
  case_name="$(basename "$case_file")"
  out_dir="$WORK_DIR/${case_name%.ts}"

  node_out=""
  node_status=0
  if ! node_out="$(node --no-warnings --experimental-strip-types "$case_file" 2>&1)"; then
    node_status=1
  fi

  tsj_raw="$(mvn -B -ntp -q -f "$REPO_ROOT/cli/pom.xml" exec:java \
    -Dexec.mainClass=dev.tsj.cli.TsjCli \
    -Dexec.args="run $case_file --out $out_dir" 2>&1 || true)"
  tsj_diag="$(printf '%s\n' "$tsj_raw" | rg '^\{"level":' | tail -n 1 || true)"
  tsj_code="$(printf '%s' "$tsj_diag" | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')"
  tsj_out="$(printf '%s\n' "$tsj_raw" | rg -v '^\{"level":' || true)"

  if [ "$node_status" -eq 0 ] && [ "$tsj_code" = "TSJ-RUN-SUCCESS" ] && [ "$tsj_out" = "$node_out" ]; then
    grammar_passed=$((grammar_passed + 1))
    printf 'PASS | %s\n' "$case_name"
    continue
  fi

  grammar_failed=$((grammar_failed + 1))
  printf 'FAIL | %s\n' "$case_name"
  printf '  node_status: %s\n' "$node_status"
  printf '  node_out: %s\n' "$node_out"
  printf '  tsj_code: %s\n' "${tsj_code:-NO_CODE}"
  printf '  tsj_out: %s\n' "$tsj_out"
done < <(find "$GRAMMAR_CASES_DIR" -maxdepth 1 -type f -name '[0-9][0-9][0-9]_*.ts' | sort)

echo
printf 'grammar summary: total=%s passed=%s failed=%s\n' "$grammar_total" "$grammar_passed" "$grammar_failed"

overall_total="$grammar_total"
overall_passed="$grammar_passed"
overall_failed="$grammar_failed"
suite_failed=0
if [ "$grammar_failed" -gt 0 ]; then
  suite_failed=1
fi

if [ -x "$JARINTEROP_RUNNER" ]; then
  echo
  jar_output="$("$JARINTEROP_RUNNER" 2>&1)"
  jar_status=$?
  printf '%s\n' "$jar_output"

  jar_summary="$(printf '%s\n' "$jar_output" | rg '^summary:' | tail -n 1 || true)"
  jar_total="$(printf '%s' "$jar_summary" | sed -n 's/.*total=\([0-9][0-9]*\).*/\1/p')"
  jar_passed="$(printf '%s' "$jar_summary" | sed -n 's/.*passed=\([0-9][0-9]*\).*/\1/p')"
  jar_failed="$(printf '%s' "$jar_summary" | sed -n 's/.*failed=\([0-9][0-9]*\).*/\1/p')"
  jar_total="${jar_total:-0}"
  jar_passed="${jar_passed:-0}"
  jar_failed="${jar_failed:-0}"

  overall_total=$((overall_total + jar_total))
  overall_passed=$((overall_passed + jar_passed))
  overall_failed=$((overall_failed + jar_failed))

  if [ "$jar_status" -ne 0 ]; then
    suite_failed=1
  fi
fi

echo
printf 'overall summary: total=%s passed=%s failed=%s\n' "$overall_total" "$overall_passed" "$overall_failed"

if [ "$suite_failed" -ne 0 ]; then
  exit 1
fi
