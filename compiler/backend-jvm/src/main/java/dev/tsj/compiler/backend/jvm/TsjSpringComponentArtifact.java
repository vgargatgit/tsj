package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Generated TS-authored Spring component adapter sources.
 */
public record TsjSpringComponentArtifact(
        Path outputDirectory,
        List<Path> sourceFiles,
        List<String> componentClassNames
) {
    public TsjSpringComponentArtifact {
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath()
                .normalize();
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
        componentClassNames = List.copyOf(Objects.requireNonNull(componentClassNames, "componentClassNames"));
    }
}
