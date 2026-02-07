package dev.tsj.compiler.ir.hir;

import java.util.List;

/**
 * HIR project output from frontend-lowering stage.
 *
 * @param tsconfigPath tsconfig source path
 * @param modules project modules
 */
public record HirProject(
        String tsconfigPath,
        List<HirModule> modules
) {
}
