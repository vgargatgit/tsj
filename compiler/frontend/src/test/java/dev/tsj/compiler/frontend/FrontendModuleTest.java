package dev.tsj.compiler.frontend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontendModuleTest {
    @Test
    void moduleNameIsStable() {
        assertEquals("compiler-frontend", FrontendModule.moduleName());
    }
}
