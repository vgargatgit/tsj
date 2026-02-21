package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjIntrospectorCompatibilityMatrixTest {
    @TempDir
    Path tempDir;

    @Test
    void introspectorMatrixWritesReportAndModuleArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("tsj39b-introspector-matrix.json");
        final TsjIntrospectorCompatibilityMatrixReport report =
                new TsjIntrospectorCompatibilityMatrixHarness().run(reportPath);

        assertEquals(3, report.scenarios().size());
        assertTrue(Files.exists(report.reportPath()));
        assertTrue(Files.exists(report.moduleReportPath()));
        final String json = Files.readString(report.reportPath());
        assertTrue(json.contains("\"suite\":\"TSJ-39b-introspector-compatibility-matrix\""));
        assertTrue(json.contains("\"scenario\":\"bridge-generic-signature\""));
        assertTrue(json.contains("\"scenario\":\"spring-web-mapping-introspection\""));
        assertTrue(json.contains("\"scenario\":\"jackson-unsupported\""));
    }

    @Test
    void introspectorMatrixSupportedScenariosPass() throws Exception {
        final TsjIntrospectorCompatibilityMatrixReport report =
                new TsjIntrospectorCompatibilityMatrixHarness().run(
                        tempDir.resolve("tsj39b-introspector-matrix.json")
                );

        final List<TsjIntrospectorCompatibilityMatrixReport.ScenarioResult> supported = report.scenarios().stream()
                .filter(TsjIntrospectorCompatibilityMatrixReport.ScenarioResult::supported)
                .toList();
        assertEquals(2, supported.size());
        assertTrue(supported.stream().allMatch(TsjIntrospectorCompatibilityMatrixReport.ScenarioResult::passed));
        assertTrue(supported.stream().allMatch(scenario -> "".equals(scenario.diagnosticCode())));
    }

    @Test
    void introspectorMatrixUnsupportedScenarioHasStableDiagnosticAndGuidance() throws Exception {
        final TsjIntrospectorCompatibilityMatrixReport report =
                new TsjIntrospectorCompatibilityMatrixHarness().run(
                        tempDir.resolve("tsj39b-introspector-matrix.json")
                );

        final TsjIntrospectorCompatibilityMatrixReport.ScenarioResult unsupported = report.scenarios().stream()
                .filter(scenario -> !scenario.supported())
                .findFirst()
                .orElseThrow();
        assertTrue(unsupported.passed());
        assertEquals("TSJ39B-INTROSPECTOR-UNSUPPORTED", unsupported.diagnosticCode());
        assertTrue(unsupported.guidance().contains("fallback"));
    }
}
