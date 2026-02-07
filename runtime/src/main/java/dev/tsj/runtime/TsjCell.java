package dev.tsj.runtime;

/**
 * Mutable cell used to preserve closure-captured variable identity.
 */
public final class TsjCell {
    private Object value;

    public TsjCell(final Object value) {
        this.value = value;
    }

    public Object get() {
        return value;
    }

    public void set(final Object value) {
        this.value = value;
    }
}
