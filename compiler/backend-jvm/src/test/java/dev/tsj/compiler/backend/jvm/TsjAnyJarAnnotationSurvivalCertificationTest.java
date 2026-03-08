package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjAnyJarAnnotationSurvivalCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj75-anyjar-annotation-survival-certification.json");
        final TsjAnyJarAnnotationSurvivalCertificationReport report =
                new TsjAnyJarAnnotationSurvivalCertificationHarness().run(reportPath);

        assertEquals(3, report.dimensions().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-75-anyjar-annotation-survival-certification\""));
        assertTrue(json.contains("\"dimension\":\"annotation-resolution\""));
        assertTrue(json.contains("\"dimension\":\"annotation-emission\""));
        assertTrue(json.contains("\"dimension\":\"reflection-consumer-parity\""));
        assertTrue(json.contains("\"fixtureVersion\":\"tsj75-fixtures-2026.03\""));
    }

    @Test
    void certificationGateRequiresResolutionEmissionAndReflectionConsumerParity() throws Exception {
        final TsjAnyJarAnnotationSurvivalCertificationReport report =
                new TsjAnyJarAnnotationSurvivalCertificationHarness().run(
                        tempDir.resolve("tsj75-anyjar-annotation-survival-certification.json")
                );

        assertTrue(report.gatePassed());
        assertTrue(report.dimensions().stream()
                .allMatch(TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult::passed));
    }

    @Test
    void certificationReportCarriesStableResolutionDiagnosticCode() throws Exception {
        final TsjAnyJarAnnotationSurvivalCertificationReport report =
                new TsjAnyJarAnnotationSurvivalCertificationHarness().run(
                        tempDir.resolve("tsj75-anyjar-annotation-survival-certification.json")
                );

        final TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult resolution =
                report.dimensions()
                        .stream()
                        .filter(dimension -> "annotation-resolution".equals(dimension.dimension()))
                        .findFirst()
                        .orElseThrow();
        assertTrue(resolution.observed().contains("missingCode=TSJ-DECORATOR-RESOLUTION"));
        assertEquals("TSJ-DECORATOR-RESOLUTION", resolution.diagnosticCode());
    }
}
