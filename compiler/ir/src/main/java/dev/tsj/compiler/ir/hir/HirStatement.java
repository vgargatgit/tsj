package dev.tsj.compiler.ir.hir;

/**
 * Minimal HIR statement for TSJ-5 bootstrap lowering.
 *
 * @param kind statement kind (for example VAR_DECL, PRINT, IMPORT)
 * @param target statement target symbol where applicable
 * @param expression raw expression payload
 * @param line 1-based source line
 */
public record HirStatement(
        String kind,
        String target,
        String expression,
        int line
) {
}
