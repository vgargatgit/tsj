package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-83 strict-mode conformance readiness report.
 */
record TsjStrictReadinessReport(
        boolean gatePassed,
        int totalFixtures,
        int passedFixtures,
        int failedFixtures,
        int strictOkTotal,
        int strictUnsupportedTotal,
        boolean serializationParityPassed,
        String serializationParityNotes,
        List<CategorySummary> categories,
        List<FixtureResult> fixtures,
        Path reportPath,
        Path moduleReportPath
) {
    TsjStrictReadinessReport {
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        fixtures = List.copyOf(Objects.requireNonNull(fixtures, "fixtures"));
        serializationParityNotes = Objects.requireNonNull(serializationParityNotes, "serializationParityNotes");
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-83-strict-readiness\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"totals\":{");
        builder.append("\"fixtures\":").append(totalFixtures).append(",");
        builder.append("\"passed\":").append(passedFixtures).append(",");
        builder.append("\"failed\":").append(failedFixtures).append(",");
        builder.append("\"strictOk\":").append(strictOkTotal).append(",");
        builder.append("\"strictUnsupported\":").append(strictUnsupportedTotal);
        builder.append("},");
        builder.append("\"serializationParity\":{");
        builder.append("\"passed\":").append(serializationParityPassed).append(",");
        builder.append("\"notes\":\"").append(escapeJson(serializationParityNotes)).append("\"");
        builder.append("},");
        builder.append("\"categories\":[");
        for (int index = 0; index < categories.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final CategorySummary category = categories.get(index);
            builder.append("{\"name\":\"")
                    .append(escapeJson(category.name()))
                    .append("\",\"total\":")
                    .append(category.total())
                    .append(",\"passed\":")
                    .append(category.passed())
                    .append(",\"failed\":")
                    .append(category.failed())
                    .append("}");
        }
        builder.append("],");
        builder.append("\"fixtures\":[");
        for (int index = 0; index < fixtures.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final FixtureResult fixture = fixtures.get(index);
            builder.append("{\"category\":\"")
                    .append(escapeJson(fixture.category()))
                    .append("\",\"fixture\":\"")
                    .append(escapeJson(fixture.fixture()))
                    .append("\",\"passed\":")
                    .append(fixture.passed())
                    .append(",\"expectedCode\":\"")
                    .append(escapeJson(fixture.expectedCode()))
                    .append("\",\"actualCode\":\"")
                    .append(escapeJson(fixture.actualCode()))
                    .append("\",\"expectedFeatureId\":\"")
                    .append(escapeJson(fixture.expectedFeatureId()))
                    .append("\",\"actualFeatureId\":\"")
                    .append(escapeJson(fixture.actualFeatureId()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(fixture.notes()))
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

    record CategorySummary(String name, int total, int passed, int failed) {
        CategorySummary {
            name = Objects.requireNonNull(name, "name");
        }
    }

    record FixtureResult(
            String category,
            String fixture,
            boolean passed,
            String expectedCode,
            String actualCode,
            String expectedFeatureId,
            String actualFeatureId,
            String notes
    ) {
        FixtureResult {
            category = Objects.requireNonNull(category, "category");
            fixture = Objects.requireNonNull(fixture, "fixture");
            expectedCode = Objects.requireNonNull(expectedCode, "expectedCode");
            actualCode = Objects.requireNonNull(actualCode, "actualCode");
            expectedFeatureId = Objects.requireNonNull(expectedFeatureId, "expectedFeatureId");
            actualFeatureId = Objects.requireNonNull(actualFeatureId, "actualFeatureId");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
