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

- [ ] `Red`: add certification fixtures for:
  - a TS-only Spring Boot + Hibernate/JPA app;
  - a TS-only Spring AOP/web/DI app;
  - a non-Spring reflection-heavy jar consumer.
- [ ] `Red`: make fixtures fail if execution depends on:
  metadata carriers,
  `spring-package`,
  `--legacy-spring-adapters`,
  generated boot-launcher code,
  or TSJ framework glue helper entrypoints.
- [x] `Green`: publish a deterministic blocker report mapping each failure to concrete core-path code.
- [x] Verify:
  targeted certification suite is red for the right reasons and stores an artifact in CI/local target output.
- Progress 2026-03-08:
  added `tests/conformance/anyjar-nohacks/{generic_package_probe,non_spring_reflection_consumer,spring_web_jpa_app}`
  plus `cli/src/test/java/dev/tsj/cli/TsjAnyJarNoHacksBaseline{Report,Harness,Test}.java`.
  Local verification:
  `mvn -B -ntp -pl cli -am -Dcheckstyle.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TsjAnyJarNoHacksBaselineTest test`
  now generates `cli/target/tsj85-anyjar-nohacks-baseline.json`.
  Current proven blockers are:
  missing generic `package`,
  dependency on `spring-package`,
  generated Spring/web adapters,
  generated Boot launcher,
  and runtime annotations landing on metadata carriers instead of executable classes.
  Remaining TSJ-85 work:
  add an explicit Spring AOP probe and an explicit guard for `--legacy-spring-adapters` / helper-entrypoint dependence.

### TSJ-86 Frontend-backed declaration model replaces regex/mapping shortcuts

- [ ] `Red`: add failing frontend/backend tests that require typed declaration extraction for complex class/decorator/generic syntax.
- [ ] `Green`: replace regex-driven framework metadata extraction with a typed frontend-backed JVM declaration model.
- [ ] `Green`: remove hard dependency on `TsDecoratorModelExtractor` and `TsDecoratorAnnotationMapping` from the native any-jar path.
- [ ] `Refactor`: centralize imported-annotation/classpath resolution so all framework jars flow through the same symbol model.
- [ ] Verify:
  frontend snapshots + backend declaration tests cover multiline/generic/nested class shapes and deterministic spans.

### TSJ-87 Executable JVM classes replace metadata carriers

- [ ] `Red`: add failing tests proving supported native-mode classes must be executable reflection targets without carrier companions.
- [ ] `Green`: emit concrete JVM classes for supported TS classes so reflection, instantiation, and invocation all target the same class.
- [ ] `Green`: preserve fields/getters/setters/constructors/parameter names/generic signatures on emitted classes.
- [ ] `Refactor`: remove metadata-carrier reliance from supported native-mode classes and confine any fallback to explicit unsupported diagnostics.
- [ ] Verify:
  reflection/invocation tests show no carrier class is needed for supported native-mode workloads.

### TSJ-88 Expand native lowering to framework-complete application subset

- [ ] `Red`: add failing multi-class application tests covering real repository/service/controller/entity workloads.
- [ ] `Green`: extend native lowering to cover framework-application class/member/body constructs:
  inheritance/super,
  field initializers,
  static members,
  object/array literals,
  closures,
  control flow,
  exceptions,
  service/repository/controller call chains,
  and boundary collection/nullability shapes.
- [ ] `Green`: keep deterministic unsupported diagnostics for shapes still outside the native path.
- [ ] Verify:
  pet-clinic-class workloads compile as native JVM classes with no adapter generation.

### TSJ-89 Generic metadata fidelity on executable classes

- [ ] `Red`: add failing tests for arbitrary annotation attribute shapes, generic signatures, parameter metadata, proxyability, and framework reflection reads on executable classes.
- [ ] `Green`: support annotation/signature fidelity on executable classes for:
  class/field/constructor/method/parameter annotations,
  enums,
  class literals,
  arrays,
  repeated annotations,
  nested-annotation subset,
  nullability annotations,
  bean-property conventions.
- [ ] `Green`: ensure Spring, Hibernate, Jackson, and validation consume emitted classes directly with no TSJ web/DI/body adapters.
- [ ] Verify:
  executable-class certification replaces metadata-carrier-only certification.

### TSJ-90 Generic packaging/launch replaces `spring-package`

- [ ] `Red`: add failing CLI tests proving Boot and non-Boot packaged apps should launch through the same packaging contract.
- [ ] `Green`: replace `spring-package` with one generic package/run/jar path.
- [ ] `Green`: remove generated Spring Boot launcher logic and rely on generic main-class/resource/classpath packaging.
- [ ] `Refactor`: unify compile/run/packaged execution around one classpath + resource + manifest model.
- [ ] Verify:
  Spring Boot and non-Spring packaged apps both launch through the same TSJ command surface.

### TSJ-91 Remove framework-specific core path

- [ ] `Red`: add guard tests that fail if framework-specific generators/flags/helpers are still required by the supported any-jar path.
- [ ] `Green`: remove or quarantine from retired compatibility areas:
  `TsjSpringComponentGenerator`,
  `TsjSpringWebControllerGenerator`,
  Spring module subset evaluators,
  generated boot-launcher code,
  `spring-package`,
  `--legacy-spring-adapters`,
  and custom Spring DI/web/request-body core helpers.
- [ ] `Refactor`: rewrite affected tests around generic native behavior instead of Spring-adapter assertions.
- [ ] Verify:
  CI guard prevents reintroduction of framework-specific logic into the supported native path.

### TSJ-92 Final no-hacks any-jar certification and docs closure

- [ ] `Red`: define final release gate over real TS-only apps using:
  Spring Boot DI/web,
  Spring AOP/transactions,
  Hibernate/JPA + H2,
  Jackson,
  Bean Validation,
  and one non-Spring reflection-heavy jar.
- [ ] `Green`: make all certification apps pass using only `java:` imports, ordinary jars/resources, and the generic TSJ command surface.
- [ ] `Green`: finalize the user-facing contract for the supported any-jar mode and deprecate obsolete legacy guidance.
- [ ] `Docs`: update canonical docs/examples so they describe only the supported generic path and any explicit legacy compatibility surface.
- [ ] Verify:
  certification gate is green locally/CI and publishes deterministic artifacts suitable for release signoff.

### Planned Execution Order

- [ ] TSJ-85
- [ ] TSJ-86
- [ ] TSJ-87
- [ ] TSJ-88
- [ ] TSJ-89
- [ ] TSJ-90
- [ ] TSJ-91
- [ ] TSJ-92
