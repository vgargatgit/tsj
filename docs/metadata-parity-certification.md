# TSJ-39c Metadata Parity Certification Gate

TSJ-39c closes TSJ-39 with a certification gate that combines generated-class
metadata parity checks and introspector matrix conformance thresholds.

## Harness

- Test entry point:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjMetadataParityCertificationTest.java`
- Harness implementation:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjMetadataParityCertificationHarness.java`
- Report model:
  `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjMetadataParityCertificationReport.java`

## Gate Inputs

1. Generated-class family parity checks for:
   `program`, `component`, `proxy`, `web-controller`, `interop-bridge`
2. TSJ-39b introspector compatibility matrix scenarios

## Report Artifact

- Module artifact:
  `compiler/backend-jvm/target/tsj39c-metadata-parity-certification.json`
- Report includes:
  `gatePassed`, class-family pass/fail dimensions, introspector scenario pass/fail dimensions

## CI/Gate Behavior

- The JUnit gate fails if:
  - any generated-class family parity check fails,
  - any supported introspector scenario fails,
  - unsupported introspector diagnostics are not stable (`TSJ39B-INTROSPECTOR-UNSUPPORTED`).
