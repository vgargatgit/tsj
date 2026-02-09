package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void emitsSourceMapFileForGeneratedProgram() throws Exception {
        final Path sourceFile = tempDir.resolve("source-map.ts");
        Files.writeString(
                sourceFile,
                """
                function fail(value: number) {
                  if (value === 1) {
                    throw "boom";
                  }
                  return value;
                }
                fail(1);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("map-out"));
        final String sourceMap = Files.readString(artifact.sourceMapFile(), UTF_8);

        assertTrue(Files.exists(artifact.sourceMapFile()));
        assertTrue(sourceMap.startsWith("TSJ-SOURCE-MAP\t1"));
        assertTrue(sourceMap.contains("source-map.ts"));
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
    void supportsObjectToPrimitiveCoercionInLooseEquality() throws Exception {
        final Path sourceFile = tempDir.resolve("coercion-object-eq.ts");
        Files.writeString(
                sourceFile,
                """
                const valueOfObject = {
                  valueOf() {
                    return 7;
                  }
                };
                console.log("eq1=" + (valueOfObject == 7));

                const toStringObject = {
                  valueOf() {
                    return {};
                  },
                  toString() {
                    return "8";
                  }
                };
                console.log("eq2=" + (toStringObject == 8));

                const boolObject = {
                  valueOf() {
                    return 1;
                  }
                };
                console.log("eq3=" + (boolObject == true));

                const plain = {};
                console.log("eq4=" + (plain == "[object Object]"));
                console.log("eq5=" + (plain == null));

                class Box {
                  value: number;
                  constructor(value: number) {
                    this.value = value;
                  }
                  valueOf() {
                    return this.value;
                  }
                }
                const box = new Box(9);
                console.log("eq6=" + (box == 9));

                const nonCallableValueOf = {
                  valueOf: 3,
                  toString() {
                    return "11";
                  }
                };
                console.log("eq7=" + (nonCallableValueOf == 11));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out20b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                "eq1=true\neq2=true\neq3=true\neq4=true\neq5=false\neq6=true\neq7=true\n",
                stdout.toString(UTF_8)
        );
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
    void supportsDeleteOperatorForObjectProperties() throws Exception {
        final Path sourceFile = tempDir.resolve("delete-operator.ts");
        Files.writeString(
                sourceFile,
                """
                const proto = { shared: "base" };
                const payload = { own: "x", shared: "local" };
                payload.__proto__ = proto;
                console.log("del1=" + delete payload.own);
                console.log("own=" + payload.own);
                console.log("del2=" + delete payload.shared);
                console.log("shared=" + payload.shared);
                console.log("del3=" + delete payload.missing);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("del1=true\nown=undefined\ndel2=true\nshared=base\ndel3=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPrototypeMutationSyntaxViaObjectSetPrototypeOfAndProtoAssignment() throws Exception {
        final Path sourceFile = tempDir.resolve("prototype-mutation.ts");
        Files.writeString(
                sourceFile,
                """
                const base = { label: "base" };
                const target = {};
                const returned = Object.setPrototypeOf(target, base);
                console.log("ret=" + (returned === target));
                console.log("label=" + target.label);

                const alt = { label: "alt" };
                target.__proto__ = alt;
                console.log("label2=" + target.label);

                const cleared = Object.setPrototypeOf(target, null);
                console.log("cleared=" + (cleared === target));
                console.log("label3=" + target.label);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("ret=true\nlabel=base\nlabel2=alt\ncleared=true\nlabel3=undefined\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsDeleteOnUnsupportedOperandInTsj21Subset() throws Exception {
        final Path sourceFile = tempDir.resolve("delete-unsupported.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 1;
                console.log(delete value);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21d"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("delete"));
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
    void foldsConstantArithmeticExpressionsWhenOptimizationsAreEnabled() throws Exception {
        final Path sourceFile = tempDir.resolve("constant-folding.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 1 + 2 * 3;
                console.log("value=" + value);
                """,
                UTF_8
        );

        final Path optimizedOut = tempDir.resolve("opt-on");
        final JvmCompiledArtifact optimizedArtifact = new JvmBytecodeCompiler().compile(sourceFile, optimizedOut);
        final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);

        final Path baselineOut = tempDir.resolve("opt-off");
        final JvmCompiledArtifact baselineArtifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                baselineOut,
                JvmOptimizationOptions.disabled()
        );
        final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);

        assertTrue(optimizedSource.contains("Integer.valueOf(7)"));
        assertFalse(optimizedSource.contains("TsjRuntime.multiply("));
        assertTrue(baselineSource.contains("TsjRuntime.multiply("));
    }

    @Test
    void eliminatesUnreachableStatementsAfterReturnWhenDceIsEnabled() throws Exception {
        final Path sourceFile = tempDir.resolve("dead-after-return.ts");
        Files.writeString(
                sourceFile,
                """
                function pick() {
                  return 1;
                  console.log("dead-branch");
                }
                console.log("value=" + pick());
                """,
                UTF_8
        );

        final Path optimizedOut = tempDir.resolve("dce-on");
        final JvmCompiledArtifact optimizedArtifact = new JvmBytecodeCompiler().compile(sourceFile, optimizedOut);
        final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);
        assertFalse(optimizedSource.contains("dead-branch"));

        final Path baselineOut = tempDir.resolve("dce-off");
        final JvmCompiledArtifact baselineArtifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                baselineOut,
                JvmOptimizationOptions.disabled()
        );
        final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);
        assertFalse(baselineSource.contains("dead-branch"));
    }

    @Test
    void removesWhileFalseLoopWhenDceIsEnabled() throws Exception {
        final Path sourceFile = tempDir.resolve("while-false.ts");
        Files.writeString(
                sourceFile,
                """
                let value = 1;
                while (false) {
                  value = value + 1;
                }
                console.log("value=" + value);
                """,
                UTF_8
        );

        final Path optimizedOut = tempDir.resolve("while-dce-on");
        final JvmCompiledArtifact optimizedArtifact = new JvmBytecodeCompiler().compile(sourceFile, optimizedOut);
        final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);

        final Path baselineOut = tempDir.resolve("while-dce-off");
        final JvmCompiledArtifact baselineArtifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                baselineOut,
                JvmOptimizationOptions.disabled()
        );
        final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);

        assertFalse(optimizedSource.contains("while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.FALSE))"));
        assertTrue(baselineSource.contains("while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.FALSE))"));
    }

    @Test
    void optimizationBenchmarkShowsGeneratedSourceReductionAcrossFixtureSet() throws Exception {
        final Path fixturesRoot = tempDir.resolve("tsj17-fixtures");
        Files.createDirectories(fixturesRoot);

        final Path fixtureOne = fixturesRoot.resolve("fixture-one.ts");
        Files.writeString(
                fixtureOne,
                """
                function score() {
                  const base = 1 + 2 + 3 + 4;
                  if (false) {
                    console.log("dead-one");
                  }
                  return base;
                }
                console.log("score=" + score());
                """,
                UTF_8
        );

        final Path fixtureTwo = fixturesRoot.resolve("fixture-two.ts");
        Files.writeString(
                fixtureTwo,
                """
                let acc = 0;
                while (false) {
                  acc = acc + 100;
                }
                if (true) {
                  acc = acc + (5 * 6);
                } else {
                  acc = acc + 999;
                }
                console.log("acc=" + acc);
                """,
                UTF_8
        );

        final Path fixtureThree = fixturesRoot.resolve("fixture-three.ts");
        Files.writeString(
                fixtureThree,
                """
                async function run() {
                  const left = await Promise.resolve(2 + 3);
                  if (false) {
                    console.log("dead-async");
                  }
                  return left + (10 - 7);
                }
                function onDone(value: number) {
                  console.log("value=" + value);
                  return value;
                }
                run().then(onDone);
                """,
                UTF_8
        );

        final JvmBytecodeCompiler compiler = new JvmBytecodeCompiler();
        int optimizedBytes = 0;
        int baselineBytes = 0;
        int optimizedOps = 0;
        int baselineOps = 0;
        for (Path fixture : new Path[]{fixtureOne, fixtureTwo, fixtureThree}) {
            final Path optimizedOut = tempDir.resolve("bench-opt-" + fixture.getFileName().toString());
            final JvmCompiledArtifact optimizedArtifact = compiler.compile(fixture, optimizedOut);
            final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);
            optimizedBytes += optimizedSource.getBytes(UTF_8).length;
            optimizedOps += runtimeOperationCount(optimizedSource);

            final Path baselineOut = tempDir.resolve("bench-base-" + fixture.getFileName().toString());
            final JvmCompiledArtifact baselineArtifact = compiler.compile(
                    fixture,
                    baselineOut,
                    JvmOptimizationOptions.disabled()
            );
            final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);
            baselineBytes += baselineSource.getBytes(UTF_8).length;
            baselineOps += runtimeOperationCount(baselineSource);
        }

        assertTrue(optimizedBytes < baselineBytes);
        assertTrue(optimizedOps < baselineOps);
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
    void supportsThenableAssimilationAndFirstSettleWins() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-thenable.ts");
        Files.writeString(
                sourceFile,
                """
                const thenable = {
                  then(resolve: any, reject: any) {
                    resolve(41);
                    reject("bad");
                    resolve(99);
                  }
                };

                Promise.resolve(thenable)
                  .then((value: number) => {
                    console.log("value=" + value);
                    return value + 1;
                  })
                  .then((value: number) => {
                    console.log("next=" + value);
                    return value;
                  });
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b2"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nvalue=41\nnext=42\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsThenableRejectBeforeSettlementAsRejection() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-thenable-reject.ts");
        Files.writeString(
                sourceFile,
                """
                const badThenable = {
                  then(resolve: any, reject: any) {
                    reject("boom");
                    resolve(99);
                  }
                };

                Promise.resolve(badThenable).then(
                  undefined,
                  (reason: string) => {
                    console.log("error=" + reason);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b3"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nerror=boom\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseCatchAndFinallyPassThrough() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-catch-finally.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.reject("boom")
                  .catch((reason: string) => {
                    console.log("catch=" + reason);
                    return 7;
                  })
                  .finally(() => {
                    console.log("finally");
                    return 999;
                  })
                  .then((value: number) => {
                    console.log("value=" + value);
                    return value;
                  });
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b4"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ncatch=boom\nfinally\nvalue=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseFinallyRejectionOverride() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-finally-reject.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.resolve(1)
                  .finally(() => Promise.reject("fin"))
                  .then(
                    (value: number) => {
                      console.log("value=" + value);
                      return value;
                    },
                    (reason: string) => {
                      console.log("error=" + reason);
                      return reason;
                    }
                  );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b5"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nerror=fin\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseAllAndRaceCombinators() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-all-race.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.all([Promise.resolve(1), Promise.resolve(2), 3]).then((values: any) => {
                  console.log("all=" + values.length);
                  return values;
                });

                Promise.race([Promise.resolve("win"), Promise.reject("lose")]).then(
                  (value: string) => {
                    console.log("race=" + value);
                    return value;
                  },
                  (reason: string) => {
                    console.log("race-err=" + reason);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b6"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nall=3\nrace=win\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseAllSettledAndAnyCombinators() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-allsettled-any.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.allSettled([Promise.resolve(1), Promise.reject("x")]).then((entries: any) => {
                  console.log("settled=" + entries.length);
                  return entries;
                });

                Promise.any([Promise.reject("a"), Promise.resolve("ok")]).then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("any-err=" + reason.name);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b7"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nsettled=2\nany=ok\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseAnyAggregateErrorWhenAllReject() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-any-reject.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.any([Promise.reject("a"), Promise.reject("b")]).then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("anyErr=" + reason.name + ":" + reason.errors.length);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b8"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nanyErr=AggregateError:2\n", stdout.toString(UTF_8));
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
    void supportsAwaitInAsyncIfCondition() throws Exception {
        final Path sourceFile = tempDir.resolve("async-if-condition-await.ts");
        Files.writeString(
                sourceFile,
                """
                async function choose() {
                  if (await Promise.resolve(true)) {
                    return 1;
                  }
                  return 0;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                choose().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22h"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=1\n", stdout.toString(UTF_8));
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
    void supportsAsyncFunctionExpressionWithAwaitInBinaryExpression() throws Exception {
        final Path sourceFile = tempDir.resolve("async-fn-expression.ts");
        Files.writeString(
                sourceFile,
                """
                const compute = async function(seed: number) {
                  return (await Promise.resolve(seed + 1)) + 2;
                };

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                compute(4).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22l"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncArrowFunctionWithAwaitExpressionBody() throws Exception {
        final Path sourceFile = tempDir.resolve("async-arrow.ts");
        Files.writeString(
                sourceFile,
                """
                const inc = async (value: number) => (await Promise.resolve(value)) + 1;

                function onDone(result: number) {
                  console.log("done=" + result);
                  return result;
                }

                inc(5).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22m"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncClassMethodWithAwaitInReturnExpression() throws Exception {
        final Path sourceFile = tempDir.resolve("async-class-method.ts");
        Files.writeString(
                sourceFile,
                """
                class Worker {
                  async compute(seed: number) {
                    return (await Promise.resolve(seed + 1)) * 2;
                  }
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                const worker = new Worker();
                worker.compute(3).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22n"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=8\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncObjectMethodWithAwaitInInitializerAndReturn() throws Exception {
        final Path sourceFile = tempDir.resolve("async-object-method.ts");
        Files.writeString(
                sourceFile,
                """
                const ops = {
                  async compute(seed: number) {
                    const value = await Promise.resolve(seed + 2);
                    return value * 3;
                  }
                };

                function onDone(result: number) {
                  console.log("done=" + result);
                  return result;
                }

                ops.compute(2).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=12\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAwaitInsideCallArgumentsAndObjectLiteralValues() throws Exception {
        final Path sourceFile = tempDir.resolve("await-expression-positions.ts");
        Files.writeString(
                sourceFile,
                """
                function add(a: number, b: number) {
                  return a + b;
                }

                async function run() {
                  const payload = {
                    left: await Promise.resolve(2),
                    right: await Promise.resolve(3)
                  };
                  const total = add(await Promise.resolve(payload.left), await Promise.resolve(payload.right));
                  console.log("total=" + total);
                  return total;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22p"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ntotal=5\ndone=5\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsAwaitInsideNonAsyncArrowFunction() throws Exception {
        final Path sourceFile = tempDir.resolve("await-non-async-arrow.ts");
        Files.writeString(
                sourceFile,
                """
                const read = (seed: number) => await Promise.resolve(seed + 1);
                console.log(read(1));
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22q"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("await"));
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
    void supportsTopLevelAwaitInSingleFileProgram() throws Exception {
        final Path sourceFile = tempDir.resolve("tla-single.ts");
        Files.writeString(
                sourceFile,
                """
                console.log("before");
                const value = await Promise.resolve(1);
                console.log("after=" + value);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out25a"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("before\nafter=1\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTopLevelAwaitAcrossImportedDependencyInitializationOrder() throws Exception {
        final Path dep = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                dep,
                """
                export let status = "init";
                status = await Promise.resolve("ready");
                console.log("dep=" + status);
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { status } from "./dep.ts";
                console.log("main=" + status);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out25b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("dep=ready\nmain=ready\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTopLevelAwaitAcrossTransitiveModuleDependencies() throws Exception {
        final Path moduleA = tempDir.resolve("a.ts");
        final Path moduleB = tempDir.resolve("b.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                moduleA,
                """
                export let value = 0;
                value = await Promise.resolve(5);
                console.log("a=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                moduleB,
                """
                import { value } from "./a.ts";
                export function read() {
                  return value;
                }
                console.log("b=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { read } from "./b.ts";
                console.log("main=" + read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out25c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("a=5\nb=5\nmain=5\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsTopLevelAwaitInWhileConditionForNow() throws Exception {
        final Path sourceFile = tempDir.resolve("tla-while-condition.ts");
        Files.writeString(
                sourceFile,
                """
                let i = 0;
                while (await Promise.resolve(i < 1)) {
                  i = i + 1;
                }
                console.log(i);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out25d"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("while condition"));
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
    void rejectsDynamicImportWithFeatureDiagnosticMetadata() throws Exception {
        final Path sourceFile = tempDir.resolve("dynamic-import.ts");
        Files.writeString(
                sourceFile,
                """
                const loader = import("./dep.ts");
                console.log(loader);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out28"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-DYNAMIC-IMPORT",
                "Use static relative imports"
        );
        assertEquals(1, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsEvalWithFeatureDiagnosticMetadata() throws Exception {
        final Path sourceFile = tempDir.resolve("eval.ts");
        Files.writeString(
                sourceFile,
                """
                const value = eval("1 + 2");
                console.log(value);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out29"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-EVAL",
                "runtime code evaluation"
        );
        assertEquals(1, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsFunctionConstructorWithFeatureDiagnosticMetadata() throws Exception {
        final Path sourceFile = tempDir.resolve("function-constructor.ts");
        Files.writeString(
                sourceFile,
                """
                const factory = new Function("return 7;");
                console.log(factory());
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out30"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-FUNCTION-CONSTRUCTOR",
                "runtime code evaluation"
        );
        assertEquals(1, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsProxyConstructorWithFeatureDiagnosticMetadata() throws Exception {
        final Path sourceFile = tempDir.resolve("proxy.ts");
        Files.writeString(
                sourceFile,
                """
                const target = { value: 1 };
                const proxy = new Proxy(target, {});
                console.log(proxy.value);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out31"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-PROXY",
                "Proxy semantics are outside MVP"
        );
        assertEquals(2, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void reportsUnsupportedFeatureLocationFromImportedModule() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(module, "const v = eval(\"2 + 3\");\nconsole.log(v);\n", UTF_8);
        Files.writeString(entry, "import \"./dep.ts\";\nconsole.log(\"main\");\n", UTF_8);

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out32"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-EVAL",
                "runtime code evaluation"
        );
        assertEquals(1, exception.line());
        assertEquals(module.toAbsolutePath().normalize().toString(), exception.sourceFile());
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

    private static void assertUnsupportedFeature(
            final JvmCompilationException exception,
            final String featureId,
            final String guidanceSnippet
    ) {
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertEquals(featureId, exception.featureId());
        assertTrue(exception.getMessage().contains(featureId));
        assertTrue(exception.getMessage().contains(guidanceSnippet));
        assertTrue(exception.guidance() != null && exception.guidance().contains(guidanceSnippet));
        assertTrue(exception.sourceFile() != null && !exception.sourceFile().isBlank());
    }

    private static String generatedJavaSource(final Path outDir, final JvmCompiledArtifact artifact) throws Exception {
        final String simpleName = artifact.className().substring(artifact.className().lastIndexOf('.') + 1);
        final Path generatedSource = outDir.resolve("generated-src/dev/tsj/generated/" + simpleName + ".java");
        return Files.readString(generatedSource, UTF_8);
    }

    private static int runtimeOperationCount(final String javaSource) {
        final String marker = "dev.tsj.runtime.TsjRuntime.";
        int count = 0;
        int index = javaSource.indexOf(marker);
        while (index >= 0) {
            count++;
            index = javaSource.indexOf(marker, index + marker.length());
        }
        return count;
    }
}
