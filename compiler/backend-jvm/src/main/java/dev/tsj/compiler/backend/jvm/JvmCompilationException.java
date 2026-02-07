package dev.tsj.compiler.backend.jvm;

/**
 * Backend compilation failure with stable diagnostic code.
 */
public final class JvmCompilationException extends RuntimeException {
    private final String code;
    private final Integer line;
    private final Integer column;

    public JvmCompilationException(final String code, final String message) {
        this(code, message, null, null, null);
    }

    public JvmCompilationException(
            final String code,
            final String message,
            final Integer line,
            final Integer column
    ) {
        this(code, message, line, column, null);
    }

    public JvmCompilationException(
            final String code,
            final String message,
            final Integer line,
            final Integer column,
            final Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.line = line;
        this.column = column;
    }

    public String code() {
        return code;
    }

    public Integer line() {
        return line;
    }

    public Integer column() {
        return column;
    }
}
