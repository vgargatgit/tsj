package dev.tsj.runtime;

/**
 * Lifecycle states for module initialization.
 */
public enum TsjModuleState {
    UNINITIALIZED,
    INITIALIZING,
    INITIALIZED,
    FAILED
}
