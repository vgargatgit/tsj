package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjDependencyMediationCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj40d-dependency-mediation-certification.json");
        final TsjDependencyMediationCertificationReport report = new TsjDependencyMediationCertificationHarness().run(reportPath);

        assertEquals(2, report.graphFixtures().size());
        assertEquals(2, report.scopePaths().size());
        assertEquals(2, report.isolationModes().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-40d-dependency-mediation-certification\""));
        assertTrue(json.contains("\"fixture\":\"nearest-mediation\""));
        assertTrue(json.contains("\"scopePath\":\"runtime\""));
        assertTrue(json.contains("\"mode\":\"app-isolated\""));
    }

    @Test
    void certificationGateRequiresGraphScopeAndIsolationParity() throws Exception {
        final TsjDependencyMediationCertificationReport report = new TsjDependencyMediationCertificationHarness().run(
                tempDir.resolve("tsj40d-dependency-mediation-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.graphFixtures().stream().allMatch(TsjDependencyMediationCertificationReport.GraphFixtureResult::passed));
        assertTrue(report.scopePaths().stream().allMatch(TsjDependencyMediationCertificationReport.ScopePathResult::passed));
        assertTrue(report.isolationModes().stream().allMatch(TsjDependencyMediationCertificationReport.IsolationModeResult::passed));
    }

    @Test
    void certificationReportCarriesStableFailureDiagnostics() throws Exception {
        final TsjDependencyMediationCertificationReport report = new TsjDependencyMediationCertificationHarness().run(
                tempDir.resolve("tsj40d-dependency-mediation-certification.json")
        );

        final List<TsjDependencyMediationCertificationReport.ScopePathResult> runtimeScopeResults = report.scopePaths()
                .stream()
                .filter(result -> "runtime".equals(result.scopePath()))
                .toList();
        assertEquals(1, runtimeScopeResults.size());
        assertEquals("TSJ-CLASSPATH-SCOPE", runtimeScopeResults.getFirst().diagnosticCode());

        final List<TsjDependencyMediationCertificationReport.IsolationModeResult> conflictResults = report.isolationModes()
                .stream()
                .filter(result -> "app-isolated".equals(result.mode()) && "conflict".equals(result.scenario()))
                .toList();
        assertEquals(1, conflictResults.size());
        assertEquals("TSJ-RUN-009", conflictResults.getFirst().diagnosticCode());
    }
}
