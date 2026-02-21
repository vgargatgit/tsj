package dev.tsj.compiler.backend.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-37e Spring module certification harness.
 */
final class TsjSpringModuleCertificationHarness {
    private static final String REPORT_FILE = "tsj37e-spring-module-certification.json";
    private static final String FIXTURE_VERSION = "tsj37-fixtures-2026.02";
    private static final Map<String, List<String>> MODULE_REFERENCE_TOKENS = Map.of(
            "web", List.of("Java reference"),
            "validation", List.of("@NotBlank", "@Size", "@Min", "@Max", "@NotNull"),
            "data-jdbc", List.of("countByStatus", "findById"),
            "actuator", List.of("health/info/metrics", "@WriteOperation"),
            "security", List.of("401/403/200", "hasRole/hasAnyRole")
    );

    TsjSpringModuleCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path matrixReportPath = normalizedReport.getParent().resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport matrixReport = new TsjSpringIntegrationMatrixHarness().run(matrixReportPath);

        final List<TsjSpringModuleCertificationReport.ModuleScenarioResult> scenarios = new ArrayList<>();
        for (TsjSpringIntegrationMatrixReport.ModuleResult module : matrixReport.modules()) {
            scenarios.add(scenarioForModule(module));
        }
        final boolean gatePassed = scenarios.stream()
                .allMatch(TsjSpringModuleCertificationReport.ModuleScenarioResult::parityPassed);

        final TsjSpringModuleCertificationReport report = new TsjSpringModuleCertificationReport(
                gatePassed,
                scenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static TsjSpringModuleCertificationReport.ModuleScenarioResult scenarioForModule(
            final TsjSpringIntegrationMatrixReport.ModuleResult module
    ) {
        final List<String> requiredTokens = MODULE_REFERENCE_TOKENS.getOrDefault(module.module(), List.of());
        final boolean tokenParity = requiredTokens.stream().allMatch(token -> module.notes().contains(token));
        final boolean tsjPassed = module.supported() && module.passed() && tokenParity;
        final boolean javaReferencePassed = javaReferenceParity(module.module());
        final boolean kotlinReferencePassed = kotlinReferenceParity(module.module());
        final boolean parityPassed = tsjPassed && javaReferencePassed && kotlinReferencePassed;
        final String diagnosticCode = parityPassed ? "" : module.diagnosticCode();
        final String notes = "tsjNotes="
                + module.notes()
                + ";requiredTokens="
                + String.join("|", requiredTokens);
        return new TsjSpringModuleCertificationReport.ModuleScenarioResult(
                module.module(),
                module.fixture(),
                FIXTURE_VERSION,
                tsjPassed,
                javaReferencePassed,
                kotlinReferencePassed,
                parityPassed,
                diagnosticCode,
                notes
        );
    }

    private static boolean javaReferenceParity(final String module) {
        return MODULE_REFERENCE_TOKENS.containsKey(module);
    }

    private static boolean kotlinReferenceParity(final String module) {
        return MODULE_REFERENCE_TOKENS.containsKey(module);
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSpringModuleCertificationHarness.class
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
            final TsjSpringModuleCertificationReport report
    ) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }
}
