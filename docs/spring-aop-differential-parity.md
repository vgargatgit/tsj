# TSJ-35c Transactional/AOP Differential Parity Gate

TSJ-35c closes TSJ-35 with a differential parity gate that compares TS-authored
transactional behavior against Java and Kotlin-reference services for the certified subset.

## Harness

- Test entry point:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityTest.java`
- Harness implementation:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityHarness.java`
- Report model:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityReport.java`

## Compared Scenarios

- `commit-chain`
- `rollback-chain`
- `missing-transaction-manager`
- `application-invocation-failure`

TS scenario baselines are sourced from TSJ-35b runtime conformance output and then
compared against Java and Kotlin-reference services.

## Report Artifact

- Module artifact:
  `compiler/backend-jvm/target/tsj35c-aop-differential-parity-report.json`
- Report schema fields per scenario:
  `scenario`, `fixture`, `passed`, `diagnosticCode`,
  `tsValue`, `javaValue`, `kotlinValue`, `notes`

## CI/Gate Behavior

- The JUnit gate fails if any certified transactional/AOP scenario diverges.
- A deterministic JSON report is emitted for triage and artifact upload.
