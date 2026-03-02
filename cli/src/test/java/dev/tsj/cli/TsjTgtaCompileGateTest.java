package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjTgtaCompileGateTest {
    private static final Map<String, String> KNOWN_FAILING_FIXTURES = Map.of();

    @TempDir
    Path tempDir;

    @Test
    void tgtaNonTsxFixturesCompileWithTsjCompileSuccess() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path tgtaOkRoot = repoRoot.resolve("examples").resolve("tgta").resolve("src").resolve("ok");
        assertTrue(Files.isDirectory(tgtaOkRoot), "Missing TGTA ok fixture directory: " + tgtaOkRoot);

        final List<Path> fixtures;
        try (Stream<Path> paths = Files.list(tgtaOkRoot)) {
            fixtures = paths
                    .filter(Files::isRegularFile)
                    .filter(TsjTgtaCompileGateTest::isNonTsxTypeScriptFixture)
                    .filter(path -> !KNOWN_FAILING_FIXTURES.containsKey(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        assertFalse(fixtures.isEmpty(), "No TGTA TypeScript fixtures found in " + tgtaOkRoot);
        final List<String> failures = new ArrayList<>();
        for (final Path fixture : fixtures) {
            final Path outDir = tempDir.resolve("tgta-" + fixture.getFileName().toString().replace('.', '_'));
            final CliInvocationResult result = invokeCompile(fixture, outDir);
            final boolean compileSuccess = result.stdoutText().contains("\"code\":\"TSJ-COMPILE-SUCCESS\"");
            if (result.exitCode() != 0 || !compileSuccess || !result.stderrText().isEmpty()) {
                failures.add(
                        "fixture=" + fixture
                                + ", exitCode=" + result.exitCode()
                                + "\nstdout:\n" + result.stdoutText()
                                + "\nstderr:\n" + result.stderrText()
                );
            }
        }

        assertTrue(
                failures.isEmpty(),
                "TGTA non-TSX compile gate failures (" + failures.size() + "):\n\n" + String.join("\n\n", failures)
        );
    }

    @Test
    void tgtaKnownFailingFixturesEmitStableDiagnosticCodes() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path tgtaOkRoot = repoRoot.resolve("examples").resolve("tgta").resolve("src").resolve("ok");
        assertTrue(Files.isDirectory(tgtaOkRoot), "Missing TGTA ok fixture directory: " + tgtaOkRoot);

        final List<String> mismatches = new ArrayList<>();
        for (final Map.Entry<String, String> entry : KNOWN_FAILING_FIXTURES.entrySet()) {
            final Path fixture = tgtaOkRoot.resolve(entry.getKey());
            assertTrue(Files.isRegularFile(fixture), "Missing TGTA fixture: " + fixture);
            final Path outDir = tempDir.resolve("tgta-known-failing-" + fixture.getFileName().toString().replace('.', '_'));
            final CliInvocationResult result = invokeCompile(fixture, outDir);
            final String expectedCodeToken = "\"code\":\"" + entry.getValue() + "\"";
            final boolean hasExpectedDiagnostic = result.stdoutText().contains(expectedCodeToken)
                    || result.stderrText().contains(expectedCodeToken);
            final boolean hasUnexpectedSuccess = result.stdoutText().contains("\"code\":\"TSJ-COMPILE-SUCCESS\"")
                    || result.stderrText().contains("\"code\":\"TSJ-COMPILE-SUCCESS\"");
            if (result.exitCode() == 0 || !hasExpectedDiagnostic || hasUnexpectedSuccess) {
                mismatches.add(
                        "fixture=" + fixture
                                + ", expectedCode=" + entry.getValue()
                                + ", exitCode=" + result.exitCode()
                                + "\nstdout:\n" + result.stdoutText()
                                + "\nstderr:\n" + result.stderrText()
                );
            }
        }

        assertTrue(
                mismatches.isEmpty(),
                "TGTA known failing fixtures changed behavior (" + mismatches.size() + "):\n\n" + String.join("\n\n", mismatches)
        );
    }

    private static boolean isNonTsxTypeScriptFixture(final Path path) {
        final String fileName = path.getFileName().toString();
        if (fileName.endsWith(".tsx")) {
            return false;
        }
        return fileName.endsWith(".ts") || fileName.endsWith(".d.ts");
    }

    private static CliInvocationResult invokeCompile(final Path fixture, final Path outDir) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "compile",
                        fixture.toString(),
                        "--out",
                        outDir.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );
        return new CliInvocationResult(
                exitCode,
                stdout.toString(UTF_8),
                stderr.toString(UTF_8)
        );
    }

    private record CliInvocationResult(int exitCode, String stdoutText, String stderrText) {
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("examples/tgta/src/ok"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve TSJ repository root.");
    }
}
