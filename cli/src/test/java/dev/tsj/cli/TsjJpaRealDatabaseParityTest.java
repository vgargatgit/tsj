package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjJpaRealDatabaseParityTest {
    @TempDir
    Path tempDir;

    @Test
    void parityHarnessWritesRealDbReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj42a-jpa-realdb-parity.json");
        final TsjJpaRealDatabaseParityReport report = new TsjJpaRealDatabaseParityHarness().run(reportPath);

        assertEquals(2, report.backends().size());
        assertEquals(2, report.diagnosticScenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-42a-jpa-realdb-parity\""));
        assertTrue(json.contains("\"backend\":\"h2\""));
        assertTrue(json.contains("\"backend\":\"hsqldb\""));
        assertTrue(json.contains("\"ormVersion\":\"jpa-lite-1.0\""));
    }

    @Test
    void parityGateRequiresCrudAndQueryParityAcrossRealDbBackends() throws Exception {
        final TsjJpaRealDatabaseParityReport report = new TsjJpaRealDatabaseParityHarness().run(
                tempDir.resolve("tsj42a-jpa-realdb-parity.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.backends().stream().allMatch(TsjJpaRealDatabaseParityReport.BackendScenarioResult::passed));
    }

    @Test
    void diagnosticsSeparateDbWiringFailuresFromOrmQueryFailures() throws Exception {
        final TsjJpaRealDatabaseParityReport report = new TsjJpaRealDatabaseParityHarness().run(
                tempDir.resolve("tsj42a-jpa-realdb-parity.json")
        );

        final List<TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult> dbWiring = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "db-wiring-failure".equals(scenario.scenario()))
                .toList();
        assertEquals(1, dbWiring.size());
        assertEquals("TSJ-ORM-DB-WIRING", dbWiring.getFirst().observedDiagnosticCode());
        assertTrue(dbWiring.getFirst().passed());

        final List<TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult> queryFailures = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "orm-query-failure".equals(scenario.scenario()))
                .toList();
        assertEquals(1, queryFailures.size());
        assertEquals("TSJ-ORM-QUERY-FAILURE", queryFailures.getFirst().observedDiagnosticCode());
        assertTrue(queryFailures.getFirst().passed());
    }
}
