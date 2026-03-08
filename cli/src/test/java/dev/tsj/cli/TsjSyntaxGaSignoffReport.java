package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-70 syntax GA readiness signoff report.
 */
record TsjSyntaxGaSignoffReport(
        boolean gatePassed,
        boolean signoffApproved,
        Path readinessReportPath,
        Path compatibilityManifestPath,
        List<String> residualExclusions,
        List<SignoffCriterion> signoffCriteria,
        Path reportPath,
        Path moduleReportPath
) {
    TsjSyntaxGaSignoffReport {
        readinessReportPath = Objects.requireNonNull(readinessReportPath, "readinessReportPath");
        compatibilityManifestPath = Objects.requireNonNull(compatibilityManifestPath, "compatibilityManifestPath");
        residualExclusions = List.copyOf(Objects.requireNonNull(residualExclusions, "residualExclusions"));
        signoffCriteria = List.copyOf(Objects.requireNonNull(signoffCriteria, "signoffCriteria"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-70-syntax-ga-signoff\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"signoffApproved\":").append(signoffApproved).append(",");
        builder.append("\"readinessReport\":\"").append(escapeJson(readinessReportPath.toString())).append("\",");
        builder.append("\"compatibilityManifest\":\"").append(escapeJson(compatibilityManifestPath.toString())).append("\",");
        builder.append("\"residualExclusions\":[");
        for (int index = 0; index < residualExclusions.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(residualExclusions.get(index))).append("\"");
        }
        builder.append("],\"signoffCriteria\":[");
        for (int index = 0; index < signoffCriteria.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final SignoffCriterion criterion = signoffCriteria.get(index);
            builder.append("{\"criterion\":\"")
                    .append(escapeJson(criterion.criterion()))
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

    record SignoffCriterion(String criterion, boolean passed, String notes) {
        SignoffCriterion {
            criterion = Objects.requireNonNull(criterion, "criterion");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
