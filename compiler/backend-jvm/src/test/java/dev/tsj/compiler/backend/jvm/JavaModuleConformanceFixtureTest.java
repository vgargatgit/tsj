package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaModuleConformanceFixtureTest {
    @TempDir
    Path tempDir;

    @Test
    void fixtureValidatesClasspathOrderPrecedenceAcrossMixedEntries() throws Exception {
        final String fqcn = "sample.tsj57b.Precedence";
        final Path classesDir = tempDir.resolve("fixture-classes");
        compileClass(
                fqcn,
                """
                package sample.tsj57b;

                public final class Precedence {
                    public static int marker() {
                        return 1;
                    }
                }
                """,
                classesDir
        );
        final Path jarClasses = tempDir.resolve("fixture-jar-classes");
        compileClass(
                fqcn,
                """
                package sample.tsj57b;

                public final class Precedence {
                    public static int marker() {
                        return 2;
                    }
                }
                """,
                jarClasses
        );
        final Path jarPath = tempDir.resolve("fixture-module.jar");
        packageAutomaticModuleJar("fixture.module", jarClasses, jarPath);

        final JavaSymbolTable classpathFirst = new JavaSymbolTable(
                List.of(classesDir, jarPath),
                "fixture-precedence-a",
                21
        );
        final JavaSymbolTable.ClassResolution classpathFirstResult =
                classpathFirst.resolveClassWithMetadata(fqcn);
        assertEquals(JavaSymbolTable.ResolutionStatus.FOUND, classpathFirstResult.status());
        assertNotNull(classpathFirstResult.origin());
        assertEquals(classesDir.toAbsolutePath().normalize(), classpathFirstResult.origin().classpathEntry());
        assertFalse(classpathFirstResult.origin().versionedEntry());

        final JavaSymbolTable moduleFirst = new JavaSymbolTable(
                List.of(jarPath, classesDir),
                "fixture-precedence-b",
                21
        );
        final JavaSymbolTable.ClassResolution moduleFirstResult =
                moduleFirst.resolveClassWithMetadata(fqcn);
        assertEquals(JavaSymbolTable.ResolutionStatus.FOUND, moduleFirstResult.status());
        assertNotNull(moduleFirstResult.origin());
        assertEquals(jarPath.toAbsolutePath().normalize(), moduleFirstResult.origin().classpathEntry());
        assertEquals("sample/tsj57b/Precedence.class", moduleFirstResult.origin().entryName());
    }

    @Test
    void fixtureValidatesMrJarSelectionAndOriginMetadata() throws Exception {
        final String fqcn = "sample.tsj57b.MultiRelease";
        final Path baseClasses = tempDir.resolve("mr-base");
        final Path version21Classes = tempDir.resolve("mr-21");
        compileClass(
                fqcn,
                """
                package sample.tsj57b;

                public final class MultiRelease {
                    public static int marker() {
                        return 8;
                    }
                }
                """,
                baseClasses
        );
        compileClass(
                fqcn,
                """
                package sample.tsj57b;

                public final class MultiRelease {
                    public static int marker() {
                        return 21;
                    }
                }
                """,
                version21Classes
        );
        final Path jarPath = tempDir.resolve("mr-fixture.jar");
        packageMultiReleaseJar(fqcn, baseClasses, Map.of(21, version21Classes), jarPath);

        final JavaSymbolTable jdk17 = new JavaSymbolTable(List.of(jarPath), "mr-fixture-17", 17);
        final JavaSymbolTable.ClassResolution jdk17Result = jdk17.resolveClassWithMetadata(fqcn);
        assertEquals(JavaSymbolTable.ResolutionStatus.FOUND, jdk17Result.status());
        assertNotNull(jdk17Result.origin());
        assertFalse(jdk17Result.origin().versionedEntry());
        assertEquals("sample/tsj57b/MultiRelease.class", jdk17Result.origin().entryName());

        final JavaSymbolTable jdk21 = new JavaSymbolTable(List.of(jarPath), "mr-fixture-21", 21);
        final JavaSymbolTable.ClassResolution jdk21Result = jdk21.resolveClassWithMetadata(fqcn);
        assertEquals(JavaSymbolTable.ResolutionStatus.FOUND, jdk21Result.status());
        assertNotNull(jdk21Result.origin());
        assertTrue(jdk21Result.origin().versionedEntry());
        assertEquals(21, jdk21Result.origin().selectedVersion());
        assertTrue(jdk21Result.origin().entryName().startsWith("META-INF/versions/21/"));
    }

    @Test
    void fixtureValidatesAutomaticModuleAccessDiagnosticsFromGraphContext() {
        final JavaModuleGraphBuilder.ModuleGraph graph = new JavaModuleGraphBuilder().build(List.of());
        final JavaModuleAccessResolver.AccessContext sqlContext =
                JavaModuleAccessResolver.AccessContext.forRequesterModule("java.sql", graph);

        final JavaModuleAccessResolver baseResolver = new JavaModuleAccessResolver(
                new JavaSymbolTable(
                        List.of(Path.of(URI.create("jrt:/java.base"))),
                        "fixture-jrt-base",
                        21
                )
        );
        final JavaModuleAccessResolver unsupportedResolver = new JavaModuleAccessResolver(
                new JavaSymbolTable(
                        List.of(Path.of(URI.create("jrt:/jdk.unsupported"))),
                        "fixture-jrt-unsupported",
                        21
                )
        );

        assertEquals(
                JavaModuleAccessResolver.AccessStatus.ACCESSIBLE,
                baseResolver.resolveClass("java.lang.String", sqlContext).status()
        );
        assertEquals(
                JavaModuleAccessResolver.AccessStatus.CLASS_NOT_READABLE,
                unsupportedResolver.resolveClass("sun.misc.Unsafe", sqlContext).status()
        );
        assertEquals(
                JavaModuleAccessResolver.AccessStatus.CLASS_NOT_EXPORTED,
                baseResolver.resolveClass("jdk.internal.module.Modules", sqlContext).status()
        );
    }

    private static void compileClass(final String fqcn, final String sourceText, final Path classesRoot) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for JavaModuleConformanceFixture tests.");
        }
        final Path sourceRoot = classesRoot.getParent().resolve("src-" + fqcn.replace('.', '_'));
        final Path javaSource = sourceRoot.resolve(fqcn.replace('.', '/') + ".java");
        Files.createDirectories(javaSource.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(javaSource, sourceText, UTF_8);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(javaSource));
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-d",
                    classesRoot.toString()
            );
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    units
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile Java fixture source " + fqcn);
            }
        }
    }

    private static void packageAutomaticModuleJar(
            final String automaticModuleName,
            final Path classesDir,
            final Path jarPath
    ) throws IOException {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", automaticModuleName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            final Path classFile = classesDir.resolve("sample/tsj57b/Precedence.class");
            output.putNextEntry(new JarEntry("sample/tsj57b/Precedence.class"));
            output.write(Files.readAllBytes(classFile));
            output.closeEntry();
        }
    }

    private static void packageMultiReleaseJar(
            final String fqcn,
            final Path baseClassesRoot,
            final Map<Integer, Path> versionedClassesByVersion,
            final Path jarPath
    ) throws IOException {
        final String classEntry = fqcn.replace('.', '/') + ".class";
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            if (baseClassesRoot != null) {
                final Path baseClass = baseClassesRoot.resolve(classEntry);
                output.putNextEntry(new JarEntry(classEntry));
                output.write(Files.readAllBytes(baseClass));
                output.closeEntry();
            }
            for (Map.Entry<Integer, Path> versioned : versionedClassesByVersion.entrySet()) {
                final int version = versioned.getKey();
                final Path classFile = versioned.getValue().resolve(classEntry);
                final String entry = "META-INF/versions/" + version + "/" + classEntry;
                output.putNextEntry(new JarEntry(entry));
                output.write(Files.readAllBytes(classFile));
                output.closeEntry();
            }
        }
    }
}
