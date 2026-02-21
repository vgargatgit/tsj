package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaModuleGraphBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsGraphForSystemModulesWithReadableAndExportedPackages() {
        final JavaModuleGraphBuilder builder = new JavaModuleGraphBuilder();
        final JavaModuleGraphBuilder.ModuleGraph graph = builder.build(List.of());

        assertTrue(graph.exportedPackagesByModule().containsKey("java.base"));
        assertTrue(graph.exportedPackagesByModule().get("java.base").contains("java/lang"));
        assertTrue(graph.readableModulesByModule().get("java.sql").contains("java.base"));
        assertEquals("java.base", graph.packageToModule().get("java/lang"));
    }

    @Test
    void buildsGraphForAutomaticModulesFromJarManifest() throws Exception {
        final Path classesDir = tempDir.resolve("auto-module-classes");
        compileClass(
                "demo.auto.Api",
                """
                package demo.auto;

                public final class Api {
                    public static int marker() {
                        return 1;
                    }
                }
                """,
                classesDir
        );
        final Path jarPath = tempDir.resolve("auto-module.jar");
        packageAutomaticModuleJar("demo.auto", classesDir, jarPath);

        final JavaModuleGraphBuilder builder = new JavaModuleGraphBuilder();
        final JavaModuleGraphBuilder.ModuleGraph graph = builder.build(List.of(jarPath));

        assertTrue(graph.exportedPackagesByModule().containsKey("demo.auto"));
        assertTrue(graph.exportedPackagesByModule().get("demo.auto").contains("demo/auto"));
        assertTrue(graph.readableModulesByModule().get("demo.auto").contains("java.base"));
        assertEquals("demo.auto", graph.packageToModule().get("demo/auto"));
    }

    @Test
    void moduleGraphContextEnablesAutomaticJrtAccessChecks() {
        final JavaModuleGraphBuilder.ModuleGraph graph = new JavaModuleGraphBuilder().build(List.of());
        final JavaModuleAccessResolver.AccessContext sqlContext =
                JavaModuleAccessResolver.AccessContext.forRequesterModule("java.sql", graph);

        final JavaSymbolTable baseSymbolTable = new JavaSymbolTable(
                List.of(Path.of(URI.create("jrt:/java.base"))),
                "jrt-base",
                21
        );
        final JavaModuleAccessResolver resolver = new JavaModuleAccessResolver(baseSymbolTable);
        final JavaModuleAccessResolver.AccessResolution stringAccess =
                resolver.resolveClass("java.lang.String", sqlContext);
        assertEquals(JavaModuleAccessResolver.AccessStatus.ACCESSIBLE, stringAccess.status());

        final JavaSymbolTable unsupportedSymbolTable = new JavaSymbolTable(
                List.of(Path.of(URI.create("jrt:/jdk.unsupported"))),
                "jrt-unsupported",
                21
        );
        final JavaModuleAccessResolver unsupportedResolver = new JavaModuleAccessResolver(unsupportedSymbolTable);
        final JavaModuleAccessResolver.AccessResolution unsafeAccess =
                unsupportedResolver.resolveClass("sun.misc.Unsafe", sqlContext);
        assertEquals(JavaModuleAccessResolver.AccessStatus.CLASS_NOT_READABLE, unsafeAccess.status());

        final JavaModuleAccessResolver.AccessResolution internalBaseAccess =
                resolver.resolveClass("jdk.internal.module.Modules", sqlContext);
        assertEquals(JavaModuleAccessResolver.AccessStatus.CLASS_NOT_EXPORTED, internalBaseAccess.status());
    }

    private static void compileClass(final String fqcn, final String sourceText, final Path classesRoot) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for JavaModuleGraphBuilder tests.");
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
            final Path classFile = classesDir.resolve("demo/auto/Api.class");
            output.putNextEntry(new JarEntry("demo/auto/Api.class"));
            output.write(Files.readAllBytes(classFile));
            output.closeEntry();
        }
    }
}
