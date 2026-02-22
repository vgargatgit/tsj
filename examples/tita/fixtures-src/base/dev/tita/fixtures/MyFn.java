package dev.tita.fixtures;

@FunctionalInterface
public interface MyFn<T, R> {
    R apply(T value);

    default String name() {
        return "fn";
    }

    static <T, R> MyFn<T, R> from(final MyFn<T, R> callback) {
        return callback;
    }

    String toString();
}
