package dev.tsj.cli;

import dev.tsj.cli.fixtures.FixtureHarness;
import dev.tsj.cli.fixtures.FixtureRunResult;
import dev.tsj.compiler.backend.jvm.BackendJvmModule;
import dev.tsj.compiler.backend.jvm.JvmBytecodeCompiler;
import dev.tsj.compiler.backend.jvm.JvmBytecodeRunner;
import dev.tsj.compiler.backend.jvm.JvmCompilationException;
import dev.tsj.compiler.backend.jvm.JvmCompiledArtifact;
import dev.tsj.compiler.frontend.FrontendModule;
import dev.tsj.compiler.ir.IrModule;
import dev.tsj.runtime.RuntimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/**
 * CLI bootstrap for TSJ-2 command skeleton.
 */
public final class TsjCli {
    private static final String COMMAND_COMPILE = "compile";
    private static final String COMMAND_RUN = "run";
    private static final String COMMAND_FIXTURES = "fixtures";
    private static final String OPTION_OUT = "--out";
    private static final String DEFAULT_RUN_OUT_DIR = ".tsj-build";
    private static final String ARTIFACT_FILE_NAME = "program.tsj.properties";

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
                        "Missing command. Expected `compile` or `run`."
                );
            }

            return switch (args[0]) {
                case COMMAND_COMPILE -> handleCompile(args, stdout);
                case COMMAND_RUN -> handleRun(args, stdout);
                case COMMAND_FIXTURES -> handleFixtures(args, stdout);
                default -> throw CliFailure.usage(
                        "TSJ-CLI-002",
                        "Unknown command `" + args[0] + "`. Expected `compile`, `run`, or `fixtures`."
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
                    "Missing input file. Usage: tsj compile <input.ts> --out <dir>"
            );
        }
        final Path entryPath = Path.of(args[1]);
        final ParsedOutOption parsedOut = parseOutOption(args, 2, true);
        final CompiledArtifact artifact = compileArtifact(entryPath, parsedOut.outDir);

        emitDiagnostic(
                stdout,
                "INFO",
                "TSJ-COMPILE-SUCCESS",
                "Compilation artifact generated.",
                Map.of(
                        "entry", artifact.entryPath.toString(),
                        "artifact", artifact.artifactPath.toString(),
                        "className", artifact.jvmArtifact.className(),
                        "classFile", artifact.jvmArtifact.classFile().toString(),
                        "outDir", parsedOut.outDir.toString()
                )
        );
        return 0;
    }

    private static int handleRun(final String[] args, final PrintStream stdout) {
        if (args.length < 2) {
            throw CliFailure.usage(
                    "TSJ-CLI-004",
                    "Missing entry file. Usage: tsj run <entry.ts> [--out <dir>]"
            );
        }
        final Path entryPath = Path.of(args[1]);
        final ParsedOutOption parsedOut = parseOutOption(args, 2, false);
        final Path outDir = parsedOut.outDir != null ? parsedOut.outDir : Path.of(DEFAULT_RUN_OUT_DIR);
        final CompiledArtifact artifact = compileArtifact(entryPath, outDir);
        executeArtifact(artifact, stdout);
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
        final List<FixtureRunResult> results;
        try {
            results = new FixtureHarness().runAll(fixturesRoot);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw CliFailure.runtime(
                    "TSJ-FIXTURE-001",
                    "Failed to load fixtures: " + illegalArgumentException.getMessage(),
                    Map.of("fixturesRoot", fixturesRoot.toString())
            );
        }

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
                                "nodeToTsjDiff", result.nodeToTsjDiff()
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
        return failed == 0 ? 0 : 1;
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

    private static CompiledArtifact compileArtifact(final Path entryPath, final Path outDir) {
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

        final JvmCompiledArtifact jvmArtifact;
        try {
            jvmArtifact = new JvmBytecodeCompiler().compile(entryPath, outDir);
        } catch (final JvmCompilationException compilationException) {
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(entryPath, compilationException)
            );
        }

        try {
            Files.createDirectories(outDir);
            final Path artifactPath = outDir.resolve(ARTIFACT_FILE_NAME);
            final Properties properties = new Properties();
            properties.setProperty("formatVersion", "0.1");
            properties.setProperty("entry", entryPath.toAbsolutePath().normalize().toString());
            properties.setProperty("compiledAt", Instant.now().toString());
            properties.setProperty("mainClass", jvmArtifact.className());
            properties.setProperty("classFile", jvmArtifact.classFile().toString());
            properties.setProperty("classesDir", jvmArtifact.outputDirectory().toString());
            properties.setProperty("frontendModule", FrontendModule.moduleName());
            properties.setProperty("irModule", IrModule.moduleName());
            properties.setProperty("backendModule", BackendJvmModule.moduleName());
            properties.setProperty("runtimeModule", RuntimeModule.moduleName());

            try (OutputStream outputStream = Files.newOutputStream(artifactPath)) {
                properties.store(outputStream, "TSJ compiled artifact");
            }
            return new CompiledArtifact(entryPath, artifactPath, jvmArtifact);
        } catch (final IOException ioException) {
            throw CliFailure.runtime(
                    "TSJ-COMPILE-500",
                    "Failed to create compilation artifact: " + ioException.getMessage(),
                    Map.of("entry", entryPath.toString(), "outDir", outDir.toString())
            );
        }
    }

    private static void executeArtifact(final CompiledArtifact artifact, final PrintStream stdout) {
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
        try {
            new JvmBytecodeRunner().run(executable, stdout);
        } catch (final JvmCompilationException compilationException) {
            throw CliFailure.runtime(
                    compilationException.code(),
                    compilationException.getMessage(),
                    backendFailureContext(artifact.entryPath, compilationException)
            );
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
        return new JvmCompiledArtifact(
                Path.of(entry).toAbsolutePath().normalize(),
                classesDir,
                className,
                classFile
        );
    }

    private static Map<String, String> backendFailureContext(
            final Path entryPath,
            final JvmCompilationException compilationException
    ) {
        final Map<String, String> context = new LinkedHashMap<>();
        context.put("entry", entryPath.toString());
        if (compilationException.line() != null) {
            context.put("line", Integer.toString(compilationException.line()));
        }
        if (compilationException.column() != null) {
            context.put("column", Integer.toString(compilationException.column()));
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

    private record ParsedOutOption(Path outDir) {
    }

    private record CompiledArtifact(Path entryPath, Path artifactPath, JvmCompiledArtifact jvmArtifact) {
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
