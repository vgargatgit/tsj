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

Run fixture harness:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="fixtures tests/fixtures"
```

CLI command contract is in `docs/cli-contract.md`.

TSJ-7 compile now emits generated classes under `<out>/classes`, and `tsj run` executes the generated JVM class before emitting run diagnostics.
TSJ-8 extends the same path with lexical closure support for nested function declarations and mutable captured locals.
TSJ-9 extends the same path with class constructors/methods, inheritance via `extends`/`super(...)`, and object literal property access/assignment.

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

## Project Planning Docs

- Backlog and story sequencing: `docs/stories.md`
- Architecture decisions: `docs/architecture-decisions.md`
- Story-to-architecture mapping: `docs/story-architecture-map.md`
- Runtime contracts: `docs/contracts/runtime-contracts-v0.1.md`

## Development Approach

This repository follows comprehensive TDD flow (red -> green -> refactor), as documented in `AGENTS.md`.
