package dev.tsj.cli.fixtures;

/**
 * Fixture execution summary across Node and TSJ runs.
 *
 * @param fixtureName fixture name
 * @param nodeResult Node execution details
 * @param tsjResult TSJ execution details
 * @param nodeToTsjRequired whether direct Node-vs-TSJ comparison is required
 * @param nodeToTsjMatched true when Node-vs-TSJ comparison succeeded
 * @param nodeToTsjDiff direct comparison mismatch summary
 */
public record FixtureRunResult(
        String fixtureName,
        RuntimeExecutionResult nodeResult,
        RuntimeExecutionResult tsjResult,
        boolean nodeToTsjRequired,
        boolean nodeToTsjMatched,
        String nodeToTsjDiff
) {
    public boolean passed() {
        if (!nodeResult.matchedExpectation() || !tsjResult.matchedExpectation()) {
            return false;
        }
        if (!nodeToTsjRequired) {
            return true;
        }
        return nodeToTsjMatched;
    }
}
