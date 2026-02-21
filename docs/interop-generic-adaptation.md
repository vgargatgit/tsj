# Interop Generic Adaptation Guarantees (TSJ-41b)

This document defines the TSJ-41b subset guarantees for generic-type adaptation at Java interop boundaries.

## Supported Guarantees

1. Interop argument conversion supports reflective `Type`-driven adaptation for representative nested generic targets.
2. Nested container adaptation is supported for:
   - `List<T>` and `Set<T>`
   - `Map<K,V>`
   - `Optional<T>`
   - arrays and generic arrays
   - `CompletableFuture<T>`
3. Recursive conversion honors generic element/key/value types and preserves deterministic conversion behavior
   from TSJ-41/TSJ-41a overload resolution.
4. Enum conversion within generic containers is supported when TS values map to enum names.
5. Adaptation failures include explicit target-type context with the error prefix:
   `Generic interop conversion failed`.

## Diagnostics

1. Generic adaptation failures surface the target type and contextual position
   (for example collection index, map key/value slot, or nested path segment).
2. Existing invocation mismatch diagnostics keep candidate-signature context from TSJ-41.

## Non-goals (TSJ-41b Subset)

1. Universal Java generic parity for all reflective edge cases and synthetic bridge behavior
   (tracked by TSJ-41c).
2. Static compile-time type checking between TS declarations and Java generic bounds.
3. Full covariance/contravariance semantics beyond runtime conversion subset rules.
4. Certification-gate closure for broad invocation/conversion parity
   (tracked by TSJ-41d).
