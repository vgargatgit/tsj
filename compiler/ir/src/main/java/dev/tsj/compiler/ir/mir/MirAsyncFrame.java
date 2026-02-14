package dev.tsj.compiler.ir.mir;

import java.util.List;

/**
 * MIR async state-machine metadata for a lowered function.
 *
 * @param states ordered async states
 * @param suspendPoints explicit await suspend/resume points
 * @param terminalOps terminal control-flow operations observed in async body
 */
public record MirAsyncFrame(
        List<MirAsyncState> states,
        List<MirAsyncSuspendPoint> suspendPoints,
        List<String> terminalOps
) {
}
