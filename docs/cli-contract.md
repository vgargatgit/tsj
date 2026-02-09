# TSJ CLI Contract (Bootstrap v0.1)

## Commands

### `tsj compile <input.ts> --out <dir> [--optimize|--no-optimize]`
Behavior:
1. Validates input file exists and has `.ts`/`.tsx` extension.
2. Compiles supported TSJ-7 subset into JVM classes.
   - TSJ-8 extends supported subset with nested function declarations and lexical closures.
   - TSJ-9 extends supported subset with class/object features:
     class declarations, `extends`/`super(...)`, `new`, `this` member access, and object literals.
   - TSJ-10 extends runtime semantics for supported primitives with:
     `undefined` literal handling and distinct lowering for `==`/`!=` vs `===`/`!==`.
   - TSJ-11 extends generated object-property reads with monomorphic call-site caches and
     runtime object fallback behavior (`missing -> undefined`) aligned to subset semantics.
   - TSJ-12 extends bootstrap compile with relative ESM import bundling (`import { ... } from "./x.ts"` and
     `import "./x.ts"`), dependency-first module initialization ordering, and baseline live-binding behavior
     for supported export/import declaration forms.
   - TSJ-13 extends runtime/codegen with `Promise` builtin support (`resolve`, `reject`), `async function`
     declaration lowering, standalone `await` expression lowering in async bodies, throw-to-rejection
     normalization, and microtask queue flushing at end-of-program execution.
   - TSJ-17 applies baseline optimization passes by default:
     constant folding and dead-code elimination.
     - `--optimize` forces defaults on.
     - `--no-optimize` disables both passes.
3. Creates output directory if missing.
4. Emits class output directory:
   - `<out>/classes`
5. Emits artifact file:
   - `<out>/program.tsj.properties`
   - includes optimization metadata keys:
     `optimization.constantFoldingEnabled` and `optimization.deadCodeEliminationEnabled`.
6. Emits source-map file for generated class stack frame mapping:
   - `<out>/classes/dev/tsj/generated/*Program.tsj.map`
7. Emits structured JSON diagnostics to stdout/stderr.

Success diagnostic:
- Code: `TSJ-COMPILE-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-003` missing required `--out`
- `TSJ-COMPILE-001` input file not found
- `TSJ-COMPILE-002` unsupported input extension
- `TSJ-COMPILE-500` artifact write error
- backend diagnostics like `TSJ-BACKEND-*` for unsupported syntax or JVM compile failures
  - TSJ-15 unsupported-feature failures use `TSJ-BACKEND-UNSUPPORTED` with context:
    `file`, `line`, `column`, `featureId`, `guidance`.

### `tsj run <entry.ts> [--out <dir>] [--ts-stacktrace] [--optimize|--no-optimize]`
Behavior:
1. Compiles entry to artifact (default out dir `.tsj-build` when omitted).
   - Optimization defaults to enabled (`--optimize`) and can be disabled with `--no-optimize`.
2. Reads generated artifact.
3. Executes generated JVM class.
4. When `--ts-stacktrace` is present and runtime execution fails, emits best-effort mapped TS stack frames to stderr.
   - Output is grouped by cause (`Cause[0]`, `Cause[1]`, ...).
   - Frames are filtered to generated-program methods and deduplicated at method level per cause.
5. Emits program stdout, then structured JSON diagnostics.

Success diagnostic:
- Code: `TSJ-RUN-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-004` missing entry path
- `TSJ-RUN-001` artifact read error
- `TSJ-RUN-007` missing class metadata in artifact
- `TSJ-RUN-*` runtime class load/execute failures
- compile-phase failure codes from `tsj compile`

### `tsj interop <interop.properties> --out <dir>`
Behavior:
1. Reads opt-in interop bridge spec (`allowlist` and optional `targets`).
2. Validates each requested target is allowlisted.
3. Validates target classes/methods exist and are static Java methods.
4. Generates bridge source stubs under:
   - `<out>/dev/tsj/generated/interop/*.java`
5. Emits bridge metadata:
   - `<out>/interop-bridges.properties`
6. Emits structured JSON diagnostics.

Success diagnostic:
- `TSJ-INTEROP-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-008` missing interop spec path
- `TSJ-INTEROP-INPUT` spec read/write failures
- `TSJ-INTEROP-INVALID` malformed target or missing class/method
- `TSJ-INTEROP-DISALLOWED` requested target not in allowlist

### `tsj fixtures <fixturesRoot>`
Behavior:
1. Loads all fixture directories under `<fixturesRoot>`.
2. Runs each fixture with Node and TSJ.
3. Compares outputs with fixture expectations.
4. Emits per-fixture diagnostics plus a summary.
5. Emits feature-coverage diagnostics and writes a JSON coverage report file.

Success diagnostics:
- `TSJ-FIXTURE-PASS`
- `TSJ-FIXTURE-SUMMARY`
- `TSJ-FIXTURE-COVERAGE`

Failure diagnostics:
- `TSJ-FIXTURE-FAIL`
- `TSJ-FIXTURE-001` fixture load error
- `TSJ-FIXTURE-002` no fixture directories

Failure diagnostic context for `TSJ-FIXTURE-FAIL` includes:
1. `minimalRepro`: compact mismatch summary with repro commands for Node and TSJ.

### `tsj bench <report.json> [--warmup <n>] [--iterations <n>] [--smoke] [--optimize|--no-optimize]`
Behavior:
1. Runs TSJ benchmark workloads and emits a JSON baseline report.
2. Benchmark suite includes:
   - `micro` workloads (startup + tight-loop style cases)
   - `macro` workloads (larger closure/class/module+async cases)
3. Supports warmup/measurement controls:
   - `--warmup <n>` warmup iteration count (`n >= 0`)
   - `--iterations <n>` measured iteration count (`n >= 1`)
4. Supports benchmark profile:
   - default `full` suite
   - `--smoke` quick suite with one micro + one macro workload
5. Supports compiler optimization toggles for benchmark runs:
   - `--optimize` (default)
   - `--no-optimize`

Success diagnostics:
- `TSJ-BENCH-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-009` missing benchmark report path
- `TSJ-CLI-010` invalid benchmark options
- `TSJ-BENCH-001` benchmark harness execution failure

## Diagnostic Shape
All diagnostics use one-line JSON objects:

```json
{
  "level": "INFO|ERROR",
  "code": "TSJ-*",
  "message": "human-readable message",
  "context": {
    "key": "value"
  }
}
```
