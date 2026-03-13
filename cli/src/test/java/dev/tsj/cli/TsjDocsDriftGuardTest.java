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
    void cliContractRetiresLegacySpringCompatibilityHooks() throws Exception {
        final String contract = Files.readString(repoRoot().resolve("docs/cli-contract.md"), UTF_8);

        assertFalse(contract.contains("--legacy-spring-adapters"), contract);
        assertFalse(contract.contains("spring-package"), contract);
        assertFalse(contract.contains("springConfiguration"), contract);
        assertFalse(contract.contains("springBeanTargets"), contract);
        assertFalse(contract.contains("springWebController"), contract);
        assertFalse(contract.contains("springRequestMappings."), contract);
        assertFalse(contract.contains("springErrorMappings"), contract);
        assertFalse(contract.contains("tsjWebControllers.controllerCount"), contract);
        assertFalse(contract.contains("tsjSpringComponents.componentCount"), contract);
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
        assertTrue(strictGuide.contains("tsj package app/main.ts --out build --mode jvm-strict"));
        assertFalse(strictGuide.contains("legacy alias"));
        assertFalse(strictGuide.contains("spring-package"));
        assertTrue(strictGuide.contains("Migration Strategy"));
        assertTrue(strictChecklist.contains("## Known Exclusions"));
        assertTrue(strictChecklist.contains("tsj package app/main.ts --out build --mode jvm-strict"));
        assertTrue(cliContract.contains("--mode default|jvm-strict"));
        assertTrue(cliContract.contains("tsj package <entry.ts>"));
        assertTrue(cliContract.contains("TSJ-STRICT-UNSUPPORTED"));
    }

    @Test
    void currentGuidesDoNotDescribeRetiredAdapterEraPackagingPath() throws Exception {
        final Path repoRoot = repoRoot();
        final String strictGuide = Files.readString(repoRoot.resolve("docs/jvm-strict-mode-guide.md"), UTF_8);
        final String classpathGuide = Files.readString(repoRoot.resolve("docs/classpath-mediation.md"), UTF_8);
        final String kotlinGuide = Files.readString(repoRoot.resolve("docs/tsj-kotlin-migration-guide.md"), UTF_8);
        final String anyJarGuide = Files.readString(repoRoot.resolve("docs/anyjar-certification.md"), UTF_8);
        final String annotationGuide = Files.readString(repoRoot.resolve("docs/annotation-mapping.md"), UTF_8);
        final String springMatrix = Files.readString(repoRoot.resolve("docs/spring-ecosystem-matrix.md"), UTF_8);
        final String petClinic = Files.readString(repoRoot.resolve("examples/pet-clinic/README.md"), UTF_8);
        final String introspectorReadme = Files.readString(repoRoot.resolve("tests/introspector-matrix/README.md"), UTF_8);

        assertFalse(strictGuide.contains("controller adapters"), strictGuide);
        assertFalse(strictGuide.contains("generated Boot launcher"), strictGuide);
        assertFalse(classpathGuide.contains("spring-package"), classpathGuide);
        assertFalse(kotlinGuide.contains("spring-package"), kotlinGuide);
        assertFalse(anyJarGuide.contains("spring-package"), anyJarGuide);
        assertFalse(anyJarGuide.contains("--legacy-spring-adapters"), anyJarGuide);
        assertFalse(anyJarGuide.contains("Automatic Spring adapter generation"), anyJarGuide);
        assertFalse(annotationGuide.contains("generated bridge and adapter classes"), annotationGuide);
        assertFalse(annotationGuide.contains("Spring web adapter classes generated from decorators"), annotationGuide);
        assertFalse(springMatrix.contains("controller adapter behavior"), springMatrix);
        assertFalse(petClinic.contains("spring-package"), petClinic);
        assertFalse(introspectorReadme.contains("generated controller adapter"), introspectorReadme);
    }

    @Test
    void tsjCliSourceDoesNotReferenceRetiredLegacySpringCompatibilityHooks() throws Exception {
        final String cliSource = Files.readString(
                repoRoot().resolve("cli/src/main/java/dev/tsj/cli/TsjCli.java"),
                UTF_8
        );

        assertFalse(cliSource.contains("TsjSpringWebControllerGenerator"), cliSource);
        assertFalse(cliSource.contains("TsjSpringComponentGenerator"), cliSource);
        assertFalse(cliSource.contains("spring-package"), cliSource);
        assertFalse(cliSource.contains("--legacy-spring-adapters"), cliSource);
        assertFalse(cliSource.contains("tsjWebControllerCount"), cliSource);
        assertFalse(cliSource.contains("tsjSpringComponentCount"), cliSource);
    }

    @Test
    void tsjCliSourceUsesGenericMetadataMergeStrategyNames() throws Exception {
        final String cliSource = Files.readString(
                repoRoot().resolve("cli/src/main/java/dev/tsj/cli/TsjCli.java"),
                UTF_8
        );

        assertFalse(cliSource.contains("SPRING_FACTORIES_ENTRY"), cliSource);
        assertFalse(cliSource.contains("\"META-INF/spring.factories\""), cliSource);
        assertFalse(cliSource.contains("entryName.startsWith(\"META-INF/spring/\")"), cliSource);
    }

    private static Path repoRoot() {
        final Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && "cli".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
        }
        return cwd;
    }
}
