package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-75 any-jar annotation-survival certification report model.
 */
record TsjAnyJarAnnotationSurvivalCertificationReport(
        boolean gatePassed,
        String fixtureVersion,
        List<DimensionResult> dimensions,
        Path reportPath,
        Path moduleReportPath
) {
    TsjAnyJarAnnotationSurvivalCertificationReport {
        fixtureVersion = Objects.requireNonNull(fixtureVersion, "fixtureVersion");
        dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-75-anyjar-annotation-survival-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
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
                    .append(",\"expected\":\"")
                    .append(escapeJson(dimension.expected()))
                    .append("\",\"observed\":\"")
                    .append(escapeJson(dimension.observed()))
                    .append("\",\"diagnosticCode\":\"")
                    .append(escapeJson(dimension.diagnosticCode()))
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
            String expected,
            String observed,
            String diagnosticCode,
            String notes
    ) {
        DimensionResult {
            dimension = Objects.requireNonNull(dimension, "dimension");
            expected = Objects.requireNonNull(expected, "expected");
            observed = Objects.requireNonNull(observed, "observed");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
