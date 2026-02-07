package dev.tsj.cli.fixtures;

/**
 * Actual execution and comparison outcome for one runtime target.
 *
 * @param runtimeName runtime label (node/tsj)
 * @param exitCode actual exit code
 * @param stdout actual stdout
 * @param stderr actual stderr
 * @param matchedExpectation true when actual output matches expected fixture values
 * @param diff summary mismatch description
 */
public record RuntimeExecutionResult(
        String runtimeName,
        int exitCode,
        String stdout,
        String stderr,
        boolean matchedExpectation,
        String diff
) {
}
