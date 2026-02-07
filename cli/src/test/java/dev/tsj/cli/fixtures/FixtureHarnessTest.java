package dev.tsj.cli.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
        assertTrue(result.minimizedRepro().contains("node --no-warnings --experimental-strip-types"));
        assertTrue(result.minimizedRepro().contains("tsj run"));
        assertTrue(result.minimizedRepro().contains("exit code differs"));
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
    void harnessRunSuiteGeneratesFeatureCoverageReport() throws Exception {
        writeFixture("tsj9-basic", false);
        writeUnsupportedFixture("tsj15-unsupported");

        final FixtureSuiteResult suite = new FixtureHarness().runSuite(tempDir);

        assertEquals(2, suite.results().size());
        assertEquals(2, suite.coverageReport().totalFixtures());
        assertTrue(Files.exists(suite.coverageReportPath()));

        final String report = Files.readString(suite.coverageReportPath(), UTF_8);
        assertTrue(report.contains("\"feature\":\"tsj9\""));
        assertTrue(report.contains("\"feature\":\"tsj15\""));
        assertTrue(report.contains("\"failed\":1"));
        assertTrue(report.contains("\"totalFixtures\":2"));
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
    void harnessSupportsTopLevelAwaitFixture() throws Exception {
        final Path fixtureDir = writeTopLevelAwaitFixture("top-level-await");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsTopLevelAwaitModulesFixture() throws Exception {
        final Path fixtureDir = writeTopLevelAwaitModuleFixture("top-level-await-modules");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsTopLevelAwaitTransitiveModulesFixture() throws Exception {
        final Path fixtureDir = writeTopLevelAwaitTransitiveFixture("top-level-await-transitive");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsTopLevelAwaitUnsupportedWhileConditionFixture() throws Exception {
        final Path fixtureDir = writeTopLevelAwaitWhileUnsupportedFixture("top-level-await-while-unsupported");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertFalse(result.nodeToTsjMatched());
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
    void harnessSupportsPromiseThenableFixture() throws Exception {
        final Path fixtureDir = writePromiseThenableFixture("promise-thenable");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsPromiseThenableRejectFixture() throws Exception {
        final Path fixtureDir = writePromiseThenableRejectFixture("promise-thenable-reject");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsPromiseCatchFinallyFixture() throws Exception {
        final Path fixtureDir = writePromiseCatchFinallyFixture("promise-catch-finally");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsPromiseFinallyRejectFixture() throws Exception {
        final Path fixtureDir = writePromiseFinallyRejectFixture("promise-finally-reject");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsPromiseAllAndRaceFixture() throws Exception {
        final Path fixtureDir = writePromiseAllRaceFixture("promise-all-race");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsPromiseAllSettledAndAnyFixture() throws Exception {
        final Path fixtureDir = writePromiseAllSettledAnyFixture("promise-allsettled-any");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsPromiseAnyAggregateErrorFixture() throws Exception {
        final Path fixtureDir = writePromiseAnyRejectFixture("promise-any-reject");
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

    @Test
    void harnessSupportsAsyncArrowFixture() throws Exception {
        final Path fixtureDir = writeAsyncArrowFixture("async-arrow");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void harnessSupportsAsyncObjectMethodFixture() throws Exception {
        final Path fixtureDir = writeAsyncObjectMethodFixture("async-object-method");
        final FixtureSpec fixture = FixtureLoader.loadFixture(fixtureDir);

        final FixtureRunResult result = new FixtureHarness().runFixture(fixture);

        assertTrue(result.passed());
        assertTrue(result.nodeToTsjMatched());
        assertEquals("", result.nodeResult().diff());
        assertEquals("", result.tsjResult().diff());
    }

    @Test
    void committedTsj13fFixturesRunSuccessfully() throws Exception {
        final Path fixturesRoot = Path.of("..", "tests", "fixtures").toAbsolutePath().normalize();
        final List<String> fixtureNames = List.of(
                "tsj13f-top-level-await",
                "tsj13f-top-level-await-modules",
                "tsj13f-top-level-await-transitive",
                "tsj13f-top-level-await-while-unsupported"
        );

        final FixtureHarness harness = new FixtureHarness();
        for (String fixtureName : fixtureNames) {
            final FixtureSpec fixture = FixtureLoader.loadFixture(fixturesRoot.resolve(fixtureName));
            try {
                final FixtureRunResult result = harness.runFixture(fixture);
                final String failureDetails = List.of(
                        "nodeDiff=" + result.nodeResult().diff(),
                        "tsjDiff=" + result.tsjResult().diff(),
                        "nodeToTsjDiff=" + result.nodeToTsjDiff(),
                        "nodeStdout=" + result.nodeResult().stdout(),
                        "nodeStderr=" + result.nodeResult().stderr(),
                        "tsjStdout=" + result.tsjResult().stdout(),
                        "tsjStderr=" + result.tsjResult().stderr()
                ).stream().collect(Collectors.joining(" | "));
                assertTrue(result.passed(), fixtureName + " should pass: " + failureDetails);
            } finally {
                deleteRecursively(fixture.directory().resolve(".tsj-out"));
            }
        }
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

    private Path writeTopLevelAwaitFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                console.log("before");
                const value = await Promise.resolve(1);
                console.log("after=" + value);
                """,
                UTF_8
        );

        final String expectedOutput = "before\nafter=1\n";
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

    private Path writeTopLevelAwaitModuleFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("dep.ts"),
                """
                export let status = "init";
                status = await Promise.resolve("ready");
                console.log("dep=" + status);
                """,
                UTF_8
        );
        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                import { status } from "./dep.ts";
                console.log("main=" + status);
                """,
                UTF_8
        );

        final String expectedOutput = "dep=ready\nmain=ready\n";
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

    private Path writeTopLevelAwaitWhileUnsupportedFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                let i = 0;
                while (await Promise.resolve(i < 1)) {
                  i = i + 1;
                }
                console.log(i);
                """,
                UTF_8
        );

        Files.writeString(expectedDir.resolve("node.stdout"), "1\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "while condition", UTF_8);

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
                "assert.nodeMatchesTsj=false",
                ""
        );
        Files.writeString(fixtureDir.resolve("fixture.properties"), properties, UTF_8);
        return fixtureDir;
    }

    private Path writeTopLevelAwaitTransitiveFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("a.ts"),
                """
                export let value = 0;
                value = await Promise.resolve(5);
                console.log("a=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                inputDir.resolve("b.ts"),
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
                inputDir.resolve("main.ts"),
                """
                import { read } from "./b.ts";
                console.log("main=" + read());
                """,
                UTF_8
        );

        final String expectedOutput = "a=5\nb=5\nmain=5\n";
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

    private Path writePromiseThenableFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nvalue=41\nnext=42\n";
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

    private Path writePromiseThenableRejectFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

    private Path writePromiseCatchFinallyFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\ncatch=boom\nfinally\nvalue=7\n";
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

    private Path writePromiseFinallyRejectFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nerror=fin\n";
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

    private Path writePromiseAllRaceFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nall=3\nrace=win\n";
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

    private Path writePromiseAllSettledAnyFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nsettled=2\nany=ok\n";
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

    private Path writePromiseAnyRejectFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\nanyErr=AggregateError:2\n";
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

    private Path writeAsyncArrowFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\ndone=6\n";
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

    private Path writeAsyncObjectMethodFixture(final String name) throws IOException {
        final Path fixtureDir = tempDir.resolve(name);
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
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

        final String expectedOutput = "sync\ndone=12\n";
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

    private static void deleteRecursively(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
