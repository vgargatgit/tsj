package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringAopRuntimeConformanceTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimeConformanceHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj35b-aop-runtime-conformance.json");
        final TsjSpringAopRuntimeConformanceReport report =
                new TsjSpringAopRuntimeConformanceHarness().run(reportPath);

        assertEquals(4, report.scenarios().size());
        assertTrue(Files.exists(reportPath));
        assertTrue(Files.exists(report.moduleReportPath()));
        final String json = Files.readString(reportPath);
        assertTrue(json.contains("\"suite\":\"TSJ-35b-aop-runtime-conformance\""));
        assertTrue(json.contains("\"scenario\":\"commit-chain\""));
        assertTrue(json.contains("\"scenario\":\"rollback-chain\""));
    }

    @Test
    void runtimeConformanceCapturesCommitRollbackAndPropagationCounts() throws Exception {
        final TsjSpringAopRuntimeConformanceReport report =
                new TsjSpringAopRuntimeConformanceHarness().run(
                        tempDir.resolve("tsj35b-aop-runtime-conformance.json")
                );

        final TsjSpringAopRuntimeConformanceReport.ScenarioResult commit = report.scenarios().stream()
                .filter(scenario -> "commit-chain".equals(scenario.scenario()))
                .findFirst()
                .orElseThrow();
        assertTrue(commit.passed());
        assertEquals(2, commit.beginCount());
        assertEquals(2, commit.commitCount());
        assertEquals(0, commit.rollbackCount());

        final TsjSpringAopRuntimeConformanceReport.ScenarioResult rollback = report.scenarios().stream()
                .filter(scenario -> "rollback-chain".equals(scenario.scenario()))
                .findFirst()
                .orElseThrow();
        assertTrue(rollback.passed());
        assertEquals(2, rollback.beginCount());
        assertEquals(0, rollback.commitCount());
        assertEquals(2, rollback.rollbackCount());
    }

    @Test
    void runtimeConformanceDistinguishesInfrastructureAndApplicationFailures() throws Exception {
        final TsjSpringAopRuntimeConformanceReport report =
                new TsjSpringAopRuntimeConformanceHarness().run(
                        tempDir.resolve("tsj35b-aop-runtime-conformance.json")
                );

        final TsjSpringAopRuntimeConformanceReport.ScenarioResult infra = report.scenarios().stream()
                .filter(scenario -> "missing-transaction-manager".equals(scenario.scenario()))
                .findFirst()
                .orElseThrow();
        assertTrue(infra.passed());
        assertEquals("TSJ-SPRING-AOP", infra.diagnosticCode());
        assertTrue(infra.notes().contains("infrastructure"));

        final TsjSpringAopRuntimeConformanceReport.ScenarioResult appFailure = report.scenarios().stream()
                .filter(scenario -> "application-invocation-failure".equals(scenario.scenario()))
                .findFirst()
                .orElseThrow();
        assertTrue(appFailure.passed());
        assertEquals("", appFailure.diagnosticCode());
        assertTrue(appFailure.notes().toLowerCase(java.util.Locale.ROOT).contains("application invocation failure"));
    }
}
