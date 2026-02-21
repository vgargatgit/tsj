package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-39b introspector compatibility matrix report model.
 */
record TsjIntrospectorCompatibilityMatrixReport(
        List<ScenarioResult> scenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjIntrospectorCompatibilityMatrixReport {
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-39b-introspector-compatibility-matrix\",\"scenarios\":[");
        for (int index = 0; index < scenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ScenarioResult scenario = scenarios.get(index);
            builder.append("{\"scenario\":\"")
                    .append(escapeJson(scenario.scenario()))
                    .append("\",\"fixture\":\"")
                    .append(escapeJson(scenario.fixture()))
                    .append("\",\"library\":\"")
                    .append(escapeJson(scenario.library()))
                    .append("\",\"version\":\"")
                    .append(escapeJson(scenario.version()))
                    .append("\",\"supported\":")
                    .append(scenario.supported())
                    .append(",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(scenario.diagnosticCode()))
                    .append("\",\"guidance\":\"")
                    .append(escapeJson(scenario.guidance()))
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

    record ScenarioResult(
            String scenario,
            String fixture,
            String library,
            String version,
            boolean supported,
            boolean passed,
            String diagnosticCode,
            String guidance,
            String notes
    ) {
        ScenarioResult {
            scenario = Objects.requireNonNull(scenario, "scenario");
            fixture = Objects.requireNonNull(fixture, "fixture");
            library = Objects.requireNonNull(library, "library");
            version = Objects.requireNonNull(version, "version");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            guidance = Objects.requireNonNull(guidance, "guidance");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
