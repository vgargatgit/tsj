# Plans

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
