package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjStrictReleaseReadinessGateTest {
    private static final Path REPORT_RELATIVE_PATH =
            Path.of("tests", "conformance", "tsj84-strict-release-readiness.json");

    @TempDir
    Path tempDir;

    @Test
    void readinessHarnessWritesReleaseReadinessArtifacts() throws Exception {
        final Path reportPath = tempDir.resolve("tsj84-strict-release-readiness.json");
        final TsjStrictReleaseReadinessReport report = new TsjStrictReleaseReadinessHarness().run(reportPath);

        assertEquals(4, report.criteria().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-84-strict-release-readiness\""));
        assertTrue(json.contains("\"criterion\":\"strict-readiness-gate\""));
        assertTrue(json.contains("\"criterion\":\"strict-guide-migration\""));
        assertTrue(json.contains("\"criterion\":\"strict-cli-matrix\""));
        assertTrue(json.contains("\"criterion\":\"strict-release-checklist\""));
    }

    @Test
    void releaseGateRequiresStrictReadinessDocsAndChecklistSignals() throws Exception {
        final TsjStrictReleaseReadinessReport report = new TsjStrictReleaseReadinessHarness().run(
                tempDir.resolve("tsj84-strict-release-readiness.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.releaseApproved());
        assertTrue(report.criteria().stream().allMatch(TsjStrictReleaseReadinessReport.ReleaseCriterion::passed));
        assertTrue(report.residualExclusions().stream().anyMatch(value -> value.contains("TSJ-STRICT-DYNAMIC-IMPORT")));
    }

    @Test
    void readinessHarnessWritesCommittedReleaseArtifact() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path reportPath = repoRoot.resolve(REPORT_RELATIVE_PATH).toAbsolutePath().normalize();

        final TsjStrictReleaseReadinessReport report = new TsjStrictReleaseReadinessHarness().run(reportPath);

        assertTrue(Files.exists(report.reportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-84-strict-release-readiness\""));
        assertTrue(json.contains("\"gatePassed\":true"));
        assertTrue(json.contains("\"releaseApproved\":true"));
    }

    private static Path resolveRepoRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml")) && Files.exists(cursor.resolve("tests/conformance"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to resolve TSJ repository root.");
    }
}
