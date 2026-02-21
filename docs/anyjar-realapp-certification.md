# TSJ-44c Real-App Certification

## Purpose
TSJ-44c adds real-application certification on top of library/range gates.

## Artifact

`cli/target/tsj44c-real-app-certification.json`

## Workload Gate

The gate validates two real-app style workloads:
1. `orders-batch`
2. `analytics-pipeline`

and enforces:
1. reliability budget: all workloads pass
2. performance budget: average and max duration under configured thresholds

## Failure Artifacts

For each workload the report includes:
1. `traceFile`
2. `bottleneckHint`
3. duration and stdout/stderr notes

Trace logs are produced under the harness work directory for deterministic reproduction.

## Local Run

```bash
mvn -B -ntp -pl cli -am -Dtest=TsjRealAppCertificationTest -Dsurefire.failIfNoSpecifiedTests=false test
```
