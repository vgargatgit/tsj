# TSJ: TypeScript to JVM Compiler

TSJ is a staged compiler project that lowers TypeScript into JVM-oriented IR and, eventually, JVM bytecode.

Current implementation status includes:
1. Architecture decisions and runtime contracts (`TSJ-0`)
2. Multi-module Maven monorepo with CI, lint, and tests (`TSJ-1`)
3. CLI bootstrap commands (`TSJ-2`)
4. Fixture-based differential harness (Node vs TSJ path) (`TSJ-3`)
5. TypeScript parser/type-check integration via compiler API bridge (`TSJ-4`)
6. Initial HIR/MIR/JIR pipeline + JSON dump tool (`TSJ-5`)
7. MIR CFG + lexical capture metadata for nested scopes/closures (`TSJ-6`)
8. JVM backend subset compile/run path for arithmetic, control flow, and function calls (`TSJ-7`)
9. JVM closure lowering for nested functions and captured variables (`TSJ-8`)
10. JVM class/object lowering for constructors, inheritance basics, and object literals (`TSJ-9`)
11. Runtime coercion/equality semantics for `==` vs `===` and `undefined` value handling (`TSJ-10`)
12. Dynamic object runtime upgrades with prototype links, delete semantics, shape tokens, and monomorphic property read caches (`TSJ-11`)
13. Multi-file module bootstrap via relative ESM import bundling, deterministic dependency initialization order, and live-binding-safe fixture coverage (`TSJ-12`)
14. Promise runtime + async/await lowering with deterministic microtask sequencing and rejection propagation (`TSJ-13`)
15. Async/promise error semantics with Promise `catch` + `finally` behavior and unhandled rejection emission (`TSJ-13d`)
16. Promise combinators (`all`, `race`, `allSettled`, `any`) with array-literal inputs and differential fixtures (`TSJ-13e`)
17. Top-level await lowering for entry/module initialization order with async diagnostics for unsupported await placements (`TSJ-13f`)
18. Runtime stack-trace source mapping with `--ts-stacktrace` CLI rendering in TypeScript coordinates (`TSJ-14`)
19. Unsupported feature policy gates for MVP non-goals (`dynamic import`, `eval`, `Function` constructor, `Proxy`) with feature-ID diagnostics (`TSJ-15`)
20. Opt-in interop bridge generation with allowlist enforcement and runtime codec helpers (`TSJ-19`)
21. Differential conformance suite execution with minimized repro output and feature coverage report generation (`TSJ-16`)

## Repository Layout

- `compiler/frontend`: TypeScript parser/type-check bridge and frontend results
- `compiler/ir`: HIR/MIR/JIR models, lowering pipeline, IR JSON dump tool
- `compiler/backend-jvm`: JVM backend module scaffold
- `runtime`: runtime scaffold
- `cli`: CLI commands (`compile`, `run`, `fixtures`)
- `tests/fixtures`: committed fixture inputs/expectations
- `docs`: architecture decisions, contracts, stories, and format docs

## Prerequisites

1. Java 21+
2. Maven 3.8+
3. Node.js 20+ (validated with Node 24)
4. TypeScript compiler API available:
   - Recommended: `npm i -D typescript`
   - Global `tsc` is also supported by the frontend bridge fallback.

## Build and Test

Run full reactor checks:

```bash
mvn -B -ntp test
```

Install artifacts:

```bash
mvn -B -ntp clean install
```

Run only selected modules:

```bash
mvn -B -ntp -pl compiler/frontend -am test
mvn -B -ntp -pl compiler/ir -am test
mvn -B -ntp -pl cli -am test
```

## CLI Usage (Bootstrap)

Compile TypeScript into TSJ artifact metadata:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build"
```

Run TSJ bootstrap execution path:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build"
```

Run with TypeScript stack-trace rendering on runtime failure:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --ts-stacktrace"
```

Run fixture harness:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="fixtures tests/fixtures"
```

Generate Java interop bridges from allowlisted targets:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="interop path/to/interop.properties --out build/interop"
```

CLI command contract is in `docs/cli-contract.md`.

TSJ-7 compile now emits generated classes under `<out>/classes`, and `tsj run` executes the generated JVM class before emitting run diagnostics.
TSJ-8 extends the same path with lexical closure support for nested function declarations and mutable captured locals.
TSJ-9 extends the same path with class constructors/methods, inheritance via `extends`/`super(...)`, and object literal property access/assignment.
TSJ-10 extends runtime semantics with `undefined`, abstract equality (`==`/`!=`) coercion, and strict equality (`===`/`!==`) separation.
TSJ-11 extends object runtime behavior with prototype mutation validation, `delete` runtime primitives, shape-token invalidation, and generated monomorphic property read cache fields.
TSJ-12 extends compile/run with bootstrap multi-file module loading for relative imports, deterministic dependency-first initialization, and baseline live-binding behavior for supported patterns.
TSJ-13 adds `async function` + `await` lowering over `TsjPromise`, throw-to-rejection normalization, and async sequencing tests that validate post-sync microtask ordering.
TSJ-13a extends async lowering with control-flow continuations for `if`/`while` blocks containing multiple standalone await suspension points.
TSJ-13b extends async language coverage with function expressions, arrow functions, async class/object methods, and await normalization across supported expression positions in async bodies.
TSJ-13c extends promise runtime semantics with thenable assimilation, self-resolution protection, first-settle-wins handling, and chained thenable adoption.
TSJ-13d extends async/promise error semantics with Promise `catch` + `finally` support and runtime unhandled rejection emission.
TSJ-13e adds Promise combinators (`all`, `race`, `allSettled`, `any`) and minimal array literal lowering for combinator inputs.
TSJ-13f adds top-level await lowering for entry + module initialization ordering (including transitive imports) and explicit diagnostics for unsupported await-in-while-condition placement.
TSJ-14 adds generated-class source maps and optional `--ts-stacktrace` TypeScript frame rendering for runtime failures.
TSJ-15 adds explicit non-goal feature gates with structured diagnostics (`featureId`, guidance, and source coordinates) and a documented matrix at `docs/unsupported-feature-matrix.md`.
TSJ-19 adds opt-in interop bridge generation from allowlisted Java targets and runtime `TsjInteropCodec` conversions for Java boundary calls.
TSJ-16 adds explicit differential suite execution outputs with minimized failure repro details and feature coverage report generation (`tests/fixtures/tsj-fixture-coverage.json`).

## Frontend and IR Tools

Frontend parser/type-check service contract:
- `docs/frontend-contract.md`

IR contract:
- `docs/ir-contract.md`

Dump IR JSON for a TS project:

```bash
mvn -B -ntp -pl compiler/ir -am exec:java \
  -Dexec.mainClass=dev.tsj.compiler.ir.IrDumpTool \
  -Dexec.args="path/to/tsconfig.json --out build/ir.json"
```

## Fixtures

Fixture format is documented in `docs/fixture-format.md`.

Seed fixture:
- `tests/fixtures/smoke-hello`
- `tests/fixtures/tsj7-control-flow`
- `tests/fixtures/tsj8-closure-counter`
- `tests/fixtures/tsj9-class-inheritance`
- `tests/fixtures/tsj9-object-literal`
- `tests/fixtures/tsj10-coercion`
- `tests/fixtures/tsj11-missing-property`
- `tests/fixtures/tsj12-modules`
- `tests/fixtures/tsj13-promise-then`
- `tests/fixtures/tsj13-async-await`
- `tests/fixtures/tsj13-async-reject`
- `tests/fixtures/tsj13a-async-if`
- `tests/fixtures/tsj13a-async-while`
- `tests/fixtures/tsj13b-async-arrow`
- `tests/fixtures/tsj13b-async-object-method`
- `tests/fixtures/tsj13c-thenable`
- `tests/fixtures/tsj13c-thenable-reject`
- `tests/fixtures/tsj13d-catch-finally`
- `tests/fixtures/tsj13d-finally-reject`
- `tests/fixtures/tsj13e-all-race`
- `tests/fixtures/tsj13e-allsettled-any`
- `tests/fixtures/tsj13e-any-reject`
- `tests/fixtures/tsj13f-top-level-await`
- `tests/fixtures/tsj13f-top-level-await-modules`
- `tests/fixtures/tsj13f-top-level-await-transitive`
- `tests/fixtures/tsj13f-top-level-await-while-unsupported`

## Project Planning Docs

- Backlog and story sequencing: `docs/stories.md`
- Architecture decisions: `docs/architecture-decisions.md`
- Story-to-architecture mapping: `docs/story-architecture-map.md`
- Runtime contracts: `docs/contracts/runtime-contracts-v0.1.md`
- Source-map format: `docs/source-map-format.md`
- Unsupported feature matrix: `docs/unsupported-feature-matrix.md`
- Interop bridge spec: `docs/interop-bridge-spec.md`

## Development Approach

This repository follows comprehensive TDD flow (red -> green -> refactor), as documented in `AGENTS.md`.
