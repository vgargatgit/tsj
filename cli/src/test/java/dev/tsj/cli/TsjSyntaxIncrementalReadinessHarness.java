package dev.tsj.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-69 incremental syntax pipeline readiness harness.
 */
final class TsjSyntaxIncrementalReadinessHarness {
    private static final String REPORT_FILE = "tsj69-incremental-readiness.json";
    private static final double MIN_WARM_FRONTEND_HIT_RATIO = 0.80d;
    private static final double MIN_WARM_LOWERING_HIT_RATIO = 0.80d;
    private static final int WARM_ITERATIONS = 3;
    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\":\"([^\"]+)\"");
    private static final Pattern FRONTEND_STAGE_PATTERN = Pattern.compile("\"incrementalFrontendStage\":\"([^\"]+)\"");
    private static final Pattern LOWERING_STAGE_PATTERN = Pattern.compile("\"incrementalLoweringStage\":\"([^\"]+)\"");
    private static final Pattern BACKEND_STAGE_PATTERN = Pattern.compile("\"incrementalBackendStage\":\"([^\"]+)\"");
    private static final String INCREMENTAL_CACHE_PROPERTY = "tsj.backend.incrementalCache";

    TsjSyntaxIncrementalReadinessReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj69-incremental-work");
        Files.createDirectories(workRoot);

        final Path dependency = workRoot.resolve("dep.ts");
        final Path entryFile = workRoot.resolve("main.ts");
        Files.writeString(dependency, "export let value = 1;\n", UTF_8);
        Files.writeString(
                entryFile,
                """
                import { value } from "./dep.ts";
                console.log("value=" + value);
                """,
                UTF_8
        );

        final List<TsjSyntaxIncrementalReadinessReport.IterationResult> iterations = new ArrayList<>();
        final String previousIncrementalCache = System.getProperty(INCREMENTAL_CACHE_PROPERTY);
        try {
            System.setProperty(INCREMENTAL_CACHE_PROPERTY, "true");

            iterations.add(runCompileIteration("cold-1", entryFile, workRoot.resolve("out-cold-1")));
            for (int index = 1; index <= WARM_ITERATIONS; index++) {
                iterations.add(runCompileIteration(
                        "warm-" + index,
                        entryFile,
                        workRoot.resolve("out-warm-" + index)
                ));
            }

            Files.writeString(dependency, "export let value = 2;\n", UTF_8);
            iterations.add(runCompileIteration("after-change", entryFile, workRoot.resolve("out-after-change")));
        } finally {
            if (previousIncrementalCache == null) {
                System.clearProperty(INCREMENTAL_CACHE_PROPERTY);
            } else {
                System.setProperty(INCREMENTAL_CACHE_PROPERTY, previousIncrementalCache);
            }
        }

        final List<TsjSyntaxIncrementalReadinessReport.IterationResult> warmIterations = iterations.stream()
                .filter(iteration -> iteration.id().startsWith("warm-"))
                .toList();
        final double warmFrontendHitRatio = stageRatio(warmIterations, "hit", StageSelector.FRONTEND);
        final double warmLoweringHitRatio = stageRatio(warmIterations, "hit", StageSelector.LOWERING);
        final TsjSyntaxIncrementalReadinessReport.IterationResult afterChange = iterations.stream()
                .filter(iteration -> "after-change".equals(iteration.id()))
                .findFirst()
                .orElseThrow();
        final boolean invalidationObserved = "invalidated".equals(afterChange.frontendStage())
                && "invalidated".equals(afterChange.loweringStage());
        final boolean allIterationsPassed = iterations.stream().allMatch(TsjSyntaxIncrementalReadinessReport.IterationResult::passed);
        final boolean gatePassed = allIterationsPassed
                && warmFrontendHitRatio >= MIN_WARM_FRONTEND_HIT_RATIO
                && warmLoweringHitRatio >= MIN_WARM_LOWERING_HIT_RATIO
                && invalidationObserved;

        final TsjSyntaxIncrementalReadinessReport report = new TsjSyntaxIncrementalReadinessReport(
                gatePassed,
                MIN_WARM_FRONTEND_HIT_RATIO,
                MIN_WARM_LOWERING_HIT_RATIO,
                warmFrontendHitRatio,
                warmLoweringHitRatio,
                invalidationObserved,
                iterations,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static TsjSyntaxIncrementalReadinessReport.IterationResult runCompileIteration(
            final String id,
            final Path entryFile,
            final Path outDir
    ) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final long started = System.nanoTime();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        final long durationMs = (System.nanoTime() - started) / 1_000_000L;
        final String stdoutText = stdout.toString(UTF_8);
        final String stderrText = stderr.toString(UTF_8);
        final String merged = stdoutText + "\n" + stderrText;
        final boolean passed = exitCode == 0 && stdoutText.contains("\"code\":\"TSJ-COMPILE-SUCCESS\"");
        return new TsjSyntaxIncrementalReadinessReport.IterationResult(
                id,
                passed,
                durationMs,
                firstMatch(CODE_PATTERN, merged),
                firstMatch(FRONTEND_STAGE_PATTERN, merged),
                firstMatch(LOWERING_STAGE_PATTERN, merged),
                firstMatch(BACKEND_STAGE_PATTERN, merged),
                "exit=" + exitCode
                        + ",stdout=" + trim(stdoutText, 180)
                        + ",stderr=" + trim(stderrText, 180)
        );
    }

    private static double stageRatio(
            final List<TsjSyntaxIncrementalReadinessReport.IterationResult> iterations,
            final String expected,
            final StageSelector selector
    ) {
        if (iterations.isEmpty()) {
            return 0.0d;
        }
        long matched = 0L;
        for (TsjSyntaxIncrementalReadinessReport.IterationResult iteration : iterations) {
            final String value = switch (selector) {
                case FRONTEND -> iteration.frontendStage();
                case LOWERING -> iteration.loweringStage();
            };
            if (expected.equals(value)) {
                matched++;
            }
        }
        return ((double) matched) / ((double) iterations.size());
    }

    private static String firstMatch(final Pattern pattern, final String text) {
        final Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private static String trim(final String text, final int maxChars) {
        final String safe = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSyntaxIncrementalReadinessHarness.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve(REPORT_FILE);
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target", REPORT_FILE).toAbsolutePath().normalize();
    }

    private static void writeReport(final Path reportPath, final TsjSyntaxIncrementalReadinessReport report)
            throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private enum StageSelector {
        FRONTEND,
        LOWERING
    }
}
