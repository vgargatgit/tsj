package dev.tsj.compiler.backend.jvm;

import java.util.List;
import java.util.Objects;

/**
 * Decorator metadata for one TS class field declaration.
 *
 * @param fieldName field identifier
 * @param line 1-based field declaration line
 * @param span source span covering the field declaration
 * @param visibility field visibility
 * @param typeAnnotation raw TS type annotation (without leading colon), nullable
 * @param decorators decorators attached to this field
 */
public record TsDecoratedField(
        String fieldName,
        int line,
        TsSourceSpan span,
        TsVisibility visibility,
        String typeAnnotation,
        List<TsDecoratorUse> decorators
) {
    public TsDecoratedField {
        fieldName = Objects.requireNonNull(fieldName, "fieldName");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        span = Objects.requireNonNull(span, "span");
        visibility = Objects.requireNonNull(visibility, "visibility");
        if (typeAnnotation != null) {
            typeAnnotation = typeAnnotation.trim();
            if (typeAnnotation.isEmpty()) {
                typeAnnotation = null;
            }
        }
        decorators = List.copyOf(Objects.requireNonNull(decorators, "decorators"));
    }

    public TsDecoratedField(
            final String fieldName,
            final int line,
            final List<TsDecoratorUse> decorators
    ) {
        this(fieldName, line, TsSourceSpan.singleLine(line), TsVisibility.PUBLIC, null, decorators);
    }
}
