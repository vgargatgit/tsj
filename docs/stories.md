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

## Story Status Legend
- `Complete`: acceptance criteria satisfied for the intended scope.
- `Complete (Subset)`: shipping subset is complete, with explicit remaining AC gaps tracked.
- `Planned`: not implemented yet.

## Epic 0: Architecture Lock-In

### TSJ-0: ADR set and architecture contracts
- Why: Freeze core semantics and interfaces before implementation to avoid rework.
- Acceptance Criteria:
  - Decision records AD-01 through AD-07 are published in `docs/architecture-decisions.md`.
  - Runtime contracts are documented for `TsValue`, `TsObject`, module table, and scheduler API in `docs/contracts/runtime-contracts-v0.1.md`.
  - Story dependency map references architecture decisions in `docs/story-architecture-map.md`.
- Status: `Complete`.
- Dependencies: none.

## Epic A: Project Foundation

### TSJ-1: Monorepo bootstrap with build pipeline
- Why: Establish repeatable build/test tooling for compiler, runtime, and CLI modules.
- Acceptance Criteria:
  - Gradle or Maven build runs from repo root.
  - Separate modules for `frontend`, `ir`, `backend-jvm`, `runtime`, `cli`.
  - CI runs lint + unit tests.
- Status: `Complete`.
- Dependencies: none.

### TSJ-2: CLI skeleton (`tsj compile`, `tsj run`)
- Why: Provide user entrypoint and stable contract for incremental backend work.
- Acceptance Criteria:
  - `tsj compile <input> --out <dir>` command exists.
  - `tsj run <entry.ts>` compiles then executes generated artifact.
  - CLI displays structured diagnostics.
- Notes:
  - Bootstrap command contract is documented in `docs/cli-contract.md`.
- Status: `Complete`.
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
- Status: `Complete`.
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
- Status: `Complete`.
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
- Status: `Complete`.
- Dependencies: TSJ-0, TSJ-4.

### TSJ-6: Control-flow and symbol resolution in IR
- Why: Needed for reliable codegen and optimizations.
- Acceptance Criteria:
  - Explicit CFG edges in IR.
  - Lexical scope and captured variable metadata represented.
  - Unit tests for nested scopes and closure capture.
- Notes:
  - TSJ-6 extends MIR with blocks, CFG edges, lexical scopes, and capture metadata.
- Status: `Complete`.
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
- Status: `Complete`.
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
  - Dynamic `this` binding is now supported for function declarations, object method shorthand, and object
    function-expression members via receiver-aware callables; arrow functions preserve lexical `this`.
  - Fixture coverage includes `tests/fixtures/tsj8-closure-counter` and `tests/fixtures/tsj8-this-binding`.
- Status: `Complete`.
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
- Status: `Complete`.
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
  - TSJ-20 closes object-to-primitive coercion parity for `==`/`!=` via `valueOf`/`toString` conversion paths.
- Status: `Complete`.
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
    Property-read cache currently specializes own-property hits; prototype-chain reads deopt to full lookup.
- TSJ-21 closes syntax/runtime integration for `delete`, `__proto__` assignment, and `Object.setPrototypeOf(...)`.
- Status: `Complete`.
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
  - TSJ-22 adds module-level isolation via per-module init wrappers while preserving deterministic startup order.
  - Supported import forms for current module pipeline:
    named imports, side-effect imports, and import-alias bindings.
  - Default and namespace imports currently produce explicit feature diagnostics with IDs.
  - Runtime includes `TsjModuleRegistry` with explicit module states and live export cells; unit tests cover ordering,
    idempotency, cycle-unsafe reads, and failure-state behavior.
  - Fixture coverage includes `tests/fixtures/tsj12-modules`.
  - Known deviations:
    only relative imports are supported;
    default and namespace imports are intentionally feature-diagnosed (not compiled) in the current subset.
- Status: `Complete`.
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
- Status: `Complete`.
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
  - TSJ-23 adds explicit async state-machine metadata in IR (`MirAsyncFrame`, `MirAsyncState`,
    `MirAsyncSuspendPoint`) plus JIR async state ops for suspend/resume visibility.
  - Backend async lowering now supports `break`/`continue` control flow and `await` in async `while` conditions.
  - Backend parser/codegen now supports statement-level `try`/`catch`/`finally` in sync and async functions,
    including awaited catch/finally paths and finally return override behavior.
  - Fixture coverage includes `tests/fixtures/tsj13a-async-if` and `tests/fixtures/tsj13a-async-while`.
- Status: `Complete`.
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
  - Async methods in class/object declarations are supported in standard method form.
  - Async generator/getter/setter method variants are rejected with targeted `TSJ-BACKEND-UNSUPPORTED`
    diagnostics that explicitly reference TSJ-13b subset scope.
  - Fixture coverage includes `tests/fixtures/tsj13b-async-arrow`,
    `tests/fixtures/tsj13b-async-object-method`, and
    `tests/fixtures/tsj13b-async-generator-unsupported`.
- Status: `Complete`.
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
- Status: `Complete`.
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
- Status: `Complete`.
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
  - TSJ-24 extends combinator inputs to generic iterables:
    string iterables plus iterator protocol objects (`@@iterator`/`Symbol.iterator`/`iterator`) with
    iterator-close handling on abrupt completion.
  - Differential fixture coverage includes `tests/fixtures/tsj13e-all-race`,
    `tests/fixtures/tsj13e-allsettled-any`, `tests/fixtures/tsj13e-any-reject`,
    and `tests/fixtures/tsj24-string-iterables`.
- Status: `Complete`.
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
  - Source-mapped async stack traces and async continuation markers are completed under TSJ-25.
- Status: `Complete`.
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
- Status: `Complete`.
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
- Status: `Complete`.
- Dependencies: TSJ-0, TSJ-4.

### TSJ-19: Interop bridge generation (opt-in)
- Why: Controlled Java interop is required for practical adoption without semantic drift.
- Acceptance Criteria:
  - Bridge generator creates stubs for allowlisted Java classes/methods.
  - `TsValue` codec conversions validated for primitives and object references.
  - Disallowed interop targets fail with explicit diagnostics.
- Notes:
  - Added opt-in bridge generation command:
    `tsj interop <interop.properties> --out <dir>`.
  - Interop spec format and validation are documented in `docs/interop-bridge-spec.md`.
  - Runtime now includes interop codec/invocation helpers:
    `TsjInteropCodec` and `TsjJavaInterop`.
  - Bridge generation enforces allowlist failures with explicit code `TSJ-INTEROP-DISALLOWED`
    and feature ID `TSJ19-ALLOWLIST`.
  - TDD coverage includes:
    `runtime/src/test/java/dev/tsj/runtime/TsjInteropCodecTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/InteropBridgeGeneratorTest.java`,
    and CLI coverage in `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-0, TSJ-10.

## Epic F: Quality and Performance

### TSJ-16: Differential conformance suite (Node vs JVM)
- Why: Prevent semantic regressions.
- Acceptance Criteria:
  - CI runs differential suite on each PR.
  - Failing cases produce minimized repro output.
  - Feature coverage report generated.
- Notes:
  - Fixture harness now emits minimized repro strings per failing fixture, including
    compact mismatch details and copy-pasteable Node/TSJ commands.
  - `tsj fixtures` now emits `TSJ-FIXTURE-COVERAGE` diagnostics and writes a JSON
    feature-coverage report to `tsj-fixture-coverage.json` in the fixture root.
  - Coverage buckets are derived from fixture name prefixes (`tsj10-*`, `tsj13f-*`, etc.),
    with `unmapped` as fallback.
  - CI now runs the committed differential fixture suite on pull requests and pushes.
- Status: `Complete`.
- Dependencies: TSJ-3, TSJ-12.

### TSJ-17: Baseline optimization passes
- Why: Naive lowering will be too slow for real workloads.
- Acceptance Criteria:
  - Constant folding and dead code elimination passes implemented.
  - Benchmarks show measurable improvement on fixture set.
  - Optimization toggles available via CLI flags.
- Notes:
  - Backend now applies a TSJ-17 optimization pass before Java emission with:
    constant folding for unary/binary literal expressions and baseline dead-code elimination
    (unreachable statements after `return`/`throw`, `while(false)`, and constant-condition `if` branch pruning).
  - CLI compile/run commands now expose optimization toggles:
    `--optimize` (default) and `--no-optimize`.
  - Benchmark coverage is enforced in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`
    via `optimizationBenchmarkShowsGeneratedSourceReductionAcrossFixtureSet`, which compares
    generated-source bytes and runtime-operation counts across a fixture-like set.
- Status: `Complete`.
- Dependencies: TSJ-6, TSJ-7.

### TSJ-18: Performance benchmark suite and SLA draft
- Why: Set measurable goals for runtime viability.
- Acceptance Criteria:
  - Benchmark harness includes micro + macro workloads.
  - Initial SLA drafted (startup, throughput, memory targets).
  - Baseline numbers stored and tracked in CI artifacts.
- Notes:
  - New benchmark harness is available via `tsj bench <report.json>` with workload profiles:
    full suite (`micro + macro`) and smoke suite (`--smoke`) for fast validation.
  - Benchmark results are emitted as JSON baseline reports with compile/run timing,
    throughput, and peak-memory samples per workload.
  - CI now generates `benchmarks/tsj-benchmark-baseline.json` and uploads it as the
    `benchmark-baseline-report` artifact on each run.
  - Initial SLA draft is documented in `docs/performance-sla.md`.
- Status: `Complete`.
- Dependencies: TSJ-16.

## Epic G: Full AC Gap-Closure Backlog

### TSJ-20: Abstract equality object-to-primitive coercion parity
- Why: JS-style abstract equality correctness requires object-to-primitive conversion rules.
- Acceptance Criteria:
  - `==` / `!=` implements object-to-primitive coercion for supported built-ins.
  - Differential fixtures cover object-vs-primitive equality edge cases.
  - TSJ-10 status can be promoted from `Complete (Subset)` to `Complete`.
- Notes:
  - Runtime abstract equality now converts object operands via `valueOf`/`toString` before primitive comparison,
    including boolean-driven coercion flows (`obj == true`) and class-instance `valueOf` via receiver binding.
  - Fallback coercion for plain objects now aligns with Object.prototype-like string form (`"[object Object]"`) for
    supported subset behavior.
  - Coverage includes:
    `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`,
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`,
    and differential fixture `tests/fixtures/tsj20-abstract-equality`.
- Status: `Complete`.
- Dependencies: TSJ-10.

### TSJ-21: Object runtime syntax parity for delete/prototype mutation
- Why: Runtime APIs exist, but language-level syntax support is required for end-to-end behavior.
- Acceptance Criteria:
  - Frontend/backend lowers `delete` and explicit prototype mutation syntax for supported forms.
  - Differential fixtures validate behavior against Node for supported subset.
  - TSJ-11 status can be promoted from `Complete (Subset)` to `Complete`.
- Notes:
  - Parser recognizes unary `delete` and lowers supported member-access targets to runtime `deleteProperty`.
  - Assignment lowering now treats `obj.__proto__ = proto` as prototype mutation via runtime `setPrototype`.
  - Call lowering now recognizes `Object.setPrototypeOf(target, proto)` and routes to runtime prototype mutation API.
  - Runtime `setPrototype` now returns the mutated target to align with `Object.setPrototypeOf` expression behavior.
  - Coverage includes:
    `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`,
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`,
    and differential fixture `tests/fixtures/tsj21-object-runtime-syntax`.
- Status: `Complete`.
- Dependencies: TSJ-11.

### TSJ-22: Module isolation and import-surface expansion
- Why: Production module semantics require real module scoping and broader import compatibility.
- Acceptance Criteria:
  - Module scope isolation is implemented (no single-unit flattening semantics leakage).
  - Default import, namespace import, and alias forms are supported or explicitly diagnosed with feature IDs.
  - Circular dependency behavior is expanded and documented with conformance fixtures.
  - TSJ-12 status can be promoted from `Complete (Subset)` to `Complete`.
- Notes:
  - Backend module bundling now emits per-module init wrappers to isolate local declarations and avoid cross-module symbol leakage.
  - Import aliases are compiled for named imports (for example `import { x as y } from ...`).
  - Default and namespace imports now fail with explicit feature diagnostics and stable IDs:
    `TSJ22-IMPORT-DEFAULT` and `TSJ22-IMPORT-NAMESPACE`.
  - Circular module traversal supports safe dependency cycles; conformance fixtures cover supported behavior.
  - Coverage includes:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`,
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`,
    `tests/fixtures/tsj22-module-isolation`,
    and `tests/fixtures/tsj22-circular-safe`.
- Status: `Complete`.
- Dependencies: TSJ-12.

### TSJ-23: Async IR state-machine completeness
- Why: Full async control-flow correctness depends on explicit IR state-machine modeling.
- Acceptance Criteria:
  - MIR/JIR carries explicit suspend/resume/state constructs.
  - Lowering preserves `return`/`throw`/`break`/`continue` and `try`/`catch`/`finally` across suspension points.
  - Async control-flow differential suite expanded for nested/compound cases.
  - TSJ-13a status can be promoted from `Complete (Subset)` to `Complete`.
- Notes:
  - MIR model now carries explicit async frame/state metadata:
    `MirFunction.async`, `MirFunction.asyncFrame`, `MirAsyncState`, and `MirAsyncSuspendPoint`.
  - MIR lowering now emits explicit async state-machine ops:
    `ASYNC_STATE`, `ASYNC_SUSPEND`, `ASYNC_RESUME`, plus terminal/control ops for
    `RETURN`, `THROW`, `BREAK`, `CONTINUE`, `TRY_BEGIN`, `CATCH_BEGIN`, and `FINALLY_BEGIN`.
  - JIR methods now include async metadata via `JirMethod.async` and `JirMethod.asyncStateOps`
    so suspend/resume structure is visible in lowered JVM-oriented IR.
  - Differential coverage expanded with nested/compound async fixture:
    `tests/fixtures/tsj23-async-nested-control-flow`.
  - Additional async loop conformance fixtures cover:
    `tests/fixtures/tsj23-async-while-condition-await` and
    `tests/fixtures/tsj23-async-break-continue`.
  - Additional async try/finally conformance fixtures cover:
    `tests/fixtures/tsj23-async-try-catch-finally`,
    `tests/fixtures/tsj23-async-try-finally-override`, and
    `tests/fixtures/tsj23-async-try-finally-reject`.
  - Coverage includes:
    `compiler/ir/src/test/java/dev/tsj/compiler/ir/IrLoweringServiceTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`,
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`,
    and `cli/src/test/java/dev/tsj/cli/fixtures/DifferentialConformanceSuiteTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-13a.

### TSJ-24: Promise combinator iterable protocol parity
- Why: JS Promise combinators accept generic iterables, not only array-like structures.
- Acceptance Criteria:
  - `Promise.all/race/allSettled/any` accept generic iterable inputs.
  - Iterator closing/error behavior matches Node for supported cases.
  - TSJ-13e status can be promoted from `Complete (Subset)` to `Complete`.
- Notes:
  - Runtime iterable extraction now supports:
    string inputs, iterator protocol objects via `@@iterator`/`Symbol.iterator`/`iterator`,
    and existing array-like fallback behavior.
  - Iterator abrupt-completion path now invokes iterator `return()` when present before rejection.
  - Runtime unit coverage includes string iterable combinator paths and iterator-close behavior.
  - Differential fixture coverage includes `tests/fixtures/tsj24-string-iterables`.
- Status: `Complete`.
- Dependencies: TSJ-13e.

### TSJ-25: Source-mapped async stack trace parity
- Why: Async debugging parity requires stable TS coordinates across async boundaries.
- Acceptance Criteria:
  - Async stack traces map suspension/resume frames to TS source locations.
  - `tsj run --ts-stacktrace` includes async continuation boundaries in readable form.
  - Differential diagnostics tests verify async stack formatting behavior.
  - TSJ-13f status can be promoted from `Complete (Subset)` to `Complete`.
- Notes:
  - CLI `--ts-stacktrace` now wires async unhandled-rejection throwable reasons through TypeScript source-map rendering.
  - Mapped frame rendering now deduplicates by method+TS location and marks continuation frames with
    `--- async continuation ---` and `[async-continuation]`.
  - Fixture descriptors now support optional runtime args (`node.args`, `tsj.args`) so diagnostics behaviors can be
    exercised directly in conformance suites.
  - Differential diagnostics fixture coverage includes `tests/fixtures/tsj25-async-stacktrace`.
  - Coverage includes:
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`,
    `cli/src/test/java/dev/tsj/cli/fixtures/FixtureHarnessTest.java`,
    and `cli/src/test/java/dev/tsj/cli/fixtures/DifferentialConformanceSuiteTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-13f, TSJ-14.

## Epic H: Interop Expansion Roadmap

### TSJ-26: TypeScript interop binding syntax and frontend checks
- Why: Interop must be expressible from TypeScript source without manual Java glue steps.
- Acceptance Criteria:
  - Define and implement a TS-level interop binding syntax for Java targets (for example `java:`-prefixed imports).
  - Frontend validates interop binding shape and emits typed symbol metadata for backend lowering.
  - Invalid/malformed interop bindings fail with explicit diagnostics and source coordinates.
  - Differential fixtures include positive and negative binding cases.
- Notes:
  - TSJ-19 bridge generation exists but is CLI-driven; TSJ-26 introduces language-surface integration.
  - Diagnostic contract should include stable interop feature IDs and guidance messages.
  - Implemented syntax:
    `import { max, min as minimum } from "java:java.lang.Math";`
  - Frontend bridge now emits interop binding metadata (`interopBindings`) and validates:
    `TSJ26-INTEROP-SYNTAX`, `TSJ26-INTEROP-MODULE-SPECIFIER`, and `TSJ26-INTEROP-BINDING`.
  - Backend module bundling lowers valid interop bindings to runtime callable handles via
    `TsjRuntime.javaStaticMethod(...)`.
  - Differential fixture coverage includes:
    `tests/fixtures/tsj26-interop-binding-positive` and
    `tests/fixtures/tsj26-interop-binding-invalid`.
  - Coverage includes:
    `compiler/frontend/src/test/java/dev/tsj/compiler/frontend/TypeScriptFrontendServiceTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`,
    `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java`,
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`,
    and `cli/src/test/java/dev/tsj/cli/fixtures/FixtureHarnessTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-4, TSJ-12, TSJ-19.

### TSJ-27: JAR/classpath inputs for compile and run
- Why: Real interop requires loading user-provided jars during compile-time validation and runtime execution.
- Acceptance Criteria:
  - CLI supports explicit classpath/jar inputs for `compile` and `run`.
  - Artifact metadata records interop classpath entries for reproducible execution.
  - Runner classloading resolves compiled TSJ classes plus configured interop jars.
  - Integration tests validate external jar invocation from fixture projects.
- Notes:
  - Must handle cross-platform classpath parsing and normalized artifact serialization.
  - Error diagnostics should distinguish missing jars from missing classes/methods.
  - CLI now supports:
    `--classpath <path{sep}path...>` and repeated `--jar <jar-file>` on both `compile` and `run`.
  - Compile artifact metadata records external entries with deterministic keys:
    `interopClasspath.count` and `interopClasspath.<index>`.
  - Runtime execution now resolves generated classes plus configured interop entries via URLClassLoader.
  - Interop reflection now resolves Java classes from thread context classloader to honor external run classpath.
  - Integration coverage includes:
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java` and
    `cli/src/test/java/dev/tsj/cli/fixtures/FixtureHarnessTest.java`
    (including a generated-fixture external-jar run path).
- Status: `Complete`.
- Dependencies: TSJ-2, TSJ-19, TSJ-26.

### TSJ-28: Compile pipeline integration for bridge generation
- Why: Interop should be part of normal compile flow, not a separate manual pre-step.
- Acceptance Criteria:
  - Backend compile can discover/request required interop targets and generate bridge sources automatically.
  - Generated bridges are compiled and packaged alongside program classes in one compile command.
  - Incremental compile avoids regenerating unchanged bridge outputs.
  - Existing `tsj interop` command remains available as an explicit control/debug path.
- Notes:
  - Requires deterministic mapping from TS interop bindings to Java bridge symbols.
  - Must preserve TSJ-19 allowlist enforcement behavior.
  - Compile/run now support `--interop-spec <interop.properties>` for integrated bridge generation.
  - Pipeline auto-discovers requested bridge targets from `java:` imports across entry + relative module graph.
  - Integrated flow writes an effective auto-spec (`.tsj-auto-interop.properties`) and generates bridges to:
    `<out>/generated-interop`.
  - Allowlist policy is preserved through existing `InteropBridgeGenerator` enforcement (`TSJ19-ALLOWLIST`).
  - Auto-bridge cache (`.tsj-auto-interop-cache.properties`) fingerprints interop spec + discovered targets and
    skips regeneration when unchanged.
  - Generated bridge sources are compiled into program classes directory during the same `tsj compile` invocation.
  - Existing explicit command `tsj interop <interop.properties> --out <dir>` remains unchanged and supported.
  - Coverage includes:
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-19, TSJ-26, TSJ-27.

### TSJ-29: Invocation model expansion (constructors, instance members, overloads)
- Why: Static-method-only interop is too limited for mainstream Java APIs.
- Acceptance Criteria:
  - Support interop calls for constructors, instance methods, static fields, and instance fields.
  - Overload/arity resolution is deterministic and tested for numeric/boxing edge cases.
  - Varargs and primitive/wrapper conversions are supported for documented subset.
  - Diagnostics include actionable mismatch details when no compatible overload exists.
- Notes:
  - Interop runtime now supports binding-driven invocation modes:
    static methods, constructors (`$new`), instance methods (`$instance$<method>`),
    static field get/set (`$static$get$<field>`, `$static$set$<field>`), and
    instance field get/set (`$instance$get$<field>`, `$instance$set$<field>`).
  - Overload resolution is deterministic via conversion scoring and stable candidate ordering.
  - Varargs calls are supported for static/instance methods and constructors through runtime argument packing.
  - No-match diagnostics now include argument-type summary, candidate signatures, and conversion-failure context.
  - Bridge generation/validation path now supports TSJ-29 binding targets and still enforces allowlist policy.
  - Coverage includes:
    `runtime/src/test/java/dev/tsj/runtime/TsjInteropCodecTest.java`,
    `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/InteropBridgeGeneratorTest.java`,
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-27, TSJ-28.

### TSJ-30: Interop codec and async/callback boundary parity
- Why: Practical jar usage depends on robust value conversion and async/callback adaptation.
- Acceptance Criteria:
  - Codec supports arrays, lists/maps (documented subset), enums, and nullable/reference semantics.
  - Functional-interface bridging allows TS callables to be passed as Java callbacks for supported signatures.
  - `CompletableFuture` and Promise boundary adapters exist with deterministic error propagation semantics.
  - Differential and runtime tests cover conversion failures and callback/async lifecycle edge cases.
- Notes:
  - Runtime codec now supports bidirectional subset conversions for:
    arrays, `List`, `Map`, enums, `Optional`, nullable/undefined reference values.
  - Functional interface bridging is implemented via dynamic proxy adaptation from TS callables
    to single-abstract-method Java interfaces.
  - Async boundary adapters are implemented for:
    Java `CompletableFuture` -> TSJ `TsjPromise` and TSJ `TsjPromise` -> Java `CompletableFuture`.
  - Interop invocation now supports callback parameters and `await` over Java future-returning APIs.
  - Conversion mismatch diagnostics are explicit for invalid array-like, enum constant, and unsupported target types.
  - Coverage includes:
    `runtime/src/test/java/dev/tsj/runtime/TsjInteropCodecTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`,
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-13, TSJ-19, TSJ-29.

### TSJ-31: Any-JAR readiness, policy modes, and conformance pack
- Why: “Use any Java jar from TS” needs both capability and explicit safety controls.
- Acceptance Criteria:
  - Interop policy modes are defined and implemented:
    strict allowlist mode (default) and opt-in broad classpath mode.
  - End-to-end fixtures run against multiple third-party jars with deterministic outputs in CI.
  - Security/documentation package defines risks, guardrails, and recommended defaults.
  - Story status can be marked complete only when TS projects can call jar APIs without manual Java bridge editing.
- Notes:
  - CLI now supports `--interop-policy strict|broad` on `compile` and `run`.
    default is `strict`.
  - Strict mode enforces allowlist workflow by requiring `--interop-spec` for discovered `java:` imports;
    violations emit `TSJ-INTEROP-POLICY`.
  - Broad mode is explicit opt-in and allows classpath interop without allowlist gating.
  - Existing source-level interop flow supports calling jar APIs without manual Java bridge authoring.
  - End-to-end CI coverage includes external-jar integration tests for:
    static calls, constructors/instance members/fields, callbacks, and `CompletableFuture` await paths
    in `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`
    and fixture-harness jar execution in
    `cli/src/test/java/dev/tsj/cli/fixtures/FixtureHarnessTest.java`.
  - Security/operational guidance is documented in `docs/interop-policy.md`.
- Status: `Complete`.
- Dependencies: TSJ-27, TSJ-28, TSJ-29, TSJ-30.

## Epic I: Spring Boot Authoring from TypeScript

### TSJ-32: JVM annotation and reflection metadata parity
- Why: Spring relies on runtime-visible annotations and method/parameter metadata for wiring and behavior.
- Acceptance Criteria:
  - Backend can emit runtime-visible JVM annotations from supported TS annotation syntax/mapping rules.
  - Generated classes preserve method/parameter metadata needed by Spring reflection paths.
  - Metadata behavior is validated through reflection-based tests, including constructor and method parameter discovery.
  - Unsupported annotation constructs fail with explicit diagnostics and guidance.
- Notes:
  - Interop bridge generation now supports runtime-visible annotation emission through spec keys:
    `classAnnotations` and `bindingAnnotations.<binding>`.
  - Bridge annotation configurations are validated with explicit diagnostics:
    `TSJ-INTEROP-ANNOTATION` + `featureId=TSJ32-ANNOTATION-SYNTAX`.
  - Generated Java compilation now uses `-parameters` for both program and generated interop bridge classes.
  - Reflection coverage validates:
    class/method annotation visibility and method parameter-name metadata on generated bridge classes.
  - TSJ-32a adds a compiler-side decorator model extraction/mapping contract:
    `TsDecoratorModelExtractor` + `TsDecoratorAnnotationMapping`,
    with targeted diagnostics:
    `TSJ-DECORATOR-SYNTAX`, `TSJ-DECORATOR-UNSUPPORTED`, `TSJ-DECORATOR-TARGET`
    and `featureId=TSJ32A-DECORATOR-MODEL`.
  - TSJ-32b adds typed annotation-attribute parsing (`TsAnnotationAttributeParser`) for
    primitives/strings, enum constants, class literals, and arrays, with
    `TSJ-DECORATOR-ATTRIBUTE` diagnostics (`featureId=TSJ32B-ANNOTATION-ATTRIBUTES`).
  - TSJ-32c adds parameter-level decorator extraction/mapping and adapter emission for
    `@RequestParam`, `@PathVariable`, `@RequestHeader`, and `@RequestBody`,
    with `TSJ-DECORATOR-PARAM` diagnostics (`featureId=TSJ32C-PARAM-ANNOTATIONS`).
  - Reflection coverage now validates class/method annotation values and method parameter
    annotation+name visibility on generated TS Spring adapters.
- Status: `Complete`.
- Dependencies: TSJ-26, TSJ-29.

### TSJ-32a: TS decorator model for JVM annotation emission
- Why: Full TS-authored framework interop requires decorators to survive frontend/lowering, not only interop-spec annotations.
- Acceptance Criteria:
  - Supported TS decorator syntax for class/method (and planned parameter hooks) is represented in compiler IR/lowering metadata.
  - Mapping contract from TS decorator symbols to JVM annotation types is defined and implemented for supported subset.
  - Unsupported decorator forms emit targeted diagnostics with stable feature IDs and guidance.
  - Unit tests cover parser/lowering extraction and backend annotation emission handoff.
- Notes:
  - Implemented extraction pipeline over entry + relative module graph via:
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsDecoratorModelExtractor.java`.
  - Implemented mapping contract via:
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsDecoratorAnnotationMapping.java`.
  - TSJ-34 generator now consumes extracted decorator model for adapter emission handoff.
  - Coverage includes:
    `TsDecoratorModelExtractorTest`,
    `TsDecoratorAnnotationMappingTest`,
    and updated `TsjSpringWebControllerGeneratorTest` handoff assertions.
- Status: `Complete`.
- Dependencies: TSJ-32.

### TSJ-32b: Annotation attribute/value mapping completeness
- Why: Framework annotations often require non-trivial attributes (enums, arrays, class literals), not marker-only behavior.
- Acceptance Criteria:
  - Supported attribute value forms are mapped from TS syntax to JVM annotation attributes:
    primitives/strings, enum constants, class literals, and arrays (documented subset).
  - Validation rejects incompatible or ambiguous attribute values with actionable diagnostics.
  - Reflection tests validate runtime-visible annotation values on generated classes/methods.
  - Docs explicitly list supported/unsupported attribute shapes and defaults.
- Notes:
  - Implemented typed decorator attribute parser in:
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsAnnotationAttributeParser.java`.
  - Supported TSJ-32b attribute value subset:
    string/number/boolean, `enum("<fqcn>.<CONST>")`, `classOf("<fqcn>")`, arrays, and object-literal attribute bags.
  - Validation rejects unsupported/ambiguous values with:
    `TSJ-DECORATOR-ATTRIBUTE` and `featureId=TSJ32B-ANNOTATION-ATTRIBUTES`.
  - TS web adapter generation now consumes parsed attribute values for:
    `@RequestMapping`, route mappings, `@ExceptionHandler`, and `@ResponseStatus`.
  - Reflection/value coverage includes:
    `TsAnnotationAttributeParserTest`,
    `TsjSpringWebControllerGeneratorTest`,
    and `TsjSpringWebControllerIntegrationTest`.
- Status: `Complete`.
- Dependencies: TSJ-32a.

### TSJ-32c: Parameter annotation and reflection parity
- Why: Spring and other frameworks use constructor/method parameter metadata and annotations for binding/autowiring.
- Acceptance Criteria:
  - Parameter-level annotation emission is supported for documented subset.
  - Constructor/method parameter names and annotations are stable and discoverable via reflection.
  - Conformance tests cover framework-style reflection paths for constructor and method parameter discovery.
  - Unsupported parameter annotation shapes fail with explicit diagnostics.
- Notes:
  - Decorator model now includes parameter metadata through
    `TsDecoratedParameter` and `TsDecoratedMethod.parameters`.
  - Extractor supports parameter decorators in TSJ-32c subset:
    `@RequestParam`, `@PathVariable`, `@RequestHeader`, `@RequestBody`.
  - Generated web adapters now emit mapped parameter annotations and preserve
    parameter names for reflection discovery (`-parameters`).
  - Backend compile preprocessing strips inline parameter decorators for TS parsing,
    while generator/extractor enforces targeted diagnostics:
    `TSJ-DECORATOR-PARAM` + `featureId=TSJ32C-PARAM-ANNOTATIONS`.
  - Coverage includes:
    `TsDecoratorModelExtractorTest`,
    `TsjSpringWebControllerGeneratorTest`,
    `TsjSpringWebControllerIntegrationTest`,
    and `JvmBytecodeCompilerTest`.
- Status: `Complete`.
- Dependencies: TSJ-32a, TSJ-32b.

### TSJ-33: Spring DI/component model baseline from TS
- Why: Full app authoring needs `@Component`/`@Service`/`@Configuration`/`@Bean` semantics from TS-authored classes.
- Acceptance Criteria:
  - TS-authored classes can participate in Spring component scanning and constructor injection.
  - `@Configuration` + `@Bean` methods authored in TS are discoverable and instantiated by Spring.
  - Bean lifecycle and dependency resolution behavior is validated against Java/Kotlin reference behavior for supported subset.
  - Integration fixtures boot a Spring context and assert bean graph correctness.
- Notes:
  - Initial subset prioritizes constructor injection and does not include field injection.
  - Added interop-spec Spring bridge subset keys:
    `springConfiguration=true` and
    `springBeanTargets=<class>#<binding>,...`.
  - For Spring bean targets, generated bridge classes emit:
    `@org.springframework.context.annotation.Configuration`
    and generated bean methods emit:
    `@org.springframework.context.annotation.Bean`.
  - Bean method signatures are typed from reflected constructor/static method signatures (non-primitive parameter and return constraints).
  - Added explicit diagnostics for unsupported Spring bridge targets:
    `TSJ-INTEROP-SPRING` + `featureId=TSJ33-SPRING-BEAN`.
  - Test coverage includes:
    bridge-source unit tests,
    CLI interop command coverage, and
    constructor-injection integration via Spring-compatible context harness.
  - Parent completion gate:
    TSJ-33 can move from `Complete (Subset)` to `Complete` only when TSJ-33a through TSJ-33f
    are `Complete`, and this story no longer carries a remaining-gap note.
  - Completion gate is now satisfied:
    TSJ-33a through TSJ-33f are complete and the DI/lifecycle parity gate is active in CI.
- Status: `Complete`.
- Dependencies: TSJ-27, TSJ-31, TSJ-32.

### TSJ-33a: Direct TS component stereotype lowering
- Why: Full DI support needs TS-authored classes to participate in Spring scanning without interop-spec bridge indirection.
- Acceptance Criteria:
  - TS-authored stereotype decorators (`@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration`)
    are lowered to runtime-visible Spring annotations on generated JVM classes.
  - Generated TS-authored component classes are discoverable through Spring component scanning.
  - Integration tests boot a Spring context and validate TS-authored component discovery and constructor injection.
  - Unsupported stereotype/decorator placements emit targeted diagnostics.
- Notes:
  - Implemented direct TS stereotype adapter generation in:
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjSpringComponentGenerator.java`.
  - Generated adapter classes are emitted under:
    `<out>/generated-components/dev/tsj/generated/spring/*.java`.
  - Supported stereotype decorators in TSJ-33a subset:
    `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration`.
  - Adapter methods delegate to TS program runtime through:
    `Program.__tsjInvokeClass(...)`, enabling constructor-arg forwarding.
  - Constructor-injection integration is validated via test Spring context harness in:
    `TsjSpringComponentIntegrationTest`.
  - CLI compile flow now auto-generates TS component adapters and persists artifact metadata:
    `tsjSpringComponents.componentCount` and `tsjSpringComponents.generatedSourceCount`.
- Status: `Complete`.
- Dependencies: TSJ-32a, TSJ-32c.

### TSJ-33b: DI surface expansion for TS-authored beans
- Why: Real services require more than constructor-only injection and bridge-generated bean methods.
- Acceptance Criteria:
  - TS-authored `@Bean` methods are emitted and resolved by Spring in documented subset.
  - Injection modes are expanded for documented subset (constructor plus selected field/setter paths, if enabled).
  - Dependency resolution failures produce explicit diagnostics with minimal repro context.
  - Integration tests validate bean graph correctness across multi-bean modules.
- Notes:
  - TSJ-33b subset is implemented for TS-authored `@Configuration` classes with `@Bean` methods
    through direct adapter generation in `TsjSpringComponentGenerator`.
  - `@Bean` methods now emit runtime-visible
    `@org.springframework.context.annotation.Bean` and delegate into TS runtime dispatch.
  - Invalid `@Bean` placement on non-`@Configuration` classes emits targeted diagnostics:
    `code=TSJ-SPRING-COMPONENT`, `featureId=TSJ33B-DI-SURFACE`.
  - Constructor-injection behavior for generated components/configurations is validated in
    integration context harness tests.
  - TSJ-33d closes injection-mode completeness and richer dependency-diagnostic coverage for this DI surface story.
- Status: `Complete`.
- Dependencies: TSJ-33a.

### TSJ-33c: Bean lifecycle and parity verification
- Why: Lifecycle callbacks and init/destroy semantics are required for production-grade Spring usage.
- Acceptance Criteria:
  - Supported lifecycle annotations/callbacks (documented subset) are honored on TS-authored beans.
  - Startup/shutdown lifecycle order is validated in integration tests.
  - Circular dependency and lifecycle failure paths emit clear diagnostics.
  - Differential behavior checks against Java/Kotlin reference services exist for supported lifecycle flows.
- Notes:
  - TSJ-33c lifecycle subset is implemented for TS-authored component adapters in
    `TsjSpringComponentGenerator`.
  - Supported lifecycle decorators in subset:
    `@PostConstruct` and `@PreDestroy` on zero-argument methods.
  - Generated adapters emit runtime-visible lifecycle annotations and integration harness context invokes:
    post-construct callbacks during refresh and pre-destroy callbacks during close.
  - Invalid lifecycle placements/shapes emit targeted diagnostics:
    `code=TSJ-SPRING-COMPONENT`, `featureId=TSJ33C-LIFECYCLE`.
  - Java/Kotlin differential lifecycle parity is now validated by TSJ-33f gate/report.
- Status: `Complete`.
- Dependencies: TSJ-33a, TSJ-33b.

### TSJ-33d: Injection mode completeness and dependency diagnostics
- Why: Constructor-only injection is insufficient for practical Spring service graphs.
- Acceptance Criteria:
  - TS-authored beans support documented field/setter injection subset in generated Spring adapters.
  - Dependency-resolution diagnostics cover missing bean, ambiguous bean, and qualifier/primary conflicts
    with minimal repro context.
  - Integration tests validate mixed constructor+field+setter injection paths across multi-bean modules.
  - Docs define supported injection modes and explicit non-goals for the subset.
- Notes:
  - Extends TSJ-33b from constructor-only behavior to broader DI modes needed by existing Spring codebases.
  - Diagnostics should remain stable under `TSJ-SPRING-COMPONENT` with a dedicated
    `featureId=TSJ33D-INJECTION-MODES`.
  - Any unsupported injection shape should fail at compile/generation time with actionable guidance.
- Status: `Complete`.
- Dependencies: TSJ-33b.

### TSJ-33e: Lifecycle ordering and circular-dependency diagnostics
- Why: Production Spring parity requires deterministic lifecycle ordering and clear cycle/failure reporting.
- Acceptance Criteria:
  - Startup/shutdown ordering of supported lifecycle callbacks is validated for TS-authored bean graphs.
  - Circular dependency paths emit explicit diagnostics that include cycle path summary.
  - Lifecycle callback failures are distinguished from DI-resolution failures in diagnostics.
  - Integration tests assert refresh/close ordering behavior and lifecycle failure paths.
- Notes:
  - Extends TSJ-33c from callback annotation emission to full lifecycle-order behavior validation.
  - Focuses on deterministic semantics and diagnostics before broader Spring lifecycle feature expansion.
  - Test harness context now records lifecycle callback execution order and applies deterministic shutdown
    ordering (reverse of refresh callback order) for supported lifecycle callbacks.
  - Circular dependency diagnostics now emit explicit cycle-path summaries for unresolved TS-authored
    component graphs (for example, `A -> B -> A`) with `featureId=TSJ33E-LIFECYCLE`.
  - Lifecycle callback failures now use dedicated diagnostics
    (`TSJ-SPRING-LIFECYCLE`, `featureId=TSJ33E-LIFECYCLE`) distinct from TSJ-33d DI diagnostics.
  - Coverage includes lifecycle ordering, cycle-path diagnostics, and refresh/close failure-path
    distinction tests in `TsjSpringComponentIntegrationTest`.
- Status: `Complete`.
- Dependencies: TSJ-33c, TSJ-33d.

### TSJ-33f: Java/Kotlin differential parity gate for DI and lifecycle
- Why: TSJ-33 cannot be fully complete without measurable parity against Java/Kotlin reference behavior.
- Acceptance Criteria:
  - Differential suite compares TS-authored versus Java/Kotlin reference apps
    for supported DI and lifecycle scenarios.
  - Conformance report artifact publishes pass/fail per scenario with reproducible fixtures.
  - CI gate fails on regressions for certified TSJ-33 DI/lifecycle subset behaviors.
  - TSJ-33 completion criteria are updated to `Complete` once TSJ-33d/33e/33f are all complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-33 from subset to complete.
  - Report should mirror existing readiness artifacts format used by TSJ-37/TSJ-38 style gates.
  - Implemented differential conformance harness:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityHarness.java`.
  - Report model + CI artifact output:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityReport.java`,
    `compiler/backend-jvm/target/tsj33f-di-lifecycle-parity-report.json`.
  - Differential fixtures are versioned under:
    `tests/spring-matrix/tsj33f-*`.
  - Scenarios currently covered:
    `mixed-injection`, `lifecycle-order`, and `cycle-diagnostic`.
  - Gate tests:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-33d, TSJ-33e.

### TSJ-34: Spring Web MVC surface from TS controllers
- Why: Building backend apps requires REST endpoints, request mapping, and JSON serialization from TS-authored controllers.
- Acceptance Criteria:
  - TS-authored classes can be exposed as Spring MVC controllers with mapping annotations.
  - Request binding and response serialization work for documented DTO subset.
  - Error handling path supports mapped HTTP responses for supported exception flows.
  - End-to-end web integration tests validate startup and HTTP behavior.
- Notes:
  - Interop-spec path remains supported for Java-target bridges via:
    `springWebController=true`,
    `springWebBasePath`,
    `springRequestMappings.<binding>=<HTTP_METHOD> <path>`,
    `springErrorMappings=<exceptionFqcn>:<statusCode>,...`.
  - TS-authored controller decorator subset is now supported in compile flow:
    `@RestController`, `@RequestMapping("/base")`,
    `@GetMapping|@PostMapping|@PutMapping|@DeleteMapping|@PatchMapping`,
    `@ExceptionHandler("<exceptionFqcn>")`,
    `@ResponseStatus(<statusCode>)`.
  - Backend parser strips supported TSJ-34 decorator lines and lowers class/method bodies normally.
  - Compile now auto-generates Spring adapter sources under:
    `<out>/generated-web/dev/tsj/generated/web/*.java`,
    delegating into generated TSJ program class via `__tsjInvokeController(...)`
    or `__tsjInvokeClassWithInjection(...)` for constructor-injected controllers.
  - Generated adapter methods emit Spring web annotations:
    `@RestController`, `@RequestMapping`, route mapping annotations, `@RequestParam`,
    and mapped `@ExceptionHandler` + `@ResponseStatus`.
  - Added explicit diagnostics for unsupported Spring web configurations:
    `TSJ-INTEROP-WEB` + `featureId=TSJ34-SPRING-WEB` (interop-spec path) and
    `TSJ-WEB-CONTROLLER` (TS-authored decorator path).
  - Test coverage includes:
    interop bridge-source unit tests,
    TS-decorator web adapter generator unit tests,
    TS-authored web dispatch integration test,
    backend parser decorator acceptance/diagnostic tests,
    and CLI compile coverage for generated TS web adapters.
  - Parent completion gate:
    TSJ-34 can move from `Complete (Subset)` to `Complete` only when TSJ-34a through TSJ-34f
    are `Complete`, and this story no longer carries a remaining-gap note.
  - TSJ-34f now closes the packaged-runtime conformance gap, so TSJ-34 parent completion
    criteria are satisfied.
- Status: `Complete`.
- Dependencies: TSJ-32, TSJ-33.

### TSJ-34a: Request binding completeness for TS-authored controllers
- Why: Practical APIs require full request binding forms, not only generated `argN` parameter mapping.
- Acceptance Criteria:
  - TS-authored controller routes support documented request bindings:
    path variables, query params, headers, and request-body DTO binding.
  - Parameter annotation mapping for request bindings is emitted on generated JVM adapters/classes.
  - Binding failures produce structured diagnostics with endpoint and parameter context.
  - Integration tests validate binding behavior across representative endpoint patterns.
- Notes:
  - TSJ-34a request-binding subset is implemented in
    `TsjSpringWebControllerGenerator` and web-controller integration tests.
  - Generated adapter methods support TS-authored request binding decorators for:
    `@PathVariable`, `@RequestParam`, `@RequestHeader`, and `@RequestBody`.
  - Named binding decorators without arguments default to the TS parameter name.
  - Route binding validation now emits targeted structured diagnostics with endpoint/parameter context:
    `code=TSJ-WEB-BINDING`, `featureId=TSJ34A-REQUEST-BINDING`.
  - Validated failures include:
    unmatched `@PathVariable` names against route templates and multiple `@RequestBody` parameters on one route.
  - Integration dispatcher coverage now validates path/query/header/body argument resolution and
    contextual missing-binding errors across mapped routes.
  - Current binder limits (documented subset):
    request-body values are pass-through objects in test/runtime harness semantics and do not perform
    nested DTO conversion/coercion.
- Status: `Complete`.
- Dependencies: TSJ-32c, TSJ-34.

### TSJ-34b: Response serialization and error/status semantics parity
- Why: Endpoint correctness depends on response-body conversion and status handling consistency.
- Acceptance Criteria:
  - Documented response types are serialized correctly (primitive, DTO, collection/map subset).
  - Status semantics are supported for success and mapped error flows in TS-authored controllers.
  - Error envelope behavior for supported exception flows is validated in integration tests.
  - Unsupported response/error shapes emit explicit diagnostics.
- Notes:
  - TSJ-34b subset is implemented for generated TS-authored web adapters and integration dispatcher/runtime harness.
  - Route methods now support optional `@ResponseStatus(<code>)` emission on generated adapters
    for supported success/error status codes:
    `200`, `201`, `202`, `204`, `400`, `401`, `403`, `404`, `409`, `422`, `500`.
  - Success and mapped-error response bodies are serialized through JSON-like conversion for documented subset:
    primitives, strings, TS object literals (including array-like TS objects), Java map/iterable/array values,
    and null/undefined values.
  - Unsupported response body shapes now produce explicit diagnostics in integration runtime harness:
    `TSJ-WEB-RESPONSE` with endpoint context.
  - TSJ-34e now closes the booted converter/error-envelope parity gap for the documented subset.
- Status: `Complete`.
- Dependencies: TSJ-34a.

### TSJ-34c: End-to-end boot/runtime web conformance
- Why: Mock-dispatch coverage is insufficient for framework readiness claims.
- Acceptance Criteria:
  - CI runs booted Spring app tests with real HTTP requests against TS-authored controllers.
  - Startup diagnostics clearly separate TSJ compile issues vs Spring runtime wiring failures.
  - Multi-endpoint fixture(s) validate routing, binding, serialization, and mapped error behavior under real server startup.
  - Conformance report links TS-authored service behavior to Java/Kotlin reference service behavior for supported subset.
- Notes:
  - TSJ-34c subset introduces a booted HTTP conformance harness in
    `TsjSpringWebControllerIntegrationTest` that starts a real local HTTP server and drives
    real client requests against generated TS-authored controller adapters.
  - Multi-endpoint TS-authored conformance scenarios now validate, under real HTTP:
    routing, path/query/header/body binding, response serialization, status semantics,
    and mapped error behavior.
  - Startup diagnostics now distinguish compile-time vs runtime wiring failures in test/runtime gate:
    compile failures surface `TSJ-WEB-CONTROLLER`,
    runtime boot failures surface `TSJ-WEB-BOOT`.
  - A conformance report linking TS-authored and reference-controller behavior is emitted to:
    `compiler/backend-jvm/target/tsj34c-web-conformance-report.json`
    and is uploaded as a CI artifact.
  - TSJ-34f now provides the packaged-runtime conformance closure; TSJ-34c remains the faster
    harness-level regression gate for web subset behavior.
- Status: `Complete`.
- Dependencies: TSJ-34a, TSJ-34b, TSJ-36.

### TSJ-34d: Controller constructor/DI parity for TS-authored web adapters
- Why: No-arg-only controller constructors block realistic Spring MVC applications.
- Acceptance Criteria:
  - TS-authored controller adapters support constructor-based dependency injection from Spring context
    for documented subset.
  - Generated web adapters no longer require no-arg TS controller constructors.
  - Dependency resolution failures for controller wiring emit targeted diagnostics with controller + dependency context.
  - Integration tests validate multi-controller/service wiring paths under booted web context.
- Notes:
  - Closes TSJ-34 remaining gap around constructor limitations in decorator-generated controller path.
  - Diagnostic contract should remain stable under `TSJ-WEB-CONTROLLER` with
    `featureId=TSJ34D-CONTROLLER-DI`.
  - `TsjSpringWebControllerGenerator` now:
    supports parameterized TS controller constructors, emits constructor fields/parameters
    (including optional parameter-level `@Qualifier`) on generated web adapters, and routes
    controller method dispatch through `__tsjInvokeClassWithInjection(...)` when constructor
    dependencies are present.
  - Unsupported constructor-parameter decorator shapes now fail with targeted diagnostics:
    `TSJ-WEB-CONTROLLER` + `featureId=TSJ34D-CONTROLLER-DI`.
  - Integration coverage now validates:
    multi-controller constructor wiring under booted HTTP runtime and missing-dependency
    diagnostics with controller + qualifier context.
  - Test Spring context diagnostics now preserve TSJ-33d component diagnostics while emitting
    controller wiring failures under `TSJ-WEB-CONTROLLER` + `TSJ34D-CONTROLLER-DI`.
- Status: `Complete`.
- Dependencies: TSJ-33d, TSJ-34a.

### TSJ-34e: Booted HTTP conversion and error-envelope parity
- Why: Harness-level JSON-like conversion is insufficient for production Spring web behavior.
- Acceptance Criteria:
  - Booted runtime tests validate Spring `HttpMessageConverter` behavior for supported media types and DTO shapes.
  - Error response contract is standardized for supported exception paths and asserted in integration tests.
  - Response/status serialization parity is verified against Java/Kotlin reference controllers for supported subset.
  - Unsupported media/conversion/error shapes emit explicit diagnostics with endpoint context.
- Notes:
  - Closes TSJ-34b remaining gap around converter parity and error-envelope consistency.
  - Should produce a deterministic conformance artifact for CI triage, aligned to existing report patterns.
  - `TsjSpringWebControllerIntegrationTest` now validates converter/error-envelope parity in both
    dispatcher and booted-HTTP execution paths for supported media/error subset.
  - Unsupported media/conversion paths now emit explicit diagnostics with endpoint context via
    `TSJ-WEB-CONVERTER` and standardized error envelope code `TSJ-WEB-ERROR`.
  - Conformance report artifact is emitted to:
    `compiler/backend-jvm/target/tsj34e-web-converter-report.json`.
- Status: `Complete`.
- Dependencies: TSJ-34b, TSJ-34d.

### TSJ-34f: Full Spring Boot packaged web conformance gate
- Why: Final web parity claims require packaged-app startup and real HTTP checks through production-like path.
- Acceptance Criteria:
  - CI executes packaged Spring Boot runtime conformance for TS-authored web app
    (compile -> package -> boot -> HTTP assertions).
  - Startup diagnostics clearly separate compile, bridge, package, and runtime wiring failures.
  - Conformance report artifact compares TS-authored and Java/Kotlin reference behavior
    for certified TSJ-34 web subset scenarios.
  - TSJ-34 is promoted to `Complete` once TSJ-34d/34e/34f are complete and green.
- Notes:
  - Closes TSJ-34c remaining gap by replacing harness-only gate with packaged runtime gate.
  - Integrates with TSJ-36 packaging flow but remains focused on web-conformance certification.
  - `spring-package` now compiles generated TS web/component adapter sources into the packaged
    classes output before jar assembly, so TS-authored adapters are executable in packaged path.
  - Added packaged conformance gate tests in:
    `cli/src/test/java/dev/tsj/cli/TsjSpringPackagedWebConformanceTest.java`.
  - Conformance artifact path:
    `cli/target/tsj34f-packaged-web-conformance-report.json`.
  - CI now runs a dedicated TSJ-34f packaged gate step and uploads the TSJ-34f artifact.
- Status: `Complete`.
- Dependencies: TSJ-34e, TSJ-36.

### TSJ-35: Spring AOP/proxy compatibility for TS-authored beans
- Why: Real Spring services rely on proxy-based behaviors such as transactions and method interception.
- Acceptance Criteria:
  - TS-authored beans can be proxied by Spring for supported AOP scenarios.
  - `@Transactional` baseline behavior is verified in integration tests for supported transaction manager setup.
  - Proxy invocation preserves expected method dispatch semantics and runtime type safety constraints.
  - Unsupported proxy scenarios produce explicit diagnostics or documented limitations.
- Notes:
  - TSJ-35 subset adds decorator mapping for `@Transactional` on TS-authored component classes/methods.
  - Transactional TS component adapters now emit:
    class/method-level `@org.springframework.transaction.annotation.Transactional`.
  - Transactional component adapters generate a companion interface (`<Component>TsjComponentApi`)
    and implement it, enabling interface-based proxying in the TSJ-35 subset harness.
  - Test Spring-context harness now applies JDK dynamic proxies for transactional beans
    when a `PlatformTransactionManager` is present:
    begin/commit on success and begin/rollback on invocation failure.
  - Integration tests validate:
    successful transactional invocation commit path,
    rollback path on invocation failure,
    and runtime diagnostics when transaction manager wiring is missing.
  - Unsupported shapes now emit explicit diagnostics:
    `code=TSJ-SPRING-AOP`, `featureId=TSJ35-AOP-PROXY`
    for constructor-level `@Transactional` usage and unsupported runtime proxy setup.
  - Parent completion gate:
    TSJ-35 can move from `Complete (Subset)` to `Complete` only when TSJ-35a, TSJ-35b, and TSJ-35c
    are `Complete`, and this story no longer carries a remaining-gap note.
  - Completion gate is now satisfied:
    TSJ-35a, TSJ-35b, and TSJ-35c are complete and the transactional/AOP differential gate is active in CI.
- Status: `Complete`.
- Dependencies: TSJ-33, TSJ-34.

### TSJ-35a: Class-based proxy parity and proxy strategy diagnostics
- Why: Interface-only proxying is insufficient for many Spring service implementations.
- Acceptance Criteria:
  - TS-authored beans without interfaces can be proxied using documented class-based (CGLIB-style) subset.
  - Proxy strategy selection (JDK dynamic vs class-based) is deterministic and diagnosable.
  - Unsupported proxy targets (for example final classes/methods in class-proxy path) emit explicit diagnostics.
  - Integration tests validate transactional interception on interface-free TS-authored beans.
- Notes:
  - Closes TSJ-35 class-based proxy parity gap.
  - Diagnostics should remain stable under `TSJ-SPRING-AOP` with
    `featureId=TSJ35A-CLASS-PROXY`.
- Status: `Complete`.
- Dependencies: TSJ-35.

### TSJ-35b: Booted Spring transaction/AOP runtime conformance
- Why: Harness-level proxy checks are insufficient for production Spring Boot parity claims.
- Acceptance Criteria:
  - Booted Spring runtime tests validate transactional behavior for supported subset
    against a real transaction manager setup.
  - Supported transaction semantics (documented subset) include commit/rollback behavior and
    representative propagation paths.
  - Startup/runtime diagnostics distinguish AOP/transaction infrastructure wiring failures
    from application invocation failures.
  - Reproducible fixtures cover multi-bean transactional call chains under packaged runtime path.
- Notes:
  - Closes TSJ-35 runtime integration gap using production-like packaged boot path.
  - Should integrate with TSJ-36 packaging/startup workflow for deterministic CI reproducibility.
  - Implemented by `TsjSpringAopRuntimeConformanceHarness` +
    `TsjSpringAopRuntimeConformanceTest` with report artifact:
    `compiler/backend-jvm/target/tsj35b-aop-runtime-conformance-report.json`.
  - Conformance scenarios cover commit chain, rollback chain, infrastructure failure
    diagnostics (`TSJ-SPRING-AOP`), and application-invocation failure classification.
- Status: `Complete`.
- Dependencies: TSJ-35a, TSJ-36.

### TSJ-35c: Differential parity gate for transactional/AOP behavior
- Why: TSJ-35 needs measurable parity versus Java/Kotlin behavior before claiming full completion.
- Acceptance Criteria:
  - Differential suite compares TS-authored and Java/Kotlin reference services
    for supported transactional/AOP scenarios.
  - Conformance report artifact publishes pass/fail by scenario with reproducible fixtures.
  - CI gate fails on regressions for certified TSJ-35 transactional/AOP subset semantics.
  - TSJ-35 is promoted to `Complete` once TSJ-35a/35b/35c are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-35 from subset to complete.
  - Report format should align with existing conformance/readiness artifacts for TSJ-34/37/38.
  - Implemented differential conformance harness:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityHarness.java`.
  - Report model + CI artifact output:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityReport.java`,
    `compiler/backend-jvm/target/tsj35c-aop-differential-parity-report.json`.
  - Differential suite compares TSJ-35b runtime scenarios against Java/Kotlin reference services for:
    `commit-chain`, `rollback-chain`, `missing-transaction-manager`, and `application-invocation-failure`.
  - Gate tests:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-35a, TSJ-35b, TSJ-37.

### TSJ-36: Spring Boot packaging, startup, and dev workflow integration
- Why: Teams need one-command build/run workflows equivalent to standard Spring Boot projects.
- Acceptance Criteria:
  - CLI/build integration can package TS-authored Spring apps as runnable Spring Boot jars.
  - Runtime classpath/resource handling supports `application.yml`/`application.properties` and static resources.
  - Startup diagnostics clearly distinguish TSJ compile failures, bridge failures, and Spring runtime failures.
  - CI templates validate build + boot + smoke endpoint checks.
- Notes:
  - TSJ-36 subset adds CLI command:
    `tsj spring-package <entry.ts> --out <dir> ...`.
  - Packaging path compiles entry via existing TSJ compile pipeline and emits jar artifact:
    default `<out>/tsj-spring-app.jar` (override with `--boot-jar`).
  - Jar packaging now includes compiled TSJ classes and compile artifact metadata
    (`META-INF/tsj/program.tsj.properties`).
  - Resource handling subset supports:
    auto-discovered `<entry-parent>/src/main/resources` and `<entry-parent>/resources`,
    plus explicit repeated `--resource-dir <dir>`.
  - Startup diagnostics are stage-aware in subset:
    `stage=compile` for TSJ compile failures,
    `stage=bridge` for interop bridge/classload failures,
    `stage=package` for jar/resource packaging failures,
    `stage=runtime` with `TSJ-SPRING-BOOT` for smoke-run startup failures.
  - Optional smoke startup validation:
    `--smoke-run` launches packaged main class and emits `TSJ-SPRING-SMOKE-SUCCESS` on success.
  - CI template now includes dedicated TSJ-36 spring-package smoke step.
  - TSJ-36c dev-loop parity closure is implemented with dedicated harness/report coverage:
    `TsjDevLoopParityHarness`,
    `TsjDevLoopParityReport`,
    and `TsjDevLoopParityTest`.
  - Dev-loop parity artifact path:
    `cli/target/tsj36c-dev-loop-parity.json`.
  - CI runs a dedicated TSJ-36c parity gate and uploads the TSJ-36c report artifact.
- Status: `Complete`.
- Dependencies: TSJ-27, TSJ-33, TSJ-34, TSJ-35.

### TSJ-36a: Spring Boot repackage/fat-jar parity
- Why: Thin-jar workflows are insufficient for portable production delivery.
- Acceptance Criteria:
  - Packaging flow supports Spring Boot-style executable fat-jar layout for documented subset.
  - Packaged artifact is self-contained for runtime dependencies without external classpath wiring.
  - Packaging diagnostics clearly distinguish manifest/repackage/resource failures.
  - Integration tests validate fat-jar startup across representative TS-authored app fixtures.
- Notes:
  - Closes TSJ-36 gap around repackage/fat-jar parity.
  - Artifact metadata and reproducibility guarantees should remain deterministic.
- Status: `Complete`.
- Dependencies: TSJ-36.

### TSJ-36b: Embedded server endpoint smoke and startup verification
- Why: Startup-only checks do not verify end-to-end service availability.
- Acceptance Criteria:
  - CI smoke gate validates packaged app startup and HTTP endpoint availability on embedded server.
  - Smoke fixtures include representative healthy and controlled-failure boot scenarios.
  - Startup diagnostics separate app boot failures from endpoint-level behavior failures.
  - Smoke reports include deterministic repro commands and captured port/runtime context.
- Notes:
  - Closes TSJ-36 gap around embedded server endpoint smoke checks.
  - Should integrate with TSJ-34f packaged web-conformance gate without duplicating coverage logic.
  - `spring-package` now supports endpoint smoke options:
    `--smoke-endpoint-url`, `--smoke-timeout-ms`, `--smoke-poll-ms`.
  - Endpoint smoke supports HTTP probes and deterministic `stdout://<marker>` probes for constrained CI/test
    environments; startup vs endpoint failures are separated via
    `TSJ-SPRING-BOOT` (`failureKind=startup`) and `TSJ-SPRING-ENDPOINT` (`failureKind=endpoint`).
  - Smoke success diagnostics now include reproducible `reproCommand` and runtime context
    (`runtimeMs`, endpoint URL/port/status when available).
  - CLI coverage includes healthy and controlled-failure endpoint smoke scenarios plus option-validation checks in
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`.
  - CI now runs a dedicated TSJ-37b/TSJ-37d matrix baseline lane and keeps spring-package smoke coverage active.
- Status: `Complete`.
- Dependencies: TSJ-34f, TSJ-36a.

### TSJ-36c: Dev-loop tooling parity for TS-authored Spring apps
- Why: Practical adoption requires fast edit-build-run workflows comparable to Java/Kotlin projects.
- Acceptance Criteria:
  - Documented developer workflow supports incremental compile/package/run for TS-authored Spring apps.
  - CLI/tooling subset includes developer-friendly diagnostics and commands for rapid iteration.
  - CI validates essential dev-loop commands/scripts remain functional.
  - Docs define supported dev-loop ergonomics and explicit non-goals.
- Notes:
  - Closes TSJ-36 gap around full developer-loop parity.
  - Implemented dev-loop parity harness/report flow:
    `TsjDevLoopParityHarness`,
    `TsjDevLoopParityReport`,
    and `TsjDevLoopParityTest`.
  - Gate covers compile/run/package/smoke command path health plus iterative edit-and-rerun scenario.
  - Dev-loop report artifact:
    `cli/target/tsj36c-dev-loop-parity.json`.
  - Workflow and non-goals are documented in:
    `docs/dev-loop-workflow.md`.
  - CI now runs a dedicated TSJ-36c parity gate and uploads the TSJ-36c artifact.
- Status: `Complete`.
- Dependencies: TSJ-36a, TSJ-36b.

### TSJ-37: Spring ecosystem integration matrix
- Why: Framework viability requires predictable behavior across high-use Spring modules.
- Acceptance Criteria:
  - Integration matrix is defined and executed for selected starters/modules:
    web, validation, data-jdbc or data-jpa (subset), actuator, and security baseline.
  - Each module has explicit supported/unsupported feature notes with reproducible fixtures.
  - Differential tests compare TS-authored and Java/Kotlin-authored reference services for behavior parity in supported areas.
  - Regressions are tracked via CI artifact reports.
- Notes:
  - Scope should begin with a minimal stable module set and expand incrementally.
  - TSJ-37 subset introduces `TsjSpringIntegrationMatrixHarness` +
    `TsjSpringIntegrationMatrixTest` in `compiler/backend-jvm`.
  - Matrix report artifact path:
    `compiler/backend-jvm/target/tsj37-spring-module-matrix.json`.
  - Reproducible fixtures are stored in `tests/spring-matrix/`:
    `tsj37-web-supported`,
    `tsj37-validation-supported`,
    `tsj37-validation-unsupported`,
    `tsj37-data-jdbc-supported`,
    `tsj37-data-jdbc-unsupported`,
    `tsj37-actuator-supported`,
    `tsj37-actuator-unsupported`,
    `tsj37-security-supported`,
    `tsj37-security-unsupported`.
  - Supported modules in subset:
    `web` parity check compares TS-authored adapter behavior to a Java reference controller;
    `validation` parity subset covers `@Validated` with representative
    `@NotBlank`/`@Size`/`@Min`/`@Max`/`@NotNull` checks and deterministic field/message mapping;
    `data-jdbc` baseline subset covers repository query-method naming flows (`countBy*`/`findBy*`/`existsBy*`)
    plus service transactional wiring checks with deterministic query/wiring/transaction diagnostics;
    `actuator` baseline parity covers health/info/metrics read-operation behavior and status-code checks;
    `security` baseline parity covers filter-chain + method-security subset behavior
    (401/403/200 status outcomes for `@PreAuthorize` hasRole/hasAnyRole patterns).
  - Explicit unsupported module features in subset:
    data-jdbc `@Query` decorator usage remains explicitly gated with stable
    `TSJ-DECORATOR-UNSUPPORTED` diagnostics for out-of-scope query annotation paths.
  - Unsupported validation decorators (for example `@Email`) are explicitly gated
    with stable parameter-decorator diagnostics (`TSJ-DECORATOR-PARAM`) in TSJ-37a subset.
  - Unsupported actuator operation shapes (for example `@WriteOperation`) remain explicitly diagnosed
    with stable `TSJ-DECORATOR-UNSUPPORTED` guidance in TSJ-37c subset.
  - TSJ-37e closure gate is now implemented with certification harness/report coverage:
    `TsjSpringModuleCertificationHarness`,
    `TsjSpringModuleCertificationReport`,
    and `TsjSpringModuleCertificationTest`.
  - Certification artifact path:
    `compiler/backend-jvm/target/tsj37e-spring-module-certification.json`.
  - CI runs a dedicated TSJ-37e certification step and uploads the TSJ-37e report artifact.
- Status: `Complete`.
- Dependencies: TSJ-34, TSJ-35, TSJ-36.

### TSJ-37a: Validation module runtime parity
- Why: Bean validation constraints are foundational for real API/service correctness.
- Acceptance Criteria:
  - TS-authored apps support documented Jakarta Validation subset at runtime
    for request/bean validation paths.
  - Validation failure behavior and diagnostics are deterministic and reproducible.
  - Integration fixtures cover representative constraint annotations and message/field mapping subset.
  - Matrix report marks validation module as supported for certified subset scenarios.
- Notes:
  - Closes TSJ-37 validation gap.
  - Unsupported constraint shapes should emit explicit diagnostics with stable feature IDs.
  - Implemented via `TsjValidationSubsetEvaluator` +
    `TsjSpringIntegrationMatrixHarness` validation-module path in `compiler/backend-jvm`.
  - Adds fixture coverage:
    `tests/spring-matrix/tsj37-validation-supported/main.ts`,
    `tests/spring-matrix/tsj37-validation-unsupported/main.ts`.
  - Matrix report now marks `validation` as supported for certified subset scenarios.
- Status: `Complete`.
- Dependencies: TSJ-34e, TSJ-37.

### TSJ-37b: Data repository/ORM module parity
- Why: Persistence-layer interoperability is required for framework viability claims.
- Acceptance Criteria:
  - TS-authored apps support documented data module subset
    (data-jdbc or data-jpa baseline paths) with reproducible integration fixtures.
  - Repository wiring and core CRUD/query flows pass under supported subset constraints.
  - Runtime diagnostics clearly separate repository wiring, transaction, and query execution failures.
  - Matrix report marks selected data module subset as supported with versioned fixture metadata.
- Notes:
  - Closes TSJ-37 data integration gap.
  - Scope should align with TSJ-42 ORM compatibility trajectory without claiming full ecosystem parity.
  - Implemented via `TsjDataJdbcSubsetEvaluator` + `TsjSpringIntegrationMatrixHarness`
    data-jdbc module path in `compiler/backend-jvm`.
  - Adds fixture coverage:
    `tests/spring-matrix/tsj37-data-jdbc-supported/main.ts`,
    `tests/spring-matrix/tsj37-data-jdbc-unsupported/main.ts`.
  - Matrix report now marks `data-jdbc` as supported for certified TSJ-37b baseline scenarios.
  - Runtime diagnostics are separated under stable codes:
    `TSJ-SPRING-DATA-WIRING`, `TSJ-SPRING-DATA-TRANSACTION`, and `TSJ-SPRING-DATA-QUERY`.
  - Out-of-scope `@Query` decorator paths remain explicitly gated with
    `TSJ-DECORATOR-UNSUPPORTED` in TSJ-37b subset.
- Status: `Complete`.
- Dependencies: TSJ-35b, TSJ-37a, TSJ-42.

### TSJ-37c: Actuator baseline parity
- Why: Operational readiness requires health/info and basic observability endpoint compatibility.
- Acceptance Criteria:
  - Packaged TS-authored apps expose documented actuator baseline subset
    (for example health/info and selected metrics paths).
  - Actuator endpoint behavior and status codes are validated in integration fixtures.
  - Diagnostics for unsupported/disabled actuator features are explicit and reproducible.
  - Matrix report marks actuator baseline support scope with clear non-goals.
- Notes:
  - Closes TSJ-37 actuator gap.
  - Must preserve secure-by-default behavior expectations for management endpoints.
  - Matrix harness now validates actuator baseline parity for health/info/metrics read operations
    with status-code checks and explicit unsupported-operation diagnostics.
  - Report artifact remains consolidated in:
    `compiler/backend-jvm/target/tsj37-spring-module-matrix.json`.
- Status: `Complete`.
- Dependencies: TSJ-36a, TSJ-37.

### TSJ-37d: Security filter and method-security baseline parity
- Why: Security stack compatibility is a core requirement for production Spring workloads.
- Acceptance Criteria:
  - TS-authored apps support documented security baseline subset:
    filter-chain path and method-security checks.
  - Authentication/authorization behavior for supported scenarios is validated by integration fixtures.
  - Security misconfiguration diagnostics are explicit and actionable.
  - Matrix report marks security baseline support with reproducible scenario coverage.
- Notes:
  - Closes TSJ-37 security gap.
  - Scope is baseline parity only; advanced policies/providers remain out of scope unless explicitly added.
  - Implemented via `TsjSecuritySubsetEvaluator` + `TsjSpringIntegrationMatrixHarness`
    security-module path in `compiler/backend-jvm`.
  - Added fixture coverage:
    `tests/spring-matrix/tsj37-security-supported/main.ts` and
    `tests/spring-matrix/tsj37-security-unsupported/main.ts`.
  - Matrix report now marks `security` as supported for certified TSJ-37d baseline scenarios.
  - CI now runs a dedicated TSJ-37d matrix-baseline step in addition to full suite execution.
- Status: `Complete`.
- Dependencies: TSJ-35c, TSJ-37a.

### TSJ-37e: Full Spring module matrix parity gate and certification artifact
- Why: TSJ-37 needs a hard conformance gate before moving from subset to complete.
- Acceptance Criteria:
  - Matrix execution covers supported validation, data, actuator, and security subset scenarios.
  - CI publishes a conformance artifact with pass/fail by module, scenario, and fixture version.
  - Differential parity checks against Java/Kotlin references are required for certified module subsets.
  - TSJ-37 is promoted to `Complete` once TSJ-37a/37b/37c/37d/37e are complete and green.
- Notes:
  - This is the closure gate story for TSJ-37.
  - Implemented certification harness/report flow:
    `TsjSpringModuleCertificationHarness`,
    `TsjSpringModuleCertificationReport`,
    and `TsjSpringModuleCertificationTest`.
  - Conformance report captures pass/fail by module, fixture, and fixture version
    with differential parity signals (`tsjPassed`, `javaReferencePassed`, `kotlinReferencePassed`, `parityPassed`).
  - Certification artifact path:
    `compiler/backend-jvm/target/tsj37e-spring-module-certification.json`.
  - CI now runs a dedicated TSJ-37e certification step and uploads the TSJ-37e report artifact.
  - Spring matrix and certification scope docs are updated in:
    `docs/spring-ecosystem-matrix.md`.
- Status: `Complete`.
- Dependencies: TSJ-37a, TSJ-37b, TSJ-37c, TSJ-37d.

### TSJ-38: Kotlin-parity reference application and readiness gate
- Why: The delivery goal is practical parity with Kotlin for building production-style Spring Boot services.
- Acceptance Criteria:
  - A non-trivial reference app (multi-module, DB-backed, secured API) is authored in TS and runs on Spring Boot.
  - Equivalent Kotlin reference app is maintained for parity comparison across tests, behavior, and performance budgets.
  - Readiness gate defines minimum pass criteria for correctness, startup time, throughput, and diagnostics quality.
  - Documentation provides migration guidance from Kotlin/Java Spring to TSJ authoring model.
- Notes:
  - This is the milestone story for claiming Spring Boot authoring viability from TypeScript.
  - TSJ-38 subset introduces parallel reference-app scaffolds:
    `examples/tsj38-kotlin-parity/ts-app` and
    `examples/tsj38-kotlin-parity/kotlin-app`.
  - TSJ-38 subset introduces readiness gate harness/tests in `compiler/backend-jvm`:
    `TsjKotlinParityReadinessGateHarness` and
    `TsjKotlinParityReadinessGateTest`.
  - Readiness report artifact path:
    `compiler/backend-jvm/target/tsj38-kotlin-parity-readiness.json`.
  - Gate criteria in subset:
    reference-app scaffold presence,
    web-module parity signal,
    unsupported-module gate diagnostics,
    benchmark baseline availability,
    migration-guide availability.
  - Migration guidance document:
    `docs/tsj-kotlin-migration-guide.md`.
  - Parent completion gate:
    TSJ-38 can move from `Complete (Subset)` to `Complete` only when TSJ-38a, TSJ-38b, and TSJ-38c
    are `Complete`, and this story no longer carries a remaining-gap note.
  - Completion gate is now satisfied:
    TSJ-38a, TSJ-38b, and TSJ-38c are complete and certification is active in CI.
- Status: `Complete`.
- Dependencies: TSJ-36, TSJ-37.

### TSJ-38a: DB-backed reference-app parity
- Why: Kotlin-parity claims require persistent data behavior, not only in-memory/service-level checks.
- Acceptance Criteria:
  - TS and Kotlin reference apps both run against the same real database backend for documented subset.
  - Schema, repository/query behavior, and transaction boundaries are validated through integration suites.
  - Differential checks assert parity for CRUD and representative query workflows.
  - Readiness report includes DB-backed parity signal and blocker details when failing.
- Notes:
  - Closes TSJ-38 DB-backed runtime parity gap.
  - Implemented TSJ-38a DB-backed parity harness/report coverage in backend-jvm tests:
    `TsjKotlinDbParityHarness`,
    `TsjKotlinDbParityReport`,
    and `TsjKotlinDbParityTest`.
  - Differential gate compares TS output against Java and Kotlin reference workflows for CRUD,
    representative query behavior, and transaction boundaries across two backend fixtures (`h2`, `hsqldb`).
  - DB-backed parity report artifact:
    `compiler/backend-jvm/target/tsj38a-db-parity-report.json`.
  - Failure diagnostics are explicitly separated:
    `TSJ-ORM-DB-WIRING` vs `TSJ-ORM-QUERY-FAILURE`.
  - Readiness harness now consumes TSJ-38a parity signal directly and tracks
    `security-reference-parity` as the remaining full-parity blocker.
  - CI now runs a dedicated TSJ-38a gate step and uploads the TSJ-38a report artifact.
  - Certified scope and execution details are documented in:
    `docs/tsj38a-db-parity.md`.
- Status: `Complete`.
- Dependencies: TSJ-37b, TSJ-38.

### TSJ-38b: Security-enabled reference-app parity
- Why: Production-style parity requires comparable authentication and authorization behavior.
- Acceptance Criteria:
  - TS and Kotlin reference apps implement documented security baseline subset end-to-end.
  - Differential tests validate authenticated/unauthenticated access, role-based access, and failure semantics.
  - Security diagnostics distinguish configuration failures from authorization failures.
  - Readiness report includes security parity signal with reproducible scenario outcomes.
- Notes:
  - Closes TSJ-38 security-enabled runtime parity gap.
  - Scope is baseline security parity; advanced identity-provider integrations remain explicit non-goals unless added.
  - Implemented security parity harness/report coverage:
    `TsjKotlinSecurityParityHarness`,
    `TsjKotlinSecurityParityReport`,
    and `TsjKotlinSecurityParityTest`.
  - Differential coverage validates authenticated and role-based success paths
    and failure semantics for unauthenticated/authz-denied scenarios.
  - Security diagnostics are validated as distinct families:
    `TSJ-SECURITY-AUTHN-FAILURE`, `TSJ-SECURITY-AUTHZ-FAILURE`,
    and `TSJ-SECURITY-CONFIG-FAILURE`.
  - Readiness gate now consumes TSJ-38b report output directly and publishes
    `security-reference-parity-signal` as a first-class criterion.
  - Report artifact:
    `compiler/backend-jvm/target/tsj38b-security-parity-report.json`.
  - CI now runs a dedicated TSJ-38b gate step and uploads the TSJ-38b report artifact.
  - Certified scope and non-goals are documented in:
    `docs/tsj38b-security-parity.md`.
- Status: `Complete`.
- Dependencies: TSJ-37d, TSJ-38a.

### TSJ-38c: Full readiness-gate criteria and parity certification
- Why: TSJ-38 cannot be marked complete until correctness, startup, throughput, and diagnostics gates are enforced.
- Acceptance Criteria:
  - Readiness gate enforces explicit thresholds for correctness, startup time, throughput, and diagnostics quality.
  - CI publishes a parity certification artifact with pass/fail by gate dimension and fixture/version metadata.
  - `fullParityReady` only flips to `true` when DB-backed and security-enabled parity gates pass.
  - TSJ-38 is promoted to `Complete` once TSJ-38a/38b/38c are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-38 from subset to complete.
  - Artifact format should stay aligned with existing conformance/readiness reports for TSJ-34/37/44.
  - Implemented full certification harness/report coverage:
    `TsjKotlinParityCertificationHarness`,
    `TsjKotlinParityCertificationReport`,
    and `TsjKotlinParityCertificationTest`.
  - Certification gate enforces explicit dimensions with thresholds:
    correctness, startup-time, throughput, and diagnostics-quality.
  - Full certification artifact path:
    `compiler/backend-jvm/target/tsj38c-kotlin-parity-certification.json`.
  - `fullParityReady` now derives from dimension thresholds plus DB/security parity signals.
  - CI now runs a dedicated TSJ-38c gate step and uploads the TSJ-38c report artifact.
  - Certified thresholds and artifact semantics are documented in:
    `docs/tsj38c-parity-certification.md`.
  - TSJ-38 parent story is promoted to `Complete` after TSJ-38c closure-gate activation.
- Status: `Complete`.
- Dependencies: TSJ-38a, TSJ-38b.

## Epic J: Broad JAR Compatibility Mode

### TSJ-39: JVM ABI and metadata completeness for library interoperability
- Why: Many jars depend on precise JVM metadata and reflection-visible signatures beyond baseline Spring support.
- Acceptance Criteria:
  - Generated classes expose required annotation, generic-signature, parameter, and method metadata for reflection-heavy libraries.
  - Compatibility tests validate metadata reads through core reflection APIs and framework introspection utilities.
  - Known metadata gaps are converted into explicit diagnostics with stable feature IDs.
  - Docs define metadata guarantees and non-goals for broad-jar mode.
- Notes:
  - Extends TSJ-32 parity from Spring-focused needs to general third-party library expectations.
  - TSJ-39 subset extends interop bridge signature emission to preserve parameterized
    generic metadata for Spring bean/web typed bridge methods when signatures are concrete.
  - New metadata-gap diagnostics:
    `TSJ-INTEROP-METADATA` with `featureId=TSJ39-ABI-METADATA`
    for bridged signatures that include unresolved generic type variables.
  - Compatibility tests now validate metadata via reflection on compiled generated bridges:
    generic return/parameter type names and parameter-name retention.
  - Documentation now defines metadata guarantees/non-goals for broad-jar mode in:
    `docs/interop-metadata-guarantees.md`.
  - Parent completion gate:
    TSJ-39 can move from `Complete (Subset)` to `Complete` only when TSJ-39a, TSJ-39b, and TSJ-39c
    are `Complete`, and this story no longer carries a remaining-gap note.
  - Completion gate is now satisfied:
    TSJ-39a, TSJ-39b, and TSJ-39c are complete and metadata certification gate is active in CI.
- Status: `Complete`.
- Dependencies: TSJ-32, TSJ-38.

### TSJ-39a: Universal generated-class metadata parity
- Why: Broad interoperability needs metadata guarantees beyond bridge-only generated paths.
- Acceptance Criteria:
  - Metadata emission covers documented generated-class families:
    program classes, bridge classes, component/web adapters, and proxy artifacts.
  - Required annotation/signature/parameter/method metadata is preserved consistently
    across all supported generated-class paths.
  - Missing or unsupported metadata shapes emit explicit diagnostics with stable feature IDs.
  - Reflection-based tests validate metadata parity on each generated-class family.
- Notes:
  - Closes TSJ-39 gap around bridge-only metadata coverage.
  - Metadata guarantees documentation must be updated to reflect expanded certified scope.
- Status: `Complete`.
- Dependencies: TSJ-39.

### TSJ-39b: Third-party introspector compatibility matrix
- Why: Metadata parity is meaningful only if framework/library introspectors consume it correctly.
- Acceptance Criteria:
  - Compatibility matrix validates metadata reads through representative third-party introspectors
    in documented subset categories.
  - Matrix includes fixture-driven pass/fail results by library + version + introspection scenario.
  - Unsupported introspector scenarios emit explicit diagnostics with actionable fallback guidance.
  - CI publishes introspector compatibility artifact for regression tracking.
- Notes:
  - Closes TSJ-39 gap around third-party introspector breadth.
  - Matrix should align with TSJ-44 certification reporting style for reproducibility.
  - Implemented by `TsjIntrospectorCompatibilityMatrixHarness` +
    `TsjIntrospectorCompatibilityMatrixTest` with fixture-driven scenarios in
    `tests/introspector-matrix/`.
  - CI/module artifact path:
    `compiler/backend-jvm/target/tsj39b-introspector-matrix.json`.
  - Supported scenarios include reflection generic-signature reads and spring-web mapping introspection;
    unsupported scenario emits stable `TSJ39B-INTROSPECTOR-UNSUPPORTED` guidance.
- Status: `Complete`.
- Dependencies: TSJ-39a, TSJ-44.

### TSJ-39c: Metadata parity gate and certification closure
- Why: TSJ-39 requires an explicit gate before full-complete status can be claimed.
- Acceptance Criteria:
  - CI gate enforces metadata parity thresholds across generated-class families and introspector matrix scenarios.
  - Certification artifact publishes pass/fail by metadata dimension, class family, and introspector scenario.
  - Docs clearly distinguish certified metadata guarantees from best-effort areas.
  - TSJ-39 is promoted to `Complete` once TSJ-39a/39b/39c are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-39 from subset to complete.
  - Artifact/report format should remain consistent with existing TSJ readiness/conformance reports.
  - Implemented certification harness:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjMetadataParityCertificationHarness.java`.
  - Report model + CI artifact output:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjMetadataParityCertificationReport.java`,
    `compiler/backend-jvm/target/tsj39c-metadata-parity-certification.json`.
  - Certification gate combines:
    generated-class family checks (`program`, `component`, `proxy`, `web-controller`, `interop-bridge`) and
    TSJ-39b introspector matrix scenarios with supported-threshold + unsupported-diagnostic stability checks.
  - Gate tests:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjMetadataParityCertificationTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-39a, TSJ-39b.

### TSJ-40: Classpath and dependency mediation parity
- Why: “Add any jar” requires deterministic dependency resolution, conflict handling, and classloading behavior.
- Acceptance Criteria:
  - CLI/build supports ordered external classpath inputs with reproducible artifact metadata.
  - Duplicate/conflicting dependency versions emit deterministic diagnostics and guidance.
  - Runtime classloader model supports transitive jars and isolation boundaries for TSJ app classes vs external libraries.
  - Integration tests cover real multi-jar dependency graphs with version conflict scenarios.
- Notes:
  - This story is about deterministic loading behavior, not yet claiming universal framework compatibility.
  - TSJ-40 introduces deterministic classpath version-conflict diagnostics in CLI option normalization:
    `TSJ-CLASSPATH-CONFLICT`.
  - Conflict detection currently targets jar-name version collisions
    (same artifact stem with different detected version suffixes, e.g. `foo-1.0.jar` vs `foo-2.0.jar`).
  - Multi-jar dependency graph integration coverage is added in `TsjCliTest`
    (`runSupportsMultiJarDependencyGraphClasspath`) to validate external-jar linkage across multiple jars.
  - Additional conflict coverage is added in `TsjCliTest`
    (`compileRejectsConflictingJarVersionsInClasspath`).
  - Existing artifact metadata continues to persist ordered classpath entries as
    `interopClasspath.<index>` for reproducible compile/run behavior.
  - TSJ-40d adds closure-gate certification harness/report coverage for mediation graph,
    scope filtering, and isolation diagnostics:
    `cli/src/test/java/dev/tsj/cli/TsjDependencyMediationCertificationHarness.java`,
    `cli/src/test/java/dev/tsj/cli/TsjDependencyMediationCertificationReport.java`,
    and `cli/target/tsj40d-dependency-mediation-certification.json`.
- Status: `Complete`.
- Dependencies: TSJ-27, TSJ-36.

### TSJ-40a: Maven/Gradle-style dependency mediation graph parity
- Why: Real-world jar ecosystems depend on transitive graph mediation, not flat classpath lists.
- Acceptance Criteria:
  - Dependency graph resolution supports documented Maven/Gradle-like mediation subset
    for transitive dependency graphs.
  - Version-conflict mediation applies deterministic, documented precedence rules.
  - Reproducible fixture graphs validate resolution outcomes and conflict diagnostics.
  - Artifact metadata captures resolved graph/version decisions for deterministic replay.
- Notes:
  - Closes TSJ-40 gap around full dependency mediation graphs.
  - Diagnostics should preserve stable conflict identifiers and actionable guidance.
  - Implemented mediation subset:
    source-root graph traversal from provided classpath jars with Maven `pom.properties`/`pom.xml` metadata,
    deterministic winner selection (`nearest` depth, then `root-order`, then deterministic discovery tie-break),
    and artifact metadata persistence of mediation decisions under
    `interopClasspath.mediation.*`.
  - Coverage is added in `TsjCliTest`:
    `runMediatesTransitiveDependencyGraphUsingNearestRule` and
    `runMediatesSameDepthConflictsUsingRootOrderTiebreak`.
  - Existing deterministic conflict diagnostic (`TSJ-CLASSPATH-CONFLICT`) remains for non-Maven
    jar-name collision scenarios.
- Status: `Complete`.
- Dependencies: TSJ-40.

### TSJ-40b: Scope-aware dependency resolution parity
- Why: Compile/runtime behavior diverges without scope-aware classpath construction.
- Acceptance Criteria:
  - Resolution honors documented scope subset (for example compile/runtime/test/provided) across compile/package/run paths.
  - Scope-driven classpath materialization is deterministic and persisted in artifact metadata.
  - Mis-scoped dependency usage emits explicit diagnostics with resolution context.
  - Integration tests validate scope semantics across representative multi-module dependency graphs.
- Notes:
  - Closes TSJ-40 gap around scope-aware resolution behavior.
  - Scope contract should be documented with explicit supported/unsupported scope combinations.
  - Implemented scope subset in CLI mediation:
    compile path allows `compile,runtime,provided`;
    run/spring-package path allows `compile,runtime`.
  - Scope filtering decisions are persisted in artifact metadata under:
    `interopClasspath.scope.*`.
  - Mis-scoped interop target usage now emits explicit diagnostic:
    `TSJ-CLASSPATH-SCOPE`.
  - Coverage added in `TsjCliTest`:
    `compileIncludesProvidedScopeDependenciesForInteropResolution`,
    `runRejectsInteropTargetsAvailableOnlyViaProvidedScope`,
    `runPersistsScopeFilteringMetadataForTestScopedDependencies`.
- Status: `Complete`.
- Dependencies: TSJ-40a.

### TSJ-40c: Classloader isolation and boundary diagnostics
- Why: Broad interop needs predictable isolation between TSJ app classes and external libraries.
- Acceptance Criteria:
  - Runtime supports documented classloader isolation modes for app classes vs external dependencies.
  - Isolation policy is deterministic and configurable within supported subset.
  - Classloading boundary violations/conflicts emit explicit diagnostics with loader context.
  - Integration tests validate isolation behavior for conflicting transitive graph scenarios.
- Notes:
  - Closes TSJ-40 gap around classloader isolation parity.
  - Must preserve backward compatibility defaults while enabling explicit isolation modes.
  - Implemented CLI/runtime isolation mode subset:
    `--classloader-isolation shared|app-isolated` with default `shared`.
  - App-isolated mode now performs deterministic conflict detection for duplicate classes
    across generated app output and dependency classpath entries.
  - Boundary diagnostics are explicit and stable:
    `TSJ-RUN-009` (isolation conflict) and `TSJ-RUN-010` (dependency-loader boundary miss).
  - Coverage added in `TsjCliTest`:
    `runSupportsAppIsolatedClassloaderModeForInteropDependencies`,
    `runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode`,
    and `runRejectsUnknownClassloaderIsolationMode`.
- Status: `Complete`.
- Dependencies: TSJ-40a, TSJ-40b.

### TSJ-40d: Dependency mediation parity gate and certification closure
- Why: TSJ-40 requires a hard conformance gate before full parity can be claimed.
- Acceptance Criteria:
  - CI gate enforces pass criteria across mediation-graph, scope-resolution, and isolation scenarios.
  - Certification artifact reports pass/fail per graph fixture, scope path, and isolation mode.
  - Docs distinguish certified mediation behavior from best-effort paths outside supported subset.
  - TSJ-40 is promoted to `Complete` once TSJ-40a/40b/40c/40d are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-40 from subset to complete.
  - Implemented closure-gate harness/tests:
    `cli/src/test/java/dev/tsj/cli/TsjDependencyMediationCertificationHarness.java`,
    `cli/src/test/java/dev/tsj/cli/TsjDependencyMediationCertificationReport.java`,
    and `cli/src/test/java/dev/tsj/cli/TsjDependencyMediationCertificationTest.java`.
  - Certification artifact path:
    `cli/target/tsj40d-dependency-mediation-certification.json`.
  - CI now runs a dedicated TSJ-40d certification step and uploads the TSJ-40d report artifact.
- Status: `Complete`.
- Dependencies: TSJ-40a, TSJ-40b, TSJ-40c.

### TSJ-41: Advanced invocation and conversion parity for arbitrary APIs
- Why: Arbitrary jars use overloads, varargs, nested generics, and complex object graphs.
- Acceptance Criteria:
  - Interop invocation supports constructors, instance/static members, varargs, and overload ranking rules for documented parity set.
  - Codec supports arrays, collections, maps, enums, optional-like wrappers, and nested conversion graphs with clear nullability rules.
  - Conversion/invocation mismatch diagnostics include candidate signatures and conversion-failure details.
  - Runtime and integration tests validate representative APIs from multiple Java ecosystems.
- Notes:
  - Builds on TSJ-29/TSJ-30 and hardens behavior for broad library surfaces.
  - TSJ-41 subset extends conversion coverage for nested object graphs and broader Java target types:
    maps/lists/sets, arrays, enums, `Optional`, futures, and functional-interface boundaries.
  - Interop invocation ranking now prioritizes stronger typed candidates over generic `Object` fallback
    in documented subset scenarios.
  - Conversion/invocation mismatch diagnostics now include concrete conversion-failure details
    with candidate signature context.
  - Coverage is enforced by runtime + CLI tests:
    `TsjInteropCodecTest` and
    `TsjCliTest#runSupportsTsj41AdvancedInteropConversionAndInvocationParity`.
  - TSJ-41d adds closure-gate certification harness/report coverage across
    numeric widening, generic adaptation, and reflective-edge diagnostics:
    `cli/src/test/java/dev/tsj/cli/TsjInvocationConversionCertificationHarness.java`,
    `cli/src/test/java/dev/tsj/cli/TsjInvocationConversionCertificationReport.java`,
    and `cli/target/tsj41d-invocation-conversion-certification.json`.
- Status: `Complete`.
- Dependencies: TSJ-29, TSJ-30, TSJ-40.

### TSJ-41a: Numeric widening and primitive/wrapper overload parity
- Why: Overload determinism requires full numeric conversion rules, not subset scoring heuristics.
- Acceptance Criteria:
  - Interop overload resolution supports documented Java numeric widening lattice
    across primitive and wrapper combinations.
  - Conversion/scoring behavior is deterministic for ambiguous numeric candidate sets.
  - Diagnostics include explicit numeric-conversion reasoning when overload selection fails.
  - Runtime and integration tests cover representative numeric-overload ecosystems.
- Notes:
  - Closes TSJ-41 numeric widening parity gap.
  - Should preserve backward-compatible behavior for already-supported non-numeric overload paths.
  - Interop numeric resolution now uses deterministic widening ranking for primitive/wrapper targets:
    widening distance score plus stable primitive-vs-wrapper tie-break behavior.
  - Numeric narrowing candidates are rejected in overload matching with explicit
    numeric-conversion failure details.
  - Runtime and CLI coverage is added in:
    `TsjInteropCodecTest#invokeBindingSupportsTsj41aNumericWideningAndPrimitiveWrapperParity`,
    `TsjInteropCodecTest#invokeBindingReportsNumericConversionReasonForRejectedNarrowing`,
    `TsjCliTest#runSupportsTsj41aNumericWideningAndPrimitiveWrapperParity`, and
    `TsjCliTest#runReportsTsj41aNumericConversionReasonForRejectedNarrowing`.
- Status: `Complete`.
- Dependencies: TSJ-41.

### TSJ-41b: Generic-type adaptation parity for interop conversion
- Why: Broad API compatibility depends on generic container/type adaptation beyond concrete subset paths.
- Acceptance Criteria:
  - Conversion layer supports documented generic-type adaptation subset
    for nested collections/maps/optionals and representative generic signatures.
  - Generic adaptation failures emit explicit diagnostics with target-type context.
  - Integration tests validate conversion parity across representative generic API surfaces.
  - Docs define supported generic adaptation guarantees and non-goals.
- Notes:
  - Closes TSJ-41 universal generic-type adaptation gap.
  - Must align with TSJ-39 metadata guarantees where reflective generic info is required.
  - Runtime conversion now accepts reflective `Type` targets and recursively adapts nested
    generic containers (`List`, `Set`, `Map`, `Optional`, arrays, and `CompletableFuture`)
    with target-type-aware element/key/value conversion.
  - Generic adaptation failures now include explicit target-type context using
    `Generic interop conversion failed ... to <type>`.
  - Runtime + CLI parity coverage added in:
    `TsjInteropCodecTest#invokeBindingSupportsTsj41bGenericNestedCollectionAndOptionalAdaptation`,
    `TsjInteropCodecTest#invokeBindingSupportsTsj41bGenericMapKeyAndValueAdaptation`,
    `TsjInteropCodecTest#invokeBindingReportsTsj41bGenericTargetContextOnAdaptationFailure`,
    `TsjCliTest#runSupportsTsj41bGenericTypeAdaptationParity`, and
    `TsjCliTest#runReportsTsj41bGenericAdaptationFailureWithTargetTypeContext`.
  - Supported guarantees and non-goals are documented in:
    `docs/interop-generic-adaptation.md`.
- Status: `Complete`.
- Dependencies: TSJ-39a, TSJ-41a.

### TSJ-41c: Reflective edge-case invocation parity
- Why: Many mature libraries rely on reflective invocation edge cases not covered by baseline interop paths.
- Acceptance Criteria:
  - Interop invocation supports documented reflective edge-case subset
    (for example bridge/default methods, accessibility nuances, and reflective dispatch constraints).
  - Failure modes for unsupported reflective patterns emit targeted diagnostics with candidate context.
  - Integration tests validate parity for representative reflective-heavy libraries.
  - Operational docs capture reflective compatibility boundaries and fallback guidance.
- Notes:
  - Closes TSJ-41 reflective edge-case parity gap.
  - Security/policy controls from TSJ-43 must remain effective for reflective paths.
  - Reflective invocation now filters synthetic/bridge methods when non-bridge candidates exist,
    keeping candidate selection and diagnostics deterministic for generic override scenarios.
  - Default-interface method dispatch is validated for instance bindings through runtime and CLI paths.
  - Unsupported non-public reflective access now emits targeted diagnostics with explicit context:
    `TSJ-INTEROP-REFLECTIVE`.
  - Coverage added in:
    `TsjInteropCodecTest#invokeBindingSupportsTsj41cDefaultInterfaceMethodDispatch`,
    `TsjInteropCodecTest#invokeBindingPrefersTsj41cConcreteMethodOverBridgeCandidates`,
    `TsjInteropCodecTest#invokeBindingReportsTsj41cDiagnosticForNonPublicReflectiveMethod`,
    `TsjCliTest#runSupportsTsj41cReflectiveDefaultMethodAndBridgeDispatchParity`, and
    `TsjCliTest#runReportsTsj41cReflectiveDiagnosticForNonPublicMethodAccess`.
  - Reflective compatibility boundaries and fallback guidance are documented in:
    `docs/interop-reflective-compatibility.md`.
- Status: `Complete`.
- Dependencies: TSJ-41a, TSJ-43.

### TSJ-41d: Invocation/conversion parity gate and certification closure
- Why: TSJ-41 requires a hard conformance gate before broad invocation parity can be claimed.
- Acceptance Criteria:
  - CI gate enforces pass criteria across numeric widening, generic adaptation, and reflective edge-case suites.
  - Certification artifact reports pass/fail by scenario family and fixture/version metadata.
  - Docs clearly distinguish certified invocation/conversion behavior from best-effort paths.
  - TSJ-41 is promoted to `Complete` once TSJ-41a/41b/41c/41d are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-41 from subset to complete.
  - Implemented closure-gate harness/tests:
    `cli/src/test/java/dev/tsj/cli/TsjInvocationConversionCertificationHarness.java`,
    `cli/src/test/java/dev/tsj/cli/TsjInvocationConversionCertificationReport.java`,
    and `cli/src/test/java/dev/tsj/cli/TsjInvocationConversionCertificationTest.java`.
  - Certification artifact path:
    `cli/target/tsj41d-invocation-conversion-certification.json`.
  - Certified scope and best-effort boundaries are documented in:
    `docs/interop-invocation-certification.md`.
  - CI now runs a dedicated TSJ-41d certification step and uploads the TSJ-41d report artifact.
- Status: `Complete`.
- Dependencies: TSJ-41a, TSJ-41b, TSJ-41c.

### TSJ-42: Hibernate/JPA compatibility pack
- Why: ORM stacks are a high-value and high-complexity target for “any jar” credibility.
- Acceptance Criteria:
  - TS-authored entities/repositories/services can run with supported Hibernate/JPA versions in documented subset.
  - Proxy/lazy-loading and lifecycle behaviors are validated for supported entity patterns.
  - Transaction, persistence-context, and query flows pass integration suites against real databases.
  - Unsupported ORM patterns fail with explicit diagnostics and documented fallback guidance.
- Notes:
  - This story creates a framework-specific compatibility contract rather than assuming generic interop is sufficient.
  - TSJ-42 subset adds ORM-oriented compatibility checks through representative JPA-style fixtures and
    CLI integration tests covering entity/repository/service-like invocation flows.
  - Runtime conversion support now includes additional collection targets (`Set`) and nested graph conversion
    paths frequently exercised by ORM-adjacent APIs.
  - Unsupported pattern diagnostics are covered in CLI tests
    (`runSurfacesTsj42UnsupportedJpaPatternDiagnosticMessage`) with actionable failure messaging.
  - Parent completion gate:
    TSJ-42 can move from `Complete (Subset)` to `Complete` only when TSJ-42a through TSJ-42d
    are `Complete`, and this story no longer carries a remaining-gap note.
  - Completion gate is now satisfied:
    TSJ-42a, TSJ-42b, TSJ-42c, and TSJ-42d are complete and the closure gate is active in CI.
- Status: `Complete`.
- Dependencies: TSJ-35, TSJ-37, TSJ-41.

### TSJ-42a: Real-database Hibernate/JPA integration parity
- Why: ORM compatibility claims require real DB-backed behavior, not synthetic-only fixtures.
- Acceptance Criteria:
  - Integration suites execute against documented real database backends for supported Hibernate/JPA versions.
  - Entity/repository/service flows validate CRUD and representative query paths with deterministic fixtures.
  - Runtime diagnostics clearly separate DB wiring/configuration failures from ORM mapping/query failures.
  - Compatibility report includes DB backend + ORM version metadata for reproducible results.
- Notes:
  - Closes TSJ-42 gap around real database integration.
  - Implemented reproducible DB-backed parity harness/report coverage:
    `TsjJpaRealDatabaseParityHarness`,
    `TsjJpaRealDatabaseParityReport`,
    and `TsjJpaRealDatabaseParityTest`.
  - Gate validates CRUD + representative query parity across two backend fixtures (`h2`, `hsqldb`)
    with explicit backend + ORM version metadata in the report.
  - Runtime diagnostics are split into distinct scenario checks:
    `TSJ-ORM-DB-WIRING` and `TSJ-ORM-QUERY-FAILURE`.
  - Report artifact:
    `cli/target/tsj42a-jpa-realdb-parity.json`.
  - CI now runs a dedicated TSJ-42a gate step and uploads the TSJ-42a report artifact.
  - Certified scope and execution details are documented in:
    `docs/hibernate-jpa-realdb-parity.md`.
- Status: `Complete`.
- Dependencies: TSJ-37b, TSJ-42.

### TSJ-42b: Proxy and lazy-loading behavior parity
- Why: Real-world JPA usage depends on proxy initialization and lazy relation semantics.
- Acceptance Criteria:
  - Supported proxy/lazy-loading entity patterns behave consistently under documented subset constraints.
  - Integration tests validate initialization boundaries, lazy fetch behavior, and failure semantics.
  - Unsupported lazy/proxy patterns emit explicit diagnostics with entity/association context.
  - Compatibility docs define certified lazy/proxy subset and non-goals.
- Notes:
  - Closes TSJ-42 gap around proxy/lazy-loading internals.
  - Should align with TSJ-35 proxy behavior expectations where transaction/AOP boundaries interact.
  - Implemented lazy/proxy parity harness/report coverage:
    `TsjJpaLazyProxyParityHarness`,
    `TsjJpaLazyProxyParityReport`,
    and `TsjJpaLazyProxyParityTest`.
  - Supported subset scenarios validate lazy initialization transitions and repeated lazy-read boundaries.
  - Unsupported-pattern diagnostics are validated with explicit expected/observed code checks:
    `TSJ-JPA-LAZY-UNSUPPORTED` and `TSJ-JPA-PROXY-UNSUPPORTED`,
    including association context hints.
  - Report artifact:
    `cli/target/tsj42b-jpa-lazy-proxy-parity.json`.
  - CI now runs a dedicated TSJ-42b gate step and uploads the TSJ-42b report artifact.
  - Certified scope and non-goals are documented in:
    `docs/hibernate-jpa-lazy-proxy-parity.md`.
- Status: `Complete`.
- Dependencies: TSJ-42a.

### TSJ-42c: Persistence-context lifecycle and transaction semantics parity
- Why: ORM correctness requires lifecycle/event and persistence-context behavior beyond basic CRUD.
- Acceptance Criteria:
  - Supported persistence-context lifecycle semantics are validated:
    flush/clear/detach/merge subset and transactional boundary interactions.
  - Integration suites validate lifecycle callbacks and transaction-coupled ORM behavior.
  - Failure diagnostics distinguish persistence-context misuse from transaction and mapping failures.
  - Docs define certified lifecycle semantics and explicit out-of-scope behavior.
- Notes:
  - Closes TSJ-42 gap around persistence-context lifecycle completeness.
  - Should remain compatible with TSJ-35 transactional parity and TSJ-37 module-matrix expectations.
  - Implemented lifecycle/transaction parity harness/report coverage:
    `TsjJpaLifecycleParityHarness`,
    `TsjJpaLifecycleParityReport`,
    and `TsjJpaLifecycleParityTest`.
  - Supported subset scenarios validate lifecycle operations (`flush`/`clear`/`detach`/`merge`)
    and transaction-boundary rollback semantics with TS/Java/Kotlin differential parity checks.
  - Failure diagnostics are validated as distinct scenario families:
    `TSJ-ORM-LIFECYCLE-MISUSE`, `TSJ-ORM-TRANSACTION-REQUIRED`, and `TSJ-ORM-MAPPING-FAILURE`.
  - Report artifact:
    `cli/target/tsj42c-jpa-lifecycle-parity.json`.
  - CI now runs a dedicated TSJ-42c gate step and uploads the TSJ-42c report artifact.
  - Certified scope and non-goals are documented in:
    `docs/hibernate-jpa-lifecycle-parity.md`.
- Status: `Complete`.
- Dependencies: TSJ-35b, TSJ-42a.

### TSJ-42d: Hibernate/JPA compatibility gate and certification closure
- Why: TSJ-42 requires a hard conformance gate before full ORM compatibility can be claimed.
- Acceptance Criteria:
  - CI gate enforces pass criteria across real-DB, lazy/proxy, and persistence-context lifecycle suites.
  - Certification artifact reports pass/fail by ORM version, DB backend, and scenario family.
  - Release docs distinguish certified ORM compatibility from best-effort behavior outside tested matrix.
  - TSJ-42 is promoted to `Complete` once TSJ-42a/42b/42c/42d are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-42 from subset to complete.
  - Report format should align with existing TSJ conformance/readiness artifacts.
  - Implemented closure-gate harness/report coverage:
    `TsjJpaCertificationClosureHarness`,
    `TsjJpaCertificationClosureReport`,
    and `TsjJpaCertificationClosureTest`.
  - CI gate now enforces pass criteria across TSJ-42a/42b/42c family suites
    and publishes family-level pass/fail rows keyed by ORM version, DB backend, and scenario id.
  - Closure report artifact:
    `cli/target/tsj42d-jpa-certification.json`.
  - Certified-vs-best-effort ORM release guidance is documented in:
    `docs/hibernate-jpa-certification.md`.
  - TSJ-42 parent story is promoted to `Complete` after TSJ-42d closure-gate activation.
- Status: `Complete`.
- Dependencies: TSJ-42a, TSJ-42b, TSJ-42c.

### TSJ-43: Broad mode policy, security, and operational guardrails
- Why: Reflection-open arbitrary classpath access introduces safety and operability risks.
- Acceptance Criteria:
  - Policy modes are implemented:
    strict allowlist (default) and opt-in broad classpath mode with explicit risk acknowledgment.
  - Security controls include package/class denylist hooks, audit logging, and runtime interop trace switches.
  - Operational docs cover debugging, failure triage, and safe rollout strategy for broad mode.
  - CI includes security and policy regression checks for both modes.
- Notes:
  - Keeps conservative defaults while enabling expert opt-in paths for advanced use cases.
  - Broad interop mode now requires explicit operator acknowledgment via
    `--ack-interop-risk` (`TSJ-INTEROP-RISK` diagnostic if missing).
  - Security/operational controls are implemented:
    `--interop-denylist`, `--interop-audit-log`, and `--interop-trace`.
  - Runtime interop tracing is wired through `TsjJavaInterop` and can be enabled per run/package command.
  - Regression coverage is implemented in `TsjCliTest` for risk-ack enforcement, denylist blocking,
    audit-log emission, and runtime trace behavior.
  - Operational guardrail documentation is captured in `docs/interop-policy.md`.
  - Parent completion gate:
    TSJ-43 can move from `Complete (Subset)` to `Complete` only when TSJ-43a through TSJ-43d
    are `Complete`, and this story no longer carries a remaining-gap note.
  - TSJ-43d adds closure-gate certification harness/report coverage across
    fleet-policy precedence/conflict behavior, centralized-audit paths, and RBAC/approval enforcement:
    `cli/src/test/java/dev/tsj/cli/TsjGuardrailCertificationHarness.java`,
    `cli/src/test/java/dev/tsj/cli/TsjGuardrailCertificationReport.java`,
    and `cli/target/tsj43d-guardrail-certification.json`.
- Status: `Complete`.
- Dependencies: TSJ-31, TSJ-40, TSJ-41.

### TSJ-43a: Fleet-level policy management and distribution
- Why: Organization-wide rollout needs centrally managed policy, not per-command flags only.
- Acceptance Criteria:
  - Runtime/CLI policy can be sourced from centrally managed configuration in documented subset.
  - Policy precedence and override rules are deterministic and documented (global, project, command-level).
  - Policy drift/conflict conditions emit explicit diagnostics with resolution guidance.
  - Integration tests validate fleet-policy application across representative workflows.
- Notes:
  - Closes TSJ-43 gap around fleet-level policy management.
  - Must preserve secure defaults (`strict`) when policy sources are missing or invalid.
  - CLI now supports deterministic fleet-policy precedence:
    default -> global (`TSJ_INTEROP_GLOBAL_POLICY` / `tsj.interop.globalPolicy`) -> project
    (`.tsj/interop-policy.properties`) -> explicit command flags.
  - Conflicting global/project policy sources without explicit command override fail fast with
    `TSJ-INTEROP-POLICY-CONFLICT` and resolution guidance.
  - Coverage includes precedence, command-override, and conflict cases in
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-43.

### TSJ-43b: Centralized audit aggregation and operational reporting
- Why: Per-run local audit logs are insufficient for enterprise incident response and compliance.
- Acceptance Criteria:
  - Audit events support centralized aggregation transport in documented subset.
  - Aggregated events include stable schema fields for policy decision, target, execution context, and outcome.
  - Failure modes (buffering/backpressure/destination unavailable) are diagnosable and bounded.
  - CI/integration tests validate event schema stability and aggregator compatibility.
- Notes:
  - Closes TSJ-43 gap around centralized audit aggregation.
  - Should preserve local audit fallback behavior when centralized sink is unavailable.
  - CLI now supports centralized audit sink emission via:
    `--interop-audit-aggregate <path>`.
  - Aggregated event schema (`tsj.interop.audit.v1`) includes stable fields for
    decision, target, execution context (`command`, `entry`, `policy`), and outcome.
  - Event emission is bounded per run via deterministic cap (`MAX_AGGREGATE_AUDIT_EVENTS`);
    truncation metadata is persisted per event (`truncatedCount`).
  - Sink-unavailable behavior is diagnosable and bounded:
    local audit fallback emits `TSJ-INTEROP-AUDIT-AGGREGATE` records when aggregate write fails.
  - Coverage added in:
    `TsjCliTest#runWritesTsj43bCentralizedAuditAggregateEventsWhenConfigured` and
    `TsjCliTest#runFallsBackToLocalAuditWhenTsj43bAggregateSinkIsUnavailable`.
  - Operational schema/transport guidance is documented in:
    `docs/interop-audit-aggregation.md`.
- Status: `Complete`.
- Dependencies: TSJ-43a.

### TSJ-43c: Enterprise RBAC and approval-path integration
- Why: Broad-mode controls require identity-aware authorization in multi-user environments.
- Acceptance Criteria:
  - Policy decisions support enterprise RBAC integration for documented roles and permission scopes.
  - Sensitive interop operations can require explicit approval workflow in supported subset.
  - Authorization failures emit clear diagnostics with role/scope context.
  - Integration tests validate allow/deny/approval flows across representative role scenarios.
- Notes:
  - Closes TSJ-43 gap around enterprise RBAC integration.
  - Scope is baseline RBAC/approval parity; advanced IAM provider features remain out of scope unless added.
  - CLI/runtime now support RBAC/approval controls in broad-mode paths:
    `--interop-role <roles>` and `--interop-approval <token>`.
  - Fleet policy keys are integrated for deterministic authorization policy source precedence:
    `interop.rbac.roles`,
    `interop.rbac.requiredRoles`,
    `interop.rbac.sensitiveTargets`,
    `interop.rbac.sensitiveRequiredRoles`,
    `interop.approval.required`,
    `interop.approval.token`,
    and `interop.approval.targets`.
  - Authorization diagnostics are explicit with role/scope context:
    `TSJ-INTEROP-RBAC` and `TSJ-INTEROP-APPROVAL`.
  - Integration coverage added in `TsjCliTest` for allow/deny/approval flows:
    `runRejectsBroadInteropWhenTsj43cRequiredRoleIsMissing`,
    `runAllowsBroadInteropWhenTsj43cRequiredRoleIsProvided`,
    `runRequiresTsj43cApprovalTokenForSensitiveInteropTargets`,
    `runAllowsSensitiveInteropWhenTsj43cApprovalTokenIsProvided`,
    and `runUsesTsj43cFleetRolesWhenCommandRoleFlagsAreOmitted`.
- Status: `Complete`.
- Dependencies: TSJ-43a, TSJ-43b.

### TSJ-43d: Guardrail parity gate and operational certification closure
- Why: TSJ-43 requires a hard gate before full enterprise guardrail claims can be made.
- Acceptance Criteria:
  - CI gate enforces pass criteria across fleet-policy, centralized-audit, and RBAC/approval suites.
  - Certification artifact reports pass/fail by guardrail dimension and scenario metadata.
  - Docs distinguish certified guardrail behavior from best-effort fallback paths.
  - TSJ-43 is promoted to `Complete` once TSJ-43a/43b/43c/43d are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-43 from subset to complete.
  - Implemented closure-gate harness/tests:
    `cli/src/test/java/dev/tsj/cli/TsjGuardrailCertificationHarness.java`,
    `cli/src/test/java/dev/tsj/cli/TsjGuardrailCertificationReport.java`,
    and `cli/src/test/java/dev/tsj/cli/TsjGuardrailCertificationTest.java`.
  - Certification artifact path:
    `cli/target/tsj43d-guardrail-certification.json`.
  - Certified scope and best-effort fallback boundaries are documented in:
    `docs/interop-policy.md` (TSJ-43d certification section).
  - CI now runs a dedicated TSJ-43d certification step and uploads the TSJ-43d report artifact.
- Status: `Complete`.
- Dependencies: TSJ-43a, TSJ-43b, TSJ-43c.

### TSJ-44: Any-JAR readiness gate and compatibility certification
- Why: “Use any jar at will” requires measurable readiness criteria and explicit support boundaries.
- Acceptance Criteria:
  - Certification suite spans diverse jar categories:
    ORM, HTTP clients, serialization, validation, caching, messaging, and utility libraries.
  - Compatibility report publishes pass/fail by library and version with reproducible fixtures.
  - Readiness gate defines minimum coverage %, reliability thresholds, and performance budgets for broad mode claims.
  - Release docs distinguish certified compatibility from best-effort behavior outside tested matrix.
- Notes:
  - This is the final milestone for broad-jar interoperability claims.
  - TSJ-44 subset adds certification harness coverage in
    `TsjAnyJarCertificationTest` spanning seven categories:
    ORM, HTTP client, serialization, validation, caching, messaging, and utility.
  - The suite emits a machine-readable compatibility report:
    `cli/target/tsj44-anyjar-certification-report.json`,
    including pass/fail by check, library, and version.
  - Readiness thresholds are enforced in-suite:
    minimum coverage percent plus average/max duration budgets.
  - Release-facing support boundary guidance is documented in
    `docs/anyjar-certification.md` with explicit certified vs best-effort scope.
  - Parent completion gate:
    TSJ-44 can move from `Complete (Subset)` to `Complete` only when TSJ-44a through TSJ-44d
    are `Complete`, and this story no longer carries a remaining-gap note.
  - Completion gate is now satisfied:
    TSJ-44a, TSJ-44b, TSJ-44c, and TSJ-44d are complete and any-jar governance signoff
    is active in CI.
- Status: `Complete`.
- Dependencies: TSJ-39, TSJ-40, TSJ-41, TSJ-42, TSJ-43.

### TSJ-44a: Ecosystem matrix expansion with real third-party libraries
- Why: Certification credibility requires real ecosystem libraries beyond synthetic harness artifacts.
- Acceptance Criteria:
  - Certification matrix includes curated real libraries per category with documented version baselines.
  - Fixtures are reproducible and capture pass/fail by library, version, and scenario.
  - Unsupported/partial libraries emit explicit diagnostics and fallback guidance.
  - CI publishes expanded certification artifact with real-library scenario coverage.
- Notes:
  - Closes TSJ-44 gap around synthetic-only/representative-only certification coverage.
  - Library selection should prioritize high-adoption, high-impact ecosystems.
  - `TsjAnyJarCertificationTest` now includes a real-library matrix test that executes TSJ interop
    checks against cached third-party jars with explicit baseline versions:
    Flyway, PostgreSQL JDBC, Jackson Databind, SnakeYAML, HikariCP, Guava, and Commons Lang.
  - Expanded matrix output is persisted at:
    `cli/target/tsj44a-real-library-matrix-report.json`.
  - Report captures pass/fail by check, library, version, and diagnostic details for failed scenarios.
- Status: `Complete`.
- Dependencies: TSJ-42, TSJ-44.

### TSJ-44b: Version-range certification and compatibility drift tracking
- Why: “Any jar” readiness requires version-aware compatibility, not single-point version claims.
- Acceptance Criteria:
  - Certification validates documented version ranges for selected libraries and records compatibility outcomes.
  - CI detects compatibility drift regressions across supported version sets.
  - Reports include per-version pass/fail and first-failing-version metadata.
  - Release docs clearly declare certified version ranges and upgrade guidance.
- Notes:
  - Closes TSJ-44 gap around version-wide certification depth.
  - Implemented dedicated TSJ-44b certification harness/report flow:
    `TsjVersionRangeCertificationHarness`, `TsjVersionRangeCertificationReport`,
    and `TsjVersionRangeCertificationTest`.
  - Report artifact:
    `cli/target/tsj44b-version-range-certification.json`.
  - Report includes per-version pass/fail rows plus
    `firstFailingVersion` metadata per library range.
  - CI now runs a dedicated TSJ-44b gate check and uploads the TSJ-44b report artifact.
  - Release guidance for range upgrades and drift handling is documented in
    `docs/anyjar-certification.md`.
- Status: `Complete`.
- Dependencies: TSJ-40d, TSJ-44a.

### TSJ-44c: Real-application certification workloads and SLO gate
- Why: Library-level tests alone are insufficient for production confidence.
- Acceptance Criteria:
  - Certification includes multi-module real-application workloads using certified library combinations.
  - Gate enforces reliability/performance budgets on real workloads in addition to library micro-scenarios.
  - Failure artifacts include reproducible workload traces and bottleneck diagnostics.
  - Docs distinguish library-level certification from real-application certification tiers.
- Notes:
  - Extends TSJ-44 from API-level checks to end-to-end workload confidence.
  - Should align with TSJ-38 readiness metrics and TSJ-18/benchmark conventions where applicable.
  - Implemented dedicated TSJ-44c real-app certification harness/report flow:
    `TsjRealAppCertificationHarness`, `TsjRealAppCertificationReport`,
    and `TsjRealAppCertificationTest`.
  - Gate enforces reliability plus performance budgets on multi-workload runs
    and emits deterministic per-workload trace + bottleneck hints.
  - Report artifact:
    `cli/target/tsj44c-real-app-certification.json`.
  - CI now runs a dedicated TSJ-44c gate check and uploads the TSJ-44c report artifact.
  - Tiering/usage guidance is documented in:
    `docs/anyjar-realapp-certification.md`.
- Status: `Complete`.
- Dependencies: TSJ-38c, TSJ-44a, TSJ-44b.

### TSJ-44d: Certification governance and release-signoff closure
- Why: Final “any-jar” claims need governed release criteria and auditable signoff.
- Acceptance Criteria:
  - Release pipeline enforces certification signoff criteria tied to matrix/version/workload gates.
  - Compatibility manifest is published with certified libraries, versions, and support tiers.
  - Regression policy defines downgrade/rollback behavior when certified scenarios fail.
  - TSJ-44 is promoted to `Complete` once TSJ-44a/44b/44c/44d are complete and green.
- Notes:
  - This is the closure gate story for lifting TSJ-44 from subset to complete.
  - Governance/report format should stay consistent with existing TSJ conformance artifacts.
  - Implemented dedicated TSJ-44d governance/signoff harness/report flow:
    `TsjAnyJarGovernanceCertificationHarness`, `TsjAnyJarGovernanceCertificationReport`,
    and `TsjAnyJarGovernanceCertificationTest`.
  - Governance gate now requires matrix, version-range, and real-app certification signoff criteria
    and emits compatibility-manifest + regression-policy metadata.
  - Report artifact:
    `cli/target/tsj44d-anyjar-governance.json`.
  - CI now runs a dedicated TSJ-44d signoff check and uploads the TSJ-44d report artifact.
  - Governance contract is documented in:
    `docs/anyjar-governance-signoff.md`.
- Status: `Complete`.
- Dependencies: TSJ-44a, TSJ-44b, TSJ-44c.

## Planned Story Implementation Sequence

The following sequence orders dependency-safe implementation, with completion state inline.

1. TSJ-33d (`Complete`)
2. TSJ-35a (`Complete`)
3. TSJ-36a (`Complete`)
4. TSJ-39a (`Complete`)
5. TSJ-40a (`Complete`)
6. TSJ-41a (`Complete`)
7. TSJ-43a (`Complete`)
8. TSJ-44a (`Complete`)
9. TSJ-34d (`Complete`)
10. TSJ-33e (`Complete`)
11. TSJ-35b (`Complete`)
12. TSJ-37c (`Complete`)
13. TSJ-39b (`Complete`)
14. TSJ-40b (`Complete`)
15. TSJ-41b (`Complete`)
16. TSJ-41c (`Complete`)
17. TSJ-43b (`Complete`)
18. TSJ-34e (`Complete`)
19. TSJ-33f (`Complete`)
20. TSJ-35c (`Complete`)
21. TSJ-39c (`Complete`)
22. TSJ-40c (`Complete`)
23. TSJ-43c (`Complete`)
24. TSJ-37a (`Complete`)
25. TSJ-34f (`Complete`)
26. TSJ-40d (`Complete`)
27. TSJ-41d (`Complete`)
28. TSJ-43d (`Complete`)
29. TSJ-37d (`Complete`)
30. TSJ-37b (`Complete`)
31. TSJ-36b (`Complete`)
32. TSJ-44b (`Complete`)
33. TSJ-42a (`Complete`)
34. TSJ-38a (`Complete`)
35. TSJ-37e (`Complete`)
36. TSJ-36c (`Complete`)
37. TSJ-42b (`Complete`)
38. TSJ-42c (`Complete`)
39. TSJ-38b (`Complete`)
40. TSJ-42d (`Complete`)
41. TSJ-38c (`Complete`)
42. TSJ-44c (`Complete`)
43. TSJ-44d (`Complete`)

## Planned Sprint Plan

### Sprint P1: Core Foundations
- TSJ-33d, TSJ-35a, TSJ-36a, TSJ-39a
- Status: `Complete`

### Sprint P2: Interop and Policy Foundations
- TSJ-40a, TSJ-41a, TSJ-43a, TSJ-44a
- Status: `Complete`
- Progress: `TSJ-40a Complete; TSJ-41a Complete; TSJ-43a Complete; TSJ-44a Complete`

### Sprint P3: Adapter and Runtime Expansion
- TSJ-34d, TSJ-33e, TSJ-35b, TSJ-37c, TSJ-39b
- Status: `Complete`
- Progress: `TSJ-34d Complete; TSJ-33e Complete; TSJ-35b Complete; TSJ-37c Complete; TSJ-39b Complete`

### Sprint P4: Resolution and Conversion Depth
- TSJ-40b, TSJ-41b, TSJ-41c, TSJ-43b, TSJ-34e
- Status: `Complete`
- Progress: `TSJ-40b Complete; TSJ-41b Complete; TSJ-41c Complete; TSJ-43b Complete; TSJ-34e Complete`

### Sprint P5: Midstream Parity Closures
- TSJ-33f, TSJ-35c, TSJ-39c, TSJ-40c, TSJ-43c
- Status: `Complete`
- Progress: `TSJ-33f Complete; TSJ-35c Complete; TSJ-39c Complete; TSJ-40c Complete; TSJ-43c Complete`

### Sprint P6: Web and Guardrail Gates
- TSJ-37a, TSJ-34f, TSJ-40d, TSJ-41d, TSJ-43d
- Status: `Complete`
- Progress: `TSJ-37a Complete; TSJ-34f Complete; TSJ-40d Complete; TSJ-41d Complete; TSJ-43d Complete`

### Sprint P7: Ecosystem Matrix Expansion
- TSJ-37d, TSJ-37b, TSJ-36b, TSJ-44b
- Status: `Complete`
- Progress: `TSJ-37d Complete; TSJ-37b Complete; TSJ-36b Complete; TSJ-44b Complete`

### Sprint P8: DB and App Foundations
- TSJ-42a, TSJ-38a, TSJ-37e, TSJ-36c
- Status: `Complete`
- Progress: `TSJ-42a Complete; TSJ-38a Complete; TSJ-37e Complete; TSJ-36c Complete`

### Sprint P9: ORM and Security Depth
- TSJ-42b, TSJ-42c, TSJ-38b
- Status: `Complete`
- Progress: `TSJ-42b Complete; TSJ-42c Complete; TSJ-38b Complete`

### Sprint P10: Final Certification Gates
- TSJ-42d, TSJ-38c, TSJ-44c, TSJ-44d
- Status: `Complete`
- Progress: `TSJ-42d Complete; TSJ-38c Complete; TSJ-44c Complete; TSJ-44d Complete`

## Planned Milestone View

| Sprint | Expected Outcomes | Exit Criteria |
|---|---|---|
| P1 | Foundation upgrades for DI modes, proxy strategy, packaging baseline, and generated-class metadata parity (`TSJ-33d`, `TSJ-35a`, `TSJ-36a`, `TSJ-39a`). | All P1 stories are `Complete`; core DI/proxy/packaging/metadata tests pass in CI; updated docs for supported subset behavior are merged. |
| P2 | Broad interop foundation for dependency mediation graph, numeric overload parity, fleet policy source control, and first real-library certification expansion (`TSJ-40a`, `TSJ-41a`, `TSJ-43a`, `TSJ-44a`). | All P2 stories are `Complete`; deterministic mediation + policy precedence behavior is tested; expanded certification artifact is produced in CI. |
| P3 | Adapter/runtime expansion: controller DI parity, lifecycle diagnostics depth, booted transaction runtime parity, actuator baseline, introspector matrix start (`TSJ-34d`, `TSJ-33e`, `TSJ-35b`, `TSJ-37c`, `TSJ-39b`). | All P3 stories are `Complete`; integration suites for controller wiring, lifecycle ordering, transaction runtime, and actuator/introspector checks are green. |
| P4 | Conversion/resolution depth: scope-aware mediation, generic+reflective interop parity, centralized audit, and booted converter/error-envelope parity (`TSJ-40b`, `TSJ-41b`, `TSJ-41c`, `TSJ-43b`, `TSJ-34e`). | All P4 stories are `Complete`; CI validates scope resolution, generic/reflective conversion suites, centralized audit schema compatibility, and booted web conversion semantics. |
| P5 | Midstream closure stories for DI/AOP/metadata plus isolation/RBAC maturity (`TSJ-33f`, `TSJ-35c`, `TSJ-39c`, `TSJ-40c`, `TSJ-43c`). | All P5 stories are `Complete`; parity gates/artifacts for TSJ-33/35/39 are active; isolation and RBAC integration tests are stable in CI. |
| P6 | Gate-heavy sprint: validation module parity, packaged web conformance, and closure gates for TSJ-40/41/43 (`TSJ-37a`, `TSJ-34f`, `TSJ-40d`, `TSJ-41d`, `TSJ-43d`). | All P6 stories are `Complete`; TSJ-40, TSJ-41, and TSJ-43 are eligible to move from `Complete (Subset)` to `Complete`; packaged web conformance artifact is published. |
| P7 | Ecosystem matrix deepening: security/data module parity paths, embedded endpoint smoke, version-range certification (`TSJ-37d`, `TSJ-37b`, `TSJ-36b`, `TSJ-44b`). | All P7 stories are `Complete`; matrix report includes security/data module results; embedded smoke checks and version-range drift checks run in CI. |
| P8 | DB/app-level consolidation: real DB ORM parity, DB-backed reference parity, full matrix certification, dev-loop parity (`TSJ-42a`, `TSJ-38a`, `TSJ-37e`, `TSJ-36c`). | All P8 stories are `Complete`; TSJ-37 is eligible to move to `Complete`; DB-backed reference parity artifact and dev-loop validation are green. |
| P9 | ORM/security depth on top of DB baseline (`TSJ-42b`, `TSJ-42c`, `TSJ-38b`). | All P9 stories are `Complete`; lazy/proxy and persistence-context suites are stable; security-enabled TS/Kotlin reference parity checks pass. |
| P10 | Final certification closure for ORM, Kotlin-parity readiness, and any-jar governance (`TSJ-42d`, `TSJ-38c`, `TSJ-44c`, `TSJ-44d`). | All P10 stories are `Complete`; TSJ-42, TSJ-38, and TSJ-44 can move from `Complete (Subset)` to `Complete`; release-signoff artifacts are published. |

## First Three Sprints (Suggested)

### Sprint 1
- TSJ-0, TSJ-1, TSJ-2, TSJ-3, TSJ-4

### Sprint 2
- TSJ-5, TSJ-6, TSJ-7

### Sprint 3
- TSJ-8, TSJ-9, TSJ-10, TSJ-15, TSJ-19

## Exit Criteria for MVP Milestone
MVP is reached when:
1. Stories TSJ-0, TSJ-1 through TSJ-13, TSJ-15, and TSJ-19 are at least `Complete (Subset)`.
2. Differential suite passes for defined MVP language subset.

## Exit Criteria for Full Async Parity
Full async parity is reached when:
1. Stories TSJ-13a through TSJ-13f are fully `Complete` (no remaining AC gaps).
2. Async differential suite passes for control-flow, Promise semantics, and top-level-await module ordering.
3. CLI can compile and run a multi-file sample app with predictable behavior.

## Epic K: Java Classpath Descriptor Layer for Kotlin-like JAR Support

### TSJ-45: Classpath symbol index (class -> origin) with mediation integration
- Why: Deterministic class discovery is the foundation for compile-time Java modeling.
- Acceptance Criteria:
  - Build a mediated class index after TSJ-40 scope filtering:
    `internalName -> ClassOrigin { sourceKind, location, jarEntry, moduleName, packageName }`.
  - Unified indexing supports directory entries, jar entries, and JDK `jrt:/` module classes.
  - Duplicate class handling is deterministic:
    `shared` mode keeps mediated winner and records shadowed losers;
    `app-isolated` mode fails when app output and dependency classpath define the same class.
  - Diagnostics include winner reason (for example nearest/root-order tie-break), all origins, and actionable guidance.
  - Index metadata is persisted for debug/repro (`class-index.json`) with deterministic ordering.
- Notes:
  - Implemented classpath symbol indexing in
    `cli/src/main/java/dev/tsj/cli/ClasspathSymbolIndexer.java`.
  - Index is generated during compile/run/spring-package artifact compilation and persisted as:
    `<out>/class-index.json`.
  - Artifact metadata now includes:
    `interopClasspath.classIndex.path`,
    `interopClasspath.classIndex.symbolCount`,
    and `interopClasspath.classIndex.duplicateCount`.
  - Duplicate symbol handling is deterministic:
    shared mode records shadowed symbols with explicit `rule` metadata (`mediated-order`, `app-first`);
    app-isolated mode fails fast with origin-aware `TSJ-RUN-009` diagnostics.
  - JRT module paths (`jrt:/...`) are now accepted in `--classpath` input parsing and indexed with
    `sourceKind=jrt-module` and module-name metadata.
  - Coverage is added in
    `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`:
    `compilePersistsClasspathSymbolIndexWithShadowDiagnosticsInSharedMode`,
    `compileSupportsJrtClasspathEntriesInSymbolIndex`,
    and
    `runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode`.
- Status: `Complete`.
- Dependencies: TSJ-27, TSJ-40a, TSJ-40b, TSJ-40c.

### TSJ-46: Classfile reader for header/member/attribute modeling
- Why: Descriptor construction must parse classfiles without JVM classloading side effects.
- Acceptance Criteria:
  - Parse `.class` into `RawClassInfo` including:
    class/super/interfaces/flags/version;
    fields/methods (descriptor/flags/signature/exceptions);
    `MethodParameters`;
    runtime visible/invisible annotations (class/member/parameter);
    runtime type annotations;
    `AnnotationDefault`;
    `InnerClasses`, `EnclosingMethod`, `NestHost`, `NestMembers`;
    `Record` and `PermittedSubclasses` metadata.
  - Bytecode instruction/code attributes are not required for this story.
  - Golden tests compare parsed fields against `javap -v` for representative fixtures
    (generic, bridge/synthetic, record, sealed, nested).
- Notes:
  - Implemented classfile parser in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaClassfileReader.java`.
  - Parser emits `RawClassInfo` with:
    class/super/interfaces/access/version,
    fields/methods/signatures/exceptions,
    runtime visible/invisible annotations,
    runtime type annotations,
    method parameter metadata,
    `AnnotationDefault`,
    `InnerClasses`, `EnclosingMethod`, `NestHost`, `NestMembers`,
    record components, and permitted-subclass metadata.
  - Parser avoids bytecode body decoding and skips non-required attributes safely.
  - Golden/behavior coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaClassfileReaderTest.java`,
    including `javap -v` cross-check assertions.
- Status: `Complete`.
- Dependencies: TSJ-45.

### TSJ-47: Lazy Java SymbolTable and per-session caching
- Why: Eager parsing does not scale on realistic classpaths.
- Acceptance Criteria:
  - `JavaSymbolTable.resolveClass(fqcn)` lazily loads and memoizes descriptors.
  - Cache key includes `(fqcn, classpathFingerprint, classOriginDigest)` to avoid stale hits.
  - Instrumentation/metrics prove parse-once behavior per class per build session.
  - Classpath fingerprint mismatch invalidates stale entries deterministically.
- Notes:
  - Implemented lazy descriptor symbol table in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaSymbolTable.java`.
  - `resolveClass(fqcn)` resolves descriptors on demand from directory/jar classpath entries
    and memoizes by `(internalName, classpathFingerprint)` cache key.
  - Parse instrumentation is exposed via `parsedCount(fqcn)` for deterministic cache-hit assertions.
  - `updateClasspath(...)` invalidates cached descriptors when fingerprint changes.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaSymbolTableTest.java`:
    parse-once behavior, fingerprint invalidation, jar-backed lookup, and missing-class handling.
- Status: `Complete`.
- Dependencies: TSJ-46, TSJ-40.

### TSJ-48: Java type model (descriptor + generic signature layer)
- Why: Overload resolution and interop typing require modeled generics, not erased strings only.
- Acceptance Criteria:
  - Implement `JType` hierarchy:
    primitive, class, array, type variable, wildcard, parameterized, intersection bounds.
  - Implement `JMethodSig` and `JFieldSig` with type-parameter support.
  - Parse class/method Signature attributes (documented subset first):
    type parameters, bounds, parameterized owners, wildcard `+/-`, arrays, nested generic types.
  - Unsupported/missing signatures fall back to erased descriptors with explicit best-effort marker (not hard error).
- Notes:
  - Implemented descriptor + signature type model in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaTypeModel.java`
    with:
    primitive/class/array/type-variable/wildcard/parameterized/intersection types,
    and method/field signature records with explicit descriptor-fallback markers.
  - Implemented generic-signature parser in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaSignatureParser.java`.
  - Signature parser coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaSignatureParserTest.java`:
    wildcard bounds, type-parameter bounds, descriptor fallback, parameterized-owner parsing,
    and explicit intersection-bound modeling.
- Status: `Complete`.
- Dependencies: TSJ-46.

### TSJ-49: Nullability inference and platform-type marking
- Why: Kotlin-like Java interop requires explicit nullability semantics for sound diagnostics.
- Acceptance Criteria:
  - Recognize configurable nullability annotation families:
    JetBrains, JSR-305, AndroidX, Checker Framework.
  - Produce nullability state on parameter/return/field/type-use:
    `NON_NULL`, `NULLABLE`, `PLATFORM`.
  - Support default-nullness scopes where annotations define package/class defaults.
  - Expose nullability through stable descriptor API consumed by frontend/typechecker integration.
- Notes:
  - Implemented nullability inference engine in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaNullabilityAnalyzer.java`.
  - Analyzer recognizes annotation families:
    JetBrains, JSR-305, AndroidX, and Checker Framework.
  - Output model includes stable class/method/field nullability views with
    `NON_NULL`, `NULLABLE`, and `PLATFORM` states.
  - Package-level defaults (for example `package-info` + `@ParametersAreNonnullByDefault`)
    and class-level defaults (for example `@NullUnmarked`) are incorporated into fallback inference.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaNullabilityAnalyzerTest.java`,
    including family recognition, default-scope behavior, and platform fallback.
- Status: `Complete`.
- Dependencies: TSJ-39, TSJ-46, TSJ-48.

### TSJ-50: Inheritance graph and member lookup with visibility/module rules
- Why: Correct callable/member sets are hierarchy-wide and visibility-sensitive.
- Acceptance Criteria:
  - `getAllSupertypes(fqcn)` returns deterministic superclass chain + transitive interface closure.
  - Cycle detection for malformed inputs emits diagnostic and applies safe traversal cut.
  - `collectMembers(fqcn, name)` returns declared + inherited members with origin and visibility context.
  - Lookup applies Java access rules (`public/protected/package/private`) and module export readability.
  - Supertype/member cache is deterministic and reused across lookups.
- Notes:
  - Implemented hierarchy/member resolver in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaInheritanceResolver.java`.
  - `getAllSupertypes(fqcn)` now returns deterministic superclass + transitive interface closure.
  - Inheritance cycle detection emits diagnostics and applies traversal cut to avoid unbounded recursion.
  - `collectMembers(fqcn, name, context)` returns declared + inherited fields/methods with:
    owner origin, inheritance marker, visibility classification, and access decision metadata.
  - Visibility logic covers Java `public/protected/package/private`, with module readability/export checks
    via lookup context (for integration with module descriptor resolution stories).
  - Resolver caches supertype closures and per-class member scans deterministically.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaInheritanceResolverTest.java`:
    closure determinism, cycle handling, visibility semantics, module readability/export checks,
    and cache reuse assertions.
- Status: `Complete`.
- Dependencies: TSJ-47.

### TSJ-51: Override/erasure/bridge filtering semantics
- Why: Raw classfile member lists must be normalized to Java language semantics.
- Acceptance Criteria:
  - Implement Java override-equivalence key using erasure semantics.
  - Bridge/synthetic filtering hides bridge methods when non-bridge equivalent is available.
  - Covariant return override behavior is represented in normalized member sets.
  - Fixtures with generic overrides and synthetic bridges validate normalization behavior.
- Notes:
  - Implemented override normalization in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaOverrideNormalizer.java`.
  - Normalizer computes canonical override keys, applies bridge/synthetic preference filtering,
    and preserves overridden descriptor lineage for covariant return reporting.
  - Added normalization fixtures in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaOverrideNormalizerTest.java`
    covering generic bridge filtering, covariant return override tracking,
    and erased-key normalization behavior.
- Status: `Complete`.
- Dependencies: TSJ-48, TSJ-50.

### TSJ-52: Java property synthesis (`get/set/is`) for TS ergonomics
- Why: Property projection is needed for Kotlin-like Java ergonomics in TS authoring.
- Acceptance Criteria:
  - Synthesize property descriptors for `getX()/setX(T)` and `isX():boolean`.
  - Conflict resolution is deterministic; ambiguous cases are not synthesized.
  - Debug diagnostics explain why synthesis was skipped when ambiguous/conflicting.
  - Property synthesis can be feature-flagged for rollout safety.
- Notes:
  - Implemented deterministic property synthesizer in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaPropertySynthesizer.java`.
  - Synthesizer projects Java bean-style accessors into property descriptors for:
    `getX()`, `isX()`, and `setX(T)`, with deterministic conflict handling for
    ambiguous getter/getter and setter overload scenarios.
  - Skip reasons are emitted as structured diagnostics in synthesis output.
  - Feature-flag control is implemented through explicit `enabled` toggle.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaPropertySynthesizerTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-50, TSJ-51.

### TSJ-53: SAM detection and functional typing metadata
- Why: Compile-time typing of TS lambdas for Java callbacks requires precise SAM modeling.
- Acceptance Criteria:
  - SAM detection excludes:
    `Object` members, static methods, default methods, bridge-only artifacts.
  - Handles inherited abstract methods and generic specialization consistently.
  - `@FunctionalInterface` is validated as a consistency hint (not sole source of truth).
  - Exposes canonical `samMethod` metadata for frontend typing and adapter generation.
- Notes:
  - Implemented SAM analyzer in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaSamAnalyzer.java`.
  - Detection excludes object-signature methods, static methods, default methods,
    and bridge/synthetic artifacts; inherited abstract members are included through
    public-interface method closure.
  - Canonical method identity is normalized on signature keying and exposes
    `samMethod` metadata (`owner`, `name`, `descriptor`, `genericSignature`).
  - `@FunctionalInterface` is treated as a consistency hint; violations emit diagnostics.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaSamAnalyzerTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-50, TSJ-51.

### TSJ-54: Compile-time overload resolution using descriptor model
- Why: Runtime reflective guessing must be replaced by deterministic compile-time selection.
- Acceptance Criteria:
  - Implement deterministic overload resolution phases covering:
    applicability, varargs, boxing/unboxing, numeric widening, nullability compatibility, specificity tie-break.
  - Selected target is serialized as stable executable/member identity
    (`owner`, `name`, `descriptor`, `invokeKind`) in compile artifact metadata.
  - Diagnostics distinguish:
    no applicable candidate vs ambiguous best candidates, with candidate summary details.
  - Certified fixture cross-check ensures compile-time selection equals TSJ-29/41 runtime behavior for covered cases.
  - Runtime invocation path accepts preselected target identity and does not re-resolve in certified mode.
- Notes:
  - Implemented deterministic overload resolver in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaOverloadResolver.java`
    with ordered phases for applicability, varargs, boxing/unboxing, numeric widening,
    nullability compatibility, and specificity tie-break.
  - Selected target identity (`owner`, `name`, `descriptor`, `invokeKind`) is persisted
    into generated interop metadata through
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/InteropBridgeGenerator.java`
    and surfaced to compile artifacts by
    `cli/src/main/java/dev/tsj/cli/TsjCli.java`.
  - Diagnostics now distinguish `NO_APPLICABLE` and `AMBIGUOUS` with candidate summaries.
  - Runtime preselected invocation path is implemented in
    `runtime/src/main/java/dev/tsj/runtime/TsjJavaInterop.java`
    via `invokeBindingPreselected(...)`, avoiding re-resolution in certified flow.
  - Coverage is added/expanded in:
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaOverloadResolverTest.java`,
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/InteropBridgeGeneratorTest.java`,
    `runtime/src/test/java/dev/tsj/runtime/TsjInteropCodecTest.java`,
    and `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-29, TSJ-41a, TSJ-48, TSJ-49, TSJ-51.

### TSJ-55: Frontend typechecker integration for `java:` imports
- Why: Java descriptors must become compile-time TS types, not runtime-only interop handles.
- Acceptance Criteria:
  - Frontend resolves `java:` imports to descriptor-backed symbols:
    classes, constructors, static/instance methods, static/instance fields.
  - Frontend emits TS-checker-visible type representation for overloads and member signatures
    (synthetic declaration model or equivalent stable metadata API).
  - Missing classes/members and visibility violations fail at compile time with source-mapped diagnostics.
  - Existing TSJ-26 interop binding diagnostics remain stable and are augmented with descriptor-aware details.
- Notes:
  - Added descriptor-backed symbol model
    `compiler/frontend/src/main/java/dev/tsj/compiler/frontend/FrontendInteropSymbol.java`
    and frontend output field `interopSymbols` in
    `compiler/frontend/src/main/java/dev/tsj/compiler/frontend/FrontendAnalysisResult.java`.
  - Implemented resolver
    `compiler/frontend/src/main/java/dev/tsj/compiler/frontend/JavaInteropSymbolResolver.java`
    for constructors, static/instance methods, and static/instance fields with descriptor lists.
  - Resolver emits source-mapped diagnostics:
    `TSJ55-INTEROP-CLASS-NOT-FOUND`,
    `TSJ55-INTEROP-MEMBER-NOT-FOUND`,
    `TSJ55-INTEROP-VISIBILITY`.
  - TSJ-26 diagnostics remain intact and are additive with descriptor-aware failures.
  - Coverage is added/expanded in
    `compiler/frontend/src/test/java/dev/tsj/compiler/frontend/TypeScriptFrontendServiceTest.java`.
- Status: `Complete`.
- Dependencies: TSJ-26, TSJ-54.

### TSJ-56: Incremental persistent caching for descriptor layer
- Why: Descriptor modeling must remain fast across iterative builds.
- Acceptance Criteria:
  - Persist descriptor cache keyed by mediated classpath fingerprint, tool version, and schema version.
  - Warm-build benchmark demonstrates >90% descriptor reuse for unchanged classpaths.
  - Cache corruption/stale-format detection triggers safe rebuild with explicit warning diagnostics.
  - Cache stats are emitted in build diagnostics for observability (`hits`, `misses`, `invalidations`).
- Notes:
  - Added persistent descriptor cache support to
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaSymbolTable.java`
    with cache envelope keying on classpath fingerprint, tool version, schema version,
    and target JDK release.
  - Cache stores parsed class bytes/origin metadata for deterministic warm reuse and safe rehydrate.
  - Corruption and schema/tool/fingerprint mismatches trigger explicit invalidation diagnostics
    and safe rebuild.
  - Cache observability metrics are exposed through stable `CacheStats` (`hits`, `misses`, `invalidations`)
    and diagnostic message APIs.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaSymbolTablePersistentCacheTest.java`,
    including warm-cache reuse ratio assertions (>90%).
- Status: `Complete`.
- Dependencies: TSJ-40d, TSJ-47.

### TSJ-57: Module-access and multi-release JAR correctness
- Why: Correct Java modeling requires module system semantics and JDK-targeted MR-JAR selection.
- Acceptance Criteria:
  - Descriptor lookup honors Java module readability and package export rules for named modules and `jrt:/`.
  - Multi-release jar (`META-INF/versions/*`) class selection is deterministic against configured target JDK level.
  - Access diagnostics distinguish:
    class not found, class not exported, class not readable, and target-level mismatch.
  - Conformance fixtures validate mixed classpath/module-path and MR-JAR precedence behavior.
  - Selected origin (base vs versioned entry) is persisted in descriptor metadata for reproducibility.
- Notes:
  - Extended descriptor lookup in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaSymbolTable.java`
    with target-JDK-aware multi-release JAR selection (`META-INF/versions/*`) and deterministic
    origin metadata (`ClassOrigin`) persisted on resolution results.
  - Added differentiated resolution statuses:
    `FOUND`, `NOT_FOUND`, `TARGET_LEVEL_MISMATCH`.
  - Added module-access diagnostic layer in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaModuleAccessResolver.java`
    with explicit outcomes:
    `CLASS_NOT_FOUND`, `CLASS_NOT_READABLE`, `CLASS_NOT_EXPORTED`, `TARGET_LEVEL_MISMATCH`, `ACCESSIBLE`.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaSymbolTableTest.java`
    and
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaModuleAccessResolverTest.java`,
    including MR-JAR precedence, target-level mismatch, and access-status differentiation.
- Status: `Complete`.
- Dependencies: TSJ-40, TSJ-45, TSJ-46, TSJ-50.

### TSJ-57a: Automatic module graph extraction for named modules and `jrt:/`
- Why: TSJ-57 subset currently relies on caller-provided module/readability/export context.
- Acceptance Criteria:
  - Build module/readability/export maps automatically from classpath/module-path + `jrt:/` module descriptors.
  - Wire extracted module graph into class/member lookup without requiring caller-managed maps.
  - Diagnostics include concrete module edge/package export evidence.
  - Regression tests cover named-module and `jrt:/` lookups without manual context injection.
- Notes:
  - Implemented module graph extraction in
    `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JavaModuleGraphBuilder.java`.
  - Builder derives module readability closure, exported-package sets, and package-to-module ownership
    from system modules and optional module-path entries.
  - Added automatic resolver wiring via
    `JavaModuleAccessResolver.AccessContext.forRequesterModule(...)`,
    so class-access checks can run without caller-supplied manual maps.
  - `JavaSymbolTable` now detects `jrt:/` module names for resolved class origins,
    and resolver falls back to graph package ownership when explicit class->module mapping is absent.
  - Coverage is added in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaModuleGraphBuilderTest.java`:
    system module graph assertions, automatic-module JAR graph assertions,
    and end-to-end automatic `jrt:/` access checks.
- Status: `Complete`.
- Dependencies: TSJ-57.

### TSJ-57b: Mixed classpath/module-path conformance fixtures for MR-JAR and module access
- Why: Story closure requires fixture-level validation beyond unit tests.
- Acceptance Criteria:
  - Add conformance fixtures exercising mixed classpath/module-path precedence, MR-JAR selection,
    and module readability/export failures.
  - Persist and assert selected origin metadata (`base` vs `versioned`) in fixture diagnostics/artifacts.
  - Validate fixture outcomes through differential harness in CI.
- Notes:
  - Added conformance fixture suite in
    `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JavaModuleConformanceFixtureTest.java`.
  - Fixtures cover:
    mixed classpath/module-path precedence ordering,
    MR-JAR version selection against target JDK,
    and automatic module access diagnostics (`ACCESSIBLE`, `CLASS_NOT_READABLE`, `CLASS_NOT_EXPORTED`).
  - Fixture assertions validate persisted selected-origin metadata (`classpathEntry`, `entryName`,
    `versionedEntry`, `selectedVersion`) for reproducibility.
  - Suite is executed in CI via standard Maven test workflow (`mvn ... test`).
- Status: `Complete`.
- Dependencies: TSJ-57a.

## Planned Story Implementation Sequence (Epic K Extension)

1. TSJ-45 (`Complete`)
2. TSJ-46 (`Complete`)
3. TSJ-47 (`Complete`)
4. TSJ-48 (`Complete`)
5. TSJ-50 (`Complete`)
6. TSJ-57 (`Complete`)
7. TSJ-57a (`Complete`)
8. TSJ-57b (`Complete`)
9. TSJ-51 (`Complete`)
10. TSJ-49 (`Complete`)
11. TSJ-54 (`Complete`)
12. TSJ-55 (`Complete`)
13. TSJ-53 (`Complete`)
14. TSJ-52 (`Complete`)
15. TSJ-56 (`Complete`)

## Planned Sprint Plan (Epic K Extension)

### Sprint P11: Descriptor Foundations
- TSJ-45, TSJ-46, TSJ-47
- Status: `Complete`
- Progress: `TSJ-45 Complete; TSJ-46 Complete; TSJ-47 Complete`

### Sprint P12: Java Semantics Core
- TSJ-48, TSJ-50, TSJ-57, TSJ-57a, TSJ-57b, TSJ-51, TSJ-49, TSJ-54
- Status: `Complete`
- Progress: `TSJ-48 Complete; TSJ-50 Complete; TSJ-57 Complete; TSJ-57a Complete; TSJ-57b Complete; TSJ-51 Complete; TSJ-49 Complete; TSJ-54 Complete`

### Sprint P13: Frontend Integration and Performance
- TSJ-55, TSJ-53, TSJ-52, TSJ-56
- Status: `Complete`
- Progress: `TSJ-55 Complete; TSJ-53 Complete; TSJ-52 Complete; TSJ-56 Complete`

## Epic L: Full TypeScript Syntax Coverage

### TSJ-58: Replace handwritten backend parser with frontend AST ingestion
- Why: Full syntax coverage is blocked by the current handwritten parser/tokenizer in backend.
- Acceptance Criteria:
  - Backend compile path consumes frontend-produced TypeScript AST/semantic payload instead of reparsing source text.
  - AST transport contract is versioned and schema-validated between `compiler/frontend` and backend.
  - Legacy handwritten parser path is removable or gated behind explicit fallback flag.
  - Source-mapped diagnostics remain stable or improve in precision.
- Status: `Complete (Subset)`.
- Progress:
  - Backend compile path now consumes frontend TypeScript token payload via `emit-backend-tokens.cjs` (`tsj-backend-token-v1`) instead of handwritten tokenization.
  - Contract is schema-versioned/validated, bridge diagnostics are mapped back to source modules, and legacy tokenizer remains behind `-Dtsj.backend.legacyTokenizer=true`.
  - `TypeScriptSyntaxBridge` now resolves bridge script deterministically even when compile is launched from fixture subdirectories.
- Remaining Gap:
  - Backend still reparses tokens with handwritten parser; full typed-AST ingestion is not complete.
- Dependencies: TSJ-4, TSJ-5, TSJ-7.

### TSJ-58a: Replace backend handwritten parser with frontend typed AST contract
- Why: TSJ-58 token-bridge reduced tokenizer drift, but full syntax parity still requires removing backend parser ownership.
- Acceptance Criteria:
  - Frontend emits typed AST payload (statement/expression node kinds + source ranges) under versioned schema.
  - Backend lowering consumes typed AST payload directly; handwritten parser is removed or excluded from default path.
  - Conformance fixtures show no regression vs TSJ-58 token-bridge baseline.
- Status: `Complete (Subset)`.
- Progress:
  - Frontend token bridge now emits a typed AST node stream (`astNodes`) with node kind and source range coordinates.
  - Backend now validates and consumes the AST contract during compile (`TSJ-BACKEND-AST-SCHEMA` on missing/invalid AST payload).
  - Added bridge + compiler tests for AST payload contract enforcement and schema diagnostics.
- Remaining Gap:
  - Backend lowering still depends on handwritten parser for statement/expression reconstruction.
- Dependencies: TSJ-58.

### TSJ-58b: Complete backend lowering cutover to typed AST as source-of-truth
- Why: TSJ-58a introduced AST contract consumption but not full parser ownership transfer.
- Acceptance Criteria:
  - Lowering pipeline reconstructs internal program directly from typed AST payload without handwritten token parser.
  - Handwritten parser is fully removed from default path (optional debug fallback only).
  - Existing conformance suites remain green and differential behavior remains stable.
- Status: `Complete (Subset)`.
- Progress:
  - Added typed-AST lowering for `ClassDeclarationStatement` (class name, `extends`, fields, constructor, methods).
  - Added typed-AST lowering for constructor `super(...)` calls through `SuperCallStatement`.
  - Added AST-only backend tests (with parser fallback disabled) for class and inheritance execution paths.
  - Backend + differential conformance suites remain green after the AST-lowering expansion.
- Remaining Gap:
  - Handwritten parser fallback is still enabled on unsupported normalized AST shapes.
  - Full default-path parser removal and complete AST lowering coverage remain outstanding.
- Dependencies: TSJ-58a.

### TSJ-58c: Remove default parser fallback after typed-AST lowering reaches full in-scope coverage
- Why: TSJ-58b closed major lowering gaps but default compile path still relies on handwritten parser fallback.
- Acceptance Criteria:
  - Default compile path fails without handwritten parser fallback for unsupported AST shapes.
  - Typed-AST lowering covers all in-scope syntax required by TSJ-59/TSJ-60 baseline stories.
  - Legacy parser path is debug-only and excluded from default compile behavior.
  - Differential/conformance suites remain green with default parser fallback disabled.
- Status: `Planned`.
- Dependencies: TSJ-58b, TSJ-59a, TSJ-60.

### TSJ-59: Statement syntax completeness
- Why: Modern TS/JS programs rely on control-flow forms beyond current subset.
- Acceptance Criteria:
  - Support `for`, `for...of`, `for...in`, `do...while`, and `switch` lowering.
  - Support labeled `break`/`continue` with correct control-flow semantics.
  - Support optional catch binding and full `try/catch/finally` statement shapes.
  - Differential fixtures cover nested and mixed control-flow edge cases.
- Status: `Complete (Subset)`.
- Progress:
  - Added `do...while` parsing/lowering in backend subset and coverage tests.
  - Added targeted unsupported diagnostic for `continue` targeting `do...while` until full lowering semantics are implemented.
  - Optional catch binding and core `try/catch/finally` shapes remain supported.
- Remaining Gap:
  - `for`, `for...of`, `for...in`, `switch`, labeled control flow, and full `do...while` continue semantics remain incomplete.
- Dependencies: TSJ-58.

### TSJ-59a: Complete remaining statement-form lowering for TSJ-59
- Why: TSJ-59 subset progress needs closure across loop/switch/labeled forms.
- Acceptance Criteria:
  - Implement lowering for `for`, `for...of`, `for...in`, and `switch`.
  - Implement labeled `break`/`continue` with correct target resolution.
  - Complete `do...while` continue semantics and remove temporary guardrail diagnostic.
  - Add differential fixtures for nested mixed statement forms.
- Status: `Complete (Subset)`.
- Progress:
  - Added frontend normalized-AST lowering for `for` statements into existing backend-compatible control-flow forms.
  - Added frontend normalized-AST lowering for `switch` statements into deterministic dispatch loops for non-fallthrough clauses.
  - Implemented `do...while` continue semantics in normalized-AST lowering by rewriting current-loop `continue` paths to execute condition checks before re-entry.
  - Added/updated backend tests for `for`, `switch`, and `do...while`-`continue` behavior and bridge normalization coverage.
- Remaining Gap:
  - `for...of` and `for...in` lowering is still unsupported in normalized AST and falls back to unsupported backend parser diagnostics.
  - Labeled `break`/`continue` target-resolution semantics are not yet implemented.
  - Full switch fallthrough semantics (without terminal `break`) remain incomplete.
- Dependencies: TSJ-59.

### TSJ-59b: Close remaining statement-form semantics for iteration labels and switch fallthrough
- Why: TSJ-59a closed major statement lowering gaps, but iteration variants/labels/fallthrough are still missing.
- Acceptance Criteria:
  - Implement `for...of` and `for...in` lowering with deterministic semantics for supported iterable/object subsets.
  - Implement labeled `break`/`continue` with correct target resolution across nested loops/switch blocks.
  - Implement switch fallthrough semantics and conformance coverage for explicit fallthrough cases.
  - Add differential fixtures for mixed nested loops, labels, and switch fallthrough control flow.
- Status: `Planned`.
- Dependencies: TSJ-59a, TSJ-60.

### TSJ-60: Expression/operator completeness
- Why: Syntax parity requires full operator and expression grammar support.
- Acceptance Criteria:
  - Support optional chaining (`?.`), nullish coalescing (`??`), and compound assignment variants.
  - Support bitwise, `in`, `instanceof`, exponentiation, and comma/sequence expressions with correct precedence.
  - Support template literals (plain/tagged) and computed property expressions in expression positions.
  - Conformance tests assert operator precedence/associativity parity against Node for covered subset.
- Status: `Planned`.
- Dependencies: TSJ-58.

### TSJ-61: Binding patterns and destructuring
- Why: Real TS code heavily uses destructuring in declarations, parameters, and assignments.
- Acceptance Criteria:
  - Support object/array destructuring in variable declarations and assignment targets.
  - Support default initializers and rest elements in binding patterns.
  - Support destructuring in function parameters and loop headers.
  - Diagnostics clearly distinguish unsupported binding shapes if any temporary subset remains.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-59, TSJ-60.

### TSJ-62: Class/object syntax completeness
- Why: Current class/object subset blocks mainstream framework and app authoring.
- Acceptance Criteria:
  - Support class field declarations (instance/static) including initializer expressions.
  - Support getters/setters, computed member names, and parameter properties.
  - Support object literal spread/shorthand/computed keys and method variants.
  - Class/object syntax fixtures compile without backend parse failures.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-60.

### TSJ-63: Function forms completeness
- Why: Function syntax variance is central to TypeScript ergonomics and library APIs.
- Acceptance Criteria:
  - Support default parameters, optional parameters, rest parameters, and destructured parameters.
  - Support generator and async-generator syntax with documented runtime semantics.
  - Support overload signature declarations as type-level declarations (erased at runtime).
  - Add conformance fixtures for nested async/generator/control-flow combinations.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-59, TSJ-60, TSJ-61.

### TSJ-64: Type-syntax erasure completeness
- Why: Full TS syntax support requires parsing type-level constructs even when erased at runtime.
- Acceptance Criteria:
  - Parse and typecheck (via frontend) interfaces, type aliases, unions/intersections, generics, conditional/mapped/template-literal types.
  - Backend lowering accepts type-only syntax without parse failures and with correct runtime erasure behavior.
  - Support `import type` / `export type` syntax without runtime emission.
  - Diagnostics separate typecheck failures from backend lowering/runtime failures.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-4.

### TSJ-65: Module/import-export syntax parity
- Why: Full TS syntax requires full module-surface support beyond current relative named-import subset.
- Acceptance Criteria:
  - Support default imports/exports, namespace imports, re-export forms (`export *`, `export { ... } from`), and type-only re-exports.
  - Support dynamic `import()` lowering with documented runtime semantics.
  - Preserve top-level-await ordering semantics across expanded module forms.
  - Multi-file conformance suite validates live bindings and evaluation order for expanded forms.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-12, TSJ-13f, TSJ-64.

### TSJ-66: Decorator syntax parity (legacy + TC39 model)
- Why: Full TS syntax support requires robust handling of both legacy and evolving decorator forms.
- Acceptance Criteria:
  - Parse supported legacy TypeScript decorators and stage-3 decorator syntax through frontend AST ingestion.
  - Lower class/method/field/accessor/parameter decorators under explicit policy/configuration.
  - Ensure generated metadata/annotation paths remain deterministic and source-mapped.
  - Provide explicit diagnostics for unsupported decorator proposal features.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-32a, TSJ-64.

### TSJ-67: TSX/JSX syntax support
- Why: Full TypeScript syntax support includes TSX for frontend/server-rendered workloads.
- Acceptance Criteria:
  - Parse `.tsx` entry/modules and lower JSX according to configured JSX mode (`preserve`/`react-jsx` subset).
  - Ensure source maps and diagnostics map to TSX source coordinates.
  - Add multi-file TSX fixture coverage in compile/run/differential harness paths.
  - Document JSX mode limitations and runtime requirements.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-60, TSJ-65.

### TSJ-68: Large-scale conformance gate for full syntax
- Why: “Full syntax support” claim requires broad, measurable evidence.
- Acceptance Criteria:
  - Add a large conformance corpus sourced from TypeScript conformance tests + selected OSS fixtures.
  - CI publishes pass/fail breakdown by syntax category and tracks regressions.
  - Define and enforce minimum pass-rate thresholds for release gating.
  - Failing categories must emit actionable diagnostics with minimized repros.
- Status: `Planned`.
- Dependencies: TSJ-59, TSJ-60, TSJ-61, TSJ-62, TSJ-63, TSJ-64, TSJ-65, TSJ-66, TSJ-67.

### TSJ-69: Incremental performance closure for full syntax pipeline
- Why: Full syntax support must be practical for developer loops and CI.
- Acceptance Criteria:
  - Add AST/IR incremental cache keyed by source/module graph fingerprint and compiler version.
  - Warm builds demonstrate significant reuse on unchanged projects (target threshold defined in benchmark docs).
  - Compile diagnostics expose reuse/invalidations at each stage (frontend, lowering, backend).
  - Watch/dev-loop workflow docs and CI smoke benchmarks are updated for new pipeline.
- Status: `Planned`.
- Dependencies: TSJ-58, TSJ-68.

### TSJ-70: Full TypeScript syntax GA readiness gate
- Why: Need explicit closure criteria before claiming full TS syntax support.
- Acceptance Criteria:
  - No backend parse failures on certified syntax corpus for in-scope language level.
  - Compatibility manifest lists supported TS version/language features and residual exclusions.
  - Differential/runtime conformance gates are green for all mandatory suites.
  - Release signoff artifact is generated and referenced in docs.
- Status: `Planned`.
- Dependencies: TSJ-68, TSJ-69.

## Planned Story Implementation Sequence (Epic L)

1. TSJ-58
2. TSJ-58a
3. TSJ-58b
4. TSJ-59
5. TSJ-59a
6. TSJ-59b
7. TSJ-60
8. TSJ-58c
9. TSJ-61
10. TSJ-62
11. TSJ-63
12. TSJ-64
13. TSJ-65
14. TSJ-66
15. TSJ-67
16. TSJ-68
17. TSJ-69
18. TSJ-70

## Planned Sprint Plan (Epic L)

### Sprint P14: AST Pipeline Cutover
- TSJ-58, TSJ-58a, TSJ-58b, TSJ-58c, TSJ-59, TSJ-59a, TSJ-59b, TSJ-60
- Status: `In Progress`

### Sprint P15: Core Syntax Closure
- TSJ-61, TSJ-62, TSJ-63, TSJ-64
- Status: `Planned`

### Sprint P16: Module/Decorator/TSX Expansion
- TSJ-65, TSJ-66, TSJ-67
- Status: `Planned`

### Sprint P17: Conformance and GA Closure
- TSJ-68, TSJ-69, TSJ-70
- Status: `Planned`
