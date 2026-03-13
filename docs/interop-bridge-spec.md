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
2. `classAnnotations`: comma-separated fully-qualified annotation types applied to generated bridge classes.
3. `bindingAnnotations.<binding>`: comma-separated annotation types applied to one generated bridge method.
   - Example: `bindingAnnotations.max=java.lang.Deprecated`

Example:

```properties
allowlist=java.lang.Math#max,java.lang.Integer#parseInt
targets=java.lang.Math#max
```

TSJ-29 binding forms are also supported:
1. Constructor: `<class>#$new`
2. Instance method: `<class>#$instance$<method>`
3. Static field get/set:
   - `<class>#$static$get$<field>`
   - `<class>#$static$set$<field>`
4. Instance field get/set:
   - `<class>#$instance$get$<field>`
   - `<class>#$instance$set$<field>`

## Validation Rules
1. Every `targets` entry must exist in `allowlist`.
2. Target class must exist on the classpath.
3. Binding target must exist in the corresponding class:
   - static method binding requires public static method,
   - constructor binding requires public constructor,
   - instance method binding requires public instance method,
   - field bindings require matching public static/instance field.
4. Configured annotation types must resolve on classpath and be Java annotation types.
5. Retired Spring-specific keys are rejected with `TSJ-INTEROP-INVALID`:
   - `springConfiguration`
   - `springBeanTargets`
   - `springWebController`
   - `springWebBasePath`
   - `springRequestMappings.<binding>`
   - `springErrorMappings`
6. For framework integration, prefer TS executable classes with imported `java:` annotations instead of interop-generated framework adapters.

## Generated Outputs
1. Java bridge stubs under:
   - `<out>/dev/tsj/generated/interop/*.java`
2. Metadata file:
   - `<out>/interop-bridges.properties`

## Diagnostics
1. `TSJ-INTEROP-INPUT`: spec file missing or I/O errors.
2. `TSJ-INTEROP-INVALID`: malformed targets or unresolved class/method.
   - also used for invalid TSJ-29 binding prefixes and unresolved constructor/field/member targets.
3. `TSJ-INTEROP-DISALLOWED`: requested target is not allowlisted.
   - `featureId`: `TSJ19-ALLOWLIST`
   - includes allowlist guidance in diagnostic context.
4. `TSJ-INTEROP-ANNOTATION`: invalid annotation configuration.
   - `featureId`: `TSJ32-ANNOTATION-SYNTAX`
   - includes annotation guidance in diagnostic context.
5. `TSJ-INTEROP-INVALID` also covers retired Spring-specific interop bridge keys.
