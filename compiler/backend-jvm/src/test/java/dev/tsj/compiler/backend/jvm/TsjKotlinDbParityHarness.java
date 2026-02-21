package dev.tsj.compiler.backend.jvm;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-38a DB-backed reference parity harness.
 */
final class TsjKotlinDbParityHarness {
    private static final String REPORT_FILE = "tsj38a-db-parity-report.json";
    private static final String ORM_VERSION = "reference-db-1.0";
    private static final String WORKFLOW_CLASS = "sample.parity.db.OrderDbWorkflow";
    private static final String H2_DRIVER_CLASS = WORKFLOW_CLASS + "$InMemoryBackend";
    private static final String HSQL_DRIVER_CLASS = WORKFLOW_CLASS + "$FileBackend";
    private static final String DB_WIRING_CODE = "TSJ-ORM-DB-WIRING";
    private static final String QUERY_FAILURE_CODE = "TSJ-ORM-QUERY-FAILURE";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");

    TsjKotlinDbParityReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj38a-db-parity-work");
        Files.createDirectories(workRoot);

        final Path supportJar = buildSupportJar(workRoot.resolve("support"));
        final List<BackendConfig> backends = List.of(
                new BackendConfig("h2", H2_DRIVER_CLASS, "mem://tsj38a-h2", "embedded-mapdb-1.0"),
                new BackendConfig(
                        "hsqldb",
                        HSQL_DRIVER_CLASS,
                        workRoot.resolve("hsqldb-store.properties").toString(),
                        "embedded-filedb-1.0"
                )
        );

        final List<TsjKotlinDbParityReport.BackendResult> backendResults = new ArrayList<>();
        for (BackendConfig backend : backends) {
            backendResults.add(runBackendParityScenario(workRoot.resolve("backend-" + backend.backend()), supportJar, backend));
        }
        final List<TsjKotlinDbParityReport.DiagnosticScenarioResult> diagnostics = List.of(
                runDbWiringFailureScenario(workRoot.resolve("diag-db-wiring"), supportJar, backends.getFirst()),
                runQueryFailureScenario(workRoot.resolve("diag-query-failure"), supportJar, backends.getFirst())
        );

        final boolean gatePassed = backendResults.stream().allMatch(TsjKotlinDbParityReport.BackendResult::passed)
                && diagnostics.stream().allMatch(TsjKotlinDbParityReport.DiagnosticScenarioResult::passed);
        final TsjKotlinDbParityReport report = new TsjKotlinDbParityReport(
                gatePassed,
                backendResults,
                diagnostics,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjKotlinDbParityReport.BackendResult runBackendParityScenario(
            final Path workDir,
            final Path supportJar,
            final BackendConfig backend
    ) {
        try {
            Files.createDirectories(workDir);
            final ProgramRunResult tsRun = runTsWorkflow(workDir, supportJar, backend);
            final String tsOutput = normalizeOutput(tsRun.stdout());
            final String javaOutput = runReferenceWorkflow(supportJar, backend, "java");
            final String kotlinOutput = runReferenceWorkflow(supportJar, backend, "kotlin");
            final String diagnosticCode = classifyDiagnostic(tsRun.failure(), tsRun.stderr());
            final boolean passed = tsRun.failure() == null
                    && tsRun.stderr().isBlank()
                    && tsOutput.equals(javaOutput)
                    && tsOutput.equals(kotlinOutput);
            return new TsjKotlinDbParityReport.BackendResult(
                    backend.backend(),
                    ORM_VERSION,
                    backend.dbVersion(),
                    passed,
                    tsOutput,
                    javaOutput,
                    kotlinOutput,
                    diagnosticCode,
                    buildNotes(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjKotlinDbParityReport.BackendResult(
                    backend.backend(),
                    ORM_VERSION,
                    backend.dbVersion(),
                    false,
                    "",
                    "",
                    "",
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjKotlinDbParityReport.DiagnosticScenarioResult runDbWiringFailureScenario(
            final Path workDir,
            final Path supportJar,
            final BackendConfig backend
    ) {
        try {
            Files.createDirectories(workDir);
            final Path source = workDir.resolve("db-wiring-failure.ts");
            Files.writeString(
                    source,
                    """
                    import { init, reset } from "java:sample.parity.db.OrderDbWorkflow";

                    init("sample.parity.db.OrderDbWorkflow$MissingBackend", "%s");
                    reset();
                    """.formatted(backend.backendUrl()),
                    UTF_8
            );
            final ProgramRunResult run = compileAndRunTs(source, workDir.resolve("program"), supportJar);
            final String observed = classifyDiagnostic(run.failure(), run.stderr());
            final boolean passed = run.failure() != null && DB_WIRING_CODE.equals(observed);
            return new TsjKotlinDbParityReport.DiagnosticScenarioResult(
                    "db-wiring-failure",
                    passed,
                    DB_WIRING_CODE,
                    observed,
                    buildNotes(run)
            );
        } catch (final Exception exception) {
            return new TsjKotlinDbParityReport.DiagnosticScenarioResult(
                    "db-wiring-failure",
                    false,
                    DB_WIRING_CODE,
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjKotlinDbParityReport.DiagnosticScenarioResult runQueryFailureScenario(
            final Path workDir,
            final Path supportJar,
            final BackendConfig backend
    ) {
        try {
            Files.createDirectories(workDir);
            final Path source = workDir.resolve("query-failure.ts");
            Files.writeString(
                    source,
                    """
                    import { init, reset, save, brokenCountByStatus } from "java:sample.parity.db.OrderDbWorkflow";

                    init("%s", "%s");
                    reset();
                    save(201, "PAID", 35);
                    console.log(brokenCountByStatus("PAID"));
                    """.formatted(backend.driverClass(), backend.backendUrl()),
                    UTF_8
            );
            final ProgramRunResult run = compileAndRunTs(source, workDir.resolve("program"), supportJar);
            final String observed = classifyDiagnostic(run.failure(), run.stderr());
            final boolean passed = run.failure() != null && QUERY_FAILURE_CODE.equals(observed);
            return new TsjKotlinDbParityReport.DiagnosticScenarioResult(
                    "orm-query-failure",
                    passed,
                    QUERY_FAILURE_CODE,
                    observed,
                    buildNotes(run)
            );
        } catch (final Exception exception) {
            return new TsjKotlinDbParityReport.DiagnosticScenarioResult(
                    "orm-query-failure",
                    false,
                    QUERY_FAILURE_CODE,
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private ProgramRunResult runTsWorkflow(
            final Path workDir,
            final Path supportJar,
            final BackendConfig backend
    ) throws IOException {
        final Path source = workDir.resolve("workflow.ts");
        Files.writeString(
                source,
                """
                import { init, reset, begin, save, commit, rollback, findStatus, countByStatus, queryIdsByMinTotal } from "java:sample.parity.db.OrderDbWorkflow";

                init("%s", "%s");
                reset();
                begin();
                save(101, "PAID", 42);
                save(102, "PENDING", 10);
                commit();
                begin();
                save(103, "PAID", 65);
                rollback();
                console.log("status=" + findStatus(101));
                console.log("paid=" + countByStatus("PAID"));
                console.log("query=" + queryIdsByMinTotal(20));
                """.formatted(backend.driverClass(), backend.backendUrl()),
                UTF_8
        );
        return compileAndRunTs(source, workDir.resolve("program"), supportJar);
    }

    private ProgramRunResult compileAndRunTs(
            final Path tsFile,
            final Path outputDir,
            final Path supportJar
    ) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Throwable failure = null;
        try {
            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(tsFile, outputDir);
            new JvmBytecodeRunner().run(
                    artifact,
                    List.of(supportJar),
                    new PrintStream(stdout),
                    new PrintStream(stderr)
            );
        } catch (final Throwable throwable) {
            failure = throwable;
        }
        return new ProgramRunResult(stdout.toString(UTF_8), stderr.toString(UTF_8), failure);
    }

    private static String runReferenceWorkflow(
            final Path supportJar,
            final BackendConfig backend,
            final String mode
    ) throws Exception {
        final String backendUrl = backend.backendUrlForMode(mode);
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{supportJar.toUri().toURL()},
                TsjKotlinDbParityHarness.class.getClassLoader()
        )) {
            final Class<?> workflowClass = Class.forName(WORKFLOW_CLASS, true, classLoader);
            invokeStatic(workflowClass, "init", backend.driverClass(), backendUrl);
            invokeStatic(workflowClass, "reset");
            invokeStatic(workflowClass, "begin");
            invokeStatic(workflowClass, "save", 101L, "PAID", 42L);
            invokeStatic(workflowClass, "save", 102L, "PENDING", 10L);
            invokeStatic(workflowClass, "commit");
            invokeStatic(workflowClass, "begin");
            invokeStatic(workflowClass, "save", 103L, "PAID", 65L);
            invokeStatic(workflowClass, "rollback");
            final Object status = invokeStatic(workflowClass, "findStatus", 101L);
            final Object paid = invokeStatic(workflowClass, "countByStatus", "PAID");
            final Object query = invokeStatic(workflowClass, "queryIdsByMinTotal", 20L);
            return "status=" + status + "\npaid=" + paid + "\nquery=" + query;
        }
    }

    private static Object invokeStatic(final Class<?> type, final String name, final Object... args) throws Exception {
        Method matched = null;
        for (Method candidate : type.getMethods()) {
            if (!name.equals(candidate.getName()) || candidate.getParameterCount() != args.length) {
                continue;
            }
            matched = candidate;
            break;
        }
        if (matched == null) {
            throw new IllegalStateException("Missing method " + type.getName() + "#" + name + "/" + args.length);
        }
        try {
            return matched.invoke(null, args);
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            if (target instanceof Exception checked) {
                throw checked;
            }
            throw invocationTargetException;
        }
    }

    private static Path buildSupportJar(final Path workDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-38a DB parity fixtures.");
        }
        Files.createDirectories(workDir);
        final Path sourceRoot = workDir.resolve("src");
        final Path classesRoot = workDir.resolve("classes");
        final String className = WORKFLOW_CLASS;
        final Path javaFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(javaFile.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(javaFile, supportSource(), UTF_8);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(javaFile));
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-d",
                    classesRoot.toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed compiling TSJ-38a support fixture.");
            }
        }

        final Path jarPath = workDir.resolve("tsj38a-db-support.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> paths = Files.walk(classesRoot)) {
                final List<Path> classFiles = paths
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .sorted()
                        .toList();
                for (Path classFile : classFiles) {
                    final String entryName = classesRoot
                            .relativize(classFile)
                            .toString()
                            .replace(File.separatorChar, '/');
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
                package sample.parity.db;

                import java.io.IOException;
                import java.io.InputStream;
                import java.io.OutputStream;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.Properties;
                import java.util.stream.Collectors;

                public final class OrderDbWorkflow {
                    private static Store store;

                    private OrderDbWorkflow() {
                    }

                    public static synchronized void init(final String driverClass, final String backendUrl) {
                        try {
                            if (InMemoryBackend.class.getName().equals(driverClass)) {
                                store = new InMemoryBackend();
                            } else if (FileBackend.class.getName().equals(driverClass)) {
                                store = new FileBackend(backendUrl);
                            } else {
                                throw new IllegalStateException("Unknown backend driver " + driverClass);
                            }
                            store.ensureInitialized();
                        } catch (final Exception exception) {
                            throw new IllegalStateException("TSJ-ORM-DB-WIRING: " + exception.getMessage(), exception);
                        }
                    }

                    public static synchronized void reset() {
                        requireStore().reset();
                    }

                    public static synchronized void begin() {
                        requireStore().begin();
                    }

                    public static synchronized void commit() {
                        requireStore().commit();
                    }

                    public static synchronized void rollback() {
                        requireStore().rollback();
                    }

                    public static synchronized void save(final long id, final String status, final long total) {
                        requireStore().save(id, status, total);
                    }

                    public static synchronized String findStatus(final long id) {
                        return requireStore().findStatus(id);
                    }

                    public static synchronized int countByStatus(final String status) {
                        return requireStore().countByStatus(status);
                    }

                    public static synchronized String queryIdsByMinTotal(final long minimumTotal) {
                        return requireStore().queryIdsByMinTotal(minimumTotal);
                    }

                    public static synchronized int brokenCountByStatus(final String status) {
                        throw new IllegalStateException("TSJ-ORM-QUERY-FAILURE: broken query path for " + status);
                    }

                    private static Store requireStore() {
                        if (store == null) {
                            throw new IllegalStateException("TSJ-ORM-DB-WIRING: backend is not initialized");
                        }
                        return store;
                    }

                    private interface Store {
                        void ensureInitialized();

                        void reset();

                        void begin();

                        void save(long id, String status, long total);

                        void commit();

                        void rollback();

                        String findStatus(long id);

                        int countByStatus(String status);

                        String queryIdsByMinTotal(long minimumTotal);
                    }

                    private static final class Row {
                        private final String status;
                        private final long total;

                        Row(final String status, final long total) {
                            this.status = status;
                            this.total = total;
                        }
                    }

                    public static final class InMemoryBackend implements Store {
                        private final Map<Long, Row> committed = new LinkedHashMap<>();
                        private Map<Long, Row> staged = null;

                        @Override
                        public void ensureInitialized() {
                            reset();
                        }

                        @Override
                        public void reset() {
                            committed.clear();
                            staged = null;
                        }

                        @Override
                        public void begin() {
                            staged = new LinkedHashMap<>(committed);
                        }

                        @Override
                        public void save(final long id, final String status, final long total) {
                            active().put(id, new Row(status, total));
                        }

                        @Override
                        public void commit() {
                            if (staged == null) {
                                return;
                            }
                            committed.clear();
                            committed.putAll(staged);
                            staged = null;
                        }

                        @Override
                        public void rollback() {
                            staged = null;
                        }

                        @Override
                        public String findStatus(final long id) {
                            final Row row = committed.get(id);
                            return row == null ? null : row.status;
                        }

                        @Override
                        public int countByStatus(final String status) {
                            int count = 0;
                            for (Row row : committed.values()) {
                                if (status.equals(row.status)) {
                                    count++;
                                }
                            }
                            return count;
                        }

                        @Override
                        public String queryIdsByMinTotal(final long minimumTotal) {
                            final List<Long> matches = new ArrayList<>();
                            for (Map.Entry<Long, Row> entry : committed.entrySet()) {
                                if (entry.getValue().total >= minimumTotal) {
                                    matches.add(entry.getKey());
                                }
                            }
                            return matches.stream().map(String::valueOf).collect(Collectors.joining(","));
                        }

                        private Map<Long, Row> active() {
                            return staged == null ? committed : staged;
                        }
                    }

                    public static final class FileBackend implements Store {
                        private final Path path;
                        private final Map<Long, Row> committed = new LinkedHashMap<>();
                        private Map<Long, Row> staged = null;

                        FileBackend(final String filePath) {
                            path = Path.of(filePath);
                        }

                        @Override
                        public void ensureInitialized() {
                            try {
                                final Path parent = path.getParent();
                                if (parent != null) {
                                    Files.createDirectories(parent);
                                }
                                if (Files.exists(path)) {
                                    loadFromDisk();
                                } else {
                                    persistToDisk();
                                }
                                staged = null;
                            } catch (final IOException ioException) {
                                throw new IllegalStateException(ioException.getMessage(), ioException);
                            }
                        }

                        @Override
                        public void reset() {
                            committed.clear();
                            staged = null;
                            persistToDisk();
                        }

                        @Override
                        public void begin() {
                            staged = new LinkedHashMap<>(committed);
                        }

                        @Override
                        public void save(final long id, final String status, final long total) {
                            active().put(id, new Row(status, total));
                            if (staged == null) {
                                persistToDisk();
                            }
                        }

                        @Override
                        public void commit() {
                            if (staged == null) {
                                return;
                            }
                            committed.clear();
                            committed.putAll(staged);
                            staged = null;
                            persistToDisk();
                        }

                        @Override
                        public void rollback() {
                            staged = null;
                        }

                        @Override
                        public String findStatus(final long id) {
                            final Row row = committed.get(id);
                            return row == null ? null : row.status;
                        }

                        @Override
                        public int countByStatus(final String status) {
                            int count = 0;
                            for (Row row : committed.values()) {
                                if (status.equals(row.status)) {
                                    count++;
                                }
                            }
                            return count;
                        }

                        @Override
                        public String queryIdsByMinTotal(final long minimumTotal) {
                            final List<Long> matches = new ArrayList<>();
                            for (Map.Entry<Long, Row> entry : committed.entrySet()) {
                                if (entry.getValue().total >= minimumTotal) {
                                    matches.add(entry.getKey());
                                }
                            }
                            return matches.stream().map(String::valueOf).collect(Collectors.joining(","));
                        }

                        private Map<Long, Row> active() {
                            return staged == null ? committed : staged;
                        }

                        private void loadFromDisk() throws IOException {
                            committed.clear();
                            final Properties properties = new Properties();
                            try (InputStream inputStream = Files.newInputStream(path)) {
                                properties.load(inputStream);
                            }
                            for (String key : properties.stringPropertyNames()) {
                                final String value = properties.getProperty(key);
                                final String[] parts = value.split("\\\\|", 2);
                                final String status = parts[0];
                                final long total = parts.length == 2 ? Long.parseLong(parts[1]) : 0L;
                                committed.put(Long.parseLong(key), new Row(status, total));
                            }
                        }

                        private void persistToDisk() {
                            final Properties properties = new Properties();
                            for (Map.Entry<Long, Row> entry : committed.entrySet()) {
                                properties.setProperty(
                                        String.valueOf(entry.getKey()),
                                        entry.getValue().status + "|" + entry.getValue().total
                                );
                            }
                            try (OutputStream outputStream = Files.newOutputStream(path)) {
                                properties.store(outputStream, "tsj38a-file-backend");
                            } catch (final IOException ioException) {
                                throw new IllegalStateException(ioException.getMessage(), ioException);
                            }
                        }
                    }
                }
                """;
    }

    private static String normalizeOutput(final String text) {
        final String safe = text == null ? "" : text;
        return safe.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private static String classifyDiagnostic(final Throwable failure, final String stderr) {
        final StringBuilder builder = new StringBuilder();
        if (stderr != null) {
            builder.append(stderr).append('\n');
        }
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                builder.append(cursor.getMessage()).append('\n');
            }
            cursor = cursor.getCause();
        }
        final String combined = builder.toString();
        if (combined.contains(DB_WIRING_CODE)) {
            return DB_WIRING_CODE;
        }
        if (combined.contains(QUERY_FAILURE_CODE)) {
            return QUERY_FAILURE_CODE;
        }
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(combined);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String buildNotes(final ProgramRunResult run) {
        final String failureSummary = run.failure() == null
                ? ""
                : run.failure().getClass().getSimpleName() + ":" + safeMessage(run.failure());
        return "stdout="
                + trim(run.stdout(), 180)
                + ",stderr="
                + trim(run.stderr(), 180)
                + ",failure="
                + trim(failureSummary, 180);
    }

    private static String trim(final String value, final int maxChars) {
        final String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static String safeMessage(final Throwable throwable) {
        return throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage();
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjKotlinDbParityHarness.class.getProtectionDomain().getCodeSource().getLocation().toURI()
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

    private static void writeReport(final Path reportPath, final TsjKotlinDbParityReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record BackendConfig(String backend, String driverClass, String backendUrl, String dbVersion) {
        private String backendUrlForMode(final String mode) {
            if (backendUrl.startsWith("mem://")) {
                return backendUrl + "-" + mode;
            }
            final Path path = Path.of(backendUrl);
            final String fileName = path.getFileName() == null ? "db" : path.getFileName().toString();
            final String prefixed = mode + "-" + fileName;
            final Path parent = path.getParent();
            return parent == null ? prefixed : parent.resolve(prefixed).toString();
        }
    }

    private record ProgramRunResult(String stdout, String stderr, Throwable failure) {
    }
}
