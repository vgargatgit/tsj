package dev.tsj.compiler.backend.jvm;

/**
 * JVM-relevant visibility extracted from a TS declaration.
 */
public enum TsVisibility {
    PUBLIC("public"),
    PROTECTED("protected"),
    PRIVATE("private");

    private final String wireValue;

    TsVisibility(final String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static TsVisibility fromWireValue(final String wireValue) {
        if (wireValue == null || wireValue.isBlank() || "public".equals(wireValue)) {
            return PUBLIC;
        }
        if ("protected".equals(wireValue)) {
            return PROTECTED;
        }
        if ("private".equals(wireValue)) {
            return PRIVATE;
        }
        throw new IllegalArgumentException("Unsupported TS visibility: " + wireValue);
    }
}
