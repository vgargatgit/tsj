package dev.tsj.cli.fixtures;

import dev.tsj.cli.TsjCli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes fixture specs on Node and TSJ.
 */
public final class FixtureHarness {
    private static final String NODE_BINARY = "node";
    private static final String NODE_TS_FLAG = "--experimental-strip-types";

    public List<FixtureRunResult> runAll(final Path fixturesRoot) {
        final List<FixtureSpec> fixtures = FixtureLoader.loadFixtures(fixturesRoot);
        final List<FixtureRunResult> results = new ArrayList<>();
        for (FixtureSpec fixture : fixtures) {
            results.add(runFixture(fixture));
        }
        return results;
    }

    public FixtureRunResult runFixture(final FixtureSpec fixture) {
        final RuntimeExecutionResult nodeResult = runNodeFixture(fixture);
        final RuntimeExecutionResult tsjResult = runTsjFixture(fixture);
        final NodeToTsjComparison nodeToTsjComparison = compareNodeToTsj(fixture, nodeResult, tsjResult);
        return new FixtureRunResult(
                fixture.name(),
                nodeResult,
                tsjResult,
                fixture.assertNodeMatchesTsj(),
                nodeToTsjComparison.matched,
                nodeToTsjComparison.diff
        );
    }

    private RuntimeExecutionResult runNodeFixture(final FixtureSpec fixture) {
        final CommandResult actual = runExternal(
                List.of(
                        NODE_BINARY,
                        NODE_TS_FLAG,
                        fixture.entryFile().toString()
                ),
                fixture.directory()
        );
        return compareExpected("node", fixture.nodeExpectation(), actual);
    }

    private RuntimeExecutionResult runTsjFixture(final FixtureSpec fixture) {
        final Path outDir = fixture.directory().resolve(".tsj-out").toAbsolutePath().normalize();
        final ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{"run", fixture.entryFile().toString(), "--out", outDir.toString()},
                new PrintStream(stdoutBuffer),
                new PrintStream(stderrBuffer)
        );
        final CommandResult actual = new CommandResult(
                exitCode,
                stdoutBuffer.toString(StandardCharsets.UTF_8),
                stderrBuffer.toString(StandardCharsets.UTF_8)
        );
        return compareExpected("tsj", fixture.tsjExpectation(), actual);
    }

    private RuntimeExecutionResult compareExpected(
            final String runtime,
            final ExpectedRuntimeResult expected,
            final CommandResult actual
    ) {
        final List<String> diffParts = new ArrayList<>();
        if (actual.exitCode != expected.exitCode()) {
            diffParts.add("exit code differs: expected " + expected.exitCode() + ", got " + actual.exitCode);
        }
        if (!matches(expected.stdoutMode(), expected.stdout(), actual.stdout)) {
            diffParts.add("stdout differs");
        }
        if (!matches(expected.stderrMode(), expected.stderr(), actual.stderr)) {
            diffParts.add("stderr differs");
        }
        return new RuntimeExecutionResult(
                runtime,
                actual.exitCode,
                actual.stdout,
                actual.stderr,
                diffParts.isEmpty(),
                String.join("; ", diffParts)
        );
    }

    private NodeToTsjComparison compareNodeToTsj(
            final FixtureSpec fixture,
            final RuntimeExecutionResult nodeResult,
            final RuntimeExecutionResult tsjResult
    ) {
        if (!fixture.assertNodeMatchesTsj()) {
            return new NodeToTsjComparison(false, "comparison disabled");
        }
        final List<String> diffParts = new ArrayList<>();
        if (nodeResult.exitCode() != tsjResult.exitCode()) {
            diffParts.add("exit code differs");
        }
        if (!normalize(nodeResult.stdout()).equals(normalize(tsjResult.stdout()))) {
            diffParts.add("stdout differs");
        }
        if (!normalize(nodeResult.stderr()).equals(normalize(tsjResult.stderr()))) {
            diffParts.add("stderr differs");
        }
        return new NodeToTsjComparison(diffParts.isEmpty(), String.join("; ", diffParts));
    }

    private static boolean matches(final MatchMode mode, final String expected, final String actual) {
        final String expectedNorm = normalize(expected);
        final String actualNorm = normalize(actual);
        return switch (mode) {
            case EXACT -> actualNorm.equals(expectedNorm);
            case CONTAINS -> actualNorm.contains(expectedNorm);
        };
    }

    private static String normalize(final String value) {
        return value.replace("\r\n", "\n");
    }

    private CommandResult runExternal(final List<String> command, final Path workDir) {
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());

        try {
            final Process process = processBuilder.start();
            final StreamCollector stdoutCollector = new StreamCollector(process.getInputStream());
            final StreamCollector stderrCollector = new StreamCollector(process.getErrorStream());
            stdoutCollector.start();
            stderrCollector.start();
            final int exitCode = process.waitFor();
            stdoutCollector.join();
            stderrCollector.join();
            return new CommandResult(exitCode, stdoutCollector.output(), stderrCollector.output());
        } catch (final IOException ioException) {
            return new CommandResult(1, "", "failed to launch command: " + ioException.getMessage());
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return new CommandResult(1, "", "process interrupted");
        }
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private record NodeToTsjComparison(boolean matched, String diff) {
    }

    private static final class StreamCollector extends Thread {
        private final InputStream inputStream;
        private volatile String output;

        private StreamCollector(final InputStream inputStream) {
            this.inputStream = inputStream;
            this.output = "";
            setName("tsj-stream-collector");
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                this.output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException ioException) {
                this.output = "failed to collect stream: " + ioException.getMessage();
            }
        }

        private String output() {
            return output;
        }
    }
}
