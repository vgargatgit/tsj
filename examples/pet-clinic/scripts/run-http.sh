#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
DEPS_DIR="$ROOT_DIR/deps"
LIB_DIR="$DEPS_DIR/lib"
OUT_DIR="$ROOT_DIR/.build/http"

bash "$SCRIPT_DIR/resolve-deps.sh"

mapfile -t spring_jars < <(find "$LIB_DIR" -maxdepth 1 -type f -name '*.jar' | sort)
if [ "${#spring_jars[@]}" -eq 0 ]; then
  echo "Missing Spring jars under $LIB_DIR"
  exit 1
fi

spring_classpath="$(IFS=:; echo "${spring_jars[*]}")"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

build_raw="$(cd "$REPO_ROOT" && mvn -B -ntp -q -pl cli -am -DskipTests install 2>&1 || true)"
printf '%s\n' "$build_raw"
if printf '%s\n' "$build_raw" | grep -qE '^\[ERROR\]'; then
  echo
  echo "PET-CLINIC HTTP RESULT: FAIL (reactor install failed before CLI invocation)"
  exit 1
fi

package_args="package examples/pet-clinic/http-main.ts --out $OUT_DIR --classpath $spring_classpath --interop-policy broad --ack-interop-risk --mode jvm-strict --resource-dir examples/pet-clinic/resources"
package_raw="$(
  cd "$REPO_ROOT" && mvn -B -ntp -q -f cli/pom.xml exec:java \
    -Dexec.mainClass=dev.tsj.cli.TsjCli \
    -Dexec.args="$package_args" 2>&1 || true
)"
printf '%s\n' "$package_raw"

if printf '%s\n' "$package_raw" | grep -qE '"level":"ERROR"'; then
  echo
  echo "PET-CLINIC HTTP RESULT: FAIL (package emitted TSJ diagnostics)"
  exit 1
fi
if printf '%s\n' "$package_raw" | grep -qE '^\[ERROR\]'; then
  echo
  echo "PET-CLINIC HTTP RESULT: FAIL (package invocation failed)"
  exit 1
fi

JAR_PATH="$OUT_DIR/tsj-app.jar"
if [ ! -f "$JAR_PATH" ]; then
  echo
  echo "PET-CLINIC HTTP RESULT: FAIL (packaged jar missing: $JAR_PATH)"
  exit 1
fi

echo
echo "Starting Pet Clinic HTTP server on http://127.0.0.1:8080"
echo "Try:"
echo "  curl 'http://127.0.0.1:8080/api/petclinic/owners?lastName=Frank'"
echo "  curl 'http://127.0.0.1:8080/api/petclinic/owners/1/pets'"
echo

exec java -jar "$JAR_PATH" \
  --server.address=127.0.0.1 \
  --server.port=8080
