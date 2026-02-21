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
| `bridge-generic-signature` | `java-reflection` | `21` | Supported | `tests/introspector-matrix/tsj39b-bridge-generic/fixture.properties` | Validates generic return/parameter signature visibility and parameter-name retention on generated bridge methods. |
| `spring-web-mapping-introspection` | `spring-web-stub` | `tsj-34-subset` | Supported | `tests/introspector-matrix/tsj39b-spring-web/fixture.properties` | Validates `@RequestMapping`/`@GetMapping` metadata visibility on generated web controller adapters. |
| `jackson-unsupported` | `jackson-databind` | `2.x` | Unsupported (explicit) | `tests/introspector-matrix/tsj39b-jackson-unsupported/fixture.properties` | Emits stable `TSJ39B-INTROSPECTOR-UNSUPPORTED` diagnostic with fallback guidance. |

## Execution

```bash
mvn -B -ntp -pl compiler/backend-jvm -am -Dtest=TsjIntrospectorCompatibilityMatrixTest test
```
