#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/.build/fixtures"
DEPS_DIR="$ROOT_DIR/deps"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$DEPS_DIR"

find "$ROOT_DIR/fixtures-src/complex" -name "*.java" | sort > "$BUILD_DIR/sources.txt"
javac -parameters -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
jar --create --file "$DEPS_DIR/xtta-complex.jar" -C "$BUILD_DIR/classes" .

echo "Built XTTA fixture jars:"
echo "  - $DEPS_DIR/xtta-complex.jar"
