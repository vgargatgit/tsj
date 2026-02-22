package dev.tita.fixtures;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Overloads {
    public static String pick(final int value) {
        return "int:" + value;
    }

    public static String pick(final long value) {
        return "long:" + value;
    }

    public static String pick(final Integer value) {
        return "integer:" + value;
    }

    public static String pick(final Number value) {
        return "number:" + value;
    }

    public static String pick(final int... values) {
        return "varargs:" + values.length;
    }

    public static String pick(final String value) {
        return "string:" + value;
    }

    public static String pick(final Object value) {
        return "object:" + String.valueOf(value);
    }

    public String join(final String left, final String right) {
        return "ss:" + left + ":" + right;
    }

    public String join(final Object left, final Object right) {
        return "oo:" + String.valueOf(left) + ":" + String.valueOf(right);
    }

    @Nullable
    public static String maybeNull(final boolean enabled) {
        if (enabled) {
            return "value";
        }
        return null;
    }

    @NotNull
    public static String require(@NotNull final String value) {
        return value;
    }
}
