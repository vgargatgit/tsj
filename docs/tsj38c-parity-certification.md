# TSJ-38c Kotlin Parity Certification

## Purpose
TSJ-38c closes Kotlin-parity readiness with explicit certification dimensions and thresholds.

## Certification Artifact

`compiler/backend-jvm/target/tsj38c-kotlin-parity-certification.json`

The artifact includes:
1. `gatePassed`
2. `fullParityReady`
3. `dbParityPassed`
4. `securityParityPassed`
5. `fixtureVersion`
6. Dimension rows with threshold + observed values

## Gate Dimensions

| Dimension | Threshold |
|---|---|
| `correctness` | `db=true && security=true && subsetReady=true` |
| `startup-time-ms` | `<= 5000ms` |
| `throughput-ops-per-sec` | `>= 1000.0` |
| `diagnostics-quality` | `>= 5` passed diagnostic scenarios with stable codes |

## Local Run

```bash
mvn -B -ntp -pl compiler/backend-jvm -am -Dtest=TsjKotlinParityCertificationTest -Dsurefire.failIfNoSpecifiedTests=false test
```
