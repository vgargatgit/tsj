package dev.tsj.compiler.backend.jvm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

final class JavaInheritanceResolver {
    private final Function<String, Optional<JavaClassfileReader.RawClassInfo>> classResolver;
    private final Map<String, SupertypeResult> supertypeCache;
    private final Map<MemberCacheKey, List<RawMemberDescriptor>> memberCache;
    private final Map<MemberCacheKey, Integer> memberScanCount;

    JavaInheritanceResolver(final JavaSymbolTable symbolTable) {
        this(symbolTable::resolveClass);
    }

    JavaInheritanceResolver(final Function<String, Optional<JavaClassfileReader.RawClassInfo>> classResolver) {
        this.classResolver = classResolver;
        this.supertypeCache = new LinkedHashMap<>();
        this.memberCache = new LinkedHashMap<>();
        this.memberScanCount = new LinkedHashMap<>();
    }

    SupertypeResult getAllSupertypes(final String fqcn) {
        final String internalName = normalizeClassName(fqcn);
        final SupertypeResult cached = supertypeCache.get(internalName);
        if (cached != null) {
            return cached;
        }
        final LinkedHashSet<String> ordered = new LinkedHashSet<>();
        final List<String> diagnostics = new ArrayList<>();
        final Deque<String> path = new ArrayDeque<>();
        collectSupertypes(internalName, ordered, diagnostics, path);
        final SupertypeResult result = new SupertypeResult(List.copyOf(ordered), List.copyOf(diagnostics));
        supertypeCache.put(internalName, result);
        return result;
    }

    MemberLookupResult collectMembers(
            final String fqcn,
            final String memberName,
            final LookupContext context
    ) {
        final String targetInternalName = normalizeClassName(fqcn);
        final List<String> diagnostics = new ArrayList<>();
        final List<ResolvedMember> members = new ArrayList<>();
        final SupertypeResult supertypes = getAllSupertypes(targetInternalName);
        diagnostics.addAll(supertypes.diagnostics());

        final List<String> owners = new ArrayList<>();
        owners.add(targetInternalName);
        owners.addAll(supertypes.supertypes());

        for (String ownerInternalName : owners) {
            final List<RawMemberDescriptor> declaredMembers = declaredMembers(ownerInternalName, memberName);
            for (RawMemberDescriptor declared : declaredMembers) {
                final boolean inherited = !ownerInternalName.equals(targetInternalName);
                final AccessResult access = evaluateAccess(declared, context, ownerInternalName);
                members.add(new ResolvedMember(
                        declared.ownerInternalName(),
                        declared.name(),
                        declared.descriptor(),
                        declared.kind(),
                        declared.accessFlags(),
                        inherited,
                        access.visibility(),
                        access.accessible(),
                        access.moduleReadable(),
                        access.packageExported()
                ));
            }
        }
        return new MemberLookupResult(List.copyOf(members), List.copyOf(diagnostics));
    }

    int supertypeCacheSize() {
        return supertypeCache.size();
    }

    int memberCacheSize() {
        return memberCache.size();
    }

    int memberScanCount(final String ownerClassName, final String memberName) {
        final MemberCacheKey key = new MemberCacheKey(normalizeClassName(ownerClassName), memberName);
        return memberScanCount.getOrDefault(key, 0);
    }

    private void collectSupertypes(
            final String internalName,
            final LinkedHashSet<String> ordered,
            final List<String> diagnostics,
            final Deque<String> path
    ) {
        final Optional<JavaClassfileReader.RawClassInfo> rawClassInfo = classResolver.apply(internalName);
        if (rawClassInfo.isEmpty()) {
            diagnostics.add("Class not found while traversing supertypes: " + internalName);
            return;
        }
        final JavaClassfileReader.RawClassInfo descriptor = rawClassInfo.get();
        path.addLast(internalName);
        final String superInternalName = descriptor.superInternalName();
        if (superInternalName != null) {
            traverseEdge(internalName, superInternalName, ordered, diagnostics, path);
        }
        for (String interfaceInternalName : descriptor.interfaces()) {
            traverseEdge(internalName, interfaceInternalName, ordered, diagnostics, path);
        }
        path.removeLast();
    }

    private void traverseEdge(
            final String ownerInternalName,
            final String nextInternalName,
            final LinkedHashSet<String> ordered,
            final List<String> diagnostics,
            final Deque<String> path
    ) {
        if (path.contains(nextInternalName)) {
            diagnostics.add("Detected inheritance cycle at " + ownerInternalName + " -> " + nextInternalName);
            return;
        }
        if (!ordered.add(nextInternalName)) {
            return;
        }
        collectSupertypes(nextInternalName, ordered, diagnostics, path);
    }

    private List<RawMemberDescriptor> declaredMembers(final String ownerInternalName, final String memberName) {
        final MemberCacheKey cacheKey = new MemberCacheKey(ownerInternalName, memberName);
        final List<RawMemberDescriptor> cached = memberCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        memberScanCount.merge(cacheKey, 1, Integer::sum);
        final Optional<JavaClassfileReader.RawClassInfo> descriptor = classResolver.apply(ownerInternalName);
        if (descriptor.isEmpty()) {
            memberCache.put(cacheKey, List.of());
            return List.of();
        }
        final List<RawMemberDescriptor> members = new ArrayList<>();
        for (JavaClassfileReader.RawFieldInfo field : descriptor.get().fields()) {
            if (field.name().equals(memberName)) {
                members.add(new RawMemberDescriptor(
                        ownerInternalName,
                        field.name(),
                        field.descriptor(),
                        field.accessFlags(),
                        MemberKind.FIELD
                ));
            }
        }
        for (JavaClassfileReader.RawMethodInfo method : descriptor.get().methods()) {
            if (method.name().equals(memberName)) {
                members.add(new RawMemberDescriptor(
                        ownerInternalName,
                        method.name(),
                        method.descriptor(),
                        method.accessFlags(),
                        MemberKind.METHOD
                ));
            }
        }
        final List<RawMemberDescriptor> stored = List.copyOf(members);
        memberCache.put(cacheKey, stored);
        return stored;
    }

    private AccessResult evaluateAccess(
            final RawMemberDescriptor member,
            final LookupContext context,
            final String ownerInternalName
    ) {
        final String requesterClass = context.requestingClassInternalName();
        final String ownerModule = context.classModuleByInternalName().get(ownerInternalName);
        final String requesterModule = context.requestingModuleName();
        final String ownerPackage = packageName(ownerInternalName);
        final String requesterPackage = packageName(requesterClass);
        final boolean sameClass = ownerInternalName.equals(requesterClass);
        final boolean samePackage = ownerPackage.equals(requesterPackage);

        final boolean moduleReadable = isModuleReadable(ownerModule, requesterModule, context);
        final boolean packageExported = isPackageExported(ownerModule, ownerPackage, requesterModule, context);

        final int accessFlags = member.accessFlags();
        final Visibility visibility = visibility(accessFlags);
        final boolean accessible = switch (visibility) {
            case PUBLIC -> moduleReadable && packageExported;
            case PROTECTED -> moduleReadable
                    && packageExported
                    && (samePackage || isRequesterSubtypeOfOwner(requesterClass, ownerInternalName));
            case PACKAGE_PRIVATE -> samePackage && modulePackageCompatible(ownerModule, requesterModule);
            case PRIVATE -> sameClass;
        };
        return new AccessResult(visibility, accessible, moduleReadable, packageExported);
    }

    private boolean isRequesterSubtypeOfOwner(final String requesterClass, final String ownerClass) {
        final SupertypeResult supertypes = getAllSupertypes(requesterClass);
        return supertypes.supertypes().contains(ownerClass);
    }

    private static boolean isModuleReadable(
            final String ownerModule,
            final String requesterModule,
            final LookupContext context
    ) {
        if (ownerModule == null || requesterModule == null) {
            return true;
        }
        if (ownerModule.equals(requesterModule)) {
            return true;
        }
        final Set<String> readable = context.readableModulesByModule().getOrDefault(requesterModule, Set.of());
        return readable.contains(ownerModule);
    }

    private static boolean isPackageExported(
            final String ownerModule,
            final String ownerPackage,
            final String requesterModule,
            final LookupContext context
    ) {
        if (ownerModule == null || requesterModule == null) {
            return true;
        }
        if (ownerModule.equals(requesterModule)) {
            return true;
        }
        final Set<String> exported = context.exportedPackagesByModule().getOrDefault(ownerModule, Set.of());
        return exported.contains(ownerPackage);
    }

    private static boolean modulePackageCompatible(final String ownerModule, final String requesterModule) {
        if (ownerModule == null || requesterModule == null) {
            return true;
        }
        return ownerModule.equals(requesterModule);
    }

    private static Visibility visibility(final int accessFlags) {
        if ((accessFlags & 0x0001) != 0) {
            return Visibility.PUBLIC;
        }
        if ((accessFlags & 0x0004) != 0) {
            return Visibility.PROTECTED;
        }
        if ((accessFlags & 0x0002) != 0) {
            return Visibility.PRIVATE;
        }
        return Visibility.PACKAGE_PRIVATE;
    }

    private static String packageName(final String internalName) {
        final int lastSlash = internalName.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return internalName.substring(0, lastSlash);
    }

    private static String normalizeClassName(final String fqcn) {
        if (fqcn == null || fqcn.isBlank()) {
            return "";
        }
        return fqcn.trim().replace('.', '/');
    }

    enum MemberKind {
        FIELD,
        METHOD
    }

    enum Visibility {
        PUBLIC,
        PROTECTED,
        PACKAGE_PRIVATE,
        PRIVATE
    }

    record SupertypeResult(List<String> supertypes, List<String> diagnostics) {
    }

    record LookupContext(
            String requestingClassInternalName,
            String requestingModuleName,
            Map<String, String> classModuleByInternalName,
            Map<String, Set<String>> readableModulesByModule,
            Map<String, Set<String>> exportedPackagesByModule
    ) {
        static LookupContext unrestricted(final String requestingClassInternalName) {
            return new LookupContext(
                    normalizeClassName(requestingClassInternalName),
                    null,
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }
    }

    record ResolvedMember(
            String ownerInternalName,
            String name,
            String descriptor,
            MemberKind kind,
            int accessFlags,
            boolean inherited,
            Visibility visibility,
            boolean accessible,
            boolean moduleReadable,
            boolean packageExported
    ) {
    }

    record MemberLookupResult(List<ResolvedMember> members, List<String> diagnostics) {
    }

    private record MemberCacheKey(String ownerInternalName, String memberName) {
    }

    private record RawMemberDescriptor(
            String ownerInternalName,
            String name,
            String descriptor,
            int accessFlags,
            MemberKind kind
    ) {
    }

    private record AccessResult(
            Visibility visibility,
            boolean accessible,
            boolean moduleReadable,
            boolean packageExported
    ) {
    }
}
