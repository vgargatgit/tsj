package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjMetadataParityCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj39c-metadata-certification.json");
        final TsjMetadataParityCertificationReport report = new TsjMetadataParityCertificationHarness().run(reportPath);

        assertEquals(5, report.classFamilies().size());
        assertEquals(5, report.introspectorScenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-39c-metadata-parity-certification\""));
        assertTrue(json.contains("\"family\":\"program\""));
        assertTrue(json.contains("\"family\":\"strict-component\""));
        assertTrue(json.contains("\"family\":\"strict-proxy-target\""));
        assertFalse(json.contains("\"family\":\"component\""));
        assertFalse(json.contains("\"family\":\"proxy\""));
        assertFalse(json.contains("\"family\":\"web-controller\""));
        assertTrue(json.contains("\"scenario\":\"hibernate-executable-entity-introspection\""));
        assertTrue(json.contains("\"scenario\":\"jackson-executable-dto-introspection\""));
        assertTrue(json.contains("\"scenario\":\"validation-executable-dto-introspection\""));
        assertTrue(json.contains("\"scenario\":\"strict-spring-web-executable-introspection\""));
    }

    @Test
    void certificationGateRequiresMetadataAndSupportedIntrospectorParity() throws Exception {
        final TsjMetadataParityCertificationReport report = new TsjMetadataParityCertificationHarness().run(
                tempDir.resolve("tsj39c-metadata-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.classFamilies().stream().allMatch(TsjMetadataParityCertificationReport.FamilyResult::passed));
        final List<TsjMetadataParityCertificationReport.IntrospectorResult> supported = report.introspectorScenarios()
                .stream()
                .filter(TsjMetadataParityCertificationReport.IntrospectorResult::supported)
                .toList();
        assertEquals(4, supported.size());
        assertTrue(supported.stream().allMatch(TsjMetadataParityCertificationReport.IntrospectorResult::passed));
    }

    @Test
    void certificationGateTracksUnsupportedIntrospectorDiagnostics() throws Exception {
        final TsjMetadataParityCertificationReport report = new TsjMetadataParityCertificationHarness().run(
                tempDir.resolve("tsj39c-metadata-certification.json")
        );

        final List<TsjMetadataParityCertificationReport.IntrospectorResult> unsupported = report.introspectorScenarios()
                .stream()
                .filter(result -> !result.supported())
                .toList();
        assertEquals(1, unsupported.size());
        assertTrue(unsupported.stream().allMatch(TsjMetadataParityCertificationReport.IntrospectorResult::passed));
        assertTrue(unsupported.stream().allMatch(
                result -> "TSJ39B-INTROSPECTOR-UNSUPPORTED".equals(result.diagnosticCode())
        ));
    }
}
