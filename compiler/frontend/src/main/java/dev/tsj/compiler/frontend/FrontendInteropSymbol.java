package dev.tsj.compiler.frontend;

import java.util.List;

/**
 * Descriptor-backed Java interop symbol metadata for frontend/typechecker consumers.
 *
 * @param filePath source file path for this import binding
 * @param line 1-based source line
 * @param column 1-based source column
 * @param className Java class name from module specifier
 * @param importedName imported Java binding name
 * @param localName local alias in TS source
 * @param symbolKind resolved binding kind (method/field/constructor)
 * @param owner resolved JVM owner class
 * @param memberName resolved member name or {@code <init>}
 * @param descriptors JVM descriptors for overload/member signatures
 * @param typeRepresentation stable synthetic declaration text for TS-checker integration
 */
public record FrontendInteropSymbol(
        String filePath,
        int line,
        int column,
        String className,
        String importedName,
        String localName,
        String symbolKind,
        String owner,
        String memberName,
        List<String> descriptors,
        String typeRepresentation
) {
}
