package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmBytecodeCompilerTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsLoadableClassForArithmeticAndFunctionCalls() throws Exception {
        final Path sourceFile = tempDir.resolve("main.ts");
        Files.writeString(
                sourceFile,
                """
                function add(a: number, b: number) {
                  return a + b;
                }

                const total = add(2, 3) * 4;
                console.log("total=" + total);
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("out");
        final JvmBytecodeCompiler compiler = new JvmBytecodeCompiler();
        final JvmCompiledArtifact artifact = compiler.compile(sourceFile, outDir);

        assertTrue(Files.exists(artifact.classFile()));

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("total=20\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsIfElseAndWhileControlFlow() throws Exception {
        final Path sourceFile = tempDir.resolve("control-flow.ts");
        Files.writeString(
                sourceFile,
                """
                let sum = 0;
                let i = 1;
                while (i <= 5) {
                  sum = sum + i;
                  i = i + 1;
                }
                if (sum === 15) {
                  console.log("ok " + sum);
                } else {
                  console.log("bad " + sum);
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("build");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, outDir);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("ok 15\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsElseBranchWhenConditionIsFalse() throws Exception {
        final Path sourceFile = tempDir.resolve("else-branch.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 7;
                if (value === 8) {
                  console.log("match");
                } else {
                  console.log("no-match");
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out3"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("no-match\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsReassignmentAndUnaryNegation() throws Exception {
        final Path sourceFile = tempDir.resolve("reassign.ts");
        Files.writeString(
                sourceFile,
                """
                let score = 10;
                score = score + 5;
                const delta = -2;
                if (score + delta > 10) {
                  console.log("high " + score);
                } else {
                  console.log("low " + score);
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out4"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("high 15\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsFunctionCallsInsideExpressions() throws Exception {
        final Path sourceFile = tempDir.resolve("calls.ts");
        Files.writeString(
                sourceFile,
                """
                function inc(value: number) {
                  return value + 1;
                }

                const result = inc(inc(3));
                console.log("result=" + result);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out5"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("result=5\n", stdout.toString(UTF_8));
    }

    @Test
    void generatesFibonacciSequenceValue() throws Exception {
        final Path sourceFile = tempDir.resolve("fibonacci.ts");
        Files.writeString(
                sourceFile,
                """
                function fib(n: number) {
                  if (n <= 1) {
                    return n;
                  }

                  let prev = 0;
                  let curr = 1;
                  let i = 2;
                  while (i <= n) {
                    const next = prev + curr;
                    prev = curr;
                    curr = next;
                    i = i + 1;
                  }
                  return curr;
                }

                console.log("fib(10)=" + fib(10));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out9"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("fib(10)=55\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsFactoryFunctionReturningClosure() throws Exception {
        final Path sourceFile = tempDir.resolve("nested-function.ts");
        Files.writeString(
                sourceFile,
                """
                function makeAdder(base: number) {
                  function add(step: number) {
                    return base + step;
                  }
                  return add;
                }

                const plus3 = makeAdder(3);
                console.log("adder=" + plus3(4));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out6"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("adder=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsCounterClosureWithMutableCapture() throws Exception {
        final Path sourceFile = tempDir.resolve("counter.ts");
        Files.writeString(
                sourceFile,
                """
                function createCounter(seed: number) {
                  let value = seed;
                  function inc() {
                    value = value + 1;
                    return value;
                  }
                  return inc;
                }

                const c1 = createCounter(2);
                console.log("c1=" + c1());
                console.log("c1=" + c1());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out10"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("c1=3\nc1=4\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNestedMultiLevelClosureCapture() throws Exception {
        final Path sourceFile = tempDir.resolve("nested-capture.ts");
        Files.writeString(
                sourceFile,
                """
                function outer(a: number) {
                  let b = a + 1;
                  function middle(c: number) {
                    function inner(d: number) {
                      return b + c + d;
                    }
                    return inner;
                  }
                  return middle;
                }

                const middleFn = outer(2);
                const innerFn = middleFn(3);
                console.log("nested=" + innerFn(4));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out11"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("nested=10\n", stdout.toString(UTF_8));
    }

    @Test
    void keepsMultipleClosureInstancesIsolated() throws Exception {
        final Path sourceFile = tempDir.resolve("closure-isolation.ts");
        Files.writeString(
                sourceFile,
                """
                function createCounter(seed: number) {
                  let value = seed;
                  function inc() {
                    value = value + 1;
                    return value;
                  }
                  return inc;
                }

                const left = createCounter(0);
                const right = createCounter(10);
                console.log("iso=" + left() + "," + left() + "," + right());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out12"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("iso=1,2,11\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsClassConstructorFieldAndMethod() throws Exception {
        final Path sourceFile = tempDir.resolve("class-basic.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  value: number;

                  constructor(seed: number) {
                    this.value = seed;
                  }

                  inc(step: number) {
                    this.value = this.value + step;
                    return this.value;
                  }
                }

                const counter = new Counter(10);
                console.log("class=" + counter.inc(5));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out13"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("class=15\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsInheritanceWithSuperCallAndBaseMethod() throws Exception {
        final Path sourceFile = tempDir.resolve("class-inheritance.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                  read() {
                    return this.value;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed + 1);
                  }
                  doubled() {
                    return this.value * 2;
                  }
                }

                const d = new Derived(4);
                console.log("inherit=" + d.read() + "," + d.doubled());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out14"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("inherit=5,10\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsObjectLiteralPropertyAccessAndAssignment() throws Exception {
        final Path sourceFile = tempDir.resolve("object-literal.ts");
        Files.writeString(
                sourceFile,
                """
                const item = { count: 2, name: "box" };
                item.count = item.count + 3;
                console.log("obj=" + item.name + ":" + item.count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out15"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("obj=box:5\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsInheritedBaseMethodWithoutOverride() throws Exception {
        final Path sourceFile = tempDir.resolve("inherit-base-method.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                  read() {
                    return this.value;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed + 3);
                  }
                }

                const d = new Derived(4);
                console.log("base=" + d.read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out16"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("base=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDerivedMethodOverride() throws Exception {
        final Path sourceFile = tempDir.resolve("inherit-override.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                  read() {
                    return this.value;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed);
                  }
                  read() {
                    return this.value + 100;
                  }
                }

                const d = new Derived(5);
                console.log("override=" + d.read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out17"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("override=105\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsEmptyObjectLiteralAndLatePropertyWrites() throws Exception {
        final Path sourceFile = tempDir.resolve("object-empty.ts");
        Files.writeString(
                sourceFile,
                """
                const payload = {};
                payload.count = 1;
                payload.label = "ok";
                payload.count = payload.count + 2;
                console.log("late=" + payload.label + ":" + payload.count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out18"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("late=ok:3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLooseEqualsCoercionAndStrictEqualsDifference() throws Exception {
        final Path sourceFile = tempDir.resolve("coercion-eq.ts");
        Files.writeString(
                sourceFile,
                """
                const undef = undefined;
                console.log("eq1=" + (1 == "1"));
                console.log("eq2=" + (1 === "1"));
                console.log("eq3=" + (undef == null));
                console.log("eq4=" + (undef === null));
                console.log("eq5=" + (false == 0));
                console.log("eq6=" + (false === 0));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out20"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("eq1=true\neq2=false\neq3=true\neq4=false\neq5=true\neq6=false\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsMissingPropertyReadAsUndefined() throws Exception {
        final Path sourceFile = tempDir.resolve("missing-property.ts");
        Files.writeString(
                sourceFile,
                """
                const payload = { name: "box" };
                console.log("missing=" + payload.count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("missing=undefined\n", stdout.toString(UTF_8));
    }

    @Test
    void generatedSourceUsesMonomorphicPropertyAccessCaches() throws Exception {
        final Path sourceFile = tempDir.resolve("cache-shape.ts");
        Files.writeString(
                sourceFile,
                """
                const item = { count: 1 };
                console.log(item.count);
                console.log(item.count);
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("out22");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, outDir);
        final String simpleName = artifact.className().substring(artifact.className().lastIndexOf('.') + 1);
        final Path generatedSource = outDir.resolve("generated-src/dev/tsj/generated/" + simpleName + ".java");
        final String javaSource = Files.readString(generatedSource, UTF_8);

        assertTrue(javaSource.contains("TsjPropertyAccessCache"));
        assertTrue(javaSource.contains("TsjRuntime.getPropertyCached("));
    }

    @Test
    void supportsPromiseResolveThenChainingWithMicrotaskOrdering() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-chain.ts");
        Files.writeString(
                sourceFile,
                """
                function step(v: number) {
                  console.log("step=" + v);
                  return v + 1;
                }
                function done(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                Promise.resolve(1).then(step).then(done);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nstep=1\ndone=2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncFunctionAwaitAndThenSequencing() throws Exception {
        final Path sourceFile = tempDir.resolve("async-await.ts");
        Files.writeString(
                sourceFile,
                """
                async function compute(seed: number) {
                  console.log("start=" + seed);
                  const next = await Promise.resolve(seed + 1);
                  console.log("after=" + next);
                  return next + 1;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                compute(4).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("start=4\nsync\nafter=5\ndone=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAwaitOnNonPromiseValueViaMicrotaskContinuation() throws Exception {
        final Path sourceFile = tempDir.resolve("async-await-value.ts");
        Files.writeString(
                sourceFile,
                """
                async function readValue() {
                  const value = await 3;
                  console.log("await=" + value);
                  return value;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                readValue().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22d"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nawait=3\ndone=3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncRejectionFlowWithThrowAfterAwait() throws Exception {
        final Path sourceFile = tempDir.resolve("async-reject.ts");
        Files.writeString(
                sourceFile,
                """
                async function failLater() {
                  await Promise.resolve(1);
                  throw "boom";
                }

                function onError(reason: string) {
                  console.log("error=" + reason);
                  return reason;
                }

                failLater().then(undefined, onError);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22e"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nerror=boom\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsAwaitOutsideAsyncFunction() throws Exception {
        final Path sourceFile = tempDir.resolve("await-outside.ts");
        Files.writeString(
                sourceFile,
                """
                function bad() {
                  const value = await Promise.resolve(1);
                  return value;
                }

                console.log(bad());
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22f"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("await"));
    }

    @Test
    void supportsAsyncIfBranchAwaitAndPostBranchContinuation() throws Exception {
        final Path sourceFile = tempDir.resolve("async-if.ts");
        Files.writeString(
                sourceFile,
                """
                async function pick(flag: number) {
                  let value = 0;
                  if (flag === 1) {
                    value = await Promise.resolve(10);
                    console.log("then=" + value);
                  } else {
                    value = await Promise.resolve(20);
                    console.log("else=" + value);
                  }
                  console.log("after=" + value);
                  return value;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                pick(1).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22g"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nthen=10\nafter=10\ndone=10\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsAwaitInAsyncIfConditionForNow() throws Exception {
        final Path sourceFile = tempDir.resolve("async-if-condition-await.ts");
        Files.writeString(
                sourceFile,
                """
                async function bad() {
                  if (await Promise.resolve(true)) {
                    return 1;
                  }
                  return 0;
                }

                bad().then(console.log);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22h"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("condition"));
    }

    @Test
    void supportsAsyncWhileLoopWithAwaitInBodyAndTailContinuation() throws Exception {
        final Path sourceFile = tempDir.resolve("async-while.ts");
        Files.writeString(
                sourceFile,
                """
                async function accumulate(limit: number) {
                  let i = 0;
                  let sum = 0;
                  while (i < limit) {
                    const step = await Promise.resolve(i + 1);
                    sum = sum + step;
                    i = i + 1;
                  }
                  console.log("sum=" + sum);
                  return sum;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                accumulate(3).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22i"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nsum=6\ndone=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNestedAsyncIfWithinAsyncWhile() throws Exception {
        final Path sourceFile = tempDir.resolve("async-while-if.ts");
        Files.writeString(
                sourceFile,
                """
                async function classify(limit: number) {
                  let i = 0;
                  let sum = 0;
                  while (i < limit) {
                    if (i === 1) {
                      const extra = await Promise.resolve(10);
                      sum = sum + extra;
                    } else {
                      const extra = await Promise.resolve(1);
                      sum = sum + extra;
                    }
                    i = i + 1;
                  }
                  return sum;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                classify(3).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22j"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=12\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsAwaitInAsyncWhileConditionForNow() throws Exception {
        final Path sourceFile = tempDir.resolve("async-while-condition-await.ts");
        Files.writeString(
                sourceFile,
                """
                async function bad() {
                  let i = 0;
                  while (await Promise.resolve(i < 1)) {
                    i = i + 1;
                  }
                  return i;
                }

                bad().then(console.log);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("while condition"));
    }

    @Test
    void supportsNamedImportAcrossFilesWithDependencyInitializationOrder() throws Exception {
        final Path mathModule = tempDir.resolve("math.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                mathModule,
                """
                console.log("math-init");
                export function double(n: number) {
                  return n * 2;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { double } from "./math.ts";
                console.log("result=" + double(3));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out23"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("math-init\nresult=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTransitiveNamedImportsAcrossMultipleFiles() throws Exception {
        final Path moduleA = tempDir.resolve("a.ts");
        final Path moduleB = tempDir.resolve("b.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                moduleA,
                """
                export function base(n: number) {
                  return n;
                }
                """,
                UTF_8
        );
        Files.writeString(
                moduleB,
                """
                import { base } from "./a.ts";
                export function plusOne(n: number) {
                  return base(n) + 1;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { plusOne } from "./b.ts";
                console.log("trans=" + plusOne(4));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out24"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("trans=5\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSideEffectImportAcrossFiles() throws Exception {
        final Path setup = tempDir.resolve("setup.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                setup,
                """
                console.log("setup-init");
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import "./setup.ts";
                console.log("ready");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out24b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("setup-init\nready\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLiveBindingBehaviorForImportedMutableValueThroughExportedReaders() throws Exception {
        final Path counterModule = tempDir.resolve("counter.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                counterModule,
                """
                export let count = 0;
                export function inc() {
                  count = count + 1;
                }
                export function read() {
                  return count;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { inc, read } from "./counter.ts";
                console.log("v=" + read());
                inc();
                console.log("v=" + read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out25"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("v=0\nv=1\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsDefaultImportInTsj12Bootstrap() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(module, "export const value = 1;\n", UTF_8);
        Files.writeString(
                entry,
                """
                import value from "./dep.ts";
                console.log(value);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26a"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Unsupported import form"));
    }

    @Test
    void rejectsImportAliasInTsj12Bootstrap() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(module, "export const value = 1;\n", UTF_8);
        Files.writeString(
                entry,
                """
                import { value as v } from "./dep.ts";
                console.log(v);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("aliases"));
    }

    @Test
    void rejectsNonRelativeImportInTsj12Bootstrap() throws Exception {
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                entry,
                """
                import { readFileSync } from "fs";
                console.log(readFileSync);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out27"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("relative imports"));
    }

    @Test
    void rejectsSuperCallOutsideConstructor() throws Exception {
        final Path sourceFile = tempDir.resolve("invalid-super.ts");
        Files.writeString(
                sourceFile,
                """
                function bad() {
                  super(1);
                }

                bad();
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out19"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("super(...)"));
    }

    @Test
    void rejectsUnsupportedForLoopInTsj7() throws Exception {
        final Path sourceFile = tempDir.resolve("for-loop.ts");
        Files.writeString(
                sourceFile,
                """
                for (let i = 0; i < 3; i = i + 1) {
                  console.log(i);
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out7"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Unsupported statement"));
    }

    @Test
    void emitsVerifierSafeClassThatCanBeLoadedReflectively() throws Exception {
        final Path sourceFile = tempDir.resolve("loadable.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 4 * 5;
                console.log("value=" + value);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out8"));
        final Class<?> mainClass = new JvmBytecodeRunner().loadMainClass(artifact);

        assertEquals(artifact.className(), mainClass.getName());
        assertTrue(Files.exists(artifact.classFile()));
    }
}
