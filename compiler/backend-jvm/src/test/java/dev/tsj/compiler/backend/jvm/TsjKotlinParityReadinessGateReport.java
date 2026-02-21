package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-38 readiness-gate report model.
 */
record TsjKotlinParityReadinessGateReport(
        boolean subsetReady,
        boolean fullParityReady,
        List<String> blockers,
        List<Criterion> criteria,
        Path reportPath,
        Path moduleReportPath
) {
    TsjKotlinParityReadinessGateReport {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-38-kotlin-parity-readiness\",");
        builder.append("\"subsetReady\":").append(subsetReady).append(",");
        builder.append("\"fullParityReady\":").append(fullParityReady).append(",");
        builder.append("\"blockers\":[");
        for (int index = 0; index < blockers.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(blockers.get(index))).append("\"");
        }
        builder.append("],\"criteria\":[");
        for (int index = 0; index < criteria.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final Criterion criterion = criteria.get(index);
            builder.append("{\"id\":\"")
                    .append(escapeJson(criterion.id()))
                    .append("\",\"passed\":")
                    .append(criterion.passed())
                    .append(",\"notes\":\"")
                    .append(escapeJson(criterion.notes()))
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

    record Criterion(String id, boolean passed, String notes) {
        Criterion {
            id = Objects.requireNonNull(id, "id");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
