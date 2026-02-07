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
