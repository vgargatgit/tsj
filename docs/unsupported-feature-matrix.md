# Supported TypeScript Grammar Matrix

This matrix tracks current TSJ grammar/runtime status with deterministic diagnostics for unsupported paths.

Status legend:
1. `Supported`: expected to compile/run in the documented scope.
2. `Subset`: supported with explicit scope/policy constraints.
3. `Unsupported`: deterministic failure with listed diagnostic code/feature ID.
4. `Out of Scope`: intentionally excluded from current closure target.

| Feature Area | Feature | Scope | Status | Diagnostic Code / Feature ID | Primary Coverage |
|---|---|---|---|---|---|
| Core grammar | Non-TSX TGTA `ok` fixtures (`*.ts`, `*.d.ts`) | `tsj compile` | Subset | `12/15` fixtures compile (`TSJ-COMPILE-SUCCESS`); known blockers emit `TSJ-BACKEND-PARSE` (`020`, `030`) and `TSJ-BACKEND-UNSUPPORTED` (`140`) | `TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess` + `TsjTgtaCompileGateTest#tgtaKnownFailingFixturesEmitStableDiagnosticCodes` |
| Core grammar | JSX/TSX parsing/execution | current TGTA closure | Out of Scope | current parse failure (`TS1005`) | `examples/tgta/src/ok/110_jsx.tsx` (excluded by scope) |
| Core grammar | Unary plus (`+x`) | backend parser path | Supported | `TSJ-COMPILE-SUCCESS` / `TSJ-RUN-SUCCESS` | `JvmBytecodeCompilerTest#supportsUnaryPlusExponentBitwiseShiftAndTypeRelationOperatorsInTsjGrammarPath` + `unsupported/grammar/001_unary_plus.ts` |
| Module grammar | Relative named imports (`import { x } from "./m.ts"`) | `tsj run` TSJ-12 path | Supported | `TSJ-RUN-SUCCESS` | `TsjCliTest#runSupportsAliasNamedImportInTsj12` |
| Module grammar | Default imports (`import x from "./m.ts"`) | `tsj run` TSJ-12 path | Supported | `TSJ-RUN-SUCCESS` | `TsjCliTest#runSupportsDefaultImportWithOptionalNamedBindingsInTsj22` |
| Module grammar | Namespace imports (`import * as ns from "./m.ts"`) | `tsj run` TSJ-12 path | Supported | `TSJ-RUN-SUCCESS` | `TsjCliTest#runSupportsNamespaceImportInTsj22` |
| Java interop grammar | Named `java:` imports | `tsj run`/`tsj compile` with interop policy | Subset | `TSJ-RUN-SUCCESS` / `TSJ-COMPILE-SUCCESS` | `TsjCliTest#runSupportsJavaInteropNamedImportsInTsj26` |
| Java interop grammar | Default/namespace import forms from `java:` modules | TSJ-26 interop syntax | Unsupported | `TSJ-BACKEND-UNSUPPORTED` / `TSJ26-INTEROP-SYNTAX` | `TsjCliTest#runRejectsDefaultJavaInteropImportWithFeatureDiagnosticMetadata` |
| Java interop grammar | Invalid `java:` module specifier forms (`#member` etc.) | TSJ-26 interop syntax | Unsupported | `TSJ-BACKEND-UNSUPPORTED` / `TSJ26-INTEROP-MODULE-SPECIFIER` | `TsjCliTest#runRejectsInvalidJavaInteropModuleSpecifierWithFeatureDiagnosticMetadata` |
| Async grammar | Async accessor/generator method variants (`async get/set`, `async *`) | TSJ-13b subset | Unsupported | `TSJ-BACKEND-UNSUPPORTED` | `JvmBytecodeCompilerTest#rejectsAsyncClassGetterMethodVariantWithTargetedDiagnostic` |
| Async grammar | Top-level `await` in while condition | TSJ-13f subset | Unsupported | `TSJ-BACKEND-UNSUPPORTED` | `TsjCliTest#runRejectsTopLevelAwaitInWhileConditionForNow` |
| Interop conversion | TS lambda -> Java SAM callback (including interfaces redeclaring `Object` methods) | runtime interop conversion | Supported | `TSJ-RUN-SUCCESS` | `TsjCliTest#runSupportsTsj53SamInteropForInterfaceThatRedeclaresObjectMethod` |
| Interop metadata | Selected-target metadata emission for SAM callback target | `tsj compile` broad/no-spec auto-interop | Supported | `interopBridges.selectedTarget*` persisted | `TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation` |
| Restricted runtime features | Dynamic import (`import("...")`) | TSJ-15 guardrail | Unsupported | `TSJ-BACKEND-UNSUPPORTED` / `TSJ15-DYNAMIC-IMPORT` | `TsjCliTest#runDynamicImportIncludesUnsupportedFeatureContext` |
| Restricted runtime features | `eval(...)` | TSJ-15 guardrail | Unsupported | `TSJ-BACKEND-UNSUPPORTED` / `TSJ15-EVAL` | `TsjCliTest#runEvalIncludesUnsupportedFeatureContext` |
| Restricted runtime features | `Function(...)` / `new Function(...)` | TSJ-15 guardrail | Unsupported | `TSJ-BACKEND-UNSUPPORTED` / `TSJ15-FUNCTION-CONSTRUCTOR` | `TsjCliTest#runFunctionConstructorIncludesUnsupportedFeatureContext` |
| Restricted runtime features | `new Proxy(...)` | TSJ-15 guardrail | Unsupported | `TSJ-BACKEND-UNSUPPORTED` / `TSJ15-PROXY` | `TsjCliTest#runProxyIncludesUnsupportedFeatureContext` |
| Runtime semantics | Bitwise/shift/exponentiation operators | run-path parity | Supported | `TSJ-RUN-SUCCESS` | `JvmBytecodeCompilerTest#supportsUnaryPlusExponentBitwiseShiftAndTypeRelationOperatorsInTsjGrammarPath` + `unsupported/grammar/002_exponentiation.ts` through `unsupported/grammar/007_bitwise_xor.ts` |
| Runtime semantics | `typeof`, `in`, `instanceof` | run-path parity | Supported | `TSJ-RUN-SUCCESS` | `JvmBytecodeCompilerTest#supportsUnaryPlusExponentBitwiseShiftAndTypeRelationOperatorsInTsjGrammarPath` + `unsupported/grammar/008_typeof_operator.ts` through `unsupported/grammar/010_instanceof_operator.ts` |

## Notes

1. This matrix intentionally separates compile-only closure status (TGTA non-TSX gate) from run-path subset constraints (TSJ-12/TSJ-26).
2. Interop policy checks (`strict`/`broad`, allowlist/denylist, approval/RBAC) are governance controls and tracked outside pure grammar rows.
3. `unsupported/run_progress.sh` is the operational progression indicator for remaining unsupported parity surfaces.
4. Add new grammar/interoperability features by:
   - adding/adjusting one matrix row,
   - linking deterministic coverage test(s),
   - documenting unsupported diagnostics when status is `Unsupported`.
