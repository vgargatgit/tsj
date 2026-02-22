# Grammar Proof App

This example intentionally exercises grammar features that were recently added:

1. Multiline named imports
2. Logical operators: `&&`, `||`, `??`
3. Conditional expression: `cond ? a : b`
4. Assignment expressions in expression position
5. Compound assignments: `+=`, `-=`, `*=`, `/=`, `%=`, `&&=`, `||=`, `??=`
6. Optional chaining: `a?.b`, `a?.()`
7. Template literals with interpolation

Run:

```bash
mvn -B -ntp -pl compiler/backend-jvm -Dtest=JvmBytecodeCompilerTest#grammarProofExampleAppCompilesAndRunsWithExpectedOutput test
```

Run directly via CLI classes:

```bash
java -cp "cli/target/classes:compiler/backend-jvm/target/classes:compiler/ir/target/classes:compiler/frontend/target/classes:runtime/target/classes:.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar:.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar:.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar" \
  dev.tsj.cli.TsjCli run examples/grammar-proof-app/src/main.ts --out examples/grammar-proof-app/.tsj-build
```

Expected stdout:

```text
--- grammar-proof ---
logical:false|true|fallback
assign:7|0|filled|alt|next
conditional:LE2
optional:4|undefined|ok|undefined
template:hello tsj #3
```
