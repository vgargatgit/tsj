package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TsjRealAppCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void realAppCertificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj44c-real-app-certification.json");
        final TsjRealAppCertificationReport report = new TsjRealAppCertificationHarness().run(reportPath);

        assertEquals(2, report.workloads().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-44c-real-app-certification\""));
        assertTrue(json.contains("\"workload\":\"orders-batch\""));
        assertTrue(json.contains("\"workload\":\"analytics-pipeline\""));
        assertTrue(json.contains("\"reliabilityBudgetPassed\":true"));
        assertTrue(json.contains("\"performanceBudgetPassed\":true"));
    }

    @Test
    void realAppGateRequiresReliabilityAndPerformanceBudgets() throws Exception {
        final TsjRealAppCertificationReport report = new TsjRealAppCertificationHarness().run(
                tempDir.resolve("tsj44c-real-app-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.reliabilityBudgetPassed());
        assertTrue(report.performanceBudgetPassed());
        assertTrue(report.workloads().stream().allMatch(TsjRealAppCertificationReport.WorkloadResult::passed));
    }

    @Test
    void realAppReportPublishesTraceArtifactsAndBottleneckDiagnostics() throws Exception {
        final TsjRealAppCertificationReport report = new TsjRealAppCertificationHarness().run(
                tempDir.resolve("tsj44c-real-app-certification.json")
        );

        for (TsjRealAppCertificationReport.WorkloadResult workload : report.workloads()) {
            final Path tracePath = Path.of(workload.traceFile());
            assertTrue(Files.exists(tracePath));
            assertTrue(workload.bottleneckHint().length() > 6);
            assertTrue(workload.notes().contains("durationMs="));
        }
    }
}
