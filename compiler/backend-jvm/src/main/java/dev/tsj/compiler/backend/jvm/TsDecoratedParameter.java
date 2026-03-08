package dev.tsj.compiler.backend.jvm;

import java.util.List;
import java.util.Objects;

/**
 * Decorator metadata for one TS method parameter.
 *
 * @param index 0-based parameter index in declaration order
 * @param name parameter identifier
 * @param decorators decorators attached to this parameter
 * @param typeAnnotation raw TS type annotation (without leading colon), or {@code null} when absent
 */
public record TsDecoratedParameter(
        int index,
        String name,
        List<TsDecoratorUse> decorators,
        String typeAnnotation
) {
    public TsDecoratedParameter {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        name = Objects.requireNonNull(name, "name");
        decorators = List.copyOf(Objects.requireNonNull(decorators, "decorators"));
        if (typeAnnotation != null) {
            typeAnnotation = typeAnnotation.trim();
            if (typeAnnotation.isEmpty()) {
                typeAnnotation = null;
            }
        }
    }

    public TsDecoratedParameter(
            final int index,
            final String name,
            final List<TsDecoratorUse> decorators
    ) {
        this(index, name, decorators, null);
    }
}
