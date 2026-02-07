package dev.tsj.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Static descriptor for a module in registry.
 */
public record TsjModuleDescriptor(
        String id,
        List<String> dependencies,
        TsjModuleInitializer initializer
) {
    public TsjModuleDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(initializer, "initializer");
        dependencies = List.copyOf(dependencies);
    }
}
