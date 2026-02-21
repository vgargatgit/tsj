package dev.tsj.compiler.backend.jvm;

import java.util.List;
import java.util.Objects;

/**
 * Decorator metadata for one TS class field declaration.
 *
 * @param fieldName field identifier
 * @param line 1-based field declaration line
 * @param decorators decorators attached to this field
 */
public record TsDecoratedField(
        String fieldName,
        int line,
        List<TsDecoratorUse> decorators
) {
    public TsDecoratedField {
        fieldName = Objects.requireNonNull(fieldName, "fieldName");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        decorators = List.copyOf(Objects.requireNonNull(decorators, "decorators"));
    }
}
