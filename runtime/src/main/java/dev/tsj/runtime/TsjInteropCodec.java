package dev.tsj.runtime;

import java.util.Objects;

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
        final Class<?> normalizedTarget = boxedType(targetType);

        if (normalizedTarget == Object.class) {
            return tsValue == TsjUndefined.INSTANCE ? null : tsValue;
        }
        if (tsValue == TsjUndefined.INSTANCE && !targetType.isPrimitive()) {
            return null;
        }
        if (tsValue != null && normalizedTarget.isAssignableFrom(tsValue.getClass())) {
            return tsValue;
        }
        if (normalizedTarget == String.class) {
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
                "Unsupported TSJ interop conversion to " + targetType.getName() + "."
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
}
