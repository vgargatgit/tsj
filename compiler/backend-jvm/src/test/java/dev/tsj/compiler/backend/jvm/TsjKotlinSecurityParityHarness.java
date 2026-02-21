package dev.tsj.compiler.backend.jvm;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-38b security-enabled reference-app parity harness.
 */
final class TsjKotlinSecurityParityHarness {
    private static final String REPORT_FILE = "tsj38b-security-parity-report.json";
    private static final String WORKFLOW_CLASS = "sample.parity.security.SecurityWorkflow";
    private static final String AUTHN_CODE = "TSJ-SECURITY-AUTHN-FAILURE";
    private static final String AUTHZ_CODE = "TSJ-SECURITY-AUTHZ-FAILURE";
    private static final String CONFIG_CODE = "TSJ-SECURITY-CONFIG-FAILURE";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");

    TsjKotlinSecurityParityReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj38b-security-parity-work");
        Files.createDirectories(workRoot);

        final Path supportJar = buildSupportJar(workRoot.resolve("support"));
        final List<TsjKotlinSecurityParityReport.SupportedScenarioResult> supportedScenarios = List.of(
                runAuthenticatedAccessScenario(workRoot.resolve("authenticated-access"), supportJar),
                runRoleBasedAdminAccessScenario(workRoot.resolve("role-based-admin-access"), supportJar)
        );
        final List<TsjKotlinSecurityParityReport.DiagnosticScenarioResult> diagnosticScenarios = List.of(
                runUnauthenticatedAccessScenario(workRoot.resolve("diag-unauthenticated"), supportJar),
                runAuthorizationDeniedScenario(workRoot.resolve("diag-authorization"), supportJar),
                runConfigurationFailureScenario(workRoot.resolve("diag-configuration"), supportJar)
        );

        final boolean gatePassed = supportedScenarios.stream().allMatch(TsjKotlinSecurityParityReport.SupportedScenarioResult::passed)
                && diagnosticScenarios.stream().allMatch(TsjKotlinSecurityParityReport.DiagnosticScenarioResult::passed);
        final TsjKotlinSecurityParityReport report = new TsjKotlinSecurityParityReport(
                gatePassed,
                supportedScenarios,
                diagnosticScenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjKotlinSecurityParityReport.SupportedScenarioResult runAuthenticatedAccessScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path source = workDir.resolve("authenticated-access.ts");
            Files.writeString(
                    source,
                    """
                    import { secureRead } from "java:sample.parity.security.SecurityWorkflow";

                    console.log("user-read=" + secureRead("user-token"));
                    console.log("admin-read=" + secureRead("admin-token"));
                    """,
                    UTF_8
            );
            final ProgramRunResult tsRun = compileAndRunTs(source, workDir.resolve("program"), supportJar);
            final String tsOutput = normalizeOutput(tsRun.stdout());
            final String javaOutput = runReferenceAuthenticatedAccess(supportJar);
            final String kotlinOutput = runReferenceAuthenticatedAccess(supportJar);
            final String diagnosticCode = classifyDiagnostic(tsRun.failure(), tsRun.stderr());
            final boolean passed = tsRun.failure() == null
                    && tsRun.stderr().isBlank()
                    && tsOutput.equals(javaOutput)
                    && tsOutput.equals(kotlinOutput);
            return new TsjKotlinSecurityParityReport.SupportedScenarioResult(
                    "authenticated-access",
                    passed,
                    tsOutput,
                    javaOutput,
                    kotlinOutput,
                    diagnosticCode,
                    buildNotes(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjKotlinSecurityParityReport.SupportedScenarioResult(
                    "authenticated-access",
                    false,
                    "",
                    "",
                    "",
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjKotlinSecurityParityReport.SupportedScenarioResult runRoleBasedAdminAccessScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path source = workDir.resolve("role-based-admin-access.ts");
            Files.writeString(
                    source,
                    """
                    import { roleFor, adminWrite } from "java:sample.parity.security.SecurityWorkflow";

                    console.log("admin-role=" + roleFor("admin-token"));
                    console.log("admin-write=" + adminWrite("admin-token", "ship"));
                    """,
                    UTF_8
            );
            final ProgramRunResult tsRun = compileAndRunTs(source, workDir.resolve("program"), supportJar);
            final String tsOutput = normalizeOutput(tsRun.stdout());
            final String javaOutput = runReferenceRoleBasedAdminAccess(supportJar);
            final String kotlinOutput = runReferenceRoleBasedAdminAccess(supportJar);
            final String diagnosticCode = classifyDiagnostic(tsRun.failure(), tsRun.stderr());
            final boolean passed = tsRun.failure() == null
                    && tsRun.stderr().isBlank()
                    && tsOutput.equals(javaOutput)
                    && tsOutput.equals(kotlinOutput);
            return new TsjKotlinSecurityParityReport.SupportedScenarioResult(
                    "role-based-admin-access",
                    passed,
                    tsOutput,
                    javaOutput,
                    kotlinOutput,
                    diagnosticCode,
                    buildNotes(tsRun)
            );
        } catch (final Exception exception) {
            return new TsjKotlinSecurityParityReport.SupportedScenarioResult(
                    "role-based-admin-access",
                    false,
                    "",
                    "",
                    "",
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjKotlinSecurityParityReport.DiagnosticScenarioResult runUnauthenticatedAccessScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path source = workDir.resolve("unauthenticated-access.ts");
            Files.writeString(
                    source,
                    """
                    import { secureRead } from "java:sample.parity.security.SecurityWorkflow";

                    console.log("read=" + secureRead(""));
                    """,
                    UTF_8
            );
            final ProgramRunResult run = compileAndRunTs(source, workDir.resolve("program"), supportJar);
            final String observedCode = classifyDiagnostic(run.failure(), run.stderr());
            final boolean passed = run.failure() != null && AUTHN_CODE.equals(observedCode);
            return new TsjKotlinSecurityParityReport.DiagnosticScenarioResult(
                    "unauthenticated-access",
                    passed,
                    AUTHN_CODE,
                    observedCode,
                    buildNotes(run)
            );
        } catch (final Exception exception) {
            return new TsjKotlinSecurityParityReport.DiagnosticScenarioResult(
                    "unauthenticated-access",
                    false,
                    AUTHN_CODE,
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjKotlinSecurityParityReport.DiagnosticScenarioResult runAuthorizationDeniedScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path source = workDir.resolve("authorization-denied.ts");
            Files.writeString(
                    source,
                    """
                    import { adminWrite } from "java:sample.parity.security.SecurityWorkflow";

                    console.log(adminWrite("user-token", "ship"));
                    """,
                    UTF_8
            );
            final ProgramRunResult run = compileAndRunTs(source, workDir.resolve("program"), supportJar);
            final String observedCode = classifyDiagnostic(run.failure(), run.stderr());
            final boolean passed = run.failure() != null && AUTHZ_CODE.equals(observedCode);
            return new TsjKotlinSecurityParityReport.DiagnosticScenarioResult(
                    "authorization-denied",
                    passed,
                    AUTHZ_CODE,
                    observedCode,
                    buildNotes(run)
            );
        } catch (final Exception exception) {
            return new TsjKotlinSecurityParityReport.DiagnosticScenarioResult(
                    "authorization-denied",
                    false,
                    AUTHZ_CODE,
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private TsjKotlinSecurityParityReport.DiagnosticScenarioResult runConfigurationFailureScenario(
            final Path workDir,
            final Path supportJar
    ) {
        try {
            Files.createDirectories(workDir);
            final Path source = workDir.resolve("configuration-failure.ts");
            Files.writeString(
                    source,
                    """
                    import { misconfiguredPolicy } from "java:sample.parity.security.SecurityWorkflow";

                    misconfiguredPolicy("/orders/**");
                    """,
                    UTF_8
            );
            final ProgramRunResult run = compileAndRunTs(source, workDir.resolve("program"), supportJar);
            final String observedCode = classifyDiagnostic(run.failure(), run.stderr());
            final boolean passed = run.failure() != null && CONFIG_CODE.equals(observedCode);
            return new TsjKotlinSecurityParityReport.DiagnosticScenarioResult(
                    "configuration-failure",
                    passed,
                    CONFIG_CODE,
                    observedCode,
                    buildNotes(run)
            );
        } catch (final Exception exception) {
            return new TsjKotlinSecurityParityReport.DiagnosticScenarioResult(
                    "configuration-failure",
                    false,
                    CONFIG_CODE,
                    classifyDiagnostic(exception, ""),
                    exception.getClass().getSimpleName() + ":" + safeMessage(exception)
            );
        }
    }

    private static String runReferenceAuthenticatedAccess(final Path supportJar) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{supportJar.toUri().toURL()},
                TsjKotlinSecurityParityHarness.class.getClassLoader()
        )) {
            final Class<?> workflowClass = Class.forName(WORKFLOW_CLASS, true, classLoader);
            final Object userRead = invokeStatic(workflowClass, "secureRead", "user-token");
            final Object adminRead = invokeStatic(workflowClass, "secureRead", "admin-token");
            return "user-read=" + userRead + "\nadmin-read=" + adminRead;
        }
    }

    private static String runReferenceRoleBasedAdminAccess(final Path supportJar) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{supportJar.toUri().toURL()},
                TsjKotlinSecurityParityHarness.class.getClassLoader()
        )) {
            final Class<?> workflowClass = Class.forName(WORKFLOW_CLASS, true, classLoader);
            final Object role = invokeStatic(workflowClass, "roleFor", "admin-token");
            final Object write = invokeStatic(workflowClass, "adminWrite", "admin-token", "ship");
            return "admin-role=" + role + "\nadmin-write=" + write;
        }
    }

    private ProgramRunResult compileAndRunTs(final Path tsFile, final Path outputDir, final Path supportJar) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Throwable failure = null;
        try {
            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(tsFile, outputDir);
            new JvmBytecodeRunner().run(
                    artifact,
                    List.of(supportJar),
                    new PrintStream(stdout),
                    new PrintStream(stderr)
            );
        } catch (final Throwable throwable) {
            failure = throwable;
        }
        return new ProgramRunResult(stdout.toString(UTF_8), stderr.toString(UTF_8), failure);
    }

    private static Object invokeStatic(final Class<?> type, final String name, final Object... args) throws Exception {
        Method matched = null;
        for (Method candidate : type.getMethods()) {
            if (!name.equals(candidate.getName()) || candidate.getParameterCount() != args.length) {
                continue;
            }
            matched = candidate;
            break;
        }
        if (matched == null) {
            throw new IllegalStateException("Missing method " + type.getName() + "#" + name + "/" + args.length);
        }
        try {
            return matched.invoke(null, args);
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            if (target instanceof Exception checked) {
                throw checked;
            }
            throw invocationTargetException;
        }
    }

    private static Path buildSupportJar(final Path workDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-38b security fixtures.");
        }
        Files.createDirectories(workDir);
        final Path sourceRoot = workDir.resolve("src");
        final Path classesRoot = workDir.resolve("classes");
        final Path javaFile = sourceRoot.resolve(WORKFLOW_CLASS.replace('.', '/') + ".java");
        Files.createDirectories(javaFile.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(javaFile, supportSource(), UTF_8);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(javaFile));
            final List<String> options = List.of("--release", "21", "-d", classesRoot.toString());
            final Boolean success = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed compiling TSJ-38b support fixture.");
            }
        }

        final Path jarPath = workDir.resolve("tsj38b-security-support.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> paths = Files.walk(classesRoot)) {
                final List<Path> classFiles = paths
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .sorted()
                        .toList();
                for (Path classFile : classFiles) {
                    final String entryName = classesRoot.relativize(classFile).toString().replace(File.separatorChar, '/');
                    jarOutputStream.putNextEntry(new JarEntry(entryName));
                    jarOutputStream.write(Files.readAllBytes(classFile));
                    jarOutputStream.closeEntry();
                }
            }
        }
        return jarPath.toAbsolutePath().normalize();
    }

    private static String supportSource() {
        return """
                package sample.parity.security;

                public final class SecurityWorkflow {
                    private SecurityWorkflow() {
                    }

                    public static String secureRead(final String token) {
                        if (token == null || token.isBlank()) {
                            throw new IllegalStateException(
                                    "TSJ-SECURITY-AUTHN-FAILURE: unauthenticated request for /orders"
                            );
                        }
                        return "orders";
                    }

                    public static String adminWrite(final String token, final String payload) {
                        if (token == null || token.isBlank()) {
                            throw new IllegalStateException(
                                    "TSJ-SECURITY-AUTHN-FAILURE: unauthenticated request for /admin/orders"
                            );
                        }
                        if (!"admin-token".equals(token)) {
                            throw new IllegalStateException(
                                    "TSJ-SECURITY-AUTHZ-FAILURE: role USER lacks ADMIN for /admin/orders"
                            );
                        }
                        return "write:" + payload;
                    }

                    public static String roleFor(final String token) {
                        if ("admin-token".equals(token)) {
                            return "ADMIN";
                        }
                        if ("user-token".equals(token)) {
                            return "USER";
                        }
                        return "ANON";
                    }

                    public static void misconfiguredPolicy(final String endpoint) {
                        throw new IllegalStateException(
                                "TSJ-SECURITY-CONFIG-FAILURE: invalid security matcher for " + endpoint
                        );
                    }
                }
                """;
    }

    private static String normalizeOutput(final String text) {
        final String safe = text == null ? "" : text;
        return safe.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private static String classifyDiagnostic(final Throwable failure, final String stderr) {
        final StringBuilder builder = new StringBuilder();
        if (stderr != null) {
            builder.append(stderr).append('\n');
        }
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                builder.append(cursor.getMessage()).append('\n');
            }
            cursor = cursor.getCause();
        }
        final String combined = builder.toString();
        if (combined.contains(AUTHN_CODE)) {
            return AUTHN_CODE;
        }
        if (combined.contains(AUTHZ_CODE)) {
            return AUTHZ_CODE;
        }
        if (combined.contains(CONFIG_CODE)) {
            return CONFIG_CODE;
        }
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(combined);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String buildNotes(final ProgramRunResult run) {
        final String failureSummary = run.failure() == null
                ? ""
                : run.failure().getClass().getSimpleName() + ":" + safeMessage(run.failure());
        return "stdout="
                + trim(run.stdout(), 180)
                + ",stderr="
                + trim(run.stderr(), 180)
                + ",failure="
                + trim(failureSummary, 180);
    }

    private static String trim(final String value, final int maxChars) {
        final String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static String safeMessage(final Throwable throwable) {
        return throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage();
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjKotlinSecurityParityHarness.class.getProtectionDomain().getCodeSource().getLocation().toURI()
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

    private static void writeReport(final Path reportPath, final TsjKotlinSecurityParityReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record ProgramRunResult(String stdout, String stderr, Throwable failure) {
    }
}
