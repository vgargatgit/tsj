package dev.tsj.compiler.ir;

import dev.tsj.compiler.ir.hir.HirStatement;
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

class IrLoweringServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void lowersMinimalSubsetIntoAllIrTiers() throws Exception {
        final Path projectDir = tempDir.resolve("ir-project");
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
                const answer: number = 42;
                console.log(answer);
                """,
                UTF_8
        );

        final IrLoweringService service = new IrLoweringService(Path.of("").toAbsolutePath());
        final IrProject project = service.lowerProject(projectDir.resolve("tsconfig.json"));

        assertEquals(1, project.hir().modules().size());
        assertEquals(2, project.hir().modules().getFirst().statements().size());
        assertTrue(project.hir().modules().getFirst().statements()
                .stream()
                .map(HirStatement::kind)
                .toList()
                .contains("VAR_DECL"));
        assertTrue(project.hir().modules().getFirst().statements()
                .stream()
                .map(HirStatement::kind)
                .toList()
                .contains("PRINT"));

        assertEquals(1, project.mir().functions().size());
        assertTrue(project.mir().functions().getFirst().instructions()
                .stream()
                .anyMatch(instruction -> instruction.op().equals("CONST")));
        assertTrue(project.mir().functions().getFirst().instructions()
                .stream()
                .anyMatch(instruction -> instruction.op().equals("PRINT")));

        assertEquals(1, project.jir().classes().size());
        assertTrue(project.jir().classes().getFirst().methods().getFirst().bytecodeOps()
                .stream()
                .anyMatch(op -> op.contains("println")));
        assertTrue(project.diagnostics().isEmpty());
    }

    @Test
    void dumpToolWritesReadableJson() throws Exception {
        final Path projectDir = tempDir.resolve("dump-project");
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
                "const x: number = 1;\n",
                UTF_8
        );
        final Path outFile = projectDir.resolve("build/ir.json");

        IrDumpTool.dumpProject(Path.of("").toAbsolutePath(), projectDir.resolve("tsconfig.json"), outFile);

        final String output = Files.readString(outFile, UTF_8);
        assertTrue(output.contains("\"hir\""));
        assertTrue(output.contains("\"mir\""));
        assertTrue(output.contains("\"jir\""));
        assertTrue(output.contains("src/main.ts"));
    }

    @Test
    void representsCfgEdgesAndClosureCaptureMetadata() throws Exception {
        final Path projectDir = tempDir.resolve("closure-project");
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
                function outer(seed: number) {
                  const offset = seed + 1;
                  function inner(step: number) {
                    return offset + step;
                  }
                  return inner;
                }
                const fn = outer(3);
                console.log(fn(4));
                """,
                UTF_8
        );

        final IrProject project = new IrLoweringService(Path.of("").toAbsolutePath())
                .lowerProject(projectDir.resolve("tsconfig.json"));

        assertTrue(project.mir().functions().size() >= 3);

        final List<String> functionNames = project.mir().functions()
                .stream()
                .map(function -> function.name())
                .toList();
        assertTrue(functionNames.contains("main__init"));
        assertTrue(functionNames.contains("outer"));
        assertTrue(functionNames.contains("outer$inner"));

        final var innerFunction = project.mir().functions()
                .stream()
                .filter(function -> function.name().equals("outer$inner"))
                .findFirst()
                .orElseThrow();
        assertFalse(innerFunction.blocks().isEmpty());
        assertFalse(innerFunction.cfgEdges().isEmpty());
        assertFalse(innerFunction.scopes().isEmpty());
        assertFalse(innerFunction.captures().isEmpty());

        final var capture = innerFunction.captures().getFirst();
        assertEquals("offset", capture.variableName());
        assertNotNull(capture.sourceScopeId());
        assertNotNull(capture.targetScopeId());

        final var innerScope = innerFunction.scopes().getFirst();
        assertTrue(innerScope.locals().contains("step"));
        assertTrue(innerScope.capturedFromParent().contains("offset"));
    }
}
