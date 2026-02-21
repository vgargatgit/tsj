package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-44d any-jar governance and release-signoff report.
 */
record TsjAnyJarGovernanceCertificationReport(
        boolean gatePassed,
        boolean signoffApproved,
        List<SignoffCriterion> signoffCriteria,
        List<ManifestEntry> compatibilityManifest,
        RegressionPolicy regressionPolicy,
        Path reportPath,
        Path moduleReportPath
) {
    TsjAnyJarGovernanceCertificationReport {
        signoffCriteria = List.copyOf(Objects.requireNonNull(signoffCriteria, "signoffCriteria"));
        compatibilityManifest = List.copyOf(Objects.requireNonNull(compatibilityManifest, "compatibilityManifest"));
        regressionPolicy = Objects.requireNonNull(regressionPolicy, "regressionPolicy");
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-44d-anyjar-governance\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"signoffApproved\":").append(signoffApproved).append(",");
        builder.append("\"signoffCriteria\":[");
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
        builder.append("],\"compatibilityManifest\":[");
        for (int index = 0; index < compatibilityManifest.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ManifestEntry entry = compatibilityManifest.get(index);
            builder.append("{\"library\":\"")
                    .append(escapeJson(entry.library()))
                    .append("\",\"version\":\"")
                    .append(escapeJson(entry.version()))
                    .append("\",\"supportTier\":\"")
                    .append(escapeJson(entry.supportTier()))
                    .append("\",\"sourceGate\":\"")
                    .append(escapeJson(entry.sourceGate()))
                    .append("\"}");
        }
        builder.append("],\"regressionPolicy\":{");
        builder.append("\"rollbackMode\":\"").append(escapeJson(regressionPolicy.rollbackMode())).append("\",");
        builder.append("\"downgradeMode\":\"").append(escapeJson(regressionPolicy.downgradeMode())).append("\",");
        builder.append("\"notes\":\"").append(escapeJson(regressionPolicy.notes())).append("\"");
        builder.append("}}");
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

    record ManifestEntry(String library, String version, String supportTier, String sourceGate) {
        ManifestEntry {
            library = Objects.requireNonNull(library, "library");
            version = Objects.requireNonNull(version, "version");
            supportTier = Objects.requireNonNull(supportTier, "supportTier");
            sourceGate = Objects.requireNonNull(sourceGate, "sourceGate");
        }
    }

    record RegressionPolicy(String rollbackMode, String downgradeMode, String notes) {
        RegressionPolicy {
            rollbackMode = Objects.requireNonNull(rollbackMode, "rollbackMode");
            downgradeMode = Objects.requireNonNull(downgradeMode, "downgradeMode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
