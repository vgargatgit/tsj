# Frontend Contract (TSJ-4 v0.1)

## Scope
TSJ-4 integrates the TypeScript parser and type-checker through `TypeScriptFrontendService`.

## Service API
Class:
- `dev.tsj.compiler.frontend.TypeScriptFrontendService`

Method:
- `analyzeProject(Path tsconfigPath): FrontendAnalysisResult`

Input:
1. Absolute or relative path to `tsconfig.json`.

Output:
1. `tsconfigPath`: normalized absolute config path.
2. `sourceFiles`: typed summaries for project source files.
3. `diagnostics`: TypeScript parser/type diagnostics with source coordinates.

## Data Models
1. `FrontendAnalysisResult`
2. `FrontendSourceFileSummary`
3. `FrontendDiagnostic`

## Bridge Runtime
Node bridge script:
- `compiler/frontend/ts-bridge/analyze-project.cjs`

Behavior:
1. Loads TypeScript compiler API from local `typescript` package when available.
2. Falls back to global `tsc` installation layout when local package is unavailable.
3. Parses `tsconfig.json` with TypeScript API.
4. Creates a `Program`, computes source-file node counts and typed-node counts.
5. Returns parser and semantic diagnostics with file/line/column mapping.

## TSJ-4 Acceptance Mapping
1. Loads `tsconfig.json`: covered by `TypeScriptFrontendService.analyzeProject`.
2. Produces typed AST summaries for all project source files: covered by `sourceFiles` with non-zero `typedNodeCount`.
3. Surfaces diagnostics with file/line mapping: covered by `FrontendDiagnostic`.
