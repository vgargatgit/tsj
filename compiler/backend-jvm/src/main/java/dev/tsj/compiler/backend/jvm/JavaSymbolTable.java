package dev.tsj.compiler.backend.jvm;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

final class JavaSymbolTable {
    private static final String DEFAULT_SCHEMA_VERSION = "1";
    private static final String DEFAULT_TOOL_VERSION = "tsj-local";

    private final JavaClassfileReader classfileReader;
    private final Map<String, ClassResolution> resolutionsByCacheKey;
    private final Map<String, Integer> parseCountByInternalName;
    private final Path persistentCacheFile;
    private final String toolVersion;
    private final String schemaVersion;
    private final List<String> cacheDiagnostics;
    private long cacheHits;
    private long cacheMisses;
    private long cacheInvalidations;
    private List<Path> classpathEntries;
    private String classpathFingerprint;
    private int targetJdkRelease;

    JavaSymbolTable(final List<Path> classpathEntries, final String classpathFingerprint) {
        this(classpathEntries, classpathFingerprint, Runtime.version().feature(), new JavaClassfileReader());
    }

    JavaSymbolTable(
            final List<Path> classpathEntries,
            final String classpathFingerprint,
            final int targetJdkRelease
    ) {
        this(classpathEntries, classpathFingerprint, targetJdkRelease, new JavaClassfileReader());
    }

    JavaSymbolTable(
            final List<Path> classpathEntries,
            final String classpathFingerprint,
            final int targetJdkRelease,
            final JavaClassfileReader classfileReader
    ) {
        this(
                classpathEntries,
                classpathFingerprint,
                targetJdkRelease,
                classfileReader,
                null,
                DEFAULT_TOOL_VERSION,
                DEFAULT_SCHEMA_VERSION
        );
    }

    JavaSymbolTable(
            final List<Path> classpathEntries,
            final String classpathFingerprint,
            final int targetJdkRelease,
            final JavaClassfileReader classfileReader,
            final Path persistentCacheFile,
            final String toolVersion,
            final String schemaVersion
    ) {
        this.classpathEntries = normalizeEntries(classpathEntries);
        this.classpathFingerprint = normalizeFingerprint(classpathFingerprint);
        this.targetJdkRelease = normalizeTargetJdk(targetJdkRelease);
        this.classfileReader = classfileReader;
        this.resolutionsByCacheKey = new LinkedHashMap<>();
        this.parseCountByInternalName = new LinkedHashMap<>();
        this.persistentCacheFile = persistentCacheFile == null ? null : persistentCacheFile.toAbsolutePath().normalize();
        this.toolVersion = normalizeVersion(toolVersion, DEFAULT_TOOL_VERSION);
        this.schemaVersion = normalizeVersion(schemaVersion, DEFAULT_SCHEMA_VERSION);
        this.cacheDiagnostics = new ArrayList<>();
        loadPersistentCache();
    }

    Optional<JavaClassfileReader.RawClassInfo> resolveClass(final String fqcn) {
        return resolveClassWithMetadata(fqcn).classInfo();
    }

    ClassResolution resolveClassWithMetadata(final String fqcn) {
        final String internalName = normalizeClassName(fqcn);
        final String cacheKey = cacheKey(internalName, classpathFingerprint);
        final ClassResolution cached = resolutionsByCacheKey.get(cacheKey);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        final ClassResolution parsed = parseClassDescriptor(internalName);
        resolutionsByCacheKey.put(cacheKey, parsed);
        if (parsed.classInfo().isPresent()) {
            parseCountByInternalName.merge(internalName, 1, Integer::sum);
        }
        persistCacheSnapshot();
        return parsed;
    }

    void updateClasspath(final List<Path> classpathEntries, final String classpathFingerprint) {
        final String normalizedFingerprint = normalizeFingerprint(classpathFingerprint);
        this.classpathEntries = normalizeEntries(classpathEntries);
        if (!this.classpathFingerprint.equals(normalizedFingerprint)) {
            resolutionsByCacheKey.clear();
            cacheInvalidations++;
        }
        this.classpathFingerprint = normalizedFingerprint;
        persistCacheSnapshot();
    }

    void setTargetJdkRelease(final int targetJdkRelease) {
        final int normalized = normalizeTargetJdk(targetJdkRelease);
        if (this.targetJdkRelease != normalized) {
            resolutionsByCacheKey.clear();
            cacheInvalidations++;
        }
        this.targetJdkRelease = normalized;
        persistCacheSnapshot();
    }

    int parsedCount(final String fqcn) {
        final String internalName = normalizeClassName(fqcn);
        return parseCountByInternalName.getOrDefault(internalName, 0);
    }

    int cacheSize() {
        return resolutionsByCacheKey.size();
    }

    CacheStats cacheStats() {
        return new CacheStats(cacheHits, cacheMisses, cacheInvalidations);
    }

    List<String> cacheDiagnostics() {
        return List.copyOf(cacheDiagnostics);
    }

    private ClassResolution parseClassDescriptor(final String internalName) {
        final String classEntry = internalName + ".class";
        boolean sawTargetMismatch = false;
        Integer lowestIncompatibleVersion = null;
        for (Path entry : classpathEntries) {
            final Path normalizedEntry = entry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalizedEntry)) {
                final Path classFile = normalizedEntry.resolve(classEntry);
                if (!Files.isRegularFile(classFile)) {
                    continue;
                }
                try {
                    final byte[] bytes = Files.readAllBytes(classFile);
                    final JavaClassfileReader.RawClassInfo classInfo = classfileReader.read(bytes, classFile);
                    return new ClassResolution(
                            Optional.of(classInfo),
                            ResolutionStatus.FOUND,
                            new ClassOrigin(
                                    normalizedEntry,
                                    classEntry,
                                    false,
                                    null,
                                    detectJrtModuleName(classFile)
                            ),
                            null,
                            bytes
                    );
                } catch (final IOException ignored) {
                    return ClassResolution.notFound();
                }
            }
            if (!Files.isRegularFile(normalizedEntry)) {
                continue;
            }
            try (JarFile jarFile = new JarFile(normalizedEntry.toFile())) {
                final JarSelection selection = selectJarEntry(jarFile, classEntry, targetJdkRelease);
                if (selection.status() == JarSelectionStatus.NOT_PRESENT) {
                    continue;
                }
                if (selection.status() == JarSelectionStatus.TARGET_LEVEL_MISMATCH) {
                    sawTargetMismatch = true;
                    if (selection.lowestIncompatibleVersion() != null) {
                        if (lowestIncompatibleVersion == null
                                || selection.lowestIncompatibleVersion() < lowestIncompatibleVersion) {
                            lowestIncompatibleVersion = selection.lowestIncompatibleVersion();
                        }
                    }
                    continue;
                }
                final JarEntry jarEntry = selection.entry();
                try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                    final byte[] bytes = inputStream.readAllBytes();
                    final Path sourcePath = normalizedEntry.resolve(jarEntry.getName());
                    final JavaClassfileReader.RawClassInfo classInfo = classfileReader.read(bytes, sourcePath);
                    return new ClassResolution(
                            Optional.of(classInfo),
                            ResolutionStatus.FOUND,
                            new ClassOrigin(
                                    normalizedEntry,
                                    jarEntry.getName(),
                                    selection.versioned(),
                                    selection.selectedVersion(),
                                    discoverJarModuleName(jarFile, normalizedEntry)
                            ),
                            null,
                            bytes
                    );
                }
            } catch (final IOException ignored) {
                return ClassResolution.notFound();
            }
        }
        if (sawTargetMismatch) {
            return ClassResolution.targetLevelMismatch(lowestIncompatibleVersion);
        }
        return ClassResolution.notFound();
    }

    private static List<Path> normalizeEntries(final List<Path> entries) {
        final List<Path> normalized = new ArrayList<>(entries.size());
        for (Path entry : entries) {
            normalized.add(entry.toAbsolutePath().normalize());
        }
        return List.copyOf(normalized);
    }

    private static String normalizeFingerprint(final String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return "no-fingerprint";
        }
        return fingerprint.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeClassName(final String fqcn) {
        return fqcn.replace('.', '/');
    }

    private static int normalizeTargetJdk(final int targetJdkRelease) {
        if (targetJdkRelease < 8) {
            return 8;
        }
        return targetJdkRelease;
    }

    private static String cacheKey(final String internalName, final String fingerprint) {
        return internalName + "@" + fingerprint;
    }

    private static String normalizeVersion(final String version, final String fallback) {
        if (version == null || version.isBlank()) {
            return fallback;
        }
        return version.trim();
    }

    private void loadPersistentCache() {
        if (persistentCacheFile == null || !Files.exists(persistentCacheFile) || !Files.isRegularFile(persistentCacheFile)) {
            return;
        }
        try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(persistentCacheFile))) {
            final Object value = inputStream.readObject();
            if (!(value instanceof CacheEnvelope envelope)) {
                cacheInvalidations++;
                cacheDiagnostics.add("Persistent descriptor cache ignored: unexpected payload type.");
                return;
            }
            if (!schemaVersion.equals(envelope.schemaVersion())
                    || !toolVersion.equals(envelope.toolVersion())
                    || !classpathFingerprint.equals(envelope.classpathFingerprint())
                    || targetJdkRelease != envelope.targetJdkRelease()) {
                cacheInvalidations++;
                cacheDiagnostics.add("Persistent descriptor cache invalidated: schema/tool/fingerprint mismatch.");
                return;
            }
            for (Map.Entry<String, CachedClassResolution> entry : envelope.entries().entrySet()) {
                final ClassResolution resolution = fromCachedResolution(entry.getValue());
                if (resolution != null) {
                    resolutionsByCacheKey.put(entry.getKey(), resolution);
                }
            }
        } catch (final IOException | ClassNotFoundException exception) {
            cacheInvalidations++;
            cacheDiagnostics.add("Persistent descriptor cache could not be loaded: " + exception.getMessage());
        }
    }

    private void persistCacheSnapshot() {
        if (persistentCacheFile == null) {
            return;
        }
        final Map<String, CachedClassResolution> cachedEntries = new LinkedHashMap<>();
        for (Map.Entry<String, ClassResolution> entry : resolutionsByCacheKey.entrySet()) {
            cachedEntries.put(entry.getKey(), toCachedResolution(entry.getValue()));
        }
        final CacheEnvelope envelope = new CacheEnvelope(
                schemaVersion,
                toolVersion,
                classpathFingerprint,
                targetJdkRelease,
                cachedEntries
        );
        try {
            if (persistentCacheFile.getParent() != null) {
                Files.createDirectories(persistentCacheFile.getParent());
            }
            try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(persistentCacheFile))) {
                outputStream.writeObject(envelope);
            }
        } catch (final IOException ioException) {
            cacheDiagnostics.add("Persistent descriptor cache write failed: " + ioException.getMessage());
        }
    }

    private ClassResolution fromCachedResolution(final CachedClassResolution cached) {
        final ResolutionStatus status = ResolutionStatus.valueOf(cached.status());
        final ClassOrigin origin = cached.origin() == null
                ? null
                : new ClassOrigin(
                Path.of(cached.origin().classpathEntry()),
                cached.origin().entryName(),
                cached.origin().versionedEntry(),
                cached.origin().selectedVersion(),
                cached.origin().moduleName()
        );
        if (status != ResolutionStatus.FOUND || cached.classBytes() == null) {
            return new ClassResolution(Optional.empty(), status, origin, cached.diagnostic(), null);
        }
        try {
            final Path sourcePath = origin == null
                    ? Path.of(cached.classPathHint() == null ? "cached.class" : cached.classPathHint())
                    : origin.classpathEntry().resolve(origin.entryName());
            final JavaClassfileReader.RawClassInfo classInfo = classfileReader.read(cached.classBytes(), sourcePath);
            return new ClassResolution(Optional.of(classInfo), status, origin, cached.diagnostic(), cached.classBytes());
        } catch (final IOException ioException) {
            cacheDiagnostics.add("Persistent descriptor cache entry parse failed: " + ioException.getMessage());
            return null;
        }
    }

    private static CachedClassResolution toCachedResolution(final ClassResolution resolution) {
        final CachedClassOrigin cachedOrigin = resolution.origin() == null
                ? null
                : new CachedClassOrigin(
                resolution.origin().classpathEntry().toString(),
                resolution.origin().entryName(),
                resolution.origin().versionedEntry(),
                resolution.origin().selectedVersion(),
                resolution.origin().moduleName()
        );
        final String pathHint = resolution.classInfo().map(info -> info.sourcePath().toString()).orElse(null);
        return new CachedClassResolution(
                resolution.status().name(),
                resolution.diagnostic(),
                cachedOrigin,
                resolution.classBytes(),
                pathHint
        );
    }

    private static JarSelection selectJarEntry(
            final JarFile jarFile,
            final String classEntry,
            final int targetJdkRelease
    ) throws IOException {
        final JarEntry baseEntry = jarFile.getJarEntry(classEntry);
        if (!isMultiReleaseJar(jarFile)) {
            if (baseEntry == null) {
                return JarSelection.notPresent();
            }
            return JarSelection.found(baseEntry, false, null);
        }

        for (int version = targetJdkRelease; version >= 9; version--) {
            final String versionedEntry = "META-INF/versions/" + version + "/" + classEntry;
            final JarEntry jarEntry = jarFile.getJarEntry(versionedEntry);
            if (jarEntry != null) {
                return JarSelection.found(jarEntry, true, version);
            }
        }

        if (baseEntry != null) {
            return JarSelection.found(baseEntry, false, null);
        }

        Integer lowestIncompatibleVersion = null;
        final String suffix = "/" + classEntry;
        final Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            final String name = entry.getName();
            if (!name.startsWith("META-INF/versions/") || !name.endsWith(suffix)) {
                continue;
            }
            final int versionStart = "META-INF/versions/".length();
            final int slashIndex = name.indexOf('/', versionStart);
            if (slashIndex < 0) {
                continue;
            }
            final String versionText = name.substring(versionStart, slashIndex);
            final int version;
            try {
                version = Integer.parseInt(versionText);
            } catch (final NumberFormatException ignored) {
                continue;
            }
            if (version > targetJdkRelease) {
                if (lowestIncompatibleVersion == null || version < lowestIncompatibleVersion) {
                    lowestIncompatibleVersion = version;
                }
            }
        }
        if (lowestIncompatibleVersion != null) {
            return JarSelection.targetMismatch(lowestIncompatibleVersion);
        }
        return JarSelection.notPresent();
    }

    private static boolean isMultiReleaseJar(final JarFile jarFile) throws IOException {
        final Manifest manifest = jarFile.getManifest();
        if (manifest == null || manifest.getMainAttributes() == null) {
            return false;
        }
        final String value = manifest.getMainAttributes().getValue("Multi-Release");
        return value != null && Boolean.parseBoolean(value.trim());
    }

    private static String discoverJarModuleName(final JarFile jarFile, final Path jarPath) {
        final Manifest manifest;
        try {
            manifest = jarFile.getManifest();
        } catch (IOException ignored) {
            return null;
        }
        if (manifest != null && manifest.getMainAttributes() != null) {
            final String automatic = manifest.getMainAttributes().getValue("Automatic-Module-Name");
            if (automatic != null && !automatic.isBlank()) {
                return automatic.trim();
            }
        }
        final String fileName = jarPath.getFileName() == null ? jarPath.toString() : jarPath.getFileName().toString();
        String module = fileName.endsWith(".jar") ? fileName.substring(0, fileName.length() - 4) : fileName;
        final int versionSeparator = module.indexOf('-');
        if (versionSeparator > 0 && versionSeparator < module.length() - 1
                && Character.isDigit(module.charAt(versionSeparator + 1))) {
            module = module.substring(0, versionSeparator);
        }
        return module.replaceAll("[^A-Za-z0-9.]", ".").replace("..", ".");
    }

    private static String detectJrtModuleName(final Path classpathEntry) {
        if (!"jrt".equalsIgnoreCase(classpathEntry.getFileSystem().provider().getScheme())) {
            return null;
        }
        if (classpathEntry.getNameCount() == 0) {
            return null;
        }
        final String first = classpathEntry.getName(0).toString();
        if ("modules".equals(first) && classpathEntry.getNameCount() >= 2) {
            return classpathEntry.getName(1).toString();
        }
        if ("packages".equals(first)) {
            return null;
        }
        return first;
    }

    enum ResolutionStatus {
        FOUND,
        NOT_FOUND,
        TARGET_LEVEL_MISMATCH
    }

    record ClassOrigin(
            Path classpathEntry,
            String entryName,
            boolean versionedEntry,
            Integer selectedVersion,
            String moduleName
    ) {
    }

    record ClassResolution(
            Optional<JavaClassfileReader.RawClassInfo> classInfo,
            ResolutionStatus status,
            ClassOrigin origin,
            String diagnostic,
            byte[] classBytes
    ) {
        static ClassResolution notFound() {
            return new ClassResolution(Optional.empty(), ResolutionStatus.NOT_FOUND, null, "class-not-found", null);
        }

        static ClassResolution targetLevelMismatch(final Integer version) {
            final String diagnostic = version == null
                    ? "target-level-mismatch"
                    : "target-level-mismatch: requires class version " + version;
            return new ClassResolution(Optional.empty(), ResolutionStatus.TARGET_LEVEL_MISMATCH, null, diagnostic, null);
        }
    }

    record CacheStats(long hits, long misses, long invalidations) {
    }

    private record CacheEnvelope(
            String schemaVersion,
            String toolVersion,
            String classpathFingerprint,
            int targetJdkRelease,
            Map<String, CachedClassResolution> entries
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private record CachedClassResolution(
            String status,
            String diagnostic,
            CachedClassOrigin origin,
            byte[] classBytes,
            String classPathHint
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private record CachedClassOrigin(
            String classpathEntry,
            String entryName,
            boolean versionedEntry,
            Integer selectedVersion,
            String moduleName
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private enum JarSelectionStatus {
        FOUND,
        NOT_PRESENT,
        TARGET_LEVEL_MISMATCH
    }

    private record JarSelection(
            JarSelectionStatus status,
            JarEntry entry,
            boolean versioned,
            Integer selectedVersion,
            Integer lowestIncompatibleVersion
    ) {
        private static JarSelection found(final JarEntry entry, final boolean versioned, final Integer selectedVersion) {
            return new JarSelection(JarSelectionStatus.FOUND, entry, versioned, selectedVersion, null);
        }

        private static JarSelection notPresent() {
            return new JarSelection(JarSelectionStatus.NOT_PRESENT, null, false, null, null);
        }

        private static JarSelection targetMismatch(final Integer version) {
            return new JarSelection(JarSelectionStatus.TARGET_LEVEL_MISMATCH, null, false, null, version);
        }
    }
}
