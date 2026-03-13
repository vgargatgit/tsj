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
    void bindingArgsNullRespectsNonNullParameterNullabilityDuringSelection() throws Exception {
        final String fixtureClass = "dev.tsj.compiler.backend.jvm.fixtures.InteropNullabilityFixtureType";
        final Path specFile = tempDir.resolve("interop-tsj49-nonnull.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#requireNonNull
                targets=%s#requireNonNull
                bindingArgs.requireNonNull=null
                """.formatted(fixtureClass, fixtureClass),
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-tsj49-nonnull"))
        );

        assertEquals("TSJ-INTEROP-INVALID", exception.code());
        assertTrue(exception.getMessage().contains("requireNonNull"));
        assertTrue(exception.getMessage().contains("nullability"));
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
    void rejectsRetiredSpringBeanInteropSpecKeys() throws Exception {
        final Path specFile = tempDir.resolve("interop-spring-constructor.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max
                targets=java.lang.Math#max
                springConfiguration=true
                springBeanTargets=java.lang.Math#max
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-spring-constructor"))
        );

        assertEquals("TSJ-INTEROP-INVALID", exception.code());
        assertTrue(exception.getMessage().contains("springConfiguration"));
        assertTrue(exception.getMessage().contains("retired"));
    }

    @Test
    void rejectsRetiredSpringWebInteropSpecKeys() throws Exception {
        final Path specFile = tempDir.resolve("interop-spring-web.properties");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max
                targets=java.lang.Math#max
                springWebController=true
                springWebBasePath=/users
                springRequestMappings.max=GET /find
                springErrorMappings=java.lang.IllegalArgumentException:400
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-spring-web"))
        );

        assertEquals("TSJ-INTEROP-INVALID", exception.code());
        assertTrue(exception.getMessage().contains("springWebController"));
        assertTrue(exception.getMessage().contains("retired"));
    }

    private static Properties readProperties(final Path path) throws Exception {
        final Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }
}
