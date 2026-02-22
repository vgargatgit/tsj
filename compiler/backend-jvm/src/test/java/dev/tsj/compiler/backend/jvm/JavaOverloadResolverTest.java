package dev.tsj.compiler.backend.jvm;

import dev.tsj.runtime.TsjJavaInterop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaOverloadResolverTest {
    private final JavaOverloadResolver resolver = new JavaOverloadResolver();

    @Test
    void resolvesMostSpecificNumericCandidate() {
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                OverloadFixture.class,
                "pick",
                JavaOverloadResolver.InvokeKind.STATIC_METHOD
        );

        final JavaOverloadResolver.Resolution resolution = resolver.resolve(
                candidates,
                List.of(JavaOverloadResolver.Argument.descriptor("I"))
        );

        assertEquals(JavaOverloadResolver.Status.SELECTED, resolution.status());
        assertNotNull(resolution.selected());
        assertEquals("(I)Ljava/lang/String;", resolution.selected().descriptor());
        assertEquals(JavaOverloadResolver.InvokeKind.STATIC_METHOD, resolution.selected().invokeKind());
    }

    @Test
    void resolvesVarargsWhenNoFixedArityCandidateMatches() {
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                OverloadFixture.class,
                "join",
                JavaOverloadResolver.InvokeKind.STATIC_METHOD
        );

        final JavaOverloadResolver.Resolution resolution = resolver.resolve(
                candidates,
                List.of(
                        JavaOverloadResolver.Argument.descriptor("Ljava/lang/String;"),
                        JavaOverloadResolver.Argument.descriptor("Ljava/lang/String;"),
                        JavaOverloadResolver.Argument.descriptor("Ljava/lang/String;")
                )
        );

        assertEquals(JavaOverloadResolver.Status.SELECTED, resolution.status());
        assertNotNull(resolution.selected());
        assertEquals("(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;", resolution.selected().descriptor());
    }

    @Test
    void reportsNoApplicableCandidatesWithDetailedSummary() {
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                OverloadFixture.class,
                "needsInt",
                JavaOverloadResolver.InvokeKind.STATIC_METHOD
        );

        final JavaOverloadResolver.Resolution resolution = resolver.resolve(
                candidates,
                List.of(JavaOverloadResolver.Argument.descriptor("Ljava/lang/String;"))
        );

        assertEquals(JavaOverloadResolver.Status.NO_APPLICABLE, resolution.status());
        assertNull(resolution.selected());
        assertTrue(resolution.diagnostic().contains("No applicable candidate"));
        assertTrue(resolution.diagnostic().contains("needsInt"));
        assertTrue(resolution.diagnostic().contains("candidates"));
    }

    @Test
    void reportsAmbiguousBestCandidatesWithCandidateSummary() {
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                OverloadFixture.class,
                "ambiguous",
                JavaOverloadResolver.InvokeKind.STATIC_METHOD
        );

        final JavaOverloadResolver.Resolution resolution = resolver.resolve(
                candidates,
                List.of(JavaOverloadResolver.Argument.descriptor("Ljava/lang/String;"))
        );

        assertEquals(JavaOverloadResolver.Status.AMBIGUOUS, resolution.status());
        assertNull(resolution.selected());
        assertTrue(resolution.diagnostic().contains("Ambiguous best candidates"));
        assertTrue(resolution.diagnostic().contains("Ljava/io/Serializable;"));
        assertTrue(resolution.diagnostic().contains("Ljava/lang/Comparable;"));
        final int serializableIndex = resolution.diagnostic().indexOf("Ljava/io/Serializable;");
        final int comparableIndex = resolution.diagnostic().indexOf("Ljava/lang/Comparable;");
        assertTrue(serializableIndex >= 0 && comparableIndex >= 0 && serializableIndex < comparableIndex);
    }

    @Test
    void candidateEnumerationIsDeterministicByDescriptorForMethods() {
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                OverloadFixture.class,
                "pick",
                JavaOverloadResolver.InvokeKind.STATIC_METHOD
        );
        final List<String> descriptors = candidates.stream()
                .map(candidate -> candidate.identity().descriptor())
                .toList();
        final List<String> sortedDescriptors = descriptors.stream().sorted().toList();
        assertEquals(sortedDescriptors, descriptors);
    }

    @Test
    void nullabilityCompatibilityRejectsNonNullOnlyCandidateForNullArgument() {
        final JavaOverloadResolver.Candidate candidate = new JavaOverloadResolver.Candidate(
                new JavaOverloadResolver.MemberIdentity(
                        OverloadFixture.class.getName(),
                        "nullableProbe",
                        "(Ljava/lang/String;)Ljava/lang/String;",
                        JavaOverloadResolver.InvokeKind.STATIC_METHOD
                ),
                false,
                List.of("Ljava/lang/String;"),
                List.of(JavaNullabilityAnalyzer.NullabilityState.NON_NULL)
        );

        final JavaOverloadResolver.Resolution resolution = resolver.resolve(
                List.of(candidate),
                List.of(JavaOverloadResolver.Argument.nullArgument())
        );

        assertEquals(JavaOverloadResolver.Status.NO_APPLICABLE, resolution.status());
        assertTrue(resolution.diagnostic().contains("nullability"));
    }

    @Test
    void certifiedCrossCheckMatchesRuntimeDispatchForCoveredCases() {
        final String className = OverloadFixture.class.getName();
        final List<JavaOverloadResolver.Candidate> candidates = JavaOverloadResolver.candidatesForClassMethod(
                OverloadFixture.class,
                "pick",
                JavaOverloadResolver.InvokeKind.STATIC_METHOD
        );

        final JavaOverloadResolver.Resolution intResolution = resolver.resolve(
                candidates,
                List.of(JavaOverloadResolver.Argument.descriptor("I"))
        );
        final Object runtimeInt = TsjJavaInterop.invokeBinding(className, "pick", 5);
        final Object preselectedInt = TsjJavaInterop.invokeBindingPreselected(
                className,
                "pick",
                intResolution.selected().owner(),
                intResolution.selected().name(),
                intResolution.selected().descriptor(),
                intResolution.selected().invokeKind().name(),
                5
        );
        assertEquals(runtimeInt, preselectedInt);

        final JavaOverloadResolver.Resolution doubleResolution = resolver.resolve(
                candidates,
                List.of(JavaOverloadResolver.Argument.descriptor("D"))
        );
        final Object runtimeDouble = TsjJavaInterop.invokeBinding(className, "pick", 2.5d);
        final Object preselectedDouble = TsjJavaInterop.invokeBindingPreselected(
                className,
                "pick",
                doubleResolution.selected().owner(),
                doubleResolution.selected().name(),
                doubleResolution.selected().descriptor(),
                doubleResolution.selected().invokeKind().name(),
                2.5d
        );
        assertEquals(runtimeDouble, preselectedDouble);
    }

    public static final class OverloadFixture {
        private OverloadFixture() {
        }

        public static String pick(final int value) {
            return "int";
        }

        public static String pick(final long value) {
            return "long";
        }

        public static String pick(final double value) {
            return "double";
        }

        public static String join(final String prefix, final String... values) {
            final StringBuilder builder = new StringBuilder(prefix);
            for (String value : values) {
                builder.append(":").append(value);
            }
            return builder.toString();
        }

        public static String needsInt(final int value) {
            return Integer.toString(value);
        }

        public static String ambiguous(final java.io.Serializable value) {
            return "serializable";
        }

        public static String ambiguous(final Comparable<?> value) {
            return "comparable";
        }
    }
}
