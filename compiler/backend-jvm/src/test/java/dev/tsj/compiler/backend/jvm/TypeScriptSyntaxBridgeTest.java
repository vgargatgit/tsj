package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptSyntaxBridgeTest {
    private static final String TOKEN_BRIDGE_SCRIPT_PROPERTY = "tsj.backend.tokenBridgeScript";

    @TempDir
    Path tempDir;

    @Test
    void emitsVersionedTokenPayloadForValidTypeScript() throws Exception {
        final Path sourceFile = tempDir.resolve("valid.ts");
        Files.writeString(sourceFile, "const total = 1 + 2;\n", UTF_8);

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertFalse(result.tokens().isEmpty());
        assertEquals("KEYWORD", result.tokens().getFirst().type());
        assertEquals("const", result.tokens().getFirst().text());
        assertEquals("EOF", result.tokens().getLast().type());
        assertFalse(result.astNodes().isEmpty());
        assertEquals("SourceFile", result.astNodes().getFirst().kind());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
    }

    @Test
    void surfacesTypeScriptParserDiagnosticsForInvalidSource() throws Exception {
        final Path sourceFile = tempDir.resolve("invalid.ts");
        Files.writeString(sourceFile, "const value = ;\n", UTF_8);

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertFalse(result.diagnostics().isEmpty());
        final TypeScriptSyntaxBridge.BridgeDiagnostic diagnostic = result.diagnostics().getFirst();
        assertTrue(diagnostic.code().startsWith("TS"));
        assertTrue(diagnostic.line() != null && diagnostic.line() >= 1);
        assertTrue(diagnostic.column() != null && diagnostic.column() >= 1);
        assertTrue(result.tokens().isEmpty());
    }

    @Test
    void failsFastWhenConfiguredBridgeScriptIsMissing() {
        final String previousScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, tempDir.resolve("missing-bridge.cjs").toString());
            final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    JvmCompilationException.class,
                    () -> new TypeScriptSyntaxBridge().tokenize("const x = 1;", tempDir.resolve("sample.ts"))
            );

            assertEquals("TSJ-BACKEND-AST-BRIDGE", exception.code());
            assertTrue(exception.getMessage().contains("not found"));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousScript);
        }
    }

    @Test
    void failsWhenBridgePayloadSchemaVersionIsUnexpected() throws Exception {
        final Path sourceFile = tempDir.resolve("schema.ts");
        final Path bridgeScript = tempDir.resolve("bridge-schema-mismatch.cjs");
        Files.writeString(sourceFile, "const value = 1;\n", UTF_8);
        Files.writeString(
                bridgeScript,
                """
                process.stdout.write(JSON.stringify({
                  schemaVersion: "tsj-backend-token-v0",
                  diagnostics: [],
                  tokens: []
                }));
                """,
                UTF_8
        );

        final String previousScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, bridgeScript.toString());
            final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    JvmCompilationException.class,
                    () -> new TypeScriptSyntaxBridge().tokenize(Files.readString(sourceFile, UTF_8), sourceFile)
            );

            assertEquals("TSJ-BACKEND-AST-SCHEMA", exception.code());
            assertTrue(exception.getMessage().contains("tsj-backend-token-v0"));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousScript);
        }
    }

    @Test
    void failsWhenBridgePayloadOmitsAstNodes() throws Exception {
        final Path sourceFile = tempDir.resolve("missing-ast.ts");
        final Path bridgeScript = tempDir.resolve("bridge-missing-ast.cjs");
        Files.writeString(sourceFile, "const value = 1;\n", UTF_8);
        Files.writeString(
                bridgeScript,
                """
                process.stdout.write(JSON.stringify({
                  schemaVersion: "tsj-backend-token-v1",
                  diagnostics: [],
                  tokens: [{ type: "KEYWORD", text: "const", line: 1, column: 1 }]
                }));
                """,
                UTF_8
        );

        final String previousScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, bridgeScript.toString());
            final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    JvmCompilationException.class,
                    () -> new TypeScriptSyntaxBridge().tokenize(Files.readString(sourceFile, UTF_8), sourceFile)
            );

            assertEquals("TSJ-BACKEND-AST-SCHEMA", exception.code());
            assertTrue(exception.getMessage().contains("astNodes"));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousScript);
        }
    }

    @Test
    void emitsNormalizedProgramForClassDeclarationSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("class-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }

                  inc(delta: number) {
                    this.value = this.value + delta;
                    return this.value;
                  }
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals("Program", result.normalizedProgram().path("kind").asText());
        assertEquals(
                "ClassDeclarationStatement",
                result.normalizedProgram().path("statements").get(0).path("kind").asText()
        );
    }

    @Test
    void normalizesSuperCallStatementInsideConstructor() throws Exception {
        final Path sourceFile = tempDir.resolve("super-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed);
                  }
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "SuperCallStatement",
                result.normalizedProgram()
                        .path("statements")
                        .get(1)
                        .path("declaration")
                        .path("constructorMethod")
                        .path("body")
                        .get(0)
                        .path("kind")
                        .asText()
        );
    }

    @Test
    void emitsNormalizedProgramForForAwaitOfShape() throws Exception {
        final Path sourceFile = tempDir.resolve("unsupported-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                async function run() {
                  for await (const item of [1, 2, 3]) {
                    console.log(item);
                  }
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "AwaitExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("declaration")
                        .path("body")
                        .get(0)
                        .path("thenBlock")
                        .get(2)
                        .path("body")
                        .get(0)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
    }

    @Test
    void emitsNormalizedProgramForForAndSwitchSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("for-switch-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                let sum = 0;
                for (let i = 0; i < 3; i = i + 1) {
                  sum = sum + i;
                }
                switch (sum) {
                  case 3:
                    console.log("ok");
                    break;
                  default:
                    console.log("bad");
                    break;
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals("Program", result.normalizedProgram().path("kind").asText());
        assertTrue(result.normalizedProgram().path("statements").size() >= 3);
    }

    @Test
    void normalizesLogicalAndNullishBinaryOperators() throws Exception {
        final Path sourceFile = tempDir.resolve("logical-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                const a = true && "x";
                const b = false || "y";
                const c = null ?? "z";
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "&&",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("expression")
                        .path("operator")
                        .asText()
        );
        assertEquals(
                "||",
                result.normalizedProgram()
                        .path("statements")
                        .get(1)
                        .path("expression")
                        .path("operator")
                        .asText()
        );
        assertEquals(
                "??",
                result.normalizedProgram()
                        .path("statements")
                        .get(2)
                        .path("expression")
                        .path("operator")
                        .asText()
        );
    }

    @Test
    void normalizesConditionalExpressionOperator() throws Exception {
        final Path sourceFile = tempDir.resolve("conditional-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                const value = true ? "then" : "else";
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "ConditionalExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
    }

    @Test
    void normalizesAssignmentExpressionsIncludingCompoundOperators() throws Exception {
        final Path sourceFile = tempDir.resolve("assignment-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                let value = 1;
                const captured = (value = 2);
                value += 3;
                value ??= 9;
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "AssignmentExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(1)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
        assertEquals(
                "+=",
                result.normalizedProgram()
                        .path("statements")
                        .get(2)
                        .path("expression")
                        .path("operator")
                        .asText()
        );
        assertEquals(
                "??=",
                result.normalizedProgram()
                        .path("statements")
                        .get(3)
                        .path("expression")
                        .path("operator")
                        .asText()
        );
    }

    @Test
    void normalizesOptionalMemberAndOptionalCallExpressions() throws Exception {
        final Path sourceFile = tempDir.resolve("optional-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                const value = holder?.name;
                const result = maybeFn?.();
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "OptionalMemberAccessExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
        assertEquals(
                "OptionalCallExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(1)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
    }

    @Test
    void normalizesTemplateExpressionsToStringConcatenation() throws Exception {
        final Path sourceFile = tempDir.resolve("template-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                const message = `hello ${name} #${count}`;
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "BinaryExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
    }

    @Test
    void normalizesDestructuringDeclarationsAssignmentsAndParameters() throws Exception {
        final Path sourceFile = tempDir.resolve("destructuring-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                function pair([a, b]) {
                  return a + b;
                }
                const { x, y: alias } = { x: 1, y: 2 };
                let first = 0;
                let second = 0;
                [first, second] = [3, 4];
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"a\""));
        assertTrue(normalized.contains("\"name\":\"b\""));
        assertTrue(normalized.contains("\"name\":\"alias\""));
        assertTrue(normalized.contains("\"member\":\"0\""));
        assertTrue(normalized.contains("\"member\":\"1\""));
        assertTrue(normalized.contains("\"kind\":\"AssignmentStatement\""));
    }

    @Test
    void normalizesSpreadForArraysObjectsAndCallArguments() throws Exception {
        final Path sourceFile = tempDir.resolve("spread-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                const base = [2, 3];
                const combined = [1, ...base, 4];
                const merged = { ...{ a: 1 }, b: 2 };
                const total = fn(...combined);
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"__tsj_array_spread\""));
        assertTrue(normalized.contains("\"name\":\"__tsj_object_spread\""));
        assertTrue(normalized.contains("\"name\":\"__tsj_call_spread\""));
    }

    @Test
    void normalizesDefaultAndRestParameters() throws Exception {
        final Path sourceFile = tempDir.resolve("default-rest-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                function summary(a = 10, ...rest) {
                  return a;
                }
                const fn = (value = "x", ...parts) => value + parts.length;
                class Runner {
                  run(seed = 1, ...tail) {
                    return seed;
                  }
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"__tsj_rest_args\""));
        assertTrue(normalized.contains("\"operator\":\"===\""));
        assertTrue(normalized.contains("\"kind\":\"UndefinedLiteral\""));
        assertTrue(normalized.contains("\"name\":\"seed\""));
    }

    @Test
    void normalizesForOfAndForInLoops() throws Exception {
        final Path sourceFile = tempDir.resolve("for-loops-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                for (const value of [1, 2, 3]) {
                  console.log(value);
                }
                let key = "";
                for (key in { a: 1, b: 2 }) {
                  console.log(key);
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"__tsj_for_of_values\""));
        assertTrue(normalized.contains("\"name\":\"__tsj_for_in_keys\""));
        assertTrue(normalized.contains("\"name\":\"__tsj_index_read\""));
        assertTrue(normalized.contains("\"kind\":\"WhileStatement\""));
    }

    @Test
    void normalizesForLoopInitializersWithMultipleDeclarations() throws Exception {
        final Path sourceFile = tempDir.resolve("for-init-multi-decl-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                let total = 0;
                for (let i = 0, j = 10; i < 5; i++, j--) {
                  total = total + i + j;
                }
                console.log(total);
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"i\""));
        assertTrue(normalized.contains("\"name\":\"j\""));
        assertTrue(normalized.contains("\"operator\":\",\""));
    }

    @Test
    void normalizesClassFieldsPrivateNamesComputedKeysAndStaticBlocks() throws Exception {
        final Path sourceFile = tempDir.resolve("class-extended-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                class Demo {
                  #value = 1;
                  field = this.#value;
                  ["inc"](step) {
                    return this.#value + step;
                  }
                  static count = 2;
                  static {
                    Demo.count = Demo.count + 1;
                  }
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("__tsj_private_value"));
        assertTrue(normalized.contains("\"member\":\"count\""));
        assertTrue(normalized.contains("\"kind\":\"ClassDeclarationStatement\""));
    }

    @Test
    void normalizesImportsAndSkipsTypeOnlyAndAmbientDeclarations() throws Exception {
        final Path sourceFile = tempDir.resolve("imports-ambient-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                import defaultThing, { named as alias, type TypeOnly } from "pkg-a";
                import * as ns from "pkg-b";
                import type { PureType } from "pkg-c";

                type Alias = string;
                interface Shape {
                  id: string;
                }

                declare const ambient: number;
                declare function declaredFn(value: string): number;
                function declaredFn(value: string): number {
                  return value.length;
                }

                const value = defaultThing;
                void [alias, ns, ambient, value, declaredFn];
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"defaultThing\""));
        assertTrue(normalized.contains("\"name\":\"alias\""));
        assertTrue(normalized.contains("\"name\":\"ns\""));
        assertTrue(normalized.contains("\"name\":\"ambient\""));
        assertTrue(normalized.contains("\"name\":\"declaredFn\""));
        assertFalse(normalized.contains("TypeAliasDeclaration"));
        assertFalse(normalized.contains("InterfaceDeclaration"));
        assertFalse(normalized.contains("\"name\":\"PureType\""));
    }

    @Test
    void normalizesBigIntElementAccessAndExpressionWithTypeArguments() throws Exception {
        final Path sourceFile = tempDir.resolve("bigint-element-access-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                function id<T>(value: T): T {
                  return value;
                }

                const arr = [7, 8];
                const first = arr[0];
                const big = 123n;
                const instantiate = id<string>;
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "MemberAccessExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(2)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
        assertEquals(
                "123n",
                result.normalizedProgram()
                        .path("statements")
                        .get(3)
                        .path("expression")
                        .path("text")
                        .asText()
        );
        assertEquals(
                "VariableExpression",
                result.normalizedProgram()
                        .path("statements")
                        .get(4)
                        .path("expression")
                        .path("kind")
                        .asText()
        );
        assertEquals(
                "id",
                result.normalizedProgram()
                        .path("statements")
                        .get(4)
                        .path("expression")
                        .path("name")
                .asText()
        );
    }

    @Test
    void normalizesOptionalElementAccessAndDeleteExpressions() throws Exception {
        final Path sourceFile = tempDir.resolve("optional-element-delete-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                const arr = [10, 20, 30];
                const maybe: any = arr;
                const none: any = null;
                const idx = 1;
                const hit = maybe?.[idx];
                const miss = none?.[idx];
                const mutable: any = { a: { b: 1 } };
                delete mutable?.a?.b;
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"__tsj_optional_index_read\""));
        assertTrue(normalized.contains("\"operator\":\"delete\""));
        assertTrue(normalized.contains("\"kind\":\"OptionalMemberAccessExpression\""));
    }

    @Test
    void normalizesSuperMemberCallsIntoHelperExpression() throws Exception {
        final Path sourceFile = tempDir.resolve("super-member-call-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                class A {
                  greet(): string {
                    return "A";
                  }
                }

                class B extends A {
                  greet(): string {
                    return super.greet() + "B";
                  }
                }
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"__tsj_super_invoke\""));
        assertTrue(normalized.contains("\"text\":\"greet\""));
    }

    @Test
    void normalizesSatisfiesAndTypeAssertionExpressions() throws Exception {
        final Path sourceFile = tempDir.resolve("ts-only-expression-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                const fixed = 7 as const;
                const checked = { x: 1 } satisfies { x: number };
                const total = (<number>fixed) + (fixed as number);
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"name\":\"fixed\""));
        assertTrue(normalized.contains("\"kind\":\"BinaryExpression\""));
    }

    @Test
    void normalizesEnumsVoidReturnNonNullAndImplementsClauses() throws Exception {
        final Path sourceFile = tempDir.resolve("enum-void-nonnull-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                interface Runnable {
                  run(): void;
                }

                enum Flags {
                  Read = 1,
                  Write = 2,
                }

                abstract class Base {
                  abstract run(): void;
                }

                class Impl extends Base implements Runnable {
                  run(): void {
                    return;
                  }
                }

                declare const maybe: { value: number } | null;
                const value = maybe!.value;
                const mask = Flags.Read | Flags.Write;
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "VariableDeclaration",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("kind")
                        .asText()
        );
        assertEquals(
                "Flags",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("name")
                        .asText()
        );
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"kind\":\"UndefinedLiteral\""));
        assertTrue(normalized.contains("\"member\":\"value\""));
    }

    @Test
    void normalizesNamespaceExportsIntoRuntimeObjectDeclarations() throws Exception {
        final Path sourceFile = tempDir.resolve("namespace-normalized.ts");
        Files.writeString(
                sourceFile,
                """
                namespace StressSpace {
                  export interface Payload {
                    id: string;
                    value: string;
                  }

                  export const defaultPayload: Payload = { id: "a", value: "b" };
                  const hidden: Payload = { id: "x", value: "y" };
                }

                const payload = StressSpace.defaultPayload;
                """,
                UTF_8
        );

        final TypeScriptSyntaxBridge.BridgeResult result = new TypeScriptSyntaxBridge().tokenize(
                Files.readString(sourceFile, UTF_8),
                sourceFile
        );

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.normalizedProgram() != null && !result.normalizedProgram().isNull());
        assertEquals(
                "VariableDeclaration",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("kind")
                        .asText()
        );
        assertEquals(
                "StressSpace",
                result.normalizedProgram()
                        .path("statements")
                        .get(0)
                        .path("name")
                        .asText()
        );
        final String normalized = result.normalizedProgram().toString();
        assertTrue(normalized.contains("\"key\":\"defaultPayload\""));
        assertFalse(normalized.contains("\"key\":\"hidden\""));
    }

    private static void restoreSystemProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
