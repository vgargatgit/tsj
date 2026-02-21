# TSJ-44d Any-JAR Governance Signoff

## Purpose
TSJ-44d provides release-governance closure for any-jar compatibility claims.

## Artifact

`cli/target/tsj44d-anyjar-governance.json`

## Signoff Criteria

Release signoff is approved only when all criteria pass:
1. `matrix-gate`
2. `version-range-gate`
3. `real-app-gate`

## Compatibility Manifest

The governance report publishes manifest rows with:
1. `library`
2. `version`
3. `supportTier`
4. `sourceGate`

Current support tiers:
1. `certified-subset`
2. `certified-range`
3. `certified-real-app`

## Regression Policy

Governance report includes explicit policy fields:
1. `rollbackMode`
2. `downgradeMode`
3. operator notes for certified-scenario regressions

## Local Run

```bash
mvn -B -ntp -pl cli -am -Dtest=TsjAnyJarGovernanceCertificationTest -Dsurefire.failIfNoSpecifiedTests=false test
```
