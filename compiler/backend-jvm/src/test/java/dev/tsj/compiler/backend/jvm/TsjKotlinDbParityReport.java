package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-38a DB-backed parity report model.
 */
record TsjKotlinDbParityReport(
        boolean gatePassed,
        List<BackendResult> backends,
        List<DiagnosticScenarioResult> diagnosticScenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjKotlinDbParityReport {
        backends = List.copyOf(Objects.requireNonNull(backends, "backends"));
        diagnosticScenarios = List.copyOf(Objects.requireNonNull(diagnosticScenarios, "diagnosticScenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-38a-db-parity\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"backends\":[");
        for (int index = 0; index < backends.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final BackendResult backend = backends.get(index);
            builder.append("{\"backend\":\"")
                    .append(escapeJson(backend.backend()))
                    .append("\",\"ormVersion\":\"")
                    .append(escapeJson(backend.ormVersion()))
                    .append("\",\"dbVersion\":\"")
                    .append(escapeJson(backend.dbVersion()))
                    .append("\",\"passed\":")
                    .append(backend.passed())
                    .append(",\"tsOutput\":\"")
                    .append(escapeJson(backend.tsOutput()))
                    .append("\",\"javaOutput\":\"")
                    .append(escapeJson(backend.javaOutput()))
                    .append("\",\"kotlinOutput\":\"")
                    .append(escapeJson(backend.kotlinOutput()))
                    .append("\",\"diagnosticCode\":\"")
                    .append(escapeJson(backend.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(backend.notes()))
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

    record BackendResult(
            String backend,
            String ormVersion,
            String dbVersion,
            boolean passed,
            String tsOutput,
            String javaOutput,
            String kotlinOutput,
            String diagnosticCode,
            String notes
    ) {
        BackendResult {
            backend = Objects.requireNonNull(backend, "backend");
            ormVersion = Objects.requireNonNull(ormVersion, "ormVersion");
            dbVersion = Objects.requireNonNull(dbVersion, "dbVersion");
            tsOutput = Objects.requireNonNull(tsOutput, "tsOutput");
            javaOutput = Objects.requireNonNull(javaOutput, "javaOutput");
            kotlinOutput = Objects.requireNonNull(kotlinOutput, "kotlinOutput");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
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
