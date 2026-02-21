# TSJ-36c Dev-Loop Workflow

## Goal
Provide a reproducible CLI workflow for TS-authored Spring app iteration:
1. Compile.
2. Run.
3. Package.
4. Smoke-check packaged runtime.

## Recommended Loop

```bash
tsj compile src/main.ts --out .tsj/out
tsj run src/main.ts --out .tsj/run
tsj spring-package src/main.ts --out .tsj/pkg --resource-dir src/main/resources --smoke-run
```

For fast iteration after edits, rerun the same three commands against the updated source.

## Diagnostics You Should See

1. `TSJ-COMPILE-SUCCESS`
2. `TSJ-RUN-SUCCESS`
3. `TSJ-SPRING-PACKAGE-SUCCESS`
4. `TSJ-SPRING-SMOKE-SUCCESS`

## CI Gate

TSJ-36c parity gate report:

`cli/target/tsj36c-dev-loop-parity.json`

This gate validates:
1. compile/run/package/smoke command health
2. iterative edit-and-rerun behavior
3. published workflow hints and explicit non-goals

## Non-Goals (Current Subset)

1. Continuous hot-reload process management.
2. IDE plugin integrations beyond CLI workflow.
3. Automatic dependency re-resolution without explicit command invocation.
