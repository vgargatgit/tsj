package dev.tsj.compiler.frontend;

/**
 * Diagnostic emitted by TypeScript parser/type-check phases.
 *
 * @param code TypeScript diagnostic code, for example TS2322
 * @param category diagnostic category, for example Error
 * @param message human-readable diagnostic message
 * @param filePath optional file path
 * @param line optional 1-based line
 * @param column optional 1-based column
 */
public record FrontendDiagnostic(
        String code,
        String category,
        String message,
        String filePath,
        Integer line,
        Integer column
) {
}
