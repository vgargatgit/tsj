package dev.tsj.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime reflective entry point used by generated TSJ interop bridges.
 */
public final class TsjJavaInterop {
    private TsjJavaInterop() {
    }

    public static Object invokeStatic(final String className, final String methodName, final Object... tsArgs) {
        final Class<?> targetClass;
        try {
            targetClass = Class.forName(className);
        } catch (final ClassNotFoundException classNotFoundException) {
            throw new IllegalArgumentException("Java interop class not found: " + className, classNotFoundException);
        }

        final List<Method> candidates = new ArrayList<>();
        for (Method method : targetClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!methodName.equals(method.getName())) {
                continue;
            }
            if (method.getParameterCount() != tsArgs.length) {
                continue;
            }
            candidates.add(method);
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No compatible static method `" + methodName + "` on " + className + " for arity " + tsArgs.length + "."
            );
        }

        for (Method method : candidates) {
            final Object[] javaArgs;
            try {
                javaArgs = TsjInteropCodec.toJavaArguments(tsArgs, method.getParameterTypes());
            } catch (final IllegalArgumentException ignored) {
                continue;
            }
            try {
                final Object result = method.invoke(null, javaArgs);
                return TsjInteropCodec.fromJava(result);
            } catch (final IllegalAccessException illegalAccessException) {
                throw new IllegalArgumentException(
                        "Java interop method not accessible: " + className + "#" + methodName,
                        illegalAccessException
                );
            } catch (final InvocationTargetException invocationTargetException) {
                final Throwable target = invocationTargetException.getTargetException();
                throw new IllegalArgumentException(
                        "Java interop invocation failed for " + className + "#" + methodName + ": " + target.getMessage(),
                        target
                );
            }
        }
        throw new IllegalArgumentException(
                "No compatible static method `" + methodName + "` on " + className + " for provided argument types."
        );
    }
}
