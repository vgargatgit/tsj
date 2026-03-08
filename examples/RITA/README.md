# RITA — Reflection Injection Test App

RITA tests a Spring-like pattern using a custom jar:
- Java annotations (`@Component`, `@Inject`)
- reflection-based field injection container
- TSJ interop call path

It also checks whether TS-authored decorators survive plain TSJ compile/run as JVM annotations.

## What This Example Proves

1. Java annotation-driven DI works when both annotations and component classes are Java classes from a jar.
2. In plain TSJ compile/run, TS decorators are runtime decorator semantics, not JVM bytecode annotations visible to Java reflection.

## Layout

```text
examples/RITA/
  README.md
  RITA_SPEC.md
  deps/
  fixtures-src/rita/dev/rita/di/
  scripts/
    build-fixtures.sh
    run.sh
  src/
    main.ts
```

## Build Fixture Jar

```bash
bash examples/RITA/scripts/build-fixtures.sh
```

Creates:
- `examples/RITA/deps/rita-di-1.0.jar`

## Run

```bash
bash examples/RITA/scripts/run.sh
```

Expected summary:
- all checks pass
- no `:false` lines

Expected check names:
- `java_di_message`
- `java_component_annotation_visible`
- `java_inject_annotation_visible`
- `ts_component_annotation_not_visible_to_java_reflection`
- `ts_inject_annotation_not_visible_to_java_reflection`
- `ts_runtime_class_is_map_backing_object`
