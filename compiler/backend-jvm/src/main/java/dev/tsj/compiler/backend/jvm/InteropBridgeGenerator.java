package dev.tsj.compiler.backend.jvm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generates opt-in Java interop bridge stubs from allowlisted targets.
 */
public final class InteropBridgeGenerator {
    private static final String CODE_INPUT = "TSJ-INTEROP-INPUT";
    private static final String CODE_INVALID = "TSJ-INTEROP-INVALID";
    private static final String CODE_DISALLOWED = "TSJ-INTEROP-DISALLOWED";
    private static final String FEATURE_ALLOWLIST = "TSJ19-ALLOWLIST";
    private static final String GUIDANCE_ALLOWLIST =
            "Only allowlisted Java targets can be bridged. Add target to allowlist or remove it from targets.";
    private static final String GENERATED_PACKAGE = "dev.tsj.generated.interop";

    public InteropBridgeArtifact generate(final Path specFile, final Path outputDir) {
        final Path normalizedSpec = normalizeSpec(specFile);
        final Path normalizedOutput = Objects.requireNonNull(outputDir, "outputDir").toAbsolutePath().normalize();

        final Properties properties = readSpecProperties(normalizedSpec);
        final Set<InteropTarget> allowlist = parseTargets(properties.getProperty("allowlist"), normalizedSpec);
        final Set<InteropTarget> requested = parseTargets(properties.getProperty("targets"), normalizedSpec);
        if (requested.isEmpty()) {
            return new InteropBridgeArtifact(normalizedOutput, List.of(), List.of());
        }
        for (InteropTarget target : requested) {
            if (!allowlist.contains(target)) {
                throw new JvmCompilationException(
                        CODE_DISALLOWED,
                        "Interop target is not allowlisted: " + target.displayName() + ".",
                        null,
                        null,
                        normalizedSpec.toString(),
                        FEATURE_ALLOWLIST,
                        GUIDANCE_ALLOWLIST
                );
            }
        }

        final Map<String, Set<String>> targetsByClass = new LinkedHashMap<>();
        final List<String> emittedTargets = new ArrayList<>();
        for (InteropTarget target : requested) {
            assertClassAndMethodExists(target, normalizedSpec);
            targetsByClass.computeIfAbsent(target.className(), ignored -> new TreeSet<>()).add(target.methodName());
            emittedTargets.add(target.displayName());
        }

        final Path sourceRoot = normalizedOutput.resolve(GENERATED_PACKAGE.replace('.', '/'));
        final List<Path> sources = new ArrayList<>();
        for (Map.Entry<String, Set<String>> classTargets : targetsByClass.entrySet()) {
            final String className = classTargets.getKey();
            final String bridgeClassName = toBridgeClassName(className);
            final String source = renderBridgeSource(className, bridgeClassName, classTargets.getValue());
            final Path sourcePath = sourceRoot.resolve(bridgeClassName + ".java");
            try {
                Files.createDirectories(sourcePath.getParent());
                Files.writeString(sourcePath, source, UTF_8);
            } catch (final IOException ioException) {
                throw new JvmCompilationException(
                        CODE_INPUT,
                        "Failed to write interop bridge source: " + ioException.getMessage(),
                        null,
                        null,
                        normalizedSpec.toString(),
                        null,
                        null,
                        ioException
                );
            }
            sources.add(sourcePath);
        }

        final Path metadataPath = normalizedOutput.resolve("interop-bridges.properties");
        final Properties metadata = new Properties();
        metadata.setProperty("formatVersion", "0.1");
        metadata.setProperty("generatedPackage", GENERATED_PACKAGE);
        metadata.setProperty("generatedCount", Integer.toString(sources.size()));
        metadata.setProperty("targets", String.join(",", emittedTargets));
        try {
            Files.createDirectories(normalizedOutput);
            try (OutputStream outputStream = Files.newOutputStream(metadataPath)) {
                metadata.store(outputStream, "TSJ interop bridges");
            }
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    CODE_INPUT,
                    "Failed to write interop bridge metadata: " + ioException.getMessage(),
                    null,
                    null,
                    normalizedSpec.toString(),
                    null,
                    null,
                    ioException
            );
        }

        return new InteropBridgeArtifact(normalizedOutput, List.copyOf(sources), List.copyOf(emittedTargets));
    }

    private static Path normalizeSpec(final Path specFile) {
        final Path normalized = Objects.requireNonNull(specFile, "specFile").toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new JvmCompilationException(
                    CODE_INPUT,
                    "Interop spec file not found: " + normalized,
                    null,
                    null,
                    normalized.toString(),
                    null,
                    null
            );
        }
        return normalized;
    }

    private static Properties readSpecProperties(final Path specFile) {
        final Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(specFile)) {
            properties.load(inputStream);
            return properties;
        } catch (final IOException ioException) {
            throw new JvmCompilationException(
                    CODE_INPUT,
                    "Failed to read interop spec: " + ioException.getMessage(),
                    null,
                    null,
                    specFile.toString(),
                    null,
                    null,
                    ioException
            );
        }
    }

    private static Set<InteropTarget> parseTargets(final String rawValue, final Path specFile) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        final Set<InteropTarget> targets = new LinkedHashSet<>();
        final String[] parts = rawValue.split(",");
        for (String part : parts) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int separator = trimmed.indexOf('#');
            if (separator <= 0 || separator >= trimmed.length() - 1) {
                throw new JvmCompilationException(
                        CODE_INVALID,
                        "Invalid interop target format: " + trimmed + ". Expected <class>#<method>.",
                        null,
                        null,
                        specFile.toString(),
                        null,
                        null
                );
            }
            final String className = trimmed.substring(0, separator).trim();
            final String methodName = trimmed.substring(separator + 1).trim();
            if (className.isEmpty() || methodName.isEmpty()) {
                throw new JvmCompilationException(
                        CODE_INVALID,
                        "Invalid interop target format: " + trimmed + ". Expected <class>#<method>.",
                        null,
                        null,
                        specFile.toString(),
                        null,
                        null
                );
            }
            targets.add(new InteropTarget(className, methodName));
        }
        return Set.copyOf(targets);
    }

    private static void assertClassAndMethodExists(final InteropTarget target, final Path specFile) {
        final Class<?> targetClass;
        try {
            targetClass = Class.forName(target.className());
        } catch (final ClassNotFoundException classNotFoundException) {
            throw new JvmCompilationException(
                    CODE_INVALID,
                    "Interop target class was not found: " + target.className(),
                    null,
                    null,
                    specFile.toString(),
                    null,
                    null,
                    classNotFoundException
            );
        }

        for (Method method : targetClass.getMethods()) {
            if (!target.methodName().equals(method.getName())) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                return;
            }
        }
        throw new JvmCompilationException(
                CODE_INVALID,
                "Interop target method was not found or not static: " + target.displayName(),
                null,
                null,
                specFile.toString(),
                null,
                null
        );
    }

    private static String renderBridgeSource(
            final String className,
            final String bridgeClassName,
            final Set<String> methods
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        builder.append("public final class ").append(bridgeClassName).append(" {\n");
        builder.append("    private ").append(bridgeClassName).append("() {\n");
        builder.append("    }\n\n");
        for (String methodName : methods) {
            final String javaMethodName = sanitizeJavaIdentifier(methodName);
            builder.append("    public static Object ")
                    .append(javaMethodName)
                    .append("(final Object... args) {\n");
            builder.append("        return dev.tsj.runtime.TsjJavaInterop.invokeStatic(\"")
                    .append(escapeJava(className))
                    .append("\", \"")
                    .append(escapeJava(methodName))
                    .append("\", args);\n");
            builder.append("    }\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private static String toBridgeClassName(final String className) {
        final String[] parts = className.split("\\.");
        final StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            final String normalized = sanitizeJavaIdentifier(part);
            builder.append(Character.toUpperCase(normalized.charAt(0)));
            if (normalized.length() > 1) {
                builder.append(normalized.substring(1));
            }
        }
        if (builder.isEmpty()) {
            builder.append("Interop");
        }
        builder.append("Bridge");
        return builder.toString();
    }

    private static String sanitizeJavaIdentifier(final String raw) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < raw.length(); index++) {
            final char current = raw.charAt(index);
            if (Character.isLetterOrDigit(current) || current == '_') {
                builder.append(current);
            } else {
                builder.append('_');
            }
        }
        if (builder.isEmpty()) {
            builder.append("value");
        }
        if (Character.isDigit(builder.charAt(0))) {
            builder.insert(0, '_');
        }
        return builder.toString();
    }

    private static String escapeJava(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record InteropTarget(String className, String methodName) {
        private String displayName() {
            return className + "#" + methodName;
        }
    }
}
