package dev.tsj.compiler.backend.jvm;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Frontend-backed decorator declaration extractor for the native JVM path.
 */
final class TsFrontendDecoratorModelExtractor {
    private static final String FEATURE_ID = "TSJ32A-DECORATOR-MODEL";
    private static final String GUIDANCE =
            "Import decorators from java:<fully.qualified.AnnotationType> so the frontend-backed JVM declaration model can resolve them.";
    private static final String RESOLUTION_FEATURE_ID = TsDecoratorClasspathResolver.FEATURE_ID;
    private static final String RESOLUTION_GUIDANCE = TsDecoratorClasspathResolver.GUIDANCE;

    private final TypeScriptSyntaxBridge syntaxBridge;
    private final Set<String> supportedBareDecorators;
    private final TsDecoratorClasspathResolver classpathResolver;
    private final boolean strictUnsupportedDecorators;

    TsFrontendDecoratorModelExtractor(
            final Set<String> supportedBareDecorators,
            final TsDecoratorClasspathResolver classpathResolver,
            final boolean strictUnsupportedDecorators
    ) {
        this.syntaxBridge = new TypeScriptSyntaxBridge();
        this.supportedBareDecorators = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(supportedBareDecorators, "supportedBareDecorators"))
        );
        this.classpathResolver = classpathResolver;
        this.strictUnsupportedDecorators = strictUnsupportedDecorators;
    }

    TsFrontendDecoratorModelExtractor(
            final TsDecoratorClasspathResolver classpathResolver,
            final boolean strictUnsupportedDecorators
    ) {
        this(Set.of(), classpathResolver, strictUnsupportedDecorators);
    }

    static TsFrontendDecoratorModelExtractor createClasspathAware(
            final Set<String> supportedBareDecorators,
            final List<Path> classpathEntries,
            final boolean strictUnsupportedDecorators,
            final String symbolTableId
    ) {
        final List<Path> normalizedEntries = classpathEntries == null
                ? List.of()
                : classpathEntries.stream()
                        .filter(Objects::nonNull)
                        .map(path -> path.toAbsolutePath().normalize())
                        .toList();
        final TsDecoratorClasspathResolver classpathResolver = new TsDecoratorClasspathResolver(
                new JavaSymbolTable(normalizedEntries, symbolTableId)
        );
        return new TsFrontendDecoratorModelExtractor(
                supportedBareDecorators,
                classpathResolver,
                strictUnsupportedDecorators
        );
    }

    static TsFrontendDecoratorModelExtractor createClasspathAware(
            final Set<String> supportedBareDecorators,
            final String classpath,
            final boolean strictUnsupportedDecorators,
            final String symbolTableId
    ) {
        return createClasspathAware(
                supportedBareDecorators,
                parseClasspathEntries(classpath),
                strictUnsupportedDecorators,
                symbolTableId
        );
    }

    TsDecoratorModel extract(final Path entryFile) {
        return extractWithImportedDecoratorBindings(entryFile).model();
    }

    ExtractionResult extractWithImportedDecoratorBindings(final Path entryFile) {
        final Path normalizedEntry = Objects.requireNonNull(entryFile, "entryFile").toAbsolutePath().normalize();
        if (!Files.exists(normalizedEntry) || !Files.isRegularFile(normalizedEntry)) {
            throw new JvmCompilationException(
                    "TSJ-DECORATOR-INPUT",
                    "Decorator extraction entry file not found: " + normalizedEntry,
                    null,
                    null,
                    normalizedEntry.toString(),
                    FEATURE_ID,
                    GUIDANCE
            );
        }
        final List<TsDecoratedClass> classes = new ArrayList<>();
        final Map<Path, Map<String, String>> importedDecoratorBindingsByFile = new LinkedHashMap<>();
        collectDecorators(normalizedEntry, new LinkedHashSet<>(), classes, importedDecoratorBindingsByFile);
        return new ExtractionResult(
                new TsDecoratorModel(List.copyOf(classes)),
                copyImportedDecoratorBindings(importedDecoratorBindingsByFile)
        );
    }

    private void collectDecorators(
            final Path sourceFile,
            final Set<Path> visited,
            final List<TsDecoratedClass> classes,
            final Map<Path, Map<String, String>> importedDecoratorBindingsByFile
    ) {
        final Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        if (!Files.exists(normalizedSource) || !Files.isRegularFile(normalizedSource)) {
            return;
        }
        if (!visited.add(normalizedSource)) {
            return;
        }

        final String sourceText;
        try {
            sourceText = Files.readString(normalizedSource, UTF_8);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-DECORATOR-INPUT",
                    "Failed to read source while extracting decorators: " + ioException.getMessage(),
                    null,
                    null,
                    normalizedSource.toString(),
                    FEATURE_ID,
                    GUIDANCE,
                    ioException
            );
        }

        final TypeScriptSyntaxBridge.BridgeResult bridgeResult = syntaxBridge.tokenize(sourceText, normalizedSource);
        final JsonNode decoratorDeclarations = bridgeResult.decoratorDeclarations();
        if (decoratorDeclarations == null || !decoratorDeclarations.isObject()) {
            throw new JvmCompilationException(
                    "TSJ-BACKEND-AST-SCHEMA",
                    "TypeScript syntax bridge payload missing `decoratorDeclarations` object.",
                    null,
                    null,
                    normalizedSource.toString(),
                    FEATURE_ID,
                    GUIDANCE
            );
        }

        final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators =
                parseImportedDecoratorBindings(normalizedSource, decoratorDeclarations.path("javaImports"));
        importedDecoratorBindingsByFile.put(normalizedSource, toImportedDecoratorClassNames(importedDecorators));
        classes.addAll(parseDecoratedClasses(normalizedSource, decoratorDeclarations.path("classes"), importedDecorators));

        final JsonNode relativeImports = decoratorDeclarations.path("relativeImports");
        if (relativeImports.isArray()) {
            for (JsonNode importNode : relativeImports) {
                final String importPath = importNode.asText("");
                if (!importPath.startsWith(".")) {
                    continue;
                }
                final Path dependency = resolveRelativeModule(normalizedSource, importPath);
                if (dependency != null) {
                    collectDecorators(dependency, visited, classes, importedDecoratorBindingsByFile);
                }
            }
        }
    }

    private List<TsDecoratedClass> parseDecoratedClasses(
            final Path sourceFile,
            final JsonNode classesNode,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        if (!classesNode.isArray()) {
            return List.of();
        }
        final List<TsDecoratedClass> classes = new ArrayList<>();
        for (JsonNode classNode : classesNode) {
            final String className = classNode.path("className").asText("");
            if (className.isBlank()) {
                continue;
            }
            final List<TsDecoratorUse> classDecorators = parseDecorators(
                    classNode.path("decorators"),
                    sourceFile,
                    importedDecorators,
                    TsDecoratorClasspathResolver.DecoratorTarget.CLASS,
                    false
            );
            final List<TsDecoratedField> fields = parseFields(sourceFile, classNode.path("fields"), importedDecorators);
            final List<TsDecoratedMethod> methods = parseMethods(sourceFile, classNode.path("methods"), importedDecorators);
            classes.add(new TsDecoratedClass(
                    sourceFile,
                    className,
                    classNode.path("line").asInt(1),
                    parseSpan(classNode),
                    rawTextList(classNode.path("genericParameters")),
                    nullableText(classNode, "extendsType"),
                    rawTextList(classNode.path("implementsTypes")),
                    classDecorators,
                    fields,
                    methods
            ));
        }
        return List.copyOf(classes);
    }

    private List<TsDecoratedField> parseFields(
            final Path sourceFile,
            final JsonNode fieldsNode,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        if (!fieldsNode.isArray()) {
            return List.of();
        }
        final List<TsDecoratedField> fields = new ArrayList<>();
        for (JsonNode fieldNode : fieldsNode) {
            final String fieldName = fieldNode.path("fieldName").asText("");
            if (fieldName.isBlank()) {
                continue;
            }
            fields.add(new TsDecoratedField(
                    fieldName,
                    fieldNode.path("line").asInt(1),
                    parseSpan(fieldNode),
                    parseVisibility(fieldNode),
                    nullableText(fieldNode, "typeAnnotation"),
                    parseDecorators(
                            fieldNode.path("decorators"),
                            sourceFile,
                            importedDecorators,
                            TsDecoratorClasspathResolver.DecoratorTarget.FIELD,
                            false
                    )
            ));
        }
        return List.copyOf(fields);
    }

    private List<TsDecoratedMethod> parseMethods(
            final Path sourceFile,
            final JsonNode methodsNode,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        if (!methodsNode.isArray()) {
            return List.of();
        }
        final List<TsDecoratedMethod> methods = new ArrayList<>();
        for (JsonNode methodNode : methodsNode) {
            final String methodName = methodNode.path("methodName").asText("");
            if (methodName.isBlank()) {
                continue;
            }
            final boolean constructor = methodNode.path("constructor").asBoolean(false);
            final TsDecoratorClasspathResolver.DecoratorTarget target = constructor
                    ? TsDecoratorClasspathResolver.DecoratorTarget.CONSTRUCTOR
                    : TsDecoratorClasspathResolver.DecoratorTarget.METHOD;
            methods.add(new TsDecoratedMethod(
                    methodName,
                    methodNode.path("line").asInt(1),
                    parseSpan(methodNode),
                    parseVisibility(methodNode),
                    rawTextList(methodNode.path("genericParameters")),
                    nullableText(methodNode, "returnTypeAnnotation"),
                    parseParameters(sourceFile, methodNode.path("parameters"), importedDecorators),
                    constructor,
                    parseDecorators(methodNode.path("decorators"), sourceFile, importedDecorators, target, false)
            ));
        }
        return List.copyOf(methods);
    }

    private List<TsDecoratedParameter> parseParameters(
            final Path sourceFile,
            final JsonNode parametersNode,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        if (!parametersNode.isArray()) {
            return List.of();
        }
        final List<TsDecoratedParameter> parameters = new ArrayList<>();
        for (JsonNode parameterNode : parametersNode) {
            final String parameterName = parameterNode.path("name").asText("");
            if (parameterName.isBlank()) {
                continue;
            }
            parameters.add(new TsDecoratedParameter(
                    parameterNode.path("index").asInt(parameters.size()),
                    parameterName,
                    parseDecorators(
                            parameterNode.path("decorators"),
                            sourceFile,
                            importedDecorators,
                            TsDecoratorClasspathResolver.DecoratorTarget.PARAMETER,
                            true
                    ),
                    nullableText(parameterNode, "typeAnnotation"),
                    parseSpan(parameterNode),
                    parseVisibility(parameterNode)
            ));
        }
        return List.copyOf(parameters);
    }

    private List<TsDecoratorUse> parseDecorators(
            final JsonNode decoratorsNode,
            final Path sourceFile,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators,
            final TsDecoratorClasspathResolver.DecoratorTarget target,
            final boolean skipUnsupportedWhenRelaxed
    ) {
        if (!decoratorsNode.isArray()) {
            return List.of();
        }
        final List<TsDecoratorUse> decorators = new ArrayList<>();
        for (JsonNode decoratorNode : decoratorsNode) {
            final String decoratorName = decoratorNode.path("name").asText("");
            if (decoratorName.isBlank()) {
                continue;
            }
            final int line = decoratorNode.path("line").asInt(1);
            final TsDecoratorClasspathResolver.ImportedDecoratorBinding importedBinding =
                    importedDecorators.get(decoratorName);
            if (supportedBareDecorators.contains(decoratorName)) {
                decorators.add(new TsDecoratorUse(
                        decoratorName,
                        nullableText(decoratorNode, "rawArgs"),
                        line,
                        parseSpan(decoratorNode)
                ));
                continue;
            }
            if (importedBinding == null) {
                if (strictUnsupportedDecorators) {
                    throw unsupportedDecoratorError(sourceFile, line, decoratorName);
                }
                if (skipUnsupportedWhenRelaxed) {
                    continue;
                }
                continue;
            }
            validateImportedDecoratorBinding(importedBinding, sourceFile, line, decoratorName, target);
            decorators.add(new TsDecoratorUse(
                    decoratorName,
                    nullableText(decoratorNode, "rawArgs"),
                    line,
                    parseSpan(decoratorNode)
            ));
        }
        return List.copyOf(decorators);
    }

    private Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> parseImportedDecoratorBindings(
            final Path sourceFile,
            final JsonNode importsNode
    ) {
        if (!importsNode.isArray()) {
            return Map.of();
        }
        final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> bindings = new LinkedHashMap<>();
        for (JsonNode importNode : importsNode) {
            final String localName = importNode.path("localName").asText("");
            final String annotationClassName = importNode.path("annotationClassName").asText("");
            if (localName.isBlank() || annotationClassName.isBlank()) {
                continue;
            }
            bindings.put(localName, new TsDecoratorClasspathResolver.ImportedDecoratorBinding(
                    localName,
                    annotationClassName,
                    sourceFile,
                    importNode.path("line").asInt(1)
            ));
        }
        return Map.copyOf(bindings);
    }

    private void validateImportedDecoratorBinding(
            final TsDecoratorClasspathResolver.ImportedDecoratorBinding importedBinding,
            final Path sourceFile,
            final int lineNumber,
            final String decoratorName,
            final TsDecoratorClasspathResolver.DecoratorTarget target
    ) {
        if (classpathResolver == null) {
            throw new JvmCompilationException(
                    TsDecoratorClasspathResolver.RESOLUTION_CODE,
                    "Decorator @" + decoratorName + " is imported from `java:` module but classpath resolution is unavailable.",
                    lineNumber,
                    1,
                    sourceFile.toString(),
                    RESOLUTION_FEATURE_ID,
                    RESOLUTION_GUIDANCE
            );
        }
        classpathResolver.validateUsage(importedBinding, target, sourceFile, lineNumber, decoratorName);
    }

    private static String nullableText(final JsonNode node, final String key) {
        if (!node.hasNonNull(key)) {
            return null;
        }
        final String value = node.path(key).asText("");
        return value.isBlank() ? null : value;
    }

    private static List<String> rawTextList(final JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        final List<String> values = new ArrayList<>();
        for (JsonNode entry : node) {
            final String value = entry.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static TsSourceSpan parseSpan(final JsonNode node) {
        final JsonNode spanNode = node.path("span");
        if (!spanNode.isObject()) {
            return TsSourceSpan.singleLine(node.path("line").asInt(1));
        }
        return new TsSourceSpan(
                spanNode.path("line").asInt(node.path("line").asInt(1)),
                spanNode.path("column").asInt(1),
                spanNode.path("endLine").asInt(spanNode.path("line").asInt(node.path("line").asInt(1))),
                spanNode.path("endColumn").asInt(1)
        );
    }

    private static TsVisibility parseVisibility(final JsonNode node) {
        return TsVisibility.fromWireValue(nullableText(node, "visibility"));
    }

    private static Map<Path, Map<String, String>> copyImportedDecoratorBindings(
            final Map<Path, Map<String, String>> importedDecoratorBindingsByFile
    ) {
        final Map<Path, Map<String, String>> copied = new LinkedHashMap<>();
        for (Map.Entry<Path, Map<String, String>> entry : importedDecoratorBindingsByFile.entrySet()) {
            copied.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(copied);
    }

    private static Map<String, String> toImportedDecoratorClassNames(
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        final Map<String, String> classNames = new LinkedHashMap<>();
        for (Map.Entry<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> entry : importedDecorators.entrySet()) {
            classNames.put(entry.getKey(), entry.getValue().annotationClassName());
        }
        return Map.copyOf(classNames);
    }

    private static Path resolveRelativeModule(final Path sourceFile, final String importPath) {
        final Path parent = sourceFile.getParent();
        final Path base = parent == null ? Path.of(importPath) : parent.resolve(importPath);
        final Path normalizedBase = base.normalize();
        final List<Path> candidates = new ArrayList<>();
        final String baseText = normalizedBase.toString();
        if (baseText.endsWith(".ts") || baseText.endsWith(".tsx")) {
            candidates.add(normalizedBase);
        } else {
            candidates.add(Path.of(baseText + ".ts"));
            candidates.add(Path.of(baseText + ".tsx"));
            candidates.add(normalizedBase.resolve("index.ts"));
        }
        for (Path candidate : candidates) {
            final Path normalizedCandidate = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalizedCandidate) && Files.isRegularFile(normalizedCandidate)) {
                return normalizedCandidate;
            }
        }
        return null;
    }

    private static JvmCompilationException unsupportedDecoratorError(
            final Path sourceFile,
            final int lineNumber,
            final String decoratorName
    ) {
        return new JvmCompilationException(
                "TSJ-DECORATOR-UNSUPPORTED",
                "Unsupported decorator in TSJ-32a subset: @" + decoratorName + ".",
                lineNumber,
                1,
                sourceFile.toString(),
                FEATURE_ID,
                GUIDANCE
        );
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

    record ExtractionResult(
            TsDecoratorModel model,
            Map<Path, Map<String, String>> importedDecoratorBindingsByFile
    ) {
        ExtractionResult {
            model = Objects.requireNonNull(model, "model");
            importedDecoratorBindingsByFile = Map.copyOf(Objects.requireNonNull(
                    importedDecoratorBindingsByFile,
                    "importedDecoratorBindingsByFile"
            ));
        }
    }
}
