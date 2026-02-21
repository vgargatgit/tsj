package dev.tsj.compiler.backend.jvm;

import java.util.List;
import java.util.Objects;

/**
 * Decorator metadata for one TS class method/constructor.
 *
 * @param methodName method name (`constructor` for ctor)
 * @param line 1-based method declaration line
 * @param parameters parameter metadata including parameter-level decorators
 * @param constructor true when method models class constructor
 * @param decorators decorators attached to this method
 */
public record TsDecoratedMethod(
        String methodName,
        int line,
        List<TsDecoratedParameter> parameters,
        boolean constructor,
        List<TsDecoratorUse> decorators
) {
    public TsDecoratedMethod {
        methodName = Objects.requireNonNull(methodName, "methodName");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        decorators = List.copyOf(Objects.requireNonNull(decorators, "decorators"));
    }

    public int parameterCount() {
        return parameters.size();
    }
}
