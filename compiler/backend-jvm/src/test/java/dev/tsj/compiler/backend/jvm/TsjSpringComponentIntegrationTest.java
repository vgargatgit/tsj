package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TsjSpringComponentIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void generatedTsServiceAdapterSupportsConstructorInjectionAndInvocation() throws Exception {
        final Path entryFile = tempDir.resolve("service.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class GreetingService {
                  constructor(prefix: any) {
                  }

                  greet(name: string) {
                    return "hi:" + name;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.GreetingServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(DependencyConfig.class);
                context.register(componentClass);
                context.refresh();

                final Object service = context.getBean(componentClass);
                final Method greet = componentClass.getMethod("greet", Object.class);
                final Object result = greet.invoke(service, "tsj");
                assertEquals("hi:tsj", String.valueOf(result));
            }
        }
    }

    @Test
    void generatedTsConfigurationBeanMethodParticipatesInContextRefresh() throws Exception {
        final Path entryFile = tempDir.resolve("config.ts");
        Files.writeString(
                entryFile,
                """
                @Configuration
                class AppConfig {
                  @Bean
                  greeting() {
                    return "hello";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out-config"));
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-config")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.AppConfigTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(componentClass);
                context.refresh();

                final Object bean = context.getBean(Object.class);
                assertEquals("hello", String.valueOf(bean));
            }
        }
    }

    @Test
    void postConstructMethodIsInvokedDuringRefresh() throws Exception {
        final Path entryFile = tempDir.resolve("post-construct.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class LifecycleService {
                  @PostConstruct
                  init() {
                    throw "refresh-failure";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out-post"));
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-post")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.LifecycleServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(componentClass);
                final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
                assertTrue(exception.getMessage().contains("TSJ-SPRING-LIFECYCLE"));
                assertTrue(exception.getMessage().contains("TSJ33E-LIFECYCLE"));
                assertTrue(exception.getMessage().contains("phase=refresh"));
                assertTrue(exception.getMessage().contains("post-construct"));
                assertFalse(exception.getMessage().contains("TSJ33D-INJECTION-MODES"));
            }
        }
    }

    @Test
    void preDestroyMethodIsInvokedDuringClose() throws Exception {
        final Path entryFile = tempDir.resolve("pre-destroy.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class LifecycleService {
                  @PreDestroy
                  shutdown() {
                    throw "close-failure";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out-close"));
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-close")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.LifecycleServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.setClassLoader(classLoader);
            context.register(componentClass);
            context.refresh();
            final IllegalStateException exception = assertThrows(IllegalStateException.class, context::close);
            assertTrue(exception.getMessage().contains("TSJ-SPRING-LIFECYCLE"));
            assertTrue(exception.getMessage().contains("TSJ33E-LIFECYCLE"));
            assertTrue(exception.getMessage().contains("phase=close"));
            assertTrue(exception.getMessage().contains("pre-destroy"));
            assertFalse(exception.getMessage().contains("TSJ33D-INJECTION-MODES"));
        }
    }

    @Test
    void lifecycleCallbacksFollowRefreshAndReverseCloseOrderingInTsj33e() throws Exception {
        final Path entryFile = tempDir.resolve("lifecycle-order.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class FirstService {
                  @PostConstruct
                  init() {
                    return "first-init";
                  }

                  @PreDestroy
                  shutdown() {
                    return "first-shutdown";
                  }
                }

                @Service
                class SecondService {
                  @PostConstruct
                  init() {
                    return "second-init";
                  }

                  @PreDestroy
                  shutdown() {
                    return "second-shutdown";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-lifecycle-order")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-lifecycle-order")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.setClassLoader(classLoader);
            for (String className : componentArtifact.componentClassNames()) {
                context.register(Class.forName(
                        "dev.tsj.generated.spring." + className + "TsjComponent",
                        true,
                        classLoader
                ));
            }
            context.refresh();
            context.close();

            assertEquals(
                    List.of(
                            "refresh:FirstServiceTsjComponent#init",
                            "refresh:SecondServiceTsjComponent#init",
                            "close:SecondServiceTsjComponent#shutdown",
                            "close:FirstServiceTsjComponent#shutdown"
                    ),
                    context.lifecycleEvents()
            );
        }
    }

    @Test
    void circularDependencyDiagnosticsIncludeCyclePathInTsj33e() throws Exception {
        final Path entryFile = tempDir.resolve("lifecycle-cycle.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                @Qualifier("alpha")
                class AlphaService {
                  constructor(@Qualifier("beta") dep: any) {
                  }
                }

                @Service
                @Qualifier("beta")
                class BetaService {
                  constructor(@Qualifier("alpha") dep: any) {
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-lifecycle-cycle")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-lifecycle-cycle")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.setClassLoader(classLoader);
            for (String className : componentArtifact.componentClassNames()) {
                context.register(Class.forName(
                        "dev.tsj.generated.spring." + className + "TsjComponent",
                        true,
                        classLoader
                ));
            }

            final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
            assertTrue(exception.getMessage().contains("TSJ-SPRING-LIFECYCLE"));
            assertTrue(exception.getMessage().contains("TSJ33E-LIFECYCLE"));
            assertTrue(exception.getMessage().contains("Circular dependency"));
            assertTrue(exception.getMessage().contains("AlphaServiceTsjComponent"));
            assertTrue(exception.getMessage().contains("BetaServiceTsjComponent"));
        }
    }

    @Test
    void transactionalMethodIsProxiedAndCommitsOnSuccessfulInvocation() throws Exception {
        RecordingTransactionManager.reset();
        final Path entryFile = tempDir.resolve("transactional-success.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BillingService {
                  @Transactional
                  charge(amount: any) {
                    return "charged:" + amount;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out-tx-success"));
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-tx-success")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.BillingServiceTsjComponent";
        final String generatedApiClassName = "dev.tsj.generated.spring.BillingServiceTsjComponentApi";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            final Class<?> apiClass = Class.forName(generatedApiClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(TransactionManagerConfig.class);
                context.register(componentClass);
                context.refresh();

                final Object service = context.getBean(apiClass);
                final Method charge = apiClass.getMethod("charge", Object.class);
                final Object result = charge.invoke(service, 7);
                assertEquals("charged:7", String.valueOf(result));
                assertTrue(Proxy.isProxyClass(service.getClass()));
                assertTrue(apiClass.isInstance(service));
                assertFalse(componentClass.isInstance(service));
                assertEquals(1, RecordingTransactionManager.beginCount());
                assertEquals(1, RecordingTransactionManager.commitCount());
                assertEquals(0, RecordingTransactionManager.rollbackCount());
            }
        }
    }

    @Test
    void transactionalMethodRollsBackOnInvocationFailure() throws Exception {
        RecordingTransactionManager.reset();
        final Path entryFile = tempDir.resolve("transactional-failure.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BillingService {
                  @Transactional
                  charge(amount: any) {
                    throw "tx-failure";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out-tx-failure"));
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-tx-failure")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.BillingServiceTsjComponent";
        final String generatedApiClassName = "dev.tsj.generated.spring.BillingServiceTsjComponentApi";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            final Class<?> apiClass = Class.forName(generatedApiClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(TransactionManagerConfig.class);
                context.register(componentClass);
                context.refresh();

                final Object service = context.getBean(apiClass);
                final Method charge = apiClass.getMethod("charge", Object.class);
                final InvocationTargetException invocationTargetException = assertThrows(
                        InvocationTargetException.class,
                        () -> charge.invoke(service, 5)
                );
                assertTrue(String.valueOf(invocationTargetException.getCause()).contains("tx-failure"));
                assertEquals(1, RecordingTransactionManager.beginCount());
                assertEquals(0, RecordingTransactionManager.commitCount());
                assertEquals(1, RecordingTransactionManager.rollbackCount());
            }
        }
    }

    @Test
    void transactionalBeansRequireTransactionManagerInTsj35Subset() throws Exception {
        final Path entryFile = tempDir.resolve("transactional-missing-manager.ts");
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
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-tx-missing-manager")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-tx-missing-manager")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.BillingServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(componentClass);
                final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
                assertTrue(exception.getMessage().contains("TSJ-SPRING-AOP"));
                assertTrue(exception.getMessage().contains("PlatformTransactionManager"));
            }
        }
    }

    @Test
    void mixedConstructorFieldSetterInjectionWorksInTsj33dSubset() throws Exception {
        final Path entryFile = tempDir.resolve("mixed-injection.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class MixedInjectionService {
                  constructor(@Qualifier("clockBean") clock: any) {
                  }

                  @Autowired
                  @Qualifier("metricsBean")
                  metrics: any;

                  @Autowired
                  @Qualifier("pricingBean")
                  setPricing(value: any) {
                    this.pricing = value;
                  }

                  report() {
                    return this.metrics + "|" + this.pricing;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-mixed-injection")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-mixed-injection")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.MixedInjectionServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(MixedInjectionDependencyConfig.class);
                context.register(componentClass);
                context.refresh();

                final Object service = context.getBean(componentClass);
                final Method report = componentClass.getMethod("report");
                final Object result = report.invoke(service);
                assertEquals("metrics|pricing", String.valueOf(result));
            }
        }
    }

    @Test
    void dependencyDiagnosticsReportMissingBeanInTsj33dSubset() throws Exception {
        final Path entryFile = tempDir.resolve("missing-bean.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class MissingBeanService {
                  @Autowired
                  @Qualifier("missingBean")
                  dep: any;

                  ping() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-missing-bean")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-missing-bean")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.MissingBeanServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(componentClass);
                final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
                assertTrue(exception.getMessage().contains("TSJ-SPRING-COMPONENT"));
                assertTrue(exception.getMessage().contains("TSJ33D-INJECTION-MODES"));
                assertTrue(exception.getMessage().contains("missingBean"));
            }
        }
    }

    @Test
    void dependencyDiagnosticsReportAmbiguousBeanInTsj33dSubset() throws Exception {
        final Path entryFile = tempDir.resolve("ambiguous-bean.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class AmbiguousConsumer {
                  constructor(dep: any) {
                  }

                  ping() {
                    return "ok";
                  }
                }

                @Service
                class FirstDependency {
                  value() {
                    return "a";
                  }
                }

                @Service
                class SecondDependency {
                  value() {
                    return "b";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-ambiguous-bean")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-ambiguous-bean")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                for (String className : componentArtifact.componentClassNames()) {
                    context.register(Class.forName(
                            "dev.tsj.generated.spring." + className + "TsjComponent",
                            true,
                            classLoader
                    ));
                }
                final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
                assertTrue(exception.getMessage().contains("TSJ-SPRING-COMPONENT"));
                assertTrue(exception.getMessage().contains("TSJ33D-INJECTION-MODES"));
                assertTrue(exception.getMessage().toLowerCase().contains("ambiguous"));
            }
        }
    }

    @Test
    void dependencyDiagnosticsReportPrimaryConflictInTsj33dSubset() throws Exception {
        final Path entryFile = tempDir.resolve("primary-conflict.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                @Primary
                class PrimaryDependencyA {
                  value() {
                    return "a";
                  }
                }

                @Service
                @Primary
                class PrimaryDependencyB {
                  value() {
                    return "b";
                  }
                }

                @Service
                class PrimaryConflictConsumer {
                  constructor(dep: any) {
                  }

                  ping() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-primary-conflict")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-primary-conflict")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                for (String className : componentArtifact.componentClassNames()) {
                    context.register(Class.forName(
                            "dev.tsj.generated.spring." + className + "TsjComponent",
                            true,
                            classLoader
                    ));
                }
                final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
                assertTrue(exception.getMessage().contains("TSJ-SPRING-COMPONENT"));
                assertTrue(exception.getMessage().contains("TSJ33D-INJECTION-MODES"));
                assertTrue(exception.getMessage().toLowerCase().contains("primary"));
            }
        }
    }

    @Test
    void classProxyStrategyCommitsTransactionalMethodWithoutJdkProxyInTsj35a() throws Exception {
        RecordingTransactionManager.reset();
        final Path entryFile = tempDir.resolve("transactional-class-proxy.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BillingService {
                  @Transactional({ proxyTargetClass: true })
                  charge(amount: any) {
                    return "charged:" + amount;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-class-proxy")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-class-proxy")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.BillingServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(TransactionManagerConfig.class);
                context.register(componentClass);
                context.refresh();

                final Object service = context.getBean(componentClass);
                final Method charge = componentClass.getMethod("charge", Object.class);
                final Object result = charge.invoke(service, 9);
                assertEquals("charged:9", String.valueOf(result));
                assertFalse(Proxy.isProxyClass(service.getClass()));
                assertEquals(1, RecordingTransactionManager.beginCount());
                assertEquals(1, RecordingTransactionManager.commitCount());
                assertEquals(0, RecordingTransactionManager.rollbackCount());
            }
        }
    }

    @Test
    void classProxyStrategyRequiresTransactionManagerWithTsj35aDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("transactional-class-proxy-missing-manager.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BillingService {
                  @Transactional({ proxyTargetClass: true })
                  charge(amount: any) {
                    return amount;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-class-proxy-missing-manager")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components-class-proxy-missing-manager")
        );
        compileSources(componentArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.spring.BillingServiceTsjComponent";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> componentClass = Class.forName(generatedClassName, true, classLoader);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(componentClass);
                final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
                assertTrue(exception.getMessage().contains("TSJ-SPRING-AOP"));
                assertTrue(exception.getMessage().contains("TSJ35A-CLASS-PROXY"));
                assertTrue(exception.getMessage().contains("PlatformTransactionManager"));
            }
        }
    }

    @Test
    void classProxyStrategyRejectsFinalProxyTargetsWithTsj35aDiagnostic() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionManagerConfig.class);
            context.register(FinalTransactionalClassProxyTarget.class);
            final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
            assertTrue(exception.getMessage().contains("TSJ-SPRING-AOP"));
            assertTrue(exception.getMessage().contains("TSJ35A-CLASS-PROXY"));
            assertTrue(exception.getMessage().toLowerCase().contains("final"));
        }
    }

    @Test
    void classProxyStrategyRejectsFinalTransactionalMethodsWithTsj35aDiagnostic() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionManagerConfig.class);
            context.register(FinalTransactionalMethodProxyTarget.class);
            final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
            assertTrue(exception.getMessage().contains("TSJ-SPRING-AOP"));
            assertTrue(exception.getMessage().contains("TSJ35A-CLASS-PROXY"));
            assertTrue(exception.getMessage().toLowerCase().contains("final"));
            assertTrue(exception.getMessage().toLowerCase().contains("method"));
        }
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
                fail("Generated TS component adapter compile failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    @Configuration
    public static class DependencyConfig {
        @Bean
        public String greetingPrefix() {
            return "prefix";
        }
    }

    @Configuration
    public static class TransactionManagerConfig {
        @Bean
        public PlatformTransactionManager platformTransactionManager() {
            return new RecordingTransactionManager();
        }
    }

    @Configuration
    public static class MixedInjectionDependencyConfig {
        @Bean
        public String clockBean() {
            return "clock";
        }

        @Bean
        public String metricsBean() {
            return "metrics";
        }

        @Bean
        public String pricingBean() {
            return "pricing";
        }
    }

    @Transactional
    public static final class FinalTransactionalClassProxyTarget {
        public String ping() {
            return "ok";
        }

        public String __tsjProxyStrategy() {
            return "class";
        }
    }

    @Transactional
    public static final class FinalTransactionalMethodProxyTarget {
        public final String ping() {
            return "ok";
        }

        public String __tsjProxyStrategy() {
            return "class";
        }
    }

    public static final class RecordingTransactionManager implements PlatformTransactionManager {
        private static final List<String> EVENTS = new ArrayList<>();

        static void reset() {
            EVENTS.clear();
        }

        static int beginCount() {
            return count("begin");
        }

        static int commitCount() {
            return count("commit");
        }

        static int rollbackCount() {
            return count("rollback");
        }

        @Override
        public TransactionStatus begin(final String beanName, final String methodName) {
            EVENTS.add("begin:" + beanName + ":" + methodName);
            return new TransactionStatus(beanName, methodName);
        }

        @Override
        public void commit(final TransactionStatus status) {
            EVENTS.add("commit:" + status.beanName() + ":" + status.methodName());
        }

        @Override
        public void rollback(final TransactionStatus status) {
            EVENTS.add("rollback:" + status.beanName() + ":" + status.methodName());
        }

        private static int count(final String prefix) {
            int total = 0;
            for (String event : EVENTS) {
                if (event.startsWith(prefix + ":")) {
                    total++;
                }
            }
            return total;
        }
    }
}
