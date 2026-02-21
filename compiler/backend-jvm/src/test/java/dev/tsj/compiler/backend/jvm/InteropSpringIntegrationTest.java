package dev.tsj.compiler.backend.jvm;

import dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InteropSpringIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void generatedSpringConfigurationBridgeSupportsConstructorInjection() throws Exception {
        final String serviceClass = InteropSpringFixtureType.Service.class.getName();
        final Path specFile = tempDir.resolve("interop-spring.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#$new
                targets=%s#$new
                springConfiguration=true
                springBeanTargets=%s#$new
                """.formatted(serviceClass, serviceClass, serviceClass),
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out"));
        assertEquals(1, artifact.sourceFiles().size());
        final Path classesDir = tempDir.resolve("classes");
        compileSources(artifact.sourceFiles(), classesDir);

        final String bridgeSimpleName = artifact.sourceFiles().getFirst().getFileName().toString().replace(".java", "");
        final String bridgeClassName = "dev.tsj.generated.interop." + bridgeSimpleName;
        final URL[] urls = new URL[]{classesDir.toUri().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader())) {
            final Class<?> bridgeClass = Class.forName(bridgeClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(DependencyConfig.class);
                context.register(bridgeClass);
                context.refresh();
                final InteropSpringFixtureType.Service service = context.getBean(InteropSpringFixtureType.Service.class);
                assertEquals("alpha-7", service.describe());
            }
        }
    }

    @Test
    void generatedSpringBridgeExposesParameterizedSignatureMetadata() throws Exception {
        final String fixtureClass = InteropSpringFixtureType.class.getName();
        final Path specFile = tempDir.resolve("interop-spring-metadata.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#copyLabels
                targets=%s#copyLabels
                springConfiguration=true
                springBeanTargets=%s#copyLabels
                """.formatted(fixtureClass, fixtureClass, fixtureClass),
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out-metadata"));
        final Path classesDir = tempDir.resolve("classes-metadata");
        compileSources(artifact.sourceFiles(), classesDir);

        final String bridgeSimpleName = artifact.sourceFiles().getFirst().getFileName().toString().replace(".java", "");
        final String bridgeClassName = "dev.tsj.generated.interop." + bridgeSimpleName;
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            final Class<?> bridgeClass = Class.forName(bridgeClassName, true, classLoader);
            final Method method = bridgeClass.getMethod("copyLabels", List.class);

            assertTrue(method.getGenericReturnType() instanceof ParameterizedType);
            assertEquals("java.util.List<java.lang.String>", method.getGenericReturnType().getTypeName());
            assertEquals("java.util.List<java.lang.String>", method.getGenericParameterTypes()[0].getTypeName());
            assertEquals("arg0", method.getParameters()[0].getName());

            final String reportJson = renderMetadataReport(
                    method.getGenericReturnType().getTypeName(),
                    method.getGenericParameterTypes()[0].getTypeName(),
                    method.getParameters()[0].getName(),
                    true
            );
            final Path reportPath = tempDir.resolve("tsj39-metadata-conformance-report.json");
            Files.writeString(reportPath, reportJson, UTF_8);
            assertTrue(Files.exists(reportPath));

            final Path moduleReportPath = resolveModuleMetadataReportPath();
            Files.createDirectories(moduleReportPath.getParent());
            Files.writeString(moduleReportPath, reportJson, UTF_8);
            assertTrue(Files.exists(moduleReportPath));
        }
    }

    private static String renderMetadataReport(
            final String returnType,
            final String parameterType,
            final String parameterName,
            final boolean passed
    ) {
        return "{\"suite\":\"TSJ-39-metadata-conformance\",\"checks\":[{\"id\":\"bridge-generic-signature\","
                + "\"passed\":"
                + passed
                + ",\"returnType\":\""
                + escapeJson(returnType)
                + "\",\"parameterType\":\""
                + escapeJson(parameterType)
                + "\",\"parameterName\":\""
                + escapeJson(parameterName)
                + "\"}]}";
    }

    private static Path resolveModuleMetadataReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    InteropSpringIntegrationTest.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve("tsj39-metadata-conformance-report.json");
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target/tsj39-metadata-conformance-report.json");
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static void compileSources(final List<Path> sourceFiles, final Path classesDir) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
        }
        Files.createDirectories(classesDir);
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.isBlank()) {
                classPath = classesDir.toString();
            }
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-classpath",
                    classPath,
                    "-d",
                    classesDir.toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                fail("Bridge source compile failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    @Configuration
    public static class DependencyConfig {
        @Bean
        public InteropSpringFixtureType.DependencyA dependencyA() {
            return new InteropSpringFixtureType.DependencyA("alpha");
        }

        @Bean
        public InteropSpringFixtureType.DependencyB dependencyB() {
            return new InteropSpringFixtureType.DependencyB(7);
        }
    }
}
