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
    void strictEqualsDistinguishesNullAndUndefined() {
        assertTrue(TsjRuntime.strictEquals(TsjRuntime.undefined(), TsjRuntime.undefined()));
        assertFalse(TsjRuntime.strictEquals(null, TsjRuntime.undefined()));
    }

    @Test
    void abstractEqualsSupportsNullishAndPrimitiveCoercions() {
        assertTrue(TsjRuntime.abstractEquals(null, TsjRuntime.undefined()));
        assertTrue(TsjRuntime.abstractEquals(4, "4"));
        assertTrue(TsjRuntime.abstractEquals("7", 7.0d));
        assertTrue(TsjRuntime.abstractEquals(true, 1));
        assertTrue(TsjRuntime.abstractEquals(false, 0));
        assertFalse(TsjRuntime.abstractEquals(TsjRuntime.undefined(), 0));
        assertFalse(TsjRuntime.abstractEquals(null, 0));
        assertFalse(TsjRuntime.abstractEquals("x", 0));
    }

    @Test
    void truthyMatchesSupportedSubsetRules() {
        assertFalse(TsjRuntime.truthy(0));
        assertFalse(TsjRuntime.truthy(""));
        assertFalse(TsjRuntime.truthy(null));
        assertFalse(TsjRuntime.truthy(TsjRuntime.undefined()));
        assertTrue(TsjRuntime.truthy(1));
        assertTrue(TsjRuntime.truthy("x"));
    }

    @Test
    void displayStringFormatsWholeAndFractionalNumbers() {
        assertEquals("5", TsjRuntime.toDisplayString(5.0d));
        assertEquals("2.25", TsjRuntime.toDisplayString(2.25d));
        assertEquals("undefined", TsjRuntime.toDisplayString(TsjRuntime.undefined()));
    }

    @Test
    void toNumberHandlesNullBooleanStringAndUndefined() {
        assertEquals(0.0d, TsjRuntime.toNumber(null));
        assertEquals(1.0d, TsjRuntime.toNumber(true));
        assertEquals(0.0d, TsjRuntime.toNumber(false));
        assertEquals(42.0d, TsjRuntime.toNumber("42"));
        assertTrue(Double.isNaN(TsjRuntime.toNumber(TsjRuntime.undefined())));
    }

    @Test
    void addConcatenatesUndefinedLikeJavaScript() {
        assertEquals("value=undefined", TsjRuntime.add("value=", TsjRuntime.undefined()));
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

    @Test
    void objectLiteralSupportsGetAndSetProperty() {
        final Object object = TsjRuntime.objectLiteral("name", "tsj", "count", 1);
        assertEquals("tsj", TsjRuntime.getProperty(object, "name"));
        assertEquals(1, TsjRuntime.getProperty(object, "count"));

        TsjRuntime.setProperty(object, "count", 2);
        assertEquals(2, TsjRuntime.getProperty(object, "count"));
    }

    @Test
    void arrayLiteralSupportsLengthAndIndexedProperties() {
        final Object array = TsjRuntime.arrayLiteral("a", "b", 3);
        assertEquals(3, TsjRuntime.getProperty(array, "length"));
        assertEquals("a", TsjRuntime.getProperty(array, "0"));
        assertEquals("b", TsjRuntime.getProperty(array, "1"));
        assertEquals(3, TsjRuntime.getProperty(array, "2"));
    }

    @Test
    void getPropertyReturnsUndefinedForMissingKey() {
        final Object object = TsjRuntime.objectLiteral("name", "tsj");
        assertEquals(TsjRuntime.undefined(), TsjRuntime.getProperty(object, "missing"));
    }

    @Test
    void deletePropertyRemovesOwnValue() {
        final Object object = TsjRuntime.objectLiteral("name", "tsj");
        assertTrue(TsjRuntime.deleteProperty(object, "name"));
        assertEquals(TsjRuntime.undefined(), TsjRuntime.getProperty(object, "name"));
    }

    @Test
    void setPrototypeSupportsObjectAndNullLikeValues() {
        final Object prototype = TsjRuntime.objectLiteral("name", "base");
        final Object object = TsjRuntime.objectLiteral();

        TsjRuntime.setPrototype(object, prototype);
        assertEquals("base", TsjRuntime.getProperty(object, "name"));

        TsjRuntime.setPrototype(object, null);
        assertEquals(TsjRuntime.undefined(), TsjRuntime.getProperty(object, "name"));
    }

    @Test
    void constructAndInvokeMemberSupportClassMethodsAndInheritance() {
        final TsjClass base = new TsjClass("Base", null);
        base.setConstructor((thisObject, args) -> {
            TsjRuntime.setProperty(thisObject, "value", args.length > 0 ? args[0] : 0);
            return null;
        });
        base.defineMethod("read", (thisObject, args) -> TsjRuntime.getProperty(thisObject, "value"));

        final TsjClass derived = new TsjClass("Derived", base);
        derived.setConstructor((thisObject, args) -> {
            base.invokeConstructor(thisObject, args);
            TsjRuntime.setProperty(
                    thisObject,
                    "value",
                    TsjRuntime.add(TsjRuntime.getProperty(thisObject, "value"), 1)
            );
            return null;
        });
        derived.defineMethod(
                "doubleValue",
                (thisObject, args) -> TsjRuntime.multiply(TsjRuntime.getProperty(thisObject, "value"), 2)
        );

        final Object instance = TsjRuntime.construct(derived, 4);
        assertEquals(5, TsjRuntime.invokeMember(instance, "read"));
        assertEquals(10, TsjRuntime.invokeMember(instance, "doubleValue"));
    }

    @Test
    void invokeMemberFailsWhenOwnDataPropertyShadowsPrototypeMethod() {
        final TsjClass base = new TsjClass("Base", null);
        base.defineMethod("read", (thisObject, args) -> TsjRuntime.getProperty(thisObject, "read"));

        final Object instance = TsjRuntime.construct(base);
        TsjRuntime.setProperty(instance, "read", 5);

        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.invokeMember(instance, "read")
        );
        assertTrue(exception.getMessage().contains("not callable"));
    }

    @Test
    void constructRejectsNonClassValues() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.construct("bad")
        );
        assertTrue(exception.getMessage().contains("not constructable"));
    }

    @Test
    void constructFallsBackToSuperclassConstructorWhenSubclassHasNoConstructor() {
        final TsjClass base = new TsjClass("Base", null);
        base.setConstructor((thisObject, args) -> {
            TsjRuntime.setProperty(thisObject, "value", args[0]);
            return null;
        });

        final TsjClass derived = new TsjClass("Derived", base);
        final Object instance = TsjRuntime.construct(derived, 9);

        assertEquals(9, TsjRuntime.getProperty(instance, "value"));
    }

    @Test
    void objectLiteralRejectsOddKeyValueArgumentCount() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.objectLiteral("name", "tsj", "dangling")
        );
        assertTrue(exception.getMessage().contains("key/value pairs"));
    }

    @Test
    void getAndSetPropertyRejectNonObjectReceivers() {
        final IllegalArgumentException getException = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.getProperty("bad", "x")
        );
        assertTrue(getException.getMessage().contains("Cannot get property"));

        final IllegalArgumentException setException = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.setProperty("bad", "x", 1)
        );
        assertTrue(setException.getMessage().contains("Cannot set property"));
    }

    @Test
    void invokeMemberRejectsMissingReceiverType() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.invokeMember("bad", "x")
        );
        assertTrue(exception.getMessage().contains("Cannot invoke member"));
    }

    @Test
    void promiseHelpersResolveThenAndRejectPaths() {
        final Object resolved = TsjRuntime.promiseResolve(5);
        final Object rejected = TsjRuntime.promiseReject("bad");

        final Object chained = TsjRuntime.promiseThen(
                resolved,
                (TsjCallable) args -> TsjRuntime.add(args[0], 1),
                TsjRuntime.undefined()
        );
        final Object passThroughRejection = TsjRuntime.promiseThen(
                rejected,
                TsjRuntime.undefined(),
                TsjRuntime.undefined()
        );

        assertTrue(chained instanceof TsjPromise);
        assertTrue(passThroughRejection instanceof TsjPromise);
    }

    @Test
    void promiseBuiltinExposesCombinators() {
        final Object builtin = TsjRuntime.promiseBuiltin();
        final Object iterable = TsjRuntime.arrayLiteral(TsjRuntime.promiseResolve(1), TsjRuntime.promiseResolve(2));

        assertTrue(TsjRuntime.invokeMember(builtin, "all", iterable) instanceof TsjPromise);
        assertTrue(TsjRuntime.invokeMember(builtin, "race", iterable) instanceof TsjPromise);
        assertTrue(TsjRuntime.invokeMember(builtin, "allSettled", iterable) instanceof TsjPromise);
        assertTrue(TsjRuntime.invokeMember(builtin, "any", iterable) instanceof TsjPromise);
    }

    @Test
    void raiseWrapsNonExceptionValuesAndNormalizeThrownUnwrapsThem() {
        final RuntimeException wrapped = TsjRuntime.raise("boom");
        assertEquals("boom", TsjRuntime.normalizeThrown(wrapped));

        final IllegalStateException direct = new IllegalStateException("x");
        assertEquals(direct, TsjRuntime.normalizeThrown(TsjRuntime.raise(direct)));
    }
}
