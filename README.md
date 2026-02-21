# TSJ: TypeScript to JVM Compiler

TSJ is a staged compiler project that lowers TypeScript into JVM-oriented IR and, eventually, JVM bytecode.

Current implementation status includes:
1. Architecture decisions and runtime contracts (`TSJ-0`)
2. Multi-module Maven monorepo with CI, lint, and tests (`TSJ-1`)
3. CLI bootstrap commands (`TSJ-2`)
4. Fixture-based differential harness (Node vs TSJ path) (`TSJ-3`)
5. TypeScript parser/type-check integration via compiler API bridge (`TSJ-4`)
6. Initial HIR/MIR/JIR pipeline + JSON dump tool (`TSJ-5`)
7. MIR CFG + lexical capture metadata for nested scopes/closures (`TSJ-6`)
8. JVM backend subset compile/run path for arithmetic, control flow, and function calls (`TSJ-7`)
9. JVM closure lowering for nested functions and captured variables (`TSJ-8`)
10. JVM class/object lowering for constructors, inheritance basics, and object literals (`TSJ-9`)
11. Runtime coercion/equality semantics for `==` vs `===` and `undefined` value handling (`TSJ-10`)
12. Dynamic object runtime upgrades with prototype links, delete semantics, shape tokens, and monomorphic property read caches (`TSJ-11`)
13. Multi-file module bootstrap via relative ESM import bundling, deterministic dependency initialization order, and live-binding-safe fixture coverage (`TSJ-12`)
14. Promise runtime + async/await lowering with deterministic microtask sequencing and rejection propagation (`TSJ-13`)
15. Async/promise error semantics with Promise `catch` + `finally` behavior and unhandled rejection emission (`TSJ-13d`)
16. Promise combinators (`all`, `race`, `allSettled`, `any`) with array-literal inputs and differential fixtures (`TSJ-13e`)
17. Top-level await lowering for entry/module initialization order with async diagnostics for unsupported await placements (`TSJ-13f`)
18. Runtime stack-trace source mapping with `--ts-stacktrace` CLI rendering in TypeScript coordinates (`TSJ-14`)
19. Unsupported feature policy gates for MVP non-goals (`dynamic import`, `eval`, `Function` constructor, `Proxy`) with feature-ID diagnostics (`TSJ-15`)
20. Opt-in interop bridge generation with allowlist enforcement and runtime codec helpers (`TSJ-19`)
21. Differential conformance suite execution with minimized repro output and feature coverage report generation (`TSJ-16`)
22. Baseline optimizer pass with constant folding + dead-code elimination, benchmarked reduction on fixture-like workloads, and CLI optimization toggles (`TSJ-17`)
23. Performance benchmark harness with micro/macro workloads, SLA draft, and CI baseline artifact tracking (`TSJ-18`)
24. Object-to-primitive abstract equality coercion parity for `==`/`!=`, including `valueOf`/`toString` conversion paths and differential fixture coverage (`TSJ-20`)
25. Object runtime syntax parity for `delete`, `__proto__` assignment, and `Object.setPrototypeOf(...)` lowering with differential fixture coverage (`TSJ-21`)
26. TypeScript Java interop binding syntax (`java:` named imports), frontend binding metadata/validation, and backend lowering to runtime callable handles (`TSJ-26`)
27. External JAR/classpath inputs for `compile` and `run` with artifact metadata persistence and runtime classloader integration (`TSJ-27`)
28. Compile-pipeline auto interop bridge generation via `--interop-spec` with deterministic cache/reuse behavior (`TSJ-28`)
29. Expanded interop invocation model for constructors, instance members, field get/set, overload ranking, varargs, and detailed mismatch diagnostics (`TSJ-29`)
30. Interop codec expansion for arrays/lists/maps/enums/functional interfaces and Promise-`CompletableFuture` async boundary adapters (`TSJ-30`)
31. Interop policy modes with default strict allowlist enforcement and opt-in broad classpath mode (`TSJ-31`)
32. JVM metadata subset for generated artifacts: runtime-visible bridge annotations and emitted method parameter names (`TSJ-32`, subset)
33. Spring DI bridge subset for interop specs: generated `@Configuration` + typed `@Bean` bridge methods with constructor-injection coverage (`TSJ-33`, subset)
34. Spring web subset:
   interop-spec bridge generation plus TS-authored decorator adapters for `@RestController` routes, request-parameter binding, and mapped exception handlers (`TSJ-34`, subset)
35. Spring AOP/proxy subset for TS-authored beans:
   `@Transactional` mapping, interface-based proxy compatibility path, and transactional integration diagnostics (`TSJ-35`, subset)
36. Spring packaging/startup:
   `spring-package` CLI command for jar packaging, resource inclusion (`application.properties`/`application.yml` + static files), and smoke startup diagnostics (`TSJ-36`, complete)
   plus endpoint smoke verification options (`--smoke-endpoint-url`, `--smoke-timeout-ms`, `--smoke-poll-ms`)
   and dev-loop parity gate (`TSJ-36c`, complete)
37. Spring ecosystem integration matrix:
   web parity checks, actuator baseline parity (`health`/`info`/`metrics` read operations),
   validation runtime parity subset (`@Validated` + representative constraints),
   security baseline subset (`@PreAuthorize` hasRole/hasAnyRole with 401/403/200 outcomes),
   and data-jdbc baseline subset (repository query-method naming + transactional wiring checks,
   with out-of-scope `@Query` decorator diagnostics) (`TSJ-37`, complete; TSJ-37e complete)
38. Kotlin-parity readiness:
   TS/Kotlin reference-app scaffolds plus readiness-gate report with explicit full-parity blockers
   (`TSJ-38`, complete; TSJ-38a/TSJ-38b/TSJ-38c complete)
39. Interop metadata subset:
   parameterized generic signature emission for typed bridge paths plus explicit metadata-gap diagnostics (`TSJ-39`, subset)
40. Classpath mediation:
   deterministic jar version-conflict diagnostics plus Maven-metadata mediation-graph subset
   (`nearest` + `root-order`), command-path scope filtering (`compile` vs `run`/`spring-package`),
   and multi-jar dependency-graph runtime coverage with closure-gate certification
   (`TSJ-40`, complete)
41. Advanced interop invocation/conversion:
   improved overload ranking, nested conversion graph support (`Optional`/`Set`), richer mismatch diagnostics,
   and certification gating (`TSJ-41`, complete)
42. Hibernate/JPA compatibility:
   ORM-oriented integration coverage and explicit unsupported-pattern diagnostics (`TSJ-42`, complete),
   plus TSJ-42a DB-backed parity/report gating (`TSJ-42a`, complete),
   TSJ-42b lazy/proxy parity + unsupported-pattern diagnostics (`TSJ-42b`, complete),
   TSJ-42c lifecycle/transaction parity + diagnostic-family separation (`TSJ-42c`, complete),
   and TSJ-42d closure certification/report gating (`TSJ-42d`, complete)
43. Broad-mode guardrails:
   explicit risk acknowledgement plus denylist/audit-log/trace controls, with fleet policy-source precedence
   and conflict diagnostics plus certification gating (`TSJ-43`, complete)
44. Any-JAR certification:
   category-spanning compatibility checks plus real third-party library matrix coverage, readiness thresholds,
   version-range drift tracking, real-app workload gates, and governance signoff artifacts
   (`TSJ-44`, complete; TSJ-44a/TSJ-44b/TSJ-44c/TSJ-44d complete)

## Repository Layout

- `compiler/frontend`: TypeScript parser/type-check bridge and frontend results
- `compiler/ir`: HIR/MIR/JIR models, lowering pipeline, IR JSON dump tool
- `compiler/backend-jvm`: JVM backend module scaffold
- `runtime`: runtime scaffold
- `cli`: CLI commands (`compile`, `run`, `spring-package`, `fixtures`)
- `tests/fixtures`: committed fixture inputs/expectations
- `tests/spring-matrix`: TSJ-37 Spring module matrix fixtures
- `examples/pet-store-api`: TypeScript-authored Spring-style Pet Store API sample
- `docs`: architecture decisions, contracts, stories, and format docs

## Prerequisites

1. Java 21+
2. Maven 3.8+
3. Node.js 20+ (validated with Node 24)
4. TypeScript compiler API available:
   - Recommended: `npm i -D typescript`
   - Global `tsc` is also supported by the frontend bridge fallback.

## Build and Test

Run full reactor checks:

```bash
mvn -B -ntp test
```

Install artifacts:

```bash
mvn -B -ntp clean install
```

Run only selected modules:

```bash
mvn -B -ntp -pl compiler/frontend -am test
mvn -B -ntp -pl compiler/ir -am test
mvn -B -ntp -pl cli -am test
```

## CLI Usage (Bootstrap)

Compile TypeScript into TSJ artifact metadata:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build"
```

Disable TSJ-17 optimizer passes for debugging/regression checks:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build --no-optimize"
```

Run TSJ bootstrap execution path:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build"
```

Package a TS-authored app as a Spring-style runnable jar (TSJ-36 subset):

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="spring-package path/to/main.ts --out build --smoke-run --smoke-endpoint-url http://127.0.0.1:8080/actuator/health"
```

Run the packaged artifact directly:

```bash
java -jar build/tsj-spring-app.jar
```

Run with external interop jars:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --jar path/to/lib.jar"
```

Run with explicit classpath list:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --classpath path/to/lib.jar:<path2-on-unix-or-use-; on windows>"
```

Run with optimizer disabled:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --no-optimize"
```

Run with TypeScript stack-trace rendering on runtime failure:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --ts-stacktrace"
```

Run fixture harness:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="fixtures tests/fixtures"
```

Generate performance benchmark baseline report:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="bench benchmarks/tsj-benchmark-baseline.json --warmup 1 --iterations 2"
```

Run a fast smoke benchmark profile:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="bench benchmarks/tsj-benchmark-smoke.json --smoke --warmup 0 --iterations 1"
```

Generate Java interop bridges from allowlisted targets:

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="interop path/to/interop.properties --out build/interop"
```

CLI command contract is in `docs/cli-contract.md`.

TSJ-26 source-level interop binding syntax (current subset):

```ts
import { max, min as minimum } from "java:java.lang.Math";
console.log(max(3, 7));
console.log(minimum(3, 7));
```

Current TSJ-26 limits:
- Only named imports are supported for `java:` modules.
- Module specifier must be `java:<fully.qualified.ClassName>`.
- Imported bindings map to runtime interop handles.
  - Plain names map to static method calls, e.g. `max`.
  - TSJ-29 binding forms:
    - `$new`
    - `$instance$<method>`
    - `$static$get$<field>`, `$static$set$<field>`
    - `$instance$get$<field>`, `$instance$set$<field>`

TSJ-27 interop classpath options:
- `--jar <jar-file>` can be repeated.
- `--classpath <entries>` accepts platform path separators (`:` on Unix, `;` on Windows).
- Compile artifacts persist interop entries as:
  `interopClasspath.count` and `interopClasspath.<index>`.
- `--interop-spec <interop.properties>` integrates bridge generation into `tsj compile`/`tsj run`.
- `--interop-policy strict|broad` controls interop authorization:
  - `strict` (default): requires allowlist spec for `java:` bindings.
  - `broad` (opt-in): permits unrestricted classpath interop.
  - `broad` requires explicit `--ack-interop-risk`.
- `--interop-role <roles>` supplies actor roles for RBAC-gated broad interop paths.
- `--interop-approval <token>` supplies approval token for approval-gated interop targets.
- `--interop-denylist <patterns>` blocks matching class/package targets.
- `--interop-audit-log <path>` appends JSON-line allow/deny decisions.
- `--interop-trace` enables runtime interop trace logging.

TSJ-7 compile now emits generated classes under `<out>/classes`, and `tsj run` executes the generated JVM class before emitting run diagnostics.
TSJ-8 extends the same path with lexical closure support, mutable captured locals, and dynamic `this` binding for
function/object-method invocation patterns in the supported subset.
TSJ-9 extends the same path with class constructors/methods, inheritance via `extends`/`super(...)`, and object literal property access/assignment.
TSJ-10 extends runtime semantics with `undefined`, abstract equality (`==`/`!=`) coercion, and strict equality (`===`/`!==`) separation.
TSJ-11 extends object runtime behavior with prototype mutation validation, `delete` runtime primitives, shape-token invalidation, and generated monomorphic property read cache fields.
TSJ-12 extends compile/run with bootstrap multi-file module loading for relative imports, deterministic dependency-first initialization, and baseline live-binding behavior for supported patterns.
TSJ-13 adds `async function` + `await` lowering over `TsjPromise`, throw-to-rejection normalization, and async sequencing tests that validate post-sync microtask ordering.
TSJ-13a extends async lowering with control-flow continuations for `if`/`while` blocks containing multiple standalone await suspension points.
TSJ-13b extends async language coverage with function expressions, arrow functions, async class/object methods, await normalization across supported expression positions, and targeted diagnostics for unsupported async generator/getter/setter variants.
TSJ-13c extends promise runtime semantics with thenable assimilation, self-resolution protection, first-settle-wins handling, and chained thenable adoption.
TSJ-13d extends async/promise error semantics with Promise `catch` + `finally` support and runtime unhandled rejection emission.
TSJ-13e adds Promise combinators (`all`, `race`, `allSettled`, `any`) and minimal array literal lowering for combinator inputs.
TSJ-13f adds top-level await lowering for entry + module initialization ordering (including transitive imports) and explicit diagnostics for unsupported await-in-while-condition placement.
TSJ-14 adds generated-class source maps and optional `--ts-stacktrace` TypeScript frame rendering for runtime failures.
TSJ-15 adds explicit non-goal feature gates with structured diagnostics (`featureId`, guidance, and source coordinates) and a documented matrix at `docs/unsupported-feature-matrix.md`.
TSJ-19 adds opt-in interop bridge generation from allowlisted Java targets and runtime `TsjInteropCodec` conversions for Java boundary calls.
TSJ-16 adds explicit differential suite execution outputs with minimized failure repro details and feature coverage report generation (`tests/fixtures/tsj-fixture-coverage.json`).
TSJ-17 adds baseline compiler optimizations (constant folding + dead-code elimination), CLI toggles (`--optimize`, `--no-optimize`), and benchmark assertions that verify generated output reduction.
TSJ-18 adds a benchmark harness (`tsj bench`) with micro/macro suites, a baseline JSON report tracked in CI artifacts, and initial SLA targets in `docs/performance-sla.md`.
TSJ-20 adds object-to-primitive abstract-equality coercion parity (`valueOf`/`toString`) for supported runtime objects and class instances.
TSJ-21 adds language-level lowering for `delete`, `obj.__proto__ = ...`, and `Object.setPrototypeOf(...)` to close TSJ-11 syntax/runtime integration gaps.
TSJ-26 adds source-level Java interop bindings via named `java:` imports, frontend interop-binding metadata/diagnostics,
and backend lowering to callable runtime handles.
TSJ-27 adds explicit `--jar` / `--classpath` inputs for compile/run, persists interop classpath metadata in artifacts,
and executes generated programs with external classpath entries available to Java interop reflection.
TSJ-28 integrates bridge generation into compile/run via `--interop-spec`, auto-discovers required `java:` targets,
and skips regeneration when inputs are unchanged.
TSJ-29 expands interop invocation to constructors, instance methods, static/instance field access, deterministic
overload resolution, and varargs conversion support.
TSJ-30 expands interop conversion/callback boundaries with container + enum codecs, functional-interface bridging
for TS callables, and Promise/`CompletableFuture` adaptation on Java async boundaries.
TSJ-31 adds explicit interop policy modes (`strict` default, `broad` opt-in), strict-mode diagnostics, and
documented security/operational guardrails for any-jar workflows.
TSJ-32 (subset) adds runtime-visible annotation emission for generated interop bridges and `-parameters` metadata
for generated Java classes, with reflection tests and explicit annotation diagnostics.
TSJ-33 adds TS-authored Spring DI/component baseline support:
stereotype lowering, `@Configuration`/`@Bean` adapter generation, constructor+field+setter injection diagnostics,
and lifecycle ordering/cycle diagnostics in Spring-compatible integration harnesses.
TSJ-33f adds differential DI/lifecycle parity gating against Java/Kotlin references with report artifact
`compiler/backend-jvm/target/tsj33f-di-lifecycle-parity-report.json`.
TSJ-34 adds Spring web support across two paths:
interop-spec bridge generation (`springWebController`, `springWebBasePath`, `springRequestMappings.*`,
`springErrorMappings`) and TS-authored decorator adapter generation
(`@RestController`, `@RequestMapping`, route mappings, `@ExceptionHandler`, `@ResponseStatus`).
Generated adapters live under `<out>/generated-web`, delegate into compiled TS program classes, and are covered by
web-dispatch integration tests; `spring-package` now compiles generated TS web/component adapters into packaged output.
TSJ-34e adds booted HTTP converter/error-envelope parity checks for supported subset and emits
`compiler/backend-jvm/target/tsj34e-web-converter-report.json`.
TSJ-34f adds packaged runtime conformance certification with artifact:
`cli/target/tsj34f-packaged-web-conformance-report.json`.
TSJ-35 adds transactional decorator mapping, interface/class-proxy compatibility for TS-authored Spring components,
booted runtime conformance checks, and TS-vs-Java/Kotlin differential parity gating via
`compiler/backend-jvm/target/tsj35c-aop-differential-parity-report.json`.
TSJ-36 adds `spring-package` fat-jar packaging with resource-copy support, stage-aware startup diagnostics,
and endpoint smoke verification (`--smoke-endpoint-url`, `--smoke-timeout-ms`, `--smoke-poll-ms`).
TSJ-36c adds dev-loop parity certification artifact:
`cli/target/tsj36c-dev-loop-parity.json`.
TSJ-37 adds an executed Spring ecosystem matrix report (`compiler/backend-jvm/target/tsj37-spring-module-matrix.json`)
with explicit supported/unsupported module entries backed by reproducible fixtures in `tests/spring-matrix`;
validation now has a supported subset path (`@Validated` with representative constraints),
security now has a supported baseline subset path (`@PreAuthorize` hasRole/hasAnyRole),
and data-jdbc now has a supported baseline subset path (repository query-method naming + transactional wiring checks)
while out-of-scope `@Query` decorator paths remain explicitly gated.
TSJ-37e adds closure-gate certification artifact:
`compiler/backend-jvm/target/tsj37e-spring-module-certification.json`.
TSJ-38 adds Kotlin-parity reference scaffolds in `examples/tsj38-kotlin-parity` and a readiness gate report
(`compiler/backend-jvm/target/tsj38-kotlin-parity-readiness.json`) that tracks subset readiness versus full-parity blockers.
TSJ-38a adds DB-backed reference parity certification with report artifact:
`compiler/backend-jvm/target/tsj38a-db-parity-report.json`.
TSJ-38b adds security-enabled reference parity certification with report artifact:
`compiler/backend-jvm/target/tsj38b-security-parity-report.json`.
TSJ-38c adds full parity certification with dimension thresholds and report artifact:
`compiler/backend-jvm/target/tsj38c-kotlin-parity-certification.json`.
TSJ-39 adds generated-class metadata parity coverage across program classes, bridge classes,
Spring component/web adapters, and proxy artifacts, with certification gating via
`compiler/backend-jvm/target/tsj39c-metadata-parity-certification.json`.
TSJ-40 adds deterministic classpath conflict diagnostics (`TSJ-CLASSPATH-CONFLICT`) for conflicting jar versions,
and TSJ-40a adds Maven-metadata mediation graph behavior with deterministic precedence
(`nearest`, then `root-order`) plus persisted mediation decisions in compile artifacts.
TSJ-40b adds scope-aware resolution metadata (`interopClasspath.scope.*`) and explicit
`TSJ-CLASSPATH-SCOPE` diagnostics when interop targets are available only through excluded scopes.
TSJ-40c adds classloader isolation mode controls (`--classloader-isolation shared|app-isolated`) with
deterministic `TSJ-RUN-009` isolation-conflict and `TSJ-RUN-010` boundary diagnostics.
TSJ-40d adds certification-gate coverage for mediation/scope/isolation with report artifact:
`cli/target/tsj40d-dependency-mediation-certification.json`.
TSJ-41 strengthens broad interop invocation/conversion behavior with improved overload scoring, nested graph conversion,
and concrete conversion-failure diagnostics in runtime/CLI tests.
TSJ-41a adds deterministic numeric widening selection across primitive/wrapper overload candidates,
plus explicit numeric-conversion narrowing diagnostics when no compatible overload exists.
TSJ-41b adds reflective `Type`-aware generic adaptation for nested collections/maps/optionals/futures
with explicit target-type conversion-failure context.
TSJ-41c adds reflective edge-case parity for default interface methods and bridge-aware dispatch,
with targeted `TSJ-INTEROP-REFLECTIVE` diagnostics for non-public member access.
TSJ-41d adds invocation/conversion parity certification gating with report artifact:
`cli/target/tsj41d-invocation-conversion-certification.json`.
Certified scope/non-goals for TSJ-41d are documented in:
`docs/interop-invocation-certification.md`.
TSJ-42 adds ORM-oriented compatibility coverage and unsupported-pattern diagnostics for Hibernate/JPA-like flows.
TSJ-42a adds DB-backed parity certification with report artifact:
`cli/target/tsj42a-jpa-realdb-parity.json`.
TSJ-42b adds lazy/proxy parity certification with report artifact:
`cli/target/tsj42b-jpa-lazy-proxy-parity.json`.
TSJ-42c adds persistence-context lifecycle and transaction parity certification with report artifact:
`cli/target/tsj42c-jpa-lifecycle-parity.json`.
TSJ-42d adds closure certification artifact:
`cli/target/tsj42d-jpa-certification.json`.
TSJ-43 adds broad-mode policy guardrails:
explicit risk acknowledgement, denylist hooks, audit logging, and runtime interop trace toggles.
TSJ-43a adds deterministic policy-source precedence (global -> project -> command) with
conflict diagnostics (`TSJ-INTEROP-POLICY-CONFLICT`).
TSJ-43b adds centralized audit aggregation (`--interop-audit-aggregate`) with
stable `tsj.interop.audit.v1` schema events, bounded emission, and local fallback diagnostics.
TSJ-43c adds RBAC and approval-path enforcement in broad mode with
`--interop-role`, `--interop-approval`, and explicit authorization diagnostics
(`TSJ-INTEROP-RBAC`, `TSJ-INTEROP-APPROVAL`).
TSJ-43d adds operational guardrail certification gating with report artifact:
`cli/target/tsj43d-guardrail-certification.json`.
TSJ-44 adds category-spanning any-jar certification checks and emits
`cli/target/tsj44-anyjar-certification-report.json` with readiness-threshold summary.
TSJ-44a adds real third-party library matrix coverage and emits
`cli/target/tsj44a-real-library-matrix-report.json`.
TSJ-44b adds certified version-range drift tracking and emits
`cli/target/tsj44b-version-range-certification.json`.
TSJ-44c adds real-application workload certification and emits
`cli/target/tsj44c-real-app-certification.json`.
TSJ-44d adds governance signoff/manifest certification and emits
`cli/target/tsj44d-anyjar-governance.json`.

## Frontend and IR Tools

Frontend parser/type-check service contract:
- `docs/frontend-contract.md`

IR contract:
- `docs/ir-contract.md`

Dump IR JSON for a TS project:

```bash
mvn -B -ntp -pl compiler/ir -am exec:java \
  -Dexec.mainClass=dev.tsj.compiler.ir.IrDumpTool \
  -Dexec.args="path/to/tsconfig.json --out build/ir.json"
```

## Fixtures

Fixture format is documented in `docs/fixture-format.md`.

Seed fixture:
- `tests/fixtures/smoke-hello`
- `tests/fixtures/tsj7-control-flow`
- `tests/fixtures/tsj8-closure-counter`
- `tests/fixtures/tsj8-this-binding`
- `tests/fixtures/tsj9-class-inheritance`
- `tests/fixtures/tsj9-object-literal`
- `tests/fixtures/tsj10-coercion`
- `tests/fixtures/tsj11-missing-property`
- `tests/fixtures/tsj12-modules`
- `tests/fixtures/tsj13-promise-then`
- `tests/fixtures/tsj13-async-await`
- `tests/fixtures/tsj13-async-reject`
- `tests/fixtures/tsj13a-async-if`
- `tests/fixtures/tsj13a-async-while`
- `tests/fixtures/tsj13b-async-arrow`
- `tests/fixtures/tsj13b-async-object-method`
- `tests/fixtures/tsj13b-async-generator-unsupported`
- `tests/fixtures/tsj13c-thenable`
- `tests/fixtures/tsj13c-thenable-reject`
- `tests/fixtures/tsj13d-catch-finally`
- `tests/fixtures/tsj13d-finally-reject`
- `tests/fixtures/tsj13e-all-race`
- `tests/fixtures/tsj13e-allsettled-any`
- `tests/fixtures/tsj13e-any-reject`
- `tests/fixtures/tsj13f-top-level-await`
- `tests/fixtures/tsj13f-top-level-await-modules`
- `tests/fixtures/tsj13f-top-level-await-transitive`
- `tests/fixtures/tsj13f-top-level-await-while-unsupported`
- `tests/fixtures/tsj26-real-app-orders`
- `tests/fixtures/tsj26-interop-binding-positive`
- `tests/fixtures/tsj26-interop-binding-invalid`
- `tests/fixtures/tsj20-abstract-equality`
- `tests/fixtures/tsj21-object-runtime-syntax`

## Project Planning Docs

- Backlog and story sequencing: `docs/stories.md`
- Architecture decisions: `docs/architecture-decisions.md`
- Story-to-architecture mapping: `docs/story-architecture-map.md`
- Runtime contracts: `docs/contracts/runtime-contracts-v0.1.md`
- Source-map format: `docs/source-map-format.md`
- Unsupported feature matrix: `docs/unsupported-feature-matrix.md`
- Interop bridge spec: `docs/interop-bridge-spec.md`
- Interop generic adaptation guarantees: `docs/interop-generic-adaptation.md`
- Interop reflective compatibility boundaries: `docs/interop-reflective-compatibility.md`
- Classpath mediation/isolation behavior: `docs/classpath-mediation.md`
- Interop policy modes: `docs/interop-policy.md`
- Interop audit aggregation subset: `docs/interop-audit-aggregation.md`
- Dev-loop workflow parity (TSJ-36c): `docs/dev-loop-workflow.md`
- Spring DI/lifecycle differential parity gate (TSJ-33f): `docs/spring-di-lifecycle-parity.md`
- Spring transactional/AOP differential parity gate (TSJ-35c): `docs/spring-aop-differential-parity.md`
- Metadata parity certification gate (TSJ-39c): `docs/metadata-parity-certification.md`
- Any-JAR certification (subset + real-library matrix + version ranges): `docs/anyjar-certification.md`
- JPA real-db parity gate (TSJ-42a): `docs/hibernate-jpa-realdb-parity.md`
- JPA lazy/proxy parity gate (TSJ-42b): `docs/hibernate-jpa-lazy-proxy-parity.md`
- JPA lifecycle/transaction parity gate (TSJ-42c): `docs/hibernate-jpa-lifecycle-parity.md`
- JPA closure certification gate (TSJ-42d): `docs/hibernate-jpa-certification.md`
- Kotlin DB parity gate (TSJ-38a): `docs/tsj38a-db-parity.md`
- Kotlin security parity gate (TSJ-38b): `docs/tsj38b-security-parity.md`
- Kotlin full parity certification gate (TSJ-38c): `docs/tsj38c-parity-certification.md`
- Any-JAR real-app certification gate (TSJ-44c): `docs/anyjar-realapp-certification.md`
- Any-JAR governance/signoff gate (TSJ-44d): `docs/anyjar-governance-signoff.md`
- Annotation mapping subset: `docs/annotation-mapping.md`
- Performance SLA draft: `docs/performance-sla.md`

## Development Approach

This repository follows comprehensive TDD flow (red -> green -> refactor), as documented in `AGENTS.md`.
