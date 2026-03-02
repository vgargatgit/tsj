package dev.tsj.runtime;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal symbol value model for TSJ runtime.
 */
public final class TsjSymbol {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final long id;
    private final String description;

    private TsjSymbol(final long id, final String description) {
        this.id = id;
        this.description = description;
    }

    public static TsjSymbol create(final String description) {
        return new TsjSymbol(NEXT_ID.getAndIncrement(), description);
    }

    public String propertyKey() {
        return "@@symbol:" + id;
    }

    @Override
    public String toString() {
        final String label = description == null ? "" : description;
        return "Symbol(" + label + ")";
    }
}

