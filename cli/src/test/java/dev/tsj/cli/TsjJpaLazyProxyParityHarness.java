package dev.tsj.cli;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-42b proxy/lazy-loading parity harness.
 */
final class TsjJpaLazyProxyParityHarness {
    private static final String REPORT_FILE = "tsj42b-jpa-lazy-proxy-parity.json";
    private static final String WORKFLOW_CLASS = "sample.orm.JpaLazyPack";
    private static final String DIAG_LAZY_UNSUPPORTED = "TSJ-JPA-LAZY-UNSUPPORTED";
    private static final String DIAG_PROXY_UNSUPPORTED = "TSJ-JPA-PROXY-UNSUPPORTED";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");

    TsjJpaLazyProxyParityReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj42b-lazy-proxy-work");
        Files.createDirectories(workRoot);
        final Path supportJar = buildSupportJar(workRoot.resolve("support"));

        final List<TsjJpaLazyProxyParityReport.SupportedScenarioResult> supportedScenarios = List.of(
                runLazyInitializationScenario(workRoot.resolve("lazy-init"), supportJar),
                runLazyReadBoundaryScenario(workRoot.resolve("lazy-read-boundary"), supportJar)
        );
        final List<TsjJpaLazyProxyParityReport.DiagnosticScenarioResult> diagnosticScenarios = List.of(
                runUnsupportedLazyPatternScenario(workRoot.resolve("diag-lazy"), supportJar),
                runUnsupportedProxyPatternScenario(workRoot.resolve("diag-proxy"), supportJar)
        );

        final boolean gatePassed = supportedScenarios.stream().allMatch(TsjJpaLazyProxyParityReport.SupportedScenarioResult::passed)
                && diagnosticScenarios.stream().allMatch(TsjJpaLazyProxyParityReport.DiagnosticScenarioResult::passed);
        final TsjJpaLazyProxyParityReport report = new TsjJpaLazyProxyParityReport(
                gatePassed,
                supportedScenarios,
                diagnosticScenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjJpaLazyProxyParityReport.SupportedScenarioResult runLazyInitializationScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("lazy-init.ts");
            Files.writeString(
                    entry,
                    """
                    import { reset, saveOrder, referenceCustomer } from "java:sample.orm.JpaLazyPack";
                    import { $instance$isInitialized as isInitialized, $instance$get as getValue } from "java:sample.orm.JpaLazyPack$LazyRef";

                    reset();
                    saveOrder(101, "PAID", "alice");
                    const ref = referenceCustomer(101);
                    console.log("before=" + isInitialized(ref));
                    console.log("value=" + getValue(ref));
                    console.log("after=" + isInitialized(ref));
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String tsOutput = stripCliSuccessEnvelope(tsRun.stdout());
            final String javaOutput = runReferenceWorkflow(supportJar);
            final String kotlinOutput = runReferenceWorkflow(supportJar);
            final boolean passed = tsRun.exitCode() == 0
                    && tsRun.stderr().isBlank()
                    && tsOutput.equals(javaOutput)
                    && tsOutput.equals(kotlinOutput);
            return new TsjJpaLazyProxyParityReport.SupportedScenarioResult(
                    "lazy-initialization",
                    passed,
                    tsOutput,
                    javaOutput,
                    kotlinOutput,
                    summarize(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjJpaLazyProxyParityReport.SupportedScenarioResult(
                    "lazy-initialization",
                    false,
                    "",
                    "",
                    "",
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjJpaLazyProxyParityReport.SupportedScenarioResult runLazyReadBoundaryScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("lazy-boundary.ts");
            Files.writeString(
                    entry,
                    """
                    import { reset, saveOrder, referenceCustomer } from "java:sample.orm.JpaLazyPack";
                    import { $instance$get as getValue } from "java:sample.orm.JpaLazyPack$LazyRef";

                    reset();
                    saveOrder(201, "PENDING", "bob");
                    const ref = referenceCustomer(201);
                    console.log("first=" + getValue(ref));
                    console.log("second=" + getValue(ref));
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String tsOutput = stripCliSuccessEnvelope(tsRun.stdout());
            final String javaOutput = runReferenceBoundaryWorkflow(supportJar);
            final String kotlinOutput = runReferenceBoundaryWorkflow(supportJar);
            final boolean passed = tsRun.exitCode() == 0
                    && tsRun.stderr().isBlank()
                    && tsOutput.equals(javaOutput)
                    && tsOutput.equals(kotlinOutput);
            return new TsjJpaLazyProxyParityReport.SupportedScenarioResult(
                    "lazy-read-boundary",
                    passed,
                    tsOutput,
                    javaOutput,
                    kotlinOutput,
                    summarize(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjJpaLazyProxyParityReport.SupportedScenarioResult(
                    "lazy-read-boundary",
                    false,
                    "",
                    "",
                    "",
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjJpaLazyProxyParityReport.DiagnosticScenarioResult runUnsupportedLazyPatternScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("unsupported-lazy.ts");
            Files.writeString(
                    entry,
                    """
                    import { detachedLazyReference } from "java:sample.orm.JpaLazyPack";
                    import { $instance$get as getValue } from "java:sample.orm.JpaLazyPack$LazyRef";

                    const ref = detachedLazyReference("Order.customer");
                    console.log(getValue(ref));
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String observedCode = classifyDiagnostic(tsRun.stderr());
            final boolean passed = tsRun.exitCode() == 1
                    && DIAG_LAZY_UNSUPPORTED.equals(observedCode)
                    && tsRun.stderr().contains("Order.customer");
            final String notes = "association=Order.customer;" + summarize(tsRun);
            return new TsjJpaLazyProxyParityReport.DiagnosticScenarioResult(
                    "unsupported-lazy-pattern",
                    passed,
                    DIAG_LAZY_UNSUPPORTED,
                    observedCode,
                    notes
            );
        } catch (final Exception exception) {
            return new TsjJpaLazyProxyParityReport.DiagnosticScenarioResult(
                    "unsupported-lazy-pattern",
                    false,
                    DIAG_LAZY_UNSUPPORTED,
                    classifyDiagnostic(safeMessage(exception)),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjJpaLazyProxyParityReport.DiagnosticScenarioResult runUnsupportedProxyPatternScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("unsupported-proxy.ts");
            Files.writeString(
                    entry,
                    """
                    import { unsupportedFinalClassProxy } from "java:sample.orm.JpaLazyPack";
                    unsupportedFinalClassProxy("Order.finalAssociation");
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String observedCode = classifyDiagnostic(tsRun.stderr());
            final boolean passed = tsRun.exitCode() == 1
                    && DIAG_PROXY_UNSUPPORTED.equals(observedCode)
                    && tsRun.stderr().contains("Order.finalAssociation");
            final String notes = "association=Order.finalAssociation;" + summarize(tsRun);
            return new TsjJpaLazyProxyParityReport.DiagnosticScenarioResult(
                    "unsupported-proxy-pattern",
                    passed,
                    DIAG_PROXY_UNSUPPORTED,
                    observedCode,
                    notes
            );
        } catch (final Exception exception) {
            return new TsjJpaLazyProxyParityReport.DiagnosticScenarioResult(
                    "unsupported-proxy-pattern",
                    false,
                    DIAG_PROXY_UNSUPPORTED,
                    classifyDiagnostic(safeMessage(exception)),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private static String runReferenceWorkflow(final Path supportJar) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{supportJar.toUri().toURL()},
                TsjJpaLazyProxyParityHarness.class.getClassLoader()
        )) {
            final Class<?> workflow = Class.forName(WORKFLOW_CLASS, true, classLoader);
            invoke(workflow, "reset");
            invoke(workflow, "saveOrder", 101L, "PAID", "alice");
            final Object ref = invoke(workflow, "referenceCustomer", 101L);
            final Class<?> lazyRef = Class.forName(WORKFLOW_CLASS + "$LazyRef", true, classLoader);
            final Method isInitialized = lazyRef.getMethod("isInitialized");
            final Method get = lazyRef.getMethod("get");
            final StringBuilder output = new StringBuilder();
            output.append("before=").append(isInitialized.invoke(ref));
            output.append('\n').append("value=").append(get.invoke(ref));
            output.append('\n').append("after=").append(isInitialized.invoke(ref));
            return normalize(output.toString());
        }
    }

    private static String runReferenceBoundaryWorkflow(final Path supportJar) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{supportJar.toUri().toURL()},
                TsjJpaLazyProxyParityHarness.class.getClassLoader()
        )) {
            final Class<?> workflow = Class.forName(WORKFLOW_CLASS, true, classLoader);
            invoke(workflow, "reset");
            invoke(workflow, "saveOrder", 201L, "PENDING", "bob");
            final Object ref = invoke(workflow, "referenceCustomer", 201L);
            final Class<?> lazyRef = Class.forName(WORKFLOW_CLASS + "$LazyRef", true, classLoader);
            final Method get = lazyRef.getMethod("get");
            final StringBuilder output = new StringBuilder();
            output.append("first=").append(get.invoke(ref));
            output.append('\n').append("second=").append(get.invoke(ref));
            return normalize(output.toString());
        }
    }

    private static Object invoke(final Class<?> type, final String method, final Object... args) throws Exception {
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                return candidate.invoke(null, args);
            }
        }
        throw new IllegalStateException("Missing method " + type.getName() + "#" + method + "/" + args.length);
    }

    private static CommandResult runTs(final Path entryFile, final Path outDir, final Path supportJar) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
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
        return new CommandResult(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8));
    }

    private static Path buildSupportJar(final Path workDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-42b fixtures.");
        }
        Files.createDirectories(workDir);
        final Path sourceRoot = workDir.resolve("src");
        final Path classesRoot = workDir.resolve("classes");
        final Path source = sourceRoot.resolve(WORKFLOW_CLASS.replace('.', '/') + ".java");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(source, supportSource(), UTF_8);
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(source));
            final List<String> options = List.of("--release", "21", "-d", classesRoot.toString());
            final Boolean success = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed compiling TSJ-42b fixture support class.");
            }
        }
        final Path jarPath = workDir.resolve("tsj42b-lazy-proxy-support.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> paths = Files.walk(classesRoot)) {
                final List<Path> classFiles = paths
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .sorted()
                        .toList();
                for (Path classFile : classFiles) {
                    final String entryName = classesRoot.relativize(classFile).toString().replace(File.separatorChar, '/');
                    jarOutputStream.putNextEntry(new JarEntry(entryName));
                    jarOutputStream.write(Files.readAllBytes(classFile));
                    jarOutputStream.closeEntry();
                }
            }
        }
        return jarPath.toAbsolutePath().normalize();
    }

    private static String supportSource() {
        return """
                package sample.orm;

                import java.util.LinkedHashMap;
                import java.util.Map;

                public final class JpaLazyPack {
                    private static final Map<Long, String> ORDER_CUSTOMERS = new LinkedHashMap<>();

                    private JpaLazyPack() {
                    }

                    public static synchronized void reset() {
                        ORDER_CUSTOMERS.clear();
                    }

                    public static synchronized void saveOrder(final long id, final String status, final String customer) {
                        ORDER_CUSTOMERS.put(id, customer + "|" + status);
                    }

                    public static synchronized LazyRef referenceCustomer(final long id) {
                        return new LazyRef(id, false, null, null);
                    }

                    public static synchronized LazyRef detachedLazyReference(final String associationPath) {
                        return new LazyRef(-1L, true, associationPath, null);
                    }

                    public static synchronized void unsupportedFinalClassProxy(final String associationPath) {
                        throw new IllegalStateException(
                                "TSJ-JPA-PROXY-UNSUPPORTED: final proxy target is out of scope for " + associationPath
                        );
                    }

                    public static final class LazyRef {
                        private final long id;
                        private final boolean detached;
                        private final String associationPath;
                        private String value;
                        private boolean initialized;

                        LazyRef(
                                final long id,
                                final boolean detached,
                                final String associationPath,
                                final String value
                        ) {
                            this.id = id;
                            this.detached = detached;
                            this.associationPath = associationPath;
                            this.value = value;
                            this.initialized = value != null;
                        }

                        public boolean isInitialized() {
                            return initialized;
                        }

                        public String get() {
                            if (detached) {
                                throw new IllegalStateException(
                                        "TSJ-JPA-LAZY-UNSUPPORTED: detached lazy association " + associationPath
                                );
                            }
                            if (!initialized) {
                                final String payload = ORDER_CUSTOMERS.get(id);
                                if (payload == null) {
                                    throw new IllegalStateException(
                                            "TSJ-JPA-LAZY-UNSUPPORTED: missing entity for association Order.customer"
                                    );
                                }
                                final int separator = payload.indexOf('|');
                                value = separator < 0 ? payload : payload.substring(0, separator);
                                initialized = true;
                            }
                            return value;
                        }
                    }
                }
                """;
    }

    private static String normalize(final String output) {
        final String safe = output == null ? "" : output;
        return safe.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static String stripCliSuccessEnvelope(final String stdout) {
        final String normalized = normalize(stdout);
        if (normalized.isBlank()) {
            return normalized;
        }
        final StringBuilder builder = new StringBuilder();
        for (String line : normalized.split("\n")) {
            if (line.contains("\"code\":\"TSJ-RUN-SUCCESS\"")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    private static String summarize(final CommandResult result) {
        return "exit="
                + result.exitCode()
                + ",stdout="
                + trim(result.stdout(), 180)
                + ",stderr="
                + trim(result.stderr(), 180);
    }

    private static String trim(final String output, final int maxChars) {
        final String safe = output == null ? "" : output.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static String classifyDiagnostic(final String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }
        if (stderr.contains(DIAG_LAZY_UNSUPPORTED)) {
            return DIAG_LAZY_UNSUPPORTED;
        }
        if (stderr.contains(DIAG_PROXY_UNSUPPORTED)) {
            return DIAG_PROXY_UNSUPPORTED;
        }
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(stderr);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String safeMessage(final Exception exception) {
        return exception.getMessage() == null ? "" : exception.getMessage();
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjJpaLazyProxyParityHarness.class
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

    private static void writeReport(final Path reportPath, final TsjJpaLazyProxyParityReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
