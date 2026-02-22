Below is a **torture app spec** whose only job is to validate **full TS grammar coverage** (parsing + AST shaping), while still integrating TSJ’s build flow. It’s designed so a single repo can run a “golden parse” suite and (optionally) a “golden emit” suite.

# TS Grammar Torture App (TGTA) — Spec

## 1) Goal

Create a TSJ project that:

1. Contains **one canonical file per grammar feature** (plus a few mixed “stress” files).
2. Runs a **parser test harness** that asserts:

    * parse succeeds/fails exactly as expected,
    * diagnostics are stable (code + span),
    * AST can be serialized deterministically (for golden snapshots).
3. Optionally runs an **emit/typecheck harness** (if TSJ already has these phases).

Primary target: **TS syntax/grammar**, not runtime semantics.

---

## 2) Repo Layout

```text
tgta/
  README.md
  tsj.json

  src/
    entry.ts              # imports all "ok" suites so build touches them
    harness/
      parse_harness.ts     # drives parse + golden compare
      snapshot.ts          # AST/dx stable serializer
      expect.ts            # minimal assertion utilities

    ok/                    # must parse successfully
      001_lexical.ts
      010_types.ts
      020_expressions.ts
      030_statements.ts
      040_declarations.ts
      050_modules.ts
      060_classes.ts
      070_interfaces.ts
      080_enums.ts
      090_namespaces.ts
      100_generics.ts
      110_jsx.tsx
      120_decorators.ts
      130_ambient_dts.d.ts
      140_ts_5x_features.ts
      900_stress_mix.ts

    err/                   # must fail with specific diagnostics + spans
      e001_unterminated.ts
      e010_bad_type.ts
      e020_bad_import.ts
      e900_recovery_stress.ts

  fixtures/
    expected/
      ok/
        001_lexical.ast.json
        ...
      err/
        e001_unterminated.diag.json
        ...
```

Notes:

* `.tsx` included for JSX grammar.
* `.d.ts` included for ambient/contextual grammar.
* `fixtures/expected` are **golden snapshots** with deterministic ordering.

---

## 3) Harness Requirements

### 3.1 Parse Harness API (internal)

Implement a CLI or library entry (whatever TSJ supports) that exposes:

* `parseFile(path): ParseResult`

    * `ParseResult` includes:

        * `success: boolean`
        * `diagnostics: Diagnostic[]` where each has:

            * `code: string` (or stable numeric code)
            * `message: string` (optional compare; prefer stable code + params)
            * `span: { file, startLine, startCol, endLine, endCol }`
        * `ast: AstNode | null`

* `serializeAst(ast): string`

    * Deterministic JSON: stable key ordering, arrays stable.
    * No volatile fields (node ids, hash codes, memory addresses).
    * Include:

        * node kind
        * token/operator kinds
        * trivia presence (optional, but if you support it, snapshot it)
        * spans (start/end) for a subset or all nodes (choose one consistently).

### 3.2 Golden Rules

* For every `src/ok/*.ts(x)`:

    * parse must succeed
    * snapshot must match `fixtures/expected/ok/<file>.ast.json`
* For every `src/err/*.ts`:

    * parse must fail
    * diagnostics snapshot must match `fixtures/expected/err/<file>.diag.json`
    * recovery must not crash; it may produce partial AST, but diagnostics must match.

### 3.3 Update Mode

Harness supports:

* `--update` to regenerate snapshots
* normal mode fails tests if snapshots differ.

---

## 4) Coverage Matrix (What must be represented)

You want one “feature file” per major grammar area. Each file contains **minimal examples + edge cases**. Below is the required content list.

### 4.1 Lexical & Tokens (`001_lexical.ts`)

Must include:

* unicode identifiers (BMP + astral via escapes if supported)
* numeric separators: `1_000_000`, `0b1010_1010`, `0xCAFE_F00D`
* bigint literals: `123n`
* string forms: single, double, template, tagged template
* regex literal vs division ambiguity (classic pitfalls)
* comments:

    * line, block, nested block (should fail if unsupported), JSDoc
* automatic semicolon insertion traps:

    * `return` newline expression
    * postfix `++` newline
    * `yield` / `await` newline (if supported)

### 4.2 Types (`010_types.ts`)

Must include:

* primitives, `any`, `unknown`, `never`, `void`
* literal types: `"x"`, `42`, `true`
* union/intersection: `A | B`, `A & B`
* tuple + labeled tuple elements + optional tuple elements
* readonly arrays: `readonly T[]`, `ReadonlyArray<T>`
* indexed access: `T[K]`
* `keyof`, `typeof`, `infer`
* conditional types + distributive behavior (grammar-level only)
* mapped types + `as` remapping
* template literal types
* `satisfies` operator usage
* `asserts` in function return types
* type-only imports/exports (`import type`, `export type`)

### 4.3 Expressions (`020_expressions.ts`)

Must include:

* precedence stress: `a + b * c ** d ?? e || f && g`
* optional chaining + nullish coalescing combos: `a?.b?.(c) ?? d`
* non-null assertion: `x!.y`
* `as` casts + angle-bracket casts (should fail in TSX context)
* `await`, `yield`, `yield*` inside generators/async
* `new.target`, `import.meta` (if supported)
* dynamic import expression: `import("x")`
* `in`, `instanceof`, `satisfies`
* destructuring in expressions
* `using` / `await using` (if TS 5.2+ supported)

### 4.4 Statements (`030_statements.ts`)

Must include:

* `if/else`, `switch` (with fallthrough), `for`, `for..of`, `for..in`
* labeled statements + `break label` / `continue label`
* `try/catch/finally` with optional catch binding
* `throw`, `return`, `with` (TS allows in JS target? depends; include either ok or err)
* `debugger`
* `declare` in statement positions where applicable
* `using` statement blocks if supported

### 4.5 Declarations (`040_declarations.ts`)

Must include:

* `var/let/const` with patterns + type annotations
* function decls:

    * overload signatures + implementation
    * default params, rest params, parameter properties style not here
    * `this` parameter
* arrow functions with generic params: `<T>(x: T) => x`
* `declare function`, `declare const`, `declare class`
* `export =` / `import = require()` forms (CommonJS TS forms)
* `satisfies` on declarations

### 4.6 Modules & Imports/Exports (`050_modules.ts`)

Must include:

* `import`, `export` variations:

    * named, default, namespace import
    * re-export `export *`, `export * as ns from`
    * `export { x as y } from`
* import assertions / attributes (depending on TS version):

    * `import data from "./x.json" assert { type: "json" }` or `with { type: "json" }`
* `import type` / `export type`
* `moduleResolution`-like syntax pitfalls not needed; keep grammar

### 4.7 Classes (`060_classes.ts`)

Must include:

* fields:

    * public/private/protected
    * `readonly`, `static`
    * definite assignment `!`
    * accessor fields `get/set`
    * `#private` fields and methods
* constructors:

    * parameter properties (TS feature): `constructor(public x: number)`
* `abstract` class + abstract members
* `override` modifier
* `implements` + `extends`
* `static { }` initialization blocks
* decorators on class/method/field/param (if supported by TSJ)
* class expressions
* `this` type in methods

### 4.8 Interfaces (`070_interfaces.ts`)

Must include:

* call/construct signatures
* index signatures
* interface merging patterns (grammar only)
* `extends` multiple
* `readonly` properties
* optional properties + `?`

### 4.9 Enums (`080_enums.ts`)

Must include:

* numeric enum
* string enum
* heterogenous (if allowed)
* `const enum` (grammar coverage)

### 4.10 Namespaces + `declare module` (`090_namespaces.ts`)

Must include:

* `namespace N { export ... }`
* nested namespaces
* `declare global { }`
* `declare module "pkg" { }`
* `export as namespace` (d.ts style; put in `.d.ts` file too)

### 4.11 Generics & Advanced Signatures (`100_generics.ts`)

Must include:

* generic functions and classes
* default type params: `<T = string>`
* constraints: `<T extends U>`
* `const` type parameters (TS 5.x)
* instantiation expressions: `f<string>`
* variance annotations if supported (TS 5.0 has `in`/`out` in some proposals—if unsupported, keep in err)

### 4.12 JSX/TSX (`110_jsx.tsx`)

Must include:

* intrinsic element `<div />`
* component `<Comp prop={x} />`
* fragments `<>...</>`
* spread props
* generic ambiguity in TSX:

    * `<T>(x: T) => x` should be **error** or moved to non-TSX file
* `as` casts in TSX context
* namespaced JSX `<svg:path />` if supported

### 4.13 Decorators (`120_decorators.ts`)

Two variants:

* If TSJ supports decorators:

    * legacy decorators `@dec` on class, method, accessor, param
    * decorator factories `@dec()`
* If TSJ doesn’t:

    * keep the file in `err/` expecting a specific “decorators not enabled” diagnostic

### 4.14 Ambient Declarations (`130_ambient_dts.d.ts`)

Must include:

* `declare namespace`
* `declare module`
* `declare const` / `declare function`
* `interface` in `.d.ts`
* global augmentation `declare global`
* `export {}` to make it a module

### 4.15 TS 5.x Features (`140_ts_5x_features.ts`)

Include whichever TSJ claims to support; otherwise put unsupported in `err/` with explicit diagnostics:

* `satisfies`
* `using` / `await using`
* `const` type parameters
* `extends` constraint in `infer` patterns
* import attributes syntax variant (`with {}`) depending on version

### 4.16 Mixed Stress File (`900_stress_mix.ts`)

A large file combining:

* nested generics + conditional + mapped + template literal types
* class with private fields, decorators, static block, overloads
* namespaces + module augmentations
* JSX-like tokens avoided unless `.tsx`
  Goal: pressure parser stack/recovery/performance.

---

## 5) Negative (Error) Suite (`src/err`)

Each file must be small and prove a *specific* failure mode:

### e001_unterminated.ts

* unterminated string/template/comment
* expected diag code + span at exact location

### e010_bad_type.ts

* malformed conditional/mapped type syntax
* illegal `infer` placement
* wrong `extends` positions
* verify recovery continues to next statement

### e020_bad_import.ts

* malformed import/export lists
* invalid `export * as` syntax variant
* illegal type-only combinations

### e900_recovery_stress.ts

* intentionally chaotic tokens to ensure:

    * parser doesn’t crash
    * emits bounded number of diagnostics
    * completes within time budget

---

## 6) Determinism Requirements

* Snapshots must be identical across:

    * OS (Windows/Linux)
    * path separators
    * different runs
* Normalize:

    * line endings (treat CRLF as LF)
    * file paths (store repo-relative paths in snapshots)
* Diagnostic ordering is deterministic:

    * primary sort by file, then start offset, then code.

---

## 7) Test Runner Commands

`README.md` must document:

* `tsj test --suite parse`
  Runs parse harness over all `ok/` and `err/`.
* `tsj test --suite parse --update`
  Regenerates snapshots.
* Optional:

    * `tsj test --suite emit` (if TSJ can emit)
    * `tsj test --suite typecheck` (if TSJ has typechecker)

Exit codes:

* 0 on pass
* 1 on snapshot mismatch
* 2 on unexpected parse success/failure
* 3 on harness internal error

---

## 8) What “done” looks like

This torture app is complete when:

* Every grammar file is parsed and snapshotted deterministically.
* Error files fail with stable diagnostic codes/spans.
* Adding a new TS grammar feature requires adding **one file** + **one snapshot**.
* CI can run it with a single command and produce actionable diffs on parser changes.

