package dev.tsj.runtime;

/**
 * Monomorphic inline cache for a property read call site.
 */
public final class TsjPropertyAccessCache {
    private final String expectedKey;
    private boolean initialized;
    private Class<?> cachedClass;
    private long cachedShapeToken;

    public TsjPropertyAccessCache(final String expectedKey) {
        this.expectedKey = expectedKey;
        this.initialized = false;
        this.cachedClass = null;
        this.cachedShapeToken = 0L;
    }

    public Object read(final TsjObject target, final String key) {
        if (!expectedKey.equals(key)) {
            return target.get(key);
        }
        if (initialized
                && cachedClass == target.getClass()
                && cachedShapeToken == target.shapeToken()
                && target.hasOwn(key)) {
            return target.getOwn(key);
        }

        final Object value = target.get(key);
        if (target.hasOwn(key)) {
            initialized = true;
            cachedClass = target.getClass();
            cachedShapeToken = target.shapeToken();
        } else {
            initialized = false;
            cachedClass = null;
            cachedShapeToken = 0L;
        }
        return value;
    }
}
