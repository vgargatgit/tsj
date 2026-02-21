package dev.tsj.compiler.backend.jvm;

import dev.tsj.compiler.backend.jvm.fixtures.InteropSpringWebFixtureType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InteropSpringWebIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void generatedSpringWebControllerSupportsRequestBindingDtoResponseAndErrorMapping() throws Exception {
        final String fixtureClass = InteropSpringWebFixtureType.class.getName();
        final Path specFile = tempDir.resolve("interop-spring-web.properties");
        Files.writeString(
                specFile,
                """
                allowlist=%s#findUser,%s#validateUser
                targets=%s#findUser,%s#validateUser
                springWebController=true
                springWebBasePath=/users
                springRequestMappings.findUser=GET /find
                springRequestMappings.validateUser=GET /validate
                springErrorMappings=java.lang.IllegalArgumentException:400
                """.formatted(fixtureClass, fixtureClass, fixtureClass, fixtureClass),
                UTF_8
        );

        final InteropBridgeArtifact artifact = new InteropBridgeGenerator().generate(specFile, tempDir.resolve("out"));
        final Path classesDir = tempDir.resolve("classes");
        compileSources(artifact.sourceFiles(), classesDir);

        final String bridgeSimpleName = artifact.sourceFiles().getFirst().getFileName().toString().replace(".java", "");
        final String bridgeClassName = "dev.tsj.generated.interop." + bridgeSimpleName;
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            final Class<?> controllerClass = Class.forName(bridgeClassName, true, classLoader);
            final Object controller = controllerClass.getDeclaredConstructor().newInstance();
            final MockWebDispatcher dispatcher = new MockWebDispatcher(controller);

            final MockResponse success = dispatcher.dispatch("GET", "/users/find", Map.of("arg0", "42"));
            assertEquals(200, success.status());
            assertTrue(success.body().contains("\"id\":\"42\""));
            assertTrue(success.body().contains("\"name\":\"user-42\""));
            assertTrue(success.body().contains("\"tags\":[\"alpha\",\"beta\"]"));

            final MockResponse failure = dispatcher.dispatch("GET", "/users/validate", Map.of("arg0", "bad"));
            assertEquals(400, failure.status());
            assertTrue(failure.body().contains("bad user id"));
        }
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
                fail("Bridge source compile failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    private static final class MockWebDispatcher {
        private final Object controller;
        private final String basePath;
        private final List<Method> requestHandlers;
        private final List<Method> exceptionHandlers;

        private MockWebDispatcher(final Object controller) {
            this.controller = controller;
            this.basePath = resolveBasePath(controller.getClass());
            this.requestHandlers = resolveRequestHandlers(controller.getClass());
            this.exceptionHandlers = resolveExceptionHandlers(controller.getClass());
        }

        private MockResponse dispatch(final String method, final String path, final Map<String, String> query) {
            for (Method handler : requestHandlers) {
                final GetMapping getMapping = handler.getAnnotation(GetMapping.class);
                if (getMapping == null) {
                    continue;
                }
                if (!"GET".equals(method)) {
                    continue;
                }
                final String fullPath = normalizePath(basePath, getMapping.value());
                if (!fullPath.equals(path)) {
                    continue;
                }
                try {
                    final Object result = handler.invoke(controller, resolveArguments(handler, query));
                    return new MockResponse(200, toJson(result));
                } catch (final IllegalAccessException illegalAccessException) {
                    throw new IllegalStateException("Failed to invoke request handler.", illegalAccessException);
                } catch (final InvocationTargetException invocationTargetException) {
                    final Throwable cause = invocationTargetException.getTargetException();
                    return handleException(cause);
                }
            }
            throw new IllegalStateException("Handler not found for " + method + " " + path);
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
                        return new MockResponse(status.value(), toJson(body));
                    } catch (final IllegalAccessException | InvocationTargetException exception) {
                        throw new IllegalStateException("Failed to invoke exception handler.", exception);
                    }
                }
            }
            throw new IllegalStateException("No mapped exception handler for " + throwable.getClass().getName());
        }

        private static String resolveBasePath(final Class<?> controllerClass) {
            final RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);
            return mapping == null ? "" : mapping.value();
        }

        private static List<Method> resolveRequestHandlers(final Class<?> controllerClass) {
            final List<Method> handlers = new ArrayList<>();
            for (Method method : controllerClass.getMethods()) {
                if (method.getAnnotation(GetMapping.class) != null) {
                    handlers.add(method);
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

        private static Object[] resolveArguments(final Method method, final Map<String, String> query) {
            final Parameter[] parameters = method.getParameters();
            final Object[] arguments = new Object[parameters.length];
            for (int index = 0; index < parameters.length; index++) {
                final Parameter parameter = parameters[index];
                final RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                final String key = requestParam == null ? "arg" + index : requestParam.value();
                arguments[index] = convert(query.get(key), parameter.getType());
            }
            return arguments;
        }

        private static Object convert(final String rawValue, final Class<?> targetType) {
            if (targetType == String.class) {
                return rawValue;
            }
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(rawValue);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(rawValue);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(rawValue);
            }
            throw new IllegalArgumentException("Unsupported test conversion for " + targetType.getName());
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

    private static String toJson(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return "\"" + escapeJson(stringValue) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
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
        if (value.getClass().isRecord()) {
            final RecordComponent[] components = value.getClass().getRecordComponents();
            final List<String> entries = new ArrayList<>();
            for (RecordComponent component : components) {
                try {
                    entries.add(
                            "\"" + escapeJson(component.getName()) + "\":" + toJson(component.getAccessor().invoke(value))
                    );
                } catch (final IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalStateException("Failed to serialize record value.", exception);
                }
            }
            return "{" + String.join(",", entries) + "}";
        }
        final List<String> entries = new ArrayList<>();
        for (java.lang.reflect.Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                entries.add("\"" + escapeJson(field.getName()) + "\":" + toJson(field.get(value)));
            } catch (final IllegalAccessException illegalAccessException) {
                throw new IllegalStateException("Failed to serialize object field.", illegalAccessException);
            }
        }
        return "{" + String.join(",", entries) + "}";
    }

    private static String escapeJson(final String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record MockResponse(int status, String body) {
    }
}
