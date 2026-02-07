package dev.tsj.runtime;

/**
 * Callable runtime function representation for TSJ-generated closures.
 */
@FunctionalInterface
public interface TsjCallable {
    Object call(Object... args);
}
