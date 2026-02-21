package dev.tsj.compiler.backend.jvm;

import java.util.Map;
import java.util.Set;

final class JavaModuleAccessResolver {
    private final JavaSymbolTable symbolTable;

    JavaModuleAccessResolver(final JavaSymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    AccessResolution resolveClass(
            final String targetClassName,
            final AccessContext context
    ) {
        final JavaSymbolTable.ClassResolution classResolution = symbolTable.resolveClassWithMetadata(targetClassName);
        if (classResolution.status() == JavaSymbolTable.ResolutionStatus.NOT_FOUND) {
            return AccessResolution.classNotFound(targetClassName);
        }
        if (classResolution.status() == JavaSymbolTable.ResolutionStatus.TARGET_LEVEL_MISMATCH) {
            return AccessResolution.targetLevelMismatch(
                    targetClassName,
                    classResolution.diagnostic()
            );
        }
        final JavaClassfileReader.RawClassInfo descriptor = classResolution.classInfo().orElseThrow();
        final String internalName = descriptor.internalName();
        final String ownerModule = resolveOwnerModule(internalName, classResolution.origin(), context);
        final String requesterModule = context.requesterModuleName();
        if (ownerModule != null
                && requesterModule != null
                && !ownerModule.equals(requesterModule)) {
            final Set<String> readableModules = context.readableModulesByRequesterModule()
                    .getOrDefault(requesterModule, Set.of());
            if (!readableModules.contains(ownerModule)) {
                return AccessResolution.classNotReadable(internalName, ownerModule, requesterModule);
            }
            final String packageName = packageName(internalName);
            final Set<String> exportedPackages = context.exportedPackagesByModule()
                    .getOrDefault(ownerModule, Set.of());
            if (!exportedPackages.contains(packageName)) {
                return AccessResolution.classNotExported(internalName, ownerModule, packageName);
            }
        }
        return AccessResolution.accessible(
                internalName,
                ownerModule,
                classResolution.origin()
        );
    }

    private static String resolveOwnerModule(
            final String internalName,
            final JavaSymbolTable.ClassOrigin origin,
            final AccessContext context
    ) {
        final String mapped = context.classModuleByInternalName().get(internalName);
        if (mapped != null) {
            return mapped;
        }
        if (origin == null) {
            return context.packageToModule().get(packageName(internalName));
        }
        if (origin.moduleName() != null) {
            return origin.moduleName();
        }
        return context.packageToModule().get(packageName(internalName));
    }

    private static String packageName(final String internalName) {
        final int slash = internalName.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return internalName.substring(0, slash);
    }

    enum AccessStatus {
        ACCESSIBLE,
        CLASS_NOT_FOUND,
        CLASS_NOT_READABLE,
        CLASS_NOT_EXPORTED,
        TARGET_LEVEL_MISMATCH
    }

    record AccessContext(
            String requesterModuleName,
            Map<String, String> classModuleByInternalName,
            Map<String, Set<String>> readableModulesByRequesterModule,
            Map<String, Set<String>> exportedPackagesByModule,
            Map<String, String> packageToModule
    ) {
        static AccessContext unrestricted() {
            return new AccessContext(null, Map.of(), Map.of(), Map.of(), Map.of());
        }

        static AccessContext forRequesterModule(
                final String requesterModuleName,
                final JavaModuleGraphBuilder.ModuleGraph moduleGraph
        ) {
            return new AccessContext(
                    requesterModuleName,
                    Map.of(),
                    moduleGraph.readableModulesByModule(),
                    moduleGraph.exportedPackagesByModule(),
                    moduleGraph.packageToModule()
            );
        }
    }

    record AccessResolution(
            AccessStatus status,
            String internalName,
            String ownerModule,
            String detail,
            JavaSymbolTable.ClassOrigin selectedOrigin
    ) {
        static AccessResolution accessible(
                final String internalName,
                final String ownerModule,
                final JavaSymbolTable.ClassOrigin selectedOrigin
        ) {
            return new AccessResolution(
                    AccessStatus.ACCESSIBLE,
                    internalName,
                    ownerModule,
                    "accessible",
                    selectedOrigin
            );
        }

        static AccessResolution classNotFound(final String targetClassName) {
            return new AccessResolution(
                    AccessStatus.CLASS_NOT_FOUND,
                    targetClassName.replace('.', '/'),
                    null,
                    "class-not-found",
                    null
            );
        }

        static AccessResolution classNotReadable(
                final String internalName,
                final String ownerModule,
                final String requesterModule
        ) {
            return new AccessResolution(
                    AccessStatus.CLASS_NOT_READABLE,
                    internalName,
                    ownerModule,
                    "class-not-readable: " + ownerModule + " from " + requesterModule,
                    null
            );
        }

        static AccessResolution classNotExported(
                final String internalName,
                final String ownerModule,
                final String packageName
        ) {
            return new AccessResolution(
                    AccessStatus.CLASS_NOT_EXPORTED,
                    internalName,
                    ownerModule,
                    "class-not-exported: " + packageName + " from " + ownerModule,
                    null
            );
        }

        static AccessResolution targetLevelMismatch(
                final String targetClassName,
                final String detail
        ) {
            return new AccessResolution(
                    AccessStatus.TARGET_LEVEL_MISMATCH,
                    targetClassName.replace('.', '/'),
                    null,
                    detail == null ? "target-level-mismatch" : detail,
                    null
            );
        }
    }
}
