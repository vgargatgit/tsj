package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjCliTest {
    @TempDir
    Path tempDir;

    @Test
    void compileCreatesArtifactAndEmitsStructuredSuccess() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(entryFile, "export const answer = 42;\n", UTF_8);
        final Path outDir = tempDir.resolve("build");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outDir.resolve("program.tsj.properties")));
        assertTrue(Files.exists(outDir.resolve("classes")));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-COMPILE-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void compilePersistsInteropClasspathEntriesFromJarAndClasspathOptions() throws Exception {
        final Path entryFile = tempDir.resolve("classpath-meta.ts");
        Files.writeString(entryFile, "console.log('meta');\n", UTF_8);
        final Path outDir = tempDir.resolve("classpath-meta-out");
        final Path jarFile = buildInteropJar(
                "sample.interop.Numbers",
                """
                package sample.interop;

                public final class Numbers {
                    private Numbers() {
                    }

                    public static int triple(final int value) {
                        return value * 3;
                    }
                }
                """
        );
        final Path extraDir = tempDir.resolve("cp-extra");
        Files.createDirectories(extraDir);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--jar",
                        jarFile.toString(),
                        "--classpath",
                        extraDir.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("2", artifact.getProperty("interopClasspath.count"));
        assertEquals(jarFile.toAbsolutePath().normalize().toString(), artifact.getProperty("interopClasspath.0"));
        assertEquals(extraDir.toAbsolutePath().normalize().toString(), artifact.getProperty("interopClasspath.1"));
    }

    @Test
    void compilePersistsClasspathSymbolIndexWithShadowDiagnosticsInSharedMode() throws Exception {
        final Path entryFile = tempDir.resolve("tsj45-shared-index.ts");
        Files.writeString(entryFile, "console.log('index');\n", UTF_8);
        final Path outDir = tempDir.resolve("tsj45-shared-index-out");

        final Path firstJar = buildInteropJar(
                "sample.tsj45.Duplicate",
                """
                package sample.tsj45;

                public final class Duplicate {
                    private Duplicate() {
                    }

                    public static String owner() {
                        return "first";
                    }
                }
                """,
                List.of(),
                "tsj45-first.jar"
        );
        final Path secondJar = buildInteropJar(
                "sample.tsj45.Duplicate",
                """
                package sample.tsj45;

                public final class Duplicate {
                    private Duplicate() {
                    }

                    public static String owner() {
                        return "second";
                    }
                }
                """,
                List.of(),
                "tsj45-second.jar"
        );

        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        firstJar + File.pathSeparator + secondJar
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())
        );

        assertEquals(0, exitCode);
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        final Path classIndexFile = Path.of(
                artifact.getProperty("interopClasspath.classIndex.path")
        ).toAbsolutePath().normalize();
        assertTrue(Files.exists(classIndexFile));
        assertTrue(Integer.parseInt(artifact.getProperty("interopClasspath.classIndex.symbolCount")) > 0);
        assertTrue(Integer.parseInt(artifact.getProperty("interopClasspath.classIndex.duplicateCount")) >= 1);
        final String classIndexJson = Files.readString(classIndexFile, UTF_8);
        assertTrue(classIndexJson.contains("\"internalName\":\"sample/tsj45/Duplicate\""));
        assertTrue(classIndexJson.contains("\"rule\":\"mediated-order\""), classIndexJson);
        assertTrue(classIndexJson.contains(firstJar.toAbsolutePath().normalize().toString()), classIndexJson);
        assertTrue(classIndexJson.contains(secondJar.toAbsolutePath().normalize().toString()), classIndexJson);
    }

    @Test
    void compileSupportsJrtClasspathEntriesInSymbolIndex() throws Exception {
        final Path entryFile = tempDir.resolve("tsj45-jrt-index.ts");
        Files.writeString(entryFile, "console.log('jrt');\n", UTF_8);
        final Path outDir = tempDir.resolve("tsj45-jrt-index-out");

        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        "jrt:/java.base/java/lang"
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())
        );

        assertEquals(0, exitCode);
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        final Path classIndexFile = Path.of(
                artifact.getProperty("interopClasspath.classIndex.path")
        ).toAbsolutePath().normalize();
        final String classIndexJson = Files.readString(classIndexFile, UTF_8);
        assertTrue(classIndexJson.contains("\"internalName\":\"java/lang/String\""), classIndexJson);
        assertTrue(classIndexJson.contains("\"moduleName\":\"java.base\""), classIndexJson);
        assertTrue(classIndexJson.contains("\"sourceKind\":\"jrt-module\""), classIndexJson);
    }

    @Test
    void compileSupportsMixedJarAndJrtClasspathEntries() throws Exception {
        final Path entryFile = tempDir.resolve("tsj45-mixed-jrt-classpath.ts");
        Files.writeString(entryFile, "console.log('mixed');\n", UTF_8);
        final Path outDir = tempDir.resolve("tsj45-mixed-jrt-classpath-out");
        final Path interopJar = buildInteropJar(
                "sample.mixed.Helper",
                """
                package sample.mixed;

                public final class Helper {
                    private Helper() {
                    }

                    public static String ping() {
                        return "pong";
                    }
                }
                """
        );

        final String classpath = interopJar + File.pathSeparator + "jrt:/java.base/java/lang";
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())
        );

        assertEquals(0, exitCode);
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        final Path classIndexFile = Path.of(
                artifact.getProperty("interopClasspath.classIndex.path")
        ).toAbsolutePath().normalize();
        final String classIndexJson = Files.readString(classIndexFile, UTF_8);
        assertTrue(classIndexJson.contains("\"internalName\":\"sample/mixed/Helper\""), classIndexJson);
        assertTrue(classIndexJson.contains("\"internalName\":\"java/lang/String\""), classIndexJson);
    }

    @Test
    void runSupportsExternalJarInteropViaJarOption() throws Exception {
        final Path entryFile = tempDir.resolve("run-jar-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { triple } from "java:sample.interop.Numbers";
                console.log("triple=" + triple(7));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.Numbers",
                """
                package sample.interop;

                public final class Numbers {
                    private Numbers() {
                    }

                    public static int triple(final int value) {
                        return value * 3;
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-jar-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("triple=21"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runAcceptsJrtClasspathEntries() throws Exception {
        final Path entryFile = tempDir.resolve("run-jrt-classpath.ts");
        Files.writeString(entryFile, "console.log('jrt-run-ok');\n", UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-jrt-classpath-out").toString(),
                        "--classpath",
                        "jrt:/java.base/java/lang"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("jrt-run-ok"));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runRoundTripsMixedJarAndJrtClasspathEntriesDeterministically() throws Exception {
        final Path entryFile = tempDir.resolve("run-jrt-mixed-roundtrip.ts");
        Files.writeString(
                entryFile,
                """
                import { ping } from "java:sample.mixed.Helper";
                console.log("mixed=" + ping());
                """,
                UTF_8
        );
        final Path interopJar = buildInteropJar(
                "sample.mixed.Helper",
                """
                package sample.mixed;

                public final class Helper {
                    private Helper() {
                    }

                    public static String ping() {
                        return "pong";
                    }
                }
                """
        );
        final String mixedClasspath = interopJar + File.pathSeparator + "jrt:/java.base/java/lang";

        final Path firstOutDir = tempDir.resolve("run-jrt-mixed-roundtrip-first-out");
        final ByteArrayOutputStream firstStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream firstStderr = new ByteArrayOutputStream();
        final int firstExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        firstOutDir.toString(),
                        "--classpath",
                        mixedClasspath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(firstStdout),
                new PrintStream(firstStderr)
        );

        final String firstStdoutText = firstStdout.toString(UTF_8);
        assertEquals(
                0,
                firstExitCode,
                "stdout:\n" + firstStdoutText + "\nstderr:\n" + firstStderr.toString(UTF_8)
        );
        assertTrue(firstStdoutText.contains("mixed=pong"));
        final Properties firstArtifact = loadArtifactProperties(firstOutDir.resolve("program.tsj.properties"));
        final List<String> firstClasspathEntries = classpathEntries(firstArtifact);
        assertTrue(firstClasspathEntries.contains(interopJar.toAbsolutePath().normalize().toString()));
        assertTrue(firstClasspathEntries.contains("jrt:/java.base/java/lang"));

        final String roundTripClasspath = String.join(File.pathSeparator, firstClasspathEntries);
        final Path secondOutDir = tempDir.resolve("run-jrt-mixed-roundtrip-second-out");
        final ByteArrayOutputStream secondStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream secondStderr = new ByteArrayOutputStream();
        final int secondExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        secondOutDir.toString(),
                        "--classpath",
                        roundTripClasspath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(secondStdout),
                new PrintStream(secondStderr)
        );

        final String secondStdoutText = secondStdout.toString(UTF_8);
        assertEquals(
                0,
                secondExitCode,
                "stdout:\n" + secondStdoutText + "\nstderr:\n" + secondStderr.toString(UTF_8)
        );
        assertTrue(secondStdoutText.contains("mixed=pong"));
        final Properties secondArtifact = loadArtifactProperties(secondOutDir.resolve("program.tsj.properties"));
        assertEquals(firstClasspathEntries, classpathEntries(secondArtifact));
    }

    @Test
    void runSupportsExternalJarInteropViaClasspathOption() throws Exception {
        final Path entryFile = tempDir.resolve("run-classpath-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { triple } from "java:sample.interop.Numbers";
                console.log("triple=" + triple(5));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.Numbers",
                """
                package sample.interop;

                public final class Numbers {
                    private Numbers() {
                    }

                    public static int triple(final int value) {
                        return value * 3;
                    }
                }
                """
        );
        final Path emptyDir = tempDir.resolve("empty-classpath-dir");
        Files.createDirectories(emptyDir);
        final String classpath = jarFile + File.pathSeparator + emptyDir;

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-classpath-out").toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("triple=15"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runSupportsMultiJarDependencyGraphClasspath() throws Exception {
        final Path helperJar = buildInteropJar(
                "sample.graph.Helper",
                """
                package sample.graph;

                public final class Helper {
                    private Helper() {
                    }

                    public static String message() {
                        return "helper";
                    }
                }
                """,
                List.of(),
                "graph-helper-1.0.jar"
        );
        final Path apiJar = buildInteropJar(
                "sample.graph.Api",
                """
                package sample.graph;

                public final class Api {
                    private Api() {
                    }

                    public static String describe() {
                        return "api->" + Helper.message();
                    }
                }
                """,
                List.of(helperJar),
                "graph-api-1.0.jar"
        );

        final Path entryFile = tempDir.resolve("run-graph.ts");
        Files.writeString(
                entryFile,
                """
                import { describe } from "java:sample.graph.Api";
                console.log("chain=" + describe());
                """,
                UTF_8
        );

        final String classpath = apiJar + File.pathSeparator + helperJar;
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-graph-out").toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("chain=api->helper"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void compileRejectsConflictingJarVersionsInClasspath() throws Exception {
        final Path baseJar = buildInteropJar(
                "sample.conflict.ConflictLib",
                """
                package sample.conflict;

                public final class ConflictLib {
                    private ConflictLib() {
                    }
                }
                """
        );
        final Path versionOne = tempDir.resolve("conflict-lib-1.0.jar");
        final Path versionTwo = tempDir.resolve("conflict-lib-2.0.jar");
        Files.copy(baseJar, versionOne);
        Files.copy(baseJar, versionTwo);

        final Path entryFile = tempDir.resolve("conflict-main.ts");
        Files.writeString(entryFile, "console.log('classpath conflict');\n", UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("conflict-out").toString(),
                        "--jar",
                        versionOne.toString(),
                        "--jar",
                        versionTwo.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(2, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-CLASSPATH-CONFLICT\""));
        assertTrue(stderrText.contains("conflict-lib"));
        assertTrue(stderrText.contains("1.0"));
        assertTrue(stderrText.contains("2.0"));
    }

    @Test
    void runMediatesTransitiveDependencyGraphUsingNearestRule() throws Exception {
        final MavenCoordinate sharedOneCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "1.0.0");
        final MavenCoordinate sharedTwoCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "2.0.0");
        final MavenCoordinate bridgeCoordinate = new MavenCoordinate("sample.graph", "bridge-lib", "1.0.0");
        final MavenCoordinate nearCoordinate = new MavenCoordinate("sample.graph", "near-api", "1.0.0");
        final MavenCoordinate farCoordinate = new MavenCoordinate("sample.graph", "far-api", "1.0.0");

        final Path sharedOne = buildInteropJarWithMavenMetadata(
                "sample.graph.Shared",
                """
                package sample.graph;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v1";
                    }
                }
                """,
                List.of(),
                "shared-lib-1.0.0.jar",
                sharedOneCoordinate,
                List.of()
        );
        final Path sharedTwo = buildInteropJarWithMavenMetadata(
                "sample.graph.Shared",
                """
                package sample.graph;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v2";
                    }
                }
                """,
                List.of(),
                "shared-lib-2.0.0.jar",
                sharedTwoCoordinate,
                List.of()
        );
        final Path bridgeJar = buildInteropJarWithMavenMetadata(
                "sample.graph.Bridge",
                """
                package sample.graph;

                public final class Bridge {
                    private Bridge() {
                    }

                    public static String relay() {
                        return Shared.label();
                    }
                }
                """,
                List.of(sharedTwo),
                "bridge-lib-1.0.0.jar",
                bridgeCoordinate,
                List.of(sharedTwoCoordinate)
        );
        final Path nearJar = buildInteropJarWithMavenMetadata(
                "sample.graph.NearApi",
                """
                package sample.graph;

                public final class NearApi {
                    private NearApi() {
                    }

                    public static String describe() {
                        return "near->" + Shared.label();
                    }
                }
                """,
                List.of(sharedOne),
                "near-api-1.0.0.jar",
                nearCoordinate,
                List.of(sharedOneCoordinate)
        );
        final Path farJar = buildInteropJarWithMavenMetadata(
                "sample.graph.FarApi",
                """
                package sample.graph;

                public final class FarApi {
                    private FarApi() {
                    }

                    public static String describe() {
                        return "far->" + Bridge.relay();
                    }
                }
                """,
                List.of(bridgeJar),
                "far-api-1.0.0.jar",
                farCoordinate,
                List.of(bridgeCoordinate)
        );

        final Path entryFile = tempDir.resolve("tsj40a-nearest.ts");
        Files.writeString(
                entryFile,
                """
                import { describe as nearDescribe } from "java:sample.graph.NearApi";
                import { describe as farDescribe } from "java:sample.graph.FarApi";

                console.log(nearDescribe());
                console.log(farDescribe());
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj40a-nearest-out");
        final String classpath = String.join(
                File.pathSeparator,
                nearJar.toString(),
                farJar.toString(),
                bridgeJar.toString(),
                sharedOne.toString(),
                sharedTwo.toString()
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
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("near->shared-v1"));
        assertTrue(stdoutText.contains("far->shared-v1"));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertMediationDecisionPresent(artifact, "sample.graph:shared-lib", "1.0.0", "2.0.0", "nearest");
        assertTrue(classpathEntries(artifact).contains(sharedOne.toAbsolutePath().normalize().toString()));
        assertFalse(classpathEntries(artifact).contains(sharedTwo.toAbsolutePath().normalize().toString()));
    }

    @Test
    void runKeepsNearestMediationWinnerStableWhenSharedClasspathInputsAreReordered() throws Exception {
        final MavenCoordinate sharedOneCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "1.0.0");
        final MavenCoordinate sharedTwoCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "2.0.0");
        final MavenCoordinate bridgeCoordinate = new MavenCoordinate("sample.graph", "bridge-lib", "1.0.0");
        final MavenCoordinate nearCoordinate = new MavenCoordinate("sample.graph", "near-api", "1.0.0");
        final MavenCoordinate farCoordinate = new MavenCoordinate("sample.graph", "far-api", "1.0.0");

        final Path sharedOne = buildInteropJarWithMavenMetadata(
                "sample.graph.Shared",
                """
                package sample.graph;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v1";
                    }
                }
                """,
                List.of(),
                "shared-lib-1.0.0.jar",
                sharedOneCoordinate,
                List.of()
        );
        final Path sharedTwo = buildInteropJarWithMavenMetadata(
                "sample.graph.Shared",
                """
                package sample.graph;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v2";
                    }
                }
                """,
                List.of(),
                "shared-lib-2.0.0.jar",
                sharedTwoCoordinate,
                List.of()
        );
        final Path bridgeJar = buildInteropJarWithMavenMetadata(
                "sample.graph.Bridge",
                """
                package sample.graph;

                public final class Bridge {
                    private Bridge() {
                    }

                    public static String relay() {
                        return Shared.label();
                    }
                }
                """,
                List.of(sharedTwo),
                "bridge-lib-1.0.0.jar",
                bridgeCoordinate,
                List.of(sharedTwoCoordinate)
        );
        final Path nearJar = buildInteropJarWithMavenMetadata(
                "sample.graph.NearApi",
                """
                package sample.graph;

                public final class NearApi {
                    private NearApi() {
                    }

                    public static String describe() {
                        return "near->" + Shared.label();
                    }
                }
                """,
                List.of(sharedOne),
                "near-api-1.0.0.jar",
                nearCoordinate,
                List.of(sharedOneCoordinate)
        );
        final Path farJar = buildInteropJarWithMavenMetadata(
                "sample.graph.FarApi",
                """
                package sample.graph;

                public final class FarApi {
                    private FarApi() {
                    }

                    public static String describe() {
                        return "far->" + Bridge.relay();
                    }
                }
                """,
                List.of(bridgeJar),
                "far-api-1.0.0.jar",
                farCoordinate,
                List.of(bridgeCoordinate)
        );

        final Path entryFile = tempDir.resolve("tsj40a-nearest-reordered.ts");
        Files.writeString(
                entryFile,
                """
                import { describe as nearDescribe } from "java:sample.graph.NearApi";
                import { describe as farDescribe } from "java:sample.graph.FarApi";

                console.log(nearDescribe());
                console.log(farDescribe());
                """,
                UTF_8
        );

        final Path firstOutDir = tempDir.resolve("tsj40a-nearest-reordered-first-out");
        final String firstClasspath = String.join(
                File.pathSeparator,
                nearJar.toString(),
                farJar.toString(),
                bridgeJar.toString(),
                sharedOne.toString(),
                sharedTwo.toString()
        );
        final ByteArrayOutputStream firstStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream firstStderr = new ByteArrayOutputStream();
        final int firstExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        firstOutDir.toString(),
                        "--classpath",
                        firstClasspath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "shared"
                },
                new PrintStream(firstStdout),
                new PrintStream(firstStderr)
        );

        final Path secondOutDir = tempDir.resolve("tsj40a-nearest-reordered-second-out");
        final String secondClasspath = String.join(
                File.pathSeparator,
                sharedTwo.toString(),
                bridgeJar.toString(),
                farJar.toString(),
                nearJar.toString(),
                sharedOne.toString()
        );
        final ByteArrayOutputStream secondStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream secondStderr = new ByteArrayOutputStream();
        final int secondExitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        secondOutDir.toString(),
                        "--classpath",
                        secondClasspath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "shared"
                },
                new PrintStream(secondStdout),
                new PrintStream(secondStderr)
        );

        final String firstStdoutText = firstStdout.toString(UTF_8);
        final String secondStdoutText = secondStdout.toString(UTF_8);
        assertEquals(
                0,
                firstExitCode,
                "stdout:\n" + firstStdoutText + "\nstderr:\n" + firstStderr.toString(UTF_8)
        );
        assertEquals(
                0,
                secondExitCode,
                "stdout:\n" + secondStdoutText + "\nstderr:\n" + secondStderr.toString(UTF_8)
        );
        assertTrue(firstStdoutText.contains("near->shared-v1"));
        assertTrue(firstStdoutText.contains("far->shared-v1"));
        assertTrue(secondStdoutText.contains("near->shared-v1"));
        assertTrue(secondStdoutText.contains("far->shared-v1"));

        final Properties firstArtifact = loadArtifactProperties(firstOutDir.resolve("program.tsj.properties"));
        final Properties secondArtifact = loadArtifactProperties(secondOutDir.resolve("program.tsj.properties"));
        final MediationDecision firstDecision = requireMediationDecision(firstArtifact, "sample.graph:shared-lib");
        final MediationDecision secondDecision = requireMediationDecision(secondArtifact, "sample.graph:shared-lib");
        assertEquals(firstDecision, secondDecision);
        assertEquals("1.0.0", firstDecision.selectedVersion());
        assertEquals("2.0.0", firstDecision.rejectedVersion());
        assertEquals("nearest", firstDecision.rule());
        assertTrue(classpathEntries(firstArtifact).contains(sharedOne.toAbsolutePath().normalize().toString()));
        assertFalse(classpathEntries(firstArtifact).contains(sharedTwo.toAbsolutePath().normalize().toString()));
        assertTrue(classpathEntries(secondArtifact).contains(sharedOne.toAbsolutePath().normalize().toString()));
        assertFalse(classpathEntries(secondArtifact).contains(sharedTwo.toAbsolutePath().normalize().toString()));
    }

    @Test
    void runMediatesSameDepthConflictsUsingRootOrderTiebreak() throws Exception {
        final MavenCoordinate sharedOneCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "1.0.0");
        final MavenCoordinate sharedTwoCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "2.0.0");
        final MavenCoordinate leftCoordinate = new MavenCoordinate("sample.graph", "left-api", "1.0.0");
        final MavenCoordinate rightCoordinate = new MavenCoordinate("sample.graph", "right-api", "1.0.0");

        final Path sharedOne = buildInteropJarWithMavenMetadata(
                "sample.graph.Shared",
                """
                package sample.graph;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v1";
                    }
                }
                """,
                List.of(),
                "shared-lib-1.0.0.jar",
                sharedOneCoordinate,
                List.of()
        );
        final Path sharedTwo = buildInteropJarWithMavenMetadata(
                "sample.graph.Shared",
                """
                package sample.graph;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v2";
                    }
                }
                """,
                List.of(),
                "shared-lib-2.0.0.jar",
                sharedTwoCoordinate,
                List.of()
        );
        final Path leftJar = buildInteropJarWithMavenMetadata(
                "sample.graph.LeftApi",
                """
                package sample.graph;

                public final class LeftApi {
                    private LeftApi() {
                    }

                    public static String describe() {
                        return "left->" + Shared.label();
                    }
                }
                """,
                List.of(sharedOne),
                "left-api-1.0.0.jar",
                leftCoordinate,
                List.of(sharedOneCoordinate)
        );
        final Path rightJar = buildInteropJarWithMavenMetadata(
                "sample.graph.RightApi",
                """
                package sample.graph;

                public final class RightApi {
                    private RightApi() {
                    }

                    public static String describe() {
                        return "right->" + Shared.label();
                    }
                }
                """,
                List.of(sharedTwo),
                "right-api-1.0.0.jar",
                rightCoordinate,
                List.of(sharedTwoCoordinate)
        );

        final Path entryFile = tempDir.resolve("tsj40a-root-order.ts");
        Files.writeString(
                entryFile,
                """
                import { describe as leftDescribe } from "java:sample.graph.LeftApi";
                import { describe as rightDescribe } from "java:sample.graph.RightApi";

                console.log(leftDescribe());
                console.log(rightDescribe());
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj40a-root-order-out");
        final String classpath = String.join(
                File.pathSeparator,
                rightJar.toString(),
                leftJar.toString(),
                sharedOne.toString(),
                sharedTwo.toString()
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
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("left->shared-v2"));
        assertTrue(stdoutText.contains("right->shared-v2"));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertMediationDecisionPresent(artifact, "sample.graph:shared-lib", "2.0.0", "1.0.0", "root-order");
    }

    @Test
    void compileIncludesProvidedScopeDependenciesForInteropResolution() throws Exception {
        final MavenCoordinate apiCoordinate = new MavenCoordinate("sample.scope", "api-lib", "1.0.0");
        final MavenCoordinate providedCoordinate = new MavenCoordinate("sample.scope", "provided-lib", "1.0.0");

        final Path providedJar = buildInteropJarWithMavenMetadata(
                "sample.scope.ProvidedOnly",
                """
                package sample.scope;

                public final class ProvidedOnly {
                    private ProvidedOnly() {
                    }

                    public static String ping() {
                        return "provided";
                    }
                }
                """,
                List.of(),
                "provided-lib-1.0.0.jar",
                providedCoordinate,
                List.of()
        );
        final Path apiJar = buildInteropJarWithScopedMavenMetadata(
                "sample.scope.Api",
                """
                package sample.scope;

                public final class Api {
                    private Api() {
                    }
                }
                """,
                List.of(providedJar),
                "api-lib-1.0.0.jar",
                apiCoordinate,
                List.of(new MavenDependencySpec(providedCoordinate, "provided"))
        );

        final Path entryFile = tempDir.resolve("tsj40b-compile-scope.ts");
        Files.writeString(
                entryFile,
                """
                import { ping } from "java:sample.scope.ProvidedOnly";
                console.log("value=" + ping());
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj40b-compile-scope-out");
        final String classpath = String.join(
                File.pathSeparator,
                apiJar.toString(),
                providedJar.toString()
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("compile", artifact.getProperty("interopClasspath.scope.usage"));
        assertEquals("compile,runtime,provided", artifact.getProperty("interopClasspath.scope.allowed"));
        assertEquals("0", artifact.getProperty("interopClasspath.scope.excluded.count", "0"));
        assertTrue(classpathEntries(artifact).contains(providedJar.toAbsolutePath().normalize().toString()));
    }

    @Test
    void runRejectsInteropTargetsAvailableOnlyViaProvidedScope() throws Exception {
        final MavenCoordinate apiCoordinate = new MavenCoordinate("sample.scope", "api-lib", "1.0.0");
        final MavenCoordinate providedCoordinate = new MavenCoordinate("sample.scope", "provided-lib", "1.0.0");

        final Path providedJar = buildInteropJarWithMavenMetadata(
                "sample.scope.ProvidedOnly",
                """
                package sample.scope;

                public final class ProvidedOnly {
                    private ProvidedOnly() {
                    }

                    public static String ping() {
                        return "provided";
                    }
                }
                """,
                List.of(),
                "provided-lib-1.0.0.jar",
                providedCoordinate,
                List.of()
        );
        final Path apiJar = buildInteropJarWithScopedMavenMetadata(
                "sample.scope.Api",
                """
                package sample.scope;

                public final class Api {
                    private Api() {
                    }
                }
                """,
                List.of(providedJar),
                "api-lib-1.0.0.jar",
                apiCoordinate,
                List.of(new MavenDependencySpec(providedCoordinate, "provided"))
        );

        final Path entryFile = tempDir.resolve("tsj40b-runtime-scope-error.ts");
        Files.writeString(
                entryFile,
                """
                import { ping } from "java:sample.scope.ProvidedOnly";
                console.log("value=" + ping());
                """,
                UTF_8
        );
        final String classpath = String.join(
                File.pathSeparator,
                apiJar.toString(),
                providedJar.toString()
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("tsj40b-runtime-scope-error-out").toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertEquals("", stdout.toString(UTF_8));
        assertTrue(stderrText.contains("\"code\":\"TSJ-CLASSPATH-SCOPE\""));
        assertTrue(stderrText.contains("sample.scope.ProvidedOnly"));
        assertTrue(stderrText.contains("\"scope\":\"provided\""));
        assertTrue(stderrText.contains("\"usage\":\"runtime\""));
    }

    @Test
    void runSupportsAppIsolatedClassloaderModeForInteropDependencies() throws Exception {
        final Path helperJar = buildInteropJar(
                "sample.isolation.Helper",
                """
                package sample.isolation;

                public final class Helper {
                    private Helper() {
                    }

                    public static String ping() {
                        return "isolated";
                    }
                }
                """
        );

        final Path entryFile = tempDir.resolve("tsj40c-app-isolated-success.ts");
        Files.writeString(
                entryFile,
                """
                import { ping } from "java:sample.isolation.Helper";
                console.log("mode=" + ping());
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("tsj40c-app-isolated-success-out").toString(),
                        "--classpath",
                        helperJar.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--classloader-isolation",
                        "app-isolated"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode, stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("mode=isolated"));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runReportsIsolationConflictWhenDependencyShadowsProgramClassInAppIsolatedMode() throws Exception {
        final MavenCoordinate duplicateCoordinate = new MavenCoordinate("sample.isolation", "dup-program", "1.0.0");
        final MavenCoordinate apiCoordinate = new MavenCoordinate("sample.isolation", "api-lib", "1.0.0");
        final String generatedMainClass = "dev.tsj.generated.Tsj40cAppIsolatedConflictProgram";

        final Path duplicateProgramJar = buildInteropJarWithMavenMetadata(
                generatedMainClass,
                """
                package dev.tsj.generated;

                public final class Tsj40cAppIsolatedConflictProgram {
                    private Tsj40cAppIsolatedConflictProgram() {
                    }

                    public static String identity() {
                        return "dependency-program";
                    }
                }
                """,
                List.of(),
                "dup-program-1.0.0.jar",
                duplicateCoordinate,
                List.of()
        );

        final Path apiJar = buildInteropJarWithMavenMetadata(
                "sample.isolation.Api",
                """
                package sample.isolation;

                public final class Api {
                    private Api() {
                    }

                    public static String ping() {
                        return "api";
                    }
                }
                """,
                List.of(duplicateProgramJar),
                "api-lib-1.0.0.jar",
                apiCoordinate,
                List.of(duplicateCoordinate)
        );

        final Path entryFile = tempDir.resolve("tsj40c-app-isolated-conflict.ts");
        Files.writeString(
                entryFile,
                """
                import { ping } from "java:sample.isolation.Api";
                console.log("value=" + ping());
                """,
                UTF_8
        );

        final String classpath = String.join(File.pathSeparator, apiJar.toString(), duplicateProgramJar.toString());
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("tsj40c-app-isolated-conflict-out").toString(),
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
        assertEquals(1, exitCode);
        assertEquals("", stdout.toString(UTF_8));
        assertTrue(stderrText.contains("\"code\":\"TSJ-RUN-009\""), stderrText);
        assertTrue(stderrText.contains(generatedMainClass), stderrText);
        assertTrue(stderrText.contains("app-isolated"), stderrText);
        assertTrue(
                stderrText.contains("\"appOrigin\":\""),
                stderrText
        );
        assertTrue(
                stderrText.contains("\"dependencyOrigin\":\""),
                stderrText
        );
        assertTrue(stderrText.contains("\"conflictClass\":\"" + generatedMainClass + "\""), stderrText);
        assertTrue(
                stderrText.contains("--classloader-isolation shared"),
                stderrText
        );
    }

    @Test
    void runRejectsUnknownClassloaderIsolationMode() throws Exception {
        final Path entryFile = tempDir.resolve("tsj40c-invalid-isolation-mode.ts");
        Files.writeString(entryFile, "console.log('hello');\n", UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("tsj40c-invalid-isolation-mode-out").toString(),
                        "--classloader-isolation",
                        "unknown"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertEquals("", stdout.toString(UTF_8));
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-015\""));
    }

    @Test
    void runPersistsScopeFilteringMetadataForTestScopedDependencies() throws Exception {
        final MavenCoordinate sharedCompileCoordinate = new MavenCoordinate("sample.scope", "shared-lib", "1.0.0");
        final MavenCoordinate sharedTestCoordinate = new MavenCoordinate("sample.scope", "shared-lib", "2.0.0");
        final MavenCoordinate compileApiCoordinate = new MavenCoordinate("sample.scope", "compile-api", "1.0.0");
        final MavenCoordinate testApiCoordinate = new MavenCoordinate("sample.scope", "test-api", "1.0.0");

        final Path sharedCompileJar = buildInteropJarWithMavenMetadata(
                "sample.scope.Shared",
                """
                package sample.scope;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v1";
                    }
                }
                """,
                List.of(),
                "shared-lib-1.0.0.jar",
                sharedCompileCoordinate,
                List.of()
        );
        final Path sharedTestJar = buildInteropJarWithMavenMetadata(
                "sample.scope.Shared",
                """
                package sample.scope;

                public final class Shared {
                    private Shared() {
                    }

                    public static String label() {
                        return "shared-v2";
                    }
                }
                """,
                List.of(),
                "shared-lib-2.0.0.jar",
                sharedTestCoordinate,
                List.of()
        );
        final Path compileApiJar = buildInteropJarWithScopedMavenMetadata(
                "sample.scope.CompileApi",
                """
                package sample.scope;

                public final class CompileApi {
                    private CompileApi() {
                    }

                    public static String describe() {
                        return "compile->" + Shared.label();
                    }
                }
                """,
                List.of(sharedCompileJar),
                "compile-api-1.0.0.jar",
                compileApiCoordinate,
                List.of(new MavenDependencySpec(sharedCompileCoordinate, "compile"))
        );
        final Path testApiJar = buildInteropJarWithScopedMavenMetadata(
                "sample.scope.TestApi",
                """
                package sample.scope;

                public final class TestApi {
                    private TestApi() {
                    }

                    public static String describe() {
                        return "test->" + Shared.label();
                    }
                }
                """,
                List.of(sharedTestJar),
                "test-api-1.0.0.jar",
                testApiCoordinate,
                List.of(new MavenDependencySpec(sharedTestCoordinate, "test"))
        );

        final Path entryFile = tempDir.resolve("tsj40b-runtime-test-scope.ts");
        Files.writeString(
                entryFile,
                """
                import { describe } from "java:sample.scope.CompileApi";
                console.log(describe());
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj40b-runtime-test-scope-out");
        final String classpath = String.join(
                File.pathSeparator,
                compileApiJar.toString(),
                testApiJar.toString(),
                sharedCompileJar.toString(),
                sharedTestJar.toString()
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
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("compile->shared-v1"));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("runtime", artifact.getProperty("interopClasspath.scope.usage"));
        assertEquals("compile,runtime", artifact.getProperty("interopClasspath.scope.allowed"));
        assertEquals("1", artifact.getProperty("interopClasspath.scope.excluded.count"));
        assertEquals("test", artifact.getProperty("interopClasspath.scope.excluded.0.scope"));
        assertEquals(
                sharedTestJar.toAbsolutePath().normalize().toString(),
                artifact.getProperty("interopClasspath.scope.excluded.0.excludedPath")
        );
        assertFalse(classpathEntries(artifact).contains(sharedTestJar.toAbsolutePath().normalize().toString()));
    }

    @Test
    void runSupportsTsj29InteropBindingsForConstructorsMembersFieldsAndVarArgs() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj29-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { $new as makeCounter, $instance$inc as inc, $instance$get$value as getValue, $instance$set$value as setValue, $static$get$GLOBAL as getGlobal, $static$set$GLOBAL as setGlobal, pick, join } from "java:sample.interop.Counter";

                const counter = makeCounter(4);
                console.log("inc=" + inc(counter, 3));
                console.log("value=" + getValue(counter));
                console.log("set=" + setValue(counter, 12));
                console.log("value2=" + getValue(counter));
                console.log("global=" + getGlobal());
                setGlobal(44);
                console.log("global2=" + getGlobal());
                console.log("pickInt=" + pick(2));
                console.log("pickDouble=" + pick(2.5));
                console.log("join=" + join("p", "x", "y"));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.Counter",
                """
                package sample.interop;

                public final class Counter {
                    public static int GLOBAL = 30;
                    public int value;

                    public Counter(final int seed) {
                        this.value = seed;
                    }

                    public int inc(final int delta) {
                        this.value += delta;
                        return this.value;
                    }

                    public static String pick(final int ignored) {
                        return "int";
                    }

                    public static String pick(final double ignored) {
                        return "double";
                    }

                    public static String join(final String prefix, final String... parts) {
                        final StringBuilder builder = new StringBuilder(prefix);
                        for (String part : parts) {
                            builder.append(":").append(part);
                        }
                        return builder.toString();
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj29-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("inc=7"));
        assertTrue(stdoutText.contains("value=7"));
        assertTrue(stdoutText.contains("set=12"));
        assertTrue(stdoutText.contains("value2=12"));
        assertTrue(stdoutText.contains("global=30"));
        assertTrue(stdoutText.contains("global2=44"));
        assertTrue(stdoutText.contains("pickInt=int"));
        assertTrue(stdoutText.contains("pickDouble=double"));
        assertTrue(stdoutText.contains("join=p:x:y"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runSupportsTsj30InteropCallbackAndCompletableFutureAwait() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj30-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { applyOperator, upperAsync } from "java:sample.interop.AsyncInterop";

                console.log("callback=" + applyOperator((value: number) => value + 2, 5));

                async function main() {
                  const upper = await upperAsync("tsj");
                  console.log("upper=" + upper);
                }

                main();
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.AsyncInterop",
                """
                package sample.interop;

                import java.util.concurrent.CompletableFuture;
                import java.util.function.IntUnaryOperator;

                public final class AsyncInterop {
                    private AsyncInterop() {
                    }

                    public static int applyOperator(final IntUnaryOperator operator, final int seed) {
                        return operator.applyAsInt(seed);
                    }

                    public static CompletableFuture<String> upperAsync(final String value) {
                        return CompletableFuture.completedFuture(value.toUpperCase());
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj30-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode);
        assertTrue(stdoutText.contains("callback=7"));
        assertTrue(stdoutText.contains("upper=TSJ"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runSupportsTsj53SamInteropForInterfaceThatRedeclaresObjectMethod() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj53-sam.ts");
        Files.writeString(
                entryFile,
                """
                import { run } from "java:sample.interop.SamRunner";

                const callback = (value: string) => value + "-ok";
                console.log("sam=" + run(callback, "sam"));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.SamRunner",
                """
                package sample.interop;

                public final class SamRunner {
                    private SamRunner() {
                    }

                    @FunctionalInterface
                    public interface MyFn<T, R> {
                        R apply(T value);

                        default String name() {
                            return "fn";
                        }

                        String toString();
                    }

                    public static String run(final MyFn<String, String> callback, final String input) {
                        return callback.apply(input);
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj53-sam-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("sam=sam-ok"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runSupportsTsj41AdvancedInteropConversionAndInvocationParity() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj41-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { classify, nestedSummary, optionalSummary } from "java:sample.ecosystem.AdvancedApi";
                import { parse as parseDuration, $instance$toMillis as durationToMillis } from "java:java.time.Duration";

                const payload = { kind: "demo", nested: { level: 2 }, items: ["a", "b", "c"] };

                console.log("classifyList=" + classify([1, 2]));
                console.log("classifyMap=" + classify(payload));
                console.log("nested=" + nestedSummary(payload));
                console.log("optionalPresent=" + optionalSummary("value"));
                console.log("optionalEmpty=" + optionalSummary(undefined));
                const duration = parseDuration("PT2.5S");
                console.log("millis=" + durationToMillis(duration));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.ecosystem.AdvancedApi",
                """
                package sample.ecosystem;

                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                public final class AdvancedApi {
                    private AdvancedApi() {
                    }

                    public static String classify(final List<Object> values) {
                        return "list";
                    }

                    public static String classify(final Map<String, Object> value) {
                        return "map";
                    }

                    public static String classify(final Object value) {
                        return "object";
                    }

                    public static String nestedSummary(final Map<String, Object> payload) {
                        final Object nestedObject = payload.get("nested");
                        final Object itemsObject = payload.get("items");
                        final String nestedLevel = nestedObject instanceof Map<?, ?> nestedMap
                                ? String.valueOf(nestedMap.get("level"))
                                : "missing";
                        final int itemCount = itemsObject instanceof List<?> list ? list.size() : -1;
                        return "kind=" + payload.get("kind") + ",nested=" + nestedLevel + ",items=" + itemCount;
                    }

                    public static String optionalSummary(final Optional<String> value) {
                        return value.orElse("empty");
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj41-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode);
        assertTrue(stdoutText.contains("classifyList=list"));
        assertTrue(stdoutText.contains("classifyMap=map"));
        assertTrue(stdoutText.contains("nested=kind=demo,nested=2,items=3"));
        assertTrue(stdoutText.contains("optionalPresent=value"));
        assertTrue(stdoutText.contains("optionalEmpty=empty"));
        assertTrue(stdoutText.contains("millis=2500"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runSupportsTsj41aNumericWideningAndPrimitiveWrapperParity() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj41a-numeric.ts");
        Files.writeString(
                entryFile,
                """
                import { widenPrimitive, widenWrapper, primitiveVsWrapper } from "java:sample.numeric.NumericApi";

                console.log("widenPrimitiveInt=" + widenPrimitive(5));
                console.log("widenPrimitiveDouble=" + widenPrimitive(5.5));
                console.log("widenWrapperInt=" + widenWrapper(5));
                console.log("widenWrapperDouble=" + widenWrapper(5.5));
                console.log("primitiveVsWrapper=" + primitiveVsWrapper(5));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.numeric.NumericApi",
                """
                package sample.numeric;

                public final class NumericApi {
                    private NumericApi() {
                    }

                    public static String widenPrimitive(final long value) {
                        return "long";
                    }

                    public static String widenPrimitive(final double value) {
                        return "double";
                    }

                    public static String widenWrapper(final Long value) {
                        return "Long";
                    }

                    public static String widenWrapper(final Double value) {
                        return "Double";
                    }

                    public static String primitiveVsWrapper(final int value) {
                        return "int";
                    }

                    public static String primitiveVsWrapper(final Integer value) {
                        return "Integer";
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj41a-numeric-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode);
        assertTrue(stdoutText.contains("widenPrimitiveInt=long"));
        assertTrue(stdoutText.contains("widenPrimitiveDouble=double"));
        assertTrue(stdoutText.contains("widenWrapperInt=Long"));
        assertTrue(stdoutText.contains("widenWrapperDouble=Double"));
        assertTrue(stdoutText.contains("primitiveVsWrapper=int"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runReportsTsj41aNumericConversionReasonForRejectedNarrowing() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj41a-narrowing-failure.ts");
        Files.writeString(
                entryFile,
                """
                import { acceptByte } from "java:sample.numeric.NumericApi";

                console.log("byte=" + acceptByte(130));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.numeric.NumericApi",
                """
                package sample.numeric;

                public final class NumericApi {
                    private NumericApi() {
                    }

                    public static String acceptByte(final byte value) {
                        return "byte=" + value;
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj41a-narrowing-failure-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-RUN-006\""));
        assertTrue(stderrText.contains("numeric conversion"));
        assertTrue(stderrText.contains("narrowing"));
        assertTrue(stderrText.contains("byte"));
    }

    @Test
    void runSupportsTsj41bGenericTypeAdaptationParity() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj41b-generic.ts");
        Files.writeString(
                entryFile,
                """
                import { sumNestedCounts, optionalIntegerSummary, weightedTotal } from "java:sample.generic.GenericApi";

                const payload = [{ count: "2" }, { count: 3 }];
                const weights = { "2": ["1.5", "2.25"], "3": [1] };

                console.log("sumNested=" + sumNestedCounts(payload));
                console.log("optionalPresent=" + optionalIntegerSummary(["4", "5"]));
                console.log("optionalEmpty=" + optionalIntegerSummary(undefined));
                console.log("weighted=" + weightedTotal(weights));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.generic.GenericApi",
                """
                package sample.generic;

                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                public final class GenericApi {
                    private GenericApi() {
                    }

                    public enum Mode {
                        ALPHA,
                        BETA
                    }

                    public static int sumNestedCounts(final List<Map<String, Integer>> payload) {
                        int total = 0;
                        for (Map<String, Integer> row : payload) {
                            total += row.get("count");
                        }
                        return total;
                    }

                    public static String optionalIntegerSummary(final Optional<List<Integer>> values) {
                        if (values.isEmpty()) {
                            return "empty";
                        }
                        int total = 0;
                        for (Integer value : values.get()) {
                            total += value;
                        }
                        return "sum=" + total;
                    }

                    public static double weightedTotal(final Map<Integer, List<Double>> weighted) {
                        double total = 0.0d;
                        for (Map.Entry<Integer, List<Double>> entry : weighted.entrySet()) {
                            for (Double value : entry.getValue()) {
                                total += entry.getKey() * value;
                            }
                        }
                        return total;
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj41b-generic-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode);
        assertTrue(stdoutText.contains("sumNested=5"));
        assertTrue(stdoutText.contains("optionalPresent=sum=9"));
        assertTrue(stdoutText.contains("optionalEmpty=empty"));
        assertTrue(stdoutText.contains("weighted=10.5"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runReportsTsj41bGenericAdaptationFailureWithTargetTypeContext() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj41b-generic-failure.ts");
        Files.writeString(
                entryFile,
                """
                import { joinModes } from "java:sample.generic.GenericApi";

                console.log("modes=" + joinModes(["ALPHA", "GAMMA"]));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.generic.GenericApi",
                """
                package sample.generic;

                import java.util.List;

                public final class GenericApi {
                    private GenericApi() {
                    }

                    public enum Mode {
                        ALPHA,
                        BETA
                    }

                    public static String joinModes(final List<Mode> modes) {
                        final StringBuilder builder = new StringBuilder();
                        for (int index = 0; index < modes.size(); index++) {
                            if (index > 0) {
                                builder.append(",");
                            }
                            builder.append(modes.get(index).name());
                        }
                        return builder.toString();
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj41b-generic-failure-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-RUN-006\""));
        assertTrue(stderrText.contains("Generic interop conversion failed"));
        assertTrue(stderrText.contains("java.util.List"));
        assertTrue(stderrText.contains("sample.generic.GenericApi$Mode"));
        assertTrue(stderrText.contains("Unknown enum constant"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void runSupportsTsj41cReflectiveDefaultMethodAndBridgeDispatchParity() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj41c-reflective.ts");
        Files.writeString(
                entryFile,
                """
                import { $new as newGreeter, $instance$greet as greet } from "java:sample.reflective.ReflectiveApi$GreeterImpl";
                import { $new as newBox, $instance$bump as bump } from "java:sample.reflective.ReflectiveApi$IntegerBridgeSample";

                const greeter = newGreeter();
                const box = newBox();

                console.log("greet=" + greet(greeter, "tsj"));
                console.log("bump=" + bump(box, 7));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.reflective.ReflectiveApi",
                """
                package sample.reflective;

                public final class ReflectiveApi {
                    private ReflectiveApi() {
                    }

                    public interface Greeter {
                        default String greet(final String name) {
                            return "hello-" + name;
                        }
                    }

                    public static final class GreeterImpl implements Greeter {
                    }

                    public static class NumberBridgeBase<T extends Number> {
                        public T bump(final T value) {
                            return value;
                        }
                    }

                    public static final class IntegerBridgeSample extends NumberBridgeBase<Integer> {
                        @Override
                        public Integer bump(final Integer value) {
                            return value + 1;
                        }
                    }

                    static String hiddenStatic() {
                        return "hidden";
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj41c-reflective-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode);
        assertTrue(stdoutText.contains("greet=hello-tsj"));
        assertTrue(stdoutText.contains("bump=8"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runReportsTsj41cReflectiveDiagnosticForNonPublicMethodAccess() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj41c-reflective-failure.ts");
        Files.writeString(
                entryFile,
                """
                import { hiddenStatic } from "java:sample.reflective.ReflectiveApi";

                console.log(hiddenStatic());
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.reflective.ReflectiveApi",
                """
                package sample.reflective;

                public final class ReflectiveApi {
                    private ReflectiveApi() {
                    }

                    static String hiddenStatic() {
                        return "hidden";
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj41c-reflective-failure-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-RUN-006\""));
        assertTrue(stderrText.contains("TSJ-INTEROP-REFLECTIVE"));
        assertTrue(stderrText.contains("hiddenStatic"));
        assertTrue(stderrText.contains("non-public"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void runSupportsTsj42JpaCompatibilityPackSubsetFlows() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj42-jpa.ts");
        Files.writeString(
                entryFile,
                """
                import { reset, begin, commit, rollback, save, find, findByStatus, distinctStatuses, reference, lifecycleEvents, readStatus, statusesSummary } from "java:sample.jpa.JpaPack";
                import { $instance$get as lazyGet, $instance$isInitialized as lazyInitialized } from "java:sample.jpa.JpaPack$LazyRef";

                class OrderEntity {
                  constructor(id: number, status: string) {
                    this.id = id;
                    this.status = status;
                  }
                }

                class OrderRepository {
                  save(order: OrderEntity) {
                    return save(order);
                  }

                  find(id: number) {
                    return find(id);
                  }

                  findByStatus(status: string) {
                    return findByStatus(status);
                  }

                  distinctStatuses() {
                    return distinctStatuses();
                  }

                  lazy(id: number) {
                    return reference(id);
                  }
                }

                class OrderService {
                  constructor(repository: OrderRepository) {
                    this.repository = repository;
                  }

                  create(order: OrderEntity) {
                    begin();
                    this.repository.save(order);
                    commit();
                  }

                  createAndRollback(order: OrderEntity) {
                    begin();
                    this.repository.save(order);
                    rollback();
                  }
                }

                reset();
                const repository = new OrderRepository();
                const service = new OrderService(repository);

                service.create(new OrderEntity(1, "NEW"));
                service.create(new OrderEntity(2, "PAID"));
                service.createAndRollback(new OrderEntity(3, "NEW"));

                const found = repository.find(1);
                const newOrders = repository.findByStatus("NEW");
                console.log("find1=" + readStatus(found));
                console.log("newCount=" + newOrders.length);
                const statuses = repository.distinctStatuses();
                console.log("statusesLen=" + statuses.length);
                console.log("statuses=" + statusesSummary(statuses));

                const lazyRef = repository.lazy(2);
                console.log("lazyInit0=" + lazyInitialized(lazyRef));
                const lazyEntity = lazyGet(lazyRef);
                console.log("lazyStatus=" + readStatus(lazyEntity));
                console.log("lazyInit1=" + lazyInitialized(lazyRef));
                const events = lifecycleEvents();
                console.log("events=" + events.length);
                """,
                UTF_8
        );

        final Path jarFile = buildInteropJar(
                "sample.jpa.JpaPack",
                """
                package sample.jpa;

                import java.util.ArrayDeque;
                import java.util.ArrayList;
                import java.util.Deque;
                import java.util.LinkedHashMap;
                import java.util.LinkedHashSet;
                import java.util.List;
                import java.util.Map;
                import java.util.Set;

                public final class JpaPack {
                    private static final Map<Long, Map<String, Object>> STORE = new LinkedHashMap<>();
                    private static final Deque<Map<Long, Map<String, Object>>> SNAPSHOTS = new ArrayDeque<>();
                    private static final List<String> EVENTS = new ArrayList<>();

                    private JpaPack() {
                    }

                    public static void reset() {
                        STORE.clear();
                        SNAPSHOTS.clear();
                        EVENTS.clear();
                    }

                    public static void begin() {
                        SNAPSHOTS.push(copyStore());
                        EVENTS.add("tx:begin");
                    }

                    public static void commit() {
                        if (SNAPSHOTS.isEmpty()) {
                            throw new IllegalStateException("No active transaction.");
                        }
                        SNAPSHOTS.pop();
                        EVENTS.add("tx:commit");
                    }

                    public static void rollback() {
                        if (SNAPSHOTS.isEmpty()) {
                            throw new IllegalStateException("No active transaction.");
                        }
                        STORE.clear();
                        STORE.putAll(SNAPSHOTS.pop());
                        EVENTS.add("tx:rollback");
                    }

                    public static Map<String, Object> save(final Map<String, Object> entity) {
                        final long id = Long.parseLong(String.valueOf(entity.get("id")));
                        STORE.put(id, new LinkedHashMap<>(entity));
                        EVENTS.add("persist:" + id);
                        return new LinkedHashMap<>(STORE.get(id));
                    }

                    public static Map<String, Object> find(final long id) {
                        final Map<String, Object> value = STORE.get(id);
                        if (value == null) {
                            return null;
                        }
                        EVENTS.add("load:" + id);
                        return new LinkedHashMap<>(value);
                    }

                    public static List<Map<String, Object>> findByStatus(final String status) {
                        final List<Map<String, Object>> matches = new ArrayList<>();
                        for (Map.Entry<Long, Map<String, Object>> entry : STORE.entrySet()) {
                            final Object storedStatus = entry.getValue().get("status");
                            if (status.equals(String.valueOf(storedStatus))) {
                                matches.add(new LinkedHashMap<>(entry.getValue()));
                            }
                        }
                        return matches;
                    }

                    public static String readStatus(final Map<String, Object> entity) {
                        if (entity == null) {
                            return "null";
                        }
                        return String.valueOf(entity.get("status"));
                    }

                    public static String statusesSummary(final List<Object> statuses) {
                        final StringBuilder builder = new StringBuilder();
                        for (int index = 0; index < statuses.size(); index++) {
                            if (index > 0) {
                                builder.append(",");
                            }
                            builder.append(String.valueOf(statuses.get(index)));
                        }
                        return builder.toString();
                    }

                    public static Set<String> distinctStatuses() {
                        final Set<String> values = new LinkedHashSet<>();
                        for (Map<String, Object> entity : STORE.values()) {
                            values.add(String.valueOf(entity.get("status")));
                        }
                        return values;
                    }

                    public static LazyRef reference(final long id) {
                        return new LazyRef(id);
                    }

                    public static List<String> lifecycleEvents() {
                        return new ArrayList<>(EVENTS);
                    }

                    private static Map<Long, Map<String, Object>> copyStore() {
                        final Map<Long, Map<String, Object>> copy = new LinkedHashMap<>();
                        for (Map.Entry<Long, Map<String, Object>> entry : STORE.entrySet()) {
                            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
                        }
                        return copy;
                    }

                    public static final class LazyRef {
                        private final long id;
                        private boolean initialized;

                        public LazyRef(final long id) {
                            this.id = id;
                        }

                        public Map<String, Object> get() {
                            initialized = true;
                            return JpaPack.find(id);
                        }

                        public boolean isInitialized() {
                            return initialized;
                        }
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj42-jpa-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(
                0,
                exitCode,
                "stdout:\n" + stdoutText + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stdoutText.contains("find1=NEW"));
        assertTrue(stdoutText.contains("newCount=1"));
        assertTrue(stdoutText.contains("statusesLen=2"));
        assertTrue(stdoutText.contains("statuses=NEW,PAID"));
        assertTrue(stdoutText.contains("lazyInit0=false"));
        assertTrue(stdoutText.contains("lazyStatus=PAID"));
        assertTrue(stdoutText.contains("lazyInit1=true"));
        assertTrue(stdoutText.contains("events=11"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runSurfacesTsj42UnsupportedJpaPatternDiagnosticMessage() throws Exception {
        final Path entryFile = tempDir.resolve("run-tsj42-unsupported.ts");
        Files.writeString(
                entryFile,
                """
                import { unsupportedNativeQuery } from "java:sample.jpa.JpaPack";
                unsupportedNativeQuery("select * from orders");
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.jpa.JpaPack",
                """
                package sample.jpa;

                public final class JpaPack {
                    private JpaPack() {
                    }

                    public static String unsupportedNativeQuery(final String sql) {
                        throw new IllegalArgumentException(
                                "TSJ-JPA-UNSUPPORTED: native query path is outside TSJ-42 subset."
                        );
                    }
                }
                """
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-tsj42-unsupported-out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(
                1,
                exitCode,
                "stdout:\n" + stdout.toString(UTF_8) + "\nstderr:\n" + stderr.toString(UTF_8)
        );
        assertTrue(stderr.toString(UTF_8).contains("TSJ-JPA-UNSUPPORTED"));
    }

    @Test
    void compileRejectsMissingJarInputWithStructuredDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("missing-jar.ts");
        Files.writeString(entryFile, "console.log('x');\n", UTF_8);
        final Path outDir = tempDir.resolve("missing-jar-out");
        final Path missingJar = tempDir.resolve("missing-lib.jar");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--jar",
                        missingJar.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(2, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-CLI-011\""));
        assertTrue(stderrText.contains("Classpath entry does not exist"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileWithInteropSpecAutoGeneratesBridgesForDiscoveredJavaImports() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("max=" + max(2, 5));
                """,
                UTF_8
        );
        final Path interopSpec = tempDir.resolve("interop.properties");
        Files.writeString(
                interopSpec,
                """
                allowlist=java.lang.Math#max
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        interopSpec.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangMathBridge.java");
        final Path bridgeMetadata = outDir.resolve("generated-interop/interop-bridges.properties");
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertTrue(Files.exists(bridgeSource));
        assertTrue(Files.exists(bridgeMetadata));
        assertTrue(stdout.toString(UTF_8).contains("\"interopBridgeRegenerated\":\"true\""));
        assertEquals("0", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("1", artifact.getProperty("interopBridges.unresolvedTargetCount"));
        assertTrue(
                artifact.getProperty("interopBridges.unresolvedTarget.0.reason", "").contains("bindingArgs.max")
        );
    }

    @Test
    void compilePersistsSelectedInteropTargetIdentityMetadataForDeterministicTarget() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-selected.ts");
        Files.writeString(
                entryFile,
                """
                import { toHexString } from "java:java.lang.Integer";
                console.log(toHexString(255));
                """,
                UTF_8
        );
        final Path interopSpec = tempDir.resolve("interop-selected.properties");
        Files.writeString(
                interopSpec,
                """
                allowlist=java.lang.Integer#toHexString
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-selected-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        interopSpec.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("1", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("toHexString", artifact.getProperty("interopBridges.selectedTarget.0.binding"));
        assertEquals("java.lang.Integer", artifact.getProperty("interopBridges.selectedTarget.0.owner"));
        assertEquals("toHexString", artifact.getProperty("interopBridges.selectedTarget.0.name"));
        assertEquals("(I)Ljava/lang/String;", artifact.getProperty("interopBridges.selectedTarget.0.descriptor"));
        assertEquals("STATIC_METHOD", artifact.getProperty("interopBridges.selectedTarget.0.invokeKind"));
    }

    @Test
    void compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataWithoutInteropSpec() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-broad-no-spec.ts");
        Files.writeString(
                entryFile,
                """
                import { toHexString } from "java:java.lang.Integer";
                console.log(toHexString(255));
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-no-spec-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangIntegerBridge.java");
        assertTrue(Files.exists(bridgeSource));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("true", artifact.getProperty("interopBridges.enabled"));
        assertEquals("1", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("toHexString", artifact.getProperty("interopBridges.selectedTarget.0.binding"));
        assertEquals("java.lang.Integer", artifact.getProperty("interopBridges.selectedTarget.0.owner"));
        assertEquals("(I)Ljava/lang/String;", artifact.getProperty("interopBridges.selectedTarget.0.descriptor"));
    }

    @Test
    void compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForMultilineNamedJavaImportWithoutInteropSpec()
            throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-broad-no-spec-multiline.ts");
        Files.writeString(
                entryFile,
                """
                import {
                    toHexString
                } from "java:java.lang.Integer";
                console.log(toHexString(255));
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-no-spec-multiline-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangIntegerBridge.java");
        assertTrue(Files.exists(bridgeSource));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("true", artifact.getProperty("interopBridges.enabled"));
        assertEquals("1", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("toHexString", artifact.getProperty("interopBridges.selectedTarget.0.binding"));
        assertEquals("java.lang.Integer", artifact.getProperty("interopBridges.selectedTarget.0.owner"));
        assertEquals("(I)Ljava/lang/String;", artifact.getProperty("interopBridges.selectedTarget.0.descriptor"));
    }

    @Test
    void compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataThroughMultilineNamedRelativeImportWithoutInteropSpec()
            throws Exception {
        final Path dependencyFile = tempDir.resolve("interop-helper.ts");
        Files.writeString(
                dependencyFile,
                """
                import { toHexString } from "java:java.lang.Integer";
                export function formatHex(): string {
                    return toHexString(255);
                }
                """,
                UTF_8
        );
        final Path entryFile = tempDir.resolve("auto-interop-broad-no-spec-relative-multiline.ts");
        Files.writeString(
                entryFile,
                """
                import {
                    formatHex
                } from "./interop-helper";
                console.log(formatHex());
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-no-spec-relative-multiline-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangIntegerBridge.java");
        assertTrue(Files.exists(bridgeSource));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("true", artifact.getProperty("interopBridges.enabled"));
        assertEquals("1", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("toHexString", artifact.getProperty("interopBridges.selectedTarget.0.binding"));
        assertEquals("java.lang.Integer", artifact.getProperty("interopBridges.selectedTarget.0.owner"));
        assertEquals("(I)Ljava/lang/String;", artifact.getProperty("interopBridges.selectedTarget.0.descriptor"));
    }

    @Test
    void compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAttributesWithoutInteropSpec()
            throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-broad-no-spec-import-attributes.ts");
        Files.writeString(
                entryFile,
                """
                import { toHexString } from "java:java.lang.Integer" with { type: "java" };
                console.log(toHexString(255));
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-no-spec-import-attributes-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangIntegerBridge.java");
        assertTrue(Files.exists(bridgeSource));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("true", artifact.getProperty("interopBridges.enabled"));
        assertEquals("1", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("toHexString", artifact.getProperty("interopBridges.selectedTarget.0.binding"));
        assertEquals("java.lang.Integer", artifact.getProperty("interopBridges.selectedTarget.0.owner"));
        assertEquals("(I)Ljava/lang/String;", artifact.getProperty("interopBridges.selectedTarget.0.descriptor"));
    }

    @Test
    void compileWithBroadPolicyAutoGeneratesSelectedInteropMetadataForJavaImportAssertionsWithoutInteropSpec()
            throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-broad-no-spec-import-assertions.ts");
        Files.writeString(
                entryFile,
                """
                import { toHexString } from "java:java.lang.Integer" assert { type: "java" };
                console.log(toHexString(255));
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-no-spec-import-assertions-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangIntegerBridge.java");
        assertTrue(Files.exists(bridgeSource));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("true", artifact.getProperty("interopBridges.enabled"));
        assertEquals("1", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("toHexString", artifact.getProperty("interopBridges.selectedTarget.0.binding"));
        assertEquals("java.lang.Integer", artifact.getProperty("interopBridges.selectedTarget.0.owner"));
        assertEquals("(I)Ljava/lang/String;", artifact.getProperty("interopBridges.selectedTarget.0.descriptor"));
    }

    @Test
    void compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForSamInterfaceInvocation() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-broad-sam-selected.ts");
        Files.writeString(
                entryFile,
                """
                import { run } from "java:sample.interop.SamRunner";
                const callback = (value: string) => value + "-ok";
                console.log(run(callback, "sam"));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.SamRunner",
                """
                package sample.interop;

                public final class SamRunner {
                    private SamRunner() {
                    }

                    @FunctionalInterface
                    public interface MyFn<T, R> {
                        R apply(T value);

                        default String name() {
                            return "fn";
                        }

                        String toString();
                    }

                    public static String run(final MyFn<String, String> callback, final String input) {
                        return callback.apply(input);
                    }
                }
                """
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-sam-selected-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("1", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("run", artifact.getProperty("interopBridges.selectedTarget.0.binding"));
        assertEquals("sample.interop.SamRunner", artifact.getProperty("interopBridges.selectedTarget.0.owner"));
        assertEquals(
                "(Lsample/interop/SamRunner$MyFn;Ljava/lang/String;)Ljava/lang/String;",
                artifact.getProperty("interopBridges.selectedTarget.0.descriptor")
        );
        assertEquals("STATIC_METHOD", artifact.getProperty("interopBridges.selectedTarget.0.invokeKind"));
    }

    @Test
    void compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForConstructorAndInstanceBindings()
            throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-broad-instance-selected.ts");
        Files.writeString(
                entryFile,
                """
                import { $new, $instance$echo } from "java:sample.interop.InstanceApi";
                const value = $new("seed");
                console.log($instance$echo(value, "x"));
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.InstanceApi",
                """
                package sample.interop;

                public final class InstanceApi {
                    private final String seed;

                    public InstanceApi(final String seed) {
                        this.seed = seed;
                    }

                    public String echo(final String suffix) {
                        return seed + "-" + suffix;
                    }
                }
                """
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-instance-selected-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("2", artifact.getProperty("interopBridges.selectedTargetCount"));
        int constructorIndex = -1;
        int instanceIndex = -1;
        for (int index = 0; index < 2; index++) {
            final String binding = artifact.getProperty("interopBridges.selectedTarget." + index + ".binding");
            if ("$new".equals(binding)) {
                constructorIndex = index;
            } else if ("$instance$echo".equals(binding)) {
                instanceIndex = index;
            }
        }
        assertTrue(constructorIndex >= 0);
        assertTrue(instanceIndex >= 0);
        assertEquals(
                "sample.interop.InstanceApi",
                artifact.getProperty("interopBridges.selectedTarget." + constructorIndex + ".owner")
        );
        assertEquals(
                "CONSTRUCTOR",
                artifact.getProperty("interopBridges.selectedTarget." + constructorIndex + ".invokeKind")
        );
        assertEquals(
                "sample.interop.InstanceApi",
                artifact.getProperty("interopBridges.selectedTarget." + instanceIndex + ".owner")
        );
        assertEquals(
                "INSTANCE_METHOD",
                artifact.getProperty("interopBridges.selectedTarget." + instanceIndex + ".invokeKind")
        );
    }

    @Test
    void compileWithBroadPolicyPersistsSelectedInteropTargetMetadataForFieldBindings() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-broad-field-selected.ts");
        Files.writeString(
                entryFile,
                """
                import {
                    $new,
                    $instance$get$value,
                    $instance$set$value,
                    $static$get$count,
                    $static$set$count
                } from "java:sample.interop.FieldApi";
                const value = $new("seed");
                $instance$set$value(value, "next");
                $static$set$count(7);
                console.log($instance$get$value(value) + ":" + $static$get$count());
                """,
                UTF_8
        );
        final Path jarFile = buildInteropJar(
                "sample.interop.FieldApi",
                """
                package sample.interop;

                public final class FieldApi {
                    public static int count = 0;
                    public String value;

                    public FieldApi(final String value) {
                        this.value = value;
                    }
                }
                """
        );
        final Path outDir = tempDir.resolve("auto-interop-broad-field-selected-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("5", artifact.getProperty("interopBridges.selectedTargetCount"));
        assertEquals("0", artifact.getProperty("interopBridges.unresolvedTargetCount"));

        int constructorIndex = -1;
        int instanceGetIndex = -1;
        int instanceSetIndex = -1;
        int staticGetIndex = -1;
        int staticSetIndex = -1;
        for (int index = 0; index < 5; index++) {
            final String binding = artifact.getProperty("interopBridges.selectedTarget." + index + ".binding");
            if ("$new".equals(binding)) {
                constructorIndex = index;
            } else if ("$instance$get$value".equals(binding)) {
                instanceGetIndex = index;
            } else if ("$instance$set$value".equals(binding)) {
                instanceSetIndex = index;
            } else if ("$static$get$count".equals(binding)) {
                staticGetIndex = index;
            } else if ("$static$set$count".equals(binding)) {
                staticSetIndex = index;
            }
        }

        assertTrue(constructorIndex >= 0);
        assertTrue(instanceGetIndex >= 0);
        assertTrue(instanceSetIndex >= 0);
        assertTrue(staticGetIndex >= 0);
        assertTrue(staticSetIndex >= 0);

        assertEquals(
                "sample.interop.FieldApi",
                artifact.getProperty("interopBridges.selectedTarget." + constructorIndex + ".owner")
        );
        assertEquals(
                "(Ljava/lang/String;)V",
                artifact.getProperty("interopBridges.selectedTarget." + constructorIndex + ".descriptor")
        );
        assertEquals(
                "CONSTRUCTOR",
                artifact.getProperty("interopBridges.selectedTarget." + constructorIndex + ".invokeKind")
        );

        assertEquals(
                "sample.interop.FieldApi",
                artifact.getProperty("interopBridges.selectedTarget." + instanceGetIndex + ".owner")
        );
        assertEquals(
                "()Ljava/lang/String;",
                artifact.getProperty("interopBridges.selectedTarget." + instanceGetIndex + ".descriptor")
        );
        assertEquals(
                "INSTANCE_FIELD_GET",
                artifact.getProperty("interopBridges.selectedTarget." + instanceGetIndex + ".invokeKind")
        );

        assertEquals(
                "sample.interop.FieldApi",
                artifact.getProperty("interopBridges.selectedTarget." + instanceSetIndex + ".owner")
        );
        assertEquals(
                "(Ljava/lang/String;)V",
                artifact.getProperty("interopBridges.selectedTarget." + instanceSetIndex + ".descriptor")
        );
        assertEquals(
                "INSTANCE_FIELD_SET",
                artifact.getProperty("interopBridges.selectedTarget." + instanceSetIndex + ".invokeKind")
        );

        assertEquals(
                "sample.interop.FieldApi",
                artifact.getProperty("interopBridges.selectedTarget." + staticGetIndex + ".owner")
        );
        assertEquals(
                "()I",
                artifact.getProperty("interopBridges.selectedTarget." + staticGetIndex + ".descriptor")
        );
        assertEquals(
                "STATIC_FIELD_GET",
                artifact.getProperty("interopBridges.selectedTarget." + staticGetIndex + ".invokeKind")
        );

        assertEquals(
                "sample.interop.FieldApi",
                artifact.getProperty("interopBridges.selectedTarget." + staticSetIndex + ".owner")
        );
        assertEquals(
                "(I)V",
                artifact.getProperty("interopBridges.selectedTarget." + staticSetIndex + ".descriptor")
        );
        assertEquals(
                "STATIC_FIELD_SET",
                artifact.getProperty("interopBridges.selectedTarget." + staticSetIndex + ".invokeKind")
        );
    }

    @Test
    void compileWithInteropSpecRejectsDisallowedDiscoveredTarget() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-disallowed.ts");
        Files.writeString(
                entryFile,
                """
                import { min } from "java:java.lang.Math";
                console.log("min=" + min(2, 5));
                """,
                UTF_8
        );
        final Path interopSpec = tempDir.resolve("interop-disallowed.properties");
        Files.writeString(
                interopSpec,
                """
                allowlist=java.lang.Math#max
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-disallowed-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        interopSpec.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-DISALLOWED\""));
        assertTrue(stderrText.contains("\"featureId\":\"TSJ19-ALLOWLIST\""));
    }

    @Test
    void compileWithInteropSpecSkipsBridgeRegenerationWhenInputsAreUnchanged() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-cache.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log(max(1, 9));
                """,
                UTF_8
        );
        final Path interopSpec = tempDir.resolve("interop-cache.properties");
        Files.writeString(
                interopSpec,
                """
                allowlist=java.lang.Math#max
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-cache-out");

        final ByteArrayOutputStream firstStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream firstStderr = new ByteArrayOutputStream();
        final int firstExitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        interopSpec.toString()
                },
                new PrintStream(firstStdout),
                new PrintStream(firstStderr)
        );
        assertEquals(0, firstExitCode);
        assertEquals("", firstStderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangMathBridge.java");
        assertTrue(Files.exists(bridgeSource));
        final long beforeModifiedTime = Files.getLastModifiedTime(bridgeSource).toMillis();

        Thread.sleep(50L);

        final ByteArrayOutputStream secondStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream secondStderr = new ByteArrayOutputStream();
        final int secondExitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        interopSpec.toString()
                },
                new PrintStream(secondStdout),
                new PrintStream(secondStderr)
        );
        assertEquals(0, secondExitCode);
        assertEquals("", secondStderr.toString(UTF_8));
        final long afterModifiedTime = Files.getLastModifiedTime(bridgeSource).toMillis();

        assertEquals(beforeModifiedTime, afterModifiedTime);
        assertTrue(secondStdout.toString(UTF_8).contains("\"interopBridgeRegenerated\":\"false\""));
    }

    @Test
    void compileWithInteropSpecSupportsTsj29BindingTargets() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-tsj29.ts");
        Files.writeString(
                entryFile,
                """
                import { $new as makeBuilder, $instance$append as append, $instance$toString as asString } from "java:java.lang.StringBuilder";
                const builder = makeBuilder("a");
                append(builder, "b");
                console.log(asString(builder));
                """,
                UTF_8
        );
        final Path interopSpec = tempDir.resolve("interop-tsj29.properties");
        Files.writeString(
                interopSpec,
                """
                allowlist=java.lang.StringBuilder#$new,java.lang.StringBuilder#$instance$append,java.lang.StringBuilder#$instance$toString
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-tsj29-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        interopSpec.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path bridgeSource = outDir.resolve("generated-interop/dev/tsj/generated/interop/JavaLangStringBuilderBridge.java");
        assertTrue(Files.exists(bridgeSource));
        final String bridgeText = Files.readString(bridgeSource, UTF_8);
        assertTrue(bridgeText.contains("invokeBinding(\"java.lang.StringBuilder\", \"$new\""));
    }

    @Test
    void compileWithInteropSpecEmitsBridgeAnnotationsAndMethodParameterMetadata() throws Exception {
        final Path entryFile = tempDir.resolve("auto-interop-annotations.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log(max(1, 9));
                """,
                UTF_8
        );
        final Path interopSpec = tempDir.resolve("interop-annotations.properties");
        Files.writeString(
                interopSpec,
                """
                allowlist=java.lang.Math#max
                classAnnotations=java.lang.Deprecated
                bindingAnnotations.max=java.lang.Deprecated
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("auto-interop-annotations-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        interopSpec.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));

        final Path classesDir = outDir.resolve("classes");
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classesDir.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()
        )) {
            final Class<?> bridgeClass = Class.forName(
                    "dev.tsj.generated.interop.JavaLangMathBridge",
                    true,
                    classLoader
            );
            assertTrue(bridgeClass.isAnnotationPresent(Deprecated.class));
            final Method maxMethod = bridgeClass.getDeclaredMethod("max", Object[].class);
            assertTrue(maxMethod.isAnnotationPresent(Deprecated.class));
            assertEquals("args", maxMethod.getParameters()[0].getName());
        }
    }

    @Test
    void benchCommandGeneratesBenchmarkReportArtifact() throws Exception {
        final Path reportPath = tempDir.resolve("benchmarks/tsj-benchmark-baseline.json");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "bench",
                        reportPath.toString(),
                        "--smoke",
                        "--warmup",
                        "0",
                        "--iterations",
                        "1"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(reportPath));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-BENCH-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void benchCommandRequiresOutputPath() {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"bench"},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-009\""));
    }

    @Test
    void benchCommandRejectsUnknownOption() {
        final Path reportPath = tempDir.resolve("benchmarks/report.json");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"bench", reportPath.toString(), "--bogus"},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-010\""));
    }

    @Test
    void benchCommandRejectsInvalidWarmupValue() {
        final Path reportPath = tempDir.resolve("benchmarks/report.json");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"bench", reportPath.toString(), "--warmup", "-1"},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-010\""));
        assertTrue(stderr.toString(UTF_8).contains("--warmup"));
    }

    @Test
    void benchCommandSupportsNoOptimizeToggle() throws Exception {
        final Path reportPath = tempDir.resolve("benchmarks/no-opt.json");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "bench",
                        reportPath.toString(),
                        "--smoke",
                        "--warmup",
                        "0",
                        "--iterations",
                        "1",
                        "--no-optimize"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String reportJson = Files.readString(reportPath, UTF_8);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(reportPath));
        assertTrue(reportJson.contains("\"constantFoldingEnabled\":false"));
        assertTrue(reportJson.contains("\"deadCodeEliminationEnabled\":false"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-BENCH-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void compileDisablesOptimizationsWithNoOptimizeFlag() throws Exception {
        final Path entryFile = tempDir.resolve("opt-toggle.ts");
        Files.writeString(
                entryFile,
                """
                const value = 1 + 2 * 3;
                console.log("value=" + value);
                """,
                UTF_8
        );
        final Path optimizedOut = tempDir.resolve("opt-enabled-out");
        final Path baselineOut = tempDir.resolve("opt-disabled-out");

        final ByteArrayOutputStream optimizedStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream optimizedStderr = new ByteArrayOutputStream();
        final int optimizedExitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", optimizedOut.toString()},
                new PrintStream(optimizedStdout),
                new PrintStream(optimizedStderr)
        );

        final ByteArrayOutputStream baselineStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream baselineStderr = new ByteArrayOutputStream();
        final int baselineExitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", baselineOut.toString(), "--no-optimize"},
                new PrintStream(baselineStdout),
                new PrintStream(baselineStderr)
        );

        assertEquals(0, optimizedExitCode);
        assertEquals(0, baselineExitCode);
        assertEquals("", optimizedStderr.toString(UTF_8));
        assertEquals("", baselineStderr.toString(UTF_8));

        final String optimizedSource = readGeneratedJavaSource(optimizedOut);
        final String baselineSource = readGeneratedJavaSource(baselineOut);
        assertFalse(optimizedSource.contains("TsjRuntime.multiply("));
        assertTrue(baselineSource.contains("TsjRuntime.multiply("));
    }

    @Test
    void compileCanReEnableOptimizationsAfterNoOptimizeFlag() throws Exception {
        final Path entryFile = tempDir.resolve("opt-order.ts");
        Files.writeString(
                entryFile,
                """
                const value = 2 + 3 * 4;
                console.log("value=" + value);
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("opt-order-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--no-optimize",
                        "--optimize"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final String generatedSource = readGeneratedJavaSource(outDir);
        assertFalse(generatedSource.contains("TsjRuntime.multiply("));
    }

    @Test
    void runCompilesAndExecutesGeneratedArtifact() throws Exception {
        final Path entryFile = tempDir.resolve("entry.ts");
        Files.writeString(entryFile, "console.log('hello');\n", UTF_8);
        final Path outDir = tempDir.resolve("out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outDir.resolve("program.tsj.properties")));
        assertTrue(stdout.toString(UTF_8).contains("hello"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runAcceptsNoOptimizeFlagAndPreservesProgramBehavior() throws Exception {
        final Path entryFile = tempDir.resolve("run-no-opt.ts");
        Files.writeString(
                entryFile,
                """
                let total = 1 + 2 * 3;
                while (false) {
                  total = total + 100;
                }
                console.log("total=" + total);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("run-no-opt-out").toString(),
                        "--no-optimize"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("total=7"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesControlFlowProgram() throws Exception {
        final Path entryFile = tempDir.resolve("control.ts");
        Files.writeString(
                entryFile,
                """
                let i = 1;
                let acc = 0;
                while (i <= 3) {
                  acc = acc + i;
                  i = i + 1;
                }
                if (acc === 6) {
                  console.log("sum=" + acc);
                } else {
                  console.log("bad");
                }
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("cf-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sum=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesClosureProgram() throws Exception {
        final Path entryFile = tempDir.resolve("closure.ts");
        Files.writeString(
                entryFile,
                """
                function makeAdder(base: number) {
                  function add(step: number) {
                    return base + step;
                  }
                  return add;
                }
                const plus2 = makeAdder(2);
                console.log("closure=" + plus2(5));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("closure-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("closure=7"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesClassAndObjectProgram() throws Exception {
        final Path entryFile = tempDir.resolve("class-object.ts");
        Files.writeString(
                entryFile,
                """
                class User {
                  name: string;
                  constructor(name: string) {
                    this.name = name;
                  }
                  tag() {
                    return "@" + this.name;
                  }
                }

                const u = new User("tsj");
                const payload = { label: "ok", count: 2 };
                payload.count = payload.count + 1;
                console.log("model=" + u.tag() + ":" + payload.count);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("class-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("model=@tsj:3"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesObjectMethodWithDynamicThisBinding() throws Exception {
        final Path entryFile = tempDir.resolve("object-method-this.ts");
        Files.writeString(
                entryFile,
                """
                const counter = {
                  value: 1,
                  inc() {
                    this.value = this.value + 1;
                    return this.value;
                  }
                };

                console.log("this=" + counter.inc() + ":" + counter.inc());
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("object-method-this-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("this=2:3"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesObjectFunctionExpressionWithDynamicThisBinding() throws Exception {
        final Path entryFile = tempDir.resolve("object-function-this.ts");
        Files.writeString(
                entryFile,
                """
                const counter = {
                  value: 10,
                  add: function(step: number) {
                    this.value = this.value + step;
                    return this.value;
                  }
                };

                console.log("fn-this=" + counter.add(5));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("object-function-this-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("fn-this=15"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesArrowWithLexicalThisInsideObjectMethod() throws Exception {
        final Path entryFile = tempDir.resolve("arrow-lexical-this.ts");
        Files.writeString(
                entryFile,
                """
                const counter = {
                  value: 3,
                  makeAdder() {
                    const apply = (step: number) => {
                      this.value = this.value + step;
                      return this.value;
                    };
                    return apply;
                  }
                };

                const apply = counter.makeAdder();
                console.log("arrow-this=" + apply(4));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("arrow-lexical-this-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("arrow-this=7"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesInheritanceProgram() throws Exception {
        final Path entryFile = tempDir.resolve("inheritance.ts");
        Files.writeString(
                entryFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                  read() {
                    return this.value;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed + 2);
                  }
                  doubled() {
                    return this.value * 2;
                  }
                }

                const d = new Derived(4);
                console.log("inherit=" + d.read() + ":" + d.doubled());
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("inherit-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("inherit=6:12"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesCoercionProgram() throws Exception {
        final Path entryFile = tempDir.resolve("coercion.ts");
        Files.writeString(
                entryFile,
                """
                const undef = undefined;
                console.log("coerce=" + (1 == "1") + ":" + (1 === "1"));
                console.log("nullish=" + (undef == null) + ":" + (undef === null));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("coercion-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("coerce=true:false"));
        assertTrue(stdout.toString(UTF_8).contains("nullish=true:false"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesObjectToPrimitiveCoercionProgram() throws Exception {
        final Path entryFile = tempDir.resolve("coercion-object.ts");
        Files.writeString(
                entryFile,
                """
                const valueOfObject = {
                  valueOf() {
                    return 7;
                  }
                };
                console.log("eq1=" + (valueOfObject == 7));

                const toStringObject = {
                  valueOf() {
                    return {};
                  },
                  toString() {
                    return "8";
                  }
                };
                console.log("eq2=" + (toStringObject == 8));

                const boolObject = {
                  valueOf() {
                    return 1;
                  }
                };
                console.log("eq3=" + (boolObject == true));

                const plain = {};
                console.log("eq4=" + (plain == "[object Object]"));
                console.log("eq5=" + (plain == null));

                class Box {
                  value: number;
                  constructor(value: number) {
                    this.value = value;
                  }
                  valueOf() {
                    return this.value;
                  }
                }
                const box = new Box(9);
                console.log("eq6=" + (box == 9));

                const nonCallableValueOf = {
                  valueOf: 3,
                  toString() {
                    return "11";
                  }
                };
                console.log("eq7=" + (nonCallableValueOf == 11));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("coercion-object-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        final String rendered = stdout.toString(UTF_8);
        assertTrue(rendered.contains("eq1=true"));
        assertTrue(rendered.contains("eq2=true"));
        assertTrue(rendered.contains("eq3=true"));
        assertTrue(rendered.contains("eq4=true"));
        assertTrue(rendered.contains("eq5=false"));
        assertTrue(rendered.contains("eq6=true"));
        assertTrue(rendered.contains("eq7=true"));
        assertTrue(rendered.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesDeleteAndPrototypeMutationProgram() throws Exception {
        final Path entryFile = tempDir.resolve("delete-prototype.ts");
        Files.writeString(
                entryFile,
                """
                const proto = { shared: "base" };
                const payload = { own: "x", shared: "local" };
                payload.__proto__ = proto;
                console.log("del1=" + delete payload.own);
                console.log("own=" + payload.own);
                console.log("del2=" + delete payload.shared);
                console.log("shared=" + payload.shared);
                console.log("del3=" + delete payload.missing);

                const target = {};
                const returned = Object.setPrototypeOf(target, proto);
                console.log("ret=" + (returned === target));
                console.log("targetShared=" + target.shared);
                const cleared = Object.setPrototypeOf(target, null);
                console.log("cleared=" + (cleared === target));
                console.log("afterClear=" + target.shared);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("delete-prototype-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        final String rendered = stdout.toString(UTF_8);
        assertTrue(rendered.contains("del1=true"));
        assertTrue(rendered.contains("own=undefined"));
        assertTrue(rendered.contains("del2=true"));
        assertTrue(rendered.contains("shared=base"));
        assertTrue(rendered.contains("del3=true"));
        assertTrue(rendered.contains("ret=true"));
        assertTrue(rendered.contains("targetShared=base"));
        assertTrue(rendered.contains("cleared=true"));
        assertTrue(rendered.contains("afterClear=undefined"));
        assertTrue(rendered.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runReportsUndefinedForMissingObjectProperty() throws Exception {
        final Path entryFile = tempDir.resolve("missing-property.ts");
        Files.writeString(
                entryFile,
                """
                const payload = { label: "ok" };
                console.log("missing=" + payload.count);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("missing-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("missing=undefined"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesPromiseResolveThenProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise.ts");
        Files.writeString(
                entryFile,
                """
                function step(v: number) {
                  console.log("step=" + v);
                  return v + 1;
                }
                function done(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                Promise.resolve(1).then(step).then(done);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("promise-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("step=1"));
        assertTrue(stdout.toString(UTF_8).contains("done=2"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesThenableAssimilationProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-thenable.ts");
        Files.writeString(
                entryFile,
                """
                const thenable = {
                  then(resolve: any, reject: any) {
                    resolve(41);
                    reject("bad");
                    resolve(99);
                  }
                };

                Promise.resolve(thenable)
                  .then((value: number) => {
                    console.log("value=" + value);
                    return value + 1;
                  })
                  .then((value: number) => {
                    console.log("next=" + value);
                    return value;
                  });
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("promise-thenable-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("value=41"));
        assertTrue(stdout.toString(UTF_8).contains("next=42"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesThenableRejectRejectionProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-thenable-reject.ts");
        Files.writeString(
                entryFile,
                """
                const badThenable = {
                  then(resolve: any, reject: any) {
                    reject("boom");
                    resolve(99);
                  }
                };

                Promise.resolve(badThenable).then(
                  undefined,
                  (reason: string) => {
                    console.log("error=" + reason);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("promise-thenable-reject-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("error=boom"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesPromiseCatchFinallyProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-catch-finally.ts");
        Files.writeString(
                entryFile,
                """
                Promise.reject("boom")
                  .catch((reason: string) => {
                    console.log("catch=" + reason);
                    return 7;
                  })
                  .finally(() => {
                    console.log("finally");
                    return 999;
                  })
                  .then((value: number) => {
                    console.log("value=" + value);
                    return value;
                  });
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("promise-catch-finally-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("catch=boom"));
        assertTrue(stdout.toString(UTF_8).contains("finally"));
        assertTrue(stdout.toString(UTF_8).contains("value=7"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesPromiseFinallyRejectionOverrideProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-finally-reject.ts");
        Files.writeString(
                entryFile,
                """
                Promise.resolve(1)
                  .finally(() => Promise.reject("fin"))
                  .then(
                    (value: number) => {
                      console.log("value=" + value);
                      return value;
                    },
                    (reason: string) => {
                      console.log("error=" + reason);
                      return reason;
                    }
                  );
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("promise-finally-reject-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("error=fin"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runEmitsUnhandledPromiseRejectionToStderr() throws Exception {
        final Path entryFile = tempDir.resolve("promise-unhandled-reject.ts");
        Files.writeString(
                entryFile,
                """
                Promise.reject("boom");
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("promise-unhandled-reject-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertTrue(stderr.toString(UTF_8).contains("TSJ-UNHANDLED-REJECTION: boom"));
    }

    @Test
    void runExecutesPromiseAllAndRaceProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-all-race.ts");
        Files.writeString(
                entryFile,
                """
                Promise.all([Promise.resolve(1), Promise.resolve(2), 3]).then((values: any) => {
                  console.log("all=" + values.length);
                  return values;
                });

                Promise.race([Promise.resolve("win"), Promise.reject("lose")]).then(
                  (value: string) => {
                    console.log("race=" + value);
                    return value;
                  },
                  (reason: string) => {
                    console.log("race-err=" + reason);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("promise-all-race-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("all=3"));
        assertTrue(stdout.toString(UTF_8).contains("race=win"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesPromiseAllSettledAndAnyProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-allsettled-any.ts");
        Files.writeString(
                entryFile,
                """
                Promise.allSettled([Promise.resolve(1), Promise.reject("x")]).then((entries: any) => {
                  console.log("settled=" + entries.length);
                  return entries;
                });

                Promise.any([Promise.reject("a"), Promise.resolve("ok")]).then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("any-err=" + reason.name);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("promise-allsettled-any-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("settled=2"));
        assertTrue(stdout.toString(UTF_8).contains("any=ok"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesPromiseAnyAggregateErrorProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-any-reject.ts");
        Files.writeString(
                entryFile,
                """
                Promise.any([Promise.reject("a"), Promise.reject("b")]).then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("anyErr=" + reason.name + ":" + reason.errors.length);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("promise-any-reject-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("anyErr=AggregateError:2"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesPromiseCombinatorsWithStringIterableInputProgram() throws Exception {
        final Path entryFile = tempDir.resolve("promise-combinators-string-iterable.ts");
        Files.writeString(
                entryFile,
                """
                Promise.all("ab").then((values: any) => {
                  console.log("all=" + values.length);
                  return values;
                });

                Promise.race("ab").then(
                  (value: string) => {
                    console.log("race=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("race-err=" + reason);
                    return reason;
                  }
                );

                Promise.allSettled("ab").then((entries: any) => {
                  console.log("settled=" + entries.length);
                  return entries;
                });

                Promise.any("ab").then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("any-err=" + reason.name);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("promise-combinators-string-iterable-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("all=2"));
        assertTrue(stdout.toString(UTF_8).contains("race=a"));
        assertTrue(stdout.toString(UTF_8).contains("settled=2"));
        assertTrue(stdout.toString(UTF_8).contains("any=a"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncAwaitProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async.ts");
        Files.writeString(
                entryFile,
                """
                async function compute(seed: number) {
                  console.log("start=" + seed);
                  const next = await Promise.resolve(seed + 1);
                  console.log("after=" + next);
                  return next + 1;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                compute(4).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("start=4"));
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("after=5"));
        assertTrue(stdout.toString(UTF_8).contains("done=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncRejectionProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-reject.ts");
        Files.writeString(
                entryFile,
                """
                async function failLater() {
                  await Promise.resolve(1);
                  throw "boom";
                }

                function onError(reason: string) {
                  console.log("error=" + reason);
                  return reason;
                }

                failLater().then(undefined, onError);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-reject-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("error=boom"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncIfBranchProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-if.ts");
        Files.writeString(
                entryFile,
                """
                async function pick(flag: number) {
                  let value = 0;
                  if (flag === 1) {
                    value = await Promise.resolve(10);
                    console.log("then=" + value);
                  } else {
                    value = await Promise.resolve(20);
                    console.log("else=" + value);
                  }
                  console.log("after=" + value);
                  return value;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                pick(1).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-if-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("then=10"));
        assertTrue(stdout.toString(UTF_8).contains("after=10"));
        assertTrue(stdout.toString(UTF_8).contains("done=10"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncWhileProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-while.ts");
        Files.writeString(
                entryFile,
                """
                async function accumulate(limit: number) {
                  let i = 0;
                  let sum = 0;
                  while (i < limit) {
                    const step = await Promise.resolve(i + 1);
                    sum = sum + step;
                    i = i + 1;
                  }
                  console.log("sum=" + sum);
                  return sum;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                accumulate(3).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-while-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("sum=6"));
        assertTrue(stdout.toString(UTF_8).contains("done=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncWhileConditionAwaitProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-while-condition-await.ts");
        Files.writeString(
                entryFile,
                """
                async function run() {
                  let i = 0;
                  while (await Promise.resolve(i < 1)) {
                    i = i + 1;
                  }
                  return i;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("async-while-condition-await-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("done=1"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncWhileBreakContinueProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-break-continue.ts");
        Files.writeString(
                entryFile,
                """
                async function flow(limit: number) {
                  let i = 0;
                  let sum = 0;
                  while (i < limit) {
                    i = i + 1;
                    if (i === 2) {
                      continue;
                    }
                    if (i === 4) {
                      break;
                    }
                    const step = await Promise.resolve(i);
                    sum = sum + step;
                  }
                  console.log("sum=" + sum);
                  return sum;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                flow(6).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("async-break-continue-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("sum=4"));
        assertTrue(stdout.toString(UTF_8).contains("done=4"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncTryCatchFinallyProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-try-catch-finally.ts");
        Files.writeString(
                entryFile,
                """
                async function run() {
                  try {
                    const value = await Promise.resolve(0);
                    if (value === 0) {
                      throw "boom";
                    }
                    return "ok";
                  } catch (err: string) {
                    const caught = await Promise.resolve(err + "-handled");
                    console.log("catch=" + caught);
                    return "catch-" + caught;
                  } finally {
                    const marker = await Promise.resolve("fin");
                    console.log("finally=" + marker);
                  }
                }

                function onDone(value: string) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("async-try-catch-finally-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("catch=boom-handled"));
        assertTrue(stdout.toString(UTF_8).contains("finally=fin"));
        assertTrue(stdout.toString(UTF_8).contains("done=catch-boom-handled"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncTryFinallyReturnOverrideProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-try-finally-override.ts");
        Files.writeString(
                entryFile,
                """
                async function run() {
                  try {
                    return await Promise.resolve("try");
                  } finally {
                    const marker = await Promise.resolve("fin");
                    return "override-" + marker;
                  }
                }

                function onDone(value: string) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("async-try-finally-override-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("done=override-fin"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncArrowProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-arrow.ts");
        Files.writeString(
                entryFile,
                """
                const inc = async (value: number) => (await Promise.resolve(value)) + 1;

                function onDone(result: number) {
                  console.log("done=" + result);
                  return result;
                }

                inc(5).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-arrow-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("done=6"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesAsyncObjectMethodProgram() throws Exception {
        final Path entryFile = tempDir.resolve("async-object-method.ts");
        Files.writeString(
                entryFile,
                """
                const ops = {
                  async compute(seed: number) {
                    const value = await Promise.resolve(seed + 2);
                    return value * 3;
                  }
                };

                function onDone(result: number) {
                  console.log("done=" + result);
                  return result;
                }

                ops.compute(2).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("async-object-method-out").toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("done=12"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runRejectsAsyncClassGeneratorMethodWithTargetedDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("async-class-generator.ts");
        Files.writeString(
                entryFile,
                """
                class Worker {
                  async *build() {
                    return 1;
                  }
                }
                console.log(new Worker());
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-class-generator-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        final String stderrText = stderr.toString(UTF_8);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("Async generator methods are unsupported"));
        assertTrue(stderrText.contains("TSJ-13b"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void runRejectsAsyncObjectGetterMethodVariantWithTargetedDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("async-object-getter.ts");
        Files.writeString(
                entryFile,
                """
                const ops = {
                  async get value() {
                    return 1;
                  }
                };
                console.log(ops);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-object-getter-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        final String stderrText = stderr.toString(UTF_8);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("Async get methods are unsupported"));
        assertTrue(stderrText.contains("TSJ-13b"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void runRejectsAsyncObjectSetterMethodVariantWithTargetedDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("async-object-setter.ts");
        Files.writeString(
                entryFile,
                """
                const ops = {
                  async set value(next: number) {
                    console.log(next);
                  }
                };
                console.log(ops);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("async-object-setter-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        final String stderrText = stderr.toString(UTF_8);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("Async set methods are unsupported"));
        assertTrue(stderrText.contains("TSJ-13b"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void runExecutesProgramWithRelativeNamedImports() throws Exception {
        final Path helperFile = tempDir.resolve("helper.ts");
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                helperFile,
                """
                console.log("helper-init");
                export function triple(n: number) {
                  return n * 3;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entryFile,
                """
                import { triple } from "./helper.ts";
                console.log("import=" + triple(4));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("import-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("helper-init"));
        assertTrue(stdout.toString(UTF_8).contains("import=12"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesProgramWithTopLevelAwaitInEntry() throws Exception {
        final Path entryFile = tempDir.resolve("tla-main.ts");
        Files.writeString(
                entryFile,
                """
                console.log("before");
                const value = await Promise.resolve(1);
                console.log("after=" + value);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("tla-entry-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("before"));
        assertTrue(stdout.toString(UTF_8).contains("after=1"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesProgramWithTopLevelAwaitAcrossModules() throws Exception {
        final Path depFile = tempDir.resolve("dep.ts");
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                depFile,
                """
                export let status = "init";
                status = await Promise.resolve("ready");
                console.log("dep=" + status);
                """,
                UTF_8
        );
        Files.writeString(
                entryFile,
                """
                import { status } from "./dep.ts";
                console.log("main=" + status);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("tla-modules-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("dep=ready"));
        assertTrue(stdout.toString(UTF_8).contains("main=ready"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runExecutesProgramWithTopLevelAwaitAcrossTransitiveModules() throws Exception {
        final Path moduleA = tempDir.resolve("a.ts");
        final Path moduleB = tempDir.resolve("b.ts");
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                moduleA,
                """
                export let value = 0;
                value = await Promise.resolve(5);
                console.log("a=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                moduleB,
                """
                import { value } from "./a.ts";
                export function read() {
                  return value;
                }
                console.log("b=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                entryFile,
                """
                import { read } from "./b.ts";
                console.log("main=" + read());
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("tla-transitive-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("a=5"));
        assertTrue(stdout.toString(UTF_8).contains("b=5"));
        assertTrue(stdout.toString(UTF_8).contains("main=5"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runRejectsTopLevelAwaitInWhileConditionForNow() throws Exception {
        final Path entryFile = tempDir.resolve("tla-while.ts");
        Files.writeString(
                entryFile,
                """
                let i = 0;
                while (await Promise.resolve(i < 1)) {
                  i = i + 1;
                }
                console.log(i);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("tla-while-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderr.toString(UTF_8).contains("while condition"));
    }

    @Test
    void runDoesNotEmitTsStackTraceWithoutFlag() throws Exception {
        final Path entryFile = tempDir.resolve("runtime-fail.ts");
        Files.writeString(
                entryFile,
                """
                function fail(value: number) {
                  if (value === 1) {
                    throw "boom";
                  }
                  return value;
                }
                fail(1);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("runtime-fail-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-RUN-006\""));
        assertTrue(!stderr.toString(UTF_8).contains("TSJ stack trace (TypeScript):"));
    }

    @Test
    void runEmitsTsStackTraceWithFlag() throws Exception {
        final Path entryFile = tempDir.resolve("runtime-fail-trace.ts");
        Files.writeString(
                entryFile,
                """
                function fail(value: number) {
                  if (value === 1) {
                    throw "boom";
                  }
                  return value;
                }
                fail(1);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("runtime-fail-trace-out").toString(),
                        "--ts-stacktrace"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("TSJ stack trace (TypeScript):"));
        assertTrue(stderr.toString(UTF_8).contains("Cause[0]:"));
        assertTrue(stderr.toString(UTF_8).contains("TsjThrownException"));
        assertTrue(stderr.toString(UTF_8).contains(entryFile.toAbsolutePath().normalize() + ":"));
        assertTrue(stderr.toString(UTF_8).contains("[method="));
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-RUN-006\""));
    }

    @Test
    void runTsStackTraceFiltersDuplicateMethodFramesPerCause() throws Exception {
        final Path entryFile = tempDir.resolve("runtime-fail-recursive.ts");
        Files.writeString(
                entryFile,
                """
                function explode(n: number) {
                  if (n === 0) {
                    throw "boom";
                  }
                  return explode(n - 1);
                }
                explode(2);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("runtime-fail-recursive-out").toString(),
                        "--ts-stacktrace"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        final String stderrText = stderr.toString(UTF_8);
        assertTrue(stderrText.contains("TSJ stack trace (TypeScript):"));
        final List<String> frameLines = stderrText.lines()
                .filter(line -> line.startsWith("  at "))
                .toList();
        assertTrue(!frameLines.isEmpty());
        assertEquals(frameLines.size(), frameLines.stream().distinct().count());
    }

    @Test
    void runTsStackTraceMarksAsyncContinuationFrames() throws Exception {
        final Path entryFile = tempDir.resolve("runtime-async-fail-trace.ts");
        Files.writeString(
                entryFile,
                """
                const notCallable: any = 1;
                async function failAfterAwait() {
                  await Promise.resolve(1);
                  return notCallable();
                }
                await failAfterAwait();
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("runtime-async-fail-trace-out").toString(),
                        "--ts-stacktrace"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        final String stderrText = stderr.toString(UTF_8);
        assertTrue(stderrText.contains("TSJ-UNHANDLED-REJECTION:"));
        assertTrue(stderrText.contains("TSJ stack trace (TypeScript):"));
        assertTrue(stderrText.contains(entryFile.toAbsolutePath().normalize() + ":"));
        assertTrue(stderrText.contains("[method="));
        assertTrue(stderrText.contains("[async-continuation]"));
    }

    @Test
    void runTsStackTraceIncludesUnhandledRejectionThrowableFrames() throws Exception {
        final Path entryFile = tempDir.resolve("promise-unhandled-reject-trace.ts");
        Files.writeString(
                entryFile,
                """
                const notCallable: any = 1;
                Promise.resolve("ok").then(() => {
                  return notCallable();
                });
                console.log("sync");
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("promise-unhandled-reject-trace-out").toString(),
                        "--ts-stacktrace"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("sync"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        final String stderrText = stderr.toString(UTF_8);
        assertTrue(stderrText.contains("TSJ-UNHANDLED-REJECTION:"));
        assertTrue(stderrText.contains("TSJ stack trace (TypeScript):"));
        assertTrue(stderrText.contains("Cause[0]: java.lang.IllegalArgumentException"));
        assertTrue(stderrText.contains(entryFile.toAbsolutePath().normalize() + ":"));
    }

    @Test
    void runSupportsImportAliasInTsj22() throws Exception {
        final Path helperFile = tempDir.resolve("helper.ts");
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(helperFile, "export const value = 2;\n", UTF_8);
        Files.writeString(
                entryFile,
                """
                import { value as v } from "./helper.ts";
                console.log(v);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("alias-import-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("2"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runRejectsDefaultImportWithFeatureDiagnosticMetadata() throws Exception {
        final Path helperFile = tempDir.resolve("helper-default.ts");
        final Path entryFile = tempDir.resolve("main-default.ts");
        Files.writeString(helperFile, "export const value = 2;\n", UTF_8);
        Files.writeString(
                entryFile,
                """
                import value from "./helper-default.ts";
                console.log(value);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("default-import-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("\"featureId\":\"TSJ22-IMPORT-DEFAULT\""));
        assertTrue(stderrText.contains("Use named imports"));
    }

    @Test
    void runRejectsNamespaceImportWithFeatureDiagnosticMetadata() throws Exception {
        final Path helperFile = tempDir.resolve("helper-namespace.ts");
        final Path entryFile = tempDir.resolve("main-namespace.ts");
        Files.writeString(helperFile, "export const value = 2;\n", UTF_8);
        Files.writeString(
                entryFile,
                """
                import * as ns from "./helper-namespace.ts";
                console.log(ns.value);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("namespace-import-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("\"featureId\":\"TSJ22-IMPORT-NAMESPACE\""));
        assertTrue(stderrText.contains("Use named imports"));
    }

    @Test
    void runSupportsJavaInteropNamedImportsInTsj26() throws Exception {
        final Path entryFile = tempDir.resolve("main-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { max, min as minimum } from "java:java.lang.Math";
                console.log("interop:max=" + max(4, 9));
                console.log("interop:min=" + minimum(4, 9));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("interop-out").toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode);
        assertTrue(stdoutText.contains("interop:max=9"));
        assertTrue(stdoutText.contains("interop:min=4"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runEnforcesStrictInteropPolicyByDefault() throws Exception {
        final Path entryFile = tempDir.resolve("main-interop-strict.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log(max(1, 2));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("interop-strict-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-POLICY\""));
        assertTrue(stderrText.contains("--interop-spec"));
    }

    @Test
    void runRequiresBroadInteropRiskAcknowledgementWhenInteropBindingsExist() throws Exception {
        final Path entryFile = tempDir.resolve("main-interop-broad-risk.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log(max(1, 2));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("interop-broad-risk-out").toString(),
                        "--interop-policy",
                        "broad"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-RISK\""));
        assertTrue(stderrText.contains("--ack-interop-risk"));
    }

    @Test
    void runAllowsBroadInteropWhenRiskAcknowledgementIsProvided() throws Exception {
        final Path entryFile = tempDir.resolve("main-interop-broad-risk-ack.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("max=" + max(2, 8));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("interop-broad-risk-ack-out").toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("max=8"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runUsesProjectFleetPolicyWhenCommandFlagsAreOmitted() throws Exception {
        final Path entryFile = tempDir.resolve("fleet-project-policy.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("fleet=" + max(3, 7));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                """,
                UTF_8
        );
        final Path missingGlobal = tempDir.resolve("missing-global-policy.properties");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                missingGlobal.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("fleet-project-policy-out").toString()
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode, stderr.toString(UTF_8));
        assertTrue(stdoutText.contains("fleet=7"));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runCommandPolicyOverridesProjectFleetPolicy() throws Exception {
        final Path entryFile = tempDir.resolve("fleet-command-override.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log(max(1, 2));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                """,
                UTF_8
        );
        final Path missingGlobal = tempDir.resolve("missing-global-policy.properties");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                missingGlobal.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("fleet-command-override-out").toString(),
                                "--interop-policy",
                                "strict"
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-POLICY\""));
        assertTrue(stderrText.contains("--interop-spec"));
    }

    @Test
    void runDetectsConflictingFleetPolicySourcesWithoutCommandOverride() throws Exception {
        final Path entryFile = tempDir.resolve("fleet-policy-conflict.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log(max(1, 2));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                """,
                UTF_8
        );
        final Path globalPolicy = tempDir.resolve("global-policy.properties");
        Files.writeString(
                globalPolicy,
                """
                interop.policy=strict
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                globalPolicy.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("fleet-policy-conflict-out").toString()
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-POLICY-CONFLICT\""));
        assertTrue(stderrText.contains("Resolve"));
        assertTrue(stderrText.contains("--interop-policy"));
    }

    @Test
    void runRejectsBroadInteropWhenTsj43cRequiredRoleIsMissing() throws Exception {
        final Path entryFile = tempDir.resolve("tsj43c-rbac-deny.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("rbac=" + max(2, 3));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                interop.rbac.requiredRoles=interop.operator
                """,
                UTF_8
        );
        final Path missingGlobal = tempDir.resolve("missing-global-policy.properties");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                missingGlobal.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("tsj43c-rbac-deny-out").toString()
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertEquals("", stdout.toString(UTF_8));
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-RBAC\""));
        assertTrue(stderrText.contains("interop.operator"));
        assertTrue(stderrText.contains("\"scope\":\"general\""));
    }

    @Test
    void runAllowsBroadInteropWhenTsj43cRequiredRoleIsProvided() throws Exception {
        final Path entryFile = tempDir.resolve("tsj43c-rbac-allow.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("rbac=" + max(4, 9));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                interop.rbac.requiredRoles=interop.operator
                """,
                UTF_8
        );
        final Path missingGlobal = tempDir.resolve("missing-global-policy.properties");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                missingGlobal.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("tsj43c-rbac-allow-out").toString(),
                                "--interop-role",
                                "interop.operator"
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        assertEquals(0, exitCode, stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("rbac=9"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runRequiresTsj43cApprovalTokenForSensitiveInteropTargets() throws Exception {
        final Path entryFile = tempDir.resolve("tsj43c-approval-deny.ts");
        Files.writeString(
                entryFile,
                """
                import { getProperty } from "java:java.lang.System";
                console.log("java=" + getProperty("java.version"));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                interop.rbac.requiredRoles=interop.operator
                interop.rbac.sensitiveTargets=java.lang.System
                interop.rbac.sensitiveRequiredRoles=interop.admin
                interop.approval.required=true
                interop.approval.targets=java.lang.System
                interop.approval.token=ticket-123
                """,
                UTF_8
        );
        final Path missingGlobal = tempDir.resolve("missing-global-policy.properties");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                missingGlobal.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("tsj43c-approval-deny-out").toString(),
                                "--interop-role",
                                "interop.admin"
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertEquals("", stdout.toString(UTF_8));
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-APPROVAL\""));
        assertTrue(stderrText.contains("java.lang.System"));
        assertTrue(stderrText.contains("ticket-123"));
    }

    @Test
    void runAllowsSensitiveInteropWhenTsj43cApprovalTokenIsProvided() throws Exception {
        final Path entryFile = tempDir.resolve("tsj43c-approval-allow.ts");
        Files.writeString(
                entryFile,
                """
                import { getProperty } from "java:java.lang.System";
                console.log("java=" + getProperty("java.version"));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                interop.rbac.requiredRoles=interop.operator
                interop.rbac.sensitiveTargets=java.lang.System
                interop.rbac.sensitiveRequiredRoles=interop.admin
                interop.approval.required=true
                interop.approval.targets=java.lang.System
                interop.approval.token=ticket-123
                """,
                UTF_8
        );
        final Path missingGlobal = tempDir.resolve("missing-global-policy.properties");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                missingGlobal.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("tsj43c-approval-allow-out").toString(),
                                "--interop-role",
                                "interop.admin",
                                "--interop-approval",
                                "ticket-123"
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        assertEquals(0, exitCode, stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("java="));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runUsesTsj43cFleetRolesWhenCommandRoleFlagsAreOmitted() throws Exception {
        final Path entryFile = tempDir.resolve("tsj43c-fleet-role.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("fleet-role=" + max(5, 7));
                """,
                UTF_8
        );
        final Path projectPolicy = tempDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(projectPolicy.getParent());
        Files.writeString(
                projectPolicy,
                """
                interop.policy=broad
                interop.ackRisk=true
                interop.rbac.requiredRoles=interop.operator
                interop.rbac.roles=interop.operator
                """,
                UTF_8
        );
        final Path missingGlobal = tempDir.resolve("missing-global-policy.properties");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = withSystemProperty(
                "tsj.interop.globalPolicy",
                missingGlobal.toString(),
                () -> TsjCli.execute(
                        new String[]{
                                "run",
                                entryFile.toString(),
                                "--out",
                                tempDir.resolve("tsj43c-fleet-role-out").toString()
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr)
                )
        );

        assertEquals(0, exitCode, stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("fleet-role=7"));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-RUN-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void compileRejectsInteropTargetsBlockedByDenylistPattern() throws Exception {
        final Path entryFile = tempDir.resolve("denylist-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log(max(2, 8));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("denylist-interop-out").toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--interop-denylist",
                        "java.lang.Math"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-DENYLIST\""));
        assertTrue(stderrText.contains("java.lang.Math"));
    }

    @Test
    void runWritesInteropAuditLogEntryWhenConfigured() throws Exception {
        final Path entryFile = tempDir.resolve("audit-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("max=" + max(4, 6));
                """,
                UTF_8
        );
        final Path auditLog = tempDir.resolve("audit/interop.jsonl");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("audit-interop-out").toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--interop-audit-log",
                        auditLog.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(auditLog));
        final String auditText = Files.readString(auditLog, UTF_8);
        assertTrue(auditText.contains("\"decision\":\"allow\""));
        assertTrue(auditText.contains("java.lang.Math#max"));
    }

    @Test
    void runWritesTsj43bCentralizedAuditAggregateEventsWhenConfigured() throws Exception {
        final Path entryFile = tempDir.resolve("audit-aggregate-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("max=" + max(1, 9));
                """,
                UTF_8
        );
        final Path auditLog = tempDir.resolve("audit/interop-local.jsonl");
        final Path aggregateLog = tempDir.resolve("audit/interop-aggregate.jsonl");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("audit-aggregate-interop-out").toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--interop-audit-log",
                        auditLog.toString(),
                        "--interop-audit-aggregate",
                        aggregateLog.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(auditLog));
        assertTrue(Files.exists(aggregateLog));
        final String aggregateText = Files.readString(aggregateLog, UTF_8);
        assertTrue(aggregateText.contains("\"schema\":\"tsj.interop.audit.v1\""));
        assertTrue(aggregateText.contains("\"command\":\"run\""));
        assertTrue(aggregateText.contains("\"decision\":\"allow\""));
        assertTrue(aggregateText.contains("\"outcome\":\"success\""));
        assertTrue(aggregateText.contains("\"target\":\"java.lang.Math#max\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runFallsBackToLocalAuditWhenTsj43bAggregateSinkIsUnavailable() throws Exception {
        final Path entryFile = tempDir.resolve("audit-aggregate-fallback.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("max=" + max(5, 8));
                """,
                UTF_8
        );
        final Path auditLog = tempDir.resolve("audit/interop-fallback.jsonl");
        final Path aggregateSink = tempDir.resolve("audit/aggregate-as-directory");
        Files.createDirectories(aggregateSink);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("audit-aggregate-fallback-out").toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--interop-audit-log",
                        auditLog.toString(),
                        "--interop-audit-aggregate",
                        aggregateSink.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(auditLog));
        final String auditText = Files.readString(auditLog, UTF_8);
        assertTrue(auditText.contains("\"decision\":\"allow\""));
        assertTrue(auditText.contains("java.lang.Math#max"));
        assertTrue(auditText.contains("\"code\":\"TSJ-INTEROP-AUDIT-AGGREGATE\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void runInteropTraceFlagEmitsRuntimeInteropTraceLines() throws Exception {
        final Path entryFile = tempDir.resolve("trace-interop.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math";
                console.log("trace=" + max(3, 9));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("trace-interop-out").toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--interop-trace"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("trace=9"));
        assertTrue(stderrText.contains("TSJ-INTEROP-TRACE invoke"));
        assertTrue(stderrText.contains("java.lang.Math"));
    }

    @Test
    void compileRejectsInvalidInteropPolicyValue() throws Exception {
        final Path entryFile = tempDir.resolve("invalid-policy.ts");
        Files.writeString(entryFile, "console.log('x');\n", UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        entryFile.toString(),
                        "--out",
                        tempDir.resolve("invalid-policy-out").toString(),
                        "--interop-policy",
                        "wide-open"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(2, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-CLI-013\""));
        assertTrue(stderrText.contains("strict"));
        assertTrue(stderrText.contains("broad"));
    }

    @Test
    void runRejectsDefaultJavaInteropImportWithFeatureDiagnosticMetadata() throws Exception {
        final Path entryFile = tempDir.resolve("main-interop-default.ts");
        Files.writeString(
                entryFile,
                """
                import Math from "java:java.lang.Math";
                console.log(Math);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("interop-default-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("\"featureId\":\"TSJ26-INTEROP-SYNTAX\""));
        assertTrue(stderrText.contains("Use named imports"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void runRejectsInvalidJavaInteropModuleSpecifierWithFeatureDiagnosticMetadata() throws Exception {
        final Path entryFile = tempDir.resolve("main-interop-invalid.ts");
        Files.writeString(
                entryFile,
                """
                import { max } from "java:java.lang.Math#max";
                console.log(max(1, 2));
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("interop-invalid-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("\"featureId\":\"TSJ26-INTEROP-MODULE-SPECIFIER\""));
        assertTrue(stderrText.contains("fully.qualified.ClassName"));
    }

    @Test
    void runRejectsNonRelativeImportInTsj12Bootstrap() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                entryFile,
                """
                import { readFileSync } from "fs";
                console.log(readFileSync);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"run", entryFile.toString(), "--out", tempDir.resolve("bad-import-out2").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderr.toString(UTF_8).contains("relative imports"));
    }

    @Test
    void compileMissingOutFlagReturnsStructuredError() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(entryFile, "const x = 1;\n", UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-003\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileMissingInputFileReturnsStructuredError() {
        final Path outDir = tempDir.resolve("build");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", tempDir.resolve("missing.ts").toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-COMPILE-001\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileUnsupportedSyntaxReturnsBackendDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("unsupported.ts");
        Files.writeString(
                entryFile,
                """
                for (let i = 0; i < 2; i = i + 1) {
                  console.log(i);
                }
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("bad-out").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileDynamicImportIncludesUnsupportedFeatureContext() throws Exception {
        final Path entryFile = tempDir.resolve("dynamic-import.ts");
        Files.writeString(
                entryFile,
                """
                const loader = import("./dep.ts");
                console.log(loader);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("bad-out2").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-BACKEND-UNSUPPORTED\""));
        assertTrue(stderrText.contains("\"featureId\":\"TSJ15-DYNAMIC-IMPORT\""));
        assertTrue(stderrText.contains("\"file\":\"" + entryFile.toAbsolutePath().normalize() + "\""));
        assertTrue(stderrText.contains("\"line\":\"1\""));
        assertTrue(stderrText.contains("\"column\":\"16\""));
        assertTrue(stderrText.contains("Use static relative imports"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileEvalIncludesUnsupportedFeatureContext() throws Exception {
        final Path entryFile = tempDir.resolve("eval.ts");
        Files.writeString(
                entryFile,
                """
                const value = eval("1 + 2");
                console.log(value);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("bad-out3").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"featureId\":\"TSJ15-EVAL\""));
        assertTrue(stderrText.contains("\"file\":\"" + entryFile.toAbsolutePath().normalize() + "\""));
        assertTrue(stderrText.contains("\"line\":\"1\""));
        assertTrue(stderrText.contains("runtime code evaluation"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileProxyIncludesUnsupportedFeatureContext() throws Exception {
        final Path entryFile = tempDir.resolve("proxy.ts");
        Files.writeString(
                entryFile,
                """
                const target = { value: 1 };
                const proxy = new Proxy(target, {});
                console.log(proxy.value);
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("bad-out4").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"featureId\":\"TSJ15-PROXY\""));
        assertTrue(stderrText.contains("\"line\":\"2\""));
        assertTrue(stderrText.contains("Proxy semantics are outside MVP"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileFunctionConstructorIncludesUnsupportedFeatureContext() throws Exception {
        final Path entryFile = tempDir.resolve("function-constructor.ts");
        Files.writeString(
                entryFile,
                """
                const factory = Function("return 7;");
                console.log(factory());
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("bad-out5").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"featureId\":\"TSJ15-FUNCTION-CONSTRUCTOR\""));
        assertTrue(stderrText.contains("\"line\":\"1\""));
        assertTrue(stderrText.contains("runtime code evaluation"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void compileUnsupportedFeatureInImportedModuleUsesModulePathInDiagnostic() throws Exception {
        final Path moduleFile = tempDir.resolve("dep.ts");
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(moduleFile, "const value = eval(\"2 + 3\");\nconsole.log(value);\n", UTF_8);
        Files.writeString(entryFile, "import \"./dep.ts\";\nconsole.log(\"main\");\n", UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", tempDir.resolve("bad-out6").toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"featureId\":\"TSJ15-EVAL\""));
        assertTrue(stderrText.contains("\"file\":\"" + moduleFile.toAbsolutePath().normalize() + "\""));
        assertTrue(stderrText.contains("\"line\":\"1\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void interopCommandGeneratesAllowlistedBridges() throws Exception {
        final Path specFile = tempDir.resolve("interop.properties");
        final Path outDir = tempDir.resolve("interop-out");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max
                targets=java.lang.Math#max
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"interop", specFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-INTEROP-SUCCESS\""));
        assertTrue(stdout.toString(UTF_8).contains("\"generatedCount\":\"1\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void interopCommandReportsDisallowedTargetDiagnostic() throws Exception {
        final Path specFile = tempDir.resolve("interop.properties");
        final Path outDir = tempDir.resolve("interop-out2");
        Files.writeString(
                specFile,
                """
                allowlist=java.lang.Math#max
                targets=java.lang.System#exit
                """,
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"interop", specFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-DISALLOWED\""));
        assertTrue(stderrText.contains("\"featureId\":\"TSJ19-ALLOWLIST\""));
        assertTrue(stderrText.contains("java.lang.System#exit"));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void interopCommandSupportsSpringConfigurationBeanTargets() throws Exception {
        final String fixtureClass = SpringBeanFixture.class.getName();
        final Path specFile = tempDir.resolve("interop-spring.properties");
        final Path outDir = tempDir.resolve("interop-spring-out");
        Files.writeString(
                specFile,
                """
                allowlist=%s#$new
                targets=%s#$new
                springConfiguration=true
                springBeanTargets=%s#$new
                """.formatted(fixtureClass, fixtureClass, fixtureClass),
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"interop", specFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-INTEROP-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));
        final Path sourceFile;
        try (Stream<Path> paths = Files.walk(outDir.resolve("dev/tsj/generated/interop"))) {
            sourceFile = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .findFirst()
                    .orElseThrow();
        }
        final String source = Files.readString(sourceFile, UTF_8);
        assertTrue(source.contains("@org.springframework.context.annotation.Configuration"));
        assertTrue(source.contains("@org.springframework.context.annotation.Bean"));
        assertTrue(source.contains("public dev.tsj.cli.TsjCliTest.SpringBeanFixture _new(final java.lang.String arg0)"));
    }

    @Test
    void interopCommandSupportsSpringWebControllerTargets() throws Exception {
        final String fixtureClass = SpringWebFixture.class.getName();
        final Path specFile = tempDir.resolve("interop-spring-web.properties");
        final Path outDir = tempDir.resolve("interop-spring-web-out");
        Files.writeString(
                specFile,
                """
                allowlist=%s#echo,%s#validate
                targets=%s#echo,%s#validate
                springWebController=true
                springWebBasePath=/api
                springRequestMappings.echo=GET /echo
                springRequestMappings.validate=GET /validate
                springErrorMappings=java.lang.IllegalArgumentException:400
                """.formatted(fixtureClass, fixtureClass, fixtureClass, fixtureClass),
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"interop", specFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-INTEROP-SUCCESS\""));
        assertEquals("", stderr.toString(UTF_8));

        final Path sourceFile;
        try (Stream<Path> paths = Files.walk(outDir.resolve("dev/tsj/generated/interop"))) {
            sourceFile = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .findFirst()
                    .orElseThrow();
        }
        final String source = Files.readString(sourceFile, UTF_8);
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RestController"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestMapping(\"/api\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.GetMapping(\"/echo\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.GetMapping(\"/validate\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.ResponseStatus"));
    }

    @Test
    void compileGeneratesTsDecoratedSpringWebControllerAdapters() throws Exception {
        final Path entryFile = tempDir.resolve("tsj34-controller.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class EchoController {
                  @GetMapping("/echo")
                  echo(value: string) {
                    return "echo:" + value;
                  }
                }

                console.log("ready");
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj34-controller-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-COMPILE-SUCCESS\""));
        final Path generatedSource = outDir.resolve(
                "generated-web/dev/tsj/generated/web/EchoControllerTsjController.java"
        );
        assertTrue(Files.exists(generatedSource));
        final String source = Files.readString(generatedSource, UTF_8);
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RestController"));
        assertTrue(source.contains(".__tsjInvokeController("));
    }

    @Test
    void compileGeneratesTsDecoratedSpringComponentAdapters() throws Exception {
        final Path entryFile = tempDir.resolve("tsj33a-service.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class GreetingService {
                  constructor(prefix: any) {
                  }

                  greet(name: string) {
                    return "hi:" + name;
                  }
                }

                console.log("ready");
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj33a-service-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path generatedSource = outDir.resolve(
                "generated-components/dev/tsj/generated/spring/GreetingServiceTsjComponent.java"
        );
        assertTrue(Files.exists(generatedSource));
        final String source = Files.readString(generatedSource, UTF_8);
        assertTrue(source.contains("@org.springframework.stereotype.Service"));
        assertTrue(
                source.contains(".__tsjInvokeClassWithInjection(")
                        || source.contains(".__tsjInvokeClass(")
        );

        final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
        assertEquals("1", artifact.getProperty("tsjSpringComponents.componentCount"));
        assertEquals("1", artifact.getProperty("tsjSpringComponents.generatedSourceCount"));
    }

    @Test
    void compileGeneratesTsConfigurationBeanComponentAdapters() throws Exception {
        final Path entryFile = tempDir.resolve("tsj33b-config.ts");
        Files.writeString(
                entryFile,
                """
                @Configuration
                class AppConfig {
                  @Bean
                  greeting() {
                    return "hello";
                  }
                }
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj33b-config-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"compile", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        final Path generatedSource = outDir.resolve(
                "generated-components/dev/tsj/generated/spring/AppConfigTsjComponent.java"
        );
        assertTrue(Files.exists(generatedSource));
        final String source = Files.readString(generatedSource, UTF_8);
        assertTrue(source.contains("@org.springframework.context.annotation.Configuration"));
        assertTrue(source.contains("@org.springframework.context.annotation.Bean"));
    }

    @Test
    void springPackageBuildsRunnableJarAndIncludesResourceFiles() throws Exception {
        final Path projectDir = tempDir.resolve("tsj36-packaging-app");
        final Path entryFile = projectDir.resolve("main.ts");
        final Path resourceDir = projectDir.resolve("src/main/resources");
        final Path staticDir = resourceDir.resolve("static");
        Files.createDirectories(staticDir);
        Files.writeString(entryFile, "console.log('tsj36:boot');\n", UTF_8);
        Files.writeString(resourceDir.resolve("application.properties"), "app.name=tsj36\n", UTF_8);
        Files.writeString(staticDir.resolve("health.txt"), "ok\n", UTF_8);
        final Path outDir = projectDir.resolve("out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"spring-package", entryFile.toString(), "--out", outDir.toString(), "--smoke-run"},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-SPRING-PACKAGE-SUCCESS\""));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-SPRING-SMOKE-SUCCESS\""));

        final Path jarPath = outDir.resolve("tsj-spring-app.jar");
        assertTrue(Files.exists(jarPath));
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertNotNull(jarFile.getJarEntry("dev/tsj/generated/MainProgram.class"));
            assertNotNull(jarFile.getJarEntry("application.properties"));
            assertNotNull(jarFile.getJarEntry("static/health.txt"));
            assertNotNull(jarFile.getJarEntry("dev/tsj/runtime/TsjRuntime.class"));
        }
        final ProcessResult jarRun = runJarAndCaptureOutput(jarPath);
        assertEquals(0, jarRun.exitCode());
        assertTrue(jarRun.output().contains("tsj36:boot"));
    }

    @Test
    void springPackageSupportsCustomJarPathAndExplicitResourceDirectory() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36-custom-main.ts");
        Files.writeString(entryFile, "console.log('custom-jar');\n", UTF_8);
        final Path explicitResources = tempDir.resolve("tsj36-explicit-resources");
        Files.createDirectories(explicitResources.resolve("static"));
        Files.writeString(explicitResources.resolve("application.yml"), "name: tsj36\n", UTF_8);
        Files.writeString(explicitResources.resolve("static/info.txt"), "hello\n", UTF_8);
        final Path outDir = tempDir.resolve("tsj36-custom-out");
        final Path bootJar = outDir.resolve("custom-app.jar");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--resource-dir",
                        explicitResources.toString(),
                        "--boot-jar",
                        bootJar.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-SPRING-PACKAGE-SUCCESS\""));
        assertTrue(Files.exists(bootJar));
        try (JarFile jarFile = new JarFile(bootJar.toFile())) {
            assertNotNull(jarFile.getJarEntry("application.yml"));
            assertNotNull(jarFile.getJarEntry("static/info.txt"));
            assertNotNull(jarFile.getJarEntry("dev/tsj/runtime/TsjRuntime.class"));
        }
        final ProcessResult jarRun = runJarAndCaptureOutput(bootJar);
        assertEquals(0, jarRun.exitCode());
        assertTrue(jarRun.output().contains("custom-jar"));
    }

    @Test
    void springPackageRejectsMissingExplicitResourceDirectory() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36-missing-resource-main.ts");
        Files.writeString(entryFile, "console.log('missing-resource');\n", UTF_8);
        final Path outDir = tempDir.resolve("tsj36-missing-resource-out");
        final Path missingDir = tempDir.resolve("does-not-exist-resources");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--resource-dir",
                        missingDir.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-SPRING-PACKAGE\""));
        assertTrue(stderrText.contains("\"stage\":\"package\""));
        assertTrue(stderrText.contains("\"failureKind\":\"resource\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void springPackageMarksCompileFailuresWithCompileStage() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36-compile-failure.ts");
        Files.writeString(entryFile, "const = ;\n", UTF_8);
        final Path outDir = tempDir.resolve("tsj36-compile-failure-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"spring-package", entryFile.toString(), "--out", outDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"stage\":\"compile\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void springPackageMarksInteropBridgeFailuresWithBridgeStage() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36-bridge-failure.ts");
        Files.writeString(
                entryFile,
                """
                import { ping } from "java:sample.missing.Bridge";
                console.log(ping());
                """,
                UTF_8
        );
        final Path specFile = tempDir.resolve("tsj36-bridge-failure.properties");
        Files.writeString(
                specFile,
                """
                allowlist=sample.missing.Bridge#ping
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj36-bridge-failure-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-spec",
                        specFile.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"stage\":\"bridge\""));
        assertTrue(stderrText.contains("\"code\":\"TSJ-INTEROP-INVALID\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void springPackageMarksSmokeRunFailuresWithRuntimeStage() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36-runtime-failure.ts");
        Files.writeString(
                entryFile,
                """
                function fail() {
                  throw "boot-failure";
                }
                fail();
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj36-runtime-failure-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--smoke-run"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-SPRING-BOOT\""));
        assertTrue(stderrText.contains("\"stage\":\"runtime\""));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-SPRING-PACKAGE-SUCCESS\""));
    }

    @Test
    void springPackageSmokeRunVerifiesEmbeddedEndpointAvailability() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36b-endpoint-ok.ts");
        Files.writeString(
                entryFile,
                """
                import { sleep } from "java:java.lang.Thread";
                console.log("TSJ36B_ENDPOINT_OK");
                sleep(1200);
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj36b-endpoint-ok-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--smoke-run",
                        "--smoke-endpoint-url",
                        "stdout://TSJ36B_ENDPOINT_OK",
                        "--smoke-timeout-ms",
                        "3000",
                        "--smoke-poll-ms",
                        "100"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stdoutText = stdout.toString(UTF_8);
        assertEquals(0, exitCode, "stderr=" + stderr.toString(UTF_8) + "\nstdout=" + stdoutText);
        assertEquals("", stderr.toString(UTF_8));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-SPRING-PACKAGE-SUCCESS\""));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-SPRING-SMOKE-ENDPOINT-SUCCESS\""));
        assertTrue(stdoutText.contains("\"code\":\"TSJ-SPRING-SMOKE-SUCCESS\""));
        assertTrue(stdoutText.contains("\"endpointUrl\":\"stdout://TSJ36B_ENDPOINT_OK\""));
    }

    @Test
    void springPackageMarksEndpointSmokeFailuresSeparatelyFromStartupFailures() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36b-endpoint-failure.ts");
        Files.writeString(
                entryFile,
                """
                import { sleep } from "java:java.lang.Thread";
                console.log("TSJ36B_OTHER_MARKER");
                sleep(1800);
                """,
                UTF_8
        );
        final Path outDir = tempDir.resolve("tsj36b-endpoint-failure-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--smoke-run",
                        "--smoke-endpoint-url",
                        "stdout://TSJ36B_ENDPOINT_OK",
                        "--smoke-timeout-ms",
                        "1400",
                        "--smoke-poll-ms",
                        "120"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String stderrText = stderr.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(stderrText.contains("\"code\":\"TSJ-SPRING-ENDPOINT\""), stderrText);
        assertTrue(stderrText.contains("\"stage\":\"runtime\""));
        assertTrue(stderrText.contains("\"failureKind\":\"endpoint\""));
        assertTrue(stderrText.contains("\"endpointUrl\":\"stdout://TSJ36B_ENDPOINT_OK\""));
        assertTrue(stderrText.contains("\"endpointError\":\"Marker not observed: TSJ36B_ENDPOINT_OK\""), stderrText);
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-SPRING-PACKAGE-SUCCESS\""));
    }

    @Test
    void springPackageRejectsSmokeEndpointOptionsWithoutSmokeRun() throws Exception {
        final Path entryFile = tempDir.resolve("tsj36b-endpoint-option.ts");
        Files.writeString(entryFile, "console.log('endpoint-option');\n", UTF_8);
        final Path outDir = tempDir.resolve("tsj36b-endpoint-option-out");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--smoke-endpoint-url",
                        "http://127.0.0.1:18081/health"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-017\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void interopCommandRequiresSpecFilePath() {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"interop"},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(UTF_8).contains("\"code\":\"TSJ-CLI-008\""));
        assertEquals("", stdout.toString(UTF_8));
    }

    @Test
    void moduleFingerprintContainsCompilerAndRuntimeModules() {
        assertEquals(
                "compiler-frontend|compiler-ir|compiler-backend-jvm|runtime",
                TsjCli.moduleFingerprint()
        );
    }

    @Test
    void fixturesCommandExecutesFixtureHarness() throws Exception {
        final Path fixturesRoot = tempDir.resolve("fixtures");
        final Path fixtureDir = fixturesRoot.resolve("basic");
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(inputDir.resolve("main.ts"), "console.log('fixture');\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stdout"), "fixture\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "\"code\":\"TSJ-RUN-SUCCESS\"", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "", UTF_8);
        Files.writeString(
                fixtureDir.resolve("fixture.properties"),
                String.join(
                        "\n",
                        "name=basic",
                        "entry=input/main.ts",
                        "expected.node.exitCode=0",
                        "expected.node.stdout=expected/node.stdout",
                        "expected.node.stderr=expected/node.stderr",
                        "expected.node.stdoutMode=exact",
                        "expected.node.stderrMode=exact",
                        "expected.tsj.exitCode=0",
                        "expected.tsj.stdout=expected/tsj.stdout",
                        "expected.tsj.stderr=expected/tsj.stderr",
                        "expected.tsj.stdoutMode=contains",
                        "expected.tsj.stderrMode=exact",
                        "assert.nodeMatchesTsj=false",
                        ""
                ),
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"fixtures", fixturesRoot.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-FIXTURE-SUMMARY\""));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-FIXTURE-COVERAGE\""));
        assertEquals("", stderr.toString(UTF_8));
    }

    @Test
    void fixturesCommandEmitsMinimizedReproOnFailure() throws Exception {
        final Path fixturesRoot = tempDir.resolve("fixtures-failing");
        final Path fixtureDir = fixturesRoot.resolve("failing");
        final Path inputDir = fixtureDir.resolve("input");
        final Path expectedDir = fixtureDir.resolve("expected");
        Files.createDirectories(inputDir);
        Files.createDirectories(expectedDir);

        Files.writeString(
                inputDir.resolve("main.ts"),
                """
                for (let i = 0; i < 2; i = i + 1) {
                  console.log(i);
                }
                """,
                UTF_8
        );
        Files.writeString(expectedDir.resolve("node.stdout"), "0\n1\n", UTF_8);
        Files.writeString(expectedDir.resolve("node.stderr"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stdout"), "", UTF_8);
        Files.writeString(expectedDir.resolve("tsj.stderr"), "\"code\":\"TSJ-BACKEND-UNSUPPORTED\"", UTF_8);
        Files.writeString(
                fixtureDir.resolve("fixture.properties"),
                String.join(
                        "\n",
                        "name=tsj16-failing",
                        "entry=input/main.ts",
                        "expected.node.exitCode=0",
                        "expected.node.stdout=expected/node.stdout",
                        "expected.node.stderr=expected/node.stderr",
                        "expected.node.stdoutMode=exact",
                        "expected.node.stderrMode=exact",
                        "expected.tsj.exitCode=1",
                        "expected.tsj.stdout=expected/tsj.stdout",
                        "expected.tsj.stderr=expected/tsj.stderr",
                        "expected.tsj.stdoutMode=exact",
                        "expected.tsj.stderrMode=contains",
                        "assert.nodeMatchesTsj=true",
                        ""
                ),
                UTF_8
        );

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final int exitCode = TsjCli.execute(
                new String[]{"fixtures", fixturesRoot.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        final String output = stdout.toString(UTF_8);
        assertEquals(1, exitCode);
        assertTrue(output.contains("\"code\":\"TSJ-FIXTURE-FAIL\""));
        assertTrue(output.contains("\"minimalRepro\""));
        assertTrue(output.contains("tsj run"));
        assertEquals("", stderr.toString(UTF_8));
    }

    private Path buildInteropJar(final String className, final String sourceText) throws Exception {
        return buildInteropJar(
                className,
                sourceText,
                List.of(),
                sanitizeFileSegment(className) + ".jar"
        );
    }

    private Path buildInteropJar(
            final String className,
            final String sourceText,
            final List<Path> compileClasspath,
            final String jarFileName
    ) throws Exception {
        return buildInteropJar(
                className,
                sourceText,
                compileClasspath,
                jarFileName,
                null,
                List.of()
        );
    }

    private Path buildInteropJarWithMavenMetadata(
            final String className,
            final String sourceText,
            final List<Path> compileClasspath,
            final String jarFileName,
            final MavenCoordinate coordinate,
            final List<MavenCoordinate> dependencies
    ) throws Exception {
        final List<MavenDependencySpec> scopedDependencies = dependencies.stream()
                .map(dependency -> new MavenDependencySpec(dependency, null))
                .toList();
        return buildInteropJarWithScopedMavenMetadata(
                className,
                sourceText,
                compileClasspath,
                jarFileName,
                coordinate,
                scopedDependencies
        );
    }

    private Path buildInteropJarWithScopedMavenMetadata(
            final String className,
            final String sourceText,
            final List<Path> compileClasspath,
            final String jarFileName,
            final MavenCoordinate coordinate,
            final List<MavenDependencySpec> dependencies
    ) throws Exception {
        return buildInteropJar(
                className,
                sourceText,
                compileClasspath,
                jarFileName,
                coordinate,
                dependencies
        );
    }

    private Path buildInteropJar(
            final String className,
            final String sourceText,
            final List<Path> compileClasspath,
            final String jarFileName,
            final MavenCoordinate coordinate,
            final List<MavenDependencySpec> dependencies
    ) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for integration tests.");
        }
        final Path sourceRoot = tempDir.resolve("java-src-" + sanitizeFileSegment(className));
        final Path classesRoot = tempDir.resolve("java-classes-" + sanitizeFileSegment(className));
        final Path javaSource = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(javaSource.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(javaSource, sourceText, UTF_8);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(List.of(javaSource));
            final List<String> options = new java.util.ArrayList<>();
            options.add("--release");
            options.add("21");
            if (!compileClasspath.isEmpty()) {
                options.add("-classpath");
                options.add(
                        compileClasspath.stream()
                                .map(path -> path.toAbsolutePath().normalize().toString())
                                .reduce((left, right) -> left + File.pathSeparator + right)
                                .orElse("")
                );
            }
            options.add("-d");
            options.add(classesRoot.toString());
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    compilationUnits
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile Java fixture class " + className);
            }
        }

        final Path jarFile = tempDir.resolve(jarFileName);
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            try (Stream<Path> paths = Files.walk(classesRoot)) {
                final List<Path> classFiles = paths
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .sorted()
                        .toList();
                for (Path classFile : classFiles) {
                    final String entryName = classesRoot
                            .relativize(classFile)
                            .toString()
                            .replace(File.separatorChar, '/');
                    final JarEntry entry = new JarEntry(entryName);
                    jarOutputStream.putNextEntry(entry);
                    jarOutputStream.write(Files.readAllBytes(classFile));
                    jarOutputStream.closeEntry();
                }
            }
            if (coordinate != null) {
                final String mavenRoot = "META-INF/maven/" + coordinate.groupId() + "/" + coordinate.artifactId();
                writeTextJarEntry(
                        jarOutputStream,
                        mavenRoot + "/pom.properties",
                        """
                        groupId=%s
                        artifactId=%s
                        version=%s
                        """.formatted(coordinate.groupId(), coordinate.artifactId(), coordinate.version())
                );
                writeTextJarEntry(
                        jarOutputStream,
                        mavenRoot + "/pom.xml",
                        buildPomXml(coordinate, dependencies)
                );
            }
        }
        return jarFile.toAbsolutePath().normalize();
    }

    private static void writeTextJarEntry(
            final JarOutputStream jarOutputStream,
            final String entryName,
            final String content
    ) throws IOException {
        final JarEntry entry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(entry);
        jarOutputStream.write(content.getBytes(UTF_8));
        jarOutputStream.closeEntry();
    }

    private static String buildPomXml(
            final MavenCoordinate coordinate,
            final List<MavenDependencySpec> dependencies
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("<project>");
        builder.append("<modelVersion>4.0.0</modelVersion>");
        builder.append("<groupId>").append(coordinate.groupId()).append("</groupId>");
        builder.append("<artifactId>").append(coordinate.artifactId()).append("</artifactId>");
        builder.append("<version>").append(coordinate.version()).append("</version>");
        builder.append("<dependencies>");
        for (MavenDependencySpec dependency : dependencies) {
            builder.append("<dependency>");
            builder.append("<groupId>").append(dependency.coordinate().groupId()).append("</groupId>");
            builder.append("<artifactId>").append(dependency.coordinate().artifactId()).append("</artifactId>");
            builder.append("<version>").append(dependency.coordinate().version()).append("</version>");
            if (dependency.scope() != null && !dependency.scope().isBlank()) {
                builder.append("<scope>").append(dependency.scope()).append("</scope>");
            }
            builder.append("</dependency>");
        }
        builder.append("</dependencies>");
        builder.append("</project>");
        return builder.toString();
    }

    private static void assertMediationDecisionPresent(
            final Properties artifact,
            final String artifactId,
            final String selectedVersion,
            final String rejectedVersion,
            final String rule
    ) {
        final int count = Integer.parseInt(artifact.getProperty("interopClasspath.mediation.count", "0"));
        boolean found = false;
        for (int index = 0; index < count; index++) {
            final String prefix = "interopClasspath.mediation." + index + ".";
            final String candidateArtifact = artifact.getProperty(prefix + "artifact");
            final String candidateSelected = artifact.getProperty(prefix + "selectedVersion");
            final String candidateRejected = artifact.getProperty(prefix + "rejectedVersion");
            final String candidateRule = artifact.getProperty(prefix + "rule");
            if (artifactId.equals(candidateArtifact)
                    && selectedVersion.equals(candidateSelected)
                    && rejectedVersion.equals(candidateRejected)
                    && rule.equals(candidateRule)) {
                found = true;
                break;
            }
        }
        assertTrue(
                found,
                "Expected mediation decision artifact=%s selected=%s rejected=%s rule=%s"
                        .formatted(artifactId, selectedVersion, rejectedVersion, rule)
        );
    }

    private static MediationDecision requireMediationDecision(
            final Properties artifact,
            final String artifactId
    ) {
        final int count = Integer.parseInt(artifact.getProperty("interopClasspath.mediation.count", "0"));
        for (int index = 0; index < count; index++) {
            final String prefix = "interopClasspath.mediation." + index + ".";
            final String candidateArtifact = artifact.getProperty(prefix + "artifact");
            if (!artifactId.equals(candidateArtifact)) {
                continue;
            }
            return new MediationDecision(
                    candidateArtifact,
                    artifact.getProperty(prefix + "selectedVersion"),
                    artifact.getProperty(prefix + "selectedPath"),
                    artifact.getProperty(prefix + "rejectedVersion"),
                    artifact.getProperty(prefix + "rejectedPath"),
                    artifact.getProperty(prefix + "rule")
            );
        }
        throw new IllegalStateException("Mediation decision not found for artifact " + artifactId);
    }

    private static List<String> classpathEntries(final Properties artifact) {
        final int count = Integer.parseInt(artifact.getProperty("interopClasspath.count", "0"));
        final List<String> entries = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String value = artifact.getProperty("interopClasspath." + index);
            if (value != null) {
                entries.add(value);
            }
        }
        return List.copyOf(entries);
    }

    private static Properties loadArtifactProperties(final Path artifactFile) throws IOException {
        final Properties properties = new Properties();
        try (java.io.InputStream inputStream = Files.newInputStream(artifactFile)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static String sanitizeFileSegment(final String value) {
        return value.replace('.', '_');
    }

    private static String readGeneratedJavaSource(final Path outDir) throws Exception {
        try (Stream<Path> paths = Files.walk(outDir.resolve("generated-src"))) {
            final Path sourcePath = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .findFirst()
                    .orElseThrow();
            return Files.readString(sourcePath, UTF_8);
        }
    }

    private static ProcessResult runJarAndCaptureOutput(final Path jarPath) throws Exception {
        final ProcessBuilder processBuilder = new ProcessBuilder(
                resolveJavaLauncher().toString(),
                "-jar",
                jarPath.toAbsolutePath().normalize().toString()
        );
        processBuilder.redirectErrorStream(true);
        final Process process = processBuilder.start();
        final String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), UTF_8);
        }
        final int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private static Path resolveJavaLauncher() {
        final String executable = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }

    private static int withSystemProperty(
            final String key,
            final String value,
            final ThrowingIntSupplier supplier
    ) throws Exception {
        final String previous = System.getProperty(key);
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
        try {
            return supplier.getAsInt();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingIntSupplier {
        int getAsInt() throws Exception;
    }

    private record ProcessResult(int exitCode, String output) {
    }

    public static final class SpringBeanFixture {
        private final String value;

        public SpringBeanFixture(final String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static final class SpringWebFixture {
        private SpringWebFixture() {
        }

        public static String echo(final String value) {
            return "echo:" + value;
        }

        public static String validate(final String value) {
            if ("bad".equals(value)) {
                throw new IllegalArgumentException("bad value");
            }
            return value;
        }
    }

    private record MavenCoordinate(String groupId, String artifactId, String version) {
    }

    private record MavenDependencySpec(MavenCoordinate coordinate, String scope) {
    }

    private record MediationDecision(
            String artifact,
            String selectedVersion,
            String selectedPath,
            String rejectedVersion,
            String rejectedPath,
            String rule
    ) {
    }
}
