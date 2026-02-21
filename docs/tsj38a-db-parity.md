# TSJ-38a DB-Backed Reference Parity

## Purpose
TSJ-38a certifies DB-backed parity for the TS/Kotlin reference workflow path.

The gate validates:
1. TS-authored workflow output matches Java and Kotlin reference outputs.
2. CRUD and representative query behavior are stable across configured backends.
3. Transaction boundaries (`begin`/`commit`/`rollback`) preserve deterministic outcomes.
4. DB wiring failures and ORM query failures emit distinct diagnostics.

## Report Artifact

`compiler/backend-jvm/target/tsj38a-db-parity-report.json`

The report includes:
1. `backends`: parity results by backend with TS/Java/Kotlin outputs.
2. `diagnosticScenarios`: expected vs observed diagnostic codes for failure paths.
3. `gatePassed`: overall TSJ-38a pass/fail signal.

## Diagnostics

| Scenario | Expected Diagnostic |
|---|---|
| DB wiring/configuration failure | `TSJ-ORM-DB-WIRING` |
| ORM query failure | `TSJ-ORM-QUERY-FAILURE` |

## Local Run

```bash
mvn -B -ntp -pl compiler/backend-jvm -am -Dtest=TsjKotlinDbParityTest -Dsurefire.failIfNoSpecifiedTests=false test
```
