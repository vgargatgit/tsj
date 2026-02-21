package dev.tsj.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-44d governance/signoff closure harness for any-jar claims.
 */
final class TsjAnyJarGovernanceCertificationHarness {
    private static final String REPORT_FILE = "tsj44d-anyjar-governance.json";

    TsjAnyJarGovernanceCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj44d-governance-work");
        Files.createDirectories(workRoot);

        final MatrixGateResult matrixGate = runMatrixGate();
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
        manifest.add(new TsjAnyJarGovernanceCertificationReport.ManifestEntry(
                "org.flywaydb.core.api.MigrationVersion",
                "10.17.3",
                "certified-subset",
                "matrix-gate"
        ));
        manifest.add(new TsjAnyJarGovernanceCertificationReport.ManifestEntry(
                "org.postgresql.Driver",
                "42.7.4",
                "certified-subset",
                "matrix-gate"
        ));
        manifest.add(new TsjAnyJarGovernanceCertificationReport.ManifestEntry(
                "com.fasterxml.jackson.databind.ObjectMapper",
                "2.17.2",
                "certified-subset",
                "matrix-gate"
        ));
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

    private static MatrixGateResult runMatrixGate() {
        final List<String> requiredClasses = List.of(
                "org.flywaydb.core.api.MigrationVersion",
                "org.postgresql.Driver",
                "com.fasterxml.jackson.databind.ObjectMapper",
                "org.yaml.snakeyaml.Yaml",
                "com.zaxxer.hikari.HikariConfig",
                "com.google.common.eventbus.EventBus",
                "org.apache.commons.lang3.StringUtils"
        );
        final List<String> missing = new ArrayList<>();
        for (String className : requiredClasses) {
            try {
                Class.forName(className);
            } catch (final ClassNotFoundException classNotFoundException) {
                missing.add(className);
            }
        }
        final boolean passed = missing.isEmpty();
        final String notes = passed ? "all matrix classes resolved" : "missing=" + String.join("|", missing);
        return new MatrixGateResult(passed, notes);
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

    private record MatrixGateResult(boolean passed, String notes) {
    }
}
