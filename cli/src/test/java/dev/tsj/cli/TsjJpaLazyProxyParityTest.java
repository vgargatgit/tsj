package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjJpaLazyProxyParityTest {
    @TempDir
    Path tempDir;

    @Test
    void parityHarnessWritesLazyProxyReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj42b-jpa-lazy-proxy-parity.json");
        final TsjJpaLazyProxyParityReport report = new TsjJpaLazyProxyParityHarness().run(reportPath);

        assertEquals(2, report.supportedScenarios().size());
        assertEquals(2, report.diagnosticScenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-42b-jpa-lazy-proxy-parity\""));
        assertTrue(json.contains("\"scenario\":\"lazy-initialization\""));
        assertTrue(json.contains("\"scenario\":\"lazy-read-boundary\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-JPA-LAZY-UNSUPPORTED\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-JPA-PROXY-UNSUPPORTED\""));
    }

    @Test
    void parityGateRequiresSupportedLazyProxyScenariosToPass() throws Exception {
        final TsjJpaLazyProxyParityReport report = new TsjJpaLazyProxyParityHarness().run(
                tempDir.resolve("tsj42b-jpa-lazy-proxy-parity.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.supportedScenarios().stream().allMatch(TsjJpaLazyProxyParityReport.SupportedScenarioResult::passed));
    }

    @Test
    void unsupportedPatternsEmitExplicitDiagnosticsWithAssociationContext() throws Exception {
        final TsjJpaLazyProxyParityReport report = new TsjJpaLazyProxyParityHarness().run(
                tempDir.resolve("tsj42b-jpa-lazy-proxy-parity.json")
        );

        final List<TsjJpaLazyProxyParityReport.DiagnosticScenarioResult> lazy = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "unsupported-lazy-pattern".equals(scenario.scenario()))
                .toList();
        assertEquals(1, lazy.size());
        assertEquals("TSJ-JPA-LAZY-UNSUPPORTED", lazy.getFirst().observedDiagnosticCode());
        assertTrue(lazy.getFirst().passed());
        assertTrue(lazy.getFirst().notes().contains("Order.customer"));

        final List<TsjJpaLazyProxyParityReport.DiagnosticScenarioResult> proxy = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "unsupported-proxy-pattern".equals(scenario.scenario()))
                .toList();
        assertEquals(1, proxy.size());
        assertEquals("TSJ-JPA-PROXY-UNSUPPORTED", proxy.getFirst().observedDiagnosticCode());
        assertTrue(proxy.getFirst().passed());
        assertTrue(proxy.getFirst().notes().contains("Order.finalAssociation"));
    }
}
