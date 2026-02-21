# TSJ Interop Policy and Guardrails (TSJ-31, TSJ-43)

## Overview
TSJ interop policy controls how `java:` imports are authorized during `tsj compile` and `tsj run`.

Policy modes:
1. `strict` (default)
2. `broad` (opt-in)

CLI option:

```bash
--interop-policy strict|broad
--interop-role roleA,roleB
--interop-approval <token>
```

## Strict Mode (Default)
1. `java:` bindings require `--interop-spec <interop.properties>`.
2. Allowlist enforcement remains active (`TSJ19-ALLOWLIST`).
3. Best for CI, shared repos, and production pipelines.

Failure behavior:
1. If `java:` bindings are discovered without an interop spec, TSJ emits `TSJ-INTEROP-POLICY`.

## Broad Mode (Opt-In)
1. Enables unrestricted classpath interop without allowlist requirement.
2. Intended for local experimentation, migration spikes, and rapid prototyping.
3. Can still be combined with `--interop-spec`; broad mode will not block discovered bindings.
4. Requires explicit acknowledgment:

```bash
--ack-interop-risk
```

If omitted, TSJ emits `TSJ-INTEROP-RISK`.

## RBAC and Approval (TSJ-43c Subset)
Broad-mode interop can be additionally gated by role and approval policy.

Supported fleet policy keys (`.tsj/interop-policy.properties` or global policy source):
1. `interop.rbac.roles` (comma-separated actor roles)
2. `interop.rbac.requiredRoles` (comma-separated baseline roles for broad interop targets)
3. `interop.rbac.sensitiveTargets` (comma-separated class/method patterns treated as sensitive scope)
4. `interop.rbac.sensitiveRequiredRoles` (comma-separated roles required when sensitive scope is matched)
5. `interop.approval.required` (`true|false`)
6. `interop.approval.token` (expected approval token in this subset)
7. `interop.approval.targets` (comma-separated patterns that require approval when enabled)

Command-level inputs:
1. `--interop-role roleA,roleB` sets actor roles for the current command.
2. `--interop-approval <token>` provides approval token for approval-gated targets.

Diagnostics:
1. `TSJ-INTEROP-RBAC`: actor roles do not satisfy required role scope.
2. `TSJ-INTEROP-APPROVAL`: approval-gated target invoked without valid approval token.

## Risks
1. Reflection surface area increases significantly in broad mode.
2. Unexpected classpath changes can alter behavior between environments.
3. Sensitive classes/methods can be invoked if runtime classpath is not controlled.

## Guardrails
1. Keep `strict` mode as default in scripts and CI.
2. Use `broad` only in explicitly scoped workflows.
3. Pin classpath/jar versions in build configuration.
4. Run interop-heavy tests in CI for deterministic verification.
5. Prefer focused allowlists for production releases.
6. Use denylist controls for high-risk packages/classes:

```bash
--interop-denylist "java.lang.System,java.lang.Runtime"
```

7. Capture decision logs for incident triage:

```bash
--interop-audit-log path/to/interop-audit.log
```

8. Capture centralized aggregate audit events (TSJ-43b subset):

```bash
--interop-audit-aggregate path/to/interop-audit-aggregate.jsonl
```

9. Enable runtime invocation tracing only during debugging:

```bash
--interop-trace
```

## Failure Triage
1. `TSJ-INTEROP-POLICY`: strict mode blocked `java:` binding without allowlist spec.
2. `TSJ-INTEROP-RISK`: broad mode used without `--ack-interop-risk`.
3. `TSJ-INTEROP-DENYLIST`: binding target matched denylist pattern.
4. `TSJ-INTEROP-AUDIT-AGGREGATE`: centralized sink write failed; local fallback was used.
5. `TSJ-INTEROP-RBAC`: role/scope authorization failure in broad mode.
6. `TSJ-INTEROP-APPROVAL`: approval token missing/invalid for approval-gated interop scope.

Recommended triage flow:
1. Reproduce with `--interop-trace`, `--interop-audit-log`, and `--interop-audit-aggregate`.
2. Confirm classpath ordering and jar versions (`TSJ-CLASSPATH-CONFLICT` checks).
3. Narrow denylist/allowlist scope to minimum required targets.
4. Add/extend fixture coverage before enabling broad mode in shared CI paths.

## Certification Gate (TSJ-43d)
TSJ-43d adds a closure gate for guardrail parity across three dimensions:

1. Fleet-policy precedence/conflict behavior (`TSJ-43a`).
2. Centralized-audit primary/fallback behavior (`TSJ-43b`).
3. RBAC/approval authorization behavior (`TSJ-43c`).

Certification artifact:

`cli/target/tsj43d-guardrail-certification.json`

CI runs:

`TsjGuardrailCertificationTest#certificationGateRequiresAllGuardrailFamiliesToPass`

Certified scope:

1. Project/global policy precedence and conflict diagnostics (`TSJ-INTEROP-POLICY-CONFLICT`).
2. Aggregate audit event emission and local fallback diagnostics (`TSJ-INTEROP-AUDIT-AGGREGATE`).
3. RBAC role enforcement and approval-token enforcement (`TSJ-INTEROP-RBAC`, `TSJ-INTEROP-APPROVAL`).

Best-effort/out-of-scope for TSJ-43d certification:

1. Advanced external IAM provider integrations beyond documented baseline role/token flow.
2. Organization-specific policy distribution tooling outside CLI/global/project source precedence.
3. Non-certified operational environments where guardrail flags/policy files are unmanaged.

## Rollout Guidance
1. Development spike: `broad` + explicit risk acknowledgment + audit log.
2. Team CI: prefer `strict` + `--interop-spec`; run broad-mode tests only where needed.
3. Release: ship only with documented certified subset (see `docs/anyjar-certification.md`).

## Recommended Defaults
1. CI: `strict` + `--interop-spec`.
2. Production release builds: `strict` + reviewed allowlist.
3. Local dev: `broad` only when needed, then migrate back to strict for commit/PR validation.
