package dev.tsj.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Codec layer between Java interop boundaries and TSJ runtime values.
 */
public final class TsjInteropCodec {
    private TsjInteropCodec() {
    }

    public static Object fromJava(final Object javaValue) {
        if (javaValue == null) {
            return null;
        }
        if (javaValue instanceof CompletableFuture<?> completableFuture) {
            return fromCompletableFuture(completableFuture);
        }
        if (javaValue.getClass().isArray()) {
            return fromJavaArray(javaValue);
        }
        if (javaValue instanceof Set<?> setValue) {
            return fromJavaCollection(setValue);
        }
        if (javaValue instanceof List<?> listValue) {
            return fromJavaList(listValue);
        }
        if (javaValue instanceof Map<?, ?> mapValue) {
            return fromJavaMap(mapValue);
        }
        if (javaValue instanceof Character character) {
            return Character.toString(character.charValue());
        }
        if (javaValue instanceof Byte byteValue) {
            return Integer.valueOf(byteValue.intValue());
        }
        if (javaValue instanceof Short shortValue) {
            return Integer.valueOf(shortValue.intValue());
        }
        if (javaValue instanceof Integer integer) {
            return integer;
        }
        if (javaValue instanceof Long longValue) {
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return Integer.valueOf(longValue.intValue());
            }
            return Double.valueOf(longValue.doubleValue());
        }
        if (javaValue instanceof Float floatValue) {
            return Double.valueOf(floatValue.doubleValue());
        }
        if (javaValue instanceof Double doubleValue) {
            if (Double.isFinite(doubleValue.doubleValue())) {
                final double raw = doubleValue.doubleValue();
                final int narrowed = (int) raw;
                if (raw == (double) narrowed) {
                    return Integer.valueOf(narrowed);
                }
            }
            return doubleValue;
        }
        return javaValue;
    }

    public static Object toJava(final Object tsValue, final Class<?> targetType) {
        Objects.requireNonNull(targetType, "targetType");
        return toJava(tsValue, (Type) targetType);
    }

    public static Object toJava(final Object tsValue, final Type targetType) {
        Objects.requireNonNull(targetType, "targetType");
        return toJavaInternal(tsValue, targetType, targetType.getTypeName());
    }

    private static Object toJavaInternal(
            final Object tsValue,
            final Type targetType,
            final String targetContext
    ) {
        if (targetType instanceof TypeVariable<?> typeVariable) {
            return toJavaTypeVariable(tsValue, typeVariable, targetContext);
        }
        if (targetType instanceof WildcardType wildcardType) {
            return toJavaWildcard(tsValue, wildcardType, targetContext);
        }
        final Type normalizedType = normalizeType(targetType);
        if (normalizedType instanceof Class<?> classType) {
            return toJavaClass(tsValue, classType, normalizedType, targetContext);
        }
        if (normalizedType instanceof ParameterizedType parameterizedType) {
            final Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> rawClass) {
                return toJavaClass(tsValue, rawClass, parameterizedType, targetContext);
            }
        }
        if (normalizedType instanceof GenericArrayType genericArrayType) {
            final Type componentType = normalizeType(genericArrayType.getGenericComponentType());
            return toJavaArray(
                    tsValue,
                    eraseType(componentType),
                    componentType,
                    targetContext
            );
        }
        throw new IllegalArgumentException(
                "Unsupported TSJ interop conversion target type `" + targetType.getTypeName() + "`."
        );
    }

    private static Object toJavaTypeVariable(
            final Object tsValue,
            final TypeVariable<?> typeVariable,
            final String targetContext
    ) {
        final Type[] bounds = typeVariable.getBounds();
        final Type primaryBound = selectPrimaryBound(bounds);
        final Object converted = toJavaInternal(tsValue, primaryBound, targetContext);
        ensureValueSatisfiesBounds(
                converted,
                bounds,
                targetContext,
                "type variable `" + typeVariable.getName() + "`"
        );
        return converted;
    }

    private static Object toJavaWildcard(
            final Object tsValue,
            final WildcardType wildcardType,
            final String targetContext
    ) {
        final Type[] lowerBounds = wildcardType.getLowerBounds();
        if (lowerBounds.length > 0) {
            final Type primaryLowerBound = selectPrimaryBound(lowerBounds);
            final Object converted = toJavaInternal(tsValue, primaryLowerBound, targetContext);
            ensureValueSatisfiesBounds(
                    converted,
                    lowerBounds,
                    targetContext,
                    "wildcard super-bound"
            );
            return converted;
        }

        final Type[] upperBounds = wildcardType.getUpperBounds();
        if (upperBounds.length > 0) {
            final Type primaryUpperBound = selectPrimaryBound(upperBounds);
            final Object converted = toJavaInternal(tsValue, primaryUpperBound, targetContext);
            ensureValueSatisfiesBounds(
                    converted,
                    upperBounds,
                    targetContext,
                    "wildcard extends-bound"
            );
            return converted;
        }

        return toJavaInternal(tsValue, Object.class, targetContext);
    }

    private static Object toJavaClass(
            final Object tsValue,
            final Class<?> targetType,
            final Type declaredType,
            final String targetContext
    ) {
        final Class<?> normalizedTarget = boxedType(targetType);
        final Type normalizedDeclaredType = normalizeType(declaredType);

        if (normalizedTarget == Object.class) {
            return toJavaDynamicObject(tsValue);
        }
        if (Optional.class.isAssignableFrom(normalizedTarget)) {
            return toJavaOptional(tsValue, optionalElementType(normalizedDeclaredType), targetContext);
        }
        if (targetType.isArray()) {
            return toJavaArray(
                    tsValue,
                    targetType.getComponentType(),
                    arrayComponentType(normalizedDeclaredType, targetType.getComponentType()),
                    targetContext
            );
        }
        if (Set.class.isAssignableFrom(normalizedTarget)) {
            return toJavaSet(tsValue, collectionElementType(normalizedDeclaredType), targetContext);
        }
        if (List.class.isAssignableFrom(normalizedTarget) || Collection.class.isAssignableFrom(normalizedTarget)) {
            return toJavaList(tsValue, collectionElementType(normalizedDeclaredType), targetContext);
        }
        if (Map.class.isAssignableFrom(normalizedTarget)) {
            return toJavaMap(
                    tsValue,
                    mapKeyType(normalizedDeclaredType),
                    mapValueType(normalizedDeclaredType),
                    targetContext
            );
        }
        if (tsValue == TsjUndefined.INSTANCE && !targetType.isPrimitive()) {
            return null;
        }
        if (tsValue != null && normalizedTarget.isAssignableFrom(tsValue.getClass())) {
            return tsValue;
        }
        if (normalizedTarget.isEnum()) {
            return toJavaEnum(tsValue, normalizedTarget);
        }
        if (CompletableFuture.class.isAssignableFrom(normalizedTarget)) {
            return toJavaCompletableFuture(tsValue, futureElementType(normalizedDeclaredType), targetContext);
        }
        if (isFunctionalInterface(normalizedTarget) && isTsCallable(tsValue)) {
            return toFunctionalInterfaceProxy(tsValue, normalizedTarget);
        }
        if (normalizedTarget == String.class) {
            if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
                return null;
            }
            return TsjRuntime.toDisplayString(tsValue);
        }
        if (normalizedTarget == Boolean.class) {
            return Boolean.valueOf(TsjRuntime.truthy(tsValue));
        }
        if (normalizedTarget == Integer.class) {
            return Integer.valueOf((int) TsjRuntime.toNumber(tsValue));
        }
        if (normalizedTarget == Double.class) {
            return Double.valueOf(TsjRuntime.toNumber(tsValue));
        }
        if (normalizedTarget == Long.class) {
            return Long.valueOf((long) TsjRuntime.toNumber(tsValue));
        }
        if (normalizedTarget == Float.class) {
            return Float.valueOf((float) TsjRuntime.toNumber(tsValue));
        }
        if (normalizedTarget == Short.class) {
            return Short.valueOf((short) TsjRuntime.toNumber(tsValue));
        }
        if (normalizedTarget == Byte.class) {
            return Byte.valueOf((byte) TsjRuntime.toNumber(tsValue));
        }
        if (normalizedTarget == Character.class) {
            final String value = TsjRuntime.toDisplayString(tsValue);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Cannot convert empty string to char.");
            }
            return Character.valueOf(value.charAt(0));
        }
        if (tsValue == null && !targetType.isPrimitive()) {
            return null;
        }
        throw new IllegalArgumentException(
                "Unsupported TSJ interop conversion to " + targetContext + "."
        );
    }

    public static Object[] toJavaArguments(final Object[] tsArgs, final Class<?>[] parameterTypes) {
        Objects.requireNonNull(tsArgs, "tsArgs");
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        if (tsArgs.length != parameterTypes.length) {
            throw new IllegalArgumentException(
                    "Interop argument arity mismatch: expected " + parameterTypes.length + " but got " + tsArgs.length + "."
            );
        }
        final Object[] converted = new Object[tsArgs.length];
        for (int index = 0; index < tsArgs.length; index++) {
            converted[index] = toJava(tsArgs[index], parameterTypes[index]);
        }
        return converted;
    }

    private static Class<?> boxedType(final Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object fromJavaArray(final Object javaArray) {
        final int length = Array.getLength(javaArray);
        final Object[] converted = new Object[length];
        for (int index = 0; index < length; index++) {
            converted[index] = fromJava(Array.get(javaArray, index));
        }
        return TsjRuntime.arrayLiteral(converted);
    }

    private static Object fromJavaCollection(final java.util.Collection<?> values) {
        final Object[] converted = new Object[values.size()];
        int index = 0;
        for (Object value : values) {
            converted[index] = fromJava(value);
            index++;
        }
        return TsjRuntime.arrayLiteral(converted);
    }

    private static Object fromJavaList(final List<?> values) {
        return fromJavaCollection(values);
    }

    private static Object fromJavaMap(final Map<?, ?> mapValue) {
        final TsjObject object = new TsjObject(null);
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            object.setOwn(key, fromJava(entry.getValue()));
        }
        return object;
    }

    private static Object fromCompletableFuture(final CompletableFuture<?> completableFuture) {
        final TsjPromise promise = new TsjPromise();
        completableFuture.whenComplete((value, throwable) -> {
            TsjRuntime.enqueueMicrotask(() -> {
                final Throwable unwrapped = unwrapCompletionThrowable(throwable);
                if (unwrapped != null) {
                    promise.rejectExternal(fromJava(unwrapped));
                } else {
                    promise.resolveExternal(fromJava(value));
                }
            });
        });
        return promise;
    }

    private static Throwable unwrapCompletionThrowable(final Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        if (throwable instanceof ExecutionException executionException && executionException.getCause() != null) {
            return executionException.getCause();
        }
        return throwable;
    }

    private static Object toJavaArray(
            final Object tsValue,
            final Class<?> componentType,
            final Type componentGenericType,
            final String targetContext
    ) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return null;
        }
        final List<Object> values = extractIterableValues(tsValue, "array");
        final Object javaArray = Array.newInstance(componentType, values.size());
        for (int index = 0; index < values.size(); index++) {
            Array.set(
                    javaArray,
                    index,
                    toJavaWithContext(
                            values.get(index),
                            componentGenericType,
                            targetContext + "[element " + index + "]"
                    )
            );
        }
        return javaArray;
    }

    private static Object toJavaDynamicObject(final Object tsValue) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return null;
        }
        if (tsValue instanceof TsjObject objectValue) {
            if (looksLikeArrayLikeObject(objectValue)) {
                return toJavaList(objectValue, Object.class, "java.util.List");
            }
            return toJavaMap(objectValue, String.class, Object.class, "java.util.Map<java.lang.String,java.lang.Object>");
        }
        if (tsValue instanceof List<?> listValue) {
            final List<Object> converted = new ArrayList<>(listValue.size());
            for (Object value : listValue) {
                converted.add(toJavaDynamicObject(value));
            }
            return converted;
        }
        if (tsValue instanceof Set<?> setValue) {
            final Set<Object> converted = new LinkedHashSet<>();
            for (Object value : setValue) {
                converted.add(toJavaDynamicObject(value));
            }
            return converted;
        }
        if (tsValue instanceof Map<?, ?> mapValue) {
            final Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), toJavaDynamicObject(entry.getValue()));
            }
            return converted;
        }
        if (tsValue.getClass().isArray()) {
            final int length = Array.getLength(tsValue);
            final List<Object> converted = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                converted.add(toJavaDynamicObject(Array.get(tsValue, index)));
            }
            return converted;
        }
        return tsValue;
    }

    private static boolean looksLikeArrayLikeObject(final TsjObject value) {
        final Object lengthValue = value.get("length");
        if (lengthValue == TsjUndefined.INSTANCE || lengthValue == null) {
            return false;
        }
        final double lengthNumber = TsjRuntime.toNumber(lengthValue);
        return !Double.isNaN(lengthNumber)
                && Double.isFinite(lengthNumber)
                && lengthNumber >= 0
                && lengthNumber == Math.rint(lengthNumber);
    }

    private static List<Object> toJavaList(final Object tsValue, final Type elementType, final String targetContext) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return null;
        }
        final List<Object> values = extractIterableValues(tsValue, "list");
        final List<Object> converted = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            converted.add(
                    toJavaWithContext(
                            values.get(index),
                            elementType,
                            targetContext + "[element " + index + "]"
                    )
            );
        }
        return converted;
    }

    private static Set<Object> toJavaSet(final Object tsValue, final Type elementType, final String targetContext) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return null;
        }
        final List<Object> values = extractIterableValues(tsValue, "set");
        final Set<Object> converted = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            converted.add(
                    toJavaWithContext(
                            values.get(index),
                            elementType,
                            targetContext + "[element " + index + "]"
                    )
            );
        }
        return converted;
    }

    private static Map<Object, Object> toJavaMap(
            final Object tsValue,
            final Type keyType,
            final Type valueType,
            final String targetContext
    ) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return null;
        }
        if (tsValue instanceof Map<?, ?> mapValue) {
            final Map<Object, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                final Object convertedKey = toJavaWithContext(
                        entry.getKey(),
                        keyType,
                        targetContext + "[key]"
                );
                final Object convertedValue = toJavaWithContext(
                        entry.getValue(),
                        valueType,
                        targetContext + "[value for key " + String.valueOf(convertedKey) + "]"
                );
                converted.put(convertedKey, convertedValue);
            }
            return converted;
        }
        if (tsValue instanceof TsjObject objectValue) {
            final Map<Object, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : objectValue.ownPropertiesView().entrySet()) {
                final Object convertedKey = toJavaWithContext(
                        entry.getKey(),
                        keyType,
                        targetContext + "[key]"
                );
                final Object convertedValue = toJavaWithContext(
                        entry.getValue(),
                        valueType,
                        targetContext + "[value for key " + String.valueOf(convertedKey) + "]"
                );
                converted.put(convertedKey, convertedValue);
            }
            return converted;
        }
        throw new IllegalArgumentException("Unsupported TSJ interop conversion to java.util.Map.");
    }

    private static Optional<Object> toJavaOptional(
            final Object tsValue,
            final Type elementType,
            final String targetContext
    ) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return Optional.empty();
        }
        if (tsValue instanceof Optional<?> optionalValue) {
            return optionalValue.map(
                    value -> toJavaWithContext(
                            value,
                            elementType,
                            targetContext + "[value]"
                    )
            );
        }
        return Optional.ofNullable(
                toJavaWithContext(
                        tsValue,
                        elementType,
                        targetContext + "[value]"
                )
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toJavaEnum(final Object tsValue, final Class<?> enumType) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return null;
        }
        if (enumType.isInstance(tsValue)) {
            return tsValue;
        }
        if (!(tsValue instanceof String enumName)) {
            throw new IllegalArgumentException(
                    "Unsupported TSJ interop conversion to enum " + enumType.getName() + "."
            );
        }
        try {
            return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), enumName);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw new IllegalArgumentException(
                    "Unknown enum constant `" + enumName + "` for " + enumType.getName() + ".",
                    illegalArgumentException
            );
        }
    }

    private static Object toJavaCompletableFuture(
            final Object tsValue,
            final Type elementType,
            final String targetContext
    ) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return null;
        }
        if (tsValue instanceof CompletableFuture<?> completableFuture) {
            return completableFuture;
        }
        if (!(tsValue instanceof TsjPromise promise)) {
            return CompletableFuture.completedFuture(
                    toJavaWithContext(
                            tsValue,
                            elementType,
                            targetContext + "[completedValue]"
                    )
            );
        }
        final CompletableFuture<Object> future = new CompletableFuture<>();
        promise.then(
                (TsjCallable) args -> {
                    final Object value = args.length > 0 ? args[0] : TsjUndefined.INSTANCE;
                    future.complete(
                            toJavaWithContext(
                                    value,
                                    elementType,
                                    targetContext + "[asyncValue]"
                            )
                    );
                    return TsjUndefined.INSTANCE;
                },
                (TsjCallable) args -> {
                    final Object reason = args.length > 0 ? args[0] : TsjUndefined.INSTANCE;
                    future.completeExceptionally(toThrowable(reason));
                    return TsjUndefined.INSTANCE;
                }
        );
        return future;
    }

    private static Throwable toThrowable(final Object reason) {
        if (reason instanceof Throwable throwable) {
            return throwable;
        }
        return new IllegalStateException(TsjRuntime.toDisplayString(reason));
    }

    private static Object toJavaWithContext(
            final Object tsValue,
            final Type targetType,
            final String conversionContext
    ) {
        try {
            return toJava(tsValue, targetType);
        } catch (final IllegalArgumentException illegalArgumentException) {
            throw new IllegalArgumentException(
                    "Generic interop conversion failed for "
                            + conversionContext
                            + " to "
                            + targetType.getTypeName()
                            + ": "
                            + illegalArgumentException.getMessage(),
                    illegalArgumentException
            );
        }
    }

    private static Type selectPrimaryBound(final Type[] bounds) {
        if (bounds == null || bounds.length == 0) {
            return Object.class;
        }
        Type fallback = normalizeType(bounds[0]);
        for (Type bound : bounds) {
            final Type normalizedBound = normalizeType(bound);
            if (eraseType(normalizedBound) != Object.class) {
                return normalizedBound;
            }
            fallback = normalizedBound;
        }
        return fallback;
    }

    private static void ensureValueSatisfiesBounds(
            final Object convertedValue,
            final Type[] bounds,
            final String targetContext,
            final String boundsContext
    ) {
        if (convertedValue == null || bounds == null || bounds.length == 0) {
            return;
        }
        for (Type bound : bounds) {
            final Type normalizedBound = normalizeType(bound);
            final Class<?> erasedBound = eraseType(normalizedBound);
            if (erasedBound == Object.class) {
                continue;
            }
            if (erasedBound.isInstance(convertedValue)) {
                continue;
            }
            throw new IllegalArgumentException(
                    "Converted value of runtime type `"
                            + convertedValue.getClass().getName()
                            + "` does not satisfy "
                            + boundsContext
                            + " bound `"
                            + normalizedBound.getTypeName()
                            + "` for "
                            + targetContext
                            + "."
            );
        }
    }

    private static Type normalizeType(final Type type) {
        if (type instanceof WildcardType wildcardType) {
            final Type[] lowerBounds = wildcardType.getLowerBounds();
            if (lowerBounds.length > 0) {
                return normalizeType(lowerBounds[0]);
            }
            final Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0) {
                return normalizeType(upperBounds[0]);
            }
            return Object.class;
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            final Type[] bounds = typeVariable.getBounds();
            if (bounds.length > 0) {
                return normalizeType(bounds[0]);
            }
            return Object.class;
        }
        return type;
    }

    private static Class<?> eraseType(final Type type) {
        final Type normalizedType = normalizeType(type);
        if (normalizedType instanceof Class<?> classType) {
            return classType;
        }
        if (normalizedType instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> rawType) {
            return rawType;
        }
        if (normalizedType instanceof GenericArrayType genericArrayType) {
            return Array.newInstance(eraseType(genericArrayType.getGenericComponentType()), 0).getClass();
        }
        return Object.class;
    }

    private static Type collectionElementType(final Type declaredType) {
        if (declaredType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length >= 1) {
            return normalizeType(parameterizedType.getActualTypeArguments()[0]);
        }
        return Object.class;
    }

    private static Type mapKeyType(final Type declaredType) {
        if (declaredType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length >= 1) {
            return normalizeType(parameterizedType.getActualTypeArguments()[0]);
        }
        return String.class;
    }

    private static Type mapValueType(final Type declaredType) {
        if (declaredType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length >= 2) {
            return normalizeType(parameterizedType.getActualTypeArguments()[1]);
        }
        return Object.class;
    }

    private static Type optionalElementType(final Type declaredType) {
        if (declaredType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length >= 1) {
            return normalizeType(parameterizedType.getActualTypeArguments()[0]);
        }
        return Object.class;
    }

    private static Type futureElementType(final Type declaredType) {
        if (declaredType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length >= 1) {
            return normalizeType(parameterizedType.getActualTypeArguments()[0]);
        }
        return Object.class;
    }

    private static Type arrayComponentType(final Type declaredType, final Class<?> componentClass) {
        if (declaredType instanceof GenericArrayType genericArrayType) {
            return normalizeType(genericArrayType.getGenericComponentType());
        }
        if (declaredType instanceof Class<?> classType && classType.isArray()) {
            return classType.getComponentType();
        }
        return componentClass;
    }

    private static List<Object> extractIterableValues(final Object tsValue, final String targetName) {
        if (tsValue instanceof List<?> listValue) {
            return new ArrayList<>(listValue);
        }
        if (tsValue.getClass().isArray()) {
            final int length = Array.getLength(tsValue);
            final List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(Array.get(tsValue, index));
            }
            return values;
        }
        if (tsValue instanceof TsjObject objectValue) {
            return extractArrayLikeValues(objectValue, targetName);
        }
        throw new IllegalArgumentException(
                "Unsupported TSJ interop conversion to java " + targetName + " from "
                        + tsValue.getClass().getName() + "."
        );
    }

    private static List<Object> extractArrayLikeValues(final TsjObject arrayLike, final String targetName) {
        final double lengthNumber = TsjRuntime.toNumber(arrayLike.get("length"));
        if (Double.isNaN(lengthNumber) || lengthNumber < 0 || lengthNumber != Math.rint(lengthNumber)) {
            throw new IllegalArgumentException(
                    "Unsupported TSJ interop conversion to java " + targetName + ": array-like length is invalid."
            );
        }
        if (lengthNumber > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Unsupported TSJ interop conversion to java " + targetName + ": length is too large."
            );
        }
        final int length = (int) lengthNumber;
        final List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(arrayLike.get(Integer.toString(index)));
        }
        return values;
    }

    private static boolean isTsCallable(final Object value) {
        return value instanceof TsjCallable || value instanceof TsjCallableWithThis;
    }

    private static boolean isFunctionalInterface(final Class<?> type) {
        return resolveFunctionalMethod(type) != null;
    }

    private static Object toFunctionalInterfaceProxy(final Object tsCallable, final Class<?> interfaceType) {
        final Method functionalMethod = resolveFunctionalMethod(interfaceType);
        if (functionalMethod == null) {
            throw new IllegalArgumentException(
                    "Unsupported TSJ interop conversion to " + interfaceType.getName() + "."
            );
        }
        final InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            if (!isFunctionalMethodInvocation(functionalMethod, method)) {
                throw new UnsupportedOperationException(
                        "Unsupported functional interface method invocation: " + method.getName()
                );
            }
            final Object[] javaArgs = args == null ? new Object[0] : args;
            final Object[] tsArgs = new Object[javaArgs.length];
            for (int index = 0; index < javaArgs.length; index++) {
                tsArgs[index] = fromJava(javaArgs[index]);
            }
            final Object tsResult = invokeTsCallable(tsCallable, tsArgs);
            if (method.getReturnType() == void.class) {
                return null;
            }
            return toJava(tsResult, method.getGenericReturnType());
        };
        return Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{interfaceType},
                handler
        );
    }

    private static Object invokeObjectMethod(final Object proxy, final Method method, final Object[] args) {
        final String name = method.getName();
        if ("toString".equals(name)) {
            return "TSJ functional proxy(" + proxy.getClass().getInterfaces()[0].getName() + ")";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == (args == null || args.length == 0 ? null : args[0]);
        }
        throw new UnsupportedOperationException("Unsupported Object method: " + name);
    }

    private static boolean isFunctionalMethodInvocation(final Method functionalMethod, final Method invocationMethod) {
        if (!functionalMethod.getName().equals(invocationMethod.getName())) {
            return false;
        }
        return Arrays.equals(functionalMethod.getParameterTypes(), invocationMethod.getParameterTypes());
    }

    private static Method resolveFunctionalMethod(final Class<?> type) {
        if (!type.isInterface()) {
            return null;
        }
        Method candidate = null;
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || isObjectContractMethod(method)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            if (candidate == null) {
                candidate = method;
                continue;
            }
            if (!isFunctionalMethodInvocation(candidate, method)) {
                return null;
            }
        }
        return candidate;
    }

    private static boolean isObjectContractMethod(final Method method) {
        final String name = method.getName();
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if ("toString".equals(name)) {
            return parameterTypes.length == 0 && method.getReturnType() == String.class;
        }
        if ("hashCode".equals(name)) {
            return parameterTypes.length == 0 && method.getReturnType() == int.class;
        }
        if ("equals".equals(name)) {
            return parameterTypes.length == 1
                    && parameterTypes[0] == Object.class
                    && method.getReturnType() == boolean.class;
        }
        return false;
    }

    private static Object invokeTsCallable(final Object callable, final Object[] args) {
        if (callable instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(TsjRuntime.undefined(), args);
        }
        return ((TsjCallable) callable).call(args);
    }
}
