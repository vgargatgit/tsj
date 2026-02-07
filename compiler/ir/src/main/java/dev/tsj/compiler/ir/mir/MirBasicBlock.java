package dev.tsj.compiler.ir.mir;

import java.util.List;

/**
 * MIR basic block with ordered instructions.
 *
 * @param id stable block identifier
 * @param instructions instructions in this block
 */
public record MirBasicBlock(
        String id,
        List<MirInstruction> instructions
) {
}
