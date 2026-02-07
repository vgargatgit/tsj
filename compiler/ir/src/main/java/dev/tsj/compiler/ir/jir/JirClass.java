package dev.tsj.compiler.ir.jir;

import java.util.List;

/**
 * JVM class model for JIR.
 *
 * @param internalName JVM internal class name
 * @param methods lowered methods
 */
public record JirClass(
        String internalName,
        List<JirMethod> methods
) {
}
