package dev.tsj.compiler.backend.jvm.fixtures;

public final class InteropNullabilityFixtureType {
    private InteropNullabilityFixtureType() {
    }

    public static String requireNonNull(@org.jetbrains.annotations.NotNull final String value) {
        return value.toUpperCase(java.util.Locale.ROOT);
    }
}
