# RITA Specification

RITA (Reflection Injection Test App) validates TSJ jar interop behavior for annotation-driven,
reflection-based dependency injection.

## Goals

1. Build a Java fixture jar that defines runtime-retained annotations (`@Component`, `@Inject`).
2. Implement a simple reflection DI container in that jar.
3. Consume the jar from TSJ TypeScript code and prove:
   - Java annotation reflection + DI works for Java component classes in the jar.
   - TS-authored decorators in plain TSJ compile/run do **not** become JVM annotations visible to Java reflection.

## Done Criteria

1. `bash examples/RITA/scripts/build-fixtures.sh` produces `examples/RITA/deps/rita-di-1.0.jar`.
2. `bash examples/RITA/scripts/run.sh` succeeds with deterministic checks.
3. Output includes positive checks for Java DI and explicit checks for TS annotation non-visibility to Java reflection.
