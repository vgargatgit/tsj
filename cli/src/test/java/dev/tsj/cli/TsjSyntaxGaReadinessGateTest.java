package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSyntaxGaReadinessGateTest {
    @TempDir
    Path tempDir;

    @Test
    void readinessHarnessWritesSignoffAndCompatibilityManifestArtifacts() throws Exception {
        final Path reportPath = tempDir.resolve("tsj70-syntax-ga-signoff.json");
        final TsjSyntaxGaSignoffReport report = new TsjSyntaxGaReadinessHarness().run(reportPath);

        assertEquals(3, report.signoffCriteria().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        assertTrue(Files.exists(report.compatibilityManifestPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-70-syntax-ga-signoff\""));
        assertTrue(json.contains("\"criterion\":\"certified-corpus-parse-failures\""));
        assertTrue(json.contains("\"criterion\":\"mandatory-suite-signals\""));
        assertTrue(json.contains("\"criterion\":\"compatibility-manifest\""));
    }

    @Test
    void readinessGateRequiresCertifiedCorpusAndMandatorySuiteSignals() throws Exception {
        final TsjSyntaxGaSignoffReport report = new TsjSyntaxGaReadinessHarness().run(
                tempDir.resolve("tsj70-syntax-ga-signoff.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.signoffApproved());
        assertTrue(report.signoffCriteria().stream().allMatch(TsjSyntaxGaSignoffReport.SignoffCriterion::passed));
        assertTrue(report.residualExclusions().stream().anyMatch(value -> value.contains("TSX/JSX")));
    }
}
