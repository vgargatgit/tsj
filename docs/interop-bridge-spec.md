# Interop Bridge Spec (TSJ-19)

TSJ-19 interop bridge generation is opt-in through:

```bash
tsj interop <interop.properties> --out <dir>
```

## File Format
The spec is a Java properties file.

Required:
1. `allowlist`: comma-separated `<fully.qualified.Class>#<methodName>` entries.

Optional:
1. `targets`: comma-separated target entries to generate.
   - If omitted, no bridges are generated (opt-in by explicit target selection).

Example:

```properties
allowlist=java.lang.Math#max,java.lang.Integer#parseInt
targets=java.lang.Math#max
```

## Validation Rules
1. Every `targets` entry must exist in `allowlist`.
2. Target class must exist on the classpath.
3. Target method must exist as a public static method on that class.

## Generated Outputs
1. Java bridge stubs under:
   - `<out>/dev/tsj/generated/interop/*.java`
2. Metadata file:
   - `<out>/interop-bridges.properties`

## Diagnostics
1. `TSJ-INTEROP-INPUT`: spec file missing or I/O errors.
2. `TSJ-INTEROP-INVALID`: malformed targets or unresolved class/method.
3. `TSJ-INTEROP-DISALLOWED`: requested target is not allowlisted.
   - `featureId`: `TSJ19-ALLOWLIST`
   - includes allowlist guidance in diagnostic context.
