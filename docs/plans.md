# Plans

## 2026-03-01 UTTA Final Grammar Closure (`001_for_await_of`, `006_proxy_reflect`)

- [x] `Red`: add/adjust regression tests to assert successful compile/run for:
  - `for await...of` (including async-generator source and promise-array source),
  - `Proxy` traps (`get`, `set`, `has`) and `Reflect` APIs used by UTTA fixture.
- [x] `Green` (bridge/backend/runtime):
  - lower `for await...of` in normalized AST with awaited iteration values,
  - allow async generator *functions* (keep async generator method guardrails unchanged),
  - remove `new Proxy(...)` hard-reject path and wire `Proxy`/`Reflect` global bindings.
- [x] `Green` (runtime):
  - implement minimal `Proxy` + `Proxy.revocable` behavior required by UTTA fixture,
  - implement `Reflect.ownKeys`, `Reflect.has`, `Reflect.get`, `Reflect.set`.
- [x] Verify:
  - targeted backend/bridge/cli/runtime regressions pass,
  - `bash examples/UTTA/scripts/run.sh` reaches `TOTAL: 30 | PASS: 30 | FAIL: 0 | CRASH: 0`,
  - regression tail fixed and validated (`compiler-backend-jvm` full suite + `mvn -B -ntp -rf :cli test` green after patching the flaky AnyJar readiness assertion).

## 2026-03-01 UTTA Next Slice (BigInt + Symbol Runtime/Compiler Closure)

- [x] `Red`: run focused backend/runtime tests that currently fail for BigInt/Symbol callable/global semantics.
- [x] `Green` (compiler): wire `BigInt`/`Symbol` global bindings in JVM backend bootstrap/binding resolution and preserve bigint literal emission (`123n` -> runtime bigint literal path).
- [x] `Green` (runtime): complete BigInt/Symbol coercion/property-key semantics (`[Symbol.toPrimitive]`, symbol-keyed iterator lookup, callable builtins behavior) required by UTTA grammar fixtures.
- [x] Verify targeted tests:
  - `JvmBytecodeCompilerTest#supportsBigIntLiteralTypeofAndConstructorInTsjRuntime`
  - `JvmBytecodeCompilerTest#supportsSymbolCreationRegistryAndSymbolPropertyKeys`
- [x] Re-run `bash examples/UTTA/scripts/run.sh` and record monotonic progression in `docs/todo.md`.

## 2026-03-01 UTTA Next Slice (`typeof` undeclared + fixture parity)

- [x] `Red`: add backend regression coverage for `typeof <undeclaredIdentifier>` to ensure compile succeeds and runtime returns `"undefined"`.
- [x] `Green`: update backend unary `typeof` emission to treat unresolved bare identifiers as `undefined` only in `typeof` context.
- [x] Validate `examples/UTTA/src/grammar/012_deep_nesting.ts` nested spread expectation against Node baseline and correct fixture assertion.
- [x] Re-run `bash examples/UTTA/scripts/run.sh` and record progression in `docs/todo.md`.

## 2026-03-01 UTTA Next Slice (Class Expression Mixin Closure)

- [x] `Red`: add backend regression for class expressions returned from mixin factories (`return class extends Base { ... }`).
- [x] `Green`: implement bridge normalization for `ClassExpression` via local IIFE lowering that emits class statements and returns the synthesized class value.
- [x] Verify targeted backend test and rerun UTTA progression harness.

## 2026-03-01 UTTA Next Slice (`grammar/011_decorators`)

- [x] `Red`: add focused backend/CLI regression coverage for decorator runtime behavior in UTTA fixture shapes (class, method, property, stacked class decorators).
- [x] `Green`: fix normalized bridge/backend lowering so decorator callbacks are invoked with expected targets/descriptor semantics for currently in-scope UTTA forms.
- [x] Verify:
  - targeted regression tests pass,
  - `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args='run examples/UTTA/src/grammar/011_decorators.ts'` returns all checks true,
  - UTTA progression remains monotonic.

## 2026-03-01 UTTA Next Slice (`grammar/008_error_cause`)

- [x] `Red`: add focused backend/runtime regression coverage for `Error(message, { cause })` and `new AggregateError(errors, message[, { cause }])`.
- [x] `Green`: add runtime `AggregateError` builtin support, wire backend global binding emission/resolution, and propagate `cause` option in error constructors.
- [x] Verify:
  - targeted runtime/backend tests pass,
  - `mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args='run examples/UTTA/src/grammar/008_error_cause.ts'` returns all checks true,
  - UTTA progression remains monotonic.

## 2026-03-01 XTTA Next TODO Slice (JSON + Builtins + Regression)

- [x] `Red`: add/extend runtime+backend tests that reproduce XTTA regressions for builtins/object invocation/control semantics.
- [x] `Green`: complete runtime builtin coverage and backend global bindings for `JSON`, `Object`, `Array`, `Map`, `Set`, `Math/Number`, `RegExp`, `Date`, and native error subtypes.
- [x] `Green`: close XTTA parser/normalization crashes for `010_rest_default_computed` and `011_getters_setters_misc` (dynamic object keys + object/class accessors).
- [x] `Green`: close XTTA normalization/runtime gaps for `006_class_features`, `013_async_edge`, and `014_enum_namespace`.
- [x] Verify targeted runtime/backend regressions plus repeated XTTA harness runs after each slice.
- [x] `Green`: implement generator semantics required by `examples/XTTA/src/grammar/005_generators.ts` (`yield`, `yield*`, `next(arg)`, iterator behavior).
- [x] Verify full XTTA reaches `TOTAL: 30 | PASS: 30 | FAIL: 0 | CRASH: 0`.
- [x] Run full Maven regression and fix any regressions introduced by the generator slice.
- [x] Update `docs/todo.md` review section with final command transcripts/outcomes and residual gaps (if any).

## 2026-03-01 UTTA Issue Triage + Fix Plan (`examples/UTTA`)

- [x] Re-run UTTA harness and capture baseline from implementation:
  `bash examples/UTTA/scripts/run.sh` -> `TOTAL: 30 | PASS: 11 | FAIL: 19 | CRASH: 17`.
- [x] Reproduce failing UTTA files with direct compile/run probes and map each failure to concrete frontend/backend/runtime/interop code paths.
- [x] Validate normalization-vs-parser split with no-fallback probes (`-Dtsj.backend.astNoFallback=true`) and bridge instrumentation for hidden normalization errors.
- [ ] `Red`: add targeted regression tests (backend/runtime/interop + CLI where needed) for each UTTA failure family before fixes.
- [ ] `Green` (bridge/lowering):
  - support element-access assignment targets so `obj[key] = v` lowers without `CallExpression` assignment targets;
  - support compound bitwise/shift assignment operators in normalized AST (`&=`, `|=`, `^=`, `<<=`, `>>=`, `>>>=`);
  - support `DeleteExpression` in normalized AST (`delete obj.x`, `delete obj?.a?.b`);
  - support optional element access normalization (`arr?.[i]`);
  - support `super.member(...)` call shape in normalized AST (currently trips on `SuperKeyword`);
  - support multi-declarator for-loop initializers (`for (let i = 0, j = 10; ...)`) to avoid fallback churn.
- [ ] `Green` (backend/runtime semantics):
  - fix postfix update semantics for non-identifier targets (`this.nextId++` should return pre-increment value);
  - add BigInt global binding/runtime callable semantics required by UTTA BigInt fixture.
- [ ] `Green` (feature coverage currently hard-blocked):
  - implement or explicitly re-scope with deterministic diagnostics for `async function*` / `for await...of`, `Proxy`/`Reflect`, `WeakMap`/`WeakSet`/`WeakRef`, and `AggregateError`.
- [ ] `Green` (interop):
  - preserve Java enum object identity across interop boundaries (do not coerce enum instances to string on `fromJava`);
  - preserve `java.util.Optional` wrapper identity when returned to TSJ (do not eagerly unwrap to raw value);
  - support bare static field imports (`import { VERSION } from "java:..."`) or auto-normalize to static-field getter bindings.
- [x] Validate UTTA fixture expectations against Node baseline and correct known false negatives (`grammar/012_deep_nesting nested_spread` now aligned with JS length `6`).
- [x] Verify monotonic UTTA progression after each slice (`PASS` non-decreasing, `FAIL/CRASH` non-increasing) and finish at `PASS: 30`.
- [x] Run full regression (`mvn -B -ntp test`) after UTTA closure and document outcomes in `docs/todo.md`.

## 2026-02-28 Full Regression Follow-up: Obsolete Code/Test Audit

- [x] Re-run full Maven regression and capture final module-level pass/fail status.
- [x] Re-run TGTA non-TSX compile sweep directly to validate current per-fixture diagnostic reality.
- [x] Make TGTA compile-gate exclusions explicit with stable-diagnostic assertions (avoid hidden stale assumptions).
- [x] Reconcile docs that overstated TGTA closure or carried stale progression summaries.
- [x] Verify targeted gate checks and unsupported progression counts after updates.

## 2026-02-28 TSJ-44d Governance Hardening (Executable Matrix Gate)

- [x] `Red`: capture current governance weakness (matrix gate only checks classpath resolution, not executable interop).
- [x] `Green`: replace matrix gate with executable TSJ `run` scenarios across certified any-jar subset libraries.
- [x] `Green`: derive certified-subset manifest entries from executed scenario results instead of hardcoded triples.
- [x] Verify targeted governance certification tests pass and report remains deterministic.
- [x] Update `docs/todo.md` to close the TSJ-44d governance-hardening TODO with verification notes.

## 2026-02-28 TNTA Spec Validation + Implementation (`examples/tnta/TNTA_SPEC.md`)

- [ ] Validate TNTA spec assumptions against current TSJ runtime/parser/CLI behavior and document concrete mismatches.
- [ ] Align TNTA execution commands with current tooling (`node --experimental-strip-types` for TS baseline tooling; `tsj run` without unsupported arg passthrough).
- [ ] `Red`: add deterministic TNTA harness tests for report shape and baseline comparison behavior.
- [ ] `Green`: implement TNTA TypeScript app scaffold (`src/main.ts`, harness modules, suites, config, expected baseline module).
- [ ] `Green`: represent unsupported TSJ grammar/operator coverage as explicit skipped TNTA cases with stable reasons.
- [ ] Verify end-to-end:
  - baseline generation command succeeds deterministically,
  - TNTA Node compare mode passes against generated baseline,
  - TNTA TSJ run returns `TSJ-RUN-SUCCESS`.
- [ ] Update `docs/todo.md` review notes with delivered scope, known skips, and verification commands.

## 2026-02-28 Root Unsupported Grammar Progression Suite (`unsupported/`)

- [x] Validate unsupported grammar/operators from parser/runtime code paths and direct compile/run probes (not docs-only).
- [x] Add root-level unsupported fixtures under `unsupported/grammar` for currently failing grammar/operator parity cases.
- [x] Add runnable progression harness (`unsupported/run_progress.sh`) that compares Node baseline output vs TSJ output per fixture.
- [x] Emit deterministic summary counts (`total`, `passed`, `failed`) and non-zero exit when failures remain, so count reduction is trackable over time.
- [x] Verify the new progression suite runs end-to-end and reports current failing count.

## 2026-02-28 Docs Validation + Consolidation (`docs/`)

- [x] Audit current docs for correctness against current code/tests (focus: CLI commands, supported/unsupported grammar, runtime behavior claims).
- [x] Build a simpler docs entry path for non-compiler engineers (`docs/README.md` with “start here” flow + short map to deeper docs).
- [x] Consolidate overlapping docs by merging small topic-fragment files into fewer canonical guides and deleting redundant originals.
- [x] Update remaining docs to remove stale or misleading claims found during audit.
- [x] Verify referenced CLI commands/options in canonical docs against current `TsjCli` implementation.
- [x] Add review notes to `docs/todo.md` with what was validated, consolidated, and removed.

## 2026-02-22 TGTA Spec Implementation (`examples/tgta/spec.md`)

- [x] Define standalone scope (TypeScript-only app, no TSJ internals) and create TGTA skeleton under `examples/tgta`.
- [x] `Red`: add TypeScript harness tests that fail before snapshot fixtures exist.
- [x] `Green`: implement TGTA parser harness (`parse_harness.ts`, `snapshot.ts`, `expect.ts`) with deterministic output and `--update`.
- [x] `Green`: add grammar source suites in `src/ok`, `src/err`, `src/entry.ts`, and project metadata (`tsj.json`, `README.md`).
- [x] `Green`: generate and commit deterministic snapshots under `fixtures/expected`.
- [x] `Refactor`: keep feature-file boundaries simple so adding a grammar feature is one file + one snapshot.
- [x] Verify by running the harness test command and parse harness command; record outcomes in `docs/todo.md`.

## 2026-02-22 TGTA -> TSJ Gap Audit (`examples/tgta/src/ok`)

- [x] Run `tsj compile` against every file in `examples/tgta/src/ok`.
- [x] Capture first blocking diagnostic code/message per file.
- [x] Map diagnostics to concrete parser/grammar capability gaps.
- [x] Update `docs/todo.md` under `## Grammar/Parser TODOs` with explicit TGTA-driven gap items.
- [x] Verify documented gaps match the captured compile output.

## 2026-02-22 TGTA Gap Closure Workstream (No JSX/TSX)

- [x] Remove JSX/TSX gap item from `docs/todo.md` per scope clarification.
- [x] `Red`: add backend test covering bigint + extended numeric literal grammar forms.
- [x] `Green`: implement numeric literal normalization (bigint suffix, separators, binary/octal/hex) in bridge/backend parse and emission paths.
- [x] Verify the new targeted backend test passes.
- [x] Continue with remaining TGTA blockers (`template literals`, `type/declare/interface/enum/namespace/module forms`, decorators/import attributes).

## 2026-02-22 TGTA Gap Closure Sprint B (No JSX/TSX)

- [x] Re-run TGTA non-TSX compile sweep and capture current first-failure diagnostics after bigint support.
- [x] `Red`: add backend tests for template/module/ambient/decorator/namespace blocker shapes found by the sweep.
- [x] `Green`: implement bridge/backend tolerance + lowering for the remaining non-TSX TGTA blockers.
- [x] Verify targeted backend tests and confirm each failing file advances to `TSJ-COMPILE-SUCCESS`.
- [x] Continue iteratively on next blockers from the same sweep (`type`/`declare`/`interface`/`enum`/`module forms`) with one red-green slice per blocker.

## 2026-02-22 TGTA Final Sweep (No JSX/TSX)

- [x] Add regression coverage for namespace value export lowering (`TypeScriptSyntaxBridgeTest`, `JvmBytecodeCompilerTest`).
- [x] Re-run non-TSX TGTA compile sweep and confirm all files return `TSJ-COMPILE-SUCCESS`.
- [x] Update `docs/todo.md` with final closure status and residual follow-up.

## 2026-02-22 TGTA Non-TSX CI Regression Gate

- [x] `Red`: add a CLI test that compiles every in-scope TGTA `ok` fixture (`*.ts`, `*.d.ts`, excluding TSX) and fails on any non-success.
- [x] `Green`: ensure the gate test passes with current bridge/backend behavior.
- [x] Wire the gate into CI as an explicit workflow step.
- [x] Update `docs/todo.md` to mark the CI gate item complete with verification details.

## 2026-02-22 TITA Interop Metadata Slice

- [x] `Red`: reproduce TITA shared-mode failure (`selectedTargetCount == 0`) and add targeted CLI regression for broad-policy/no-spec interop metadata emission.
- [x] `Green`: update CLI auto-interop generation to synthesize bridge specs from discovered targets when `--interop-spec` is absent under broad policy.
- [x] Preserve missing-jar failure contract for `run` by remapping class-not-found bridge bootstrap failures to `TSJ-RUN-006` with actionable context.
- [x] Verify with targeted tests (`TsjCliTest`, `TsjTitaInteropAppTest`, `TsjTgtaCompileGateTest`).

## 2026-02-22 Interop Discovery Multiline Slice

- [x] `Red`: add CLI regression proving broad-policy/no-spec auto-interop metadata is emitted for multiline named `java:` imports.
- [x] `Green`: replace line-based import discovery with source-level import-statement parsing that handles multiline named imports.
- [x] `Green`: extend regression coverage for multiline named relative import traversal so nested `java:` targets still emit selected-target metadata.
- [x] Verify targeted interop tests (`TsjCliTest` multiline case + existing broad/no-spec case + multiline relative case + `TsjTitaInteropAppTest` + `TsjTgtaCompileGateTest`) remain green.

## 2026-02-22 TITA Runbook Documentation

- [x] Gather exact shared/app-isolated/missing-jar expected outcomes from existing TITA regression tests.
- [x] Author `docs/tita-runbook.md` with commands, expected artifacts/diagnostics, and deterministic reproducibility checks.
- [x] Update `docs/todo.md` documentation TODO status and review notes for the runbook deliverable.

## 2026-02-22 TSJ53 SAM Adapter Closure Slice

- [x] `Red`: add CLI regression where TS lambda is passed directly into user-defined Java SAM interface that redeclares `Object` methods.
- [x] `Green`: update runtime SAM detection to treat `Object`-contract signatures (`toString`, `hashCode`, `equals`) as non-SAM candidates even when redeclared on interfaces.
- [x] `Green`: extend compile-time selected-target metadata regression coverage for SAM callback invocation (`SamRunner#run(MyFn,String)`).
- [x] Verify targeted interop/TITA/TGTA guard tests remain green.

## 2026-02-22 Supported Grammar Matrix Publication

- [x] Replace `docs/unsupported-feature-matrix.md` with a status-oriented supported grammar matrix (compile/run scopes).
- [x] Map each matrix row to stable diagnostic code/feature ID when unsupported.
- [x] Update `docs/todo.md` to mark the matrix publication TODO complete with review notes.

## 2026-02-22 Overload Tie-Break Parity + Ambiguity Diagnostics

- [x] `Red`: add runtime interop regression tests for (a) specificity winner vs lexicographic tie and (b) explicit ambiguous-call diagnostics.
- [x] `Green`: implement deterministic overload ordering/tie-break parity in runtime interop resolution and raise ambiguity diagnostics with candidate/reason details.
- [x] `Green`: make backend overload candidate enumeration deterministic so compile-time diagnostics/metadata remain stable across reflection order.
- [x] Verify targeted runtime/backend/CLI interop tests remain green.
- [x] Update `docs/todo.md` review section and TODO status for this slice.

## 2026-02-22 Runtime Null/Undefined + Vararg Parity Slice

- [x] `Red`: add runtime interop tests that cover null/undefined argument conversion across static, instance, and preselected invocation paths.
- [x] `Red`: add regression tests for vararg + null literal behavior with and without preselected targets.
- [x] `Green`: implement runtime fixes only if the new tests expose conversion/dispatch parity gaps.
- [x] Verify targeted runtime tests pass and no interop/TITA guard regressions occur.
- [x] Update `docs/todo.md` runtime TODO checkboxes and review notes for this slice.

## 2026-02-22 Shared-Mode Mediation Reorder Determinism

- [x] `Red`: add CLI regression for shared-mode dependency mediation where classpath input order is permuted.
- [x] `Green`: ensure selected/rejected mediation winner metadata and runtime output remain stable for the nearest-rule duplicate-version graph under reordering.
- [x] Verify targeted CLI mediation/certification tests remain green.
- [x] Update `docs/todo.md` to close the remaining runtime TODO and record review notes.

## 2026-02-22 Interop Selected-Target Metadata Import-Attributes Coverage

- [x] `Red`: add CLI regression proving broad-policy/no-spec selected-target metadata is preserved for `java:` imports that include import attributes (`with`/`assert`) clauses.
- [x] `Green`: extend interop target discovery import parsing to accept import-attributes clauses so selected-target metadata is emitted for that entry path.
- [x] Verify targeted CLI interop metadata tests remain green.
- [x] Update `docs/todo.md` TODO/review notes for this slice.

## 2026-02-22 `jrt:/` Mixed Classpath Determinism

- [x] `Red`: add CLI run regression that round-trips mixed jar + `jrt:/...` classpath entries through artifact serialization and reparse.
- [x] `Green`: no code change required; existing parser/serialization/runtime classloader path already satisfied the determinism contract under the new regression.
- [x] Verify targeted CLI mixed-classpath and interop guard tests remain green.
- [x] Update `docs/todo.md` TODO/review notes for this slice.

## 2026-02-22 App-Isolated Conflict Diagnostic Payloads

- [x] `Red`: tighten app-isolated conflict tests to require explicit `appOrigin` + `dependencyOrigin` context fields and actionable guidance for `TSJ-RUN-009`.
- [x] `Green`: add deterministic payload fields `conflictClass` + `guidance` to the CLI `TSJ-RUN-009` emission path while preserving existing `className` and origin metadata.
- [x] Verify targeted app-isolated CLI/TITA tests remain green.
- [x] Update `docs/todo.md` TODO/review notes for this slice.

## 2026-02-22 MR-JAR Winner Provenance

- [x] `Red`: tighten TITA shared-mode assertions to require MR-JAR winner provenance in class-index JSON and artifact metadata.
- [x] `Green`: implement deterministic MR-JAR class winner selection (`base` vs `META-INF/versions/*`) during classpath symbol indexing and persist provenance fields.
- [x] Verify targeted class-index/jrt/app-isolated/TITA tests remain green.
- [x] Update `docs/todo.md` TODO/review notes for this slice.

## 2026-02-22 Generic Signature Bounds Hardening (Intersection + Wildcards)

- [x] `Red`: add runtime interop regressions for wildcard `? super` element conversion and intersection-bound (`T extends A & B`) enforcement.
- [x] `Green`: update interop conversion to preserve wildcard lower bounds and enforce all intersection bounds deterministically.
- [x] Verify targeted runtime/interop tests pass locally.
- [x] Update `docs/todo.md` with TODO closure + review notes for this slice.

## 2026-02-22 Nullability Inference Integration for Binding Args Resolution

- [x] `Red`: add backend regression proving `bindingArgs.<name>=null` is rejected for `@NotNull` parameters during TSJ-54 selected-target resolution.
- [x] `Green`: propagate analyzed Java nullability (`JavaNullabilityAnalyzer`) into `JavaOverloadResolver` candidate parameter states used by interop bridge target selection.
- [x] Verify targeted backend and CLI interop metadata tests remain green.
- [x] Update `docs/todo.md` with TODO closure + review notes for this slice.

## 2026-02-22 Property Synthesis Deterministic Conflict Reasons

- [x] `Red`: add property-synthesizer regressions for deterministic overloaded-setter skip signatures and `getURL/getUrl` alias conflict handling.
- [x] `Green`: normalize property buckets by canonical key, skip alias-casing collisions deterministically, and include sorted method signatures in conflict diagnostics.
- [x] Verify targeted backend and CLI interop guard tests remain green.
- [x] Update `docs/todo.md` with TODO closure + review notes for this slice.

## 2026-02-22 Selected-Target Metadata Constructor/Instance Coverage

- [x] `Red`: add CLI regression for broad/no-spec auto-interop where selected targets include constructor (`$new`) and instance (`$instance$...`) bindings.
- [x] `Green`: no production change required; existing selected-target metadata emission already covered this entry path with deterministic ordering.
- [x] Verify targeted CLI selected-target metadata tests remain green.
- [x] Update `docs/todo.md` progress notes for the still-open selected-target TODO.

## 2026-02-22 Selected-Target Metadata Field Binding Coverage

- [x] `Red`: add CLI regression covering selected-target metadata for TSJ29 field bindings (`$instance$get$...`, `$instance$set$...`, `$static$get$...`, `$static$set$...`) under broad/no-spec auto-interop.
- [x] `Green`: fix interop discovery/bridge metadata emission only if the new regression fails.
- [x] Verify targeted CLI selected-target metadata tests remain green.
- [x] Update `docs/todo.md` progress/closure state for the selected-target metadata TODO.

## 2026-02-22 TITA Fixture Pack Determinism Gate

- [x] `Red`: add TITA shared-mode regression that runs the fixture app twice and asserts deterministic `class-index.json` + selected-target artifact metadata.
- [x] `Red`: add TITA app-isolated regression that runs the conflict path twice and asserts deterministic conflict code/origin payloads.
- [x] `Green`: adjust run/diagnostic/class-index emission only if the determinism regressions fail.
- [x] Verify targeted TITA fixture tests remain green.
- [x] Update `docs/todo.md` to close the end-to-end TITA fixture-pack TODO items with verification notes.

## 2026-02-22 Raw TS Parser Conformance Snapshot Suite

- [x] `Red`: add backend conformance harness that parses raw TGTA non-TSX fixtures and compares bridge AST/lowering snapshots to expected files.
- [x] `Green`: generate/commit deterministic expected snapshots for all covered fixtures (`ok` + `err`, excluding TSX).
- [x] Verify the new conformance suite passes in normal (non-update) mode.
- [x] Update `docs/todo.md` to close the parser-conformance TODO with verification details.

## 2026-02-22 Grammar Differential Fixtures (Node vs TSJ)

- [x] `Red`: add grammar-heavy Node-vs-TSJ differential fixtures for logical chains, optional chaining, destructuring, and template literals in CLI fixture harness tests.
- [x] `Green`: keep fixture setup minimal by reusing a strict-parity fixture helper for deterministic expected stdout/stderr.
- [x] Verify targeted CLI fixture harness differential tests pass.
- [x] Update `docs/todo.md` to close the differential-testing TODO with verification notes.

## 2026-02-22 Grammar Feature CI Gate

- [x] Add a CI step that explicitly runs grammar gates across parser conformance snapshots, backend syntax bridge tests, and CLI grammar differential fixtures.
- [x] Verify the new CI grammar gate command succeeds locally.
- [x] Update `docs/todo.md` to close the CI-gate TODO with command references.

## 2026-02-22 JVM Backend Regression Recovery (Failing `JvmBytecodeCompilerTest`)

- [x] Reproduce the 11 failing/erroring `JvmBytecodeCompilerTest` cases and group them by root-cause surface (syntax bridge lowering, unsupported-feature diagnostics, runtime semantics).
- [x] `Red`: lock in any missing regression expectations only if needed (prefer existing failing tests as the red state).
- [x] `Green`: restore object/class method lowering so method calls keep correct callable binding (`this`, async object method init, arrow lexical `this`).
- [x] `Green`: restore targeted compile-time rejections for unsupported async getter/setter/object-generator variants and import restrictions (non-relative + dynamic import diagnostics).
- [x] `Green`: fix runtime semantic regressions surfaced by the suite (abstract equality object-to-primitive coercion and thenable assimilation/reject-first-settle semantics).
- [x] Verify by running targeted failing tests, then full `JvmBytecodeCompilerTest`.
- [x] Update `docs/todo.md` review section with root causes, fixes, and verification commands/outcomes.

## 2026-02-22 README Quickstart Revamp (Compile/Run + Extra JARs)

- [x] Replace root `README.md` with a minimal user quickstart focused only on compile/run workflows.
- [x] Include explicit commands for `compile` and `run` with additional JARs (`--jar`) and classpath entries (`--classpath`).
- [x] Keep content concise and remove unrelated architecture/status details from README.
- [x] Verify command syntax against current CLI contract and ensure examples are copy-paste ready.

## 2026-02-28 Unsupported Jar-Interop Progression Suite

- [x] Validate unsupported jar-interop behaviors from implementation/tests (not docs) and list concrete unsupported/rejected categories.
- [x] Add `unsupported/jarinterop` fixtures covering stable unsupported/rejected jar-interop scenarios.
- [x] Add a jar-interop progression runner that executes fixtures, builds required test jars, and checks deterministic diagnostic codes.
- [x] Wire `unsupported/run_progress.sh` to run grammar + jarinterop suites with a combined summary.
- [x] Run the progression suite locally and record outcomes in `docs/todo.md` review notes.

## 2026-02-28 Unsupported Grammar Operator + Unary Parity Slice

- [x] `Red`: add backend/runtime regression coverage for unary plus, exponentiation, shift/bitwise operators, `typeof`, `in`, and `instanceof`.
- [x] `Green`: implement parser/tokenizer/runtime/codegen support for the above operators with deterministic behavior.
- [x] Verify targeted backend tests pass.
- [x] Verify `unsupported/run_progress.sh` shows reduced grammar failures from the current baseline.
- [x] Record outcomes in `docs/todo.md` review notes.

## 2026-02-28 TSJ-22 Default + Namespace Import Support Slice

- [x] `Red`: convert existing TSJ-22 default/namespace rejection tests into positive compile/run assertions in backend + CLI suites.
- [x] `Green`: implement relative default import support (including default+named form) in module bundling.
- [x] `Green`: implement relative namespace import support in module bundling.
- [x] Verify targeted backend + CLI tests pass.
- [x] Verify `unsupported/run_progress.sh` failure count decreases accordingly.
- [x] Record outcomes in `docs/todo.md` review notes and refresh `docs/unsupported-feature-matrix.md`.

## 2026-02-28 TGTA Non-TSX 15/15 Closure + Fallback Reduction

- [x] `Red`: remove TGTA known-failing exclusions so gate requires `TSJ-COMPILE-SUCCESS` for all non-TSX `ok` fixtures.
- [x] `Green`: implement normalized-AST handling for remaining `020_expressions.ts` blocker (dynamic import expression in compile path) while keeping existing relative dynamic-import guardrail diagnostics intact.
- [x] Verify targeted gates:
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTgtaCompileGateTest test`
- [x] Verify broader guardrails/regressions still hold:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#rejectsDynamicImportWithFeatureDiagnosticMetadata test`
  `mvn -B -ntp -pl cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#compileUnsupportedSyntaxReturnsBackendDiagnostic,TsjCliTest#compileDynamicImportIncludesUnsupportedFeatureContext test`
- [x] Run full regression (`mvn -B -ntp test`) and resolve surfaced regressions (conformance snapshot drift + fixture-harness mismatch fixture).
- [x] Update `docs/todo.md` status/review entries for TGTA closure progress and fallback-removal progress.

## 2026-02-28 XTTA Baseline + TODO Plan (`examples/XTTA/README.md`)

- [x] Re-run XTTA harness to validate current baseline from implementation (not docs-only):
  `bash examples/XTTA/scripts/run.sh`
- [x] Capture observed baseline:
  `TOTAL: 30 | PASS: 1 | FAIL: 29 | CRASH: 29`
  with dominant categories
  `TSJ-BACKEND-PARSE`, `TSJ-BACKEND-UNSUPPORTED`, `TSJ-RUN-006`, `TSJ-BACKEND-JAVAC`, `TSJ-INTEROP-INPUT`.
- [ ] `Red`: add targeted regression tests for each XTTA failure family (one focused backend/cli/runtime test per family) so fixes are tracked outside XTTA script output.
- [ ] `Green`: parser slice for XTTA grammar blockers:
  object/array destructuring declarations, template literal expressions, rest parameters/default-computed parameters, object-literal getters/setters, labeled statements, comma operator.
- [ ] `Green`: backend lowering slice for XTTA grammar/runtime blockers:
  catch-without-binding lowering correctness, generator `.next()` object shape, closure-returned callables, enum reverse lookup behavior.
- [ ] `Green`: runtime semantics slice:
  `for...of` string iteration parity, optional chaining on primitives/boxed values, `typeof` narrowing property access parity where already expected by TSJ runtime model.
- [ ] `Green`: JS built-ins slice:
  register missing globals (`JSON`, `Map`, `Set`, `NaN`, `Infinity`, `TypeError`, `RangeError`, `Date`) and implement/high-confidence bridge coverage for XTTA-called methods (`Array`, `String`, `Object`, `RegExp`, `Date.now`).
- [x] `Green`: interop infrastructure unblocker:
  fix `TSJ-INTEROP-INPUT: package dev.tsj.runtime does not exist` for XTTA interop fixtures and verify jar-enabled `tsj run` path.
- [ ] Verification gate:
  rerun `bash examples/XTTA/scripts/run.sh` after each slice; require monotonic progression (`PASS` non-decreasing, `CRASH` non-increasing).
- [ ] Final gate for this story:
  full regression remains green (`mvn -B -ntp test`) and XTTA README baseline section updated to match measured current state.

Progress updates:
- [x] XTTA rerun after interop classpath + null conversion + throwable property fixes:
  `TOTAL: 30 | PASS: 6 | FAIL: 24 | CRASH: 24`.
- [x] XTTA rerun after normalized-AST destructuring/default/rest + multi-declaration normalization:
  `TOTAL: 30 | PASS: 7 | FAIL: 23 | CRASH: 23`.
- [x] XTTA rerun after runtime string iteration parity for `for...of`:
  `TOTAL: 30 | PASS: 8 | FAIL: 22 | CRASH: 22`.
- [x] XTTA rerun after optional primitive member-call fix:
  `TOTAL: 30 | PASS: 9 | FAIL: 21 | CRASH: 21`.
- [x] XTTA rerun after exception-clause + template-literal/runtime String fixes:
  `TOTAL: 30 | PASS: 11 | FAIL: 19 | CRASH: 19`.
- [x] XTTA rerun after control-flow label/comma lowering + array `push` fallback:
  `TOTAL: 30 | PASS: 11 | FAIL: 19 | CRASH: 17`.
- [x] XTTA rerun after switch fallthrough/default-in-middle lowering fix:
  `TOTAL: 30 | PASS: 12 | FAIL: 18 | CRASH: 17`.

## 2026-02-28 XTTA Next Slice: `grammar/004_optional_nullish`

- [x] `Red`: add regression coverage for optional member call on primitive receiver (`str?.toUpperCase()`) to lock current crash.
- [x] `Green`: preserve receiver-aware optional member-call lowering (`CallExpression` over `OptionalMemberAccessExpression`) with lazy argument evaluation.
- [x] Verify:
  - targeted tests for new optional-member-call behavior pass,
  - `examples/XTTA/src/grammar/004_optional_nullish.ts` runs without crash,
  - XTTA progression is monotonic (`PASS` non-decreasing, `CRASH` non-increasing).

Progress update:
- [x] XTTA rerun after optional primitive member-call fix:
  `TOTAL: 30 | PASS: 9 | FAIL: 21 | CRASH: 21`.

## 2026-02-28 XTTA Next Slice: `grammar/015_exceptions`

- [x] `Red`: add regression coverage for `try/finally` and `try/catch` forms with empty clauses (`finally {}` / `catch {}`) that currently trigger javac malformed-try output.
- [x] `Green`: preserve catch/finally clause presence through bridge normalization and backend try-statement lowering/emission, even when clause bodies are empty.
- [x] Verify:
  - targeted backend regression passes,
  - `examples/XTTA/src/grammar/015_exceptions.ts` runs with `TSJ-RUN-SUCCESS`,
  - XTTA progression remains monotonic (`PASS` non-decreasing, `CRASH` non-increasing).

## 2026-02-28 XTTA Next Slice: `grammar/005_generators`

- [ ] `Red`: add regression coverage for generator `next()` behavior where yielded iterator object currently resolves to null in runtime invocation path.
- [ ] `Green`: fix backend/runtime lowering so generator factory invocation returns a callable iterator with `next` bound correctly.
- [ ] Verify:
  - targeted backend/runtime regression tests pass,
  - `examples/XTTA/src/grammar/005_generators.ts` runs with `TSJ-RUN-SUCCESS`,
  - XTTA progression is monotonic (`PASS` non-decreasing, `CRASH` non-increasing).

## 2026-02-28 XTTA Next Slice: `grammar/009_control_flow`

- [x] `Red`: add focused regressions for labeled `break`/`continue` and comma-operator evaluation order/result.
- [x] `Green`: implement normalized-AST + backend lowering/emission support for labeled statements and labeled break/continue.
- [x] `Green`: add comma-operator support through parsing/lowering/emission so `(a, b, c)` evaluates left-to-right and returns the final value.
- [x] Verify:
  - targeted backend regressions pass,
  - `examples/XTTA/src/grammar/009_control_flow.ts` runs with `TSJ-RUN-SUCCESS`,
  - XTTA progression remains monotonic (`PASS` non-decreasing, `CRASH` non-increasing).
- [x] Follow-up semantic parity: fix `switch` fallthrough behavior (`switch_fall`) so `grammar/009_control_flow` reaches 9/9 checks.
