# TSJ CLI Contract (Bootstrap v0.1)

## Commands

### `tsj compile <input.ts> --out <dir> [--classpath <entries>] [--jar <jar-file>] [--interop-spec <interop.properties>] [--interop-policy strict|broad] [--ack-interop-risk] [--interop-role <roles>] [--interop-approval <token>] [--interop-denylist <patterns>] [--interop-audit-log <path>] [--interop-audit-aggregate <path>] [--interop-trace] [--optimize|--no-optimize]`
Behavior:
1. Validates input file exists and has `.ts`/`.tsx` extension.
2. Compiles supported TSJ-7 subset into JVM classes.
   - TSJ-8 extends supported subset with nested function declarations and lexical closures.
   - TSJ-9 extends supported subset with class/object features:
     class declarations, `extends`/`super(...)`, `new`, `this` member access, and object literals.
   - TSJ-10 extends runtime semantics for supported primitives with:
     `undefined` literal handling and distinct lowering for `==`/`!=` vs `===`/`!==`.
   - TSJ-11 extends generated object-property reads with monomorphic call-site caches and
     runtime object fallback behavior (`missing -> undefined`) aligned to subset semantics.
   - TSJ-12 extends bootstrap compile with relative ESM import bundling (`import { ... } from "./x.ts"` and
     `import "./x.ts"`), dependency-first module initialization ordering, and baseline live-binding behavior
     for supported export/import declaration forms.
   - TSJ-13 extends runtime/codegen with `Promise` builtin support (`resolve`, `reject`), `async function`
     declaration lowering, standalone `await` expression lowering in async bodies, throw-to-rejection
     normalization, and microtask queue flushing at end-of-program execution.
   - TSJ-17 applies baseline optimization passes by default:
     constant folding and dead-code elimination.
     - `--optimize` forces defaults on.
     - `--no-optimize` disables both passes.
3. Creates output directory if missing.
4. Emits class output directory:
   - `<out>/classes`
5. Emits artifact file:
   - `<out>/program.tsj.properties`
   - includes optimization metadata keys:
     `optimization.constantFoldingEnabled` and `optimization.deadCodeEliminationEnabled`.
   - includes interop classpath metadata keys:
     `interopClasspath.count` and `interopClasspath.<index>`.
   - includes interop bridge metadata keys:
     `interopBridges.enabled`, `interopBridges.regenerated`,
     `interopBridges.targetCount`, `interopBridges.generatedSourceCount`.
   - includes TS-authored web adapter metadata keys:
     `tsjWebControllers.controllerCount`, `tsjWebControllers.generatedSourceCount`.
6. Emits source-map file for generated class stack frame mapping:
   - `<out>/classes/dev/tsj/generated/*Program.tsj.map`
7. Emits structured JSON diagnostics to stdout/stderr.
8. When `--interop-spec` is present:
   - auto-discovers `java:` bindings from entry + relative imports,
   - writes effective auto spec and generated sources under `<out>/generated-interop`,
   - compiles generated bridge sources in the same compile invocation.
9. Interop policy mode:
   - default `strict`: `java:` bindings require `--interop-spec` allowlist input.
   - opt-in `broad`: allows classpath interop without allowlist enforcement.
   - `broad` requires explicit `--ack-interop-risk`.
   - TSJ-43a policy-source precedence when flags are omitted:
     default -> global (`TSJ_INTEROP_GLOBAL_POLICY` / `tsj.interop.globalPolicy`) ->
     project (`.tsj/interop-policy.properties`) -> explicit command flags.
   - conflicting global/project sources without explicit `--interop-policy` fail with
     `TSJ-INTEROP-POLICY-CONFLICT`.
10. Broad-mode guardrails:
   - `--interop-denylist <patterns>` blocks discovered interop targets matching configured package/class patterns.
   - `--interop-role <roles>` supplies actor roles for RBAC-scoped broad-mode authorization.
   - `--interop-approval <token>` supplies approval token for approval-gated interop targets.
   - `--interop-audit-log <path>` appends JSON-line allow/deny decisions for discovered targets.
   - `--interop-audit-aggregate <path>` emits centralized schema-stable JSON-line events
     (`schema=tsj.interop.audit.v1`) for policy/target/outcome reporting.
   - aggregate sink configuration requires local fallback log (`--interop-audit-log`).
   - `--interop-trace` records runtime interop invocation traces when executing generated code.
11. Classpath mediation subset:
   - classpath entries preserve user-specified order for artifact metadata persistence.
   - conflicting jar versions for the same detected artifact stem (for example,
     `foo-1.0.jar` and `foo-2.0.jar`) fail fast with deterministic diagnostics.
   - when jars provide Maven metadata (`META-INF/maven/**/pom.properties` + `pom.xml`),
     TSJ applies deterministic transitive mediation subset rules:
     `nearest` dependency depth, then `root-order`, then deterministic discovery order.
   - mediation decisions are persisted in compile artifact metadata under
     `interopClasspath.mediation.*`.
   - scope-aware resolution subset is applied by command path:
     `compile` allows `compile,runtime,provided`;
     `run` and `spring-package` allow `compile,runtime`.
   - scope filtering metadata is persisted under `interopClasspath.scope.*`.
   - interop targets available only via excluded scopes fail with `TSJ-CLASSPATH-SCOPE`.
12. TSJ-34 decorator path:
   - scans TS source/module graph for supported decorators:
     `@RestController`, `@RequestMapping`,
     `@GetMapping|@PostMapping|@PutMapping|@DeleteMapping|@PatchMapping`,
     `@ExceptionHandler`, `@ResponseStatus`.
   - auto-generates Spring adapter source files under:
     `<out>/generated-web/dev/tsj/generated/web/*.java`.
   - generated adapter methods delegate to compiled TS program via `__tsjInvokeController(...)`.

Success diagnostic:
- Code: `TSJ-COMPILE-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-003` missing required `--out`
- `TSJ-CLI-011` invalid classpath/jar input
- `TSJ-CLASSPATH-CONFLICT` conflicting jar versions for one artifact stem
- `TSJ-CLASSPATH-SCOPE` interop target requires dependency scope excluded for current command path
- `TSJ-CLI-012` missing/invalid interop spec input
- `TSJ-CLI-013` invalid interop policy value
- `TSJ-INTEROP-POLICY-CONFLICT` conflicting fleet policy sources without explicit command override
- `TSJ-INTEROP-POLICY` strict mode violation (interop bindings used without allowlist spec)
- `TSJ-INTEROP-RISK` broad mode used without explicit risk acknowledgement
- `TSJ-INTEROP-RBAC` broad mode authorization failed due missing required role scope
- `TSJ-INTEROP-APPROVAL` broad mode authorization failed due missing/invalid approval token
- `TSJ-INTEROP-DENYLIST` interop target blocked by denylist policy
- `TSJ-INTEROP-AUDIT` local interop audit log write failure
- `TSJ-COMPILE-001` input file not found
- `TSJ-COMPILE-002` unsupported input extension
- `TSJ-COMPILE-500` artifact write error
- `TSJ-WEB-CONTROLLER` TS-authored web decorator parse/generation failure
- backend diagnostics like `TSJ-BACKEND-*` for unsupported syntax or JVM compile failures
  - TSJ-15 unsupported-feature failures use `TSJ-BACKEND-UNSUPPORTED` with context:
    `file`, `line`, `column`, `featureId`, `guidance`.

### `tsj run <entry.ts> [--out <dir>] [--classpath <entries>] [--jar <jar-file>] [--interop-spec <interop.properties>] [--interop-policy strict|broad] [--ack-interop-risk] [--interop-role <roles>] [--interop-approval <token>] [--interop-denylist <patterns>] [--interop-audit-log <path>] [--interop-audit-aggregate <path>] [--interop-trace] [--classloader-isolation shared|app-isolated] [--ts-stacktrace] [--optimize|--no-optimize]`
Behavior:
1. Compiles entry to artifact (default out dir `.tsj-build` when omitted).
   - Optimization defaults to enabled (`--optimize`) and can be disabled with `--no-optimize`.
   - Interop classpath can be provided explicitly through `--classpath` and/or repeated `--jar`.
   - `--interop-spec <interop.properties>` enables integrated auto-bridge generation during compile phase.
   - Interop policy defaults to `strict`; use `--interop-policy broad` for unrestricted classpath interop.
   - Broad mode requires `--ack-interop-risk`.
   - When command policy flags are omitted, fleet policy-source precedence applies:
     default -> global -> project -> command.
   - Optional broad-mode guardrails:
     `--interop-role`, `--interop-approval`, `--interop-denylist`,
     `--interop-audit-log`, `--interop-audit-aggregate`, `--interop-trace`.
2. Reads generated artifact.
3. Executes generated JVM class with artifact output classes plus configured interop classpath entries.
   - Program argv passthrough is currently not supported; TSJ invokes generated `main` with empty args (`String[0]`).
   - Classloader isolation subset:
     `--classloader-isolation shared|app-isolated` (default `shared`).
   - App-isolated conflict/boundary diagnostics:
     `TSJ-RUN-009`, `TSJ-RUN-010`.
   - TSJ-41a numeric overload subset applies deterministic widening selection across
     primitive/wrapper numeric candidates:
     widening distance first, then stable primitive-vs-wrapper tie-break.
   - Numeric narrowing candidates are rejected with explicit numeric-conversion diagnostics
     in no-match error messages.
   - TSJ-41b generic adaptation subset applies reflective `Type`-aware conversion for nested
     `List`/`Set`/`Map`/`Optional`/array/`CompletableFuture` signatures with target-type context
     in conversion failures (`Generic interop conversion failed ...`).
   - TSJ-41c reflective subset supports default-interface method dispatch and bridge-aware candidate
     selection; non-public reflective member access fails with `TSJ-INTEROP-REFLECTIVE` context.
4. When `--ts-stacktrace` is present and runtime execution fails, emits best-effort mapped TS stack frames to stderr.
   - Output is grouped by cause (`Cause[0]`, `Cause[1]`, ...).
   - Frames are filtered to generated-program methods and deduplicated at method level per cause.
5. Emits program stdout, then structured JSON diagnostics.

Success diagnostic:
- Code: `TSJ-RUN-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-004` missing entry path
- `TSJ-CLI-011` invalid classpath/jar input
- `TSJ-CLASSPATH-CONFLICT` conflicting jar versions for one artifact stem
- `TSJ-CLI-013` invalid interop policy value
- `TSJ-INTEROP-POLICY-CONFLICT` conflicting fleet policy sources without explicit command override
- `TSJ-INTEROP-POLICY` strict mode violation (interop bindings used without allowlist spec)
- `TSJ-INTEROP-RISK` broad mode used without explicit risk acknowledgement
- `TSJ-INTEROP-RBAC` broad mode authorization failed due missing required role scope
- `TSJ-INTEROP-APPROVAL` broad mode authorization failed due missing/invalid approval token
- `TSJ-INTEROP-DENYLIST` interop target blocked by denylist policy
- `TSJ-INTEROP-AUDIT` local interop audit log write failure
- `TSJ-RUN-001` artifact read error
- `TSJ-RUN-007` missing class metadata in artifact
- `TSJ-RUN-008` configured classpath entry not found at execution time
- `TSJ-INTEROP-REFLECTIVE` runtime reflective subset violation
- `TSJ-RUN-*` runtime class load/execute failures
- compile-phase failure codes from `tsj compile`

### `tsj spring-package <entry.ts> --out <dir> [--classpath <entries>] [--jar <jar-file>] [--interop-spec <interop.properties>] [--interop-policy strict|broad] [--ack-interop-risk] [--interop-role <roles>] [--interop-approval <token>] [--interop-denylist <patterns>] [--interop-audit-log <path>] [--interop-audit-aggregate <path>] [--interop-trace] [--resource-dir <dir>] [--boot-jar <jar-file>] [--smoke-run] [--smoke-endpoint-url <http(s)-url|stdout://marker>] [--smoke-timeout-ms <ms>] [--smoke-poll-ms <ms>] [--optimize|--no-optimize]`
Behavior:
1. Compiles entry to TSJ artifact using the same compile path as `tsj compile`.
2. Compiles generated TS-authored Spring adapter sources (web/component) into packaged classes output when present.
3. Packages generated classes into a runnable jar:
   - default jar path: `<out>/tsj-spring-app.jar`
   - override with `--boot-jar <jar-file>`.
   - jar layout is fat/self-contained for TSJ runtime + configured interop dependencies.
4. Copies resource files into jar from supported directories:
   - auto-discovery under entry parent:
     - `<entry-parent>/src/main/resources`
     - `<entry-parent>/resources`
   - plus explicit repeated `--resource-dir <dir>`.
5. Persists compile artifact metadata into jar:
   - `META-INF/tsj/program.tsj.properties`.
6. Optional smoke run (`--smoke-run`):
   - launches packaged app via `java -jar <jar>`.
   - emits startup diagnostic for runtime failures.
   - optional endpoint verification:
     - `--smoke-endpoint-url` supports:
       - `http(s)://...` endpoint probes
       - `stdout://<marker>` deterministic marker probes for constrained CI/test environments
     - `--smoke-timeout-ms` controls total wait budget (default `5000`)
     - `--smoke-poll-ms` controls probe polling interval (default `150`)
   - emits endpoint smoke diagnostics with runtime context and repro command.
7. Emits structured JSON diagnostics to stdout/stderr.
8. Interop guardrails and policy behavior match `tsj compile`/`tsj run`:
   - broad mode requires `--ack-interop-risk`,
   - optional role/approval/denylist/audit-log/audit-aggregate/trace controls apply,
   - fleet policy-source precedence and conflict diagnostics apply when command flags are omitted.

Success diagnostics:
- `TSJ-SPRING-PACKAGE-SUCCESS`
- `TSJ-SPRING-SMOKE-ENDPOINT-SUCCESS` (when endpoint smoke probe is configured and passes)
- `TSJ-SPRING-SMOKE-SUCCESS` (when `--smoke-run` is enabled)

Failure diagnostics:
- compile-stage failures from `tsj compile` (annotated with `context.stage=compile`)
- `TSJ-SPRING-PACKAGE` generated-adapter compile failures
  (`context.stage=compile`, `context.failureKind=generated-adapter-compile`)
- interop/bridge-stage failures from integrated bridge generation (annotated with `context.stage=bridge`)
- `TSJ-SPRING-PACKAGE` packaging failures (`context.stage=package`,
  `context.failureKind=manifest|repackage|resource`)
- `TSJ-SPRING-BOOT` smoke startup failures (`context.stage=runtime`)
- `TSJ-SPRING-ENDPOINT` smoke endpoint probe failures (`context.stage=runtime`,
  `context.failureKind=endpoint`)
- `TSJ-CLI-014` invalid `spring-package` usage
- `TSJ-CLI-017` invalid endpoint smoke option usage/values
- `TSJ-INTEROP-RISK` broad mode used without explicit risk acknowledgement
- `TSJ-INTEROP-POLICY-CONFLICT` conflicting fleet policy sources without explicit command override
- `TSJ-INTEROP-RBAC` broad mode authorization failed due missing required role scope
- `TSJ-INTEROP-APPROVAL` broad mode authorization failed due missing/invalid approval token
- `TSJ-INTEROP-DENYLIST` interop target blocked by denylist policy

### `tsj interop <interop.properties> --out <dir>`
Behavior:
1. Reads opt-in interop bridge spec (`allowlist` and optional `targets`).
2. Validates each requested target is allowlisted.
3. Validates target classes/bindings exist for supported interop binding forms.
   - static method binding: `<class>#<method>`
   - constructor binding: `<class>#$new`
   - instance method binding: `<class>#$instance$<method>`
   - field bindings:
     `<class>#$static$get$<field>`, `<class>#$static$set$<field>`,
     `<class>#$instance$get$<field>`, `<class>#$instance$set$<field>`
4. Generates bridge source stubs under:
   - `<out>/dev/tsj/generated/interop/*.java`
   - optional class/method annotation emission via:
     `classAnnotations` and `bindingAnnotations.<binding>` spec keys.
   - TSJ-33 subset Spring bridge options:
     `springConfiguration=true` and
     `springBeanTargets=<class>#<binding>,...`
     for typed `@Configuration` + `@Bean` emission on supported targets.
   - TSJ-34 subset Spring web bridge options:
     `springWebController=true`,
     `springWebBasePath=/base`,
     `springRequestMappings.<binding>=<HTTP_METHOD> <path>`,
     `springErrorMappings=<exceptionFqcn>:<statusCode>,...`.
5. Emits bridge metadata:
   - `<out>/interop-bridges.properties`
6. Emits structured JSON diagnostics.

Success diagnostic:
- `TSJ-INTEROP-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-008` missing interop spec path
- `TSJ-INTEROP-INPUT` spec read/write failures
- `TSJ-INTEROP-INVALID` malformed target or missing class/method
- `TSJ-INTEROP-DISALLOWED` requested target not in allowlist
- `TSJ-INTEROP-ANNOTATION` invalid annotation type/configuration
- `TSJ-INTEROP-SPRING` invalid Spring bridge configuration/target compatibility
- `TSJ-INTEROP-WEB` invalid Spring web bridge configuration/target compatibility

### `tsj fixtures <fixturesRoot>`
Behavior:
1. Loads all fixture directories under `<fixturesRoot>`.
2. Runs each fixture with Node and TSJ.
3. Compares outputs with fixture expectations.
4. Emits per-fixture diagnostics plus a summary.
5. Emits feature-coverage diagnostics and writes a JSON coverage report file.

Success diagnostics:
- `TSJ-FIXTURE-PASS`
- `TSJ-FIXTURE-SUMMARY`
- `TSJ-FIXTURE-COVERAGE`

Failure diagnostics:
- `TSJ-FIXTURE-FAIL`
- `TSJ-FIXTURE-001` fixture load error
- `TSJ-FIXTURE-002` no fixture directories

Failure diagnostic context for `TSJ-FIXTURE-FAIL` includes:
1. `minimalRepro`: compact mismatch summary with repro commands for Node and TSJ.

### `tsj bench <report.json> [--warmup <n>] [--iterations <n>] [--smoke] [--optimize|--no-optimize]`
Behavior:
1. Runs TSJ benchmark workloads and emits a JSON baseline report.
2. Benchmark suite includes:
   - `micro` workloads (startup + tight-loop style cases)
   - `macro` workloads (larger closure/class/module+async cases)
3. Supports warmup/measurement controls:
   - `--warmup <n>` warmup iteration count (`n >= 0`)
   - `--iterations <n>` measured iteration count (`n >= 1`)
4. Supports benchmark profile:
   - default `full` suite
   - `--smoke` quick suite with one micro + one macro workload
5. Supports compiler optimization toggles for benchmark runs:
   - `--optimize` (default)
   - `--no-optimize`

Success diagnostics:
- `TSJ-BENCH-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-009` missing benchmark report path
- `TSJ-CLI-010` invalid benchmark options
- `TSJ-BENCH-001` benchmark harness execution failure

## Diagnostic Shape
All diagnostics use one-line JSON objects:

```json
{
  "level": "INFO|ERROR",
  "code": "TSJ-*",
  "message": "human-readable message",
  "context": {
    "key": "value"
  }
}
```
