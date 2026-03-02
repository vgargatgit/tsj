# TSJ-38 Parity Guide (Readiness + Certification)

This guide consolidates TSJ-38 readiness and certification docs.

## Gate Sequence

1. `TSJ-38a`: DB-backed reference parity
2. `TSJ-38b`: Security reference parity
3. `TSJ-38` readiness gate: aggregate subset/full-parity signals
4. `TSJ-38c`: final certification dimensions

## TSJ-38a DB-Backed Parity

Validates:

1. TS workflow parity vs Java/Kotlin references for DB-backed flows.
2. Stable CRUD/query behavior across configured backends.
3. Distinct diagnostics for DB wiring vs query failures.

Artifact:

1. `compiler/backend-jvm/target/tsj38a-db-parity-report.json`

Diagnostics:

1. `TSJ-ORM-DB-WIRING`
2. `TSJ-ORM-QUERY-FAILURE`

## TSJ-38b Security Parity

Validates:

1. Authenticated/authorized success paths.
2. Unauthenticated/unauthorized failure semantics.
3. Distinct security configuration diagnostics.

Artifact:

1. `compiler/backend-jvm/target/tsj38b-security-parity-report.json`

Diagnostics:

1. `TSJ-SECURITY-AUTHN-FAILURE`
2. `TSJ-SECURITY-AUTHZ-FAILURE`
3. `TSJ-SECURITY-CONFIG-FAILURE`

## TSJ-38 Readiness Gate

Artifact:

1. `compiler/backend-jvm/target/tsj38-kotlin-parity-readiness.json`

Key fields:

1. `subsetReady`
2. `fullParityReady`
3. `blockers`

Readiness inputs include TSJ-38a/38b reports, benchmark baseline artifact, reference app scaffolds, and migration guide.

## TSJ-38c Certification

Artifact:

1. `compiler/backend-jvm/target/tsj38c-kotlin-parity-certification.json`

Dimensions:

1. `correctness` (`db=true && security=true && subsetReady=true`)
2. `startup-time-ms <= 5000`
3. `throughput-ops-per-sec >= 1000.0`
4. `diagnostics-quality >= 5`

## Local Runs

```bash
mvn -B -ntp -pl compiler/backend-jvm -am \
  -Dtest=TsjKotlinDbParityTest,TsjKotlinSecurityParityTest,TsjKotlinParityReadinessGateTest,TsjKotlinParityCertificationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
