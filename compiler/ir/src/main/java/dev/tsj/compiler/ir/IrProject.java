package dev.tsj.compiler.ir;

import dev.tsj.compiler.ir.hir.HirProject;
import dev.tsj.compiler.ir.jir.JirProject;
import dev.tsj.compiler.ir.mir.MirProject;

import java.util.List;

/**
 * Aggregate IR project across all lowering stages.
 *
 * @param hir high-level IR
 * @param mir middle-level IR
 * @param jir JVM-oriented IR
 * @param diagnostics lowering diagnostics
 */
public record IrProject(
        HirProject hir,
        MirProject mir,
        JirProject jir,
        List<IrDiagnostic> diagnostics
) {
}
