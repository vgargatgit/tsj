package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TsjGenericReflectionConsumerParityTest {
    private static final String BACKEND_ADDITIONAL_CLASSPATH_PROPERTY = "tsj.backend.additionalClasspath";

    @TempDir
    Path tempDir;

    @Test
    void supportsGenericDiAndMetadataReflectionConsumersFromExternalJarAgainstTsCarrierClasses() throws Exception {
        final Path fixtureJar = buildFixtureJar();
        final Path sourceFile = tempDir.resolve("generic-reflection-consumers.ts");
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
            artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out"));
        } finally {
            if (previousAdditionalClasspath == null || previousAdditionalClasspath.isBlank()) {
                System.clearProperty(BACKEND_ADDITIONAL_CLASSPATH_PROPERTY);
            } else {
                System.setProperty(BACKEND_ADDITIONAL_CLASSPATH_PROPERTY, previousAdditionalClasspath);
            }
        }

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, List.of(fixtureJar), new PrintStream(stdout), System.err);

        assertEquals(
                """
                component=true
                injectFields=1
                route=/orders
                ctorNamedParams=1
                namedParams=1
                """,
                stdout.toString(UTF_8)
        );
    }

    private Path buildFixtureJar() throws Exception {
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

        final Path sourceRoot = tempDir.resolve("fixture-src");
        final Path classesRoot = tempDir.resolve("fixture-classes");
        final Path jarPath = tempDir.resolve("fixture-reflect-consumers.jar");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classesRoot);

        final List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            final Path sourcePath = sourceRoot.resolve(source.getKey());
            Files.createDirectories(sourcePath.getParent());
            Files.writeString(sourcePath, source.getValue(), UTF_8);
            sourceFiles.add(sourcePath);
        }

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
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
                throw new IllegalStateException("Failed to compile fixture sources for reflection-consumer jar.");
            }
        }

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
}
