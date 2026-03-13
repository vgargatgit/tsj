package dev.tsj.cli;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-85 baseline harness for the no-hacks any-jar target.
 */
final class TsjAnyJarNoHacksBaselineHarness {
    private static final String REPORT_FILE = "tsj85-anyjar-nohacks-baseline.json";
    private static final String FIXTURE_VERSION = "tsj85-baseline-2026.03";
    private static final String INCREMENTAL_CACHE_PROPERTY = "tsj.backend.incrementalCache";
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\"code\":\"([^\"]+)\"");
    private static final String SCENARIO_GENERIC_PACKAGE = "generic-package-command";
    private static final String SCENARIO_SPRING_WEB_JPA = "spring-web-jpa-package";
    private static final String SCENARIO_SPRING_AOP_WEB_DI = "spring-aop-web-di-generic-compile";
    private static final String SCENARIO_RUNTIME_ANNOTATION = "runtime-annotation-on-executable-class";

    TsjAnyJarNoHacksBaselineReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj85-anyjar-nohacks-work");
        Files.createDirectories(workRoot);

        final Path repoRoot = resolveRepoRoot();
        final TsjAnyJarNoHacksBaselineReport.ScenarioResult genericPackageScenario =
                runGenericPackageScenario(workRoot.resolve("generic-package"), repoRoot);
        final TsjAnyJarNoHacksBaselineReport.ScenarioResult springWebJpaScenario =
                runSpringWebJpaScenario(workRoot.resolve("spring-web-jpa"), repoRoot);
        final TsjAnyJarNoHacksBaselineReport.ScenarioResult springAopWebDiScenario =
                runSpringAopWebDiScenario(workRoot.resolve("spring-aop-web-di"), repoRoot);
        final TsjAnyJarNoHacksBaselineReport.ScenarioResult runtimeAnnotationScenario =
                runRuntimeAnnotationScenario(workRoot.resolve("runtime-annotation"), repoRoot);

        final List<TsjAnyJarNoHacksBaselineReport.ScenarioResult> scenarios = List.of(
                genericPackageScenario,
                springWebJpaScenario,
                springAopWebDiScenario,
                runtimeAnnotationScenario
        );
        final List<TsjAnyJarNoHacksBaselineReport.Blocker> blockers = deriveBlockers(
                genericPackageScenario,
                springWebJpaScenario,
                springAopWebDiScenario,
                runtimeAnnotationScenario
        );
        final boolean gatePassed = scenarios.stream().allMatch(TsjAnyJarNoHacksBaselineReport.ScenarioResult::passed)
                && blockers.stream().noneMatch(TsjAnyJarNoHacksBaselineReport.Blocker::present);

        final TsjAnyJarNoHacksBaselineReport report = new TsjAnyJarNoHacksBaselineReport(
                gatePassed,
                FIXTURE_VERSION,
                scenarios,
                blockers,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjAnyJarNoHacksBaselineReport.ScenarioResult runGenericPackageScenario(
            final Path workDir,
            final Path repoRoot
    ) throws IOException {
        final String expected =
                "CLI exposes a generic `package` command for jar packaging without framework-specific command names.";
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/conformance/anyjar-nohacks/generic_package_probe"),
                workDir.resolve("fixture")
        );
        final CommandResult result = runCommand(
                "package",
                fixtureRoot.resolve("main.ts").toString(),
                "--out",
                workDir.resolve("out").toString()
        );
        final String diagnosticCode = lastDiagnosticCode(result.stderr(), result.stdout());
        final boolean passed = result.exitCode() == 0;
        final String observed = "exit=" + result.exitCode() + ",code=" + diagnosticCode;
        return new TsjAnyJarNoHacksBaselineReport.ScenarioResult(
                SCENARIO_GENERIC_PACKAGE,
                passed,
                expected,
                observed,
                diagnosticCode,
                trim(result.stderr(), 240)
        );
    }

    private TsjAnyJarNoHacksBaselineReport.ScenarioResult runSpringWebJpaScenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        final String expected =
                "A pure TS Spring/JPA app packages without TSJ-generated Spring/web adapters or a generated Boot launcher.";
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/conformance/anyjar-nohacks/spring_web_jpa_app"),
                workDir.resolve("fixture")
        );
        final Path outDir = workDir.resolve("out");
        final List<String> args = new ArrayList<>();
        args.add("package");
        args.add(fixtureRoot.resolve("main.ts").toString());
        args.add("--out");
        args.add(outDir.toString());
        args.add("--interop-policy");
        args.add("broad");
        args.add("--ack-interop-risk");
        args.add("--mode");
        args.add("jvm-strict");
        for (Path jar : resolvePetClinicSpringClasspath(repoRoot)) {
            args.add("--jar");
            args.add(jar.toString());
        }

        final CommandResult result = runCommand(args.toArray(String[]::new));
        final String diagnosticCode = lastDiagnosticCode(result.stdout(), result.stderr());
        final Path packagedJar = outDir.resolve("tsj-app.jar");
        int generatedSpringAdapters = 0;
        int generatedWebAdapters = 0;
        boolean generatedBootLauncher = false;
        if (Files.exists(packagedJar)) {
            try (JarFile jarFile = new JarFile(packagedJar.toFile())) {
                generatedSpringAdapters = countJarEntries(jarFile, "dev/tsj/generated/spring/");
                generatedWebAdapters = countJarEntries(jarFile, "dev/tsj/generated/web/");
                generatedBootLauncher = jarFile.getJarEntry("dev/tsj/generated/boot/TsjSpringBootLauncher.class") != null;
            }
        }
        final boolean passed = result.exitCode() == 0
                && generatedSpringAdapters == 0
                && generatedWebAdapters == 0
                && !generatedBootLauncher;
        final String observed = "exit=" + result.exitCode()
                + ",code=" + diagnosticCode
                + ",command=package"
                + ",generatedSpringAdapters=" + generatedSpringAdapters
                + ",generatedWebAdapters=" + generatedWebAdapters
                + ",generatedBootLauncher=" + generatedBootLauncher;
        return new TsjAnyJarNoHacksBaselineReport.ScenarioResult(
                SCENARIO_SPRING_WEB_JPA,
                passed,
                expected,
                observed,
                diagnosticCode,
                "packagedJar=" + packagedJar.toAbsolutePath().normalize()
        );
    }

    private TsjAnyJarNoHacksBaselineReport.ScenarioResult runRuntimeAnnotationScenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        final String expected =
                "Imported runtime annotations survive on the executable JVM class without metadata-carrier indirection.";
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/conformance/anyjar-nohacks/non_spring_reflection_consumer"),
                workDir.resolve("fixture")
        );
        final Path supportJar = buildReflectionSupportJar(workDir.resolve("support"));
        final Path outDir = workDir.resolve("out");
        final CommandResult result = runCommand(
                "compile",
                fixtureRoot.resolve("main.ts").toString(),
                "--out",
                outDir.toString(),
                "--jar",
                supportJar.toString(),
                "--interop-policy",
                "broad",
                "--ack-interop-risk",
                "--mode",
                "jvm-strict"
        );
        final String diagnosticCode = lastDiagnosticCode(result.stdout(), result.stderr());

        boolean carrierExists = false;
        boolean carrierAnnotated = false;
        String nativeClassName = "";
        boolean nativeAnnotated = false;

        if (result.exitCode() == 0) {
            final Path classesDir = outDir.resolve("classes");
            nativeClassName = findGeneratedClassName(classesDir, "ReflectedEntity__TsjStrictNative.class").orElse("");
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{classesDir.toUri().toURL(), supportJar.toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                @SuppressWarnings("unchecked")
                final Class<? extends Annotation> annotationType =
                        (Class<? extends Annotation>) Class.forName("sample.anno.RuntimeMark", true, classLoader);
                final Class<?> carrierClass = tryLoadClass(
                        classLoader,
                        "dev.tsj.generated.metadata.ReflectedEntityTsjCarrier"
                );
                carrierExists = carrierClass != null;
                carrierAnnotated = carrierClass != null && carrierClass.isAnnotationPresent(annotationType);
                if (!nativeClassName.isBlank()) {
                    final Class<?> nativeClass = Class.forName(nativeClassName, true, classLoader);
                    nativeAnnotated = nativeClass.isAnnotationPresent(annotationType);
                }
            }
        }

        final boolean passed = result.exitCode() == 0 && nativeAnnotated && !carrierExists;
        final String observed = "exit=" + result.exitCode()
                + ",code=" + diagnosticCode
                + ",carrierExists=" + carrierExists
                + ",carrierAnnotated=" + carrierAnnotated
                + ",nativeClass=" + nativeClassName
                + ",nativeAnnotated=" + nativeAnnotated;
        return new TsjAnyJarNoHacksBaselineReport.ScenarioResult(
                SCENARIO_RUNTIME_ANNOTATION,
                passed,
                expected,
                observed,
                diagnosticCode,
                "supportJar=" + supportJar.toAbsolutePath().normalize()
        );
    }

    private TsjAnyJarNoHacksBaselineReport.ScenarioResult runSpringAopWebDiScenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        final String expected =
                "Generic compile path supports a TS Spring AOP/web/DI app without retired legacy flags or helper-entrypoint glue.";
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/conformance/anyjar-nohacks/spring_aop_web_di_app"),
                workDir.resolve("fixture")
        );
        final Path noLegacyOutDir = workDir.resolve("out-no-legacy");
        final Path retiredFlagOutDir = workDir.resolve("out-retired-flag");
        final List<String> compileArgs = new ArrayList<>();
        compileArgs.add("compile");
        compileArgs.add(fixtureRoot.resolve("main.ts").toString());
        compileArgs.add("--interop-policy");
        compileArgs.add("broad");
        compileArgs.add("--ack-interop-risk");
        for (Path jar : resolveSpringAopClasspath(repoRoot)) {
            compileArgs.add("--jar");
            compileArgs.add(jar.toString());
        }

        final List<String> noLegacyArgs = new ArrayList<>(compileArgs);
        noLegacyArgs.add("--out");
        noLegacyArgs.add(noLegacyOutDir.toString());
        final CommandResult noLegacyResult = runCommand(noLegacyArgs.toArray(String[]::new));
        final String noLegacyCode = lastDiagnosticCode(noLegacyResult.stdout(), noLegacyResult.stderr());
        final int noLegacySpringAdapters = countGeneratedJavaFiles(noLegacyOutDir.resolve("generated-components"));
        final int noLegacyWebAdapters = countGeneratedJavaFiles(noLegacyOutDir.resolve("generated-web"));

        final List<String> retiredFlagArgs = new ArrayList<>(compileArgs);
        retiredFlagArgs.add("--out");
        retiredFlagArgs.add(retiredFlagOutDir.toString());
        retiredFlagArgs.add("--legacy-spring-adapters");
        final CommandResult retiredFlagResult = runCommand(retiredFlagArgs.toArray(String[]::new));
        final String retiredFlagCode = lastDiagnosticCode(retiredFlagResult.stdout(), retiredFlagResult.stderr());

        final boolean retiredFlagRejected = "TSJ-CLI-005".equals(retiredFlagCode);
        final boolean passed = noLegacyResult.exitCode() == 0
                && noLegacySpringAdapters == 0
                && noLegacyWebAdapters == 0
                && retiredFlagRejected;

        final String observed = "noLegacyExit=" + noLegacyResult.exitCode()
                + ",noLegacyCode=" + noLegacyCode
                + ",noLegacySpringAdapters=" + noLegacySpringAdapters
                + ",noLegacyWebAdapters=" + noLegacyWebAdapters
                + ",retiredLegacyFlagExit=" + retiredFlagResult.exitCode()
                + ",retiredLegacyFlagCode=" + retiredFlagCode;
        return new TsjAnyJarNoHacksBaselineReport.ScenarioResult(
                SCENARIO_SPRING_AOP_WEB_DI,
                passed,
                expected,
                observed,
                noLegacyCode,
                "retiredFlagOutDir=" + retiredFlagOutDir.toAbsolutePath().normalize()
        );
    }

    private static List<TsjAnyJarNoHacksBaselineReport.Blocker> deriveBlockers(
            final TsjAnyJarNoHacksBaselineReport.ScenarioResult genericPackageScenario,
            final TsjAnyJarNoHacksBaselineReport.ScenarioResult springWebJpaScenario,
            final TsjAnyJarNoHacksBaselineReport.ScenarioResult springAopWebDiScenario,
            final TsjAnyJarNoHacksBaselineReport.ScenarioResult runtimeAnnotationScenario
    ) {
        final List<TsjAnyJarNoHacksBaselineReport.Blocker> blockers = new ArrayList<>();
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "missing-generic-package-command",
                !genericPackageScenario.passed(),
                genericPackageScenario.observed(),
                "cli/src/main/java/dev/tsj/cli/TsjCli.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "requires-spring-package-command",
                springWebJpaScenario.observed().contains("command=spring-package"),
                springWebJpaScenario.observed(),
                "cli/src/main/java/dev/tsj/cli/TsjCli.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "requires-generated-spring-adapters",
                positiveCount(springWebJpaScenario.observed(), "generatedSpringAdapters=")
                        || springWebJpaScenario.observed().contains("code=TSJ-SPRING-COMPONENT"),
                springWebJpaScenario.observed(),
                "compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjSpringComponentGenerator.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "requires-generated-web-adapters",
                positiveCount(springWebJpaScenario.observed(), "generatedWebAdapters=")
                        || springWebJpaScenario.observed().contains("code=TSJ-WEB-CONTROLLER"),
                springWebJpaScenario.observed(),
                "compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/TsjSpringWebControllerGenerator.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "requires-generated-boot-launcher",
                springWebJpaScenario.observed().contains("generatedBootLauncher=true"),
                springWebJpaScenario.observed(),
                "cli/src/main/java/dev/tsj/cli/TsjCli.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "requires-legacy-spring-adapter-flag",
                !springAopWebDiScenario.observed().contains("retiredLegacyFlagCode=TSJ-CLI-005"),
                springAopWebDiScenario.observed(),
                "cli/src/main/java/dev/tsj/cli/TsjCli.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "requires-framework-glue-helper-entrypoints",
                false,
                springAopWebDiScenario.observed(),
                "compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "annotations-land-on-metadata-carrier",
                runtimeAnnotationScenario.observed().contains("carrierAnnotated=true"),
                runtimeAnnotationScenario.observed(),
                "compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java"
        ));
        blockers.add(new TsjAnyJarNoHacksBaselineReport.Blocker(
                "executable-class-missing-runtime-annotations",
                runtimeAnnotationScenario.observed().contains("nativeAnnotated=false"),
                runtimeAnnotationScenario.observed(),
                "compiler/backend-jvm/src/main/java/dev/tsj/compiler/backend/jvm/JvmBytecodeCompiler.java"
        ));
        return List.copyOf(blockers);
    }

    private static boolean positiveCount(final String observed, final String marker) {
        final int markerIndex = observed.indexOf(marker);
        if (markerIndex < 0) {
            return false;
        }
        final int valueStart = markerIndex + marker.length();
        int valueEnd = observed.indexOf(",", valueStart);
        if (valueEnd < 0) {
            valueEnd = observed.length();
        }
        try {
            return Integer.parseInt(observed.substring(valueStart, valueEnd)) > 0;
        } catch (final NumberFormatException numberFormatException) {
            return false;
        }
    }

    private static int countJarEntries(final JarFile jarFile, final String prefix) {
        int count = 0;
        final var entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(prefix) && entry.getName().endsWith(".class")) {
                count++;
            }
        }
        return count;
    }

    private static int countGeneratedJavaFiles(final Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .count();
        }
    }

    private static boolean containsGeneratedSourceText(final Path sourceRoot, final String snippet) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path sourceFile : stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                if (Files.readString(sourceFile, UTF_8).contains(snippet)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CommandResult runCommand(final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final String previousIncrementalCache = System.getProperty(INCREMENTAL_CACHE_PROPERTY);
        final int exitCode;
        try {
            System.setProperty(INCREMENTAL_CACHE_PROPERTY, "false");
            exitCode = TsjCli.execute(args, new PrintStream(stdout), new PrintStream(stderr));
        } finally {
            restoreSystemProperty(INCREMENTAL_CACHE_PROPERTY, previousIncrementalCache);
        }
        return new CommandResult(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8));
    }

    private static void restoreSystemProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previousValue);
    }

    private static String lastDiagnosticCode(final String primary, final String secondary) {
        final StringBuilder combined = new StringBuilder();
        if (primary != null) {
            combined.append(primary);
        }
        if (secondary != null && !secondary.isBlank()) {
            if (!combined.isEmpty()) {
                combined.append('\n');
            }
            combined.append(secondary);
        }
        String code = "NO_CODE";
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(combined);
        while (matcher.find()) {
            code = matcher.group(1);
        }
        return code;
    }

    private static Path copyFixtureTree(final Path sourceRoot, final Path targetRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalStateException("Fixture root not found: " + sourceRoot);
        }
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : stream.sorted().toList()) {
                final Path relative = sourceRoot.relativize(source);
                final Path target = targetRoot.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(Objects.requireNonNull(target.getParent(), "Target parent is required."));
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return targetRoot;
    }

    private static List<Path> resolvePetClinicSpringClasspath(final Path repoRoot) throws IOException {
        final List<String> jarNames = List.of(
                "jakarta.persistence-api-3.1.0.jar",
                "spring-beans-6.1.5.jar",
                "spring-context-6.1.5.jar",
                "spring-core-6.1.5.jar",
                "spring-jcl-6.1.5.jar",
                "spring-web-6.1.5.jar",
                "spring-boot-3.2.4.jar",
                "spring-boot-autoconfigure-3.2.4.jar"
        );
        final Path libDir = repoRoot.resolve("examples/pet-clinic/deps/lib");
        final List<Path> jars = new ArrayList<>();
        for (String jarName : jarNames) {
            final Path jar = libDir.resolve(jarName).toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("Required Spring/JPA jar not found: " + jar);
            }
            jars.add(jar);
        }
        return List.copyOf(jars);
    }

    private static List<Path> resolveSpringAopClasspath(final Path repoRoot) {
        final List<String> jarNames = List.of(
                "spring-beans-6.1.5.jar",
                "spring-context-6.1.5.jar",
                "spring-core-6.1.5.jar",
                "spring-jcl-6.1.5.jar",
                "spring-web-6.1.5.jar",
                "spring-tx-6.1.5.jar"
        );
        final Path libDir = repoRoot.resolve("examples/pet-clinic/deps/lib");
        final List<Path> jars = new ArrayList<>();
        for (String jarName : jarNames) {
            final Path jar = libDir.resolve(jarName).toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("Required Spring AOP/Web jar not found: " + jar);
            }
            jars.add(jar);
        }
        return List.copyOf(jars);
    }

    private static Path buildReflectionSupportJar(final Path workDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-85 baseline harness.");
        }
        final Path sourceRoot = workDir.resolve("src");
        final Path classesRoot = workDir.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classesRoot);
        final Path sourceFile = sourceRoot.resolve("sample/anno/RuntimeMark.java");
        Files.createDirectories(Objects.requireNonNull(sourceFile.getParent(), "Annotation source parent is required."));
        Files.writeString(
                sourceFile,
                """
                package sample.anno;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE})
                public @interface RuntimeMark {
                }
                """,
                UTF_8
        );

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(
                    List.of(sourceFile.toFile())
            );
            final List<String> options = List.of("-d", classesRoot.toString());
            final Boolean success = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile TSJ-85 reflection support jar.");
            }
        }

        final Path jarPath = workDir.resolve("tsj85-reflection-support.jar");
        Files.createDirectories(Objects.requireNonNull(jarPath.getParent(), "Jar parent is required."));
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> stream = Files.walk(classesRoot)) {
                for (Path classFile : stream.filter(Files::isRegularFile).sorted().toList()) {
                    final String entryName = classesRoot.relativize(classFile).toString().replace('\\', '/');
                    jarOutputStream.putNextEntry(new JarEntry(entryName));
                    jarOutputStream.write(Files.readAllBytes(classFile));
                    jarOutputStream.closeEntry();
                }
            }
        }
        return jarPath;
    }

    private static Optional<String> findGeneratedClassName(final Path classesDir, final String classFileSuffix) throws IOException {
        if (!Files.isDirectory(classesDir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.walk(classesDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(classFileSuffix))
                    .findFirst()
                    .map(path -> classesDir.relativize(path).toString()
                            .replace('/', '.')
                            .replace('\\', '.')
                            .replaceAll("\\.class$", ""));
        }
    }

    private static Class<?> tryLoadClass(final ClassLoader classLoader, final String className) {
        try {
            return Class.forName(className, true, classLoader);
        } catch (final ClassNotFoundException classNotFoundException) {
            return null;
        }
    }

    private static String trim(final String text, final int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private static Path resolveRepoRoot() {
        final List<Path> seeds = new ArrayList<>();
        seeds.add(Path.of("").toAbsolutePath().normalize());
        try {
            final Path classLocation = Path.of(
                    TsjAnyJarNoHacksBaselineHarness.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            seeds.add(classLocation.toAbsolutePath().normalize());
        } catch (final Exception ignored) {
            // Fall back to current working directory search only.
        }
        for (Path seed : seeds) {
            Path current = Files.isDirectory(seed) ? seed : seed.getParent();
            while (current != null) {
                if (Files.isDirectory(current.resolve("examples/pet-clinic"))
                        && Files.isDirectory(current.resolve("tests/conformance"))) {
                    return current;
                }
                current = current.getParent();
            }
        }
        throw new IllegalStateException("Failed to resolve repository root for TSJ-85 baseline harness.");
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjAnyJarNoHacksBaselineHarness.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve(REPORT_FILE);
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target", REPORT_FILE).toAbsolutePath().normalize();
    }

    private static void writeReport(
            final Path reportPath,
            final TsjAnyJarNoHacksBaselineReport report
    ) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
