package dev.tsj.compiler.ir.jir;

import java.util.List;

/**
 * JVM-oriented lowered method body.
 *
 * @param name method name
 * @param bytecodeOps pseudo-bytecode operations for debug output
 */
public record JirMethod(
        String name,
        List<String> bytecodeOps
) {
}
