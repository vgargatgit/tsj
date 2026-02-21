package dev.tsj.compiler.backend.jvm;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class JavaPropertySynthesizer {
    SynthesisResult synthesize(final Class<?> targetClass, final boolean enabled) {
        Objects.requireNonNull(targetClass, "targetClass");
        if (!enabled) {
            return new SynthesisResult(List.of(), List.of("Property synthesis disabled by feature flag."));
        }

        final Map<String, List<Method>> getCandidates = new LinkedHashMap<>();
        final Map<String, List<Method>> isCandidates = new LinkedHashMap<>();
        final Map<String, List<Method>> setCandidates = new LinkedHashMap<>();

        for (Method method : targetClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            final String methodName = method.getName();
            if (methodName.startsWith("get")
                    && methodName.length() > 3
                    && method.getParameterCount() == 0
                    && method.getReturnType() != void.class) {
                final String property = normalizePropertyName(methodName.substring(3));
                getCandidates.computeIfAbsent(property, ignored -> new ArrayList<>()).add(method);
                continue;
            }
            if (methodName.startsWith("is")
                    && methodName.length() > 2
                    && method.getParameterCount() == 0
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                final String property = normalizePropertyName(methodName.substring(2));
                isCandidates.computeIfAbsent(property, ignored -> new ArrayList<>()).add(method);
                continue;
            }
            if (methodName.startsWith("set") && methodName.length() > 3 && method.getParameterCount() == 1) {
                final String property = normalizePropertyName(methodName.substring(3));
                setCandidates.computeIfAbsent(property, ignored -> new ArrayList<>()).add(method);
            }
        }

        final List<String> diagnostics = new ArrayList<>();
        final List<SynthesizedProperty> properties = new ArrayList<>();

        final List<String> propertyNames = new ArrayList<>();
        propertyNames.addAll(getCandidates.keySet());
        propertyNames.addAll(isCandidates.keySet());
        propertyNames.addAll(setCandidates.keySet());
        final List<String> sortedPropertyNames = propertyNames.stream().distinct().sorted().toList();

        for (String propertyName : sortedPropertyNames) {
            final Method getter = selectGetter(propertyName, getCandidates.get(propertyName), isCandidates.get(propertyName), diagnostics);
            final Method setter = selectSetter(propertyName, setCandidates.get(propertyName), getter, diagnostics);
            if (getter == null && setter == null) {
                continue;
            }
            properties.add(new SynthesizedProperty(
                    propertyName,
                    getter == null ? null : getter.getName(),
                    getter == null ? null : JavaOverloadResolver.methodDescriptor(getter),
                    setter == null ? null : setter.getName(),
                    setter == null ? null : JavaOverloadResolver.methodDescriptor(setter)
            ));
        }

        properties.sort(Comparator.comparing(SynthesizedProperty::name));
        return new SynthesisResult(List.copyOf(properties), List.copyOf(diagnostics));
    }

    private static Method selectGetter(
            final String propertyName,
            final List<Method> getMethods,
            final List<Method> isMethods,
            final List<String> diagnostics
    ) {
        final List<Method> gets = getMethods == null ? List.of() : getMethods;
        final List<Method> ises = isMethods == null ? List.of() : isMethods;
        if (gets.size() > 1) {
            diagnostics.add("Skipped property `" + propertyName + "`: ambiguous get-method overloads.");
            return null;
        }
        if (ises.size() > 1) {
            diagnostics.add("Skipped property `" + propertyName + "`: ambiguous is-method overloads.");
            return null;
        }
        final Method get = gets.isEmpty() ? null : gets.getFirst();
        final Method is = ises.isEmpty() ? null : ises.getFirst();
        if (get != null && is != null) {
            if (get.getReturnType() == boolean.class || get.getReturnType() == Boolean.class) {
                return is;
            }
            diagnostics.add(
                    "Skipped property `"
                            + propertyName
                            + "`: conflicting get/is methods with non-boolean get return type."
            );
            return null;
        }
        return is != null ? is : get;
    }

    private static Method selectSetter(
            final String propertyName,
            final List<Method> setters,
            final Method getter,
            final List<String> diagnostics
    ) {
        if (setters == null || setters.isEmpty()) {
            return null;
        }
        if (setters.size() == 1) {
            return setters.getFirst();
        }
        if (getter != null) {
            for (Method setter : setters) {
                if (setter.getParameterTypes()[0] == getter.getReturnType()) {
                    return setter;
                }
            }
        }
        diagnostics.add("Skipped property `" + propertyName + "`: ambiguous setter overloads.");
        return null;
    }

    private static String normalizePropertyName(final String raw) {
        return Introspector.decapitalize(raw);
    }

    record SynthesizedProperty(
            String name,
            String getterName,
            String getterDescriptor,
            String setterName,
            String setterDescriptor
    ) {
    }

    record SynthesisResult(
            List<SynthesizedProperty> properties,
            List<String> diagnostics
    ) {
    }
}
