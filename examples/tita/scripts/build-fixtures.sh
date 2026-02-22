#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/.build/fixtures"
DEPS_DIR="$ROOT_DIR/deps"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/base-classes" "$BUILD_DIR/v11-classes" "$BUILD_DIR/dup-classes" "$BUILD_DIR/app-conflict-classes"
mkdir -p "$DEPS_DIR"

find "$ROOT_DIR/fixtures-src/base" -name "*.java" | sort > "$BUILD_DIR/base-sources.txt"
javac -parameters -d "$BUILD_DIR/base-classes" @"$BUILD_DIR/base-sources.txt"

find "$ROOT_DIR/fixtures-src/v11" -name "*.java" | sort > "$BUILD_DIR/v11-sources.txt"
javac --release 11 -parameters -cp "$BUILD_DIR/base-classes" -d "$BUILD_DIR/v11-classes" @"$BUILD_DIR/v11-sources.txt"

cat > "$BUILD_DIR/manifest.mf" <<'MANIFEST'
Manifest-Version: 1.0
Automatic-Module-Name: dev.tita.fixtures
Multi-Release: true

MANIFEST

jar --create \
  --file "$DEPS_DIR/tita-fixtures-1.0.jar" \
  --manifest "$BUILD_DIR/manifest.mf" \
  -C "$BUILD_DIR/base-classes" . \
  --release 11 \
  -C "$BUILD_DIR/v11-classes" .

find "$ROOT_DIR/fixtures-src/duplicate" -name "*.java" | sort > "$BUILD_DIR/dup-sources.txt"
javac -parameters -d "$BUILD_DIR/dup-classes" @"$BUILD_DIR/dup-sources.txt"
jar --create --file "$DEPS_DIR/tita-duplicates-1.0.jar" -C "$BUILD_DIR/dup-classes" .

find "$ROOT_DIR/fixtures-src/app-conflict" -name "*.java" | sort > "$BUILD_DIR/app-conflict-sources.txt"
javac -parameters -d "$BUILD_DIR/app-conflict-classes" @"$BUILD_DIR/app-conflict-sources.txt"
jar --create --file "$DEPS_DIR/tita-app-conflict-1.0.jar" -C "$BUILD_DIR/app-conflict-classes" .

echo "Built fixture jars:"
echo " - $DEPS_DIR/tita-fixtures-1.0.jar"
echo " - $DEPS_DIR/tita-duplicates-1.0.jar"
echo " - $DEPS_DIR/tita-app-conflict-1.0.jar"
