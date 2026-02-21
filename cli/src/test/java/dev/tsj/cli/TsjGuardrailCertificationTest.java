package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjGuardrailCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj43d-guardrail-certification.json");
        final TsjGuardrailCertificationReport report = new TsjGuardrailCertificationHarness().run(reportPath);

        assertEquals(3, report.families().size());
        assertEquals(7, report.scenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-43d-guardrail-certification\""));
        assertTrue(json.contains("\"family\":\"fleet-policy\""));
        assertTrue(json.contains("\"family\":\"centralized-audit\""));
        assertTrue(json.contains("\"family\":\"rbac-approval\""));
    }

    @Test
    void certificationGateRequiresAllGuardrailFamiliesToPass() throws Exception {
        final TsjGuardrailCertificationReport report = new TsjGuardrailCertificationHarness().run(
                tempDir.resolve("tsj43d-guardrail-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.families().stream().allMatch(TsjGuardrailCertificationReport.FamilySummary::passed));
        assertTrue(report.scenarios().stream().allMatch(TsjGuardrailCertificationReport.ScenarioResult::passed));
    }

    @Test
    void certificationReportCarriesStableGuardrailDiagnostics() throws Exception {
        final TsjGuardrailCertificationReport report = new TsjGuardrailCertificationHarness().run(
                tempDir.resolve("tsj43d-guardrail-certification.json")
        );

        final List<TsjGuardrailCertificationReport.ScenarioResult> policyConflict = report.scenarios()
                .stream()
                .filter(result -> "fleet-policy-conflict-diagnostic".equals(result.scenario()))
                .toList();
        assertEquals(1, policyConflict.size());
        assertEquals("TSJ-INTEROP-POLICY-CONFLICT", policyConflict.getFirst().diagnosticCode());

        final List<TsjGuardrailCertificationReport.ScenarioResult> rbacDeny = report.scenarios()
                .stream()
                .filter(result -> "rbac-required-role-diagnostic".equals(result.scenario()))
                .toList();
        assertEquals(1, rbacDeny.size());
        assertEquals("TSJ-INTEROP-RBAC", rbacDeny.getFirst().diagnosticCode());

        final List<TsjGuardrailCertificationReport.ScenarioResult> approvalDeny = report.scenarios()
                .stream()
                .filter(result -> "approval-required-diagnostic".equals(result.scenario()))
                .toList();
        assertEquals(1, approvalDeny.size());
        assertEquals("TSJ-INTEROP-APPROVAL", approvalDeny.getFirst().diagnosticCode());

        final List<TsjGuardrailCertificationReport.ScenarioResult> auditFallback = report.scenarios()
                .stream()
                .filter(result -> "aggregate-fallback-diagnostic".equals(result.scenario()))
                .toList();
        assertEquals(1, auditFallback.size());
        assertTrue(auditFallback.getFirst().notes().contains("TSJ-INTEROP-AUDIT-AGGREGATE"));
    }
}
