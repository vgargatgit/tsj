package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSamAnalyzerTest {
    private final JavaSamAnalyzer analyzer = new JavaSamAnalyzer();

    @Test
    void detectsSimpleSamMethodAndExposesCanonicalMetadata() {
        final JavaSamAnalyzer.SamResult result = analyzer.analyze(SimpleCallback.class);

        assertTrue(result.functional());
        assertNotNull(result.samMethod());
        assertEquals("apply", result.samMethod().name());
        assertEquals("(I)I", result.samMethod().descriptor());
        assertTrue(result.candidateDescriptors().contains("apply(I)I"));
    }

    @Test
    void excludesStaticDefaultAndObjectMembersFromSamDetection() {
        final JavaSamAnalyzer.SamResult defaultResult = analyzer.analyze(WithDefaultAndStatic.class);
        assertTrue(defaultResult.functional());
        assertEquals("run", defaultResult.samMethod().name());

        final JavaSamAnalyzer.SamResult objectOnlyResult = analyzer.analyze(ObjectMethodsOnly.class);
        assertFalse(objectOnlyResult.functional());
        assertNull(objectOnlyResult.samMethod());
        assertTrue(objectOnlyResult.candidateDescriptors().isEmpty());
    }

    @Test
    void handlesInheritedAbstractMethodsAndGenericSpecialization() {
        final JavaSamAnalyzer.SamResult inherited = analyzer.analyze(ChildCallback.class);
        assertTrue(inherited.functional());
        assertNotNull(inherited.samMethod());
        assertEquals("call", inherited.samMethod().name());

        final JavaSamAnalyzer.SamResult specialized = analyzer.analyze(StringMapper.class);
        assertTrue(specialized.functional());
        assertNotNull(specialized.samMethod());
        assertEquals("map", specialized.samMethod().name());
    }

    @Test
    void reportsNonFunctionalWhenMultipleAbstractMethodsExist() {
        final JavaSamAnalyzer.SamResult result = analyzer.analyze(MultiMethod.class);

        assertFalse(result.functional());
        assertNull(result.samMethod());
        assertEquals(2, result.candidateDescriptors().size());
    }

    @Test
    void validatesFunctionalInterfaceAnnotationAsConsistencyHint() {
        final JavaSamAnalyzer.SamResult annotatedGood = analyzer.analyze(AnnotatedGood.class);
        assertTrue(annotatedGood.functional());
        assertTrue(annotatedGood.functionalInterfaceAnnotated());
        assertTrue(annotatedGood.diagnostics().isEmpty());

        final JavaSamAnalyzer.SamResult unannotated = analyzer.analyze(SimpleCallback.class);
        assertTrue(unannotated.functional());
        assertFalse(unannotated.functionalInterfaceAnnotated());
    }

    interface SimpleCallback {
        int apply(int value);
    }

    interface WithDefaultAndStatic {
        void run();

        default void noop() {
        }

        static void helper() {
        }
    }

    interface ObjectMethodsOnly {
        boolean equals(Object value);

        int hashCode();
    }

    interface ParentCallback {
        String call(String value);
    }

    interface ChildCallback extends ParentCallback {
    }

    interface GenericMapper<T> {
        T map(T value);
    }

    interface StringMapper extends GenericMapper<String> {
    }

    interface MultiMethod {
        void first();

        void second();
    }

    @FunctionalInterface
    interface AnnotatedGood {
        void run();
    }
}
