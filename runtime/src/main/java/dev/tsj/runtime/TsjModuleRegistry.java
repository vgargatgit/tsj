package dev.tsj.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry that manages module initialization and live export bindings.
 */
public final class TsjModuleRegistry {
    private final Map<String, TsjModuleDescriptor> descriptors;
    private final Map<String, TsjModuleState> states;
    private final Map<String, Map<String, TsjCell>> exports;

    public TsjModuleRegistry() {
        this.descriptors = new LinkedHashMap<>();
        this.states = new LinkedHashMap<>();
        this.exports = new LinkedHashMap<>();
    }

    public void register(final TsjModuleDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptors.containsKey(descriptor.id())) {
            throw new IllegalArgumentException("Duplicate module id: " + descriptor.id());
        }
        descriptors.put(descriptor.id(), descriptor);
        states.put(descriptor.id(), TsjModuleState.UNINITIALIZED);
        exports.put(descriptor.id(), new LinkedHashMap<>());
    }

    public void initializeAll() {
        for (String moduleId : descriptors.keySet()) {
            initialize(moduleId);
        }
    }

    public void initialize(final String moduleId) {
        final TsjModuleDescriptor descriptor = resolveDescriptor(moduleId);
        final TsjModuleState currentState = states.get(moduleId);
        if (currentState == TsjModuleState.INITIALIZED) {
            return;
        }
        if (currentState == TsjModuleState.INITIALIZING) {
            return;
        }
        if (currentState == TsjModuleState.FAILED) {
            throw new IllegalStateException("TSJRT_MODULE_FAILED: " + moduleId);
        }

        states.put(moduleId, TsjModuleState.INITIALIZING);
        try {
            for (String dependencyId : descriptor.dependencies()) {
                if (!descriptors.containsKey(dependencyId)) {
                    throw new IllegalArgumentException("Unknown dependency `" + dependencyId + "` for " + moduleId);
                }
                initialize(dependencyId);
            }
            descriptor.initializer().initialize(this);
            states.put(moduleId, TsjModuleState.INITIALIZED);
        } catch (RuntimeException runtimeException) {
            states.put(moduleId, TsjModuleState.FAILED);
            throw runtimeException;
        }
    }

    public Object readBinding(final String moduleId, final String exportName) {
        resolveDescriptor(moduleId);
        final Map<String, TsjCell> moduleExports = exports.get(moduleId);
        if (!moduleExports.containsKey(exportName)) {
            if (states.get(moduleId) == TsjModuleState.INITIALIZING) {
                throw new IllegalStateException(
                        "TSJRT_MODULE_CYCLE_UNSAFE: binding `" + exportName + "` read before initialization."
                );
            }
            return TsjUndefined.INSTANCE;
        }
        return moduleExports.get(exportName).get();
    }

    public void writeBinding(final String moduleId, final String exportName, final Object value) {
        resolveDescriptor(moduleId);
        final Map<String, TsjCell> moduleExports = exports.get(moduleId);
        final TsjCell cell = moduleExports.computeIfAbsent(exportName, ignored -> new TsjCell(TsjUndefined.INSTANCE));
        cell.set(value);
    }

    public TsjModuleState stateOf(final String moduleId) {
        resolveDescriptor(moduleId);
        return states.get(moduleId);
    }

    private TsjModuleDescriptor resolveDescriptor(final String moduleId) {
        final TsjModuleDescriptor descriptor = descriptors.get(moduleId);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown module id: " + moduleId);
        }
        return descriptor;
    }
}
