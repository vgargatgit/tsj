package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSymbolTablePersistentCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAndReusesDescriptorCacheAcrossSessions() throws Exception {
        final Path classesDir = tempDir.resolve("cache-classes");
        compileClass(
                "sample.tsj56.CacheOne",
                """
                package sample.tsj56;

                public final class CacheOne {
                    public static int value() {
                        return 1;
                    }
                }
                """,
                classesDir
        );
        final Path cacheFile = tempDir.resolve("descriptor-cache.bin");

        final JavaSymbolTable cold = new JavaSymbolTable(
                List.of(classesDir),
                "fp-cache",
                21,
                new JavaClassfileReader(),
                cacheFile,
                "tsj-test",
                "1"
        );
        assertTrue(cold.resolveClass("sample.tsj56.CacheOne").isPresent());
        assertEquals(1, cold.parsedCount("sample.tsj56.CacheOne"));

        final JavaSymbolTable warm = new JavaSymbolTable(
                List.of(classesDir),
                "fp-cache",
                21,
                new JavaClassfileReader(),
                cacheFile,
                "tsj-test",
                "1"
        );
        assertTrue(warm.resolveClass("sample.tsj56.CacheOne").isPresent());
        assertEquals(0, warm.parsedCount("sample.tsj56.CacheOne"));
        assertTrue(warm.cacheStats().hits() >= 1);
        assertEquals(0, warm.cacheStats().misses());
    }

    @Test
    void invalidatesCacheWhenSchemaChangesAndEmitsDiagnostic() throws Exception {
        final Path classesDir = tempDir.resolve("cache-schema-classes");
        compileClass(
                "sample.tsj56.Schema",
                """
                package sample.tsj56;

                public final class Schema {
                    public static int value() {
                        return 1;
                    }
                }
                """,
                classesDir
        );
        final Path cacheFile = tempDir.resolve("descriptor-cache-schema.bin");
        final JavaSymbolTable seed = new JavaSymbolTable(
                List.of(classesDir),
                "fp-schema",
                21,
                new JavaClassfileReader(),
                cacheFile,
                "tsj-test",
                "1"
        );
        seed.resolveClass("sample.tsj56.Schema");

        final JavaSymbolTable stale = new JavaSymbolTable(
                List.of(classesDir),
                "fp-schema",
                21,
                new JavaClassfileReader(),
                cacheFile,
                "tsj-test",
                "2"
        );
        assertTrue(stale.cacheDiagnostics().stream().anyMatch(message -> message.contains("invalidated")));
        assertTrue(stale.resolveClass("sample.tsj56.Schema").isPresent());
        assertEquals(1, stale.cacheStats().invalidations());
        assertEquals(1, stale.cacheStats().misses());
    }

    @Test
    void treatsCorruptedCacheAsSafeMissWithWarning() throws Exception {
        final Path classesDir = tempDir.resolve("cache-corrupt-classes");
        compileClass(
                "sample.tsj56.Corrupt",
                """
                package sample.tsj56;

                public final class Corrupt {
                    public static int value() {
                        return 1;
                    }
                }
                """,
                classesDir
        );
        final Path cacheFile = tempDir.resolve("descriptor-cache-corrupt.bin");
        Files.writeString(cacheFile, "not-a-valid-cache", UTF_8);

        final JavaSymbolTable symbolTable = new JavaSymbolTable(
                List.of(classesDir),
                "fp-corrupt",
                21,
                new JavaClassfileReader(),
                cacheFile,
                "tsj-test",
                "1"
        );

        assertTrue(symbolTable.cacheDiagnostics().stream().anyMatch(message -> message.contains("could not be loaded")));
        assertTrue(symbolTable.resolveClass("sample.tsj56.Corrupt").isPresent());
        assertEquals(1, symbolTable.cacheStats().invalidations());
        assertEquals(1, symbolTable.cacheStats().misses());
    }

    @Test
    void warmBuildAchievesHighReuseRatioForUnchangedClasspath() throws Exception {
        final Path classesDir = tempDir.resolve("cache-ratio-classes");
        final List<String> classNames = List.of(
                "sample.tsj56.RatioOne",
                "sample.tsj56.RatioTwo",
                "sample.tsj56.RatioThree",
                "sample.tsj56.RatioFour",
                "sample.tsj56.RatioFive",
                "sample.tsj56.RatioSix",
                "sample.tsj56.RatioSeven",
                "sample.tsj56.RatioEight",
                "sample.tsj56.RatioNine",
                "sample.tsj56.RatioTen"
        );
        for (String className : classNames) {
            compileClass(
                    className,
                    """
                    package sample.tsj56;

                    public final class %s {
                        public static int value() {
                            return 1;
                        }
                    }
                    """.formatted(className.substring(className.lastIndexOf('.') + 1)),
                    classesDir
            );
        }
        final Path cacheFile = tempDir.resolve("descriptor-cache-ratio.bin");

        final JavaSymbolTable cold = new JavaSymbolTable(
                List.of(classesDir),
                "fp-ratio",
                21,
                new JavaClassfileReader(),
                cacheFile,
                "tsj-test",
                "1"
        );
        for (String className : classNames) {
            assertTrue(cold.resolveClass(className).isPresent());
        }

        final JavaSymbolTable warm = new JavaSymbolTable(
                List.of(classesDir),
                "fp-ratio",
                21,
                new JavaClassfileReader(),
                cacheFile,
                "tsj-test",
                "1"
        );
        for (String className : classNames) {
            assertTrue(warm.resolveClass(className).isPresent());
        }

        final long hits = warm.cacheStats().hits();
        final long misses = warm.cacheStats().misses();
        final double reuseRatio = hits / (double) (hits + misses);
        assertTrue(reuseRatio >= 0.9d, "Expected reuse ratio >= 0.9 but was " + reuseRatio);
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
}
