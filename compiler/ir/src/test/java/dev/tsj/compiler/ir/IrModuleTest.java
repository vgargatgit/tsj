package dev.tsj.compiler.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrModuleTest {
    @Test
    void moduleNameIsStable() {
        assertEquals("compiler-ir", IrModule.moduleName());
    }
}
