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
 * @param decorators class-level decorators
 * @param fields field-level decorator metadata
 * @param methods method-level decorator metadata
 */
public record TsDecoratedClass(
        Path sourceFile,
        String className,
        int line,
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
        decorators = List.copyOf(Objects.requireNonNull(decorators, "decorators"));
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    }
}
