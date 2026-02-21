package dev.tsj.generated;

public final class MainProgram {
    private MainProgram() {
    }

    private static final dev.tsj.runtime.TsjCell PROMISE_BUILTIN_CELL = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.promiseBuiltin());
    private static final Object __TSJ_ASYNC_BREAK_SIGNAL = new Object();
    private static final Object __TSJ_ASYNC_CONTINUE_SIGNAL = new Object();
    private static final java.util.Map<String, Object> __TSJ_TOP_LEVEL_CLASSES = new java.util.LinkedHashMap<>();
    private static boolean __TSJ_BOOTSTRAPPED = false;

    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_0 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_1 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_2 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_3 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_4 = new dev.tsj.runtime.TsjPropertyAccessCache("id");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_5 = new dev.tsj.runtime.TsjPropertyAccessCache("nextId");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_6 = new dev.tsj.runtime.TsjPropertyAccessCache("nextId");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_7 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_8 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_9 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_10 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_11 = new dev.tsj.runtime.TsjPropertyAccessCache("id");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_12 = new dev.tsj.runtime.TsjPropertyAccessCache("length");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_13 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_14 = new dev.tsj.runtime.TsjPropertyAccessCache("pets");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_15 = new dev.tsj.runtime.TsjPropertyAccessCache("id");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_16 = new dev.tsj.runtime.TsjPropertyAccessCache("repository");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_17 = new dev.tsj.runtime.TsjPropertyAccessCache("repository");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_18 = new dev.tsj.runtime.TsjPropertyAccessCache("repository");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_19 = new dev.tsj.runtime.TsjPropertyAccessCache("repository");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_20 = new dev.tsj.runtime.TsjPropertyAccessCache("repository");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_21 = new dev.tsj.runtime.TsjPropertyAccessCache("service");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_22 = new dev.tsj.runtime.TsjPropertyAccessCache("service");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_23 = new dev.tsj.runtime.TsjPropertyAccessCache("service");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_24 = new dev.tsj.runtime.TsjPropertyAccessCache("service");
    private static final dev.tsj.runtime.TsjPropertyAccessCache PROPERTY_CACHE_25 = new dev.tsj.runtime.TsjPropertyAccessCache("service");

    private static synchronized void __tsjBootstrap() {
        if (__TSJ_BOOTSTRAPPED) {
            return;
        }
        __TSJ_TOP_LEVEL_CLASSES.clear();
        final dev.tsj.runtime.TsjCell __tsj_init_module_0_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_1_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_2_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_3_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_init_module_4_cell = new dev.tsj.runtime.TsjCell(null);
        final dev.tsj.runtime.TsjCell __tsj_export_0_HealthController_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_1_InMemoryPetRepository_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_2_PetService_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        final dev.tsj.runtime.TsjCell __tsj_export_3_PetController_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.undefined());
        __tsj_init_module_0_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis, Object... lambdaArgs) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/health-controller.ts	3	3
            final dev.tsj.runtime.TsjCell HealthController_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjClass HealthController_class = new dev.tsj.runtime.TsjClass("HealthController", null);
            HealthController_cell.set(HealthController_class);
            HealthController_class.defineMethod("health", (dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/health-controller.ts	6	7
                return dev.tsj.runtime.TsjRuntime.objectLiteral("service", "pet-store", "status", "UP");
            });
            __tsj_export_0_HealthController_cell.set(HealthController_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_0_cell.get());
        __tsj_init_module_1_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_1, Object... lambdaArgs_1) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	1	3
            final dev.tsj.runtime.TsjCell InMemoryPetRepository_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjClass InMemoryPetRepository_class = new dev.tsj.runtime.TsjClass("InMemoryPetRepository", null);
            InMemoryPetRepository_cell.set(InMemoryPetRepository_class);
            InMemoryPetRepository_class.setConstructor((dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	3	7
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "pets", dev.tsj.runtime.TsjRuntime.arrayLiteral(dev.tsj.runtime.TsjRuntime.objectLiteral("id", "pet-1", "name", "Luna", "species", "cat", "age", Integer.valueOf(3), "vaccinated", Boolean.TRUE), dev.tsj.runtime.TsjRuntime.objectLiteral("id", "pet-2", "name", "Milo", "species", "dog", "age", Integer.valueOf(5), "vaccinated", Boolean.FALSE)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	19	7
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "nextId", Integer.valueOf(3));
                return null;
            });
            InMemoryPetRepository_class.defineMethod("list", (dev.tsj.runtime.TsjObject thisObject_1, Object... methodArgs_1) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	23	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_0, thisObject_1, "pets"), "slice");
            });
            InMemoryPetRepository_class.defineMethod("findById", (dev.tsj.runtime.TsjObject thisObject_2, Object... methodArgs_2) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_2.length > 0 ? methodArgs_2[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	27	7
                final dev.tsj.runtime.TsjCell index_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	28	7
                while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(index_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_1, dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_2, thisObject_2, "pets"), "length"))))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	29	9
                    final dev.tsj.runtime.TsjCell current_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_3, thisObject_2, "pets"), "at", index_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	30	9
                    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_4, current_cell.get(), "id"), id_cell.get())))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	31	11
                        return current_cell.get();
                    }
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	33	9
                    index_cell.set(dev.tsj.runtime.TsjRuntime.add(index_cell.get(), Integer.valueOf(1)));
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	35	7
                throw dev.tsj.runtime.TsjRuntime.raise(dev.tsj.runtime.TsjRuntime.add("pet-not-found:", id_cell.get()));
            });
            InMemoryPetRepository_class.defineMethod("create", (dev.tsj.runtime.TsjObject thisObject_3, Object... methodArgs_3) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 0 ? methodArgs_3[0] : null);
                final dev.tsj.runtime.TsjCell species_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 1 ? methodArgs_3[1] : null);
                final dev.tsj.runtime.TsjCell age_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 2 ? methodArgs_3[2] : null);
                final dev.tsj.runtime.TsjCell vaccinated_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 3 ? methodArgs_3[3] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	39	7
                final dev.tsj.runtime.TsjCell pet_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral("id", dev.tsj.runtime.TsjRuntime.add("pet-", dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_5, thisObject_3, "nextId")), "name", name_cell.get(), "species", species_cell.get(), "age", age_cell.get(), "vaccinated", vaccinated_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	46	7
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject_3, "nextId", dev.tsj.runtime.TsjRuntime.add(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_6, thisObject_3, "nextId"), Integer.valueOf(1)));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	47	7
                dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_7, thisObject_3, "pets"), "push", pet_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	48	7
                return pet_cell.get();
            });
            InMemoryPetRepository_class.defineMethod("update", (dev.tsj.runtime.TsjObject thisObject_4, Object... methodArgs_4) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 0 ? methodArgs_4[0] : null);
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 1 ? methodArgs_4[1] : null);
                final dev.tsj.runtime.TsjCell species_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 2 ? methodArgs_4[2] : null);
                final dev.tsj.runtime.TsjCell age_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 3 ? methodArgs_4[3] : null);
                final dev.tsj.runtime.TsjCell vaccinated_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 4 ? methodArgs_4[4] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	52	7
                final dev.tsj.runtime.TsjCell updated_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.objectLiteral("id", id_cell.get(), "name", name_cell.get(), "species", species_cell.get(), "age", age_cell.get(), "vaccinated", vaccinated_cell.get()));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	59	7
                final dev.tsj.runtime.TsjCell index_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	60	7
                final dev.tsj.runtime.TsjCell found_cell = new dev.tsj.runtime.TsjCell(Boolean.FALSE);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	61	7
                final dev.tsj.runtime.TsjCell nextPets_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arrayLiteral());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	62	7
                while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(index_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_8, dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_9, thisObject_4, "pets"), "length"))))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	63	9
                    final dev.tsj.runtime.TsjCell current_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_10, thisObject_4, "pets"), "at", index_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	64	9
                    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_11, current_cell.get(), "id"), id_cell.get())))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	65	11
                        dev.tsj.runtime.TsjRuntime.invokeMember(nextPets_cell.get(), "push", updated_cell.get());
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	66	11
                        found_cell.set(Boolean.TRUE);
                    } else {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	68	11
                        dev.tsj.runtime.TsjRuntime.invokeMember(nextPets_cell.get(), "push", current_cell.get());
                    }
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	70	9
                    index_cell.set(dev.tsj.runtime.TsjRuntime.add(index_cell.get(), Integer.valueOf(1)));
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	72	7
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(found_cell.get(), Boolean.FALSE)))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	73	9
                    throw dev.tsj.runtime.TsjRuntime.raise(dev.tsj.runtime.TsjRuntime.add("pet-not-found:", id_cell.get()));
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	75	7
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject_4, "pets", nextPets_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	76	7
                return updated_cell.get();
            });
            InMemoryPetRepository_class.defineMethod("deleteById", (dev.tsj.runtime.TsjObject thisObject_5, Object... methodArgs_5) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_5.length > 0 ? methodArgs_5[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	80	7
                final dev.tsj.runtime.TsjCell index_cell = new dev.tsj.runtime.TsjCell(Integer.valueOf(0));
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	81	7
                final dev.tsj.runtime.TsjCell found_cell = new dev.tsj.runtime.TsjCell(Boolean.FALSE);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	82	7
                final dev.tsj.runtime.TsjCell nextPets_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.arrayLiteral());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	83	7
                while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(index_cell.get(), dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_12, dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_13, thisObject_5, "pets"), "length"))))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	84	9
                    final dev.tsj.runtime.TsjCell current_cell = new dev.tsj.runtime.TsjCell(dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_14, thisObject_5, "pets"), "at", index_cell.get()));
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	85	9
                    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_15, current_cell.get(), "id"), id_cell.get())))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	86	11
                        found_cell.set(Boolean.TRUE);
                    } else {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	88	11
                        dev.tsj.runtime.TsjRuntime.invokeMember(nextPets_cell.get(), "push", current_cell.get());
                    }
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	90	9
                    index_cell.set(dev.tsj.runtime.TsjRuntime.add(index_cell.get(), Integer.valueOf(1)));
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	92	7
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject_5, "pets", nextPets_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/repository/in-memory-pet-repository.ts	93	7
                return dev.tsj.runtime.TsjRuntime.objectLiteral("deleted", found_cell.get(), "id", id_cell.get());
            });
            __tsj_export_1_InMemoryPetRepository_cell.set(InMemoryPetRepository_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_1_cell.get());
        __tsj_init_module_2_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_2, Object... lambdaArgs_2) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	1	3
            final dev.tsj.runtime.TsjCell InMemoryPetRepository_cell = new dev.tsj.runtime.TsjCell(__tsj_export_1_InMemoryPetRepository_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	3	3
            final dev.tsj.runtime.TsjCell PetService_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjClass PetService_class = new dev.tsj.runtime.TsjClass("PetService", null);
            PetService_cell.set(PetService_class);
            PetService_class.setConstructor((dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	5	7
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "repository", dev.tsj.runtime.TsjRuntime.construct(InMemoryPetRepository_cell.get()));
                return null;
            });
            PetService_class.defineMethod("listPets", (dev.tsj.runtime.TsjObject thisObject_1, Object... methodArgs_1) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	9	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_16, thisObject_1, "repository"), "list");
            });
            PetService_class.defineMethod("getPet", (dev.tsj.runtime.TsjObject thisObject_2, Object... methodArgs_2) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_2.length > 0 ? methodArgs_2[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	13	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_17, thisObject_2, "repository"), "findById", id_cell.get());
            });
            PetService_class.defineMethod("createPet", (dev.tsj.runtime.TsjObject thisObject_3, Object... methodArgs_3) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 0 ? methodArgs_3[0] : null);
                final dev.tsj.runtime.TsjCell species_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 1 ? methodArgs_3[1] : null);
                final dev.tsj.runtime.TsjCell age_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 2 ? methodArgs_3[2] : null);
                final dev.tsj.runtime.TsjCell vaccinated_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 3 ? methodArgs_3[3] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	17	7
                dev.tsj.runtime.TsjRuntime.invokeMember(thisObject_3, "validateRequest", name_cell.get(), species_cell.get(), age_cell.get(), vaccinated_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	18	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_18, thisObject_3, "repository"), "create", name_cell.get(), species_cell.get(), age_cell.get(), vaccinated_cell.get());
            });
            PetService_class.defineMethod("changePet", (dev.tsj.runtime.TsjObject thisObject_4, Object... methodArgs_4) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 0 ? methodArgs_4[0] : null);
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 1 ? methodArgs_4[1] : null);
                final dev.tsj.runtime.TsjCell species_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 2 ? methodArgs_4[2] : null);
                final dev.tsj.runtime.TsjCell age_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 3 ? methodArgs_4[3] : null);
                final dev.tsj.runtime.TsjCell vaccinated_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 4 ? methodArgs_4[4] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	22	7
                dev.tsj.runtime.TsjRuntime.invokeMember(thisObject_4, "validateRequest", name_cell.get(), species_cell.get(), age_cell.get(), vaccinated_cell.get());
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	23	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_19, thisObject_4, "repository"), "update", id_cell.get(), name_cell.get(), species_cell.get(), age_cell.get(), vaccinated_cell.get());
            });
            PetService_class.defineMethod("deletePet", (dev.tsj.runtime.TsjObject thisObject_5, Object... methodArgs_5) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_5.length > 0 ? methodArgs_5[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	27	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_20, thisObject_5, "repository"), "deleteById", id_cell.get());
            });
            PetService_class.defineMethod("validateRequest", (dev.tsj.runtime.TsjObject thisObject_6, Object... methodArgs_6) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_6.length > 0 ? methodArgs_6[0] : null);
                final dev.tsj.runtime.TsjCell species_cell = new dev.tsj.runtime.TsjCell(methodArgs_6.length > 1 ? methodArgs_6[1] : null);
                final dev.tsj.runtime.TsjCell age_cell = new dev.tsj.runtime.TsjCell(methodArgs_6.length > 2 ? methodArgs_6[2] : null);
                final dev.tsj.runtime.TsjCell vaccinated_cell = new dev.tsj.runtime.TsjCell(methodArgs_6.length > 3 ? methodArgs_6[3] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	31	7
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.abstractEquals(name_cell.get(), null)))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	32	9
                    throw dev.tsj.runtime.TsjRuntime.raise("invalid-pet-name");
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	34	7
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(name_cell.get(), "")))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	35	9
                    throw dev.tsj.runtime.TsjRuntime.raise("invalid-pet-name");
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	37	7
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.abstractEquals(species_cell.get(), null)))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	38	9
                    throw dev.tsj.runtime.TsjRuntime.raise("invalid-pet-species");
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	40	7
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.strictEquals(species_cell.get(), "")))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	41	9
                    throw dev.tsj.runtime.TsjRuntime.raise("invalid-pet-species");
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	43	7
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(dev.tsj.runtime.TsjRuntime.lessThan(age_cell.get(), Integer.valueOf(0))))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	44	9
                    throw dev.tsj.runtime.TsjRuntime.raise("invalid-pet-age");
                }
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	46	7
                if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.strictEquals(vaccinated_cell.get(), Boolean.TRUE)))) {
                    // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	47	9
                    if (dev.tsj.runtime.TsjRuntime.truthy(Boolean.valueOf(!dev.tsj.runtime.TsjRuntime.strictEquals(vaccinated_cell.get(), Boolean.FALSE)))) {
                        // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/service/pet-service.ts	48	11
                        throw dev.tsj.runtime.TsjRuntime.raise("invalid-vaccinated-flag");
                    }
                }
                return null;
            });
            __tsj_export_2_PetService_cell.set(PetService_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_2_cell.get());
        __tsj_init_module_3_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_3, Object... lambdaArgs_3) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	1	3
            final dev.tsj.runtime.TsjCell PetService_cell = new dev.tsj.runtime.TsjCell(__tsj_export_2_PetService_cell.get());
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	5	3
            final dev.tsj.runtime.TsjCell PetController_cell = new dev.tsj.runtime.TsjCell(null);
            final dev.tsj.runtime.TsjClass PetController_class = new dev.tsj.runtime.TsjClass("PetController", null);
            PetController_cell.set(PetController_class);
            PetController_class.setConstructor((dev.tsj.runtime.TsjObject thisObject, Object... methodArgs) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	7	7
                dev.tsj.runtime.TsjRuntime.setProperty(thisObject, "service", dev.tsj.runtime.TsjRuntime.construct(PetService_cell.get()));
                return null;
            });
            PetController_class.defineMethod("list", (dev.tsj.runtime.TsjObject thisObject_1, Object... methodArgs_1) -> {
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	12	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_21, thisObject_1, "service"), "listPets");
            });
            PetController_class.defineMethod("getById", (dev.tsj.runtime.TsjObject thisObject_2, Object... methodArgs_2) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_2.length > 0 ? methodArgs_2[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	17	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_22, thisObject_2, "service"), "getPet", id_cell.get());
            });
            PetController_class.defineMethod("create", (dev.tsj.runtime.TsjObject thisObject_3, Object... methodArgs_3) -> {
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 0 ? methodArgs_3[0] : null);
                final dev.tsj.runtime.TsjCell species_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 1 ? methodArgs_3[1] : null);
                final dev.tsj.runtime.TsjCell age_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 2 ? methodArgs_3[2] : null);
                final dev.tsj.runtime.TsjCell vaccinated_cell = new dev.tsj.runtime.TsjCell(methodArgs_3.length > 3 ? methodArgs_3[3] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	22	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_23, thisObject_3, "service"), "createPet", name_cell.get(), species_cell.get(), age_cell.get(), vaccinated_cell.get());
            });
            PetController_class.defineMethod("update", (dev.tsj.runtime.TsjObject thisObject_4, Object... methodArgs_4) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 0 ? methodArgs_4[0] : null);
                final dev.tsj.runtime.TsjCell name_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 1 ? methodArgs_4[1] : null);
                final dev.tsj.runtime.TsjCell species_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 2 ? methodArgs_4[2] : null);
                final dev.tsj.runtime.TsjCell age_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 3 ? methodArgs_4[3] : null);
                final dev.tsj.runtime.TsjCell vaccinated_cell = new dev.tsj.runtime.TsjCell(methodArgs_4.length > 4 ? methodArgs_4[4] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	27	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_24, thisObject_4, "service"), "changePet", id_cell.get(), name_cell.get(), species_cell.get(), age_cell.get(), vaccinated_cell.get());
            });
            PetController_class.defineMethod("remove", (dev.tsj.runtime.TsjObject thisObject_5, Object... methodArgs_5) -> {
                final dev.tsj.runtime.TsjCell id_cell = new dev.tsj.runtime.TsjCell(methodArgs_5.length > 0 ? methodArgs_5[0] : null);
                // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/src/web/pet-controller.ts	32	7
                return dev.tsj.runtime.TsjRuntime.invokeMember(dev.tsj.runtime.TsjRuntime.getPropertyCached(PROPERTY_CACHE_25, thisObject_5, "service"), "deletePet", id_cell.get());
            });
            __tsj_export_3_PetController_cell.set(PetController_cell.get());
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_3_cell.get());
        __tsj_init_module_4_cell.set((dev.tsj.runtime.TsjCallableWithThis) (Object lambdaThis_4, Object... lambdaArgs_4) -> {
            // TSJ-SOURCE	/mnt/d/coding/tsj/examples/pet-store-api/main.ts	4	3
            dev.tsj.runtime.TsjRuntime.print("tsj-pet-store-boot");
            return null;
        });
        dev.tsj.runtime.TsjRuntime.call(__tsj_init_module_4_cell.get());
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
