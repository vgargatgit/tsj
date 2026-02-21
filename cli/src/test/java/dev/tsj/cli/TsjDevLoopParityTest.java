package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjDevLoopParityTest {
    @TempDir
    Path tempDir;

    @Test
    void parityHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj36c-dev-loop-parity.json");
        final TsjDevLoopParityReport report = new TsjDevLoopParityHarness().run(reportPath);

        assertEquals(5, report.scenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-36c-dev-loop-parity\""));
        assertTrue(json.contains("\"id\":\"compile\""));
        assertTrue(json.contains("\"id\":\"run\""));
        assertTrue(json.contains("\"id\":\"spring-package\""));
        assertTrue(json.contains("\"id\":\"spring-smoke\""));
        assertTrue(json.contains("\"id\":\"incremental-iteration\""));
    }

    @Test
    void parityGateRequiresAllDevLoopScenariosToPass() throws Exception {
        final TsjDevLoopParityReport report = new TsjDevLoopParityHarness().run(
                tempDir.resolve("tsj36c-dev-loop-parity.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.scenarios().stream().allMatch(TsjDevLoopParityReport.ScenarioResult::passed));
    }

    @Test
    void parityReportIncludesWorkflowHintsAndNonGoals() throws Exception {
        final TsjDevLoopParityReport report = new TsjDevLoopParityHarness().run(
                tempDir.resolve("tsj36c-dev-loop-parity.json")
        );

        assertTrue(report.workflowHints().stream().anyMatch(hint -> hint.startsWith("tsj compile")));
        assertTrue(report.workflowHints().stream().anyMatch(hint -> hint.startsWith("tsj run")));
        assertTrue(report.workflowHints().stream().anyMatch(hint -> hint.startsWith("tsj spring-package")));
        assertTrue(report.nonGoals().stream().anyMatch(goal -> goal.contains("hot-reload")));
        assertTrue(report.nonGoals().stream().anyMatch(goal -> goal.contains("IDE")));
    }
}
