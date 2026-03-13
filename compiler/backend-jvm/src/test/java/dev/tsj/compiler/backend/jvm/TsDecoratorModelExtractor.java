package dev.tsj.compiler.backend.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Extracts a minimal TS decorator model from entry/module graph.
 */
public final class TsDecoratorModelExtractor {
    private static final String FEATURE_ID = "TSJ32A-DECORATOR-MODEL";
    private static final String PARAM_FEATURE_ID = "TSJ32C-PARAM-ANNOTATIONS";
    private static final String GUIDANCE =
            "Use supported class/method decorator syntax and declared TSJ mapping decorators.";
    private static final String PARAM_GUIDANCE =
            "Use supported parameter decorators: @RequestParam, @PathVariable, @RequestHeader, @RequestBody, "
                    + "@Qualifier, @NotNull, @NotBlank, @Size, @Min, @Max, @Valid.";
    private static final String RESOLUTION_FEATURE_ID = TsDecoratorClasspathResolver.FEATURE_ID;
    private static final String RESOLUTION_GUIDANCE = TsDecoratorClasspathResolver.GUIDANCE;

    private static final Pattern NAMED_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s*(?:type\\s+)?\\{([^}]*)}\\s*from\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Pattern SIDE_EFFECT_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s*[\"']([^\"']+)[\"']\\s*;\\s*$"
    );
    private static final Pattern INTEROP_MODULE_PATTERN = Pattern.compile(
            "^java:([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)$"
    );
    private static final Pattern DECORATOR_PATTERN = Pattern.compile(
            "^\\s*@([A-Za-z_$][A-Za-z0-9_$]*)(?:\\((.*)\\))?\\s*$"
    );
    private static final Pattern CLASS_DECLARATION_PATTERN = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b.*$"
    );
    private static final Pattern METHOD_DECLARATION_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?(?:async\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "\\s*\\((.*)\\)\\s*\\{?\\s*$"
    );
    private static final Pattern FIELD_DECLARATION_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?(?:readonly\\s+)?"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?::[^;=]+)?(?:\\s*=.*)?;\\s*$"
    );

    private final TsDecoratorAnnotationMapping annotationMapping;
    private final TsDecoratorClasspathResolver classpathResolver;
    private final boolean strictUnsupportedDecorators;

    public TsDecoratorModelExtractor() {
        this(new TsDecoratorAnnotationMapping());
    }

    public TsDecoratorModelExtractor(final TsDecoratorAnnotationMapping annotationMapping) {
        this(annotationMapping, null);
    }

    public TsDecoratorModelExtractor(
            final TsDecoratorAnnotationMapping annotationMapping,
            final TsDecoratorClasspathResolver classpathResolver
    ) {
        this(annotationMapping, classpathResolver, true);
    }

    public TsDecoratorModelExtractor(
            final TsDecoratorAnnotationMapping annotationMapping,
            final TsDecoratorClasspathResolver classpathResolver,
            final boolean strictUnsupportedDecorators
    ) {
        this.annotationMapping = Objects.requireNonNull(annotationMapping, "annotationMapping");
        this.classpathResolver = classpathResolver;
        this.strictUnsupportedDecorators = strictUnsupportedDecorators;
    }

    public TsDecoratorModel extract(final Path entryFile) {
        return extractWithImportedDecoratorBindings(entryFile).model();
    }

    public ExtractionResult extractWithImportedDecoratorBindings(final Path entryFile) {
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

        final List<String> lines;
        try {
            lines = Files.readAllLines(normalizedSource, UTF_8);
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

        final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators =
                parseImportedDecoratorBindings(normalizedSource, lines);
        importedDecoratorBindingsByFile.put(
                normalizedSource,
                toImportedDecoratorClassNames(importedDecorators)
        );
        classes.addAll(parseDecoratedClasses(normalizedSource, lines, importedDecorators));
        for (String line : lines) {
            final Matcher namedImport = NAMED_IMPORT_PATTERN.matcher(line);
            if (namedImport.matches()) {
                final String importPath = namedImport.group(2);
                if (importPath.startsWith(".")) {
                    final Path dependency = resolveRelativeModule(normalizedSource, importPath);
                    if (dependency != null) {
                        collectDecorators(dependency, visited, classes, importedDecoratorBindingsByFile);
                    }
                }
                continue;
            }
            final Matcher sideEffectImport = SIDE_EFFECT_IMPORT_PATTERN.matcher(line);
            if (sideEffectImport.matches()) {
                final String importPath = sideEffectImport.group(1);
                if (importPath.startsWith(".")) {
                    final Path dependency = resolveRelativeModule(normalizedSource, importPath);
                    if (dependency != null) {
                        collectDecorators(dependency, visited, classes, importedDecoratorBindingsByFile);
                    }
                }
            }
        }
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

    private List<TsDecoratedClass> parseDecoratedClasses(
            final Path sourceFile,
            final List<String> lines,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        final List<TsDecoratedClass> classes = new ArrayList<>();
        final List<TsDecoratorUse> pendingClassDecorators = new ArrayList<>();
        final List<TsDecoratorUse> pendingMethodDecorators = new ArrayList<>();
        ActiveClass activeClass = null;
        int classBraceDepth = 0;

        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            final int lineNumber = index + 1;
            final String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("@")) {
                final Matcher decoratorMatcher = DECORATOR_PATTERN.matcher(trimmed);
                if (!decoratorMatcher.matches()) {
                    throw decoratorSyntaxError(sourceFile, lineNumber, trimmed);
                }
                final String decoratorName = decoratorMatcher.group(1);
                final TsDecoratorUse decorator = new TsDecoratorUse(
                        decoratorName,
                        decoratorMatcher.group(2),
                        lineNumber
                );
                if (activeClass == null) {
                    pendingClassDecorators.add(decorator);
                } else {
                    pendingMethodDecorators.add(decorator);
                }
                continue;
            }

            if (activeClass == null) {
                final Matcher classMatcher = CLASS_DECLARATION_PATTERN.matcher(trimmed);
                if (classMatcher.matches()) {
                    validateDecoratorsForTarget(
                            pendingClassDecorators,
                            importedDecorators,
                            sourceFile,
                            TsDecoratorClasspathResolver.DecoratorTarget.CLASS
                    );
                    activeClass = new ActiveClass(classMatcher.group(1), lineNumber, List.copyOf(pendingClassDecorators));
                    pendingClassDecorators.clear();
                    classBraceDepth = braceDelta(line);
                    continue;
                }
                if (!pendingClassDecorators.isEmpty()) {
                    final TsDecoratorUse dangling = pendingClassDecorators.getFirst();
                    throw new JvmCompilationException(
                            "TSJ-DECORATOR-TARGET",
                            "Decorator @" + dangling.name() + " must annotate a class declaration.",
                            dangling.line(),
                            1,
                            sourceFile.toString(),
                            FEATURE_ID,
                            GUIDANCE
                    );
                }
                continue;
            }

            if (trimmed.startsWith("}")) {
                classBraceDepth += braceDelta(line);
                if (classBraceDepth <= 0) {
                    classes.add(activeClass.toRecord(sourceFile));
                    activeClass = null;
                    pendingMethodDecorators.clear();
                }
                continue;
            }

            final Matcher methodMatcher = METHOD_DECLARATION_PATTERN.matcher(trimmed);
            if (methodMatcher.matches()) {
                final String methodName = methodMatcher.group(1);
                final boolean constructor = "constructor".equals(methodName);
                validateDecoratorsForTarget(
                        pendingMethodDecorators,
                        importedDecorators,
                        sourceFile,
                        constructor
                                ? TsDecoratorClasspathResolver.DecoratorTarget.CONSTRUCTOR
                                : TsDecoratorClasspathResolver.DecoratorTarget.METHOD
                );
                final List<TsDecoratedParameter> parameters = parseParameters(
                        methodMatcher.group(2),
                        sourceFile,
                        lineNumber,
                        importedDecorators
                );
                activeClass.methods.add(new TsDecoratedMethod(
                        methodName,
                        lineNumber,
                        parameters,
                        constructor,
                        List.copyOf(pendingMethodDecorators)
                ));
                pendingMethodDecorators.clear();
            } else if (!pendingMethodDecorators.isEmpty()) {
                final Matcher fieldMatcher = FIELD_DECLARATION_PATTERN.matcher(trimmed);
                if (fieldMatcher.matches()) {
                    validateDecoratorsForTarget(
                            pendingMethodDecorators,
                            importedDecorators,
                            sourceFile,
                            TsDecoratorClasspathResolver.DecoratorTarget.FIELD
                    );
                    activeClass.fields.add(new TsDecoratedField(
                            fieldMatcher.group(1),
                            lineNumber,
                            List.copyOf(pendingMethodDecorators)
                    ));
                    pendingMethodDecorators.clear();
                } else {
                    final TsDecoratorUse dangling = pendingMethodDecorators.getFirst();
                    throw new JvmCompilationException(
                            "TSJ-DECORATOR-TARGET",
                            "Decorator @" + dangling.name() + " must annotate a class method or field.",
                            dangling.line(),
                            1,
                            sourceFile.toString(),
                            FEATURE_ID,
                            GUIDANCE
                    );
                }
            }

            classBraceDepth += braceDelta(line);
            if (classBraceDepth <= 0) {
                classes.add(activeClass.toRecord(sourceFile));
                activeClass = null;
                pendingMethodDecorators.clear();
            }
        }

        if (activeClass != null) {
            classes.add(activeClass.toRecord(sourceFile));
        }
        if (!pendingClassDecorators.isEmpty()) {
            final TsDecoratorUse dangling = pendingClassDecorators.getFirst();
            throw new JvmCompilationException(
                    "TSJ-DECORATOR-TARGET",
                    "Decorator @" + dangling.name() + " must annotate a class declaration.",
                    dangling.line(),
                    1,
                    sourceFile.toString(),
                    FEATURE_ID,
                    GUIDANCE
            );
        }
        return List.copyOf(classes);
    }

    private List<TsDecoratedParameter> parseParameters(
            final String rawParameters,
            final Path sourceFile,
            final int lineNumber,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        final String trimmed = rawParameters == null ? "" : rawParameters.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        final List<String> segments = splitTopLevel(trimmed, ',');
        final List<TsDecoratedParameter> parameters = new ArrayList<>();
        int parameterIndex = 0;
        for (String rawSegment : segments) {
            if (rawSegment == null || rawSegment.trim().isEmpty()) {
                continue;
            }
            parameters.add(parseParameter(rawSegment, parameterIndex, sourceFile, lineNumber, importedDecorators));
            parameterIndex++;
        }
        return List.copyOf(parameters);
    }

    private TsDecoratedParameter parseParameter(
            final String rawParameterSegment,
            final int parameterIndex,
            final Path sourceFile,
            final int lineNumber,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators
    ) {
        String remaining = rawParameterSegment.trim();
        final List<TsDecoratorUse> decorators = new ArrayList<>();
        while (remaining.startsWith("@")) {
            final ParameterDecoratorParseResult parsed;
            try {
                parsed = parseLeadingParameterDecorator(remaining);
            } catch (final IllegalArgumentException illegalArgumentException) {
                throw new JvmCompilationException(
                        "TSJ-DECORATOR-PARAM",
                        "Invalid parameter decorator syntax in TSJ-32c subset: " + remaining,
                        lineNumber,
                        1,
                        sourceFile.toString(),
                        PARAM_FEATURE_ID,
                        PARAM_GUIDANCE
                );
            }
            if (annotationMapping.mapParameterDecorator(parsed.decoratorName()).isEmpty()) {
                final TsDecoratorClasspathResolver.ImportedDecoratorBinding importedBinding =
                        importedDecorators.get(parsed.decoratorName());
                if (importedBinding == null) {
                    if (strictUnsupportedDecorators) {
                        throw new JvmCompilationException(
                                "TSJ-DECORATOR-PARAM",
                                "Unsupported parameter decorator in TSJ-32c subset: @"
                                        + parsed.decoratorName()
                                        + ". Supported: RequestParam, PathVariable, RequestHeader, RequestBody, Qualifier, "
                                        + "NotNull, NotBlank, Size, Min, Max, Valid, or java-imported runtime annotations.",
                                lineNumber,
                                1,
                                sourceFile.toString(),
                                PARAM_FEATURE_ID,
                                PARAM_GUIDANCE
                        );
                    }
                    remaining = parsed.remaining().trim();
                    continue;
                }
                validateImportedDecoratorBinding(
                        importedBinding,
                        sourceFile,
                        lineNumber,
                        parsed.decoratorName(),
                        TsDecoratorClasspathResolver.DecoratorTarget.PARAMETER
                );
            }
            decorators.add(new TsDecoratorUse(parsed.decoratorName(), parsed.rawArgs(), lineNumber));
            remaining = parsed.remaining().trim();
        }
        if (remaining.isEmpty()) {
            throw new JvmCompilationException(
                    "TSJ-DECORATOR-PARAM",
                    "Missing parameter declaration after parameter decorators.",
                    lineNumber,
                    1,
                    sourceFile.toString(),
                    PARAM_FEATURE_ID,
                    PARAM_GUIDANCE
            );
        }
        final String parameterName;
        try {
            parameterName = parseParameterName(remaining);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw new JvmCompilationException(
                    "TSJ-DECORATOR-PARAM",
                    "Invalid parameter declaration in TSJ-32c subset: " + remaining,
                    lineNumber,
                    1,
                    sourceFile.toString(),
                    PARAM_FEATURE_ID,
                    PARAM_GUIDANCE
            );
        }
        final String typeAnnotation = parseParameterTypeAnnotation(remaining, parameterName);
        return new TsDecoratedParameter(parameterIndex, parameterName, List.copyOf(decorators), typeAnnotation);
    }

    private void validateDecoratorsForTarget(
            final List<TsDecoratorUse> decorators,
            final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> importedDecorators,
            final Path sourceFile,
            final TsDecoratorClasspathResolver.DecoratorTarget target
    ) {
        for (TsDecoratorUse decorator : decorators) {
            if (annotationMapping.supportedDecoratorNames().contains(decorator.name())) {
                continue;
            }
            final TsDecoratorClasspathResolver.ImportedDecoratorBinding importedBinding =
                    importedDecorators.get(decorator.name());
            if (importedBinding == null) {
                if (strictUnsupportedDecorators) {
                    throw unsupportedDecoratorError(sourceFile, decorator.line(), decorator.name());
                }
                continue;
            }
            validateImportedDecoratorBinding(importedBinding, sourceFile, decorator.line(), decorator.name(), target);
        }
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

    private static Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> parseImportedDecoratorBindings(
            final Path sourceFile,
            final List<String> lines
    ) {
        final Map<String, TsDecoratorClasspathResolver.ImportedDecoratorBinding> bindings = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            final Matcher namedImport = NAMED_IMPORT_PATTERN.matcher(line);
            if (!namedImport.matches()) {
                continue;
            }
            final String rawBindings = namedImport.group(1);
            final String importPath = namedImport.group(2);
            final Matcher interopModule = INTEROP_MODULE_PATTERN.matcher(importPath);
            if (!interopModule.matches()) {
                continue;
            }
            final String annotationClassName = interopModule.group(1);
            final List<ImportBinding> parsedBindings = parseNamedImportBindings(rawBindings, sourceFile, index + 1);
            for (ImportBinding parsedBinding : parsedBindings) {
                final TsDecoratorClasspathResolver.ImportedDecoratorBinding existing =
                        bindings.get(parsedBinding.localName());
                if (existing != null && !existing.annotationClassName().equals(annotationClassName)) {
                    throw new JvmCompilationException(
                            TsDecoratorClasspathResolver.RESOLUTION_CODE,
                            "Conflicting java decorator imports for local name `"
                                    + parsedBinding.localName()
                                    + "`: `"
                                    + existing.annotationClassName()
                                    + "` vs `"
                                    + annotationClassName
                                    + "`.",
                            index + 1,
                            1,
                            sourceFile.toString(),
                            RESOLUTION_FEATURE_ID,
                            RESOLUTION_GUIDANCE
                    );
                }
                bindings.put(
                        parsedBinding.localName(),
                        new TsDecoratorClasspathResolver.ImportedDecoratorBinding(
                                parsedBinding.localName(),
                                annotationClassName,
                                sourceFile,
                                index + 1
                        )
                );
            }
        }
        return Map.copyOf(bindings);
    }

    private static List<ImportBinding> parseNamedImportBindings(
            final String rawBindings,
            final Path sourceFile,
            final int lineNumber
    ) {
        final List<ImportBinding> parsed = new ArrayList<>();
        final String[] segments = rawBindings.split(",");
        for (String segment : segments) {
            final String trimmed = segment.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("type ")) {
                continue;
            }
            final String importedName;
            final String localName;
            if (trimmed.contains(" as ")) {
                final String[] parts = trimmed.split("\\s+as\\s+");
                if (parts.length != 2) {
                    throw invalidImportedBinding(sourceFile, lineNumber, trimmed);
                }
                importedName = parts[0].trim();
                localName = parts[1].trim();
            } else {
                importedName = trimmed;
                localName = trimmed;
            }
            if (!isValidIdentifier(importedName) || !isValidIdentifier(localName)) {
                throw invalidImportedBinding(sourceFile, lineNumber, trimmed);
            }
            parsed.add(new ImportBinding(importedName, localName));
        }
        return List.copyOf(parsed);
    }

    private static JvmCompilationException invalidImportedBinding(
            final Path sourceFile,
            final int lineNumber,
            final String binding
    ) {
        return new JvmCompilationException(
                TsDecoratorClasspathResolver.RESOLUTION_CODE,
                "Invalid java decorator import binding: `" + binding + "`.",
                lineNumber,
                1,
                sourceFile.toString(),
                RESOLUTION_FEATURE_ID,
                RESOLUTION_GUIDANCE
        );
    }

    private static boolean isValidIdentifier(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        final char first = value.charAt(0);
        if (!Character.isJavaIdentifierStart(first) && first != '$') {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (!Character.isJavaIdentifierPart(current) && current != '$') {
                return false;
            }
        }
        return true;
    }

    private static String parseParameterName(final String rawParameter) {
        String text = rawParameter.trim();
        if (text.startsWith("...")) {
            text = text.substring(3).trim();
        }
        int index = 0;
        if (index < text.length()
                && (Character.isJavaIdentifierStart(text.charAt(index)) || text.charAt(index) == '$')) {
            index++;
            while (index < text.length()) {
                final char value = text.charAt(index);
                if (Character.isJavaIdentifierPart(value) || value == '$') {
                    index++;
                    continue;
                }
                break;
            }
            return text.substring(0, index);
        }
        throw new IllegalArgumentException("Invalid parameter declaration segment: " + rawParameter);
    }

    private static String parseParameterTypeAnnotation(
            final String rawParameter,
            final String parameterName
    ) {
        String text = rawParameter == null ? "" : rawParameter.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("...")) {
            text = text.substring(3).trim();
        }
        if (!text.startsWith(parameterName)) {
            return null;
        }
        text = text.substring(parameterName.length()).trim();
        if (text.startsWith("?")) {
            text = text.substring(1).trim();
        }
        if (!text.startsWith(":")) {
            return null;
        }
        text = text.substring(1).trim();
        if (text.isEmpty()) {
            return null;
        }
        final int assignmentIndex = indexOfTopLevelChar(text, '=');
        if (assignmentIndex >= 0) {
            text = text.substring(0, assignmentIndex).trim();
        }
        return text.isEmpty() ? null : text;
    }

    private static int indexOfTopLevelChar(final String text, final char target) {
        int braceDepth = 0;
        int bracketDepth = 0;
        int parenDepth = 0;
        int angleDepth = 0;
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
            if (value == '\'' || value == '"') {
                inString = true;
                quote = value;
                continue;
            }
            switch (value) {
                case '{' -> braceDepth++;
                case '}' -> braceDepth = Math.max(0, braceDepth - 1);
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                case '(' -> parenDepth++;
                case ')' -> parenDepth = Math.max(0, parenDepth - 1);
                case '<' -> angleDepth++;
                case '>' -> angleDepth = Math.max(0, angleDepth - 1);
                default -> {
                    if (value == target
                            && braceDepth == 0
                            && bracketDepth == 0
                            && parenDepth == 0
                            && angleDepth == 0) {
                        return index;
                    }
                }
            }
        }
        return -1;
    }

    private static ParameterDecoratorParseResult parseLeadingParameterDecorator(final String raw) {
        if (raw.length() < 2) {
            throw new IllegalArgumentException("Invalid parameter decorator syntax: " + raw);
        }
        final char first = raw.charAt(1);
        if (!Character.isJavaIdentifierStart(first) && first != '$') {
            throw new IllegalArgumentException("Invalid parameter decorator syntax: " + raw);
        }
        int index = 1;
        while (index < raw.length()) {
            final char value = raw.charAt(index);
            if (Character.isJavaIdentifierPart(value) || value == '$') {
                index++;
                continue;
            }
            break;
        }
        if (index <= 1) {
            throw new IllegalArgumentException("Invalid parameter decorator syntax: " + raw);
        }
        final String decoratorName = raw.substring(1, index);
        String rawArgs = null;
        if (index < raw.length() && raw.charAt(index) == '(') {
            int depth = 1;
            int cursor = index + 1;
            boolean inString = false;
            char quote = 0;
            while (cursor < raw.length() && depth > 0) {
                final char value = raw.charAt(cursor);
                if (inString) {
                    if (value == '\\') {
                        cursor += 2;
                        continue;
                    }
                    if (value == quote) {
                        inString = false;
                    }
                    cursor++;
                    continue;
                }
                if (value == '"' || value == '\'') {
                    inString = true;
                    quote = value;
                    cursor++;
                    continue;
                }
                if (value == '(') {
                    depth++;
                } else if (value == ')') {
                    depth--;
                }
                cursor++;
            }
            if (depth != 0) {
                throw new IllegalArgumentException("Unclosed decorator argument list: " + raw);
            }
            rawArgs = raw.substring(index + 1, cursor - 1).trim();
            index = cursor;
        }
        return new ParameterDecoratorParseResult(
                decoratorName,
                rawArgs,
                raw.substring(index)
        );
    }

    private static List<String> splitTopLevel(final String text, final char separator) {
        final List<String> segments = new ArrayList<>();
        int start = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int angleDepth = 0;
        boolean inString = false;
        char quote = 0;
        int index = 0;
        while (index < text.length()) {
            final char value = text.charAt(index);
            if (inString) {
                if (value == '\\') {
                    index += 2;
                    continue;
                }
                if (value == quote) {
                    inString = false;
                }
                index++;
                continue;
            }
            if (value == '"' || value == '\'') {
                inString = true;
                quote = value;
                index++;
                continue;
            }
            if (value == '(') {
                parenDepth++;
            } else if (value == ')') {
                parenDepth--;
            } else if (value == '<') {
                angleDepth++;
            } else if (value == '>') {
                if (angleDepth > 0) {
                    angleDepth--;
                }
            } else if (value == '[') {
                bracketDepth++;
            } else if (value == ']') {
                bracketDepth--;
            } else if (value == '{') {
                braceDepth++;
            } else if (value == '}') {
                braceDepth--;
            } else if (value == separator
                    && parenDepth == 0
                    && angleDepth == 0
                    && bracketDepth == 0
                    && braceDepth == 0) {
                segments.add(text.substring(start, index));
                start = index + 1;
            }
            index++;
        }
        segments.add(text.substring(start));
        return List.copyOf(segments);
    }

    private static int braceDelta(final String line) {
        int delta = 0;
        for (int index = 0; index < line.length(); index++) {
            final char value = line.charAt(index);
            if (value == '{') {
                delta++;
            } else if (value == '}') {
                delta--;
            }
        }
        return delta;
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

    private static JvmCompilationException decoratorSyntaxError(
            final Path sourceFile,
            final int lineNumber,
            final String rawDecorator
    ) {
        return new JvmCompilationException(
                "TSJ-DECORATOR-SYNTAX",
                "Invalid decorator syntax in TSJ-32a subset: " + rawDecorator,
                lineNumber,
                1,
                sourceFile.toString(),
                FEATURE_ID,
                GUIDANCE
        );
    }

    private JvmCompilationException unsupportedDecoratorError(
            final Path sourceFile,
            final int lineNumber,
            final String decoratorName
    ) {
        return new JvmCompilationException(
                "TSJ-DECORATOR-UNSUPPORTED",
                "Unsupported decorator in TSJ-32a subset: @" + decoratorName
                        + ". Supported: " + String.join(", ", annotationMapping.supportedDecoratorNames()) + ".",
                lineNumber,
                1,
                sourceFile.toString(),
                FEATURE_ID,
                GUIDANCE
        );
    }

    private static final class ActiveClass {
        private final String className;
        private final int line;
        private final List<TsDecoratorUse> decorators;
        private final List<TsDecoratedField> fields;
        private final List<TsDecoratedMethod> methods;

        private ActiveClass(final String className, final int line, final List<TsDecoratorUse> decorators) {
            this.className = className;
            this.line = line;
            this.decorators = decorators;
            this.fields = new ArrayList<>();
            this.methods = new ArrayList<>();
        }

        private TsDecoratedClass toRecord(final Path sourceFile) {
            return new TsDecoratedClass(
                    sourceFile,
                    className,
                    line,
                    decorators,
                    List.copyOf(fields),
                    List.copyOf(methods)
            );
        }
    }

    private record ImportBinding(String importedName, String localName) {
    }

    private record ParameterDecoratorParseResult(
            String decoratorName,
            String rawArgs,
            String remaining
    ) {
    }

    public record ExtractionResult(
            TsDecoratorModel model,
            Map<Path, Map<String, String>> importedDecoratorBindingsByFile
    ) {
        public ExtractionResult {
            model = Objects.requireNonNull(model, "model");
            importedDecoratorBindingsByFile = copyImportedDecoratorBindings(
                    Objects.requireNonNull(importedDecoratorBindingsByFile, "importedDecoratorBindingsByFile")
            );
        }
    }
}
