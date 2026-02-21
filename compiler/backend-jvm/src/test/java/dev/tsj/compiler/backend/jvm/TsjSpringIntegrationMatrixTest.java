package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringIntegrationMatrixTest {
    @TempDir
    Path tempDir;

    @Test
    void springModuleMatrixRunsAndWritesReport() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixHarness harness = new TsjSpringIntegrationMatrixHarness();

        final TsjSpringIntegrationMatrixReport report = harness.run(reportPath);

        assertEquals(5, report.modules().size());
        assertTrue(report.modules().stream().anyMatch(module -> "web".equals(module.module()) && module.supported()));
        assertTrue(report.modules().stream().anyMatch(module -> "validation".equals(module.module()) && module.supported()));
        assertTrue(report.modules().stream().anyMatch(module -> "data-jdbc".equals(module.module()) && module.supported()));
        assertTrue(report.modules().stream().anyMatch(module -> "actuator".equals(module.module()) && module.supported()));
        assertTrue(report.modules().stream().anyMatch(module -> "security".equals(module.module()) && module.supported()));
        assertTrue(Files.exists(reportPath));
        final String json = Files.readString(reportPath);
        assertTrue(json.contains("\"suite\":\"TSJ-37-spring-ecosystem-matrix\""));
        assertTrue(json.contains("\"module\":\"web\""));
        assertTrue(json.contains("\"module\":\"data-jdbc\""));
        assertTrue(json.contains("\"module\":\"actuator\""));
        assertTrue(json.contains("\"module\":\"security\""));
    }

    @Test
    void springModuleMatrixWritesModuleTargetArtifactForCiUpload() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixHarness().run(reportPath);

        assertTrue(Files.exists(report.moduleReportPath()));
        final String moduleJson = Files.readString(report.moduleReportPath());
        assertTrue(moduleJson.contains("\"suite\":\"TSJ-37-spring-ecosystem-matrix\""));
        assertTrue(moduleJson.contains("\"module\":\"validation\""));
    }

    @Test
    void springModuleMatrixReportsNoUnsupportedModulesInCurrentCertifiedSubset() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixHarness().run(reportPath);

        final List<TsjSpringIntegrationMatrixReport.ModuleResult> unsupported = report.modules().stream()
                .filter(module -> !module.supported())
                .toList();
        assertTrue(unsupported.isEmpty());
        final String moduleStatuses = report.modules().stream()
                .map(module -> module.module() + ":" + module.supported())
                .collect(Collectors.joining(","));
        assertEquals("web:true,validation:true,data-jdbc:true,actuator:true,security:true", moduleStatuses);
    }

    @Test
    void springModuleMatrixWebModuleRunsParityCheck() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixHarness().run(reportPath);

        final TsjSpringIntegrationMatrixReport.ModuleResult webModule = report.modules().stream()
                .filter(module -> "web".equals(module.module()))
                .findFirst()
                .orElseThrow();
        assertTrue(webModule.supported());
        assertTrue(webModule.passed());
        assertEquals("", webModule.diagnosticCode());
        assertEquals("spring-matrix/tsj37-web-supported/main.ts", webModule.fixture());
    }

    @Test
    void springModuleMatrixActuatorModuleRunsBaselineParityAndUnsupportedFeatureGate() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixHarness().run(reportPath);

        final TsjSpringIntegrationMatrixReport.ModuleResult actuatorModule = report.modules().stream()
                .filter(module -> "actuator".equals(module.module()))
                .findFirst()
                .orElseThrow();
        assertTrue(actuatorModule.supported());
        assertTrue(actuatorModule.passed());
        assertEquals("", actuatorModule.diagnosticCode());
        assertEquals("spring-matrix/tsj37-actuator-supported/main.ts", actuatorModule.fixture());
        assertTrue(actuatorModule.notes().contains("health/info/metrics"));
        assertTrue(actuatorModule.notes().contains("@WriteOperation"));
    }

    @Test
    void springModuleMatrixValidationModuleRunsConstraintParityAndUnsupportedFeatureGate() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixHarness().run(reportPath);

        final TsjSpringIntegrationMatrixReport.ModuleResult validationModule = report.modules().stream()
                .filter(module -> "validation".equals(module.module()))
                .findFirst()
                .orElseThrow();
        assertTrue(validationModule.supported());
        assertTrue(validationModule.passed());
        assertEquals("", validationModule.diagnosticCode());
        assertEquals("spring-matrix/tsj37-validation-supported/main.ts", validationModule.fixture());
        assertTrue(validationModule.notes().contains("@NotBlank"));
        assertTrue(validationModule.notes().contains("@Size"));
        assertTrue(validationModule.notes().contains("@Min"));
        assertTrue(validationModule.notes().contains("@Max"));
        assertTrue(validationModule.notes().contains("@NotNull"));
        assertTrue(validationModule.notes().contains("@Email"));
    }

    @Test
    void springModuleMatrixDataJdbcModuleRunsCrudAndQueryParityWithUnsupportedQueryGate() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixHarness().run(reportPath);

        final TsjSpringIntegrationMatrixReport.ModuleResult dataJdbcModule = report.modules().stream()
                .filter(module -> "data-jdbc".equals(module.module()))
                .findFirst()
                .orElseThrow();
        assertTrue(dataJdbcModule.supported());
        assertTrue(dataJdbcModule.passed());
        assertEquals("", dataJdbcModule.diagnosticCode());
        assertEquals("spring-matrix/tsj37-data-jdbc-supported/main.ts", dataJdbcModule.fixture());
        assertTrue(dataJdbcModule.notes().contains("countByStatus"));
        assertTrue(dataJdbcModule.notes().contains("findById"));
        assertTrue(dataJdbcModule.notes().contains("TSJ-DECORATOR-UNSUPPORTED"));
    }

    @Test
    void springModuleMatrixSecurityModuleRunsBaselineParityAndUnsupportedExpressionGate() throws Exception {
        final Path reportPath = tempDir.resolve("tsj37-spring-module-matrix.json");
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixHarness().run(reportPath);

        final TsjSpringIntegrationMatrixReport.ModuleResult securityModule = report.modules().stream()
                .filter(module -> "security".equals(module.module()))
                .findFirst()
                .orElseThrow();
        assertTrue(securityModule.supported());
        assertTrue(securityModule.passed());
        assertEquals("", securityModule.diagnosticCode());
        assertEquals("spring-matrix/tsj37-security-supported/main.ts", securityModule.fixture());
        assertTrue(securityModule.notes().contains("401/403/200"));
        assertTrue(securityModule.notes().contains("hasRole/hasAnyRole"));
    }
}
