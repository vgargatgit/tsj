package dev.tsj.runtime;

/**
 * Module initializer callback.
 */
@FunctionalInterface
public interface TsjModuleInitializer {
    void initialize(TsjModuleRegistry registry);
}
