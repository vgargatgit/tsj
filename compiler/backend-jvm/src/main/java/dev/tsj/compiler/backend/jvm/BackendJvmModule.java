package dev.tsj.compiler.backend.jvm;

import dev.tsj.compiler.ir.IrModule;
import dev.tsj.runtime.RuntimeModule;

/**
 * Backend module placeholder for JVM bytecode emission.
 */
public final class BackendJvmModule {
    private BackendJvmModule() {
    }

    public static String moduleName() {
        return "compiler-backend-jvm";
    }

    public static String dependencyFingerprint() {
        return IrModule.moduleName() + "+" + RuntimeModule.moduleName();
    }
}
