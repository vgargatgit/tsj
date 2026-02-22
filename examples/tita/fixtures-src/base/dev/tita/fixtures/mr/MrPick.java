package dev.tita.fixtures.mr;

public final class MrPick {
    private MrPick() {
    }

    public static String marker() {
        return "base";
    }

    public static String marker(final String prefix) {
        return prefix + ":" + marker();
    }
}
