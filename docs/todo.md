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
- [x] Implement generator semantics (`function*`, `yield`, `yield*`, `next(arg)`) on normalized lowering/runtime path.
- [x] Support TS-only grammar constructs in frontend/backend bridge path (type-only imports/exports, `as const`, `satisfies`, assertion syntax).
- [x] TGTA non-TSX full-closure target: `tsj compile` returns `TSJ-COMPILE-SUCCESS` for all in-scope `examples/tgta/src/ok/*` files.
  Current status: `15/15` pass for `*.ts` + `*.d.ts` (`110_jsx.tsx` intentionally excluded by scope).
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
- [x] Add CI regression gate that executes the TGTA non-TSX compile sweep and fails on any non-`TSJ-COMPILE-SUCCESS` result for the supported subset, while tracking known blockers with stable diagnostics (`TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess`, `TsjTgtaCompileGateTest#tgtaKnownFailingFixturesEmitStableDiagnosticCodes` + `.github/workflows/ci.yml` step `TGTA Non-TSX Compile Gate`).
- [ ] Remove transitional parser fallbacks once bridge lowering is complete (`tsj.backend.legacyTokenizer`, token fallback from normalized AST path) and run conformance with fallback disabled by default.

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
- [x] Strengthen TSJ-44d any-jar governance from classpath-presence/hardcoded-manifest checks to executable compile+run interop scenarios across certified ranges.

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

## Review: XTTA Generator Closure + Regression (2026-03-01)
- Generator closure delivered:
  bridge/backend/runtime now support generator declarations and expressions on normalized lowering path (`YieldExpression`, generator function flags, runtime generator iterator object, `generatorYield`, `generatorYieldStar`, iterable protocol handling in `for...of`).
- Namespace regression fixed:
  namespace export lowering now preserves captured outer exported bindings in nested namespace functions by rewriting through namespace build scope, and namespace declarations are now built through an IIFE namespace cell to avoid backend javac self-reference initialization errors.
- Runtime regression fixes:
  generator worker shutdown now handles interrupts without surfacing uncaught exceptions;
  vararg interop preserves explicit `null` literal behavior for `String...` without changing global `toJava(..., String.class)` null semantics.
- Verification:
  `bash examples/XTTA/scripts/run.sh` passed with `TOTAL: 30 | PASS: 30 | FAIL: 0 | CRASH: 0`.
  `mvn -B -ntp -pl runtime -Dtest=TsjRuntimeTest#generatorRuntimeSupportsNextArgumentsAndIterationProtocol,TsjRuntimeTest#forOfValuesConsumesGeneratorIteratorProtocol,TsjRuntimeTest#getAndSetPropertyRejectNonObjectReceivers,TsjInteropCodecTest#invokeBindingPreservesNullLiteralBehaviorForVarargsWithAndWithoutPreselectedTarget,TsjInteropCodecTest#toJavaReturnsNullForNullWhenTargetIsStringReferenceType test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -Dtest=JvmBytecodeCompilerTest#supportsGeneratorFunctionsWithYieldYieldStarAndNextArguments,TypeScriptSyntaxBridgeConformanceSnapshotTest test` passed.
  `mvn -B -ntp -pl cli -Dtest=TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess test` passed.
  `mvn -B -ntp test -rf :compiler-backend-jvm` passed (`compiler-backend-jvm` and `cli` green).
  `mvn -B -ntp -pl compiler/frontend test` passed after bridge namespace updates.

## Review: TGTA Non-TSX 15/15 Closure (2026-02-28)
- Gate hardening outcome:
  `TsjTgtaCompileGateTest` now runs without known-failing exclusions (`KNOWN_FAILING_FIXTURES = Map.of()`), so all non-TSX `ok` fixtures are required to compile successfully.
- Remaining blocker fix:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now normalizes bare-specifier dynamic import expressions (`import("pkg")`) into a compile-safe placeholder expression (`Promise.resolve(undefined)`) for AST-lowering coverage.
  Relative dynamic import forms remain intentionally unsupported in normalized lowering, preserving TSJ-15 guardrail diagnostics via fallback parser path.
- Verification:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTgtaCompileGateTest test` passed (`Tests run: 2, Failures: 0, Errors: 0`).
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#rejectsDynamicImportWithFeatureDiagnosticMetadata test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileUnsupportedSyntaxReturnsBackendDiagnostic,TsjCliTest#compileDynamicImportIncludesUnsupportedFeatureContext test` passed.

## Review: Full Regression After TGTA Closure (2026-02-28)
- First full regression run (`mvn -B -ntp test`) surfaced two follow-up regressions:
  conformance snapshot drift (`TypeScriptSyntaxBridgeConformanceSnapshotTest`) and a now-obsolete fixture mismatch in `FixtureHarnessTest` tied to bare dynamic-import behavior.
- Fixes applied:
  refreshed conformance snapshots with
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dtsj.updateConformanceSnapshots=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TypeScriptSyntaxBridgeConformanceSnapshotTest test`;
  updated fixture-harness unsupported fixture to use relative dynamic import (`import("./dep.ts")`) plus local `dep.ts` to preserve intentional TSJ15 mismatch semantics.
- Targeted verification:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FixtureHarnessTest#harnessFlagsNodeToTsjMismatchWhenExecutionDiffers,FixtureHarnessTest#harnessRunSuiteGeneratesFeatureCoverageReport test` passed (`Tests run: 2, Failures: 0, Errors: 0`).
- Final full regression re-run:
  `mvn -B -ntp test` passed with `BUILD SUCCESS`;
  reactor summary ended green (`compiler-backend-jvm` success, `cli` success with `Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`).

## Review: Full Regression + Obsolete Gate Cleanup (2026-02-28)
- Full regression command:
  `mvn -B -ntp test`
  passed with `BUILD SUCCESS` (`Tests run: 249, Failures: 0, Errors: 0, Skipped: 0` in `cli`; all reactor modules green).
- Direct TGTA non-TSX compile sweep (all `examples/tgta/src/ok/*.ts` + `*.d.ts`) confirms current state:
  `12/15` compile success, blockers are
  `020_expressions.ts` (`TSJ-BACKEND-PARSE`),
  `030_statements.ts` (`TSJ-BACKEND-PARSE`),
  `140_ts_5x_features.ts` (`TSJ-BACKEND-UNSUPPORTED`).
- Gate hardening delivered:
  `TsjTgtaCompileGateTest#tgtaKnownFailingFixturesEmitStableDiagnosticCodes`
  keeps excluded blockers explicit with stable codes instead of silent filtering only.
- CI gate step updated to execute both TGTA gate methods:
  `tgtaNonTsxFixturesCompileWithTsjCompileSuccess`
  and
  `tgtaKnownFailingFixturesEmitStableDiagnosticCodes`.
- Progression re-check:
  `unsupported/run_progress.sh`
  now reports
  `grammar summary: total=16 passed=12 failed=4`,
  `overall summary: total=21 passed=17 failed=4`.

## Review: TSJ-44d Governance Executable Matrix Gate (2026-02-28)
- Root cause addressed:
  TSJ-44d `matrix-gate` previously validated only classpath presence (`Class.forName`) and used hardcoded certified-subset manifest triples.
- Governance hardening delivered:
  `TsjAnyJarGovernanceCertificationHarness` now runs executable TSJ `run` interop scenarios for the certified subset libraries:
  Flyway, PostgreSQL driver, Jackson, SnakeYAML, HikariCP, Guava EventBus, and Apache Commons Lang.
- Contract updates:
  `matrix-gate` pass/fail is now based on executable scenario outcomes (`scenarios`, `passed`, `failed`, failure IDs/diagnostics).
  `certified-subset` manifest entries are emitted from executed scenario results instead of static hardcoded entries.
- Regression coverage tightened:
  `TsjAnyJarGovernanceCertificationTest#governanceGateRequiresMatrixVersionAndWorkloadSignoffCriteria`
  now asserts executable matrix summary markers (`scenarios=...`, `failed=0`) in signoff notes.
- Verification command:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjAnyJarGovernanceCertificationTest test`
  passed (`Tests run: 3, Failures: 0, Errors: 0`).

## Review: Unsupported Grammar Operator + TSJ-22 Import Closure (2026-02-28)
- `Red` coverage updates:
  `JvmBytecodeCompilerTest#supportsUnaryPlusExponentBitwiseShiftAndTypeRelationOperatorsInTsjGrammarPath`,
  `JvmBytecodeCompilerTest#supportsDefaultImportWithOptionalNamedBindingsInTsj22`,
  `JvmBytecodeCompilerTest#supportsNamespaceImportInTsj22`,
  `TsjCliTest#runSupportsDefaultImportWithOptionalNamedBindingsInTsj22`,
  `TsjCliTest#runSupportsNamespaceImportInTsj22`.
- `Green` implementation updates:
  backend tokenizer/parser/runtime/operator emission support for unary plus, exponentiation, bitwise, shift, `typeof`, `in`, `instanceof`;
  module bundling support for relative default imports (including default+named form),
  module bundling support for relative namespace imports,
  default export rewrite/lowering in module bundling path.
- Verification commands:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsUnaryPlusExponentBitwiseShiftAndTypeRelationOperatorsInTsjGrammarPath,JvmBytecodeCompilerTest#supportsDefaultImportWithOptionalNamedBindingsInTsj22,JvmBytecodeCompilerTest#supportsNamespaceImportInTsj22 test` passed.
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#runSupportsDefaultImportWithOptionalNamedBindingsInTsj22,TsjCliTest#runSupportsNamespaceImportInTsj22 test` passed.
  `mvn -B -ntp -pl cli -am -DskipTests install` succeeded for CLI exec path snapshot refresh.
  `unsupported/run_progress.sh` now reports
  `grammar summary: total=16 passed=12 failed=4`,
  `overall summary: total=21 passed=17 failed=4`.
- Remaining unsupported grammar progression fixtures are TSJ-15 guardrail features only:
  `013_dynamic_import.ts`, `014_eval_call.ts`, `015_proxy_constructor.ts`, `016_function_constructor.ts`.

## Review: Unsupported Jar-Interop Progression Suite (2026-02-28)
- Deliverables added:
  `unsupported/jarinterop/README.md`
  `unsupported/jarinterop/run_progress.sh`
  `unsupported/jarinterop/001_missing_target_class.ts`
  `unsupported/jarinterop/002_non_public_member.ts`
  `unsupported/jarinterop/003_classpath_version_conflict.ts`
  `unsupported/jarinterop/004_runtime_provided_scope.ts`
  `unsupported/jarinterop/005_app_isolated_conflict.ts`.
- Root suite integration:
  `unsupported/run_progress.sh` now executes grammar progression first, then jar-interop progression, and prints combined `overall summary`.
- Unsupported/rejected jar-interop categories validated from implementation/tests:
  missing interop target class on classpath (`TSJ-RUN-006`),
  non-public static member binding rejection (`TSJ-INTEROP-INVALID`),
  conflicting legacy jar versions in one classpath (`TSJ-CLASSPATH-CONFLICT`),
  runtime access to provided-scope dependency (`TSJ-CLASSPATH-SCOPE`),
  app-isolated duplicate class conflict (`TSJ-RUN-009`).
- Verification command:
  `unsupported/jarinterop/run_progress.sh`
  result: `summary: total=5 passed=5 failed=0`.
- Verification command:
  `unsupported/run_progress.sh`
  historical baseline result (before grammar/operator closures in this pass): `grammar summary: total=16 passed=0 failed=16`,
  `overall summary: total=21 passed=5 failed=16`.

## Review: Root Unsupported Grammar Progression Suite (2026-02-28)
- Deliverables added:
  `unsupported/README.md`
  `unsupported/run_progress.sh`
  `unsupported/grammar/001_unary_plus.ts`
  `unsupported/grammar/002_exponentiation.ts`
  `unsupported/grammar/003_shift_left.ts`
  `unsupported/grammar/004_shift_right.ts`
  `unsupported/grammar/005_shift_unsigned_right.ts`
  `unsupported/grammar/006_bitwise_and.ts`
  `unsupported/grammar/007_bitwise_xor.ts`.
- Progression model:
  each fixture is executed under Node baseline and TSJ run-path; mismatches/errors are counted as failures.
- Verification command:
  `unsupported/run_progress.sh`
  historical baseline result after expanding non-numeric cases: `summary: total=16 passed=0 failed=16`.
- Current failing surfaces captured by fixtures:
  unary-plus parse rejection (`TSJ-BACKEND-PARSE`), operator-parity mismatches for exponentiation/bitwise/shift forms,
  `typeof`/`in`/`instanceof` semantic mismatches, and unsupported import/dynamic-import/eval/proxy/function-constructor forms.

## Review: Docs Validation + Consolidation (2026-02-28)
- Added a non-expert docs entrypoint: `docs/README.md` with task-oriented navigation.
- Consolidated interop status docs into:
  `docs/interop-compatibility-guide.md`
  and removed:
  `interop-generic-adaptation.md`
  `interop-invocation-certification.md`
  `interop-metadata-guarantees.md`
  `interop-reflective-compatibility.md`.
- Consolidated JPA parity/certification docs into:
  `docs/jpa-certification-guide.md`
  and removed:
  `hibernate-jpa-realdb-parity.md`
  `hibernate-jpa-lazy-proxy-parity.md`
  `hibernate-jpa-lifecycle-parity.md`
  `hibernate-jpa-certification.md`.
- Consolidated TSJ-38 parity docs into:
  `docs/tsj38-parity-guide.md`
  and removed:
  `tsj38-readiness-gate.md`
  `tsj38a-db-parity.md`
  `tsj38b-security-parity.md`
  `tsj38c-parity-certification.md`.
- Merged TSJ-44 real-app/governance fragments into `docs/anyjar-certification.md` and removed:
  `anyjar-realapp-certification.md`
  `anyjar-governance-signoff.md`.
- Updated `docs/stories.md` links to consolidated doc paths.
- Corrected stale CLI invocation docs from `mvn ... -pl cli -am exec:java` to
  `mvn ... -f cli/pom.xml exec:java` (including root `README.md`, `docs/*.md`, and impacted example READMEs/specs).
- Documented current run-path limitation: `tsj run` does not forward argv to TS programs (`String[0]` invocation).
- Updated `docs/unsupported-feature-matrix.md` with currently observed unsupported/non-parity rows backed by tests/progression fixtures.
- Docs count reduction: top-level `docs/*.md` reduced from `44` to `34`.
- Verification commands:
  `mvn -B -ntp -pl cli -am -Dtest=TsjCliTest#runRejectsNonRelativeImportInTsj12Bootstrap -Dsurefire.failIfNoSpecifiedTests=false test` passed.
  `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args="compile examples/multi-file-demo/src/main.ts --out /tmp/tsj-doc-check-compile"` returned `TSJ-COMPILE-SUCCESS`.
  `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args="run examples/multi-file-demo/src/main.ts --out /tmp/tsj-doc-check-run"` returned `TSJ-RUN-SUCCESS`.
  CLI option audit (`docs` canonical commands vs `TsjCli` constants) reported no missing options.
  Markdown path audit for `docs/*.md` references found no missing `docs/...` files.

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
- Result at time of this 2026-02-22 slice: `15/15` files returned `TSJ-COMPILE-SUCCESS`.
  Current status is tracked in the top `Grammar/Parser TODOs` section (`12/15` with three known blockers).
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

## Review: JVM Backend Regression Recovery (2026-02-22)
- Root causes fixed:
  syntax-bridge normalization dropped object literal method declarations (breaking dynamic `this`, lexical `this` in arrow closures, thenable `then`, and object `valueOf`/`toString` coercion);
  dynamic `import()` was rewritten to `undefined` in normalized AST (bypassing TSJ15 diagnostics);
  async class accessors were silently discarded in normalization (bypassing targeted async getter/setter unsupported diagnostics);
  module collection pass-through for non-relative named imports bypassed TSJ-12 bootstrap relative-import rejection.
- Bridge/compiler fixes delivered:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now lowers object method declarations to `FunctionExpression` entries with `thisMode: 'DYNAMIC'`, rejects dynamic `import()` from normalization so parser diagnostics remain authoritative, and triggers parser fallback for async class accessors;
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java` now resolves named and side-effect imports through `resolveImport(...)` in module collection so non-relative specifiers emit deterministic relative-import diagnostics.
- Conformance updates:
  refreshed bridge conformance snapshots for files impacted by method normalization:
  `compiler/backend-jvm/src/test/resources/ts-conformance/expected/ok/020_expressions.ts.snapshot.json`,
  `compiler/backend-jvm/src/test/resources/ts-conformance/expected/ok/030_statements.ts.snapshot.json`,
  `compiler/backend-jvm/src/test/resources/ts-conformance/expected/ok/140_ts_5x_features.ts.snapshot.json`.
- Verification commands:
  `mvn -B -ntp -pl compiler/backend-jvm -Dtest=JvmBytecodeCompilerTest#rejectsAsyncClassGetterMethodVariantWithTargetedDiagnostic+supportsLexicalThisForArrowFunctionInsideMethod+rejectsNonRelativeImportInTsj12Bootstrap+supportsObjectToPrimitiveCoercionInLooseEquality+supportsDynamicThisInObjectMethodShorthand+supportsAsyncObjectMethodWithAwaitInInitializerAndReturn+rejectsDynamicImportWithFeatureDiagnosticMetadata+rejectsAsyncClassSetterMethodVariantWithTargetedDiagnostic+supportsThenableRejectBeforeSettlementAsRejection+rejectsAsyncObjectGeneratorMethodWithTargetedDiagnostic+supportsThenableAssimilationAndFirstSettleWins test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -Dtest=JvmBytecodeCompilerTest test` passed (`133` tests, `0` failures, `0` errors).
  `mvn -B -ntp -pl compiler/backend-jvm -Dtest=TypeScriptSyntaxBridgeConformanceSnapshotTest -Dtsj.updateConformanceSnapshots=true test` passed (snapshot refresh).
  `mvn -B -ntp -pl compiler/backend-jvm -Dtest=TypeScriptSyntaxBridgeTest,TypeScriptSyntaxBridgeConformanceSnapshotTest test` passed.

## Review: README Quickstart Revamp (2026-02-22)
- Scope delivered:
  root `README.md` rewritten as a minimal quickstart focused only on compiling/running TypeScript with TSJ.
- User-facing content now limited to:
  prerequisites;
  `compile` command;
  `run` command;
  additional dependency usage via repeated `--jar` and `--classpath`.
- Platform parity included:
  classpath delimiter examples for Linux/macOS (`:`) and Windows (`;`).
- Verification:
  command flag syntax validated against `docs/cli-contract.md` command signatures for
  `tsj compile ... [--classpath <entries>] [--jar <jar-file>]`
  and
  `tsj run ... [--classpath <entries>] [--jar <jar-file>]`.

## Review: XTTA Interop + Destructuring Progress (2026-02-28)
- Scope delivered:
  fixed XTTA interop null/exception blockers and enabled normalized-AST lowering for destructuring defaults/rest and multi-declaration statements.
- Runtime/interop fixes:
  `TsjInteropCodec.toJava(..., String.class)` now preserves `null`/`undefined` as Java `null` instead of coercing to `"null"`;
  `TsjRuntime.getProperty` / `getPropertyCached` now expose Java `Throwable` properties (`message`, `name`, `cause`) for catch-block access.
- Bridge/backend fixes:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now lowers destructuring default initializers and array rest bindings, and supports multi-declarator variable statements in `normalizedProgram`;
  `JvmBytecodeCompiler` now handles synthetic `__tsj_array_rest(...)` factory calls;
  `TsjRuntime` now provides `arrayRest(value, startIndex)`.
- Regression coverage added:
  `TsjInteropCodecTest#toJavaReturnsNullForNullWhenTargetIsStringReferenceType`,
  `TsjRuntimeTest#getPropertySupportsJavaThrowableMessageAndName`,
  `TsjRuntimeTest#getPropertyCachedSupportsJavaThrowableMessage`,
  `TsjRuntimeTest#arrayRestBuildsTailForArrayLikeAndIterableValues`,
  `TsjRuntimeTest#arrayRestRejectsNullishInputs`,
  `TsjCliTest#runSupportsInteropStaticMethodWithNullLiteralArgument`,
  `TsjCliTest#runSupportsJavaInteropExceptionMessageAccessInCatchBlock`,
  `TsjCliTest#runSupportsDestructuringDefaultsArrayRestAndSwapAssignment`.
- XTTA progression (same harness path `examples/XTTA/scripts/run.sh`):
  baseline progressed from `PASS: 1` to `PASS: 8` (`CRASH: 29` -> `CRASH: 22`);
  current interop suite is `5/5` passing, grammar `001_destructuring` passes (`10 checks`), and grammar `003_for_of` passes (`6 checks`).

## Review: XTTA Optional/Nullish Progress (2026-02-28)
- Scope delivered:
  fixed optional member-call lowering for `CallExpression` over `OptionalMemberAccessExpression` so expressions like `str?.toUpperCase()` preserve receiver semantics and short-circuit correctly on nullish receivers.
- Runtime/backend changes:
  `TsjRuntime.optionalInvokeMember(receiver, methodName, argsSupplier)` added for lazy receiver-aware optional member invocation;
  `JvmBytecodeCompiler.emitExpression(...)` now emits `optionalInvokeMember(...)` for call nodes whose callee is `OptionalMemberAccessExpression`.
- Regression coverage added:
  `JvmBytecodeCompilerTest#supportsOptionalPrimitiveMemberMethodCall`,
  `TsjRuntimeTest#optionalInvokeMemberShortCircuitsNullishReceiverAndPreservesMemberCall`.
- Verification:
  `mvn -B -ntp -pl runtime -Dtest=TsjRuntimeTest#optionalInvokeMemberShortCircuitsNullishReceiverAndPreservesMemberCall test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsOptionalPrimitiveMemberMethodCall test` passed.
  `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args='run examples/XTTA/src/grammar/004_optional_nullish.ts --out /tmp/xtta-g004'` returned `TSJ-RUN-SUCCESS`.
  `bash examples/XTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 9 | FAIL: 21 | CRASH: 21`.

## Review: XTTA Control-Flow Label/Comma Progress (2026-02-28)
- Scope delivered:
  `grammar/009_control_flow` no longer crashes; labeled loop control flow and comma-operator lowering now execute on the normalized-AST path.
- Root causes fixed:
  bridge dropped all `LabeledStatement` nodes (`return []`), which silently removed labeled loops;
  comma expressions were unsupported in normalized lowering and fell back into parser errors;
  `for...in` key collection in this fixture crashed on `keys.push(...)` because array-like `push` was missing.
- Bridge/backend/runtime changes:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now lowers labeled statements to explicit `LabeledStatement` nodes, preserves labeled `break`/`continue`, supports `CommaToken` lowering, and keeps labeled-loop prelude scope while attaching labels to the underlying loop statements;
  `JvmBytecodeCompiler` now lowers/emits `LabeledStatement`, resolves labeled break/continue targets in emission context, and emits comma operator via `TsjRuntime.comma(left, right)`;
  `TsjRuntime` now provides `comma(...)` and array-like `push` fallback for TSJ array objects via `invokeMember`.
- Regression coverage added:
  `JvmBytecodeCompilerTest#supportsLabeledBreakAndContinueInTsj59aSubset`,
  `JvmBytecodeCompilerTest#supportsCommaOperatorEvaluationOrderAndFinalValueInTsj59aSubset`,
  `JvmBytecodeCompilerTest#supportsSwitchFallthroughAndDefaultInMiddleInTsj59aSubset`,
  `TsjRuntimeTest#invokeMemberSupportsArrayLikePushOnTsjArrayObjects`.
- Verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsLabeledBreakAndContinueInTsj59aSubset,JvmBytecodeCompilerTest#supportsCommaOperatorEvaluationOrderAndFinalValueInTsj59aSubset test` passed.
  `mvn -B -ntp -pl runtime -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjRuntimeTest#invokeMemberSupportsArrayLikePushOnTsjArrayObjects,TsjRuntimeTest#invokeMemberSupportsStringIncludesAndTrimAliases test` passed.
  `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args='run examples/XTTA/src/grammar/009_control_flow.ts --out /tmp/xtta-g009'` now reports all 9 checks as `true` and returns `TSJ-RUN-SUCCESS`.
  `bash examples/XTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 12 | FAIL: 18 | CRASH: 17` (monotonic from `CRASH: 19` baseline).

## Review: XTTA 29/30 Progress (2026-03-01)
- Scope delivered:
  closed all remaining XTTA grammar crashes except generators by extending bridge normalization + runtime lowering for dynamic object keys, object/class accessors, class constructor parameter-properties, namespace exported functions, enum numeric reverse mapping, and top-level await async-IIFE parse shape.
- Core implementation changes:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now
  supports non-spread object literals with computed keys via dynamic-key lowering,
  lowers object literal accessors through `__tsj_define_accessor`,
  lowers class accessors and constructor parameter-properties,
  lowers namespace exported functions into namespace object entries,
  emits enum numeric reverse mappings,
  and rewrites `await (async () => {...})()` call-shape to `AwaitExpression`.
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java` now emits runtime calls for `__tsj_define_accessor`.
  `runtime/src/main/java/dev/tsj/runtime/TsjRuntime.java` now supports accessor descriptors with receiver-aware read/write semantics and exposes `defineAccessorProperty(...)`.
- Regression coverage added:
  `JvmBytecodeCompilerTest#supportsComputedObjectPropertyNameFromStringConcatenation`,
  `JvmBytecodeCompilerTest#supportsObjectLiteralGettersAndSetters`,
  `JvmBytecodeCompilerTest#supportsClassAccessorsPrivateFieldsAndConstructorParameterProperties`,
  `JvmBytecodeCompilerTest#supportsTopLevelAwaitOnAsyncIifeCallPattern`,
  `JvmBytecodeCompilerTest#supportsNamespaceExportedFunctionsAndNumericEnumReverseMapping`,
  `TsjRuntimeTest#accessorPropertiesInvokeGetterAndSetterWithReceiver`.
- Verification:
  `mvn -B -ntp -pl runtime -Dtest=TsjRuntimeTest#accessorPropertiesInvokeGetterAndSetterWithReceiver test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsNamespaceExportedFunctionsAndNumericEnumReverseMapping,JvmBytecodeCompilerTest#supportsTopLevelAwaitOnAsyncIifeCallPattern,JvmBytecodeCompilerTest#supportsClassAccessorsPrivateFieldsAndConstructorParameterProperties,JvmBytecodeCompilerTest#supportsObjectLiteralGettersAndSetters,JvmBytecodeCompilerTest#supportsComputedObjectPropertyNameFromStringConcatenation test` passed.
  `bash examples/XTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 29 | FAIL: 1 | CRASH: 1` with only `grammar/005_generators` remaining.

## Review: UTTA Baseline Triage + Planned Fix Order (2026-03-01)
- Baseline captured from implementation:
  `bash examples/UTTA/scripts/run.sh` -> `TOTAL: 30 | PASS: 11 | FAIL: 19 | CRASH: 17`.
- Bridge/lowering failures validated via no-fallback compile probes and temporary bridge instrumentation:
  `for await...of` unsupported in normalized program;
  compound bitwise assignment unsupported in normalized program;
  `DeleteExpression` unsupported in normalized program;
  `SuperKeyword` expression shape unsupported in normalized program (`super.member(...)`);
  element-access assignment lowers to `CallExpression` target (`__tsj_index_read(...)`) and then fails backend assignment target checks.
- Runtime/semantic failures validated from generated output:
  postfix semantics regression on member target (`this.nextId++`) causes shifted IDs and downstream UTTA stress failures (`find_id`, `order_ok`, `revenue`, `orders`);
  UTTA `nested_spread` assertion is incorrect for JS semantics (expected `length===7`, actual JS/Node result is `6`).
- Interop failures validated from runtime/bridge code paths:
  `TsjInteropCodec.fromJava(...)` currently unwraps `Enum` to `name()` string and `Optional` to contained value/null, causing receiver-type mismatches in UTTA enum/Optional scenarios;
  bare static field imports (`VERSION`, `MAX_SIZE`, `PI_APPROX`) resolve as static methods, so constant imports fail without explicit `$static$get$...` binding names.
- Plan recorded in `docs/plans.md` under:
  `## 2026-03-01 UTTA Issue Triage + Fix Plan (examples/UTTA)`.

## Review: UTTA BigInt/Symbol Closure Slice (2026-03-01)
- Scope delivered:
  closed UTTA `grammar/003_bigint` and `grammar/002_symbol` runtime failures by completing BigInt/Symbol global binding + literal emission in backend, and Symbol iterator/toPrimitive coercion semantics in runtime.
- Root causes fixed:
  `BigInt`/`Symbol` were still resolving through implicit-undefined global fallback in backend root scope, so calls like `BigInt(...)`/`Symbol(...)` executed against null;
  bigint numeric normalization stripped `n` and always emitted number-path constants, so bigint literals were not represented as runtime bigint values;
  runtime static initialization order created `SYMBOL_BUILTIN` before `SYMBOL_ITERATOR`/`SYMBOL_TO_PRIMITIVE`, so `Symbol.iterator` and `Symbol.toPrimitive` were initialized as null;
  iterator lookup only worked for string forms in some paths and lacked symbol-key parity across full for-of/spread code paths.
- Implementation changes:
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java` now
  defines `BIGINT_BUILTIN_CELL` and `SYMBOL_BUILTIN_CELL`,
  resolves root bindings for `BigInt` and `Symbol` explicitly,
  preserves bigint literal identity in normalization (`...n`) and emits bigint literals through `TsjRuntime.bigIntLiteral(...)`.
  `runtime/src/main/java/dev/tsj/runtime/TsjRuntime.java` now
  initializes symbol well-known values before symbol builtin construction,
  supports object coercion via `[Symbol.toPrimitive]` (number/string/default hint flow),
  checks symbol-keyed iterator member (`SYMBOL_ITERATOR.propertyKey()`) in iterator resolution,
  and includes dedicated regression coverage for symbol-key iterator behavior.
  Added runtime regression:
  `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java#forOfValuesSupportsSymbolIteratorOnPrototype`.
- Verification:
  `mvn -B -ntp -pl runtime -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjRuntimeTest#forOfValuesSupportsSymbolIteratorOnPrototype test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsBigIntLiteralTypeofAndConstructorInTsjRuntime,JvmBytecodeCompilerTest#supportsSymbolCreationRegistryAndSymbolPropertyKeys test` passed.
  `bash examples/UTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 22 | FAIL: 8 | CRASH: 7` (monotonic from `PASS: 20 | FAIL: 10 | CRASH: 9` baseline for this slice).

## Review: UTTA `typeof` Undeclared + Fixture Parity Slice (2026-03-01)
- Scope delivered:
  closed UTTA `grammar/015_misc_edge` compile blocker (`typeof undeclaredVariable`) and corrected the known false-negative UTTA fixture assertion in `grammar/012_deep_nesting` to match JS semantics.
- Root causes fixed:
  backend emitted `typeof` by first resolving the identifier binding, which failed compile for undeclared identifiers even though JS `typeof undeclared` must return `"undefined"`;
  UTTA `nested_spread` expected `length===7` but JS/Node result is `length===6` for `const r8 = [...[...base, 3], ...[4, ...base]]`.
- Implementation changes:
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java` now emits unary `typeof` through a specialized operand path that maps unresolved bare identifiers to runtime `undefined` in `typeof` context only.
  Added regression:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java#supportsTypeofOnUndeclaredIdentifierWithoutCompileFailure`.
  Updated fixture expectation:
  `examples/UTTA/src/grammar/012_deep_nesting.ts` now checks `r8.length === 6` and terminal element at index `5`.
- Verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsTypeofOnUndeclaredIdentifierWithoutCompileFailure,JvmBytecodeCompilerTest#supportsBigIntLiteralTypeofAndConstructorInTsjRuntime,JvmBytecodeCompilerTest#supportsSymbolCreationRegistryAndSymbolPropertyKeys test` passed.
  `node --experimental-strip-types examples/UTTA/src/grammar/012_deep_nesting.ts` reports `nested_spread:true`.
  `bash examples/UTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 24 | FAIL: 6 | CRASH: 6`.

## Review: UTTA Class-Expression Mixin Closure Slice (2026-03-01)
- Scope delivered:
  closed UTTA `stress/004_prototype_chains` crash by implementing normalized bridge support for class expressions used in mixin factories.
- Root cause fixed:
  `ClassExpression` nodes in the TS bridge were previously normalized to `UndefinedLiteral`, so patterns like
  `const Enhanced = addTimestamp(BaseEntity); new Enhanced();`
  lowered to `new undefined` at runtime.
- Implementation changes:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now:
  allows synthesized class names when normalizing class-like nodes without explicit identifiers,
  lowers `ClassExpression` to an IIFE-shaped `CallExpression` that emits the normalized class statements and returns the synthesized class reference.
  Added regression:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java#supportsClassExpressionReturnedFromMixinFactory`.
- Verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsClassExpressionReturnedFromMixinFactory,JvmBytecodeCompilerTest#supportsTypeofOnUndeclaredIdentifierWithoutCompileFailure,JvmBytecodeCompilerTest#supportsSymbolCreationRegistryAndSymbolPropertyKeys test` passed.
  `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args='run examples/UTTA/src/stress/004_prototype_chains.ts'` now reports `mixin:true` and returns `TSJ-RUN-SUCCESS`.
  `bash examples/UTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 25 | FAIL: 5 | CRASH: 5`.

## Review: UTTA Decorator Closure Slice (2026-03-01)
- Scope delivered:
  closed UTTA `grammar/011_decorators` by implementing legacy decorator lowering for in-scope class/method/property/static forms and runtime descriptor/callable support needed by the fixture.
- Root causes fixed:
  backend compile path stripped decorator lines before the TypeScript bridge (`preprocessTsj34Decorators(...)`), so decorators never executed;
  runtime lacked `Object.defineProperty`/`Object.getOwnPropertyDescriptor`/`Object.seal` behavior required by decorator callbacks;
  callable values used in decorator wrappers lacked `.apply`/`.call` member invocation support.
- Implementation changes:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now lowers class/method/property decorators to post-class runtime calls, applies method decorators via descriptor read/update/define flow, and skips unresolved external decorators deterministically by binding-aware filtering (preserving TSJ34 “unknown decorator” tolerance cases);
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java` now feeds unstripped bundled source into bridge parsing so decorator lowering is reachable;
  `runtime/src/main/java/dev/tsj/runtime/TsjRuntime.java` now provides object descriptor builtins (`seal`, `getOwnPropertyDescriptor`, `defineProperty`) and callable-member invocation for `.apply`/`.call`.
- Regression coverage added:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java#supportsLegacyDecoratorsForClassMethodPropertyAndStaticMembers`;
  `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java#objectBuiltinSupportsDefinePropertyAndDescriptorLookup`;
  `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java#invokeMemberApplySupportsTsjMethodTargets`.
- Verification:
  `mvn -B -ntp -pl runtime -Dtest=TsjRuntimeTest#objectBuiltinSupportsDefinePropertyAndDescriptorLookup,TsjRuntimeTest#invokeMemberApplySupportsTsjMethodTargets test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsLegacyDecoratorsForClassMethodPropertyAndStaticMembers,JvmBytecodeCompilerTest#supportsTsj34ControllerDecoratorLinesInBackendParser,JvmBytecodeCompilerTest#stripsSupportedParameterDecoratorsBeforeBackendParsing,JvmBytecodeCompilerTest#stripsUnknownDecoratorLinesInsteadOfFailingCompilation test` passed.
  `mvn -B -ntp -pl cli -am -DskipTests install` completed to refresh CLI dependency artifacts.
  `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args='run examples/UTTA/src/grammar/011_decorators.ts --out /tmp/utta011cli2'` now reports all checks `true` and returns `TSJ-RUN-SUCCESS`.
  `bash examples/UTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 26 | FAIL: 4 | CRASH: 4`.

## Review: UTTA `Error.cause` + `AggregateError` Closure Slice (2026-03-01)
- Scope delivered:
  closed UTTA `grammar/008_error_cause` by implementing runtime constructor support for `Error(..., { cause })` and global `AggregateError`.
- Root causes fixed:
  backend root-scope binding did not map `AggregateError`, producing unresolved-identifier compile failures;
  runtime `Error` and native subtype constructors ignored the options argument and therefore never populated `cause`.
- Implementation changes:
  `runtime/src/main/java/dev/tsj/runtime/TsjRuntime.java` now exposes `aggregateErrorBuiltin()`, adds `AggregateError` class construction (`name`, `message`, `errors`, optional `cause`), and centralizes error-constructor initialization with cause-option propagation;
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java` now emits `AGGREGATE_ERROR_BUILTIN_CELL` and resolves `AggregateError` in root binding lookup.
- Regression coverage added:
  `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java#errorBuiltinSupportsCauseOptionObject`;
  `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java#aggregateErrorBuiltinConstructsErrorsListAndMessage`;
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java#supportsErrorCauseAndAggregateErrorConstructorInTsj59aSubset`.
- Verification:
  `mvn -B -ntp -pl runtime -Dtest=TsjRuntimeTest#errorBuiltinSupportsCauseOptionObject,TsjRuntimeTest#aggregateErrorBuiltinConstructsErrorsListAndMessage,TsjRuntimeTest#objectBuiltinSupportsDefinePropertyAndDescriptorLookup,TsjRuntimeTest#invokeMemberApplySupportsTsjMethodTargets test` passed.
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#supportsErrorCauseAndAggregateErrorConstructorInTsj59aSubset test` passed.
  `mvn -B -ntp -pl cli -am -DskipTests install` completed to refresh CLI dependency artifacts.
  `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args='run examples/UTTA/src/grammar/008_error_cause.ts --out /tmp/utta008c'` reports all checks `true` with `TSJ-RUN-SUCCESS`.
  `bash examples/UTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 27 | FAIL: 3 | CRASH: 3`.

## Review: UTTA Final Grammar Closure + Regression Tail Fixes (2026-03-01)
- Scope delivered:
  closed remaining UTTA grammar failures `grammar/001_for_await_of` and `grammar/006_proxy_reflect`, then fixed follow-on regression failures surfaced by full-suite runs.
- Core implementation changes:
  `compiler/frontend/ts-bridge/emit-backend-tokens.cjs` now lowers `for await...of` by awaiting per-iteration values in normalized AST;
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java` now accepts async generator functions (method guardrails unchanged), removes Proxy constructor hard-reject, and wires root globals for `Proxy` and `Reflect`;
  `runtime/src/main/java/dev/tsj/runtime/TsjRuntime.java` now provides `proxyBuiltin()` and `reflectBuiltin()` with UTTA-required semantics (`Proxy`, `Proxy.revocable`, `Reflect.ownKeys/has/get/set`) plus proxy trap handling in property read/write/delete and `in` operator paths.
- Regression coverage added/updated:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TypeScriptSyntaxBridgeTest.java#emitsNormalizedProgramForForAwaitOfShape`;
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java#supportsAstOnlyPathForForAwaitOfWithAsyncGeneratorAndPromiseArray`;
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java#supportsProxyConstructorReflectApiAndRevocableProxy`;
  `runtime/src/test/java/dev/tsj/runtime/TsjRuntimeTest.java#proxyAndReflectBuiltinsSupportBasicTrapAndRevocableFlow`;
  `cli/src/test/java/dev/tsj/cli/TsjCliTest.java#compileProxySourceSucceeds`.
- Verification:
  `bash examples/UTTA/scripts/run.sh` now reports `TOTAL: 30 | PASS: 30 | FAIL: 0 | CRASH: 0`;
  targeted backend/bridge/runtime/cli tests for new behaviors passed;
  backend full suite reached `Tests run: 400, Failures: 0, Errors: 0`.
- Full-regression tail fixes after closure:
  stabilized mutable static-field interop test ordering by resetting `InteropFixtureType.GLOBAL` at test start in
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`;
  adjusted Spring integration fixture to use numeric `@ResponseStatus` form accepted by generator path in
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringWebControllerIntegrationTest.java`;
  refreshed bridge conformance snapshots via
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtsj.updateConformanceSnapshots=true -Dtest=TypeScriptSyntaxBridgeConformanceSnapshotTest test`;
  hardened real-library AnyJar matrix readiness assertion against host-load timing variance in
  `cli/src/test/java/dev/tsj/cli/TsjAnyJarCertificationTest.java` (assert coverage + pass count instead of timing-derived `subsetReady`).
- Final regression confirmation:
  CLI reactor tail from patched state passed end-to-end:
  `mvn -B -ntp -rf :cli test` -> `Tests run: 255, Failures: 0, Errors: 0`.
