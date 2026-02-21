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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-44b version-range compatibility drift certification harness.
 */
final class TsjVersionRangeCertificationHarness {
    private static final String REPORT_FILE = "tsj44b-version-range-certification.json";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");

    TsjVersionRangeCertificationReport run(final Path reportPath) throws Exception {
        return runInternal(reportPath, false);
    }

    TsjVersionRangeCertificationReport runWithDrift(final Path reportPath) throws Exception {
        return runInternal(reportPath, true);
    }

    private TsjVersionRangeCertificationReport runInternal(
            final Path reportPath,
            final boolean injectDrift
    ) throws Exception {
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReportPath
                .getParent()
                .resolve(injectDrift ? "tsj44b-certification-work-drift" : "tsj44b-certification-work");
        Files.createDirectories(workRoot);

        final List<TsjVersionRangeCertificationReport.LibraryRangeResult> libraries = List.of(
                runRangeApiVersionRange(workRoot.resolve("range-api"), injectDrift),
                runTextApiVersionRange(workRoot.resolve("text-api"))
        );
        final boolean driftDetected = libraries.stream()
                .anyMatch(library -> !library.passed());
        final boolean gatePassed = !driftDetected;

        final TsjVersionRangeCertificationReport report = new TsjVersionRangeCertificationReport(
                gatePassed,
                driftDetected,
                libraries,
                normalizedReportPath,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static TsjVersionRangeCertificationReport.LibraryRangeResult runRangeApiVersionRange(
            final Path workDir,
            final boolean injectDrift
    ) throws Exception {
        Files.createDirectories(workDir);
        final List<TsjVersionRangeCertificationReport.VersionCheckResult> checks = new ArrayList<>();

        checks.add(runVersionCheck(
                workDir,
                "sample.range.RangeApi",
                "1.0.0",
                """
                package sample.range;

                public final class RangeApi {
                    private RangeApi() {
                    }

                    public static String mode() {
                        return "OK";
                    }
                }
                """,
                """
                import { mode } from "java:sample.range.RangeApi";
                console.log("range=" + mode());
                """,
                "range=OK"
        ));

        checks.add(runVersionCheck(
                workDir,
                "sample.range.RangeApi",
                "1.1.0",
                injectDrift
                        ? """
                        package sample.range;

                        public final class RangeApi {
                            private RangeApi() {
                            }

                            public static String mode() {
                                return "REGRESSION";
                            }
                        }
                        """
                        : """
                        package sample.range;

                        public final class RangeApi {
                            private RangeApi() {
                            }

                            public static String mode() {
                                return "OK";
                            }
                        }
                        """,
                """
                import { mode } from "java:sample.range.RangeApi";
                console.log("range=" + mode());
                """,
                "range=OK"
        ));

        return summarizeRange("utility", "sample.range.RangeApi", "[1.0.0,2.0.0)", checks);
    }

    private static TsjVersionRangeCertificationReport.LibraryRangeResult runTextApiVersionRange(
            final Path workDir
    ) throws Exception {
        Files.createDirectories(workDir);
        final List<TsjVersionRangeCertificationReport.VersionCheckResult> checks = new ArrayList<>();
        checks.add(runVersionCheck(
                workDir,
                "sample.range.TextApi",
                "3.0.0",
                """
                package sample.range;

                public final class TextApi {
                    private TextApi() {
                    }

                    public static String encode(final String value) {
                        return value.toUpperCase();
                    }
                }
                """,
                """
                import { encode } from "java:sample.range.TextApi";
                console.log("text=" + encode("tsj"));
                """,
                "text=TSJ"
        ));
        checks.add(runVersionCheck(
                workDir,
                "sample.range.TextApi",
                "3.1.0",
                """
                package sample.range;

                public final class TextApi {
                    private TextApi() {
                    }

                    public static String encode(final String value) {
                        return value.toUpperCase();
                    }
                }
                """,
                """
                import { encode } from "java:sample.range.TextApi";
                console.log("text=" + encode("tsj"));
                """,
                "text=TSJ"
        ));
        return summarizeRange("serialization", "sample.range.TextApi", "[3.0.0,4.0.0)", checks);
    }

    private static TsjVersionRangeCertificationReport.LibraryRangeResult summarizeRange(
            final String category,
            final String library,
            final String certifiedRange,
            final List<TsjVersionRangeCertificationReport.VersionCheckResult> checks
    ) {
        final List<TsjVersionRangeCertificationReport.VersionCheckResult> sortedChecks = checks.stream()
                .sorted((left, right) -> compareVersions(left.version(), right.version()))
                .toList();
        String firstFailingVersion = "";
        for (TsjVersionRangeCertificationReport.VersionCheckResult check : sortedChecks) {
            if (!check.passed()) {
                firstFailingVersion = check.version();
                break;
            }
        }
        return new TsjVersionRangeCertificationReport.LibraryRangeResult(
                category,
                library,
                certifiedRange,
                firstFailingVersion.isEmpty(),
                firstFailingVersion,
                sortedChecks
        );
    }

    private static TsjVersionRangeCertificationReport.VersionCheckResult runVersionCheck(
            final Path workDir,
            final String className,
            final String version,
            final String javaSource,
            final String tsSource,
            final String expectedStdoutMarker
    ) throws Exception {
        final Path versionDir = workDir.resolve(version.replace('.', '_'));
        Files.createDirectories(versionDir);

        final Path jarFile = buildInteropJar(versionDir, className, version, javaSource);
        final Path entryFile = versionDir.resolve("entry.ts");
        Files.writeString(entryFile, tsSource, UTF_8);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final long startNanos = System.nanoTime();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "run",
                        entryFile.toString(),
                        "--out",
                        versionDir.resolve("out").toString(),
                        "--jar",
                        jarFile.toString(),
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk"
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        final long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

        final String stdoutText = stdout.toString(UTF_8);
        final String stderrText = stderr.toString(UTF_8);
        final boolean passed = exitCode == 0 && stdoutText.contains(expectedStdoutMarker);
        final String diagnosticCode = extractDiagnosticCode(stderrText);
        final String notes = "jar="
                + jarFile.getFileName()
                + ",expected="
                + expectedStdoutMarker
                + ",exit="
                + exitCode
                + ",stdout="
                + trim(stdoutText, 100)
                + ",stderr="
                + trim(stderrText, 100);
        return new TsjVersionRangeCertificationReport.VersionCheckResult(
                version,
                passed,
                durationMs,
                diagnosticCode,
                notes
        );
    }

    private static Path buildInteropJar(
            final Path versionDir,
            final String className,
            final String version,
            final String sourceText
    ) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-44b certification harness.");
        }

        final Path sourceRoot = versionDir.resolve("src");
        final Path classesRoot = versionDir.resolve("classes");
        final Path javaSource = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(javaSource.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(javaSource, sourceText, UTF_8);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(javaSource));
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-d",
                    classesRoot.toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile version fixture class " + className + ":" + version);
            }
        }

        final String simpleName = className.substring(className.lastIndexOf('.') + 1);
        final Path jarFile = versionDir.resolve(simpleName + "-" + version + ".jar");
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

    private static int compareVersions(final String left, final String right) {
        final String[] leftParts = left.split("\\.");
        final String[] rightParts = right.split("\\.");
        final int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            final int leftValue = index < leftParts.length ? parseVersionPart(leftParts[index]) : 0;
            final int rightValue = index < rightParts.length ? parseVersionPart(rightParts[index]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
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

    private static String extractDiagnosticCode(final String stderr) {
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(stderr == null ? "" : stderr);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String trim(final String value, final int maxChars) {
        final String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjVersionRangeCertificationHarness.class
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

    private static void writeReport(
            final Path reportPath,
            final TsjVersionRangeCertificationReport report
    ) throws IOException {
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalizedReportPath.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalizedReportPath, report.toJson() + "\n", UTF_8);
    }
}
