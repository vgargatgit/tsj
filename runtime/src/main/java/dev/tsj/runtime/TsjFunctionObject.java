package dev.tsj.runtime;

import java.util.Objects;

/**
 * Callable object that can also carry own properties like a JavaScript function object.
 */
public final class TsjFunctionObject extends TsjObject implements TsjCallableWithThis {
    private final TsjCallableWithThis body;

    public TsjFunctionObject(final TsjCallableWithThis body) {
        super(null);
        this.body = Objects.requireNonNull(body, "body");
    }

    @Override
    public Object callWithThis(final Object thisValue, final Object... args) {
        return body.callWithThis(thisValue, args);
    }
}
