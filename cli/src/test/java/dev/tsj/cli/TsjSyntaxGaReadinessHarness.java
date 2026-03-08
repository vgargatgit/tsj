package dev.tsj.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-70 syntax GA readiness/signoff harness.
 */
final class TsjSyntaxGaReadinessHarness {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPORT_FILE = "tsj70-syntax-ga-signoff.json";
    private static final String READINESS_REPORT_RELATIVE_PATH = "tests/conformance/tsj-syntax-readiness.json";
    private static final String COMPAT_MANIFEST_RELATIVE_PATH = "tests/conformance/tsj70-syntax-compatibility-manifest.json";
    private static final String SIGNOFF_RELATIVE_PATH = "tests/conformance/tsj70-syntax-ga-signoff.json";

    TsjSyntaxGaSignoffReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path repoRoot = resolveRepoRoot();
        refreshReadinessReport();
        final Path readinessReportPath = repoRoot.resolve(READINESS_REPORT_RELATIVE_PATH).toAbsolutePath().normalize();
        final JsonNode readiness = OBJECT_MAPPER.readTree(Files.readString(readinessReportPath, UTF_8));

        final int totalFixtures = readiness.path("totals").path("total").asInt();
        final int totalFailures = readiness.path("totals").path("failed").asInt();
        final ArrayNode expectedBlockers = asArray(readiness.path("expectedBlockers"));
        final ArrayNode unexpectedFailures = asArray(readiness.path("unexpectedFailures"));
        final int expectedBlockerCount = expectedBlockers.size();
        final int certifiedCorpusTotal = Math.max(0, totalFixtures - expectedBlockerCount);
        final int certifiedParseFailures = Math.max(0, totalFailures - expectedBlockerCount);

        boolean expectedBlockersMatched = true;
        for (JsonNode blocker : expectedBlockers) {
            if (!"matched".equals(blocker.path("status").asText())) {
                expectedBlockersMatched = false;
                break;
            }
        }

        int mandatoryCategoryFailures = 0;
        final ArrayNode categories = asArray(readiness.path("categories"));
        for (JsonNode category : categories) {
            final String categoryName = category.path("category").asText();
            if (isMandatoryCategory(categoryName) && category.path("failed").asInt() > 0) {
                mandatoryCategoryFailures++;
            }
        }

        final List<String> residualExclusions = new ArrayList<>();
        residualExclusions.add("TSX/JSX inputs (.tsx) are out of scope (TSJ67-TSX-OUT-OF-SCOPE).");
        for (JsonNode blocker : expectedBlockers) {
            final String fixture = blocker.path("fixture").asText();
            final String code = blocker.path("expectedCode").asText();
            residualExclusions.add("Excluded fixture: " + fixture + " (code=" + code + ")");
        }

        final boolean certifiedCorpusPassed = certifiedParseFailures == 0 && expectedBlockersMatched;
        final boolean mandatorySuitesPassed = unexpectedFailures.isEmpty() && mandatoryCategoryFailures == 0;

        final Path compatibilityManifestPath = repoRoot.resolve(COMPAT_MANIFEST_RELATIVE_PATH)
                .toAbsolutePath()
                .normalize();
        writeCompatibilityManifest(
                compatibilityManifestPath,
                readinessReportPath,
                certifiedCorpusTotal,
                certifiedParseFailures,
                residualExclusions,
                mandatorySuitesPassed
        );

        final List<TsjSyntaxGaSignoffReport.SignoffCriterion> criteria = List.of(
                new TsjSyntaxGaSignoffReport.SignoffCriterion(
                        "certified-corpus-parse-failures",
                        certifiedCorpusPassed,
                        "certifiedTotal=" + certifiedCorpusTotal
                                + ",certifiedFailures=" + certifiedParseFailures
                                + ",expectedBlockers=" + expectedBlockerCount
                ),
                new TsjSyntaxGaSignoffReport.SignoffCriterion(
                        "mandatory-suite-signals",
                        mandatorySuitesPassed,
                        "unexpectedFailures=" + unexpectedFailures.size()
                                + ",mandatoryCategoryFailures=" + mandatoryCategoryFailures
                ),
                new TsjSyntaxGaSignoffReport.SignoffCriterion(
                        "compatibility-manifest",
                        Files.exists(compatibilityManifestPath) && !residualExclusions.isEmpty(),
                        "manifest=" + compatibilityManifestPath
                                + ",residualExclusions=" + residualExclusions.size()
                )
        );
        final boolean gatePassed = criteria.stream().allMatch(TsjSyntaxGaSignoffReport.SignoffCriterion::passed);

        final TsjSyntaxGaSignoffReport report = new TsjSyntaxGaSignoffReport(
                gatePassed,
                gatePassed,
                readinessReportPath,
                compatibilityManifestPath,
                residualExclusions,
                criteria,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }

        final Path canonicalSignoffPath = repoRoot.resolve(SIGNOFF_RELATIVE_PATH).toAbsolutePath().normalize();
        if (!canonicalSignoffPath.equals(report.reportPath())) {
            writeReport(canonicalSignoffPath, report);
        }
        return report;
    }

    private static void refreshReadinessReport() throws Exception {
        final TsjSyntaxConformanceReadinessGateTest readinessGate = new TsjSyntaxConformanceReadinessGateTest();
        final Field tempDirField = TsjSyntaxConformanceReadinessGateTest.class.getDeclaredField("tempDir");
        tempDirField.setAccessible(true);
        tempDirField.set(readinessGate, Files.createTempDirectory("tsj70-readiness-"));
        readinessGate.readinessGateGeneratesSyntaxCategoryReportAndEnforcesThresholds();
    }

    private static void writeCompatibilityManifest(
            final Path compatibilityManifestPath,
            final Path readinessReportPath,
            final int certifiedCorpusTotal,
            final int certifiedParseFailures,
            final List<String> residualExclusions,
            final boolean mandatorySuitesPassed
    ) throws IOException {
        final ObjectNode manifest = OBJECT_MAPPER.createObjectNode();
        manifest.put("suite", "TSJ-70-syntax-compatibility-manifest");
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("readinessReport", readinessReportPath.toString());
        manifest.put("languageLevel", "TypeScript 5.9 bridge-certified subset");
        manifest.put("certifiedCorpusTotal", certifiedCorpusTotal);
        manifest.put("certifiedCorpusParseFailures", certifiedParseFailures);
        manifest.put("mandatorySuitesPassed", mandatorySuitesPassed);
        final ArrayNode residual = manifest.putArray("residualExclusions");
        for (String exclusion : residualExclusions) {
            residual.add(exclusion);
        }
        final ArrayNode supported = manifest.putArray("supportedSignals");
        supported.add("TGTA non-TSX compile gate");
        supported.add("UTTA grammar + stress categories");
        supported.add("XTTA grammar + builtins categories");
        supported.add("TypeScript + OSS conformance corpus categories");
        Files.createDirectories(compatibilityManifestPath.getParent());
        Files.writeString(compatibilityManifestPath, OBJECT_MAPPER.writeValueAsString(manifest) + "\n", UTF_8);
    }

    private static boolean isMandatoryCategory(final String categoryName) {
        return categoryName.startsWith("utta/")
                || categoryName.startsWith("xtta/")
                || categoryName.startsWith("typescript/")
                || categoryName.startsWith("oss/");
    }

    private static ArrayNode asArray(final JsonNode node) {
        if (node instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        return OBJECT_MAPPER.createArrayNode();
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
                    TsjSyntaxGaReadinessHarness.class
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

    private static void writeReport(final Path reportPath, final TsjSyntaxGaSignoffReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }
}
