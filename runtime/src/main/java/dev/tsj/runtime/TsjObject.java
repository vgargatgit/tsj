package dev.tsj.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic object with own properties and prototype pointer.
 */
public class TsjObject {
    private TsjObject prototype;
    private final Map<String, Object> ownProperties;
    private long shapeToken;

    public TsjObject(final TsjObject prototype) {
        this.prototype = prototype;
        this.ownProperties = new LinkedHashMap<>();
        this.shapeToken = 1L;
    }

    public TsjObject prototype() {
        return prototype;
    }

    public void setPrototype(final TsjObject prototype) {
        ensureNoPrototypeCycle(prototype);
        this.prototype = prototype;
        shapeToken++;
    }

    public boolean hasOwn(final String key) {
        return ownProperties.containsKey(key);
    }

    public Object getOwn(final String key) {
        return ownProperties.getOrDefault(key, TsjUndefined.INSTANCE);
    }

    public void setOwn(final String key, final Object value) {
        ownProperties.put(key, value);
        shapeToken++;
    }

    public boolean deleteOwn(final String key) {
        if (ownProperties.containsKey(key)) {
            ownProperties.remove(key);
            shapeToken++;
        }
        return true;
    }

    public Object get(final String key) {
        if (ownProperties.containsKey(key)) {
            return ownProperties.get(key);
        }
        if (prototype != null) {
            return prototype.get(key);
        }
        return TsjUndefined.INSTANCE;
    }

    public void set(final String key, final Object value) {
        ownProperties.put(key, value);
        shapeToken++;
    }

    public long shapeToken() {
        return shapeToken;
    }

    public Map<String, Object> ownPropertiesView() {
        return Collections.unmodifiableMap(ownProperties);
    }

    private void ensureNoPrototypeCycle(final TsjObject candidatePrototype) {
        TsjObject cursor = candidatePrototype;
        while (cursor != null) {
            if (cursor == this) {
                throw new IllegalArgumentException("Prototype cycle detected.");
            }
            cursor = cursor.prototype;
        }
    }
}
