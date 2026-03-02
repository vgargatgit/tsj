package dev.xtta.complex;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

/**
 * Extreme interop torture: deep generics, complex overloads, varargs,
 * arrays, builder pattern, enums with methods, exception hierarchies,
 * default interface methods, static interface methods, inner classes.
 */
public final class TortureLib {
    private TortureLib() {}

    // --- Deep generics ---
    public static <T> List<T> wrapInList(T item) {
        return List.of(item);
    }

    public static Map<String, List<Integer>> deepGenericMap() {
        Map<String, List<Integer>> result = new HashMap<>();
        result.put("evens", Arrays.asList(2, 4, 6));
        result.put("odds", Arrays.asList(1, 3, 5));
        return result;
    }

    // --- Complex overloads ---
    public static String identify(int x) { return "int:" + x; }
    public static String identify(double x) { return "double:" + x; }
    public static String identify(String x) { return "string:" + x; }
    public static String identify(Object x) { return "object:" + x; }

    // --- Varargs ---
    public static int sumVarargs(int... nums) {
        int total = 0;
        for (int n : nums) total += n;
        return total;
    }

    public static String joinVarargs(String separator, String... parts) {
        return String.join(separator, parts);
    }

    // --- Arrays ---
    public static int[] createIntArray(int size) {
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) arr[i] = i * 10;
        return arr;
    }

    public static String[] createStringArray(String... items) {
        return items;
    }

    public static int arraySum(int[] arr) {
        int total = 0;
        for (int n : arr) total += n;
        return total;
    }

    // --- Builder pattern (fluent chaining) ---
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final StringBuilder sb = new StringBuilder();
        public Builder add(String part) { sb.append(part); return this; }
        public Builder separator(String sep) { sb.append(sep); return this; }
        public String build() { return sb.toString(); }
    }

    // --- Exception hierarchy ---
    public static String riskyOperation(boolean shouldFail) {
        if (shouldFail) {
            throw new IllegalArgumentException("Operation failed intentionally");
        }
        return "success";
    }

    // --- Enum with methods ---
    public enum Priority {
        LOW(1), MEDIUM(5), HIGH(10);

        private final int weight;
        Priority(int weight) { this.weight = weight; }
        public int getWeight() { return weight; }
        public String label() { return name().toLowerCase(); }
    }

    public static Priority getPriority(String name) {
        return Priority.valueOf(name.toUpperCase());
    }

    public static int priorityWeight(Priority p) {
        return p.getWeight();
    }

    // --- Static nested class ---
    public static final class Pair<A, B> {
        private final A first;
        private final B second;
        public Pair(A first, B second) { this.first = first; this.second = second; }
        public A getFirst() { return first; }
        public B getSecond() { return second; }
        @Override public String toString() { return "(" + first + "," + second + ")"; }
    }

    public static <A, B> Pair<A, B> pair(A a, B b) {
        return new Pair<>(a, b);
    }

    // --- Null handling ---
    public static String nullSafe(String input) {
        if (input == null) return "was-null";
        return input.toUpperCase();
    }

    // --- Multiple return type methods ---
    public static Object polymorphicReturn(int choice) {
        switch (choice) {
            case 0: return "string-result";
            case 1: return 42;
            case 2: return true;
            default: return null;
        }
    }
}
