package dev.tsj.cli.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Loads fixture specs from filesystem.
 */
public final class FixtureLoader {
    private static final String FIXTURE_FILE_NAME = "fixture.properties";

    private FixtureLoader() {
    }

    public static List<FixtureSpec> loadFixtures(final Path fixturesRoot) {
        try {
            if (!Files.exists(fixturesRoot) || !Files.isDirectory(fixturesRoot)) {
                throw new IllegalArgumentException("Fixture root is missing or not a directory: " + fixturesRoot);
            }
            final List<FixtureSpec> fixtures = new ArrayList<>();
            try (var stream = Files.list(fixturesRoot)) {
                stream.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(path -> fixtures.add(loadFixture(path)));
            }
            return fixtures;
        } catch (final IOException ioException) {
            throw new IllegalArgumentException(
                    "Failed to read fixture root " + fixturesRoot + ": " + ioException.getMessage(),
                    ioException
            );
        }
    }

    public static FixtureSpec loadFixture(final Path fixtureDir) {
        final Path descriptorPath = fixtureDir.resolve(FIXTURE_FILE_NAME);
        if (!Files.exists(descriptorPath)) {
            throw new IllegalArgumentException("Missing fixture descriptor: " + descriptorPath);
        }

        final Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(descriptorPath)) {
            properties.load(stream);
        } catch (final IOException ioException) {
            throw new IllegalArgumentException(
                    "Failed to load fixture descriptor " + descriptorPath + ": " + ioException.getMessage(),
                    ioException
            );
        }

        final String name = required(properties, "name", descriptorPath);
        final Path entry = resolvePath(fixtureDir, required(properties, "entry", descriptorPath));
        final ExpectedRuntimeResult nodeExpected = loadExpected(properties, fixtureDir, "node", descriptorPath);
        final ExpectedRuntimeResult tsjExpected = loadExpected(properties, fixtureDir, "tsj", descriptorPath);
        final boolean assertNodeMatchesTsj = Boolean.parseBoolean(
                properties.getProperty("assert.nodeMatchesTsj", "false")
        );
        final List<String> nodeArgs = parseArgs(properties.getProperty("node.args"));
        final List<String> tsjArgs = parseArgs(properties.getProperty("tsj.args"));

        return new FixtureSpec(
                name,
                fixtureDir,
                entry,
                nodeExpected,
                tsjExpected,
                assertNodeMatchesTsj,
                nodeArgs,
                tsjArgs
        );
    }

    private static ExpectedRuntimeResult loadExpected(
            final Properties properties,
            final Path fixtureDir,
            final String runtime,
            final Path descriptorPath
    ) {
        final int exitCode = parseInt(
                required(properties, "expected." + runtime + ".exitCode", descriptorPath),
                "expected." + runtime + ".exitCode",
                descriptorPath
        );
        final String stdout = readText(resolvePath(
                fixtureDir,
                required(properties, "expected." + runtime + ".stdout", descriptorPath)
        ));
        final String stderr = readText(resolvePath(
                fixtureDir,
                required(properties, "expected." + runtime + ".stderr", descriptorPath)
        ));
        final MatchMode stdoutMode = MatchMode.parse(
                properties.getProperty("expected." + runtime + ".stdoutMode"),
                MatchMode.EXACT
        );
        final MatchMode stderrMode = MatchMode.parse(
                properties.getProperty("expected." + runtime + ".stderrMode"),
                MatchMode.EXACT
        );
        return new ExpectedRuntimeResult(exitCode, stdout, stderr, stdoutMode, stderrMode);
    }

    private static Path resolvePath(final Path root, final String pathValue) {
        return root.resolve(pathValue).toAbsolutePath().normalize();
    }

    private static String readText(final Path filePath) {
        try {
            return Files.readString(filePath, UTF_8);
        } catch (final IOException ioException) {
            throw new IllegalArgumentException(
                    "Failed to read expected output file " + filePath + ": " + ioException.getMessage(),
                    ioException
            );
        }
    }

    private static String required(final Properties properties, final String key, final Path descriptorPath) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required key `" + key + "` in " + descriptorPath);
        }
        return value.trim();
    }

    private static int parseInt(final String value, final String key, final Path descriptorPath) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(
                    "Invalid integer for key `" + key + "` in " + descriptorPath + ": " + value,
                    numberFormatException
            );
        }
    }

    private static List<String> parseArgs(final String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        final String[] tokens = value.trim().split("\\s+");
        return List.copyOf(List.of(tokens));
    }
}
