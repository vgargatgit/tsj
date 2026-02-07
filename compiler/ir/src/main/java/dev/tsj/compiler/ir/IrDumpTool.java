package dev.tsj.compiler.ir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * IR dump utility for debugging pipeline output.
 */
public final class IrDumpTool {
    private IrDumpTool() {
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: IrDumpTool <tsconfig-path> [--out <output-file>]");
            System.exit(2);
        }
        final Path tsconfigPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path outFile = null;
        if (args.length > 1) {
            if (args.length != 3 || !"--out".equals(args[1])) {
                System.err.println("Usage: IrDumpTool <tsconfig-path> [--out <output-file>]");
                System.exit(2);
            }
            outFile = Path.of(args[2]).toAbsolutePath().normalize();
        }

        if (outFile != null) {
            dumpProject(Path.of("").toAbsolutePath(), tsconfigPath, outFile);
            return;
        }

        final IrProject project = new IrLoweringService(Path.of("").toAbsolutePath()).lowerProject(tsconfigPath);
        System.out.println(IrJsonPrinter.toJson(project));
    }

    public static void dumpProject(final Path workspaceRoot, final Path tsconfigPath, final Path outFile) {
        final IrProject project = new IrLoweringService(workspaceRoot).lowerProject(tsconfigPath);
        final String json = IrJsonPrinter.toJson(project);
        try {
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, json, UTF_8);
        } catch (final IOException ioException) {
            throw new IllegalStateException("Failed to write IR dump file: " + outFile, ioException);
        }
    }
}
