# TSJ-38b Security Reference Parity

## Purpose
TSJ-38b certifies baseline security parity for TS and Kotlin reference workflows.

The gate validates:
1. Authenticated access and role-based success paths.
2. Failure semantics for unauthenticated and unauthorized requests.
3. Distinct diagnostics for security configuration failures.

## Certified Scenarios

| Scenario | Expected Behavior |
|---|---|
| `authenticated-access` | TS output matches Java/Kotlin reference output |
| `role-based-admin-access` | Admin-role path output matches references |
| `unauthenticated-access` | Fails with `TSJ-SECURITY-AUTHN-FAILURE` |
| `authorization-denied` | Fails with `TSJ-SECURITY-AUTHZ-FAILURE` |
| `configuration-failure` | Fails with `TSJ-SECURITY-CONFIG-FAILURE` |

## Report Artifact

`compiler/backend-jvm/target/tsj38b-security-parity-report.json`

The report includes:
1. Supported-scenario TS/Java/Kotlin parity rows.
2. Expected-vs-observed diagnostic checks for authn/authz/config failure families.
3. Gate pass/fail summary.

## Local Run

```bash
mvn -B -ntp -pl compiler/backend-jvm -am -Dtest=TsjKotlinSecurityParityTest -Dsurefire.failIfNoSpecifiedTests=false test
```
