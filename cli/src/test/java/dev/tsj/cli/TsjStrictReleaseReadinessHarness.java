package dev.tsj.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-84 strict-mode release readiness harness.
 */
final class TsjStrictReleaseReadinessHarness {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPORT_FILE = "tsj84-strict-release-readiness.json";
    private static final Path STRICT_READINESS_RELATIVE_PATH =
            Path.of("tests", "conformance", "tsj83-strict-readiness.json");
    private static final Path STRICT_RELEASE_RELATIVE_PATH =
            Path.of("tests", "conformance", "tsj84-strict-release-readiness.json");
    private static final Path STRICT_GUIDE_RELATIVE_PATH =
            Path.of("docs", "jvm-strict-mode-guide.md");
    private static final Path STRICT_CHECKLIST_RELATIVE_PATH =
            Path.of("docs", "jvm-strict-release-checklist.md");
    private static final Path CLI_CONTRACT_RELATIVE_PATH =
            Path.of("docs", "cli-contract.md");

    TsjStrictReleaseReadinessReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path repoRoot = resolveRepoRoot();

        final Path strictReadinessFile = repoRoot.resolve(STRICT_READINESS_RELATIVE_PATH).toAbsolutePath().normalize();
        final TsjStrictReadinessReport strictReadinessReport = new TsjStrictReadinessHarness().run(strictReadinessFile);
        final JsonNode strictReadinessJson = OBJECT_MAPPER.readTree(Files.readString(strictReadinessFile, UTF_8));
        final boolean strictReadinessPassed = strictReadinessJson.path("gatePassed").asBoolean(false);
        final int strictFixtureTotal = strictReadinessJson.path("totals").path("fixtures").asInt(0);
        final int strictFixtureFailures = strictReadinessJson.path("totals").path("failed").asInt(0);

        final Path strictGuideFile = repoRoot.resolve(STRICT_GUIDE_RELATIVE_PATH).toAbsolutePath().normalize();
        final Path strictChecklistFile = repoRoot.resolve(STRICT_CHECKLIST_RELATIVE_PATH).toAbsolutePath().normalize();
        final Path cliContractFile = repoRoot.resolve(CLI_CONTRACT_RELATIVE_PATH).toAbsolutePath().normalize();

        final String strictGuide = Files.readString(strictGuideFile, UTF_8);
        final String strictChecklist = Files.readString(strictChecklistFile, UTF_8);
        final String cliContract = Files.readString(cliContractFile, UTF_8);
        final List<String> residualExclusions = extractKnownExclusions(strictChecklist);

        final List<TsjStrictReleaseReadinessReport.ReleaseCriterion> criteria = List.of(
                new TsjStrictReleaseReadinessReport.ReleaseCriterion(
                        "strict-readiness-gate",
                        strictReadinessPassed && strictReadinessReport.gatePassed(),
                        "fixtures=" + strictFixtureTotal + ",failed=" + strictFixtureFailures
                ),
                new TsjStrictReleaseReadinessReport.ReleaseCriterion(
                        "strict-guide-migration",
                        strictGuide.contains("tsj compile app/main.ts --out build --mode jvm-strict")
                                && strictGuide.contains("Migration Strategy"),
                        STRICT_GUIDE_RELATIVE_PATH.toString()
                ),
                new TsjStrictReleaseReadinessReport.ReleaseCriterion(
                        "strict-cli-matrix",
                        cliContract.contains("--mode default|jvm-strict")
                                && cliContract.contains("TSJ-STRICT-UNSUPPORTED")
                                && cliContract.contains("package <entry.ts>"),
                        CLI_CONTRACT_RELATIVE_PATH.toString()
                ),
                new TsjStrictReleaseReadinessReport.ReleaseCriterion(
                        "strict-release-checklist",
                        strictChecklist.contains("## Known Exclusions")
                                && strictChecklist.contains("TSJ-STRICT-DYNAMIC-IMPORT")
                                && strictChecklist.contains("tests/conformance/tsj84-strict-release-readiness.json"),
                        STRICT_CHECKLIST_RELATIVE_PATH.toString()
                )
        );

        final boolean gatePassed = criteria.stream().allMatch(TsjStrictReleaseReadinessReport.ReleaseCriterion::passed)
                && !residualExclusions.isEmpty();

        final TsjStrictReleaseReadinessReport report = new TsjStrictReleaseReadinessReport(
                gatePassed,
                gatePassed,
                STRICT_READINESS_RELATIVE_PATH,
                STRICT_GUIDE_RELATIVE_PATH,
                STRICT_CHECKLIST_RELATIVE_PATH,
                residualExclusions,
                criteria,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }

        final Path canonicalPath = repoRoot.resolve(STRICT_RELEASE_RELATIVE_PATH).toAbsolutePath().normalize();
        if (!canonicalPath.equals(report.reportPath())) {
            writeReport(canonicalPath, report);
        }
        return report;
    }

    private static List<String> extractKnownExclusions(final String strictChecklist) {
        final List<String> exclusions = new ArrayList<>();
        for (String line : strictChecklist.split("\\R")) {
            final String trimmed = line.trim();
            if (!trimmed.startsWith("- ")) {
                continue;
            }
            if (!trimmed.contains("TSJ-STRICT-")) {
                continue;
            }
            exclusions.add(trimmed.substring(2).trim());
        }
        return List.copyOf(exclusions);
    }

    private static Path resolveRepoRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml")) && Files.exists(cursor.resolve("tests/conformance"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to resolve TSJ repository root.");
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjStrictReleaseReadinessHarness.class
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

    private static void writeReport(final Path reportPath, final TsjStrictReleaseReadinessReport report)
            throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }
}
