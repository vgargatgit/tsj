package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjRuntimeTest {
    @Test
    void addPerformsNumericAdditionForNumbers() {
        assertEquals(7, TsjRuntime.add(3, 4));
    }

    @Test
    void addPerformsStringConcatenationWhenAnyOperandIsString() {
        assertEquals("hello 2", TsjRuntime.add("hello ", 2));
    }

    @Test
    void arithmeticHelpersSupportSubtractMultiplyAndDivide() {
        assertEquals(5, TsjRuntime.subtract(8, 3));
        assertEquals(12, TsjRuntime.multiply(3, 4));
        assertEquals(2.5d, TsjRuntime.divide(5, 2));
    }

    @Test
    void comparisonHelpersEvaluateNumericRelations() {
        assertTrue(TsjRuntime.lessThan(1, 2));
        assertTrue(TsjRuntime.lessThanOrEqual(2, 2));
        assertTrue(TsjRuntime.greaterThan(3, 2));
        assertTrue(TsjRuntime.greaterThanOrEqual(4, 4));
    }

    @Test
    void strictEqualsHandlesNumbersAndNan() {
        assertTrue(TsjRuntime.strictEquals(4, 4.0d));
        assertFalse(TsjRuntime.strictEquals(Double.NaN, Double.NaN));
        assertFalse(TsjRuntime.strictEquals(1, "1"));
    }

    @Test
    void truthyMatchesSupportedSubsetRules() {
        assertFalse(TsjRuntime.truthy(0));
        assertFalse(TsjRuntime.truthy(""));
        assertFalse(TsjRuntime.truthy(null));
        assertTrue(TsjRuntime.truthy(1));
        assertTrue(TsjRuntime.truthy("x"));
    }

    @Test
    void displayStringFormatsWholeAndFractionalNumbers() {
        assertEquals("5", TsjRuntime.toDisplayString(5.0d));
        assertEquals("2.25", TsjRuntime.toDisplayString(2.25d));
    }

    @Test
    void callInvokesTsjCallableWithArguments() {
        final TsjCallable callable = args -> TsjRuntime.add(args[0], args[1]);
        assertEquals(9, TsjRuntime.call(callable, 4, 5));
    }

    @Test
    void callRejectsNonCallableValues() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.call("not-callable")
        );
        assertTrue(exception.getMessage().contains("Value is not callable"));
    }
}
