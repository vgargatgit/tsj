package dev.tsj.compiler.frontend;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

final class JavaInteropSymbolResolver {
    private static final String CODE_CLASS_NOT_FOUND = "TSJ55-INTEROP-CLASS-NOT-FOUND";
    private static final String CODE_MEMBER_NOT_FOUND = "TSJ55-INTEROP-MEMBER-NOT-FOUND";
    private static final String CODE_VISIBILITY = "TSJ55-INTEROP-VISIBILITY";

    private static final String BINDING_CONSTRUCTOR = "$new";
    private static final String BINDING_INSTANCE_PREFIX = "$instance$";
    private static final String BINDING_INSTANCE_GET_PREFIX = "$instance$get$";
    private static final String BINDING_INSTANCE_SET_PREFIX = "$instance$set$";
    private static final String BINDING_STATIC_GET_PREFIX = "$static$get$";
    private static final String BINDING_STATIC_SET_PREFIX = "$static$set$";

    Resolution resolve(final List<FrontendInteropBinding> bindings) {
        final List<FrontendInteropSymbol> symbols = new ArrayList<>();
        final List<FrontendDiagnostic> diagnostics = new ArrayList<>();
        for (FrontendInteropBinding binding : bindings) {
            resolveBinding(binding, symbols, diagnostics);
        }
        return new Resolution(List.copyOf(symbols), List.copyOf(diagnostics));
    }

    private static void resolveBinding(
            final FrontendInteropBinding binding,
            final List<FrontendInteropSymbol> symbols,
            final List<FrontendDiagnostic> diagnostics
    ) {
        final Class<?> targetClass;
        try {
            targetClass = Class.forName(binding.className(), true, resolveClassLoader());
        } catch (final ClassNotFoundException classNotFoundException) {
            diagnostics.add(diagnostic(
                    binding,
                    CODE_CLASS_NOT_FOUND,
                    "Java interop class not found: " + binding.className() + "."
            ));
            return;
        }

        final String importedName = binding.importedName();
        if (BINDING_CONSTRUCTOR.equals(importedName)) {
            resolveConstructor(binding, targetClass, symbols, diagnostics);
            return;
        }
        if (importedName.startsWith(BINDING_INSTANCE_GET_PREFIX)) {
            final String fieldName = importedName.substring(BINDING_INSTANCE_GET_PREFIX.length());
            resolveField(binding, targetClass, fieldName, false, true, symbols, diagnostics);
            return;
        }
        if (importedName.startsWith(BINDING_INSTANCE_SET_PREFIX)) {
            final String fieldName = importedName.substring(BINDING_INSTANCE_SET_PREFIX.length());
            resolveField(binding, targetClass, fieldName, false, false, symbols, diagnostics);
            return;
        }
        if (importedName.startsWith(BINDING_STATIC_GET_PREFIX)) {
            final String fieldName = importedName.substring(BINDING_STATIC_GET_PREFIX.length());
            resolveField(binding, targetClass, fieldName, true, true, symbols, diagnostics);
            return;
        }
        if (importedName.startsWith(BINDING_STATIC_SET_PREFIX)) {
            final String fieldName = importedName.substring(BINDING_STATIC_SET_PREFIX.length());
            resolveField(binding, targetClass, fieldName, true, false, symbols, diagnostics);
            return;
        }
        if (importedName.startsWith(BINDING_INSTANCE_PREFIX)) {
            final String methodName = importedName.substring(BINDING_INSTANCE_PREFIX.length());
            resolveMethod(binding, targetClass, methodName, false, symbols, diagnostics);
            return;
        }
        if (importedName.startsWith("$")) {
            diagnostics.add(diagnostic(
                    binding,
                    CODE_MEMBER_NOT_FOUND,
                    "Unsupported Java interop binding: " + importedName + "."
            ));
            return;
        }
        resolveStaticMethodOrField(binding, targetClass, importedName, symbols, diagnostics);
    }

    private static void resolveStaticMethodOrField(
            final FrontendInteropBinding binding,
            final Class<?> targetClass,
            final String memberName,
            final List<FrontendInteropSymbol> symbols,
            final List<FrontendDiagnostic> diagnostics
    ) {
        final List<FrontendInteropSymbol> methodSymbols = new ArrayList<>();
        final List<FrontendDiagnostic> methodDiagnostics = new ArrayList<>();
        resolveMethod(binding, targetClass, memberName, true, methodSymbols, methodDiagnostics);
        if (!methodSymbols.isEmpty()) {
            symbols.addAll(methodSymbols);
            return;
        }

        final List<FrontendInteropSymbol> fieldSymbols = new ArrayList<>();
        final List<FrontendDiagnostic> fieldDiagnostics = new ArrayList<>();
        resolveField(binding, targetClass, memberName, true, true, fieldSymbols, fieldDiagnostics);
        if (!fieldSymbols.isEmpty()) {
            symbols.addAll(fieldSymbols);
            return;
        }

        final FrontendDiagnostic methodDiagnostic =
                methodDiagnostics.isEmpty() ? null : methodDiagnostics.getFirst();
        if (methodDiagnostic != null && !CODE_MEMBER_NOT_FOUND.equals(methodDiagnostic.code())) {
            diagnostics.add(methodDiagnostic);
            return;
        }
        if (!fieldDiagnostics.isEmpty()) {
            diagnostics.addAll(fieldDiagnostics);
            return;
        }
        diagnostics.addAll(methodDiagnostics);
    }

    private static void resolveConstructor(
            final FrontendInteropBinding binding,
            final Class<?> targetClass,
            final List<FrontendInteropSymbol> symbols,
            final List<FrontendDiagnostic> diagnostics
    ) {
        final Constructor<?>[] constructors = targetClass.getConstructors();
        if (constructors.length == 0) {
            if (targetClass.getDeclaredConstructors().length > 0) {
                diagnostics.add(diagnostic(
                        binding,
                        CODE_VISIBILITY,
                        "Java interop constructor exists but is not public on " + targetClass.getName() + "."
                ));
            } else {
                diagnostics.add(diagnostic(
                        binding,
                        CODE_MEMBER_NOT_FOUND,
                        "Java interop constructor not found on " + targetClass.getName() + "."
                ));
            }
            return;
        }
        final LinkedHashSet<String> descriptors = new LinkedHashSet<>();
        for (Constructor<?> constructor : constructors) {
            descriptors.add(constructorDescriptor(constructor));
        }
        symbols.add(new FrontendInteropSymbol(
                binding.filePath(),
                binding.line(),
                binding.column(),
                binding.className(),
                binding.importedName(),
                binding.localName(),
                "CONSTRUCTOR",
                targetClass.getName(),
                "<init>",
                List.copyOf(descriptors),
                "constructor overloads " + String.join(" | ", descriptors)
        ));
    }

    private static void resolveMethod(
            final FrontendInteropBinding binding,
            final Class<?> targetClass,
            final String methodName,
            final boolean expectStatic,
            final List<FrontendInteropSymbol> symbols,
            final List<FrontendDiagnostic> diagnostics
    ) {
        final List<Method> allCandidates = new ArrayList<>();
        final List<Method> nonBridgeCandidates = new ArrayList<>();
        for (Method method : targetClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) != expectStatic) {
                continue;
            }
            allCandidates.add(method);
            if (!method.isBridge() && !method.isSynthetic()) {
                nonBridgeCandidates.add(method);
            }
        }
        final List<Method> selected = nonBridgeCandidates.isEmpty() ? allCandidates : nonBridgeCandidates;
        if (selected.isEmpty()) {
            if (hasRestrictedDeclaredMethod(targetClass, methodName, expectStatic)) {
                diagnostics.add(diagnostic(
                        binding,
                        CODE_VISIBILITY,
                        "Java interop member exists but is not publicly visible: "
                                + targetClass.getName() + "#" + methodName + "."
                ));
            } else {
                diagnostics.add(diagnostic(
                        binding,
                        CODE_MEMBER_NOT_FOUND,
                        "Java interop member not found: " + targetClass.getName() + "#" + methodName + "."
                ));
            }
            return;
        }

        final LinkedHashSet<String> descriptors = new LinkedHashSet<>();
        for (Method method : selected) {
            descriptors.add(methodDescriptor(method));
        }
        symbols.add(new FrontendInteropSymbol(
                binding.filePath(),
                binding.line(),
                binding.column(),
                binding.className(),
                binding.importedName(),
                binding.localName(),
                expectStatic ? "STATIC_METHOD" : "INSTANCE_METHOD",
                targetClass.getName(),
                methodName,
                List.copyOf(descriptors),
                "method overloads " + String.join(" | ", descriptors)
        ));
    }

    private static void resolveField(
            final FrontendInteropBinding binding,
            final Class<?> targetClass,
            final String fieldName,
            final boolean expectStatic,
            final boolean getter,
            final List<FrontendInteropSymbol> symbols,
            final List<FrontendDiagnostic> diagnostics
    ) {
        final Field field;
        try {
            field = targetClass.getField(fieldName);
        } catch (final NoSuchFieldException noSuchFieldException) {
            if (hasRestrictedDeclaredField(targetClass, fieldName, expectStatic)) {
                diagnostics.add(diagnostic(
                        binding,
                        CODE_VISIBILITY,
                        "Java interop field exists but is not publicly visible: "
                                + targetClass.getName() + "#" + fieldName + "."
                ));
            } else {
                diagnostics.add(diagnostic(
                        binding,
                        CODE_MEMBER_NOT_FOUND,
                        "Java interop field not found: " + targetClass.getName() + "#" + fieldName + "."
                ));
            }
            return;
        }
        if (Modifier.isStatic(field.getModifiers()) != expectStatic) {
            diagnostics.add(diagnostic(
                    binding,
                    CODE_MEMBER_NOT_FOUND,
                    "Java interop field kind mismatch for " + targetClass.getName() + "#" + fieldName + "."
            ));
            return;
        }
        final String fieldDescriptor = descriptorFor(field.getType());
        final String memberDescriptor = getter ? "()" + fieldDescriptor : "(" + fieldDescriptor + ")V";
        symbols.add(new FrontendInteropSymbol(
                binding.filePath(),
                binding.line(),
                binding.column(),
                binding.className(),
                binding.importedName(),
                binding.localName(),
                symbolKindForField(expectStatic, getter),
                targetClass.getName(),
                fieldName,
                List.of(memberDescriptor),
                "field " + fieldName + " " + memberDescriptor
        ));
    }

    private static String symbolKindForField(final boolean expectStatic, final boolean getter) {
        if (expectStatic) {
            return getter ? "STATIC_FIELD_GET" : "STATIC_FIELD_SET";
        }
        return getter ? "INSTANCE_FIELD_GET" : "INSTANCE_FIELD_SET";
    }

    private static boolean hasRestrictedDeclaredMethod(
            final Class<?> targetClass,
            final String methodName,
            final boolean expectStatic
    ) {
        Class<?> cursor = targetClass;
        while (cursor != null) {
            final boolean classPublic = Modifier.isPublic(cursor.getModifiers());
            for (Method method : cursor.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers()) != expectStatic) {
                    continue;
                }
                if (!(classPublic && Modifier.isPublic(method.getModifiers()))) {
                    return true;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return false;
    }

    private static boolean hasRestrictedDeclaredField(
            final Class<?> targetClass,
            final String fieldName,
            final boolean expectStatic
    ) {
        Class<?> cursor = targetClass;
        while (cursor != null) {
            final boolean classPublic = Modifier.isPublic(cursor.getModifiers());
            for (Field field : cursor.getDeclaredFields()) {
                if (!field.getName().equals(fieldName)) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers()) != expectStatic) {
                    continue;
                }
                if (!(classPublic && Modifier.isPublic(field.getModifiers()))) {
                    return true;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return false;
    }

    private static FrontendDiagnostic diagnostic(
            final FrontendInteropBinding binding,
            final String code,
            final String message
    ) {
        return new FrontendDiagnostic(
                code,
                "Error",
                message,
                binding.filePath(),
                binding.line(),
                binding.column()
        );
    }

    private static String constructorDescriptor(final Constructor<?> constructor) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            builder.append(descriptorFor(parameterType));
        }
        builder.append(")V");
        return builder.toString();
    }

    private static String methodDescriptor(final Method method) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Class<?> parameterType : method.getParameterTypes()) {
            builder.append(descriptorFor(parameterType));
        }
        builder.append(")");
        builder.append(descriptorFor(method.getReturnType()));
        return builder.toString();
    }

    private static String descriptorFor(final Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) {
                return "V";
            }
            if (type == boolean.class) {
                return "Z";
            }
            if (type == byte.class) {
                return "B";
            }
            if (type == short.class) {
                return "S";
            }
            if (type == char.class) {
                return "C";
            }
            if (type == int.class) {
                return "I";
            }
            if (type == long.class) {
                return "J";
            }
            if (type == float.class) {
                return "F";
            }
            return "D";
        }
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static ClassLoader resolveClassLoader() {
        final ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            return context;
        }
        return JavaInteropSymbolResolver.class.getClassLoader();
    }

    record Resolution(
            List<FrontendInteropSymbol> symbols,
            List<FrontendDiagnostic> diagnostics
    ) {
        Resolution {
            symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
