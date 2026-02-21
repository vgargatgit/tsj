package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjJpaCertificationClosureTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesOrmClosureReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj42d-jpa-certification.json");
        final TsjJpaCertificationClosureReport report = new TsjJpaCertificationClosureHarness().run(reportPath);

        assertTrue(report.familyScenarios().size() >= 10);
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-42d-jpa-certification-closure\""));
        assertTrue(json.contains("\"family\":\"real-db\""));
        assertTrue(json.contains("\"family\":\"lazy-proxy\""));
        assertTrue(json.contains("\"family\":\"lifecycle-transaction\""));
        assertTrue(json.contains("\"backend\":\"h2\""));
        assertTrue(json.contains("\"backend\":\"hsqldb\""));
    }

    @Test
    void certificationGateRequiresAllOrmFamilySuitesToPass() throws Exception {
        final TsjJpaCertificationClosureReport report = new TsjJpaCertificationClosureHarness().run(
                tempDir.resolve("tsj42d-jpa-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.familyScenarios().stream().allMatch(TsjJpaCertificationClosureReport.FamilyScenarioResult::passed));
    }

    @Test
    void certificationReportIncludesOrmVersionBackendAndScenarioFamilySignals() throws Exception {
        final TsjJpaCertificationClosureReport report = new TsjJpaCertificationClosureHarness().run(
                tempDir.resolve("tsj42d-jpa-certification.json")
        );

        final List<TsjJpaCertificationClosureReport.FamilyScenarioResult> h2 = report.familyScenarios()
                .stream()
                .filter(row -> "h2".equals(row.backend()))
                .toList();
        assertTrue(h2.size() >= 2);
        assertTrue(h2.stream().allMatch(row -> "jpa-lite-1.0".equals(row.ormVersion())));

        final List<TsjJpaCertificationClosureReport.FamilyScenarioResult> lazyProxy = report.familyScenarios()
                .stream()
                .filter(row -> "lazy-proxy".equals(row.family()))
                .toList();
        assertTrue(lazyProxy.size() >= 4);
        assertTrue(lazyProxy.stream().allMatch(TsjJpaCertificationClosureReport.FamilyScenarioResult::passed));

        final List<TsjJpaCertificationClosureReport.FamilyScenarioResult> lifecycle = report.familyScenarios()
                .stream()
                .filter(row -> "lifecycle-transaction".equals(row.family()))
                .toList();
        assertTrue(lifecycle.size() >= 5);
        assertEquals(
                3L,
                lifecycle.stream().filter(row -> row.scenario().startsWith("diag:")).count()
        );
    }
}
