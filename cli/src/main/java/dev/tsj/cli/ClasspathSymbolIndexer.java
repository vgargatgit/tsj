package dev.tsj.cli;

import dev.tsj.compiler.backend.jvm.JvmBytecodeRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class ClasspathSymbolIndexer {
    private static final String INDEX_FILE_NAME = "class-index.json";

    private ClasspathSymbolIndexer() {
    }

    static ClasspathSymbolIndex build(
            final Path appOutputDirectory,
            final List<Path> classpathEntries,
            final JvmBytecodeRunner.ClassloaderIsolationMode isolationMode
    ) {
        final Map<String, ClassOrigin> winners = new LinkedHashMap<>();
        final List<DuplicateSymbol> duplicates = new ArrayList<>();
        final Path normalizedAppOutput = appOutputDirectory.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedAppOutput)) {
            indexDirectory(
                    normalizedAppOutput,
                    normalizedAppOutput,
                    "app",
                    "directory",
                    winners,
                    duplicates,
                    isolationMode
            );
        }
        for (Path classpathEntry : classpathEntries) {
            final Path normalized = classpathEntry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                if (isJrtPath(normalized)) {
                    indexJrtDirectory(
                            normalized,
                            "dependency",
                            winners,
                            duplicates,
                            isolationMode
                    );
                } else {
                    indexDirectory(
                            normalized,
                            normalized,
                            "dependency",
                            "directory",
                            winners,
                            duplicates,
                            isolationMode
                    );
                }
                continue;
            }
            if (Files.isRegularFile(normalized)) {
                indexJar(normalized, winners, duplicates, isolationMode);
            }
        }
        final List<SymbolEntry> symbols = winners.entrySet().stream()
                .map(entry -> new SymbolEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(SymbolEntry::internalName))
                .toList();
        final List<DuplicateSymbol> sortedDuplicates = duplicates.stream()
                .sorted(
                        Comparator.comparing(DuplicateSymbol::internalName)
                                .thenComparing(symbol -> symbol.shadowed().location())
                                .thenComparing(symbol -> symbol.shadowed().entry())
                )
                .toList();
        return new ClasspathSymbolIndex(symbols, sortedDuplicates);
    }

    static Path writeIndexFile(
            final Path outDir,
            final ClasspathSymbolIndex classpathSymbolIndex,
            final JvmBytecodeRunner.ClassloaderIsolationMode isolationMode
    ) throws IOException {
        final Path indexPath = outDir.resolve(INDEX_FILE_NAME);
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"formatVersion\":\"0.1\",");
        builder.append("\"isolationMode\":\"").append(escapeJson(isolationMode.cliValue())).append("\",");
        builder.append("\"symbolCount\":").append(classpathSymbolIndex.symbolCount()).append(",");
        builder.append("\"duplicateCount\":").append(classpathSymbolIndex.duplicateCount()).append(",");
        builder.append("\"symbols\":[");
        appendSymbolsJson(builder, classpathSymbolIndex.symbols());
        builder.append("],");
        builder.append("\"duplicates\":[");
        appendDuplicatesJson(builder, classpathSymbolIndex.duplicates());
        builder.append("]");
        builder.append("}");
        Files.writeString(indexPath, builder.toString());
        return indexPath.toAbsolutePath().normalize();
    }

    private static void appendSymbolsJson(final StringBuilder builder, final List<SymbolEntry> symbols) {
        boolean first = true;
        for (SymbolEntry symbol : symbols) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("{");
            builder.append("\"internalName\":\"").append(escapeJson(symbol.internalName())).append("\",");
            builder.append("\"origin\":");
            appendOriginJson(builder, symbol.origin());
            builder.append("}");
        }
    }

    private static void appendDuplicatesJson(final StringBuilder builder, final List<DuplicateSymbol> duplicates) {
        boolean first = true;
        for (DuplicateSymbol duplicate : duplicates) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("{");
            builder.append("\"internalName\":\"").append(escapeJson(duplicate.internalName())).append("\",");
            builder.append("\"mode\":\"").append(escapeJson(duplicate.mode())).append("\",");
            builder.append("\"rule\":\"").append(escapeJson(duplicate.rule())).append("\",");
            builder.append("\"winner\":");
            appendOriginJson(builder, duplicate.winner());
            builder.append(",");
            builder.append("\"shadowed\":");
            appendOriginJson(builder, duplicate.shadowed());
            builder.append("}");
        }
    }

    private static void appendOriginJson(final StringBuilder builder, final ClassOrigin origin) {
        builder.append("{");
        builder.append("\"owner\":\"").append(escapeJson(origin.owner())).append("\",");
        builder.append("\"sourceKind\":\"").append(escapeJson(origin.sourceKind())).append("\",");
        builder.append("\"location\":\"").append(escapeJson(origin.location())).append("\",");
        builder.append("\"entry\":\"").append(escapeJson(origin.entry())).append("\",");
        builder.append("\"moduleName\":\"").append(escapeJson(origin.moduleName())).append("\",");
        builder.append("\"packageName\":\"").append(escapeJson(origin.packageName())).append("\"");
        builder.append("}");
    }

    private static void indexDirectory(
            final Path root,
            final Path location,
            final String owner,
            final String sourceKind,
            final Map<String, ClassOrigin> winners,
            final List<DuplicateSymbol> duplicates,
            final JvmBytecodeRunner.ClassloaderIsolationMode isolationMode
    ) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                    .sorted()
                    .forEach(path -> {
                        final Path relative = root.relativize(path);
                        final String entry = relative.toString().replace(File.separatorChar, '/');
                        final String internalName = internalNameFromEntry(entry);
                        if (internalName == null) {
                            return;
                        }
                        final ClassOrigin origin = new ClassOrigin(
                                owner,
                                sourceKind,
                                location.toString(),
                                entry,
                                "",
                                packageNameFromInternalName(internalName)
                        );
                        registerSymbol(internalName, origin, winners, duplicates, isolationMode);
                    });
        } catch (final IOException ignored) {
            // Best-effort indexing for inaccessible directory roots.
        }
    }

    private static void indexJrtDirectory(
            final Path root,
            final String owner,
            final Map<String, ClassOrigin> winners,
            final List<DuplicateSymbol> duplicates,
            final JvmBytecodeRunner.ClassloaderIsolationMode isolationMode
    ) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                    .sorted()
                    .forEach(path -> {
                        final JrtClassRecord jrtClassRecord = readJrtClassRecord(path);
                        if (jrtClassRecord == null) {
                            return;
                        }
                        final ClassOrigin origin = new ClassOrigin(
                                owner,
                                "jrt-module",
                                root.toString(),
                                jrtClassRecord.entry(),
                                jrtClassRecord.moduleName(),
                                packageNameFromInternalName(jrtClassRecord.internalName())
                        );
                        registerSymbol(
                                jrtClassRecord.internalName(),
                                origin,
                                winners,
                                duplicates,
                                isolationMode
                        );
                    });
        } catch (final IOException ignored) {
            // Best-effort indexing for inaccessible JRT paths.
        }
    }

    private static void indexJar(
            final Path jarPath,
            final Map<String, ClassOrigin> winners,
            final List<DuplicateSymbol> duplicates,
            final JvmBytecodeRunner.ClassloaderIsolationMode isolationMode
    ) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry jarEntry = entries.nextElement();
                if (jarEntry.isDirectory()) {
                    continue;
                }
                final String entryName = jarEntry.getName();
                final String internalName = internalNameFromEntry(entryName);
                if (internalName == null) {
                    continue;
                }
                final ClassOrigin origin = new ClassOrigin(
                        "dependency",
                        "jar",
                        jarPath.toString(),
                        entryName,
                        "",
                        packageNameFromInternalName(internalName)
                );
                registerSymbol(internalName, origin, winners, duplicates, isolationMode);
            }
        } catch (final IOException ignored) {
            // Best-effort indexing for unreadable jar files.
        }
    }

    private static void registerSymbol(
            final String internalName,
            final ClassOrigin origin,
            final Map<String, ClassOrigin> winners,
            final List<DuplicateSymbol> duplicates,
            final JvmBytecodeRunner.ClassloaderIsolationMode isolationMode
    ) {
        final ClassOrigin winner = winners.get(internalName);
        if (winner == null) {
            winners.put(internalName, origin);
            return;
        }
        if (isolationMode == JvmBytecodeRunner.ClassloaderIsolationMode.APP_ISOLATED
                && appDependencyConflict(winner, origin)) {
            final ClassOrigin appOrigin = "app".equals(winner.owner()) ? winner : origin;
            final ClassOrigin dependencyOrigin = "app".equals(winner.owner()) ? origin : winner;
            throw new AppIsolationConflictException(
                    internalName,
                    appOrigin,
                    dependencyOrigin,
                    "app-vs-dependency"
            );
        }
        duplicates.add(new DuplicateSymbol(
                internalName,
                winner,
                origin,
                duplicateRule(winner, origin),
                isolationMode.cliValue()
        ));
    }

    private static boolean appDependencyConflict(final ClassOrigin left, final ClassOrigin right) {
        return ("app".equals(left.owner()) && "dependency".equals(right.owner()))
                || ("dependency".equals(left.owner()) && "app".equals(right.owner()));
    }

    private static String duplicateRule(final ClassOrigin winner, final ClassOrigin shadowed) {
        if ("app".equals(winner.owner()) && "dependency".equals(shadowed.owner())) {
            return "app-first";
        }
        if ("dependency".equals(winner.owner()) && "dependency".equals(shadowed.owner())) {
            return "mediated-order";
        }
        return "classpath-order";
    }

    private static boolean isJrtPath(final Path path) {
        return "jrt".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
    }

    private static JrtClassRecord readJrtClassRecord(final Path classFilePath) {
        final String normalizedPath = classFilePath.toString().replace('\\', '/');
        final int modulesIndex = normalizedPath.indexOf("/modules/");
        if (modulesIndex < 0) {
            return null;
        }
        final String suffix = normalizedPath.substring(modulesIndex + "/modules/".length());
        final int moduleSeparator = suffix.indexOf('/');
        if (moduleSeparator <= 0) {
            return null;
        }
        final String moduleName = suffix.substring(0, moduleSeparator);
        final String entry = suffix.substring(moduleSeparator + 1);
        final String internalName = internalNameFromEntry(entry);
        if (internalName == null) {
            return null;
        }
        return new JrtClassRecord(internalName, entry, moduleName);
    }

    private static String internalNameFromEntry(final String entryName) {
        if (!entryName.endsWith(".class")) {
            return null;
        }
        return entryName.substring(0, entryName.length() - ".class".length());
    }

    private static String packageNameFromInternalName(final String internalName) {
        final int separator = internalName.lastIndexOf('/');
        if (separator < 0) {
            return "";
        }
        return internalName.substring(0, separator).replace('/', '.');
    }

    private static String escapeJson(final String rawValue) {
        final String value = rawValue == null ? "" : rawValue;
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    record ClasspathSymbolIndex(List<SymbolEntry> symbols, List<DuplicateSymbol> duplicates) {
        int symbolCount() {
            return symbols.size();
        }

        int duplicateCount() {
            return duplicates.size();
        }
    }

    record SymbolEntry(String internalName, ClassOrigin origin) {
    }

    record DuplicateSymbol(
            String internalName,
            ClassOrigin winner,
            ClassOrigin shadowed,
            String rule,
            String mode
    ) {
    }

    record ClassOrigin(
            String owner,
            String sourceKind,
            String location,
            String entry,
            String moduleName,
            String packageName
    ) {
    }

    record JrtClassRecord(String internalName, String entry, String moduleName) {
    }

    static final class AppIsolationConflictException extends RuntimeException {
        private final String internalName;
        private final ClassOrigin appOrigin;
        private final ClassOrigin dependencyOrigin;
        private final String rule;

        AppIsolationConflictException(
                final String internalName,
                final ClassOrigin appOrigin,
                final ClassOrigin dependencyOrigin,
                final String rule
        ) {
            super(
                    "Classloader isolation conflict under mode `app-isolated`: class `"
                            + internalName.replace('/', '.')
                            + "` exists in both app output `"
                            + appOrigin.location()
                            + "!"
                            + appOrigin.entry()
                            + "` and dependency `"
                            + dependencyOrigin.location()
                            + "!"
                            + dependencyOrigin.entry()
                            + "` (rule="
                            + rule
                            + ")."
            );
            this.internalName = internalName;
            this.appOrigin = appOrigin;
            this.dependencyOrigin = dependencyOrigin;
            this.rule = rule;
        }

        String internalName() {
            return internalName;
        }

        ClassOrigin appOrigin() {
            return appOrigin;
        }

        ClassOrigin dependencyOrigin() {
            return dependencyOrigin;
        }

        String rule() {
            return rule;
        }
    }
}
