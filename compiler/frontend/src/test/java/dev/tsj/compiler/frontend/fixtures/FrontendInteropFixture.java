package dev.tsj.compiler.frontend.fixtures;

/**
 * Java interop fixture for frontend descriptor-backed symbol resolution tests.
 */
public final class FrontendInteropFixture {
    public static final int PUBLIC_COUNT = 7;
    static final int HIDDEN_COUNT = 3;

    private FrontendInteropFixture() {
    }

    public static String echo(final String value) {
        return value;
    }

    static String hiddenEcho(final String value) {
        return value;
    }
}
