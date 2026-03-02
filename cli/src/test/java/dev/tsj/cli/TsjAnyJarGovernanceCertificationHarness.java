package dev.tsj.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.EventBus;
import com.zaxxer.hikari.HikariConfig;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.postgresql.Driver;
import org.yaml.snakeyaml.Yaml;

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
 * TSJ-44d governance/signoff closure harness for any-jar claims.
 */
final class TsjAnyJarGovernanceCertificationHarness {
    private static final String REPORT_FILE = "tsj44d-anyjar-governance.json";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern VERSION_FROM_JAR_PATTERN = Pattern.compile("-([0-9][A-Za-z0-9._-]*)\\.jar$");

    TsjAnyJarGovernanceCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj44d-governance-work");
        Files.createDirectories(workRoot);

        final MatrixGateResult matrixGate = runMatrixGate(workRoot.resolve("matrix-gate"));
        final TsjVersionRangeCertificationReport rangeReport = new TsjVersionRangeCertificationHarness().run(
                workRoot.resolve("tsj44b-version-range-certification.json")
        );
        final TsjRealAppCertificationReport realAppReport = new TsjRealAppCertificationHarness().run(
                workRoot.resolve("tsj44c-real-app-certification.json")
        );

        final List<TsjAnyJarGovernanceCertificationReport.SignoffCriterion> signoffCriteria = List.of(
                new TsjAnyJarGovernanceCertificationReport.SignoffCriterion(
                        "matrix-gate",
                        matrixGate.passed(),
                        matrixGate.notes()
                ),
                new TsjAnyJarGovernanceCertificationReport.SignoffCriterion(
                        "version-range-gate",
                        rangeReport.gatePassed(),
                        "driftDetected=" + rangeReport.driftDetected()
                ),
                new TsjAnyJarGovernanceCertificationReport.SignoffCriterion(
                        "real-app-gate",
                        realAppReport.gatePassed(),
                        "reliability=" + realAppReport.reliabilityBudgetPassed()
                                + ",performance=" + realAppReport.performanceBudgetPassed()
                )
        );
        final boolean gatePassed = signoffCriteria.stream().allMatch(TsjAnyJarGovernanceCertificationReport.SignoffCriterion::passed);
        final boolean signoffApproved = gatePassed;

        final List<TsjAnyJarGovernanceCertificationReport.ManifestEntry> manifest = new ArrayList<>();
        for (MatrixScenarioResult scenarioResult : matrixGate.results()) {
            manifest.add(new TsjAnyJarGovernanceCertificationReport.ManifestEntry(
                    scenarioResult.library(),
                    scenarioResult.version(),
                    "certified-subset",
                    "matrix-gate"
            ));
        }
        for (TsjVersionRangeCertificationReport.LibraryRangeResult library : rangeReport.libraries()) {
            manifest.add(new TsjAnyJarGovernanceCertificationReport.ManifestEntry(
                    library.library(),
                    library.certifiedRange(),
                    "certified-range",
                    "version-range-gate"
            ));
        }
        for (TsjRealAppCertificationReport.WorkloadResult workload : realAppReport.workloads()) {
            manifest.add(new TsjAnyJarGovernanceCertificationReport.ManifestEntry(
                    "workload:" + workload.workload(),
                    realAppReport.fixtureVersion(),
                    "certified-real-app",
                    "real-app-gate"
            ));
        }

        final TsjAnyJarGovernanceCertificationReport.RegressionPolicy regressionPolicy =
                new TsjAnyJarGovernanceCertificationReport.RegressionPolicy(
                        "rollback-on-certified-regression",
                        "pin-last-green-manifest",
                        "On any certified scenario failure, rollback release candidates and downgrade to last green manifest."
                );

        final TsjAnyJarGovernanceCertificationReport report = new TsjAnyJarGovernanceCertificationReport(
                gatePassed,
                signoffApproved,
                signoffCriteria,
                manifest,
                regressionPolicy,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static MatrixGateResult runMatrixGate(final Path matrixWorkDir) throws Exception {
        Files.createDirectories(matrixWorkDir);
        final List<MatrixScenario> scenarios = List.of(
                new MatrixScenario(
                        "flyway-version",
                        "org.flywaydb.core.api.MigrationVersion",
                        jarPathForClass(MigrationVersion.class),
                        versionFromJarName(jarPathForClass(MigrationVersion.class)),
                        """
                        import { fromVersion } from "java:org.flywaydb.core.api.MigrationVersion";
                        console.log("matrix-flyway=" + fromVersion("1.2.3"));
                        """,
                        "matrix-flyway=1.2.3"
                ),
                new MatrixScenario(
                        "postgres-driver",
                        "org.postgresql.Driver",
                        jarPathForClass(Driver.class),
                        versionFromJarName(jarPathForClass(Driver.class)),
                        """
                        import { isRegistered } from "java:org.postgresql.Driver";
                        console.log("matrix-postgres=" + isRegistered());
                        """,
                        "matrix-postgres=true"
                ),
                new MatrixScenario(
                        "jackson-objectmapper",
                        "com.fasterxml.jackson.databind.ObjectMapper",
                        jarPathForClass(ObjectMapper.class),
                        versionFromJarName(jarPathForClass(ObjectMapper.class)),
                        """
                        import { $new, $instance$writeValueAsString as writeValueAsString } from "java:com.fasterxml.jackson.databind.ObjectMapper";
                        const mapper = $new();
                        console.log("matrix-jackson=" + writeValueAsString(mapper, { id: 7 }));
                        """,
                        "matrix-jackson="
                ),
                new MatrixScenario(
                        "snakeyaml-load",
                        "org.yaml.snakeyaml.Yaml",
                        jarPathForClass(Yaml.class),
                        versionFromJarName(jarPathForClass(Yaml.class)),
                        """
                        import { $new, $instance$load as load } from "java:org.yaml.snakeyaml.Yaml";
                        const yaml = $new();
                        console.log("matrix-yaml=" + load(yaml, "broad"));
                        """,
                        "matrix-yaml=broad"
                ),
                new MatrixScenario(
                        "hikaricp-pool-name",
                        "com.zaxxer.hikari.HikariConfig",
                        jarPathForClass(HikariConfig.class),
                        versionFromJarName(jarPathForClass(HikariConfig.class)),
                        """
                        import { $new, $instance$setPoolName as setPoolName, $instance$getPoolName as getPoolName } from "java:com.zaxxer.hikari.HikariConfig";
                        const config = $new();
                        setPoolName(config, "tsj-governance");
                        console.log("matrix-hikari=" + getPoolName(config));
                        """,
                        "matrix-hikari=tsj-governance"
                ),
                new MatrixScenario(
                        "guava-eventbus",
                        "com.google.common.eventbus.EventBus",
                        jarPathForClass(EventBus.class),
                        versionFromJarName(jarPathForClass(EventBus.class)),
                        """
                        import { $new, $instance$post as post } from "java:com.google.common.eventbus.EventBus";
                        const bus = $new();
                        post(bus, "ping");
                        console.log("matrix-eventbus=posted");
                        """,
                        "matrix-eventbus=posted"
                ),
                new MatrixScenario(
                        "commons-lang3",
                        "org.apache.commons.lang3.StringUtils",
                        jarPathForClass(StringUtils.class),
                        versionFromJarName(jarPathForClass(StringUtils.class)),
                        """
                        import { isBlank } from "java:org.apache.commons.lang3.StringUtils";
                        console.log("matrix-lang3=" + isBlank("   "));
                        """,
                        "matrix-lang3=true"
                )
        );
        final List<MatrixScenarioResult> results = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        for (MatrixScenario scenario : scenarios) {
            final MatrixScenarioResult result = runMatrixScenario(matrixWorkDir, scenario);
            results.add(result);
            if (!result.passed()) {
                failures.add(scenario.id() + ":" + result.diagnosticCode());
            }
        }
        final boolean passed = failures.isEmpty();
        final long passedCount = results.stream().filter(MatrixScenarioResult::passed).count();
        final String notes = "scenarios=" + scenarios.size()
                + ",passed=" + passedCount
                + ",failed=" + (scenarios.size() - passedCount)
                + (failures.isEmpty() ? "" : ",failures=" + String.join("|", failures));
        return new MatrixGateResult(passed, notes, List.copyOf(results));
    }

    private static MatrixScenarioResult runMatrixScenario(
            final Path matrixWorkDir,
            final MatrixScenario scenario
    ) throws IOException {
        final Path scenarioDir = matrixWorkDir.resolve(scenario.id());
        Files.createDirectories(scenarioDir);
        final Path entryFile = scenarioDir.resolve("main.ts");
        Files.writeString(entryFile, scenario.tsSource(), UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final long started = System.nanoTime();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        scenarioDir.resolve("out").toString(),
                        "--jar",
                        scenario.jarPath().toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        final long durationMs = (System.nanoTime() - started) / 1_000_000L;
        final String stdoutText = stdout.toString(UTF_8);
        final String stderrText = stderr.toString(UTF_8);
        final boolean passed = exitCode == 0 && stdoutText.contains(scenario.expectedMarker());
        return new MatrixScenarioResult(
                scenario.library(),
                scenario.version(),
                passed,
                extractDiagnosticCode(stderrText),
                "id=" + scenario.id()
                        + ",exit=" + exitCode
                        + ",durationMs=" + durationMs
                        + ",expected=" + scenario.expectedMarker()
                        + ",stdout=" + trim(stdoutText, 180)
                        + ",stderr=" + trim(stderrText, 180)
        );
    }

    private static String extractDiagnosticCode(final String stderrText) {
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(stderrText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return stderrText.isBlank() ? "" : "NO_CODE";
    }

    private static Path jarPathForClass(final Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
        } catch (final Exception exception) {
            throw new IllegalStateException("Unable to resolve jar path for " + type.getName(), exception);
        }
    }

    private static String versionFromJarName(final Path jarPath) {
        final String fileName = jarPath.getFileName().toString();
        final Matcher matcher = VERSION_FROM_JAR_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown";
    }

    private static String trim(final String text, final int maxLength) {
        if (text == null) {
            return "";
        }
        final String normalized = text.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjAnyJarGovernanceCertificationHarness.class
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

    private static void writeReport(
            final Path reportPath,
            final TsjAnyJarGovernanceCertificationReport report
    ) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record MatrixScenario(
            String id,
            String library,
            Path jarPath,
            String version,
            String tsSource,
            String expectedMarker
    ) {
        MatrixScenario {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(library, "library");
            Objects.requireNonNull(jarPath, "jarPath");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(tsSource, "tsSource");
            Objects.requireNonNull(expectedMarker, "expectedMarker");
        }
    }

    private record MatrixScenarioResult(
            String library,
            String version,
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
        MatrixScenarioResult {
            Objects.requireNonNull(library, "library");
            Objects.requireNonNull(version, "version");
            diagnosticCode = diagnosticCode == null ? "" : diagnosticCode;
            notes = notes == null ? "" : notes;
        }
    }

    private record MatrixGateResult(boolean passed, String notes, List<MatrixScenarioResult> results) {
        MatrixGateResult {
            notes = notes == null ? "" : notes;
            results = List.copyOf(results);
        }
    }
}
