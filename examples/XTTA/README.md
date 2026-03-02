# XTTA — eXtreme TypeScript Torture App

An extreme torture test that systematically tests TypeScript grammar, JavaScript built-in methods,
and Java interop features to validate TSJ's claim of "full TS grammar support (except TSX)
and interop with any Java JAR."

## Result Summary

**29 out of 30 tests CRASH.** Only `grammar/002_spread` (object/array spread) passes.

```
TOTAL: 30 | PASS: 1 | FAIL: 29 | CRASH: 29
```

## Failure Categories

### 🔴 Parser Failures (`TSJ-BACKEND-PARSE`) — 5 tests

Features that TSJ's parser cannot even parse:

| Test | Feature | Error |
|------|---------|-------|
| `001_destructuring` | `const { a, b } = obj` | "Expected variable name after declaration keyword" |
| `007_template_literals` | `` `hello ${name}` `` | "Unexpected token" |
| `009_control_flow` | Labeled statements, comma operator | "Expected `;` after expression statement" |
| `010_rest_default_computed` | `...args` rest params | "Expected parameter name" |
| `011_getters_setters_misc` | `get name() {}` in object literal | "Expected `:` or `(`" |

### 🔴 Unresolved Built-in Globals (`TSJ-BACKEND-UNSUPPORTED`) — 7 tests

Global identifiers that TSJ does not recognize at all:

| Test | Missing Global |
|------|---------------|
| `006_class_features` | `JSON` |
| `013_async_edge` | Top-level `await` (IIFE) |
| `builtins/004_json` | `JSON` |
| `builtins/005_map_set` | `Map`, `Set` |
| `builtins/006_math_number` | `Infinity` |
| `builtins/008_error_types` | `TypeError`, `RangeError` |
| `builtins/009_coercion` | `NaN` |

### 🔴 Runtime Failures (`TSJ-RUN-006`) — 12 tests

Features that compile but crash at runtime:

| Test | Feature | Error |
|------|---------|-------|
| `003_for_of` | `for (const ch of "abc")` | "Spread target is not iterable: abc" |
| `004_optional_nullish` | `str?.toUpperCase()` | "Cannot get property `toUpperCase` from hello" |
| `005_generators` | `function*`, `.next()` | "Cannot invoke member `next` on null" |
| `008_closures` | Return function from function | "Value is not callable: undefined" |
| `012_type_narrowing` | `typeof x === "string"` → `x.length` | "Cannot get property `length` from hi" |
| `014_enum_namespace` | Enum reverse mapping | "Value is not callable: undefined" |
| `builtins/001_array_methods` | `[].map()`, `[].filter()` | "Value is not callable: undefined" |
| `builtins/002_string_methods` | `.trimStart()`, `.padStart()` | "No compatible instance method" |
| `builtins/003_object_methods` | `Object.keys()` | "Cannot invoke member `keys` on null" |
| `builtins/007_regexp` | `/regex/.test()` | "No compatible instance method `test`" |
| `builtins/010_date` | `Date.now()` | "Cannot invoke member `now` on null" |

### 🔴 Javac Backend Failure — 1 test

| Test | Feature | Error |
|------|---------|-------|
| `015_exceptions` | `catch {}` without variable | "'try' without 'catch', 'finally' or resource declarations" |

### 🔴 Interop Infrastructure Failure — 5 tests

All interop tests fail with `TSJ-INTEROP-INPUT: package dev.tsj.runtime does not exist`.
This prevents testing deep generics, complex overloads, arrays, exceptions, and inheritance through interop.

## Broken Claims Analysis

### "Full TS Grammar Support (except TSX)"

**FALSE.** The following core TypeScript/JavaScript syntax features are not supported:

1. **Destructuring** — Object and array destructuring (`const { a } = obj`, `const [x] = arr`)
2. **Template literal expressions** — `\`hello ${name}\`` fails to parse
3. **Rest parameters** — `function(...args)` fails to parse
4. **Getters/setters in object literals** — `{ get x() {} }` fails to parse
5. **Labeled statements** — `label: for (...)` fails to parse
6. **Comma operator** — `(a, b, c)` fails to parse
7. **Generator functions** — `function*` compiles but `.next()` returns null
8. **Optional chaining** — `obj?.method()` fails at runtime (property access on primitives)
9. **for...of on strings** — `for (const ch of str)` crashes
10. **Higher-order closures** — Functions returning functions return `undefined`
11. **Enum reverse mapping** — `Direction[0]` not functional
12. **Top-level await in expressions** — `await (async () => {})()` not supported

### "Interop with Any Java JAR"

**Untestable.** Bridge compilation fails with missing `dev.tsj.runtime` package,
preventing any actual interop validation beyond the existing TITA scenarios.

### Missing JS Built-in Globals

These standard JavaScript globals are completely absent:

- `JSON` (parse, stringify)
- `Map`, `Set`
- `RegExp` (as object with `.test()`)
- `Date` (constructor, `.now()`)
- `TypeError`, `RangeError` (error subclasses)
- `NaN`, `Infinity` (numeric constants)
- `Number.isInteger`, `Number.isFinite`, `Number.isNaN`
- `parseInt`, `parseFloat`

### Missing Instance Methods

Even on types TSJ does recognize, many standard methods are missing:

- **Array**: `.map()`, `.filter()`, `.reduce()`, `.find()`, `.some()`, `.every()`, `.includes()`,
  `.flat()`, `.forEach()`, `.sort()`, `.slice()`, `.concat()`, `.join()`, `.reverse()`,
  `.push()`, `.pop()`, `.shift()`, `.unshift()`, `.fill()`, `Array.from()`, `Array.isArray()`
- **String**: `.trimStart()`, `.trimEnd()`, `.padStart()`, `.padEnd()`, `.startsWith()`,
  `.endsWith()`, `.repeat()`, `.replaceAll()`, `.at()`, `.match()`, `.search()`
- **Object**: `Object.keys()`, `Object.values()`, `Object.entries()`, `Object.assign()`,
  `Object.freeze()`, `Object.fromEntries()`, `Object.create()`

## What DOES Work

Only **spread operator** (002_spread) passes all 7 checks:
- Array spread: `[...arr]`, `[0, ...arr, 4]`
- Object spread: `{ ...obj }`, `{ ...obj, x: 99 }`
- Function call spread: `fn(...args)`
- Array/object cloning via spread

## How to Run

```bash
# Build Java fixtures (for interop tests)
bash examples/XTTA/scripts/build-fixtures.sh

# Run the full torture suite
bash examples/XTTA/scripts/run.sh
```

## Layout

```text
examples/XTTA/
  README.md
  src/
    grammar/     # 15 TypeScript grammar tests
    builtins/    # 10 JS built-in method tests
    interop/     # 5 Java interop tests
  fixtures-src/  # Java source for interop fixtures
  scripts/
    build-fixtures.sh
    run.sh
```
