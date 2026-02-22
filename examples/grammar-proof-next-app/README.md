# Grammar Proof Next App

This app is based on `examples/grammar-proof-app` and exercises features added after that baseline:

1. Array/object/call spread syntax
2. Default parameters
3. Rest parameters
4. `for..of` and `for..in` loops
5. Destructuring inside loop bindings
6. Class grammar features: computed keys, static blocks, field initializers, private fields
7. TS-only constructs in runtime-erased positions: type-only imports/exports, `as const`, `satisfies`, assertion syntax

Run the suite check:

```bash
CP="cli/target/classes:compiler/backend-jvm/target/classes:compiler/ir/target/classes:compiler/frontend/target/classes:runtime/target/classes:$PWD/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar:$PWD/.m2/repository/com/faster
xml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar:$PWD/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2
.17.2.jar"

java -cp "$CP" dev.tsj.cli.TsjCli run examples/grammar-proof-next-app/src/main.ts --out examples/grammar-proof-next-app/.tsj-build
```

Expected stdout:

```text
--- grammar-proof-next ---
spread:1|2|3|4|10|1|2|3|4
params:10|11|0|none|none|3|4|1|9|none|P-x-2|5|2
loops:4|ab|b|10
class:2|5|3|4
ts-only:14|ok
```
