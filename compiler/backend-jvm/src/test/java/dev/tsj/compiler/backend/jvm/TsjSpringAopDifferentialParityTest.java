package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringAopDifferentialParityTest {
    @TempDir
    Path tempDir;

    @Test
    void differentialParityHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj35c-aop-differential-parity.json");
        final TsjSpringAopDifferentialParityReport report = new TsjSpringAopDifferentialParityHarness().run(reportPath);

        assertEquals(4, report.scenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-35c-aop-differential-parity\""));
        assertTrue(json.contains("\"scenario\":\"commit-chain\""));
        assertTrue(json.contains("\"scenario\":\"rollback-chain\""));
        assertTrue(json.contains("\"scenario\":\"missing-transaction-manager\""));
        assertTrue(json.contains("\"scenario\":\"application-invocation-failure\""));
    }

    @Test
    void differentialParityHarnessMatchesTsAgainstJavaAndKotlinReferences() throws Exception {
        final TsjSpringAopDifferentialParityReport report = new TsjSpringAopDifferentialParityHarness().run(
                tempDir.resolve("tsj35c-aop-differential-parity.json")
        );

        assertEquals(
                List.of(
                        "commit-chain",
                        "rollback-chain",
                        "missing-transaction-manager",
                        "application-invocation-failure"
                ),
                report.scenarios().stream().map(TsjSpringAopDifferentialParityReport.ScenarioResult::scenario).toList()
        );
        assertTrue(report.scenarios().stream().allMatch(TsjSpringAopDifferentialParityReport.ScenarioResult::passed));
    }

    @Test
    void differentialParityHarnessPublishesStableScenarioDiagnostics() throws Exception {
        final TsjSpringAopDifferentialParityReport report = new TsjSpringAopDifferentialParityHarness().run(
                tempDir.resolve("tsj35c-aop-differential-parity.json")
        );

        for (TsjSpringAopDifferentialParityReport.ScenarioResult scenario : report.scenarios()) {
            assertTrue(scenario.fixture().startsWith("spring-matrix/tsj35b-"));
            if ("missing-transaction-manager".equals(scenario.scenario())) {
                assertEquals("TSJ-SPRING-AOP", scenario.diagnosticCode());
            } else {
                assertEquals("", scenario.diagnosticCode());
            }
        }
    }
}
