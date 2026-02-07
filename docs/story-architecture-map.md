# Story to Architecture Decision Map

This matrix is the traceability artifact for TSJ-0 acceptance criteria.
It maps backlog stories to the architecture decisions in `docs/architecture-decisions.md`.

## Legend
- `P`: primary decision driver
- `S`: secondary influence

## Matrix

| Story | AD-01 | AD-02 | AD-03 | AD-04 | AD-05 | AD-06 | AD-07 | AD-08 | Notes |
|---|---|---|---|---|---|---|---|---|---|
| TSJ-0 | P | P | P | P | P | P | P | P | Publish ADRs and runtime contracts |
| TSJ-1 | S | S | S | S | S | P | S | S | Monorepo must reflect compiler/runtime boundaries |
| TSJ-2 | S | S | P | S | S | S | P | P | CLI contract exposes compile/run and diagnostics |
| TSJ-3 | S | S | S | S | S | S | P | P | Differential harness enforces declared boundaries |
| TSJ-4 | S | S | S | S | S | P | P | S | Frontend must emit unsupported-feature diagnostics |
| TSJ-5 | S | S | S | S | S | P | S | S | HIR/MIR/JIR design is AD-06 core |
| TSJ-6 | S | S | S | S | S | P | S | S | CFG and capture metadata support lowering constraints |
| TSJ-7 | S | P | S | S | S | P | S | S | JIR->bytecode and primitive lanes |
| TSJ-8 | P | S | S | S | S | P | S | S | Closures and `this` binding align with runtime model |
| TSJ-9 | P | S | S | S | S | P | S | S | Class/object lowering depends on TsObject contract |
| TSJ-10 | S | P | S | S | S | S | S | S | Coercion/equality semantics |
| TSJ-11 | P | S | S | S | S | S | S | S | Prototype lookup and inline cache behavior |
| TSJ-12 | S | S | P | S | S | S | S | P | Static linking and live bindings |
| TSJ-13 | S | S | S | P | S | P | S | S | Async lowering + scheduler semantics |
| TSJ-14 | S | S | S | S | S | S | S | P | Debuggability promise for MVP subset |
| TSJ-15 | S | S | P | P | S | S | P | P | Unsupported feature policy enforcement |
| TSJ-16 | S | S | S | S | S | S | P | P | Conformance suite validates decision envelope |
| TSJ-17 | S | P | S | S | S | P | S | S | Optimization passes on MIR/JIR |
| TSJ-18 | S | S | S | S | S | S | S | P | SLA tracked against MVP boundary |
| TSJ-19 | S | S | S | S | P | S | P | S | Interop bridge and allowlist model |

## Change Control
1. Any new story must be added to this matrix before implementation starts.
2. Any ADR update requires a matrix review in the same change.
3. CI should eventually validate that every story references at least one AD.

