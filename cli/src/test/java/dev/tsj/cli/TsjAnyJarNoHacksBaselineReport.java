package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-85 any-jar no-hacks baseline report.
 */
record TsjAnyJarNoHacksBaselineReport(
        boolean gatePassed,
        String fixtureVersion,
        List<ScenarioResult> scenarios,
        List<Blocker> blockers,
        Path reportPath,
        Path moduleReportPath
) {
    TsjAnyJarNoHacksBaselineReport {
        fixtureVersion = Objects.requireNonNull(fixtureVersion, "fixtureVersion");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-85-anyjar-nohacks-baseline\",");
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
                    .append(",\"expected\":\"")
                    .append(escapeJson(scenario.expected()))
                    .append("\",\"observed\":\"")
                    .append(escapeJson(scenario.observed()))
                    .append("\",\"diagnosticCode\":\"")
                    .append(escapeJson(scenario.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(scenario.notes()))
                    .append("\"}");
        }
        builder.append("],\"blockers\":[");
        for (int index = 0; index < blockers.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final Blocker blocker = blockers.get(index);
            builder.append("{\"blockerId\":\"")
                    .append(escapeJson(blocker.blockerId()))
                    .append("\",\"present\":")
                    .append(blocker.present())
                    .append(",\"evidence\":\"")
                    .append(escapeJson(blocker.evidence()))
                    .append("\",\"ownerPath\":\"")
                    .append(escapeJson(blocker.ownerPath()))
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

    record ScenarioResult(
            String scenario,
            boolean passed,
            String expected,
            String observed,
            String diagnosticCode,
            String notes
    ) {
        ScenarioResult {
            scenario = Objects.requireNonNull(scenario, "scenario");
            expected = Objects.requireNonNull(expected, "expected");
            observed = Objects.requireNonNull(observed, "observed");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }

    record Blocker(
            String blockerId,
            boolean present,
            String evidence,
            String ownerPath
    ) {
        Blocker {
            blockerId = Objects.requireNonNull(blockerId, "blockerId");
            evidence = Objects.requireNonNull(evidence, "evidence");
            ownerPath = Objects.requireNonNull(ownerPath, "ownerPath");
        }
    }
}
