package dev.tsj.cli.fixtures;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifferentialConformanceSuiteTest {
    @Test
    void committedFixturesMatchNodeAndGenerateCoverageReport() throws Exception {
        final Path fixturesRoot = Path.of("..", "tests", "fixtures").toAbsolutePath().normalize();
        final FixtureSuiteResult suite = new FixtureHarness().runSuite(fixturesRoot);

        final List<FixtureRunResult> failures = suite.results().stream()
                .filter(result -> !result.passed())
                .toList();
        final String failureSummary = failures.stream()
                .map(result -> result.fixtureName() + ": " + result.minimizedRepro())
                .collect(Collectors.joining(" || "));
        assertTrue(failures.isEmpty(), "Differential conformance failures: " + failureSummary);

        assertTrue(Files.exists(suite.coverageReportPath()));
        final String report = Files.readString(suite.coverageReportPath(), UTF_8);
        assertTrue(report.contains("\"totalFixtures\""));
        assertTrue(report.contains("\"features\""));
    }
}
