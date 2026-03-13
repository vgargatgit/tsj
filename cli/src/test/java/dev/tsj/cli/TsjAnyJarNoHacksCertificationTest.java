package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjAnyJarNoHacksCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void certificationHarnessWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj92-anyjar-nohacks-certification.json");
        final TsjAnyJarNoHacksCertificationReport report = new TsjAnyJarNoHacksCertificationHarness().run(reportPath);

        assertEquals(7, report.scenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));

        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-92-anyjar-nohacks-certification\""));
        assertTrue(json.contains("\"scenario\":\"tsj85-baseline\""));
        assertTrue(json.contains("\"scenario\":\"spring-web-di-package-runtime\""));
        assertTrue(json.contains("\"scenario\":\"spring-aop-transaction-proxy\""));
        assertTrue(json.contains("\"scenario\":\"hibernate-jpa-h2-executable\""));
        assertTrue(json.contains("\"scenario\":\"jackson-executable-dto\""));
        assertTrue(json.contains("\"scenario\":\"validation-executable-dto\""));
        assertTrue(json.contains("\"scenario\":\"non-spring-reflection-consumer\""));
    }

    @Test
    void certificationGateRequiresAllNoHacksScenarioFamiliesToPass() throws Exception {
        final TsjAnyJarNoHacksCertificationReport report = new TsjAnyJarNoHacksCertificationHarness().run(
                tempDir.resolve("tsj92-anyjar-nohacks-certification.json")
        );

        assertTrue(report.gatePassed());
        assertEquals(
                List.of(
                        "tsj85-baseline",
                        "spring-web-di-package-runtime",
                        "spring-aop-transaction-proxy",
                        "hibernate-jpa-h2-executable",
                        "jackson-executable-dto",
                        "validation-executable-dto",
                        "non-spring-reflection-consumer"
                ),
                report.scenarios().stream().map(TsjAnyJarNoHacksCertificationReport.ScenarioResult::scenario).toList()
        );
        assertTrue(report.scenarios().stream().allMatch(TsjAnyJarNoHacksCertificationReport.ScenarioResult::passed));
    }
}
