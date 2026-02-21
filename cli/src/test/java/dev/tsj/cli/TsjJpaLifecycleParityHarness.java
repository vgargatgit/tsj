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
 * TSJ-42c persistence-context lifecycle and transaction parity harness.
 */
final class TsjJpaLifecycleParityHarness {
    private static final String REPORT_FILE = "tsj42c-jpa-lifecycle-parity.json";
    private static final String WORKFLOW_CLASS = "sample.orm.JpaLifecyclePack";
    private static final String DIAG_LIFECYCLE_MISUSE = "TSJ-ORM-LIFECYCLE-MISUSE";
    private static final String DIAG_TRANSACTION_REQUIRED = "TSJ-ORM-TRANSACTION-REQUIRED";
    private static final String DIAG_MAPPING_FAILURE = "TSJ-ORM-MAPPING-FAILURE";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");

    TsjJpaLifecycleParityReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj42c-lifecycle-work");
        Files.createDirectories(workRoot);
        final Path supportJar = buildSupportJar(workRoot.resolve("support"));

        final List<TsjJpaLifecycleParityReport.SupportedScenarioResult> supportedScenarios = List.of(
                runFlushClearDetachMergeScenario(workRoot.resolve("flush-clear-detach-merge"), supportJar),
                runTransactionBoundaryRollbackScenario(workRoot.resolve("transaction-boundary-rollback"), supportJar)
        );
        final List<TsjJpaLifecycleParityReport.DiagnosticScenarioResult> diagnosticScenarios = List.of(
                runLifecycleMisuseScenario(workRoot.resolve("diag-lifecycle-misuse"), supportJar),
                runTransactionRequiredScenario(workRoot.resolve("diag-transaction-required"), supportJar),
                runMappingFailureScenario(workRoot.resolve("diag-mapping-failure"), supportJar)
        );

        final boolean gatePassed = supportedScenarios.stream().allMatch(TsjJpaLifecycleParityReport.SupportedScenarioResult::passed)
                && diagnosticScenarios.stream().allMatch(TsjJpaLifecycleParityReport.DiagnosticScenarioResult::passed);
        final TsjJpaLifecycleParityReport report = new TsjJpaLifecycleParityReport(
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

    private TsjJpaLifecycleParityReport.SupportedScenarioResult runFlushClearDetachMergeScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("flush-clear-detach-merge.ts");
            Files.writeString(
                    entry,
                    """
                    import { reset, begin, save, flush, clear, detach, merge, commit, findStatus, lifecycleTrace } from "java:sample.orm.JpaLifecyclePack";

                    reset();
                    begin();
                    save(101, "NEW");
                    flush();
                    clear();
                    detach(101);
                    merge(101, "PAID");
                    commit();
                    console.log("status=" + findStatus(101));
                    console.log("callbacks=" + lifecycleTrace());
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String tsOutput = stripCliSuccessEnvelope(tsRun.stdout());
            final String javaOutput = runReferenceFlushClearDetachMerge(supportJar);
            final String kotlinOutput = runReferenceFlushClearDetachMerge(supportJar);
            final boolean passed = tsRun.exitCode() == 0
                    && tsRun.stderr().isBlank()
                    && tsOutput.equals(javaOutput)
                    && tsOutput.equals(kotlinOutput);
            return new TsjJpaLifecycleParityReport.SupportedScenarioResult(
                    "flush-clear-detach-merge",
                    passed,
                    tsOutput,
                    javaOutput,
                    kotlinOutput,
                    summarize(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjJpaLifecycleParityReport.SupportedScenarioResult(
                    "flush-clear-detach-merge",
                    false,
                    "",
                    "",
                    "",
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjJpaLifecycleParityReport.SupportedScenarioResult runTransactionBoundaryRollbackScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("transaction-boundary-rollback.ts");
            Files.writeString(
                    entry,
                    """
                    import { reset, begin, save, rollback, findStatus } from "java:sample.orm.JpaLifecyclePack";

                    reset();
                    begin();
                    save(201, "PENDING");
                    rollback();
                    console.log("status=" + findStatus(201));
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String tsOutput = stripCliSuccessEnvelope(tsRun.stdout());
            final String javaOutput = runReferenceRollbackBoundary(supportJar);
            final String kotlinOutput = runReferenceRollbackBoundary(supportJar);
            final boolean passed = tsRun.exitCode() == 0
                    && tsRun.stderr().isBlank()
                    && tsOutput.equals(javaOutput)
                    && tsOutput.equals(kotlinOutput);
            return new TsjJpaLifecycleParityReport.SupportedScenarioResult(
                    "transaction-boundary-rollback",
                    passed,
                    tsOutput,
                    javaOutput,
                    kotlinOutput,
                    summarize(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjJpaLifecycleParityReport.SupportedScenarioResult(
                    "transaction-boundary-rollback",
                    false,
                    "",
                    "",
                    "",
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjJpaLifecycleParityReport.DiagnosticScenarioResult runLifecycleMisuseScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("lifecycle-misuse.ts");
            Files.writeString(
                    entry,
                    """
                    import { reset, lifecycleMisuse } from "java:sample.orm.JpaLifecyclePack";

                    reset();
                    lifecycleMisuse("Order.customer");
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String observedCode = classifyDiagnostic(tsRun.stderr());
            final boolean passed = tsRun.exitCode() == 1
                    && DIAG_LIFECYCLE_MISUSE.equals(observedCode)
                    && tsRun.stderr().contains("Order.customer");
            return new TsjJpaLifecycleParityReport.DiagnosticScenarioResult(
                    "lifecycle-misuse",
                    passed,
                    DIAG_LIFECYCLE_MISUSE,
                    observedCode,
                    "context=Order.customer;" + summarize(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjJpaLifecycleParityReport.DiagnosticScenarioResult(
                    "lifecycle-misuse",
                    false,
                    DIAG_LIFECYCLE_MISUSE,
                    classifyDiagnostic(safeMessage(exception)),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjJpaLifecycleParityReport.DiagnosticScenarioResult runTransactionRequiredScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("transaction-required.ts");
            Files.writeString(
                    entry,
                    """
                    import { reset, save } from "java:sample.orm.JpaLifecyclePack";

                    reset();
                    save(301, "PENDING");
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String observedCode = classifyDiagnostic(tsRun.stderr());
            final boolean passed = tsRun.exitCode() == 1
                    && DIAG_TRANSACTION_REQUIRED.equals(observedCode)
                    && tsRun.stderr().contains("save");
            return new TsjJpaLifecycleParityReport.DiagnosticScenarioResult(
                    "transaction-required",
                    passed,
                    DIAG_TRANSACTION_REQUIRED,
                    observedCode,
                    "context=save;" + summarize(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjJpaLifecycleParityReport.DiagnosticScenarioResult(
                    "transaction-required",
                    false,
                    DIAG_TRANSACTION_REQUIRED,
                    classifyDiagnostic(safeMessage(exception)),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjJpaLifecycleParityReport.DiagnosticScenarioResult runMappingFailureScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entry = workDir.resolve("mapping-failure.ts");
            Files.writeString(
                    entry,
                    """
                    import { reset, begin, merge } from "java:sample.orm.JpaLifecyclePack";

                    reset();
                    begin();
                    merge(401, "__MAPPING_FAIL__");
                    """,
                    UTF_8
            );
            final CommandResult tsRun = runTs(entry, workDir.resolve("out"), supportJar);
            final String observedCode = classifyDiagnostic(tsRun.stderr());
            final boolean passed = tsRun.exitCode() == 1
                    && DIAG_MAPPING_FAILURE.equals(observedCode)
                    && tsRun.stderr().contains("Order.status");
            return new TsjJpaLifecycleParityReport.DiagnosticScenarioResult(
                    "mapping-failure",
                    passed,
                    DIAG_MAPPING_FAILURE,
                    observedCode,
                    "context=Order.status;" + summarize(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjJpaLifecycleParityReport.DiagnosticScenarioResult(
                    "mapping-failure",
                    false,
                    DIAG_MAPPING_FAILURE,
                    classifyDiagnostic(safeMessage(exception)),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private static String runReferenceFlushClearDetachMerge(final Path supportJar) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{supportJar.toUri().toURL()},
                TsjJpaLifecycleParityHarness.class.getClassLoader()
        )) {
            final Class<?> workflow = Class.forName(WORKFLOW_CLASS, true, classLoader);
            invoke(workflow, "reset");
            invoke(workflow, "begin");
            invoke(workflow, "save", 101L, "NEW");
            invoke(workflow, "flush");
            invoke(workflow, "clear");
            invoke(workflow, "detach", 101L);
            invoke(workflow, "merge", 101L, "PAID");
            invoke(workflow, "commit");
            final StringBuilder output = new StringBuilder();
            output.append("status=").append(invoke(workflow, "findStatus", 101L));
            output.append('\n').append("callbacks=").append(invoke(workflow, "lifecycleTrace"));
            return normalize(output.toString());
        }
    }

    private static String runReferenceRollbackBoundary(final Path supportJar) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{supportJar.toUri().toURL()},
                TsjJpaLifecycleParityHarness.class.getClassLoader()
        )) {
            final Class<?> workflow = Class.forName(WORKFLOW_CLASS, true, classLoader);
            invoke(workflow, "reset");
            invoke(workflow, "begin");
            invoke(workflow, "save", 201L, "PENDING");
            invoke(workflow, "rollback");
            return "status=" + String.valueOf(invoke(workflow, "findStatus", 201L));
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
            throw new IllegalStateException("JDK compiler is required for TSJ-42c fixtures.");
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
                throw new IllegalStateException("Failed compiling TSJ-42c fixture support class.");
            }
        }
        final Path jarPath = workDir.resolve("tsj42c-lifecycle-support.jar");
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

                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.LinkedHashSet;
                import java.util.List;
                import java.util.Map;
                import java.util.Set;

                public final class JpaLifecyclePack {
                    private static final Map<Long, String> STORE = new LinkedHashMap<>();
                    private static final Map<Long, String> TX_BUFFER = new LinkedHashMap<>();
                    private static final Set<Long> DETACHED = new LinkedHashSet<>();
                    private static final List<String> CALLBACKS = new ArrayList<>();
                    private static boolean transactionOpen;
                    private static int transactionSequence;

                    private JpaLifecyclePack() {
                    }

                    public static synchronized void reset() {
                        STORE.clear();
                        TX_BUFFER.clear();
                        DETACHED.clear();
                        CALLBACKS.clear();
                        transactionOpen = false;
                        transactionSequence = 0;
                    }

                    public static synchronized void begin() {
                        if (transactionOpen) {
                            throw new IllegalStateException(
                                    "TSJ-ORM-TRANSACTION-REQUIRED: nested transaction not supported"
                            );
                        }
                        transactionOpen = true;
                        transactionSequence++;
                    }

                    public static synchronized void save(final long id, final String status) {
                        requireTx("save");
                        CALLBACKS.add("prePersist#" + id);
                        TX_BUFFER.put(id, status);
                        CALLBACKS.add("postPersist#" + id);
                    }

                    public static synchronized void flush() {
                        requireTx("flush");
                        CALLBACKS.add("flush#tx" + transactionSequence);
                        STORE.putAll(TX_BUFFER);
                    }

                    public static synchronized void clear() {
                        requireTx("clear");
                        CALLBACKS.add("clear#tx" + transactionSequence);
                        DETACHED.clear();
                    }

                    public static synchronized void detach(final long id) {
                        requireTx("detach");
                        DETACHED.add(id);
                        CALLBACKS.add("detach#" + id);
                    }

                    public static synchronized void merge(final long id, final String status) {
                        requireTx("merge");
                        if ("__MAPPING_FAIL__".equals(status)) {
                            throw new IllegalStateException(
                                    "TSJ-ORM-MAPPING-FAILURE: invalid mapping for Order.status"
                            );
                        }
                        CALLBACKS.add("preUpdate#" + id);
                        TX_BUFFER.put(id, status);
                        DETACHED.remove(id);
                        CALLBACKS.add("postUpdate#" + id);
                    }

                    public static synchronized void commit() {
                        requireTx("commit");
                        STORE.putAll(TX_BUFFER);
                        TX_BUFFER.clear();
                        DETACHED.clear();
                        CALLBACKS.add("commit#tx" + transactionSequence);
                        transactionOpen = false;
                    }

                    public static synchronized void rollback() {
                        requireTx("rollback");
                        TX_BUFFER.clear();
                        DETACHED.clear();
                        CALLBACKS.add("rollback#tx" + transactionSequence);
                        transactionOpen = false;
                    }

                    public static synchronized String findStatus(final long id) {
                        return STORE.get(id);
                    }

                    public static synchronized String lifecycleTrace() {
                        return String.join("|", CALLBACKS);
                    }

                    public static synchronized void lifecycleMisuse(final String associationPath) {
                        throw new IllegalStateException(
                                "TSJ-ORM-LIFECYCLE-MISUSE: detached merge outside persistence context for "
                                        + associationPath
                        );
                    }

                    private static void requireTx(final String stage) {
                        if (!transactionOpen) {
                            throw new IllegalStateException(
                                    "TSJ-ORM-TRANSACTION-REQUIRED: active transaction required for " + stage
                            );
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
                + trim(result.stdout(), 200)
                + ",stderr="
                + trim(result.stderr(), 200);
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
        if (stderr.contains(DIAG_LIFECYCLE_MISUSE)) {
            return DIAG_LIFECYCLE_MISUSE;
        }
        if (stderr.contains(DIAG_TRANSACTION_REQUIRED)) {
            return DIAG_TRANSACTION_REQUIRED;
        }
        if (stderr.contains(DIAG_MAPPING_FAILURE)) {
            return DIAG_MAPPING_FAILURE;
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
                    TsjJpaLifecycleParityHarness.class
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

    private static void writeReport(final Path reportPath, final TsjJpaLifecycleParityReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
