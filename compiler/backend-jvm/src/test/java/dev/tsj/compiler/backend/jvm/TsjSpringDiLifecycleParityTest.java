package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringDiLifecycleParityTest {
    @TempDir
    Path tempDir;

    @Test
    void differentialParityHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj33f-di-lifecycle-parity.json");
        final TsjSpringDiLifecycleParityReport report = new TsjSpringDiLifecycleParityHarness().run(reportPath);

        assertEquals(3, report.scenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-33f-di-lifecycle-parity\""));
        assertTrue(json.contains("\"scenario\":\"mixed-injection\""));
        assertTrue(json.contains("\"scenario\":\"lifecycle-order\""));
        assertTrue(json.contains("\"scenario\":\"cycle-diagnostic\""));
    }

    @Test
    void differentialParityHarnessMatchesTsAgainstJavaAndKotlinReferences() throws Exception {
        final TsjSpringDiLifecycleParityReport report = new TsjSpringDiLifecycleParityHarness().run(
                tempDir.resolve("tsj33f-di-lifecycle-parity.json")
        );

        assertEquals(
                List.of("mixed-injection", "lifecycle-order", "cycle-diagnostic"),
                report.scenarios().stream().map(TsjSpringDiLifecycleParityReport.ScenarioResult::scenario).toList()
        );
        assertTrue(report.scenarios().stream().allMatch(TsjSpringDiLifecycleParityReport.ScenarioResult::passed));
    }

    @Test
    void differentialParityHarnessPublishesFixtureTraceabilityForScenarios() throws Exception {
        final TsjSpringDiLifecycleParityReport report = new TsjSpringDiLifecycleParityHarness().run(
                tempDir.resolve("tsj33f-di-lifecycle-parity.json")
        );

        for (TsjSpringDiLifecycleParityReport.ScenarioResult scenario : report.scenarios()) {
            assertTrue(scenario.fixture().startsWith("spring-matrix/tsj33f-"));
            assertTrue(scenario.notes().length() > 8);
            assertEquals("", scenario.diagnosticCode());
        }
    }
}
