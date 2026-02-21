package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjInvocationConversionCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj41d-invocation-conversion-certification.json");
        final TsjInvocationConversionCertificationReport report = new TsjInvocationConversionCertificationHarness()
                .run(reportPath);

        assertEquals(6, report.scenarios().size());
        assertEquals(3, report.families().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-41d-invocation-conversion-certification\""));
        assertTrue(json.contains("\"family\":\"numeric-widening\""));
        assertTrue(json.contains("\"family\":\"generic-adaptation\""));
        assertTrue(json.contains("\"family\":\"reflective-edge\""));
    }

    @Test
    void certificationGateRequiresAllFamilySuitesToPass() throws Exception {
        final TsjInvocationConversionCertificationReport report = new TsjInvocationConversionCertificationHarness().run(
                tempDir.resolve("tsj41d-invocation-conversion-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.families().stream().allMatch(TsjInvocationConversionCertificationReport.FamilySummary::passed));
        assertTrue(report.scenarios().stream().allMatch(TsjInvocationConversionCertificationReport.ScenarioResult::passed));
    }

    @Test
    void certificationReportCarriesStableDiagnosticSignals() throws Exception {
        final TsjInvocationConversionCertificationReport report = new TsjInvocationConversionCertificationHarness().run(
                tempDir.resolve("tsj41d-invocation-conversion-certification.json")
        );

        final List<TsjInvocationConversionCertificationReport.ScenarioResult> numericFailure = report.scenarios()
                .stream()
                .filter(result -> "numeric-narrowing-diagnostic".equals(result.scenario()))
                .toList();
        assertEquals(1, numericFailure.size());
        assertEquals("TSJ-RUN-006", numericFailure.getFirst().diagnosticCode());

        final List<TsjInvocationConversionCertificationReport.ScenarioResult> reflectiveFailure = report.scenarios()
                .stream()
                .filter(result -> "reflective-nonpublic-diagnostic".equals(result.scenario()))
                .toList();
        assertEquals(1, reflectiveFailure.size());
        assertEquals("TSJ-RUN-006", reflectiveFailure.getFirst().diagnosticCode());
        assertTrue(reflectiveFailure.getFirst().notes().contains("TSJ-INTEROP-REFLECTIVE"));
    }
}
