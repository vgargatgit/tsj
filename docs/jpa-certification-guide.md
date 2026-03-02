# JPA Certification Guide (TSJ-42a/b/c/d)

This guide consolidates all TSJ-42 JPA parity/certification gates.

## Gate Stack

1. `TSJ-42a`: Real DB parity
2. `TSJ-42b`: Lazy/proxy parity
3. `TSJ-42c`: Lifecycle/transaction parity
4. `TSJ-42d`: Closure gate across all families

## TSJ-42a Real DB Parity

Purpose:

1. Reproducible DB-backed parity for TS-authored JPA-style flows.

Backends:

1. `h2` (`sample.orm.JpaDbPack$InMemoryBackend`)
2. `hsqldb` (`sample.orm.JpaDbPack$FileBackend`)

Diagnostics:

1. `TSJ-ORM-DB-WIRING`
2. `TSJ-ORM-QUERY-FAILURE`

Artifact:

1. `cli/target/tsj42a-jpa-realdb-parity.json`

## TSJ-42b Lazy/Proxy Parity

Purpose:

1. Validate supported lazy initialization boundaries.
2. Emit explicit diagnostics for unsupported lazy/proxy patterns.

Diagnostics:

1. `TSJ-JPA-LAZY-UNSUPPORTED`
2. `TSJ-JPA-PROXY-UNSUPPORTED`

Artifact:

1. `cli/target/tsj42b-jpa-lazy-proxy-parity.json`

## TSJ-42c Lifecycle/Transaction Parity

Purpose:

1. Validate `flush`/`clear`/`detach`/`merge` lifecycle semantics.
2. Validate transaction boundary behavior.

Diagnostics:

1. `TSJ-ORM-LIFECYCLE-MISUSE`
2. `TSJ-ORM-TRANSACTION-REQUIRED`
3. `TSJ-ORM-MAPPING-FAILURE`

Artifact:

1. `cli/target/tsj42c-jpa-lifecycle-parity.json`

## TSJ-42d Closure

Purpose:

1. Final compatibility closure gate for ORM claims.

Rule:

1. Passes only when TSJ-42a, TSJ-42b, and TSJ-42c family rows are all green.

Artifact:

1. `cli/target/tsj42d-jpa-certification.json`

## Local Runs

Run all TSJ-42 suites:

```bash
mvn -B -ntp -pl cli -am \
  -Dtest=TsjJpaRealDatabaseParityTest,TsjJpaLazyProxyParityTest,TsjJpaLifecycleParityTest,TsjJpaCertificationClosureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Certified vs Best-Effort

Certified:

1. Scenarios represented by TSJ-42a/b/c fixtures and TSJ-42d closure report.

Best-effort:

1. Provider-specific internals not covered by TSJ-42 fixtures.
2. Library/version combinations outside tested matrices.
