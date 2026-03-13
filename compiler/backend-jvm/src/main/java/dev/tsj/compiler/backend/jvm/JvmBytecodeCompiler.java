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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    private static final Pattern EXPORT_ALL_FROM_PATTERN = Pattern.compile(
            "^\\s*export\\s*\\*\\s*from\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Pattern EXPORT_NAMED_FROM_PATTERN = Pattern.compile(
            "^\\s*export\\s*\\{([^}]*)}\\s*from\\s*[\"']([^\"']+)['\"]\\s*;\\s*$"
    );
    private static final Pattern DYNAMIC_IMPORT_CALL_PATTERN = Pattern.compile(
            "\\bimport\\s*\\(([^)]*)\\)"
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
    private static final String FEATURE_TSX_OUT_OF_SCOPE = "TSJ67-TSX-OUT-OF-SCOPE";
    private static final String FEATURE_STRICT_BRIDGE = "TSJ80-STRICT-BRIDGE";
    private static final int JAVA_ACC_ANNOTATION = 0x2000;
    private static final String ASYNC_BREAK_SIGNAL_FIELD = "__TSJ_ASYNC_BREAK_SIGNAL";
    private static final String ASYNC_CONTINUE_SIGNAL_FIELD = "__TSJ_ASYNC_CONTINUE_SIGNAL";
    private static final String TOP_LEVEL_CLASS_MAP_FIELD = "__TSJ_TOP_LEVEL_CLASSES";
    private static final String BOOTSTRAP_GUARD_FIELD = "__TSJ_BOOTSTRAPPED";
    private static final String BOOTSTRAP_IN_PROGRESS_FIELD = "__TSJ_BOOTSTRAPPING";
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
    private static final String GUIDANCE_TSX_OUT_OF_SCOPE =
            "TSX/JSX is not in scope. Use .ts/.d.ts inputs or remove JSX syntax.";
    private static final String GUIDANCE_STRICT_BRIDGE =
            "Rewrite class members to strict-native subset (field assignment, return values, direct `this.method(...)` calls) or compile in `default` mode.";
    private static final String LEGACY_TOKENIZER_PROPERTY = "tsj.backend.legacyTokenizer";
    private static final String AST_NO_FALLBACK_PROPERTY = "tsj.backend.astNoFallback";
    private static final String ADDITIONAL_CLASSPATH_PROPERTY = "tsj.backend.additionalClasspath";
    private static final String INCREMENTAL_CACHE_PROPERTY = "tsj.backend.incrementalCache";
    private static final String INCREMENTAL_CACHE_VERSION = "tsj69-v1";
    private static final int INCREMENTAL_PARSE_CACHE_MAX_ENTRIES = 256;
    private static final int INCREMENTAL_FINGERPRINT_HISTORY_MAX_ENTRIES = 1024;
    private static final Set<String> STRICT_NATIVE_SUPPORTED_UNARY_OPERATORS = Set.of("+", "-", "!", "~");
    private static final Set<String> STRICT_NATIVE_SUPPORTED_BINARY_OPERATORS = Set.of(
            "+",
            "-",
            "*",
            "/",
            "%",
            "**",
            "&",
            "|",
            "^",
            "<<",
            ">>",
            ">>>",
            ",",
            "<",
            "<=",
            ">",
            ">=",
            "==",
            "===",
            "!=",
            "!==",
            "&&",
            "||",
            "??"
    );

    private static final String OUTPUT_PACKAGE = "dev.tsj.generated";
    private static final String METADATA_CARRIER_PACKAGE = OUTPUT_PACKAGE + ".metadata";
    private static final String METADATA_CARRIER_SUFFIX = "TsjCarrier";
    private static final Map<IncrementalParseCacheKey, ParseResult> INCREMENTAL_PARSE_CACHE =
            newLruMap(INCREMENTAL_PARSE_CACHE_MAX_ENTRIES);
    private static final Map<Path, String> LAST_SOURCE_GRAPH_FINGERPRINT =
            newLruMap(INCREMENTAL_FINGERPRINT_HISTORY_MAX_ENTRIES);
    private IncrementalCompilationReport lastIncrementalCompilationReport = IncrementalCompilationReport.disabled();
    private StrictLoweringPath lastStrictLoweringPath = StrictLoweringPath.RUNTIME_CARRIER;

    public enum BackendMode {
        DEFAULT,
        JVM_STRICT
    }

    public enum StrictLoweringPath {
        RUNTIME_CARRIER("runtime-carrier"),
        JVM_NATIVE_CLASS_SUBSET("jvm-native-class-subset");

        private final String metadataValue;

        StrictLoweringPath(final String metadataValue) {
            this.metadataValue = metadataValue;
        }

        public String metadataValue() {
            return metadataValue;
        }
    }

    public enum IncrementalStageState {
        HIT,
        MISS,
        INVALIDATED,
        DISABLED
    }

    public record IncrementalCompilationReport(
            boolean cacheEnabled,
            String compilerVersion,
            String sourceGraphFingerprint,
            IncrementalStageState frontend,
            IncrementalStageState lowering,
            IncrementalStageState backend
    ) {
        public static IncrementalCompilationReport disabled() {
            return new IncrementalCompilationReport(
                    false,
                    "",
                    "",
                    IncrementalStageState.DISABLED,
                    IncrementalStageState.DISABLED,
                    IncrementalStageState.DISABLED
            );
        }
    }

    private record ParseWithIncrementalResult(ParseResult parseResult, IncrementalCompilationReport report) {
    }

    private record IncrementalParseCacheKey(
            String sourceGraphFingerprint,
            String compilerVersion
    ) {
    }

    public JvmCompiledArtifact compile(final Path sourceFile, final Path outputDir) {
        return compile(sourceFile, outputDir, JvmOptimizationOptions.defaults(), BackendMode.DEFAULT);
    }

    public IncrementalCompilationReport lastIncrementalCompilationReport() {
        return lastIncrementalCompilationReport;
    }

    public StrictLoweringPath lastStrictLoweringPath() {
        return lastStrictLoweringPath;
    }

    public JvmCompiledArtifact compile(
            final Path sourceFile,
            final Path outputDir,
            final JvmOptimizationOptions optimizationOptions
    ) {
        return compile(sourceFile, outputDir, optimizationOptions, BackendMode.DEFAULT);
    }

    public JvmCompiledArtifact compile(
            final Path sourceFile,
            final Path outputDir,
            final JvmOptimizationOptions optimizationOptions,
            final BackendMode backendMode
    ) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(optimizationOptions, "optimizationOptions");
        Objects.requireNonNull(backendMode, "backendMode");

        final Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        final String fileName = normalizedSource.getFileName().toString();
        lastIncrementalCompilationReport = IncrementalCompilationReport.disabled();
        lastStrictLoweringPath = StrictLoweringPath.RUNTIME_CARRIER;
        if (!fileName.endsWith(".ts") && !fileName.endsWith(".tsx")) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-INPUT",
                    "Unsupported input extension for backend compile: " + normalizedSource
            );
        }
        if (fileName.endsWith(".tsx")) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-UNSUPPORTED",
                    "TSX/JSX input is out of scope for current TSJ syntax support."
                            + " [featureId=" + FEATURE_TSX_OUT_OF_SCOPE + "]. Guidance: " + GUIDANCE_TSX_OUT_OF_SCOPE,
                    1,
                    1,
                    normalizedSource.toString(),
                    FEATURE_TSX_OUT_OF_SCOPE,
                    GUIDANCE_TSX_OUT_OF_SCOPE
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
            final ParseWithIncrementalResult parseWithIncrementalResult = parseProgramWithIncrementalCache(
                    bundleResult.sourceText(),
                    normalizedSource,
                    bundleResult
            );
            parseResult = parseWithIncrementalResult.parseResult();
            lastIncrementalCompilationReport = parseWithIncrementalResult.report();
        }
        final Program parsedProgram = parseResult.program();
        final Map<Statement, SourceLocation> parsedStatementLocations = parseResult.statementLocations();
        final ProgramOptimizationResult optimizationResult = new ProgramOptimizer(
                optimizationOptions,
                parsedStatementLocations
        ).optimize(parsedProgram);
        final Program program = optimizationResult.program();
        final StrictNativeClassLoweringPlan strictLoweringPlan = resolveStrictNativeClassLoweringPlan(
                parsedProgram,
                parsedStatementLocations,
                normalizedSource,
                backendMode
        );
        lastStrictLoweringPath = strictLoweringPlan.loweringPath();
        final String classSimpleName = toPascalCase(stripExtension(fileName)) + "Program";
        final String className = OUTPUT_PACKAGE + "." + classSimpleName;
        final JavaSourceGenerator javaSourceGenerator = new JavaSourceGenerator(
                OUTPUT_PACKAGE,
                classSimpleName,
                program,
                optimizationResult.statementLocations(),
                strictLoweringPlan
        );
        final String javaSource = javaSourceGenerator.generate();
        final List<TopLevelClassDeclaration> topLevelClassDeclarations = collectTopLevelClassDeclarations(
                parsedProgram,
                parsedStatementLocations
        );
        final List<MetadataCarrierDeclaration> metadataCarrierDeclarations =
                resolveMetadataCarrierDeclarations(normalizedSource, topLevelClassDeclarations);
        final List<MetadataCarrierDeclaration> runtimeCarrierMetadataDeclarations =
                filterRuntimeCarrierMetadataDeclarations(
                        metadataCarrierDeclarations,
                        strictLoweringPlan,
                        normalizedSource
                );

        final Path normalizedOutput = outputDir.toAbsolutePath().normalize();
        final Path classesDir = normalizedOutput.resolve("classes");
        final Path generatedSourceRoot = normalizedOutput.resolve("generated-src");
        final Path generatedSource = generatedSourceRoot
                .resolve(OUTPUT_PACKAGE.replace('.', '/'))
                .resolve(classSimpleName + ".java");
        final Path sourceMapFile = classesDir.resolve(OUTPUT_PACKAGE.replace('.', '/'))
                .resolve(classSimpleName + ".tsj.map");
        final AnnotationRenderContext annotationRenderContext = createAnnotationRenderContext();
        final List<Path> generatedSources = new ArrayList<>();
        try {
            Files.createDirectories(classesDir);
            Files.createDirectories(generatedSource.getParent());
            Files.writeString(generatedSource, javaSource, UTF_8);
            Files.createDirectories(sourceMapFile.getParent());
            writeSourceMapFile(sourceMapFile, parseSourceMapEntries(javaSource));
            generatedSources.add(generatedSource);
            generatedSources.addAll(javaSourceGenerator.writeStrictNativeSources(
                    generatedSourceRoot,
                    metadataCarrierDeclarations,
                    annotationRenderContext
            ));
            generatedSources.addAll(writeMetadataCarrierSources(
                    generatedSourceRoot,
                    runtimeCarrierMetadataDeclarations,
                    annotationRenderContext
            ));
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-IO",
                    "Failed to write generated Java source: " + ioException.getMessage(),
                    null,
                    null,
                    ioException
            );
        }

        compileJava(generatedSources, classesDir);

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
        return new JvmCompiledArtifact(
                normalizedSource,
                classesDir,
                className,
                classFile,
                sourceMapFile,
                strictLoweringPlan.loweringPath().metadataValue()
        );
    }

    private static List<TopLevelClassDeclaration> collectTopLevelClassDeclarations(
            final Program program,
            final Map<Statement, SourceLocation> statementLocations
    ) {
        final List<TopLevelClassDeclaration> declarations = new ArrayList<>();
        final Set<String> rootBindingNames = collectTopLevelBindingNames(program.statements());
        for (Statement statement : program.statements()) {
            collectTopLevelClassDeclarationsFromStatement(
                    statement,
                    statementLocations,
                    rootBindingNames,
                    declarations
            );
        }
        return List.copyOf(declarations);
    }

    private static void collectTopLevelClassDeclarationsFromStatement(
            final Statement statement,
            final Map<Statement, SourceLocation> statementLocations,
            final Set<String> visibleBindingNames,
            final List<TopLevelClassDeclaration> declarations
    ) {
        if (statement instanceof ClassDeclarationStatement classDeclarationStatement) {
            declarations.add(new TopLevelClassDeclaration(
                    classDeclarationStatement.declaration(),
                    statementLocations.get(statement),
                    visibleBindingNames,
                    Map.of()
            ));
            return;
        }
        if (statement instanceof FunctionDeclarationStatement functionDeclarationStatement
                && functionDeclarationStatement.declaration().name().startsWith("__tsj_init_module_")) {
            final Set<String> moduleBindingNames = collectTopLevelBindingNames(
                    functionDeclarationStatement.declaration().body()
            );
            final Map<String, String> importBindingTargets = collectBundledModuleImportBindingTargets(
                    functionDeclarationStatement.declaration().body()
            );
            final Map<String, String> moduleBindingTargets = new LinkedHashMap<>();
            for (String moduleBindingName : moduleBindingNames) {
                moduleBindingTargets.put(
                        moduleBindingName,
                        importBindingTargets.getOrDefault(
                                moduleBindingName,
                                moduleBindingLookupKey(functionDeclarationStatement.declaration().name(), moduleBindingName)
                        )
                );
            }
            for (Statement moduleTopLevelStatement : functionDeclarationStatement.declaration().body()) {
                if (moduleTopLevelStatement instanceof ClassDeclarationStatement classDeclarationStatement) {
                    declarations.add(new TopLevelClassDeclaration(
                            classDeclarationStatement.declaration(),
                            statementLocations.get(moduleTopLevelStatement),
                            moduleBindingNames,
                            moduleBindingTargets
                    ));
                }
            }
        }
    }

    private static String moduleBindingLookupKey(
            final String moduleInitializerFunctionName,
            final String bindingName
    ) {
        return moduleInitializerFunctionName + "::" + bindingName;
    }

    private static Map<String, String> collectBundledModuleImportBindingTargets(
            final List<Statement> statements
    ) {
        final Map<String, String> bindingTargets = new LinkedHashMap<>();
        for (Statement statement : statements) {
            if (!(statement instanceof VariableDeclaration variableDeclaration)) {
                continue;
            }
            if (!(variableDeclaration.expression() instanceof VariableExpression variableExpression)) {
                continue;
            }
            if (!isBundledExportSymbolName(variableExpression.name())) {
                continue;
            }
            bindingTargets.put(variableDeclaration.name(), variableExpression.name());
        }
        return Map.copyOf(bindingTargets);
    }

    private static boolean isBundledExportSymbolName(final String bindingName) {
        return bindingName.startsWith("__tsj_export_");
    }

    private static Set<String> collectTopLevelBindingNames(final List<Statement> statements) {
        final Set<String> bindingNames = new LinkedHashSet<>();
        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration variableDeclaration) {
                bindingNames.add(variableDeclaration.name());
                continue;
            }
            if (statement instanceof FunctionDeclarationStatement functionDeclarationStatement) {
                bindingNames.add(functionDeclarationStatement.declaration().name());
                continue;
            }
            if (statement instanceof ClassDeclarationStatement classDeclarationStatement) {
                bindingNames.add(classDeclarationStatement.declaration().name());
            }
        }
        return Set.copyOf(bindingNames);
    }

    private record StrictNativeClassLoweringPlan(
            StrictLoweringPath loweringPath,
            List<TopLevelClassDeclaration> nativeClasses
    ) {
        private StrictNativeClassLoweringPlan {
            loweringPath = Objects.requireNonNull(loweringPath, "loweringPath");
            nativeClasses = List.copyOf(Objects.requireNonNull(nativeClasses, "nativeClasses"));
        }

        private static StrictNativeClassLoweringPlan runtimeCarrier() {
            return new StrictNativeClassLoweringPlan(StrictLoweringPath.RUNTIME_CARRIER, List.of());
        }
    }

    private static StrictNativeClassLoweringPlan resolveStrictNativeClassLoweringPlan(
            final Program program,
            final Map<Statement, SourceLocation> statementLocations,
            final Path entryFile,
            final BackendMode backendMode
    ) {
        if (backendMode != BackendMode.JVM_STRICT) {
            return StrictNativeClassLoweringPlan.runtimeCarrier();
        }
        final List<TopLevelClassDeclaration> discoveredClasses = collectTopLevelClassDeclarations(
                program,
                statementLocations
        );
        if (discoveredClasses.isEmpty()) {
            return StrictNativeClassLoweringPlan.runtimeCarrier();
        }
        final Map<String, ClassDeclaration> strictClassesByName = new LinkedHashMap<>();
        final List<TopLevelClassDeclaration> nativeClasses = new ArrayList<>();
        for (TopLevelClassDeclaration discoveredClass : discoveredClasses) {
            strictClassesByName.put(discoveredClass.declaration().name(), discoveredClass.declaration());
        }
        for (TopLevelClassDeclaration discoveredClass : discoveredClasses) {
            validateStrictNativeClassDeclaration(
                    discoveredClass.declaration(),
                    discoveredClass.visibleBindingNames(),
                    strictClassesByName,
                    discoveredClass.sourceLocation(),
                    entryFile
            );
            nativeClasses.add(discoveredClass);
        }
        return new StrictNativeClassLoweringPlan(
                StrictLoweringPath.JVM_NATIVE_CLASS_SUBSET,
                List.copyOf(nativeClasses)
        );
    }

    private static void predeclareStrictNativeFunctionBindings(
            final Set<String> bindings,
            final List<Statement> statements
    ) {
        for (Statement statement : statements) {
            if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                bindings.add(declarationStatement.declaration().name());
            }
        }
    }

    private static void validateStrictNativeClassDeclaration(
            final ClassDeclaration declaration,
            final Set<String> topLevelBindingNames,
            final Map<String, ClassDeclaration> strictClassesByName,
            final SourceLocation location,
            final Path entryFile
    ) {
        final ClassDeclaration superDeclaration;
        if (declaration.superClassName() == null) {
            superDeclaration = null;
        } else {
            superDeclaration = strictClassesByName.get(declaration.superClassName());
            if (superDeclaration == null) {
                throw strictBridgeFailure(
                        "Class extends is only supported when the base class is also lowered via the strict native subset (`"
                                + declaration.name()
                                + " extends "
                                + declaration.superClassName()
                                + "`).",
                        location,
                        entryFile
                );
            }
        }
        final Set<String> fieldNames = new LinkedHashSet<>(declaration.fieldNames());
        final Set<String> methodNames = new LinkedHashSet<>();
        for (ClassMethod method : declaration.methods()) {
            methodNames.add(method.name());
        }
        final Set<String> staticFieldNames = new LinkedHashSet<>();
        for (ClassField field : declaration.staticFields()) {
            staticFieldNames.add(field.name());
        }
        final Set<String> staticMethodNames = new LinkedHashSet<>();
        for (ClassMethod method : declaration.staticMethods()) {
            staticMethodNames.add(method.name());
        }
        if (declaration.constructorMethod() != null) {
            validateStrictNativeMethod(
                    declaration.constructorMethod(),
                    true,
                    superDeclaration,
                    topLevelBindingNames,
                    strictClassesByName,
                    declaration.name(),
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    location,
                    entryFile
            );
        } else if (superDeclaration != null) {
            final int superConstructorArity = superDeclaration.constructorMethod() == null
                    ? 0
                    : superDeclaration.constructorMethod().parameters().size();
            if (superConstructorArity != 0) {
                throw strictBridgeFailure(
                        "Derived class without constructor requires zero-argument base constructor in strict native subset (`"
                                + declaration.name()
                                + "`).",
                        location,
                        entryFile
                );
            }
        }
        for (ClassMethod method : declaration.methods()) {
            validateStrictNativeMethod(
                    method,
                    false,
                    superDeclaration,
                    topLevelBindingNames,
                    strictClassesByName,
                    declaration.name(),
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    location,
                    entryFile
            );
        }
        for (ClassField field : declaration.staticFields()) {
            validateStrictNativeExpression(
                    field.initializer(),
                    false,
                    strictClassesByName,
                    declaration.name(),
                    Set.of(),
                    Set.of(),
                    staticFieldNames,
                    staticMethodNames,
                    topLevelBindingNames,
                    location,
                    entryFile
            );
        }
        for (ClassMethod method : declaration.staticMethods()) {
            validateStrictNativeMethod(
                    method,
                    false,
                    null,
                    topLevelBindingNames,
                    strictClassesByName,
                    declaration.name(),
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    location,
                    entryFile
            );
        }
    }

    private static void validateStrictNativeMethod(
            final ClassMethod method,
            final boolean constructor,
            final ClassDeclaration superDeclaration,
            final Set<String> topLevelBindingNames,
            final Map<String, ClassDeclaration> strictClassesByName,
            final String currentClassName,
            final Set<String> fieldNames,
            final Set<String> methodNames,
            final Set<String> staticFieldNames,
            final Set<String> staticMethodNames,
            final SourceLocation location,
            final Path entryFile
    ) {
        if (method.async()) {
            throw strictBridgeFailure(
                    "Async class members are not supported in strict native subset (`" + method.name() + "`).",
                    location,
                    entryFile
            );
        }
        final Set<String> bindings = new LinkedHashSet<>(topLevelBindingNames);
        bindings.addAll(method.parameters());
        predeclareStrictNativeFunctionBindings(bindings, method.body());
        int startIndex = 0;
        if (constructor && superDeclaration != null) {
            if (method.body().isEmpty() || !(method.body().getFirst() instanceof SuperCallStatement superCallStatement)) {
                throw strictBridgeFailure(
                        "Derived class constructors must start with `super(...)` in strict native subset (`"
                                + method.name()
                                + "`).",
                        location,
                        entryFile
                );
            }
            final int expectedArity = superDeclaration.constructorMethod() == null
                    ? 0
                    : superDeclaration.constructorMethod().parameters().size();
            if (superCallStatement.arguments().size() != expectedArity) {
                throw strictBridgeFailure(
                        "Super constructor arity mismatch in strict native subset (`"
                                + method.name()
                                + "` expects "
                                + expectedArity
                                + " arguments).",
                        location,
                        entryFile
                );
            }
            for (Expression argument : superCallStatement.arguments()) {
                validateStrictNativeExpression(
                        argument,
                        true,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        location,
                        entryFile
                );
            }
            startIndex = 1;
        }
        for (int index = startIndex; index < method.body().size(); index++) {
            final Statement statement = method.body().get(index);
            validateStrictNativeStatement(
                    statement,
                    superDeclaration != null,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    location,
                    entryFile
            );
        }
    }

    private static void validateStrictNativeStatement(
            final Statement statement,
            final boolean hasSuperClass,
            final Map<String, ClassDeclaration> strictClassesByName,
            final String currentClassName,
            final Set<String> fieldNames,
            final Set<String> methodNames,
            final Set<String> staticFieldNames,
            final Set<String> staticMethodNames,
            final Set<String> bindings,
            final SourceLocation location,
            final Path entryFile
    ) {
        validateStrictNativeStatement(
                statement,
                hasSuperClass,
                strictClassesByName,
                currentClassName,
                fieldNames,
                methodNames,
                staticFieldNames,
                staticMethodNames,
                bindings,
                false,
                location,
                entryFile
        );
    }

    private static void validateStrictNativeStatement(
            final Statement statement,
            final boolean hasSuperClass,
            final Map<String, ClassDeclaration> strictClassesByName,
            final String currentClassName,
            final Set<String> fieldNames,
            final Set<String> methodNames,
            final Set<String> staticFieldNames,
            final Set<String> staticMethodNames,
            final Set<String> bindings,
            final boolean dynamicThisScope,
            final SourceLocation location,
            final Path entryFile
    ) {
        validateStrictNativeStatement(
                statement,
                hasSuperClass,
                strictClassesByName,
                currentClassName,
                fieldNames,
                methodNames,
                staticFieldNames,
                staticMethodNames,
                bindings,
                dynamicThisScope,
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                location,
                entryFile
        );
    }

    private static void validateStrictNativeStatement(
            final Statement statement,
            final boolean hasSuperClass,
            final Map<String, ClassDeclaration> strictClassesByName,
            final String currentClassName,
            final Set<String> fieldNames,
            final Set<String> methodNames,
            final Set<String> staticFieldNames,
            final Set<String> staticMethodNames,
            final Set<String> bindings,
            final boolean dynamicThisScope,
            final Set<String> activeLabels,
            final Set<String> loopLabels,
            final SourceLocation location,
            final Path entryFile
    ) {
        if (statement instanceof VariableDeclaration variableDeclaration) {
            bindings.add(variableDeclaration.name());
            validateStrictNativeExpression(
                    variableDeclaration.expression(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (statement instanceof AssignmentStatement assignmentStatement) {
            validateStrictNativeAssignmentTarget(
                    assignmentStatement.target(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            validateStrictNativeExpression(
                    assignmentStatement.expression(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (statement instanceof ReturnStatement returnStatement) {
            validateStrictNativeExpression(
                    returnStatement.expression(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (statement instanceof ThrowStatement throwStatement) {
            validateStrictNativeExpression(
                    throwStatement.expression(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (statement instanceof ExpressionStatement expressionStatement) {
            validateStrictNativeExpression(
                    expressionStatement.expression(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (statement instanceof IfStatement ifStatement) {
            validateStrictNativeExpression(
                    ifStatement.condition(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            final Set<String> thenBindings = new LinkedHashSet<>(bindings);
            predeclareStrictNativeFunctionBindings(thenBindings, ifStatement.thenBlock());
            for (Statement branchStatement : ifStatement.thenBlock()) {
                validateStrictNativeStatement(
                        branchStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        thenBindings,
                        dynamicThisScope,
                        new LinkedHashSet<>(activeLabels),
                        new LinkedHashSet<>(loopLabels),
                        location,
                        entryFile
                );
            }
            final Set<String> elseBindings = new LinkedHashSet<>(bindings);
            predeclareStrictNativeFunctionBindings(elseBindings, ifStatement.elseBlock());
            for (Statement branchStatement : ifStatement.elseBlock()) {
                validateStrictNativeStatement(
                        branchStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        elseBindings,
                        dynamicThisScope,
                        new LinkedHashSet<>(activeLabels),
                        new LinkedHashSet<>(loopLabels),
                        location,
                        entryFile
                );
            }
            return;
        }
        if (statement instanceof WhileStatement whileStatement) {
            validateStrictNativeExpression(
                    whileStatement.condition(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            final Set<String> loopBindings = new LinkedHashSet<>(bindings);
            predeclareStrictNativeFunctionBindings(loopBindings, whileStatement.body());
            for (Statement bodyStatement : whileStatement.body()) {
                validateStrictNativeStatement(
                        bodyStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        loopBindings,
                        dynamicThisScope,
                        new LinkedHashSet<>(activeLabels),
                        new LinkedHashSet<>(loopLabels),
                        location,
                        entryFile
                );
            }
            return;
        }
        if (statement instanceof TryStatement tryStatement) {
            final Set<String> tryBindings = new LinkedHashSet<>(bindings);
            predeclareStrictNativeFunctionBindings(tryBindings, tryStatement.tryBlock());
            for (Statement tryBlockStatement : tryStatement.tryBlock()) {
                validateStrictNativeStatement(
                        tryBlockStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        tryBindings,
                        dynamicThisScope,
                        new LinkedHashSet<>(activeLabels),
                        new LinkedHashSet<>(loopLabels),
                        location,
                        entryFile
                );
            }
            final Set<String> catchBindings = new LinkedHashSet<>(bindings);
            if (tryStatement.catchBinding() != null) {
                catchBindings.add(tryStatement.catchBinding());
            }
            predeclareStrictNativeFunctionBindings(catchBindings, tryStatement.catchBlock());
            for (Statement catchBlockStatement : tryStatement.catchBlock()) {
                validateStrictNativeStatement(
                        catchBlockStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        catchBindings,
                        dynamicThisScope,
                        new LinkedHashSet<>(activeLabels),
                        new LinkedHashSet<>(loopLabels),
                        location,
                        entryFile
                );
            }
            final Set<String> finallyBindings = new LinkedHashSet<>(bindings);
            predeclareStrictNativeFunctionBindings(finallyBindings, tryStatement.finallyBlock());
            for (Statement finallyBlockStatement : tryStatement.finallyBlock()) {
                validateStrictNativeStatement(
                        finallyBlockStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        finallyBindings,
                        dynamicThisScope,
                        new LinkedHashSet<>(activeLabels),
                        new LinkedHashSet<>(loopLabels),
                        location,
                        entryFile
                );
            }
            return;
        }
        if (statement instanceof LabeledStatement labeledStatement) {
            if (activeLabels.contains(labeledStatement.label())) {
                throw strictBridgeFailure(
                        "Duplicate label `" + labeledStatement.label() + "` in strict native subset.",
                        location,
                        entryFile
                );
            }
            final Set<String> nestedLabels = new LinkedHashSet<>(activeLabels);
            nestedLabels.add(labeledStatement.label());
            final Set<String> nestedLoopLabels = new LinkedHashSet<>(loopLabels);
            if (labeledStatement.statement() instanceof WhileStatement) {
                nestedLoopLabels.add(labeledStatement.label());
            }
            validateStrictNativeStatement(
                    labeledStatement.statement(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    nestedLabels,
                    nestedLoopLabels,
                    location,
                    entryFile
            );
            return;
        }
        if (statement instanceof FunctionDeclarationStatement declarationStatement) {
            final FunctionDeclaration declaration = declarationStatement.declaration();
            if (declaration.async()) {
                throw strictBridgeFailure(
                        "Async function declarations are not supported in strict native subset (`"
                                + declaration.name()
                                + "`).",
                        location,
                        entryFile
                );
            }
            if (declaration.generator()) {
                throw strictBridgeFailure(
                        "Generator function declarations are not supported in strict native subset (`"
                                + declaration.name()
                                + "`).",
                        location,
                        entryFile
                );
            }
            final Set<String> functionBindings = new LinkedHashSet<>(bindings);
            functionBindings.addAll(declaration.parameters());
            predeclareStrictNativeFunctionBindings(functionBindings, declaration.body());
            for (Statement bodyStatement : declaration.body()) {
                validateStrictNativeStatement(
                        bodyStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        functionBindings,
                        true,
                        new LinkedHashSet<>(),
                        new LinkedHashSet<>(),
                        location,
                        entryFile
                );
            }
            return;
        }
        if (statement instanceof BreakStatement breakStatement) {
            if (breakStatement.label() != null && !activeLabels.contains(breakStatement.label())) {
                throw strictBridgeFailure(
                        "Unresolved labeled `break` target `" + breakStatement.label() + "` in strict native subset.",
                        location,
                        entryFile
                );
            }
            return;
        }
        if (statement instanceof ContinueStatement continueStatement) {
            if (continueStatement.label() != null && !loopLabels.contains(continueStatement.label())) {
                throw strictBridgeFailure(
                        "Labeled `continue` target `" + continueStatement.label()
                                + "` must resolve to an enclosing loop in strict native subset.",
                        location,
                        entryFile
                );
            }
            return;
        }
        if (statement instanceof SuperCallStatement) {
            throw strictBridgeFailure(
                    "`super(...)` is not supported in strict native subset.",
                    location,
                    entryFile
            );
        }
        throw strictBridgeFailure(
                "Unsupported class statement in strict native subset: " + statement.getClass().getSimpleName(),
                location,
                entryFile
        );
    }

    private static void validateStrictNativeAssignmentTarget(
            final Expression target,
            final boolean hasSuperClass,
            final Map<String, ClassDeclaration> strictClassesByName,
            final String currentClassName,
            final Set<String> fieldNames,
            final Set<String> methodNames,
            final Set<String> staticFieldNames,
            final Set<String> staticMethodNames,
            final Set<String> bindings,
            final boolean dynamicThisScope,
            final SourceLocation location,
            final Path entryFile
    ) {
        if (target instanceof VariableExpression variableExpression) {
            if (!bindings.contains(variableExpression.name())) {
                throw strictBridgeFailure(
                        "Unknown local binding assignment `" + variableExpression.name()
                                + "` in strict native subset.",
                        location,
                        entryFile
                );
            }
            return;
        }
        if (target instanceof MemberAccessExpression memberAccessExpression) {
            final boolean dynamicThisAssignment =
                    dynamicThisScope && memberAccessExpression.receiver() instanceof ThisExpression;
            final boolean instanceFieldAssignment = memberAccessExpression.receiver() instanceof ThisExpression
                    && fieldNames.contains(memberAccessExpression.member());
            final boolean staticFieldAssignment = isStrictNativeStaticFieldAccess(
                    memberAccessExpression.receiver(),
                    memberAccessExpression.member(),
                    currentClassName,
                    staticFieldNames,
                    strictClassesByName
            );
            if (!dynamicThisAssignment && !instanceFieldAssignment && !staticFieldAssignment) {
                validateStrictNativeExpression(
                        memberAccessExpression.receiver(),
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            return;
        }
        if (target instanceof CallExpression callExpression && isStrictNativeIndexAssignmentTarget(callExpression)) {
            validateStrictNativeExpression(
                    callExpression.arguments().get(0),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            validateStrictNativeExpression(
                    callExpression.arguments().get(1),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        throw strictBridgeFailure(
                "Unsupported assignment target in strict native subset.",
                location,
                entryFile
        );
    }

    private static boolean isStrictNativeIndexAssignmentTarget(final CallExpression callExpression) {
        return callExpression.callee() instanceof VariableExpression variableExpression
                && "__tsj_index_read".equals(variableExpression.name())
                && callExpression.arguments().size() == 2;
    }

    private static boolean isStrictNativeSupportedAssignmentOperator(final String operator) {
        return "=".equals(operator)
                || "&&=".equals(operator)
                || "||=".equals(operator)
                || "??=".equals(operator)
                || assignmentCompoundBinaryOperator(operator) != null;
    }

    private static String assignmentCompoundBinaryOperator(final String assignmentOperator) {
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

    private static void validateStrictNativeExpression(
            final Expression expression,
            final boolean hasSuperClass,
            final Map<String, ClassDeclaration> strictClassesByName,
            final String currentClassName,
            final Set<String> fieldNames,
            final Set<String> methodNames,
            final Set<String> staticFieldNames,
            final Set<String> staticMethodNames,
            final Set<String> bindings,
            final SourceLocation location,
            final Path entryFile
    ) {
        validateStrictNativeExpression(
                expression,
                hasSuperClass,
                strictClassesByName,
                currentClassName,
                fieldNames,
                methodNames,
                staticFieldNames,
                staticMethodNames,
                bindings,
                false,
                location,
                entryFile
        );
    }

    private static void validateStrictNativeExpression(
            final Expression expression,
            final boolean hasSuperClass,
            final Map<String, ClassDeclaration> strictClassesByName,
            final String currentClassName,
            final Set<String> fieldNames,
            final Set<String> methodNames,
            final Set<String> staticFieldNames,
            final Set<String> staticMethodNames,
            final Set<String> bindings,
            final boolean dynamicThisScope,
            final SourceLocation location,
            final Path entryFile
    ) {
        if (expression instanceof NumberLiteral
                || expression instanceof StringLiteral
                || expression instanceof BooleanLiteral
                || expression instanceof NullLiteral
                || expression instanceof UndefinedLiteral
                || expression instanceof ThisExpression) {
            return;
        }
        if (expression instanceof VariableExpression variableExpression) {
            if (!bindings.contains(variableExpression.name())) {
                throw strictBridgeFailure(
                        "Unknown identifier `" + variableExpression.name() + "` in strict native subset.",
                        location,
                        entryFile
                );
            }
            return;
        }
        if (expression instanceof MemberAccessExpression memberAccessExpression) {
            if (memberAccessExpression.receiver() instanceof ThisExpression) {
                if (dynamicThisScope) {
                    return;
                }
                if (!fieldNames.contains(memberAccessExpression.member())) {
                    throw strictBridgeFailure(
                            "Unknown class field `" + memberAccessExpression.member() + "` in strict native subset.",
                            location,
                            entryFile
                    );
                }
                return;
            }
            if (memberAccessExpression.receiver() instanceof VariableExpression variableExpression
                    && bindings.contains(variableExpression.name())) {
                return;
            }
            if (memberAccessExpression.receiver() instanceof VariableExpression variableExpression
                    && isStrictNativeStaticFieldAccess(
                    variableExpression,
                    memberAccessExpression.member(),
                    currentClassName,
                    staticFieldNames,
                    strictClassesByName
            )) {
                return;
            }
            validateStrictNativeExpression(
                    memberAccessExpression.receiver(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (expression instanceof UnaryExpression unaryExpression) {
            if (!STRICT_NATIVE_SUPPORTED_UNARY_OPERATORS.contains(unaryExpression.operator())) {
                throw strictBridgeFailure(
                        "Unsupported unary operator `" + unaryExpression.operator()
                                + "` in strict native subset.",
                        location,
                        entryFile
                );
            }
            validateStrictNativeExpression(
                    unaryExpression.expression(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (expression instanceof BinaryExpression binaryExpression) {
            if (!STRICT_NATIVE_SUPPORTED_BINARY_OPERATORS.contains(binaryExpression.operator())) {
                throw strictBridgeFailure(
                        "Unsupported binary operator `" + binaryExpression.operator()
                                + "` in strict native subset.",
                        location,
                        entryFile
                );
            }
            validateStrictNativeExpression(
                    binaryExpression.left(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            validateStrictNativeExpression(
                    binaryExpression.right(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (expression instanceof AssignmentExpression assignmentExpression) {
            if (!isStrictNativeSupportedAssignmentOperator(assignmentExpression.operator())) {
                throw strictBridgeFailure(
                        "Unsupported assignment operator `" + assignmentExpression.operator()
                                + "` in strict native subset.",
                        location,
                        entryFile
                );
            }
            validateStrictNativeAssignmentTarget(
                    assignmentExpression.target(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            validateStrictNativeExpression(
                    assignmentExpression.expression(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (expression instanceof ConditionalExpression conditionalExpression) {
            validateStrictNativeExpression(
                    conditionalExpression.condition(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            validateStrictNativeExpression(
                    conditionalExpression.whenTrue(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            validateStrictNativeExpression(
                    conditionalExpression.whenFalse(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (expression instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
            validateStrictNativeExpression(
                    optionalMemberAccessExpression.receiver(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            return;
        }
        if (expression instanceof ArrayLiteralExpression arrayLiteralExpression) {
            for (Expression element : arrayLiteralExpression.elements()) {
                validateStrictNativeExpression(
                        element,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            return;
        }
        if (expression instanceof ObjectLiteralExpression objectLiteralExpression) {
            for (ObjectLiteralEntry entry : objectLiteralExpression.entries()) {
                validateStrictNativeExpression(
                        entry.value(),
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            return;
        }
        if (expression instanceof OptionalCallExpression optionalCallExpression) {
            validateStrictNativeExpression(
                    optionalCallExpression.callee(),
                    hasSuperClass,
                    strictClassesByName,
                    currentClassName,
                    fieldNames,
                    methodNames,
                    staticFieldNames,
                    staticMethodNames,
                    bindings,
                    dynamicThisScope,
                    location,
                    entryFile
            );
            for (Expression argument : optionalCallExpression.arguments()) {
                validateStrictNativeExpression(
                        argument,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            return;
        }
        if (expression instanceof NewExpression newExpression) {
            final boolean strictClassConstructor = newExpression.constructor() instanceof VariableExpression variableExpression
                    && strictClassesByName.containsKey(variableExpression.name());
            if (!strictClassConstructor) {
                validateStrictNativeExpression(
                        newExpression.constructor(),
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            for (Expression argument : newExpression.arguments()) {
                validateStrictNativeExpression(
                        argument,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            return;
        }
        if (expression instanceof FunctionExpression functionExpression) {
            if (functionExpression.async()) {
                throw strictBridgeFailure(
                        "Async function expressions are not supported in strict native subset.",
                        location,
                        entryFile
                );
            }
            if (functionExpression.generator()) {
                throw strictBridgeFailure(
                        "Generator function expressions are not supported in strict native subset.",
                        location,
                        entryFile
                );
            }
            final Set<String> functionBindings = new LinkedHashSet<>(bindings);
            functionBindings.addAll(functionExpression.parameters());
            predeclareStrictNativeFunctionBindings(functionBindings, functionExpression.body());
            final boolean functionDynamicThisScope = functionExpression.thisMode() == FunctionThisMode.DYNAMIC
                    || dynamicThisScope;
            for (Statement bodyStatement : functionExpression.body()) {
                validateStrictNativeStatement(
                        bodyStatement,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        functionBindings,
                        functionDynamicThisScope,
                        location,
                        entryFile
                );
            }
            return;
        }
        if (expression instanceof CallExpression callExpression) {
            if (callExpression.callee() instanceof VariableExpression variableExpression
                    && "__tsj_super_invoke".equals(variableExpression.name())) {
                if (!hasSuperClass) {
                    throw strictBridgeFailure(
                            "`super.member(...)` is only valid inside derived strict-native classes.",
                            location,
                            entryFile
                    );
                }
                if (callExpression.arguments().isEmpty()
                        || !(callExpression.arguments().getFirst() instanceof StringLiteral)) {
                    throw strictBridgeFailure(
                            "Normalized super-member calls require a string-literal method token in strict native subset.",
                            location,
                            entryFile
                    );
                }
                for (int index = 1; index < callExpression.arguments().size(); index++) {
                    validateStrictNativeExpression(
                            callExpression.arguments().get(index),
                            hasSuperClass,
                            strictClassesByName,
                            currentClassName,
                            fieldNames,
                            methodNames,
                            staticFieldNames,
                            staticMethodNames,
                            bindings,
                            dynamicThisScope,
                            location,
                            entryFile
                    );
                }
                return;
            }
            if (callExpression.callee() instanceof VariableExpression variableExpression) {
                if ("__tsj_for_of_values".equals(variableExpression.name())
                        || "__tsj_for_in_keys".equals(variableExpression.name())) {
                    if (callExpression.arguments().size() != 1) {
                        throw strictBridgeFailure(
                                "Collection helper `" + variableExpression.name()
                                        + "` requires exactly one argument in strict native subset.",
                                location,
                                entryFile
                        );
                    }
                    validateStrictNativeExpression(
                            callExpression.arguments().getFirst(),
                            hasSuperClass,
                            strictClassesByName,
                            currentClassName,
                            fieldNames,
                            methodNames,
                            staticFieldNames,
                            staticMethodNames,
                            bindings,
                            dynamicThisScope,
                            location,
                            entryFile
                    );
                    return;
                }
                if ("__tsj_index_read".equals(variableExpression.name())
                        || "__tsj_optional_index_read".equals(variableExpression.name())) {
                    if (callExpression.arguments().size() != 2) {
                        throw strictBridgeFailure(
                                "Index helper `" + variableExpression.name()
                                        + "` requires exactly two arguments in strict native subset.",
                                location,
                                entryFile
                        );
                    }
                    for (Expression argument : callExpression.arguments()) {
                        validateStrictNativeExpression(
                                argument,
                                hasSuperClass,
                                strictClassesByName,
                                currentClassName,
                                fieldNames,
                                methodNames,
                                staticFieldNames,
                                staticMethodNames,
                                bindings,
                                dynamicThisScope,
                                location,
                                entryFile
                        );
                    }
                    return;
                }
                if (!bindings.contains(variableExpression.name())) {
                    throw strictBridgeFailure(
                            "Unknown callable identifier `" + variableExpression.name() + "` in strict native subset.",
                            location,
                            entryFile
                    );
                }
                for (Expression argument : callExpression.arguments()) {
                    validateStrictNativeExpression(
                            argument,
                            hasSuperClass,
                            strictClassesByName,
                            currentClassName,
                            fieldNames,
                            methodNames,
                            staticFieldNames,
                            staticMethodNames,
                            bindings,
                            dynamicThisScope,
                            location,
                            entryFile
                    );
                }
                return;
            }
            if (!(callExpression.callee() instanceof MemberAccessExpression memberAccessExpression)) {
                throw strictBridgeFailure(
                        "Only member call expressions are supported in strict native subset.",
                        location,
                        entryFile
                );
            }
            final boolean directThisMethodCall = memberAccessExpression.receiver() instanceof ThisExpression
                    && !dynamicThisScope
                    && methodNames.contains(memberAccessExpression.member());
            final boolean directStaticMethodCall = memberAccessExpression.receiver() instanceof VariableExpression variableExpression
                    && isStrictNativeStaticMethodAccess(
                    variableExpression,
                    memberAccessExpression.member(),
                    currentClassName,
                    staticMethodNames,
                    strictClassesByName
            );
            if (!directThisMethodCall && !directStaticMethodCall) {
                validateStrictNativeExpression(
                        memberAccessExpression.receiver(),
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            for (Expression argument : callExpression.arguments()) {
                validateStrictNativeExpression(
                        argument,
                        hasSuperClass,
                        strictClassesByName,
                        currentClassName,
                        fieldNames,
                        methodNames,
                        staticFieldNames,
                        staticMethodNames,
                        bindings,
                        dynamicThisScope,
                        location,
                        entryFile
                );
            }
            return;
        }
        throw strictBridgeFailure(
                "Unsupported class expression in strict native subset: " + expression.getClass().getSimpleName(),
                location,
                entryFile
        );
    }

    private static boolean isStrictNativeStaticFieldAccess(
            final Expression receiver,
            final String fieldName,
            final String currentClassName,
            final Set<String> currentClassStaticFieldNames,
            final Map<String, ClassDeclaration> strictClassesByName
    ) {
        if (!(receiver instanceof VariableExpression variableExpression)) {
            return false;
        }
        if (currentClassName.equals(variableExpression.name())) {
            return currentClassStaticFieldNames.contains(fieldName);
        }
        final ClassDeclaration targetClass = strictClassesByName.get(variableExpression.name());
        if (targetClass == null) {
            return false;
        }
        for (ClassField field : targetClass.staticFields()) {
            if (field.name().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStrictNativeStaticMethodAccess(
            final Expression receiver,
            final String methodName,
            final String currentClassName,
            final Set<String> currentClassStaticMethodNames,
            final Map<String, ClassDeclaration> strictClassesByName
    ) {
        if (!(receiver instanceof VariableExpression variableExpression)) {
            return false;
        }
        if (currentClassName.equals(variableExpression.name())) {
            return currentClassStaticMethodNames.contains(methodName);
        }
        final ClassDeclaration targetClass = strictClassesByName.get(variableExpression.name());
        if (targetClass == null) {
            return false;
        }
        for (ClassMethod method : targetClass.staticMethods()) {
            if (method.name().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static JvmCompilationException strictBridgeFailure(
            final String detail,
            final SourceLocation location,
            final Path entryFile
    ) {
        final Path sourcePath = location == null
                ? entryFile.toAbsolutePath().normalize()
                : location.sourceFile().toAbsolutePath().normalize();
        final int line = location == null ? 1 : location.line();
        final int column = location == null ? 1 : location.column();
        return new JvmCompilationException(
                "TSJ-STRICT-BRIDGE",
                "Strict mode cannot lower class member to JVM-native subset: "
                        + detail
                        + " [featureId="
                        + FEATURE_STRICT_BRIDGE
                        + "]. Guidance: "
                        + GUIDANCE_STRICT_BRIDGE,
                line,
                column,
                sourcePath.toString(),
                FEATURE_STRICT_BRIDGE,
                GUIDANCE_STRICT_BRIDGE
        );
    }

    private static List<MetadataCarrierDeclaration> resolveMetadataCarrierDeclarations(
            final Path entryFile,
            final List<TopLevelClassDeclaration> topLevelClassDeclarations
    ) {
        if (topLevelClassDeclarations.isEmpty()) {
            return List.of();
        }

        final TsFrontendDecoratorModelExtractor.ExtractionResult extractionResult;
        try {
            final TsFrontendDecoratorModelExtractor extractor = TsFrontendDecoratorModelExtractor.createClasspathAware(
                    Set.of(),
                    buildJavacClasspath(),
                    false,
                    "tsj-jvm-bytecode-compiler"
            );
            extractionResult = extractor.extractWithImportedDecoratorBindings(entryFile);
        } catch (final JvmCompilationException exception) {
            if (TsDecoratorClasspathResolver.FEATURE_ID.equals(exception.featureId())) {
                throw exception;
            }
            return topLevelClassDeclarations.stream()
                    .map(value -> new MetadataCarrierDeclaration(
                            value.declaration(),
                            null,
                            Map.of()
                    ))
                    .toList();
        }

        final Map<Path, Map<String, String>> importedBindingsByFile = extractionResult.importedDecoratorBindingsByFile();
        final List<TsDecoratedClass> decoratedClasses = extractionResult.model().classes();
        final Map<ClassLookupKey, List<TsDecoratedClass>> decoratedByKey = new LinkedHashMap<>();
        final Map<String, List<TsDecoratedClass>> decoratedByName = new LinkedHashMap<>();
        for (TsDecoratedClass decoratedClass : decoratedClasses) {
            decoratedByKey.computeIfAbsent(
                    new ClassLookupKey(decoratedClass.sourceFile(), decoratedClass.className()),
                    ignored -> new ArrayList<>()
            ).add(decoratedClass);
            decoratedByName.computeIfAbsent(decoratedClass.className(), ignored -> new ArrayList<>()).add(decoratedClass);
        }

        final IdentityHashMap<TsDecoratedClass, Boolean> consumed = new IdentityHashMap<>();
        final List<MetadataCarrierDeclaration> declarations = new ArrayList<>();
        for (TopLevelClassDeclaration topLevelClass : topLevelClassDeclarations) {
            final Path sourcePath = topLevelClass.sourceLocation() == null
                    ? entryFile
                    : topLevelClass.sourceLocation().sourceFile().toAbsolutePath().normalize();
            final ClassLookupKey key = new ClassLookupKey(sourcePath, topLevelClass.declaration().name());
            TsDecoratedClass decoratedClass = selectDecoratedClass(
                    decoratedByKey.get(key),
                    topLevelClass.sourceLocation(),
                    consumed
            );
            if (decoratedClass == null) {
                decoratedClass = selectDecoratedClass(
                        decoratedByName.get(topLevelClass.declaration().name()),
                        topLevelClass.sourceLocation(),
                        consumed
                );
            }
            declarations.add(new MetadataCarrierDeclaration(
                    topLevelClass.declaration(),
                    decoratedClass,
                    importedBindingsByFile.getOrDefault(sourcePath, Map.of())
            ));
        }
        return List.copyOf(declarations);
    }

    private static TsDecoratedClass selectDecoratedClass(
            final List<TsDecoratedClass> candidates,
            final SourceLocation sourceLocation,
            final IdentityHashMap<TsDecoratedClass, Boolean> consumed
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        final Integer targetLine = sourceLocation == null ? null : sourceLocation.line();
        TsDecoratedClass selected = null;
        int bestDistance = Integer.MAX_VALUE;
        for (TsDecoratedClass candidate : candidates) {
            if (consumed.containsKey(candidate)) {
                continue;
            }
            if (targetLine == null) {
                selected = candidate;
                break;
            }
            final int distance = Math.abs(candidate.line() - targetLine);
            if (selected == null || distance < bestDistance) {
                selected = candidate;
                bestDistance = distance;
            }
        }
        if (selected != null) {
            consumed.put(selected, Boolean.TRUE);
        }
        return selected;
    }

    private static List<MetadataCarrierDeclaration> filterRuntimeCarrierMetadataDeclarations(
            final List<MetadataCarrierDeclaration> metadataCarrierDeclarations,
            final StrictNativeClassLoweringPlan strictLoweringPlan,
            final Path entryFile
    ) {
        if (metadataCarrierDeclarations.isEmpty()
                || strictLoweringPlan.loweringPath() != StrictLoweringPath.JVM_NATIVE_CLASS_SUBSET
                || strictLoweringPlan.nativeClasses().isEmpty()) {
            return metadataCarrierDeclarations;
        }
        final Set<ClassLookupKey> strictNativeKeys = new LinkedHashSet<>();
        for (TopLevelClassDeclaration topLevelClass : strictLoweringPlan.nativeClasses()) {
            final Path sourcePath = topLevelClass.sourceLocation() == null
                    ? entryFile.toAbsolutePath().normalize()
                    : topLevelClass.sourceLocation().sourceFile().toAbsolutePath().normalize();
            strictNativeKeys.add(new ClassLookupKey(sourcePath, topLevelClass.declaration().name()));
        }
        final List<MetadataCarrierDeclaration> filtered = new ArrayList<>();
        for (MetadataCarrierDeclaration declaration : metadataCarrierDeclarations) {
            final TsDecoratedClass decoratedClass = declaration.decoratedClass();
            final Path sourcePath = decoratedClass == null
                    ? entryFile.toAbsolutePath().normalize()
                    : decoratedClass.sourceFile().toAbsolutePath().normalize();
            final ClassLookupKey key = new ClassLookupKey(sourcePath, declaration.classDeclaration().name());
            if (!strictNativeKeys.contains(key)) {
                filtered.add(declaration);
            }
        }
        return List.copyOf(filtered);
    }

    private static List<Path> writeMetadataCarrierSources(
            final Path generatedSourceRoot,
            final List<MetadataCarrierDeclaration> classDeclarations,
            final AnnotationRenderContext annotationRenderContext
    ) throws IOException {
        if (classDeclarations.isEmpty()) {
            return List.of();
        }
        final Path metadataSourceDir = generatedSourceRoot.resolve(METADATA_CARRIER_PACKAGE.replace('.', '/'));
        Files.createDirectories(metadataSourceDir);

        final Map<String, Integer> carrierNameCounts = new LinkedHashMap<>();
        final List<Path> sourceFiles = new ArrayList<>();
        for (MetadataCarrierDeclaration declaration : classDeclarations) {
            final String baseName = sanitizeJavaIdentifier(declaration.classDeclaration().name()) + METADATA_CARRIER_SUFFIX;
            final String carrierSimpleName = allocateUniqueName(carrierNameCounts, baseName);
            final Path sourceFile = metadataSourceDir.resolve(carrierSimpleName + ".java");
            Files.writeString(
                    sourceFile,
                    renderMetadataCarrierSource(carrierSimpleName, declaration, annotationRenderContext),
                    UTF_8
            );
            sourceFiles.add(sourceFile);
        }
        return List.copyOf(sourceFiles);
    }

    private static String renderMetadataCarrierSource(
            final String carrierSimpleName,
            final MetadataCarrierDeclaration declaration,
            final AnnotationRenderContext annotationRenderContext
    ) {
        final StringBuilder builder = new StringBuilder();
        final ClassDeclaration classDeclaration = declaration.classDeclaration();
        final TsDecoratedClass decoratedClass = declaration.decoratedClass();
        final Map<String, String> importedDecoratorBindings = declaration.importedDecoratorBindings();

        builder.append("package ").append(METADATA_CARRIER_PACKAGE).append(";\n\n");
        appendAnnotationLines(
                builder,
                "",
                decoratedClass == null ? List.of() : decoratedClass.decorators(),
                importedDecoratorBindings,
                annotationRenderContext
        );
        builder.append("public final class ").append(carrierSimpleName).append(" {\n");

        final Map<String, List<TsDecoratedField>> decoratedFieldsByName = new LinkedHashMap<>();
        if (decoratedClass != null) {
            for (TsDecoratedField field : decoratedClass.fields()) {
                decoratedFieldsByName.computeIfAbsent(field.fieldName(), ignored -> new ArrayList<>()).add(field);
            }
        }
        final Map<String, Integer> fieldNameCounts = new LinkedHashMap<>();
        for (String fieldName : classDeclaration.fieldNames()) {
            appendAnnotationLines(
                    builder,
                    "  ",
                    resolveFieldDecorators(decoratedFieldsByName.get(fieldName)),
                    importedDecoratorBindings,
                    annotationRenderContext
            );
            final String memberName = allocateUniqueName(fieldNameCounts, sanitizeJavaIdentifier(fieldName));
            builder.append("  public Object ").append(memberName).append(";\n");
        }
        if (!classDeclaration.fieldNames().isEmpty()) {
            builder.append('\n');
        }

        final ClassMethod constructorMethod = classDeclaration.constructorMethod();
        final TsDecoratedMethod decoratedConstructor = constructorMethod == null
                ? null
                : findDecoratedMethod(
                decoratedClass == null ? List.of() : decoratedClass.methods(),
                true,
                constructorMethod.name(),
                constructorMethod.parameters().size(),
                null
        );
        if (constructorMethod == null) {
            builder.append("  public ").append(carrierSimpleName).append("() {\n");
            builder.append("  }\n\n");
        } else {
            appendAnnotationLines(
                    builder,
                    "  ",
                    decoratedConstructor == null ? List.of() : decoratedConstructor.decorators(),
                    importedDecoratorBindings,
                    annotationRenderContext
            );
            builder.append("  public ").append(carrierSimpleName).append("(");
            builder.append(renderParameters(
                    constructorMethod.parameters(),
                    decoratedConstructor == null ? List.of() : decoratedConstructor.parameters(),
                    importedDecoratorBindings,
                    annotationRenderContext
            ));
            builder.append(") {\n");
            builder.append("  }\n\n");
        }

        final IdentityHashMap<TsDecoratedMethod, Boolean> consumedMethods = new IdentityHashMap<>();
        if (decoratedConstructor != null) {
            consumedMethods.put(decoratedConstructor, Boolean.TRUE);
        }
        final Map<String, Integer> methodNameCounts = new LinkedHashMap<>();
        for (ClassMethod method : classDeclaration.methods()) {
            final TsDecoratedMethod decoratedMethod = findDecoratedMethod(
                    decoratedClass == null ? List.of() : decoratedClass.methods(),
                    false,
                    method.name(),
                    method.parameters().size(),
                    consumedMethods
            );
            appendAnnotationLines(
                    builder,
                    "  ",
                    decoratedMethod == null ? List.of() : decoratedMethod.decorators(),
                    importedDecoratorBindings,
                    annotationRenderContext
            );
            String methodName = sanitizeJavaIdentifier(method.name());
            methodName = allocateUniqueName(methodNameCounts, methodName);
            builder.append("  public Object ").append(methodName).append("(");
            builder.append(renderParameters(
                    method.parameters(),
                    decoratedMethod == null ? List.of() : decoratedMethod.parameters(),
                    importedDecoratorBindings,
                    annotationRenderContext
            ));
            builder.append(") {\n");
            builder.append("    return null;\n");
            builder.append("  }\n\n");
        }

        builder.append("}\n");
        return builder.toString();
    }

    private static List<TsDecoratorUse> resolveFieldDecorators(final List<TsDecoratedField> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.getFirst().decorators();
    }

    private static String resolveFieldTypeAnnotation(final List<TsDecoratedField> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.getFirst().typeAnnotation();
    }

    private static boolean hasResolvedDecorator(
            final List<TsDecoratorUse> decorators,
            final Map<String, String> importedDecoratorBindings,
            final String annotationClassName
    ) {
        if (annotationClassName == null || annotationClassName.isBlank()) {
            return false;
        }
        for (TsDecoratorUse decorator : decorators) {
            if (annotationClassName.equals(importedDecoratorBindings.get(decorator.name()))) {
                return true;
            }
        }
        return false;
    }

    private static TsDecoratedMethod findDecoratedMethod(
            final List<TsDecoratedMethod> candidates,
            final boolean constructor,
            final String methodName,
            final int parameterCount,
            final IdentityHashMap<TsDecoratedMethod, Boolean> consumed
    ) {
        for (TsDecoratedMethod candidate : candidates) {
            if (candidate.constructor() != constructor) {
                continue;
            }
            if (consumed != null && consumed.containsKey(candidate)) {
                continue;
            }
            if (!candidate.methodName().equals(methodName) || candidate.parameterCount() != parameterCount) {
                continue;
            }
            if (consumed != null) {
                consumed.put(candidate, Boolean.TRUE);
            }
            return candidate;
        }
        return null;
    }

    private static String renderParameters(
            final List<String> parameterNames,
            final List<TsDecoratedParameter> decoratedParameters,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext
    ) {
        if (parameterNames.isEmpty()) {
            return "";
        }
        final Map<Integer, List<TsDecoratorUse>> decoratorsByParameterIndex = new LinkedHashMap<>();
        for (TsDecoratedParameter decoratedParameter : decoratedParameters) {
            decoratorsByParameterIndex.put(decoratedParameter.index(), decoratedParameter.decorators());
        }
        final StringBuilder builder = new StringBuilder();
        final Map<String, Integer> parameterNameCounts = new LinkedHashMap<>();
        for (int index = 0; index < parameterNames.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            final String inlineAnnotations = renderInlineAnnotations(
                    decoratorsByParameterIndex.getOrDefault(index, List.of()),
                    importedDecoratorBindings,
                    annotationRenderContext
            );
            if (!inlineAnnotations.isBlank()) {
                builder.append(inlineAnnotations).append(' ');
            }
            final String parameterName = allocateUniqueName(
                    parameterNameCounts,
                    sanitizeJavaIdentifier(parameterNames.get(index))
            );
            builder.append("Object ").append(parameterName);
        }
        return builder.toString();
    }

    private static String renderInlineAnnotations(
            final List<TsDecoratorUse> decorators,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext
    ) {
        final StringBuilder builder = new StringBuilder();
        for (TsDecoratorUse decorator : decorators) {
            final String annotationClassName = importedDecoratorBindings.get(decorator.name());
            if (annotationClassName == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(renderResolvedAnnotation(
                    annotationClassName,
                    decorator.rawArgs(),
                    importedDecoratorBindings,
                    annotationRenderContext
            ));
        }
        return builder.toString();
    }

    private static void appendAnnotationLines(
            final StringBuilder builder,
            final String indent,
            final List<TsDecoratorUse> decorators,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext
    ) {
        for (TsDecoratorUse decorator : decorators) {
            final String annotationClassName = importedDecoratorBindings.get(decorator.name());
            if (annotationClassName == null) {
                continue;
            }
            builder.append(indent)
                    .append(renderResolvedAnnotation(
                            annotationClassName,
                            decorator.rawArgs(),
                            importedDecoratorBindings,
                            annotationRenderContext
                    ))
                    .append('\n');
        }
    }

    private static String renderResolvedAnnotation(
            final String annotationClassName,
            final String rawArgs,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext
    ) {
        final String renderedArgs = renderAnnotationArguments(
                annotationClassName,
                rawArgs,
                importedDecoratorBindings,
                annotationRenderContext
        );
        if (renderedArgs.isEmpty()) {
            return "@" + annotationClassName;
        }
        return "@" + annotationClassName + "(" + renderedArgs + ")";
    }

    private static String renderAnnotationArguments(
            final String annotationClassName,
            final String rawArgs,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext
    ) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return "";
        }
        final List<String> segments = splitTopLevelSegments(rawArgs, ',');
        if (segments.isEmpty()) {
            return "";
        }
        if (segments.size() == 1) {
            final String single = segments.getFirst().trim();
            final String objectAssignments = renderObjectLiteralAssignments(
                    annotationClassName,
                    single,
                    importedDecoratorBindings,
                    annotationRenderContext
            );
            if (!objectAssignments.isEmpty()) {
                return objectAssignments;
            }
        }

        final List<AnnotationAssignment> namedAssignments = new ArrayList<>();
        boolean sawNamed = false;
        for (String segment : segments) {
            final AnnotationAssignment assignment = parseAnnotationAssignment(segment);
            if (assignment == null) {
                continue;
            }
            sawNamed = true;
            final String renderedValue = renderAnnotationValue(
                    assignment.value(),
                    importedDecoratorBindings,
                    annotationRenderContext,
                    annotationRenderContext.resolveMemberTarget(annotationClassName, assignment.name())
            );
            if (renderedValue.isEmpty()) {
                return "";
            }
            namedAssignments.add(new AnnotationAssignment(assignment.name(), renderedValue));
        }
        if (sawNamed) {
            if (namedAssignments.size() != segments.size()) {
                return "";
            }
            final StringBuilder builder = new StringBuilder();
            for (int index = 0; index < namedAssignments.size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                final AnnotationAssignment assignment = namedAssignments.get(index);
                builder.append(assignment.name()).append(" = ").append(assignment.value());
            }
            return builder.toString();
        }

        if (segments.size() == 1) {
            return renderAnnotationValue(
                    segments.getFirst(),
                    importedDecoratorBindings,
                    annotationRenderContext,
                    AnnotationMemberTarget.none()
            );
        }
        final List<String> renderedValues = new ArrayList<>();
        for (String segment : segments) {
            final String renderedValue = renderAnnotationValue(
                    segment,
                    importedDecoratorBindings,
                    annotationRenderContext,
                    AnnotationMemberTarget.none()
            );
            if (renderedValue.isEmpty()) {
                return "";
            }
            renderedValues.add(renderedValue);
        }
        return "{" + String.join(", ", renderedValues) + "}";
    }

    private static String renderObjectLiteralAssignments(
            final String annotationClassName,
            final String candidate,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext
    ) {
        final String trimmed = candidate.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return "";
        }
        final String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return "";
        }
        final List<String> entries = splitTopLevelSegments(body, ',');
        if (entries.isEmpty()) {
            return "";
        }
        final List<AnnotationAssignment> assignments = new ArrayList<>();
        for (String entry : entries) {
            final AnnotationAssignment assignment = parseAnnotationAssignment(entry);
            if (assignment == null) {
                return "";
            }
            final String renderedValue = renderAnnotationValue(
                    assignment.value(),
                    importedDecoratorBindings,
                    annotationRenderContext,
                    annotationRenderContext.resolveMemberTarget(annotationClassName, assignment.name())
            );
            if (renderedValue.isEmpty()) {
                return "";
            }
            assignments.add(new AnnotationAssignment(assignment.name(), renderedValue));
        }
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < assignments.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            final AnnotationAssignment assignment = assignments.get(index);
            builder.append(assignment.name()).append(" = ").append(assignment.value());
        }
        return builder.toString();
    }

    private static AnnotationAssignment parseAnnotationAssignment(final String rawSegment) {
        final String segment = rawSegment == null ? "" : rawSegment.trim();
        if (segment.isEmpty()) {
            return null;
        }
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        char quote = 0;
        for (int index = 0; index < segment.length(); index++) {
            final char value = segment.charAt(index);
            if (inString) {
                if (value == '\\') {
                    index++;
                    continue;
                }
                if (value == quote) {
                    inString = false;
                }
                continue;
            }
            if (value == '\'' || value == '"' || value == '`') {
                inString = true;
                quote = value;
                continue;
            }
            if (value == '(') {
                parenDepth++;
                continue;
            }
            if (value == ')') {
                parenDepth--;
                continue;
            }
            if (value == '[') {
                bracketDepth++;
                continue;
            }
            if (value == ']') {
                bracketDepth--;
                continue;
            }
            if (value == '{') {
                braceDepth++;
                continue;
            }
            if (value == '}') {
                braceDepth--;
                continue;
            }
            if ((value == ':' || value == '=')
                    && parenDepth == 0
                    && bracketDepth == 0
                    && braceDepth == 0) {
                final String name = segment.substring(0, index).trim();
                final String rawValue = segment.substring(index + 1).trim();
                if (!isValidTsIdentifier(name) || rawValue.isEmpty()) {
                    return null;
                }
                return new AnnotationAssignment(name, rawValue);
            }
        }
        return null;
    }

    private static String renderAnnotationValue(
            final String rawValue,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext,
            final AnnotationMemberTarget memberTarget
    ) {
        if (rawValue == null) {
            return "";
        }
        final String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2) {
            return convertSingleQuotedString(trimmed);
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed;
        }
        if (memberTarget.isNestedAnnotation()) {
            final String nestedValue = renderNestedAnnotationValue(
                    memberTarget.annotationClassName(),
                    trimmed,
                    importedDecoratorBindings,
                    annotationRenderContext
            );
            if (!nestedValue.isEmpty()) {
                return nestedValue;
            }
        }
        final String classLiteralHelper = renderClassLiteralHelper(trimmed);
        if (!classLiteralHelper.isEmpty()) {
            return classLiteralHelper;
        }
        final String enumHelper = renderEnumHelper(trimmed);
        if (!enumHelper.isEmpty()) {
            return enumHelper;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            final String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) {
                return "{}";
            }
            final List<String> values = splitTopLevelSegments(inner, ',');
            final List<String> rendered = new ArrayList<>();
            final AnnotationMemberTarget elementTarget = memberTarget.arrayElementTarget();
            for (String value : values) {
                final String renderedValue = renderAnnotationValue(
                        value,
                        importedDecoratorBindings,
                        annotationRenderContext,
                        elementTarget
                );
                if (renderedValue.isEmpty()) {
                    return "";
                }
                rendered.add(renderedValue);
            }
            return "{" + String.join(", ", rendered) + "}";
        }
        if ("undefined".equals(trimmed)) {
            return "null";
        }
        final String importedJavaReference = renderImportedJavaAnnotationValue(trimmed, importedDecoratorBindings);
        if (!importedJavaReference.isEmpty()) {
            return importedJavaReference;
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return "";
        }
        return trimmed;
    }

    private static String renderNestedAnnotationValue(
            final String nestedAnnotationClassName,
            final String rawValue,
            final Map<String, String> importedDecoratorBindings,
            final AnnotationRenderContext annotationRenderContext
    ) {
        final String trimmed = rawValue == null ? "" : rawValue.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return "";
        }
        final String renderedArgs = renderObjectLiteralAssignments(
                nestedAnnotationClassName,
                trimmed,
                importedDecoratorBindings,
                annotationRenderContext
        );
        if (renderedArgs.isEmpty()) {
            return "@" + nestedAnnotationClassName;
        }
        return "@" + nestedAnnotationClassName + "(" + renderedArgs + ")";
    }

    private static String renderClassLiteralHelper(final String rawValue) {
        return renderHelperStringArgument(rawValue, "classOf", ".class");
    }

    private static String renderEnumHelper(final String rawValue) {
        return renderHelperStringArgument(rawValue, "enum", "");
    }

    private static String renderHelperStringArgument(
            final String rawValue,
            final String helperName,
            final String suffix
    ) {
        if (!rawValue.startsWith(helperName + "(") || !rawValue.endsWith(")")) {
            return "";
        }
        final String inner = rawValue.substring(helperName.length() + 1, rawValue.length() - 1).trim();
        if ((inner.startsWith("'") && inner.endsWith("'")) || (inner.startsWith("\"") && inner.endsWith("\""))) {
            final String literal = inner.substring(1, inner.length() - 1);
            if (!literal.isEmpty()) {
                return literal + suffix;
            }
        }
        return "";
    }

    private static String renderImportedJavaAnnotationValue(
            final String rawValue,
            final Map<String, String> importedDecoratorBindings
    ) {
        if (rawValue.isEmpty() || importedDecoratorBindings == null || importedDecoratorBindings.isEmpty()) {
            return "";
        }
        if (isValidTsIdentifier(rawValue)) {
            final String importedClassName = importedDecoratorBindings.get(rawValue);
            return importedClassName == null ? "" : importedClassName + ".class";
        }
        final int firstDot = rawValue.indexOf('.');
        if (firstDot <= 0) {
            return "";
        }
        final String rootIdentifier = rawValue.substring(0, firstDot);
        if (!isValidTsIdentifier(rootIdentifier)) {
            return "";
        }
        final String importedClassName = importedDecoratorBindings.get(rootIdentifier);
        if (importedClassName == null) {
            return "";
        }
        return importedClassName + rawValue.substring(firstDot);
    }

    private enum AnnotationMemberTargetKind {
        NONE,
        NESTED_ANNOTATION,
        NESTED_ANNOTATION_ARRAY
    }

    private record AnnotationMemberTarget(
            AnnotationMemberTargetKind kind,
            String annotationClassName
    ) {
        private AnnotationMemberTarget {
            kind = Objects.requireNonNull(kind, "kind");
        }

        private static AnnotationMemberTarget none() {
            return new AnnotationMemberTarget(AnnotationMemberTargetKind.NONE, null);
        }

        private boolean isNestedAnnotation() {
            return kind == AnnotationMemberTargetKind.NESTED_ANNOTATION;
        }

        private AnnotationMemberTarget arrayElementTarget() {
            if (kind == AnnotationMemberTargetKind.NESTED_ANNOTATION_ARRAY) {
                return new AnnotationMemberTarget(AnnotationMemberTargetKind.NESTED_ANNOTATION, annotationClassName);
            }
            return none();
        }
    }

    private record NullabilityAnnotationFamily(
            String nonNullAnnotationClassName,
            String nullableAnnotationClassName
    ) {
        private NullabilityAnnotationFamily {
            nonNullAnnotationClassName = Objects.requireNonNull(
                    nonNullAnnotationClassName,
                    "nonNullAnnotationClassName"
            );
            nullableAnnotationClassName = Objects.requireNonNull(
                    nullableAnnotationClassName,
                    "nullableAnnotationClassName"
            );
        }
    }

    private static final class AnnotationRenderContext {
        private static final List<NullabilityAnnotationFamily> NULLABILITY_ANNOTATION_FAMILIES = List.of(
                new NullabilityAnnotationFamily(
                        "org.jetbrains.annotations.NotNull",
                        "org.jetbrains.annotations.Nullable"
                ),
                new NullabilityAnnotationFamily(
                        "javax.annotation.Nonnull",
                        "javax.annotation.Nullable"
                ),
                new NullabilityAnnotationFamily(
                        "androidx.annotation.NonNull",
                        "androidx.annotation.Nullable"
                ),
                new NullabilityAnnotationFamily(
                        "org.checkerframework.checker.nullness.qual.NonNull",
                        "org.checkerframework.checker.nullness.qual.Nullable"
                )
        );

        private final JavaSymbolTable symbolTable;
        private final JavaSignatureParser signatureParser;
        private final Map<String, Map<String, AnnotationMemberTarget>> nestedTargetsByAnnotationClass;
        private final NullabilityAnnotationFamily nullabilityAnnotationFamily;

        private AnnotationRenderContext(final JavaSymbolTable symbolTable) {
            this.symbolTable = Objects.requireNonNull(symbolTable, "symbolTable");
            this.signatureParser = new JavaSignatureParser();
            this.nestedTargetsByAnnotationClass = new LinkedHashMap<>();
            this.nullabilityAnnotationFamily = selectNullabilityAnnotationFamily(symbolTable);
        }

        private AnnotationMemberTarget resolveMemberTarget(
                final String annotationClassName,
                final String memberName
        ) {
            if (annotationClassName == null || annotationClassName.isBlank() || memberName == null || memberName.isBlank()) {
                return AnnotationMemberTarget.none();
            }
            final Map<String, AnnotationMemberTarget> targets = nestedTargetsByAnnotationClass.computeIfAbsent(
                    annotationClassName,
                    this::loadNestedTargets
            );
            return targets.getOrDefault(memberName, AnnotationMemberTarget.none());
        }

        private Map<String, AnnotationMemberTarget> loadNestedTargets(final String annotationClassName) {
            final Optional<JavaClassfileReader.RawClassInfo> rawClassInfo = symbolTable.resolveClass(annotationClassName);
            if (rawClassInfo.isEmpty()) {
                return Map.of();
            }
            final Map<String, AnnotationMemberTarget> targets = new LinkedHashMap<>();
            for (JavaClassfileReader.RawMethodInfo method : rawClassInfo.get().methods()) {
                if (method.name() == null
                        || method.name().startsWith("<")
                        || method.descriptor() == null
                        || !method.descriptor().startsWith("()")) {
                    continue;
                }
                final JavaTypeModel.JMethodSig methodSig = signatureParser.parseMethodSignatureOrDescriptor(
                        method.signature(),
                        method.descriptor()
                );
                final AnnotationMemberTarget target = toNestedAnnotationTarget(methodSig.returnType());
                if (target.kind() != AnnotationMemberTargetKind.NONE) {
                    targets.put(method.name(), target);
                }
            }
            return Map.copyOf(targets);
        }

        private AnnotationMemberTarget toNestedAnnotationTarget(final JavaTypeModel.JType type) {
            if (type instanceof JavaTypeModel.ClassType classType) {
                return annotationTargetForInternalName(classType.internalName(), false);
            }
            if (type instanceof JavaTypeModel.ArrayType arrayType
                    && arrayType.elementType() instanceof JavaTypeModel.ClassType classType) {
                return annotationTargetForInternalName(classType.internalName(), true);
            }
            return AnnotationMemberTarget.none();
        }

        private AnnotationMemberTarget annotationTargetForInternalName(
                final String internalName,
                final boolean array
        ) {
            if (internalName == null || internalName.isBlank()) {
                return AnnotationMemberTarget.none();
            }
            final String fqcn = internalName.replace('/', '.');
            final Optional<JavaClassfileReader.RawClassInfo> nestedType = symbolTable.resolveClass(fqcn);
            if (nestedType.isEmpty() || (nestedType.get().accessFlags() & JAVA_ACC_ANNOTATION) == 0) {
                return AnnotationMemberTarget.none();
            }
            return new AnnotationMemberTarget(
                    array ? AnnotationMemberTargetKind.NESTED_ANNOTATION_ARRAY : AnnotationMemberTargetKind.NESTED_ANNOTATION,
                    fqcn
            );
        }

        private String nonNullAnnotationClassName() {
            return nullabilityAnnotationFamily == null ? null : nullabilityAnnotationFamily.nonNullAnnotationClassName();
        }

        private String nullableAnnotationClassName() {
            return nullabilityAnnotationFamily == null ? null : nullabilityAnnotationFamily.nullableAnnotationClassName();
        }

        private static NullabilityAnnotationFamily selectNullabilityAnnotationFamily(final JavaSymbolTable symbolTable) {
            for (NullabilityAnnotationFamily family : NULLABILITY_ANNOTATION_FAMILIES) {
                if (symbolTable.resolveClass(family.nonNullAnnotationClassName()).isPresent()
                        && symbolTable.resolveClass(family.nullableAnnotationClassName()).isPresent()) {
                    return family;
                }
            }
            return null;
        }
    }

    private static String convertSingleQuotedString(final String raw) {
        final String body = raw.substring(1, raw.length() - 1);
        final StringBuilder builder = new StringBuilder();
        builder.append('"');
        boolean escaping = false;
        for (int index = 0; index < body.length(); index++) {
            final char value = body.charAt(index);
            if (escaping) {
                if (value == '\'' || value == '"' || value == '\\') {
                    if (value == '"') {
                        builder.append("\\\"");
                    } else {
                        builder.append(value);
                    }
                } else {
                    builder.append('\\').append(value);
                }
                escaping = false;
                continue;
            }
            if (value == '\\') {
                escaping = true;
                continue;
            }
            if (value == '"') {
                builder.append("\\\"");
            } else {
                builder.append(value);
            }
        }
        if (escaping) {
            builder.append("\\\\");
        }
        builder.append('"');
        return builder.toString();
    }

    private static List<String> splitTopLevelSegments(final String text, final char separator) {
        final List<String> segments = new ArrayList<>();
        int start = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            final char value = text.charAt(index);
            if (inString) {
                if (value == '\\') {
                    index++;
                    continue;
                }
                if (value == quote) {
                    inString = false;
                }
                continue;
            }
            if (value == '\'' || value == '"' || value == '`') {
                inString = true;
                quote = value;
                continue;
            }
            if (value == '(') {
                parenDepth++;
                continue;
            }
            if (value == ')') {
                parenDepth--;
                continue;
            }
            if (value == '[') {
                bracketDepth++;
                continue;
            }
            if (value == ']') {
                bracketDepth--;
                continue;
            }
            if (value == '{') {
                braceDepth++;
                continue;
            }
            if (value == '}') {
                braceDepth--;
                continue;
            }
            if (value == separator
                    && parenDepth == 0
                    && bracketDepth == 0
                    && braceDepth == 0) {
                final String segment = text.substring(start, index).trim();
                if (!segment.isEmpty()) {
                    segments.add(segment);
                }
                start = index + 1;
            }
        }
        final String trailing = text.substring(start).trim();
        if (!trailing.isEmpty()) {
            segments.add(trailing);
        }
        return List.copyOf(segments);
    }

    private static String sanitizeJavaIdentifier(final String identifier) {
        String sanitized = sanitizeGeneratedIdentifier(identifier);
        if (JAVA_KEYWORDS.contains(sanitized)) {
            sanitized = sanitized + "_ts";
        }
        return sanitized;
    }

    private static String allocateUniqueName(final Map<String, Integer> counts, final String baseName) {
        final int next = counts.merge(baseName, 1, Integer::sum);
        if (next == 1) {
            return baseName;
        }
        return baseName + "_" + next;
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
        final Map<Path, Integer> moduleIndexBySource = new LinkedHashMap<>();
        final Map<Path, Map<String, String>> exportSymbolsByModule = new LinkedHashMap<>();

        for (int index = 0; index < modules.size(); index++) {
            final ModuleSource module = modules.get(index);
            initFunctionByModule.put(module.sourceFile(), "__tsj_init_module_" + index);
            moduleIndexBySource.put(module.sourceFile(), index);
            final Map<String, String> exportSymbols = new LinkedHashMap<>();
            for (ExportBinding exportBinding : module.exportBindings()) {
                exportSymbols.putIfAbsent(
                        exportBinding.exportName(),
                        exportGlobalSymbol(index, exportBinding.exportName())
                );
            }
            exportSymbolsByModule.put(module.sourceFile(), exportSymbols);
        }
        for (ModuleSource module : modules) {
            final Map<String, String> moduleExports = exportSymbolsByModule.get(module.sourceFile());
            for (ModuleReExport reExport : module.reExports()) {
                final Map<String, String> dependencyExports = exportSymbolsByModule.get(reExport.dependency());
                if (dependencyExports == null) {
                    continue;
                }
                if (reExport.kind() == ModuleReExportKind.ALL) {
                    for (String exportName : dependencyExports.keySet()) {
                        if ("default".equals(exportName)) {
                            continue;
                        }
                        moduleExports.putIfAbsent(
                                exportName,
                                exportGlobalSymbol(moduleIndexBySource.get(module.sourceFile()), exportName)
                        );
                    }
                    continue;
                }
                for (ReExportBinding binding : reExport.bindings()) {
                    moduleExports.putIfAbsent(
                            binding.exportName(),
                            exportGlobalSymbol(moduleIndexBySource.get(module.sourceFile()), binding.exportName())
                    );
                }
            }
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
        final Set<Path> dynamicImportTargets = new LinkedHashSet<>();
        for (ModuleSource module : modules) {
            dynamicImportTargets.addAll(module.dynamicImportDependencies());
        }
        if (!dynamicImportTargets.isEmpty()) {
            appendBundledLine(builder, lineOrigins, "function __tsj_dynamic_import(specifier) {", null, -1);
            appendBundledLine(builder, lineOrigins, "  switch (specifier) {", null, -1);
            final List<Path> sortedTargets = new ArrayList<>(dynamicImportTargets);
            sortedTargets.sort(Path::compareTo);
            for (Path target : sortedTargets) {
                final Map<String, String> dependencyExports = exportSymbolsByModule.getOrDefault(target, Map.of());
                appendBundledLine(
                        builder,
                        lineOrigins,
                        "    case \"" + escapeJavaLiteral(target.toString()) + "\":",
                        null,
                        -1
                );
                appendBundledLine(
                        builder,
                        lineOrigins,
                        "      return Promise.resolve(" + buildNamespaceImportObjectLiteral(dependencyExports) + ");",
                        null,
                        -1
                );
            }
            appendBundledLine(
                    builder,
                    lineOrigins,
                    "    default: return Promise.reject(new Error(\"Unsupported dynamic import specifier in TSJ-65 subset: \" + specifier));",
                    null,
                    -1
            );
            appendBundledLine(builder, lineOrigins, "  }", null, -1);
            appendBundledLine(builder, lineOrigins, "}", null, -1);
            appendBundledLine(builder, lineOrigins, "", null, -1);
        }

        for (ModuleSource module : modules) {
            final String initFunctionName = initFunctionByModule.get(module.sourceFile());
            final boolean asyncInit = module.requiresAsyncInit();
            final List<ImportRefreshBinding> importRefreshBindings = new ArrayList<>();
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
                            "  let " + binding.localName() + " = " + exportSymbol + ";",
                            module.sourceFile(),
                            moduleImport.line()
                    );
                    importRefreshBindings.add(new ImportRefreshBinding(binding.localName(), exportSymbol));
                }
            }

            for (int index = 0; index < module.bodyLines().size(); index++) {
                if (isTopLevelModuleLine(module.bodyLines().get(index))
                        && shouldRefreshImportBindingsBeforeLine(module.bodyLines(), index)) {
                    for (ImportRefreshBinding refreshBinding : importRefreshBindings) {
                        appendBundledLine(
                                builder,
                                lineOrigins,
                                "  " + refreshBinding.localName() + " = " + refreshBinding.exportSymbol() + ";",
                                null,
                                -1
                        );
                    }
                }
                appendBundledLine(
                        builder,
                        lineOrigins,
                        "  " + module.bodyLines().get(index),
                        module.sourceFile(),
                        module.bodyLineNumbers().get(index)
                );
            }

            final Map<String, String> moduleExports = exportSymbolsByModule.get(module.sourceFile());
            final Set<String> mutableExportLocals = detectMutableExportLocalNames(module);
            final Set<String> functionExportLocals = detectFunctionDeclarationLocalNames(module);
            final Map<String, List<String>> exportSymbolsByLocal = exportSymbolsByLocalName(module, moduleExports);
            final Set<String> localExportNames = new LinkedHashSet<>();
            for (ExportBinding exportBinding : module.exportBindings()) {
                localExportNames.add(exportBinding.exportName());
                appendBundledLine(
                        builder,
                        lineOrigins,
                        "  " + moduleExports.get(exportBinding.exportName()) + " = "
                                + exportBinding.localName() + ";",
                        null,
                                -1
                        );
            }
            if (!mutableExportLocals.isEmpty()) {
                for (ExportBinding exportBinding : module.exportBindings()) {
                    if (!functionExportLocals.contains(exportBinding.localName())) {
                        continue;
                    }
                    final String exportSymbol = moduleExports.get(exportBinding.exportName());
                    if (exportSymbol == null) {
                        continue;
                    }
                    appendBundledLine(
                            builder,
                            lineOrigins,
                            "  " + exportSymbol + " = function(...__tsj_live_args) {",
                            null,
                            -1
                    );
                    appendBundledLine(
                            builder,
                            lineOrigins,
                            "    const __tsj_live_result = " + exportBinding.localName() + ".apply(this, __tsj_live_args);",
                            null,
                            -1
                    );
                    for (String mutableLocal : mutableExportLocals) {
                        final List<String> symbols = exportSymbolsByLocal.get(mutableLocal);
                        if (symbols == null) {
                            continue;
                        }
                        for (String symbol : symbols) {
                            appendBundledLine(
                                    builder,
                                    lineOrigins,
                                    "    " + symbol + " = " + mutableLocal + ";",
                                    null,
                                    -1
                            );
                        }
                    }
                    appendBundledLine(builder, lineOrigins, "    return __tsj_live_result;", null, -1);
                    appendBundledLine(builder, lineOrigins, "  };", null, -1);
                }
            }
            final Set<String> assignedReExportNames = new LinkedHashSet<>();
            for (ModuleReExport reExport : module.reExports()) {
                final Map<String, String> dependencyExports = exportSymbolsByModule.get(reExport.dependency());
                if (dependencyExports == null) {
                    continue;
                }
                if (reExport.kind() == ModuleReExportKind.ALL) {
                    for (Map.Entry<String, String> entry : dependencyExports.entrySet()) {
                        final String exportName = entry.getKey();
                        if ("default".equals(exportName)
                                || localExportNames.contains(exportName)
                                || assignedReExportNames.contains(exportName)) {
                            continue;
                        }
                        final String targetExportSymbol = moduleExports.get(exportName);
                        if (targetExportSymbol == null) {
                            continue;
                        }
                        appendBundledLine(
                                builder,
                                lineOrigins,
                                "  " + targetExportSymbol + " = " + entry.getValue() + ";",
                                null,
                                -1
                        );
                        assignedReExportNames.add(exportName);
                    }
                    continue;
                }
                for (ReExportBinding binding : reExport.bindings()) {
                    final String exportName = binding.exportName();
                    if (localExportNames.contains(exportName) || assignedReExportNames.contains(exportName)) {
                        continue;
                    }
                    final String sourceExportSymbol = dependencyExports.get(binding.importedName());
                    if (sourceExportSymbol == null) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Re-export binding `" + binding.importedName() + "` is not exported by "
                                        + reExport.dependency().getFileName()
                                        + " (re-exported from " + module.sourceFile().getFileName() + ").",
                                reExport.line(),
                                1
                        );
                    }
                    final String targetExportSymbol = moduleExports.get(exportName);
                    if (targetExportSymbol == null) {
                        continue;
                    }
                    appendBundledLine(
                            builder,
                            lineOrigins,
                            "  " + targetExportSymbol + " = " + sourceExportSymbol + ";",
                            null,
                            -1
                    );
                    assignedReExportNames.add(exportName);
                }
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

    private static boolean isTopLevelModuleLine(final String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return !Character.isWhitespace(line.charAt(0));
    }

    private static boolean shouldRefreshImportBindingsBeforeLine(final List<String> bodyLines, final int index) {
        final String current = bodyLines.get(index).trim();
        if (current.isEmpty() || isContinuationLineStart(current)) {
            return false;
        }
        int previousIndex = index - 1;
        while (previousIndex >= 0) {
            final String previous = bodyLines.get(previousIndex).trim();
            if (previous.isEmpty()) {
                previousIndex--;
                continue;
            }
            return !isContinuationLineEnd(previous);
        }
        return true;
    }

    private static boolean isContinuationLineStart(final String line) {
        return line.startsWith("&&")
                || line.startsWith("||")
                || line.startsWith("??")
                || line.startsWith("?.")
                || line.startsWith(".")
                || line.startsWith(",")
                || line.startsWith(":")
                || line.startsWith(")")
                || line.startsWith("]")
                || line.startsWith("}")
                || line.startsWith("+")
                || line.startsWith("-")
                || line.startsWith("*")
                || line.startsWith("/")
                || line.startsWith("%")
                || line.startsWith("|")
                || line.startsWith("&")
                || line.startsWith("^")
                || line.startsWith("<<")
                || line.startsWith(">>");
    }

    private static boolean isContinuationLineEnd(final String line) {
        return line.endsWith("(")
                || line.endsWith("[")
                || line.endsWith("{")
                || line.endsWith(",")
                || line.endsWith(".")
                || line.endsWith(":")
                || line.endsWith("?")
                || line.endsWith("&&")
                || line.endsWith("||")
                || line.endsWith("??")
                || line.endsWith("+")
                || line.endsWith("-")
                || line.endsWith("*")
                || line.endsWith("/")
                || line.endsWith("%")
                || line.endsWith("|")
                || line.endsWith("&")
                || line.endsWith("^")
                || line.endsWith("<<")
                || line.endsWith(">>")
                || line.endsWith(">>>")
                || line.endsWith("=")
                || line.endsWith("=>");
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
        final List<ModuleReExport> reExports = new ArrayList<>();
        final List<String> bodyLines = new ArrayList<>();
        final List<Integer> bodyLineNumbers = new ArrayList<>();
        final Set<Path> dynamicImportDependencies = new LinkedHashSet<>();
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
            if (trimmedLine.startsWith("export *") || trimmedLine.startsWith("export {")) {
                final ImportStatement exportStatement = collectImportStatement(lines, index);
                final String exportCandidate = exportStatement.canonicalStatement();
                final Matcher exportAllFromMatcher = EXPORT_ALL_FROM_PATTERN.matcher(exportCandidate);
                final Matcher exportNamedFromMatcher = EXPORT_NAMED_FROM_PATTERN.matcher(exportCandidate);
                if (exportAllFromMatcher.matches()) {
                    final String importPath = exportAllFromMatcher.group(1);
                    final Path dependency = resolveImport(normalizedModule, importPath, index + 1);
                    collectModule(dependency, orderedModules, visiting);
                    reExports.add(ModuleReExport.all(dependency, index + 1));
                    index = exportStatement.endLineIndex();
                    continue;
                }
                if (exportNamedFromMatcher.matches()) {
                    final String rawBindings = exportNamedFromMatcher.group(1);
                    final String importPath = exportNamedFromMatcher.group(2);
                    final Path dependency = resolveImport(normalizedModule, importPath, index + 1);
                    collectModule(dependency, orderedModules, visiting);
                    reExports.add(
                            ModuleReExport.named(
                                    dependency,
                                    parseNamedReExportBindings(rawBindings, normalizedModule, index + 1),
                                    index + 1
                            )
                    );
                    index = exportStatement.endLineIndex();
                    continue;
                }
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
                final String rewrittenExportLine = rewriteDynamicImportCalls(
                        exportRewrite.rewrittenLine(),
                        normalizedModule,
                        index + 1,
                        orderedModules,
                        visiting,
                        dynamicImportDependencies
                );
                bodyLines.add(rewrittenExportLine);
                bodyLineNumbers.add(index + 1);
                for (ExportBinding exportBinding : exportRewrite.exportedBindings()) {
                    exportBindings.put(exportBinding.exportName(), exportBinding.localName());
                }
                requiresAsyncInit = requiresAsyncInit || lineContainsAwaitKeyword(rewrittenExportLine);
                continue;
            }
            final String rewrittenLine = rewriteDynamicImportCalls(
                    line,
                    normalizedModule,
                    index + 1,
                    orderedModules,
                    visiting,
                    dynamicImportDependencies
            );
            bodyLines.add(rewrittenLine);
            bodyLineNumbers.add(index + 1);
            requiresAsyncInit = requiresAsyncInit || lineContainsAwaitKeyword(rewrittenLine);
        }

        orderedModules.put(
                normalizedModule,
                new ModuleSource(
                        normalizedModule,
                        List.copyOf(imports),
                        List.copyOf(reExports),
                        List.copyOf(bodyLines),
                        List.copyOf(bodyLineNumbers),
                        Set.copyOf(dynamicImportDependencies),
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

    private static List<ReExportBinding> parseNamedReExportBindings(
            final String rawBindings,
            final Path sourceFile,
            final int line
    ) {
        final List<ReExportBinding> parsedBindings = new ArrayList<>();
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
            final String exportName;
            if (trimmed.contains(" as ")) {
                final String[] parts = trimmed.split("\\s+as\\s+");
                if (parts.length != 2) {
                    throw invalidNamedBinding(sourceFile, line, trimmed, null, null);
                }
                importedName = parts[0].trim();
                exportName = parts[1].trim();
            } else {
                importedName = trimmed;
                exportName = trimmed;
            }
            if (!isValidTsIdentifier(importedName) || !isValidTsIdentifier(exportName)) {
                throw invalidNamedBinding(sourceFile, line, trimmed, null, null);
            }
            parsedBindings.add(new ReExportBinding(importedName, exportName));
        }
        return List.copyOf(parsedBindings);
    }

    private static String rewriteDynamicImportCalls(
            final String line,
            final Path sourceFile,
            final int lineNumber,
            final Map<Path, ModuleSource> orderedModules,
            final Set<Path> visiting,
            final Set<Path> dynamicImportDependencies
    ) {
        if (line == null || line.indexOf("import") < 0) {
            return line;
        }
        final Matcher matcher = DYNAMIC_IMPORT_CALL_PATTERN.matcher(line);
        final StringBuffer rewritten = new StringBuffer();
        boolean foundDynamicImport = false;
        while (matcher.find()) {
            foundDynamicImport = true;
            final String rawArgs = matcher.group(1).trim();
            if (rawArgs.contains(",")) {
                throw unsupportedModuleFeature(
                        sourceFile,
                        lineNumber,
                        FEATURE_DYNAMIC_IMPORT,
                        "dynamic import() requires exactly one string-literal relative specifier in TSJ-65 subset.",
                        GUIDANCE_DYNAMIC_IMPORT
                );
            }
            final String specifier = parseDynamicImportSpecifier(rawArgs);
            if (specifier == null) {
                throw unsupportedModuleFeature(
                        sourceFile,
                        lineNumber,
                        FEATURE_DYNAMIC_IMPORT,
                        "dynamic import() requires a string-literal relative specifier in TSJ-65 subset.",
                        GUIDANCE_DYNAMIC_IMPORT
                );
            }
            if (!(specifier.startsWith("./") || specifier.startsWith("../") || specifier.startsWith("/"))) {
                throw unsupportedModuleFeature(
                        sourceFile,
                        lineNumber,
                        FEATURE_DYNAMIC_IMPORT,
                        "Only relative dynamic import specifiers are supported in TSJ-65 subset: " + specifier,
                        GUIDANCE_DYNAMIC_IMPORT
                );
            }
            final Path dependency = resolveImport(sourceFile, specifier, lineNumber);
            collectModule(dependency, orderedModules, visiting);
            dynamicImportDependencies.add(dependency);
            final String replacement = "__tsj_dynamic_import(\"" + escapeJavaLiteral(dependency.toString()) + "\")";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        if (!foundDynamicImport) {
            return line;
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private static String parseDynamicImportSpecifier(final String rawArgs) {
        if (rawArgs == null || rawArgs.length() < 2) {
            return null;
        }
        final char quote = rawArgs.charAt(0);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        if (rawArgs.charAt(rawArgs.length() - 1) != quote) {
            return null;
        }
        return rawArgs.substring(1, rawArgs.length() - 1);
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
        final Matcher localNamedExportMatcher = Pattern.compile("^export\\s*\\{([^}]*)}\\s*;?$").matcher(trimmed);
        if (localNamedExportMatcher.find()) {
            final List<ExportBinding> localNamedBindings =
                    parseLocalExportBindings(localNamedExportMatcher.group(1), lineNumber);
            return new ExportRewrite("", localNamedBindings);
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

    private static List<ExportBinding> parseLocalExportBindings(final String rawBindings, final int lineNumber) {
        final List<ExportBinding> parsedBindings = new ArrayList<>();
        final String[] bindingSegments = rawBindings.split(",");
        for (String rawBinding : bindingSegments) {
            final String trimmed = rawBinding.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("type ")) {
                continue;
            }
            final String localName;
            final String exportName;
            if (trimmed.contains(" as ")) {
                final String[] parts = trimmed.split("\\s+as\\s+");
                if (parts.length != 2) {
                    throw new JvmCompilationException(
                            "TSJ-BACKEND-PARSE",
                            "Invalid local export binding: " + trimmed,
                            lineNumber,
                            1
                    );
                }
                localName = parts[0].trim();
                exportName = parts[1].trim();
            } else {
                localName = trimmed;
                exportName = trimmed;
            }
            if (!isValidTsIdentifier(localName) || !isValidTsIdentifier(exportName)) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-PARSE",
                        "Invalid local export binding: " + trimmed,
                        lineNumber,
                        1
                );
            }
            parsedBindings.add(new ExportBinding(exportName, localName));
        }
        return List.copyOf(parsedBindings);
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

    private static Set<String> detectMutableExportLocalNames(final ModuleSource module) {
        final Set<String> mutableLocals = new LinkedHashSet<>();
        if (module.exportBindings().isEmpty()) {
            return mutableLocals;
        }
        for (ExportBinding exportBinding : module.exportBindings()) {
            final String localName = exportBinding.localName();
            for (String bodyLine : module.bodyLines()) {
                final String trimmed = bodyLine.trim();
                if (trimmed.startsWith("let ") || trimmed.startsWith("var ")) {
                    if (lineDeclaresIdentifier(trimmed, localName)) {
                        mutableLocals.add(localName);
                        break;
                    }
                }
            }
        }
        return mutableLocals;
    }

    private static Set<String> detectFunctionDeclarationLocalNames(final ModuleSource module) {
        final Set<String> functionLocals = new LinkedHashSet<>();
        if (module.exportBindings().isEmpty()) {
            return functionLocals;
        }
        for (ExportBinding exportBinding : module.exportBindings()) {
            final String localName = exportBinding.localName();
            final Pattern functionPattern = Pattern.compile(
                    "^(?:async\\s+)?function\\s+" + Pattern.quote(localName) + "\\s*\\("
            );
            for (String bodyLine : module.bodyLines()) {
                if (functionPattern.matcher(bodyLine.trim()).find()) {
                    functionLocals.add(localName);
                    break;
                }
            }
        }
        return functionLocals;
    }

    private static Map<String, List<String>> exportSymbolsByLocalName(
            final ModuleSource module,
            final Map<String, String> moduleExports
    ) {
        final Map<String, List<String>> byLocal = new LinkedHashMap<>();
        for (ExportBinding exportBinding : module.exportBindings()) {
            final String exportSymbol = moduleExports.get(exportBinding.exportName());
            if (exportSymbol == null) {
                continue;
            }
            byLocal.computeIfAbsent(exportBinding.localName(), ignored -> new ArrayList<>()).add(exportSymbol);
        }
        return byLocal;
    }

    private static boolean lineDeclaresIdentifier(final String declarationLine, final String identifier) {
        final String declarators = declarationLine.startsWith("let ")
                ? declarationLine.substring("let ".length())
                : declarationLine.startsWith("var ")
                ? declarationLine.substring("var ".length())
                : "";
        if (declarators.isEmpty()) {
            return false;
        }
        final String[] parts = declarators.split(",");
        for (String part : parts) {
            final String candidate = part.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            final int equalsIndex = candidate.indexOf('=');
            final String namePortion = equalsIndex >= 0
                    ? candidate.substring(0, equalsIndex).trim()
                    : candidate;
            if (identifier.equals(namePortion)) {
                return true;
            }
        }
        return false;
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
        return module.imports().isEmpty()
                && module.reExports().isEmpty()
                && module.dynamicImportDependencies().isEmpty()
                && module.exportBindings().isEmpty();
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

    private static void compileJava(final List<Path> javaSourcePaths, final Path classesDir) {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-JDK",
                    "JDK compiler is unavailable. Use a JDK runtime for TSJ backend compile."
            );
        }

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, UTF_8)) {
            final List<Path> compilationSources = new ArrayList<>(javaSourcePaths);
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
        final String additionalClasspath = System.getProperty(ADDITIONAL_CLASSPATH_PROPERTY, "");
        if (!additionalClasspath.isBlank()) {
            for (String entry : additionalClasspath.split(Pattern.quote(File.pathSeparator))) {
                if (entry != null && !entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        addClasspathEntryIfExists(entries, discoverRuntimeClassesFromWorkspace());
        addClasspathEntryIfExists(entries, discoverRuntimeClassesFromWorkingDirectory());
        return String.join(File.pathSeparator, entries);
    }

    private static AnnotationRenderContext createAnnotationRenderContext() {
        return new AnnotationRenderContext(new JavaSymbolTable(
                parseClasspathEntries(buildJavacClasspath()),
                "tsj-jvm-bytecode-annotation-render"
        ));
    }

    private static List<Path> parseClasspathEntries(final String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return List.of();
        }
        final List<Path> entries = new ArrayList<>();
        final String[] parts = classpath.split(Pattern.quote(File.pathSeparator));
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                entries.add(Path.of(part));
            }
        }
        return List.copyOf(entries);
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

    private static ParseWithIncrementalResult parseProgramWithIncrementalCache(
            final String sourceText,
            final Path sourceFile,
            final BundleResult bundleResult
    ) {
        final boolean cacheEnabled = Boolean.parseBoolean(System.getProperty(INCREMENTAL_CACHE_PROPERTY, "true"));
        final String compilerVersion = incrementalCompilerVersion();
        final String sourceGraphFingerprint = computeSourceGraphFingerprint(
                sourceText,
                sourceFile,
                bundleResult,
                compilerVersion
        );
        if (!cacheEnabled) {
            return new ParseWithIncrementalResult(
                    parseProgram(sourceText, sourceFile, bundleResult),
                    new IncrementalCompilationReport(
                            false,
                            compilerVersion,
                            sourceGraphFingerprint,
                            IncrementalStageState.DISABLED,
                            IncrementalStageState.DISABLED,
                            IncrementalStageState.MISS
                    )
            );
        }

        final Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        final String previousFingerprint;
        synchronized (LAST_SOURCE_GRAPH_FINGERPRINT) {
            previousFingerprint = LAST_SOURCE_GRAPH_FINGERPRINT.put(normalizedSource, sourceGraphFingerprint);
        }
        final boolean invalidated = previousFingerprint != null && !previousFingerprint.equals(sourceGraphFingerprint);
        final IncrementalParseCacheKey cacheKey = new IncrementalParseCacheKey(sourceGraphFingerprint, compilerVersion);
        final ParseResult cached;
        synchronized (INCREMENTAL_PARSE_CACHE) {
            cached = INCREMENTAL_PARSE_CACHE.get(cacheKey);
        }
        if (cached != null) {
            return new ParseWithIncrementalResult(
                    cached,
                    new IncrementalCompilationReport(
                            true,
                            compilerVersion,
                            sourceGraphFingerprint,
                            IncrementalStageState.HIT,
                            IncrementalStageState.HIT,
                            IncrementalStageState.MISS
                    )
            );
        }

        final ParseResult parsed = parseProgram(sourceText, sourceFile, bundleResult);
        synchronized (INCREMENTAL_PARSE_CACHE) {
            INCREMENTAL_PARSE_CACHE.put(cacheKey, parsed);
        }
        final IncrementalStageState missState = invalidated
                ? IncrementalStageState.INVALIDATED
                : IncrementalStageState.MISS;
        return new ParseWithIncrementalResult(
                parsed,
                new IncrementalCompilationReport(
                        true,
                        compilerVersion,
                        sourceGraphFingerprint,
                        missState,
                        missState,
                        IncrementalStageState.MISS
                )
        );
    }

    private static String incrementalCompilerVersion() {
        return INCREMENTAL_CACHE_VERSION
                + "|"
                + BackendJvmModule.moduleName()
                + "|"
                + BackendJvmModule.dependencyFingerprint();
    }

    private static String computeSourceGraphFingerprint(
            final String sourceText,
            final Path sourceFile,
            final BundleResult bundleResult,
            final String compilerVersion
    ) {
        final MessageDigest digest = messageDigestSha256();
        updateDigest(digest, compilerVersion);
        updateDigest(digest, sourceFile.toAbsolutePath().normalize().toString());
        updateDigest(digest, System.getProperty(LEGACY_TOKENIZER_PROPERTY, "false"));
        updateDigest(digest, System.getProperty(AST_NO_FALLBACK_PROPERTY, "true"));
        final LinkedHashSet<String> moduleFiles = new LinkedHashSet<>();
        for (SourceLineOrigin lineOrigin : bundleResult.lineOrigins()) {
            if (lineOrigin != null && lineOrigin.sourceFile() != null) {
                moduleFiles.add(lineOrigin.sourceFile().toAbsolutePath().normalize().toString());
            }
        }
        for (String moduleFile : moduleFiles) {
            updateDigest(digest, moduleFile);
        }
        updateDigest(digest, sourceText);
        return toHex(digest.digest());
    }

    private static MessageDigest messageDigestSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", noSuchAlgorithmException);
        }
    }

    private static void updateDigest(final MessageDigest digest, final String value) {
        digest.update((value == null ? "" : value).getBytes(UTF_8));
        digest.update((byte) 0);
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", current));
        }
        return builder.toString();
    }

    private static <K, V> Map<K, V> newLruMap(final int maxEntries) {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        });
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

        if (Boolean.parseBoolean(System.getProperty(AST_NO_FALLBACK_PROPERTY, "true"))) {
            if (!bridgeResult.normalizationDiagnostics().isEmpty()) {
                throw bridgeDiagnosticToCompilationException(
                        bridgeResult.normalizationDiagnostics().getFirst(),
                        bundleResult
                );
            }
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
        throw bridgeDiagnosticToCompilationException(bridgeResult.diagnostics().getFirst(), bundleResult);
    }

    private static JvmCompilationException bridgeDiagnosticToCompilationException(
            final TypeScriptSyntaxBridge.BridgeDiagnostic diagnostic,
            final BundleResult bundleResult
    ) {
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
        final String featureId = diagnostic.featureId();
        final String guidance = diagnostic.guidance();
        String message = diagnostic.message();
        if (featureId != null && !featureId.isBlank() && message != null && !message.contains("[featureId=")) {
            message = message + " [featureId=" + featureId + "]";
            if (guidance != null && !guidance.isBlank()) {
                message = message + ". Guidance: " + guidance;
            }
        }
        return new JvmCompilationException(
                code,
                message,
                line,
                column,
                diagnosticSource,
                featureId,
                guidance
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
        final List<ClassField> staticFields = new ArrayList<>();
        final JsonNode staticFieldsNode = declarationNode.get("staticFields");
        if (staticFieldsNode != null && staticFieldsNode.isArray()) {
            for (JsonNode fieldNode : staticFieldsNode) {
                staticFields.add(lowerClassFieldFromAst(fieldNode, bundleResult));
            }
        }
        final List<ClassMethod> staticMethods = new ArrayList<>();
        final JsonNode staticMethodsNode = declarationNode.get("staticMethods");
        if (staticMethodsNode != null && staticMethodsNode.isArray()) {
            for (JsonNode methodNode : staticMethodsNode) {
                staticMethods.add(lowerClassMethodFromAst(methodNode, bundleResult, statementLocations));
            }
        }
        return new ClassDeclaration(
                requiredText(declarationNode, "name"),
                superClassName,
                fieldNames,
                constructorMethod,
                List.copyOf(methods),
                List.copyOf(staticFields),
                List.copyOf(staticMethods)
        );
    }

    private static ClassField lowerClassFieldFromAst(
            final JsonNode fieldNode,
            final BundleResult bundleResult
    ) {
        return new ClassField(
                requiredText(fieldNode, "name"),
                lowerExpressionFromAst(requiredNode(fieldNode, "initializer"), bundleResult)
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
            List<ClassMethod> methods,
            List<ClassField> staticFields,
            List<ClassMethod> staticMethods
    ) {
    }

    private record ClassField(String name, Expression initializer) {
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

    private record TopLevelClassDeclaration(
            ClassDeclaration declaration,
            SourceLocation sourceLocation,
            Set<String> visibleBindingNames,
            Map<String, String> moduleBindingTargets
    ) {
        private TopLevelClassDeclaration {
            visibleBindingNames = Set.copyOf(Objects.requireNonNull(visibleBindingNames, "visibleBindingNames"));
            moduleBindingTargets = Map.copyOf(Objects.requireNonNull(moduleBindingTargets, "moduleBindingTargets"));
        }
    }

    private record MetadataCarrierDeclaration(
            ClassDeclaration classDeclaration,
            TsDecoratedClass decoratedClass,
            Map<String, String> importedDecoratorBindings
    ) {
        private MetadataCarrierDeclaration {
            classDeclaration = Objects.requireNonNull(classDeclaration, "classDeclaration");
            importedDecoratorBindings = Map.copyOf(
                    Objects.requireNonNull(importedDecoratorBindings, "importedDecoratorBindings")
            );
        }
    }

    private record ClassLookupKey(Path sourceFile, String className) {
        private ClassLookupKey {
            sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
            className = Objects.requireNonNull(className, "className");
        }
    }

    private record AnnotationAssignment(String name, String value) {
    }

    private enum ModuleImportKind {
        NAMED,
        NAMESPACE,
        INTEROP,
        SIDE_EFFECT
    }

    private record ImportBinding(String importedName, String localName) {
    }

    private record ImportRefreshBinding(String localName, String exportSymbol) {
    }

    private record ExportBinding(String exportName, String localName) {
    }

    private enum ModuleReExportKind {
        ALL,
        NAMED
    }

    private record ReExportBinding(String importedName, String exportName) {
    }

    private record ModuleReExport(
            ModuleReExportKind kind,
            Path dependency,
            List<ReExportBinding> bindings,
            int line
    ) {
        private static ModuleReExport all(final Path dependency, final int line) {
            return new ModuleReExport(
                    ModuleReExportKind.ALL,
                    dependency,
                    List.of(),
                    line
            );
        }

        private static ModuleReExport named(
                final Path dependency,
                final List<ReExportBinding> bindings,
                final int line
        ) {
            return new ModuleReExport(
                    ModuleReExportKind.NAMED,
                    dependency,
                    List.copyOf(bindings),
                    line
            );
        }
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
            List<ModuleReExport> reExports,
            List<String> bodyLines,
            List<Integer> bodyLineNumbers,
            Set<Path> dynamicImportDependencies,
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
            final List<ClassField> staticFields = new ArrayList<>();
            final List<ClassMethod> staticMethods = new ArrayList<>();
            ClassMethod constructorMethod = null;
            while (!checkSymbol("}") && !isAtEnd()) {
                final boolean staticMethod = matchKeyword("static");
                final boolean asyncMethod = matchKeyword("async");
                if (asyncMethod) {
                    rejectUnsupportedAsyncMethodVariantIfPresent("class");
                }
                final Token memberName = consumeIdentifier("Expected class member name.");
                if (checkSymbol(":") || checkSymbol("=") || checkSymbol(";")) {
                    if (asyncMethod) {
                        throw new JvmCompilationException(
                                "TSJ-BACKEND-UNSUPPORTED",
                                "Async class fields are unsupported in TSJ-13b subset.",
                                memberName.line(),
                                memberName.column()
                        );
                    }
                    if (matchSymbol(":")) {
                        skipTypeAnnotation();
                    }
                    final Expression initializer = matchSymbol("=") ? parseExpression() : new UndefinedLiteral();
                    consumeSymbol(";", "Expected `;` after class field declaration.");
                    if (staticMethod) {
                        staticFields.add(new ClassField(memberName.text(), initializer));
                    } else {
                        fields.add(memberName.text());
                    }
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
                } else if (staticMethod) {
                    staticMethods.add(method);
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
                    List.copyOf(methods),
                    List.copyOf(staticFields),
                    List.copyOf(staticMethods)
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
                final List<ClassField> staticFields = new ArrayList<>();
                for (ClassField field : declaration.staticFields()) {
                    staticFields.add(new ClassField(
                            field.name(),
                            optimizeExpression(field.initializer())
                    ));
                }
                final List<ClassMethod> staticMethods = new ArrayList<>();
                for (ClassMethod method : declaration.staticMethods()) {
                    staticMethods.add(new ClassMethod(
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
                        List.copyOf(methods),
                        List.copyOf(staticFields),
                        List.copyOf(staticMethods)
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
        private final List<StrictNativeClassModel> strictNativeClassModels;
        private final List<String> propertyCacheFieldDeclarations;
        private Map<String, String> topLevelBindingCells;
        private Set<String> topLevelBindingNames;
        private int propertyCacheCounter;
        private int strictNativeLambdaCounter;

        private JavaSourceGenerator(
                final String packageName,
                final String classSimpleName,
                final Program program,
                final Map<Statement, SourceLocation> statementLocations,
                final StrictNativeClassLoweringPlan strictLoweringPlan
        ) {
            this.packageName = packageName;
            this.classSimpleName = classSimpleName;
            this.program = program;
            this.statementLocations = new IdentityHashMap<>(statementLocations);
            this.strictNativeClassModels = createStrictNativeClassModels(
                    Objects.requireNonNull(strictLoweringPlan, "strictLoweringPlan").nativeClasses()
            );
            this.propertyCacheFieldDeclarations = new ArrayList<>();
            this.topLevelBindingCells = Map.of();
            this.topLevelBindingNames = Set.of();
            this.propertyCacheCounter = 0;
            this.strictNativeLambdaCounter = 0;
        }

        private String generate() {
            final StringBuilder bootstrapBody = new StringBuilder();
            final EmissionContext mainContext = new EmissionContext(null);
            if (requiresTopLevelAwaitLowering(program.statements())) {
                emitTopLevelAwaitStatements(bootstrapBody, mainContext, program.statements(), "        ");
            } else {
                emitStatements(bootstrapBody, mainContext, program.statements(), "        ", false);
            }
            this.topLevelBindingCells = Map.copyOf(new LinkedHashMap<>(mainContext.bindings));
            this.topLevelBindingNames = Set.copyOf(topLevelBindingCells.keySet());
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
            builder.append("    public static interface __TsjStrictNativeInstance {\n");
            builder.append("        Object __tsjInvoke(String methodName, Object... args);\n");
            builder.append("        void __tsjSetField(String fieldName, Object value);\n");
            builder.append("    }\n");
            builder.append("    private static Object __tsjStrictArg(final Object[] args, final int index) {\n");
            builder.append("        return args != null && index < args.length\n");
            builder.append("                ? args[index]\n");
            builder.append("                : dev.tsj.runtime.TsjRuntime.undefined();\n");
            builder.append("    }\n");
            builder.append("    private interface __TsjStrictNativeFactory {\n");
            builder.append("        __TsjStrictNativeInstance create(Object[] constructorArgs);\n");
            builder.append("    }\n");
            builder.append("    private static final java.util.Map<String, __TsjStrictNativeFactory> ")
                    .append("__TSJ_STRICT_FACTORIES")
                    .append(" = new java.util.LinkedHashMap<>();\n");
            builder.append("    private static final java.util.Map<String, dev.tsj.runtime.TsjCell> ")
                    .append("__TSJ_TOP_LEVEL_BINDINGS")
                    .append(" = new java.util.LinkedHashMap<>();\n");
            builder.append("    private static boolean ")
                    .append(BOOTSTRAP_GUARD_FIELD)
                    .append(" = false;\n");
            builder.append("    private static boolean ")
                    .append(BOOTSTRAP_IN_PROGRESS_FIELD)
                    .append(" = false;\n");
            builder.append("    static void __tsjEnsureBootstrapped() {\n");
            builder.append("        if (!").append(BOOTSTRAP_GUARD_FIELD).append(") {\n");
            builder.append("            __tsjBootstrap();\n");
            builder.append("        }\n");
            builder.append("    }\n");
            builder.append("    static dev.tsj.runtime.TsjCell __tsjResolveTopLevelBinding(final String bindingName) {\n");
            builder.append("        final dev.tsj.runtime.TsjCell binding = __TSJ_TOP_LEVEL_BINDINGS.get(bindingName);\n");
            builder.append("        if (binding != null) {\n");
            builder.append("            return binding;\n");
            builder.append("        }\n");
            builder.append("        throw new IllegalArgumentException(\"TSJ strict-native top-level binding not found: \" + bindingName);\n");
            builder.append("    }\n");
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
            builder.append("        if (").append(BOOTSTRAP_GUARD_FIELD).append(" || ")
                    .append(BOOTSTRAP_IN_PROGRESS_FIELD)
                    .append(") {\n");
            builder.append("            return;\n");
            builder.append("        }\n");
            builder.append("        ").append(BOOTSTRAP_IN_PROGRESS_FIELD).append(" = true;\n");
            builder.append("        try {\n");
            builder.append("        ").append(TOP_LEVEL_CLASS_MAP_FIELD).append(".clear();\n");
            builder.append("        __TSJ_STRICT_FACTORIES.clear();\n");
            builder.append("        __TSJ_TOP_LEVEL_BINDINGS.clear();\n");
            for (StrictNativeClassModel model : strictNativeClassModels) {
                builder.append("        __TSJ_STRICT_FACTORIES.put(\"")
                        .append(escapeJava(model.tsClassName()))
                        .append("\", (__tsjCtorArgs) -> ")
                        .append(renderStrictNativeConstructionExpression(model, "__tsjCtorArgs"))
                        .append(");\n");
            }
            builder.append(bootstrapBody);
            builder.append("        } finally {\n");
            builder.append("            ").append(BOOTSTRAP_IN_PROGRESS_FIELD).append(" = false;\n");
            builder.append("        }\n");
            builder.append("    }\n\n");
            builder.append("    public static void main(String[] args) {\n");
            builder.append("        __tsjBootstrap();\n");
            builder.append("    }\n");
            builder.append("}\n");
            return builder.toString();
        }

        private List<StrictNativeClassModel> createStrictNativeClassModels(
                final List<TopLevelClassDeclaration> classDeclarations
        ) {
            if (classDeclarations.isEmpty()) {
                return List.of();
            }
            final List<StrictNativeClassModel> models = new ArrayList<>();
            final Map<String, Integer> classNameCounts = new LinkedHashMap<>();
            for (TopLevelClassDeclaration topLevelClass : classDeclarations) {
                final ClassDeclaration declaration = topLevelClass.declaration();
                final String nativeClassSimpleName = allocateUniqueName(
                        classNameCounts,
                        sanitizeJavaIdentifier(declaration.name()) + "__TsjStrictNative"
                );
                final Map<String, String> fieldNameMap = new LinkedHashMap<>();
                final Map<String, Integer> fieldNameCounts = new LinkedHashMap<>();
                for (String fieldName : declaration.fieldNames()) {
                    fieldNameMap.put(
                            fieldName,
                            allocateUniqueName(fieldNameCounts, sanitizeJavaIdentifier(fieldName))
                    );
                }
                final Map<String, String> methodNameMap = new LinkedHashMap<>();
                final Map<String, Integer> methodNameCounts = new LinkedHashMap<>();
                for (ClassMethod method : declaration.methods()) {
                    methodNameMap.put(
                            method.name(),
                            allocateUniqueName(methodNameCounts, sanitizeJavaIdentifier(method.name()))
                    );
                }
                final Map<String, String> staticFieldNameMap = new LinkedHashMap<>();
                final Map<String, Integer> staticFieldNameCounts = new LinkedHashMap<>();
                for (ClassField field : declaration.staticFields()) {
                    staticFieldNameMap.put(
                            field.name(),
                            allocateUniqueName(staticFieldNameCounts, sanitizeJavaIdentifier(field.name()))
                    );
                }
                final Map<String, String> staticMethodNameMap = new LinkedHashMap<>();
                final Map<String, Integer> staticMethodNameCounts = new LinkedHashMap<>();
                for (ClassMethod method : declaration.staticMethods()) {
                    staticMethodNameMap.put(
                            method.name(),
                            allocateUniqueName(staticMethodNameCounts, sanitizeJavaIdentifier(method.name()))
                    );
                }
                models.add(new StrictNativeClassModel(
                        declaration.name(),
                        nativeClassSimpleName,
                        topLevelClass.sourceLocation(),
                        declaration,
                        topLevelClass.moduleBindingTargets(),
                        Map.copyOf(fieldNameMap),
                        Map.copyOf(methodNameMap),
                        Map.copyOf(staticFieldNameMap),
                        Map.copyOf(staticMethodNameMap)
                ));
            }
            return List.copyOf(models);
        }

        private List<Path> writeStrictNativeSources(
                final Path generatedSourceRoot,
                final List<MetadataCarrierDeclaration> metadataCarrierDeclarations,
                final AnnotationRenderContext annotationRenderContext
        ) throws IOException {
            if (strictNativeClassModels.isEmpty()) {
                return List.of();
            }
            final Path packageDir = generatedSourceRoot.resolve(packageName.replace('.', '/'));
            Files.createDirectories(packageDir);
            final List<Path> sourceFiles = new ArrayList<>();
            for (StrictNativeClassModel model : strictNativeClassModels) {
                final Path sourceFile = packageDir.resolve(model.nativeClassSimpleName() + ".java");
                Files.writeString(
                        sourceFile,
                        renderStrictNativeClassSource(
                                model,
                                resolveMetadataCarrierDeclaration(model, metadataCarrierDeclarations),
                                annotationRenderContext
                        ),
                        UTF_8
                );
                sourceFiles.add(sourceFile);
            }
            return List.copyOf(sourceFiles);
        }

        private MetadataCarrierDeclaration resolveMetadataCarrierDeclaration(
                final StrictNativeClassModel model,
                final List<MetadataCarrierDeclaration> metadataCarrierDeclarations
        ) {
            final Path sourcePath = model.sourceLocation() == null
                    ? null
                    : model.sourceLocation().sourceFile().toAbsolutePath().normalize();
            for (MetadataCarrierDeclaration declaration : metadataCarrierDeclarations) {
                if (!declaration.classDeclaration().name().equals(model.tsClassName())) {
                    continue;
                }
                final TsDecoratedClass decoratedClass = declaration.decoratedClass();
                if (sourcePath == null || decoratedClass == null || decoratedClass.sourceFile().equals(sourcePath)) {
                    return declaration;
                }
            }
            return null;
        }

        private String renderStrictNativeClassSource(
                final StrictNativeClassModel model,
                final MetadataCarrierDeclaration metadataDeclaration,
                final AnnotationRenderContext annotationRenderContext
        ) {
            final StringBuilder builder = new StringBuilder();
            final TsDecoratedClass decoratedClass = metadataDeclaration == null ? null : metadataDeclaration.decoratedClass();
            final Map<String, String> importedDecoratorBindings = metadataDeclaration == null
                    ? Map.of()
                    : metadataDeclaration.importedDecoratorBindings();
            final Set<String> classTypeParameters = decoratedClass == null
                    ? Set.of()
                    : extractStrictNativeTypeParameterNames(decoratedClass.genericParameters());
            final String superClassType = model.declaration().superClassName() == null
                    ? null
                    : resolveStrictNativeRawBaseType(model.declaration().superClassName(), Set.of());

            builder.append("package ").append(packageName).append(";\n\n");
            appendAnnotationLines(
                    builder,
                    "",
                    decoratedClass == null ? List.of() : decoratedClass.decorators(),
                    importedDecoratorBindings,
                    annotationRenderContext
            );
            builder.append("public class ")
                    .append(model.nativeClassSimpleName())
                    .append(renderStrictNativeTypeParameterDeclaration(
                            decoratedClass == null ? List.of() : decoratedClass.genericParameters(),
                            Set.of()
                    ));
            if (superClassType != null && !"Object".equals(superClassType)) {
                builder.append(" extends ").append(superClassType);
            }
            builder.append(" implements ")
                    .append(classSimpleName)
                    .append(".__TsjStrictNativeInstance {\n");

            for (ClassField field : model.declaration().staticFields()) {
                final String javaFieldName = model.staticFieldNameMap().get(field.name());
                builder.append("    public static Object ")
                        .append(javaFieldName)
                        .append(" = ")
                        .append(emitStrictNativeExpression(model, Map.of(), Set.of(), field.initializer()))
                        .append(";\n");
            }
            if (!model.declaration().staticFields().isEmpty()) {
                builder.append("\n");
            }

            final Map<String, List<TsDecoratedField>> decoratedFieldsByName = new LinkedHashMap<>();
            if (decoratedClass != null) {
                for (TsDecoratedField field : decoratedClass.fields()) {
                    decoratedFieldsByName.computeIfAbsent(field.fieldName(), ignored -> new ArrayList<>()).add(field);
                }
            }
            final Map<String, String> fieldTypesByName = new LinkedHashMap<>();
            final Map<String, String> fieldNullabilityAnnotationsByName = new LinkedHashMap<>();
            for (String fieldName : model.declaration().fieldNames()) {
                final String javaFieldName = model.fieldNameMap().get(fieldName);
                final String fieldTypeAnnotation = resolveFieldTypeAnnotation(decoratedFieldsByName.get(fieldName));
                final String fieldType = resolveStrictNativeFieldType(
                        fieldName,
                        decoratedFieldsByName.get(fieldName),
                        classTypeParameters
                );
                fieldTypesByName.put(fieldName, fieldType);
                appendAnnotationLines(
                        builder,
                        "    ",
                        resolveFieldDecorators(decoratedFieldsByName.get(fieldName)),
                        importedDecoratorBindings,
                        annotationRenderContext
                );
                final String fieldNullabilityAnnotation = renderStrictNativeNullabilityAnnotation(
                        fieldTypeAnnotation,
                        fieldType,
                        annotationRenderContext
                );
                fieldNullabilityAnnotationsByName.put(fieldName, fieldNullabilityAnnotation);
                if (!fieldNullabilityAnnotation.isBlank()
                        && !hasResolvedDecorator(
                        resolveFieldDecorators(decoratedFieldsByName.get(fieldName)),
                        importedDecoratorBindings,
                        fieldNullabilityAnnotation.substring(1)
                )) {
                    builder.append("    ").append(fieldNullabilityAnnotation).append('\n');
                }
                builder.append("    private ").append(fieldType).append(" ").append(javaFieldName).append(";\n");
            }
            if (!model.declaration().fieldNames().isEmpty()) {
                builder.append("\n");
            }

            builder.append("    private static Object __tsjArg(final Object[] args, final int index) {\n");
            builder.append("        return args != null && index < args.length\n");
            builder.append("                ? args[index]\n");
            builder.append("                : dev.tsj.runtime.TsjRuntime.undefined();\n");
            builder.append("    }\n\n");

            builder.append("    private void __tsjInitFields() {\n");
            for (String fieldName : model.declaration().fieldNames()) {
                final String javaFieldName = model.fieldNameMap().get(fieldName);
                final String fieldType = fieldTypesByName.getOrDefault(fieldName, "Object");
                builder.append("        this.")
                        .append(javaFieldName)
                        .append(" = ")
                        .append(renderStrictNativeDefaultValue(fieldType))
                        .append(";\n");
            }
            builder.append("    }\n\n");

            final ClassMethod constructorMethod = model.declaration().constructorMethod();
            final TsDecoratedMethod decoratedConstructor = constructorMethod == null
                    ? null
                    : findDecoratedMethod(
                    decoratedClass == null ? List.of() : decoratedClass.methods(),
                    true,
                    constructorMethod.name(),
                    constructorMethod.parameters().size(),
                    null
            );

            if (constructorMethod == null) {
                builder.append("    public ").append(model.nativeClassSimpleName()).append("() {\n");
                if (superClassType != null) {
                    builder.append("        super();\n");
                }
                builder.append("        __tsjInitFields();\n");
                builder.append("        ").append(classSimpleName).append(".__tsjEnsureBootstrapped();\n");
                builder.append("    }\n\n");
            } else if (constructorMethod.parameters().isEmpty()) {
                final int constructorBodyStart = constructorMethod.body().isEmpty()
                        || !(constructorMethod.body().getFirst() instanceof SuperCallStatement)
                        ? 0
                        : 1;
                appendAnnotationLines(
                        builder,
                        "    ",
                        decoratedConstructor == null ? List.of() : decoratedConstructor.decorators(),
                        importedDecoratorBindings,
                        annotationRenderContext
                );
                builder.append("    public ").append(model.nativeClassSimpleName()).append("() {\n");
                if (superClassType != null && constructorBodyStart == 1) {
                    builder.append("        super(")
                            .append(renderStrictNativeExpressionList(
                                    model,
                                    Map.of(),
                                    Set.of(),
                                    ((SuperCallStatement) constructorMethod.body().getFirst()).arguments()
                            ))
                            .append(");\n");
                } else if (superClassType != null) {
                    builder.append("        super();\n");
                }
                builder.append("        __tsjInitFields();\n");
                builder.append("        ").append(classSimpleName).append(".__tsjEnsureBootstrapped();\n");
                final Map<String, String> variableNames = new LinkedHashMap<>();
                final Map<String, String> variableTypes = new LinkedHashMap<>();
                final Set<String> boxedBindings = new LinkedHashSet<>();
                final Map<String, Integer> localNameCounts = new LinkedHashMap<>();
                appendStrictNativeStatements(
                        builder,
                        model,
                        fieldTypesByName,
                        constructorMethod.body().subList(constructorBodyStart, constructorMethod.body().size()),
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        null,
                        "        "
                );
                builder.append("    }\n\n");
            } else {
                final List<String> constructorParameterTypes = resolveStrictNativeParameterTypes(
                        constructorMethod.parameters(),
                        decoratedConstructor == null ? List.of() : decoratedConstructor.parameters(),
                        classTypeParameters
                );
                appendAnnotationLines(
                        builder,
                        "    ",
                        decoratedConstructor == null ? List.of() : decoratedConstructor.decorators(),
                        importedDecoratorBindings,
                        annotationRenderContext
                );
                builder.append("    public ").append(model.nativeClassSimpleName()).append("(");
                builder.append(renderStrictNativeParameters(
                        constructorMethod.parameters(),
                        constructorParameterTypes,
                        decoratedConstructor == null ? List.of() : decoratedConstructor.parameters(),
                        importedDecoratorBindings,
                        annotationRenderContext
                ));
                builder.append(") {\n");
                final Map<String, String> variableNames = new LinkedHashMap<>();
                final Map<String, String> variableTypes = new LinkedHashMap<>();
                final Set<String> boxedBindings = new LinkedHashSet<>();
                final Map<String, Integer> localNameCounts = new LinkedHashMap<>();
                final boolean boxConstructorBindings = containsStrictNativeFunctionExpression(constructorMethod.body());
                appendStrictNativeParameterLocals(
                        builder,
                        constructorMethod.parameters(),
                        constructorParameterTypes,
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        boxConstructorBindings,
                        "        "
                );
                final int constructorBodyStart = constructorMethod.body().isEmpty()
                        || !(constructorMethod.body().getFirst() instanceof SuperCallStatement)
                        ? 0
                        : 1;
                if (superClassType != null && constructorBodyStart == 1) {
                    builder.append("        super(")
                            .append(renderStrictNativeExpressionList(
                                    model,
                                    variableNames,
                                    boxedBindings,
                                    ((SuperCallStatement) constructorMethod.body().getFirst()).arguments()
                            ))
                            .append(");\n");
                } else if (superClassType != null) {
                    builder.append("        super();\n");
                }
                builder.append("        __tsjInitFields();\n");
                builder.append("        ").append(classSimpleName).append(".__tsjEnsureBootstrapped();\n");
                appendStrictNativeStatements(
                        builder,
                        model,
                        fieldTypesByName,
                        constructorMethod.body().subList(constructorBodyStart, constructorMethod.body().size()),
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        null,
                        "        "
                );
                builder.append("    }\n\n");
                builder.append("    static ").append(model.nativeClassSimpleName()).append(" __tsjCreate(");
                builder.append("final Object[] __tsjCtorArgs");
                builder.append(") {\n");
                builder.append("        return new ")
                        .append(model.nativeClassSimpleName())
                        .append("(")
                        .append(renderStrictNativeArgumentList(
                                "__tsjCtorArgs",
                                "__tsjArg",
                                constructorParameterTypes
                        ))
                        .append(");\n");
                builder.append("    }\n\n");
            }

            final Set<String> existingMethodNames = new LinkedHashSet<>(model.methodNameMap().values());
            for (String fieldName : model.declaration().fieldNames()) {
                final String javaFieldName = model.fieldNameMap().get(fieldName);
                final String fieldType = fieldTypesByName.getOrDefault(fieldName, "Object");
                final String fieldNullabilityAnnotation = fieldNullabilityAnnotationsByName.getOrDefault(fieldName, "");
                final String pascalFieldName = toPascalCase(javaFieldName);
                final String getterName = "get" + pascalFieldName;
                if (!existingMethodNames.contains(getterName)) {
                    if (!fieldNullabilityAnnotation.isBlank()) {
                        builder.append("    ").append(fieldNullabilityAnnotation).append('\n');
                    }
                    builder.append("    public ").append(fieldType).append(" ")
                            .append(getterName)
                            .append("() {\n");
                    builder.append("        return this.")
                            .append(javaFieldName)
                            .append(";\n");
                    builder.append("    }\n\n");
                }
                final String setterName = "set" + pascalFieldName;
                if (!existingMethodNames.contains(setterName)) {
                    final String setterParameterPrefix = fieldNullabilityAnnotation.isBlank()
                            ? ""
                            : fieldNullabilityAnnotation + " ";
                    builder.append("    public void ")
                            .append(setterName)
                            .append("(final ").append(setterParameterPrefix).append(fieldType).append(" value) {\n");
                    builder.append("        this.")
                            .append(javaFieldName)
                            .append(" = value;\n");
                    builder.append("    }\n\n");
                }
            }

            final IdentityHashMap<TsDecoratedMethod, Boolean> consumedMethods = new IdentityHashMap<>();
            if (decoratedConstructor != null) {
                consumedMethods.put(decoratedConstructor, Boolean.TRUE);
            }
            for (ClassMethod method : model.declaration().methods()) {
                final String javaMethodName = model.methodNameMap().get(method.name());
                final TsDecoratedMethod decoratedMethod = findDecoratedMethod(
                        decoratedClass == null ? List.of() : decoratedClass.methods(),
                        false,
                        method.name(),
                        method.parameters().size(),
                        consumedMethods
                );
                final Set<String> visibleMethodTypeParameters = new LinkedHashSet<>(classTypeParameters);
                if (decoratedMethod != null) {
                    visibleMethodTypeParameters.addAll(extractStrictNativeTypeParameterNames(decoratedMethod.genericParameters()));
                }
                final List<String> methodParameterTypes = resolveStrictNativeParameterTypes(
                        method.parameters(),
                        decoratedMethod == null ? List.of() : decoratedMethod.parameters(),
                        visibleMethodTypeParameters
                );
                final String returnType = resolveStrictNativeJavaType(
                        decoratedMethod == null ? null : decoratedMethod.returnTypeAnnotation(),
                        visibleMethodTypeParameters
                );
                appendAnnotationLines(
                        builder,
                        "    ",
                        decoratedMethod == null ? List.of() : decoratedMethod.decorators(),
                        importedDecoratorBindings,
                        annotationRenderContext
                );
                final String returnNullabilityAnnotation = renderStrictNativeNullabilityAnnotation(
                        decoratedMethod == null ? null : decoratedMethod.returnTypeAnnotation(),
                        returnType,
                        annotationRenderContext
                );
                if (!returnNullabilityAnnotation.isBlank()
                        && !hasResolvedDecorator(
                        decoratedMethod == null ? List.of() : decoratedMethod.decorators(),
                        importedDecoratorBindings,
                        returnNullabilityAnnotation.substring(1)
                )) {
                    builder.append("    ").append(returnNullabilityAnnotation).append('\n');
                }
                builder.append("    public ")
                        .append(renderStrictNativeTypeParameterDeclaration(
                                decoratedMethod == null ? List.of() : decoratedMethod.genericParameters(),
                                classTypeParameters
                        ));
                if (decoratedMethod != null && !decoratedMethod.genericParameters().isEmpty()) {
                    builder.append(' ');
                }
                builder.append(returnType).append(" ").append(javaMethodName).append("(");
                builder.append(renderStrictNativeParameters(
                        method.parameters(),
                        methodParameterTypes,
                        decoratedMethod == null ? List.of() : decoratedMethod.parameters(),
                        importedDecoratorBindings,
                        annotationRenderContext
                ));
                builder.append(") {\n");
                builder.append("        ").append(classSimpleName).append(".__tsjEnsureBootstrapped();\n");
                final Map<String, String> variableNames = new LinkedHashMap<>();
                final Map<String, String> variableTypes = new LinkedHashMap<>();
                final Set<String> boxedBindings = new LinkedHashSet<>();
                final Map<String, Integer> localNameCounts = new LinkedHashMap<>();
                final boolean boxMethodBindings = containsStrictNativeFunctionExpression(method.body());
                appendStrictNativeParameterLocals(
                        builder,
                        method.parameters(),
                        methodParameterTypes,
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        boxMethodBindings,
                        "        "
                );
                appendStrictNativeStatements(
                        builder,
                        model,
                        fieldTypesByName,
                        method.body(),
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        returnType,
                        "        "
                );
                if (!blockAlwaysExits(method.body())) {
                    builder.append("        return ")
                            .append(renderStrictNativeDefaultValue(returnType))
                            .append(";\n");
                }
                builder.append("    }\n\n");
            }

            for (ClassMethod method : model.declaration().staticMethods()) {
                final String javaMethodName = model.staticMethodNameMap().get(method.name());
                final TsDecoratedMethod decoratedMethod = findDecoratedMethod(
                        decoratedClass == null ? List.of() : decoratedClass.methods(),
                        false,
                        method.name(),
                        method.parameters().size(),
                        null
                );
                final Set<String> visibleMethodTypeParameters = new LinkedHashSet<>(classTypeParameters);
                if (decoratedMethod != null) {
                    visibleMethodTypeParameters.addAll(extractStrictNativeTypeParameterNames(
                            decoratedMethod.genericParameters()
                    ));
                }
                final List<String> methodParameterTypes = resolveStrictNativeParameterTypes(
                        method.parameters(),
                        decoratedMethod == null ? List.of() : decoratedMethod.parameters(),
                        visibleMethodTypeParameters
                );
                final String returnType = resolveStrictNativeJavaType(
                        decoratedMethod == null ? null : decoratedMethod.returnTypeAnnotation(),
                        visibleMethodTypeParameters
                );
                appendAnnotationLines(
                        builder,
                        "    ",
                        decoratedMethod == null ? List.of() : decoratedMethod.decorators(),
                        importedDecoratorBindings,
                        annotationRenderContext
                );
                final String returnNullabilityAnnotation = renderStrictNativeNullabilityAnnotation(
                        decoratedMethod == null ? null : decoratedMethod.returnTypeAnnotation(),
                        returnType,
                        annotationRenderContext
                );
                if (!returnNullabilityAnnotation.isBlank()
                        && !hasResolvedDecorator(
                        decoratedMethod == null ? List.of() : decoratedMethod.decorators(),
                        importedDecoratorBindings,
                        returnNullabilityAnnotation.substring(1)
                )) {
                    builder.append("    ").append(returnNullabilityAnnotation).append('\n');
                }
                builder.append("    public static ")
                        .append(renderStrictNativeTypeParameterDeclaration(
                                decoratedMethod == null ? List.of() : decoratedMethod.genericParameters(),
                                classTypeParameters
                        ));
                if (decoratedMethod != null && !decoratedMethod.genericParameters().isEmpty()) {
                    builder.append(' ');
                }
                builder.append(returnType).append(" ").append(javaMethodName).append("(");
                builder.append(renderStrictNativeParameters(
                        method.parameters(),
                        methodParameterTypes,
                        decoratedMethod == null ? List.of() : decoratedMethod.parameters(),
                        importedDecoratorBindings,
                        annotationRenderContext
                ));
                builder.append(") {\n");
                builder.append("        ").append(classSimpleName).append(".__tsjEnsureBootstrapped();\n");
                final Map<String, String> variableNames = new LinkedHashMap<>();
                final Map<String, String> variableTypes = new LinkedHashMap<>();
                final Set<String> boxedBindings = new LinkedHashSet<>();
                final Map<String, Integer> localNameCounts = new LinkedHashMap<>();
                final boolean boxMethodBindings = containsStrictNativeFunctionExpression(method.body());
                appendStrictNativeParameterLocals(
                        builder,
                        method.parameters(),
                        methodParameterTypes,
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        boxMethodBindings,
                        "        "
                );
                appendStrictNativeStatements(
                        builder,
                        model,
                        Map.of(),
                        method.body(),
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        returnType,
                        "        "
                );
                if (!blockAlwaysExits(method.body())) {
                    builder.append("        return ")
                            .append(renderStrictNativeDefaultValue(returnType))
                            .append(";\n");
                }
                builder.append("    }\n\n");
                final String mainParameterTypeAnnotation;
                if (decoratedMethod == null || decoratedMethod.parameters().size() != 1) {
                    mainParameterTypeAnnotation = null;
                } else {
                    mainParameterTypeAnnotation = decoratedMethod.parameters().getFirst().typeAnnotation();
                }
                final boolean jvmMainCandidate = "main".equals(method.name())
                        && method.parameters().size() == 1
                        && mainParameterTypeAnnotation != null
                        && ("string[]".equals(mainParameterTypeAnnotation.trim())
                        || "Array<string>".equals(mainParameterTypeAnnotation.trim()));
                if (jvmMainCandidate) {
                    builder.append("    public static void main(final String[] args) {\n");
                    builder.append("        ").append(classSimpleName).append(".__tsjEnsureBootstrapped();\n");
                    builder.append("        ").append(javaMethodName)
                            .append("(java.util.Arrays.asList(args));\n");
                    builder.append("    }\n\n");
                }
            }

            builder.append("    @Override\n");
            builder.append("    public Object __tsjInvoke(final String methodName, final Object... args) {\n");
            if (model.methodNameMap().isEmpty()) {
                if (superClassType != null) {
                    builder.append("        return super.__tsjInvoke(methodName, args);\n");
                } else {
                    builder.append("        throw new IllegalArgumentException(")
                            .append("\"TSJ strict-native method not found: \" + methodName);\n");
                }
            } else {
                builder.append("        return switch (methodName) {\n");
                for (Map.Entry<String, String> entry : model.methodNameMap().entrySet()) {
                    final ClassMethod method = findMethodDeclaration(model.declaration().methods(), entry.getKey());
                    final TsDecoratedMethod decoratedMethod = findDecoratedMethod(
                            decoratedClass == null ? List.of() : decoratedClass.methods(),
                            false,
                            entry.getKey(),
                            method == null ? 0 : method.parameters().size(),
                            null
                    );
                    final Set<String> visibleMethodTypeParameters = new LinkedHashSet<>(classTypeParameters);
                    if (decoratedMethod != null) {
                        visibleMethodTypeParameters.addAll(extractStrictNativeTypeParameterNames(
                                decoratedMethod.genericParameters()
                        ));
                    }
                    final List<String> methodParameterTypes = method == null
                            ? List.of()
                            : resolveStrictNativeParameterTypes(
                            method.parameters(),
                            decoratedMethod == null ? List.of() : decoratedMethod.parameters(),
                            visibleMethodTypeParameters
                    );
                    builder.append("            case \"")
                            .append(escapeJava(entry.getKey()))
                            .append("\" -> this.")
                            .append(entry.getValue())
                            .append("(")
                            .append(renderStrictNativeArgumentList("args", "__tsjArg", methodParameterTypes))
                            .append(");\n");
                }
                if (superClassType != null) {
                    builder.append("            default -> super.__tsjInvoke(methodName, args);\n");
                } else {
                    builder.append("            default -> throw new IllegalArgumentException(")
                            .append("\"TSJ strict-native method not found: \" + methodName);\n");
                }
                builder.append("        };\n");
            }
            builder.append("    }\n\n");

            builder.append("    @Override\n");
            builder.append("    public void __tsjSetField(final String fieldName, final Object value) {\n");
            if (model.fieldNameMap().isEmpty()) {
                if (superClassType != null) {
                    builder.append("        super.__tsjSetField(fieldName, value);\n");
                } else {
                    builder.append("        throw new IllegalArgumentException(")
                            .append("\"TSJ strict-native field not found: \" + fieldName);\n");
                }
            } else {
                builder.append("        switch (fieldName) {\n");
                for (Map.Entry<String, String> entry : model.fieldNameMap().entrySet()) {
                    final String fieldType = fieldTypesByName.getOrDefault(entry.getKey(), "Object");
                    builder.append("            case \"")
                            .append(escapeJava(entry.getKey()))
                            .append("\" -> this.")
                            .append(entry.getValue())
                            .append(" = ")
                            .append(renderStrictNativeCastExpression(fieldType, "value"))
                            .append(";\n");
                }
                if (superClassType != null) {
                    builder.append("            default -> super.__tsjSetField(fieldName, value);\n");
                } else {
                    builder.append("            default -> throw new IllegalArgumentException(")
                            .append("\"TSJ strict-native field not found: \" + fieldName);\n");
                }
                builder.append("        }\n");
            }
            builder.append("    }\n");
            builder.append("}\n");
            return builder.toString();
        }

        private String renderStrictNativeExpressionList(
                final StrictNativeClassModel model,
                final Map<String, String> variableNames,
                final Set<String> boxedBindings,
                final List<Expression> expressions
        ) {
            final List<String> renderedExpressions = new ArrayList<>();
            for (Expression expression : expressions) {
                renderedExpressions.add(emitStrictNativeExpression(model, variableNames, boxedBindings, expression));
            }
            return String.join(", ", renderedExpressions);
        }

        private String nextStrictNativeLambdaName(final String prefix) {
            return prefix + (strictNativeLambdaCounter++);
        }

        private String renderStrictNativeConstructionExpression(
                final StrictNativeClassModel model,
                final String argsVariable
        ) {
            final ClassMethod constructorMethod = model.declaration().constructorMethod();
            final int parameterCount = constructorMethod == null
                    ? 0
                    : constructorMethod.parameters().size();
            if (parameterCount == 0) {
                return "new " + model.nativeClassSimpleName() + "()";
            }
            return model.nativeClassSimpleName() + ".__tsjCreate(" + argsVariable + ")";
        }

        private String resolveStrictNativeTopLevelBindingTarget(
                final StrictNativeClassModel model,
                final String bindingName
        ) {
            if (topLevelBindingNames.contains(bindingName)) {
                return bindingName;
            }
            return model.moduleBindingTargets().get(bindingName);
        }

        private static ClassMethod findMethodDeclaration(
                final List<ClassMethod> methods,
                final String methodName
        ) {
            for (ClassMethod method : methods) {
                if (method.name().equals(methodName)) {
                    return method;
                }
            }
            return null;
        }

        private static String renderStrictNativeArgumentList(
                final String argsVariable,
                final String helperName,
                final List<String> parameterTypes
        ) {
            if (parameterTypes.isEmpty()) {
                return "";
            }
            final StringBuilder builder = new StringBuilder();
            for (int index = 0; index < parameterTypes.size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(renderStrictNativeCastExpression(
                        parameterTypes.get(index),
                        helperName
                                + "("
                                + argsVariable
                                + ", "
                                + index
                                + ")"
                ));
            }
            return builder.toString();
        }

        private static String renderStrictNativeParameters(
                final List<String> parameterNames,
                final List<String> parameterTypes,
                final List<TsDecoratedParameter> decoratedParameters,
                final Map<String, String> importedDecoratorBindings,
                final AnnotationRenderContext annotationRenderContext
        ) {
            if (parameterNames.isEmpty()) {
                return "";
            }
            final Map<Integer, List<TsDecoratorUse>> decoratorsByParameterIndex = new LinkedHashMap<>();
            final Map<Integer, TsDecoratedParameter> decoratedParametersByIndex = new LinkedHashMap<>();
            for (TsDecoratedParameter decoratedParameter : decoratedParameters) {
                decoratorsByParameterIndex.put(decoratedParameter.index(), decoratedParameter.decorators());
                decoratedParametersByIndex.put(decoratedParameter.index(), decoratedParameter);
            }
            final StringBuilder builder = new StringBuilder();
            final Map<String, Integer> parameterNameCounts = new LinkedHashMap<>();
            for (int index = 0; index < parameterNames.size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                final String inlineAnnotations = renderInlineAnnotations(
                        decoratorsByParameterIndex.getOrDefault(index, List.of()),
                        importedDecoratorBindings,
                        annotationRenderContext
                );
                if (!inlineAnnotations.isBlank()) {
                    builder.append(inlineAnnotations).append(' ');
                }
                final String parameterName = allocateUniqueName(
                        parameterNameCounts,
                        sanitizeJavaIdentifier(parameterNames.get(index))
                );
                final String parameterType = index < parameterTypes.size()
                        ? parameterTypes.get(index)
                        : "Object";
                final String nullabilityAnnotation = renderStrictNativeNullabilityAnnotation(
                        decoratedParametersByIndex.containsKey(index)
                                ? decoratedParametersByIndex.get(index).typeAnnotation()
                                : null,
                        parameterType,
                        annotationRenderContext
                );
                if (!nullabilityAnnotation.isBlank()
                        && !hasResolvedDecorator(
                        decoratorsByParameterIndex.getOrDefault(index, List.of()),
                        importedDecoratorBindings,
                        nullabilityAnnotation.substring(1)
                )) {
                    builder.append(nullabilityAnnotation).append(' ');
                }
                builder.append(parameterType).append(' ').append(parameterName);
            }
            return builder.toString();
        }

        private static String renderStrictNativeNullabilityAnnotation(
                final String typeAnnotation,
                final String javaType,
                final AnnotationRenderContext annotationRenderContext
        ) {
            if (annotationRenderContext == null || typeAnnotation == null || typeAnnotation.isBlank()) {
                return "";
            }
            if (javaType == null || javaType.isBlank() || "void".equals(javaType) || isStrictNativePrimitiveJavaType(javaType)) {
                return "";
            }
            final JavaNullabilityAnalyzer.NullabilityState nullabilityState =
                    inferStrictNativeNullabilityState(typeAnnotation);
            return switch (nullabilityState) {
                case NON_NULL -> annotationRenderContext.nonNullAnnotationClassName() == null
                        ? ""
                        : "@" + annotationRenderContext.nonNullAnnotationClassName();
                case NULLABLE -> annotationRenderContext.nullableAnnotationClassName() == null
                        ? ""
                        : "@" + annotationRenderContext.nullableAnnotationClassName();
                case PLATFORM -> "";
            };
        }

        private static JavaNullabilityAnalyzer.NullabilityState inferStrictNativeNullabilityState(
                final String typeAnnotation
        ) {
            final String normalized = stripStrictNativeOuterParens(typeAnnotation.trim());
            if (normalized.isBlank()) {
                return JavaNullabilityAnalyzer.NullabilityState.PLATFORM;
            }
            final List<String> unionParts = splitStrictNativeTopLevel(normalized, '|');
            boolean nullable = false;
            String primary = null;
            for (String part : unionParts) {
                final String candidate = stripStrictNativeOuterParens(part.trim());
                if (candidate.isBlank()) {
                    continue;
                }
                if ("null".equals(candidate) || "undefined".equals(candidate)) {
                    nullable = true;
                    continue;
                }
                if ("any".equals(candidate) || "unknown".equals(candidate)) {
                    return JavaNullabilityAnalyzer.NullabilityState.PLATFORM;
                }
                if (primary == null) {
                    primary = candidate;
                    continue;
                }
                if (!primary.equals(candidate)) {
                    return JavaNullabilityAnalyzer.NullabilityState.PLATFORM;
                }
            }
            if (primary == null) {
                return JavaNullabilityAnalyzer.NullabilityState.PLATFORM;
            }
            return nullable
                    ? JavaNullabilityAnalyzer.NullabilityState.NULLABLE
                    : JavaNullabilityAnalyzer.NullabilityState.NON_NULL;
        }

        private static boolean isStrictNativePrimitiveJavaType(final String javaType) {
            return "boolean".equals(javaType)
                    || "byte".equals(javaType)
                    || "short".equals(javaType)
                    || "int".equals(javaType)
                    || "long".equals(javaType)
                    || "float".equals(javaType)
                    || "double".equals(javaType)
                    || "char".equals(javaType);
        }

        private String resolveStrictNativeFieldType(
                final String fieldName,
                final List<TsDecoratedField> decoratedFields,
                final Set<String> visibleTypeParameters
        ) {
            if (decoratedFields == null || decoratedFields.isEmpty()) {
                return "Object";
            }
            for (TsDecoratedField field : decoratedFields) {
                if (fieldName.equals(field.fieldName())) {
                    return resolveStrictNativeJavaType(field.typeAnnotation(), visibleTypeParameters);
                }
            }
            return "Object";
        }

        private List<String> resolveStrictNativeParameterTypes(
                final List<String> parameterNames,
                final List<TsDecoratedParameter> decoratedParameters,
                final Set<String> visibleTypeParameters
        ) {
            final List<String> parameterTypes = new ArrayList<>(java.util.Collections.nCopies(parameterNames.size(), "Object"));
            for (TsDecoratedParameter parameter : decoratedParameters) {
                if (parameter.index() < 0 || parameter.index() >= parameterNames.size()) {
                    continue;
                }
                parameterTypes.set(
                        parameter.index(),
                        resolveStrictNativeJavaType(parameter.typeAnnotation(), visibleTypeParameters)
                );
            }
            return List.copyOf(parameterTypes);
        }

        private String resolveStrictNativeJavaType(
                final String typeAnnotation,
                final Set<String> visibleTypeParameters
        ) {
            if (typeAnnotation == null || typeAnnotation.isBlank()) {
                return "Object";
            }
            final String normalized = stripStrictNativeOuterParens(typeAnnotation.trim());
            if (normalized.isBlank()) {
                return "Object";
            }
            final List<String> unionParts = splitStrictNativeTopLevel(normalized, '|');
            String base = null;
            for (String part : unionParts) {
                final String candidate = stripStrictNativeOuterParens(part.trim());
                if (candidate.isBlank() || "null".equals(candidate) || "undefined".equals(candidate)) {
                    continue;
                }
                if (base == null) {
                    base = candidate;
                    continue;
                }
                if (!base.equals(candidate)) {
                    return "Object";
                }
            }
            if (base == null) {
                return "Object";
            }
            if (base.endsWith("[]") && base.length() > 2) {
                final String elementType = base.substring(0, base.length() - 2).trim();
                return "java.util.List<" + resolveStrictNativeJavaType(elementType, visibleTypeParameters) + ">";
            }
            if (base.startsWith("Array<") && base.endsWith(">")) {
                final String inner = base.substring("Array<".length(), base.length() - 1);
                final List<String> arguments = splitStrictNativeTopLevel(inner, ',');
                if (arguments.size() == 1) {
                    return "java.util.List<"
                            + resolveStrictNativeJavaType(arguments.getFirst().trim(), visibleTypeParameters)
                            + ">";
                }
                return "Object";
            }
            if (base.startsWith("Record<") && base.endsWith(">")) {
                final String inner = base.substring("Record<".length(), base.length() - 1);
                final List<String> arguments = splitStrictNativeTopLevel(inner, ',');
                if (arguments.size() == 2) {
                    final String keyType = stripStrictNativeOuterParens(arguments.getFirst().trim());
                    if ("string".equals(keyType)) {
                        return "java.util.Map<String, "
                                + resolveStrictNativeJavaType(arguments.get(1).trim(), visibleTypeParameters)
                                + ">";
                    }
                }
                return "Object";
            }
            final int genericStart = findStrictNativeTopLevelGenericStart(base);
            if (genericStart > 0 && base.endsWith(">")) {
                final String rawBase = base.substring(0, genericStart).trim();
                final String rawArgs = base.substring(genericStart + 1, base.length() - 1);
                final String javaBase = resolveStrictNativeRawBaseType(rawBase, visibleTypeParameters);
                if ("Object".equals(javaBase)) {
                    return "Object";
                }
                final List<String> javaArguments = new ArrayList<>();
                for (String argument : splitStrictNativeTopLevel(rawArgs, ',')) {
                    javaArguments.add(resolveStrictNativeJavaType(argument.trim(), visibleTypeParameters));
                }
                return javaBase + "<" + String.join(", ", javaArguments) + ">";
            }
            return resolveStrictNativeRawBaseType(base, visibleTypeParameters);
        }

        private String resolveStrictNativeRawBaseType(
                final String base,
                final Set<String> visibleTypeParameters
        ) {
            final String normalized = stripStrictNativeOuterParens(base.trim());
            if (normalized.isBlank()) {
                return "Object";
            }
            if (visibleTypeParameters.contains(normalized)) {
                return normalized;
            }
            for (StrictNativeClassModel model : strictNativeClassModels) {
                if (model.tsClassName().equals(normalized)) {
                    return model.nativeClassSimpleName();
                }
            }
            return switch (normalized) {
                case "string", "String" -> "String";
                case "number", "Number", "Double" -> "Number";
                case "boolean", "Boolean" -> "Boolean";
                case "bigint", "BigInt" -> "java.math.BigInteger";
                case "any", "unknown", "object", "Object", "void", "null", "undefined" -> "Object";
                case "Array", "List" -> "java.util.List";
                case "Map", "Record" -> "java.util.Map";
                case "Set" -> "java.util.Set";
                default -> normalized.contains(".") ? normalized : "Object";
            };
        }

        private String renderStrictNativeTypeParameterDeclaration(
                final List<String> rawTypeParameters,
                final Set<String> visibleOuterTypeParameters
        ) {
            if (rawTypeParameters.isEmpty()) {
                return "";
            }
            final List<String> rendered = new ArrayList<>();
            final Set<String> visible = new LinkedHashSet<>(visibleOuterTypeParameters);
            for (String rawTypeParameter : rawTypeParameters) {
                final String normalized = rawTypeParameter == null ? "" : rawTypeParameter.trim();
                if (normalized.isBlank()) {
                    continue;
                }
                final int extendsIndex = normalized.indexOf(" extends ");
                if (extendsIndex < 0) {
                    rendered.add(normalized);
                    visible.add(normalized);
                    continue;
                }
                final String identifier = normalized.substring(0, extendsIndex).trim();
                final String rawBound = normalized.substring(extendsIndex + " extends ".length()).trim();
                final List<String> renderedBounds = new ArrayList<>();
                for (String bound : splitStrictNativeTopLevel(rawBound, '&')) {
                    renderedBounds.add(resolveStrictNativeJavaType(bound.trim(), visible));
                }
                rendered.add(identifier + " extends " + String.join(" & ", renderedBounds));
                visible.add(identifier);
            }
            if (rendered.isEmpty()) {
                return "";
            }
            return "<" + String.join(", ", rendered) + ">";
        }

        private static Set<String> extractStrictNativeTypeParameterNames(final List<String> rawTypeParameters) {
            if (rawTypeParameters.isEmpty()) {
                return Set.of();
            }
            final Set<String> identifiers = new LinkedHashSet<>();
            for (String rawTypeParameter : rawTypeParameters) {
                if (rawTypeParameter == null) {
                    continue;
                }
                final String normalized = rawTypeParameter.trim();
                if (normalized.isBlank()) {
                    continue;
                }
                final int extendsIndex = normalized.indexOf(" extends ");
                identifiers.add(extendsIndex < 0 ? normalized : normalized.substring(0, extendsIndex).trim());
            }
            return Set.copyOf(identifiers);
        }

        private static String renderStrictNativeDefaultValue(final String javaType) {
            return "Object".equals(javaType) ? "dev.tsj.runtime.TsjRuntime.undefined()" : "null";
        }

        private static String renderStrictNativeCastExpression(
                final String javaType,
                final String expression
        ) {
            if ("Object".equals(javaType)) {
                return expression;
            }
            return "(" + javaType + ") " + expression;
        }

        private static String stripStrictNativeOuterParens(final String value) {
            String normalized = value.trim();
            boolean changed = true;
            while (changed && normalized.length() >= 2 && normalized.startsWith("(") && normalized.endsWith(")")) {
                changed = false;
                int depth = 0;
                boolean wrapsWholeExpression = true;
                for (int index = 0; index < normalized.length(); index++) {
                    final char current = normalized.charAt(index);
                    if (current == '(') {
                        depth++;
                    } else if (current == ')') {
                        depth--;
                        if (depth == 0 && index < normalized.length() - 1) {
                            wrapsWholeExpression = false;
                            break;
                        }
                    }
                }
                if (wrapsWholeExpression) {
                    normalized = normalized.substring(1, normalized.length() - 1).trim();
                    changed = true;
                }
            }
            return normalized;
        }

        private static List<String> splitStrictNativeTopLevel(final String text, final char separator) {
            final List<String> parts = new ArrayList<>();
            int start = 0;
            int angleDepth = 0;
            int parenDepth = 0;
            int bracketDepth = 0;
            int braceDepth = 0;
            for (int index = 0; index < text.length(); index++) {
                final char current = text.charAt(index);
                switch (current) {
                    case '<' -> angleDepth++;
                    case '>' -> angleDepth = Math.max(0, angleDepth - 1);
                    case '(' -> parenDepth++;
                    case ')' -> parenDepth = Math.max(0, parenDepth - 1);
                    case '[' -> bracketDepth++;
                    case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                    case '{' -> braceDepth++;
                    case '}' -> braceDepth = Math.max(0, braceDepth - 1);
                    default -> {
                    }
                }
                if (current == separator
                        && angleDepth == 0
                        && parenDepth == 0
                        && bracketDepth == 0
                        && braceDepth == 0) {
                    parts.add(text.substring(start, index));
                    start = index + 1;
                }
            }
            parts.add(text.substring(start));
            return List.copyOf(parts);
        }

        private static int findStrictNativeTopLevelGenericStart(final String text) {
            int angleDepth = 0;
            for (int index = 0; index < text.length(); index++) {
                final char current = text.charAt(index);
                if (current == '<') {
                    if (angleDepth == 0) {
                        return index;
                    }
                    angleDepth++;
                    continue;
                }
                if (current == '>') {
                    angleDepth = Math.max(0, angleDepth - 1);
                }
            }
            return -1;
        }

        private static void appendStrictNativeParameterLocals(
                final StringBuilder builder,
                final List<String> parameters,
                final List<String> parameterTypes,
                final Map<String, String> variableNames,
                final Map<String, String> variableTypes,
                final Set<String> boxedBindings,
                final Map<String, Integer> localNameCounts,
                final boolean boxBindings,
                final String indent
        ) {
            for (int index = 0; index < parameters.size(); index++) {
                final String parameter = parameters.get(index);
                final String parameterName = allocateUniqueName(localNameCounts, sanitizeJavaIdentifier(parameter));
                variableTypes.put(parameter, index < parameterTypes.size() ? parameterTypes.get(index) : "Object");
                if (boxBindings) {
                    final String cellName = allocateUniqueName(localNameCounts, parameterName + "Cell");
                    variableNames.put(parameter, cellName);
                    boxedBindings.add(parameter);
                    builder.append(indent)
                            .append("final dev.tsj.runtime.TsjCell ")
                            .append(cellName)
                            .append(" = new dev.tsj.runtime.TsjCell(")
                            .append(parameterName)
                            .append(");\n");
                } else {
                    variableNames.put(parameter, parameterName);
                }
            }
        }

        private static void predeclareStrictNativeFunctionBindings(
                final Set<String> bindings,
                final List<Statement> statements
        ) {
            for (Statement statement : statements) {
                if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                    bindings.add(declarationStatement.declaration().name());
                }
            }
        }

        private static void predeclareStrictNativeBoxedBindings(
                final StringBuilder builder,
                final List<Statement> statements,
                final Map<String, String> variableNames,
                final Map<String, String> variableTypes,
                final Set<String> boxedBindings,
                final Map<String, Integer> localNameCounts,
                final String indent
        ) {
            final Set<String> localBindings = new LinkedHashSet<>();
            for (Statement statement : statements) {
                if (statement instanceof VariableDeclaration variableDeclaration) {
                    final String name = variableDeclaration.name();
                    if (!localBindings.add(name)) {
                        continue;
                    }
                    final String cellName = allocateUniqueName(
                            localNameCounts,
                            sanitizeJavaIdentifier(name) + "Cell"
                    );
                    variableNames.put(name, cellName);
                    variableTypes.put(name, "Object");
                    boxedBindings.add(name);
                    builder.append(indent)
                            .append("final dev.tsj.runtime.TsjCell ")
                            .append(cellName)
                            .append(" = new dev.tsj.runtime.TsjCell(null);\n");
                    continue;
                }
                if (!(statement instanceof FunctionDeclarationStatement declarationStatement)) {
                    continue;
                }
                final String name = declarationStatement.declaration().name();
                if (!localBindings.add(name)) {
                    continue;
                }
                final String cellName = allocateUniqueName(
                        localNameCounts,
                        sanitizeJavaIdentifier(name) + "Cell"
                );
                variableNames.put(name, cellName);
                variableTypes.put(name, "Object");
                boxedBindings.add(name);
                builder.append(indent)
                        .append("final dev.tsj.runtime.TsjCell ")
                        .append(cellName)
                        .append(" = new dev.tsj.runtime.TsjCell(null);\n");
            }
        }

        private void emitStrictNativeHoistedFunctionAssignments(
                final StringBuilder builder,
                final StrictNativeClassModel model,
                final List<Statement> statements,
                final Map<String, String> variableNames,
                final Set<String> boxedBindings,
                final String thisReference,
                final boolean dynamicThisScope,
                final String indent
        ) {
            for (Statement statement : statements) {
                if (!(statement instanceof FunctionDeclarationStatement declarationStatement)) {
                    continue;
                }
                final String cellName = variableNames.get(declarationStatement.declaration().name());
                if (cellName == null) {
                    throw strictNativeLoweringFailure(
                            "Unknown function declaration binding `"
                                    + declarationStatement.declaration().name()
                                    + "`."
                    );
                }
                builder.append(indent)
                        .append(cellName)
                        .append(".set(")
                        .append(emitStrictNativeFunctionValue(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                new FunctionExpression(
                                        declarationStatement.declaration().parameters(),
                                        declarationStatement.declaration().body(),
                                        declarationStatement.declaration().async(),
                                        declarationStatement.declaration().generator(),
                                        FunctionThisMode.DYNAMIC
                                )
                        ))
                        .append(");\n");
            }
        }

        private static boolean containsStrictNativeFunctionExpression(final List<Statement> statements) {
            for (Statement statement : statements) {
                if (containsStrictNativeFunctionExpression(statement)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsStrictNativeFunctionExpression(final Statement statement) {
            if (statement instanceof VariableDeclaration variableDeclaration) {
                return containsStrictNativeFunctionExpression(variableDeclaration.expression());
            }
            if (statement instanceof AssignmentStatement assignmentStatement) {
                return containsStrictNativeFunctionExpression(assignmentStatement.target())
                        || containsStrictNativeFunctionExpression(assignmentStatement.expression());
            }
            if (statement instanceof FunctionDeclarationStatement) {
                return true;
            }
            if (statement instanceof ReturnStatement returnStatement) {
                return containsStrictNativeFunctionExpression(returnStatement.expression());
            }
            if (statement instanceof ThrowStatement throwStatement) {
                return containsStrictNativeFunctionExpression(throwStatement.expression());
            }
            if (statement instanceof ExpressionStatement expressionStatement) {
                return containsStrictNativeFunctionExpression(expressionStatement.expression());
            }
            if (statement instanceof IfStatement ifStatement) {
                return containsStrictNativeFunctionExpression(ifStatement.condition())
                        || containsStrictNativeFunctionExpression(ifStatement.thenBlock())
                        || containsStrictNativeFunctionExpression(ifStatement.elseBlock());
            }
            if (statement instanceof WhileStatement whileStatement) {
                return containsStrictNativeFunctionExpression(whileStatement.condition())
                        || containsStrictNativeFunctionExpression(whileStatement.body());
            }
            if (statement instanceof TryStatement tryStatement) {
                return containsStrictNativeFunctionExpression(tryStatement.tryBlock())
                        || containsStrictNativeFunctionExpression(tryStatement.catchBlock())
                        || containsStrictNativeFunctionExpression(tryStatement.finallyBlock());
            }
            if (statement instanceof LabeledStatement labeledStatement) {
                return containsStrictNativeFunctionExpression(labeledStatement.statement());
            }
            return false;
        }

        private static boolean containsStrictNativeFunctionExpression(final Expression expression) {
            if (expression instanceof UnaryExpression unaryExpression) {
                return containsStrictNativeFunctionExpression(unaryExpression.expression());
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                return containsStrictNativeFunctionExpression(binaryExpression.left())
                        || containsStrictNativeFunctionExpression(binaryExpression.right());
            }
            if (expression instanceof AssignmentExpression assignmentExpression) {
                return containsStrictNativeFunctionExpression(assignmentExpression.target())
                        || containsStrictNativeFunctionExpression(assignmentExpression.expression());
            }
            if (expression instanceof ConditionalExpression conditionalExpression) {
                return containsStrictNativeFunctionExpression(conditionalExpression.condition())
                        || containsStrictNativeFunctionExpression(conditionalExpression.whenTrue())
                        || containsStrictNativeFunctionExpression(conditionalExpression.whenFalse());
            }
            if (expression instanceof ArrayLiteralExpression arrayLiteralExpression) {
                for (Expression element : arrayLiteralExpression.elements()) {
                    if (containsStrictNativeFunctionExpression(element)) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof ObjectLiteralExpression objectLiteralExpression) {
                for (ObjectLiteralEntry entry : objectLiteralExpression.entries()) {
                    if (containsStrictNativeFunctionExpression(entry.value())) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof FunctionExpression) {
                return true;
            }
            if (expression instanceof CallExpression callExpression) {
                if (containsStrictNativeFunctionExpression(callExpression.callee())) {
                    return true;
                }
                for (Expression argument : callExpression.arguments()) {
                    if (containsStrictNativeFunctionExpression(argument)) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof MemberAccessExpression memberAccessExpression) {
                return containsStrictNativeFunctionExpression(memberAccessExpression.receiver());
            }
            if (expression instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                return containsStrictNativeFunctionExpression(optionalMemberAccessExpression.receiver());
            }
            if (expression instanceof OptionalCallExpression optionalCallExpression) {
                if (containsStrictNativeFunctionExpression(optionalCallExpression.callee())) {
                    return true;
                }
                for (Expression argument : optionalCallExpression.arguments()) {
                    if (containsStrictNativeFunctionExpression(argument)) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof NewExpression newExpression) {
                if (containsStrictNativeFunctionExpression(newExpression.constructor())) {
                    return true;
                }
                for (Expression argument : newExpression.arguments()) {
                    if (containsStrictNativeFunctionExpression(argument)) {
                        return true;
                    }
                }
                return false;
            }
            if (expression instanceof YieldExpression yieldExpression) {
                return containsStrictNativeFunctionExpression(yieldExpression.expression());
            }
            return false;
        }

        private void appendStrictNativeParameterBindings(
                final StringBuilder builder,
                final List<String> parameters,
                final String argsVariable,
                final Map<String, String> variableNames,
                final Map<String, Integer> localNameCounts,
                final String indent
        ) {
            for (int index = 0; index < parameters.size(); index++) {
                final String parameter = parameters.get(index);
                final String javaName = allocateUniqueName(localNameCounts, sanitizeJavaIdentifier(parameter));
                variableNames.put(parameter, javaName);
                builder.append(indent)
                        .append("Object ")
                        .append(javaName)
                        .append(" = __tsjArg(")
                        .append(argsVariable)
                        .append(", ")
                        .append(index)
                        .append(");\n");
            }
        }

        private void appendStrictNativeStatements(
                final StringBuilder builder,
                final StrictNativeClassModel model,
                final Map<String, String> fieldTypesByName,
                final List<Statement> statements,
                final Map<String, String> variableNames,
                final Map<String, String> variableTypes,
                final Set<String> boxedBindings,
                final Map<String, Integer> localNameCounts,
                final String returnType,
                final String indent
        ) {
            appendStrictNativeStatements(
                    builder,
                    model,
                    fieldTypesByName,
                    statements,
                    variableNames,
                    variableTypes,
                    boxedBindings,
                    localNameCounts,
                    "this",
                    false,
                    returnType,
                    indent
            );
        }

        private void appendStrictNativeStatements(
                final StringBuilder builder,
                final StrictNativeClassModel model,
                final Map<String, String> fieldTypesByName,
                final List<Statement> statements,
                final Map<String, String> variableNames,
                final Map<String, String> variableTypes,
                final Set<String> boxedBindings,
                final Map<String, Integer> localNameCounts,
                final String thisReference,
                final boolean dynamicThisScope,
                final String returnType,
                final String indent
        ) {
            appendStrictNativeStatements(
                    builder,
                    model,
                    fieldTypesByName,
                    statements,
                    variableNames,
                    variableTypes,
                    boxedBindings,
                    localNameCounts,
                    thisReference,
                    dynamicThisScope,
                    new LinkedHashMap<>(),
                    new LinkedHashSet<>(),
                    returnType,
                    indent
            );
        }

        private void appendStrictNativeStatements(
                final StringBuilder builder,
                final StrictNativeClassModel model,
                final Map<String, String> fieldTypesByName,
                final List<Statement> statements,
                final Map<String, String> variableNames,
                final Map<String, String> variableTypes,
                final Set<String> boxedBindings,
                final Map<String, Integer> localNameCounts,
                final String thisReference,
                final boolean dynamicThisScope,
                final Map<String, String> labelNames,
                final Set<String> loopLabels,
                final String returnType,
                final String indent
        ) {
            final boolean boxScopeBindings = containsStrictNativeFunctionExpression(statements);
            if (boxScopeBindings) {
                predeclareStrictNativeBoxedBindings(
                        builder,
                        statements,
                        variableNames,
                        variableTypes,
                        boxedBindings,
                        localNameCounts,
                        indent
                );
                emitStrictNativeHoistedFunctionAssignments(
                        builder,
                        model,
                        statements,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        indent
                );
            }
            for (Statement statement : statements) {
                if (statement instanceof VariableDeclaration variableDeclaration) {
                    variableTypes.put(variableDeclaration.name(), "Object");
                    final String initializer = emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            variableDeclaration.expression()
                    );
                    if (boxScopeBindings) {
                        final String javaName = variableNames.get(variableDeclaration.name());
                        if (javaName == null) {
                            throw strictNativeLoweringFailure(
                                    "Unknown boxed variable declaration `" + variableDeclaration.name() + "`."
                            );
                        }
                        builder.append(indent)
                                .append(javaName)
                                .append(".set(")
                                .append(initializer)
                                .append(");\n");
                    } else {
                        final String javaName = allocateUniqueName(
                                localNameCounts,
                                sanitizeJavaIdentifier(variableDeclaration.name())
                        );
                        variableNames.put(variableDeclaration.name(), javaName);
                        builder.append(indent)
                                .append("Object ")
                                .append(javaName)
                                .append(" = ")
                                .append(initializer)
                                .append(";\n");
                    }
                    continue;
                }
                if (statement instanceof AssignmentStatement assignmentStatement) {
                    final String targetType = resolveStrictNativeAssignmentType(
                            model,
                            fieldTypesByName,
                            variableTypes,
                            dynamicThisScope,
                            assignmentStatement.target()
                    );
                    String renderedExpression = emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            assignmentStatement.expression()
                    );
                    if (!"Object".equals(targetType)) {
                        renderedExpression = assignmentStatement.expression() instanceof UndefinedLiteral
                                ? renderStrictNativeDefaultValue(targetType)
                                : renderStrictNativeCastExpression(targetType, renderedExpression);
                    }
                    if (assignmentStatement.target() instanceof VariableExpression variableExpression
                            && boxedBindings.contains(variableExpression.name())) {
                        final String cellName = variableNames.get(variableExpression.name());
                        if (cellName == null) {
                            throw strictNativeLoweringFailure(
                                    "Unknown boxed variable assignment target `" + variableExpression.name() + "`."
                            );
                        }
                        builder.append(indent)
                                .append(cellName)
                                .append(".set(")
                                .append(renderedExpression)
                                .append(");\n");
                    } else if (assignmentStatement.target() instanceof VariableExpression variableExpression
                            && topLevelBindingNames.contains(variableExpression.name())) {
                        final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                                model,
                                variableExpression.name()
                        );
                        builder.append(indent)
                                .append(classSimpleName)
                                .append(".__tsjResolveTopLevelBinding(\"")
                                .append(escapeJava(bindingTarget))
                                .append("\").set(")
                                .append(renderedExpression)
                                .append(");\n");
                    } else if (assignmentStatement.target() instanceof VariableExpression variableExpression
                            && model.moduleBindingTargets().containsKey(variableExpression.name())) {
                        final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                                model,
                                variableExpression.name()
                        );
                        builder.append(indent)
                                .append(classSimpleName)
                                .append(".__tsjResolveTopLevelBinding(\"")
                                .append(escapeJava(bindingTarget))
                                .append("\").set(")
                                .append(renderedExpression)
                                .append(");\n");
                    } else if (dynamicThisScope
                            && assignmentStatement.target() instanceof MemberAccessExpression memberAccessExpression
                            && memberAccessExpression.receiver() instanceof ThisExpression) {
                        builder.append(indent)
                                .append("dev.tsj.runtime.TsjRuntime.setProperty(")
                                .append(thisReference)
                                .append(", \"")
                                .append(escapeJava(memberAccessExpression.member()))
                                .append("\", ")
                                .append(renderedExpression)
                                .append(");\n");
                    } else if (assignmentStatement.target() instanceof MemberAccessExpression memberAccessExpression
                            && !(memberAccessExpression.receiver() instanceof ThisExpression)
                            && !(memberAccessExpression.receiver() instanceof VariableExpression variableExpression
                            && findStrictNativeClassModel(variableExpression.name()) != null
                            && findStrictNativeClassModel(variableExpression.name())
                                    .staticFieldNameMap()
                                    .containsKey(memberAccessExpression.member()))) {
                        final String receiver = emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                memberAccessExpression.receiver()
                        );
                        builder.append(indent)
                                .append("dev.tsj.runtime.TsjRuntime.setProperty(")
                                .append(receiver)
                                .append(", \"")
                                .append(escapeJava(memberAccessExpression.member()))
                                .append("\", ")
                                .append(renderedExpression)
                                .append(");\n");
                    } else if (assignmentStatement.target() instanceof CallExpression callExpression
                            && JvmBytecodeCompiler.isStrictNativeIndexAssignmentTarget(callExpression)) {
                        final String receiver = emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                callExpression.arguments().get(0)
                        );
                        final String key = emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                callExpression.arguments().get(1)
                        );
                        builder.append(indent)
                                .append("dev.tsj.runtime.TsjRuntime.setPropertyDynamic(")
                                .append(receiver)
                                .append(", ")
                                .append(key)
                                .append(", ")
                                .append(renderedExpression)
                                .append(");\n");
                    } else {
                        final String left = emitStrictNativeAssignmentTarget(
                                model,
                                variableNames,
                                assignmentStatement.target()
                        );
                        builder.append(indent)
                                .append(left)
                                .append(" = ")
                                .append(renderedExpression)
                                .append(";\n");
                    }
                    continue;
                }
                if (statement instanceof FunctionDeclarationStatement declarationStatement) {
                    continue;
                }
                if (statement instanceof ReturnStatement returnStatement) {
                    String renderedExpression = emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            returnStatement.expression()
                    );
                    if (returnType != null && !"Object".equals(returnType)) {
                        renderedExpression = returnStatement.expression() instanceof UndefinedLiteral
                                ? renderStrictNativeDefaultValue(returnType)
                                : renderStrictNativeCastExpression(returnType, renderedExpression);
                    }
                    builder.append(indent)
                            .append("return ")
                            .append(renderedExpression)
                            .append(";\n");
                    continue;
                }
                if (statement instanceof ThrowStatement throwStatement) {
                    builder.append(indent)
                            .append("throw dev.tsj.runtime.TsjRuntime.raise(")
                            .append(emitStrictNativeExpression(
                                    model,
                                    variableNames,
                                    boxedBindings,
                                    thisReference,
                                    dynamicThisScope,
                                    throwStatement.expression()
                            ))
                            .append(");\n");
                    continue;
                }
                if (statement instanceof ExpressionStatement expressionStatement) {
                    builder.append(indent)
                            .append(emitStrictNativeExpression(
                                    model,
                                    variableNames,
                                    boxedBindings,
                                    thisReference,
                                    dynamicThisScope,
                                    expressionStatement.expression()
                            ))
                            .append(";\n");
                    continue;
                }
                if (statement instanceof IfStatement ifStatement) {
                    builder.append(indent)
                            .append("if (dev.tsj.runtime.TsjRuntime.truthy(")
                            .append(emitStrictNativeExpression(
                                    model,
                                    variableNames,
                                    boxedBindings,
                                    thisReference,
                                    dynamicThisScope,
                                    ifStatement.condition()
                            ))
                            .append(")) {\n");
                    appendStrictNativeStatements(
                            builder,
                            model,
                            fieldTypesByName,
                            ifStatement.thenBlock(),
                            new LinkedHashMap<>(variableNames),
                            new LinkedHashMap<>(variableTypes),
                            new LinkedHashSet<>(boxedBindings),
                            new LinkedHashMap<>(localNameCounts),
                            thisReference,
                            dynamicThisScope,
                            new LinkedHashMap<>(labelNames),
                            new LinkedHashSet<>(loopLabels),
                            returnType,
                            indent + "    "
                    );
                    builder.append(indent).append("}");
                    if (!ifStatement.elseBlock().isEmpty()) {
                        builder.append(" else {\n");
                        appendStrictNativeStatements(
                                builder,
                                model,
                                fieldTypesByName,
                                ifStatement.elseBlock(),
                                new LinkedHashMap<>(variableNames),
                                new LinkedHashMap<>(variableTypes),
                                new LinkedHashSet<>(boxedBindings),
                                new LinkedHashMap<>(localNameCounts),
                                thisReference,
                                dynamicThisScope,
                                new LinkedHashMap<>(labelNames),
                                new LinkedHashSet<>(loopLabels),
                                returnType,
                                indent + "    "
                        );
                        builder.append(indent).append("}");
                    }
                    builder.append("\n");
                    continue;
                }
                if (statement instanceof LabeledStatement labeledStatement) {
                    appendStrictNativeLabeledStatement(
                            builder,
                            model,
                            fieldTypesByName,
                            labeledStatement,
                            variableNames,
                            variableTypes,
                            boxedBindings,
                            localNameCounts,
                            thisReference,
                            dynamicThisScope,
                            labelNames,
                            loopLabels,
                            returnType,
                            indent
                    );
                    continue;
                }
                if (statement instanceof WhileStatement whileStatement) {
                    builder.append(indent)
                            .append("while (dev.tsj.runtime.TsjRuntime.truthy(")
                            .append(emitStrictNativeExpression(
                                    model,
                                    variableNames,
                                    boxedBindings,
                                    thisReference,
                                    dynamicThisScope,
                                    whileStatement.condition()
                            ))
                            .append(")) {\n");
                    appendStrictNativeStatements(
                            builder,
                            model,
                            fieldTypesByName,
                            whileStatement.body(),
                            new LinkedHashMap<>(variableNames),
                            new LinkedHashMap<>(variableTypes),
                            new LinkedHashSet<>(boxedBindings),
                            new LinkedHashMap<>(localNameCounts),
                            thisReference,
                            dynamicThisScope,
                            new LinkedHashMap<>(labelNames),
                            new LinkedHashSet<>(loopLabels),
                            returnType,
                            indent + "    "
                    );
                    builder.append(indent).append("}\n");
                    continue;
                }
                if (statement instanceof TryStatement tryStatement) {
                    builder.append(indent).append("try {\n");
                    appendStrictNativeStatements(
                            builder,
                            model,
                            fieldTypesByName,
                            tryStatement.tryBlock(),
                            new LinkedHashMap<>(variableNames),
                            new LinkedHashMap<>(variableTypes),
                            new LinkedHashSet<>(boxedBindings),
                            new LinkedHashMap<>(localNameCounts),
                            thisReference,
                            dynamicThisScope,
                            returnType,
                            indent + "    "
                    );
                    builder.append(indent).append("}");
                    if (tryStatement.hasCatch()) {
                        builder.append(" catch (RuntimeException __tsjCaughtError) {\n");
                        final Map<String, String> catchVariableNames = new LinkedHashMap<>(variableNames);
                        final Map<String, String> catchVariableTypes = new LinkedHashMap<>(variableTypes);
                        final Set<String> catchBoxedBindings = new LinkedHashSet<>(boxedBindings);
                        final Map<String, Integer> catchLocalNameCounts = new LinkedHashMap<>(localNameCounts);
                        if (tryStatement.catchBinding() != null) {
                            final String javaName = allocateUniqueName(
                                    catchLocalNameCounts,
                                    sanitizeJavaIdentifier(tryStatement.catchBinding())
                                            + (containsStrictNativeFunctionExpression(tryStatement.catchBlock()) ? "Cell" : "")
                            );
                            catchVariableNames.put(tryStatement.catchBinding(), javaName);
                            catchVariableTypes.put(tryStatement.catchBinding(), "Object");
                            if (containsStrictNativeFunctionExpression(tryStatement.catchBlock())) {
                                catchBoxedBindings.add(tryStatement.catchBinding());
                                builder.append(indent)
                                        .append("    final dev.tsj.runtime.TsjCell ")
                                        .append(javaName)
                                        .append(" = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjCaughtError));\n");
                            } else {
                                builder.append(indent)
                                        .append("    final Object ")
                                        .append(javaName)
                                        .append(" = dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjCaughtError);\n");
                            }
                        }
                        appendStrictNativeStatements(
                                builder,
                                model,
                                fieldTypesByName,
                                tryStatement.catchBlock(),
                                catchVariableNames,
                                catchVariableTypes,
                                catchBoxedBindings,
                                catchLocalNameCounts,
                                thisReference,
                                dynamicThisScope,
                                new LinkedHashMap<>(labelNames),
                                new LinkedHashSet<>(loopLabels),
                                returnType,
                                indent + "    "
                        );
                        builder.append(indent).append("}");
                    }
                    if (tryStatement.hasFinally()) {
                        builder.append(" finally {\n");
                        appendStrictNativeStatements(
                                builder,
                                model,
                                fieldTypesByName,
                                tryStatement.finallyBlock(),
                                new LinkedHashMap<>(variableNames),
                                new LinkedHashMap<>(variableTypes),
                                new LinkedHashSet<>(boxedBindings),
                                new LinkedHashMap<>(localNameCounts),
                                thisReference,
                                dynamicThisScope,
                                new LinkedHashMap<>(labelNames),
                                new LinkedHashSet<>(loopLabels),
                                returnType,
                                indent + "    "
                        );
                        builder.append(indent).append("}");
                    }
                    builder.append("\n");
                    continue;
                }
                if (statement instanceof BreakStatement breakStatement) {
                    builder.append(indent).append("break");
                    if (breakStatement.label() != null) {
                        final String javaLabel = labelNames.get(breakStatement.label());
                        if (javaLabel == null) {
                            throw strictNativeLoweringFailure(
                                    "Unresolved labeled `break` target `" + breakStatement.label() + "`."
                            );
                        }
                        builder.append(" ").append(javaLabel);
                    }
                    builder.append(";\n");
                    continue;
                }
                if (statement instanceof ContinueStatement continueStatement) {
                    builder.append(indent).append("continue");
                    if (continueStatement.label() != null) {
                        if (!loopLabels.contains(continueStatement.label())) {
                            throw strictNativeLoweringFailure(
                                    "Labeled `continue` target `" + continueStatement.label()
                                            + "` must resolve to an enclosing loop."
                            );
                        }
                        final String javaLabel = labelNames.get(continueStatement.label());
                        if (javaLabel == null) {
                            throw strictNativeLoweringFailure(
                                    "Unresolved labeled `continue` target `" + continueStatement.label() + "`."
                            );
                        }
                        builder.append(" ").append(javaLabel);
                    }
                    builder.append(";\n");
                    continue;
                }
                throw strictNativeLoweringFailure(
                        "Unsupported class statement in strict-native lowering: "
                                + statement.getClass().getSimpleName()
                );
            }
        }

        private void appendStrictNativeLabeledStatement(
                final StringBuilder builder,
                final StrictNativeClassModel model,
                final Map<String, String> fieldTypesByName,
                final LabeledStatement labeledStatement,
                final Map<String, String> variableNames,
                final Map<String, String> variableTypes,
                final Set<String> boxedBindings,
                final Map<String, Integer> localNameCounts,
                final String thisReference,
                final boolean dynamicThisScope,
                final Map<String, String> labelNames,
                final Set<String> loopLabels,
                final String returnType,
                final String indent
        ) {
            if (labelNames.containsKey(labeledStatement.label())) {
                throw strictNativeLoweringFailure(
                        "Duplicate label `" + labeledStatement.label() + "` in strict-native lowering."
                );
            }
            final String javaLabel = allocateUniqueName(
                    localNameCounts,
                    "__tsj_label_" + sanitizeJavaIdentifier(labeledStatement.label())
            );
            final Map<String, String> nestedLabelNames = new LinkedHashMap<>(labelNames);
            nestedLabelNames.put(labeledStatement.label(), javaLabel);
            if (labeledStatement.statement() instanceof WhileStatement whileStatement) {
                final Set<String> nestedLoopLabels = new LinkedHashSet<>(loopLabels);
                nestedLoopLabels.add(labeledStatement.label());
                builder.append(indent)
                        .append(javaLabel)
                        .append(": while (dev.tsj.runtime.TsjRuntime.truthy(")
                        .append(emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                whileStatement.condition()
                        ))
                        .append(")) {\n");
                appendStrictNativeStatements(
                        builder,
                        model,
                        fieldTypesByName,
                        whileStatement.body(),
                        new LinkedHashMap<>(variableNames),
                        new LinkedHashMap<>(variableTypes),
                        new LinkedHashSet<>(boxedBindings),
                        new LinkedHashMap<>(localNameCounts),
                        thisReference,
                        dynamicThisScope,
                        nestedLabelNames,
                        nestedLoopLabels,
                        returnType,
                        indent + "    "
                );
                builder.append(indent).append("}\n");
                return;
            }
            builder.append(indent)
                    .append(javaLabel)
                    .append(": {\n");
            appendStrictNativeStatements(
                    builder,
                    model,
                    fieldTypesByName,
                    List.of(labeledStatement.statement()),
                    new LinkedHashMap<>(variableNames),
                    new LinkedHashMap<>(variableTypes),
                    new LinkedHashSet<>(boxedBindings),
                    new LinkedHashMap<>(localNameCounts),
                    thisReference,
                    dynamicThisScope,
                    nestedLabelNames,
                    new LinkedHashSet<>(loopLabels),
                    returnType,
                    indent + "    "
            );
            builder.append(indent).append("}\n");
        }

        private String emitStrictNativeAssignmentTarget(
                final StrictNativeClassModel model,
                final Map<String, String> variableNames,
                final Expression target
        ) {
            if (target instanceof VariableExpression variableExpression) {
                final String javaName = variableNames.get(variableExpression.name());
                if (javaName == null) {
                    throw strictNativeLoweringFailure(
                            "Unknown variable assignment target `" + variableExpression.name() + "`."
                    );
                }
                return javaName;
            }
            if (target instanceof MemberAccessExpression memberAccessExpression
                    && memberAccessExpression.receiver() instanceof ThisExpression) {
                final String javaField = model.fieldNameMap().get(memberAccessExpression.member());
                if (javaField == null) {
                    throw strictNativeLoweringFailure(
                            "Unknown class field assignment target `" + memberAccessExpression.member() + "`."
                    );
                }
                return "this." + javaField;
            }
            if (target instanceof MemberAccessExpression memberAccessExpression
                    && memberAccessExpression.receiver() instanceof VariableExpression variableExpression) {
                final StrictNativeClassModel targetModel = findStrictNativeClassModel(variableExpression.name());
                if (targetModel != null) {
                    final String javaField = targetModel.staticFieldNameMap().get(memberAccessExpression.member());
                    if (javaField != null) {
                        return targetModel.nativeClassSimpleName() + "." + javaField;
                    }
                }
            }
            throw strictNativeLoweringFailure("Unsupported assignment target in strict-native lowering.");
        }

        private String resolveStrictNativeAssignmentType(
                final StrictNativeClassModel model,
                final Map<String, String> fieldTypesByName,
                final Map<String, String> variableTypes,
                final Expression target
        ) {
            return resolveStrictNativeAssignmentType(model, fieldTypesByName, variableTypes, false, target);
        }

        private String resolveStrictNativeAssignmentType(
                final StrictNativeClassModel model,
                final Map<String, String> fieldTypesByName,
                final Map<String, String> variableTypes,
                final boolean dynamicThisScope,
                final Expression target
        ) {
            if (target instanceof VariableExpression variableExpression) {
                return variableTypes.getOrDefault(variableExpression.name(), "Object");
            }
            if (target instanceof MemberAccessExpression memberAccessExpression
                    && memberAccessExpression.receiver() instanceof ThisExpression) {
                return dynamicThisScope
                        ? "Object"
                        : fieldTypesByName.getOrDefault(memberAccessExpression.member(), "Object");
            }
            if (target instanceof MemberAccessExpression memberAccessExpression
                    && memberAccessExpression.receiver() instanceof VariableExpression variableExpression) {
                final StrictNativeClassModel targetModel = findStrictNativeClassModel(variableExpression.name());
                if (targetModel != null && targetModel.staticFieldNameMap().containsKey(memberAccessExpression.member())) {
                    return "Object";
                }
            }
            return "Object";
        }

        private String emitStrictNativeAssignmentExpression(
                final StrictNativeClassModel model,
                final Map<String, String> variableNames,
                final Set<String> boxedBindings,
                final String thisReference,
                final boolean dynamicThisScope,
                final AssignmentExpression assignmentExpression
        ) {
            if (!JvmBytecodeCompiler.isStrictNativeSupportedAssignmentOperator(assignmentExpression.operator())) {
                throw strictNativeLoweringFailure(
                        "Unsupported assignment operator in strict-native lowering: " + assignmentExpression.operator()
                );
            }
            final String operator = assignmentExpression.operator();
            final String valueExpression = emitStrictNativeExpression(
                    model,
                    variableNames,
                    boxedBindings,
                    thisReference,
                    dynamicThisScope,
                    assignmentExpression.expression()
            );
            final Expression target = assignmentExpression.target();
            if (target instanceof VariableExpression variableExpression) {
                final String javaName = variableNames.get(variableExpression.name());
                if (javaName != null) {
                    if (boxedBindings.contains(variableExpression.name())) {
                        return emitVariableAssignmentExpression(javaName, operator, valueExpression);
                    }
                    return emitStrictNativeDirectAssignmentExpression(javaName, javaName, operator, valueExpression);
                }
                if (topLevelBindingNames.contains(variableExpression.name())) {
                    final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                            model,
                            variableExpression.name()
                    );
                    final String cellExpression = classSimpleName
                            + ".__tsjResolveTopLevelBinding(\""
                            + escapeJava(bindingTarget)
                            + "\")";
                    return emitVariableAssignmentExpression(cellExpression, operator, valueExpression);
                }
                if (model.moduleBindingTargets().containsKey(variableExpression.name())) {
                    final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                            model,
                            variableExpression.name()
                    );
                    final String cellExpression = classSimpleName
                            + ".__tsjResolveTopLevelBinding(\""
                            + escapeJava(bindingTarget)
                            + "\")";
                    return emitVariableAssignmentExpression(cellExpression, operator, valueExpression);
                }
                throw strictNativeLoweringFailure(
                        "Unknown variable assignment target `" + variableExpression.name() + "`."
                );
            }
            if (target instanceof MemberAccessExpression memberAccessExpression) {
                if (memberAccessExpression.receiver() instanceof ThisExpression) {
                    if (dynamicThisScope) {
                        return emitMemberAssignmentExpression(
                                thisReference,
                                escapeJava(memberAccessExpression.member()),
                                operator,
                                valueExpression
                        );
                    }
                    final String javaField = model.fieldNameMap().get(memberAccessExpression.member());
                    if (javaField == null) {
                        throw strictNativeLoweringFailure(
                                "Unknown class field assignment target `" + memberAccessExpression.member() + "`."
                        );
                    }
                    return emitStrictNativeDirectAssignmentExpression(
                            "this." + javaField,
                            "this." + javaField,
                            operator,
                            valueExpression
                    );
                }
                if (memberAccessExpression.receiver() instanceof VariableExpression variableExpression) {
                    final StrictNativeClassModel targetModel = findStrictNativeClassModel(variableExpression.name());
                    if (targetModel != null) {
                        final String javaField = targetModel.staticFieldNameMap().get(memberAccessExpression.member());
                        if (javaField != null) {
                            final String left = targetModel.nativeClassSimpleName() + "." + javaField;
                            return emitStrictNativeDirectAssignmentExpression(left, left, operator, valueExpression);
                        }
                    }
                }
                final String receiver = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        memberAccessExpression.receiver()
                );
                return emitMemberAssignmentExpression(
                        receiver,
                        escapeJava(memberAccessExpression.member()),
                        operator,
                        valueExpression
                );
            }
            if (target instanceof CallExpression callExpression
                    && JvmBytecodeCompiler.isStrictNativeIndexAssignmentTarget(callExpression)) {
                final String receiver = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        callExpression.arguments().get(0)
                );
                final String key = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        callExpression.arguments().get(1)
                );
                return emitDynamicIndexAssignmentExpression(receiver, key, operator, valueExpression);
            }
            throw strictNativeLoweringFailure(
                    "Unsupported assignment expression target in strict-native lowering: "
                            + target.getClass().getSimpleName()
            );
        }

        private String emitStrictNativeDirectAssignmentExpression(
                final String leftExpression,
                final String currentValueExpression,
                final String operator,
                final String valueExpression
        ) {
            final String compoundOperator = compoundBinaryOperator(operator);
            if (compoundOperator != null) {
                return "("
                        + leftExpression
                        + " = "
                        + emitBinaryOperatorExpression(compoundOperator, currentValueExpression, valueExpression)
                        + ")";
            }
            return switch (operator) {
                case "=" -> "(" + leftExpression + " = " + valueExpression + ")";
                case "&&=" -> "(dev.tsj.runtime.TsjRuntime.truthy(" + currentValueExpression + ") ? ("
                        + leftExpression + " = " + valueExpression + ") : " + currentValueExpression + ")";
                case "||=" -> "(dev.tsj.runtime.TsjRuntime.truthy(" + currentValueExpression + ") ? "
                        + currentValueExpression + " : (" + leftExpression + " = " + valueExpression + "))";
                case "??=" -> "(dev.tsj.runtime.TsjRuntime.isNullishValue(" + currentValueExpression + ") ? ("
                        + leftExpression + " = " + valueExpression + ") : " + currentValueExpression + ")";
                default -> throw strictNativeLoweringFailure(
                        "Unsupported direct assignment operator in strict-native lowering: " + operator
                );
            };
        }

        private String emitStrictNativeFunctionValue(
                final StrictNativeClassModel model,
                final Map<String, String> variableNames,
                final Set<String> boxedBindings,
                final String thisReference,
                final boolean dynamicThisScope,
                final FunctionExpression functionExpression
        ) {
            if (functionExpression.async()) {
                throw strictNativeLoweringFailure(
                        "Async function expressions are not supported in strict-native lowering."
                );
            }
            if (functionExpression.generator()) {
                throw strictNativeLoweringFailure(
                        "Generator function expressions are not supported in strict-native lowering."
                );
            }
            final String argsVar = nextStrictNativeLambdaName("__tsjLambdaArgs");
            final String lambdaThisReference;
            final boolean lambdaDynamicThisScope;
            final boolean lambdaBoxesBindings = containsStrictNativeFunctionExpression(functionExpression.body());
            final StringBuilder functionBuilder = new StringBuilder();
            if (functionExpression.thisMode() == FunctionThisMode.DYNAMIC) {
                final String thisVar = nextStrictNativeLambdaName("__tsjLambdaThis");
                lambdaThisReference = thisVar;
                lambdaDynamicThisScope = true;
                functionBuilder.append("((dev.tsj.runtime.TsjCallableWithThis) (Object ")
                        .append(thisVar)
                        .append(", Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
            } else {
                lambdaThisReference = thisReference;
                lambdaDynamicThisScope = dynamicThisScope;
                functionBuilder.append("((dev.tsj.runtime.TsjCallable) (Object... ")
                        .append(argsVar)
                        .append(") -> {\n");
            }
            final Map<String, String> lambdaVariableNames = new LinkedHashMap<>(variableNames);
            final Map<String, String> lambdaVariableTypes = new LinkedHashMap<>();
            final Set<String> lambdaBoxedBindings = new LinkedHashSet<>(boxedBindings);
            final Map<String, Integer> lambdaLocalNameCounts = new LinkedHashMap<>();
            for (int index = 0; index < functionExpression.parameters().size(); index++) {
                final String parameter = functionExpression.parameters().get(index);
                final String javaName = allocateUniqueName(
                        lambdaLocalNameCounts,
                        sanitizeJavaIdentifier(parameter)
                );
                final String argumentExpression = argsVar
                        + ".length > "
                        + index
                        + " ? "
                        + argsVar
                        + "["
                        + index
                        + "] : dev.tsj.runtime.TsjRuntime.undefined()";
                lambdaVariableTypes.put(parameter, "Object");
                if (lambdaBoxesBindings) {
                    final String cellName = allocateUniqueName(lambdaLocalNameCounts, javaName + "Cell");
                    lambdaVariableNames.put(parameter, cellName);
                    lambdaBoxedBindings.add(parameter);
                    functionBuilder.append("    final dev.tsj.runtime.TsjCell ")
                            .append(cellName)
                            .append(" = new dev.tsj.runtime.TsjCell(")
                            .append(argumentExpression)
                            .append(");\n");
                } else {
                    lambdaVariableNames.put(parameter, javaName);
                    functionBuilder.append("    final Object ")
                            .append(javaName)
                            .append(" = ")
                            .append(argumentExpression)
                            .append(";\n");
                }
            }
            appendStrictNativeStatements(
                    functionBuilder,
                    model,
                    Map.of(),
                    functionExpression.body(),
                    lambdaVariableNames,
                    lambdaVariableTypes,
                    lambdaBoxedBindings,
                    lambdaLocalNameCounts,
                    lambdaThisReference,
                    lambdaDynamicThisScope,
                    "Object",
                    "    "
            );
            if (!blockAlwaysExits(functionExpression.body())) {
                functionBuilder.append("    return null;\n");
            }
            functionBuilder.append("})");
            return functionBuilder.toString();
        }

        private String emitStrictNativeExpression(
                final StrictNativeClassModel model,
                final Map<String, String> variableNames,
                final Set<String> boxedBindings,
                final Expression expression
        ) {
            return emitStrictNativeExpression(model, variableNames, boxedBindings, "this", false, expression);
        }

        private String emitStrictNativeExpression(
                final StrictNativeClassModel model,
                final Map<String, String> variableNames,
                final Set<String> boxedBindings,
                final String thisReference,
                final boolean dynamicThisScope,
                final Expression expression
        ) {
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
            if (expression instanceof ThisExpression) {
                return thisReference;
            }
            if (expression instanceof VariableExpression variableExpression) {
                final String javaName = variableNames.get(variableExpression.name());
                if (javaName != null) {
                    return boxedBindings.contains(variableExpression.name()) ? javaName + ".get()" : javaName;
                }
                final StrictNativeClassModel strictNativeClassModel = findStrictNativeClassModel(variableExpression.name());
                if (strictNativeClassModel != null) {
                    return strictNativeClassModel.nativeClassSimpleName() + ".class";
                }
                if (topLevelBindingNames.contains(variableExpression.name())) {
                    final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                            model,
                            variableExpression.name()
                    );
                    return classSimpleName
                            + ".__tsjResolveTopLevelBinding(\""
                            + escapeJava(bindingTarget)
                            + "\").get()";
                }
                if (model.moduleBindingTargets().containsKey(variableExpression.name())) {
                    final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                            model,
                            variableExpression.name()
                    );
                    return classSimpleName
                            + ".__tsjResolveTopLevelBinding(\""
                            + escapeJava(bindingTarget)
                            + "\").get()";
                }
                throw strictNativeLoweringFailure(
                        "Unknown variable `" + variableExpression.name() + "` in strict-native lowering."
                );
            }
            if (expression instanceof MemberAccessExpression memberAccessExpression) {
                if (memberAccessExpression.receiver() instanceof ThisExpression) {
                    if (dynamicThisScope) {
                        return "dev.tsj.runtime.TsjRuntime.getProperty("
                                + thisReference
                                + ", \""
                                + escapeJava(memberAccessExpression.member())
                                + "\")";
                    }
                    final String javaField = model.fieldNameMap().get(memberAccessExpression.member());
                    if (javaField == null) {
                        throw strictNativeLoweringFailure(
                                "Unknown class field `" + memberAccessExpression.member() + "` in strict-native lowering."
                        );
                    }
                    return "this." + javaField;
                }
                if (memberAccessExpression.receiver() instanceof VariableExpression variableExpression
                        && variableNames.containsKey(variableExpression.name())) {
                    final String receiver = emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            variableExpression
                    );
                    return "dev.tsj.runtime.TsjRuntime.getProperty("
                            + receiver
                            + ", \""
                            + escapeJava(memberAccessExpression.member())
                            + "\")";
                }
                if (memberAccessExpression.receiver() instanceof VariableExpression variableExpression) {
                    final StrictNativeClassModel targetModel = findStrictNativeClassModel(variableExpression.name());
                    if (targetModel != null) {
                        final String javaField = targetModel.staticFieldNameMap().get(memberAccessExpression.member());
                        if (javaField != null) {
                            return targetModel.nativeClassSimpleName() + "." + javaField;
                        }
                    }
                }
                final String receiver = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        memberAccessExpression.receiver()
                );
                return "dev.tsj.runtime.TsjRuntime.getProperty("
                        + receiver
                        + ", \""
                        + escapeJava(memberAccessExpression.member())
                        + "\")";
            }
            if (expression instanceof UnaryExpression unaryExpression) {
                final String operand = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        unaryExpression.expression()
                );

                return switch (unaryExpression.operator()) {
                    case "+" -> "dev.tsj.runtime.TsjRuntime.unaryPlus(" + operand + ")";
                    case "-" -> "dev.tsj.runtime.TsjRuntime.negate(" + operand + ")";
                    case "!" -> "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.truthy(" + operand + "))";
                    case "~" -> "dev.tsj.runtime.TsjRuntime.bitwiseNot(" + operand + ")";
                    default -> throw strictNativeLoweringFailure(
                            "Unsupported unary operator in strict-native lowering: " + unaryExpression.operator()
                    );
                };
            }
            if (expression instanceof BinaryExpression binaryExpression) {
                final String left = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        binaryExpression.left()
                );
                final String right = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        binaryExpression.right()
                );
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
                    case "==" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.abstractEquals("
                            + left + ", " + right + "))";
                    case "===" -> "Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals("
                            + left + ", " + right + "))";
                    case "!=" -> "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.abstractEquals("
                            + left + ", " + right + "))";
                    case "!==" -> "Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.strictEquals("
                            + left + ", " + right + "))";
                    case "&&" -> "dev.tsj.runtime.TsjRuntime.logicalAnd(" + left + ", () -> " + right + ")";
                    case "||" -> "dev.tsj.runtime.TsjRuntime.logicalOr(" + left + ", () -> " + right + ")";
                    case "??" -> "dev.tsj.runtime.TsjRuntime.nullishCoalesce(" + left + ", () -> " + right + ")";
                    default -> throw strictNativeLoweringFailure(
                            "Unsupported binary operator in strict-native lowering: " + binaryExpression.operator()
                    );
                };
            }
            if (expression instanceof AssignmentExpression assignmentExpression) {
                return emitStrictNativeAssignmentExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        assignmentExpression
                );
            }
            if (expression instanceof ConditionalExpression conditionalExpression) {
                final String condition = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        conditionalExpression.condition()
                );
                final String whenTrue = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        conditionalExpression.whenTrue()
                );
                final String whenFalse = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        conditionalExpression.whenFalse()
                );
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
                        + emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        optionalMemberAccessExpression.receiver()
                )
                        + ", \""
                        + escapeJava(optionalMemberAccessExpression.member())
                        + "\")";
            }
            if (expression instanceof ArrayLiteralExpression arrayLiteralExpression) {
                final List<String> renderedElements = new ArrayList<>();
                for (Expression element : arrayLiteralExpression.elements()) {
                    renderedElements.add(emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            element
                    ));
                }
                if (renderedElements.isEmpty()) {
                    return "dev.tsj.runtime.TsjRuntime.arrayLiteral()";
                }
                return "dev.tsj.runtime.TsjRuntime.arrayLiteral(" + String.join(", ", renderedElements) + ")";
            }
            if (expression instanceof ObjectLiteralExpression objectLiteralExpression) {
                final List<String> keyValueSegments = new ArrayList<>();
                for (ObjectLiteralEntry entry : objectLiteralExpression.entries()) {
                    keyValueSegments.add("\"" + escapeJava(entry.key()) + "\"");
                    keyValueSegments.add(emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            entry.value()
                    ));
                }
                if (keyValueSegments.isEmpty()) {
                    return "dev.tsj.runtime.TsjRuntime.objectLiteral()";
                }
                return "dev.tsj.runtime.TsjRuntime.objectLiteral(" + String.join(", ", keyValueSegments) + ")";
            }
            if (expression instanceof FunctionExpression functionExpression) {
                return emitStrictNativeFunctionValue(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        functionExpression
                );
            }
            if (expression instanceof OptionalCallExpression optionalCallExpression) {
                final List<String> renderedArgs = new ArrayList<>();
                for (Expression argument : optionalCallExpression.arguments()) {
                    renderedArgs.add(emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            argument
                    ));
                }
                final String argsSupplier = renderedArgs.isEmpty()
                        ? "() -> new Object[0]"
                        : "() -> new Object[]{" + String.join(", ", renderedArgs) + "}";
                if (optionalCallExpression.callee() instanceof OptionalMemberAccessExpression optionalMemberAccessExpression) {
                    return "dev.tsj.runtime.TsjRuntime.optionalInvokeMember("
                            + emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            optionalMemberAccessExpression.receiver()
                    )
                            + ", \""
                            + escapeJava(optionalMemberAccessExpression.member())
                            + "\", "
                            + argsSupplier
                            + ")";
                }
                return "dev.tsj.runtime.TsjRuntime.optionalCall("
                        + emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        optionalCallExpression.callee()
                )
                        + ", "
                        + argsSupplier
                        + ")";
            }
            if (expression instanceof NewExpression newExpression) {
                final List<String> renderedArgs = new ArrayList<>();
                for (Expression argument : newExpression.arguments()) {
                    renderedArgs.add(emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            argument
                    ));
                }
                if (newExpression.constructor() instanceof VariableExpression variableExpression) {
                    final StrictNativeClassModel targetModel = findStrictNativeClassModel(variableExpression.name());
                    if (targetModel != null) {
                        if (renderedArgs.isEmpty()) {
                            return "new " + targetModel.nativeClassSimpleName() + "()";
                        }
                        return "new "
                                + targetModel.nativeClassSimpleName()
                                + "("
                                + String.join(", ", renderedArgs)
                                + ")";
                    }
                }
                final String constructor = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        newExpression.constructor()
                );
                if (renderedArgs.isEmpty()) {
                    return "dev.tsj.runtime.TsjRuntime.construct(" + constructor + ")";
                }
                return "dev.tsj.runtime.TsjRuntime.construct("
                        + constructor
                        + ", "
                        + String.join(", ", renderedArgs)
                        + ")";
            }
            if (expression instanceof CallExpression callExpression) {
                if (callExpression.callee() instanceof VariableExpression variableExpression
                        && "__tsj_super_invoke".equals(variableExpression.name())) {
                    if (model.declaration().superClassName() == null) {
                        throw strictNativeLoweringFailure(
                                "`super.member(...)` is only valid for derived strict-native classes."
                        );
                    }
                    if (callExpression.arguments().isEmpty()
                            || !(callExpression.arguments().getFirst() instanceof StringLiteral methodNameLiteral)) {
                        throw strictNativeLoweringFailure(
                                "Super-member helper requires a leading string-literal method token."
                        );
                    }
                    final String javaMethodName = resolveStrictNativeSuperMethodName(
                            model.declaration().superClassName(),
                            methodNameLiteral.value()
                    );
                    final List<String> renderedArgs = new ArrayList<>();
                    for (int index = 1; index < callExpression.arguments().size(); index++) {
                        renderedArgs.add(emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                callExpression.arguments().get(index)
                        ));
                    }
                    if (renderedArgs.isEmpty()) {
                        return "super." + javaMethodName + "()";
                    }
                    return "super." + javaMethodName + "(" + String.join(", ", renderedArgs) + ")";
                }
                if (callExpression.callee() instanceof VariableExpression variableExpression) {
                    if ("__tsj_for_of_values".equals(variableExpression.name())
                            || "__tsj_for_in_keys".equals(variableExpression.name())) {
                        if (callExpression.arguments().size() != 1) {
                            throw strictNativeLoweringFailure(
                                    "Collection helper `" + variableExpression.name()
                                            + "` requires exactly one argument."
                            );
                        }
                        final String argument = emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                callExpression.arguments().getFirst()
                        );
                        final String runtimeMethod = "__tsj_for_of_values".equals(variableExpression.name())
                                ? "forOfValues"
                                : "forInKeys";
                        return "dev.tsj.runtime.TsjRuntime." + runtimeMethod + "(" + argument + ")";
                    }
                    if ("__tsj_index_read".equals(variableExpression.name())
                            || "__tsj_optional_index_read".equals(variableExpression.name())) {
                        if (callExpression.arguments().size() != 2) {
                            throw strictNativeLoweringFailure(
                                    "Index helper `" + variableExpression.name()
                                            + "` requires exactly two arguments."
                            );
                        }
                        final String receiver = emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                callExpression.arguments().get(0)
                        );
                        final String index = emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                callExpression.arguments().get(1)
                        );
                        final String runtimeMethod = "__tsj_optional_index_read".equals(variableExpression.name())
                                ? "optionalIndexRead"
                                : "indexRead";
                        return "dev.tsj.runtime.TsjRuntime."
                                + runtimeMethod
                                + "("
                                + receiver
                                + ", "
                                + index
                                + ")";
                    }
                    final String javaName = variableNames.get(variableExpression.name());
                    final String callableValue;
                    if (javaName != null) {
                        callableValue = boxedBindings.contains(variableExpression.name())
                                ? javaName + ".get()"
                                : javaName;
                    } else if (topLevelBindingNames.contains(variableExpression.name())) {
                        final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                                model,
                                variableExpression.name()
                        );
                        callableValue = classSimpleName
                                + ".__tsjResolveTopLevelBinding(\""
                                + escapeJava(bindingTarget)
                                + "\").get()";
                    } else if (model.moduleBindingTargets().containsKey(variableExpression.name())) {
                        final String bindingTarget = resolveStrictNativeTopLevelBindingTarget(
                                model,
                                variableExpression.name()
                        );
                        callableValue = classSimpleName
                                + ".__tsjResolveTopLevelBinding(\""
                                + escapeJava(bindingTarget)
                                + "\").get()";
                    } else {
                        throw strictNativeLoweringFailure(
                                "Unknown callable variable `" + variableExpression.name() + "` in strict-native lowering."
                        );
                    }
                    final List<String> renderedArgs = new ArrayList<>();
                    for (Expression argument : callExpression.arguments()) {
                        renderedArgs.add(emitStrictNativeExpression(
                                model,
                                variableNames,
                                boxedBindings,
                                thisReference,
                                dynamicThisScope,
                                argument
                        ));
                    }
                    if (renderedArgs.isEmpty()) {
                        return "dev.tsj.runtime.TsjRuntime.call(" + callableValue + ")";
                    }
                    return "dev.tsj.runtime.TsjRuntime.call("
                            + callableValue
                            + ", "
                            + String.join(", ", renderedArgs)
                            + ")";
                }
                if (!(callExpression.callee() instanceof MemberAccessExpression memberAccessExpression)) {
                    throw strictNativeLoweringFailure(
                            "Only member call expressions are supported in strict-native lowering."
                    );
                }
                final List<String> renderedArgs = new ArrayList<>();
                for (Expression argument : callExpression.arguments()) {
                    renderedArgs.add(emitStrictNativeExpression(
                            model,
                            variableNames,
                            boxedBindings,
                            thisReference,
                            dynamicThisScope,
                            argument
                    ));
                }
                final boolean directThisMethodCall = memberAccessExpression.receiver() instanceof ThisExpression
                        && !dynamicThisScope
                        && model.methodNameMap().containsKey(memberAccessExpression.member());
                final StrictNativeClassModel directStaticTarget = memberAccessExpression.receiver() instanceof VariableExpression variableExpression
                        ? findStrictNativeClassModel(variableExpression.name())
                        : null;
                final boolean directStaticMethodCall = directStaticTarget != null
                        && directStaticTarget.staticMethodNameMap().containsKey(memberAccessExpression.member());
                if (directThisMethodCall) {
                    final String javaMethod = model.methodNameMap().get(memberAccessExpression.member());
                    if (renderedArgs.isEmpty()) {
                        return "this." + javaMethod + "()";
                    }
                    return "this." + javaMethod + "(" + String.join(", ", renderedArgs) + ")";
                }
                if (directStaticMethodCall) {
                    final String javaMethod = directStaticTarget.staticMethodNameMap().get(memberAccessExpression.member());
                    if (renderedArgs.isEmpty()) {
                        return directStaticTarget.nativeClassSimpleName() + "." + javaMethod + "()";
                    }
                    return directStaticTarget.nativeClassSimpleName() + "."
                            + javaMethod
                            + "("
                            + String.join(", ", renderedArgs)
                            + ")";
                }
                final String receiver = emitStrictNativeExpression(
                        model,
                        variableNames,
                        boxedBindings,
                        thisReference,
                        dynamicThisScope,
                        memberAccessExpression.receiver()
                );
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
            throw strictNativeLoweringFailure(
                    "Unsupported class expression in strict-native lowering: "
                            + expression.getClass().getSimpleName()
            );
        }

        private StrictNativeClassModel findStrictNativeClassModel(final String tsClassName) {
            for (StrictNativeClassModel model : strictNativeClassModels) {
                if (model.tsClassName().equals(tsClassName)) {
                    return model;
                }
            }
            return null;
        }

        private String resolveStrictNativeSuperMethodName(
                final String superTsClassName,
                final String tsMethodName
        ) {
            StrictNativeClassModel currentModel = findStrictNativeClassModel(superTsClassName);
            while (currentModel != null) {
                final String javaMethodName = currentModel.methodNameMap().get(tsMethodName);
                if (javaMethodName != null) {
                    return javaMethodName;
                }
                final String nextSuperClassName = currentModel.declaration().superClassName();
                currentModel = nextSuperClassName == null ? null : findStrictNativeClassModel(nextSuperClassName);
            }
            return sanitizeJavaIdentifier(tsMethodName);
        }

        private JvmCompilationException strictNativeLoweringFailure(final String detail) {
            return new JvmCompilationException(
                    "TSJ-STRICT-BRIDGE",
                    "Strict-native lowering mismatch: " + detail
                            + " [featureId="
                            + FEATURE_STRICT_BRIDGE
                            + "]. Guidance: "
                            + GUIDANCE_STRICT_BRIDGE,
                    null,
                    null,
                    null,
                    FEATURE_STRICT_BRIDGE,
                    GUIDANCE_STRICT_BRIDGE
            );
        }

        private record StrictNativeClassModel(
                String tsClassName,
                String nativeClassSimpleName,
                SourceLocation sourceLocation,
                ClassDeclaration declaration,
                Map<String, String> moduleBindingTargets,
                Map<String, String> fieldNameMap,
                Map<String, String> methodNameMap,
                Map<String, String> staticFieldNameMap,
                Map<String, String> staticMethodNameMap
        ) {
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
                    emitTopLevelBindingRegistration(builder, context, declarationStatement.declaration().name(), indent);
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
                    emitTopLevelBindingRegistration(builder, context, declaration.name(), indent);
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

        private void emitTopLevelBindingRegistration(
                final StringBuilder builder,
                final EmissionContext context,
                final String bindingName,
                final String indent
        ) {
            if (!(context.isTopLevelScope() || context.isModuleInitializerScope())) {
                return;
            }
            final String lookupKey = context.isModuleInitializerScope()
                    ? moduleBindingLookupKey(context.resolveModuleInitializerName(), bindingName)
                    : bindingName;
            builder.append(indent)
                    .append("__TSJ_TOP_LEVEL_BINDINGS.put(\"")
                    .append(escapeJava(lookupKey))
                    .append("\", ")
                    .append(context.resolveBinding(bindingName))
                    .append(");\n");
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
            final boolean moduleInitializerFunction = declaration.name().startsWith("__tsj_init_module_");

            builder.append(indent)
                    .append(cellName)
                    .append(".set((dev.tsj.runtime.TsjCallableWithThis) (Object ")
                    .append(thisVar)
                    .append(", Object... ")
                    .append(argsVar)
                    .append(") -> {\n");

            final EmissionContext functionContext =
                    new EmissionContext(
                            context,
                            thisVar,
                            context.resolveSuperClassExpression(),
                            false,
                            argsVar,
                            moduleInitializerFunction,
                            moduleInitializerFunction ? declaration.name() : null
                    );
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
            emitTopLevelBindingRegistration(builder, context, declaration.name(), indent);
            if (context.isTopLevelScope() || context.isModuleInitializerScope()) {
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
            return JvmBytecodeCompiler.assignmentCompoundBinaryOperator(assignmentOperator);
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
                if (isObjectRestFactoryCall(callExpression)) {
                    return emitObjectRestRuntimeCall(context, callExpression);
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

        private String emitObjectRestRuntimeCall(
                final EmissionContext context,
                final CallExpression callExpression
        ) {
            if (callExpression.arguments().isEmpty()) {
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "__tsj_object_rest requires a source object expression."
                );
            }
            return emitSpreadRuntimeCall(context, "objectRest", callExpression.arguments());
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

        private boolean isObjectRestFactoryCall(final CallExpression callExpression) {
            if (!(callExpression.callee() instanceof VariableExpression variableExpression)) {
                return false;
            }
            return "__tsj_object_rest".equals(variableExpression.name());
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
            private final boolean moduleInitializerScope;
            private final String moduleInitializerName;

            private EmissionContext(final EmissionContext parent) {
                this(
                        parent,
                        parent != null ? parent.thisReference : null,
                        parent != null ? parent.superClassExpression : null,
                        parent != null && parent.constructorContext,
                        parent != null ? parent.argumentsReference : null,
                        false,
                        null
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
                        parent != null ? parent.argumentsReference : null,
                        false,
                        null
                );
            }

            private EmissionContext(
                    final EmissionContext parent,
                    final String thisReference,
                    final String superClassExpression,
                    final boolean constructorContext,
                    final String argumentsReference
            ) {
                this(
                        parent,
                        thisReference,
                        superClassExpression,
                        constructorContext,
                        argumentsReference,
                        false,
                        null
                );
            }

            private EmissionContext(
                    final EmissionContext parent,
                    final String thisReference,
                    final String superClassExpression,
                    final boolean constructorContext,
                    final String argumentsReference,
                    final boolean moduleInitializerScope,
                    final String moduleInitializerName
            ) {
                this.parent = parent;
                this.bindings = new LinkedHashMap<>();
                this.labels = new LinkedHashMap<>();
                this.generatedNames = new LinkedHashSet<>();
                this.thisReference = thisReference;
                this.superClassExpression = superClassExpression;
                this.constructorContext = constructorContext;
                this.argumentsReference = argumentsReference;
                this.moduleInitializerScope = moduleInitializerScope;
                this.moduleInitializerName = moduleInitializerName;
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

            private boolean isModuleInitializerScope() {
                return moduleInitializerScope;
            }

            private String resolveModuleInitializerName() {
                if (moduleInitializerName != null) {
                    return moduleInitializerName;
                }
                if (parent != null) {
                    return parent.resolveModuleInitializerName();
                }
                throw new JvmCompilationException(
                        "TSJ-BACKEND-UNSUPPORTED",
                        "Missing module initializer name for strict-native module binding registration."
                );
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
