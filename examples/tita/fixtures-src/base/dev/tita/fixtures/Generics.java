package dev.tita.fixtures;

import java.util.List;
import java.util.Map;

public final class Generics {
    private Generics() {
    }

    public static <T extends CharSequence & Comparable<T>> T echo(final T value) {
        return value;
    }

    public static List<? extends Number> nums() {
        return List.of(1, 2, 3);
    }

    public static Map<String, List<Integer>> map() {
        return Map.of("numbers", List.of(1, 2, 3));
    }

    public static final class Box<T> {
        private T value;

        public T get() {
            return value;
        }

        public void set(final T next) {
            this.value = next;
        }
    }
}
