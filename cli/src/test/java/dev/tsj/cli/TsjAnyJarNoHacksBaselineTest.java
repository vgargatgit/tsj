package dev.tsj.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjAnyJarNoHacksBaselineTest {
    private static final Path REPORT_PATH = Path.of("target/tsj85-anyjar-nohacks-baseline.json").toAbsolutePath().normalize();
    private static TsjAnyJarNoHacksBaselineReport cachedReport;

    @Test
    void baselineReportCapturesCurrentNoHacksBlockers() throws Exception {
        final TsjAnyJarNoHacksBaselineReport report = loadReport();

        assertFalse(report.gatePassed());
        assertTrue(Files.exists(REPORT_PATH));

        final Map<String, TsjAnyJarNoHacksBaselineReport.Blocker> blockers = report.blockers().stream().collect(
                Collectors.toMap(
                        TsjAnyJarNoHacksBaselineReport.Blocker::blockerId,
                        Function.identity()
                )
        );
        assertTrue(blockers.get("missing-generic-package-command").present());
        assertTrue(blockers.get("requires-spring-package-command").present());
        assertTrue(blockers.get("requires-generated-spring-adapters").present());
        assertTrue(blockers.get("requires-generated-web-adapters").present());
        assertTrue(blockers.get("requires-generated-boot-launcher").present());
        assertTrue(blockers.get("annotations-land-on-metadata-carrier").present());
        assertTrue(blockers.get("executable-class-missing-runtime-annotations").present());
    }

    @Test
    void baselineReportPersistsStableScenarioObservations() throws Exception {
        final TsjAnyJarNoHacksBaselineReport report = loadReport();

        final Map<String, TsjAnyJarNoHacksBaselineReport.ScenarioResult> scenarios = report.scenarios().stream().collect(
                Collectors.toMap(
                        TsjAnyJarNoHacksBaselineReport.ScenarioResult::scenario,
                        Function.identity()
                )
        );
        assertEquals("TSJ-CLI-002", scenarios.get("generic-package-command").diagnosticCode());
        assertTrue(scenarios.get("spring-web-jpa-package").observed().contains("generatedSpringAdapters="));
        assertTrue(scenarios.get("spring-web-jpa-package").observed().contains("generatedWebAdapters="));
        assertTrue(scenarios.get("spring-web-jpa-package").observed().contains("generatedBootLauncher=true"));
        assertEquals("TSJ-COMPILE-SUCCESS", scenarios.get("runtime-annotation-on-executable-class").diagnosticCode());
        assertTrue(scenarios.get("runtime-annotation-on-executable-class").observed().contains("carrierAnnotated=true"));
        assertTrue(scenarios.get("runtime-annotation-on-executable-class").observed().contains("nativeAnnotated=false"));

        final String persisted = Files.readString(REPORT_PATH);
        assertTrue(persisted.contains("\"suite\":\"TSJ-85-anyjar-nohacks-baseline\""));
        assertTrue(persisted.contains("\"gatePassed\":false"));
    }

    private static synchronized TsjAnyJarNoHacksBaselineReport loadReport() throws Exception {
        if (cachedReport == null) {
            cachedReport = new TsjAnyJarNoHacksBaselineHarness().run(REPORT_PATH);
        }
        return cachedReport;
    }
}
