package dev.tsj.compiler.backend.jvm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generates opt-in Java interop bridge stubs from allowlisted targets.
 */
public final class InteropBridgeGenerator {
    private static final String CODE_INPUT = "TSJ-INTEROP-INPUT";
    private static final String CODE_INVALID = "TSJ-INTEROP-INVALID";
    private static final String CODE_DISALLOWED = "TSJ-INTEROP-DISALLOWED";
    private static final String FEATURE_ALLOWLIST = "TSJ19-ALLOWLIST";
    private static final String GUIDANCE_ALLOWLIST =
            "Only allowlisted Java targets can be bridged. Add target to allowlist or remove it from targets.";
    private static final String CODE_ANNOTATION = "TSJ-INTEROP-ANNOTATION";
    private static final String GUIDANCE_ANNOTATION =
            "Use runtime-visible annotation types and fully-qualified class names.";
    private static final String GENERATED_PACKAGE = "dev.tsj.generated.interop";
    private static final String BINDING_ANNOTATION_PREFIX = "bindingAnnotations.";
    private static final String BINDING_ARGS_PREFIX = "bindingArgs.";
    private static final String CLASS_ANNOTATIONS_KEY = "classAnnotations";
    private static final String TYPE_NAME_PATTERN = "^[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*$";
    private static final String BINDING_CONSTRUCTOR = "$new";
    private static final String BINDING_INSTANCE_PREFIX = "$instance$";
    private static final String BINDING_INSTANCE_GET_PREFIX = "$instance$get$";
    private static final String BINDING_INSTANCE_SET_PREFIX = "$instance$set$";
    private static final String BINDING_STATIC_GET_PREFIX = "$static$get$";
    private static final String BINDING_STATIC_SET_PREFIX = "$static$set$";

    public InteropBridgeArtifact generate(final Path specFile, final Path outputDir) {
        final Path normalizedSpec = normalizeSpec(specFile);
        final Path normalizedOutput = Objects.requireNonNull(outputDir, "outputDir").toAbsolutePath().normalize();

        final Properties properties = readSpecProperties(normalizedSpec);
        validateRetiredInteropProperties(properties, normalizedSpec);
        final Set<InteropTarget> allowlist = parseTargets(properties.getProperty("allowlist"), normalizedSpec);
        final Set<InteropTarget> requested = parseTargets(properties.getProperty("targets"), normalizedSpec);
        final List<String> classAnnotations = parseAnnotations(properties.getProperty(CLASS_ANNOTATIONS_KEY), normalizedSpec);
        final Map<String, List<String>> bindingAnnotations = parseBindingAnnotations(properties, normalizedSpec);
        final Map<String, List<JavaOverloadResolver.Argument>> bindingArgsByBinding =
                parseBindingArgs(properties, normalizedSpec);
        for (String annotationType : classAnnotations) {
            validateAnnotationType(annotationType, normalizedSpec);
        }
        for (Map.Entry<String, List<String>> entry : bindingAnnotations.entrySet()) {
            if (entry.getKey().isBlank()) {
                throw annotationError(normalizedSpec, "Binding annotation key must not be empty.");
            }
            for (String annotationType : entry.getValue()) {
                validateAnnotationType(annotationType, normalizedSpec);
            }
        }
        if (requested.isEmpty()) {
            return new InteropBridgeArtifact(normalizedOutput, List.of(), List.of(), List.of(), List.of());
        }
        for (InteropTarget target : requested) {
            if (!allowlist.contains(target)) {
                throw new JvmCompilationException(
                        CODE_DISALLOWED,
                        "Interop target is not allowlisted: " + target.displayName() + ".",
                        null,
                        null,
                        normalizedSpec.toString(),
                        FEATURE_ALLOWLIST,
                        GUIDANCE_ALLOWLIST
                );
            }
        }
        final Map<String, Set<String>> targetsByClass = new LinkedHashMap<>();
        final List<InteropBridgeArtifact.SelectedTargetIdentity> selectedTargets = new ArrayList<>();
        final List<InteropBridgeArtifact.UnresolvedTarget> unresolvedTargets = new ArrayList<>();
        final List<String> emittedTargets = new ArrayList<>();
        final JavaOverloadResolver overloadResolver = new JavaOverloadResolver();
        final Map<Class<?>, Map<String, List<JavaNullabilityAnalyzer.NullabilityState>>> nullabilityByClass =
                new LinkedHashMap<>();
        for (InteropTarget target : requested) {
            final Class<?> targetClass = resolveTargetClass(target, normalizedSpec);
            validateBindingTarget(targetClass, target.bindingName(), normalizedSpec, target.displayName());
            final Map<String, List<JavaNullabilityAnalyzer.NullabilityState>> methodNullabilityByKey =
                    nullabilityByClass.computeIfAbsent(
                            targetClass,
                            InteropBridgeGenerator::resolveParameterNullabilityByMethodKey
                    );
            final SelectedIdentityResolution selectedIdentityResolution = resolveSelectedIdentity(
                    targetClass,
                    target,
                    bindingArgsByBinding.get(target.bindingName()),
                    methodNullabilityByKey,
                    overloadResolver,
                    normalizedSpec
            );
            if (selectedIdentityResolution.selectedIdentity() != null) {
                selectedTargets.add(selectedIdentityResolution.selectedIdentity());
            } else if (selectedIdentityResolution.unresolvedReason() != null) {
                unresolvedTargets.add(new InteropBridgeArtifact.UnresolvedTarget(
                        target.className(),
                        target.bindingName(),
                        selectedIdentityResolution.unresolvedReason()
                ));
            }
            targetsByClass.computeIfAbsent(target.className(), ignored -> new TreeSet<>()).add(target.bindingName());
            emittedTargets.add(target.displayName());
        }
        for (String bindingName : bindingAnnotations.keySet()) {
            boolean present = false;
            for (Set<String> classBindings : targetsByClass.values()) {
                if (classBindings.contains(bindingName)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                throw annotationError(
                        normalizedSpec,
                        "Binding annotation configured for unknown target binding: " + bindingName
                );
            }
        }

        final Path sourceRoot = normalizedOutput.resolve(GENERATED_PACKAGE.replace('.', '/'));
        final List<Path> sources = new ArrayList<>();
        for (Map.Entry<String, Set<String>> classTargets : targetsByClass.entrySet()) {
            final String className = classTargets.getKey();
            final String bridgeClassName = toBridgeClassName(className);
            final String source = renderBridgeSource(
                    className,
                    bridgeClassName,
                    classTargets.getValue(),
                    classAnnotations,
                    bindingAnnotations
            );
            final Path sourcePath = sourceRoot.resolve(bridgeClassName + ".java");
            try {
                Files.createDirectories(sourcePath.getParent());
                Files.writeString(sourcePath, source, UTF_8);
            } catch (final IOException ioException) {
                throw new JvmCompilationException(
                        CODE_INPUT,
                        "Failed to write interop bridge source: " + ioException.getMessage(),
                        null,
                        null,
                        normalizedSpec.toString(),
                        null,
                        null,
                        ioException
                );
            }
            sources.add(sourcePath);
        }

        final Path metadataPath = normalizedOutput.resolve("interop-bridges.properties");
        final Properties metadata = new Properties();
        metadata.setProperty("formatVersion", "0.1");
        metadata.setProperty("generatedPackage", GENERATED_PACKAGE);
        metadata.setProperty("generatedCount", Integer.toString(sources.size()));
        metadata.setProperty("targets", String.join(",", emittedTargets));
        metadata.setProperty("selectedTarget.count", Integer.toString(selectedTargets.size()));
        for (int index = 0; index < selectedTargets.size(); index++) {
            final InteropBridgeArtifact.SelectedTargetIdentity selectedTarget = selectedTargets.get(index);
            final String prefix = "selectedTarget." + index + ".";
            metadata.setProperty(prefix + "className", selectedTarget.className());
            metadata.setProperty(prefix + "binding", selectedTarget.bindingName());
            metadata.setProperty(prefix + "owner", selectedTarget.owner());
            metadata.setProperty(prefix + "name", selectedTarget.name());
            metadata.setProperty(prefix + "descriptor", selectedTarget.descriptor());
            metadata.setProperty(prefix + "invokeKind", selectedTarget.invokeKind());
        }
        metadata.setProperty("selectedTarget.unresolved.count", Integer.toString(unresolvedTargets.size()));
        for (int index = 0; index < unresolvedTargets.size(); index++) {
            final InteropBridgeArtifact.UnresolvedTarget unresolvedTarget = unresolvedTargets.get(index);
            final String prefix = "selectedTarget.unresolved." + index + ".";
            metadata.setProperty(prefix + "className", unresolvedTarget.className());
            metadata.setProperty(prefix + "binding", unresolvedTarget.bindingName());
            metadata.setProperty(prefix + "reason", unresolvedTarget.reason());
        }
        try {
            Files.createDirectories(normalizedOutput);
            try (OutputStream outputStream = Files.newOutputStream(metadataPath)) {
                metadata.store(outputStream, "TSJ interop bridges");
            }
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    CODE_INPUT,
                    "Failed to write interop bridge metadata: " + ioException.getMessage(),
                    null,
                    null,
                    normalizedSpec.toString(),
                    null,
                    null,
                    ioException
            );
        }

        return new InteropBridgeArtifact(
                normalizedOutput,
                List.copyOf(sources),
                List.copyOf(emittedTargets),
                List.copyOf(selectedTargets),
                List.copyOf(unresolvedTargets)
        );
    }

    private static Path normalizeSpec(final Path specFile) {
        final Path normalized = Objects.requireNonNull(specFile, "specFile").toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new JvmCompilationException(
                    CODE_INPUT,
                    "Interop spec file not found: " + normalized,
                    null,
                    null,
                    normalized.toString(),
                    null,
                    null
            );
        }
        return normalized;
    }

    private static Properties readSpecProperties(final Path specFile) {
        final Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(specFile)) {
            properties.load(inputStream);
            return properties;
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    CODE_INPUT,
                    "Failed to read interop spec: " + ioException.getMessage(),
                    null,
                    null,
                    specFile.toString(),
                    null,
                    null,
                    ioException
            );
        }
    }

    private static void validateRetiredInteropProperties(final Properties properties, final Path specFile) {
        for (String propertyName : properties.stringPropertyNames()) {
            if (isRetiredInteropProperty(propertyName)) {
                throw invalidTarget(
                        specFile,
                        "Interop spec property `"
                                + propertyName
                                + "` is retired. Use TS executable classes with `java:` annotations instead "
                                + "of Spring-specific interop bridge generation."
                );
            }
        }
    }

    private static boolean isRetiredInteropProperty(final String propertyName) {
        return "springConfiguration".equals(propertyName)
                || "springBeanTargets".equals(propertyName)
                || "springWebController".equals(propertyName)
                || "springWebBasePath".equals(propertyName)
                || "springErrorMappings".equals(propertyName)
                || propertyName.startsWith("springRequestMappings.");
    }

    private static Set<InteropTarget> parseTargets(final String rawValue, final Path specFile) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        final Set<InteropTarget> targets = new LinkedHashSet<>();
        final String[] parts = rawValue.split(",");
        for (String part : parts) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int separator = trimmed.indexOf('#');
            if (separator <= 0 || separator >= trimmed.length() - 1) {
                throw new JvmCompilationException(
                        CODE_INVALID,
                        "Invalid interop target format: " + trimmed + ". Expected <class>#<method>.",
                        null,
                        null,
                        specFile.toString(),
                        null,
                        null
                );
            }
            final String className = trimmed.substring(0, separator).trim();
            final String methodName = trimmed.substring(separator + 1).trim();
            if (className.isEmpty() || methodName.isEmpty()) {
                throw new JvmCompilationException(
                        CODE_INVALID,
                        "Invalid interop target format: " + trimmed + ". Expected <class>#<method>.",
                        null,
                        null,
                        specFile.toString(),
                        null,
                        null
                );
            }
            targets.add(new InteropTarget(className, methodName));
        }
        return Set.copyOf(targets);
    }

    private static Class<?> resolveTargetClass(final InteropTarget target, final Path specFile) {
        try {
            final ClassLoader classLoader = resolveInteropClassLoader();
            return Class.forName(target.className(), true, classLoader);
        } catch (final ClassNotFoundException classNotFoundException) {
            throw new JvmCompilationException(
                    CODE_INVALID,
                    "Interop target class was not found: " + target.className(),
                    null,
                    null,
                    specFile.toString(),
                    null,
                    null,
                    classNotFoundException
            );
        }
    }

    private static ClassLoader resolveInteropClassLoader() {
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return contextLoader;
        }
        return InteropBridgeGenerator.class.getClassLoader();
    }

    private static String renderBridgeSource(
            final String className,
            final String bridgeClassName,
            final Set<String> bindings,
            final List<String> classAnnotations,
            final Map<String, List<String>> bindingAnnotations
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        for (String classAnnotation : classAnnotations) {
            builder.append("@").append(classAnnotation).append("\n");
        }
        builder.append("public final class ").append(bridgeClassName).append(" {\n");
        builder.append("    private ").append(bridgeClassName).append("() {\n");
        builder.append("    }\n\n");
        for (String bindingName : bindings) {
            final String javaMethodName = sanitizeJavaIdentifier(bindingName);
            final List<String> methodAnnotations = bindingAnnotations.getOrDefault(bindingName, List.of());
            for (String methodAnnotation : methodAnnotations) {
                builder.append("    @").append(methodAnnotation).append("\n");
            }
            builder.append("    public static Object ")
                    .append(javaMethodName)
                    .append("(final Object... args) {\n");
            builder.append("        return dev.tsj.runtime.TsjJavaInterop.invokeBinding(\"")
                    .append(escapeJava(className))
                    .append("\", \"")
                    .append(escapeJava(bindingName))
                    .append("\", args);\n");
            builder.append("    }\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private static String toBridgeClassName(final String className) {
        final String[] parts = className.split("\\.");
        final StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            final String normalized = sanitizeJavaIdentifier(part);
            builder.append(Character.toUpperCase(normalized.charAt(0)));
            if (normalized.length() > 1) {
                builder.append(normalized.substring(1));
            }
        }
        if (builder.isEmpty()) {
            builder.append("Interop");
        }
        builder.append("Bridge");
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

    private static List<String> parseAnnotations(final String rawValue, final Path specFile) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        final List<String> annotations = new ArrayList<>();
        for (String segment : rawValue.split(",")) {
            final String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.matches(TYPE_NAME_PATTERN)) {
                throw annotationError(specFile, "Invalid annotation type name: " + trimmed);
            }
            annotations.add(trimmed);
        }
        return List.copyOf(annotations);
    }

    private static Map<String, List<String>> parseBindingAnnotations(final Properties properties, final Path specFile) {
        final Map<String, List<String>> annotationsByBinding = new LinkedHashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith(BINDING_ANNOTATION_PREFIX)) {
                continue;
            }
            final String bindingName = propertyName.substring(BINDING_ANNOTATION_PREFIX.length()).trim();
            annotationsByBinding.put(bindingName, parseAnnotations(properties.getProperty(propertyName), specFile));
        }
        return Map.copyOf(annotationsByBinding);
    }

    private static Map<String, List<JavaOverloadResolver.Argument>> parseBindingArgs(
            final Properties properties,
            final Path specFile
    ) {
        final Map<String, List<JavaOverloadResolver.Argument>> bindingArgs = new LinkedHashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith(BINDING_ARGS_PREFIX)) {
                continue;
            }
            final String bindingName = propertyName.substring(BINDING_ARGS_PREFIX.length()).trim();
            if (bindingName.isBlank()) {
                throw invalidTarget(specFile, "Binding args key must not be empty.");
            }
            final String rawValue = properties.getProperty(propertyName);
            if (rawValue == null || rawValue.isBlank()) {
                bindingArgs.put(bindingName, List.of());
                continue;
            }
            final List<JavaOverloadResolver.Argument> arguments = new ArrayList<>();
            for (String segment : rawValue.split(",")) {
                final String token = segment.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if ("null".equals(token)) {
                    arguments.add(JavaOverloadResolver.Argument.nullArgument());
                    continue;
                }
                if ("undefined".equals(token)) {
                    arguments.add(JavaOverloadResolver.Argument.undefinedArgument());
                    continue;
                }
                arguments.add(JavaOverloadResolver.Argument.descriptor(token));
            }
            bindingArgs.put(bindingName, List.copyOf(arguments));
        }
        return Map.copyOf(bindingArgs);
    }

    private static void validateAnnotationType(final String annotationType, final Path specFile) {
        final Class<?> resolvedType;
        try {
            final ClassLoader classLoader = resolveInteropClassLoader();
            resolvedType = Class.forName(annotationType, true, classLoader);
        } catch (final ClassNotFoundException classNotFoundException) {
            throw annotationError(specFile, "Annotation type not found: " + annotationType, classNotFoundException);
        }
        if (!Annotation.class.isAssignableFrom(resolvedType)) {
            throw annotationError(specFile, "Configured annotation is not a Java annotation type: " + annotationType);
        }
    }

    private static JvmCompilationException annotationError(final Path specFile, final String message) {
        return annotationError(specFile, message, null);
    }

    private static JvmCompilationException annotationError(
            final Path specFile,
            final String message,
            final Throwable cause
    ) {
        return new JvmCompilationException(
                CODE_ANNOTATION,
                message,
                null,
                null,
                specFile.toString(),
                "TSJ32-ANNOTATION-SYNTAX",
                GUIDANCE_ANNOTATION,
                cause
        );
    }

    private static SelectedIdentityResolution resolveSelectedIdentity(
            final Class<?> targetClass,
            final InteropTarget target,
            final List<JavaOverloadResolver.Argument> bindingArgs,
            final Map<String, List<JavaNullabilityAnalyzer.NullabilityState>> methodNullabilityByKey,
            final JavaOverloadResolver overloadResolver,
            final Path specFile
    ) {
        final String bindingName = target.bindingName();
        if (BINDING_CONSTRUCTOR.equals(bindingName)) {
            return resolveSelectedConstructorIdentity(
                    targetClass,
                    target,
                    bindingArgs,
                    overloadResolver,
                    specFile
            );
        }
        if (bindingName.startsWith(BINDING_INSTANCE_GET_PREFIX)) {
            return resolveSelectedFieldIdentity(
                    targetClass,
                    target,
                    bindingName.substring(BINDING_INSTANCE_GET_PREFIX.length()),
                    JavaOverloadResolver.InvokeKind.INSTANCE_FIELD_GET,
                    true,
                    specFile
            );
        }
        if (bindingName.startsWith(BINDING_INSTANCE_SET_PREFIX)) {
            return resolveSelectedFieldIdentity(
                    targetClass,
                    target,
                    bindingName.substring(BINDING_INSTANCE_SET_PREFIX.length()),
                    JavaOverloadResolver.InvokeKind.INSTANCE_FIELD_SET,
                    false,
                    specFile
            );
        }
        if (bindingName.startsWith(BINDING_STATIC_GET_PREFIX)) {
            return resolveSelectedFieldIdentity(
                    targetClass,
                    target,
                    bindingName.substring(BINDING_STATIC_GET_PREFIX.length()),
                    JavaOverloadResolver.InvokeKind.STATIC_FIELD_GET,
                    true,
                    specFile
            );
        }
        if (bindingName.startsWith(BINDING_STATIC_SET_PREFIX)) {
            return resolveSelectedFieldIdentity(
                    targetClass,
                    target,
                    bindingName.substring(BINDING_STATIC_SET_PREFIX.length()),
                    JavaOverloadResolver.InvokeKind.STATIC_FIELD_SET,
                    false,
                    specFile
            );
        }
        if (bindingName.startsWith(BINDING_INSTANCE_PREFIX)) {
            final String methodName = bindingName.substring(BINDING_INSTANCE_PREFIX.length());
            final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                    targetClass,
                    methodName,
                    JavaOverloadResolver.InvokeKind.INSTANCE_METHOD,
                    methodNullabilityByKey
            );
            return resolveSelectedCallableIdentity(target, bindingArgs, overloadResolver, specFile, candidates);
        }
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                targetClass,
                bindingName,
                JavaOverloadResolver.InvokeKind.STATIC_METHOD,
                methodNullabilityByKey
        );
        if (!candidates.isEmpty()) {
            return resolveSelectedCallableIdentity(target, bindingArgs, overloadResolver, specFile, candidates);
        }
        return resolveSelectedFieldIdentity(
                targetClass,
                target,
                bindingName,
                JavaOverloadResolver.InvokeKind.STATIC_FIELD_GET,
                true,
                specFile
        );
    }

    private static Map<String, List<JavaNullabilityAnalyzer.NullabilityState>> resolveParameterNullabilityByMethodKey(
            final Class<?> targetClass
    ) {
        final LinkedHashSet<Class<?>> declaringClasses = new LinkedHashSet<>();
        declaringClasses.add(targetClass);
        for (Method method : targetClass.getMethods()) {
            declaringClasses.add(method.getDeclaringClass());
        }
        final List<Class<?>> orderedClasses = declaringClasses.stream()
                .sorted(Comparator
                        .comparingInt(InteropBridgeGenerator::inheritanceDepth)
                        .thenComparing(Class::getName))
                .toList();

        final List<JavaClassfileReader.RawClassInfo> classInfos = new ArrayList<>();
        final LinkedHashSet<String> packageInfoResources = new LinkedHashSet<>();
        for (Class<?> declaringClass : orderedClasses) {
            final String internalName = declaringClass.getName().replace('.', '/');
            final int packageSeparator = internalName.lastIndexOf('/');
            if (packageSeparator > 0) {
                final String packageInfoResource = internalName.substring(0, packageSeparator) + "/package-info.class";
                if (packageInfoResources.add(packageInfoResource)) {
                    final JavaClassfileReader.RawClassInfo packageInfo =
                            readRawClassInfo(declaringClass, packageInfoResource);
                    if (packageInfo != null) {
                        classInfos.add(packageInfo);
                    }
                }
            }
            final JavaClassfileReader.RawClassInfo classInfo =
                    readRawClassInfo(declaringClass, internalName + ".class");
            if (classInfo != null) {
                classInfos.add(classInfo);
            }
        }
        if (classInfos.isEmpty()) {
            return Map.of();
        }

        final JavaNullabilityAnalyzer.AnalysisResult analysis = new JavaNullabilityAnalyzer().analyze(classInfos);
        final Map<String, List<JavaNullabilityAnalyzer.NullabilityState>> methodNullabilityByKey = new LinkedHashMap<>();
        for (Class<?> declaringClass : orderedClasses) {
            final String internalName = declaringClass.getName().replace('.', '/');
            final JavaNullabilityAnalyzer.ClassNullability classNullability =
                    analysis.classesByInternalName().get(internalName);
            if (classNullability == null) {
                continue;
            }
            final List<Map.Entry<String, JavaNullabilityAnalyzer.MethodNullability>> methodEntries =
                    new ArrayList<>(classNullability.methodsByKey().entrySet());
            methodEntries.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, JavaNullabilityAnalyzer.MethodNullability> methodEntry : methodEntries) {
                methodNullabilityByKey.put(
                        methodEntry.getKey(),
                        methodEntry.getValue().parameterNullability()
                );
            }
        }
        return Map.copyOf(methodNullabilityByKey);
    }

    private static int inheritanceDepth(final Class<?> type) {
        int depth = 0;
        Class<?> cursor = type;
        while (cursor != null && cursor.getSuperclass() != null) {
            depth++;
            cursor = cursor.getSuperclass();
        }
        return depth;
    }

    private static JavaClassfileReader.RawClassInfo readRawClassInfo(
            final Class<?> lookupClass,
            final String resourceName
    ) {
        final String normalizedResource = resourceName.startsWith("/")
                ? resourceName.substring(1)
                : resourceName;
        final InputStream stream = openClassResource(lookupClass, normalizedResource);
        if (stream == null) {
            return null;
        }
        try (InputStream inputStream = stream) {
            final byte[] classBytes = inputStream.readAllBytes();
            return new JavaClassfileReader().read(classBytes, Path.of(normalizedResource));
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static InputStream openClassResource(final Class<?> lookupClass, final String resourceName) {
        final ClassLoader classLoader = lookupClass.getClassLoader();
        if (classLoader != null) {
            final InputStream stream = classLoader.getResourceAsStream(resourceName);
            if (stream != null) {
                return stream;
            }
        }
        return lookupClass.getResourceAsStream("/" + resourceName);
    }

    private static SelectedIdentityResolution resolveSelectedConstructorIdentity(
            final Class<?> targetClass,
            final InteropTarget target,
            final List<JavaOverloadResolver.Argument> bindingArgs,
            final JavaOverloadResolver overloadResolver,
            final Path specFile
    ) {
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForConstructors(targetClass);
        return resolveSelectedCallableIdentity(target, bindingArgs, overloadResolver, specFile, candidates);
    }

    private static SelectedIdentityResolution resolveSelectedFieldIdentity(
            final Class<?> targetClass,
            final InteropTarget target,
            final String fieldName,
            final JavaOverloadResolver.InvokeKind invokeKind,
            final boolean getter,
            final Path specFile
    ) {
        final boolean expectStatic = invokeKind == JavaOverloadResolver.InvokeKind.STATIC_FIELD_GET
                || invokeKind == JavaOverloadResolver.InvokeKind.STATIC_FIELD_SET;
        final Field field;
        try {
            field = targetClass.getField(fieldName);
        } catch (final NoSuchFieldException noSuchFieldException) {
            throw invalidTarget(specFile, "Interop target field was not found: " + target.displayName(), noSuchFieldException);
        }
        if (Modifier.isStatic(field.getModifiers()) != expectStatic) {
            throw invalidTarget(specFile, "Interop target field kind mismatch: " + target.displayName());
        }
        final String fieldDescriptor = JavaOverloadResolver.descriptorFor(field.getType());
        final String memberDescriptor = getter
                ? "()" + fieldDescriptor
                : "(" + fieldDescriptor + ")V";
        return SelectedIdentityResolution.selected(new InteropBridgeArtifact.SelectedTargetIdentity(
                target.className(),
                target.bindingName(),
                targetClass.getName(),
                fieldName,
                memberDescriptor,
                invokeKind.name()
        ));
    }

    private static SelectedIdentityResolution resolveSelectedCallableIdentity(
            final InteropTarget target,
            final List<JavaOverloadResolver.Argument> bindingArgs,
            final JavaOverloadResolver overloadResolver,
            final Path specFile,
            final List<JavaOverloadResolver.Candidate> candidates
    ) {
        if (bindingArgs != null) {
            final JavaOverloadResolver.Resolution resolution = overloadResolver.resolve(candidates, bindingArgs);
            if (resolution.status() != JavaOverloadResolver.Status.SELECTED || resolution.selected() == null) {
                throw invalidTarget(
                        specFile,
                        "TSJ-54 overload resolution failed for " + target.displayName() + ": " + resolution.diagnostic()
                );
            }
            final JavaOverloadResolver.MemberIdentity selected = resolution.selected();
            return SelectedIdentityResolution.selected(new InteropBridgeArtifact.SelectedTargetIdentity(
                    target.className(),
                    target.bindingName(),
                    selected.owner(),
                    selected.name(),
                    selected.descriptor(),
                    selected.invokeKind().name()
            ));
        }

        if (candidates.size() == 1) {
            final JavaOverloadResolver.MemberIdentity selected = candidates.getFirst().identity();
            return SelectedIdentityResolution.selected(new InteropBridgeArtifact.SelectedTargetIdentity(
                    target.className(),
                    target.bindingName(),
                    selected.owner(),
                    selected.name(),
                    selected.descriptor(),
                    selected.invokeKind().name()
            ));
        }

        final String unresolvedReason = "overloaded target requires bindingArgs."
                + target.bindingName()
                + " (candidates="
                + candidateSummary(candidates)
                + ")";
        return SelectedIdentityResolution.unresolved(unresolvedReason);
    }

    private static String candidateSummary(final List<JavaOverloadResolver.Candidate> candidates) {
        final List<String> parts = new ArrayList<>();
        for (JavaOverloadResolver.Candidate candidate : candidates) {
            final JavaOverloadResolver.MemberIdentity identity = candidate.identity();
            parts.add(identity.name() + identity.descriptor());
        }
        return String.join(", ", parts);
    }

    private static void validateBindingTarget(
            final Class<?> targetClass,
            final String bindingName,
            final Path specFile,
            final String displayName
    ) {
        if (BINDING_CONSTRUCTOR.equals(bindingName)) {
            final Constructor<?>[] constructors = targetClass.getConstructors();
            if (constructors.length > 0) {
                return;
            }
            throw invalidTarget(specFile, "Interop target constructor was not found: " + displayName);
        }
        if (bindingName.startsWith(BINDING_INSTANCE_GET_PREFIX)) {
            validateFieldBinding(targetClass, bindingName, BINDING_INSTANCE_GET_PREFIX, false, specFile, displayName);
            return;
        }
        if (bindingName.startsWith(BINDING_INSTANCE_SET_PREFIX)) {
            validateFieldBinding(targetClass, bindingName, BINDING_INSTANCE_SET_PREFIX, false, specFile, displayName);
            return;
        }
        if (bindingName.startsWith(BINDING_STATIC_GET_PREFIX)) {
            validateFieldBinding(targetClass, bindingName, BINDING_STATIC_GET_PREFIX, true, specFile, displayName);
            return;
        }
        if (bindingName.startsWith(BINDING_STATIC_SET_PREFIX)) {
            validateFieldBinding(targetClass, bindingName, BINDING_STATIC_SET_PREFIX, true, specFile, displayName);
            return;
        }
        if (bindingName.startsWith(BINDING_INSTANCE_PREFIX)) {
            final String methodName = bindingName.substring(BINDING_INSTANCE_PREFIX.length());
            if (methodName.isBlank()) {
                throw invalidTarget(specFile, "Invalid interop binding format: " + displayName);
            }
            for (Method method : targetClass.getMethods()) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers())) {
                    return;
                }
            }
            throw invalidTarget(
                    specFile,
                    "Interop target instance method was not found: " + displayName
            );
        }
        if (bindingName.startsWith("$")) {
            throw invalidTarget(specFile, "Invalid interop binding prefix in target: " + displayName);
        }
        for (Method method : targetClass.getMethods()) {
            if (!bindingName.equals(method.getName())) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                return;
            }
        }
        try {
            final Field field = targetClass.getField(bindingName);
            if (Modifier.isStatic(field.getModifiers())) {
                return;
            }
        } catch (NoSuchFieldException ignored) {
            // No static field with that name; report invalid bare binding.
        }
        throw invalidTarget(
                specFile,
                "Interop target method was not found or not static: " + displayName
        );
    }

    private static void validateFieldBinding(
            final Class<?> targetClass,
            final String bindingName,
            final String prefix,
            final boolean expectStatic,
            final Path specFile,
            final String displayName
    ) {
        final String fieldName = bindingName.substring(prefix.length());
        if (fieldName.isBlank()) {
            throw invalidTarget(specFile, "Invalid interop binding format: " + displayName);
        }
        final Field field;
        try {
            field = targetClass.getField(fieldName);
        } catch (final NoSuchFieldException noSuchFieldException) {
            throw invalidTarget(specFile, "Interop target field was not found: " + displayName, noSuchFieldException);
        }
        final boolean isStatic = Modifier.isStatic(field.getModifiers());
        if (expectStatic != isStatic) {
            final String expectedKind = expectStatic ? "static" : "instance";
            throw invalidTarget(specFile, "Interop target field is not " + expectedKind + ": " + displayName);
        }
    }

    private static JvmCompilationException invalidTarget(final Path specFile, final String message) {
        return invalidTarget(specFile, message, null);
    }

    private static JvmCompilationException invalidTarget(
            final Path specFile,
            final String message,
            final Throwable cause
    ) {
        return new JvmCompilationException(
                CODE_INVALID,
                message,
                null,
                null,
                specFile.toString(),
                null,
                null,
                cause
        );
    }

    private record SelectedIdentityResolution(
            InteropBridgeArtifact.SelectedTargetIdentity selectedIdentity,
            String unresolvedReason
    ) {
        private static SelectedIdentityResolution selected(
                final InteropBridgeArtifact.SelectedTargetIdentity selectedIdentity
        ) {
            return new SelectedIdentityResolution(selectedIdentity, null);
        }

        private static SelectedIdentityResolution unresolved(final String reason) {
            return new SelectedIdentityResolution(null, reason);
        }
    }

    private record InteropTarget(String className, String bindingName) {
        private String displayName() {
            return className + "#" + bindingName;
        }
    }
}
