package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-92 final no-hacks any-jar certification report.
 */
record TsjAnyJarNoHacksCertificationReport(
        boolean gatePassed,
        String fixtureVersion,
        List<ScenarioResult> scenarios,
        Path reportPath,
        Path moduleReportPath
) {
    TsjAnyJarNoHacksCertificationReport {
        fixtureVersion = Objects.requireNonNull(fixtureVersion, "fixtureVersion");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-92-anyjar-nohacks-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"fixtureVersion\":\"").append(escapeJson(fixtureVersion)).append("\",");
        builder.append("\"scenarios\":[");
        for (int index = 0; index < scenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ScenarioResult scenario = scenarios.get(index);
            builder.append("{\"scenario\":\"")
                    .append(escapeJson(scenario.scenario()))
                    .append("\",\"passed\":")
                    .append(scenario.passed())
                    .append(",\"notes\":\"")
                    .append(escapeJson(scenario.notes()))
                    .append("\",\"artifact\":\"")
                    .append(escapeJson(scenario.artifact()))
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

    record ScenarioResult(String scenario, boolean passed, String notes, String artifact) {
        ScenarioResult {
            scenario = Objects.requireNonNull(scenario, "scenario");
            notes = Objects.requireNonNull(notes, "notes");
            artifact = Objects.requireNonNull(artifact, "artifact");
        }
    }
}
