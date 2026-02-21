package org.springframework.context.annotation;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal test-only context used to validate TSJ Spring-style bridge metadata
 * without external Spring dependencies.
 */
public final class AnnotationConfigApplicationContext implements AutoCloseable {
    private static final String AOP_CODE = "TSJ-SPRING-AOP";
    private static final String AOP_CLASS_PROXY_FEATURE_ID = "TSJ35A-CLASS-PROXY";
    private static final String COMPONENT_CODE = "TSJ-SPRING-COMPONENT";
    private static final String DI_FEATURE_ID = "TSJ33D-INJECTION-MODES";
    private static final String LIFECYCLE_CODE = "TSJ-SPRING-LIFECYCLE";
    private static final String LIFECYCLE_FEATURE_ID = "TSJ33E-LIFECYCLE";
    private static final String WEB_CONTROLLER_CODE = "TSJ-WEB-CONTROLLER";
    private static final String WEB_CONTROLLER_DI_FEATURE_ID = "TSJ34D-CONTROLLER-DI";

    private final List<Class<?>> registered = new ArrayList<>();
    private final Map<Class<?>, Object> beansByType = new LinkedHashMap<>();
    private final List<BeanRegistration> beans = new ArrayList<>();
    private final List<String> lifecycleEvents = new ArrayList<>();

    public void setClassLoader(final ClassLoader classLoader) {
        // Test-only compatibility hook; class loading is driven by registered classes.
    }

    public void register(final Class<?> configClass) {
        registered.add(configClass);
    }

    public List<String> lifecycleEvents() {
        return List.copyOf(lifecycleEvents);
    }

    public void refresh() {
        lifecycleEvents.clear();
        final List<ConfigurationInstance> configs = new ArrayList<>();
        final List<Class<?>> componentClasses = new ArrayList<>();
        for (Class<?> configClass : registered) {
            if (configClass.isAnnotationPresent(Configuration.class)) {
                final Object instance = instantiateNoArg(configClass);
                configs.add(new ConfigurationInstance(configClass, instance));
            } else {
                componentClasses.add(configClass);
            }
        }

        final List<BeanMethod> unresolvedBeans = new ArrayList<>();
        for (ConfigurationInstance config : configs) {
            for (Method method : config.type().getMethods()) {
                if (method.isAnnotationPresent(Bean.class)) {
                    unresolvedBeans.add(new BeanMethod(config.instance(), method));
                }
            }
        }

        while (!unresolvedBeans.isEmpty()) {
            boolean progressed = false;
            for (int index = 0; index < unresolvedBeans.size(); index++) {
                final BeanMethod beanMethod = unresolvedBeans.get(index);
                final Object[] args = resolveExecutableArguments(beanMethod.method(), null, false);
                if (args == null) {
                    continue;
                }
                final Object bean = invokeBeanMethod(beanMethod, args);
                registerBean(
                        beanMethod.method().getReturnType(),
                        bean,
                        beanMethod.method().getName(),
                        isPrimary(beanMethod.method()),
                        collectQualifiers(beanMethod.method().getAnnotation(Qualifier.class), beanMethod.method().getName())
                );
                invokePostConstruct(bean);
                unresolvedBeans.remove(index);
                index--;
                progressed = true;
            }
            if (!progressed) {
                final BeanMethod pending = unresolvedBeans.getFirst();
                resolveExecutableArguments(pending.method(), null, true);
                throw dependencyError("Unable to resolve bean dependencies for test context.");
            }
        }

        final List<Class<?>> unresolvedComponents = new ArrayList<>(componentClasses);
        while (!unresolvedComponents.isEmpty()) {
            boolean progressed = false;
            for (int index = 0; index < unresolvedComponents.size(); index++) {
                final Class<?> componentClass = unresolvedComponents.get(index);
                final Object component = tryInstantiateComponent(componentClass, false);
                if (component == null) {
                    continue;
                }
                registerBean(
                        componentClass,
                        component,
                        defaultBeanName(componentClass),
                        isPrimary(componentClass),
                        collectQualifiers(componentClass.getAnnotation(Qualifier.class), defaultBeanName(componentClass))
                );
                invokePostConstruct(component);
                unresolvedComponents.remove(index);
                index--;
                progressed = true;
            }
            if (!progressed) {
                final List<Class<?>> cycle = detectCircularDependency(unresolvedComponents);
                if (!cycle.isEmpty()) {
                    throw lifecycleError(
                            "refresh",
                            "Circular dependency detected: " + renderCyclePath(cycle) + "."
                    );
                }
                final Class<?> pending = unresolvedComponents.getFirst();
                tryInstantiateComponent(pending, true);
                throw dependencyError("Unable to resolve component dependencies for test context.");
            }
        }

        applyTransactionalProxies();
    }

    public <T> T getBean(final Class<T> type) {
        final Object direct = beansByType.get(type);
        if (direct != null) {
            return type.cast(direct);
        }
        for (Object bean : beansByType.values()) {
            if (type.isInstance(bean)) {
                return type.cast(bean);
            }
        }
        throw new IllegalStateException("Bean not found: " + type.getName());
    }

    @Override
    public void close() {
        final List<Object> uniqueBeans = new ArrayList<>(new LinkedHashSet<>(beansByType.values()));
        for (int index = uniqueBeans.size() - 1; index >= 0; index--) {
            invokePreDestroy(uniqueBeans.get(index));
        }
        registered.clear();
        beansByType.clear();
        beans.clear();
    }

    private void applyTransactionalProxies() {
        final PlatformTransactionManager transactionManager = resolveTransactionManager();
        final Set<Object> seenTargets = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object bean : List.copyOf(beansByType.values())) {
            if (bean == null || !seenTargets.add(bean)) {
                continue;
            }
            final Class<?> beanType = bean.getClass();
            if (!isTransactionalBean(beanType)) {
                continue;
            }
            final ProxyStrategy proxyStrategy = resolveProxyStrategy(bean);
            if (proxyStrategy == ProxyStrategy.CLASS) {
                validateClassProxyTarget(beanType);
                if (transactionManager == null) {
                    throw classProxyError(
                            "Class-proxy transactional bean requires a PlatformTransactionManager: "
                                    + beanType.getName()
                    );
                }
                continue;
            }
            if (transactionManager == null) {
                throw new IllegalStateException(
                        AOP_CODE + ": @Transactional bean requires a PlatformTransactionManager for TSJ-35 subset."
                );
            }
            final Class<?>[] interfaces = beanType.getInterfaces();
            if (interfaces.length == 0) {
                throw new IllegalStateException(
                        AOP_CODE + ": @Transactional bean must expose at least one interface for TSJ-35 proxy subset: "
                                + beanType.getName()
                );
            }
            final Object proxy = createTransactionalProxy(bean, interfaces, transactionManager);
            for (Class<?> interfaceType : interfaces) {
                beansByType.put(interfaceType, proxy);
                beans.add(new BeanRegistration(interfaceType, proxy, interfaceType.getName(), false, Set.of()));
            }
        }
    }

    private static ProxyStrategy resolveProxyStrategy(final Object bean) {
        final Method strategyMethod;
        try {
            strategyMethod = bean.getClass().getMethod("__tsjProxyStrategy");
        } catch (final NoSuchMethodException noSuchMethodException) {
            return ProxyStrategy.JDK;
        }
        if (strategyMethod.getParameterCount() != 0 || !String.class.equals(strategyMethod.getReturnType())) {
            throw classProxyError(
                    "Invalid __tsjProxyStrategy signature on `" + bean.getClass().getName() + "`."
            );
        }
        final Object rawValue;
        try {
            rawValue = strategyMethod.invoke(bean);
        } catch (final IllegalAccessException | InvocationTargetException reflectionException) {
            throw classProxyError(
                    "Failed to evaluate __tsjProxyStrategy on `" + bean.getClass().getName() + "`: "
                            + reflectionException.getMessage()
            );
        }
        if (rawValue == null) {
            throw classProxyError(
                    "Invalid __tsjProxyStrategy value `null` on `" + bean.getClass().getName() + "`."
            );
        }
        final String normalized = String.valueOf(rawValue).trim().toLowerCase(java.util.Locale.ROOT);
        if ("class".equals(normalized)) {
            return ProxyStrategy.CLASS;
        }
        if ("jdk".equals(normalized)) {
            return ProxyStrategy.JDK;
        }
        throw classProxyError(
                "Unsupported __tsjProxyStrategy value `" + rawValue + "` on `" + bean.getClass().getName() + "`."
        );
    }

    private static void validateClassProxyTarget(final Class<?> beanType) {
        if (Modifier.isFinal(beanType.getModifiers())) {
            throw classProxyError(
                    "Class-proxy strategy does not support final transactional class: " + beanType.getName()
            );
        }
        for (Method method : beanType.getDeclaredMethods()) {
            if (!isClassProxyInterceptable(method) || !isTransactionalInvocation(beanType, method)) {
                continue;
            }
            if (Modifier.isFinal(method.getModifiers())) {
                throw classProxyError(
                        "Class-proxy strategy does not support final transactional method `"
                                + beanType.getName() + "." + method.getName() + "`."
                );
            }
        }
    }

    private static boolean isClassProxyInterceptable(final Method method) {
        final int modifiers = method.getModifiers();
        return !method.isSynthetic()
                && !method.isBridge()
                && method.getDeclaringClass() != Object.class
                && !Modifier.isStatic(modifiers)
                && !Modifier.isPrivate(modifiers);
    }

    private static Object createTransactionalProxy(
            final Object target,
            final Class<?>[] interfaces,
            final PlatformTransactionManager transactionManager
    ) {
        final InvocationHandler invocationHandler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            final Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            if (!isTransactionalInvocation(target.getClass(), targetMethod)) {
                return invokeTarget(target, targetMethod, args);
            }
            final TransactionStatus status = transactionManager.begin(
                    target.getClass().getName(),
                    targetMethod.getName()
            );
            try {
                final Object result = invokeTarget(target, targetMethod, args);
                transactionManager.commit(status);
                return result;
            } catch (final InvocationTargetException invocationTargetException) {
                transactionManager.rollback(status);
                throw invocationTargetException.getTargetException();
            } catch (final Throwable throwable) {
                transactionManager.rollback(status);
                throw throwable;
            }
        };
        return Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                interfaces,
                invocationHandler
        );
    }

    private static Object invokeObjectMethod(final Object proxy, final Method method, final Object[] args) {
        if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
            return "TSJ Spring proxy(" + proxy.getClass().getName() + ")";
        }
        if ("hashCode".equals(method.getName()) && method.getParameterCount() == 0) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
            return proxy == (args == null ? null : args[0]);
        }
        throw new IllegalStateException("Unsupported Object method dispatch on proxy: " + method.getName());
    }

    private static boolean isTransactionalInvocation(final Class<?> beanType, final Method method) {
        return beanType.isAnnotationPresent(Transactional.class)
                || method.isAnnotationPresent(Transactional.class);
    }

    private static boolean isTransactionalBean(final Class<?> beanType) {
        if (beanType.isAnnotationPresent(Transactional.class)) {
            return true;
        }
        for (Method method : beanType.getMethods()) {
            if (method.isAnnotationPresent(Transactional.class)) {
                return true;
            }
        }
        return false;
    }

    private PlatformTransactionManager resolveTransactionManager() {
        final Object candidate = findDependency(
                PlatformTransactionManager.class,
                null,
                null,
                false,
                "PlatformTransactionManager",
                null
        );
        if (candidate == null) {
            return null;
        }
        return (PlatformTransactionManager) candidate;
    }

    private static Object invokeTarget(final Object target, final Method method, final Object[] args)
            throws InvocationTargetException {
        try {
            return method.invoke(target, args);
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalStateException(
                    "Failed to invoke proxy target method: " + method.getName(),
                    illegalAccessException
            );
        }
    }

    private static Object instantiateNoArg(final Class<?> configClass) {
        try {
            return configClass.getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException(
                    "Failed to instantiate configuration class: " + configClass.getName(),
                    reflectiveOperationException
            );
        }
    }

    private Object[] resolveExecutableArguments(
            final Executable executable,
            final Object owner,
            final boolean strictMissing
    ) {
        final Parameter[] parameters = executable.getParameters();
        final Object[] args = new Object[parameters.length];
        for (int index = 0; index < parameters.length; index++) {
            final Parameter parameter = parameters[index];
            final Qualifier qualifier = parameter.getAnnotation(Qualifier.class);
            final String qualifierValue = qualifier == null ? null : normalizeQualifierValue(qualifier.value());
            final String injectionPoint = executable.getDeclaringClass().getSimpleName()
                    + "."
                    + executable.getName()
                    + " arg"
                    + index;
            final Object dependency = findDependency(
                    parameter.getType(),
                    qualifierValue,
                    owner,
                    strictMissing,
                    injectionPoint,
                    executable.getDeclaringClass()
            );
            if (dependency == null && !strictMissing) {
                return null;
            }
            args[index] = dependency;
        }
        return args;
    }

    private Object tryInstantiateComponent(final Class<?> componentClass, final boolean strictMissing) {
        final Constructor<?>[] constructors = componentClass.getDeclaredConstructors();
        final List<Constructor<?>> sorted = new ArrayList<>(List.of(constructors));
        sorted.sort(Comparator.comparingInt((Constructor<?> constructor) ->
                constructor.getParameterTypes().length).reversed());

        for (Constructor<?> constructor : sorted) {
            final Object[] args = resolveExecutableArguments(constructor, null, strictMissing);
            if (args == null) {
                continue;
            }
            final Object component;
            try {
                component = constructor.newInstance(args);
            } catch (final ReflectiveOperationException reflectiveOperationException) {
                throw new IllegalStateException(
                        "Failed to instantiate component class: " + componentClass.getName(),
                        reflectiveOperationException
                );
            }
            if (!injectAutowiredMembers(component, strictMissing)) {
                if (strictMissing) {
                    throw dependencyError("Missing bean while injecting component `" + componentClass.getName() + "`.");
                }
                continue;
            }
            return component;
        }
        return null;
    }

    private boolean injectAutowiredMembers(final Object component, final boolean strictMissing) {
        final Class<?> type = component.getClass();
        for (Field field : type.getDeclaredFields()) {
            final Autowired autowired = field.getAnnotation(Autowired.class);
            if (autowired == null) {
                continue;
            }
            final boolean required = autowired.required();
            final Qualifier qualifier = field.getAnnotation(Qualifier.class);
            final String qualifierValue = qualifier == null ? null : normalizeQualifierValue(qualifier.value());
            final Object dependency = findDependency(
                    field.getType(),
                    qualifierValue,
                    component,
                    strictMissing && required,
                    type.getSimpleName() + "." + field.getName(),
                    type
            );
            if (dependency == null) {
                if (!required) {
                    continue;
                }
                if (!strictMissing) {
                    return false;
                }
            }
            try {
                field.setAccessible(true);
                field.set(component, dependency);
            } catch (final IllegalAccessException illegalAccessException) {
                throw dependencyError("Failed to inject @Autowired field `"
                        + type.getSimpleName() + "." + field.getName() + "`: " + illegalAccessException.getMessage());
            }
        }

        for (Method method : type.getMethods()) {
            final Autowired autowired = method.getAnnotation(Autowired.class);
            if (autowired == null) {
                continue;
            }
            final boolean required = autowired.required();
            if (method.getParameterCount() != 1) {
                throw dependencyError("@Autowired setter `"
                        + type.getSimpleName() + "." + method.getName()
                        + "` must declare exactly one parameter.");
            }
            final Parameter parameter = method.getParameters()[0];
            final Qualifier parameterQualifier = parameter.getAnnotation(Qualifier.class);
            final Qualifier methodQualifier = method.getAnnotation(Qualifier.class);
            final String qualifierValue;
            if (parameterQualifier != null) {
                qualifierValue = normalizeQualifierValue(parameterQualifier.value());
            } else if (methodQualifier != null) {
                qualifierValue = normalizeQualifierValue(methodQualifier.value());
            } else {
                qualifierValue = null;
            }
            final Object dependency = findDependency(
                    parameter.getType(),
                    qualifierValue,
                    component,
                    strictMissing && required,
                    type.getSimpleName() + "." + method.getName(),
                    type
            );
            if (dependency == null) {
                if (!required) {
                    continue;
                }
                if (!strictMissing) {
                    return false;
                }
            }
            try {
                method.invoke(component, dependency);
            } catch (final IllegalAccessException | InvocationTargetException reflectionException) {
                throw dependencyError("Failed to invoke @Autowired setter `"
                        + type.getSimpleName() + "." + method.getName() + "`: " + reflectionException.getMessage());
            }
        }

        return true;
    }

    private Object findDependency(
            final Class<?> requestedType,
            final String qualifier,
            final Object owner,
            final boolean strictMissing,
            final String injectionPoint,
            final Class<?> injectionOwnerType
    ) {
        final List<BeanRegistration> candidates = new ArrayList<>();
        for (BeanRegistration registration : beans) {
            if (owner != null && registration.instance() == owner) {
                continue;
            }
            if (!requestedType.isAssignableFrom(registration.exposedType()) && !requestedType.isInstance(registration.instance())) {
                continue;
            }
            if (qualifier != null && !registration.qualifiers().contains(qualifier)) {
                continue;
            }
            candidates.add(registration);
        }

        if (candidates.isEmpty()) {
            if (!strictMissing) {
                return null;
            }
            final String qualifierPart = qualifier == null ? "" : " (qualifier=`" + qualifier + "`)";
            throw dependencyError(
                    injectionOwnerType,
                    "Missing bean for dependency `"
                            + requestedType.getName()
                            + "`"
                            + qualifierPart
                            + " at "
                            + injectionPoint
                            + "."
            );
        }
        if (candidates.size() == 1) {
            return candidates.getFirst().instance();
        }

        final List<BeanRegistration> primaries = new ArrayList<>();
        for (BeanRegistration candidate : candidates) {
            if (candidate.primary()) {
                primaries.add(candidate);
            }
        }
        if (primaries.size() == 1) {
            return primaries.getFirst().instance();
        }
        if (primaries.size() > 1) {
            throw dependencyError(
                    injectionOwnerType,
                    "Primary conflict for dependency `"
                            + requestedType.getName()
                            + "` at "
                            + injectionPoint
                            + ": multiple @Primary beans matched"
                            + (qualifier == null ? "" : " qualifier `" + qualifier + "`")
                            + "."
            );
        }

        throw dependencyError(
                injectionOwnerType,
                "Ambiguous dependency for `"
                        + requestedType.getName()
                        + "` at "
                        + injectionPoint
                        + (qualifier == null ? "" : " with qualifier `" + qualifier + "`")
                        + ": "
                        + candidates.size()
                        + " beans matched."
        );
    }

    private void registerBean(
            final Class<?> exposedType,
            final Object instance,
            final String beanName,
            final boolean primary,
            final Set<String> qualifiers
    ) {
        beansByType.put(exposedType, instance);
        beans.add(new BeanRegistration(exposedType, instance, beanName, primary, qualifiers));
    }

    private List<Class<?>> detectCircularDependency(final List<Class<?>> unresolvedComponents) {
        final Map<Class<?>, List<Class<?>>> graph = new LinkedHashMap<>();
        final Map<Class<?>, Set<String>> qualifiersByType = new LinkedHashMap<>();
        for (Class<?> componentClass : unresolvedComponents) {
            qualifiersByType.put(
                    componentClass,
                    collectQualifiers(
                            componentClass.getAnnotation(Qualifier.class),
                            defaultBeanName(componentClass)
                    )
            );
            graph.put(componentClass, new ArrayList<>());
        }

        for (Class<?> componentClass : unresolvedComponents) {
            final List<DependencyRequest> dependencyRequests = collectDependencyRequests(componentClass);
            final List<Class<?>> edges = graph.get(componentClass);
            for (DependencyRequest request : dependencyRequests) {
                final List<Class<?>> matches = new ArrayList<>();
                for (Class<?> candidate : unresolvedComponents) {
                    if (candidate == componentClass) {
                        continue;
                    }
                    if (!request.requestedType().isAssignableFrom(candidate)) {
                        continue;
                    }
                    if (request.qualifier() != null
                            && !qualifiersByType.getOrDefault(candidate, Set.of()).contains(request.qualifier())) {
                        continue;
                    }
                    matches.add(candidate);
                }
                if (matches.size() == 1 || request.qualifier() != null) {
                    edges.addAll(matches);
                }
            }
        }

        final Set<Class<?>> visiting = new LinkedHashSet<>();
        final Set<Class<?>> visited = new LinkedHashSet<>();
        final List<Class<?>> stack = new ArrayList<>();
        for (Class<?> componentClass : unresolvedComponents) {
            final List<Class<?>> cycle = dfsCycle(componentClass, graph, visiting, visited, stack);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<DependencyRequest> collectDependencyRequests(final Class<?> componentClass) {
        final List<DependencyRequest> requests = new ArrayList<>();
        for (Constructor<?> constructor : componentClass.getDeclaredConstructors()) {
            final Parameter[] parameters = constructor.getParameters();
            for (Parameter parameter : parameters) {
                final Qualifier qualifier = parameter.getAnnotation(Qualifier.class);
                final String qualifierValue = qualifier == null ? null : normalizeQualifierValue(qualifier.value());
                requests.add(new DependencyRequest(parameter.getType(), qualifierValue));
            }
        }
        for (Field field : componentClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Autowired.class)) {
                continue;
            }
            final Qualifier qualifier = field.getAnnotation(Qualifier.class);
            final String qualifierValue = qualifier == null ? null : normalizeQualifierValue(qualifier.value());
            requests.add(new DependencyRequest(field.getType(), qualifierValue));
        }
        for (Method method : componentClass.getMethods()) {
            if (!method.isAnnotationPresent(Autowired.class) || method.getParameterCount() != 1) {
                continue;
            }
            final Parameter parameter = method.getParameters()[0];
            final Qualifier parameterQualifier = parameter.getAnnotation(Qualifier.class);
            final Qualifier methodQualifier = method.getAnnotation(Qualifier.class);
            final String qualifierValue;
            if (parameterQualifier != null) {
                qualifierValue = normalizeQualifierValue(parameterQualifier.value());
            } else if (methodQualifier != null) {
                qualifierValue = normalizeQualifierValue(methodQualifier.value());
            } else {
                qualifierValue = null;
            }
            requests.add(new DependencyRequest(parameter.getType(), qualifierValue));
        }
        return List.copyOf(requests);
    }

    private static List<Class<?>> dfsCycle(
            final Class<?> current,
            final Map<Class<?>, List<Class<?>>> graph,
            final Set<Class<?>> visiting,
            final Set<Class<?>> visited,
            final List<Class<?>> stack
    ) {
        if (visited.contains(current)) {
            return List.of();
        }
        visiting.add(current);
        stack.add(current);
        for (Class<?> next : graph.getOrDefault(current, List.of())) {
            if (visiting.contains(next)) {
                final int cycleStart = stack.indexOf(next);
                if (cycleStart >= 0) {
                    final List<Class<?>> cycle = new ArrayList<>(stack.subList(cycleStart, stack.size()));
                    cycle.add(next);
                    return List.copyOf(cycle);
                }
            } else {
                final List<Class<?>> cycle = dfsCycle(next, graph, visiting, visited, stack);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        stack.remove(stack.size() - 1);
        visiting.remove(current);
        visited.add(current);
        return List.of();
    }

    private static String renderCyclePath(final List<Class<?>> cycle) {
        final List<String> names = new ArrayList<>(cycle.size());
        for (Class<?> type : cycle) {
            names.add(type.getSimpleName());
        }
        return String.join(" -> ", names);
    }

    private static Set<String> collectQualifiers(final Qualifier qualifierAnnotation, final String beanName) {
        final Set<String> qualifiers = new LinkedHashSet<>();
        qualifiers.add(beanName);
        if (qualifierAnnotation != null) {
            final String qualifierValue = normalizeQualifierValue(qualifierAnnotation.value());
            if (qualifierValue != null) {
                qualifiers.add(qualifierValue);
            }
        }
        return Set.copyOf(qualifiers);
    }

    private static boolean isPrimary(final Class<?> type) {
        return type.isAnnotationPresent(Primary.class);
    }

    private static boolean isPrimary(final Method method) {
        return method.isAnnotationPresent(Primary.class);
    }

    private static String defaultBeanName(final Class<?> type) {
        final String simple = type.getSimpleName();
        if (simple.isEmpty()) {
            return type.getName();
        }
        if (simple.length() == 1) {
            return simple.toLowerCase(java.util.Locale.ROOT);
        }
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    private static String normalizeQualifierValue(final String raw) {
        if (raw == null) {
            return null;
        }
        final String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static IllegalStateException lifecycleError(final String phase, final String message) {
        return new IllegalStateException(
                LIFECYCLE_CODE + " [featureId=" + LIFECYCLE_FEATURE_ID + "] "
                        + "Lifecycle phase=" + phase + " failed: " + message
        );
    }

    private static IllegalStateException lifecycleError(
            final String phase,
            final Object bean,
            final Method method,
            final Throwable cause
    ) {
        final String callbackKind = "refresh".equals(phase) ? "post-construct" : "pre-destroy";
        final String detail = LIFECYCLE_CODE + " [featureId=" + LIFECYCLE_FEATURE_ID + "] "
                + "Lifecycle phase=" + phase + " failed: "
                + callbackKind + " callback `" + bean.getClass().getName() + "#" + method.getName() + "` failed.";
        return new IllegalStateException(detail, cause);
    }

    private static IllegalStateException dependencyError(final String message) {
        return new IllegalStateException(COMPONENT_CODE + " [featureId=" + DI_FEATURE_ID + "] " + message);
    }

    private static IllegalStateException dependencyError(final Class<?> injectionOwnerType, final String message) {
        if (injectionOwnerType != null && injectionOwnerType.isAnnotationPresent(RestController.class)) {
            return new IllegalStateException(
                    WEB_CONTROLLER_CODE
                            + " [featureId="
                            + WEB_CONTROLLER_DI_FEATURE_ID
                            + "] Controller wiring failure: "
                            + message
            );
        }
        return dependencyError(message);
    }

    private static IllegalStateException classProxyError(final String message) {
        return new IllegalStateException(
                AOP_CODE + " [featureId=" + AOP_CLASS_PROXY_FEATURE_ID + "] " + message
        );
    }

    private static Object invokeBeanMethod(final BeanMethod beanMethod, final Object[] args) {
        try {
            return beanMethod.method().invoke(beanMethod.configInstance(), args);
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalStateException(
                    "Failed to access bean method: " + beanMethod.method().getName(),
                    illegalAccessException
            );
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            throw new IllegalStateException(
                    "Bean method invocation failed: " + beanMethod.method().getName(),
                    target
            );
        }
    }

    private void invokePostConstruct(final Object bean) {
        if (bean == null) {
            return;
        }
        for (Method method : bean.getClass().getMethods()) {
            if (!method.isAnnotationPresent(PostConstruct.class)) {
                continue;
            }
            lifecycleEvents.add("refresh:" + bean.getClass().getSimpleName() + "#" + method.getName());
            if (method.getParameterCount() != 0) {
                throw lifecycleError(
                        "refresh",
                        "Invalid post-construct callback signature `" + bean.getClass().getName()
                                + "#" + method.getName() + "`."
                );
            }
            try {
                method.invoke(bean);
            } catch (final IllegalAccessException | InvocationTargetException reflectionException) {
                throw lifecycleError("refresh", bean, method, reflectionException);
            }
        }
    }

    private void invokePreDestroy(final Object bean) {
        if (bean == null) {
            return;
        }
        for (Method method : bean.getClass().getMethods()) {
            if (!method.isAnnotationPresent(PreDestroy.class)) {
                continue;
            }
            lifecycleEvents.add("close:" + bean.getClass().getSimpleName() + "#" + method.getName());
            if (method.getParameterCount() != 0) {
                throw lifecycleError(
                        "close",
                        "Invalid pre-destroy callback signature `" + bean.getClass().getName()
                                + "#" + method.getName() + "`."
                );
            }
            try {
                method.invoke(bean);
            } catch (final IllegalAccessException | InvocationTargetException reflectionException) {
                throw lifecycleError("close", bean, method, reflectionException);
            }
        }
    }

    private record ConfigurationInstance(Class<?> type, Object instance) {
    }

    private record BeanMethod(Object configInstance, Method method) {
    }

    private record BeanRegistration(
            Class<?> exposedType,
            Object instance,
            String beanName,
            boolean primary,
            Set<String> qualifiers
    ) {
    }

    private record DependencyRequest(Class<?> requestedType, String qualifier) {
    }

    private enum ProxyStrategy {
        JDK,
        CLASS
    }
}
