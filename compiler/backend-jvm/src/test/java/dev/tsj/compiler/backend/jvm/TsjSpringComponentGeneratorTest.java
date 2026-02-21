package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringComponentGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesSpringComponentAdapterFromTsServiceDecorator() throws Exception {
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

        final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-components")
        );

        assertEquals(1, artifact.sourceFiles().size());
        assertEquals("GreetingService", artifact.componentClassNames().getFirst());
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.stereotype.Service"));
        assertTrue(source.contains("public GreetingServiceTsjComponent(Object prefix)"));
        assertTrue(source.contains("return dev.tsj.generated.MainProgram.__tsjInvokeClassWithInjection("));
        assertTrue(source.contains("\"GreetingService\", \"greet\""));
        assertTrue(source.contains("new Object[]{prefix}"));
    }

    @Test
    void ignoresTsClassesWithoutComponentStereotypes() throws Exception {
        final Path entryFile = tempDir.resolve("plain.ts");
        Files.writeString(
                entryFile,
                """
                class PlainService {
                  hello() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-components")
        );

        assertEquals(0, artifact.sourceFiles().size());
        assertEquals(0, artifact.componentClassNames().size());
    }

    @Test
    void emitsBeanMethodsForTsConfigurationClass() throws Exception {
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

                  helper() {
                    return "noop";
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-components")
        );

        assertEquals(1, artifact.sourceFiles().size());
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.context.annotation.Configuration"));
        assertTrue(source.contains("@org.springframework.context.annotation.Bean"));
        assertTrue(source.contains("public Object greeting("));
        assertTrue(source.contains("public Object helper("));
    }

    @Test
    void rejectsBeanDecoratorOnNonConfigurationClass() throws Exception {
        final Path entryFile = tempDir.resolve("bad-bean.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BadService {
                  @Bean
                  create() {
                    return "bad";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringComponentGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-components")
                )
        );

        assertEquals("TSJ-SPRING-COMPONENT", exception.code());
        assertEquals("TSJ33B-DI-SURFACE", exception.featureId());
        assertTrue(exception.getMessage().contains("@Bean"));
    }

    @Test
    void emitsLifecycleAnnotationsForDecoratedMethods() throws Exception {
        final Path entryFile = tempDir.resolve("lifecycle.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class LifecycleService {
                  @PostConstruct
                  init() {
                    return "ok";
                  }

                  @PreDestroy
                  shutdown() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-components")
        );

        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@jakarta.annotation.PostConstruct"));
        assertTrue(source.contains("@jakarta.annotation.PreDestroy"));
    }

    @Test
    void rejectsLifecycleMethodWithParametersInTsj33cSubset() throws Exception {
        final Path entryFile = tempDir.resolve("lifecycle-params.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BadLifecycleService {
                  @PostConstruct
                  init(value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringComponentGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-components")
                )
        );

        assertEquals("TSJ-SPRING-COMPONENT", exception.code());
        assertEquals("TSJ33C-LIFECYCLE", exception.featureId());
        assertTrue(exception.getMessage().contains("@PostConstruct"));
    }

    @Test
    void emitsTransactionalAnnotationsForSupportedTsj35Subset() throws Exception {
        final Path entryFile = tempDir.resolve("transactional.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                @Transactional
                class BillingService {
                  @Transactional
                  charge(amount: any) {
                    return "ok:" + amount;
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-components")
        );

        assertEquals(2, artifact.sourceFiles().size());
        final String componentSource = Files.readString(artifact.sourceFiles().get(1), UTF_8);
        assertTrue(componentSource.contains("@org.springframework.transaction.annotation.Transactional"));
        assertTrue(componentSource.contains("public class BillingServiceTsjComponent implements BillingServiceTsjComponentApi"));
        assertTrue(componentSource.contains("public Object charge(Object amount)"));

        final String apiSource = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(apiSource.contains("public interface BillingServiceTsjComponentApi"));
        assertTrue(apiSource.contains("Object charge(Object amount);"));
    }

    @Test
    void rejectsTransactionalDecoratorOnConstructorInTsj35Subset() throws Exception {
        final Path entryFile = tempDir.resolve("constructor-transactional.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BillingService {
                  @Transactional
                  constructor(dep: any) {
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringComponentGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-components")
                )
        );

        assertEquals("TSJ-SPRING-AOP", exception.code());
        assertEquals("TSJ35-AOP-PROXY", exception.featureId());
        assertTrue(exception.getMessage().contains("constructor"));
        assertTrue(exception.getMessage().contains("@Transactional"));
    }

    @Test
    void emitsTsj33dFieldAndSetterInjectionWiringForSupportedSubset() throws Exception {
        final Path entryFile = tempDir.resolve("injection-modes.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                @Primary
                class InjectionService {
                  @Autowired
                  @Qualifier("metricsBean")
                  metrics: any;

                  @Autowired
                  @Qualifier("pricingBean")
                  setPricing(value: any) {
                    this.pricing = value;
                  }

                  constructor(@Qualifier("clockBean") clock: any) {
                  }

                  report() {
                    return this.metrics + ":" + this.pricing;
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-components")
        );

        assertEquals(1, artifact.sourceFiles().size());
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.context.annotation.Primary"));
        assertTrue(source.contains("@org.springframework.beans.factory.annotation.Autowired"));
        assertTrue(source.contains("@org.springframework.beans.factory.annotation.Qualifier(\"metricsBean\")"));
        assertTrue(source.contains("private Object metrics;"));
        assertTrue(source.contains("public void setPricing("));
        assertTrue(source.contains("@org.springframework.beans.factory.annotation.Qualifier(\"pricingBean\")"));
        assertTrue(source.contains("return dev.tsj.generated.MainProgram.__tsjInvokeClassWithInjection("));
        assertTrue(source.contains("\"InjectionService\", \"report\""));
    }

    @Test
    void rejectsUnsupportedAutowiredSetterShapeInTsj33dSubset() throws Exception {
        final Path entryFile = tempDir.resolve("bad-setter.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BadSetterService {
                  @Autowired
                  wire(a: any, b: any) {
                    this.a = a;
                    this.b = b;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringComponentGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-components")
                )
        );

        assertEquals("TSJ-SPRING-COMPONENT", exception.code());
        assertEquals("TSJ33D-INJECTION-MODES", exception.featureId());
        assertTrue(exception.getMessage().contains("@Autowired"));
    }

    @Test
    void supportsClassProxyStrategyWhenTransactionalDecoratorRequestsProxyTargetClass() throws Exception {
        final Path entryFile = tempDir.resolve("class-proxy.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                class BillingService {
                  @Transactional({ proxyTargetClass: true })
                  charge(amount: any) {
                    return "ok:" + amount;
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringComponentArtifact artifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-components")
        );

        assertEquals(1, artifact.sourceFiles().size());
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("public String __tsjProxyStrategy()"));
        assertTrue(source.contains("return \"class\";"));
        assertTrue(source.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)"));
        assertTrue(source.contains("private org.springframework.transaction.PlatformTransactionManager __tsjTxManager;"));
        assertTrue(source.contains("__tsjTxManager.begin("));
        assertTrue(source.contains("__tsjTxManager.commit("));
        assertTrue(source.contains("__tsjTxManager.rollback("));
    }

    @Test
    void rejectsConflictingTransactionalProxyStrategyHintsInTsj35a() throws Exception {
        final Path entryFile = tempDir.resolve("conflicting-proxy-strategy.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                @Transactional({ proxyTargetClass: false })
                class BillingService {
                  @Transactional({ proxyTargetClass: true })
                  charge(amount: any) {
                    return amount;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringComponentGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-components")
                )
        );

        assertEquals("TSJ-SPRING-AOP", exception.code());
        assertEquals("TSJ35A-CLASS-PROXY", exception.featureId());
        assertTrue(exception.getMessage().contains("proxyTargetClass"));
    }
}
