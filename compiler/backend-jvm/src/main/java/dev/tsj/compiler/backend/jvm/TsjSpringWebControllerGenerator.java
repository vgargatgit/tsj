package dev.tsj.compiler.backend.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generates Spring web adapter sources from TS-authored controller decorators.
 */
public final class TsjSpringWebControllerGenerator {
    private static final String PARAM_FEATURE_ID = "TSJ32C-PARAM-ANNOTATIONS";
    private static final String PARAM_GUIDANCE =
            "Use supported parameter decorators: @RequestParam, @PathVariable, @RequestHeader, @RequestBody.";
    private static final String CONTROLLER_DI_FEATURE_ID = "TSJ34D-CONTROLLER-DI";
    private static final String CONTROLLER_DI_GUIDANCE =
            "Use constructor parameters with optional @Qualifier(\"beanName\") for TS-authored controllers.";
    private static final String BINDING_CODE = "TSJ-WEB-BINDING";
    private static final String BINDING_FEATURE_ID = "TSJ34A-REQUEST-BINDING";
    private static final String BINDING_GUIDANCE =
            "Route bindings must match endpoint templates and use at most one @RequestBody parameter.";
    private static final String GENERATED_PACKAGE = "dev.tsj.generated.web";
    private static final Pattern PATH_VARIABLE_PATTERN =
            Pattern.compile("\\{([A-Za-z_$][A-Za-z0-9_$]*)\\}");
    private static final Map<Integer, String> STATUS_CODE_ENUM = Map.ofEntries(
            Map.entry(200, "OK"),
            Map.entry(201, "CREATED"),
            Map.entry(202, "ACCEPTED"),
            Map.entry(204, "NO_CONTENT"),
            Map.entry(400, "BAD_REQUEST"),
            Map.entry(401, "UNAUTHORIZED"),
            Map.entry(403, "FORBIDDEN"),
            Map.entry(404, "NOT_FOUND"),
            Map.entry(409, "CONFLICT"),
            Map.entry(422, "UNPROCESSABLE_ENTITY"),
            Map.entry(500, "INTERNAL_SERVER_ERROR")
    );
    private static final String HTTP_STATUS_ENUM_PREFIX = "org.springframework.http.HttpStatus.";

    private final TsDecoratorModelExtractor decoratorModelExtractor;
    private final TsDecoratorAnnotationMapping annotationMapping;
    private final TsAnnotationAttributeParser attributeParser;

    public TsjSpringWebControllerGenerator() {
        this(
                new TsDecoratorModelExtractor(),
                new TsDecoratorAnnotationMapping(),
                new TsAnnotationAttributeParser()
        );
    }

    TsjSpringWebControllerGenerator(
            final TsDecoratorModelExtractor decoratorModelExtractor,
            final TsDecoratorAnnotationMapping annotationMapping,
            final TsAnnotationAttributeParser attributeParser
    ) {
        this.decoratorModelExtractor = Objects.requireNonNull(decoratorModelExtractor, "decoratorModelExtractor");
        this.annotationMapping = Objects.requireNonNull(annotationMapping, "annotationMapping");
        this.attributeParser = Objects.requireNonNull(attributeParser, "attributeParser");
    }

    public TsjSpringWebControllerArtifact generate(
            final Path entryFile,
            final String programClassName,
            final Path outputDirectory
    ) {
        Objects.requireNonNull(entryFile, "entryFile");
        Objects.requireNonNull(programClassName, "programClassName");
        Objects.requireNonNull(outputDirectory, "outputDirectory");

        final Path normalizedEntry = entryFile.toAbsolutePath().normalize();
        final Path normalizedOutput = outputDirectory.toAbsolutePath().normalize();
        if (!Files.exists(normalizedEntry) || !Files.isRegularFile(normalizedEntry)) {
            throw new JvmCompilationException(
                    "TSJ-WEB-CONTROLLER",
                    "Controller source entry file not found: " + normalizedEntry
            );
        }

        final TsDecoratorModel decoratorModel = decoratorModelExtractor.extract(normalizedEntry);
        final List<ControllerModel> controllers = collectControllerModels(decoratorModel);
        if (controllers.isEmpty()) {
            return new TsjSpringWebControllerArtifact(normalizedOutput, List.of(), List.of());
        }

        final List<Path> sourceFiles = new ArrayList<>();
        final List<String> controllerClassNames = new ArrayList<>();
        final Path generatedDir = normalizedOutput.resolve(GENERATED_PACKAGE.replace('.', '/'));
        try {
            Files.createDirectories(generatedDir);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    "TSJ-WEB-CONTROLLER",
                    "Failed to create TSJ web output directory: " + ioException.getMessage(),
                    null,
                    null,
                    normalizedOutput.toString(),
                    null,
                    null,
                    ioException
            );
        }

        for (ControllerModel controller : controllers) {
            final String generatedSimpleName = controller.className() + "TsjController";
            final Path sourcePath = generatedDir.resolve(generatedSimpleName + ".java");
            final String source = renderControllerSource(programClassName, generatedSimpleName, controller);
            try {
                Files.writeString(sourcePath, source, UTF_8);
            } catch (final IOException ioException) {
                throw new JvmCompilationException(
                        "TSJ-WEB-CONTROLLER",
                        "Failed to write TSJ web adapter source: " + ioException.getMessage(),
                        null,
                        null,
                        sourcePath.toString(),
                        null,
                        null,
                        ioException
                );
            }
            sourceFiles.add(sourcePath);
            controllerClassNames.add(controller.className());
        }

        return new TsjSpringWebControllerArtifact(
                normalizedOutput,
                List.copyOf(sourceFiles),
                List.copyOf(controllerClassNames)
        );
    }

    private List<ControllerModel> collectControllerModels(final TsDecoratorModel decoratorModel) {
        final Map<String, ControllerModel> controllersByClass = new LinkedHashMap<>();
        for (TsDecoratedClass decoratedClass : decoratorModel.classes()) {
            final TsDecoratorUse restDecorator = findDecorator(decoratedClass.decorators(), "RestController");
            if (restDecorator == null) {
                continue;
            }

            final TsDecoratorUse requestMappingDecorator = findDecorator(decoratedClass.decorators(), "RequestMapping");
            final String basePath = requestMappingDecorator == null
                    ? ""
                    : parsePathArgument(requestMappingDecorator, decoratedClass.sourceFile());
            final ControllerModel controllerModel = new ControllerModel(
                    decoratedClass.className(),
                    basePath,
                    decoratedClass.line(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );

            TsDecoratedMethod constructor = null;
            for (TsDecoratedMethod method : decoratedClass.methods()) {
                if (method.constructor()) {
                    if (constructor != null) {
                        throw controllerDiDiagnostic(
                                decoratedClass.sourceFile(),
                                method.line(),
                                "Duplicate constructor declaration in controller `"
                                        + decoratedClass.className() + "`."
                        );
                    }
                    constructor = method;
                    continue;
                }

                final RouteMapping route = parseRouteMapping(method.decorators(), decoratedClass.sourceFile());
                if (route != null) {
                    final String endpointPath = normalizePath(controllerModel.basePath(), route.path());
                    final String endpointSignature = httpMethodForRouteDecorator(route.routeDecoratorName())
                            + " "
                            + endpointPath;
                    final Integer routeStatusCode = parseRouteStatusCode(
                            method.decorators(),
                            route,
                            decoratedClass.sourceFile()
                    );
                    final List<RouteParameter> parameters = parseRouteParameters(
                            method,
                            decoratedClass.sourceFile(),
                            endpointPath,
                            endpointSignature
                    );
                    controllerModel.routes().add(new RouteMethod(
                            method.methodName(),
                            route.routeDecoratorName(),
                            route.path(),
                            parameters,
                            routeStatusCode
                    ));
                }
                final ErrorMapping errorMapping = parseErrorMapping(method.decorators(), decoratedClass.sourceFile());
                if (errorMapping != null) {
                    controllerModel.errorHandlers().add(new ErrorHandlerMethod(
                            method.methodName(),
                            errorMapping.exceptionClassNames(),
                            errorMapping.statusCode()
                    ));
                }
            }
            if (constructor != null) {
                controllerModel.constructorDependencies().addAll(
                        normalizeConstructorDependencies(
                                constructor,
                                decoratedClass.sourceFile(),
                                decoratedClass.className()
                        )
                );
            }

            if (controllerModel.routes().isEmpty() && controllerModel.errorHandlers().isEmpty()) {
                continue;
            }
            final ControllerModel existing = controllersByClass.putIfAbsent(
                    decoratedClass.className(),
                    controllerModel
            );
            if (existing != null) {
                throw new JvmCompilationException(
                        "TSJ-WEB-CONTROLLER",
                        "Duplicate TS controller class name across sources: " + decoratedClass.className(),
                        decoratedClass.line(),
                        1,
                        decoratedClass.sourceFile().toString(),
                        null,
                        "Use unique top-level TS controller class names in TSJ-34 subset."
                );
            }
        }
        return List.copyOf(controllersByClass.values());
    }

    private RouteMapping parseRouteMapping(
            final List<TsDecoratorUse> decorators,
            final Path sourceFile
    ) {
        RouteMapping route = null;
        for (TsDecoratorUse decorator : decorators) {
            if (!isRouteDecorator(decorator.name())) {
                continue;
            }
            if (route != null) {
                throw new JvmCompilationException(
                        "TSJ-WEB-CONTROLLER",
                        "TSJ-34 route methods may declare only one mapping decorator.",
                        decorator.line(),
                        1,
                        sourceFile.toString(),
                        null,
                        "Use only one of @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping."
                );
            }
            route = new RouteMapping(decorator.name(), parsePathArgument(decorator, sourceFile));
        }
        return route;
    }

    private ErrorMapping parseErrorMapping(
            final List<TsDecoratorUse> decorators,
            final Path sourceFile
    ) {
        final TsDecoratorUse exceptionHandler = findDecorator(decorators, "ExceptionHandler");
        if (exceptionHandler == null) {
            return null;
        }
        final TsDecoratorUse responseStatus = findDecorator(decorators, "ResponseStatus");
        if (responseStatus == null) {
            throw new JvmCompilationException(
                    "TSJ-WEB-CONTROLLER",
                    "@ExceptionHandler requires @ResponseStatus in TSJ-34 subset.",
                    exceptionHandler.line(),
                    1,
                    sourceFile.toString(),
                    null,
                    "Add @ResponseStatus(<supported-status-code>) on the same method."
            );
        }
        return new ErrorMapping(
                parseExceptionClassNames(exceptionHandler, sourceFile),
                parseStatusCode(responseStatus, sourceFile)
        );
    }

    private List<ConstructorDependency> normalizeConstructorDependencies(
            final TsDecoratedMethod constructor,
            final Path sourceFile,
            final String className
    ) {
        final List<String> names = normalizeParameterNames(constructor.parameters(), "dep");
        final List<ConstructorDependency> dependencies = new ArrayList<>();
        for (int index = 0; index < constructor.parameters().size(); index++) {
            final TsDecoratedParameter parameter = constructor.parameters().get(index);
            final String qualifier = parseQualifierFromConstructorParameter(
                    parameter,
                    sourceFile,
                    constructor.line(),
                    className + " constructor parameter `" + names.get(index) + "`"
            );
            dependencies.add(new ConstructorDependency(names.get(index), qualifier));
        }
        return List.copyOf(dependencies);
    }

    private String parseQualifierFromConstructorParameter(
            final TsDecoratedParameter parameter,
            final Path sourceFile,
            final int line,
            final String owner
    ) {
        if (parameter.decorators().isEmpty()) {
            return null;
        }
        TsDecoratorUse qualifierDecorator = null;
        for (TsDecoratorUse decorator : parameter.decorators()) {
            if (!"Qualifier".equals(decorator.name())) {
                throw controllerDiDiagnostic(
                        sourceFile,
                        line,
                        "Unsupported constructor parameter decorator @"
                                + decorator.name()
                                + " on `"
                                + owner
                                + "`."
                );
            }
            if (qualifierDecorator != null) {
                throw controllerDiDiagnostic(
                        sourceFile,
                        line,
                        "Multiple @Qualifier decorators are unsupported on `" + owner + "`."
                );
            }
            qualifierDecorator = decorator;
        }
        if (qualifierDecorator == null) {
            return null;
        }
        return parseQualifierValue(qualifierDecorator, sourceFile, line, owner);
    }

    private String parseQualifierValue(
            final TsDecoratorUse decorator,
            final Path sourceFile,
            final int line,
            final String owner
    ) {
        if (decorator.rawArgs() == null || decorator.rawArgs().isBlank()) {
            throw controllerDiDiagnostic(
                    sourceFile,
                    line,
                    "@Qualifier on `" + owner + "` requires a non-empty value."
            );
        }
        final TsAnnotationAttributeParser.DecoratorAttributes attributes;
        try {
            attributes = attributeParser.parse(
                    decorator.rawArgs(),
                    sourceFile,
                    decorator.line(),
                    decorator.name()
            );
        } catch (final JvmCompilationException compilationException) {
            throw controllerDiDiagnostic(
                    sourceFile,
                    line,
                    "Invalid @Qualifier arguments on `" + owner + "`."
            );
        }
        final String qualifier;
        if (attributes.has("value")) {
            qualifier = attributes.requireString("value");
        } else if (attributes.has("name")) {
            qualifier = attributes.requireString("name");
        } else {
            throw controllerDiDiagnostic(
                    sourceFile,
                    line,
                    "@Qualifier on `" + owner + "` requires a string value."
            );
        }
        final String normalized = qualifier.trim();
        if (normalized.isEmpty()) {
            throw controllerDiDiagnostic(
                    sourceFile,
                    line,
                    "@Qualifier on `" + owner + "` requires a non-empty value."
            );
        }
        return normalized;
    }

    private int parseStatusCode(final TsDecoratorUse decorator, final Path sourceFile) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes =
                parseDecoratorAttributes(decorator, sourceFile);
        final TsAnnotationAttributeParser.AnnotationValue statusValue = attributes.require("value");
        if (statusValue instanceof TsAnnotationAttributeParser.NumberValue) {
            final int statusCode = attributes.requireInt("value");
            if (!STATUS_CODE_ENUM.containsKey(statusCode)) {
                throw new JvmCompilationException(
                        "TSJ-WEB-CONTROLLER",
                        "Unsupported @ResponseStatus code in TSJ-34 subset: " + statusCode,
                        decorator.line(),
                        1,
                        sourceFile.toString(),
                        null,
                        "Use one of: 200, 201, 202, 204, 400, 401, 403, 404, 409, 422, 500."
                );
            }
            return statusCode;
        }
        if (statusValue instanceof TsAnnotationAttributeParser.EnumConstantValue enumConstantValue) {
            final String qualifiedConstant = enumConstantValue.qualifiedConstant();
            if (!qualifiedConstant.startsWith(HTTP_STATUS_ENUM_PREFIX)) {
                throw new JvmCompilationException(
                        "TSJ-WEB-CONTROLLER",
                        "Unsupported @ResponseStatus enum constant: " + qualifiedConstant,
                        decorator.line(),
                        1,
                        sourceFile.toString(),
                        null,
                        "Use enum(\"org.springframework.http.HttpStatus.<CONSTANT>\") in TSJ-32b subset."
                );
            }
            final String enumConstant = qualifiedConstant.substring(HTTP_STATUS_ENUM_PREFIX.length());
            for (Map.Entry<Integer, String> entry : STATUS_CODE_ENUM.entrySet()) {
                if (entry.getValue().equals(enumConstant)) {
                    return entry.getKey();
                }
            }
            throw new JvmCompilationException(
                    "TSJ-WEB-CONTROLLER",
                    "Unsupported @ResponseStatus enum constant: " + qualifiedConstant,
                    decorator.line(),
                    1,
                    sourceFile.toString(),
                    null,
                    "Use one of: OK, CREATED, ACCEPTED, NO_CONTENT, BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, UNPROCESSABLE_ENTITY, INTERNAL_SERVER_ERROR."
            );
        }
        throw new JvmCompilationException(
                "TSJ-WEB-CONTROLLER",
                "Unsupported @ResponseStatus attribute type in TSJ-32b subset.",
                decorator.line(),
                1,
                sourceFile.toString(),
                null,
                "Use numeric codes or enum(\"org.springframework.http.HttpStatus.<CONSTANT>\")."
        );
    }

    private Integer parseRouteStatusCode(
            final List<TsDecoratorUse> decorators,
            final RouteMapping routeMapping,
            final Path sourceFile
    ) {
        if (routeMapping == null) {
            return null;
        }
        if (findDecorator(decorators, "ExceptionHandler") != null) {
            return null;
        }
        final TsDecoratorUse responseStatus = findDecorator(decorators, "ResponseStatus");
        if (responseStatus == null) {
            return null;
        }
        return parseStatusCode(responseStatus, sourceFile);
    }

    private List<String> parseExceptionClassNames(
            final TsDecoratorUse decorator,
            final Path sourceFile
    ) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes =
                parseDecoratorAttributes(decorator, sourceFile);
        final TsAnnotationAttributeParser.AnnotationValue value = attributes.require("value");
        if (value instanceof TsAnnotationAttributeParser.StringValue) {
            final String className = attributes.requireString("value");
            validateFqcn(className, decorator, sourceFile);
            return List.of(className);
        }
        if (value instanceof TsAnnotationAttributeParser.ClassLiteralValue) {
            final String className = attributes.requireClassLiteral("value");
            validateFqcn(className, decorator, sourceFile);
            return List.of(className);
        }
        if (value instanceof TsAnnotationAttributeParser.ArrayValue arrayValue) {
            final List<String> classNames = new ArrayList<>();
            for (TsAnnotationAttributeParser.AnnotationValue element : arrayValue.elements()) {
                if (element instanceof TsAnnotationAttributeParser.ClassLiteralValue classLiteralValue) {
                    validateFqcn(classLiteralValue.qualifiedClassName(), decorator, sourceFile);
                    classNames.add(classLiteralValue.qualifiedClassName());
                    continue;
                }
                if (element instanceof TsAnnotationAttributeParser.StringValue stringValue) {
                    validateFqcn(stringValue.value(), decorator, sourceFile);
                    classNames.add(stringValue.value());
                    continue;
                }
                throw new JvmCompilationException(
                        "TSJ-WEB-CONTROLLER",
                        "Unsupported @ExceptionHandler array element type in TSJ-32b subset.",
                        decorator.line(),
                        1,
                        sourceFile.toString(),
                        null,
                        "Use classOf(\"<fqcn>\") or \"<fqcn>\" elements."
                );
            }
            if (classNames.isEmpty()) {
                throw new JvmCompilationException(
                        "TSJ-WEB-CONTROLLER",
                        "@ExceptionHandler requires at least one exception type.",
                        decorator.line(),
                        1,
                        sourceFile.toString(),
                        null,
                        "Provide one or more exception classes."
                );
            }
            return List.copyOf(classNames);
        }
        throw new JvmCompilationException(
                "TSJ-WEB-CONTROLLER",
                "Unsupported @ExceptionHandler attribute type in TSJ-32b subset.",
                decorator.line(),
                1,
                sourceFile.toString(),
                null,
                "Use classOf(\"<fqcn>\"), \"<fqcn>\", or arrays of those."
        );
    }

    private String parsePathArgument(final TsDecoratorUse decorator, final Path sourceFile) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes =
                parseDecoratorAttributes(decorator, sourceFile);
        final String path;
        if (attributes.has("path")) {
            path = attributes.requireString("path");
        } else {
            path = attributes.requireString("value");
        }
        if (!path.startsWith("/")) {
            throw new JvmCompilationException(
                    "TSJ-WEB-CONTROLLER",
                    "Decorator @" + decorator.name() + " path must start with `/`: " + path,
                    decorator.line(),
                    1,
                    sourceFile.toString(),
                    null,
                    "Use absolute Spring mapping paths such as `/users` or `/find`."
            );
        }
        return path;
    }

    private List<RouteParameter> parseRouteParameters(
            final TsDecoratedMethod method,
            final Path sourceFile,
            final String endpointPath,
            final String endpointSignature
    ) {
        final List<RouteParameter> parameters = new ArrayList<>();
        final Set<String> routePathVariables = extractPathVariables(endpointPath);
        int requestBodyParameterCount = 0;
        for (TsDecoratedParameter parameter : method.parameters()) {
            if (parameter.decorators().isEmpty()) {
                final String generatedName = "arg" + parameter.index();
                parameters.add(new RouteParameter(
                        generatedName,
                        "org.springframework.web.bind.annotation.RequestParam",
                        generatedName
                ));
                continue;
            }
            if (parameter.decorators().size() > 1) {
                throw parameterDiagnostic(
                        sourceFile,
                        method.line(),
                        "Only one parameter decorator is supported per route parameter in TSJ-32c subset. "
                                + "Endpoint: "
                                + endpointSignature
                                + ", parameter: "
                                + parameter.name()
                                + "."
                );
            }
            final TsDecoratorUse decorator = parameter.decorators().getFirst();
            final String mappedAnnotation = annotationMapping.mapParameterDecorator(decorator.name()).orElseThrow(() ->
                    parameterDiagnostic(
                            sourceFile,
                            method.line(),
                            "Unsupported parameter decorator in TSJ-32c subset: @" + decorator.name() + "."
                    )
            );
            if ("RequestBody".equals(decorator.name())) {
                if (decorator.rawArgs() != null && !decorator.rawArgs().isBlank()) {
                    throw parameterDiagnostic(
                            sourceFile,
                            method.line(),
                            "@RequestBody does not accept arguments in TSJ-32c subset."
                    );
                }
                requestBodyParameterCount++;
                if (requestBodyParameterCount > 1) {
                    throw bindingDiagnostic(
                            sourceFile,
                            method.line(),
                            endpointSignature,
                            parameter.name(),
                            "Only one @RequestBody parameter is supported per route."
                    );
                }
                parameters.add(new RouteParameter(parameter.name(), mappedAnnotation, null));
                continue;
            }
            final String annotationValue = parseNamedParameterDecoratorValue(decorator, parameter.name(), sourceFile);
            if (annotationValue.isBlank()) {
                throw bindingDiagnostic(
                        sourceFile,
                        method.line(),
                        endpointSignature,
                        parameter.name(),
                        "Binding name for @" + decorator.name() + " must not be blank."
                );
            }
            if ("PathVariable".equals(decorator.name()) && !routePathVariables.contains(annotationValue)) {
                throw bindingDiagnostic(
                        sourceFile,
                        method.line(),
                        endpointSignature,
                        parameter.name(),
                        "@PathVariable(\""
                                + annotationValue
                                + "\") does not match route template variables "
                                + routePathVariables
                                + "."
                );
            }
            parameters.add(new RouteParameter(parameter.name(), mappedAnnotation, annotationValue));
        }
        return List.copyOf(parameters);
    }

    private String parseNamedParameterDecoratorValue(
            final TsDecoratorUse decorator,
            final String parameterName,
            final Path sourceFile
    ) {
        if (decorator.rawArgs() == null || decorator.rawArgs().isBlank()) {
            return parameterName;
        }
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = attributeParser.parse(
                decorator.rawArgs(),
                sourceFile,
                decorator.line(),
                decorator.name()
        );
        if (attributes.has("name")) {
            return attributes.requireString("name");
        }
        if (attributes.has("path")) {
            return attributes.requireString("path").trim();
        }
        return attributes.requireString("value").trim();
    }

    private static JvmCompilationException parameterDiagnostic(
            final Path sourceFile,
            final int line,
            final String message
    ) {
        return new JvmCompilationException(
                "TSJ-DECORATOR-PARAM",
                message,
                line,
                1,
                sourceFile.toString(),
                PARAM_FEATURE_ID,
                PARAM_GUIDANCE
        );
    }

    private static JvmCompilationException bindingDiagnostic(
            final Path sourceFile,
            final int line,
            final String endpointSignature,
            final String parameterName,
            final String reason
    ) {
        return new JvmCompilationException(
                BINDING_CODE,
                "Request binding failure at endpoint "
                        + endpointSignature
                        + ", parameter `"
                        + parameterName
                        + "`: "
                        + reason,
                line,
                1,
                sourceFile.toString(),
                BINDING_FEATURE_ID,
                BINDING_GUIDANCE
        );
    }

    private static JvmCompilationException controllerDiDiagnostic(
            final Path sourceFile,
            final int line,
            final String message
    ) {
        return new JvmCompilationException(
                "TSJ-WEB-CONTROLLER",
                "Controller wiring failure: " + message,
                line,
                1,
                sourceFile.toString(),
                CONTROLLER_DI_FEATURE_ID,
                CONTROLLER_DI_GUIDANCE
        );
    }

    private TsAnnotationAttributeParser.DecoratorAttributes parseDecoratorAttributes(
            final TsDecoratorUse decorator,
            final Path sourceFile
    ) {
        if (decorator.rawArgs() == null || decorator.rawArgs().isBlank()) {
            throw new JvmCompilationException(
                    "TSJ-WEB-CONTROLLER",
                    "Decorator @" + decorator.name() + " requires arguments in TSJ-34 subset.",
                    decorator.line(),
                    1,
                    sourceFile.toString(),
                    null,
                    "Provide required decorator arguments."
            );
        }
        return attributeParser.parse(
                decorator.rawArgs(),
                sourceFile,
                decorator.line(),
                decorator.name()
        );
    }

    private static void validateFqcn(
            final String className,
            final TsDecoratorUse decorator,
            final Path sourceFile
    ) {
        if (className.matches("^[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")) {
            return;
        }
        throw new JvmCompilationException(
                "TSJ-WEB-CONTROLLER",
                "Invalid class name in @" + decorator.name() + ": " + className,
                decorator.line(),
                1,
                sourceFile.toString(),
                null,
                "Use a fully-qualified class name."
        );
    }

    private static TsDecoratorUse findDecorator(
            final List<TsDecoratorUse> decorators,
            final String name
    ) {
        for (TsDecoratorUse decorator : decorators) {
            if (name.equals(decorator.name())) {
                return decorator;
            }
        }
        return null;
    }

    private static boolean isRouteDecorator(final String decoratorName) {
        return "GetMapping".equals(decoratorName)
                || "PostMapping".equals(decoratorName)
                || "PutMapping".equals(decoratorName)
                || "DeleteMapping".equals(decoratorName)
                || "PatchMapping".equals(decoratorName);
    }

    private static Set<String> extractPathVariables(final String pathTemplate) {
        final Set<String> names = new LinkedHashSet<>();
        final Matcher matcher = PATH_VARIABLE_PATTERN.matcher(pathTemplate);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return Set.copyOf(names);
    }

    private static String httpMethodForRouteDecorator(final String routeDecoratorName) {
        return switch (routeDecoratorName) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            default -> throw new IllegalArgumentException("Unsupported route decorator: " + routeDecoratorName);
        };
    }

    private static String normalizePath(final String basePath, final String childPath) {
        final String normalizedBase = basePath == null || basePath.isBlank() ? "" : basePath;
        final String normalizedChild = childPath == null || childPath.isBlank() ? "" : childPath;
        if (normalizedBase.endsWith("/") && normalizedChild.startsWith("/")) {
            return normalizedBase.substring(0, normalizedBase.length() - 1) + normalizedChild;
        }
        if (!normalizedBase.isEmpty() && !normalizedChild.isEmpty()
                && !normalizedBase.endsWith("/") && !normalizedChild.startsWith("/")) {
            return normalizedBase + "/" + normalizedChild;
        }
        return normalizedBase + normalizedChild;
    }

    private String renderControllerSource(
            final String programClassName,
            final String generatedSimpleName,
            final ControllerModel controller
    ) {
        final String restControllerAnnotation = annotationMapping
                .mapClassDecorator("RestController")
                .orElseThrow(() -> new IllegalStateException("Missing RestController annotation mapping."));
        final String requestMappingAnnotation = annotationMapping
                .mapClassDecorator("RequestMapping")
                .orElseThrow(() -> new IllegalStateException("Missing RequestMapping annotation mapping."));
        final String exceptionHandlerAnnotation = annotationMapping
                .mapMethodDecorator("ExceptionHandler")
                .orElseThrow(() -> new IllegalStateException("Missing ExceptionHandler annotation mapping."));
        final String responseStatusAnnotation = annotationMapping
                .mapMethodDecorator("ResponseStatus")
                .orElseThrow(() -> new IllegalStateException("Missing ResponseStatus annotation mapping."));
        final StringBuilder builder = new StringBuilder();
        builder.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        builder.append("@").append(restControllerAnnotation).append("\n");
        if (!controller.basePath().isBlank()) {
            builder.append("@").append(requestMappingAnnotation).append("(\"")
                    .append(escapeJava(controller.basePath()))
                    .append("\")\n");
        }
        builder.append("public final class ").append(generatedSimpleName).append(" {\n");
        for (ConstructorDependency constructorDependency : controller.constructorDependencies()) {
            builder.append("    private final Object ").append(constructorDependency.name()).append(";\n");
        }
        if (!controller.constructorDependencies().isEmpty()) {
            builder.append("\n");
        }
        builder.append("    public ").append(generatedSimpleName).append("(");
        for (int index = 0; index < controller.constructorDependencies().size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            final ConstructorDependency dependency = controller.constructorDependencies().get(index);
            if (dependency.qualifier() != null) {
                builder.append("@org.springframework.beans.factory.annotation.Qualifier(\"")
                        .append(escapeJava(dependency.qualifier()))
                        .append("\") ");
            }
            builder.append("Object ").append(dependency.name());
        }
        builder.append(") {\n");
        for (ConstructorDependency constructorDependency : controller.constructorDependencies()) {
            builder.append("        this.")
                    .append(constructorDependency.name())
                    .append(" = ")
                    .append(constructorDependency.name())
                    .append(";\n");
        }
        builder.append("    }\n\n");

        for (RouteMethod route : controller.routes()) {
            final String mappingAnnotation = annotationMapping
                    .mapMethodDecorator(route.routeDecoratorName())
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing route decorator mapping for " + route.routeDecoratorName() + "."
                    ));
            builder.append("    @").append(mappingAnnotation).append("(\"")
                    .append(escapeJava(route.path()))
                    .append("\")\n");
            if (route.statusCode() != null) {
                builder.append("    @").append(responseStatusAnnotation).append("(")
                        .append("org.springframework.http.HttpStatus.")
                        .append(STATUS_CODE_ENUM.get(route.statusCode()))
                        .append(")\n");
            }
            builder.append("    public Object ").append(route.methodName()).append("(");
            for (int index = 0; index < route.parameters().size(); index++) {
                final RouteParameter parameter = route.parameters().get(index);
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(parameter.renderAnnotation())
                        .append(" Object ")
                        .append(parameter.javaName());
            }
            builder.append(") {\n");
            builder.append("        return ").append(renderControllerInvocationExpression(
                    programClassName,
                    controller,
                    route.methodName(),
                    route.parameterNames()
            )).append(";\n");
            builder.append("    }\n\n");
        }

        for (ErrorHandlerMethod errorHandler : controller.errorHandlers()) {
            builder.append("    @")
                    .append(exceptionHandlerAnnotation)
                    .append("(")
                    .append(renderExceptionHandlerAnnotationValue(errorHandler.exceptionClassNames()))
                    .append(")\n");
            builder.append("    @").append(responseStatusAnnotation).append("(")
                    .append("org.springframework.http.HttpStatus.")
                    .append(STATUS_CODE_ENUM.get(errorHandler.statusCode()))
                    .append(")\n");
            builder.append("    public Object ")
                    .append(errorHandler.methodName())
                    .append("(")
                    .append(errorHandler.parameterType())
                    .append(" error) {\n");
            builder.append("        return ").append(renderControllerInvocationExpression(
                    programClassName,
                    controller,
                    errorHandler.methodName(),
                    List.of("error")
            )).append(";\n");
            builder.append("    }\n\n");
        }

        builder.append("}\n");
        return builder.toString();
    }

    private String renderControllerInvocationExpression(
            final String programClassName,
            final ControllerModel controller,
            final String methodName,
            final List<String> arguments
    ) {
        if (controller.constructorDependencies().isEmpty()) {
            final StringBuilder invocation = new StringBuilder();
            invocation.append(programClassName)
                    .append(".__tsjInvokeController(\"")
                    .append(escapeJava(controller.className()))
                    .append("\", \"")
                    .append(escapeJava(methodName))
                    .append("\"");
            for (String argument : arguments) {
                invocation.append(", ").append(argument);
            }
            invocation.append(")");
            return invocation.toString();
        }
        final StringBuilder invocation = new StringBuilder();
        invocation.append(programClassName)
                .append(".__tsjInvokeClassWithInjection(\"")
                .append(escapeJava(controller.className()))
                .append("\", \"")
                .append(escapeJava(methodName))
                .append("\", ")
                .append(renderObjectArrayLiteral(constructorDependencyExpressions(controller.constructorDependencies())))
                .append(", new String[]{}, new Object[]{}, new String[]{}, new Object[]{}");
        for (String argument : arguments) {
            invocation.append(", ").append(argument);
        }
        invocation.append(")");
        return invocation.toString();
    }

    private static List<String> constructorDependencyExpressions(final List<ConstructorDependency> dependencies) {
        final List<String> values = new ArrayList<>(dependencies.size());
        for (ConstructorDependency dependency : dependencies) {
            values.add("this." + dependency.name());
        }
        return List.copyOf(values);
    }

    private static String renderObjectArrayLiteral(final List<String> values) {
        if (values.isEmpty()) {
            return "new Object[]{}";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("new Object[]{");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(values.get(index));
        }
        builder.append("}");
        return builder.toString();
    }

    private static List<String> normalizeParameterNames(
            final List<TsDecoratedParameter> parameters,
            final String fallbackPrefix
    ) {
        final List<String> normalized = new ArrayList<>();
        final Set<String> used = new LinkedHashSet<>();
        for (TsDecoratedParameter parameter : parameters) {
            String candidate = parameter.name();
            if (!candidate.matches("^[A-Za-z_$][A-Za-z0-9_$]*$")) {
                candidate = fallbackPrefix + parameter.index();
            }
            if (used.contains(candidate)) {
                candidate = fallbackPrefix + parameter.index();
            }
            used.add(candidate);
            normalized.add(candidate);
        }
        return List.copyOf(normalized);
    }

    private static String renderExceptionHandlerAnnotationValue(final List<String> exceptionClassNames) {
        if (exceptionClassNames.size() == 1) {
            return exceptionClassNames.getFirst() + ".class";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        for (int index = 0; index < exceptionClassNames.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(exceptionClassNames.get(index)).append(".class");
        }
        builder.append("}");
        return builder.toString();
    }

    private static String escapeJava(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record RouteMapping(String routeDecoratorName, String path) {
    }

    private record ErrorMapping(List<String> exceptionClassNames, int statusCode) {
    }

    private record RouteMethod(
            String methodName,
            String routeDecoratorName,
            String path,
            List<RouteParameter> parameters,
            Integer statusCode
    ) {
        private List<String> parameterNames() {
            final List<String> names = new ArrayList<>(parameters.size());
            for (RouteParameter parameter : parameters) {
                names.add(parameter.javaName());
            }
            return List.copyOf(names);
        }
    }

    private record RouteParameter(
            String javaName,
            String annotationClassName,
            String annotationValue
    ) {
        private String renderAnnotation() {
            if (annotationValue == null) {
                return "@" + annotationClassName;
            }
            return "@" + annotationClassName + "(\"" + escapeJava(annotationValue) + "\")";
        }
    }

    private record ErrorHandlerMethod(String methodName, List<String> exceptionClassNames, int statusCode) {
        private String parameterType() {
            if (exceptionClassNames.size() == 1) {
                return exceptionClassNames.getFirst();
            }
            return "java.lang.Throwable";
        }
    }

    private record ConstructorDependency(String name, String qualifier) {
    }

    private record ControllerModel(
            String className,
            String basePath,
            int line,
            List<ConstructorDependency> constructorDependencies,
            List<RouteMethod> routes,
            List<ErrorHandlerMethod> errorHandlers
    ) {
    }
}
