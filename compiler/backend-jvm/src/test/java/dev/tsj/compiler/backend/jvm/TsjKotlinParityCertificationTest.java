package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjKotlinParityCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesParityCertificationReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj38c-kotlin-parity-certification.json");
        final TsjKotlinParityCertificationReport report = new TsjKotlinParityCertificationHarness().run(reportPath);

        assertEquals(4, report.dimensions().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-38c-kotlin-parity-certification\""));
        assertTrue(json.contains("\"dimension\":\"correctness\""));
        assertTrue(json.contains("\"dimension\":\"startup-time-ms\""));
        assertTrue(json.contains("\"dimension\":\"throughput-ops-per-sec\""));
        assertTrue(json.contains("\"dimension\":\"diagnostics-quality\""));
        assertTrue(json.contains("\"fixtureVersion\":\"tsj38-fixtures-2026.02\""));
    }

    @Test
    void certificationGateRequiresAllDimensionThresholdsAndParitySignalsToPass() throws Exception {
        final TsjKotlinParityCertificationReport report = new TsjKotlinParityCertificationHarness().run(
                tempDir.resolve("tsj38c-kotlin-parity-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.dbParityPassed());
        assertTrue(report.securityParityPassed());
        assertTrue(report.dimensions().stream().allMatch(TsjKotlinParityCertificationReport.DimensionResult::passed));
    }

    @Test
    void fullParityReadyFlipsOnlyWhenDbAndSecurityParityGatesPass() throws Exception {
        final TsjKotlinParityCertificationReport report = new TsjKotlinParityCertificationHarness().run(
                tempDir.resolve("tsj38c-kotlin-parity-certification.json")
        );

        assertTrue(report.dbParityPassed());
        assertTrue(report.securityParityPassed());
        assertTrue(report.fullParityReady());

        final List<TsjKotlinParityCertificationReport.DimensionResult> correctness = report.dimensions().stream()
                .filter(dimension -> "correctness".equals(dimension.dimension()))
                .toList();
        assertEquals(1, correctness.size());
        assertTrue(correctness.getFirst().observed().contains("db=true"));
        assertTrue(correctness.getFirst().observed().contains("security=true"));
    }
}
