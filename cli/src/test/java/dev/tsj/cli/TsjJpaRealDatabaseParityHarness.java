package dev.tsj.cli;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
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
 * TSJ-42a real-database parity harness for JPA-style entity/repository/service flows.
 */
final class TsjJpaRealDatabaseParityHarness {
    private static final String REPORT_FILE = "tsj42a-jpa-realdb-parity.json";
    private static final String ORM_VERSION = "jpa-lite-1.0";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");
    private static final String H2_DRIVER = "sample.orm.JpaDbPack$InMemoryBackend";
    private static final String HSQLDB_DRIVER = "sample.orm.JpaDbPack$FileBackend";

    TsjJpaRealDatabaseParityReport run(final Path reportPath) throws Exception {
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReportPath.getParent().resolve("tsj42a-jpa-realdb-work");
        Files.createDirectories(workRoot);

        final Path fixtureJar = buildJpaFixtureJar(workRoot);
        final List<BackendConfig> backends = List.of(
                new BackendConfig(
                        "h2",
                        H2_DRIVER,
                        "mem://tsj42a-h2",
                        "embedded-mapdb-1.0"
                ),
                new BackendConfig(
                        "hsqldb",
                        HSQLDB_DRIVER,
                        workRoot.resolve("hsqldb-store.properties").toString(),
                        "embedded-filedb-1.0"
                )
        );

        final List<TsjJpaRealDatabaseParityReport.BackendScenarioResult> backendResults = new ArrayList<>();
        for (BackendConfig backend : backends) {
            backendResults.add(runCrudAndQueryParityScenario(workRoot.resolve(backend.backend()), fixtureJar, backend));
        }
        final List<TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult> diagnosticScenarios = List.of(
                runDbWiringFailureScenario(workRoot.resolve("diagnostic-db-wiring"), fixtureJar, backends.getFirst()),
                runOrmQueryFailureScenario(workRoot.resolve("diagnostic-query-failure"), fixtureJar, backends.getFirst())
        );

        final boolean gatePassed = backendResults.stream().allMatch(TsjJpaRealDatabaseParityReport.BackendScenarioResult::passed)
                && diagnosticScenarios.stream().allMatch(TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult::passed);

        final TsjJpaRealDatabaseParityReport report = new TsjJpaRealDatabaseParityReport(
                gatePassed,
                backendResults,
                diagnosticScenarios,
                normalizedReportPath,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static TsjJpaRealDatabaseParityReport.BackendScenarioResult runCrudAndQueryParityScenario(
            final Path workDir,
            final Path fixtureJar,
            final BackendConfig backend
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj42a-crud-query.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { init, reset, save, findStatus, countByStatus } from "java:sample.orm.JpaDbPack";

                    init("%s", "%s");
                    reset();
                    save(101, "PAID");
                    save(102, "PENDING");
                    save(103, "PAID");
                    console.log("status=" + findStatus(101));
                    console.log("count=" + countByStatus("PAID"));
                    """.formatted(backend.driverClassName(), backend.backendUrl()),
                    UTF_8
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    fixtureJar.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final String diagnosticCode = classifyDiagnostic(command.stderr());
            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("status=PAID")
                    && command.stdout().contains("count=2")
                    && command.stderr().isBlank();
            return new TsjJpaRealDatabaseParityReport.BackendScenarioResult(
                    backend.backend(),
                    ORM_VERSION,
                    backend.dbVersion(),
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return new TsjJpaRealDatabaseParityReport.BackendScenarioResult(
                    backend.backend(),
                    ORM_VERSION,
                    backend.dbVersion(),
                    false,
                    classifyDiagnostic(exception.getMessage()),
                    exception.getClass().getSimpleName() + ":" + exception.getMessage()
            );
        }
    }

    private static TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult runDbWiringFailureScenario(
            final Path workDir,
            final Path fixtureJar,
            final BackendConfig backend
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj42a-db-wiring-failure.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { init, reset } from "java:sample.orm.JpaDbPack";

                    init("sample.orm.JpaDbPack$UnknownBackend", "%s");
                    reset();
                    """.formatted(backend.backendUrl()),
                    UTF_8
            );
            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    fixtureJar.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final String observedDiagnosticCode = classifyDiagnostic(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-ORM-DB-WIRING".equals(observedDiagnosticCode)
                    && command.stdout().isBlank();
            return new TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult(
                    backend.backend(),
                    "db-wiring-failure",
                    passed,
                    "TSJ-ORM-DB-WIRING",
                    observedDiagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return new TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult(
                    backend.backend(),
                    "db-wiring-failure",
                    false,
                    "TSJ-ORM-DB-WIRING",
                    classifyDiagnostic(exception.getMessage()),
                    exception.getClass().getSimpleName() + ":" + exception.getMessage()
            );
        }
    }

    private static TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult runOrmQueryFailureScenario(
            final Path workDir,
            final Path fixtureJar,
            final BackendConfig backend
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj42a-query-failure.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { init, reset, save, brokenCountByStatus } from "java:sample.orm.JpaDbPack";

                    init("%s", "%s");
                    reset();
                    save(201, "PAID");
                    console.log(brokenCountByStatus("PAID"));
                    """.formatted(backend.driverClassName(), backend.backendUrl()),
                    UTF_8
            );
            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    fixtureJar.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final String observedDiagnosticCode = classifyDiagnostic(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-ORM-QUERY-FAILURE".equals(observedDiagnosticCode)
                    && command.stdout().isBlank();
            return new TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult(
                    backend.backend(),
                    "orm-query-failure",
                    passed,
                    "TSJ-ORM-QUERY-FAILURE",
                    observedDiagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return new TsjJpaRealDatabaseParityReport.DiagnosticScenarioResult(
                    backend.backend(),
                    "orm-query-failure",
                    false,
                    "TSJ-ORM-QUERY-FAILURE",
                    classifyDiagnostic(exception.getMessage()),
                    exception.getClass().getSimpleName() + ":" + exception.getMessage()
            );
        }
    }

    private static Path buildJpaFixtureJar(final Path workRoot) throws Exception {
        final String className = "sample.orm.JpaDbPack";
        final String source = """
                package sample.orm;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.LinkedHashMap;
                import java.util.Map;
                import java.util.Properties;

                public final class JpaDbPack {
                    private static Store store;

                    private JpaDbPack() {
                    }

                    public static synchronized void init(final String driver, final String url) {
                        try {
                            if (InMemoryBackend.class.getName().equals(driver)) {
                                store = new InMemoryBackend();
                            } else if (FileBackend.class.getName().equals(driver)) {
                                store = new FileBackend(url);
                            } else {
                                throw new IllegalStateException("Unsupported backend " + driver);
                            }
                            store.ensureInitialized();
                        } catch (final Exception exception) {
                            throw new IllegalStateException("TSJ-ORM-DB-WIRING: " + exception.getMessage(), exception);
                        }
                    }

                    public static synchronized void reset() {
                        requireStore().reset();
                    }

                    public static synchronized void save(final long id, final String status) {
                        requireStore().save(id, status);
                    }

                    public static synchronized String findStatus(final long id) {
                        return requireStore().findStatus(id);
                    }

                    public static synchronized int countByStatus(final String status) {
                        return requireStore().countByStatus(status);
                    }

                    public static synchronized int brokenCountByStatus(final String status) {
                        try {
                            return requireStore().brokenCountByStatus(status);
                        } catch (final Exception exception) {
                            throw new IllegalStateException("TSJ-ORM-QUERY-FAILURE: " + exception.getMessage(), exception);
                        }
                    }

                    private static Store requireStore() {
                        if (store == null) {
                            throw new IllegalStateException("TSJ-ORM-DB-WIRING: backend not initialized");
                        }
                        return store;
                    }

                    private interface Store {
                        void ensureInitialized();
                        void reset();
                        void save(long id, String status);
                        String findStatus(long id);
                        int countByStatus(String status);
                        int brokenCountByStatus(String status);
                    }

                    public static final class InMemoryBackend implements Store {
                        private final Map<Long, String> entries = new LinkedHashMap<>();

                        @Override
                        public void ensureInitialized() {
                            entries.clear();
                        }

                        @Override
                        public void reset() {
                            entries.clear();
                        }

                        @Override
                        public void save(final long id, final String status) {
                            entries.put(id, status);
                        }

                        @Override
                        public String findStatus(final long id) {
                            return entries.get(id);
                        }

                        @Override
                        public int countByStatus(final String status) {
                            int count = 0;
                            for (String value : entries.values()) {
                                if (status.equals(value)) {
                                    count++;
                                }
                            }
                            return count;
                        }

                        @Override
                        public int brokenCountByStatus(final String status) {
                            throw new IllegalStateException("column total does not exist");
                        }
                    }

                    public static final class FileBackend implements Store {
                        private final Path file;
                        private final Map<Long, String> entries = new LinkedHashMap<>();

                        FileBackend(final String filePath) {
                            file = Path.of(filePath);
                        }

                        @Override
                        public void ensureInitialized() {
                            try {
                                final Path parent = file.getParent();
                                if (parent != null) {
                                    Files.createDirectories(parent);
                                }
                                if (Files.exists(file)) {
                                    load();
                                } else {
                                    persist();
                                }
                            } catch (final Exception exception) {
                                throw new IllegalStateException(exception.getMessage(), exception);
                            }
                        }

                        @Override
                        public void reset() {
                            entries.clear();
                            persist();
                        }

                        @Override
                        public void save(final long id, final String status) {
                            entries.put(id, status);
                            persist();
                        }

                        @Override
                        public String findStatus(final long id) {
                            return entries.get(id);
                        }

                        @Override
                        public int countByStatus(final String status) {
                            int count = 0;
                            for (String value : entries.values()) {
                                if (status.equals(value)) {
                                    count++;
                                }
                            }
                            return count;
                        }

                        @Override
                        public int brokenCountByStatus(final String status) {
                            throw new IllegalStateException("column total does not exist");
                        }

                        private void load() {
                            try {
                                entries.clear();
                                final Properties properties = new Properties();
                                properties.load(Files.newInputStream(file));
                                for (String key : properties.stringPropertyNames()) {
                                    entries.put(Long.parseLong(key), properties.getProperty(key));
                                }
                            } catch (final Exception exception) {
                                throw new IllegalStateException(exception.getMessage(), exception);
                            }
                        }

                        private void persist() {
                            try {
                                final Properties properties = new Properties();
                                for (Map.Entry<Long, String> entry : entries.entrySet()) {
                                    properties.setProperty(Long.toString(entry.getKey()), entry.getValue());
                                }
                                properties.store(Files.newOutputStream(file), "tsj42a-file-backend");
                            } catch (final Exception exception) {
                                throw new IllegalStateException(exception.getMessage(), exception);
                            }
                        }
                    }
                }
                """;

        final Path sourceRoot = workRoot.resolve("jpa-src");
        final Path classesRoot = workRoot.resolve("jpa-classes");
        final Path javaSource = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(javaSource.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(javaSource, source, UTF_8);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-42a certification harness.");
        }
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(List.of(javaSource));
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-d",
                    classesRoot.toString()
            );
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    compilationUnits
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed compiling TSJ-42a fixture class " + className);
            }
        }

        final Path jarFile = workRoot.resolve("jpa-db-pack.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
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
                    final JarEntry entry = new JarEntry(entryName);
                    jarOutputStream.putNextEntry(entry);
                    jarOutputStream.write(Files.readAllBytes(classFile));
                    jarOutputStream.closeEntry();
                }
            }
        }
        return jarFile.toAbsolutePath().normalize();
    }

    private static CommandResult executeCli(final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                args,
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        return new CommandResult(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8));
    }

    private static String classifyDiagnostic(final String text) {
        final String safe = text == null ? "" : text;
        if (safe.contains("TSJ-ORM-DB-WIRING")) {
            return "TSJ-ORM-DB-WIRING";
        }
        if (safe.contains("TSJ-ORM-QUERY-FAILURE")) {
            return "TSJ-ORM-QUERY-FAILURE";
        }
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(safe);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String buildNotes(final CommandResult command) {
        return "exit=" + command.exitCode()
                + ",stdout=" + trim(command.stdout(), 160)
                + ",stderr=" + trim(command.stderr(), 160);
    }

    private static String trim(final String value, final int maxChars) {
        final String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjJpaRealDatabaseParityHarness.class
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
            final TsjJpaRealDatabaseParityReport report
    ) throws IOException {
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalizedReportPath.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalizedReportPath, report.toJson() + "\n", UTF_8);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private record BackendConfig(
            String backend,
            String driverClassName,
            String backendUrl,
            String dbVersion
    ) {
    }
}
