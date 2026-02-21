package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TsjSpringPackagedWebConformanceTest {
    @TempDir
    Path tempDir;

    @Test
    void springPackageIncludesCompiledTsWebControllerAdaptersForPackagedRuntime() throws Exception {
        final Path supportJar = buildPackagedWebSupportJar();
        final Path scenarioOutput = tempDir.resolve("tsj34f-scenarios.txt");
        final Path entryFile = writeWebEntryFixture();
        final Path outDir = tempDir.resolve("tsj34f-package-out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--jar",
                        supportJar.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode, "stderr:\n" + stderr.toString(UTF_8));
        assertEquals("", stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-SPRING-PACKAGE-SUCCESS\""));

        final Path packagedJar = outDir.resolve("tsj-spring-app.jar");
        assertTrue(Files.exists(packagedJar));
        try (JarFile jarFile = new JarFile(packagedJar.toFile())) {
            assertTrue(
                    jarFile.getJarEntry("dev/tsj/generated/web/EchoControllerTsjController.class") != null,
                    "Expected generated TS web adapter class in packaged jar."
            );
        }
    }

    @Test
    void packagedWebConformanceGateProducesTsJavaKotlinReport() throws Exception {
        final Path supportJar = buildPackagedWebSupportJar();
        final Path scenarioOutput = tempDir.resolve("tsj34f-scenario-output.txt");
        final Path entryFile = writeWebEntryFixture();
        final Path outDir = tempDir.resolve("tsj34f-http-out");

        final ByteArrayOutputStream packageStdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream packageStderr = new ByteArrayOutputStream();
        final int packageExitCode = TsjCli.execute(
                new String[]{
                        "spring-package",
                        entryFile.toString(),
                        "--out",
                        outDir.toString(),
                        "--jar",
                        supportJar.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(packageStdout),
                new PrintStream(packageStderr)
        );

        assertEquals(0, packageExitCode, "stderr:\n" + packageStderr.toString(UTF_8));
        assertEquals("", packageStderr.toString(UTF_8));
        final Path packagedJar = outDir.resolve("tsj-spring-app.jar");
        assertTrue(Files.exists(packagedJar));

        final ProcessResult run = runJarAndCaptureOutput(packagedJar);
        assertEquals(0, run.exitCode(), run.output());
        assertTrue(run.output().contains("tsj34f-boot"), run.output());

        runPackagedHarnessScenarios(packagedJar, scenarioOutput);
        assertTrue(Files.exists(scenarioOutput));

        final Map<String, HttpResult> tsjResults = readScenarioResults(scenarioOutput);
        final List<ScenarioResult> scenarios = new ArrayList<>();
        scenarios.add(runConformanceScenario(tsjResults, "echo-matrix", "matrix"));
        scenarios.add(runConformanceScenario(tsjResults, "echo-kotlin", "kotlin"));

        for (ScenarioResult scenario : scenarios) {
            assertTrue(scenario.match(), "Scenario failed: " + scenario.name() + " -> " + scenario.tsj());
        }

        final Path reportPath = Path.of("target/tsj34f-packaged-web-conformance-report.json")
                .toAbsolutePath()
                .normalize();
        final String reportJson = renderConformanceReportJson(scenarios);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, reportJson, UTF_8);

        assertTrue(Files.exists(reportPath));
        final String persisted = Files.readString(reportPath, UTF_8);
        assertTrue(persisted.contains("\"suite\":\"TSJ-34f-packaged-web-conformance\""));
        assertTrue(persisted.contains("\"match\":true"));
    }

    @Test
    void springPackageStartupDiagnosticsSeparateCompileBridgePackageAndRuntimeStages() throws Exception {
        final Path compileFailureEntry = tempDir.resolve("tsj34f-compile-failure.ts");
        Files.writeString(compileFailureEntry, "const = ;\n", UTF_8);
        final StageResult compileFailure = runSpringPackage(
                new String[]{
                        "spring-package",
                        compileFailureEntry.toString(),
                        "--out",
                        tempDir.resolve("compile-fail").toString()
                }
        );
        assertEquals(1, compileFailure.exitCode());
        assertTrue(compileFailure.stderr().contains("\"stage\":\"compile\""));

        final Path bridgeFailureEntry = tempDir.resolve("tsj34f-bridge-failure.ts");
        Files.writeString(
                bridgeFailureEntry,
                "import { ping } from \"java:sample.missing.Bridge\";\nconsole.log(ping());\n",
                UTF_8
        );
        final Path bridgeSpec = tempDir.resolve("tsj34f-bridge-failure.properties");
        Files.writeString(bridgeSpec, "allowlist=sample.missing.Bridge#ping\n", UTF_8);
        final StageResult bridgeFailure = runSpringPackage(
                new String[]{
                        "spring-package",
                        bridgeFailureEntry.toString(),
                        "--out",
                        tempDir.resolve("bridge-fail").toString(),
                        "--interop-spec",
                        bridgeSpec.toString()
                }
        );
        assertEquals(1, bridgeFailure.exitCode());
        assertTrue(bridgeFailure.stderr().contains("\"stage\":\"bridge\""));

        final Path packageFailureEntry = tempDir.resolve("tsj34f-package-failure.ts");
        Files.writeString(packageFailureEntry, "console.log('package');\n", UTF_8);
        final StageResult packageFailure = runSpringPackage(
                new String[]{
                        "spring-package",
                        packageFailureEntry.toString(),
                        "--out",
                        tempDir.resolve("package-fail").toString(),
                        "--resource-dir",
                        tempDir.resolve("missing-resource-dir").toString()
                }
        );
        assertEquals(1, packageFailure.exitCode());
        assertTrue(packageFailure.stderr().contains("\"stage\":\"package\""));

        final Path runtimeFailureEntry = tempDir.resolve("tsj34f-runtime-failure.ts");
        Files.writeString(
                runtimeFailureEntry,
                """
                function fail() {
                  throw "runtime-failure";
                }
                fail();
                """,
                UTF_8
        );
        final StageResult runtimeFailure = runSpringPackage(
                new String[]{
                        "spring-package",
                        runtimeFailureEntry.toString(),
                        "--out",
                        tempDir.resolve("runtime-fail").toString(),
                        "--smoke-run"
                }
        );
        assertEquals(1, runtimeFailure.exitCode());
        assertTrue(runtimeFailure.stderr().contains("\"stage\":\"runtime\""));
        assertTrue(runtimeFailure.stderr().contains("\"code\":\"TSJ-SPRING-BOOT\""));
    }

    private ScenarioResult runConformanceScenario(
            final Map<String, HttpResult> tsjResults,
            final String scenarioName,
            final String value
    ) {
        final HttpResult tsj = tsjResults.get(scenarioName);
        if (tsj == null) {
            fail("Missing TSJ scenario output for " + scenarioName + " in " + tsjResults.keySet());
        }
        final HttpResult javaReference = new HttpResult(200, JavaReferenceController.echo(value));
        final HttpResult kotlinReference = new HttpResult(200, KotlinReferenceController.echo(value));
        final boolean match = tsj.statusCode() == javaReference.statusCode()
                && tsj.statusCode() == kotlinReference.statusCode()
                && tsj.body().equals(javaReference.body())
                && tsj.body().equals(kotlinReference.body());
        return new ScenarioResult(scenarioName, tsj, javaReference, kotlinReference, match);
    }

    private static Map<String, HttpResult> readScenarioResults(final Path scenarioOutput) throws Exception {
        final Map<String, HttpResult> results = new LinkedHashMap<>();
        for (String line : Files.readAllLines(scenarioOutput, UTF_8)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            final String[] parts = line.split("\\|", 3);
            if (parts.length != 3) {
                fail("Unexpected scenario output row: " + line);
            }
            final String name = parts[0];
            final int status = Integer.parseInt(parts[1]);
            final byte[] decodedBody = Base64.getDecoder().decode(parts[2]);
            final String body = new String(decodedBody, UTF_8);
            results.put(name, new HttpResult(status, body));
        }
        return Map.copyOf(results);
    }

    private static String renderConformanceReportJson(final List<ScenarioResult> scenarios) {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-34f-packaged-web-conformance\",\"scenarios\":[");
        for (int index = 0; index < scenarios.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            final ScenarioResult scenario = scenarios.get(index);
            builder.append("{\"name\":\"")
                    .append(escapeJson(scenario.name()))
                    .append("\",\"match\":")
                    .append(scenario.match())
                    .append(",\"tsj\":{\"status\":")
                    .append(scenario.tsj().statusCode())
                    .append(",\"body\":\"")
                    .append(escapeJson(scenario.tsj().body()))
                    .append("\"},\"java\":{\"status\":")
                    .append(scenario.javaReference().statusCode())
                    .append(",\"body\":\"")
                    .append(escapeJson(scenario.javaReference().body()))
                    .append("\"},\"kotlin\":{\"status\":")
                    .append(scenario.kotlinReference().statusCode())
                    .append(",\"body\":\"")
                    .append(escapeJson(scenario.kotlinReference().body()))
                    .append("\"}}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static ProcessResult runJarAndCaptureOutput(final Path jarPath) throws Exception {
        final ProcessBuilder processBuilder = new ProcessBuilder(
                resolveJavaLauncher().toString(),
                "-jar",
                jarPath.toAbsolutePath().normalize().toString()
        );
        processBuilder.redirectErrorStream(true);
        final Process process = processBuilder.start();
        final String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), UTF_8);
        }
        final int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private void runPackagedHarnessScenarios(
            final Path packagedJar,
            final Path scenarioOutput
    ) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{packagedJar.toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> harnessClass = Class.forName("sample.web.PackagedWebHarness", true, classLoader);
            final Method runScenarios = harnessClass.getMethod("runScenarios", String.class, String.class);
            try {
                runScenarios.invoke(
                        null,
                        "dev.tsj.generated.web.EchoControllerTsjController",
                        scenarioOutput.toAbsolutePath().normalize().toString()
                );
            } catch (final InvocationTargetException invocationTargetException) {
                final Throwable target = invocationTargetException.getTargetException();
                if (target instanceof Exception exception) {
                    throw exception;
                }
                throw new RuntimeException(target);
            }
        }
    }

    private Path writeWebEntryFixture() throws Exception {
        final Path entry = tempDir.resolve("tsj34f-web-main.ts");
        Files.writeString(
                entry,
                """
                @RestController
                @RequestMapping("/api")
                class EchoController {
                  @GetMapping("/echo")
                  echo(@RequestParam("value") value: string) {
                    return "echo:" + value;
                  }
                }
                console.log("tsj34f-boot");
                """,
                UTF_8
        );
        return entry;
    }

    private StageResult runSpringPackage(final String[] args) throws Exception {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(args, new PrintStream(stdout), new PrintStream(stderr));
        return new StageResult(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8));
    }

    private Path buildPackagedWebSupportJar() throws Exception {
        final Path sourceRoot = tempDir.resolve("tsj34f-support-src");
        final Path classesDir = tempDir.resolve("tsj34f-support-classes");
        final Path jarPath = tempDir.resolve("tsj34f-support/tsj34f-support.jar");
        Files.createDirectories(sourceRoot);

        writeJavaSource(
                sourceRoot,
                "org/springframework/web/bind/annotation/RestController.java",
                """
                package org.springframework.web.bind.annotation;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE})
                public @interface RestController {
                }
                """
        );
        writeJavaSource(
                sourceRoot,
                "org/springframework/web/bind/annotation/RequestMapping.java",
                """
                package org.springframework.web.bind.annotation;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE, ElementType.METHOD})
                public @interface RequestMapping {
                    String value() default "";
                }
                """
        );
        writeJavaSource(
                sourceRoot,
                "org/springframework/web/bind/annotation/GetMapping.java",
                """
                package org.springframework.web.bind.annotation;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.METHOD})
                public @interface GetMapping {
                    String value() default "";
                }
                """
        );
        writeJavaSource(
                sourceRoot,
                "org/springframework/web/bind/annotation/RequestParam.java",
                """
                package org.springframework.web.bind.annotation;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.PARAMETER})
                public @interface RequestParam {
                    String value() default "";
                }
                """
        );
        writeJavaSource(
                sourceRoot,
                "sample/web/PackagedWebHarness.java",
                """
                package sample.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                import java.lang.reflect.InvocationTargetException;
                import java.lang.reflect.Method;
                import java.lang.reflect.Parameter;
                import java.nio.charset.StandardCharsets;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.ArrayList;
                import java.util.Base64;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;

                public final class PackagedWebHarness {
                    private PackagedWebHarness() {
                    }

                    public static void runScenarios(final String controllerClassName, final String outputPath)
                            throws Exception {
                        final Class<?> controllerClass = Class.forName(controllerClassName);
                        if (controllerClass.getAnnotation(RestController.class) == null) {
                            throw new IllegalStateException("TSJ-WEB-BOOT runtime wiring failed: missing @RestController");
                        }
                        final Object controller = controllerClass.getDeclaredConstructor().newInstance();
                        final String basePath = resolveBasePath(controllerClass);
                        final List<Route> routes = resolveRoutes(controllerClass, basePath);
                        if (routes.isEmpty()) {
                            throw new IllegalStateException("TSJ-WEB-BOOT runtime wiring failed: no handlers");
                        }

                        final String echoPath = normalizePath(basePath, "/echo");
                        final List<Scenario> scenarios = List.of(
                                new Scenario("echo-matrix", "GET", echoPath, Map.of("value", "matrix")),
                                new Scenario("echo-kotlin", "GET", echoPath, Map.of("value", "kotlin"))
                        );
                        final StringBuilder builder = new StringBuilder();
                        for (Scenario scenario : scenarios) {
                            final DispatchResult result = dispatch(controller, routes, scenario);
                            builder.append(scenario.name())
                                    .append('|')
                                    .append(result.statusCode())
                                    .append('|')
                                    .append(Base64.getEncoder().encodeToString(
                                            result.body().getBytes(StandardCharsets.UTF_8)
                                    ))
                                    .append('\\n');
                        }

                        final Path outputFile = Path.of(outputPath).toAbsolutePath().normalize();
                        final Path parent = outputFile.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
                        System.out.println("TSJ34F-SCENARIOS:" + outputFile);
                    }

                    private static DispatchResult dispatch(
                            final Object controller,
                            final List<Route> routes,
                            final Scenario scenario
                    ) {
                        for (Route route : routes) {
                            if (!route.httpMethod().equals(scenario.method()) || !route.path().equals(scenario.path())) {
                                continue;
                            }
                            try {
                                final Object[] args = resolveArgs(route.method(), scenario.query());
                                final Object value = route.method().invoke(controller, args);
                                return new DispatchResult(200, String.valueOf(value));
                            } catch (final IllegalArgumentException illegalArgumentException) {
                                return new DispatchResult(400, String.valueOf(illegalArgumentException.getMessage()));
                            } catch (final InvocationTargetException invocationTargetException) {
                                return new DispatchResult(500, String.valueOf(invocationTargetException.getTargetException()));
                            } catch (final Exception exception) {
                                return new DispatchResult(500, String.valueOf(exception));
                            }
                        }
                        return new DispatchResult(404, "not-found");
                    }

                    private static Object[] resolveArgs(final Method method, final Map<String, String> query) {
                        final Parameter[] parameters = method.getParameters();
                        final Object[] args = new Object[parameters.length];
                        for (int index = 0; index < parameters.length; index++) {
                            final RequestParam requestParam = parameters[index].getAnnotation(RequestParam.class);
                            final String key = requestParam == null || requestParam.value().isBlank()
                                    ? "arg" + index
                                    : requestParam.value();
                            final String value = query.get(key);
                            if (value == null) {
                                throw new IllegalArgumentException("Request binding failure for request parameter `" + key + "`");
                            }
                            args[index] = value;
                        }
                        return args;
                    }

                    private static String resolveBasePath(final Class<?> controllerClass) {
                        final RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
                        return requestMapping == null ? "" : requestMapping.value();
                    }

                    private static List<Route> resolveRoutes(final Class<?> controllerClass, final String basePath) {
                        final List<Route> routes = new ArrayList<>();
                        for (Method method : controllerClass.getMethods()) {
                            final GetMapping getMapping = method.getAnnotation(GetMapping.class);
                            if (getMapping == null) {
                                continue;
                            }
                            routes.add(new Route("GET", normalizePath(basePath, getMapping.value()), method));
                        }
                        return List.copyOf(routes);
                    }

                    private static String normalizePath(final String basePath, final String methodPath) {
                        final String base = normalizeSegment(basePath);
                        final String method = normalizeSegment(methodPath);
                        if (base.isEmpty()) {
                            return method.isEmpty() ? "/" : method;
                        }
                        if (method.isEmpty()) {
                            return base;
                        }
                        if ("/".equals(base)) {
                            return method;
                        }
                        return base + method;
                    }

                    private static String normalizeSegment(final String raw) {
                        if (raw == null || raw.isBlank()) {
                            return "";
                        }
                        String value = raw.trim();
                        if (!value.startsWith("/")) {
                            value = "/" + value;
                        }
                        while (value.length() > 1 && value.endsWith("/")) {
                            value = value.substring(0, value.length() - 1);
                        }
                        return value;
                    }

                    private record Route(String httpMethod, String path, Method method) {
                    }

                    private record Scenario(String name, String method, String path, Map<String, String> query) {
                    }

                    private record DispatchResult(int statusCode, String body) {
                    }
                }
                """
        );

        final List<Path> sourceFiles;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            sourceFiles = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        compileJavaSources(sourceFiles, classesDir, List.of());
        packageClassesJar(classesDir, jarPath);
        return jarPath;
    }

    private static void writeJavaSource(final Path sourceRoot, final String relativePath, final String source)
            throws Exception {
        final Path file = sourceRoot.resolve(relativePath);
        final Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, source, UTF_8);
    }

    private static void compileJavaSources(
            final List<Path> sourceFiles,
            final Path classesDir,
            final List<Path> classpathEntries
    ) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
        }
        Files.createDirectories(classesDir);
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final StringBuilder classPathBuilder = new StringBuilder(System.getProperty("java.class.path", ""));
            for (Path classpathEntry : classpathEntries) {
                if (classPathBuilder.length() > 0) {
                    classPathBuilder.append(File.pathSeparator);
                }
                classPathBuilder.append(classpathEntry.toAbsolutePath().normalize());
            }
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-classpath",
                    classPathBuilder.toString(),
                    "-d",
                    classesDir.toAbsolutePath().normalize().toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                final List<Diagnostic<? extends JavaFileObject>> messages = diagnostics.getDiagnostics();
                final String rendered = messages.isEmpty()
                        ? "Compilation failed without diagnostics."
                        : messages.get(0).toString();
                fail("Support jar compile failed: " + rendered);
            }
        }
    }

    private static void packageClassesJar(final Path classesDir, final Path jarPath) throws Exception {
        final Path parent = jarPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> paths = Files.walk(classesDir)) {
                final List<Path> classFiles = paths.filter(Files::isRegularFile).sorted().toList();
                for (Path classFile : classFiles) {
                    final String entryName = classesDir.relativize(classFile)
                            .toString()
                            .replace(File.separatorChar, '/');
                    final JarEntry entry = new JarEntry(entryName);
                    jarOutputStream.putNextEntry(entry);
                    Files.copy(classFile, jarOutputStream);
                    jarOutputStream.closeEntry();
                }
            }
        }
    }

    private static Path resolveJavaLauncher() {
        final String executable = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }

    private static final class JavaReferenceController {
        private JavaReferenceController() {
        }

        private static String echo(final String value) {
            return "echo:" + value;
        }
    }

    private static final class KotlinReferenceController {
        private KotlinReferenceController() {
        }

        private static String echo(final String value) {
            return "echo:" + value;
        }
    }

    private record HttpResult(int statusCode, String body) {
    }

    private record ScenarioResult(
            String name,
            HttpResult tsj,
            HttpResult javaReference,
            HttpResult kotlinReference,
            boolean match
    ) {
    }

    private record StageResult(int exitCode, String stdout, String stderr) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
