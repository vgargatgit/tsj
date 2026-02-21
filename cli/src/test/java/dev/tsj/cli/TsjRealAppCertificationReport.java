package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-44c real-application certification report.
 */
record TsjRealAppCertificationReport(
        boolean gatePassed,
        boolean reliabilityBudgetPassed,
        boolean performanceBudgetPassed,
        long maxAverageDurationMs,
        long maxWorkloadDurationMs,
        String fixtureVersion,
        List<WorkloadResult> workloads,
        Path reportPath,
        Path moduleReportPath
) {
    TsjRealAppCertificationReport {
        fixtureVersion = Objects.requireNonNull(fixtureVersion, "fixtureVersion");
        workloads = List.copyOf(Objects.requireNonNull(workloads, "workloads"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-44c-real-app-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"reliabilityBudgetPassed\":").append(reliabilityBudgetPassed).append(",");
        builder.append("\"performanceBudgetPassed\":").append(performanceBudgetPassed).append(",");
        builder.append("\"maxAverageDurationMs\":").append(maxAverageDurationMs).append(",");
        builder.append("\"maxWorkloadDurationMs\":").append(maxWorkloadDurationMs).append(",");
        builder.append("\"fixtureVersion\":\"").append(escapeJson(fixtureVersion)).append("\",");
        builder.append("\"workloads\":[");
        for (int index = 0; index < workloads.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final WorkloadResult workload = workloads.get(index);
            builder.append("{\"workload\":\"")
                    .append(escapeJson(workload.workload()))
                    .append("\",\"passed\":")
                    .append(workload.passed())
                    .append(",\"durationMs\":")
                    .append(workload.durationMs())
                    .append(",\"traceFile\":\"")
                    .append(escapeJson(workload.traceFile()))
                    .append("\",\"bottleneckHint\":\"")
                    .append(escapeJson(workload.bottleneckHint()))
                    .append("\",\"notes\":\"")
                    .append(escapeJson(workload.notes()))
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

    record WorkloadResult(
            String workload,
            boolean passed,
            long durationMs,
            String traceFile,
            String bottleneckHint,
            String notes
    ) {
        WorkloadResult {
            workload = Objects.requireNonNull(workload, "workload");
            traceFile = Objects.requireNonNull(traceFile, "traceFile");
            bottleneckHint = Objects.requireNonNull(bottleneckHint, "bottleneckHint");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
