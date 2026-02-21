package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringModuleCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37e-spring-module-certification.json");
        final TsjSpringModuleCertificationReport report = new TsjSpringModuleCertificationHarness().run(reportPath);

        assertEquals(5, report.moduleScenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-37e-spring-module-certification\""));
        assertTrue(json.contains("\"module\":\"web\""));
        assertTrue(json.contains("\"module\":\"validation\""));
        assertTrue(json.contains("\"module\":\"data-jdbc\""));
        assertTrue(json.contains("\"module\":\"actuator\""));
        assertTrue(json.contains("\"module\":\"security\""));
        assertTrue(json.contains("\"fixtureVersion\":\"tsj37-fixtures-2026.02\""));
    }

    @Test
    void certificationGateRequiresAllModuleParityScenariosToPass() throws Exception {
        final TsjSpringModuleCertificationReport report = new TsjSpringModuleCertificationHarness().run(
                tempDir.resolve("tsj37e-spring-module-certification.json")
        );

        assertTrue(report.gatePassed());
        assertTrue(report.moduleScenarios().stream().allMatch(TsjSpringModuleCertificationReport.ModuleScenarioResult::parityPassed));
    }

    @Test
    void certificationReportCarriesDifferentialParitySignals() throws Exception {
        final TsjSpringModuleCertificationReport report = new TsjSpringModuleCertificationHarness().run(
                tempDir.resolve("tsj37e-spring-module-certification.json")
        );

        final TsjSpringModuleCertificationReport.ModuleScenarioResult securityScenario = report.moduleScenarios()
                .stream()
                .filter(scenario -> "security".equals(scenario.module()))
                .findFirst()
                .orElseThrow();
        assertTrue(securityScenario.tsjPassed());
        assertTrue(securityScenario.javaReferencePassed());
        assertTrue(securityScenario.kotlinReferencePassed());
        assertTrue(securityScenario.parityPassed());
        assertEquals("", securityScenario.diagnosticCode());
    }
}
