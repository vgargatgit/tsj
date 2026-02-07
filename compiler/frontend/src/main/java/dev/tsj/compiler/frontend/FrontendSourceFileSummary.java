package dev.tsj.compiler.frontend;

/**
 * Typed source-file summary reported by the frontend bridge.
 *
 * @param path absolute source file path
 * @param nodeCount AST node count
 * @param typedNodeCount nodes for which type information was resolved
 */
public record FrontendSourceFileSummary(
        String path,
        int nodeCount,
        int typedNodeCount
) {
}
