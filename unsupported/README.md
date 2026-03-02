# Unsupported Progression Suite

This folder tracks TypeScript grammar/operator behaviors that are not yet
parity-correct in TSJ.

## Goal

Use the **failing case count** as a progression indicator. As support improves,
the number of failing fixtures should go down.

## Run

```bash
unsupported/run_progress.sh
```

The script:

1. Runs each fixture under Node (`node --experimental-strip-types`) to get the
   baseline output.
2. Runs the same fixture with TSJ (`tsj run` through Maven exec).
3. Compares status/output and reports `PASS` or `FAIL`.
4. Prints a summary with totals.

Current fixture roots:

- `unsupported/grammar`
- `unsupported/jarinterop`

Grammar case file convention:

- runnable progression cases: `NNN_name.ts` (for example `013_dynamic_import.ts`)
- helper modules shared by cases: `_name.ts` (excluded from case counting)

Jar interop case file convention:

- runnable progression cases: `NNN_name.ts`
- see `unsupported/jarinterop/README.md` for fixture-jar build details
