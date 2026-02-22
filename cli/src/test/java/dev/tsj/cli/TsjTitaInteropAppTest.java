package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjTitaInteropAppTest {
    @TempDir
    Path tempDir;

    @Test
    void sharedModeRunsTitaAndPersistsInteropArtifacts() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path titaDir = repoRoot.resolve("examples").resolve("tita");
        final Path depsDir = tempDir.resolve("deps");
        final Path fixtureJar = buildTitaFixtureJar(titaDir, depsDir);
        final Path duplicateJar = buildSourceJar(
                titaDir.resolve("fixtures-src").resolve("duplicate"),
                depsDir.resolve("tita-duplicates-1.0.jar"),
                List.of()
        );
        final Path outDir = tempDir.resolve("tita-shared-out");
        final Path entryFile = titaDir.resolve("src").resolve("main.ts");

        final String classpath = String.join(
                File.pathSeparator,
                List.of(
                        fixtureJar.toString(),
                        duplicateJar.toString(),
                        "jrt:/java.base/java/util"
                )
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "shared"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        final String stderrText = stderr.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderrText
        );
        assertEquals("", stderrText);
        assertTrue(stdoutText.contains("OVERLOAD_OK"), stdoutText);
        assertTrue(stdoutText.contains("GENERICS_OK"), stdoutText);
        assertTrue(stdoutText.contains("NULLABILITY_OK"), stdoutText);
        assertTrue(stdoutText.contains("INHERITANCE_OK"), stdoutText);
        assertTrue(stdoutText.contains("SAM_OK"), stdoutText);
        assertTrue(stdoutText.contains("PROPS_OK"), stdoutText);
        assertTrue(stdoutText.contains("MRJAR_OK"), stdoutText);
        assertTrue(stdoutText.contains("JRT_OK"), stdoutText);
        assertTrue(stdoutText.contains("TITA_OK"), stdoutText);

        final Properties artifact = loadArtifact(outDir.resolve("program.tsj.properties"));
        final Path classIndexPath = Path.of(artifact.getProperty("interopClasspath.classIndex.path"))
                .toAbsolutePath()
                .normalize();
        assertTrue(Files.exists(classIndexPath), classIndexPath.toString());
        assertTrue(Integer.parseInt(artifact.getProperty("interopClasspath.classIndex.symbolCount")) > 0);
        assertTrue(Integer.parseInt(artifact.getProperty("interopClasspath.classIndex.duplicateCount")) > 0);
        final String classIndexJson = Files.readString(classIndexPath, UTF_8);
        assertTrue(classIndexJson.contains("\"internalName\":\"dev/tita/fixtures/Conflict\""), classIndexJson);
        assertTrue(classIndexJson.contains("\"rule\":\"mediated-order\""), classIndexJson);
        assertTrue(classIndexJson.contains("\"internalName\":\"dev/tita/fixtures/mr/MrPick\""), classIndexJson);
        assertTrue(
                classIndexJson.contains("\"entry\":\"META-INF/versions/11/dev/tita/fixtures/mr/MrPick.class\""),
                classIndexJson
        );
        assertTrue(classIndexJson.contains("\"mrJarSource\":\"META-INF/versions/11\""), classIndexJson);
        assertFalse(
                classIndexJson.contains("\"internalName\":\"META-INF/versions/11/dev/tita/fixtures/mr/MrPick\""),
                classIndexJson
        );
        assertTrue(classIndexJson.contains("\"internalName\":\"java/util/Optional\""), classIndexJson);
        assertTrue(
                Integer.parseInt(
                        artifact.getProperty("interopClasspath.classIndex.mrJarWinnerCount", "0")
                ) > 0
        );
        assertTrue(
                Integer.parseInt(
                        artifact.getProperty("interopClasspath.classIndex.mrJarVersionedWinnerCount", "0")
                ) > 0
        );

        final int selectedTargetCount = Integer.parseInt(artifact.getProperty("interopBridges.selectedTargetCount"));
        assertTrue(selectedTargetCount > 0);
        boolean sawOverloadsSelection = false;
        for (int index = 0; index < selectedTargetCount; index++) {
            final String className = artifact.getProperty("interopBridges.selectedTarget." + index + ".className");
            if ("dev.tita.fixtures.Overloads".equals(className)) {
                sawOverloadsSelection = true;
                break;
            }
        }
        assertTrue(sawOverloadsSelection);
    }

    @Test
    void appIsolatedModeFailsFastWithDeterministicConflictDiagnostic() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path titaDir = repoRoot.resolve("examples").resolve("tita");
        final Path depsDir = tempDir.resolve("deps");
        final Path fixtureJar = buildTitaFixtureJar(titaDir, depsDir);
        final Path appConflictJar = buildSourceJar(
                titaDir.resolve("fixtures-src").resolve("app-conflict"),
                depsDir.resolve("tita-app-conflict-1.0.jar"),
                List.of()
        );
        final Path outDir = tempDir.resolve("tita-isolated-out");
        final Path entryFile = titaDir.resolve("src").resolve("main.ts");

        final String classpath = String.join(
                File.pathSeparator,
                List.of(fixtureJar.toString(), appConflictJar.toString())
        );
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "app-isolated"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertNotEquals(0, exitCode, "stdout:\n" + stdout.toString(UTF_8) + "\nstderr:\n" + stderrText);
        assertTrue(stderrText.contains("\"code\":\"TSJ-RUN-009\""), stderrText);
        assertTrue(stderrText.contains("app-isolated"), stderrText);
        assertTrue(stderrText.contains("dev.tsj.generated.MainProgram"), stderrText);
        assertTrue(stderrText.contains("\"appOrigin\":\""), stderrText);
        assertTrue(stderrText.contains("\"dependencyOrigin\":\""), stderrText);
        assertTrue(stderrText.contains("--classloader-isolation shared"), stderrText);
    }

    @Test
    void sharedModeIsDeterministicForClassIndexAndSelectedTargetMetadataAcrossRepeatedRuns() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path titaDir = repoRoot.resolve("examples").resolve("tita");
        final Path depsDir = tempDir.resolve("deps");
        final Path fixtureJar = buildTitaFixtureJar(titaDir, depsDir);
        final Path duplicateJar = buildSourceJar(
                titaDir.resolve("fixtures-src").resolve("duplicate"),
                depsDir.resolve("tita-duplicates-1.0.jar"),
                List.of()
        );
        final Path outDir = tempDir.resolve("tita-shared-determinism-out");
        final Path entryFile = titaDir.resolve("src").resolve("main.ts");
        final String classpath = String.join(
                File.pathSeparator,
                List.of(
                        fixtureJar.toString(),
                        duplicateJar.toString(),
                        "jrt:/java.base/java/util"
                )
        );

        final ByteArrayOutputStream firstStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream firstStderr = new ByteArrayOutputStream();
        final int firstExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "shared"
                },
                new PrintStream(firstStdout),
                new PrintStream(firstStderr)
        );

        final String firstStderrText = firstStderr.toString(UTF_8);
        assertEquals(0, firstExitCode, "stderr:\n" + firstStderrText);
        assertEquals("", firstStderrText);
        final Properties firstArtifact = loadArtifact(outDir.resolve("program.tsj.properties"));
        final Path firstClassIndex = Path.of(firstArtifact.getProperty("interopClasspath.classIndex.path"))
                .toAbsolutePath()
                .normalize();
        final String firstClassIndexJson = Files.readString(firstClassIndex, UTF_8);
        final String firstSelectedSnapshot = selectedTargetSnapshot(firstArtifact);

        final ByteArrayOutputStream secondStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream secondStderr = new ByteArrayOutputStream();
        final int secondExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "shared"
                },
                new PrintStream(secondStdout),
                new PrintStream(secondStderr)
        );

        final String secondStderrText = secondStderr.toString(UTF_8);
        assertEquals(0, secondExitCode, "stderr:\n" + secondStderrText);
        assertEquals("", secondStderrText);
        final Properties secondArtifact = loadArtifact(outDir.resolve("program.tsj.properties"));
        final Path secondClassIndex = Path.of(secondArtifact.getProperty("interopClasspath.classIndex.path"))
                .toAbsolutePath()
                .normalize();
        final String secondClassIndexJson = Files.readString(secondClassIndex, UTF_8);
        final String secondSelectedSnapshot = selectedTargetSnapshot(secondArtifact);

        assertEquals(firstClassIndexJson, secondClassIndexJson);
        assertEquals(firstSelectedSnapshot, secondSelectedSnapshot);
        assertEquals(
                firstArtifact.getProperty("interopClasspath.classIndex.symbolCount"),
                secondArtifact.getProperty("interopClasspath.classIndex.symbolCount")
        );
        assertEquals(
                firstArtifact.getProperty("interopClasspath.classIndex.duplicateCount"),
                secondArtifact.getProperty("interopClasspath.classIndex.duplicateCount")
        );
        assertEquals(
                firstArtifact.getProperty("interopClasspath.classIndex.mrJarWinnerCount"),
                secondArtifact.getProperty("interopClasspath.classIndex.mrJarWinnerCount")
        );
        assertEquals(
                firstArtifact.getProperty("interopClasspath.classIndex.mrJarVersionedWinnerCount"),
                secondArtifact.getProperty("interopClasspath.classIndex.mrJarVersionedWinnerCount")
        );
        assertEquals(
                firstArtifact.getProperty("interopBridges.selectedTargetCount"),
                secondArtifact.getProperty("interopBridges.selectedTargetCount")
        );
    }

    @Test
    void appIsolatedModeConflictDiagnosticIsDeterministicAcrossRepeatedRuns() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path titaDir = repoRoot.resolve("examples").resolve("tita");
        final Path depsDir = tempDir.resolve("deps");
        final Path fixtureJar = buildTitaFixtureJar(titaDir, depsDir);
        final Path appConflictJar = buildSourceJar(
                titaDir.resolve("fixtures-src").resolve("app-conflict"),
                depsDir.resolve("tita-app-conflict-1.0.jar"),
                List.of()
        );
        final Path outDir = tempDir.resolve("tita-isolated-determinism-out");
        final Path entryFile = titaDir.resolve("src").resolve("main.ts");
        final String classpath = String.join(
                File.pathSeparator,
                List.of(fixtureJar.toString(), appConflictJar.toString())
        );

        final ByteArrayOutputStream firstStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream firstStderr = new ByteArrayOutputStream();
        final int firstExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "app-isolated"
                },
                new PrintStream(firstStdout),
                new PrintStream(firstStderr)
        );

        final String firstStderrText = firstStderr.toString(UTF_8);
        assertNotEquals(0, firstExitCode, "stderr:\n" + firstStderrText);
        assertTrue(firstStderrText.contains("\"code\":\"TSJ-RUN-009\""), firstStderrText);
        final String firstAppOrigin = extractJsonField(firstStderrText, "appOrigin");
        final String firstDependencyOrigin = extractJsonField(firstStderrText, "dependencyOrigin");
        final String firstClassName = extractJsonField(firstStderrText, "className");
        final String firstConflictClass = extractJsonField(firstStderrText, "conflictClass");
        assertTrue(firstAppOrigin != null && !firstAppOrigin.isBlank(), firstStderrText);
        assertTrue(firstDependencyOrigin != null && !firstDependencyOrigin.isBlank(), firstStderrText);
        assertEquals("dev.tsj.generated.MainProgram", firstClassName);
        assertEquals("dev.tsj.generated.MainProgram", firstConflictClass);

        final ByteArrayOutputStream secondStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream secondStderr = new ByteArrayOutputStream();
        final int secondExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "app-isolated"
                },
                new PrintStream(secondStdout),
                new PrintStream(secondStderr)
        );

        final String secondStderrText = secondStderr.toString(UTF_8);
        assertNotEquals(0, secondExitCode, "stderr:\n" + secondStderrText);
        assertTrue(secondStderrText.contains("\"code\":\"TSJ-RUN-009\""), secondStderrText);
        final String secondAppOrigin = extractJsonField(secondStderrText, "appOrigin");
        final String secondDependencyOrigin = extractJsonField(secondStderrText, "dependencyOrigin");
        final String secondClassName = extractJsonField(secondStderrText, "className");
        final String secondConflictClass = extractJsonField(secondStderrText, "conflictClass");

        assertEquals(firstAppOrigin, secondAppOrigin);
        assertEquals(firstDependencyOrigin, secondDependencyOrigin);
        assertEquals(firstClassName, secondClassName);
        assertEquals(firstConflictClass, secondConflictClass);
    }

    @Test
    void titaRequiresFixtureJarForJavaImports() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path titaDir = repoRoot.resolve("examples").resolve("tita");
        final Path outDir = tempDir.resolve("tita-missing-jar-out");
        final Path entryFile = titaDir.resolve("src").resolve("main.ts");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        "jrt:/java.base/java/util",
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertNotEquals(0, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-RUN-006\""), stderrText);
        assertTrue(stderrText.contains("dev.tita.fixtures.Overloads"), stderrText);
    }

    private static String selectedTargetSnapshot(final Properties artifact) {
        final int selectedTargetCount = Integer.parseInt(artifact.getProperty("interopBridges.selectedTargetCount"));
        final List<String> rows = new ArrayList<>();
        for (int index = 0; index < selectedTargetCount; index++) {
            final String prefix = "interopBridges.selectedTarget." + index + ".";
            rows.add(
                    artifact.getProperty(prefix + "className", "")
                            + "|"
                            + artifact.getProperty(prefix + "binding", "")
                            + "|"
                            + artifact.getProperty(prefix + "owner", "")
                            + "|"
                            + artifact.getProperty(prefix + "name", "")
                            + "|"
                            + artifact.getProperty(prefix + "descriptor", "")
                            + "|"
                            + artifact.getProperty(prefix + "invokeKind", "")
            );
        }
        return String.join("\n", rows);
    }

    private static String extractJsonField(final String diagnostic, final String fieldName) {
        final String marker = "\"" + fieldName + "\":\"";
        final int startIndex = diagnostic.indexOf(marker);
        if (startIndex < 0) {
            return null;
        }
        final int valueStart = startIndex + marker.length();
        final int valueEnd = diagnostic.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return diagnostic.substring(valueStart, valueEnd);
    }

    private static Properties loadArtifact(final Path artifactPath) throws IOException {
        final Properties properties = new Properties();
        try (java.io.InputStream inputStream = Files.newInputStream(artifactPath)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("README.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve TSJ repository root.");
    }

    private Path buildTitaFixtureJar(final Path titaDir, final Path depsDir) throws Exception {
        final Path baseSourcesRoot = titaDir.resolve("fixtures-src").resolve("base");
        final Path v11SourcesRoot = titaDir.resolve("fixtures-src").resolve("v11");
        final Path classesBase = tempDir.resolve("tita-fixture").resolve("classes-base");
        final Path classesV11 = tempDir.resolve("tita-fixture").resolve("classes-v11");
        compileJavaSources(baseSourcesRoot, classesBase, List.of("-parameters"));
        compileJavaSources(
                v11SourcesRoot,
                classesV11,
                List.of("--release", "11", "-parameters", "-classpath", classesBase.toString())
        );

        Files.createDirectories(depsDir);
        final Path jarPath = depsDir.resolve("tita-fixtures-1.0.jar");
        final Manifest manifest = new Manifest();
        final Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Automatic-Module-Name", "dev.tita.fixtures");
        attributes.putValue("Multi-Release", "true");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            addJarEntries(jarOutputStream, classesBase, "");
            addJarEntries(jarOutputStream, classesV11, "META-INF/versions/11/");
        }
        return jarPath;
    }

    private Path buildSourceJar(
            final Path sourceRoot,
            final Path jarPath,
            final List<String> compilerOptions
    ) throws Exception {
        final Path classesDir = tempDir.resolve("tita-fixture")
                .resolve(jarPath.getFileName().toString().replace(".jar", "-classes"));
        compileJavaSources(sourceRoot, classesDir, compilerOptions);
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addJarEntries(jarOutputStream, classesDir, "");
        }
        return jarPath;
    }

    private static void compileJavaSources(
            final Path sourceRoot,
            final Path classesDir,
            final List<String> compilerOptions
    ) throws Exception {
        final List<Path> sourceFiles = listJavaSourceFiles(sourceRoot);
        if (sourceFiles.isEmpty()) {
            throw new IllegalStateException("No Java sources found under " + sourceRoot);
        }
        Files.createDirectories(classesDir);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable in current runtime.");
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final List<String> options = new ArrayList<>(compilerOptions);
            options.add("-d");
            options.add(classesDir.toString());
            final boolean success = Boolean.TRUE.equals(
                    compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
            );
            if (!success) {
                final StringBuilder message = new StringBuilder("javac compilation failed:");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    message.append("\n - ").append(diagnostic.getKind())
                            .append(" @ ").append(diagnostic.getSource())
                            .append(":").append(diagnostic.getLineNumber())
                            .append(": ").append(diagnostic.getMessage(java.util.Locale.ROOT));
                }
                throw new IllegalStateException(message.toString());
            }
        }
    }

    private static List<Path> listJavaSourceFiles(final Path sourceRoot) throws IOException {
        if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private static void addJarEntries(
            final JarOutputStream jarOutputStream,
            final Path classesRoot,
            final String entryPrefix
    ) throws IOException {
        if (!Files.exists(classesRoot)) {
            return;
        }
        final List<Path> classFiles;
        try (java.util.stream.Stream<Path> stream = Files.walk(classesRoot)) {
            classFiles = stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        for (Path classFile : classFiles) {
            final String relative = classesRoot.relativize(classFile).toString().replace(File.separatorChar, '/');
            final JarEntry entry = new JarEntry(entryPrefix + relative);
            jarOutputStream.putNextEntry(entry);
            Files.copy(classFile, jarOutputStream);
            jarOutputStream.closeEntry();
        }
    }
}
