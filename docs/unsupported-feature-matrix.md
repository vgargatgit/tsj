# TSJ Unsupported Feature Matrix (MVP v0.1)

This matrix defines non-goal language/runtime features that must fail fast at compile time under TSJ-15.

| Feature ID | Non-goal feature | Detection scope | Diagnostic code | Guidance summary |
|---|---|---|---|---|
| `TSJ15-DYNAMIC-IMPORT` | `import("...")` dynamic module loading | parser expression analysis | `TSJ-BACKEND-UNSUPPORTED` | Use static relative imports in the TSJ-12 bootstrap subset. |
| `TSJ15-EVAL` | `eval(...)` runtime evaluation | parser call-expression analysis | `TSJ-BACKEND-UNSUPPORTED` | Replace runtime code evaluation with explicit functions/modules. |
| `TSJ15-FUNCTION-CONSTRUCTOR` | `Function(...)` / `new Function(...)` | parser call/new-expression analysis | `TSJ-BACKEND-UNSUPPORTED` | Replace runtime code evaluation with explicit functions/modules. |
| `TSJ15-PROXY` | `new Proxy(...)` metaprogramming proxy semantics | parser new-expression analysis | `TSJ-BACKEND-UNSUPPORTED` | Proxy semantics are outside MVP; use explicit wrappers. |

Required diagnostic context fields for TSJ-15 failures:
1. `file`
2. `line`
3. `column`
4. `featureId`
5. `guidance`

Coverage references:
1. `compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompilerTest.java`
2. `cli/src/test/java/dev/tsj/cli/TsjCliTest.java`
