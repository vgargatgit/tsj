# XTTA — eXtreme TypeScript Torture App

XTTA is the highest-pressure end-to-end suite for TS grammar, JS built-ins, and jar interop.

## Current Status

Measured on **March 2, 2026** using `examples/XTTA/scripts/run.sh`:

```text
TOTAL: 30 | PASS: 30 | FAIL: 0 | CRASH: 0
```

Category breakdown from that run:

| Category | Total | Pass | Fail |
|---|---:|---:|---:|
| Grammar | 15 | 15 | 0 |
| Built-ins | 10 | 10 | 0 |
| Interop | 5 | 5 | 0 |

## Run

From repo root:

```bash
bash examples/XTTA/scripts/build-fixtures.sh
bash examples/XTTA/scripts/run.sh
```

`run.sh` exits non-zero if any case is `FAIL`, `CRASH`, or `EMPTY`.

## Scope

```text
examples/XTTA/
  src/grammar/    # TS syntax + runtime semantics suites
  src/builtins/   # JS built-in globals/method suites
  src/interop/    # Java interop suites
  fixtures-src/   # Java fixture sources
  deps/           # built fixture jar(s)
  scripts/
    build-fixtures.sh
    run.sh
```
