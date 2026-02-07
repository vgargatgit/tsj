package dev.tsj.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic object with own properties and prototype pointer.
 */
public final class TsjObject {
    private final TsjObject prototype;
    private final Map<String, Object> ownProperties;

    public TsjObject(final TsjObject prototype) {
        this.prototype = prototype;
        this.ownProperties = new LinkedHashMap<>();
    }

    public TsjObject prototype() {
        return prototype;
    }

    public boolean hasOwn(final String key) {
        return ownProperties.containsKey(key);
    }

    public Object getOwn(final String key) {
        return ownProperties.get(key);
    }

    public void setOwn(final String key, final Object value) {
        ownProperties.put(key, value);
    }

    public Object get(final String key) {
        if (ownProperties.containsKey(key)) {
            return ownProperties.get(key);
        }
        if (prototype != null) {
            return prototype.get(key);
        }
        return null;
    }

    public void set(final String key, final Object value) {
        ownProperties.put(key, value);
    }
}
