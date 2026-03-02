# JITA — Jar Interop Torture App

JITA validates deterministic diagnostics for jar/classpath edge cases.

## Current Status

Measured on **March 2, 2026** using `examples/JITA/scripts/run_matrix.sh`:

```text
summary: total=5 passed=5 failed=0
```

## What It Covers

| Scenario | Expected Outcome |
|---|---|
| `S1_missing_runtime_classpath` | runtime blocked with `TSJ-RUN-006` |
| `S2_non_public_member` | interop validation blocked with `TSJ-INTEROP-INVALID` |
| `S3_conflicting_versions` | compile blocked with `TSJ-CLASSPATH-CONFLICT` |
| `S4_provided_scope_runtime` | runtime classpath scope blocked with `TSJ-CLASSPATH-SCOPE` |
| `S5_app_isolated_duplication` | app-isolated loader conflict blocked with `TSJ-RUN-009` |

## Run

From repo root:

```bash
bash examples/JITA/scripts/build-fixtures.sh
bash examples/JITA/scripts/run_matrix.sh
```

`run_matrix.sh` compares each scenario against its `expect.json` and exits non-zero on any mismatch.

## Layout

```text
examples/JITA/
  fixtures-src/          # Java sources for fixture jars
  deps/                  # built jars
  scenarios/             # TS scenario + expect.json pairs
  scripts/
    build-fixtures.sh
    run_matrix.sh
```
