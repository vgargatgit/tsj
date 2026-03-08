#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LIB_DIR="$ROOT_DIR/deps/lib"

rm -rf "$LIB_DIR"
mkdir -p "$LIB_DIR"

mvn -B -ntp -q -f "$ROOT_DIR/pom.xml" dependency:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory="$LIB_DIR" \
  -Dmdep.useRepositoryLayout=false

count="$(find "$LIB_DIR" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d '[:space:]')"
if [ "$count" -eq 0 ]; then
  echo "No Spring jars were resolved into $LIB_DIR"
  exit 1
fi

echo "Resolved Spring jars ($count):"
find "$LIB_DIR" -maxdepth 1 -type f -name '*.jar' | sort | sed 's#^#  - #'
