package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarFile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjGenericPackageCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void packageCommandPackagesPlainTsProgramAndSmokeRunsJar() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                entryFile,
                """
                console.log("tsj90-package-plain");
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("pkg-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--smoke-run"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode, "stderr:\n" + stderr.toString(UTF_8));
        assertEquals("", stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-PACKAGE-SUCCESS\""));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-PACKAGE-SMOKE-SUCCESS\""));

        final Path packagedJar = outDir.resolve("tsj-app.jar");
        assertTrue(Files.exists(packagedJar));
        final ProcessResult run = runJar(packagedJar);
        assertEquals(0, run.exitCode(), run.output());
        assertTrue(run.output().contains("tsj90-package-plain"), run.output());
    }

    @Test
    void packageCommandUsesProgramMainClassInJarManifestForPlainProgram() throws Exception {
        final Path entryFile = tempDir.resolve("plain-main.ts");
        Files.writeString(entryFile, "console.log('tsj90-manifest-plain');\n", UTF_8);
        final Path outDir = tempDir.resolve("plain-pkg-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode, "stderr:\n" + stderr.toString(UTF_8));
        assertEquals("", stderr.toString(UTF_8));

        final Properties artifact = new Properties();
        try (InputStream inputStream = Files.newInputStream(outDir.resolve("program.tsj.properties"))) {
            artifact.load(inputStream);
        }
        final String programMainClass = artifact.getProperty("mainClass");
        assertTrue(programMainClass != null && !programMainClass.isBlank());

        try (JarFile jarFile = new JarFile(outDir.resolve("tsj-app.jar").toFile())) {
            assertEquals(programMainClass, jarFile.getManifest().getMainAttributes().getValue("Main-Class"));
        }
    }

    @Test
    void packageCommandAddsDirectoryEntriesNeededForClasspathScanning() throws Exception {
        final Path entryFile = tempDir.resolve("dirs-main.ts");
        Files.writeString(
                entryFile,
                """
                class Owner {
                }

                console.log("tsj90-package-dirs");
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("dirs-pkg-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--mode",
                        "jvm-strict"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode, "stderr:\n" + stderr.toString(UTF_8));
        assertEquals("", stderr.toString(UTF_8));

        try (JarFile jarFile = new JarFile(outDir.resolve("tsj-app.jar").toFile())) {
            assertTrue(jarFile.getJarEntry("dev/") != null);
            assertTrue(jarFile.getJarEntry("dev/tsj/") != null);
            assertTrue(jarFile.getJarEntry("dev/tsj/generated/") != null);
            assertTrue(jarFile.getJarEntry("META-INF/") != null);
            assertTrue(jarFile.getJarEntry("META-INF/tsj/") != null);
        }
    }

    private static ProcessResult runJar(final Path jarPath) throws Exception {
        final Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString(),
                "-jar",
                jarPath.toAbsolutePath().normalize().toString()
        ).redirectErrorStream(true).start();
        final String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), UTF_8);
        }
        final int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
