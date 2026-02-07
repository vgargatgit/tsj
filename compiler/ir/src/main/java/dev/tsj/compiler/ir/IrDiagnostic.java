package dev.tsj.compiler.ir;

/**
 * Diagnostic emitted during frontend-to-IR lowering.
 *
 * @param stage pipeline stage (FRONTEND/HIR/MIR/JIR)
 * @param code diagnostic code
 * @param severity severity label (Error/Warning/Info)
 * @param message diagnostic message
 * @param filePath optional source file path
 * @param line optional 1-based line
 * @param column optional 1-based column
 */
public record IrDiagnostic(
        String stage,
        String code,
        String severity,
        String message,
        String filePath,
        Integer line,
        Integer column
) {
}
