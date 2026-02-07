package dev.tsj.compiler.ir.jir;

import java.util.List;

/**
 * JVM-oriented project representation.
 *
 * @param classes lowered classes
 */
public record JirProject(
        List<JirClass> classes
) {
}
