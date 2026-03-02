# Interop Compatibility Guide (TSJ-39 + TSJ-41)

This guide consolidates runtime interop guarantees and certification scope.

## Scope

Covers:

1. Metadata guarantees (`TSJ-39`, `TSJ-39a`, `TSJ-39b`)
2. Invocation/conversion guarantees (`TSJ-41a`, `TSJ-41b`, `TSJ-41c`)
3. Certification closure (`TSJ-41d`)

## Metadata Guarantees (TSJ-39 Family)

TSJ guarantees reflection-visible metadata for generated classes in supported paths:

1. Generated program classes
2. Interop bridge classes
3. TS-authored Spring component/web adapter classes
4. Generated proxy/interface artifacts in supported flows

Guaranteed behaviors:

1. Runtime-visible class/method annotations configured by interop spec are emitted.
2. Parameter names are retained (`-parameters` compile path).
3. Generic return/parameter signatures are preserved for supported typed bridge methods.
4. Introspector matrix scenarios are tracked in:
   `compiler/backend-jvm/target/tsj39b-introspector-matrix.json`.

Targeted metadata diagnostic:

1. `TSJ-INTEROP-METADATA` (`featureId=TSJ39-ABI-METADATA`) for unsupported signature shapes.

## Invocation/Conversion Guarantees (TSJ-41 Family)

### Numeric overload selection (TSJ-41a)

1. Deterministic widening selection across primitive/wrapper numeric candidates.
2. Numeric narrowing candidates rejected with explicit mismatch diagnostics.

### Generic adaptation subset (TSJ-41b)

1. Type-driven conversion for nested:
   `List`, `Set`, `Map`, `Optional`, arrays, `CompletableFuture`.
2. Recursive adaptation with target-type context.
3. Generic conversion failures include target context with message prefix:
   `Generic interop conversion failed`.

### Reflective compatibility subset (TSJ-41c)

1. Public instance/static member invocation over reflective metadata.
2. Default-interface method dispatch support.
3. Bridge-aware candidate handling for generic override signatures.
4. Non-public reflective access rejected with `TSJ-INTEROP-REFLECTIVE` context.

## TSJ-41d Certification Gate

Certification families:

1. `numeric-widening`
2. `generic-adaptation`
3. `reflective-edge`

Artifact:

1. `cli/target/tsj41d-invocation-conversion-certification.json`

CI gate:

1. `TsjInvocationConversionCertificationTest#certificationGateRequiresAllFamilySuitesToPass`

## Certified vs Best-Effort

Certified:

1. Documented TSJ-41a numeric widening scenarios
2. Documented TSJ-41b generic adaptation scenarios
3. Documented TSJ-41c reflective/default-method scenarios

Best-effort:

1. Arbitrary framework-specific reflection edge cases beyond listed fixtures
2. Full framework compatibility claims outside TSJ-41d certification scope
