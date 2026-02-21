package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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

class JavaSymbolTableTest {
    @TempDir
    Path tempDir;

    @Test
    void resolveClassParsesAtMostOncePerFingerprint() throws Exception {
        final Path classesDir = tempDir.resolve("classes-once");
        compileClass(
                "sample.tsj47.Once",
                """
                package sample.tsj47;

                public final class Once {
                    public static int value() {
                        return 1;
                    }
                }
                """,
                classesDir
        );

        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(classesDir), "fp-1");
        assertTrue(symbolTable.resolveClass("sample.tsj47.Once").isPresent());
        assertTrue(symbolTable.resolveClass("sample.tsj47.Once").isPresent());
        assertEquals(1, symbolTable.parsedCount("sample.tsj47.Once"));
        assertEquals(1, symbolTable.cacheSize());
    }

    @Test
    void updateClasspathInvalidatesDescriptorCacheWhenFingerprintChanges() throws Exception {
        final Path classesDir = tempDir.resolve("classes-invalidate");
        compileClass(
                "sample.tsj47.Invalidate",
                """
                package sample.tsj47;

                public final class Invalidate {
                    public static int value() {
                        return 1;
                    }
                }
                """,
                classesDir
        );

        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(classesDir), "fp-a");
        assertTrue(symbolTable.resolveClass("sample.tsj47.Invalidate").isPresent());
        assertEquals(1, symbolTable.parsedCount("sample.tsj47.Invalidate"));

        compileClass(
                "sample.tsj47.Invalidate",
                """
                package sample.tsj47;

                public final class Invalidate {
                    public static int value() {
                        return 2;
                    }
                }
                """,
                classesDir
        );

        assertTrue(symbolTable.resolveClass("sample.tsj47.Invalidate").isPresent());
        assertEquals(1, symbolTable.parsedCount("sample.tsj47.Invalidate"));

        symbolTable.updateClasspath(List.of(classesDir), "fp-b");
        assertTrue(symbolTable.resolveClass("sample.tsj47.Invalidate").isPresent());
        assertEquals(2, symbolTable.parsedCount("sample.tsj47.Invalidate"));
    }

    @Test
    void resolveClassSupportsJarEntries() throws Exception {
        final Path jarClasses = tempDir.resolve("jar-classes");
        compileClass(
                "sample.tsj47.FromJar",
                """
                package sample.tsj47;

                public final class FromJar {
                    public static String tag() {
                        return "jar";
                    }
                }
                """,
                jarClasses
        );
        final Path jarFile = tempDir.resolve("from-jar.jar");
        packageJar(jarClasses, jarFile);

        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(jarFile), "jar-fp");
        final JavaClassfileReader.RawClassInfo descriptor = symbolTable.resolveClass("sample.tsj47.FromJar").orElseThrow();
        assertEquals("sample/tsj47/FromJar", descriptor.internalName());
        assertEquals(1, symbolTable.parsedCount("sample.tsj47.FromJar"));
    }

    @Test
    void resolveClassReturnsEmptyForUnknownClass() {
        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(tempDir), "none");
        assertFalse(symbolTable.resolveClass("sample.tsj47.Missing").isPresent());
        assertEquals(0, symbolTable.parsedCount("sample.tsj47.Missing"));
    }

    @Test
    void resolveClassUsesHighestCompatibleMultiReleaseEntry() throws Exception {
        final String fqcn = "sample.tsj57.MultiRelease";
        final Path baseClasses = tempDir.resolve("mr-base");
        final Path version21Classes = tempDir.resolve("mr-21");
        compileClass(
                fqcn,
                """
                package sample.tsj57;

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
                package sample.tsj57;

                public final class MultiRelease {
                    public static int marker() {
                        return 21;
                    }
                }
                """,
                version21Classes
        );

        final Path jarFile = tempDir.resolve("mr.jar");
        packageMultiReleaseJar(fqcn, baseClasses, Map.of(21, version21Classes), jarFile);

        final JavaSymbolTable jdk17 = new JavaSymbolTable(List.of(jarFile), "mr-17", 17);
        final JavaSymbolTable.ClassResolution baseResolution =
                jdk17.resolveClassWithMetadata("sample.tsj57.MultiRelease");
        assertEquals(JavaSymbolTable.ResolutionStatus.FOUND, baseResolution.status());
        assertNotNull(baseResolution.origin());
        assertFalse(baseResolution.origin().versionedEntry());
        assertEquals("sample/tsj57/MultiRelease.class", baseResolution.origin().entryName());

        final JavaSymbolTable jdk21 = new JavaSymbolTable(List.of(jarFile), "mr-21", 21);
        final JavaSymbolTable.ClassResolution versionedResolution =
                jdk21.resolveClassWithMetadata("sample.tsj57.MultiRelease");
        assertEquals(JavaSymbolTable.ResolutionStatus.FOUND, versionedResolution.status());
        assertNotNull(versionedResolution.origin());
        assertTrue(versionedResolution.origin().versionedEntry());
        assertEquals(21, versionedResolution.origin().selectedVersion());
        assertEquals(
                "META-INF/versions/21/sample/tsj57/MultiRelease.class",
                versionedResolution.origin().entryName()
        );
    }

    @Test
    void resolveClassReportsTargetLevelMismatchForIncompatibleMultiReleaseOnlyClass() throws Exception {
        final String fqcn = "sample.tsj57.VersionOnly";
        final Path version21Classes = tempDir.resolve("mr-only-21");
        compileClass(
                fqcn,
                """
                package sample.tsj57;

                public final class VersionOnly {
                    public static int marker() {
                        return 21;
                    }
                }
                """,
                version21Classes
        );
        final Path jarFile = tempDir.resolve("mr-only.jar");
        packageMultiReleaseJar(fqcn, null, Map.of(21, version21Classes), jarFile);

        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(jarFile), "mr-only", 17);
        final JavaSymbolTable.ClassResolution resolution =
                symbolTable.resolveClassWithMetadata("sample.tsj57.VersionOnly");

        assertEquals(JavaSymbolTable.ResolutionStatus.TARGET_LEVEL_MISMATCH, resolution.status());
        assertTrue(resolution.classInfo().isEmpty());
        assertTrue(resolution.diagnostic().contains("target-level-mismatch"));
    }

    private static void compileClass(final String fqcn, final String sourceText, final Path classesRoot) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for JavaSymbolTable tests.");
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

    private static void packageJar(final Path classesRoot, final Path jarPath) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> paths = Files.walk(classesRoot)) {
                final List<Path> classFiles = paths
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .toList();
                for (Path classFile : classFiles) {
                    final String entryName = classesRoot
                            .relativize(classFile)
                            .toString()
                            .replace(java.io.File.separatorChar, '/');
                    output.putNextEntry(new JarEntry(entryName));
                    output.write(Files.readAllBytes(classFile));
                    output.closeEntry();
                }
            }
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
