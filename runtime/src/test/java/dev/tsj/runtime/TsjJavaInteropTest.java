package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
