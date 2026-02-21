package dev.tsj.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-36c dev-loop parity harness.
 */
final class TsjDevLoopParityHarness {
    private static final String REPORT_FILE = "tsj36c-dev-loop-parity.json";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");

    TsjDevLoopParityReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj36c-dev-loop-work");
        Files.createDirectories(workRoot);

        final Path entryFile = workRoot.resolve("src/main.ts");
        Files.createDirectories(entryFile.getParent());
        Files.writeString(entryFile, "console.log(\"devloop-v1\");\n", UTF_8);

        final Path resourcesDir = workRoot.resolve("resources");
        Files.createDirectories(resourcesDir.resolve("static"));
        Files.writeString(resourcesDir.resolve("application.properties"), "spring.application.name=tsj36c\n", UTF_8);
        Files.writeString(resourcesDir.resolve("static/hello.txt"), "hello\n", UTF_8);

        final List<TsjDevLoopParityReport.ScenarioResult> scenarios = new ArrayList<>();
        scenarios.add(runCompileScenario(workRoot.resolve("compile"), entryFile));
        scenarios.add(runRunScenario(workRoot.resolve("run"), entryFile, "devloop-v1"));
        scenarios.add(runSpringPackageScenario(workRoot.resolve("package"), entryFile, resourcesDir));
        scenarios.add(runSpringSmokeScenario(workRoot.resolve("smoke"), entryFile));

        Files.writeString(entryFile, "console.log(\"devloop-v2\");\n", UTF_8);
        scenarios.add(runIncrementalIterationScenario(workRoot.resolve("iterate"), entryFile));

        final boolean gatePassed = scenarios.stream().allMatch(TsjDevLoopParityReport.ScenarioResult::passed);
        final List<String> workflowHints = List.of(
                "tsj compile <entry.ts> --out <dir>",
                "tsj run <entry.ts> --out <dir>",
                "tsj spring-package <entry.ts> --out <dir> [--smoke-run]"
        );
        final List<String> nonGoals = List.of(
                "Continuous hot-reload process management.",
                "IDE plugin integrations beyond CLI workflow.",
                "Automatic dependency re-resolution beyond explicit command invocation."
        );

        final TsjDevLoopParityReport report = new TsjDevLoopParityReport(
                gatePassed,
                scenarios,
                workflowHints,
                nonGoals,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjDevLoopParityReport.ScenarioResult runCompileScenario(final Path outDir, final Path entryFile) {
        final CommandResult result = execute(
                "compile",
                entryFile.toString(),
                "--out",
                outDir.toString()
        );
        final boolean passed = result.exitCode() == 0
                && result.stdout().contains("\"code\":\"TSJ-COMPILE-SUCCESS\"")
                && result.stderr().isBlank();
        return scenario("compile", passed, result);
    }

    private TsjDevLoopParityReport.ScenarioResult runRunScenario(
            final Path outDir,
            final Path entryFile,
            final String expectedMarker
    ) {
        final CommandResult result = execute(
                "run",
                entryFile.toString(),
                "--out",
                outDir.toString()
        );
        final boolean passed = result.exitCode() == 0
                && result.stdout().contains(expectedMarker)
                && result.stdout().contains("\"code\":\"TSJ-RUN-SUCCESS\"")
                && result.stderr().isBlank();
        return scenario("run", passed, result);
    }

    private TsjDevLoopParityReport.ScenarioResult runSpringPackageScenario(
            final Path outDir,
            final Path entryFile,
            final Path resourcesDir
    ) {
        final CommandResult result = execute(
                "spring-package",
                entryFile.toString(),
                "--out",
                outDir.toString(),
                "--resource-dir",
                resourcesDir.toString()
        );
        final Path packagedJar = outDir.resolve("tsj-spring-app.jar");
        final boolean passed = result.exitCode() == 0
                && Files.exists(packagedJar)
                && result.stdout().contains("\"code\":\"TSJ-SPRING-PACKAGE-SUCCESS\"")
                && result.stderr().isBlank();
        return scenario("spring-package", passed, result);
    }

    private TsjDevLoopParityReport.ScenarioResult runSpringSmokeScenario(
            final Path outDir,
            final Path entryFile
    ) {
        final CommandResult result = execute(
                "spring-package",
                entryFile.toString(),
                "--out",
                outDir.toString(),
                "--smoke-run",
                "--smoke-endpoint-url",
                "stdout://devloop-v1"
        );
        final boolean passed = result.exitCode() == 0
                && result.stdout().contains("\"code\":\"TSJ-SPRING-SMOKE-SUCCESS\"")
                && result.stdout().contains("\"code\":\"TSJ-SPRING-SMOKE-ENDPOINT-SUCCESS\"")
                && result.stderr().isBlank();
        return scenario("spring-smoke", passed, result);
    }

    private TsjDevLoopParityReport.ScenarioResult runIncrementalIterationScenario(
            final Path outDir,
            final Path entryFile
    ) {
        final long started = System.nanoTime();
        final CommandResult compile = execute(
                "compile",
                entryFile.toString(),
                "--out",
                outDir.resolve("compile").toString()
        );
        final CommandResult run = execute(
                "run",
                entryFile.toString(),
                "--out",
                outDir.resolve("run").toString()
        );
        final long durationMs = (System.nanoTime() - started) / 1_000_000L;
        final boolean passed = compile.exitCode() == 0
                && run.exitCode() == 0
                && run.stdout().contains("devloop-v2")
                && compile.stderr().isBlank()
                && run.stderr().isBlank();
        final String notes = "compile=" + summarize(compile) + ";run=" + summarize(run);
        return new TsjDevLoopParityReport.ScenarioResult(
                "incremental-iteration",
                passed,
                durationMs,
                passed ? "" : selectDiagnosticCode(compile.stderr() + "\n" + run.stderr()),
                notes
        );
    }

    private static TsjDevLoopParityReport.ScenarioResult scenario(
            final String id,
            final boolean passed,
            final CommandResult result
    ) {
        return new TsjDevLoopParityReport.ScenarioResult(
                id,
                passed,
                result.durationMs(),
                passed ? "" : selectDiagnosticCode(result.stderr()),
                summarize(result)
        );
    }

    private static String summarize(final CommandResult result) {
        return "exit="
                + result.exitCode()
                + ",stdout="
                + trim(result.stdout(), 160)
                + ",stderr="
                + trim(result.stderr(), 160);
    }

    private static String selectDiagnosticCode(final String stderr) {
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(stderr == null ? "" : stderr);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String trim(final String text, final int maxChars) {
        final String safe = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, maxChars) + "...";
    }

    private static CommandResult execute(final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final long started = System.nanoTime();
        final int exitCode = TsjCli.execute(args, new PrintStream(stdout), new PrintStream(stderr));
        final long durationMs = (System.nanoTime() - started) / 1_000_000L;
        return new CommandResult(
                exitCode,
                stdout.toString(UTF_8),
                stderr.toString(UTF_8),
                durationMs
        );
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjDevLoopParityHarness.class
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

    private static void writeReport(final Path reportPath, final TsjDevLoopParityReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record CommandResult(int exitCode, String stdout, String stderr, long durationMs) {
    }
}
