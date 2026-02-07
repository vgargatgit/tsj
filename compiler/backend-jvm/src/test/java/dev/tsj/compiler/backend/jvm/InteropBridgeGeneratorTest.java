package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
        assertTrue(source.contains("invokeStatic(\"java.lang.Math\", \"max\""));
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
}
