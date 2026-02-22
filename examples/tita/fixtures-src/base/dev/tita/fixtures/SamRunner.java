package dev.tita.fixtures;

public final class SamRunner {
    private SamRunner() {
    }

    public static MyFn<String, String> prefixer() {
        return value -> value + "-ok";
    }

    public static String runWithBuiltIn(final String input) {
        return prefixer().apply(input);
    }

    public static String run(final MyFn<String, String> callback, final String input) {
        return callback.apply(input);
    }
}
