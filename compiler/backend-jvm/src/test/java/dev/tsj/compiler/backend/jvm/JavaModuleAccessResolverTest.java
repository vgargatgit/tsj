package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaModuleAccessResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolveClassReportsClassNotFound() {
        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(tempDir), "none", 21);
        final JavaModuleAccessResolver resolver = new JavaModuleAccessResolver(symbolTable);

        final JavaModuleAccessResolver.AccessResolution resolution = resolver.resolveClass(
                "sample.tsj57.Missing",
                JavaModuleAccessResolver.AccessContext.unrestricted()
        );

        assertEquals(JavaModuleAccessResolver.AccessStatus.CLASS_NOT_FOUND, resolution.status());
        assertEquals("class-not-found", resolution.detail());
    }

    @Test
    void resolveClassReportsTargetLevelMismatch() throws Exception {
        final String fqcn = "sample.tsj57.VersionOnly";
        final Path version21Classes = tempDir.resolve("version-only-21");
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
        final Path jarFile = tempDir.resolve("version-only.jar");
        packageMultiReleaseJar(fqcn, null, Map.of(21, version21Classes), jarFile);

        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(jarFile), "version-only", 17);
        final JavaModuleAccessResolver resolver = new JavaModuleAccessResolver(symbolTable);
        final JavaModuleAccessResolver.AccessResolution resolution = resolver.resolveClass(
                fqcn,
                JavaModuleAccessResolver.AccessContext.unrestricted()
        );

        assertEquals(JavaModuleAccessResolver.AccessStatus.TARGET_LEVEL_MISMATCH, resolution.status());
        assertTrue(resolution.detail().contains("target-level-mismatch"));
    }

    @Test
    void resolveClassReportsUnreadableAndNotExportedClasses() throws Exception {
        final String fqcn = "sample.tsj57.moda.PublicApi";
        final Path classesDir = tempDir.resolve("module-a-classes");
        compileClass(
                fqcn,
                """
                package sample.tsj57.moda;

                public final class PublicApi {
                    public static int marker() {
                        return 1;
                    }
                }
                """,
                classesDir
        );

        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(classesDir), "module-a", 21);
        final JavaModuleAccessResolver resolver = new JavaModuleAccessResolver(symbolTable);
        final String internalName = "sample/tsj57/moda/PublicApi";

        final JavaModuleAccessResolver.AccessContext unreadableContext = new JavaModuleAccessResolver.AccessContext(
                "module.b",
                Map.of(internalName, "module.a"),
                Map.of(),
                Map.of("module.a", Set.of("sample/tsj57/moda")),
                Map.of()
        );
        final JavaModuleAccessResolver.AccessResolution unreadable = resolver.resolveClass(
                fqcn,
                unreadableContext
        );
        assertEquals(JavaModuleAccessResolver.AccessStatus.CLASS_NOT_READABLE, unreadable.status());

        final JavaModuleAccessResolver.AccessContext notExportedContext = new JavaModuleAccessResolver.AccessContext(
                "module.b",
                Map.of(internalName, "module.a"),
                Map.of("module.b", Set.of("module.a")),
                Map.of("module.a", Set.of("sample/tsj57/internal")),
                Map.of()
        );
        final JavaModuleAccessResolver.AccessResolution notExported = resolver.resolveClass(
                fqcn,
                notExportedContext
        );
        assertEquals(JavaModuleAccessResolver.AccessStatus.CLASS_NOT_EXPORTED, notExported.status());
    }

    @Test
    void resolveClassReportsAccessibleAndSelectedOriginMetadata() throws Exception {
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

        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(jarFile), "mr", 21);
        final JavaModuleAccessResolver resolver = new JavaModuleAccessResolver(symbolTable);
        final JavaModuleAccessResolver.AccessContext context = new JavaModuleAccessResolver.AccessContext(
                "module.b",
                Map.of("sample/tsj57/MultiRelease", "module.a"),
                Map.of("module.b", Set.of("module.a")),
                Map.of("module.a", Set.of("sample/tsj57")),
                Map.of()
        );

        final JavaModuleAccessResolver.AccessResolution resolution = resolver.resolveClass(fqcn, context);

        assertEquals(JavaModuleAccessResolver.AccessStatus.ACCESSIBLE, resolution.status());
        assertNotNull(resolution.selectedOrigin());
        assertEquals(21, resolution.selectedOrigin().selectedVersion());
        assertTrue(resolution.selectedOrigin().entryName().startsWith("META-INF/versions/21/"));
    }

    private static void compileClass(final String fqcn, final String sourceText, final Path classesRoot) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for JavaModuleAccessResolver tests.");
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
