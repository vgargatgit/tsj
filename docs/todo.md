# TSJ TODOs (From TITA + Full TS Grammar Gap Analysis)

## Goal
- Reach full TypeScript grammar support without rewriting user TS source.
- Make TITA pass using native TS syntax and deterministic interop artifacts/diagnostics.

## Grammar/Parser TODOs
- [x] Add logical expressions to parser: `&&`, `||`, `??` with correct precedence and short-circuit lowering.
- [x] Add conditional expression parsing/lowering: `cond ? a : b`.
- [x] Add assignment-expression coverage beyond current subset: compound assignments and expression-position assignments.
- [x] Support multiline named imports and broader import forms without one-line normalization requirements.
- [x] Support optional chaining forms: `a?.b`, `a?.()`.
- [x] Support template literals (plain and expression interpolation).
- [x] Support destructuring in variable declarations, assignments, and parameters.
- [x] Support rest/spread syntax in arrays, objects, and call arguments.
- [x] Support default parameters and rest parameters in functions.
- [x] Support full loop grammar (`for..of`, `for..in`) and control-flow parity across lowering paths.
- [x] Support class grammar features still outside subset: computed keys, static blocks, field initializers, and private fields.
- [x] Support TS-only grammar constructs in frontend/backend bridge path (type-only imports/exports, `as const`, `satisfies`, assertion syntax).
- [x] TGTA non-TSX closure target: `tsj compile` returns `TSJ-COMPILE-SUCCESS` for all in-scope `examples/tgta/src/ok/*` files (`15/15` success for `*.ts` + `*.d.ts`; `110_jsx.tsx` intentionally excluded by scope).
- [x] Add bigint literal grammar support in backend parser (covered by `JvmBytecodeCompilerTest#supportsBigIntAndExtendedNumericLiteralFormsInTsjGrammarPath`).
- [x] Add top-level type grammar tolerance in backend pipeline (`type` aliases, conditional/mapped/template-literal/indexed types) so TGTA type fixtures compile successfully.
- [x] Add `declare`-context and ambient declaration tolerance in `.ts` and `.d.ts` compile paths (including no-op `.d.ts` compilation).
- [x] Add interface declaration tolerance for TGTA compile path.
- [x] Add enum declaration lowering in normalized bridge path for TGTA compile path.
- [x] Expand class/generic declaration tolerance used by TGTA fixtures (including `implements`, overload signatures, `return;`, non-null assertion, static-block `this` rewrite).
- [x] Add legacy/TS module import-export tolerance (`import = require`, default import, namespace/module forms) for TGTA compile path.
- [x] Add support path for ambient export form handling in declaration-file compilation.
- [x] Add decorator syntax tolerance so unknown decorators do not fail compilation in TGTA compile flow.
- [x] Add TS 5.x import-attributes/related module-form tolerance for TGTA compile flow.
- [x] Add CI regression gate that executes the TGTA non-TSX compile sweep and fails on any non-`TSJ-COMPILE-SUCCESS` result (`TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess` + `.github/workflows/ci.yml` step `TGTA Non-TSX Compile Gate`).

## Interop TODOs (TITA-Relevant)
- [x] Implement TS lambda/closure -> Java SAM adapter generation so calls like `run(MyFn, value)` work directly.
- [x] Ensure compile-time overload selection metadata is always emitted (`interopBridges.selectedTarget*`) for all selected interop calls (validated for broad-policy/no-spec auto-interop across multiline named `java:` imports, multiline named relative-import traversal, `java:` import-attributes clauses (`with`/`assert`), user-defined SAM callback invocation (`SamRunner#run(MyFn,String)`), constructor/instance bindings (`$new`, `$instance$...`), and TSJ29 field bindings (`$instance$get$...`, `$instance$set$...`, `$static$get$...`, `$static$set$...`)).
- [x] Complete deterministic overload tie-break parity and emit candidate/reason diagnostics for ambiguous calls.
- [x] Harden generic signature use in interop conversion and resolution for intersection bounds/wildcards (no silent erasure drift).
- [x] Complete nullability inference integration in overload ranking and conversion diagnostics.
- [x] Finalize property synthesis conflict handling with deterministic skip reasons (`getURL`/`getUrl`, overloaded setters, etc.).
- [x] Validate and persist MR-JAR winner provenance (`base` vs `META-INF/versions/*`) in class index/artifact metadata.
- [x] Keep `jrt:/` mixed-classpath handling deterministic across parse, artifact serialization, and run classloader paths.
- [x] Improve app-isolated conflict diagnostic payloads to always include both origins and actionable remediation.

## Runtime/Execution TODOs
- [x] Expand runtime invocation parity tests for null/undefined argument edges across static/instance/preselected paths.
- [x] Add regression tests for interop vararg + null literal behavior with and without preselected targets.
- [x] Ensure deterministic behavior for duplicate mediation winner rules in shared mode under reordered classpath inputs.

## Test & Conformance TODOs
- [x] Add a parser conformance suite that compiles raw TS fixtures (no normalization) and compares parse/lowering behavior against expected AST/IR snapshots (`TypeScriptSyntaxBridgeConformanceSnapshotTest` + committed snapshots under `compiler/backend-jvm/src/test/resources/ts-conformance/expected`).
- [x] Add end-to-end TITA fixture pack in both modes:
- [x] Shared mode: must pass and emit deterministic `class-index.json` + artifact metadata.
- [x] App-isolated mode: must fail with deterministic conflict code and origin details.
- [x] Add differential tests for Node vs TSJ on grammar-heavy fixtures (logical chains, optional chaining, destructuring, template literals) via CLI fixture harness parity tests.
- [x] Add CI gate for new grammar features: each new feature requires red-green tests in parser + backend + CLI fixture harness (workflow step `Grammar Feature Gate (Parser/Backend/CLI)`).

## Documentation TODOs
- [x] Publish a “Supported TypeScript Grammar Matrix” in `docs/unsupported-feature-matrix.md` with per-feature status and diagnostic code.
- [x] Add a TITA runbook documenting exact expected artifacts, diagnostics, and reproducibility checks (`docs/tita-runbook.md`).

## Review: TGTA Implementation (2026-02-22)
- Scope delivered: standalone TypeScript grammar torture app in `examples/tgta` with no TSJ-internal dependency for parsing/snapshot generation.
- Harness delivered: `examples/tgta/src/harness/parse_harness.ts` with deterministic AST/diagnostic snapshots, `--update`, and exit codes `0/1/2/3`.
- Grammar suites delivered: `examples/tgta/src/ok` (16 feature/stress files) and `examples/tgta/src/err` (4 negative/recovery files).
- Snapshot output delivered: `examples/tgta/fixtures/expected/ok/*.ast.json` and `examples/tgta/fixtures/expected/err/*.diag.json` (20 total snapshots).
- Add-a-feature workflow validated: one new grammar file in `src/ok` or `src/err` maps to one snapshot file in `fixtures/expected`.
- Verification command: `node --experimental-strip-types --test examples/tgta/src/harness/*.test.ts` passed (`5` tests, `0` failures).
- Verification command: `node --experimental-strip-types examples/tgta/src/harness/parse_harness.ts --update` passed (`checkedFiles=20`, `updatedSnapshots=20`, `exitCode=0`).
- Verification command: `node --experimental-strip-types examples/tgta/src/harness/parse_harness.ts` passed (`checkedFiles=20`, `mismatches=0`, `unexpectedStatuses=0`, `exitCode=0`).

## Review: TGTA -> TSJ Non-TSX Gap Closure (2026-02-22)
- Scope: `examples/tgta/src/ok/*.ts` and `examples/tgta/src/ok/*.d.ts` only (`110_jsx.tsx` excluded per explicit scope).
- Verification command:
  `find examples/tgta/src/ok -maxdepth 1 -type f \( -name '*.ts' -o -name '*.d.ts' \) | sort | while IFS= read -r f; do out=$(mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args="compile $f --out /tmp/tgta-tsj-check/$(basename "$f")" 2>&1 || true); diag=$(printf '%s\n' "$out" | rg '^\{"level":' | tail -n 1); code=$(printf '%s' "$diag" | sed -n 's/.*"code":"\([^"]*\)".*/\1/p'); printf '%s | %s\n' "$f" "${code:-NO_CODE}"; done`
- Result: `15/15` files returned `TSJ-COMPILE-SUCCESS`.
- Final blocker resolved in this pass: namespace value binding in `examples/tgta/src/ok/900_stress_mix.ts` (`StressSpace`) by lowering non-ambient namespace exports to runtime object declarations in the bridge.
- Regression coverage added:
  `TypeScriptSyntaxBridgeTest#normalizesNamespaceExportsIntoRuntimeObjectDeclarations`
  `JvmBytecodeCompilerTest#supportsNamespaceValueExportsInTsjGrammarPath`.
- CI gate added:
  `TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess`
  `.github/workflows/ci.yml` step `TGTA Non-TSX Compile Gate`.
- Gate verification command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess test` passed.

## Review: TITA Interop Metadata Stabilization (2026-02-22)
- Root cause reproduced: `TsjTitaInteropAppTest#sharedModeRunsTitaAndPersistsInteropArtifacts` failed because CLI skipped auto-interop bridge generation when `--interop-spec` was omitted, producing `interopBridges.selectedTargetCount=0`.
- Fix: CLI now synthesizes auto interop specs from discovered `java:` targets in broad-policy flows without explicit `--interop-spec`, then generates bridges and selected-target metadata.
- Compatibility guard: `run` command remaps bridge bootstrap class-not-found failures to `TSJ-RUN-006` and includes generated-spec target list in diagnostic context, preserving missing-jar contract.
- Regression coverage added:
  `TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec`.
- Verification command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjTitaInteropAppTest test` passed.
- Cross-check command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess test` passed.

## Review: Interop Discovery Multiline Coverage (2026-02-22)
- Root cause reproduced: auto-interop discovery was line-based, so multiline `import { ... } from "java:..."` and multiline named relative imports were skipped, leaving `discoveredTargets` incomplete.
- Fix: replaced line-by-line discovery with source-level static-import scanning in CLI, extracting named bindings from import clauses and traversing relative imports using parsed module specifiers.
- Regression coverage added:
  `TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForMultilineNamedJavaImportWithoutInteropSpec`
  `TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataThroughMultilineNamedRelativeImportWithoutInteropSpec`.
- Verification command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForMultilineNamedJavaImportWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataThroughMultilineNamedRelativeImportWithoutInteropSpec,TsjTitaInteropAppTest,TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess test` passed.

## Review: TITA Runbook Documentation (2026-02-22)
- Deliverable added: `docs/tita-runbook.md`.
- Scope covered: shared-mode success contract, app-isolated conflict failure contract (`TSJ-RUN-009`), missing-jar failure contract (`TSJ-RUN-006`), and reproducibility checks for `class-index.json` plus selected-target metadata.
- Source of truth alignment: expectations and markers were derived from `TsjTitaInteropAppTest`.

## Review: TSJ53 SAM Adapter Closure (2026-02-22)
- Root cause reproduced: runtime functional-interface detection rejected interfaces that redeclare `Object` methods (for example `toString()`), causing direct TS lambda -> user-SAM conversion to fail with unsupported conversion diagnostics.
- Fix: SAM detection in runtime conversion/scoring now ignores `Object`-contract signatures (`toString`, `hashCode`, `equals`) even when declared on the interface, while still requiring exactly one non-Object abstract method.
- Regression coverage added:
  `TsjCliTest#runSupportsTsj53SamInteropForInterfaceThatRedeclaresObjectMethod`.
- Verification command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runSupportsTsj53SamInteropForInterfaceThatRedeclaresObjectMethod,TsjCliTest#runSupportsTsj30InteropCallbackAndCompletableFutureAwait,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForMultilineNamedJavaImportWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataThroughMultilineNamedRelativeImportWithoutInteropSpec,TsjTitaInteropAppTest,TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess test` passed.

## Review: Selected-Target Metadata SAM Coverage (2026-02-22)
- Scope: compile-time selected-target metadata persistence for static interop call with user-defined SAM parameter (`sample.interop.SamRunner#run`).
- Regression coverage added:
  `TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation`.
- Verified metadata assertions:
  `interopBridges.selectedTargetCount=1`
  `binding=run`
  `owner=sample.interop.SamRunner`
  `descriptor=(Lsample/interop/SamRunner$MyFn;Ljava/lang/String;)Ljava/lang/String;`
  `invokeKind=STATIC_METHOD`.

## Review: Supported Grammar Matrix Publication (2026-02-22)
- Deliverable updated: `docs/unsupported-feature-matrix.md` now serves as a status-oriented supported grammar matrix (compile + run scopes).
- Rows include deterministic diagnostic mapping for unsupported paths (`TSJ15-*`, `TSJ22-*`, `TSJ26-*`) and explicit coverage references to existing regression tests/gates.
- Closure note: the documentation TODO for grammar matrix publication is now complete.

## Review: Overload Tie-Break Parity + Ambiguity Diagnostics (2026-02-22)
- Root cause reproduced: runtime interop overload selection still used lexicographic fallback on equal scores, which diverged from compile-time specificity behavior and hid true ambiguity.
- Runtime fix delivered (`TsjJavaInterop`):
  deterministic candidate ordering, specificity winner selection for equal-score candidates, and explicit ambiguity diagnostics including candidate/reason payloads.
- Runtime scoring refinement delivered:
  exact-instance matches now score as exact before collection coercion heuristics, and array-like `TsjObject` values are scored to prefer `List` over `Map` so TSJ41/TITA behavior remains stable.
- Backend determinism delivered (`JavaOverloadResolver`):
  class-method and constructor candidate enumeration is descriptor-sorted to keep compile-time diagnostics/metadata stable across reflection order.
- Regression coverage added:
  `TsjJavaInteropTest#invokeBindingPrefersMostSpecificReferenceOverloadWhenScoresTie`
  `TsjJavaInteropTest#invokeBindingFailsWithAmbiguousCandidateDiagnosticWhenNoSpecificityWinnerExists`
  `TsjJavaInteropTest#invokeBindingPrefersPrimitiveCandidateOverReferenceSupertypeOnTie`
  `JavaOverloadResolverTest#candidateEnumerationIsDeterministicByDescriptorForMethods`.
- Verification commands:
  `mvn -B -ntp -pl runtime -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjJavaInteropTest test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JavaOverloadResolverTest test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runSupportsTsj41AdvancedInteropConversionAndInvocationParity,TsjCliTest#runSupportsTsj53SamInteropForInterfaceThatRedeclaresObjectMethod,TsjCliTest#runSupportsTsj30InteropCallbackAndCompletableFutureAwait,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation,TsjTitaInteropAppTest,TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess test` passed.

## Review: Runtime Null/Undefined + Vararg Parity (2026-02-22)
- Scope delivered: runtime parity coverage for nullish argument conversion across static, instance, and preselected interop paths.
- Regression coverage added:
  `TsjInteropCodecTest#invokeBindingConvertsNullishArgumentsAcrossStaticInstanceAndPreselectedPaths`
  `TsjInteropCodecTest#invokeBindingPreservesNullLiteralBehaviorForVarargsWithAndWithoutPreselectedTarget`.
- Fixture coverage added in `InteropSample`:
  static/object and instance null-kind probes plus null-aware vararg target to validate argument conversion contracts.
- Behavioral contract locked:
  for `String...` varargs, `null` converts to the string literal `"null"` while `undefined` converts to Java `null`; both non-preselected and preselected invocation paths now share this behavior under regression tests.
- Verification commands:
  `mvn -B -ntp -pl runtime -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjInteropCodecTest,TsjJavaInteropTest test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTitaInteropAppTest#sharedModeRunsTitaAndPersistsInteropArtifacts test` passed.

## Review: Shared-Mode Mediation Reorder Determinism (2026-02-22)
- Scope delivered: shared-mode mediation parity now has an explicit reorder regression for duplicate-version mediation winners.
- Regression coverage added:
  `TsjCliTest#runKeepsNearestMediationWinnerStableWhenSharedClasspathInputsAreReordered`.
- Verified invariants across reordered `--classpath` inputs for the nearest-rule transitive graph:
  runtime output remains `near->shared-v1` and `far->shared-v1`,
  mediation metadata remains stable (`artifact=sample.graph:shared-lib`, `selectedVersion=1.0.0`, `rejectedVersion=2.0.0`, `rule=nearest`),
  and rejected version path is consistently excluded from persisted resolved classpath entries.
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runMediatesTransitiveDependencyGraphUsingNearestRule,TsjCliTest#runKeepsNearestMediationWinnerStableWhenSharedClasspathInputsAreReordered,TsjCliTest#runMediatesSameDepthConflictsUsingRootOrderTiebreak test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjDependencyMediationCertificationTest#certificationGateRequiresGraphScopeAndIsolationParity test` passed.

## Review: Selected-Target Metadata Import-Attributes Coverage (2026-02-22)
- Root cause reproduced: import discovery required `;` immediately after module specifier, so `import { ... } from "java:..." with { ... };` compiled but skipped auto-interop target discovery, yielding `interopBridges.selectedTargetCount=0`.
- CLI fix delivered: `IMPORT_STATEMENT_PATTERN` now accepts optional `with { ... }` / `assert { ... }` clauses for both binding and side-effect import forms before the terminating semicolon.
- Regression coverage added:
  `TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAttributesWithoutInteropSpec`
  `TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAssertionsWithoutInteropSpec`.
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForMultilineNamedJavaImportWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataThroughMultilineNamedRelativeImportWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAttributesWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAssertionsWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation,TsjTitaInteropAppTest,TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess test` passed.

## Review: `jrt:/` Mixed Classpath Determinism (2026-02-22)
- Scope delivered: added runtime round-trip regression to lock deterministic mixed classpath behavior across option parse, artifact serialization, and reparse for run classloader setup.
- Regression coverage added:
  `TsjCliTest#runRoundTripsMixedJarAndJrtClasspathEntriesDeterministically`.
- Contract verified:
  mixed `jar + jrt:/java.base/java/lang` run succeeds, artifact classpath persists canonical `jrt:/...`, and re-running with classpath reconstructed from artifact metadata produces identical persisted classpath entries.
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runRoundTripsMixedJarAndJrtClasspathEntriesDeterministically test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileSupportsJrtClasspathEntriesInSymbolIndex,TsjCliTest#compileSupportsMixedJarAndJrtClasspathEntries,TsjCliTest#runAcceptsJrtClasspathEntries,TsjCliTest#runRoundTripsMixedJarAndJrtClasspathEntriesDeterministically test` passed.

## Review: App-Isolated Conflict Diagnostic Payloads (2026-02-22)
- Scope delivered: strengthened `TSJ-RUN-009` diagnostics to include actionable remediation while preserving deterministic origin metadata for app-isolated duplicate-class conflicts.
- CLI payload update delivered (`TsjCli` app-isolated conflict catch path):
  added `conflictClass` as a stable alias for the conflicting internal class name, and added `guidance` with remediation that references `--classloader-isolation shared` for intentional shadowing cases.
- Regression coverage tightened:
  `TsjCliTest#runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode`
  `TsjTitaInteropAppTest#appIsolatedModeFailsFastWithDeterministicConflictDiagnostic`
  now require `appOrigin`, `dependencyOrigin`, and actionable guidance in emitted diagnostics.
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode,TsjTitaInteropAppTest#appIsolatedModeFailsFastWithDeterministicConflictDiagnostic test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runSupportsAppIsolatedClassloaderModeForInteropDependencies,TsjCliTest#runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode,TsjTitaInteropAppTest#sharedModeRunsTitaAndPersistsInteropArtifacts,TsjTitaInteropAppTest#appIsolatedModeFailsFastWithDeterministicConflictDiagnostic test` passed.

## Review: MR-JAR Winner Provenance (2026-02-22)
- Scope delivered: classpath symbol indexing now deterministically resolves MR-JAR class winners against runtime feature version and persists provenance (`base` vs `META-INF/versions/<n>`).
- Indexing fix delivered (`ClasspathSymbolIndexer`):
  jar scanning now ignores non-class `META-INF/*` entries, normalizes versioned class entries to their real internal names, applies MR-JAR winner selection (highest eligible version in multi-release jars, otherwise base), and writes winner provenance via `origin.mrJarSource`.
- Artifact metadata persistence delivered (`TsjCli`):
  added class-index aggregate properties:
  `interopClasspath.classIndex.mrJarWinnerCount`
  `interopClasspath.classIndex.mrJarBaseWinnerCount`
  `interopClasspath.classIndex.mrJarVersionedWinnerCount`.
- Regression coverage tightened:
  `TsjTitaInteropAppTest#sharedModeRunsTitaAndPersistsInteropArtifacts`
  now asserts `dev/tita/fixtures/mr/MrPick` resolves to `META-INF/versions/11/...` with `mrJarSource=META-INF/versions/11`, rejects pseudo-internal-name leakage for versioned paths, and checks MR-JAR winner counts in artifact metadata.
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTitaInteropAppTest#sharedModeRunsTitaAndPersistsInteropArtifacts,TsjTitaInteropAppTest#appIsolatedModeFailsFastWithDeterministicConflictDiagnostic test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compilePersistsClasspathSymbolIndexWithShadowDiagnosticsInSharedMode,TsjCliTest#compileSupportsJrtClasspathEntriesInSymbolIndex,TsjCliTest#compileSupportsMixedJarAndJrtClasspathEntries,TsjCliTest#runAcceptsJrtClasspathEntries,TsjCliTest#runRoundTripsMixedJarAndJrtClasspathEntriesDeterministically,TsjCliTest#runSupportsAppIsolatedClassloaderModeForInteropDependencies,TsjCliTest#runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode,TsjTitaInteropAppTest#sharedModeRunsTitaAndPersistsInteropArtifacts,TsjTitaInteropAppTest#appIsolatedModeFailsFastWithDeterministicConflictDiagnostic test` passed.

## Review: Generic Signature Bounds Hardening (2026-02-22)
- Scope delivered: interop conversion now preserves wildcard `? super` lower bounds and enforces all declared type-variable bounds during runtime candidate resolution.
- Runtime conversion fix delivered (`TsjInteropCodec`):
  conversion now has explicit `TypeVariable`/`WildcardType` paths;
  wildcard `? super` selects lower bounds before upper `Object` fallback;
  intersection bounds are validated after conversion so values must satisfy every declared bound.
- Regression coverage added (`TsjInteropCodecTest`):
  `invokeBindingUsesWildcardSuperLowerBoundDuringGenericListElementConversion`
  `invokeBindingRejectsIntersectionTypeVariableWhenSecondaryBoundIsNotSatisfied`.
- Verification commands:
  `mvn -B -ntp -pl runtime -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjInteropCodecTest#invokeBindingUsesWildcardSuperLowerBoundDuringGenericListElementConversion,TsjInteropCodecTest#invokeBindingRejectsIntersectionTypeVariableWhenSecondaryBoundIsNotSatisfied test` passed.
  `mvn -B -ntp -pl runtime -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjInteropCodecTest,TsjJavaInteropTest test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runSupportsTsj41bGenericTypeAdaptationParity,TsjCliTest#runReportsTsj41bGenericAdaptationFailureWithTargetTypeContext test` passed.

## Review: Nullability Inference Integration for Binding Args Resolution (2026-02-22)
- Scope delivered: interop target-selection overload ranking now consumes analyzed Java nullability states instead of treating every parameter as platform-nullable.
- Integration delivered:
  `InteropBridgeGenerator` now builds deterministic per-class method nullability maps from classfile metadata (`JavaClassfileReader` + `JavaNullabilityAnalyzer`) and passes them into `JavaOverloadResolver` candidate generation;
  `JavaOverloadResolver` now accepts per-method nullability maps and applies them to parameter conversion checks.
- Regression coverage added:
  `InteropBridgeGeneratorTest#bindingArgsNullRespectsNonNullParameterNullabilityDuringSelection`,
  with fixture `InteropNullabilityFixtureType#requireNonNull(@NotNull String)` and test annotation shim `org.jetbrains.annotations.NotNull` (CLASS retention).
- Behavioral contract locked:
  for `bindingArgs.<binding>=null`, NON_NULL parameters are now rejected during TSJ-54 selected-target resolution with deterministic nullability diagnostics.
- Verification commands:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InteropBridgeGeneratorTest#bindingArgsNullRespectsNonNullParameterNullabilityDuringSelection test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JavaOverloadResolverTest,InteropBridgeGeneratorTest#emitsSelectedTargetIdentityMetadataForDisambiguatedOverloadArgs,InteropBridgeGeneratorTest#bindingArgsFailsWhenNoApplicableCandidateExists,InteropBridgeGeneratorTest#bindingArgsFailsWhenBestCandidatesAreAmbiguous,InteropBridgeGeneratorTest#bindingArgsNullRespectsNonNullParameterNullabilityDuringSelection test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation test` passed.

## Review: Property Synthesis Deterministic Conflict Reasons (2026-02-22)
- Scope delivered: property synthesis now emits deterministic, signature-stable skip reasons for conflict shapes including overloaded setters and mixed-casing accessor aliases (`getURL`/`getUrl`).
- Synthesizer fix delivered (`JavaPropertySynthesizer`):
  property candidates are grouped by canonical lowercase key;
  casing aliases on the same canonical property now skip synthesis with deterministic alias list diagnostics;
  ambiguous getter/setter diagnostics now include sorted method signatures (for example `setMode(I)V`, `setMode(J)V`) for reproducibility.
- Regression coverage added (`JavaPropertySynthesizerTest`):
  tightened overload reason assertion in `skipsAmbiguousPropertyShapesWithExplainableDiagnostics`;
  new `skipsGetterAliasCasingConflictsWithDeterministicReason` for `getURL/getUrl` conflict handling.
- Verification commands:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JavaPropertySynthesizerTest test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JavaPropertySynthesizerTest,JavaOverloadResolverTest,InteropBridgeGeneratorTest#bindingArgsNullRespectsNonNullParameterNullabilityDuringSelection test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation test` passed.

## Review: Selected-Target Metadata Constructor/Instance Coverage (2026-02-22)
- Scope delivered: broadened selected-target metadata regression coverage to include non-static interop bindings under broad/no-spec auto-discovery.
- Regression coverage added:
  `TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForConstructorAndInstanceBindings`.
- Contract verified:
  compile now explicitly asserts metadata emission for both constructor (`$new` -> `CONSTRUCTOR`) and instance method (`$instance$echo` -> `INSTANCE_METHOD`) selected targets, with order-insensitive validation.
- Verification command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForConstructorAndInstanceBindings,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation test` passed.

## Review: Selected-Target Metadata Field Binding Coverage (2026-02-22)
- Scope delivered: selected-target metadata regression coverage now includes TSJ29 field binding imports in broad/no-spec auto-interop flows.
- Regression coverage added:
  `TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForFieldBindings`.
- Contract verified:
  compile now asserts selected-target identity metadata for all mixed field-binding kinds (`INSTANCE_FIELD_GET`, `INSTANCE_FIELD_SET`, `STATIC_FIELD_GET`, `STATIC_FIELD_SET`) plus constructor selection in one fixture, with descriptor/owner checks and `unresolvedTargetCount=0`.
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForFieldBindings test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForMultilineNamedJavaImportWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataThroughMultilineNamedRelativeImportWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAttributesWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAssertionsWithoutInteropSpec,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForConstructorAndInstanceBindings,TsjCliTest#compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForFieldBindings test` passed.

## Review: End-to-End TITA Fixture Pack Determinism (2026-02-22)
- Scope delivered: end-to-end TITA fixture pack coverage now includes deterministic repeated-run assertions for both shared and app-isolated classloader modes.
- Regression coverage added:
  `TsjTitaInteropAppTest#sharedModeIsDeterministicForClassIndexAndSelectedTargetMetadataAcrossRepeatedRuns`
  `TsjTitaInteropAppTest#appIsolatedModeConflictDiagnosticIsDeterministicAcrossRepeatedRuns`.
- Shared-mode contract verified:
  repeated runs to the same output directory preserve byte-for-byte `class-index.json` content and selected-target identity metadata snapshots (`interopBridges.selectedTarget.*`) while keeping class-index aggregate counters stable.
- App-isolated contract verified:
  repeated runs fail with `TSJ-RUN-009` and stable conflict payload fields (`className`, `conflictClass`, `appOrigin`, `dependencyOrigin`).
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTitaInteropAppTest#sharedModeIsDeterministicForClassIndexAndSelectedTargetMetadataAcrossRepeatedRuns,TsjTitaInteropAppTest#appIsolatedModeConflictDiagnosticIsDeterministicAcrossRepeatedRuns test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTitaInteropAppTest test` passed.

## Review: Raw TS Parser Conformance Snapshot Suite (2026-02-22)
- Scope delivered: backend now has a raw TypeScript fixture conformance harness that snapshots parser AST-node streams plus normalized lowering output for TGTA non-TSX fixtures.
- Harness added:
  `TypeScriptSyntaxBridgeConformanceSnapshotTest#tgtaRawFixturesMatchBridgeConformanceSnapshots`.
- Snapshot corpus added:
  `compiler/backend-jvm/src/test/resources/ts-conformance/expected/{ok,err}/*.snapshot.json` (`19` files: `15` ok + `4` err; TSX intentionally excluded by scope).
- Determinism contract:
  snapshots canonicalize JSON object key ordering and capture `diagnostics`, `astNodes` spans/kinds, and `normalizedProgram`; update mode is explicit via `-Dtsj.updateConformanceSnapshots=true`.
- Verification commands:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtsj.updateConformanceSnapshots=true -Dtest=TypeScriptSyntaxBridgeConformanceSnapshotTest#tgtaRawFixturesMatchBridgeConformanceSnapshots test` passed (snapshot generation).
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TypeScriptSyntaxBridgeConformanceSnapshotTest#tgtaRawFixturesMatchBridgeConformanceSnapshots test` passed (normal mode).
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TypeScriptSyntaxBridgeTest,TypeScriptSyntaxBridgeConformanceSnapshotTest test` passed.

## Review: Grammar Differential Fixtures (Node vs TSJ) (2026-02-22)
- Scope delivered: CLI fixture harness now covers grammar-heavy Node-vs-TSJ parity scenarios for logical chains, optional chaining, destructuring, and template literals.
- Regression coverage added:
  `FixtureHarnessTest#harnessSupportsLogicalChainsDifferentialFixture`
  `FixtureHarnessTest#harnessSupportsOptionalChainingDifferentialFixture`
  `FixtureHarnessTest#harnessSupportsDestructuringDifferentialFixture`
  `FixtureHarnessTest#harnessSupportsTemplateLiteralDifferentialFixture`.
- Fixture implementation detail:
  a strict-parity helper now emits deterministic fixture specs with `assert.nodeMatchesTsj=true` and exact expected stdout for both runtimes.
- Verification command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FixtureHarnessTest#harnessSupportsLogicalChainsDifferentialFixture,FixtureHarnessTest#harnessSupportsOptionalChainingDifferentialFixture,FixtureHarnessTest#harnessSupportsDestructuringDifferentialFixture,FixtureHarnessTest#harnessSupportsTemplateLiteralDifferentialFixture test` passed.

## Review: Grammar Feature CI Gate (2026-02-22)
- Scope delivered: CI now has an explicit grammar-feature gate step that enforces parser/bridge, backend lowering, and CLI fixture-harness differential coverage in one command.
- Workflow update:
  `.github/workflows/ci.yml` step `Grammar Feature Gate (Parser/Backend/CLI)`.
- Gate command coverage:
  parser bridge regression suite (`TypeScriptSyntaxBridgeTest`),
  raw TS conformance snapshots (`TypeScriptSyntaxBridgeConformanceSnapshotTest`),
  backend grammar compile regression (`JvmBytecodeCompilerTest#supportsNamespaceValueExportsInTsjGrammarPath`),
  CLI differential grammar fixtures (logical/optional/destructuring/template tests in `FixtureHarnessTest`).
- Verification command:
  `mvn -B -ntp -pl compiler/backend-jvm,cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TypeScriptSyntaxBridgeTest,TypeScriptSyntaxBridgeConformanceSnapshotTest,JvmBytecodeCompilerTest#supportsNamespaceValueExportsInTsjGrammarPath,FixtureHarnessTest#harnessSupportsLogicalChainsDifferentialFixture,FixtureHarnessTest#harnessSupportsOptionalChainingDifferentialFixture,FixtureHarnessTest#harnessSupportsDestructuringDifferentialFixture,FixtureHarnessTest#harnessSupportsTemplateLiteralDifferentialFixture test` passed.
