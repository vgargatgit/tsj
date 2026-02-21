package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjKotlinDbParityTest {
    @TempDir
    Path tempDir;

    @Test
    void dbParityHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj38a-db-parity-report.json");
        final TsjKotlinDbParityReport report = new TsjKotlinDbParityHarness().run(reportPath);

        assertEquals(2, report.backends().size());
        assertEquals(2, report.diagnosticScenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-38a-db-parity\""));
        assertTrue(json.contains("\"backend\":\"h2\""));
        assertTrue(json.contains("\"backend\":\"hsqldb\""));
        assertTrue(json.contains("\"ormVersion\":\"reference-db-1.0\""));
    }

    @Test
    void dbParityGateRequiresTsAndKotlinDifferentialMatchAcrossBackends() throws Exception {
        final TsjKotlinDbParityReport report = new TsjKotlinDbParityHarness().run(
                tempDir.resolve("tsj38a-db-parity-report.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.backends().stream().allMatch(TsjKotlinDbParityReport.BackendResult::passed));
    }

    @Test
    void dbParitySeparatesDbWiringAndOrmQueryFailureDiagnostics() throws Exception {
        final TsjKotlinDbParityReport report = new TsjKotlinDbParityHarness().run(
                tempDir.resolve("tsj38a-db-parity-report.json")
        );

        final List<TsjKotlinDbParityReport.DiagnosticScenarioResult> dbWiring = report.diagnosticScenarios()
                .stream()
                .filter(result -> "db-wiring-failure".equals(result.scenario()))
                .toList();
        assertEquals(1, dbWiring.size());
        assertEquals("TSJ-ORM-DB-WIRING", dbWiring.getFirst().observedDiagnosticCode());
        assertTrue(dbWiring.getFirst().passed());

        final List<TsjKotlinDbParityReport.DiagnosticScenarioResult> queryFailure = report.diagnosticScenarios()
                .stream()
                .filter(result -> "orm-query-failure".equals(result.scenario()))
                .toList();
        assertEquals(1, queryFailure.size());
        assertEquals("TSJ-ORM-QUERY-FAILURE", queryFailure.getFirst().observedDiagnosticCode());
        assertTrue(queryFailure.getFirst().passed());
    }
}
