package dev.tsj.cli.fixtures;

import java.util.List;
import java.util.Objects;

/**
 * Coverage summary for differential fixture suite runs.
 */
public record FixtureCoverageReport(
        int totalFixtures,
        int passedFixtures,
        int failedFixtures,
        List<FeatureCoverage> byFeature
) {
    public FixtureCoverageReport {
        byFeature = List.copyOf(Objects.requireNonNull(byFeature, "byFeature"));
    }

    public String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"totalFixtures\":").append(totalFixtures).append(",");
        builder.append("\"passedFixtures\":").append(passedFixtures).append(",");
        builder.append("\"failedFixtures\":").append(failedFixtures).append(",");
        builder.append("\"features\":[");
        for (int index = 0; index < byFeature.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final FeatureCoverage coverage = byFeature.get(index);
            builder.append("{");
            builder.append("\"feature\":\"").append(escapeJson(coverage.feature())).append("\",");
            builder.append("\"total\":").append(coverage.total()).append(",");
            builder.append("\"passed\":").append(coverage.passed()).append(",");
            builder.append("\"failed\":").append(coverage.failed());
            builder.append("}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record FeatureCoverage(
            String feature,
            int total,
            int passed,
            int failed
    ) {
    }
}
