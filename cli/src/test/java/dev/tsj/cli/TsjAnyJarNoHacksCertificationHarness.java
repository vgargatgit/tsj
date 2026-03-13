package dev.tsj.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.logging.LogFactory;
import org.h2.Driver;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.SpringVersion;
import org.springframework.expression.Expression;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-92 final no-hacks any-jar certification harness.
 */
final class TsjAnyJarNoHacksCertificationHarness {
    private static final String REPORT_FILE = "tsj92-anyjar-nohacks-certification.json";
    private static final String FIXTURE_VERSION = "tsj92-anyjar-nohacks-2026.03";
    private static final String INCREMENTAL_CACHE_PROPERTY = "tsj.backend.incrementalCache";
    private static final String SPRING_CONTEXT_CLASS = "org.springframework.context.annotation.AnnotationConfigApplicationContext";
    private static final String SPRING_REPOSITORY = "org.springframework.stereotype.Repository";
    private static final String SPRING_SERVICE = "org.springframework.stereotype.Service";
    private static final String SPRING_REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String SPRING_REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String SPRING_GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private static final String SPRING_POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private static final String SPRING_REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam";
    private static final String SPRING_REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody";
    private static final String SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final List<String> SPRING_CHILD_FIRST_PREFIXES = List.of(
            "dev.tsj.generated.",
            "org.springframework.",
            "org.aopalliance."
    );

    TsjAnyJarNoHacksCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj92-anyjar-nohacks-work");
        Files.createDirectories(workRoot);

        final Path repoRoot = resolveRepoRoot();
        final List<TsjAnyJarNoHacksCertificationReport.ScenarioResult> scenarios = List.of(
                runBaselineScenario(workRoot.resolve("baseline")),
                runSpringWebDiPackageRuntimeScenario(workRoot.resolve("spring-web-di"), repoRoot),
                runSpringAopTransactionProxyScenario(workRoot.resolve("spring-aop"), repoRoot),
                runHibernateJpaH2Scenario(workRoot.resolve("hibernate-h2"), repoRoot),
                runJacksonExecutableDtoScenario(workRoot.resolve("jackson"), repoRoot),
                runValidationExecutableDtoScenario(workRoot.resolve("validation"), repoRoot),
                runNonSpringReflectionConsumerScenario(workRoot.resolve("reflection-consumer"))
        );

        final boolean gatePassed = scenarios.stream().allMatch(TsjAnyJarNoHacksCertificationReport.ScenarioResult::passed);
        final TsjAnyJarNoHacksCertificationReport report = new TsjAnyJarNoHacksCertificationReport(
                gatePassed,
                FIXTURE_VERSION,
                scenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private static TsjAnyJarNoHacksCertificationReport.ScenarioResult runBaselineScenario(final Path workDir) throws Exception {
        Files.createDirectories(workDir);
        final TsjAnyJarNoHacksBaselineReport baseline = new TsjAnyJarNoHacksBaselineHarness().run(
                workDir.resolve("tsj85-anyjar-nohacks-baseline.json")
        );
        final long blockerCount = baseline.blockers().stream().filter(TsjAnyJarNoHacksBaselineReport.Blocker::present).count();
        return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                "tsj85-baseline",
                baseline.gatePassed(),
                "gatePassed=" + baseline.gatePassed() + ",blockers=" + blockerCount,
                baseline.reportPath().toAbsolutePath().normalize().toString()
        );
    }

    private static TsjAnyJarNoHacksCertificationReport.ScenarioResult runSpringWebDiPackageRuntimeScenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/conformance/anyjar-nohacks/spring_web_jpa_app"),
                workDir.resolve("fixture")
        );
        final Path outDir = workDir.resolve("out");
        final CommandResult packageResult = runCommand(
                "package",
                fixtureRoot.resolve("main.ts").toString(),
                "--out",
                outDir.toString(),
                "--mode",
                "jvm-strict",
                "--interop-policy",
                "broad",
                "--ack-interop-risk",
                "--jar",
                jarPathForClass(Entity.class).toString(),
                "--jar",
                jarPathForClass(ApplicationContext.class).toString(),
                "--jar",
                jarPathForClass(MediaType.class).toString()
        );
        if (packageResult.exitCode() != 0) {
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "spring-web-di-package-runtime",
                    false,
                    "packageExit=" + packageResult.exitCode() + ",stderr=" + trim(packageResult.stderr(), 220),
                    outDir.resolve("tsj-app.jar").toAbsolutePath().normalize().toString()
            );
        }

        final Path packagedJar = outDir.resolve("tsj-app.jar");
        try (URLClassLoader classLoader = createChildFirstClassLoader(
                List.of(packagedJar),
                SPRING_CHILD_FIRST_PREFIXES
        )) {
            final String repositoryClassName = resolveGeneratedClassName(outDir, "OwnerRepository__TsjStrictNative");
            final String serviceClassName = resolveGeneratedClassName(outDir, "ClinicService__TsjStrictNative");
            final String controllerClassName = resolveGeneratedClassName(outDir, "ClinicController__TsjStrictNative");
            final Class<?> repositoryClass = Class.forName(repositoryClassName, true, classLoader);
            final Class<?> serviceClass = Class.forName(serviceClassName, true, classLoader);
            final Class<?> controllerClass = Class.forName(controllerClassName, true, classLoader);

            final Method listMethod = resolveMethodByNameAndArity(controllerClass, "list", 1);
            final boolean requestParamPresent = parameterHasAnnotationValue(
                    listMethod,
                    0,
                    SPRING_REQUEST_PARAM,
                    "value",
                    "lastName"
            );
            final boolean stereotypesPresent = hasAnnotation(repositoryClass, SPRING_REPOSITORY)
                    && hasAnnotation(serviceClass, SPRING_SERVICE)
                    && hasAnnotation(controllerClass, SPRING_REST_CONTROLLER);
            final String[] requestMapping = annotationStringArrayValue(controllerClass, SPRING_REQUEST_MAPPING, "value");
            final String[] getMapping = annotationStringArrayValue(listMethod, SPRING_GET_MAPPING, "value");

            Object response = null;
            Object context = null;
            try {
                context = newSpringContext(classLoader);
                final Object finalContext = context;
                response = withThreadContextClassLoader(classLoader, () -> {
                    invokeMethod(finalContext, "scan", new Class<?>[]{String[].class}, (Object) new String[]{"dev.tsj.generated"});
                    invokeMethod(finalContext, "refresh");
                    final Object controller = invokeMethod(finalContext, "getBean", new Class<?>[]{Class.class}, controllerClass);
                    return listMethod.invoke(controller, "Simpson");
                });
            } finally {
                closeQuietly(context);
            }

            final boolean passed = stereotypesPresent
                    && containsPath(requestMapping, "/api/owners")
                    && containsPath(getMapping, "/")
                    && requestParamPresent
                    && "owners:Simpson".equals(String.valueOf(response));
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "spring-web-di-package-runtime",
                    passed,
                    "response=" + response
                            + ",requestMapping=" + String.join("|", requestMapping)
                            + ",getMapping=" + String.join("|", getMapping)
                            + ",requestParam=" + requestParamPresent
                            + ",controllerClass=" + controllerClassName,
                    packagedJar.toAbsolutePath().normalize().toString()
            );
        }
    }

    private static TsjAnyJarNoHacksCertificationReport.ScenarioResult runSpringAopTransactionProxyScenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/conformance/anyjar-nohacks/spring_aop_web_di_app"),
                workDir.resolve("fixture")
        );
        final Path outDir = workDir.resolve("out");
        final CommandResult compileResult = runCommand(
                "compile",
                fixtureRoot.resolve("main.ts").toString(),
                "--out",
                outDir.toString(),
                "--mode",
                "jvm-strict",
                "--interop-policy",
                "broad",
                "--ack-interop-risk",
                "--jar",
                jarPathForClass(ApplicationContext.class).toString(),
                "--jar",
                jarPathForClass(TransactionTemplate.class).toString(),
                "--jar",
                jarPathForClass(MediaType.class).toString()
        );
        if (compileResult.exitCode() != 0) {
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "spring-aop-transaction-proxy",
                    false,
                    "compileExit=" + compileResult.exitCode() + ",stderr=" + trim(compileResult.stderr(), 220),
                    outDir.resolve("classes").toAbsolutePath().normalize().toString()
            );
        }

        final Path classesDir = outDir.resolve("classes");
        try (URLClassLoader classLoader = createChildFirstClassLoader(
                springCompileScenarioClasspath(repoRoot, classesDir),
                SPRING_CHILD_FIRST_PREFIXES
        )) {
            final String payloadClassName = resolveGeneratedClassName(classesDir, "AuditPayload__TsjStrictNative");
            final String serviceClassName = resolveGeneratedClassName(classesDir, "AuditService__TsjStrictNative");
            final String controllerClassName = resolveGeneratedClassName(classesDir, "AuditController__TsjStrictNative");
            final Class<?> payloadClass = Class.forName(payloadClassName, true, classLoader);
            final Class<?> serviceClass = Class.forName(serviceClassName, true, classLoader);
            final Class<?> controllerClass = Class.forName(controllerClassName, true, classLoader);

            final Object payload = payloadClass.getDeclaredConstructor().newInstance();
            final Object service = serviceClass.getDeclaredConstructor().newInstance();
            final AtomicInteger adviceCount = new AtomicInteger();

            final Class<?> proxyFactoryType = Class.forName("org.springframework.aop.framework.ProxyFactory", true, classLoader);
            final Object proxyFactory = proxyFactoryType.getConstructor(Object.class).newInstance(service);
            invokeMethod(proxyFactory, "setProxyTargetClass", new Class<?>[]{boolean.class}, true);
            addCountingAdvice(classLoader, proxyFactory, adviceCount);
            final Object proxy = invokeMethod(proxyFactory, "getProxy", new Class<?>[]{ClassLoader.class}, classLoader);

            final Method saveMethod = serviceClass.getMethod("save", payloadClass);
            final Object proxiedResult = saveMethod.invoke(proxy, payload);

            final Method controllerSave = controllerClass.getMethod("save", payloadClass);
            final boolean requestBodyPresent = parameterHasAnnotation(controllerSave, 0, SPRING_REQUEST_BODY);
            final boolean transactional = hasAnnotation(saveMethod, SPRING_TRANSACTIONAL);
            final String[] requestMapping = annotationStringArrayValue(controllerClass, SPRING_REQUEST_MAPPING, "value");

            final Object controller = controllerClass
                    .getConstructors()[0]
                    .newInstance(proxy);
            final Object controllerResult = controllerSave.invoke(controller, payload);
            final boolean cglibProxy = isCglibProxy(classLoader, proxy);

            final boolean passed = transactional
                    && cglibProxy
                    && adviceCount.get() == 2
                    && "saved".equals(String.valueOf(proxiedResult))
                    && containsPath(requestMapping, "/api/audit")
                    && requestBodyPresent
                    && "saved".equals(String.valueOf(controllerResult));
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "spring-aop-transaction-proxy",
                    passed,
                    "proxied=" + proxiedResult
                            + ",controller=" + controllerResult
                            + ",adviceCount=" + adviceCount.get()
                            + ",requestMapping=" + String.join("|", requestMapping)
                            + ",serviceClass=" + serviceClassName,
                    classesDir.toAbsolutePath().normalize().toString()
            );
        }
    }

    private static TsjAnyJarNoHacksCertificationReport.ScenarioResult runHibernateJpaH2Scenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/introspector-matrix/tsj39b-hibernate-executable"),
                workDir.resolve("fixture")
        );
        final Path outDir = workDir.resolve("out");
        final CommandResult compileResult = runCommand(
                "compile",
                fixtureRoot.resolve("main.ts").toString(),
                "--out",
                outDir.toString(),
                "--mode",
                "jvm-strict",
                "--interop-policy",
                "broad",
                "--ack-interop-risk",
                "--jar",
                jarPathForClass(Entity.class).toString()
        );
        if (compileResult.exitCode() != 0) {
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "hibernate-jpa-h2-executable",
                    false,
                    "compileExit=" + compileResult.exitCode() + ",stderr=" + trim(compileResult.stderr(), 220),
                    outDir.resolve("classes").toAbsolutePath().normalize().toString()
            );
        }

        final Path classesDir = outDir.resolve("classes");
        StandardServiceRegistry serviceRegistry = null;
        try (URLClassLoader classLoader = createChildFirstClassLoader(
                List.of(classesDir),
                List.of("dev.tsj.generated.")
        )) {
            final String entityClassName = resolveGeneratedClassName(classesDir, "HibernateMatrixPerson__TsjStrictNative");
            final Class<?> entityClass = Class.forName(entityClassName, true, classLoader);
            final BootstrapServiceRegistry bootstrapServiceRegistry = new BootstrapServiceRegistryBuilder()
                    .applyClassLoader(classLoader)
                    .build();
            serviceRegistry = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                    .applySetting(AvailableSettings.DRIVER, Driver.class.getName())
                    .applySetting(AvailableSettings.URL, "jdbc:h2:mem:tsj92;DB_CLOSE_DELAY=-1")
                    .applySetting(AvailableSettings.USER, "sa")
                    .applySetting(AvailableSettings.PASS, "")
                    .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                    .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                    .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                    .build();
            final Metadata metadata = new MetadataSources(serviceRegistry)
                    .addAnnotatedClass(entityClass)
                    .buildMetadata();
            metadata.buildSessionFactory().close();
            final PersistentClass binding = metadata.getEntityBindings().stream()
                    .filter(candidate -> entityClass.getName().equals(candidate.getClassName()))
                    .findFirst()
                    .orElse(null);
            final Property nameProperty = binding == null ? null : binding.getProperty("name");
            final String tableName = binding == null ? "null" : binding.getTable().getName();
            final List<String> nameColumns = nameProperty == null
                    ? List.of()
                    : nameProperty.getColumns().stream()
                            .map(org.hibernate.mapping.Column::getName)
                            .sorted()
                            .toList();
            final boolean passed = binding != null
                    && "people".equals(tableName)
                    && nameColumns.equals(List.of("display_name"))
                    && "id".equals(binding.getIdentifierProperty().getName());
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "hibernate-jpa-h2-executable",
                    passed,
                    "entity=" + entityClassName + ",table=" + tableName + ",columns=" + nameColumns,
                    classesDir.toAbsolutePath().normalize().toString()
            );
        } finally {
            if (serviceRegistry != null) {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
            }
        }
    }

    private static TsjAnyJarNoHacksCertificationReport.ScenarioResult runJacksonExecutableDtoScenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/introspector-matrix/tsj39b-jackson-executable"),
                workDir.resolve("fixture")
        );
        final Path outDir = workDir.resolve("out");
        final CommandResult compileResult = runCommand(
                "compile",
                fixtureRoot.resolve("main.ts").toString(),
                "--out",
                outDir.toString(),
                "--mode",
                "jvm-strict",
                "--interop-policy",
                "broad",
                "--ack-interop-risk",
                "--jar",
                jarPathForClass(JsonProperty.class).toString()
        );
        if (compileResult.exitCode() != 0) {
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "jackson-executable-dto",
                    false,
                    "compileExit=" + compileResult.exitCode() + ",stderr=" + trim(compileResult.stderr(), 220),
                    outDir.resolve("classes").toAbsolutePath().normalize().toString()
            );
        }

        final Path classesDir = outDir.resolve("classes");
        try (URLClassLoader classLoader = createChildFirstClassLoader(
                List.of(classesDir),
                List.of("dev.tsj.generated.")
        )) {
            final String dtoClassName = resolveGeneratedClassName(classesDir, "JacksonMatrixPerson__TsjStrictNative");
            final Class<?> dtoClass = Class.forName(dtoClassName, true, classLoader);
            final Object dto = dtoClass.getDeclaredConstructor().newInstance();
            dtoClass.getMethod("setId", String.class).invoke(dto, "p-1");
            dtoClass.getMethod("setName", String.class).invoke(dto, "Ada");

            final ObjectMapper mapper = new ObjectMapper();
            final String json = mapper.writeValueAsString(dto);
            final Object rebound = mapper.readValue("{\"person_id\":\"p-2\",\"display_name\":\"Grace\"}", dtoClass);
            final Object reboundId = dtoClass.getMethod("getId").invoke(rebound);
            final Object reboundName = dtoClass.getMethod("getName").invoke(rebound);
            final boolean passed = json.contains("\"person_id\":\"p-1\"")
                    && json.contains("\"display_name\":\"Ada\"")
                    && "p-2".equals(String.valueOf(reboundId))
                    && "Grace".equals(String.valueOf(reboundName));
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "jackson-executable-dto",
                    passed,
                    "json=" + json + ",reboundId=" + reboundId + ",reboundName=" + reboundName,
                    classesDir.toAbsolutePath().normalize().toString()
            );
        }
    }

    private static TsjAnyJarNoHacksCertificationReport.ScenarioResult runValidationExecutableDtoScenario(
            final Path workDir,
            final Path repoRoot
    ) throws Exception {
        Files.createDirectories(workDir);
        final Path fixtureRoot = copyFixtureTree(
                repoRoot.resolve("tests/introspector-matrix/tsj39b-validation-executable"),
                workDir.resolve("fixture")
        );
        final Path outDir = workDir.resolve("out");
        final CommandResult compileResult = runCommand(
                "compile",
                fixtureRoot.resolve("main.ts").toString(),
                "--out",
                outDir.toString(),
                "--mode",
                "jvm-strict",
                "--interop-policy",
                "broad",
                "--ack-interop-risk",
                "--jar",
                jarPathForClass(NotBlank.class).toString()
        );
        if (compileResult.exitCode() != 0) {
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "validation-executable-dto",
                    false,
                    "compileExit=" + compileResult.exitCode() + ",stderr=" + trim(compileResult.stderr(), 220),
                    outDir.resolve("classes").toAbsolutePath().normalize().toString()
            );
        }

        final Path classesDir = outDir.resolve("classes");
        try (URLClassLoader classLoader = createChildFirstClassLoader(
                List.of(classesDir),
                List.of("dev.tsj.generated.")
        )) {
            final String dtoClassName = resolveGeneratedClassName(classesDir, "ValidationMatrixPerson__TsjStrictNative");
            final Class<?> dtoClass = Class.forName(dtoClassName, true, classLoader);
            final Object invalid = dtoClass.getDeclaredConstructor().newInstance();
            final ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class)
                    .configure()
                    .messageInterpolator(new ParameterMessageInterpolator())
                    .buildValidatorFactory();
            final Validator validator = validatorFactory.getValidator();
            final Set<?> violations = validator.validate(invalid);

            final Object valid = dtoClass.getDeclaredConstructor().newInstance();
            dtoClass.getMethod("setName", String.class).invoke(valid, "Ada");
            dtoClass.getMethod("setAlias", String.class).invoke(valid, "adalace");
            final Set<?> validViolations = validator.validate(valid);
            validatorFactory.close();

            final boolean passed = violations.size() == 2 && validViolations.isEmpty();
            return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                    "validation-executable-dto",
                    passed,
                    "invalidViolations=" + violations.size() + ",validViolations=" + validViolations.size(),
                    classesDir.toAbsolutePath().normalize().toString()
            );
        }
    }

    private static TsjAnyJarNoHacksCertificationReport.ScenarioResult runNonSpringReflectionConsumerScenario(
            final Path workDir
    ) throws Exception {
        Files.createDirectories(workDir);
        final Path supportJar = buildReflectionConsumerJar(workDir.resolve("support"));
        final Path sourceFile = workDir.resolve("main.ts");
        Files.writeString(
                sourceFile,
                """
                import { Component } from "java:sample.reflect.Component";
                import { Inject } from "java:sample.reflect.Inject";
                import { Route } from "java:sample.reflect.Route";
                import { Named } from "java:sample.reflect.Named";
                import { loadClass } from "java:sample.reflect.TypeLocator";
                import { hasComponent, countInjectFields } from "java:sample.reflect.DiConsumer";
                import {
                  routePath,
                  countNamedParameters,
                  countNamedConstructorParameters
                } from "java:sample.reflect.MetadataConsumer";

                @Component
                class Repo {
                }

                @Component
                @Route("/orders")
                class Controller {
                  @Inject
                  repo: Repo;

                  constructor(@Named("repoCtor") repo: Repo) {
                    this.repo = repo;
                  }

                  find(@Named("id") id: string): string {
                    return id;
                  }
                }

                const type = loadClass("dev.tsj.generated.Controller__TsjStrictNative");
                console.log("component=" + hasComponent(type));
                console.log("injectFields=" + countInjectFields(type));
                console.log("route=" + routePath(type));
                console.log("ctorNamedParams=" + countNamedConstructorParameters(type));
                console.log("namedParams=" + countNamedParameters(type, "find"));
                """,
                UTF_8
        );

        final Path outDir = workDir.resolve("out");
        final CommandResult runResult = runCommand(
                "run",
                sourceFile.toString(),
                "--out",
                outDir.toString(),
                "--mode",
                "jvm-strict",
                "--jar",
                supportJar.toString(),
                "--interop-policy",
                "broad",
                "--ack-interop-risk"
        );
        final String stdoutText = runResult.stdout();
        final boolean passed = runResult.exitCode() == 0 && stdoutText.contains(
                """
                component=true
                injectFields=1
                route=/orders
                ctorNamedParams=1
                namedParams=1
                """
        );
        return new TsjAnyJarNoHacksCertificationReport.ScenarioResult(
                "non-spring-reflection-consumer",
                passed,
                "exit=" + runResult.exitCode() + ",stdout=" + trim(stdoutText, 220) + ",stderr=" + trim(runResult.stderr(), 220),
                supportJar.toAbsolutePath().normalize().toString()
        );
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

    private static Path jarPathForClass(final Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
        } catch (final Exception exception) {
            throw new IllegalStateException("Unable to resolve jar path for " + type.getName(), exception);
        }
    }

    private static List<Path> springCompileScenarioClasspath(final Path repoRoot, final Path classesDir) {
        return List.of(
                classesDir,
                repoRoot.resolve("runtime/target/classes"),
                jarPathForClass(ApplicationContext.class),
                jarPathForClass(BeanFactory.class),
                jarPathForClass(SpringVersion.class),
                jarPathForClass(LogFactory.class),
                jarPathForClass(Expression.class),
                jarPathForClass(MediaType.class),
                jarPathForClass(TransactionTemplate.class),
                jarPathForClass(ProxyFactory.class)
        );
    }

    private static URLClassLoader createChildFirstClassLoader(
            final List<Path> entries,
            final List<String> childFirstPrefixes
    ) throws IOException {
        final URL[] urls = new URL[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            urls[index] = entries.get(index).toUri().toURL();
        }
        return new ChildFirstUrlClassLoader(
                urls,
                TsjAnyJarNoHacksCertificationHarness.class.getClassLoader(),
                childFirstPrefixes
        );
    }

    private static String resolveGeneratedClassName(final Path artifactRoot, final String simpleName) throws IOException {
        final List<String> matches = resolveGeneratedClassNames(artifactRoot, simpleName);
        if (matches.isEmpty()) {
            throw new IllegalStateException("Generated class not found for `" + simpleName + "` under " + artifactRoot);
        }
        final List<String> topLevelMatches = matches.stream()
                .filter(candidate -> !candidate.contains("$"))
                .toList();
        if (topLevelMatches.size() == 1) {
            return topLevelMatches.getFirst();
        }
        if (topLevelMatches.size() > 1) {
            throw new IllegalStateException("Ambiguous generated class matches for `" + simpleName + "`: " + topLevelMatches);
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        return matches.stream()
                .sorted((left, right) -> Integer.compare(left.length(), right.length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No generated class candidate selected for `" + simpleName + "`."));
    }

    private static List<String> resolveGeneratedClassNames(final Path artifactRoot, final String simpleName) throws IOException {
        if (Files.isDirectory(artifactRoot)) {
            final Path classIndexPath = artifactRoot.resolve("class-index.json");
            if (Files.isRegularFile(classIndexPath)) {
                final List<String> classIndexMatches = resolveGeneratedClassNamesFromClassIndex(classIndexPath, simpleName);
                if (!classIndexMatches.isEmpty()) {
                    return classIndexMatches;
                }
            }
            final Path classesDir = artifactRoot.resolve("classes");
            if (Files.isDirectory(classesDir)) {
                final List<String> classesDirMatches = resolveGeneratedClassNamesFromDirectory(classesDir, simpleName);
                if (!classesDirMatches.isEmpty()) {
                    return classesDirMatches;
                }
            }
            return resolveGeneratedClassNamesFromDirectory(artifactRoot, simpleName);
        }
        final Path parent = artifactRoot.getParent();
        if (parent != null) {
            final Path siblingClassIndex = parent.resolve("class-index.json");
            if (Files.isRegularFile(siblingClassIndex)) {
                final List<String> classIndexMatches = resolveGeneratedClassNamesFromClassIndex(siblingClassIndex, simpleName);
                if (!classIndexMatches.isEmpty()) {
                    return classIndexMatches;
                }
            }
        }
        return resolveGeneratedClassNamesFromJar(artifactRoot, simpleName);
    }

    private static List<String> resolveGeneratedClassNamesFromClassIndex(final Path classIndexPath, final String simpleName)
            throws IOException {
        final JsonNode root = new ObjectMapper().readTree(Files.readString(classIndexPath, UTF_8));
        final JsonNode symbols = root.path("symbols");
        if (!symbols.isArray()) {
            return List.of();
        }
        final List<String> matches = new ArrayList<>();
        for (JsonNode symbol : symbols) {
            final JsonNode origin = symbol.path("origin");
            if (!"app".equals(origin.path("owner").asText())) {
                continue;
            }
            final String internalName = symbol.path("internalName").asText("");
            if (!internalName.endsWith(simpleName)) {
                continue;
            }
            matches.add(internalName.replace('/', '.'));
        }
        matches.sort(String::compareTo);
        return List.copyOf(matches);
    }

    private static List<String> resolveGeneratedClassNamesFromDirectory(final Path classesDir, final String simpleName)
            throws IOException {
        try (Stream<Path> stream = Files.walk(classesDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(simpleName + ".class"))
                    .map(path -> toBinaryClassName(classesDir.relativize(path).toString()))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> resolveGeneratedClassNamesFromJar(final Path jarPath, final String simpleName)
            throws IOException {
        final List<String> matches = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            final var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(simpleName + ".class")) {
                    continue;
                }
                matches.add(toBinaryClassName(entry.getName()));
            }
        }
        matches.sort(String::compareTo);
        return List.copyOf(matches);
    }

    private static String toBinaryClassName(final String relativeClassPath) {
        return relativeClassPath
                .replace('/', '.')
                .replace('\\', '.')
                .replaceAll("\\.class$", "");
    }

    private static boolean hasAnnotation(final AnnotatedElement element, final String annotationClassName) {
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationClassName)) {
                return true;
            }
        }
        return false;
    }

    private static String[] annotationStringArrayValue(
            final AnnotatedElement element,
            final String annotationClassName,
            final String attributeName
    ) throws ReflectiveOperationException {
        for (Annotation annotation : element.getAnnotations()) {
            if (!annotation.annotationType().getName().equals(annotationClassName)) {
                continue;
            }
            final Object value = annotation.annotationType().getMethod(attributeName).invoke(annotation);
            if (value instanceof String[] strings) {
                return strings;
            }
            if (value instanceof String string) {
                return new String[]{string};
            }
            return new String[]{String.valueOf(value)};
        }
        return new String[0];
    }

    private static boolean parameterHasAnnotation(final Method method, final int parameterIndex, final String annotationClassName) {
        if (parameterIndex >= method.getParameterAnnotations().length) {
            return false;
        }
        for (Annotation annotation : method.getParameterAnnotations()[parameterIndex]) {
            if (annotation.annotationType().getName().equals(annotationClassName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean parameterHasAnnotationValue(
            final Method method,
            final int parameterIndex,
            final String annotationClassName,
            final String attributeName,
            final String expectedValue
    ) throws ReflectiveOperationException {
        if (parameterIndex >= method.getParameterAnnotations().length) {
            return false;
        }
        for (Annotation annotation : method.getParameterAnnotations()[parameterIndex]) {
            if (!annotation.annotationType().getName().equals(annotationClassName)) {
                continue;
            }
            final Object value = annotation.annotationType().getMethod(attributeName).invoke(annotation);
            return expectedValue.equals(String.valueOf(value));
        }
        return false;
    }

    private static Method resolveMethodByNameAndArity(
            final Class<?> owner,
            final String methodName,
            final int arity
    ) {
        final List<Method> matches = new ArrayList<>();
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity) {
                matches.add(method);
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        throw new IllegalStateException(
                "Expected exactly one method `" + methodName + "` with arity " + arity + " on " + owner.getName()
                        + ", found " + matches.size()
        );
    }

    private static Object newSpringContext(final ClassLoader classLoader) throws ReflectiveOperationException {
        final Class<?> contextType = Class.forName(SPRING_CONTEXT_CLASS, true, classLoader);
        final Object context = contextType.getDeclaredConstructor().newInstance();
        try {
            contextType.getMethod("setClassLoader", ClassLoader.class).invoke(context, classLoader);
        } catch (NoSuchMethodException ignored) {
            // Real Spring context exposes this through DefaultResourceLoader; older/stub variants may not.
        }
        return context;
    }

    private static Object invokeMethod(final Object target, final String methodName) throws ReflectiveOperationException {
        return invokeMethod(target, methodName, new Class<?>[0]);
    }

    private static Object invokeMethod(
            final Object target,
            final String methodName,
            final Class<?>[] parameterTypes,
            final Object... args
    ) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
    }

    private static <T> T withThreadContextClassLoader(
            final ClassLoader classLoader,
            final ThrowingSupplier<T> supplier
    ) throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static void closeQuietly(final Object closeable) throws Exception {
        if (closeable == null) {
            return;
        }
        invokeMethod(closeable, "close");
    }

    private static void addCountingAdvice(
            final ClassLoader classLoader,
            final Object proxyFactory,
            final AtomicInteger adviceCount
    ) throws ReflectiveOperationException {
        final Class<?> adviceType = Class.forName("org.aopalliance.aop.Advice", true, classLoader);
        final Class<?> interceptorType = Class.forName("org.aopalliance.intercept.MethodInterceptor", true, classLoader);
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("invoke".equals(method.getName()) && args != null && args.length == 1) {
                adviceCount.incrementAndGet();
                final Method proceed = args[0].getClass().getDeclaredMethod("proceed");
                proceed.setAccessible(true);
                return proceed.invoke(args[0]);
            }
            if ("toString".equals(method.getName())) {
                return "TSJ-92-counting-advice";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return null;
        };
        final Object interceptor = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{interceptorType},
                handler
        );
        proxyFactory.getClass()
                .getMethod("addAdvice", adviceType)
                .invoke(proxyFactory, interceptor);
    }

    private static boolean isCglibProxy(final ClassLoader classLoader, final Object proxy)
            throws ReflectiveOperationException {
        final Class<?> aopUtilsType = Class.forName("org.springframework.aop.support.AopUtils", true, classLoader);
        return (Boolean) aopUtilsType.getMethod("isCglibProxy", Object.class).invoke(null, proxy);
    }

    private static Path buildReflectionConsumerJar(final Path workDir) throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put(
                "sample/reflect/Component.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Component {}
                """
        );
        sources.put(
                "sample/reflect/Inject.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.FIELD)
                public @interface Inject {}
                """
        );
        sources.put(
                "sample/reflect/Route.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Route {
                    String value();
                }
                """
        );
        sources.put(
                "sample/reflect/Named.java",
                """
                package sample.reflect;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.PARAMETER)
                public @interface Named {
                    String value();
                }
                """
        );
        sources.put(
                "sample/reflect/TypeLocator.java",
                """
                package sample.reflect;

                public final class TypeLocator {
                    private TypeLocator() {}

                    public static Class<?> loadClass(final String className) {
                        try {
                            final ClassLoader context = Thread.currentThread().getContextClassLoader();
                            return Class.forName(className, true, context);
                        } catch (final ClassNotFoundException exception) {
                            throw new IllegalStateException("Class not found: " + className, exception);
                        }
                    }
                }
                """
        );
        sources.put(
                "sample/reflect/DiConsumer.java",
                """
                package sample.reflect;

                import java.lang.reflect.Field;

                public final class DiConsumer {
                    private DiConsumer() {}

                    public static boolean hasComponent(final Class<?> type) {
                        return type != null && type.isAnnotationPresent(Component.class);
                    }

                    public static int countInjectFields(final Class<?> type) {
                        if (type == null) {
                            return 0;
                        }
                        int count = 0;
                        Class<?> current = type;
                        while (current != null && current != Object.class) {
                            for (Field field : current.getDeclaredFields()) {
                                if (field.isAnnotationPresent(Inject.class)) {
                                    count++;
                                }
                            }
                            current = current.getSuperclass();
                        }
                        return count;
                    }
                }
                """
        );
        sources.put(
                "sample/reflect/MetadataConsumer.java",
                """
                package sample.reflect;

                import java.lang.reflect.Method;
                import java.lang.reflect.Parameter;

                public final class MetadataConsumer {
                    private MetadataConsumer() {}

                    public static String routePath(final Class<?> type) {
                        if (type == null) {
                            return "none";
                        }
                        final Route route = type.getAnnotation(Route.class);
                        return route == null ? "none" : route.value();
                    }

                    public static int countNamedParameters(final Class<?> type, final String methodName) {
                        if (type == null || methodName == null) {
                            return 0;
                        }
                        for (Method method : type.getDeclaredMethods()) {
                            if (!method.getName().equals(methodName)) {
                                continue;
                            }
                            int count = 0;
                            for (Parameter parameter : method.getParameters()) {
                                if (parameter.isAnnotationPresent(Named.class)) {
                                    count++;
                                }
                            }
                            return count;
                        }
                        return 0;
                    }

                    public static int countNamedConstructorParameters(final Class<?> type) {
                        if (type == null) {
                            return 0;
                        }
                        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
                            int count = 0;
                            for (Parameter parameter : constructor.getParameters()) {
                                if (parameter.isAnnotationPresent(Named.class)) {
                                    count++;
                                }
                            }
                            return count;
                        }
                        return 0;
                    }
                }
                """
        );

        final Path sourceRoot = workDir.resolve("src");
        final Path classesRoot = workDir.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classesRoot);

        final List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            final Path sourcePath = sourceRoot.resolve(source.getKey());
            Files.createDirectories(Objects.requireNonNull(sourcePath.getParent(), "Source parent is required."));
            Files.writeString(sourcePath, source.getValue(), UTF_8);
            sourceFiles.add(sourcePath);
        }

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for TSJ-92 reflection-consumer support jar.");
        }
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            final Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final List<String> options = List.of(
                    "--release",
                    "21",
                    "-parameters",
                    "-d",
                    classesRoot.toString()
            );
            final Boolean success = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile TSJ-92 reflection-consumer jar.");
            }
        }

        final Path jarPath = workDir.resolve("tsj92-reflection-consumer.jar");
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> files = Files.walk(classesRoot)) {
                for (Path classFile : files.filter(Files::isRegularFile).toList()) {
                    final String entryName = classesRoot.relativize(classFile).toString().replace('\\', '/');
                    outputStream.putNextEntry(new JarEntry(entryName));
                    outputStream.write(Files.readAllBytes(classFile));
                    outputStream.closeEntry();
                }
            }
        }
        return jarPath;
    }

    private static String trim(final String text, final int maxLength) {
        if (text == null) {
            return "";
        }
        final String normalized = text.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private static boolean containsPath(final String[] values, final String expected) {
        for (String value : values) {
            if (expected.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.exists(current.resolve("tests/conformance"))
                    && Files.exists(current.resolve("tests/introspector-matrix"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Failed to resolve repository root for TSJ-92 certification harness.");
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjAnyJarNoHacksCertificationHarness.class
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

    private static void writeReport(final Path reportPath, final TsjAnyJarNoHacksCertificationReport report) throws IOException {
        final Path normalized = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalized, report.toJson() + "\n", UTF_8);
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private final List<String> childFirstPrefixes;

        private ChildFirstUrlClassLoader(
                final URL[] urls,
                final ClassLoader parent,
                final List<String> childFirstPrefixes
        ) {
            super(urls, parent);
            this.childFirstPrefixes = List.copyOf(childFirstPrefixes);
        }

        @Override
        protected synchronized Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            if (!usesChildFirst(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException exception) {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }

        private boolean usesChildFirst(final String className) {
            for (String prefix : childFirstPrefixes) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
