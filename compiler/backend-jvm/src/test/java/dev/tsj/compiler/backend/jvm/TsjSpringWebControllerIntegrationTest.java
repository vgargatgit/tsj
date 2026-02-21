package dev.tsj.compiler.backend.jvm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.tsj.runtime.TsjObject;
import dev.tsj.runtime.TsjUndefined;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TsjSpringWebControllerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void tsDecoratedControllerDispatchesRequestAndMappedErrors() throws Exception {
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

                  @GetMapping("/validate")
                  validate(value: string) {
                    if (value === "bad") {
                      throw "bad-value";
                    }
                    return "ok:" + value;
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

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        final String generatedClassName = "dev.tsj.generated.web.EchoControllerTsjController";
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(generatedClassName, true, classLoader);
            final Object controller = controllerClass.getDeclaredConstructor().newInstance();
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller);

            final MockResponse success = dispatcher.dispatch("GET", "/api/echo", Map.of("arg0", "x"));
            assertEquals(200, success.status());
            assertTrue(success.body().contains("echo:x"));

            final MockResponse failure = dispatcher.dispatch("GET", "/api/validate", Map.of("arg0", "bad"));
            assertEquals(400, failure.status());
            assertTrue(failure.body().contains("mapped"));
        }
    }

    @Test
    void generatedAdapterRetainsDecoratorAttributeValuesForReflection() throws Exception {
        final Path entryFile = tempDir.resolve("rich-attributes.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping({ value: "/api" })
                class EchoController {
                  @GetMapping({ path: "/echo" })
                  echo(value: string) {
                    return "echo:" + value;
                  }

                  @ExceptionHandler({ value: [classOf("dev.tsj.runtime.TsjThrownException"), classOf("java.lang.IllegalArgumentException")] })
                  @ResponseStatus({ value: enum("org.springframework.http.HttpStatus.BAD_REQUEST") })
                  onThrown(error: any) {
                    return "mapped";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.web.EchoControllerTsjController",
                    true,
                    classLoader
            );
            final RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
            assertEquals("/api", requestMapping.value());

            final Method routeMethod = controllerClass.getMethod("echo", Object.class);
            final GetMapping getMapping = routeMethod.getAnnotation(GetMapping.class);
            assertEquals("/echo", getMapping.value());

            final Method errorMethod = controllerClass.getMethod("onThrown", Throwable.class);
            final ExceptionHandler exceptionHandler = errorMethod.getAnnotation(ExceptionHandler.class);
            assertEquals(2, exceptionHandler.value().length);
            assertEquals("dev.tsj.runtime.TsjThrownException", exceptionHandler.value()[0].getName());
            assertEquals("java.lang.IllegalArgumentException", exceptionHandler.value()[1].getName());

            final ResponseStatus responseStatus = errorMethod.getAnnotation(ResponseStatus.class);
            assertEquals(HttpStatus.BAD_REQUEST, responseStatus.value());
        }
    }

    @Test
    void generatedAdapterRetainsParameterNamesAndBindingAnnotations() throws Exception {
        final Path entryFile = tempDir.resolve("parameter-bindings.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ParamController {
                  @GetMapping("/lookup/{id}")
                  lookup(@PathVariable("id") id: string, @RequestHeader("X-Tenant") tenant: string, @RequestParam({ value: "q" }) query: string, @RequestBody payload: any) {
                    return id + ":" + tenant + ":" + query + ":" + payload;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.web.ParamControllerTsjController",
                    true,
                    classLoader
            );
            final Method method = controllerClass.getMethod(
                    "lookup",
                    Object.class,
                    Object.class,
                    Object.class,
                    Object.class
            );
            final Parameter[] parameters = method.getParameters();
            assertEquals(4, parameters.length);
            assertEquals("id", parameters[0].getName());
            assertEquals("tenant", parameters[1].getName());
            assertEquals("query", parameters[2].getName());
            assertEquals("payload", parameters[3].getName());

            assertEquals("id", parameters[0].getAnnotation(PathVariable.class).value());
            assertEquals("X-Tenant", parameters[1].getAnnotation(RequestHeader.class).value());
            assertEquals("q", parameters[2].getAnnotation(RequestParam.class).value());
            assertTrue(parameters[3].isAnnotationPresent(RequestBody.class));
        }
    }

    @Test
    void tsDecoratedControllerDispatchesPathVariableHeaderQueryAndBodyBindings() throws Exception {
        final Path entryFile = tempDir.resolve("binding-main.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class BindingController {
                  @PostMapping("/users/{id}")
                  update(@PathVariable("id") id: string, @RequestHeader("X-Tenant") tenant: string, @RequestParam("q") query: string, @RequestBody payload: any) {
                    return id + ":" + tenant + ":" + query + ":" + payload;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.web.BindingControllerTsjController",
                    true,
                    classLoader
            );
            final Object controller = controllerClass.getDeclaredConstructor().newInstance();
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller);
            final MockResponse response = dispatcher.dispatch(
                    new MockRequest(
                            "POST",
                            "/api/users/42",
                            Map.of("q", "value"),
                            Map.of("X-Tenant", "acme"),
                            "body-data"
                    )
            );
            assertEquals(200, response.status());
            assertTrue(response.body().contains("42:acme:value:body-data"));
        }
    }

    @Test
    void dispatcherReportsStructuredBindingFailureForMissingRequiredHeader() throws Exception {
        final Path entryFile = tempDir.resolve("binding-missing-header.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class BindingController {
                  @PostMapping("/users/{id}")
                  update(@PathVariable("id") id: string, @RequestHeader("X-Tenant") tenant: string, @RequestParam("q") query: string, @RequestBody payload: any) {
                    return id + ":" + tenant + ":" + query + ":" + payload;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.web.BindingControllerTsjController",
                    true,
                    classLoader
            );
            final Object controller = controllerClass.getDeclaredConstructor().newInstance();
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller);

            final IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> dispatcher.dispatch(
                            new MockRequest(
                                    "POST",
                                    "/api/users/42",
                                    Map.of("q", "value"),
                                    Map.of(),
                                    "body-data"
                            )
                    )
            );
            assertTrue(exception.getMessage().contains("POST /api/users/{id}"));
            assertTrue(exception.getMessage().contains("X-Tenant"));
            assertTrue(exception.getMessage().contains("tenant"));
        }
    }

    @Test
    void tsDecoratedControllerAppliesRouteResponseStatusAndSerializesObjectLiteralBody() throws Exception {
        final Path entryFile = tempDir.resolve("response-status-main.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ResponseController {
                  @GetMapping("/created")
                  @ResponseStatus(201)
                  created() {
                    return { id: "42", tags: ["alpha", "beta"] };
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.web.ResponseControllerTsjController",
                    true,
                    classLoader
            );
            final Object controller = controllerClass.getDeclaredConstructor().newInstance();
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller);
            final MockResponse response = dispatcher.dispatch("GET", "/api/created", Map.of());

            assertEquals(201, response.status());
            assertTrue(response.body().contains("\"id\":\"42\""));
            assertTrue(response.body().contains("\"tags\":[\"alpha\",\"beta\"]"));
        }
    }

    @Test
    void dispatcherEmitsExplicitDiagnosticForUnsupportedResponseBodyShape() throws Exception {
        final Path entryFile = tempDir.resolve("unsupported-response.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ResponseController {
                  @GetMapping("/callable")
                  callable() {
                    return () => "hi";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out"));
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.web.ResponseControllerTsjController",
                    true,
                    classLoader
            );
            final Object controller = controllerClass.getDeclaredConstructor().newInstance();
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller);
            final IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> dispatcher.dispatch("GET", "/api/callable", Map.of())
            );
            assertTrue(exception.getMessage().contains("TSJ-WEB-RESPONSE"));
            assertTrue(exception.getMessage().contains("GET /api/callable"));
        }
    }

    @Test
    void controllerConstructorInjectionWorksAcrossMultipleControllersInTsj34dSubset() throws Exception {
        Assumptions.assumeTrue(
                BootedHttpServer.socketBindingAvailable(),
                "Local sockets unavailable in this runtime."
        );
        final Path entryFile = tempDir.resolve("tsj34d-multi-controller.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/users")
                class UserController {
                  constructor(@Qualifier("prefixBean") prefix: any, @Qualifier("suffixBean") suffix: any) {
                    this.prefix = prefix;
                    this.suffix = suffix;
                  }

                  @GetMapping("/name")
                  name(@RequestParam("value") value: string) {
                    return this.prefix + value + this.suffix;
                  }
                }

                @RestController
                @RequestMapping("/orders")
                class OrderController {
                  constructor(@Qualifier("prefixBean") prefix: any) {
                    this.prefix = prefix;
                  }

                  @GetMapping("/id")
                  id(@RequestParam("value") value: string) {
                    return this.prefix + value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-tsj34d-multi-controller")
        );
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web-tsj34d-multi-controller")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> userControllerClass = Class.forName(
                    "dev.tsj.generated.web.UserControllerTsjController",
                    true,
                    classLoader
            );
            final Class<?> orderControllerClass = Class.forName(
                    "dev.tsj.generated.web.OrderControllerTsjController",
                    true,
                    classLoader
            );
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(WebControllerDependencyConfig.class);
                context.register(userControllerClass);
                context.register(orderControllerClass);
                context.refresh();

                final Object userController = context.getBean(userControllerClass);
                final Object orderController = context.getBean(orderControllerClass);
                try (BootedHttpServer userServer = BootedHttpServer.start(userController);
                     BootedHttpServer orderServer = BootedHttpServer.start(orderController)) {
                    final HttpResult userResponse = sendRequest(
                            userServer.baseUri(),
                            new HttpScenario("user", "GET", "/users/name?value=ada", Map.of(), null)
                    );
                    assertEquals(200, userResponse.statusCode());
                    assertTrue(userResponse.body().contains("prefix-ada-suffix"));

                    final HttpResult orderResponse = sendRequest(
                            orderServer.baseUri(),
                            new HttpScenario("order", "GET", "/orders/id?value=42", Map.of(), null)
                    );
                    assertEquals(200, orderResponse.statusCode());
                    assertTrue(orderResponse.body().contains("prefix-42"));
                }
            }
        }
    }

    @Test
    void missingControllerConstructorDependencyEmitsTsj34dDiagnostics() throws Exception {
        final Path entryFile = tempDir.resolve("tsj34d-missing-dependency.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class MissingDependencyController {
                  constructor(@Qualifier("missingBean") dep: any) {
                    this.dep = dep;
                  }

                  @GetMapping("/health")
                  health() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-tsj34d-missing-dependency")
        );
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web-tsj34d-missing-dependency")
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.web.MissingDependencyControllerTsjController",
                    true,
                    classLoader
            );
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(classLoader);
                context.register(controllerClass);
                final IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
                assertTrue(exception.getMessage().contains("TSJ-WEB-CONTROLLER"));
                assertTrue(exception.getMessage().contains("TSJ34D-CONTROLLER-DI"));
                assertTrue(exception.getMessage().contains("MissingDependencyControllerTsjController"));
                assertTrue(exception.getMessage().contains("missingBean"));
            }
        }
    }

    @Test
    void bootedHttpServerHandlesRoutingBindingSerializationAndMappedErrors() throws Exception {
        Assumptions.assumeTrue(
                BootedHttpServer.socketBindingAvailable(),
                "Local sockets unavailable in this runtime."
        );
        final Path entryFile = tempDir.resolve("tsj34c-http.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ApiController {
                  @GetMapping("/health")
                  health() {
                    return { status: "up", version: 1 };
                  }

                  @PostMapping("/users/{id}")
                  @ResponseStatus(201)
                  create(@PathVariable("id") id: string, @RequestHeader("X-Tenant") tenant: string, @RequestParam("q") query: string, @RequestBody payload: any) {
                    return { id: id, tenant: tenant, query: query, payload: payload };
                  }

                  @GetMapping("/fail")
                  fail(@RequestParam("reason") reason: string) {
                    throw reason;
                  }

                  @ExceptionHandler("dev.tsj.runtime.TsjThrownException")
                  @ResponseStatus(400)
                  onThrown(error: any) {
                    return { error: "mapped" };
                  }
                }
                """,
                UTF_8
        );

        try (LoadedController controller = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.ApiControllerTsjController"
        ); BootedHttpServer server = BootedHttpServer.start(controller.instance())) {
            final HttpResult health = sendRequest(
                    server.baseUri(),
                    new HttpScenario("health", "GET", "/api/health", Map.of(), null)
            );
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"up\""));
            assertTrue(health.body().contains("\"version\":1"));

            final HttpResult created = sendRequest(
                    server.baseUri(),
                    new HttpScenario(
                            "create",
                            "POST",
                            "/api/users/42?q=qa",
                            Map.of("X-Tenant", "acme"),
                            "payload-1"
                    )
            );
            assertEquals(201, created.statusCode());
            assertTrue(created.body().contains("\"id\":\"42\""));
            assertTrue(created.body().contains("\"tenant\":\"acme\""));
            assertTrue(created.body().contains("\"query\":\"qa\""));
            assertTrue(created.body().contains("\"payload\":\"payload-1\""));

            final HttpResult mappedError = sendRequest(
                    server.baseUri(),
                    new HttpScenario("mappedError", "GET", "/api/fail?reason=bad", Map.of(), null)
            );
            assertEquals(400, mappedError.statusCode());
            assertTrue(mappedError.body().contains("\"error\":\"mapped\""));
        }
    }

    @Test
    void bootedHttpServerSupportsTsj34eJsonAndTextConverterSubset() throws Exception {
        Assumptions.assumeTrue(
                BootedHttpServer.socketBindingAvailable(),
                "Local sockets unavailable in this runtime."
        );
        final Path entryFile = tempDir.resolve("tsj34e-converters.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ConverterController {
                  @PostMapping("/echo")
                  echo(@RequestBody payload: any) {
                    return payload;
                  }

                  @GetMapping("/plain")
                  plain(@RequestParam("value") value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        try (LoadedController controller = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.ConverterControllerTsjController"
        ); BootedHttpServer server = BootedHttpServer.start(controller.instance())) {
            final HttpResult jsonEcho = sendRequest(
                    server.baseUri(),
                    new HttpScenario(
                            "jsonEcho",
                            "POST",
                            "/api/echo",
                            Map.of("Content-Type", "application/json", "Accept", "application/json"),
                            "{\"name\":\"tsj\",\"count\":2}"
                    )
            );
            assertEquals(200, jsonEcho.statusCode());
            assertTrue(jsonEcho.body().contains("\"name\":\"tsj\""));
            assertTrue(jsonEcho.body().contains("\"count\":2"));

            final HttpResult plain = sendRequest(
                    server.baseUri(),
                    new HttpScenario(
                            "plain",
                            "GET",
                            "/api/plain?value=ok",
                            Map.of("Accept", "text/plain"),
                            null
                    )
            );
            assertEquals(200, plain.statusCode());
            assertEquals("ok", plain.body());
        }
    }

    @Test
    void bootedHttpServerEmitsTsj34eErrorEnvelopeForUnsupportedMediaType() throws Exception {
        Assumptions.assumeTrue(
                BootedHttpServer.socketBindingAvailable(),
                "Local sockets unavailable in this runtime."
        );
        final Path entryFile = tempDir.resolve("tsj34e-media-error.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class MediaController {
                  @PostMapping("/echo")
                  echo(@RequestBody payload: any) {
                    return payload;
                  }
                }
                """,
                UTF_8
        );

        try (LoadedController controller = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.MediaControllerTsjController"
        ); BootedHttpServer server = BootedHttpServer.start(controller.instance())) {
            final HttpResult unsupported = sendRequest(
                    server.baseUri(),
                    new HttpScenario(
                            "unsupportedMedia",
                            "POST",
                            "/api/echo",
                            Map.of("Content-Type", "application/xml", "Accept", "application/json"),
                            "<payload/>"
                    )
            );
            assertEquals(400, unsupported.statusCode());
            assertTrue(unsupported.body().contains("\"code\":\"TSJ-WEB-ERROR\""));
            assertTrue(unsupported.body().contains("\"type\":\"conversion\""));
            assertTrue(unsupported.body().contains("TSJ-WEB-CONVERTER"));
            assertTrue(unsupported.body().contains("POST /api/echo"));
        }
    }

    @Test
    void dispatcherSupportsTsj34eJsonAndTextConverterSubset() throws Exception {
        final Path entryFile = tempDir.resolve("tsj34e-dispatcher-converters.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class DispatcherConverterController {
                  @PostMapping("/echo")
                  echo(@RequestBody payload: any) {
                    return payload;
                  }

                  @GetMapping("/plain")
                  plain(@RequestParam("value") value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        try (LoadedController controller = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.DispatcherConverterControllerTsjController"
        )) {
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller.instance());
            final MockResponse jsonEcho = dispatcher.dispatch(
                    new MockRequest(
                            "POST",
                            "/api/echo",
                            Map.of(),
                            Map.of("Content-Type", "application/json", "Accept", "application/json"),
                            "{\"name\":\"tsj\",\"count\":2}"
                    )
            );
            assertEquals(200, jsonEcho.status());
            assertTrue(jsonEcho.body().contains("\"name\":\"tsj\""));
            assertTrue(jsonEcho.body().contains("\"count\":2"));

            final MockResponse plain = dispatcher.dispatch(
                    new MockRequest(
                            "GET",
                            "/api/plain",
                            Map.of("value", "ok"),
                            Map.of("Accept", "text/plain"),
                            null
                    )
            );
            assertEquals(200, plain.status());
            assertEquals("ok", plain.body());
        }
    }

    @Test
    void dispatcherEmitsTsj34eErrorEnvelopeForUnsupportedMediaType() throws Exception {
        final Path entryFile = tempDir.resolve("tsj34e-dispatcher-media-error.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class DispatcherMediaController {
                  @PostMapping("/echo")
                  echo(@RequestBody payload: any) {
                    return payload;
                  }
                }
                """,
                UTF_8
        );

        try (LoadedController controller = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.DispatcherMediaControllerTsjController"
        )) {
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller.instance());
            final MockResponse unsupported = dispatcher.dispatchHttp(
                    new MockRequest(
                            "POST",
                            "/api/echo",
                            Map.of(),
                            Map.of("Content-Type", "application/xml", "Accept", "application/json"),
                            "<payload/>"
                    )
            );
            assertEquals(400, unsupported.status());
            assertTrue(unsupported.body().contains("\"code\":\"TSJ-WEB-ERROR\""));
            assertTrue(unsupported.body().contains("\"type\":\"conversion\""));
            assertTrue(unsupported.body().contains("TSJ-WEB-CONVERTER"));
            assertTrue(unsupported.body().contains("POST /api/echo"));
        }
    }

    @Test
    void dispatcherTsj34eConverterAndEnvelopeParityMatchesReferenceController() throws Exception {
        final Path entryFile = tempDir.resolve("tsj34e-dispatcher-parity.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class DispatcherParityController {
                  @PostMapping("/echo")
                  echo(@RequestBody payload: any) {
                    return payload;
                  }

                  @GetMapping("/plain")
                  plain(@RequestParam("value") value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        try (LoadedController tsController = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.DispatcherParityControllerTsjController"
        )) {
            final MockWebDispatcher tsDispatcher = new MockWebDispatcher(tsController.instance());
            final MockWebDispatcher referenceDispatcher = new MockWebDispatcher(new ReferenceConverterController());
            final List<MockRequest> scenarios = List.of(
                    new MockRequest(
                            "POST",
                            "/api/echo",
                            Map.of(),
                            Map.of("Content-Type", "application/json", "Accept", "application/json"),
                            "{\"id\":42,\"name\":\"demo\"}"
                    ),
                    new MockRequest(
                            "GET",
                            "/api/plain",
                            Map.of("value", "ok"),
                            Map.of("Accept", "text/plain"),
                            null
                    ),
                    new MockRequest(
                            "POST",
                            "/api/echo",
                            Map.of(),
                            Map.of("Content-Type", "application/xml", "Accept", "application/json"),
                            "<x/>"
                    )
            );
            final List<ConformanceResult> results = new ArrayList<>();
            for (MockRequest scenario : scenarios) {
                final MockResponse tsResponse = tsDispatcher.dispatchHttp(scenario);
                final MockResponse referenceResponse = referenceDispatcher.dispatchHttp(scenario);
                assertEquals(referenceResponse.status(), tsResponse.status());
                final String scenarioName = scenario.method()
                        + " "
                        + scenario.path()
                        + " "
                        + scenario.headers().getOrDefault("Content-Type", "none");
                if (scenario.path().equals("/api/echo")
                        && "POST".equals(scenario.method())
                        && scenario.headers().getOrDefault("Content-Type", "").contains("application/xml")) {
                    assertTrue(tsResponse.body().contains("\"code\":\"TSJ-WEB-ERROR\""));
                    assertTrue(tsResponse.body().contains("\"type\":\"conversion\""));
                    assertTrue(tsResponse.body().contains("\"endpoint\":\"POST /api/echo\""));
                    assertTrue(referenceResponse.body().contains("\"code\":\"TSJ-WEB-ERROR\""));
                    assertTrue(referenceResponse.body().contains("\"type\":\"conversion\""));
                    assertTrue(referenceResponse.body().contains("\"endpoint\":\"POST /api/echo\""));
                    results.add(
                            new ConformanceResult(
                                    scenarioName,
                                    new HttpResult(tsResponse.status(), tsResponse.body()),
                                    new HttpResult(referenceResponse.status(), referenceResponse.body()),
                                    true
                            )
                    );
                    continue;
                }
                assertEquals(referenceResponse.body(), tsResponse.body());
                results.add(
                        new ConformanceResult(
                                scenarioName,
                                new HttpResult(tsResponse.status(), tsResponse.body()),
                                new HttpResult(referenceResponse.status(), referenceResponse.body()),
                                true
                        )
                );
            }
            final String reportJson = renderConformanceReport("TSJ-34e-converter-conformance", results);
            final Path reportPath = tempDir.resolve("tsj34e-web-converter-report.json");
            Files.writeString(reportPath, reportJson, UTF_8);
            assertTrue(Files.exists(reportPath));
            assertTrue(Files.readString(reportPath, UTF_8).contains("\"match\":true"));

            final Path moduleReportPath = resolveModuleConverterConformanceReportPath();
            Files.createDirectories(moduleReportPath.getParent());
            Files.writeString(moduleReportPath, reportJson, UTF_8);
            assertTrue(Files.exists(moduleReportPath));
        }
    }

    @Test
    void tsj34eConverterAndEnvelopeParityMatchesReferenceController() throws Exception {
        Assumptions.assumeTrue(
                BootedHttpServer.socketBindingAvailable(),
                "Local sockets unavailable in this runtime."
        );
        final Path entryFile = tempDir.resolve("tsj34e-parity.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ParityController {
                  @PostMapping("/echo")
                  echo(@RequestBody payload: any) {
                    return payload;
                  }

                  @GetMapping("/plain")
                  plain(@RequestParam("value") value: string) {
                    return value;
                  }
                }
                """,
                UTF_8
        );
        final List<HttpScenario> scenarios = List.of(
                new HttpScenario(
                        "jsonEcho",
                        "POST",
                        "/api/echo",
                        Map.of("Content-Type", "application/json", "Accept", "application/json"),
                        "{\"id\":42,\"name\":\"demo\"}"
                ),
                new HttpScenario(
                        "plain",
                        "GET",
                        "/api/plain?value=ok",
                        Map.of("Accept", "text/plain"),
                        null
                ),
                new HttpScenario(
                        "unsupportedMedia",
                        "POST",
                        "/api/echo",
                        Map.of("Content-Type", "application/xml", "Accept", "application/json"),
                        "<x/>"
                )
        );

        try (LoadedController tsController = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.ParityControllerTsjController"
        ); BootedHttpServer tsServer = BootedHttpServer.start(tsController.instance());
             BootedHttpServer referenceServer = BootedHttpServer.start(new ReferenceConverterController())) {
            for (HttpScenario scenario : scenarios) {
                final HttpResult tsResult = sendRequest(tsServer.baseUri(), scenario);
                final HttpResult referenceResult = sendRequest(referenceServer.baseUri(), scenario);
                assertEquals(referenceResult.statusCode(), tsResult.statusCode(), scenario.name());
                assertEquals(referenceResult.body(), tsResult.body(), scenario.name());
            }
        }
    }

    @Test
    void startupDiagnosticsSeparateCompileAndRuntimeWiringFailures() throws Exception {
        final Path invalidEntryFile = tempDir.resolve("tsj34c-invalid.ts");
        Files.writeString(
                invalidEntryFile,
                """
                @RestController
                class InvalidController {
                  @ExceptionHandler("java.lang.IllegalArgumentException")
                  onError(error: any) {
                    return "broken";
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException compileException = assertThrows(
                JvmCompilationException.class,
                () -> new TsjSpringWebControllerGenerator().generate(
                        invalidEntryFile,
                        "dev.tsj.generated.MainProgram",
                        tempDir.resolve("generated-web")
                )
        );
        assertEquals("TSJ-WEB-CONTROLLER", compileException.code());

        final IllegalStateException runtimeException = assertThrows(
                IllegalStateException.class,
                () -> BootedHttpServer.start(new Object())
        );
        assertTrue(runtimeException.getMessage().contains("TSJ-WEB-BOOT"));
        assertTrue(runtimeException.getMessage().contains("runtime wiring"));
    }

    @Test
    void conformanceReportLinksTsAndReferenceHttpBehavior() throws Exception {
        Assumptions.assumeTrue(
                BootedHttpServer.socketBindingAvailable(),
                "Local sockets unavailable in this runtime."
        );
        final Path entryFile = tempDir.resolve("tsj34c-report.ts");
        Files.writeString(
                entryFile,
                """
                @RestController
                @RequestMapping("/api")
                class ReportController {
                  @GetMapping("/health")
                  health() {
                    return { status: "up", version: 1 };
                  }

                  @PostMapping("/users/{id}")
                  @ResponseStatus(201)
                  create(@PathVariable("id") id: string, @RequestHeader("X-Tenant") tenant: string, @RequestParam("q") query: string, @RequestBody payload: any) {
                    return { id: id, tenant: tenant, query: query, payload: payload };
                  }

                  @GetMapping("/fail")
                  fail(@RequestParam("reason") reason: string) {
                    throw reason;
                  }

                  @ExceptionHandler("dev.tsj.runtime.TsjThrownException")
                  @ResponseStatus(400)
                  onThrown(error: any) {
                    return { error: "mapped" };
                  }
                }
                """,
                UTF_8
        );

        final List<HttpScenario> scenarios = List.of(
                new HttpScenario("health", "GET", "/api/health", Map.of(), null),
                new HttpScenario("create", "POST", "/api/users/99?q=q1", Map.of("X-Tenant", "team"), "payload-x"),
                new HttpScenario("mappedError", "GET", "/api/fail?reason=bad", Map.of(), null)
        );

        final List<ConformanceResult> results = new ArrayList<>();
        try (LoadedController tsController = compileAndLoadController(
                entryFile,
                "dev.tsj.generated.web.ReportControllerTsjController"
        ); BootedHttpServer tsServer = BootedHttpServer.start(tsController.instance());
             BootedHttpServer referenceServer = BootedHttpServer.start(new ReferenceWebController())) {
            for (HttpScenario scenario : scenarios) {
                final HttpResult tsResult = sendRequest(tsServer.baseUri(), scenario);
                final HttpResult referenceResult = sendRequest(referenceServer.baseUri(), scenario);
                final boolean match = tsResult.statusCode() == referenceResult.statusCode()
                        && tsResult.body().equals(referenceResult.body());
                results.add(new ConformanceResult(scenario.name(), tsResult, referenceResult, match));
                assertTrue(match, "Scenario did not match reference: " + scenario.name());
            }
        }

        final Path reportPath = tempDir.resolve("tsj34c-web-conformance-report.json");
        final String reportJson = renderConformanceReport("TSJ-34c-http-conformance", results);
        Files.writeString(reportPath, reportJson, UTF_8);
        assertTrue(Files.exists(reportPath));
        assertTrue(Files.readString(reportPath, UTF_8).contains("\"match\":true"));

        final Path moduleReportPath = resolveModuleConformanceReportPath();
        Files.createDirectories(moduleReportPath.getParent());
        Files.writeString(moduleReportPath, reportJson, UTF_8);
        assertTrue(Files.exists(moduleReportPath));
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
                fail("Generated TS controller adapter compile failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    private LoadedController compileAndLoadController(
            final Path entryFile,
            final String generatedClassName
    ) throws Exception {
        final String stem = generatedClassName.replace('.', '-');
        final JvmCompiledArtifact compiledArtifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out-" + stem)
        );
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web-" + stem)
        );
        compileSources(controllerArtifact.sourceFiles(), compiledArtifact.outputDirectory());
        final URLClassLoader classLoader = new URLClassLoader(
                new URL[]{compiledArtifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        );
        final Class<?> controllerClass = Class.forName(generatedClassName, true, classLoader);
        final Object controller = controllerClass.getDeclaredConstructor().newInstance();
        return new LoadedController(controller, classLoader);
    }

    private static HttpResult sendRequest(
            final URI baseUri,
            final HttpScenario scenario
    ) throws Exception {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(
                baseUri.resolve(stripLeadingSlash(scenario.pathAndQuery()))
        );
        for (Map.Entry<String, String> header : scenario.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        if ("GET".equals(scenario.method())) {
            builder.GET();
        } else if ("POST".equals(scenario.method())) {
            final String body = scenario.body() == null ? "" : scenario.body();
            builder.POST(HttpRequest.BodyPublishers.ofString(body, UTF_8));
            boolean hasContentTypeHeader = false;
            for (String headerName : scenario.headers().keySet()) {
                if ("Content-Type".equalsIgnoreCase(headerName)) {
                    hasContentTypeHeader = true;
                    break;
                }
            }
            if (!hasContentTypeHeader) {
                builder.header("Content-Type", "text/plain; charset=utf-8");
            }
        } else {
            throw new IllegalArgumentException("Unsupported scenario method: " + scenario.method());
        }
        final HttpResponse<String> response = HttpClient.newHttpClient().send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(UTF_8)
        );
        return new HttpResult(response.statusCode(), response.body());
    }

    private static String renderConformanceReport(
            final String suiteName,
            final List<ConformanceResult> results
    ) {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"").append(escapeJson(suiteName)).append("\",\"scenarios\":[");
        for (int index = 0; index < results.size(); index++) {
            final ConformanceResult result = results.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{\"name\":\"")
                    .append(escapeJson(result.name()))
                    .append("\",\"match\":")
                    .append(result.match())
                    .append(",\"tsj\":{\"status\":")
                    .append(result.tsj().statusCode())
                    .append(",\"body\":\"")
                    .append(escapeJson(result.tsj().body()))
                    .append("\"},\"reference\":{\"status\":")
                    .append(result.reference().statusCode())
                    .append(",\"body\":\"")
                    .append(escapeJson(result.reference().body()))
                    .append("\"}}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private static Path resolveModuleConformanceReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSpringWebControllerIntegrationTest.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve("tsj34c-web-conformance-report.json");
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target/tsj34c-web-conformance-report.json");
    }

    private static Path resolveModuleConverterConformanceReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSpringWebControllerIntegrationTest.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve("tsj34e-web-converter-report.json");
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target/tsj34e-web-converter-report.json");
    }

    private static String stripLeadingSlash(final String pathAndQuery) {
        if (pathAndQuery == null || pathAndQuery.isEmpty()) {
            return "";
        }
        if (pathAndQuery.startsWith("/")) {
            return pathAndQuery.substring(1);
        }
        return pathAndQuery;
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Configuration
    static class WebControllerDependencyConfig {
        @Bean
        public String prefixBean() {
            return "prefix-";
        }

        @Bean
        public String suffixBean() {
            return "-suffix";
        }
    }

    @RestController
    @RequestMapping("/api")
    private static final class ReferenceWebController {
        @GetMapping("/health")
        public Object health() {
            return linkedMap("status", "up", "version", Integer.valueOf(1));
        }

        @PostMapping("/users/{id}")
        @ResponseStatus(HttpStatus.CREATED)
        public Object create(
                @PathVariable("id") final Object id,
                @RequestHeader("X-Tenant") final Object tenant,
                @RequestParam("q") final Object query,
                @RequestBody final Object payload
        ) {
            return linkedMap(
                    "id",
                    String.valueOf(id),
                    "tenant",
                    String.valueOf(tenant),
                    "query",
                    String.valueOf(query),
                    "payload",
                    String.valueOf(payload)
            );
        }

        @GetMapping("/fail")
        public Object fail(@RequestParam("reason") final Object reason) {
            throw new IllegalArgumentException(String.valueOf(reason));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public Object onError(final IllegalArgumentException error) {
            return linkedMap("error", "mapped");
        }
    }

    @RestController
    @RequestMapping("/api")
    private static final class ReferenceConverterController {
        @PostMapping("/echo")
        public Object echo(@RequestBody final Object payload) {
            return payload;
        }

        @GetMapping("/plain")
        public Object plain(@RequestParam("value") final Object value) {
            return String.valueOf(value);
        }
    }

    private static Map<String, Object> linkedMap(final Object... keyValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return Map.copyOf(map);
    }

    private static final class BootedHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final URI baseUri;

        private BootedHttpServer(final HttpServer server, final URI baseUri) {
            this.server = server;
            this.baseUri = baseUri;
        }

        private static boolean socketBindingAvailable() {
            try (ServerSocket probe = new ServerSocket()) {
                probe.bind(new InetSocketAddress("127.0.0.1", 0));
                return true;
            } catch (final IOException ignored) {
                return false;
            }
        }

        private static BootedHttpServer start(final Object controller) throws IOException {
            if (controller == null) {
                throw new IllegalStateException("TSJ-WEB-BOOT runtime wiring failed: controller instance is null.");
            }
            if (controller.getClass().getAnnotation(RestController.class) == null) {
                throw new IllegalStateException(
                        "TSJ-WEB-BOOT runtime wiring failed: controller is missing @RestController."
                );
            }
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller);
            if (!dispatcher.hasRequestHandlers()) {
                throw new IllegalStateException(
                        "TSJ-WEB-BOOT runtime wiring failed: no request handlers for "
                                + controller.getClass().getName()
                                + "."
                );
            }
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> handleExchange(exchange, dispatcher));
            server.start();
            final URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            return new BootedHttpServer(server, baseUri);
        }

        private static void handleExchange(
                final HttpExchange exchange,
                final MockWebDispatcher dispatcher
        ) throws IOException {
            final String method = exchange.getRequestMethod();
            final String path = exchange.getRequestURI().getPath();
            final Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            final Map<String, String> headers = flattenHeaders(exchange);
            final byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            final String body = requestBytes.length == 0 ? null : new String(requestBytes, UTF_8);
            final MockResponse response = dispatcher.dispatchHttp(
                    new MockRequest(method, path, query, headers, body)
            );
            writeExchangeResponse(exchange, response);
        }

        private static void writeExchangeResponse(
                final HttpExchange exchange,
                final MockResponse response
        ) throws IOException {
            final byte[] responseBytes = response.body().getBytes(UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), responseBytes.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(responseBytes);
            }
        }

        private static Map<String, String> parseQuery(final String rawQuery) {
            if (rawQuery == null || rawQuery.isBlank()) {
                return Map.of();
            }
            final Map<String, String> query = new LinkedHashMap<>();
            for (String segment : rawQuery.split("&")) {
                if (segment.isBlank()) {
                    continue;
                }
                final int separator = segment.indexOf('=');
                final String rawKey = separator < 0 ? segment : segment.substring(0, separator);
                final String rawValue = separator < 0 ? "" : segment.substring(separator + 1);
                query.put(urlDecode(rawKey), urlDecode(rawValue));
            }
            return Map.copyOf(query);
        }

        private static Map<String, String> flattenHeaders(final HttpExchange exchange) {
            final Map<String, String> headers = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                headers.put(entry.getKey(), entry.getValue().getFirst());
            }
            return Map.copyOf(headers);
        }

        private static String urlDecode(final String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        private URI baseUri() {
            return baseUri;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record LoadedController(Object instance, URLClassLoader classLoader) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            classLoader.close();
        }
    }

    private record HttpScenario(
            String name,
            String method,
            String pathAndQuery,
            Map<String, String> headers,
            String body
    ) {
    }

    private record HttpResult(int statusCode, String body) {
    }

    private record ConformanceResult(
            String name,
            HttpResult tsj,
            HttpResult reference,
            boolean match
    ) {
    }

    private static final class MockWebDispatcher {
        private final Object controller;
        private final String basePath;
        private final List<RouteHandler> requestHandlers;
        private final List<Method> exceptionHandlers;

        private MockWebDispatcher(final Object controller) {
            this.controller = controller;
            this.basePath = resolveBasePath(controller.getClass());
            this.requestHandlers = resolveRequestHandlers(controller.getClass());
            this.exceptionHandlers = resolveExceptionHandlers(controller.getClass());
        }

        private boolean hasRequestHandlers() {
            return !requestHandlers.isEmpty();
        }

        private MockResponse dispatch(final String method, final String path, final Map<String, String> query) {
            return dispatch(new MockRequest(method, path, query, Map.of(), null));
        }

        private MockResponse dispatch(final MockRequest request) {
            for (RouteHandler routeHandler : requestHandlers) {
                if (!routeHandler.httpMethod().equals(request.method())) {
                    continue;
                }
                final String endpointTemplate = normalizePath(basePath, routeHandler.pathTemplate());
                final PathMatch pathMatch = matchPath(endpointTemplate, request.path());
                if (pathMatch == null) {
                    continue;
                }
                try {
                    final Object result = routeHandler.method().invoke(
                            controller,
                            resolveArguments(routeHandler, request, pathMatch.pathVariables(), endpointTemplate)
                    );
                    final ResponseStatus responseStatus = routeHandler.method().getAnnotation(ResponseStatus.class);
                    final int statusCode = responseStatus == null ? 200 : responseStatus.value().value();
                    return new MockResponse(
                            statusCode,
                            serializeResponseBody(
                                    result,
                                    routeHandler.httpMethod(),
                                    endpointTemplate,
                                    request.headers()
                            )
                    );
                } catch (final IllegalAccessException illegalAccessException) {
                    throw new IllegalStateException("Failed to invoke request handler.", illegalAccessException);
                } catch (final InvocationTargetException invocationTargetException) {
                    return handleException(invocationTargetException.getTargetException());
                }
            }
            throw new IllegalStateException("No request handler for " + request.method() + " " + request.path());
        }

        private MockResponse dispatchHttp(final MockRequest request) {
            try {
                return dispatch(request);
            } catch (final IllegalStateException illegalStateException) {
                return toErrorResponse(request.method(), request.path(), illegalStateException);
            }
        }

        private static MockResponse toErrorResponse(
                final String method,
                final String path,
                final IllegalStateException illegalStateException
        ) {
            final String message = illegalStateException.getMessage() == null
                    ? "runtime failure"
                    : illegalStateException.getMessage();
            final String endpoint = method + " " + path;
            final boolean notFound = message.startsWith("No request handler");
            final boolean bindingFailure = message.startsWith("Request binding failure");
            final boolean conversionFailure = message.contains("TSJ-WEB-CONVERTER")
                    || message.startsWith("TSJ-WEB-RESPONSE");
            final int status = notFound ? 404 : (bindingFailure || conversionFailure ? 400 : 500);
            final String errorType = notFound
                    ? "not_found"
                    : (bindingFailure ? "binding" : (conversionFailure ? "conversion" : "runtime"));
            final String text = "{\"error\":{\"code\":\"TSJ-WEB-ERROR\",\"type\":\""
                    + escapeJson(errorType)
                    + "\",\"endpoint\":\""
                    + escapeJson(endpoint)
                    + "\",\"message\":\""
                    + escapeJson(message)
                    + "\"}}";
            return new MockResponse(status, text);
        }

        private MockResponse handleException(final Throwable throwable) {
            for (Method handler : exceptionHandlers) {
                final ExceptionHandler exceptionHandler = handler.getAnnotation(ExceptionHandler.class);
                if (exceptionHandler == null) {
                    continue;
                }
                for (Class<? extends Throwable> candidate : exceptionHandler.value()) {
                    if (!candidate.isInstance(throwable)) {
                        continue;
                    }
                    final ResponseStatus responseStatus = handler.getAnnotation(ResponseStatus.class);
                    final HttpStatus status = responseStatus == null
                            ? HttpStatus.INTERNAL_SERVER_ERROR
                            : responseStatus.value();
                    try {
                        final Object body = handler.invoke(controller, throwable);
                        return new MockResponse(
                                status.value(),
                                serializeResponseBody(body, "EXCEPTION", handler.getName(), Map.of())
                        );
                    } catch (final IllegalAccessException | InvocationTargetException exception) {
                        throw new IllegalStateException("Failed to invoke exception handler.", exception);
                    }
                }
            }
            throw new IllegalStateException("No mapped exception handler for " + throwable.getClass().getName());
        }

        private static String resolveBasePath(final Class<?> controllerClass) {
            final RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
            return requestMapping == null ? "" : requestMapping.value();
        }

        private static List<RouteHandler> resolveRequestHandlers(final Class<?> controllerClass) {
            final List<RouteHandler> handlers = new ArrayList<>();
            for (Method method : controllerClass.getMethods()) {
                final GetMapping getMapping = method.getAnnotation(GetMapping.class);
                if (getMapping != null) {
                    handlers.add(new RouteHandler(method, "GET", getMapping.value()));
                    continue;
                }
                final PostMapping postMapping = method.getAnnotation(PostMapping.class);
                if (postMapping != null) {
                    handlers.add(new RouteHandler(method, "POST", postMapping.value()));
                    continue;
                }
                final PutMapping putMapping = method.getAnnotation(PutMapping.class);
                if (putMapping != null) {
                    handlers.add(new RouteHandler(method, "PUT", putMapping.value()));
                    continue;
                }
                final DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
                if (deleteMapping != null) {
                    handlers.add(new RouteHandler(method, "DELETE", deleteMapping.value()));
                    continue;
                }
                final PatchMapping patchMapping = method.getAnnotation(PatchMapping.class);
                if (patchMapping != null) {
                    handlers.add(new RouteHandler(method, "PATCH", patchMapping.value()));
                }
            }
            return List.copyOf(handlers);
        }

        private static List<Method> resolveExceptionHandlers(final Class<?> controllerClass) {
            final List<Method> handlers = new ArrayList<>();
            for (Method method : controllerClass.getMethods()) {
                if (method.getAnnotation(ExceptionHandler.class) != null) {
                    handlers.add(method);
                }
            }
            return List.copyOf(handlers);
        }

        private static Object[] resolveArguments(
                final RouteHandler routeHandler,
                final MockRequest request,
                final Map<String, String> pathVariables,
                final String endpointTemplate
        ) {
            final Method method = routeHandler.method();
            final Parameter[] parameters = method.getParameters();
            final Object[] arguments = new Object[parameters.length];
            for (int index = 0; index < parameters.length; index++) {
                final Parameter parameter = parameters[index];
                final RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    final String key = requestParam.value().isBlank() ? "arg" + index : requestParam.value();
                    final String value = request.query().get(key);
                    if (value == null) {
                        throw bindingFailure(
                                routeHandler.httpMethod(),
                                endpointTemplate,
                                parameter.getName(),
                                "request parameter `" + key + "`"
                        );
                    }
                    arguments[index] = value;
                    continue;
                }
                final PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
                if (pathVariable != null) {
                    final String key = pathVariable.value().isBlank() ? parameter.getName() : pathVariable.value();
                    final String value = pathVariables.get(key);
                    if (value == null) {
                        throw bindingFailure(
                                routeHandler.httpMethod(),
                                endpointTemplate,
                                parameter.getName(),
                                "path variable `" + key + "`"
                        );
                    }
                    arguments[index] = value;
                    continue;
                }
                final RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
                if (requestHeader != null) {
                    final String key = requestHeader.value().isBlank() ? parameter.getName() : requestHeader.value();
                    final String value = findHeaderValue(request.headers(), key);
                    if (value == null) {
                        throw bindingFailure(
                                routeHandler.httpMethod(),
                                endpointTemplate,
                                parameter.getName(),
                                "request header `" + key + "`"
                        );
                    }
                    arguments[index] = value;
                    continue;
                }
                final RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
                if (requestBody != null) {
                    if (request.body() == null) {
                        throw bindingFailure(
                                routeHandler.httpMethod(),
                                endpointTemplate,
                                parameter.getName(),
                                "request body"
                        );
                    }
                    arguments[index] = convertRequestBody(
                            String.valueOf(request.body()),
                            request.headers(),
                            routeHandler.httpMethod(),
                            endpointTemplate,
                            parameter.getName()
                    );
                    continue;
                }
                final String fallback = request.query().get("arg" + index);
                arguments[index] = fallback;
            }
            return arguments;
        }

        private static String findHeaderValue(final Map<String, String> headers, final String key) {
            if (headers.containsKey(key)) {
                return headers.get(key);
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private static IllegalStateException bindingFailure(
                final String httpMethod,
                final String endpointTemplate,
                final String parameterName,
                final String missingBinding
        ) {
            return new IllegalStateException(
                    "Request binding failure at endpoint "
                            + httpMethod
                            + " "
                            + endpointTemplate
                            + ", parameter `"
                            + parameterName
                            + "`: missing "
                            + missingBinding
                            + "."
            );
        }

        private static PathMatch matchPath(final String template, final String actualPath) {
            final String normalizedTemplate = stripTrailingSlash(template);
            final String normalizedActual = stripTrailingSlash(actualPath);
            final String[] templateSegments = normalizedTemplate.split("/");
            final String[] actualSegments = normalizedActual.split("/");
            if (templateSegments.length != actualSegments.length) {
                return null;
            }
            final Map<String, String> variables = new LinkedHashMap<>();
            for (int index = 0; index < templateSegments.length; index++) {
                final String templateSegment = templateSegments[index];
                final String actualSegment = actualSegments[index];
                if (templateSegment.startsWith("{")
                        && templateSegment.endsWith("}")
                        && templateSegment.length() > 2) {
                    final String variableName = templateSegment.substring(1, templateSegment.length() - 1);
                    variables.put(variableName, actualSegment);
                    continue;
                }
                if (!templateSegment.equals(actualSegment)) {
                    return null;
                }
            }
            return new PathMatch(Map.copyOf(variables));
        }

        private static String stripTrailingSlash(final String value) {
            if (value == null || value.isEmpty() || "/".equals(value)) {
                return value == null ? "" : value;
            }
            if (value.endsWith("/")) {
                return value.substring(0, value.length() - 1);
            }
            return value;
        }

        private static String serializeResponseBody(
                final Object value,
                final String httpMethod,
                final String endpointTemplate,
                final Map<String, String> headers
        ) {
            final String acceptHeader = findHeaderValue(headers, "Accept");
            try {
                if (prefersTextPlain(acceptHeader)) {
                    return toPlainText(value, httpMethod, endpointTemplate);
                }
                if (!supportsJson(acceptHeader)) {
                    throw new IllegalArgumentException(
                            "unsupported response media type `" + acceptHeader + "`"
                    );
                }
                return toJson(value);
            } catch (final IllegalArgumentException illegalArgumentException) {
                throw new IllegalStateException(
                        "TSJ-WEB-RESPONSE TSJ-WEB-CONVERTER unsupported response body at endpoint "
                                + httpMethod
                                + " "
                                + endpointTemplate
                                + ": "
                                + illegalArgumentException.getMessage(),
                        illegalArgumentException
                );
            }
        }

        private static Object convertRequestBody(
                final String rawBody,
                final Map<String, String> headers,
                final String httpMethod,
                final String endpointTemplate,
                final String parameterName
        ) {
            final String contentTypeHeader = findHeaderValue(headers, "Content-Type");
            final String mediaType = normalizeMediaType(contentTypeHeader);
            if (mediaType == null || "text/plain".equals(mediaType)) {
                return rawBody;
            }
            if (isJsonMediaType(mediaType)) {
                return parseJsonRequestBody(rawBody, httpMethod, endpointTemplate, parameterName);
            }
            throw new IllegalStateException(
                    "TSJ-WEB-CONVERTER unsupported request media type `"
                            + mediaType
                            + "` at endpoint "
                            + httpMethod
                            + " "
                            + endpointTemplate
                            + ", parameter `"
                            + parameterName
                            + "`."
            );
        }

        private static boolean prefersTextPlain(final String acceptHeader) {
            if (acceptHeader == null || acceptHeader.isBlank()) {
                return false;
            }
            final String normalized = acceptHeader.toLowerCase(Locale.ROOT);
            return normalized.contains("text/plain") && !normalized.contains("application/json");
        }

        private static boolean supportsJson(final String acceptHeader) {
            if (acceptHeader == null || acceptHeader.isBlank()) {
                return true;
            }
            final String normalized = acceptHeader.toLowerCase(Locale.ROOT);
            return normalized.contains("application/json")
                    || normalized.contains("*/*")
                    || normalized.contains("text/plain");
        }

        private static String toPlainText(
                final Object value,
                final String httpMethod,
                final String endpointTemplate
        ) {
            if (value == null || value == TsjUndefined.INSTANCE) {
                return "";
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
            throw new IllegalArgumentException(
                    "unsupported text/plain response type "
                            + value.getClass().getName()
                            + " at endpoint "
                            + httpMethod
                            + " "
                            + endpointTemplate
            );
        }

        private static String normalizeMediaType(final String rawHeader) {
            if (rawHeader == null || rawHeader.isBlank()) {
                return null;
            }
            final String[] segments = rawHeader.split(";");
            if (segments.length == 0) {
                return null;
            }
            final String normalized = segments[0].trim().toLowerCase(Locale.ROOT);
            return normalized.isBlank() ? null : normalized;
        }

        private static boolean isJsonMediaType(final String mediaType) {
            return "application/json".equals(mediaType) || mediaType.endsWith("+json");
        }

        private static Object parseJsonRequestBody(
                final String rawBody,
                final String httpMethod,
                final String endpointTemplate,
                final String parameterName
        ) {
            try {
                return parseJsonValue(rawBody);
            } catch (final IllegalArgumentException illegalArgumentException) {
                throw new IllegalStateException(
                        "TSJ-WEB-CONVERTER unsupported JSON request body at endpoint "
                                + httpMethod
                                + " "
                                + endpointTemplate
                                + ", parameter `"
                                + parameterName
                                + "`: "
                                + illegalArgumentException.getMessage(),
                        illegalArgumentException
                );
            }
        }

        private static Object parseJsonValue(final String text) {
            if (text == null) {
                throw new IllegalArgumentException("body is null");
            }
            final int[] cursor = new int[]{0};
            final Object value = parseJsonValue(text, cursor);
            skipJsonWhitespace(text, cursor);
            if (cursor[0] != text.length()) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
            return value;
        }

        private static Object parseJsonValue(final String text, final int[] cursor) {
            skipJsonWhitespace(text, cursor);
            if (cursor[0] >= text.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            final char token = text.charAt(cursor[0]);
            if (token == '{') {
                return parseJsonObject(text, cursor);
            }
            if (token == '[') {
                return parseJsonArray(text, cursor);
            }
            if (token == '"') {
                return parseJsonString(text, cursor);
            }
            if (text.startsWith("true", cursor[0])) {
                cursor[0] += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", cursor[0])) {
                cursor[0] += 5;
                return Boolean.FALSE;
            }
            if (text.startsWith("null", cursor[0])) {
                cursor[0] += 4;
                return null;
            }
            return parseJsonNumber(text, cursor);
        }

        private static Map<String, Object> parseJsonObject(final String text, final int[] cursor) {
            expectJsonChar(text, cursor, '{');
            skipJsonWhitespace(text, cursor);
            final Map<String, Object> map = new LinkedHashMap<>();
            if (cursor[0] < text.length() && text.charAt(cursor[0]) == '}') {
                cursor[0]++;
                return Map.copyOf(map);
            }
            while (cursor[0] < text.length()) {
                final String key = parseJsonString(text, cursor);
                skipJsonWhitespace(text, cursor);
                expectJsonChar(text, cursor, ':');
                final Object value = parseJsonValue(text, cursor);
                map.put(key, value);
                skipJsonWhitespace(text, cursor);
                if (cursor[0] >= text.length()) {
                    break;
                }
                final char separator = text.charAt(cursor[0]++);
                if (separator == '}') {
                    return Map.copyOf(map);
                }
                if (separator != ',') {
                    throw new IllegalArgumentException("expected `,` or `}` in object");
                }
            }
            throw new IllegalArgumentException("unterminated JSON object");
        }

        private static List<Object> parseJsonArray(final String text, final int[] cursor) {
            expectJsonChar(text, cursor, '[');
            skipJsonWhitespace(text, cursor);
            final List<Object> values = new ArrayList<>();
            if (cursor[0] < text.length() && text.charAt(cursor[0]) == ']') {
                cursor[0]++;
                return List.copyOf(values);
            }
            while (cursor[0] < text.length()) {
                values.add(parseJsonValue(text, cursor));
                skipJsonWhitespace(text, cursor);
                if (cursor[0] >= text.length()) {
                    break;
                }
                final char separator = text.charAt(cursor[0]++);
                if (separator == ']') {
                    return List.copyOf(values);
                }
                if (separator != ',') {
                    throw new IllegalArgumentException("expected `,` or `]` in array");
                }
            }
            throw new IllegalArgumentException("unterminated JSON array");
        }

        private static String parseJsonString(final String text, final int[] cursor) {
            expectJsonChar(text, cursor, '"');
            final StringBuilder builder = new StringBuilder();
            while (cursor[0] < text.length()) {
                final char character = text.charAt(cursor[0]++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character != '\\') {
                    builder.append(character);
                    continue;
                }
                if (cursor[0] >= text.length()) {
                    throw new IllegalArgumentException("unterminated escape sequence");
                }
                final char escaped = text.charAt(cursor[0]++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escaped);
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        if (cursor[0] + 4 > text.length()) {
                            throw new IllegalArgumentException("invalid unicode escape");
                        }
                        final String hex = text.substring(cursor[0], cursor[0] + 4);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (final NumberFormatException numberFormatException) {
                            throw new IllegalArgumentException("invalid unicode escape");
                        }
                        cursor[0] += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported escape sequence `\\" + escaped + "`");
                }
            }
            throw new IllegalArgumentException("unterminated JSON string");
        }

        private static Number parseJsonNumber(final String text, final int[] cursor) {
            final int start = cursor[0];
            if (text.charAt(cursor[0]) == '-') {
                cursor[0]++;
            }
            while (cursor[0] < text.length() && Character.isDigit(text.charAt(cursor[0]))) {
                cursor[0]++;
            }
            if (cursor[0] < text.length() && text.charAt(cursor[0]) == '.') {
                cursor[0]++;
                while (cursor[0] < text.length() && Character.isDigit(text.charAt(cursor[0]))) {
                    cursor[0]++;
                }
            }
            if (cursor[0] < text.length()
                    && (text.charAt(cursor[0]) == 'e' || text.charAt(cursor[0]) == 'E')) {
                cursor[0]++;
                if (cursor[0] < text.length()
                        && (text.charAt(cursor[0]) == '+' || text.charAt(cursor[0]) == '-')) {
                    cursor[0]++;
                }
                while (cursor[0] < text.length() && Character.isDigit(text.charAt(cursor[0]))) {
                    cursor[0]++;
                }
            }
            final String numberText = text.substring(start, cursor[0]);
            if (numberText.isBlank() || "-".equals(numberText)) {
                throw new IllegalArgumentException("invalid number token");
            }
            try {
                if (numberText.contains(".") || numberText.contains("e") || numberText.contains("E")) {
                    return Double.valueOf(numberText);
                }
                final long longValue = Long.parseLong(numberText);
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return Integer.valueOf((int) longValue);
                }
                return Long.valueOf(longValue);
            } catch (final NumberFormatException numberFormatException) {
                throw new IllegalArgumentException("invalid number token");
            }
        }

        private static void skipJsonWhitespace(final String text, final int[] cursor) {
            while (cursor[0] < text.length() && Character.isWhitespace(text.charAt(cursor[0]))) {
                cursor[0]++;
            }
        }

        private static void expectJsonChar(final String text, final int[] cursor, final char expected) {
            if (cursor[0] >= text.length() || text.charAt(cursor[0]) != expected) {
                throw new IllegalArgumentException("expected `" + expected + "`");
            }
            cursor[0]++;
        }

        private static String toJson(final Object value) {
            if (value == null || value == TsjUndefined.INSTANCE) {
                return "null";
            }
            if (value instanceof String stringValue) {
                return "\"" + escapeJson(stringValue) + "\"";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
            if (value instanceof TsjObject tsjObject) {
                final Map<String, Object> properties = extractTsjObjectProperties(tsjObject);
                final Integer arrayLength = detectArrayLength(properties);
                if (arrayLength != null) {
                    final List<String> values = new ArrayList<>();
                    for (int index = 0; index < arrayLength.intValue(); index++) {
                        values.add(toJson(properties.get(Integer.toString(index))));
                    }
                    return "[" + String.join(",", values) + "]";
                }
                return toJson(properties);
            }
            if (value instanceof Map<?, ?> mapValue) {
                final List<String> entries = new ArrayList<>();
                for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                    entries.add("\"" + escapeJson(String.valueOf(entry.getKey())) + "\":" + toJson(entry.getValue()));
                }
                return "{" + String.join(",", entries) + "}";
            }
            if (value instanceof Iterable<?> iterableValue) {
                final List<String> values = new ArrayList<>();
                for (Object item : iterableValue) {
                    values.add(toJson(item));
                }
                return "[" + String.join(",", values) + "]";
            }
            if (value.getClass().isArray()) {
                final int length = Array.getLength(value);
                final List<String> values = new ArrayList<>();
                for (int index = 0; index < length; index++) {
                    values.add(toJson(Array.get(value, index)));
                }
                return "[" + String.join(",", values) + "]";
            }
            throw new IllegalArgumentException("unsupported response type " + value.getClass().getName());
        }

        private static Map<String, Object> extractTsjObjectProperties(final TsjObject tsjObject) {
            try {
                final Method accessor = tsjObject.getClass().getMethod("ownPropertiesView");
                final Object result = accessor.invoke(tsjObject);
                if (result instanceof Map<?, ?> mapValue) {
                    return normalizeMap(mapValue);
                }
            } catch (final ReflectiveOperationException ignored) {
                // Fall through to direct-field access for older runtime variants.
            }
            try {
                final Field ownPropertiesField = tsjObject.getClass().getDeclaredField("ownProperties");
                ownPropertiesField.setAccessible(true);
                final Object result = ownPropertiesField.get(tsjObject);
                if (result instanceof Map<?, ?> mapValue) {
                    return normalizeMap(mapValue);
                }
            } catch (final ReflectiveOperationException ignored) {
                // Fall through to explicit diagnostic below.
            }
            throw new IllegalArgumentException("unsupported TSJ object reflection shape");
        }

        private static Map<String, Object> normalizeMap(final Map<?, ?> mapValue) {
            final Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return Map.copyOf(normalized);
        }

        private static Integer detectArrayLength(final Map<String, Object> properties) {
            final Object lengthValue = properties.get("length");
            if (!(lengthValue instanceof Number numberValue)) {
                return null;
            }
            final int length = numberValue.intValue();
            if (length < 0) {
                return null;
            }
            for (int index = 0; index < length; index++) {
                if (!properties.containsKey(Integer.toString(index))) {
                    return null;
                }
            }
            return Integer.valueOf(length);
        }

        private static String escapeJson(final String value) {
            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }

        private static String normalizePath(final String basePath, final String childPath) {
            final String normalizedBase = basePath == null || basePath.isBlank() ? "" : basePath;
            final String normalizedChild = childPath == null || childPath.isBlank() ? "" : childPath;
            if (normalizedBase.endsWith("/") && normalizedChild.startsWith("/")) {
                return normalizedBase.substring(0, normalizedBase.length() - 1) + normalizedChild;
            }
            if (!normalizedBase.isEmpty() && !normalizedChild.isEmpty()
                    && !normalizedBase.endsWith("/") && !normalizedChild.startsWith("/")) {
                return normalizedBase + "/" + normalizedChild;
            }
            return normalizedBase + normalizedChild;
        }
    }

    private record MockRequest(
            String method,
            String path,
            Map<String, String> query,
            Map<String, String> headers,
            Object body
    ) {
    }

    private record RouteHandler(Method method, String httpMethod, String pathTemplate) {
    }

    private record PathMatch(Map<String, String> pathVariables) {
    }

    private record MockResponse(int status, String body) {
    }
}
