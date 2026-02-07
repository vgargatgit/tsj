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
- Dependencies: TSJ-0, TSJ-6.

### TSJ-8: Function and closure representation on JVM
- Why: Core TS/JS behavior depends on lexical closure semantics.
- Acceptance Criteria:
  - Captured variables preserved across nested function boundaries.
  - `this` binding strategy documented and implemented for supported patterns.
  - Closure fixtures pass (factory functions, counters, nested capture).
- Dependencies: TSJ-7.

### TSJ-9: Class and object model (MVP subset)
- Why: Most TS code uses class syntax and object literals.
- Acceptance Criteria:
  - Class constructors, fields, and methods compile and run.
  - Object literal property access and assignment works in runtime model.
  - Fixtures for inheritance basics pass.
- Dependencies: TSJ-7.

## Epic D: Runtime Compatibility Layer

### TSJ-10: Runtime primitives and coercion semantics
- Why: JS behavior requires consistent coercion and equality rules.
- Acceptance Criteria:
  - Runtime APIs for numbers, strings, booleans, null/undefined semantics.
  - Primitive fast lanes (`int32`, `double`) are supported with boundary boxing rules.
  - `==` and `===` behaviors for supported types verified by tests.
  - Documented known deviations.
- Dependencies: TSJ-0, TSJ-7.

### TSJ-11: Dynamic object runtime with prototype links
- Why: Property lookup semantics must match JS expectations.
- Acceptance Criteria:
  - `TsObject` with own-properties map + prototype pointer is implemented.
  - Property get/set/delete semantics implemented.
  - Prototype chain lookup supported.
  - Monomorphic inline property cache exists for generated call sites.
  - Fallback behavior for missing properties aligns with JS subset rules.
- Dependencies: TSJ-0, TSJ-9.

### TSJ-12: Module loader and initialization order
- Why: Multi-file TS projects require deterministic module linking.
- Acceptance Criteria:
  - Imports/exports resolved and initialized in dependency order.
  - ESM live binding semantics are validated for supported patterns.
  - Circular dependency behavior documented and tested for supported cases.
  - CLI packaging includes compiled modules and runtime.
- Dependencies: TSJ-0, TSJ-2, TSJ-9.

## Epic E: Async and Advanced Features

### TSJ-13: Promise runtime and async/await lowering
- Why: Modern TS applications rely on async heavily.
- Acceptance Criteria:
  - Async functions lower to explicit state machines in MIR.
  - Promise chaining works for core success/error flows.
  - Microtask ordering matches defined runtime semantics.
  - Differential tests for async sequencing pass.
- Dependencies: TSJ-0, TSJ-10, TSJ-12.

### TSJ-14: Error model and stack trace source mapping
- Why: Debuggability is mandatory for adoption.
- Acceptance Criteria:
  - Runtime exceptions map to TS source locations.
  - Source map format documented.
  - CLI flag enables readable stack traces in TS coordinates.
- Dependencies: TSJ-7.

### TSJ-15: Unsupported feature detection and diagnostics
- Why: Fast, clear failure is better than silent miscompilation.
- Acceptance Criteria:
  - Compiler detects non-MVP features (e.g. Proxy, eval, dynamic import) and emits errors.
  - Diagnostics include file/line, feature ID, and guidance.
  - Tests verify detection coverage for all documented non-goals in feature matrix.
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
1. Stories TSJ-0, TSJ-1 through TSJ-12, TSJ-15, and TSJ-19 are complete.
2. Differential suite passes for defined MVP language subset.
3. CLI can compile and run a multi-file sample app with predictable behavior.
