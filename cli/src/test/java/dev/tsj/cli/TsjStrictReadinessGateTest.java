package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjStrictReadinessGateTest {
    private static final Path REPORT_RELATIVE_PATH = Path.of("tests", "conformance", "tsj83-strict-readiness.json");

    @TempDir
    Path tempDir;

    @Test
    void readinessHarnessWritesDeterministicStrictReportArtifacts() throws Exception {
        final Path reportPath = tempDir.resolve("tsj83-strict-readiness.json");
        final TsjStrictReadinessReport report = new TsjStrictReadinessHarness().run(reportPath);

        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        assertTrue(report.strictOkTotal() >= 2);
        assertTrue(report.strictUnsupportedTotal() >= 2);
        assertTrue(report.serializationParityPassed());
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-83-strict-readiness\""));
        assertTrue(json.contains("\"name\":\"strict-ok\""));
        assertTrue(json.contains("\"name\":\"strict-unsupported\""));
        assertTrue(json.contains("\"serializationParity\""));
    }

    @Test
    void readinessGateRequiresStrictOkAndUnsupportedDiagnosticsToPass() throws Exception {
        final TsjStrictReadinessReport report = new TsjStrictReadinessHarness().run(
                tempDir.resolve("tsj83-strict-readiness.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.failedFixtures() == 0);
        assertTrue(report.serializationParityPassed(), report.serializationParityNotes());
        assertTrue(report.fixtures().stream()
                .filter(result -> "strict-ok".equals(result.category()))
                .allMatch(TsjStrictReadinessReport.FixtureResult::passed));
        assertTrue(report.fixtures().stream()
                .filter(result -> "strict-unsupported".equals(result.category()))
                .allMatch(TsjStrictReadinessReport.FixtureResult::passed));
        assertTrue(report.fixtures().stream()
                .anyMatch(result -> "TSJ-STRICT-DYNAMIC-IMPORT".equals(result.actualFeatureId())));
        assertTrue(report.fixtures().stream()
                .anyMatch(result -> "TSJ-STRICT-EVAL".equals(result.actualFeatureId())));
    }

    @Test
    void readinessHarnessWritesCommittedStrictConformanceArtifact() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path reportPath = repoRoot.resolve(REPORT_RELATIVE_PATH).toAbsolutePath().normalize();

        final TsjStrictReadinessReport report = new TsjStrictReadinessHarness().run(reportPath);

        assertTrue(Files.exists(report.reportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-83-strict-readiness\""));
        assertTrue(json.contains("\"gatePassed\":true"));
        assertTrue(json.contains("\"serializationParity\""));
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
