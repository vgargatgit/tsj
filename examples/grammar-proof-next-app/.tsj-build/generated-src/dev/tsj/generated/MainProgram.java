package dev.tsj.generated;

public final class MainProgram {
    private MainProgram() {
    }

    private static final dev.tsj.runtime.TsjCell PROMISE_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.promiseBuiltin());
    private static final Object __TSJ_ASYNC_BREAK_SIGNAL = new Object();
    private static final Object __TSJ_ASYNC_CONTINUE_SIGNAL = new Object();
    private static final java.util.Map<String, Object> __TSJ_TOP_LEVEL_CLASSES = new java.util.LinkedHashMap<>();
    private static boolean __TSJ_BOOTSTRAPPED = false;

    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_0 = new dev.tsj.runtime.TsjPropertyAccessCache("0");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_1 = new dev.tsj.runtime.TsjPropertyAccessCache("1");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_2 = new dev.tsj.runtime.TsjPropertyAccessCache("2");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_3 = new dev.tsj.runtime.TsjPropertyAccessCache("3");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_4 = new dev.tsj.runtime.TsjPropertyAccessCache("a");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_5 = new dev.tsj.runtime.TsjPropertyAccessCache("b");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_6 = new dev.tsj.runtime.TsjPropertyAccessCache("c");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_7 = new dev.tsj.runtime.TsjPropertyAccessCache("d");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_8 = new dev.tsj.runtime.TsjPropertyAccessCache("0");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_9 = new dev.tsj.runtime.TsjPropertyAccessCache("1");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_10 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_11 = new dev.tsj.runtime.TsjPropertyAccessCache("0");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_12 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_13 = new dev.tsj.runtime.TsjPropertyAccessCache("0");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_14 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_15 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_16 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_17 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_18 = new dev.tsj.runtime.TsjPropertyAccessCache("0");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_19 = new dev.tsj.runtime.TsjPropertyAccessCache("1");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_20 = new dev.tsj.runtime.TsjPropertyAccessCache("__tsj_private_seed");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_21 = new dev.tsj.runtime.TsjPropertyAccessCache("prototype");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_22 = new dev.tsj.runtime.TsjPropertyAccessCache("__tsj_private_seed");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_23 = new dev.tsj.runtime.TsjPropertyAccessCache("total");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_24 = new dev.tsj.runtime.TsjPropertyAccessCache("total");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_25 = new dev.tsj.runtime.TsjPropertyAccessCache("total");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_26 = new dev.tsj.runtime.TsjPropertyAccessCache("value");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_27 = new dev.tsj.runtime.TsjPropertyAccessCache("total");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_28 = new dev.tsj.runtime.TsjPropertyAccessCache("name");

    private static synchronized void __tsjBootstrap() {
        if (__TSJ_BOOTSTRAPPED) {
            return;
        }
        __TSJ_TOP_LEVEL_CLASSES.clear();
        final dev.tsj.runtime.TsjCell __tsj_init_module_0_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_1_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_2_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_3_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_export_0_divider_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_0_defaultTitle_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_1_base_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_2_runFeaturePack_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        __tsj_init_module_0_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis, Object... lambdaArgs) -> {
            final dev.tsj.runtime.TsjCell divider_cell = new dev.tsj.runtime.TsjCell(null);
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/helpers.ts	1	3
            divider_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 0 ? lambdaArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/helpers.ts	2	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("--- ", name_cell.get()), " ---");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/helpers.ts	5	3
            final dev.tsj.runtime.TsjCell defaultTitle_cell = new dev.tsj.runtime.TsjCell("grammar-proof-next");
            __tsj_export_0_divider_cell.set(divider_cell.get());
            __tsj_export_0_defaultTitle_cell.set(defaultTitle_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_0_cell.get());
        __tsj_init_module_1_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/types.ts	2	3
            final dev.tsj.runtime.TsjCell base_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(7));
            __tsj_export_1_base_cell.set(base_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_1_cell.get());
        __tsj_init_module_2_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_2, Object... lambdaArgs_2) -> {
            final dev.tsj.runtime.TsjCell renderSpreadLine_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjCell renderParamsLine_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjCell renderLoopLine_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjCell renderClassLine_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjCell renderTypesLine_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjCell runFeaturePack_cell = new dev.tsj.runtime.TsjCell(null);
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	1	3
            final dev.tsj.runtime.TsjCell divider_cell = new dev.tsj.runtime.TsjCell(__tsj_export_0_divider_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	1	3
            final dev.tsj.runtime.TsjCell defaultTitle_cell = new dev.tsj.runtime.TsjCell(__tsj_export_0_defaultTitle_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	8	3
            final dev.tsj.runtime.TsjCell base_cell = new dev.tsj.runtime.TsjCell(__tsj_export_1_base_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	12	3
            renderSpreadLine_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_3, Object... lambdaArgs_3) -> {
                final dev.tsj.runtime.TsjCell sum4_cell = new dev.tsj.runtime.TsjCell(null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	13	5
                sum4_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_4, Object... lambdaArgs_4) -> {
                    final dev.tsj.runtime.TsjCell a_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_4.length > 0 ? lambdaArgs_4[0] : dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell b_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_4.length > 1 ? lambdaArgs_4[1] : dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell c_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_4.length > 2 ? lambdaArgs_4[2] : dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell d_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_4.length > 3 ? lambdaArgs_4[3] : dev.tsj.runtime.TsjRuntime.undefined());
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	14	7
                    return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(a_cell.get(), b_cell.get()), c_cell.get()), d_cell.get());
                });
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	17	5
                final dev.tsj.runtime.TsjCell base_cell_1 = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arrayLiteral(Integer.valueOf(2), Integer.valueOf(3)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	18	5
                final dev.tsj.runtime.TsjCell combined_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arraySpread(dev.tsj.runtime.TsjRuntime.arrayLiteral(Integer.valueOf(1)), base_cell_1.get(), dev.tsj.runtime.TsjRuntime.arrayLiteral(Integer.valueOf(4))));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	19	11
                final dev.tsj.runtime.TsjCell destruct_1_cell = new dev.tsj.runtime.TsjCell(combined_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	19	11
                final dev.tsj.runtime.TsjCell w_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_0, destruct_1_cell.get(), "0"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	19	11
                final dev.tsj.runtime.TsjCell x_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_1, destruct_1_cell.get(), "1"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	19	11
                final dev.tsj.runtime.TsjCell y_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_2, destruct_1_cell.get(), "2"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	19	11
                final dev.tsj.runtime.TsjCell z_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_3, destruct_1_cell.get(), "3"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	21	5
                final dev.tsj.runtime.TsjCell left_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral("a", Integer.valueOf(1)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	22	5
                final dev.tsj.runtime.TsjCell right_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral("b", Integer.valueOf(2), "c", Integer.valueOf(3)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	23	5
                final dev.tsj.runtime.TsjCell merged_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectSpread(left_cell.get(), right_cell.get(), dev.tsj.runtime.TsjRuntime.objectLiteral("d", Integer.valueOf(4))));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	24	11
                final dev.tsj.runtime.TsjCell destruct_2_cell = new dev.tsj.runtime.TsjCell(merged_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	24	11
                final dev.tsj.runtime.TsjCell a_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_4, destruct_2_cell.get(), "a"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	24	11
                final dev.tsj.runtime.TsjCell b_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_5, destruct_2_cell.get(), "b"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	24	11
                final dev.tsj.runtime.TsjCell c_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_6, destruct_2_cell.get(), "c"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	24	11
                final dev.tsj.runtime.TsjCell d_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_7, destruct_2_cell.get(), "d"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	26	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", w_cell.get()), "|"), x_cell.get()), "|"), y_cell.get()), "|"), z_cell.get()), "|"), dev.tsj.runtime.TsjRuntime.callSpread(sum4_cell.get(), combined_cell.get())), "|"), a_cell.get()), "|"), b_cell.get()), "|"), c_cell.get()), "|"), d_cell.get()), "");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	29	3
            renderParamsLine_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_4, Object... lambdaArgs_4) -> {
                final dev.tsj.runtime.TsjCell summary_cell = new dev.tsj.runtime.TsjCell(null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	30	5
                summary_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_5, Object... lambdaArgs_5) -> {
                    final dev.tsj.runtime.TsjCell a_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_5.length > 0 ? lambdaArgs_5[0] : dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell b_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_5.length > 1 ? lambdaArgs_5[1] : dev.tsj.runtime.TsjRuntime.undefined());
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	30	22
                    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(a_cell.get(), dev.tsj.runtime.TsjRuntime.undefined())))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	30	22
                        a_cell.set(Integer.valueOf(10));
                    }
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	30	30
                    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(b_cell.get(), dev.tsj.runtime.TsjRuntime.undefined())))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	30	30
                        b_cell.set(dev.tsj.runtime.TsjRuntime.add(a_cell.get(), Integer.valueOf(1)));
                    }
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	30	41
                    final dev.tsj.runtime.TsjCell rest_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.restArgs(lambdaArgs_5, 2));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	31	13
                    final dev.tsj.runtime.TsjCell destruct_3_cell = new dev.tsj.runtime.TsjCell(rest_cell.get());
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	31	13
                    final dev.tsj.runtime.TsjCell first_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_8, destruct_3_cell.get(), "0"));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	31	13
                    final dev.tsj.runtime.TsjCell second_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_9, destruct_3_cell.get(), "1"));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	32	7
                    return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", a_cell.get()), "|"), b_cell.get()), "|"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_10, rest_cell.get(), "length")), "|"), dev.tsj.runtime.TsjRuntime.nullishCoalesce(first_cell.get(), () -> "none")), "|"), dev.tsj.runtime.TsjRuntime.nullishCoalesce(second_cell.get(), () -> "none")), "");
                });
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	35	5
                final dev.tsj.runtime.TsjCell combine_cell = new dev.tsj.runtime.TsjCell(((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_6) -> {
    final dev.tsj.runtime.TsjCell prefix_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_6.length > 0 ? lambdaArgs_6[0] : dev.tsj.runtime.TsjRuntime.undefined());
    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(prefix_cell.get(), dev.tsj.runtime.TsjRuntime.undefined())))) {
        prefix_cell.set("P");
    }
    final dev.tsj.runtime.TsjCell parts_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.restArgs(lambdaArgs_6, 1));
    final dev.tsj.runtime.TsjCell destruct_4_cell = new dev.tsj.runtime.TsjCell(parts_cell.get());
    final dev.tsj.runtime.TsjCell head_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_11, destruct_4_cell.get(), "0"));
    return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", prefix_cell.get()), "-"), dev.tsj.runtime.TsjRuntime.nullishCoalesce(head_cell.get(), () -> "none")), "-"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_12, parts_cell.get(), "length")), "");
}));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	40	5
                final dev.tsj.runtime.TsjCell Runner_cell = new dev.tsj.runtime.TsjCell(null);
                final dev.tsj.runtime.TsjClass Runner_class = new dev.tsj.runtime.TsjClass("Runner", null);
                Runner_cell.set(Runner_class);
                Runner_class.defineMethod("run", (dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
                    final dev.tsj.runtime.TsjCell seed_cell = new dev.tsj.runtime.TsjCell(methodArgs.length > 0 ? methodArgs[0] : dev.tsj.runtime.TsjRuntime.undefined());
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	41	11
                    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(seed_cell.get(), dev.tsj.runtime.TsjRuntime.undefined())))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	41	11
                        seed_cell.set(Integer.valueOf(1));
                    }
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	41	21
                    final dev.tsj.runtime.TsjCell tail_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.restArgs(methodArgs, 1));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	42	15
                    final dev.tsj.runtime.TsjCell destruct_5_cell = new dev.tsj.runtime.TsjCell(tail_cell.get());
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	42	15
                    final dev.tsj.runtime.TsjCell first_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_13, destruct_5_cell.get(), "0"));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	43	9
                    return dev.tsj.runtime.TsjRuntime.add(seed_cell.get(), dev.tsj.runtime.TsjRuntime.nullishCoalesce(first_cell.get(), () -> Integer.valueOf(0)));
                });
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	47	5
                final dev.tsj.runtime.TsjCell runner_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Runner_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	48	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", dev.tsj.runtime.TsjRuntime.call(summary_cell.get())), "|"), dev.tsj.runtime.TsjRuntime.call(summary_cell.get(), Integer.valueOf(3), dev.tsj.runtime.TsjRuntime.undefined(), Integer.valueOf(9))), "|"), dev.tsj.runtime.TsjRuntime.call(combine_cell.get(), dev.tsj.runtime.TsjRuntime.undefined(), "x", "y")), "|"), dev.tsj.runtime.TsjRuntime.invokeMember(runner_cell.get(), "run", dev.tsj.runtime.TsjRuntime.undefined(), Integer.valueOf(4))), "|"), dev.tsj.runtime.TsjRuntime.invokeMember(runner_cell.get(), "run", Integer.valueOf(2))), "");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	51	3
            renderLoopLine_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_5, Object... lambdaArgs_5) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	52	5
                final dev.tsj.runtime.TsjCell values_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arrayLiteral(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	53	5
                final dev.tsj.runtime.TsjCell sum_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	54	5
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.TRUE)) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	54	5
                    final dev.tsj.runtime.TsjCell forOfValues_6_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.forOfValues(values_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	54	5
                    final dev.tsj.runtime.TsjCell forIndex_7_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	54	5
                    while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(forIndex_7_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_14, forOfValues_6_cell.get(), "length"))))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	54	5
                        final dev.tsj.runtime.TsjCell value_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.indexRead(forOfValues_6_cell.get(), forIndex_7_cell.get()));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	55	7
                        if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(value_cell.get(), Integer.valueOf(2))))) {
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	54	5
                            forIndex_7_cell.set(dev.tsj.runtime.TsjRuntime.add(forIndex_7_cell.get(), Integer.valueOf(1)));
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	56	9
                            continue;
                        }
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	58	7
                        dev.tsj.runtime.TsjRuntime.assignCell(sum_cell, dev.tsj.runtime.TsjRuntime.add(sum_cell.get(), value_cell.get()));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	54	5
                        forIndex_7_cell.set(dev.tsj.runtime.TsjRuntime.add(forIndex_7_cell.get(), Integer.valueOf(1)));
                    }
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	61	5
                final dev.tsj.runtime.TsjCell obj_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral("a", Integer.valueOf(1), "b", Integer.valueOf(2)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	62	5
                final dev.tsj.runtime.TsjCell keys_cell = new dev.tsj.runtime.TsjCell("");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	63	5
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.TRUE)) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	63	5
                    final dev.tsj.runtime.TsjCell forInKeys_8_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.forInKeys(obj_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	63	5
                    final dev.tsj.runtime.TsjCell forIndex_9_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	63	5
                    while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(forIndex_9_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_15, forInKeys_8_cell.get(), "length"))))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	63	5
                        final dev.tsj.runtime.TsjCell key_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.indexRead(forInKeys_8_cell.get(), forIndex_9_cell.get()));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	64	7
                        keys_cell.set(dev.tsj.runtime.TsjRuntime.add(keys_cell.get(), key_cell.get()));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	63	5
                        forIndex_9_cell.set(dev.tsj.runtime.TsjRuntime.add(forIndex_9_cell.get(), Integer.valueOf(1)));
                    }
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	67	5
                final dev.tsj.runtime.TsjCell last_cell = new dev.tsj.runtime.TsjCell("");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	68	5
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.TRUE)) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	68	5
                    final dev.tsj.runtime.TsjCell forInKeys_10_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.forInKeys(obj_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	68	5
                    final dev.tsj.runtime.TsjCell forIndex_11_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	68	5
                    while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(forIndex_11_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_16, forInKeys_10_cell.get(), "length"))))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	68	5
                        last_cell.set(dev.tsj.runtime.TsjRuntime.indexRead(forInKeys_10_cell.get(), forIndex_11_cell.get()));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	68	5
                        forIndex_11_cell.set(dev.tsj.runtime.TsjRuntime.add(forIndex_11_cell.get(), Integer.valueOf(1)));
                    }
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	71	5
                final dev.tsj.runtime.TsjCell pairTotal_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	72	5
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.TRUE)) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	72	5
                    final dev.tsj.runtime.TsjCell forOfValues_12_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.forOfValues(dev.tsj.runtime.TsjRuntime.arrayLiteral(dev.tsj.runtime.TsjRuntime.arrayLiteral(Integer.valueOf(1), Integer.valueOf(2)), dev.tsj.runtime.TsjRuntime.arrayLiteral(Integer.valueOf(3), Integer.valueOf(4)))));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	72	5
                    final dev.tsj.runtime.TsjCell forIndex_13_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	72	5
                    while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(forIndex_13_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_17, forOfValues_12_cell.get(), "length"))))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	72	5
                        final dev.tsj.runtime.TsjCell left_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_18, dev.tsj.runtime.TsjRuntime.indexRead(forOfValues_12_cell.get(), forIndex_13_cell.get()), "0"));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	72	5
                        final dev.tsj.runtime.TsjCell right_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_19, dev.tsj.runtime.TsjRuntime.indexRead(forOfValues_12_cell.get(), forIndex_13_cell.get()), "1"));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	73	7
                        dev.tsj.runtime.TsjRuntime.assignCell(pairTotal_cell, dev.tsj.runtime.TsjRuntime.add(pairTotal_cell.get(), dev.tsj.runtime.TsjRuntime.add(left_cell.get(), right_cell.get())));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	72	5
                        forIndex_13_cell.set(dev.tsj.runtime.TsjRuntime.add(forIndex_13_cell.get(), Integer.valueOf(1)));
                    }
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	76	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", sum_cell.get()), "|"), keys_cell.get()), "|"), last_cell.get()), "|"), pairTotal_cell.get()), "");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	79	3
            renderClassLine_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_6, Object... lambdaArgs_6) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	80	5
                final dev.tsj.runtime.TsjCell keyPart_cell = new dev.tsj.runtime.TsjCell("sum");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	82	5
                final dev.tsj.runtime.TsjCell Counter_cell = new dev.tsj.runtime.TsjCell(null);
                final dev.tsj.runtime.TsjClass Counter_class = new dev.tsj.runtime.TsjClass("Counter", null);
                Counter_cell.set(Counter_class);
                Counter_class.setConstructor((dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	83	7
                    dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "__tsj_private_seed", Integer.valueOf(2));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	84	7
                    dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "value", dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_20, thisObject, "__tsj_private_seed"));
                    return null;
                });
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	86	7
                dev.tsj.runtime.TsjRuntime.setPropertyDynamic(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_21, Counter_cell.get(), "prototype"), dev.tsj.runtime.TsjRuntime.add(keyPart_cell.get(), "With"), ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_7, Object... lambdaArgs_7) -> {
    final dev.tsj.runtime.TsjCell delta_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_7.length > 0 ? lambdaArgs_7[0] : dev.tsj.runtime.TsjRuntime.undefined());
    return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_22, lambdaThis_7, "__tsj_private_seed"), delta_cell.get());
}));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	90	7
                dev.tsj.runtime.TsjRuntime.setProperty(Counter_cell.get(), "total", Integer.valueOf(1));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	93	9
                dev.tsj.runtime.TsjRuntime.setProperty(Counter_cell.get(), "total", dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_23, Counter_cell.get(), "total"), Integer.valueOf(2)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	96	7
                dev.tsj.runtime.TsjRuntime.setProperty(Counter_cell.get(), "bump", ((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_8, Object... lambdaArgs_8) -> {
    final dev.tsj.runtime.TsjCell step_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_8.length > 0 ? lambdaArgs_8[0] : dev.tsj.runtime.TsjRuntime.undefined());
    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(step_cell.get(), dev.tsj.runtime.TsjRuntime.undefined())))) {
        step_cell.set(Integer.valueOf(1));
    }
    dev.tsj.runtime.TsjRuntime.setProperty(Counter_cell.get(), "total", dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_24, Counter_cell.get(), "total"), step_cell.get()));
    return dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_25, Counter_cell.get(), "total");
}));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	102	5
                final dev.tsj.runtime.TsjCell counter_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.construct(Counter_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	103	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_26, counter_cell.get(), "value")), "|"), dev.tsj.runtime.TsjRuntime.invokeMember(counter_cell.get(), "sumWith", Integer.valueOf(3))), "|"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_27, Counter_cell.get(), "total")), "|"), dev.tsj.runtime.TsjRuntime.invokeMember(Counter_cell.get(), "bump")), "");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	106	3
            renderTypesLine_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_7, Object... lambdaArgs_7) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	107	5
                final dev.tsj.runtime.TsjCell checked_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral("name", "ok"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	108	5
                final dev.tsj.runtime.TsjCell person_cell = new dev.tsj.runtime.TsjCell(checked_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	109	5
                final dev.tsj.runtime.TsjCell total_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.add(base_cell.get(), base_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	110	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("", total_cell.get()), "|"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_28, person_cell.get(), "name")), "");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	113	3
            runFeaturePack_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_8, Object... lambdaArgs_8) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	114	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.call(divider_cell.get(), defaultTitle_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	115	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("spread:", dev.tsj.runtime.TsjRuntime.call(renderSpreadLine_cell.get())), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	116	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("params:", dev.tsj.runtime.TsjRuntime.call(renderParamsLine_cell.get())), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	117	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("loops:", dev.tsj.runtime.TsjRuntime.call(renderLoopLine_cell.get())), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	118	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("class:", dev.tsj.runtime.TsjRuntime.call(renderClassLine_cell.get())), ""));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/feature-pack.ts	119	5
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("ts-only:", dev.tsj.runtime.TsjRuntime.call(renderTypesLine_cell.get())), ""));
                return null;
            });
            __tsj_export_2_runFeaturePack_cell.set(runFeaturePack_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_2_cell.get());
        __tsj_init_module_3_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_3, Object... lambdaArgs_3) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/main.ts	1	3
            final dev.tsj.runtime.TsjCell run_cell = new dev.tsj.runtime.TsjCell(__tsj_export_2_runFeaturePack_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/grammar-proof-next-app/src/main.ts	5	3
            dev.tsj.runtime.TsjRuntime.call(run_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_3_cell.get());
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
