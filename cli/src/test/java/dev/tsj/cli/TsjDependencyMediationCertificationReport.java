package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-40d dependency mediation parity certification report.
 */
record TsjDependencyMediationCertificationReport(
        boolean gatePassed,
        List<GraphFixtureResult> graphFixtures,
        List<ScopePathResult> scopePaths,
        List<IsolationModeResult> isolationModes,
        Path reportPath,
        Path moduleReportPath
) {
    TsjDependencyMediationCertificationReport {
        graphFixtures = List.copyOf(Objects.requireNonNull(graphFixtures, "graphFixtures"));
        scopePaths = List.copyOf(Objects.requireNonNull(scopePaths, "scopePaths"));
        isolationModes = List.copyOf(Objects.requireNonNull(isolationModes, "isolationModes"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-40d-dependency-mediation-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"graphFixtures\":[");
        for (int index = 0; index < graphFixtures.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final GraphFixtureResult result = graphFixtures.get(index);
            builder.append("{\"fixture\":\"")
                    .append(escapeJson(result.fixture()))
                    .append("\",\"passed\":")
                    .append(result.passed())
                    .append(",\"selectedVersion\":\"")
                    .append(escapeJson(result.selectedVersion()))
                    .append("\",\"rejectedVersion\":\"")
                    .append(escapeJson(result.rejectedVersion()))
                    .append("\",\"rule\":\"")
                    .append(escapeJson(result.rule()))
                    .append("\",\"diagnosticCode\":\"")
                    .append(escapeJson(result.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(result.notes()))
                    .append("\"}");
        }
        builder.append("],\"scopePaths\":[");
        for (int index = 0; index < scopePaths.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ScopePathResult result = scopePaths.get(index);
            builder.append("{\"scopePath\":\"")
                    .append(escapeJson(result.scopePath()))
                    .append("\",\"passed\":")
                    .append(result.passed())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(result.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(result.notes()))
                    .append("\"}");
        }
        builder.append("],\"isolationModes\":[");
        for (int index = 0; index < isolationModes.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final IsolationModeResult result = isolationModes.get(index);
            builder.append("{\"mode\":\"")
                    .append(escapeJson(result.mode()))
                    .append("\",\"scenario\":\"")
                    .append(escapeJson(result.scenario()))
                    .append("\",\"passed\":")
                    .append(result.passed())
                    .append(",\"diagnosticCode\":\"")
                    .append(escapeJson(result.diagnosticCode()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(result.notes()))
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

    record GraphFixtureResult(
            String fixture,
            boolean passed,
            String selectedVersion,
            String rejectedVersion,
            String rule,
            String diagnosticCode,
            String notes
    ) {
        GraphFixtureResult {
            fixture = Objects.requireNonNull(fixture, "fixture");
            selectedVersion = Objects.requireNonNull(selectedVersion, "selectedVersion");
            rejectedVersion = Objects.requireNonNull(rejectedVersion, "rejectedVersion");
            rule = Objects.requireNonNull(rule, "rule");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }

    record ScopePathResult(
            String scopePath,
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
        ScopePathResult {
            scopePath = Objects.requireNonNull(scopePath, "scopePath");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }

    record IsolationModeResult(
            String mode,
            String scenario,
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
        IsolationModeResult {
            mode = Objects.requireNonNull(mode, "mode");
            scenario = Objects.requireNonNull(scenario, "scenario");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
