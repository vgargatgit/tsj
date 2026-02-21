# Interop Invocation/Conversion Certification (TSJ-41d)

TSJ-41d closes the TSJ-41 story with a deterministic certification gate over three
interop parity families:

1. `numeric-widening` (TSJ-41a)
2. `generic-adaptation` (TSJ-41b)
3. `reflective-edge` (TSJ-41c)

## Certified Scenarios

The certification harness executes both success and diagnostic scenarios per family:

1. Numeric widening success + narrowing rejection diagnostics.
2. Generic adaptation success + generic-target conversion diagnostics.
3. Reflective default/bridge dispatch success + non-public reflective access diagnostics.

## Artifact

Report artifact path:

`cli/target/tsj41d-invocation-conversion-certification.json`

The report includes:

1. Family-level pass/fail summary.
2. Scenario-level pass/fail rows with fixture ID, library, version, and diagnostic code.

## CI Gate

CI runs the TSJ-41d gate via:

`TsjInvocationConversionCertificationTest#certificationGateRequiresAllFamilySuitesToPass`

The report is uploaded as a build artifact for regression tracking.

## Certified Scope vs Best Effort

Certified scope:

1. Numeric widening and primitive/wrapper overload parity for documented TSJ-41a cases.
2. Nested generic adaptation for documented TSJ-41b container/type cases.
3. Reflective default-method and bridge-aware dispatch parity, plus non-public access diagnostics.

Best-effort/out-of-scope (not certified by TSJ-41d):

1. Arbitrary third-party reflective frameworks outside documented TSJ-41c patterns.
2. Full framework-level compatibility guarantees (covered by TSJ-42+ and TSJ-44+ gates).
3. Version-range compatibility governance (covered by TSJ-44b+).
