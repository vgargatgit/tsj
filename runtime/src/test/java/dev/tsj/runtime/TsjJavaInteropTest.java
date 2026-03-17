package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjJavaInteropTest {
    @Test
    void invokeBindingTreatsNullVarargsAsEmptyArguments() {
        final Object result = TsjJavaInterop.invokeBinding(
                "java.lang.System",
                "lineSeparator",
                (Object[]) null
        );
        assertEquals(System.lineSeparator(), result);
    }

    @Test
    void invokeBindingPreselectedTreatsNullVarargsAsEmptyArguments() {
        final Object result = TsjJavaInterop.invokeBindingPreselected(
                "java.lang.System",
                "lineSeparator",
                "java/lang/System",
                "lineSeparator",
                "()Ljava/lang/String;",
                "STATIC_METHOD",
                (Object[]) null
        );
        assertEquals(System.lineSeparator(), result);
    }

    @Test
    void invokeInstanceMemberTreatsNullVarargsAsEmptyArguments() {
        final StringBuilder builder = new StringBuilder("x");
        final Object result = TsjJavaInterop.invokeInstanceMember(builder, "length", (Object[]) null);
        assertEquals(1, result);
    }

    @Test
    void invokeInstanceMemberRawPreservesJavaCollectionResults() {
        final RawCollectionFixture fixture = new RawCollectionFixture();
        final Object result = TsjJavaInterop.invokeInstanceMemberRaw(fixture, "names");

        final List<?> names = assertInstanceOf(List.class, result);
        assertEquals(List.of("Ada", "Lin"), names);
    }

    @Test
    void invokeBindingPrefersMostSpecificReferenceOverloadWhenScoresTie() {
        final Object result = TsjJavaInterop.invokeBinding(
                OverloadParityFixture.class.getName(),
                "pickSpecific",
                java.util.List.of("value")
        );
        assertEquals("list", result);
    }

    @Test
    void invokeBindingFailsWithAmbiguousCandidateDiagnosticWhenNoSpecificityWinnerExists() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(OverloadParityFixture.class.getName(), "ambiguous", "value")
        );

        assertTrue(exception.getMessage().contains("Ambiguous interop candidates for `ambiguous`"));
        assertTrue(exception.getMessage().contains("ambiguous(Comparable)"));
        assertTrue(exception.getMessage().contains("ambiguous(Serializable)"));
        assertTrue(exception.getMessage().contains("score="));
        assertTrue(exception.getMessage().contains("reason="));
        final int serializableIndex = exception.getMessage().indexOf("ambiguous(Serializable)");
        final int comparableIndex = exception.getMessage().indexOf("ambiguous(Comparable)");
        assertTrue(serializableIndex >= 0 && comparableIndex >= 0 && serializableIndex < comparableIndex);
    }

    @Test
    void invokeBindingPrefersPrimitiveCandidateOverReferenceSupertypeOnTie() {
        final Object result = TsjJavaInterop.invokeBinding(
                OverloadParityFixture.class.getName(),
                "pickNumeric",
                Integer.valueOf(7)
        );
        assertEquals("int", result);
    }

    @Test
    void resolveExecutableDeduplicatesEquivalentHierarchyMethodsBeforeAmbiguityCheck() throws Exception {
        final Method superclassMethod = EquivalentHierarchyBase.class.getDeclaredMethod(
                "ping",
                String.class,
                Class.class
        );
        final Method subclassMethod = EquivalentHierarchyChild.class.getDeclaredMethod(
                "ping",
                String.class,
                Class.class
        );

        final Method resolveExecutable = TsjJavaInterop.class.getDeclaredMethod(
                "resolveExecutable",
                List.class,
                String.class,
                Object[].class
        );
        resolveExecutable.setAccessible(true);

        final Object resolved = assertDoesNotThrow(() -> {
            try {
                return resolveExecutable.invoke(
                        null,
                        List.of(superclassMethod, subclassMethod),
                        "ping",
                        new Object[]{"value", String.class}
                );
            } catch (final InvocationTargetException invocationTargetException) {
                final Throwable targetException = invocationTargetException.getTargetException();
                if (targetException instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(targetException);
            }
        });

        final Method memberAccessor = resolved.getClass().getDeclaredMethod("member");
        memberAccessor.setAccessible(true);
        final Method selectedMethod = (Method) memberAccessor.invoke(resolved);
        assertEquals(EquivalentHierarchyChild.class, selectedMethod.getDeclaringClass());
    }

    public static final class OverloadParityFixture {
        private OverloadParityFixture() {
        }

        public static String pickSpecific(final java.util.Collection value) {
            return "collection";
        }

        public static String pickSpecific(final java.util.List value) {
            return "list";
        }

        public static String ambiguous(final java.io.Serializable value) {
            return "serializable";
        }

        public static String ambiguous(final Comparable value) {
            return "comparable";
        }

        public static String pickNumeric(final int value) {
            return "int";
        }

        public static String pickNumeric(final Number value) {
            return "number";
        }
    }

    public static class EquivalentHierarchyBase {
        public CharSequence ping(final String value, final Class<?> type) {
            return value + ":" + type.getSimpleName();
        }
    }

    public static final class EquivalentHierarchyChild extends EquivalentHierarchyBase {
        @Override
        public String ping(final String value, final Class<?> type) {
            return value + ":" + type.getSimpleName();
        }
    }

    public static final class RawCollectionFixture {
        public List<String> names() {
            return List.of("Ada", "Lin");
        }
    }
}
