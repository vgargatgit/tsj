package dev.tsj.compiler.backend.jvm;

import java.util.List;
import java.util.Objects;

/**
 * Extracted decorator model from TS source/module graph.
 *
 * @param classes decorated class metadata entries
 */
public record TsDecoratorModel(
        List<TsDecoratedClass> classes
) {
    public TsDecoratorModel {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
    }
}
