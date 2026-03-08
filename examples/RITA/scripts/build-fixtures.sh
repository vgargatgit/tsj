#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR/fixtures-src/rita"
BUILD_DIR="$ROOT_DIR/.build/fixtures"
DEPS_DIR="$ROOT_DIR/deps"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$DEPS_DIR"

find "$SRC_DIR" -name '*.java' | sort > "$BUILD_DIR/sources.txt"
javac --release 21 -parameters -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"

jar --create --file "$DEPS_DIR/rita-di-1.0.jar" -C "$BUILD_DIR/classes" .

echo "Built RITA fixture jar:"
echo "  - $DEPS_DIR/rita-di-1.0.jar"
jar tf "$DEPS_DIR/rita-di-1.0.jar" | grep '\.class$' | sed 's/^/    /'
