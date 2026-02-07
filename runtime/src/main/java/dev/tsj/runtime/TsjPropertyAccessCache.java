package dev.tsj.runtime;

/**
 * Monomorphic inline cache for a property read call site.
 */
public final class TsjPropertyAccessCache {
    private final String expectedKey;
    private boolean initialized;
    private Class<?> cachedClass;
    private long cachedShapeToken;
    private Object cachedValue;

    public TsjPropertyAccessCache(final String expectedKey) {
        this.expectedKey = expectedKey;
        this.initialized = false;
        this.cachedClass = null;
        this.cachedShapeToken = 0L;
        this.cachedValue = null;
    }

    public Object read(final TsjObject target, final String key) {
        if (!expectedKey.equals(key)) {
            return target.get(key);
        }
        if (initialized
                && cachedClass == target.getClass()
                && cachedShapeToken == target.shapeToken()
                && target.hasOwn(key)) {
            return cachedValue;
        }

        final Object value = target.get(key);
        if (target.hasOwn(key)) {
            initialized = true;
            cachedClass = target.getClass();
            cachedShapeToken = target.shapeToken();
            cachedValue = value;
        } else {
            initialized = false;
            cachedClass = null;
            cachedShapeToken = 0L;
            cachedValue = null;
        }
        return value;
    }
}
