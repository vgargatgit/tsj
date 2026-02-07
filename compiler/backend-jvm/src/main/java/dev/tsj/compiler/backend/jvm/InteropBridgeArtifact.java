package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Generated interop bridge output metadata.
 */
public record InteropBridgeArtifact(
        Path outputDirectory,
        List<Path> sourceFiles,
        List<String> targets
) {
    public InteropBridgeArtifact {
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath()
                .normalize();
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }
}
