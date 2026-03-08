package dev.tsj.compiler.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictEligibilityCheckerTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsDynamicComputedPropertyWrite() throws Exception {
        final Path entryFile = tempDir.resolve("strict-dynamic-write.ts");
        Files.writeString(
                entryFile,
                """
                const target: any = {};
                const key = "x";
                target[key] = 1;
                """,
                UTF_8
        );

        final StrictEligibilityChecker.StrictEligibilityAnalysis analysis =
                new StrictEligibilityChecker().analyze(entryFile);

        assertFalse(analysis.eligible());
        assertNotNull(analysis.violation());
        assertEquals("TSJ-STRICT-DYNAMIC-PROPERTY-ADD", analysis.violation().featureId());
        assertEquals(3, analysis.violation().line());
    }

    @Test
    void rejectsPrototypeMutationFromImportedModule() throws Exception {
        final Path entryFile = tempDir.resolve("entry.ts");
        final Path dependencyFile = tempDir.resolve("dep.ts");
        Files.writeString(
                dependencyFile,
                """
                class Service {}
                Service.prototype.extra = () => 1;
                export const value = new Service();
                """,
                UTF_8
        );
        Files.writeString(
                entryFile,
                """
                import { value } from "./dep.ts";
                console.log(value);
                """,
                UTF_8
        );

        final StrictEligibilityChecker.StrictEligibilityAnalysis analysis =
                new StrictEligibilityChecker().analyze(entryFile);

        assertFalse(analysis.eligible());
        assertNotNull(analysis.violation());
        assertEquals("TSJ-STRICT-PROTOTYPE-MUTATION", analysis.violation().featureId());
        assertEquals(dependencyFile.toAbsolutePath().normalize(), analysis.violation().filePath());
        assertEquals(2, analysis.violation().line());
        assertTrue(analysis.analyzedFileCount() >= 2);
    }

    @Test
    void allowsStaticClassObjectFunctionAndModuleSurfaces() throws Exception {
        final Path entryFile = tempDir.resolve("entry-safe.ts");
        final Path moduleFile = tempDir.resolve("dep-safe.ts");
        Files.writeString(
                moduleFile,
                """
                export function buildOwner(id: number, name: string) {
                  return { id, name };
                }
                """,
                UTF_8
        );
        Files.writeString(
                entryFile,
                """
                import { buildOwner } from "./dep-safe.ts";

                class OwnerService {
                  describe(id: number, name: string): string {
                    const owner = buildOwner(id, name);
                    return owner.id + ":" + owner.name;
                  }
                }

                console.log(new OwnerService().describe(1, "Ada"));
                """,
                UTF_8
        );

        final StrictEligibilityChecker.StrictEligibilityAnalysis analysis =
                new StrictEligibilityChecker().analyze(entryFile);

        assertTrue(analysis.eligible());
        assertEquals(null, analysis.violation());
        assertTrue(analysis.analyzedFileCount() >= 2);
    }

    @Test
    void decisionsRemainDeterministicAcrossRepeatedRuns() throws Exception {
        final Path entryFile = tempDir.resolve("entry-deterministic.ts");
        Files.writeString(
                entryFile,
                """
                function make() {
                  const f = new Function("return 1;");
                  return f();
                }
                console.log(make());
                """,
                UTF_8
        );

        final StrictEligibilityChecker checker = new StrictEligibilityChecker();
        final StrictEligibilityChecker.StrictEligibilityAnalysis first = checker.analyze(entryFile);
        final StrictEligibilityChecker.StrictEligibilityAnalysis second = checker.analyze(entryFile);

        assertFalse(first.eligible());
        assertFalse(second.eligible());
        assertNotNull(first.violation());
        assertNotNull(second.violation());
        assertEquals(first.violation().featureId(), second.violation().featureId());
        assertEquals(first.violation().filePath(), second.violation().filePath());
        assertEquals(first.violation().line(), second.violation().line());
        assertEquals(first.violation().column(), second.violation().column());
    }

    @Test
    void ignoresStrictMarkersInsideCommentsAndStrings() throws Exception {
        final Path entryFile = tempDir.resolve("entry-comment-string-safe.ts");
        Files.writeString(
                entryFile,
                """
                // eval("1 + 2");
                // import("./dep.ts");
                const text = "new Proxy({}, {}) delete value.x Object.setPrototypeOf({}, null)";
                console.log(text.length);
                """,
                UTF_8
        );

        final StrictEligibilityChecker.StrictEligibilityAnalysis analysis =
                new StrictEligibilityChecker().analyze(entryFile);

        assertTrue(analysis.eligible());
        assertEquals(null, analysis.violation());
    }

    @Test
    void rejectsUncheckedAnyMemberInvocationFromTypedFunctionParameter() throws Exception {
        final Path entryFile = tempDir.resolve("entry-any-param.ts");
        Files.writeString(
                entryFile,
                """
                function callDynamic(target: any): number {
                  return target.make();
                }
                console.log(callDynamic({ make: () => 1 }));
                """,
                UTF_8
        );

        final StrictEligibilityChecker.StrictEligibilityAnalysis analysis =
                new StrictEligibilityChecker().analyze(entryFile);

        assertFalse(analysis.eligible());
        assertNotNull(analysis.violation());
        assertEquals("TSJ-STRICT-UNCHECKED-ANY-MEMBER-INVOKE", analysis.violation().featureId());
        assertEquals(2, analysis.violation().line());
    }

    @Test
    void rejectsObjectSetPrototypeOfAsPrototypeMutation() throws Exception {
        final Path entryFile = tempDir.resolve("entry-set-prototype.ts");
        Files.writeString(
                entryFile,
                """
                const value = {};
                Object.setPrototypeOf(value, null);
                console.log(value);
                """,
                UTF_8
        );

        final StrictEligibilityChecker.StrictEligibilityAnalysis analysis =
                new StrictEligibilityChecker().analyze(entryFile);

        assertFalse(analysis.eligible());
        assertNotNull(analysis.violation());
        assertEquals("TSJ-STRICT-PROTOTYPE-MUTATION", analysis.violation().featureId());
        assertEquals(2, analysis.violation().line());
    }

    @Test
    void rejectsEvalInsideTemplateExpression() throws Exception {
        final Path entryFile = tempDir.resolve("entry-template-eval.ts");
        Files.writeString(
                entryFile,
                """
                const message = `${eval("1 + 2")}`;
                console.log(message);
                """,
                UTF_8
        );

        final StrictEligibilityChecker.StrictEligibilityAnalysis analysis =
                new StrictEligibilityChecker().analyze(entryFile);

        assertFalse(analysis.eligible());
        assertNotNull(analysis.violation());
        assertEquals("TSJ-STRICT-EVAL", analysis.violation().featureId());
        assertEquals(1, analysis.violation().line());
    }

    @Test
    void exposesDeterministicSupportedStrictFeatureCatalog() {
        final Set<String> featureIds = StrictEligibilityChecker.supportedFeatureIds();

        assertTrue(featureIds.contains("TSJ-STRICT-DYNAMIC-IMPORT"));
        assertTrue(featureIds.contains("TSJ-STRICT-EVAL"));
        assertTrue(featureIds.contains("TSJ-STRICT-FUNCTION-CONSTRUCTOR"));
        assertTrue(featureIds.contains("TSJ-STRICT-PROXY"));
        assertTrue(featureIds.contains("TSJ-STRICT-DELETE"));
        assertTrue(featureIds.contains("TSJ-STRICT-PROTOTYPE-MUTATION"));
        assertTrue(featureIds.contains("TSJ-STRICT-DYNAMIC-PROPERTY-ADD"));
        assertTrue(featureIds.contains("TSJ-STRICT-UNCHECKED-ANY-MEMBER-INVOKE"));
        assertEquals(8, featureIds.size());
    }
}
