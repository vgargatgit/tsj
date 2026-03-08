package dev.rita.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal reflection-based field-injection container.
 */
public final class MiniContainer {
    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();

    private MiniContainer() {
    }

    public static <T> T createRoot(final Class<T> rootType) {
        return new MiniContainer().create(rootType);
    }

    private <T> T create(final Class<T> type) {
        if (!type.isAnnotationPresent(Component.class)) {
            throw new IllegalArgumentException("Type is not a @Component: " + type.getName());
        }

        final Object existing = instances.get(type);
        if (existing != null) {
            return type.cast(existing);
        }

        final T instance = instantiate(type);
        instances.put(type, instance);
        injectFields(instance, type);
        return instance;
    }

    private static <T> T instantiate(final Class<T> type) {
        try {
            final Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (final Exception exception) {
            throw new IllegalStateException("Failed to instantiate component " + type.getName(), exception);
        }
    }

    private void injectFields(final Object instance, final Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                final Object dependency = create(field.getType());
                try {
                    field.setAccessible(true);
                    field.set(instance, dependency);
                } catch (final IllegalAccessException exception) {
                    throw new IllegalStateException(
                            "Failed to inject field " + current.getName() + "#" + field.getName(),
                            exception
                    );
                }
            }
            current = current.getSuperclass();
        }
    }
}
