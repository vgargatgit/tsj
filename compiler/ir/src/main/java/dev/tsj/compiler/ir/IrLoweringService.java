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
    private static final Pattern VARIABLE_DECLARATION_PATTERN = Pattern.compile(
            "^(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?::\\s*[^=]+)?=\\s*(.+);$"
    );
    private static final Pattern PRINT_PATTERN = Pattern.compile("^console\\.log\\((.+)\\);$");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+.+;$");
    private static final Pattern FUNCTION_DECLARATION_PATTERN = Pattern.compile(
            "^function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*\\{?$"
    );
    private static final Pattern RETURN_PATTERN = Pattern.compile("^return\\s+(.+);$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Set<String> IDENTIFIER_SKIP_LIST = Set.of(
            "const", "let", "var", "function", "return", "import", "from", "as",
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "new", "true", "false", "null", "undefined", "console", "log"
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
                    new ScopeContext("scope0", null, functionName)
            );
            return List.of(fallback);
        }

        final String moduleInitFunction = sanitizeModuleName(module.path()) + "__init";
        final MirModuleContext context = new MirModuleContext(moduleInitFunction);

        for (int i = 0; i < lines.size(); i++) {
            final int lineNumber = i + 1;
            final String rawLine = lines.get(i);
            final String trimmed = rawLine.trim();
            if (trimmed.isBlank() || "{".equals(trimmed)) {
                continue;
            }

            if (startsWithCloseBrace(trimmed)) {
                context.popScopes(countCloseBraces(trimmed));
                continue;
            }

            final Matcher importMatcher = IMPORT_PATTERN.matcher(trimmed);
            if (importMatcher.matches()) {
                context.addInstruction(new MirInstruction("IMPORT", List.of(trimmed), lineNumber));
                continue;
            }

            final Matcher functionMatcher = FUNCTION_DECLARATION_PATTERN.matcher(trimmed);
            if (functionMatcher.matches()) {
                final String declaredName = functionMatcher.group(1).trim();
                final String parameterText = functionMatcher.group(2).trim();
                context.enterFunction(declaredName, parseParameterNames(parameterText), lineNumber);
                continue;
            }

            final Matcher variableMatcher = VARIABLE_DECLARATION_PATTERN.matcher(trimmed);
            if (variableMatcher.matches()) {
                final String variableName = variableMatcher.group(1).trim();
                final String expression = variableMatcher.group(2).trim();
                context.declareLocal(variableName);
                context.trackCaptures(expression, lineNumber);
                context.addInstruction(new MirInstruction("CONST", List.of(variableName, expression), lineNumber));
                continue;
            }

            final Matcher printMatcher = PRINT_PATTERN.matcher(trimmed);
            if (printMatcher.matches()) {
                final String expression = printMatcher.group(1).trim();
                context.trackCaptures(expression, lineNumber);
                context.addInstruction(new MirInstruction("PRINT", List.of(expression), lineNumber));
                continue;
            }

            final Matcher returnMatcher = RETURN_PATTERN.matcher(trimmed);
            if (returnMatcher.matches()) {
                final String expression = returnMatcher.group(1).trim();
                context.trackCaptures(expression, lineNumber);
                context.addInstruction(new MirInstruction("RETURN", List.of(expression), lineNumber));
                continue;
            }

            context.trackCaptures(trimmed, lineNumber);
            context.addInstruction(new MirInstruction("UNSUPPORTED", List.of(trimmed), lineNumber));
        }

        return context.toMirFunctions();
    }

    private static boolean startsWithCloseBrace(final String line) {
        return !line.isBlank() && line.charAt(0) == '}';
    }

    private static int countCloseBraces(final String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '}') {
                count++;
            }
        }
        return count;
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
            final ScopeContext scopeContext
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
        return new MirFunction(functionName, instructions, blocks, cfgEdges, scopes, captures);
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
                    default -> bytecodeOps.add("// UNSUPPORTED " + instruction.args().getFirst());
                }
            }
            final JirMethod method = new JirMethod(function.name(), bytecodeOps);
            classes.add(new JirClass(className, List.of(method)));
        }
        return new JirProject(classes);
    }

    private static final class MirModuleContext {
        private final Map<String, FunctionBuilder> functionBuilders;
        private final Deque<ScopeContext> scopeStack;

        private MirModuleContext(final String moduleInitFunction) {
            this.functionBuilders = new LinkedHashMap<>();
            this.scopeStack = new ArrayDeque<>();
            final ScopeContext rootScope = new ScopeContext("scope0", null, moduleInitFunction);
            final FunctionBuilder rootBuilder = new FunctionBuilder(moduleInitFunction, rootScope);
            functionBuilders.put(moduleInitFunction, rootBuilder);
            scopeStack.push(rootScope);
        }

        private void addInstruction(final MirInstruction instruction) {
            currentBuilder().instructions.add(instruction);
        }

        private void declareLocal(final String localName) {
            currentScope().locals.add(localName);
        }

        private void enterFunction(final String declaredName, final List<String> parameters, final int lineNumber) {
            final ScopeContext parentScope = currentScope();
            parentScope.locals.add(declaredName);

            final String functionName = qualifyFunctionName(parentScope.functionName, declaredName);
            addInstruction(new MirInstruction("FUNCTION_DEF", List.of(functionName), lineNumber));

            final String scopeId = "scope" + functionBuilders.size();
            final ScopeContext childScope = new ScopeContext(scopeId, parentScope.scopeId, functionName);
            childScope.locals.addAll(parameters);
            final FunctionBuilder childBuilder = new FunctionBuilder(functionName, childScope);
            functionBuilders.put(functionName, childBuilder);
            scopeStack.push(childScope);
        }

        private void popScopes(final int count) {
            for (int i = 0; i < count; i++) {
                if (scopeStack.size() <= 1) {
                    return;
                }
                scopeStack.pop();
            }
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
                functions.add(createMirFunction(
                        builder.name,
                        List.copyOf(builder.instructions),
                        builder.scopeContext
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

        private FunctionBuilder(final String name, final ScopeContext scopeContext) {
            this.name = name;
            this.scopeContext = scopeContext;
            this.instructions = new ArrayList<>();
        }
    }

    private static final class ScopeContext {
        private final String scopeId;
        private final String parentScopeId;
        private final String functionName;
        private final Set<String> locals;
        private final Set<String> captured;
        private final Set<MirCapture> captures;

        private ScopeContext(final String scopeId, final String parentScopeId, final String functionName) {
            this.scopeId = scopeId;
            this.parentScopeId = parentScopeId;
            this.functionName = functionName;
            this.locals = new LinkedHashSet<>();
            this.captured = new LinkedHashSet<>();
            this.captures = new LinkedHashSet<>();
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
