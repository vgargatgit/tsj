package dev.tsj.compiler.backend.jvm;

import com.fasterxml.jackson.databind.JsonNode;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
import java.util.stream.Stream;
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
    private static final Pattern DEFAULT_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+([A-Za-z_$][A-Za-z0-9_$]*)(?:\\s*,\\s*\\{([^}]*)})?\\s*from\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Pattern NAMESPACE_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s*\\*\\s*as\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*from\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Pattern SIDE_EFFECT_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Pattern INTEROP_MODULE_PATTERN = Pattern.compile(
            "^java:([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)$"
    );
    private static final Set<String> KEYWORDS = Set.of(
            "function", "const", "let", "var", "if", "else", "while", "return",
            "true", "false", "null", "for", "export", "import", "from",
            "class", "extends", "this", "super", "new", "undefined",
            "async", "await", "throw", "delete", "break", "continue", "do",
            "typeof", "in", "instanceof"
    );
    private static final Set<String> IMPLICIT_GLOBAL_BUILTINS = Set.of(
            "Error",
            "Object",
            "Symbol",
            "Reflect",
            "Array",
            "String",
            "Number",
            "Boolean",
            "BigInt",
            "Math",
            "Date",
            "RegExp"
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
    private static final String FEATURE_DYNAMIC_IMPORT = "TSJ15-DYNAMIC-IMPORT";
    private static final String FEATURE_EVAL = "TSJ15-EVAL";
    private static final String FEATURE_FUNCTION_CONSTRUCTOR = "TSJ15-FUNCTION-CONSTRUCTOR";
    private static final String FEATURE_INTEROP_SYNTAX = "TSJ26-INTEROP-SYNTAX";
    private static final String FEATURE_INTEROP_MODULE_SPECIFIER = "TSJ26-INTEROP-MODULE-SPECIFIER";
    private static final String FEATURE_INTEROP_BINDING = "TSJ26-INTEROP-BINDING";
    private static final String ASYNC_BREAK_SIGNAL_FIELD = "__TSJ_ASYNC_BREAK_SIGNAL";
    private static final String ASYNC_CONTINUE_SIGNAL_FIELD = "__TSJ_ASYNC_CONTINUE_SIGNAL";
    private static final String TOP_LEVEL_CLASS_MAP_FIELD = "__TSJ_TOP_LEVEL_CLASSES";
    private static final String BOOTSTRAP_GUARD_FIELD = "__TSJ_BOOTSTRAPPED";
    private static final String ERROR_BUILTIN_CELL_FIELD = "ERROR_BUILTIN_CELL";
    private static final String STRING_BUILTIN_CELL_FIELD = "STRING_BUILTIN_CELL";
    private static final String JSON_BUILTIN_CELL_FIELD = "JSON_BUILTIN_CELL";
    private static final String MATH_BUILTIN_CELL_FIELD = "MATH_BUILTIN_CELL";
    private static final String NUMBER_BUILTIN_CELL_FIELD = "NUMBER_BUILTIN_CELL";
    private static final String BIGINT_BUILTIN_CELL_FIELD = "BIGINT_BUILTIN_CELL";
    private static final String SYMBOL_BUILTIN_CELL_FIELD = "SYMBOL_BUILTIN_CELL";
    private static final String OBJECT_BUILTIN_CELL_FIELD = "OBJECT_BUILTIN_CELL";
    private static final String REFLECT_BUILTIN_CELL_FIELD = "REFLECT_BUILTIN_CELL";
    private static final String PROXY_BUILTIN_CELL_FIELD = "PROXY_BUILTIN_CELL";
    private static final String ARRAY_BUILTIN_CELL_FIELD = "ARRAY_BUILTIN_CELL";
    private static final String MAP_BUILTIN_CELL_FIELD = "MAP_BUILTIN_CELL";
    private static final String SET_BUILTIN_CELL_FIELD = "SET_BUILTIN_CELL";
    private static final String WEAK_MAP_BUILTIN_CELL_FIELD = "WEAK_MAP_BUILTIN_CELL";
    private static final String WEAK_SET_BUILTIN_CELL_FIELD = "WEAK_SET_BUILTIN_CELL";
    private static final String WEAK_REF_BUILTIN_CELL_FIELD = "WEAK_REF_BUILTIN_CELL";
    private static final String DATE_BUILTIN_CELL_FIELD = "DATE_BUILTIN_CELL";
    private static final String REGEXP_BUILTIN_CELL_FIELD = "REGEXP_BUILTIN_CELL";
    private static final String AGGREGATE_ERROR_BUILTIN_CELL_FIELD = "AGGREGATE_ERROR_BUILTIN_CELL";
    private static final String TYPE_ERROR_BUILTIN_CELL_FIELD = "TYPE_ERROR_BUILTIN_CELL";
    private static final String RANGE_ERROR_BUILTIN_CELL_FIELD = "RANGE_ERROR_BUILTIN_CELL";
    private static final String PARSE_INT_BUILTIN_CELL_FIELD = "PARSE_INT_BUILTIN_CELL";
    private static final String PARSE_FLOAT_BUILTIN_CELL_FIELD = "PARSE_FLOAT_BUILTIN_CELL";
    private static final String INFINITY_BUILTIN_CELL_FIELD = "INFINITY_BUILTIN_CELL";
    private static final String NAN_BUILTIN_CELL_FIELD = "NAN_BUILTIN_CELL";
    private static final String UNDEFINED_BUILTIN_CELL_FIELD = "UNDEFINED_BUILTIN_CELL";
    private static final String GUIDANCE_DYNAMIC_IMPORT =
            "Use static relative imports (`import { x } from \"./m.ts\"`) in TSJ MVP.";
    private static final String GUIDANCE_EVAL =
            "Replace runtime code evaluation with explicit functions or precompiled modules.";
    private static final String GUIDANCE_FUNCTION_CONSTRUCTOR =
            "Replace runtime code evaluation with explicit functions or precompiled modules.";
    private static final String GUIDANCE_INTEROP_SYNTAX =
            "Use named imports with java modules, for example `import { max } from \"java:java.lang.Math\"`.";
    private static final String GUIDANCE_INTEROP_MODULE_SPECIFIER =
            "Use `java:<fully.qualified.ClassName>` (without method names in the module specifier).";
    private static final String GUIDANCE_INTEROP_BINDING =
            "Interop bindings must be valid identifiers, for example `{ max, min as minimum }`.";
    private static final String LEGACY_TOKENIZER_PROPERTY = "tsj.backend.legacyTokenizer";
    private static final String AST_NO_FALLBACK_PROPERTY = "tsj.backend.astNoFallback";

    private static final String OUTPUT_PACKAGE = "dev.tsj.generated";

    public JvmCompiledArtifact compile(final Path sourceFile, final Path outputDir) {
        return compile(sourceFile, outputDir, JvmOptimizationOptions.defaults());
    }

    public JvmCompiledArtifact compile(
            final Path sourceFile,
            final Path outputDir,
            final JvmOptimizationOptions optimizationOptions
    ) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(optimizationOptions, "optimizationOptions");

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

        final ParseResult parseResult;
        if (fileName.endsWith(".d.ts")) {
            parseResult = new ParseResult(new Program(List.of()), Map.of());
        } else {
            final BundleResult bundleResult = bundleModules(normalizedSource);
            parseResult = parseProgram(bundleResult.sourceText(), normalizedSource, bundleResult);
        }
        final Program parsedProgram = parseResult.program();
        final Map<Statement, SourceLocation> parsedStatementLocations = parseResult.statementLocations();
        final ProgramOptimizationResult optimizationResult = new ProgramOptimizer(
                optimizationOptions,
                parsedStatementLocations
        ).optimize(parsedProgram);
        final Program program = optimizationResult.program();
        final String classSimpleName = toPascalCase(stripExtension(fileName)) + "Program";
        final String className = OUTPUT_PACKAGE + "." + classSimpleName;
        final String javaSource = new JavaSourceGenerator(
                OUTPUT_PACKAGE,
                classSimpleName,
                program,
                optimizationResult.statementLocations()
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

    private static String preprocessTsj34Decorators(final String sourceText) {
        final String[] lines = sourceText.split("\n", -1);
        final StringBuilder builder = new StringBuilder(sourceText.length());
        for (int index = 0; index < lines.length; index++) {
            final String line = lines[index];
            final String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                builder.append("// TSJ-34 decorator stripped for backend parser");
            } else {
                builder.append(stripInlineParameterDecorators(line));
            }
            if (index + 1 < lines.length) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static String stripInlineParameterDecorators(final String line) {
        if (line.indexOf('@') < 0 || line.indexOf('(') < 0) {
            return line;
        }
        final StringBuilder builder = new StringBuilder(line.length());
        int index = 0;
        boolean inString = false;
        char quote = 0;
        while (index < line.length()) {
            final char value = line.charAt(index);
            if (inString) {
                builder.append(value);
                if (value == '\\' && index + 1 < line.length()) {
                    index++;
                    builder.append(line.charAt(index));
                } else if (value == quote) {
                    inString = false;
                }
                index++;
                continue;
            }
            if (value == '"' || value == '\'') {
                inString = true;
                quote = value;
                builder.append(value);
                index++;
                continue;
            }
            if (value != '@') {
                builder.append(value);
                index++;
                continue;
            }
            int cursor = index + 1;
            if (cursor >= line.length()) {
                builder.append(value);
                index++;
                continue;
            }
            final char first = line.charAt(cursor);
            if (!Character.isJavaIdentifierStart(first) && first != '$') {
                builder.append(value);
                index++;
                continue;
            }
            cursor++;
            while (cursor < line.length()) {
                final char decoratorChar = line.charAt(cursor);
                if (Character.isJavaIdentifierPart(decoratorChar) || decoratorChar == '$') {
                    cursor++;
                    continue;
                }
                break;
            }
            if (cursor < line.length() && line.charAt(cursor) == '(') {
                int depth = 1;
                cursor++;
                boolean argsInString = false;
                char argsQuote = 0;
                while (cursor < line.length() && depth > 0) {
                    final char argsChar = line.charAt(cursor);
                    if (argsInString) {
                        if (argsChar == '\\') {
                            cursor += 2;
                            continue;
                        }
                        if (argsChar == argsQuote) {
                            argsInString = false;
                        }
                        cursor++;
                        continue;
                    }
                    if (argsChar == '"' || argsChar == '\'') {
                        argsInString = true;
                        argsQuote = argsChar;
                        cursor++;
                        continue;
                    }
                    if (argsChar == '(') {
                        depth++;
                    } else if (argsChar == ')') {
                        depth--;
                    }
                    cursor++;
                }
                if (depth > 0) {
                    builder.append(value);
                    index++;
                    continue;
                }
            }
            while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
                cursor++;
            }
            index = cursor;
        }
        return builder.toString();
    }

    private static BundleResult bundleModules(final Path entryFile) {
        final Map<Path, ModuleSource> orderedModules = new LinkedHashMap<>();
        collectModule(entryFile, orderedModules, new LinkedHashSet<>());
        final List<ModuleSource> modules = List.copyOf(orderedModules.values());
        if (isPassthroughBundle(modules)) {
            final ModuleSource module = modules.get(0);
            final StringBuilder passthroughBuilder = new StringBuilder();
            final List<SourceLineOrigin> passthroughOrigins = new ArrayList<>();
            appendBundledLine(passthroughBuilder, passthroughOrigins, "// module: " + module.sourceFile(), null, -1);
            for (int index = 0; index < module.bodyLines().size(); index++) {
                appendBundledLine(
                        passthroughBuilder,
                        passthroughOrigins,
                        module.bodyLines().get(index),
                        module.sourceFile(),
                        module.bodyLineNumbers().get(index)
                );
            }
            appendBundledLine(passthroughBuilder, passthroughOrigins, "", null, -1);
            return new BundleResult(passthroughBuilder.toString(), List.copyOf(passthroughOrigins));
        }
        final Map<Path, String> initFunctionByModule = new LinkedHashMap<>();
        final Map<Path, Map<String, String>> exportSymbolsByModule = new LinkedHashMap<>();

        for (int index = 0; index < modules.size(); index++) {
            final ModuleSource module = modules.get(index);
            initFunctionByModule.put(module.sourceFile(), "__tsj_init_module_" + index);
            final Map<String, String> exportSymbols = new LinkedHashMap<>();
            for (ExportBinding exportBinding : module.exportBindings()) {
                exportSymbols.putIfAbsent(
                        exportBinding.exportName(),
                        exportGlobalSymbol(index, exportBinding.exportName())
                );
            }
            exportSymbolsByModule.put(module.sourceFile(), exportSymbols);
        }

        final StringBuilder builder = new StringBuilder();
        final List<SourceLineOrigin> lineOrigins = new ArrayList<>();

        for (ModuleSource module : modules) {
            final Map<String, String> moduleExports = exportSymbolsByModule.get(module.sourceFile());
            for (String exportSymbol : moduleExports.values()) {
                appendBundledLine(builder, lineOrigins, "let " + exportSymbol + " = undefined;", null, -1);
            }
        }
        if (!modules.isEmpty()) {
            appendBundledLine(builder, lineOrigins, "", null, -1);
        }

        for (ModuleSource module : modules) {
            final String initFunctionName = initFunctionByModule.get(module.sourceFile());
            final boolean asyncInit = module.requiresAsyncInit();
            appendBundledLine(builder, lineOrigins, "// module: " + module.sourceFile(), null, -1);
            appendBundledLine(
                    builder,
                    lineOrigins,
                    (asyncInit ? "async function " : "function ") + initFunctionName + "() {",
                    null,
                    -1
            );

            for (ModuleImport moduleImport : module.imports()) {
                if (moduleImport.kind() == ModuleImportKind.SIDE_EFFECT) {
                    continue;
                }
                if (moduleImport.kind() == ModuleImportKind.INTEROP) {
                    for (ImportBinding binding : moduleImport.bindings()) {
                        appendBundledLine(
                                builder,
                                lineOrigins,
                                "  const " + binding.localName() + " = __tsj_java_binding(\""
                                        + escapeJavaLiteral(moduleImport.interopClassName())
                                        + "\", \""
                                        + escapeJavaLiteral(binding.importedName())
                                        + "\");",
                                module.sourceFile(),
                                moduleImport.line()
                        );
                    }
                    continue;
                }
                if (moduleImport.kind() == ModuleImportKind.NAMESPACE) {
                    final Map<String, String> dependencyExports = exportSymbolsByModule.get(moduleImport.dependency());
                    appendBundledLine(
                            builder,
                            lineOrigins,
                            "  const " + moduleImport.namespaceLocalName() + " = "
                                    + buildNamespaceImportObjectLiteral(dependencyExports) + ";",
                            module.sourceFile(),
                            moduleImport.line()
                    );
                    continue;
                }
                final Map<String, String> dependencyExports = exportSymbolsByModule.get(moduleImport.dependency());
                for (ImportBinding binding : moduleImport.bindings()) {
                    final String exportSymbol = dependencyExports.get(binding.importedName());
                    if (exportSymbol == null) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Imported binding `" + binding.importedName() + "` is not exported by "
                                        + moduleImport.dependency().getFileName()
                                        + " (imported from " + module.sourceFile().getFileName() + ").",
                                moduleImport.line(),
                                1
                        );
                    }
                    appendBundledLine(
                            builder,
                            lineOrigins,
                            "  const " + binding.localName() + " = " + exportSymbol + ";",
                            module.sourceFile(),
                            moduleImport.line()
                    );
                }
            }

            for (int index = 0; index < module.bodyLines().size(); index++) {
                appendBundledLine(
                        builder,
                        lineOrigins,
                        "  " + module.bodyLines().get(index),
                        module.sourceFile(),
                        module.bodyLineNumbers().get(index)
                );
            }

            final Map<String, String> moduleExports = exportSymbolsByModule.get(module.sourceFile());
            for (ExportBinding exportBinding : module.exportBindings()) {
                appendBundledLine(
                        builder,
                        lineOrigins,
                        "  " + moduleExports.get(exportBinding.exportName()) + " = "
                                + exportBinding.localName() + ";",
                        null,
                        -1
                );
            }
            appendBundledLine(builder, lineOrigins, "}", null, -1);
            if (asyncInit) {
                appendBundledLine(builder, lineOrigins, "await " + initFunctionName + "();", null, -1);
            } else {
                appendBundledLine(builder, lineOrigins, initFunctionName + "();", null, -1);
            }
            appendBundledLine(builder, lineOrigins, "", null, -1);
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
            return;
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

        final List<ModuleImport> imports = new ArrayList<>();
        final List<String> bodyLines = new ArrayList<>();
        final List<Integer> bodyLineNumbers = new ArrayList<>();
        final Map<String, String> exportBindings = new LinkedHashMap<>();
        boolean requiresAsyncInit = false;
        final String[] lines = sourceText.replace("\r\n", "\n").split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            final String line = lines[index];
            final String trimmedLine = line.trim();
            if (trimmedLine.startsWith("import type ")) {
                final ImportStatement importStatement = collectImportStatement(lines, index);
                index = importStatement.endLineIndex();
                continue;
            }
            if (trimmedLine.startsWith("export type ")) {
                final ImportStatement exportTypeStatement = collectImportStatement(lines, index);
                index = exportTypeStatement.endLineIndex();
                continue;
            }
            final String importCandidate;
            final int importStartLine;
            final int importEndIndex;
            if (trimmedLine.startsWith("import ")) {
                importStartLine = index + 1;
                final ImportStatement importStatement = collectImportStatement(lines, index);
                importCandidate = importStatement.canonicalStatement();
                importEndIndex = importStatement.endLineIndex();
            } else {
                importCandidate = line;
                importStartLine = index + 1;
                importEndIndex = index;
            }

            final Matcher namedImportMatcher = NAMED_IMPORT_PATTERN.matcher(importCandidate);
            final Matcher defaultImportMatcher = DEFAULT_IMPORT_PATTERN.matcher(importCandidate);
            final Matcher namespaceImportMatcher = NAMESPACE_IMPORT_PATTERN.matcher(importCandidate);
            final Matcher sideEffectImportMatcher = SIDE_EFFECT_IMPORT_PATTERN.matcher(importCandidate);
            if (namedImportMatcher.matches()) {
                final String importPath = namedImportMatcher.group(2);
                if (isInteropImportPath(importPath)) {
                    imports.add(
                            ModuleImport.interop(
                                    parseInteropClassName(importPath, normalizedModule, importStartLine),
                                    parseNamedImportBindings(
                                            namedImportMatcher.group(1),
                                            normalizedModule,
                                            importStartLine,
                                            FEATURE_INTEROP_BINDING,
                                            GUIDANCE_INTEROP_BINDING
                                    ),
                                    importStartLine
                            )
                    );
                    index = importEndIndex;
                    continue;
                }
                final List<ImportBinding> parsedBindings =
                        parseNamedImportBindings(namedImportMatcher.group(1), normalizedModule, importStartLine);
                if (parsedBindings.isEmpty()) {
                    index = importEndIndex;
                    continue;
                }
                final Path dependency = resolveImport(normalizedModule, importPath, importStartLine);
                collectModule(dependency, orderedModules, visiting);
                imports.add(
                        ModuleImport.named(
                                dependency,
                                parsedBindings,
                                importStartLine
                        )
                );
                index = importEndIndex;
                continue;
            }
            if (defaultImportMatcher.matches()) {
                final String defaultBindingName = defaultImportMatcher.group(1);
                final String namedBindings = defaultImportMatcher.group(2);
                final String importPath = defaultImportMatcher.group(3);
                if (isInteropImportPath(importPath)) {
                    throw unsupportedModuleFeature(
                            normalizedModule,
                            importStartLine,
                            FEATURE_INTEROP_SYNTAX,
                            "Java interop imports do not support default bindings in TSJ-26.",
                            GUIDANCE_INTEROP_SYNTAX
                    );
                }
                if (!importPath.startsWith(".")) {
                    bodyLines.add(importCandidate);
                    bodyLineNumbers.add(importStartLine);
                    index = importEndIndex;
                    continue;
                }
                final Path dependency = resolveImport(normalizedModule, importPath, importStartLine);
                collectModule(dependency, orderedModules, visiting);
                final List<ImportBinding> parsedBindings = new ArrayList<>();
                parsedBindings.add(new ImportBinding("default", defaultBindingName));
                if (namedBindings != null && !namedBindings.isBlank()) {
                    parsedBindings.addAll(parseNamedImportBindings(namedBindings, normalizedModule, importStartLine));
                }
                imports.add(
                        ModuleImport.named(
                                dependency,
                                List.copyOf(parsedBindings),
                                importStartLine
                        )
                );
                index = importEndIndex;
                continue;
            }
            if (namespaceImportMatcher.matches()) {
                final String localNamespaceName = namespaceImportMatcher.group(1);
                final String importPath = namespaceImportMatcher.group(2);
                if (isInteropImportPath(importPath)) {
                    throw unsupportedModuleFeature(
                            normalizedModule,
                            importStartLine,
                            FEATURE_INTEROP_SYNTAX,
                            "Java interop imports do not support namespace bindings in TSJ-26.",
                            GUIDANCE_INTEROP_SYNTAX
                    );
                }
                if (!importPath.startsWith(".")) {
                    bodyLines.add(importCandidate);
                    bodyLineNumbers.add(importStartLine);
                    index = importEndIndex;
                    continue;
                }
                final Path dependency = resolveImport(normalizedModule, importPath, importStartLine);
                collectModule(dependency, orderedModules, visiting);
                imports.add(
                        ModuleImport.namespace(
                                dependency,
                                localNamespaceName,
                                importStartLine
                        )
                );
                index = importEndIndex;
                continue;
            }
            if (sideEffectImportMatcher.matches()) {
                final String importPath = sideEffectImportMatcher.group(1);
                if (isInteropImportPath(importPath)) {
                    throw unsupportedModuleFeature(
                            normalizedModule,
                            importStartLine,
                            FEATURE_INTEROP_SYNTAX,
                            "Java interop imports must declare named bindings in TSJ-26.",
                            GUIDANCE_INTEROP_SYNTAX
                    );
                }
                final Path dependency = resolveImport(normalizedModule, importPath, importStartLine);
                collectModule(dependency, orderedModules, visiting);
                imports.add(ModuleImport.sideEffect(dependency, importStartLine));
                index = importEndIndex;
                continue;
            }
            if (trimmedLine.startsWith("import ")) {
                bodyLines.add(importCandidate);
                bodyLineNumbers.add(importStartLine);
                index = importEndIndex;
                continue;
            }
            final ExportRewrite exportRewrite = isLikelyTopLevelLine(line)
                    ? rewriteExportLine(line, index + 1)
                    : null;
            if (exportRewrite != null) {
                bodyLines.add(exportRewrite.rewrittenLine());
                bodyLineNumbers.add(index + 1);
                for (ExportBinding exportBinding : exportRewrite.exportedBindings()) {
                    exportBindings.put(exportBinding.exportName(), exportBinding.localName());
                }
                requiresAsyncInit = requiresAsyncInit || lineContainsAwaitKeyword(exportRewrite.rewrittenLine());
                continue;
            }
            bodyLines.add(line);
            bodyLineNumbers.add(index + 1);
            requiresAsyncInit = requiresAsyncInit || lineContainsAwaitKeyword(line);
        }

        orderedModules.put(
                normalizedModule,
                new ModuleSource(
                        normalizedModule,
                        List.copyOf(imports),
                        List.copyOf(bodyLines),
                        List.copyOf(bodyLineNumbers),
                        exportBindings.entrySet()
                                .stream()
                                .map(entry -> new ExportBinding(entry.getKey(), entry.getValue()))
                                .toList(),
                        requiresAsyncInit
                )
        );
        visiting.remove(normalizedModule);
    }

    private static List<ImportBinding> parseNamedImportBindings(
            final String rawBindings,
            final Path sourceFile,
            final int line
    ) {
        return parseNamedImportBindings(rawBindings, sourceFile, line, null, null);
    }

    private static List<ImportBinding> parseNamedImportBindings(
            final String rawBindings,
            final Path sourceFile,
            final int line,
            final String featureId,
            final String guidance
    ) {
        final List<ImportBinding> parsedBindings = new ArrayList<>();
        final String[] bindingSegments = rawBindings.split(",");
        for (String rawBinding : bindingSegments) {
            final String trimmed = rawBinding.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("type ")) {
                continue;
            }
            final String importedName;
            final String localName;
            if (trimmed.contains(" as ")) {
                final String[] parts = trimmed.split("\\s+as\\s+");
                if (parts.length != 2) {
                    throw invalidNamedBinding(sourceFile, line, trimmed, featureId, guidance);
                }
                importedName = parts[0].trim();
                localName = parts[1].trim();
            } else {
                importedName = trimmed;
                localName = trimmed;
            }
            if (!isValidTsIdentifier(importedName) || !isValidTsIdentifier(localName)) {
                throw invalidNamedBinding(sourceFile, line, trimmed, featureId, guidance);
            }
            parsedBindings.add(new ImportBinding(importedName, localName));
        }
        if (featureId != null && parsedBindings.isEmpty()) {
            throw unsupportedModuleFeature(
                    sourceFile,
                    line,
                    featureId,
                    "Java interop imports must declare at least one named binding in TSJ-26.",
                    guidance
            );
        }
        return List.copyOf(parsedBindings);
    }

    private static ExportRewrite rewriteExportLine(final String line, final int lineNumber) {
        final String trimmed = line.trim();
        if (!trimmed.startsWith("export ")) {
            return null;
        }
        final Matcher defaultNamedFunctionMatcher = Pattern.compile(
                "^export\\s+default\\s+function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
        ).matcher(trimmed);
        if (defaultNamedFunctionMatcher.find()) {
            final String localName = defaultNamedFunctionMatcher.group(1);
            return new ExportRewrite(
                    line.replaceFirst("^(\\s*)export\\s+default\\s+", "$1"),
                    List.of(new ExportBinding("default", localName))
            );
        }
        final Matcher defaultNamedClassMatcher = Pattern.compile(
                "^export\\s+default\\s+class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
        ).matcher(trimmed);
        if (defaultNamedClassMatcher.find()) {
            final String localName = defaultNamedClassMatcher.group(1);
            return new ExportRewrite(
                    line.replaceFirst("^(\\s*)export\\s+default\\s+", "$1"),
                    List.of(new ExportBinding("default", localName))
            );
        }
        final Matcher defaultAnonymousFunctionMatcher = Pattern.compile(
                "^export\\s+default\\s+function\\s*\\("
        ).matcher(trimmed);
        if (defaultAnonymousFunctionMatcher.find()) {
            final String localName = "__tsj_default_export_" + lineNumber;
            return new ExportRewrite(
                    line.replaceFirst(
                            "^(\\s*)export\\s+default\\s+function\\s*\\(",
                            "$1const " + localName + " = function("
                    ),
                    List.of(new ExportBinding("default", localName))
            );
        }
        final Matcher defaultAnonymousClassMatcher = Pattern.compile(
                "^export\\s+default\\s+class\\b"
        ).matcher(trimmed);
        if (defaultAnonymousClassMatcher.find()) {
            final String localName = "__tsj_default_export_" + lineNumber;
            return new ExportRewrite(
                    line.replaceFirst(
                            "^(\\s*)export\\s+default\\s+class\\b",
                            "$1const " + localName + " = class"
                    ),
                    List.of(new ExportBinding("default", localName))
            );
        }
        final Matcher defaultMatcher = Pattern.compile("^export\\s+default\\s+(.+?);?$").matcher(trimmed);
        if (defaultMatcher.find()) {
            final String expression = defaultMatcher.group(1).trim();
            if (!expression.endsWith("{")) {
                final String localName = "__tsj_default_export_" + lineNumber;
                return new ExportRewrite(
                        "const " + localName + " = " + expression + ";",
                        List.of(new ExportBinding("default", localName))
                );
            }
        }
        final List<ExportBinding> exportedBindings = parseExportedBindings(trimmed);
        if (exportedBindings.isEmpty()) {
            return new ExportRewrite(line, List.of());
        }
        final String rewrittenLine = line.replaceFirst("^(\\s*)export\\s+", "$1");
        return new ExportRewrite(rewrittenLine, exportedBindings);
    }

    private static List<ExportBinding> parseExportedBindings(final String trimmedExportLine) {
        final Matcher asyncFunctionMatcher = Pattern.compile(
                "^export\\s+async\\s+function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
        ).matcher(trimmedExportLine);
        if (asyncFunctionMatcher.find()) {
            final String name = asyncFunctionMatcher.group(1);
            return List.of(new ExportBinding(name, name));
        }
        final Matcher functionMatcher = Pattern.compile(
                "^export\\s+function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
        ).matcher(trimmedExportLine);
        if (functionMatcher.find()) {
            final String name = functionMatcher.group(1);
            return List.of(new ExportBinding(name, name));
        }
        final Matcher classMatcher = Pattern.compile(
                "^export\\s+class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
        ).matcher(trimmedExportLine);
        if (classMatcher.find()) {
            final String name = classMatcher.group(1);
            return List.of(new ExportBinding(name, name));
        }
        final Matcher variableMatcher = Pattern.compile(
                "^export\\s+(?:const|let|var)\\s+(.+?);?$"
        ).matcher(trimmedExportLine);
        if (variableMatcher.find()) {
            final String declarations = variableMatcher.group(1).trim();
            final int equalsIndex = declarations.indexOf('=');
            String declarationHead = equalsIndex >= 0
                    ? declarations.substring(0, equalsIndex).trim()
                    : declarations;
            if (declarationHead.contains(",")) {
                return List.of();
            }
            final int typeIndex = declarationHead.indexOf(':');
            if (typeIndex >= 0) {
                declarationHead = declarationHead.substring(0, typeIndex).trim();
            }
            if (!isValidTsIdentifier(declarationHead)) {
                return List.of();
            }
            return List.of(new ExportBinding(declarationHead, declarationHead));
        }
        return List.of();
    }

    private static String exportGlobalSymbol(final int moduleIndex, final String exportName) {
        return "__tsj_export_" + moduleIndex + "_" + sanitizeGeneratedIdentifier(exportName);
    }

    private static String sanitizeGeneratedIdentifier(final String identifier) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < identifier.length(); index++) {
            final char ch = identifier.charAt(index);
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
        return builder.toString();
    }

    private static boolean isInteropImportPath(final String importPath) {
        return importPath != null && importPath.startsWith("java:");
    }

    private static String parseInteropClassName(
            final String importPath,
            final Path sourceFile,
            final int line
    ) {
        final Matcher matcher = INTEROP_MODULE_PATTERN.matcher(importPath);
        if (!matcher.matches()) {
            throw unsupportedModuleFeature(
                    sourceFile,
                    line,
                    FEATURE_INTEROP_MODULE_SPECIFIER,
                    "Invalid Java interop module specifier `" + importPath + "` in TSJ-26.",
                    GUIDANCE_INTEROP_MODULE_SPECIFIER
            );
        }
        return matcher.group(1);
    }

    private static JvmCompilationException invalidNamedBinding(
            final Path sourceFile,
            final int line,
            final String binding,
            final String featureId,
            final String guidance
    ) {
        if (featureId != null) {
            return unsupportedModuleFeature(
                    sourceFile,
                    line,
                    featureId,
                    "Invalid Java interop binding: " + binding,
                    guidance
            );
        }
        return new JvmCompilationException(
                "TSJ-BACKEND-PARSE",
                "Invalid named import binding: " + binding,
                line,
                1
        );
    }

    private static JvmCompilationException unsupportedModuleFeature(
            final Path sourceFile,
            final int line,
            final String featureId,
            final String summary,
            final String guidance
    ) {
        return new JvmCompilationException(
                "TSJ-BACKEND-UNSUPPORTED",
                summary + " [featureId=" + featureId + "]. Guidance: " + guidance,
                line,
                1,
                sourceFile.toString(),
                featureId,
                guidance
        );
    }

    private static String escapeJavaLiteral(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String buildNamespaceImportObjectLiteral(final Map<String, String> dependencyExports) {
        if (dependencyExports == null || dependencyExports.isEmpty()) {
            return "{}";
        }
        final StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : dependencyExports.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append("\"")
                    .append(escapeJavaLiteral(entry.getKey()))
                    .append("\": ")
                    .append(entry.getValue());
        }
        builder.append("}");
        return builder.toString();
    }

    private static void appendBundledLine(
            final StringBuilder builder,
            final List<SourceLineOrigin> lineOrigins,
            final String line,
            final Path sourceFile,
            final int sourceLine
    ) {
        builder.append(line).append("\n");
        lineOrigins.add(new SourceLineOrigin(sourceFile, sourceLine));
    }

    private static boolean isPassthroughBundle(final List<ModuleSource> modules) {
        if (modules.size() != 1) {
            return false;
        }
        final ModuleSource module = modules.get(0);
        return module.imports().isEmpty() && module.exportBindings().isEmpty();
    }

    private static boolean isValidTsIdentifier(final String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        final char first = value.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '$')) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '$')) {
                return false;
            }
        }
        return true;
    }

    private static boolean lineContainsAwaitKeyword(final String line) {
        return Pattern.compile("(^|\\W)await(\\W|$)").matcher(line).find();
    }

    private static boolean isLikelyTopLevelLine(final String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return !Character.isWhitespace(line.charAt(0));
    }

    private static ImportStatement collectImportStatement(final String[] lines, final int startIndex) {
        final StringBuilder builder = new StringBuilder();
        int endIndex = startIndex;
        while (true) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(lines[endIndex].trim());
            if (lines[endIndex].contains(";") || endIndex + 1 >= lines.length) {
                break;
            }
            endIndex++;
        }
        final String canonical = builder.toString().replaceAll("\\s+", " ").trim();
        return new ImportStatement(canonical, endIndex);
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
            final List<Path> compilationSources = new ArrayList<>();
            compilationSources.add(javaSourcePath);
            if (!isRuntimeAvailableOnClasspath()) {
                compilationSources.addAll(discoverRuntimeSourceFiles());
            }
            final Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(compilationSources);
            final String classPath = buildJavacClasspath();
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
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

    private static boolean isRuntimeAvailableOnClasspath() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null
                && contextClassLoader.getResource("dev/tsj/runtime/TsjRuntime.class") != null) {
            return true;
        }
        final ClassLoader compilerClassLoader = JvmBytecodeCompiler.class.getClassLoader();
        return compilerClassLoader != null
                && compilerClassLoader.getResource("dev/tsj/runtime/TsjRuntime.class") != null;
    }

    private static List<Path> discoverRuntimeSourceFiles() {
        final Path runtimeSourceRoot = discoverRuntimeSourceRoot();
        if (runtimeSourceRoot == null) {
            return List.of();
        }
        final List<Path> sourceFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(runtimeSourceRoot)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(sourceFiles::add);
        } catch (final IOException ignored) {
            return List.of();
        }
        return List.copyOf(sourceFiles);
    }

    private static Path discoverRuntimeSourceRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            final Path candidate = cursor.resolve("runtime").resolve("src").resolve("main").resolve("java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        final Path workspaceRuntimeClasses = discoverRuntimeClassesFromWorkspace();
        if (workspaceRuntimeClasses != null) {
            final Path targetDir = workspaceRuntimeClasses.getParent();
            final Path runtimeModuleDir = targetDir != null ? targetDir.getParent() : null;
            if (runtimeModuleDir != null) {
                final Path candidate = runtimeModuleDir.resolve("src").resolve("main").resolve("java");
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String buildJavacClasspath() {
        final LinkedHashSet<String> entries = new LinkedHashSet<>();
        final String systemClassPath = System.getProperty("java.class.path", "");
        if (!systemClassPath.isBlank()) {
            for (String entry : systemClassPath.split(Pattern.quote(File.pathSeparator))) {
                if (entry != null && !entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        addClasspathEntryIfExists(entries, discoverRuntimeClassesFromWorkspace());
        addClasspathEntryIfExists(entries, discoverRuntimeClassesFromWorkingDirectory());
        return String.join(File.pathSeparator, entries);
    }

    private static void addClasspathEntryIfExists(final Set<String> entries, final Path candidate) {
        if (candidate == null) {
            return;
        }
        final Path normalized = candidate.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            entries.add(normalized.toString());
        }
    }

    private static Path discoverRuntimeClassesFromWorkspace() {
        URL codeSourceUrl = null;
        try {
            codeSourceUrl = JvmBytecodeCompiler.class.getProtectionDomain().getCodeSource().getLocation();
        } catch (final RuntimeException ignored) {
            return null;
        }
        if (codeSourceUrl == null) {
            return null;
        }
        final URI codeSourceUri;
        try {
            codeSourceUri = codeSourceUrl.toURI();
        } catch (final URISyntaxException ignored) {
            return null;
        }
        Path cursor = Path.of(codeSourceUri).toAbsolutePath().normalize();
        if (!Files.isDirectory(cursor)) {
            cursor = cursor.getParent();
        }
        while (cursor != null) {
            final Path candidate = cursor.resolve("runtime").resolve("target").resolve("classes");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private static Path discoverRuntimeClassesFromWorkingDirectory() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            final Path candidate = cursor.resolve("runtime").resolve("target").resolve("classes");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
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

    private static ParseResult parseProgram(
            final String sourceText,
            final Path sourceFile,
            final BundleResult bundleResult
    ) {
        if (Boolean.parseBoolean(System.getProperty(LEGACY_TOKENIZER_PROPERTY, "false"))) {
            return parseProgramFromTokens(tokenize(sourceText), bundleResult);
        }

        final TypeScriptSyntaxBridge.BridgeResult bridgeResult =
                new TypeScriptSyntaxBridge().tokenize(sourceText, sourceFile);
        validateBridgeAstPayload(bridgeResult);
        throwIfBridgeDiagnosticsPresent(bridgeResult, bundleResult);

        final AstLoweringResult astLoweringResult = lowerProgramFromNormalizedAst(
                bridgeResult.normalizedProgram(),
                bundleResult
        );
        if (astLoweringResult != null) {
            return new ParseResult(astLoweringResult.program(), astLoweringResult.statementLocations());
        }

        if (Boolean.parseBoolean(System.getProperty(AST_NO_FALLBACK_PROPERTY, "false"))) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-LOWERING",
                    "Normalized AST lowering is unavailable for this source and parser fallback is disabled."
            );
        }

        return parseProgramFromTokens(bridgeTokensToBackendTokens(bridgeResult), bundleResult);
    }

    private static ParseResult parseProgramFromTokens(
            final List<Token> tokens,
            final BundleResult bundleResult
    ) {
        final Parser parser = new Parser(tokens, bundleResult);
        return new ParseResult(parser.parseProgram(), parser.statementLocations());
    }

    private static void throwIfBridgeDiagnosticsPresent(
            final TypeScriptSyntaxBridge.BridgeResult bridgeResult,
            final BundleResult bundleResult
    ) {
        if (bridgeResult.diagnostics().isEmpty()) {
            return;
        }
        final TypeScriptSyntaxBridge.BridgeDiagnostic diagnostic = bridgeResult.diagnostics().getFirst();
        final String code = diagnostic.code() == null || diagnostic.code().isBlank()
                ? "TSJ-BACKEND-PARSE"
                : diagnostic.code();
        final SourceLocation mappedLocation = diagnostic.line() != null && diagnostic.column() != null
                ? bundleResult.sourceLocationFor(diagnostic.line(), diagnostic.column())
                : null;
        final Integer line = mappedLocation != null ? mappedLocation.line() : diagnostic.line();
        final Integer column = mappedLocation != null ? mappedLocation.column() : diagnostic.column();
        final String diagnosticSource = mappedLocation != null
                ? mappedLocation.sourceFile().toString()
                : null;
        throw new JvmCompilationException(
                code,
                diagnostic.message(),
                line,
                column,
                diagnosticSource,
                null,
                null
        );
    }

    private static AstLoweringResult lowerProgramFromNormalizedAst(
            final JsonNode normalizedProgram,
            final BundleResult bundleResult
    ) {
        if (normalizedProgram == null || normalizedProgram.isNull()) {
            return null;
        }
        try {
            final String kind = requiredText(normalizedProgram, "kind");
            if (!"Program".equals(kind)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-AST-SCHEMA",
                        "normalizedProgram root kind must be `Program`, found `" + kind + "`."
                );
            }
            final JsonNode statementsNode = requiredArray(normalizedProgram, "statements");
            final List<Statement> statements = new ArrayList<>();
            final IdentityHashMap<Statement, SourceLocation> statementLocations = new IdentityHashMap<>();
            for (JsonNode statementNode : statementsNode) {
                statements.add(lowerStatementFromAst(statementNode, bundleResult, statementLocations));
            }
            return new AstLoweringResult(
                    new Program(List.copyOf(statements)),
                    new IdentityHashMap<>(statementLocations)
            );
        } catch (AstLoweringUnsupportedException unsupportedException) {
            return null;
        }
    }

    private static Statement lowerStatementFromAst(
            final JsonNode statementNode,
            final BundleResult bundleResult,
            final IdentityHashMap<Statement, SourceLocation> statementLocations
    ) {
        final String kind = requiredText(statementNode, "kind");
        return switch (kind) {
            case "VariableDeclaration" -> locateStatementFromAst(
                    new VariableDeclaration(
                            requiredText(statementNode, "name"),
                            lowerExpressionFromAst(requiredNode(statementNode, "expression"), bundleResult)
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "AssignmentStatement" -> locateStatementFromAst(
                    new AssignmentStatement(
                            lowerExpressionFromAst(requiredNode(statementNode, "target"), bundleResult),
                            lowerExpressionFromAst(requiredNode(statementNode, "expression"), bundleResult)
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "FunctionDeclarationStatement" -> locateStatementFromAst(
                    new FunctionDeclarationStatement(
                            lowerFunctionDeclarationFromAst(
                                    requiredNode(statementNode, "declaration"),
                                    bundleResult,
                                    statementLocations
                            )
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "ClassDeclarationStatement" -> locateStatementFromAst(
                    new ClassDeclarationStatement(
                            lowerClassDeclarationFromAst(
                                    requiredNode(statementNode, "declaration"),
                                    bundleResult,
                                    statementLocations
                            )
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "LabeledStatement" -> locateStatementFromAst(
                    new LabeledStatement(
                            requiredText(statementNode, "label"),
                            lowerStatementFromAst(
                                    requiredNode(statementNode, "statement"),
                                    bundleResult,
                                    statementLocations
                            )
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "IfStatement" -> locateStatementFromAst(
                    new IfStatement(
                            lowerExpressionFromAst(requiredNode(statementNode, "condition"), bundleResult),
                            lowerStatementList(requiredArray(statementNode, "thenBlock"), bundleResult, statementLocations),
                            lowerStatementList(requiredArray(statementNode, "elseBlock"), bundleResult, statementLocations)
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "WhileStatement" -> locateStatementFromAst(
                    new WhileStatement(
                            lowerExpressionFromAst(requiredNode(statementNode, "condition"), bundleResult),
                            lowerStatementList(requiredArray(statementNode, "body"), bundleResult, statementLocations)
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "TryStatement" -> locateStatementFromAst(
                    new TryStatement(
                            lowerStatementList(requiredArray(statementNode, "tryBlock"), bundleResult, statementLocations),
                            nullableText(statementNode, "catchBinding"),
                            lowerStatementList(requiredArray(statementNode, "catchBlock"), bundleResult, statementLocations),
                            lowerStatementList(requiredArray(statementNode, "finallyBlock"), bundleResult, statementLocations)
                    ),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "BreakStatement" -> locateStatementFromAst(
                    new BreakStatement(nullableText(statementNode, "label")),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "ContinueStatement" -> locateStatementFromAst(
                    new ContinueStatement(nullableText(statementNode, "label")),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "SuperCallStatement" -> locateStatementFromAst(
                    new SuperCallStatement(lowerExpressionList(requiredArray(statementNode, "arguments"), bundleResult)),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "ReturnStatement" -> locateStatementFromAst(
                    new ReturnStatement(lowerExpressionFromAst(requiredNode(statementNode, "expression"), bundleResult)),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "ThrowStatement" -> locateStatementFromAst(
                    new ThrowStatement(lowerExpressionFromAst(requiredNode(statementNode, "expression"), bundleResult)),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "ConsoleLogStatement" -> locateStatementFromAst(
                    new ConsoleLogStatement(lowerExpressionFromAst(requiredNode(statementNode, "expression"), bundleResult)),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            case "ExpressionStatement" -> locateStatementFromAst(
                    new ExpressionStatement(lowerExpressionFromAst(requiredNode(statementNode, "expression"), bundleResult)),
                    statementNode,
                    bundleResult,
                    statementLocations
            );
            default -> throw new AstLoweringUnsupportedException(
                    "Unsupported normalized statement kind: " + kind
            );
        };
    }

    private static FunctionDeclaration lowerFunctionDeclarationFromAst(
            final JsonNode declarationNode,
            final BundleResult bundleResult,
            final IdentityHashMap<Statement, SourceLocation> statementLocations
    ) {
        final List<String> parameters = new ArrayList<>();
        for (JsonNode parameterNode : requiredArray(declarationNode, "parameters")) {
            if (!parameterNode.isTextual()) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-AST-SCHEMA",
                        "Function parameter entries in normalizedProgram must be strings."
                );
            }
            parameters.add(parameterNode.asText());
        }
        final List<Statement> body = lowerStatementList(
                requiredArray(declarationNode, "body"),
                bundleResult,
                statementLocations
        );
        return new FunctionDeclaration(
                requiredText(declarationNode, "name"),
                List.copyOf(parameters),
                List.copyOf(body),
                declarationNode.path("async").asBoolean(false),
                declarationNode.path("generator").asBoolean(false)
        );
    }

    private static ClassDeclaration lowerClassDeclarationFromAst(
            final JsonNode declarationNode,
            final BundleResult bundleResult,
            final IdentityHashMap<Statement, SourceLocation> statementLocations
    ) {
        final List<String> fieldNames = lowerStringList(requiredArray(declarationNode, "fieldNames"));
        final String superClassName = nullableText(declarationNode, "superClassName");
        final ClassMethod constructorMethod;
        final JsonNode constructorMethodNode = declarationNode.get("constructorMethod");
        if (constructorMethodNode == null || constructorMethodNode.isNull()) {
            constructorMethod = null;
        } else {
            constructorMethod = lowerClassMethodFromAst(constructorMethodNode, bundleResult, statementLocations);
        }

        final List<ClassMethod> methods = new ArrayList<>();
        for (JsonNode methodNode : requiredArray(declarationNode, "methods")) {
            methods.add(lowerClassMethodFromAst(methodNode, bundleResult, statementLocations));
        }
        return new ClassDeclaration(
                requiredText(declarationNode, "name"),
                superClassName,
                fieldNames,
                constructorMethod,
                List.copyOf(methods)
        );
    }

    private static ClassMethod lowerClassMethodFromAst(
            final JsonNode methodNode,
            final BundleResult bundleResult,
            final IdentityHashMap<Statement, SourceLocation> statementLocations
    ) {
        final List<String> parameters = lowerStringList(requiredArray(methodNode, "parameters"));
        final List<Statement> body = lowerStatementList(
                requiredArray(methodNode, "body"),
                bundleResult,
                statementLocations
        );
        return new ClassMethod(
                requiredText(methodNode, "name"),
                parameters,
                body,
                methodNode.path("async").asBoolean(false)
        );
    }

    private static List<Statement> lowerStatementList(
            final JsonNode arrayNode,
            final BundleResult bundleResult,
            final IdentityHashMap<Statement, SourceLocation> statementLocations
    ) {
        Objects.requireNonNull(bundleResult, "bundleResult");
        Objects.requireNonNull(statementLocations, "statementLocations");
        final List<Statement> statements = new ArrayList<>();
        for (JsonNode statementNode : arrayNode) {
            statements.add(lowerStatementFromAst(statementNode, bundleResult, statementLocations));
        }
        return List.copyOf(statements);
    }

    private static List<Statement> lowerStatementListWithoutLocations(final JsonNode arrayNode) {
        final List<Statement> statements = new ArrayList<>();
        final IdentityHashMap<Statement, SourceLocation> ignoredLocations = new IdentityHashMap<>();
        final BundleResult syntheticBundle = new BundleResult("", List.of());
        for (JsonNode statementNode : arrayNode) {
            statements.add(lowerStatementFromAst(statementNode, syntheticBundle, ignoredLocations));
        }
        return List.copyOf(statements);
    }

    private static Expression lowerExpressionFromAst(
            final JsonNode expressionNode,
            final BundleResult bundleResult
    ) {
        final String kind = requiredText(expressionNode, "kind");
        return switch (kind) {
            case "NumberLiteral" -> new NumberLiteral(
                    normalizeNumericLiteralText(
                            requiredText(expressionNode, "text"),
                            expressionNode.path("line").asInt(-1) > 0 ? expressionNode.path("line").asInt() : null,
                            expressionNode.path("column").asInt(-1) > 0 ? expressionNode.path("column").asInt() : null,
                            null
                    )
            );
            case "StringLiteral" -> new StringLiteral(requiredText(expressionNode, "text"));
            case "BooleanLiteral" -> new BooleanLiteral(requiredBoolean(expressionNode, "value"));
            case "NullLiteral" -> new NullLiteral();
            case "UndefinedLiteral" -> new UndefinedLiteral();
            case "VariableExpression" -> new VariableExpression(requiredText(expressionNode, "name"));
            case "ThisExpression" -> new ThisExpression();
            case "UnaryExpression" -> new UnaryExpression(
                    requiredText(expressionNode, "operator"),
                    lowerExpressionFromAst(requiredNode(expressionNode, "expression"), bundleResult)
            );
            case "YieldExpression" -> new YieldExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "expression"), bundleResult),
                    expressionNode.path("delegate").asBoolean(false)
            );
            case "AwaitExpression" -> new AwaitExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "expression"), bundleResult)
            );
            case "BinaryExpression" -> new BinaryExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "left"), bundleResult),
                    requiredText(expressionNode, "operator"),
                    lowerExpressionFromAst(requiredNode(expressionNode, "right"), bundleResult)
            );
            case "AssignmentExpression" -> new AssignmentExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "target"), bundleResult),
                    requiredText(expressionNode, "operator"),
                    lowerExpressionFromAst(requiredNode(expressionNode, "expression"), bundleResult)
            );
            case "ConditionalExpression" -> new ConditionalExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "condition"), bundleResult),
                    lowerExpressionFromAst(requiredNode(expressionNode, "whenTrue"), bundleResult),
                    lowerExpressionFromAst(requiredNode(expressionNode, "whenFalse"), bundleResult)
            );
            case "CallExpression" -> {
                final Expression callee = lowerExpressionFromAst(requiredNode(expressionNode, "callee"), bundleResult);
                if (callee instanceof VariableExpression variableExpression && "eval".equals(variableExpression.name())) {
                    throw unsupportedFeatureFromAst(
                            expressionNode,
                            bundleResult,
                            FEATURE_EVAL,
                            "`eval(...)` is unsupported in TSJ MVP.",
                            GUIDANCE_EVAL
                    );
                }
                if (callee instanceof VariableExpression variableExpression
                        && "Function".equals(variableExpression.name())) {
                    throw unsupportedFeatureFromAst(
                            expressionNode,
                            bundleResult,
                            FEATURE_FUNCTION_CONSTRUCTOR,
                            "Function constructor is unsupported in TSJ MVP.",
                            GUIDANCE_FUNCTION_CONSTRUCTOR
                    );
                }
                yield new CallExpression(callee, lowerExpressionList(requiredArray(expressionNode, "arguments"), bundleResult));
            }
            case "OptionalCallExpression" -> new OptionalCallExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "callee"), bundleResult),
                    lowerExpressionList(requiredArray(expressionNode, "arguments"), bundleResult)
            );
            case "MemberAccessExpression" -> new MemberAccessExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "receiver"), bundleResult),
                    requiredText(expressionNode, "member")
            );
            case "OptionalMemberAccessExpression" -> new OptionalMemberAccessExpression(
                    lowerExpressionFromAst(requiredNode(expressionNode, "receiver"), bundleResult),
                    requiredText(expressionNode, "member")
            );
            case "NewExpression" -> {
                final Expression constructor = lowerExpressionFromAst(requiredNode(expressionNode, "constructor"), bundleResult);
                final String constructorName = resolveGlobalConstructorNameFromAstExpression(constructor);
                if ("Function".equals(constructorName)) {
                    throw unsupportedFeatureFromAst(
                            expressionNode,
                            bundleResult,
                            FEATURE_FUNCTION_CONSTRUCTOR,
                            "Function constructor is unsupported in TSJ MVP.",
                            GUIDANCE_FUNCTION_CONSTRUCTOR
                    );
                }
                yield new NewExpression(constructor, lowerExpressionList(requiredArray(expressionNode, "arguments"), bundleResult));
            }
            case "ArrayLiteralExpression" -> new ArrayLiteralExpression(
                    lowerExpressionList(requiredArray(expressionNode, "elements"), bundleResult)
            );
            case "ObjectLiteralExpression" -> new ObjectLiteralExpression(
                    lowerObjectLiteralEntries(requiredArray(expressionNode, "entries"), bundleResult)
            );
            case "FunctionExpression" -> new FunctionExpression(
                    lowerStringList(requiredArray(expressionNode, "parameters")),
                    lowerStatementListWithoutLocations(requiredArray(expressionNode, "body")),
                    expressionNode.path("async").asBoolean(false),
                    expressionNode.path("generator").asBoolean(false),
                    parseFunctionThisMode(requiredText(expressionNode, "thisMode"))
            );
            default -> throw new AstLoweringUnsupportedException(
                    "Unsupported normalized expression kind: " + kind
            );
        };
    }

    private static List<Expression> lowerExpressionList(
            final JsonNode arrayNode,
            final BundleResult bundleResult
    ) {
        final List<Expression> expressions = new ArrayList<>();
        for (JsonNode expressionNode : arrayNode) {
            expressions.add(lowerExpressionFromAst(expressionNode, bundleResult));
        }
        return List.copyOf(expressions);
    }

    private static List<ObjectLiteralEntry> lowerObjectLiteralEntries(
            final JsonNode entriesNode,
            final BundleResult bundleResult
    ) {
        final List<ObjectLiteralEntry> entries = new ArrayList<>();
        for (JsonNode entryNode : entriesNode) {
            entries.add(new ObjectLiteralEntry(
                    requiredText(entryNode, "key"),
                    lowerExpressionFromAst(requiredNode(entryNode, "value"), bundleResult)
            ));
        }
        return List.copyOf(entries);
    }

    private static String resolveGlobalConstructorNameFromAstExpression(final Expression expression) {
        if (expression instanceof VariableExpression variable) {
            return variable.name();
        }
        if (expression instanceof MemberAccessExpression member) {
            if (member.receiver() instanceof VariableExpression variable && "globalThis".equals(variable.name())) {
                return member.member();
            }
        }
        return null;
    }

    private static JvmCompilationException unsupportedFeatureFromAst(
            final JsonNode node,
            final BundleResult bundleResult,
            final String featureId,
            final String summary,
            final String guidance
    ) {
        final int bundledLine = node.path("line").asInt(-1);
        final int bundledColumn = node.path("column").asInt(-1);
        final SourceLocation sourceLocation = bundledLine > 0 && bundledColumn > 0
                ? bundleResult.sourceLocationFor(bundledLine, bundledColumn)
                : null;
        final Integer line = sourceLocation != null ? sourceLocation.line() : bundledLine > 0 ? bundledLine : null;
        final Integer column = sourceLocation != null
                ? sourceLocation.column()
                : bundledColumn > 0 ? bundledColumn : null;
        final String sourceFile = sourceLocation != null ? sourceLocation.sourceFile().toString() : null;
        return new JvmCompilationException(
                "TSJ-BACKEND-UNSUPPORTED",
                summary + " [featureId=" + featureId + "]. Guidance: " + guidance,
                line,
                column,
                sourceFile,
                featureId,
                guidance
        );
    }

    private static List<String> lowerStringList(final JsonNode arrayNode) {
        final List<String> values = new ArrayList<>();
        for (JsonNode valueNode : arrayNode) {
            if (!valueNode.isTextual()) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-AST-SCHEMA",
                        "normalizedProgram string arrays must contain string values."
                );
            }
            values.add(valueNode.asText());
        }
        return List.copyOf(values);
    }

    private static FunctionThisMode parseFunctionThisMode(final String mode) {
        try {
            return FunctionThisMode.valueOf(mode);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "Unsupported function thisMode in normalizedProgram: `" + mode + "`.",
                    null,
                    null,
                    illegalArgumentException
            );
        }
    }

    private static Statement locateStatementFromAst(
            final Statement statement,
            final JsonNode statementNode,
            final BundleResult bundleResult,
            final IdentityHashMap<Statement, SourceLocation> statementLocations
    ) {
        final int line = statementNode.path("line").asInt(-1);
        final int column = statementNode.path("column").asInt(-1);
        if (line > 0 && column > 0 && bundleResult != null) {
            final SourceLocation sourceLocation = bundleResult.sourceLocationFor(line, column);
            if (sourceLocation != null) {
                statementLocations.put(statement, sourceLocation);
            }
        }
        return statement;
    }

    private static JsonNode requiredNode(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "normalizedProgram payload missing required `" + field + "` field."
            );
        }
        return value;
    }

    private static JsonNode requiredArray(final JsonNode node, final String field) {
        final JsonNode value = requiredNode(node, field);
        if (!value.isArray()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "normalizedProgram `" + field + "` field must be an array."
            );
        }
        return value;
    }

    private static String requiredText(final JsonNode node, final String field) {
        final JsonNode value = requiredNode(node, field);
        if (!value.isTextual()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "normalizedProgram `" + field + "` field must be a string."
            );
        }
        return value.asText();
    }

    private static boolean requiredBoolean(final JsonNode node, final String field) {
        final JsonNode value = requiredNode(node, field);
        if (!value.isBoolean()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "normalizedProgram `" + field + "` field must be a boolean."
            );
        }
        return value.asBoolean();
    }

    private static String nullableText(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "normalizedProgram `" + field + "` field must be null or string."
            );
        }
        return value.asText();
    }

    private static List<Token> bridgeTokensToBackendTokens(final TypeScriptSyntaxBridge.BridgeResult bridgeResult) {
        final List<Token> tokens = new ArrayList<>(bridgeResult.tokens().size() + 1);
        for (TypeScriptSyntaxBridge.BridgeToken bridgeToken : bridgeResult.tokens()) {
            tokens.add(toBackendToken(bridgeToken));
        }
        if (tokens.isEmpty() || tokens.getLast().type() != TokenType.EOF) {
            final int eofLine;
            final int eofColumn;
            if (tokens.isEmpty()) {
                eofLine = 1;
                eofColumn = 1;
            } else {
                final Token lastToken = tokens.getLast();
                eofLine = lastToken.line();
                eofColumn = Math.max(1, lastToken.column() + Math.max(1, lastToken.text().length()));
            }
            tokens.add(new Token(TokenType.EOF, "", eofLine, eofColumn));
        }
        return List.copyOf(tokens);
    }

    private static void validateBridgeAstPayload(final TypeScriptSyntaxBridge.BridgeResult bridgeResult) {
        if (bridgeResult.astNodes().isEmpty()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "TypeScript syntax bridge payload must include at least one AST node."
            );
        }
        final TypeScriptSyntaxBridge.BridgeAstNode firstNode = bridgeResult.astNodes().getFirst();
        if (!"SourceFile".equals(firstNode.kind())) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "TypeScript syntax bridge AST must start with SourceFile, found `" + firstNode.kind() + "`."
            );
        }
    }

    private static Token toBackendToken(final TypeScriptSyntaxBridge.BridgeToken bridgeToken) {
        final TokenType type;
        try {
            type = TokenType.valueOf(bridgeToken.type());
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "Unsupported token type from TypeScript bridge: `" + bridgeToken.type() + "`.",
                    bridgeToken.line(),
                    bridgeToken.column(),
                    illegalArgumentException
            );
        }
        return new Token(
                type,
                bridgeToken.text(),
                Math.max(1, bridgeToken.line()),
                Math.max(1, bridgeToken.column())
        );
    }

    private static String normalizeNumericLiteralText(
            final String rawLiteral,
            final Integer line,
            final Integer column,
            final String sourceFile
    ) {
        if (rawLiteral == null || rawLiteral.isBlank()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-PARSE",
                    "Invalid numeric literal.",
                    line,
                    column,
                    sourceFile,
                    null,
                    null
            );
        }
        String value = rawLiteral.replace("_", "");
        final boolean bigIntLiteral = value.endsWith("n") || value.endsWith("N");
        if (bigIntLiteral) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-PARSE",
                    "Invalid numeric literal `" + rawLiteral + "`.",
                    line,
                    column,
                    sourceFile,
                    null,
                    null
            );
        }

        try {
            if (bigIntLiteral && (value.contains(".") || value.contains("e") || value.contains("E"))) {
                throw new NumberFormatException("BigInt literals cannot include decimals or exponents.");
            }
            if (value.startsWith("0x") || value.startsWith("0X")) {
                final String normalized = new BigInteger(value.substring(2), 16).toString();
                return bigIntLiteral ? normalized + "n" : normalized;
            }
            if (value.startsWith("0b") || value.startsWith("0B")) {
                final String normalized = new BigInteger(value.substring(2), 2).toString();
                return bigIntLiteral ? normalized + "n" : normalized;
            }
            if (value.startsWith("0o") || value.startsWith("0O")) {
                final String normalized = new BigInteger(value.substring(2), 8).toString();
                return bigIntLiteral ? normalized + "n" : normalized;
            }
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                return Double.toString(Double.parseDouble(value));
            }
            final String normalized = new BigInteger(value, 10).toString();
            return bigIntLiteral ? normalized + "n" : normalized;
        } catch (final NumberFormatException | ArithmeticException ex) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-PARSE",
                    "Invalid numeric literal `" + rawLiteral + "`.",
                    line,
                    column,
                    sourceFile,
                    null,
                    null,
                    ex
            );
        }
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
            final String four = index + 4 <= source.length() ? source.substring(index, index + 4) : "";
            if (">>>=".equals(four)) {
                tokens.add(new Token(TokenType.SYMBOL, four, line, column));
                index += 4;
                column += 4;
                continue;
            }
            final String three = index + 3 <= source.length() ? source.substring(index, index + 3) : "";
            if ("===".equals(three)
                    || "!==".equals(three)
                    || ">>>".equals(three)
                    || "<<=".equals(three)
                    || ">>=".equals(three)
                    || "**=".equals(three)
                    || "&&=".equals(three)
                    || "||=".equals(three)
                    || "??=".equals(three)) {
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
                    || "<<".equals(two)
                    || ">>".equals(two)
                    || "**".equals(two)
                    || "&&".equals(two)
                    || "||".equals(two)
                    || "??".equals(two)
                    || "?.".equals(two)
                    || "+=".equals(two)
                    || "-=".equals(two)
                    || "*=".equals(two)
                    || "&=".equals(two)
                    || "|=".equals(two)
                    || "^=".equals(two)
                    || "/=".equals(two)
                    || "%=".equals(two)
                    || "=>".equals(two)) {
                tokens.add(new Token(TokenType.SYMBOL, two, line, column));
                index += 2;
                column += 2;
                continue;
            }
            final String one = Character.toString(current);
            if ("(){}[];,.+-*/%<>!=:?&|^~".contains(one)) {
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
            LabeledStatement,
            IfStatement,
            WhileStatement,
            TryStatement,
            BreakStatement,
            ContinueStatement,
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

    private record LabeledStatement(String label, Statement statement) implements Statement {
    }

    private record IfStatement(Expression condition, List<Statement> thenBlock, List<Statement> elseBlock)
            implements Statement {
    }

    private record WhileStatement(Expression condition, List<Statement> body) implements Statement {
    }

    private record TryStatement(
            List<Statement> tryBlock,
            String catchBinding,
            List<Statement> catchBlock,
            List<Statement> finallyBlock
    ) implements Statement {
        private boolean hasCatch() {
            return !catchBlock.isEmpty();
        }

        private boolean hasFinally() {
            return !finallyBlock.isEmpty();
        }
    }

    private record BreakStatement(String label) implements Statement {
    }

    private record ContinueStatement(String label) implements Statement {
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

    private record FunctionDeclaration(
            String name,
            List<String> parameters,
            List<Statement> body,
            boolean async,
            boolean generator
    ) {
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
            YieldExpression,
            AwaitExpression,
            FunctionExpression,
            BinaryExpression,
            AssignmentExpression,
            ConditionalExpression,
            CallExpression,
            OptionalCallExpression,
            MemberAccessExpression,
            OptionalMemberAccessExpression,
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

    private record YieldExpression(Expression expression, boolean delegate) implements Expression {
    }

    private record AwaitExpression(Expression expression) implements Expression {
    }

    private enum FunctionThisMode {
        DYNAMIC,
        LEXICAL
    }

    private record FunctionExpression(
            List<String> parameters,
            List<Statement> body,
            boolean async,
            boolean generator,
            FunctionThisMode thisMode
    )
            implements Expression {
    }

    private record BinaryExpression(Expression left, String operator, Expression right) implements Expression {
    }

    private record AssignmentExpression(Expression target, String operator, Expression expression) implements Expression {
    }

    private record ConditionalExpression(Expression condition, Expression whenTrue, Expression whenFalse)
            implements Expression {
    }

    private record CallExpression(Expression callee, List<Expression> arguments) implements Expression {
    }

    private record OptionalCallExpression(Expression callee, List<Expression> arguments) implements Expression {
    }

    private record MemberAccessExpression(Expression receiver, String member) implements Expression {
    }

    private record OptionalMemberAccessExpression(Expression receiver, String member) implements Expression {
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

    private enum ModuleImportKind {
        NAMED,
        NAMESPACE,
        INTEROP,
        SIDE_EFFECT
    }

    private record ImportBinding(String importedName, String localName) {
    }

    private record ExportBinding(String exportName, String localName) {
    }

    private record ModuleImport(
            ModuleImportKind kind,
            Path dependency,
            String interopClassName,
            String namespaceLocalName,
            List<ImportBinding> bindings,
            int line
    ) {
        private static ModuleImport named(final Path dependency, final List<ImportBinding> bindings, final int line) {
            return new ModuleImport(
                    ModuleImportKind.NAMED,
                    dependency,
                    null,
                    null,
                    List.copyOf(bindings),
                    line
            );
        }

        private static ModuleImport namespace(
                final Path dependency,
                final String namespaceLocalName,
                final int line
        ) {
            return new ModuleImport(
                    ModuleImportKind.NAMESPACE,
                    dependency,
                    null,
                    namespaceLocalName,
                    List.of(),
                    line
            );
        }

        private static ModuleImport interop(
                final String interopClassName,
                final List<ImportBinding> bindings,
                final int line
        ) {
            return new ModuleImport(
                    ModuleImportKind.INTEROP,
                    null,
                    interopClassName,
                    null,
                    List.copyOf(bindings),
                    line
            );
        }

        private static ModuleImport sideEffect(final Path dependency, final int line) {
            return new ModuleImport(
                    ModuleImportKind.SIDE_EFFECT,
                    dependency,
                    null,
                    null,
                    List.of(),
                    line
            );
        }
    }

    private record ExportRewrite(String rewrittenLine, List<ExportBinding> exportedBindings) {
    }

    private record ModuleSource(
            Path sourceFile,
            List<ModuleImport> imports,
            List<String> bodyLines,
            List<Integer> bodyLineNumbers,
            List<ExportBinding> exportBindings,
            boolean requiresAsyncInit
    ) {
    }

    private record ImportStatement(String canonicalStatement, int endLineIndex) {
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

    private record ParseResult(
            Program program,
            Map<Statement, SourceLocation> statementLocations
    ) {
    }

    private record AstLoweringResult(
            Program program,
            Map<Statement, SourceLocation> statementLocations
    ) {
    }

    private static final class AstLoweringUnsupportedException extends RuntimeException {
        private AstLoweringUnsupportedException(final String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final BundleResult bundleResult;
        private final IdentityHashMap<Statement, SourceLocation> statementLocations;
        private int loopDepth;
        private int index;

        private Parser(final List<Token> tokens, final BundleResult bundleResult) {
            this.tokens = tokens;
            this.bundleResult = bundleResult;
            this.statementLocations = new IdentityHashMap<>();
            this.loopDepth = 0;
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
            if (matchKeyword("declare")) {
                if (insideFunction) {
                    final Token token = previous();
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Block-scoped `declare` is unsupported in TSJ-9 subset.",
                            token.line(),
                            token.column()
                    );
                }
                if (matchKeyword("const") || matchKeyword("let") || matchKeyword("var")) {
                    statement = parseDeclareVariableDeclaration();
                    return locate(statement, statementStart);
                }
                final Token token = current();
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported `declare` statement form in TSJ-9 subset.",
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
            if (matchKeyword("do")) {
                statement = parseDoWhileStatement(insideFunction, previous());
                return locate(statement, statementStart);
            }
            if (matchSoftKeyword("try")) {
                statement = parseTryStatement(insideFunction);
                return locate(statement, statementStart);
            }
            if (matchKeyword("break")) {
                statement = parseBreakStatement();
                return locate(statement, statementStart);
            }
            if (matchKeyword("continue")) {
                statement = parseContinueStatement();
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
            if (expression instanceof AssignmentExpression assignmentExpression
                    && "=".equals(assignmentExpression.operator())) {
                consumeSymbol(";", "Expected `;` after assignment.");
                return new AssignmentStatement(assignmentExpression.target(), assignmentExpression.expression());
            }
            consumeSymbol(";", "Expected `;` after expression statement.");
            return new ExpressionStatement(expression);
        }

        private FunctionDeclaration parseFunctionDeclaration(final boolean asyncFunction) {
            final boolean generatorFunction = matchSymbol("*");
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
            return new FunctionDeclaration(
                    nameToken.text(),
                    List.copyOf(parameters),
                    List.copyOf(body),
                    asyncFunction,
                    generatorFunction
            );
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
                if (asyncMethod) {
                    rejectUnsupportedAsyncMethodVariantIfPresent("class");
                }
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

        private VariableDeclaration parseDeclareVariableDeclaration() {
            final Token name = consumeIdentifier("Expected variable name after `declare` declaration keyword.");
            if (matchSymbol(":")) {
                skipDeclareVariableTypeAnnotation();
            }
            final Expression expression;
            if (matchSymbol("=")) {
                expression = parseExpression();
            } else {
                expression = new UndefinedLiteral();
            }
            consumeSymbol(";", "Expected `;` after `declare` variable declaration.");
            return new VariableDeclaration(name.text(), expression);
        }

        private void skipDeclareVariableTypeAnnotation() {
            int parenDepth = 0;
            int bracketDepth = 0;
            int braceDepth = 0;
            int angleDepth = 0;
            while (!isAtEnd()) {
                if (current().type() == TokenType.SYMBOL) {
                    final String symbol = current().text();
                    switch (symbol) {
                        case "<" -> {
                            angleDepth++;
                            advance();
                            continue;
                        }
                        case ">" -> {
                            if (angleDepth > 0) {
                                angleDepth--;
                            }
                            advance();
                            continue;
                        }
                        case "(" -> {
                            parenDepth++;
                            advance();
                            continue;
                        }
                        case ")" -> {
                            if (parenDepth > 0) {
                                parenDepth--;
                            }
                            advance();
                            continue;
                        }
                        case "[" -> {
                            bracketDepth++;
                            advance();
                            continue;
                        }
                        case "]" -> {
                            if (bracketDepth > 0) {
                                bracketDepth--;
                            }
                            advance();
                            continue;
                        }
                        case "{" -> {
                            braceDepth++;
                            advance();
                            continue;
                        }
                        case "}" -> {
                            if (braceDepth > 0) {
                                braceDepth--;
                            }
                            advance();
                            continue;
                        }
                        case ",", "=", ";" -> {
                            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                                return;
                            }
                            advance();
                            continue;
                        }
                        default -> {
                            // fall through
                        }
                    }
                }
                advance();
            }
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
            loopDepth++;
            final List<Statement> body;
            try {
                body = parseBlock(insideFunction);
            } finally {
                loopDepth--;
            }
            return new WhileStatement(condition, body);
        }

        private WhileStatement parseDoWhileStatement(final boolean insideFunction, final Token doToken) {
            loopDepth++;
            final List<Statement> body;
            try {
                body = parseBlock(insideFunction);
            } finally {
                loopDepth--;
            }

            if (!matchKeyword("while")) {
                final Token token = current();
                throw new JvmCompilationException(
                        "TSJ-BACKEND-PARSE",
                        "Expected `while` after `do` block.",
                        token.line(),
                        token.column()
                );
            }
            consumeSymbol("(", "Expected `(` after `while` in `do...while` statement.");
            final Expression condition = parseExpression();
            consumeSymbol(")", "Expected `)` after `do...while` condition.");
            consumeSymbol(";", "Expected `;` after `do...while` condition.");

            if (containsContinueTargetingCurrentLoop(body, 0)) {
                throw unsupportedFeature(
                        doToken,
                        "TSJ59-DO-WHILE-CONTINUE",
                        "`continue` targeting `do...while` is not yet supported in TSJ-59 subset.",
                        "Rewrite the loop using `while` until TSJ-59 do-while continue lowering is completed."
                );
            }

            final List<Statement> loweredBody = new ArrayList<>(body);
            loweredBody.add(
                    new IfStatement(
                            new UnaryExpression("!", condition),
                            List.of(new BreakStatement(null)),
                            List.of()
                    )
            );
            return new WhileStatement(new BooleanLiteral(true), List.copyOf(loweredBody));
        }

        private boolean containsContinueTargetingCurrentLoop(
                final List<Statement> statements,
                final int nestedLoopDepth
        ) {
            for (Statement statement : statements) {
                if (statement instanceof ContinueStatement continueStatement
                        && continueStatement.label() == null
                        && nestedLoopDepth == 0) {
                    return true;
                }
                if (statement instanceof WhileStatement whileStatement
                        && containsContinueTargetingCurrentLoop(whileStatement.body(), nestedLoopDepth + 1)) {
                    return true;
                }
                if (statement instanceof IfStatement ifStatement) {
                    if (containsContinueTargetingCurrentLoop(ifStatement.thenBlock(), nestedLoopDepth)) {
                        return true;
                    }
                    if (containsContinueTargetingCurrentLoop(ifStatement.elseBlock(), nestedLoopDepth)) {
                        return true;
                    }
                }
                if (statement instanceof TryStatement tryStatement) {
                    if (containsContinueTargetingCurrentLoop(tryStatement.tryBlock(), nestedLoopDepth)) {
                        return true;
                    }
                    if (containsContinueTargetingCurrentLoop(tryStatement.catchBlock(), nestedLoopDepth)) {
                        return true;
                    }
                    if (containsContinueTargetingCurrentLoop(tryStatement.finallyBlock(), nestedLoopDepth)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private TryStatement parseTryStatement(final boolean insideFunction) {
            final List<Statement> tryBlock = parseBlock(insideFunction);
            String catchBinding = null;
            List<Statement> catchBlock = List.of();
            List<Statement> finallyBlock = List.of();
            boolean hasCatchClause = false;
            boolean hasFinallyClause = false;

            if (matchSoftKeyword("catch")) {
                hasCatchClause = true;
                if (matchSymbol("(")) {
                    catchBinding = consumeIdentifier("Expected catch binding name.").text();
                    if (matchSymbol(":")) {
                        skipTypeAnnotation();
                    }
                    consumeSymbol(")", "Expected `)` after catch binding.");
                }
                catchBlock = parseBlock(insideFunction);
            }
            if (matchSoftKeyword("finally")) {
                hasFinallyClause = true;
                finallyBlock = parseBlock(insideFunction);
            }

            if (!hasCatchClause && !hasFinallyClause) {
                final Token token = current();
                throw new JvmCompilationException(
                        "TSJ-BACKEND-PARSE",
                        "Expected `catch` or `finally` after `try` block.",
                        token.line(),
                        token.column()
                );
            }

            if (hasCatchClause && catchBlock.isEmpty()) {
                catchBlock = List.of(new ExpressionStatement(new UndefinedLiteral()));
            }
            if (hasFinallyClause && finallyBlock.isEmpty()) {
                finallyBlock = List.of(new ExpressionStatement(new UndefinedLiteral()));
            }

            return new TryStatement(List.copyOf(tryBlock), catchBinding, List.copyOf(catchBlock), List.copyOf(finallyBlock));
        }

        private BreakStatement parseBreakStatement() {
            final Token breakToken = previous();
            final String label = matchType(TokenType.IDENTIFIER) ? previous().text() : null;
            if (loopDepth <= 0 && label == null) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "`break` is only supported inside loop bodies in TSJ-13 subset.",
                        breakToken.line(),
                        breakToken.column()
                );
            }
            consumeSymbol(";", "Expected `;` after `break`.");
            return new BreakStatement(label);
        }

        private ContinueStatement parseContinueStatement() {
            final Token continueToken = previous();
            final String label = matchType(TokenType.IDENTIFIER) ? previous().text() : null;
            if (loopDepth <= 0) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "`continue` is only supported inside loop bodies in TSJ-13 subset.",
                        continueToken.line(),
                        continueToken.column()
                );
            }
            consumeSymbol(";", "Expected `;` after `continue`.");
            return new ContinueStatement(label);
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
            return parseAssignment();
        }

        private Expression parseAssignment() {
            final Expression left = parseConditional();
            final Token assignmentOperator = matchAssignmentOperator();
            if (assignmentOperator == null) {
                return left;
            }
            if (!isAssignmentTarget(left)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Invalid assignment target in TSJ subset.",
                        assignmentOperator.line(),
                        assignmentOperator.column()
                );
            }
            final Expression right = parseAssignment();
            return new AssignmentExpression(left, assignmentOperator.text(), right);
        }

        private Expression parseConditional() {
            Expression expression = parseNullishCoalescing();
            if (matchSymbol("?")) {
                final Expression whenTrue = parseExpression();
                consumeSymbol(":", "Expected `:` in conditional expression.");
                final Expression whenFalse = parseConditional();
                expression = new ConditionalExpression(expression, whenTrue, whenFalse);
            }
            return expression;
        }

        private Expression parseNullishCoalescing() {
            Expression expression = parseLogicalOr();
            while (matchSymbol("??")) {
                final String operator = previous().text();
                final Expression right = parseLogicalOr();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseLogicalOr() {
            Expression expression = parseLogicalAnd();
            while (matchSymbol("||")) {
                final String operator = previous().text();
                final Expression right = parseLogicalAnd();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseLogicalAnd() {
            Expression expression = parseBitwiseOr();
            while (matchSymbol("&&")) {
                final String operator = previous().text();
                final Expression right = parseBitwiseOr();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseBitwiseOr() {
            Expression expression = parseBitwiseXor();
            while (matchSymbol("|")) {
                final String operator = previous().text();
                final Expression right = parseBitwiseXor();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseBitwiseXor() {
            Expression expression = parseBitwiseAnd();
            while (matchSymbol("^")) {
                final String operator = previous().text();
                final Expression right = parseBitwiseAnd();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseBitwiseAnd() {
            Expression expression = parseEquality();
            while (matchSymbol("&")) {
                final String operator = previous().text();
                final Expression right = parseEquality();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
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
            Expression expression = parseShift();
            while (matchSymbol("<")
                    || matchSymbol("<=")
                    || matchSymbol(">")
                    || matchSymbol(">=")
                    || matchKeyword("instanceof")
                    || matchKeyword("in")) {
                final String operator = previous().text();
                final Expression right = parseShift();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseShift() {
            Expression expression = parseTerm();
            while (true) {
                final String operator = matchShiftOperator();
                if (operator == null) {
                    break;
                }
                final Expression right = parseTerm();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private String matchShiftOperator() {
            if (matchSymbol("<<") || matchSymbol(">>") || matchSymbol(">>>")) {
                return previous().text();
            }
            if (checkSymbol(">")
                    && lookAhead(1).type() == TokenType.SYMBOL
                    && ">".equals(lookAhead(1).text())) {
                if (lookAhead(2).type() == TokenType.SYMBOL && ">".equals(lookAhead(2).text())) {
                    advance();
                    advance();
                    advance();
                    return ">>>";
                }
                advance();
                advance();
                return ">>";
            }
            return null;
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
            Expression expression = parseExponent();
            while (matchSymbol("*") || matchSymbol("/") || matchSymbol("%")) {
                final String operator = previous().text();
                final Expression right = parseExponent();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseExponent() {
            Expression expression = parseUnary();
            if (matchSymbol("**")) {
                final String operator = previous().text();
                final Expression right = parseExponent();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseUnary() {
            if (matchSymbol("+") || matchSymbol("-") || matchSymbol("!") || matchSymbol("~")) {
                final String operator = previous().text();
                return new UnaryExpression(operator, parseUnary());
            }
            if (matchKeyword("delete")) {
                return new UnaryExpression("delete", parseUnary());
            }
            if (matchKeyword("typeof")) {
                return new UnaryExpression("typeof", parseUnary());
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
            final String constructorName = resolveGlobalConstructorName(constructor);
            if ("Function".equals(constructorName)) {
                throw unsupportedFeature(
                        current(),
                        FEATURE_FUNCTION_CONSTRUCTOR,
                        "Function constructor is unsupported in TSJ MVP.",
                        GUIDANCE_FUNCTION_CONSTRUCTOR
                );
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
                if (matchSymbol("?.")) {
                    if (matchSymbol("(")) {
                        final List<Expression> arguments = new ArrayList<>();
                        if (!checkSymbol(")")) {
                            do {
                                arguments.add(parseExpression());
                            } while (matchSymbol(","));
                        }
                        consumeSymbol(")", "Expected `)` after optional call arguments.");
                        expression = new OptionalCallExpression(expression, List.copyOf(arguments));
                        continue;
                    }
                    final Token member = consumeIdentifier("Expected property name after `?.`.");
                    expression = new OptionalMemberAccessExpression(expression, member.text());
                    continue;
                }
                if (matchSymbol(".")) {
                    final Token member = consumeIdentifier("Expected property name after `.`.");
                    expression = new MemberAccessExpression(expression, member.text());
                    continue;
                }
                if (checkSymbol("(")) {
                    if (expression instanceof VariableExpression variable && "eval".equals(variable.name())) {
                        throw unsupportedFeature(
                                current(),
                                FEATURE_EVAL,
                                "`eval(...)` is unsupported in TSJ MVP.",
                                GUIDANCE_EVAL
                        );
                    }
                    if (expression instanceof VariableExpression variable
                            && "Function".equals(variable.name())) {
                        throw unsupportedFeature(
                                current(),
                                FEATURE_FUNCTION_CONSTRUCTOR,
                                "Function constructor is unsupported in TSJ MVP.",
                                GUIDANCE_FUNCTION_CONSTRUCTOR
                        );
                    }
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
            if (current().type() == TokenType.KEYWORD && "import".equals(current().text())) {
                final Token importToken = advance();
                if (checkSymbol("(")) {
                    throw unsupportedFeature(
                            importToken,
                            FEATURE_DYNAMIC_IMPORT,
                            "dynamic import() is unsupported in TSJ MVP.",
                            GUIDANCE_DYNAMIC_IMPORT
                    );
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported import form in TSJ-12 bootstrap: import",
                        importToken.line(),
                        importToken.column()
                );
            }
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
                final Token numberToken = previous();
                return new NumberLiteral(
                        normalizeNumericLiteralText(
                                numberToken.text(),
                                numberToken.line(),
                                numberToken.column(),
                                null
                        )
                );
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
                    if (isUnsupportedAsyncMethodVariant()) {
                        rejectUnsupportedAsyncMethodVariant("object literal");
                    }
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

        private boolean isUnsupportedAsyncMethodVariant() {
            if (current().type() != TokenType.KEYWORD || !"async".equals(current().text())) {
                return false;
            }
            if (lookAhead(1).type() == TokenType.SYMBOL && "*".equals(lookAhead(1).text())) {
                return true;
            }
            return lookAhead(1).type() == TokenType.IDENTIFIER
                    && ("get".equals(lookAhead(1).text()) || "set".equals(lookAhead(1).text()))
                    && lookAhead(2).type() == TokenType.IDENTIFIER
                    && lookAhead(3).type() == TokenType.SYMBOL
                    && "(".equals(lookAhead(3).text());
        }

        private void rejectUnsupportedAsyncMethodVariantIfPresent(final String location) {
            if (checkSymbol("*")) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Async generator methods are unsupported in TSJ-13b subset for " + location + " declarations.",
                        current().line(),
                        current().column()
                );
            }
            if (current().type() == TokenType.IDENTIFIER
                    && ("get".equals(current().text()) || "set".equals(current().text()))
                    && lookAhead(1).type() == TokenType.IDENTIFIER
                    && lookAhead(2).type() == TokenType.SYMBOL
                    && "(".equals(lookAhead(2).text())) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Async " + current().text() + " methods are unsupported in TSJ-13b subset for "
                                + location
                                + " declarations.",
                        current().line(),
                        current().column()
                );
            }
        }

        private void rejectUnsupportedAsyncMethodVariant(final String location) {
            if (lookAhead(1).type() == TokenType.SYMBOL && "*".equals(lookAhead(1).text())) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Async generator methods are unsupported in TSJ-13b subset for " + location + " declarations.",
                        lookAhead(1).line(),
                        lookAhead(1).column()
                );
            }
            final String accessorKind = lookAhead(1).text();
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Async " + accessorKind + " methods are unsupported in TSJ-13b subset for "
                            + location
                            + " declarations.",
                    lookAhead(1).line(),
                    lookAhead(1).column()
            );
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
            final boolean generatorFunction = matchSymbol("*");
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
            return new FunctionExpression(
                    List.copyOf(parameters),
                    List.copyOf(body),
                    asyncFunction,
                    generatorFunction,
                    FunctionThisMode.DYNAMIC
            );
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
            return new FunctionExpression(
                    List.copyOf(parameters),
                    List.copyOf(body),
                    asyncFunction,
                    false,
                    FunctionThisMode.LEXICAL
            );
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
            return new FunctionExpression(
                    List.copyOf(parameters),
                    List.copyOf(body),
                    asyncFunction,
                    false,
                    FunctionThisMode.DYNAMIC
            );
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
                    || "await".equals(keyword)
                    || "delete".equals(keyword)
                    || "typeof".equals(keyword);
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

        private JvmCompilationException unsupportedFeature(
                final Token token,
                final String featureId,
                final String summary,
                final String guidance
        ) {
            final SourceLocation sourceLocation = bundleResult.sourceLocationFor(token.line(), token.column());
            final Integer line = sourceLocation != null ? sourceLocation.line() : token.line();
            final Integer column = sourceLocation != null ? sourceLocation.column() : token.column();
            final String sourceFile = sourceLocation != null ? sourceLocation.sourceFile().toString() : null;
            return new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    summary + " [featureId=" + featureId + "]. Guidance: " + guidance,
                    line,
                    column,
                    sourceFile,
                    featureId,
                    guidance
            );
        }

        private String resolveGlobalConstructorName(final Expression expression) {
            if (expression instanceof VariableExpression variable) {
                return variable.name();
            }
            if (expression instanceof MemberAccessExpression member) {
                if (member.receiver() instanceof VariableExpression variable
                        && "globalThis".equals(variable.name())) {
                    return member.member();
                }
            }
            return null;
        }

        private boolean checkSymbol(final String symbol) {
            return current().type() == TokenType.SYMBOL && symbol.equals(current().text());
        }

        private Token matchAssignmentOperator() {
            if (matchSymbol("=")
                    || matchSymbol("+=")
                    || matchSymbol("-=")
                    || matchSymbol("*=")
                    || matchSymbol("/=")
                    || matchSymbol("%=")
                    || matchSymbol("&=")
                    || matchSymbol("|=")
                    || matchSymbol("^=")
                    || matchSymbol("<<=")
                    || matchSymbol(">>=")
                    || matchSymbol(">>>=")
                    || matchSymbol("**=")
                    || matchSymbol("&&=")
                    || matchSymbol("||=")
                    || matchSymbol("??=")) {
                return previous();
            }
            return null;
        }

        private boolean isAssignmentTarget(final Expression expression) {
            if (expression instanceof VariableExpression || expression instanceof MemberAccessExpression) {
                return true;
            }
            if (expression instanceof CallExpression callExpression) {
                return isIndexReadAssignmentTarget(callExpression);
            }
            return false;
        }

        private boolean isIndexReadAssignmentTarget(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_index_read".equals(variableExpression.name()) && callExpression.arguments().size() == 2;
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

        private boolean matchSoftKeyword(final String keyword) {
            if ((current().type() == TokenType.KEYWORD || current().type() == TokenType.IDENTIFIER)
                    && keyword.equals(current().text())) {
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

    private record ProgramOptimizationResult(
            Program program,
            Map<Statement, SourceLocation> statementLocations
    ) {
    }

    private static final class ProgramOptimizer {
        private final JvmOptimizationOptions options;
        private final IdentityHashMap<Statement, SourceLocation> sourceLocations;
        private final IdentityHashMap<Statement, SourceLocation> rewrittenSourceLocations;

        private ProgramOptimizer(
                final JvmOptimizationOptions options,
                final Map<Statement, SourceLocation> sourceLocations
        ) {
            this.options = options;
            this.sourceLocations = new IdentityHashMap<>(sourceLocations);
            this.rewrittenSourceLocations = new IdentityHashMap<>();
        }

        private ProgramOptimizationResult optimize(final Program program) {
            final List<Statement> optimizedStatements = optimizeStatementList(program.statements());
            final IdentityHashMap<Statement, SourceLocation> mergedSourceLocations =
                    new IdentityHashMap<>(sourceLocations);
            mergedSourceLocations.putAll(rewrittenSourceLocations);
            return new ProgramOptimizationResult(
                    new Program(List.copyOf(optimizedStatements)),
                    mergedSourceLocations
            );
        }

        private List<Statement> optimizeStatementList(final List<Statement> statements) {
            final List<Statement> optimized = new ArrayList<>();
            boolean terminated = false;
            for (Statement statement : statements) {
                if (terminated) {
                    continue;
                }
                final List<Statement> rewritten = optimizeStatement(statement);
                for (Statement candidate : rewritten) {
                    optimized.add(candidate);
                    if (isTerminal(candidate)) {
                        terminated = true;
                    }
                }
            }
            return List.copyOf(optimized);
        }

        private List<Statement> optimizeStatement(final Statement statement) {
            if (statement instanceof VariableDeclaration declaration) {
                final VariableDeclaration rewritten =
                        new VariableDeclaration(declaration.name(), optimizeExpression(declaration.expression()));
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof AssignmentStatement assignment) {
                final AssignmentStatement rewritten = new AssignmentStatement(
                        optimizeExpression(assignment.target()),
                        optimizeExpression(assignment.expression())
                );
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                final FunctionDeclaration declaration = declarationStatement.declaration();
                final FunctionDeclaration rewrittenDeclaration = new FunctionDeclaration(
                        declaration.name(),
                        declaration.parameters(),
                        optimizeStatementList(declaration.body()),
                        declaration.async(),
                        declaration.generator()
                );
                final FunctionDeclarationStatement rewritten = new FunctionDeclarationStatement(rewrittenDeclaration);
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof ClassDeclarationStatement classDeclarationStatement) {
                final ClassDeclaration declaration = classDeclarationStatement.declaration();
                ClassMethod constructorMethod = null;
                if (declaration.constructorMethod() != null) {
                    final ClassMethod constructor = declaration.constructorMethod();
                    constructorMethod = new ClassMethod(
                            constructor.name(),
                            constructor.parameters(),
                            optimizeStatementList(constructor.body()),
                            constructor.async()
                    );
                }
                final List<ClassMethod> methods = new ArrayList<>();
                for (ClassMethod method : declaration.methods()) {
                    methods.add(new ClassMethod(
                            method.name(),
                            method.parameters(),
                            optimizeStatementList(method.body()),
                            method.async()
                    ));
                }
                final ClassDeclaration rewrittenDeclaration = new ClassDeclaration(
                        declaration.name(),
                        declaration.superClassName(),
                        declaration.fieldNames(),
                        constructorMethod,
                        List.copyOf(methods)
                );
                final ClassDeclarationStatement rewritten = new ClassDeclarationStatement(rewrittenDeclaration);
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof IfStatement ifStatement) {
                final Expression rewrittenCondition = optimizeExpression(ifStatement.condition());
                final List<Statement> rewrittenThen = optimizeStatementList(ifStatement.thenBlock());
                final List<Statement> rewrittenElse = optimizeStatementList(ifStatement.elseBlock());

                if (options.deadCodeEliminationEnabled()) {
                    final ConstantTruthiness truthiness = constantTruthiness(rewrittenCondition);
                    if (truthiness == ConstantTruthiness.ALWAYS_FALSE) {
                        if (rewrittenElse.isEmpty()) {
                            return List.of();
                        }
                        final IfStatement normalized = new IfStatement(
                                new BooleanLiteral(true),
                                rewrittenElse,
                                List.of()
                        );
                        copySourceLocation(statement, normalized);
                        return List.of(normalized);
                    }
                    if (truthiness == ConstantTruthiness.ALWAYS_TRUE) {
                        final IfStatement normalized = new IfStatement(
                                new BooleanLiteral(true),
                                rewrittenThen,
                                List.of()
                        );
                        copySourceLocation(statement, normalized);
                        return List.of(normalized);
                    }
                }

                final IfStatement rewritten = new IfStatement(rewrittenCondition, rewrittenThen, rewrittenElse);
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof WhileStatement whileStatement) {
                final Expression rewrittenCondition = optimizeExpression(whileStatement.condition());
                final List<Statement> rewrittenBody = optimizeStatementList(whileStatement.body());
                if (options.deadCodeEliminationEnabled()
                        && constantTruthiness(rewrittenCondition) == ConstantTruthiness.ALWAYS_FALSE) {
                    return List.of();
                }
                final WhileStatement rewritten = new WhileStatement(rewrittenCondition, rewrittenBody);
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof SuperCallStatement superCallStatement) {
                final List<Expression> rewrittenArguments = new ArrayList<>();
                for (Expression argument : superCallStatement.arguments()) {
                    rewrittenArguments.add(optimizeExpression(argument));
                }
                final SuperCallStatement rewritten = new SuperCallStatement(List.copyOf(rewrittenArguments));
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof ReturnStatement returnStatement) {
                final ReturnStatement rewritten = new ReturnStatement(optimizeExpression(returnStatement.expression()));
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof ThrowStatement throwStatement) {
                final ThrowStatement rewritten = new ThrowStatement(optimizeExpression(throwStatement.expression()));
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof ConsoleLogStatement logStatement) {
                final ConsoleLogStatement rewritten = new ConsoleLogStatement(optimizeExpression(logStatement.expression()));
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            if (statement instanceof ExpressionStatement expressionStatement) {
                final Expression rewrittenExpression = optimizeExpression(expressionStatement.expression());
                if (options.deadCodeEliminationEnabled() && isPureLiteralExpression(rewrittenExpression)) {
                    return List.of();
                }
                final ExpressionStatement rewritten = new ExpressionStatement(rewrittenExpression);
                copySourceLocation(statement, rewritten);
                return List.of(rewritten);
            }
            return List.of(statement);
        }

        private boolean isPureLiteralExpression(final Expression expression) {
            if (expression instanceof NumberLiteral
                    || expression instanceof StringLiteral
                    || expression instanceof BooleanLiteral
                    || expression instanceof NullLiteral
                    || expression instanceof UndefinedLiteral) {
                return true;
            }
            if (expression instanceof UnaryExpression unaryExpression) {
                return isPureLiteralExpression(unaryExpression.expression());
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                return isPureLiteralExpression(binaryExpression.left())
                        && isPureLiteralExpression(binaryExpression.right());
            }
            return false;
        }

        private boolean isTerminal(final Statement statement) {
            return statement instanceof ReturnStatement || statement instanceof ThrowStatement;
        }

        private Expression optimizeExpression(final Expression expression) {
            if (expression instanceof UnaryExpression unaryExpression) {
                final Expression operand = optimizeExpression(unaryExpression.expression());
                final UnaryExpression rewritten = new UnaryExpression(unaryExpression.operator(), operand);
                return maybeFoldExpression(rewritten);
            }
            if (expression instanceof YieldExpression yieldExpression) {
                return new YieldExpression(optimizeExpression(yieldExpression.expression()), yieldExpression.delegate());
            }
            if (expression instanceof AwaitExpression awaitExpression) {
                return new AwaitExpression(optimizeExpression(awaitExpression.expression()));
            }
            if (expression instanceof FunctionExpression functionExpression) {
                return new FunctionExpression(
                        functionExpression.parameters(),
                        optimizeStatementList(functionExpression.body()),
                        functionExpression.async(),
                        functionExpression.generator(),
                        functionExpression.thisMode()
                );
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                final Expression left = optimizeExpression(binaryExpression.left());
                final Expression right = optimizeExpression(binaryExpression.right());
                final BinaryExpression rewritten = new BinaryExpression(left, binaryExpression.operator(), right);
                return maybeFoldExpression(rewritten);
            }
            if (expression instanceof AssignmentExpression assignmentExpression) {
                final Expression target = optimizeExpression(assignmentExpression.target());
                final Expression value = optimizeExpression(assignmentExpression.expression());
                return new AssignmentExpression(target, assignmentExpression.operator(), value);
            }
            if (expression instanceof CallExpression callExpression) {
                final Expression callee = optimizeExpression(callExpression.callee());
                final List<Expression> arguments = new ArrayList<>();
                for (Expression argument : callExpression.arguments()) {
                    arguments.add(optimizeExpression(argument));
                }
                return new CallExpression(callee, List.copyOf(arguments));
            }
            if (expression instanceof OptionalCallExpression optionalCallExpression) {
                final Expression callee = optimizeExpression(optionalCallExpression.callee());
                final List<Expression> arguments = new ArrayList<>();
                for (Expression argument : optionalCallExpression.arguments()) {
                    arguments.add(optimizeExpression(argument));
                }
                return new OptionalCallExpression(callee, List.copyOf(arguments));
            }
            if (expression instanceof MemberAccessExpression memberAccessExpression) {
                return new MemberAccessExpression(
                        optimizeExpression(memberAccessExpression.receiver()),
                        memberAccessExpression.member()
                );
            }
            if (expression instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                return new OptionalMemberAccessExpression(
                        optimizeExpression(optionalMemberAccessExpression.receiver()),
                        optionalMemberAccessExpression.member()
                );
            }
            if (expression instanceof NewExpression newExpression) {
                final Expression constructor = optimizeExpression(newExpression.constructor());
                final List<Expression> arguments = new ArrayList<>();
                for (Expression argument : newExpression.arguments()) {
                    arguments.add(optimizeExpression(argument));
                }
                return new NewExpression(constructor, List.copyOf(arguments));
            }
            if (expression instanceof ArrayLiteralExpression arrayLiteralExpression) {
                final List<Expression> elements = new ArrayList<>();
                for (Expression element : arrayLiteralExpression.elements()) {
                    elements.add(optimizeExpression(element));
                }
                return new ArrayLiteralExpression(List.copyOf(elements));
            }
            if (expression instanceof ObjectLiteralExpression objectLiteralExpression) {
                final List<ObjectLiteralEntry> entries = new ArrayList<>();
                for (ObjectLiteralEntry entry : objectLiteralExpression.entries()) {
                    entries.add(new ObjectLiteralEntry(entry.key(), optimizeExpression(entry.value())));
                }
                return new ObjectLiteralExpression(List.copyOf(entries));
            }
            return expression;
        }

        private Expression maybeFoldExpression(final Expression expression) {
            if (!options.constantFoldingEnabled()) {
                return expression;
            }
            final ConstantValue constantValue = constantOf(expression);
            if (constantValue == null) {
                return expression;
            }
            final Expression folded = expressionFromConstant(constantValue);
            return folded == null ? expression : folded;
        }

        private ConstantTruthiness constantTruthiness(final Expression expression) {
            final ConstantValue constantValue = constantOf(expression);
            if (constantValue == null) {
                return ConstantTruthiness.UNKNOWN;
            }
            return isTruthy(constantValue) ? ConstantTruthiness.ALWAYS_TRUE : ConstantTruthiness.ALWAYS_FALSE;
        }

        private ConstantValue constantOf(final Expression expression) {
            if (expression instanceof NumberLiteral numberLiteral) {
                try {
                    return ConstantValue.number(Double.parseDouble(numberLiteral.value()));
                } catch (final NumberFormatException ignored) {
                    return null;
                }
            }
            if (expression instanceof StringLiteral stringLiteral) {
                return ConstantValue.string(stringLiteral.value());
            }
            if (expression instanceof BooleanLiteral booleanLiteral) {
                return ConstantValue.bool(booleanLiteral.value());
            }
            if (expression instanceof NullLiteral) {
                return ConstantValue.nullValue();
            }
            if (expression instanceof UndefinedLiteral) {
                return ConstantValue.undefinedValue();
            }
            if (expression instanceof UnaryExpression unaryExpression) {
                final ConstantValue operand = constantOf(unaryExpression.expression());
                if (operand == null) {
                    return null;
                }
                if ("+".equals(unaryExpression.operator())) {
                    return ConstantValue.number(toNumber(operand));
                }
                if ("-".equals(unaryExpression.operator())) {
                    return ConstantValue.number(-toNumber(operand));
                }
                if ("!".equals(unaryExpression.operator())) {
                    return ConstantValue.bool(!isTruthy(operand));
                }
                if ("~".equals(unaryExpression.operator())) {
                    return ConstantValue.number((double) ~toInt32(operand));
                }
                if ("typeof".equals(unaryExpression.operator())) {
                    return ConstantValue.string(typeOfConstant(operand));
                }
                return null;
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                final ConstantValue left = constantOf(binaryExpression.left());
                final ConstantValue right = constantOf(binaryExpression.right());
                if (left == null || right == null) {
                    return null;
                }
                return switch (binaryExpression.operator()) {
                    case "+" -> foldAddition(left, right);
                    case "-" -> ConstantValue.number(toNumber(left) - toNumber(right));
                    case "*" -> ConstantValue.number(toNumber(left) * toNumber(right));
                    case "/" -> ConstantValue.number(toNumber(left) / toNumber(right));
                    case "%" -> ConstantValue.number(toNumber(left) % toNumber(right));
                    case "**" -> ConstantValue.number(Math.pow(toNumber(left), toNumber(right)));
                    case "<<" -> ConstantValue.number((double) (toInt32(left) << (toUint32(right) & 31)));
                    case ">>" -> ConstantValue.number((double) (toInt32(left) >> (toUint32(right) & 31)));
                    case ">>>" -> ConstantValue.number((double) Integer.toUnsignedLong(
                            toInt32(left) >>> (toUint32(right) & 31)
                    ));
                    case "&" -> ConstantValue.number((double) (toInt32(left) & toInt32(right)));
                    case "^" -> ConstantValue.number((double) (toInt32(left) ^ toInt32(right)));
                    case "|" -> ConstantValue.number((double) (toInt32(left) | toInt32(right)));
                    case "<" -> ConstantValue.bool(lessThan(left, right));
                    case "<=" -> ConstantValue.bool(lessThanOrEqual(left, right));
                    case ">" -> ConstantValue.bool(greaterThan(left, right));
                    case ">=" -> ConstantValue.bool(greaterThanOrEqual(left, right));
                    case "==" -> ConstantValue.bool(abstractEquals(left, right));
                    case "!=" -> ConstantValue.bool(!abstractEquals(left, right));
                    case "===" -> ConstantValue.bool(strictEquals(left, right));
                    case "!==" -> ConstantValue.bool(!strictEquals(left, right));
                    default -> null;
                };
            }
            return null;
        }

        private ConstantValue foldAddition(final ConstantValue left, final ConstantValue right) {
            if (left.kind() == ConstantKind.STRING || right.kind() == ConstantKind.STRING) {
                return ConstantValue.string(toJsString(left) + toJsString(right));
            }
            return ConstantValue.number(toNumber(left) + toNumber(right));
        }

        private boolean lessThan(final ConstantValue left, final ConstantValue right) {
            if (left.kind() == ConstantKind.STRING && right.kind() == ConstantKind.STRING) {
                return left.stringValue().compareTo(right.stringValue()) < 0;
            }
            final double leftNumber = toNumber(left);
            final double rightNumber = toNumber(right);
            if (Double.isNaN(leftNumber) || Double.isNaN(rightNumber)) {
                return false;
            }
            return leftNumber < rightNumber;
        }

        private boolean lessThanOrEqual(final ConstantValue left, final ConstantValue right) {
            if (left.kind() == ConstantKind.STRING && right.kind() == ConstantKind.STRING) {
                return left.stringValue().compareTo(right.stringValue()) <= 0;
            }
            final double leftNumber = toNumber(left);
            final double rightNumber = toNumber(right);
            if (Double.isNaN(leftNumber) || Double.isNaN(rightNumber)) {
                return false;
            }
            return leftNumber <= rightNumber;
        }

        private boolean greaterThan(final ConstantValue left, final ConstantValue right) {
            return lessThan(right, left);
        }

        private boolean greaterThanOrEqual(final ConstantValue left, final ConstantValue right) {
            return lessThanOrEqual(right, left);
        }

        private boolean strictEquals(final ConstantValue left, final ConstantValue right) {
            if (left.kind() != right.kind()) {
                return false;
            }
            return switch (left.kind()) {
                case NUMBER -> {
                    final double leftNumber = left.numberValue();
                    final double rightNumber = right.numberValue();
                    if (Double.isNaN(leftNumber) || Double.isNaN(rightNumber)) {
                        yield false;
                    }
                    yield leftNumber == rightNumber;
                }
                case STRING -> left.stringValue().equals(right.stringValue());
                case BOOLEAN -> left.booleanValue() == right.booleanValue();
                case NULL, UNDEFINED -> true;
            };
        }

        private boolean abstractEquals(final ConstantValue left, final ConstantValue right) {
            if (left.kind() == right.kind()) {
                return strictEquals(left, right);
            }
            if ((left.kind() == ConstantKind.NULL && right.kind() == ConstantKind.UNDEFINED)
                    || (left.kind() == ConstantKind.UNDEFINED && right.kind() == ConstantKind.NULL)) {
                return true;
            }
            if (left.kind() == ConstantKind.NUMBER && right.kind() == ConstantKind.STRING) {
                return strictEquals(left, ConstantValue.number(toNumber(right)));
            }
            if (left.kind() == ConstantKind.STRING && right.kind() == ConstantKind.NUMBER) {
                return strictEquals(ConstantValue.number(toNumber(left)), right);
            }
            if (left.kind() == ConstantKind.BOOLEAN) {
                return abstractEquals(ConstantValue.number(toNumber(left)), right);
            }
            if (right.kind() == ConstantKind.BOOLEAN) {
                return abstractEquals(left, ConstantValue.number(toNumber(right)));
            }
            return false;
        }

        private String typeOfConstant(final ConstantValue value) {
            return switch (value.kind()) {
                case NUMBER -> "number";
                case STRING -> "string";
                case BOOLEAN -> "boolean";
                case NULL -> "object";
                case UNDEFINED -> "undefined";
            };
        }

        private double toNumber(final ConstantValue value) {
            return switch (value.kind()) {
                case NUMBER -> value.numberValue();
                case STRING -> parseStringNumber(value.stringValue());
                case BOOLEAN -> value.booleanValue() ? 1d : 0d;
                case NULL -> 0d;
                case UNDEFINED -> Double.NaN;
            };
        }

        private int toInt32(final ConstantValue value) {
            return (int) toUint32(value);
        }

        private int toUint32(final ConstantValue value) {
            final double number = toNumber(value);
            if (!Double.isFinite(number) || number == 0d) {
                return 0;
            }
            final long truncated = number < 0d ? (long) Math.ceil(number) : (long) Math.floor(number);
            final long uint32 = truncated & 0xFFFF_FFFFL;
            return (int) uint32;
        }

        private String toJsString(final ConstantValue value) {
            return switch (value.kind()) {
                case NUMBER -> jsNumberToString(value.numberValue());
                case STRING -> value.stringValue();
                case BOOLEAN -> value.booleanValue() ? "true" : "false";
                case NULL -> "null";
                case UNDEFINED -> "undefined";
            };
        }

        private String jsNumberToString(final double number) {
            if (Double.isNaN(number)) {
                return "NaN";
            }
            if (Double.isInfinite(number)) {
                return number > 0 ? "Infinity" : "-Infinity";
            }
            if (number == 0d) {
                return "0";
            }
            if (Math.rint(number) == number && number >= Long.MIN_VALUE && number <= Long.MAX_VALUE) {
                return Long.toString((long) number);
            }
            return Double.toString(number);
        }

        private double parseStringNumber(final String value) {
            final String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return 0d;
            }
            try {
                return Double.parseDouble(trimmed);
            } catch (final NumberFormatException ignored) {
                return Double.NaN;
            }
        }

        private boolean isTruthy(final ConstantValue value) {
            return switch (value.kind()) {
                case NUMBER -> value.numberValue() != 0d && !Double.isNaN(value.numberValue());
                case STRING -> !value.stringValue().isEmpty();
                case BOOLEAN -> value.booleanValue();
                case NULL, UNDEFINED -> false;
            };
        }

        private Expression expressionFromConstant(final ConstantValue value) {
            return switch (value.kind()) {
                case NUMBER -> {
                    final String rendered = renderNumericLiteral(value.numberValue());
                    yield rendered == null ? null : new NumberLiteral(rendered);
                }
                case STRING -> new StringLiteral(value.stringValue());
                case BOOLEAN -> new BooleanLiteral(value.booleanValue());
                case NULL -> new NullLiteral();
                case UNDEFINED -> new UndefinedLiteral();
            };
        }

        private String renderNumericLiteral(final double number) {
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return null;
            }
            if (Math.rint(number) == number
                    && number >= Integer.MIN_VALUE
                    && number <= Integer.MAX_VALUE) {
                return Integer.toString((int) number);
            }
            String rendered = Double.toString(number);
            if (!rendered.contains(".") && !rendered.contains("E") && !rendered.contains("e")) {
                rendered = rendered + ".0";
            }
            return rendered;
        }

        private SourceLocation sourceLocationFor(final Statement statement) {
            final SourceLocation rewritten = rewrittenSourceLocations.get(statement);
            if (rewritten != null) {
                return rewritten;
            }
            return sourceLocations.get(statement);
        }

        private void copySourceLocation(final Statement source, final Statement rewritten) {
            final SourceLocation location = sourceLocationFor(source);
            if (location != null) {
                rewrittenSourceLocations.put(rewritten, location);
            }
        }

        private enum ConstantTruthiness {
            ALWAYS_TRUE,
            ALWAYS_FALSE,
            UNKNOWN
        }

        private enum ConstantKind {
            NUMBER,
            STRING,
            BOOLEAN,
            NULL,
            UNDEFINED
        }

        private record ConstantValue(ConstantKind kind, Double numberValue, String stringValue, Boolean booleanValue) {
            private static ConstantValue number(final double value) {
                return new ConstantValue(ConstantKind.NUMBER, value, null, null);
            }

            private static ConstantValue string(final String value) {
                return new ConstantValue(ConstantKind.STRING, null, value, null);
            }

            private static ConstantValue bool(final boolean value) {
                return new ConstantValue(ConstantKind.BOOLEAN, null, null, value);
            }

            private static ConstantValue nullValue() {
                return new ConstantValue(ConstantKind.NULL, null, null, null);
            }

            private static ConstantValue undefinedValue() {
                return new ConstantValue(ConstantKind.UNDEFINED, null, null, null);
            }
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
            final StringBuilder bootstrapBody = new StringBuilder();
            final EmissionContext mainContext = new EmissionContext(null);
            if (requiresTopLevelAwaitLowering(program.statements())) {
                emitTopLevelAwaitStatements(bootstrapBody, mainContext, program.statements(), "        ");
            } else {
                emitStatements(bootstrapBody, mainContext, program.statements(), "        ", false);
            }
            bootstrapBody.append("        dev.tsj.runtime.TsjRuntime.flushMicrotasks();\n");
            bootstrapBody.append("        ").append(BOOTSTRAP_GUARD_FIELD).append(" = true;\n");

            final StringBuilder builder = new StringBuilder();
            builder.append("package ").append(packageName).append(";\n\n");
            builder.append("public final class ").append(classSimpleName).append(" {\n");
            builder.append("    private ").append(classSimpleName).append("() {\n");
            builder.append("    }\n\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell PROMISE_BUILTIN_CELL = ")
                    .append("new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.promiseBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(ERROR_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.errorBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(STRING_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.stringBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(JSON_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.jsonBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(OBJECT_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(REFLECT_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.reflectBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(PROXY_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.proxyBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(ARRAY_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arrayBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(MAP_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.mapBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(SET_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.setBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(WEAK_MAP_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.weakMapBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(WEAK_SET_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.weakSetBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(WEAK_REF_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.weakRefBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(DATE_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.dateBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(REGEXP_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.regexpBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(AGGREGATE_ERROR_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.aggregateErrorBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(TYPE_ERROR_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.typeErrorBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(RANGE_ERROR_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.rangeErrorBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(MATH_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.mathBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(NUMBER_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.numberBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(BIGINT_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.bigIntBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(SYMBOL_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.symbolBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(PARSE_INT_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.parseIntBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(PARSE_FLOAT_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.parseFloatBuiltin());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(INFINITY_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.infinity());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(NAN_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.nanValue());\n");
            builder.append("    private static final dev.tsj.runtime.TsjCell ")
                    .append(UNDEFINED_BUILTIN_CELL_FIELD)
                    .append(" = new dev.tsj.runtime.TsjCell(null);\n");
            builder.append("    private static final Object ")
                    .append(ASYNC_BREAK_SIGNAL_FIELD)
                    .append(" = new Object();\n");
            builder.append("    private static final Object ")
                    .append(ASYNC_CONTINUE_SIGNAL_FIELD)
                    .append(" = new Object();\n");
            builder.append("    private static final java.util.Map<String, Object> ")
                    .append(TOP_LEVEL_CLASS_MAP_FIELD)
                    .append(" = new java.util.LinkedHashMap<>();\n");
            builder.append("    private static boolean ")
                    .append(BOOTSTRAP_GUARD_FIELD)
                    .append(" = false;\n");
            if (!propertyCacheFieldDeclarations.isEmpty()) {
                builder.append("\n");
            }
            for (String declaration : propertyCacheFieldDeclarations) {
                builder.append("    ").append(declaration).append("\n");
            }
            if (!propertyCacheFieldDeclarations.isEmpty()) {
                builder.append("\n");
            }
            builder.append("    private static synchronized void __tsjBootstrap() {\n");
            builder.append("        if (").append(BOOTSTRAP_GUARD_FIELD).append(") {\n");
            builder.append("            return;\n");
            builder.append("        }\n");
            builder.append("        ").append(TOP_LEVEL_CLASS_MAP_FIELD).append(".clear();\n");
            builder.append(bootstrapBody);
            builder.append("    }\n\n");
            builder.append("    public static Object __tsjInvokeClass(")
                    .append("final String className, final String methodName, ")
                    .append("final Object[] constructorArgs, final Object... args")
                    .append(") {\n");
            builder.append("        return __tsjInvokeClassWithInjection(")
                    .append("className, methodName, constructorArgs, ")
                    .append("new String[0], new Object[0], new String[0], new Object[0], args")
                    .append(");\n");
            builder.append("    }\n\n");
            builder.append("    public static Object __tsjInvokeClassWithInjection(")
                    .append("final String className, final String methodName, ")
                    .append("final Object[] constructorArgs, ")
                    .append("final String[] fieldNames, final Object[] fieldValues, ")
                    .append("final String[] setterNames, final Object[] setterValues, ")
                    .append("final Object... args")
                    .append(") {\n");
            builder.append("        __tsjBootstrap();\n");
            builder.append("        final Object classValue = ")
                    .append(TOP_LEVEL_CLASS_MAP_FIELD)
                    .append(".get(className);\n");
            builder.append("        if (classValue == null) {\n");
            builder.append("            throw new IllegalArgumentException(")
                    .append("\"TSJ controller class not found: \" + className);\n");
            builder.append("        }\n");
            builder.append("        final Object[] ctorArgs = constructorArgs == null ? new Object[0] : constructorArgs;\n");
            builder.append("        final String[] safeFieldNames = fieldNames == null ? new String[0] : fieldNames;\n");
            builder.append("        final Object[] safeFieldValues = fieldValues == null ? new Object[0] : fieldValues;\n");
            builder.append("        if (safeFieldNames.length != safeFieldValues.length) {\n");
            builder.append("            throw new IllegalArgumentException(\"TSJ injection field name/value length mismatch.\");\n");
            builder.append("        }\n");
            builder.append("        final String[] safeSetterNames = setterNames == null ? new String[0] : setterNames;\n");
            builder.append("        final Object[] safeSetterValues = setterValues == null ? new Object[0] : setterValues;\n");
            builder.append("        if (safeSetterNames.length != safeSetterValues.length) {\n");
            builder.append("            throw new IllegalArgumentException(\"TSJ injection setter name/value length mismatch.\");\n");
            builder.append("        }\n");
            builder.append("        final Object instance = dev.tsj.runtime.TsjRuntime.construct(classValue, ctorArgs);\n");
            builder.append("        for (int i = 0; i < safeFieldNames.length; i++) {\n");
            builder.append("            dev.tsj.runtime.TsjRuntime.setProperty(instance, safeFieldNames[i], safeFieldValues[i]);\n");
            builder.append("        }\n");
            builder.append("        for (int i = 0; i < safeSetterNames.length; i++) {\n");
            builder.append("            dev.tsj.runtime.TsjRuntime.invokeMember(instance, safeSetterNames[i], safeSetterValues[i]);\n");
            builder.append("        }\n");
            builder.append("        final Object result = dev.tsj.runtime.TsjRuntime.invokeMember(")
                    .append("instance, methodName, args);\n");
            builder.append("        dev.tsj.runtime.TsjRuntime.flushMicrotasks();\n");
            builder.append("        return result;\n");
            builder.append("    }\n\n");
            builder.append("    public static Object __tsjInvokeController(")
                    .append("final String className, final String methodName, final Object... args")
                    .append(") {\n");
            builder.append("        return __tsjInvokeClass(className, methodName, new Object[0], args);\n");
            builder.append("    }\n\n");
            builder.append("    public static void main(String[] args) {\n");
            builder.append("        __tsjBootstrap();\n");
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
                if (statement instanceof LabeledStatement labeledStatement) {
                    emitLabeledStatement(builder, context, labeledStatement, indent, insideFunction);
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
                    emitWhileStatement(builder, context, whileStatement, indent, insideFunction, null);
                    continue;
                }
                if (statement instanceof TryStatement tryStatement) {
                    builder.append(indent).append("try {\n");
                    emitStatements(
                            builder,
                            new EmissionContext(context),
                            tryStatement.tryBlock(),
                            indent + "    ",
                            insideFunction
                    );
                    builder.append(indent).append("}");
                    if (tryStatement.hasCatch()) {
                        builder.append(" catch (RuntimeException __tsjCaughtError) {\n");
                        final EmissionContext catchContext = new EmissionContext(context);
                        if (tryStatement.catchBinding() != null) {
                            final String catchCellName = catchContext.declareBinding(tryStatement.catchBinding());
                            builder.append(indent)
                                    .append("    final dev.tsj.runtime.TsjCell ")
                                    .append(catchCellName)
                                    .append(" = new dev.tsj.runtime.TsjCell(")
                                    .append("dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjCaughtError));\n");
                        }
                        emitStatements(
                                builder,
                                catchContext,
                                tryStatement.catchBlock(),
                                indent + "    ",
                                insideFunction
                        );
                        builder.append(indent).append("}");
                    }
                    if (tryStatement.hasFinally()) {
                        builder.append(" finally {\n");
                        emitStatements(
                                builder,
                                new EmissionContext(context),
                                tryStatement.finallyBlock(),
                                indent + "    ",
                                insideFunction
                        );
                        builder.append(indent).append("}");
                    }
                    builder.append("\n");
                    continue;
                }
                if (statement instanceof BreakStatement breakStatement) {
                    emitBreakStatement(builder, context, breakStatement, indent);
                    continue;
                }
                if (statement instanceof ContinueStatement continueStatement) {
                    emitContinueStatement(builder, context, continueStatement, indent);
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

        private void emitLabeledStatement(
                final StringBuilder builder,
                final EmissionContext context,
                final LabeledStatement labeledStatement,
                final String indent,
                final boolean insideFunction
        ) {
            final EmissionContext labelContext = new EmissionContext(context);
            final String javaLabel = labelContext.declareLabel(labeledStatement.label());
            final Statement body = labeledStatement.statement();
            if (body instanceof WhileStatement whileStatement) {
                emitWhileStatement(builder, labelContext, whileStatement, indent, insideFunction, javaLabel);
                return;
            }

            builder.append(indent)
                    .append(javaLabel)
                    .append(": {\n");
            emitStatements(
                    builder,
                    labelContext,
                    List.of(body),
                    indent + "    ",
                    insideFunction
            );
            builder.append(indent).append("}\n");
        }

        private void emitWhileStatement(
                final StringBuilder builder,
                final EmissionContext context,
                final WhileStatement whileStatement,
                final String indent,
                final boolean insideFunction,
                final String label
        ) {
            builder.append(indent);
            if (label != null) {
                builder.append(label).append(": ");
            }
            builder.append("while (dev.tsj.runtime.TsjRuntime.truthy(")
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
        }

        private void emitBreakStatement(
                final StringBuilder builder,
                final EmissionContext context,
                final BreakStatement breakStatement,
                final String indent
        ) {
            builder.append(indent).append("break");
            if (breakStatement.label() != null) {
                builder.append(" ").append(context.resolveLabel(breakStatement.label()));
            }
            builder.append(";\n");
        }

        private void emitContinueStatement(
                final StringBuilder builder,
                final EmissionContext context,
                final ContinueStatement continueStatement,
                final String indent
        ) {
            builder.append(indent).append("continue");
            if (continueStatement.label() != null) {
                builder.append(" ").append(context.resolveLabel(continueStatement.label()));
            }
            builder.append(";\n");
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
            if (declaration.generator()) {
                emitGeneratorFunctionAssignment(builder, context, declaration, indent);
                return;
            }
            if (declaration.async()) {
                emitAsyncFunctionAssignment(builder, context, declaration, indent);
                return;
            }

            final String cellName = context.resolveBinding(declaration.name());
            final String thisVar = context.allocateGeneratedName("lambdaThis");
            final String argsVar = context.allocateGeneratedName("lambdaArgs");

            builder.append(indent)
                    .append(cellName)
                    .append(".set((dev.tsj.runtime.TsjCallableWithThis) (Object ")
                    .append(thisVar)
                    .append(", Object... ")
                    .append(argsVar)
                    .append(") -> {\n");

            final EmissionContext functionContext =
                    new EmissionContext(context, thisVar, context.resolveSuperClassExpression(), false, argsVar);
            emitParameterCells(builder, functionContext, declaration.parameters(), argsVar, indent + "    ");

            emitStatements(builder, functionContext, declaration.body(), indent + "    ", true);
            if (!blockAlwaysExits(declaration.body())) {
                builder.append(indent).append("    return null;\n");
            }

            builder.append(indent).append("});\n");
        }

        private void emitGeneratorFunctionAssignment(
                final StringBuilder builder,
                final EmissionContext context,
                final FunctionDeclaration declaration,
                final String indent
        ) {
            final String cellName = context.resolveBinding(declaration.name());
            final String thisVar = context.allocateGeneratedName("lambdaThis");
            final String argsVar = context.allocateGeneratedName("lambdaArgs");
            final String generatorThisVar = context.allocateGeneratedName("generatorThis");
            final String generatorArgsVar = context.allocateGeneratedName("generatorArgs");

            builder.append(indent)
                    .append(cellName)
                    .append(".set((dev.tsj.runtime.TsjCallableWithThis) (Object ")
                    .append(thisVar)
                    .append(", Object... ")
                    .append(argsVar)
                    .append(") -> {\n");
            builder.append(indent)
                    .append("    return dev.tsj.runtime.TsjRuntime.createGenerator(")
                    .append("(dev.tsj.runtime.TsjCallableWithThis) (Object ")
                    .append(generatorThisVar)
                    .append(", Object... ")
                    .append(generatorArgsVar)
                    .append(") -> {\n");

            final EmissionContext generatorContext =
                    new EmissionContext(context, generatorThisVar, context.resolveSuperClassExpression(), false, generatorArgsVar);
            emitParameterCells(builder, generatorContext, declaration.parameters(), generatorArgsVar, indent + "        ");
            emitStatements(builder, generatorContext, declaration.body(), indent + "        ", true);
            if (!blockAlwaysExits(declaration.body())) {
                builder.append(indent).append("        return null;\n");
            }

            builder.append(indent)
                    .append("    }, ")
                    .append(thisVar)
                    .append(", ")
                    .append(argsVar)
                    .append(");\n");
            builder.append(indent).append("});\n");
        }

        private void emitAsyncFunctionAssignment(
                final StringBuilder builder,
                final EmissionContext context,
                final FunctionDeclaration declaration,
                final String indent
        ) {
            final String cellName = context.resolveBinding(declaration.name());
            final String thisVar = context.allocateGeneratedName("lambdaThis");
            final String argsVar = context.allocateGeneratedName("lambdaArgs");

            builder.append(indent)
                    .append(cellName)
                    .append(".set((dev.tsj.runtime.TsjCallableWithThis) (Object ")
                    .append(thisVar)
                    .append(", Object... ")
                    .append(argsVar)
                    .append(") -> {\n");

            final EmissionContext functionContext =
                    new EmissionContext(context, thisVar, context.resolveSuperClassExpression(), false, argsVar);
            final List<Statement> normalizedBody =
                    normalizeAsyncStatementsForAwaitExpressions(functionContext, declaration.body());
            emitParameterCells(builder, functionContext, declaration.parameters(), argsVar, indent + "    ");
            predeclareFunctionBindings(builder, functionContext, normalizedBody, indent + "    ");
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
            if (context.isTopLevelScope()) {
                builder.append(indent)
                        .append(TOP_LEVEL_CLASS_MAP_FIELD)
                        .append(".put(\"")
                        .append(escapeJava(declaration.name()))
                        .append("\", ")
                        .append(classCellName)
                        .append(".get());\n");
            }

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
                    new EmissionContext(context, thisVar, superClassExpression, constructor, argsVar);
            emitParameterCells(builder, methodContext, method.parameters(), argsVar, indent + "    ");
            if (!constructor && method.async()) {
                final List<Statement> normalizedBody =
                        normalizeAsyncStatementsForAwaitExpressions(methodContext, method.body());
                predeclareFunctionBindings(builder, methodContext, normalizedBody, indent + "    ");
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
                if (!blockAlwaysExits(method.body())) {
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
                        .append("] : dev.tsj.runtime.TsjRuntime.undefined());\n");
            }
        }

        private String emitFunctionExpression(
                final EmissionContext context,
                final FunctionExpression functionExpression
        ) {
            if (functionExpression.generator()) {
                return emitGeneratorFunctionExpression(context, functionExpression);
            }

            final String argsVar = context.allocateGeneratedName("lambdaArgs");
            final StringBuilder functionBuilder = new StringBuilder();
            final EmissionContext functionContext;
            if (functionExpression.thisMode() == FunctionThisMode.DYNAMIC) {
                final String thisVar = context.allocateGeneratedName("lambdaThis");
                functionBuilder.append("((dev.tsj.runtime.TsjCallableWithThis) (Object ")
                        .append(thisVar)
                        .append(", Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
                functionContext =
                        new EmissionContext(context, thisVar, context.resolveSuperClassExpression(), false, argsVar);
            } else {
                functionBuilder.append("((dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
                functionContext = new EmissionContext(
                        context,
                        context.thisReference,
                        context.superClassExpression,
                        context.constructorContext,
                        argsVar
                );
            }
            emitParameterCells(functionBuilder, functionContext, functionExpression.parameters(), argsVar, "    ");

            if (functionExpression.async()) {
                final List<Statement> normalizedBody =
                        normalizeAsyncStatementsForAwaitExpressions(functionContext, functionExpression.body());
                predeclareFunctionBindings(functionBuilder, functionContext, normalizedBody, "    ");
                predeclareAsyncLocalBindings(functionBuilder, functionContext, normalizedBody, "    ");
                functionBuilder.append("    try {\n");
                emitAsyncStatements(functionBuilder, functionContext, normalizedBody, "        ");
                functionBuilder.append("    } catch (RuntimeException __tsjAsyncError) {\n");
                functionBuilder.append("        return dev.tsj.runtime.TsjRuntime.promiseReject(")
                        .append("dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));\n");
                functionBuilder.append("    }\n");
            } else {
                emitStatements(functionBuilder, functionContext, functionExpression.body(), "    ", true);
                if (!blockAlwaysExits(functionExpression.body())) {
                    functionBuilder.append("    return null;\n");
                }
            }

            functionBuilder.append("})");
            return functionBuilder.toString();
        }

        private String emitGeneratorFunctionExpression(
                final EmissionContext context,
                final FunctionExpression functionExpression
        ) {
            final String argsVar = context.allocateGeneratedName("lambdaArgs");
            final String generatorThisVar = context.allocateGeneratedName("generatorThis");
            final String generatorArgsVar = context.allocateGeneratedName("generatorArgs");
            final StringBuilder functionBuilder = new StringBuilder();

            final String thisReferenceForGenerator;
            if (functionExpression.thisMode() == FunctionThisMode.DYNAMIC) {
                final String thisVar = context.allocateGeneratedName("lambdaThis");
                thisReferenceForGenerator = thisVar;
                functionBuilder.append("((dev.tsj.runtime.TsjCallableWithThis) (Object ")
                        .append(thisVar)
                        .append(", Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
            } else {
                thisReferenceForGenerator = context.thisReference;
                functionBuilder.append("((dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
            }

            functionBuilder.append("    return dev.tsj.runtime.TsjRuntime.createGenerator(")
                    .append("(dev.tsj.runtime.TsjCallableWithThis) (Object ")
                    .append(generatorThisVar)
                    .append(", Object... ")
                    .append(generatorArgsVar)
                    .append(") -> {\n");

            final EmissionContext generatorContext =
                    new EmissionContext(context, generatorThisVar, context.resolveSuperClassExpression(), false, generatorArgsVar);
            emitParameterCells(functionBuilder, generatorContext, functionExpression.parameters(), generatorArgsVar, "        ");
            emitStatements(functionBuilder, generatorContext, functionExpression.body(), "        ", true);
            if (!blockAlwaysExits(functionExpression.body())) {
                functionBuilder.append("        return null;\n");
            }

            functionBuilder.append("    }, ")
                    .append(thisReferenceForGenerator)
                    .append(", ")
                    .append(argsVar)
                    .append(");\n");
            functionBuilder.append("})");
            return functionBuilder.toString();
        }

        private boolean blockAlwaysExits(final List<Statement> statements) {
            if (statements.isEmpty()) {
                return false;
            }
            return statementAlwaysExits(statements.get(statements.size() - 1));
        }

        private boolean statementAlwaysExits(final Statement statement) {
            if (statement instanceof ReturnStatement || statement instanceof ThrowStatement) {
                return true;
            }
            if (statement instanceof IfStatement ifStatement) {
                if (ifStatement.elseBlock().isEmpty()) {
                    return false;
                }
                return blockAlwaysExits(ifStatement.thenBlock()) && blockAlwaysExits(ifStatement.elseBlock());
            }
            if (statement instanceof TryStatement tryStatement) {
                if (blockAlwaysExits(tryStatement.finallyBlock())) {
                    return true;
                }
                final boolean tryExits = blockAlwaysExits(tryStatement.tryBlock());
                if (!tryStatement.hasCatch()) {
                    return tryExits;
                }
                return tryExits && blockAlwaysExits(tryStatement.catchBlock());
            }
            return false;
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
                if (context.parent == null && expressionContainsAwait(whileStatement.condition())) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "`await` in top-level while condition is unsupported in TSJ-13f."
                    );
                }
                final AwaitExpressionRewrite rewrittenCondition =
                        rewriteAsyncExpressionForAwait(context, whileStatement.condition(), sourceLocation);
                final EmissionContext bodyContext = new EmissionContext(context);
                final List<Statement> rewrittenBody =
                        normalizeAsyncStatementsForAwaitExpressions(bodyContext, whileStatement.body());
                final List<Statement> normalizedBody = new ArrayList<>(rewrittenCondition.hoistedStatements());
                final BreakStatement breakStatement = new BreakStatement(null);
                copySourceLocation(statement, breakStatement);
                final IfStatement breakIfStatement = new IfStatement(
                        new UnaryExpression("!", rewrittenCondition.expression()),
                        List.of(breakStatement),
                        List.of()
                );
                copySourceLocation(statement, breakIfStatement);
                normalizedBody.add(breakIfStatement);
                normalizedBody.addAll(rewrittenBody);
                final WhileStatement rewrittenStatement = new WhileStatement(
                        new BooleanLiteral(true),
                        List.copyOf(normalizedBody)
                );
                copySourceLocation(statement, rewrittenStatement);
                return List.of(rewrittenStatement);
            }
            if (statement instanceof TryStatement tryStatement) {
                final EmissionContext tryContext = new EmissionContext(context);
                final List<Statement> rewrittenTry = normalizeAsyncStatementsForAwaitExpressions(
                        tryContext,
                        tryStatement.tryBlock()
                );
                final List<Statement> rewrittenCatch;
                if (tryStatement.hasCatch()) {
                    final EmissionContext catchContext = new EmissionContext(context);
                    rewrittenCatch = normalizeAsyncStatementsForAwaitExpressions(catchContext, tryStatement.catchBlock());
                } else {
                    rewrittenCatch = List.of();
                }
                final EmissionContext finallyContext = new EmissionContext(context);
                final List<Statement> rewrittenFinally = normalizeAsyncStatementsForAwaitExpressions(
                        finallyContext,
                        tryStatement.finallyBlock()
                );
                final TryStatement rewrittenStatement = new TryStatement(
                        rewrittenTry,
                        tryStatement.catchBinding(),
                        rewrittenCatch,
                        rewrittenFinally
                );
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
            if (expression instanceof AssignmentExpression assignmentExpression) {
                final AwaitExpressionRewrite rewrittenTarget =
                        rewriteAsyncExpressionForAwait(context, assignmentExpression.target(), sourceLocation);
                final AwaitExpressionRewrite rewrittenValue =
                        rewriteAsyncExpressionForAwait(context, assignmentExpression.expression(), sourceLocation);
                final List<Statement> hoisted = new ArrayList<>(rewrittenTarget.hoistedStatements());
                hoisted.addAll(rewrittenValue.hoistedStatements());
                return new AwaitExpressionRewrite(
                        new AssignmentExpression(
                                rewrittenTarget.expression(),
                                assignmentExpression.operator(),
                                rewrittenValue.expression()
                        ),
                        List.copyOf(hoisted)
                );
            }
            if (expression instanceof ConditionalExpression conditionalExpression) {
                final AwaitExpressionRewrite rewrittenCondition =
                        rewriteAsyncExpressionForAwait(context, conditionalExpression.condition(), sourceLocation);
                final AwaitExpressionRewrite rewrittenWhenTrue =
                        rewriteAsyncExpressionForAwait(context, conditionalExpression.whenTrue(), sourceLocation);
                final AwaitExpressionRewrite rewrittenWhenFalse =
                        rewriteAsyncExpressionForAwait(context, conditionalExpression.whenFalse(), sourceLocation);
                final List<Statement> hoisted = new ArrayList<>(rewrittenCondition.hoistedStatements());
                hoisted.addAll(rewrittenWhenTrue.hoistedStatements());
                hoisted.addAll(rewrittenWhenFalse.hoistedStatements());
                return new AwaitExpressionRewrite(
                        new ConditionalExpression(
                                rewrittenCondition.expression(),
                                rewrittenWhenTrue.expression(),
                                rewrittenWhenFalse.expression()
                        ),
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
            if (expression instanceof OptionalCallExpression optionalCallExpression) {
                final AwaitExpressionRewrite rewrittenCallee =
                        rewriteAsyncExpressionForAwait(context, optionalCallExpression.callee(), sourceLocation);
                final List<Expression> rewrittenArguments = new ArrayList<>();
                final List<Statement> hoisted = new ArrayList<>(rewrittenCallee.hoistedStatements());
                for (Expression argument : optionalCallExpression.arguments()) {
                    final AwaitExpressionRewrite rewrittenArgument =
                            rewriteAsyncExpressionForAwait(context, argument, sourceLocation);
                    hoisted.addAll(rewrittenArgument.hoistedStatements());
                    rewrittenArguments.add(rewrittenArgument.expression());
                }
                return new AwaitExpressionRewrite(
                        new OptionalCallExpression(rewrittenCallee.expression(), List.copyOf(rewrittenArguments)),
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
            if (expression instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                final AwaitExpressionRewrite rewrittenReceiver =
                        rewriteAsyncExpressionForAwait(context, optionalMemberAccessExpression.receiver(), sourceLocation);
                return new AwaitExpressionRewrite(
                        new OptionalMemberAccessExpression(
                                rewrittenReceiver.expression(),
                                optionalMemberAccessExpression.member()
                        ),
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
            if (statement instanceof TryStatement tryStatement) {
                emitAsyncTryStatement(
                        builder,
                        context,
                        statements,
                        index,
                        tryStatement,
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
            if (statement instanceof ThrowStatement
                    || statement instanceof BreakStatement
                    || statement instanceof ContinueStatement) {
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
            final String loopBodyCallable = context.allocateGeneratedName("asyncLoopBody");
            final String loopBodyArgs = context.allocateGeneratedName("asyncLoopBodyArgs");
            final String loopResultArgs = context.allocateGeneratedName("asyncLoopResultArgs");
            final String loopResultValue = context.allocateGeneratedName("asyncLoopResult");
            final String loopBodyCompletionExpression =
                    "dev.tsj.runtime.TsjRuntime.promiseResolve(" + ASYNC_CONTINUE_SIGNAL_FIELD + ")";

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

            builder.append(indent)
                    .append("    final dev.tsj.runtime.TsjCallable ")
                    .append(loopBodyCallable)
                    .append(" = (Object... ")
                    .append(loopBodyArgs)
                    .append(") -> {\n");
            final EmissionContext loopContext = new EmissionContext(context);
            predeclareAsyncLocalBindings(builder, loopContext, whileStatement.body(), indent + "        ");
            emitAsyncStatements(
                    builder,
                    loopContext,
                    whileStatement.body(),
                    indent + "        ",
                    loopBodyCompletionExpression
            );
            builder.append(indent).append("    };\n");

            builder.append(indent)
                    .append("    return dev.tsj.runtime.TsjRuntime.promiseThen(")
                    .append("dev.tsj.runtime.TsjRuntime.call(")
                    .append(loopBodyCallable)
                    .append("), ")
                    .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                    .append(loopResultArgs)
                    .append(") -> {\n");
            builder.append(indent)
                    .append("        final Object ")
                    .append(loopResultValue)
                    .append(" = ")
                    .append(loopResultArgs)
                    .append(".length > 0 ? ")
                    .append(loopResultArgs)
                    .append("[0] : dev.tsj.runtime.TsjRuntime.undefined();\n");
            builder.append(indent)
                    .append("        if (")
                    .append(loopResultValue)
                    .append(" == ")
                    .append(ASYNC_BREAK_SIGNAL_FIELD)
                    .append(") {\n");
            emitAsyncStatementsFrom(
                    builder,
                    context,
                    statements,
                    index + 1,
                    indent + "            ",
                    completionExpression
            );
            builder.append(indent).append("        }\n");
            builder.append(indent)
                    .append("        if (")
                    .append(loopResultValue)
                    .append(" == ")
                    .append(ASYNC_CONTINUE_SIGNAL_FIELD)
                    .append(") {\n");
            builder.append(indent)
                    .append("            return ")
                    .append(loopCallExpression)
                    .append(";\n");
            builder.append(indent).append("        }\n");
            builder.append(indent)
                    .append("        return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                    .append(loopResultValue)
                    .append(");\n");
            builder.append(indent)
                    .append("    }, dev.tsj.runtime.TsjRuntime.undefined());\n");

            builder.append(indent).append("});\n");
            builder.append(indent).append("return ").append(loopCallExpression).append(";\n");
        }

        private void emitAsyncTryStatement(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final int index,
                final TryStatement tryStatement,
                final String indent,
                final String completionExpression
        ) {
            final String tryFallthrough = context.allocateGeneratedName("asyncTryFallthrough");
            final String blockCompletionExpression =
                    "dev.tsj.runtime.TsjRuntime.promiseResolve(" + tryFallthrough + ")";
            final String chainedPromise = context.allocateGeneratedName("asyncTryPromise");

            builder.append(indent)
                    .append("final Object ")
                    .append(tryFallthrough)
                    .append(" = new Object();\n");
            builder.append(indent)
                    .append("Object ")
                    .append(chainedPromise)
                    .append(";\n");
            emitAsyncBlockInvocation(
                    builder,
                    context,
                    tryStatement.tryBlock(),
                    indent,
                    blockCompletionExpression,
                    chainedPromise,
                    "asyncTry"
            );

            if (tryStatement.hasCatch()) {
                final String catchArgs = context.allocateGeneratedName("asyncCatchArgs");
                final String catchReason = context.allocateGeneratedName("asyncCatchReason");
                builder.append(indent)
                        .append(chainedPromise)
                        .append(" = dev.tsj.runtime.TsjRuntime.promiseThen(")
                        .append("dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(chainedPromise)
                        .append("), dev.tsj.runtime.TsjRuntime.undefined(), ")
                        .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(catchArgs)
                        .append(") -> {\n");
                builder.append(indent)
                        .append("    final Object ")
                        .append(catchReason)
                        .append(" = ")
                        .append(catchArgs)
                        .append(".length > 0 ? ")
                        .append(catchArgs)
                        .append("[0] : dev.tsj.runtime.TsjRuntime.undefined();\n");
                final EmissionContext catchContext = new EmissionContext(context);
                if (tryStatement.catchBinding() != null) {
                    final String catchCellName = catchContext.declareBinding(tryStatement.catchBinding());
                    builder.append(indent)
                            .append("    final dev.tsj.runtime.TsjCell ")
                            .append(catchCellName)
                            .append(" = new dev.tsj.runtime.TsjCell(")
                            .append(catchReason)
                            .append(");\n");
                }
                predeclareAsyncLocalBindings(builder, catchContext, tryStatement.catchBlock(), indent + "    ");
                emitAsyncStatements(
                        builder,
                        catchContext,
                        tryStatement.catchBlock(),
                        indent + "    ",
                        blockCompletionExpression
                );
                builder.append(indent).append("});\n");
            }

            if (tryStatement.hasFinally()) {
                final String onFulfilledArgs = context.allocateGeneratedName("asyncFinallyFulfilledArgs");
                final String onRejectedArgs = context.allocateGeneratedName("asyncFinallyRejectedArgs");
                final String priorSuccessValue = context.allocateGeneratedName("asyncFinallyPriorValue");
                final String priorErrorValue = context.allocateGeneratedName("asyncFinallyPriorError");
                final String finallySuccessPromise = context.allocateGeneratedName("asyncFinallySuccessPromise");
                final String finallyRejectedPromise = context.allocateGeneratedName("asyncFinallyRejectedPromise");
                final String finallySuccessResultArgs = context.allocateGeneratedName("asyncFinallySuccessResultArgs");
                final String finallySuccessResultValue = context.allocateGeneratedName("asyncFinallySuccessResult");
                final String finallyRejectedResultArgs = context.allocateGeneratedName("asyncFinallyRejectedResultArgs");
                final String finallyRejectedResultValue = context.allocateGeneratedName("asyncFinallyRejectedResult");

                builder.append(indent)
                        .append(chainedPromise)
                        .append(" = dev.tsj.runtime.TsjRuntime.promiseThen(")
                        .append("dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(chainedPromise)
                        .append("), ")
                        .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(onFulfilledArgs)
                        .append(") -> {\n");
                builder.append(indent)
                        .append("    final Object ")
                        .append(priorSuccessValue)
                        .append(" = ")
                        .append(onFulfilledArgs)
                        .append(".length > 0 ? ")
                        .append(onFulfilledArgs)
                        .append("[0] : dev.tsj.runtime.TsjRuntime.undefined();\n");
                builder.append(indent)
                        .append("    Object ")
                        .append(finallySuccessPromise)
                        .append(";\n");
                emitAsyncBlockInvocation(
                        builder,
                        context,
                        tryStatement.finallyBlock(),
                        indent + "    ",
                        blockCompletionExpression,
                        finallySuccessPromise,
                        "asyncFinallySuccess"
                );
                builder.append(indent)
                        .append("    return dev.tsj.runtime.TsjRuntime.promiseThen(")
                        .append("dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(finallySuccessPromise)
                        .append("), ")
                        .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(finallySuccessResultArgs)
                        .append(") -> {\n");
                builder.append(indent)
                        .append("        final Object ")
                        .append(finallySuccessResultValue)
                        .append(" = ")
                        .append(finallySuccessResultArgs)
                        .append(".length > 0 ? ")
                        .append(finallySuccessResultArgs)
                        .append("[0] : dev.tsj.runtime.TsjRuntime.undefined();\n");
                builder.append(indent)
                        .append("        if (")
                        .append(finallySuccessResultValue)
                        .append(" == ")
                        .append(tryFallthrough)
                        .append(") {\n");
                builder.append(indent)
                        .append("            return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(priorSuccessValue)
                        .append(");\n");
                builder.append(indent).append("        }\n");
                builder.append(indent)
                        .append("        return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(finallySuccessResultValue)
                        .append(");\n");
                builder.append(indent)
                        .append("    }, dev.tsj.runtime.TsjRuntime.undefined());\n");
                builder.append(indent)
                        .append("}, (dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(onRejectedArgs)
                        .append(") -> {\n");
                builder.append(indent)
                        .append("    final Object ")
                        .append(priorErrorValue)
                        .append(" = ")
                        .append(onRejectedArgs)
                        .append(".length > 0 ? ")
                        .append(onRejectedArgs)
                        .append("[0] : dev.tsj.runtime.TsjRuntime.undefined();\n");
                builder.append(indent)
                        .append("    Object ")
                        .append(finallyRejectedPromise)
                        .append(";\n");
                emitAsyncBlockInvocation(
                        builder,
                        context,
                        tryStatement.finallyBlock(),
                        indent + "    ",
                        blockCompletionExpression,
                        finallyRejectedPromise,
                        "asyncFinallyRejected"
                );
                builder.append(indent)
                        .append("    return dev.tsj.runtime.TsjRuntime.promiseThen(")
                        .append("dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(finallyRejectedPromise)
                        .append("), ")
                        .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(finallyRejectedResultArgs)
                        .append(") -> {\n");
                builder.append(indent)
                        .append("        final Object ")
                        .append(finallyRejectedResultValue)
                        .append(" = ")
                        .append(finallyRejectedResultArgs)
                        .append(".length > 0 ? ")
                        .append(finallyRejectedResultArgs)
                        .append("[0] : dev.tsj.runtime.TsjRuntime.undefined();\n");
                builder.append(indent)
                        .append("        if (")
                        .append(finallyRejectedResultValue)
                        .append(" == ")
                        .append(tryFallthrough)
                        .append(") {\n");
                builder.append(indent)
                        .append("            return dev.tsj.runtime.TsjRuntime.promiseReject(")
                        .append(priorErrorValue)
                        .append(");\n");
                builder.append(indent).append("        }\n");
                builder.append(indent)
                        .append("        return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(finallyRejectedResultValue)
                        .append(");\n");
                builder.append(indent)
                        .append("    }, dev.tsj.runtime.TsjRuntime.undefined());\n");
                builder.append(indent).append("});\n");
            }

            final String tryResultArgs = context.allocateGeneratedName("asyncTryResultArgs");
            final String tryResultValue = context.allocateGeneratedName("asyncTryResult");
            builder.append(indent)
                    .append("return dev.tsj.runtime.TsjRuntime.promiseThen(")
                    .append("dev.tsj.runtime.TsjRuntime.promiseResolve(")
                    .append(chainedPromise)
                    .append("), ")
                    .append("(dev.tsj.runtime.TsjCallable) (Object... ")
                    .append(tryResultArgs)
                    .append(") -> {\n");
            builder.append(indent)
                    .append("    final Object ")
                    .append(tryResultValue)
                    .append(" = ")
                    .append(tryResultArgs)
                    .append(".length > 0 ? ")
                    .append(tryResultArgs)
                    .append("[0] : dev.tsj.runtime.TsjRuntime.undefined();\n");
            builder.append(indent)
                    .append("    if (")
                    .append(tryResultValue)
                    .append(" == ")
                    .append(tryFallthrough)
                    .append(") {\n");
            emitAsyncStatementsFrom(
                    builder,
                    context,
                    statements,
                    index + 1,
                    indent + "        ",
                    completionExpression
            );
            builder.append(indent).append("    }\n");
            builder.append(indent)
                    .append("    return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                    .append(tryResultValue)
                    .append(");\n");
            builder.append(indent)
                    .append("}, dev.tsj.runtime.TsjRuntime.undefined());\n");
        }

        private void emitAsyncBlockInvocation(
                final StringBuilder builder,
                final EmissionContext context,
                final List<Statement> statements,
                final String indent,
                final String completionExpression,
                final String promiseVariable,
                final String generatedPrefix
        ) {
            final String callable = context.allocateGeneratedName(generatedPrefix + "Callable");
            final String callableArgs = context.allocateGeneratedName(generatedPrefix + "Args");
            builder.append(indent)
                    .append("final dev.tsj.runtime.TsjCallable ")
                    .append(callable)
                    .append(" = (Object... ")
                    .append(callableArgs)
                    .append(") -> {\n");
            final EmissionContext blockContext = new EmissionContext(context);
            predeclareAsyncLocalBindings(builder, blockContext, statements, indent + "    ");
            emitAsyncStatements(builder, blockContext, statements, indent + "    ", completionExpression);
            builder.append(indent).append("};\n");
            builder.append(indent).append("try {\n");
            builder.append(indent)
                    .append("    ")
                    .append(promiseVariable)
                    .append(" = dev.tsj.runtime.TsjRuntime.call(")
                    .append(callable)
                    .append(");\n");
            builder.append(indent).append("} catch (RuntimeException __tsjAsyncBlockError) {\n");
            builder.append(indent)
                    .append("    ")
                    .append(promiseVariable)
                    .append(" = dev.tsj.runtime.TsjRuntime.promiseReject(")
                    .append("dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncBlockError));\n");
            builder.append(indent).append("}\n");
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
                        if (isPrototypeMutationMemberAccess(memberAccessExpression)) {
                            builder.append(indent)
                                    .append("dev.tsj.runtime.TsjRuntime.setPrototype(")
                                    .append(emitExpression(context, memberAccessExpression.receiver()))
                                    .append(", ")
                                    .append(awaitValueExpression)
                                    .append(");\n");
                        } else {
                            builder.append(indent)
                                    .append("dev.tsj.runtime.TsjRuntime.setProperty(")
                                    .append(emitExpression(context, memberAccessExpression.receiver()))
                                    .append(", \"")
                                    .append(escapeJava(memberAccessExpression.member()))
                                    .append("\", ")
                                    .append(awaitValueExpression)
                                    .append(");\n");
                        }
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
            if (statement instanceof BreakStatement breakStatement) {
                if (breakStatement.label() != null) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Labeled `break` is unsupported inside async-lowered control flow in TSJ-13 subset."
                    );
                }
                builder.append(indent)
                        .append("return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(ASYNC_BREAK_SIGNAL_FIELD)
                        .append(");\n");
                return;
            }
            if (statement instanceof ContinueStatement continueStatement) {
                if (continueStatement.label() != null) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Labeled `continue` is unsupported inside async-lowered control flow in TSJ-13 subset."
                    );
                }
                builder.append(indent)
                        .append("return dev.tsj.runtime.TsjRuntime.promiseResolve(")
                        .append(ASYNC_CONTINUE_SIGNAL_FIELD)
                        .append(");\n");
                return;
            }
            if (statement instanceof LabeledStatement) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Labeled statements are unsupported inside async-lowered control flow in TSJ-13 subset."
                );
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
            if (statement instanceof TryStatement tryStatement) {
                for (Statement nested : tryStatement.tryBlock()) {
                    if (statementContainsAwait(nested)) {
                        return true;
                    }
                }
                for (Statement nested : tryStatement.catchBlock()) {
                    if (statementContainsAwait(nested)) {
                        return true;
                    }
                }
                for (Statement nested : tryStatement.finallyBlock()) {
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
            if (expression instanceof AssignmentExpression assignmentExpression) {
                return expressionContainsAwait(assignmentExpression.target())
                        || expressionContainsAwait(assignmentExpression.expression());
            }
            if (expression instanceof ConditionalExpression conditionalExpression) {
                return expressionContainsAwait(conditionalExpression.condition())
                        || expressionContainsAwait(conditionalExpression.whenTrue())
                        || expressionContainsAwait(conditionalExpression.whenFalse());
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
            if (expression instanceof OptionalCallExpression optionalCallExpression) {
                if (expressionContainsAwait(optionalCallExpression.callee())) {
                    return true;
                }
                for (Expression argument : optionalCallExpression.arguments()) {
                    if (expressionContainsAwait(argument)) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof MemberAccessExpression memberAccessExpression) {
                return expressionContainsAwait(memberAccessExpression.receiver());
            }
            if (expression instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                return expressionContainsAwait(optionalMemberAccessExpression.receiver());
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
                if (isPrototypeMutationMemberAccess(memberAccessExpression)) {
                    builder.append(indent)
                            .append("dev.tsj.runtime.TsjRuntime.setPrototype(")
                            .append(emitExpression(context, memberAccessExpression.receiver()))
                            .append(", ")
                            .append(valueExpression)
                            .append(");\n");
                } else {
                    builder.append(indent)
                            .append("dev.tsj.runtime.TsjRuntime.setProperty(")
                            .append(emitExpression(context, memberAccessExpression.receiver()))
                            .append(", \"")
                            .append(escapeJava(memberAccessExpression.member()))
                            .append("\", ")
                            .append(valueExpression)
                            .append(");\n");
                }
                return;
            }
            if (assignment.target() instanceof CallExpression callExpression
                    && isIndexReadFactoryCall(callExpression)) {
                if (callExpression.arguments().size() != 2) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "__tsj_index_read assignment target requires receiver and key arguments."
                    );
                }
                builder.append(indent)
                        .append("dev.tsj.runtime.TsjRuntime.setPropertyDynamic(")
                        .append(emitExpression(context, callExpression.arguments().get(0)))
                        .append(", ")
                        .append(emitExpression(context, callExpression.arguments().get(1)))
                        .append(", ")
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

        private String emitAssignmentExpression(
                final EmissionContext context,
                final AssignmentExpression assignmentExpression
        ) {
            final String operator = assignmentExpression.operator();
            final String valueExpression = emitExpression(context, assignmentExpression.expression());
            final Expression target = assignmentExpression.target();
            if (target instanceof VariableExpression variableExpression) {
                return emitVariableAssignmentExpression(
                        context.resolveBinding(variableExpression.name()),
                        operator,
                        valueExpression
                );
            }
            if (target instanceof MemberAccessExpression memberAccessExpression) {
                final String receiverExpression = emitExpression(context, memberAccessExpression.receiver());
                final String memberName = escapeJava(memberAccessExpression.member());
                if (isPrototypeMutationMemberAccess(memberAccessExpression)) {
                    if (!"=".equals(operator)) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Compound `__proto__` assignment is unsupported in TSJ assignment subset."
                        );
                    }
                    return "dev.tsj.runtime.TsjRuntime.setPrototypeValue("
                            + receiverExpression
                            + ", "
                            + valueExpression
                            + ")";
                }
                return emitMemberAssignmentExpression(receiverExpression, memberName, operator, valueExpression);
            }
            if (target instanceof CallExpression callExpression && isIndexReadFactoryCall(callExpression)) {
                if (callExpression.arguments().size() != 2) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "__tsj_index_read assignment target requires receiver and key arguments."
                    );
                }
                final String receiverExpression = emitExpression(context, callExpression.arguments().get(0));
                final String keyExpression = emitExpression(context, callExpression.arguments().get(1));
                return emitDynamicIndexAssignmentExpression(receiverExpression, keyExpression, operator, valueExpression);
            }
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Unsupported assignment target in TSJ assignment expression subset: "
                            + target.getClass().getSimpleName()
            );
        }

        private String emitVariableAssignmentExpression(
                final String cellName,
                final String operator,
                final String valueExpression
        ) {
            final String currentValue = cellName + ".get()";
            final String compoundOperator = compoundBinaryOperator(operator);
            if (compoundOperator != null) {
                return "dev.tsj.runtime.TsjRuntime.assignCell("
                        + cellName
                        + ", "
                        + emitBinaryOperatorExpression(compoundOperator, currentValue, valueExpression)
                        + ")";
            }
            return switch (operator) {
                case "=" -> "dev.tsj.runtime.TsjRuntime.assignCell(" + cellName + ", " + valueExpression + ")";
                case "&&=" -> "dev.tsj.runtime.TsjRuntime.assignLogicalAnd(" + cellName + ", () -> " + valueExpression + ")";
                case "||=" -> "dev.tsj.runtime.TsjRuntime.assignLogicalOr(" + cellName + ", () -> " + valueExpression + ")";
                case "??=" -> "dev.tsj.runtime.TsjRuntime.assignNullish(" + cellName + ", () -> " + valueExpression + ")";
                default -> throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported assignment operator: " + operator
                );
            };
        }

        private String emitMemberAssignmentExpression(
                final String receiverExpression,
                final String memberName,
                final String operator,
                final String valueExpression
        ) {
            final String memberLiteral = "\"" + memberName + "\"";
            final String currentValue =
                    "dev.tsj.runtime.TsjRuntime.getProperty(" + receiverExpression + ", " + memberLiteral + ")";
            final String compoundOperator = compoundBinaryOperator(operator);
            if (compoundOperator != null) {
                return "dev.tsj.runtime.TsjRuntime.setProperty("
                        + receiverExpression
                        + ", "
                        + memberLiteral
                        + ", "
                        + emitBinaryOperatorExpression(compoundOperator, currentValue, valueExpression)
                        + ")";
            }
            return switch (operator) {
                case "=" -> "dev.tsj.runtime.TsjRuntime.setProperty("
                        + receiverExpression
                        + ", "
                        + memberLiteral
                        + ", "
                        + valueExpression
                        + ")";
                case "&&=" -> "dev.tsj.runtime.TsjRuntime.assignPropertyLogicalAnd("
                        + receiverExpression
                        + ", "
                        + memberLiteral
                        + ", () -> "
                        + valueExpression
                        + ")";
                case "||=" -> "dev.tsj.runtime.TsjRuntime.assignPropertyLogicalOr("
                        + receiverExpression
                        + ", "
                        + memberLiteral
                        + ", () -> "
                        + valueExpression
                        + ")";
                case "??=" -> "dev.tsj.runtime.TsjRuntime.assignPropertyNullish("
                        + receiverExpression
                        + ", "
                        + memberLiteral
                        + ", () -> "
                        + valueExpression
                        + ")";
                default -> throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported assignment operator: " + operator
                );
            };
        }

        private String emitDynamicIndexAssignmentExpression(
                final String receiverExpression,
                final String keyExpression,
                final String operator,
                final String valueExpression
        ) {
            final String currentValue =
                    "dev.tsj.runtime.TsjRuntime.indexRead(" + receiverExpression + ", " + keyExpression + ")";
            final String compoundOperator = compoundBinaryOperator(operator);
            if (compoundOperator != null) {
                return "dev.tsj.runtime.TsjRuntime.setPropertyDynamic("
                        + receiverExpression
                        + ", "
                        + keyExpression
                        + ", "
                        + emitBinaryOperatorExpression(compoundOperator, currentValue, valueExpression)
                        + ")";
            }
            return switch (operator) {
                case "=" -> "dev.tsj.runtime.TsjRuntime.setPropertyDynamic("
                        + receiverExpression
                        + ", "
                        + keyExpression
                        + ", "
                        + valueExpression
                        + ")";
                case "&&=" -> "dev.tsj.runtime.TsjRuntime.assignPropertyDynamicLogicalAnd("
                        + receiverExpression
                        + ", "
                        + keyExpression
                        + ", () -> "
                        + valueExpression
                        + ")";
                case "||=" -> "dev.tsj.runtime.TsjRuntime.assignPropertyDynamicLogicalOr("
                        + receiverExpression
                        + ", "
                        + keyExpression
                        + ", () -> "
                        + valueExpression
                        + ")";
                case "??=" -> "dev.tsj.runtime.TsjRuntime.assignPropertyDynamicNullish("
                        + receiverExpression
                        + ", "
                        + keyExpression
                        + ", () -> "
                        + valueExpression
                        + ")";
                default -> throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported assignment operator: " + operator
                );
            };
        }

        private String compoundBinaryOperator(final String assignmentOperator) {
            return switch (assignmentOperator) {
                case "+=" -> "+";
                case "-=" -> "-";
                case "*=" -> "*";
                case "/=" -> "/";
                case "%=" -> "%";
                case "**=" -> "**";
                case "&=" -> "&";
                case "|=" -> "|";
                case "^=" -> "^";
                case "<<=" -> "<<";
                case ">>=" -> ">>";
                case ">>>=" -> ">>>";
                default -> null;
            };
        }

        private String emitTypeofOperandExpression(
                final EmissionContext context,
                final Expression expression
        ) {
            if (!(expression instanceof VariableExpression variableExpression)) {
                return emitExpression(context, expression);
            }
            try {
                return context.resolveBinding(variableExpression.name()) + ".get()";
            } catch (final JvmCompilationException exception) {
                if ("TSJ-BACKEND-UNSUPPORTED".equals(exception.code())) {
                    return "dev.tsj.runtime.TsjRuntime.undefined()";
                }
                throw exception;
            }
        }

        private String emitBinaryOperatorExpression(
                final String operator,
                final String left,
                final String right
        ) {
            return switch (operator) {
                case "+" -> "dev.tsj.runtime.TsjRuntime.add(" + left + ", " + right + ")";
                case "-" -> "dev.tsj.runtime.TsjRuntime.subtract(" + left + ", " + right + ")";
                case "*" -> "dev.tsj.runtime.TsjRuntime.multiply(" + left + ", " + right + ")";
                case "/" -> "dev.tsj.runtime.TsjRuntime.divide(" + left + ", " + right + ")";
                case "%" -> "dev.tsj.runtime.TsjRuntime.modulo(" + left + ", " + right + ")";
                case "**" -> "dev.tsj.runtime.TsjRuntime.power(" + left + ", " + right + ")";
                case "&" -> "dev.tsj.runtime.TsjRuntime.bitwiseAnd(" + left + ", " + right + ")";
                case "|" -> "dev.tsj.runtime.TsjRuntime.bitwiseOr(" + left + ", " + right + ")";
                case "^" -> "dev.tsj.runtime.TsjRuntime.bitwiseXor(" + left + ", " + right + ")";
                case "<<" -> "dev.tsj.runtime.TsjRuntime.shiftLeft(" + left + ", " + right + ")";
                case ">>" -> "dev.tsj.runtime.TsjRuntime.shiftRight(" + left + ", " + right + ")";
                case ">>>" -> "dev.tsj.runtime.TsjRuntime.shiftRightUnsigned(" + left + ", " + right + ")";
                default -> throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported binary operator in assignment lowering: " + operator
                );
            };
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
                return emitNumberLiteral(numberLiteral.value());
            }
            if (expression instanceof StringLiteral stringLiteral) {
                return "\"" + escapeJava(stringLiteral.value()) + "\"";
            }
            if (expression instanceof BooleanLiteral booleanLiteral) {
                return booleanLiteral.value() ? "Boolean.TRUE" : "Boolean.FALSE";
            }
            if (expression instanceof NullLiteral) {
                return "((Object) null)";
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
                if ("+".equals(unaryExpression.operator())) {
                    return "dev.tsj.runtime.TsjRuntime.unaryPlus("
                            + emitExpression(context, unaryExpression.expression())
                            + ")";
                }
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
                if ("delete".equals(unaryExpression.operator())) {
                    if (unaryExpression.expression() instanceof MemberAccessExpression memberAccessExpression) {
                        return "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.deleteProperty("
                                + emitExpression(context, memberAccessExpression.receiver())
                                + ", \""
                                + escapeJava(memberAccessExpression.member())
                                + "\"))";
                    }
                    if (unaryExpression.expression() instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                        return "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.optionalDeleteProperty("
                                + emitExpression(context, optionalMemberAccessExpression.receiver())
                                + ", \""
                                + escapeJava(optionalMemberAccessExpression.member())
                                + "\"))";
                    }
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "`delete` supports only member access targets in TSJ-21 subset."
                    );
                }
                if ("~".equals(unaryExpression.operator())) {
                    return "dev.tsj.runtime.TsjRuntime.bitwiseNot("
                            + emitExpression(context, unaryExpression.expression())
                            + ")";
                }
                if ("typeof".equals(unaryExpression.operator())) {
                    return "dev.tsj.runtime.TsjRuntime.typeOf("
                            + emitTypeofOperandExpression(context, unaryExpression.expression())
                            + ")";
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unsupported unary operator: " + unaryExpression.operator()
                );
            }
            if (expression instanceof FunctionExpression functionExpression) {
                return emitFunctionExpression(context, functionExpression);
            }
            if (expression instanceof YieldExpression yieldExpression) {
                if (yieldExpression.delegate()) {
                    return "dev.tsj.runtime.TsjRuntime.generatorYieldStar("
                            + emitExpression(context, yieldExpression.expression())
                            + ")";
                }
                return "dev.tsj.runtime.TsjRuntime.generatorYield("
                        + emitExpression(context, yieldExpression.expression())
                        + ")";
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
                    case "+", "-", "*", "/", "%", "**", "&", "|", "^", "<<", ">>", ">>>"
                            -> emitBinaryOperatorExpression(binaryExpression.operator(), left, right);
                    case "," -> "dev.tsj.runtime.TsjRuntime.comma(" + left + ", " + right + ")";
                    case "<" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(" + left + ", " + right + "))";
                    case "<=" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThanOrEqual("
                            + left + ", " + right + "))";
                    case ">" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.greaterThan(" + left + ", " + right + "))";
                    case ">=" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.greaterThanOrEqual("
                            + left + ", " + right + "))";
                    case "in" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.inOperator(" + left + ", " + right + "))";
                    case "instanceof" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.instanceOf("
                            + left + ", " + right + "))";
                    case "==" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.abstractEquals("
                            + left + ", " + right + "))";
                    case "===" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals("
                            + left + ", " + right + "))";
                    case "!=" -> "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.abstractEquals("
                            + left + ", " + right + "))";
                    case "!==" -> "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.strictEquals("
                            + left + ", " + right + "))";
                    case "&&" -> "dev.tsj.runtime.TsjRuntime.logicalAnd("
                            + left
                            + ", () -> "
                            + right
                            + ")";
                    case "||" -> "dev.tsj.runtime.TsjRuntime.logicalOr("
                            + left
                            + ", () -> "
                            + right
                            + ")";
                    case "??" -> "dev.tsj.runtime.TsjRuntime.nullishCoalesce("
                            + left
                            + ", () -> "
                            + right
                            + ")";
                    default -> throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Unsupported binary operator: " + binaryExpression.operator()
                    );
                };
            }
            if (expression instanceof AssignmentExpression assignmentExpression) {
                return emitAssignmentExpression(context, assignmentExpression);
            }
            if (expression instanceof ConditionalExpression conditionalExpression) {
                final String condition = emitExpression(context, conditionalExpression.condition());
                final String whenTrue = emitExpression(context, conditionalExpression.whenTrue());
                final String whenFalse = emitExpression(context, conditionalExpression.whenFalse());
                return "(dev.tsj.runtime.TsjRuntime.truthy("
                        + condition
                        + ") ? "
                        + whenTrue
                        + " : "
                        + whenFalse
                        + ")";
            }
            if (expression instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                return "dev.tsj.runtime.TsjRuntime.optionalMemberAccess("
                        + emitExpression(context, optionalMemberAccessExpression.receiver())
                        + ", \""
                        + escapeJava(optionalMemberAccessExpression.member())
                        + "\")";
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
                if (isJavaInteropFactoryCall(callExpression)) {
                    return emitJavaInteropFactoryCall(callExpression);
                }
                if (isArraySpreadFactoryCall(callExpression)) {
                    return emitSpreadRuntimeCall(context, "arraySpread", callExpression.arguments());
                }
                if (isObjectSpreadFactoryCall(callExpression)) {
                    return emitSpreadRuntimeCall(context, "objectSpread", callExpression.arguments());
                }
                if (isRestArgsFactoryCall(callExpression)) {
                    return emitRestArgsRuntimeCall(context, callExpression);
                }
                if (isArrayRestFactoryCall(callExpression)) {
                    return emitArrayRestRuntimeCall(context, callExpression);
                }
                if (isForOfValuesFactoryCall(callExpression)) {
                    return emitSingleArgumentRuntimeCall(context, callExpression, "forOfValues", "__tsj_for_of_values");
                }
                if (isForInKeysFactoryCall(callExpression)) {
                    return emitSingleArgumentRuntimeCall(context, callExpression, "forInKeys", "__tsj_for_in_keys");
                }
                if (isIndexReadFactoryCall(callExpression)) {
                    return emitIndexReadRuntimeCall(context, callExpression);
                }
                if (isOptionalIndexReadFactoryCall(callExpression)) {
                    return emitOptionalIndexReadRuntimeCall(context, callExpression);
                }
                if (isSuperInvokeFactoryCall(callExpression)) {
                    return emitSuperInvokeRuntimeCall(context, callExpression);
                }
                if (isSetDynamicKeyFactoryCall(callExpression)) {
                    return emitSetDynamicKeyRuntimeCall(context, callExpression);
                }
                if (isDefineAccessorFactoryCall(callExpression)) {
                    return emitDefineAccessorRuntimeCall(context, callExpression);
                }
                if (isCallSpreadFactoryCall(callExpression)) {
                    if (callExpression.arguments().isEmpty()) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "__tsj_call_spread requires at least one argument in TSJ spread subset."
                        );
                    }
                    final String calleeExpression = emitExpression(context, callExpression.arguments().get(0));
                    if (callExpression.arguments().size() == 1) {
                        return "dev.tsj.runtime.TsjRuntime.call(" + calleeExpression + ")";
                    }
                    final List<String> segmentExpressions = new ArrayList<>();
                    for (int index = 1; index < callExpression.arguments().size(); index++) {
                        segmentExpressions.add(emitExpression(context, callExpression.arguments().get(index)));
                    }
                    return "dev.tsj.runtime.TsjRuntime.callSpread("
                            + calleeExpression
                            + ", "
                            + String.join(", ", segmentExpressions)
                            + ")";
                }
                if (callExpression.callee() instanceof MemberAccessExpression memberAccessExpression
                        && isObjectSetPrototypeOfCall(memberAccessExpression)) {
                    if (callExpression.arguments().size() != 2) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Object.setPrototypeOf requires exactly 2 arguments in TSJ-21 subset."
                        );
                    }
                    return "dev.tsj.runtime.TsjRuntime.setPrototype("
                            + emitExpression(context, callExpression.arguments().get(0))
                            + ", "
                            + emitExpression(context, callExpression.arguments().get(1))
                            + ")";
                }
                final List<String> renderedArgs = new ArrayList<>();
                for (Expression argument : callExpression.arguments()) {
                    renderedArgs.add(emitExpression(context, argument));
                }
                if (callExpression.callee() instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                    final String receiver = emitExpression(context, optionalMemberAccessExpression.receiver());
                    final String methodName = "\"" + escapeJava(optionalMemberAccessExpression.member()) + "\"";
                    final String argsSupplier = renderedArgs.isEmpty()
                            ? "() -> new Object[0]"
                            : "() -> new Object[]{" + String.join(", ", renderedArgs) + "}";
                    return "dev.tsj.runtime.TsjRuntime.optionalInvokeMember("
                            + receiver
                            + ", "
                            + methodName
                            + ", "
                            + argsSupplier
                            + ")";
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
            if (expression instanceof OptionalCallExpression optionalCallExpression) {
                final String callee = emitExpression(context, optionalCallExpression.callee());
                final List<String> renderedArgs = new ArrayList<>();
                for (Expression argument : optionalCallExpression.arguments()) {
                    renderedArgs.add(emitExpression(context, argument));
                }
                final String argsSupplier;
                if (renderedArgs.isEmpty()) {
                    argsSupplier = "() -> new Object[0]";
                } else {
                    argsSupplier = "() -> new Object[]{" + String.join(", ", renderedArgs) + "}";
                }
                return "dev.tsj.runtime.TsjRuntime.optionalCall(" + callee + ", " + argsSupplier + ")";
            }
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "Unsupported expression node: " + expression.getClass().getSimpleName()
            );
        }

        private String emitNumberLiteral(final String normalizedLiteral) {
            if (normalizedLiteral.endsWith("n") || normalizedLiteral.endsWith("N")) {
                final String decimalText = normalizedLiteral.substring(0, normalizedLiteral.length() - 1);
                return "dev.tsj.runtime.TsjRuntime.bigIntLiteral(\"" + escapeJava(decimalText) + "\")";
            }
            if (normalizedLiteral.contains(".")
                    || normalizedLiteral.contains("e")
                    || normalizedLiteral.contains("E")) {
                return "Double.valueOf(" + normalizedLiteral + "d)";
            }
            try {
                final BigInteger integerValue = new BigInteger(normalizedLiteral);
                if (integerValue.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                        && integerValue.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                    return "Integer.valueOf(" + integerValue + ")";
                }
                if (integerValue.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                        && integerValue.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                    return "Long.valueOf(" + integerValue + "L)";
                }
                final String roundedDouble = Double.toString(new BigDecimal(integerValue).doubleValue());
                return "Double.valueOf(" + roundedDouble + "d)";
            } catch (final NumberFormatException exception) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-PARSE",
                        "Invalid normalized numeric literal `" + normalizedLiteral + "`.",
                        null,
                        null,
                        exception
                );
            }
        }

        private boolean isPrototypeMutationMemberAccess(final MemberAccessExpression memberAccessExpression) {
            return "__proto__".equals(memberAccessExpression.member());
        }

        private boolean isObjectSetPrototypeOfCall(final MemberAccessExpression memberAccessExpression) {
            if (!"setPrototypeOf".equals(memberAccessExpression.member())) {
                return false;
            }
            if (!(memberAccessExpression.receiver() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "Object".equals(variableExpression.name());
        }

        private boolean isJavaInteropFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            if (!"__tsj_java_binding".equals(variableExpression.name())
                    && !"__tsj_java_static_method".equals(variableExpression.name())) {
                return false;
            }
            if (callExpression.arguments().size() != 2) {
                return false;
            }
            return callExpression.arguments().get(0) instanceof StringLiteral
                    && callExpression.arguments().get(1) instanceof StringLiteral;
        }

        private String emitJavaInteropFactoryCall(final CallExpression callExpression) {
            final StringLiteral className = (StringLiteral) callExpression.arguments().get(0);
            final StringLiteral methodName = (StringLiteral) callExpression.arguments().get(1);
            return "dev.tsj.runtime.TsjRuntime.javaBinding(\""
                    + escapeJava(className.value())
                    + "\", \""
                    + escapeJava(methodName.value())
                    + "\")";
        }

        private String emitSpreadRuntimeCall(
                final EmissionContext context,
                final String methodName,
                final List<Expression> arguments
        ) {
            if (arguments.isEmpty()) {
                return "dev.tsj.runtime.TsjRuntime." + methodName + "()";
            }
            final List<String> renderedArgs = new ArrayList<>();
            for (Expression argument : arguments) {
                renderedArgs.add(emitExpression(context, argument));
            }
            return "dev.tsj.runtime.TsjRuntime."
                    + methodName
                    + "("
                    + String.join(", ", renderedArgs)
                    + ")";
        }

        private String emitSingleArgumentRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression,
                final String runtimeMethod,
                final String helperName
        ) {
            if (callExpression.arguments().size() != 1) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        helperName + " requires exactly one argument."
                );
            }
            return "dev.tsj.runtime.TsjRuntime."
                    + runtimeMethod
                    + "("
                    + emitExpression(context, callExpression.arguments().get(0))
                    + ")";
        }

        private String emitIndexReadRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().size() != 2) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_index_read requires exactly two arguments."
                );
            }
            return "dev.tsj.runtime.TsjRuntime.indexRead("
                    + emitExpression(context, callExpression.arguments().get(0))
                    + ", "
                    + emitExpression(context, callExpression.arguments().get(1))
                    + ")";
        }

        private String emitOptionalIndexReadRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().size() != 2) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_optional_index_read requires exactly two arguments."
                );
            }
            return "dev.tsj.runtime.TsjRuntime.optionalIndexRead("
                    + emitExpression(context, callExpression.arguments().get(0))
                    + ", "
                    + emitExpression(context, callExpression.arguments().get(1))
                    + ")";
        }

        private String emitSuperInvokeRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().isEmpty()) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_super_invoke requires at least a method name argument."
                );
            }
            if (!(callExpression.arguments().get(0) instanceof StringLiteral methodNameLiteral)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_super_invoke requires a string method name argument."
                );
            }
            final String superClassExpression = context.resolveSuperClassExpression();
            if (superClassExpression == null) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "`super.member(...)` is only valid in derived class methods."
                );
            }
            final String methodName = "\"" + escapeJava(methodNameLiteral.value()) + "\"";
            final List<String> renderedArgs = new ArrayList<>();
            for (int index = 1; index < callExpression.arguments().size(); index++) {
                renderedArgs.add(emitExpression(context, callExpression.arguments().get(index)));
            }
            if (renderedArgs.isEmpty()) {
                return "dev.tsj.runtime.TsjRuntime.superInvokeMember("
                        + superClassExpression
                        + ", "
                        + context.resolveThisReference()
                        + ", "
                        + methodName
                        + ")";
            }
            return "dev.tsj.runtime.TsjRuntime.superInvokeMember("
                    + superClassExpression
                    + ", "
                    + context.resolveThisReference()
                    + ", "
                    + methodName
                    + ", "
                    + String.join(", ", renderedArgs)
                    + ")";
        }

        private String emitSetDynamicKeyRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().size() != 3) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_set_dynamic_key requires exactly three arguments."
                );
            }
            return "dev.tsj.runtime.TsjRuntime.setPropertyDynamic("
                    + emitExpression(context, callExpression.arguments().get(0))
                    + ", "
                    + emitExpression(context, callExpression.arguments().get(1))
                    + ", "
                            + emitExpression(context, callExpression.arguments().get(2))
                            + ")";
        }

        private String emitDefineAccessorRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().size() != 4) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_define_accessor requires exactly four arguments."
                );
            }
            return "dev.tsj.runtime.TsjRuntime.defineAccessorProperty("
                    + emitExpression(context, callExpression.arguments().get(0))
                    + ", "
                    + emitExpression(context, callExpression.arguments().get(1))
                    + ", "
                    + emitExpression(context, callExpression.arguments().get(2))
                    + ", "
                    + emitExpression(context, callExpression.arguments().get(3))
                    + ")";
        }

        private String emitRestArgsRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().size() != 1
                    || !(callExpression.arguments().get(0) instanceof NumberLiteral numberLiteral)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_rest_args requires exactly one numeric start index argument."
                );
            }
            final int startIndex;
            try {
                startIndex = Integer.parseInt(numberLiteral.value());
            } catch (NumberFormatException numberFormatException) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_rest_args start index must be an integer literal."
                );
            }
            if (startIndex < 0) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_rest_args start index must be non-negative."
                );
            }
            return "dev.tsj.runtime.TsjRuntime.restArgs("
                    + context.resolveArgumentsReference()
                    + ", "
                    + startIndex
                    + ")";
        }

        private String emitArrayRestRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().size() != 2
                    || !(callExpression.arguments().get(1) instanceof NumberLiteral numberLiteral)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_array_rest requires source expression and numeric start index."
                );
            }
            final int startIndex;
            try {
                startIndex = Integer.parseInt(numberLiteral.value());
            } catch (NumberFormatException numberFormatException) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_array_rest start index must be an integer literal."
                );
            }
            if (startIndex < 0) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_array_rest start index must be non-negative."
                );
            }
            return "dev.tsj.runtime.TsjRuntime.arrayRest("
                    + emitExpression(context, callExpression.arguments().get(0))
                    + ", "
                    + startIndex
                    + ")";
        }

        private boolean isArraySpreadFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_array_spread".equals(variableExpression.name());
        }

        private boolean isObjectSpreadFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_object_spread".equals(variableExpression.name());
        }

        private boolean isCallSpreadFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_call_spread".equals(variableExpression.name());
        }

        private boolean isRestArgsFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_rest_args".equals(variableExpression.name());
        }

        private boolean isArrayRestFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_array_rest".equals(variableExpression.name());
        }

        private boolean isForOfValuesFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_for_of_values".equals(variableExpression.name());
        }

        private boolean isForInKeysFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_for_in_keys".equals(variableExpression.name());
        }

        private boolean isIndexReadFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_index_read".equals(variableExpression.name());
        }

        private boolean isOptionalIndexReadFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_optional_index_read".equals(variableExpression.name());
        }

        private boolean isSuperInvokeFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_super_invoke".equals(variableExpression.name());
        }

        private boolean isSetDynamicKeyFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_set_dynamic_key".equals(variableExpression.name());
        }

        private boolean isDefineAccessorFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_define_accessor".equals(variableExpression.name());
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
            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f");
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
            private final Map<String, String> labels;
            private final Set<String> generatedNames;
            private final String thisReference;
            private final String superClassExpression;
            private final boolean constructorContext;
            private final String argumentsReference;

            private EmissionContext(final EmissionContext parent) {
                this(
                        parent,
                        parent != null ? parent.thisReference : null,
                        parent != null ? parent.superClassExpression : null,
                        parent != null && parent.constructorContext,
                        parent != null ? parent.argumentsReference : null
                );
            }

            private EmissionContext(
                    final EmissionContext parent,
                    final String thisReference,
                    final String superClassExpression,
                    final boolean constructorContext
            ) {
                this(
                        parent,
                        thisReference,
                        superClassExpression,
                        constructorContext,
                        parent != null ? parent.argumentsReference : null
                );
            }

            private EmissionContext(
                    final EmissionContext parent,
                    final String thisReference,
                    final String superClassExpression,
                    final boolean constructorContext,
                    final String argumentsReference
            ) {
                this.parent = parent;
                this.bindings = new LinkedHashMap<>();
                this.labels = new LinkedHashMap<>();
                this.generatedNames = new LinkedHashSet<>();
                this.thisReference = thisReference;
                this.superClassExpression = superClassExpression;
                this.constructorContext = constructorContext;
                this.argumentsReference = argumentsReference;
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
                if ("Error".equals(sourceName)) {
                    return ERROR_BUILTIN_CELL_FIELD;
                }
                if ("String".equals(sourceName)) {
                    return STRING_BUILTIN_CELL_FIELD;
                }
                if ("JSON".equals(sourceName)) {
                    return JSON_BUILTIN_CELL_FIELD;
                }
                if ("Object".equals(sourceName)) {
                    return OBJECT_BUILTIN_CELL_FIELD;
                }
                if ("Reflect".equals(sourceName)) {
                    return REFLECT_BUILTIN_CELL_FIELD;
                }
                if ("Proxy".equals(sourceName)) {
                    return PROXY_BUILTIN_CELL_FIELD;
                }
                if ("Array".equals(sourceName)) {
                    return ARRAY_BUILTIN_CELL_FIELD;
                }
                if ("Map".equals(sourceName)) {
                    return MAP_BUILTIN_CELL_FIELD;
                }
                if ("Set".equals(sourceName)) {
                    return SET_BUILTIN_CELL_FIELD;
                }
                if ("WeakMap".equals(sourceName)) {
                    return WEAK_MAP_BUILTIN_CELL_FIELD;
                }
                if ("WeakSet".equals(sourceName)) {
                    return WEAK_SET_BUILTIN_CELL_FIELD;
                }
                if ("WeakRef".equals(sourceName)) {
                    return WEAK_REF_BUILTIN_CELL_FIELD;
                }
                if ("Date".equals(sourceName)) {
                    return DATE_BUILTIN_CELL_FIELD;
                }
                if ("RegExp".equals(sourceName)) {
                    return REGEXP_BUILTIN_CELL_FIELD;
                }
                if ("AggregateError".equals(sourceName)) {
                    return AGGREGATE_ERROR_BUILTIN_CELL_FIELD;
                }
                if ("TypeError".equals(sourceName)) {
                    return TYPE_ERROR_BUILTIN_CELL_FIELD;
                }
                if ("RangeError".equals(sourceName)) {
                    return RANGE_ERROR_BUILTIN_CELL_FIELD;
                }
                if ("Math".equals(sourceName)) {
                    return MATH_BUILTIN_CELL_FIELD;
                }
                if ("Number".equals(sourceName)) {
                    return NUMBER_BUILTIN_CELL_FIELD;
                }
                if ("BigInt".equals(sourceName)) {
                    return BIGINT_BUILTIN_CELL_FIELD;
                }
                if ("Symbol".equals(sourceName)) {
                    return SYMBOL_BUILTIN_CELL_FIELD;
                }
                if ("parseInt".equals(sourceName)) {
                    return PARSE_INT_BUILTIN_CELL_FIELD;
                }
                if ("parseFloat".equals(sourceName)) {
                    return PARSE_FLOAT_BUILTIN_CELL_FIELD;
                }
                if ("Infinity".equals(sourceName)) {
                    return INFINITY_BUILTIN_CELL_FIELD;
                }
                if ("NaN".equals(sourceName)) {
                    return NAN_BUILTIN_CELL_FIELD;
                }
                if ("Promise".equals(sourceName)) {
                    return "PROMISE_BUILTIN_CELL";
                }
                if (IMPLICIT_GLOBAL_BUILTINS.contains(sourceName)) {
                    return UNDEFINED_BUILTIN_CELL_FIELD;
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

            private String declareLabel(final String sourceLabel) {
                if (labels.containsKey(sourceLabel)) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-UNSUPPORTED",
                            "Duplicate label in scope: " + sourceLabel
                    );
                }
                final String labelName = allocateUniqueName("__tsj_label_" + sanitizeIdentifier(sourceLabel));
                labels.put(sourceLabel, labelName);
                return labelName;
            }

            private String resolveLabel(final String sourceLabel) {
                if (labels.containsKey(sourceLabel)) {
                    return labels.get(sourceLabel);
                }
                if (parent != null) {
                    return parent.resolveLabel(sourceLabel);
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Unresolved loop label in TSJ-59 subset: " + sourceLabel
                );
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
                if (bindings.containsValue(value) || labels.containsValue(value) || generatedNames.contains(value)) {
                    return true;
                }
                if (parent != null) {
                    return parent.isNameUsed(value);
                }
                return false;
            }

            private boolean isTopLevelScope() {
                return parent == null;
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
                        "`this` is only valid inside function or class method bodies in TSJ-8 subset."
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

            private String resolveArgumentsReference() {
                if (argumentsReference != null) {
                    return argumentsReference;
                }
                if (parent != null) {
                    return parent.resolveArgumentsReference();
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_rest_args is only valid inside function-like bodies."
                );
            }
        }
    }
}
