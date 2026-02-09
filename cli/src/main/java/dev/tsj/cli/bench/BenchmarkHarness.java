package dev.tsj.cli.bench;

import dev.tsj.compiler.backend.jvm.JvmBytecodeCompiler;
import dev.tsj.compiler.backend.jvm.JvmBytecodeRunner;
import dev.tsj.compiler.backend.jvm.JvmCompiledArtifact;
import dev.tsj.compiler.backend.jvm.JvmOptimizationOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-18 benchmark harness for micro/macro workload timing and baseline report generation.
 */
public final class BenchmarkHarness {
    public BenchmarkReport run(final Path reportPath) {
        return run(reportPath, BenchmarkOptions.defaults());
    }

    public BenchmarkReport run(final Path reportPath, final BenchmarkOptions options) {
        Objects.requireNonNull(reportPath, "reportPath");
        Objects.requireNonNull(options, "options");

        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path parent = normalizedReportPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (final IOException ioException) {
                throw new IllegalArgumentException(
                        "Failed to create benchmark report directory: " + ioException.getMessage(),
                        ioException
                );
            }
        }

        final Path workspace;
        try {
            workspace = Files.createTempDirectory("tsj-bench-");
        } catch (final IOException ioException) {
            throw new IllegalStateException("Failed to create benchmark workspace: " + ioException.getMessage(), ioException);
        }

        try {
            final List<BenchmarkWorkload> workloads = workloadSuite(options.profile());
            final List<BenchmarkResult> results = new ArrayList<>();
            for (BenchmarkWorkload workload : workloads) {
                results.add(runWorkload(workspace, workload, options));
            }
            final BenchmarkSummary summary = summarize(results);
            final BenchmarkReport report = new BenchmarkReport(
                    "1.0",
                    Instant.now().toString(),
                    options,
                    environmentMetadata(),
                    List.copyOf(results),
                    summary
            );

            Files.writeString(normalizedReportPath, report.toJson(), UTF_8);
            return report;
        } catch (final IOException ioException) {
            throw new IllegalStateException("Failed to write benchmark report: " + ioException.getMessage(), ioException);
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static Map<String, String> environmentMetadata() {
        return Map.of(
                "javaVersion", System.getProperty("java.version", "unknown"),
                "osName", System.getProperty("os.name", "unknown"),
                "osArch", System.getProperty("os.arch", "unknown"),
                "availableProcessors", Integer.toString(Runtime.getRuntime().availableProcessors())
        );
    }

    private static BenchmarkSummary summarize(final List<BenchmarkResult> results) {
        int microCount = 0;
        int macroCount = 0;
        long maxPeakMemoryBytes = 0L;
        double compileTotalMs = 0.0d;
        double runTotalMs = 0.0d;
        double throughputTotal = 0.0d;

        for (BenchmarkResult result : results) {
            if (result.category() == BenchmarkCategory.MICRO) {
                microCount++;
            } else {
                macroCount++;
            }
            compileTotalMs += result.compileAvgMs();
            runTotalMs += result.runAvgMs();
            throughputTotal += result.throughputOpsPerSec();
            maxPeakMemoryBytes = Math.max(maxPeakMemoryBytes, result.peakMemoryBytes());
        }

        final int total = results.size();
        final double avgCompileMs = total == 0 ? 0.0d : compileTotalMs / total;
        final double avgRunMs = total == 0 ? 0.0d : runTotalMs / total;
        final double avgThroughput = total == 0 ? 0.0d : throughputTotal / total;
        return new BenchmarkSummary(total, microCount, macroCount, avgCompileMs, avgRunMs, avgThroughput, maxPeakMemoryBytes);
    }

    private static BenchmarkResult runWorkload(
            final Path workspace,
            final BenchmarkWorkload workload,
            final BenchmarkOptions options
    ) throws IOException {
        final Path workloadRoot = workspace.resolve(workload.id());
        Files.createDirectories(workloadRoot);
        writeSources(workloadRoot, workload.sources());

        final Path entryFile = workloadRoot.resolve(workload.entryFile()).toAbsolutePath().normalize();
        final JvmBytecodeCompiler compiler = new JvmBytecodeCompiler();
        final JvmBytecodeRunner runner = new JvmBytecodeRunner();
        final List<Long> compileSamples = new ArrayList<>();
        final List<Long> runSamples = new ArrayList<>();
        long peakMemoryBytes = 0L;
        final int totalIterations = options.warmupIterations() + options.measuredIterations();

        for (int iteration = 0; iteration < totalIterations; iteration++) {
            final Path outDir = workloadRoot.resolve("out-" + iteration);
            final long beforeMemory = usedMemoryBytes();

            final long compileStart = System.nanoTime();
            final JvmCompiledArtifact artifact = compiler.compile(entryFile, outDir, options.optimizationOptions());
            final long compileNanos = System.nanoTime() - compileStart;

            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            final long runStart = System.nanoTime();
            runner.run(artifact, new PrintStream(stdout), new PrintStream(stderr));
            final long runNanos = System.nanoTime() - runStart;

            final String runStdout = stdout.toString(UTF_8);
            final String runStderr = stderr.toString(UTF_8);
            if (!runStderr.isEmpty()) {
                throw new IllegalStateException("Benchmark workload emitted stderr for `" + workload.id() + "`: " + runStderr);
            }
            if (!workload.expectedStdout().equals(runStdout)) {
                throw new IllegalStateException(
                        "Benchmark workload output mismatch for `" + workload.id() + "`."
                                + " Expected `" + workload.expectedStdout().replace("\n", "\\n")
                                + "` but got `" + runStdout.replace("\n", "\\n") + "`."
                );
            }

            final long afterMemory = usedMemoryBytes();
            final long memoryDelta = Math.max(0L, afterMemory - beforeMemory);
            peakMemoryBytes = Math.max(peakMemoryBytes, memoryDelta);

            if (iteration >= options.warmupIterations()) {
                compileSamples.add(compileNanos);
                runSamples.add(runNanos);
            }
        }

        final double compileAvgMs = nanosToMillis(averageNanos(compileSamples));
        final double compileMinMs = nanosToMillis(minNanos(compileSamples));
        final double compileMaxMs = nanosToMillis(maxNanos(compileSamples));
        final double runAvgMs = nanosToMillis(averageNanos(runSamples));
        final double runMinMs = nanosToMillis(minNanos(runSamples));
        final double runMaxMs = nanosToMillis(maxNanos(runSamples));
        final double throughput = throughputOpsPerSecond(workload.operationCount(), averageNanos(runSamples));

        return new BenchmarkResult(
                workload.id(),
                workload.category(),
                workload.description(),
                options.measuredIterations(),
                compileAvgMs,
                compileMinMs,
                compileMaxMs,
                runAvgMs,
                runMinMs,
                runMaxMs,
                throughput,
                peakMemoryBytes
        );
    }

    private static long usedMemoryBytes() {
        final Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double throughputOpsPerSecond(final long operationCount, final long runAvgNanos) {
        if (operationCount <= 0L || runAvgNanos <= 0L) {
            return 0.0d;
        }
        final double runSeconds = ((double) runAvgNanos) / 1_000_000_000.0d;
        return operationCount / runSeconds;
    }

    private static double nanosToMillis(final long nanos) {
        return ((double) nanos) / 1_000_000.0d;
    }

    private static long averageNanos(final List<Long> samples) {
        if (samples.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Long sample : samples) {
            total += sample;
        }
        return total / samples.size();
    }

    private static long minNanos(final List<Long> samples) {
        if (samples.isEmpty()) {
            return 0L;
        }
        long min = Long.MAX_VALUE;
        for (Long sample : samples) {
            min = Math.min(min, sample);
        }
        return min;
    }

    private static long maxNanos(final List<Long> samples) {
        if (samples.isEmpty()) {
            return 0L;
        }
        long max = Long.MIN_VALUE;
        for (Long sample : samples) {
            max = Math.max(max, sample);
        }
        return max;
    }

    private static void writeSources(final Path workloadRoot, final Map<String, String> sources) throws IOException {
        for (Map.Entry<String, String> source : sources.entrySet()) {
            final Path filePath = workloadRoot.resolve(source.getKey());
            final Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, source.getValue(), UTF_8);
        }
    }

    private static void deleteRecursively(final Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException ignored) {
                    // Best-effort cleanup for temp workspace.
                }
            });
        } catch (final IOException ignored) {
            // Best-effort cleanup for temp workspace.
        }
    }

    private static List<BenchmarkWorkload> workloadSuite(final BenchmarkProfile profile) {
        final BenchmarkWorkload microStartup = new BenchmarkWorkload(
                "micro-startup-hello",
                BenchmarkCategory.MICRO,
                "Startup baseline with minimal execution path.",
                1L,
                "main.ts",
                Map.of("main.ts", "console.log(\"hello\");\n"),
                "hello\n"
        );

        final BenchmarkWorkload microArithmetic = new BenchmarkWorkload(
                "micro-arithmetic-loop",
                BenchmarkCategory.MICRO,
                "Hot arithmetic + while loop throughput.",
                50_000L,
                "main.ts",
                Map.of(
                        "main.ts",
                        """
                        let i = 0;
                        let acc = 0;
                        while (i < 50000) {
                          acc = acc + i;
                          i = i + 1;
                        }
                        console.log("acc=" + acc);
                        """
                ),
                "acc=1249975000\n"
        );

        final BenchmarkWorkload macroClosureClass = new BenchmarkWorkload(
                "macro-closure-class",
                BenchmarkCategory.MACRO,
                "Macro-style workload with closure capture and class method dispatch.",
                2_000L,
                "main.ts",
                Map.of(
                        "main.ts",
                        """
                        function makeMultiplier(base: number) {
                          function mul(value: number) {
                            return value * base;
                          }
                          return mul;
                        }

                        class Accumulator {
                          value: number;
                          constructor() {
                            this.value = 0;
                          }
                          add(value: number) {
                            this.value = this.value + value;
                            return this.value;
                          }
                        }

                        const mul = makeMultiplier(3);
                        const acc = new Accumulator();
                        let i = 1;
                        while (i <= 2000) {
                          acc.add(mul(i));
                          i = i + 1;
                        }
                        console.log("macro=" + acc.value);
                        """
                ),
                "macro=6003000\n"
        );

        final Map<String, String> moduleSources = new LinkedHashMap<>();
        moduleSources.put(
                "lib/math.ts",
                """
                export function plusOne(value: number) {
                  return Promise.resolve(value + 1);
                }
                """
        );
        moduleSources.put(
                "lib/work.ts",
                """
                import { plusOne } from "./math.ts";

                export async function runWork(limit: number) {
                  let i = 0;
                  let total = 0;
                  while (i < limit) {
                    total = total + await plusOne(i);
                    i = i + 1;
                  }
                  return total;
                }
                """
        );
        moduleSources.put(
                "main.ts",
                """
                import { runWork } from "./lib/work.ts";

                function onDone(total: number) {
                  console.log("total=" + total);
                  return total;
                }

                runWork(1000).then(onDone);
                console.log("sync");
                """
        );
        final BenchmarkWorkload macroModulesAsync = new BenchmarkWorkload(
                "macro-modules-async",
                BenchmarkCategory.MACRO,
                "Macro-style workload with module graph and async sequencing.",
                1_000L,
                "main.ts",
                moduleSources,
                "sync\ntotal=500500\n"
        );

        if (profile == BenchmarkProfile.SMOKE) {
            return List.of(microStartup, macroClosureClass);
        }
        return List.of(microStartup, microArithmetic, macroClosureClass, macroModulesAsync);
    }

    private record BenchmarkWorkload(
            String id,
            BenchmarkCategory category,
            String description,
            long operationCount,
            String entryFile,
            Map<String, String> sources,
            String expectedStdout
    ) {
    }

    public enum BenchmarkCategory {
        MICRO("micro"),
        MACRO("macro");

        private final String jsonValue;

        BenchmarkCategory(final String jsonValue) {
            this.jsonValue = jsonValue;
        }

        public String jsonValue() {
            return jsonValue;
        }
    }

    public enum BenchmarkProfile {
        FULL,
        SMOKE
    }

    public record BenchmarkOptions(
            int warmupIterations,
            int measuredIterations,
            BenchmarkProfile profile,
            JvmOptimizationOptions optimizationOptions
    ) {
        public BenchmarkOptions {
            if (warmupIterations < 0) {
                throw new IllegalArgumentException("warmupIterations must be >= 0");
            }
            if (measuredIterations <= 0) {
                throw new IllegalArgumentException("measuredIterations must be >= 1");
            }
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(optimizationOptions, "optimizationOptions");
        }

        public static BenchmarkOptions defaults() {
            return new BenchmarkOptions(1, 2, BenchmarkProfile.FULL, JvmOptimizationOptions.defaults());
        }
    }

    public record BenchmarkResult(
            String id,
            BenchmarkCategory category,
            String description,
            int samples,
            double compileAvgMs,
            double compileMinMs,
            double compileMaxMs,
            double runAvgMs,
            double runMinMs,
            double runMaxMs,
            double throughputOpsPerSec,
            long peakMemoryBytes
    ) {
        private String toJson() {
            return "{"
                    + "\"id\":\"" + escapeJson(id) + "\","
                    + "\"category\":\"" + category.jsonValue() + "\","
                    + "\"description\":\"" + escapeJson(description) + "\","
                    + "\"samples\":" + samples + ","
                    + "\"compileAvgMs\":" + formatDouble(compileAvgMs) + ","
                    + "\"compileMinMs\":" + formatDouble(compileMinMs) + ","
                    + "\"compileMaxMs\":" + formatDouble(compileMaxMs) + ","
                    + "\"runAvgMs\":" + formatDouble(runAvgMs) + ","
                    + "\"runMinMs\":" + formatDouble(runMinMs) + ","
                    + "\"runMaxMs\":" + formatDouble(runMaxMs) + ","
                    + "\"throughputOpsPerSec\":" + formatDouble(throughputOpsPerSec) + ","
                    + "\"peakMemoryBytes\":" + peakMemoryBytes
                    + "}";
        }
    }

    public record BenchmarkSummary(
            int totalWorkloads,
            int microWorkloads,
            int macroWorkloads,
            double avgCompileMs,
            double avgRunMs,
            double avgThroughputOpsPerSec,
            long maxPeakMemoryBytes
    ) {
        private String toJson() {
            return "{"
                    + "\"totalWorkloads\":" + totalWorkloads + ","
                    + "\"microWorkloads\":" + microWorkloads + ","
                    + "\"macroWorkloads\":" + macroWorkloads + ","
                    + "\"avgCompileMs\":" + formatDouble(avgCompileMs) + ","
                    + "\"avgRunMs\":" + formatDouble(avgRunMs) + ","
                    + "\"avgThroughputOpsPerSec\":" + formatDouble(avgThroughputOpsPerSec) + ","
                    + "\"maxPeakMemoryBytes\":" + maxPeakMemoryBytes
                    + "}";
        }
    }

    public record BenchmarkReport(
            String schemaVersion,
            String generatedAt,
            BenchmarkOptions options,
            Map<String, String> environment,
            List<BenchmarkResult> results,
            BenchmarkSummary summary
    ) {
        public String toJson() {
            final StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append("\"schemaVersion\":\"").append(escapeJson(schemaVersion)).append("\",");
            builder.append("\"generatedAt\":\"").append(escapeJson(generatedAt)).append("\",");
            builder.append("\"options\":{");
            builder.append("\"warmupIterations\":").append(options.warmupIterations()).append(",");
            builder.append("\"measuredIterations\":").append(options.measuredIterations()).append(",");
            builder.append("\"profile\":\"").append(options.profile().name().toLowerCase(Locale.ROOT)).append("\",");
            builder.append("\"constantFoldingEnabled\":")
                    .append(options.optimizationOptions().constantFoldingEnabled()).append(",");
            builder.append("\"deadCodeEliminationEnabled\":")
                    .append(options.optimizationOptions().deadCodeEliminationEnabled());
            builder.append("},");

            builder.append("\"environment\":{");
            boolean firstEnvironment = true;
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                if (!firstEnvironment) {
                    builder.append(",");
                }
                firstEnvironment = false;
                builder.append("\"")
                        .append(escapeJson(entry.getKey()))
                        .append("\":\"")
                        .append(escapeJson(entry.getValue()))
                        .append("\"");
            }
            builder.append("},");

            builder.append("\"results\":[");
            boolean firstResult = true;
            for (BenchmarkResult result : results) {
                if (!firstResult) {
                    builder.append(",");
                }
                firstResult = false;
                builder.append(result.toJson());
            }
            builder.append("],");

            builder.append("\"summary\":").append(summary.toJson());
            builder.append("}");
            return builder.toString();
        }
    }

    private static String escapeJson(final String value) {
        final String safe = value == null ? "" : value;
        return safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String formatDouble(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
