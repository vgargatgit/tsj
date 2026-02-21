package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjKotlinSecurityParityTest {
    @TempDir
    Path tempDir;

    @Test
    void securityParityHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj38b-security-parity-report.json");
        final TsjKotlinSecurityParityReport report = new TsjKotlinSecurityParityHarness().run(reportPath);

        assertEquals(2, report.supportedScenarios().size());
        assertEquals(3, report.diagnosticScenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-38b-security-parity\""));
        assertTrue(json.contains("\"scenario\":\"authenticated-access\""));
        assertTrue(json.contains("\"scenario\":\"role-based-admin-access\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-SECURITY-AUTHN-FAILURE\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-SECURITY-AUTHZ-FAILURE\""));
        assertTrue(json.contains("\"expectedDiagnosticCode\":\"TSJ-SECURITY-CONFIG-FAILURE\""));
    }

    @Test
    void securityParityGateRequiresAuthenticatedAndRoleBasedParityScenarios() throws Exception {
        final TsjKotlinSecurityParityReport report = new TsjKotlinSecurityParityHarness().run(
                tempDir.resolve("tsj38b-security-parity-report.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.supportedScenarios().stream().allMatch(TsjKotlinSecurityParityReport.SupportedScenarioResult::passed));
    }

    @Test
    void securityParityDiagnosticsSeparateConfigurationAndAuthorizationFailures() throws Exception {
        final TsjKotlinSecurityParityReport report = new TsjKotlinSecurityParityHarness().run(
                tempDir.resolve("tsj38b-security-parity-report.json")
        );

        final List<TsjKotlinSecurityParityReport.DiagnosticScenarioResult> authn = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "unauthenticated-access".equals(scenario.scenario()))
                .toList();
        assertEquals(1, authn.size());
        assertEquals("TSJ-SECURITY-AUTHN-FAILURE", authn.getFirst().observedDiagnosticCode());
        assertTrue(authn.getFirst().passed());

        final List<TsjKotlinSecurityParityReport.DiagnosticScenarioResult> authz = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "authorization-denied".equals(scenario.scenario()))
                .toList();
        assertEquals(1, authz.size());
        assertEquals("TSJ-SECURITY-AUTHZ-FAILURE", authz.getFirst().observedDiagnosticCode());
        assertTrue(authz.getFirst().passed());

        final List<TsjKotlinSecurityParityReport.DiagnosticScenarioResult> config = report.diagnosticScenarios()
                .stream()
                .filter(scenario -> "configuration-failure".equals(scenario.scenario()))
                .toList();
        assertEquals(1, config.size());
        assertEquals("TSJ-SECURITY-CONFIG-FAILURE", config.getFirst().observedDiagnosticCode());
        assertTrue(config.getFirst().passed());
    }
}
