package dev.tsj.generated;

public final class Main011DecoratorsProgram {
    private Main011DecoratorsProgram() {
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
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_1 = new dev.tsj.runtime.TsjPropertyAccessCache("x");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_2 = new dev.tsj.runtime.TsjPropertyAccessCache("value");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_3 = new dev.tsj.runtime.TsjPropertyAccessCache("port");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_4 = new dev.tsj.runtime.TsjPropertyAccessCache("host");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_5 = new dev.tsj.runtime.TsjPropertyAccessCache("tags");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_6 = new dev.tsj.runtime.TsjPropertyAccessCache("prototype");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_7 = new dev.tsj.runtime.TsjPropertyAccessCache("prototype");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_8 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_9 = new dev.tsj.runtime.TsjPropertyAccessCache("tags");

    private static synchronized void __tsjBootstrap() {
        if (__TSJ_BOOTSTRAPPED) {
            return;
        }
        __TSJ_TOP_LEVEL_CLASSES.clear();
        final dev.tsj.runtime.TsjCell sealed_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell log_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell defaultVal_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell addTag_cell = new dev.tsj.runtime.TsjCell(null);
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	5	1
        sealed_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis, Object... lambdaArgs) -> {
            final dev.tsj.runtime.TsjCell target_cell = new dev.tsj.runtime.TsjCell(lambdaArgs.length > 0 ? lambdaArgs[0] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	6	3
            dev.tsj.runtime.TsjRuntime.invokeMember(OBJECT_BUILTIN_CELL.get(), "seal", target_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	7	3
            dev.tsj.runtime.TsjRuntime.invokeMember(OBJECT_BUILTIN_CELL.get(), "seal", dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_0, target_cell.get(), "prototype"));
            return null;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	11	1
        final dev.tsj.runtime.TsjCell Frozen_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass Frozen_class = new dev.tsj.runtime.TsjClass("Frozen", null);
        Frozen_cell.set(Frozen_class);
        __TSJ_TOP_LEVEL_CLASSES.put("Frozen", Frozen_cell.get());
        Frozen_class.setConstructor((dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	12	3
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "x", Integer.valueOf(1));
            return null;
        });
        Frozen_class.defineMethod("method", (dev.tsj.runtime.TsjObject thisObject_1, Object... methodArgs_1) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	13	14
            return dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_1, thisObject_1, "x");
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	15	7
        final dev.tsj.runtime.TsjCell f_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Frozen_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	16	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("class_dec:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.invokeMember(f_cell.get(), "method"), Integer.valueOf(1)))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	19	1
        log_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
            final dev.tsj.runtime.TsjCell _target_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 0 ? lambdaArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
            final dev.tsj.runtime.TsjCell _key_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 1 ? lambdaArgs_1[1] : dev.tsj.runtime.TsjRuntime.undefined());
            final dev.tsj.runtime.TsjCell descriptor_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 2 ? lambdaArgs_1[2] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	20	9
            final dev.tsj.runtime.TsjCell orig_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_2, descriptor_cell.get(), "value"));
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	21	3
            dev.tsj.runtime.TsjRuntime.setProperty(descriptor_cell.get(), "value", ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_2, Object... lambdaArgs_2) -> {
    final dev.tsj.runtime.TsjCell args_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.restArgs(lambdaArgs_2, 0));
    return dev.tsj.runtime.TsjRuntime.add("logged:", dev.tsj.runtime.TsjRuntime.invokeMember(orig_cell.get(), "apply", lambdaThis_2, args_cell.get()));
}));
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	24	3
            return descriptor_cell.get();
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	27	1
        final dev.tsj.runtime.TsjCell Svc_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass Svc_class = new dev.tsj.runtime.TsjClass("Svc", null);
        Svc_cell.set(Svc_class);
        __TSJ_TOP_LEVEL_CLASSES.put("Svc", Svc_cell.get());
        Svc_class.defineMethod("hello", (dev.tsj.runtime.TsjObject thisObject_2, Object... methodArgs_2) -> {
            final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_2.length > 0 ? methodArgs_2[0] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	29	25
            return name_cell.get();
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	31	7
        final dev.tsj.runtime.TsjCell svc_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Svc_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	32	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("method_dec:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.invokeMember(svc_cell.get(), "hello", "world"), "logged:world"))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	35	1
        defaultVal_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_2, Object... lambdaArgs_2) -> {
            final dev.tsj.runtime.TsjCell val_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_2.length > 0 ? lambdaArgs_2[0] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	36	3
            return ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_3, Object... lambdaArgs_3) -> {
    final dev.tsj.runtime.TsjCell _target_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_3.length > 0 ? lambdaArgs_3[0] : dev.tsj.runtime.TsjRuntime.undefined());
    final dev.tsj.runtime.TsjCell key_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_3.length > 1 ? lambdaArgs_3[1] : dev.tsj.runtime.TsjRuntime.undefined());
    final dev.tsj.runtime.TsjCell symbol_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.call(SYMBOL_BUILTIN_CELL.get(), key_cell.get()));
    dev.tsj.runtime.TsjRuntime.invokeMember(OBJECT_BUILTIN_CELL.get(), "defineProperty", _target_cell.get(), key_cell.get(), dev.tsj.runtime.TsjRuntime.objectLiteral("get", ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_4, Object... lambdaArgs_4) -> {
    return dev.tsj.runtime.TsjRuntime.nullishCoalesce(dev.tsj.runtime.TsjRuntime.indexRead(lambdaThis_4, symbol_cell.get()), () -> val_cell.get());
}), "set", ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_5, Object... lambdaArgs_5) -> {
    final dev.tsj.runtime.TsjCell v_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_5.length > 0 ? lambdaArgs_5[0] : dev.tsj.runtime.TsjRuntime.undefined());
    dev.tsj.runtime.TsjRuntime.setPropertyDynamic(lambdaThis_5, symbol_cell.get(), v_cell.get());
    return null;
})));
    return null;
});
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	45	1
        final dev.tsj.runtime.TsjCell Config_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass Config_class = new dev.tsj.runtime.TsjClass("Config", null);
        Config_cell.set(Config_class);
        __TSJ_TOP_LEVEL_CLASSES.put("Config", Config_cell.get());
        Config_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_3, Object... methodArgs_3) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	47	3
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_3, "port", dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	49	3
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_3, "host", dev.tsj.runtime.TsjRuntime.undefined());
            return null;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	51	7
        final dev.tsj.runtime.TsjCell cfg_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Config_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	52	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("prop_dec:", dev.tsj.runtime.TsjRuntime.logicalAnd(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_3, cfg_cell.get(), "port"), Integer.valueOf(3000))), () -> Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_4, cfg_cell.get(), "host"), "localhost")))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	55	1
        addTag_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_3, Object... lambdaArgs_3) -> {
            final dev.tsj.runtime.TsjCell tag_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_3.length > 0 ? lambdaArgs_3[0] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	56	3
            return ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_4, Object... lambdaArgs_4) -> {
    final dev.tsj.runtime.TsjCell target_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_4.length > 0 ? lambdaArgs_4[0] : dev.tsj.runtime.TsjRuntime.undefined());
    dev.tsj.runtime.TsjRuntime.setProperty(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_7, target_cell.get(), "prototype"), "tags", dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.logicalOr(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_5, dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_6, target_cell.get(), "prototype"), "tags"), () -> dev.tsj.runtime.TsjRuntime.arrayLiteral()), "concat", tag_cell.get()));
    return null;
});
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	63	1
        final dev.tsj.runtime.TsjCell Tagged_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass Tagged_class = new dev.tsj.runtime.TsjClass("Tagged", null);
        Tagged_cell.set(Tagged_class);
        __TSJ_TOP_LEVEL_CLASSES.put("Tagged", Tagged_cell.get());
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	64	7
        final dev.tsj.runtime.TsjCell t_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Tagged_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	65	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("multi_dec:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_8, dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_9, t_cell.get(), "tags"), "length"), Integer.valueOf(2)))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	68	1
        final dev.tsj.runtime.TsjCell StaticSvc_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass StaticSvc_class = new dev.tsj.runtime.TsjClass("StaticSvc", null);
        StaticSvc_cell.set(StaticSvc_class);
        __TSJ_TOP_LEVEL_CLASSES.put("StaticSvc", StaticSvc_cell.get());
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	70	3
        dev.tsj.runtime.TsjRuntime.setProperty(StaticSvc_cell.get(), "greet", ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_4, Object... lambdaArgs_4) -> {
    return "hello";
}));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/grammar/011_decorators.ts	72	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("static_dec:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.invokeMember(StaticSvc_cell.get(), "greet"), "logged:hello"))));
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
