# TypeScript to JVM Bytecode Compiler: Initial Analysis

## Goal
Build a compiler that takes TypeScript source and emits JVM bytecode that runs on the Java Virtual Machine.

## Critical Framing
The phrase "any TypeScript code" is an end-state objective, not a realistic phase-1 scope.
TypeScript can express JavaScript runtime behavior (dynamic objects, prototype chains, `eval`, async/event-loop semantics) that does not map directly to JVM semantics without a runtime compatibility layer.

Pragmatic delivery requires:
1. A staged compatibility model.
2. A TypeScript runtime library on JVM.
3. Progressive lowering from TS semantics to JVM-friendly IR.

## Recommended Architecture

### Frontend
1. Parse and type-check via TypeScript compiler API.
2. Reuse TS AST and symbol/type information.
3. Capture diagnostics with source maps preserved.

### Middle-end
1. Lower TS AST to a typed intermediate representation (IR) with explicit control flow.
2. Normalize JS/TS features (closures, classes, modules, async, generators) into explicit runtime calls + IR operations.
3. Run optimization passes (constant folding, dead code elimination, inlining candidates, escape analysis hints).

### Backend
1. Emit JVM bytecode from IR using ASM.
2. Generate `.class` files and packaging (jar).
3. Emit debug metadata and source maps for stack trace mapping.

### Runtime Compatibility Layer
Required JVM library to model JS semantics:
1. Dynamic object model (property bag, prototype links).
2. JS primitives and coercions.
3. Function objects, closures, `this` binding behavior.
4. Promise/event-loop scheduler abstraction.
5. Module loader semantics (ESM/CommonJS interop policy).

## Compatibility Strategy

### Level 0 (Bootstrap)
TypeScript subset:
1. `let`/`const`, arithmetic, conditionals, loops, functions.
2. Simple classes without dynamic metaprogramming.
3. No async/generators/proxies/reflection.

### Level 1 (Core App Support)
1. Modules, imports/exports.
2. Closures and lexical capture.
3. Exceptions and stack traces.
4. Basic standard library surface (`Array`, `Map`, `Date`, JSON).

### Level 2 (Modern TS)
1. Async/await and Promise behavior.
2. Advanced class features.
3. Decorators (policy decision needed for stage/version behavior).
4. Improved type erasure and generic constraints for optimization hints.

### Level 3 (High Dynamic Features)
1. Proxy, reflection-heavy patterns.
2. `eval`/`Function` constructor (likely sandboxed/limited).
3. Broad npm ecosystem compatibility.

## Locked Architecture Decisions (v0.1)

### AD-01: Runtime Object Model
Decision:
1. Use `TsObject` runtime type with:
   - Own-properties map (`String -> TsValue`).
   - Prototype pointer (`TsObject | null`).
   - Optional property attributes structure (minimal for MVP).
2. Add inline property cache at generated call sites (monomorphic first).
3. Defer full hidden-class engine to post-MVP.

Why:
1. Delivers semantic correctness earlier than a full hidden-class implementation.
2. Keeps a performance path open via call-site caching without committing to complex shape transitions.

Consequences:
1. MVP is slower than optimized JS engines on heavy object workloads.
2. Runtime API boundary (`TsObject` interface) must remain stable so internal layout can evolve later.

### AD-02: Value and Numeric Model
Decision:
1. Canonical runtime value is `TsValue` (boxed dynamic value).
2. IR and bytecode emitter support primitive fast lanes:
   - `int32` lane for bitwise/index-heavy flows.
   - `double` lane for general JS `number`.
3. Box/unbox only at dynamic boundaries (property reads, generic calls, interop boundaries).

Why:
1. Pure boxing everywhere is too slow.
2. Pure primitive specialization is too complex for full JS semantics at MVP.

Consequences:
1. Compiler must track lane transitions explicitly in MIR.
2. Runtime must provide coercion helpers that are behaviorally identical to JS for supported types.

### AD-03: Module and Linking Strategy
Decision:
1. Resolve full module graph at compile time for MVP.
2. Emit one runnable jar containing:
   - Generated classes.
   - Runtime library.
   - Module initialization table.
3. Implement ESM-style live binding semantics for supported patterns.
4. Mark `dynamic import()` as unsupported in MVP (diagnostic error).

Why:
1. Static graph simplifies deterministic startup, packaging, and debugging.
2. Live bindings are required for semantic correctness across many TS codebases.

Consequences:
1. CLI needs deterministic module evaluation order logic.
2. Circular dependency behavior must be explicitly tested and documented.

### AD-04: Async Execution Model
Decision:
1. Lower `async/await` to explicit state machines in MIR.
2. Implement Promise + microtask queue in runtime with single-threaded event loop semantics by default.
3. Use JVM scheduler primitives only as a host mechanism, not as semantic truth.
4. Provide optional bridge adapters to/from `CompletableFuture` for interop.

Why:
1. JS async semantics depend on microtask ordering guarantees.
2. Direct `CompletableFuture` semantics differ from JS in scheduling details.

Consequences:
1. Runtime must own task queue and flush policy.
2. Differential tests need strict ordering assertions for async fixtures.

### AD-05: Java Interop Boundary
Decision:
1. Interop is opt-in and explicit:
   - TS calls Java through generated bridge stubs.
   - Java calls TS through runtime invocation APIs.
2. Use annotation/config-driven allowlist for callable Java packages/classes.
3. All boundary calls convert via `TsValue` codec layer.

Why:
1. Unrestricted reflection-based interop creates safety and maintainability risks.
2. Explicit bridge generation keeps diagnostics and types predictable.

Consequences:
1. Initial interop coverage is narrower but safer.
2. Tooling must generate and validate bridge metadata.

### AD-06: IR Architecture
Decision:
1. Use three compiler IR layers:
   - HIR: typed AST-near representation.
   - MIR: normalized CFG with explicit temporaries and control-flow.
   - JIR: JVM-oriented lowered form (stack/local layout resolved).
2. Put semantic rewrites in HIR->MIR, and performance rewrites in MIR passes.

Why:
1. Separates correctness rewrites from backend mechanics.
2. Keeps JVM backend replaceable if new targets are added later.

Consequences:
1. More upfront design work than a direct AST->bytecode emitter.
2. Better testability at each layer.

### AD-07: Unsupported Feature Policy
Decision:
1. Unsupported high-dynamic features fail at compile time with targeted diagnostics.
2. No silent fallback to partial semantics.
3. Maintain a feature matrix by compatibility level.

Why:
1. Predictable failure is safer than incorrect execution.
2. Teams need clear upgrade path as support expands.

Consequences:
1. Requires dedicated detection passes in frontend and MIR validation.
2. Story backlog must include feature-gating tests per release.

## Decision-Driven Implementation Order
1. Establish runtime core contracts (`TsValue`, `TsObject`, module table, scheduler API).
2. Finalize HIR/MIR/JIR schemas and serialization.
3. Deliver static modules + closures + classes before async.
4. Add async runtime/state machines after deterministic sync semantics are validated.
5. Expand interop and performance optimizations after conformance baseline is stable.

## Constraints and Risks
1. Semantic mismatch risk:
   - JS dynamic semantics versus JVM static model.
2. Performance risk:
   - Boxing, dynamic dispatch, and property lookup overhead.
3. Ecosystem risk:
   - npm packages depending on Node/browser globals.
4. Debuggability risk:
   - Mapping TS source to JVM stack traces.

## MVP Definition (First Usable Release)
Deliver a compiler that can compile and run:
1. Multi-file TS project with modules.
2. Functions, classes, closures, exceptions.
3. Deterministic test suite for semantic equivalence on selected fixtures.
4. CLI:
   - `tsj compile src --out build`
   - `tsj run src/main.ts`

Out of scope for MVP:
1. Full npm compatibility.
2. `eval` and Proxy.
3. Complete Node.js API surface.

## Validation Plan
1. Golden tests:
   - TS input, expected output behavior, expected stdout/stderr.
2. Differential tests:
   - Run same tests on Node.js and JVM backend; compare results.
3. Conformance buckets:
   - Language semantics tests by feature area.
4. Performance baseline:
   - Microbenchmarks and representative app benchmarks.

## Proposed Repository Shape
1. `compiler/frontend` (TS parser/type integration)
2. `compiler/ir` (IR model and passes)
3. `compiler/backend-jvm` (bytecode emission)
4. `runtime/jvm` (TS runtime support on JVM)
5. `cli` (user-facing compile/run tools)
6. `tests/fixtures` (language fixtures)
7. `tests/differential` (Node vs JVM comparisons)
