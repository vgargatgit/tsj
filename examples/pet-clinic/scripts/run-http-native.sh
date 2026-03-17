#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"

NATIVE_ROOT="${TMPDIR:-/tmp}/tsj-pet-clinic-http"
NATIVE_DEPS_DIR="$NATIVE_ROOT/deps/lib"
NATIVE_OUT_DIR="$NATIVE_ROOT/out"
DEPS_STAMP_FILE="$NATIVE_ROOT/deps/pom.sha256"

now_ms() {
  date +%s%3N
}

log() {
  printf '[pet-clinic-native] %s\n' "$*"
}

phase_run() {
  local phase="$1"
  shift

  local start_ms
  start_ms="$(now_ms)"
  log "phase=$phase status=start"

  "$@" &
  local command_pid=$!

  while kill -0 "$command_pid" >/dev/null 2>&1; do
    sleep 10
    if kill -0 "$command_pid" >/dev/null 2>&1; then
      local elapsed_ms
      elapsed_ms="$(( $(now_ms) - start_ms ))"
      log "phase=$phase status=running elapsed_ms=$elapsed_ms"
    fi
  done

  wait "$command_pid"

  local end_ms
  end_ms="$(now_ms)"
  log "phase=$phase status=done elapsed_ms=$((end_ms - start_ms))"
}

pom_fingerprint() {
  sha256sum "$ROOT_DIR/pom.xml" | awk '{print $1}'
}

ensure_native_dependencies() {
  local fingerprint
  fingerprint="$(pom_fingerprint)"

  mkdir -p "$(dirname "$DEPS_STAMP_FILE")"

  if [ -f "$DEPS_STAMP_FILE" ] \
    && [ -d "$NATIVE_DEPS_DIR" ] \
    && [ "$(cat "$DEPS_STAMP_FILE")" = "$fingerprint" ] \
    && find "$NATIVE_DEPS_DIR" -maxdepth 1 -type f -name '*.jar' | grep -q .; then
    local jar_count
    jar_count="$(find "$NATIVE_DEPS_DIR" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d '[:space:]')"
    log "phase=resolve_deps status=cache-hit jars=$jar_count dir=$NATIVE_DEPS_DIR"
    return
  fi

  rm -rf "$NATIVE_DEPS_DIR"
  mkdir -p "$NATIVE_DEPS_DIR"

  phase_run resolve_deps mvn -B -ntp -q -f "$ROOT_DIR/pom.xml" dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="$NATIVE_DEPS_DIR" \
    -Dmdep.useRepositoryLayout=false

  printf '%s\n' "$fingerprint" > "$DEPS_STAMP_FILE"

  local jar_count
  jar_count="$(find "$NATIVE_DEPS_DIR" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d '[:space:]')"
  if [ "$jar_count" -eq 0 ]; then
    echo "No Spring jars were resolved into $NATIVE_DEPS_DIR"
    exit 1
  fi
  log "phase=resolve_deps status=ready jars=$jar_count dir=$NATIVE_DEPS_DIR"
}

main() {
  local total_start_ms
  total_start_ms="$(now_ms)"

  log "mode=native-tmp native_root=$NATIVE_ROOT"
  ensure_native_dependencies

  mapfile -t spring_jars < <(find "$NATIVE_DEPS_DIR" -maxdepth 1 -type f -name '*.jar' | sort)
  local spring_classpath
  spring_classpath="$(IFS=:; echo "${spring_jars[*]}")"

  rm -rf "$NATIVE_OUT_DIR"
  mkdir -p "$NATIVE_OUT_DIR"

  phase_run maven_install \
    bash -lc "cd '$REPO_ROOT' && mvn -B -ntp -q -pl cli -am -DskipTests install"

  local package_args
  package_args="package examples/pet-clinic/http-main.ts --out $NATIVE_OUT_DIR --classpath $spring_classpath --interop-policy broad --ack-interop-risk --mode jvm-strict --resource-dir examples/pet-clinic/resources"
  phase_run package \
    bash -lc "cd '$REPO_ROOT' && mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args=\"$package_args\""

  local jar_path
  jar_path="$NATIVE_OUT_DIR/tsj-app.jar"
  if [ ! -f "$jar_path" ]; then
    echo "PET-CLINIC HTTP RESULT: FAIL (packaged jar missing: $jar_path)"
    exit 1
  fi

  log "phase=package status=published jar=$jar_path"
  log "Starting Pet Clinic HTTP server on http://127.0.0.1:8080"
  log "Try: curl 'http://127.0.0.1:8080/api/petclinic/owners?lastName=Frank'"
  log "Try: curl 'http://127.0.0.1:8080/api/petclinic/owners/1/pets'"
  log "Try: curl 'http://127.0.0.1:8080/v3/api-docs'"
  log "total_elapsed_before_boot_ms=$(( $(now_ms) - total_start_ms ))"

  exec java -jar "$jar_path" \
    --server.address=127.0.0.1 \
    --server.port=8080
}

main "$@"
