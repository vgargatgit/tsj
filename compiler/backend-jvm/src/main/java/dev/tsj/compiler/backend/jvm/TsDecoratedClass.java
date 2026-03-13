package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Decorator metadata for one TS top-level class declaration.
 *
 * @param sourceFile absolute source path
 * @param className class identifier
 * @param line 1-based class declaration line
 * @param span source span covering the class declaration
 * @param genericParameters raw class generic parameter declarations
 * @param extendsType raw extends clause, nullable
 * @param implementsTypes raw implements clause entries
 * @param decorators class-level decorators
 * @param fields field-level decorator metadata
 * @param methods method-level decorator metadata
 */
public record TsDecoratedClass(
        Path sourceFile,
        String className,
        int line,
        TsSourceSpan span,
        List<String> genericParameters,
        String extendsType,
        List<String> implementsTypes,
        List<TsDecoratorUse> decorators,
        List<TsDecoratedField> fields,
        List<TsDecoratedMethod> methods
) {
    public TsDecoratedClass {
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        className = Objects.requireNonNull(className, "className");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        span = Objects.requireNonNull(span, "span");
        genericParameters = List.copyOf(Objects.requireNonNull(genericParameters, "genericParameters"));
        if (extendsType != null) {
            extendsType = extendsType.trim();
            if (extendsType.isEmpty()) {
                extendsType = null;
            }
        }
        implementsTypes = List.copyOf(Objects.requireNonNull(implementsTypes, "implementsTypes"));
        decorators = List.copyOf(Objects.requireNonNull(decorators, "decorators"));
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    }

    public TsDecoratedClass(
            final Path sourceFile,
            final String className,
            final int line,
            final List<TsDecoratorUse> decorators,
            final List<TsDecoratedField> fields,
            final List<TsDecoratedMethod> methods
    ) {
        this(
                sourceFile,
                className,
                line,
                TsSourceSpan.singleLine(line),
                List.of(),
                null,
                List.of(),
                decorators,
                fields,
                methods
        );
    }
}
