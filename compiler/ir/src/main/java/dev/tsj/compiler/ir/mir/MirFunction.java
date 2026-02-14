package dev.tsj.compiler.ir.mir;

import java.util.List;

/**
 * MIR function built from lowered module statements.
 *
 * @param name function name
 * @param instructions normalized instruction list
 * @param blocks explicit basic blocks
 * @param cfgEdges explicit control-flow edges
 * @param scopes lexical scope metadata
 * @param captures captured variable metadata
 * @param async whether the lowered function is async
 * @param asyncFrame explicit async state-machine metadata when async=true
 */
public record MirFunction(
        String name,
        List<MirInstruction> instructions,
        List<MirBasicBlock> blocks,
        List<MirControlFlowEdge> cfgEdges,
        List<MirScope> scopes,
        List<MirCapture> captures,
        boolean async,
        MirAsyncFrame asyncFrame
) {
}
