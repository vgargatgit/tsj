# TSJ Interop Torture App (TITA)

This project is a concrete TSJ interop stress app that exercises:
- classpath indexing and duplicate mediation
- Java interop bridge generation from `java:` imports
- overload resolution + selected target metadata persistence
- MR-JAR behavior
- JDK module access via `jrt:/`

## Layout

```text
examples/tita/
  README.md
  tsj.json
  tsj.isolated.json
  deps/
  fixtures-src/
  scripts/build-fixtures.sh
  src/
    main.ts
    scenarios/
      overloads.ts
      generics.ts
      nullability.ts
      sam_and_props.ts
      modules_jrt.ts
      duplicates.ts
  expected/
    shared/class-index.json.expect
    isolated/diagnostics.expect
```

## Build Fixture Jars

```bash
bash examples/tita/scripts/build-fixtures.sh
```

This creates:
- `examples/tita/deps/tita-fixtures-1.0.jar`
- `examples/tita/deps/tita-duplicates-1.0.jar`
- `examples/tita/deps/tita-app-conflict-1.0.jar`

## Shared Mode Run

Current TSJ CLI equivalent:

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/tita/src/main.ts --out examples/tita/.out/shared --classpath examples/tita/deps/tita-fixtures-1.0.jar:examples/tita/deps/tita-duplicates-1.0.jar:jrt:/java.base/java/util --interop-policy broad --ack-interop-risk --classloader-isolation shared"
```

Expected stdout markers:
- `OVERLOAD_OK`
- `GENERICS_OK`
- `NULLABILITY_OK`
- `INHERITANCE_OK`
- `SAM_OK`
- `PROPS_OK`
- `MRJAR_OK`
- `JRT_OK`
- `TITA_OK`

Produced artifacts:
- `examples/tita/.out/shared/class-index.json`
- `examples/tita/.out/shared/program.tsj.properties`

## App-Isolated Negative Test

Current TSJ CLI equivalent:

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/tita/src/main.ts --out examples/tita/.out/isolated --classpath examples/tita/deps/tita-fixtures-1.0.jar:examples/tita/deps/tita-app-conflict-1.0.jar --interop-policy broad --ack-interop-risk --classloader-isolation app-isolated"
```

Expected result:
- non-zero exit
- diagnostic code `TSJ-RUN-009`

## Spec Command Mapping

The original TITA spec uses config-driven commands:

```bash
tsj compile --config tsj.json
tsj run --config tsj.json
tsj compile --config tsj.isolated.json
```

Current TSJ CLI does not yet support `--config` JSON ingestion. The equivalent explicit commands are listed above.
