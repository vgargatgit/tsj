package dev.tsj.compiler.backend.jvm.fixtures.boot;

public final class StrictAppInteropProbe {
    private StrictAppInteropProbe() {
    }

    public static String describeApplicationClass(final Class<?> applicationClass) {
        return applicationClass.getName();
    }
}
