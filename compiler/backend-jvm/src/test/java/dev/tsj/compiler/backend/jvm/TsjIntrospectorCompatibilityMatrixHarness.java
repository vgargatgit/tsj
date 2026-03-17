package dev.tsj.compiler.backend.jvm;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-39b third-party introspector compatibility matrix harness.
 */
final class TsjIntrospectorCompatibilityMatrixHarness {
    private static final String REPORT_FILE = "tsj39b-introspector-matrix.json";
    private static final String UNSUPPORTED_CODE = "TSJ39B-INTROSPECTOR-UNSUPPORTED";
    private static final String MISMATCH_CODE = "TSJ39B-INTROSPECTOR-MISMATCH";
    private static final String UNKNOWN_SCENARIO_CODE = "TSJ39B-INTROSPECTOR-UNKNOWN";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    TsjIntrospectorCompatibilityMatrixReport run(final Path reportPath) throws Exception {
        final Path repoRoot = resolveRepoRoot();
        final Path fixtureRoot = repoRoot.resolve("tests").resolve("introspector-matrix");
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj39b-introspector-work");
        Files.createDirectories(workRoot);

        final List<Path> fixtureFiles;
        try (java.util.stream.Stream<Path> paths = Files.walk(fixtureRoot, 2)) {
            fixtureFiles = paths
                    .filter(path -> path.getFileName().toString().equals("fixture.properties"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        final List<TsjIntrospectorCompatibilityMatrixReport.ScenarioResult> scenarios = new ArrayList<>();
        for (Path fixtureFile : fixtureFiles) {
            final Properties properties = loadProperties(fixtureFile);
            final String scenario = requiredProperty(properties, "scenario", fixtureFile);
            final String library = requiredProperty(properties, "library", fixtureFile);
            final String version = requiredProperty(properties, "version", fixtureFile);
            final boolean supported = Boolean.parseBoolean(requiredProperty(properties, "supported", fixtureFile));
            final String guidance = properties.getProperty(
                    "guidance",
                    "Use documented TSJ-supported introspector scenarios or fallback to reflection-based metadata reads."
            );
            final String fixture = repoRoot.relativize(fixtureFile).toString().replace('\\', '/');
            final Path scenarioWorkDir = workRoot.resolve(fixtureFile.getParent().getFileName().toString());
            Files.createDirectories(scenarioWorkDir);

            scenarios.add(runScenario(
                    repoRoot,
                    fixture,
                    scenario,
                    library,
                    version,
                    supported,
                    guidance,
                    properties,
                    scenarioWorkDir
            ));
        }

        final Path moduleReportPath = resolveModuleReportPath();
        final TsjIntrospectorCompatibilityMatrixReport report = new TsjIntrospectorCompatibilityMatrixReport(
                scenarios,
                normalizedReport,
                moduleReportPath
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runScenario(
            final Path repoRoot,
            final String fixture,
            final String scenario,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Properties properties,
            final Path workDir
    ) {
        return switch (scenario) {
            case "strict-spring-web-executable-introspection" -> runStrictSpringWebExecutableScenario(
                    repoRoot,
                    fixture,
                    library,
                    version,
                    supported,
                    guidance,
                    properties,
                    workDir
            );
            case "jackson-executable-dto-introspection" -> runJacksonExecutableDtoScenario(
                    repoRoot,
                    fixture,
                    library,
                    version,
                    supported,
                    guidance,
                    properties,
                    workDir
            );
            case "hibernate-executable-entity-introspection" -> runHibernateExecutableEntityScenario(
                    repoRoot,
                    fixture,
                    library,
                    version,
                    supported,
                    guidance,
                    properties,
                    workDir
            );
            case "validation-executable-dto-introspection" -> runValidationExecutableDtoScenario(
                    repoRoot,
                    fixture,
                    library,
                    version,
                    supported,
                    guidance,
                    properties,
                    workDir
            );
            case "jackson-unsupported" -> runUnsupportedScenario(
                    fixture,
                    library,
                    version,
                    supported,
                    guidance
            );
            default -> new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    scenario,
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    UNKNOWN_SCENARIO_CODE,
                    "Use documented scenarios: strict-spring-web-executable-introspection, jackson-executable-dto-introspection, "
                            + "validation-executable-dto-introspection, hibernate-executable-entity-introspection, "
                            + "jackson-unsupported.",
                    "Unknown introspector scenario in fixture."
            );
        };
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runStrictSpringWebExecutableScenario(
            final Path repoRoot,
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Properties properties,
            final Path workDir
    ) {
        try {
            final String entryValue = requiredProperty(properties, "entry", Path.of(fixture));
            final Path entryFile = repoRoot.resolve(entryValue).toAbsolutePath().normalize();
            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                    entryFile,
                    workDir.resolve("program"),
                    JvmOptimizationOptions.defaults(),
                    JvmBytecodeCompiler.BackendMode.JVM_STRICT
            );

            final String strictClassName = "dev.tsj.generated.StrictWebMatrixController";
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final Class<?> controllerClass = Class.forName(strictClassName, true, classLoader);
                final Method method = controllerClass.getMethod("echo", String.class);
                final RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
                final GetMapping getMapping = method.getAnnotation(GetMapping.class);
                final RequestParam requestParam = method.getParameters()[0].getAnnotation(RequestParam.class);
                final boolean passed = requestMapping != null
                        && "/api".equals(requestMapping.value())
                        && getMapping != null
                        && "/echo".equals(getMapping.value())
                        && method.getParameterCount() == 1
                        && method.getParameters()[0].isNamePresent()
                        && "value".equals(method.getParameters()[0].getName())
                        && requestParam != null
                        && "value".equals(requestParam.value());
                return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                        "strict-spring-web-executable-introspection",
                        fixture,
                        library,
                        version,
                        supported,
                        passed,
                        passed ? "" : MISMATCH_CODE,
                        guidance,
                        passed
                                ? "Spring-web-style annotation introspection reads route and parameter metadata directly from the strict executable class."
                                : "Spring-web-style annotation introspection observed metadata mismatch on the strict executable class."
                );
            }
        } catch (final Exception exception) {
            return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    "strict-spring-web-executable-introspection",
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    MISMATCH_CODE,
                    guidance,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runJacksonExecutableDtoScenario(
            final Path repoRoot,
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Properties properties,
            final Path workDir
    ) {
        try {
            final String entryValue = requiredProperty(properties, "entry", Path.of(fixture));
            final Path entryFile = repoRoot.resolve(entryValue).toAbsolutePath().normalize();
            final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                    entryFile,
                    workDir.resolve("program"),
                    JvmOptimizationOptions.defaults(),
                    JvmBytecodeCompiler.BackendMode.JVM_STRICT
            );

            final String className = "dev.tsj.generated.JacksonMatrixPerson";
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final Class<?> dtoClass = Class.forName(className, true, classLoader);
                final Object dto = dtoClass.getDeclaredConstructor().newInstance();
                dtoClass.getDeclaredMethod("setId", String.class).invoke(dto, "42");
                dtoClass.getDeclaredMethod("setName", String.class).invoke(dto, "Ada");

                final String json = OBJECT_MAPPER.writeValueAsString(dto);
                final Object rebound = OBJECT_MAPPER.readValue(json, dtoClass);
                final String reboundId = String.valueOf(dtoClass.getDeclaredMethod("getId").invoke(rebound));
                final String reboundName = String.valueOf(dtoClass.getDeclaredMethod("getName").invoke(rebound));

                final boolean passed = json.contains("\"person_id\":\"42\"")
                        && json.contains("\"display_name\":\"Ada\"")
                        && "42".equals(reboundId)
                        && "Ada".equals(reboundName);
                return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                        "jackson-executable-dto-introspection",
                        fixture,
                        library,
                        version,
                        supported,
                        passed,
                        passed ? "" : MISMATCH_CODE,
                        guidance,
                        passed
                                ? "Jackson serializes and deserializes the strict executable DTO using imported annotation metadata."
                                : "Jackson observed a metadata or binding mismatch on the strict executable DTO."
                );
            }
        } catch (final Exception exception) {
            return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    "jackson-executable-dto-introspection",
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    MISMATCH_CODE,
                    guidance,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runValidationExecutableDtoScenario(
            final Path repoRoot,
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Properties properties,
            final Path workDir
    ) {
        try {
            final String entryValue = requiredProperty(properties, "entry", Path.of(fixture));
            final Path entryFile = repoRoot.resolve(entryValue).toAbsolutePath().normalize();
            final JvmCompiledArtifact strictArtifact = new JvmBytecodeCompiler().compile(
                    entryFile,
                    workDir.resolve("strict-program"),
                    JvmOptimizationOptions.defaults(),
                    JvmBytecodeCompiler.BackendMode.JVM_STRICT
            );

            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{strictArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            );
                 ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class)
                         .configure()
                         .messageInterpolator(new ParameterMessageInterpolator())
                         .buildValidatorFactory()) {
                final Class<?> dtoClass = Class.forName(
                        "dev.tsj.generated.ValidationMatrixPerson",
                        true,
                        classLoader
                );
                final Object dto = dtoClass.getDeclaredConstructor().newInstance();
                final Validator validator = validatorFactory.getValidator();

                final List<String> invalidViolations = describeViolations(validator.validate(dto));
                dtoClass.getMethod("setName", String.class).invoke(dto, "Ada");
                dtoClass.getMethod("setAlias", String.class).invoke(dto, "adalove");
                final List<String> reboundViolations = describeViolations(validator.validate(dto));

                final boolean passed = invalidViolations.equals(List.of(
                        "alias=person.alias.length",
                        "name=person.name.required"
                )) && reboundViolations.isEmpty();
                final String notes = passed
                        ? "Bean Validation discovers imported field constraints directly on the strict executable DTO."
                        : "Bean Validation mismatch: invalid=" + invalidViolations + ", rebound=" + reboundViolations;
                return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                        "validation-executable-dto-introspection",
                        fixture,
                        library,
                        version,
                        supported,
                        passed,
                        passed ? "" : MISMATCH_CODE,
                        guidance,
                        notes
                );
            }
        } catch (final Exception exception) {
            return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    "validation-executable-dto-introspection",
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    MISMATCH_CODE,
                    guidance,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runHibernateExecutableEntityScenario(
            final Path repoRoot,
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance,
            final Properties properties,
            final Path workDir
    ) {
        StandardServiceRegistry serviceRegistry = null;
        try {
            final String entryValue = requiredProperty(properties, "entry", Path.of(fixture));
            final Path entryFile = repoRoot.resolve(entryValue).toAbsolutePath().normalize();
            final JvmCompiledArtifact strictArtifact = new JvmBytecodeCompiler().compile(
                    entryFile,
                    workDir.resolve("strict-program"),
                    JvmOptimizationOptions.defaults(),
                    JvmBytecodeCompiler.BackendMode.JVM_STRICT
            );

            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{strictArtifact.outputDirectory().toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                final BootstrapServiceRegistry bootstrapServiceRegistry = new BootstrapServiceRegistryBuilder()
                        .applyClassLoader(classLoader)
                        .build();
                serviceRegistry = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                        .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                        .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                        .build();
                final Class<?> entityClass = Class.forName(
                        "dev.tsj.generated.HibernateMatrixPerson",
                        true,
                        classLoader
                );
                final org.hibernate.boot.Metadata metadata = new MetadataSources(serviceRegistry)
                        .addAnnotatedClass(entityClass)
                        .buildMetadata();
                final PersistentClass entityBinding = metadata.getEntityBindings().stream()
                        .filter(binding -> entityClass.getName().equals(binding.getClassName()))
                        .findFirst()
                        .orElse(null);
                final Property idProperty = entityBinding == null ? null : entityBinding.getIdentifierProperty();
                final Property nameProperty = entityBinding == null ? null : entityBinding.getProperty("name");
                final List<String> nameColumns = nameProperty == null
                        ? List.of()
                        : nameProperty.getColumns().stream()
                                .map(column -> column.getName())
                                .sorted()
                                .toList();

                final boolean passed = entityBinding != null
                        && "people".equals(entityBinding.getTable().getName())
                        && idProperty != null
                        && "id".equals(idProperty.getName())
                        && nameColumns.equals(List.of("display_name"));
                final String notes = passed
                        ? "Hibernate metadata bootstrap discovers imported JPA annotations directly on the strict executable entity."
                        : "Hibernate metadata mismatch: entity="
                                + (entityBinding == null ? "null" : entityBinding.getEntityName())
                                + ", table="
                                + (entityBinding == null ? "null" : entityBinding.getTable().getName())
                                + ", id="
                                + (idProperty == null ? "null" : idProperty.getName())
                                + ", columns="
                                + nameColumns;
                return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                        "hibernate-executable-entity-introspection",
                        fixture,
                        library,
                        version,
                        supported,
                        passed,
                        passed ? "" : MISMATCH_CODE,
                        guidance,
                        notes
                );
            }
        } catch (final Exception exception) {
            return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                    "hibernate-executable-entity-introspection",
                    fixture,
                    library,
                    version,
                    supported,
                    false,
                    MISMATCH_CODE,
                    guidance,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        } finally {
            if (serviceRegistry != null) {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
            }
        }
    }

    private static List<String> describeViolations(final Set<? extends jakarta.validation.ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + "=" + violation.getMessage())
                .sorted()
                .toList();
    }

    private static TsjIntrospectorCompatibilityMatrixReport.ScenarioResult runUnsupportedScenario(
            final String fixture,
            final String library,
            final String version,
            final boolean supported,
            final String guidance
    ) {
        return new TsjIntrospectorCompatibilityMatrixReport.ScenarioResult(
                "jackson-unsupported",
                fixture,
                library,
                version,
                supported,
                true,
                UNSUPPORTED_CODE,
                guidance,
                "Unsupported introspector scenario is explicitly diagnosed with actionable fallback guidance."
        );
    }

    private static Properties loadProperties(final Path fixtureFile) throws IOException {
        final Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(fixtureFile)) {
            properties.load(input);
        }
        return properties;
    }

    private static String requiredProperty(
            final Properties properties,
            final String key,
            final Path fixtureFile
    ) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required fixture property `" + key + "` in " + fixtureFile);
        }
        return value.trim();
    }

    private static void compileSources(final List<Path> sourceFiles, final Path classesDir) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable.");
        }
        Files.createDirectories(classesDir);
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

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("docs").resolve("stories.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve repository root for TSJ-39b harness.");
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjIntrospectorCompatibilityMatrixHarness.class
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
            final TsjIntrospectorCompatibilityMatrixReport report
    ) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toJson() + "\n", UTF_8);
    }
}
