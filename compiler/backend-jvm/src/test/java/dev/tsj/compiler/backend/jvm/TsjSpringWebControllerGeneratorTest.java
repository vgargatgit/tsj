package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringWebControllerGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesAdapterSourceFromTsDecoratedControllerClass() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class EchoController {
                  @GetMapping("/echo")
                  echo(value: string) {
                    return "echo:" + value;
                  }

                  @ExceptionHandler("dev.tsj.runtime.TsjThrownException")
                  @ResponseStatus(400)
                  onThrown(error: any) {
                    return "mapped";
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-web")
        );

        assertEquals(1, artifact.sourceFiles().size());
        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RestController"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestMapping(\"/api\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.GetMapping(\"/echo\")"));
        assertTrue(source.contains(
                "@org.springframework.web.bind.annotation.ExceptionHandler(dev.tsj.runtime.TsjThrownException.class)"
        ));
        assertTrue(source.contains(
                "@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)"
        ));
        assertTrue(source.contains("dev.tsj.generated.MainProgram.__tsjInvokeController("));
    }

    @Test
    void exceptionHandlerDecoratorRequiresResponseStatusDecorator() throws Exception {
        final Path entryFile = tempDir.resolve("missing-status.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class BrokenController {
                  @ExceptionHandler("java.lang.IllegalArgumentException")
                  onError(error: any) {
                    return "broken";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringWebControllerGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-web")
                )
        );

        assertEquals("TSJ-WEB-CONTROLLER", exception.code());
        assertTrue(exception.getMessage().contains("@ResponseStatus"));
    }

    @Test
    void ignoresTsClassesThatDoNotMapToRestControllerAdapterEmission() throws Exception {
        final Path entryFile = tempDir.resolve("non-rest.ts");
        Files.writeString(
                entryFile,
                """
                @Controller
                class PlainController {
                  @GetMapping("/echo")
                  echo(value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-web")
        );

        assertEquals(0, artifact.sourceFiles().size());
        assertEquals(0, artifact.controllerClassNames().size());
    }

    @Test
    void supportsDecoratorObjectLiteralAttributesForPathEnumAndClassLiteralArrays() throws Exception {
        final Path entryFile = tempDir.resolve("rich-attributes.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping({ value: "/api" })
                class RichController {
                  @GetMapping({ path: "/echo" })
                  echo(value: string) {
                    return value;
                  }

                  @ExceptionHandler({ value: [classOf("dev.tsj.runtime.TsjThrownException"), classOf("java.lang.IllegalArgumentException")] })
                  @ResponseStatus({ value: enum("org.springframework.http.HttpStatus.BAD_REQUEST") })
                  onError(error: any) {
                    return "mapped";
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-web")
        );

        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestMapping(\"/api\")"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.GetMapping(\"/echo\")"));
        assertTrue(source.contains(
                "@org.springframework.web.bind.annotation.ExceptionHandler({"
                        + "dev.tsj.runtime.TsjThrownException.class, java.lang.IllegalArgumentException.class})"
        ));
        assertTrue(source.contains(
                "@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)"
        ));
    }

    @Test
    void rejectsUnsupportedDecoratorAttributeShapeWithTargetedDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("invalid-attribute.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class BadController {
                  @GetMapping({ value: SOME_CONST })
                  echo(value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringWebControllerGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-web")
                )
        );

        assertEquals("TSJ-DECORATOR-ATTRIBUTE", exception.code());
        assertEquals("TSJ32B-ANNOTATION-ATTRIBUTES", exception.featureId());
    }

    @Test
    void emitsSpringParameterAnnotationsFromTsParameterDecorators() throws Exception {
        final Path entryFile = tempDir.resolve("parameter-bindings.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ParamController {
                  @GetMapping("/lookup/{id}")
                  lookup(@PathVariable("id") id: string, @RequestHeader({ value: "X-Tenant" }) tenant: string, @RequestParam("q") query: string, @RequestBody payload: any) {
                    return id + ":" + tenant + ":" + query + ":" + payload;
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-web")
        );

        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.web.bind.annotation.PathVariable(\"id\") Object id"));
        assertTrue(source.contains(
                "@org.springframework.web.bind.annotation.RequestHeader(\"X-Tenant\") Object tenant"
        ));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestParam(\"q\") Object query"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestBody Object payload"));
    }

    @Test
    void rejectsMultipleParameterDecoratorsOnSingleParameterWithTargetedDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("invalid-parameter-decorators.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class BadController {
                  @GetMapping("/echo")
                  echo(@RequestParam("q") @PathVariable("id") value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringWebControllerGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-web")
                )
        );

        assertEquals("TSJ-DECORATOR-PARAM", exception.code());
        assertEquals("TSJ32C-PARAM-ANNOTATIONS", exception.featureId());
    }

    @Test
    void rejectsPathVariableNotPresentInRouteTemplateWithEndpointContext() throws Exception {
        final Path entryFile = tempDir.resolve("invalid-path-variable.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class BadController {
                  @GetMapping("/users/{id}")
                  lookup(@PathVariable("tenant") tenant: string) {
                    return tenant;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringWebControllerGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-web")
                )
        );

        assertEquals("TSJ-WEB-BINDING", exception.code());
        assertEquals("TSJ34A-REQUEST-BINDING", exception.featureId());
        assertTrue(exception.getMessage().contains("GET /api/users/{id}"));
        assertTrue(exception.getMessage().contains("tenant"));
    }

    @Test
    void rejectsMultipleRequestBodyParametersOnSingleRouteWithEndpointContext() throws Exception {
        final Path entryFile = tempDir.resolve("invalid-request-body-count.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class BadController {
                  @PostMapping("/ingest")
                  ingest(@RequestBody first: any, @RequestBody second: any) {
                    return first;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringWebControllerGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-web")
                )
        );

        assertEquals("TSJ-WEB-BINDING", exception.code());
        assertEquals("TSJ34A-REQUEST-BINDING", exception.featureId());
        assertTrue(exception.getMessage().contains("POST /ingest"));
        assertTrue(exception.getMessage().contains("@RequestBody"));
    }

    @Test
    void usesParameterNameWhenNamedRequestBindingDecoratorArgumentsAreOmitted() throws Exception {
        final Path entryFile = tempDir.resolve("named-binding-defaults.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ParamController {
                  @PostMapping("/users/{id}")
                  lookup(@PathVariable id: string, @RequestHeader tenant: string, @RequestParam query: string, @RequestBody payload: any) {
                    return id + ":" + tenant + ":" + query + ":" + payload;
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-web")
        );

        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.web.bind.annotation.PathVariable(\"id\") Object id"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestHeader(\"tenant\") Object tenant"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestParam(\"query\") Object query"));
        assertTrue(source.contains("@org.springframework.web.bind.annotation.RequestBody Object payload"));
    }

    @Test
    void emitsRouteResponseStatusAnnotationForSuccessfulHandlers() throws Exception {
        final Path entryFile = tempDir.resolve("route-response-status.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class StatusController {
                  @GetMapping("/created")
                  @ResponseStatus(201)
                  created() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-web")
        );

        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("@org.springframework.web.bind.annotation.GetMapping(\"/created\")"));
        assertTrue(source.contains(
                "@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)"
        ));
    }

    @Test
    void emitsConstructorDependencyInjectionWiringForTsj34dControllerSubset() throws Exception {
        final Path entryFile = tempDir.resolve("constructor-di.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class DiController {
                  constructor(@Qualifier("prefixBean") prefix: any, suffix: any) {
                    this.prefix = prefix;
                    this.suffix = suffix;
                  }

                  @GetMapping("/echo")
                  echo(value: string) {
                    return this.prefix + value + this.suffix;
                  }
                }
                """,
                UTF_8
        );

        final TsjSpringWebControllerArtifact artifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                "dev.tsj.generated.MainProgram",
                tempDir.resolve("generated-web")
        );

        final String source = Files.readString(artifact.sourceFiles().getFirst(), UTF_8);
        assertTrue(source.contains("private final Object prefix;"));
        assertTrue(source.contains("private final Object suffix;"));
        assertTrue(source.contains("@org.springframework.beans.factory.annotation.Qualifier(\"prefixBean\") Object prefix"));
        assertTrue(source.contains("Object suffix"));
        assertTrue(source.contains("this.prefix = prefix;"));
        assertTrue(source.contains("this.suffix = suffix;"));
        assertTrue(source.contains("dev.tsj.generated.MainProgram.__tsjInvokeClassWithInjection("));
        assertTrue(source.contains("\"DiController\", \"echo\""));
        assertTrue(source.contains("new Object[]{this.prefix, this.suffix}"));
        assertTrue(!source.contains("public DiControllerTsjController()"));
    }

    @Test
    void rejectsUnsupportedConstructorParameterDecoratorsWithTsj34dDiagnostic() throws Exception {
        final Path entryFile = tempDir.resolve("invalid-constructor-decorator.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                class BrokenController {
                  constructor(@RequestParam("q") dep: any) {
                    this.dep = dep;
                  }

                  @GetMapping("/ok")
                  ok() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringWebControllerGenerator().generate(
                        entryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-web")
                )
        );

        assertEquals("TSJ-WEB-CONTROLLER", exception.code());
        assertEquals("TSJ34D-CONTROLLER-DI", exception.featureId());
        assertTrue(exception.getMessage().contains("constructor"));
        assertTrue(exception.getMessage().contains("@RequestParam"));
    }
}
