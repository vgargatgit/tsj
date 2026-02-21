package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-33f differential parity report model for DI/lifecycle subset.
 */
record TsjSpringDiLifecycleParityReport(
        List<ScenarioResult> scenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjSpringDiLifecycleParityReport {
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-33f-di-lifecycle-parity\",\"scenarios\":[");
        for (int index = 0; index < scenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ScenarioResult scenario = scenarios.get(index);
            builder.append("{\"scenario\":\"")
                    .append(escapeJson(scenario.scenario()))
                    .append("\",\"fixture\":\"")
                    .append(escapeJson(scenario.fixture()))
                    .append("\",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(scenario.diagnosticCode()))
                    .append("\",\"tsValue\":\"")
                    .append(escapeJson(scenario.tsValue()))
                    .append("\",\"javaValue\":\"")
                    .append(escapeJson(scenario.javaValue()))
                    .append("\",\"kotlinValue\":\"")
                    .append(escapeJson(scenario.kotlinValue()))
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
            boolean passed,
            String diagnosticCode,
            String tsValue,
            String javaValue,
            String kotlinValue,
            String notes
    ) {
        ScenarioResult {
            scenario = Objects.requireNonNull(scenario, "scenario");
            fixture = Objects.requireNonNull(fixture, "fixture");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            tsValue = Objects.requireNonNull(tsValue, "tsValue");
            javaValue = Objects.requireNonNull(javaValue, "javaValue");
            kotlinValue = Objects.requireNonNull(kotlinValue, "kotlinValue");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
