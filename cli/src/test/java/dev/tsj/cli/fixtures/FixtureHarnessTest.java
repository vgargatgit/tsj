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
}
