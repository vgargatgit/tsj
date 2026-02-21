package dev.tsj.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.EventBus;
import com.zaxxer.hikari.HikariConfig;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.Driver;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjAnyJarCertificationTest {
    @TempDir
    Path tempDir;

    @Test
    void anyJarCertificationSuiteProducesReadinessReportForTsj44Subset() throws Exception {
        final List<CertificationCheckResult> checks = new ArrayList<>();

        final Path ormJar = buildInteropJar(
                "sample.orm.JpaLite",
                """
                package sample.orm;

                import java.util.LinkedHashMap;
                import java.util.Map;

                public final class JpaLite {
                    private static final Map<Long, String> STORE = new LinkedHashMap<>();

                    private JpaLite() {
                    }

                    public static void reset() {
                        STORE.clear();
                    }

                    public static void save(final long id, final String status) {
                        STORE.put(id, status);
                    }

                    public static String findStatus(final long id) {
                        return STORE.get(id);
                    }
                }
                """,
                "orm-jpalite-1.0.jar"
        );
        checks.add(runCheck(
                "orm-jpalite",
                "orm",
                "sample.orm.JpaLite",
                "1.0",
                """
                import { reset, save, findStatus } from "java:sample.orm.JpaLite";
                reset();
                save(7, "PAID");
                console.log("orm=" + findStatus(7));
                """,
                "orm=PAID",
                ormJar
        ));

        checks.add(runCheck(
                "http-uri",
                "http-client",
                "java.net.URI",
                "JDK21",
                """
                import { create as uriCreate } from "java:java.net.URI";
                import { $instance$getHost as uriHost } from "java:java.net.URI";
                const uri = uriCreate("https://example.org/orders");
                console.log("http=" + uriHost(uri));
                """,
                "http=example.org",
                null
        ));

        final Path serializationJar = buildInteropJar(
                "sample.serialization.JsonCodec",
                """
                package sample.serialization;

                import java.util.Map;

                public final class JsonCodec {
                    private JsonCodec() {
                    }

                    public static String encode(final Map<String, Object> value) {
                        return String.valueOf(value.get("name")) + ":" + String.valueOf(value.get("count"));
                    }
                }
                """,
                "serialization-jsoncodec-1.0.jar"
        );
        checks.add(runCheck(
                "serialization-jsoncodec",
                "serialization",
                "sample.serialization.JsonCodec",
                "1.0",
                """
                import { encode } from "java:sample.serialization.JsonCodec";
                console.log("serialization=" + encode({ name: "tsj", count: 2 }));
                """,
                "serialization=tsj:2",
                serializationJar
        ));

        final Path validationJar = buildInteropJar(
                "sample.validation.EmailValidator",
                """
                package sample.validation;

                public final class EmailValidator {
                    private EmailValidator() {
                    }

                    public static boolean isValid(final String email) {
                        return email.contains("@") && email.contains(".");
                    }
                }
                """,
                "validation-email-1.0.jar"
        );
        checks.add(runCheck(
                "validation-email",
                "validation",
                "sample.validation.EmailValidator",
                "1.0",
                """
                import { isValid } from "java:sample.validation.EmailValidator";
                console.log("validation=" + isValid("hello@tsj.dev"));
                """,
                "validation=true",
                validationJar
        ));

        final Path cacheJar = buildInteropJar(
                "sample.cache.MemoryCache",
                """
                package sample.cache;

                import java.util.LinkedHashMap;
                import java.util.Map;

                public final class MemoryCache {
                    private static final Map<String, String> STORE = new LinkedHashMap<>();

                    private MemoryCache() {
                    }

                    public static void put(final String key, final String value) {
                        STORE.put(key, value);
                    }

                    public static String get(final String key) {
                        return STORE.get(key);
                    }
                }
                """,
                "cache-memory-1.0.jar"
        );
        checks.add(runCheck(
                "cache-memory",
                "caching",
                "sample.cache.MemoryCache",
                "1.0",
                """
                import { put, get } from "java:sample.cache.MemoryCache";
                put("k", "v");
                console.log("cache=" + get("k"));
                """,
                "cache=v",
                cacheJar
        ));

        final Path messagingJar = buildInteropJar(
                "sample.messaging.Bus",
                """
                package sample.messaging;

                import java.util.ArrayList;
                import java.util.List;

                public final class Bus {
                    private static final List<String> MESSAGES = new ArrayList<>();

                    private Bus() {
                    }

                    public static void publish(final String message) {
                        MESSAGES.add(message);
                    }

                    public static int drainCount() {
                        final int count = MESSAGES.size();
                        MESSAGES.clear();
                        return count;
                    }
                }
                """,
                "messaging-bus-1.0.jar"
        );
        checks.add(runCheck(
                "messaging-bus",
                "messaging",
                "sample.messaging.Bus",
                "1.0",
                """
                import { publish, drainCount } from "java:sample.messaging.Bus";
                publish("a");
                publish("b");
                console.log("messaging=" + drainCount());
                """,
                "messaging=2",
                messagingJar
        ));

        checks.add(runCheck(
                "utility-duration",
                "utility",
                "java.time.Duration",
                "JDK21",
                """
                import { parse as parseDuration } from "java:java.time.Duration";
                import { $instance$toMillis as durationToMillis } from "java:java.time.Duration";
                const duration = parseDuration("PT1.5S");
                console.log("utility=" + durationToMillis(duration));
                """,
                "utility=1500",
                null
        ));

        final CertificationReport report = summarize(checks);
        final Path reportPath = Path.of("target/tsj44-anyjar-certification-report.json")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report.toJson(), UTF_8);

        assertEquals(7, report.totalChecks());
        assertEquals(7, report.passedChecks());
        assertTrue(report.coveragePercent() >= report.minCoveragePercent());
        assertTrue(report.averageDurationMs() <= report.maxAverageDurationMs());
        assertTrue(report.maxDurationMs() <= report.maxCheckDurationMs());
        assertTrue(report.subsetReady());
        assertTrue(Files.exists(reportPath));
        final String reportJson = Files.readString(reportPath, UTF_8);
        assertTrue(reportJson.contains("\"category\":\"orm\""));
        assertTrue(reportJson.contains("\"category\":\"http-client\""));
        assertTrue(reportJson.contains("\"category\":\"serialization\""));
        assertTrue(reportJson.contains("\"category\":\"validation\""));
        assertTrue(reportJson.contains("\"category\":\"caching\""));
        assertTrue(reportJson.contains("\"category\":\"messaging\""));
        assertTrue(reportJson.contains("\"category\":\"utility\""));
    }

    @Test
    void anyJarCertificationExpandsRealLibraryMatrixForTsj44a() throws Exception {
        final List<CertificationCheckResult> checks = new ArrayList<>();

        checks.add(runCheck(
                "orm-flyway-version",
                "orm",
                "org.flywaydb.core.api.MigrationVersion",
                "10.17.3",
                """
                import { fromVersion } from "java:org.flywaydb.core.api.MigrationVersion";
                console.log("orm=" + fromVersion("1.2.3"));
                """,
                "orm=1.2.3",
                jarPathForClass(MigrationVersion.class)
        ));
        checks.add(runCheck(
                "jdbc-postgresql-driver",
                "jdbc-driver",
                "org.postgresql.Driver",
                "42.7.4",
                """
                import { isRegistered } from "java:org.postgresql.Driver";
                console.log("jdbc=" + isRegistered());
                """,
                "jdbc=true",
                jarPathForClass(Driver.class)
        ));
        checks.add(runCheck(
                "serialization-jackson",
                "serialization",
                "com.fasterxml.jackson.databind.ObjectMapper",
                "2.17.2",
                """
                import { $new, $instance$writeValueAsString as writeValueAsString } from "java:com.fasterxml.jackson.databind.ObjectMapper";
                const codec = $new();
                console.log("serialization=" + writeValueAsString(codec, { name: "tsj", count: 2 }));
                """,
                "serialization=",
                jarPathForClass(ObjectMapper.class)
        ));
        checks.add(runCheck(
                "config-snakeyaml",
                "configuration",
                "org.yaml.snakeyaml.Yaml",
                "2.2",
                """
                import { $new, $instance$load as load } from "java:org.yaml.snakeyaml.Yaml";
                const yaml = $new();
                console.log("config=" + load(yaml, "broad"));
                """,
                "config=broad",
                jarPathForClass(Yaml.class)
        ));
        checks.add(runCheck(
                "pool-hikaricp",
                "pooling",
                "com.zaxxer.hikari.HikariConfig",
                "5.1.0",
                """
                import { $new, $instance$setPoolName as setPoolName, $instance$getPoolName as getPoolName } from "java:com.zaxxer.hikari.HikariConfig";
                const config = $new();
                setPoolName(config, "tsjPool");
                console.log("pool=" + getPoolName(config));
                """,
                "pool=tsjPool",
                jarPathForClass(HikariConfig.class)
        ));
        checks.add(runCheck(
                "messaging-guava-eventbus",
                "messaging",
                "com.google.common.eventbus.EventBus",
                "33.3.0-jre",
                """
                import { $new, $instance$post as post } from "java:com.google.common.eventbus.EventBus";
                const bus = $new();
                post(bus, "ping");
                console.log("messaging=posted");
                """,
                "messaging=posted",
                jarPathForClass(EventBus.class)
        ));
        checks.add(runCheck(
                "utility-commons-lang3",
                "utility",
                "org.apache.commons.lang3.StringUtils",
                "3.1",
                """
                import { capitalize } from "java:org.apache.commons.lang3.StringUtils";
                console.log("utility=" + capitalize("tsj"));
                """,
                "utility=Tsj",
                jarPathForClass(StringUtils.class)
        ));

        final CertificationReport report = summarize(checks);
        final Path reportPath = Path.of("target/tsj44a-real-library-matrix-report.json")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report.toJson(), UTF_8);

        assertEquals(7, report.totalChecks());
        assertEquals(7, report.passedChecks());
        assertTrue(report.subsetReady());
        assertTrue(Files.exists(reportPath));
        final String reportJson = Files.readString(reportPath, UTF_8);
        assertTrue(reportJson.contains("\"library\":\"org.flywaydb.core.api.MigrationVersion\""));
        assertTrue(reportJson.contains("\"library\":\"org.postgresql.Driver\""));
        assertTrue(reportJson.contains("\"library\":\"com.fasterxml.jackson.databind.ObjectMapper\""));
        assertTrue(reportJson.contains("\"library\":\"org.yaml.snakeyaml.Yaml\""));
        assertTrue(reportJson.contains("\"library\":\"com.zaxxer.hikari.HikariConfig\""));
        assertTrue(reportJson.contains("\"library\":\"com.google.common.eventbus.EventBus\""));
        assertTrue(reportJson.contains("\"library\":\"org.apache.commons.lang3.StringUtils\""));
    }

    @Test
    void readinessGateFallsBackToNotReadyWhenCoverageOrBudgetsFail() {
        final List<CertificationCheckResult> checks = List.of(
                new CertificationCheckResult(
                        "orm-jpalite",
                        "orm",
                        "sample.orm.JpaLite",
                        "1.0",
                        true,
                        100L,
                        "ok"
                ),
                new CertificationCheckResult(
                        "serialization-jsoncodec",
                        "serialization",
                        "sample.serialization.JsonCodec",
                        "1.0",
                        false,
                        25_000L,
                        "diagnostic"
                )
        );

        final CertificationReport report = summarize(checks);
        assertEquals(2, report.totalChecks());
        assertEquals(1, report.passedChecks());
        assertTrue(report.coveragePercent() < report.minCoveragePercent());
        assertTrue(report.maxDurationMs() > report.maxCheckDurationMs());
        assertFalse(report.subsetReady());

        final String reportJson = report.toJson();
        assertTrue(reportJson.contains("\"library\":\"sample.orm.JpaLite\""));
        assertTrue(reportJson.contains("\"version\":\"1.0\""));
        assertTrue(reportJson.contains("\"subsetReady\":false"));
    }

    @Test
    void anyJarCertificationTracksVersionRangesAndFirstFailingVersionForTsj44b() throws Exception {
        final List<CertificationCheckResult> checks = new ArrayList<>();

        checks.add(runCheck(
                "range-lib-v1_0_0",
                "utility",
                "sample.version.RangeApi",
                "1.0.0",
                """
                import { mode } from "java:sample.version.RangeApi";
                console.log("range=" + mode());
                """,
                "range=OK",
                buildInteropJar(
                        "sample.version.RangeApi",
                        """
                        package sample.version;

                        public final class RangeApi {
                            private RangeApi() {
                            }

                            public static String mode() {
                                return "OK";
                            }
                        }
                        """,
                        "range-api-1.0.0.jar"
                )
        ));
        checks.add(runCheck(
                "range-lib-v1_1_0",
                "utility",
                "sample.version.RangeApi",
                "1.1.0",
                """
                import { mode } from "java:sample.version.RangeApi";
                console.log("range=" + mode());
                """,
                "range=OK",
                buildInteropJar(
                        "sample.version.RangeApi",
                        """
                        package sample.version;

                        public final class RangeApi {
                            private RangeApi() {
                            }

                            public static String mode() {
                                return "OK";
                            }
                        }
                        """,
                        "range-api-1.1.0.jar"
                )
        ));
        checks.add(runCheck(
                "range-lib-v2_0_0",
                "utility",
                "sample.version.RangeApi",
                "2.0.0",
                """
                import { mode } from "java:sample.version.RangeApi";
                console.log("range=" + mode());
                """,
                "range=OK",
                buildInteropJar(
                        "sample.version.RangeApi",
                        """
                        package sample.version;

                        public final class RangeApi {
                            private RangeApi() {
                            }

                            public static String mode() {
                                return "BROKEN";
                            }
                        }
                        """,
                        "range-api-2.0.0.jar"
                )
        ));

        checks.add(runCheck(
                "text-lib-v3_0_0",
                "serialization",
                "sample.version.TextApi",
                "3.0.0",
                """
                import { encode } from "java:sample.version.TextApi";
                console.log("text=" + encode("tsj"));
                """,
                "text=TSJ",
                buildInteropJar(
                        "sample.version.TextApi",
                        """
                        package sample.version;

                        public final class TextApi {
                            private TextApi() {
                            }

                            public static String encode(final String value) {
                                return value.toUpperCase();
                            }
                        }
                        """,
                        "text-api-3.0.0.jar"
                )
        ));
        checks.add(runCheck(
                "text-lib-v3_1_0",
                "serialization",
                "sample.version.TextApi",
                "3.1.0",
                """
                import { encode } from "java:sample.version.TextApi";
                console.log("text=" + encode("tsj"));
                """,
                "text=TSJ",
                buildInteropJar(
                        "sample.version.TextApi",
                        """
                        package sample.version;

                        public final class TextApi {
                            private TextApi() {
                            }

                            public static String encode(final String value) {
                                return value.toUpperCase();
                            }
                        }
                        """,
                        "text-api-3.1.0.jar"
                )
        ));

        final VersionRangeCertificationReport report = summarizeVersionRanges(checks);
        final Path reportPath = Path.of("target/tsj44b-version-range-certification.json")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report.toJson(), UTF_8);

        assertEquals(2, report.libraries().size());
        final VersionRangeLibrarySummary rangeApi = report.libraries().stream()
                .filter(summary -> "sample.version.RangeApi".equals(summary.library()))
                .findFirst()
                .orElseThrow();
        assertEquals("2.0.0", rangeApi.firstFailingVersion());
        assertEquals("1.0.0", rangeApi.minVersion());
        assertEquals("2.0.0", rangeApi.maxVersion());
        assertTrue(report.driftDetected());
        assertFalse(report.subsetReady());
        assertTrue(Files.exists(reportPath));
        final String json = Files.readString(reportPath, UTF_8);
        assertTrue(json.contains("\"suite\":\"TSJ-44b-version-range-certification\""));
        assertTrue(json.contains("\"firstFailingVersion\":\"2.0.0\""));
        assertTrue(json.contains("\"library\":\"sample.version.TextApi\""));
    }

    @Test
    void versionRangeSummaryIsReadyWhenAllVersionsPass() {
        final List<CertificationCheckResult> checks = List.of(
                new CertificationCheckResult("a-1-0", "utility", "sample.version.A", "1.0.0", true, 10L, "ok"),
                new CertificationCheckResult("a-1-1", "utility", "sample.version.A", "1.1.0", true, 11L, "ok"),
                new CertificationCheckResult("b-2-0", "utility", "sample.version.B", "2.0.0", true, 12L, "ok")
        );

        final VersionRangeCertificationReport report = summarizeVersionRanges(checks);

        assertFalse(report.driftDetected());
        assertTrue(report.subsetReady());
        assertEquals("", report.libraries().stream()
                .filter(summary -> "sample.version.A".equals(summary.library()))
                .findFirst()
                .orElseThrow()
                .firstFailingVersion());
    }

    private CertificationCheckResult runCheck(
            final String checkId,
            final String category,
            final String library,
            final String version,
            final String tsSource,
            final String expectedStdoutMarker,
            final Path jarFile
    ) throws Exception {
        final Path entryFile = tempDir.resolve(checkId + ".ts");
        Files.writeString(entryFile, tsSource, UTF_8);

        final List<String> args = new ArrayList<>();
        args.add("run");
        args.add(entryFile.toString());
        args.add("--out");
        args.add(tempDir.resolve("out-" + checkId).toString());
        if (jarFile != null) {
            args.add("--jar");
            args.add(jarFile.toString());
        }
        args.add("--interop-policy");
        args.add("broad");
        args.add("--ack-interop-risk");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final long startNanos = System.nanoTime();
        final int exitCode = TsjCli.execute(
                args.toArray(String[]::new),
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        final long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

        final String stdoutText = stdout.toString(UTF_8);
        final String stderrText = stderr.toString(UTF_8);
        final boolean passed = exitCode == 0 && stdoutText.contains(expectedStdoutMarker);
        final String details = passed
                ? "ok"
                : ("exit=" + exitCode + ",stdout=" + trim(stdoutText, 140) + ",stderr=" + trim(stderrText, 140));
        return new CertificationCheckResult(
                checkId,
                category,
                library,
                version,
                passed,
                durationMs,
                details
        );
    }

    private CertificationReport summarize(final List<CertificationCheckResult> checks) {
        final int totalChecks = checks.size();
        int passedChecks = 0;
        long totalDurationMs = 0;
        long maxDurationMs = 0;
        for (CertificationCheckResult check : checks) {
            if (check.passed()) {
                passedChecks++;
            }
            totalDurationMs += check.durationMs();
            maxDurationMs = Math.max(maxDurationMs, check.durationMs());
        }
        final double coveragePercent = totalChecks == 0 ? 0.0d : (passedChecks * 100.0d) / totalChecks;
        final long averageDurationMs = totalChecks == 0 ? 0L : totalDurationMs / totalChecks;

        final double minCoveragePercent = 85.0d;
        final long maxAverageDurationMs = 8_000L;
        final long maxCheckDurationMs = 20_000L;
        final boolean subsetReady = coveragePercent >= minCoveragePercent
                && averageDurationMs <= maxAverageDurationMs
                && maxDurationMs <= maxCheckDurationMs;
        return new CertificationReport(
                "TSJ-44-any-jar-certification-subset",
                Instant.now().toString(),
                minCoveragePercent,
                maxAverageDurationMs,
                maxCheckDurationMs,
                totalChecks,
                passedChecks,
                coveragePercent,
                averageDurationMs,
                maxDurationMs,
                subsetReady,
                List.copyOf(checks)
        );
    }

    private VersionRangeCertificationReport summarizeVersionRanges(final List<CertificationCheckResult> checks) {
        final java.util.Map<String, List<CertificationCheckResult>> byLibrary = new java.util.TreeMap<>();
        for (CertificationCheckResult check : checks) {
            byLibrary.computeIfAbsent(check.library(), ignored -> new ArrayList<>()).add(check);
        }
        final List<VersionRangeLibrarySummary> libraries = new ArrayList<>();
        boolean driftDetected = false;
        for (java.util.Map.Entry<String, List<CertificationCheckResult>> entry : byLibrary.entrySet()) {
            final List<CertificationCheckResult> sorted = entry.getValue().stream()
                    .sorted((left, right) -> compareVersions(left.version(), right.version()))
                    .toList();
            final String minVersion = sorted.getFirst().version();
            final String maxVersion = sorted.getLast().version();
            String firstFailingVersion = "";
            for (CertificationCheckResult check : sorted) {
                if (!check.passed()) {
                    firstFailingVersion = check.version();
                    driftDetected = true;
                    break;
                }
            }
            libraries.add(new VersionRangeLibrarySummary(
                    entry.getKey(),
                    minVersion,
                    maxVersion,
                    firstFailingVersion,
                    List.copyOf(sorted)
            ));
        }
        return new VersionRangeCertificationReport(
                "TSJ-44b-version-range-certification",
                Instant.now().toString(),
                !driftDetected,
                driftDetected,
                List.copyOf(libraries)
        );
    }

    private static int compareVersions(final String left, final String right) {
        final String[] leftParts = left.split("\\.");
        final String[] rightParts = right.split("\\.");
        final int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            final int leftPart = index < leftParts.length ? parseVersionPart(leftParts[index]) : 0;
            final int rightPart = index < rightParts.length ? parseVersionPart(rightParts[index]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int parseVersionPart(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private Path buildInteropJar(
            final String className,
            final String sourceText,
            final String jarFileName
    ) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for certification fixtures.");
        }

        final Path sourceRoot = tempDir.resolve("src-" + sanitizeSegment(className));
        final Path classesRoot = tempDir.resolve("classes-" + sanitizeSegment(className));
        final Path javaSource = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(javaSource.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(javaSource, sourceText, UTF_8);

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
                throw new IllegalStateException("Failed to compile certification fixture class " + className);
            }
        }

        final Path jarFile = tempDir.resolve(jarFileName);
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

    private static String sanitizeSegment(final String value) {
        return value.replace('.', '_');
    }

    private static Path jarPathForClass(final Class<?> type) throws Exception {
        final var codeSource = type.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("Missing code source for class " + type.getName());
        }
        return Path.of(codeSource.getLocation().toURI())
                .toAbsolutePath()
                .normalize();
    }

    private static String trim(final String value, final int maxChars) {
        final String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private record CertificationCheckResult(
            String id,
            String category,
            String library,
            String version,
            boolean passed,
            long durationMs,
            String details
    ) {
        private String toJson() {
            final StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append("\"id\":\"").append(escape(id)).append("\",");
            builder.append("\"category\":\"").append(escape(category)).append("\",");
            builder.append("\"library\":\"").append(escape(library)).append("\",");
            builder.append("\"version\":\"").append(escape(version)).append("\",");
            builder.append("\"passed\":").append(passed).append(",");
            builder.append("\"durationMs\":").append(durationMs).append(",");
            builder.append("\"details\":\"").append(escape(details)).append("\"");
            builder.append("}");
            return builder.toString();
        }
    }

    private record CertificationReport(
            String suite,
            String generatedAt,
            double minCoveragePercent,
            long maxAverageDurationMs,
            long maxCheckDurationMs,
            int totalChecks,
            int passedChecks,
            double coveragePercent,
            long averageDurationMs,
            long maxDurationMs,
            boolean subsetReady,
            List<CertificationCheckResult> checks
    ) {
        private String toJson() {
            final StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append("\"suite\":\"").append(escape(suite)).append("\",");
            builder.append("\"generatedAt\":\"").append(escape(generatedAt)).append("\",");
            builder.append("\"thresholds\":{");
            builder.append("\"minCoveragePercent\":")
                    .append(String.format(Locale.ROOT, "%.2f", minCoveragePercent))
                    .append(",");
            builder.append("\"maxAverageDurationMs\":").append(maxAverageDurationMs).append(",");
            builder.append("\"maxCheckDurationMs\":").append(maxCheckDurationMs);
            builder.append("},");
            builder.append("\"summary\":{");
            builder.append("\"totalChecks\":").append(totalChecks).append(",");
            builder.append("\"passedChecks\":").append(passedChecks).append(",");
            builder.append("\"coveragePercent\":")
                    .append(String.format(Locale.ROOT, "%.2f", coveragePercent))
                    .append(",");
            builder.append("\"averageDurationMs\":").append(averageDurationMs).append(",");
            builder.append("\"maxDurationMs\":").append(maxDurationMs).append(",");
            builder.append("\"subsetReady\":").append(subsetReady);
            builder.append("},");
            builder.append("\"checks\":[");
            for (int index = 0; index < checks.size(); index++) {
                if (index > 0) {
                    builder.append(",");
                }
                builder.append(checks.get(index).toJson());
            }
            builder.append("]");
            builder.append("}");
            return builder.toString();
        }
    }

    private record VersionRangeLibrarySummary(
            String library,
            String minVersion,
            String maxVersion,
            String firstFailingVersion,
            List<CertificationCheckResult> versions
    ) {
        private String toJson() {
            final StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append("\"library\":\"").append(escape(library)).append("\",");
            builder.append("\"minVersion\":\"").append(escape(minVersion)).append("\",");
            builder.append("\"maxVersion\":\"").append(escape(maxVersion)).append("\",");
            builder.append("\"firstFailingVersion\":\"").append(escape(firstFailingVersion)).append("\",");
            builder.append("\"versions\":[");
            for (int index = 0; index < versions.size(); index++) {
                if (index > 0) {
                    builder.append(",");
                }
                builder.append(versions.get(index).toJson());
            }
            builder.append("]");
            builder.append("}");
            return builder.toString();
        }
    }

    private record VersionRangeCertificationReport(
            String suite,
            String generatedAt,
            boolean subsetReady,
            boolean driftDetected,
            List<VersionRangeLibrarySummary> libraries
    ) {
        private String toJson() {
            final StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append("\"suite\":\"").append(escape(suite)).append("\",");
            builder.append("\"generatedAt\":\"").append(escape(generatedAt)).append("\",");
            builder.append("\"subsetReady\":").append(subsetReady).append(",");
            builder.append("\"driftDetected\":").append(driftDetected).append(",");
            builder.append("\"libraries\":[");
            for (int index = 0; index < libraries.size(); index++) {
                if (index > 0) {
                    builder.append(",");
                }
                builder.append(libraries.get(index).toJson());
            }
            builder.append("]");
            builder.append("}");
            return builder.toString();
        }
    }

    private static String escape(final String value) {
        final String safe = value == null ? "" : value;
        return safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
