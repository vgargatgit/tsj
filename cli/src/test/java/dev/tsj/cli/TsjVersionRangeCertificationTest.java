package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjVersionRangeCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesVersionRangeReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj44b-version-range-certification.json");
        final TsjVersionRangeCertificationReport report = new TsjVersionRangeCertificationHarness().run(reportPath);

        assertEquals(2, report.libraries().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-44b-version-range-certification\""));
        assertTrue(json.contains("\"library\":\"sample.range.RangeApi\""));
        assertTrue(json.contains("\"library\":\"sample.range.TextApi\""));
        assertTrue(json.contains("\"certifiedRange\":\"[1.0.0,2.0.0)\""));
        assertTrue(json.contains("\"certifiedRange\":\"[3.0.0,4.0.0)\""));
    }

    @Test
    void certificationGatePassesWhenCertifiedVersionRangesAreGreen() throws Exception {
        final TsjVersionRangeCertificationReport report = new TsjVersionRangeCertificationHarness().run(
                tempDir.resolve("tsj44b-version-range-certification.json")
        );

        assertTrue(report.gatePassed());
        assertFalse(report.driftDetected());
        assertTrue(report.libraries().stream().allMatch(TsjVersionRangeCertificationReport.LibraryRangeResult::passed));
        assertTrue(report.libraries()
                .stream()
                .flatMap(library -> library.versions().stream())
                .allMatch(TsjVersionRangeCertificationReport.VersionCheckResult::passed));
    }

    @Test
    void certificationReportCapturesFirstFailingVersionWhenCompatibilityDrifts() throws Exception {
        final TsjVersionRangeCertificationReport report = new TsjVersionRangeCertificationHarness().runWithDrift(
                tempDir.resolve("tsj44b-version-range-certification-drift.json")
        );

        assertFalse(report.gatePassed());
        assertTrue(report.driftDetected());
        final List<TsjVersionRangeCertificationReport.LibraryRangeResult> rangeLibraries = report.libraries()
                .stream()
                .filter(library -> "sample.range.RangeApi".equals(library.library()))
                .toList();
        assertEquals(1, rangeLibraries.size());
        assertEquals("1.1.0", rangeLibraries.getFirst().firstFailingVersion());
        assertTrue(rangeLibraries.getFirst().versions()
                .stream()
                .anyMatch(version -> "1.1.0".equals(version.version()) && !version.passed()));
    }
}
