package dev.tsj.generated;

public final class Main002SymbolProgram {
    private Main002SymbolProgram() {
    }

    private static final dev.tsj.runtime.TsjCell PROMISE_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.promiseBuiltin());
    private static final dev.tsj.runtime.TsjCell ERROR_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.errorBuiltin());
    private static final dev.tsj.runtime.TsjCell STRING_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.stringBuiltin());
    private static final dev.tsj.runtime.TsjCell JSON_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.jsonBuiltin());
    private static final dev.tsj.runtime.TsjCell OBJECT_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectBuiltin());
    private static final dev.tsj.runtime.TsjCell ARRAY_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arrayBuiltin());
    private static final dev.tsj.runtime.TsjCell MAP_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.mapBuiltin());
    private static final dev.tsj.runtime.TsjCell SET_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.setBuiltin());
    private static final dev.tsj.runtime.TsjCell DATE_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.dateBuiltin());
    private static final dev.tsj.runtime.TsjCell REGEXP_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.regexpBuiltin());
    private static final dev.tsj.runtime.TsjCell TYPE_ERROR_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.typeErrorBuiltin());
    private static final dev.tsj.runtime.TsjCell RANGE_ERROR_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.rangeErrorBuiltin());
    private static final dev.tsj.runtime.TsjCell MATH_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.mathBuiltin());
    private static final dev.tsj.runtime.TsjCell NUMBER_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.numberBuiltin());
    private static final dev.tsj.runtime.TsjCell BIGINT_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.bigIntBuiltin());
    private static final dev.tsj.runtime.TsjCell SYMBOL_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.symbolBuiltin());
    private static final dev.tsj.runtime.TsjCell PARSE_INT_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.parseIntBuiltin());
    private static final dev.tsj.runtime.TsjCell PARSE_FLOAT_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.parseFloatBuiltin());
    private static final dev.tsj.runtime.TsjCell INFINITY_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.infinity());
    private static final dev.tsj.runtime.TsjCell NAN_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.nanValue());
    private static final dev.tsj.runtime.TsjCell UNDEFINED_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(null);
    private static final Object __TSJ_ASYNC_BREAK_SIGNAL = new Object();
    private static final Object __TSJ_ASYNC_CONTINUE_SIGNAL = new Object();
    private static final java.util.Map<String, Object> __TSJ_TOP_LEVEL_CLASSES = new java.util.LinkedHashMap<>();
    private static boolean __TSJ_BOOTSTRAPPED = false;

    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_0 = new dev.tsj.runtime.TsjPropertyAccessCache("prototype");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_1 = new dev.tsj.runtime.TsjPropertyAccessCache("iterator");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_2 = new dev.tsj.runtime.TsjPropertyAccessCache("start");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_3 = new dev.tsj.runtime.TsjPropertyAccessCache("end");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_4 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_5 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_6 = new dev.tsj.runtime.TsjPropertyAccessCache("0");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_7 = new dev.tsj.runtime.TsjPropertyAccessCache("2");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_8 = new dev.tsj.runtime.TsjPropertyAccessCache("prototype");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_9 = new dev.tsj.runtime.TsjPropertyAccessCache("toPrimitive");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_10 = new dev.tsj.runtime.TsjPropertyAccessCache("amount");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_11 = new dev.tsj.runtime.TsjPropertyAccessCache("amount");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_12 = new dev.tsj.runtime.TsjPropertyAccessCache("currency");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_13 = new dev.tsj.runtime.TsjPropertyAccessCache("amount");

    private static synchronized void __tsjBootstrap() {
        if (__TSJ_BOOTSTRAPPED) {
            return;
        }
        __TSJ_TOP_LEVEL_CLASSES.clear();
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	5	7
        final dev.tsj.runtime.TsjCell s1_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.call(SYMBOL_BUILTIN_CELL.get(), "test"));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	6	7
        final dev.tsj.runtime.TsjCell s2_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.call(SYMBOL_BUILTIN_CELL.get(), "test"));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	7	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("unique:", Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.strictEquals(s1_cell.get(), s2_cell.get()))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	10	7
        final dev.tsj.runtime.TsjCell key_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.call(SYMBOL_BUILTIN_CELL.get(), "myKey"));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	11	7
        final dev.tsj.runtime.TsjCell obj_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral());
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	12	1
        dev.tsj.runtime.TsjRuntime.setPropertyDynamic(obj_cell.get(), key_cell.get(), Integer.valueOf(42));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	13	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("prop_key:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.indexRead(obj_cell.get(), key_cell.get()), Integer.valueOf(42)))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	16	7
        final dev.tsj.runtime.TsjCell g1_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.invokeMember(SYMBOL_BUILTIN_CELL.get(), "for", "shared"));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	17	7
        final dev.tsj.runtime.TsjCell g2_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.invokeMember(SYMBOL_BUILTIN_CELL.get(), "for", "shared"));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	18	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("global_same:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(g1_cell.get(), g2_cell.get()))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	21	7
        final dev.tsj.runtime.TsjCell desc_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.invokeMember(SYMBOL_BUILTIN_CELL.get(), "keyFor", g1_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	22	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("key_for:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(desc_cell.get(), "shared"))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	25	1
        final dev.tsj.runtime.TsjCell Range_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass Range_class = new dev.tsj.runtime.TsjClass("Range", null);
        Range_cell.set(Range_class);
        __TSJ_TOP_LEVEL_CLASSES.put("Range", Range_cell.get());
        Range_class.setConstructor((dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
            final dev.tsj.runtime.TsjCell start_cell = new dev.tsj.runtime.TsjCell(methodArgs.length > 0 ? methodArgs[0] : dev.tsj.runtime.TsjRuntime.undefined());
            final dev.tsj.runtime.TsjCell end_cell = new dev.tsj.runtime.TsjCell(methodArgs.length > 1 ? methodArgs[1] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	26	15
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "start", start_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	26	38
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "end", end_cell.get());
            return null;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	27	3
        dev.tsj.runtime.TsjRuntime.setPropertyDynamic(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_0, Range_cell.get(), "prototype"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_1, SYMBOL_BUILTIN_CELL.get(), "iterator"), ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis, Object... lambdaArgs) -> {
    final dev.tsj.runtime.TsjCell cur_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_2, lambdaThis, "start"));
    final dev.tsj.runtime.TsjCell end_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_3, lambdaThis, "end"));
    return dev.tsj.runtime.TsjRuntime.objectLiteral("next", ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThanOrEqual(cur_cell.get(), end_cell.get())))) {
        return dev.tsj.runtime.TsjRuntime.objectLiteral("value", dev.tsj.runtime.TsjRuntime.call(((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_2) -> {
    final dev.tsj.runtime.TsjCell postfix_1_cell = new dev.tsj.runtime.TsjCell(cur_cell.get());
    cur_cell.set(dev.tsj.runtime.TsjRuntime.add(cur_cell.get(), Integer.valueOf(1)));
    return postfix_1_cell.get();
})), "done", Boolean.FALSE);
    }
    return dev.tsj.runtime.TsjRuntime.objectLiteral("value", dev.tsj.runtime.TsjRuntime.undefined(), "done", Boolean.TRUE);
}));
}));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	38	7
        final dev.tsj.runtime.TsjCell r_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Range_cell.get(), Integer.valueOf(1), Integer.valueOf(3)));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	39	7
        final dev.tsj.runtime.TsjCell vals_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arrayLiteral());
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	40	1
        if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.TRUE)) {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	40	1
            final dev.tsj.runtime.TsjCell forOfValues_2_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.forOfValues(r_cell.get()));
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	40	1
            final dev.tsj.runtime.TsjCell forIndex_3_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	40	1
            while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(forIndex_3_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_4, forOfValues_2_cell.get(), "length"))))) {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	40	1
                final dev.tsj.runtime.TsjCell v_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.indexRead(forOfValues_2_cell.get(), forIndex_3_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	41	3
                dev.tsj.runtime.TsjRuntime.invokeMember(vals_cell.get(), "push", v_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	40	1
                forIndex_3_cell.set(dev.tsj.runtime.TsjRuntime.add(forIndex_3_cell.get(), Integer.valueOf(1)));
            }
        }
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	43	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("iterator:", dev.tsj.runtime.TsjRuntime.logicalAnd(dev.tsj.runtime.TsjRuntime.logicalAnd(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_5, vals_cell.get(), "length"), Integer.valueOf(3))), () -> Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_6, vals_cell.get(), "0"), Integer.valueOf(1)))), () -> Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_7, vals_cell.get(), "2"), Integer.valueOf(3))))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	46	1
        final dev.tsj.runtime.TsjCell Money_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass Money_class = new dev.tsj.runtime.TsjClass("Money", null);
        Money_cell.set(Money_class);
        __TSJ_TOP_LEVEL_CLASSES.put("Money", Money_cell.get());
        Money_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_1, Object... methodArgs_1) -> {
            final dev.tsj.runtime.TsjCell amount_cell = new dev.tsj.runtime.TsjCell(methodArgs_1.length > 0 ? methodArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
            final dev.tsj.runtime.TsjCell currency_cell = new dev.tsj.runtime.TsjCell(methodArgs_1.length > 1 ? methodArgs_1[1] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	47	15
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_1, "amount", amount_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	47	39
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_1, "currency", currency_cell.get());
            return null;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	48	3
        dev.tsj.runtime.TsjRuntime.setPropertyDynamic(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_8, Money_cell.get(), "prototype"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_9, SYMBOL_BUILTIN_CELL.get(), "toPrimitive"), ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
    final dev.tsj.runtime.TsjCell hint_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 0 ? lambdaArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(hint_cell.get(), "number")))) {
        return dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_10, lambdaThis_1, "amount");
    }
    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(hint_cell.get(), "string")))) {
        return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_11, lambdaThis_1, "amount")), " "), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_12, lambdaThis_1, "currency")), "");
    }
    return dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_13, lambdaThis_1, "amount");
}));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	54	7
        final dev.tsj.runtime.TsjCell m_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Money_cell.get(), Integer.valueOf(42), "USD"));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	55	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("to_prim_num:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.unaryPlus(m_cell.get()), Integer.valueOf(42)))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	56	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("to_prim_str:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", m_cell.get()), ""), "42 USD"))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/002_symbol.ts	59	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("typeof_sym:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.typeOf(s1_cell.get()), "symbol"))));
        dev.tsj.runtime.TsjRuntime.flushMicrotasks();
        __TSJ_BOOTSTRAPPED = true;
    }

    public static Object __tsjInvokeClass(final String className, final String methodName, final Object[] constructorArgs, final Object... args) {
        return __tsjInvokeClassWithInjection(className, methodName, constructorArgs, new String[0], new Object[0], new String[0], new Object[0], args);
    }

    public static Object __tsjInvokeClassWithInjection(final String className, final String methodName, final Object[] constructorArgs, final String[] fieldNames, final Object[] fieldValues, final String[] setterNames, final Object[] setterValues, final Object... args) {
        __tsjBootstrap();
        final Object classValue = __TSJ_TOP_LEVEL_CLASSES.get(className);
        if (classValue == null) {
            throw new IllegalArgumentException("TSJ controller class not found: " + className);
        }
        final Object[] ctorArgs = constructorArgs == null ? new Object[0] : constructorArgs;
        final String[] safeFieldNames = fieldNames == null ? new String[0] : fieldNames;
        final Object[] safeFieldValues = fieldValues == null ? new Object[0] : fieldValues;
        if (safeFieldNames.length != safeFieldValues.length) {
            throw new IllegalArgumentException("TSJ injection field name/value length mismatch.");
        }
        final String[] safeSetterNames = setterNames == null ? new String[0] : setterNames;
        final Object[] safeSetterValues = setterValues == null ? new Object[0] : setterValues;
        if (safeSetterNames.length != safeSetterValues.length) {
            throw new IllegalArgumentException("TSJ injection setter name/value length mismatch.");
        }
        final Object instance = dev.tsj.runtime.TsjRuntime.construct(classValue, ctorArgs);
        for (int i = 0; i < safeFieldNames.length; i++) {
            dev.tsj.runtime.TsjRuntime.setProperty(instance, safeFieldNames[i], safeFieldValues[i]);
        }
        for (int i = 0; i < safeSetterNames.length; i++) {
            dev.tsj.runtime.TsjRuntime.invokeMember(instance, safeSetterNames[i], safeSetterValues[i]);
        }
        final Object result = dev.tsj.runtime.TsjRuntime.invokeMember(instance, methodName, args);
        dev.tsj.runtime.TsjRuntime.flushMicrotasks();
        return result;
    }

    public static Object __tsjInvokeController(final String className, final String methodName, final Object... args) {
        return __tsjInvokeClass(className, methodName, new Object[0], args);
    }

    public static void main(String[] args) {
        __tsjBootstrap();
    }
}
