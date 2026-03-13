# Plans

## 2026-03-07 Pet Clinic HTTP Strict Boot Fix (TS-only, no Java fixtures)

- [ ] `Red`: add/adjust a CLI regression that fails when `spring-package` in strict mode drops TS Spring adapters/launcher for reachable TS modules importing decorators via `java:`.
- [ ] `Green`: make adapter generation classpath-aware (for `java:` decorator resolution) so controller/component adapters are emitted instead of silently dropped.
- [ ] `Green`: ensure `spring-package` emits `dev.tsj.generated.boot.TsjSpringBootLauncher` into packaged jar when TS controllers/components are present.
- [ ] Verify with `bash examples/pet-clinic/scripts/run-http.sh` + live `curl` checks for `/api/petclinic/owners` and `/api/petclinic/owners/{ownerId}/pets`.
- [ ] Record review evidence in `docs/todo.md`.

## 2026-03-07 JVM-Strict Mode Storying + User Guide Baseline

- [x] Define new `jvm-strict` epic stories in `docs/stories.md` with explicit acceptance criteria, dependencies, and execution sequence.
- [x] Keep `jvm-strict` scope explicit: direct JVM class model subset with deterministic `TSJ-STRICT-*` diagnostics for out-of-subset features.
- [x] Add programmer-facing user guide for writing strict-eligible TypeScript (do/don't patterns + migration strategy).
- [x] Link strict-mode guide from `docs/README.md` and note planned CLI mode contract in `docs/cli-contract.md`.
- [x] Add review note in `docs/todo.md` for traceability of planning/docs deliverables.

## 2026-03-07 TSJ-78 Implementation Slice A (`--mode jvm-strict` CLI Contract)

- [x] `Red`: add CLI tests for `--mode jvm-strict` parsing/validation on `compile` and `run`.
- [x] `Red`: add CLI test asserting strict mode is persisted in compile artifact metadata.
- [x] `Green`: implement CLI option parsing with default mode unchanged and deterministic invalid-mode diagnostics.
- [x] `Green`: persist selected compilation mode in `program.tsj.properties` and success diagnostic context.
- [x] `Green`: add deterministic strict-mode scaffolding diagnostic path (`TSJ-STRICT-*`) for guarded unsupported shapes in this slice.
- [x] Verify targeted CLI/backend tests and update `docs/todo.md` review notes.

## 2026-03-07 TSJ-79 Implementation Slice A (Eligibility Gate Seed)

- [x] `Red`: add strict-mode regression for unchecked `any` member invocation in compile path.
- [x] `Green`: implement deterministic strict rejection for unchecked `any` member invocation with stable `featureId`.
- [x] Verify strict-mode targeted suite (`mode parsing + strict guards + docs drift`) remains green.

## 2026-03-07 TSJ-79 Implementation Slice B (Module Graph + Dynamic Property Add)

- [x] `Red`: add strict-mode regression for dynamic computed property write (`obj[key] = ...`) with deterministic diagnostic.
- [x] `Red`: add strict-mode regression proving imported modules are scanned (prototype mutation in dependency fails with dependency file/span).
- [x] `Green`: expand strict eligibility scan from entry-only to relative module graph traversal.
- [x] `Green`: add deterministic strict rejection for non-literal computed property writes while allowing literal index writes (`arr[0] = ...`).
- [x] Verify strict-mode targeted suite (`mode + strict diagnostics + docs drift`) remains green.

## 2026-03-07 TSJ-79 Implementation Slice C (Determinism Across Incremental Modes)

- [x] `Red`: add regression asserting strict diagnostics remain stable across non-incremental and incremental-cache-enabled compile runs.
- [x] `Green`: keep strict eligibility gate evaluation deterministic before backend/incremental stages so `featureId/file/line/column` stay stable.
- [x] Verify strict-mode targeted suite (`mode + strict diagnostics + docs drift`) remains green.

## 2026-03-07 TSJ-79 Implementation Slice D (Surface Coverage Expansion)

- [x] `Red`: add strict eligibility tests covering positive/negative class/function/object/module surfaces.
- [x] `Green`: validate strict checker behavior for class method `eval`, function `Function` constructor, and static-safe class/object/module flow.
- [x] Verify strict-mode targeted suite (`mode + strict diagnostics + docs drift`) remains green.

## 2026-03-07 TSJ-79 Implementation Slice E (Frontend Static-Analysis Pass)

- [x] `Red`: add frontend unit tests for strict eligibility checker (negative + positive + determinism).
- [x] `Green`: implement `compiler/frontend` strict eligibility analyzer and wire CLI strict gate to consume frontend results.
- [x] Verify frontend + CLI targeted reactor suite remains green.

## 2026-03-07 TSJ-80 Implementation Slice A (Strict Lowering Path Metadata Scaffold)

- [x] `Red`: extend strict compile test to require explicit strict lowering-path metadata in success diagnostics/artifact output.
- [x] `Green`: persist strict scaffold metadata (`strict.eligibility=passed`, `strict.loweringPath=runtime-carrier`) and emit `strictLoweringPath` in compile success context.
- [x] Verify strict frontend+CLI targeted reactor suite remains green.

## 2026-03-07 TSJ-80 Implementation Slice B (Initial JVM-Native Strict Lowering)

- [x] `Red`: add strict-mode runtime regression proving covered subset executes without runtime carrier object wrappers for DTO/class instances.
- [x] `Red`: add strict-mode backend regression asserting direct field/method bytecode path for covered class subset.
- [x] `Green`: implement initial strict lowering path for covered subset (class fields/getters/setters + direct method invocation) and switch `strict.loweringPath` away from `runtime-carrier` for those artifacts.
- [x] `Green`: add deterministic bridge diagnostics for constructs still requiring runtime-carrier fallback in strict mode.
- [x] Verify targeted frontend/backend/cli strict suites and update review notes.

## 2026-03-07 TSJ-81 Implementation Slice A (Strict DTO Jackson Boundary Baseline)

- [x] `Red`: add backend strict-mode regression proving strict-native DTO instances serialize/deserialize through Jackson without custom adapters.
- [x] `Red`: add Spring web adapter integration regression proving strict-mode controller invocation returns a Jackson-serializable JVM-native DTO.
- [x] `Green`: update strict-native class emission shape to framework-friendly JVM POJO baseline (public nested class, no-arg constructor, getter/setter accessors) while preserving strict invocation path.
- [x] Verify targeted strict tests and broader backend regression remain green.

## 2026-03-07 TSJ-81 Implementation Slice B (Strict Request Body + Packaged Strict Mode)

- [x] `Red`: add backend regression proving strict controller `@RequestBody` typed parameters bind into strict-native DTO instances (not `Map` fallbacks).
- [x] `Red`: add CLI packaged-web regression proving `spring-package --mode jvm-strict` is accepted and produces packaged output.
- [x] `Green`: add strict request-body coercion path from adapter invocation into strict-native DTO instances for supported named class shapes.
- [x] `Green`: wire `spring-package` option parsing and compile flow to honor `--mode default|jvm-strict`.
- [x] Verify targeted backend+CLI tests and update story/review docs.

## 2026-03-07 TSJ-82 Implementation Slice A (Strict Request-Body Type Mapping Baseline)

- [x] `Red`: add strict integration regressions for `@RequestBody` array and `Record<string, T>` coercion into strict-native DTO container shapes.
- [x] `Red`: add strict integration regressions for nullability behavior (`T | null` allowed, `T` rejects null) and deterministic unsupported-union diagnostics.
- [x] `Green`: replace request-body class-only coercion with strict type-spec coercion supporting subset mappings (`T`, `T[]`, `Array<T>`, `Record<string, T>`, nullable unions).
- [x] `Green`: enforce deterministic strict boundary diagnostics for unsupported union/type shapes with stable `TSJ-WEB-BINDING` messaging.
- [x] Verify targeted backend suite and update TSJ-82 story/review progress docs.

## 2026-03-07 TSJ-82 Implementation Slice B (Generic Signature Emission for Strict Request-Body Shapes)

- [x] `Red`: add controller generator/integration regressions asserting `@RequestBody` array/record shapes emit parameterized JVM signatures (`List<T>` / `Map<String, T>` nested forms).
- [x] `Green`: map supported strict request-body TS types (`T[]`, `Array<T>`, `Record<string, T>`, nullable unions) to deterministic Java parameter types in generated adapters.
- [x] `Green`: keep strict request-body coercion diagnostics/behavior unchanged while upgrading adapter signatures.
- [x] Verify targeted backend tests and then broader backend regression remain green.
- [x] Update TSJ-82 progress and review docs once slice-B evidence is green.

## 2026-03-07 TSJ-83 Implementation Slice A (Strict Conformance Corpus + Readiness Gate Baseline)

- [x] `Red`: add strict readiness gate tests expecting deterministic report output, strict fixture counting, and pass/fail gating.
- [x] `Red`: add strict unsupported fixture assertions for stable diagnostic code + featureId reporting.
- [x] `Green`: add strict conformance fixture roots (`tests/conformance/strict/ok`, `unsupported/strict`) and implement readiness harness/report generation.
- [x] `Green`: wire gate to run strict compile mode (`--mode jvm-strict`) and emit deterministic category totals for CI.
- [x] Verify targeted CLI gate suite and docs drift guard; update TSJ-83 story/review docs with slice-A evidence.

## 2026-03-07 TSJ-84 Implementation Slice A (Strict Guide + Release Readiness Artifact)

- [x] `Red`: add strict release-readiness gate tests requiring deterministic artifact output and explicit signoff criteria.
- [x] `Green`: publish strict release-readiness checklist artifact under `tests/conformance` sourced from strict readiness + docs coverage.
- [x] `Green`: update docs/CLI matrix references for strict mode commands, diagnostics, limitations, and migration links.
- [x] `Green`: extend docs drift guard to treat strict-mode guide/release docs as required canonical references.
- [x] Verify targeted CLI tests and docs drift guard; update TSJ-84 story/review docs with slice-A evidence.

## 2026-03-06 Pet Clinic Example (`examples/pet-clinic`)

- [x] `Red`: add a runnable verification script that fails until `examples/pet-clinic` compiles/runs with `java:`-imported Spring annotations and reflection checks.
- [x] `Green`: create `examples/pet-clinic` TypeScript app structure (domain/service/controller/bootstrap) modeled after Spring PetClinic flow.
- [x] `Green`: import Spring annotations only via `java:` modules (`org.springframework.*`) in TS files; do not rely on TSJ built-in/global decorator symbols.
- [x] `Green`: add Java fixture sources + build script for reflection probes that assert generated TS carriers retain Spring annotation metadata.
- [x] `Green`: add run script and README with exact commands for dependency resolution, fixture build, TSJ compile/run, and expected success checks.
- [x] Verify end-to-end by executing the new scripts and recording deterministic pass output.

## 2026-03-06 Pet Clinic HTTP Runtime (`examples/pet-clinic`)

- [x] `Red`: reproduce missing HTTP endpoint behavior from `run.sh` and document that compile/run verification path does not start a server.
- [x] `Green`: add Spring Boot launcher fixture and dependency wiring to start generated TSJ controllers on port `8080`.
- [x] `Green`: add a dedicated script that packages and runs Pet Clinic in server mode for `curl` checks.
- [x] Verify with live `curl` probes for `/api/petclinic/owners` and `/api/petclinic/owners/{ownerId}/pets`.

## 2026-03-06 Story Closure Wave B (TSJ-67/69/70 + TSJ-58 Final Promotion)

- [x] `TSJ-67` closure via explicit out-of-scope guard:
  add deterministic `.tsx/.jsx` unsupported diagnostics and update TGTA/readiness expectations away from parser-generic `TS1005`.
- [x] `TSJ-69` `Red`: add backend+CLI tests proving source-graph incremental cache behavior (`miss` on first build, `hit` on unchanged rebuild, `invalidated` on source/module-graph change) and expose stage telemetry.
- [x] `TSJ-69` `Green`: implement incremental source-graph cache keyed by source/module fingerprint + compiler version; emit per-stage reuse/invalidations for frontend/lowering/backend in compile diagnostics.
- [x] `TSJ-70` `Red`: add GA readiness harness tests requiring zero parse failures on certified non-TSX corpus, compatibility manifest generation, and release signoff artifact criteria.
- [x] `TSJ-70` `Green`: implement/readiness artifact generation and deterministic gate test with docs + CI wiring.
- [x] Revalidate targeted suites for TSJ-67/69/70 and then promote `TSJ-58`, `TSJ-58a`, `TSJ-58b`, `TSJ-67`, `TSJ-69`, `TSJ-70` to `Complete` only after evidence is green.

## 2026-03-06 Story Closure Wave A (Status-Lag + TSJ-68 Final AC)

- [x] Promote story statuses from `Complete (Subset)` to `Complete` where acceptance criteria are already `4/4` with no open AC gap (`TSJ-59b`, `TSJ-60`, `TSJ-61`, `TSJ-62`, `TSJ-63`, `TSJ-64`).
- [x] Revalidate TSJ-59/TSJ-59a control-flow closure with targeted runtime + differential fixture tests and promote both to `Complete`.
- [x] `Red`: extend TSJ-68 readiness gate coverage beyond example-only roots by adding explicit external corpus roots (`tests/conformance/corpus/typescript/ok`, `tests/conformance/corpus/oss/ok`) and failing if roots are missing/empty.
- [x] `Green`: add curated TypeScript-conformance-sourced fixtures and OSS-sourced fixtures (standalone compile-safe subset) and wire them into the readiness gate/report.
- [x] Update TSJ-68 status/evidence in `docs/plans.md` + `docs/stories.md` once external corpus ingestion is verified in CI-targeted gate run.
- [x] Verify with fast targeted command:
  `mvn -B -ntp -pl cli -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjTgtaCompileGateTest#tgtaNonTsxFixturesCompileWithTsjCompileSuccess+tgtaKnownFailingFixturesEmitStableDiagnosticCodes,TsjSyntaxConformanceReadinessGateTest#readinessGateGeneratesSyntaxCategoryReportAndEnforcesThresholds test`.

## 2026-03-06 TSJ-65 Closure Slice (Live-Binding + Conformance Evidence)

- [x] `Red`: add backend regression for mutable named-import live binding (`import { count, inc } ...` reflects updated `count` after `inc()`).
- [x] `Green`: update module bundling import/export synchronization so named imports re-read export cells per statement and exported function calls synchronize mutable export cells.
- [x] `Green`: add CLI differential fixture coverage for mutable named-import live binding.
- [x] Re-run targeted TSJ-65 suite (`JvmBytecodeCompilerTest` + `FixtureHarnessTest`) and verify green.
- [x] Promote TSJ-65 status/evidence from `Complete (Subset)` to `Complete` in story/audit docs once targeted gates pass.

## 2026-03-06 TSJ-66 Closure Confirmation Slice

- [x] Re-run focused TSJ-66 backend decorator coverage for stage-3 class/method/field semantics and policy diagnostics (private/accessor/getter/setter/parameter).
- [x] Re-run focused TSJ-66 CLI diagnostic-surface coverage for private/accessor/getter/setter/parameter decorator diagnostics.
- [x] Promote TSJ-66 status/evidence from `Complete (Subset)` to `Complete` in story/audit docs with no open AC gap.

## 2026-03-06 TSJ-68 Slice B (Corpus Expansion + Doc Drift Cleanup)

- [x] Reconcile stale TGTA status claims in docs to match current gate source-truth (`13/15` pass, two stable known blockers).
- [x] `Red`: add readiness-gate coverage that ingests a broader syntax corpus (TGTA + UTTA + XTTA fixtures) with deterministic per-category pass/fail accounting.
- [x] `Green`: implement corpus-manifest driven readiness report generation with expected-blocker handling and actionable per-fixture diagnostics.
- [x] Wire CI artifact/report paths for the expanded readiness gate and verify deterministic output.
- [x] Verify with fast targeted loop (`-pl cli -am`, `-Dcheckstyle.skip=true`, targeted `-Dtest`) and document outcomes in `docs/todo.md`.

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

## 2026-02-28 TGTA Non-TSX Gate Hardening + Fallback Reduction

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
- [x] Verify:
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

## 2026-03-02 README Recheck: JITA / UTTA / XTTA

- [x] Re-run `examples/JITA/scripts/run_matrix.sh` and capture current matrix outcome.
- [x] Re-run `examples/UTTA/scripts/run.sh` and capture current totals plus notable feature status.
- [x] Re-run `examples/XTTA/scripts/run.sh` and capture current totals plus notable failure families.
- [x] Update `examples/JITA/README.md` to reflect current workflow/output and remove stale claims.
- [x] Update `examples/UTTA/README.md` to reflect current measured status and scope.
- [x] Update `examples/XTTA/README.md` to reflect current measured status and scope.
- [x] Sanity-check updated README instructions/commands against current scripts and file layout.

## 2026-03-02 RITA Example: Reflection + Annotation DI JAR Interop

- [x] Create `examples/RITA` scaffold (README, spec, scripts, fixture sources, TS app).
- [x] Implement Java fixture jar with runtime-retained annotations (`@Component`, `@Inject`) and reflection-based field injection container.
- [x] Expose interop entrypoints in the jar to:
  - run Java-only annotation-driven DI path,
  - inspect arbitrary object/class annotation visibility via Java reflection.
- [x] Implement TSJ sample app that consumes the jar and prints deterministic checks for:
  - Java annotation DI success,
  - TS-authored decorator metadata visibility (or non-visibility) to Java reflection.
- [x] Add `examples/RITA/scripts/build-fixtures.sh` and `examples/RITA/scripts/run.sh` in existing example style.
- [x] Run the RITA scripts and verify deterministic pass/fail behavior.
- [x] Document measured behavior and commands in `examples/RITA/README.md`.

## 2026-03-02 Full Solution Plan: Any-Jar Annotation/Reflection + Repo Docs Integrity (TSJ-71..TSJ-77)

- [x] Confirm baseline limitation from implementation/tests: TS-authored classes are runtime `TsjObject` instances and do not currently expose JVM annotation metadata as concrete reflected TS classes.
- [x] Replan stories in `docs/stories.md` so TSJ-71..TSJ-75 explicitly target a framework-agnostic path and decommission Spring-specific annotation branches.
- [x] Architecture invariant (applies to all steps): no framework-specific annotation mapping/emission logic in default compile/run path.
- [x] `Red` (TSJ-71): add failing backend/interop tests for classpath annotation resolution from TS decorators (unresolved type, non-annotation type, target mismatch, deterministic ordering, no hardcoded framework path).
- [x] `Green` (TSJ-71): implement generic annotation symbol resolution against compile classpath and targeted diagnostics.
- [x] `Red` (TSJ-72): add failing tests that require loadable JVM metadata-carrier classes for TS-authored class declarations with reflection-readable signatures.
- [x] `Green` (TSJ-72): emit framework-neutral metadata-carrier classes for supported TS class subset and wire runtime metadata lookup.
- [x] `Red` (TSJ-73): add failing reflection tests for class/method/field/constructor/parameter annotation visibility + attribute values across non-framework-specific annotations.
- [x] `Green` (TSJ-73): implement generic annotation emission/attribute mapping with retention+target enforcement.
- [x] `Red` (TSJ-74): add failing end-to-end fixtures with multiple independent reflection consumers (DI-style + metadata scanner) that must work without TSJ framework adapters.
- [x] `Green` (TSJ-74): implement runtime/interop integration so generic reflection-consumer fixtures pass deterministically.
- [x] `Red` (TSJ-75): add failing tests asserting Spring-specific annotation bridge paths are absent from default compile/run behavior.
- [x] `Green` (TSJ-75): remove or isolate Spring-specific annotation mapping/emission/generator paths from core flow, keeping only explicit legacy mode if needed.
- [x] `TSJ-75` gate: add certification harness + CI gate + docs updates for supported subset/non-goals/migration.
- [x] `Red` (TSJ-76): add failing checks that expose obsolete/duplicate code+tests after cutover (retired path references, stale story-specific fixtures, dead diagnostics).
- [x] `Green` (TSJ-76): remove or quarantine obsolete production code/tests and consolidate docs to match the generic path.
- [x] `TSJ-76` gate: add deterministic CI guard preventing reintroduction of retired Spring-specific core-path symbols and stale suite entries.
- [x] `Red` (TSJ-77): add failing doc-integrity checks over all in-scope markdown (`docs/**/*.md`, `examples/**/README.md`, root `README.md`, specs under `examples/**`) for stale commands/features and contradictory claims.
- [x] `Green` (TSJ-77): update/merge/retire markdown docs so behavior, command examples, diagnostics, and support claims match implementation/tests.
- [x] `TSJ-77` gate: enforce deterministic CI doc-drift governance check and ownership/update workflow for future feature changes.
- [x] Verify progression after each story with targeted suites, then run full regression (`mvn -B -ntp test`).

## 2026-03-02 TSJ-75 Slice A: Remove Spring Adapter Generation from Default `compile`/`run`

- [x] `Red`: add CLI regression tests asserting Spring adapter generators are not invoked by default for `compile` and `run`.
- [x] `Green`: isolate Spring adapter generation behind explicit legacy mode, and keep `spring-package` as explicit path.
- [x] `Green`: update legacy Spring adapter tests to use explicit legacy mode contract instead of default `compile` behavior.
- [x] Verify targeted CLI/backend tests and ensure no regressions in generic annotation/reflection paths.

## 2026-03-02 TSJ-76 Slice A: Legacy Core-Path Guard and Obsolete-Test Consolidation

- [x] `Red`: convert default compile/run Spring adapter expectations into explicit guard tests (`default off`, `legacy opt-in`).
- [x] `Green`: quarantine legacy adapter generation to explicit `--legacy-spring-adapters` mode and keep Spring-path tests opt-in only.
- [x] `Gate`: add deterministic CI guard step for legacy core-path behavior.
- [x] Verify targeted CLI guard suite passes.

## 2026-03-02 TSJ-77 Slice A: Docs Drift Guard for Canonical Command Contracts

- [x] `Red`: add failing docs-drift checks that detect stale claims about default compile/run Spring adapter generation.
- [x] `Green`: update canonical docs (`docs/cli-contract.md`, `docs/README.md`) to match current implementation and legacy-mode contract.
- [x] `Gate`: add deterministic CI docs-drift guard step.
- [x] Verify docs guard test passes.

## 2026-03-02 TSJ-75 Slice B: Any-Jar Annotation Survival Certification Gate

- [x] `Red`: add a failing certification gate test requiring all annotation-survival dimensions (resolution, emission, reflection-consumer) to pass.
- [x] `Green`: implement certification harness/report artifact for TSJ-75 and deterministic gate assertions.
- [x] `Gate`: wire TSJ-75 certification gate into CI.
- [x] `Docs`: record supported subset/non-goals/migration notes for TSJ-75 gate in canonical docs/todo review.
- [x] Verify targeted certification test and CI gate command pass.

## 2026-03-03 TSJ-58c Closure: Default AST-Only Compile Path (No Silent Parser Fallback)

- [x] `Red`: add/upgrade no-fallback gate coverage so unsupported normalized-AST lowering fails deterministically in default flow (legacy fallback only via explicit debug flag).
- [x] Baseline: run high-signal suites with `-Dtsj.backend.astNoFallback=true` to identify fallback-dependent syntax/fixtures.
- [x] `Green`: implement remaining normalized-AST lowering/normalization gaps required by TSJ-59b/TSJ-60 baseline scope to remove fallback dependence.
- [x] `Green`: flip default compile behavior to AST-only for supported flow; keep token-parser fallback behind explicit debug property.
- [x] `Gate`: wire deterministic CI no-fallback regression gate over parser/backend/CLI differential surfaces.
- [x] `Docs`: update story/todo/docs status to reflect TSJ-58c completion and fallback policy.
- [x] Verify targeted suites and full regression (`mvn -B -ntp test`).

## 2026-03-03 Regression Follow-up: Kotlin Parity Certification Flake

- [x] Reproduce backend regression failure from full run (`TsjKotlinParityCertificationTest#fullParityReadyFlipsOnlyWhenDbAndSecurityParityGatesPass`).
- [x] `Green`: fix TSJ-38c startup probe to measure runtime startup (compile pre-step + run timing) instead of compile+run wall clock.
- [x] `Green`: align `fullParityReady` signal with DB/security parity gates (independent from performance dimensions).
- [x] Verify targeted Kotlin parity suites pass:
  - `TsjKotlinParityCertificationTest`
  - `TsjKotlinParityReadinessGateTest`
- [x] Re-run one clean full regression (`mvn -B -ntp test`) with no overlapping Maven jobs.

## 2026-03-03 Story Audit + Sequenced Development Plan (Planned / Complete-Subset)

### Acceptance-Criteria Coverage Audit

| Story | Status in `docs/stories.md` | AC Coverage (Met/Total) | Evidence Snapshot | Primary Remaining Gap |
|---|---|---:|---|---|
| TSJ-58 | Complete | 4/4 | Default compile path now consumes bridge normalized AST with schema diagnostics, source-module mapping, and debug-only handwritten fallback controls | No open AC gap |
| TSJ-58a | Complete | 3/3 | Typed AST contract (`astNodes` + normalized payload) is validated/consumed with deterministic schema diagnostics and default-path parser fallback disabled | No open AC gap |
| TSJ-58b | Complete | 3/3 | AST-first lowering is default source-of-truth; handwritten parser remains debug-only and conformance suites stay green | No open AC gap |
| TSJ-59 | Complete | 4/4 | Statement-form runtime coverage exists across `for`, `for...of`, `for...in`, `switch`, labels, and `do...while` paths, with differential fixture coverage for mixed nested control flow | No open AC gap |
| TSJ-59a | Complete | 4/4 | Targeted runtime tests for `for`, `switch` (incl. fallthrough), labels, and `do...while continue` pass; mixed nested control-flow differential fixture is green | No open AC gap |
| TSJ-59b | Complete | 4/4 | Mixed nested differential fixture and labeled-continue regression coverage now added (`FixtureHarnessTest#harnessSupportsIterationLabelsAndSwitchFallthroughDifferentialFixture`, `JvmBytecodeCompilerTest#supportsLabeledContinueTargetingOuterForOfLoopInTsj59bSubset`) | No open AC gap |
| TSJ-60 | Complete | 4/4 | Added operator precedence/associativity differential fixture (`FixtureHarnessTest#harnessSupportsOperatorPrecedenceDifferentialFixture`) on top of existing backend operator coverage | No open AC gap |
| TSJ-61 | Complete | 4/4 | Object/array rest + default destructuring now covered in backend + CLI parity (`JvmBytecodeCompilerTest#supportsDestructuringDefaultsAndRestAcrossDeclarationsAssignmentsParametersAndLoopHeaders`, `FixtureHarnessTest#harnessSupportsDestructuringDefaultsAndRestDifferentialFixture`) with runtime helper coverage (`TsjRuntimeTest#objectRestBuildsObjectWithoutExcludedKeys`) | No open AC gap |
| TSJ-62 | Complete | 4/4 | Added consolidated class/object Node-vs-TSJ fixture gate (`FixtureHarnessTest#harnessSupportsClassObjectConformanceDifferentialFixture`) on top of backend coverage | No open AC gap |
| TSJ-63 | Complete | 4/4 | Added nested async/generator/control-flow differential fixture (`FixtureHarnessTest#harnessSupportsAsyncGeneratorControlFlowDifferentialFixture`) on top of existing backend generator/function-form coverage | No open AC gap |
| TSJ-64 | Complete | 4/4 | Added frontend-vs-backend diagnostic separation gate in CLI tests (`TsjCliTest#compileSyntaxErrorReturnsFrontendTypeScriptDiagnosticCode` + backend unsupported counterpart) atop existing type-erasure tolerance coverage | No open AC gap |
| TSJ-65 | Complete | 4/4 | TSJ-65 module parity now includes re-export forms, relative dynamic-import lowering, mutable named-import live-binding coverage, and multi-file differential conformance (`JvmBytecodeCompilerTest#supportsReExportStarAndNamedFromInTsj65Subset`, `JvmBytecodeCompilerTest#supportsRelativeDynamicImportWithModuleNamespaceObjectInTsj65Subset`, `JvmBytecodeCompilerTest#supportsNamedImportLiveBindingForMutableExportInTsj65Subset`, `FixtureHarnessTest#harnessSupportsModuleReExportAndDynamicImportFixture`, `FixtureHarnessTest#harnessSupportsModuleLiveBindingFixtureInTsj65Subset`) | No open AC gap |
| TSJ-66 | Complete | 4/4 | Legacy/classpath decorator extraction + deterministic diagnostics are covered, stage-3 class/method/field subset lowering is implemented, explicit policy diagnostics cover private/accessor/parameter shapes (including `get`/`set` accessor decorators), and legacy-safe decorator-factory hardening is verified through TSJ-66 Slices A/B/C/D/E/F | No open AC gap |
| TSJ-67 | Complete | 4/4 | TSX/JSX is now policy-gated with deterministic unsupported diagnostics (`TSJ67-TSX-OUT-OF-SCOPE`) and explicit docs/tests | No open AC gap |
| TSJ-68 | Complete | 4/4 | Readiness gate now covers TGTA + UTTA + XTTA plus curated external corpus roots (`tests/conformance/corpus/typescript/ok`, `tests/conformance/corpus/oss/ok`) with deterministic category breakdown, pass-rate thresholds, expected-blocker stability checks, and per-fixture repro diagnostics | No open AC gap |
| TSJ-69 | Complete | 4/4 | Source-graph incremental cache + stage telemetry + warm-hit/invalidation readiness gate + CI artifact are implemented | No open AC gap |
| TSJ-70 | Complete | 4/4 | GA signoff harness + compatibility manifest + certified-corpus gate + CI artifacts are implemented | No open AC gap |

### Sequenced Story Execution (Implementation Order)

#### Wave 1: Convert “Planned but Mostly Implemented” to complete with hard evidence
- [x] TSJ-59b closure: added mixed nested differential fixture pack + labeled continue lowering fix through `LabeledStatement` traversal in bridge rewrite, and promoted status on green.
- [x] TSJ-60 closure: expanded precedence/associativity differential fixture coverage and promoted status.
- [x] TSJ-61 closure: implemented defaults/rest support breadth in bindings (including object/array rest for declaration/assignment/params), added fixture coverage, and promoted status.
- [x] TSJ-62 closure: added dedicated class/object conformance fixture gate and promoted status.
- [x] TSJ-63 closure: added nested async/generator/control-flow conformance fixture coverage and promoted status.
- [x] TSJ-64 closure: added diagnostic-separation tests and promoted status.

#### Wave 2: Close genuinely missing syntax/runtime surfaces
- [x] TSJ-65 implementation: expanded module parity (re-export/live-binding depth + dynamic import semantics policy decision).
- [x] TSJ-66 implementation: stage-3 decorator parse/lowering support or explicit policy-gated diagnostics for all unsupported proposal features.
- [x] TSJ-67 implementation: TSX/JSX policy closure via deterministic unsupported diagnostics + docs/gates.

#### Wave 3: GA-quality gates and release closure
- [x] TSJ-68 implementation: large conformance corpus + CI category breakdown + pass-rate thresholds.
- [x] TSJ-69 implementation: incremental source-graph cache, stage reuse diagnostics, warm-build thresholds, and CI/readiness governance.
- [x] TSJ-70 implementation: GA readiness gate, compatibility manifest, and release signoff artifact wired to docs/CI.

### Immediate Next Story Pick

- [x] Start with TSJ-59b (lowest-risk closure, highest leverage for reducing planned backlog without architecture churn).
- [x] Next: TSJ-60 closure (precedence/associativity differential coverage expansion).
- [x] Next: TSJ-61 closure (unsupported-binding diagnostic clarity + fixture breadth).
- [x] Next: TSJ-62 closure (class/object conformance fixture gate).
- [x] Next: TSJ-63 closure (nested async/generator/control-flow conformance fixtures + semantics notes).
- [x] Next: TSJ-64 closure (type-syntax diagnostic separation tests).
- [x] Next: TSJ-65 implementation (module parity expansion + dynamic import policy hardening).
- [x] Next: TSJ-66 implementation (stage-3 decorator parity policy + diagnostics).

## 2026-03-03 TSJ-65 Slice A: Re-export Coverage + Relative Dynamic Import Runtime

- [x] `Red`: add backend and fixture-harness tests that fail today for:
  - `export * from "./dep.ts"` and `export { x as y } from "./dep.ts"` conformance.
  - relative-literal `import("./dep.ts")` runtime semantics (promise resolves to module namespace object).
- [x] `Green`: extend module bundling to support re-export forms (`export *`, `export { ... } from`, local `export { ... }`) with deterministic ordering.
- [x] `Green`: replace current dynamic-import hard rejection for relative literals with lowered runtime helper semantics in bundled modules; keep unsupported diagnostics for out-of-policy forms.
- [x] `Refactor`: tighten module/re-export parsing helpers to keep deterministic diagnostics/spans and avoid regex duplication.
- [x] Verify targeted suites:
  - `JvmBytecodeCompilerTest` (new TSJ-65 coverage methods)
  - `FixtureHarnessTest` (new TSJ-65 differential fixture)
  - `TsjCliTest` diagnostics for unsupported out-of-policy dynamic import forms.

## 2026-03-03 TSJ-66 Slice A: Stage-3 Decorator Runtime Bridge (Class/Method)

- [x] `Red`: add backend tests that prove stage-3 decorator callback contracts currently fail:
  - class decorator receives `(value, context)` and can replace class.
  - method decorator receives `(value, context)` and can replace method.
- [x] `Green`: extend bridge decorator lowering to dual-mode invocation:
  - legacy mode remains for legacy signatures.
  - stage-3 mode applies for class/method decorators with context objects and replacement semantics.
- [x] `Refactor`: centralize stage-3 context object construction helpers to keep lowering deterministic.
- [x] Verify targeted suites:
  - `JvmBytecodeCompilerTest` (new TSJ-66 methods + legacy decorator regression method).

## 2026-03-03 TSJ-66 Slice B: Unsupported Proposal Diagnostics (Private Decorated Members)

- [x] `Red`: add backend + CLI tests for decorated private class member rejection with stable feature metadata.
- [x] `Green`: emit deterministic backend unsupported diagnostic for decorated private class elements in TSJ-66 subset:
  - `code=TSJ-BACKEND-UNSUPPORTED`
  - `featureId=TSJ66-DECORATOR-PRIVATE-ELEMENT`
  - stable source line/file context and guidance text.
- [x] Verify targeted suites:
  - `JvmBytecodeCompilerTest#rejectsDecoratedPrivateClassElementWithTsj66FeatureDiagnostic`
  - `TsjCliTest#compileDecoratedPrivateMemberIncludesTsj66FeatureContext`.

## 2026-03-03 TSJ-66 Slice C: Stage-3 Field Decorators + Accessor Policy Diagnostic

- [x] `Red`: add backend/CLI tests for:
  - stage-3 field decorator initializer transformation (`(value, context) -> initializer`).
  - unsupported stage-3 accessor decorator diagnostics with stable feature metadata.
- [x] `Green`: implement stage-3 field decorator lowering for non-accessor class fields in deterministic subset:
  - stage-3 invocation for supported bindings,
  - chained initializer transformation order,
  - legacy property decorator behavior preserved for existing call-expression patterns.
- [x] `Green`: emit explicit unsupported diagnostic for stage-3 accessor decorators in TSJ-66 subset:
  - `featureId=TSJ66-DECORATOR-STAGE3-ACCESSOR`.
- [x] Verify targeted suites:
  - `JvmBytecodeCompilerTest` (new TSJ-66 field/accessor tests + legacy decorator regression).
  - `TsjCliTest` new accessor diagnostic surface test.

## 2026-03-03 TSJ-66 Slice D: Stage-3 Parameter Decorator Policy Diagnostic

- [x] `Red`: add backend/CLI tests for stage-3-style parameter decorator rejection with stable metadata.
- [x] `Green`: emit explicit unsupported diagnostic for stage-3 parameter decorators while preserving legacy parameter decorator factory subset:
  - `featureId=TSJ66-DECORATOR-STAGE3-PARAMETER`.
- [x] Verify targeted suites:
  - `JvmBytecodeCompilerTest` new parameter diagnostic test + existing legacy parameter-decorator regression.
  - `TsjCliTest` new parameter diagnostic surface test.

## 2026-03-03 TSJ-66 Slice E: Decorator Factory Policy Hardening (Legacy-Safe)

- [x] `Red`: add backend regression for legacy method decorator factory call-expression forms (`@dec(...)`) to prevent stage-3 misclassification.
- [x] `Green`: harden stage-3 mode-selection policy:
  - stage-3 callback invocation applies to bare decorator identifiers only;
  - decorator call-expression forms stay on legacy invocation paths.
- [x] Verify targeted suites:
  - new backend regression for method decorator factory call-expression.
  - existing TSJ-66 stage-3 class/method/field and diagnostic tests remain green.

## 2026-03-04 TSJ-66 Slice F: Stage-3 Getter/Setter Decorator Policy Diagnostics

- [x] `Red`: add backend + CLI regression tests proving stage-3 decorators on `get`/`set` accessor declarations currently compile when they should be explicitly policy-rejected.
- [x] `Green`: extend accessor policy diagnostics so stage-3 decorators on accessor declarations are rejected deterministically across:
  - `accessor` fields;
  - `get` accessors;
  - `set` accessors.
- [x] `Refactor`: split method decorators into stage-3 vs legacy subsets before accessor lowering so legacy accessor decorator paths remain available while stage-3 forms are blocked with stable metadata.
- [x] Verify targeted suites:
  - `JvmBytecodeCompilerTest#rejectsStage3GetterDecoratorWithTsj66FeatureDiagnostic+rejectsStage3SetterDecoratorWithTsj66FeatureDiagnostic`
  - `JvmBytecodeCompilerTest#*Decorator*`
  - `TsjCliTest#compileDecoratedPrivateMemberIncludesTsj66FeatureContext+compileStage3AccessorDecoratorIncludesTsj66FeatureContext+compileStage3GetterDecoratorIncludesTsj66FeatureContext+compileStage3SetterDecoratorIncludesTsj66FeatureContext+compileStage3ParameterDecoratorIncludesTsj66FeatureContext`
  - `TypeScriptSyntaxBridgeConformanceSnapshotTest`.

## 2026-03-07 TSJ-78/79/80 Completion Phase 1 (Strict Gate Precision + Native Subset Expansion Seed)

- [x] `Red`: add strict eligibility tests proving current regex scanning is too coarse
  (comment/string/template false positives and typed-`any` detection gaps).
- [x] `Green`: replace regex-first strict checker with token-aware source analysis
  that ignores comments/strings and computes deterministic feature spans from lexical structure.
- [x] `Green`: expand strict unsupported progression fixtures under `unsupported/strict`
  for additional unsupported dynamic semantics now enforced by the checker.
- [x] `Refactor`: centralize strict rule definitions so CLI diagnostics, fixture expectations,
  and checker outputs stay in a single deterministic contract.
- [x] Verify:
  - `compiler/frontend` strict checker unit suite.
  - strict CLI targeted tests (`TsjCliTest` strict-mode cases).
  - `unsupported/run_progress.sh`.

## 2026-03-07 TSJ-80 Completion Phase 2 (Strict Native Lowering Coverage Expansion)

- [x] `Red`: add backend strict-native tests for currently blocked class constructs
  needed for completion promotion (starting with `if`/branching and `this` method chaining beyond trivial expressions).
- [x] `Green`: extend strict-native class validator/emitter to support those constructs
  while preserving deterministic `TSJ-STRICT-BRIDGE` diagnostics for remaining unsupported shapes.
- [x] `Green`: increase strict conformance `ok` corpus to include new strict-native class patterns
  and ensure `strict.loweringPath=jvm-native-class-subset` for them.
- [x] Verify:
  - targeted `JvmBytecodeCompilerTest` strict-native methods.
  - strict readiness/release gates.

## 2026-03-07 TSJ-78/79/80 Closure Promotion + Unsupported Runner Stability

- [x] `Red`: reproduce unsupported progression runner drift caused by stale CLI/backend classpath artifacts and strict fixture metadata parse false negatives.
- [x] `Green`: harden `unsupported/run_progress.sh` and `unsupported/jarinterop/run_progress.sh` with one-time reactor bootstrap (`-pl cli -am install -DskipTests`) for deterministic local snapshots.
- [x] `Green`: fix strict fixture metadata extraction regex in `unsupported/run_progress.sh` so `EXPECT_CODE`/`EXPECT_FEATURE_ID` are read correctly.
- [x] Verify progression outputs:
  - `unsupported/strict` now reports deterministic `passed=4 failed=0`.
  - `unsupported/jarinterop` reports deterministic `passed=5 failed=0`.
  - `unsupported/grammar` reports only current feature-gap failures (`014_eval_call.ts`, `016_function_constructor.ts`).
- [x] Promote story/sprint docs from subset to complete where strict AC closure evidence is now explicit (`TSJ-78`, `TSJ-79`, `TSJ-80`, `Sprint P22`, `Sprint P23`).

## 2026-03-07 Pet Clinic Strict + JPA/H2 Migration

- Superseded by `2026-03-08 Full Any-Jar No-Hacks Re-Architecture (TSJ-85..TSJ-92)`.
- Reason:
  this narrower example plan still assumes the current Spring-specific packaging/native-subset architecture,
  which is not sufficient for the final no-hacks any-jar goal.

- [ ] `Red`: capture current example behavior gaps (non-strict compile path, in-memory arrays, no JPA/Hibernate persistence) with a failing/insufficient baseline run note.
- [ ] `Green`: migrate `examples/pet-clinic` compile/run scripts to `--mode jvm-strict` for verification and HTTP paths.
- [ ] `Green`: add JPA/Hibernate + H2 runtime dependencies in `examples/pet-clinic/pom.xml` and refresh dependency resolution flow.
- [ ] `Green`: replace in-memory repository/service arrays with Spring Data JPA backed persistence (`EntityManager`/repository path) using H2 in-memory database.
- [ ] `Green`: add/adjust fixture launcher/resources to bootstrap H2 schema/data and ensure Spring Boot can serve endpoints with persistent domain entities.
- [ ] `Refactor`: simplify TS domain/service/controller shapes to strict-friendly typed classes/interfaces (eliminate `any[]` stores in pet-clinic source).
- [ ] Verify end-to-end:
  - [ ] `bash examples/pet-clinic/scripts/run.sh` passes in strict mode.
  - [ ] `bash examples/pet-clinic/scripts/run-http.sh` starts server and `curl` endpoints return data from H2-backed JPA path.
  - [ ] `examples/pet-clinic/README.md` commands/expectations match implemented strict/JPA flow.

## 2026-03-07 Pet Clinic Replan (TS-Only, No Java Fixtures)

- Superseded by `2026-03-08 Full Any-Jar No-Hacks Re-Architecture (TSJ-85..TSJ-92)`.
- Reason:
  this plan fixes one example on top of the current Spring-specific path; the new plan removes the path itself.

- [ ] Remove newly introduced Java fixture application logic for pet-clinic (repositories/entities/controllers/bootstrap/resources) and keep implementation ownership in TS.
- [ ] Keep dependency resolution via jars only (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `h2`) with no custom app fixtures.
- [ ] Extend strict-mode class lowering to support TS repository/service/controller call patterns needed by pet-clinic (field-member method invocation path) while preserving deterministic diagnostics.
- [ ] Refactor pet-clinic TS sources to TS-only Spring/JPA shape:
  - TS entities with `java:` JPA annotations.
  - TS repository/service/controller classes.
  - TS entrypoints for strict compile/run and HTTP packaging.
- [ ] Update scripts to use TSJ packaging/run flow in strict mode (no fixture build step).
- [ ] Verify:
  - [ ] `bash examples/pet-clinic/scripts/run.sh` strict verification passes.
  - [ ] `bash examples/pet-clinic/scripts/run-http.sh` serves data from H2-backed JPA entities from TS-defined app code.
  - [ ] `examples/pet-clinic/README.md` reflects TS-only + strict commands.

## 2026-03-08 Full Any-Jar No-Hacks Re-Architecture (TSJ-85..TSJ-92)

- Goal:
  make TSJ support framework-heavy jars such as Spring Boot, Spring DI/AOP/Web, Hibernate/JPA, Jackson, and arbitrary reflection-heavy jars
  without framework-specific logic in TSJ CLI/compiler/runtime and without TS-application hacks.
- Architecture invariant:
  supported any-jar applications must work through one generic JVM-native path.
  No metadata carriers, Spring-specific adapters, generated boot launchers, `spring-package`, or TSJ-owned DI/web glue may be required.
- Root-cause inventory from current code:
  default executable class semantics still run through `TsjObject` / `TsjClass`;
  annotation survival on real apps still depends on metadata carriers;
  Spring app execution still depends on `TsjSpringComponentGenerator`, `TsjSpringWebControllerGenerator`,
  generated boot-launcher code, custom request-body coercion, and Spring-specific packaging entrypoints;
  decorator extraction for framework-facing paths still depends on regex/model shortcuts instead of the typed frontend declaration model.

### TSJ-85 Red Baseline: certify the real target and current blockers

- [x] `Red`: add certification fixtures for:
  - a TS-only Spring Boot + Hibernate/JPA app;
  - a TS-only Spring AOP/web/DI app;
  - a non-Spring reflection-heavy jar consumer.
- [x] `Red`: make fixtures fail if execution depends on:
  metadata carriers,
  `spring-package`,
  `--legacy-spring-adapters`,
  generated boot-launcher code,
  or TSJ framework glue helper entrypoints.
- [x] `Green`: publish a deterministic blocker report mapping each failure to concrete core-path code.
- [x] Verify:
  targeted certification suite is red for the right reasons and stores an artifact in CI/local target output.
- Progress 2026-03-08:
  added `tests/conformance/anyjar-nohacks/{generic_package_probe,non_spring_reflection_consumer,spring_web_jpa_app,spring_aop_web_di_app}`
  plus `cli/src/test/java/dev/tsj/cli/TsjAnyJarNoHacksBaseline{Report,Harness,Test}.java`.
  Local verification:
  `mvn -B -ntp -pl cli -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjAnyJarNoHacksBaselineTest test`
  now generates `cli/target/tsj85-anyjar-nohacks-baseline.json`.
  CI now runs the same suite and uploads the baseline JSON as an artifact.
  Current proven blockers are:
  missing generic `package`,
  dependency on `spring-package`,
  generated Spring/web adapters,
  generated Boot launcher,
  dependency on `--legacy-spring-adapters`,
  dependency on framework glue helper entrypoints,
  and runtime annotations landing on metadata carriers instead of executable classes.

### TSJ-86 Frontend-backed declaration model replaces regex/mapping shortcuts

- [x] `Red`: add failing frontend/backend tests that require typed declaration extraction for complex class/decorator/generic syntax.
- [x] `Green`: replace regex-driven framework metadata extraction with a typed frontend-backed JVM declaration model.
- [x] `Green`: remove hard dependency on `TsDecoratorModelExtractor` and `TsDecoratorAnnotationMapping` from the native any-jar path.
- [x] `Refactor`: centralize imported-annotation/classpath resolution so all framework jars flow through the same symbol model.
- [x] Verify:
  frontend snapshots + backend declaration tests cover multiline/generic/nested class shapes and deterministic spans.
- Progress 2026-03-08:
  added `TsFrontendDecoratorModelExtractor` and extended the frontend bridge payload with typed decorator declarations.
  `JvmBytecodeCompiler.resolveMetadataCarrierDeclarations(...)` now uses the frontend-backed extractor instead of the regex-based `TsDecoratorModelExtractor`.
  Added coverage in
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`
  for definite-assignment fields plus multiline constructor/method signatures, and
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsFrontendDecoratorModelExtractorTest.java`
  for aliased generic class shapes across a relative-import graph.
  `TsjSpringComponentGenerator` and `TsjSpringWebControllerGenerator` now also consume
  the frontend-backed extractor instead of the legacy regex extractor, with local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjSpringComponentGeneratorTest,TsjSpringWebControllerGeneratorTest,TsjSpringComponentIntegrationTest,TsjSpringWebControllerIntegrationTest test`
  passed.
  Extended the frontend declaration model with source spans, visibilities, raw generic parameter declarations,
  raw `extends`/`implements` clauses, field type annotations, method return type annotations, and parameter-property visibility.
  Added dedicated frontend declaration snapshots in
  `compiler/backend-jvm/src/test/resources/declaration-model/`
  plus unit coverage in
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TypeScriptDeclarationModelSnapshotTest.java`
  and
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TypeScriptSyntaxBridgeTest.java`.
  Centralized classpath-aware extractor creation in `TsFrontendDecoratorModelExtractor.createClasspathAware(...)`
  so the native compiler and both legacy Spring helper generators share the same imported-annotation resolution path.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsFrontendDecoratorModelExtractorTest,TypeScriptDeclarationModelSnapshotTest,TypeScriptSyntaxBridgeTest,JvmBytecodeCompilerTest#emitsImportedRuntimeAnnotationsOnMetadataCarrierForMultilineCtorMethodAndDefiniteField,TsjSpringComponentGeneratorTest,TsjSpringWebControllerGeneratorTest,TsjSpringComponentIntegrationTest,TsjSpringWebControllerIntegrationTest test`
  passed.

### TSJ-87 Executable JVM classes replace metadata carriers

- [x] `Red`: add failing tests proving supported native-mode classes must be executable reflection targets without carrier companions.
- [x] `Green`: emit concrete JVM classes for supported TS classes so reflection, instantiation, and invocation all target the same class.
- [x] `Green`: preserve fields/getters/setters/constructors/parameter names/generic signatures on emitted classes.
- [x] `Refactor`: remove metadata-carrier reliance from supported native-mode classes and confine any fallback to explicit unsupported diagnostics.
- [x] Verify:
  reflection/invocation tests show no carrier class is needed for supported native-mode workloads.
- Progress 2026-03-08:
  strict planning now discovers eligible classes inside bundled module initializers,
  supported strict-native classes emit top-level executable JVM classes in `dev.tsj.generated`,
  and metadata carriers are filtered out for those classes.
  The executable class path now emits non-final proxy-friendly shapes with:
  typed fields,
  typed constructors,
  typed getters/setters,
  typed reflected method signatures,
  direct runtime annotations on the executable class,
  and object-bridge constructors/invocation casts so the strict runtime still instantiates the same emitted class.
  Added and greened targeted coverage in
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`
  for:
  `strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier`,
  and
  `strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape`.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries,TsjSpringWebControllerIntegrationTest test`
  which passed.

### TSJ-88 Expand native lowering to framework-complete application subset

- [x] `Red`: add failing multi-class application tests covering real repository/service/controller/entity workloads.
- [x] `Green`: extend native lowering to cover framework-application class/member/body constructs:
  inheritance/super,
  field initializers,
  static members,
  object/array literals,
  closures,
  control flow,
  exceptions,
  service/repository/controller call chains,
  and boundary collection/nullability shapes.
- [x] `Green`: keep deterministic unsupported diagnostics for shapes still outside the native path.
- [x] Verify:
  pet-clinic-class workloads compile as native JVM classes with no adapter generation.
- Progress 2026-03-08:
  added red coverage in
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`
  for
  `strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult`,
  a direct strict-native `Repo -> Service -> Controller` chain that returns an array literal from the repository.
  The initial failure was deterministic:
  `TSJ-STRICT-BRIDGE` rejected `ArrayLiteralExpression` inside strict class bodies.
  Greened the first TSJ-88 slice by extending strict validation/emission to allow array/object literals
  through the existing `TsjRuntime.arrayLiteral(...)` / `TsjRuntime.objectLiteral(...)` path.
  Follow-up regression during the broader rerun exposed that typed strict-native `number` members were emitted as `Double`,
  which caused runtime bridge casts like `Integer -> Double` to fail.
  Fixed that by mapping TS `number` to JVM `Number` on executable strict-native classes.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult,TsjSpringWebControllerIntegrationTest test`
  which passed.
  Added the next red/green slice in the same test class:
  `strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody`.
  The initial failure was again deterministic:
  `TSJ-STRICT-BRIDGE` rejected `TryStatement` in strict class bodies.
  Greened that by extending strict validation/emission to support:
  `ThrowStatement`
  and
  `TryStatement`
  with catch-binding locals wired through
  `TsjRuntime.raise(...)`
  and
  `TsjRuntime.normalizeThrown(...)`.
  Verified the broadened strict path with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody,TsjSpringWebControllerIntegrationTest test`
  which passed.
  Added the next control-flow slice in the same test class:
  `strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody`.
  The initial failure was deterministic:
  `TSJ-STRICT-BRIDGE` rejected `WhileStatement` in strict class bodies.
  Greened that by extending strict validation/emission to support plain
  `WhileStatement`
  loops over the existing strict expression/assignment subset.
  This widened strict-native control flow without yet taking on the larger structural work for
  static members
  or
  inheritance/super.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody test`
  which passed.
  Added the next structural slice in the same test class:
  `strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch`.
  The initial failure was deterministic:
  `TSJ-STRICT-BRIDGE` rejected `extends` outright for strict-native classes.
  Greened that by extending strict validation/source emission to support derived classes when the base class is also lowered through the strict-native path,
  including:
  real Java `extends`,
  validated `super(...)` constructor calls,
  zero-arg base-constructor checks for implicit derived constructors,
  and superclass fallback in
  `__tsjInvoke(...)`
  /
  `__tsjSetField(...)`
  so inherited methods and injectable fields remain reachable through the string-dispatch path.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods,TsjSpringWebControllerIntegrationTest test`
  which passed.
  Added the next inheritance-adjacent slice in the same test class:
  `strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods`.
  The initial failure was deterministic:
  normalized `super.member(...)` calls lowered to a helper form that strict validation still rejected as
  “Only member call expressions are supported”.
  Greened that by teaching strict validation to accept normalized
  `__tsj_super_invoke`
  calls on derived classes and by lowering them to direct Java
  `super.method(...)`
  dispatch,
  with method-name resolution following the strict-native superclass chain.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods test`
  which passed.
  Added the next closure slice in the same test class:
  `strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod`.
  The initial failure was deterministic:
  strict validation rejected
  `FunctionExpression`
  outright inside strict-native method bodies.
  Greened that by extending strict validation/lowering to support lexical arrow functions and direct invocation of callable local bindings,
  while still rejecting dynamic `function(){}` forms in the strict-native path.
  Callable local invocation now lowers through
  `TsjRuntime.call(...)`,
  and lexical arrow bodies lower through the strict-native statement emitter itself.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod test`
  which passed.
  Added the next member-model slice in the same test class:
  `strictJvmExecutableClassSupportsStaticMethodEmission`.
  The initial failure was deterministic:
  the strict-native executable class did not emit any reflected static JVM method,
  so lookup of
  `Metrics__TsjStrictNative.twice(Number)`
  failed with
  `NoSuchMethodException`.
  Greened that by extending the normalized class-declaration model and strict-native emitter to carry
  and emit static methods end to end:
  frontend bridge payload,
  AST lowering,
  parser,
  optimizer rewrite,
  strict-native class model,
  and Java source emission.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmExecutableClassSupportsStaticMethodEmission test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmExecutableClassSupportsStaticMethodEmission,TsjSpringWebControllerIntegrationTest test`
  passed.
  Before taking on the next gap, added a proof regression:
  `strictJvmNativeSubsetSupportsInstanceFieldInitializers`.
  That test passed immediately with no compiler changes, confirming that instance field initializers were already preserved by the frontend bridge via constructor-body injection.
  So instance field initializers are no longer treated as an open TSJ-88 blocker.
  Added the next real gap regression in the same test class:
  `strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization`.
  The initial failure was deterministic:
  the strict-native executable class did not emit any reflected static field,
  so lookup of
  `Metrics__TsjStrictNative.base`
  failed with
  `NoSuchFieldException`.
  Greened that by extending the normalized class model and strict-native emitter to preserve static fields end to end:
  frontend bridge payload,
  AST lowering,
  parser normalization,
  optimizer rewrites,
  strict-native validation,
  strict-native class models,
  and final Java source emission with JVM static initialization.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next static-member usability slice in the same test class:
  `strictJvmNativeSubsetSupportsStaticMemberAccessByClassName`.
  The initial failure was deterministic:
  strict validation still enforced the old rule that only
  `this.<field>`
  access was allowed,
  so current-class references like
  `Metrics.base`
  and
  `Metrics.twice(...)`
  failed before lowering.
  Greened that by extending strict validation and source emission to allow
  current-class static field reads and current-class static method calls by class name,
  while still rejecting arbitrary external class references.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsStaticMemberAccessByClassName test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next closure slice in the same test class:
  `strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter`.
  The initial failure was deterministic:
  strict validation still treated lexical arrows as parameter-only scopes,
  so references to enclosing bindings like
  `input`
  and
  `offset`
  failed with
  `Unknown identifier`
  diagnostics.
  Greened that by widening lexical-arrow validation to close over enclosing bindings and by boxing strict-native locals/parameters into
  `dev.tsj.runtime.TsjCell`
  whenever a scope contains lexical closures.
  That preserves by-reference capture identity while staying inside the strict-native executable path.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next broader static semantics slice in the same test class:
  `strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess`.
  The initial failure was deterministic:
  strict validation still rejected static field assignment with the old
  `Only this.<field> assignments`
  rule.
  After widening validation/lowering to recognize strict-native static members across class boundaries,
  the next failure was also deterministic:
  cross-class Java source emission could see the target static field name,
  but the emitted field was still `private`.
  Greened that by:
  allowing strict-native validation for
  `<StrictClass>.<staticField>` reads/writes and
  `<StrictClass>.<staticMethod>(...)` calls when the target class is also in the strict-native set,
  routing lowering through the owning emitted strict-native class model,
  and emitting strict-native static fields in an accessible JVM shape for same-package class access.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next non-arrow closure slice in the same test class:
  `strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis`.
  The initial failure was deterministic:
  strict validation still rejected non-arrow
  `FunctionExpression`
  bodies with the older
  `Only lexical arrow functions`
  diagnostic.
  After widening validation, the next failure stayed deterministic:
  recursive validation lost dynamic-`this` scope and rejected
  `this.base`
  as an unknown class field.
  Greened that by threading strict-native scope context through validation and lowering:
  lexical arrows continue to capture their enclosing `this`,
  non-arrow function expressions switch to a dynamic call-site `this`,
  `this.<member>` reads/writes inside those dynamic scopes lower through
  `TsjRuntime.getProperty(...)`
  /
  `TsjRuntime.setProperty(...)`,
  and captured locals still use the existing
  `TsjCell`
  boxing path.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next loop-control slice in the same test class:
  `strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop`.
  The initial failure was deterministic:
  strict-native validation still rejected
  `ContinueStatement`
  as an unsupported class statement,
  which also confirmed that loop-control exits remained outside the executable subset even after `while` itself was supported.
  Greened that by extending strict-native validation and lowering for the minimal useful case:
  unlabeled
  `break`
  and
  `continue`
  now pass through inside strict-native loop bodies as direct Java loop-control statements,
  while labeled variants still fail deterministically.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next proof slice in the same test class:
  `strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue`.
  No compiler change was required.
  This regression proves that the parser's existing
  `do...while`
  lowering to
  `while (true) { ... if (!cond) break; }`
  is now accepted end to end by the strict-native executable path after the unlabeled
  `break`
  /
  `continue`
  slice landed.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next closure/general-programming slice in the same test class:
  `strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture`.
  The initial failure was deterministic:
  strict validation treated the call-before-declaration form as an unknown callable identifier,
  and the first lowering attempt then failed at runtime with
  `Value is not callable: null`
  because the local function binding existed but had not been initialized before use.
  Greened that by teaching the strict-native path to predeclare local function bindings at block scope,
  allocate boxed
  `dev.tsj.runtime.TsjCell`
  storage for them in closure-bearing scopes,
  and emit hoisted callable assignments before normal statement execution.
  Validation now also predeclares local function names before descending into blocks so call-before-declaration is accepted deterministically,
  while the lowering path shares the existing strict-native function-value emitter for both function expressions and local function declarations.
  Verified locally with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next lowered-loop slice in the same test class:
  `strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral`
  and
  `strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral`.
  The initial failure was deterministic:
  parser-lowered collection loops were already normalized into helper-driven `while` loops,
  but strict validation still rejected the lowered helper callee
  `__tsj_for_of_values`
  as an unknown callable identifier.
  Greened that by treating lowered collection helpers as strict-native intrinsics:
  `__tsj_for_of_values`,
  `__tsj_for_in_keys`,
  `__tsj_index_read`,
  and
  `__tsj_optional_index_read`
  now validate and lower directly to their corresponding
  `dev.tsj.runtime.TsjRuntime`
  calls.
  The same slice also widens strict-native member reads just enough for the lowered loop shape:
  local
  `<binding>.length`
  now lowers through
  `TsjRuntime.getProperty(...)`,
  which is what the normalized `for...of` / `for...in` loops already rely on.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next lowered-binding slice in the same test class:
  `strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding`.
  The initial failure was deterministic:
  array destructuring inside parser-lowered `for...of` did not reuse the index-read helper path.
  Instead, the frontend normalized bindings like
  `[left, right]`
  into ordinary property reads on the current iteration value
  (`value.0`, `value.1`),
  which the strict-native path still rejected.
  Greened that by broadening strict-native property-read handling for non-`this` receivers:
  direct JVM lowering is still used for
  `this.<field>`
  and
  `<StrictClass>.<staticField>`,
  while all other member reads now validate by recursively validating the receiver expression and lower through
  `dev.tsj.runtime.TsjRuntime.getProperty(...)`.
  That converts frontend-lowered destructuring shapes into supported strict-native forms instead of proliferating more one-off helper exceptions.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the companion object-pattern proof slice in the same test class:
  `strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding`.
  No compiler change was needed after the prior property-read widening:
  the same non-`this` member-read path that made array destructuring executable also covers lowered object-pattern bindings
  (`value.left`, `value.right`).
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next loop-control slice in the same test class:
  `strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops`.
  The initial failure was deterministic:
  strict-native validation rejected
  `LabeledStatement`
  outright before labeled `break` / `continue` resolution was even considered.
  Greened that by threading label scope through the strict-native validator and statement emitter:
  labeled statements now register source labels in scope,
  labeled `continue` is restricted to enclosing labeled loops,
  labeled `break` resolves against enclosing labels,
  and function boundaries reset label scope so loop control cannot leak across closures.
  Lowering now emits real Java labels for strict-native
  `while`
  loops and labeled blocks, aligning the executable subset with the existing non-strict backend model.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the companion proof slice for parser-lowered `do...while` with `continue`:
  `strictJvmNativeSubsetSupportsDoWhileLoopWithContinue`.
  No compiler change was needed after the prior loop-control work.
  The frontend already lowers
  `do...while`
  with `continue`
  into a `while (true)` form with inserted exit guards, and the strict-native path now covers the required ingredients:
  `if`,
  `continue`,
  `break`,
  and the lowered loop shell itself.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsDoWhileLoopWithContinue test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Added the next assignment-shape slice in the same test class:
  `JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsLocalObjectPropertyMutation`.
  The initial failure was deterministic:
  strict-native validation still rejected any member assignment target outside
  `this.<field>`
  and
  `<StrictClass>.<staticField>`,
  even though the same path already allowed the corresponding non-`this` member reads.
  Greened that by widening strict-native assignment validation and lowering for the useful non-dynamic case:
  supported receiver expressions now validate as assignment receivers,
  and non-`this` member writes lower through
  `TsjRuntime.setProperty(...)`
  just like the existing non-`this` member reads lower through
  `TsjRuntime.getProperty(...)`.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsLocalObjectPropertyMutation test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Remaining `TSJ-88` gaps are narrower again:
  broader computed/element-style write shapes beyond the normalized helper form,
  and the other application/member shapes still outside the executable strict-native subset.
  Added the next assignment-target slice:
  strict-native support for parser-lowered
  `__tsj_index_read(...) = ...`
  assignment targets.
  The red target was:
  a strict-native method mutates object/array state through
  `state[key] = ...`
  and
  `values[1] = ...`.
  The initial failure was deterministic:
  strict-native validation still only accepts assignment targets shaped as
  local bindings,
  member access,
  or supported strict static fields,
  even though the parser already normalizes index reads to the helper form and strict-native lowering already supports those reads.
  Greened that by extending strict-native validation and lowering to recognize the normalized index-assignment target directly:
  `__tsj_index_read(receiver, key)` now validates as an assignment target in strict-native mode,
  and lowering routes the write through
  `TsjRuntime.setPropertyDynamic(receiver, key, value)`.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsIndexAssignmentTargets test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Remaining `TSJ-88` gaps are narrower again:
  broader computed/element-style write shapes beyond the normalized helper form,
  and the other application/member shapes still outside the executable strict-native subset.
  Added the next expression slice:
  strict-native support for plain
  `=`
  assignment expressions that must return the assigned value.
  The red target was:
  a strict-native method returns the value of
  `(state.count = state.count + 41)`.
  The initial failure was deterministic:
  strict-native statement lowering now covers assignment targets well,
  but strict-native expression validation/emission still treat
  `AssignmentExpression`
  as outside the executable subset.
  Greened that by sharing strict assignment-target validation with expression validation and by adding strict-native lowering for plain
  `=`
  assignment expressions across the existing supported target shapes:
  locals,
  member writes,
  and normalized index writes.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsAssignmentExpressionResult test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Remaining `TSJ-88` gaps are narrower again:
  broader computed/element-style write shapes beyond the normalized helper form,
  and the other application/member shapes still outside the executable strict-native subset.
  Next slice:
  compound assignment expressions in strict-native code.
  Red target:
  a strict-native method returns the combined result of
  `+=`
  across
  a local variable,
  a local object member,
  a normalized object index target,
  and a normalized array index target.
  Expected initial failure:
  strict-native assignment-expression validation/lowering now handles plain
  `=`,
  but still rejects compound assignment operators in expression position.
  Greened that by extending the shared strict assignment-expression path to accept the existing compound operator family:
  arithmetic/bitwise compound operators now reuse the same binary-operator lowering as the non-strict path,
  while
  `&&=`,
  `||=`,
  and
  `??=`
  reuse the same runtime helper model already used for property/index assignment and boxed locals.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Remaining `TSJ-88` gaps are narrower again:
  broader computed/element-style write shapes beyond the normalized helper form,
  and the other application/member shapes still outside the executable strict-native subset.
  Next proof slice:
  logical compound assignment expressions in strict-native code.
  Red target:
  a strict-native method returns the combined results of
  `??=`,
  `&&=`,
  and
  `||=`
  in expression position over local bindings.
  Expectation:
  this should already be green after the broader compound-assignment work,
  and the slice exists to certify the runtime-helper semantics instead of just inferring them.
  Verified with:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsLogicalCompoundAssignmentExpressionResults test`
  which passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsLogicalCompoundAssignmentExpressionResults+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Remaining `TSJ-88` gaps are narrower again:
  broader computed/element-style write shapes beyond the normalized helper form,
  and the other application/member shapes still outside the executable strict-native subset.
  Next slice:
  optional member access and optional call in strict-native code.
  Red target:
  a strict-native method returns the combined results of
  `holder?.value`,
  `missing?.value`,
  `holder.read?.()`,
  and
  `missingFn?.()`
  so short-circuiting and receiver-aware optional invocation both stay inside the executable subset.
  The initial failure was deterministic:
  strict-native validation still rejected
  `OptionalMemberAccessExpression`
  as an unsupported class expression, even though the general backend already supported optional chaining.
  Greened that by extending
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java`:
  strict-native validation now walks
  `OptionalMemberAccessExpression`
  and
  `OptionalCallExpression`
  nodes,
  and strict-native lowering now reuses the existing runtime helpers
  `TsjRuntime.optionalMemberAccess(...)`,
  `TsjRuntime.optionalInvokeMember(...)`,
  and
  `TsjRuntime.optionalCall(...)`
  instead of introducing a separate strict-only model.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsOptionalMemberAccessAndOptionalCall test`
  passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsLogicalCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsOptionalMemberAccessAndOptionalCall+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Practical readout:
  strict-native executable classes now cover the common optional-chaining read/call shapes already used by ordinary application code,
  while still sharing the same runtime semantics as the general backend instead of diverging into a special strict-only implementation.
  Next slice:
  direct
  `new`
  construction for strict-native class names.
  Red target:
  a strict-native method constructs another strict-native class with
  `new Point(20, 22)`
  and immediately calls an instance method on the result.
  Scope note:
  this slice is intentionally limited to TS-defined strict-native class names.
  Imported/module-level constructor name resolution remains separate work and is not being silently conflated with this step.
  The initial failure was deterministic:
  strict-native validation still rejected
  `NewExpression`
  before lowering.
  Greened that by extending
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java`:
  strict-native validation now traverses
  `NewExpression`,
  direct strict-native class-name constructors lower to real JVM
  `new NativeClass(...)`
  calls,
  and already-valid non-strict constructor expressions fall back to the existing
  `TsjRuntime.construct(...)`
  path.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsNewExpressionForStrictNativeClassNames test`
  passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsLogicalCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsOptionalMemberAccessAndOptionalCall+strictJvmNativeSubsetSupportsNewExpressionForStrictNativeClassNames+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Practical readout:
  strict-native executable classes can now construct other strict-native classes directly as JVM objects instead of forcing those cases back to the legacy runtime-carrier path.
  Next slice:
  module-scope binding reads inside strict-native methods.
  Red target:
  a strict-native method reads a top-level constant and calls a top-level function from the same enclosing module/root scope.
  Scope note:
  this slice is read-only.
  Top-level binding mutation and cross-module name resolution remain separate work.
  The initial failure was deterministic:
  strict-native validation still seeded only parameter/local bindings,
  so the focused test failed with an
  `Unknown callable identifier`
  diagnostic for the top-level function.
  Greened that by extending
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java`
  in three connected places:
  top-level class discovery now carries the visible binding names from the enclosing root/module scope,
  strict-native validation seeds those names into method/class validation,
  and the generated program class now exposes a generic top-level binding resolver backed by a declaration-time-populated
  `TsjCell`
  map that strict-native classes can read from without duplicating module state.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsTopLevelConstAndFunctionBindings test`
  passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsLogicalCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsOptionalMemberAccessAndOptionalCall+strictJvmNativeSubsetSupportsNewExpressionForStrictNativeClassNames+strictJvmNativeSubsetSupportsTopLevelConstAndFunctionBindings+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Practical readout:
  strict-native executable classes can now read same-module top-level const/function/class bindings through the generated program binding resolver instead of treating every non-local identifier as unsupported.
  Next slice:
  top-level binding mutation from strict-native methods.
  Red target:
  a strict-native method updates a top-level
  `let`
  binding, returns the new value, and a second invocation observes the persisted updated value.
  Scope note:
  this slice is limited to same-module/root top-level bindings already visible through the strict-native binding resolver.
  Cross-module mutation semantics remain separate work.
  The initial failure was deterministic:
  strict-native lowering still treated variable writes as either local boxed cells or direct JVM locals,
  so the focused test failed with
  `Unknown variable assignment target`
  for the top-level
  `let`.
  Greened that by extending
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java`
  in both strict-native assignment paths:
  statement-form top-level writes now call
  `__tsjResolveTopLevelBinding(...).set(...)`,
  and assignment-expression top-level writes reuse the existing cell-assignment helper path through
  `emitVariableAssignmentExpression(...)`.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsTopLevelLetMutation test`
  passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset+strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier+strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier+strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape+strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries+strictJvmNativeSubsetSupportsIfStatementInClassMethodBody+strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields+strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult+strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody+strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody+strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop+strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue+strictJvmNativeSubsetSupportsDoWhileLoopWithContinue+strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch+strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods+strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod+strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter+strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis+strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture+strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral+strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding+strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding+strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops+strictJvmNativeSubsetSupportsLocalObjectPropertyMutation+strictJvmNativeSubsetSupportsIndexAssignmentTargets+strictJvmNativeSubsetSupportsAssignmentExpressionResult+strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsLogicalCompoundAssignmentExpressionResults+strictJvmNativeSubsetSupportsOptionalMemberAccessAndOptionalCall+strictJvmNativeSubsetSupportsNewExpressionForStrictNativeClassNames+strictJvmNativeSubsetSupportsTopLevelConstAndFunctionBindings+strictJvmNativeSubsetSupportsTopLevelLetMutation+strictJvmExecutableClassSupportsStaticMethodEmission+strictJvmNativeSubsetSupportsInstanceFieldInitializers+strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization+strictJvmNativeSubsetSupportsStaticMemberAccessByClassName+strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess,TsjSpringWebControllerIntegrationTest test`
  passed.
  Practical readout:
  same-module top-level state can now be both read and mutated from strict-native methods without falling back to the runtime-carrier path.
  Next slice:
  bundled-module binding resolution inside strict-native methods.
  Red targets:
  a strict-native class in a bundled module reads imported
  `const`
  / function bindings,
  and two bundled modules with the same local binding name stay isolated instead of bleeding through the shared resolver.
  The initial failure was deterministic:
  strict-native validation already accepted the bundled-module aliases,
  but lowering still only knew about the root-program binding set,
  so the focused import test failed with
  `Unknown callable variable 'next' in strict-native lowering`.
  Greened that by extending
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java`
  in the strict-native binding path:
  top-level class discovery now carries module-local binding lookup targets for classes discovered inside bundled
  `__tsj_init_module_*`
  initializers,
  module-initializer bindings register into
  `__TSJ_TOP_LEVEL_BINDINGS`
  under stable module-scoped keys instead of a shared flat name,
  and strict-native reads/calls/writes resolve through those per-class module binding targets when the root binding set is not enough.
  That keeps bundled-module strict-native code on the same generic binding resolver instead of adding module- or framework-specific execution branches.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsImportedConstAndFunctionBindings+strictJvmNativeSubsetKeepsModuleBindingScopesSeparatedAcrossModules test`
  passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest test`
  passed (`233` tests, `0` failures, `0` errors).
  Regression-note:
  that full sweep also exposed two stale expectations in
  `JvmBytecodeCompilerTest`:
  bundled module classes now lower through the strict-native path instead of runtime-carrier fallback,
  and the legacy fallback-rejection test had to be retargeted to a genuinely unsupported strict-native shape
  (`delete this.value`)
  because its old callable-transform example is now supported.
  Practical readout:
  strict-native executable classes can now use bundled-module/imported bindings without global-name collisions, and the broad backend class is green on the updated contract.
  Next slice:
  live imported-binding semantics across bundled module calls.
  Red target:
  an imported
  `let`
  binding must reflect updates made by the exporting module after a strict-native method calls an imported function.
  The initial failure was deterministic:
  the first invocation still returned
  `40`
  instead of
  `41`,
  which proved bundled named imports were resolving through the module-local snapshot cell instead of the exporter’s live binding.
  Greened that by tightening bundled-module binding discovery in
  `compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java`:
  when a module-top-level binding is just an alias to a bundled
  `__tsj_export_*`
  symbol,
  strict-native class discovery now resolves that alias directly to the export symbol.
  That keeps imported named bindings live without reintroducing per-call import refresh logic into strict-native execution.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetPreservesLiveImportedLetBindingsAcrossModuleCalls test`
  passed.
  Focused cluster verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsImportedConstAndFunctionBindings+strictJvmNativeSubsetKeepsModuleBindingScopesSeparatedAcrossModules+strictJvmNativeSubsetPreservesLiveImportedLetBindingsAcrossModuleCalls test`
  passed (`3` tests, `0` failures, `0` errors).
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest test`
  passed (`234` tests, `0` failures, `0` errors).
  Practical readout:
  strict-native bundled-module imports now preserve live named-binding semantics as well as name isolation.
  Proof slice:
  imported strict-native constructor aliases are already covered.
  Added a focused regression that imports
  `Point as ImportedPoint`
  from another module and constructs it with
  `new ImportedPoint(...)`
  inside a strict-native class.
  The test passed immediately with no compiler changes, which means the current native-lowering path already handles that alias case sufficiently for executable application code.
  Local verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest#strictJvmNativeSubsetSupportsNewExpressionForImportedStrictNativeClassAlias test`
  passed.
  Broader verification:
  `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JvmBytecodeCompilerTest test`
  passed (`235` tests, `0` failures, `0` errors).

### TSJ-89 Generic metadata fidelity on executable classes

- Progress 2026-03-09 slice A:
  - [x] `Red`: added `JvmBytecodeCompilerTest#strictJvmExecutableClassEmitsEnumAndClassLiteralAnnotationAttributes`
    to prove executable classes were not lowering imported `java:` enum/class aliases inside annotation values.
  - [x] `Green`: generic annotation rendering now resolves imported `java:` aliases inside annotation values,
    so executable classes and metadata carriers emit fully qualified enum constants and `.class` literals
    across object-literal attribute bags and arrays.
  - [x] `Proof`: added `JvmBytecodeCompilerTest#strictJvmExecutableClassPreservesRepeatableAnnotations`,
    which passed without compiler changes and proves repeatable runtime annotations already survive on executable classes.
- Progress 2026-03-09 slice B:
  - [x] `Red`: added `JvmBytecodeCompilerTest#strictJvmExecutableClassEmitsNestedAnnotationAttributes`
    to prove executable classes were dropping nested annotation values expressed as TS object literals.
  - [x] `Green`: nested annotation values now lower recursively through a classpath-backed annotation render context,
    using Java annotation member return types to emit nested `@Inner(...)` values and arrays of those values.
  - [x] `Proof`: added `JvmBytecodeCompilerTest#strictJvmExecutableClassExposesBeanPropertyDescriptors`,
    which passed without compiler changes and proves Java bean introspection sees generated getter/setter properties on executable classes.
  - [x] Follow-up closed by later TSJ-89 slices C through K.
- Progress 2026-03-09 slice C:
  - [x] `Red`: added `JvmBytecodeCompilerTest#strictJvmExecutableClassEmitsClasspathNullabilityAnnotations`
    to prove executable strict-native classes do not yet preserve `T` vs `T | null | undefined`
    as framework-readable JVM nullability metadata.
  - [x] `Green`: executable strict-native classes now emit classpath-aware nullability annotations on
    fields, getters/setters, method returns, and parameters for supported strict type shapes,
    selecting the first recognized nullability family actually available on the javac classpath
    (JetBrains, JSR-305, AndroidX, Checker) and avoiding duplicate emission when the source already carries
    an explicit imported nullability decorator.
  - [x] `Proof`: `JvmBytecodeCompilerTest#strictJvmExecutableClassEmitsClasspathNullabilityAnnotations`
    now passes by inspecting the generated executable classfile directly and proving `Nonnull`/`Nullable`
    metadata lands on the executable class rather than a metadata carrier/helper.
  - [x] Follow-up closed by later TSJ-89 slices D through K.
- Progress 2026-03-09 slice D:
  - [x] `Red`: added
    `TsjGenericReflectionConsumerParityTest#supportsGenericDiAndMetadataReflectionConsumersFromExternalJarAgainstStrictExecutableClasses`
    to point the existing external reflection consumer jar at `Controller__TsjStrictNative`
    instead of `ControllerTsjCarrier`.
  - [x] `Green`: no compiler change was required for this slice.
    The strict executable class already satisfies the generic reflection consumer directly,
    so this slice retires a carrier-only certification assumption rather than exposing a backend gap.
  - [x] `Proof`: `TsjGenericReflectionConsumerParityTest` now passes both the legacy carrier scenario
    and the new strict executable-class scenario, proving external consumers can read
    component/field/constructor/method metadata from the executable strict-native class with no carrier lookup step.
- Progress 2026-03-09 slice E:
  - [x] `Red`: updated the existing metadata parity certification harness so it no longer proves only
    carrier/generated-adapter families and now adds strict executable-class family checks driven by `java:` Spring annotation imports.
  - [x] `Green`: the certification harness/report now validates strict executable classes directly,
    while keeping the legacy generated-family coverage green during the transition.
  - [x] `Proof`: `TsjMetadataParityCertificationTest` passes with explicit strict executable-class family evidence
    (`strict-component`, `strict-web-controller`) in the certification report and gate.
- Progress 2026-03-09 slice F:
  - [x] `Red`: extended the older TSJ-39b introspector matrix with a strict executable Spring-web scenario
    that uses explicit `java:` imports; the matrix initially failed with deterministic
    `TSJ39B-INTROSPECTOR-UNKNOWN`, proving the harness still only recognized generated-adapter scenarios.
  - [x] `Green`: the introspector matrix now validates both generated adapter and strict executable Spring-web metadata paths.
  - [x] `Proof`: `TsjIntrospectorCompatibilityMatrixTest` passes with explicit strict executable scenario evidence in the report,
    and `TsjMetadataParityCertificationTest` remains green with the new introspector scenario count.
- Progress 2026-03-09 slice G:
  - [x] `Red`: added a supported Jackson executable DTO scenario to the old TSJ-39b matrix and updated the matrix/certification counts,
    so the legacy gate no longer treats Jackson evidence as only “unsupported guidance”.
  - [x] `Green`: the matrix now validates Jackson serialization/deserialization against a strict executable class,
    using imported Jackson annotations directly on the executable DTO.
  - [x] `Proof`: `TsjIntrospectorCompatibilityMatrixTest` and `TsjMetadataParityCertificationTest`
    pass with explicit supported Jackson executable-class evidence in their reports.
  - [x] Follow-up closed by later TSJ-89 slices H through K.
- Progress 2026-03-09 slice H:
  - [x] `Red`: added a supported Bean Validation executable scenario to the TSJ-39b matrix and certification counts,
    driven by a real validator instead of `TsjValidationSubsetEvaluator`.
  - [x] `Green`: the matrix now validates constraint discovery and violation reporting directly from a strict executable class
    using imported `java:` validation annotations and real Hibernate Validator execution.
  - [x] `Proof`: `TsjIntrospectorCompatibilityMatrixTest` and `TsjMetadataParityCertificationTest`
    pass with explicit supported Bean Validation executable-class evidence in their reports.
  - [x] Follow-up closed by later TSJ-89 slices I through K.
- Progress 2026-03-09 slice I:
  - [x] `Red`: added a supported JPA/Hibernate executable entity scenario to the TSJ-39b matrix and certification counts,
    so entity metadata is no longer absent from executable-class certification.
  - [x] `Green`: the matrix now validates Hibernate metadata bootstrap directly against a strict executable class
    that uses imported `java:` JPA annotations and a Hibernate bootstrap registry wired to the generated classloader.
  - [x] `Proof`: `TsjIntrospectorCompatibilityMatrixTest` and `TsjMetadataParityCertificationTest`
    pass with explicit supported JPA/Hibernate executable-class evidence in their reports.
  - [x] Follow-up closed by later TSJ-89 slices J and K.
- Progress 2026-03-09 slice J:
  - [x] `Red`: added a proxy-facing regression that points real Spring AOP class-based proxying at a strict executable class.
  - [x] `Green`: no compiler change was required for this slice.
    The strict executable class shape already works with `ProxyFactory` class proxies and intercepted method invocation,
    with no TSJ-specific adapter layer.
  - [x] `Proof`: `TsjSpringAopExecutableProxyParityTest` passes and shows the proxy subclasses the strict executable class successfully.
  - [x] Follow-up closed by TSJ-89 slice K and the subsequent TSJ-90 packaging work.
- Progress 2026-03-09 slice K:
  - [x] `Red`: made the metadata certification tests require an executable-class-first family set instead of the previous hybrid generated-plus-executable family list.
  - [x] `Green`: replaced generated component/web/proxy families in `TsjMetadataParityCertificationHarness`
    with strict executable families, including proxy-facing strict-class evidence.
  - [x] `Proof`: `TsjMetadataParityCertificationTest` now passes with an executable-class-first report shape.
- [x] `Red`: added failing tests for arbitrary annotation attribute shapes, generic signatures, parameter metadata, proxyability, and framework reflection reads on executable classes.
- [x] `Green`: support annotation/signature fidelity on executable classes for:
  class/field/constructor/method/parameter annotations,
  enums,
  class literals,
  arrays,
  repeated annotations,
  nested-annotation subset,
  nullability annotations,
  bean-property conventions.
- [x] `Green`: ensure Spring, Hibernate, Jackson, and validation consume emitted classes directly with no TSJ web/DI/body adapters.
- [x] Verify:
  executable-class certification replaces metadata-carrier-only certification.

### TSJ-90 Generic packaging/launch replaces `spring-package`

- Progress 2026-03-10 slice A:
  - [x] `Red`: add CLI tests requiring a generic `package` command to package and run
    both a plain TS app and the existing packaged Spring-web scenario.
  - [x] `Green`: expose `package` as the public command surface and route it through the current packaging pipeline,
    while keeping `spring-package` as a legacy alias during transition.
  - [x] `Proof`: the new generic `package` tests pass, public docs now point at `package`,
    and existing `spring-package` conformance stays green.

- Progress 2026-03-10 slice B:
  - [x] `Red`: add CLI regressions proving `package` emits generic packaging/runtime failure diagnostics
    instead of `TSJ-SPRING-*` codes, while `spring-package` preserves legacy diagnostics.
  - [x] `Green`: make packaging/smoke failure diagnostics command-aware for `package` vs legacy alias.
  - [x] `Proof`: generic package diagnostic tests pass and existing legacy `spring-package` diagnostic tests stay green.

- Progress 2026-03-10 slice C:
  - [x] `Red`: add CLI regressions proving packaged plain apps and packaged web apps both launch through ordinary
    jar manifests, with plain apps using the program main class and packaged web apps using the generated launcher main class.
  - [x] `Green`: make packaged-jar manifest main-class selection command/payload aware so web apps launch through `java -jar`
    without an external launcher invocation contract.
  - [x] `Proof`: plain and packaged-web manifest tests pass and existing packaged-web conformance stays green.

- Progress 2026-03-10 slice D:
  - [x] `Red`: add failing strict-native and CLI packaging tests proving a TS-authored application class can be the packaged
    JVM entrypoint and can pass itself to ordinary Java APIs such as `SpringApplication.run(...)`, with no generated Boot launcher.
  - [x] `Green`: make strict-native application classes usable as real JVM main classes and runtime `Class<?>` values,
    then make `package` prefer that generic entrypoint instead of generating `dev.tsj.generated.boot.TsjSpringBootLauncher`.
  - [x] `Proof`: packaged Boot-style apps launch via `java -jar` through the TS-authored main class, and the packaged jar no longer
    contains `dev/tsj/generated/boot/TsjSpringBootLauncher.class` for that path.

- Progress 2026-03-10 slice E:
  - [x] `Red`: add failing CLI regressions proving generic `package` does not emit generated Spring/web artifacts
    for controller-only and strict-main fixtures, while legacy `spring-package` still preserves the adapter/launcher-backed path.
  - [x] `Green`: reserve generated Boot launcher generation and legacy Spring/web adapter generation for `spring-package` only;
    generic `package` must keep the authored program/strict-native main class contract and clear stale generated artifacts when reusing `--out`.
  - [x] `Proof`: generic `package` jars no longer contain `dev/tsj/generated/boot/TsjSpringBootLauncher.class`
    or generated `dev/tsj/generated/web/*` entries, reused output directories do not leak stale adapters, and legacy `spring-package`
    manifest/adapter tests still pass.

- Progress 2026-03-12 slice F:
  - [x] `Red`: add CLI smoke-run tests proving plain TS apps and TS-authored Boot-style apps both launch through
    the same generic `package --smoke-run` contract.
  - [x] `Green`: keep the package/smoke path framework-agnostic for strict-native Boot main classes, with no generated launcher requirement.
  - [x] `Proof`: generic package smoke-run tests pass for both plain and Boot-style apps, and the Boot-style jar still has no generated launcher.

- Progress 2026-03-12 slice G:
  - [x] `Refactor`: genericize the package pipeline internals so generic packaging no longer uses Spring-specific type and method names.
  - [x] `Proof`: focused CLI packaging/docs suites stay green after renaming `SpringPackage*` internals to generic package terms.

- Progress 2026-03-12 slice H:
  - [x] `Red`: add failing CLI regressions proving `spring-package` does not emit generated Spring/web artifacts
    when the TS app already provides an explicit strict-native `main(args: string[])`.
  - [x] `Green`: make the legacy alias skip legacy adapter/launcher generation for explicit TS-authored strict-native app mains.
  - [x] `Proof`: `spring-package` Boot-style strict-main fixtures package and smoke-run via the TS-authored main class
    with no generated web adapters or generated Boot launcher.

- Progress 2026-03-12 slice I:
  - [x] `Red`: add/update CLI regressions proving the legacy alias now shares the same default packaged jar path
    as the generic `package` command.
  - [x] `Green`: make `spring-package` default to `<out>/tsj-app.jar` while preserving explicit `--boot-jar` override behavior.
  - [x] `Proof`: alias and generic package tests pass with the shared default jar name.

- Progress 2026-03-12 slice J:
  - [x] `Red`: add/update CLI regressions proving the legacy alias now shares the same packaging/smoke diagnostic families
    as the generic `package` command.
  - [x] `Green`: make `spring-package` report `TSJ-PACKAGE*` diagnostics while remaining an accepted legacy command spelling.
  - [x] `Proof`: alias packaging/smoke/failure tests pass with the generic diagnostic families.

- Progress 2026-03-12 slice K:
  - [x] `Red`: update the remaining controller-only and packaged-web alias regressions so `spring-package`
    is expected to package the same authored classes as `package`, with no generated web adapters or generated Boot launcher.
  - [x] `Green`: remove package-time Spring-specific behavior from `spring-package` so it becomes a legacy command spelling
    over the generic compile/package/main-class path.
  - [x] `Proof`: focused CLI packaging/conformance tests pass with shared manifest/main-class behavior,
    no packaged `dev/tsj/generated/web/*` entries, and no packaged `dev/tsj/generated/boot/TsjSpringBootLauncher.class`.

- Progress 2026-03-12 slice L:
  - [x] `Red`: add alias-path regressions proving `spring-package` no longer creates `generated-web/` or `generated-components/`
    source directories during package builds.
  - [x] `Green`: remove the compile-time `spring-package` special case from `compileArtifact(...)` so only explicit
    `--legacy-spring-adapters` requests generate legacy adapter sources.
  - [x] `Proof`: focused CLI alias packaging tests pass with no generated legacy adapter source directories on disk.

- [x] `Red`: add failing CLI tests proving Boot and non-Boot packaged apps should launch through the same packaging contract.
- [x] `Green`: replace `spring-package` with one generic package/run/jar path.
- [x] `Green`: remove generated Spring Boot launcher logic and rely on generic main-class/resource/classpath packaging.
- [x] `Refactor`: unify compile/run/packaged execution around one classpath + resource + manifest model.
- [x] Verify:
  Spring Boot and non-Spring packaged apps both launch through the same TSJ command surface.

### TSJ-91 Remove framework-specific core path

- Progress 2026-03-12 slice A:
  - [x] `Red`: add CLI usage/help regressions proving the public command surface no longer advertises
    `spring-package` or `--legacy-spring-adapters`.
  - [x] `Green`: keep those legacy compatibility hooks accepted, but remove them from the public usage strings and docs.
  - [x] `Proof`: focused CLI usage/docs tests pass with the legacy surfaces hidden from the supported-path contract.

- Progress 2026-03-12 slice B:
  - [x] `Red`: add alias regressions proving hidden `spring-package` invocations are normalized to canonical `package`
    in usage messaging and interop audit records.
  - [x] `Green`: canonicalize legacy alias handling so internal package metadata uses `package` rather than `spring-package`.
  - [x] `Proof`: focused CLI alias tests pass with canonical `package` usage/audit output.

- Progress 2026-03-12 slice C:
  - [x] `Red`: add failing CLI regressions proving retired compatibility hooks are now rejected:
    `spring-package` as a command,
    `--legacy-spring-adapters` for `compile`/`run`,
    and any Spring-adapter assertions rewritten to generic package/compile behavior.
  - [x] `Green`: remove hidden legacy alias/flag parsing plus direct `TsjSpringComponentGenerator` /
    `TsjSpringWebControllerGenerator` plumbing from `TsjCli`, and drop Spring-specific success/artifact counters
    from the supported CLI contract.
  - [x] `Refactor`: rewrite affected CLI/docs/baseline tests around generic native packaging/compile behavior,
    not generated Spring adapter output.
  - [x] `Proof`: focused CLI/package/docs/baseline suites pass with retired hooks rejected and no direct Spring-generator
    references left in `TsjCli`.
    - `mvn -B -ntp -pl cli -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjSpringPackagedWebConformanceTest,TsjGenericPackageCommandTest,TsjAnyJarNoHacksBaselineTest,TsjDocsDriftGuardTest,TsjCliTest#publicMissingCommandUsageDoesNotAdvertiseLegacySpringPackageAlias+compileAndRunUsageDoNotAdvertiseLegacySpringAdapterFlag+retiredLegacySpringCompatibilityHooksAreRejected+compileSupportsDecoratedClassesWithoutLegacyAdapterMetadata+runSupportsDecoratedClassesWithoutLegacyAdapterMetadata+packageBuildsRunnableJarAndIncludesResourceFiles+packageSupportsCustomJarPathAndExplicitResourceDirectory+packageMergesSpringFactoriesFromDependencyJars+packageRejectsMissingExplicitResourceDirectory+packageMarksCompileFailuresWithCompileStage+packageMarksInteropBridgeFailuresWithBridgeStage+packageMarksSmokeRunFailuresWithRuntimeStage+packageSmokeRunVerifiesEmbeddedEndpointAvailability+packageMarksEndpointSmokeFailuresSeparatelyFromStartupFailures+packageRejectsSmokeEndpointOptionsWithoutSmokeRun test`
      passed with `37` tests and `0` failures.

- Progress 2026-03-12 slice D:
  - [x] `Red`: add a deterministic CI guard test that fails if `TsjCli` reintroduces retired framework-specific
    core-path references such as `TsjSpringComponentGenerator`, `TsjSpringWebControllerGenerator`,
    `spring-package`, or `--legacy-spring-adapters`.
  - [x] `Green`: keep retired compatibility surfaces out of the supported CLI/core path and document only
    the generic package/run contract.
  - [x] `Proof`: CLI/docs drift guard suite proves the source guard stays green.

- Progress 2026-03-12 slice E:
  - [x] `Red`: add backend guard tests that fail while any retired generator-path code is still present:
    `TsjSpringComponentGenerator`,
    `TsjSpringWebControllerGenerator`,
    their artifact records,
    generator-backed test suites,
    the generated-only `spring-web-mapping-introspection` fixture,
    or helper entrypoints such as `__tsjInvokeClassWithInjection`,
    `__tsjInvokeController`,
    and `__tsjCoerceControllerRequestBody`.
  - [x] `Green`: rewrite the remaining live backend harnesses (`TSJ-35b`, `TSJ-33f`, `TSJ-37`, `TSJ-39b`)
    onto executable strict-native classes so they no longer depend on generated Spring adapters.
  - [x] `Green`: delete the retired generator classes/artifacts, generator-only integration tests,
    and generated-only metadata parity/introspector fixtures.
  - [x] `Proof`: focused backend/CLI guard suites pass with no remaining supported-path references to the retired generator flow.
    - `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjRetiredSpringGeneratorGuardTest,TsjGenericReflectionConsumerParityTest,TsjSpringIntegrationMatrixTest,TsjSpringModuleCertificationTest test`
      passed with `13` tests and `0` failures.

- Progress 2026-03-12 slice F:
  - [x] `Red`: audit framework-specific helper classes still compiled into backend main and prove which ones are test-only.
  - [x] `Green`: move test-only framework subset analyzers/parsers out of `compiler/backend-jvm/src/main/java`
    into test scope so the supported backend artifact no longer ships them.
  - [x] `Proof`: focused backend guard/certification suites still pass after the move, with the production backend source set smaller.
    - `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsAnnotationAttributeParserTest,TsjValidationSubsetEvaluatorTest,TsjDataJdbcSubsetEvaluatorTest,TsjRetiredSpringGeneratorGuardTest,TsjGenericReflectionConsumerParityTest,TsjSpringIntegrationMatrixTest,TsjSpringModuleCertificationTest test`
      passed with `25` tests and `0` failures.

- Progress 2026-03-12 slice G:
  - [x] `Red`: extend the retirement guard to fail while the legacy regex-backed `TsDecoratorModelExtractor`
    remains in backend main even though only tests/harnesses use it.
  - [x] `Green`: move `TsDecoratorModelExtractor` out of `compiler/backend-jvm/src/main/java`
    into test scope and keep the production backend on `TsFrontendDecoratorModelExtractor`.
  - [x] `Proof`: extractor/evaluator/matrix suites still pass after the move, with the production backend source set smaller again.
    - `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsDecoratorModelExtractorTest,TsDecoratorClasspathResolutionTest,TsAnnotationAttributeParserTest,TsjValidationSubsetEvaluatorTest,TsjDataJdbcSubsetEvaluatorTest,TsjRetiredSpringGeneratorGuardTest,TsjGenericReflectionConsumerParityTest,TsjSpringIntegrationMatrixTest,TsjSpringModuleCertificationTest test`
      passed with `39` tests and `0` failures.

- Progress 2026-03-12 slice H:
  - [x] `Red`: extend the retirement guard to fail while the hardcoded Spring/Jakarta `TsDecoratorAnnotationMapping`
    remains in backend main even though the production extractor only needs an allowlist of explicit bare names.
  - [x] `Green`: remove `TsDecoratorAnnotationMapping` from backend main, change `TsFrontendDecoratorModelExtractor`
    to accept plain supported-bare-decorator sets, and keep the legacy hardcoded mapping only in test scope.
  - [x] `Proof`: frontend extractor tests, legacy decorator-wrapper tests, and backend guard/matrix suites still pass after the move.
    - `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsDecoratorAnnotationMappingTest,TsDecoratorModelExtractorTest,TsDecoratorClasspathResolutionTest,TsFrontendDecoratorModelExtractorTest,TsAnnotationAttributeParserTest,TsjValidationSubsetEvaluatorTest,TsjDataJdbcSubsetEvaluatorTest,TsjRetiredSpringGeneratorGuardTest,TsjGenericReflectionConsumerParityTest,TsjSpringIntegrationMatrixTest,TsjSpringModuleCertificationTest test`
      passed with `44` tests and `0` failures.

- Progress 2026-03-13 slice I:
  - [x] `Red`: replace Spring-bridge success tests with deterministic rejection/retirement tests for
    `springConfiguration`, `springBeanTargets`, `springWebController`, `springWebBasePath`,
    `springRequestMappings.*`, and `springErrorMappings` in interop specs.
  - [x] `Green`: remove Spring-specific bridge generation, diagnostics, metadata, and emitted annotations from
    `InteropBridgeGenerator`, keeping only the generic interop bridge contract.
  - [x] `Refactor`: update CLI/docs/guard suites so the supported interop contract no longer documents or references
    Spring-specific interop bridge keys.
  - [x] `Proof`: focused interop/backend/CLI/docs guard suites pass with no Spring-specific branches left in
    `InteropBridgeGenerator`.
    - `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InteropBridgeGeneratorTest,TsjRetiredSpringGeneratorGuardTest,TsjIntrospectorCompatibilityMatrixTest,TsjMetadataParityCertificationTest test`
      passed with `22` tests and `0` failures.
    - `mvn -B -ntp -pl cli -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#interopCommandRejectsRetiredSpringBeanInteropKeys+interopCommandRejectsRetiredSpringWebInteropKeys,TsjDocsDriftGuardTest test`
      passed with `6` tests and `0` failures.

- Progress 2026-03-13 slice J:
  - [x] `Red`: add CLI/package regression and source-guard coverage proving packaged metadata merging works for
    `.factories` / `.imports` resources while `TsjCli` no longer hardcodes Spring-specific merge rule names.
  - [x] `Green`: replace explicit `spring.factories` / `META-INF/spring/*.imports` merge branches with generic
    property-file and line-set merge strategies keyed by resource shape.
  - [x] `Refactor`: keep package behavior and diagnostics unchanged while removing Spring-only naming from the
    supported packaging path.
  - [x] `Proof`: focused CLI/package/docs guard suites pass with the generic merge strategy.
    - `mvn -B -ntp -pl cli -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjCliTest#packageMergesImportsMetadataFromDependencyJars+packageMergesSpringFactoriesFromDependencyJars,TsjDocsDriftGuardTest#tsjCliSourceUsesGenericMetadataMergeStrategyNames test`
      passed with `3` tests and `0` failures.

- Progress 2026-03-13 slice K:
  - [x] `Red`: add docs/harness drift guards proving the supported contract no longer describes generated Spring adapters,
    `spring-package`, or generated Boot launchers as part of the current path.
  - [x] `Green`: rewrite stale docs and readiness/introspector fixture descriptions around executable strict-native classes,
    generic `package`, and imported `java:` annotations.
  - [x] `Refactor`: keep historical review material in `docs/todo.md` / `docs/stories.md`, but remove obsolete wording
    from current user-facing guides and certification readmes.
  - [x] `Proof`: focused docs/readiness guard suites pass after the wording cleanup.
    - `mvn -B -ntp -pl cli -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjDocsDriftGuardTest test`
      passed with `6` tests and `0` failures.
    - `mvn -B -ntp -pl compiler/backend-jvm -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjKotlinParityReadinessGateTest,TsjKotlinParityCertificationTest test`
      passed with `8` tests and `0` failures.

- [x] `Refactor`: rewrite any remaining differential/certification harnesses around generic native behavior instead of Spring-adapter assertions.
- [x] Verify:
  CI guard prevents reintroduction of framework-specific logic into the supported native path.
  - `TsjRetiredSpringGeneratorGuardTest` and `TsjDocsDriftGuardTest` now cover retired generator/helper symbols plus
    stale adapter-era wording in active parity/certification harnesses and current guides.

### TSJ-92 Final no-hacks any-jar certification and docs closure

- Progress 2026-03-13 slice A:
  - [x] `Red`: add a final any-jar certification gate/report test that requires one deterministic artifact to aggregate:
    generic package/no-hacks baseline,
    Spring packaged-web proof,
    Spring AOP proxyability,
    Hibernate/JPA executable introspection,
    Jackson executable DTO introspection,
    Bean Validation executable DTO introspection,
    and a non-Spring reflection-consumer proof.
  - [x] `Green`: implement the certification harness/report by composing existing focused harnesses instead of inventing
    a new framework-specific path.
  - [x] `Constraint`: keep TSJ production behavior generic; the certification lane may use Spring/Hibernate/Jackson as
    consumers, but must not add Spring-specific compiler/CLI logic or test-only workarounds that mask a general gap.
  - [x] `Proof`: targeted certification gate passes and emits a deterministic report artifact suitable for CI upload.
  - Result:
    `cli/src/test/java/dev/tsj/cli/TsjAnyJarNoHacksCertificationTest.java`
    now emits
    `cli/target/tsj92-anyjar-nohacks-certification.json`
    and the final green slice required two generic fixes only:
    runtime JavaBean/record property access for plain JVM objects, and annotation-aware auto-interop discovery so
    decorator-only `java:` imports do not generate bogus runtime bridge targets.

- [x] `Red`: define final release gate over real TS-only apps using:
  Spring Boot DI/web,
  Spring AOP/transactions,
  Hibernate/JPA + H2,
  Jackson,
  Bean Validation,
  and one non-Spring reflection-heavy jar.
- [x] `Green`: make all certification apps pass using only `java:` imports, ordinary jars/resources, and the generic TSJ command surface.
- [x] `Green`: finalize the user-facing contract for the supported any-jar mode and deprecate obsolete legacy guidance.
- [x] `Docs`: update canonical docs/examples so they describe only the supported generic path and any explicit legacy compatibility surface.
- [x] Verify:
  certification gate is green locally/CI and publishes deterministic artifacts suitable for release signoff.

### Planned Execution Order

- [x] TSJ-85
- [x] TSJ-86
- [x] TSJ-87
- [x] TSJ-88
- [x] TSJ-89
- [x] TSJ-90
- [x] TSJ-91
- [x] TSJ-92

## 2026-03-13 Follow-up Execution: Full Regression + TSJ-88 Resume

- [x] Record the immediate execution order before more code changes.
- [x] Run full repository regression on the current tree and fix any failures first.
- [x] If regression is green, resume `TSJ-88` from the next remaining strict-native subset gap with fresh red/green proof.

  Final 2026-03-13 state:
  the full reactor regression
  `mvn -B -ntp test`
  passed with
  `compiler-backend-jvm`
  at
  `418`
  tests and
  `cli`
  at
  `331`
  tests, both with
  `0`
  failures and
  `0`
  errors.
  The last fixes before that green run were test-harness isolation for the no-hacks certification lanes
  (disable backend incremental parse cache inside the in-process harness command runners)
  and a packaged-web conformance harness update so annotation attributes are read reflectively across both stub
  and real Spring signatures.
