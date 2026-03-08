package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-84 strict-mode release readiness report.
 */
record TsjStrictReleaseReadinessReport(
        boolean gatePassed,
        boolean releaseApproved,
        Path strictReadinessReportPath,
        Path strictGuidePath,
        Path strictReleaseChecklistPath,
        List<String> residualExclusions,
        List<ReleaseCriterion> criteria,
        Path reportPath,
        Path moduleReportPath
) {
    TsjStrictReleaseReadinessReport {
        strictReadinessReportPath = Objects.requireNonNull(strictReadinessReportPath, "strictReadinessReportPath");
        strictGuidePath = Objects.requireNonNull(strictGuidePath, "strictGuidePath");
        strictReleaseChecklistPath = Objects.requireNonNull(strictReleaseChecklistPath, "strictReleaseChecklistPath");
        residualExclusions = List.copyOf(Objects.requireNonNull(residualExclusions, "residualExclusions"));
        criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-84-strict-release-readiness\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"releaseApproved\":").append(releaseApproved).append(",");
        builder.append("\"strictReadinessReport\":\"")
                .append(escapeJson(strictReadinessReportPath.toString()))
                .append("\",");
        builder.append("\"strictGuide\":\"")
                .append(escapeJson(strictGuidePath.toString()))
                .append("\",");
        builder.append("\"strictReleaseChecklist\":\"")
                .append(escapeJson(strictReleaseChecklistPath.toString()))
                .append("\",");
        builder.append("\"residualExclusions\":[");
        for (int index = 0; index < residualExclusions.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(residualExclusions.get(index))).append("\"");
        }
        builder.append("],\"criteria\":[");
        for (int index = 0; index < criteria.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ReleaseCriterion criterion = criteria.get(index);
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

    record ReleaseCriterion(String criterion, boolean passed, String notes) {
        ReleaseCriterion {
            criterion = Objects.requireNonNull(criterion, "criterion");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
