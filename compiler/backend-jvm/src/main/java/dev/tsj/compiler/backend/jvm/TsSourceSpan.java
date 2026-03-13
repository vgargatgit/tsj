package dev.tsj.compiler.backend.jvm;

/**
 * 1-based source span for a TypeScript declaration or decorator node.
 */
public record TsSourceSpan(
        int line,
        int column,
        int endLine,
        int endColumn
) {
    public TsSourceSpan {
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be >= 1");
        }
        if (endLine < line) {
            throw new IllegalArgumentException("endLine must be >= line");
        }
        if (endColumn < 1) {
            throw new IllegalArgumentException("endColumn must be >= 1");
        }
        if (endLine == line && endColumn < column) {
            throw new IllegalArgumentException("endColumn must be >= column when span is single-line");
        }
    }

    public static TsSourceSpan singleLine(final int line) {
        return new TsSourceSpan(line, 1, line, 1);
    }
}
