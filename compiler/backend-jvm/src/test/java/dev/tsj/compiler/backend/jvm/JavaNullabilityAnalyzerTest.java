package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaNullabilityAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void recognizesSupportedNullabilityAnnotationFamilies() throws Exception {
        final Path classesDir = tempDir.resolve("annotation-families");
        compileCommonNullabilityAnnotations(classesDir);
        compileClass(
                "sample.tsj49.Annotated",
                """
                package sample.tsj49;

                public class Annotated {
                    @org.jetbrains.annotations.NotNull
                    public String jetbrainsNotNull;

                    @androidx.annotation.Nullable
                    public String androidxNullable;

                    @javax.annotation.Nonnull
                    public String jsrNonnull;

                    @org.checkerframework.checker.nullness.qual.Nullable
                    public String checkerNullable;

                    @org.jetbrains.annotations.Nullable
                    public String nullableReturn() {
                        return null;
                    }

                    public String platformReturn(String value) {
                        return value;
                    }

                    public void params(
                            @androidx.annotation.NonNull String nonNullParam,
                            @javax.annotation.Nullable String nullableParam
                    ) {
                    }
                }
                """,
                classesDir
        );

        final JavaNullabilityAnalyzer analyzer = new JavaNullabilityAnalyzer();
        final JavaNullabilityAnalyzer.AnalysisResult result = analyzer.analyze(readClassInfos(classesDir));
        final JavaNullabilityAnalyzer.ClassNullability annotated = result.classesByInternalName()
                .get("sample/tsj49/Annotated");

        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NON_NULL,
                annotated.fieldsByKey().get("jetbrainsNotNull:Ljava/lang/String;")
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NULLABLE,
                annotated.fieldsByKey().get("androidxNullable:Ljava/lang/String;")
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NON_NULL,
                annotated.fieldsByKey().get("jsrNonnull:Ljava/lang/String;")
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NULLABLE,
                annotated.fieldsByKey().get("checkerNullable:Ljava/lang/String;")
        );

        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NULLABLE,
                annotated.methodsByKey().get("nullableReturn()Ljava/lang/String;").returnNullability()
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.PLATFORM,
                annotated.methodsByKey().get("platformReturn(Ljava/lang/String;)Ljava/lang/String;").returnNullability()
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NON_NULL,
                annotated.methodsByKey().get("params(Ljava/lang/String;Ljava/lang/String;)V")
                        .parameterNullability().get(0)
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NULLABLE,
                annotated.methodsByKey().get("params(Ljava/lang/String;Ljava/lang/String;)V")
                        .parameterNullability().get(1)
        );
    }

    @Test
    void appliesPackageAndClassDefaultNullabilityWithOverrides() throws Exception {
        final Path classesDir = tempDir.resolve("defaults");
        compileCommonNullabilityAnnotations(classesDir);
        compileClass(
                "sample.tsj49defaults.package-info",
                """
                @javax.annotation.ParametersAreNonnullByDefault
                package sample.tsj49defaults;
                """,
                classesDir
        );
        compileClass(
                "sample.tsj49defaults.Defaulted",
                """
                package sample.tsj49defaults;

                public class Defaulted {
                    public String value(
                            String input,
                            @org.jetbrains.annotations.Nullable String maybe
                    ) {
                        return input;
                    }

                    public String fieldDefault;
                }
                """,
                classesDir
        );
        compileClass(
                "sample.tsj49defaults.NullUnmarkedClass",
                """
                package sample.tsj49defaults;

                @org.jspecify.annotations.NullUnmarked
                public class NullUnmarkedClass {
                    public String maybe;
                }
                """,
                classesDir
        );

        final JavaNullabilityAnalyzer analyzer = new JavaNullabilityAnalyzer();
        final JavaNullabilityAnalyzer.AnalysisResult result = analyzer.analyze(readClassInfos(classesDir));

        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NON_NULL,
                result.packageDefaults().get("sample/tsj49defaults")
        );

        final JavaNullabilityAnalyzer.ClassNullability defaulted =
                result.classesByInternalName().get("sample/tsj49defaults/Defaulted");
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NON_NULL,
                defaulted.fieldsByKey().get("fieldDefault:Ljava/lang/String;")
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NON_NULL,
                defaulted.methodsByKey().get("value(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                        .parameterNullability().get(0)
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NULLABLE,
                defaulted.methodsByKey().get("value(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                        .parameterNullability().get(1)
        );

        final JavaNullabilityAnalyzer.ClassNullability nullUnmarked =
                result.classesByInternalName().get("sample/tsj49defaults/NullUnmarkedClass");
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NULLABLE,
                nullUnmarked.defaultNullability()
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.NULLABLE,
                nullUnmarked.fieldsByKey().get("maybe:Ljava/lang/String;")
        );
    }

    @Test
    void fallsBackToPlatformWhenNoNullabilitySignalsExist() throws Exception {
        final Path classesDir = tempDir.resolve("platform-default");
        compileClass(
                "sample.tsj49.Plain",
                """
                package sample.tsj49;

                public class Plain {
                    public String field;
                    public String method(String input) {
                        return input;
                    }
                }
                """,
                classesDir
        );

        final JavaNullabilityAnalyzer analyzer = new JavaNullabilityAnalyzer();
        final JavaNullabilityAnalyzer.AnalysisResult result = analyzer.analyze(readClassInfos(classesDir));
        final JavaNullabilityAnalyzer.ClassNullability plain = result.classesByInternalName().get("sample/tsj49/Plain");
        assertEquals(JavaNullabilityAnalyzer.NullabilityState.PLATFORM, plain.defaultNullability());
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.PLATFORM,
                plain.fieldsByKey().get("field:Ljava/lang/String;")
        );
        assertEquals(
                JavaNullabilityAnalyzer.NullabilityState.PLATFORM,
                plain.methodsByKey().get("method(Ljava/lang/String;)Ljava/lang/String;").returnNullability()
        );
    }

    private static List<JavaClassfileReader.RawClassInfo> readClassInfos(final Path classesDir) throws Exception {
        final JavaClassfileReader reader = new JavaClassfileReader();
        final List<JavaClassfileReader.RawClassInfo> infos = new ArrayList<>();
        try (var paths = Files.walk(classesDir)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                infos.add(reader.read(classFile));
            }
        }
        return infos;
    }

    private static void compileCommonNullabilityAnnotations(final Path classesDir) throws Exception {
        compileClass(
                "org.jetbrains.annotations.NotNull",
                annotationSource("org.jetbrains.annotations", "NotNull"),
                classesDir
        );
        compileClass(
                "org.jetbrains.annotations.Nullable",
                annotationSource("org.jetbrains.annotations", "Nullable"),
                classesDir
        );
        compileClass(
                "javax.annotation.Nonnull",
                annotationSource("javax.annotation", "Nonnull"),
                classesDir
        );
        compileClass(
                "javax.annotation.Nullable",
                annotationSource("javax.annotation", "Nullable"),
                classesDir
        );
        compileClass(
                "javax.annotation.ParametersAreNonnullByDefault",
                annotationSource("javax.annotation", "ParametersAreNonnullByDefault"),
                classesDir
        );
        compileClass(
                "androidx.annotation.NonNull",
                annotationSource("androidx.annotation", "NonNull"),
                classesDir
        );
        compileClass(
                "androidx.annotation.Nullable",
                annotationSource("androidx.annotation", "Nullable"),
                classesDir
        );
        compileClass(
                "org.checkerframework.checker.nullness.qual.NonNull",
                annotationSource("org.checkerframework.checker.nullness.qual", "NonNull"),
                classesDir
        );
        compileClass(
                "org.checkerframework.checker.nullness.qual.Nullable",
                annotationSource("org.checkerframework.checker.nullness.qual", "Nullable"),
                classesDir
        );
        compileClass(
                "org.jspecify.annotations.NullUnmarked",
                annotationSource("org.jspecify.annotations", "NullUnmarked"),
                classesDir
        );
    }

    private static String annotationSource(final String packageName, final String simpleName) {
        return "package " + packageName + ";\n"
                + "\n"
                + "import java.lang.annotation.ElementType;\n"
                + "import java.lang.annotation.Retention;\n"
                + "import java.lang.annotation.RetentionPolicy;\n"
                + "import java.lang.annotation.Target;\n"
                + "\n"
                + "@Retention(RetentionPolicy.RUNTIME)\n"
                + "@Target({ElementType.TYPE_USE, ElementType.TYPE, ElementType.PACKAGE,"
                + " ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})\n"
                + "public @interface " + simpleName + " {}\n";
    }

    private static void compileClass(final String fqcn, final String sourceText, final Path classesRoot) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for JavaNullabilityAnalyzer tests.");
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
