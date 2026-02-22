package dev.tsj.generated;

public final class MainProgram {
    private MainProgram() {
    }

    private static final dev.tsj.runtime.TsjCell PROMISE_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.promiseBuiltin());
    private static final Object __TSJ_ASYNC_BREAK_SIGNAL = new Object();
    private static final Object __TSJ_ASYNC_CONTINUE_SIGNAL = new Object();
    private static final java.util.Map<String, Object> __TSJ_TOP_LEVEL_CLASSES = new java.util.LinkedHashMap<>();
    private static boolean __TSJ_BOOTSTRAPPED = false;

    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_0 = new dev.tsj.runtime.TsjPropertyAccessCache("read");

    private static synchronized void __tsjBootstrap() {
        if (__TSJ_BOOTSTRAPPED) {
            return;
        }
        __TSJ_TOP_LEVEL_CLASSES.clear();
        final dev.tsj.runtime.TsjCell __tsj_init_module_0_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_1_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_2_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_export_0_divider_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_0_defaultTitle_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_1_runFeaturePack_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        __tsj_init_module_0_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis, Object... lambdaArgs) -> {
            final dev.tsj.runtime.TsjCell divider_cell = new dev.tsj.runtime.TsjCell(null);
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/helpers.ts	1	3
            divider_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 0 ? lambdaArgs_1[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/helpers.ts	2	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("--- ", name_cell.get()), " ---");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/helpers.ts	5	3
            final dev.tsj.runtime.TsjCell defaultTitle_cell = new dev.tsj.runtime.TsjCell("grammar-proof");
            __tsj_export_0_divider_cell.set(divider_cell.get());
            __tsj_export_0_defaultTitle_cell.set(defaultTitle_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_0_cell.get());
        __tsj_init_module_1_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
            final dev.tsj.runtime.TsjCell runFeaturePack_cell = new dev.tsj.runtime.TsjCell(null);
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	1	3
            final dev.tsj.runtime.TsjCell divider_cell = new dev.tsj.runtime.TsjCell(__tsj_export_0_divider_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	1	3
            final dev.tsj.runtime.TsjCell defaultTitle_cell = new dev.tsj.runtime.TsjCell(__tsj_export_0_defaultTitle_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	6	3
            runFeaturePack_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_2, Object... lambdaArgs_2) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	7	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.call(divider_cell.get(), defaultTitle_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	9	5
                final dev.tsj.runtime.TsjCell leftFalse_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.logicalAnd(Boolean.FALSE, () -> "rhs-and"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	12	5
                final dev.tsj.runtime.TsjCell leftTrue_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.logicalOr(Boolean.TRUE, () -> "rhs-or"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	14	5
                final dev.tsj.runtime.TsjCell coalesced_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.nullishCoalesce(null, () -> "fallback"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	18	8
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("logical:", leftFalse_cell.get()), "|"), leftTrue_cell.get()), "|"), coalesced_cell.get()), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	20	5
                final dev.tsj.runtime.TsjCell score_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(10));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	21	5
                final dev.tsj.runtime.TsjCell captured_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.assignCell(score_cell, Integer.valueOf(7)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	22	5
                dev.tsj.runtime.TsjRuntime.assignCell(score_cell, dev.tsj.runtime.TsjRuntime.add(score_cell.get(), Integer.valueOf(5)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	24	5
                dev.tsj.runtime.TsjRuntime.assignCell(score_cell, dev.tsj.runtime.TsjRuntime.subtract(score_cell.get(), Integer.valueOf(2)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	25	5
                dev.tsj.runtime.TsjRuntime.assignCell(score_cell, dev.tsj.runtime.TsjRuntime.multiply(score_cell.get(), Integer.valueOf(3)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	26	5
                dev.tsj.runtime.TsjRuntime.assignCell(score_cell, dev.tsj.runtime.TsjRuntime.divide(score_cell.get(), Integer.valueOf(2)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	27	5
                dev.tsj.runtime.TsjRuntime.assignCell(score_cell, dev.tsj.runtime.TsjRuntime.modulo(score_cell.get(), Integer.valueOf(5)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	29	5
                final dev.tsj.runtime.TsjCell maybe_cell = new dev.tsj.runtime.TsjCell(null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	30	5
                dev.tsj.runtime.TsjRuntime.assignNullish(maybe_cell, () -> "filled");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	31	5
                final dev.tsj.runtime.TsjCell orValue_cell = new dev.tsj.runtime.TsjCell(Boolean.FALSE);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	32	5
                dev.tsj.runtime.TsjRuntime.assignLogicalOr(orValue_cell, () -> "alt");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	33	5
                final dev.tsj.runtime.TsjCell andValue_cell = new dev.tsj.runtime.TsjCell("seed");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	34	5
                dev.tsj.runtime.TsjRuntime.assignLogicalAnd(andValue_cell, () -> "next");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	35	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("assign:", captured_cell.get()), "|"), score_cell.get()), "|"), maybe_cell.get()), "|"), orValue_cell.get()), "|"), andValue_cell.get()), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	37	5
                final dev.tsj.runtime.TsjCell picked_cell = new dev.tsj.runtime.TsjCell((dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.greaterThan(score_cell.get(), Integer.valueOf(2)))) ? "GT2" : "LE2"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	38	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("conditional:", picked_cell.get()), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	40	5
                final dev.tsj.runtime.TsjCell holder_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral("value", Integer.valueOf(4), "read", ((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_3) -> {
    return "ok";
})));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	44	5
                final dev.tsj.runtime.TsjCell none_cell = new dev.tsj.runtime.TsjCell(null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	45	5
                final dev.tsj.runtime.TsjCell maybeFn_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	46	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("optional:", dev.tsj.runtime.TsjRuntime.optionalMemberAccess(holder_cell.get(), "value")), "|"), dev.tsj.runtime.TsjRuntime.optionalMemberAccess(none_cell.get(), "value")), "|"), dev.tsj.runtime.TsjRuntime.optionalCall(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_0, holder_cell.get(), "read"), () -> new Object[0])), "|"), dev.tsj.runtime.TsjRuntime.optionalCall(maybeFn_cell.get(), () -> new Object[0])), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	48	5
                final dev.tsj.runtime.TsjCell who_cell = new dev.tsj.runtime.TsjCell("tsj");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	49	5
                final dev.tsj.runtime.TsjCell count_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(3));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	50	5
                final dev.tsj.runtime.TsjCell templated_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("hello ", who_cell.get()), " #"), count_cell.get()), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/feature-pack.ts	51	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("template:", templated_cell.get()), ""));
                return null;
            });
            __tsj_export_1_runFeaturePack_cell.set(runFeaturePack_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_1_cell.get());
        __tsj_init_module_2_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_2, Object... lambdaArgs_2) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/main.ts	1	3
            final dev.tsj.runtime.TsjCell run_cell = new dev.tsj.runtime.TsjCell(__tsj_export_1_runFeaturePack_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-app/src/main.ts	5	3
            dev.tsj.runtime.TsjRuntime.call(run_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_2_cell.get());
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
