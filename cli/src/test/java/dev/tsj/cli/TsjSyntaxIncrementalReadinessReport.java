package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * TSJ-69 incremental syntax pipeline readiness report.
 */
record TsjSyntaxIncrementalReadinessReport(
        boolean gatePassed,
        double minWarmFrontendHitRatio,
        double minWarmLoweringHitRatio,
        double warmFrontendHitRatio,
        double warmLoweringHitRatio,
        boolean invalidationObserved,
        List<IterationResult> iterations,
        Path reportPath,
        Path moduleReportPath
) {
    TsjSyntaxIncrementalReadinessReport {
        iterations = List.copyOf(Objects.requireNonNull(iterations, "iterations"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-69-incremental-readiness\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"thresholds\":{");
        builder.append("\"minWarmFrontendHitRatio\":").append(formatRatio(minWarmFrontendHitRatio)).append(",");
        builder.append("\"minWarmLoweringHitRatio\":").append(formatRatio(minWarmLoweringHitRatio));
        builder.append("},");
        builder.append("\"observed\":{");
        builder.append("\"warmFrontendHitRatio\":").append(formatRatio(warmFrontendHitRatio)).append(",");
        builder.append("\"warmLoweringHitRatio\":").append(formatRatio(warmLoweringHitRatio)).append(",");
        builder.append("\"invalidationObserved\":").append(invalidationObserved);
        builder.append("},");
        builder.append("\"iterations\":[");
        for (int index = 0; index < iterations.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final IterationResult iteration = iterations.get(index);
            builder.append("{\"id\":\"")
                    .append(escapeJson(iteration.id()))
                    .append("\",\"passed\":")
                    .append(iteration.passed())
                    .append(",\"durationMs\":")
                    .append(iteration.durationMs())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(iteration.diagnosticCode()))
                    .append("\",\"frontend\":\"")
                    .append(escapeJson(iteration.frontendStage()))
                    .append("\",\"lowering\":\"")
                    .append(escapeJson(iteration.loweringStage()))
                    .append("\",\"backend\":\"")
                    .append(escapeJson(iteration.backendStage()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(iteration.notes()))
                    .append("\"}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String formatRatio(final double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    record IterationResult(
            String id,
            boolean passed,
            long durationMs,
            String diagnosticCode,
            String frontendStage,
            String loweringStage,
            String backendStage,
            String notes
    ) {
        IterationResult {
            id = Objects.requireNonNull(id, "id");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            frontendStage = Objects.requireNonNull(frontendStage, "frontendStage");
            loweringStage = Objects.requireNonNull(loweringStage, "loweringStage");
            backendStage = Objects.requireNonNull(backendStage, "backendStage");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
