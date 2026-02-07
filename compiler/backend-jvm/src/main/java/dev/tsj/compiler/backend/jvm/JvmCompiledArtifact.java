package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;

/**
 * Output of TSJ JVM backend compile phase.
 *
 * @param entryFile TypeScript entry file
 * @param outputDirectory class output directory
 * @param className generated main class name
 * @param classFile generated class file path
 */
public record JvmCompiledArtifact(
        Path entryFile,
        Path outputDirectory,
        String className,
        Path classFile
) {
}
