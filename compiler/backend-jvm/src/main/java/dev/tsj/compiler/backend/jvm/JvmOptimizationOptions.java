package dev.tsj.compiler.backend.jvm;

/**
 * TSJ-17 baseline optimization controls.
 *
 * @param constantFoldingEnabled enable constant-expression folding
 * @param deadCodeEliminationEnabled enable baseline dead-code elimination
 */
public record JvmOptimizationOptions(
        boolean constantFoldingEnabled,
        boolean deadCodeEliminationEnabled
) {
    public static JvmOptimizationOptions defaults() {
        return new JvmOptimizationOptions(true, true);
    }

    public static JvmOptimizationOptions disabled() {
        return new JvmOptimizationOptions(false, false);
    }
}
