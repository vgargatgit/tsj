package dev.tsj.compiler.backend.jvm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-39c metadata parity certification harness.
 */
final class TsjMetadataParityCertificationHarness {
    private static final String REPORT_FILE = "tsj39c-metadata-parity-certification.json";
    private static final String UNSUPPORTED_DIAGNOSTIC = "TSJ39B-INTROSPECTOR-UNSUPPORTED";

    TsjMetadataParityCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj39c-certification-work");
        Files.createDirectories(workRoot);

        final List<TsjMetadataParityCertificationReport.FamilyResult> classFamilies =
                evaluateGeneratedClassFamilies(workRoot.resolve("generated-class-families"));
        final TsjIntrospectorCompatibilityMatrixReport matrixReport =
                new TsjIntrospectorCompatibilityMatrixHarness().run(
                        workRoot.resolve("tsj39b-introspector-matrix.json")
                );
        final List<TsjMetadataParityCertificationReport.IntrospectorResult> introspectorScenarios =
                mapIntrospectorScenarios(matrixReport.scenarios());

        final boolean classFamiliesPassed = classFamilies.stream()
                .allMatch(TsjMetadataParityCertificationReport.FamilyResult::passed);
        final boolean supportedScenariosPassed = introspectorScenarios.stream()
                .filter(TsjMetadataParityCertificationReport.IntrospectorResult::supported)
                .allMatch(TsjMetadataParityCertificationReport.IntrospectorResult::passed);
        final boolean unsupportedDiagnosticsStable = introspectorScenarios.stream()
                .filter(result -> !result.supported())
                .allMatch(result -> result.passed() && UNSUPPORTED_DIAGNOSTIC.equals(result.diagnosticCode()));
        final boolean gatePassed = classFamiliesPassed && supportedScenariosPassed && unsupportedDiagnosticsStable;

        final TsjMetadataParityCertificationReport report = new TsjMetadataParityCertificationReport(
                gatePassed,
                classFamilies,
                introspectorScenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static List<TsjMetadataParityCertificationReport.IntrospectorResult> mapIntrospectorScenarios(
            final List<TsjIntrospectorCompatibilityMatrixReport.ScenarioResult> scenarios
    ) {
        final List<TsjMetadataParityCertificationReport.IntrospectorResult> results = new ArrayList<>();
        for (TsjIntrospectorCompatibilityMatrixReport.ScenarioResult scenario : scenarios) {
            results.add(new TsjMetadataParityCertificationReport.IntrospectorResult(
                    scenario.scenario(),
                    scenario.fixture(),
                    scenario.library(),
                    scenario.version(),
                    scenario.supported(),
                    scenario.passed(),
                    scenario.diagnosticCode(),
                    scenario.notes()
            ));
        }
        return results;
    }

    private List<TsjMetadataParityCertificationReport.FamilyResult> evaluateGeneratedClassFamilies(
            final Path workDir
    ) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("metadata-certification.ts");
            Files.writeString(
                    entryFile,
                    """
                    @Service
                    class BillingService {
                      @Transactional
                      charge(amount: any) {
                        return amount;
                      }
                    }

                    @RestController
                    @RequestMapping("/api")
                    class EchoController {
                      @GetMapping("/echo")
                      echo(@RequestParam("q") query: any) {
                        return query;
                      }
                    }
                    """,
                    UTF_8
            );

            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                    entryFile,
                    workDir.resolve("program")
            );
            final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                    entryFile,
                    compiledArtifact.className(),
                    workDir.resolve("generated-components")
            );
            final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                    entryFile,
                    compiledArtifact.className(),
                    workDir.resolve("generated-web")
            );
            final Path interopSpec = workDir.resolve("interop.properties");
            Files.writeString(
                    interopSpec,
                    """
                    allowlist=java.lang.Math#max
                    targets=java.lang.Math#max
                    classAnnotations=java.lang.Deprecated
                    """,
                    UTF_8
            );
            final InteropBridgeArtifact bridgeArtifact = new InteropBridgeGenerator().generate(
                    interopSpec,
                    workDir.resolve("generated-interop")
            );

            final List<Path> generatedSources = new ArrayList<>();
            generatedSources.addAll(componentArtifact.sourceFiles());
            generatedSources.addAll(controllerArtifact.sourceFiles());
            generatedSources.addAll(bridgeArtifact.sourceFiles());
            compileSources(generatedSources, compiledArtifact.outputDirectory());

            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final Class<?> programClass = Class.forName(compiledArtifact.className(), true, classLoader);
                final Method programMain = programClass.getMethod("main", String[].class);
                final boolean programParamNames = programMain.getParameters()[0].isNamePresent()
                        && "args".equals(programMain.getParameters()[0].getName());

                final Class<?> componentClass = Class.forName(
                        "dev.tsj.generated.spring.BillingServiceTsjComponent",
                        true,
                        classLoader
                );
                final Method chargeMethod = componentClass.getMethod("charge", Object.class);
                final boolean componentParamNames = chargeMethod.getParameters()[0].isNamePresent()
                        && "amount".equals(chargeMethod.getParameters()[0].getName());
                final boolean componentAnnotations = componentClass.isAnnotationPresent(Service.class)
                        && chargeMethod.isAnnotationPresent(Transactional.class);

                final Class<?> proxyClass = Class.forName(
                        "dev.tsj.generated.spring.BillingServiceTsjComponentApi",
                        true,
                        classLoader
                );
                final Method proxyCharge = proxyClass.getMethod("charge", Object.class);
                final boolean proxyParamNames = proxyCharge.getParameters()[0].isNamePresent()
                        && "amount".equals(proxyCharge.getParameters()[0].getName());

                final Class<?> webControllerClass = Class.forName(
                        "dev.tsj.generated.web.EchoControllerTsjController",
                        true,
                        classLoader
                );
                final Method echoMethod = webControllerClass.getMethod("echo", Object.class);
                final Parameter echoParameter = echoMethod.getParameters()[0];
                final RequestParam requestParam = echoParameter.getAnnotation(RequestParam.class);
                final boolean webParamNames = echoParameter.isNamePresent()
                        && "query".equals(echoParameter.getName());
                final boolean webAnnotations = webControllerClass.isAnnotationPresent(RestController.class)
                        && requestParam != null
                        && "q".equals(requestParam.value());

                final Path bridgeSource = bridgeArtifact.sourceFiles().getFirst();
                final String bridgeSimpleName = bridgeSource.getFileName().toString().replace(".java", "");
                final Class<?> bridgeClass = Class.forName(
                        "dev.tsj.generated.interop." + bridgeSimpleName,
                        true,
                        classLoader
                );
                final boolean bridgeParamNames = allPublicMethodParameterNamesPresent(bridgeClass);
                final boolean bridgeAnnotations = bridgeClass.isAnnotationPresent(Deprecated.class);

                return List.of(
                        familyResult(
                                "program",
                                programParamNames,
                                true,
                                "Program class retains main parameter metadata."
                        ),
                        familyResult(
                                "component",
                                componentParamNames,
                                componentAnnotations,
                                "Generated Spring component retains parameter and decorator metadata."
                        ),
                        familyResult(
                                "proxy",
                                proxyParamNames,
                                true,
                                "Generated transactional proxy API retains parameter metadata."
                        ),
                        familyResult(
                                "web-controller",
                                webParamNames,
                                webAnnotations,
                                "Generated web controller retains request-parameter and controller metadata."
                        ),
                        familyResult(
                                "interop-bridge",
                                bridgeParamNames,
                                bridgeAnnotations,
                                "Generated interop bridge retains parameter and class-annotation metadata."
                        )
                );
            }
        } catch (final Exception exception) {
            return failureFamilies(exception);
        }
    }

    private static TsjMetadataParityCertificationReport.FamilyResult familyResult(
            final String family,
            final boolean parameterMetadataPresent,
            final boolean annotationMetadataPresent,
            final String notes
    ) {
        return new TsjMetadataParityCertificationReport.FamilyResult(
                family,
                parameterMetadataPresent && annotationMetadataPresent,
                parameterMetadataPresent,
                annotationMetadataPresent,
                notes
        );
    }

    private static List<TsjMetadataParityCertificationReport.FamilyResult> failureFamilies(final Exception exception) {
        final String notes = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return List.of(
                new TsjMetadataParityCertificationReport.FamilyResult("program", false, false, false, notes),
                new TsjMetadataParityCertificationReport.FamilyResult("component", false, false, false, notes),
                new TsjMetadataParityCertificationReport.FamilyResult("proxy", false, false, false, notes),
                new TsjMetadataParityCertificationReport.FamilyResult("web-controller", false, false, false, notes),
                new TsjMetadataParityCertificationReport.FamilyResult("interop-bridge", false, false, false, notes)
        );
    }

    private static boolean allPublicMethodParameterNamesPresent(final Class<?> type) {
        boolean sawPublicMethod = false;
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            sawPublicMethod = true;
            for (Parameter parameter : method.getParameters()) {
                if (!parameter.isNamePresent()) {
                    return false;
                }
            }
        }
        return sawPublicMethod;
    }

    private static void compileSources(final List<Path> sourceFiles, final Path classesDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.isBlank()) {
                classPath = classesDir.toString();
            } else {
                classPath = classPath + java.io.File.pathSeparator + classesDir;
            }
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-classpath",
                    classPath,
                    "-d",
                    classesDir.toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Generated source compile failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjMetadataParityCertificationHarness.class
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
            final TsjMetadataParityCertificationReport report
    ) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toJson() + "\n", UTF_8);
    }
}
