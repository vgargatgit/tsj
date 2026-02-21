# TSJ-37 Spring Ecosystem Matrix

This document defines the current Spring module compatibility matrix executed by
`compiler/backend-jvm` tests.

Report artifact:
- `compiler/backend-jvm/target/tsj37-spring-module-matrix.json`
- `compiler/backend-jvm/target/tsj37e-spring-module-certification.json`

Fixture root:
- `tests/spring-matrix`

## Module Matrix (TSJ-37 Subset)

| Module | Starter | Status | Fixture | Notes |
|---|---|---|---|---|
| web | `spring-boot-starter-web` | Supported (subset) | `tests/spring-matrix/tsj37-web-supported/main.ts` | Differential parity check compares TS-authored controller adapter behavior with Java reference controller behavior. |
| validation | `spring-boot-starter-validation` | Supported (subset) | `tests/spring-matrix/tsj37-validation-supported/main.ts` + `tests/spring-matrix/tsj37-validation-unsupported/main.ts` | Runtime parity subset for `@Validated` + `@NotBlank`/`@Size`/`@Min`/`@Max`/`@NotNull` with deterministic field/message mapping. Unsupported constraint decorators (for example `@Email`) are explicitly gated with stable diagnostics (`TSJ-DECORATOR-PARAM`). |
| data-jdbc | `spring-boot-starter-data-jdbc` | Supported (baseline subset) | `tests/spring-matrix/tsj37-data-jdbc-supported/main.ts` + `tests/spring-matrix/tsj37-data-jdbc-unsupported/main.ts` | Baseline parity for repository query-method naming (`countBy*`/`findBy*`/`existsBy*`) and transactional service wiring checks. Runtime diagnostics separate wiring/transaction/query failures (`TSJ-SPRING-DATA-WIRING`, `TSJ-SPRING-DATA-TRANSACTION`, `TSJ-SPRING-DATA-QUERY`). Out-of-scope `@Query` decorator paths remain explicitly gated with `TSJ-DECORATOR-UNSUPPORTED`. |
| actuator | `spring-boot-starter-actuator` | Supported (baseline subset) | `tests/spring-matrix/tsj37-actuator-supported/main.ts` | Baseline parity for health/info/metrics read operations (`200`/`503` status behavior in matrix harness). Unsupported `@WriteOperation` remains explicitly diagnosed via `tests/spring-matrix/tsj37-actuator-unsupported/main.ts` with `TSJ-DECORATOR-UNSUPPORTED`. |
| security | `spring-boot-starter-security` | Supported (baseline subset) | `tests/spring-matrix/tsj37-security-supported/main.ts` + `tests/spring-matrix/tsj37-security-unsupported/main.ts` | Baseline parity for filter-chain + method-security subset (`401`/`403`/`200`) with `@PreAuthorize` `hasRole`/`hasAnyRole`; unsupported expressions (for example `hasAuthority`) remain explicitly gated with stable diagnostics (`TSJ-DECORATOR-UNSUPPORTED`, `featureId=TSJ37D-SECURITY`). |

## Execution

Executed via Maven test run:

```bash
mvn -B -ntp -pl compiler/backend-jvm -am -Dtest=TsjSpringIntegrationMatrixTest test
```

The harness also runs during regular `mvn test` in CI as part of the backend-jvm test suite.

TSJ-37e adds a closure-gate certification harness (`TsjSpringModuleCertificationHarness`)
that consumes the matrix output and publishes module scenario parity results
(`tsjPassed`, `javaReferencePassed`, `kotlinReferencePassed`, `parityPassed`)
with fixture version metadata for all certified modules.
