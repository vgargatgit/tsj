package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.bind.annotation.RequestParam;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TsjGeneratedMetadataParityTest {
    @TempDir
    Path tempDir;

    @Test
    void generatedClassFamiliesRetainMetadataParityInTsj39a() throws Exception {
        final Path entryFile = tempDir.resolve("metadata-parity.ts");
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
                tempDir.resolve("out")
        );
        final TsjSpringComponentArtifact componentArtifact = new TsjSpringComponentGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-components")
        );
        final TsjSpringWebControllerArtifact controllerArtifact = new TsjSpringWebControllerGenerator().generate(
                entryFile,
                compiledArtifact.className(),
                tempDir.resolve("generated-web")
        );

        final Path interopSpec = tempDir.resolve("interop.properties");
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
                tempDir.resolve("generated-interop")
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
            final Method mainMethod = programClass.getMethod("main", String[].class);
            assertTrue(mainMethod.getParameters()[0].isNamePresent());
            assertEquals("args", mainMethod.getParameters()[0].getName());

            final Class<?> componentClass = Class.forName(
                    "dev.tsj.generated.spring.BillingServiceTsjComponent",
                    true,
                    classLoader
            );
            final Method chargeMethod = componentClass.getMethod("charge", Object.class);
            assertTrue(chargeMethod.getParameters()[0].isNamePresent());
            assertEquals("amount", chargeMethod.getParameters()[0].getName());

            final Class<?> proxyClass = Class.forName(
                    "dev.tsj.generated.spring.BillingServiceTsjComponentApi",
                    true,
                    classLoader
            );
            final Method proxyChargeMethod = proxyClass.getMethod("charge", Object.class);
            assertTrue(proxyChargeMethod.getParameters()[0].isNamePresent());
            assertEquals("amount", proxyChargeMethod.getParameters()[0].getName());

            final Class<?> webControllerClass = Class.forName(
                    "dev.tsj.generated.web.EchoControllerTsjController",
                    true,
                    classLoader
            );
            final Method echoMethod = webControllerClass.getMethod("echo", Object.class);
            final Parameter echoParameter = echoMethod.getParameters()[0];
            assertTrue(echoParameter.isNamePresent());
            assertEquals("query", echoParameter.getName());
            assertEquals("q", echoParameter.getAnnotation(RequestParam.class).value());

            final Path bridgeSource = bridgeArtifact.sourceFiles().getFirst();
            final String bridgeSimpleName = bridgeSource.getFileName().toString().replace(".java", "");
            final Class<?> bridgeClass = Class.forName(
                    "dev.tsj.generated.interop." + bridgeSimpleName,
                    true,
                    classLoader
            );
            assertTrue(bridgeClass.isAnnotationPresent(Deprecated.class));
            assertAllPublicMethodParameterNamesArePresent(bridgeClass);
        }
    }

    private static void assertAllPublicMethodParameterNamesArePresent(final Class<?> type) {
        boolean sawPublicMethod = false;
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            sawPublicMethod = true;
            for (Parameter parameter : method.getParameters()) {
                assertTrue(
                        parameter.isNamePresent(),
                        "Expected parameter metadata on " + type.getName() + "." + method.getName()
                );
            }
        }
        assertFalse(!sawPublicMethod, "Expected at least one public method on " + type.getName());
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
                fail("Generated source compile failed: " + diagnostics.getDiagnostics());
            }
        }
    }
}
