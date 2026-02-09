package dev.tsj.cli.bench;

import dev.tsj.compiler.backend.jvm.JvmOptimizationOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkBaselineReportTest {
    @Test
    void writesFullProfileBenchmarkBaselineReportToRepositoryPath() throws Exception {
        final Path cwd = Path.of("").toAbsolutePath().normalize();
        final Path repositoryRoot = cwd.getFileName() != null && "cli".equals(cwd.getFileName().toString())
                ? cwd.getParent()
                : cwd;
        final Path reportPath = repositoryRoot.resolve("benchmarks/tsj-benchmark-baseline.json");
        final BenchmarkHarness.BenchmarkOptions options = new BenchmarkHarness.BenchmarkOptions(
                1,
                1,
                BenchmarkHarness.BenchmarkProfile.FULL,
                JvmOptimizationOptions.defaults()
        );

        final BenchmarkHarness.BenchmarkReport report = new BenchmarkHarness().run(reportPath, options);
        final String json = Files.readString(reportPath, UTF_8);

        assertTrue(Files.exists(reportPath));
        assertTrue(report.summary().microWorkloads() >= 2);
        assertTrue(report.summary().macroWorkloads() >= 2);
        assertTrue(json.contains("\"profile\":\"full\""));
    }
}
