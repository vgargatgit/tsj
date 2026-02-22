package dev.tsj.compiler.backend.jvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.fail;

class TypeScriptSyntaxBridgeConformanceSnapshotTest {
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();
    private static final boolean UPDATE_SNAPSHOTS = Boolean.getBoolean("tsj.updateConformanceSnapshots");
    private static final String SNAPSHOT_ROOT = "compiler/backend-jvm/src/test/resources/ts-conformance/expected";

    @Test
    void tgtaRawFixturesMatchBridgeConformanceSnapshots() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path tgtaSourceRoot = repoRoot.resolve("examples").resolve("tgta").resolve("src");
        final Path snapshotRoot = repoRoot.resolve(SNAPSHOT_ROOT);
        final List<Path> fixtureFiles = collectFixtureFiles(tgtaSourceRoot);
        final TypeScriptSyntaxBridge bridge = new TypeScriptSyntaxBridge();
        final List<String> failures = new ArrayList<>();

        for (Path fixtureFile : fixtureFiles) {
            final Path relativeFixture = tgtaSourceRoot.relativize(fixtureFile);
            final TypeScriptSyntaxBridge.BridgeResult result = bridge.tokenize(
                    Files.readString(fixtureFile, UTF_8),
                    fixtureFile
            );
            final String snapshotText = renderSnapshot(relativeFixture, result);
            final Path expectedSnapshot = snapshotRoot.resolve(relativeFixture.toString() + ".snapshot.json");
            if (UPDATE_SNAPSHOTS) {
                Files.createDirectories(expectedSnapshot.getParent());
                Files.writeString(expectedSnapshot, snapshotText, UTF_8);
                continue;
            }
            if (!Files.exists(expectedSnapshot)) {
                failures.add("Missing snapshot: " + toUnixPath(expectedSnapshot));
                continue;
            }
            final String expectedText = Files.readString(expectedSnapshot, UTF_8);
            if (!expectedText.equals(snapshotText)) {
                failures.add(
                        "Snapshot mismatch for "
                                + toUnixPath(relativeFixture)
                                + " (rerun with -Dtsj.updateConformanceSnapshots=true to refresh)"
                );
            }
        }

        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    private static List<Path> collectFixtureFiles(final Path tgtaSourceRoot) throws IOException {
        final List<Path> files = new ArrayList<>();
        files.addAll(listFixturesInDirectory(tgtaSourceRoot.resolve("ok")));
        files.addAll(listFixturesInDirectory(tgtaSourceRoot.resolve("err")));
        files.sort(Comparator.comparing(path -> toUnixPath(tgtaSourceRoot.relativize(path))));
        return List.copyOf(files);
    }

    private static List<Path> listFixturesInDirectory(final Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(TypeScriptSyntaxBridgeConformanceSnapshotTest::isConformanceFixture)
                    .toList();
        }
    }

    private static boolean isConformanceFixture(final Path path) {
        final String fileName = path.getFileName().toString();
        if (fileName.endsWith(".tsx")) {
            return false;
        }
        return fileName.endsWith(".ts") || fileName.endsWith(".d.ts");
    }

    private static String renderSnapshot(
            final Path relativeFixture,
            final TypeScriptSyntaxBridge.BridgeResult result
    ) throws IOException {
        final ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("schemaVersion", TypeScriptSyntaxBridge.SCHEMA_VERSION);
        root.put("fixture", toUnixPath(relativeFixture));

        final ArrayNode diagnosticsNode = root.putArray("diagnostics");
        for (TypeScriptSyntaxBridge.BridgeDiagnostic diagnostic : result.diagnostics()) {
            final ObjectNode entry = diagnosticsNode.addObject();
            entry.put("code", diagnostic.code());
            entry.put("message", diagnostic.message());
            if (diagnostic.line() == null) {
                entry.putNull("line");
            } else {
                entry.put("line", diagnostic.line());
            }
            if (diagnostic.column() == null) {
                entry.putNull("column");
            } else {
                entry.put("column", diagnostic.column());
            }
        }

        final ArrayNode astNodesNode = root.putArray("astNodes");
        for (TypeScriptSyntaxBridge.BridgeAstNode astNode : result.astNodes()) {
            final ObjectNode entry = astNodesNode.addObject();
            entry.put("kind", astNode.kind());
            entry.put("line", astNode.line());
            entry.put("column", astNode.column());
            entry.put("endLine", astNode.endLine());
            entry.put("endColumn", astNode.endColumn());
        }

        if (result.normalizedProgram() == null || result.normalizedProgram().isNull()) {
            root.putNull("normalizedProgram");
        } else {
            root.set("normalizedProgram", canonicalize(result.normalizedProgram()));
        }

        return SNAPSHOT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(canonicalize(root)) + "\n";
    }

    private static JsonNode canonicalize(final JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            final ObjectNode canonical = JsonNodeFactory.instance.objectNode();
            final List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.sort(String::compareTo);
            for (String fieldName : fieldNames) {
                canonical.set(fieldName, canonicalize(node.get(fieldName)));
            }
            return canonical;
        }
        if (node.isArray()) {
            final ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
            for (JsonNode element : node) {
                canonical.add(canonicalize(element));
            }
            return canonical;
        }
        return node.deepCopy();
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("README.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve TSJ repository root.");
    }

    private static String toUnixPath(final Path path) {
        return path.toString().replace('\\', '/');
    }
}
