package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-38c Kotlin parity certification report model.
 */
record TsjKotlinParityCertificationReport(
        boolean gatePassed,
        boolean fullParityReady,
        boolean dbParityPassed,
        boolean securityParityPassed,
        String fixtureVersion,
        List<DimensionResult> dimensions,
        Path reportPath,
        Path moduleReportPath
) {
    TsjKotlinParityCertificationReport {
        fixtureVersion = Objects.requireNonNull(fixtureVersion, "fixtureVersion");
        dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-38c-kotlin-parity-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"fullParityReady\":").append(fullParityReady).append(",");
        builder.append("\"dbParityPassed\":").append(dbParityPassed).append(",");
        builder.append("\"securityParityPassed\":").append(securityParityPassed).append(",");
        builder.append("\"fixtureVersion\":\"").append(escapeJson(fixtureVersion)).append("\",");
        builder.append("\"dimensions\":[");
        for (int index = 0; index < dimensions.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final DimensionResult dimension = dimensions.get(index);
            builder.append("{\"dimension\":\"")
                    .append(escapeJson(dimension.dimension()))
                    .append("\",\"passed\":")
                    .append(dimension.passed())
                    .append(",\"threshold\":\"")
                    .append(escapeJson(dimension.threshold()))
                    .append("\",\"observed\":\"")
                    .append(escapeJson(dimension.observed()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(dimension.notes()))
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

    record DimensionResult(
            String dimension,
            boolean passed,
            String threshold,
            String observed,
            String notes
    ) {
        DimensionResult {
            dimension = Objects.requireNonNull(dimension, "dimension");
            threshold = Objects.requireNonNull(threshold, "threshold");
            observed = Objects.requireNonNull(observed, "observed");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
