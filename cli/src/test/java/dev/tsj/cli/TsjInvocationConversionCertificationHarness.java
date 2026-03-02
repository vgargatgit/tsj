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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-41d invocation/conversion parity certification harness.
 */
final class TsjInvocationConversionCertificationHarness {
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");
    private static final String REPORT_FILE = "tsj41d-invocation-conversion-certification.json";

    TsjInvocationConversionCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj41d-certification-work");
        Files.createDirectories(workRoot);

        final List<TsjInvocationConversionCertificationReport.ScenarioResult> scenarios = List.of(
                runNumericWideningSuccess(workRoot.resolve("numeric-widening-success")),
                runNumericNarrowingDiagnostic(workRoot.resolve("numeric-widening-diagnostic")),
                runGenericAdaptationSuccess(workRoot.resolve("generic-adaptation-success")),
                runGenericAdaptationDiagnostic(workRoot.resolve("generic-adaptation-diagnostic")),
                runReflectiveSuccess(workRoot.resolve("reflective-edge-success")),
                runReflectiveDiagnostic(workRoot.resolve("reflective-edge-diagnostic"))
        );

        final List<TsjInvocationConversionCertificationReport.FamilySummary> families = summarizeFamilies(scenarios);
        final boolean gatePassed = families.stream().allMatch(TsjInvocationConversionCertificationReport.FamilySummary::passed);

        final TsjInvocationConversionCertificationReport report = new TsjInvocationConversionCertificationReport(
                gatePassed,
                families,
                scenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static List<TsjInvocationConversionCertificationReport.FamilySummary> summarizeFamilies(
            final List<TsjInvocationConversionCertificationReport.ScenarioResult> scenarios
    ) {
        final Map<String, List<TsjInvocationConversionCertificationReport.ScenarioResult>> byFamily = new LinkedHashMap<>();
        byFamily.put("numeric-widening", new ArrayList<>());
        byFamily.put("generic-adaptation", new ArrayList<>());
        byFamily.put("reflective-edge", new ArrayList<>());

        for (TsjInvocationConversionCertificationReport.ScenarioResult scenario : scenarios) {
            byFamily.computeIfAbsent(scenario.family(), ignored -> new ArrayList<>()).add(scenario);
        }

        final List<TsjInvocationConversionCertificationReport.FamilySummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<TsjInvocationConversionCertificationReport.ScenarioResult>> entry : byFamily.entrySet()) {
            final int scenarioCount = entry.getValue().size();
            final int passedCount = (int) entry.getValue().stream()
                    .filter(TsjInvocationConversionCertificationReport.ScenarioResult::passed)
                    .count();
            summaries.add(new TsjInvocationConversionCertificationReport.FamilySummary(
                    entry.getKey(),
                    scenarioCount > 0 && scenarioCount == passedCount,
                    scenarioCount,
                    passedCount
            ));
        }
        return List.copyOf(summaries);
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult runNumericWideningSuccess(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj41d-numeric-success.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { widenPrimitive, widenWrapper, primitiveVsWrapper } from "java:sample.numeric.NumericApi";

                    console.log("widenPrimitiveInt=" + widenPrimitive(5));
                    console.log("widenPrimitiveDouble=" + widenPrimitive(5.5));
                    console.log("widenWrapperInt=" + widenWrapper(5));
                    console.log("widenWrapperDouble=" + widenWrapper(5.5));
                    console.log("primitiveVsWrapper=" + primitiveVsWrapper(5));
                    """,
                    UTF_8
            );
            final Path jarFile = buildInteropJar(
                    workDir,
                    "sample.numeric.NumericApi",
                    """
                    package sample.numeric;

                    public final class NumericApi {
                        private NumericApi() {
                        }

                        public static String widenPrimitive(final long value) {
                            return "long";
                        }

                        public static String widenPrimitive(final double value) {
                            return "double";
                        }

                        public static String widenWrapper(final Long value) {
                            return "Long";
                        }

                        public static String widenWrapper(final Double value) {
                            return "Double";
                        }

                        public static String primitiveVsWrapper(final int value) {
                            return "int";
                        }

                        public static String primitiveVsWrapper(final Integer value) {
                            return "Integer";
                        }
                    }
                    """,
                    "numeric-api.jar"
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    jarFile.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("widenPrimitiveInt=long")
                    && command.stdout().contains("widenPrimitiveDouble=double")
                    && command.stdout().contains("widenWrapperInt=Long")
                    && command.stdout().contains("widenWrapperDouble=Double")
                    && command.stdout().contains("primitiveVsWrapper=int")
                    && command.stdout().contains("\"code\":\"TSJ-RUN-SUCCESS\"")
                    && command.stderr().isBlank();
            return scenarioResult(
                    "numeric-widening",
                    "numeric-widening-success",
                    "tsj41a-numeric-success",
                    "sample.numeric.NumericApi",
                    "1.0",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure(
                    "numeric-widening",
                    "numeric-widening-success",
                    "tsj41a-numeric-success",
                    "sample.numeric.NumericApi",
                    "1.0",
                    exception
            );
        }
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult runNumericNarrowingDiagnostic(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj41d-numeric-diagnostic.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { acceptByte } from "java:sample.numeric.NumericApi";

                    console.log("byte=" + acceptByte(130));
                    """,
                    UTF_8
            );
            final Path jarFile = buildInteropJar(
                    workDir,
                    "sample.numeric.NumericApi",
                    """
                    package sample.numeric;

                    public final class NumericApi {
                        private NumericApi() {
                        }

                        public static String acceptByte(final byte value) {
                            return "byte=" + value;
                        }
                    }
                    """,
                    "numeric-api-diagnostic.jar"
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    jarFile.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-RUN-006".equals(diagnosticCode)
                    && command.stderr().contains("numeric conversion")
                    && command.stderr().contains("narrowing")
                    && command.stderr().contains("byte")
                    && command.stdout().isBlank();
            return scenarioResult(
                    "numeric-widening",
                    "numeric-narrowing-diagnostic",
                    "tsj41a-numeric-diagnostic",
                    "sample.numeric.NumericApi",
                    "1.0",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure(
                    "numeric-widening",
                    "numeric-narrowing-diagnostic",
                    "tsj41a-numeric-diagnostic",
                    "sample.numeric.NumericApi",
                    "1.0",
                    exception
            );
        }
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult runGenericAdaptationSuccess(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj41d-generic-success.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { sumNestedCounts, optionalIntegerSummary, weightedTotal } from "java:sample.generic.GenericApi";

                    const payload = [{ count: "2" }, { count: 3 }];
                    const weights = { "2": ["1.5", "2.25"], "3": [1] };

                    console.log("sumNested=" + sumNestedCounts(payload));
                    console.log("optionalPresent=" + optionalIntegerSummary(["4", "5"]));
                    console.log("optionalEmpty=" + optionalIntegerSummary(undefined));
                    console.log("weighted=" + weightedTotal(weights));
                    """,
                    UTF_8
            );
            final Path jarFile = buildInteropJar(
                    workDir,
                    "sample.generic.GenericApi",
                    """
                    package sample.generic;

                    import java.util.List;
                    import java.util.Map;
                    import java.util.Optional;

                    public final class GenericApi {
                        private GenericApi() {
                        }

                        public static int sumNestedCounts(final List<Map<String, Integer>> payload) {
                            int total = 0;
                            for (Map<String, Integer> row : payload) {
                                total += row.get("count");
                            }
                            return total;
                        }

                        public static String optionalIntegerSummary(final Optional<List<Integer>> values) {
                            if (values.isEmpty()) {
                                return "empty";
                            }
                            int total = 0;
                            for (Integer value : values.get()) {
                                total += value;
                            }
                            return "sum=" + total;
                        }

                        public static double weightedTotal(final Map<Integer, List<Double>> weighted) {
                            double total = 0.0d;
                            for (Map.Entry<Integer, List<Double>> entry : weighted.entrySet()) {
                                for (Double value : entry.getValue()) {
                                    total += entry.getKey() * value;
                                }
                            }
                            return total;
                        }
                    }
                    """,
                    "generic-api.jar"
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    jarFile.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("sumNested=5")
                    && command.stdout().contains("optionalPresent=sum=9")
                    && command.stdout().contains("optionalEmpty=empty")
                    && command.stdout().contains("weighted=10.5")
                    && command.stdout().contains("\"code\":\"TSJ-RUN-SUCCESS\"")
                    && command.stderr().isBlank();
            return scenarioResult(
                    "generic-adaptation",
                    "generic-adaptation-success",
                    "tsj41b-generic-success",
                    "sample.generic.GenericApi",
                    "1.0",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure(
                    "generic-adaptation",
                    "generic-adaptation-success",
                    "tsj41b-generic-success",
                    "sample.generic.GenericApi",
                    "1.0",
                    exception
            );
        }
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult runGenericAdaptationDiagnostic(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj41d-generic-diagnostic.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { joinModes } from "java:sample.generic.GenericApi";

                    console.log("modes=" + joinModes(["ALPHA", "GAMMA"]));
                    """,
                    UTF_8
            );
            final Path jarFile = buildInteropJar(
                    workDir,
                    "sample.generic.GenericApi",
                    """
                    package sample.generic;

                    import java.util.List;

                    public final class GenericApi {
                        private GenericApi() {
                        }

                        public enum Mode {
                            ALPHA,
                            BETA
                        }

                        public static String joinModes(final List<Mode> modes) {
                            final StringBuilder builder = new StringBuilder();
                            for (int index = 0; index < modes.size(); index++) {
                                if (index > 0) {
                                    builder.append(",");
                                }
                                builder.append(modes.get(index).name());
                            }
                            return builder.toString();
                        }
                    }
                    """,
                    "generic-api-diagnostic.jar"
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    jarFile.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-RUN-006".equals(diagnosticCode)
                    && command.stderr().contains("Generic interop conversion failed")
                    && command.stderr().contains("java.util.List")
                    && command.stderr().contains("sample.generic.GenericApi$Mode")
                    && command.stderr().contains("Unknown enum constant")
                    && command.stdout().isBlank();
            return scenarioResult(
                    "generic-adaptation",
                    "generic-adaptation-diagnostic",
                    "tsj41b-generic-diagnostic",
                    "sample.generic.GenericApi",
                    "1.0",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure(
                    "generic-adaptation",
                    "generic-adaptation-diagnostic",
                    "tsj41b-generic-diagnostic",
                    "sample.generic.GenericApi",
                    "1.0",
                    exception
            );
        }
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult runReflectiveSuccess(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj41d-reflective-success.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { $new as newGreeter, $instance$greet as greet } from "java:sample.reflective.ReflectiveApi$GreeterImpl";
                    import { $new as newBox, $instance$bump as bump } from "java:sample.reflective.ReflectiveApi$IntegerBridgeSample";

                    const greeter = newGreeter();
                    const box = newBox();

                    console.log("greet=" + greet(greeter, "tsj"));
                    console.log("bump=" + bump(box, 7));
                    """,
                    UTF_8
            );
            final Path jarFile = buildInteropJar(
                    workDir,
                    "sample.reflective.ReflectiveApi",
                    """
                    package sample.reflective;

                    public final class ReflectiveApi {
                        private ReflectiveApi() {
                        }

                        public interface Greeter {
                            default String greet(final String name) {
                                return "hello-" + name;
                            }
                        }

                        public static final class GreeterImpl implements Greeter {
                        }

                        public static class NumberBridgeBase<T extends Number> {
                            public T bump(final T value) {
                                return value;
                            }
                        }

                        public static final class IntegerBridgeSample extends NumberBridgeBase<Integer> {
                            @Override
                            public Integer bump(final Integer value) {
                                return value + 1;
                            }
                        }
                    }
                    """,
                    "reflective-api.jar"
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    jarFile.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("greet=hello-tsj")
                    && command.stdout().contains("bump=8")
                    && command.stdout().contains("\"code\":\"TSJ-RUN-SUCCESS\"")
                    && command.stderr().isBlank();
            return scenarioResult(
                    "reflective-edge",
                    "reflective-default-bridge-success",
                    "tsj41c-reflective-success",
                    "sample.reflective.ReflectiveApi",
                    "1.0",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure(
                    "reflective-edge",
                    "reflective-default-bridge-success",
                    "tsj41c-reflective-success",
                    "sample.reflective.ReflectiveApi",
                    "1.0",
                    exception
            );
        }
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult runReflectiveDiagnostic(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj41d-reflective-diagnostic.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { hiddenStatic } from "java:sample.reflective.ReflectiveApi";

                    console.log(hiddenStatic());
                    """,
                    UTF_8
            );
            final Path jarFile = buildInteropJar(
                    workDir,
                    "sample.reflective.ReflectiveApi",
                    """
                    package sample.reflective;

                    public final class ReflectiveApi {
                        private ReflectiveApi() {
                        }

                        static String hiddenStatic() {
                            return "hidden";
                        }
                    }
                    """,
                    "reflective-api-diagnostic.jar"
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--jar",
                    jarFile.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-INTEROP-INVALID".equals(diagnosticCode)
                    && command.stderr().contains("hiddenStatic")
                    && command.stderr().contains("not found or not static")
                    && command.stdout().isBlank();
            return scenarioResult(
                    "reflective-edge",
                    "reflective-nonpublic-diagnostic",
                    "tsj41c-reflective-diagnostic",
                    "sample.reflective.ReflectiveApi",
                    "1.0",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure(
                    "reflective-edge",
                    "reflective-nonpublic-diagnostic",
                    "tsj41c-reflective-diagnostic",
                    "sample.reflective.ReflectiveApi",
                    "1.0",
                    exception
            );
        }
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult scenarioResult(
            final String family,
            final String scenario,
            final String fixture,
            final String library,
            final String version,
            final boolean passed,
            final String diagnosticCode,
            final String notes
    ) {
        return new TsjInvocationConversionCertificationReport.ScenarioResult(
                family,
                scenario,
                fixture,
                library,
                version,
                passed,
                diagnosticCode,
                notes
        );
    }

    private static TsjInvocationConversionCertificationReport.ScenarioResult scenarioFailure(
            final String family,
            final String scenario,
            final String fixture,
            final String library,
            final String version,
            final Exception exception
    ) {
        final String notes = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return scenarioResult(family, scenario, fixture, library, version, false, "", notes);
    }

    private static String buildNotes(final CommandResult command) {
        final String stderr = command.stderr().isBlank() ? "" : " stderr=" + command.stderr().trim();
        return "exit=" + command.exitCode() + stderr;
    }

    private static CommandResult executeCli(final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(args, new PrintStream(stdout), new PrintStream(stderr));
        return new CommandResult(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8));
    }

    private static String extractDiagnosticCode(final String stderrText) {
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(stderrText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static Path buildInteropJar(
            final Path workDir,
            final String className,
            final String sourceText,
            final String jarFileName
    ) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for integration tests.");
        }
        final Path sourceRoot = workDir.resolve("java-src-" + sanitizeFileSegment(className));
        final Path classesRoot = workDir.resolve("java-classes-" + sanitizeFileSegment(className));
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
                throw new IllegalStateException("Failed to compile Java fixture class " + className);
            }
        }

        final Path jarFile = workDir.resolve(jarFileName);
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            try (Stream<Path> paths = Files.walk(classesRoot)) {
                final List<Path> classFiles = paths
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .sorted(Comparator.naturalOrder())
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

    private static String sanitizeFileSegment(final String value) {
        return value.replace('.', '_');
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjInvocationConversionCertificationHarness.class
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
            final TsjInvocationConversionCertificationReport report
    ) throws IOException {
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalizedReportPath.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalizedReportPath, report.toJson() + "\n", UTF_8);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
