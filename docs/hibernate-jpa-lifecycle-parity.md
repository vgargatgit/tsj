# TSJ-42c JPA Lifecycle/Transaction Parity

## Purpose
TSJ-42c certifies persistence-context lifecycle and transaction-coupled semantics for the supported ORM subset.

The gate validates:
1. Lifecycle flow semantics for `flush`/`clear`/`detach`/`merge`.
2. Transaction boundary behavior (`commit`/`rollback`) on persisted state.
3. Distinct diagnostics for lifecycle misuse, transaction misuse, and mapping failure.

## Certified Subset

| Scenario | Expected Behavior |
|---|---|
| `flush-clear-detach-merge` | Lifecycle transition outputs match Java/Kotlin references |
| `transaction-boundary-rollback` | Rolled-back writes are not committed |
| `lifecycle-misuse` | Fails with `TSJ-ORM-LIFECYCLE-MISUSE` |
| `transaction-required` | Fails with `TSJ-ORM-TRANSACTION-REQUIRED` |
| `mapping-failure` | Fails with `TSJ-ORM-MAPPING-FAILURE` |

## Diagnostics

| Scenario | Expected Diagnostic |
|---|---|
| Persistence-context misuse | `TSJ-ORM-LIFECYCLE-MISUSE` |
| Missing transaction boundary | `TSJ-ORM-TRANSACTION-REQUIRED` |
| Mapping/query model failure | `TSJ-ORM-MAPPING-FAILURE` |

## Report Artifact

`cli/target/tsj42c-jpa-lifecycle-parity.json`

The report includes:
1. TS/Java/Kotlin parity rows for supported lifecycle/transaction scenarios.
2. Expected-vs-observed diagnostics for failure families.
3. Gate pass/fail summary.

## Local Run

```bash
mvn -B -ntp -pl cli -am -Dtest=TsjJpaLifecycleParityTest -Dsurefire.failIfNoSpecifiedTests=false test
```
