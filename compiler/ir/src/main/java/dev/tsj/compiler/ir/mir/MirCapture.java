package dev.tsj.compiler.ir.mir;

/**
 * Captured-variable mapping for closures.
 *
 * @param functionName capturing function
 * @param variableName captured variable
 * @param sourceScopeId scope where variable was declared
 * @param targetScopeId scope that captures it
 */
public record MirCapture(
        String functionName,
        String variableName,
        String sourceScopeId,
        String targetScopeId
) {
}
