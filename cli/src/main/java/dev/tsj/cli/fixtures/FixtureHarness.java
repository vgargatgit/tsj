package dev.tsj.cli.fixtures;

import dev.tsj.cli.TsjCli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes fixture specs on Node and TSJ.
 */
public final class FixtureHarness {
    private static final String NODE_BINARY = "node";
    private static final String NODE_NO_WARNINGS_FLAG = "--no-warnings";
    private static final String NODE_TS_FLAG = "--experimental-strip-types";
    private static final String COVERAGE_REPORT_FILE = "tsj-fixture-coverage.json";
    private static final Pattern FEATURE_BUCKET_PATTERN = Pattern.compile("^tsj(\\d+[a-z]?)($|[-_].*)");

    public List<FixtureRunResult> runAll(final Path fixturesRoot) {
        return runSuite(fixturesRoot).results();
    }

    public FixtureSuiteResult runSuite(final Path fixturesRoot) {
        final List<FixtureSpec> fixtures = FixtureLoader.loadFixtures(fixturesRoot);
        final List<FixtureRunResult> results = new ArrayList<>();
        for (FixtureSpec fixture : fixtures) {
            results.add(runFixture(fixture));
        }
        final FixtureCoverageReport coverageReport = buildCoverageReport(results);
        final Path coverageReportPath = fixturesRoot.toAbsolutePath()
                .normalize()
                .resolve(COVERAGE_REPORT_FILE);
        writeCoverageReport(coverageReportPath, coverageReport);
        return new FixtureSuiteResult(results, coverageReport, coverageReportPath);
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
                nodeToTsjComparison.diff,
                buildMinimizedRepro(fixture, nodeResult, tsjResult, nodeToTsjComparison)
        );
    }

    private RuntimeExecutionResult runNodeFixture(final FixtureSpec fixture) {
        final List<String> command = new ArrayList<>();
        command.add(NODE_BINARY);
        command.add(NODE_NO_WARNINGS_FLAG);
        command.add(NODE_TS_FLAG);
        command.addAll(fixture.nodeArgs());
        command.add(fixture.entryFile().toString());
        final CommandResult actual = runExternal(
                command,
                fixture.directory()
        );
        return compareExpected("node", fixture.nodeExpectation(), actual);
    }

    private RuntimeExecutionResult runTsjFixture(final FixtureSpec fixture) {
        final Path outDir = fixture.directory().resolve(".tsj-out").toAbsolutePath().normalize();
        final ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        final List<String> command = new ArrayList<>();
        command.add("run");
        command.add(fixture.entryFile().toString());
        command.add("--out");
        command.add(outDir.toString());
        command.addAll(fixture.tsjArgs());
        final int exitCode = TsjCli.execute(
                command.toArray(String[]::new),
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
        final String nodeStdout = canonicalizeForComparison(nodeResult.stdout());
        final String tsjStdout = canonicalizeForComparison(stripTsjDiagnostics(tsjResult.stdout()));
        if (!nodeStdout.equals(tsjStdout)) {
            diffParts.add("stdout differs");
        }
        final String nodeStderr = canonicalizeForComparison(nodeResult.stderr());
        final String tsjStderr = canonicalizeForComparison(stripTsjDiagnostics(tsjResult.stderr()));
        if (!nodeStderr.equals(tsjStderr)) {
            diffParts.add("stderr differs");
        }
        return new NodeToTsjComparison(diffParts.isEmpty(), String.join("; ", diffParts));
    }

    private static boolean matches(final MatchMode mode, final String expected, final String actual) {
        final String expectedNorm = canonicalizeForComparison(expected);
        final String actualNorm = canonicalizeForComparison(actual);
        return switch (mode) {
            case EXACT -> actualNorm.equals(expectedNorm);
            case CONTAINS -> containsByLines(expectedNorm, actualNorm);
        };
    }

    private static boolean containsByLines(final String expected, final String actual) {
        if (expected.isEmpty()) {
            return true;
        }
        final String[] expectedLines = expected.split("\n", -1);
        for (String line : expectedLines) {
            if (line.isBlank()) {
                continue;
            }
            if (!actual.contains(line)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(final String value) {
        return value.replace("\r\n", "\n");
    }

    private static String canonicalizeForComparison(final String value) {
        final String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized;
    }

    private static String stripTsjDiagnostics(final String output) {
        final String normalized = normalize(output);
        final boolean hadTrailingNewline = normalized.endsWith("\n");
        final String[] lines = normalized.split("\n", -1);
        final List<String> retained = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            final String line = lines[index];
            if (hadTrailingNewline && index == lines.length - 1 && line.isEmpty()) {
                continue;
            }
            final String trimmed = line.trim();
            final boolean isTsjDiagnostic = trimmed.startsWith("{")
                    && trimmed.contains("\"code\":\"TSJ-");
            if (!isTsjDiagnostic) {
                retained.add(line);
            }
        }
        if (retained.isEmpty()) {
            return "";
        }
        final String joined = String.join("\n", retained);
        if (hadTrailingNewline) {
            return joined + "\n";
        }
        return joined;
    }

    private static String buildMinimizedRepro(
            final FixtureSpec fixture,
            final RuntimeExecutionResult nodeResult,
            final RuntimeExecutionResult tsjResult,
            final NodeToTsjComparison nodeToTsjComparison
    ) {
        final boolean passed = nodeResult.matchedExpectation()
                && tsjResult.matchedExpectation()
                && (!fixture.assertNodeMatchesTsj() || nodeToTsjComparison.matched());
        if (passed) {
            return "";
        }

        final List<String> mismatches = new ArrayList<>();
        if (!nodeResult.matchedExpectation()) {
            mismatches.add("node expectation mismatch: " + nodeResult.diff());
        }
        if (!tsjResult.matchedExpectation()) {
            mismatches.add("tsj expectation mismatch: " + tsjResult.diff());
        }
        if (fixture.assertNodeMatchesTsj() && !nodeToTsjComparison.matched()) {
            mismatches.add("node-vs-tsj mismatch: " + nodeToTsjComparison.diff());
        }

        final String stdoutMismatch = firstLineMismatch(
                canonicalizeForComparison(nodeResult.stdout()),
                canonicalizeForComparison(stripTsjDiagnostics(tsjResult.stdout()))
        );
        if (!stdoutMismatch.isEmpty()) {
            mismatches.add("stdout " + stdoutMismatch);
        }
        final String stderrMismatch = firstLineMismatch(
                canonicalizeForComparison(nodeResult.stderr()),
                canonicalizeForComparison(stripTsjDiagnostics(tsjResult.stderr()))
        );
        if (!stderrMismatch.isEmpty()) {
            mismatches.add("stderr " + stderrMismatch);
        }

        final String entry = fixture.entryFile().toString();
        final String nodeArgs = fixture.nodeArgs().isEmpty() ? "" : " " + String.join(" ", fixture.nodeArgs());
        final String tsjArgs = fixture.tsjArgs().isEmpty() ? "" : " " + String.join(" ", fixture.tsjArgs());
        final String nodeCommand = NODE_BINARY
                + " "
                + NODE_NO_WARNINGS_FLAG
                + " "
                + NODE_TS_FLAG
                + nodeArgs
                + " "
                + entry;
        final String tsjCommand = "tsj run "
                + entry
                + " --out "
                + fixture.directory().resolve(".tsj-out").toAbsolutePath().normalize()
                + tsjArgs;
        return "fixture=" + fixture.name()
                + " | mismatch=" + String.join(" | ", mismatches)
                + " | repro=" + nodeCommand
                + " && " + tsjCommand;
    }

    private static String firstLineMismatch(final String left, final String right) {
        if (left.equals(right)) {
            return "";
        }
        final String[] leftLines = left.split("\n", -1);
        final String[] rightLines = right.split("\n", -1);
        final int limit = Math.max(leftLines.length, rightLines.length);
        for (int index = 0; index < limit; index++) {
            final String leftLine = index < leftLines.length ? leftLines[index] : "<missing>";
            final String rightLine = index < rightLines.length ? rightLines[index] : "<missing>";
            if (!leftLine.equals(rightLine)) {
                return "@line=" + (index + 1)
                        + " node=" + truncateLine(leftLine)
                        + " tsj=" + truncateLine(rightLine);
            }
        }
        return "";
    }

    private static String truncateLine(final String value) {
        final String cleaned = value.replace("\r", "");
        if (cleaned.length() <= 80) {
            return "'" + cleaned + "'";
        }
        return "'" + cleaned.substring(0, 77) + "...'";
    }

    private static FixtureCoverageReport buildCoverageReport(final List<FixtureRunResult> results) {
        final Map<String, int[]> counts = new LinkedHashMap<>();
        int passed = 0;
        for (FixtureRunResult result : results) {
            final String feature = featureBucketForFixture(result.fixtureName());
            final int[] counter = counts.computeIfAbsent(feature, ignored -> new int[3]);
            counter[0] = counter[0] + 1;
            if (result.passed()) {
                counter[1] = counter[1] + 1;
                passed++;
            } else {
                counter[2] = counter[2] + 1;
            }
        }

        final List<FixtureCoverageReport.FeatureCoverage> byFeature = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    final int[] counter = entry.getValue();
                    byFeature.add(
                            new FixtureCoverageReport.FeatureCoverage(
                                    entry.getKey(),
                                    counter[0],
                                    counter[1],
                                    counter[2]
                            )
                    );
                });

        return new FixtureCoverageReport(
                results.size(),
                passed,
                results.size() - passed,
                byFeature
        );
    }

    private static String featureBucketForFixture(final String fixtureName) {
        final String normalized = fixtureName == null ? "" : fixtureName.toLowerCase();
        final Matcher matcher = FEATURE_BUCKET_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return "tsj" + matcher.group(1);
        }
        return "unmapped";
    }

    private static void writeCoverageReport(final Path reportPath, final FixtureCoverageReport coverageReport) {
        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, coverageReport.toJson() + "\n", StandardCharsets.UTF_8);
        } catch (final IOException ioException) {
            throw new IllegalArgumentException(
                    "Failed to write fixture coverage report " + reportPath + ": " + ioException.getMessage(),
                    ioException
            );
        }
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
