package dev.tsj.compiler.backend.jvm;

import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavaModuleGraphBuilder {
    ModuleGraph build(final List<Path> modulePathEntries) {
        final Map<String, ModuleDescriptor> descriptors = new LinkedHashMap<>();
        final List<String> diagnostics = new ArrayList<>();

        loadModuleDescriptors(ModuleFinder.ofSystem(), descriptors);
        if (!modulePathEntries.isEmpty()) {
            try {
                loadModuleDescriptors(
                        ModuleFinder.of(modulePathEntries.toArray(Path[]::new)),
                        descriptors
                );
            } catch (final FindException failure) {
                diagnostics.add("module-path-scan-failed: " + failure.getMessage());
            }
        }

        final Map<String, Set<String>> exportedPackages = computeExportedPackages(descriptors);
        final Map<String, Set<String>> readableModules = computeReadableClosure(descriptors);
        final Map<String, String> packageToModule = computePackageOwners(descriptors, diagnostics);

        return new ModuleGraph(
                immutableMapOfSets(exportedPackages),
                immutableMapOfSets(readableModules),
                Map.copyOf(packageToModule),
                List.copyOf(diagnostics)
        );
    }

    private static void loadModuleDescriptors(
            final ModuleFinder finder,
            final Map<String, ModuleDescriptor> descriptors
    ) {
        final List<ModuleReference> references = finder.findAll().stream()
                .sorted((left, right) -> left.descriptor().name().compareTo(right.descriptor().name()))
                .toList();
        for (ModuleReference reference : references) {
            final ModuleDescriptor descriptor = reference.descriptor();
            descriptors.put(descriptor.name(), descriptor);
        }
    }

    private static Map<String, Set<String>> computeExportedPackages(
            final Map<String, ModuleDescriptor> descriptors
    ) {
        final Map<String, Set<String>> exportedPackages = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleDescriptor> entry : descriptors.entrySet()) {
            final ModuleDescriptor descriptor = entry.getValue();
            final LinkedHashSet<String> exported = new LinkedHashSet<>();
            if (descriptor.isAutomatic()) {
                for (String packageName : descriptor.packages()) {
                    exported.add(toInternalPackage(packageName));
                }
            } else {
                for (ModuleDescriptor.Exports exports : descriptor.exports()) {
                    if (exports.targets().isEmpty()) {
                        exported.add(toInternalPackage(exports.source()));
                    }
                }
            }
            exportedPackages.put(entry.getKey(), Set.copyOf(exported));
        }
        return exportedPackages;
    }

    private static Map<String, Set<String>> computeReadableClosure(
            final Map<String, ModuleDescriptor> descriptors
    ) {
        final List<String> moduleNames = List.copyOf(descriptors.keySet());
        final Map<String, Set<String>> directReadable = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleDescriptor> entry : descriptors.entrySet()) {
            final ModuleDescriptor descriptor = entry.getValue();
            final LinkedHashSet<String> readable = new LinkedHashSet<>();
            if (descriptor.isAutomatic()) {
                readable.addAll(moduleNames);
                readable.remove(entry.getKey());
            }
            if (!"java.base".equals(entry.getKey()) && descriptors.containsKey("java.base")) {
                readable.add("java.base");
            }
            for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                readable.add(requires.name());
            }
            directReadable.put(entry.getKey(), Set.copyOf(readable));
        }

        final Map<String, Set<String>> closure = new LinkedHashMap<>();
        for (String moduleName : moduleNames) {
            final LinkedHashSet<String> reachable = new LinkedHashSet<>();
            final ArrayDeque<String> queue = new ArrayDeque<>(
                    directReadable.getOrDefault(moduleName, Set.of())
            );
            while (!queue.isEmpty()) {
                final String next = queue.removeFirst();
                if (!reachable.add(next)) {
                    continue;
                }
                final Set<String> outgoing = directReadable.get(next);
                if (outgoing != null) {
                    queue.addAll(outgoing);
                }
            }
            closure.put(moduleName, Set.copyOf(reachable));
        }
        return closure;
    }

    private static Map<String, String> computePackageOwners(
            final Map<String, ModuleDescriptor> descriptors,
            final List<String> diagnostics
    ) {
        final Map<String, String> packageToModule = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleDescriptor> entry : descriptors.entrySet()) {
            for (String packageName : entry.getValue().packages()) {
                final String internalPackage = toInternalPackage(packageName);
                final String existing = packageToModule.putIfAbsent(internalPackage, entry.getKey());
                if (existing != null && !existing.equals(entry.getKey())) {
                    diagnostics.add("split-package: " + internalPackage + " in " + existing + " and " + entry.getKey());
                }
            }
        }
        return packageToModule;
    }

    private static Map<String, Set<String>> immutableMapOfSets(final Map<String, Set<String>> input) {
        final Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : input.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static String toInternalPackage(final String packageName) {
        return packageName.replace('.', '/');
    }

    record ModuleGraph(
            Map<String, Set<String>> exportedPackagesByModule,
            Map<String, Set<String>> readableModulesByModule,
            Map<String, String> packageToModule,
            List<String> diagnostics
    ) {
    }
}
