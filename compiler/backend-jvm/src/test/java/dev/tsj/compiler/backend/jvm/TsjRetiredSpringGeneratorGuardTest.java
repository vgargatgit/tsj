package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TsjRetiredSpringGeneratorGuardTest {

    @Test
    void retiredSpringGeneratorSourcesAndTestsAreAbsent() {
        final Path repoRoot = repoRoot();
        final List<Path> retiredPaths = List.of(
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjSpringComponentGenerator.java"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjSpringComponentArtifact.java"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjSpringWebControllerGenerator.java"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjSpringWebControllerArtifact.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringComponentGeneratorTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringComponentIntegrationTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringWebControllerGeneratorTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringWebControllerIntegrationTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjGeneratedMetadataParityTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityHarness.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityReport.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringDiLifecycleParityTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopRuntimeConformanceHarness.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopRuntimeConformanceReport.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopRuntimeConformanceTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityHarness.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityReport.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringAopDifferentialParityTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/InteropSpringIntegrationTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/InteropSpringWebIntegrationTest.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/fixtures/InteropSpringFixtureType.java"),
                repoRoot.resolve("compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/fixtures/InteropSpringWebFixtureType.java"),
                repoRoot.resolve("tests/introspector-matrix/tsj39b-spring-web/fixture.properties"),
                repoRoot.resolve("tests/introspector-matrix/tsj39b-bridge-generic/fixture.properties"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsAnnotationAttributeParser.java"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsDecoratorAnnotationMapping.java"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsDecoratorModelExtractor.java"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjValidationSubsetEvaluator.java"),
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjDataJdbcSubsetEvaluator.java")
        );

        for (Path retiredPath : retiredPaths) {
            assertFalse(Files.exists(retiredPath), () -> "Retired generator path still present: " + retiredPath);
        }
    }

    @Test
    void backendCoreSourcesDoNotReferenceRetiredSpringGeneratorHelpers() throws Exception {
        final Path repoRoot = repoRoot();
        final String compilerSource = Files.readString(
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java"),
                UTF_8
        );
        final String interopBridgeSource = Files.readString(
                repoRoot.resolve("compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/InteropBridgeGenerator.java"),
                UTF_8
        );
        final String matrixSource = Files.readString(
                repoRoot.resolve(
                        "compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjIntrospectorCompatibilityMatrixHarness.java"
                ),
                UTF_8
        );
        final String readinessSource = Files.readString(
                repoRoot.resolve(
                        "compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjKotlinParityReadinessGateHarness.java"
                ),
                UTF_8
        );
        final String springMatrixSource = Files.readString(
                repoRoot.resolve(
                        "compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjSpringIntegrationMatrixHarness.java"
                ),
                UTF_8
        );
        final String certificationSource = Files.readString(
                repoRoot.resolve(
                        "compiler/backend-jvm/src/test/java/dev/tsj/compiler/backend/jvm/TsjMetadataParityCertificationHarness.java"
                ),
                UTF_8
        );

        assertFalse(compilerSource.contains("__tsjInvokeClassWithInjection"), compilerSource);
        assertFalse(compilerSource.contains("__tsjInvokeController"), compilerSource);
        assertFalse(compilerSource.contains("__tsjCoerceControllerRequestBody"), compilerSource);
        assertFalse(matrixSource.contains("spring-web-mapping-introspection"), matrixSource);
        assertFalse(matrixSource.contains("TsjSpringWebControllerGenerator"), matrixSource);
        assertFalse(readinessSource.contains("adapter parity signal"), readinessSource);
        assertFalse(springMatrixSource.contains("controller adapter behavior"), springMatrixSource);
        assertFalse(certificationSource.contains("generated controller adapter"), certificationSource);
        assertFalse(interopBridgeSource.contains("TSJ-INTEROP-SPRING"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("TSJ-INTEROP-WEB"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("SpringBeanSignature"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("SpringWebSignature"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("SpringWebRoute"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("SpringErrorMapping"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("emitSpringWebMethod"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("emitSpringErrorHandler"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("TSJ34-SPRING-WEB"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("org.springframework.web.bind.annotation"), interopBridgeSource);
        assertFalse(interopBridgeSource.contains("org.springframework.context.annotation"), interopBridgeSource);
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("docs/stories.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve repository root.");
    }
}
