package dev.tsj.compiler.backend.jvm;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-9 JVM backend compiler for expression/statement subset with closures and class/object support.
 */
public final class JvmBytecodeCompiler {
    private static final Pattern NAMED_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s*\\{([^}]*)}\\s*from\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Pattern SIDE_EFFECT_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Set<String> KEYWORDS = Set.of(
            "function", "const", "let", "var", "if", "else", "while", "return",
            "true", "false", "null", "for", "export", "import", "from",
            "class", "extends", "this", "super", "new", "undefined",
            "async", "await", "throw"
    );
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "_", "null", "true", "false"
    );
    private static final String SOURCE_MARKER_PREFIX = "// TSJ-SOURCE\t";

    private static final String OUTPUT_PACKAGE = "dev.tsj.generated";

    public JvmCompiledArtifact compile(final Path sourceFile, final Path outputDir) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(outputDir, "outputDir");

        final Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        final String fileName = normalizedSource.getFileName().toString();
        if (!fileName.endsWith(".ts") && !fileName.endsWith(".tsx")) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-INPUT",
                    "Unsupported input extension for backend compile: " + normalizedSource
            );
        }
        if (!Files.exists(normalizedSource) || !Files.isRegularFile(normalizedSource)) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-INPUT",
                "TypeScript source file not found: " + normalizedSource
            );
        }

        final BundleResult bundleResult = bundleModules(normalizedSource);
        final String sourceText = bundleResult.sourceText();

        final Parser parser = new Parser(tokenize(sourceText), bundleResult);
        final Program program = parser.parseProgram();
        final String classSimpleName = toPascalCase(stripExtension(fileName)) + "Program";
        final String className = OUTPUT_PACKAGE + "." + classSimpleName;
        final String javaSource = new JavaSourceGenerator(
                OUTPUT_PACKAGE,
                classSimpleName,
                program,
                parser.statementLocations()
        ).generate();

        final Path normalizedOutput = outputDir.toAbsolutePath().normalize();
        final Path classesDir = normalizedOutput.resolve("classes");
        final Path generatedSource = normalizedOutput.resolve("generated-src")
                .resolve(OUTPUT_PACKAGE.replace('.', '/'))
                .resolve(classSimpleName + ".java");
        final Path sourceMapFile = classesDir.resolve(OUTPUT_PACKAGE.replace('.', '/'))
                .resolve(classSimpleName + ".tsj.map");
        try {
            Files.createDirectories(classesDir);
            Files.createDirectories(generatedSource.getParent());
            Files.writeString(generatedSource, javaSource, UTF_8);
            Files.createDirectories(sourceMapFile.getParent());
            writeSourceMapFile(sourceMapFile, parseSourceMapEntries(javaSource));
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-IO",
                    "Failed to write generated Java source: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }

        compileJava(generatedSource, classesDir);

        final Path classFile = classesDir.resolve(OUTPUT_PACKAGE.replace('.', '/'))
                .resolve(classSimpleName + ".class")
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(classFile)) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-CLASS",
                    "Compiled class file not found after javac: " + classFile
            );
        }
        return new JvmCompiledArtifact(normalizedSource, classesDir, className, classFile, sourceMapFile);
    }

    private static BundleResult bundleModules(final Path entryFile) {
        final Map<Path, ModuleSource> orderedModules = new LinkedHashMap<>();
        collectModule(entryFile, orderedModules, new LinkedHashSet<>());
        final StringBuilder builder = new StringBuilder();
        final List<SourceLineOrigin> lineOrigins = new ArrayList<>();
        for (ModuleSource module : orderedModules.values()) {
            builder.append("// module: ").append(module.sourceFile()).append("\n");
            lineOrigins.add(new SourceLineOrigin(null, -1));
            for (int index = 0; index < module.filteredLines().size(); index++) {
                builder.append(module.filteredLines().get(index)).append("\n");
                lineOrigins.add(new SourceLineOrigin(module.sourceFile(), module.sourceLineNumbers().get(index)));
            }
            builder.append("\n");
            lineOrigins.add(new SourceLineOrigin(null, -1));
        }
        return new BundleResult(builder.toString(), List.copyOf(lineOrigins));
    }

    private static void collectModule(
            final Path moduleFile,
            final Map<Path, ModuleSource> orderedModules,
            final Set<Path> visiting
    ) {
        final Path normalizedModule = moduleFile.toAbsolutePath().normalize();
        if (orderedModules.containsKey(normalizedModule)) {
            return;
        }
        if (visiting.contains(normalizedModule)) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Circular imports are unsupported in TSJ-12 bootstrap: " + normalizedModule
            );
        }
        visiting.add(normalizedModule);

        final String sourceText;
        try {
            sourceText = Files.readString(normalizedModule, UTF_8);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-IO",
                    "Failed to read TypeScript source: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }

        final List<String> filteredLines = new ArrayList<>();
        final List<Integer> sourceLineNumbers = new ArrayList<>();
        final String[] lines = sourceText.replace("\r\n", "\n").split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            final String line = lines[index];
            final Matcher namedImportMatcher = NAMED_IMPORT_PATTERN.matcher(line);
            final Matcher sideEffectImportMatcher = SIDE_EFFECT_IMPORT_PATTERN.matcher(line);
            if (namedImportMatcher.matches()) {
                validateImportBindings(namedImportMatcher.group(1), normalizedModule, index + 1);
                final Path dependency = resolveImport(normalizedModule, namedImportMatcher.group(2), index + 1);
                collectModule(dependency, orderedModules, visiting);
                continue;
            }
            if (sideEffectImportMatcher.matches()) {
                final Path dependency = resolveImport(normalizedModule, sideEffectImportMatcher.group(1), index + 1);
                collectModule(dependency, orderedModules, visiting);
                continue;
            }
            if (line.trim().startsWith("import ")) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported import form in TSJ-12 bootstrap: " + line.trim(),
                        index + 1,
                    1
                );
            }
            filteredLines.add(line);
            sourceLineNumbers.add(index + 1);
        }

        orderedModules.put(
                normalizedModule,
                new ModuleSource(normalizedModule, List.copyOf(filteredLines), List.copyOf(sourceLineNumbers))
        );
        visiting.remove(normalizedModule);
    }

    private static void validateImportBindings(final String rawBindings, final Path sourceFile, final int line) {
        final String[] bindings = rawBindings.split(",");
        for (String binding : bindings) {
            final String trimmed = binding.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains(" as ")) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Import aliases are unsupported in TSJ-12 bootstrap: " + trimmed,
                        line,
                        1
                );
            }
        }
    }

    private static Path resolveImport(final Path sourceFile, final String importPath, final int line) {
        if (!importPath.startsWith(".")) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Only relative imports are supported in TSJ-12 bootstrap: " + importPath,
                    line,
                    1
            );
        }
        final Path parent = sourceFile.getParent();
        final Path base = parent == null ? Path.of(importPath) : parent.resolve(importPath);
        final Path normalizedBase = base.normalize();

        final List<Path> candidates = new ArrayList<>();
        if (normalizedBase.toString().endsWith(".ts") || normalizedBase.toString().endsWith(".tsx")) {
            candidates.add(normalizedBase);
        } else {
            candidates.add(Path.of(normalizedBase.toString() + ".ts"));
            candidates.add(Path.of(normalizedBase.toString() + ".tsx"));
            candidates.add(normalizedBase.resolve("index.ts"));
        }

        for (Path candidate : candidates) {
            final Path normalizedCandidate = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalizedCandidate) && Files.isRegularFile(normalizedCandidate)) {
                return normalizedCandidate;
            }
        }
        throw new JvmCompilationException(
                "TSJ-BACKEND-INPUT",
                "Imported module file not found: " + importPath + " (from " + sourceFile + ")",
                line,
                1
        );
    }

    private static void compileJava(final Path javaSourcePath, final Path classesDir) {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-JDK",
                    "JDK compiler is unavailable. Use a JDK runtime for TSJ backend compile."
            );
        }

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, UTF_8)) {
            final Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(List.of(javaSourcePath));
            final String classPath = System.getProperty("java.class.path", "");
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-classpath",
                    classPath,
                    "-d",
                    classesDir.toString()
            );
            final Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    compilationUnits
            ).call();
            if (!Boolean.TRUE.equals(success)) {
                throw toCompilationException(diagnostics);
            }
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-IO",
                    "Failed to run javac: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }
    }

    private static JvmCompilationException toCompilationException(
            final DiagnosticCollector<JavaFileObject> diagnostics
    ) {
        final List<Diagnostic<? extends JavaFileObject>> collected = diagnostics.getDiagnostics();
        if (collected.isEmpty()) {
            return new JvmCompilationException(
                    "TSJ-BACKEND-JAVAC",
                    "javac failed without diagnostics."
            );
        }
        final Diagnostic<? extends JavaFileObject> first = collected.getFirst();
        final Integer line = first.getLineNumber() > 0 ? (int) first.getLineNumber() : null;
        final Integer column = first.getColumnNumber() > 0 ? (int) first.getColumnNumber() : null;
        return new JvmCompilationException(
                "TSJ-BACKEND-JAVAC",
                first.getMessage(Locale.ROOT),
                line,
                column
        );
    }

    private static void writeSourceMapFile(
            final Path sourceMapFile,
            final Map<Integer, SourceLocation> sourceMapEntries
    ) throws IOException {
        final StringBuilder builder = new StringBuilder();
        builder.append("TSJ-SOURCE-MAP\t1\n");
        for (Map.Entry<Integer, SourceLocation> entry : sourceMapEntries.entrySet()) {
            final SourceLocation location = entry.getValue();
            builder.append(entry.getKey())
                    .append("\t")
                    .append(escapeSourceMarker(location.sourceFile().toString()))
                    .append("\t")
                    .append(location.line())
                    .append("\t")
                    .append(location.column())
                    .append("\n");
        }
        Files.writeString(sourceMapFile, builder.toString(), UTF_8);
    }

    private static Map<Integer, SourceLocation> parseSourceMapEntries(final String javaSource) {
        final String normalized = javaSource.replace("\r\n", "\n");
        final String[] lines = normalized.split("\n", -1);
        final Map<Integer, SourceLocation> mapping = new LinkedHashMap<>();
        SourceLocation current = null;
        for (int index = 0; index < lines.length; index++) {
            final String line = lines[index];
            final String trimmed = line.trim();
            if (trimmed.startsWith(SOURCE_MARKER_PREFIX)) {
                current = parseSourceMarker(trimmed.substring(SOURCE_MARKER_PREFIX.length()));
                continue;
            }
            if (current != null && !trimmed.isEmpty()) {
                mapping.put(index + 1, current);
            }
        }
        return mapping;
    }

    private static SourceLocation parseSourceMarker(final String markerPayload) {
        final String[] parts = markerPayload.split("\t", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new SourceLocation(
                    Path.of(unescapeSourceMarker(parts[0])).toAbsolutePath().normalize(),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static String escapeSourceMarker(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescapeSourceMarker(final String value) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                builder.append(current);
                continue;
            }
            final char next = value.charAt(index + 1);
            index++;
            switch (next) {
                case '\\' -> builder.append('\\');
                case 't' -> builder.append('\t');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                default -> {
                    builder.append('\\');
                    builder.append(next);
                }
            }
        }
        return builder.toString();
    }

    private static List<Token> tokenize(final String source) {
        final List<Token> tokens = new ArrayList<>();
        int index = 0;
        int line = 1;
        int column = 1;
        while (index < source.length()) {
            final char current = source.charAt(index);
            if (current == '\n') {
                line++;
                column = 1;
                index++;
                continue;
            }
            if (current == '\r' || current == '\t' || current == ' ') {
                index++;
                column++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                final char next = source.charAt(index + 1);
                if (next == '/') {
                    index += 2;
                    column += 2;
                    while (index < source.length() && source.charAt(index) != '\n') {
                        index++;
                        column++;
                    }
                    continue;
                }
                if (next == '*') {
                    index += 2;
                    column += 2;
                    while (index + 1 < source.length()) {
                        final char inner = source.charAt(index);
                        if (inner == '\n') {
                            line++;
                            column = 1;
                            index++;
                            continue;
                        }
                        if (inner == '*' && source.charAt(index + 1) == '/') {
                            index += 2;
                            column += 2;
                            break;
                        }
                        index++;
                        column++;
                    }
                    continue;
                }
            }
            if (Character.isLetter(current) || current == '_' || current == '$') {
                final int start = index;
                final int startColumn = column;
                while (index < source.length()) {
                    final char ch = source.charAt(index);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$') {
                        index++;
                        column++;
                    } else {
                        break;
                    }
                }
                final String text = source.substring(start, index);
                final TokenType type = KEYWORDS.contains(text) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                tokens.add(new Token(type, text, line, startColumn));
                continue;
            }
            if (Character.isDigit(current)) {
                final int start = index;
                final int startColumn = column;
                boolean seenDot = false;
                while (index < source.length()) {
                    final char ch = source.charAt(index);
                    if (Character.isDigit(ch)) {
                        index++;
                        column++;
                    } else if (ch == '.' && !seenDot) {
                        seenDot = true;
                        index++;
                        column++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, source.substring(start, index), line, startColumn));
                continue;
            }
            if (current == '"' || current == '\'') {
                final int startColumn = column;
                final char quote = current;
                index++;
                column++;
                final StringBuilder value = new StringBuilder();
                boolean terminated = false;
                while (index < source.length()) {
                    final char ch = source.charAt(index);
                    if (ch == '\\') {
                        if (index + 1 >= source.length()) {
                            break;
                        }
                        final char escaped = source.charAt(index + 1);
                        value.append(escaped);
                        index += 2;
                        column += 2;
                        continue;
                    }
                    if (ch == quote) {
                        terminated = true;
                        index++;
                        column++;
                        break;
                    }
                    if (ch == '\n') {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-PARSE",
                                "Unterminated string literal.",
                                line,
                                startColumn
                        );
                    }
                    value.append(ch);
                    index++;
                    column++;
                }
                if (!terminated) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-PARSE",
                            "Unterminated string literal.",
                            line,
                            startColumn
                    );
                }
                tokens.add(new Token(TokenType.STRING, value.toString(), line, startColumn));
                continue;
            }
            final String three = index + 3 <= source.length() ? source.substring(index, index + 3) : "";
            if ("===".equals(three) || "!==".equals(three)) {
                tokens.add(new Token(TokenType.SYMBOL, three, line, column));
                index += 3;
                column += 3;
                continue;
            }
            final String two = index + 2 <= source.length() ? source.substring(index, index + 2) : "";
            if ("==".equals(two)
                    || "!=".equals(two)
                    || "<=".equals(two)
                    || ">=".equals(two)
                    || "&&".equals(two)
                    || "||".equals(two)
                    || "=>".equals(two)) {
                tokens.add(new Token(TokenType.SYMBOL, two, line, column));
                index += 2;
                column += 2;
                continue;
            }
            final String one = Character.toString(current);
            if ("(){}[];,.+-*/<>!=:".contains(one)) {
                tokens.add(new Token(TokenType.SYMBOL, one, line, column));
                index++;
                column++;
                continue;
            }
            throw new JvmCompilationException(
                    "TSJ-BACKEND-PARSE",
                    "Unexpected character `" + current + "`.",
                    line,
                    column
            );
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private static String stripExtension(final String fileName) {
        final int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    private static String toPascalCase(final String value) {
        final String[] parts = value.split("[^A-Za-z0-9]+");
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
            return "Main";
        }
        final String normalized = builder.toString();
        if (Character.isDigit(normalized.charAt(0))) {
            return "Main" + normalized;
        }
        return normalized;
    }

    private enum TokenType {
        IDENTIFIER,
        NUMBER,
        STRING,
        SYMBOL,
        KEYWORD,
        EOF
    }

    private record Token(TokenType type, String text, int line, int column) {
    }

    private record Program(List<Statement> statements) {
    }

    private sealed interface Statement permits
            VariableDeclaration,
            AssignmentStatement,
            FunctionDeclarationStatement,
            ClassDeclarationStatement,
            IfStatement,
            WhileStatement,
            SuperCallStatement,
            ReturnStatement,
            ThrowStatement,
            ConsoleLogStatement,
            ExpressionStatement {
    }

    private record VariableDeclaration(String name, Expression expression) implements Statement {
    }

    private record AssignmentStatement(Expression target, Expression expression) implements Statement {
    }

    private record FunctionDeclarationStatement(FunctionDeclaration declaration) implements Statement {
    }

    private record ClassDeclarationStatement(ClassDeclaration declaration) implements Statement {
    }

    private record IfStatement(Expression condition, List<Statement> thenBlock, List<Statement> elseBlock)
            implements Statement {
    }

    private record WhileStatement(Expression condition, List<Statement> body) implements Statement {
    }

    private record SuperCallStatement(List<Expression> arguments) implements Statement {
    }

    private record ReturnStatement(Expression expression) implements Statement {
    }

    private record ThrowStatement(Expression expression) implements Statement {
    }

    private record ConsoleLogStatement(Expression expression) implements Statement {
    }

    private record ExpressionStatement(Expression expression) implements Statement {
    }

    private record FunctionDeclaration(String name, List<String> parameters, List<Statement> body, boolean async) {
    }

    private record ClassDeclaration(
            String name,
            String superClassName,
            List<String> fieldNames,
            ClassMethod constructorMethod,
            List<ClassMethod> methods
    ) {
    }

    private record ClassMethod(String name, List<String> parameters, List<Statement> body, boolean async) {
    }

    private sealed interface Expression permits
            NumberLiteral,
            StringLiteral,
            BooleanLiteral,
            NullLiteral,
            UndefinedLiteral,
            VariableExpression,
            ThisExpression,
            UnaryExpression,
            AwaitExpression,
            FunctionExpression,
            BinaryExpression,
            CallExpression,
            MemberAccessExpression,
            NewExpression,
            ArrayLiteralExpression,
            ObjectLiteralExpression {
    }

    private record NumberLiteral(String value) implements Expression {
    }

    private record StringLiteral(String value) implements Expression {
    }

    private record BooleanLiteral(boolean value) implements Expression {
    }

    private record NullLiteral() implements Expression {
    }

    private record UndefinedLiteral() implements Expression {
    }

    private record VariableExpression(String name) implements Expression {
    }

    private record ThisExpression() implements Expression {
    }

    private record UnaryExpression(String operator, Expression expression) implements Expression {
    }

    private record AwaitExpression(Expression expression) implements Expression {
    }

    private record FunctionExpression(List<String> parameters, List<Statement> body, boolean async)
            implements Expression {
    }

    private record BinaryExpression(Expression left, String operator, Expression right) implements Expression {
    }

    private record CallExpression(Expression callee, List<Expression> arguments) implements Expression {
    }

    private record MemberAccessExpression(Expression receiver, String member) implements Expression {
    }

    private record NewExpression(Expression constructor, List<Expression> arguments) implements Expression {
    }

    private record ArrayLiteralExpression(List<Expression> elements) implements Expression {
    }

    private record ObjectLiteralExpression(List<ObjectLiteralEntry> entries) implements Expression {
    }

    private record ObjectLiteralEntry(String key, Expression value) {
    }

    private record SourceLineOrigin(Path sourceFile, int sourceLine) {
    }

    private record SourceLocation(Path sourceFile, int line, int column) {
    }

    private record ModuleSource(
            Path sourceFile,
            List<String> filteredLines,
            List<Integer> sourceLineNumbers
    ) {
    }

    private record BundleResult(
            String sourceText,
            List<SourceLineOrigin> lineOrigins
    ) {
        private SourceLocation sourceLocationFor(final int bundledLine, final int bundledColumn) {
            if (bundledLine <= 0 || bundledLine > lineOrigins.size()) {
                return null;
            }
            final SourceLineOrigin origin = lineOrigins.get(bundledLine - 1);
            if (origin == null || origin.sourceFile() == null || origin.sourceLine() <= 0) {
                return null;
            }
            return new SourceLocation(
                    origin.sourceFile(),
                    origin.sourceLine(),
                    Math.max(1, bundledColumn)
            );
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final BundleResult bundleResult;
        private final IdentityHashMap<Statement, SourceLocation> statementLocations;
        private int index;

        private Parser(final List<Token> tokens, final BundleResult bundleResult) {
            this.tokens = tokens;
            this.bundleResult = bundleResult;
            this.statementLocations = new IdentityHashMap<>();
            this.index = 0;
        }

        private Map<Statement, SourceLocation> statementLocations() {
            return new IdentityHashMap<>(statementLocations);
        }

        private Program parseProgram() {
            final List<Statement> statements = new ArrayList<>();
            while (!isAtEnd()) {
                statements.add(parseStatement(false));
            }
            return new Program(List.copyOf(statements));
        }

        private Statement parseStatement(final boolean insideFunction) {
            final Token statementStart = current();
            final Statement statement;
            if (matchKeyword("export")) {
                if (insideFunction) {
                    final Token token = previous();
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Block-scoped `export` is unsupported in TSJ-12 subset.",
                            token.line(),
                            token.column()
                    );
                }
                if (matchKeyword("async")) {
                    if (matchKeyword("function")) {
                        statement = new FunctionDeclarationStatement(parseFunctionDeclaration(true));
                        return locate(statement, statementStart);
                    }
                    final Token token = current();
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Unsupported `export async` form in TSJ-13 subset.",
                            token.line(),
                            token.column()
                    );
                }
                if (matchKeyword("function")) {
                    statement = new FunctionDeclarationStatement(parseFunctionDeclaration(false));
                    return locate(statement, statementStart);
                }
                if (matchKeyword("class")) {
                    statement = new ClassDeclarationStatement(parseClassDeclaration());
                    return locate(statement, statementStart);
                }
                if (matchKeyword("const") || matchKeyword("let") || matchKeyword("var")) {
                    statement = parseVariableDeclaration();
                    return locate(statement, statementStart);
                }
                final Token token = current();
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported export form in TSJ-9 subset.",
                        token.line(),
                        token.column()
                );
            }
            if (matchKeyword("const") || matchKeyword("let") || matchKeyword("var")) {
                statement = parseVariableDeclaration();
                return locate(statement, statementStart);
            }
            if (matchKeyword("async")) {
                if (matchKeyword("function")) {
                    statement = new FunctionDeclarationStatement(parseFunctionDeclaration(true));
                    return locate(statement, statementStart);
                }
                final Token token = current();
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported `async` statement form in TSJ-13 subset.",
                        token.line(),
                        token.column()
                );
            }
            if (matchKeyword("function")) {
                statement = new FunctionDeclarationStatement(parseFunctionDeclaration(false));
                return locate(statement, statementStart);
            }
            if (matchKeyword("class")) {
                statement = new ClassDeclarationStatement(parseClassDeclaration());
                return locate(statement, statementStart);
            }
            if (matchKeyword("if")) {
                statement = parseIfStatement(insideFunction);
                return locate(statement, statementStart);
            }
            if (matchKeyword("while")) {
                statement = parseWhileStatement(insideFunction);
                return locate(statement, statementStart);
            }
            if (matchKeyword("super")) {
                statement = parseSuperCallStatement();
                return locate(statement, statementStart);
            }
            if (matchKeyword("return")) {
                if (!insideFunction) {
                    final Token token = previous();
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Top-level `return` is unsupported in TSJ-9 subset.",
                            token.line(),
                            token.column()
                    );
                }
                statement = parseReturnStatement();
                return locate(statement, statementStart);
            }
            if (matchKeyword("throw")) {
                if (!insideFunction) {
                    final Token token = previous();
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Top-level `throw` is unsupported in TSJ-13 subset.",
                            token.line(),
                            token.column()
                    );
                }
                statement = parseThrowStatement();
                return locate(statement, statementStart);
            }
            if (isConsoleLogStart()) {
                statement = parseConsoleLog();
                return locate(statement, statementStart);
            }
            if (current().type() == TokenType.KEYWORD && !isExpressionStartKeyword(current().text())) {
                final Token token = current();
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported statement in TSJ-9 subset: " + token.text(),
                        token.line(),
                        token.column()
                );
            }
            statement = parseExpressionOrAssignmentStatement();
            return locate(statement, statementStart);
        }

        private Statement locate(final Statement statement, final Token token) {
            final SourceLocation location = bundleResult.sourceLocationFor(token.line(), token.column());
            if (location != null) {
                statementLocations.put(statement, location);
            }
            return statement;
        }

        private Statement parseExpressionOrAssignmentStatement() {
            final Expression expression = parseExpression();
            if (matchSymbol("=")) {
                final Expression right = parseExpression();
                consumeSymbol(";", "Expected `;` after assignment.");
                return new AssignmentStatement(expression, right);
            }
            consumeSymbol(";", "Expected `;` after expression statement.");
            return new ExpressionStatement(expression);
        }

        private FunctionDeclaration parseFunctionDeclaration(final boolean asyncFunction) {
            final Token nameToken = consumeIdentifier("Expected function name after `function`.");
            consumeSymbol("(", "Expected `(` after function name.");
            final List<String> parameters = new ArrayList<>();
            if (!checkSymbol(")")) {
                do {
                    final Token parameter = consumeIdentifier("Expected parameter name.");
                    parameters.add(parameter.text());
                    if (matchSymbol(":")) {
                        skipTypeAnnotation();
                    }
                } while (matchSymbol(","));
            }
            consumeSymbol(")", "Expected `)` after function parameter list.");
            if (matchSymbol(":")) {
                skipTypeAnnotation();
            }
            final List<Statement> body = parseBlock(true);
            return new FunctionDeclaration(nameToken.text(), List.copyOf(parameters), List.copyOf(body), asyncFunction);
        }

        private ClassDeclaration parseClassDeclaration() {
            final Token className = consumeIdentifier("Expected class name after `class`.");
            String superClassName = null;
            if (matchKeyword("extends")) {
                superClassName = consumeIdentifier("Expected base class name after `extends`.").text();
            }
            consumeSymbol("{", "Expected `{` to start class body.");
            final List<String> fields = new ArrayList<>();
            final List<ClassMethod> methods = new ArrayList<>();
            ClassMethod constructorMethod = null;
            while (!checkSymbol("}") && !isAtEnd()) {
                final boolean asyncMethod = matchKeyword("async");
                final Token memberName = consumeIdentifier("Expected class member name.");
                if (matchSymbol(":")) {
                    if (asyncMethod) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Async class fields are unsupported in TSJ-13b subset.",
                                memberName.line(),
                                memberName.column()
                        );
                    }
                    skipTypeAnnotation();
                    consumeSymbol(";", "Expected `;` after class field declaration.");
                    fields.add(memberName.text());
                    continue;
                }
                consumeSymbol("(", "Expected `(` after class method name.");
                final List<String> parameters = new ArrayList<>();
                if (!checkSymbol(")")) {
                    do {
                        final Token parameter = consumeIdentifier("Expected parameter name.");
                        parameters.add(parameter.text());
                        if (matchSymbol(":")) {
                            skipTypeAnnotation();
                        }
                    } while (matchSymbol(","));
                }
                consumeSymbol(")", "Expected `)` after class method parameters.");
                if (matchSymbol(":")) {
                    skipTypeAnnotation();
                }
                final List<Statement> body = parseBlock(true);
                final ClassMethod method = new ClassMethod(
                        memberName.text(),
                        List.copyOf(parameters),
                        List.copyOf(body),
                        asyncMethod
                );
                if ("constructor".equals(memberName.text())) {
                    if (asyncMethod) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Async constructors are unsupported in TSJ-13b subset.",
                                memberName.line(),
                                memberName.column()
                        );
                    }
                    if (constructorMethod != null) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Duplicate class constructor declaration: " + className.text(),
                                memberName.line(),
                                memberName.column()
                        );
                    }
                    constructorMethod = method;
                } else {
                    methods.add(method);
                }
            }
            consumeSymbol("}", "Expected `}` to close class body.");
            return new ClassDeclaration(
                    className.text(),
                    superClassName,
                    List.copyOf(fields),
                    constructorMethod,
                    List.copyOf(methods)
            );
        }

        private VariableDeclaration parseVariableDeclaration() {
            final Token name = consumeIdentifier("Expected variable name after declaration keyword.");
            if (matchSymbol(":")) {
                skipTypeAnnotation();
            }
            consumeSymbol("=", "Expected `=` after variable name.");
            final Expression expression = parseExpression();
            consumeSymbol(";", "Expected `;` after variable declaration.");
            return new VariableDeclaration(name.text(), expression);
        }

        private IfStatement parseIfStatement(final boolean insideFunction) {
            consumeSymbol("(", "Expected `(` after `if`.");
            final Expression condition = parseExpression();
            consumeSymbol(")", "Expected `)` after if condition.");
            final List<Statement> thenBlock = parseBlock(insideFunction);
            List<Statement> elseBlock = List.of();
            if (matchKeyword("else")) {
                elseBlock = parseBlock(insideFunction);
            }
            return new IfStatement(condition, thenBlock, elseBlock);
        }

        private WhileStatement parseWhileStatement(final boolean insideFunction) {
            consumeSymbol("(", "Expected `(` after `while`.");
            final Expression condition = parseExpression();
            consumeSymbol(")", "Expected `)` after while condition.");
            final List<Statement> body = parseBlock(insideFunction);
            return new WhileStatement(condition, body);
        }

        private SuperCallStatement parseSuperCallStatement() {
            consumeSymbol("(", "Expected `(` after `super`.");
            final List<Expression> arguments = new ArrayList<>();
            if (!checkSymbol(")")) {
                do {
                    arguments.add(parseExpression());
                } while (matchSymbol(","));
            }
            consumeSymbol(")", "Expected `)` after super call arguments.");
            consumeSymbol(";", "Expected `;` after super call.");
            return new SuperCallStatement(List.copyOf(arguments));
        }

        private ReturnStatement parseReturnStatement() {
            final Expression expression = parseExpression();
            consumeSymbol(";", "Expected `;` after return expression.");
            return new ReturnStatement(expression);
        }

        private ThrowStatement parseThrowStatement() {
            final Expression expression = parseExpression();
            consumeSymbol(";", "Expected `;` after throw expression.");
            return new ThrowStatement(expression);
        }

        private ConsoleLogStatement parseConsoleLog() {
            consumeIdentifier("Expected `console`.");
            consumeSymbol(".", "Expected `.` after `console`.");
            final Token logToken = consumeIdentifier("Expected `log` method after `console.`");
            if (!"log".equals(logToken.text())) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Only console.log is supported in TSJ-9 subset.",
                        logToken.line(),
                        logToken.column()
                );
            }
            consumeSymbol("(", "Expected `(` after `console.log`.");
            final Expression expression = parseExpression();
            consumeSymbol(")", "Expected `)` after console.log argument.");
            consumeSymbol(";", "Expected `;` after console.log.");
            return new ConsoleLogStatement(expression);
        }

        private List<Statement> parseBlock(final boolean insideFunction) {
            consumeSymbol("{", "Expected `{` to start block.");
            final List<Statement> statements = new ArrayList<>();
            while (!checkSymbol("}") && !isAtEnd()) {
                statements.add(parseStatement(insideFunction));
            }
            consumeSymbol("}", "Expected `}` to close block.");
            return statements;
        }

        private Expression parseExpression() {
            return parseEquality();
        }

        private Expression parseEquality() {
            Expression expression = parseComparison();
            while (matchSymbol("===") || matchSymbol("==") || matchSymbol("!==") || matchSymbol("!=")) {
                final String operator = previous().text();
                final Expression right = parseComparison();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseComparison() {
            Expression expression = parseTerm();
            while (matchSymbol("<")
                    || matchSymbol("<=")
                    || matchSymbol(">")
                    || matchSymbol(">=")) {
                final String operator = previous().text();
                final Expression right = parseTerm();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseTerm() {
            Expression expression = parseFactor();
            while (matchSymbol("+") || matchSymbol("-")) {
                final String operator = previous().text();
                final Expression right = parseFactor();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseFactor() {
            Expression expression = parseUnary();
            while (matchSymbol("*") || matchSymbol("/")) {
                final String operator = previous().text();
                final Expression right = parseUnary();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseUnary() {
            if (matchSymbol("-") || matchSymbol("!")) {
                final String operator = previous().text();
                return new UnaryExpression(operator, parseUnary());
            }
            if (matchKeyword("await")) {
                return new AwaitExpression(parseUnary());
            }
            if (matchKeyword("new")) {
                return parseNewExpression();
            }
            return parseCall();
        }

        private Expression parseCall() {
            return parsePostfix(parsePrimary());
        }

        private Expression parseNewExpression() {
            Expression constructor = parsePrimary();
            while (matchSymbol(".")) {
                final Token member = consumeIdentifier("Expected member name after `.` in constructor expression.");
                constructor = new MemberAccessExpression(constructor, member.text());
            }
            consumeSymbol("(", "Expected `(` after constructor expression in `new`.");
            final List<Expression> arguments = new ArrayList<>();
            if (!checkSymbol(")")) {
                do {
                    arguments.add(parseExpression());
                } while (matchSymbol(","));
            }
            consumeSymbol(")", "Expected `)` after constructor arguments.");
            return parsePostfix(new NewExpression(constructor, List.copyOf(arguments)));
        }

        private Expression parsePostfix(Expression expression) {
            while (true) {
                if (matchSymbol(".")) {
                    final Token member = consumeIdentifier("Expected property name after `.`.");
                    expression = new MemberAccessExpression(expression, member.text());
                    continue;
                }
                if (matchSymbol("(")) {
                    final List<Expression> arguments = new ArrayList<>();
                    if (!checkSymbol(")")) {
                        do {
                            arguments.add(parseExpression());
                        } while (matchSymbol(","));
                    }
                    consumeSymbol(")", "Expected `)` after call arguments.");
                    expression = new CallExpression(expression, List.copyOf(arguments));
                    continue;
                }
                break;
            }
            return expression;
        }

        private Expression parsePrimary() {
            if (matchKeyword("async")) {
                if (matchKeyword("function")) {
                    return parseFunctionExpression(true);
                }
                if (current().type() == TokenType.IDENTIFIER
                        && lookAhead(1).type() == TokenType.SYMBOL
                        && "=>".equals(lookAhead(1).text())) {
                    final Token parameter = advance();
                    consumeSymbol("=>", "Expected `=>` after async arrow parameter.");
                    return parseArrowFunctionExpression(List.of(parameter.text()), true);
                }
                if (checkSymbol("(") && looksLikeParenthesizedArrowFunction()) {
                    return parseArrowFunctionExpressionFromParenthesized(true);
                }
                final Token token = current();
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported async expression form in TSJ-13b subset.",
                        token.line(),
                        token.column()
                );
            }
            if (matchKeyword("function")) {
                return parseFunctionExpression(false);
            }
            if (matchType(TokenType.NUMBER)) {
                return new NumberLiteral(previous().text());
            }
            if (matchType(TokenType.STRING)) {
                return new StringLiteral(previous().text());
            }
            if (matchKeyword("true")) {
                return new BooleanLiteral(true);
            }
            if (matchKeyword("false")) {
                return new BooleanLiteral(false);
            }
            if (matchKeyword("null")) {
                return new NullLiteral();
            }
            if (matchKeyword("undefined")) {
                return new UndefinedLiteral();
            }
            if (matchKeyword("this")) {
                return new ThisExpression();
            }
            if (checkSymbol("(") && looksLikeParenthesizedArrowFunction()) {
                return parseArrowFunctionExpressionFromParenthesized(false);
            }
            if (matchType(TokenType.IDENTIFIER)) {
                final Token identifier = previous();
                if (matchSymbol("=>")) {
                    return parseArrowFunctionExpression(List.of(identifier.text()), false);
                }
                return new VariableExpression(identifier.text());
            }
            if (matchSymbol("{")) {
                return parseObjectLiteral();
            }
            if (matchSymbol("[")) {
                return parseArrayLiteral();
            }
            if (matchSymbol("(")) {
                final Expression expression = parseExpression();
                consumeSymbol(")", "Expected `)` after grouped expression.");
                return expression;
            }
            final Token token = current();
            throw new JvmCompilationException(
                    "TSJ-BACKEND-PARSE",
                    "Unexpected token `" + token.text() + "`.",
                    token.line(),
                    token.column()
            );
        }

        private ObjectLiteralExpression parseObjectLiteral() {
            final List<ObjectLiteralEntry> entries = new ArrayList<>();
            if (!checkSymbol("}")) {
                do {
                    if (current().type() == TokenType.KEYWORD
                            && "async".equals(current().text())
                            && lookAhead(1).type() == TokenType.IDENTIFIER
                            && lookAhead(2).type() == TokenType.SYMBOL
                            && "(".equals(lookAhead(2).text())) {
                        advance();
                        final Token methodName = consumeIdentifier("Expected async object method name.");
                        entries.add(new ObjectLiteralEntry(methodName.text(), parseMethodFunctionExpression(true)));
                        continue;
                    }

                    final String key;
                    if (matchType(TokenType.IDENTIFIER)) {
                        key = previous().text();
                    } else if (matchType(TokenType.STRING)) {
                        key = previous().text();
                    } else if (matchKeyword("async")) {
                        key = "async";
                    } else {
                        final Token token = current();
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-PARSE",
                                "Expected object literal property name.",
                                token.line(),
                                token.column()
                        );
                    }
                    if (matchSymbol(":")) {
                        entries.add(new ObjectLiteralEntry(key, parseExpression()));
                    } else if (checkSymbol("(")) {
                        entries.add(new ObjectLiteralEntry(key, parseMethodFunctionExpression(false)));
                    } else {
                        final Token token = current();
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-PARSE",
                                "Expected `:` or `(` after object literal property name.",
                                token.line(),
                                token.column()
                        );
                    }
                } while (matchSymbol(","));
            }
            consumeSymbol("}", "Expected `}` to close object literal.");
            return new ObjectLiteralExpression(List.copyOf(entries));
        }

        private ArrayLiteralExpression parseArrayLiteral() {
            final List<Expression> elements = new ArrayList<>();
            if (!checkSymbol("]")) {
                do {
                    elements.add(parseExpression());
                } while (matchSymbol(","));
            }
            consumeSymbol("]", "Expected `]` to close array literal.");
            return new ArrayLiteralExpression(List.copyOf(elements));
        }

        private FunctionExpression parseFunctionExpression(final boolean asyncFunction) {
            if (current().type() == TokenType.IDENTIFIER
                    && lookAhead(1).type() == TokenType.SYMBOL
                    && "(".equals(lookAhead(1).text())) {
                advance();
            }
            consumeSymbol("(", "Expected `(` after function keyword.");
            final List<String> parameters = new ArrayList<>();
            if (!checkSymbol(")")) {
                do {
                    final Token parameter = consumeIdentifier("Expected parameter name.");
                    parameters.add(parameter.text());
                    if (matchSymbol(":")) {
                        skipTypeAnnotation();
                    }
                } while (matchSymbol(","));
            }
            consumeSymbol(")", "Expected `)` after function parameter list.");
            if (matchSymbol(":")) {
                skipTypeAnnotation();
            }
            final List<Statement> body = parseBlock(true);
            return new FunctionExpression(List.copyOf(parameters), List.copyOf(body), asyncFunction);
        }

        private FunctionExpression parseArrowFunctionExpressionFromParenthesized(final boolean asyncFunction) {
            consumeSymbol("(", "Expected `(` before arrow parameters.");
            final List<String> parameters = new ArrayList<>();
            if (!checkSymbol(")")) {
                do {
                    final Token parameter = consumeIdentifier("Expected arrow function parameter name.");
                    parameters.add(parameter.text());
                    if (matchSymbol(":")) {
                        skipTypeAnnotation();
                    }
                } while (matchSymbol(","));
            }
            consumeSymbol(")", "Expected `)` after arrow parameters.");
            if (matchSymbol(":")) {
                skipTypeAnnotation();
            }
            consumeSymbol("=>", "Expected `=>` after arrow parameters.");
            return parseArrowFunctionExpression(parameters, asyncFunction);
        }

        private FunctionExpression parseArrowFunctionExpression(
                final List<String> parameters,
                final boolean asyncFunction
        ) {
            final List<Statement> body = parseArrowFunctionBody();
            return new FunctionExpression(List.copyOf(parameters), List.copyOf(body), asyncFunction);
        }

        private List<Statement> parseArrowFunctionBody() {
            if (checkSymbol("{")) {
                return parseBlock(true);
            }
            return List.of(new ReturnStatement(parseExpression()));
        }

        private FunctionExpression parseMethodFunctionExpression(final boolean asyncFunction) {
            consumeSymbol("(", "Expected `(` after object method name.");
            final List<String> parameters = new ArrayList<>();
            if (!checkSymbol(")")) {
                do {
                    final Token parameter = consumeIdentifier("Expected method parameter name.");
                    parameters.add(parameter.text());
                    if (matchSymbol(":")) {
                        skipTypeAnnotation();
                    }
                } while (matchSymbol(","));
            }
            consumeSymbol(")", "Expected `)` after method parameters.");
            if (matchSymbol(":")) {
                skipTypeAnnotation();
            }
            final List<Statement> body = parseBlock(true);
            return new FunctionExpression(List.copyOf(parameters), List.copyOf(body), asyncFunction);
        }

        private boolean looksLikeParenthesizedArrowFunction() {
            int cursor = index;
            if (tokens.get(cursor).type() != TokenType.SYMBOL || !"(".equals(tokens.get(cursor).text())) {
                return false;
            }
            int depth = 0;
            while (cursor < tokens.size()) {
                final Token token = tokens.get(cursor);
                if (token.type() == TokenType.SYMBOL) {
                    if ("(".equals(token.text())) {
                        depth++;
                    } else if (")".equals(token.text())) {
                        depth--;
                        if (depth == 0) {
                            cursor++;
                            break;
                        }
                    }
                }
                cursor++;
            }
            if (depth != 0 || cursor >= tokens.size()) {
                return false;
            }
            final Token next = tokens.get(cursor);
            return next.type() == TokenType.SYMBOL && "=>".equals(next.text());
        }

        private boolean isConsoleLogStart() {
            return current().type() == TokenType.IDENTIFIER
                    && "console".equals(current().text())
                    && lookAhead(1).type() == TokenType.SYMBOL
                    && ".".equals(lookAhead(1).text())
                    && lookAhead(2).type() == TokenType.IDENTIFIER
                    && "log".equals(lookAhead(2).text());
        }

        private boolean isExpressionStartKeyword(final String keyword) {
            return "true".equals(keyword)
                    || "false".equals(keyword)
                    || "null".equals(keyword)
                    || "undefined".equals(keyword)
                    || "this".equals(keyword)
                    || "new".equals(keyword)
                    || "await".equals(keyword);
        }

        private void skipTypeAnnotation() {
            while (!isAtEnd()) {
                if (checkSymbol("=")
                        || checkSymbol(",")
                        || checkSymbol(")")
                        || checkSymbol(";")
                        || checkSymbol("{")) {
                    return;
                }
                advance();
            }
        }

        private Token consumeIdentifier(final String message) {
            if (current().type() == TokenType.IDENTIFIER) {
                return advance();
            }
            final Token token = current();
            throw new JvmCompilationException(
                    "TSJ-BACKEND-PARSE",
                    message,
                    token.line(),
                    token.column()
            );
        }

        private void consumeSymbol(final String symbol, final String message) {
            if (matchSymbol(symbol)) {
                return;
            }
            final Token token = current();
            throw new JvmCompilationException(
                    "TSJ-BACKEND-PARSE",
                    message,
                    token.line(),
                    token.column()
            );
        }

        private boolean checkSymbol(final String symbol) {
            return current().type() == TokenType.SYMBOL && symbol.equals(current().text());
        }

        private boolean matchSymbol(final String symbol) {
            if (checkSymbol(symbol)) {
                advance();
                return true;
            }
            return false;
        }

        private boolean matchKeyword(final String keyword) {
            if (current().type() == TokenType.KEYWORD && keyword.equals(current().text())) {
                advance();
                return true;
            }
            return false;
        }

        private boolean matchType(final TokenType type) {
            if (current().type() == type) {
                advance();
                return true;
            }
            return false;
        }

        private Token lookAhead(final int offset) {
            final int resolved = Math.min(index + offset, tokens.size() - 1);
            return tokens.get(resolved);
        }

        private Token current() {
            return tokens.get(index);
        }

        private Token previous() {
            return tokens.get(index - 1);
        }

        private Token advance() {
            if (!isAtEnd()) {
                index++;
            }
            return previous();
        }

        private boolean isAtEnd() {
            return current().type() == TokenType.EOF;
        }
    }

    private static final class JavaSourceGenerator {
        private final String packageName;
        private final String classSimpleName;
        private final Program program;
        private final IdentityHashMap<Statement, SourceLocation> statementLocations;
        private final List<String> propertyCacheFieldDeclarations;
        private int propertyCacheCounter;

        private JavaSourceGenerator(
                final String packageName,
                final String classSimpleName,
                final Program program,
                final Map<Statement, SourceLocation> statementLocations
        ) {
            this.packageName = packageName;
            this.classSimpleName = classSimpleName;
            this.program = program;
            this.statementLocations = new IdentityHashMap<>(statementLocations);
            this.propertyCacheFieldDeclarations = new ArrayList<>();
            this.propertyCacheCounter = 0;
        }

        private String generate() {
            final StringBuilder mainBody = new StringBuilder();
            final EmissionContext mainContext = new EmissionContext(null);
            if (requiresTopLevelAwaitLowering(program.statements())) {
                emitTopLevelAwaitStatements(mainBody, mainContext, program.statements(), "        ");
            } else {
                emitStatements(mainBody, mainContext, program.statements(), "        ", false);
            }
            mainBody.append("        dev.tsj.runtime.TsjRuntime.flushMicrotasks();\n");

            final StringBuilder builder = new StringBuilder();
            builder.append("package ").append(packageName).append(";\n\n");
            builder.append("public final class ").append(classSimpleName).append(" {\n");
            builder.append("    private ").append(classSimpleName).append("() {\n");
            builder.append("    }\n\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell PROMISE_BUILTIN_CELL = ")
                    .append("new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.promiseBuiltin());\n");
            if (!propertyCacheFieldDeclarations.isEmpty()) {
                builder.append("\n");
            }
            for (String declaration : propertyCacheFieldDeclarations) {
                builder.append("    ").append(declaration).append("\n");
            }
            if (!propertyCacheFieldDeclarations.isEmpty()) {
                builder.append("\n");
            }
            builder.append("    public static void main(String[] args) {\n");
            builder.append(mainBody);
            builder.append("    }\n");
            builder.append("}\n");
            return builder.toString();
        }

        private void emitStatements(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final String indent,
                final boolean insideFunction
        ) {
            predeclareFunctionBindings(builder, context, statements, indent);

            for (Statement statement : statements) {
                emitSourceMarker(builder, statement, indent);
                if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                    emitFunctionAssignment(builder, context, declarationStatement.declaration(), indent);
                    continue;
                }
                if (statement instanceof ClassDeclarationStatement classDeclarationStatement) {
                    emitClassDeclaration(builder, context, classDeclarationStatement.declaration(), indent);
                    continue;
                }
                if (statement instanceof VariableDeclaration declaration) {
                    final String cellName = context.declareBinding(declaration.name());
                    builder.append(indent)
                            .append("final dev.tsj.runtime.TsjCell ")
                            .append(cellName)
                            .append(" = new dev.tsj.runtime.TsjCell(")
                            .append(emitExpression(context, declaration.expression()))
                            .append(");\n");
                    continue;
                }
                if (statement instanceof AssignmentStatement assignment) {
                    emitAssignment(builder, context, assignment, indent);
                    continue;
                }
                if (statement instanceof ConsoleLogStatement logStatement) {
                    builder.append(indent)
                            .append("dev.tsj.runtime.TsjRuntime.print(")
                            .append(emitExpression(context, logStatement.expression()))
                            .append(");\n");
                    continue;
                }
                if (statement instanceof IfStatement ifStatement) {
                    builder.append(indent)
                            .append("if (dev.tsj.runtime.TsjRuntime.truthy(")
                            .append(emitExpression(context, ifStatement.condition()))
                            .append(")) {\n");
                    emitStatements(
                            builder,
                            new EmissionContext(context),
                            ifStatement.thenBlock(),
                            indent + "    ",
                            insideFunction
                    );
                    builder.append(indent).append("}");
                    if (!ifStatement.elseBlock().isEmpty()) {
                        builder.append(" else {\n");
                        emitStatements(
                                builder,
                                new EmissionContext(context),
                                ifStatement.elseBlock(),
                                indent + "    ",
                                insideFunction
                        );
                        builder.append(indent).append("}");
                    }
                    builder.append("\n");
                    continue;
                }
                if (statement instanceof WhileStatement whileStatement) {
                    builder.append(indent)
                            .append("while (dev.tsj.runtime.TsjRuntime.truthy(")
                            .append(emitExpression(context, whileStatement.condition()))
                            .append(")) {\n");
                    emitStatements(
                            builder,
                            new EmissionContext(context),
                            whileStatement.body(),
                            indent + "    ",
                            insideFunction
                    );
                    builder.append(indent).append("}\n");
                    continue;
                }
                if (statement instanceof SuperCallStatement superCallStatement) {
                    emitSuperConstructorCall(builder, context, superCallStatement, indent);
                    continue;
                }
                if (statement instanceof ReturnStatement returnStatement) {
                    if (!insideFunction) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Return statements are only valid inside functions in TSJ-9."
                        );
                    }
                    builder.append(indent)
                            .append("return ")
                            .append(emitExpression(context, returnStatement.expression()))
                            .append(";\n");
                    continue;
                }
                if (statement instanceof ThrowStatement throwStatement) {
                    if (!insideFunction) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Throw statements are only valid inside functions in TSJ-13."
                        );
                    }
                    builder.append(indent)
                            .append("throw dev.tsj.runtime.TsjRuntime.raise(")
                            .append(emitExpression(context, throwStatement.expression()))
                            .append(");\n");
                    continue;
                }
                if (statement instanceof ExpressionStatement expressionStatement) {
                    builder.append(indent)
                            .append(emitExpression(context, expressionStatement.expression()))
                            .append(";\n");
                    continue;
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported statement node in code generation: " + statement.getClass().getSimpleName()
                );
            }
        }

        private void emitSourceMarker(
                final StringBuilder builder,
                final Statement statement,
                final String indent
        ) {
            final SourceLocation sourceLocation = statementLocations.get(statement);
            if (sourceLocation == null) {
                return;
            }
            builder.append(indent)
                    .append(SOURCE_MARKER_PREFIX)
                    .append(escapeSourceMarker(sourceLocation.sourceFile().toString()))
                    .append("\t")
                    .append(sourceLocation.line())
                    .append("\t")
                    .append(sourceLocation.column())
                    .append("\n");
        }

        private void emitTopLevelAwaitStatements(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final String indent
        ) {
            final List<Statement> normalized = normalizeAsyncStatementsForAwaitExpressions(context, statements);
            predeclareFunctionBindings(builder, context, normalized, indent);
            predeclareAsyncLocalBindings(builder, context, normalized, indent);

            final String topLevelArgs = context.allocateGeneratedName("topLevelArgs");
            builder.append(indent)
                    .append("dev.tsj.runtime.TsjRuntime.promiseThen(")
                    .append("dev.tsj.runtime.TsjRuntime.promiseResolve(")
                    .append("dev.tsj.runtime.TsjRuntime.undefined()), ")
                    .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                    .append(topLevelArgs)
                    .append(") -> {\n");
            emitAsyncStatements(builder, context, normalized, indent + "    ");
            builder.append(indent)
                    .append("}, dev.tsj.runtime.TsjRuntime.undefined());\n");
        }

        private boolean requiresTopLevelAwaitLowering(final List<Statement> statements) {
            for (Statement statement : statements) {
                if (statementContainsAwait(statement)) {
                    return true;
                }
            }
            return false;
        }

        private void predeclareFunctionBindings(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final String indent
        ) {
            for (Statement statement : statements) {
                if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                    final FunctionDeclaration declaration = declarationStatement.declaration();
                    final String cellName = context.predeclareBinding(declaration.name());
                    builder.append(indent)
                            .append("final dev.tsj.runtime.TsjCell ")
                            .append(cellName)
                            .append(" = new dev.tsj.runtime.TsjCell(null);\n");
                }
            }
        }

        private void emitFunctionAssignment(
                final StringBuilder builder,
                final EmissionContext context,
                final FunctionDeclaration declaration,
                final String indent
        ) {
            if (declaration.async()) {
                emitAsyncFunctionAssignment(builder, context, declaration, indent);
                return;
            }

            final String cellName = context.resolveBinding(declaration.name());
            final String argsVar = context.allocateGeneratedName("lambdaArgs");

            builder.append(indent)
                    .append(cellName)
                    .append(".set((dev.tsj.runtime.TsjCallable) (Object... ")
                    .append(argsVar)
                    .append(") -> {\n");

            final EmissionContext functionContext = new EmissionContext(context);
            emitParameterCells(builder, functionContext, declaration.parameters(), argsVar, indent + "    ");

            emitStatements(builder, functionContext, declaration.body(), indent + "    ", true);
            if (declaration.body().isEmpty()
                    || !(declaration.body().get(declaration.body().size() - 1) instanceof ReturnStatement)) {
                builder.append(indent).append("    return null;\n");
            }

            builder.append(indent).append("});\n");
        }

        private void emitAsyncFunctionAssignment(
                final StringBuilder builder,
                final EmissionContext context,
                final FunctionDeclaration declaration,
                final String indent
        ) {
            final String cellName = context.resolveBinding(declaration.name());
            final String argsVar = context.allocateGeneratedName("lambdaArgs");

            builder.append(indent)
                    .append(cellName)
                    .append(".set((dev.tsj.runtime.TsjCallable) (Object... ")
                    .append(argsVar)
                    .append(") -> {\n");

            final EmissionContext functionContext = new EmissionContext(context);
            final List<Statement> normalizedBody =
                    normalizeAsyncStatementsForAwaitExpressions(functionContext, declaration.body());
            emitParameterCells(builder, functionContext, declaration.parameters(), argsVar, indent + "    ");
            predeclareAsyncLocalBindings(builder, functionContext, normalizedBody, indent + "    ");

            builder.append(indent).append("    try {\n");
            emitAsyncStatements(builder, functionContext, normalizedBody, indent + "        ");
            builder.append(indent).append("    } catch (RuntimeException __tsjAsyncError) {\n");
            builder.append(indent)
                    .append("        return dev.tsj.runtime.TsjRuntime.promiseReject(")
                    .append("dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));\n");
            builder.append(indent).append("    }\n");
            builder.append(indent).append("});\n");
        }

        private void emitClassDeclaration(
                final StringBuilder builder,
                final EmissionContext context,
                final ClassDeclaration declaration,
                final String indent
        ) {
            final String classCellName = context.declareBinding(declaration.name());
            final String classVar = context.allocateGeneratedName(sanitizeIdentifier(declaration.name()) + "_class");
            final String superClassExpression = declaration.superClassName() == null
                    ? null
                    : context.resolveBinding(declaration.superClassName()) + ".get()";
            final String superClassArg = superClassExpression == null
                    ? "null"
                    : "dev.tsj.runtime.TsjRuntime.asClass(" + superClassExpression + ")";

            builder.append(indent)
                    .append("final dev.tsj.runtime.TsjCell ")
                    .append(classCellName)
                    .append(" = new dev.tsj.runtime.TsjCell(null);\n");
            builder.append(indent)
                    .append("final dev.tsj.runtime.TsjClass ")
                    .append(classVar)
                    .append(" = new dev.tsj.runtime.TsjClass(\"")
                    .append(escapeJava(declaration.name()))
                    .append("\", ")
                    .append(superClassArg)
                    .append(");\n");
            builder.append(indent)
                    .append(classCellName)
                    .append(".set(")
                    .append(classVar)
                    .append(");\n");

            if (declaration.constructorMethod() != null) {
                emitClassMethod(
                        builder,
                        context,
                        classVar,
                        declaration.constructorMethod(),
                        true,
                        superClassExpression,
                        indent
                );
            }
            for (ClassMethod method : declaration.methods()) {
                emitClassMethod(builder, context, classVar, method, false, superClassExpression, indent);
            }
        }

        private void emitClassMethod(
                final StringBuilder builder,
                final EmissionContext context,
                final String classVar,
                final ClassMethod method,
                final boolean constructor,
                final String superClassExpression,
                final String indent
        ) {
            final String thisVar = context.allocateGeneratedName("thisObject");
            final String argsVar = context.allocateGeneratedName("methodArgs");
            if (constructor) {
                builder.append(indent)
                        .append(classVar)
                        .append(".setConstructor((dev.tsj.runtime.TsjObject ")
                        .append(thisVar)
                        .append(", Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
            } else {
                builder.append(indent)
                        .append(classVar)
                        .append(".defineMethod(\"")
                        .append(escapeJava(method.name()))
                        .append("\", (dev.tsj.runtime.TsjObject ")
                        .append(thisVar)
                        .append(", Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
            }

            final EmissionContext methodContext =
                    new EmissionContext(context, thisVar, superClassExpression, constructor);
            emitParameterCells(builder, methodContext, method.parameters(), argsVar, indent + "    ");
            if (!constructor && method.async()) {
                final List<Statement> normalizedBody =
                        normalizeAsyncStatementsForAwaitExpressions(methodContext, method.body());
                predeclareAsyncLocalBindings(builder, methodContext, normalizedBody, indent + "    ");
                builder.append(indent).append("    try {\n");
                emitAsyncStatements(builder, methodContext, normalizedBody, indent + "        ");
                builder.append(indent).append("    } catch (RuntimeException __tsjAsyncError) {\n");
                builder.append(indent)
                        .append("        return dev.tsj.runtime.TsjRuntime.promiseReject(")
                        .append("dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));\n");
                builder.append(indent).append("    }\n");
            } else {
                emitStatements(builder, methodContext, method.body(), indent + "    ", true);
                if (method.body().isEmpty()
                        || !(method.body().get(method.body().size() - 1) instanceof ReturnStatement)) {
                    builder.append(indent).append("    return null;\n");
                }
            }
            builder.append(indent).append("});\n");
        }

        private void emitParameterCells(
                final StringBuilder builder,
                final EmissionContext context,
                final List<String> parameters,
                final String argsVar,
                final String indent
        ) {
            for (int index = 0; index < parameters.size(); index++) {
                final String parameterName = parameters.get(index);
                final String parameterCell = context.declareBinding(parameterName);
                builder.append(indent)
                        .append("final dev.tsj.runtime.TsjCell ")
                        .append(parameterCell)
                        .append(" = new dev.tsj.runtime.TsjCell(")
                        .append(argsVar)
                        .append(".length > ")
                        .append(index)
                        .append(" ? ")
                        .append(argsVar)
                        .append("[")
                        .append(index)
                        .append("] : null);\n");
            }
        }

        private String emitFunctionExpression(
                final EmissionContext context,
                final FunctionExpression functionExpression
        ) {
            final String argsVar = context.allocateGeneratedName("lambdaArgs");
            final StringBuilder functionBuilder = new StringBuilder();
            functionBuilder.append("((dev.tsj.runtime.TsjCallable) (Object... ")
                    .append(argsVar)
                    .append(") -> {\n");

            final EmissionContext functionContext = new EmissionContext(context);
            emitParameterCells(functionBuilder, functionContext, functionExpression.parameters(), argsVar, "    ");

            if (functionExpression.async()) {
                final List<Statement> normalizedBody =
                        normalizeAsyncStatementsForAwaitExpressions(functionContext, functionExpression.body());
                predeclareAsyncLocalBindings(functionBuilder, functionContext, normalizedBody, "    ");
                functionBuilder.append("    try {\n");
                emitAsyncStatements(functionBuilder, functionContext, normalizedBody, "        ");
                functionBuilder.append("    } catch (RuntimeException __tsjAsyncError) {\n");
                functionBuilder.append("        return dev.tsj.runtime.TsjRuntime.promiseReject(")
                        .append("dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));\n");
                functionBuilder.append("    }\n");
            } else {
                emitStatements(functionBuilder, functionContext, functionExpression.body(), "    ", true);
                if (functionExpression.body().isEmpty()
                        || !(functionExpression.body().get(functionExpression.body().size() - 1) instanceof ReturnStatement)) {
                    functionBuilder.append("    return null;\n");
                }
            }

            functionBuilder.append("})");
            return functionBuilder.toString();
        }

        private List<Statement> normalizeAsyncStatementsForAwaitExpressions(
                final EmissionContext context,
                final List<Statement> statements
        ) {
            final List<Statement> normalized = new ArrayList<>();
            for (Statement statement : statements) {
                normalized.addAll(rewriteAsyncStatementForAwait(context, statement));
            }
            return List.copyOf(normalized);
        }

        private List<Statement> rewriteAsyncStatementForAwait(
                final EmissionContext context,
                final Statement statement
        ) {
            final SourceLocation sourceLocation = sourceLocationFor(statement);
            if (statement instanceof VariableDeclaration declaration) {
                final AwaitExpressionRewrite rewritten =
                        rewriteAsyncExpressionForAwait(context, declaration.expression(), sourceLocation);
                final List<Statement> expanded = new ArrayList<>(rewritten.hoistedStatements());
                final VariableDeclaration rewrittenStatement =
                        new VariableDeclaration(declaration.name(), rewritten.expression());
                copySourceLocation(statement, rewrittenStatement);
                expanded.add(rewrittenStatement);
                return List.copyOf(expanded);
            }
            if (statement instanceof AssignmentStatement assignment) {
                if (expressionContainsAwait(assignment.target())) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Await is unsupported in assignment target."
                    );
                }
                final AwaitExpressionRewrite rewritten =
                        rewriteAsyncExpressionForAwait(context, assignment.expression(), sourceLocation);
                final List<Statement> expanded = new ArrayList<>(rewritten.hoistedStatements());
                final AssignmentStatement rewrittenStatement =
                        new AssignmentStatement(assignment.target(), rewritten.expression());
                copySourceLocation(statement, rewrittenStatement);
                expanded.add(rewrittenStatement);
                return List.copyOf(expanded);
            }
            if (statement instanceof ReturnStatement returnStatement) {
                final AwaitExpressionRewrite rewritten =
                        rewriteAsyncExpressionForAwait(context, returnStatement.expression(), sourceLocation);
                final List<Statement> expanded = new ArrayList<>(rewritten.hoistedStatements());
                final ReturnStatement rewrittenStatement = new ReturnStatement(rewritten.expression());
                copySourceLocation(statement, rewrittenStatement);
                expanded.add(rewrittenStatement);
                return List.copyOf(expanded);
            }
            if (statement instanceof ThrowStatement throwStatement) {
                final AwaitExpressionRewrite rewritten =
                        rewriteAsyncExpressionForAwait(context, throwStatement.expression(), sourceLocation);
                final List<Statement> expanded = new ArrayList<>(rewritten.hoistedStatements());
                final ThrowStatement rewrittenStatement = new ThrowStatement(rewritten.expression());
                copySourceLocation(statement, rewrittenStatement);
                expanded.add(rewrittenStatement);
                return List.copyOf(expanded);
            }
            if (statement instanceof ConsoleLogStatement logStatement) {
                final AwaitExpressionRewrite rewritten =
                        rewriteAsyncExpressionForAwait(context, logStatement.expression(), sourceLocation);
                final List<Statement> expanded = new ArrayList<>(rewritten.hoistedStatements());
                final ConsoleLogStatement rewrittenStatement = new ConsoleLogStatement(rewritten.expression());
                copySourceLocation(statement, rewrittenStatement);
                expanded.add(rewrittenStatement);
                return List.copyOf(expanded);
            }
            if (statement instanceof ExpressionStatement expressionStatement) {
                final AwaitExpressionRewrite rewritten =
                        rewriteAsyncExpressionForAwait(context, expressionStatement.expression(), sourceLocation);
                final List<Statement> expanded = new ArrayList<>(rewritten.hoistedStatements());
                final ExpressionStatement rewrittenStatement = new ExpressionStatement(rewritten.expression());
                copySourceLocation(statement, rewrittenStatement);
                expanded.add(rewrittenStatement);
                return List.copyOf(expanded);
            }
            if (statement instanceof IfStatement ifStatement) {
                final AwaitExpressionRewrite rewrittenCondition =
                        rewriteAsyncExpressionForAwait(context, ifStatement.condition(), sourceLocation);
                final EmissionContext thenContext = new EmissionContext(context);
                final EmissionContext elseContext = new EmissionContext(context);
                final List<Statement> rewrittenThen =
                        normalizeAsyncStatementsForAwaitExpressions(thenContext, ifStatement.thenBlock());
                final List<Statement> rewrittenElse =
                        normalizeAsyncStatementsForAwaitExpressions(elseContext, ifStatement.elseBlock());
                final List<Statement> expanded = new ArrayList<>(rewrittenCondition.hoistedStatements());
                final IfStatement rewrittenStatement =
                        new IfStatement(rewrittenCondition.expression(), rewrittenThen, rewrittenElse);
                copySourceLocation(statement, rewrittenStatement);
                expanded.add(rewrittenStatement);
                return List.copyOf(expanded);
            }
            if (statement instanceof WhileStatement whileStatement) {
                if (expressionContainsAwait(whileStatement.condition())) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "`await` in async while condition is unsupported in TSJ-13b."
                    );
                }
                final EmissionContext bodyContext = new EmissionContext(context);
                final List<Statement> rewrittenBody =
                        normalizeAsyncStatementsForAwaitExpressions(bodyContext, whileStatement.body());
                final WhileStatement rewrittenStatement = new WhileStatement(whileStatement.condition(), rewrittenBody);
                copySourceLocation(statement, rewrittenStatement);
                return List.of(rewrittenStatement);
            }
            return List.of(statement);
        }

        private AwaitExpressionRewrite rewriteAsyncExpressionForAwait(
                final EmissionContext context,
                final Expression expression,
                final SourceLocation sourceLocation
        ) {
            if (expression instanceof AwaitExpression awaitExpression) {
                final AwaitExpressionRewrite rewrittenInner =
                        rewriteAsyncExpressionForAwait(context, awaitExpression.expression(), sourceLocation);
                final String tempName = context.allocateGeneratedName("awaitExpr");
                final List<Statement> hoisted = new ArrayList<>(rewrittenInner.hoistedStatements());
                final VariableDeclaration hoistedDeclaration =
                        new VariableDeclaration(tempName, new AwaitExpression(rewrittenInner.expression()));
                copySourceLocation(sourceLocation, hoistedDeclaration);
                hoisted.add(hoistedDeclaration);
                return new AwaitExpressionRewrite(new VariableExpression(tempName), List.copyOf(hoisted));
            }
            if (expression instanceof UnaryExpression unaryExpression) {
                final AwaitExpressionRewrite rewrittenOperand =
                        rewriteAsyncExpressionForAwait(context, unaryExpression.expression(), sourceLocation);
                return new AwaitExpressionRewrite(
                        new UnaryExpression(unaryExpression.operator(), rewrittenOperand.expression()),
                        rewrittenOperand.hoistedStatements()
                );
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                final AwaitExpressionRewrite rewrittenLeft =
                        rewriteAsyncExpressionForAwait(context, binaryExpression.left(), sourceLocation);
                final AwaitExpressionRewrite rewrittenRight =
                        rewriteAsyncExpressionForAwait(context, binaryExpression.right(), sourceLocation);
                final List<Statement> hoisted = new ArrayList<>(rewrittenLeft.hoistedStatements());
                hoisted.addAll(rewrittenRight.hoistedStatements());
                return new AwaitExpressionRewrite(
                        new BinaryExpression(rewrittenLeft.expression(), binaryExpression.operator(), rewrittenRight.expression()),
                        List.copyOf(hoisted)
                );
            }
            if (expression instanceof CallExpression callExpression) {
                final AwaitExpressionRewrite rewrittenCallee =
                        rewriteAsyncExpressionForAwait(context, callExpression.callee(), sourceLocation);
                final List<Expression> rewrittenArguments = new ArrayList<>();
                final List<Statement> hoisted = new ArrayList<>(rewrittenCallee.hoistedStatements());
                for (Expression argument : callExpression.arguments()) {
                    final AwaitExpressionRewrite rewrittenArgument =
                            rewriteAsyncExpressionForAwait(context, argument, sourceLocation);
                    hoisted.addAll(rewrittenArgument.hoistedStatements());
                    rewrittenArguments.add(rewrittenArgument.expression());
                }
                return new AwaitExpressionRewrite(
                        new CallExpression(rewrittenCallee.expression(), List.copyOf(rewrittenArguments)),
                        List.copyOf(hoisted)
                );
            }
            if (expression instanceof MemberAccessExpression memberAccessExpression) {
                final AwaitExpressionRewrite rewrittenReceiver =
                        rewriteAsyncExpressionForAwait(context, memberAccessExpression.receiver(), sourceLocation);
                return new AwaitExpressionRewrite(
                        new MemberAccessExpression(rewrittenReceiver.expression(), memberAccessExpression.member()),
                        rewrittenReceiver.hoistedStatements()
                );
            }
            if (expression instanceof NewExpression newExpression) {
                final AwaitExpressionRewrite rewrittenConstructor =
                        rewriteAsyncExpressionForAwait(context, newExpression.constructor(), sourceLocation);
                final List<Expression> rewrittenArguments = new ArrayList<>();
                final List<Statement> hoisted = new ArrayList<>(rewrittenConstructor.hoistedStatements());
                for (Expression argument : newExpression.arguments()) {
                    final AwaitExpressionRewrite rewrittenArgument =
                            rewriteAsyncExpressionForAwait(context, argument, sourceLocation);
                    hoisted.addAll(rewrittenArgument.hoistedStatements());
                    rewrittenArguments.add(rewrittenArgument.expression());
                }
                return new AwaitExpressionRewrite(
                        new NewExpression(rewrittenConstructor.expression(), List.copyOf(rewrittenArguments)),
                        List.copyOf(hoisted)
                );
            }
            if (expression instanceof ObjectLiteralExpression objectLiteralExpression) {
                final List<ObjectLiteralEntry> rewrittenEntries = new ArrayList<>();
                final List<Statement> hoisted = new ArrayList<>();
                for (ObjectLiteralEntry entry : objectLiteralExpression.entries()) {
                    final AwaitExpressionRewrite rewrittenValue =
                            rewriteAsyncExpressionForAwait(context, entry.value(), sourceLocation);
                    hoisted.addAll(rewrittenValue.hoistedStatements());
                    rewrittenEntries.add(new ObjectLiteralEntry(entry.key(), rewrittenValue.expression()));
                }
                return new AwaitExpressionRewrite(new ObjectLiteralExpression(List.copyOf(rewrittenEntries)), List.copyOf(hoisted));
            }
            if (expression instanceof ArrayLiteralExpression arrayLiteralExpression) {
                final List<Expression> rewrittenElements = new ArrayList<>();
                final List<Statement> hoisted = new ArrayList<>();
                for (Expression element : arrayLiteralExpression.elements()) {
                    final AwaitExpressionRewrite rewrittenElement =
                            rewriteAsyncExpressionForAwait(context, element, sourceLocation);
                    hoisted.addAll(rewrittenElement.hoistedStatements());
                    rewrittenElements.add(rewrittenElement.expression());
                }
                return new AwaitExpressionRewrite(new ArrayLiteralExpression(List.copyOf(rewrittenElements)), List.copyOf(hoisted));
            }
            return new AwaitExpressionRewrite(expression, List.of());
        }

        private SourceLocation sourceLocationFor(final Statement statement) {
            return statementLocations.get(statement);
        }

        private void copySourceLocation(final Statement source, final Statement target) {
            copySourceLocation(sourceLocationFor(source), target);
        }

        private void copySourceLocation(final SourceLocation sourceLocation, final Statement target) {
            if (sourceLocation == null) {
                return;
            }
            statementLocations.put(target, sourceLocation);
        }

        private void predeclareAsyncLocalBindings(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final String indent
        ) {
            for (Statement statement : statements) {
                if (statement instanceof VariableDeclaration declaration) {
                    final String cellName = context.declareBinding(declaration.name());
                    builder.append(indent)
                            .append("final dev.tsj.runtime.TsjCell ")
                            .append(cellName)
                            .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());\n");
                }
            }
        }

        private void emitAsyncStatements(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final String indent
        ) {
            emitAsyncStatements(
                    builder,
                    context,
                    statements,
                    indent,
                    "dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.undefined())"
            );
        }

        private void emitAsyncStatements(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final String indent,
                final String completionExpression
        ) {
            emitAsyncStatementsFrom(builder, context, statements, 0, indent, completionExpression);
        }

        private void emitAsyncStatementsFrom(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final int index,
                final String indent
        ) {
            emitAsyncStatementsFrom(
                    builder,
                    context,
                    statements,
                    index,
                    indent,
                    "dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.undefined())"
            );
        }

        private void emitAsyncStatementsFrom(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final int index,
                final String indent,
                final String completionExpression
        ) {
            if (index >= statements.size()) {
                builder.append(indent)
                        .append("return ")
                        .append(completionExpression)
                        .append(";\n");
                return;
            }

            final Statement statement = statements.get(index);
            emitSourceMarker(builder, statement, indent);
            if (statement instanceof ReturnStatement returnStatement) {
                if (returnStatement.expression() instanceof AwaitExpression awaitExpression) {
                    assertNoAwait(
                            awaitExpression.expression(),
                            "Nested `await` in return is unsupported in TSJ-13 subset."
                    );
                    builder.append(indent)
                            .append("return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                            .append(emitExpression(context, awaitExpression.expression()))
                            .append(");\n");
                    return;
                }
                assertNoAwait(returnStatement.expression(), "`await` is only supported as standalone expression.");
                builder.append(indent)
                        .append("return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(emitExpression(context, returnStatement.expression()))
                        .append(");\n");
                return;
            }

            if (statement instanceof IfStatement ifStatement) {
                assertNoAwait(ifStatement.condition(), "`await` in async if condition is unsupported in TSJ-13a.");
                final List<Statement> remaining = statements.subList(index + 1, statements.size());
                final List<Statement> thenWithTail = new ArrayList<>(ifStatement.thenBlock());
                thenWithTail.addAll(remaining);
                final List<Statement> elseWithTail = new ArrayList<>(ifStatement.elseBlock());
                elseWithTail.addAll(remaining);

                builder.append(indent)
                        .append("if (dev.tsj.runtime.TsjRuntime.truthy(")
                        .append(emitExpression(context, ifStatement.condition()))
                        .append(")) {\n");
                final EmissionContext thenContext = new EmissionContext(context);
                predeclareAsyncLocalBindings(builder, thenContext, ifStatement.thenBlock(), indent + "    ");
                emitAsyncStatements(
                        builder,
                        thenContext,
                        List.copyOf(thenWithTail),
                        indent + "    ",
                        completionExpression
                );
                builder.append(indent).append("} else {\n");
                final EmissionContext elseContext = new EmissionContext(context);
                predeclareAsyncLocalBindings(builder, elseContext, ifStatement.elseBlock(), indent + "    ");
                emitAsyncStatements(
                        builder,
                        elseContext,
                        List.copyOf(elseWithTail),
                        indent + "    ",
                        completionExpression
                );
                builder.append(indent).append("}\n");
                return;
            }

            if (statement instanceof WhileStatement whileStatement) {
                emitAsyncWhileStatement(
                        builder,
                        context,
                        statements,
                        index,
                        whileStatement,
                        indent,
                        completionExpression
                );
                return;
            }

            final AwaitSite awaitSite = extractAwaitSite(statement);
            if (awaitSite != null) {
                final String awaitArgs = context.allocateGeneratedName("awaitArgs");
                final String awaitValue = awaitArgs + ".length > 0 ? "
                        + awaitArgs
                        + "[0] : dev.tsj.runtime.TsjRuntime.undefined()";

                builder.append(indent)
                        .append("return dev.tsj.runtime.TsjRuntime.promiseThen(")
                        .append("dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(emitExpression(context, awaitSite.awaitedExpression()))
                        .append("), ")
                        .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(awaitArgs)
                        .append(") -> {\n");
                final boolean continueAfterAwait =
                        emitAwaitResumePrefix(builder, context, awaitSite, awaitValue, indent + "    ");
                if (continueAfterAwait) {
                    emitAsyncStatementsFrom(
                            builder,
                            context,
                            statements,
                            index + 1,
                            indent + "    ",
                            completionExpression
                    );
                }
                builder.append(indent)
                        .append("}, dev.tsj.runtime.TsjRuntime.undefined());\n");
                return;
            }

            emitAsyncImmediateStatement(builder, context, statement, indent);
            if (statement instanceof ThrowStatement) {
                return;
            }
            emitAsyncStatementsFrom(builder, context, statements, index + 1, indent, completionExpression);
        }

        private void emitAsyncWhileStatement(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final int index,
                final WhileStatement whileStatement,
                final String indent,
                final String completionExpression
        ) {
            assertNoAwait(
                    whileStatement.condition(),
                    "`await` in async while condition is unsupported in TSJ-13a."
            );
            final String loopCell = context.allocateGeneratedName("asyncLoopCell");
            final String loopArgs = context.allocateGeneratedName("asyncLoopArgs");
            final String loopCallExpression = "dev.tsj.runtime.TsjRuntime.call(" + loopCell + ".get())";

            builder.append(indent)
                    .append("final dev.tsj.runtime.TsjCell ")
                    .append(loopCell)
                    .append(" = new dev.tsj.runtime.TsjCell(null);\n");
            builder.append(indent)
                    .append(loopCell)
                    .append(".set((dev.tsj.runtime.TsjCallable) (Object... ")
                    .append(loopArgs)
                    .append(") -> {\n");
            builder.append(indent)
                    .append("    if (!dev.tsj.runtime.TsjRuntime.truthy(")
                    .append(emitExpression(context, whileStatement.condition()))
                    .append(")) {\n");
            emitAsyncStatementsFrom(
                    builder,
                    context,
                    statements,
                    index + 1,
                    indent + "        ",
                    completionExpression
            );
            builder.append(indent)
                    .append("    }\n");

            final EmissionContext loopContext = new EmissionContext(context);
            predeclareAsyncLocalBindings(builder, loopContext, whileStatement.body(), indent + "    ");
            emitAsyncStatements(
                    builder,
                    loopContext,
                    whileStatement.body(),
                    indent + "    ",
                    loopCallExpression
            );

            builder.append(indent).append("});\n");
            builder.append(indent).append("return ").append(loopCallExpression).append(";\n");
        }

        private boolean emitAwaitResumePrefix(
                final StringBuilder builder,
                final EmissionContext context,
                final AwaitSite awaitSite,
                final String awaitValueExpression,
                final String indent
        ) {
            switch (awaitSite.kind()) {
                case VAR_DECLARATION -> {
                    final String cellName = context.resolveBinding(awaitSite.variableName());
                    builder.append(indent)
                            .append(cellName)
                            .append(".set(")
                            .append(awaitValueExpression)
                            .append(");\n");
                }
                case ASSIGNMENT -> {
                    final Expression target = awaitSite.assignmentTarget();
                    if (target instanceof VariableExpression variableExpression) {
                        final String cellName = context.resolveBinding(variableExpression.name());
                        builder.append(indent)
                                .append(cellName)
                                .append(".set(")
                                .append(awaitValueExpression)
                                .append(");\n");
                    } else if (target instanceof MemberAccessExpression memberAccessExpression) {
                        builder.append(indent)
                                .append("dev.tsj.runtime.TsjRuntime.setProperty(")
                                .append(emitExpression(context, memberAccessExpression.receiver()))
                                .append(", \"")
                                .append(escapeJava(memberAccessExpression.member()))
                                .append("\", ")
                                .append(awaitValueExpression)
                                .append(");\n");
                    } else {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Unsupported assignment target for await in TSJ-13 subset: "
                                        + target.getClass().getSimpleName()
                        );
                    }
                }
                case EXPRESSION -> {
                    // no-op: awaited value is intentionally ignored
                }
                case CONSOLE_LOG -> builder.append(indent)
                        .append("dev.tsj.runtime.TsjRuntime.print(")
                        .append(awaitValueExpression)
                        .append(");\n");
                case THROW -> {
                    builder.append(indent)
                            .append("throw dev.tsj.runtime.TsjRuntime.raise(")
                            .append(awaitValueExpression)
                            .append(");\n");
                    return false;
                }
                default -> throw new IllegalStateException("Unknown await site kind: " + awaitSite.kind());
            }
            return true;
        }

        private void emitAsyncImmediateStatement(
                final StringBuilder builder,
                final EmissionContext context,
                final Statement statement,
                final String indent
        ) {
            if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                emitFunctionAssignment(builder, context, declarationStatement.declaration(), indent);
                return;
            }
            if (statement instanceof ClassDeclarationStatement classDeclarationStatement) {
                emitClassDeclaration(builder, context, classDeclarationStatement.declaration(), indent);
                return;
            }
            if (statement instanceof VariableDeclaration declaration) {
                assertNoAwait(declaration.expression(), "`await` is only supported as standalone expression.");
                final String cellName = context.resolveBinding(declaration.name());
                builder.append(indent)
                        .append(cellName)
                        .append(".set(")
                        .append(emitExpression(context, declaration.expression()))
                        .append(");\n");
                return;
            }
            if (statement instanceof AssignmentStatement assignment) {
                assertNoAwait(assignment.expression(), "`await` is only supported as standalone expression.");
                emitAssignment(builder, context, assignment, indent);
                return;
            }
            if (statement instanceof ConsoleLogStatement logStatement) {
                assertNoAwait(logStatement.expression(), "`await` is only supported as standalone expression.");
                builder.append(indent)
                        .append("dev.tsj.runtime.TsjRuntime.print(")
                        .append(emitExpression(context, logStatement.expression()))
                        .append(");\n");
                return;
            }
            if (statement instanceof ExpressionStatement expressionStatement) {
                assertNoAwait(expressionStatement.expression(), "`await` is only supported as standalone expression.");
                builder.append(indent)
                        .append(emitExpression(context, expressionStatement.expression()))
                        .append(";\n");
                return;
            }
            if (statement instanceof ThrowStatement throwStatement) {
                assertNoAwait(throwStatement.expression(), "`await` is only supported as standalone expression.");
                builder.append(indent)
                        .append("throw dev.tsj.runtime.TsjRuntime.raise(")
                        .append(emitExpression(context, throwStatement.expression()))
                        .append(");\n");
                return;
            }
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Unsupported statement in async function TSJ-13 subset: "
                            + statement.getClass().getSimpleName()
            );
        }

        private AwaitSite extractAwaitSite(final Statement statement) {
            if (statement instanceof VariableDeclaration declaration
                    && declaration.expression() instanceof AwaitExpression awaitExpression) {
                assertNoAwait(awaitExpression.expression(), "Nested `await` is unsupported in TSJ-13 subset.");
                return new AwaitSite(
                        AwaitSiteKind.VAR_DECLARATION,
                        awaitExpression.expression(),
                        declaration.name(),
                        null
                );
            }
            if (statement instanceof AssignmentStatement assignment
                    && assignment.expression() instanceof AwaitExpression awaitExpression) {
                assertNoAwait(awaitExpression.expression(), "Nested `await` is unsupported in TSJ-13 subset.");
                assertNoAwait(assignment.target(), "Await is unsupported in assignment target.");
                return new AwaitSite(
                        AwaitSiteKind.ASSIGNMENT,
                        awaitExpression.expression(),
                        null,
                        assignment.target()
                );
            }
            if (statement instanceof ConsoleLogStatement logStatement
                    && logStatement.expression() instanceof AwaitExpression awaitExpression) {
                assertNoAwait(awaitExpression.expression(), "Nested `await` is unsupported in TSJ-13 subset.");
                return new AwaitSite(AwaitSiteKind.CONSOLE_LOG, awaitExpression.expression(), null, null);
            }
            if (statement instanceof ExpressionStatement expressionStatement
                    && expressionStatement.expression() instanceof AwaitExpression awaitExpression) {
                assertNoAwait(awaitExpression.expression(), "Nested `await` is unsupported in TSJ-13 subset.");
                return new AwaitSite(AwaitSiteKind.EXPRESSION, awaitExpression.expression(), null, null);
            }
            if (statement instanceof ThrowStatement throwStatement
                    && throwStatement.expression() instanceof AwaitExpression awaitExpression) {
                assertNoAwait(awaitExpression.expression(), "Nested `await` is unsupported in TSJ-13 subset.");
                return new AwaitSite(AwaitSiteKind.THROW, awaitExpression.expression(), null, null);
            }
            if (statementContainsAwait(statement)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported await placement in TSJ-13 subset. Await must be a standalone initializer, "
                                + "assignment, expression, throw, or return."
                );
            }
            return null;
        }

        private void assertNoAwait(final Expression expression, final String message) {
            if (expressionContainsAwait(expression)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        message
                );
            }
        }

        private boolean statementContainsAwait(final Statement statement) {
            if (statement instanceof VariableDeclaration declaration) {
                return expressionContainsAwait(declaration.expression());
            }
            if (statement instanceof AssignmentStatement assignment) {
                return expressionContainsAwait(assignment.target()) || expressionContainsAwait(assignment.expression());
            }
            if (statement instanceof ReturnStatement returnStatement) {
                return expressionContainsAwait(returnStatement.expression());
            }
            if (statement instanceof ThrowStatement throwStatement) {
                return expressionContainsAwait(throwStatement.expression());
            }
            if (statement instanceof ConsoleLogStatement logStatement) {
                return expressionContainsAwait(logStatement.expression());
            }
            if (statement instanceof ExpressionStatement expressionStatement) {
                return expressionContainsAwait(expressionStatement.expression());
            }
            if (statement instanceof IfStatement ifStatement) {
                if (expressionContainsAwait(ifStatement.condition())) {
                    return true;
                }
                for (Statement nested : ifStatement.thenBlock()) {
                    if (statementContainsAwait(nested)) {
                        return true;
                    }
                }
                for (Statement nested : ifStatement.elseBlock()) {
                    if (statementContainsAwait(nested)) {
                        return true;
                    }
                }
                return false;
            }
            if (statement instanceof WhileStatement whileStatement) {
                if (expressionContainsAwait(whileStatement.condition())) {
                    return true;
                }
                for (Statement nested : whileStatement.body()) {
                    if (statementContainsAwait(nested)) {
                        return true;
                    }
                }
                return false;
            }
            if (statement instanceof SuperCallStatement superCallStatement) {
                for (Expression argument : superCallStatement.arguments()) {
                    if (expressionContainsAwait(argument)) {
                        return true;
                    }
                }
                return false;
            }
            if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                return false;
            }
            if (statement instanceof ClassDeclarationStatement classDeclarationStatement) {
                // Await inside class method bodies does not imply top-level await.
                return false;
            }
            return false;
        }

        private boolean expressionContainsAwait(final Expression expression) {
            if (expression instanceof AwaitExpression) {
                return true;
            }
            if (expression instanceof UnaryExpression unaryExpression) {
                return expressionContainsAwait(unaryExpression.expression());
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                return expressionContainsAwait(binaryExpression.left())
                        || expressionContainsAwait(binaryExpression.right());
            }
            if (expression instanceof CallExpression callExpression) {
                if (expressionContainsAwait(callExpression.callee())) {
                    return true;
                }
                for (Expression argument : callExpression.arguments()) {
                    if (expressionContainsAwait(argument)) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof MemberAccessExpression memberAccessExpression) {
                return expressionContainsAwait(memberAccessExpression.receiver());
            }
            if (expression instanceof NewExpression newExpression) {
                if (expressionContainsAwait(newExpression.constructor())) {
                    return true;
                }
                for (Expression argument : newExpression.arguments()) {
                    if (expressionContainsAwait(argument)) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof ObjectLiteralExpression objectLiteralExpression) {
                for (ObjectLiteralEntry entry : objectLiteralExpression.entries()) {
                    if (expressionContainsAwait(entry.value())) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof ArrayLiteralExpression arrayLiteralExpression) {
                for (Expression element : arrayLiteralExpression.elements()) {
                    if (expressionContainsAwait(element)) {
                        return true;
                    }
                }
                return false;
            }
            return false;
        }

        private void emitAssignment(
                final StringBuilder builder,
                final EmissionContext context,
                final AssignmentStatement assignment,
                final String indent
        ) {
            final String valueExpression = emitExpression(context, assignment.expression());
            if (assignment.target() instanceof VariableExpression variableExpression) {
                final String cellName = context.resolveBinding(variableExpression.name());
                builder.append(indent)
                        .append(cellName)
                        .append(".set(")
                        .append(valueExpression)
                        .append(");\n");
                return;
            }
            if (assignment.target() instanceof MemberAccessExpression memberAccessExpression) {
                builder.append(indent)
                        .append("dev.tsj.runtime.TsjRuntime.setProperty(")
                        .append(emitExpression(context, memberAccessExpression.receiver()))
                        .append(", \"")
                        .append(escapeJava(memberAccessExpression.member()))
                        .append("\", ")
                        .append(valueExpression)
                        .append(");\n");
                return;
            }
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Unsupported assignment target in TSJ-9 subset: "
                            + assignment.target().getClass().getSimpleName()
            );
        }

        private void emitSuperConstructorCall(
                final StringBuilder builder,
                final EmissionContext context,
                final SuperCallStatement superCallStatement,
                final String indent
        ) {
            if (!context.isConstructorContext()) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "`super(...)` is only valid in class constructors in TSJ-9 subset."
                );
            }
            final String superClassExpression = context.resolveSuperClassExpression();
            if (superClassExpression == null) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Cannot use `super(...)` in a class without a base class in TSJ-9 subset."
                );
            }
            builder.append(indent)
                    .append("dev.tsj.runtime.TsjRuntime.asClass(")
                    .append(superClassExpression)
                    .append(").invokeConstructor(")
                    .append(context.resolveThisReference());
            for (Expression argument : superCallStatement.arguments()) {
                builder.append(", ").append(emitExpression(context, argument));
            }
            builder.append(");\n");
        }

        private String emitExpression(final EmissionContext context, final Expression expression) {
            if (expression instanceof NumberLiteral numberLiteral) {
                if (numberLiteral.value().contains(".")) {
                    return "Double.valueOf(" + numberLiteral.value() + "d)";
                }
                return "Integer.valueOf(" + numberLiteral.value() + ")";
            }
            if (expression instanceof StringLiteral stringLiteral) {
                return "\"" + escapeJava(stringLiteral.value()) + "\"";
            }
            if (expression instanceof BooleanLiteral booleanLiteral) {
                return booleanLiteral.value() ? "Boolean.TRUE" : "Boolean.FALSE";
            }
            if (expression instanceof NullLiteral) {
                return "null";
            }
            if (expression instanceof UndefinedLiteral) {
                return "dev.tsj.runtime.TsjRuntime.undefined()";
            }
            if (expression instanceof VariableExpression variableExpression) {
                return context.resolveBinding(variableExpression.name()) + ".get()";
            }
            if (expression instanceof ThisExpression) {
                return context.resolveThisReference();
            }
            if (expression instanceof UnaryExpression unaryExpression) {
                if ("-".equals(unaryExpression.operator())) {
                    return "dev.tsj.runtime.TsjRuntime.negate("
                            + emitExpression(context, unaryExpression.expression())
                            + ")";
                }
                if ("!".equals(unaryExpression.operator())) {
                    return "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.truthy("
                            + emitExpression(context, unaryExpression.expression())
                            + "))";
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported unary operator: " + unaryExpression.operator()
                );
            }
            if (expression instanceof FunctionExpression functionExpression) {
                return emitFunctionExpression(context, functionExpression);
            }
            if (expression instanceof AwaitExpression) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "`await` is only valid inside async function lowering in TSJ-13 subset."
                );
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                final String left = emitExpression(context, binaryExpression.left());
                final String right = emitExpression(context, binaryExpression.right());
                return switch (binaryExpression.operator()) {
                    case "+" -> "dev.tsj.runtime.TsjRuntime.add(" + left + ", " + right + ")";
                    case "-" -> "dev.tsj.runtime.TsjRuntime.subtract(" + left + ", " + right + ")";
                    case "*" -> "dev.tsj.runtime.TsjRuntime.multiply(" + left + ", " + right + ")";
                    case "/" -> "dev.tsj.runtime.TsjRuntime.divide(" + left + ", " + right + ")";
                    case "<" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(" + left + ", " + right + "))";
                    case "<=" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThanOrEqual("
                            + left + ", " + right + "))";
                    case ">" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.greaterThan(" + left + ", " + right + "))";
                    case ">=" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.greaterThanOrEqual("
                            + left + ", " + right + "))";
                    case "==" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.abstractEquals("
                            + left + ", " + right + "))";
                    case "===" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals("
                            + left + ", " + right + "))";
                    case "!=" -> "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.abstractEquals("
                            + left + ", " + right + "))";
                    case "!==" -> "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.strictEquals("
                            + left + ", " + right + "))";
                    default -> throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Unsupported binary operator: " + binaryExpression.operator()
                    );
                };
            }
            if (expression instanceof MemberAccessExpression memberAccessExpression) {
                final String cacheField = allocatePropertyCacheField(memberAccessExpression.member());
                return "dev.tsj.runtime.TsjRuntime.getPropertyCached("
                        + cacheField
                        + ", "
                        + emitExpression(context, memberAccessExpression.receiver())
                        + ", \""
                        + escapeJava(memberAccessExpression.member())
                        + "\")";
            }
            if (expression instanceof NewExpression newExpression) {
                final String constructor = emitExpression(context, newExpression.constructor());
                final List<String> renderedArgs = new ArrayList<>();
                for (Expression argument : newExpression.arguments()) {
                    renderedArgs.add(emitExpression(context, argument));
                }
                if (renderedArgs.isEmpty()) {
                    return "dev.tsj.runtime.TsjRuntime.construct(" + constructor + ")";
                }
                return "dev.tsj.runtime.TsjRuntime.construct("
                        + constructor
                        + ", "
                        + String.join(", ", renderedArgs)
                        + ")";
            }
            if (expression instanceof ObjectLiteralExpression objectLiteralExpression) {
                final List<String> keyValueSegments = new ArrayList<>();
                for (ObjectLiteralEntry entry : objectLiteralExpression.entries()) {
                    keyValueSegments.add("\"" + escapeJava(entry.key()) + "\"");
                    keyValueSegments.add(emitExpression(context, entry.value()));
                }
                if (keyValueSegments.isEmpty()) {
                    return "dev.tsj.runtime.TsjRuntime.objectLiteral()";
                }
                return "dev.tsj.runtime.TsjRuntime.objectLiteral(" + String.join(", ", keyValueSegments) + ")";
            }
            if (expression instanceof ArrayLiteralExpression arrayLiteralExpression) {
                final List<String> renderedElements = new ArrayList<>();
                for (Expression element : arrayLiteralExpression.elements()) {
                    renderedElements.add(emitExpression(context, element));
                }
                if (renderedElements.isEmpty()) {
                    return "dev.tsj.runtime.TsjRuntime.arrayLiteral()";
                }
                return "dev.tsj.runtime.TsjRuntime.arrayLiteral(" + String.join(", ", renderedElements) + ")";
            }
            if (expression instanceof CallExpression callExpression) {
                final List<String> renderedArgs = new ArrayList<>();
                for (Expression argument : callExpression.arguments()) {
                    renderedArgs.add(emitExpression(context, argument));
                }
                if (callExpression.callee() instanceof MemberAccessExpression memberAccessExpression) {
                    final String receiver = emitExpression(context, memberAccessExpression.receiver());
                    final String methodName = "\"" + escapeJava(memberAccessExpression.member()) + "\"";
                    if (renderedArgs.isEmpty()) {
                        return "dev.tsj.runtime.TsjRuntime.invokeMember(" + receiver + ", " + methodName + ")";
                    }
                    return "dev.tsj.runtime.TsjRuntime.invokeMember("
                            + receiver
                            + ", "
                            + methodName
                            + ", "
                            + String.join(", ", renderedArgs)
                            + ")";
                }
                final String callee = emitExpression(context, callExpression.callee());
                if (renderedArgs.isEmpty()) {
                    return "dev.tsj.runtime.TsjRuntime.call(" + callee + ")";
                }
                return "dev.tsj.runtime.TsjRuntime.call(" + callee + ", " + String.join(", ", renderedArgs) + ")";
            }
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Unsupported expression node: " + expression.getClass().getSimpleName()
            );
        }

        private String sanitizeIdentifier(final String identifier) {
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < identifier.length(); i++) {
                final char ch = identifier.charAt(i);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$') {
                    builder.append(ch);
                } else {
                    builder.append('_');
                }
            }
            if (builder.isEmpty()) {
                builder.append("value");
            }
            if (Character.isDigit(builder.charAt(0))) {
                builder.insert(0, '_');
            }
            String value = builder.toString();
            if (JAVA_KEYWORDS.contains(value)) {
                value = value + "_ts";
            }
            return value;
        }

        private String escapeJava(final String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private String allocatePropertyCacheField(final String propertyName) {
            final String fieldName = "PROPERTY_CACHE_" + propertyCacheCounter;
            propertyCacheCounter++;
            propertyCacheFieldDeclarations.add(
                    "private static final dev.tsj.runtime.TsjPropertyAccessCache "
                            + fieldName
                            + " = new dev.tsj.runtime.TsjPropertyAccessCache(\""
                            + escapeJava(propertyName)
                            + "\");"
            );
            return fieldName;
        }

        private record AwaitExpressionRewrite(
                Expression expression,
                List<Statement> hoistedStatements
        ) {
        }

        private enum AwaitSiteKind {
            VAR_DECLARATION,
            ASSIGNMENT,
            CONSOLE_LOG,
            EXPRESSION,
            THROW
        }

        private record AwaitSite(
                AwaitSiteKind kind,
                Expression awaitedExpression,
                String variableName,
                Expression assignmentTarget
        ) {
        }

        private final class EmissionContext {
            private final EmissionContext parent;
            private final Map<String, String> bindings;
            private final Set<String> generatedNames;
            private final String thisReference;
            private final String superClassExpression;
            private final boolean constructorContext;

            private EmissionContext(final EmissionContext parent) {
                this(
                        parent,
                        parent != null ? parent.thisReference : null,
                        parent != null ? parent.superClassExpression : null,
                        parent != null && parent.constructorContext
                );
            }

            private EmissionContext(
                    final EmissionContext parent,
                    final String thisReference,
                    final String superClassExpression,
                    final boolean constructorContext
            ) {
                this.parent = parent;
                this.bindings = new LinkedHashMap<>();
                this.generatedNames = new LinkedHashSet<>();
                this.thisReference = thisReference;
                this.superClassExpression = superClassExpression;
                this.constructorContext = constructorContext;
            }

            private String predeclareBinding(final String sourceName) {
                if (bindings.containsKey(sourceName)) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Duplicate declaration in scope: " + sourceName
                    );
                }
                final String cellName = allocateUniqueName(sanitizeIdentifier(sourceName) + "_cell");
                bindings.put(sourceName, cellName);
                return cellName;
            }

            private String declareBinding(final String sourceName) {
                if (bindings.containsKey(sourceName)) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Duplicate declaration in scope: " + sourceName
                    );
                }
                final String cellName = allocateUniqueName(sanitizeIdentifier(sourceName) + "_cell");
                bindings.put(sourceName, cellName);
                return cellName;
            }

            private String resolveBinding(final String sourceName) {
                if (bindings.containsKey(sourceName)) {
                    return bindings.get(sourceName);
                }
                if (parent != null) {
                    return parent.resolveBinding(sourceName);
                }
                if ("Promise".equals(sourceName)) {
                    return "PROMISE_BUILTIN_CELL";
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unresolved identifier in TSJ-9 subset: " + sourceName
                );
            }

            private String allocateGeneratedName(final String prefix) {
                final String base = sanitizeIdentifier(prefix);
                final String allocated = allocateUniqueName(base);
                generatedNames.add(allocated);
                return allocated;
            }

            private String allocateUniqueName(final String baseName) {
                String candidate = baseName;
                int counter = 1;
                while (isNameUsed(candidate)) {
                    candidate = baseName + "_" + counter;
                    counter++;
                }
                return candidate;
            }

            private boolean isNameUsed(final String value) {
                if (bindings.containsValue(value) || generatedNames.contains(value)) {
                    return true;
                }
                if (parent != null) {
                    return parent.isNameUsed(value);
                }
                return false;
            }

            private boolean isConstructorContext() {
                return constructorContext;
            }

            private String resolveThisReference() {
                if (thisReference != null) {
                    return thisReference;
                }
                if (parent != null) {
                    return parent.resolveThisReference();
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "`this` is only valid inside class methods in TSJ-9 subset."
                );
            }

            private String resolveSuperClassExpression() {
                if (superClassExpression != null) {
                    return superClassExpression;
                }
                if (parent != null) {
                    return parent.resolveSuperClassExpression();
                }
                return null;
            }
        }
    }
}
