# TITA Runbook

This runbook validates `examples/tita` end-to-end with deterministic artifact and diagnostic checks.

## Preconditions

1. Run from repository root.
2. Use JDK 21+ and Maven.
3. Build fixture jars:

```bash
bash examples/tita/scripts/build-fixtures.sh
```

Generated jars:
- `examples/tita/deps/tita-fixtures-1.0.jar`
- `examples/tita/deps/tita-duplicates-1.0.jar`
- `examples/tita/deps/tita-app-conflict-1.0.jar`

Note: commands below use `:` classpath separators (Unix/macOS). On Windows use `;`.

## 1) Shared Mode Must Succeed

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/tita/src/main.ts --out examples/tita/.out/shared --classpath examples/tita/deps/tita-fixtures-1.0.jar:examples/tita/deps/tita-duplicates-1.0.jar:jrt:/java.base/java/util --interop-policy broad --ack-interop-risk --classloader-isolation shared"
```

Expected:
1. Exit code `0`.
2. Stdout contains:
   - `OVERLOAD_OK`
   - `GENERICS_OK`
   - `NULLABILITY_OK`
   - `INHERITANCE_OK`
   - `SAM_OK`
   - `PROPS_OK`
   - `MRJAR_OK`
   - `JRT_OK`
   - `TITA_OK`
3. `examples/tita/.out/shared/program.tsj.properties` exists.
4. `interopClasspath.classIndex.path` points to an existing `class-index.json`.
5. `interopBridges.selectedTargetCount` is greater than `0`.

Recommended artifact checks:

```bash
ART=examples/tita/.out/shared/program.tsj.properties
CLASS_INDEX=$(sed -n 's/^interopClasspath.classIndex.path=//p' "$ART")
test -f "$CLASS_INDEX"
rg '^interopClasspath.classIndex.symbolCount=' "$ART"
rg '^interopClasspath.classIndex.duplicateCount=' "$ART"
rg '^interopBridges.selectedTargetCount=' "$ART"
rg '"internalName":"dev/tita/fixtures/Conflict"' "$CLASS_INDEX"
rg '"rule":"mediated-order"' "$CLASS_INDEX"
rg '"internalName":"java/util/Optional"' "$CLASS_INDEX"
```

## 2) App-Isolated Mode Must Fail with Conflict Code

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/tita/src/main.ts --out examples/tita/.out/isolated --classpath examples/tita/deps/tita-fixtures-1.0.jar:examples/tita/deps/tita-app-conflict-1.0.jar --interop-policy broad --ack-interop-risk --classloader-isolation app-isolated"
```

Expected:
1. Non-zero exit code.
2. Diagnostic includes:
   - code `TSJ-RUN-009`
   - `app-isolated`
   - `dev.tsj.generated.MainProgram`

## 3) Missing Fixture Jar Must Fail with Missing-Class Code

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/tita/src/main.ts --out examples/tita/.out/missing-jar --classpath jrt:/java.base/java/util --interop-policy broad --ack-interop-risk"
```

Expected:
1. Non-zero exit code.
2. Diagnostic includes:
   - code `TSJ-RUN-006`
   - missing fixture class marker such as `dev.tita.fixtures.Overloads`

## 4) Determinism Repro Check

Run shared mode twice to two fresh output roots:

```bash
rm -rf examples/tita/.out/shared-a examples/tita/.out/shared-b
```

Use the shared-mode command twice, changing only `--out` to:
1. `examples/tita/.out/shared-a`
2. `examples/tita/.out/shared-b`

Then compare stable artifacts:

```bash
A_ART=examples/tita/.out/shared-a/program.tsj.properties
B_ART=examples/tita/.out/shared-b/program.tsj.properties
A_INDEX=$(sed -n 's/^interopClasspath.classIndex.path=//p' "$A_ART")
B_INDEX=$(sed -n 's/^interopClasspath.classIndex.path=//p' "$B_ART")

cmp -s "$A_INDEX" "$B_INDEX"
diff -u \
  <(rg '^interopBridges.selectedTarget' "$A_ART" | sort) \
  <(rg '^interopBridges.selectedTarget' "$B_ART" | sort)
```

Success criteria:
1. `cmp` exits `0` for class-index JSON.
2. selected-target metadata diff is empty.
