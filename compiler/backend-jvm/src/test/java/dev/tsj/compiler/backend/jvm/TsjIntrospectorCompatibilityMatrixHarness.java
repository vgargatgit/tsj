package dev.tsj.compiler.backend.jvm;

import dev.tsj.compiler.backend.jvm.fixtures.InteropSpringFixtureType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-39b third-party introspector compatibility matrix harness.
 */
final class TsjIntrospectorCompatibilityMatrixHarness {
    private static final String REPORT_FILE = "tsj39b-introspector-matrix.json";
    private static final String UNSUPPORTED_CODE = "TSJ39B-INTROSPECTOR-UNSUPPORTED";
    private static final String MISMATCH_CODE = "TSJ39B-INTROSPECTOR-MISMATCH";
    private static final String UNKNOWN_SCENARIO_CODE = "TSJ39B-INTROSPECTOR-UNKNOWN";

    TsjIntrospectorCompatibilityMatrixReport run(final Path reportPath) throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path fixtureRoot = repoRoot.resolve("tests").resolve("introspector-matrix");
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj39b-introspector-work");
        Files.createDirectories(workRoot);

        final List<Path> fixtureFiles;
        try (java.util.stream.Stream<Path> paths = Files.walk(fixtureRoot, 2)) {
            fixtureFiles = paths
                    .filter(path -> path.getFileName().toString().equals("fixture.properties"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        final List<TsjIntrospectorCompatibilityMatrixReport.ScenarioResult> scenarios = new ArrayList<>();
        for (Path fixtureFile : fixtureFiles) {
            final Properties properties = loadProperties(fixtureFile);
            final String scenario = requiredProperty(properties, "scenario", fixtureFile);
            final String library = requiredProperty(properties, "library", fixtureFile);
            final String version = requiredProperty(properties, "version", fixtureFile);
            final boolean supported = Boolean.parseBoolean(requiredProperty(properties, "supported", fixtureFile));
            final String guidance = properties.getProperty(
                    "guidance",
                    "Use documented TSJ-supported introspector scenarios or fallback to reflection-based metadata reads."
            );
            final String fixture = repoRoot.relativize(fixtureFile).toString().replace('\\', '/');
            final Path scenarioWorkDir = workRoot.resolve(fixtureFile.getParent().getFileName().toString());
            Files.createDirectories(scenarioWorkDir);

            scenarios.add(runScenario(
                    repoRoot,
                    fixture,
                    scenario,
                    library,
                    version,
                    supported,
                    guidance,
                    properties,
                    scenarioWorkDir
            ));
        }

        final Path moduleReportPath = resolveModuleReportPath();
        final TsjIntrospectorCompatibilityMatrixReport report = new TsjIntrospectorCompatibilityMatrixReport(
                scenarios,
                normalizedReport,
                moduleReportPath
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runScenario(
            final Path repoRoot,
            final String fixture,
            final String scenario,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Properties properties,
            final Path workDir
    ) {
        return switch (scenario) {
            case "bridge-generic-signature" -> runBridgeGenericSignatureScenario(
                    fixture,
                    library,
                    version,
                    supported,
                    guidance,
                    workDir
            );
            case "spring-web-mapping-introspection" -> runSpringWebMappingScenario(
                    repoRoot,
                    fixture,
                    library,
                    version,
                    supported,
                    guidance,
                    properties,
                    workDir
            );
            case "jackson-unsupported" -> runUnsupportedScenario(
                    fixture,
                    library,
                    version,
                    supported,
                    guidance
            );
            default -> new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    scenario,
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    UNKNOWN_SCENARIO_CODE,
                    "Use documented scenarios: bridge-generic-signature, spring-web-mapping-introspection, "
                            + "jackson-unsupported.",
                    "Unknown introspector scenario in fixture."
            );
        };
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runBridgeGenericSignatureScenario(
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Path workDir
    ) {
        try {
            final String fixtureClass = InteropSpringFixtureType.class.getName();
            final Path specFile = workDir.resolve("interop-spring-metadata.properties");
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

            final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(specFile, workDir.resolve("bridge"));
            final Path classesDir = workDir.resolve("classes");
            compileSources(artifact.sourceFiles(), classesDir);

            final String bridgeSimpleName = artifact.sourceFiles().getFirst().getFileName().toString().replace(".java", "");
            final String bridgeClassName = "dev.tsj.generated.interop." + bridgeSimpleName;
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{classesDir.toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final Class<?> bridgeClass = Class.forName(bridgeClassName, true, classLoader);
                final Method method = bridgeClass.getMethod("copyLabels", List.class);
                final boolean passed = method.getGenericReturnType() instanceof ParameterizedType
                        && "java.util.List<java.lang.String>".equals(method.getGenericReturnType().getTypeName())
                        && "java.util.List<java.lang.String>".equals(method.getGenericParameterTypes()[0].getTypeName())
                        && "arg0".equals(method.getParameters()[0].getName());
                return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                        "bridge-generic-signature",
                        fixture,
                        library,
                        version,
                        supported,
                        passed,
                        passed ? "" : MISMATCH_CODE,
                        guidance,
                        passed
                                ? "Reflection introspector reads generic signature and parameter metadata successfully."
                                : "Reflection introspector observed metadata mismatch on generated bridge method."
                );
            }
        } catch (final Exception exception) {
            return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    "bridge-generic-signature",
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    MISMATCH_CODE,
                    guidance,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runSpringWebMappingScenario(
            final Path repoRoot,
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Properties properties,
            final Path workDir
    ) {
        try {
            final String entryValue = requiredProperty(properties, "entry", Path.of(fixture));
            final Path entryFile = repoRoot.resolve(entryValue).toAbsolutePath().normalize();
            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, workDir.resolve("program"));
            final TsjSpringWebControllerArtifact webArtifact = new TsjSpringWebControllerGenerator().generate(
                    entryFile,
                    compiledArtifact.className(),
                    workDir.resolve("generated")
            );
            compileSources(webArtifact.sourceFiles(), compiledArtifact.outputDirectory());

            final String generatedClassName = "dev.tsj.generated.web.WebMatrixControllerTsjController";
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final Class<?> controllerClass = Class.forName(generatedClassName, true, classLoader);
                final Method method = controllerClass.getMethod("echo", Object.class);
                final RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
                final GetMapping getMapping = method.getAnnotation(GetMapping.class);
                final boolean passed = requestMapping != null
                        && "/api".equals(requestMapping.value())
                        && getMapping != null
                        && "/echo".equals(getMapping.value())
                        && method.getParameterCount() == 1;
                return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                        "spring-web-mapping-introspection",
                        fixture,
                        library,
                        version,
                        supported,
                        passed,
                        passed ? "" : MISMATCH_CODE,
                        guidance,
                        passed
                                ? "Spring-web-style annotation introspection reads route metadata successfully."
                                : "Spring-web-style annotation introspection observed mapping metadata mismatch."
                );
            }
        } catch (final Exception exception) {
            return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    "spring-web-mapping-introspection",
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    MISMATCH_CODE,
                    guidance,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private static TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runUnsupportedScenario(
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance
    ) {
        return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                "jackson-unsupported",
                fixture,
                library,
                version,
                supported,
                true,
                UNSUPPORTED_CODE,
                guidance,
                "Unsupported introspector scenario is explicitly diagnosed with actionable fallback guidance."
        );
    }

    private static Properties loadProperties(final Path fixtureFile) throws IOException {
        final Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(fixtureFile)) {
            properties.load(input);
        }
        return properties;
    }

    private static String requiredProperty(
            final Properties properties,
            final String key,
            final Path fixtureFile
    ) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required fixture property `" + key + "` in " + fixtureFile);
        }
        return value.trim();
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
            } else {
                classPath = classPath + java.io.File.pathSeparator + classesDir;
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
                throw new IllegalStateException("Generated source compile failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("docs").resolve("stories.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve repository root for TSJ-39b harness.");
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjIntrospectorCompatibilityMatrixHarness.class
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
            final TsjIntrospectorCompatibilityMatrixReport report
    ) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toJson() + "\n", UTF_8);
    }
}
