package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-42b lazy/proxy parity report.
 */
record TsjJpaLazyProxyParityReport(
        boolean gatePassed,
        List<SupportedScenarioResult> supportedScenarios,
        List<DiagnosticScenarioResult> diagnosticScenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjJpaLazyProxyParityReport {
        supportedScenarios = List.copyOf(Objects.requireNonNull(supportedScenarios, "supportedScenarios"));
        diagnosticScenarios = List.copyOf(Objects.requireNonNull(diagnosticScenarios, "diagnosticScenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-42b-jpa-lazy-proxy-parity\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"supportedScenarios\":[");
        for (int index = 0; index < supportedScenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final SupportedScenarioResult scenario = supportedScenarios.get(index);
            builder.append("{\"scenario\":\"")
                    .append(escapeJson(scenario.scenario()))
                    .append("\",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"tsOutput\":\"")
                    .append(escapeJson(scenario.tsOutput()))
                    .append("\",\"javaOutput\":\"")
                    .append(escapeJson(scenario.javaOutput()))
                    .append("\",\"kotlinOutput\":\"")
                    .append(escapeJson(scenario.kotlinOutput()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(scenario.notes()))
                    .append("\"}");
        }
        builder.append("],\"diagnosticScenarios\":[");
        for (int index = 0; index < diagnosticScenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final DiagnosticScenarioResult scenario = diagnosticScenarios.get(index);
            builder.append("{\"scenario\":\"")
                    .append(escapeJson(scenario.scenario()))
                    .append("\",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"expectedDiagnosticCode\":\"")
                    .append(escapeJson(scenario.expectedDiagnosticCode()))
                    .append("\",\"observedDiagnosticCode\":\"")
                    .append(escapeJson(scenario.observedDiagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(scenario.notes()))
                    .append("\"}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    record SupportedScenarioResult(
            String scenario,
            boolean passed,
            String tsOutput,
            String javaOutput,
            String kotlinOutput,
            String notes
    ) {
        SupportedScenarioResult {
            scenario = Objects.requireNonNull(scenario, "scenario");
            tsOutput = Objects.requireNonNull(tsOutput, "tsOutput");
            javaOutput = Objects.requireNonNull(javaOutput, "javaOutput");
            kotlinOutput = Objects.requireNonNull(kotlinOutput, "kotlinOutput");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }

    record DiagnosticScenarioResult(
            String scenario,
            boolean passed,
            String expectedDiagnosticCode,
            String observedDiagnosticCode,
            String notes
    ) {
        DiagnosticScenarioResult {
            scenario = Objects.requireNonNull(scenario, "scenario");
            expectedDiagnosticCode = Objects.requireNonNull(expectedDiagnosticCode, "expectedDiagnosticCode");
            observedDiagnosticCode = Objects.requireNonNull(observedDiagnosticCode, "observedDiagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
