# TSJ-42a JPA Real-DB Parity

## Purpose
TSJ-42a adds a reproducible DB-backed parity gate for TS-authored JPA-style flows.

The gate validates:
1. CRUD + representative query behavior across two backend fixtures.
2. Stable diagnostics that separate DB wiring failures from ORM query failures.
3. A machine-readable report with backend + ORM metadata.

## Certified Backends

| Backend | Driver Id | DB Version Metadata | ORM Version Metadata |
|---|---|---|---|
| `h2` | `sample.orm.JpaDbPack$InMemoryBackend` | `embedded-mapdb-1.0` | `jpa-lite-1.0` |
| `hsqldb` | `sample.orm.JpaDbPack$FileBackend` | `embedded-filedb-1.0` | `jpa-lite-1.0` |

## Diagnostics

| Scenario | Expected Diagnostic |
|---|---|
| DB wiring/configuration failure | `TSJ-ORM-DB-WIRING` |
| ORM mapping/query failure | `TSJ-ORM-QUERY-FAILURE` |

## Report Artifact

`cli/target/tsj42a-jpa-realdb-parity.json`

The report includes:
1. Per-backend parity pass/fail result.
2. DB + ORM version metadata.
3. Diagnostic scenario results with expected/observed codes.

## Local Run

```bash
mvn -B -ntp -pl cli -am -Dtest=TsjJpaRealDatabaseParityTest -Dsurefire.failIfNoSpecifiedTests=false test
```
