# JITA — Jar Interop Torture App

A repo with **5 scenario fixtures**, each designed to trip exactly one jar-interop shortfall.
Each scenario has a TypeScript entry file, controlled jar/classpath inputs, and an `expect.json`
defining whether it should pass or fail and which diagnostic code to expect.

## Layout

```text
examples/JITA/
  SPEC.md
  README.md
  jita.json
  fixtures-src/          # Java source files for fixture jars
  deps/                  # Built fixture jars (gitignored build output)
  scenarios/
    S1_missing_runtime_classpath/
      main.ts
      expect.json
    S2_non_public_member/
      main.ts
      expect.json
    S3_conflicting_versions/
      main.ts
      expect.json
    S4_provided_scope_runtime/
      main.ts
      expect.json
    S5_app_isolated_duplication/
      main.ts
      expect.json
  scripts/
    build-fixtures.sh    # Compile Java → jars
    run_matrix.sh        # Run all scenarios and report
```

## Build Fixture Jars

```bash
bash examples/JITA/scripts/build-fixtures.sh
```

This creates:
- `deps/api.jar` — S1: `dev.jita.Api` with `ping()`
- `deps/nonpublic.jar` — S2: `dev.jita.hidden.Hidden` with non-public methods
- `deps/dupe-lib-1.0.jar` — S3: `dev.jita.dupe.Versioned` returning `"1.0"`
- `deps/dupe-lib-2.0.jar` — S3: `dev.jita.dupe.Versioned` returning `"2.0"`
- `deps/provided-only.jar` — S4: `dev.jita.provided.ProvidedApi` (Maven metadata)
- `deps/api-lib.jar` — S4: declares provided-only as `<scope>provided</scope>` dep
- `deps/clash-dep.jar` — S5: `dev.jita.conflict.Clash`

## Run the Matrix

```bash
bash examples/JITA/scripts/run_matrix.sh
```

Output legend:
- ✅ scenario still blocked (expected fail, saw correct diagnostic)
- ✅ scenario now supported (expected pass, succeeded)
- ❌ regression (expected pass but failed, or expected fail but passed, or wrong diagnostic)

## Scenarios

| # | Scenario | Expected Diagnostic | Tests |
|---|----------|-------------------|-------|
| S1 | Missing runtime classpath | `TSJ-RUN-006` | Compile-time vs runtime classloading |
| S2 | Non-public member access | `TSJ-INTEROP-INVALID` | Visibility enforcement |
| S3 | Conflicting jar versions | `TSJ-CLASSPATH-CONFLICT` | Version conflict detection |
| S4 | Provided-scope at runtime | `TSJ-CLASSPATH-SCOPE` | Scope-aware classpath filtering |
| S5 | App-isolated duplication | `TSJ-RUN-009` | Classloader isolation conflicts |

## Manual Execution

Each scenario can be run individually via `mvn exec:java`:

### S1 — Compile (should pass)

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile examples/JITA/scenarios/S1_missing_runtime_classpath/main.ts \
    --out examples/JITA/.build/matrix/S1_missing_runtime_classpath \
    --jar examples/JITA/deps/api.jar \
    --interop-policy broad --ack-interop-risk"
```

### S1 — Run without jar (should fail: TSJ-RUN-006)

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/JITA/scenarios/S1_missing_runtime_classpath/main.ts \
    --out examples/JITA/.build/matrix/S1_missing_runtime_classpath \
    --interop-policy broad --ack-interop-risk"
```

### S3 — Compile with conflicting jars (should fail: TSJ-CLASSPATH-CONFLICT)

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile examples/JITA/scenarios/S3_conflicting_versions/main.ts \
    --out examples/JITA/.build/matrix/S3_conflicting_versions \
    --jar examples/JITA/deps/dupe-lib-1.0.jar \
    --jar examples/JITA/deps/dupe-lib-2.0.jar"
```
