# Interop Reflective Compatibility (TSJ-41c)

This document defines TSJ-41c reflective invocation compatibility for Java interop calls.

## Supported Reflective Subset

1. Instance and static invocation over public reflective members discovered through runtime class metadata.
2. Default interface method dispatch through instance bindings.
3. Generic override scenarios where bridge/synthetic methods exist:
   - TSJ prefers non-bridge candidates when available.
   - Candidate diagnostics stay deterministic for generic override signatures.
4. Existing TSJ-41/TSJ-41a/TSJ-41b conversion and overload scoring rules remain in effect.

## Unsupported Reflective Patterns (with Targeted Diagnostics)

1. Non-public reflective member access (method/constructor/field) is rejected.
2. Failure emits:
   - message token: `TSJ-INTEROP-REFLECTIVE`
   - contextual member key (`<class>#<member>`)
   - declared restricted candidates for triage.

## Operational Fallback Guidance

1. Prefer public adapter/facade methods around restricted members.
2. For generic override APIs, invoke stable public signatures directly rather than relying on bridge dispatch.
3. Keep `strict`/`broad` interop policy controls from TSJ-43 enabled as appropriate for environment risk posture.

## Non-goals (TSJ-41c Subset)

1. Full reflective parity for every JVM accessibility mechanism.
2. Runtime auto-escalation (`setAccessible`) for restricted members.
3. Closure-gate certification and release artifact publishing for TSJ-41
   (tracked by TSJ-41d).
