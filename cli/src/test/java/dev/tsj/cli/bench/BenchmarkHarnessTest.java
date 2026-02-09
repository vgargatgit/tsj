package dev.tsj.cli.bench;

import dev.tsj.compiler.backend.jvm.JvmOptimizationOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkHarnessTest {
    @TempDir
    Path tempDir;

    @Test
    void runWritesBenchmarkReportForSmokeProfileWithMicroAndMacroWorkloads() throws Exception {
        final Path outputFile = tempDir.resolve("tsj-benchmark-baseline.json");
        final BenchmarkHarness.BenchmarkOptions options = new BenchmarkHarness.BenchmarkOptions(
                0,
                1,
                BenchmarkHarness.BenchmarkProfile.SMOKE,
                JvmOptimizationOptions.defaults()
        );

        final BenchmarkHarness.BenchmarkReport report = new BenchmarkHarness().run(outputFile, options);

        assertTrue(Files.exists(outputFile));
        assertEquals(2, report.results().size());
        assertEquals(1, report.summary().microWorkloads());
        assertEquals(1, report.summary().macroWorkloads());

        final String json = Files.readString(outputFile, UTF_8);
        assertTrue(json.contains("\"category\":\"micro\""));
        assertTrue(json.contains("\"category\":\"macro\""));
        assertTrue(json.contains("\"summary\""));
    }

    @Test
    void runCollectsPositiveTimingAndThroughputMetrics() throws Exception {
        final Path outputFile = tempDir.resolve("tsj-benchmark-metrics.json");
        final BenchmarkHarness.BenchmarkOptions options = new BenchmarkHarness.BenchmarkOptions(
                0,
                1,
                BenchmarkHarness.BenchmarkProfile.SMOKE,
                JvmOptimizationOptions.defaults()
        );

        final BenchmarkHarness.BenchmarkReport report = new BenchmarkHarness().run(outputFile, options);

        for (BenchmarkHarness.BenchmarkResult result : report.results()) {
            assertTrue(result.compileAvgMs() > 0.0d);
            assertTrue(result.runAvgMs() > 0.0d);
            assertTrue(result.throughputOpsPerSec() > 0.0d);
            assertTrue(result.peakMemoryBytes() >= 0L);
        }
    }

    @Test
    void runWritesBenchmarkReportForFullProfileWithAllWorkloads() throws Exception {
        final Path outputFile = tempDir.resolve("tsj-benchmark-full.json");
        final BenchmarkHarness.BenchmarkOptions options = new BenchmarkHarness.BenchmarkOptions(
                0,
                1,
                BenchmarkHarness.BenchmarkProfile.FULL,
                JvmOptimizationOptions.defaults()
        );

        final BenchmarkHarness.BenchmarkReport report = new BenchmarkHarness().run(outputFile, options);

        assertTrue(Files.exists(outputFile));
        assertEquals(4, report.results().size());
        assertEquals(2, report.summary().microWorkloads());
        assertEquals(2, report.summary().macroWorkloads());
        assertTrue(report.results().stream().anyMatch(result -> "micro-arithmetic-loop".equals(result.id())));
        assertTrue(report.results().stream().anyMatch(result -> "macro-modules-async".equals(result.id())));
    }

    @Test
    void runPersistsOptimizationFlagsInReportJson() throws Exception {
        final Path outputFile = tempDir.resolve("tsj-benchmark-no-opt.json");
        final BenchmarkHarness.BenchmarkOptions options = new BenchmarkHarness.BenchmarkOptions(
                0,
                1,
                BenchmarkHarness.BenchmarkProfile.SMOKE,
                JvmOptimizationOptions.disabled()
        );

        new BenchmarkHarness().run(outputFile, options);

        final String json = Files.readString(outputFile, UTF_8);
        assertTrue(json.contains("\"constantFoldingEnabled\":false"));
        assertTrue(json.contains("\"deadCodeEliminationEnabled\":false"));
    }

    @Test
    void runRejectsNullArguments() {
        final BenchmarkHarness harness = new BenchmarkHarness();
        final Path outputFile = tempDir.resolve("report.json");

        assertThrows(NullPointerException.class, () -> harness.run(null));
        assertThrows(NullPointerException.class, () -> harness.run(outputFile, null));
    }

    @Test
    void runRejectsInvalidBenchmarkIterationOptions() {
        final Path outputFile = tempDir.resolve("invalid.json");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BenchmarkHarness.BenchmarkOptions(
                        -1,
                        1,
                        BenchmarkHarness.BenchmarkProfile.SMOKE,
                        JvmOptimizationOptions.defaults()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new BenchmarkHarness.BenchmarkOptions(
                        0,
                        0,
                        BenchmarkHarness.BenchmarkProfile.SMOKE,
                        JvmOptimizationOptions.defaults()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new BenchmarkHarness().run(
                        outputFile,
                        new BenchmarkHarness.BenchmarkOptions(
                                0,
                                0,
                                BenchmarkHarness.BenchmarkProfile.SMOKE,
                                JvmOptimizationOptions.defaults()
                        )
                )
        );
    }
}
