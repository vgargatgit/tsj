# TSJ-39b Introspector Matrix Fixtures

Fixtures under this directory drive `TsjIntrospectorCompatibilityMatrixHarness`.

Report artifact:
- `compiler/backend-jvm/target/tsj39b-introspector-matrix.json`

Fixture entries:
1. `tsj39b-strict-spring-web/fixture.properties`: Spring-web annotation introspection on strict executable controller classes.
2. `tsj39b-jackson-executable/fixture.properties`: Jackson-visible annotation and bean-shape introspection on strict executable DTO classes.
3. `tsj39b-validation-executable/fixture.properties`: Bean Validation metadata introspection on strict executable DTO classes.
4. `tsj39b-hibernate-executable/fixture.properties`: JPA/Hibernate entity metadata introspection on strict executable entity classes.
5. `tsj39b-jackson-unsupported/fixture.properties`: explicit unsupported introspector scenario with fallback guidance.
