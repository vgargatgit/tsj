package dev.tsj.compiler.backend.jvm;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-35b booted runtime conformance harness for transactional/AOP subset behavior.
 */
final class TsjSpringAopRuntimeConformanceHarness {
    private static final String MODULE_REPORT_FILE = "tsj35b-aop-runtime-conformance-report.json";

    TsjSpringAopRuntimeConformanceReport run(final Path reportPath) throws Exception {
        final Path fixtureRoot = resolveFixtureRoot();
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReportPath.getParent().resolve("tsj35b-runtime-work");
        Files.createDirectories(workRoot);

        final Path commitFixture = fixtureRoot.resolve("tsj35b-transaction-chain").resolve("main.ts");
        final Path rollbackFixture = fixtureRoot.resolve("tsj35b-transaction-rollback").resolve("main.ts");
        final List<TsjSpringAopRuntimeConformanceReport.ScenarioResult> scenarios = List.of(
                runCommitChainScenario(commitFixture, workRoot.resolve("commit-chain")),
                runRollbackChainScenario(rollbackFixture, workRoot.resolve("rollback-chain")),
                runMissingTxManagerScenario(commitFixture, workRoot.resolve("missing-tx-manager")),
                runApplicationFailureScenario(rollbackFixture, workRoot.resolve("application-failure"))
        );

        final TsjSpringAopRuntimeConformanceReport report = new TsjSpringAopRuntimeConformanceReport(
                scenarios,
                normalizedReportPath,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjSpringAopRuntimeConformanceReport.ScenarioResult runCommitChainScenario(
            final Path fixtureFile,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(fixtureFile);
        try (LoadedComponents loadedComponents = compileFixture(fixtureFile, outputDir);
             AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setClassLoader(loadedComponents.classLoader());
            context.register(RecordingTransactionManagerConfig.class);
            for (Class<?> componentClass : loadedComponents.componentClasses()) {
                context.register(componentClass);
            }
            context.refresh();

            final Class<?> orderServiceClass = loadedComponents.requireComponentClass("OrderService");
            final Object orderService = context.getBean(orderServiceClass);
            final Method place = orderServiceClass.getMethod("place", Object.class);
            final Object result = place.invoke(orderService, 7);
            final RecordingTransactionManager transactionManager =
                    (RecordingTransactionManager) context.getBean(PlatformTransactionManager.class);

            final List<String> expectedEvents = List.of(
                    "begin:OrderServiceTsjComponent#place",
                    "begin:LedgerServiceTsjComponent#record",
                    "commit:LedgerServiceTsjComponent#record",
                    "commit:OrderServiceTsjComponent#place"
            );
            final boolean passed = "ledger:7".equals(String.valueOf(result))
                    && transactionManager.beginCount() == 2
                    && transactionManager.commitCount() == 2
                    && transactionManager.rollbackCount() == 0
                    && expectedEvents.equals(transactionManager.events());
            return new TsjSpringAopRuntimeConformanceReport.ScenarioResult(
                    "commit-chain",
                    fixture,
                    passed,
                    passed ? "" : "TSJ35B-COMMIT-MISMATCH",
                    transactionManager.beginCount(),
                    transactionManager.commitCount(),
                    transactionManager.rollbackCount(),
                    passed
                            ? "Transactional chain commit behavior matched TSJ-35b subset expectations."
                            : "Commit-chain runtime behavior diverged from TSJ-35b subset expectations."
            );
        } catch (final Exception exception) {
            return failure(
                    "commit-chain",
                    fixture,
                    "TSJ35B-COMMIT-ERROR",
                    "Commit-chain scenario failed: " + exception.getClass().getSimpleName() + ": "
                            + exception.getMessage(),
                    0,
                    0,
                    0
            );
        }
    }

    private TsjSpringAopRuntimeConformanceReport.ScenarioResult runRollbackChainScenario(
            final Path fixtureFile,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(fixtureFile);
        try (LoadedComponents loadedComponents = compileFixture(fixtureFile, outputDir);
             AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setClassLoader(loadedComponents.classLoader());
            context.register(RecordingTransactionManagerConfig.class);
            for (Class<?> componentClass : loadedComponents.componentClasses()) {
                context.register(componentClass);
            }
            context.refresh();

            final Class<?> orderServiceClass = loadedComponents.requireComponentClass("OrderService");
            final Object orderService = context.getBean(orderServiceClass);
            final Method place = orderServiceClass.getMethod("place", Object.class);
            final Throwable cause = captureInvocationFailure(place, orderService, 9);
            final RecordingTransactionManager transactionManager =
                    (RecordingTransactionManager) context.getBean(PlatformTransactionManager.class);

            final List<String> expectedEvents = List.of(
                    "begin:OrderServiceTsjComponent#place",
                    "begin:LedgerServiceTsjComponent#failRecord",
                    "rollback:LedgerServiceTsjComponent#failRecord",
                    "rollback:OrderServiceTsjComponent#place"
            );
            final boolean passed = cause != null
                    && String.valueOf(cause).contains("chain-failure")
                    && transactionManager.beginCount() == 2
                    && transactionManager.commitCount() == 0
                    && transactionManager.rollbackCount() == 2
                    && expectedEvents.equals(transactionManager.events());
            return new TsjSpringAopRuntimeConformanceReport.ScenarioResult(
                    "rollback-chain",
                    fixture,
                    passed,
                    passed ? "" : "TSJ35B-ROLLBACK-MISMATCH",
                    transactionManager.beginCount(),
                    transactionManager.commitCount(),
                    transactionManager.rollbackCount(),
                    passed
                            ? "Transactional chain rollback behavior matched TSJ-35b subset expectations."
                            : "Rollback-chain runtime behavior diverged from TSJ-35b subset expectations."
            );
        } catch (final Exception exception) {
            return failure(
                    "rollback-chain",
                    fixture,
                    "TSJ35B-ROLLBACK-ERROR",
                    "Rollback-chain scenario failed: " + exception.getClass().getSimpleName() + ": "
                            + exception.getMessage(),
                    0,
                    0,
                    0
            );
        }
    }

    private TsjSpringAopRuntimeConformanceReport.ScenarioResult runMissingTxManagerScenario(
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
            final IllegalStateException exception = expectRefreshFailure(context);
            final boolean passed = exception.getMessage() != null
                    && exception.getMessage().contains("TSJ-SPRING-AOP");
            return new TsjSpringAopRuntimeConformanceReport.ScenarioResult(
                    "missing-transaction-manager",
                    fixture,
                    passed,
                    passed ? "TSJ-SPRING-AOP" : "TSJ35B-INFRA-DIAGNOSTIC-MISMATCH",
                    0,
                    0,
                    0,
                    passed
                            ? "AOP infrastructure wiring failure surfaced with stable TSJ-SPRING-AOP diagnostic."
                            : "Infrastructure failure did not emit expected TSJ-SPRING-AOP diagnostic."
            );
        } catch (final Exception exception) {
            return failure(
                    "missing-transaction-manager",
                    fixture,
                    "TSJ35B-INFRA-ERROR",
                    "Infrastructure scenario failed unexpectedly: "
                            + exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    0,
                    0,
                    0
            );
        }
    }

    private TsjSpringAopRuntimeConformanceReport.ScenarioResult runApplicationFailureScenario(
            final Path fixtureFile,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(fixtureFile);
        try (LoadedComponents loadedComponents = compileFixture(fixtureFile, outputDir);
             AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setClassLoader(loadedComponents.classLoader());
            context.register(RecordingTransactionManagerConfig.class);
            for (Class<?> componentClass : loadedComponents.componentClasses()) {
                context.register(componentClass);
            }
            context.refresh();

            final Class<?> orderServiceClass = loadedComponents.requireComponentClass("OrderService");
            final Object orderService = context.getBean(orderServiceClass);
            final Method place = orderServiceClass.getMethod("place", Object.class);
            final Throwable cause = captureInvocationFailure(place, orderService, 5);
            final RecordingTransactionManager transactionManager =
                    (RecordingTransactionManager) context.getBean(PlatformTransactionManager.class);
            final boolean passed = cause != null
                    && String.valueOf(cause).contains("chain-failure")
                    && !String.valueOf(cause).contains("TSJ-SPRING-AOP")
                    && transactionManager.rollbackCount() > 0;
            return new TsjSpringAopRuntimeConformanceReport.ScenarioResult(
                    "application-invocation-failure",
                    fixture,
                    passed,
                    "",
                    transactionManager.beginCount(),
                    transactionManager.commitCount(),
                    transactionManager.rollbackCount(),
                    passed
                            ? "Application invocation failure remained distinct from infrastructure diagnostics."
                            : "Application failure path was incorrectly classified as infrastructure error."
            );
        } catch (final Exception exception) {
            return failure(
                    "application-invocation-failure",
                    fixture,
                    "TSJ35B-APP-ERROR",
                    "Application-failure scenario failed unexpectedly: "
                            + exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    0,
                    0,
                    0
            );
        }
    }

    private static TsjSpringAopRuntimeConformanceReport.ScenarioResult failure(
            final String scenario,
            final String fixture,
            final String diagnosticCode,
            final String notes,
            final int beginCount,
            final int commitCount,
            final int rollbackCount
    ) {
        return new TsjSpringAopRuntimeConformanceReport.ScenarioResult(
                scenario,
                fixture,
                false,
                diagnosticCode,
                beginCount,
                commitCount,
                rollbackCount,
                notes
        );
    }

    private static Throwable captureInvocationFailure(
            final Method method,
            final Object receiver,
            final Object argument
    ) throws IllegalAccessException {
        try {
            method.invoke(receiver, argument);
            return null;
        } catch (final InvocationTargetException invocationTargetException) {
            return invocationTargetException.getTargetException();
        }
    }

    private static IllegalStateException expectRefreshFailure(final AnnotationConfigApplicationContext context) {
        try {
            context.refresh();
        } catch (final IllegalStateException illegalStateException) {
            return illegalStateException;
        }
        throw new IllegalStateException("Expected refresh failure for missing transaction manager.");
    }

    private static LoadedComponents compileFixture(final Path entryFile, final Path outputDir) throws Exception {
        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, outputDir.resolve("program"));
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                outputDir.resolve("generated-components")
        );
        compileGeneratedSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());
        final URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                TsjSpringAopRuntimeConformanceHarness.class.getClassLoader()
        );
        final List<Class<?>> componentClasses = new ArrayList<>();
        for (String componentClassName : componentArtifact.componentClassNames()) {
            componentClasses.add(Class.forName(
                    "dev.tsj.generated.spring." + componentClassName + "TsjComponent",
                    true,
                    classLoader
            ));
        }
        return new LoadedComponents(classLoader, componentClasses);
    }

    private static void compileGeneratedSources(final List<Path> sourceFiles, final Path outputDir) throws Exception {
        if (sourceFiles.isEmpty()) {
            return;
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.isBlank()) {
                classPath = outputDir.toString();
            } else {
                classPath = classPath + java.io.File.pathSeparator + outputDir;
            }
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-classpath",
                    classPath,
                    "-d",
                    outputDir.toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException(
                        "Failed to compile generated Spring component sources: " + diagnostics.getDiagnostics()
                );
            }
        }
    }

    private static Path resolveFixtureRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            final Path candidate = current.resolve("tests").resolve("spring-matrix");
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve tests/spring-matrix fixture root.");
    }

    private static String relativizeFixture(final Path fixtureFile) {
        final Path absoluteFixture = fixtureFile.toAbsolutePath().normalize();
        final Path fixtureRoot = resolveFixtureRoot();
        try {
            return fixtureRoot.getParent().relativize(absoluteFixture).toString().replace('\\', '/');
        } catch (final IllegalArgumentException ignored) {
            return absoluteFixture.toString().replace('\\', '/');
        }
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSpringAopRuntimeConformanceHarness.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve(MODULE_REPORT_FILE);
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target", MODULE_REPORT_FILE).toAbsolutePath().normalize();
    }

    private static void writeReport(
            final Path reportPath,
            final TsjSpringAopRuntimeConformanceReport report
    ) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toJson() + "\n", UTF_8);
    }

    @Configuration
    public static class RecordingTransactionManagerConfig {
        @Bean
        public PlatformTransactionManager platformTransactionManager() {
            return new RecordingTransactionManager();
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        private final List<String> events = new ArrayList<>();
        private int beginCount;
        private int commitCount;
        private int rollbackCount;

        @Override
        public TransactionStatus begin(final String beanName, final String methodName) {
            beginCount++;
            events.add("begin:" + simpleName(beanName) + "#" + methodName);
            return new TransactionStatus(beanName, methodName);
        }

        @Override
        public void commit(final TransactionStatus status) {
            commitCount++;
            events.add("commit:" + simpleName(status.beanName()) + "#" + status.methodName());
        }

        @Override
        public void rollback(final TransactionStatus status) {
            rollbackCount++;
            events.add("rollback:" + simpleName(status.beanName()) + "#" + status.methodName());
        }

        private int beginCount() {
            return beginCount;
        }

        private int commitCount() {
            return commitCount;
        }

        private int rollbackCount() {
            return rollbackCount;
        }

        private List<String> events() {
            return List.copyOf(events);
        }

        private static String simpleName(final String className) {
            final int separator = className.lastIndexOf('.');
            if (separator < 0 || separator >= className.length() - 1) {
                return className;
            }
            return className.substring(separator + 1);
        }
    }

    private record LoadedComponents(
            URLClassLoader classLoader,
            List<Class<?>> componentClasses
    ) implements AutoCloseable {
        private Class<?> requireComponentClass(final String simpleName) {
            final String suffix = simpleName + "TsjComponent";
            for (Class<?> componentClass : componentClasses) {
                if (componentClass.getSimpleName().equals(suffix)) {
                    return componentClass;
                }
            }
            throw new IllegalStateException("Generated component class not found: " + suffix);
        }

        @Override
        public void close() throws IOException {
            classLoader.close();
        }
    }
}
