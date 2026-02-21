# Interop Audit Aggregation (TSJ-43b)

This document defines the TSJ-43b centralized audit aggregation subset.

## CLI Controls

1. Local audit log:
   - `--interop-audit-log <path>`
2. Centralized aggregate sink:
   - `--interop-audit-aggregate <path>`
3. Aggregation requires local fallback log configuration in this subset.

## Aggregate Event Schema

Aggregate events are emitted as JSON lines with schema tag:
`"schema":"tsj.interop.audit.v1"`.

Stable fields include:
1. Policy decision: `decision`, `code`, `message`
2. Target: `target`, `targetIndex`, `targetCount`
3. Execution context: `command`, `entry`, `policy`, `trace`
4. Outcome: `outcome` (`success`/`failure`)
5. Bounded-truncation metadata: `truncatedCount`

## Bounded Behavior

1. Aggregate emission is bounded by `MAX_AGGREGATE_AUDIT_EVENTS` per run.
2. When discovered targets exceed the cap, emitted records include truncation metadata.

## Sink Failure Behavior

1. If aggregate sink write fails, TSJ falls back to local audit log.
2. Fallback emits a diagnosable local audit record:
   - `code`: `TSJ-INTEROP-AUDIT-AGGREGATE`
   - `decision`: `warn`
   - message includes aggregate sink failure reason.

## Non-goals (TSJ-43b Subset)

1. Remote transport protocols (HTTP/Kafka/etc.).
2. Distributed delivery guarantees.
3. Advanced IAM/provider integrations beyond local-policy RBAC/approval subset (tracked by TSJ-43d).
