# IR Contract (TSJ-5 v0.1)

## Scope
TSJ-5 defines the initial three-tier IR pipeline and debug serialization.

## Tier Responsibilities
1. `HIR` (`dev.tsj.compiler.ir.hir`)
   - AST-near, source-oriented statements.
   - Captures top-level subset statements (`VAR_DECL`, `PRINT`, `IMPORT`).
2. `MIR` (`dev.tsj.compiler.ir.mir`)
   - Normalized instruction layer (`CONST`, `PRINT`, `IMPORT`, `RETURN`, `FUNCTION_DEF`, `CAPTURE`).
   - Explicit control-flow metadata:
     - `MirBasicBlock`
     - `MirControlFlowEdge`
   - Lexical metadata:
     - `MirScope`
     - `MirCapture`
   - One module-init function plus declared function entries in v0.1 bootstrap.
3. `JIR` (`dev.tsj.compiler.ir.jir`)
   - JVM-oriented pseudo-bytecode plan for backend handoff.
   - Class/method structure with ordered bytecode-op strings.

## Pipeline Entry
Service:
- `dev.tsj.compiler.ir.IrLoweringService`

Method:
- `IrProject lowerProject(Path tsconfigPath)`

Input:
1. Path to TypeScript project `tsconfig.json`.

Output:
1. `IrProject` with:
   - `hir`
   - `mir`
   - `jir`
   - `diagnostics`

## Frontend Integration
`IrLoweringService` delegates parsing/type-checking to TSJ-4 frontend:
1. Invokes `TypeScriptFrontendService`.
2. Converts frontend diagnostics to IR diagnostics (`stage=FRONTEND`).
3. Lowers source files into HIR statements for the bootstrap subset.

## Dump/Print Tool
Tool class:
- `dev.tsj.compiler.ir.IrDumpTool`

Capabilities:
1. Prints IR JSON to stdout.
2. Writes IR JSON to a file via `--out`.
3. Uses pretty JSON serialization (`IrJsonPrinter`).

Programmatic API:
- `IrDumpTool.dumpProject(Path workspaceRoot, Path tsconfigPath, Path outFile)`

## TSJ-6 Guarantees
1. Each MIR function contains explicit CFG edges.
2. Nested function scopes are represented with parent linkage.
3. Captured variables from ancestor scopes are recorded in `MirCapture`.
