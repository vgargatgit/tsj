# Interop Metadata Guarantees (TSJ-39 / TSJ-39a)

This document defines JVM metadata guarantees for TSJ-generated classes in the TSJ-39 subset.

## Guarantees

1. Metadata parity is validated across documented generated-class families:
   - program classes,
   - interop bridge classes,
   - TS-authored Spring component/web adapter classes,
   - generated proxy interfaces/artifacts.
2. Runtime-visible class/method annotations configured via interop spec are emitted on generated bridges.
3. Parameter names are retained for generated classes compiled through TSJ (`-parameters` compile path).
4. Typed Spring bean/web bridge methods preserve parameterized generic signatures when target signatures are concrete.
5. Reflection conformance tests validate:
   - generic return type visibility,
   - generic parameter type visibility,
   - parameter-name retention across generated-class families.
6. Third-party introspector subset compatibility is tracked in TSJ-39b via fixture-driven matrix scenarios:
   - report artifact: `compiler/backend-jvm/target/tsj39b-introspector-matrix.json`.

## Explicit Metadata Gaps (with Diagnostics)

1. Bridge target signatures containing unresolved generic type variables are rejected.
2. Diagnostic emitted:
   - `code`: `TSJ-INTEROP-METADATA`
   - `featureId`: `TSJ39-ABI-METADATA`

## Non-goals in TSJ-39 Subset

1. Full generic-signature synthesis for every bridge path outside supported typed Spring bean/web bridge methods.
2. Compatibility guarantees for arbitrary framework-specific metadata conventions not covered by conformance tests.
3. Full third-party introspector/library certification closure
   (tracked by TSJ-39c).
