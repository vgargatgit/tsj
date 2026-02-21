# TSJ-38 Readiness Gate (Subset)

The TSJ-38 readiness gate is produced by backend-jvm tests and persisted as:

- `compiler/backend-jvm/target/tsj38-kotlin-parity-readiness.json`

## Current Gate Semantics

1. `subsetReady`: all subset criteria pass.
2. `fullParityReady`: subset criteria pass and no framework blockers remain.
3. `blockers`: unresolved parity blockers preventing full TSJ-38 readiness.
4. Latest subset report currently records no runtime parity blockers.

## Evaluated Criteria

1. `reference-app-scaffold`: TS and Kotlin reference app scaffolds exist.
2. `web-module-parity-signal`: TS web subset parity signal passes.
3. `unsupported-module-gates`: unsupported module features are rejected with stable diagnostics.
4. `db-backed-reference-parity-signal`: TSJ-38a DB-backed parity gate passes.
5. `security-reference-parity-signal`: TSJ-38b security parity gate passes.
6. `performance-baseline-signal`: benchmark baseline artifact is present.
7. `migration-guide-available`: migration documentation exists.

## Inputs

1. TSJ-37 spring matrix report (`tsj37-spring-module-matrix.json`).
2. TSJ-38a DB parity report (`tsj38a-db-parity-report.json`).
3. TSJ-38b security parity report (`tsj38b-security-parity-report.json`).
4. Benchmark baseline (`benchmarks/tsj-benchmark-baseline.json`).
5. Reference app scaffolds (`examples/tsj38-kotlin-parity/*`).
6. Migration guide (`docs/tsj-kotlin-migration-guide.md`).
