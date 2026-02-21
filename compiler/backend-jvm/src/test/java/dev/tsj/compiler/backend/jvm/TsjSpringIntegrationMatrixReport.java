package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-37 integration matrix report model.
 */
record TsjSpringIntegrationMatrixReport(
        List<ModuleResult> modules,
        Path reportPath,
        Path moduleReportPath
) {
    TsjSpringIntegrationMatrixReport {
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-37-spring-ecosystem-matrix\",\"modules\":[");
        for (int index = 0; index < modules.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ModuleResult module = modules.get(index);
            builder.append("{\"module\":\"")
                    .append(escapeJson(module.module()))
                    .append("\",\"starter\":\"")
                    .append(escapeJson(module.starter()))
                    .append("\",\"supported\":")
                    .append(module.supported())
                    .append(",\"passed\":")
                    .append(module.passed())
                    .append(",\"fixture\":\"")
                    .append(escapeJson(module.fixture()))
                    .append("\",\"diagnosticCode\":\"")
                    .append(escapeJson(module.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(module.notes()))
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

    record ModuleResult(
            String module,
            String starter,
            boolean supported,
            boolean passed,
            String fixture,
            String diagnosticCode,
            String notes
    ) {
        ModuleResult {
            module = Objects.requireNonNull(module, "module");
            starter = Objects.requireNonNull(starter, "starter");
            fixture = Objects.requireNonNull(fixture, "fixture");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
