# UTTA — Ultimate TypeScript Torture App

UTTA tracks high-risk TypeScript grammar, runtime semantics, and Java interop scenarios that were historically weak spots.

## Current Status

Measured on **March 2, 2026** using `examples/UTTA/scripts/run.sh`:

```text
TOTAL: 30 | PASS: 29 | FAIL: 1 | CRASH: 0
```

Category breakdown from that run:

| Category | Total | Pass | Fail |
|---|---:|---:|---:|
| Grammar | 15 | 15 | 0 |
| Interop | 10 | 9 | 1 |
| Stress | 5 | 5 | 0 |

## Remaining Red Test

Current failing entry:

| Test | Status | Notes |
|---|---|---|
| `interop/005_completable_future` | `EMPTY` | Harness saw no `name:true`/`name:false` check lines, so it is counted as failure. |

## Run

From repo root:

```bash
bash examples/UTTA/scripts/build-fixtures.sh
bash examples/UTTA/scripts/run.sh
```

`run.sh` exits non-zero when any case is `FAIL`, `CRASH`, or `EMPTY`.

## Scope

```text
examples/UTTA/
  src/grammar/   # 15 grammar/runtime language suites
  src/interop/   # 10 Java interop suites
  src/stress/    # 5 stress suites
  fixtures-src/  # Java fixture sources
  deps/          # built fixture jar(s)
  scripts/
    build-fixtures.sh
    run.sh
```
