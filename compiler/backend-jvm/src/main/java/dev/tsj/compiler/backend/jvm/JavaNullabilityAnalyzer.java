package dev.tsj.compiler.backend.jvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavaNullabilityAnalyzer {
    private static final Set<String> NON_NULL_ANNOTATIONS = Set.of(
            "Lorg/jetbrains/annotations/NotNull;",
            "Ljavax/annotation/Nonnull;",
            "Landroidx/annotation/NonNull;",
            "Lorg/checkerframework/checker/nullness/qual/NonNull;"
    );

    private static final Set<String> NULLABLE_ANNOTATIONS = Set.of(
            "Lorg/jetbrains/annotations/Nullable;",
            "Ljavax/annotation/Nullable;",
            "Landroidx/annotation/Nullable;",
            "Lorg/checkerframework/checker/nullness/qual/Nullable;"
    );

    private static final Set<String> NON_NULL_DEFAULT_ANNOTATIONS = Set.of(
            "Ljavax/annotation/ParametersAreNonnullByDefault;",
            "Lorg/springframework/lang/NonNullApi;",
            "Lorg/jspecify/annotations/NullMarked;"
    );

    private static final Set<String> NULLABLE_DEFAULT_ANNOTATIONS = Set.of(
            "Lorg/jspecify/annotations/NullUnmarked;"
    );

    AnalysisResult analyze(final List<JavaClassfileReader.RawClassInfo> classes) {
        final Map<String, NullabilityState> packageDefaults = collectPackageDefaults(classes);
        final Map<String, ClassNullability> classViews = new LinkedHashMap<>();

        final List<JavaClassfileReader.RawClassInfo> sorted = classes.stream()
                .sorted((left, right) -> left.internalName().compareTo(right.internalName()))
                .toList();
        for (JavaClassfileReader.RawClassInfo rawClass : sorted) {
            if (isPackageInfo(rawClass.internalName())) {
                continue;
            }
            final String packageName = packageName(rawClass.internalName());
            final NullabilityState classDefault = resolveClassDefault(rawClass, packageDefaults.get(packageName));
            final Map<String, NullabilityState> fields = analyzeFields(rawClass, classDefault);
            final Map<String, MethodNullability> methods = analyzeMethods(rawClass, classDefault);
            classViews.put(rawClass.internalName(), new ClassNullability(
                    rawClass.internalName(),
                    classDefault,
                    Map.copyOf(fields),
                    Map.copyOf(methods)
            ));
        }

        return new AnalysisResult(
                Map.copyOf(classViews),
                Map.copyOf(packageDefaults)
        );
    }

    private static Map<String, NullabilityState> collectPackageDefaults(
            final List<JavaClassfileReader.RawClassInfo> classes
    ) {
        final Map<String, NullabilityState> packageDefaults = new LinkedHashMap<>();
        final List<JavaClassfileReader.RawClassInfo> sorted = classes.stream()
                .filter(candidate -> isPackageInfo(candidate.internalName()))
                .sorted((left, right) -> left.internalName().compareTo(right.internalName()))
                .toList();
        for (JavaClassfileReader.RawClassInfo packageInfo : sorted) {
            final NullabilityState explicit = explicitFromAnnotations(
                    packageInfo.runtimeVisibleAnnotations(),
                    packageInfo.runtimeInvisibleAnnotations(),
                    List.of(),
                    NON_NULL_DEFAULT_ANNOTATIONS,
                    NULLABLE_DEFAULT_ANNOTATIONS
            );
            if (explicit != NullabilityState.PLATFORM) {
                packageDefaults.put(packageName(packageInfo.internalName()), explicit);
            }
        }
        return packageDefaults;
    }

    private static Map<String, NullabilityState> analyzeFields(
            final JavaClassfileReader.RawClassInfo rawClass,
            final NullabilityState classDefault
    ) {
        final Map<String, NullabilityState> fields = new LinkedHashMap<>();
        for (JavaClassfileReader.RawFieldInfo field : rawClass.fields()) {
            final NullabilityState explicit = explicitFromAnnotations(
                    field.runtimeVisibleAnnotations(),
                    field.runtimeInvisibleAnnotations(),
                    mergeTypeAnnotations(
                            field.runtimeVisibleTypeAnnotations(),
                            field.runtimeInvisibleTypeAnnotations()
                    ),
                    NON_NULL_ANNOTATIONS,
                    NULLABLE_ANNOTATIONS
            );
            fields.put(
                    field.name() + ":" + field.descriptor(),
                    explicit == NullabilityState.PLATFORM ? classDefault : explicit
            );
        }
        return fields;
    }

    private static Map<String, MethodNullability> analyzeMethods(
            final JavaClassfileReader.RawClassInfo rawClass,
            final NullabilityState classDefault
    ) {
        final Map<String, MethodNullability> methods = new LinkedHashMap<>();
        for (JavaClassfileReader.RawMethodInfo method : rawClass.methods()) {
            if ("<init>".equals(method.name()) || "<clinit>".equals(method.name())) {
                continue;
            }
            final NullabilityState explicitReturn = explicitFromAnnotations(
                    method.runtimeVisibleAnnotations(),
                    method.runtimeInvisibleAnnotations(),
                    mergeTypeAnnotations(
                            method.runtimeVisibleTypeAnnotations(),
                            method.runtimeInvisibleTypeAnnotations()
                    ),
                    NON_NULL_ANNOTATIONS,
                    NULLABLE_ANNOTATIONS
            );
            final NullabilityState returnNullability =
                    explicitReturn == NullabilityState.PLATFORM ? classDefault : explicitReturn;
            final List<NullabilityState> parameterNullability = analyzeMethodParameters(method, classDefault);
            final String key = method.name() + method.descriptor();
            methods.put(
                    key,
                    new MethodNullability(
                            method.name(),
                            method.descriptor(),
                            returnNullability,
                            List.copyOf(parameterNullability)
                    )
            );
        }
        return methods;
    }

    private static List<NullabilityState> analyzeMethodParameters(
            final JavaClassfileReader.RawMethodInfo method,
            final NullabilityState classDefault
    ) {
        final int descriptorArity = descriptorArity(method.descriptor());
        final int paramCount = Math.max(
                descriptorArity,
                Math.max(
                        method.runtimeVisibleParameterAnnotations().size(),
                        method.runtimeInvisibleParameterAnnotations().size()
                )
        );
        final List<NullabilityState> params = new ArrayList<>(paramCount);
        for (int index = 0; index < paramCount; index++) {
            final List<JavaClassfileReader.RawAnnotationInfo> visible = index < method.runtimeVisibleParameterAnnotations().size()
                    ? method.runtimeVisibleParameterAnnotations().get(index)
                    : List.of();
            final List<JavaClassfileReader.RawAnnotationInfo> invisible = index < method.runtimeInvisibleParameterAnnotations().size()
                    ? method.runtimeInvisibleParameterAnnotations().get(index)
                    : List.of();
            final NullabilityState explicit = explicitFromAnnotations(
                    visible,
                    invisible,
                    List.of(),
                    NON_NULL_ANNOTATIONS,
                    NULLABLE_ANNOTATIONS
            );
            params.add(explicit == NullabilityState.PLATFORM ? classDefault : explicit);
        }
        return params;
    }

    private static List<JavaClassfileReader.RawAnnotationInfo> mergeTypeAnnotations(
            final List<JavaClassfileReader.RawTypeAnnotationInfo> visible,
            final List<JavaClassfileReader.RawTypeAnnotationInfo> invisible
    ) {
        final List<JavaClassfileReader.RawAnnotationInfo> annotations = new ArrayList<>();
        for (JavaClassfileReader.RawTypeAnnotationInfo annotation : visible) {
            annotations.add(annotation.annotation());
        }
        for (JavaClassfileReader.RawTypeAnnotationInfo annotation : invisible) {
            annotations.add(annotation.annotation());
        }
        return annotations;
    }

    private static NullabilityState explicitFromAnnotations(
            final List<JavaClassfileReader.RawAnnotationInfo> visibleAnnotations,
            final List<JavaClassfileReader.RawAnnotationInfo> invisibleAnnotations,
            final List<JavaClassfileReader.RawAnnotationInfo> typeAnnotations,
            final Set<String> nonNullDescriptors,
            final Set<String> nullableDescriptors
    ) {
        final Set<String> descriptors = new LinkedHashSet<>();
        for (JavaClassfileReader.RawAnnotationInfo annotation : visibleAnnotations) {
            descriptors.add(annotation.descriptor());
        }
        for (JavaClassfileReader.RawAnnotationInfo annotation : invisibleAnnotations) {
            descriptors.add(annotation.descriptor());
        }
        for (JavaClassfileReader.RawAnnotationInfo annotation : typeAnnotations) {
            descriptors.add(annotation.descriptor());
        }
        if (descriptors.stream().anyMatch(nullableDescriptors::contains)) {
            return NullabilityState.NULLABLE;
        }
        if (descriptors.stream().anyMatch(nonNullDescriptors::contains)) {
            return NullabilityState.NON_NULL;
        }
        return NullabilityState.PLATFORM;
    }

    private static NullabilityState resolveClassDefault(
            final JavaClassfileReader.RawClassInfo rawClass,
            final NullabilityState packageDefault
    ) {
        final NullabilityState classExplicit = explicitFromAnnotations(
                rawClass.runtimeVisibleAnnotations(),
                rawClass.runtimeInvisibleAnnotations(),
                List.of(),
                NON_NULL_DEFAULT_ANNOTATIONS,
                NULLABLE_DEFAULT_ANNOTATIONS
        );
        if (classExplicit != NullabilityState.PLATFORM) {
            return classExplicit;
        }
        if (packageDefault != null) {
            return packageDefault;
        }
        return NullabilityState.PLATFORM;
    }

    private static int descriptorArity(final String descriptor) {
        final int start = descriptor.indexOf('(');
        final int end = descriptor.indexOf(')');
        if (start < 0 || end < 0 || end <= start) {
            return 0;
        }
        int count = 0;
        int index = start + 1;
        while (index < end) {
            char marker = descriptor.charAt(index);
            while (marker == '[') {
                index++;
                marker = descriptor.charAt(index);
            }
            if (marker == 'L') {
                index = descriptor.indexOf(';', index);
                if (index < 0 || index > end) {
                    return count;
                }
            }
            count++;
            index++;
        }
        return count;
    }

    private static boolean isPackageInfo(final String internalName) {
        return internalName.endsWith("/package-info") || "package-info".equals(internalName);
    }

    private static String packageName(final String internalName) {
        final int lastSlash = internalName.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return internalName.substring(0, lastSlash);
    }

    enum NullabilityState {
        NON_NULL,
        NULLABLE,
        PLATFORM
    }

    record MethodNullability(
            String name,
            String descriptor,
            NullabilityState returnNullability,
            List<NullabilityState> parameterNullability
    ) {
    }

    record ClassNullability(
            String internalName,
            NullabilityState defaultNullability,
            Map<String, NullabilityState> fieldsByKey,
            Map<String, MethodNullability> methodsByKey
    ) {
    }

    record AnalysisResult(
            Map<String, ClassNullability> classesByInternalName,
            Map<String, NullabilityState> packageDefaults
    ) {
    }
}
