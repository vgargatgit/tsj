package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjModuleRegistryTest {
    @Test
    void initializeOrdersDependenciesBeforeDependents() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        final List<String> order = new ArrayList<>();

        registry.register(new TsjModuleDescriptor("base", List.of(), ignored -> order.add("base")));
        registry.register(new TsjModuleDescriptor("mid", List.of("base"), ignored -> order.add("mid")));
        registry.register(new TsjModuleDescriptor("entry", List.of("mid"), ignored -> order.add("entry")));

        registry.initialize("entry");

        assertEquals(List.of("base", "mid", "entry"), order);
        assertEquals(TsjModuleState.INITIALIZED, registry.stateOf("base"));
        assertEquals(TsjModuleState.INITIALIZED, registry.stateOf("mid"));
        assertEquals(TsjModuleState.INITIALIZED, registry.stateOf("entry"));
    }

    @Test
    void initializeAllUsesDeterministicRegistrationOrderForIndependentModules() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        final List<String> order = new ArrayList<>();

        registry.register(new TsjModuleDescriptor("first", List.of(), ignored -> order.add("first")));
        registry.register(new TsjModuleDescriptor("second", List.of(), ignored -> order.add("second")));
        registry.register(new TsjModuleDescriptor("third", List.of(), ignored -> order.add("third")));

        registry.initializeAll();

        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    void initializeIsIdempotent() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        final List<String> order = new ArrayList<>();

        registry.register(new TsjModuleDescriptor("mod", List.of(), ignored -> order.add("mod")));

        registry.initialize("mod");
        registry.initialize("mod");

        assertEquals(List.of("mod"), order);
    }

    @Test
    void writeAndReadBindingExposeLiveValue() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        registry.register(new TsjModuleDescriptor(
                "counter",
                List.of(),
                ignored -> {
                }
        ));

        registry.initialize("counter");
        registry.writeBinding("counter", "value", 1);
        assertEquals(1, registry.readBinding("counter", "value"));

        registry.writeBinding("counter", "value", 2);
        assertEquals(2, registry.readBinding("counter", "value"));
    }

    @Test
    void readMissingBindingReturnsUndefinedAfterInitialization() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        registry.register(new TsjModuleDescriptor("mod", List.of(), ignored -> {
        }));
        registry.initialize("mod");

        assertEquals(TsjUndefined.INSTANCE, registry.readBinding("mod", "missing"));
    }

    @Test
    void readMissingBindingDuringInitializingModuleThrowsCycleUnsafe() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        registry.register(new TsjModuleDescriptor(
                "a",
                List.of("b"),
                ignored -> registry.writeBinding("a", "value", registry.readBinding("b", "value"))
        ));
        registry.register(new TsjModuleDescriptor(
                "b",
                List.of("a"),
                ignored -> registry.writeBinding("b", "value", registry.readBinding("a", "value"))
        ));

        final IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> registry.initialize("a")
        );
        assertTrue(exception.getMessage().contains("TSJRT_MODULE_CYCLE_UNSAFE"));
        assertEquals(TsjModuleState.FAILED, registry.stateOf("a"));
        assertEquals(TsjModuleState.FAILED, registry.stateOf("b"));
    }

    @Test
    void circularModulesCanInitializeWhenNoUnsafeReadOccurs() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        registry.register(new TsjModuleDescriptor(
                "a",
                List.of("b"),
                ignored -> registry.writeBinding("a", "value", "A")
        ));
        registry.register(new TsjModuleDescriptor(
                "b",
                List.of("a"),
                ignored -> registry.writeBinding("b", "value", "B")
        ));

        registry.initialize("a");

        assertEquals(TsjModuleState.INITIALIZED, registry.stateOf("a"));
        assertEquals(TsjModuleState.INITIALIZED, registry.stateOf("b"));
        assertEquals("A", registry.readBinding("a", "value"));
        assertEquals("B", registry.readBinding("b", "value"));
    }

    @Test
    void failedModuleCannotBeInitializedAgain() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        registry.register(new TsjModuleDescriptor(
                "broken",
                List.of(),
                ignored -> {
                    throw new IllegalStateException("boom");
                }
        ));

        assertThrows(IllegalStateException.class, () -> registry.initialize("broken"));
        final IllegalStateException second = assertThrows(
                IllegalStateException.class,
                () -> registry.initialize("broken")
        );
        assertTrue(second.getMessage().contains("TSJRT_MODULE_FAILED"));
    }

    @Test
    void registerRejectsDuplicateIds() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        registry.register(new TsjModuleDescriptor("dup", List.of(), ignored -> {
        }));
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(new TsjModuleDescriptor("dup", List.of(), ignored -> {
                }))
        );
        assertTrue(exception.getMessage().contains("Duplicate"));
    }

    @Test
    void unknownDependencyFailsInitialization() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        registry.register(new TsjModuleDescriptor("entry", List.of("missing"), ignored -> {
        }));

        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.initialize("entry")
        );
        assertTrue(exception.getMessage().contains("Unknown dependency"));
        assertEquals(TsjModuleState.FAILED, registry.stateOf("entry"));
    }

    @Test
    void unknownModuleOperationsFailFast() {
        final TsjModuleRegistry registry = new TsjModuleRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.stateOf("missing"));
        assertThrows(IllegalArgumentException.class, () -> registry.readBinding("missing", "x"));
        assertThrows(IllegalArgumentException.class, () -> registry.writeBinding("missing", "x", 1));
        assertThrows(IllegalArgumentException.class, () -> registry.initialize("missing"));
    }
}
