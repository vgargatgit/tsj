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

    @Test
    void collectsJavaInteropBindingsFromNamedImports() throws Exception {
        final Path projectDir = tempDir.resolve("interop-project");
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
                import { max, min as lowest } from "java:java.lang.Math";
                console.log(max(7, lowest(2, 5)));
                """,
                UTF_8
        );

        final TypeScriptFrontendService service = new TypeScriptFrontendService(Path.of("").toAbsolutePath());
        final FrontendAnalysisResult result = service.analyzeProject(projectDir.resolve("tsconfig.json"));

        assertEquals(2, result.interopBindings().size());
        assertTrue(result.interopBindings().stream().anyMatch(binding ->
                binding.className().equals("java.lang.Math")
                        && binding.importedName().equals("max")
                        && binding.localName().equals("max")
        ));
        assertTrue(result.interopBindings().stream().anyMatch(binding ->
                binding.className().equals("java.lang.Math")
                        && binding.importedName().equals("min")
                        && binding.localName().equals("lowest")
        ));
        assertEquals(2, result.interopSymbols().size());
        assertTrue(result.interopSymbols().stream().anyMatch(symbol ->
                symbol.className().equals("java.lang.Math")
                        && symbol.importedName().equals("max")
                        && symbol.symbolKind().equals("STATIC_METHOD")
                        && symbol.descriptors().stream().anyMatch(descriptor -> descriptor.startsWith("(II)"))
        ));
    }

    @Test
    void surfacesInteropBindingDiagnosticsForUnsupportedImportShape() throws Exception {
        final Path projectDir = tempDir.resolve("interop-bad-project");
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
                import Math from "java:java.lang.Math";
                console.log(Math);
                """,
                UTF_8
        );

        final TypeScriptFrontendService service = new TypeScriptFrontendService(Path.of("").toAbsolutePath());
        final FrontendAnalysisResult result = service.analyzeProject(projectDir.resolve("tsconfig.json"));

        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                "TSJ26-INTEROP-SYNTAX".equals(diagnostic.code())
                        && diagnostic.message().contains("named imports")
        ));
        assertEquals(0, result.interopBindings().size());
    }

    @Test
    void resolvesDescriptorBackedSymbolsForConstructorsAndFieldAccessors() throws Exception {
        final Path projectDir = tempDir.resolve("interop-descriptor-project");
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
                import { $new, $instance$append, $instance$toString } from "java:java.lang.StringBuilder";
                import { $static$get$PUBLIC_COUNT } from "java:dev.tsj.compiler.frontend.fixtures.FrontendInteropFixture";
                const builder = $new();
                console.log($instance$toString($instance$append(builder, "ok")));
                console.log($static$get$PUBLIC_COUNT());
                """,
                UTF_8
        );

        final TypeScriptFrontendService service = new TypeScriptFrontendService(Path.of("").toAbsolutePath());
        final FrontendAnalysisResult result = service.analyzeProject(projectDir.resolve("tsconfig.json"));

        assertTrue(result.interopSymbols().stream().anyMatch(symbol ->
                symbol.importedName().equals("$new")
                        && symbol.symbolKind().equals("CONSTRUCTOR")
                        && symbol.descriptors().stream().anyMatch(descriptor -> descriptor.startsWith("("))
        ));
        assertTrue(result.interopSymbols().stream().anyMatch(symbol ->
                symbol.importedName().equals("$instance$append")
                        && symbol.symbolKind().equals("INSTANCE_METHOD")
                        && symbol.descriptors().stream().anyMatch(descriptor -> descriptor.contains("Ljava/lang/String;"))
        ));
        assertTrue(result.interopSymbols().stream().anyMatch(symbol ->
                symbol.importedName().equals("$static$get$PUBLIC_COUNT")
                        && symbol.symbolKind().equals("STATIC_FIELD_GET")
                        && symbol.descriptors().contains("()I")
        ));
    }

    @Test
    void emitsDescriptorAwareDiagnosticsForMissingClassesAndVisibilityViolations() throws Exception {
        final Path projectDir = tempDir.resolve("interop-diagnostics-project");
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
                import { nope } from "java:dev.tsj.compiler.frontend.DoesNotExist";
                import { hiddenEcho, missingEcho, $static$get$HIDDEN_COUNT } from "java:dev.tsj.compiler.frontend.fixtures.FrontendInteropFixture";
                console.log(nope, hiddenEcho, missingEcho, $static$get$HIDDEN_COUNT);
                """,
                UTF_8
        );

        final TypeScriptFrontendService service = new TypeScriptFrontendService(Path.of("").toAbsolutePath());
        final FrontendAnalysisResult result = service.analyzeProject(projectDir.resolve("tsconfig.json"));

        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                "TSJ55-INTEROP-CLASS-NOT-FOUND".equals(diagnostic.code())
                        && diagnostic.message().contains("DoesNotExist")
        ));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                "TSJ55-INTEROP-VISIBILITY".equals(diagnostic.code())
                        && diagnostic.message().contains("hiddenEcho")
        ));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                "TSJ55-INTEROP-MEMBER-NOT-FOUND".equals(diagnostic.code())
                        && diagnostic.message().contains("missingEcho")
        ));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                "TSJ55-INTEROP-VISIBILITY".equals(diagnostic.code())
                        && diagnostic.message().contains("HIDDEN_COUNT")
        ));
    }
}
