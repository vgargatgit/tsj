package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaClassfileReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsClassHeadersMembersSignaturesAndExceptions() throws Exception {
        final Path classesDir = compileSources(Map.of(
                "sample/tsj46/GenericBox.java",
                """
                package sample.tsj46;

                import java.io.IOException;
                import java.io.Serializable;
                import java.util.List;

                @Deprecated
                public class GenericBox<T extends Number> implements Serializable {
                    public List<String> names;

                    public GenericBox() {
                    }

                    @Deprecated
                    public <R extends CharSequence> R map(final R value) throws IOException {
                        return value;
                    }
                }
                """
        ));

        final JavaClassfileReader.RawClassInfo info = new JavaClassfileReader()
                .read(classFile(classesDir, "sample/tsj46/GenericBox"));

        assertEquals("sample/tsj46/GenericBox", info.internalName());
        assertEquals("java/lang/Object", info.superInternalName());
        assertTrue(info.interfaces().contains("java/io/Serializable"));
        assertNotNull(info.signature());

        final JavaClassfileReader.RawFieldInfo field = info.fields().stream()
                .filter(candidate -> "names".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("Ljava/util/List;", field.descriptor());
        assertNotNull(field.signature());

        final JavaClassfileReader.RawMethodInfo method = info.methods().stream()
                .filter(candidate -> "map".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;", method.descriptor());
        assertNotNull(method.signature());
        assertTrue(method.exceptions().contains("java/io/IOException"));
        assertFalse(method.methodParameters().isEmpty());
    }

    @Test
    void readsRuntimeAnnotationsTypeAnnotationsAndAnnotationDefault() throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put(
                "sample/tsj46/Mark.java",
                """
                package sample.tsj46;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
                public @interface Mark {
                    String value() default "tag";
                }
                """
        );
        sources.put(
                "sample/tsj46/Annotated.java",
                """
                package sample.tsj46;

                import java.util.List;

                @Mark("class")
                public final class Annotated {
                    @Mark("field")
                    public List<@Mark("type-use") String> values;

                    @Mark("method")
                    public String run(@Mark("param") final String value) {
                        return value;
                    }
                }
                """
        );
        final Path classesDir = compileSources(sources);
        final JavaClassfileReader reader = new JavaClassfileReader();

        final JavaClassfileReader.RawClassInfo annotationInfo = reader.read(classFile(classesDir, "sample/tsj46/Mark"));
        final JavaClassfileReader.RawMethodInfo valueMethod = annotationInfo.methods().stream()
                .filter(candidate -> "value".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("tag", valueMethod.annotationDefault());

        final JavaClassfileReader.RawClassInfo annotatedInfo = reader.read(classFile(classesDir, "sample/tsj46/Annotated"));
        assertTrue(
                annotatedInfo.runtimeVisibleAnnotations().stream()
                        .anyMatch(annotation -> "Lsample/tsj46/Mark;".equals(annotation.descriptor()))
        );

        final JavaClassfileReader.RawFieldInfo field = annotatedInfo.fields().stream()
                .filter(candidate -> "values".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertFalse(field.runtimeVisibleTypeAnnotations().isEmpty());

        final JavaClassfileReader.RawMethodInfo runMethod = annotatedInfo.methods().stream()
                .filter(candidate -> "run".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertFalse(runMethod.runtimeVisibleParameterAnnotations().isEmpty());
        assertTrue(
                runMethod.runtimeVisibleParameterAnnotations().get(0).stream()
                        .anyMatch(annotation -> "Lsample/tsj46/Mark;".equals(annotation.descriptor()))
        );
    }

    @Test
    void readsInnerEnclosingNestRecordAndPermittedSubclassAttributes() throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put(
                "sample/tsj46/Outer.java",
                """
                package sample.tsj46;

                public class Outer {
                    public static class Nested {
                    }

                    public Runnable anonymous() {
                        return new Runnable() {
                            @Override
                            public void run() {
                            }
                        };
                    }
                }
                """
        );
        sources.put(
                "sample/tsj46/Point.java",
                """
                package sample.tsj46;

                public record Point(int x, int y) {
                }
                """
        );
        sources.put(
                "sample/tsj46/Base.java",
                """
                package sample.tsj46;

                public sealed class Base permits Child {
                }
                """
        );
        sources.put(
                "sample/tsj46/Child.java",
                """
                package sample.tsj46;

                public final class Child extends Base {
                }
                """
        );
        final Path classesDir = compileSources(sources);
        final JavaClassfileReader reader = new JavaClassfileReader();

        final JavaClassfileReader.RawClassInfo outerInfo = reader.read(classFile(classesDir, "sample/tsj46/Outer"));
        assertTrue(
                outerInfo.nestMembers().stream().anyMatch(member -> member.endsWith("Outer$Nested"))
        );
        assertFalse(outerInfo.innerClasses().isEmpty());

        final JavaClassfileReader.RawClassInfo nestedInfo = reader.read(classFile(classesDir, "sample/tsj46/Outer$Nested"));
        assertEquals("sample/tsj46/Outer", nestedInfo.nestHost());

        final JavaClassfileReader.RawClassInfo anonymousInfo = reader.read(classFile(classesDir, "sample/tsj46/Outer$1"));
        assertNotNull(anonymousInfo.enclosingMethod());
        assertEquals("anonymous", anonymousInfo.enclosingMethod().methodName());

        final JavaClassfileReader.RawClassInfo pointInfo = reader.read(classFile(classesDir, "sample/tsj46/Point"));
        assertFalse(pointInfo.recordComponents().isEmpty());

        final JavaClassfileReader.RawClassInfo baseInfo = reader.read(classFile(classesDir, "sample/tsj46/Base"));
        assertTrue(baseInfo.permittedSubclasses().contains("sample/tsj46/Child"));
    }

    @Test
    void parserOutputMatchesJavapVerboseForCoreFields() throws Exception {
        final Path classesDir = compileSources(Map.of(
                "sample/tsj46/JavapFixture.java",
                """
                package sample.tsj46;

                public class JavapFixture<T> {
                    public T id(final T value) {
                        return value;
                    }
                }
                """
        ));

        final Path classFile = classFile(classesDir, "sample/tsj46/JavapFixture");
        final JavaClassfileReader.RawClassInfo info = new JavaClassfileReader().read(classFile);
        final String javapOutput = runJavapVerbose(classesDir, "sample.tsj46.JavapFixture");

        assertTrue(javapOutput.contains("major version: " + info.majorVersion()));
        assertTrue(javapOutput.contains("minor version: " + info.minorVersion()));
        assertTrue(javapOutput.contains(info.internalName().replace('/', '.')));
        assertTrue(javapOutput.contains("Signature"));

        final JavaClassfileReader.RawMethodInfo idMethod = info.methods().stream()
                .filter(candidate -> "id".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(javapOutput.contains(idMethod.descriptor()));
    }

    private Path compileSources(final Map<String, String> relativeSources) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for JavaClassfileReader tests.");
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
                throw new IllegalStateException("Failed to compile Java fixture sources.");
            }
        }
        return classesRoot;
    }

    private static Path classFile(final Path classesRoot, final String internalName) {
        return classesRoot.resolve(internalName + ".class");
    }

    private static String runJavapVerbose(final Path classesRoot, final String fqcn)
            throws IOException, InterruptedException {
        final Path javap = resolveToolLauncher("javap");
        final ProcessBuilder processBuilder = new ProcessBuilder(
                javap.toString(),
                "-classpath",
                classesRoot.toAbsolutePath().normalize().toString(),
                "-v",
                fqcn
        );
        processBuilder.redirectErrorStream(true);
        final Process process = processBuilder.start();
        final String output;
        try (java.io.InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), UTF_8);
        }
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("javap -v failed for " + fqcn + " with exit code " + exitCode);
        }
        return output;
    }

    private static Path resolveToolLauncher(final String toolName) {
        final String executable = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? toolName + ".exe"
                : toolName;
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }
}
