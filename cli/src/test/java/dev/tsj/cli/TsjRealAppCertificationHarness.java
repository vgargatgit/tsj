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
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-44c real-application certification harness.
 */
final class TsjRealAppCertificationHarness {
    private static final String REPORT_FILE = "tsj44c-real-app-certification.json";
    private static final String FIXTURE_VERSION = "tsj44c-real-app-2026.02";
    private static final long MAX_AVERAGE_DURATION_MS = 12_000L;
    private static final long MAX_WORKLOAD_DURATION_MS = 20_000L;

    TsjRealAppCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj44c-real-app-work");
        Files.createDirectories(workRoot);

        final Path supportJar = buildSupportJar(workRoot.resolve("support"));
        final List<TsjRealAppCertificationReport.WorkloadResult> workloads = new ArrayList<>();
        workloads.add(runWorkload(
                workRoot,
                supportJar,
                "orders-batch",
                """
                import { reset, ingestOrder, closeBatch, summary } from "java:real.app.RealAppWorkflow";
                reset();
                ingestOrder(1, 42, "alice");
                ingestOrder(2, 33, "bob");
                ingestOrder(3, 42, "carol");
                closeBatch("B1");
                console.log(summary());
                """,
                "batch=B1,count=3,total=117",
                "order-ingestion-and-batch-close pipeline"
        ));
        workloads.add(runWorkload(
                workRoot,
                supportJar,
                "analytics-pipeline",
                """
                import { reset, ingestOrder, runAnalytics } from "java:real.app.RealAppWorkflow";
                reset();
                ingestOrder(10, 42, "alice");
                ingestOrder(11, 33, "bob");
                ingestOrder(12, 60, "carol");
                console.log(runAnalytics(40));
                """,
                "analytics=2,102",
                "aggregation-and-threshold filter path"
        ));

        final boolean reliabilityBudgetPassed = workloads.stream().allMatch(TsjRealAppCertificationReport.WorkloadResult::passed);
        final long totalDurationMs = workloads.stream().mapToLong(TsjRealAppCertificationReport.WorkloadResult::durationMs).sum();
        final long averageDurationMs = workloads.isEmpty() ? 0L : totalDurationMs / workloads.size();
        final long maxDurationMs = workloads.stream().mapToLong(TsjRealAppCertificationReport.WorkloadResult::durationMs).max().orElse(0L);
        final boolean performanceBudgetPassed = averageDurationMs <= MAX_AVERAGE_DURATION_MS
                && maxDurationMs <= MAX_WORKLOAD_DURATION_MS;
        final boolean gatePassed = reliabilityBudgetPassed && performanceBudgetPassed;

        final TsjRealAppCertificationReport report = new TsjRealAppCertificationReport(
                gatePassed,
                reliabilityBudgetPassed,
                performanceBudgetPassed,
                MAX_AVERAGE_DURATION_MS,
                MAX_WORKLOAD_DURATION_MS,
                FIXTURE_VERSION,
                workloads,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjRealAppCertificationReport.WorkloadResult runWorkload(
            final Path workRoot,
            final Path supportJar,
            final String workload,
            final String sourceText,
            final String expectedMarker,
            final String bottleneckHint
    ) throws IOException {
        final Path workloadDir = workRoot.resolve(workload);
        Files.createDirectories(workloadDir);
        final Path entryFile = workloadDir.resolve("main.ts");
        Files.writeString(entryFile, sourceText, UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final long started = System.nanoTime();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        workloadDir.resolve("out").toString(),
                        "--jar",
                        supportJar.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        final long durationMs = (System.nanoTime() - started) / 1_000_000L;
        final String stdoutText = stdout.toString(UTF_8);
        final String stderrText = stderr.toString(UTF_8);
        final boolean passed = exitCode == 0 && stdoutText.contains(expectedMarker);

        final Path traceDir = workRoot.resolve("traces");
        Files.createDirectories(traceDir);
        final Path traceFile = traceDir.resolve(workload + ".log");
        Files.writeString(
                traceFile,
                "workload=" + workload + "\n"
                        + "durationMs=" + durationMs + "\n"
                        + "exitCode=" + exitCode + "\n"
                        + "expectedMarker=" + expectedMarker + "\n"
                        + "stdout=" + trim(stdoutText, 6_000) + "\n"
                        + "stderr=" + trim(stderrText, 6_000) + "\n",
                UTF_8
        );

        final String notes = "durationMs=" + durationMs
                + ",exit=" + exitCode
                + ",stdout=" + trim(stdoutText, 180)
                + ",stderr=" + trim(stderrText, 180);
        return new TsjRealAppCertificationReport.WorkloadResult(
                workload,
                passed,
                durationMs,
                traceFile.toAbsolutePath().normalize().toString(),
                bottleneckHint,
                notes
        );
    }

    private static Path buildSupportJar(final Path workDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-44c real-app certification.");
        }
        Files.createDirectories(workDir);
        final Path sourceRoot = workDir.resolve("src");
        final Path classesRoot = workDir.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classesRoot);

        final List<SourceUnit> sources = List.of(
                new SourceUnit(
                        "real.app.OrderRepository",
                        """
                        package real.app;

                        import java.util.LinkedHashMap;
                        import java.util.Map;

                        final class OrderRepository {
                            private final Map<Long, Long> totals = new LinkedHashMap<>();
                            private String batchId = "NONE";

                            void reset() {
                                totals.clear();
                                batchId = "NONE";
                            }

                            void save(final long id, final long total) {
                                totals.put(id, total);
                            }

                            void closeBatch(final String id) {
                                batchId = id;
                            }

                            String batchId() {
                                return batchId;
                            }

                            int count() {
                                return totals.size();
                            }

                            long total() {
                                long total = 0L;
                                for (Long value : totals.values()) {
                                    total += value;
                                }
                                return total;
                            }

                            Map<Long, Long> snapshot() {
                                return new LinkedHashMap<>(totals);
                            }
                        }
                        """
                ),
                new SourceUnit(
                        "real.app.AnalyticsEngine",
                        """
                        package real.app;

                        import java.util.Map;

                        final class AnalyticsEngine {
                            private final OrderRepository repository;

                            AnalyticsEngine(final OrderRepository repository) {
                                this.repository = repository;
                            }

                            String run(final long minimumTotal) {
                                int count = 0;
                                long sum = 0L;
                                for (Map.Entry<Long, Long> entry : repository.snapshot().entrySet()) {
                                    if (entry.getValue() >= minimumTotal) {
                                        count++;
                                        sum += entry.getValue();
                                    }
                                }
                                return "analytics=" + count + "," + sum;
                            }
                        }
                        """
                ),
                new SourceUnit(
                        "real.app.RealAppWorkflow",
                        """
                        package real.app;

                        public final class RealAppWorkflow {
                            private static final OrderRepository REPOSITORY = new OrderRepository();
                            private static final AnalyticsEngine ANALYTICS = new AnalyticsEngine(REPOSITORY);

                            private RealAppWorkflow() {
                            }

                            public static synchronized void reset() {
                                REPOSITORY.reset();
                            }

                            public static synchronized void ingestOrder(final long id, final long total, final String customer) {
                                REPOSITORY.save(id, total);
                            }

                            public static synchronized void closeBatch(final String id) {
                                REPOSITORY.closeBatch(id);
                            }

                            public static synchronized String summary() {
                                return "batch=" + REPOSITORY.batchId()
                                        + ",count=" + REPOSITORY.count()
                                        + ",total=" + REPOSITORY.total();
                            }

                            public static synchronized String runAnalytics(final long minimumTotal) {
                                return ANALYTICS.run(minimumTotal);
                            }
                        }
                        """
                )
        );

        final List<Path> sourceFiles = new ArrayList<>();
        for (SourceUnit source : sources) {
            final Path javaFile = sourceRoot.resolve(source.className().replace('.', '/') + ".java");
            Files.createDirectories(javaFile.getParent());
            Files.writeString(javaFile, source.sourceText(), UTF_8);
            sourceFiles.add(javaFile);
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final List<String> options = List.of("--release", "21", "-d", classesRoot.toString());
            final Boolean success = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed compiling TSJ-44c fixture support classes.");
            }
        }

        final Path jarPath = workDir.resolve("tsj44c-real-app-support.jar");
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

    private static String trim(final String text, final int maxChars) {
        final String safe = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjRealAppCertificationHarness.class
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

    private static void writeReport(final Path reportPath, final TsjRealAppCertificationReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record SourceUnit(String className, String sourceText) {
    }
}
