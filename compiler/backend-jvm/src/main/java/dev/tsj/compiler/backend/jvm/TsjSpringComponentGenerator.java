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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generates Spring stereotype component adapters from TS-authored classes.
 */
public final class TsjSpringComponentGenerator {
    private static final String CODE = "TSJ-SPRING-COMPONENT";
    private static final String AOP_CODE = "TSJ-SPRING-AOP";
    private static final String FEATURE_ID = "TSJ33A-TS-COMPONENTS";
    private static final String DI_FEATURE_ID = "TSJ33B-DI-SURFACE";
    private static final String LIFECYCLE_FEATURE_ID = "TSJ33C-LIFECYCLE";
    private static final String DI_MODE_FEATURE_ID = "TSJ33D-INJECTION-MODES";
    private static final String AOP_FEATURE_ID = "TSJ35-AOP-PROXY";
    private static final String AOP_CLASS_PROXY_FEATURE_ID = "TSJ35A-CLASS-PROXY";
    private static final String GUIDANCE =
            "Use one supported stereotype decorator per class: @Component, @Service, @Repository, @Controller, @Configuration.";
    private static final String DI_GUIDANCE =
            "Use @Bean methods only inside @Configuration classes in TSJ-33b subset.";
    private static final String LIFECYCLE_GUIDANCE =
            "Use zero-argument @PostConstruct/@PreDestroy methods in TSJ-33c subset.";
    private static final String DI_MODE_GUIDANCE =
            "Use supported TSJ-33d subset shapes: @Autowired fields, single-parameter @Autowired setters, and optional @Qualifier/@Primary.";
    private static final String AOP_GUIDANCE =
            "Use @Transactional on class/method declarations (not constructors); TSJ-35 subset emits interface-based proxies.";
    private static final String AOP_CLASS_PROXY_GUIDANCE =
            "Use consistent @Transactional proxyTargetClass hints; class-proxy strategy does not support final proxy targets.";
    private static final String GENERATED_PACKAGE = "dev.tsj.generated.spring";
    private static final Set<String> STEREOTYPE_DECORATORS = Set.of(
            "Component",
            "Service",
            "Repository",
            "Controller",
            "Configuration"
    );
    private static final Set<String> SUPPORTED_INJECTION_DECORATORS = Set.of(
            "Autowired",
            "Qualifier"
    );

    private final TsDecoratorModelExtractor decoratorModelExtractor;
    private final TsDecoratorAnnotationMapping annotationMapping;

    public TsjSpringComponentGenerator() {
        this(new TsDecoratorModelExtractor(), new TsDecoratorAnnotationMapping());
    }

    TsjSpringComponentGenerator(
            final TsDecoratorModelExtractor decoratorModelExtractor,
            final TsDecoratorAnnotationMapping annotationMapping
    ) {
        this.decoratorModelExtractor = Objects.requireNonNull(decoratorModelExtractor, "decoratorModelExtractor");
        this.annotationMapping = Objects.requireNonNull(annotationMapping, "annotationMapping");
    }

    public TsjSpringComponentArtifact generate(
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
                    CODE,
                    "TS component source entry file not found: " + normalizedEntry
            );
        }

        final TsDecoratorModel decoratorModel = decoratorModelExtractor.extract(normalizedEntry);
        final List<ComponentModel> components = collectComponents(decoratorModel);
        if (components.isEmpty()) {
            return new TsjSpringComponentArtifact(normalizedOutput, List.of(), List.of());
        }

        final List<Path> sourceFiles = new ArrayList<>();
        final List<String> componentClassNames = new ArrayList<>();
        final Path generatedDir = normalizedOutput.resolve(GENERATED_PACKAGE.replace('.', '/'));
        try {
            Files.createDirectories(generatedDir);
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    CODE,
                    "Failed to create TS component output directory: " + ioException.getMessage(),
                    null,
                    null,
                    normalizedOutput.toString(),
                    FEATURE_ID,
                    GUIDANCE,
                    ioException
            );
        }

        for (ComponentModel component : components) {
            final String generatedSimpleName = component.className() + "TsjComponent";
            final String proxyInterfaceName = component.proxyInterface()
                    ? generatedSimpleName + "Api"
                    : null;
            if (proxyInterfaceName != null) {
                final Path apiSourcePath = generatedDir.resolve(proxyInterfaceName + ".java");
                final String apiSource = renderProxyInterfaceSource(proxyInterfaceName, component);
                try {
                    Files.writeString(apiSourcePath, apiSource, UTF_8);
                } catch (final IOException ioException) {
                    throw new JvmCompilationException(
                            CODE,
                            "Failed to write TS component proxy interface source: " + ioException.getMessage(),
                            null,
                            null,
                            apiSourcePath.toString(),
                            AOP_FEATURE_ID,
                            AOP_GUIDANCE,
                            ioException
                    );
                }
                sourceFiles.add(apiSourcePath);
            }

            final Path sourcePath = generatedDir.resolve(generatedSimpleName + ".java");
            final String source = renderComponentSource(programClassName, generatedSimpleName, component, proxyInterfaceName);
            try {
                Files.writeString(sourcePath, source, UTF_8);
            } catch (final IOException ioException) {
                throw new JvmCompilationException(
                        CODE,
                        "Failed to write TS component adapter source: " + ioException.getMessage(),
                        null,
                        null,
                        sourcePath.toString(),
                        FEATURE_ID,
                        GUIDANCE,
                        ioException
                );
            }
            sourceFiles.add(sourcePath);
            componentClassNames.add(component.className());
        }

        return new TsjSpringComponentArtifact(
                normalizedOutput,
                List.copyOf(sourceFiles),
                List.copyOf(componentClassNames)
        );
    }

    private List<ComponentModel> collectComponents(final TsDecoratorModel decoratorModel) {
        final Map<String, ComponentModel> componentsByClass = new LinkedHashMap<>();
        for (TsDecoratedClass decoratedClass : decoratorModel.classes()) {
            final List<String> stereotypes = new ArrayList<>();
            for (TsDecoratorUse decorator : decoratedClass.decorators()) {
                if (STEREOTYPE_DECORATORS.contains(decorator.name())) {
                    stereotypes.add(decorator.name());
                }
            }
            if (stereotypes.isEmpty()) {
                continue;
            }
            final Set<String> unique = new LinkedHashSet<>(stereotypes);
            if (unique.size() > 1) {
                throw new JvmCompilationException(
                        CODE,
                        "TSJ-33a supports one stereotype decorator per class, found: " + String.join(", ", unique) + ".",
                        decoratedClass.line(),
                        1,
                        decoratedClass.sourceFile().toString(),
                        FEATURE_ID,
                        GUIDANCE
                );
            }
            final String stereotype = stereotypes.getFirst();
            final boolean configurationClass = "Configuration".equals(stereotype);
            final boolean classTransactional = hasDecorator(decoratedClass.decorators(), "Transactional");
            final boolean classPrimary = hasDecorator(decoratedClass.decorators(), "Primary");
            final String classQualifier = parseQualifierFromDecorators(
                    decoratedClass.decorators(),
                    decoratedClass.sourceFile(),
                    decoratedClass.line(),
                    "class " + decoratedClass.className(),
                    false
            );
            ProxyStrategy explicitProxyStrategy = parseTransactionalProxyHint(
                    decoratedClass.decorators(),
                    decoratedClass.sourceFile(),
                    decoratedClass.line(),
                    "class " + decoratedClass.className()
            );
            final String annotationType = annotationMapping.mapClassDecorator(stereotype).orElseThrow(() ->
                    new JvmCompilationException(
                            CODE,
                            "Missing JVM mapping for stereotype decorator @" + stereotype + ".",
                            decoratedClass.line(),
                            1,
                            decoratedClass.sourceFile().toString(),
                            FEATURE_ID,
                            GUIDANCE
                    )
            );

            final List<ComponentFieldInjection> fieldInjections = new ArrayList<>();
            for (TsDecoratedField field : decoratedClass.fields()) {
                for (TsDecoratorUse decorator : field.decorators()) {
                    if (!SUPPORTED_INJECTION_DECORATORS.contains(decorator.name())) {
                        throw unsupportedInjectionShape(
                                decoratedClass.sourceFile(),
                                field.line(),
                                "Unsupported field injection decorator @" + decorator.name() + " on `" + field.fieldName() + "`."
                        );
                    }
                }
                if (!hasDecorator(field.decorators(), "Autowired")) {
                    throw unsupportedInjectionShape(
                            decoratedClass.sourceFile(),
                            field.line(),
                            "Decorated field `" + field.fieldName() + "` must include @Autowired in TSJ-33d subset."
                    );
                }
                final String qualifier = parseQualifierFromDecorators(
                        field.decorators(),
                        decoratedClass.sourceFile(),
                        field.line(),
                        "field " + field.fieldName(),
                        true
                );
                fieldInjections.add(new ComponentFieldInjection(field.fieldName(), qualifier));
            }

            TsDecoratedMethod constructor = null;
            final List<ComponentMethod> methods = new ArrayList<>();
            final List<ComponentSetterInjection> setterInjections = new ArrayList<>();
            for (TsDecoratedMethod method : decoratedClass.methods()) {
                final boolean beanMethod = hasDecorator(method.decorators(), "Bean");
                final boolean postConstruct = hasDecorator(method.decorators(), "PostConstruct");
                final boolean preDestroy = hasDecorator(method.decorators(), "PreDestroy");
                final boolean transactional = hasDecorator(method.decorators(), "Transactional");
                final boolean autowired = hasDecorator(method.decorators(), "Autowired");
                final boolean methodQualifier = hasDecorator(method.decorators(), "Qualifier");
                final ProxyStrategy methodProxyHint = parseTransactionalProxyHint(
                        method.decorators(),
                        decoratedClass.sourceFile(),
                        method.line(),
                        decoratedClass.className() + "." + method.methodName()
                );
                if (methodProxyHint != null) {
                    if (explicitProxyStrategy != null && explicitProxyStrategy != methodProxyHint) {
                        throw classProxyDiagnostic(
                                decoratedClass.sourceFile(),
                                method.line(),
                                "Conflicting @Transactional proxyTargetClass hints in component `"
                                        + decoratedClass.className() + "`."
                        );
                    }
                    explicitProxyStrategy = methodProxyHint;
                }
                if (method.constructor()) {
                    if (beanMethod) {
                        throw new JvmCompilationException(
                                CODE,
                                "@Bean cannot target constructors in TSJ-33b subset.",
                                method.line(),
                                1,
                                decoratedClass.sourceFile().toString(),
                                DI_FEATURE_ID,
                                DI_GUIDANCE
                        );
                    }
                    if (postConstruct || preDestroy) {
                        throw new JvmCompilationException(
                                CODE,
                                "Lifecycle decorators cannot target constructors in TSJ-33c subset.",
                                method.line(),
                                1,
                                decoratedClass.sourceFile().toString(),
                                LIFECYCLE_FEATURE_ID,
                                LIFECYCLE_GUIDANCE
                        );
                    }
                    if (transactional) {
                        throw new JvmCompilationException(
                                AOP_CODE,
                                "TSJ-35 subset does not support @Transactional on constructors.",
                                method.line(),
                                1,
                                decoratedClass.sourceFile().toString(),
                                AOP_FEATURE_ID,
                                AOP_GUIDANCE
                        );
                    }
                    if (autowired || methodQualifier) {
                        throw unsupportedInjectionShape(
                                decoratedClass.sourceFile(),
                                method.line(),
                                "Constructors use implicit injection in TSJ-33d subset; method-level @Autowired/@Qualifier on constructors is unsupported."
                        );
                    }
                    constructor = method;
                    continue;
                }

                if (autowired) {
                    if (method.parameters().size() != 1) {
                        throw unsupportedInjectionShape(
                                decoratedClass.sourceFile(),
                                method.line(),
                                "@Autowired setter `" + method.methodName()
                                        + "` must declare exactly one parameter in TSJ-33d subset."
                        );
                    }
                    if (beanMethod || postConstruct || preDestroy || transactional) {
                        throw unsupportedInjectionShape(
                                decoratedClass.sourceFile(),
                                method.line(),
                                "@Autowired setter `" + method.methodName()
                                        + "` cannot combine lifecycle/bean/transactional decorators in TSJ-33d subset."
                        );
                    }
                    final String methodLevelQualifier = parseQualifierFromDecorators(
                            method.decorators(),
                            decoratedClass.sourceFile(),
                            method.line(),
                            "setter method " + method.methodName(),
                            true
                    );
                    final TsDecoratedParameter parameter = method.parameters().getFirst();
                    final String parameterQualifier = parseQualifierFromParameter(
                            parameter,
                            decoratedClass.sourceFile(),
                            method.line(),
                            method.methodName()
                    );
                    final String qualifier;
                    if (methodLevelQualifier != null && parameterQualifier != null
                            && !methodLevelQualifier.equals(parameterQualifier)) {
                        throw unsupportedInjectionShape(
                                decoratedClass.sourceFile(),
                                method.line(),
                                "Conflicting @Qualifier values on @Autowired setter `" + method.methodName() + "`."
                        );
                    }
                    qualifier = methodLevelQualifier != null ? methodLevelQualifier : parameterQualifier;
                    final List<String> parameterNames = normalizeParameterNames(method.parameters(), "dep");
                    setterInjections.add(new ComponentSetterInjection(
                            method.methodName(),
                            parameterNames.getFirst(),
                            qualifier,
                            "__tsjSetterDep_" + sanitizeJavaIdentifier(method.methodName())
                    ));
                    continue;
                }

                if (methodQualifier) {
                    throw unsupportedInjectionShape(
                            decoratedClass.sourceFile(),
                            method.line(),
                            "@Qualifier is supported only with @Autowired setters in TSJ-33d subset."
                    );
                }
                if (beanMethod && !configurationClass) {
                    throw new JvmCompilationException(
                            CODE,
                            "@Bean methods are only supported inside @Configuration classes in TSJ-33b subset.",
                            method.line(),
                            1,
                            decoratedClass.sourceFile().toString(),
                            DI_FEATURE_ID,
                            DI_GUIDANCE
                    );
                }
                if ((postConstruct || preDestroy) && !method.parameters().isEmpty()) {
                    throw new JvmCompilationException(
                            CODE,
                            "Lifecycle method @" + (postConstruct ? "PostConstruct" : "PreDestroy")
                                    + " must declare zero parameters in TSJ-33c subset.",
                            method.line(),
                            1,
                            decoratedClass.sourceFile().toString(),
                            LIFECYCLE_FEATURE_ID,
                            LIFECYCLE_GUIDANCE
                    );
                }
                methods.add(new ComponentMethod(
                        method.methodName(),
                        normalizeParameterNames(method.parameters(), "arg"),
                        beanMethod,
                        postConstruct,
                        preDestroy,
                        transactional
                ));
            }
            boolean hasTransactionalMethods = classTransactional;
            if (!hasTransactionalMethods) {
                for (ComponentMethod method : methods) {
                    if (method.transactional()) {
                        hasTransactionalMethods = true;
                        break;
                    }
                }
            }
            final ProxyStrategy proxyStrategy =
                    explicitProxyStrategy == null
                            ? ProxyStrategy.JDK
                            : explicitProxyStrategy;
            final boolean requiresProxyInterface = hasTransactionalMethods && proxyStrategy == ProxyStrategy.JDK;

            final List<ConstructorDependency> constructorDependencies =
                    constructor == null
                            ? List.of()
                            : normalizeConstructorDependencies(
                                    constructor,
                                    decoratedClass.sourceFile(),
                                    decoratedClass.className()
                            );

            final ComponentModel component = new ComponentModel(
                    decoratedClass.className(),
                    annotationType,
                    constructorDependencies,
                    List.copyOf(fieldInjections),
                    List.copyOf(setterInjections),
                    List.copyOf(methods),
                    classTransactional,
                    classPrimary,
                    classQualifier,
                    proxyStrategy,
                    hasTransactionalMethods,
                    requiresProxyInterface
            );
            final ComponentModel existing = componentsByClass.putIfAbsent(decoratedClass.className(), component);
            if (existing != null) {
                throw new JvmCompilationException(
                        CODE,
                        "Duplicate TS component class name across sources: " + decoratedClass.className(),
                        decoratedClass.line(),
                        1,
                        decoratedClass.sourceFile().toString(),
                        FEATURE_ID,
                        GUIDANCE
                );
            }
        }
        return List.copyOf(componentsByClass.values());
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
            final String qualifier = parseQualifierFromParameter(
                    parameter,
                    sourceFile,
                    constructor.line(),
                    className + " constructor"
            );
            dependencies.add(new ConstructorDependency(names.get(index), qualifier));
        }
        return List.copyOf(dependencies);
    }

    private String parseQualifierFromParameter(
            final TsDecoratedParameter parameter,
            final Path sourceFile,
            final int line,
            final String owner
    ) {
        TsDecoratorUse qualifierDecorator = null;
        for (TsDecoratorUse decorator : parameter.decorators()) {
            if (!"Qualifier".equals(decorator.name())) {
                throw unsupportedInjectionShape(
                        sourceFile,
                        line,
                        "Unsupported parameter decorator @" + decorator.name()
                                + " on `" + owner + "` in TSJ-33d subset."
                );
            }
            if (qualifierDecorator != null) {
                throw unsupportedInjectionShape(
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

    private String parseQualifierFromDecorators(
            final List<TsDecoratorUse> decorators,
            final Path sourceFile,
            final int line,
            final String owner,
            final boolean strictInjectionSubset
    ) {
        TsDecoratorUse qualifierDecorator = null;
        for (TsDecoratorUse decorator : decorators) {
            if ("Qualifier".equals(decorator.name())) {
                if (qualifierDecorator != null) {
                    throw unsupportedInjectionShape(
                            sourceFile,
                            line,
                            "Multiple @Qualifier decorators are unsupported on `" + owner + "`."
                    );
                }
                qualifierDecorator = decorator;
                continue;
            }
            if (strictInjectionSubset && !SUPPORTED_INJECTION_DECORATORS.contains(decorator.name())) {
                throw unsupportedInjectionShape(
                        sourceFile,
                        line,
                        "Unsupported decorator @" + decorator.name() + " on `" + owner + "` in TSJ-33d subset."
                );
            }
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
        final String raw = decorator.rawArgs() == null ? "" : decorator.rawArgs().trim();
        if (raw.isEmpty()) {
            throw unsupportedInjectionShape(
                    sourceFile,
                    line,
                    "@Qualifier on `" + owner + "` requires a non-empty value."
            );
        }
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            if (raw.length() <= 2) {
                throw unsupportedInjectionShape(
                        sourceFile,
                        line,
                        "@Qualifier on `" + owner + "` requires a non-empty value."
                );
            }
            return raw.substring(1, raw.length() - 1);
        }
        if (!raw.matches("^[A-Za-z_$][A-Za-z0-9_$.-]*$")) {
            throw unsupportedInjectionShape(
                    sourceFile,
                    line,
                    "Unsupported @Qualifier argument for `" + owner + "`: " + raw
            );
        }
        return raw;
    }

    private ProxyStrategy parseTransactionalProxyHint(
            final List<TsDecoratorUse> decorators,
            final Path sourceFile,
            final int line,
            final String owner
    ) {
        ProxyStrategy hint = null;
        for (TsDecoratorUse decorator : decorators) {
            if (!"Transactional".equals(decorator.name())) {
                continue;
            }
            final String rawArgs = decorator.rawArgs() == null ? "" : decorator.rawArgs();
            final String normalized = rawArgs.replace(" ", "").replace("\t", "");
            if (!normalized.toLowerCase(java.util.Locale.ROOT).contains("proxytargetclass")) {
                continue;
            }
            final ProxyStrategy currentHint;
            if (normalized.toLowerCase(java.util.Locale.ROOT).matches(".*proxytargetclass[:=]true.*")) {
                currentHint = ProxyStrategy.CLASS;
            } else if (normalized.toLowerCase(java.util.Locale.ROOT).matches(".*proxytargetclass[:=]false.*")) {
                currentHint = ProxyStrategy.JDK;
            } else {
                throw classProxyDiagnostic(
                        sourceFile,
                        line,
                        "Invalid @Transactional proxyTargetClass hint on `" + owner + "`: " + rawArgs
                );
            }
            if (hint != null && hint != currentHint) {
                throw classProxyDiagnostic(
                        sourceFile,
                        line,
                        "Conflicting @Transactional proxyTargetClass hints on `" + owner + "`."
                );
            }
            hint = currentHint;
        }
        return hint;
    }

    private JvmCompilationException classProxyDiagnostic(
            final Path sourceFile,
            final int line,
            final String message
    ) {
        return new JvmCompilationException(
                AOP_CODE,
                message,
                line,
                1,
                sourceFile.toString(),
                AOP_CLASS_PROXY_FEATURE_ID,
                AOP_CLASS_PROXY_GUIDANCE
        );
    }

    private JvmCompilationException unsupportedInjectionShape(
            final Path sourceFile,
            final int line,
            final String message
    ) {
        return new JvmCompilationException(
                CODE,
                message,
                line,
                1,
                sourceFile.toString(),
                DI_MODE_FEATURE_ID,
                DI_MODE_GUIDANCE
        );
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

    private String renderComponentSource(
            final String programClassName,
            final String generatedSimpleName,
            final ComponentModel component,
            final String proxyInterfaceName
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        builder.append("@").append(component.annotationType()).append("\n");
        if (component.classQualifier() != null) {
            builder.append("@org.springframework.beans.factory.annotation.Qualifier(\"")
                    .append(escapeJava(component.classQualifier()))
                    .append("\")\n");
        }
        if (component.classPrimary()) {
            builder.append("@org.springframework.context.annotation.Primary\n");
        }
        if (component.classTransactional()) {
            builder.append("@org.springframework.transaction.annotation.Transactional\n");
        }
        builder.append("public class ").append(generatedSimpleName);
        if (proxyInterfaceName != null) {
            builder.append(" implements ").append(proxyInterfaceName);
        }
        builder.append(" {\n");
        final boolean classProxyStrategy = component.proxyStrategy() == ProxyStrategy.CLASS;

        for (ConstructorDependency constructorDependency : component.constructorDependencies()) {
            builder.append("    private final Object ").append(constructorDependency.name()).append(";\n");
        }
        for (ComponentFieldInjection fieldInjection : component.fieldInjections()) {
            builder.append("    @org.springframework.beans.factory.annotation.Autowired\n");
            if (fieldInjection.qualifier() != null) {
                builder.append("    @org.springframework.beans.factory.annotation.Qualifier(\"")
                        .append(escapeJava(fieldInjection.qualifier()))
                        .append("\")\n");
            }
            builder.append("    private Object ").append(fieldInjection.fieldName()).append(";\n");
        }
        for (ComponentSetterInjection setterInjection : component.setterInjections()) {
            builder.append("    private Object ").append(setterInjection.backingFieldName()).append(";\n");
        }
        if (classProxyStrategy && component.transactionalCandidate()) {
            builder.append("    @org.springframework.beans.factory.annotation.Autowired(required = false)\n");
            builder.append("    private org.springframework.transaction.PlatformTransactionManager __tsjTxManager;\n");
        }
        if (!component.constructorDependencies().isEmpty()
                || !component.fieldInjections().isEmpty()
                || !component.setterInjections().isEmpty()
                || (classProxyStrategy && component.transactionalCandidate())) {
            builder.append("\n");
        }

        builder.append("    public ").append(generatedSimpleName).append("(");
        for (int index = 0; index < component.constructorDependencies().size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            final ConstructorDependency dependency = component.constructorDependencies().get(index);
            if (dependency.qualifier() != null) {
                builder.append("@org.springframework.beans.factory.annotation.Qualifier(\"")
                        .append(escapeJava(dependency.qualifier()))
                        .append("\") ");
            }
            builder.append("Object ").append(dependency.name());
        }
        builder.append(") {\n");
        for (ConstructorDependency constructorDependency : component.constructorDependencies()) {
            builder.append("        this.")
                    .append(constructorDependency.name())
                    .append(" = ")
                    .append(constructorDependency.name())
                    .append(";\n");
        }
        builder.append("    }\n\n");

        if (component.transactionalCandidate()) {
            builder.append("    public String __tsjProxyStrategy() {\n");
            builder.append("        return \"")
                    .append(classProxyStrategy ? "class" : "jdk")
                    .append("\";\n");
            builder.append("    }\n\n");
        }

        for (ComponentSetterInjection setterInjection : component.setterInjections()) {
            builder.append("    @org.springframework.beans.factory.annotation.Autowired\n");
            builder.append("    public void ").append(setterInjection.methodName()).append("(");
            if (setterInjection.qualifier() != null) {
                builder.append("@org.springframework.beans.factory.annotation.Qualifier(\"")
                        .append(escapeJava(setterInjection.qualifier()))
                        .append("\") ");
            }
            builder.append("Object ").append(setterInjection.parameterName()).append(") {\n");
            builder.append("        this.")
                    .append(setterInjection.backingFieldName())
                    .append(" = ")
                    .append(setterInjection.parameterName())
                    .append(";\n");
            builder.append("    }\n\n");
        }

        for (ComponentMethod method : component.methods()) {
            if (method.beanMethod()) {
                builder.append("    @org.springframework.context.annotation.Bean\n");
            }
            if (method.postConstruct()) {
                builder.append("    @jakarta.annotation.PostConstruct\n");
            }
            if (method.preDestroy()) {
                builder.append("    @jakarta.annotation.PreDestroy\n");
            }
            if (method.transactional()) {
                builder.append("    @org.springframework.transaction.annotation.Transactional\n");
            }
            builder.append("    public Object ").append(method.name()).append("(");
            for (int index = 0; index < method.parameters().size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append("Object ").append(method.parameters().get(index));
            }
            builder.append(") {\n");
            final String invocationExpression = renderTsjInvocationExpression(programClassName, component, method);
            final boolean classProxyTransactionalInvocation = classProxyStrategy
                    && (component.classTransactional() || method.transactional());
            if (classProxyTransactionalInvocation) {
                builder.append("        if (this.__tsjTxManager == null) {\n");
                builder.append("            throw new IllegalStateException(")
                        .append("\"TSJ-SPRING-AOP [featureId=")
                        .append(AOP_CLASS_PROXY_FEATURE_ID)
                        .append("] Missing PlatformTransactionManager for class-proxy strategy.\");\n");
                builder.append("        }\n");
                builder.append("        final org.springframework.transaction.TransactionStatus __tsjStatus = ")
                        .append("this.__tsjTxManager.begin(getClass().getName(), \"")
                        .append(escapeJava(method.name()))
                        .append("\");\n");
                builder.append("        try {\n");
                builder.append("            final Object __tsjResult = ").append(invocationExpression).append(";\n");
                builder.append("            this.__tsjTxManager.commit(__tsjStatus);\n");
                builder.append("            return __tsjResult;\n");
                builder.append("        } catch (java.lang.RuntimeException __tsjRuntimeException) {\n");
                builder.append("            this.__tsjTxManager.rollback(__tsjStatus);\n");
                builder.append("            throw __tsjRuntimeException;\n");
                builder.append("        } catch (java.lang.Error __tsjError) {\n");
                builder.append("            this.__tsjTxManager.rollback(__tsjStatus);\n");
                builder.append("            throw __tsjError;\n");
                builder.append("        } catch (java.lang.Throwable __tsjThrowable) {\n");
                builder.append("            this.__tsjTxManager.rollback(__tsjStatus);\n");
                builder.append("            throw new RuntimeException(__tsjThrowable);\n");
                builder.append("        }\n");
            } else {
                builder.append("        return ").append(invocationExpression).append(";\n");
            }
            builder.append("    }\n\n");
        }

        builder.append("}\n");
        return builder.toString();
    }

    private String renderTsjInvocationExpression(
            final String programClassName,
            final ComponentModel component,
            final ComponentMethod method
    ) {
        final StringBuilder invocation = new StringBuilder();
        invocation.append(programClassName)
                .append(".__tsjInvokeClassWithInjection(\"")
                .append(escapeJava(component.className()))
                .append("\", \"")
                .append(escapeJava(method.name()))
                .append("\", ")
                .append(renderObjectArrayLiteral(constructorDependencyNames(component.constructorDependencies())))
                .append(", ")
                .append(renderStringArrayLiteral(fieldNames(component.fieldInjections())))
                .append(", ")
                .append(renderObjectArrayLiteral(fieldValueExpressions(component.fieldInjections())))
                .append(", ")
                .append(renderStringArrayLiteral(setterMethodNames(component.setterInjections())))
                .append(", ")
                .append(renderObjectArrayLiteral(setterBackingExpressions(component.setterInjections())));
        for (String parameter : method.parameters()) {
            invocation.append(", ").append(parameter);
        }
        invocation.append(")");
        return invocation.toString();
    }

    private String renderProxyInterfaceSource(
            final String interfaceSimpleName,
            final ComponentModel component
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        builder.append("public interface ").append(interfaceSimpleName).append(" {\n");
        for (ComponentMethod method : component.methods()) {
            builder.append("    Object ").append(method.name()).append("(");
            for (int index = 0; index < method.parameters().size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append("Object ").append(method.parameters().get(index));
            }
            builder.append(");\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private static List<String> constructorDependencyNames(final List<ConstructorDependency> dependencies) {
        final List<String> names = new ArrayList<>(dependencies.size());
        for (ConstructorDependency dependency : dependencies) {
            names.add(dependency.name());
        }
        return List.copyOf(names);
    }

    private static List<String> fieldNames(final List<ComponentFieldInjection> fieldInjections) {
        final List<String> names = new ArrayList<>(fieldInjections.size());
        for (ComponentFieldInjection fieldInjection : fieldInjections) {
            names.add(fieldInjection.fieldName());
        }
        return List.copyOf(names);
    }

    private static List<String> fieldValueExpressions(final List<ComponentFieldInjection> fieldInjections) {
        final List<String> values = new ArrayList<>(fieldInjections.size());
        for (ComponentFieldInjection fieldInjection : fieldInjections) {
            values.add(fieldInjection.fieldName());
        }
        return List.copyOf(values);
    }

    private static List<String> setterMethodNames(final List<ComponentSetterInjection> setterInjections) {
        final List<String> names = new ArrayList<>(setterInjections.size());
        for (ComponentSetterInjection setterInjection : setterInjections) {
            names.add(setterInjection.methodName());
        }
        return List.copyOf(names);
    }

    private static List<String> setterBackingExpressions(final List<ComponentSetterInjection> setterInjections) {
        final List<String> values = new ArrayList<>(setterInjections.size());
        for (ComponentSetterInjection setterInjection : setterInjections) {
            values.add("this." + setterInjection.backingFieldName());
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

    private static String renderStringArrayLiteral(final List<String> values) {
        if (values.isEmpty()) {
            return "new String[]{}";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("new String[]{");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append("\"").append(escapeJava(values.get(index))).append("\"");
        }
        builder.append("}");
        return builder.toString();
    }

    private static String sanitizeJavaIdentifier(final String raw) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < raw.length(); index++) {
            final char current = raw.charAt(index);
            if (Character.isLetterOrDigit(current) || current == '_') {
                builder.append(current);
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

    private static String escapeJava(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean hasDecorator(
            final List<TsDecoratorUse> decorators,
            final String decoratorName
    ) {
        for (TsDecoratorUse decorator : decorators) {
            if (decoratorName.equals(decorator.name())) {
                return true;
            }
        }
        return false;
    }

    private record ConstructorDependency(
            String name,
            String qualifier
    ) {
    }

    private record ComponentFieldInjection(
            String fieldName,
            String qualifier
    ) {
    }

    private record ComponentSetterInjection(
            String methodName,
            String parameterName,
            String qualifier,
            String backingFieldName
    ) {
    }

    private record ComponentMethod(
            String name,
            List<String> parameters,
            boolean beanMethod,
            boolean postConstruct,
            boolean preDestroy,
            boolean transactional
    ) {
    }

    private record ComponentModel(
            String className,
            String annotationType,
            List<ConstructorDependency> constructorDependencies,
            List<ComponentFieldInjection> fieldInjections,
            List<ComponentSetterInjection> setterInjections,
            List<ComponentMethod> methods,
            boolean classTransactional,
            boolean classPrimary,
            String classQualifier,
            ProxyStrategy proxyStrategy,
            boolean transactionalCandidate,
            boolean proxyInterface
    ) {
    }

    private enum ProxyStrategy {
        JDK,
        CLASS
    }
}
