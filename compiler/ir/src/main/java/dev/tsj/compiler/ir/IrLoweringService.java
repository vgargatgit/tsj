package dev.tsj.compiler.ir;

import dev.tsj.compiler.frontend.FrontendAnalysisResult;
import dev.tsj.compiler.frontend.FrontendDiagnostic;
import dev.tsj.compiler.frontend.FrontendSourceFileSummary;
import dev.tsj.compiler.frontend.TypeScriptFrontendService;
import dev.tsj.compiler.ir.hir.HirModule;
import dev.tsj.compiler.ir.hir.HirProject;
import dev.tsj.compiler.ir.hir.HirStatement;
import dev.tsj.compiler.ir.jir.JirClass;
import dev.tsj.compiler.ir.jir.JirMethod;
import dev.tsj.compiler.ir.jir.JirProject;
import dev.tsj.compiler.ir.mir.MirAsyncFrame;
import dev.tsj.compiler.ir.mir.MirAsyncState;
import dev.tsj.compiler.ir.mir.MirAsyncSuspendPoint;
import dev.tsj.compiler.ir.mir.MirBasicBlock;
import dev.tsj.compiler.ir.mir.MirCapture;
import dev.tsj.compiler.ir.mir.MirControlFlowEdge;
import dev.tsj.compiler.ir.mir.MirFunction;
import dev.tsj.compiler.ir.mir.MirInstruction;
import dev.tsj.compiler.ir.mir.MirProject;
import dev.tsj.compiler.ir.mir.MirScope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Lowers frontend analysis into HIR/MIR/JIR bootstrap IR tiers.
 */
public final class IrLoweringService {
    private static final String MIR_OP_ASYNC_STATE = "ASYNC_STATE";
    private static final String MIR_OP_ASYNC_SUSPEND = "ASYNC_SUSPEND";
    private static final String MIR_OP_ASYNC_RESUME = "ASYNC_RESUME";
    private static final String MIR_OP_IF_CONDITION = "IF_CONDITION";
    private static final String MIR_OP_WHILE_CONDITION = "WHILE_CONDITION";
    private static final String MIR_OP_BREAK = "BREAK";
    private static final String MIR_OP_CONTINUE = "CONTINUE";
    private static final String MIR_OP_TRY_BEGIN = "TRY_BEGIN";
    private static final String MIR_OP_CATCH_BEGIN = "CATCH_BEGIN";
    private static final String MIR_OP_FINALLY_BEGIN = "FINALLY_BEGIN";

    private static final Pattern VARIABLE_DECLARATION_PATTERN = Pattern.compile(
            "^(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?::\\s*[^=]+)?=\\s*(.+);$"
    );
    private static final Pattern PRINT_PATTERN = Pattern.compile("^console\\.log\\((.+)\\);$");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+.+;$");
    private static final Pattern ASYNC_FUNCTION_DECLARATION_PATTERN = Pattern.compile(
            "^async\\s+function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*\\{?$"
    );
    private static final Pattern FUNCTION_DECLARATION_PATTERN = Pattern.compile(
            "^function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*\\{?$"
    );
    private static final Pattern IF_PATTERN = Pattern.compile("^if\\s*\\((.+)\\)\\s*\\{?$");
    private static final Pattern WHILE_PATTERN = Pattern.compile("^while\\s*\\((.+)\\)\\s*\\{?$");
    private static final Pattern BREAK_PATTERN = Pattern.compile("^break\\s*;$");
    private static final Pattern CONTINUE_PATTERN = Pattern.compile("^continue\\s*;$");
    private static final Pattern TRY_PATTERN = Pattern.compile("^try\\s*\\{?$");
    private static final Pattern CATCH_PATTERN = Pattern.compile("^\\}?\\s*catch\\s*\\([^)]*\\)\\s*\\{?$");
    private static final Pattern FINALLY_PATTERN = Pattern.compile("^\\}?\\s*finally\\s*\\{?$");
    private static final Pattern AWAIT_CAPTURE_PATTERN = Pattern.compile("\\bawait\\b\\s*(.+)$");
    private static final Pattern RETURN_PATTERN = Pattern.compile("^return\\s+(.+);$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Set<String> IDENTIFIER_SKIP_LIST = Set.of(
            "const", "let", "var", "function", "return", "import", "from", "as",
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "new", "true", "false", "null", "undefined", "console", "log",
            "async", "await", "try", "catch", "finally", "throw"
    );

    private final Path workspaceRoot;

    public IrLoweringService(final Path workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot")
                .toAbsolutePath()
                .normalize();
    }

    public IrProject lowerProject(final Path tsconfigPath) {
        final Path repositoryRoot = resolveRepositoryRoot(workspaceRoot);
        final TypeScriptFrontendService frontendService = new TypeScriptFrontendService(repositoryRoot);
        final FrontendAnalysisResult frontendResult = frontendService.analyzeProject(tsconfigPath);

        final List<IrDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(convertFrontendDiagnostics(frontendResult.diagnostics()));

        final HirProject hirProject = buildHir(frontendResult, diagnostics);
        final MirProject mirProject = buildMir(hirProject);
        final JirProject jirProject = buildJir(mirProject);
        return new IrProject(hirProject, mirProject, jirProject, diagnostics);
    }

    private static Path resolveRepositoryRoot(final Path startPath) {
        Path current = startPath;
        while (current != null) {
            final Path marker = current.resolve("compiler/frontend/ts-bridge/analyze-project.cjs");
            if (Files.exists(marker)) {
                return current;
            }
            current = current.getParent();
        }
        return startPath;
    }

    private static List<IrDiagnostic> convertFrontendDiagnostics(final List<FrontendDiagnostic> frontendDiagnostics) {
        final List<IrDiagnostic> diagnostics = new ArrayList<>();
        for (FrontendDiagnostic diagnostic : frontendDiagnostics) {
            diagnostics.add(new IrDiagnostic(
                    "FRONTEND",
                    diagnostic.code(),
                    diagnostic.category(),
                    diagnostic.message(),
                    diagnostic.filePath(),
                    diagnostic.line(),
                    diagnostic.column()
            ));
        }
        return diagnostics;
    }

    private static HirProject buildHir(
            final FrontendAnalysisResult frontendResult,
            final List<IrDiagnostic> diagnostics
    ) {
        final List<HirModule> modules = new ArrayList<>();
        for (FrontendSourceFileSummary sourceFile : frontendResult.sourceFiles()) {
            final Path sourcePath = Path.of(sourceFile.path());
            final List<HirStatement> statements = parseStatements(sourcePath, diagnostics);
            modules.add(new HirModule(sourcePath.toString(), statements));
        }
        return new HirProject(frontendResult.tsconfigPath().toString(), modules);
    }

    private static List<HirStatement> parseStatements(final Path sourcePath, final List<IrDiagnostic> diagnostics) {
        final List<HirStatement> statements = new ArrayList<>();
        final List<String> lines;
        try {
            lines = Files.readAllLines(sourcePath, UTF_8);
        } catch (final IOException ioException) {
            diagnostics.add(new IrDiagnostic(
                    "HIR",
                    "TSJ-IR-500",
                    "Error",
                    "Failed to read source file for HIR lowering: " + ioException.getMessage(),
                    sourcePath.toString(),
                    null,
                    null
            ));
            return statements;
        }

        for (int i = 0; i < lines.size(); i++) {
            final int line = i + 1;
            final String rawLine = lines.get(i).trim();
            if (rawLine.isBlank() || rawLine.equals("{") || rawLine.equals("}")) {
                continue;
            }

            final Matcher variableMatcher = VARIABLE_DECLARATION_PATTERN.matcher(rawLine);
            if (variableMatcher.matches()) {
                statements.add(new HirStatement(
                        "VAR_DECL",
                        variableMatcher.group(1),
                        variableMatcher.group(2).trim(),
                        line
                ));
                continue;
            }

            final Matcher printMatcher = PRINT_PATTERN.matcher(rawLine);
            if (printMatcher.matches()) {
                statements.add(new HirStatement("PRINT", null, printMatcher.group(1).trim(), line));
                continue;
            }

            final Matcher asyncFunctionMatcher = ASYNC_FUNCTION_DECLARATION_PATTERN.matcher(rawLine);
            if (asyncFunctionMatcher.matches()) {
                statements.add(new HirStatement("ASYNC_FUNCTION_DECL", asyncFunctionMatcher.group(1), rawLine, line));
                continue;
            }

            final Matcher functionMatcher = FUNCTION_DECLARATION_PATTERN.matcher(rawLine);
            if (functionMatcher.matches()) {
                statements.add(new HirStatement("FUNCTION_DECL", functionMatcher.group(1), rawLine, line));
                continue;
            }

            final Matcher returnMatcher = RETURN_PATTERN.matcher(rawLine);
            if (returnMatcher.matches()) {
                statements.add(new HirStatement("RETURN", null, returnMatcher.group(1).trim(), line));
                continue;
            }

            if (IMPORT_PATTERN.matcher(rawLine).matches()) {
                statements.add(new HirStatement("IMPORT", null, rawLine, line));
                continue;
            }

            diagnostics.add(new IrDiagnostic(
                    "HIR",
                    "TSJ-IR-001",
                    "Warning",
                    "Unsupported statement in TSJ-6 subset: " + rawLine,
                    sourcePath.toString(),
                    line,
                    1
            ));
        }
        return statements;
    }

    private static MirProject buildMir(final HirProject hirProject) {
        final List<MirFunction> functions = new ArrayList<>();
        for (HirModule module : hirProject.modules()) {
            functions.addAll(buildMirFunctionsForModule(module));
        }
        return new MirProject(functions);
    }

    private static List<MirFunction> buildMirFunctionsForModule(final HirModule module) {
        final Path sourcePath = Path.of(module.path());
        final List<String> lines;
        try {
            lines = Files.readAllLines(sourcePath, UTF_8);
        } catch (final IOException ioException) {
            final String functionName = sanitizeModuleName(module.path()) + "__init";
            final MirFunction fallback = createMirFunction(
                    functionName,
                    List.of(new MirInstruction("UNSUPPORTED", List.of("/* read-error */"), 1)),
                    new ScopeContext("scope0", null, functionName),
                    false,
                    null
            );
            return List.of(fallback);
        }

        final String moduleInitFunction = sanitizeModuleName(module.path()) + "__init";
        final MirModuleContext context = new MirModuleContext(moduleInitFunction);

        for (int i = 0; i < lines.size(); i++) {
            final int lineNumber = i + 1;
            final String rawLine = lines.get(i);
            final String trimmed = rawLine.trim();

            if (trimmed.isBlank()) {
                context.finishLine(trimmed);
                continue;
            }

            boolean handled = false;
            if (isBraceOnlyLine(trimmed)) {
                handled = true;
            } else {
                final Matcher importMatcher = IMPORT_PATTERN.matcher(trimmed);
                if (importMatcher.matches()) {
                    context.addInstruction(new MirInstruction("IMPORT", List.of(trimmed), lineNumber));
                    handled = true;
                }

                if (!handled) {
                    final Matcher asyncFunctionMatcher = ASYNC_FUNCTION_DECLARATION_PATTERN.matcher(trimmed);
                    if (asyncFunctionMatcher.matches()) {
                        final String declaredName = asyncFunctionMatcher.group(1).trim();
                        final String parameterText = asyncFunctionMatcher.group(2).trim();
                        context.enterFunction(declaredName, parseParameterNames(parameterText), lineNumber, true);
                        handled = true;
                    }
                }

                if (!handled) {
                    final Matcher functionMatcher = FUNCTION_DECLARATION_PATTERN.matcher(trimmed);
                    if (functionMatcher.matches()) {
                        final String declaredName = functionMatcher.group(1).trim();
                        final String parameterText = functionMatcher.group(2).trim();
                        context.enterFunction(declaredName, parseParameterNames(parameterText), lineNumber, false);
                        handled = true;
                    }
                }

                if (!handled) {
                    final Matcher variableMatcher = VARIABLE_DECLARATION_PATTERN.matcher(trimmed);
                    if (variableMatcher.matches()) {
                        final String variableName = variableMatcher.group(1).trim();
                        final String expression = variableMatcher.group(2).trim();
                        context.declareLocal(variableName);
                        context.trackCaptures(expression, lineNumber);
                        context.addInstruction(new MirInstruction("CONST", List.of(variableName, expression), lineNumber));
                        context.trackAsyncAwait(expression, lineNumber);
                        handled = true;
                    }
                }

                if (!handled) {
                    final Matcher printMatcher = PRINT_PATTERN.matcher(trimmed);
                    if (printMatcher.matches()) {
                        final String expression = printMatcher.group(1).trim();
                        context.trackCaptures(expression, lineNumber);
                        context.addInstruction(new MirInstruction("PRINT", List.of(expression), lineNumber));
                        context.trackAsyncAwait(expression, lineNumber);
                        handled = true;
                    }
                }

                if (!handled) {
                    final Matcher ifMatcher = IF_PATTERN.matcher(trimmed);
                    if (ifMatcher.matches()) {
                        final String condition = ifMatcher.group(1).trim();
                        context.trackCaptures(condition, lineNumber);
                        context.addInstruction(new MirInstruction(MIR_OP_IF_CONDITION, List.of(condition), lineNumber));
                        context.trackAsyncAwait(condition, lineNumber);
                        handled = true;
                    }
                }

                if (!handled) {
                    final Matcher whileMatcher = WHILE_PATTERN.matcher(trimmed);
                    if (whileMatcher.matches()) {
                        final String condition = whileMatcher.group(1).trim();
                        context.trackCaptures(condition, lineNumber);
                        context.addInstruction(new MirInstruction(
                                MIR_OP_WHILE_CONDITION,
                                List.of(condition),
                                lineNumber
                        ));
                        context.trackAsyncAwait(condition, lineNumber);
                        handled = true;
                    }
                }

                if (!handled && BREAK_PATTERN.matcher(trimmed).matches()) {
                    context.addInstruction(new MirInstruction(MIR_OP_BREAK, List.of(), lineNumber));
                    context.recordAsyncTerminal(MIR_OP_BREAK);
                    handled = true;
                }

                if (!handled && CONTINUE_PATTERN.matcher(trimmed).matches()) {
                    context.addInstruction(new MirInstruction(MIR_OP_CONTINUE, List.of(), lineNumber));
                    context.recordAsyncTerminal(MIR_OP_CONTINUE);
                    handled = true;
                }

                if (!handled && TRY_PATTERN.matcher(trimmed).matches()) {
                    context.addInstruction(new MirInstruction(MIR_OP_TRY_BEGIN, List.of(), lineNumber));
                    handled = true;
                }

                if (!handled && CATCH_PATTERN.matcher(trimmed).matches()) {
                    context.addInstruction(new MirInstruction(MIR_OP_CATCH_BEGIN, List.of(), lineNumber));
                    handled = true;
                }

                if (!handled && FINALLY_PATTERN.matcher(trimmed).matches()) {
                    context.addInstruction(new MirInstruction(MIR_OP_FINALLY_BEGIN, List.of(), lineNumber));
                    handled = true;
                }

                if (!handled) {
                    final Matcher returnMatcher = RETURN_PATTERN.matcher(trimmed);
                    if (returnMatcher.matches()) {
                        final String expression = returnMatcher.group(1).trim();
                        context.trackCaptures(expression, lineNumber);
                        context.addInstruction(new MirInstruction("RETURN", List.of(expression), lineNumber));
                        context.trackAsyncAwait(expression, lineNumber);
                        context.recordAsyncTerminal("RETURN");
                        handled = true;
                    }
                }

                if (!handled && trimmed.startsWith("throw ")) {
                    final String expression = trimExpressionSuffix(trimmed.substring("throw ".length()));
                    context.trackCaptures(expression, lineNumber);
                    context.addInstruction(new MirInstruction("THROW", List.of(expression), lineNumber));
                    context.trackAsyncAwait(expression, lineNumber);
                    context.recordAsyncTerminal("THROW");
                    handled = true;
                }
            }

            if (!handled) {
                context.trackCaptures(trimmed, lineNumber);
                context.addInstruction(new MirInstruction("UNSUPPORTED", List.of(trimmed), lineNumber));
                context.trackAsyncAwait(trimmed, lineNumber);
            }

            context.finishLine(trimmed);
        }

        return context.toMirFunctions();
    }

    private static boolean isBraceOnlyLine(final String line) {
        final String normalized = line.replace(";", "").trim();
        if (normalized.isBlank()) {
            return true;
        }
        for (int i = 0; i < normalized.length(); i++) {
            final char current = normalized.charAt(i);
            if (current != '{' && current != '}') {
                return false;
            }
        }
        return true;
    }

    private static int braceDelta(final String line) {
        int delta = 0;
        for (int i = 0; i < line.length(); i++) {
            final char current = line.charAt(i);
            if (current == '{') {
                delta++;
            } else if (current == '}') {
                delta--;
            }
        }
        return delta;
    }

    private static String trimExpressionSuffix(final String expression) {
        String normalized = expression.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static List<String> extractAwaitExpressions(final String expression) {
        final List<String> awaitExpressions = new ArrayList<>();
        final Matcher matcher = AWAIT_CAPTURE_PATTERN.matcher(expression);
        while (matcher.find()) {
            final String captured = trimExpressionSuffix(matcher.group(1));
            if (!captured.isBlank()) {
                awaitExpressions.add(captured);
            }
        }
        return awaitExpressions;
    }

    private static List<String> parseParameterNames(final String parameterText) {
        if (parameterText.isBlank()) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        final String[] parts = parameterText.split(",");
        for (String part : parts) {
            final String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String name = trimmed;
            final int colonIndex = name.indexOf(':');
            if (colonIndex >= 0) {
                name = name.substring(0, colonIndex);
            }
            final int equalsIndex = name.indexOf('=');
            if (equalsIndex >= 0) {
                name = name.substring(0, equalsIndex);
            }
            name = name.trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private static MirFunction createMirFunction(
            final String functionName,
            final List<MirInstruction> instructions,
            final ScopeContext scopeContext,
            final boolean async,
            final MirAsyncFrame asyncFrame
    ) {
        final String entryBlock = functionName + "_B0";
        final String exitBlock = functionName + "_B1";
        final List<MirBasicBlock> blocks = List.of(
                new MirBasicBlock(entryBlock, instructions),
                new MirBasicBlock(exitBlock, List.of())
        );
        final List<MirControlFlowEdge> cfgEdges = List.of(
                new MirControlFlowEdge(entryBlock, exitBlock, "FALLTHROUGH")
        );
        final List<MirScope> scopes = List.of(new MirScope(
                scopeContext.scopeId,
                scopeContext.parentScopeId,
                functionName,
                List.copyOf(scopeContext.locals),
                List.copyOf(scopeContext.captured)
        ));
        final List<MirCapture> captures = List.copyOf(scopeContext.captures);
        return new MirFunction(functionName, instructions, blocks, cfgEdges, scopes, captures, async, asyncFrame);
    }

    private static JirProject buildJir(final MirProject mirProject) {
        final List<JirClass> classes = new ArrayList<>();
        for (MirFunction function : mirProject.functions()) {
            final String className = "tsj/gen/" + toPascalCase(sanitizeModuleName(function.name())) + "Fn";
            final List<String> bytecodeOps = new ArrayList<>();
            for (MirInstruction instruction : function.instructions()) {
                switch (instruction.op()) {
                    case "CONST" -> {
                        bytecodeOps.add("LDC " + instruction.args().get(1));
                        bytecodeOps.add("PUTSTATIC " + instruction.args().get(0));
                    }
                    case "PRINT" -> {
                        bytecodeOps.add("GETSTATIC java/lang/System/out");
                        bytecodeOps.add("LDC " + instruction.args().getFirst());
                        bytecodeOps.add("INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V");
                    }
                    case "IMPORT" -> bytecodeOps.add("// IMPORT " + instruction.args().getFirst());
                    case "RETURN" -> bytecodeOps.add("RETURN " + instruction.args().getFirst());
                    case "FUNCTION_DEF" -> bytecodeOps.add("// FUNCTION_DEF " + instruction.args().getFirst());
                    case MIR_OP_ASYNC_STATE ->
                            bytecodeOps.add("// ASYNC_STATE pc=" + instruction.args().getFirst());
                    case MIR_OP_ASYNC_SUSPEND ->
                            bytecodeOps.add("// ASYNC_SUSPEND " + instruction.args().getFirst());
                    case MIR_OP_ASYNC_RESUME ->
                            bytecodeOps.add("// ASYNC_RESUME pc=" + instruction.args().getFirst());
                    case MIR_OP_IF_CONDITION ->
                            bytecodeOps.add("// IF_CONDITION " + instruction.args().getFirst());
                    case MIR_OP_WHILE_CONDITION ->
                            bytecodeOps.add("// WHILE_CONDITION " + instruction.args().getFirst());
                    case MIR_OP_BREAK -> bytecodeOps.add("// BREAK");
                    case MIR_OP_CONTINUE -> bytecodeOps.add("// CONTINUE");
                    case MIR_OP_TRY_BEGIN -> bytecodeOps.add("// TRY_BEGIN");
                    case MIR_OP_CATCH_BEGIN -> bytecodeOps.add("// CATCH_BEGIN");
                    case MIR_OP_FINALLY_BEGIN -> bytecodeOps.add("// FINALLY_BEGIN");
                    case "THROW" -> bytecodeOps.add("// THROW " + instruction.args().getFirst());
                    default -> bytecodeOps.add("// UNSUPPORTED " + instruction.args().getFirst());
                }
            }
            final List<String> asyncStateOps = buildJirAsyncStateOps(function.asyncFrame());
            final JirMethod method = new JirMethod(function.name(), bytecodeOps, function.async(), asyncStateOps);
            classes.add(new JirClass(className, List.of(method)));
        }
        return new JirProject(classes);
    }

    private static List<String> buildJirAsyncStateOps(final MirAsyncFrame asyncFrame) {
        if (asyncFrame == null) {
            return List.of();
        }
        final List<String> operations = new ArrayList<>();
        for (MirAsyncState state : asyncFrame.states()) {
            operations.add("STATE pc=" + state.pc() + " kind=" + state.kind() + " line=" + state.line());
        }
        for (MirAsyncSuspendPoint suspendPoint : asyncFrame.suspendPoints()) {
            operations.add(
                    "SUSPEND pc=" + suspendPoint.suspendPc()
                            + " resume=" + suspendPoint.resumePc()
                            + " await=" + suspendPoint.awaitedExpression()
            );
            operations.add("RESUME pc=" + suspendPoint.resumePc());
        }
        for (String terminalOp : asyncFrame.terminalOps()) {
            operations.add("TERMINAL " + terminalOp);
        }
        return List.copyOf(operations);
    }

    private static final class MirModuleContext {
        private final Map<String, FunctionBuilder> functionBuilders;
        private final Deque<ScopeContext> scopeStack;

        private MirModuleContext(final String moduleInitFunction) {
            this.functionBuilders = new LinkedHashMap<>();
            this.scopeStack = new ArrayDeque<>();
            final ScopeContext rootScope = new ScopeContext("scope0", null, moduleInitFunction);
            final FunctionBuilder rootBuilder = new FunctionBuilder(moduleInitFunction, rootScope, false, 1);
            functionBuilders.put(moduleInitFunction, rootBuilder);
            scopeStack.push(rootScope);
        }

        private void addInstruction(final MirInstruction instruction) {
            currentBuilder().instructions.add(instruction);
        }

        private void declareLocal(final String localName) {
            currentScope().locals.add(localName);
        }

        private void enterFunction(
                final String declaredName,
                final List<String> parameters,
                final int lineNumber,
                final boolean asyncFunction
        ) {
            final ScopeContext parentScope = currentScope();
            parentScope.locals.add(declaredName);

            final String functionName = qualifyFunctionName(parentScope.functionName, declaredName);
            addInstruction(new MirInstruction("FUNCTION_DEF", List.of(functionName), lineNumber));

            final String scopeId = "scope" + functionBuilders.size();
            final ScopeContext childScope = new ScopeContext(scopeId, parentScope.scopeId, functionName);
            childScope.locals.addAll(parameters);
            final FunctionBuilder childBuilder = new FunctionBuilder(
                    functionName,
                    childScope,
                    asyncFunction,
                    lineNumber
            );
            functionBuilders.put(functionName, childBuilder);
            scopeStack.push(childScope);
        }

        private void trackCaptures(final String expression, final int lineNumber) {
            final String scrubbed = expression
                    .replaceAll("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"", " ")
                    .replaceAll("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'", " ");
            final Matcher matcher = IDENTIFIER_PATTERN.matcher(scrubbed);
            while (matcher.find()) {
                final String identifier = matcher.group();
                if (IDENTIFIER_SKIP_LIST.contains(identifier)) {
                    continue;
                }
                final ScopeContext current = currentScope();
                if (current.locals.contains(identifier)) {
                    continue;
                }
                final ScopeContext declaringScope = findDeclaringScope(identifier);
                if (declaringScope == null || declaringScope.scopeId.equals(current.scopeId)) {
                    continue;
                }
                current.captured.add(identifier);
                final MirCapture capture = new MirCapture(
                        current.functionName,
                        identifier,
                        declaringScope.scopeId,
                        current.scopeId
                );
                current.captures.add(capture);
                currentBuilder().instructions.add(new MirInstruction(
                        "CAPTURE",
                        List.of(identifier, declaringScope.scopeId),
                        lineNumber
                ));
            }
        }

        private void trackAsyncAwait(final String expression, final int lineNumber) {
            final FunctionBuilder builder = currentBuilder();
            if (!builder.async || builder.asyncFrameBuilder == null) {
                return;
            }
            for (String awaitExpression : extractAwaitExpressions(expression)) {
                builder.asyncFrameBuilder.addSuspendPoint(builder.instructions, awaitExpression, lineNumber);
            }
        }

        private void recordAsyncTerminal(final String operation) {
            final FunctionBuilder builder = currentBuilder();
            if (!builder.async || builder.asyncFrameBuilder == null) {
                return;
            }
            builder.asyncFrameBuilder.recordTerminal(operation);
        }

        private void finishLine(final String line) {
            final ScopeContext scope = currentScope();
            if (scope == null || scope.parentScopeId == null) {
                return;
            }
            scope.braceDepth += braceDelta(line);
            while (scopeStack.size() > 1 && currentScope().braceDepth <= 0) {
                scopeStack.pop();
            }
        }

        private ScopeContext findDeclaringScope(final String identifier) {
            for (ScopeContext scopeContext : scopeStack) {
                if (scopeContext.locals.contains(identifier)) {
                    return scopeContext;
                }
            }
            return null;
        }

        private ScopeContext currentScope() {
            return scopeStack.peek();
        }

        private FunctionBuilder currentBuilder() {
            return functionBuilders.get(currentScope().functionName);
        }

        private List<MirFunction> toMirFunctions() {
            final List<MirFunction> functions = new ArrayList<>();
            for (FunctionBuilder builder : functionBuilders.values()) {
                final MirAsyncFrame asyncFrame = builder.buildAsyncFrame();
                functions.add(createMirFunction(
                        builder.name,
                        List.copyOf(builder.instructions),
                        builder.scopeContext,
                        builder.async,
                        asyncFrame
                ));
            }
            return functions;
        }
    }

    private static String qualifyFunctionName(final String parentFunctionName, final String declaredName) {
        if (parentFunctionName.endsWith("__init")) {
            return declaredName;
        }
        return parentFunctionName + "$" + declaredName;
    }

    private static final class FunctionBuilder {
        private final String name;
        private final ScopeContext scopeContext;
        private final List<MirInstruction> instructions;
        private final boolean async;
        private final AsyncFrameBuilder asyncFrameBuilder;

        private FunctionBuilder(
                final String name,
                final ScopeContext scopeContext,
                final boolean async,
                final int declarationLine
        ) {
            this.name = name;
            this.scopeContext = scopeContext;
            this.instructions = new ArrayList<>();
            this.async = async;
            this.asyncFrameBuilder = async ? new AsyncFrameBuilder(declarationLine) : null;
        }

        private MirAsyncFrame buildAsyncFrame() {
            if (!async || asyncFrameBuilder == null) {
                return null;
            }
            final int exitLine;
            if (instructions.isEmpty()) {
                exitLine = asyncFrameBuilder.entryLine;
            } else {
                exitLine = instructions.get(instructions.size() - 1).line();
            }
            return asyncFrameBuilder.finish(exitLine);
        }
    }

    private static final class AsyncFrameBuilder {
        private final List<MirAsyncState> states;
        private final List<MirAsyncSuspendPoint> suspendPoints;
        private final Set<String> terminalOps;
        private final int entryLine;
        private int nextPc;
        private boolean exitRecorded;

        private AsyncFrameBuilder(final int declarationLine) {
            this.states = new ArrayList<>();
            this.suspendPoints = new ArrayList<>();
            this.terminalOps = new LinkedHashSet<>();
            this.entryLine = Math.max(1, declarationLine);
            this.nextPc = 1;
            this.exitRecorded = false;
            states.add(new MirAsyncState(0, "ENTRY", this.entryLine));
        }

        private void addSuspendPoint(
                final List<MirInstruction> instructions,
                final String awaitedExpression,
                final int lineNumber
        ) {
            final int line = Math.max(1, lineNumber);
            final int suspendPc = nextPc++;
            final int resumePc = nextPc++;
            states.add(new MirAsyncState(suspendPc, "SUSPEND", line));
            states.add(new MirAsyncState(resumePc, "RESUME", line));
            suspendPoints.add(new MirAsyncSuspendPoint(suspendPc, resumePc, awaitedExpression, line));
            instructions.add(new MirInstruction(MIR_OP_ASYNC_STATE, List.of(Integer.toString(suspendPc)), line));
            instructions.add(new MirInstruction(MIR_OP_ASYNC_SUSPEND, List.of(awaitedExpression), line));
            instructions.add(new MirInstruction(MIR_OP_ASYNC_STATE, List.of(Integer.toString(resumePc)), line));
            instructions.add(new MirInstruction(MIR_OP_ASYNC_RESUME, List.of(Integer.toString(resumePc)), line));
        }

        private void recordTerminal(final String operation) {
            terminalOps.add(operation);
        }

        private MirAsyncFrame finish(final int exitLineValue) {
            if (!exitRecorded) {
                states.add(new MirAsyncState(nextPc, "EXIT", Math.max(entryLine, exitLineValue)));
                exitRecorded = true;
            }
            return new MirAsyncFrame(
                    List.copyOf(states),
                    List.copyOf(suspendPoints),
                    List.copyOf(terminalOps)
            );
        }
    }

    private static final class ScopeContext {
        private final String scopeId;
        private final String parentScopeId;
        private final String functionName;
        private final Set<String> locals;
        private final Set<String> captured;
        private final Set<MirCapture> captures;
        private int braceDepth;

        private ScopeContext(final String scopeId, final String parentScopeId, final String functionName) {
            this.scopeId = scopeId;
            this.parentScopeId = parentScopeId;
            this.functionName = functionName;
            this.locals = new LinkedHashSet<>();
            this.captured = new LinkedHashSet<>();
            this.captures = new LinkedHashSet<>();
            this.braceDepth = parentScopeId == null ? Integer.MAX_VALUE : 0;
        }
    }

    private static String sanitizeModuleName(final String pathValue) {
        final String fileName = Path.of(pathValue).getFileName().toString();
        int endIndex = fileName.lastIndexOf('.');
        if (endIndex <= 0) {
            endIndex = fileName.length();
        }
        final String raw = fileName.substring(0, endIndex);
        return raw.replaceAll("[^A-Za-z0-9_$]", "_");
    }

    private static String toPascalCase(final String value) {
        final String[] parts = value.split("[_\\-]+");
        final StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        if (builder.isEmpty()) {
            return "Module";
        }
        return builder.toString();
    }
}
