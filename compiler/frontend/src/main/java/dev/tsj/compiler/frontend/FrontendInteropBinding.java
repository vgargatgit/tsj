package dev.tsj.compiler.frontend;

/**
 * Frontend-discovered Java interop binding from TS source.
 *
 * @param filePath absolute source file path
 * @param line 1-based binding line
 * @param column 1-based binding column
 * @param className Java fully-qualified class name
 * @param importedName imported Java member name
 * @param localName local binding name in TS source
 */
public record FrontendInteropBinding(
        String filePath,
        int line,
        int column,
        String className,
        String importedName,
        String localName
) {
}
