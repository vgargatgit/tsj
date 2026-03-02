package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjKotlinParityReadinessGateTest {
    private static final String PERFORMANCE_BASELINE_OVERRIDE_PROPERTY = "tsj.kotlinParity.performanceBaselinePath";

    @TempDir
    Path tempDir;

    @Test
    void readinessGateGeneratesSubsetAndFullParitySignals() throws Exception {
        final Path reportPath = tempDir.resolve("tsj38-kotlin-parity-readiness.json");
        final TsjKotlinParityReadinessGateHarness harness = new TsjKotlinParityReadinessGateHarness();

        final TsjKotlinParityReadinessGateReport report = harness.run(reportPath);

        assertTrue(report.subsetReady());
        assertTrue(report.fullParityReady());
        assertFalse(report.blockers().contains("db-backed-reference-parity"));
        assertFalse(report.blockers().contains("security-reference-parity"));
        assertFalse(report.blockers().contains("data-jdbc"));
        assertFalse(report.blockers().contains("security"));
        assertEquals(7, report.criteria().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        assertTrue(Files.readString(report.reportPath()).contains("\"suite\":\"TSJ-38-kotlin-parity-readiness\""));
    }

    @Test
    void readinessGateReportsDeterministicFullParityBlockers() throws Exception {
        final Path reportPath = tempDir.resolve("tsj38-kotlin-parity-readiness.json");
        final TsjKotlinParityReadinessGateReport report = new TsjKotlinParityReadinessGateHarness().run(reportPath);

        assertEquals(List.of(), report.blockers());
        assertTrue(report.fullParityReady());
    }

    @Test
    void readinessGateIncludesExpectedCriteria() throws Exception {
        final Path reportPath = tempDir.resolve("tsj38-kotlin-parity-readiness.json");
        final TsjKotlinParityReadinessGateReport report = new TsjKotlinParityReadinessGateHarness().run(reportPath);

        final List<String> ids = report.criteria().stream()
                .map(TsjKotlinParityReadinessGateReport.Criterion::id)
                .toList();
        assertEquals(
                List.of(
                        "reference-app-scaffold",
                        "web-module-parity-signal",
                        "unsupported-module-gates",
                        "db-backed-reference-parity-signal",
                        "security-reference-parity-signal",
                        "performance-baseline-signal",
                        "migration-guide-available"
                ),
                ids
        );
        assertTrue(report.criteria().stream().allMatch(TsjKotlinParityReadinessGateReport.Criterion::passed));
    }

    @Test
    void readinessGateBootstrapsWorkspacePerformanceBaselineWhenRepoBaselineIsMissing() throws Exception {
        final Path reportPath = tempDir.resolve("tsj38-kotlin-parity-readiness.json");
        final Path missingBaselinePath = tempDir.resolve("missing-benchmark-baseline.json");
        final String previousOverride = System.getProperty(PERFORMANCE_BASELINE_OVERRIDE_PROPERTY);
        try {
            System.setProperty(PERFORMANCE_BASELINE_OVERRIDE_PROPERTY, missingBaselinePath.toString());
            final TsjKotlinParityReadinessGateReport report = new TsjKotlinParityReadinessGateHarness().run(reportPath);

            final TsjKotlinParityReadinessGateReport.Criterion performanceCriterion = report.criteria().stream()
                    .filter(criterion -> "performance-baseline-signal".equals(criterion.id()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(performanceCriterion.passed());
            assertTrue(Files.exists(tempDir.resolve("tsj38-kotlin-parity-benchmark-baseline.json")));
        } finally {
            if (previousOverride == null) {
                System.clearProperty(PERFORMANCE_BASELINE_OVERRIDE_PROPERTY);
            } else {
                System.setProperty(PERFORMANCE_BASELINE_OVERRIDE_PROPERTY, previousOverride);
            }
        }
    }
}
