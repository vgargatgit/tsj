package dev.utta;

/**
 * Nested and inner class interop testing.
 */
public final class Outer {
    private final String name;

    public Outer(String name) { this.name = name; }
    public String getName() { return name; }

    // Static nested class
    public static class Config {
        private final int value;
        public Config(int value) { this.value = value; }
        public int getValue() { return value; }
        public String describe() { return "Config(v=" + value + ")"; }
    }

    // Another static nested class
    public static class Builder {
        private String name = "default";
        private int count = 0;
        public Builder setName(String name) { this.name = name; return this; }
        public Builder setCount(int count) { this.count = count; return this; }
        public String build() { return name + ":" + count; }
    }

    // Static constant
    public static final String VERSION = "1.0.0";
    public static final int MAX_SIZE = 1024;
    public static final double PI_APPROX = 3.14159;
}
