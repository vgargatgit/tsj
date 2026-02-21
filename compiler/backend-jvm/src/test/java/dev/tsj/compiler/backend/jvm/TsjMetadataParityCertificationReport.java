package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-39c metadata parity certification report model.
 */
record TsjMetadataParityCertificationReport(
        boolean gatePassed,
        List<FamilyResult> classFamilies,
        List<IntrospectorResult> introspectorScenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjMetadataParityCertificationReport {
        classFamilies = List.copyOf(Objects.requireNonNull(classFamilies, "classFamilies"));
        introspectorScenarios = List.copyOf(Objects.requireNonNull(introspectorScenarios, "introspectorScenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-39c-metadata-parity-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"classFamilies\":[");
        for (int index = 0; index < classFamilies.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final FamilyResult family = classFamilies.get(index);
            builder.append("{\"family\":\"")
                    .append(escapeJson(family.family()))
                    .append("\",\"passed\":")
                    .append(family.passed())
                    .append(",\"parameterMetadataPresent\":")
                    .append(family.parameterMetadataPresent())
                    .append(",\"annotationMetadataPresent\":")
                    .append(family.annotationMetadataPresent())
                    .append(",\"notes\":\"")
                    .append(escapeJson(family.notes()))
                    .append("\"}");
        }
        builder.append("],\"introspectorScenarios\":[");
        for (int index = 0; index < introspectorScenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final IntrospectorResult scenario = introspectorScenarios.get(index);
            builder.append("{\"scenario\":\"")
                    .append(escapeJson(scenario.scenario()))
                    .append("\",\"fixture\":\"")
                    .append(escapeJson(scenario.fixture()))
                    .append("\",\"library\":\"")
                    .append(escapeJson(scenario.library()))
                    .append("\",\"version\":\"")
                    .append(escapeJson(scenario.version()))
                    .append("\",\"supported\":")
                    .append(scenario.supported())
                    .append(",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(scenario.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(scenario.notes()))
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

    record FamilyResult(
            String family,
            boolean passed,
            boolean parameterMetadataPresent,
            boolean annotationMetadataPresent,
            String notes
    ) {
        FamilyResult {
            family = Objects.requireNonNull(family, "family");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }

    record IntrospectorResult(
            String scenario,
            String fixture,
            String library,
            String version,
            boolean supported,
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
        IntrospectorResult {
            scenario = Objects.requireNonNull(scenario, "scenario");
            fixture = Objects.requireNonNull(fixture, "fixture");
            library = Objects.requireNonNull(library, "library");
            version = Objects.requireNonNull(version, "version");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
