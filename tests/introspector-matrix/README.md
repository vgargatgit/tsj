# TSJ-39b Introspector Matrix Fixtures

Fixtures under this directory drive `TsjIntrospectorCompatibilityMatrixHarness`.

Report artifact:
- `compiler/backend-jvm/target/tsj39b-introspector-matrix.json`

Fixture entries:
1. `tsj39b-bridge-generic/fixture.properties`: reflection generic-signature introspection on generated interop bridge.
2. `tsj39b-spring-web/fixture.properties`: Spring-web annotation introspection on generated controller adapter.
3. `tsj39b-jackson-unsupported/fixture.properties`: explicit unsupported introspector scenario with fallback guidance.
