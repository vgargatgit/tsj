# Unsupported JAR Interop Progression Suite

This suite tracks jar/classpath interop scenarios that are currently rejected
or unsupported by TSJ with stable diagnostics.

## Run

```bash
unsupported/jarinterop/run_progress.sh
```

The runner:

1. Builds minimal Java fixture jars needed by each case.
2. Executes TSJ `compile`/`run` for each case.
3. Asserts stable diagnostic code (and targeted message marker when needed).
4. Reports `PASS`/`FAIL` with a summary.

Case file convention:

- runnable progression cases: `NNN_name.ts`
