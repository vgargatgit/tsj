# JVM Annotation Emission

## Current Supported Path

TSJ now preserves JVM-visible annotations through two generic paths:

1. executable strict-native classes authored in TypeScript
2. generic interop bridge classes described by `docs/interop-bridge-spec.md`

The supported user model is:

1. import annotation types from `java:<fully.qualified.Type>`
2. apply them in TS source
3. compile/package through the normal TSJ command surface
4. let Java reflection frameworks inspect the emitted JVM classes directly

## Supported Capabilities

1. Runtime-visible class, field, constructor, method, and parameter annotations on supported strict-native classes.
2. Runtime-visible annotations on generic interop bridge declarations.
3. Supported annotation attribute values:
   - strings
   - booleans
   - numbers
   - arrays
   - enum constants
   - class literals
   - nested object-literal attribute bags used by supported consumers
4. Parameter name metadata (`-parameters`) on emitted JVM classes.
5. Generic signature preservation needed by common reflection consumers and proxy libraries.

## Constraints

1. Annotation types must be runtime-retained and target-compatible.
2. Source must use the TSJ-supported decorator/expression subset.
3. Dynamic decorator construction outside the supported frontend model still fails deterministically.

## Diagnostics

1. `TSJ-DECORATOR-RESOLUTION`: unresolved or invalid `java:` decorator imports.
2. `TSJ-DECORATOR-ATTRIBUTE`: unsupported attribute/value syntax.
3. `TSJ-DECORATOR-PARAM`: unsupported parameter-decorator shape.
4. `TSJ-INTEROP-ANNOTATION`: invalid annotation configuration in generic interop bridge specs.

## Verification Coverage

TSJ regression and certification tests currently prove:

1. annotation visibility on executable strict-native classes
2. annotation attribute fidelity for enum/class-literal values
3. repeatable annotation preservation
4. direct consumption by Spring, Hibernate/JPA, Jackson, Bean Validation, and generic reflection consumers
