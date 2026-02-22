package dev.tita.fixtures;

public final class Conflict {
    private Conflict() {
    }

    public static String marker() {
        return "duplicate";
    }

    public static String marker(final String prefix) {
        return prefix + ":" + marker();
    }
}
