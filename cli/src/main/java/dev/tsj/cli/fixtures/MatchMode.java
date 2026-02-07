package dev.tsj.cli.fixtures;

import java.util.Locale;

/**
 * Output match mode for fixture expectations.
 */
public enum MatchMode {
    EXACT,
    CONTAINS;

    static MatchMode parse(final String value, final MatchMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "EXACT" -> EXACT;
            case "CONTAINS" -> CONTAINS;
            default -> throw new IllegalArgumentException("Unsupported match mode: " + value);
        };
    }
}
