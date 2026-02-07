package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjCliTest {
    @TempDir
    Path tempDir;

    @Test
    void compileCreatesArtifactAndEmitsStructuredSuccess() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(entryFile, "export const answer = 42;\n", UTF_8);
        final Path outDir = tempDir.resolve("build");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outDir.resolve("program.tsj.properties")));
        assertTrue(Files.exists(outDir.resolve("classes")));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-COMPILE-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runCompilesAndExecutesGeneratedArtifact() throws Exception {
        final Path entryFile = tempDir.resolve("entry.ts");
        Files.writeString(entryFile, "console.log('hello');\n", UTF_8);
        final Path outDir = tempDir.resolve("out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outDir.resolve("program.tsj.properties")));
        assertTrue(stdout.toString(UTF_8).contains("hello"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesControlFlowProgram() throws Exception {
        final Path entryFile = tempDir.resolve("control.ts");
        Files.writeString(
                entryFile,
                """
                let i = 1;
                let acc = 0;
                while (i <= 3) {
                  acc = acc + i;
                  i = i + 1;
                }
                if (acc === 6) {
                  console.log("sum=" + acc);
                } else {
                  console.log("bad");
                }
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("cf-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sum=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesClosureProgram() throws Exception {
        final Path entryFile = tempDir.resolve("closure.ts");
        Files.writeString(
                entryFile,
                """
                function makeAdder(base: number) {
                  function add(step: number) {
                    return base + step;
                  }
                  return add;
                }
                const plus2 = makeAdder(2);
                console.log("closure=" + plus2(5));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("closure-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("closure=7"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesClassAndObjectProgram() throws Exception {
        final Path entryFile = tempDir.resolve("class-object.ts");
        Files.writeString(
                entryFile,
                """
                class User {
                  name: string;
                  constructor(name: string) {
                    this.name = name;
                  }
                  tag() {
                    return "@" + this.name;
                  }
                }

                const u = new User("tsj");
                const payload = { label: "ok", count: 2 };
                payload.count = payload.count + 1;
                console.log("model=" + u.tag() + ":" + payload.count);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("class-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("model=@tsj:3"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesInheritanceProgram() throws Exception {
        final Path entryFile = tempDir.resolve("inheritance.ts");
        Files.writeString(
                entryFile,
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
                    super(seed + 2);
                  }
                  doubled() {
                    return this.value * 2;
                  }
                }

                const d = new Derived(4);
                console.log("inherit=" + d.read() + ":" + d.doubled());
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("inherit-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("inherit=6:12"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesCoercionProgram() throws Exception {
        final Path entryFile = tempDir.resolve("coercion.ts");
        Files.writeString(
                entryFile,
                """
                const undef = undefined;
                console.log("coerce=" + (1 == "1") + ":" + (1 === "1"));
                console.log("nullish=" + (undef == null) + ":" + (undef === null));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("coercion-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("coerce=true:false"));
        assertTrue(stdout.toString(UTF_8).contains("nullish=true:false"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runReportsUndefinedForMissingObjectProperty() throws Exception {
        final Path entryFile = tempDir.resolve("missing-property.ts");
        Files.writeString(
                entryFile,
                """
                const payload = { label: "ok" };
                console.log("missing=" + payload.count);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("missing-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("missing=undefined"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesPromiseResolveThenProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise.ts");
        Files.writeString(
                entryFile,
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

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("promise-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("step=1"));
        assertTrue(stdout.toString(UTF_8).contains("done=2"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncAwaitProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async.ts");
        Files.writeString(
                entryFile,
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

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("start=4"));
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("after=5"));
        assertTrue(stdout.toString(UTF_8).contains("done=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncRejectionProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-reject.ts");
        Files.writeString(
                entryFile,
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

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-reject-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("error=boom"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncIfBranchProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-if.ts");
        Files.writeString(
                entryFile,
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

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-if-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("then=10"));
        assertTrue(stdout.toString(UTF_8).contains("after=10"));
        assertTrue(stdout.toString(UTF_8).contains("done=10"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncWhileProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-while.ts");
        Files.writeString(
                entryFile,
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

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-while-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("sum=6"));
        assertTrue(stdout.toString(UTF_8).contains("done=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncArrowProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-arrow.ts");
        Files.writeString(
                entryFile,
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

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-arrow-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("done=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncObjectMethodProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-object-method.ts");
        Files.writeString(
                entryFile,
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

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("async-object-method-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("done=12"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesProgramWithRelativeNamedImports() throws Exception {
        final Path helperFile = tempDir.resolve("helper.ts");
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                helperFile,
                """
                console.log("helper-init");
                export function triple(n: number) {
                  return n * 3;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entryFile,
                """
                import { triple } from "./helper.ts";
                console.log("import=" + triple(4));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("import-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("helper-init"));
        assertTrue(stdout.toString(UTF_8).contains("import=12"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runRejectsImportAliasInTsj12Bootstrap() throws Exception {
        final Path helperFile = tempDir.resolve("helper.ts");
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(helperFile, "export const value = 2;\n", UTF_8);
        Files.writeString(
                entryFile,
                """
                import { value as v } from "./helper.ts";
                console.log(v);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("bad-import-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderr.toString(UTF_8).contains("aliases"));
    }

    @Test
    void runRejectsNonRelativeImportInTsj12Bootstrap() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                entryFile,
                """
                import { readFileSync } from "fs";
                console.log(readFileSync);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("bad-import-out2").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderr.toString(UTF_8).contains("relative imports"));
    }

    @Test
    void compileMissingOutFlagReturnsStructuredError() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(entryFile, "const x = 1;\n", UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-003\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileMissingInputFileReturnsStructuredError() {
        final Path outDir = tempDir.resolve("build");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", tempDir.resolve("missing.ts").toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-COMPILE-001\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileUnsupportedSyntaxReturnsBackendDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("unsupported.ts");
        Files.writeString(
                entryFile,
                """
                for (let i = 0; i < 2; i = i + 1) {
                  console.log(i);
                }
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("bad-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void moduleFingerprintContainsCompilerAndRuntimeModules() {
        assertEquals(
                "compiler-frontend|compiler-ir|compiler-backend-jvm|runtime",
                TsjCli.moduleFingerprint()
        );
    }

    @Test
    void fixturesCommandExecutesFixtureHarness() throws Exception {
        final Path fixturesRoot = tempDir.resolve("fixtures");
        final Path fixtureDir = fixturesRoot.resolve("basic");
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(inputDir.resolve("main.ts"), "console.log('fixture');\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stdout"), "fixture\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "\"code\":\"TSJ-RUN-SUCCESS\"", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);
        Files.writeString(
                fixtureDir.resolve("fixture.properties"),
                String.join(
                        "\n",
                        "name=basic",
                        "entry=input/main.ts",
                        "expected.node.exitCode=0",
                        "expected.node.stdout=expected/node.stdout",
                        "expected.node.stderr=expected/node.stderr",
                        "expected.node.stdoutMode=exact",
                        "expected.node.stderrMode=exact",
                        "expected.tsj.exitCode=0",
                        "expected.tsj.stdout=expected/tsj.stdout",
                        "expected.tsj.stderr=expected/tsj.stderr",
                        "expected.tsj.stdoutMode=contains",
                        "expected.tsj.stderrMode=exact",
                        "assert.nodeMatchesTsj=false",
                        ""
                ),
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"fixtures", fixturesRoot.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-FIXTURE-SUMMARY\""));
        assertEquals("", stderr.toString(UTF_8));
    }
}
