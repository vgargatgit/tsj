package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSyntaxIncrementalReadinessGateTest {
    @TempDir
    Path tempDir;

    @Test
    void readinessHarnessWritesIncrementalReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj69-incremental-readiness.json");
        final TsjSyntaxIncrementalReadinessReport report = new TsjSyntaxIncrementalReadinessHarness().run(reportPath);

        assertEquals(5, report.iterations().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-69-incremental-readiness\""));
        assertTrue(json.contains("\"id\":\"cold-1\""));
        assertTrue(json.contains("\"id\":\"warm-1\""));
        assertTrue(json.contains("\"id\":\"after-change\""));
    }

    @Test
    void readinessGateRequiresWarmReuseAndInvalidationSignals() throws Exception {
        final TsjSyntaxIncrementalReadinessReport report = new TsjSyntaxIncrementalReadinessHarness().run(
                tempDir.resolve("tsj69-incremental-readiness.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.warmFrontendHitRatio() >= report.minWarmFrontendHitRatio());
        assertTrue(report.warmLoweringHitRatio() >= report.minWarmLoweringHitRatio());
        assertTrue(report.invalidationObserved());
        assertTrue(report.iterations().stream().allMatch(TsjSyntaxIncrementalReadinessReport.IterationResult::passed));
    }
}
