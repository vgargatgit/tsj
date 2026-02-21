package dev.tsj.compiler.backend.jvm;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-33f differential parity harness for DI/lifecycle subset behavior.
 */
final class TsjSpringDiLifecycleParityHarness {
    private static final String REPORT_FILE = "tsj33f-di-lifecycle-parity-report.json";
    private static final String GENERATED_COMPONENT_PREFIX = "dev.tsj.generated.spring.";
    private static final String DIFF_DIAGNOSTIC_CODE = "TSJ33F-DIFF-MISMATCH";
    private static final String ERROR_DIAGNOSTIC_CODE = "TSJ33F-SCENARIO-ERROR";

    TsjSpringDiLifecycleParityReport run(final Path reportPath) throws Exception {
        final Path fixtureRoot = resolveFixtureRoot();
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj33f-parity-work");
        Files.createDirectories(workRoot);

        final List<TsjSpringDiLifecycleParityReport.ScenarioResult> scenarios = List.of(
                runMixedInjectionScenario(
                        fixtureRoot.resolve("tsj33f-mixed-injection").resolve("main.ts"),
                        workRoot.resolve("mixed-injection")
                ),
                runLifecycleOrderScenario(
                        fixtureRoot.resolve("tsj33f-lifecycle-order").resolve("main.ts"),
                        workRoot.resolve("lifecycle-order")
                ),
                runCycleDiagnosticScenario(
                        fixtureRoot.resolve("tsj33f-cycle-diagnostic").resolve("main.ts"),
                        workRoot.resolve("cycle-diagnostic")
                )
        );

        final TsjSpringDiLifecycleParityReport report = new TsjSpringDiLifecycleParityReport(
                scenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjSpringDiLifecycleParityReport.ScenarioResult runMixedInjectionScenario(
            final Path fixtureFile,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(fixtureFile);
        try (LoadedComponents loadedComponents = compileFixture(fixtureFile, outputDir);
             AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setClassLoader(loadedComponents.classLoader());
            for (Class<?> componentClass : loadedComponents.componentClasses()) {
                context.register(componentClass);
            }
            context.refresh();

            final Class<?> tsServiceClass = loadedComponents.requireComponentClass("MixedInjectionService");
            final Object tsService = context.getBean(tsServiceClass);
            final Method tsReportMethod = tsServiceClass.getMethod("report");
            final String tsValue = String.valueOf(tsReportMethod.invoke(tsService));

            final String javaValue = runJavaMixedInjectionReference();
            final String kotlinValue = runKotlinMixedInjectionReference();
            final boolean passed = tsValue.equals(javaValue) && tsValue.equals(kotlinValue);
            return scenario(
                    "mixed-injection",
                    fixture,
                    passed,
                    passed ? "" : DIFF_DIAGNOSTIC_CODE,
                    tsValue,
                    javaValue,
                    kotlinValue,
                    passed
                            ? "TS, Java, and Kotlin-reference DI outputs matched for constructor/field/setter subset."
                            : "DI output mismatch across TS/Java/Kotlin-reference paths."
            );
        } catch (final Exception exception) {
            return scenarioError("mixed-injection", fixture, exception);
        }
    }

    private TsjSpringDiLifecycleParityReport.ScenarioResult runLifecycleOrderScenario(
            final Path fixtureFile,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(fixtureFile);
        try (LoadedComponents loadedComponents = compileFixture(fixtureFile, outputDir)) {
            final List<String> tsEvents = runTsLifecycleEvents(loadedComponents);
            final List<String> javaEvents = runReferenceLifecycleEvents(JavaReferenceFirstLifecycle.class,
                    JavaReferenceSecondLifecycle.class);
            final List<String> kotlinEvents = runReferenceLifecycleEvents(KotlinReferenceFirstLifecycle.class,
                    KotlinReferenceSecondLifecycle.class);
            final String tsValue = String.join(",", tsEvents);
            final String javaValue = String.join(",", javaEvents);
            final String kotlinValue = String.join(",", kotlinEvents);
            final boolean passed = tsEvents.equals(javaEvents) && tsEvents.equals(kotlinEvents);
            return scenario(
                    "lifecycle-order",
                    fixture,
                    passed,
                    passed ? "" : DIFF_DIAGNOSTIC_CODE,
                    tsValue,
                    javaValue,
                    kotlinValue,
                    passed
                            ? "Lifecycle refresh/close ordering matched Java and Kotlin-reference paths."
                            : "Lifecycle ordering diverged across TS/Java/Kotlin-reference paths."
            );
        } catch (final Exception exception) {
            return scenarioError("lifecycle-order", fixture, exception);
        }
    }

    private TsjSpringDiLifecycleParityReport.ScenarioResult runCycleDiagnosticScenario(
            final Path fixtureFile,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(fixtureFile);
        try (LoadedComponents loadedComponents = compileFixture(fixtureFile, outputDir)) {
            final CycleDiagnosticSignal tsSignal = runTsCycleSignal(loadedComponents);
            final CycleDiagnosticSignal javaSignal = runReferenceCycleSignal(JavaReferenceAlphaCycle.class,
                    JavaReferenceBetaCycle.class);
            final CycleDiagnosticSignal kotlinSignal = runReferenceCycleSignal(KotlinReferenceAlphaCycle.class,
                    KotlinReferenceBetaCycle.class);
            final boolean expectedShape = tsSignal.matchesExpectedShape();
            final boolean passed = expectedShape && tsSignal.equals(javaSignal) && tsSignal.equals(kotlinSignal);
            return scenario(
                    "cycle-diagnostic",
                    fixture,
                    passed,
                    passed ? "" : DIFF_DIAGNOSTIC_CODE,
                    tsSignal.render(),
                    javaSignal.render(),
                    kotlinSignal.render(),
                    passed
                            ? "Cycle diagnostics matched Java and Kotlin-reference shape/token expectations."
                            : "Cycle diagnostics diverged across TS/Java/Kotlin-reference paths."
            );
        } catch (final Exception exception) {
            return scenarioError("cycle-diagnostic", fixture, exception);
        }
    }

    private static TsjSpringDiLifecycleParityReport.ScenarioResult scenario(
            final String scenario,
            final String fixture,
            final boolean passed,
            final String diagnosticCode,
            final String tsValue,
            final String javaValue,
            final String kotlinValue,
            final String notes
    ) {
        return new TsjSpringDiLifecycleParityReport.ScenarioResult(
                scenario,
                fixture,
                passed,
                diagnosticCode,
                tsValue,
                javaValue,
                kotlinValue,
                notes
        );
    }

    private static TsjSpringDiLifecycleParityReport.ScenarioResult scenarioError(
            final String scenario,
            final String fixture,
            final Exception exception
    ) {
        final String rendered = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return scenario(
                scenario,
                fixture,
                false,
                ERROR_DIAGNOSTIC_CODE,
                rendered,
                rendered,
                rendered,
                rendered
        );
    }

    private static String runJavaMixedInjectionReference() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(JavaReferenceMixedConfig.class);
            context.register(JavaReferenceMixedInjectionService.class);
            context.refresh();
            return String.valueOf(context.getBean(JavaReferenceMixedInjectionService.class).report());
        }
    }

    private static String runKotlinMixedInjectionReference() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(KotlinReferenceMixedConfig.class);
            context.register(KotlinReferenceMixedInjectionService.class);
            context.refresh();
            return String.valueOf(context.getBean(KotlinReferenceMixedInjectionService.class).report());
        }
    }

    private static List<String> runTsLifecycleEvents(final LoadedComponents loadedComponents) {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setClassLoader(loadedComponents.classLoader());
        for (Class<?> componentClass : loadedComponents.componentClasses()) {
            context.register(componentClass);
        }
        context.refresh();
        context.close();
        return normalizeLifecycleEvents(context.lifecycleEvents());
    }

    private static List<String> runReferenceLifecycleEvents(final Class<?> firstClass, final Class<?> secondClass) {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(firstClass);
        context.register(secondClass);
        context.refresh();
        context.close();
        return normalizeLifecycleEvents(context.lifecycleEvents());
    }

    private static List<String> normalizeLifecycleEvents(final List<String> events) {
        final List<String> normalized = new ArrayList<>();
        for (String event : events) {
            final int phaseDelimiter = event.indexOf(':');
            final int methodDelimiter = event.indexOf('#');
            if (phaseDelimiter <= 0 || methodDelimiter <= phaseDelimiter) {
                normalized.add(event);
                continue;
            }
            final String phase = event.substring(0, phaseDelimiter);
            final String method = event.substring(methodDelimiter + 1);
            normalized.add(phase + ":" + method);
        }
        return normalized;
    }

    private static CycleDiagnosticSignal runTsCycleSignal(final LoadedComponents loadedComponents) {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setClassLoader(loadedComponents.classLoader());
        for (Class<?> componentClass : loadedComponents.componentClasses()) {
            context.register(componentClass);
        }
        try {
            context.refresh();
            return CycleDiagnosticSignal.empty();
        } catch (final IllegalStateException exception) {
            return CycleDiagnosticSignal.fromMessage(exception.getMessage());
        } finally {
            context.close();
        }
    }

    private static CycleDiagnosticSignal runReferenceCycleSignal(final Class<?> alphaClass, final Class<?> betaClass) {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(alphaClass);
        context.register(betaClass);
        try {
            context.refresh();
            return CycleDiagnosticSignal.empty();
        } catch (final IllegalStateException exception) {
            return CycleDiagnosticSignal.fromMessage(exception.getMessage());
        } finally {
            context.close();
        }
    }

    private LoadedComponents compileFixture(final Path fixtureFile, final Path outputDir) throws Exception {
        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                fixtureFile,
                outputDir.resolve("program")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                fixtureFile,
                compiledArtifact.className(),
                outputDir.resolve("generated-components")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        );
        final List<Class<?>> componentClasses = new ArrayList<>();
        for (String componentClassName : componentArtifact.componentClassNames()) {
            componentClasses.add(Class.forName(
                    GENERATED_COMPONENT_PREFIX + componentClassName + "TsjComponent",
                    true,
                    classLoader
            ));
        }
        return new LoadedComponents(classLoader, componentClasses);
    }

    private static void compileSources(final List<Path> sourceFiles, final Path classesDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
        }
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
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    units
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Generated TS component adapter compile failed: "
                        + diagnostics.getDiagnostics());
            }
        }
    }

    private static Path resolveFixtureRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            final Path fixtureRoot = current.resolve("tests").resolve("spring-matrix");
            if (Files.isDirectory(fixtureRoot)) {
                return fixtureRoot;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate tests/spring-matrix for TSJ-33f parity harness.");
    }

    private static String relativizeFixture(final Path fixtureFile) {
        final Path fixtureRoot = resolveFixtureRoot();
        return "spring-matrix/" + fixtureRoot.relativize(
                fixtureFile.toAbsolutePath().normalize()
        ).toString().replace('\\', '/');
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSpringDiLifecycleParityHarness.class
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
            final TsjSpringDiLifecycleParityReport report
    ) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toJson() + "\n", UTF_8);
    }

    @Configuration
    public static class JavaReferenceMixedConfig {
        @Bean
        public String clockBean() {
            return "clock";
        }

        @Bean
        public String metricsBean() {
            return "metrics";
        }

        @Bean
        public String pricingBean() {
            return "pricing";
        }
    }

    @Service
    public static class JavaReferenceMixedInjectionService {
        @Autowired
        @Qualifier("metricsBean")
        public Object metrics;

        private Object pricing;

        public JavaReferenceMixedInjectionService(@Qualifier("clockBean") final Object clock) {
        }

        @Autowired
        @Qualifier("pricingBean")
        public void setPricing(final Object pricing) {
            this.pricing = pricing;
        }

        public String report() {
            return String.valueOf(metrics) + "|" + String.valueOf(pricing);
        }
    }

    @Configuration
    public static class KotlinReferenceMixedConfig {
        @Bean
        public String clockBean() {
            return "clock";
        }

        @Bean
        public String metricsBean() {
            return "metrics";
        }

        @Bean
        public String pricingBean() {
            return "pricing";
        }
    }

    @Service
    public static class KotlinReferenceMixedInjectionService {
        @Autowired
        @Qualifier("metricsBean")
        public Object metrics;

        private Object pricing;

        public KotlinReferenceMixedInjectionService(@Qualifier("clockBean") final Object clock) {
        }

        @Autowired
        @Qualifier("pricingBean")
        public void setPricing(final Object pricing) {
            this.pricing = pricing;
        }

        public String report() {
            return String.valueOf(metrics) + "|" + String.valueOf(pricing);
        }
    }

    @Service
    public static class JavaReferenceFirstLifecycle {
        @PostConstruct
        public void initFirst() {
        }

        @PreDestroy
        public void shutdownFirst() {
        }
    }

    @Service
    public static class JavaReferenceSecondLifecycle {
        @PostConstruct
        public void initSecond() {
        }

        @PreDestroy
        public void shutdownSecond() {
        }
    }

    @Service
    public static class KotlinReferenceFirstLifecycle {
        @PostConstruct
        public void initFirst() {
        }

        @PreDestroy
        public void shutdownFirst() {
        }
    }

    @Service
    public static class KotlinReferenceSecondLifecycle {
        @PostConstruct
        public void initSecond() {
        }

        @PreDestroy
        public void shutdownSecond() {
        }
    }

    @Service
    @Qualifier("alpha")
    public static class JavaReferenceAlphaCycle {
        public JavaReferenceAlphaCycle(@Qualifier("beta") final Object dep) {
        }
    }

    @Service
    @Qualifier("beta")
    public static class JavaReferenceBetaCycle {
        public JavaReferenceBetaCycle(@Qualifier("alpha") final Object dep) {
        }
    }

    @Service
    @Qualifier("alpha")
    public static class KotlinReferenceAlphaCycle {
        public KotlinReferenceAlphaCycle(@Qualifier("beta") final Object dep) {
        }
    }

    @Service
    @Qualifier("beta")
    public static class KotlinReferenceBetaCycle {
        public KotlinReferenceBetaCycle(@Qualifier("alpha") final Object dep) {
        }
    }

    private record LoadedComponents(URLClassLoader classLoader, List<Class<?>> componentClasses)
            implements AutoCloseable {
        private static final String GENERATED_SUFFIX = "TsjComponent";

        private LoadedComponents {
            componentClasses = List.copyOf(componentClasses);
        }

        @Override
        public void close() throws Exception {
            classLoader.close();
        }

        private Class<?> requireComponentClass(final String logicalName) {
            final String expectedSimpleName = logicalName + GENERATED_SUFFIX;
            for (Class<?> componentClass : componentClasses) {
                if (expectedSimpleName.equals(componentClass.getSimpleName())) {
                    return componentClass;
                }
            }
            throw new IllegalStateException("Missing generated component class: " + expectedSimpleName);
        }
    }

    private record CycleDiagnosticSignal(
            boolean lifecycleCode,
            boolean lifecycleFeatureId,
            boolean circularDependency,
            boolean alphaMention,
            boolean betaMention
    ) {
        private static CycleDiagnosticSignal fromMessage(final String message) {
            final String rendered = String.valueOf(message);
            return new CycleDiagnosticSignal(
                    rendered.contains("TSJ-SPRING-LIFECYCLE"),
                    rendered.contains("TSJ33E-LIFECYCLE"),
                    rendered.contains("Circular dependency"),
                    rendered.contains("Alpha"),
                    rendered.contains("Beta")
            );
        }

        private static CycleDiagnosticSignal empty() {
            return new CycleDiagnosticSignal(false, false, false, false, false);
        }

        private boolean matchesExpectedShape() {
            return lifecycleCode && lifecycleFeatureId && circularDependency && alphaMention && betaMention;
        }

        private String render() {
            return "lifecycleCode=" + lifecycleCode
                    + ",featureId=" + lifecycleFeatureId
                    + ",circular=" + circularDependency
                    + ",alpha=" + alphaMention
                    + ",beta=" + betaMention;
        }
    }
}
