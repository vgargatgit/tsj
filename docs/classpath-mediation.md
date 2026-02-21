# Classpath Mediation (TSJ-40 + TSJ-40a + TSJ-40b + TSJ-40c + TSJ-40d)

TSJ classpath mediation defines deterministic handling for `--classpath` and `--jar`
inputs across `compile`, `run`, and `spring-package`.

## Current Behavior

1. Classpath inputs are normalized to absolute paths and deduplicated.
2. Compile artifacts persist resolved classpath entries as:
   - `interopClasspath.count`
   - `interopClasspath.<index>`
3. Legacy jar-name conflict detection still fails fast for non-Maven metadata jars:
   - Example conflict: `foo-1.0.jar` and `foo-2.0.jar`.
   - Diagnostic code: `TSJ-CLASSPATH-CONFLICT`.
4. Maven/Gradle-style mediation subset is supported when jars expose Maven metadata
   (`META-INF/maven/**/pom.properties` and `pom.xml`):
   - graph roots are source jars within the provided classpath set,
   - dependency candidates are resolved inside the provided set,
   - version mediation is deterministic:
     `nearest` depth, then `root-order`, then deterministic discovery order.
5. Compile artifacts persist mediation decisions as:
   - `interopClasspath.mediation.count`
   - `interopClasspath.mediation.<index>.artifact`
   - `interopClasspath.mediation.<index>.selectedVersion`
   - `interopClasspath.mediation.<index>.selectedPath`
   - `interopClasspath.mediation.<index>.rejectedVersion`
   - `interopClasspath.mediation.<index>.rejectedPath`
   - `interopClasspath.mediation.<index>.rule`
6. Scope-aware dependency resolution subset is supported:
   - supported dependency scopes: `compile`, `runtime`, `provided`, `test`,
   - compile-path scope contract:
     allowed scopes are `compile,runtime,provided`,
   - run/spring-package scope contract:
     allowed scopes are `compile,runtime`,
   - unsupported/other scope tokens are currently treated as pass-through for compatibility.
7. Compile artifacts persist scope-resolution metadata as:
   - `interopClasspath.scope.usage`
   - `interopClasspath.scope.allowed`
   - `interopClasspath.scope.excluded.count`
   - `interopClasspath.scope.excluded.<index>.ownerArtifact`
   - `interopClasspath.scope.excluded.<index>.ownerVersion`
   - `interopClasspath.scope.excluded.<index>.dependencyArtifact`
   - `interopClasspath.scope.excluded.<index>.dependencyVersion`
   - `interopClasspath.scope.excluded.<index>.scope`
   - `interopClasspath.scope.excluded.<index>.usage`
   - `interopClasspath.scope.excluded.<index>.excludedPath`
8. Mis-scoped interop-target usage now fails fast with:
   - diagnostic code `TSJ-CLASSPATH-SCOPE`,
   - context fields including `targetClass`, `scope`, `usage`, and `excludedPath`.
9. Runtime classloader isolation subset is supported:
   - CLI option: `--classloader-isolation shared|app-isolated`,
   - default mode: `shared`,
   - deterministic app-vs-dependency duplicate-class conflict detection in `app-isolated`.
10. Isolation diagnostics are explicit:
   - `TSJ-RUN-009`: duplicate class exists in both app output and dependency classpath under `app-isolated`,
   - `TSJ-RUN-010`: dependency-loader boundary violation (class resolution miss under `app-isolated`).
11. Compile artifacts persist classloader mode metadata:
   - `interopClasspath.classloaderIsolation`

## Test Coverage

1. `TsjCliTest.runSupportsMultiJarDependencyGraphClasspath` validates multi-jar runtime linkage.
2. `TsjCliTest.compileRejectsConflictingJarVersionsInClasspath` validates deterministic
   legacy conflict diagnostics.
3. `TsjCliTest.runMediatesTransitiveDependencyGraphUsingNearestRule` validates nearest-depth
   mediation over a transitive graph.
4. `TsjCliTest.runMediatesSameDepthConflictsUsingRootOrderTiebreak` validates deterministic
   root-order tie-break mediation and persisted mediation metadata.
5. `TsjCliTest.compileIncludesProvidedScopeDependenciesForInteropResolution` validates
   compile-path inclusion for `provided` scope plus persisted scope metadata.
6. `TsjCliTest.runRejectsInteropTargetsAvailableOnlyViaProvidedScope` validates
   explicit `TSJ-CLASSPATH-SCOPE` diagnostics for run-path mis-scoped interop targets.
7. `TsjCliTest.runPersistsScopeFilteringMetadataForTestScopedDependencies` validates
   deterministic runtime filtering for `test` scope and persisted exclusion metadata.
8. `TsjCliTest.runSupportsAppIsolatedClassloaderModeForInteropDependencies` validates
   successful execution under explicit app-isolated classloading.
9. `TsjCliTest.runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode`
   validates deterministic `TSJ-RUN-009` conflict diagnostics.
10. `TsjCliTest.runRejectsUnknownClassloaderIsolationMode` validates strict CLI mode parsing
   with `TSJ-CLI-015`.
11. `TsjDependencyMediationCertificationTest.certificationHarnessWritesReportAndModuleArtifact`
    validates TSJ-40d report generation and module artifact emission.
12. `TsjDependencyMediationCertificationTest.certificationGateRequiresGraphScopeAndIsolationParity`
    validates TSJ-40d closure gate across mediation, scope, and isolation suites.

## Current Limits

1. Resolution is limited to jars already provided on CLI classpath inputs.
2. Certification is currently scoped to deterministic TSJ-40a/40b/40c scenarios and does not yet
   certify version-range drift behavior (tracked by TSJ-44b).
3. TSJ-40d certification report artifact path:
   `cli/target/tsj40d-dependency-mediation-certification.json`.
