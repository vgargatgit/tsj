package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-41d invocation/conversion parity certification report.
 */
record TsjInvocationConversionCertificationReport(
        boolean gatePassed,
        List<FamilySummary> families,
        List<ScenarioResult> scenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjInvocationConversionCertificationReport {
        families = List.copyOf(Objects.requireNonNull(families, "families"));
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-41d-invocation-conversion-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"families\":[");
        for (int index = 0; index < families.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final FamilySummary family = families.get(index);
            builder.append("{\"family\":\"")
                    .append(escapeJson(family.family()))
                    .append("\",\"passed\":")
                    .append(family.passed())
                    .append(",\"scenarioCount\":")
                    .append(family.scenarioCount())
                    .append(",\"passedCount\":")
                    .append(family.passedCount())
                    .append("}");
        }
        builder.append("],\"scenarios\":[");
        for (int index = 0; index < scenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ScenarioResult scenario = scenarios.get(index);
            builder.append("{\"family\":\"")
                    .append(escapeJson(scenario.family()))
                    .append("\",\"scenario\":\"")
                    .append(escapeJson(scenario.scenario()))
                    .append("\",\"fixture\":\"")
                    .append(escapeJson(scenario.fixture()))
                    .append("\",\"library\":\"")
                    .append(escapeJson(scenario.library()))
                    .append("\",\"version\":\"")
                    .append(escapeJson(scenario.version()))
                    .append("\",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(scenario.diagnosticCode()))
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

    record FamilySummary(
            String family,
            boolean passed,
            int scenarioCount,
            int passedCount
    ) {
        FamilySummary {
            family = Objects.requireNonNull(family, "family");
        }
    }

    record ScenarioResult(
            String family,
            String scenario,
            String fixture,
            String library,
            String version,
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
        ScenarioResult {
            family = Objects.requireNonNull(family, "family");
            scenario = Objects.requireNonNull(scenario, "scenario");
            fixture = Objects.requireNonNull(fixture, "fixture");
            library = Objects.requireNonNull(library, "library");
            version = Objects.requireNonNull(version, "version");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
