# Story to Architecture Decision Map

This matrix is the traceability artifact for TSJ-0 acceptance criteria.
It maps backlog stories to the architecture decisions in `docs/architecture-decisions.md`.

## Legend
- `P`: primary decision driver
- `S`: secondary influence

## Matrix

| Story | AD-01 | AD-02 | AD-03 | AD-04 | AD-05 | AD-06 | AD-07 | AD-08 | Notes |
|---|---|---|---|---|---|---|---|---|---|
| TSJ-0 | P | P | P | P | P | P | P | P | Publish ADRs and runtime contracts |
| TSJ-1 | S | S | S | S | S | P | S | S | Monorepo must reflect compiler/runtime boundaries |
| TSJ-2 | S | S | P | S | S | S | P | P | CLI contract exposes compile/run and diagnostics |
| TSJ-3 | S | S | S | S | S | S | P | P | Differential harness enforces declared boundaries |
| TSJ-4 | S | S | S | S | S | P | P | S | Frontend must emit unsupported-feature diagnostics |
| TSJ-5 | S | S | S | S | S | P | S | S | HIR/MIR/JIR design is AD-06 core |
| TSJ-6 | S | S | S | S | S | P | S | S | CFG and capture metadata support lowering constraints |
| TSJ-7 | S | P | S | S | S | P | S | S | JIR->bytecode and primitive lanes |
| TSJ-8 | P | S | S | S | S | P | S | S | Closures and `this` binding align with runtime model |
| TSJ-9 | P | S | S | S | S | P | S | S | Class/object lowering depends on TsObject contract |
| TSJ-10 | S | P | S | S | S | S | S | S | Coercion/equality semantics |
| TSJ-11 | P | S | S | S | S | S | S | S | Prototype lookup and inline cache behavior |
| TSJ-12 | S | S | P | S | S | S | S | P | Static linking and live bindings |
| TSJ-13 | S | S | S | P | S | P | S | S | Async lowering + scheduler semantics |
| TSJ-14 | S | S | S | S | S | S | S | P | Debuggability promise for MVP subset |
| TSJ-15 | S | S | P | P | S | S | P | P | Unsupported feature policy enforcement |
| TSJ-16 | S | S | S | S | S | S | P | P | Conformance suite validates decision envelope |
| TSJ-17 | S | P | S | S | S | P | S | S | Optimization passes on MIR/JIR |
| TSJ-18 | S | S | S | S | S | S | S | P | SLA tracked against MVP boundary |
| TSJ-19 | S | S | S | S | P | S | P | S | Interop bridge and allowlist model |
| TSJ-26 | S | S | P | S | P | P | P | S | TS-level `java:` binding syntax + frontend diagnostics/metadata |
| TSJ-27 | S | S | P | S | P | S | P | S | Compile/run classpath inputs + artifact metadata + runtime loading |
| TSJ-28 | S | S | P | S | P | S | P | S | Integrated auto-bridge generation in compile pipeline with deterministic cache behavior |
| TSJ-29 | S | P | S | S | P | S | P | S | Interop invocation expansion (constructors/instance members/fields/overloads/varargs) |
| TSJ-30 | S | P | S | P | P | S | P | S | Codec conversion expansion + callback/Promise-CompletableFuture boundary adapters |
| TSJ-31 | S | S | P | S | P | S | P | P | Interop policy modes, any-jar workflow gating, and documented security guardrails |
| TSJ-32 | S | S | P | S | P | S | P | S | Runtime-visible annotation emission + parameter metadata on generated bridge/program classes |
| TSJ-32a | S | S | P | S | P | S | P | S | TS decorator extraction/mapping model for JVM annotation emission |
| TSJ-32b | S | P | P | S | P | S | P | S | Annotation attribute/value mapping coverage and validation |
| TSJ-32c | S | S | P | S | P | S | P | S | Parameter annotation emission + reflection parity conformance |
| TSJ-33 | S | S | P | S | P | S | P | S | Spring DI subset via generated `@Configuration` + typed `@Bean` bridge targets with constructor-injection coverage |
| TSJ-33a | S | S | P | S | P | S | P | S | Direct TS stereotype lowering for Spring component scanning paths |
| TSJ-33b | S | S | P | S | P | S | P | S | DI mode expansion for TS-authored beans and bean-method authoring |
| TSJ-33c | S | S | P | S | P | S | P | S | Bean lifecycle parity and failure diagnostics for TS-authored beans |
| TSJ-33d | S | S | P | S | P | S | P | P | Injection mode completeness (field/setter subset) and dependency-resolution diagnostics |
| TSJ-33e | S | S | P | S | P | S | P | P | Lifecycle ordering and circular-dependency diagnostic completeness |
| TSJ-33f | S | S | P | S | P | S | P | P | Differential Java/Kotlin parity gate and CI conformance report for DI+lifecycle subset |
| TSJ-34 | S | S | P | S | P | S | P | S | Spring web subset via interop-spec bridges + TS-decorator adapters for generated `@RestController` routes, request-param binding, and mapped exception handlers |
| TSJ-34a | S | S | P | S | P | S | P | S | Request binding completeness (path/query/header/body) for TS-authored controllers |
| TSJ-34b | S | S | P | S | P | S | P | S | Response serialization and status/error semantics parity for TS web subset |
| TSJ-34c | S | S | P | S | P | S | P | P | End-to-end booted web conformance and diagnostics readiness gate |
| TSJ-34d | S | S | P | S | P | S | P | P | Controller constructor-injection parity and controller-wiring diagnostics for TS-authored web adapters |
| TSJ-34e | S | S | P | S | P | S | P | P | Booted `HttpMessageConverter` + standardized error-envelope parity for TS web subset |
| TSJ-34f | S | S | P | S | P | S | P | P | Packaged Spring Boot runtime web conformance gate and CI certification artifact |
| TSJ-35 | S | S | P | S | P | S | P | S | TS-authored Spring component `@Transactional` subset with interface-based proxy compatibility and diagnostics |
| TSJ-35a | S | S | P | S | P | S | P | P | Class-based proxy parity (CGLIB-style subset) and deterministic proxy-strategy diagnostics |
| TSJ-35b | S | S | P | S | P | S | P | P | Booted Spring transaction/AOP runtime conformance over packaged execution path |
| TSJ-35c | S | S | P | S | P | S | P | P | Differential Java/Kotlin parity gate and CI artifact for transactional/AOP subset |
| TSJ-36 | S | S | P | S | P | S | P | P | Spring package/startup subset: CLI jar packaging, resource handling, smoke-run diagnostics, and CI template gate |
| TSJ-36a | S | S | P | S | P | S | P | P | Spring Boot-style repackage/fat-jar parity for TS-authored packaged apps |
| TSJ-36b | S | S | P | S | P | S | P | P | Embedded server startup plus endpoint smoke verification and diagnostics |
| TSJ-36c | S | S | P | S | P | S | P | P | Dev-loop tooling/workflow parity for iterative TS-authored Spring development |
| TSJ-37 | S | S | P | S | P | S | P | P | Spring ecosystem matrix subset with web parity checks, unsupported-module diagnostics, and CI artifact reporting |
| TSJ-37a | S | S | P | S | P | S | P | P | Validation module runtime parity and deterministic validation-diagnostic coverage |
| TSJ-37b | S | S | P | S | P | S | P | P | Data repository/ORM module baseline parity with reproducible integration fixtures |
| TSJ-37c | S | S | P | S | P | S | P | P | Actuator baseline endpoint parity and operational diagnostics coverage |
| TSJ-37d | S | S | P | S | P | S | P | P | Security filter and method-security baseline parity with explicit misconfiguration diagnostics |
| TSJ-37e | S | S | P | S | P | S | P | P | Full Spring module matrix parity gate and CI certification artifact publication |
| TSJ-38 | S | S | P | S | P | S | P | P | Kotlin-parity reference-app scaffolds and readiness gate reporting with explicit full-parity blockers |
| TSJ-38a | S | S | P | S | P | S | P | P | DB-backed TS/Kotlin reference-app parity over shared persistence fixtures |
| TSJ-38b | S | S | P | S | P | S | P | P | Security-enabled TS/Kotlin reference-app parity across baseline authn/authz flows |
| TSJ-38c | S | S | P | S | P | S | P | P | Full readiness-gate parity certification for correctness/startup/throughput/diagnostics |
| TSJ-39 | S | P | P | S | P | S | P | P | Interop bridge generic-signature metadata subset with reflection conformance tests and explicit metadata-gap diagnostics |
| TSJ-39a | S | P | P | S | P | S | P | P | Universal generated-class metadata parity across program/bridge/adapter/proxy artifacts |
| TSJ-39b | S | P | P | S | P | S | P | P | Third-party introspector compatibility matrix with versioned fixture scenarios |
| TSJ-39c | S | P | P | S | P | S | P | P | Metadata parity certification gate and CI artifact closure for broad interop claims |
| TSJ-40 | S | S | P | S | P | S | P | P | Deterministic classpath mediation subset with version-conflict diagnostics and multi-jar graph runtime tests |
| TSJ-40a | S | S | P | S | P | S | P | P | Maven/Gradle-style transitive dependency mediation graph parity with deterministic conflict rules |
| TSJ-40b | S | S | P | S | P | S | P | P | Scope-aware dependency resolution parity across compile/package/run classpath construction |
| TSJ-40c | S | S | P | S | P | S | P | P | Classloader isolation modes and boundary-diagnostic parity for app-vs-dependency loading |
| TSJ-40d | S | S | P | S | P | S | P | P | Dependency mediation parity gate and CI certification artifact for closure |
| TSJ-41 | S | P | P | S | P | S | P | P | Advanced interop invocation/conversion subset with overload ranking and richer mismatch diagnostics |
| TSJ-41a | S | P | P | S | P | S | P | P | Numeric widening + primitive/wrapper overload parity for deterministic interop invocation |
| TSJ-41b | S | P | P | S | P | S | P | P | Generic-type adaptation parity across nested interop conversion scenarios |
| TSJ-41c | S | P | P | S | P | S | P | P | Reflective edge-case invocation parity with explicit compatibility diagnostics |
| TSJ-41d | S | P | P | S | P | S | P | P | Invocation/conversion parity gate and CI certification artifact for closure |
| TSJ-42 | S | P | P | S | P | S | P | P | Hibernate/JPA-oriented compatibility subset fixtures and unsupported-pattern diagnostics |
| TSJ-42a | S | P | P | S | P | S | P | P | Real-database Hibernate/JPA integration parity across supported backend/version fixtures |
| TSJ-42b | S | P | P | S | P | S | P | P | Proxy and lazy-loading behavior parity with explicit unsupported-pattern diagnostics |
| TSJ-42c | S | P | P | S | P | S | P | P | Persistence-context lifecycle and transaction-coupled ORM semantics parity |
| TSJ-42d | S | P | P | S | P | S | P | P | Hibernate/JPA compatibility gate and CI certification artifact for closure |
| TSJ-43 | S | S | P | S | P | S | P | P | Broad-mode risk acknowledgement, denylist/audit/trace controls, and policy regression coverage |
| TSJ-43a | S | S | P | S | P | S | P | P | Fleet-level policy management and deterministic policy-source precedence/diagnostics |
| TSJ-43b | S | S | P | S | P | S | P | P | Centralized audit aggregation schema/transport parity with operational reporting guarantees |
| TSJ-43c | S | S | P | S | P | S | P | P | Enterprise RBAC and approval-path integration for broad-mode interop authorization |
| TSJ-43d | S | S | P | S | P | S | P | P | Guardrail parity gate and CI operational certification artifact for closure |
| TSJ-44 | S | S | P | S | P | S | P | P | Any-jar certification closure with matrix/range/workload governance gates and compatibility manifest publication |
| TSJ-44a | S | S | P | S | P | S | P | P | Ecosystem matrix expansion using real third-party library fixtures and certification artifacts |
| TSJ-44b | S | S | P | S | P | S | P | P | Version-range certification and compatibility drift tracking across supported libraries |
| TSJ-44c | S | S | P | S | P | S | P | P | Real-application certification workloads with reliability/performance SLO gate |
| TSJ-44d | S | S | P | S | P | S | P | P | Certification governance and release-signoff closure for any-jar claims |
| TSJ-45 | S | S | P | S | P | S | P | P | Mediated classpath symbol index with deterministic class-origin diagnostics |
| TSJ-46 | S | P | P | S | P | S | P | S | Classfile header/member/attribute reader for descriptor construction without classloading |
| TSJ-47 | S | S | P | S | P | S | S | P | Lazy Java SymbolTable with classpath-fingerprint-aware descriptor caching |
| TSJ-48 | S | P | P | S | P | S | P | S | Descriptor type model and generic-signature parsing with best-effort fallback |
| TSJ-49 | S | P | P | S | P | S | P | S | Nullability/platform-type inference from Java annotations and type-use metadata |
| TSJ-50 | S | S | P | S | P | S | P | S | Inheritance/member lookup normalization with visibility and module-export rules |
| TSJ-51 | S | P | P | S | P | S | P | S | Override-erasure and bridge/synthetic filtering to model Java method semantics |
| TSJ-52 | P | S | P | S | P | S | S | S | Java property synthesis (`get/set/is`) for TS ergonomics with deterministic conflict handling |
| TSJ-53 | S | P | P | S | P | S | S | S | SAM detection and functional typing metadata for Java callback typing |
| TSJ-54 | S | P | P | S | P | S | P | S | Compile-time overload resolution with stable selected-target identity and lockstep runtime invocation |
| TSJ-55 | S | S | P | S | P | S | P | S | Frontend/typechecker integration for descriptor-backed `java:` imports |
| TSJ-56 | S | S | P | S | P | S | S | P | Persistent incremental descriptor cache with warm-build reuse and corruption recovery |
| TSJ-57 | S | S | P | S | P | S | P | S | Module-access + multi-release JAR correctness for descriptor lookup and diagnostics |
| TSJ-57a | S | S | P | S | P | S | P | S | Automatic module graph extraction for named modules and `jrt:/` integration |
| TSJ-57b | S | S | P | S | P | S | P | P | Mixed classpath/module-path conformance fixtures for MR-JAR and module diagnostics |
| TSJ-58 | S | S | P | S | S | P | P | S | Cut over backend from handwritten parser to frontend AST ingestion contract |
| TSJ-58a | S | S | P | S | S | P | P | S | Complete AST ingestion by removing backend handwritten parser in default pipeline |
| TSJ-58b | S | S | P | S | S | P | P | S | Finish lowering cutover so typed AST is backend source-of-truth |
| TSJ-58c | S | S | P | S | S | P | P | S | Disable default handwritten-parser fallback once typed AST lowering coverage is sufficient |
| TSJ-59 | P | S | S | S | S | P | S | S | Statement syntax completeness (`for`/`switch`/labeled control flow) |
| TSJ-59a | P | S | S | S | S | P | S | S | Complete remaining statement lowering (`for*`, `switch`, labeled control-flow) |
| TSJ-59b | P | S | S | S | S | P | S | S | Complete `for...of`/`for...in`, labeled control-flow target resolution, and switch fallthrough semantics |
| TSJ-60 | P | S | S | S | S | P | S | S | Expression/operator grammar completeness with precedence parity |
| TSJ-61 | P | S | S | S | S | P | S | S | Binding patterns/destructuring/default/rest lowering completeness |
| TSJ-62 | P | S | S | S | S | P | S | S | Class/object syntax completeness (fields/accessors/computed members) |
| TSJ-63 | S | S | S | P | S | P | S | S | Function-form completeness (defaults/rest/generators/async-generators) |
| TSJ-64 | S | S | P | S | S | P | P | S | Type-syntax erasure completeness and type-only import/export handling |
| TSJ-65 | S | S | P | P | S | S | P | S | Module/import-export syntax parity including dynamic import semantics |
| TSJ-66 | S | S | P | S | P | S | P | S | Decorator syntax parity across legacy and stage-3 forms |
| TSJ-67 | S | S | P | S | S | S | S | S | TSX/JSX syntax support with configured lowering modes |
| TSJ-68 | S | S | S | S | S | S | P | P | Large-scale conformance gate for full syntax claim and regression tracking |
| TSJ-69 | S | S | S | S | S | S | S | P | Incremental performance closure for full-syntax pipeline |
| TSJ-70 | S | S | P | S | S | S | P | P | Full TypeScript syntax GA readiness gate and release signoff artifact |

## Change Control
1. Any new story must be added to this matrix before implementation starts.
2. Any ADR update requires a matrix review in the same change.
3. CI should eventually validate that every story references at least one AD.
