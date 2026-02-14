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

    @Test
    void emitsExplicitAsyncStateMachineMetadataForNestedAwaitControlFlow() throws Exception {
        final Path projectDir = tempDir.resolve("async-state-machine-project");
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
                async function run(limit: number) {
                  let total = 0;
                  let i = 0;
                  while (i < limit) {
                    if (i === 1) {
                      total = total + await Promise.resolve(i + 1);
                    } else {
                      total = total + await Promise.resolve(i + 2);
                    }
                    i = i + 1;
                  }
                  return total;
                }
                """,
                UTF_8
        );

        final IrProject project = new IrLoweringService(Path.of("").toAbsolutePath())
                .lowerProject(projectDir.resolve("tsconfig.json"));

        final var runFunction = project.mir().functions()
                .stream()
                .filter(function -> function.name().equals("run"))
                .findFirst()
                .orElseThrow();

        assertTrue(runFunction.async());
        assertNotNull(runFunction.asyncFrame());
        assertTrue(runFunction.asyncFrame().states().size() >= 4);
        assertEquals("ENTRY", runFunction.asyncFrame().states().getFirst().kind());
        assertTrue(runFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("ASYNC_SUSPEND")));
        assertTrue(runFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("ASYNC_RESUME")));
        assertTrue(runFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("ASYNC_STATE")));

        final var runMethod = project.jir().classes()
                .stream()
                .flatMap(clazz -> clazz.methods().stream())
                .filter(method -> method.name().equals("run"))
                .findFirst()
                .orElseThrow();
        assertTrue(runMethod.async());
        assertFalse(runMethod.asyncStateOps().isEmpty());
        assertTrue(runMethod.asyncStateOps().stream().anyMatch(op -> op.contains("SUSPEND")));
        assertTrue(runMethod.asyncStateOps().stream().anyMatch(op -> op.contains("RESUME")));
    }

    @Test
    void modelsAsyncTerminalControlFlowAcrossSuspensionPoints() throws Exception {
        final Path projectDir = tempDir.resolve("async-terminal-flow-project");
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
                async function flow(flag: number) {
                  let i = 0;
                  while (i < 3) {
                    if (i === 1) {
                      i = i + await Promise.resolve(1);
                      continue;
                    }
                    if (i === 2) {
                      break;
                    }
                    i = i + 1;
                  }
                  try {
                    if (flag > 0) {
                      throw await Promise.resolve("boom");
                    }
                  } catch (err) {
                    return "caught";
                  } finally {
                    console.log("done");
                  }
                  return "ok";
                }
                """,
                UTF_8
        );

        final IrProject project = new IrLoweringService(Path.of("").toAbsolutePath())
                .lowerProject(projectDir.resolve("tsconfig.json"));

        final var flowFunction = project.mir().functions()
                .stream()
                .filter(function -> function.name().equals("flow"))
                .findFirst()
                .orElseThrow();
        assertTrue(flowFunction.async());
        assertNotNull(flowFunction.asyncFrame());

        assertTrue(flowFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("BREAK")));
        assertTrue(flowFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("CONTINUE")));
        assertTrue(flowFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("TRY_BEGIN")));
        assertTrue(flowFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("CATCH_BEGIN")));
        assertTrue(flowFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("FINALLY_BEGIN")));
        assertTrue(flowFunction.instructions().stream().anyMatch(instruction -> instruction.op().equals("THROW")));

        assertTrue(flowFunction.asyncFrame().terminalOps().contains("BREAK"));
        assertTrue(flowFunction.asyncFrame().terminalOps().contains("CONTINUE"));
        assertTrue(flowFunction.asyncFrame().terminalOps().contains("THROW"));
        assertTrue(flowFunction.asyncFrame().terminalOps().contains("RETURN"));
    }

    @Test
    void asyncFrameMetadataIsSerializedInIrJson() throws Exception {
        final Path projectDir = tempDir.resolve("async-json-project");
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
                async function sample() {
                  const value = await Promise.resolve(3);
                  return value;
                }
                sample();
                """,
                UTF_8
        );

        final IrProject project = new IrLoweringService(Path.of("").toAbsolutePath())
                .lowerProject(projectDir.resolve("tsconfig.json"));
        final String json = IrJsonPrinter.toJson(project);

        assertTrue(json.contains("\"async\" : true"));
        assertTrue(json.contains("\"asyncFrame\""));
        assertTrue(json.contains("\"kind\" : \"SUSPEND\""));
        assertTrue(json.contains("\"op\" : \"ASYNC_SUSPEND\""));
        assertTrue(json.contains("\"asyncStateOps\""));
    }
}
