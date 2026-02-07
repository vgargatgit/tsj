package dev.tsj.compiler.ir.mir;

/**
 * Explicit MIR control-flow edge.
 *
 * @param fromBlock source block id
 * @param toBlock destination block id
 * @param kind edge kind (for example FALLTHROUGH, BRANCH_TRUE, BRANCH_FALSE)
 */
public record MirControlFlowEdge(
        String fromBlock,
        String toBlock,
        String kind
) {
}
