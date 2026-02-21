package dev.tsj.compiler.backend.jvm.fixtures;

import java.util.concurrent.CompletableFuture;
import java.util.function.IntUnaryOperator;

/**
 * JVM interop fixture target used by TSJ-29 backend tests.
 */
public final class InteropFixtureType {
    public static int GLOBAL = 100;
    public int value;

    public InteropFixtureType(final int seed) {
        this.value = seed;
    }

    public int add(final int delta) {
        this.value += delta;
        return this.value;
    }

    public static String pick(final int ignored) {
        return "int";
    }

    public static String pick(final double ignored) {
        return "double";
    }

    public static String join(final String prefix, final String... values) {
        final StringBuilder builder = new StringBuilder(prefix);
        for (String value : values) {
            builder.append(":").append(value);
        }
        return builder.toString();
    }

    public static int applyOperator(final IntUnaryOperator operator, final int seed) {
        return operator.applyAsInt(seed);
    }

    public static CompletableFuture<String> upperAsync(final String value) {
        return CompletableFuture.completedFuture(value.toUpperCase());
    }

    public static String ambiguous(final java.io.Serializable value) {
        return "serializable";
    }

    public static String ambiguous(final Comparable<?> value) {
        return "comparable";
    }
}
