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
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-40d dependency mediation parity certification harness.
 */
final class TsjDependencyMediationCertificationHarness {
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");
    private static final String REPORT_FILE = "tsj40d-dependency-mediation-certification.json";

    TsjDependencyMediationCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj40d-certification-work");
        Files.createDirectories(workRoot);

        final List<TsjDependencyMediationCertificationReport.GraphFixtureResult> graphFixtures = List.of(
                runNearestMediationFixture(workRoot.resolve("graph-nearest")),
                runRootOrderMediationFixture(workRoot.resolve("graph-root-order"))
        );
        final List<TsjDependencyMediationCertificationReport.ScopePathResult> scopePaths = List.of(
                runCompileScopeFixture(workRoot.resolve("scope-compile")),
                runRuntimeScopeFixture(workRoot.resolve("scope-runtime"))
        );
        final List<TsjDependencyMediationCertificationReport.IsolationModeResult> isolationModes = List.of(
                runSharedIsolationFixture(workRoot.resolve("isolation-shared")),
                runAppIsolatedConflictFixture(workRoot.resolve("isolation-app-conflict"))
        );

        final boolean gatePassed = graphFixtures.stream()
                .allMatch(TsjDependencyMediationCertificationReport.GraphFixtureResult::passed)
                && scopePaths.stream().allMatch(TsjDependencyMediationCertificationReport.ScopePathResult::passed)
                && isolationModes.stream().allMatch(TsjDependencyMediationCertificationReport.IsolationModeResult::passed);

        final TsjDependencyMediationCertificationReport report = new TsjDependencyMediationCertificationReport(
                gatePassed,
                graphFixtures,
                scopePaths,
                isolationModes,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static TsjDependencyMediationCertificationReport.GraphFixtureResult runNearestMediationFixture(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final MavenCoordinate sharedOneCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "1.0.0");
            final MavenCoordinate sharedTwoCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "2.0.0");
            final MavenCoordinate bridgeCoordinate = new MavenCoordinate("sample.graph", "bridge-lib", "1.0.0");
            final MavenCoordinate nearCoordinate = new MavenCoordinate("sample.graph", "near-api", "1.0.0");
            final MavenCoordinate farCoordinate = new MavenCoordinate("sample.graph", "far-api", "1.0.0");

            final Path sharedOne = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.Shared",
                    """
                    package sample.graph;

                    public final class Shared {
                        private Shared() {
                        }

                        public static String label() {
                            return "shared-v1";
                        }
                    }
                    """,
                    List.of(),
                    "shared-lib-1.0.0.jar",
                    sharedOneCoordinate,
                    List.of()
            );
            final Path sharedTwo = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.Shared",
                    """
                    package sample.graph;

                    public final class Shared {
                        private Shared() {
                        }

                        public static String label() {
                            return "shared-v2";
                        }
                    }
                    """,
                    List.of(),
                    "shared-lib-2.0.0.jar",
                    sharedTwoCoordinate,
                    List.of()
            );
            final Path bridgeJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.Bridge",
                    """
                    package sample.graph;

                    public final class Bridge {
                        private Bridge() {
                        }

                        public static String relay() {
                            return Shared.label();
                        }
                    }
                    """,
                    List.of(sharedTwo),
                    "bridge-lib-1.0.0.jar",
                    bridgeCoordinate,
                    List.of(new MavenDependencySpec(sharedTwoCoordinate, null))
            );
            final Path nearJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.NearApi",
                    """
                    package sample.graph;

                    public final class NearApi {
                        private NearApi() {
                        }

                        public static String describe() {
                            return "near->" + Shared.label();
                        }
                    }
                    """,
                    List.of(sharedOne),
                    "near-api-1.0.0.jar",
                    nearCoordinate,
                    List.of(new MavenDependencySpec(sharedOneCoordinate, null))
            );
            final Path farJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.FarApi",
                    """
                    package sample.graph;

                    public final class FarApi {
                        private FarApi() {
                        }

                        public static String describe() {
                            return "far->" + Bridge.relay();
                        }
                    }
                    """,
                    List.of(bridgeJar),
                    "far-api-1.0.0.jar",
                    farCoordinate,
                    List.of(new MavenDependencySpec(bridgeCoordinate, null))
            );

            final Path entryFile = workDir.resolve("tsj40d-nearest.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { describe as nearDescribe } from "java:sample.graph.NearApi";
                    import { describe as farDescribe } from "java:sample.graph.FarApi";

                    console.log(nearDescribe());
                    console.log(farDescribe());
                    """,
                    UTF_8
            );
            final Path outDir = workDir.resolve("out");
            final String classpath = String.join(
                    File.pathSeparator,
                    nearJar.toString(),
                    farJar.toString(),
                    bridgeJar.toString(),
                    sharedOne.toString(),
                    sharedTwo.toString()
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    outDir.toString(),
                    "--classpath",
                    classpath,
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
            final MediationDecision decision = findMediationDecision(artifact, "sample.graph:shared-lib");

            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("near->shared-v1")
                    && command.stdout().contains("far->shared-v1")
                    && "1.0.0".equals(decision.selectedVersion())
                    && "2.0.0".equals(decision.rejectedVersion())
                    && "nearest".equals(decision.rule());
            return new TsjDependencyMediationCertificationReport.GraphFixtureResult(
                    "nearest-mediation",
                    passed,
                    decision.selectedVersion(),
                    decision.rejectedVersion(),
                    decision.rule(),
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return graphFailure("nearest-mediation", exception);
        }
    }

    private static TsjDependencyMediationCertificationReport.GraphFixtureResult runRootOrderMediationFixture(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final MavenCoordinate sharedOneCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "1.0.0");
            final MavenCoordinate sharedTwoCoordinate = new MavenCoordinate("sample.graph", "shared-lib", "2.0.0");
            final MavenCoordinate leftCoordinate = new MavenCoordinate("sample.graph", "left-api", "1.0.0");
            final MavenCoordinate rightCoordinate = new MavenCoordinate("sample.graph", "right-api", "1.0.0");

            final Path sharedOne = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.Shared",
                    """
                    package sample.graph;

                    public final class Shared {
                        private Shared() {
                        }

                        public static String label() {
                            return "shared-v1";
                        }
                    }
                    """,
                    List.of(),
                    "shared-lib-1.0.0.jar",
                    sharedOneCoordinate,
                    List.of()
            );
            final Path sharedTwo = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.Shared",
                    """
                    package sample.graph;

                    public final class Shared {
                        private Shared() {
                        }

                        public static String label() {
                            return "shared-v2";
                        }
                    }
                    """,
                    List.of(),
                    "shared-lib-2.0.0.jar",
                    sharedTwoCoordinate,
                    List.of()
            );
            final Path leftJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.LeftApi",
                    """
                    package sample.graph;

                    public final class LeftApi {
                        private LeftApi() {
                        }

                        public static String describe() {
                            return "left->" + Shared.label();
                        }
                    }
                    """,
                    List.of(sharedOne),
                    "left-api-1.0.0.jar",
                    leftCoordinate,
                    List.of(new MavenDependencySpec(sharedOneCoordinate, null))
            );
            final Path rightJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.graph.RightApi",
                    """
                    package sample.graph;

                    public final class RightApi {
                        private RightApi() {
                        }

                        public static String describe() {
                            return "right->" + Shared.label();
                        }
                    }
                    """,
                    List.of(sharedTwo),
                    "right-api-1.0.0.jar",
                    rightCoordinate,
                    List.of(new MavenDependencySpec(sharedTwoCoordinate, null))
            );

            final Path entryFile = workDir.resolve("tsj40d-root-order.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { describe as leftDescribe } from "java:sample.graph.LeftApi";
                    import { describe as rightDescribe } from "java:sample.graph.RightApi";

                    console.log(leftDescribe());
                    console.log(rightDescribe());
                    """,
                    UTF_8
            );
            final Path outDir = workDir.resolve("out");
            final String classpath = String.join(
                    File.pathSeparator,
                    rightJar.toString(),
                    leftJar.toString(),
                    sharedOne.toString(),
                    sharedTwo.toString()
            );

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    outDir.toString(),
                    "--classpath",
                    classpath,
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
            final MediationDecision decision = findMediationDecision(artifact, "sample.graph:shared-lib");

            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("left->shared-v2")
                    && command.stdout().contains("right->shared-v2")
                    && "2.0.0".equals(decision.selectedVersion())
                    && "1.0.0".equals(decision.rejectedVersion())
                    && "root-order".equals(decision.rule());
            return new TsjDependencyMediationCertificationReport.GraphFixtureResult(
                    "root-order-mediation",
                    passed,
                    decision.selectedVersion(),
                    decision.rejectedVersion(),
                    decision.rule(),
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return graphFailure("root-order-mediation", exception);
        }
    }

    private static TsjDependencyMediationCertificationReport.ScopePathResult runCompileScopeFixture(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final MavenCoordinate apiCoordinate = new MavenCoordinate("sample.scope", "api-lib", "1.0.0");
            final MavenCoordinate providedCoordinate = new MavenCoordinate("sample.scope", "provided-lib", "1.0.0");

            final Path providedJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.scope.ProvidedOnly",
                    """
                    package sample.scope;

                    public final class ProvidedOnly {
                        private ProvidedOnly() {
                        }

                        public static String ping() {
                            return "provided";
                        }
                    }
                    """,
                    List.of(),
                    "provided-lib-1.0.0.jar",
                    providedCoordinate,
                    List.of()
            );
            final Path apiJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.scope.Api",
                    """
                    package sample.scope;

                    public final class Api {
                        private Api() {
                        }
                    }
                    """,
                    List.of(providedJar),
                    "api-lib-1.0.0.jar",
                    apiCoordinate,
                    List.of(new MavenDependencySpec(providedCoordinate, "provided"))
            );

            final Path entryFile = workDir.resolve("tsj40d-compile-scope.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { ping } from "java:sample.scope.ProvidedOnly";
                    console.log("scope=" + ping());
                    """,
                    UTF_8
            );
            final Path outDir = workDir.resolve("out");
            final String classpath = String.join(File.pathSeparator, apiJar.toString(), providedJar.toString());

            final CommandResult command = executeCli(
                    "compile",
                    entryFile.toString(),
                    "--out",
                    outDir.toString(),
                    "--classpath",
                    classpath,
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final Properties artifact = loadArtifactProperties(outDir.resolve("program.tsj.properties"));
            final boolean passed = command.exitCode() == 0
                    && "compile".equals(artifact.getProperty("interopClasspath.scope.usage"))
                    && "compile,runtime,provided".equals(artifact.getProperty("interopClasspath.scope.allowed"))
                    && "0".equals(artifact.getProperty("interopClasspath.scope.excluded.count", "0"));
            return new TsjDependencyMediationCertificationReport.ScopePathResult(
                    "compile",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scopeFailure("compile", exception);
        }
    }

    private static TsjDependencyMediationCertificationReport.ScopePathResult runRuntimeScopeFixture(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final MavenCoordinate apiCoordinate = new MavenCoordinate("sample.scope", "api-lib", "1.0.0");
            final MavenCoordinate providedCoordinate = new MavenCoordinate("sample.scope", "provided-lib", "1.0.0");

            final Path providedJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.scope.ProvidedOnly",
                    """
                    package sample.scope;

                    public final class ProvidedOnly {
                        private ProvidedOnly() {
                        }

                        public static String ping() {
                            return "provided";
                        }
                    }
                    """,
                    List.of(),
                    "provided-lib-1.0.0.jar",
                    providedCoordinate,
                    List.of()
            );
            final Path apiJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.scope.Api",
                    """
                    package sample.scope;

                    public final class Api {
                        private Api() {
                        }
                    }
                    """,
                    List.of(providedJar),
                    "api-lib-1.0.0.jar",
                    apiCoordinate,
                    List.of(new MavenDependencySpec(providedCoordinate, "provided"))
            );

            final Path entryFile = workDir.resolve("tsj40d-runtime-scope.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { ping } from "java:sample.scope.ProvidedOnly";
                    console.log("scope=" + ping());
                    """,
                    UTF_8
            );
            final Path outDir = workDir.resolve("out");
            final String classpath = String.join(File.pathSeparator, apiJar.toString(), providedJar.toString());

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    outDir.toString(),
                    "--classpath",
                    classpath,
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk"
            );
            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-CLASSPATH-SCOPE".equals(diagnosticCode)
                    && command.stderr().contains("sample.scope.ProvidedOnly")
                    && command.stderr().contains("\"scope\":\"provided\"")
                    && command.stderr().contains("\"usage\":\"runtime\"");
            return new TsjDependencyMediationCertificationReport.ScopePathResult(
                    "runtime",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scopeFailure("runtime", exception);
        }
    }

    private static TsjDependencyMediationCertificationReport.IsolationModeResult runSharedIsolationFixture(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final Path helperJar = buildInteropJar(
                    workDir,
                    "sample.isolation.Helper",
                    """
                    package sample.isolation;

                    public final class Helper {
                        private Helper() {
                        }

                        public static String ping() {
                            return "shared";
                        }
                    }
                    """,
                    List.of(),
                    "helper.jar",
                    null,
                    List.of()
            );

            final Path entryFile = workDir.resolve("tsj40d-shared-isolation.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { ping } from "java:sample.isolation.Helper";
                    console.log("mode=" + ping());
                    """,
                    UTF_8
            );
            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--classpath",
                    helperJar.toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk",
                    "--classloader-isolation",
                    "shared"
            );

            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("mode=shared")
                    && command.stderr().isBlank();
            return new TsjDependencyMediationCertificationReport.IsolationModeResult(
                    "shared",
                    "success",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return isolationFailure("shared", "success", exception);
        }
    }

    private static TsjDependencyMediationCertificationReport.IsolationModeResult runAppIsolatedConflictFixture(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final MavenCoordinate duplicateCoordinate = new MavenCoordinate("sample.isolation", "dup-program", "1.0.0");
            final MavenCoordinate apiCoordinate = new MavenCoordinate("sample.isolation", "api-lib", "1.0.0");
            final String generatedMainClass = "dev.tsj.generated.Tsj40dAppIsolatedConflictProgram";

            final Path duplicateProgramJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    generatedMainClass,
                    """
                    package dev.tsj.generated;

                    public final class Tsj40dAppIsolatedConflictProgram {
                        private Tsj40dAppIsolatedConflictProgram() {
                        }

                        public static String identity() {
                            return "dependency-program";
                        }
                    }
                    """,
                    List.of(),
                    "dup-program-1.0.0.jar",
                    duplicateCoordinate,
                    List.of()
            );
            final Path apiJar = buildInteropJarWithMavenMetadata(
                    workDir,
                    "sample.isolation.Api",
                    """
                    package sample.isolation;

                    public final class Api {
                        private Api() {
                        }

                        public static String ping() {
                            return "api";
                        }
                    }
                    """,
                    List.of(duplicateProgramJar),
                    "api-lib-1.0.0.jar",
                    apiCoordinate,
                    List.of(new MavenDependencySpec(duplicateCoordinate, null))
            );

            final Path entryFile = workDir.resolve("tsj40d-app-isolated-conflict.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { ping } from "java:sample.isolation.Api";
                    console.log("value=" + ping());
                    """,
                    UTF_8
            );
            final String classpath = String.join(File.pathSeparator, apiJar.toString(), duplicateProgramJar.toString());
            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--classpath",
                    classpath,
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk",
                    "--classloader-isolation",
                    "app-isolated"
            );

            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-RUN-009".equals(diagnosticCode)
                    && command.stderr().contains(generatedMainClass)
                    && command.stderr().contains("app-isolated");
            return new TsjDependencyMediationCertificationReport.IsolationModeResult(
                    "app-isolated",
                    "conflict",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return isolationFailure("app-isolated", "conflict", exception);
        }
    }

    private static TsjDependencyMediationCertificationReport.GraphFixtureResult graphFailure(
            final String fixture,
            final Exception exception
    ) {
        final String notes = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return new TsjDependencyMediationCertificationReport.GraphFixtureResult(
                fixture,
                false,
                "",
                "",
                "",
                "",
                notes
        );
    }

    private static TsjDependencyMediationCertificationReport.ScopePathResult scopeFailure(
            final String scopePath,
            final Exception exception
    ) {
        final String notes = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return new TsjDependencyMediationCertificationReport.ScopePathResult(scopePath, false, "", notes);
    }

    private static TsjDependencyMediationCertificationReport.IsolationModeResult isolationFailure(
            final String mode,
            final String scenario,
            final Exception exception
    ) {
        final String notes = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return new TsjDependencyMediationCertificationReport.IsolationModeResult(mode, scenario, false, "", notes);
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

    private static Properties loadArtifactProperties(final Path artifactFile) throws IOException {
        final Properties properties = new Properties();
        try (java.io.InputStream inputStream = Files.newInputStream(artifactFile)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static MediationDecision findMediationDecision(final Properties artifact, final String artifactId) {
        final int count = Integer.parseInt(artifact.getProperty("interopClasspath.mediation.count", "0"));
        for (int index = 0; index < count; index++) {
            final String prefix = "interopClasspath.mediation." + index + ".";
            if (artifactId.equals(artifact.getProperty(prefix + "artifact"))) {
                return new MediationDecision(
                        artifact.getProperty(prefix + "selectedVersion", ""),
                        artifact.getProperty(prefix + "rejectedVersion", ""),
                        artifact.getProperty(prefix + "rule", "")
                );
            }
        }
        return new MediationDecision("", "", "");
    }

    private static String extractDiagnosticCode(final String stderrText) {
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(stderrText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static Path buildInteropJarWithMavenMetadata(
            final Path workDir,
            final String className,
            final String sourceText,
            final List<Path> compileClasspath,
            final String jarFileName,
            final MavenCoordinate coordinate,
            final List<MavenDependencySpec> dependencies
    ) throws Exception {
        return buildInteropJar(
                workDir,
                className,
                sourceText,
                compileClasspath,
                jarFileName,
                coordinate,
                dependencies
        );
    }

    private static Path buildInteropJar(
            final Path workDir,
            final String className,
            final String sourceText,
            final List<Path> compileClasspath,
            final String jarFileName,
            final MavenCoordinate coordinate,
            final List<MavenDependencySpec> dependencies
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
            final List<String> options = new ArrayList<>();
            options.add("--release");
            options.add("21");
            if (!compileClasspath.isEmpty()) {
                options.add("-classpath");
                options.add(
                        compileClasspath.stream()
                                .map(path -> path.toAbsolutePath().normalize().toString())
                                .reduce((left, right) -> left + File.pathSeparator + right)
                                .orElse("")
                );
            }
            options.add("-d");
            options.add(classesRoot.toString());
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
            if (coordinate != null) {
                final String mavenRoot = "META-INF/maven/" + coordinate.groupId() + "/" + coordinate.artifactId();
                writeTextJarEntry(
                        jarOutputStream,
                        mavenRoot + "/pom.properties",
                        """
                        groupId=%s
                        artifactId=%s
                        version=%s
                        """.formatted(coordinate.groupId(), coordinate.artifactId(), coordinate.version())
                );
                writeTextJarEntry(
                        jarOutputStream,
                        mavenRoot + "/pom.xml",
                        buildPomXml(coordinate, dependencies)
                );
            }
        }
        return jarFile.toAbsolutePath().normalize();
    }

    private static void writeTextJarEntry(
            final JarOutputStream jarOutputStream,
            final String entryName,
            final String content
    ) throws IOException {
        final JarEntry entry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(entry);
        jarOutputStream.write(content.getBytes(UTF_8));
        jarOutputStream.closeEntry();
    }

    private static String buildPomXml(
            final MavenCoordinate coordinate,
            final List<MavenDependencySpec> dependencies
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("<project>");
        builder.append("<modelVersion>4.0.0</modelVersion>");
        builder.append("<groupId>").append(coordinate.groupId()).append("</groupId>");
        builder.append("<artifactId>").append(coordinate.artifactId()).append("</artifactId>");
        builder.append("<version>").append(coordinate.version()).append("</version>");
        builder.append("<dependencies>");
        for (MavenDependencySpec dependency : dependencies) {
            builder.append("<dependency>");
            builder.append("<groupId>").append(dependency.coordinate().groupId()).append("</groupId>");
            builder.append("<artifactId>").append(dependency.coordinate().artifactId()).append("</artifactId>");
            builder.append("<version>").append(dependency.coordinate().version()).append("</version>");
            if (dependency.scope() != null && !dependency.scope().isBlank()) {
                builder.append("<scope>").append(dependency.scope()).append("</scope>");
            }
            builder.append("</dependency>");
        }
        builder.append("</dependencies>");
        builder.append("</project>");
        return builder.toString();
    }

    private static String sanitizeFileSegment(final String value) {
        return value.replace('.', '_');
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjDependencyMediationCertificationHarness.class
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
            final TsjDependencyMediationCertificationReport report
    ) throws IOException {
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalizedReportPath.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalizedReportPath, report.toJson() + "\n", UTF_8);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private record MediationDecision(String selectedVersion, String rejectedVersion, String rule) {
    }

    private record MavenCoordinate(String groupId, String artifactId, String version) {
    }

    private record MavenDependencySpec(MavenCoordinate coordinate, String scope) {
    }
}
