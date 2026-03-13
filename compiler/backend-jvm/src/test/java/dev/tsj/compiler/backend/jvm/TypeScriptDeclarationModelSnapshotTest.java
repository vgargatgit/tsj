package dev.tsj.compiler.backend.jvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class TypeScriptDeclarationModelSnapshotTest {
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();
    private static final boolean UPDATE_SNAPSHOTS = Boolean.getBoolean("tsj.updateDeclarationSnapshots");
    private static final String FIXTURE_ROOT =
            "compiler/backend-jvm/src/test/resources/declaration-model/fixtures";
    private static final String SNAPSHOT_ROOT =
            "compiler/backend-jvm/src/test/resources/declaration-model/expected";

    @Test
    void declarationFixturesMatchSnapshots() throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path fixtureRoot = repoRoot.resolve(FIXTURE_ROOT);
        final Path snapshotRoot = repoRoot.resolve(SNAPSHOT_ROOT);
        final TypeScriptSyntaxBridge bridge = new TypeScriptSyntaxBridge();
        final List<String> failures = new ArrayList<>();

        try (Stream<Path> stream = Files.list(fixtureRoot)) {
            final List<Path> fixtureFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".ts"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path fixtureFile : fixtureFiles) {
                final Path relativeFixture = fixtureRoot.relativize(fixtureFile);
                final TypeScriptSyntaxBridge.BridgeResult result = bridge.tokenize(
                        Files.readString(fixtureFile, UTF_8),
                        fixtureFile
                );
                final String snapshotText = renderSnapshot(relativeFixture, result.decoratorDeclarations());
                final Path expectedSnapshot = snapshotRoot.resolve(relativeFixture.toString() + ".snapshot.json");
                if (UPDATE_SNAPSHOTS) {
                    Files.createDirectories(expectedSnapshot.getParent());
                    Files.writeString(expectedSnapshot, snapshotText, UTF_8);
                    continue;
                }
                if (!Files.exists(expectedSnapshot)) {
                    failures.add("Missing declaration snapshot: " + toUnixPath(expectedSnapshot));
                    continue;
                }
                final String expectedText = Files.readString(expectedSnapshot, UTF_8);
                if (!expectedText.equals(snapshotText)) {
                    failures.add(
                            "Declaration snapshot mismatch for "
                                    + toUnixPath(relativeFixture)
                                    + " (rerun with -Dtsj.updateDeclarationSnapshots=true to refresh)"
                    );
                }
            }
        }

        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    private static String renderSnapshot(final Path relativeFixture, final JsonNode declarationModel) throws IOException {
        final ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("schemaVersion", TypeScriptSyntaxBridge.SCHEMA_VERSION);
        root.put("fixture", toUnixPath(relativeFixture));
        root.set("declarationModel", canonicalize(declarationModel));
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
            final com.fasterxml.jackson.databind.node.ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
            for (JsonNode element : node) {
                canonical.add(canonicalize(element));
            }
            return canonical;
        }
        return node.deepCopy();
    }

    private static Path resolveRepoRoot() {
        final Path cwd = Path.of("").toAbsolutePath().normalize();
        Path cursor = cwd;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml")) && Files.isDirectory(cursor.resolve("compiler"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from " + cwd);
    }

    private static String toUnixPath(final Path path) {
        return path.toString().replace('\\', '/');
    }
}
