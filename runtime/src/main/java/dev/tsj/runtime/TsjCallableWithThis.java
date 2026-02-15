package dev.tsj.runtime;

/**
 * Callable that can receive a dynamic `this` binding from the call site.
 */
@FunctionalInterface
public interface TsjCallableWithThis extends TsjCallable {
    Object callWithThis(Object thisValue, Object... args);

    @Override
    default Object call(final Object... args) {
        return callWithThis(TsjRuntime.undefined(), args);
    }
}
