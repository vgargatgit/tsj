#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/.build/fixtures"
DEPS_DIR="$ROOT_DIR/deps"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/s1-classes" "$BUILD_DIR/s2-classes" \
  "$BUILD_DIR/s3-v1-classes" "$BUILD_DIR/s3-v2-classes" \
  "$BUILD_DIR/s4-provided-classes" "$BUILD_DIR/s4-api-classes" \
  "$BUILD_DIR/s5-clash-classes"
mkdir -p "$DEPS_DIR"

# S1: api.jar — dev.jita.Api with public static ping()
javac -parameters -d "$BUILD_DIR/s1-classes" \
  "$ROOT_DIR/fixtures-src/s1-api/dev/jita/Api.java"
jar --create --file "$DEPS_DIR/api.jar" -C "$BUILD_DIR/s1-classes" .

# S2: nonpublic.jar — dev.jita.hidden.Hidden with package-private/private methods
javac -parameters -d "$BUILD_DIR/s2-classes" \
  "$ROOT_DIR/fixtures-src/s2-hidden/dev/jita/hidden/Hidden.java"
jar --create --file "$DEPS_DIR/nonpublic.jar" -C "$BUILD_DIR/s2-classes" .

# S3: dupe-lib-1.0.jar and dupe-lib-2.0.jar — same class, different return values
javac -parameters -d "$BUILD_DIR/s3-v1-classes" \
  "$ROOT_DIR/fixtures-src/s3-v1/dev/jita/dupe/Versioned.java"
jar --create --file "$DEPS_DIR/dupe-lib-1.0.jar" -C "$BUILD_DIR/s3-v1-classes" .

javac -parameters -d "$BUILD_DIR/s3-v2-classes" \
  "$ROOT_DIR/fixtures-src/s3-v2/dev/jita/dupe/Versioned.java"
jar --create --file "$DEPS_DIR/dupe-lib-2.0.jar" -C "$BUILD_DIR/s3-v2-classes" .

# S4: provided-only.jar + api-lib.jar (with Maven metadata declaring provided scope)
javac -parameters -d "$BUILD_DIR/s4-provided-classes" \
  "$ROOT_DIR/fixtures-src/s4-provided/dev/jita/provided/ProvidedApi.java"

# Create provided-only jar with Maven metadata
PACK_PROVIDED="$BUILD_DIR/pack-provided"
META_PROVIDED="$PACK_PROVIDED/META-INF/maven/dev.jita/provided-only"
mkdir -p "$META_PROVIDED"
cp -R "$BUILD_DIR/s4-provided-classes"/. "$PACK_PROVIDED"/
cat > "$META_PROVIDED/pom.properties" <<'EOF'
groupId=dev.jita
artifactId=provided-only
version=1.0.0
EOF
cat > "$META_PROVIDED/pom.xml" <<'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.jita</groupId>
  <artifactId>provided-only</artifactId>
  <version>1.0.0</version>
</project>
EOF
jar --create --file "$DEPS_DIR/provided-only.jar" -C "$PACK_PROVIDED" .

# Create api-lib jar that declares provided-only as a provided-scope dependency
javac -parameters -d "$BUILD_DIR/s4-api-classes" \
  "$ROOT_DIR/fixtures-src/s4-api/dev/jita/provided/Api.java"

PACK_API="$BUILD_DIR/pack-api"
META_API="$PACK_API/META-INF/maven/dev.jita/api-lib"
mkdir -p "$META_API"
cp -R "$BUILD_DIR/s4-api-classes"/. "$PACK_API"/
cat > "$META_API/pom.properties" <<'EOF'
groupId=dev.jita
artifactId=api-lib
version=1.0.0
EOF
cat > "$META_API/pom.xml" <<'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.jita</groupId>
  <artifactId>api-lib</artifactId>
  <version>1.0.0</version>
  <dependencies>
    <dependency>
      <groupId>dev.jita</groupId>
      <artifactId>provided-only</artifactId>
      <version>1.0.0</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
</project>
EOF
jar --create --file "$DEPS_DIR/api-lib.jar" -C "$PACK_API" .

# S5: clash-dep.jar — dev.jita.conflict.Clash
javac -parameters -d "$BUILD_DIR/s5-clash-classes" \
  "$ROOT_DIR/fixtures-src/s5-clash/dev/jita/conflict/Clash.java"
jar --create --file "$DEPS_DIR/clash-dep.jar" -C "$BUILD_DIR/s5-clash-classes" .

echo "Built JITA fixture jars:"
for f in "$DEPS_DIR"/*.jar; do
  echo "  - $f"
done
