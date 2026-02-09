package dev.tsj.generated;

public final class MainProgram {
    private MainProgram() {
    }

    private static final dev.tsj.runtime.TsjCell PROMISE_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.promiseBuiltin());

    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_0 = new dev.tsj.runtime.TsjPropertyAccessCache("value");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_1 = new dev.tsj.runtime.TsjPropertyAccessCache("value");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_2 = new dev.tsj.runtime.TsjPropertyAccessCache("name");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_3 = new dev.tsj.runtime.TsjPropertyAccessCache("id");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_4 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_5 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_6 = new dev.tsj.runtime.TsjPropertyAccessCache("count");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_7 = new dev.tsj.runtime.TsjPropertyAccessCache("count");

    public static void main(String[] args) {
        final dev.tsj.runtime.TsjCell sumTo_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell makeAdder_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell double_ts_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell buildUser_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell computeSeries_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell runPromiseLab_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell moduleReady_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell awaitExpr_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell plusOne_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell asyncOps_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell adder_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell total_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell boosted_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell account_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell payload_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell user_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell missing_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell awaitExpr_1_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell boostedAsync_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell awaitExpr_2_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell series_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell awaitExpr_3_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell promiseLab_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.undefined()), (dev.tsj.runtime.TsjCallable) (Object... topLevelArgs) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/bootstrap.ts	1	1
            dev.tsj.runtime.TsjRuntime.print("bootstrap:init");
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	1	1
            sumTo_cell.set((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs) -> {
                final dev.tsj.runtime.TsjCell limit_cell = new dev.tsj.runtime.TsjCell(lambdaArgs.length > 0 ? lambdaArgs[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	2	3
                final dev.tsj.runtime.TsjCell i_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(1));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	3	3
                final dev.tsj.runtime.TsjCell total_cell_1 = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	4	3
                while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThanOrEqual(i_cell.get(), limit_cell.get())))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	5	5
                    total_cell_1.set(dev.tsj.runtime.TsjRuntime.add(total_cell_1.get(), i_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	6	5
                    i_cell.set(dev.tsj.runtime.TsjRuntime.add(i_cell.get(), Integer.valueOf(1)));
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	8	3
                return total_cell_1.get();
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	11	1
            makeAdder_cell.set((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_1) -> {
                final dev.tsj.runtime.TsjCell base_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_1.length > 0 ? lambdaArgs_1[0] : null);
                final dev.tsj.runtime.TsjCell add_cell = new dev.tsj.runtime.TsjCell(null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	12	3
                add_cell.set((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_2) -> {
                    final dev.tsj.runtime.TsjCell value_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_2.length > 0 ? lambdaArgs_2[0] : null);
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	13	5
                    return dev.tsj.runtime.TsjRuntime.add(value_cell.get(), base_cell.get());
                });
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	15	3
                return add_cell.get();
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	18	1
            double_ts_cell.set((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_2) -> {
                final dev.tsj.runtime.TsjCell value_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_2.length > 0 ? lambdaArgs_2[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/math.ts	19	3
                return dev.tsj.runtime.TsjRuntime.multiply(value_cell.get(), Integer.valueOf(2));
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	1	1
            final dev.tsj.runtime.TsjCell Account_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjClass Account_class = new dev.tsj.runtime.TsjClass("Account", null);
            Account_cell.set(Account_class);
            Account_class.setConstructor((dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
                final dev.tsj.runtime.TsjCell seed_cell = new dev.tsj.runtime.TsjCell(methodArgs.length > 0 ? methodArgs[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	5	5
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "value", seed_cell.get());
                return null;
            });
            Account_class.defineMethod("read", (dev.tsj.runtime.TsjObject thisObject_1, Object... methodArgs_1) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	9	5
                return dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_0, thisObject_1, "value");
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	13	1
            final dev.tsj.runtime.TsjCell PremiumAccount_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjClass PremiumAccount_class = new dev.tsj.runtime.TsjClass("PremiumAccount", dev.tsj.runtime.TsjRuntime.asClass(Account_cell.get()));
            PremiumAccount_cell.set(PremiumAccount_class);
            PremiumAccount_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_2, Object... methodArgs_2) -> {
                final dev.tsj.runtime.TsjCell seed_cell = new dev.tsj.runtime.TsjCell(methodArgs_2.length > 0 ? methodArgs_2[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	15	5
                dev.tsj.runtime.TsjRuntime.asClass(Account_cell.get()).invokeConstructor(thisObject_2, dev.tsj.runtime.TsjRuntime.add(seed_cell.get(), Integer.valueOf(2)));
                return null;
            });
            PremiumAccount_class.defineMethod("bonus", (dev.tsj.runtime.TsjObject thisObject_3, Object... methodArgs_3) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	19	5
                return dev.tsj.runtime.TsjRuntime.multiply(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_1, thisObject_3, "value"), Integer.valueOf(2));
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	23	1
            final dev.tsj.runtime.TsjCell User_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjClass User_class = new dev.tsj.runtime.TsjClass("User", null);
            User_cell.set(User_class);
            User_class.setConstructor((dev.tsj.runtime.TsjObject thisObject_4, Object... methodArgs_4) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 0 ? methodArgs_4[0] : null);
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 1 ? methodArgs_4[1] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	28	5
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject_4, "name", name_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	29	5
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject_4, "id", id_cell.get());
                return null;
            });
            User_class.defineMethod("tag", (dev.tsj.runtime.TsjObject thisObject_5, Object... methodArgs_5) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	33	5
                return dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_2, thisObject_5, "name"), "#"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_3, thisObject_5, "id"));
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	37	1
            buildUser_cell.set((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_3) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_3.length > 0 ? lambdaArgs_3[0] : null);
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_3.length > 1 ? lambdaArgs_3[1] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/users.ts	38	3
                return dev.tsj.runtime.TsjRuntime.construct(User_cell.get(), name_cell.get(), id_cell.get());
            });
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	1	1
            moduleReady_cell.set("boot");
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	2	1
            return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", "ready")), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs) -> {
                awaitExpr_cell.set(awaitArgs.length > 0 ? awaitArgs[0] : dev.tsj.runtime.TsjRuntime.undefined());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	2	1
                moduleReady_cell.set(awaitExpr_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	3	1
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("module:init=", moduleReady_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	5	1
                plusOne_cell.set(((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_4) -> {
    final dev.tsj.runtime.TsjCell value_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_4.length > 0 ? lambdaArgs_4[0] : null);
    final dev.tsj.runtime.TsjCell awaitExpr_4_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
    final dev.tsj.runtime.TsjCell next_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
    try {
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	6	3
        return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", dev.tsj.runtime.TsjRuntime.add(value_cell.get(), Integer.valueOf(1)))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_1) -> {
            awaitExpr_4_cell.set(awaitArgs_1.length > 0 ? awaitArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	6	3
            next_cell.set(awaitExpr_4_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	7	3
            return dev.tsj.runtime.TsjRuntime.promiseResolve(next_cell.get());
        }, dev.tsj.runtime.TsjRuntime.undefined());
    } catch (RuntimeException __tsjAsyncError) {
        return dev.tsj.runtime.TsjRuntime.promiseReject(dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));
    }
}));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	10	1
                asyncOps_cell.set(dev.tsj.runtime.TsjRuntime.objectLiteral("boost", ((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_5) -> {
    final dev.tsj.runtime.TsjCell value_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_5.length > 0 ? lambdaArgs_5[0] : null);
    final dev.tsj.runtime.TsjCell awaitExpr_4_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
    try {
        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	12	5
        return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.call(plusOne_cell.get(), dev.tsj.runtime.TsjRuntime.add(value_cell.get(), Integer.valueOf(1)))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_1) -> {
            awaitExpr_4_cell.set(awaitArgs_1.length > 0 ? awaitArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	12	5
            return dev.tsj.runtime.TsjRuntime.promiseResolve(awaitExpr_4_cell.get());
        }, dev.tsj.runtime.TsjRuntime.undefined());
    } catch (RuntimeException __tsjAsyncError) {
        return dev.tsj.runtime.TsjRuntime.promiseReject(dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));
    }
})));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	16	1
                computeSeries_cell.set((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_6) -> {
                    final dev.tsj.runtime.TsjCell seed_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_6.length > 0 ? lambdaArgs_6[0] : null);
                    final dev.tsj.runtime.TsjCell i_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell total_cell_1 = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    try {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	17	3
                        i_cell.set(Integer.valueOf(0));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	18	3
                        total_cell_1.set(seed_cell.get());
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	19	3
                        final dev.tsj.runtime.TsjCell asyncLoopCell = new dev.tsj.runtime.TsjCell(null);
                        asyncLoopCell.set((dev.tsj.runtime.TsjCallable) (Object... asyncLoopArgs) -> {
                            if (!dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(i_cell.get(), Integer.valueOf(2))))) {
                                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	27	3
                                return dev.tsj.runtime.TsjRuntime.promiseResolve(total_cell_1.get());
                            }
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	20	5
                            if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.abstractEquals(i_cell.get(), Integer.valueOf(0))))) {
                                final dev.tsj.runtime.TsjCell awaitExpr_4_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	21	7
                                return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", Integer.valueOf(3))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_1) -> {
                                    awaitExpr_4_cell.set(awaitArgs_1.length > 0 ? awaitArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
                                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	21	7
                                    total_cell_1.set(dev.tsj.runtime.TsjRuntime.add(total_cell_1.get(), awaitExpr_4_cell.get()));
                                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	25	5
                                    i_cell.set(dev.tsj.runtime.TsjRuntime.add(i_cell.get(), Integer.valueOf(1)));
                                    return dev.tsj.runtime.TsjRuntime.call(asyncLoopCell.get());
                                }, dev.tsj.runtime.TsjRuntime.undefined());
                            } else {
                                final dev.tsj.runtime.TsjCell awaitExpr_4_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	23	7
                                return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", Integer.valueOf(4))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_1) -> {
                                    awaitExpr_4_cell.set(awaitArgs_1.length > 0 ? awaitArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
                                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	23	7
                                    total_cell_1.set(dev.tsj.runtime.TsjRuntime.add(total_cell_1.get(), awaitExpr_4_cell.get()));
                                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/async-work.ts	25	5
                                    i_cell.set(dev.tsj.runtime.TsjRuntime.add(i_cell.get(), Integer.valueOf(1)));
                                    return dev.tsj.runtime.TsjRuntime.call(asyncLoopCell.get());
                                }, dev.tsj.runtime.TsjRuntime.undefined());
                            }
                        });
                        return dev.tsj.runtime.TsjRuntime.call(asyncLoopCell.get());
                    } catch (RuntimeException __tsjAsyncError) {
                        return dev.tsj.runtime.TsjRuntime.promiseReject(dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));
                    }
                });
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	1	1
                runPromiseLab_cell.set((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_7) -> {
                    final dev.tsj.runtime.TsjCell base_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_7.length > 0 ? lambdaArgs_7[0] : null);
                    final dev.tsj.runtime.TsjCell awaitExpr_4_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell recovered_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell awaitExpr_5_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell allValues_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell awaitExpr_6_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell raceValue_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell awaitExpr_7_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell settledValues_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell awaitExpr_8_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    final dev.tsj.runtime.TsjCell anyValue_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
                    try {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	2	3
                        return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "reject", "boom"), "catch", ((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_9) -> {
    final dev.tsj.runtime.TsjCell reason_cell = new dev.tsj.runtime.TsjCell(lambdaArgs_9.length > 0 ? lambdaArgs_9[0] : null);
    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	4	7
    dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("promise:catch=", reason_cell.get()));
    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	5	7
    return base_cell.get();
})), "finally", ((dev.tsj.runtime.TsjCallable) (Object... lambdaArgs_8) -> {
    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	8	7
    dev.tsj.runtime.TsjRuntime.print("promise:finally");
    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	9	7
    return Integer.valueOf(99);
}))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_1) -> {
                            awaitExpr_4_cell.set(awaitArgs_1.length > 0 ? awaitArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	2	3
                            recovered_cell.set(awaitExpr_4_cell.get());
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	12	3
                            return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "all", dev.tsj.runtime.TsjRuntime.arrayLiteral(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", recovered_cell.get()), dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", dev.tsj.runtime.TsjRuntime.add(recovered_cell.get(), Integer.valueOf(1))), dev.tsj.runtime.TsjRuntime.add(recovered_cell.get(), Integer.valueOf(2))))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_2) -> {
                                awaitExpr_5_cell.set(awaitArgs_2.length > 0 ? awaitArgs_2[0] : dev.tsj.runtime.TsjRuntime.undefined());
                                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	12	3
                                allValues_cell.set(awaitExpr_5_cell.get());
                                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	17	3
                                return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "race", dev.tsj.runtime.TsjRuntime.arrayLiteral(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", "win"), dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "reject", "lose")))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_3) -> {
                                    awaitExpr_6_cell.set(awaitArgs_3.length > 0 ? awaitArgs_3[0] : dev.tsj.runtime.TsjRuntime.undefined());
                                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	17	3
                                    raceValue_cell.set(awaitExpr_6_cell.get());
                                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	18	3
                                    return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "allSettled", dev.tsj.runtime.TsjRuntime.arrayLiteral(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", Integer.valueOf(1)), dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "reject", "x")))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_4) -> {
                                        awaitExpr_7_cell.set(awaitArgs_4.length > 0 ? awaitArgs_4[0] : dev.tsj.runtime.TsjRuntime.undefined());
                                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	18	3
                                        settledValues_cell.set(awaitExpr_7_cell.get());
                                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	19	3
                                        return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "any", dev.tsj.runtime.TsjRuntime.arrayLiteral(dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "reject", "bad"), dev.tsj.runtime.TsjRuntime.invokeMember(PROMISE_BUILTIN_CELL.get(), "resolve", "ok")))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_5) -> {
                                            awaitExpr_8_cell.set(awaitArgs_5.length > 0 ? awaitArgs_5[0] : dev.tsj.runtime.TsjRuntime.undefined());
                                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	19	3
                                            anyValue_cell.set(awaitExpr_8_cell.get());
                                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/promise-lab.ts	21	3
                                            return dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("lab=", dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_4, allValues_cell.get(), "length")), ":"), raceValue_cell.get()), ":"), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_5, settledValues_cell.get(), "length")), ":"), anyValue_cell.get()));
                                        }, dev.tsj.runtime.TsjRuntime.undefined());
                                    }, dev.tsj.runtime.TsjRuntime.undefined());
                                }, dev.tsj.runtime.TsjRuntime.undefined());
                            }, dev.tsj.runtime.TsjRuntime.undefined());
                        }, dev.tsj.runtime.TsjRuntime.undefined());
                    } catch (RuntimeException __tsjAsyncError) {
                        return dev.tsj.runtime.TsjRuntime.promiseReject(dev.tsj.runtime.TsjRuntime.normalizeThrown(__tsjAsyncError));
                    }
                });
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	7	1
                adder_cell.set(dev.tsj.runtime.TsjRuntime.call(makeAdder_cell.get(), Integer.valueOf(5)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	8	1
                total_cell.set(dev.tsj.runtime.TsjRuntime.call(sumTo_cell.get(), Integer.valueOf(5)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	9	1
                boosted_cell.set(dev.tsj.runtime.TsjRuntime.call(adder_cell.get(), Integer.valueOf(10)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	10	1
                account_cell.set(dev.tsj.runtime.TsjRuntime.construct(PremiumAccount_cell.get(), Integer.valueOf(4)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	11	1
                payload_cell.set(dev.tsj.runtime.TsjRuntime.objectLiteral("label", "ok", "count", Integer.valueOf(2)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	12	1
                dev.tsj.runtime.TsjRuntime.setProperty(payload_cell.get(), "count", dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_6, payload_cell.get(), "count"), Integer.valueOf(1)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	14	1
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("sync:ready=", moduleReady_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	15	1
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("sync:total=", total_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	16	1
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("sync:boosted=", boosted_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	17	1
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.add("sync:account=", dev.tsj.runtime.TsjRuntime.invokeMember(account_cell.get(), "read")), ":"), dev.tsj.runtime.TsjRuntime.invokeMember(account_cell.get(), "bonus")));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	19	1
                user_cell.set(dev.tsj.runtime.TsjRuntime.call(buildUser_cell.get(), "ada", dev.tsj.runtime.TsjRuntime.call(double_ts_cell.get(), boosted_cell.get())));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	20	1
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("sync:user=", dev.tsj.runtime.TsjRuntime.invokeMember(user_cell.get(), "tag")));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	22	1
                dev.tsj.runtime.TsjRuntime.print("sync:coerce=true:false");
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	23	1
                missing_cell.set(dev.tsj.runtime.TsjRuntime.objectLiteral("label", "x"));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	24	1
                dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("sync:missing=", dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_7, missing_cell.get(), "count")));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	26	1
                return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.invokeMember(asyncOps_cell.get(), "boost", Integer.valueOf(10))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_1) -> {
                    awaitExpr_1_cell.set(awaitArgs_1.length > 0 ? awaitArgs_1[0] : dev.tsj.runtime.TsjRuntime.undefined());
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	26	1
                    boostedAsync_cell.set(awaitExpr_1_cell.get());
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	27	1
                    dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("async:boost=", boostedAsync_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	29	1
                    return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.call(computeSeries_cell.get(), Integer.valueOf(5))), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_2) -> {
                        awaitExpr_2_cell.set(awaitArgs_2.length > 0 ? awaitArgs_2[0] : dev.tsj.runtime.TsjRuntime.undefined());
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	29	1
                        series_cell.set(awaitExpr_2_cell.get());
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	30	1
                        dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("async:series=", series_cell.get()));
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	32	1
                        return dev.tsj.runtime.TsjRuntime.promiseThen(dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.call(runPromiseLab_cell.get(), series_cell.get())), (dev.tsj.runtime.TsjCallable) (Object... awaitArgs_3) -> {
                            awaitExpr_3_cell.set(awaitArgs_3.length > 0 ? awaitArgs_3[0] : dev.tsj.runtime.TsjRuntime.undefined());
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	32	1
                            promiseLab_cell.set(awaitExpr_3_cell.get());
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	33	1
                            dev.tsj.runtime.TsjRuntime.print(dev.tsj.runtime.TsjRuntime.add("async:", promiseLab_cell.get()));
                            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/multi-file-demo/src/main.ts	35	1
                            dev.tsj.runtime.TsjRuntime.print("sync:done");
                            return dev.tsj.runtime.TsjRuntime.promiseResolve(dev.tsj.runtime.TsjRuntime.undefined());
                        }, dev.tsj.runtime.TsjRuntime.undefined());
                    }, dev.tsj.runtime.TsjRuntime.undefined());
                }, dev.tsj.runtime.TsjRuntime.undefined());
            }, dev.tsj.runtime.TsjRuntime.undefined());
        }, dev.tsj.runtime.TsjRuntime.undefined());
        dev.tsj.runtime.TsjRuntime.flushMicrotasks();
    }
}
