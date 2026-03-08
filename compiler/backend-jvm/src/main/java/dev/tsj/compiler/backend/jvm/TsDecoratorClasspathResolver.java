package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class TsDecoratorClasspathResolver {
    static final String RESOLUTION_CODE = "TSJ-DECORATOR-RESOLUTION";
    static final String TYPE_CODE = "TSJ-DECORATOR-TYPE";
    static final String RETENTION_CODE = "TSJ-DECORATOR-RETENTION";
    static final String FEATURE_ID = "TSJ71-DECORATOR-CLASSPATH";
    static final String GUIDANCE =
            "Import decorators from java:<fully.qualified.AnnotationType> and ensure runtime-retained annotation types are on classpath.";

    private static final int ACC_ANNOTATION = 0x2000;
    private static final String TARGET_DESCRIPTOR = "Ljava/lang/annotation/Target;";
    private static final String RETENTION_DESCRIPTOR = "Ljava/lang/annotation/Retention;";
    private static final String ELEMENT_TYPE_PREFIX = "Ljava/lang/annotation/ElementType;.";
    private static final String RETENTION_POLICY_PREFIX = "Ljava/lang/annotation/RetentionPolicy;.";

    private final JavaSymbolTable symbolTable;
    private final Map<String, AnnotationMetadata> metadataByClassName;

    TsDecoratorClasspathResolver(final JavaSymbolTable symbolTable) {
        this.symbolTable = Objects.requireNonNull(symbolTable, "symbolTable");
        this.metadataByClassName = new LinkedHashMap<>();
    }

    void validateUsage(
            final ImportedDecoratorBinding binding,
            final DecoratorTarget target,
            final Path sourceFile,
            final int line,
            final String decoratorName
    ) {
        final AnnotationMetadata metadata = metadataFor(binding.annotationClassName(), sourceFile, line, decoratorName);
        if (!metadata.runtimeRetention()) {
            throw new JvmCompilationException(
                    RETENTION_CODE,
                    "Imported decorator @" + decoratorName + " resolves to annotation type `"
                            + binding.annotationClassName()
                            + "` with non-runtime retention. Runtime retention is required.",
                    line,
                    1,
                    sourceFile.toString(),
                    FEATURE_ID,
                    GUIDANCE
            );
        }
        if (!metadata.allowedTargets().contains(target)) {
            throw new JvmCompilationException(
                    "TSJ-DECORATOR-TARGET",
                    "Decorator @" + decoratorName + " resolves to annotation type `"
                            + binding.annotationClassName()
                            + "` which does not allow target "
                            + target.displayName()
                            + ".",
                    line,
                    1,
                    sourceFile.toString(),
                    FEATURE_ID,
                    GUIDANCE
            );
        }
    }

    private AnnotationMetadata metadataFor(
            final String annotationClassName,
            final Path sourceFile,
            final int line,
            final String decoratorName
    ) {
        final AnnotationMetadata cached = metadataByClassName.get(annotationClassName);
        if (cached != null) {
            return cached;
        }

        final JavaSymbolTable.ClassResolution classResolution = symbolTable.resolveClassWithMetadata(annotationClassName);
        final Optional<JavaClassfileReader.RawClassInfo> classInfo = classResolution.classInfo();
        if (classInfo.isEmpty()) {
            final String detail = classResolution.status() == JavaSymbolTable.ResolutionStatus.TARGET_LEVEL_MISMATCH
                    ? " (" + classResolution.diagnostic() + ")"
                    : "";
            throw new JvmCompilationException(
                    RESOLUTION_CODE,
                    "Failed to resolve decorator @" + decoratorName + " annotation type `"
                            + annotationClassName
                            + "` from classpath"
                            + detail
                            + ".",
                    line,
                    1,
                    sourceFile.toString(),
                    FEATURE_ID,
                    GUIDANCE
            );
        }

        final JavaClassfileReader.RawClassInfo resolvedClass = classInfo.get();
        final boolean annotationType = (resolvedClass.accessFlags() & ACC_ANNOTATION) != 0;
        if (!annotationType) {
            throw new JvmCompilationException(
                    TYPE_CODE,
                    "Decorator @" + decoratorName + " import resolves to `"
                            + annotationClassName
                            + "`, but that type is not a Java annotation type.",
                    line,
                    1,
                    sourceFile.toString(),
                    FEATURE_ID,
                    GUIDANCE
            );
        }

        final AnnotationMetadata metadata = new AnnotationMetadata(
                parseRuntimeRetention(resolvedClass),
                parseAllowedTargets(resolvedClass)
        );
        metadataByClassName.put(annotationClassName, metadata);
        return metadata;
    }

    private static boolean parseRuntimeRetention(final JavaClassfileReader.RawClassInfo classInfo) {
        final List<JavaClassfileReader.RawAnnotationInfo> annotations = new ArrayList<>();
        annotations.addAll(classInfo.runtimeVisibleAnnotations());
        annotations.addAll(classInfo.runtimeInvisibleAnnotations());
        for (JavaClassfileReader.RawAnnotationInfo annotation : annotations) {
            if (!RETENTION_DESCRIPTOR.equals(annotation.descriptor())) {
                continue;
            }
            final String value = annotation.values().get("value");
            if (value == null) {
                break;
            }
            final String normalized = value.toUpperCase(Locale.ROOT);
            final String runtimeToken = (RETENTION_POLICY_PREFIX + "RUNTIME").toUpperCase(Locale.ROOT);
            return normalized.contains(runtimeToken);
        }
        return false;
    }

    private static EnumSet<DecoratorTarget> parseAllowedTargets(final JavaClassfileReader.RawClassInfo classInfo) {
        final List<JavaClassfileReader.RawAnnotationInfo> annotations = new ArrayList<>();
        annotations.addAll(classInfo.runtimeVisibleAnnotations());
        annotations.addAll(classInfo.runtimeInvisibleAnnotations());
        for (JavaClassfileReader.RawAnnotationInfo annotation : annotations) {
            if (!TARGET_DESCRIPTOR.equals(annotation.descriptor())) {
                continue;
            }
            final String rawValue = annotation.values().get("value");
            if (rawValue == null || rawValue.isBlank()) {
                break;
            }
            final EnumSet<DecoratorTarget> parsed = EnumSet.noneOf(DecoratorTarget.class);
            if (rawValue.contains(ELEMENT_TYPE_PREFIX + "TYPE")) {
                parsed.add(DecoratorTarget.CLASS);
            }
            if (rawValue.contains(ELEMENT_TYPE_PREFIX + "FIELD")) {
                parsed.add(DecoratorTarget.FIELD);
            }
            if (rawValue.contains(ELEMENT_TYPE_PREFIX + "METHOD")) {
                parsed.add(DecoratorTarget.METHOD);
            }
            if (rawValue.contains(ELEMENT_TYPE_PREFIX + "PARAMETER")) {
                parsed.add(DecoratorTarget.PARAMETER);
            }
            if (rawValue.contains(ELEMENT_TYPE_PREFIX + "CONSTRUCTOR")) {
                parsed.add(DecoratorTarget.CONSTRUCTOR);
            }
            return parsed.isEmpty() ? EnumSet.noneOf(DecoratorTarget.class) : parsed;
        }
        return EnumSet.allOf(DecoratorTarget.class);
    }

    enum DecoratorTarget {
        CLASS("class"),
        METHOD("method"),
        FIELD("field"),
        PARAMETER("parameter"),
        CONSTRUCTOR("constructor");

        private final String displayName;

        DecoratorTarget(final String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    record ImportedDecoratorBinding(
            String localName,
            String annotationClassName,
            Path sourceFile,
            int line
    ) {
        ImportedDecoratorBinding {
            localName = Objects.requireNonNull(localName, "localName");
            annotationClassName = Objects.requireNonNull(annotationClassName, "annotationClassName");
            sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
            if (line < 1) {
                throw new IllegalArgumentException("line must be >= 1");
            }
        }
    }

    private record AnnotationMetadata(
            boolean runtimeRetention,
            EnumSet<DecoratorTarget> allowedTargets
    ) {
        private AnnotationMetadata {
            allowedTargets = Objects.requireNonNull(allowedTargets, "allowedTargets");
        }
    }
}
