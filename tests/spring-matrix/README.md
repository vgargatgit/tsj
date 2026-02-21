# TSJ-37 Spring Matrix Fixtures

These fixtures are used by `TsjSpringIntegrationMatrixHarness` to execute the
TSJ-37 Spring ecosystem matrix and generate:

- `compiler/backend-jvm/target/tsj37-spring-module-matrix.json`

Fixtures:

1. `tsj37-web-supported/main.ts`: supported web subset parity scenario.
2. `tsj37-validation-supported/main.ts`: validation subset parity scenario for
   `@Validated` + `@NotBlank`/`@Size`/`@Min`/`@Max`/`@NotNull`.
3. `tsj37-validation-unsupported/main.ts`: unsupported validation decorator (`@Email`) diagnostic gate.
4. `tsj37-data-jdbc-supported/main.ts`: data-jdbc baseline subset parity scenario for
   repository query-method naming (`countBy*`/`findBy*`) and transactional service wiring.
5. `tsj37-data-jdbc-unsupported/main.ts`: out-of-scope JDBC query decorator (`@Query`) diagnostic gate.
6. `tsj37-actuator-supported/main.ts`: actuator baseline subset parity scenario (health/info/metrics read operations).
7. `tsj37-actuator-unsupported/main.ts`: unsupported actuator operation decorator (`@WriteOperation`) diagnostic gate.
8. `tsj37-security-supported/main.ts`: security baseline subset parity scenario for
   `@PreAuthorize` `hasRole`/`hasAnyRole` patterns.
9. `tsj37-security-unsupported/main.ts`: unsupported security expression diagnostic gate.
10. `tsj35b-transaction-chain/main.ts`: TSJ-35b transactional multi-bean commit-chain scenario.
11. `tsj35b-transaction-rollback/main.ts`: TSJ-35b transactional multi-bean rollback-chain scenario.
12. `tsj33f-mixed-injection/main.ts`: TSJ-33f DI parity fixture for constructor + field + setter injection.
13. `tsj33f-lifecycle-order/main.ts`: TSJ-33f lifecycle ordering parity fixture.
14. `tsj33f-cycle-diagnostic/main.ts`: TSJ-33f lifecycle cycle-diagnostic parity fixture.
