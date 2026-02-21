package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteropBridgeGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesInteropBridgeSourceForAllowlistedTargets() throws Exception {
        final Path specFile = tempDir.resolve("interop.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max,java.lang.Integer#parseInt
                targets=java.lang.Math#max
                """,
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(
                specFile,
                tempDir.resolve("out")
        );

        assertEquals(1, artifact.targets().size());
        assertEquals(1, artifact.sourceFiles().size());
        final Path generatedSource = artifact.sourceFiles().getFirst();
        assertTrue(Files.exists(generatedSource));

        final String source = Files.readString(generatedSource, UTF_8);
        assertTrue(source.contains("class JavaLangMathBridge"));
        assertTrue(source.contains("invokeBinding(\"java.lang.Math\", \"max\""));
        assertTrue(source.contains("public static Object max"));
    }

    @Test
    void disallowedTargetFailsWithExplicitDiagnostic() throws Exception {
        final Path specFile = tempDir.resolve("interop.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max
                targets=java.lang.System#exit
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out"))
        );

        assertEquals("TSJ-INTEROP-DISALLOWED", exception.code());
        assertEquals("TSJ19-ALLOWLIST", exception.featureId());
        assertTrue(exception.getMessage().contains("java.lang.System#exit"));
        assertTrue(exception.getMessage().contains("allowlist"));
        assertTrue(exception.guidance() != null && exception.guidance().contains("allowlist"));
    }

    @Test
    void invalidTargetMethodFailsWithExplicitDiagnostic() throws Exception {
        final Path specFile = tempDir.resolve("interop.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#definitelyMissing
                targets=java.lang.Math#definitelyMissing
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out"))
        );

        assertEquals("TSJ-INTEROP-INVALID", exception.code());
        assertTrue(exception.getMessage().contains("definitelyMissing"));
    }

    @Test
    void optInBehaviorSkipsGenerationWhenNoTargetsConfigured() throws Exception {
        final Path specFile = tempDir.resolve("interop.properties");
        Files.writeString(specFile, "allowlist=java.lang.Math#max\n", UTF_8);

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(
                specFile,
                tempDir.resolve("out")
        );

        assertTrue(artifact.sourceFiles().isEmpty());
        assertTrue(artifact.targets().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("out").resolve("dev/tsj/generated/interop")));
    }

    @Test
    void supportsTsj29BindingTargetsForConstructorsAndInstanceMembers() throws Exception {
        final Path specFile = tempDir.resolve("interop-tsj29.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.StringBuilder#$new,java.lang.StringBuilder#$instance$append,java.lang.StringBuilder#$instance$toString
                targets=java.lang.StringBuilder#$new,java.lang.StringBuilder#$instance$append,java.lang.StringBuilder#$instance$toString
                """,
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(
                specFile,
                tempDir.resolve("out-tsj29")
        );

        assertEquals(3, artifact.targets().size());
        assertEquals(1, artifact.sourceFiles().size());
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("invokeBinding(\"java.lang.StringBuilder\", \"$new\""));
        assertTrue(source.contains("invokeBinding(\"java.lang.StringBuilder\", \"$instance$append\""));
    }

    @Test
    void emitsSelectedTargetIdentityMetadataForDisambiguatedOverloadArgs() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType";
        final Path specFile = tempDir.resolve("interop-tsj54-selected.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#pick
                targets=%s#pick
                bindingArgs.pick=I
                """.formatted(fixtureClass, fixtureClass),
                UTF_8
        );

        new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-tsj54-selected"));

        final Properties metadata = readProperties(tempDir.resolve("out-tsj54-selected/interop-bridges.properties"));
        assertEquals("1", metadata.getProperty("selectedTarget.count"));
        assertEquals("pick", metadata.getProperty("selectedTarget.0.binding"));
        assertEquals(fixtureClass, metadata.getProperty("selectedTarget.0.owner"));
        assertEquals("pick", metadata.getProperty("selectedTarget.0.name"));
        assertEquals("(I)Ljava/lang/String;", metadata.getProperty("selectedTarget.0.descriptor"));
        assertEquals("STATIC_METHOD", metadata.getProperty("selectedTarget.0.invokeKind"));
    }

    @Test
    void bindingArgsFailsWhenNoApplicableCandidateExists() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType";
        final Path specFile = tempDir.resolve("interop-tsj54-no-applicable.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#pick
                targets=%s#pick
                bindingArgs.pick=Ljava/lang/String;
                """.formatted(fixtureClass, fixtureClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-tsj54-no-applicable"))
        );

        assertEquals("TSJ-INTEROP-INVALID", exception.code());
        assertTrue(exception.getMessage().contains("No applicable candidate"));
        assertTrue(exception.getMessage().contains("pick"));
    }

    @Test
    void bindingArgsFailsWhenBestCandidatesAreAmbiguous() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType";
        final Path specFile = tempDir.resolve("interop-tsj54-ambiguous.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#ambiguous
                targets=%s#ambiguous
                bindingArgs.ambiguous=Ljava/lang/String;
                """.formatted(fixtureClass, fixtureClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-tsj54-ambiguous"))
        );

        assertEquals("TSJ-INTEROP-INVALID", exception.code());
        assertTrue(exception.getMessage().contains("Ambiguous best candidates"));
        assertTrue(exception.getMessage().contains("ambiguous"));
    }

    @Test
    void rejectsUnknownTsj29BindingPrefixInInteropSpec() throws Exception {
        final Path specFile = tempDir.resolve("interop-tsj29-invalid.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#$instance$unknown$max
                targets=java.lang.Math#$instance$unknown$max
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-tsj29-invalid"))
        );

        assertEquals("TSJ-INTEROP-INVALID", exception.code());
        assertTrue(exception.getMessage().contains("$instance$unknown$max"));
    }

    @Test
    void emitsConfiguredClassAndBindingAnnotations() throws Exception {
        final Path specFile = tempDir.resolve("interop-annotations.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max
                targets=java.lang.Math#max
                classAnnotations=java.lang.Deprecated
                bindingAnnotations.max=java.lang.Deprecated
                """,
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(
                specFile,
                tempDir.resolve("out-annotations")
        );
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);

        assertTrue(source.contains("@java.lang.Deprecated"));
        assertTrue(source.contains("public final class JavaLangMathBridge"));
        assertTrue(source.contains("public static Object max"));
    }

    @Test
    void rejectsInvalidAnnotationTypeConfiguration() throws Exception {
        final Path specFile = tempDir.resolve("interop-invalid-annotation.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max
                targets=java.lang.Math#max
                classAnnotations=java.lang.String
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-invalid-annotation"))
        );

        assertEquals("TSJ-INTEROP-ANNOTATION", exception.code());
        assertEquals("TSJ32-ANNOTATION-SYNTAX", exception.featureId());
        assertTrue(exception.guidance() != null && exception.guidance().contains("runtime-visible"));
    }

    @Test
    void emitsSpringConfigurationBeanMethodsForConstructorTarget() throws Exception {
        final String serviceClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType$Service";
        final String dependencyAClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType$DependencyA";
        final String dependencyBClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType$DependencyB";
        final Path specFile = tempDir.resolve("interop-spring-constructor.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#$new
                targets=%s#$new
                springConfiguration=true
                springBeanTargets=%s#$new
                """.formatted(serviceClass, serviceClass, serviceClass),
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(
                specFile,
                tempDir.resolve("out-spring-constructor")
        );
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);

        assertTrue(source.contains("@org.springframework.context.annotation.Configuration"));
        assertTrue(source.contains("@org.springframework.context.annotation.Bean"));
        assertTrue(source.contains("public dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType.Service _new("));
        assertTrue(source.contains(dependencyAClass.replace('$', '.') + " arg0"));
        assertTrue(source.contains(dependencyBClass.replace('$', '.') + " arg1"));
        assertTrue(source.contains("invokeBinding(\"" + serviceClass + "\", \"$new\", arg0, arg1)"));
    }

    @Test
    void springBeanTargetPreservesParameterizedSignatureMetadata() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType";
        final Path specFile = tempDir.resolve("interop-spring-generics.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#copyLabels
                targets=%s#copyLabels
                springConfiguration=true
                springBeanTargets=%s#copyLabels
                """.formatted(fixtureClass, fixtureClass, fixtureClass),
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(
                specFile,
                tempDir.resolve("out-spring-generics")
        );
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);

        assertTrue(source.contains("public java.util.List<java.lang.String> copyLabels("));
        assertTrue(source.contains("final java.util.List<java.lang.String> arg0"));
    }

    @Test
    void springBeanTargetWithTypeVariableSignatureFailsWithMetadataDiagnostic() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType";
        final Path specFile = tempDir.resolve("interop-spring-generic-typevar.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#identity
                targets=%s#identity
                springConfiguration=true
                springBeanTargets=%s#identity
                """.formatted(fixtureClass, fixtureClass, fixtureClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-spring-generic-typevar"))
        );

        assertEquals("TSJ-INTEROP-METADATA", exception.code());
        assertEquals("TSJ39-ABI-METADATA", exception.featureId());
        assertTrue(exception.getMessage().contains("identity"));
    }

    @Test
    void springBeanTargetWithAmbiguousConstructorsFailsWithDiagnostic() throws Exception {
        final String multiConstructorClass =
                "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType$MultiConstructorService";
        final Path specFile = tempDir.resolve("interop-spring-ambiguous.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#$new
                targets=%s#$new
                springConfiguration=true
                springBeanTargets=%s#$new
                """.formatted(multiConstructorClass, multiConstructorClass, multiConstructorClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-spring-ambiguous"))
        );

        assertEquals("TSJ-INTEROP-SPRING", exception.code());
        assertEquals("TSJ33-SPRING-BEAN", exception.featureId());
        assertTrue(exception.getMessage().contains("exactly one public constructor"));
    }

    @Test
    void springBeanTargetRequiresSpringConfigurationFlag() throws Exception {
        final String serviceClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType$Service";
        final Path specFile = tempDir.resolve("interop-spring-missing-config.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#$new
                targets=%s#$new
                springBeanTargets=%s#$new
                """.formatted(serviceClass, serviceClass, serviceClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-spring-missing-config"))
        );

        assertEquals("TSJ-INTEROP-SPRING", exception.code());
        assertTrue(exception.getMessage().contains("springConfiguration=true"));
    }

    @Test
    void emitsSpringWebControllerMappingsAndErrorHandlers() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringWebFixtureType";
        final String dtoClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringWebFixtureType$UserDto";
        final Path specFile = tempDir.resolve("interop-spring-web.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#findUser,%s#validateUser
                targets=%s#findUser,%s#validateUser
                springWebController=true
                springWebBasePath=/users
                springRequestMappings.findUser=GET /find
                springRequestMappings.validateUser=GET /validate
                springErrorMappings=java.lang.IllegalArgumentException:400
                """.formatted(fixtureClass, fixtureClass, fixtureClass, fixtureClass),
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(
                specFile,
                tempDir.resolve("out-spring-web")
        );
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);

        assertTrue(source.contains("@org.springframework.web.bind.annotation.RestController"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestMapping(\"/users\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.GetMapping(\"/find\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.GetMapping(\"/validate\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestParam(\"arg0\")"));
        assertTrue(source.contains("public " + dtoClass.replace('$', '.') + " findUser("));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.ExceptionHandler(java.lang.IllegalArgumentException.class)"));
        assertTrue(source.contains(
                "@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)"
        ));
    }

    @Test
    void springRequestMappingsRequireWebControllerFlag() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringWebFixtureType";
        final Path specFile = tempDir.resolve("interop-spring-web-flag.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#findUser
                targets=%s#findUser
                springRequestMappings.findUser=GET /find
                """.formatted(fixtureClass, fixtureClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-spring-web-flag"))
        );

        assertEquals("TSJ-INTEROP-WEB", exception.code());
        assertEquals("TSJ34-SPRING-WEB", exception.featureId());
        assertTrue(exception.getMessage().contains("springWebController=true"));
    }

    @Test
    void springRequestMappingRejectsInstanceBindingTarget() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropSpringWebFixtureType$InstanceService";
        final Path specFile = tempDir.resolve("interop-spring-web-instance.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#$instance$find
                targets=%s#$instance$find
                springWebController=true
                springRequestMappings.$instance$find=GET /find
                """.formatted(fixtureClass, fixtureClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-spring-web-instance"))
        );

        assertEquals("TSJ-INTEROP-WEB", exception.code());
        assertTrue(exception.getMessage().contains("constructor"));
    }

    private static Properties readProperties(final Path path) throws Exception {
        final Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }
}
