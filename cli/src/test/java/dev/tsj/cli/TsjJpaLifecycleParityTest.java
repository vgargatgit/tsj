package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjJpaLifecycleParityTest {
    @TempDir
    Path tempDir;

    @Test
    void parityHarnessWritesLifecycleReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj42c-jpa-lifecycle-parity.json");
        final TsjJpaLifecycleParityReport report = new TsjJpaLifecycleParityHarness().run(reportPath);

        assertEquals(2, report.supportedScenarios().size());
        assertEquals(3, report.diagnosticScenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-42c-jpa-lifecycle-parity\""));
        assertTrue(json.contains("\"scenario\":\"flush-clear-detach-merge\""));
        assertTrue(json.contains("\"scenario\":\"transaction-boundary-rollback\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-ORM-LIFECYCLE-MISUSE\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-ORM-TRANSACTION-REQUIRED\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-ORM-MAPPING-FAILURE\""));
    }

    @Test
    void parityGateRequiresLifecycleAndTransactionScenariosToPass() throws Exception {
        final TsjJpaLifecycleParityReport report = new TsjJpaLifecycleParityHarness().run(
                tempDir.resolve("tsj42c-jpa-lifecycle-parity.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.supportedScenarios().stream().allMatch(TsjJpaLifecycleParityReport.SupportedScenarioResult::passed));
    }

    @Test
    void diagnosticsSeparateLifecycleMisuseFromTransactionAndMappingFailures() throws Exception {
        final TsjJpaLifecycleParityReport report = new TsjJpaLifecycleParityHarness().run(
                tempDir.resolve("tsj42c-jpa-lifecycle-parity.json")
        );

        final List<TsjJpaLifecycleParityReport.DiagnosticScenarioResult> lifecycle = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "lifecycle-misuse".equals(scenario.scenario()))
                .toList();
        assertEquals(1, lifecycle.size());
        assertEquals("TSJ-ORM-LIFECYCLE-MISUSE", lifecycle.getFirst().observedDiagnosticCode());
        assertTrue(lifecycle.getFirst().passed());

        final List<TsjJpaLifecycleParityReport.DiagnosticScenarioResult> transaction = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "transaction-required".equals(scenario.scenario()))
                .toList();
        assertEquals(1, transaction.size());
        assertEquals("TSJ-ORM-TRANSACTION-REQUIRED", transaction.getFirst().observedDiagnosticCode());
        assertTrue(transaction.getFirst().passed());

        final List<TsjJpaLifecycleParityReport.DiagnosticScenarioResult> mapping = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "mapping-failure".equals(scenario.scenario()))
                .toList();
        assertEquals(1, mapping.size());
        assertEquals("TSJ-ORM-MAPPING-FAILURE", mapping.getFirst().observedDiagnosticCode());
        assertTrue(mapping.getFirst().passed());
    }
}
