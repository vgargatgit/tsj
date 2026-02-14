package dev.tsj.compiler.ir.jir;

import java.util.List;

/**
 * JVM-oriented lowered method body.
 *
 * @param name method name
 * @param bytecodeOps pseudo-bytecode operations for debug output
 * @param async whether the method corresponds to async lowering
 * @param asyncStateOps explicit async state-machine pseudo-ops
 */
public record JirMethod(
        String name,
        List<String> bytecodeOps,
        boolean async,
        List<String> asyncStateOps
) {
}
