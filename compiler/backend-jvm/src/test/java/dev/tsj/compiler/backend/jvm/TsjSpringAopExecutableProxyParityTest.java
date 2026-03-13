package dev.tsj.compiler.backend.jvm;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSpringAopExecutableProxyParityTest {
    @TempDir
    Path tempDir;

    @Test
    void strictExecutableClassSupportsSpringAopClassProxying() throws Exception {
        final Path entryFile = tempDir.resolve("billing-service.ts");
        Files.writeString(
                entryFile,
                """
                class BillingService {
                  charge(customer: string) {
                    return "charged:" + customer;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                entryFile,
                tempDir.resolve("out"),
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{artifact.outputDirectory().toUri().toURL()},
                getClass().getClassLoader()
        )) {
            final Class<?> targetClass = Class.forName(
                    "dev.tsj.generated.BillingService__TsjStrictNative",
                    true,
                    classLoader
            );
            final Object target = targetClass.getDeclaredConstructor().newInstance();
            final AtomicInteger interceptions = new AtomicInteger();

            final ProxyFactory proxyFactory = new ProxyFactory(target);
            proxyFactory.setProxyTargetClass(true);
            proxyFactory.addAdvice((MethodInterceptor) invocation -> {
                if ("charge".equals(invocation.getMethod().getName())) {
                    interceptions.incrementAndGet();
                }
                final Object result = invocation.proceed();
                if (result instanceof String value && "charge".equals(invocation.getMethod().getName())) {
                    return value + ":via-proxy";
                }
                return result;
            });

            final Object proxy = proxyFactory.getProxy(classLoader);
            final Method chargeMethod = proxy.getClass().getMethod("charge", String.class);
            final Object result = chargeMethod.invoke(proxy, "ada");

            assertEquals("charged:ada:via-proxy", result);
            assertEquals(1, interceptions.get());
            assertTrue(AopUtils.isCglibProxy(proxy));
            assertEquals(targetClass, proxy.getClass().getSuperclass());
            assertFalse(Modifier.isFinal(targetClass.getModifiers()));
        }
    }
}
