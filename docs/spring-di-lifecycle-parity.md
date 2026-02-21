# TSJ-33f DI/Lifecycle Differential Parity Gate

TSJ-33f closes TSJ-33 with a differential parity harness that compares TS-authored
Spring component behavior against Java and Kotlin-reference paths for the certified subset.

## Harness

- Test entry point:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityTest.java`
- Harness implementation:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityHarness.java`
- Report model:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityReport.java`

## Versioned Fixtures

- `tests/spring-matrix/tsj33f-mixed-injection/main.ts`
- `tests/spring-matrix/tsj33f-lifecycle-order/main.ts`
- `tests/spring-matrix/tsj33f-cycle-diagnostic/main.ts`

## Report Artifact

- Module artifact:
  `compiler/backend-jvm/target/tsj33f-di-lifecycle-parity-report.json`
- Report schema fields per scenario:
  `scenario`, `fixture`, `passed`, `diagnosticCode`,
  `tsValue`, `javaValue`, `kotlinValue`, `notes`

## CI/Gate Behavior

- The JUnit gate fails if any certified scenario parity regresses.
- A deterministic JSON report is emitted for triage and artifact upload.
