package dev.tsj.compiler.backend.jvm;

import java.util.Objects;

/**
 * One extracted TypeScript decorator usage.
 *
 * @param name decorator symbol name
 * @param rawArgs raw argument text (without parentheses), nullable
 * @param line 1-based source line
 * @param span source span for the decorator use
 */
public record TsDecoratorUse(
        String name,
        String rawArgs,
        int line,
        TsSourceSpan span
) {
    public TsDecoratorUse {
        name = Objects.requireNonNull(name, "name");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        span = Objects.requireNonNull(span, "span");
        if (span.line() != line) {
            throw new IllegalArgumentException("span line must match line");
        }
    }

    public TsDecoratorUse(
            final String name,
            final String rawArgs,
            final int line
    ) {
        this(name, rawArgs, line, TsSourceSpan.singleLine(line));
    }
}
