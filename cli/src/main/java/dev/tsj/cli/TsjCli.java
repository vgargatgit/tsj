package dev.tsj.cli;

import dev.tsj.cli.bench.BenchmarkHarness;
import dev.tsj.cli.fixtures.FixtureHarness;
import dev.tsj.cli.fixtures.FixtureSuiteResult;
import dev.tsj.cli.fixtures.FixtureRunResult;
import dev.tsj.compiler.backend.jvm.BackendJvmModule;
import dev.tsj.compiler.backend.jvm.InteropBridgeArtifact;
import dev.tsj.compiler.backend.jvm.InteropBridgeGenerator;
import dev.tsj.compiler.backend.jvm.JvmBytecodeCompiler;
import dev.tsj.compiler.backend.jvm.JvmBytecodeRunner;
import dev.tsj.compiler.backend.jvm.JvmCompilationException;
import dev.tsj.compiler.backend.jvm.JvmCompiledArtifact;
import dev.tsj.compiler.backend.jvm.JvmOptimizationOptions;
import dev.tsj.compiler.backend.jvm.TsjSpringWebControllerArtifact;
import dev.tsj.compiler.backend.jvm.TsjSpringWebControllerGenerator;
import dev.tsj.compiler.backend.jvm.TsjSpringComponentArtifact;
import dev.tsj.compiler.backend.jvm.TsjSpringComponentGenerator;
import dev.tsj.compiler.frontend.FrontendModule;
import dev.tsj.compiler.ir.IrModule;
import dev.tsj.runtime.RuntimeModule;
import dev.tsj.runtime.TsjJavaInterop;
import dev.tsj.runtime.TsjRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * CLI bootstrap for TSJ-2 command skeleton.
 */
public final class TsjCli {
    private static final String COMMAND_COMPILE = "compile";
    private static final String COMMAND_RUN = "run";
    private static final String COMMAND_FIXTURES = "fixtures";
    private static final String COMMAND_INTEROP = "interop";
    private static final String COMMAND_BENCH = "bench";
    private static final String COMMAND_SPRING_PACKAGE = "spring-package";
    private static final String OPTION_OUT = "--out";
    private static final String OPTION_CLASSPATH = "--classpath";
    private static final String OPTION_JAR = "--jar";
    private static final String OPTION_INTEROP_SPEC = "--interop-spec";
    private static final String OPTION_INTEROP_POLICY = "--interop-policy";
    private static final String OPTION_INTEROP_DENYLIST = "--interop-denylist";
    private static final String OPTION_INTEROP_AUDIT_LOG = "--interop-audit-log";
    private static final String OPTION_INTEROP_AUDIT_AGGREGATE = "--interop-audit-aggregate";
    private static final String OPTION_INTEROP_TRACE = "--interop-trace";
    private static final String OPTION_INTEROP_ROLE = "--interop-role";
    private static final String OPTION_INTEROP_APPROVAL = "--interop-approval";
    private static final String OPTION_ACK_INTEROP_RISK = "--ack-interop-risk";
    private static final String OPTION_CLASSLOADER_ISOLATION = "--classloader-isolation";
    private static final String OPTION_RESOURCE_DIR = "--resource-dir";
    private static final String OPTION_BOOT_JAR = "--boot-jar";
    private static final String OPTION_SMOKE_RUN = "--smoke-run";
    private static final String OPTION_SMOKE_ENDPOINT_URL = "--smoke-endpoint-url";
    private static final String OPTION_SMOKE_TIMEOUT_MS = "--smoke-timeout-ms";
    private static final String OPTION_SMOKE_POLL_MS = "--smoke-poll-ms";
    private static final String OPTION_TS_STACKTRACE = "--ts-stacktrace";
    private static final String OPTION_OPTIMIZE = "--optimize";
    private static final String OPTION_NO_OPTIMIZE = "--no-optimize";
    private static final String OPTION_WARMUP = "--warmup";
    private static final String OPTION_ITERATIONS = "--iterations";
    private static final String OPTION_SMOKE = "--smoke";
    private static final String SYSTEM_PROPERTY_GLOBAL_POLICY_PATH = "tsj.interop.globalPolicy";
    private static final String ENV_GLOBAL_POLICY_PATH = "TSJ_INTEROP_GLOBAL_POLICY";
    private static final String PROJECT_POLICY_RELATIVE_PATH = ".tsj/interop-policy.properties";
    private static final String POLICY_KEY_INTEROP_POLICY = "interop.policy";
    private static final String POLICY_KEY_INTEROP_ACK_RISK = "interop.ackRisk";
    private static final String POLICY_KEY_INTEROP_RBAC_ROLES = "interop.rbac.roles";
    private static final String POLICY_KEY_INTEROP_RBAC_REQUIRED_ROLES = "interop.rbac.requiredRoles";
    private static final String POLICY_KEY_INTEROP_RBAC_SENSITIVE_TARGETS = "interop.rbac.sensitiveTargets";
    private static final String POLICY_KEY_INTEROP_RBAC_SENSITIVE_REQUIRED_ROLES =
            "interop.rbac.sensitiveRequiredRoles";
    private static final String POLICY_KEY_INTEROP_APPROVAL_REQUIRED = "interop.approval.required";
    private static final String POLICY_KEY_INTEROP_APPROVAL_TOKEN = "interop.approval.token";
    private static final String POLICY_KEY_INTEROP_APPROVAL_TARGETS = "interop.approval.targets";
    private static final String DEFAULT_RUN_OUT_DIR = ".tsj-build";
    private static final String ARTIFACT_FILE_NAME = "program.tsj.properties";
    private static final String ARTIFACT_INTEROP_CLASSPATH_COUNT = "interopClasspath.count";
    private static final String ARTIFACT_INTEROP_CLASSPATH_ENTRY_PREFIX = "interopClasspath.";
    private static final String ARTIFACT_INTEROP_MEDIATION_COUNT = "interopClasspath.mediation.count";
    private static final String ARTIFACT_INTEROP_MEDIATION_ENTRY_PREFIX = "interopClasspath.mediation.";
    private static final String ARTIFACT_INTEROP_SCOPE_USAGE = "interopClasspath.scope.usage";
    private static final String ARTIFACT_INTEROP_SCOPE_ALLOWED = "interopClasspath.scope.allowed";
    private static final String ARTIFACT_INTEROP_SCOPE_EXCLUDED_COUNT = "interopClasspath.scope.excluded.count";
    private static final String ARTIFACT_INTEROP_SCOPE_EXCLUDED_ENTRY_PREFIX = "interopClasspath.scope.excluded.";
    private static final String ARTIFACT_INTEROP_CLASSLOADER_ISOLATION = "interopClasspath.classloaderIsolation";
    private static final String ARTIFACT_INTEROP_CLASS_INDEX_PATH = "interopClasspath.classIndex.path";
    private static final String ARTIFACT_INTEROP_CLASS_INDEX_SYMBOL_COUNT = "interopClasspath.classIndex.symbolCount";
    private static final String ARTIFACT_INTEROP_CLASS_INDEX_DUPLICATE_COUNT =
            "interopClasspath.classIndex.duplicateCount";
    private static final String ARTIFACT_INTEROP_CLASS_INDEX_MRJAR_WINNER_COUNT =
            "interopClasspath.classIndex.mrJarWinnerCount";
    private static final String ARTIFACT_INTEROP_CLASS_INDEX_MRJAR_BASE_WINNER_COUNT =
            "interopClasspath.classIndex.mrJarBaseWinnerCount";
    private static final String ARTIFACT_INTEROP_CLASS_INDEX_MRJAR_VERSIONED_WINNER_COUNT =
            "interopClasspath.classIndex.mrJarVersionedWinnerCount";
    private static final String ARTIFACT_INTEROP_BRIDGE_SELECTED_COUNT = "interopBridges.selectedTargetCount";
    private static final String ARTIFACT_INTEROP_BRIDGE_SELECTED_PREFIX = "interopBridges.selectedTarget.";
    private static final String ARTIFACT_INTEROP_BRIDGE_UNRESOLVED_COUNT = "interopBridges.unresolvedTargetCount";
    private static final String ARTIFACT_INTEROP_BRIDGE_UNRESOLVED_PREFIX = "interopBridges.unresolvedTarget.";
    private static final int MAX_AGGREGATE_AUDIT_EVENTS = 256;
    private static final long DEFAULT_SMOKE_ENDPOINT_TIMEOUT_MS = 5_000L;
    private static final long DEFAULT_SMOKE_ENDPOINT_POLL_MS = 150L;
    private static final String AUTO_INTEROP_SPEC_FILE = ".tsj-auto-interop.properties";
    private static final String AUTO_INTEROP_CACHE_FILE = ".tsj-auto-interop-cache.properties";
    private static final String AUTO_INTEROP_CACHE_FINGERPRINT_KEY = "fingerprint";
    private static final String AUTO_INTEROP_OUTPUT_DIR = "generated-interop";
    private static final String SPRING_PACKAGE_DEFAULT_JAR_NAME = "tsj-spring-app.jar";
    private static final Pattern IMPORT_STATEMENT_PATTERN = Pattern.compile(
            "(?s)\\bimport\\s+(?:(type\\s+)?(.+?)\\s+from\\s*[\"']([^\"']+)[\"']"
                    + "(?:\\s+(?:with|assert)\\s*\\{[^;]*})?|[\"']([^\"']+)[\"']"
                    + "(?:\\s+(?:with|assert)\\s*\\{[^;]*})?)\\s*;"
    );
    private static final Pattern NAMED_BINDINGS_PATTERN = Pattern.compile("(?s)\\{([^}]*)}");
    private static final Pattern INTEROP_MODULE_PATTERN = Pattern.compile(
            "^java:([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)$"
    );
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");
    private static final Pattern JAR_VERSION_PATTERN = Pattern.compile("^(.+)-([0-9][A-Za-z0-9._+-]*)\\.jar$");

    private TsjCli() {
    }

    public static String moduleFingerprint() {
        return String.join(
                "|",
                List.of(
                        FrontendModule.moduleName(),
                        IrModule.moduleName(),
                        BackendJvmModule.moduleName(),
                        RuntimeModule.moduleName()
                )
        );
    }

    public static int execute(final String[] args, final PrintStream stdout, final PrintStream stderr) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");

        try {
            if (args.length == 0) {
                throw CliFailure.usage(
                        "TSJ-CLI-001",
                        "Missing command. Expected `compile`, `run`, `spring-package`, `fixtures`, `interop`, or `bench`."
                );
            }

            return switch (args[0]) {
                case COMMAND_COMPILE -> handleCompile(args, stdout);
                case COMMAND_RUN -> handleRun(args, stdout, stderr);
                case COMMAND_SPRING_PACKAGE -> handleSpringPackage(args, stdout);
                case COMMAND_FIXTURES -> handleFixtures(args, stdout);
                case COMMAND_INTEROP -> handleInterop(args, stdout);
                case COMMAND_BENCH -> handleBench(args, stdout);
                default -> throw CliFailure.usage(
                        "TSJ-CLI-002",
                        "Unknown command `" + args[0]
                                + "`. Expected `compile`, `run`, `spring-package`, `fixtures`, `interop`, or `bench`."
                );
            };
        } catch (final CliFailure failure) {
            emitDiagnostic(
                    stderr,
                    "ERROR",
                    failure.code,
                    failure.getMessage(),
                    failure.context
            );
            return failure.exitCode;
        } catch (final Exception ex) {
            emitDiagnostic(
                    stderr,
                    "ERROR",
                    "TSJ-CLI-500",
                    "Unhandled CLI error: " + ex.getMessage(),
                    Map.of()
            );
            return 1;
        }
    }

    public static void main(final String[] args) {
        final int exitCode = execute(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int handleCompile(final String[] args, final PrintStream stdout) {
        if (args.length < 2) {
            throw CliFailure.usage(
                    "TSJ-CLI-003",
                    "Missing input file. Usage: tsj compile <input.ts> --out <dir> "
                            + "[--classpath <entries>] [--jar <jar-file>] [--interop-spec <interop.properties>] "
                            + "[--interop-policy strict|broad] [--ack-interop-risk] "
                            + "[--interop-role <roles>] [--interop-approval <token>] "
                            + "[--interop-denylist <patterns>] [--interop-audit-log <path>] "
                            + "[--interop-audit-aggregate <path>] "
                            + "[--interop-trace]"
            );
        }
        final Path entryPath = Path.of(args[1]);
        final CompileOptions options = parseCompileOptions(entryPath, args, 2, true);
        final CompiledArtifact artifact = compileArtifact(
                entryPath,
                options.outDir(),
                options.optimizationOptions(),
                options.interopClasspathEntries(),
                options.classpathMediationDecisions(),
                options.classpathUsageMode(),
                options.classpathScopeExclusions(),
                options.interopSpecPath(),
                options.interopPolicy(),
                options.interopRiskAcknowledged(),
                options.interopAuthorizationResolution(),
                options.interopDenylistPatterns(),
                options.interopAuditLogPath(),
                options.interopAuditAggregatePath(),
                options.interopTraceEnabled(),
                JvmBytecodeRunner.ClassloaderIsolationMode.SHARED,
                COMMAND_COMPILE
        );

        final Map<String, String> context = new LinkedHashMap<>();
        context.put("entry", artifact.entryPath.toString());
        context.put("artifact", artifact.artifactPath.toString());
        context.put("className", artifact.jvmArtifact.className());
        context.put("classFile", artifact.jvmArtifact.classFile().toString());
        context.put("outDir", options.outDir().toString());
        context.put("interopClasspathEntries", Integer.toString(artifact.classpathResolution().entries().size()));
        context.put(
                "interopMediationDecisions",
                Integer.toString(artifact.classpathResolution().decisions().size())
        );
        context.put("interopClasspathScope", artifact.classpathResolution().usageMode().cliValue());
        context.put(
                "interopClasspathScopeExclusions",
                Integer.toString(artifact.classpathResolution().scopeExclusions().size())
        );
        context.put(
                "interopClassIndexPath",
                artifact.classpathSymbolIndex().indexPath().toAbsolutePath().normalize().toString()
        );
        context.put(
                "interopClassIndexSymbols",
                Integer.toString(artifact.classpathSymbolIndex().symbolCount())
        );
        context.put(
                "interopClassIndexDuplicates",
                Integer.toString(artifact.classpathSymbolIndex().duplicateCount())
        );
        context.put(
                "interopClassIndexMrJarWinners",
                Integer.toString(artifact.classpathSymbolIndex().mrJarWinnerCount())
        );
        context.put(
                "interopClassIndexMrJarBaseWinners",
                Integer.toString(artifact.classpathSymbolIndex().mrJarBaseWinnerCount())
        );
        context.put(
                "interopClassIndexMrJarVersionedWinners",
                Integer.toString(artifact.classpathSymbolIndex().mrJarVersionedWinnerCount())
        );
        context.put("interopPolicy", options.interopPolicy().cliValue());
        context.put("interopPolicySource", options.interopPolicySource());
        context.put(
                "interopRbacRoles",
                String.join(",", options.interopAuthorizationResolution().actorRoles())
        );
        context.put(
                "interopRbacRequiredRoles",
                String.join(",", options.interopAuthorizationResolution().requiredRoles())
        );
        context.put(
                "interopApprovalRequired",
                Boolean.toString(options.interopAuthorizationResolution().approvalRequired())
        );
        context.put("interopTrace", Boolean.toString(options.interopTraceEnabled()));
        context.put(
                "interopAuditAggregate",
                options.interopAuditAggregatePath() == null ? "" : options.interopAuditAggregatePath().toString()
        );
        context.put("interopDenylistCount", Integer.toString(options.interopDenylistPatterns().size()));
        context.put("interopBridgeTargets", Integer.toString(artifact.interopBridgeResult().targets().size()));
        context.put("interopBridgeSourceCount", Integer.toString(artifact.interopBridgeResult().sourceCount()));
        context.put("interopBridgeRegenerated", Boolean.toString(artifact.interopBridgeResult().regenerated()));
        context.put(
                "tsjWebControllerCount",
                Integer.toString(artifact.tsjWebControllerResult().controllerClassNames().size())
        );
        context.put(
                "tsjWebControllerSourceCount",
                Integer.toString(artifact.tsjWebControllerResult().sourceCount())
        );
        context.put(
                "tsjSpringComponentCount",
                Integer.toString(artifact.tsjSpringComponentResult().componentClassNames().size())
        );
        context.put(
                "tsjSpringComponentSourceCount",
                Integer.toString(artifact.tsjSpringComponentResult().sourceCount())
        );
        context.put("optConstantFolding", Boolean.toString(options.optimizationOptions().constantFoldingEnabled()));
        context.put(
                "optDeadCodeElimination",
                Boolean.toString(options.optimizationOptions().deadCodeEliminationEnabled())
        );
        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-COMPILE-SUCCESS",
                "Compilation artifact generated.",
                Map.copyOf(context)
        );
        return 0;
    }

    private static int handleRun(final String[] args, final PrintStream stdout, final PrintStream stderr) {
        if (args.length < 2) {
            throw CliFailure.usage(
                    "TSJ-CLI-004",
                    "Missing entry file. Usage: tsj run <entry.ts> [--out <dir>] "
                            + "[--classpath <entries>] [--jar <jar-file>] "
                            + "[--interop-spec <interop.properties>] [--interop-policy strict|broad] "
                            + "[--ack-interop-risk] [--interop-denylist <patterns>] "
                            + "[--interop-role <roles>] [--interop-approval <token>] "
                            + "[--interop-audit-log <path>] [--interop-audit-aggregate <path>] "
                            + "[--interop-trace] [--classloader-isolation shared|app-isolated] "
                            + "[--ts-stacktrace]"
            );
        }
        final Path entryPath = Path.of(args[1]);
        final RunOptions runOptions = parseRunOptions(entryPath, args, 2);
        final Path outDir = runOptions.outDir() != null ? runOptions.outDir() : Path.of(DEFAULT_RUN_OUT_DIR);
        final CompiledArtifact artifact;
        try {
            artifact = compileArtifact(
                    entryPath,
                    outDir,
                    runOptions.optimizationOptions(),
                    runOptions.interopClasspathEntries(),
                    runOptions.classpathMediationDecisions(),
                    runOptions.classpathUsageMode(),
                    runOptions.classpathScopeExclusions(),
                    runOptions.interopSpecPath(),
                    runOptions.interopPolicy(),
                    runOptions.interopRiskAcknowledged(),
                    runOptions.interopAuthorizationResolution(),
                    runOptions.interopDenylistPatterns(),
                    runOptions.interopAuditLogPath(),
                    runOptions.interopAuditAggregatePath(),
                    runOptions.interopTraceEnabled(),
                    runOptions.classloaderIsolationMode(),
                    COMMAND_RUN
            );
        } catch (final CliFailure failure) {
            final CliFailure remappedFailure = maybeRunInteropClasspathFailure(failure, entryPath);
            if (remappedFailure != null) {
                throw remappedFailure;
            }
            throw failure;
        }
        executeArtifact(
                artifact,
                stdout,
                stderr,
                runOptions.showTsStackTrace(),
                runOptions.interopTraceEnabled(),
                runOptions.classloaderIsolationMode()
        );
        return 0;
    }

    private static int handleSpringPackage(final String[] args, final PrintStream stdout) {
        if (args.length < 2) {
            throw CliFailure.usage(
                    "TSJ-CLI-014",
                    "Missing entry file. Usage: tsj spring-package <entry.ts> --out <dir> "
                            + "[--classpath <entries>] [--jar <jar-file>] "
                            + "[--interop-spec <interop.properties>] [--interop-policy strict|broad] "
                            + "[--ack-interop-risk] [--interop-denylist <patterns>] "
                            + "[--interop-role <roles>] [--interop-approval <token>] "
                            + "[--interop-audit-log <path>] [--interop-audit-aggregate <path>] "
                            + "[--interop-trace] "
                            + "[--resource-dir <dir>] [--boot-jar <jar-file>] [--smoke-run] "
                            + "[--smoke-endpoint-url <http-url>] [--smoke-timeout-ms <ms>] "
                            + "[--smoke-poll-ms <ms>] "
                            + "[--optimize|--no-optimize]"
            );
        }
        final Path entryPath = Path.of(args[1]);
        final SpringPackageOptions options = parseSpringPackageOptions(entryPath, args, 2);

        final CompiledArtifact artifact;
        try {
            artifact = compileArtifact(
                    entryPath,
                    options.outDir(),
                    options.optimizationOptions(),
                    options.interopClasspathEntries(),
                    options.classpathMediationDecisions(),
                    options.classpathUsageMode(),
                    options.classpathScopeExclusions(),
                    options.interopSpecPath(),
                    options.interopPolicy(),
                    options.interopRiskAcknowledged(),
                    options.interopAuthorizationResolution(),
                    options.interopDenylistPatterns(),
                    options.interopAuditLogPath(),
                    options.interopAuditAggregatePath(),
                    options.interopTraceEnabled(),
                    JvmBytecodeRunner.ClassloaderIsolationMode.SHARED,
                    COMMAND_SPRING_PACKAGE
            );
        } catch (final CliFailure failure) {
            throw CliFailure.runtime(
                    failure.code,
                    failure.getMessage(),
                    mergeFailureContext(failure.context, "stage", classifyStartupStage(failure.code))
            );
        }
        compileGeneratedSpringAdaptersForPackaging(artifact, options);

        final SpringPackageResult packageResult = packageSpringJar(artifact, options);
        final Map<String, String> packageContext = new LinkedHashMap<>();
        packageContext.put("entry", artifact.entryPath().toAbsolutePath().normalize().toString());
        packageContext.put("outDir", options.outDir().toAbsolutePath().normalize().toString());
        packageContext.put("jar", packageResult.jarPath().toString());
        packageContext.put("resourceFiles", Integer.toString(packageResult.resourceFileCount()));
        packageContext.put("resourceDirs", Integer.toString(packageResult.resourceDirectories().size()));
        packageContext.put("fatJarDependencyEntries", Integer.toString(packageResult.dependencyEntryCount()));
        packageContext.put("fatJarDependencySources", Integer.toString(packageResult.dependencySources().size()));
        packageContext.put("interopPolicy", options.interopPolicy().cliValue());
        packageContext.put("interopPolicySource", options.interopPolicySource());
        packageContext.put(
                "interopAuditAggregate",
                options.interopAuditAggregatePath() == null ? "" : options.interopAuditAggregatePath().toString()
        );
        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-SPRING-PACKAGE-SUCCESS",
                "Spring package artifact generated.",
                Map.copyOf(packageContext)
        );
        if (options.smokeRun()) {
            final SpringSmokeResult smokeResult = smokeRunSpringPackage(artifact, packageResult.jarPath(), options);
            if (smokeResult.endpointUrl() != null && !smokeResult.endpointUrl().isBlank()) {
                final Map<String, String> endpointContext = new LinkedHashMap<>();
                endpointContext.put("jar", packageResult.jarPath().toString());
                endpointContext.put("endpointUrl", smokeResult.endpointUrl());
                endpointContext.put("endpointPort", smokeResult.endpointPort());
                endpointContext.put("endpointStatus", Integer.toString(smokeResult.endpointStatusCode()));
                endpointContext.put("responsePreview", smokeResult.endpointResponsePreview());
                endpointContext.put("runtimeMs", Long.toString(smokeResult.runtimeMs()));
                endpointContext.put("reproCommand", smokeResult.reproCommand());
                emitDiagnostic(
                        stdout,
                        "INFO",
                        "TSJ-SPRING-SMOKE-ENDPOINT-SUCCESS",
                        "Spring package endpoint smoke check completed.",
                        Map.copyOf(endpointContext)
                );
            }
            final Map<String, String> smokeContext = new LinkedHashMap<>();
            smokeContext.put("jar", packageResult.jarPath().toString());
            smokeContext.put("mainClass", artifact.jvmArtifact().className());
            smokeContext.put("exitCode", Integer.toString(smokeResult.exitCode()));
            smokeContext.put("outputPreview", smokeResult.outputPreview());
            smokeContext.put("runtimeMs", Long.toString(smokeResult.runtimeMs()));
            smokeContext.put("reproCommand", smokeResult.reproCommand());
            if (smokeResult.endpointUrl() != null && !smokeResult.endpointUrl().isBlank()) {
                smokeContext.put("endpointUrl", smokeResult.endpointUrl());
                smokeContext.put("endpointPort", smokeResult.endpointPort());
            }
            emitDiagnostic(
                    stdout,
                    "INFO",
                    "TSJ-SPRING-SMOKE-SUCCESS",
                    "Spring package smoke run completed.",
                    Map.copyOf(smokeContext)
            );
        }
        return 0;
    }

    private static int handleFixtures(final String[] args, final PrintStream stdout) {
        if (args.length < 2) {
            throw CliFailure.usage(
                    "TSJ-CLI-007",
                    "Missing fixture root. Usage: tsj fixtures <fixturesRoot>"
            );
        }
        final Path fixturesRoot = Path.of(args[1]);
        final FixtureSuiteResult suiteResult;
        try {
            suiteResult = new FixtureHarness().runSuite(fixturesRoot);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw CliFailure.runtime(
                    "TSJ-FIXTURE-001",
                    "Failed to load fixtures: " + illegalArgumentException.getMessage(),
                    Map.of("fixturesRoot", fixturesRoot.toString())
            );
        }
        final List<FixtureRunResult> results = suiteResult.results();

        if (results.isEmpty()) {
            throw CliFailure.runtime(
                    "TSJ-FIXTURE-002",
                    "No fixture directories found.",
                    Map.of("fixturesRoot", fixturesRoot.toString())
            );
        }

        int passed = 0;
        int failed = 0;
        for (FixtureRunResult result : results) {
            if (result.passed()) {
                passed++;
                emitDiagnostic(
                        stdout,
                        "INFO",
                        "TSJ-FIXTURE-PASS",
                        "Fixture passed.",
                        Map.of("fixture", result.fixtureName())
                );
            } else {
                failed++;
                emitDiagnostic(
                        stdout,
                        "ERROR",
                        "TSJ-FIXTURE-FAIL",
                        "Fixture failed.",
                        Map.of(
                                "fixture", result.fixtureName(),
                                "nodeDiff", result.nodeResult().diff(),
                                "tsjDiff", result.tsjResult().diff(),
                                "nodeToTsjDiff", result.nodeToTsjDiff(),
                                "minimalRepro", result.minimizedRepro()
                        )
                );
            }
        }

        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-FIXTURE-SUMMARY",
                "Fixture run complete.",
                Map.of(
                        "total", Integer.toString(results.size()),
                        "passed", Integer.toString(passed),
                        "failed", Integer.toString(failed)
                )
        );
        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-FIXTURE-COVERAGE",
                "Fixture coverage report generated.",
                Map.of(
                        "coverageReport", suiteResult.coverageReportPath().toString(),
                        "totalFixtures", Integer.toString(suiteResult.coverageReport().totalFixtures()),
                        "passedFixtures", Integer.toString(suiteResult.coverageReport().passedFixtures()),
                        "failedFixtures", Integer.toString(suiteResult.coverageReport().failedFixtures()),
                        "featureBuckets", Integer.toString(suiteResult.coverageReport().byFeature().size())
                )
        );
        return failed == 0 ? 0 : 1;
    }

    private static int handleInterop(final String[] args, final PrintStream stdout) {
        if (args.length < 2) {
            throw CliFailure.usage(
                    "TSJ-CLI-008",
                    "Missing interop spec file. Usage: tsj interop <interop.properties> --out <dir>"
            );
        }
        final Path specPath = Path.of(args[1]);
        final ParsedOutOption parsedOut = parseOutOption(args, 2, true);

        final InteropBridgeArtifact artifact;
        try {
            artifact = new InteropBridgeGenerator().generate(specPath, parsedOut.outDir);
        } catch (final JvmCompilationException compilationException) {
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(specPath, compilationException)
            );
        }

        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-INTEROP-SUCCESS",
                "Interop bridge generation complete.",
                Map.of(
                        "spec", specPath.toString(),
                        "outDir", parsedOut.outDir.toString(),
                        "generatedCount", Integer.toString(artifact.sourceFiles().size())
                )
        );
        return 0;
    }

    private static int handleBench(final String[] args, final PrintStream stdout) {
        if (args.length < 2) {
            throw CliFailure.usage(
                    "TSJ-CLI-009",
                    "Missing benchmark report path. Usage: tsj bench <report.json> [--warmup <n>] "
                            + "[--iterations <n>] [--smoke] [--optimize|--no-optimize]"
            );
        }
        final Path reportPath = Path.of(args[1]);
        final BenchmarkHarness.BenchmarkOptions options = parseBenchmarkOptions(args, 2);

        final BenchmarkHarness.BenchmarkReport report;
        try {
            report = new BenchmarkHarness().run(reportPath, options);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw CliFailure.usage(
                    "TSJ-CLI-010",
                    "Invalid benchmark options: " + illegalArgumentException.getMessage()
            );
        } catch (final RuntimeException runtimeException) {
            throw CliFailure.runtime(
                    "TSJ-BENCH-001",
                    "Benchmark harness failed: " + runtimeException.getMessage(),
                    Map.of("report", reportPath.toString())
            );
        }

        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-BENCH-SUCCESS",
                "Benchmark report generated.",
                Map.of(
                        "report", reportPath.toAbsolutePath().normalize().toString(),
                        "workloads", Integer.toString(report.summary().totalWorkloads()),
                        "microWorkloads", Integer.toString(report.summary().microWorkloads()),
                        "macroWorkloads", Integer.toString(report.summary().macroWorkloads()),
                        "avgCompileMs", formatDouble(report.summary().avgCompileMs()),
                        "avgRunMs", formatDouble(report.summary().avgRunMs()),
                        "avgThroughputOpsPerSec", formatDouble(report.summary().avgThroughputOpsPerSec()),
                        "maxPeakMemoryBytes", Long.toString(report.summary().maxPeakMemoryBytes())
                )
        );
        return 0;
    }

    private static CompileOptions parseCompileOptions(
            final Path entryPath,
            final String[] args,
            final int startIndex,
            final boolean requiredOut
    ) {
        Path outDir = null;
        Path interopSpecPath = null;
        InteropPolicy interopPolicy = InteropPolicy.STRICT;
        boolean interopPolicyExplicit = false;
        boolean interopRiskAcknowledged = false;
        boolean interopRiskAcknowledgedExplicit = false;
        boolean interopRolesExplicit = false;
        boolean interopApprovalExplicit = false;
        boolean interopTraceEnabled = false;
        Path interopAuditLogPath = null;
        Path interopAuditAggregatePath = null;
        String interopApprovalToken = null;
        JvmOptimizationOptions optimizationOptions = JvmOptimizationOptions.defaults();
        final List<String> interopDenylistPatterns = new ArrayList<>();
        final List<String> interopRoles = new ArrayList<>();
        final List<ClasspathInput> classpathInputs = new ArrayList<>();
        int index = startIndex;
        while (index < args.length) {
            final String token = args[index];
            if (OPTION_OUT.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--out`."
                    );
                }
                outDir = Path.of(args[index + 1]);
                index += 2;
                continue;
            }
            if (OPTION_CLASSPATH.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--classpath`."
                    );
                }
                classpathInputs.addAll(parseClasspathOptionEntries(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_JAR.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--jar`."
                    );
                }
                classpathInputs.add(new ClasspathInput(Path.of(args[index + 1]), true));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_SPEC.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-spec`."
                    );
                }
                interopSpecPath = normalizeInteropSpecPath(Path.of(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_POLICY.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-policy`."
                    );
                }
                interopPolicy = parseInteropPolicyValue(args[index + 1]);
                interopPolicyExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_ACK_INTEROP_RISK.equals(token)) {
                interopRiskAcknowledged = true;
                interopRiskAcknowledgedExplicit = true;
                index++;
                continue;
            }
            if (OPTION_INTEROP_ROLE.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-role`."
                    );
                }
                interopRoles.addAll(parseInteropRoleValues(args[index + 1]));
                interopRolesExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_APPROVAL.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-approval`."
                    );
                }
                interopApprovalToken = parseInteropApprovalToken(args[index + 1]);
                interopApprovalExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_DENYLIST.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-denylist`."
                    );
                }
                interopDenylistPatterns.addAll(parseInteropDenylistPatterns(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_AUDIT_LOG.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-audit-log`."
                    );
                }
                interopAuditLogPath = Path.of(args[index + 1]).toAbsolutePath().normalize();
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_AUDIT_AGGREGATE.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-audit-aggregate`."
                    );
                }
                interopAuditAggregatePath = Path.of(args[index + 1]).toAbsolutePath().normalize();
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_TRACE.equals(token)) {
                interopTraceEnabled = true;
                index++;
                continue;
            }
            final JvmOptimizationOptions toggled = parseOptimizationToggle(token);
            if (toggled != null) {
                optimizationOptions = toggled;
                index++;
                continue;
            }
            throw CliFailure.usage(
                    "TSJ-CLI-005",
                    "Unknown option `" + token + "`."
            );
        }

        if (requiredOut && outDir == null) {
            throw CliFailure.usage(
                    "TSJ-CLI-003",
                    "Missing required option `--out`."
            );
        }
        if (interopAuditAggregatePath != null && interopAuditLogPath == null) {
            throw CliFailure.usage(
                    "TSJ-CLI-011",
                    "Centralized interop audit aggregation requires local fallback log. "
                            + "Add `--interop-audit-log <path>` with `--interop-audit-aggregate`."
            );
        }
        final InteropPolicyResolution policyResolution = resolveInteropPolicyResolution(
                entryPath,
                interopPolicy,
                interopPolicyExplicit,
                interopRiskAcknowledged,
                interopRiskAcknowledgedExplicit
        );
        final InteropAuthorizationResolution authorizationResolution = resolveInteropAuthorizationResolution(
                entryPath,
                interopRoles,
                interopRolesExplicit,
                interopApprovalToken,
                interopApprovalExplicit
        );
        final ClasspathResolution classpathResolution = normalizeClasspathInputs(
                classpathInputs,
                ClasspathUsageMode.COMPILE
        );
        return new CompileOptions(
                outDir,
                optimizationOptions,
                classpathResolution.entries(),
                classpathResolution.decisions(),
                classpathResolution.usageMode(),
                classpathResolution.scopeExclusions(),
                interopSpecPath,
                policyResolution.interopPolicy(),
                policyResolution.interopRiskAcknowledged(),
                policyResolution.source(),
                authorizationResolution,
                List.copyOf(interopDenylistPatterns),
                interopAuditLogPath,
                interopAuditAggregatePath,
                interopTraceEnabled
        );
    }

    private static ParsedOutOption parseOutOption(
            final String[] args,
            final int startIndex,
            final boolean required
    ) {
        Path outDir = null;
        int index = startIndex;
        while (index < args.length) {
            final String token = args[index];
            if (!OPTION_OUT.equals(token)) {
                throw CliFailure.usage(
                        "TSJ-CLI-005",
                        "Unknown option `" + token + "`."
                );
            }
            if (index + 1 >= args.length) {
                throw CliFailure.usage(
                        "TSJ-CLI-006",
                        "Missing value for `--out`."
                );
            }
            outDir = Path.of(args[index + 1]);
            index += 2;
        }

        if (required && outDir == null) {
            throw CliFailure.usage(
                    "TSJ-CLI-003",
                    "Missing required option `--out`."
            );
        }
        return new ParsedOutOption(outDir);
    }

    private static BenchmarkHarness.BenchmarkOptions parseBenchmarkOptions(
            final String[] args,
            final int startIndex
    ) {
        int warmupIterations = 1;
        int measuredIterations = 2;
        BenchmarkHarness.BenchmarkProfile profile = BenchmarkHarness.BenchmarkProfile.FULL;
        JvmOptimizationOptions optimizationOptions = JvmOptimizationOptions.defaults();
        int index = startIndex;
        while (index < args.length) {
            final String token = args[index];
            if (OPTION_WARMUP.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-010", "Missing value for `--warmup`.");
                }
                warmupIterations = parseNonNegativeInteger(args[index + 1], OPTION_WARMUP);
                index += 2;
                continue;
            }
            if (OPTION_ITERATIONS.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-010", "Missing value for `--iterations`.");
                }
                measuredIterations = parsePositiveInteger(args[index + 1], OPTION_ITERATIONS);
                index += 2;
                continue;
            }
            if (OPTION_SMOKE.equals(token)) {
                profile = BenchmarkHarness.BenchmarkProfile.SMOKE;
                index++;
                continue;
            }
            final JvmOptimizationOptions toggled = parseOptimizationToggle(token);
            if (toggled != null) {
                optimizationOptions = toggled;
                index++;
                continue;
            }
            throw CliFailure.usage("TSJ-CLI-010", "Unknown benchmark option `" + token + "`.");
        }
        return new BenchmarkHarness.BenchmarkOptions(
                warmupIterations,
                measuredIterations,
                profile,
                optimizationOptions
        );
    }

    private static int parsePositiveInteger(final String rawValue, final String optionName) {
        final int value;
        try {
            value = Integer.parseInt(rawValue);
        } catch (final NumberFormatException numberFormatException) {
            throw CliFailure.usage(
                    "TSJ-CLI-010",
                    "Invalid value for `" + optionName + "`: `" + rawValue + "`."
            );
        }
        if (value <= 0) {
            throw CliFailure.usage(
                    "TSJ-CLI-010",
                    "Value for `" + optionName + "` must be greater than zero."
            );
        }
        return value;
    }

    private static int parseNonNegativeInteger(final String rawValue, final String optionName) {
        final int value;
        try {
            value = Integer.parseInt(rawValue);
        } catch (final NumberFormatException numberFormatException) {
            throw CliFailure.usage(
                    "TSJ-CLI-010",
                    "Invalid value for `" + optionName + "`: `" + rawValue + "`."
            );
        }
        if (value < 0) {
            throw CliFailure.usage(
                    "TSJ-CLI-010",
                    "Value for `" + optionName + "` must be zero or greater."
            );
        }
        return value;
    }

    private static CompiledArtifact compileArtifact(
            final Path entryPath,
            final Path outDir,
            final JvmOptimizationOptions optimizationOptions,
            final List<Path> interopClasspathEntries,
            final List<ClasspathMediationDecision> classpathMediationDecisions,
            final ClasspathUsageMode classpathUsageMode,
            final List<ClasspathScopeExclusion> classpathScopeExclusions,
            final Path interopSpecPath,
            final InteropPolicy interopPolicy,
            final boolean interopRiskAcknowledged,
            final InteropAuthorizationResolution interopAuthorizationResolution,
            final List<String> interopDenylistPatterns,
            final Path interopAuditLogPath,
            final Path interopAuditAggregatePath,
            final boolean interopTraceEnabled,
            final JvmBytecodeRunner.ClassloaderIsolationMode classloaderIsolationMode,
            final String commandName
    ) {
        if (!Files.exists(entryPath) || !Files.isRegularFile(entryPath)) {
            throw CliFailure.runtime(
                    "TSJ-COMPILE-001",
                    "Input TypeScript file not found: " + entryPath,
                    Map.of("entry", entryPath.toString())
            );
        }
        final String fileName = entryPath.getFileName().toString();
        if (!fileName.endsWith(".ts") && !fileName.endsWith(".tsx")) {
            throw CliFailure.runtime(
                    "TSJ-COMPILE-002",
                "Unsupported input extension. Expected .ts or .tsx",
                Map.of("entry", entryPath.toString())
            );
        }

        final List<String> discoveredInteropTargets = discoverInteropTargets(entryPath);
        try {
            enforceInteropRiskAcknowledgement(
                    entryPath,
                    interopPolicy,
                    interopRiskAcknowledged,
                    discoveredInteropTargets
            );
            enforceInteropAuthorization(
                    entryPath,
                    interopPolicy,
                    discoveredInteropTargets,
                    interopAuthorizationResolution
            );
            enforceInteropDenylist(entryPath, discoveredInteropTargets, interopDenylistPatterns);
            enforceInteropPolicy(entryPath, interopPolicy, interopSpecPath, discoveredInteropTargets);
            appendInteropAuditLog(
                    interopAuditLogPath,
                    interopAuditAggregatePath,
                    commandName,
                    entryPath,
                    interopPolicy,
                    interopTraceEnabled,
                    interopDenylistPatterns,
                    discoveredInteropTargets,
                    "allow",
                    "",
                    ""
            );
        } catch (final CliFailure failure) {
            appendInteropAuditLog(
                    interopAuditLogPath,
                    interopAuditAggregatePath,
                    commandName,
                    entryPath,
                    interopPolicy,
                    interopTraceEnabled,
                    interopDenylistPatterns,
                    discoveredInteropTargets,
                    "deny",
                    failure.code,
                    failure.getMessage()
            );
            throw failure;
        }
        enforceClasspathScopeCompatibility(
                discoveredInteropTargets,
                interopClasspathEntries,
                classpathUsageMode,
                classpathScopeExclusions
        );

        final JvmCompiledArtifact jvmArtifact;
        try {
            jvmArtifact = new JvmBytecodeCompiler().compile(entryPath, outDir, optimizationOptions);
        } catch (final JvmCompilationException compilationException) {
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(entryPath, compilationException)
            );
        }
        final AutoInteropBridgeResult interopBridgeResult;
        try {
            interopBridgeResult = maybeGenerateAutoInteropBridges(
                    entryPath,
                    outDir,
                    jvmArtifact.outputDirectory(),
                    interopClasspathEntries,
                    interopSpecPath,
                    interopPolicy,
                    discoveredInteropTargets
            );
        } catch (final JvmCompilationException compilationException) {
            final CliFailure scopeFailure = maybeScopeFailure(
                    compilationException,
                    classpathUsageMode,
                    classpathScopeExclusions
            );
            if (scopeFailure != null) {
                throw scopeFailure;
            }
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(entryPath, compilationException)
            );
        }
        final TsjWebControllerResult tsjWebControllerResult;
        try {
            tsjWebControllerResult = maybeGenerateTsjWebControllerAdapters(
                    entryPath,
                    outDir,
                    jvmArtifact.className()
            );
        } catch (final JvmCompilationException compilationException) {
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(entryPath, compilationException)
            );
        }
        final TsjSpringComponentResult tsjSpringComponentResult;
        try {
            tsjSpringComponentResult = maybeGenerateTsjSpringComponentAdapters(
                    entryPath,
                    outDir,
                    jvmArtifact.className()
            );
        } catch (final JvmCompilationException compilationException) {
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(entryPath, compilationException)
            );
        }

        final ClasspathSymbolIndexer.ClasspathSymbolIndex classpathSymbolIndex;
        try {
            classpathSymbolIndex = ClasspathSymbolIndexer.build(
                    jvmArtifact.outputDirectory(),
                    interopClasspathEntries,
                    classloaderIsolationMode
            );
        } catch (final ClasspathSymbolIndexer.AppIsolationConflictException conflictException) {
            final Map<String, String> context = new LinkedHashMap<>();
            context.put("entry", entryPath.toAbsolutePath().normalize().toString());
            context.put("classloaderIsolation", classloaderIsolationMode.cliValue());
            context.put("className", conflictException.internalName().replace('/', '.'));
            context.put("conflictClass", conflictException.internalName().replace('/', '.'));
            context.put(
                    "appOrigin",
                    conflictException.appOrigin().location() + "!" + conflictException.appOrigin().entry()
            );
            context.put(
                    "dependencyOrigin",
                    conflictException.dependencyOrigin().location()
                            + "!"
                            + conflictException.dependencyOrigin().entry()
            );
            context.put("rule", conflictException.rule());
            context.put(
                    "guidance",
                    "Remove conflicting duplicate classes from the dependency classpath, "
                            + "or run with `--classloader-isolation shared` when shadowing is intentional."
            );
            throw CliFailure.runtime(
                    "TSJ-RUN-009",
                    conflictException.getMessage(),
                    Map.copyOf(context)
            );
        }

        try {
            Files.createDirectories(outDir);
            final Path classIndexPath = ClasspathSymbolIndexer.writeIndexFile(
                    outDir,
                    classpathSymbolIndex,
                    classloaderIsolationMode
            );
            final Path artifactPath = outDir.resolve(ARTIFACT_FILE_NAME);
            final Properties properties = new Properties();
            properties.setProperty("formatVersion", "0.1");
            properties.setProperty("entry", entryPath.toAbsolutePath().normalize().toString());
            properties.setProperty("compiledAt", Instant.now().toString());
            properties.setProperty("mainClass", jvmArtifact.className());
            properties.setProperty("classFile", jvmArtifact.classFile().toString());
            properties.setProperty("sourceMapFile", jvmArtifact.sourceMapFile().toString());
            properties.setProperty("classesDir", jvmArtifact.outputDirectory().toString());
            properties.setProperty(ARTIFACT_INTEROP_CLASSPATH_COUNT, Integer.toString(interopClasspathEntries.size()));
            for (int index = 0; index < interopClasspathEntries.size(); index++) {
                properties.setProperty(
                        ARTIFACT_INTEROP_CLASSPATH_ENTRY_PREFIX + index,
                        serializeClasspathEntry(interopClasspathEntries.get(index))
                );
            }
            properties.setProperty(
                    ARTIFACT_INTEROP_MEDIATION_COUNT,
                    Integer.toString(classpathMediationDecisions.size())
            );
            for (int index = 0; index < classpathMediationDecisions.size(); index++) {
                final ClasspathMediationDecision decision = classpathMediationDecisions.get(index);
                final String keyPrefix = ARTIFACT_INTEROP_MEDIATION_ENTRY_PREFIX + index + ".";
                properties.setProperty(keyPrefix + "artifact", decision.artifact());
                properties.setProperty(keyPrefix + "selectedVersion", decision.selectedVersion());
                properties.setProperty(keyPrefix + "selectedPath", decision.selectedPath().toString());
                properties.setProperty(keyPrefix + "rejectedVersion", decision.rejectedVersion());
                properties.setProperty(keyPrefix + "rejectedPath", decision.rejectedPath().toString());
                properties.setProperty(keyPrefix + "rule", decision.rule());
            }
            properties.setProperty(ARTIFACT_INTEROP_SCOPE_USAGE, classpathUsageMode.cliValue());
            properties.setProperty(
                    ARTIFACT_INTEROP_SCOPE_ALLOWED,
                    String.join(",", classpathUsageMode.allowedScopes())
            );
            properties.setProperty(
                    ARTIFACT_INTEROP_SCOPE_EXCLUDED_COUNT,
                    Integer.toString(classpathScopeExclusions.size())
            );
            for (int index = 0; index < classpathScopeExclusions.size(); index++) {
                final ClasspathScopeExclusion exclusion = classpathScopeExclusions.get(index);
                final String keyPrefix = ARTIFACT_INTEROP_SCOPE_EXCLUDED_ENTRY_PREFIX + index + ".";
                properties.setProperty(keyPrefix + "ownerArtifact", exclusion.ownerArtifact());
                properties.setProperty(keyPrefix + "ownerVersion", exclusion.ownerVersion());
                properties.setProperty(keyPrefix + "dependencyArtifact", exclusion.dependencyArtifact());
                properties.setProperty(keyPrefix + "dependencyVersion", exclusion.dependencyVersion());
                properties.setProperty(keyPrefix + "scope", exclusion.scope());
                properties.setProperty(keyPrefix + "usage", exclusion.usage());
                properties.setProperty(keyPrefix + "excludedPath", exclusion.excludedPath().toString());
            }
            properties.setProperty(
                    "interopBridges.enabled",
                    Boolean.toString(interopBridgeResult.enabled())
            );
            properties.setProperty(
                    "interopBridges.regenerated",
                    Boolean.toString(interopBridgeResult.regenerated())
            );
            properties.setProperty(
                    "interopBridges.targetCount",
                    Integer.toString(interopBridgeResult.targets().size())
            );
            properties.setProperty(
                    "interopBridges.generatedSourceCount",
                    Integer.toString(interopBridgeResult.sourceCount())
            );
            properties.setProperty(
                    ARTIFACT_INTEROP_BRIDGE_SELECTED_COUNT,
                    Integer.toString(interopBridgeResult.selectedTargets().size())
            );
            for (int index = 0; index < interopBridgeResult.selectedTargets().size(); index++) {
                final InteropBridgeArtifact.SelectedTargetIdentity selectedTarget =
                        interopBridgeResult.selectedTargets().get(index);
                final String prefix = ARTIFACT_INTEROP_BRIDGE_SELECTED_PREFIX + index + ".";
                properties.setProperty(prefix + "className", selectedTarget.className());
                properties.setProperty(prefix + "binding", selectedTarget.bindingName());
                properties.setProperty(prefix + "owner", selectedTarget.owner());
                properties.setProperty(prefix + "name", selectedTarget.name());
                properties.setProperty(prefix + "descriptor", selectedTarget.descriptor());
                properties.setProperty(prefix + "invokeKind", selectedTarget.invokeKind());
            }
            properties.setProperty(
                    ARTIFACT_INTEROP_BRIDGE_UNRESOLVED_COUNT,
                    Integer.toString(interopBridgeResult.unresolvedTargets().size())
            );
            for (int index = 0; index < interopBridgeResult.unresolvedTargets().size(); index++) {
                final InteropBridgeArtifact.UnresolvedTarget unresolvedTarget =
                        interopBridgeResult.unresolvedTargets().get(index);
                final String prefix = ARTIFACT_INTEROP_BRIDGE_UNRESOLVED_PREFIX + index + ".";
                properties.setProperty(prefix + "className", unresolvedTarget.className());
                properties.setProperty(prefix + "binding", unresolvedTarget.bindingName());
                properties.setProperty(prefix + "reason", unresolvedTarget.reason());
            }
            properties.setProperty(
                    "tsjWebControllers.controllerCount",
                    Integer.toString(tsjWebControllerResult.controllerClassNames().size())
            );
            properties.setProperty(
                    "tsjWebControllers.generatedSourceCount",
                    Integer.toString(tsjWebControllerResult.sourceCount())
            );
            properties.setProperty(
                    "tsjSpringComponents.componentCount",
                    Integer.toString(tsjSpringComponentResult.componentClassNames().size())
            );
            properties.setProperty(
                    "tsjSpringComponents.generatedSourceCount",
                    Integer.toString(tsjSpringComponentResult.sourceCount())
            );
            properties.setProperty("interopPolicy", interopPolicy.cliValue());
            properties.setProperty("interopTraceEnabled", Boolean.toString(interopTraceEnabled));
            properties.setProperty(
                    "interopAuth.roles",
                    String.join(",", interopAuthorizationResolution.actorRoles())
            );
            properties.setProperty(
                    "interopAuth.requiredRoles",
                    String.join(",", interopAuthorizationResolution.requiredRoles())
            );
            properties.setProperty(
                    "interopAuth.sensitiveRequiredRoles",
                    String.join(",", interopAuthorizationResolution.sensitiveRequiredRoles())
            );
            properties.setProperty(
                    "interopAuth.approvalRequired",
                    Boolean.toString(interopAuthorizationResolution.approvalRequired())
            );
            properties.setProperty(
                    ARTIFACT_INTEROP_CLASSLOADER_ISOLATION,
                    classloaderIsolationMode.cliValue()
            );
            properties.setProperty(ARTIFACT_INTEROP_CLASS_INDEX_PATH, classIndexPath.toString());
            properties.setProperty(
                    ARTIFACT_INTEROP_CLASS_INDEX_SYMBOL_COUNT,
                    Integer.toString(classpathSymbolIndex.symbolCount())
            );
            properties.setProperty(
                    ARTIFACT_INTEROP_CLASS_INDEX_DUPLICATE_COUNT,
                    Integer.toString(classpathSymbolIndex.duplicateCount())
            );
            properties.setProperty(
                    ARTIFACT_INTEROP_CLASS_INDEX_MRJAR_WINNER_COUNT,
                    Integer.toString(classpathSymbolIndex.mrJarWinnerCount())
            );
            properties.setProperty(
                    ARTIFACT_INTEROP_CLASS_INDEX_MRJAR_BASE_WINNER_COUNT,
                    Integer.toString(classpathSymbolIndex.mrJarBaseWinnerCount())
            );
            properties.setProperty(
                    ARTIFACT_INTEROP_CLASS_INDEX_MRJAR_VERSIONED_WINNER_COUNT,
                    Integer.toString(classpathSymbolIndex.mrJarVersionedWinnerCount())
            );
            properties.setProperty("interopDenylist.count", Integer.toString(interopDenylistPatterns.size()));
            properties.setProperty("frontendModule", FrontendModule.moduleName());
            properties.setProperty("irModule", IrModule.moduleName());
            properties.setProperty("backendModule", BackendJvmModule.moduleName());
            properties.setProperty("runtimeModule", RuntimeModule.moduleName());
            properties.setProperty(
                    "optimization.constantFoldingEnabled",
                    Boolean.toString(optimizationOptions.constantFoldingEnabled())
            );
            properties.setProperty(
                    "optimization.deadCodeEliminationEnabled",
                    Boolean.toString(optimizationOptions.deadCodeEliminationEnabled())
            );

            try (OutputStream outputStream = Files.newOutputStream(artifactPath)) {
                properties.store(outputStream, "TSJ compiled artifact");
            }
            return new CompiledArtifact(
                    entryPath,
                    artifactPath,
                    jvmArtifact,
                    new ClasspathResolution(
                            interopClasspathEntries,
                            classpathMediationDecisions,
                            classpathUsageMode,
                            classpathScopeExclusions
                    ),
                    new ClasspathSymbolIndexSummary(
                            classIndexPath,
                            classpathSymbolIndex.symbolCount(),
                            classpathSymbolIndex.duplicateCount(),
                            classpathSymbolIndex.mrJarWinnerCount(),
                            classpathSymbolIndex.mrJarBaseWinnerCount(),
                            classpathSymbolIndex.mrJarVersionedWinnerCount()
                    ),
                    interopBridgeResult,
                    tsjWebControllerResult,
                    tsjSpringComponentResult
            );
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    "TSJ-COMPILE-500",
                    "Failed to create compilation artifact: " + ioException.getMessage(),
                    Map.of("entry", entryPath.toString(), "outDir", outDir.toString())
            );
        }
    }

    private static CliFailure maybeScopeFailure(
            final JvmCompilationException compilationException,
            final ClasspathUsageMode classpathUsageMode,
            final List<ClasspathScopeExclusion> classpathScopeExclusions
    ) {
        if (!"TSJ-INTEROP-INVALID".equals(compilationException.code())) {
            return null;
        }
        final String message = compilationException.getMessage();
        if (message == null) {
            return null;
        }
        final String prefix = "Interop target class was not found: ";
        if (!message.startsWith(prefix)) {
            return null;
        }
        final String targetClass = message.substring(prefix.length()).trim();
        if (targetClass.isEmpty()) {
            return null;
        }
        final ClasspathScopeExclusion matchingExclusion = findScopeExclusionForTargetClass(
                targetClass,
                classpathScopeExclusions
        );
        if (matchingExclusion == null) {
            return null;
        }
        return CliFailure.runtime(
                "TSJ-CLASSPATH-SCOPE",
                "Interop target class `"
                        + targetClass
                        + "` is reachable only through dependency scope `"
                        + matchingExclusion.scope()
                        + "`, which is excluded for `"
                        + classpathUsageMode.cliValue()
                        + "` classpath resolution.",
                Map.of(
                        "targetClass", targetClass,
                        "scope", matchingExclusion.scope(),
                        "usage", classpathUsageMode.cliValue(),
                        "excludedPath", matchingExclusion.excludedPath().toString(),
                        "ownerArtifact", matchingExclusion.ownerArtifact(),
                        "guidance", "Move the dependency to compile/runtime scope or run with compatible classpath scope."
                )
        );
    }

    private static CliFailure maybeRunInteropClasspathFailure(
            final CliFailure failure,
            final Path entryPath
    ) {
        if (!"TSJ-INTEROP-INVALID".equals(failure.code)) {
            return null;
        }
        final String message = failure.getMessage();
        final String prefix = "Interop target class was not found: ";
        if (message == null || !message.startsWith(prefix)) {
            return null;
        }
        final Map<String, String> context = new LinkedHashMap<>(failure.context);
        context.putIfAbsent("entry", entryPath.toAbsolutePath().normalize().toString());
        final String interopTargets = loadInteropTargetsFromGeneratedSpec(context.get("file"));
        if (interopTargets != null) {
            context.put("interopTargets", interopTargets);
        }
        context.putIfAbsent(
                "guidance",
                "Add required jars/classpath entries for `java:` imports or use an explicit interop spec."
        );
        return CliFailure.runtime(
                "TSJ-RUN-006",
                message,
                Map.copyOf(context)
        );
    }

    private static String loadInteropTargetsFromGeneratedSpec(final String specPathValue) {
        if (specPathValue == null || specPathValue.isBlank()) {
            return null;
        }
        final Path specPath = Path.of(specPathValue).toAbsolutePath().normalize();
        if (!Files.exists(specPath) || !Files.isRegularFile(specPath)) {
            return null;
        }
        final Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(specPath)) {
            properties.load(inputStream);
        } catch (final IOException ioException) {
            return null;
        }
        final String targets = properties.getProperty("targets");
        if (targets == null || targets.isBlank()) {
            return null;
        }
        return targets;
    }

    private static void enforceClasspathScopeCompatibility(
            final List<String> discoveredInteropTargets,
            final List<Path> interopClasspathEntries,
            final ClasspathUsageMode classpathUsageMode,
            final List<ClasspathScopeExclusion> classpathScopeExclusions
    ) {
        if (discoveredInteropTargets.isEmpty() || classpathScopeExclusions.isEmpty()) {
            return;
        }
        final LinkedHashSet<String> classes = new LinkedHashSet<>();
        for (String target : discoveredInteropTargets) {
            final int separator = target.indexOf('#');
            if (separator <= 0) {
                continue;
            }
            classes.add(target.substring(0, separator));
        }
        for (String targetClass : classes) {
            if (classExistsInClasspathEntries(targetClass, interopClasspathEntries)) {
                continue;
            }
            final ClasspathScopeExclusion matchingExclusion = findScopeExclusionForTargetClass(
                    targetClass,
                    classpathScopeExclusions
            );
            if (matchingExclusion == null) {
                continue;
            }
            throw CliFailure.runtime(
                    "TSJ-CLASSPATH-SCOPE",
                    "Interop target class `"
                            + targetClass
                            + "` is reachable only through dependency scope `"
                            + matchingExclusion.scope()
                            + "`, which is excluded for `"
                            + classpathUsageMode.cliValue()
                            + "` classpath resolution.",
                    Map.of(
                            "targetClass", targetClass,
                            "scope", matchingExclusion.scope(),
                            "usage", classpathUsageMode.cliValue(),
                            "excludedPath", matchingExclusion.excludedPath().toString(),
                            "ownerArtifact", matchingExclusion.ownerArtifact(),
                            "guidance", "Move the dependency to compile/runtime scope or run with compatible classpath scope."
                    )
            );
        }
    }

    private static boolean classExistsInClasspathEntries(final String className, final List<Path> classpathEntries) {
        for (Path classpathEntry : classpathEntries) {
            final Path normalized = classpathEntry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                final Path classFile = normalized.resolve(className.replace('.', File.separatorChar) + ".class");
                if (Files.isRegularFile(classFile)) {
                    return true;
                }
                continue;
            }
            if (jarContainsClass(normalized, className)) {
                return true;
            }
        }
        return false;
    }

    private static ClasspathScopeExclusion findScopeExclusionForTargetClass(
            final String targetClass,
            final List<ClasspathScopeExclusion> classpathScopeExclusions
    ) {
        for (ClasspathScopeExclusion exclusion : classpathScopeExclusions) {
            if (jarContainsClass(exclusion.excludedPath(), targetClass)) {
                return exclusion;
            }
        }
        return null;
    }

    private static boolean jarContainsClass(final Path jarPath, final String className) {
        final Path normalizedJarPath = jarPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedJarPath)) {
            return false;
        }
        final String entryName = className.replace('.', '/') + ".class";
        try (JarFile jarFile = new JarFile(normalizedJarPath.toFile())) {
            return jarFile.getJarEntry(entryName) != null;
        } catch (final IOException ioException) {
            return false;
        }
    }

    private static SpringPackageResult packageSpringJar(
            final CompiledArtifact artifact,
            final SpringPackageOptions options
    ) {
        final Path jarPath = (options.bootJarPath() == null
                ? options.outDir().resolve(SPRING_PACKAGE_DEFAULT_JAR_NAME)
                : options.bootJarPath())
                .toAbsolutePath()
                .normalize();
        final List<Path> resourceDirectories = resolveSpringResourceDirectories(
                artifact.entryPath(),
                options.resourceDirectories()
        );
        final List<Path> dependencySources = resolveSpringPackageDependencySources(options.interopClasspathEntries());
        try {
            final Path parent = jarPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (final IOException ioException) {
            throw springPackageFailure(
                    "manifest",
                    "Failed to prepare Spring jar output path: " + ioException.getMessage(),
                    jarPath,
                    artifact.entryPath(),
                    Map.of()
            );
        }

        final Manifest manifest = new Manifest();
        final Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, artifact.jvmArtifact().className());
        attributes.putValue("TSJ-Boot-Layout", "fat");

        int resourceFileCount = 0;
        int dependencyEntryCount = 0;
        final Set<String> writtenEntries = new LinkedHashSet<>();
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            try {
                writeDirectoryToJar(
                        jarOutputStream,
                        artifact.jvmArtifact().outputDirectory(),
                        artifact.jvmArtifact().outputDirectory(),
                        writtenEntries
                );
            } catch (final IOException ioException) {
                throw springPackageFailure(
                        "repackage",
                        "Failed to add compiled classes to Spring fat jar: " + ioException.getMessage(),
                        jarPath,
                        artifact.entryPath(),
                        Map.of()
                );
            }
            for (Path resourceDirectory : resourceDirectories) {
                try {
                    resourceFileCount += writeDirectoryToJar(
                            jarOutputStream,
                            resourceDirectory,
                            resourceDirectory,
                            writtenEntries
                    );
                } catch (final IOException ioException) {
                    throw springPackageFailure(
                            "resource",
                            "Failed to add resource directory to Spring fat jar: " + resourceDirectory
                                    + " (" + ioException.getMessage() + ")",
                            jarPath,
                            artifact.entryPath(),
                            Map.of("resourceDir", resourceDirectory.toString())
                    );
                }
            }
            try {
                addFileToJar(
                        jarOutputStream,
                        artifact.artifactPath(),
                        "META-INF/tsj/program.tsj.properties",
                        writtenEntries
                );
            } catch (final IOException ioException) {
                throw springPackageFailure(
                        "manifest",
                        "Failed to add TSJ artifact metadata to Spring fat jar: " + ioException.getMessage(),
                        jarPath,
                        artifact.entryPath(),
                        Map.of()
                );
            }
            for (Path dependencySource : dependencySources) {
                try {
                    dependencyEntryCount += writeClasspathEntryToJar(
                            jarOutputStream,
                            dependencySource,
                            writtenEntries
                    );
                } catch (final IOException ioException) {
                    throw springPackageFailure(
                            "repackage",
                            "Failed to merge dependency into Spring fat jar: " + dependencySource
                                    + " (" + ioException.getMessage() + ")",
                            jarPath,
                            artifact.entryPath(),
                            Map.of("dependency", dependencySource.toString())
                    );
                }
            }
        } catch (final IOException ioException) {
            throw springPackageFailure(
                    "manifest",
                    "Failed to initialize Spring fat jar stream: " + ioException.getMessage(),
                    jarPath,
                    artifact.entryPath(),
                    Map.of()
            );
        }
        return new SpringPackageResult(
                jarPath,
                resourceFileCount,
                resourceDirectories,
                dependencyEntryCount,
                dependencySources
        );
    }

    private static SpringSmokeResult smokeRunSpringPackage(
            final CompiledArtifact artifact,
            final Path jarPath,
            final SpringPackageOptions options
    ) {
        final List<String> command = new ArrayList<>();
        command.add(resolveJavaLauncher().toString());
        if (options.interopTraceEnabled()) {
            command.add("-Dtsj.interop.trace=true");
        }
        command.add("-jar");
        command.add(jarPath.toString());
        final String reproCommand = String.join(" ", command);

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        final Path smokeLogPath;
        try {
            smokeLogPath = Files.createTempFile("tsj-spring-smoke-", ".log");
            processBuilder.redirectOutput(smokeLogPath.toFile());
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    "TSJ-SPRING-BOOT",
                    "Failed to allocate smoke-run log file: " + ioException.getMessage(),
                    Map.of(
                            "stage", "runtime",
                            "jar", jarPath.toString(),
                            "mainClass", artifact.jvmArtifact().className(),
                            "reproCommand", reproCommand
                    )
            );
        }
        final long startedAtNanos = System.nanoTime();
        try {
            final Process process = processBuilder.start();
            if (options.smokeEndpointUrl() != null && !options.smokeEndpointUrl().isBlank()) {
                return runEndpointSmoke(
                        artifact,
                        jarPath,
                        options,
                        process,
                        smokeLogPath,
                        reproCommand,
                        startedAtNanos
                );
            }

            final int exitCode = process.waitFor();
            final String output = readSmokeLog(smokeLogPath);
            final String preview = truncateOutput(output, 240);
            final long runtimeMs = nanosToMillis(System.nanoTime() - startedAtNanos);
            if (exitCode != 0) {
                throw CliFailure.runtime(
                        "TSJ-SPRING-BOOT",
                        "Spring package smoke run failed with exit code " + exitCode + ".",
                        Map.of(
                                "stage", "runtime",
                                "jar", jarPath.toString(),
                                "mainClass", artifact.jvmArtifact().className(),
                                "exitCode", Integer.toString(exitCode),
                                "outputPreview", preview,
                                "runtimeMs", Long.toString(runtimeMs),
                                "reproCommand", reproCommand
                        )
                );
            }
            return new SpringSmokeResult(
                    exitCode,
                    preview,
                    "",
                    "",
                    0,
                    "",
                    runtimeMs,
                    reproCommand
            );
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    "TSJ-SPRING-BOOT",
                    "Failed to launch Spring package smoke run: " + ioException.getMessage(),
                    Map.of(
                        "stage", "runtime",
                        "jar", jarPath.toString(),
                        "mainClass", artifact.jvmArtifact().className(),
                        "reproCommand", reproCommand
                    )
            );
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw CliFailure.runtime(
                    "TSJ-SPRING-BOOT",
                    "Spring package smoke run was interrupted.",
                    Map.of(
                            "stage", "runtime",
                            "jar", jarPath.toString(),
                            "mainClass", artifact.jvmArtifact().className(),
                            "reproCommand", reproCommand
                    )
            );
        } finally {
            try {
                Files.deleteIfExists(smokeLogPath);
            } catch (final IOException ignored) {
                // Ignore cleanup failures for temporary smoke logs.
            }
        }
    }

    private static SpringSmokeResult runEndpointSmoke(
            final CompiledArtifact artifact,
            final Path jarPath,
            final SpringPackageOptions options,
            final Process process,
            final Path smokeLogPath,
            final String reproCommand,
            final long startedAtNanos
    ) throws InterruptedException {
        final long timeoutMs = options.smokeTimeoutMs();
        final long pollMs = options.smokePollMs();
        final String endpointUrl = options.smokeEndpointUrl();
        final String endpointPort = endpointPort(endpointUrl);
        final long deadline = System.nanoTime() + (timeoutMs * 1_000_000L);
        EndpointProbeResult lastProbe = EndpointProbeResult.unavailable("Endpoint check not started.");

        try {
            while (System.nanoTime() < deadline) {
                lastProbe = probeEndpoint(endpointUrl, smokeLogPath);
                if (lastProbe.success()) {
                    final String preview = truncateOutput(readSmokeLog(smokeLogPath), 240);
                    final long runtimeMs = nanosToMillis(System.nanoTime() - startedAtNanos);
                    return new SpringSmokeResult(
                            0,
                            preview,
                            endpointUrl,
                            endpointPort,
                            lastProbe.statusCode(),
                            truncateOutput(lastProbe.responsePreview(), 160),
                            runtimeMs,
                            reproCommand
                    );
                }
                if (!process.isAlive()) {
                    final int exitCode = process.waitFor();
                    final String preview = truncateOutput(readSmokeLog(smokeLogPath), 240);
                    final long runtimeMs = nanosToMillis(System.nanoTime() - startedAtNanos);
                    throw CliFailure.runtime(
                            "TSJ-SPRING-BOOT",
                            "Spring package process exited before endpoint smoke check passed.",
                            Map.of(
                                    "stage", "runtime",
                                    "failureKind", "startup",
                                    "jar", jarPath.toString(),
                                    "mainClass", artifact.jvmArtifact().className(),
                                    "exitCode", Integer.toString(exitCode),
                                    "endpointUrl", endpointUrl,
                                    "endpointPort", endpointPort,
                                    "outputPreview", preview,
                                    "runtimeMs", Long.toString(runtimeMs),
                                    "reproCommand", reproCommand
                            )
                    );
                }
                Thread.sleep(pollMs);
            }
            final String preview = truncateOutput(readSmokeLog(smokeLogPath), 240);
            final long runtimeMs = nanosToMillis(System.nanoTime() - startedAtNanos);
            final Map<String, String> context = new LinkedHashMap<>();
            context.put("stage", "runtime");
            context.put("failureKind", "endpoint");
            context.put("jar", jarPath.toString());
            context.put("mainClass", artifact.jvmArtifact().className());
            context.put("endpointUrl", endpointUrl);
            context.put("endpointPort", endpointPort);
            context.put("runtimeMs", Long.toString(runtimeMs));
            context.put("outputPreview", preview);
            context.put("reproCommand", reproCommand);
            if (lastProbe.statusCode() > 0) {
                context.put("endpointStatus", Integer.toString(lastProbe.statusCode()));
            }
            if (!lastProbe.responsePreview().isBlank()) {
                context.put("endpointResponsePreview", truncateOutput(lastProbe.responsePreview(), 160));
            }
            if (!lastProbe.error().isBlank()) {
                context.put("endpointError", lastProbe.error());
            }
            throw CliFailure.runtime(
                    "TSJ-SPRING-ENDPOINT",
                    "Spring package endpoint smoke check failed for `" + endpointUrl + "`.",
                    Map.copyOf(context)
            );
        } finally {
            destroyProcess(process);
        }
    }

    private static EndpointProbeResult probeEndpoint(final String endpointUrl, final Path smokeLogPath) {
        if (endpointUrl.startsWith("stdout://")) {
            final String marker = endpointUrl.substring("stdout://".length());
            final String output = readSmokeLog(smokeLogPath);
            if (output.contains(marker)) {
                return new EndpointProbeResult(true, 200, "marker:" + marker, "");
            }
            return EndpointProbeResult.unavailable("Marker not observed: " + marker);
        }
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(350);
            connection.setReadTimeout(350);
            final int statusCode = connection.getResponseCode();
            final InputStream bodyStream = statusCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            final String responsePreview;
            if (bodyStream == null) {
                responsePreview = "";
            } else {
                try (InputStream inputStream = bodyStream) {
                    responsePreview = truncateOutput(
                            new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                            160
                    );
                }
            }
            final boolean success = statusCode >= 200 && statusCode < 300;
            return new EndpointProbeResult(success, statusCode, responsePreview, "");
        } catch (final IOException ioException) {
            return EndpointProbeResult.unavailable(ioException.getClass().getSimpleName() + ": " + ioException.getMessage());
        }
    }

    private static String endpointPort(final String endpointUrl) {
        if (endpointUrl != null && endpointUrl.startsWith("stdout://")) {
            return "";
        }
        try {
            final URI uri = URI.create(endpointUrl);
            final int explicitPort = uri.getPort();
            if (explicitPort > 0) {
                return Integer.toString(explicitPort);
            }
            final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return "https".equals(scheme) ? "443" : "80";
        } catch (final IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String readSmokeLog(final Path smokeLogPath) {
        try {
            return Files.readString(smokeLogPath, java.nio.charset.StandardCharsets.UTF_8);
        } catch (final IOException ioException) {
            return "";
        }
    }

    private static void destroyProcess(final Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static long nanosToMillis(final long nanos) {
        return nanos <= 0 ? 0 : nanos / 1_000_000L;
    }

    private static List<Path> resolveSpringPackageDependencySources(final List<Path> interopClasspathEntries) {
        final LinkedHashSet<Path> sources = new LinkedHashSet<>();
        addClassLocation(sources, TsjRuntime.class);
        addClassLocation(sources, TsjJavaInterop.class);
        for (Path interopClasspathEntry : interopClasspathEntries) {
            sources.add(interopClasspathEntry.toAbsolutePath().normalize());
        }
        return List.copyOf(sources);
    }

    private static void addClassLocation(final Set<Path> destination, final Class<?> markerClass) {
        try {
            final URL location = markerClass
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            destination.add(Path.of(location.toURI()).toAbsolutePath().normalize());
        } catch (final Exception exception) {
            throw CliFailure.runtime(
                    "TSJ-SPRING-PACKAGE",
                    "Failed to resolve runtime dependency location for "
                            + markerClass.getName() + ": " + exception.getMessage(),
                    Map.of(
                            "stage", "package",
                            "failureKind", "manifest",
                            "class", markerClass.getName()
                    )
            );
        }
    }

    private static int writeClasspathEntryToJar(
            final JarOutputStream jarOutputStream,
            final Path classpathEntry,
            final Set<String> writtenEntries
    ) throws IOException {
        final Path normalized = classpathEntry.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return writeDirectoryToJar(jarOutputStream, normalized, normalized, writtenEntries);
        }
        final String fileName = normalized.getFileName() == null
                ? ""
                : normalized.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(normalized) && fileName.endsWith(".jar")) {
            return writeJarFileToJar(jarOutputStream, normalized, writtenEntries);
        }
        return 0;
    }

    private static void compileGeneratedSpringAdaptersForPackaging(
            final CompiledArtifact artifact,
            final SpringPackageOptions options
    ) {
        final List<Path> sourceFiles = new ArrayList<>();
        sourceFiles.addAll(collectGeneratedJavaSources(options.outDir().resolve("generated-web")));
        sourceFiles.addAll(collectGeneratedJavaSources(options.outDir().resolve("generated-components")));
        if (sourceFiles.isEmpty()) {
            return;
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw CliFailure.runtime(
                    "TSJ-SPRING-PACKAGE",
                    "JDK compiler is unavailable for generated Spring adapter compilation.",
                    Map.of(
                            "stage", "compile",
                            "failureKind", "generated-adapter-compile",
                            "entry", artifact.entryPath().toAbsolutePath().normalize().toString()
                    )
            );
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics,
                java.util.Locale.ROOT,
                java.nio.charset.StandardCharsets.UTF_8
        )) {
            final Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            String classPath = System.getProperty("java.class.path", "");
            final String classesDir = artifact.jvmArtifact().outputDirectory().toString();
            if (classPath.isBlank()) {
                classPath = classesDir;
            } else {
                classPath = classPath + File.pathSeparator + classesDir;
            }
            for (Path classpathEntry : options.interopClasspathEntries()) {
                classPath = classPath + File.pathSeparator + classpathEntry.toAbsolutePath().normalize();
            }
            final List<String> compilationOptions = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-classpath",
                    classPath,
                    "-d",
                    classesDir
            );
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    compilationOptions,
                    null,
                    compilationUnits
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                final String message = renderFirstDiagnosticMessage(
                        diagnostics.getDiagnostics(),
                        "Generated Spring adapter compile failed without diagnostics."
                );
                throw CliFailure.runtime(
                        "TSJ-SPRING-PACKAGE",
                        "Generated Spring adapter compile failed: " + message,
                        Map.of(
                                "stage", "compile",
                                "failureKind", "generated-adapter-compile",
                                "entry", artifact.entryPath().toAbsolutePath().normalize().toString(),
                                "sourceCount", Integer.toString(sourceFiles.size())
                        )
                );
            }
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    "TSJ-SPRING-PACKAGE",
                    "Failed to compile generated Spring adapters: " + ioException.getMessage(),
                    Map.of(
                            "stage", "compile",
                            "failureKind", "generated-adapter-compile",
                            "entry", artifact.entryPath().toAbsolutePath().normalize().toString(),
                            "sourceCount", Integer.toString(sourceFiles.size())
                    )
            );
        }
    }

    private static List<Path> collectGeneratedJavaSources(final Path rootDirectory) {
        final Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot) || !Files.isDirectory(normalizedRoot)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(normalizedRoot)) {
            return paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    "TSJ-SPRING-PACKAGE",
                    "Failed to scan generated Spring adapter sources: " + ioException.getMessage(),
                    Map.of(
                            "stage", "compile",
                            "failureKind", "generated-adapter-compile",
                            "generatedDir", normalizedRoot.toString()
                    )
            );
        }
    }

    private static String renderFirstDiagnosticMessage(
            final List<Diagnostic<? extends JavaFileObject>> diagnostics,
            final String fallback
    ) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return fallback;
        }
        final Diagnostic<? extends JavaFileObject> first = diagnostics.getFirst();
        final String rendered = first.getMessage(java.util.Locale.ROOT);
        if (rendered == null || rendered.isBlank()) {
            return fallback;
        }
        return rendered;
    }

    private static int writeJarFileToJar(
            final JarOutputStream jarOutputStream,
            final Path sourceJar,
            final Set<String> writtenEntries
    ) throws IOException {
        int added = 0;
        try (JarFile jarFile = new JarFile(sourceJar.toFile())) {
            final java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry sourceEntry = entries.nextElement();
                if (sourceEntry.isDirectory()) {
                    continue;
                }
                final String entryName = sourceEntry.getName().replace('\\', '/');
                if (shouldSkipDependencyEntry(entryName) || !writtenEntries.add(entryName)) {
                    continue;
                }
                final JarEntry mergedEntry = new JarEntry(entryName);
                jarOutputStream.putNextEntry(mergedEntry);
                try (InputStream inputStream = jarFile.getInputStream(sourceEntry)) {
                    inputStream.transferTo(jarOutputStream);
                }
                jarOutputStream.closeEntry();
                added++;
            }
        }
        return added;
    }

    private static boolean shouldSkipDependencyEntry(final String entryName) {
        final String upper = entryName.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) {
            return false;
        }
        return "META-INF/MANIFEST.MF".equals(upper)
                || upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA");
    }

    private static int writeDirectoryToJar(
            final JarOutputStream jarOutputStream,
            final Path baseDirectory,
            final Path sourceDirectory,
            final Set<String> writtenEntries
    ) throws IOException {
        int added = 0;
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            final List<Path> files = paths.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                final String entryName = baseDirectory
                        .relativize(file)
                        .toString()
                        .replace(File.separatorChar, '/');
                if (entryName.isBlank()) {
                    continue;
                }
                if (addFileToJar(jarOutputStream, file, entryName, writtenEntries)) {
                    added++;
                }
            }
        }
        return added;
    }

    private static boolean addFileToJar(
            final JarOutputStream jarOutputStream,
            final Path sourceFile,
            final String entryName,
            final Set<String> writtenEntries
    ) throws IOException {
        final String normalizedEntry = entryName.replace('\\', '/');
        if (!writtenEntries.add(normalizedEntry)) {
            return false;
        }
        final JarEntry jarEntry = new JarEntry(normalizedEntry);
        jarOutputStream.putNextEntry(jarEntry);
        Files.copy(sourceFile, jarOutputStream);
        jarOutputStream.closeEntry();
        return true;
    }

    private static List<Path> resolveSpringResourceDirectories(
            final Path entryPath,
            final List<Path> explicitResourceDirectories
    ) {
        final LinkedHashSet<Path> resourceDirectories = new LinkedHashSet<>();
        final Path parent = entryPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            addDirectoryIfExists(resourceDirectories, parent.resolve("src/main/resources"));
            addDirectoryIfExists(resourceDirectories, parent.resolve("resources"));
        }
        resourceDirectories.addAll(explicitResourceDirectories);
        return List.copyOf(resourceDirectories);
    }

    private static void addDirectoryIfExists(final Set<Path> destination, final Path candidate) {
        final Path normalized = candidate.toAbsolutePath().normalize();
        if (Files.exists(normalized) && Files.isDirectory(normalized)) {
            destination.add(normalized);
        }
    }

    private static Path resolveJavaLauncher() {
        final String executable = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }

    private static String truncateOutput(final String output, final int maxChars) {
        final String safe = output == null ? "" : output.replace('\n', ' ').replace('\r', ' ').trim();
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static CliFailure springPackageFailure(
            final String failureKind,
            final String message,
            final Path jarPath,
            final Path entryPath,
            final Map<String, String> extraContext
    ) {
        final Map<String, String> context = new LinkedHashMap<>();
        context.put("stage", "package");
        context.put("failureKind", failureKind);
        context.put("jar", jarPath.toString());
        context.put("entry", entryPath.toAbsolutePath().normalize().toString());
        context.putAll(extraContext);
        return CliFailure.runtime("TSJ-SPRING-PACKAGE", message, Map.copyOf(context));
    }

    private static AutoInteropBridgeResult maybeGenerateAutoInteropBridges(
            final Path entryPath,
            final Path outDir,
            final Path classesDir,
            final List<Path> interopClasspathEntries,
            final Path interopSpecPath,
            final InteropPolicy interopPolicy,
            final List<String> discoveredTargets
    ) {
        if (discoveredTargets.isEmpty()) {
            if (interopSpecPath == null) {
                return AutoInteropBridgeResult.disabled();
            }
            return new AutoInteropBridgeResult(true, List.of(), false, 0, List.of(), List.of());
        }

        final Path normalizedOutDir = outDir.toAbsolutePath().normalize();
        final Path generatedInteropDir = normalizedOutDir.resolve(AUTO_INTEROP_OUTPUT_DIR);
        final Path metadataPath = generatedInteropDir.resolve("interop-bridges.properties");
        final Path cacheFile = normalizedOutDir.resolve(AUTO_INTEROP_CACHE_FILE);
        final String fingerprint = computeInteropFingerprint(interopSpecPath, discoveredTargets, interopPolicy);
        if (isAutoInteropCacheHit(cacheFile, metadataPath, fingerprint)) {
            return new AutoInteropBridgeResult(
                    true,
                    List.copyOf(discoveredTargets),
                    false,
                    readGeneratedBridgeCount(metadataPath),
                    readSelectedBridgeTargets(metadataPath),
                    readUnresolvedBridgeTargets(metadataPath)
            );
        }

        final Path autoSpecPath = normalizedOutDir.resolve(AUTO_INTEROP_SPEC_FILE);
        writeAutoInteropSpec(interopSpecPath, autoSpecPath, discoveredTargets, interopPolicy);
        final InteropBridgeArtifact bridgeArtifact = generateInteropBridgesWithClasspath(
                autoSpecPath,
                generatedInteropDir,
                interopClasspathEntries
        );
        compileInteropBridgeSources(bridgeArtifact.sourceFiles(), classesDir);
        writeAutoInteropCache(cacheFile, fingerprint);
        return new AutoInteropBridgeResult(
                true,
                List.copyOf(discoveredTargets),
                true,
                bridgeArtifact.sourceFiles().size(),
                bridgeArtifact.selectedTargets(),
                bridgeArtifact.unresolvedTargets()
        );
    }

    private static List<String> discoverInteropTargets(final Path entryPath) {
        final Path normalizedEntry = entryPath.toAbsolutePath().normalize();
        final Set<String> discoveredTargets = new LinkedHashSet<>();
        collectInteropTargets(normalizedEntry, new LinkedHashSet<>(), discoveredTargets);
        final List<String> sortedTargets = new ArrayList<>(discoveredTargets);
        sortedTargets.sort(String::compareTo);
        return List.copyOf(sortedTargets);
    }

    private static TsjWebControllerResult maybeGenerateTsjWebControllerAdapters(
            final Path entryPath,
            final Path outDir,
            final String programClassName
    ) {
        final Path normalizedOutDir = outDir.toAbsolutePath().normalize();
        final Path generatedWebDir = normalizedOutDir.resolve("generated-web");
        try {
            final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                    entryPath,
                    programClassName,
                    generatedWebDir
            );
            return new TsjWebControllerResult(
                    artifact.controllerClassNames(),
                    artifact.sourceFiles().size()
            );
        } catch (final JvmCompilationException compilationException) {
            if (isDecoratorSubsetDiagnostic(compilationException.code())) {
                return new TsjWebControllerResult(List.of(), 0);
            }
            throw compilationException;
        }
    }

    private static TsjSpringComponentResult maybeGenerateTsjSpringComponentAdapters(
            final Path entryPath,
            final Path outDir,
            final String programClassName
    ) {
        final Path normalizedOutDir = outDir.toAbsolutePath().normalize();
        final Path generatedComponentsDir = normalizedOutDir.resolve("generated-components");
        try {
            final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                    entryPath,
                    programClassName,
                    generatedComponentsDir
            );
            return new TsjSpringComponentResult(
                    artifact.componentClassNames(),
                    artifact.sourceFiles().size()
            );
        } catch (final JvmCompilationException compilationException) {
            if (isDecoratorSubsetDiagnostic(compilationException.code())) {
                return new TsjSpringComponentResult(List.of(), 0);
            }
            throw compilationException;
        }
    }

    private static boolean isDecoratorSubsetDiagnostic(final String code) {
        return code != null && code.startsWith("TSJ-DECORATOR-");
    }

    private static void collectInteropTargets(
            final Path sourceFile,
            final Set<Path> visited,
            final Set<String> targets
    ) {
        final Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        if (!Files.exists(normalizedSource) || !Files.isRegularFile(normalizedSource)) {
            return;
        }
        if (!visited.add(normalizedSource)) {
            return;
        }
        final String sourceText;
        try {
            sourceText = Files.readString(normalizedSource);
        } catch (final IOException ioException) {
            return;
        }
        for (StaticImportStatement importStatement : parseImportStatements(sourceText)) {
            final String importPath = importStatement.moduleSpecifier();
            final java.util.regex.Matcher interopImport = INTEROP_MODULE_PATTERN.matcher(importPath);
            if (interopImport.matches()) {
                if (importStatement.typeOnly() || importStatement.namedBindings() == null) {
                    continue;
                }
                final String className = interopImport.group(1);
                for (String importedName : parseImportedNames(importStatement.namedBindings())) {
                    targets.add(className + "#" + importedName);
                }
                continue;
            }
            if (importStatement.typeOnly() || !importPath.startsWith(".")) {
                continue;
            }
            final Path dependency = resolveRelativeModule(normalizedSource, importPath);
            if (dependency != null) {
                collectInteropTargets(dependency, visited, targets);
            }
        }
    }

    private static List<StaticImportStatement> parseImportStatements(final String sourceText) {
        final List<StaticImportStatement> statements = new ArrayList<>();
        final java.util.regex.Matcher importMatcher = IMPORT_STATEMENT_PATTERN.matcher(sourceText);
        while (importMatcher.find()) {
            final String sideEffectModule = importMatcher.group(4);
            if (sideEffectModule != null) {
                statements.add(new StaticImportStatement(sideEffectModule, null, false));
                continue;
            }
            final String importClause = importMatcher.group(2);
            final String importModule = importMatcher.group(3);
            final boolean typeOnly = importMatcher.group(1) != null;
            statements.add(new StaticImportStatement(
                    importModule,
                    extractNamedBindings(importClause),
                    typeOnly
            ));
        }
        return List.copyOf(statements);
    }

    private static String extractNamedBindings(final String importClause) {
        if (importClause == null || importClause.isBlank()) {
            return null;
        }
        final java.util.regex.Matcher namedBindingsMatcher = NAMED_BINDINGS_PATTERN.matcher(importClause);
        if (!namedBindingsMatcher.find()) {
            return null;
        }
        return namedBindingsMatcher.group(1);
    }

    private record StaticImportStatement(
            String moduleSpecifier,
            String namedBindings,
            boolean typeOnly
    ) {
    }

    private static List<String> parseImportedNames(final String rawBindings) {
        final List<String> importedNames = new ArrayList<>();
        final String[] segments = rawBindings.split(",");
        for (String segment : segments) {
            final String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final String importedName;
            if (trimmed.contains(" as ")) {
                final String[] aliasParts = trimmed.split("\\s+as\\s+");
                if (aliasParts.length != 2) {
                    continue;
                }
                importedName = aliasParts[0].trim();
            } else {
                importedName = trimmed;
            }
            if (IDENTIFIER_PATTERN.matcher(importedName).matches()) {
                importedNames.add(importedName);
            }
        }
        return List.copyOf(importedNames);
    }

    private static Path resolveRelativeModule(final Path sourceFile, final String importPath) {
        final Path parent = sourceFile.getParent();
        final Path base = parent == null ? Path.of(importPath) : parent.resolve(importPath);
        final Path normalizedBase = base.normalize();
        final List<Path> candidates = new ArrayList<>();
        final String baseText = normalizedBase.toString();
        if (baseText.endsWith(".ts") || baseText.endsWith(".tsx")) {
            candidates.add(normalizedBase);
        } else {
            candidates.add(Path.of(baseText + ".ts"));
            candidates.add(Path.of(baseText + ".tsx"));
            candidates.add(normalizedBase.resolve("index.ts"));
        }
        for (Path candidate : candidates) {
            final Path normalizedCandidate = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalizedCandidate) && Files.isRegularFile(normalizedCandidate)) {
                return normalizedCandidate;
            }
        }
        return null;
    }

    private static String computeInteropFingerprint(
            final Path interopSpecPath,
            final List<String> discoveredTargets,
            final InteropPolicy interopPolicy
    ) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("Missing SHA-256 digest algorithm.", noSuchAlgorithmException);
        }
        if (interopSpecPath != null) {
            try {
                digest.update(Files.readAllBytes(interopSpecPath));
            } catch (final IOException ioException) {
                throw new JvmCompilationException(
                        "TSJ-INTEROP-INPUT",
                        "Failed to read interop spec for fingerprint: " + ioException.getMessage(),
                        null,
                        null,
                        interopSpecPath.toString(),
                        null,
                        null,
                        ioException
                );
            }
        } else {
            digest.update("no-spec".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (interopPolicy != null) {
            digest.update((byte) '\n');
            digest.update(interopPolicy.cliValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        for (String target : discoveredTargets) {
            digest.update((byte) '\n');
            digest.update(target.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        final byte[] bytes = digest.digest();
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static boolean isAutoInteropCacheHit(
            final Path cacheFile,
            final Path metadataFile,
            final String fingerprint
    ) {
        if (!Files.exists(cacheFile) || !Files.isRegularFile(cacheFile)) {
            return false;
        }
        if (!Files.exists(metadataFile) || !Files.isRegularFile(metadataFile)) {
            return false;
        }
        final Properties cache = new Properties();
        try (InputStream inputStream = Files.newInputStream(cacheFile)) {
            cache.load(inputStream);
        } catch (final IOException ioException) {
            return false;
        }
        final String cachedFingerprint = cache.getProperty(AUTO_INTEROP_CACHE_FINGERPRINT_KEY);
        return fingerprint.equals(cachedFingerprint);
    }

    private static int readGeneratedBridgeCount(final Path metadataPath) {
        final Properties metadata = loadInteropBridgeMetadata(metadataPath);
        if (metadata == null) {
            return 0;
        }
        return parseIntProperty(metadata, "generatedCount", 0);
    }

    private static List<InteropBridgeArtifact.SelectedTargetIdentity> readSelectedBridgeTargets(final Path metadataPath) {
        final Properties metadata = loadInteropBridgeMetadata(metadataPath);
        if (metadata == null) {
            return List.of();
        }
        final int count = parseIntProperty(metadata, "selectedTarget.count", 0);
        final List<InteropBridgeArtifact.SelectedTargetIdentity> selectedTargets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String prefix = "selectedTarget." + index + ".";
            final String className = metadata.getProperty(prefix + "className");
            final String binding = metadata.getProperty(prefix + "binding");
            final String owner = metadata.getProperty(prefix + "owner");
            final String name = metadata.getProperty(prefix + "name");
            final String descriptor = metadata.getProperty(prefix + "descriptor");
            final String invokeKindText = metadata.getProperty(prefix + "invokeKind");
            if (className == null || binding == null || owner == null
                    || name == null || descriptor == null || invokeKindText == null) {
                continue;
            }
            selectedTargets.add(new InteropBridgeArtifact.SelectedTargetIdentity(
                    className,
                    binding,
                    owner,
                    name,
                    descriptor,
                    invokeKindText
            ));
        }
        return List.copyOf(selectedTargets);
    }

    private static List<InteropBridgeArtifact.UnresolvedTarget> readUnresolvedBridgeTargets(final Path metadataPath) {
        final Properties metadata = loadInteropBridgeMetadata(metadataPath);
        if (metadata == null) {
            return List.of();
        }
        final int count = parseIntProperty(metadata, "selectedTarget.unresolved.count", 0);
        final List<InteropBridgeArtifact.UnresolvedTarget> unresolvedTargets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String prefix = "selectedTarget.unresolved." + index + ".";
            final String className = metadata.getProperty(prefix + "className");
            final String binding = metadata.getProperty(prefix + "binding");
            final String reason = metadata.getProperty(prefix + "reason");
            if (className == null || binding == null || reason == null) {
                continue;
            }
            unresolvedTargets.add(new InteropBridgeArtifact.UnresolvedTarget(className, binding, reason));
        }
        return List.copyOf(unresolvedTargets);
    }

    private static Properties loadInteropBridgeMetadata(final Path metadataPath) {
        if (!Files.exists(metadataPath) || !Files.isRegularFile(metadataPath)) {
            return null;
        }
        final Properties metadata = new Properties();
        try (InputStream inputStream = Files.newInputStream(metadataPath)) {
            metadata.load(inputStream);
            return metadata;
        } catch (final IOException ioException) {
            return null;
        }
    }

    private static int parseIntProperty(
            final Properties properties,
            final String key,
            final int defaultValue
    ) {
        final String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException numberFormatException) {
            return defaultValue;
        }
    }

    private static void writeAutoInteropSpec(
            final Path baseInteropSpec,
            final Path autoSpecPath,
            final List<String> discoveredTargets,
            final InteropPolicy interopPolicy
    ) {
        final Properties properties = new Properties();
        if (baseInteropSpec != null) {
            try (InputStream inputStream = Files.newInputStream(baseInteropSpec)) {
                properties.load(inputStream);
            } catch (final IOException ioException) {
                throw new JvmCompilationException(
                        "TSJ-INTEROP-INPUT",
                        "Failed to read interop spec: " + ioException.getMessage(),
                        null,
                        null,
                        baseInteropSpec.toString(),
                        null,
                        null,
                        ioException
                );
            }
        }
        properties.setProperty("targets", String.join(",", discoveredTargets));
        if (baseInteropSpec == null || interopPolicy == InteropPolicy.BROAD) {
            properties.setProperty("allowlist", String.join(",", discoveredTargets));
        }
        try {
            Files.createDirectories(autoSpecPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(autoSpecPath)) {
                properties.store(outputStream, "TSJ auto interop spec");
            }
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-INTEROP-INPUT",
                    "Failed to write auto interop spec: " + ioException.getMessage(),
                    null,
                    null,
                    autoSpecPath.toString(),
                    null,
                    null,
                    ioException
            );
        }
    }

    private static InteropBridgeArtifact generateInteropBridgesWithClasspath(
            final Path autoSpecPath,
            final Path outputDir,
            final List<Path> interopClasspathEntries
    ) {
        if (interopClasspathEntries.isEmpty()) {
            return new InteropBridgeGenerator().generate(autoSpecPath, outputDir);
        }
        final URL[] urls = new URL[interopClasspathEntries.size()];
        for (int index = 0; index < interopClasspathEntries.size(); index++) {
            try {
                urls[index] = interopClasspathEntries.get(index).toUri().toURL();
            } catch (final IOException ioException) {
                throw new JvmCompilationException(
                        "TSJ-INTEROP-INPUT",
                        "Invalid interop classpath entry: " + interopClasspathEntries.get(index),
                        null,
                        null,
                        autoSpecPath.toString(),
                        null,
                        null,
                        ioException
                );
            }
        }
        final Thread thread = Thread.currentThread();
        final ClassLoader original = thread.getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(urls, original)) {
            thread.setContextClassLoader(classLoader);
            return new InteropBridgeGenerator().generate(autoSpecPath, outputDir);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-INTEROP-INPUT",
                    "Failed to create interop classloader: " + ioException.getMessage(),
                    null,
                    null,
                    autoSpecPath.toString(),
                    null,
                    null,
                    ioException
            );
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static void compileInteropBridgeSources(final List<Path> sourceFiles, final Path classesDir) {
        if (sourceFiles.isEmpty()) {
            return;
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-JDK",
                    "JDK compiler is unavailable. Use a JDK runtime for TSJ backend compile."
            );
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics,
                java.util.Locale.ROOT,
                java.nio.charset.StandardCharsets.UTF_8
        )) {
            final Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final String classPath = buildInteropBridgeJavacClasspath(classesDir);
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-classpath",
                    classPath,
                    "-d",
                    classesDir.toString()
            );
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    compilationUnits
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                final List<Diagnostic<? extends JavaFileObject>> collected = diagnostics.getDiagnostics();
                final String message = collected.isEmpty()
                        ? "Generated interop bridge compile failed without diagnostics."
                        : collected.getFirst().getMessage(java.util.Locale.ROOT);
                throw new JvmCompilationException("TSJ-INTEROP-INPUT", message);
            }
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-INTEROP-INPUT",
                    "Failed to compile generated interop bridges: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }
    }

    private static String buildInteropBridgeJavacClasspath(final Path classesDir) {
        final LinkedHashSet<String> entries = new LinkedHashSet<>();
        addClasspathEntries(entries, System.getProperty("java.class.path", ""));
        addClasspathEntry(entries, classesDir);
        addClasspathEntry(entries, classCodeSourcePath(TsjCli.class));
        addClasspathEntry(entries, classCodeSourcePath(RuntimeModule.class));
        addClasspathEntry(entries, classCodeSourcePath(TsjRuntime.class));
        addClasspathEntry(entries, classCodeSourcePath(TsjJavaInterop.class));
        addClasspathEntry(entries, classCodeSourcePath(InteropBridgeGenerator.class));
        return String.join(File.pathSeparator, entries);
    }

    private static void addClasspathEntries(final Set<String> entries, final String classPath) {
        if (classPath == null || classPath.isBlank()) {
            return;
        }
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            if (entry != null && !entry.isBlank()) {
                entries.add(entry);
            }
        }
    }

    private static void addClasspathEntry(final Set<String> entries, final Path candidate) {
        if (candidate == null) {
            return;
        }
        final Path normalized = candidate.toAbsolutePath().normalize();
        if (Files.exists(normalized)) {
            entries.add(normalized.toString());
        }
    }

    private static Path classCodeSourcePath(final Class<?> type) {
        try {
            final URL codeSource = type.getProtectionDomain().getCodeSource().getLocation();
            if (codeSource == null) {
                return null;
            }
            return Path.of(codeSource.toURI()).toAbsolutePath().normalize();
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static void writeAutoInteropCache(final Path cacheFile, final String fingerprint) {
        final Properties properties = new Properties();
        properties.setProperty(AUTO_INTEROP_CACHE_FINGERPRINT_KEY, fingerprint);
        try {
            Files.createDirectories(cacheFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(cacheFile)) {
                properties.store(outputStream, "TSJ auto interop cache");
            }
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-INTEROP-INPUT",
                    "Failed to write auto interop cache: " + ioException.getMessage(),
                    null,
                    null,
                    cacheFile.toString(),
                    null,
                    null,
                    ioException
            );
        }
    }

    private static void executeArtifact(
            final CompiledArtifact artifact,
            final PrintStream stdout,
            final PrintStream stderr,
            final boolean showTsStackTrace,
            final boolean interopTraceEnabled,
            final JvmBytecodeRunner.ClassloaderIsolationMode classloaderIsolationMode
    ) {
        final Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(artifact.artifactPath)) {
            properties.load(inputStream);
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    "TSJ-RUN-001",
                    "Failed to read compilation artifact: " + ioException.getMessage(),
                    Map.of("artifact", artifact.artifactPath.toString())
            );
        }

        final String entry = properties.getProperty("entry", artifact.entryPath.toString());
        final JvmCompiledArtifact executable = resolveExecutableArtifact(artifact, properties, entry);
        final List<Path> interopClasspathEntries = resolveInteropClasspathEntries(properties);
        final boolean installedUnhandledRejectionReporter = showTsStackTrace;
        final boolean originalInteropTrace = TsjJavaInterop.traceEnabled();
        if (interopTraceEnabled) {
            TsjJavaInterop.setTraceEnabled(true);
        }
        if (installedUnhandledRejectionReporter) {
            TsjRuntime.setUnhandledRejectionReporter(
                    reason -> emitUnhandledRejectionWithTsStackTrace(stderr, executable, reason)
            );
        }
        try {
            new JvmBytecodeRunner().run(
                    executable,
                    interopClasspathEntries,
                    classloaderIsolationMode,
                    stdout,
                    stderr
            );
        } catch (final JvmCompilationException compilationException) {
            if (showTsStackTrace) {
                emitTsStackTrace(stderr, executable, compilationException.getCause());
            }
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(artifact.entryPath, compilationException)
            );
        } finally {
            if (installedUnhandledRejectionReporter) {
                TsjRuntime.resetUnhandledRejectionReporter();
            }
            if (interopTraceEnabled) {
                TsjJavaInterop.setTraceEnabled(originalInteropTrace);
            }
        }

        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-RUN-SUCCESS",
                "Artifact executed.",
                Map.of(
                        "entry", entry,
                        "artifact", artifact.artifactPath.toString(),
                        "moduleFingerprint", moduleFingerprint()
                )
        );
    }

    private static JvmCompiledArtifact resolveExecutableArtifact(
            final CompiledArtifact artifact,
            final Properties properties,
            final String entry
    ) {
        if (artifact.jvmArtifact != null) {
            return artifact.jvmArtifact;
        }
        final String className = properties.getProperty("mainClass");
        if (className == null || className.isBlank()) {
            throw CliFailure.runtime(
                    "TSJ-RUN-007",
                    "Compilation artifact is missing `mainClass` metadata.",
                    Map.of("artifact", artifact.artifactPath.toString())
            );
        }
        final String classesDirValue = properties.getProperty(
                "classesDir",
                artifact.artifactPath.getParent().resolve("classes").toString()
        );
        final Path classesDir = Path.of(classesDirValue).toAbsolutePath().normalize();
        final String classFileValue = properties.getProperty(
                "classFile",
                classesDir.resolve(className.replace('.', '/') + ".class").toString()
        );
        final Path classFile = Path.of(classFileValue).toAbsolutePath().normalize();
        final String defaultSourceMapValue = classFile.toString().endsWith(".class")
                ? classFile.toString().substring(0, classFile.toString().length() - ".class".length()) + ".tsj.map"
                : classFile.toString() + ".tsj.map";
        final Path sourceMapFile = Path.of(
                properties.getProperty("sourceMapFile", defaultSourceMapValue)
        ).toAbsolutePath().normalize();
        return new JvmCompiledArtifact(
                Path.of(entry).toAbsolutePath().normalize(),
                classesDir,
                className,
                classFile,
                sourceMapFile
        );
    }

    private static List<Path> resolveInteropClasspathEntries(final Properties properties) {
        final String countValue = properties.getProperty(ARTIFACT_INTEROP_CLASSPATH_COUNT, "0");
        final int count;
        try {
            count = Integer.parseInt(countValue);
        } catch (final NumberFormatException numberFormatException) {
            return List.of();
        }
        final List<Path> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String rawValue = properties.getProperty(ARTIFACT_INTEROP_CLASSPATH_ENTRY_PREFIX + index);
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            entries.add(parseClasspathPath(rawValue).toAbsolutePath().normalize());
        }
        return List.copyOf(entries);
    }

    private static RunOptions parseRunOptions(
            final Path entryPath,
            final String[] args,
            final int startIndex
    ) {
        Path outDir = null;
        Path interopSpecPath = null;
        InteropPolicy interopPolicy = InteropPolicy.STRICT;
        boolean interopPolicyExplicit = false;
        boolean interopRiskAcknowledged = false;
        boolean interopRiskAcknowledgedExplicit = false;
        boolean interopRolesExplicit = false;
        boolean interopApprovalExplicit = false;
        boolean interopTraceEnabled = false;
        Path interopAuditLogPath = null;
        Path interopAuditAggregatePath = null;
        String interopApprovalToken = null;
        boolean showTsStackTrace = false;
        JvmBytecodeRunner.ClassloaderIsolationMode classloaderIsolationMode =
                JvmBytecodeRunner.ClassloaderIsolationMode.SHARED;
        JvmOptimizationOptions optimizationOptions = JvmOptimizationOptions.defaults();
        final List<String> interopDenylistPatterns = new ArrayList<>();
        final List<String> interopRoles = new ArrayList<>();
        final List<ClasspathInput> classpathInputs = new ArrayList<>();
        int index = startIndex;
        while (index < args.length) {
            final String token = args[index];
            if (OPTION_OUT.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--out`."
                    );
                }
                outDir = Path.of(args[index + 1]);
                index += 2;
                continue;
            }
            if (OPTION_CLASSPATH.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--classpath`."
                    );
                }
                classpathInputs.addAll(parseClasspathOptionEntries(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_JAR.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--jar`."
                    );
                }
                classpathInputs.add(new ClasspathInput(Path.of(args[index + 1]), true));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_SPEC.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-spec`."
                    );
                }
                interopSpecPath = normalizeInteropSpecPath(Path.of(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_POLICY.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-policy`."
                    );
                }
                interopPolicy = parseInteropPolicyValue(args[index + 1]);
                interopPolicyExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_ACK_INTEROP_RISK.equals(token)) {
                interopRiskAcknowledged = true;
                interopRiskAcknowledgedExplicit = true;
                index++;
                continue;
            }
            if (OPTION_INTEROP_ROLE.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-role`."
                    );
                }
                interopRoles.addAll(parseInteropRoleValues(args[index + 1]));
                interopRolesExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_APPROVAL.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-approval`."
                    );
                }
                interopApprovalToken = parseInteropApprovalToken(args[index + 1]);
                interopApprovalExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_DENYLIST.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-denylist`."
                    );
                }
                interopDenylistPatterns.addAll(parseInteropDenylistPatterns(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_AUDIT_LOG.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-audit-log`."
                    );
                }
                interopAuditLogPath = Path.of(args[index + 1]).toAbsolutePath().normalize();
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_AUDIT_AGGREGATE.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--interop-audit-aggregate`."
                    );
                }
                interopAuditAggregatePath = Path.of(args[index + 1]).toAbsolutePath().normalize();
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_TRACE.equals(token)) {
                interopTraceEnabled = true;
                index++;
                continue;
            }
            if (OPTION_TS_STACKTRACE.equals(token)) {
                showTsStackTrace = true;
                index++;
                continue;
            }
            if (OPTION_CLASSLOADER_ISOLATION.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage(
                            "TSJ-CLI-006",
                            "Missing value for `--classloader-isolation`."
                    );
                }
                classloaderIsolationMode = parseClassloaderIsolationModeValue(args[index + 1]);
                index += 2;
                continue;
            }
            final JvmOptimizationOptions toggled = parseOptimizationToggle(token);
            if (toggled != null) {
                optimizationOptions = toggled;
                index++;
                continue;
            }
            throw CliFailure.usage(
                    "TSJ-CLI-005",
                    "Unknown option `" + token + "`."
                );
        }
        if (interopAuditAggregatePath != null && interopAuditLogPath == null) {
            throw CliFailure.usage(
                    "TSJ-CLI-011",
                    "Centralized interop audit aggregation requires local fallback log. "
                            + "Add `--interop-audit-log <path>` with `--interop-audit-aggregate`."
            );
        }
        final InteropPolicyResolution policyResolution = resolveInteropPolicyResolution(
                entryPath,
                interopPolicy,
                interopPolicyExplicit,
                interopRiskAcknowledged,
                interopRiskAcknowledgedExplicit
        );
        final InteropAuthorizationResolution authorizationResolution = resolveInteropAuthorizationResolution(
                entryPath,
                interopRoles,
                interopRolesExplicit,
                interopApprovalToken,
                interopApprovalExplicit
        );
        final ClasspathResolution classpathResolution = normalizeClasspathInputs(
                classpathInputs,
                ClasspathUsageMode.RUNTIME
        );
        return new RunOptions(
                outDir,
                showTsStackTrace,
                optimizationOptions,
                classpathResolution.entries(),
                classpathResolution.decisions(),
                classpathResolution.usageMode(),
                classpathResolution.scopeExclusions(),
                interopSpecPath,
                policyResolution.interopPolicy(),
                policyResolution.interopRiskAcknowledged(),
                policyResolution.source(),
                authorizationResolution,
                List.copyOf(interopDenylistPatterns),
                interopAuditLogPath,
                interopAuditAggregatePath,
                interopTraceEnabled,
                classloaderIsolationMode
        );
    }

    private static SpringPackageOptions parseSpringPackageOptions(
            final Path entryPath,
            final String[] args,
            final int startIndex
    ) {
        Path outDir = null;
        Path interopSpecPath = null;
        Path bootJarPath = null;
        InteropPolicy interopPolicy = InteropPolicy.STRICT;
        boolean interopPolicyExplicit = false;
        boolean interopRiskAcknowledged = false;
        boolean interopRiskAcknowledgedExplicit = false;
        boolean interopRolesExplicit = false;
        boolean interopApprovalExplicit = false;
        boolean interopTraceEnabled = false;
        Path interopAuditLogPath = null;
        Path interopAuditAggregatePath = null;
        String interopApprovalToken = null;
        boolean smokeRun = false;
        String smokeEndpointUrl = null;
        long smokeTimeoutMs = DEFAULT_SMOKE_ENDPOINT_TIMEOUT_MS;
        long smokePollMs = DEFAULT_SMOKE_ENDPOINT_POLL_MS;
        JvmOptimizationOptions optimizationOptions = JvmOptimizationOptions.defaults();
        final List<String> interopDenylistPatterns = new ArrayList<>();
        final List<String> interopRoles = new ArrayList<>();
        final List<ClasspathInput> classpathInputs = new ArrayList<>();
        final List<Path> resourceDirs = new ArrayList<>();
        int index = startIndex;
        while (index < args.length) {
            final String token = args[index];
            if (OPTION_OUT.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--out`.");
                }
                outDir = Path.of(args[index + 1]);
                index += 2;
                continue;
            }
            if (OPTION_CLASSPATH.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--classpath`.");
                }
                classpathInputs.addAll(parseClasspathOptionEntries(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_JAR.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--jar`.");
                }
                classpathInputs.add(new ClasspathInput(Path.of(args[index + 1]), true));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_SPEC.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--interop-spec`.");
                }
                interopSpecPath = normalizeInteropSpecPath(Path.of(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_POLICY.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--interop-policy`.");
                }
                interopPolicy = parseInteropPolicyValue(args[index + 1]);
                interopPolicyExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_ACK_INTEROP_RISK.equals(token)) {
                interopRiskAcknowledged = true;
                interopRiskAcknowledgedExplicit = true;
                index++;
                continue;
            }
            if (OPTION_INTEROP_ROLE.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--interop-role`.");
                }
                interopRoles.addAll(parseInteropRoleValues(args[index + 1]));
                interopRolesExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_APPROVAL.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--interop-approval`.");
                }
                interopApprovalToken = parseInteropApprovalToken(args[index + 1]);
                interopApprovalExplicit = true;
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_DENYLIST.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--interop-denylist`.");
                }
                interopDenylistPatterns.addAll(parseInteropDenylistPatterns(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_AUDIT_LOG.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--interop-audit-log`.");
                }
                interopAuditLogPath = Path.of(args[index + 1]).toAbsolutePath().normalize();
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_AUDIT_AGGREGATE.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--interop-audit-aggregate`.");
                }
                interopAuditAggregatePath = Path.of(args[index + 1]).toAbsolutePath().normalize();
                index += 2;
                continue;
            }
            if (OPTION_INTEROP_TRACE.equals(token)) {
                interopTraceEnabled = true;
                index++;
                continue;
            }
            if (OPTION_RESOURCE_DIR.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--resource-dir`.");
                }
                resourceDirs.add(Path.of(args[index + 1]));
                index += 2;
                continue;
            }
            if (OPTION_BOOT_JAR.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-006", "Missing value for `--boot-jar`.");
                }
                bootJarPath = Path.of(args[index + 1]);
                index += 2;
                continue;
            }
            if (OPTION_SMOKE_RUN.equals(token)) {
                smokeRun = true;
                index++;
                continue;
            }
            if (OPTION_SMOKE_ENDPOINT_URL.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-017", "Missing value for `--smoke-endpoint-url`.");
                }
                smokeEndpointUrl = parseSmokeEndpointUrl(args[index + 1]);
                index += 2;
                continue;
            }
            if (OPTION_SMOKE_TIMEOUT_MS.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-017", "Missing value for `--smoke-timeout-ms`.");
                }
                smokeTimeoutMs = parseSmokeDurationMillisOption(args[index + 1], OPTION_SMOKE_TIMEOUT_MS);
                index += 2;
                continue;
            }
            if (OPTION_SMOKE_POLL_MS.equals(token)) {
                if (index + 1 >= args.length) {
                    throw CliFailure.usage("TSJ-CLI-017", "Missing value for `--smoke-poll-ms`.");
                }
                smokePollMs = parseSmokeDurationMillisOption(args[index + 1], OPTION_SMOKE_POLL_MS);
                index += 2;
                continue;
            }
            final JvmOptimizationOptions toggled = parseOptimizationToggle(token);
            if (toggled != null) {
                optimizationOptions = toggled;
                index++;
                continue;
            }
            throw CliFailure.usage("TSJ-CLI-005", "Unknown option `" + token + "`.");
        }
        if (outDir == null) {
            throw CliFailure.usage("TSJ-CLI-014", "Missing required option `--out`.");
        }
        if (smokeEndpointUrl != null && !smokeRun) {
            throw CliFailure.usage(
                    "TSJ-CLI-017",
                    "`--smoke-endpoint-url` requires `--smoke-run`."
            );
        }
        if (smokePollMs > smokeTimeoutMs) {
            throw CliFailure.usage(
                    "TSJ-CLI-017",
                    "`--smoke-poll-ms` must be <= `--smoke-timeout-ms`."
            );
        }
        if (interopAuditAggregatePath != null && interopAuditLogPath == null) {
            throw CliFailure.usage(
                    "TSJ-CLI-011",
                    "Centralized interop audit aggregation requires local fallback log. "
                            + "Add `--interop-audit-log <path>` with `--interop-audit-aggregate`."
            );
        }
        final InteropPolicyResolution policyResolution = resolveInteropPolicyResolution(
                entryPath,
                interopPolicy,
                interopPolicyExplicit,
                interopRiskAcknowledged,
                interopRiskAcknowledgedExplicit
        );
        final InteropAuthorizationResolution authorizationResolution = resolveInteropAuthorizationResolution(
                entryPath,
                interopRoles,
                interopRolesExplicit,
                interopApprovalToken,
                interopApprovalExplicit
        );
        final ClasspathResolution classpathResolution = normalizeClasspathInputs(
                classpathInputs,
                ClasspathUsageMode.RUNTIME
        );
        return new SpringPackageOptions(
                outDir,
                optimizationOptions,
                classpathResolution.entries(),
                classpathResolution.decisions(),
                classpathResolution.usageMode(),
                classpathResolution.scopeExclusions(),
                interopSpecPath,
                policyResolution.interopPolicy(),
                policyResolution.interopRiskAcknowledged(),
                policyResolution.source(),
                authorizationResolution,
                List.copyOf(interopDenylistPatterns),
                interopAuditLogPath,
                interopAuditAggregatePath,
                interopTraceEnabled,
                normalizeResourceDirectories(resourceDirs),
                bootJarPath == null ? null : bootJarPath.toAbsolutePath().normalize(),
                smokeRun,
                smokeEndpointUrl,
                smokeTimeoutMs,
                smokePollMs
        );
    }

    private static InteropPolicy parseInteropPolicyValue(final String rawValue) {
        if (rawValue == null) {
            throw CliFailure.usage(
                    "TSJ-CLI-013",
                    "Invalid value for `--interop-policy`: null. Expected `strict` or `broad`."
            );
        }
        return switch (rawValue.trim()) {
            case "strict" -> InteropPolicy.STRICT;
            case "broad" -> InteropPolicy.BROAD;
            default -> throw CliFailure.usage(
                    "TSJ-CLI-013",
                    "Invalid value for `--interop-policy`: `" + rawValue + "`. Expected `strict` or `broad`."
            );
        };
    }

    private static String parseSmokeEndpointUrl(final String rawValue) {
        final String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw CliFailure.usage("TSJ-CLI-017", "`--smoke-endpoint-url` must not be empty.");
        }
        if (value.startsWith("stdout://")) {
            final String marker = value.substring("stdout://".length()).trim();
            if (marker.isEmpty()) {
                throw CliFailure.usage(
                        "TSJ-CLI-017",
                        "`--smoke-endpoint-url stdout://` requires a non-empty marker token."
                );
            }
            return value;
        }
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw CliFailure.usage(
                    "TSJ-CLI-017",
                    "Invalid value for `--smoke-endpoint-url`: `" + rawValue + "`."
            );
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw CliFailure.usage(
                    "TSJ-CLI-017",
                    "Unsupported scheme for `--smoke-endpoint-url`: `"
                            + value
                            + "`. Use http/https or stdout://<marker>."
            );
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw CliFailure.usage(
                    "TSJ-CLI-017",
                    "Invalid host for `--smoke-endpoint-url`: `" + value + "`."
            );
        }
        return uri.toString();
    }

    private static long parseSmokeDurationMillisOption(final String rawValue, final String optionName) {
        final String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw CliFailure.usage("TSJ-CLI-017", "Missing value for `" + optionName + "`.");
        }
        final long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (final NumberFormatException numberFormatException) {
            throw CliFailure.usage(
                    "TSJ-CLI-017",
                    "Invalid numeric value for `" + optionName + "`: `" + rawValue + "`."
            );
        }
        if (parsed <= 0) {
            throw CliFailure.usage(
                    "TSJ-CLI-017",
                    "`" + optionName + "` must be > 0."
            );
        }
        return parsed;
    }

    private static JvmBytecodeRunner.ClassloaderIsolationMode parseClassloaderIsolationModeValue(
            final String rawValue
    ) {
        if (rawValue == null) {
            throw CliFailure.usage(
                    "TSJ-CLI-015",
                    "Invalid value for `--classloader-isolation`: null. Expected `shared` or `app-isolated`."
            );
        }
        return switch (rawValue.trim()) {
            case "shared" -> JvmBytecodeRunner.ClassloaderIsolationMode.SHARED;
            case "app-isolated" -> JvmBytecodeRunner.ClassloaderIsolationMode.APP_ISOLATED;
            default -> throw CliFailure.usage(
                    "TSJ-CLI-015",
                    "Invalid value for `--classloader-isolation`: `"
                            + rawValue
                            + "`. Expected `shared` or `app-isolated`."
            );
        };
    }

    private static InteropPolicyResolution resolveInteropPolicyResolution(
            final Path entryPath,
            final InteropPolicy commandPolicy,
            final boolean commandPolicyExplicit,
            final boolean commandRiskAcknowledged,
            final boolean commandRiskAcknowledgedExplicit
    ) {
        final FleetPolicySource globalSource = loadFleetPolicySource(resolveGlobalPolicyPath());
        final FleetPolicySource projectSource = loadFleetPolicySource(resolveProjectPolicyPath(entryPath));
        if (!commandPolicyExplicit
                && globalSource != null
                && globalSource.interopPolicy() != null
                && projectSource != null
                && projectSource.interopPolicy() != null
                && globalSource.interopPolicy() != projectSource.interopPolicy()) {
            throw CliFailure.runtime(
                    "TSJ-INTEROP-POLICY-CONFLICT",
                    "Conflicting interop policies detected across fleet sources. Resolve by aligning policy files "
                            + "or passing an explicit `--interop-policy` override.",
                    Map.of(
                            "globalPolicyPath", globalSource.path().toString(),
                            "globalPolicy", globalSource.interopPolicy().cliValue(),
                            "projectPolicyPath", projectSource.path().toString(),
                            "projectPolicy", projectSource.interopPolicy().cliValue(),
                            "guidance", "Use --interop-policy strict|broad to select one policy explicitly."
                    )
            );
        }

        InteropPolicy resolvedPolicy = InteropPolicy.STRICT;
        String policySource = "default";
        boolean resolvedRiskAcknowledged = false;
        if (globalSource != null) {
            if (globalSource.interopPolicy() != null) {
                resolvedPolicy = globalSource.interopPolicy();
                policySource = "global";
            }
            if (globalSource.interopRiskAcknowledged() != null) {
                resolvedRiskAcknowledged = globalSource.interopRiskAcknowledged();
            }
        }
        if (projectSource != null) {
            if (projectSource.interopPolicy() != null) {
                resolvedPolicy = projectSource.interopPolicy();
                policySource = "project";
            }
            if (projectSource.interopRiskAcknowledged() != null) {
                resolvedRiskAcknowledged = projectSource.interopRiskAcknowledged();
            }
        }
        if (commandPolicyExplicit) {
            resolvedPolicy = commandPolicy;
            policySource = "command";
        }
        if (commandRiskAcknowledgedExplicit) {
            resolvedRiskAcknowledged = commandRiskAcknowledged;
        }
        return new InteropPolicyResolution(resolvedPolicy, resolvedRiskAcknowledged, policySource);
    }

    private static InteropAuthorizationResolution resolveInteropAuthorizationResolution(
            final Path entryPath,
            final List<String> commandRoles,
            final boolean commandRolesExplicit,
            final String commandApprovalToken,
            final boolean commandApprovalExplicit
    ) {
        final FleetPolicySource globalSource = loadFleetPolicySource(resolveGlobalPolicyPath());
        final FleetPolicySource projectSource = loadFleetPolicySource(resolveProjectPolicyPath(entryPath));
        List<String> actorRoles = List.of();
        List<String> requiredRoles = List.of();
        List<String> sensitiveTargets = List.of();
        List<String> sensitiveRequiredRoles = List.of();
        boolean approvalRequired = false;
        String expectedApprovalToken = null;
        List<String> approvalTargets = List.of();
        String source = "default";

        if (globalSource != null) {
            if (globalSource.interopRbacRoles() != null) {
                actorRoles = globalSource.interopRbacRoles();
            }
            if (globalSource.interopRbacRequiredRoles() != null) {
                requiredRoles = globalSource.interopRbacRequiredRoles();
                source = "global";
            }
            if (globalSource.interopRbacSensitiveTargets() != null) {
                sensitiveTargets = globalSource.interopRbacSensitiveTargets();
                source = "global";
            }
            if (globalSource.interopRbacSensitiveRequiredRoles() != null) {
                sensitiveRequiredRoles = globalSource.interopRbacSensitiveRequiredRoles();
                source = "global";
            }
            if (globalSource.interopApprovalRequired() != null) {
                approvalRequired = globalSource.interopApprovalRequired();
                source = "global";
            }
            if (globalSource.interopApprovalToken() != null) {
                expectedApprovalToken = globalSource.interopApprovalToken();
                source = "global";
            }
            if (globalSource.interopApprovalTargets() != null) {
                approvalTargets = globalSource.interopApprovalTargets();
                source = "global";
            }
        }
        if (projectSource != null) {
            if (projectSource.interopRbacRoles() != null) {
                actorRoles = projectSource.interopRbacRoles();
            }
            if (projectSource.interopRbacRequiredRoles() != null) {
                requiredRoles = projectSource.interopRbacRequiredRoles();
                source = "project";
            }
            if (projectSource.interopRbacSensitiveTargets() != null) {
                sensitiveTargets = projectSource.interopRbacSensitiveTargets();
                source = "project";
            }
            if (projectSource.interopRbacSensitiveRequiredRoles() != null) {
                sensitiveRequiredRoles = projectSource.interopRbacSensitiveRequiredRoles();
                source = "project";
            }
            if (projectSource.interopApprovalRequired() != null) {
                approvalRequired = projectSource.interopApprovalRequired();
                source = "project";
            }
            if (projectSource.interopApprovalToken() != null) {
                expectedApprovalToken = projectSource.interopApprovalToken();
                source = "project";
            }
            if (projectSource.interopApprovalTargets() != null) {
                approvalTargets = projectSource.interopApprovalTargets();
                source = "project";
            }
        }
        if (commandRolesExplicit) {
            actorRoles = List.copyOf(commandRoles);
        }
        final String providedApprovalToken = commandApprovalExplicit ? commandApprovalToken : null;
        return new InteropAuthorizationResolution(
                List.copyOf(actorRoles),
                List.copyOf(requiredRoles),
                List.copyOf(sensitiveTargets),
                List.copyOf(sensitiveRequiredRoles),
                approvalRequired,
                expectedApprovalToken,
                List.copyOf(approvalTargets),
                providedApprovalToken,
                source
        );
    }

    private static FleetPolicySource loadFleetPolicySource(final Path policyPath) {
        if (policyPath == null || !Files.isRegularFile(policyPath)) {
            return null;
        }
        final Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(policyPath)) {
            properties.load(inputStream);
        } catch (final IOException ioException) {
            return null;
        }

        final String rawPolicy = properties.getProperty(POLICY_KEY_INTEROP_POLICY);
        final String rawRisk = properties.getProperty(POLICY_KEY_INTEROP_ACK_RISK);
        final List<String> interopRbacRoles = parsePolicyList(properties.getProperty(POLICY_KEY_INTEROP_RBAC_ROLES));
        final List<String> interopRbacRequiredRoles = parsePolicyList(
                properties.getProperty(POLICY_KEY_INTEROP_RBAC_REQUIRED_ROLES)
        );
        final List<String> interopRbacSensitiveTargets = parsePolicyList(
                properties.getProperty(POLICY_KEY_INTEROP_RBAC_SENSITIVE_TARGETS)
        );
        final List<String> interopRbacSensitiveRequiredRoles = parsePolicyList(
                properties.getProperty(POLICY_KEY_INTEROP_RBAC_SENSITIVE_REQUIRED_ROLES)
        );
        final Boolean interopApprovalRequired = parsePolicyBoolean(
                properties.getProperty(POLICY_KEY_INTEROP_APPROVAL_REQUIRED)
        );
        final String interopApprovalToken = parsePolicyString(properties.getProperty(POLICY_KEY_INTEROP_APPROVAL_TOKEN));
        final List<String> interopApprovalTargets = parsePolicyList(
                properties.getProperty(POLICY_KEY_INTEROP_APPROVAL_TARGETS)
        );
        final InteropPolicy interopPolicy = parsePolicyValueFromSource(rawPolicy);
        final Boolean interopRiskAcknowledged = parsePolicyBoolean(rawRisk);
        if (interopPolicy == null
                && interopRiskAcknowledged == null
                && interopRbacRoles == null
                && interopRbacRequiredRoles == null
                && interopRbacSensitiveTargets == null
                && interopRbacSensitiveRequiredRoles == null
                && interopApprovalRequired == null
                && interopApprovalToken == null
                && interopApprovalTargets == null) {
            return null;
        }
        return new FleetPolicySource(
                policyPath.toAbsolutePath().normalize(),
                interopPolicy,
                interopRiskAcknowledged,
                interopRbacRoles == null ? null : List.copyOf(interopRbacRoles),
                interopRbacRequiredRoles == null ? null : List.copyOf(interopRbacRequiredRoles),
                interopRbacSensitiveTargets == null ? null : List.copyOf(interopRbacSensitiveTargets),
                interopRbacSensitiveRequiredRoles == null ? null : List.copyOf(interopRbacSensitiveRequiredRoles),
                interopApprovalRequired,
                interopApprovalToken,
                interopApprovalTargets == null ? null : List.copyOf(interopApprovalTargets)
        );
    }

    private static InteropPolicy parsePolicyValueFromSource(final String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (rawValue.trim()) {
            case "strict" -> InteropPolicy.STRICT;
            case "broad" -> InteropPolicy.BROAD;
            default -> null;
        };
    }

    private static Boolean parsePolicyBoolean(final String rawValue) {
        if (rawValue == null) {
            return null;
        }
        final String normalized = rawValue.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String parsePolicyString(final String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return rawValue.trim();
    }

    private static List<String> parsePolicyList(final String rawValue) {
        if (rawValue == null) {
            return null;
        }
        final String[] segments = rawValue.split(",");
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String segment : segments) {
            final String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            values.add(trimmed);
        }
        return List.copyOf(values);
    }

    private static Path resolveGlobalPolicyPath() {
        final String overridePath = System.getProperty(SYSTEM_PROPERTY_GLOBAL_POLICY_PATH);
        if (overridePath != null && !overridePath.isBlank()) {
            return Path.of(overridePath).toAbsolutePath().normalize();
        }
        final String envPath = System.getenv(ENV_GLOBAL_POLICY_PATH);
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath).toAbsolutePath().normalize();
        }
        final String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        return Path.of(home).resolve(PROJECT_POLICY_RELATIVE_PATH).toAbsolutePath().normalize();
    }

    private static Path resolveProjectPolicyPath(final Path entryPath) {
        if (entryPath == null) {
            return null;
        }
        Path current = entryPath.toAbsolutePath().normalize().getParent();
        while (current != null) {
            final Path candidate = current.resolve(PROJECT_POLICY_RELATIVE_PATH).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static void enforceInteropPolicy(
            final Path entryPath,
            final InteropPolicy interopPolicy,
            final Path interopSpecPath,
            final List<String> discoveredInteropTargets
    ) {
        if (interopPolicy != InteropPolicy.STRICT) {
            return;
        }
        if (discoveredInteropTargets.isEmpty()) {
            return;
        }
        if (interopSpecPath != null) {
            return;
        }
        throw CliFailure.runtime(
                "TSJ-INTEROP-POLICY",
                "Strict interop policy requires `--interop-spec` when `java:` bindings are used.",
                Map.of(
                        "entry", entryPath.toAbsolutePath().normalize().toString(),
                        "interopPolicy", interopPolicy.cliValue(),
                        "interopBindingCount", Integer.toString(discoveredInteropTargets.size())
                )
        );
    }

    private static void enforceInteropRiskAcknowledgement(
            final Path entryPath,
            final InteropPolicy interopPolicy,
            final boolean interopRiskAcknowledged,
            final List<String> discoveredInteropTargets
    ) {
        if (interopPolicy != InteropPolicy.BROAD) {
            return;
        }
        if (discoveredInteropTargets.isEmpty()) {
            return;
        }
        if (interopRiskAcknowledged) {
            return;
        }
        throw CliFailure.runtime(
                "TSJ-INTEROP-RISK",
                "Broad interop policy requires explicit risk acknowledgement. Add `--ack-interop-risk`.",
                Map.of(
                        "entry", entryPath.toAbsolutePath().normalize().toString(),
                        "interopPolicy", interopPolicy.cliValue(),
                        "interopBindingCount", Integer.toString(discoveredInteropTargets.size())
                )
        );
    }

    private static void enforceInteropAuthorization(
            final Path entryPath,
            final InteropPolicy interopPolicy,
            final List<String> discoveredInteropTargets,
            final InteropAuthorizationResolution authorizationResolution
    ) {
        if (interopPolicy != InteropPolicy.BROAD) {
            return;
        }
        if (discoveredInteropTargets.isEmpty()) {
            return;
        }
        if (authorizationResolution == null) {
            return;
        }

        final List<String> sensitiveTargets = matchTargetsByPatterns(
                discoveredInteropTargets,
                authorizationResolution.sensitiveTargets()
        );
        final boolean sensitiveScope = !sensitiveTargets.isEmpty();
        final List<String> requiredRoles = sensitiveScope
                && !authorizationResolution.sensitiveRequiredRoles().isEmpty()
                ? authorizationResolution.sensitiveRequiredRoles()
                : authorizationResolution.requiredRoles();
        final String scope = sensitiveScope ? "sensitive" : "general";

        if (!requiredRoles.isEmpty()
                && !hasRequiredRole(
                        authorizationResolution.actorRoles(),
                        requiredRoles
                )) {
            throw CliFailure.runtime(
                    "TSJ-INTEROP-RBAC",
                    "Interop authorization failed: missing role for `" + scope + "` scope.",
                    Map.of(
                            "entry", entryPath.toAbsolutePath().normalize().toString(),
                            "scope", scope,
                            "requiredRoles", String.join(",", requiredRoles),
                            "actorRoles", String.join(",", authorizationResolution.actorRoles()),
                            "source", authorizationResolution.source(),
                            "target", sensitiveScope
                                    ? sensitiveTargets.get(0)
                                    : discoveredInteropTargets.get(0)
                    )
            );
        }

        if (!authorizationResolution.approvalRequired()) {
            return;
        }
        final List<String> approvalTargets = authorizationResolution.approvalTargets().isEmpty()
                ? List.copyOf(discoveredInteropTargets)
                : matchTargetsByPatterns(discoveredInteropTargets, authorizationResolution.approvalTargets());
        if (approvalTargets.isEmpty()) {
            return;
        }
        if (approvalTokenMatches(
                authorizationResolution.providedApprovalToken(),
                authorizationResolution.expectedApprovalToken()
        )) {
            return;
        }
        throw CliFailure.runtime(
                "TSJ-INTEROP-APPROVAL",
                "Sensitive interop operation requires approval. Add `--interop-approval <token>`.",
                Map.of(
                        "entry", entryPath.toAbsolutePath().normalize().toString(),
                        "scope", "approval",
                        "approvalTargets", String.join(",", approvalTargets),
                        "expectedApproval", authorizationResolution.expectedApprovalToken() == null
                                ? ""
                                : authorizationResolution.expectedApprovalToken(),
                        "providedApproval", authorizationResolution.providedApprovalToken() == null
                                ? ""
                                : authorizationResolution.providedApprovalToken()
                )
        );
    }

    private static boolean hasRequiredRole(final List<String> actorRoles, final List<String> requiredRoles) {
        final LinkedHashSet<String> normalizedActors = new LinkedHashSet<>();
        for (String actorRole : actorRoles) {
            normalizedActors.add(actorRole.toLowerCase(Locale.ROOT));
        }
        for (String requiredRole : requiredRoles) {
            if (normalizedActors.contains(requiredRole.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean approvalTokenMatches(
            final String providedApprovalToken,
            final String expectedApprovalToken
    ) {
        if (providedApprovalToken == null || providedApprovalToken.isBlank()) {
            return false;
        }
        if (expectedApprovalToken == null || expectedApprovalToken.isBlank()) {
            return true;
        }
        return expectedApprovalToken.equals(providedApprovalToken);
    }

    private static List<String> matchTargetsByPatterns(
            final List<String> targets,
            final List<String> patterns
    ) {
        if (targets.isEmpty() || patterns.isEmpty()) {
            return List.of();
        }
        final LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String target : targets) {
            for (String pattern : patterns) {
                if (matchesInteropDenylistPattern(target, pattern)) {
                    matched.add(target);
                    break;
                }
            }
        }
        return List.copyOf(matched);
    }

    private static void enforceInteropDenylist(
            final Path entryPath,
            final List<String> discoveredInteropTargets,
            final List<String> denylistPatterns
    ) {
        if (denylistPatterns == null || denylistPatterns.isEmpty()) {
            return;
        }
        for (String target : discoveredInteropTargets) {
            for (String pattern : denylistPatterns) {
                if (!matchesInteropDenylistPattern(target, pattern)) {
                    continue;
                }
                throw CliFailure.runtime(
                        "TSJ-INTEROP-DENYLIST",
                        "Interop target `" + target + "` is blocked by denylist pattern `" + pattern + "`.",
                        Map.of(
                                "entry", entryPath.toAbsolutePath().normalize().toString(),
                                "target", target,
                                "pattern", pattern
                        )
                );
            }
        }
    }

    private static boolean matchesInteropDenylistPattern(final String target, final String pattern) {
        final String trimmed = pattern == null ? "" : pattern.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.endsWith(".*")) {
            final String prefix = trimmed.substring(0, trimmed.length() - 1);
            return target.startsWith(prefix);
        }
        if (trimmed.endsWith("*")) {
            final String prefix = trimmed.substring(0, trimmed.length() - 1);
            return target.startsWith(prefix);
        }
        if (trimmed.contains("#")) {
            return target.equals(trimmed);
        }
        return target.equals(trimmed) || target.startsWith(trimmed + "#");
    }

    private static void appendInteropAuditLog(
            final Path auditLogPath,
            final Path aggregateAuditPath,
            final String commandName,
            final Path entryPath,
            final InteropPolicy interopPolicy,
            final boolean interopTraceEnabled,
            final List<String> denylistPatterns,
            final List<String> discoveredInteropTargets,
            final String decision,
            final String code,
            final String message
    ) {
        final Path normalizedAuditLog = auditLogPath == null ? null : auditLogPath.toAbsolutePath().normalize();
        final String timestamp = Instant.now().toString();
        if (normalizedAuditLog != null) {
            appendAuditLine(
                    normalizedAuditLog,
                    toLocalInteropAuditJson(
                            timestamp,
                            commandName,
                            entryPath,
                            interopPolicy,
                            interopTraceEnabled,
                            denylistPatterns,
                            discoveredInteropTargets,
                            decision,
                            code,
                            message
                    ),
                    "TSJ-INTEROP-AUDIT",
                    "Failed to write interop audit log: "
            );
        }
        if (aggregateAuditPath == null) {
            return;
        }
        final Path normalizedAggregateAudit = aggregateAuditPath.toAbsolutePath().normalize();
        try {
            appendAggregateInteropAuditEvents(
                    normalizedAggregateAudit,
                    timestamp,
                    commandName,
                    entryPath,
                    interopPolicy,
                    interopTraceEnabled,
                    denylistPatterns,
                    discoveredInteropTargets,
                    decision,
                    code,
                    message
            );
        } catch (final IOException ioException) {
            final String aggregateFailureMessage = "Centralized audit sink unavailable: "
                    + ioException.getMessage();
            if (normalizedAuditLog != null) {
                appendAuditLine(
                        normalizedAuditLog,
                        toLocalInteropAuditJson(
                                timestamp,
                                commandName,
                                entryPath,
                                interopPolicy,
                                interopTraceEnabled,
                                denylistPatterns,
                                discoveredInteropTargets,
                                "warn",
                                "TSJ-INTEROP-AUDIT-AGGREGATE",
                                aggregateFailureMessage + " (fallback=local)"
                        ),
                        "TSJ-INTEROP-AUDIT",
                        "Failed to write interop audit fallback log: "
                );
                return;
            }
            throw CliFailure.runtime(
                    "TSJ-INTEROP-AUDIT-AGGREGATE",
                    aggregateFailureMessage,
                    Map.of("aggregateAudit", normalizedAggregateAudit.toString())
            );
        }
    }

    private static void appendAggregateInteropAuditEvents(
            final Path aggregateAuditPath,
            final String timestamp,
            final String commandName,
            final Path entryPath,
            final InteropPolicy interopPolicy,
            final boolean interopTraceEnabled,
            final List<String> denylistPatterns,
            final List<String> discoveredInteropTargets,
            final String decision,
            final String code,
            final String message
    ) throws IOException {
        final Path parent = aggregateAuditPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final int totalTargets = discoveredInteropTargets.size();
        final int emittedTargets = Math.min(totalTargets, MAX_AGGREGATE_AUDIT_EVENTS);
        final int truncatedTargets = totalTargets - emittedTargets;
        final String outcome = "allow".equals(decision) ? "success" : "failure";
        final String denylist = String.join(",", denylistPatterns);
        final String entry = entryPath.toAbsolutePath().normalize().toString();

        final StringBuilder lines = new StringBuilder();
        final int lineCount = Math.max(1, emittedTargets);
        for (int index = 0; index < lineCount; index++) {
            final String target = emittedTargets == 0 ? "" : discoveredInteropTargets.get(index);
            lines.append("{");
            lines.append("\"schema\":\"tsj.interop.audit.v1\",");
            lines.append("\"ts\":\"").append(escapeJson(timestamp)).append("\",");
            lines.append("\"command\":\"").append(escapeJson(commandName)).append("\",");
            lines.append("\"entry\":\"").append(escapeJson(entry)).append("\",");
            lines.append("\"policy\":\"").append(escapeJson(interopPolicy.cliValue())).append("\",");
            lines.append("\"trace\":\"").append(Boolean.toString(interopTraceEnabled)).append("\",");
            lines.append("\"decision\":\"").append(escapeJson(decision)).append("\",");
            lines.append("\"outcome\":\"").append(escapeJson(outcome)).append("\",");
            lines.append("\"code\":\"").append(escapeJson(code)).append("\",");
            lines.append("\"message\":\"").append(escapeJson(message)).append("\",");
            lines.append("\"target\":\"").append(escapeJson(target)).append("\",");
            lines.append("\"targetIndex\":\"").append(index).append("\",");
            lines.append("\"targetCount\":\"").append(totalTargets).append("\",");
            lines.append("\"truncatedCount\":\"").append(Math.max(0, truncatedTargets)).append("\",");
            lines.append("\"denylist\":\"").append(escapeJson(denylist)).append("\"");
            lines.append("}");
            lines.append(System.lineSeparator());
        }
        Files.writeString(
                aggregateAuditPath,
                lines.toString(),
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private static String toLocalInteropAuditJson(
            final String timestamp,
            final String commandName,
            final Path entryPath,
            final InteropPolicy interopPolicy,
            final boolean interopTraceEnabled,
            final List<String> denylistPatterns,
            final List<String> discoveredInteropTargets,
            final String decision,
            final String code,
            final String message
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"ts\":\"").append(escapeJson(timestamp)).append("\",");
        builder.append("\"command\":\"").append(escapeJson(commandName)).append("\",");
        builder.append("\"entry\":\"")
                .append(escapeJson(entryPath.toAbsolutePath().normalize().toString()))
                .append("\",");
        builder.append("\"policy\":\"").append(escapeJson(interopPolicy.cliValue())).append("\",");
        builder.append("\"trace\":\"").append(Boolean.toString(interopTraceEnabled)).append("\",");
        builder.append("\"decision\":\"").append(escapeJson(decision)).append("\",");
        builder.append("\"code\":\"").append(escapeJson(code)).append("\",");
        builder.append("\"message\":\"").append(escapeJson(message)).append("\",");
        builder.append("\"targets\":\"")
                .append(escapeJson(String.join(",", discoveredInteropTargets)))
                .append("\",");
        builder.append("\"denylist\":\"")
                .append(escapeJson(String.join(",", denylistPatterns)))
                .append("\"");
        builder.append("}");
        builder.append(System.lineSeparator());
        return builder.toString();
    }

    private static void appendAuditLine(
            final Path logPath,
            final String line,
            final String failureCode,
            final String failurePrefix
    ) {
        final Path parent = logPath.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    logPath,
                    line,
                    java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    failureCode,
                    failurePrefix + ioException.getMessage(),
                    Map.of("auditLog", logPath.toString())
            );
        }
    }

    private static List<String> parseInteropDenylistPatterns(final String rawValue) {
        final String[] segments = rawValue.split(",");
        final List<String> patterns = new ArrayList<>();
        for (String segment : segments) {
            final String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            patterns.add(trimmed);
        }
        if (patterns.isEmpty()) {
            throw CliFailure.usage(
                    "TSJ-CLI-011",
                    "Interop denylist must contain at least one non-empty pattern."
            );
        }
        return List.copyOf(patterns);
    }

    private static List<String> parseInteropRoleValues(final String rawValue) {
        final String[] segments = rawValue.split(",");
        final LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (String segment : segments) {
            final String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            roles.add(trimmed);
        }
        if (roles.isEmpty()) {
            throw CliFailure.usage(
                    "TSJ-CLI-016",
                    "Interop role list must contain at least one non-empty role."
            );
        }
        return List.copyOf(roles);
    }

    private static String parseInteropApprovalToken(final String rawValue) {
        final String token = rawValue == null ? "" : rawValue.trim();
        if (token.isEmpty()) {
            throw CliFailure.usage(
                    "TSJ-CLI-016",
                    "Interop approval token must not be empty."
            );
        }
        return token;
    }

    private static List<ClasspathInput> parseClasspathOptionEntries(final String rawValue) {
        final List<String> segments = splitClasspathSegments(rawValue);
        final List<ClasspathInput> inputs = new ArrayList<>();
        for (String segment : segments) {
            inputs.add(new ClasspathInput(parseClasspathPath(segment), false));
        }
        if (inputs.isEmpty()) {
            throw CliFailure.usage(
                    "TSJ-CLI-011",
                    "Classpath entry list must not be empty."
            );
        }
        return List.copyOf(inputs);
    }

    private static List<String> splitClasspathSegments(final String rawValue) {
        final String trimmedRaw = rawValue == null ? "" : rawValue.trim();
        if (trimmedRaw.isEmpty()) {
            return List.of();
        }
        if (File.pathSeparatorChar != ':') {
            final String[] rawSegments = trimmedRaw.split(Pattern.quote(File.pathSeparator));
            final List<String> segments = new ArrayList<>();
            for (String rawSegment : rawSegments) {
                final String trimmed = rawSegment.trim();
                if (!trimmed.isEmpty()) {
                    segments.add(trimmed);
                }
            }
            return List.copyOf(segments);
        }

        final String[] rawSegments = trimmedRaw.split(Pattern.quote(File.pathSeparator), -1);
        final List<String> merged = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            final String trimmed = rawSegment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!merged.isEmpty() && "jrt".equals(merged.get(merged.size() - 1))) {
                merged.set(merged.size() - 1, "jrt:" + trimmed);
                continue;
            }
            merged.add(trimmed);
        }
        return List.copyOf(merged);
    }

    private static Path parseClasspathPath(final String rawValue) {
        final String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.startsWith("jrt:/")) {
            try {
                return Path.of(URI.create(trimmed));
            } catch (final RuntimeException runtimeException) {
                throw CliFailure.usage(
                        "TSJ-CLI-011",
                        "Invalid jrt classpath entry: " + trimmed
                );
            }
        }
        return Path.of(trimmed);
    }

    private static String serializeClasspathEntry(final Path classpathEntry) {
        if ("jrt".equalsIgnoreCase(classpathEntry.getFileSystem().provider().getScheme())) {
            return classpathEntry.toUri().toString();
        }
        return classpathEntry.toString();
    }

    private static ClasspathResolution normalizeClasspathInputs(
            final List<ClasspathInput> classpathInputs,
            final ClasspathUsageMode usageMode
    ) {
        final LinkedHashSet<Path> normalized = new LinkedHashSet<>();
        final Map<String, JarVersionEntry> legacyJarVersionsByArtifact = new LinkedHashMap<>();
        final Map<Path, MavenJarMetadata> mavenJarMetadataByPath = new LinkedHashMap<>();
        for (ClasspathInput classpathInput : classpathInputs) {
            final Path value = classpathInput.value().toAbsolutePath().normalize();
            if (!Files.exists(value)) {
                throw CliFailure.usage(
                        "TSJ-CLI-011",
                        "Classpath entry does not exist: " + value
                );
            }
            if (classpathInput.jarOnly() && !Files.isRegularFile(value)) {
                throw CliFailure.usage(
                        "TSJ-CLI-011",
                        "JAR input must be a regular file: " + value
                );
            }
            normalized.add(value);
        }
        final List<Path> normalizedEntries = List.copyOf(normalized);
        for (Path path : normalizedEntries) {
            final MavenJarMetadata metadata = readMavenJarMetadata(path);
            if (metadata != null) {
                mavenJarMetadataByPath.put(path, metadata);
                continue;
            }
            final JarVersionEntry jarVersionEntry = parseJarVersionEntry(path);
            if (jarVersionEntry == null) {
                continue;
            }
            final JarVersionEntry existing = legacyJarVersionsByArtifact.putIfAbsent(
                    jarVersionEntry.artifactKey(),
                    jarVersionEntry
            );
            if (existing != null && !existing.version().equals(jarVersionEntry.version())) {
                throw CliFailure.usage(
                        "TSJ-CLASSPATH-CONFLICT",
                        "Classpath version conflict for artifact `"
                                + jarVersionEntry.artifactName()
                                + "`: `"
                                + existing.version()
                                + "` ("
                                + existing.path()
                                + ") vs `"
                                + jarVersionEntry.version()
                                + "` ("
                                + jarVersionEntry.path()
                                + "). Keep exactly one version on classpath."
                );
            }
        }
        if (mavenJarMetadataByPath.isEmpty()) {
            return new ClasspathResolution(normalizedEntries, List.of(), usageMode, List.of());
        }
        return mediateClasspathGraph(normalizedEntries, mavenJarMetadataByPath, usageMode);
    }

    private static ClasspathResolution mediateClasspathGraph(
            final List<Path> normalizedEntries,
            final Map<Path, MavenJarMetadata> mavenJarMetadataByPath,
            final ClasspathUsageMode usageMode
    ) {
        final Map<String, List<MavenJarMetadata>> metadataByArtifactKey = new LinkedHashMap<>();
        final List<MavenJarMetadata> orderedMetadata = new ArrayList<>();
        for (Path entry : normalizedEntries) {
            final MavenJarMetadata metadata = mavenJarMetadataByPath.get(entry);
            if (metadata == null) {
                continue;
            }
            orderedMetadata.add(metadata);
            metadataByArtifactKey.computeIfAbsent(
                metadata.coordinate().artifactKey(),
                ignored -> new ArrayList<>()
            ).add(metadata);
        }
        if (orderedMetadata.isEmpty()) {
            return new ClasspathResolution(normalizedEntries, List.of(), usageMode, List.of());
        }

        final Map<Path, Integer> incomingEdgeCountByPath = new LinkedHashMap<>();
        for (MavenJarMetadata metadata : orderedMetadata) {
            incomingEdgeCountByPath.put(metadata.path(), 0);
        }
        for (MavenJarMetadata metadata : orderedMetadata) {
            for (MavenDependencyCoordinate dependency : metadata.dependencies()) {
                final List<MavenJarMetadata> matches = resolveDependencyCandidates(dependency, metadataByArtifactKey);
                for (MavenJarMetadata match : matches) {
                    incomingEdgeCountByPath.computeIfPresent(match.path(), (ignored, count) -> count + 1);
                }
            }
        }

        final List<MavenJarMetadata> roots = new ArrayList<>();
        for (MavenJarMetadata metadata : orderedMetadata) {
            if (incomingEdgeCountByPath.getOrDefault(metadata.path(), 0) == 0) {
                roots.add(metadata);
            }
        }
        if (roots.isEmpty()) {
            roots.addAll(orderedMetadata);
        }

        final Map<String, List<DependencyCandidate>> candidatesByArtifact = new LinkedHashMap<>();
        final Map<String, ClasspathScopeExclusion> scopeExclusionsByKey = new LinkedHashMap<>();
        final java.util.Deque<DependencyTraversalState> queue = new java.util.ArrayDeque<>();
        for (int rootIndex = 0; rootIndex < roots.size(); rootIndex++) {
            queue.addLast(new DependencyTraversalState(roots.get(rootIndex), rootIndex, 0));
        }
        final Map<String, Integer> bestDepthByRootAndPath = new LinkedHashMap<>();
        int discoveryOrder = 0;
        while (!queue.isEmpty()) {
            final DependencyTraversalState current = queue.removeFirst();
            final String visitKey = current.rootIndex() + "|" + current.jar().path().toString();
            final Integer knownDepth = bestDepthByRootAndPath.get(visitKey);
            if (knownDepth != null && knownDepth <= current.depth()) {
                continue;
            }
            bestDepthByRootAndPath.put(visitKey, current.depth());
            final DependencyCandidate candidate = new DependencyCandidate(
                    current.jar(),
                    current.rootIndex(),
                    current.depth(),
                    discoveryOrder
            );
            discoveryOrder++;
            candidatesByArtifact.computeIfAbsent(
                    current.jar().coordinate().artifactKey(),
                    ignored -> new ArrayList<>()
            ).add(candidate);
            for (MavenDependencyCoordinate dependency : current.jar().dependencies()) {
                final List<MavenJarMetadata> matches = resolveDependencyCandidates(
                        current.jar(),
                        dependency,
                        metadataByArtifactKey,
                        usageMode,
                        scopeExclusionsByKey
                );
                for (MavenJarMetadata match : matches) {
                    queue.addLast(new DependencyTraversalState(match, current.rootIndex(), current.depth() + 1));
                }
            }
        }

        final Map<String, DependencyCandidate> winnersByArtifact = new LinkedHashMap<>();
        final List<ClasspathMediationDecision> decisions = new ArrayList<>();
        for (Map.Entry<String, List<DependencyCandidate>> entry : candidatesByArtifact.entrySet()) {
            final String artifact = entry.getKey();
            final List<DependencyCandidate> candidates = entry.getValue();
            final DependencyCandidate winner = selectWinningCandidate(candidates);
            if (winner == null) {
                continue;
            }
            winnersByArtifact.put(artifact, winner);
            final Map<String, DependencyCandidate> bestByVersion = new LinkedHashMap<>();
            for (DependencyCandidate candidate : candidates) {
                final String version = candidate.jar().coordinate().version();
                final DependencyCandidate knownBest = bestByVersion.get(version);
                if (knownBest == null || compareCandidates(candidate, knownBest) < 0) {
                    bestByVersion.put(version, candidate);
                }
            }
            if (bestByVersion.size() <= 1) {
                continue;
            }
            for (Map.Entry<String, DependencyCandidate> versionEntry : bestByVersion.entrySet()) {
                final String rejectedVersion = versionEntry.getKey();
                final DependencyCandidate rejected = versionEntry.getValue();
                if (winner.jar().coordinate().version().equals(rejectedVersion)) {
                    continue;
                }
                decisions.add(
                        new ClasspathMediationDecision(
                                artifact,
                                winner.jar().coordinate().version(),
                                winner.jar().path(),
                                rejectedVersion,
                                rejected.jar().path(),
                                classifyMediationRule(winner, rejected)
                        )
                );
            }
        }

        final Set<Path> winningPaths = new LinkedHashSet<>();
        for (DependencyCandidate winner : winnersByArtifact.values()) {
            winningPaths.add(winner.jar().path());
        }

        final List<Path> mediatedEntries = new ArrayList<>();
        for (Path entry : normalizedEntries) {
            if (!mavenJarMetadataByPath.containsKey(entry) || winningPaths.contains(entry)) {
                mediatedEntries.add(entry);
            }
        }
        return new ClasspathResolution(
                List.copyOf(mediatedEntries),
                List.copyOf(decisions),
                usageMode,
                List.copyOf(scopeExclusionsByKey.values())
        );
    }

    private static List<MavenJarMetadata> resolveDependencyCandidates(
            final MavenDependencyCoordinate dependency,
            final Map<String, List<MavenJarMetadata>> metadataByArtifactKey
    ) {
        final List<MavenJarMetadata> candidates = metadataByArtifactKey.get(dependency.artifactKey());
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        final List<MavenJarMetadata> filtered = new ArrayList<>();
        for (MavenJarMetadata candidate : candidates) {
            if (dependency.version() == null || dependency.version().equals(candidate.coordinate().version())) {
                filtered.add(candidate);
            }
        }
        filtered.sort((left, right) -> left.path().toString().compareTo(right.path().toString()));
        return List.copyOf(filtered);
    }

    private static List<MavenJarMetadata> resolveDependencyCandidates(
            final MavenJarMetadata owner,
            final MavenDependencyCoordinate dependency,
            final Map<String, List<MavenJarMetadata>> metadataByArtifactKey,
            final ClasspathUsageMode usageMode,
            final Map<String, ClasspathScopeExclusion> scopeExclusionsByKey
    ) {
        final List<MavenJarMetadata> candidates = resolveDependencyCandidates(dependency, metadataByArtifactKey);
        if (candidates.isEmpty()) {
            return List.of();
        }
        final String dependencyScope = dependency.normalizedScope();
        if (usageMode.allows(dependencyScope)) {
            return candidates;
        }
        for (MavenJarMetadata candidate : candidates) {
            registerScopeExclusion(
                    owner,
                    dependency,
                    candidate,
                    dependencyScope,
                    usageMode,
                    scopeExclusionsByKey
            );
        }
        return List.of();
    }

    private static void registerScopeExclusion(
            final MavenJarMetadata owner,
            final MavenDependencyCoordinate dependency,
            final MavenJarMetadata candidate,
            final String dependencyScope,
            final ClasspathUsageMode usageMode,
            final Map<String, ClasspathScopeExclusion> scopeExclusionsByKey
    ) {
        final Path excludedPath = candidate.path().toAbsolutePath().normalize();
        final String key = owner.path().toString()
                + "|"
                + dependency.artifactKey()
                + "|"
                + dependencyScope
                + "|"
                + usageMode.cliValue()
                + "|"
                + excludedPath;
        scopeExclusionsByKey.computeIfAbsent(
                key,
                ignored -> new ClasspathScopeExclusion(
                        owner.coordinate().artifactKey(),
                        owner.coordinate().version(),
                        dependency.artifactKey(),
                        candidate.coordinate().version(),
                        dependencyScope,
                        usageMode.cliValue(),
                        excludedPath
                )
        );
    }

    private static DependencyCandidate selectWinningCandidate(final List<DependencyCandidate> candidates) {
        DependencyCandidate winner = null;
        for (DependencyCandidate candidate : candidates) {
            if (winner == null || compareCandidates(candidate, winner) < 0) {
                winner = candidate;
            }
        }
        return winner;
    }

    private static int compareCandidates(final DependencyCandidate left, final DependencyCandidate right) {
        final int byDepth = Integer.compare(left.depth(), right.depth());
        if (byDepth != 0) {
            return byDepth;
        }
        final int byRoot = Integer.compare(left.rootIndex(), right.rootIndex());
        if (byRoot != 0) {
            return byRoot;
        }
        final int byDiscovery = Integer.compare(left.discoveryOrder(), right.discoveryOrder());
        if (byDiscovery != 0) {
            return byDiscovery;
        }
        return left.jar().path().toString().compareTo(right.jar().path().toString());
    }

    private static String classifyMediationRule(
            final DependencyCandidate winner,
            final DependencyCandidate rejected
    ) {
        if (winner.depth() < rejected.depth()) {
            return "nearest";
        }
        if (winner.rootIndex() < rejected.rootIndex()) {
            return "root-order";
        }
        if (winner.discoveryOrder() < rejected.discoveryOrder()) {
            return "discovery-order";
        }
        return "deterministic";
    }

    private static MavenJarMetadata readMavenJarMetadata(final Path value) {
        if (!Files.isRegularFile(value)) {
            return null;
        }
        final String fileName = value.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".jar")) {
            return null;
        }
        try (JarFile jarFile = new JarFile(value.toFile())) {
            final String propertiesPath = resolveMavenDescriptorPath(jarFile, "/pom.properties");
            if (propertiesPath == null) {
                return null;
            }
            final Properties properties = new Properties();
            final JarEntry propertiesEntry = jarFile.getJarEntry(propertiesPath);
            if (propertiesEntry == null) {
                return null;
            }
            try (InputStream inputStream = jarFile.getInputStream(propertiesEntry)) {
                properties.load(inputStream);
            }
            final String groupId = normalizeMetadataToken(properties.getProperty("groupId"));
            final String artifactId = normalizeMetadataToken(properties.getProperty("artifactId"));
            final String version = normalizeMetadataToken(properties.getProperty("version"));
            if (groupId == null || artifactId == null || version == null) {
                return null;
            }
            final MavenCoordinate coordinate = new MavenCoordinate(groupId, artifactId, version);
            final List<MavenDependencyCoordinate> dependencies = readPomDependencies(jarFile, coordinate);
            return new MavenJarMetadata(value, coordinate, dependencies);
        } catch (final IOException ioException) {
            return null;
        }
    }

    private static String resolveMavenDescriptorPath(final JarFile jarFile, final String suffix) {
        final List<String> matches = new ArrayList<>();
        final java.util.Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            final String name = entry.getName();
            if (name.startsWith("META-INF/maven/") && name.endsWith(suffix)) {
                matches.add(name);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(String::compareTo);
        return matches.get(0);
    }

    private static String normalizeMetadataToken(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<MavenDependencyCoordinate> readPomDependencies(
            final JarFile jarFile,
            final MavenCoordinate coordinate
    ) {
        final String coordinatePom = "META-INF/maven/"
                + coordinate.groupId()
                + "/"
                + coordinate.artifactId()
                + "/pom.xml";
        JarEntry pomEntry = jarFile.getJarEntry(coordinatePom);
        if (pomEntry == null) {
            final String fallbackPath = resolveMavenDescriptorPath(jarFile, "/pom.xml");
            if (fallbackPath == null) {
                return List.of();
            }
            pomEntry = jarFile.getJarEntry(fallbackPath);
            if (pomEntry == null) {
                return List.of();
            }
        }
        try (InputStream inputStream = jarFile.getInputStream(pomEntry)) {
            return parsePomDependencies(inputStream);
        } catch (final Exception exception) {
            return List.of();
        }
    }

    private static List<MavenDependencyCoordinate> parsePomDependencies(final InputStream inputStream) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final Document document = factory.newDocumentBuilder().parse(inputStream);
        final NodeList dependencyNodes = document.getElementsByTagName("dependency");
        final List<MavenDependencyCoordinate> dependencies = new ArrayList<>();
        for (int index = 0; index < dependencyNodes.getLength(); index++) {
            if (!(dependencyNodes.item(index) instanceof Element dependency)) {
                continue;
            }
            final String groupId = normalizeMetadataToken(extractDependencyValue(dependency, "groupId"));
            final String artifactId = normalizeMetadataToken(extractDependencyValue(dependency, "artifactId"));
            String version = normalizeMetadataToken(extractDependencyValue(dependency, "version"));
            String scope = normalizeMetadataToken(extractDependencyValue(dependency, "scope"));
            if (groupId == null || artifactId == null) {
                continue;
            }
            if (version != null && version.contains("${")) {
                version = null;
            }
            if (scope != null && scope.contains("${")) {
                scope = null;
            }
            dependencies.add(new MavenDependencyCoordinate(groupId, artifactId, version, scope));
        }
        return List.copyOf(dependencies);
    }

    private static String extractDependencyValue(final Element dependency, final String elementName) {
        final NodeList values = dependency.getElementsByTagName(elementName);
        if (values.getLength() == 0) {
            return null;
        }
        return values.item(0).getTextContent();
    }

    private static JarVersionEntry parseJarVersionEntry(final Path value) {
        if (!Files.isRegularFile(value)) {
            return null;
        }
        final String fileName = value.getFileName().toString();
        final String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar")) {
            return null;
        }
        final java.util.regex.Matcher matcher = JAR_VERSION_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        final String artifactName = matcher.group(1);
        final String version = matcher.group(2);
        final String artifactKey = artifactName.toLowerCase(Locale.ROOT);
        return new JarVersionEntry(artifactName, artifactKey, version, value);
    }

    private static List<Path> normalizeResourceDirectories(final List<Path> resourceDirectories) {
        final LinkedHashSet<Path> normalized = new LinkedHashSet<>();
        for (Path resourceDirectory : resourceDirectories) {
            final Path value = resourceDirectory.toAbsolutePath().normalize();
            if (!Files.exists(value) || !Files.isDirectory(value)) {
                throw CliFailure.runtime(
                        "TSJ-SPRING-PACKAGE",
                        "Resource directory does not exist: " + value,
                        Map.of(
                                "resourceDir", value.toString(),
                                "stage", "package",
                                "failureKind", "resource"
                        )
                );
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private static String classifyStartupStage(final String code) {
        if (code != null && code.startsWith("TSJ-INTEROP")) {
            return "bridge";
        }
        return "compile";
    }

    private static Map<String, String> mergeFailureContext(
            final Map<String, String> context,
            final String key,
            final String value
    ) {
        final Map<String, String> merged = new LinkedHashMap<>();
        if (context != null) {
            merged.putAll(context);
        }
        merged.put(key, value);
        return Map.copyOf(merged);
    }

    private static Path normalizeInteropSpecPath(final Path specPath) {
        final Path normalized = specPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw CliFailure.usage(
                    "TSJ-CLI-012",
                    "Interop spec file not found: " + normalized
            );
        }
        return normalized;
    }

    private static JvmOptimizationOptions parseOptimizationToggle(final String token) {
        if (OPTION_OPTIMIZE.equals(token)) {
            return JvmOptimizationOptions.defaults();
        }
        if (OPTION_NO_OPTIMIZE.equals(token)) {
            return JvmOptimizationOptions.disabled();
        }
        return null;
    }

    private static void emitTsStackTrace(
            final PrintStream stderr,
            final JvmCompiledArtifact executable,
            final Throwable throwable
    ) {
        if (throwable == null) {
            return;
        }
        final Map<Integer, TsSourceFrame> sourceMap = readSourceMap(executable.sourceMapFile());
        if (sourceMap.isEmpty()) {
            return;
        }

        final List<RenderedCause> renderedCauses = new ArrayList<>();
        Throwable current = throwable;
        int causeIndex = 0;
        while (current != null) {
            final List<String> renderedFrames = renderMappedCauseFrames(executable, sourceMap, current);
            renderedCauses.add(new RenderedCause(causeIndex, describeThrowable(current), renderedFrames));
            current = current.getCause();
            causeIndex++;
        }
        if (renderedCauses.stream().allMatch(cause -> cause.frames().isEmpty())) {
            return;
        }

        stderr.println("TSJ stack trace (TypeScript):");
        for (RenderedCause cause : renderedCauses) {
            stderr.println("Cause[" + cause.index() + "]: " + cause.description());
            if (cause.frames().isEmpty()) {
                stderr.println("  (no mapped TSJ frames)");
                continue;
            }
            for (String frame : cause.frames()) {
                stderr.println("  " + frame);
            }
        }
    }

    private static List<String> renderMappedCauseFrames(
            final JvmCompiledArtifact executable,
            final Map<Integer, TsSourceFrame> sourceMap,
            final Throwable throwable
    ) {
        final List<String> renderedFrames = new ArrayList<>();
        final Set<String> seenFrames = new LinkedHashSet<>();
        boolean inAsyncContinuationBlock = false;
        for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
            if (!executable.className().equals(stackTraceElement.getClassName())) {
                continue;
            }
            final TsSourceFrame frame = sourceMap.get(stackTraceElement.getLineNumber());
            if (frame == null) {
                continue;
            }
            final String methodName = stackTraceElement.getMethodName();
            final String frameKey = methodName
                    + "@"
                    + frame.sourceFile()
                    + ":"
                    + frame.line()
                    + ":"
                    + frame.column();
            if (!seenFrames.add(frameKey)) {
                continue;
            }
            final boolean asyncContinuation = isAsyncContinuationMethod(methodName);
            if (asyncContinuation && !inAsyncContinuationBlock) {
                renderedFrames.add("--- async continuation ---");
            }
            inAsyncContinuationBlock = asyncContinuation;
            renderedFrames.add(
                    "at "
                            + frame.sourceFile()
                            + ":"
                            + frame.line()
                            + ":"
                            + frame.column()
                            + " [method="
                            + methodName
                            + "]"
                            + (asyncContinuation ? " [async-continuation]" : "")
            );
        }
        return List.copyOf(renderedFrames);
    }

    private static void emitUnhandledRejectionWithTsStackTrace(
            final PrintStream stderr,
            final JvmCompiledArtifact executable,
            final Object reason
    ) {
        stderr.println("TSJ-UNHANDLED-REJECTION: " + TsjRuntime.toDisplayString(reason));
        if (reason instanceof Throwable throwable) {
            emitTsStackTrace(stderr, executable, throwable);
        }
    }

    private static boolean isAsyncContinuationMethod(final String methodName) {
        return methodName.startsWith("lambda$")
                || methodName.contains("$async")
                || methodName.startsWith("async");
    }

    private static String describeThrowable(final Throwable throwable) {
        final String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }

    private static Map<Integer, TsSourceFrame> readSourceMap(final Path sourceMapFile) {
        if (!Files.exists(sourceMapFile) || !Files.isRegularFile(sourceMapFile)) {
            return Map.of();
        }
        final String raw;
        try {
            raw = Files.readString(sourceMapFile);
        } catch (final IOException ioException) {
            return Map.of();
        }

        final String[] lines = raw.replace("\r\n", "\n").split("\n", -1);
        final Map<Integer, TsSourceFrame> mapping = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("TSJ-SOURCE-MAP")) {
                continue;
            }
            final String[] parts = line.split("\t", -1);
            if (parts.length != 4) {
                continue;
            }
            try {
                final int javaLine = Integer.parseInt(parts[0]);
                final String sourceFile = unescapeSourceMapValue(parts[1]);
                final int sourceLine = Integer.parseInt(parts[2]);
                final int sourceColumn = Integer.parseInt(parts[3]);
                mapping.put(javaLine, new TsSourceFrame(sourceFile, sourceLine, sourceColumn));
            } catch (final RuntimeException ignored) {
                // Skip malformed source map rows and keep best-effort mapping.
            }
        }
        return Map.copyOf(mapping);
    }

    private static String unescapeSourceMapValue(final String value) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                builder.append(current);
                continue;
            }
            final char next = value.charAt(index + 1);
            index++;
            switch (next) {
                case '\\' -> builder.append('\\');
                case 't' -> builder.append('\t');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                default -> {
                    builder.append('\\');
                    builder.append(next);
                }
            }
        }
        return builder.toString();
    }

    private static Map<String, String> backendFailureContext(
            final Path entryPath,
            final JvmCompilationException compilationException
    ) {
        final Map<String, String> context = new LinkedHashMap<>();
        context.put("entry", entryPath.toString());
        if (compilationException.sourceFile() != null && !compilationException.sourceFile().isBlank()) {
            context.put("file", compilationException.sourceFile());
        }
        if (compilationException.line() != null) {
            context.put("line", Integer.toString(compilationException.line()));
        }
        if (compilationException.column() != null) {
            context.put("column", Integer.toString(compilationException.column()));
        }
        if (compilationException.featureId() != null && !compilationException.featureId().isBlank()) {
            context.put("featureId", compilationException.featureId());
        }
        if (compilationException.guidance() != null && !compilationException.guidance().isBlank()) {
            context.put("guidance", compilationException.guidance());
        }
        return Map.copyOf(context);
    }

    private static void emitDiagnostic(
            final PrintStream stream,
            final String level,
            final String code,
            final String message,
            final Map<String, String> context
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"level\":\"").append(escapeJson(level)).append("\",");
        builder.append("\"code\":\"").append(escapeJson(code)).append("\",");
        builder.append("\"message\":\"").append(escapeJson(message)).append("\"");
        if (context != null && !context.isEmpty()) {
            builder.append(",\"context\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : new TreeMap<>(context).entrySet()) {
                if (!first) {
                    builder.append(",");
                }
                first = false;
                builder.append("\"")
                        .append(escapeJson(entry.getKey()))
                        .append("\":\"")
                        .append(escapeJson(entry.getValue()))
                        .append("\"");
            }
            builder.append("}");
        }
        builder.append("}");
        stream.println(builder);
    }

    private static String escapeJson(final String value) {
        final String safe = value == null ? "" : value;
        return safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String formatDouble(final double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private record ParsedOutOption(Path outDir) {
    }

    private record ClasspathInput(Path value, boolean jarOnly) {
    }

    private record ClasspathResolution(
            List<Path> entries,
            List<ClasspathMediationDecision> decisions,
            ClasspathUsageMode usageMode,
            List<ClasspathScopeExclusion> scopeExclusions
    ) {
    }

    private record ClasspathMediationDecision(
            String artifact,
            String selectedVersion,
            Path selectedPath,
            String rejectedVersion,
            Path rejectedPath,
            String rule
    ) {
    }

    private record JarVersionEntry(
            String artifactName,
            String artifactKey,
            String version,
            Path path
    ) {
    }

    private record MavenCoordinate(String groupId, String artifactId, String version) {
        private String artifactKey() {
            return groupId + ":" + artifactId;
        }
    }

    private record MavenDependencyCoordinate(String groupId, String artifactId, String version, String scope) {
        private String artifactKey() {
            return groupId + ":" + artifactId;
        }

        private String normalizedScope() {
            if (scope == null || scope.isBlank()) {
                return "compile";
            }
            return scope.trim().toLowerCase(Locale.ROOT);
        }
    }

    private record MavenJarMetadata(
            Path path,
            MavenCoordinate coordinate,
            List<MavenDependencyCoordinate> dependencies
    ) {
    }

    private record DependencyTraversalState(MavenJarMetadata jar, int rootIndex, int depth) {
    }

    private record DependencyCandidate(
            MavenJarMetadata jar,
            int rootIndex,
            int depth,
            int discoveryOrder
    ) {
    }

    private record ClasspathScopeExclusion(
            String ownerArtifact,
            String ownerVersion,
            String dependencyArtifact,
            String dependencyVersion,
            String scope,
            String usage,
            Path excludedPath
    ) {
    }

    private record InteropPolicyResolution(
            InteropPolicy interopPolicy,
            boolean interopRiskAcknowledged,
            String source
    ) {
    }

    private record FleetPolicySource(
            Path path,
            InteropPolicy interopPolicy,
            Boolean interopRiskAcknowledged,
            List<String> interopRbacRoles,
            List<String> interopRbacRequiredRoles,
            List<String> interopRbacSensitiveTargets,
            List<String> interopRbacSensitiveRequiredRoles,
            Boolean interopApprovalRequired,
            String interopApprovalToken,
            List<String> interopApprovalTargets
    ) {
    }

    private record InteropAuthorizationResolution(
            List<String> actorRoles,
            List<String> requiredRoles,
            List<String> sensitiveTargets,
            List<String> sensitiveRequiredRoles,
            boolean approvalRequired,
            String expectedApprovalToken,
            List<String> approvalTargets,
            String providedApprovalToken,
            String source
    ) {
    }

    private enum ClasspathUsageMode {
        COMPILE("compile", List.of("compile", "runtime", "provided")),
        RUNTIME("runtime", List.of("compile", "runtime"));

        private final String cliValue;
        private final List<String> allowedScopes;

        ClasspathUsageMode(final String cliValue, final List<String> allowedScopes) {
            this.cliValue = cliValue;
            this.allowedScopes = List.copyOf(allowedScopes);
        }

        private String cliValue() {
            return cliValue;
        }

        private List<String> allowedScopes() {
            return allowedScopes;
        }

        private boolean allows(final String scope) {
            if (scope == null || scope.isBlank()) {
                return true;
            }
            final String normalized = scope.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "compile", "runtime", "provided", "test" -> allowedScopes.contains(normalized);
                default -> true;
            };
        }
    }

    private record CompileOptions(
            Path outDir,
            JvmOptimizationOptions optimizationOptions,
            List<Path> interopClasspathEntries,
            List<ClasspathMediationDecision> classpathMediationDecisions,
            ClasspathUsageMode classpathUsageMode,
            List<ClasspathScopeExclusion> classpathScopeExclusions,
            Path interopSpecPath,
            InteropPolicy interopPolicy,
            boolean interopRiskAcknowledged,
            String interopPolicySource,
            InteropAuthorizationResolution interopAuthorizationResolution,
            List<String> interopDenylistPatterns,
            Path interopAuditLogPath,
            Path interopAuditAggregatePath,
            boolean interopTraceEnabled
    ) {
    }

    private record RunOptions(
            Path outDir,
            boolean showTsStackTrace,
            JvmOptimizationOptions optimizationOptions,
            List<Path> interopClasspathEntries,
            List<ClasspathMediationDecision> classpathMediationDecisions,
            ClasspathUsageMode classpathUsageMode,
            List<ClasspathScopeExclusion> classpathScopeExclusions,
            Path interopSpecPath,
            InteropPolicy interopPolicy,
            boolean interopRiskAcknowledged,
            String interopPolicySource,
            InteropAuthorizationResolution interopAuthorizationResolution,
            List<String> interopDenylistPatterns,
            Path interopAuditLogPath,
            Path interopAuditAggregatePath,
            boolean interopTraceEnabled,
            JvmBytecodeRunner.ClassloaderIsolationMode classloaderIsolationMode
    ) {
    }

    private record SpringPackageOptions(
            Path outDir,
            JvmOptimizationOptions optimizationOptions,
            List<Path> interopClasspathEntries,
            List<ClasspathMediationDecision> classpathMediationDecisions,
            ClasspathUsageMode classpathUsageMode,
            List<ClasspathScopeExclusion> classpathScopeExclusions,
            Path interopSpecPath,
            InteropPolicy interopPolicy,
            boolean interopRiskAcknowledged,
            String interopPolicySource,
            InteropAuthorizationResolution interopAuthorizationResolution,
            List<String> interopDenylistPatterns,
            Path interopAuditLogPath,
            Path interopAuditAggregatePath,
            boolean interopTraceEnabled,
            List<Path> resourceDirectories,
            Path bootJarPath,
            boolean smokeRun,
            String smokeEndpointUrl,
            long smokeTimeoutMs,
            long smokePollMs
    ) {
    }

    private enum InteropPolicy {
        STRICT("strict"),
        BROAD("broad");

        private final String cliValue;

        InteropPolicy(final String cliValue) {
            this.cliValue = cliValue;
        }

        private String cliValue() {
            return cliValue;
        }
    }

    private record CompiledArtifact(
            Path entryPath,
            Path artifactPath,
            JvmCompiledArtifact jvmArtifact,
            ClasspathResolution classpathResolution,
            ClasspathSymbolIndexSummary classpathSymbolIndex,
            AutoInteropBridgeResult interopBridgeResult,
            TsjWebControllerResult tsjWebControllerResult,
            TsjSpringComponentResult tsjSpringComponentResult
    ) {
    }

    private record ClasspathSymbolIndexSummary(
            Path indexPath,
            int symbolCount,
            int duplicateCount,
            int mrJarWinnerCount,
            int mrJarBaseWinnerCount,
            int mrJarVersionedWinnerCount
    ) {
    }

    private record AutoInteropBridgeResult(
            boolean enabled,
            List<String> targets,
            boolean regenerated,
            int sourceCount,
            List<InteropBridgeArtifact.SelectedTargetIdentity> selectedTargets,
            List<InteropBridgeArtifact.UnresolvedTarget> unresolvedTargets
    ) {
        private static AutoInteropBridgeResult disabled() {
            return new AutoInteropBridgeResult(false, List.of(), false, 0, List.of(), List.of());
        }
    }

    private record TsjWebControllerResult(
            List<String> controllerClassNames,
            int sourceCount
    ) {
    }

    private record TsjSpringComponentResult(
            List<String> componentClassNames,
            int sourceCount
    ) {
    }

    private record SpringPackageResult(
            Path jarPath,
            int resourceFileCount,
            List<Path> resourceDirectories,
            int dependencyEntryCount,
            List<Path> dependencySources
    ) {
    }

    private record SpringSmokeResult(
            int exitCode,
            String outputPreview,
            String endpointUrl,
            String endpointPort,
            int endpointStatusCode,
            String endpointResponsePreview,
            long runtimeMs,
            String reproCommand
    ) {
    }

    private record EndpointProbeResult(
            boolean success,
            int statusCode,
            String responsePreview,
            String error
    ) {
        private static EndpointProbeResult unavailable(final String error) {
            return new EndpointProbeResult(false, 0, "", error == null ? "" : error);
        }
    }

    private record TsSourceFrame(String sourceFile, int line, int column) {
    }

    private record RenderedCause(int index, String description, List<String> frames) {
    }

    private static final class CliFailure extends RuntimeException {
        private final int exitCode;
        private final String code;
        private final Map<String, String> context;

        private CliFailure(
                final int exitCode,
                final String code,
                final String message,
                final Map<String, String> context
        ) {
            super(message);
            this.exitCode = exitCode;
            this.code = code;
            this.context = context;
        }

        private static CliFailure usage(final String code, final String message) {
            return new CliFailure(2, code, message, Map.of());
        }

        private static CliFailure runtime(
                final String code,
                final String message,
                final Map<String, String> context
        ) {
            return new CliFailure(1, code, message, context);
        }
    }
}
