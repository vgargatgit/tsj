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

        assertTrue(report.gatePassed());
        assertTrue(Files.exists(REPORT_PATH));

        final Map<String, TsjAnyJarNoHacksBaselineReport.Blocker> blockers = report.blockers().stream().collect(
                Collectors.toMap(
                        TsjAnyJarNoHacksBaselineReport.Blocker::blockerId,
                        Function.identity()
                )
        );
        assertFalse(blockers.get("missing-generic-package-command").present());
        assertFalse(blockers.get("requires-spring-package-command").present());
        assertFalse(blockers.get("requires-generated-spring-adapters").present());
        assertFalse(blockers.get("requires-generated-web-adapters").present());
        assertFalse(blockers.get("requires-generated-boot-launcher").present());
        assertFalse(blockers.get("requires-legacy-spring-adapter-flag").present());
        assertFalse(blockers.get("requires-framework-glue-helper-entrypoints").present());
        assertFalse(blockers.get("annotations-land-on-metadata-carrier").present());
        assertFalse(blockers.get("executable-class-missing-runtime-annotations").present());
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
        assertEquals("TSJ-PACKAGE-SUCCESS", scenarios.get("generic-package-command").diagnosticCode());
        assertTrue(scenarios.get("spring-web-jpa-package").observed().contains("command=package"));
        assertEquals("TSJ-PACKAGE-SUCCESS", scenarios.get("spring-web-jpa-package").diagnosticCode());
        assertTrue(scenarios.get("spring-web-jpa-package").observed().contains("generatedSpringAdapters=0"));
        assertTrue(scenarios.get("spring-web-jpa-package").observed().contains("generatedWebAdapters=0"));
        assertTrue(scenarios.get("spring-web-jpa-package").observed().contains("generatedBootLauncher=false"));
        assertEquals("TSJ-COMPILE-SUCCESS", scenarios.get("spring-aop-web-di-generic-compile").diagnosticCode());
        assertTrue(scenarios.get("spring-aop-web-di-generic-compile").observed().contains("noLegacySpringAdapters=0"));
        assertTrue(scenarios.get("spring-aop-web-di-generic-compile").observed().contains("noLegacyWebAdapters=0"));
        assertTrue(scenarios.get("spring-aop-web-di-generic-compile").observed().contains("retiredLegacyFlagCode=TSJ-CLI-005"));
        assertEquals("TSJ-COMPILE-SUCCESS", scenarios.get("runtime-annotation-on-executable-class").diagnosticCode());
        assertTrue(scenarios.get("runtime-annotation-on-executable-class").observed().contains("carrierExists=false"));
        assertTrue(scenarios.get("runtime-annotation-on-executable-class").observed().contains("carrierAnnotated=false"));
        assertTrue(scenarios.get("runtime-annotation-on-executable-class").observed().contains("nativeAnnotated=true"));

        final String persisted = Files.readString(REPORT_PATH);
        assertTrue(persisted.contains("\"suite\":\"TSJ-85-anyjar-nohacks-baseline\""));
        assertTrue(persisted.contains("\"gatePassed\":true"));
    }

    private static synchronized TsjAnyJarNoHacksBaselineReport loadReport() throws Exception {
        if (cachedReport == null) {
            cachedReport = new TsjAnyJarNoHacksBaselineHarness().run(REPORT_PATH);
        }
        return cachedReport;
    }
}
