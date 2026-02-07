package dev.tsj.compiler.ir.mir;

import java.util.List;

/**
 * MIR lexical scope metadata.
 *
 * @param scopeId unique scope identifier
 * @param parentScopeId parent scope id, null for root scope
 * @param ownerFunction function that owns this scope
 * @param locals declared local variables/parameters
 * @param capturedFromParent variables captured from ancestor scopes
 */
public record MirScope(
        String scopeId,
        String parentScopeId,
        String ownerFunction,
        List<String> locals,
        List<String> capturedFromParent
) {
}
