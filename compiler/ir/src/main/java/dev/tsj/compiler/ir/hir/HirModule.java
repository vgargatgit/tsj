package dev.tsj.compiler.ir.hir;

import java.util.List;

/**
 * HIR module representing one source file.
 *
 * @param path source file path
 * @param statements lowered top-level statements
 */
public record HirModule(
        String path,
        List<HirStatement> statements
) {
}
