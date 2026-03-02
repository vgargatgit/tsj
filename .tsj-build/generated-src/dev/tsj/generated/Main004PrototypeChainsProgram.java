package dev.tsj.generated;

public final class Main004PrototypeChainsProgram {
    private Main004PrototypeChainsProgram() {
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

    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_0 = new dev.tsj.runtime.TsjPropertyAccessCache("count");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_1 = new dev.tsj.runtime.TsjPropertyAccessCache("count");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_2 = new dev.tsj.runtime.TsjPropertyAccessCache("count");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_3 = new dev.tsj.runtime.TsjPropertyAccessCache("prototype");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_4 = new dev.tsj.runtime.TsjPropertyAccessCache("info");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_5 = new dev.tsj.runtime.TsjPropertyAccessCache("extra");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_6 = new dev.tsj.runtime.TsjPropertyAccessCache("timestamp");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_7 = new dev.tsj.runtime.TsjPropertyAccessCache("name");

    private static synchronized void __tsjBootstrap() {
        if (__TSJ_BOOTSTRAPPED) {
            return;
        }
        __TSJ_TOP_LEVEL_CLASSES.clear();
        final dev.tsj.runtime.TsjCell addTimestamp_cell = new dev.tsj.runtime.TsjCell(null);
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	5	1
        final dev.tsj.runtime.TsjCell L0_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L0_class = new dev.tsj.runtime.TsjClass("L0", null);
        L0_cell.set(L0_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L0", L0_cell.get());
        L0_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	5	22
            return Integer.valueOf(0);
        });
        L0_class.defineMethod("base", (dev.tsj.runtime.TsjObject thisObject_1, Object... methodArgs_1) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	5	43
            return "L0";
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	6	1
        final dev.tsj.runtime.TsjCell L1_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L1_class = new dev.tsj.runtime.TsjClass("L1", dev.tsj.runtime.TsjRuntime.asClass(L0_cell.get()));
        L1_cell.set(L1_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L1", L1_cell.get());
        L1_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_2, Object... methodArgs_2) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	6	33
            return Integer.valueOf(1);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	7	1
        final dev.tsj.runtime.TsjCell L2_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L2_class = new dev.tsj.runtime.TsjClass("L2", dev.tsj.runtime.TsjRuntime.asClass(L1_cell.get()));
        L2_cell.set(L2_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L2", L2_cell.get());
        L2_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_3, Object... methodArgs_3) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	7	33
            return Integer.valueOf(2);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	8	1
        final dev.tsj.runtime.TsjCell L3_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L3_class = new dev.tsj.runtime.TsjClass("L3", dev.tsj.runtime.TsjRuntime.asClass(L2_cell.get()));
        L3_cell.set(L3_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L3", L3_cell.get());
        L3_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_4, Object... methodArgs_4) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	8	33
            return Integer.valueOf(3);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	9	1
        final dev.tsj.runtime.TsjCell L4_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L4_class = new dev.tsj.runtime.TsjClass("L4", dev.tsj.runtime.TsjRuntime.asClass(L3_cell.get()));
        L4_cell.set(L4_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L4", L4_cell.get());
        L4_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_5, Object... methodArgs_5) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	9	33
            return Integer.valueOf(4);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	10	1
        final dev.tsj.runtime.TsjCell L5_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L5_class = new dev.tsj.runtime.TsjClass("L5", dev.tsj.runtime.TsjRuntime.asClass(L4_cell.get()));
        L5_cell.set(L5_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L5", L5_cell.get());
        L5_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_6, Object... methodArgs_6) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	10	33
            return Integer.valueOf(5);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	11	1
        final dev.tsj.runtime.TsjCell L6_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L6_class = new dev.tsj.runtime.TsjClass("L6", dev.tsj.runtime.TsjRuntime.asClass(L5_cell.get()));
        L6_cell.set(L6_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L6", L6_cell.get());
        L6_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_7, Object... methodArgs_7) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	11	33
            return Integer.valueOf(6);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	12	1
        final dev.tsj.runtime.TsjCell L7_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L7_class = new dev.tsj.runtime.TsjClass("L7", dev.tsj.runtime.TsjRuntime.asClass(L6_cell.get()));
        L7_cell.set(L7_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L7", L7_cell.get());
        L7_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_8, Object... methodArgs_8) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	12	33
            return Integer.valueOf(7);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	13	1
        final dev.tsj.runtime.TsjCell L8_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L8_class = new dev.tsj.runtime.TsjClass("L8", dev.tsj.runtime.TsjRuntime.asClass(L7_cell.get()));
        L8_cell.set(L8_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L8", L8_cell.get());
        L8_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_9, Object... methodArgs_9) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	13	33
            return Integer.valueOf(8);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	14	1
        final dev.tsj.runtime.TsjCell L9_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass L9_class = new dev.tsj.runtime.TsjClass("L9", dev.tsj.runtime.TsjRuntime.asClass(L8_cell.get()));
        L9_cell.set(L9_class);
        __TSJ_TOP_LEVEL_CLASSES.put("L9", L9_cell.get());
        L9_class.defineMethod("level", (dev.tsj.runtime.TsjObject thisObject_10, Object... methodArgs_10) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	14	33
            return Integer.valueOf(9);
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	16	7
        final dev.tsj.runtime.TsjCell deep_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(L9_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	17	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("deep_level:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.invokeMember(deep_cell.get(), "level"), Integer.valueOf(9)))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	18	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("deep_base:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.invokeMember(deep_cell.get(), "base"), "L0"))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	19	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("deep_inst:", dev.tsj.runtime.TsjRuntime.logicalAnd(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.instanceOf(deep_cell.get(), L0_cell.get())), () -> Boolean.valueOf(dev.tsj.runtime.TsjRuntime.instanceOf(deep_cell.get(), L5_cell.get())))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	22	1
        final dev.tsj.runtime.TsjCell Counter_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass Counter_class = new dev.tsj.runtime.TsjClass("Counter", null);
        Counter_cell.set(Counter_class);
        __TSJ_TOP_LEVEL_CLASSES.put("Counter", Counter_cell.get());
        Counter_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_11, Object... methodArgs_11) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	23	3
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_11, "count", Integer.valueOf(0));
            return null;
        });
        Counter_class.defineMethod("increment", (dev.tsj.runtime.TsjObject thisObject_12, Object... methodArgs_12) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	24	17
            dev.tsj.runtime.TsjRuntime.call(((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs) -> {
    final dev.tsj.runtime.TsjCell postfix_1_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_0, thisObject_12, "count"));
    dev.tsj.runtime.TsjRuntime.setProperty(thisObject_12, "count", dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_1, thisObject_12, "count"), Integer.valueOf(1)));
    return postfix_1_cell.get();
}));
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	24	31
            return thisObject_12;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	26	1
        final dev.tsj.runtime.TsjCell DoubleCounter_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass DoubleCounter_class = new dev.tsj.runtime.TsjClass("DoubleCounter", dev.tsj.runtime.TsjRuntime.asClass(Counter_cell.get()));
        DoubleCounter_cell.set(DoubleCounter_class);
        __TSJ_TOP_LEVEL_CLASSES.put("DoubleCounter", DoubleCounter_cell.get());
        DoubleCounter_class.defineMethod("increment", (dev.tsj.runtime.TsjObject thisObject_13, Object... methodArgs_13) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	27	17
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_13, "count", dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getProperty(thisObject_13, "count"), Integer.valueOf(2)));
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	27	34
            return thisObject_13;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	29	1
        final dev.tsj.runtime.TsjCell TripleCounter_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass TripleCounter_class = new dev.tsj.runtime.TsjClass("TripleCounter", dev.tsj.runtime.TsjRuntime.asClass(DoubleCounter_cell.get()));
        TripleCounter_cell.set(TripleCounter_class);
        __TSJ_TOP_LEVEL_CLASSES.put("TripleCounter", TripleCounter_cell.get());
        TripleCounter_class.defineMethod("increment", (dev.tsj.runtime.TsjObject thisObject_14, Object... methodArgs_14) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	30	17
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_14, "count", dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getProperty(thisObject_14, "count"), Integer.valueOf(3)));
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	30	34
            return thisObject_14;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	32	7
        final dev.tsj.runtime.TsjCell tc_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(TripleCounter_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	33	1
        dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.invokeMember(tc_cell.get(), "increment"), "increment"), "increment");
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	34	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("override_chain:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_2, tc_cell.get(), "count"), Integer.valueOf(9)))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	37	1
        final dev.tsj.runtime.TsjCell A_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass A_class = new dev.tsj.runtime.TsjClass("A", null);
        A_cell.set(A_class);
        __TSJ_TOP_LEVEL_CLASSES.put("A", A_cell.get());
        A_class.defineMethod("greet", (dev.tsj.runtime.TsjObject thisObject_15, Object... methodArgs_15) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	38	21
            return "A";
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	40	1
        final dev.tsj.runtime.TsjCell B_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass B_class = new dev.tsj.runtime.TsjClass("B", dev.tsj.runtime.TsjRuntime.asClass(A_cell.get()));
        B_cell.set(B_class);
        __TSJ_TOP_LEVEL_CLASSES.put("B", B_cell.get());
        B_class.defineMethod("greet", (dev.tsj.runtime.TsjObject thisObject_16, Object... methodArgs_16) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	41	21
            return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.superInvokeMember(A_cell.get(), thisObject_16, "greet"), "B");
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	43	1
        final dev.tsj.runtime.TsjCell C_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass C_class = new dev.tsj.runtime.TsjClass("C", dev.tsj.runtime.TsjRuntime.asClass(B_cell.get()));
        C_cell.set(C_class);
        __TSJ_TOP_LEVEL_CLASSES.put("C", C_cell.get());
        C_class.defineMethod("greet", (dev.tsj.runtime.TsjObject thisObject_17, Object... methodArgs_17) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	44	21
            return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.superInvokeMember(B_cell.get(), thisObject_17, "greet"), "C");
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	46	1
        final dev.tsj.runtime.TsjCell D_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass D_class = new dev.tsj.runtime.TsjClass("D", dev.tsj.runtime.TsjRuntime.asClass(C_cell.get()));
        D_cell.set(D_class);
        __TSJ_TOP_LEVEL_CLASSES.put("D", D_cell.get());
        D_class.defineMethod("greet", (dev.tsj.runtime.TsjObject thisObject_18, Object... methodArgs_18) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	47	21
            return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.superInvokeMember(C_cell.get(), thisObject_18, "greet"), "D");
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	49	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("super_chain:", Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.construct(D_cell.get()), "greet"), "ABCD"))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	52	1
        final dev.tsj.runtime.TsjCell PropBase_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass PropBase_class = new dev.tsj.runtime.TsjClass("PropBase", null);
        PropBase_cell.set(PropBase_class);
        __TSJ_TOP_LEVEL_CLASSES.put("PropBase", PropBase_cell.get());
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	53	3
        dev.tsj.runtime.TsjRuntime.defineAccessorProperty(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_3, PropBase_cell.get(), "prototype"), "info", ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis, Object... lambdaArgs) -> {
    return "base";
}), dev.tsj.runtime.TsjRuntime.undefined());
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	55	1
        final dev.tsj.runtime.TsjCell PropChild_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass PropChild_class = new dev.tsj.runtime.TsjClass("PropChild", dev.tsj.runtime.TsjRuntime.asClass(PropBase_cell.get()));
        PropChild_cell.set(PropChild_class);
        __TSJ_TOP_LEVEL_CLASSES.put("PropChild", PropChild_cell.get());
        PropChild_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_19, Object... methodArgs_19) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	55	1
            dev.tsj.runtime.TsjRuntime.asClass(PropBase_cell.get()).invokeConstructor(thisObject_19);
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	56	3
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_19, "extra", Integer.valueOf(42));
            return null;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	58	7
        final dev.tsj.runtime.TsjCell pc_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(PropChild_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	59	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("prop_lookup:", dev.tsj.runtime.TsjRuntime.logicalAnd(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_4, pc_cell.get(), "info"), "base")), () -> Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_5, pc_cell.get(), "extra"), Integer.valueOf(42))))));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	62	1
        addTimestamp_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
            final dev.tsj.runtime.TsjCell Base_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 0 ? lambdaArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	63	3
            return dev.tsj.runtime.TsjRuntime.call(((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_2) -> {
    final dev.tsj.runtime.TsjCell classExpr_2_cell = new dev.tsj.runtime.TsjCell(null);
    final dev.tsj.runtime.TsjClass classExpr_2_class = new dev.tsj.runtime.TsjClass("classExpr_2", dev.tsj.runtime.TsjRuntime.asClass(Base_cell.get()));
    classExpr_2_cell.set(classExpr_2_class);
    classExpr_2_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_20, Object... methodArgs_20) -> {
        dev.tsj.runtime.TsjRuntime.asClass(Base_cell.get()).invokeConstructor(thisObject_20);
        dev.tsj.runtime.TsjRuntime.setProperty(thisObject_20, "timestamp", dev.tsj.runtime.TsjRuntime.invokeMember(DATE_BUILTIN_CELL.get(), "now"));
        return null;
    });
    classExpr_2_class.defineMethod("getTimestamp", (dev.tsj.runtime.TsjObject thisObject_21, Object... methodArgs_21) -> {
        return dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_6, thisObject_21, "timestamp");
    });
    return classExpr_2_cell.get();
}));
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	68	1
        final dev.tsj.runtime.TsjCell BaseEntity_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjClass BaseEntity_class = new dev.tsj.runtime.TsjClass("BaseEntity", null);
        BaseEntity_cell.set(BaseEntity_class);
        __TSJ_TOP_LEVEL_CLASSES.put("BaseEntity", BaseEntity_cell.get());
        BaseEntity_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_20, Object... methodArgs_20) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	68	20
            dev.tsj.runtime.TsjRuntime.setProperty(thisObject_20, "name", "entity");
            return null;
        });
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	69	7
        final dev.tsj.runtime.TsjCell Enhanced_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.call(addTimestamp_cell.get(), BaseEntity_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	70	7
        final dev.tsj.runtime.TsjCell inst_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Enhanced_cell.get()));
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/UTTA/src/stress/004_prototype_chains.ts	71	1
        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("mixin:", dev.tsj.runtime.TsjRuntime.logicalAnd(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_7, inst_cell.get(), "name"), "entity")), () -> Boolean.valueOf(dev.tsj.runtime.TsjRuntime.greaterThan(dev.tsj.runtime.TsjRuntime.invokeMember(inst_cell.get(), "getTimestamp"), Integer.valueOf(0))))));
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
