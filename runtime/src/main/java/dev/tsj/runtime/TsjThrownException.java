package dev.tsj.runtime;

/**
 * Internal runtime wrapper for `throw` values that are not Java exceptions.
 */
public final class TsjThrownException extends RuntimeException {
    private final Object thrownValue;

    public TsjThrownException(final Object thrownValue) {
        super("TSJ thrown value: " + TsjRuntime.toDisplayString(thrownValue));
        this.thrownValue = thrownValue;
    }

    public Object thrownValue() {
        return thrownValue;
    }
}
