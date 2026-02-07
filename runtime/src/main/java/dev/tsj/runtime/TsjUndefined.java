package dev.tsj.runtime;

/**
 * Singleton sentinel for JavaScript undefined value in TSJ runtime.
 */
public enum TsjUndefined {
    /**
     * Single undefined instance.
     */
    INSTANCE;

    @Override
    public String toString() {
        return "undefined";
    }
}
