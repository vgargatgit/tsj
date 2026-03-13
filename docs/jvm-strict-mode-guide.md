# JVM-Strict Mode User Guide

Status: `TSJ-78` through `TSJ-84` implemented for current strict-mode roadmap scope.

Purpose:
`jvm-strict` mode is designed for teams that prefer direct JVM class-model output over full JavaScript runtime semantics.

## Command Shape

```bash
tsj compile app/main.ts --out build --mode jvm-strict
tsj run app/main.ts --out build --mode jvm-strict
tsj package app/main.ts --out build --mode jvm-strict
```

Mode values:
1. `default` (existing behavior)
2. `jvm-strict` (strict guardrails enabled)

In this phase, `jvm-strict` enforces deterministic diagnostics for baseline dynamic constructs:
`import(...)`, `eval`, `Function(...)`, `new Proxy(...)`, dynamic computed property writes
(`obj[key] = ...`), `delete`, prototype mutation assignment including `Object|Reflect.setPrototypeOf(...)`,
and unchecked member invocation on `: any` bindings.

For strict top-level classes that fit the initial native subset
(field assignments, return statements, `if/else` branching, direct `this.method(...)` calls),
TSJ emits `strict.loweringPath=jvm-native-class-subset` and dispatches class invocation without `TsjObject` carriers.
Class members outside that subset fail deterministically with `TSJ-STRICT-BRIDGE` (`featureId=TSJ80-STRICT-BRIDGE`).
Strict-native DTO classes now emit JVM-friendly shape for framework boundaries
(no-arg constructor + getters/setters), enabling baseline Jackson serialize/deserialize round-trip.
TS-authored controllers and services can bind typed framework-facing DTO parameters directly to
strict-native classes for the supported named-class subset.
Collection/nullability boundary mapping is available for strict request-body subset shapes:
`T`, `T[]`, `Array<T>`, `Record<string, T>`, and nullable unions (`T | null | undefined`).
`package` also honors strict mode (`--mode jvm-strict`) for packaged runtime flow.
When a strict-native TS class defines `static main(args: string[])`,
TSJ emits a real JVM bridge `main(String[])` and packaged jars use that TS-authored entrypoint directly.

These checks now evaluate the relative import module graph, not only the entry file.
Implementation note:
strict eligibility is enforced through a frontend static-analysis checker (`compiler/frontend`), then surfaced by CLI diagnostics.

## Programming Model

Write TypeScript as a statically-shaped JVM-friendly subset.

### Do

1. Use explicit interfaces/types for DTOs and class fields.
2. Keep object shapes closed after construction.
3. Use typed class methods and constructor injection patterns.
4. Return JVM-serializable shapes at framework boundaries.
5. Use explicit null handling and narrow unions early.

### Avoid

1. Adding/removing properties dynamically (`obj[k] = ...` where `k` is unconstrained, `delete obj.x`).
2. Prototype mutation (`__proto__`, `Object.setPrototypeOf`).
3. `eval`, `Function` constructor, and dynamic code execution.
4. Proxy-driven behavior as a core runtime mechanism.
5. Unchecked `any` member invocation in core domain paths.

## Design Rules for Strict-Eligible Code

1. Prefer classes and typed records over ad-hoc dynamic maps.
2. Keep API payloads deterministic:
   no runtime shape drift between requests.
3. Treat framework boundaries as typed contracts:
   request body type in, typed DTO out.
4. Separate dynamic interop into isolated boundary modules:
   keep main domain/service paths strict-safe.

## Migration Strategy (Default Mode -> JVM-Strict)

1. Inventory dynamic constructs and annotate hotspots.
2. Replace `any` with concrete interfaces/classes in core modules.
3. Refactor dynamic object mutation into typed constructors/factories.
4. Start with TSJ-78 guardrails now; expand CI strict eligibility gates as `TSJ-79` lands.
5. Move modules incrementally to strict mode and track coverage in strict conformance suites.

## Example Pattern

Good strict-style domain model:

```ts
interface Owner {
  id: string;
  firstName: string;
  lastName: string;
}

class OwnerService {
  constructor(private readonly owners: Owner[]) {}

  findByLastName(lastName: string): Owner[] {
    const normalized = lastName.toLowerCase();
    return this.owners.filter((o) => o.lastName.toLowerCase().includes(normalized));
  }
}
```

Non-strict pattern to avoid:

```ts
const owner: any = {};
owner[randomKey()] = "value";
delete owner.lastName;
```

## Expected Outcome

With TSJ-83 complete, `jvm-strict` mode provides deterministic mode selection,
strict diagnostics, JVM-native class lowering for covered strict class shapes,
typed request-body collection/nullability mapping with JVM-friendly method signatures,
and strict conformance/release gate artifacts.
Use `docs/jvm-strict-release-checklist.md` for release signoff.

1. JVM-native class/field/method output for strict-eligible code.
2. Better framework interoperability (Spring/Jackson/JPA style paths).
3. Deterministic compile-time diagnostics when code requires dynamic semantics.
