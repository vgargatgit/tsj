package dev.tsj.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-42d ORM compatibility closure certification harness.
 */
final class TsjJpaCertificationClosureHarness {
    private static final String REPORT_FILE = "tsj42d-jpa-certification.json";
    private static final String ORM_VERSION = "jpa-lite-1.0";
    private static final String FAMILY_REAL_DB = "real-db";
    private static final String FAMILY_LAZY_PROXY = "lazy-proxy";
    private static final String FAMILY_LIFECYCLE = "lifecycle-transaction";

    TsjJpaCertificationClosureReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj42d-certification-work");
        Files.createDirectories(workRoot);

        final TsjJpaRealDatabaseParityReport realDb = new TsjJpaRealDatabaseParityHarness().run(
                workRoot.resolve("tsj42a-jpa-realdb-parity.json")
        );
        final TsjJpaLazyProxyParityReport lazyProxy = new TsjJpaLazyProxyParityHarness().run(
                workRoot.resolve("tsj42b-jpa-lazy-proxy-parity.json")
        );
        final TsjJpaLifecycleParityReport lifecycle = new TsjJpaLifecycleParityHarness().run(
                workRoot.resolve("tsj42c-jpa-lifecycle-parity.json")
        );

        final List<TsjJpaCertificationClosureReport.FamilyScenarioResult> familyScenarios = new ArrayList<>();
        appendRealDbRows(familyScenarios, realDb);
        appendLazyProxyRows(familyScenarios, lazyProxy);
        appendLifecycleRows(familyScenarios, lifecycle);

        final boolean gatePassed = realDb.gatePassed()
                && lazyProxy.gatePassed()
                && lifecycle.gatePassed()
                && familyScenarios.stream().allMatch(TsjJpaCertificationClosureReport.FamilyScenarioResult::passed);

        final TsjJpaCertificationClosureReport report = new TsjJpaCertificationClosureReport(
                gatePassed,
                familyScenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static void appendRealDbRows(
            final List<TsjJpaCertificationClosureReport.FamilyScenarioResult> rows,
            final TsjJpaRealDatabaseParityReport report
    ) {
        for (TsjJpaRealDatabaseParityReport.BackendScenarioResult backend : report.backends()) {
            rows.add(new TsjJpaCertificationClosureReport.FamilyScenarioResult(
                    FAMILY_REAL_DB,
                    "backend:" + backend.backend(),
                    backend.ormVersion(),
                    backend.backend(),
                    backend.passed(),
                    backend.diagnosticCode(),
                    "dbVersion=" + backend.dbVersion() + ";" + backend.notes()
            ));
        }
        for (TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult scenario : report.diagnosticScenarios()) {
            rows.add(new TsjJpaCertificationClosureReport.FamilyScenarioResult(
                    FAMILY_REAL_DB,
                    "diag:" + scenario.scenario(),
                    ORM_VERSION,
                    scenario.backend(),
                    scenario.passed(),
                    scenario.observedDiagnosticCode(),
                    "expected=" + scenario.expectedDiagnosticCode() + ";" + scenario.notes()
            ));
        }
    }

    private static void appendLazyProxyRows(
            final List<TsjJpaCertificationClosureReport.FamilyScenarioResult> rows,
            final TsjJpaLazyProxyParityReport report
    ) {
        for (TsjJpaLazyProxyParityReport.SupportedScenarioResult scenario : report.supportedScenarios()) {
            rows.add(new TsjJpaCertificationClosureReport.FamilyScenarioResult(
                    FAMILY_LAZY_PROXY,
                    "supported:" + scenario.scenario(),
                    ORM_VERSION,
                    "fixture-lazy-proxy",
                    scenario.passed(),
                    "",
                    scenario.notes()
            ));
        }
        for (TsjJpaLazyProxyParityReport.DiagnosticScenarioResult scenario : report.diagnosticScenarios()) {
            rows.add(new TsjJpaCertificationClosureReport.FamilyScenarioResult(
                    FAMILY_LAZY_PROXY,
                    "diag:" + scenario.scenario(),
                    ORM_VERSION,
                    "fixture-lazy-proxy",
                    scenario.passed(),
                    scenario.observedDiagnosticCode(),
                    "expected=" + scenario.expectedDiagnosticCode() + ";" + scenario.notes()
            ));
        }
    }

    private static void appendLifecycleRows(
            final List<TsjJpaCertificationClosureReport.FamilyScenarioResult> rows,
            final TsjJpaLifecycleParityReport report
    ) {
        for (TsjJpaLifecycleParityReport.SupportedScenarioResult scenario : report.supportedScenarios()) {
            rows.add(new TsjJpaCertificationClosureReport.FamilyScenarioResult(
                    FAMILY_LIFECYCLE,
                    "supported:" + scenario.scenario(),
                    ORM_VERSION,
                    "fixture-lifecycle",
                    scenario.passed(),
                    "",
                    scenario.notes()
            ));
        }
        for (TsjJpaLifecycleParityReport.DiagnosticScenarioResult scenario : report.diagnosticScenarios()) {
            rows.add(new TsjJpaCertificationClosureReport.FamilyScenarioResult(
                    FAMILY_LIFECYCLE,
                    "diag:" + scenario.scenario(),
                    ORM_VERSION,
                    "fixture-lifecycle",
                    scenario.passed(),
                    scenario.observedDiagnosticCode(),
                    "expected=" + scenario.expectedDiagnosticCode() + ";" + scenario.notes()
            ));
        }
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjJpaCertificationClosureHarness.class
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

    private static void writeReport(final Path reportPath, final TsjJpaCertificationClosureReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }
}
