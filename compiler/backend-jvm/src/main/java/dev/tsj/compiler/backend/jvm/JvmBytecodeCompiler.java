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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-9 JVM backend compiler for expression/statement subset with closures and class/object support.
 */
public final class JvmBytecodeCompiler {
    private static final Set<String> KEYWORDS = Set.of(
            "function", "const", "let", "var", "if", "else", "while", "return",
            "true", "false", "null", "for", "export", "import", "from",
            "class", "extends", "this", "super", "new", "undefined"
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

        final String sourceText;
        try {
            sourceText = Files.readString(normalizedSource, UTF_8);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-IO",
                    "Failed to read TypeScript source: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }

        final Program program = new Parser(tokenize(sourceText)).parseProgram();
        final String classSimpleName = toPascalCase(stripExtension(fileName)) + "Program";
        final String className = OUTPUT_PACKAGE + "." + classSimpleName;
        final String javaSource = new JavaSourceGenerator(OUTPUT_PACKAGE, classSimpleName, program).generate();

        final Path normalizedOutput = outputDir.toAbsolutePath().normalize();
        final Path classesDir = normalizedOutput.resolve("classes");
        final Path generatedSource = normalizedOutput.resolve("generated-src")
                .resolve(OUTPUT_PACKAGE.replace('.', '/'))
                .resolve(classSimpleName + ".java");
        try {
            Files.createDirectories(classesDir);
            Files.createDirectories(generatedSource.getParent());
            Files.writeString(generatedSource, javaSource, UTF_8);
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
        return new JvmCompiledArtifact(normalizedSource, classesDir, className, classFile);
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
                    || "||".equals(two)) {
                tokens.add(new Token(TokenType.SYMBOL, two, line, column));
                index += 2;
                column += 2;
                continue;
            }
            final String one = Character.toString(current);
            if ("(){};,.+-*/<>!=:".contains(one)) {
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

    private record ConsoleLogStatement(Expression expression) implements Statement {
    }

    private record ExpressionStatement(Expression expression) implements Statement {
    }

    private record FunctionDeclaration(String name, List<String> parameters, List<Statement> body) {
    }

    private record ClassDeclaration(
            String name,
            String superClassName,
            List<String> fieldNames,
            ClassMethod constructorMethod,
            List<ClassMethod> methods
    ) {
    }

    private record ClassMethod(String name, List<String> parameters, List<Statement> body) {
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
            BinaryExpression,
            CallExpression,
            MemberAccessExpression,
            NewExpression,
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

    private record BinaryExpression(Expression left, String operator, Expression right) implements Expression {
    }

    private record CallExpression(Expression callee, List<Expression> arguments) implements Expression {
    }

    private record MemberAccessExpression(Expression receiver, String member) implements Expression {
    }

    private record NewExpression(Expression constructor, List<Expression> arguments) implements Expression {
    }

    private record ObjectLiteralExpression(List<ObjectLiteralEntry> entries) implements Expression {
    }

    private record ObjectLiteralEntry(String key, Expression value) {
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int index;

        private Parser(final List<Token> tokens) {
            this.tokens = tokens;
            this.index = 0;
        }

        private Program parseProgram() {
            final List<Statement> statements = new ArrayList<>();
            while (!isAtEnd()) {
                if (matchKeyword("export")) {
                    if (matchKeyword("function")) {
                        statements.add(new FunctionDeclarationStatement(parseFunctionDeclaration()));
                        continue;
                    }
                    if (matchKeyword("class")) {
                        statements.add(new ClassDeclarationStatement(parseClassDeclaration()));
                        continue;
                    }
                    if (matchKeyword("const") || matchKeyword("let") || matchKeyword("var")) {
                        statements.add(parseVariableDeclaration());
                        continue;
                    }
                    final Token token = current();
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Unsupported export form in TSJ-9 subset.",
                            token.line(),
                            token.column()
                    );
                }
                statements.add(parseStatement(false));
            }
            return new Program(List.copyOf(statements));
        }

        private Statement parseStatement(final boolean insideFunction) {
            if (matchKeyword("const") || matchKeyword("let") || matchKeyword("var")) {
                return parseVariableDeclaration();
            }
            if (matchKeyword("function")) {
                return new FunctionDeclarationStatement(parseFunctionDeclaration());
            }
            if (matchKeyword("class")) {
                return new ClassDeclarationStatement(parseClassDeclaration());
            }
            if (matchKeyword("if")) {
                return parseIfStatement(insideFunction);
            }
            if (matchKeyword("while")) {
                return parseWhileStatement(insideFunction);
            }
            if (matchKeyword("super")) {
                return parseSuperCallStatement();
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
                return parseReturnStatement();
            }
            if (isConsoleLogStart()) {
                return parseConsoleLog();
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
            return parseExpressionOrAssignmentStatement();
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

        private FunctionDeclaration parseFunctionDeclaration() {
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
            return new FunctionDeclaration(nameToken.text(), List.copyOf(parameters), List.copyOf(body));
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
                final Token memberName = consumeIdentifier("Expected class member name.");
                if (matchSymbol(":")) {
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
                final ClassMethod method = new ClassMethod(memberName.text(), List.copyOf(parameters), List.copyOf(body));
                if ("constructor".equals(memberName.text())) {
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
            if (matchType(TokenType.IDENTIFIER)) {
                return new VariableExpression(previous().text());
            }
            if (matchSymbol("{")) {
                return parseObjectLiteral();
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
                    final String key;
                    if (matchType(TokenType.IDENTIFIER)) {
                        key = previous().text();
                    } else if (matchType(TokenType.STRING)) {
                        key = previous().text();
                    } else {
                        final Token token = current();
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-PARSE",
                                "Expected object literal property name.",
                                token.line(),
                                token.column()
                        );
                    }
                    consumeSymbol(":", "Expected `:` after object literal property name.");
                    entries.add(new ObjectLiteralEntry(key, parseExpression()));
                } while (matchSymbol(","));
            }
            consumeSymbol("}", "Expected `}` to close object literal.");
            return new ObjectLiteralExpression(List.copyOf(entries));
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
                    || "new".equals(keyword);
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

        private JavaSourceGenerator(
                final String packageName,
                final String classSimpleName,
                final Program program
        ) {
            this.packageName = packageName;
            this.classSimpleName = classSimpleName;
            this.program = program;
        }

        private String generate() {
            final StringBuilder builder = new StringBuilder();
            builder.append("package ").append(packageName).append(";\n\n");
            builder.append("public final class ").append(classSimpleName).append(" {\n");
            builder.append("    private ").append(classSimpleName).append("() {\n");
            builder.append("    }\n\n");
            builder.append("    public static void main(String[] args) {\n");
            final EmissionContext mainContext = new EmissionContext(null);
            emitStatements(builder, mainContext, program.statements(), "        ", false);
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

            for (Statement statement : statements) {
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

        private void emitFunctionAssignment(
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
            emitParameterCells(builder, functionContext, declaration.parameters(), argsVar, indent + "    ");

            emitStatements(builder, functionContext, declaration.body(), indent + "    ", true);
            if (declaration.body().isEmpty()
                    || !(declaration.body().get(declaration.body().size() - 1) instanceof ReturnStatement)) {
                builder.append(indent).append("    return null;\n");
            }

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
            emitStatements(builder, methodContext, method.body(), indent + "    ", true);
            if (method.body().isEmpty()
                    || !(method.body().get(method.body().size() - 1) instanceof ReturnStatement)) {
                builder.append(indent).append("    return null;\n");
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
                return "dev.tsj.runtime.TsjRuntime.getProperty("
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
