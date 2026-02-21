package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaInheritanceResolverTest {
    @Test
    void getAllSupertypesReturnsDeterministicSuperclassAndInterfaceClosure() {
        final Map<String, JavaClassfileReader.RawClassInfo> classes = new LinkedHashMap<>();
        classes.put("sample/tsj50/Root", rawClass("sample/tsj50/Root", null, List.of()));
        classes.put("sample/tsj50/Mid", rawClass("sample/tsj50/Mid", null, List.of("sample/tsj50/Root")));
        classes.put("sample/tsj50/Base", rawClass(
                "sample/tsj50/Base",
                "java/lang/Object",
                List.of("sample/tsj50/Mid")
        ));
        classes.put("sample/tsj50/Child", rawClass("sample/tsj50/Child", "sample/tsj50/Base", List.of()));
        classes.put("java/lang/Object", rawClass("java/lang/Object", null, List.of()));

        final JavaInheritanceResolver resolver = new JavaInheritanceResolver(fromMap(classes));
        final JavaInheritanceResolver.SupertypeResult first = resolver.getAllSupertypes("sample.tsj50.Child");
        final JavaInheritanceResolver.SupertypeResult second = resolver.getAllSupertypes("sample.tsj50.Child");

        assertEquals(List.of(
                "sample/tsj50/Base",
                "java/lang/Object",
                "sample/tsj50/Mid",
                "sample/tsj50/Root"
        ), first.supertypes());
        assertEquals(first.supertypes(), second.supertypes());
        assertTrue(first.diagnostics().isEmpty());
        assertEquals(1, resolver.supertypeCacheSize());
    }

    @Test
    void getAllSupertypesDetectsCyclesAndCutsTraversalSafely() {
        final Map<String, JavaClassfileReader.RawClassInfo> classes = new LinkedHashMap<>();
        classes.put("sample/tsj50/CycleA", rawClass("sample/tsj50/CycleA", "sample/tsj50/CycleB", List.of()));
        classes.put("sample/tsj50/CycleB", rawClass("sample/tsj50/CycleB", "sample/tsj50/CycleA", List.of()));

        final JavaInheritanceResolver resolver = new JavaInheritanceResolver(fromMap(classes));
        final JavaInheritanceResolver.SupertypeResult result = resolver.getAllSupertypes("sample.tsj50.CycleA");

        assertEquals(List.of("sample/tsj50/CycleB"), result.supertypes());
        assertTrue(result.diagnostics().stream().anyMatch(line -> line.contains("cycle")));
    }

    @Test
    void collectMembersIncludesOriginAndAppliesVisibilityRules() {
        final Map<String, JavaClassfileReader.RawClassInfo> classes = new LinkedHashMap<>();
        classes.put("sample/tsj50/Base", rawClass(
                "sample/tsj50/Base",
                "java/lang/Object",
                List.of(),
                List.of(),
                List.of(
                        rawMethod("publicApi", "()V", 0x0001),
                        rawMethod("protectedApi", "()V", 0x0004),
                        rawMethod("packageApi", "()V", 0x0000),
                        rawMethod("privateApi", "()V", 0x0002)
                )
        ));
        classes.put("sample/tsj50/Child", rawClass(
                "sample/tsj50/Child",
                "sample/tsj50/Base",
                List.of(),
                List.of(),
                List.of(rawMethod("publicApi", "()V", 0x0001))
        ));
        classes.put("sample/tsj50/sub/Requester", rawClass(
                "sample/tsj50/sub/Requester",
                "sample/tsj50/Child",
                List.of()
        ));
        classes.put("java/lang/Object", rawClass("java/lang/Object", null, List.of()));

        final JavaInheritanceResolver resolver = new JavaInheritanceResolver(fromMap(classes));
        final JavaInheritanceResolver.LookupContext context = JavaInheritanceResolver.LookupContext.unrestricted(
                "sample/tsj50/sub/Requester"
        );

        final JavaInheritanceResolver.MemberLookupResult protectedMembers =
                resolver.collectMembers("sample.tsj50.Child", "protectedApi", context);
        assertEquals(1, protectedMembers.members().size());
        assertTrue(protectedMembers.members().get(0).accessible());
        assertTrue(protectedMembers.members().get(0).inherited());
        assertEquals("sample/tsj50/Base", protectedMembers.members().get(0).ownerInternalName());

        final JavaInheritanceResolver.MemberLookupResult packageMembers =
                resolver.collectMembers("sample.tsj50.Child", "packageApi", context);
        assertEquals(1, packageMembers.members().size());
        assertFalse(packageMembers.members().get(0).accessible());

        final JavaInheritanceResolver.MemberLookupResult privateMembers =
                resolver.collectMembers("sample.tsj50.Child", "privateApi", context);
        assertEquals(1, privateMembers.members().size());
        assertFalse(privateMembers.members().get(0).accessible());
    }

    @Test
    void collectMembersAppliesModuleReadabilityAndExportRules() {
        final Map<String, JavaClassfileReader.RawClassInfo> classes = new LinkedHashMap<>();
        classes.put("sample/tsj50/moda/PublicApi", rawClass(
                "sample/tsj50/moda/PublicApi",
                "java/lang/Object",
                List.of(),
                List.of(),
                List.of(rawMethod("call", "()V", 0x0001))
        ));
        classes.put("sample/tsj50/modb/Requester", rawClass(
                "sample/tsj50/modb/Requester",
                "java/lang/Object",
                List.of()
        ));
        classes.put("java/lang/Object", rawClass("java/lang/Object", null, List.of()));

        final JavaInheritanceResolver resolver = new JavaInheritanceResolver(fromMap(classes));
        final Map<String, String> classModules = Map.of(
                "sample/tsj50/moda/PublicApi", "module.a",
                "sample/tsj50/modb/Requester", "module.b"
        );
        final JavaInheritanceResolver.LookupContext unreadable = new JavaInheritanceResolver.LookupContext(
                "sample/tsj50/modb/Requester",
                "module.b",
                classModules,
                Map.of(),
                Map.of("module.a", Set.of("sample/tsj50/moda"))
        );
        final JavaInheritanceResolver.MemberLookupResult unreadableResult = resolver.collectMembers(
                "sample.tsj50.moda.PublicApi",
                "call",
                unreadable
        );
        assertFalse(unreadableResult.members().get(0).accessible());

        final JavaInheritanceResolver.LookupContext notExported = new JavaInheritanceResolver.LookupContext(
                "sample/tsj50/modb/Requester",
                "module.b",
                classModules,
                Map.of("module.b", Set.of("module.a")),
                Map.of("module.a", Set.of("sample/tsj50/internal"))
        );
        final JavaInheritanceResolver.MemberLookupResult notExportedResult = resolver.collectMembers(
                "sample.tsj50.moda.PublicApi",
                "call",
                notExported
        );
        assertFalse(notExportedResult.members().get(0).accessible());

        final JavaInheritanceResolver.LookupContext readableAndExported = new JavaInheritanceResolver.LookupContext(
                "sample/tsj50/modb/Requester",
                "module.b",
                classModules,
                Map.of("module.b", Set.of("module.a")),
                Map.of("module.a", Set.of("sample/tsj50/moda"))
        );
        final JavaInheritanceResolver.MemberLookupResult readableAndExportedResult = resolver.collectMembers(
                "sample.tsj50.moda.PublicApi",
                "call",
                readableAndExported
        );
        assertTrue(readableAndExportedResult.members().get(0).accessible());
    }

    @Test
    void memberScanningUsesDeterministicCacheAcrossLookups() {
        final Map<String, JavaClassfileReader.RawClassInfo> classes = new LinkedHashMap<>();
        classes.put("sample/tsj50/Base", rawClass(
                "sample/tsj50/Base",
                "java/lang/Object",
                List.of(),
                List.of(rawField("value", "I", 0x0001)),
                List.of(rawMethod("value", "()I", 0x0001))
        ));
        classes.put("sample/tsj50/Child", rawClass("sample/tsj50/Child", "sample/tsj50/Base", List.of()));
        classes.put("java/lang/Object", rawClass("java/lang/Object", null, List.of()));
        final JavaInheritanceResolver resolver = new JavaInheritanceResolver(fromMap(classes));
        final JavaInheritanceResolver.LookupContext context = JavaInheritanceResolver.LookupContext.unrestricted(
                "sample/tsj50/Child"
        );

        resolver.collectMembers("sample.tsj50.Child", "value", context);
        assertEquals(3, resolver.memberCacheSize());
        assertEquals(1, resolver.memberScanCount("sample/tsj50/Child", "value"));
        assertEquals(1, resolver.memberScanCount("sample/tsj50/Base", "value"));
        assertEquals(1, resolver.memberScanCount("java/lang/Object", "value"));

        resolver.collectMembers("sample.tsj50.Child", "value", context);
        assertEquals(3, resolver.memberCacheSize());
        assertEquals(1, resolver.memberScanCount("sample/tsj50/Child", "value"));
        assertEquals(1, resolver.memberScanCount("sample/tsj50/Base", "value"));
        assertEquals(1, resolver.memberScanCount("java/lang/Object", "value"));
    }

    private static Function<String, Optional<JavaClassfileReader.RawClassInfo>> fromMap(
            final Map<String, JavaClassfileReader.RawClassInfo> classes
    ) {
        return className -> Optional.ofNullable(classes.get(className.replace('.', '/')));
    }

    private static JavaClassfileReader.RawClassInfo rawClass(
            final String internalName,
            final String superInternalName,
            final List<String> interfaces
    ) {
        return rawClass(internalName, superInternalName, interfaces, List.of(), List.of());
    }

    private static JavaClassfileReader.RawClassInfo rawClass(
            final String internalName,
            final String superInternalName,
            final List<String> interfaces,
            final List<JavaClassfileReader.RawFieldInfo> fields,
            final List<JavaClassfileReader.RawMethodInfo> methods
    ) {
        return new JavaClassfileReader.RawClassInfo(
                Path.of(internalName + ".class"),
                0,
                65,
                0x0001,
                internalName,
                superInternalName,
                interfaces,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                fields,
                methods,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static JavaClassfileReader.RawMethodInfo rawMethod(
            final String name,
            final String descriptor,
            final int accessFlags
    ) {
        return new JavaClassfileReader.RawMethodInfo(
                name,
                descriptor,
                accessFlags,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    private static JavaClassfileReader.RawFieldInfo rawField(
            final String name,
            final String descriptor,
            final int accessFlags
    ) {
        return new JavaClassfileReader.RawFieldInfo(
                name,
                descriptor,
                accessFlags,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
