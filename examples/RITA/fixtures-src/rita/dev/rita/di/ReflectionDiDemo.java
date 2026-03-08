package dev.rita.di;

import java.lang.reflect.Field;

/**
 * TSJ interop entrypoints for reflection DI checks.
 */
public final class ReflectionDiDemo {
    private ReflectionDiDemo() {
    }

    public static String bootstrapGreeting(final String name) {
        final GreetingController controller = MiniContainer.createRoot(GreetingController.class);
        return controller.handle(name);
    }

    public static Object createJavaGreetingController() {
        return MiniContainer.createRoot(GreetingController.class);
    }

    public static boolean isComponentAnnotationPresent(final Object value) {
        if (value == null) {
            return false;
        }
        return value.getClass().isAnnotationPresent(Component.class);
    }

    public static int countInjectAnnotatedFields(final Object value) {
        if (value == null) {
            return 0;
        }
        int count = 0;
        Class<?> current = value.getClass();
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

    public static String runtimeClassName(final Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
