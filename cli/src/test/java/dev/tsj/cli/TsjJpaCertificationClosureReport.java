package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-42d ORM compatibility certification closure report.
 */
record TsjJpaCertificationClosureReport(
        boolean gatePassed,
        List<FamilyScenarioResult> familyScenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjJpaCertificationClosureReport {
        familyScenarios = List.copyOf(Objects.requireNonNull(familyScenarios, "familyScenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-42d-jpa-certification-closure\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"familyScenarios\":[");
        for (int index = 0; index < familyScenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final FamilyScenarioResult row = familyScenarios.get(index);
            builder.append("{\"family\":\"")
                    .append(escapeJson(row.family()))
                    .append("\",\"scenario\":\"")
                    .append(escapeJson(row.scenario()))
                    .append("\",\"ormVersion\":\"")
                    .append(escapeJson(row.ormVersion()))
                    .append("\",\"backend\":\"")
                    .append(escapeJson(row.backend()))
                    .append("\",\"passed\":")
                    .append(row.passed())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(row.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(row.notes()))
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

    record FamilyScenarioResult(
            String family,
            String scenario,
            String ormVersion,
            String backend,
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
        FamilyScenarioResult {
            family = Objects.requireNonNull(family, "family");
            scenario = Objects.requireNonNull(scenario, "scenario");
            ormVersion = Objects.requireNonNull(ormVersion, "ormVersion");
            backend = Objects.requireNonNull(backend, "backend");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
