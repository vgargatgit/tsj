package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsDecoratorModelExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsDecoratedClassesAndMethodsAcrossRelativeImportGraph() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        final Path depFile = tempDir.resolve("dep.ts");
        Files.writeString(
                entryFile,
                """
                import "./dep.ts";

                @RestController
                @RequestMapping("/api")
                class MainController {
                  @GetMapping("/echo")
                  echo(value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );
        Files.writeString(
                depFile,
                """
                @Service
                class WorkerService {
                  @Bean
                  build() {
                    return 1;
                  }
                }
                """,
                UTF_8
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);

        assertEquals(2, model.classes().size());
        assertTrue(model.classes().stream().anyMatch(clazz ->
                clazz.className().equals("MainController")
                        && clazz.decorators().stream().anyMatch(decorator -> decorator.name().equals("RestController"))
                        && clazz.methods().stream().anyMatch(method ->
                        method.methodName().equals("echo")
                                && method.decorators().stream().anyMatch(decorator -> decorator.name().equals("GetMapping")
                        ))
        ));
        assertTrue(model.classes().stream().anyMatch(clazz ->
                clazz.className().equals("WorkerService")
                        && clazz.decorators().stream().anyMatch(decorator -> decorator.name().equals("Service"))
                        && clazz.methods().stream().anyMatch(method ->
                        method.methodName().equals("build")
                                && method.decorators().stream().anyMatch(decorator -> decorator.name().equals("Bean")
                        ))
        ));
    }

    @Test
    void rejectsUnsupportedDecoratorWithTargetedDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("unsupported.ts");
        Files.writeString(
                entryFile,
                """
                @Magic
                class Demo {
                  value() {
                    return 1;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsDecoratorModelExtractor().extract(entryFile)
        );

        assertEquals("TSJ-DECORATOR-UNSUPPORTED", exception.code());
        assertEquals("TSJ32A-DECORATOR-MODEL", exception.featureId());
        assertTrue(exception.getMessage().contains("@Magic"));
    }

    @Test
    void rejectsDanglingMethodDecoratorWithoutMethodDeclaration() throws Exception {
        final Path entryFile = tempDir.resolve("dangling.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class DemoController {
                  @GetMapping("/x")
                  const value = 1;
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsDecoratorModelExtractor().extract(entryFile)
        );

        assertEquals("TSJ-DECORATOR-TARGET", exception.code());
        assertEquals("TSJ32A-DECORATOR-MODEL", exception.featureId());
        assertTrue(exception.getMessage().contains("class method"));
    }

    @Test
    void extractsMethodParameterDecoratorsForSupportedSubset() throws Exception {
        final Path entryFile = tempDir.resolve("parameter-decorators.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class EchoController {
                  @GetMapping("/echo")
                  echo(@RequestParam("q") query: string, @RequestHeader({ value: "X-Tenant" }) tenant: string, @RequestBody payload: any) {
                    return query + ":" + tenant + ":" + payload;
                  }
                }
                """,
                UTF_8
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final TsDecoratedClass controller = model.classes().stream()
                .filter(value -> "EchoController".equals(value.className()))
                .findFirst()
                .orElseThrow();
        final TsDecoratedMethod method = controller.methods().stream()
                .filter(value -> "echo".equals(value.methodName()))
                .findFirst()
                .orElseThrow();

        assertEquals(3, method.parameters().size());
        assertEquals("query", method.parameters().get(0).name());
        assertEquals("RequestParam", method.parameters().get(0).decorators().getFirst().name());
        assertEquals("\"q\"", method.parameters().get(0).decorators().getFirst().rawArgs());
        assertEquals("tenant", method.parameters().get(1).name());
        assertEquals("RequestHeader", method.parameters().get(1).decorators().getFirst().name());
        assertTrue(method.parameters().get(1).decorators().getFirst().rawArgs().contains("X-Tenant"));
        assertEquals("payload", method.parameters().get(2).name());
        assertEquals("RequestBody", method.parameters().get(2).decorators().getFirst().name());
    }

    @Test
    void rejectsUnsupportedParameterDecoratorWithTargetedDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("unsupported-parameter-decorator.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class EchoController {
                  @GetMapping("/echo")
                  echo(@MagicParam value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsDecoratorModelExtractor().extract(entryFile)
        );

        assertEquals("TSJ-DECORATOR-PARAM", exception.code());
        assertEquals("TSJ32C-PARAM-ANNOTATIONS", exception.featureId());
        assertTrue(exception.getMessage().contains("@MagicParam"));
    }

    @Test
    void extractsDecoratedFieldsAndQualifierDecoratorsForTsj33dSubset() throws Exception {
        final Path entryFile = tempDir.resolve("field-injection-decorators.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                @Primary
                class OrdersService {
                  @Autowired
                  @Qualifier("pricingBean")
                  pricing: any;

                  @Autowired
                  setClock(@Qualifier("clockBean") clock: any) {
                    this.clock = clock;
                  }
                }
                """,
                UTF_8
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final TsDecoratedClass service = model.classes().stream()
                .filter(value -> "OrdersService".equals(value.className()))
                .findFirst()
                .orElseThrow();

        assertTrue(service.decorators().stream().anyMatch(value -> "Primary".equals(value.name())));
        assertEquals(1, service.fields().size());
        final TsDecoratedField field = service.fields().getFirst();
        assertEquals("pricing", field.fieldName());
        assertTrue(field.decorators().stream().anyMatch(value -> "Autowired".equals(value.name())));
        assertTrue(field.decorators().stream().anyMatch(value -> "Qualifier".equals(value.name())));

        final TsDecoratedMethod setter = service.methods().stream()
                .filter(value -> "setClock".equals(value.methodName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, setter.parameters().size());
        assertTrue(setter.parameters().getFirst().decorators().stream()
                .anyMatch(value -> "Qualifier".equals(value.name())));
    }

    @Test
    void extractsValidationDecoratorsForTsj37aSubset() throws Exception {
        final Path entryFile = tempDir.resolve("validation-decorators.ts");
        Files.writeString(
                entryFile,
                """
                @Service
                @Validated
                class ValidationService {
                  validate(@NotBlank({ message: "username.required" }) username: string, @Size({ min: 3, max: 8, message: "alias.length" }) alias: string, @Min({ value: 18, message: "age.min" }) age: number, @Max({ value: 65, message: "age.max" }) score: number, @NotNull({ message: "email.required" }) email: any) {
                    return username + ":" + alias + ":" + age + ":" + score + ":" + email;
                  }
                }
                """,
                UTF_8
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final TsDecoratedClass service = model.classes().stream()
                .filter(value -> "ValidationService".equals(value.className()))
                .findFirst()
                .orElseThrow();
        assertTrue(service.decorators().stream().anyMatch(value -> "Validated".equals(value.name())));

        final TsDecoratedMethod validate = service.methods().stream()
                .filter(value -> "validate".equals(value.methodName()))
                .findFirst()
                .orElseThrow();
        assertEquals(5, validate.parameters().size());
        assertEquals("NotBlank", validate.parameters().get(0).decorators().getFirst().name());
        assertEquals("Size", validate.parameters().get(1).decorators().getFirst().name());
        assertEquals("Min", validate.parameters().get(2).decorators().getFirst().name());
        assertEquals("Max", validate.parameters().get(3).decorators().getFirst().name());
        assertEquals("NotNull", validate.parameters().get(4).decorators().getFirst().name());
    }
}
