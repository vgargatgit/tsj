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
4. `springConfiguration`: boolean (`true`/`false`), default `false`.
   - When `true`, generated bridge classes are annotated with
     `@org.springframework.context.annotation.Configuration`.
5. `springBeanTargets`: comma-separated `<class>#<binding>` entries.
   - Requires `springConfiguration=true`.
   - For listed targets, generated bridge methods are annotated with
     `@org.springframework.context.annotation.Bean`
     and emitted with typed method signatures for DI wiring.
6. `springWebController`: boolean (`true`/`false`), default `false`.
   - When `true`, generated bridge classes are annotated with
     `@org.springframework.web.bind.annotation.RestController`.
7. `springWebBasePath`: optional base path (must start with `/`) applied via class-level `@RequestMapping`.
8. `springRequestMappings.<binding>`: request mapping declaration for one binding.
   - value format: `<HTTP_METHOD> <path>`, example:
     `springRequestMappings.findUser=GET /find`
9. `springErrorMappings`: comma-separated exception/status mappings for controller handlers.
   - format: `<exceptionFqcn>:<statusCode>`, example:
     `springErrorMappings=java.lang.IllegalArgumentException:400`

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
5. `springBeanTargets` entries must also appear in `targets`.
6. Spring bean targets support this subset:
   - constructor binding (`$new`) with exactly one public constructor and non-primitive parameter types,
   - static method binding with an unambiguous public static method, non-void non-primitive return type,
     and non-primitive parameter types.
   - parameterized generic signatures are preserved for concrete generic types.
   - signatures containing unresolved generic type variables are rejected with metadata diagnostics.
   - instance method and field bindings are not valid Spring bean targets in this subset.
7. Spring web mappings require `springWebController=true`.
8. Spring web target bindings must also appear in `targets`.
9. Spring web targets support this subset:
   - constructor binding (`$new`) with exactly one public constructor,
   - unambiguous static method bindings (`<class>#<method>`),
   - instance/field bindings are rejected for web routes.
10. `springErrorMappings` exception types must resolve and be throwable types.
11. `springErrorMappings` status codes are limited to:
   `400, 401, 403, 404, 409, 422, 500`.
12. Typed bridge metadata subset rejects signatures containing unresolved type variables.

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
5. `TSJ-INTEROP-SPRING`: invalid Spring bridge configuration/target shape.
   - `featureId`: `TSJ33-SPRING-BEAN`
   - includes Spring bean-target guidance in diagnostic context.
6. `TSJ-INTEROP-WEB`: invalid Spring web bridge configuration/target shape.
   - `featureId`: `TSJ34-SPRING-WEB`
   - includes Spring web-target guidance in diagnostic context.
7. `TSJ-INTEROP-METADATA`: unsupported bridge metadata signature shape.
   - `featureId`: `TSJ39-ABI-METADATA`
   - emitted when target signatures include unresolved generic type variables.
