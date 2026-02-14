package dev.tsj.compiler.ir.mir;

/**
 * Async state-machine state descriptor.
 *
 * @param pc logical async program-counter value
 * @param kind state kind (for example ENTRY, SUSPEND, RESUME, EXIT)
 * @param line 1-based source line associated with the state
 */
public record MirAsyncState(
        int pc,
        String kind,
        int line
) {
}
