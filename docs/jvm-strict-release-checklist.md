# JVM-Strict Release Checklist

This checklist is the release signoff contract for `--mode jvm-strict`.

## Required Inputs

1. `tests/conformance/tsj83-strict-readiness.json` reports `"gatePassed": true`.
2. `docs/jvm-strict-mode-guide.md` is current and includes command + migration guidance.
3. `docs/cli-contract.md` includes strict-mode command forms and strict diagnostics.
4. `tests/conformance/tsj84-strict-release-readiness.json` is regenerated and reports `"gatePassed": true`.

## Command Matrix

```bash
tsj compile app/main.ts --out build --mode jvm-strict
tsj run app/main.ts --out build --mode jvm-strict
tsj spring-package app/main.ts --out build --mode jvm-strict
```

## Known Exclusions

- Dynamic `import(...)` is rejected in strict mode (`TSJ-STRICT-DYNAMIC-IMPORT`).
- Runtime code evaluation (`eval`, `Function`) is rejected (`TSJ-STRICT-EVAL`, `TSJ-STRICT-FUNCTION-CONSTRUCTOR`).
- Dynamic computed property writes/prototype mutation remain out of strict subset (`TSJ-STRICT-DYNAMIC-PROPERTY-ADD`, `TSJ-STRICT-PROTOTYPE-MUTATION`).

## Release Decision

Release is approved only when all required inputs are green and known exclusions are explicitly accepted.
