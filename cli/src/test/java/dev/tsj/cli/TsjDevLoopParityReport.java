package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-36c dev-loop parity report.
 */
record TsjDevLoopParityReport(
        boolean gatePassed,
        List<ScenarioResult> scenarios,
        List<String> workflowHints,
        List<String> nonGoals,
        Path reportPath,
        Path moduleReportPath
) {
    TsjDevLoopParityReport {
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        workflowHints = List.copyOf(Objects.requireNonNull(workflowHints, "workflowHints"));
        nonGoals = List.copyOf(Objects.requireNonNull(nonGoals, "nonGoals"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-36c-dev-loop-parity\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"scenarios\":[");
        for (int index = 0; index < scenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ScenarioResult scenario = scenarios.get(index);
            builder.append("{\"id\":\"")
                    .append(escapeJson(scenario.id()))
                    .append("\",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"durationMs\":")
                    .append(scenario.durationMs())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(scenario.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(scenario.notes()))
                    .append("\"}");
        }
        builder.append("],\"workflowHints\":[");
        for (int index = 0; index < workflowHints.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(workflowHints.get(index))).append("\"");
        }
        builder.append("],\"nonGoals\":[");
        for (int index = 0; index < nonGoals.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(nonGoals.get(index))).append("\"");
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
            String id,
            boolean passed,
            long durationMs,
            String diagnosticCode,
            String notes
    ) {
        ScenarioResult {
            id = Objects.requireNonNull(id, "id");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
