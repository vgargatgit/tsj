package dev.tsj.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tsj.compiler.frontend.StrictEligibilityChecker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-83 strict-mode conformance readiness harness.
 */
final class TsjStrictReadinessHarness {
    private static final String REPORT_FILE = "tsj83-strict-readiness.json";
    private static final Pattern EXPECTED_CODE_PATTERN =
            Pattern.compile("(?m)^\\s*//\\s*EXPECT_CODE\\s*:\\s*([A-Z0-9\\-]+)\\s*$");
    private static final Pattern EXPECTED_FEATURE_ID_PATTERN =
            Pattern.compile("(?m)^\\s*//\\s*EXPECT_FEATURE_ID\\s*:\\s*([A-Z0-9\\-]+)\\s*$");
    private static final Path STRICT_OK_ROOT = Path.of("tests", "conformance", "strict", "ok");
    private static final Path STRICT_UNSUPPORTED_ROOT = Path.of("unsupported", "strict");
    private static final Path SERIALIZATION_FIXTURE =
            Path.of("tests", "conformance", "strict", "ok", "003_serialization_dto.ts");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    TsjStrictReadinessReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path repoRoot = resolveRepoRoot();
        final Path strictOkRoot = repoRoot.resolve(STRICT_OK_ROOT).normalize();
        final Path strictUnsupportedRoot = repoRoot.resolve(STRICT_UNSUPPORTED_ROOT).normalize();

        final List<Path> strictOkFixtures = collectStrictOkFixtures(strictOkRoot);
        final List<Path> strictUnsupportedFixtures = collectStrictUnsupportedFixtures(strictUnsupportedRoot);
        final Set<String> strictFeatureCatalog = StrictEligibilityChecker.supportedFeatureIds();

        final List<TsjStrictReadinessReport.FixtureResult> fixtureResults = new ArrayList<>();
        final Map<String, Counter> categoryCounters = new LinkedHashMap<>();
        final Path moduleTargetDir = Objects.requireNonNull(
                resolveModuleReportPath().getParent(),
                "Module report path parent is required."
        );
        final Path workRoot = moduleTargetDir.resolve("tsj83-strict-work");
        Files.createDirectories(workRoot);

        for (Path fixture : strictOkFixtures) {
            final String fixturePath = normalizePath(repoRoot.relativize(fixture));
            final CompileResult result = compileStrict(fixture, workRoot.resolve(sanitizePath(fixturePath)));
            final String actualCode = lastDiagnosticField(result.output(), "code");
            final boolean passed = result.exitCode() == 0 && "TSJ-COMPILE-SUCCESS".equals(actualCode);
            fixtureResults.add(new TsjStrictReadinessReport.FixtureResult(
                    "strict-ok",
                    fixturePath,
                    passed,
                    "TSJ-COMPILE-SUCCESS",
                    actualCode,
                    "",
                    "",
                    "exit=" + result.exitCode()
            ));
            categoryCounters.computeIfAbsent("strict-ok", ignored -> new Counter()).accept(passed);
        }

        for (Path fixture : strictUnsupportedFixtures) {
            final String fixturePath = normalizePath(repoRoot.relativize(fixture));
            final UnsupportedExpectation expectation = readUnsupportedExpectation(fixture, strictFeatureCatalog);
            final CompileResult result = compileStrict(fixture, workRoot.resolve(sanitizePath(fixturePath)));
            final String actualCode = lastDiagnosticField(result.output(), "code");
            final String actualFeatureId = lastDiagnosticField(result.output(), "featureId");
            final boolean passed = result.exitCode() != 0
                    && expectation.expectedCode().equals(actualCode)
                    && expectation.expectedFeatureId().equals(actualFeatureId);
            fixtureResults.add(new TsjStrictReadinessReport.FixtureResult(
                    "strict-unsupported",
                    fixturePath,
                    passed,
                    expectation.expectedCode(),
                    actualCode,
                    expectation.expectedFeatureId(),
                    actualFeatureId,
                    "exit=" + result.exitCode()
            ));
            categoryCounters.computeIfAbsent("strict-unsupported", ignored -> new Counter()).accept(passed);
        }

        final int totalFixtures = fixtureResults.size();
        final int passedFixtures = (int) fixtureResults.stream().filter(TsjStrictReadinessReport.FixtureResult::passed).count();
        final int failedFixtures = totalFixtures - passedFixtures;
        final int strictOkTotal = strictOkFixtures.size();
        final int strictUnsupportedTotal = strictUnsupportedFixtures.size();
        final SerializationParityResult serializationParityResult = runSerializationParityScenario(repoRoot, workRoot);
        final boolean gatePassed = failedFixtures == 0
                && strictOkTotal > 0
                && strictUnsupportedTotal > 0
                && serializationParityResult.passed();

        final List<TsjStrictReadinessReport.CategorySummary> categories = List.of(
                categoryCounters.getOrDefault("strict-ok", new Counter()).summary("strict-ok"),
                categoryCounters.getOrDefault("strict-unsupported", new Counter()).summary("strict-unsupported")
        );

        final TsjStrictReadinessReport report = new TsjStrictReadinessReport(
                gatePassed,
                totalFixtures,
                passedFixtures,
                failedFixtures,
                strictOkTotal,
                strictUnsupportedTotal,
                serializationParityResult.passed(),
                serializationParityResult.notes(),
                categories,
                fixtureResults,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static List<Path> collectStrictOkFixtures(final Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Missing strict conformance root: " + root);
        }
        try (Stream<Path> paths = Files.list(root)) {
            final List<Path> fixtures = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".ts"))
                    .filter(path -> !path.getFileName().toString().startsWith("_"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (fixtures.isEmpty()) {
                throw new IllegalStateException("No strict conformance fixtures found in " + root);
            }
            return fixtures;
        }
    }

    private static List<Path> collectStrictUnsupportedFixtures(final Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Missing strict unsupported root: " + root);
        }
        try (Stream<Path> paths = Files.list(root)) {
            final List<Path> fixtures = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("[0-9]{3}_.*\\.ts"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (fixtures.isEmpty()) {
                throw new IllegalStateException("No strict unsupported fixtures found in " + root);
            }
            return fixtures;
        }
    }

    private static UnsupportedExpectation readUnsupportedExpectation(
            final Path fixture,
            final Set<String> strictFeatureCatalog
    ) throws IOException {
        final String source = Files.readString(fixture, UTF_8);
        final String code = firstMatch(EXPECTED_CODE_PATTERN, source);
        final String featureId = firstMatch(EXPECTED_FEATURE_ID_PATTERN, source);
        if (code.isBlank() || featureId.isBlank()) {
            throw new IllegalStateException(
                    "Unsupported strict fixture must declare EXPECT_CODE and EXPECT_FEATURE_ID: " + fixture
            );
        }
        if (!strictFeatureCatalog.contains(featureId)) {
            throw new IllegalStateException(
                    "Unsupported strict fixture declares unknown EXPECT_FEATURE_ID `" + featureId
                            + "`: " + fixture
            );
        }
        return new UnsupportedExpectation(code, featureId);
    }

    private static CompileResult compileStrict(final Path fixture, final Path outDir) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        fixture.toString(),
                        "--out",
                        outDir.toString(),
                        "--mode",
                        "jvm-strict"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        return new CompileResult(exitCode, stdout.toString(UTF_8) + "\n" + stderr.toString(UTF_8));
    }

    private static String firstMatch(final Pattern pattern, final String text) {
        final Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private static String lastDiagnosticField(final String output, final String fieldName) {
        final Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\":\"([^\"]+)\"");
        final Matcher matcher = pattern.matcher(output == null ? "" : output);
        String value = "";
        while (matcher.find()) {
            value = matcher.group(1);
        }
        return value;
    }

    private static String normalizePath(final Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private static String sanitizePath(final String value) {
        return value.replace('/', '_').replace('.', '_');
    }

    private static SerializationParityResult runSerializationParityScenario(
            final Path repoRoot,
            final Path workRoot
    ) {
        final Path fixture = repoRoot.resolve(SERIALIZATION_FIXTURE).normalize();
        if (!Files.exists(fixture)) {
            return new SerializationParityResult(false, "missing fixture: " + fixture);
        }
        final Path outDir = workRoot.resolve("serialization-parity");
        final CompileResult compileResult = compileStrict(fixture, outDir);
        if (compileResult.exitCode() != 0) {
            return new SerializationParityResult(
                    false,
                    "compile failed: code=" + lastDiagnosticField(compileResult.output(), "code")
            );
        }
        final String programClassName = lastDiagnosticField(compileResult.output(), "className");
        if (programClassName.isBlank()) {
            return new SerializationParityResult(false, "missing className metadata from strict compile output");
        }
        final Path classesDir = outDir.resolve("classes");
        if (!Files.isDirectory(classesDir)) {
            return new SerializationParityResult(false, "missing classes directory: " + classesDir);
        }
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classesDir.toUri().toURL()},
                TsjStrictReadinessHarness.class.getClassLoader()
        )) {
            final Class<?> dtoClass = Class.forName(
                    "dev.tsj.generated.SerializableOwner",
                    true,
                    classLoader
            );
            final Object dto = dtoClass.getDeclaredConstructor().newInstance();
            final Method asDto = dtoClass.getDeclaredMethod("asDto");
            final Object dtoView = asDto.invoke(dto);
            final String json = OBJECT_MAPPER.writeValueAsString(dtoView);
            if (!json.contains("\"id\":\"101\"") || !json.contains("\"name\":\"Lin\"")) {
                return new SerializationParityResult(false, "serialized DTO payload mismatch: " + json);
            }
            final Object rebound = OBJECT_MAPPER.readValue(json, dtoClass);
            final Method getId = rebound.getClass().getDeclaredMethod("getId");
            final Method getName = rebound.getClass().getDeclaredMethod("getName");
            final Object idValue = getId.invoke(rebound);
            final Object nameValue = getName.invoke(rebound);
            if (!"101".equals(String.valueOf(idValue)) || !"Lin".equals(String.valueOf(nameValue))) {
                return new SerializationParityResult(
                        false,
                        "deserialized DTO payload mismatch: id=" + idValue + ",name=" + nameValue
                );
            }
            return new SerializationParityResult(true, "strict native DTO Jackson round-trip passed");
        } catch (final Exception exception) {
            return new SerializationParityResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
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
                    TsjStrictReadinessHarness.class
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

    private static void writeReport(final Path reportPath, final TsjStrictReadinessReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record CompileResult(int exitCode, String output) {
    }

    private record UnsupportedExpectation(String expectedCode, String expectedFeatureId) {
    }

    private record SerializationParityResult(boolean passed, String notes) {
        private SerializationParityResult {
            notes = Objects.requireNonNull(notes, "notes");
        }
    }

    private static final class Counter {
        private int total;
        private int passed;

        private void accept(final boolean isPassed) {
            total++;
            if (isPassed) {
                passed++;
            }
        }

        private TsjStrictReadinessReport.CategorySummary summary(final String name) {
            return new TsjStrictReadinessReport.CategorySummary(name, total, passed, total - passed);
        }
    }
}
