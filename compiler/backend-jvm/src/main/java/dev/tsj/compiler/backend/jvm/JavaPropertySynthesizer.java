package dev.tsj.compiler.backend.jvm;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class JavaPropertySynthesizer {
    SynthesisResult synthesize(final Class<?> targetClass, final boolean enabled) {
        Objects.requireNonNull(targetClass, "targetClass");
        if (!enabled) {
            return new SynthesisResult(List.of(), List.of("Property synthesis disabled by feature flag."));
        }

        final Map<String, List<Method>> getCandidates = new LinkedHashMap<>();
        final Map<String, List<Method>> isCandidates = new LinkedHashMap<>();
        final Map<String, List<Method>> setCandidates = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<String>> propertyAliases = new LinkedHashMap<>();

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
                final String canonicalProperty = canonicalPropertyKey(property);
                propertyAliases.computeIfAbsent(canonicalProperty, ignored -> new LinkedHashSet<>()).add(property);
                getCandidates.computeIfAbsent(canonicalProperty, ignored -> new ArrayList<>()).add(method);
                continue;
            }
            if (methodName.startsWith("is")
                    && methodName.length() > 2
                    && method.getParameterCount() == 0
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                final String property = normalizePropertyName(methodName.substring(2));
                final String canonicalProperty = canonicalPropertyKey(property);
                propertyAliases.computeIfAbsent(canonicalProperty, ignored -> new LinkedHashSet<>()).add(property);
                isCandidates.computeIfAbsent(canonicalProperty, ignored -> new ArrayList<>()).add(method);
                continue;
            }
            if (methodName.startsWith("set") && methodName.length() > 3 && method.getParameterCount() == 1) {
                final String property = normalizePropertyName(methodName.substring(3));
                final String canonicalProperty = canonicalPropertyKey(property);
                propertyAliases.computeIfAbsent(canonicalProperty, ignored -> new LinkedHashSet<>()).add(property);
                setCandidates.computeIfAbsent(canonicalProperty, ignored -> new ArrayList<>()).add(method);
            }
        }

        final List<String> diagnostics = new ArrayList<>();
        final List<SynthesizedProperty> properties = new ArrayList<>();

        final List<String> propertyNames = new ArrayList<>();
        propertyNames.addAll(getCandidates.keySet());
        propertyNames.addAll(isCandidates.keySet());
        propertyNames.addAll(setCandidates.keySet());
        final List<String> sortedPropertyNames = propertyNames.stream().distinct().sorted().toList();

        for (String propertyKey : sortedPropertyNames) {
            final List<String> aliases = sortedAliases(propertyAliases.get(propertyKey));
            if (aliases.size() > 1) {
                diagnostics.add(
                        "Skipped property `"
                                + propertyKey
                                + "`: conflicting accessor casing aliases "
                                + formatList(aliases)
                                + "."
                );
                continue;
            }
            final String propertyName = aliases.isEmpty() ? propertyKey : aliases.getFirst();
            final Method getter = selectGetter(
                    propertyName,
                    getCandidates.get(propertyKey),
                    isCandidates.get(propertyKey),
                    diagnostics
            );
            final Method setter = selectSetter(propertyName, setCandidates.get(propertyKey), getter, diagnostics);
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
            diagnostics.add(
                    "Skipped property `"
                            + propertyName
                            + "`: ambiguous get-method overloads "
                            + formatMethodSignatures(gets)
                            + "."
            );
            return null;
        }
        if (ises.size() > 1) {
            diagnostics.add(
                    "Skipped property `"
                            + propertyName
                            + "`: ambiguous is-method overloads "
                            + formatMethodSignatures(ises)
                            + "."
            );
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
                            + "`: conflicting get/is methods with non-boolean get return type "
                            + formatList(List.of(methodSignature(get), methodSignature(is)))
                            + "."
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
            final List<Method> exactMatches = new ArrayList<>();
            for (Method setter : setters) {
                if (setter.getParameterTypes()[0] == getter.getReturnType()) {
                    exactMatches.add(setter);
                }
            }
            if (exactMatches.size() == 1) {
                return exactMatches.getFirst();
            }
            if (exactMatches.size() > 1) {
                diagnostics.add(
                        "Skipped property `"
                                + propertyName
                                + "`: ambiguous setter overloads "
                                + formatMethodSignatures(exactMatches)
                                + "."
                );
                return null;
            }
        }
        diagnostics.add(
                "Skipped property `"
                        + propertyName
                        + "`: ambiguous setter overloads "
                        + formatMethodSignatures(setters)
                        + "."
        );
        return null;
    }

    private static String canonicalPropertyKey(final String propertyName) {
        return propertyName.toLowerCase(Locale.ROOT);
    }

    private static List<String> sortedAliases(final Set<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        final List<String> sorted = new ArrayList<>(aliases);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    private static String formatMethodSignatures(final List<Method> methods) {
        final List<String> signatures = new ArrayList<>(methods.size());
        for (Method method : methods) {
            signatures.add(methodSignature(method));
        }
        signatures.sort(String::compareTo);
        return formatList(signatures);
    }

    private static String methodSignature(final Method method) {
        return method.getName() + JavaOverloadResolver.methodDescriptor(method);
    }

    private static String formatList(final List<String> values) {
        return "[" + String.join(", ", values) + "]";
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
