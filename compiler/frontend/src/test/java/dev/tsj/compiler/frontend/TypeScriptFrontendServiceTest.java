package dev.tsj.compiler.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptFrontendServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsTsconfigAndBuildsTypedSummariesForAllProjectFiles() throws Exception {
        final Path projectDir = tempDir.resolve("good-project");
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(
                projectDir.resolve("tsconfig.json"),
                """
                {
                  "compilerOptions": {
                    "target": "ES2022",
                    "module": "ESNext",
                    "strict": true,
                    "noEmit": true
                  },
                  "include": ["src/**/*.ts"]
                }
                """,
                UTF_8
        );
        Files.writeString(
                projectDir.resolve("src/math.ts"),
                """
                export function add(a: number, b: number): number {
                  return a + b;
                }
                """,
                UTF_8
        );
        Files.writeString(
                projectDir.resolve("src/main.ts"),
                """
                import { add } from "./math";
                const value: number = add(20, 22);
                console.log(value);
                """,
                UTF_8
        );

        final TypeScriptFrontendService service = new TypeScriptFrontendService(Path.of("").toAbsolutePath());
        final FrontendAnalysisResult result = service.analyzeProject(projectDir.resolve("tsconfig.json"));

        assertEquals(
                projectDir.resolve("tsconfig.json").toAbsolutePath().normalize(),
                result.tsconfigPath()
        );
        assertEquals(2, result.sourceFiles().size());
        assertTrue(result.sourceFiles().stream().anyMatch(file -> file.path().endsWith("src/main.ts")));
        assertTrue(result.sourceFiles().stream().anyMatch(file -> file.path().endsWith("src/math.ts")));
        assertTrue(result.sourceFiles().stream().allMatch(file -> file.nodeCount() > 0));
        assertTrue(result.sourceFiles().stream().allMatch(file -> file.typedNodeCount() > 0));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void surfacesTypeDiagnosticsWithFileLineAndColumn() throws Exception {
        final Path projectDir = tempDir.resolve("bad-project");
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(
                projectDir.resolve("tsconfig.json"),
                """
                {
                  "compilerOptions": {
                    "target": "ES2022",
                    "module": "ESNext",
                    "strict": true,
                    "noEmit": true
                  },
                  "include": ["src/**/*.ts"]
                }
                """,
                UTF_8
        );
        Files.writeString(
                projectDir.resolve("src/main.ts"),
                """
                const text: string = 42;
                console.log(text);
                """,
                UTF_8
        );

        final TypeScriptFrontendService service = new TypeScriptFrontendService(Path.of("").toAbsolutePath());
        final FrontendAnalysisResult result = service.analyzeProject(projectDir.resolve("tsconfig.json"));

        assertFalse(result.diagnostics().isEmpty());
        final List<FrontendDiagnostic> withFile = result.diagnostics()
                .stream()
                .filter(diagnostic -> diagnostic.filePath() != null)
                .toList();
        assertFalse(withFile.isEmpty());
        final FrontendDiagnostic diagnostic = withFile.getFirst();
        assertTrue(diagnostic.filePath().endsWith("src/main.ts"));
        assertNotNull(diagnostic.line());
        assertNotNull(diagnostic.column());
        assertTrue(diagnostic.line() >= 1);
        assertTrue(diagnostic.column() >= 1);
    }
}
