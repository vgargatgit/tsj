package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Generated TS-authored Spring web controller adapter metadata.
 */
public record TsjSpringWebControllerArtifact(
        Path outputDirectory,
        List<Path> sourceFiles,
        List<String> controllerClassNames
) {
    public TsjSpringWebControllerArtifact {
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath()
                .normalize();
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
        controllerClassNames = List.copyOf(Objects.requireNonNull(controllerClassNames, "controllerClassNames"));
    }
}
