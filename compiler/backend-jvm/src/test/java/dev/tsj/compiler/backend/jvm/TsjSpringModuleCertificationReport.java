package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-37e Spring module certification report.
 */
record TsjSpringModuleCertificationReport(
        boolean gatePassed,
        List<ModuleScenarioResult> moduleScenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjSpringModuleCertificationReport {
        moduleScenarios = List.copyOf(Objects.requireNonNull(moduleScenarios, "moduleScenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-37e-spring-module-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"moduleScenarios\":[");
        for (int index = 0; index < moduleScenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ModuleScenarioResult scenario = moduleScenarios.get(index);
            builder.append("{\"module\":\"")
                    .append(escapeJson(scenario.module()))
                    .append("\",\"fixture\":\"")
                    .append(escapeJson(scenario.fixture()))
                    .append("\",\"fixtureVersion\":\"")
                    .append(escapeJson(scenario.fixtureVersion()))
                    .append("\",\"tsjPassed\":")
                    .append(scenario.tsjPassed())
                    .append(",\"javaReferencePassed\":")
                    .append(scenario.javaReferencePassed())
                    .append(",\"kotlinReferencePassed\":")
                    .append(scenario.kotlinReferencePassed())
                    .append(",\"parityPassed\":")
                    .append(scenario.parityPassed())
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

    record ModuleScenarioResult(
            String module,
            String fixture,
            String fixtureVersion,
            boolean tsjPassed,
            boolean javaReferencePassed,
            boolean kotlinReferencePassed,
            boolean parityPassed,
            String diagnosticCode,
            String notes
    ) {
        ModuleScenarioResult {
            module = Objects.requireNonNull(module, "module");
            fixture = Objects.requireNonNull(fixture, "fixture");
            fixtureVersion = Objects.requireNonNull(fixtureVersion, "fixtureVersion");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
