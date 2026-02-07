package dev.tsj.compiler.ir.mir;

import java.util.List;

/**
 * MIR project representation.
 *
 * @param functions normalized functions for the project
 */
public record MirProject(
        List<MirFunction> functions
) {
}
