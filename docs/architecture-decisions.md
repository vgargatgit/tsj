# TypeScript to JVM Compiler: Architecture Decisions (v0.1)

## Status Legend
- `Accepted`: locked for MVP.
- `Deferred`: intentionally postponed.

## Normative References
1. Runtime contracts: `docs/contracts/runtime-contracts-v0.1.md`
2. Story traceability matrix: `docs/story-architecture-map.md`

## AD-01 Runtime Object Model
- Status: `Accepted`
- Decision:
  - Runtime object is `TsObject` with own-properties map, prototype pointer, and minimal property metadata.
  - Generated call sites use monomorphic inline caches for property access.
- Deferred:
  - Full hidden-class / shape-transition object engine.

## AD-02 Value and Numeric Semantics
- Status: `Accepted`
- Decision:
  - Dynamic boundary type is `TsValue`.
  - Compiler and backend use primitive lanes (`int32`, `double`) where provable.
  - Boxing/unboxing happens only at dynamic boundaries.
- Deferred:
  - Global JIT-like specialization/deoptimization framework.

## AD-03 Module and Linking
- Status: `Accepted`
- Decision:
  - Resolve module graph at compile time.
  - Package app + runtime as runnable jar.
  - Support ESM live bindings for MVP subset.
  - `dynamic import()` is unsupported in MVP.
- Deferred:
  - Runtime dynamic module loading.

## AD-04 Async Model
- Status: `Accepted`
- Decision:
  - Lower `async/await` to explicit state machines.
  - Promise runtime uses single-threaded microtask semantics.
  - Host scheduler (JVM executors) is implementation detail only.
- Deferred:
  - Multi-loop/event-source emulation matching full Node/browser behavior.

## AD-05 Java Interop
- Status: `Accepted`
- Decision:
  - Interop is explicit and opt-in through generated bridge stubs.
  - Allowlist-driven target classes/packages.
  - Boundary conversions through `TsValue` codecs.
- Deferred:
  - Reflection-open arbitrary classpath interop by default.

## AD-06 IR Layering
- Status: `Accepted`
- Decision:
  - HIR: typed AST-near.
  - MIR: normalized control-flow IR.
  - JIR: JVM-lowered representation with stack/local mapping.
- Deferred:
  - Additional backend targets before JVM backend stabilizes.

## AD-07 Unsupported Feature Policy
- Status: `Accepted`
- Decision:
  - Unsupported features fail at compile time with specific diagnostics.
  - No partial fallback execution for unsupported semantics.
  - Maintain compatibility-level feature matrix.
- Deferred:
  - Best-effort runtime polyfill fallback for non-trivial semantics.

## AD-08 Delivery Boundary for MVP
- Status: `Accepted`
- Decision:
  - MVP guarantees deterministic behavior for declared subset only.
  - Conformance is judged by differential tests against Node on selected fixture buckets.
  - Performance is tracked but not a blocker unless severe regressions occur.
- Deferred:
  - Full npm or Node API compatibility claims.
