package dev.tsj.compiler.backend.jvm;

import java.util.List;
import java.util.Objects;

/**
 * Decorator metadata for one TS class method/constructor.
 *
 * @param methodName method name (`constructor` for ctor)
 * @param line 1-based method declaration line
 * @param span source span covering the method/constructor declaration
 * @param visibility method visibility
 * @param genericParameters raw generic parameter declarations
 * @param returnTypeAnnotation raw TS return type annotation, nullable
 * @param parameters parameter metadata including parameter-level decorators
 * @param constructor true when method models class constructor
 * @param decorators decorators attached to this method
 */
public record TsDecoratedMethod(
        String methodName,
        int line,
        TsSourceSpan span,
        TsVisibility visibility,
        List<String> genericParameters,
        String returnTypeAnnotation,
        List<TsDecoratedParameter> parameters,
        boolean constructor,
        List<TsDecoratorUse> decorators
) {
    public TsDecoratedMethod {
        methodName = Objects.requireNonNull(methodName, "methodName");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        span = Objects.requireNonNull(span, "span");
        visibility = Objects.requireNonNull(visibility, "visibility");
        genericParameters = List.copyOf(Objects.requireNonNull(genericParameters, "genericParameters"));
        if (returnTypeAnnotation != null) {
            returnTypeAnnotation = returnTypeAnnotation.trim();
            if (returnTypeAnnotation.isEmpty()) {
                returnTypeAnnotation = null;
            }
        }
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        decorators = List.copyOf(Objects.requireNonNull(decorators, "decorators"));
    }

    public int parameterCount() {
        return parameters.size();
    }

    public TsDecoratedMethod(
            final String methodName,
            final int line,
            final List<TsDecoratedParameter> parameters,
            final boolean constructor,
            final List<TsDecoratorUse> decorators
    ) {
        this(
                methodName,
                line,
                TsSourceSpan.singleLine(line),
                TsVisibility.PUBLIC,
                List.of(),
                null,
                parameters,
                constructor,
                decorators
        );
    }
}
