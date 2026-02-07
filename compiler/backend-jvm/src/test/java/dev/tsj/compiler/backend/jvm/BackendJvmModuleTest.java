package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendJvmModuleTest {
    @Test
    void moduleNameIsStable() {
        assertEquals("compiler-backend-jvm", BackendJvmModule.moduleName());
    }

    @Test
    void dependencyFingerprintIncludesIrAndRuntime() {
        assertEquals("compiler-ir+runtime", BackendJvmModule.dependencyFingerprint());
    }
}
