package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjAnyJarGovernanceCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void governanceHarnessWritesSignoffReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj44d-anyjar-governance.json");
        final TsjAnyJarGovernanceCertificationReport report = new TsjAnyJarGovernanceCertificationHarness().run(reportPath);

        assertEquals(3, report.signoffCriteria().size());
        assertTrue(report.compatibilityManifest().size() >= 5);
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-44d-anyjar-governance\""));
        assertTrue(json.contains("\"criterion\":\"matrix-gate\""));
        assertTrue(json.contains("\"criterion\":\"version-range-gate\""));
        assertTrue(json.contains("\"criterion\":\"real-app-gate\""));
        assertTrue(json.contains("\"supportTier\":\"certified-subset\""));
        assertTrue(json.contains("\"supportTier\":\"certified-range\""));
        assertTrue(json.contains("\"supportTier\":\"certified-real-app\""));
    }

    @Test
    void governanceGateRequiresMatrixVersionAndWorkloadSignoffCriteria() throws Exception {
        final TsjAnyJarGovernanceCertificationReport report = new TsjAnyJarGovernanceCertificationHarness().run(
                tempDir.resolve("tsj44d-anyjar-governance.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.signoffCriteria().stream().allMatch(TsjAnyJarGovernanceCertificationReport.SignoffCriterion::passed));
        assertTrue(report.signoffApproved());
    }

    @Test
    void governanceReportPublishesRegressionPolicyWithDowngradeRollbackGuidance() throws Exception {
        final TsjAnyJarGovernanceCertificationReport report = new TsjAnyJarGovernanceCertificationHarness().run(
                tempDir.resolve("tsj44d-anyjar-governance.json")
        );

        assertEquals("rollback-on-certified-regression", report.regressionPolicy().rollbackMode());
        assertEquals("pin-last-green-manifest", report.regressionPolicy().downgradeMode());
        assertTrue(report.regressionPolicy().notes().contains("certified scenario"));

        final List<TsjAnyJarGovernanceCertificationReport.ManifestEntry> realAppTier = report.compatibilityManifest()
                .stream()
                .filter(entry -> "certified-real-app".equals(entry.supportTier()))
                .toList();
        assertTrue(realAppTier.size() >= 2);
    }
}
