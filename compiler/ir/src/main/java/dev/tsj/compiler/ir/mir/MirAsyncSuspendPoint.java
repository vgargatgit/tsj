package dev.tsj.compiler.ir.mir;

/**
 * Async suspension marker with resume target.
 *
 * @param suspendPc suspension state pc
 * @param resumePc resume state pc
 * @param awaitedExpression awaited expression payload
 * @param line 1-based source line associated with the await
 */
public record MirAsyncSuspendPoint(
        int suspendPc,
        int resumePc,
        String awaitedExpression,
        int line
) {
}
