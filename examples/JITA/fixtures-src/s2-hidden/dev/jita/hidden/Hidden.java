package dev.jita.hidden;

public final class Hidden {
    private Hidden() {
    }

    static String packagePrivatePing() {
        return "package-private";
    }

    private static String privatePing() {
        return "private";
    }
}
