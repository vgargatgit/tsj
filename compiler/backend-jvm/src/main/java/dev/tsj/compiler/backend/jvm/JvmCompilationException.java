package dev.tsj.compiler.backend.jvm;

/**
 * Backend compilation failure with stable diagnostic code.
 */
public final class JvmCompilationException extends RuntimeException {
    private final String code;
    private final Integer line;
    private final Integer column;
    private final String sourceFile;
    private final String featureId;
    private final String guidance;

    public JvmCompilationException(final String code, final String message) {
        this(code, message, null, null, null, null, null, null);
    }

    public JvmCompilationException(
            final String code,
            final String message,
            final Integer line,
            final Integer column
    ) {
        this(code, message, line, column, null, null, null, null);
    }

    public JvmCompilationException(
            final String code,
            final String message,
            final Integer line,
            final Integer column,
            final Throwable cause
    ) {
        this(code, message, line, column, null, null, null, cause);
    }

    public JvmCompilationException(
            final String code,
            final String message,
            final Integer line,
            final Integer column,
            final String sourceFile,
            final String featureId,
            final String guidance
    ) {
        this(code, message, line, column, sourceFile, featureId, guidance, null);
    }

    public JvmCompilationException(
            final String code,
            final String message,
            final Integer line,
            final Integer column,
            final String sourceFile,
            final String featureId,
            final String guidance,
            final Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.line = line;
        this.column = column;
        this.sourceFile = sourceFile;
        this.featureId = featureId;
        this.guidance = guidance;
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

    public String sourceFile() {
        return sourceFile;
    }

    public String featureId() {
        return featureId;
    }

    public String guidance() {
        return guidance;
    }
}
