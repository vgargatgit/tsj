package dev.tsj.compiler.backend.jvm;

import dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.RichMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-75 any-jar annotation-survival certification harness.
 */
final class TsjAnyJarAnnotationSurvivalCertificationHarness {
    private static final String REPORT_FILE = "tsj75-anyjar-annotation-survival-certification.json";
    private static final String FIXTURE_VERSION = "tsj75-fixtures-2026.03";
    private static final String BACKEND_ADDITIONAL_CLASSPATH_PROPERTY = "tsj.backend.additionalClasspath";
    private static final String DIMENSION_RESOLUTION = "annotation-resolution";
    private static final String DIMENSION_EMISSION = "annotation-emission";
    private static final String DIMENSION_REFLECTION_CONSUMER = "reflection-consumer-parity";

    TsjAnyJarAnnotationSurvivalCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj75-anyjar-annotation-survival-work");
        Files.createDirectories(workRoot);

        final TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult resolution =
                runResolutionDimension(workRoot.resolve("resolution"));
        final TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult emission =
                runEmissionDimension(workRoot.resolve("emission"));
        final TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult reflectionConsumer =
                runReflectionConsumerDimension(workRoot.resolve("reflection-consumer"));

        final List<TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult> dimensions = List.of(
                resolution,
                emission,
                reflectionConsumer
        );
        final boolean gatePassed = dimensions.stream()
                .allMatch(TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult::passed);
        final TsjAnyJarAnnotationSurvivalCertificationReport report = new TsjAnyJarAnnotationSurvivalCertificationReport(
                gatePassed,
                FIXTURE_VERSION,
                dimensions,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult runResolutionDimension(final Path workDir) {
        final String expected =
                "resolves imported runtime decorators and emits stable TSJ-DECORATOR-RESOLUTION for unresolved imports";
        try {
            Files.createDirectories(workDir);
            final Path classesDir = compileSources(
                    workDir.resolve("annotation-classes"),
                    Map.of(
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
                    ),
                    false
            );

            final Path validSource = workDir.resolve("resolution-valid.ts");
            Files.writeString(
                    validSource,
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
                    TsDecoratorAnnotationMapping.empty(),
                    new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(classesDir), "tsj75-resolution"))
            );
            final TsDecoratorModel model = extractor.extract(validSource);
            final boolean resolved = model.classes().stream()
                    .anyMatch(clazz -> "Demo".equals(clazz.className())
                            && clazz.decorators().stream().anyMatch(decorator -> "Trace".equals(decorator.name()))
                            && clazz.fields().stream()
                            .flatMap(field -> field.decorators().stream())
                            .anyMatch(decorator -> "Trace".equals(decorator.name()))
                            && clazz.methods().stream()
                            .flatMap(method -> method.parameters().stream())
                            .flatMap(parameter -> parameter.decorators().stream())
                            .anyMatch(decorator -> "Trace".equals(decorator.name())));

            final Path missingSource = workDir.resolve("resolution-missing.ts");
            Files.writeString(
                    missingSource,
                    """
                    import { Missing } from "java:sample.anno.Missing";

                    @Missing
                    class MissingDemo {}
                    """,
                    UTF_8
            );

            String diagnosticCode = "NO_DIAGNOSTIC";
            try {
                extractor.extract(missingSource);
            } catch (final JvmCompilationException exception) {
                diagnosticCode = exception.code();
            }

            final boolean passed = resolved
                    && TsDecoratorClasspathResolver.RESOLUTION_CODE.equals(diagnosticCode);
            final String observed = "resolved=" + resolved + ",missingCode=" + diagnosticCode;
            final String notes = "featureId=" + TsDecoratorClasspathResolver.FEATURE_ID;
            return new TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult(
                    DIMENSION_RESOLUTION,
                    passed,
                    expected,
                    observed,
                    diagnosticCode,
                    notes
            );
        } catch (final Throwable throwable) {
            return failedDimension(DIMENSION_RESOLUTION, expected, throwable);
        }
    }

    private TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult runEmissionDimension(final Path workDir) {
        final String expected =
                "emits runtime-visible imported annotations for class/field/ctor/method/parameter with attribute values";
        try {
            Files.createDirectories(workDir);
            final Path sourceFile = workDir.resolve("emission.ts");
            Files.writeString(
                    sourceFile,
                    """
                    import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                    import { FieldMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark";
                    import { CtorMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark";
                    import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                    import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";
                    import { RichMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.RichMark";

                    @TypeMark
                    @RichMark('class-value')
                    class AnnotatedCarrierTarget {
                      @FieldMark
                      @RichMark({ name: 'field-name', count: 2 })
                      value: number = 1;

                      @CtorMark
                      constructor(@ParamMark @RichMark('ctor-param') name: string) {
                        this.value = 2;
                      }

                      @MethodMark
                      @RichMark({ tags: ['method', 'value'] })
                      greet(@ParamMark @RichMark({ name: 'param-name', count: 9 }) prefix: string) {
                        return prefix + this.value;
                      }
                    }
                    """,
                    UTF_8
            );

            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, workDir.resolve("out"));
            try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
                final Class<?> carrierClassType = Class.forName(
                        "dev.tsj.generated.metadata.AnnotatedCarrierTargetTsjCarrier",
                        true,
                        classLoader
                );
                final boolean classTypeMark = carrierClassType.isAnnotationPresent(TypeMark.class);
                final boolean fieldMark =
                        carrierClassType.getDeclaredField("value").isAnnotationPresent(FieldMark.class);
                final java.lang.reflect.Constructor<?> constructor =
                        carrierClassType.getDeclaredConstructor(Object.class);
                final boolean ctorMark = constructor.isAnnotationPresent(CtorMark.class);
                final boolean ctorParamMark = constructor.getParameters()[0].isAnnotationPresent(ParamMark.class);
                final java.lang.reflect.Method method = carrierClassType.getDeclaredMethod("greet", Object.class);
                final boolean methodMark = method.isAnnotationPresent(MethodMark.class);
                final boolean methodParamMark = method.getParameters()[0].isAnnotationPresent(ParamMark.class);
                final RichMark classRichMark = carrierClassType.getAnnotation(RichMark.class);
                final RichMark fieldRichMark = carrierClassType.getDeclaredField("value").getAnnotation(RichMark.class);
                final RichMark methodParamRichMark = method.getParameters()[0].getAnnotation(RichMark.class);
                final boolean richValues = classRichMark != null
                        && "class-value".equals(classRichMark.value())
                        && fieldRichMark != null
                        && "field-name".equals(fieldRichMark.name())
                        && fieldRichMark.count() == 2
                        && methodParamRichMark != null
                        && "param-name".equals(methodParamRichMark.name())
                        && methodParamRichMark.count() == 9;

                final boolean passed = classTypeMark
                        && fieldMark
                        && ctorMark
                        && ctorParamMark
                        && methodMark
                        && methodParamMark
                        && richValues;
                final String observed = "class="
                        + classTypeMark
                        + ",field="
                        + fieldMark
                        + ",ctor="
                        + ctorMark
                        + ",ctorParam="
                        + ctorParamMark
                        + ",method="
                        + methodMark
                        + ",methodParam="
                        + methodParamMark
                        + ",richValues="
                        + richValues;
                return new TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult(
                        DIMENSION_EMISSION,
                        passed,
                        expected,
                        observed,
                        "",
                        "carrierClass=dev.tsj.generated.metadata.AnnotatedCarrierTargetTsjCarrier"
                );
            }
        } catch (final Throwable throwable) {
            return failedDimension(DIMENSION_EMISSION, expected, throwable);
        }
    }

    private TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult runReflectionConsumerDimension(
            final Path workDir
    ) {
        final String expected =
                "generic external DI+metadata reflection consumers read TSJ carrier annotations with deterministic output";
        final String expectedOutput = """
                component=true
                injectFields=1
                route=/orders
                ctorNamedParams=1
                namedParams=1
                """;
        try {
            Files.createDirectories(workDir);
            final Path fixtureJar = buildReflectionFixtureJar(workDir.resolve("fixture-jar"));
            final Path sourceFile = workDir.resolve("generic-reflection-consumers.ts");
            Files.writeString(
                    sourceFile,
                    """
                    import { Component } from "java:sample.reflect.Component";
                    import { Inject } from "java:sample.reflect.Inject";
                    import { Route } from "java:sample.reflect.Route";
                    import { Named } from "java:sample.reflect.Named";
                    import { carrierClass } from "java:sample.reflect.CarrierLocator";
                    import { hasComponent, countInjectFields } from "java:sample.reflect.DiConsumer";
                    import {
                      routePath,
                      countNamedParameters,
                      countNamedConstructorParameters
                    } from "java:sample.reflect.MetadataConsumer";

                    @Component
                    class Repo {
                    }

                    @Component
                    @Route("/orders")
                    class Controller {
                      @Inject
                      repo: Repo | undefined;

                      constructor(@Named("repoCtor") repo: Repo) {
                        this.repo = repo;
                      }

                      find(@Named("id") id: string) {
                        return id;
                      }
                    }

                    const carrier = carrierClass("dev.tsj.generated.metadata.ControllerTsjCarrier");
                    console.log("component=" + hasComponent(carrier));
                    console.log("injectFields=" + countInjectFields(carrier));
                    console.log("route=" + routePath(carrier));
                    console.log("ctorNamedParams=" + countNamedConstructorParameters(carrier));
                    console.log("namedParams=" + countNamedParameters(carrier, "find"));
                    """,
                    UTF_8
            );

            final String previousAdditionalClasspath = System.getProperty(BACKEND_ADDITIONAL_CLASSPATH_PROPERTY);
            final JvmCompiledArtifact artifact;
            if (previousAdditionalClasspath == null || previousAdditionalClasspath.isBlank()) {
                System.setProperty(BACKEND_ADDITIONAL_CLASSPATH_PROPERTY, fixtureJar.toString());
            } else {
                System.setProperty(
                        BACKEND_ADDITIONAL_CLASSPATH_PROPERTY,
                        previousAdditionalClasspath + java.io.File.pathSeparator + fixtureJar
                );
            }
            try {
                artifact = new JvmBytecodeCompiler().compile(sourceFile, workDir.resolve("out"));
            } finally {
                if (previousAdditionalClasspath == null || previousAdditionalClasspath.isBlank()) {
                    System.clearProperty(BACKEND_ADDITIONAL_CLASSPATH_PROPERTY);
                } else {
                    System.setProperty(BACKEND_ADDITIONAL_CLASSPATH_PROPERTY, previousAdditionalClasspath);
                }
            }

            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            new JvmBytecodeRunner().run(artifact, List.of(fixtureJar), new PrintStream(stdout), System.err);
            final String observedOutput = normalizeLineEndings(stdout.toString(UTF_8));
            final boolean passed = expectedOutput.equals(observedOutput);
            return new TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult(
                    DIMENSION_REFLECTION_CONSUMER,
                    passed,
                    trim(expectedOutput, 120),
                    trim(observedOutput, 120),
                    "",
                    "fixtureJar=" + fixtureJar.getFileName()
            );
        } catch (final Throwable throwable) {
            return failedDimension(DIMENSION_REFLECTION_CONSUMER, expected, throwable);
        }
    }

    private static TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult failedDimension(
            final String dimension,
            final String expected,
            final Throwable throwable
    ) {
        final String diagnosticCode = extractDiagnosticCode(throwable);
        final String observed = "failure=" + renderFailure(throwable);
        final String notes = "message=" + trim(Objects.toString(throwable.getMessage(), ""), 160);
        return new TsjAnyJarAnnotationSurvivalCertificationReport.DimensionResult(
                dimension,
                false,
                expected,
                observed,
                diagnosticCode,
                notes
        );
    }

    private static String extractDiagnosticCode(final Throwable throwable) {
        if (throwable instanceof JvmCompilationException compilationException) {
            return compilationException.code();
        }
        return "UNEXPECTED_EXCEPTION";
    }

    private static String renderFailure(final Throwable throwable) {
        return throwable.getClass().getSimpleName() + ":" + Objects.toString(throwable.getMessage(), "");
    }

    private static String normalizeLineEndings(final String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String trim(final String text, final int maxChars) {
        final String safe = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private Path buildReflectionFixtureJar(final Path workDir) throws Exception {
        final Map<String, String> sources = Map.of(
                "sample/reflect/Component.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Component {}
                """,
                "sample/reflect/Inject.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.FIELD)
                public @interface Inject {}
                """,
                "sample/reflect/Route.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Route {
                    String value();
                }
                """,
                "sample/reflect/Named.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.PARAMETER)
                public @interface Named {
                    String value();
                }
                """,
                "sample/reflect/CarrierLocator.java",
                """
                package sample.reflect;

                public final class CarrierLocator {
                    private CarrierLocator() {}

                    public static Class<?> carrierClass(final String className) {
                        try {
                            final ClassLoader context = Thread.currentThread().getContextClassLoader();
                            return Class.forName(className, true, context);
                        } catch (final ClassNotFoundException exception) {
                            throw new IllegalStateException("Carrier class not found: " + className, exception);
                        }
                    }
                }
                """,
                "sample/reflect/DiConsumer.java",
                """
                package sample.reflect;

                import java.lang.reflect.Field;

                public final class DiConsumer {
                    private DiConsumer() {}

                    public static boolean hasComponent(final Class<?> type) {
                        return type != null && type.isAnnotationPresent(Component.class);
                    }

                    public static int countInjectFields(final Class<?> type) {
                        if (type == null) {
                            return 0;
                        }
                        int count = 0;
                        Class<?> current = type;
                        while (current != null && current != Object.class) {
                            for (Field field : current.getDeclaredFields()) {
                                if (field.isAnnotationPresent(Inject.class)) {
                                    count++;
                                }
                            }
                            current = current.getSuperclass();
                        }
                        return count;
                    }
                }
                """,
                "sample/reflect/MetadataConsumer.java",
                """
                package sample.reflect;

                import java.lang.reflect.Method;
                import java.lang.reflect.Parameter;

                public final class MetadataConsumer {
                    private MetadataConsumer() {}

                    public static String routePath(final Class<?> type) {
                        if (type == null) {
                            return "none";
                        }
                        final Route route = type.getAnnotation(Route.class);
                        return route == null ? "none" : route.value();
                    }

                    public static int countNamedParameters(final Class<?> type, final String methodName) {
                        if (type == null || methodName == null) {
                            return 0;
                        }
                        for (Method method : type.getDeclaredMethods()) {
                            if (!method.getName().equals(methodName)) {
                                continue;
                            }
                            int count = 0;
                            for (Parameter parameter : method.getParameters()) {
                                if (parameter.isAnnotationPresent(Named.class)) {
                                    count++;
                                }
                            }
                            return count;
                        }
                        return 0;
                    }

                    public static int countNamedConstructorParameters(final Class<?> type) {
                        if (type == null) {
                            return 0;
                        }
                        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
                            int count = 0;
                            for (Parameter parameter : constructor.getParameters()) {
                                if (parameter.isAnnotationPresent(Named.class)) {
                                    count++;
                                }
                            }
                            return count;
                        }
                        return 0;
                    }
                }
                """
        );
        final Path classesRoot = compileSources(workDir, sources, true);
        final Path jarPath = workDir.resolve("fixture-reflect-consumers.jar");
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> files = Files.walk(classesRoot)) {
                for (Path classFile : files.filter(Files::isRegularFile).toList()) {
                    final String entryName = classesRoot.relativize(classFile).toString().replace('\\', '/');
                    outputStream.putNextEntry(new JarEntry(entryName));
                    outputStream.write(Files.readAllBytes(classFile));
                    outputStream.closeEntry();
                }
            }
        }
        return jarPath;
    }

    private Path compileSources(
            final Path workDir,
            final Map<String, String> sources,
            final boolean includeParameters
    ) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-75 certification harness.");
        }

        final Path sourceRoot = workDir.resolve("src");
        final Path classesRoot = workDir.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classesRoot);

        final List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            final Path sourcePath = sourceRoot.resolve(source.getKey());
            Files.createDirectories(sourcePath.getParent());
            Files.writeString(sourcePath, source.getValue(), UTF_8);
            sourceFiles.add(sourcePath);
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final List<String> options = new ArrayList<>();
            options.add("--release");
            options.add("21");
            if (includeParameters) {
                options.add("-parameters");
            }
            options.add("-d");
            options.add(classesRoot.toString());
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    units
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile fixture sources for TSJ-75 certification.");
            }
        }
        return classesRoot;
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjAnyJarAnnotationSurvivalCertificationHarness.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve(REPORT_FILE);
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target", REPORT_FILE).toAbsolutePath().normalize();
    }

    private static void writeReport(
            final Path reportPath,
            final TsjAnyJarAnnotationSurvivalCertificationReport report
    ) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }
}
