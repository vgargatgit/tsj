package dev.tsj.cli.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureHarnessTest {
    @TempDir
    Path tempDir;

    @Test
    void loaderReadsFixtureWithExpectedResultModes() throws Exception {
        final Path fixtureDir = writeFixture("hello", false);

        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        assertEquals("hello", fixture.name());
        assertEquals("main.ts", fixture.entryFile().getFileName().toString());
        assertEquals(MatchMode.EXACT, fixture.nodeExpectation().stdoutMode());
        assertEquals(MatchMode.CONTAINS, fixture.tsjExpectation().stdoutMode());
        assertFalse(fixture.assertNodeMatchesTsj());
    }

    @Test
    void harnessRunsNodeAndTsjAndMatchesExpectedOutput() throws Exception {
        final Path fixtureDir = writeFixture("hello", false);
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeResult().matchedExpectation());
        assertTrue(result.tsjResult().matchedExpectation());
        assertFalse(result.nodeToTsjMatched());
    }

    @Test
    void harnessCanEnforceNodeToTsjComparison() throws Exception {
        final Path fixtureDir = writeFixture("strict", true);
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
    }

    @Test
    void harnessFlagsNodeToTsjMismatchWhenExecutionDiffers() throws Exception {
        final Path fixtureDir = writeUnsupportedFixture("strict-mismatch");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertFalse(result.passed());
        assertFalse(result.nodeToTsjMatched());
        assertTrue(result.nodeToTsjDiff().contains("exit code differs"));
    }

    @Test
    void harnessRunsAllFixturesFromRoot() throws Exception {
        writeFixture("first", false);
        writeFixture("second", false);

        final List<FixtureRunResult> results = new FixtureHarness().runAll(tempDir);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(FixtureRunResult::passed));
    }

    @Test
    void harnessSupportsClosureComparisonFixture() throws Exception {
        final Path fixtureDir = writeClosureFixture("closure");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsClassAndInheritanceFixture() throws Exception {
        final Path fixtureDir = writeClassFixture("class-inheritance");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsObjectLiteralFixture() throws Exception {
        final Path fixtureDir = writeObjectFixture("object-literal");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsCoercionFixture() throws Exception {
        final Path fixtureDir = writeCoercionFixture("coercion");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsMissingPropertyFixture() throws Exception {
        final Path fixtureDir = writeMissingPropertyFixture("missing-property");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsModuleImportFixture() throws Exception {
        final Path fixtureDir = writeModuleFixture("module-imports");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsPromiseThenFixture() throws Exception {
        final Path fixtureDir = writePromiseFixture("promise-then");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsAsyncAwaitFixture() throws Exception {
        final Path fixtureDir = writeAsyncAwaitFixture("async-await");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsAsyncRejectionFixture() throws Exception {
        final Path fixtureDir = writeAsyncRejectFixture("async-reject");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsAsyncIfFixture() throws Exception {
        final Path fixtureDir = writeAsyncIfFixture("async-if");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsAsyncWhileFixture() throws Exception {
        final Path fixtureDir = writeAsyncWhileFixture("async-while");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    private Path writeFixture(final String name, final boolean assertNodeMatchesTsj) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        final Path entryFile = inputDir.resolve("main.ts");
        Files.writeString(entryFile, "const value: number = 2;\nconsole.log('hello ' + value);\n", UTF_8);

        Files.writeString(expectedDir.resolve("node.stdout"), "hello 2\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "\"code\":\"TSJ-RUN-SUCCESS\"", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=" + assertNodeMatchesTsj,
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeUnsupportedFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        final Path entryFile = inputDir.resolve("main.ts");
        Files.writeString(
                entryFile,
                """
                for (let i = 0; i < 2; i = i + 1) {
                  console.log(i);
                }
                """,
                UTF_8
        );

        Files.writeString(expectedDir.resolve("node.stdout"), "0\n1\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "\"code\":\"TSJ-BACKEND-UNSUPPORTED\"", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
                "entry=input/main.ts",
                "expected.node.exitCode=0",
                "expected.node.stdout=expected/node.stdout",
                "expected.node.stderr=expected/node.stderr",
                "expected.node.stdoutMode=exact",
                "expected.node.stderrMode=exact",
                "expected.tsj.exitCode=1",
                "expected.tsj.stdout=expected/tsj.stdout",
                "expected.tsj.stderr=expected/tsj.stderr",
                "expected.tsj.stdoutMode=exact",
                "expected.tsj.stderrMode=contains",
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeClosureFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        Files.writeString(expectedDir.resolve("node.stdout"), "closure=7\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "closure=7\n", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeClassFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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
                  triple() {
                    return this.value * 3;
                  }
                }

                const d = new Derived(3);
                console.log("class=" + d.read() + ":" + d.triple());
                """,
                UTF_8
        );

        Files.writeString(expectedDir.resolve("node.stdout"), "class=5:15\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "class=5:15\n", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeObjectFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                const payload = { label: "ok", count: 2 };
                payload.count = payload.count + 4;
                console.log("object=" + payload.label + ":" + payload.count);
                """,
                UTF_8
        );

        Files.writeString(expectedDir.resolve("node.stdout"), "object=ok:6\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "object=ok:6\n", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeCoercionFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                const undef = undefined;
                console.log("coerce=" + (1 == "1") + ":" + (1 === "1"));
                console.log("nullish=" + (undef == null) + ":" + (undef === null));
                """,
                UTF_8
        );

        Files.writeString(expectedDir.resolve("node.stdout"), "coerce=true:false\nnullish=true:false\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "coerce=true:false\nnullish=true:false\n", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeModuleFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("counter.ts"),
                """
                console.log("counter-init");
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
                inputDir.resolve("format.ts"),
                """
                import { read } from "./counter.ts";
                console.log("format-init");
                export function display() {
                  return "count=" + read();
                }
                """,
                UTF_8
        );
        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                import { inc } from "./counter.ts";
                import { display } from "./format.ts";
                console.log(display());
                inc();
                console.log(display());
                """,
                UTF_8
        );

        final String expectedOutput = "counter-init\nformat-init\ncount=0\ncount=1\n";
        Files.writeString(expectedDir.resolve("node.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writePromiseFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nstep=1\ndone=2\n";
        Files.writeString(expectedDir.resolve("node.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeAsyncAwaitFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "start=4\nsync\nafter=5\ndone=6\n";
        Files.writeString(expectedDir.resolve("node.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeAsyncRejectFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nerror=boom\n";
        Files.writeString(expectedDir.resolve("node.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeAsyncIfFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nthen=10\nafter=10\ndone=10\n";
        Files.writeString(expectedDir.resolve("node.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeAsyncWhileFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nsum=6\ndone=6\n";
        Files.writeString(expectedDir.resolve("node.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), expectedOutput, UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeMissingPropertyFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                const payload = { label: "ok" };
                console.log("missing=" + payload.count);
                """,
                UTF_8
        );

        Files.writeString(expectedDir.resolve("node.stdout"), "missing=undefined\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "missing=undefined\n", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);

        final String properties = String.join(
                "\n",
                "name=" + name,
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
                "assert.nodeMatchesTsj=true",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }
}
