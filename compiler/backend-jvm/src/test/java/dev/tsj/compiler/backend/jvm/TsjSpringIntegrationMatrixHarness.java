package dev.tsj.compiler.backend.jvm;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.tsj.runtime.TsjRuntime;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-37 spring ecosystem matrix harness.
 */
final class TsjSpringIntegrationMatrixHarness {
    private static final String UNSUPPORTED_DECORATOR_CODE = "TSJ-DECORATOR-UNSUPPORTED";
    private static final String MATRIX_REPORT_FILE = "tsj37-spring-module-matrix.json";
    private static final String VALIDATION_MODULE = "validation";
    private static final String VALIDATION_FEATURE_ID = "TSJ37A-VALIDATION";
    private static final String DATA_JDBC_MODULE = "data-jdbc";
    private static final String DATA_JDBC_FEATURE_ID = "TSJ37B-DATA-JDBC";
    private static final String ACTUATOR_MODULE = "actuator";
    private static final String ACTUATOR_BASELINE_FEATURE_ID = "TSJ37C-ACTUATOR-BASELINE";
    private static final String SECURITY_MODULE = "security";
    private static final String SECURITY_FEATURE_ID = "TSJ37D-SECURITY";

    TsjSpringIntegrationMatrixReport run(final Path reportPath) throws Exception {
        final Path fixtureRoot = resolveFixtureRoot();
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj37-matrix-work");
        Files.createDirectories(workRoot);

        final List<TsjSpringIntegrationMatrixReport.ModuleResult> modules = new ArrayList<>();
        modules.add(runWebModule(
                "web",
                "spring-boot-starter-web",
                fixtureRoot.resolve("tsj37-web-supported").resolve("main.ts"),
                workRoot.resolve("web")
        ));
        modules.add(runValidationModule(
                VALIDATION_MODULE,
                "spring-boot-starter-validation",
                fixtureRoot.resolve("tsj37-validation-supported").resolve("main.ts"),
                fixtureRoot.resolve("tsj37-validation-unsupported").resolve("main.ts"),
                workRoot.resolve("validation")
        ));
        modules.add(runDataJdbcModule(
                DATA_JDBC_MODULE,
                "spring-boot-starter-data-jdbc",
                fixtureRoot.resolve("tsj37-data-jdbc-supported").resolve("main.ts"),
                fixtureRoot.resolve("tsj37-data-jdbc-unsupported").resolve("main.ts"),
                workRoot.resolve("data-jdbc")
        ));
        modules.add(runActuatorModule(
                ACTUATOR_MODULE,
                "spring-boot-starter-actuator",
                fixtureRoot.resolve("tsj37-actuator-supported").resolve("main.ts"),
                fixtureRoot.resolve("tsj37-actuator-unsupported").resolve("main.ts"),
                workRoot.resolve("actuator")
        ));
        modules.add(runSecurityModule(
                SECURITY_MODULE,
                "spring-boot-starter-security",
                fixtureRoot.resolve("tsj37-security-supported").resolve("main.ts"),
                fixtureRoot.resolve("tsj37-security-unsupported").resolve("main.ts"),
                workRoot.resolve("security")
        ));

        final Path moduleReportPath = resolveModuleReportPath();
        final TsjSpringIntegrationMatrixReport report = new TsjSpringIntegrationMatrixReport(
                modules,
                normalizedReport,
                moduleReportPath
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjSpringIntegrationMatrixReport.ModuleResult runWebModule(
            final String module,
            final String starter,
            final Path entryFile,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(entryFile);
        try {
            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, outputDir.resolve("program"));
            final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                    entryFile,
                    compiledArtifact.className(),
                    outputDir.resolve("generated")
            );
            compileGeneratedSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

            final String generatedClassName = "dev.tsj.generated.web.WebMatrixControllerTsjController";
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final Class<?> controllerClass = Class.forName(generatedClassName, true, classLoader);
                final Object controller = controllerClass.getDeclaredConstructor().newInstance();
                final Object actual = controllerClass.getMethod("echo", Object.class).invoke(controller, "matrix");
                final Object expected = new ReferenceWebController().echo("matrix");

                final RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
                final GetMapping getMapping = controllerClass.getMethod("echo", Object.class).getAnnotation(GetMapping.class);
                final boolean matches = String.valueOf(actual).equals(String.valueOf(expected))
                        && requestMapping != null
                        && "/api".equals(requestMapping.value())
                        && getMapping != null
                        && "/echo".equals(getMapping.value());
                if (matches) {
                    return new TsjSpringIntegrationMatrixReport.ModuleResult(
                            module,
                            starter,
                            true,
                            true,
                            fixture,
                            "",
                            "TS-authored controller matches Java reference behavior for supported web subset."
                    );
                }
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        false,
                        fixture,
                        "TSJ37-WEB-MISMATCH",
                        "Generated controller behavior diverged from Java reference."
                );
            }
        } catch (final Exception exception) {
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    "TSJ37-WEB-ERROR",
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjSpringIntegrationMatrixReport.ModuleResult runValidationModule(
            final String module,
            final String starter,
            final Path supportedFixture,
            final Path unsupportedFixture,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(supportedFixture);
        try {
            final TsDecoratorModel decoratorModel = new TsDecoratorModelExtractor().extract(supportedFixture);
            final TsjValidationSubsetEvaluator evaluator = new TsjValidationSubsetEvaluator();
            final List<TsjValidationSubsetEvaluator.ValidatedMethod> methods =
                    evaluator.collectValidatedMethods(decoratorModel);
            if (methods.isEmpty()) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        false,
                        fixture,
                        VALIDATION_FEATURE_ID,
                        "No @Validated method with supported constraints was found in validation fixture."
                );
            }

            final TsjValidationSubsetEvaluator.ValidatedMethod validatedMethod = methods.getFirst();
            final Object[] validArgs = new Object[validatedMethod.parameters().size()];
            for (TsjValidationSubsetEvaluator.ParameterConstraints parameter : validatedMethod.parameters()) {
                validArgs[parameter.index()] = evaluator.validValue(parameter);
            }
            final List<TsjValidationSubsetEvaluator.ConstraintViolation> validViolations =
                    evaluator.validate(validatedMethod, validArgs);
            if (!validViolations.isEmpty()) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        false,
                        fixture,
                        VALIDATION_FEATURE_ID,
                        "Expected zero validation violations for supported valid input, but observed "
                                + validViolations.size() + "."
                );
            }

            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                    supportedFixture,
                    outputDir.resolve("program")
            );
            invokeClassMethod(
                    compiledArtifact,
                    validatedMethod.className(),
                    validatedMethod.methodName(),
                    validArgs
            );

            final ValidationParityResult parityResult = verifyValidationViolationParity(
                    evaluator,
                    validatedMethod,
                    validArgs
            );
            final UnsupportedGateResult unsupportedGate =
                    verifyValidationUnsupportedDiagnostic(unsupportedFixture);
            final boolean passed = parityResult.passed() && unsupportedGate.passed();
            if (passed) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        true,
                        fixture,
                        "",
                        "Validation subset parity passed for @NotBlank/@Size/@Min/@Max/@NotNull with deterministic "
                                + "field+message mapping; unsupported @Email remains explicitly gated."
                );
            }
            final String diagnosticCode = parityResult.passed()
                    ? unsupportedGate.diagnosticCode()
                    : parityResult.diagnosticCode();
            final String notes = parityResult.passed()
                    ? unsupportedGate.notes()
                    : parityResult.notes();
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    diagnosticCode,
                    notes
            );
        } catch (final JvmCompilationException exception) {
            final String diagnosticCode = exception.code() == null ? VALIDATION_FEATURE_ID : exception.code();
            final String message = exception.getMessage() == null
                    ? "Validation subset execution failed."
                    : exception.getMessage();
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    diagnosticCode,
                    message
            );
        } catch (final Exception exception) {
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    VALIDATION_FEATURE_ID,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjSpringIntegrationMatrixReport.ModuleResult runActuatorModule(
            final String module,
            final String starter,
            final Path supportedFixture,
            final Path unsupportedFixture,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(supportedFixture);
        try {
            final TsDecoratorModel decoratorModel = new TsDecoratorModelExtractor().extract(supportedFixture);
            final List<ActuatorReadOperation> operations = collectActuatorReadOperations(decoratorModel, supportedFixture);
            final Map<String, ExpectedActuatorOperation> expectedByEndpoint = expectedActuatorOperations();

            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                    supportedFixture,
                    outputDir.resolve("program")
            );
            final Map<String, ActuatorObservedOperation> observedByEndpoint = new LinkedHashMap<>();
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final Class<?> programClass = Class.forName(compiledArtifact.className(), true, classLoader);
                final Method invokeClass = programClass.getMethod(
                        "__tsjInvokeClass",
                        String.class,
                        String.class,
                        Object[].class,
                        Object[].class
                );
                for (ActuatorReadOperation operation : operations) {
                    final Object value = invokeActuatorOperation(invokeClass, operation);
                    final String body = TsjRuntime.toDisplayString(value);
                    final int statusCode = statusCodeForActuator(operation.endpointId(), body);
                    observedByEndpoint.put(
                            operation.endpointId(),
                            new ActuatorObservedOperation(
                                    "/actuator/" + operation.endpointId(),
                                    operation.methodName(),
                                    body,
                                    statusCode
                            )
                    );
                }
            }

            final boolean baselineParity = actuatorBaselineMatches(expectedByEndpoint, observedByEndpoint);
            final UnsupportedGateResult unsupportedGate = verifyActuatorUnsupportedDiagnostic(unsupportedFixture);
            final boolean passed = baselineParity && unsupportedGate.passed();
            if (passed) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        true,
                        fixture,
                        "",
                        "Actuator baseline parity passed for health/info/metrics; unsupported @WriteOperation "
                                + "surfaces stable TSJ-DECORATOR-UNSUPPORTED diagnostic."
                );
            }
            final String diagnosticCode = baselineParity
                    ? unsupportedGate.diagnosticCode()
                    : ACTUATOR_BASELINE_FEATURE_ID;
            final String notes = baselineParity
                    ? unsupportedGate.notes()
                    : "Actuator baseline parity mismatch for health/info/metrics operations.";
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    diagnosticCode,
                    notes
            );
        } catch (final Exception exception) {
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    ACTUATOR_BASELINE_FEATURE_ID,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjSpringIntegrationMatrixReport.ModuleResult runDataJdbcModule(
            final String module,
            final String starter,
            final Path supportedFixture,
            final Path unsupportedFixture,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(supportedFixture);
        try {
            final TsjDataJdbcSubsetEvaluator evaluator = new TsjDataJdbcSubsetEvaluator();
            final TsjDataJdbcSubsetEvaluator.DataJdbcSubset subset =
                    evaluator.analyze(new TsDecoratorModelExtractor().extract(supportedFixture), supportedFixture);
            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                    supportedFixture,
                    outputDir.resolve("program")
            );

            final Object countByStatus = invokeClassMethod(
                    compiledArtifact,
                    subset.repositoryClassName(),
                    "countByStatus",
                    new Object[]{"OPEN"}
            );
            final Object findById = invokeClassMethod(
                    compiledArtifact,
                    subset.repositoryClassName(),
                    "findById",
                    new Object[]{102}
            );

            final boolean hasReportMethod = subset.transactionalServiceMethods().contains("reportOpenCount");
            final Object serviceCount = hasReportMethod
                    ? invokeClassMethod(
                    compiledArtifact,
                    subset.serviceClassName(),
                    "reportOpenCount",
                    new Object[0]
            )
                    : null;

            final boolean baselineParity = "2".equals(TsjRuntime.toDisplayString(countByStatus))
                    && "CLOSED".equals(TsjRuntime.toDisplayString(findById))
                    && (!hasReportMethod || "2".equals(TsjRuntime.toDisplayString(serviceCount)));

            final UnsupportedGateResult unsupportedGate = verifyDataJdbcUnsupportedDiagnostic(unsupportedFixture);
            final boolean passed = baselineParity && unsupportedGate.passed();
            if (passed) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        true,
                        fixture,
                        "",
                        "Data JDBC subset parity passed for repository query-method naming flows "
                                + "(countByStatus/findById), service transactional wiring, and explicit "
                                + "@Query unsupported gate diagnostics (" + UNSUPPORTED_DECORATOR_CODE + ")."
                );
            }
            final String diagnosticCode = baselineParity ? unsupportedGate.diagnosticCode() : DATA_JDBC_FEATURE_ID;
            final String notes = baselineParity
                    ? unsupportedGate.notes()
                    : "Data JDBC subset parity mismatch for supported query-method naming flows.";
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    diagnosticCode,
                    notes
            );
        } catch (final JvmCompilationException exception) {
            final String diagnosticCode = exception.code() == null ? DATA_JDBC_FEATURE_ID : exception.code();
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    diagnosticCode,
                    exception.getMessage() == null ? "Data JDBC subset execution failed." : exception.getMessage()
            );
        } catch (final Exception exception) {
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    DATA_JDBC_FEATURE_ID,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjSpringIntegrationMatrixReport.ModuleResult runSecurityModule(
            final String module,
            final String starter,
            final Path supportedFixture,
            final Path unsupportedFixture,
            final Path outputDir
    ) {
        final String fixture = relativizeFixture(supportedFixture);
        try {
            final TsjSecuritySubsetEvaluator evaluator = new TsjSecuritySubsetEvaluator();
            final List<TsjSecuritySubsetEvaluator.SecuredRoute> routes =
                    evaluator.collectSecuredRoutes(new TsDecoratorModelExtractor().extract(supportedFixture));
            if (routes.isEmpty()) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        false,
                        fixture,
                        SECURITY_FEATURE_ID,
                        "No @GetMapping routes found for TSJ-37d security baseline fixture."
                );
            }

            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                    supportedFixture,
                    outputDir.resolve("program")
            );
            final Map<String, String> routeBodies = new LinkedHashMap<>();
            for (TsjSecuritySubsetEvaluator.SecuredRoute route : routes) {
                final Object value = invokeClassMethod(
                        compiledArtifact,
                        route.className(),
                        route.methodName(),
                        new Object[0]
                );
                routeBodies.put(route.endpointPath(), TsjRuntime.toDisplayString(value));
            }

            final Map<String, TsjSecuritySubsetEvaluator.SecuredRoute> routeByPath = new LinkedHashMap<>();
            for (TsjSecuritySubsetEvaluator.SecuredRoute route : routes) {
                routeByPath.put(route.endpointPath(), route);
            }

            final TsjSecuritySubsetEvaluator.SecuredRoute publicRoute = routeByPath.get("/secure/public");
            final TsjSecuritySubsetEvaluator.SecuredRoute adminRoute = routeByPath.get("/secure/admin");
            final TsjSecuritySubsetEvaluator.SecuredRoute supportRoute = routeByPath.get("/secure/support");
            if (publicRoute == null || adminRoute == null || supportRoute == null) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        false,
                        fixture,
                        SECURITY_FEATURE_ID,
                        "Security fixture must expose /secure/public, /secure/admin, and /secure/support routes."
                );
            }

            final boolean baselineParity =
                    evaluator.evaluateStatus(publicRoute, Set.of()) == 200
                            && "public".equals(routeBodies.get("/secure/public"))
                            && evaluator.evaluateStatus(adminRoute, Set.of()) == 401
                            && evaluator.evaluateStatus(adminRoute, Set.of("USER")) == 403
                            && evaluator.evaluateStatus(adminRoute, Set.of("ADMIN")) == 200
                            && "admin".equals(routeBodies.get("/secure/admin"))
                            && evaluator.evaluateStatus(supportRoute, Set.of("SUPPORT")) == 200
                            && evaluator.evaluateStatus(supportRoute, Set.of("USER")) == 403
                            && "support".equals(routeBodies.get("/secure/support"));

            final UnsupportedGateResult unsupportedGate = verifySecurityUnsupportedDiagnostic(unsupportedFixture);
            final boolean passed = baselineParity && unsupportedGate.passed();
            if (passed) {
                return new TsjSpringIntegrationMatrixReport.ModuleResult(
                        module,
                        starter,
                        true,
                        true,
                        fixture,
                        "",
                        "Security baseline parity passed for filter-chain + method-security subset "
                                + "(401/403/200 with @PreAuthorize hasRole/hasAnyRole); unsupported expressions "
                                + "remain explicitly diagnosed."
                );
            }

            final String diagnosticCode = baselineParity ? unsupportedGate.diagnosticCode() : SECURITY_FEATURE_ID;
            final String notes = baselineParity
                    ? unsupportedGate.notes()
                    : "Security baseline parity mismatch for supported route/auth scenarios.";
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    diagnosticCode,
                    notes
            );
        } catch (final Exception exception) {
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    true,
                    false,
                    fixture,
                    SECURITY_FEATURE_ID,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjSpringIntegrationMatrixReport.ModuleResult runUnsupportedModule(
            final String module,
            final String starter,
            final Path fixtureFile,
            final String unsupportedDecorator
    ) {
        final String fixture = relativizeFixture(fixtureFile);
        try {
            new TsDecoratorModelExtractor().extract(fixtureFile);
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    false,
                    false,
                    fixture,
                    "TSJ37-EXPECTED-UNSUPPORTED",
                    "Expected unsupported decorator @" + unsupportedDecorator + " but extraction succeeded."
            );
        } catch (final JvmCompilationException exception) {
            final boolean matched = UNSUPPORTED_DECORATOR_CODE.equals(exception.code())
                    && exception.getMessage() != null
                    && exception.getMessage().contains("@" + unsupportedDecorator);
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    false,
                    matched,
                    fixture,
                    exception.code() == null ? "" : exception.code(),
                    matched
                            ? "Module intentionally unsupported in TSJ-37 subset with stable diagnostic."
                            : "Unexpected unsupported diagnostic: " + exception.getMessage()
            );
        } catch (final Exception exception) {
            return new TsjSpringIntegrationMatrixReport.ModuleResult(
                    module,
                    starter,
                    false,
                    false,
                    fixture,
                    "TSJ37-UNSUPPORTED-ERROR",
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private static ValidationParityResult verifyValidationViolationParity(
            final TsjValidationSubsetEvaluator evaluator,
            final TsjValidationSubsetEvaluator.ValidatedMethod validatedMethod,
            final Object[] validArgs
    ) {
        int checks = 0;
        for (TsjValidationSubsetEvaluator.ParameterConstraints parameter : validatedMethod.parameters()) {
            for (TsjValidationSubsetEvaluator.ConstraintDefinition constraint : parameter.constraints()) {
                final Object[] invalidArgs = validArgs.clone();
                invalidArgs[parameter.index()] = evaluator.invalidValue(parameter, constraint);
                final List<TsjValidationSubsetEvaluator.ConstraintViolation> violations =
                        evaluator.validate(validatedMethod, invalidArgs);
                checks++;
                if (violations.isEmpty()) {
                    return new ValidationParityResult(
                            false,
                            VALIDATION_FEATURE_ID,
                            "Expected a validation violation for parameter `"
                                    + parameter.name()
                                    + "` with constraint @"
                                    + constraint.name()
                                    + ", but none were emitted."
                    );
                }
                final TsjValidationSubsetEvaluator.ConstraintViolation violation = violations.getFirst();
                if (!parameter.name().equals(violation.field())) {
                    return new ValidationParityResult(
                            false,
                            VALIDATION_FEATURE_ID,
                            "Validation field mapping mismatch for @" + constraint.name()
                                    + ": expected field `"
                                    + parameter.name()
                                    + "` but got `"
                                    + violation.field()
                                    + "`."
                    );
                }
                if (!constraint.message().equals(violation.message())) {
                    return new ValidationParityResult(
                            false,
                            VALIDATION_FEATURE_ID,
                            "Validation message mapping mismatch for @" + constraint.name()
                                    + ": expected `"
                                    + constraint.message()
                                    + "` but got `"
                                    + violation.message()
                                    + "`."
                    );
                }
            }
        }
        return new ValidationParityResult(
                true,
                "",
                "Validation parity checks passed for " + checks + " constrained argument scenarios."
        );
    }

    private static UnsupportedGateResult verifyValidationUnsupportedDiagnostic(final Path unsupportedFixture) {
        try {
            new TsDecoratorModelExtractor().extract(unsupportedFixture);
            return new UnsupportedGateResult(
                    false,
                    VALIDATION_FEATURE_ID,
                    "Expected unsupported validation decorator @Email but extraction succeeded."
            );
        } catch (final JvmCompilationException exception) {
            final boolean matched = ("TSJ-DECORATOR-PARAM".equals(exception.code())
                    || UNSUPPORTED_DECORATOR_CODE.equals(exception.code()))
                    && exception.getMessage() != null
                    && exception.getMessage().contains("@Email");
            return new UnsupportedGateResult(
                    matched,
                    exception.code() == null ? "" : exception.code(),
                    matched
                            ? "Unsupported validation decorator @Email is gated."
                            : "Unexpected unsupported validation diagnostic: " + exception.getMessage()
            );
        }
    }

    private static UnsupportedGateResult verifyDataJdbcUnsupportedDiagnostic(final Path unsupportedFixture) {
        try {
            new TsDecoratorModelExtractor().extract(unsupportedFixture);
            return new UnsupportedGateResult(
                    false,
                    DATA_JDBC_FEATURE_ID,
                    "Expected unsupported data-jdbc decorator @Query but extraction succeeded."
            );
        } catch (final JvmCompilationException exception) {
            final boolean matched = UNSUPPORTED_DECORATOR_CODE.equals(exception.code())
                    && exception.getMessage() != null
                    && exception.getMessage().contains("@Query");
            return new UnsupportedGateResult(
                    matched,
                    exception.code() == null ? "" : exception.code(),
                    matched
                            ? "Unsupported data-jdbc decorator @Query is explicitly gated in TSJ-37b subset."
                            : "Unexpected data-jdbc unsupported diagnostic: " + exception.getMessage()
            );
        }
    }

    private Object invokeClassMethod(
            final JvmCompiledArtifact compiledArtifact,
            final String className,
            final String methodName,
            final Object[] args
    ) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> programClass = Class.forName(compiledArtifact.className(), true, classLoader);
            final Method invokeClass = programClass.getMethod(
                    "__tsjInvokeClass",
                    String.class,
                    String.class,
                    Object[].class,
                    Object[].class
            );
            return invokeClassMethod(invokeClass, className, methodName, args);
        }
    }

    private static Object invokeClassMethod(
            final Method invokeClass,
            final String className,
            final String methodName,
            final Object[] args
    ) throws Exception {
        try {
            return invokeClass.invoke(
                    null,
                    className,
                    methodName,
                    new Object[0],
                    args
            );
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            if (target instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(target);
        }
    }

    private static List<ActuatorReadOperation> collectActuatorReadOperations(
            final TsDecoratorModel model,
            final Path sourceFile
    ) {
        final List<ActuatorReadOperation> operations = new ArrayList<>();
        for (TsDecoratedClass decoratedClass : model.classes()) {
            final TsDecoratorUse endpointDecorator = findDecorator(decoratedClass.decorators(), "Endpoint");
            if (endpointDecorator == null) {
                continue;
            }
            final String endpointId = parseEndpointId(endpointDecorator, decoratedClass.className(), sourceFile);
            for (TsDecoratedMethod method : decoratedClass.methods()) {
                if (method.constructor()) {
                    continue;
                }
                if (findDecorator(method.decorators(), "ReadOperation") == null) {
                    continue;
                }
                operations.add(new ActuatorReadOperation(decoratedClass.className(), endpointId, method.methodName()));
            }
        }
        if (operations.isEmpty()) {
            throw new IllegalStateException(
                    "TSJ-37c actuator fixture must declare at least one @Endpoint/@ReadOperation pair."
            );
        }
        return List.copyOf(operations);
    }

    private static TsDecoratorUse findDecorator(
            final List<TsDecoratorUse> decorators,
            final String decoratorName
    ) {
        for (TsDecoratorUse decorator : decorators) {
            if (decoratorName.equals(decorator.name())) {
                return decorator;
            }
        }
        return null;
    }

    private static String parseEndpointId(
            final TsDecoratorUse endpointDecorator,
            final String className,
            final Path sourceFile
    ) {
        final String rawArgs = endpointDecorator.rawArgs();
        if (rawArgs == null || rawArgs.trim().isEmpty()) {
            throw new JvmCompilationException(
                    "TSJ-ACTUATOR-ENDPOINT",
                    "@Endpoint on `" + className + "` requires a non-empty endpoint id.",
                    endpointDecorator.line(),
                    1,
                    sourceFile.toString(),
                    ACTUATOR_BASELINE_FEATURE_ID,
                    "Use @Endpoint(\"health\"|\"info\"|\"metrics\") in TSJ-37c subset."
            );
        }
        final String trimmed = rawArgs.trim();
        final String value;
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            value = trimmed.substring(1, trimmed.length() - 1);
        } else {
            value = trimmed;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new JvmCompilationException(
                    "TSJ-ACTUATOR-ENDPOINT",
                    "@Endpoint on `" + className + "` requires a non-empty endpoint id.",
                    endpointDecorator.line(),
                    1,
                    sourceFile.toString(),
                    ACTUATOR_BASELINE_FEATURE_ID,
                    "Use @Endpoint(\"health\"|\"info\"|\"metrics\") in TSJ-37c subset."
            );
        }
        return normalized;
    }

    private static Object invokeActuatorOperation(
            final Method invokeClass,
            final ActuatorReadOperation operation
    ) throws Exception {
        try {
            return invokeClass.invoke(
                    null,
                    operation.className(),
                    operation.methodName(),
                    new Object[0],
                    new Object[0]
            );
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            if (target instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(target);
        }
    }

    private static int statusCodeForActuator(final String endpointId, final String body) {
        if ("health".equals(endpointId)) {
            return "UP".equalsIgnoreCase(body.trim()) ? 200 : 503;
        }
        return 200;
    }

    private static Map<String, ExpectedActuatorOperation> expectedActuatorOperations() {
        final Map<String, ExpectedActuatorOperation> expected = new LinkedHashMap<>();
        expected.put("health", new ExpectedActuatorOperation("/actuator/health", "health", "UP", 200));
        expected.put("info", new ExpectedActuatorOperation("/actuator/info", "info", "tsj-actuator", 200));
        expected.put("metrics", new ExpectedActuatorOperation("/actuator/metrics", "summary", "42", 200));
        return Map.copyOf(expected);
    }

    private static boolean actuatorBaselineMatches(
            final Map<String, ExpectedActuatorOperation> expectedByEndpoint,
            final Map<String, ActuatorObservedOperation> observedByEndpoint
    ) {
        if (!observedByEndpoint.keySet().containsAll(expectedByEndpoint.keySet())) {
            return false;
        }
        for (Map.Entry<String, ExpectedActuatorOperation> entry : expectedByEndpoint.entrySet()) {
            final String endpointId = entry.getKey();
            final ExpectedActuatorOperation expected = entry.getValue();
            final ActuatorObservedOperation observed = observedByEndpoint.get(endpointId);
            if (observed == null) {
                return false;
            }
            if (!expected.path().equals(observed.path())) {
                return false;
            }
            if (!expected.methodName().equals(observed.methodName())) {
                return false;
            }
            if (!expected.body().equals(observed.body())) {
                return false;
            }
            if (expected.statusCode() != observed.statusCode()) {
                return false;
            }
        }
        return true;
    }

    private static UnsupportedGateResult verifyActuatorUnsupportedDiagnostic(final Path unsupportedFixture) {
        try {
            new TsDecoratorModelExtractor().extract(unsupportedFixture);
            return new UnsupportedGateResult(
                    false,
                    "TSJ37C-ACTUATOR-UNSUPPORTED-GATE",
                    "Expected unsupported decorator @WriteOperation but extraction succeeded."
            );
        } catch (final JvmCompilationException exception) {
            final boolean matched = UNSUPPORTED_DECORATOR_CODE.equals(exception.code())
                    && exception.getMessage() != null
                    && exception.getMessage().contains("@WriteOperation");
            return new UnsupportedGateResult(
                    matched,
                    exception.code() == null ? "" : exception.code(),
                    matched
                            ? "Unsupported actuator operation decorator @WriteOperation is gated."
                            : "Unexpected unsupported diagnostic: " + exception.getMessage()
            );
        }
    }

    private static UnsupportedGateResult verifySecurityUnsupportedDiagnostic(final Path unsupportedFixture) {
        try {
            final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(unsupportedFixture);
            new TsjSecuritySubsetEvaluator().collectSecuredRoutes(model);
            return new UnsupportedGateResult(
                    false,
                    SECURITY_FEATURE_ID,
                    "Expected unsupported security expression diagnostic but extraction/evaluation succeeded."
            );
        } catch (final JvmCompilationException exception) {
            final boolean matched = UNSUPPORTED_DECORATOR_CODE.equals(exception.code())
                    && SECURITY_FEATURE_ID.equals(exception.featureId())
                    && exception.getMessage() != null
                    && exception.getMessage().contains("Unsupported security expression");
            return new UnsupportedGateResult(
                    matched,
                    exception.code() == null ? "" : exception.code(),
                    matched
                            ? "Unsupported security expression is explicitly gated in TSJ-37d subset."
                            : "Unexpected security unsupported diagnostic: " + exception.getMessage()
            );
        }
    }

    private record ActuatorReadOperation(
            String className,
            String endpointId,
            String methodName
    ) {
    }

    private record ExpectedActuatorOperation(
            String path,
            String methodName,
            String body,
            int statusCode
    ) {
    }

    private record ActuatorObservedOperation(
            String path,
            String methodName,
            String body,
            int statusCode
    ) {
    }

    private record ValidationParityResult(
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
    }

    private record UnsupportedGateResult(
            boolean passed,
            String diagnosticCode,
            String notes
    ) {
    }

    private static void compileGeneratedSources(final List<Path> sourceFiles, final Path outputDir) throws Exception {
        if (sourceFiles.isEmpty()) {
            return;
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.isBlank()) {
                classPath = outputDir.toString();
            } else {
                classPath = classPath + java.io.File.pathSeparator + outputDir;
            }
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-classpath",
                    classPath,
                    "-d",
                    outputDir.toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile generated web adapter sources: " + diagnostics.getDiagnostics());
            }
        }
    }

    private static Path resolveFixtureRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            final Path candidate = current.resolve("tests").resolve("spring-matrix");
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve tests/spring-matrix fixture root.");
    }

    private static String relativizeFixture(final Path fixtureFile) {
        final Path absoluteFixture = fixtureFile.toAbsolutePath().normalize();
        final Path fixtureRoot = resolveFixtureRoot();
        try {
            return fixtureRoot.getParent().relativize(absoluteFixture).toString().replace('\\', '/');
        } catch (final IllegalArgumentException ignored) {
            return absoluteFixture.toString().replace('\\', '/');
        }
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSpringIntegrationMatrixHarness.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve(MATRIX_REPORT_FILE);
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target", MATRIX_REPORT_FILE).toAbsolutePath().normalize();
    }

    private static void writeReport(final Path reportPath, final TsjSpringIntegrationMatrixReport report) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toJson() + "\n", UTF_8);
    }

    @RestController
    @RequestMapping("/api")
    private static final class ReferenceWebController {
        @GetMapping("/echo")
        public Object echo(final Object value) {
            return "echo:" + value;
        }
    }
}
