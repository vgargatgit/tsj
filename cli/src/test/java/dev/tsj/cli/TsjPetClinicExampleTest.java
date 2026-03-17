package dev.tsj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TsjPetClinicExampleTest {
    @TempDir
    Path tempDir;

    @Test
    void petClinicPackagesRunsWithH2AndPublishesOpenApi() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path exampleRoot = copyFixtureTree(
                repoRoot.resolve("examples/pet-clinic"),
                tempDir.resolve("pet-clinic")
        );
        final Path resourcesDir = exampleRoot.resolve("resources");
        Files.writeString(
                resourcesDir.resolve("application.properties"),
                Files.readString(resourcesDir.resolve("application.properties"), UTF_8)
                        + System.lineSeparator()
                        + "server.address=127.0.0.1" + System.lineSeparator()
                        + "server.port=18081" + System.lineSeparator(),
                UTF_8
        );

        final Path libDir = resolveDependencies(exampleRoot, tempDir.resolve("deps"));
        final String classpath = buildClasspath(libDir);
        final Path outDir = tempDir.resolve("out");

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(
                new String[]{
                        "package",
                        exampleRoot.resolve("http-main.ts").toString(),
                        "--out",
                        outDir.toString(),
                        "--classpath",
                        classpath,
                        "--interop-policy",
                        "broad",
                        "--ack-interop-risk",
                        "--mode",
                        "jvm-strict",
                        "--resource-dir",
                        resourcesDir.toString()
                },
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode, "stderr:\n" + stderr.toString(UTF_8));
        assertEquals("", stderr.toString(UTF_8));
        assertTrue(stdout.toString(UTF_8).contains("\"code\":\"TSJ-PACKAGE-SUCCESS\""), stdout.toString(UTF_8));

        final Path packagedJar = outDir.resolve("tsj-app.jar");
        assertTrue(Files.exists(packagedJar), "Packaged jar missing: " + packagedJar);

        final StartedProcess started = launchJar(packagedJar);
        try {
            final String openApi = waitForHttpBody("http://127.0.0.1:18081/v3/api-docs", started);
            final String owners = waitForHttpBody("http://127.0.0.1:18081/api/petclinic/owners?lastName=Frank", started);
            final String petsBefore = waitForHttpBody("http://127.0.0.1:18081/api/petclinic/owners/1/pets", started);
            final String created = httpRequest(
                    "POST",
                    "http://127.0.0.1:18081/api/petclinic/owners/1/pets",
                    "{\"name\":\"Nova\",\"type\":\"cat\",\"birthDate\":\"2022-06-01\"}",
                    "application/json"
            );
            final String petsAfter = httpRequest(
                    "GET",
                    "http://127.0.0.1:18081/api/petclinic/owners/1/pets",
                    null,
                    null
            );
            final String swaggerUi = httpRequest(
                    "GET",
                    "http://127.0.0.1:18081/swagger-ui/index.html",
                    null,
                    null
            );

            assertTrue(openApi.contains("\"title\":\"TSJ Pet Clinic API\""), openApi);
            assertTrue(openApi.contains("/api/petclinic/owners"), openApi);
            assertTrue(openApi.contains("/api/petclinic/owners/{ownerId}/pets"), openApi);
            assertTrue(owners.contains("\"George\""), owners);
            assertTrue(owners.contains("\"Franklin\""), owners);
            assertTrue(petsBefore.contains("\"Leo\""), petsBefore);
            assertTrue(created.contains("\"Nova\""), created);
            assertTrue(petsAfter.contains("\"Nova\""), petsAfter);
            assertTrue(swaggerUi.contains("Swagger UI"), swaggerUi);
        } finally {
            started.stop();
        }
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("cli"))
                    && Files.isDirectory(current.resolve("examples"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve repository root from working directory.");
    }

    private static Path copyFixtureTree(final Path sourceRoot, final Path targetRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : stream.sorted().toList()) {
                final Path relative = sourceRoot.relativize(source);
                if (relative.startsWith(".build") || relative.startsWith("deps")) {
                    continue;
                }
                final Path target = targetRoot.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(Objects.requireNonNull(target.getParent(), "Target parent is required."));
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return targetRoot;
    }

    private static Path resolveDependencies(final Path exampleRoot, final Path libDir) throws Exception {
        Files.createDirectories(libDir);
        final ProcessBuilder processBuilder = new ProcessBuilder(
                "mvn",
                "-B",
                "-ntp",
                "-q",
                "-f",
                exampleRoot.resolve("pom.xml").toString(),
                "dependency:copy-dependencies",
                "-DincludeScope=runtime",
                "-Dmdep.useRepositoryLayout=false",
                "-DoutputDirectory=" + libDir
        );
        processBuilder.redirectErrorStream(true);
        final Process process = processBuilder.start();
        final String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), UTF_8);
        }
        final int exitCode = process.waitFor();
        assertEquals(0, exitCode, output);
        try (Stream<Path> files = Files.list(libDir)) {
            final long jarCount = files.filter(path -> path.getFileName().toString().endsWith(".jar")).count();
            assertTrue(jarCount > 0, "Expected runtime jars in " + libDir);
        }
        return libDir;
    }

    private static String buildClasspath(final Path libDir) throws IOException {
        try (Stream<Path> files = Files.list(libDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .reduce((left, right) -> left + File.pathSeparator + right)
                    .orElseThrow(() -> new IllegalStateException("No jar dependencies found under " + libDir));
        }
    }

    private static StartedProcess launchJar(final Path jarPath) throws IOException {
        final ProcessBuilder processBuilder = new ProcessBuilder(
                resolveJavaLauncher().toString(),
                "-jar",
                jarPath.toAbsolutePath().normalize().toString()
        );
        processBuilder.redirectErrorStream(true);
        return new StartedProcess(processBuilder.start());
    }

    private static String waitForHttpBody(final String url, final StartedProcess started) throws Exception {
        final Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        IOException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            if (!started.process().isAlive()) {
                fail("Pet clinic process exited early.\nOutput:\n" + started.output());
            }
            try {
                return httpRequest("GET", url, null, null);
            } catch (IOException exception) {
                lastFailure = exception;
                Thread.sleep(250L);
            }
        }
        final String output = started.output();
        if (lastFailure != null) {
            throw new IOException("Timed out waiting for HTTP endpoint " + url + "\nProcess output:\n" + output, lastFailure);
        }
        throw new IOException("Timed out waiting for HTTP endpoint " + url + "\nProcess output:\n" + output);
    }

    private static String httpRequest(
            final String method,
            final String url,
            final String body,
            final String contentType
    ) throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, UTF_8));
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        final HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url + ": " + response.body());
        }
        return response.body();
    }

    private static Path resolveJavaLauncher() {
        final String executable = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }

    private static final class StartedProcess {
        private final Process process;
        private final ByteArrayOutputStream outputBuffer;
        private final Thread reader;

        private StartedProcess(final Process process) {
            this.process = process;
            this.outputBuffer = new ByteArrayOutputStream();
            this.reader = new Thread(() -> {
                try (InputStream inputStream = process.getInputStream()) {
                    inputStream.transferTo(outputBuffer);
                } catch (IOException ignored) {
                }
            }, "pet-clinic-output");
            this.reader.setDaemon(true);
            this.reader.start();
        }

        Process process() {
            return process;
        }

        String output() {
            try {
                reader.join(200L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            return outputBuffer.toString(UTF_8);
        }

        void stop() {
            process.destroy();
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                reader.join(500L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
