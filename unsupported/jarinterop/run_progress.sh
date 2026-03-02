#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CASES_DIR="$REPO_ROOT/unsupported/jarinterop"
WORK_DIR="${TMPDIR:-/tmp}/tsj-unsupported-jarinterop"
BUILD_DIR="$WORK_DIR/build"

if [ ! -d "$CASES_DIR" ]; then
  echo "missing cases dir: $CASES_DIR" >&2
  exit 2
fi

if ! command -v javac >/dev/null 2>&1; then
  echo "missing required tool: javac" >&2
  exit 2
fi

if ! command -v jar >/dev/null 2>&1; then
  echo "missing required tool: jar" >&2
  exit 2
fi

mkdir -p "$WORK_DIR" "$BUILD_DIR"

total=0
passed=0
failed=0

extract_diag_code() {
  local raw="$1"
  printf '%s\n' "$raw" | rg '^\{"level":' | tail -n 1 | sed -n 's/.*"code":"\([^"]*\)".*/\1/p'
}

tsj_exec() {
  local args="$1"
  mvn -B -ntp -q -f "$REPO_ROOT/cli/pom.xml" exec:java \
    -Dexec.mainClass=dev.tsj.cli.TsjCli \
    -Dexec.args="$args" 2>&1 || true
}

record_result() {
  local case_name="$1"
  local expected_code="$2"
  local expected_hint="$3"
  local args="$4"

  total=$((total + 1))
  local raw
  raw="$(tsj_exec "$args")"
  local actual_code
  actual_code="$(extract_diag_code "$raw")"

  if [ "$actual_code" = "$expected_code" ]; then
    if [ -n "$expected_hint" ] && ! printf '%s' "$raw" | grep -F -q "$expected_hint"; then
      failed=$((failed + 1))
      printf 'FAIL | %s\n' "$case_name"
      printf '  expected_code: %s\n' "$expected_code"
      printf '  actual_code: %s\n' "${actual_code:-NO_CODE}"
      printf '  expected_hint: %s\n' "$expected_hint"
      printf '  raw_tail: %s\n' "$(printf '%s\n' "$raw" | tail -n 2 | tr '\n' ' ')"
      return
    fi
    passed=$((passed + 1))
    printf 'PASS | %s\n' "$case_name"
    return
  fi

  failed=$((failed + 1))
  printf 'FAIL | %s\n' "$case_name"
  printf '  expected_code: %s\n' "$expected_code"
  printf '  actual_code: %s\n' "${actual_code:-NO_CODE}"
  printf '  raw_tail: %s\n' "$(printf '%s\n' "$raw" | tail -n 2 | tr '\n' ' ')"
}

compile_class_to_dir() {
  local label="$1"
  local class_name="$2"
  local src_root="$BUILD_DIR/src/$label"
  local classes_root="$BUILD_DIR/classes/$label"
  local java_file="$src_root/${class_name//./\/}.java"

  rm -rf "$src_root" "$classes_root"
  mkdir -p "$(dirname "$java_file")" "$classes_root"
  cat > "$java_file"
  if ! javac --release 21 -d "$classes_root" "$java_file" >/dev/null 2>&1; then
    echo "failed to compile java fixture: $class_name" >&2
    exit 2
  fi
  printf '%s' "$classes_root"
}

create_plain_jar() {
  local classes_root="$1"
  local jar_path="$2"
  rm -f "$jar_path"
  if ! jar --create --file "$jar_path" -C "$classes_root" . >/dev/null 2>&1; then
    echo "failed to create jar: $jar_path" >&2
    exit 2
  fi
}

create_maven_jar() {
  local label="$1"
  local classes_root="$2"
  local jar_path="$3"
  local group_id="$4"
  local artifact_id="$5"
  local version="$6"
  local dependencies_xml="${7:-}"

  local pack_root="$BUILD_DIR/pack/$label"
  local meta_root="$pack_root/META-INF/maven/$group_id/$artifact_id"
  rm -rf "$pack_root"
  mkdir -p "$pack_root" "$meta_root"
  cp -R "$classes_root"/. "$pack_root"/
  cat > "$meta_root/pom.properties" <<EOF
groupId=$group_id
artifactId=$artifact_id
version=$version
EOF
  cat > "$meta_root/pom.xml" <<EOF
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group_id</groupId>
  <artifactId>$artifact_id</artifactId>
  <version>$version</version>
  <dependencies>
$dependencies_xml
  </dependencies>
</project>
EOF
  rm -f "$jar_path"
  if ! jar --create --file "$jar_path" -C "$pack_root" . >/dev/null 2>&1; then
    echo "failed to create maven jar: $jar_path" >&2
    exit 2
  fi
}

discover_main_class() {
  local entry_file="$1"
  local out_dir="$2"
  local raw
  raw="$(tsj_exec "compile $entry_file --out $out_dir")"
  local diag
  diag="$(printf '%s\n' "$raw" | rg '^\{"level":' | tail -n 1)"
  printf '%s' "$diag" | sed -n 's/.*"className":"\([^"]*\)".*/\1/p'
}

echo "running unsupported jar-interop progression suite from $CASES_DIR"
echo

case_001="$CASES_DIR/001_missing_target_class.ts"
record_result \
  "001_missing_target_class.ts" \
  "TSJ-RUN-006" \
  "Interop target class was not found" \
  "run $case_001 --out $WORK_DIR/case001-out --interop-policy broad --ack-interop-risk"

classes_case_002="$(compile_class_to_dir "case002-hidden" "sample.reflective.HiddenApi" <<'EOF'
package sample.reflective;

public final class HiddenApi {
    private HiddenApi() {
    }

    static String hiddenStatic() {
        return "hidden";
    }
}
EOF
)"
jar_case_002="$WORK_DIR/reflective-hidden.jar"
create_plain_jar "$classes_case_002" "$jar_case_002"
case_002="$CASES_DIR/002_non_public_member.ts"
record_result \
  "002_non_public_member.ts" \
  "TSJ-INTEROP-INVALID" \
  "was not found or not static" \
  "run $case_002 --out $WORK_DIR/case002-out --jar $jar_case_002 --interop-policy broad --ack-interop-risk"

classes_case_003="$(compile_class_to_dir "case003-conflict" "sample.conflict.ConflictLib" <<'EOF'
package sample.conflict;

public final class ConflictLib {
    private ConflictLib() {
    }
}
EOF
)"
jar_conflict_one="$WORK_DIR/conflict-lib-1.0.jar"
jar_conflict_two="$WORK_DIR/conflict-lib-2.0.jar"
create_plain_jar "$classes_case_003" "$jar_conflict_one"
create_plain_jar "$classes_case_003" "$jar_conflict_two"
case_003="$CASES_DIR/003_classpath_version_conflict.ts"
record_result \
  "003_classpath_version_conflict.ts" \
  "TSJ-CLASSPATH-CONFLICT" \
  "Classpath version conflict" \
  "compile $case_003 --out $WORK_DIR/case003-out --jar $jar_conflict_one --jar $jar_conflict_two"

classes_provided="$(compile_class_to_dir "case004-provided" "sample.scope.ProvidedOnly" <<'EOF'
package sample.scope;

public final class ProvidedOnly {
    private ProvidedOnly() {
    }

    public static String ping() {
        return "provided";
    }
}
EOF
)"
jar_provided="$WORK_DIR/provided-lib-1.0.0.jar"
create_maven_jar \
  "case004-provided" \
  "$classes_provided" \
  "$jar_provided" \
  "sample.scope" \
  "provided-lib" \
  "1.0.0"

classes_api="$(compile_class_to_dir "case004-api" "sample.scope.Api" <<'EOF'
package sample.scope;

public final class Api {
    private Api() {
    }
}
EOF
)"
api_dependencies_xml="$(cat <<'EOF'
    <dependency>
      <groupId>sample.scope</groupId>
      <artifactId>provided-lib</artifactId>
      <version>1.0.0</version>
      <scope>provided</scope>
    </dependency>
EOF
)"
jar_api="$WORK_DIR/api-lib-1.0.0.jar"
create_maven_jar \
  "case004-api" \
  "$classes_api" \
  "$jar_api" \
  "sample.scope" \
  "api-lib" \
  "1.0.0" \
  "$api_dependencies_xml"

case_004="$CASES_DIR/004_runtime_provided_scope.ts"
classpath_case_004="$jar_api${PATH_SEPARATOR:-:}$jar_provided"
record_result \
  "004_runtime_provided_scope.ts" \
  "TSJ-CLASSPATH-SCOPE" \
  "\"scope\":\"provided\"" \
  "run $case_004 --out $WORK_DIR/case004-out --classpath $classpath_case_004 --interop-policy broad --ack-interop-risk"

case_005="$CASES_DIR/005_app_isolated_conflict.ts"
probe_out="$WORK_DIR/case005-probe"
main_class="$(discover_main_class "$case_005" "$probe_out")"
if [ -z "$main_class" ]; then
  total=$((total + 1))
  failed=$((failed + 1))
  printf 'FAIL | %s\n' "005_app_isolated_conflict.ts"
  printf '  expected: discover generated main class from compile artifact\n'
  printf '  actual: unable to parse className from compile diagnostic\n'
else
  main_class_simple="${main_class##*.}"
  main_class_pkg="${main_class%.*}"
  if [ "$main_class_pkg" = "$main_class" ]; then
    main_package_line=""
  else
    main_package_line="package $main_class_pkg;"
  fi
  classes_case_005="$(compile_class_to_dir "case005-duplicate" "$main_class" <<EOF
$main_package_line

public final class $main_class_simple {
    private $main_class_simple() {
    }

    public static String marker() {
        return "dependency-shadow";
    }
}
EOF
)"
  jar_case_005="$WORK_DIR/app-duplicate-main.jar"
  create_plain_jar "$classes_case_005" "$jar_case_005"
  record_result \
    "005_app_isolated_conflict.ts" \
    "TSJ-RUN-009" \
    "app-isolated" \
    "run $case_005 --out $WORK_DIR/case005-out --classpath $jar_case_005 --classloader-isolation app-isolated"
fi

echo
printf 'summary: total=%s passed=%s failed=%s\n' "$total" "$passed" "$failed"

if [ "$failed" -gt 0 ]; then
  exit 1
fi
