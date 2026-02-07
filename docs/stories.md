# TypeScript to JVM Compiler: Story Backlog (Initial)

## Story Format
- `ID`
- `Title`
- `Why`
- `Acceptance Criteria`
- `Dependencies`

## Traceability
- Architecture decisions: `docs/architecture-decisions.md`
- Runtime contracts: `docs/contracts/runtime-contracts-v0.1.md`
- Story to AD map: `docs/story-architecture-map.md`

## Epic 0: Architecture Lock-In

### TSJ-0: ADR set and architecture contracts
- Why: Freeze core semantics and interfaces before implementation to avoid rework.
- Acceptance Criteria:
  - Decision records AD-01 through AD-07 are published in `docs/architecture-decisions.md`.
  - Runtime contracts are documented for `TsValue`, `TsObject`, module table, and scheduler API in `docs/contracts/runtime-contracts-v0.1.md`.
  - Story dependency map references architecture decisions in `docs/story-architecture-map.md`.
- Dependencies: none.

## Epic A: Project Foundation

### TSJ-1: Monorepo bootstrap with build pipeline
- Why: Establish repeatable build/test tooling for compiler, runtime, and CLI modules.
- Acceptance Criteria:
  - Gradle or Maven build runs from repo root.
  - Separate modules for `frontend`, `ir`, `backend-jvm`, `runtime`, `cli`.
  - CI runs lint + unit tests.
- Dependencies: none.

### TSJ-2: CLI skeleton (`tsj compile`, `tsj run`)
- Why: Provide user entrypoint and stable contract for incremental backend work.
- Acceptance Criteria:
  - `tsj compile <input> --out <dir>` command exists.
  - `tsj run <entry.ts>` compiles then executes generated artifact.
  - CLI displays structured diagnostics.
- Notes:
  - Bootstrap command contract is documented in `docs/cli-contract.md`.
- Dependencies: TSJ-1.

### TSJ-3: Fixture-based test harness
- Why: Needed for regression control and semantic comparison with Node.
- Acceptance Criteria:
  - Fixture format supports input files + expected output.
  - Harness can run fixture under Node and capture outputs.
  - Harness can run generated JVM artifact and compare outputs.
- Notes:
  - Fixture format is documented in `docs/fixture-format.md`.
  - Seed fixture lives at `tests/fixtures/smoke-hello`.
- Dependencies: TSJ-1.

## Epic B: Frontend and IR

### TSJ-4: TypeScript parser and type-check integration
- Why: Compiler correctness depends on accurate AST and symbol/type data.
- Acceptance Criteria:
  - Frontend loads `tsconfig.json`.
  - Produces typed AST for all project source files.
  - Surfaces TS diagnostics with file/line mapping.
- Notes:
  - Frontend service contract is documented in `docs/frontend-contract.md`.
- Dependencies: TSJ-1.

### TSJ-5: Initial IR design and serializer
- Why: Decouple frontend from backend and make transformations testable.
- Acceptance Criteria:
  - IR tiers exist as HIR, MIR, and JIR with explicit responsibilities.
  - MIR supports functions, blocks, branches, locals, calls, literals.
  - Frontend lowers a minimal TS subset into IR.
  - IR dump/print tool exists for debugging.
- Notes:
  - TSJ-5 bootstrap IR contract is documented in `docs/ir-contract.md`.
- Dependencies: TSJ-0, TSJ-4.

### TSJ-6: Control-flow and symbol resolution in IR
- Why: Needed for reliable codegen and optimizations.
- Acceptance Criteria:
  - Explicit CFG edges in IR.
  - Lexical scope and captured variable metadata represented.
  - Unit tests for nested scopes and closure capture.
- Notes:
  - TSJ-6 extends MIR with blocks, CFG edges, lexical scopes, and capture metadata.
- Dependencies: TSJ-5.

## Epic C: JVM Backend (MVP)

### TSJ-7: Bytecode emitter for expressions/statements subset
- Why: First runnable end-to-end path.
- Acceptance Criteria:
  - Emits valid `.class` files for arithmetic, branches, loops, calls.
  - Generated classes pass JVM verifier.
  - Fixtures for subset pass differential tests vs Node.
- Notes:
  - TSJ-7 implementation generates JVM classes through backend codegen + JDK compiler for the supported subset.
  - Differential fixture comparison ignores TSJ diagnostic JSON lines and compares runtime outputs.
- Dependencies: TSJ-0, TSJ-6.

### TSJ-8: Function and closure representation on JVM
- Why: Core TS/JS behavior depends on lexical closure semantics.
- Acceptance Criteria:
  - Captured variables preserved across nested function boundaries.
  - `this` binding strategy documented and implemented for supported patterns.
  - Closure fixtures pass (factory functions, counters, nested capture).
- Notes:
  - TSJ-8 backend lowers function declarations to runtime callables with closure cells.
  - Lexical captures are preserved through shared `TsjCell` references across nested scopes.
  - `this` is not supported in TSJ-8 subset; usage remains outside the supported feature surface.
- Dependencies: TSJ-7.

### TSJ-9: Class and object model (MVP subset)
- Why: Most TS code uses class syntax and object literals.
- Acceptance Criteria:
  - Class constructors, fields, and methods compile and run.
  - Object literal property access and assignment works in runtime model.
  - Fixtures for inheritance basics pass.
- Notes:
  - TSJ-9 backend parser/codegen supports `class`, `extends`, `new`, `this`, and constructor `super(...)` calls.
  - Runtime model uses `TsjClass` + `TsjObject` with prototype-based method dispatch and property mutation helpers.
  - Fixture coverage includes `tests/fixtures/tsj9-class-inheritance` and `tests/fixtures/tsj9-object-literal`.
- Dependencies: TSJ-7.

## Epic D: Runtime Compatibility Layer

### TSJ-10: Runtime primitives and coercion semantics
- Why: JS behavior requires consistent coercion and equality rules.
- Acceptance Criteria:
  - Runtime APIs for numbers, strings, booleans, null/undefined semantics.
  - Primitive fast lanes (`int32`, `double`) are supported with boundary boxing rules.
  - `==` and `===` behaviors for supported types verified by tests.
  - Documented known deviations.
- Notes:
  - Runtime adds explicit `undefined` sentinel value and coercion helpers for display, truthiness, and numeric conversion.
  - Backend lowering distinguishes abstract equality (`==`, `!=`) from strict equality (`===`, `!==`).
  - Fixture coverage includes `tests/fixtures/tsj10-coercion`.
  - Known deviations:
    object-to-primitive coercion paths for `==` are not implemented yet; these comparisons currently return `false`
    unless both operands are the same object reference.
- Dependencies: TSJ-0, TSJ-7.

### TSJ-11: Dynamic object runtime with prototype links
- Why: Property lookup semantics must match JS expectations.
- Acceptance Criteria:
  - `TsObject` with own-properties map + prototype pointer is implemented.
  - Property get/set/delete semantics implemented.
  - Prototype chain lookup supported.
  - Monomorphic inline property cache exists for generated call sites.
  - Fallback behavior for missing properties aligns with JS subset rules.
- Notes:
  - Runtime object model now includes `setPrototype` cycle checks, `deleteOwn`, and `shapeToken` invalidation.
  - Missing property reads return `undefined` sentinel and flow through display/coercion helpers.
  - Backend codegen emits monomorphic property-read cache fields per member-access call site.
  - Fixture coverage includes `tests/fixtures/tsj11-missing-property`.
  - Known deviations:
    `delete` and explicit prototype mutation syntax are not yet parsed/lowered by backend;
    runtime APIs exist and are covered by unit tests.
    Property-read cache currently specializes own-property hits; prototype-chain reads deopt to full lookup.
- Dependencies: TSJ-0, TSJ-9.

### TSJ-12: Module loader and initialization order
- Why: Multi-file TS projects require deterministic module linking.
- Acceptance Criteria:
  - Imports/exports resolved and initialized in dependency order.
  - ESM live binding semantics are validated for supported patterns.
  - Circular dependency behavior documented and tested for supported cases.
  - CLI packaging includes compiled modules and runtime.
- Notes:
  - Backend compile path now performs deterministic dependency-first source bundling for supported relative imports.
  - Supported import forms in TSJ-12 bootstrap:
    named imports (`import { x } from "./m.ts"`) and side-effect imports (`import "./m.ts"`).
  - Runtime includes `TsjModuleRegistry` with explicit module states and live export cells; unit tests cover ordering,
    idempotency, cycle-unsafe reads, and failure-state behavior.
  - Fixture coverage includes `tests/fixtures/tsj12-modules`.
  - Known deviations:
    module scope isolation is not yet implemented (modules are flattened into one generated program unit);
    only relative imports are supported;
    import aliases/default imports/namespace imports are rejected in TSJ-12 bootstrap;
    circular imports fail unless no unsafe read occurs during initialization.
- Dependencies: TSJ-0, TSJ-2, TSJ-9.

## Epic E: Async and Advanced Features

### TSJ-13: Promise runtime and async/await lowering foundation
- Why: Modern TS applications rely on async heavily.
- Acceptance Criteria:
  - Runtime supports Promise creation/chaining for baseline success/error flows.
  - Backend supports `async function` declarations and standalone `await` continuation lowering.
  - Microtask ordering is deterministic and covered by differential fixtures.
- Notes:
  - Runtime includes `TsjPromise`, `Promise.resolve`/`Promise.reject`, thrown-value normalization,
    and microtask queue semantics used by async continuations.
  - Fixture coverage includes:
    `tests/fixtures/tsj13-promise-then`, `tests/fixtures/tsj13-async-await`, and `tests/fixtures/tsj13-async-reject`.
  - Full JS-like async support is tracked by TSJ-13a through TSJ-13f.
- Dependencies: TSJ-0, TSJ-10, TSJ-12.

### TSJ-13a: Async state-machine lowering for control flow
- Why: Correct suspension/resume semantics require IR-backed state machines, not backend-only local rewrites.
- Acceptance Criteria:
  - MIR/JIR includes explicit async suspend/resume/state constructs.
  - Lowering supports `if`/`while`/nested blocks with multiple `await` points.
  - `return`/`throw`/`break`/`continue` semantics remain correct across suspension points.
  - `try`/`catch`/`finally` behavior is preserved through async state transitions.
- Notes:
  - Current implementation pass adds backend continuation lowering for async `if` + async `while` with nested blocks
    when `await` appears in supported standalone forms.
  - Fixture coverage includes `tests/fixtures/tsj13a-async-if` and `tests/fixtures/tsj13a-async-while`.
  - Known deviations in this pass:
    `await` in `if`/`while` conditions is still rejected;
    `break`/`continue`/`try`/`catch`/`finally` are not yet represented in the TSJ parser/lowering subset.
- Dependencies: TSJ-13, TSJ-6, TSJ-7.

### TSJ-13b: Async language surface completeness
- Why: Real TypeScript code uses async forms beyond named declarations.
- Acceptance Criteria:
  - Support `async` function expressions and async arrow functions.
  - Support async class methods and async object literal methods.
  - Support `await` in expression positions beyond standalone statement/initializer forms.
  - Unsupported placements fail with targeted diagnostics.
- Notes:
  - Current implementation pass adds parser/codegen support for:
    async function expressions, async arrow functions, async class methods, and async object-literal methods.
  - Async lowering now normalizes supported await-containing expressions into explicit await sites
    before continuation emission (e.g. return expressions, binary/call/object-literal value positions, async if conditions).
  - Fixture coverage includes `tests/fixtures/tsj13b-async-arrow` and `tests/fixtures/tsj13b-async-object-method`.
  - Known deviations in this pass:
    async while conditions with `await` remain unsupported;
    async methods in class/object declarations are only supported in standard method form (not generator/getter/setter variants).
- Dependencies: TSJ-13a, TSJ-9.

### TSJ-13c: Promise resolution procedure and thenable assimilation
- Why: Promise interop depends on full resolution semantics.
- Acceptance Criteria:
  - Runtime implements Promise resolution procedure with thenable assimilation.
  - Self-resolution and double-resolution protections match expected JS behavior.
  - Error propagation across chained thenables matches Node differential expectations.
- Notes:
  - `TsjPromise` now applies Promise resolution semantics for thenables by reading `then` from object values,
    invoking callable `then` handlers, and adopting nested thenable outcomes.
  - Resolution guardrails are implemented and tested for self-resolution and first-settle-wins behavior.
  - Runtime TDD coverage includes thenable throw-before-settlement rejection, nested thenable adoption,
    non-callable `then` handling, and callback-returned thenable chaining.
  - Differential fixture coverage includes `tests/fixtures/tsj13c-thenable` and `tests/fixtures/tsj13c-thenable-reject`.
- Dependencies: TSJ-13, TSJ-10.

### TSJ-13d: Async error semantics and rejection handling parity
- Why: Production async code depends on predictable error/rejection semantics.
- Acceptance Criteria:
  - `catch` and `finally` semantics match JS behavior in async and Promise chains.
  - Unhandled rejection behavior is defined, emitted, and test-covered.
  - Async error paths are differentially tested against Node.
- Notes:
  - Promise `catch` and `finally` runtime behavior is now implemented for value pass-through,
    rejection preservation, and `finally` rejection override semantics.
  - TSJ runtime emits `TSJ-UNHANDLED-REJECTION: <reason>` when rejected promises remain unhandled
    after the microtask turn.
  - Differential fixture coverage includes `tests/fixtures/tsj13d-catch-finally` and
    `tests/fixtures/tsj13d-finally-reject`.
- Dependencies: TSJ-13a, TSJ-13c, TSJ-14.

### TSJ-13e: Promise combinators
- Why: Application-level orchestration depends on standard Promise helpers.
- Acceptance Criteria:
  - Implement `Promise.all`, `Promise.race`, `Promise.allSettled`, and `Promise.any`.
  - Ordering and short-circuit semantics match Node.
  - Differential fixtures cover success and rejection edge cases for each combinator.
- Notes:
  - Runtime now implements combinator semantics for `Promise.all`, `Promise.race`, `Promise.allSettled`, and `Promise.any`.
  - TSJ parser/backend now supports array literals for combinator input lowering in TSJ-13e subset.
  - TSJ-13e combinators currently consume TSJ array-like inputs (including array literals); full generic iterator
    protocol parity remains a follow-up.
  - Differential fixture coverage includes `tests/fixtures/tsj13e-all-race`,
    `tests/fixtures/tsj13e-allsettled-any`, and `tests/fixtures/tsj13e-any-reject`.
- Dependencies: TSJ-13c.

### TSJ-13f: Top-level await and async conformance/diagnostics
- Why: Module-level async ordering and debuggability are required for practical adoption.
- Acceptance Criteria:
  - Module loader/runtime supports top-level await execution ordering across dependency graphs.
  - Differential async conformance suite is expanded for module + control-flow async semantics.
  - Diagnostics include async-specific guidance and source-mapped async stack traces.
- Notes:
  - JVM backend now detects top-level await and lowers entry/module initialization through async continuation flow.
  - Module initialization ordering with top-level await is covered for direct and transitive import graphs.
  - Differential fixture coverage includes `tests/fixtures/tsj13f-top-level-await`,
    `tests/fixtures/tsj13f-top-level-await-modules`,
    `tests/fixtures/tsj13f-top-level-await-transitive`, and
    `tests/fixtures/tsj13f-top-level-await-while-unsupported`.
  - Async diagnostics now include explicit unsupported placement guidance for `await` in while conditions.
  - Source-mapped async stack traces remain future work under TSJ-14.
- Dependencies: TSJ-12, TSJ-13a, TSJ-14, TSJ-16.

### TSJ-14: Error model and stack trace source mapping
- Why: Debuggability is mandatory for adoption.
- Acceptance Criteria:
  - Runtime exceptions map to TS source locations.
  - Source map format documented.
  - CLI flag enables readable stack traces in TS coordinates.
- Notes:
  - JVM backend now emits line-oriented source map files at
    `classes/dev/tsj/generated/*Program.tsj.map`.
  - `tsj run --ts-stacktrace` now renders best-effort TypeScript frame locations for runtime failures.
  - Source map format is documented in `docs/source-map-format.md`.
- Dependencies: TSJ-7.

### TSJ-15: Unsupported feature detection and diagnostics
- Why: Fast, clear failure is better than silent miscompilation.
- Acceptance Criteria:
  - Compiler detects non-MVP features (e.g. Proxy, eval, dynamic import) and emits errors.
  - Diagnostics include file/line, feature ID, and guidance.
  - Tests verify detection coverage for all documented non-goals in feature matrix.
- Notes:
  - TSJ-15 now enforces unsupported-feature failures with stable feature IDs:
    `TSJ15-DYNAMIC-IMPORT`, `TSJ15-EVAL`, `TSJ15-FUNCTION-CONSTRUCTOR`, and `TSJ15-PROXY`.
  - `TSJ-BACKEND-UNSUPPORTED` diagnostics now include `file`, `line`, `column`, `featureId`, and `guidance`
    when triggered by TSJ-15 feature gates.
  - Non-goal matrix is documented in `docs/unsupported-feature-matrix.md`.
  - Coverage exists at backend and CLI layers for direct and imported-module failures.
- Dependencies: TSJ-0, TSJ-4.

### TSJ-19: Interop bridge generation (opt-in)
- Why: Controlled Java interop is required for practical adoption without semantic drift.
- Acceptance Criteria:
  - Bridge generator creates stubs for allowlisted Java classes/methods.
  - `TsValue` codec conversions validated for primitives and object references.
  - Disallowed interop targets fail with explicit diagnostics.
- Dependencies: TSJ-0, TSJ-10.

## Epic F: Quality and Performance

### TSJ-16: Differential conformance suite (Node vs JVM)
- Why: Prevent semantic regressions.
- Acceptance Criteria:
  - CI runs differential suite on each PR.
  - Failing cases produce minimized repro output.
  - Feature coverage report generated.
- Dependencies: TSJ-3, TSJ-12.

### TSJ-17: Baseline optimization passes
- Why: Naive lowering will be too slow for real workloads.
- Acceptance Criteria:
  - Constant folding and dead code elimination passes implemented.
  - Benchmarks show measurable improvement on fixture set.
  - Optimization toggles available via CLI flags.
- Dependencies: TSJ-6, TSJ-7.

### TSJ-18: Performance benchmark suite and SLA draft
- Why: Set measurable goals for runtime viability.
- Acceptance Criteria:
  - Benchmark harness includes micro + macro workloads.
  - Initial SLA drafted (startup, throughput, memory targets).
  - Baseline numbers stored and tracked in CI artifacts.
- Dependencies: TSJ-16.

## First Three Sprints (Suggested)

### Sprint 1
- TSJ-0, TSJ-1, TSJ-2, TSJ-3, TSJ-4

### Sprint 2
- TSJ-5, TSJ-6, TSJ-7

### Sprint 3
- TSJ-8, TSJ-9, TSJ-10, TSJ-15, TSJ-19

## Exit Criteria for MVP Milestone
MVP is reached when:
1. Stories TSJ-0, TSJ-1 through TSJ-13, TSJ-15, and TSJ-19 are complete.
2. Differential suite passes for defined MVP language subset.

## Exit Criteria for Full Async Parity
Full async parity is reached when:
1. Stories TSJ-13a through TSJ-13f are complete.
2. Async differential suite passes for control-flow, Promise semantics, and top-level-await module ordering.
3. CLI can compile and run a multi-file sample app with predictable behavior.
