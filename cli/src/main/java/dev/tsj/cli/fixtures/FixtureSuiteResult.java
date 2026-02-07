package dev.tsj.cli.fixtures;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Fixture suite run outputs including coverage reporting metadata.
 */
public record FixtureSuiteResult(
        List<FixtureRunResult> results,
        FixtureCoverageReport coverageReport,
        Path coverageReportPath
) {
    public FixtureSuiteResult {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        coverageReport = Objects.requireNonNull(coverageReport, "coverageReport");
        coverageReportPath = Objects.requireNonNull(coverageReportPath, "coverageReportPath")
                .toAbsolutePath()
                .normalize();
    }
}
