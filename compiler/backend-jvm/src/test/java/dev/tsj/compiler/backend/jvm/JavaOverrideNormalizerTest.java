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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaOverrideNormalizerTest {
    @TempDir
    Path tempDir;

    @Test
    void bridgeMethodsAreFilteredWhenEquivalentNonBridgeExists() throws Exception {
        final Path classesDir = tempDir.resolve("bridge-filter");
        compileClass(
                "sample.tsj51.GenericBase",
                """
                package sample.tsj51;

                public abstract class GenericBase<T> {
                    public abstract T value();
                }
                """,
                classesDir
        );
        compileClass(
                "sample.tsj51.StringImpl",
                """
                package sample.tsj51;

                public final class StringImpl extends GenericBase<String> {
                    @Override
                    public String value() {
                        return "ok";
                    }
                }
                """,
                classesDir
        );

        final JavaOverrideNormalizer.NormalizationResult normalization = normalize(
                classesDir,
                "sample.tsj51.StringImpl",
                "value"
        );

        assertTrue(normalization.candidates().stream()
                .anyMatch(candidate -> (candidate.accessFlags() & 0x0040) != 0));
        assertEquals(1, normalization.normalizedMethods().size());
        final JavaOverrideNormalizer.NormalizedMethod normalized = normalization.normalizedMethods().get(0);
        assertEquals("()Ljava/lang/String;", normalized.descriptor());
        assertFalse(normalized.bridge());
        assertFalse(normalized.synthetic());
        assertTrue(normalized.overriddenDescriptors().contains("()Ljava/lang/Object;"));
    }

    @Test
    void covariantReturnOverridesAreRepresentedInNormalizedOutput() throws Exception {
        final Path classesDir = tempDir.resolve("covariant");
        compileClass(
                "sample.tsj51.Animal",
                """
                package sample.tsj51;

                public class Animal {}
                """,
                classesDir
        );
        compileClass(
                "sample.tsj51.Dog",
                """
                package sample.tsj51;

                public final class Dog extends Animal {}
                """,
                classesDir
        );
        compileClass(
                "sample.tsj51.Parent",
                """
                package sample.tsj51;

                public class Parent {
                    public Animal create() {
                        return new Animal();
                    }
                }
                """,
                classesDir
        );
        compileClass(
                "sample.tsj51.Child",
                """
                package sample.tsj51;

                public final class Child extends Parent {
                    @Override
                    public Dog create() {
                        return new Dog();
                    }
                }
                """,
                classesDir
        );

        final JavaOverrideNormalizer.NormalizationResult normalization = normalize(
                classesDir,
                "sample.tsj51.Child",
                "create"
        );

        assertEquals(1, normalization.normalizedMethods().size());
        final JavaOverrideNormalizer.NormalizedMethod normalized = normalization.normalizedMethods().get(0);
        assertEquals("()Lsample/tsj51/Dog;", normalized.descriptor());
        assertTrue(normalized.overriddenDescriptors().contains("()Lsample/tsj51/Animal;"));
    }

    @Test
    void overrideKeysUseErasedParameterDescriptors() throws Exception {
        final Path classesDir = tempDir.resolve("erasure-key");
        compileClass(
                "sample.tsj51.IMapper",
                """
                package sample.tsj51;

                public interface IMapper<T> {
                    T map(T input);
                }
                """,
                classesDir
        );
        compileClass(
                "sample.tsj51.MapperImpl",
                """
                package sample.tsj51;

                public final class MapperImpl implements IMapper<String> {
                    @Override
                    public String map(String input) {
                        return input;
                    }
                }
                """,
                classesDir
        );

        final JavaOverrideNormalizer.NormalizationResult normalization = normalize(
                classesDir,
                "sample.tsj51.MapperImpl",
                "map"
        );

        assertTrue(normalization.candidates().size() >= 2);
        assertEquals(1, normalization.normalizedMethods().size());
        final JavaOverrideNormalizer.NormalizedMethod normalized = normalization.normalizedMethods().get(0);
        assertEquals("map(Ljava/lang/Object;)", normalized.overrideKey());
    }

    private static JavaOverrideNormalizer.NormalizationResult normalize(
            final Path classesDir,
            final String targetClass,
            final String memberName
    ) {
        final JavaSymbolTable symbolTable = new JavaSymbolTable(List.of(classesDir), "tsj51", 21);
        final JavaInheritanceResolver resolver = new JavaInheritanceResolver(symbolTable);
        final JavaInheritanceResolver.MemberLookupResult members = resolver.collectMembers(
                targetClass,
                memberName,
                JavaInheritanceResolver.LookupContext.unrestricted(targetClass)
        );
        final JavaOverrideNormalizer normalizer = new JavaOverrideNormalizer();
        return normalizer.normalizeMethods(members.members());
    }

    private static void compileClass(final String fqcn, final String sourceText, final Path classesRoot) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for JavaOverrideNormalizer tests.");
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
                    "-classpath",
                    classesRoot.toString(),
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
