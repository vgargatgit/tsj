package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSyntaxConformanceReadinessGateTest {
    private static final double MIN_OVERALL_PASS_PERCENT = 95.0d;
    private static final double MIN_CATEGORY_PASS_PERCENT = 100.0d;
    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\":\"([^\"]+)\"");
    private static final String REPORT_RELATIVE_PATH = "tests/conformance/tsj-syntax-readiness.json";
    private static final Map<String, String> EXPECTED_BLOCKERS = Map.of(
            "examples/tgta/src/ok/020_expressions.ts", "TSJ-BACKEND-UNSUPPORTED",
            "examples/tgta/src/ok/050_modules.ts", "TSJ-BACKEND-UNSUPPORTED"
    );
    private static final List<CorpusRoot> CORPUS_ROOTS = List.of(
            new CorpusRoot("tgta", "examples/tgta/src/ok", true, true),
            new CorpusRoot("utta", "examples/UTTA/src/grammar", false, false),
            new CorpusRoot("utta", "examples/UTTA/src/stress", false, false),
            new CorpusRoot("xtta", "examples/XTTA/src/grammar", false, false),
            new CorpusRoot("xtta", "examples/XTTA/src/builtins", false, false),
            new CorpusRoot("typescript", "tests/conformance/corpus/typescript/ok", false, false),
            new CorpusRoot("oss", "tests/conformance/corpus/oss/ok", false, false)
    );

    @TempDir
    Path tempDir;

    @Test
    void readinessGateGeneratesSyntaxCategoryReportAndEnforcesThresholds() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final List<FixtureSpec> fixtures = collectCorpusFixtures(repoRoot);
        assertFalse(fixtures.isEmpty(), "No syntax corpus fixtures discovered.");

        final Map<String, CategoryCounter> byCategory = new LinkedHashMap<>();
        final List<FailureEntry> unexpectedFailures = new ArrayList<>();
        final Map<String, BlockerObservation> blockerObservations = new LinkedHashMap<>();
        final Set<String> seenExpectedBlockers = new LinkedHashSet<>();
        final Set<String> blockedCategories = new LinkedHashSet<>();
        int passed = 0;

        for (FixtureSpec fixture : fixtures) {
            final CategoryCounter counter = byCategory.computeIfAbsent(fixture.category(), ignored -> new CategoryCounter());
            counter.total++;

            final Path outDir = tempDir.resolve("tsj68-" + fixture.relativePath().replace('/', '_').replace('.', '_'));
            final CliInvocationResult result = invokeCompile(fixture.absolutePath(), outDir);
            final boolean compileSuccess = result.exitCode() == 0
                    && result.stdoutText().contains("\"code\":\"TSJ-COMPILE-SUCCESS\"");
            final String expectedBlockerCode = EXPECTED_BLOCKERS.get(fixture.relativePath());
            if (expectedBlockerCode != null) {
                seenExpectedBlockers.add(fixture.relativePath());
            }

            if (compileSuccess) {
                counter.passed++;
                passed++;
                if (expectedBlockerCode != null) {
                    unexpectedFailures.add(FailureEntry.expectedBlockerCompiled(fixture, expectedBlockerCode, result));
                }
                continue;
            }

            counter.failed++;
            final String diagnosticCode = firstDiagnosticCode(result.stdoutText(), result.stderrText());
            if (expectedBlockerCode != null) {
                blockerObservations.put(
                        fixture.relativePath(),
                        new BlockerObservation(fixture, expectedBlockerCode, diagnosticCode, result.exitCode())
                );
                if (expectedBlockerCode.equals(diagnosticCode)) {
                    blockedCategories.add(fixture.category());
                    continue;
                }
                unexpectedFailures.add(FailureEntry.unexpectedDiagnostic(fixture, expectedBlockerCode, diagnosticCode, result));
                continue;
            }

            unexpectedFailures.add(FailureEntry.unexpectedDiagnostic(fixture, null, diagnosticCode, result));
        }

        for (Map.Entry<String, String> expected : EXPECTED_BLOCKERS.entrySet()) {
            if (!seenExpectedBlockers.contains(expected.getKey())) {
                unexpectedFailures.add(FailureEntry.missingExpectedBlocker(expected.getKey(), expected.getValue()));
            }
        }

        final int total = fixtures.size();
        final int failed = total - passed;
        final double overallPassPercent = percent(passed, total);
        final Path reportPath = repoRoot.resolve(REPORT_RELATIVE_PATH).toAbsolutePath().normalize();
        Files.createDirectories(reportPath.getParent());
        Files.writeString(
                reportPath,
                renderReportJson(fixtures, byCategory, total, passed, failed, overallPassPercent, blockedCategories, blockerObservations, unexpectedFailures) + "\n",
                UTF_8
        );

        assertTrue(
                unexpectedFailures.isEmpty(),
                "TSJ-68 syntax readiness failures (" + unexpectedFailures.size() + "):\n\n" + renderFailureSummaries(unexpectedFailures)
        );

        assertTrue(
                overallPassPercent >= MIN_OVERALL_PASS_PERCENT,
                "TSJ-68 readiness overall pass rate " + formatPercent(overallPassPercent)
                        + "% is below threshold " + formatPercent(MIN_OVERALL_PASS_PERCENT) + "%."
        );

        final List<String> categoryThresholdFailures = new ArrayList<>();
        for (Map.Entry<String, CategoryCounter> entry : byCategory.entrySet()) {
            final String category = entry.getKey();
            final CategoryCounter counter = entry.getValue();
            final double categoryPassPercent = percent(counter.passed, counter.total);
            if (blockedCategories.contains(category)) {
                continue;
            }
            if (categoryPassPercent < MIN_CATEGORY_PASS_PERCENT) {
                categoryThresholdFailures.add(
                        category + "=" + formatPercent(categoryPassPercent)
                                + "% (threshold " + formatPercent(MIN_CATEGORY_PASS_PERCENT) + "%)"
                );
            }
        }

        assertTrue(
                categoryThresholdFailures.isEmpty(),
                "TSJ-68 readiness category thresholds failed: " + String.join(", ", categoryThresholdFailures)
        );
    }

    private static List<FixtureSpec> collectCorpusFixtures(final Path repoRoot) throws Exception {
        final List<FixtureSpec> fixtures = new ArrayList<>();
        for (CorpusRoot corpusRoot : CORPUS_ROOTS) {
            final Path root = repoRoot.resolve(corpusRoot.relativeRoot()).normalize();
            assertTrue(Files.isDirectory(root), "Missing syntax corpus root: " + root);

            final List<Path> rootFixtures;
            try (Stream<Path> paths = Files.list(root)) {
                rootFixtures = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> isCorpusFixture(path, corpusRoot.includeDts()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
            assertFalse(rootFixtures.isEmpty(), "No fixtures discovered in corpus root " + root);

            final String baseCategory = root.getFileName().toString();
            for (Path fixture : rootFixtures) {
                final String relativePath = normalizePath(repoRoot.relativize(fixture));
                final String categoryName = corpusRoot.useTgtaFilenameCategories()
                        ? syntaxCategoryForTgtaFixture(fixture.getFileName().toString())
                        : baseCategory;
                fixtures.add(new FixtureSpec(
                        corpusRoot.suite(),
                        corpusRoot.suite() + "/" + categoryName,
                        relativePath,
                        fixture
                ));
            }
        }

        fixtures.sort(Comparator.comparing(FixtureSpec::relativePath));
        return fixtures;
    }

    private static CliInvocationResult invokeCompile(final Path fixture, final Path outDir) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        fixture.toString(),
                        "--out",
                        outDir.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        return new CliInvocationResult(
                exitCode,
                stdout.toString(UTF_8),
                stderr.toString(UTF_8)
        );
    }

    private static boolean isCorpusFixture(final Path path, final boolean includeDts) {
        final String fileName = path.getFileName().toString();
        if (fileName.endsWith(".tsx")) {
            return false;
        }
        if (fileName.endsWith(".ts")) {
            return true;
        }
        return includeDts && fileName.endsWith(".d.ts");
    }

    private static String firstDiagnosticCode(final String stdoutText, final String stderrText) {
        final String merged = stdoutText + "\n" + stderrText;
        final Matcher matcher = CODE_PATTERN.matcher(merged);
        if (!matcher.find()) {
            return "NO_CODE";
        }
        return matcher.group(1);
    }

    private static String syntaxCategoryForTgtaFixture(final String fixtureName) {
        String stem = fixtureName;
        if (stem.endsWith(".d.ts")) {
            stem = stem.substring(0, stem.length() - ".d.ts".length());
        } else if (stem.endsWith(".ts")) {
            stem = stem.substring(0, stem.length() - ".ts".length());
        }
        final int separator = stem.indexOf('_');
        if (separator < 0 || separator + 1 >= stem.length()) {
            return "unmapped";
        }
        return stem.substring(separator + 1);
    }

    private static double percent(final int numerator, final int denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (numerator * 100.0d) / denominator;
    }

    private static String formatPercent(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String renderFailureSummaries(final List<FailureEntry> failures) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(failures.get(i).summary());
        }
        return builder.toString();
    }

    private static String renderReportJson(
            final List<FixtureSpec> fixtures,
            final Map<String, CategoryCounter> byCategory,
            final int total,
            final int passed,
            final int failed,
            final double overallPassPercent,
            final Set<String> blockedCategories,
            final Map<String, BlockerObservation> blockerObservations,
            final List<FailureEntry> unexpectedFailures
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"corpus\":\"tsj-syntax-conformance\",");
        builder.append("\"totals\":{");
        builder.append("\"total\":").append(total).append(",");
        builder.append("\"passed\":").append(passed).append(",");
        builder.append("\"failed\":").append(failed).append(",");
        builder.append("\"passPercent\":").append(formatPercent(overallPassPercent));
        builder.append("},");
        builder.append("\"thresholds\":{");
        builder.append("\"minOverallPassPercent\":").append(formatPercent(MIN_OVERALL_PASS_PERCENT)).append(",");
        builder.append("\"minCategoryPassPercent\":").append(formatPercent(MIN_CATEGORY_PASS_PERCENT));
        builder.append("},");
        builder.append("\"fixtures\":[");
        for (int i = 0; i < fixtures.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            final FixtureSpec fixture = fixtures.get(i);
            builder.append("{");
            builder.append("\"suite\":\"").append(escapeJson(fixture.suite())).append("\",");
            builder.append("\"category\":\"").append(escapeJson(fixture.category())).append("\",");
            builder.append("\"path\":\"").append(escapeJson(fixture.relativePath())).append("\"");
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"expectedBlockers\":[");
        final List<Map.Entry<String, String>> expectedEntries = EXPECTED_BLOCKERS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int i = 0; i < expectedEntries.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            final Map.Entry<String, String> expected = expectedEntries.get(i);
            final BlockerObservation observation = blockerObservations.get(expected.getKey());
            builder.append("{");
            builder.append("\"fixture\":\"").append(escapeJson(expected.getKey())).append("\",");
            builder.append("\"expectedCode\":\"").append(escapeJson(expected.getValue())).append("\",");
            builder.append("\"observedCode\":\"")
                    .append(escapeJson(observation == null ? "MISSING" : observation.observedCode()))
                    .append("\",");
            builder.append("\"status\":\"")
                    .append(escapeJson(observation != null && expected.getValue().equals(observation.observedCode()) ? "matched" : "mismatch"))
                    .append("\",");
            builder.append("\"repro\":\"").append(escapeJson(reproCommand(expected.getKey()))).append("\"");
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"blockedCategories\":[");
        final List<String> blocked = blockedCategories.stream().sorted().toList();
        for (int i = 0; i < blocked.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(blocked.get(i))).append("\"");
        }
        builder.append("],");
        builder.append("\"categories\":[");
        final List<Map.Entry<String, CategoryCounter>> categories = byCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int i = 0; i < categories.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            final Map.Entry<String, CategoryCounter> entry = categories.get(i);
            final CategoryCounter counter = entry.getValue();
            final double passPercent = percent(counter.passed, counter.total);
            builder.append("{");
            builder.append("\"category\":\"").append(escapeJson(entry.getKey())).append("\",");
            builder.append("\"total\":").append(counter.total).append(",");
            builder.append("\"passed\":").append(counter.passed).append(",");
            builder.append("\"failed\":").append(counter.failed).append(",");
            builder.append("\"passPercent\":").append(formatPercent(passPercent));
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"unexpectedFailures\":[");
        final List<FailureEntry> sortedFailures = unexpectedFailures.stream()
                .sorted(Comparator.comparing(FailureEntry::sortKey))
                .toList();
        for (int i = 0; i < sortedFailures.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            final FailureEntry failure = sortedFailures.get(i);
            builder.append("{");
            builder.append("\"type\":\"").append(escapeJson(failure.type())).append("\",");
            builder.append("\"fixture\":\"").append(escapeJson(failure.fixture())).append("\",");
            builder.append("\"category\":\"").append(escapeJson(failure.category())).append("\",");
            builder.append("\"expectedCode\":\"").append(escapeJson(failure.expectedCode())).append("\",");
            builder.append("\"observedCode\":\"").append(escapeJson(failure.observedCode())).append("\",");
            builder.append("\"exitCode\":").append(failure.exitCode()).append(",");
            builder.append("\"repro\":\"").append(escapeJson(failure.repro())).append("\"");
            builder.append("}");
        }
        builder.append("]");
        builder.append("}");
        return builder.toString();
    }

    private static String reproCommand(final String fixturePath) {
        return "mvn -B -ntp -q -f cli/pom.xml exec:java -Dexec.mainClass=dev.tsj.cli.TsjCli -Dexec.args=\"compile "
                + fixturePath
                + " --out /tmp/tsj68-repro\"";
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String normalizePath(final Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("examples/tgta/src/ok"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve TSJ repository root.");
    }

    private record CorpusRoot(String suite, String relativeRoot, boolean includeDts, boolean useTgtaFilenameCategories) {
    }

    private record FixtureSpec(String suite, String category, String relativePath, Path absolutePath) {
    }

    private static final class CategoryCounter {
        private int total;
        private int passed;
        private int failed;
    }

    private record BlockerObservation(FixtureSpec fixture, String expectedCode, String observedCode, int exitCode) {
    }

    private record CliInvocationResult(int exitCode, String stdoutText, String stderrText) {
    }

    private record FailureEntry(
            String type,
            String fixture,
            String category,
            String expectedCode,
            String observedCode,
            int exitCode,
            String repro
    ) {
        static FailureEntry expectedBlockerCompiled(
                final FixtureSpec fixture,
                final String expectedCode,
                final CliInvocationResult ignored
        ) {
            return new FailureEntry(
                    "expected_blocker_compiled",
                    fixture.relativePath(),
                    fixture.category(),
                    expectedCode,
                    "TSJ-COMPILE-SUCCESS",
                    0,
                    reproCommand(fixture.relativePath())
            );
        }

        static FailureEntry unexpectedDiagnostic(
                final FixtureSpec fixture,
                final String expectedCode,
                final String observedCode,
                final CliInvocationResult result
        ) {
            return new FailureEntry(
                    "unexpected_diagnostic",
                    fixture.relativePath(),
                    fixture.category(),
                    expectedCode == null ? "TSJ-COMPILE-SUCCESS" : expectedCode,
                    observedCode,
                    result.exitCode(),
                    reproCommand(fixture.relativePath())
            );
        }

        static FailureEntry missingExpectedBlocker(final String fixturePath, final String expectedCode) {
            return new FailureEntry(
                    "missing_expected_blocker",
                    fixturePath,
                    "tgta/unknown",
                    expectedCode,
                    "NOT_OBSERVED",
                    -1,
                    reproCommand(fixturePath)
            );
        }

        String summary() {
            return "type=" + type
                    + ", fixture=" + fixture
                    + ", category=" + category
                    + ", expectedCode=" + expectedCode
                    + ", observedCode=" + observedCode
                    + ", exitCode=" + exitCode
                    + "\nrepro: " + repro;
        }

        String sortKey() {
            return fixture + "|" + type;
        }
    }
}
