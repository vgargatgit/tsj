package dev.tsj.compiler.backend.jvm;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toUnmodifiableSet;

final class JavaSamAnalyzer {
    private static final Set<SamSignatureKey> OBJECT_METHOD_KEYS = Arrays.stream(Object.class.getMethods())
            .map(method -> new SamSignatureKey(method.getName(), parameterDescriptorOnly(method)))
            .collect(toUnmodifiableSet());

    SamResult analyze(final Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (!type.isInterface()) {
            return new SamResult(type.getName(), false, false, null, List.of(), List.of());
        }

        final List<Method> abstractCandidates = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (isObjectSignature(method)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.isDefault()) {
                continue;
            }
            if (!Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            abstractCandidates.add(method);
        }

        final Map<SamSignatureKey, Method> canonicalBySignature = new LinkedHashMap<>();
        for (Method method : abstractCandidates) {
            final SamSignatureKey key = new SamSignatureKey(method.getName(), parameterDescriptorOnly(method));
            final Method existing = canonicalBySignature.get(key);
            if (existing == null) {
                canonicalBySignature.put(key, method);
                continue;
            }
            if (existing.getReturnType().isAssignableFrom(method.getReturnType())) {
                canonicalBySignature.put(key, method);
            }
        }

        final List<String> candidateDescriptors = new ArrayList<>();
        for (Method method : canonicalBySignature.values()) {
            candidateDescriptors.add(method.getName() + JavaOverloadResolver.methodDescriptor(method));
        }

        final List<String> diagnostics = new ArrayList<>();
        final boolean annotated = type.isAnnotationPresent(FunctionalInterface.class);
        final SamMethod samMethod;
        if (canonicalBySignature.size() == 1) {
            final Method method = canonicalBySignature.values().iterator().next();
            samMethod = new SamMethod(
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    JavaOverloadResolver.methodDescriptor(method),
                    method.toGenericString()
            );
        } else {
            samMethod = null;
            if (annotated) {
                diagnostics.add(
                        "@FunctionalInterface is inconsistent: expected exactly one abstract method but found "
                                + canonicalBySignature.size()
                                + "."
                );
            }
        }
        return new SamResult(
                type.getName(),
                canonicalBySignature.size() == 1,
                annotated,
                samMethod,
                List.copyOf(candidateDescriptors),
                List.copyOf(diagnostics)
        );
    }

    private static boolean isObjectSignature(final Method method) {
        return OBJECT_METHOD_KEYS.contains(new SamSignatureKey(method.getName(), parameterDescriptorOnly(method)));
    }

    private static String parameterDescriptorOnly(final Method method) {
        final String descriptor = JavaOverloadResolver.methodDescriptor(method);
        final int closing = descriptor.indexOf(')');
        if (closing < 0) {
            return descriptor;
        }
        return descriptor.substring(0, closing + 1);
    }

    record SamMethod(
            String owner,
            String name,
            String descriptor,
            String genericSignature
    ) {
    }

    record SamResult(
            String interfaceName,
            boolean functional,
            boolean functionalInterfaceAnnotated,
            SamMethod samMethod,
            List<String> candidateDescriptors,
            List<String> diagnostics
    ) {
    }

    private record SamSignatureKey(String name, String parameterDescriptor) {
    }
}
