# TSJ-42d JPA Certification Closure

## Purpose
TSJ-42d is the closure gate for ORM compatibility claims.

It enforces that the three ORM family suites are all green:
1. Real DB parity (`TSJ-42a`)
2. Lazy/proxy parity (`TSJ-42b`)
3. Lifecycle/transaction parity (`TSJ-42c`)

## Closure Gate

Certification artifact:

`cli/target/tsj42d-jpa-certification.json`

The gate passes only when every family row is `passed=true`.

Each row includes:
1. `family`
2. `scenario`
3. `ormVersion`
4. `backend`
5. `diagnosticCode`
6. `notes`

## Certified vs Best-Effort Boundary

Certified:
1. ORM behavior represented by TSJ-42a/42b/42c family suites and scenarios.
2. Diagnostics exercised by those suites.

Best-effort:
1. ORM internals outside these scenario families.
2. Provider-specific features not covered by closure rows.
3. Behavior from untested ORM/library/version combinations.

## Local Run

```bash
mvn -B -ntp -pl cli -am -Dtest=TsjJpaCertificationClosureTest -Dsurefire.failIfNoSpecifiedTests=false test
```
