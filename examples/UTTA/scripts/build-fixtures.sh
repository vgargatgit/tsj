#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_DIR="$ROOT_DIR/fixtures-src/utta"
OUT_DIR="$ROOT_DIR/.build/fixtures"
DEPS_DIR="$ROOT_DIR/deps"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR" "$DEPS_DIR"

# Compile all Java sources
find "$SRC_DIR" -name '*.java' | xargs javac -d "$OUT_DIR" --release 21

# Package into JAR
(cd "$OUT_DIR" && jar cf "$DEPS_DIR/utta.jar" .)

echo "Built UTTA fixture jar:"
echo "  - $DEPS_DIR/utta.jar"
jar tf "$DEPS_DIR/utta.jar" | grep '\.class$' | sed 's/^/    /'
