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

        assertFalse(result.passed());
        assertFalse(result.nodeToTsjMatched());
        assertTrue(result.nodeToTsjDiff().contains("stdout differs"));
    }

    @Test
    void harnessRunsAllFixturesFromRoot() throws Exception {
        writeFixture("first", false);
        writeFixture("second", false);

        final List<FixtureRunResult> results = new FixtureHarness().runAll(tempDir);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(FixtureRunResult::passed));
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
}
