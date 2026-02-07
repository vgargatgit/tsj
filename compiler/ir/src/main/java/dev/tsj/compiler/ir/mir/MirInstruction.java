package dev.tsj.compiler.ir.mir;

import java.util.List;

/**
 * MIR instruction in normalized control-flow representation.
 *
 * @param op instruction opcode
 * @param args instruction operands
 * @param line source line
 */
public record MirInstruction(
        String op,
        List<String> args,
        int line
) {
}
