package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjInteropCodecTest {
    @Test
    void fromJavaConvertsPrimitiveFriendlyValues() {
        assertEquals(7, TsjInteropCodec.fromJava(Integer.valueOf(7)));
        assertEquals(3.5d, TsjInteropCodec.fromJava(Float.valueOf(3.5f)));
        assertEquals("x", TsjInteropCodec.fromJava(Character.valueOf('x')));
        assertEquals(9, TsjInteropCodec.fromJava(Long.valueOf(9L)));
    }

    @Test
    void fromJavaPreservesObjectReferences() {
        final Object marker = new Object();
        assertSame(marker, TsjInteropCodec.fromJava(marker));
    }

    @Test
    void toJavaConvertsToPrimitiveTargets() {
        assertEquals(12, TsjInteropCodec.toJava("12", int.class));
        assertEquals(2.25d, TsjInteropCodec.toJava("2.25", double.class));
        assertEquals(true, TsjInteropCodec.toJava(1, boolean.class));
    }

    @Test
    void toJavaReturnsNullForUndefinedWhenTargetIsReferenceType() {
        assertNull(TsjInteropCodec.toJava(TsjRuntime.undefined(), String.class));
    }

    @Test
    void toJavaReturnsNullForNullWhenTargetIsStringReferenceType() {
        assertNull(TsjInteropCodec.toJava(null, String.class));
    }

    @Test
    void toJavaKeepsAssignableObjectReference() {
        final TsjObject object = new TsjObject(null);
        assertSame(object, TsjInteropCodec.toJava(object, TsjObject.class));
    }

    @Test
    void toJavaArgumentsConvertsBySignatureAndChecksArity() {
        final Object[] converted = TsjInteropCodec.toJavaArguments(
                new Object[]{"3", 4},
                new Class<?>[]{int.class, double.class}
        );

        assertEquals(3, converted[0]);
        assertEquals(4.0d, converted[1]);

        final IllegalArgumentException arityException = assertThrows(
                IllegalArgumentException.class,
                () -> TsjInteropCodec.toJavaArguments(
                        new Object[]{1},
                        new Class<?>[]{int.class, int.class}
                )
        );
        assertTrue(arityException.getMessage().contains("arity"));
    }

    @Test
    void invokeStaticUsesCodecForArgumentsAndReturnValues() {
        assertEquals(5, TsjJavaInterop.invokeStatic("java.lang.Math", "max", "3", 5));

        final Object singleton = TsjJavaInterop.invokeStatic("java.util.Collections", "singletonList", "tsj");
        assertNotNull(singleton);
        assertTrue(singleton instanceof TsjObject);
        assertEquals(1, TsjRuntime.getProperty(singleton, "length"));
        assertEquals("tsj", TsjRuntime.getProperty(singleton, "0"));
    }

    @Test
    void invokeStaticRejectsMissingMethod() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeStatic("java.lang.Math", "definitelyMissing", 1)
        );
        assertTrue(exception.getMessage().contains("No compatible static method"));
    }

    @Test
    void invokeBindingSupportsConstructorAndInstanceMethodDispatch() {
        final String className = InteropSample.class.getName();
        final Object instance = TsjJavaInterop.invokeBinding(className, "$new", 4);

        assertEquals(6, TsjJavaInterop.invokeBinding(className, "$instance$add", instance, 2));
        assertEquals("seed=6", TsjJavaInterop.invokeBinding(className, "$instance$describe", instance));
    }

    @Test
    void invokeInstanceMemberSupportsDirectJavaReceiverDispatch() {
        final InteropSample sample = new InteropSample(3);

        assertEquals(5, TsjJavaInterop.invokeInstanceMember(sample, "add", 2));
        assertEquals("seed=5", TsjJavaInterop.invokeInstanceMember(sample, "describe"));
    }

    @Test
    void invokeInstanceMemberRejectsMissingMethodWithInstanceDiagnostic() {
        final InteropSample sample = new InteropSample(1);
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeInstanceMember(sample, "missing", 1)
        );
        assertTrue(exception.getMessage().contains("No compatible instance method"));
        assertTrue(exception.getMessage().contains("InteropSample"));
    }

    @Test
    void invokeBindingSupportsStaticAndInstanceFieldAccess() {
        final String className = InteropSample.class.getName();
        InteropSample.GLOBAL = 7;
        final Object instance = TsjJavaInterop.invokeBinding(className, "$new", 3);

        assertEquals(7, TsjJavaInterop.invokeBinding(className, "$static$get$GLOBAL"));
        assertEquals(11, TsjJavaInterop.invokeBinding(className, "$static$set$GLOBAL", 11));
        assertEquals(11, TsjJavaInterop.invokeBinding(className, "$static$get$GLOBAL"));

        assertEquals(3, TsjJavaInterop.invokeBinding(className, "$instance$get$value", instance));
        assertEquals(9, TsjJavaInterop.invokeBinding(className, "$instance$set$value", instance, 9));
        assertEquals(9, TsjJavaInterop.invokeBinding(className, "$instance$get$value", instance));
    }

    @Test
    void invokeBindingSupportsDeterministicOverloadAndVarArgsResolution() {
        final String className = InteropSample.class.getName();

        assertEquals("int", TsjJavaInterop.invokeBinding(className, "pick", 3));
        assertEquals("double", TsjJavaInterop.invokeBinding(className, "pick", 3.25d));
        assertEquals("prefix:a:b:c", TsjJavaInterop.invokeBinding(className, "join", "prefix", "a", "b", "c"));
    }

    @Test
    void invokeBindingPreselectedUsesDescriptorIdentityWithoutReresolution() {
        final String className = InteropSample.class.getName();
        final Object selected = TsjJavaInterop.invokeBindingPreselected(
                className,
                "pick",
                className,
                "pick",
                "(D)Ljava/lang/String;",
                "STATIC_METHOD",
                3
        );

        assertEquals("double", selected);
    }

    @Test
    void invokeBindingConvertsNullishArgumentsAcrossStaticInstanceAndPreselectedPaths() {
        final String className = InteropSample.class.getName();
        final Object instance = TsjJavaInterop.invokeBinding(className, "$new", 2);

        assertEquals("null", TsjJavaInterop.invokeBinding(className, "nullKind", (Object) null));
        assertEquals("null", TsjJavaInterop.invokeBinding(className, "nullKind", TsjRuntime.undefined()));
        assertEquals(
                "null",
                TsjJavaInterop.invokeBinding(className, "$instance$nullKindInstance", instance, TsjRuntime.undefined())
        );

        assertEquals(
                "null",
                TsjJavaInterop.invokeBindingPreselected(
                        className,
                        "nullKind",
                        className,
                        "nullKind",
                        "(Ljava/lang/Object;)Ljava/lang/String;",
                        "STATIC_METHOD",
                        TsjRuntime.undefined()
                )
        );
        assertEquals(
                "null",
                TsjJavaInterop.invokeBindingPreselected(
                        className,
                        "$instance$nullKindInstance",
                        className,
                        "nullKindInstance",
                        "(Ljava/lang/Object;)Ljava/lang/String;",
                        "INSTANCE_METHOD",
                        instance,
                        TsjRuntime.undefined()
                )
        );
    }

    @Test
    void invokeBindingPreservesNullLiteralBehaviorForVarargsWithAndWithoutPreselectedTarget() {
        final String className = InteropSample.class.getName();
        assertEquals(
                "prefix:null:<null>:x",
                TsjJavaInterop.invokeBinding(
                        className,
                        "joinNullAware",
                        "prefix",
                        null,
                        TsjRuntime.undefined(),
                        "x"
                )
        );
        assertEquals(
                "prefix:null:<null>:x",
                TsjJavaInterop.invokeBindingPreselected(
                        className,
                        "joinNullAware",
                        className,
                        "joinNullAware",
                        "(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;",
                        "STATIC_METHOD",
                        "prefix",
                        null,
                        TsjRuntime.undefined(),
                        "x"
                )
        );
    }

    @Test
    void invokeBindingPreselectedRejectsUnknownDescriptorWithDiagnostic() {
        final String className = InteropSample.class.getName();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBindingPreselected(
                        className,
                        "pick",
                        className,
                        "pick",
                        "(Ljava/lang/String;)Ljava/lang/String;",
                        "STATIC_METHOD",
                        "x"
                )
        );

        assertTrue(exception.getMessage().contains("preselected target"));
        assertTrue(exception.getMessage().contains("(Ljava/lang/String;)Ljava/lang/String;"));
    }

    @Test
    void invokeBindingMismatchDiagnosticsIncludeCandidates() {
        final String className = InteropSample.class.getName();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(className, "needsSample", 1)
        );
        assertTrue(exception.getMessage().contains("No compatible static method"));
        assertTrue(exception.getMessage().contains("Candidates:"));
        assertTrue(exception.getMessage().contains("needsSample("));
    }

    @Test
    void fromJavaConvertsArraysListsMapsEnumsAndFutures() {
        final Object arrayValue = TsjInteropCodec.fromJava(new int[]{2, 4, 6});
        assertTrue(arrayValue instanceof TsjObject);
        assertEquals(3, TsjRuntime.getProperty(arrayValue, "length"));
        assertEquals(4, TsjRuntime.getProperty(arrayValue, "1"));

        final Object listValue = TsjInteropCodec.fromJava(List.of("a", "b"));
        assertEquals(2, TsjRuntime.getProperty(listValue, "length"));
        assertEquals("b", TsjRuntime.getProperty(listValue, "1"));

        final Object setValue = TsjInteropCodec.fromJava(Set.of("x", "y"));
        assertTrue(setValue instanceof TsjObject);
        assertEquals(2, TsjRuntime.getProperty(setValue, "length"));

        final Object mapValue = TsjInteropCodec.fromJava(Map.of("x", 1, "y", 2));
        assertTrue(mapValue instanceof TsjObject);
        assertEquals(1, TsjRuntime.getProperty(mapValue, "x"));
        assertEquals(2, TsjRuntime.getProperty(mapValue, "y"));

        assertSame(InteropMode.BETA, TsjInteropCodec.fromJava(InteropMode.BETA));

        final CompletableFuture<String> completed = CompletableFuture.completedFuture("ok");
        final Object promiseValue = TsjInteropCodec.fromJava(completed);
        assertInstanceOf(TsjPromise.class, promiseValue);
        TsjRuntime.flushMicrotasks();
        assertEquals("ok", capturePromiseValue((TsjPromise) promiseValue));
    }

    @Test
    void invokeBindingPreservesEnumInstanceIdentityAcrossInteropBoundaries() {
        final Object monday = TsjJavaInterop.invokeBinding("java.time.DayOfWeek", "valueOf", "MONDAY");
        assertEquals("MONDAY", TsjJavaInterop.invokeBinding("java.time.DayOfWeek", "$instance$name", monday));
        assertEquals(1, TsjJavaInterop.invokeBinding("java.time.DayOfWeek", "$instance$getValue", monday));
    }

    @Test
    void invokeBindingPreservesOptionalWrapperAcrossInteropBoundaries() {
        final Object present = TsjJavaInterop.invokeBinding("java.util.Optional", "of", 41);
        assertTrue((Boolean) TsjJavaInterop.invokeBinding("java.util.Optional", "$instance$isPresent", present));
        assertEquals(41, TsjJavaInterop.invokeBinding("java.util.Optional", "$instance$get", present));

        final Object empty = TsjJavaInterop.invokeBinding("java.util.Optional", "empty");
        assertFalse((Boolean) TsjJavaInterop.invokeBinding("java.util.Optional", "$instance$isPresent", empty));
    }

    @Test
    void toJavaConvertsArrayLikeListMapEnumAndFutureTargets() {
        final Object tsArray = TsjRuntime.arrayLiteral("1", "2", "3");
        final int[] intArray = (int[]) TsjInteropCodec.toJava(tsArray, int[].class);
        assertEquals(3, intArray.length);
        assertEquals(1, intArray[0]);
        assertEquals(3, intArray[2]);

        @SuppressWarnings("unchecked")
        final List<Object> listValue = (List<Object>) TsjInteropCodec.toJava(tsArray, List.class);
        assertEquals(List.of("1", "2", "3"), listValue);

        final Object tsMap = TsjRuntime.objectLiteral("name", "tsj", "count", 4);
        @SuppressWarnings("unchecked")
        final Map<String, Object> mapValue = (Map<String, Object>) TsjInteropCodec.toJava(tsMap, Map.class);
        assertEquals("tsj", mapValue.get("name"));
        assertEquals(4, mapValue.get("count"));

        assertEquals(InteropMode.ALPHA, TsjInteropCodec.toJava("ALPHA", InteropMode.class));

        final CompletableFuture<?> completedFuture = (CompletableFuture<?>) TsjInteropCodec.toJava(
                TsjPromise.resolved("ready"),
                CompletableFuture.class
        );
        TsjRuntime.flushMicrotasks();
        assertEquals("ready", completedFuture.join());
    }

    @Test
    void toJavaConvertsArrayLikeValuesToSetTargets() {
        final Object tsArray = TsjRuntime.arrayLiteral("new", "paid", "new");
        @SuppressWarnings("unchecked")
        final Set<Object> statuses = (Set<Object>) TsjInteropCodec.toJava(tsArray, Set.class);
        assertEquals(Set.of("new", "paid"), statuses);
    }

    @Test
    void toJavaRecursivelyConvertsNestedObjectGraphsForMapTargets() {
        final Object payload = TsjRuntime.objectLiteral(
                "kind",
                "demo",
                "nested",
                TsjRuntime.objectLiteral("level", 2),
                "items",
                TsjRuntime.arrayLiteral("a", "b")
        );

        @SuppressWarnings("unchecked")
        final Map<String, Object> javaMap = (Map<String, Object>) TsjInteropCodec.toJava(payload, Map.class);
        assertEquals("demo", javaMap.get("kind"));
        assertInstanceOf(Map.class, javaMap.get("nested"));
        assertEquals(2, ((Map<?, ?>) javaMap.get("nested")).get("level"));
        assertInstanceOf(List.class, javaMap.get("items"));
        assertEquals(List.of("a", "b"), javaMap.get("items"));
    }

    @Test
    void toJavaSupportsOptionalTargetConversions() {
        @SuppressWarnings("unchecked")
        final Optional<Object> present = (Optional<Object>) TsjInteropCodec.toJava("value", Optional.class);
        @SuppressWarnings("unchecked")
        final Optional<Object> empty = (Optional<Object>) TsjInteropCodec.toJava(
                TsjRuntime.undefined(),
                Optional.class
        );

        assertTrue(present.isPresent());
        assertEquals("value", present.get());
        assertTrue(empty.isEmpty());
    }

    @Test
    void functionalInterfaceCallbackBridgeInvokesTsCallable() {
        final IntUnaryOperator operator = (IntUnaryOperator) TsjInteropCodec.toJava(
                (TsjCallable) args -> TsjRuntime.add(args[0], 10),
                IntUnaryOperator.class
        );
        assertEquals(15, operator.applyAsInt(5));
    }

    @Test
    void invokeBindingSupportsFunctionalInterfaceAndCompletableFutureBoundary() {
        final String className = InteropSample.class.getName();
        final Object callbackResult = TsjJavaInterop.invokeBinding(
                className,
                "applyOperator",
                (TsjCallable) args -> TsjRuntime.add(args[0], 2),
                5
        );
        assertEquals(7, callbackResult);

        final Object promiseResult = TsjJavaInterop.invokeBinding(className, "futureUpper", "tsj");
        assertInstanceOf(TsjPromise.class, promiseResult);
        TsjRuntime.flushMicrotasks();
        assertEquals("TSJ", capturePromiseValue((TsjPromise) promiseResult));
    }

    @Test
    void invokeBindingPrefersCollectionOverloadsOverObjectFallback() {
        final String className = InteropSample.class.getName();
        assertEquals("list", TsjJavaInterop.invokeBinding(className, "classify", TsjRuntime.arrayLiteral(1, 2)));
        assertEquals(
                "map",
                TsjJavaInterop.invokeBinding(className, "classify", TsjRuntime.objectLiteral("k", "v"))
        );
    }

    @Test
    void invokeBindingMismatchDiagnosticsIncludeConcreteConversionDetails() {
        final String className = InteropSample.class.getName();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(className, "modeName", "GAMMA")
        );
        assertTrue(exception.getMessage().contains("No compatible static method"));
        assertTrue(exception.getMessage().contains("Candidates:"));
        assertTrue(exception.getMessage().contains("Unknown enum constant"));
    }

    @Test
    void invokeBindingSupportsTsj41aNumericWideningAndPrimitiveWrapperParity() {
        final String className = InteropSample.class.getName();
        assertEquals("long", TsjJavaInterop.invokeBinding(className, "widenPrimitive", 5));
        assertEquals("double", TsjJavaInterop.invokeBinding(className, "widenPrimitive", 5.5d));
        assertEquals("Long", TsjJavaInterop.invokeBinding(className, "widenWrapper", 5));
        assertEquals("Double", TsjJavaInterop.invokeBinding(className, "widenWrapper", 5.5d));
        assertEquals("int", TsjJavaInterop.invokeBinding(className, "primitiveVsWrapper", 5));
    }

    @Test
    void invokeBindingReportsNumericConversionReasonForRejectedNarrowing() {
        final String className = InteropSample.class.getName();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(className, "acceptByte", 130)
        );
        assertTrue(exception.getMessage().contains("No compatible static method"));
        assertTrue(exception.getMessage().contains("numeric conversion"));
        assertTrue(exception.getMessage().contains("narrowing"));
        assertTrue(exception.getMessage().contains("byte"));
    }

    @Test
    void invokeBindingSupportsTsj41bGenericNestedCollectionAndOptionalAdaptation() {
        final String className = InteropSample.class.getName();
        final Object payload = TsjRuntime.arrayLiteral(
                TsjRuntime.objectLiteral("count", "2"),
                TsjRuntime.objectLiteral("count", 3)
        );
        final Object optionalValues = TsjRuntime.arrayLiteral("4", "5");

        assertEquals(5, TsjJavaInterop.invokeBinding(className, "sumNestedCounts", payload));
        assertEquals("sum=9", TsjJavaInterop.invokeBinding(className, "optionalIntegerSummary", optionalValues));
        assertEquals("empty", TsjJavaInterop.invokeBinding(className, "optionalIntegerSummary", TsjRuntime.undefined()));
    }

    @Test
    void invokeBindingSupportsTsj41bGenericMapKeyAndValueAdaptation() {
        final String className = InteropSample.class.getName();
        final Object weighted = TsjRuntime.objectLiteral(
                "2",
                TsjRuntime.arrayLiteral("1.5", "2.25"),
                "3",
                TsjRuntime.arrayLiteral(1)
        );

        final Object result = TsjJavaInterop.invokeBinding(className, "weightedTotal", weighted);
        assertEquals(10.5d, ((Number) result).doubleValue());
    }

    @Test
    void invokeBindingReportsTsj41bGenericTargetContextOnAdaptationFailure() {
        final String className = InteropSample.class.getName();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(
                        className,
                        "joinModes",
                        TsjRuntime.arrayLiteral("ALPHA", "GAMMA")
                )
        );
        assertTrue(exception.getMessage().contains("Generic interop conversion failed"));
        assertTrue(exception.getMessage().contains("java.util.List"));
        assertTrue(exception.getMessage().contains("InteropMode"));
        assertTrue(exception.getMessage().contains("Unknown enum constant"));
    }

    @Test
    void invokeBindingUsesWildcardSuperLowerBoundDuringGenericListElementConversion() {
        final String className = InteropSample.class.getName();
        final Object result = TsjJavaInterop.invokeBinding(
                className,
                "wildcardSuperElementType",
                TsjRuntime.arrayLiteral("7")
        );
        assertEquals("java.lang.Integer:7", result);
    }

    @Test
    void invokeBindingRejectsIntersectionTypeVariableWhenSecondaryBoundIsNotSatisfied() {
        final String className = InteropSample.class.getName();
        assertEquals(
                "ok",
                TsjJavaInterop.invokeBinding(
                        className,
                        "requireRunnableAndCloseable",
                        new RunnableAndCloseableSample()
                )
        );

        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(
                        className,
                        "requireRunnableAndCloseable",
                        (TsjCallable) args -> TsjRuntime.undefined()
                )
        );
        assertTrue(exception.getMessage().contains("No compatible static method"));
        assertTrue(exception.getMessage().contains("AutoCloseable"));
    }

    @Test
    void invokeBindingSupportsTsj41cDefaultInterfaceMethodDispatch() {
        final String className = DefaultGreeterImpl.class.getName();
        final Object receiver = TsjJavaInterop.invokeBinding(className, "$new");

        assertEquals(
                "hello-tsj",
                TsjJavaInterop.invokeBinding(className, "$instance$greet", receiver, "tsj")
        );
    }

    @Test
    void invokeBindingPrefersTsj41cConcreteMethodOverBridgeCandidates() {
        final String className = IntegerBridgeSample.class.getName();
        final Object receiver = TsjJavaInterop.invokeBinding(className, "$new");

        assertEquals(8, TsjJavaInterop.invokeBinding(className, "$instance$bump", receiver, 7));

        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(className, "$instance$bump", receiver, "bad")
        );
        assertTrue(exception.getMessage().contains("Candidates:"));
        assertTrue(exception.getMessage().contains("bump(Integer)"));
        assertFalse(exception.getMessage().contains("bump(Number)"));
    }

    @Test
    void invokeBindingReportsTsj41cDiagnosticForNonPublicReflectiveMethod() {
        final String className = RestrictedInteropSample.class.getName();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeBinding(className, "hiddenStatic")
        );
        assertTrue(exception.getMessage().contains("TSJ-INTEROP-REFLECTIVE"));
        assertTrue(exception.getMessage().contains("hiddenStatic"));
        assertTrue(exception.getMessage().contains("non-public"));
    }

    @Test
    void toJavaSupportsPromiseToFutureErrorPropagation() {
        final CompletableFuture<?> future = (CompletableFuture<?>) TsjInteropCodec.toJava(
                TsjPromise.rejected("boom"),
                CompletableFuture.class
        );
        TsjRuntime.flushMicrotasks();
        final Throwable throwable = assertThrows(RuntimeException.class, future::join);
        assertTrue(throwable.getCause() != null);
        assertTrue(throwable.getCause().getMessage().contains("boom"));
    }

    @Test
    void toJavaRejectsInvalidArrayLikeForArrayConversion() {
        final Object nonArrayLike = TsjRuntime.objectLiteral("value", 1);
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjInteropCodec.toJava(nonArrayLike, int[].class)
        );
        assertTrue(exception.getMessage().contains("array-like"));
    }

    private static Object capturePromiseValue(final TsjPromise promise) {
        final List<Object> holder = new ArrayList<>();
        promise.then(
                (TsjCallable) args -> {
                    holder.add(args.length > 0 ? args[0] : TsjRuntime.undefined());
                    return TsjRuntime.undefined();
                },
                (TsjCallable) args -> {
                    holder.add(args.length > 0 ? args[0] : TsjRuntime.undefined());
                    return TsjRuntime.undefined();
                }
        );
        TsjRuntime.flushMicrotasks();
        return holder.getFirst();
    }

    public static final class InteropSample {
        public static int GLOBAL = 7;
        public int value;

        public InteropSample(final int seed) {
            this.value = seed;
        }

        public int add(final int delta) {
            this.value += delta;
            return this.value;
        }

        public String describe() {
            return "seed=" + this.value;
        }

        public static String pick(final int ignored) {
            return "int";
        }

        public static String pick(final double ignored) {
            return "double";
        }

        public static String join(final String prefix, final String... values) {
            final StringBuilder builder = new StringBuilder(prefix);
            for (String value : values) {
                builder.append(":").append(value);
            }
            return builder.toString();
        }

        public static String joinNullAware(final String prefix, final String... values) {
            final StringBuilder builder = new StringBuilder(prefix);
            for (String value : values) {
                builder.append(":");
                if (value == null) {
                    builder.append("<null>");
                } else {
                    builder.append(value);
                }
            }
            return builder.toString();
        }

        public static String needsSample(final InteropSample sample) {
            return sample.describe();
        }

        public static String nullKind(final Object value) {
            return value == null ? "null" : value.getClass().getSimpleName();
        }

        public String nullKindInstance(final Object value) {
            return value == null ? "null" : value.getClass().getSimpleName();
        }

        public static String classify(final List<?> values) {
            return "list";
        }

        public static String classify(final Map<?, ?> value) {
            return "map";
        }

        public static String classify(final Object value) {
            return "object";
        }

        public static String modeName(final InteropMode mode) {
            return mode.name();
        }

        public static String widenPrimitive(final long ignored) {
            return "long";
        }

        public static String widenPrimitive(final double ignored) {
            return "double";
        }

        public static String widenWrapper(final Long ignored) {
            return "Long";
        }

        public static String widenWrapper(final Double ignored) {
            return "Double";
        }

        public static String primitiveVsWrapper(final int ignored) {
            return "int";
        }

        public static String primitiveVsWrapper(final Integer ignored) {
            return "Integer";
        }

        public static String acceptByte(final byte value) {
            return "byte=" + value;
        }

        public static int sumNestedCounts(final List<Map<String, Integer>> payload) {
            int total = 0;
            for (Map<String, Integer> entry : payload) {
                total += entry.get("count");
            }
            return total;
        }

        public static String optionalIntegerSummary(final Optional<List<Integer>> values) {
            if (values.isEmpty()) {
                return "empty";
            }
            int total = 0;
            for (Integer value : values.get()) {
                total += value;
            }
            return "sum=" + total;
        }

        public static double weightedTotal(final Map<Integer, List<Double>> weighted) {
            double total = 0.0d;
            for (Map.Entry<Integer, List<Double>> entry : weighted.entrySet()) {
                for (Double value : entry.getValue()) {
                    total += entry.getKey() * value;
                }
            }
            return total;
        }

        public static String joinModes(final List<InteropMode> modes) {
            final StringBuilder builder = new StringBuilder();
            for (int index = 0; index < modes.size(); index++) {
                if (index > 0) {
                    builder.append(",");
                }
                builder.append(modes.get(index).name());
            }
            return builder.toString();
        }

        public static String wildcardSuperElementType(final List<? super Integer> values) {
            if (values.isEmpty()) {
                return "empty";
            }
            final Object first = values.get(0);
            if (first == null) {
                return "null";
            }
            return first.getClass().getName() + ":" + first;
        }

        public static <T extends Runnable & AutoCloseable> String requireRunnableAndCloseable(final T callback) {
            callback.run();
            try {
                callback.close();
            } catch (final Exception exception) {
                throw new IllegalStateException(exception);
            }
            return "ok";
        }

        public static int applyOperator(final IntUnaryOperator operator, final int seed) {
            return operator.applyAsInt(seed);
        }

        public static CompletableFuture<String> futureUpper(final String value) {
            return CompletableFuture.completedFuture(value.toUpperCase());
        }
    }

    enum InteropMode {
        ALPHA,
        BETA
    }

    public interface DefaultGreeter {
        default String greet(final String name) {
            return "hello-" + name;
        }
    }

    public static final class DefaultGreeterImpl implements DefaultGreeter {
    }

    public static class NumberBridgeBase<T extends Number> {
        public T bump(final T value) {
            return value;
        }
    }

    public static final class IntegerBridgeSample extends NumberBridgeBase<Integer> {
        @Override
        public Integer bump(final Integer value) {
            return value + 1;
        }
    }

    public static final class RestrictedInteropSample {
        static String hiddenStatic() {
            return "hidden";
        }
    }

    public static final class RunnableAndCloseableSample implements Runnable, AutoCloseable {
        @Override
        public void run() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
