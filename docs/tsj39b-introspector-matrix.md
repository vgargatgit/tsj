# TSJ-39b Introspector Compatibility Matrix

This document defines the TSJ-39b compatibility matrix executed by
`TsjIntrospectorCompatibilityMatrixHarness`.

Report artifact:
- `compiler/backend-jvm/target/tsj39b-introspector-matrix.json`

Fixture root:
- `tests/introspector-matrix`

## Scenario Matrix

| Scenario | Library | Version | Status | Fixture | Notes |
|---|---|---|---|---|---|
| `strict-spring-web-executable-introspection` | `spring-web` | `tsj-jvm-strict` | Supported | `tests/introspector-matrix/tsj39b-strict-spring-web/fixture.properties` | Validates `@RequestMapping`/`@GetMapping`/`@RequestParam` metadata visibility directly on strict executable JVM classes. |
| `jackson-executable-dto-introspection` | `jackson-databind` | `2.x` | Supported | `tests/introspector-matrix/tsj39b-jackson-executable/fixture.properties` | Validates direct Jackson serialization/deserialization plus imported annotation visibility on strict executable DTO classes. |
| `validation-executable-dto-introspection` | `hibernate-validator` | `8.x` | Supported | `tests/introspector-matrix/tsj39b-validation-executable/fixture.properties` | Validates direct Bean Validation metadata visibility on strict executable DTO classes. |
| `hibernate-executable-entity-introspection` | `hibernate-core` | `6.x` | Supported | `tests/introspector-matrix/tsj39b-hibernate-executable/fixture.properties` | Validates imported JPA metadata visibility on strict executable entity classes during Hibernate bootstrap. |
| `jackson-unsupported` | `jackson-databind` | `2.x` | Unsupported (explicit) | `tests/introspector-matrix/tsj39b-jackson-unsupported/fixture.properties` | Emits stable `TSJ39B-INTROSPECTOR-UNSUPPORTED` diagnostic with fallback guidance. |

## Execution

```bash
mvn -B -ntp -pl compiler/backend-jvm -am -Dtest=TsjIntrospectorCompatibilityMatrixTest test
```
