package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeModuleTest {
    @Test
    void moduleNameIsStable() {
        assertEquals("runtime", RuntimeModule.moduleName());
    }
}
