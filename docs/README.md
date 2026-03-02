# TSJ Docs Guide

This guide is the starting point for engineers who are not compiler experts.

## Read This First

1. `README.md` (repo root): quickest way to compile/run TS with TSJ.
2. `docs/cli-contract.md`: exact CLI command and diagnostic contract.
3. `docs/unsupported-feature-matrix.md`: supported vs unsupported TS syntax/features.
4. `unsupported/README.md`: progression suite for known unsupported grammar/semantic gaps.

## Common Tasks

### Run TypeScript with TSJ

Use root quickstart in `README.md`.

Note:

1. `tsj run` currently does not forward program argv to TS entrypoints.

### Understand why code fails

1. Look at diagnostic code in CLI JSON output.
2. Match the code family in `docs/cli-contract.md`.
3. Check if the feature is in `docs/unsupported-feature-matrix.md`.

### Work on language support

1. Add/adjust a case in `unsupported/grammar`.
2. Run `unsupported/run_progress.sh`.
3. Reduce failing count over time.

## Docs Map (Simplified)

### Core docs

- `docs/cli-contract.md`: command syntax, options, diagnostics.
- `docs/developer-guide.md`: contributor workflow and module-level test loops.
- `docs/unsupported-feature-matrix.md`: support status matrix.
- `docs/todo.md`: active backlog and recent review outcomes.
- `docs/plans.md`: execution plans and checklists.

### Interop and compatibility

- `docs/interop-bridge-spec.md`: interop spec format.
- `docs/interop-policy.md`: strict/broad policy and controls.
- `docs/interop-compatibility-guide.md`: metadata, reflection, generic adaptation, and certification gates.
- `docs/anyjar-certification.md`: TSJ-44 any-jar certification stack.
- `docs/tita-runbook.md`: TITA reproducible checks.

### Spring/JPA/Kotlin parity

- `docs/spring-ecosystem-matrix.md`: Spring subset status.
- `docs/jpa-certification-guide.md`: TSJ-42a/b/c/d JPA parity and closure.
- `docs/tsj38-parity-guide.md`: TSJ-38 readiness, DB/security parity, and certification.

### Architecture/reference (advanced)

- `docs/architecture-decisions.md`
- `docs/frontend-contract.md`
- `docs/ir-contract.md`
- `docs/source-map-format.md`
- `docs/compiler-analysis.md`

## What Was Consolidated

This docs pass merged multiple small status docs into fewer guides:

1. Interop compatibility/certification docs -> `docs/interop-compatibility-guide.md`
2. TSJ-42 JPA parity docs -> `docs/jpa-certification-guide.md`
3. TSJ-38 parity docs -> `docs/tsj38-parity-guide.md`
4. TSJ-44 real-app/governance fragments -> merged into `docs/anyjar-certification.md`
