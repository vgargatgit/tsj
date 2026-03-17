# Lessons

## 2026-02-22
- Pattern: I started by inspecting TSJ implementation details for a task that was explicitly scoped to building a standalone TypeScript example app.
- Rule: For spec implementation under `examples/*`, default to app-local implementation and only touch TSJ internals if the user explicitly asks for integration.
- Guardrail: Before exploration, state dependency intent: `standalone app` vs `TSJ integration`; if standalone, restrict reads/edits to that example folder and shared docs.
- Pattern: I proposed creating a separate TSJ-compatible suite when the user asked for full TSJ success on existing TGTA `src/ok` files.
- Rule: When user goal is explicit (`TSJ-COMPILE-SUCCESS for all files`), do gap analysis against that exact target first; do not redirect scope unless asked.
- Guardrail: For validation requests, always report current pass/fail against the exact requested file set before suggesting alternatives.
- Pattern: I left an out-of-scope JSX/TSX parser gap item in the actionable TODO list after scope was narrowed.
- Rule: When scope exclusion is explicit (for example, `jsx/tsx is not in scope`), remove excluded work items immediately from active TODOs and plans.
- Guardrail: Before starting implementation, re-check active TODO bullets against the latest scope constraints from the user.
- Pattern: I attempted file edits via `exec_command` using `apply_patch` text instead of using the dedicated `apply_patch` tool.
- Rule: For source/doc edits, use `apply_patch` directly and reserve `exec_command` for shell execution only.
- Guardrail: Before any patch edit, choose the tool explicitly: `apply_patch` for text diffs, `exec_command` for running commands/tests.
- Pattern: I closed TGTA TODO slices without running the high-signal backend regression class, so bridge normalization drift surfaced later as 11 `JvmBytecodeCompilerTest` regressions.
- Rule: For parser/bridge/backend grammar work, always run `JvmBytecodeCompilerTest` (or the impacted full suite) before claiming completion.
- Guardrail: Completion checklist for grammar changes must include both targeted failing tests and one broad regression run (`JvmBytecodeCompilerTest` + bridge conformance snapshots).
- Pattern: I used documentation summaries as primary evidence for grammar/operator support status instead of validating directly from parser/runtime code and tests.
- Rule: For capability claims (supported/unsupported grammar/operators), derive status from implementation and executable tests first; treat docs as secondary.
- Guardrail: Before reporting feature support, capture at least one code reference plus one test/reference run (or explicit absence of tests) for each claimed gap.
- Pattern: I scoped unsupported-grammar fixtures under `examples/tnta` instead of using the repository-wide `unsupported/` root requested by the user.
- Rule: When a shared root path is specified (for example `unsupported/`), place fixtures/tests there unless explicitly asked to use an example-local folder.
- Guardrail: Before creating new fixture directories, verify whether a requested root folder already exists and target it first.

## 2026-03-02
- Pattern: I began execution for a major architecture request before first formalizing the story breakdown.
- Rule: For major cross-module overhauls, create/update `docs/stories.md` and `docs/plans.md` before implementation starts.
- Guardrail: Before first code change on a major feature, ensure story IDs, acceptance criteria, and red/green checkpoints are documented and linked.
- Pattern: I allowed a replan that still implied framework-specific framing (`Spring-like`) when the user required out-of-the-box any-jar behavior.
- Rule: When user goal is framework-agnostic any-jar support, remove framework-specific special casing from acceptance criteria and implementation plan.
- Guardrail: For each story in the track, include an explicit "no framework hardcoding in default path" check.

## 2026-03-06
- Pattern: I used `mvn ... -rf :cli` for CLI validation after backend/frontend changes, which can exercise stale local snapshot jars and create false failures.
- Rule: For cross-module validation, run CLI tests through reactor (`-pl cli -am`) so backend/frontend/runtime sources are rebuilt in the same invocation.
- Guardrail: Use `-rf` only for same-module retries; for dependency-sensitive suites, always prefer reactor module selection.
- Pattern: I tried to optimize with `-pl cli` (without `-am`) while upstream modules were dirty, and readiness results diverged because CLI linked against stale installed artifacts.
- Rule: Only use `-pl cli` without `-am` when upstream dependencies are unchanged and local snapshots are known current; otherwise keep `-am`.
- Guardrail: If a targeted `-pl cli` run disagrees with reactor results, treat it as stale-artifact risk and re-run with `-pl cli -am` before changing code/tests.
- Pattern: I ran broad regressions for small deltas instead of narrowing to impacted tests first, causing slow iteration loops.
- Rule: For inner-loop debugging, run targeted tests with `-Dtest=...` and skip non-critical lint gates (`-Dcheckstyle.skip=true`), then run broader gates once green.
- Guardrail: Before running Maven, choose one of two modes explicitly: `targeted-fast` (`-pl ... -am -Dcheckstyle.skip=true -Dtest=...`) or `full-regression`.
- Pattern: I started a Spring-style example flow without explicitly pinning decorator sources to `java:` imports after the user clarified no inbuilt TSJ Spring annotations.
- Rule: For Spring annotation examples meant to validate jar interop, always import decorators from `java:org.springframework...` modules and avoid relying on TSJ built-in/global decorator mappings.
- Guardrail: Before writing controllers/services, add and verify explicit `java:` annotation imports in each TS file that uses decorators.
- Pattern: I left `run.sh` behavior ambiguous in the Pet Clinic example, so users expected a live HTTP server from a verification-only script.
- Rule: For example scripts, separate verification and long-running server modes with explicit script names and README guidance.
- Guardrail: Any script that does not start a listener must state that clearly and point to the server-start command.

## 2026-03-07
- Pattern: I introduced Java fixture implementation code for pet-clinic after the user required a TS-only implementation compiled by TSJ.
- Rule: When user says implementation ownership must stay in TS, do not add custom Java fixture application logic (controllers/repositories/entities/bootstraps); only use dependency jars and TSJ-generated outputs.
- Guardrail: Before adding any new Java source under `examples/*/fixtures-src`, validate that the user explicitly asked for Java fixture code; if not explicit, block and keep logic in TS.

## 2026-03-08
- Pattern: I kept pushing feature work while the user wanted the dirty worktree problem addressed first.
- Rule: When the user redirects to worktree hygiene, pause feature execution and fix generated-output tracking/noise before resuming implementation.
- Guardrail: Before the next code feature change, re-run `git status --short` and separate generated dirt from authored changes so cleanup work does not trample in-flight source edits.

## 2026-03-12
- Pattern: I preserved retired compatibility code and tests after the supported path had already moved to the generic executable-class flow.
- Rule: Once a legacy path is retired from the supported contract, prefer deleting its orphaned code/tests over hiding it behind compatibility scaffolding.
- Guardrail: For cleanup stories, trace every remaining reference; if a path is only exercised by legacy-only tests or docs, rewrite the last live harnesses first and then remove the entire cluster in one pass.

## 2026-03-13
- Pattern: I started steering a failing any-jar certification lane toward a Spring-specific harness workaround before restating the framework-agnostic constraint.
- Rule: When the user requires no framework-specific hacks, treat framework tests only as consumers of the generic TSJ path; do not add Spring-only behavior to TSJ or hide general bytecode gaps behind Spring-only test wiring.
- Guardrail: Before fixing a framework integration failure, write down whether the proposed change generalizes to arbitrary jars; if not, keep it out of production code and redesign the proof/fix.
- Pattern: I let overlapping long-running Maven/test sessions accumulate until they consumed the available exec slots and obscured the real regression state.
- Rule: Before starting another long regression or module rerun, check whether an equivalent run is already active; reuse it if valid or terminate the older duplicate before launching a replacement.
- Guardrail: Keep at most one active long-running regression per module/goal. If a rerun is needed, stop the stale process tree first and then restart cleanly.
- Pattern: While upgrading `examples/pet-clinic`, I began shaping the example aggressively toward green before first restating that any failure must expose a generic TSJ/Spring/JPA/OpenAPI gap rather than be papered over locally.
- Rule: For example upgrades that are intended to validate any-jar support, do not introduce example-only bootstrapping tricks or test-only masking; either use ordinary framework APIs or record the remaining generic blocker explicitly.
- Guardrail: Before each fix in an any-jar example, ask: `would this same change be needed for arbitrary framework consumers, or am I just making the sample pass?` If it is sample-only, stop and document the gap instead.
- Pattern: I initially focused on pet-clinic startup symptoms before isolating the generic package publication behavior across filesystems.
- Rule: When an example fails on the generic path, reduce it first to the smallest product-level invariant (here: `package` must publish a structurally valid jar everywhere) before reasoning about the framework stack above it.
- Guardrail: For packaged-app failures, validate the jar artifact itself with `JarFile`/`jar tf` before touching any framework integration code or example scripts.
