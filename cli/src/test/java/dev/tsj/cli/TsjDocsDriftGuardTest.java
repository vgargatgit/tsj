package dev.tsj.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjDocsDriftGuardTest {

    @Test
    void cliContractDocumentsLegacySpringAdapterOptInForCompileAndRun() throws Exception {
        final String contract = Files.readString(repoRoot().resolve("docs/cli-contract.md"), UTF_8);

        assertTrue(contract.contains("[--legacy-spring-adapters] [--optimize|--no-optimize]"));
        assertTrue(contract.contains("[--legacy-spring-adapters] [--classloader-isolation shared|app-isolated]"));
        assertTrue(contract.contains("default `compile` does not generate Spring web/component adapters."));
        assertTrue(contract.contains("default `run` compile phase does not generate Spring web/component adapters."));
    }

    @Test
    void canonicalDocsDoNotClaimDefaultCompileRunAutoGeneratesSpringAdapters() throws Exception {
        final List<Path> canonicalDocs = List.of(
                repoRoot().resolve("README.md"),
                repoRoot().resolve("docs/README.md"),
                repoRoot().resolve("docs/cli-contract.md")
        );

        for (Path doc : canonicalDocs) {
            final String lower = Files.readString(doc, UTF_8).toLowerCase();
            assertFalse(
                    lower.contains("auto-generates spring adapter source files"),
                    () -> "Stale default spring-adapter claim in " + doc
            );
            assertFalse(
                    lower.contains("compile now auto-generates spring adapter"),
                    () -> "Stale default spring-adapter claim in " + doc
            );
        }
    }

    @Test
    void strictModeGuideChecklistAndCliMatrixRemainCanonical() throws Exception {
        final Path repoRoot = repoRoot();
        final String docsReadme = Files.readString(repoRoot.resolve("docs/README.md"), UTF_8);
        final String strictGuide = Files.readString(repoRoot.resolve("docs/jvm-strict-mode-guide.md"), UTF_8);
        final String strictChecklist = Files.readString(repoRoot.resolve("docs/jvm-strict-release-checklist.md"), UTF_8);
        final String cliContract = Files.readString(repoRoot.resolve("docs/cli-contract.md"), UTF_8);

        assertTrue(docsReadme.contains("docs/jvm-strict-mode-guide.md"));
        assertTrue(docsReadme.contains("docs/jvm-strict-release-checklist.md"));
        assertTrue(strictGuide.contains("tsj compile app/main.ts --out build --mode jvm-strict"));
        assertTrue(strictGuide.contains("Migration Strategy"));
        assertTrue(strictChecklist.contains("## Known Exclusions"));
        assertTrue(strictChecklist.contains("tsj spring-package app/main.ts --out build --mode jvm-strict"));
        assertTrue(cliContract.contains("--mode default|jvm-strict"));
        assertTrue(cliContract.contains("TSJ-STRICT-UNSUPPORTED"));
    }

    private static Path repoRoot() {
        final Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && "cli".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
        }
        return cwd;
    }
}
