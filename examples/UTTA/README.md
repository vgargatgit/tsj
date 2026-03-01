# UTTA — Ultimate TypeScript Torture App

Stress-tests TSJ's claims of "full TS grammar (except TSX)" and "any-JAR interop" by
targeting features with **zero existing test coverage**: Symbol, BigInt, Proxy/Reflect,
WeakMap/WeakRef, decorators, bitwise operators, async generators, Java enums, records,
sealed classes, CompletableFuture bridging, builder patterns, and stress scenarios.

## Results Summary

```
TOTAL: 30 | PASS: 11 | FAIL: 19 | CRASH: 17
```

| Category | Tests | Pass | Fail |
|----------|-------|------|------|
| Grammar  | 15    | 3    | 12   |
| Interop  | 10    | 5    | 5    |
| Stress   | 5     | 3    | 2    |

---

## Failure Breakdown

### 🔴 Parser Failures (4 tests) — `TSJ-BACKEND-PARSE`

| Test | Feature | Error |
|------|---------|-------|
| `005_bitwise` | Compound bitwise assign (`&=`, `\|=`, `^=`, `<<=`, `>>=`) | `Expected ; after expression statement` |
| `009_complex_chaining` | Optional element access (`?.[n]`) | `Expected property name after ?.` |
| `015_misc_edge` | `void` operator, comma operator `(1,2,3)` | `Expected ) after grouped expression` |
| `stress/004_prototype_chains` | Generic constraints with rest params in type position (`new (...args: any[]) => any`) | `Expected ) after grouped expression` |

**Root Cause:** The parser (both the legacy tokenizer and the normalized AST lowering) doesn't handle these syntax patterns. They are valid TypeScript but not in TSJ's parser grammar.

### 🟠 Missing Globals / Unsupported Features (7 tests) — `TSJ-BACKEND-UNSUPPORTED`

| Test | Missing Global/Feature | Error |
|------|----------------------|-------|
| `001_for_await_of` | `async function*` (async generators) | `Async generator functions are unsupported in TSJ-13b subset` |
| `002_symbol` | `Symbol()` + computed property `obj[key]` as assignment target | `Unsupported assignment target: CallExpression` |
| `006_proxy_reflect` | `Proxy`, `Reflect` | (empty message — immediate crash) |
| `007_weak_refs` | `WeakMap`, `WeakSet`, `WeakRef` | `Unresolved identifier: WeakMap` |
| `008_error_cause` | `AggregateError` | `Unresolved identifier: AggregateError` |
| `011_decorators` | `@decorator` syntax + `Object.seal()` + `Object.defineProperty()` | `Unsupported assignment target: CallExpression` |
| `013_module_patterns` | `forEach` callback with multiple params | `Unsupported assignment target: CallExpression` |

### 🟡 Runtime Failures (2 tests) — `TSJ-RUN-006`

| Test | Feature | Error |
|------|---------|-------|
| `003_bigint` | `BigInt()` constructor + `2n ** 10n` | `Value is not callable: null` |
| `012_deep_nesting` | Nested spread `[...[...a, 3], ...[4, ...a]]` | 7/8 pass; `nested_spread` fails |

### 🟡 Behavioral Bugs (1 test) — Logic errors at runtime

| Test | Bug | Details |
|------|-----|---------|
| `stress/005_realistic_app` | `this.repo.findById()` returns `undefined` inside nested class method | Service class calling method on injected dependency via `this.repo` fails — the method call on a stored object reference loses context when called from another class method. 5/10 checks pass. |

### 🔴 Interop: Enum/Constant Binding Failures (5 tests) — `TSJ-INTEROP-INVALID` / `TSJ-RUN-006`

| Test | Feature | Error |
|------|---------|-------|
| `001_java_enums` | Java enum `valueOf()` returns `String` not enum instance | `Instance receiver type mismatch for Color#getHex: got String` |
| `004_functional` | `Optional.isPresent()` on return value from static method | `Instance receiver type mismatch for Optional#isPresent: got Integer` |
| `006_nested_classes` | `static final` field access (`Outer.VERSION`) | `Interop target method not found or not static: Outer#VERSION` |
| `009_static_constants` | Same — `static final` fields | `Interop target not found: Outer#MAX_SIZE` |
| `010_cross_class` | Enum `.name()` on `valueOf()` result | `Instance receiver type mismatch for Color#name: got String` |

**Root Causes:**
1. **Enum interop:** `valueOf("RED")` returns the string `"RED"` instead of the enum constant object. The codec unwraps the enum to its string representation, making instance method calls fail.
2. **Static final fields:** TSJ's interop bridge only discovers static methods, not static fields. `Outer.VERSION` (a `static final String`) can't be imported.
3. **Optional unwrap:** `safeDiv()` returns `Optional<Integer>`, but TSJ's codec unwraps the `Optional` to its contained `Integer`, so `isPresent()` is called on `Integer` instead of `Optional`.

---

## What Passes ✅

| Test | Features Covered | Checks |
|------|-----------------|--------|
| `004_numeric_exotic` | Numeric separators (`1_000_000`), hex/bin/oct literals, exponents | 10 |
| `010_logical_assign` | `??=`, `\|\|=`, `&&=` on variables and object properties | 11 |
| `014_type_guards` | `typeof`, `instanceof`, user-defined type guards, discriminated unions, assertion fns | 12 |
| `002_java_records` | Java records: construction, accessor methods, instance methods, static factory | 6 |
| `003_sealed_classes` | Java sealed class hierarchy: Circle, Rectangle, interface method dispatch | 6 |
| `005_completable_future` | `CompletableFuture` → awaitable Promise bridging (success + failure) | 3 |
| `007_builder` | Java builder pattern: step-by-step mutation, `build()` | 4 |
| `008_exceptions` | Java exception hierarchies: catch, dispatch, multiple exception types | 6 |
| `001_many_functions` | 100 function definitions, array of functions, map/reduce | 3 |
| `002_deep_recursion` | 1000-level recursion, fibonacci, mutual recursion, tree building | 6 |
| `003_large_arrays` | 10K array creation, filter/map/reduce, matrix, object arrays | 8 |

---

## Severity Classification

### Critical (blocks real-world usage)
- **No `static final` field access** — can't read Java constants like `Integer.MAX_VALUE`
- **Enum unwrapping** — enum interop is broken; `valueOf()` returns string not enum
- **Optional/wrapper unwrapping** — return values auto-unwrapped, can't call methods on them
- **Compound bitwise assignment** — `x &= mask` is fundamental systems code
- **Nested class `this` context bug** — service/repository patterns break

### High (significant gaps)
- **No Symbol support** — can't implement iterators, custom protocols
- **No Proxy/Reflect** — can't build dynamic APIs, ORMs, or validation layers
- **No WeakMap/WeakSet/WeakRef** — can't manage memory-sensitive caches
- **No BigInt** — can't handle large integers (crypto, finance)
- **No async generators / for-await-of** — can't stream async data

### Medium (noticeable gaps)
- **No `void` operator** — uncommon but valid TS
- **No `?.[n]` optional element access** — common in array-heavy code
- **No AggregateError** — needed for `Promise.any()` error handling
- **No decorators** — needed for frameworks (NestJS, TypeORM)

---

## Running

```bash
# Build Java fixtures
bash scripts/build-fixtures.sh

# Run all tests
bash scripts/run.sh
```

## Structure

```
src/
  grammar/   — 15 tests: for-await-of, Symbol, BigInt, numerics, bitwise, Proxy,
                WeakMap, Error.cause, optional chaining, logical assign, decorators,
                deep nesting, module patterns, type guards, misc edge cases
  interop/   — 10 tests: enums, records, sealed classes, functional interfaces,
                CompletableFuture, nested classes, builder, exceptions, constants,
                cross-class integration
  stress/    — 5 tests: 100 functions, deep recursion, large arrays, prototype
                chains, realistic multi-class app
fixtures-src/  — Java classes: Color enum, Point record, Shape sealed hierarchy,
                FuncLib (streams/Optional), AsyncLib (CompletableFuture), Outer
                (nested classes), QueryBuilder (builder), AppException (hierarchy)
```
