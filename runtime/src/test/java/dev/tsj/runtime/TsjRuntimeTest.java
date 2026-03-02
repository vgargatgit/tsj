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
        assertEquals(1.0d, TsjRuntime.modulo(7, 3));
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
    void abstractEqualsSupportsObjectToPrimitiveViaValueOfAndToString() {
        final TsjObject valueOfObject = new TsjObject(null);
        valueOfObject.setOwn("valueOf", (TsjMethod) (thisObject, args) -> 7);
        assertTrue(TsjRuntime.abstractEquals(valueOfObject, 7));
        assertTrue(TsjRuntime.abstractEquals(7, valueOfObject));

        final TsjObject toStringObject = new TsjObject(null);
        toStringObject.setOwn("valueOf", (TsjMethod) (thisObject, args) -> TsjRuntime.objectLiteral("x", 1));
        toStringObject.setOwn("toString", (TsjMethod) (thisObject, args) -> "8");
        assertTrue(TsjRuntime.abstractEquals(toStringObject, 8));
        assertTrue(TsjRuntime.abstractEquals("8", toStringObject));
    }

    @Test
    void abstractEqualsSupportsObjectToPrimitiveWithBooleanAndThisBinding() {
        final TsjObject boolObject = new TsjObject(null);
        boolObject.setOwn("valueOf", (TsjMethod) (thisObject, args) -> 1);
        assertTrue(TsjRuntime.abstractEquals(boolObject, true));
        assertFalse(TsjRuntime.abstractEquals(boolObject, false));

        final TsjClass boxClass = new TsjClass("Box", null);
        boxClass.setConstructor((thisObject, args) -> {
            TsjRuntime.setProperty(thisObject, "value", args[0]);
            return null;
        });
        boxClass.defineMethod("valueOf", (thisObject, args) -> TsjRuntime.getProperty(thisObject, "value"));
        final Object instance = TsjRuntime.construct(boxClass, 9);
        assertTrue(TsjRuntime.abstractEquals(instance, 9));
    }

    @Test
    void abstractEqualsSupportsDefaultObjectToStringFallback() {
        final TsjObject plainObject = new TsjObject(null);
        assertTrue(TsjRuntime.abstractEquals(plainObject, "[object Object]"));

        final TsjObject customObject = new TsjObject(null);
        customObject.setOwn("valueOf", 123);
        customObject.setOwn("toString", (TsjMethod) (thisObject, args) -> "11");
        assertTrue(TsjRuntime.abstractEquals(customObject, 11));
        assertFalse(TsjRuntime.abstractEquals(plainObject, null));
        assertFalse(TsjRuntime.abstractEquals(plainObject, TsjRuntime.undefined()));
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
    void logicalOperatorsPreserveOperandValuesAndShortCircuit() {
        final int[] hits = new int[]{0};

        final Object andResult = TsjRuntime.logicalAnd(false, () -> {
            hits[0] = hits[0] + 1;
            return "rhs-and";
        });
        final Object orResult = TsjRuntime.logicalOr(true, () -> {
            hits[0] = hits[0] + 1;
            return "rhs-or";
        });
        final Object nullishNull = TsjRuntime.nullishCoalesce(null, () -> {
            hits[0] = hits[0] + 1;
            return "rhs-null";
        });
        final Object nullishUndefined = TsjRuntime.nullishCoalesce(TsjRuntime.undefined(), () -> {
            hits[0] = hits[0] + 1;
            return "rhs-undefined";
        });
        final Object nullishKept = TsjRuntime.nullishCoalesce("kept", () -> {
            hits[0] = hits[0] + 1;
            return "rhs-kept";
        });

        assertEquals(false, andResult);
        assertEquals(true, orResult);
        assertEquals("rhs-null", nullishNull);
        assertEquals("rhs-undefined", nullishUndefined);
        assertEquals("kept", nullishKept);
        assertEquals(2, hits[0]);
    }

    @Test
    void logicalAssignmentHelpersShortCircuitAndAssignWhenNeeded() {
        final int[] hits = new int[]{0};
        final TsjCell andCell = new TsjCell(false);
        final TsjCell orCell = new TsjCell(false);
        final TsjCell nullishCell = new TsjCell(null);

        assertEquals(false, TsjRuntime.assignLogicalAnd(andCell, () -> {
            hits[0] = hits[0] + 1;
            return "x";
        }));
        assertEquals("alt", TsjRuntime.assignLogicalOr(orCell, () -> {
            hits[0] = hits[0] + 1;
            return "alt";
        }));
        assertEquals("fallback", TsjRuntime.assignNullish(nullishCell, () -> {
            hits[0] = hits[0] + 1;
            return "fallback";
        }));

        final Object object = TsjRuntime.objectLiteral("value", 0);
        assertEquals(0, TsjRuntime.assignPropertyLogicalAnd(object, "value", () -> {
            hits[0] = hits[0] + 1;
            return 9;
        }));
        assertEquals(9, TsjRuntime.assignPropertyLogicalOr(object, "value", () -> {
            hits[0] = hits[0] + 1;
            return 9;
        }));
        TsjRuntime.setProperty(object, "value", null);
        assertEquals(11, TsjRuntime.assignPropertyNullish(object, "value", () -> {
            hits[0] = hits[0] + 1;
            return 11;
        }));

        assertEquals(4, hits[0]);
    }

    @Test
    void optionalAccessAndCallReturnUndefinedForNullishValues() {
        final Object object = TsjRuntime.objectLiteral(
                "value",
                9,
                "read",
                (TsjCallable) args -> "ok"
        );
        final Object maybeNull = null;
        final TsjCell hits = new TsjCell(0);

        assertEquals(9, TsjRuntime.optionalMemberAccess(object, "value"));
        assertEquals(TsjRuntime.undefined(), TsjRuntime.optionalMemberAccess(maybeNull, "value"));
        assertEquals("ok", TsjRuntime.optionalCall(TsjRuntime.getProperty(object, "read"), () -> new Object[0]));
        assertEquals(TsjRuntime.undefined(), TsjRuntime.optionalCall(TsjRuntime.undefined(), () -> {
            hits.set(TsjRuntime.add(hits.get(), 1));
            return new Object[0];
        }));
        assertEquals(0, hits.get());
    }

    @Test
    void optionalInvokeMemberShortCircuitsNullishReceiverAndPreservesMemberCall() {
        final int[] hits = new int[]{0};

        assertEquals(TsjRuntime.undefined(), TsjRuntime.optionalInvokeMember(null, "missing", () -> {
            hits[0] = hits[0] + 1;
            return new Object[]{"x"};
        }));
        assertEquals(0, hits[0]);

        assertEquals("HELLO", TsjRuntime.optionalInvokeMember("hello", "toUpperCase", () -> {
            hits[0] = hits[0] + 1;
            return new Object[0];
        }));
        assertEquals(1, hits[0]);
    }

    @Test
    void spreadHelpersFlattenSegmentsAndMergeObjects() {
        final Object spreadArray = TsjRuntime.arraySpread(
                TsjRuntime.arrayLiteral(1, 2),
                new Object[]{3},
                java.util.List.of(4, 5)
        );
        assertEquals(5, TsjRuntime.getProperty(spreadArray, "length"));
        assertEquals(1, TsjRuntime.getProperty(spreadArray, "0"));
        assertEquals(5, TsjRuntime.getProperty(spreadArray, "4"));

        final Object merged = TsjRuntime.objectSpread(
                TsjRuntime.objectLiteral("a", 1),
                TsjRuntime.objectLiteral("b", 2),
                null,
                TsjRuntime.undefined()
        );
        assertEquals(1, TsjRuntime.getProperty(merged, "a"));
        assertEquals(2, TsjRuntime.getProperty(merged, "b"));

        final TsjCallable sum = args -> TsjRuntime.add(TsjRuntime.add(args[0], args[1]), args[2]);
        assertEquals(6, TsjRuntime.callSpread(sum, TsjRuntime.arrayLiteral(1, 2), TsjRuntime.arrayLiteral(3)));
    }

    @Test
    void restArgsBuildsArrayFromTrailingCallArguments() {
        final Object rest = TsjRuntime.restArgs(new Object[]{1, 2, 3, 4}, 2);
        assertEquals(2, TsjRuntime.getProperty(rest, "length"));
        assertEquals(3, TsjRuntime.getProperty(rest, "0"));
        assertEquals(4, TsjRuntime.getProperty(rest, "1"));

        final Object empty = TsjRuntime.restArgs(new Object[]{1}, 5);
        assertEquals(0, TsjRuntime.getProperty(empty, "length"));
    }

    @Test
    void arrayRestBuildsTailForArrayLikeAndIterableValues() {
        final Object fromArrayLike = TsjRuntime.arrayRest(TsjRuntime.arrayLiteral(10, 20, 30), 1);
        assertEquals(2, TsjRuntime.getProperty(fromArrayLike, "length"));
        assertEquals(20, TsjRuntime.getProperty(fromArrayLike, "0"));
        assertEquals(30, TsjRuntime.getProperty(fromArrayLike, "1"));

        final Object fromIterable = TsjRuntime.arrayRest(java.util.List.of("a", "b", "c"), 2);
        assertEquals(1, TsjRuntime.getProperty(fromIterable, "length"));
        assertEquals("c", TsjRuntime.getProperty(fromIterable, "0"));
    }

    @Test
    void arrayRestRejectsNullishInputs() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.arrayRest(null, 0)
        );
        assertTrue(exception.getMessage().contains("nullish"));
    }

    @Test
    void forLoopHelpersCollectValuesKeysAndIndexReads() {
        final Object ofValues = TsjRuntime.forOfValues(TsjRuntime.arrayLiteral(4, 5, 6));
        assertEquals(3, TsjRuntime.getProperty(ofValues, "length"));
        assertEquals(5, TsjRuntime.getProperty(ofValues, "1"));

        final Object stringValues = TsjRuntime.forOfValues("abc");
        assertEquals(3, TsjRuntime.getProperty(stringValues, "length"));
        assertEquals("a", TsjRuntime.getProperty(stringValues, "0"));
        assertEquals("c", TsjRuntime.getProperty(stringValues, "2"));

        final Object inKeys = TsjRuntime.forInKeys(TsjRuntime.objectLiteral("a", 1, "b", 2));
        assertEquals(2, TsjRuntime.getProperty(inKeys, "length"));
        assertEquals("a", TsjRuntime.getProperty(inKeys, "0"));
        assertEquals("b", TsjRuntime.getProperty(inKeys, "1"));

        assertEquals(6, TsjRuntime.indexRead(ofValues, 2));
    }

    @Test
    void generatorRuntimeSupportsNextArgumentsAndIterationProtocol() {
        final Object iterator = TsjRuntime.createGenerator(
                (thisValue, args) -> {
                    final Object firstResume = TsjRuntime.generatorYield(1);
                    TsjRuntime.generatorYield(firstResume);
                    return 42;
                },
                TsjRuntime.undefined()
        );

        final Object step1 = TsjRuntime.invokeMember(iterator, "next");
        assertEquals(1, TsjRuntime.getProperty(step1, "value"));
        assertEquals(false, TsjRuntime.getProperty(step1, "done"));

        final Object step2 = TsjRuntime.invokeMember(iterator, "next", 7);
        assertEquals(7, TsjRuntime.getProperty(step2, "value"));
        assertEquals(false, TsjRuntime.getProperty(step2, "done"));

        final Object step3 = TsjRuntime.invokeMember(iterator, "next");
        assertEquals(42, TsjRuntime.getProperty(step3, "value"));
        assertEquals(true, TsjRuntime.getProperty(step3, "done"));
    }

    @Test
    void forOfValuesConsumesGeneratorIteratorProtocol() {
        final Object iterator = TsjRuntime.createGenerator(
                (thisValue, args) -> {
                    TsjRuntime.generatorYield("a");
                    TsjRuntime.generatorYield("b");
                    return TsjRuntime.undefined();
                },
                TsjRuntime.undefined()
        );

        final Object values = TsjRuntime.forOfValues(iterator);
        assertEquals(2, TsjRuntime.getProperty(values, "length"));
        assertEquals("a", TsjRuntime.getProperty(values, "0"));
        assertEquals("b", TsjRuntime.getProperty(values, "1"));
    }

    @Test
    void forOfValuesSupportsSymbolIteratorOnPrototype() {
        final TsjClass range = new TsjClass("Range", null);
        range.setConstructor((thisObject, args) -> {
            TsjRuntime.setProperty(thisObject, "start", args.length > 0 ? args[0] : 1);
            TsjRuntime.setProperty(thisObject, "end", args.length > 1 ? args[1] : 1);
            return null;
        });

        final Object symbolIterator = TsjRuntime.getProperty(TsjRuntime.symbolBuiltin(), "iterator");
        assertTrue(symbolIterator instanceof TsjSymbol);
        assertTrue(TsjRuntime.strictEquals(
                symbolIterator,
                TsjRuntime.getProperty(TsjRuntime.symbolBuiltin(), "iterator")
        ));
        TsjRuntime.setPropertyDynamic(
                range.prototype(),
                symbolIterator,
                (TsjCallableWithThis) (thisValue, args) -> {
                    final int[] current = new int[]{
                            ((Number) TsjRuntime.getProperty(thisValue, "start")).intValue()
                    };
                    final int end = ((Number) TsjRuntime.getProperty(thisValue, "end")).intValue();
                    return TsjRuntime.objectLiteral(
                            "next",
                            (TsjCallableWithThis) (iteratorThis, iteratorArgs) -> {
                                if (current[0] <= end) {
                                    return TsjRuntime.objectLiteral("value", current[0]++, "done", false);
                                }
                                return TsjRuntime.objectLiteral("value", TsjRuntime.undefined(), "done", true);
                            }
                    );
                }
        );

        final Object instance = TsjRuntime.construct(range, 1, 3);
        assertFalse(TsjRuntime.strictEquals(TsjRuntime.indexRead(instance, symbolIterator), TsjRuntime.undefined()));

        final Object values = TsjRuntime.forOfValues(instance);
        assertEquals(3, TsjRuntime.getProperty(values, "length"));
        assertEquals(1, TsjRuntime.getProperty(values, "0"));
        assertEquals(2, TsjRuntime.getProperty(values, "1"));
        assertEquals(3, TsjRuntime.getProperty(values, "2"));
    }

    @Test
    void propertyAccessCacheDoesNotReuseValueAcrossDifferentObjectsWithSameShape() {
        final TsjPropertyAccessCache cache = new TsjPropertyAccessCache("value");
        final Object first = TsjRuntime.objectLiteral("value", 1);
        final Object second = TsjRuntime.objectLiteral("value", 2);

        assertEquals(1, TsjRuntime.getPropertyCached(cache, first, "value"));
        assertEquals(2, TsjRuntime.getPropertyCached(cache, second, "value"));
    }

    @Test
    void classObjectsSupportStaticPropertyReadsWritesAndMethodInvocation() {
        final TsjClass klass = new TsjClass("Counter", null);
        TsjRuntime.setProperty(klass, "count", 1);
        assertEquals(1, TsjRuntime.getProperty(klass, "count"));

        TsjRuntime.setProperty(
                klass,
                "inc",
                (TsjCallableWithThis) (thisValue, args) -> {
                    final Object current = TsjRuntime.getProperty(thisValue, "count");
                    final Object delta = args.length > 0 ? args[0] : 1;
                    final Object next = TsjRuntime.add(current, delta);
                    TsjRuntime.setProperty(thisValue, "count", next);
                    return next;
                }
        );

        assertEquals(2, TsjRuntime.invokeMember(klass, "inc"));
        assertEquals(7, TsjRuntime.invokeMember(klass, "inc", 5));
        assertEquals(7, TsjRuntime.getProperty(klass, "count"));
    }

    @Test
    void errorBuiltinSupportsConstructionAndThrowableInstanceofChecks() {
        final Object errorCtor = TsjRuntime.errorBuiltin();
        final Object errorInstance = TsjRuntime.construct(errorCtor, "boom");

        assertEquals("Error", TsjRuntime.getProperty(errorInstance, "name"));
        assertEquals("boom", TsjRuntime.getProperty(errorInstance, "message"));
        assertTrue(TsjRuntime.instanceOf(errorInstance, errorCtor));
        assertTrue(TsjRuntime.instanceOf(new IllegalArgumentException("bad"), errorCtor));
    }

    @Test
    void stringBuiltinCoercesToDisplayStringsLikeGlobalStringFunction() {
        final Object stringFn = TsjRuntime.stringBuiltin();
        assertEquals("42", TsjRuntime.call(stringFn, 42));
        assertEquals("undefined", TsjRuntime.call(stringFn));
        assertEquals("null", TsjRuntime.call(stringFn, (Object) null));
    }

    @Test
    void mathAndNumberBuiltinsExposeNumericHelpersAndGlobals() {
        final Object math = TsjRuntime.mathBuiltin();
        assertEquals(3, TsjRuntime.invokeMember(math, "floor", 3.7));
        assertEquals(4, TsjRuntime.invokeMember(math, "ceil", 3.2));
        assertTrue(TsjRuntime.greaterThan(TsjRuntime.getProperty(math, "PI"), 3.14));
        assertEquals(3, TsjRuntime.invokeMember(math, "log10", 1000));

        final Object numberFn = TsjRuntime.numberBuiltin();
        assertEquals(42, TsjRuntime.call(numberFn, "42"));
        assertEquals(true, TsjRuntime.invokeMember(numberFn, "isInteger", 5));
        assertEquals(false, TsjRuntime.invokeMember(numberFn, "isFinite", TsjRuntime.infinity()));
        assertEquals(true, TsjRuntime.invokeMember(numberFn, "isNaN", TsjRuntime.nanValue()));

        assertEquals(42, TsjRuntime.call(TsjRuntime.parseIntBuiltin(), "42"));
        assertEquals(3.14d, TsjRuntime.call(TsjRuntime.parseFloatBuiltin(), "3.14"));
    }

    @Test
    void getPropertySupportsStringLengthAndIndexAccess() {
        assertEquals(5, TsjRuntime.getProperty("hello", "length"));
        assertEquals("e", TsjRuntime.getProperty("hello", "1"));
        assertEquals(TsjRuntime.undefined(), TsjRuntime.getProperty("hello", "99"));
    }

    @Test
    void objectDateAndErrorSubtypeBuiltinsSupportCoreBehaviors() {
        final Object objectBuiltin = TsjRuntime.objectBuiltin();
        final Object keys = TsjRuntime.invokeMember(objectBuiltin, "keys", TsjRuntime.objectLiteral("a", 1, "b", 2));
        assertEquals(2, TsjRuntime.getProperty(keys, "length"));
        assertEquals("a", TsjRuntime.getProperty(keys, "0"));

        final Object dateCtor = TsjRuntime.dateBuiltin();
        final Object now = TsjRuntime.invokeMember(dateCtor, "now");
        assertTrue(TsjRuntime.greaterThan(now, 0));
        final Object epoch = TsjRuntime.construct(dateCtor, 0);
        assertEquals(0L, TsjRuntime.invokeMember(epoch, "getTime"));

        final Object typeErrorCtor = TsjRuntime.typeErrorBuiltin();
        final Object typeError = TsjRuntime.construct(typeErrorCtor, "bad");
        assertEquals(true, TsjRuntime.instanceOf(typeError, typeErrorCtor));
        assertEquals(true, TsjRuntime.instanceOf(typeError, TsjRuntime.errorBuiltin()));
        assertEquals("TypeError", TsjRuntime.getProperty(typeError, "name"));
    }

    @Test
    void accessorPropertiesInvokeGetterAndSetterWithReceiver() {
        final TsjObject object = new TsjObject(null);
        object.setOwn("_value", 1);

        final Object getter = (TsjMethod) (thisObject, args) -> TsjRuntime.getProperty(thisObject, "_value");
        final Object setter = (TsjMethod) (thisObject, args) -> {
            TsjRuntime.setProperty(thisObject, "_value", args.length > 0 ? args[0] : TsjRuntime.undefined());
            return TsjRuntime.undefined();
        };

        TsjRuntime.defineAccessorProperty(object, "value", getter, TsjRuntime.undefined());
        TsjRuntime.defineAccessorProperty(object, "value", TsjRuntime.undefined(), setter);

        assertEquals(1, TsjRuntime.getProperty(object, "value"));
        assertEquals(9, TsjRuntime.setProperty(object, "value", 9));
        assertEquals(9, TsjRuntime.getProperty(object, "_value"));
        assertEquals(9, TsjRuntime.getProperty(object, "value"));
    }

    @Test
    void arrayMapSetAndSetBuiltinsSupportCoreCollectionsBehavior() {
        final Object arrayCtor = TsjRuntime.arrayBuiltin();
        final Object array = TsjRuntime.construct(arrayCtor, 1, 2, 3);
        final Object mapped = TsjRuntime.invokeMember(array, "map", (TsjCallable) args -> TsjRuntime.multiply(args[0], 2));
        assertEquals(3, TsjRuntime.getProperty(mapped, "length"));
        assertEquals(6, TsjRuntime.getProperty(mapped, "2"));

        final Object from = TsjRuntime.invokeMember(arrayCtor, "from", "ab");
        assertEquals("a", TsjRuntime.getProperty(from, "0"));
        assertEquals(true, TsjRuntime.invokeMember(arrayCtor, "isArray", mapped));

        final Object mapCtor = TsjRuntime.mapBuiltin();
        final Object map = TsjRuntime.construct(mapCtor);
        TsjRuntime.invokeMember(map, "set", "a", 1);
        TsjRuntime.invokeMember(map, "set", "b", 2);
        assertEquals(2, TsjRuntime.getProperty(map, "size"));
        assertEquals(1, TsjRuntime.invokeMember(map, "get", "a"));

        final Object setCtor = TsjRuntime.setBuiltin();
        final Object set = TsjRuntime.construct(setCtor);
        TsjRuntime.invokeMember(set, "add", 1);
        TsjRuntime.invokeMember(set, "add", 1);
        TsjRuntime.invokeMember(set, "add", 2);
        assertEquals(2, TsjRuntime.getProperty(set, "size"));
        assertEquals(true, TsjRuntime.invokeMember(set, "has", 2));
    }

    @Test
    void regexpBuiltinSupportsLiteralAndConstructorStyleCalls() {
        final Object regexCtor = TsjRuntime.regexpBuiltin();
        final Object testRegex = TsjRuntime.construct(regexCtor, "\\d+", "g");
        assertEquals(true, TsjRuntime.invokeMember(testRegex, "test", "a1b22"));

        final Object regex = TsjRuntime.construct(regexCtor, "\\d+", "g");
        final Object first = TsjRuntime.invokeMember(regex, "exec", "a1b22");
        final Object second = TsjRuntime.invokeMember(regex, "exec", "a1b22");
        assertEquals("1", TsjRuntime.getProperty(first, "0"));
        assertEquals("22", TsjRuntime.getProperty(second, "0"));

        assertEquals(true, TsjRuntime.invokeMember("/hello/i", "test", "HELLO"));
        assertEquals(2, TsjRuntime.invokeMember("ab3cd", "search", "/\\d/"));
        assertEquals("xxbxx", TsjRuntime.invokeMember("aabaa", "replace", "/a/g", "x"));
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
    void callDefaultsThisToUndefinedForReceiverAwareCallable() {
        final TsjCallableWithThis callable = (thisValue, args) -> TsjRuntime.strictEquals(thisValue, TsjRuntime.undefined());
        assertEquals(true, TsjRuntime.call(callable));
    }

    @Test
    void javaStaticMethodCreatesCallableInteropHandle() {
        final Object max = TsjRuntime.javaStaticMethod("java.lang.Math", "max");
        assertEquals(9, TsjRuntime.call(max, 4, 9));
    }

    @Test
    void javaBindingCreatesCallableForConstructorAndInstanceMember() {
        final String className = "java.lang.StringBuilder";
        final Object constructor = TsjRuntime.javaBinding(className, "$new");
        final Object append = TsjRuntime.javaBinding(className, "$instance$append");
        final Object asString = TsjRuntime.javaBinding(className, "$instance$toString");

        final Object builder = TsjRuntime.call(constructor, "a");
        TsjRuntime.call(append, builder, "b");

        assertEquals("ab", TsjRuntime.call(asString, builder));
    }

    @Test
    void invokeMemberBindsThisForReceiverAwareCallable() {
        final Object object = TsjRuntime.objectLiteral(
                "value",
                4,
                "read",
                (TsjCallableWithThis) (thisValue, args) -> TsjRuntime.getProperty(thisValue, "value")
        );
        assertEquals(4, TsjRuntime.invokeMember(object, "read"));
    }

    @Test
    void invokeMemberFallsBackToJavaReceiverDispatchWhenTargetIsNotTsjObject() {
        final StringBuilder builder = new StringBuilder("a");
        TsjRuntime.invokeMember(builder, "append", "b");
        assertEquals("ab", TsjRuntime.invokeMember(builder, "toString"));
    }

    @Test
    void invokeMemberSupportsStringIncludesAndTrimAliases() {
        assertEquals(true, TsjRuntime.invokeMember("hello", "includes", "ell"));
        assertEquals(false, TsjRuntime.invokeMember("hello", "includes", "ell", 2));
        assertEquals("hi", TsjRuntime.invokeMember("  hi", "trimStart"));
        assertEquals("hi", TsjRuntime.invokeMember("hi  ", "trimEnd"));
    }

    @Test
    void invokeMemberSupportsArrayLikePushOnTsjArrayObjects() {
        final Object array = TsjRuntime.arrayLiteral("a");
        assertEquals(2, TsjRuntime.invokeMember(array, "push", "b"));
        assertEquals(3, TsjRuntime.invokeMember(array, "push", "c"));
        assertEquals(3, TsjRuntime.getProperty(array, "length"));
        assertEquals("c", TsjRuntime.getProperty(array, "2"));
    }

    @Test
    void getPropertySupportsJavaThrowableMessageAndName() {
        final IllegalArgumentException exception = new IllegalArgumentException("boom");
        assertEquals("boom", TsjRuntime.getProperty(exception, "message"));
        assertEquals("IllegalArgumentException", TsjRuntime.getProperty(exception, "name"));
    }

    @Test
    void getPropertyCachedSupportsJavaThrowableMessage() {
        final TsjPropertyAccessCache cache = new TsjPropertyAccessCache("message");
        final IllegalArgumentException exception = new IllegalArgumentException("boom");
        assertEquals("boom", TsjRuntime.getPropertyCached(cache, exception, "message"));
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
    void deletePropertyReturnsTrueAndFallsBackToPrototypeValue() {
        final Object prototype = TsjRuntime.objectLiteral("name", "base");
        final Object object = TsjRuntime.objectLiteral("name", "local");
        TsjRuntime.setPrototype(object, prototype);

        assertEquals("local", TsjRuntime.getProperty(object, "name"));
        assertTrue(TsjRuntime.deleteProperty(object, "name"));
        assertEquals("base", TsjRuntime.getProperty(object, "name"));
        assertTrue(TsjRuntime.deleteProperty(object, "missing"));
    }

    @Test
    void setPrototypeSupportsObjectAndNullLikeValues() {
        final Object prototype = TsjRuntime.objectLiteral("name", "base");
        final Object object = TsjRuntime.objectLiteral();

        assertTrue(TsjRuntime.setPrototype(object, prototype) == object);
        assertEquals("base", TsjRuntime.getProperty(object, "name"));

        assertTrue(TsjRuntime.setPrototype(object, null) == object);
        assertEquals(TsjRuntime.undefined(), TsjRuntime.getProperty(object, "name"));
    }

    @Test
    void assignmentHelpersReturnAssignedValues() {
        final TsjCell cell = new TsjCell(1);
        assertEquals(7, TsjRuntime.assignCell(cell, 7));
        assertEquals(7, cell.get());

        final Object prototype = TsjRuntime.objectLiteral("ready", true);
        final Object object = TsjRuntime.objectLiteral();
        assertTrue(TsjRuntime.setPrototypeValue(object, prototype) == prototype);
        assertTrue(TsjRuntime.isNullishValue(null));
        assertTrue(TsjRuntime.isNullishValue(TsjRuntime.undefined()));
        assertFalse(TsjRuntime.isNullishValue(0));
    }

    @Test
    void setPrototypeRejectsPrimitivePrototypeValues() {
        final Object object = TsjRuntime.objectLiteral();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.setPrototype(object, 1)
        );
        assertTrue(exception.getMessage().contains("Prototype must be object|null|undefined"));
    }

    @Test
    void objectBuiltinSupportsDefinePropertyAndDescriptorLookup() {
        final Object objectBuiltin = TsjRuntime.objectBuiltin();
        final Object target = TsjRuntime.objectLiteral();

        TsjRuntime.invokeMember(
                objectBuiltin,
                "defineProperty",
                target,
                "answer",
                TsjRuntime.objectLiteral("value", 42)
        );

        assertEquals(42, TsjRuntime.getProperty(target, "answer"));

        final Object descriptor = TsjRuntime.invokeMember(objectBuiltin, "getOwnPropertyDescriptor", target, "answer");
        assertEquals(42, TsjRuntime.getProperty(descriptor, "value"));
        assertEquals(target, TsjRuntime.invokeMember(objectBuiltin, "seal", target));
    }

    @Test
    void invokeMemberApplySupportsTsjMethodTargets() {
        final TsjClass box = new TsjClass("Box", null);
        box.setConstructor((thisObject, args) -> {
            TsjRuntime.setProperty(thisObject, "value", args.length > 0 ? args[0] : 0);
            return null;
        });
        box.defineMethod(
                "sum",
                (thisObject, args) -> TsjRuntime.add(
                        TsjRuntime.getProperty(thisObject, "value"),
                        args.length > 0 ? args[0] : 0
                )
        );

        final Object instance = TsjRuntime.construct(box, 5);
        final Object method = TsjRuntime.getProperty(TsjRuntime.getProperty(box, "prototype"), "sum");
        final Object result = TsjRuntime.invokeMember(
                method,
                "apply",
                instance,
                TsjRuntime.arrayLiteral(7)
        );

        assertEquals(12, result);
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
    void weakCollectionsAndWeakRefBuiltinsSupportBasicOperations() {
        final Object weakMap = TsjRuntime.construct(TsjRuntime.weakMapBuiltin());
        final Object weakSet = TsjRuntime.construct(TsjRuntime.weakSetBuiltin());
        final Object key = TsjRuntime.objectLiteral("id", 1);

        TsjRuntime.invokeMember(weakMap, "set", key, "one");
        assertEquals("one", TsjRuntime.invokeMember(weakMap, "get", key));
        assertEquals(true, TsjRuntime.invokeMember(weakMap, "has", key));
        TsjRuntime.invokeMember(weakMap, "delete", key);
        assertEquals(false, TsjRuntime.invokeMember(weakMap, "has", key));

        TsjRuntime.invokeMember(weakSet, "add", key);
        assertEquals(true, TsjRuntime.invokeMember(weakSet, "has", key));
        TsjRuntime.invokeMember(weakSet, "delete", key);
        assertEquals(false, TsjRuntime.invokeMember(weakSet, "has", key));

        final Object target = TsjRuntime.objectLiteral("value", 42);
        final Object weakRef = TsjRuntime.construct(TsjRuntime.weakRefBuiltin(), target);
        final Object deref = TsjRuntime.invokeMember(weakRef, "deref");
        assertEquals(42, TsjRuntime.getProperty(deref, "value"));
    }

    @Test
    void proxyAndReflectBuiltinsSupportBasicTrapAndRevocableFlow() {
        final Object target = TsjRuntime.objectLiteral("x", 1, "secret", true);
        final Object handler = TsjRuntime.objectLiteral(
                "get",
                (TsjCallable) args -> TsjRuntime.inOperator(args[1], args[0])
                        ? TsjRuntime.indexRead(args[0], args[1])
                        : -1,
                "set",
                (TsjCallable) args -> {
                    TsjRuntime.setPropertyDynamic(args[0], args[1], args[2]);
                    return Boolean.TRUE;
                },
                "has",
                (TsjCallable) args -> "secret".equals(TsjRuntime.toDisplayString(args[1]))
                        ? Boolean.FALSE
                        : Boolean.valueOf(TsjRuntime.inOperator(args[1], args[0]))
        );

        final Object proxy = TsjRuntime.construct(TsjRuntime.proxyBuiltin(), target, handler);
        assertEquals(1, TsjRuntime.getProperty(proxy, "x"));
        assertEquals(-1, TsjRuntime.getProperty(proxy, "missing"));

        TsjRuntime.setProperty(proxy, "y", 7);
        assertEquals(7, TsjRuntime.getProperty(target, "y"));

        assertTrue(TsjRuntime.inOperator("x", proxy));
        assertFalse(TsjRuntime.inOperator("secret", proxy));

        final Object reflect = TsjRuntime.reflectBuiltin();
        assertEquals(true, TsjRuntime.invokeMember(reflect, "has", proxy, "x"));
        assertEquals(1, TsjRuntime.invokeMember(reflect, "get", proxy, "x"));
        assertEquals(true, TsjRuntime.invokeMember(reflect, "set", proxy, "x", 2));
        assertEquals(2, TsjRuntime.getProperty(target, "x"));

        final Object keys = TsjRuntime.invokeMember(reflect, "ownKeys", TsjRuntime.objectLiteral("a", 1, "b", 2));
        assertEquals(2, TsjRuntime.getProperty(keys, "length"));

        final Object revocable = TsjRuntime.invokeMember(
                TsjRuntime.proxyBuiltin(),
                "revocable",
                TsjRuntime.objectLiteral("data", 42),
                TsjRuntime.objectLiteral()
        );
        final Object revocableProxy = TsjRuntime.getProperty(revocable, "proxy");
        assertEquals(42, TsjRuntime.getProperty(revocableProxy, "data"));
        TsjRuntime.invokeMember(revocable, "revoke");
        final IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> TsjRuntime.getProperty(revocableProxy, "data")
        );
        assertTrue(exception.getMessage().contains("revoked"));
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
                () -> TsjRuntime.getProperty(TsjRuntime.undefined(), "x")
        );
        assertTrue(getException.getMessage().contains("Cannot get property"));

        final IllegalArgumentException setException = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.setProperty(TsjRuntime.undefined(), "x", 1)
        );
        assertTrue(setException.getMessage().contains("Cannot set property"));
    }

    @Test
    void invokeMemberRejectsMissingReceiverType() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjRuntime.invokeMember(TsjRuntime.undefined(), "x")
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
    void errorBuiltinSupportsCauseOptionObject() {
        final Object root = TsjRuntime.construct(TsjRuntime.errorBuiltin(), "root");
        final Object wrapped = TsjRuntime.construct(
                TsjRuntime.errorBuiltin(),
                "wrapped",
                TsjRuntime.objectLiteral("cause", root)
        );

        assertEquals("wrapped", TsjRuntime.getProperty(wrapped, "message"));
        assertEquals(root, TsjRuntime.getProperty(wrapped, "cause"));
    }

    @Test
    void aggregateErrorBuiltinConstructsErrorsListAndMessage() {
        final Object e1 = TsjRuntime.construct(TsjRuntime.errorBuiltin(), "e1");
        final Object e2 = TsjRuntime.construct(TsjRuntime.errorBuiltin(), "e2");
        final Object aggregate = TsjRuntime.construct(
                TsjRuntime.aggregateErrorBuiltin(),
                TsjRuntime.arrayLiteral(e1, e2),
                "many",
                TsjRuntime.objectLiteral("cause", TsjRuntime.construct(TsjRuntime.errorBuiltin(), "root"))
        );

        assertEquals("AggregateError", TsjRuntime.getProperty(aggregate, "name"));
        assertEquals("many", TsjRuntime.getProperty(aggregate, "message"));
        final Object errors = TsjRuntime.getProperty(aggregate, "errors");
        assertEquals(2, TsjRuntime.getProperty(errors, "length"));
        final Object first = TsjRuntime.getProperty(errors, "0");
        assertEquals("e1", TsjRuntime.getProperty(first, "message"));
        assertEquals("root", TsjRuntime.getProperty(TsjRuntime.getProperty(aggregate, "cause"), "message"));
    }

    @Test
    void raiseWrapsNonExceptionValuesAndNormalizeThrownUnwrapsThem() {
        final RuntimeException wrapped = TsjRuntime.raise("boom");
        assertEquals("boom", TsjRuntime.normalizeThrown(wrapped));

        final IllegalStateException direct = new IllegalStateException("x");
        assertEquals(direct, TsjRuntime.normalizeThrown(TsjRuntime.raise(direct)));
    }
}
