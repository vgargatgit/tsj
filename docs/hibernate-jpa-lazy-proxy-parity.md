# TSJ-42b JPA Lazy/Proxy Parity

## Purpose
TSJ-42b extends ORM compatibility with deterministic proxy/lazy-loading parity checks.

The gate validates:
1. Supported lazy initialization and repeated-read boundary behavior.
2. Explicit diagnostics for unsupported lazy/proxy patterns.
3. A machine-readable report artifact for CI and release tracking.

## Certified Subset

| Scenario | Expected Behavior |
|---|---|
| Lazy reference initialization | `isInitialized` transitions `false -> true` after first materialization |
| Lazy read boundary | Repeated lazy reads return stable value after initialization |
| Detached lazy association (unsupported) | Fails with `TSJ-JPA-LAZY-UNSUPPORTED` and association context |
| Final-class proxy target (unsupported) | Fails with `TSJ-JPA-PROXY-UNSUPPORTED` and association context |

## Diagnostics

| Scenario | Expected Diagnostic |
|---|---|
| Unsupported detached lazy access | `TSJ-JPA-LAZY-UNSUPPORTED` |
| Unsupported proxy target | `TSJ-JPA-PROXY-UNSUPPORTED` |

## Report Artifact

`cli/target/tsj42b-jpa-lazy-proxy-parity.json`

The report includes:
1. Supported-scenario TS/Java/Kotlin output parity records.
2. Unsupported-pattern diagnostic expected-vs-observed codes.
3. Gate pass/fail summary.

## Local Run

```bash
mvn -B -ntp -pl cli -am -Dtest=TsjJpaLazyProxyParityTest -Dsurefire.failIfNoSpecifiedTests=false test
```
