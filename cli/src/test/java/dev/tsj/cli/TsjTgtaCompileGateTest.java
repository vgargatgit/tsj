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
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjTgtaCompileGateTest {
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
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        assertFalse(fixtures.isEmpty(), "No TGTA TypeScript fixtures found in " + tgtaOkRoot);
        final List<String> failures = new ArrayList<>();
        for (final Path fixture : fixtures) {
            final Path outDir = tempDir.resolve("tgta-" + fixture.getFileName().toString().replace('.', '_'));
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

            final String stdoutText = stdout.toString(UTF_8);
            final String stderrText = stderr.toString(UTF_8);
            final boolean compileSuccess = stdoutText.contains("\"code\":\"TSJ-COMPILE-SUCCESS\"");
            if (exitCode != 0 || !compileSuccess || !stderrText.isEmpty()) {
                failures.add(
                        "fixture=" + fixture
                                + ", exitCode=" + exitCode
                                + "\nstdout:\n" + stdoutText
                                + "\nstderr:\n" + stderrText
                );
            }
        }

        assertTrue(
                failures.isEmpty(),
                "TGTA non-TSX compile gate failures (" + failures.size() + "):\n\n" + String.join("\n\n", failures)
        );
    }

    private static boolean isNonTsxTypeScriptFixture(final Path path) {
        final String fileName = path.getFileName().toString();
        if (fileName.endsWith(".tsx")) {
            return false;
        }
        return fileName.endsWith(".ts") || fileName.endsWith(".d.ts");
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
