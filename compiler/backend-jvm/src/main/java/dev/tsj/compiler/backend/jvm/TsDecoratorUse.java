package dev.tsj.compiler.backend.jvm;

import java.util.Objects;

/**
 * One extracted TypeScript decorator usage.
 *
 * @param name decorator symbol name
 * @param rawArgs raw argument text (without parentheses), nullable
 * @param line 1-based source line
 */
public record TsDecoratorUse(
        String name,
        String rawArgs,
        int line
) {
    public TsDecoratorUse {
        name = Objects.requireNonNull(name, "name");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
    }
}
