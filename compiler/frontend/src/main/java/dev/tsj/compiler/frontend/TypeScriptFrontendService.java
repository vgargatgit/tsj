package dev.tsj.compiler.frontend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Frontend service that delegates parsing and type-checking to a Node bridge
 * using the TypeScript compiler API.
 */
public final class TypeScriptFrontendService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path workspaceRoot;

    public TypeScriptFrontendService(final Path workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot")
                .toAbsolutePath()
                .normalize();
    }

    public FrontendAnalysisResult analyzeProject(final Path tsconfigPath) {
        final Path normalizedTsconfig = normalizeTsconfig(tsconfigPath);
        final Path bridgeScript = resolveBridgeScript();

        final ProcessResult processResult = runBridgeProcess(bridgeScript, normalizedTsconfig);
        if (processResult.exitCode != 0) {
            final String errorMessage = processResult.stderr.isBlank()
                    ? processResult.stdout
                    : processResult.stderr;
            throw new IllegalStateException("TypeScript bridge failed: " + errorMessage.trim());
        }

        try {
            final JsonNode root = OBJECT_MAPPER.readTree(processResult.stdout);
            final List<FrontendSourceFileSummary> sourceFiles = new ArrayList<>();
            for (JsonNode fileNode : root.path("sourceFiles")) {
                sourceFiles.add(new FrontendSourceFileSummary(
                        fileNode.path("path").asText(),
                        fileNode.path("nodeCount").asInt(),
                        fileNode.path("typedNodeCount").asInt()
                ));
            }

            final List<FrontendDiagnostic> diagnostics = new ArrayList<>();
            for (JsonNode diagnosticNode : root.path("diagnostics")) {
                diagnostics.add(new FrontendDiagnostic(
                        diagnosticNode.path("code").asText(),
                        diagnosticNode.path("category").asText(),
                        diagnosticNode.path("message").asText(),
                        nullableText(diagnosticNode, "filePath"),
                        nullableInt(diagnosticNode, "line"),
                        nullableInt(diagnosticNode, "column")
                ));
            }

            final List<FrontendInteropBinding> interopBindings = new ArrayList<>();
            for (JsonNode bindingNode : root.path("interopBindings")) {
                interopBindings.add(new FrontendInteropBinding(
                        bindingNode.path("filePath").asText(),
                        bindingNode.path("line").asInt(),
                        bindingNode.path("column").asInt(),
                        bindingNode.path("className").asText(),
                        bindingNode.path("importedName").asText(),
                        bindingNode.path("localName").asText()
                ));
            }

            final JavaInteropSymbolResolver.Resolution interopResolution =
                    new JavaInteropSymbolResolver().resolve(interopBindings);
            diagnostics.addAll(interopResolution.diagnostics());

            return new FrontendAnalysisResult(
                    normalizedTsconfig,
                    sourceFiles,
                    diagnostics,
                    interopBindings,
                    interopResolution.symbols()
            );
        } catch (final IOException ioException) {
            throw new IllegalStateException(
                    "Failed to parse TypeScript bridge output: " + ioException.getMessage()
                            + ". Raw stdout: " + processResult.stdout,
                    ioException
            );
        }
    }

    private Path resolveBridgeScript() {
        final Path moduleRelative = workspaceRoot.resolve("ts-bridge/analyze-project.cjs").normalize();
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }

        final Path repoRelative = workspaceRoot.resolve("compiler/frontend/ts-bridge/analyze-project.cjs")
                .normalize();
        if (Files.exists(repoRelative)) {
            return repoRelative;
        }

        throw new IllegalStateException(
                "Missing frontend bridge script. Checked: " + moduleRelative + " and " + repoRelative
        );
    }

    private Path normalizeTsconfig(final Path tsconfigPath) {
        final Path normalized = Objects.requireNonNull(tsconfigPath, "tsconfigPath")
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("tsconfig file not found: " + normalized);
        }
        return normalized;
    }

    private ProcessResult runBridgeProcess(final Path bridgeScript, final Path tsconfigPath) {
        final ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                bridgeScript.toString(),
                tsconfigPath.toString()
        );
        processBuilder.directory(workspaceRoot.toFile());
        try {
            final Process process = processBuilder.start();
            final StreamCollector stdoutCollector = new StreamCollector(process.getInputStream());
            final StreamCollector stderrCollector = new StreamCollector(process.getErrorStream());
            stdoutCollector.start();
            stderrCollector.start();
            final int exitCode = process.waitFor();
            stdoutCollector.join();
            stderrCollector.join();
            return new ProcessResult(exitCode, stdoutCollector.output(), stderrCollector.output());
        } catch (final IOException ioException) {
            throw new IllegalStateException("Failed to start TypeScript bridge: " + ioException.getMessage(), ioException);
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for TypeScript bridge");
        }
    }

    private static String nullableText(final JsonNode node, final String key) {
        if (!node.hasNonNull(key)) {
            return null;
        }
        return node.path(key).asText();
    }

    private static Integer nullableInt(final JsonNode node, final String key) {
        if (!node.hasNonNull(key)) {
            return null;
        }
        return node.path(key).asInt();
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }

    private static final class StreamCollector extends Thread {
        private final InputStream stream;
        private volatile String output;

        private StreamCollector(final InputStream stream) {
            this.stream = stream;
            this.output = "";
            setName("tsj-frontend-stream-collector");
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException ioException) {
                output = "failed to read process stream: " + ioException.getMessage();
            }
        }

        private String output() {
            return output;
        }
    }
}
