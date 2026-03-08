package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsDecoratorClasspathResolutionTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesImportedRuntimeAnnotationForClassFieldMethodAndParameter() throws Exception {
        final Path classesDir = compileSources(Map.of(
                "sample/anno/Trace.java",
                """
                package sample.anno;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
                public @interface Trace {}
                """
        ));

        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                entryFile,
                """
                import { Trace } from "java:sample.anno.Trace";

                @Trace
                class Demo {
                  @Trace
                  value: number = 1;

                  @Trace
                  run(@Trace input: string) {
                    return input;
                  }
                }
                """,
                UTF_8
        );

        final TsDecoratorModelExtractor extractor = new TsDecoratorModelExtractor(
                new TsDecoratorAnnotationMapping(),
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(classesDir), "tsj71-success"))
        );

        final TsDecoratorModel model = extractor.extract(entryFile);
        final TsDecoratedClass decoratedClass = model.classes().stream()
                .filter(candidate -> "Demo".equals(candidate.className()))
                .findFirst()
                .orElseThrow();

        assertTrue(decoratedClass.decorators().stream().anyMatch(value -> "Trace".equals(value.name())));
        assertTrue(decoratedClass.fields().stream()
                .flatMap(value -> value.decorators().stream())
                .anyMatch(value -> "Trace".equals(value.name())));
        final TsDecoratedMethod method = decoratedClass.methods().stream()
                .filter(value -> "run".equals(value.methodName()))
                .findFirst()
                .orElseThrow();
        assertTrue(method.decorators().stream().anyMatch(value -> "Trace".equals(value.name())));
        assertTrue(method.parameters().stream()
                .flatMap(value -> value.decorators().stream())
                .anyMatch(value -> "Trace".equals(value.name())));
    }

    @Test
    void resolvesImportedRuntimeAnnotationFromImportTypeStatement() throws Exception {
        final Path classesDir = compileSources(Map.of(
                "sample/anno/Trace.java",
                """
                package sample.anno;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
                public @interface Trace {}
                """
        ));

        final Path entryFile = tempDir.resolve("import-type.ts");
        Files.writeString(
                entryFile,
                """
                import type { Trace } from "java:sample.anno.Trace";

                @Trace
                class Demo {
                  @Trace
                  value: number = 1;

                  @Trace
                  run(@Trace input: string) {
                    return input;
                  }
                }
                """,
                UTF_8
        );

        final TsDecoratorModelExtractor extractor = new TsDecoratorModelExtractor(
                new TsDecoratorAnnotationMapping(),
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(classesDir), "tsj71-import-type"))
        );

        final TsDecoratorModel model = extractor.extract(entryFile);
        final TsDecoratedClass decoratedClass = model.classes().stream()
                .filter(candidate -> "Demo".equals(candidate.className()))
                .findFirst()
                .orElseThrow();

        assertTrue(decoratedClass.decorators().stream().anyMatch(value -> "Trace".equals(value.name())));
        assertTrue(decoratedClass.fields().stream()
                .flatMap(value -> value.decorators().stream())
                .anyMatch(value -> "Trace".equals(value.name())));
        final TsDecoratedMethod method = decoratedClass.methods().stream()
                .filter(value -> "run".equals(value.methodName()))
                .findFirst()
                .orElseThrow();
        assertTrue(method.decorators().stream().anyMatch(value -> "Trace".equals(value.name())));
        assertTrue(method.parameters().stream()
                .flatMap(value -> value.decorators().stream())
                .anyMatch(value -> "Trace".equals(value.name())));
    }

    @Test
    void rejectsImportedDecoratorWhenAnnotationTypeCannotBeResolved() throws Exception {
        final Path entryFile = tempDir.resolve("missing.ts");
        Files.writeString(
                entryFile,
                """
                import { Missing } from "java:sample.anno.Missing";

                @Missing
                class Demo {}
                """,
                UTF_8
        );

        final TsDecoratorModelExtractor extractor = new TsDecoratorModelExtractor(
                new TsDecoratorAnnotationMapping(),
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(), "tsj71-missing"))
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> extractor.extract(entryFile)
        );

        assertEquals("TSJ-DECORATOR-RESOLUTION", exception.code());
        assertEquals("TSJ71-DECORATOR-CLASSPATH", exception.featureId());
        assertTrue(exception.getMessage().contains("sample.anno.Missing"));
    }

    @Test
    void rejectsImportedDecoratorWhenResolvedTypeIsNotAnnotation() throws Exception {
        final Path classesDir = compileSources(Map.of(
                "sample/anno/NotAnnotation.java",
                """
                package sample.anno;

                public final class NotAnnotation {}
                """
        ));
        final Path entryFile = tempDir.resolve("non-annotation.ts");
        Files.writeString(
                entryFile,
                """
                import { NotAnnotation as Marker } from "java:sample.anno.NotAnnotation";

                @Marker
                class Demo {}
                """,
                UTF_8
        );

        final TsDecoratorModelExtractor extractor = new TsDecoratorModelExtractor(
                new TsDecoratorAnnotationMapping(),
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(classesDir), "tsj71-non-annotation"))
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> extractor.extract(entryFile)
        );

        assertEquals("TSJ-DECORATOR-TYPE", exception.code());
        assertEquals("TSJ71-DECORATOR-CLASSPATH", exception.featureId());
        assertTrue(exception.getMessage().contains("NotAnnotation"));
    }

    @Test
    void rejectsImportedDecoratorWhenTargetMetaDoesNotAllowUsage() throws Exception {
        final Path classesDir = compileSources(Map.of(
                "sample/anno/MethodOnly.java",
                """
                package sample.anno;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface MethodOnly {}
                """
        ));
        final Path entryFile = tempDir.resolve("target-mismatch.ts");
        Files.writeString(
                entryFile,
                """
                import { MethodOnly } from "java:sample.anno.MethodOnly";

                @MethodOnly
                class Demo {}
                """,
                UTF_8
        );

        final TsDecoratorModelExtractor extractor = new TsDecoratorModelExtractor(
                new TsDecoratorAnnotationMapping(),
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(classesDir), "tsj71-target"))
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> extractor.extract(entryFile)
        );

        assertEquals("TSJ-DECORATOR-TARGET", exception.code());
        assertEquals("TSJ71-DECORATOR-CLASSPATH", exception.featureId());
        assertTrue(exception.getMessage().contains("does not allow target class"));
    }

    @Test
    void rejectsImportedDecoratorWhenRetentionIsNotRuntime() throws Exception {
        final Path classesDir = compileSources(Map.of(
                "sample/anno/ClassRetention.java",
                """
                package sample.anno;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.CLASS)
                @Target(ElementType.TYPE)
                public @interface ClassRetention {}
                """
        ));
        final Path entryFile = tempDir.resolve("retention.ts");
        Files.writeString(
                entryFile,
                """
                import { ClassRetention } from "java:sample.anno.ClassRetention";

                @ClassRetention
                class Demo {}
                """,
                UTF_8
        );

        final TsDecoratorModelExtractor extractor = new TsDecoratorModelExtractor(
                new TsDecoratorAnnotationMapping(),
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(classesDir), "tsj71-retention"))
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> extractor.extract(entryFile)
        );

        assertEquals("TSJ-DECORATOR-RETENTION", exception.code());
        assertEquals("TSJ71-DECORATOR-CLASSPATH", exception.featureId());
        assertTrue(exception.getMessage().contains("non-runtime retention"));
    }

    private Path compileSources(final Map<String, String> relativeSources) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TsDecoratorClasspathResolutionTest.");
        }
        final Path sourceRoot = tempDir.resolve("src-" + System.nanoTime());
        final Path classesRoot = tempDir.resolve("classes-" + System.nanoTime());
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classesRoot);

        final List<Path> sourceFiles = new java.util.ArrayList<>();
        for (Map.Entry<String, String> source : relativeSources.entrySet()) {
            final Path sourcePath = sourceRoot.resolve(source.getKey());
            Files.createDirectories(sourcePath.getParent());
            Files.writeString(sourcePath, source.getValue(), UTF_8);
            sourceFiles.add(sourcePath);
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
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
                throw new IllegalStateException("Failed to compile Java fixture sources for decorator resolution test.");
            }
        }
        return classesRoot;
    }
}
