package dev.tsj.compiler.backend.jvm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-38c full readiness-gate certification harness.
 */
final class TsjKotlinParityCertificationHarness {
    private static final String REPORT_FILE = "tsj38c-kotlin-parity-certification.json";
    private static final String FIXTURE_VERSION = "tsj38-fixtures-2026.02";
    private static final long STARTUP_MAX_MS = 5_000L;
    private static final double THROUGHPUT_MIN_OPS_PER_SEC = 1_000.0d;
    private static final int THROUGHPUT_OPERATIONS = 120_000;
    private static final int DIAGNOSTIC_EXPECTED_PASS_COUNT = 5;

    TsjKotlinParityCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj38c-parity-certification-work");
        Files.createDirectories(workRoot);

        final TsjKotlinDbParityReport dbReport = new TsjKotlinDbParityHarness().run(
                workRoot.resolve("tsj38a-db-parity-report.json")
        );
        final TsjKotlinSecurityParityReport securityReport = new TsjKotlinSecurityParityHarness().run(
                workRoot.resolve("tsj38b-security-parity-report.json")
        );
        final TsjKotlinParityReadinessGateReport readinessReport = new TsjKotlinParityReadinessGateHarness().run(
                workRoot.resolve("tsj38-kotlin-parity-readiness.json")
        );

        final ProbeResult startupProbe = runStartupProbe(workRoot.resolve("startup-probe"));
        final ThroughputProbeResult throughputProbe = runThroughputProbe(workRoot.resolve("throughput-probe"));
        final DiagnosticQualityResult diagnosticQuality = assessDiagnosticQuality(dbReport, securityReport);

        final List<TsjKotlinParityCertificationReport.DimensionResult> dimensions = new ArrayList<>();
        final boolean correctnessPassed = dbReport.gatePassed() && securityReport.gatePassed() && readinessReport.subsetReady();
        dimensions.add(new TsjKotlinParityCertificationReport.DimensionResult(
                "correctness",
                correctnessPassed,
                "db=true && security=true && subsetReady=true",
                "db=" + dbReport.gatePassed()
                        + ",security=" + securityReport.gatePassed()
                        + ",subsetReady=" + readinessReport.subsetReady(),
                "Aggregates TSJ-38a/38b parity gates and readiness subset signal."
        ));
        dimensions.add(new TsjKotlinParityCertificationReport.DimensionResult(
                "startup-time-ms",
                startupProbe.passed() && startupProbe.durationMs() <= STARTUP_MAX_MS,
                "<=" + STARTUP_MAX_MS + "ms",
                startupProbe.durationMs() + "ms",
                startupProbe.notes()
        ));
        dimensions.add(new TsjKotlinParityCertificationReport.DimensionResult(
                "throughput-ops-per-sec",
                throughputProbe.passed() && throughputProbe.opsPerSecond() >= THROUGHPUT_MIN_OPS_PER_SEC,
                ">=" + THROUGHPUT_MIN_OPS_PER_SEC,
                String.format("%.2f", throughputProbe.opsPerSecond()),
                throughputProbe.notes()
        ));
        dimensions.add(new TsjKotlinParityCertificationReport.DimensionResult(
                "diagnostics-quality",
                diagnosticQuality.passed(),
                ">=" + DIAGNOSTIC_EXPECTED_PASS_COUNT + " passed scenarios with stable codes",
                "passed=" + diagnosticQuality.passedCount() + "/" + diagnosticQuality.totalCount(),
                diagnosticQuality.notes()
        ));

        final boolean gatePassed = dimensions.stream().allMatch(TsjKotlinParityCertificationReport.DimensionResult::passed);
        final boolean fullParityReady = gatePassed && dbReport.gatePassed() && securityReport.gatePassed();
        final TsjKotlinParityCertificationReport report = new TsjKotlinParityCertificationReport(
                gatePassed,
                fullParityReady,
                dbReport.gatePassed(),
                securityReport.gatePassed(),
                FIXTURE_VERSION,
                dimensions,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private ProbeResult runStartupProbe(final Path workDir) throws IOException {
        Files.createDirectories(workDir);
        final Path source = workDir.resolve("startup.ts");
        Files.writeString(source, "console.log(\"boot=ok\");\n", UTF_8);
        final long started = System.nanoTime();
        final ProgramRunResult run = compileAndRunTs(source, workDir.resolve("program"));
        final long durationMs = (System.nanoTime() - started) / 1_000_000L;
        final boolean passed = run.failure() == null && run.stderr().isBlank() && run.stdout().contains("boot=ok");
        final String notes = "stdout=" + trim(run.stdout(), 120)
                + ",stderr=" + trim(run.stderr(), 120)
                + ",failure=" + trim(renderFailure(run.failure()), 120);
        return new ProbeResult(durationMs, passed, notes);
    }

    private ThroughputProbeResult runThroughputProbe(final Path workDir) throws IOException {
        Files.createDirectories(workDir);
        final Path source = workDir.resolve("throughput.ts");
        Files.writeString(
                source,
                """
                let total = 0;
                let i = 0;
                while (i < 120000) {
                    total = total + 1;
                    i = i + 1;
                }
                console.log("sum=" + total);
                """,
                UTF_8
        );
        final long started = System.nanoTime();
        final ProgramRunResult run = compileAndRunTs(source, workDir.resolve("program"));
        final long durationMs = (System.nanoTime() - started) / 1_000_000L;
        final double elapsedSeconds = Math.max(0.001d, durationMs / 1000.0d);
        final double opsPerSecond = THROUGHPUT_OPERATIONS / elapsedSeconds;
        final boolean passed = run.failure() == null
                && run.stderr().isBlank()
                && run.stdout().contains("sum=" + THROUGHPUT_OPERATIONS);
        final String notes = "durationMs=" + durationMs
                + ",stdout=" + trim(run.stdout(), 120)
                + ",stderr=" + trim(run.stderr(), 120)
                + ",failure=" + trim(renderFailure(run.failure()), 120);
        return new ThroughputProbeResult(opsPerSecond, passed, notes);
    }

    private static DiagnosticQualityResult assessDiagnosticQuality(
            final TsjKotlinDbParityReport dbReport,
            final TsjKotlinSecurityParityReport securityReport
    ) {
        int total = 0;
        int passed = 0;
        final StringBuilder codes = new StringBuilder();

        for (TsjKotlinDbParityReport.DiagnosticScenarioResult scenario : dbReport.diagnosticScenarios()) {
            total++;
            if (scenario.passed() && !scenario.observedDiagnosticCode().isBlank()) {
                passed++;
            }
            if (codes.length() > 0) {
                codes.append("|");
            }
            codes.append(scenario.observedDiagnosticCode());
        }
        for (TsjKotlinSecurityParityReport.DiagnosticScenarioResult scenario : securityReport.diagnosticScenarios()) {
            total++;
            if (scenario.passed() && !scenario.observedDiagnosticCode().isBlank()) {
                passed++;
            }
            if (codes.length() > 0) {
                codes.append("|");
            }
            codes.append(scenario.observedDiagnosticCode());
        }

        final boolean passedGate = passed >= DIAGNOSTIC_EXPECTED_PASS_COUNT;
        return new DiagnosticQualityResult(passedGate, total, passed, "codes=" + codes);
    }

    private ProgramRunResult compileAndRunTs(final Path tsFile, final Path outputDir) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Throwable failure = null;
        try {
            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(tsFile, outputDir);
            new JvmBytecodeRunner().run(
                    artifact,
                    List.of(),
                    new PrintStream(stdout),
                    new PrintStream(stderr)
            );
        } catch (final Throwable throwable) {
            failure = throwable;
        }
        return new ProgramRunResult(stdout.toString(UTF_8), stderr.toString(UTF_8), failure);
    }

    private static String trim(final String text, final int maxChars) {
        final String safe = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static String renderFailure(final Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        return throwable.getClass().getSimpleName() + ":" + Objects.toString(throwable.getMessage(), "");
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjKotlinParityCertificationHarness.class
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

    private static void writeReport(final Path reportPath, final TsjKotlinParityCertificationReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record ProgramRunResult(String stdout, String stderr, Throwable failure) {
    }

    private record ProbeResult(long durationMs, boolean passed, String notes) {
    }

    private record ThroughputProbeResult(double opsPerSecond, boolean passed, String notes) {
    }

    private record DiagnosticQualityResult(boolean passed, int totalCount, int passedCount, String notes) {
    }
}
