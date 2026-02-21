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
        List<String> targets,
        List<SelectedTargetIdentity> selectedTargets,
        List<UnresolvedTarget> unresolvedTargets
) {
    public InteropBridgeArtifact {
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath()
                .normalize();
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        selectedTargets = List.copyOf(Objects.requireNonNull(selectedTargets, "selectedTargets"));
        unresolvedTargets = List.copyOf(Objects.requireNonNull(unresolvedTargets, "unresolvedTargets"));
    }

    public record SelectedTargetIdentity(
            String className,
            String bindingName,
            String owner,
            String name,
            String descriptor,
            String invokeKind
    ) {
    }

    public record UnresolvedTarget(String className, String bindingName, String reason) {
    }
}
