package dev.tsj.compiler.backend.jvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

final class TypeScriptSyntaxBridge {
    static final String SCHEMA_VERSION = "tsj-backend-token-v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BRIDGE_SCRIPT_PROPERTY = "tsj.backend.tokenBridgeScript";
    private static final String BRIDGE_SCRIPT_REPO_RELATIVE = "compiler/frontend/ts-bridge/emit-backend-tokens.cjs";
    private static final String BRIDGE_SCRIPT_MODULE_RELATIVE = "frontend/ts-bridge/emit-backend-tokens.cjs";

    BridgeResult tokenize(final String sourceText, final Path sourceFileHint) {
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(sourceFileHint, "sourceFileHint");

        final Path bridgeScript = resolveBridgeScript();
        final Path tempSource = createTempSource(sourceText, sourceFileHint);
        try {
            final ProcessBuilder processBuilder = new ProcessBuilder(
                    "node",
                    bridgeScript.toString(),
                    tempSource.toString()
            );
            processBuilder.directory(Path.of("").toAbsolutePath().normalize().toFile());
            final Process process = processBuilder.start();
            final String stdout;
            final String stderr;
            try (InputStream stdoutStream = process.getInputStream();
                 InputStream stderrStream = process.getErrorStream()) {
                stdout = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
                stderr = new String(stderrStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-AST-BRIDGE",
                        "TypeScript syntax bridge failed: " + (stderr.isBlank() ? stdout.trim() : stderr.trim())
                );
            }
            return parseBridgePayload(stdout);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-BRIDGE",
                    "Failed to invoke TypeScript syntax bridge: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-BRIDGE",
                    "Interrupted while waiting for TypeScript syntax bridge."
            );
        } finally {
            try {
                Files.deleteIfExists(tempSource);
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static Path createTempSource(final String sourceText, final Path sourceFileHint) {
        final String extension = sourceFileHint.getFileName().toString().endsWith(".tsx") ? ".tsx" : ".ts";
        try {
            final Path tempFile = Files.createTempFile("tsj-backend-syntax-", extension);
            Files.writeString(tempFile, sourceText, StandardCharsets.UTF_8, WRITE, TRUNCATE_EXISTING);
            return tempFile;
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-BRIDGE",
                    "Failed to create temporary TypeScript source for syntax bridge: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }
    }

    private static Path resolveBridgeScript() {
        final String configured = System.getProperty(BRIDGE_SCRIPT_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            final Path configuredPath = Path.of(configured).toAbsolutePath().normalize();
            if (Files.exists(configuredPath)) {
                return configuredPath;
            }
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-BRIDGE",
                    "Configured TypeScript syntax bridge script not found: " + configuredPath
            );
        }

        final Path cwd = Path.of("").toAbsolutePath().normalize();
        final Set<Path> searchRoots = new LinkedHashSet<>();
        Path cursor = cwd;
        while (cursor != null) {
            searchRoots.add(cursor);
            cursor = cursor.getParent();
        }

        for (Path root : searchRoots) {
            final Path[] candidates = new Path[]{
                    root.resolve(BRIDGE_SCRIPT_REPO_RELATIVE),
                    root.resolve(BRIDGE_SCRIPT_MODULE_RELATIVE),
                    root.resolve("../frontend/ts-bridge/emit-backend-tokens.cjs"),
                    root.resolve("../../compiler/frontend/ts-bridge/emit-backend-tokens.cjs")
            };
            for (Path candidate : candidates) {
                final Path normalized = candidate.normalize();
                if (Files.exists(normalized)) {
                    return normalized;
                }
            }
        }

        final Path[] classpathAdjacentCandidates = new Path[]{
                cwd.resolve(BRIDGE_SCRIPT_REPO_RELATIVE),
                cwd.resolve(BRIDGE_SCRIPT_MODULE_RELATIVE)
        };
        for (Path candidate : classpathAdjacentCandidates) {
            final Path normalized = candidate.normalize();
            if (Files.exists(normalized)) {
                return normalized;
            }
        }
        throw new JvmCompilationException(
                "TSJ-BACKEND-AST-BRIDGE",
                "Could not locate TypeScript syntax bridge script `emit-backend-tokens.cjs`."
        );
    }

    private static BridgeResult parseBridgePayload(final String stdout) {
        final JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(stdout);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-BRIDGE",
                    "Failed to parse TypeScript syntax bridge payload: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }
        final String schemaVersion = root.path("schemaVersion").asText("");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "Unexpected TypeScript syntax bridge schema `" + schemaVersion
                            + "`. Expected `" + SCHEMA_VERSION + "`."
            );
        }

        final JsonNode astNodesNode = root.get("astNodes");
        if (astNodesNode == null || !astNodesNode.isArray()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "TypeScript syntax bridge payload missing required `astNodes` array."
            );
        }

        final List<BridgeDiagnostic> diagnostics = new ArrayList<>();
        for (JsonNode diagnosticNode : root.path("diagnostics")) {
            diagnostics.add(new BridgeDiagnostic(
                    diagnosticNode.path("code").asText("TSJ-BACKEND-PARSE"),
                    diagnosticNode.path("message").asText("TypeScript parse diagnostic."),
                    nullableInt(diagnosticNode, "line"),
                    nullableInt(diagnosticNode, "column")
            ));
        }

        final List<BridgeToken> tokens = new ArrayList<>();
        for (JsonNode tokenNode : root.path("tokens")) {
            final String type = tokenNode.path("type").asText("");
            if (type.isBlank()) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-AST-SCHEMA",
                        "Token payload missing `type` field."
                );
            }
            tokens.add(new BridgeToken(
                    type,
                    tokenNode.path("text").asText(""),
                    tokenNode.path("line").asInt(1),
                    tokenNode.path("column").asInt(1)
            ));
        }

        final List<BridgeAstNode> astNodes = new ArrayList<>();
        for (JsonNode astNode : astNodesNode) {
            final String kind = astNode.path("kind").asText("");
            if (kind.isBlank()) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-AST-SCHEMA",
                        "AST payload node missing `kind` field."
                );
            }
            astNodes.add(new BridgeAstNode(
                    kind,
                    astNode.path("line").asInt(1),
                    astNode.path("column").asInt(1),
                    astNode.path("endLine").asInt(1),
                    astNode.path("endColumn").asInt(1)
            ));
        }

        final JsonNode normalizedProgram = root.has("normalizedProgram") && !root.get("normalizedProgram").isNull()
                ? root.get("normalizedProgram").deepCopy()
                : null;
        return new BridgeResult(
                List.copyOf(tokens),
                List.copyOf(diagnostics),
                List.copyOf(astNodes),
                normalizedProgram
        );
    }

    private static Integer nullableInt(final JsonNode node, final String key) {
        if (!node.hasNonNull(key)) {
            return null;
        }
        return node.path(key).asInt();
    }

    record BridgeResult(
            List<BridgeToken> tokens,
            List<BridgeDiagnostic> diagnostics,
            List<BridgeAstNode> astNodes,
            JsonNode normalizedProgram
    ) {
    }

    record BridgeToken(
            String type,
            String text,
            int line,
            int column
    ) {
    }

    record BridgeDiagnostic(
            String code,
            String message,
            Integer line,
            Integer column
    ) {
    }

    record BridgeAstNode(
            String kind,
            int line,
            int column,
            int endLine,
            int endColumn
    ) {
    }
}
