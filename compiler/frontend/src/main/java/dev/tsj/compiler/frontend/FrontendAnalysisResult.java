package dev.tsj.compiler.frontend;

import java.nio.file.Path;
import java.util.List;

/**
 * Frontend analysis output for one tsconfig project.
 *
 * @param tsconfigPath analyzed tsconfig file
 * @param sourceFiles source-file typed summaries
 * @param diagnostics parser/type diagnostics with source mapping
 * @param interopBindings discovered TSJ interop bindings from source imports
 * @param interopSymbols descriptor-backed Java interop symbol metadata
 */
public record FrontendAnalysisResult(
        Path tsconfigPath,
        List<FrontendSourceFileSummary> sourceFiles,
        List<FrontendDiagnostic> diagnostics,
        List<FrontendInteropBinding> interopBindings,
        List<FrontendInteropSymbol> interopSymbols
) {
}
