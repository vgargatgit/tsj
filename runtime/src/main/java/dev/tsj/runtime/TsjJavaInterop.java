package dev.tsj.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime reflective entry point used by generated TSJ interop bridges.
 */
public final class TsjJavaInterop {
    private static final String BINDING_CONSTRUCTOR = "$new";
    private static final String BINDING_INSTANCE_PREFIX = "$instance$";
    private static final String BINDING_INSTANCE_GET_PREFIX = "$instance$get$";
    private static final String BINDING_INSTANCE_SET_PREFIX = "$instance$set$";
    private static final String BINDING_STATIC_GET_PREFIX = "$static$get$";
    private static final String BINDING_STATIC_SET_PREFIX = "$static$set$";
    private static final String INVOKE_KIND_CONSTRUCTOR = "CONSTRUCTOR";
    private static final String INVOKE_KIND_STATIC_METHOD = "STATIC_METHOD";
    private static final String INVOKE_KIND_INSTANCE_METHOD = "INSTANCE_METHOD";
    private static final String INVOKE_KIND_STATIC_FIELD_GET = "STATIC_FIELD_GET";
    private static final String INVOKE_KIND_STATIC_FIELD_SET = "STATIC_FIELD_SET";
    private static final String INVOKE_KIND_INSTANCE_FIELD_GET = "INSTANCE_FIELD_GET";
    private static final String INVOKE_KIND_INSTANCE_FIELD_SET = "INSTANCE_FIELD_SET";
    private static final int CONVERSION_IMPOSSIBLE = Integer.MAX_VALUE / 4;
    private static final Object[] EMPTY_TS_ARGS = new Object[0];
    private static volatile boolean TRACE_ENABLED = Boolean.getBoolean("tsj.interop.trace");

    private TsjJavaInterop() {
    }

    public static void setTraceEnabled(final boolean enabled) {
        TRACE_ENABLED = enabled;
    }

    public static boolean traceEnabled() {
        return TRACE_ENABLED;
    }

    public static Object invokeBinding(final String className, final String bindingName, final Object... tsArgs) {
        final Object[] normalizedArgs = normalizeTsArgs(tsArgs);
        traceInvocation(className, bindingName, normalizedArgs);
        try {
            final Class<?> targetClass = resolveClass(className);
            final Object result;
            if (BINDING_CONSTRUCTOR.equals(bindingName)) {
                result = invokeConstructor(targetClass, normalizedArgs);
            } else if (bindingName.startsWith(BINDING_INSTANCE_GET_PREFIX)) {
                final String fieldName = requireBindingSuffix(bindingName, BINDING_INSTANCE_GET_PREFIX);
                result = readInstanceField(targetClass, fieldName, normalizedArgs);
            } else if (bindingName.startsWith(BINDING_INSTANCE_SET_PREFIX)) {
                final String fieldName = requireBindingSuffix(bindingName, BINDING_INSTANCE_SET_PREFIX);
                result = writeInstanceField(targetClass, fieldName, normalizedArgs);
            } else if (bindingName.startsWith(BINDING_STATIC_GET_PREFIX)) {
                final String fieldName = requireBindingSuffix(bindingName, BINDING_STATIC_GET_PREFIX);
                result = readStaticField(targetClass, fieldName, normalizedArgs);
            } else if (bindingName.startsWith(BINDING_STATIC_SET_PREFIX)) {
                final String fieldName = requireBindingSuffix(bindingName, BINDING_STATIC_SET_PREFIX);
                result = writeStaticField(targetClass, fieldName, normalizedArgs);
            } else if (bindingName.startsWith(BINDING_INSTANCE_PREFIX)) {
                final String methodName = requireBindingSuffix(bindingName, BINDING_INSTANCE_PREFIX);
                result = invokeInstance(targetClass, methodName, normalizedArgs);
            } else if (bindingName.startsWith("$")) {
                throw new IllegalArgumentException(
                        "Unsupported interop binding `" + bindingName + "` on " + className + "."
                );
            } else {
                result = invokeStatic(targetClass, className, bindingName, normalizedArgs);
            }
            traceSuccess(className, bindingName, result);
            return result;
        } catch (final RuntimeException runtimeException) {
            traceFailure(className, bindingName, runtimeException);
            throw runtimeException;
        }
    }

    public static Object invokeStatic(final String className, final String methodName, final Object... tsArgs) {
        final Class<?> targetClass = resolveClass(className);
        return invokeStatic(targetClass, className, methodName, normalizeTsArgs(tsArgs));
    }

    public static boolean hasPublicStaticField(final String className, final String fieldName) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(fieldName, "fieldName");
        final Class<?> targetClass = resolveClass(className);
        try {
            final Field field = targetClass.getField(fieldName);
            return Modifier.isStatic(field.getModifiers());
        } catch (NoSuchFieldException noSuchFieldException) {
            return false;
        }
    }

    public static Object readPublicStaticField(final String className, final String fieldName) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(fieldName, "fieldName");
        final Class<?> targetClass = resolveClass(className);
        return readStaticField(targetClass, fieldName, EMPTY_TS_ARGS);
    }

    public static Object invokeBindingPreselected(
            final String className,
            final String bindingName,
            final String owner,
            final String memberName,
            final String descriptor,
            final String invokeKind,
            final Object... tsArgs
    ) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(memberName, "memberName");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(invokeKind, "invokeKind");

        final String traceBinding = bindingName + "#preselected";
        final Object[] normalizedArgs = normalizeTsArgs(tsArgs);
        traceInvocation(className, traceBinding, normalizedArgs);
        try {
            final Class<?> ownerClass = resolveClass(normalizeOwnerName(owner));
            final Object result = switch (invokeKind) {
                case INVOKE_KIND_CONSTRUCTOR -> invokePreselectedConstructor(ownerClass, descriptor, normalizedArgs);
                case INVOKE_KIND_STATIC_METHOD -> invokePreselectedMethod(
                        ownerClass,
                        memberName,
                        descriptor,
                        true,
                        normalizedArgs
                );
                case INVOKE_KIND_INSTANCE_METHOD -> invokePreselectedMethod(
                        ownerClass,
                        memberName,
                        descriptor,
                        false,
                        normalizedArgs
                );
                case INVOKE_KIND_STATIC_FIELD_GET -> invokePreselectedFieldGet(
                        ownerClass,
                        memberName,
                        descriptor,
                        true,
                        normalizedArgs
                );
                case INVOKE_KIND_STATIC_FIELD_SET -> invokePreselectedFieldSet(
                        ownerClass,
                        memberName,
                        descriptor,
                        true,
                        normalizedArgs
                );
                case INVOKE_KIND_INSTANCE_FIELD_GET -> invokePreselectedFieldGet(
                        ownerClass,
                        memberName,
                        descriptor,
                        false,
                        normalizedArgs
                );
                case INVOKE_KIND_INSTANCE_FIELD_SET -> invokePreselectedFieldSet(
                        ownerClass,
                        memberName,
                        descriptor,
                        false,
                        normalizedArgs
                );
                default -> throw new IllegalArgumentException(
                        "Unsupported preselected invokeKind `" + invokeKind + "` for " + className + "#" + bindingName
                );
            };
            traceSuccess(className, traceBinding, result);
            return result;
        } catch (final RuntimeException runtimeException) {
            traceFailure(className, traceBinding, runtimeException);
            throw runtimeException;
        }
    }

    public static Object invokeInstanceMember(final Object receiver, final String methodName, final Object... tsArgs) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodName, "methodName");

        final Class<?> receiverClass = receiver.getClass();
        final String bindingName = BINDING_INSTANCE_PREFIX + methodName;
        final Object[] normalizedArgs = normalizeTsArgs(tsArgs);
        traceInvocation(receiverClass.getName(), bindingName, normalizedArgs);
        final Object[] invocationArgs = new Object[normalizedArgs.length + 1];
        invocationArgs[0] = receiver;
        System.arraycopy(normalizedArgs, 0, invocationArgs, 1, normalizedArgs.length);
        try {
            final Object result = invokeInstance(receiverClass, methodName, invocationArgs);
            traceSuccess(receiverClass.getName(), bindingName, result);
            return result;
        } catch (final RuntimeException runtimeException) {
            traceFailure(receiverClass.getName(), bindingName, runtimeException);
            throw runtimeException;
        }
    }

    private static Object invokePreselectedConstructor(
            final Class<?> ownerClass,
            final String descriptor,
            final Object[] tsArgs
    ) {
        final Constructor<?> constructor = findPreselectedConstructor(ownerClass, descriptor);
        final ConversionAttempt conversion = convertArguments(
                tsArgs,
                constructor.getParameterTypes(),
                constructor.getGenericParameterTypes(),
                constructor.isVarArgs()
        );
        if (!conversion.success()) {
            throw new IllegalArgumentException(
                    "TSJ-INTEROP-SELECTED preselected target argument conversion failed for "
                            + ownerClass.getName()
                            + "#<init>"
                            + descriptor
                            + ": "
                            + conversion.failureReason()
            );
        }
        try {
            return TsjInteropCodec.fromJava(constructor.newInstance(conversion.arguments()));
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            throw new IllegalArgumentException(
                    "Java interop constructor invocation failed for " + ownerClass.getName() + ": "
                            + target.getMessage(),
                    target
            );
        } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalArgumentException(
                    "Java interop constructor not accessible for " + ownerClass.getName(),
                    reflectiveOperationException
            );
        }
    }

    private static Object invokePreselectedMethod(
            final Class<?> ownerClass,
            final String methodName,
            final String descriptor,
            final boolean expectStatic,
            final Object[] tsArgs
    ) {
        final Method method = findPreselectedMethod(ownerClass, methodName, descriptor, expectStatic);
        final Object receiver;
        final Object[] methodArgs;
        if (expectStatic) {
            receiver = null;
            methodArgs = tsArgs;
        } else {
            if (tsArgs.length == 0) {
                throw new IllegalArgumentException(
                        "Instance interop method `" + methodName + "` requires receiver argument."
                );
            }
            receiver = tsArgs[0];
            ensureInstanceReceiver(ownerClass, methodName, receiver);
            methodArgs = Arrays.copyOfRange(tsArgs, 1, tsArgs.length);
        }
        final ConversionAttempt conversion = convertArguments(
                methodArgs,
                method.getParameterTypes(),
                method.getGenericParameterTypes(),
                method.isVarArgs()
        );
        if (!conversion.success()) {
            throw new IllegalArgumentException(
                    "TSJ-INTEROP-SELECTED preselected target argument conversion failed for "
                            + ownerClass.getName()
                            + "#"
                            + methodName
                            + descriptor
                            + ": "
                            + conversion.failureReason()
            );
        }
        try {
            final Object result = method.invoke(receiver, conversion.arguments());
            return TsjInteropCodec.fromJava(result);
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop method not accessible: " + ownerClass.getName() + "#" + methodName,
                    illegalAccessException
            );
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            throw new IllegalArgumentException(
                    "Java interop invocation failed for " + ownerClass.getName() + "#" + methodName + ": "
                            + target.getMessage(),
                    target
            );
        }
    }

    private static Object invokePreselectedFieldGet(
            final Class<?> ownerClass,
            final String fieldName,
            final String descriptor,
            final boolean expectStatic,
            final Object[] tsArgs
    ) {
        final Field field = findPreselectedField(ownerClass, fieldName, descriptor, expectStatic);
        final Object receiver;
        if (expectStatic) {
            if (tsArgs.length != 0) {
                throw new IllegalArgumentException(
                        "Static field getter `" + ownerClass.getName() + "#" + fieldName + "` expects no arguments."
                );
            }
            receiver = null;
        } else {
            if (tsArgs.length != 1) {
                throw new IllegalArgumentException(
                        "Instance field getter `" + ownerClass.getName() + "#" + fieldName + "` expects receiver only."
                );
            }
            receiver = tsArgs[0];
            ensureInstanceReceiver(ownerClass, fieldName, receiver);
        }
        try {
            return TsjInteropCodec.fromJava(field.get(receiver));
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop field not accessible: " + ownerClass.getName() + "#" + fieldName,
                    illegalAccessException
            );
        }
    }

    private static Object invokePreselectedFieldSet(
            final Class<?> ownerClass,
            final String fieldName,
            final String descriptor,
            final boolean expectStatic,
            final Object[] tsArgs
    ) {
        final Field field = findPreselectedField(ownerClass, fieldName, descriptor, expectStatic);
        final Object receiver;
        final Object value;
        if (expectStatic) {
            if (tsArgs.length != 1) {
                throw new IllegalArgumentException(
                        "Static field setter `" + ownerClass.getName() + "#" + fieldName + "` expects 1 argument."
                );
            }
            receiver = null;
            value = tsArgs[0];
        } else {
            if (tsArgs.length != 2) {
                throw new IllegalArgumentException(
                        "Instance field setter `" + ownerClass.getName() + "#" + fieldName
                                + "` expects receiver and value."
                );
            }
            receiver = tsArgs[0];
            ensureInstanceReceiver(ownerClass, fieldName, receiver);
            value = tsArgs[1];
        }
        try {
            final Object converted = TsjInteropCodec.toJava(value, field.getGenericType());
            field.set(receiver, converted);
            return TsjInteropCodec.fromJava(converted);
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop field not accessible: " + ownerClass.getName() + "#" + fieldName,
                    illegalAccessException
            );
        }
    }

    private static Constructor<?> findPreselectedConstructor(final Class<?> ownerClass, final String descriptor) {
        for (Constructor<?> constructor : ownerClass.getConstructors()) {
            if (constructorDescriptor(constructor).equals(descriptor)) {
                return constructor;
            }
        }
        throw new IllegalArgumentException(
                "TSJ-INTEROP-SELECTED preselected target not found: "
                        + ownerClass.getName()
                        + "#<init>"
                        + descriptor
        );
    }

    private static Method findPreselectedMethod(
            final Class<?> ownerClass,
            final String methodName,
            final String descriptor,
            final boolean expectStatic
    ) {
        for (Method method : ownerClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) != expectStatic) {
                continue;
            }
            if (methodDescriptor(method).equals(descriptor)) {
                return method;
            }
        }
        throw new IllegalArgumentException(
                "TSJ-INTEROP-SELECTED preselected target not found: "
                        + ownerClass.getName()
                        + "#"
                        + methodName
                        + descriptor
        );
    }

    private static Field findPreselectedField(
            final Class<?> ownerClass,
            final String fieldName,
            final String descriptor,
            final boolean expectStatic
    ) {
        final Field field = resolveField(ownerClass, fieldName, expectStatic);
        final String actualDescriptor = descriptorFor(field.getType());
        if (!actualDescriptor.equals(descriptor)) {
            throw new IllegalArgumentException(
                    "TSJ-INTEROP-SELECTED preselected target not found: "
                            + ownerClass.getName()
                            + "#"
                            + fieldName
                            + " expected descriptor "
                            + descriptor
                            + " but found "
                            + actualDescriptor
            );
        }
        return field;
    }

    private static Object invokeStatic(
            final Class<?> targetClass,
            final String className,
            final String methodName,
            final Object[] tsArgs
    ) {
        final List<Method> candidates = collectVisibleMethodCandidates(targetClass, methodName, true);
        if (candidates.isEmpty()) {
            final List<String> restrictedCandidates = declaredRestrictedMethodSignatures(targetClass, methodName, true);
            if (!restrictedCandidates.isEmpty()) {
                throw reflectiveAccessDiagnostic(
                        "non-public static method",
                        targetClass.getName() + "#" + methodName,
                        restrictedCandidates
                );
            }
            throw new IllegalArgumentException(
                    "No compatible static method `" + methodName + "` on " + className + " for arity "
                            + tsArgs.length + "."
            );
        }
        final ResolvedExecutable<Method> resolved = resolveExecutable(candidates, methodName, tsArgs);
        final Method method = resolved.member();
        try {
            final Object result = method.invoke(null, resolved.arguments());
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

    private static Object invokeConstructor(final Class<?> targetClass, final Object[] tsArgs) {
        final Constructor<?>[] constructors = targetClass.getConstructors();
        if (constructors.length == 0) {
            final List<String> restrictedCandidates = declaredRestrictedConstructorSignatures(targetClass);
            if (!restrictedCandidates.isEmpty()) {
                throw reflectiveAccessDiagnostic(
                        "non-public constructor",
                        targetClass.getName(),
                        restrictedCandidates
                );
            }
            throw new IllegalArgumentException("No public constructor available on " + targetClass.getName() + ".");
        }
        final ResolvedExecutable<Constructor<?>> resolved = resolveExecutable(
                Arrays.stream(constructors)
                        .sorted(Comparator
                                .comparing(TsjJavaInterop::constructorDescriptor)
                                .thenComparing(constructor -> constructor.getDeclaringClass().getName()))
                        .toList(),
                targetClass.getSimpleName(),
                tsArgs
        );
        try {
            return TsjInteropCodec.fromJava(resolved.member().newInstance(resolved.arguments()));
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            throw new IllegalArgumentException(
                    "Java interop constructor invocation failed for " + targetClass.getName() + ": "
                            + target.getMessage(),
                    target
            );
        } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalArgumentException(
                    "Java interop constructor not accessible for " + targetClass.getName(),
                    reflectiveOperationException
            );
        }
    }

    private static Object invokeInstance(
            final Class<?> targetClass,
            final String methodName,
            final Object[] tsArgs
    ) {
        if (tsArgs.length == 0) {
            throw new IllegalArgumentException(
                    "Instance interop method `" + methodName + "` requires receiver argument."
            );
        }
        final Object receiver = tsArgs[0];
        final Object[] methodArgs = Arrays.copyOfRange(tsArgs, 1, tsArgs.length);
        if (receiver == null) {
            throw new IllegalArgumentException(
                    "Instance interop method `" + methodName + "` receiver must not be null."
            );
        }
        if (!targetClass.isInstance(receiver)) {
            throw new IllegalArgumentException(
                    "Instance interop receiver type mismatch for " + targetClass.getName() + "#" + methodName
                            + ": got " + receiver.getClass().getName() + "."
            );
        }

        final List<Method> candidates = new ArrayList<>();
        candidates.addAll(collectVisibleMethodCandidates(targetClass, methodName, false));
        if (candidates.isEmpty()) {
            final List<String> restrictedCandidates = declaredRestrictedMethodSignatures(targetClass, methodName, false);
            if (!restrictedCandidates.isEmpty()) {
                throw reflectiveAccessDiagnostic(
                        "non-public instance method",
                        targetClass.getName() + "#" + methodName,
                        restrictedCandidates
                );
            }
            throw new IllegalArgumentException(
                    "No compatible instance method `" + methodName + "` on " + targetClass.getName()
                            + " for arity " + methodArgs.length + "."
            );
        }
        final ResolvedExecutable<Method> resolved = resolveExecutable(candidates, methodName, methodArgs);
        final Method method = resolved.member();
        try {
            final Object result = method.invoke(receiver, resolved.arguments());
            return TsjInteropCodec.fromJava(result);
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop instance method not accessible: " + targetClass.getName() + "#" + methodName,
                    illegalAccessException
            );
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            throw new IllegalArgumentException(
                    "Java interop invocation failed for " + targetClass.getName() + "#" + methodName
                            + ": " + target.getMessage(),
                    target
            );
        }
    }

    private static Object readStaticField(
            final Class<?> targetClass,
            final String fieldName,
            final Object[] tsArgs
    ) {
        if (tsArgs.length != 0) {
            throw new IllegalArgumentException(
                    "Static field getter `" + targetClass.getName() + "#" + fieldName + "` expects no arguments."
            );
        }
        final Field field = resolveField(targetClass, fieldName, true);
        try {
            return TsjInteropCodec.fromJava(field.get(null));
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop static field not accessible: " + targetClass.getName() + "#" + fieldName,
                    illegalAccessException
            );
        }
    }

    private static Object writeStaticField(
            final Class<?> targetClass,
            final String fieldName,
            final Object[] tsArgs
    ) {
        if (tsArgs.length != 1) {
            throw new IllegalArgumentException(
                    "Static field setter `" + targetClass.getName() + "#" + fieldName + "` expects 1 argument."
            );
        }
        final Field field = resolveField(targetClass, fieldName, true);
        try {
            final Object converted = TsjInteropCodec.toJava(tsArgs[0], field.getGenericType());
            field.set(null, converted);
            return TsjInteropCodec.fromJava(converted);
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop static field not accessible: " + targetClass.getName() + "#" + fieldName,
                    illegalAccessException
            );
        }
    }

    private static Object readInstanceField(
            final Class<?> targetClass,
            final String fieldName,
            final Object[] tsArgs
    ) {
        if (tsArgs.length != 1) {
            throw new IllegalArgumentException(
                    "Instance field getter `" + targetClass.getName() + "#" + fieldName + "` expects receiver only."
            );
        }
        final Object receiver = tsArgs[0];
        ensureInstanceReceiver(targetClass, fieldName, receiver);
        final Field field = resolveField(targetClass, fieldName, false);
        try {
            return TsjInteropCodec.fromJava(field.get(receiver));
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop instance field not accessible: " + targetClass.getName() + "#" + fieldName,
                    illegalAccessException
            );
        }
    }

    private static Object writeInstanceField(
            final Class<?> targetClass,
            final String fieldName,
            final Object[] tsArgs
    ) {
        if (tsArgs.length != 2) {
            throw new IllegalArgumentException(
                    "Instance field setter `" + targetClass.getName() + "#" + fieldName
                            + "` expects receiver and value."
            );
        }
        final Object receiver = tsArgs[0];
        ensureInstanceReceiver(targetClass, fieldName, receiver);
        final Field field = resolveField(targetClass, fieldName, false);
        try {
            final Object converted = TsjInteropCodec.toJava(tsArgs[1], field.getGenericType());
            field.set(receiver, converted);
            return TsjInteropCodec.fromJava(converted);
        } catch (final IllegalAccessException illegalAccessException) {
            throw new IllegalArgumentException(
                    "Java interop instance field not accessible: " + targetClass.getName() + "#" + fieldName,
                    illegalAccessException
            );
        }
    }

    private static void ensureInstanceReceiver(
            final Class<?> targetClass,
            final String memberName,
            final Object receiver
    ) {
        if (receiver == null) {
            throw new IllegalArgumentException(
                    "Instance interop receiver must not be null for " + targetClass.getName() + "#" + memberName + "."
            );
        }
        if (!targetClass.isInstance(receiver)) {
            throw new IllegalArgumentException(
                    "Instance interop receiver type mismatch for " + targetClass.getName() + "#" + memberName
                            + ": got " + receiver.getClass().getName() + "."
            );
        }
    }

    private static Field resolveField(
            final Class<?> targetClass,
            final String fieldName,
            final boolean expectStatic
    ) {
        final Field field;
        try {
            field = targetClass.getField(fieldName);
        } catch (final NoSuchFieldException noSuchFieldException) {
            final List<String> restrictedCandidates = declaredRestrictedFieldSignatures(
                    targetClass,
                    fieldName,
                    expectStatic
            );
            if (!restrictedCandidates.isEmpty()) {
                final String memberType = expectStatic ? "non-public static field" : "non-public instance field";
                throw reflectiveAccessDiagnostic(
                        memberType,
                        targetClass.getName() + "#" + fieldName,
                        restrictedCandidates
                );
            }
            throw new IllegalArgumentException(
                    "Interop field not found: " + targetClass.getName() + "#" + fieldName,
                    noSuchFieldException
            );
        }
        final boolean staticField = Modifier.isStatic(field.getModifiers());
        if (expectStatic != staticField) {
            final String expected = expectStatic ? "static" : "instance";
            throw new IllegalArgumentException(
                    "Interop field " + targetClass.getName() + "#" + fieldName + " is not " + expected + "."
            );
        }
        return field;
    }

    private static String requireBindingSuffix(final String bindingName, final String prefix) {
        final String suffix = bindingName.substring(prefix.length());
        if (suffix.isBlank()) {
            throw new IllegalArgumentException("Invalid interop binding `" + bindingName + "`.");
        }
        return suffix;
    }

    private static Class<?> resolveClass(final String className) {
        try {
            final ClassLoader classLoader = resolveInteropClassLoader();
            return Class.forName(className, true, classLoader);
        } catch (final ClassNotFoundException classNotFoundException) {
            throw new IllegalArgumentException("Java interop class not found: " + className, classNotFoundException);
        }
    }

    private static String normalizeOwnerName(final String owner) {
        if (owner.indexOf('/') < 0) {
            return owner;
        }
        return owner.replace('/', '.');
    }

    private static Object[] normalizeTsArgs(final Object[] tsArgs) {
        if (tsArgs == null) {
            return EMPTY_TS_ARGS;
        }
        return tsArgs;
    }

    private static ClassLoader resolveInteropClassLoader() {
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return contextLoader;
        }
        return TsjJavaInterop.class.getClassLoader();
    }

    private static List<Method> collectVisibleMethodCandidates(
            final Class<?> targetClass,
            final String methodName,
            final boolean expectStatic
    ) {
        final List<Method> visibleCandidates = new ArrayList<>();
        final List<Method> nonBridgeCandidates = new ArrayList<>();
        for (Method method : targetClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) != expectStatic) {
                continue;
            }
            if (!methodName.equals(method.getName())) {
                continue;
            }
            visibleCandidates.add(method);
            if (!method.isBridge() && !method.isSynthetic()) {
                nonBridgeCandidates.add(method);
            }
        }
        final List<Method> selected = nonBridgeCandidates.isEmpty() ? visibleCandidates : nonBridgeCandidates;
        selected.sort(Comparator
                .comparing(TsjJavaInterop::methodDescriptor)
                .thenComparing(method -> method.getDeclaringClass().getName()));
        return List.copyOf(selected);
    }

    private static String executableOrderKey(final Member member) {
        return member.getDeclaringClass().getName()
                + "#"
                + member.getName()
                + executableDescriptor(member);
    }

    private static String executableDescriptor(final Member member) {
        if (member instanceof Method method) {
            return methodDescriptor(method);
        }
        return constructorDescriptor((Constructor<?>) member);
    }

    private static List<String> declaredRestrictedMethodSignatures(
            final Class<?> targetClass,
            final String methodName,
            final boolean expectStatic
    ) {
        final List<String> signatures = new ArrayList<>();
        Class<?> cursor = targetClass;
        while (cursor != null) {
            final boolean classPublic = Modifier.isPublic(cursor.getModifiers());
            for (Method method : cursor.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) != expectStatic) {
                    continue;
                }
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                if (classPublic && Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                signatures.add(executableSignature(method));
            }
            cursor = cursor.getSuperclass();
        }
        return signatures;
    }

    private static List<String> declaredRestrictedConstructorSignatures(final Class<?> targetClass) {
        final List<String> signatures = new ArrayList<>();
        final boolean classPublic = Modifier.isPublic(targetClass.getModifiers());
        for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
            if (classPublic && Modifier.isPublic(constructor.getModifiers())) {
                continue;
            }
            signatures.add(executableSignature(constructor));
        }
        return signatures;
    }

    private static List<String> declaredRestrictedFieldSignatures(
            final Class<?> targetClass,
            final String fieldName,
            final boolean expectStatic
    ) {
        final List<String> signatures = new ArrayList<>();
        Class<?> cursor = targetClass;
        while (cursor != null) {
            final boolean classPublic = Modifier.isPublic(cursor.getModifiers());
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) != expectStatic) {
                    continue;
                }
                if (!fieldName.equals(field.getName())) {
                    continue;
                }
                if (classPublic && Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                signatures.add(fieldSignature(field));
            }
            cursor = cursor.getSuperclass();
        }
        return signatures;
    }

    private static IllegalArgumentException reflectiveAccessDiagnostic(
            final String memberType,
            final String memberKey,
            final List<String> declaredCandidates
    ) {
        final String candidates = declaredCandidates.isEmpty()
                ? "none"
                : String.join(", ", declaredCandidates);
        return new IllegalArgumentException(
                "TSJ-INTEROP-REFLECTIVE unsupported " + memberType + " access for " + memberKey
                        + ". Declared candidates: " + candidates + "."
        );
    }

    private static <M extends Member> ResolvedExecutable<M> resolveExecutable(
            final List<M> candidates,
            final String memberName,
            final Object[] tsArgs
    ) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No interop member candidates found for `" + memberName + "`.");
        }

        final List<ResolvedExecutable<M>> resolved = new ArrayList<>();
        final List<String> mismatchReasons = new ArrayList<>();
        for (M candidate : candidates) {
            final Class<?>[] parameterTypes = candidate instanceof Method method
                    ? method.getParameterTypes()
                    : ((Constructor<?>) candidate).getParameterTypes();
            final Type[] genericParameterTypes = candidate instanceof Method method
                    ? method.getGenericParameterTypes()
                    : ((Constructor<?>) candidate).getGenericParameterTypes();
            final boolean varArgs = candidate instanceof Method method && method.isVarArgs()
                    || candidate instanceof Constructor<?> constructor && constructor.isVarArgs();
            final ConversionAttempt conversion = convertArguments(tsArgs, parameterTypes, genericParameterTypes, varArgs);
            if (conversion.success()) {
                resolved.add(new ResolvedExecutable<>(
                        candidate,
                        conversion.arguments(),
                        conversion.score(),
                        varArgs,
                        executableSignature(candidate),
                        executableDescriptor(candidate),
                        executableOrderKey(candidate)
                ));
            } else {
                mismatchReasons.add(executableSignature(candidate) + ": " + conversion.failureReason());
            }
        }
        if (resolved.isEmpty()) {
            final String argTypes = describeTsArgTypes(tsArgs);
            final String candidatesSummary = summarizeCandidates(candidates);
            mismatchReasons.sort(String::compareTo);
            final String mismatchSummary = mismatchReasons.isEmpty()
                    ? ""
                    : " Conversion failures: " + String.join("; ", mismatchReasons);
            throw new IllegalArgumentException(
                    "No compatible static method `" + memberName + "` for argument types " + argTypes + ". "
                            + "Candidates: " + candidatesSummary + "." + mismatchSummary
            );
        }

        resolved.sort(Comparator
                .comparingInt(ResolvedExecutable<M>::score)
                .thenComparing(ResolvedExecutable<M>::varArgs)
                .thenComparing(ResolvedExecutable<M>::descriptor)
                .thenComparing(ResolvedExecutable<M>::orderKey));
        final ResolvedExecutable<M> best = resolved.getFirst();
        final List<ResolvedExecutable<M>> bestCandidates = resolved.stream()
                .filter(candidate -> candidate.score() == best.score() && candidate.varArgs() == best.varArgs())
                .toList();
        if (bestCandidates.size() == 1) {
            return best;
        }

        final ResolvedExecutable<M> specificityWinner = selectSpecificityWinner(bestCandidates, tsArgs.length);
        if (specificityWinner != null) {
            return specificityWinner;
        }

        throw new IllegalArgumentException(ambiguousCandidatesDiagnostic(memberName, tsArgs, bestCandidates));
    }

    private static ConversionAttempt convertArguments(
            final Object[] tsArgs,
            final Class<?>[] parameterTypes,
            final Type[] genericParameterTypes,
            final boolean varArgs
    ) {
        if (!varArgs && tsArgs.length != parameterTypes.length) {
            return ConversionAttempt.failure("arity mismatch");
        }
        if (varArgs && tsArgs.length < parameterTypes.length - 1) {
            return ConversionAttempt.failure("arity mismatch for varargs");
        }

        final Object[] converted = new Object[parameterTypes.length];
        int score = 0;
        final int fixedCount = varArgs ? parameterTypes.length - 1 : parameterTypes.length;
        for (int index = 0; index < fixedCount; index++) {
            final Type genericType = genericParameterTypes.length > index
                    ? genericParameterTypes[index]
                    : parameterTypes[index];
            final ConversionResult conversion = tryConvert(tsArgs[index], parameterTypes[index], genericType);
            if (!conversion.success()) {
                return ConversionAttempt.failure(
                        "argument " + index
                                + " incompatible with " + genericType.getTypeName()
                                + ": " + conversion.failureDetail()
                );
            }
            converted[index] = conversion.value();
            score += conversion.score();
        }

        if (varArgs) {
            final Class<?> arrayType = parameterTypes[parameterTypes.length - 1];
            final Class<?> componentType = arrayType.getComponentType();
            final Type componentGenericType = resolveVarArgComponentType(
                    genericParameterTypes.length > parameterTypes.length - 1
                            ? genericParameterTypes[parameterTypes.length - 1]
                            : arrayType,
                    componentType
            );
            final int varArgCount = tsArgs.length - fixedCount;
            final Object varArgArray = Array.newInstance(componentType, varArgCount);
            for (int varIndex = 0; varIndex < varArgCount; varIndex++) {
                final Object tsVarArgValue = tsArgs[fixedCount + varIndex];
                if (componentType == String.class && tsVarArgValue == null) {
                    Array.set(varArgArray, varIndex, "null");
                    score += 1;
                    continue;
                }
                final ConversionResult conversion = tryConvert(
                        tsVarArgValue,
                        componentType,
                        componentGenericType
                );
                if (!conversion.success()) {
                    return ConversionAttempt.failure(
                            "vararg " + varIndex
                                    + " incompatible with " + componentGenericType.getTypeName()
                                    + ": " + conversion.failureDetail()
                    );
                }
                Array.set(varArgArray, varIndex, conversion.value());
                score += conversion.score() + 1;
            }
            converted[parameterTypes.length - 1] = varArgArray;
            score += 1;
        }
        return ConversionAttempt.success(converted, score);
    }

    private static Type resolveVarArgComponentType(final Type genericVarArgType, final Class<?> fallbackComponentType) {
        if (genericVarArgType instanceof GenericArrayType genericArrayType) {
            return genericArrayType.getGenericComponentType();
        }
        if (genericVarArgType instanceof Class<?> classType && classType.isArray()) {
            return classType.getComponentType();
        }
        return fallbackComponentType;
    }

    private static ConversionResult tryConvert(
            final Object tsValue,
            final Class<?> targetType,
            final Type targetGenericType
    ) {
        final Class<?> boxedTarget = boxedType(targetType);
        if (isNumericTarget(boxedTarget)) {
            return tryConvertNumeric(tsValue, targetType, boxedTarget);
        }
        try {
            final Object value = TsjInteropCodec.toJava(tsValue, targetGenericType);
            final int score = conversionScore(tsValue, targetType);
            if (score >= CONVERSION_IMPOSSIBLE) {
                return ConversionResult.success(value, 50);
            }
            return ConversionResult.success(value, score);
        } catch (final IllegalArgumentException exception) {
            final String message = exception.getMessage() == null ? "conversion rejected" : exception.getMessage();
            return ConversionResult.failure(message);
        }
    }

    private static ConversionResult tryConvertNumeric(
            final Object tsValue,
            final Class<?> targetType,
            final Class<?> boxedTarget
    ) {
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            if (targetType.isPrimitive()) {
                return ConversionResult.failure(
                        "numeric conversion to " + targetType.getSimpleName()
                                + " requires a concrete value (got " + describeTsValueType(tsValue) + ")."
                );
            }
            return ConversionResult.success(null, 1);
        }
        final NumericKind targetKind = numericKindForTarget(boxedTarget);
        if (targetKind == null) {
            return ConversionResult.failure(
                    "numeric conversion target is unsupported: " + targetType.getName() + "."
            );
        }
        final NumericSource source = numericSource(tsValue);
        if (!source.valid()) {
            return ConversionResult.failure(
                    "numeric conversion from "
                            + describeTsValueType(tsValue)
                            + " to "
                            + targetType.getSimpleName()
                            + " is unsupported."
            );
        }
        final int sourceRank = source.kind().rank();
        final int targetRank = targetKind.rank();
        if (source.kind().floating() && targetKind.integral()) {
            return ConversionResult.failure(
                    "numeric conversion from "
                            + source.kind().display()
                            + " to "
                            + targetType.getSimpleName()
                            + " requires narrowing."
            );
        }
        if (sourceRank > targetRank) {
            return ConversionResult.failure(
                    "numeric conversion from "
                            + source.kind().display()
                            + " to "
                            + targetType.getSimpleName()
                            + " requires narrowing."
            );
        }

        final Object convertedValue;
        try {
            convertedValue = convertNumericValue(source.numericValue(), targetKind, targetType.getSimpleName());
        } catch (final IllegalArgumentException exception) {
            return ConversionResult.failure(exception.getMessage());
        }
        final int wideningDistance = targetRank - sourceRank;
        int score = wideningDistance;
        if (!targetType.isPrimitive()) {
            score += 1;
        }
        score += source.baseScore();
        return ConversionResult.success(convertedValue, score);
    }

    private static NumericSource numericSource(final Object tsValue) {
        if (tsValue instanceof Byte byteValue) {
            return new NumericSource(true, NumericKind.BYTE, byteValue.doubleValue(), 0);
        }
        if (tsValue instanceof Short shortValue) {
            return new NumericSource(true, NumericKind.SHORT, shortValue.doubleValue(), 0);
        }
        if (tsValue instanceof Integer integerValue) {
            return new NumericSource(true, NumericKind.INT, integerValue.doubleValue(), 0);
        }
        if (tsValue instanceof Long longValue) {
            return new NumericSource(true, NumericKind.LONG, longValue.doubleValue(), 0);
        }
        if (tsValue instanceof Float floatValue) {
            return new NumericSource(true, NumericKind.FLOAT, floatValue.doubleValue(), 0);
        }
        if (tsValue instanceof Double doubleValue) {
            return new NumericSource(true, NumericKind.DOUBLE, doubleValue.doubleValue(), 0);
        }
        if (tsValue instanceof Number numberValue) {
            return new NumericSource(true, NumericKind.DOUBLE, numberValue.doubleValue(), 1);
        }
        if (tsValue instanceof String || tsValue instanceof Boolean) {
            final double numericValue = TsjRuntime.toNumber(tsValue);
            if (Double.isNaN(numericValue) || Double.isInfinite(numericValue)) {
                return NumericSource.invalid();
            }
            if (numericValue == Math.rint(numericValue)) {
                if (numericValue >= Integer.MIN_VALUE && numericValue <= Integer.MAX_VALUE) {
                    return new NumericSource(true, NumericKind.INT, numericValue, 6);
                }
                if (numericValue >= Long.MIN_VALUE && numericValue <= Long.MAX_VALUE) {
                    return new NumericSource(true, NumericKind.LONG, numericValue, 6);
                }
            }
            return new NumericSource(true, NumericKind.DOUBLE, numericValue, 6);
        }
        return NumericSource.invalid();
    }

    private static Object convertNumericValue(
            final double sourceValue,
            final NumericKind targetKind,
            final String targetDisplay
    ) {
        return switch (targetKind) {
            case BYTE -> {
                final long integral = requireIntegralNumericValue(sourceValue, targetDisplay);
                if (integral < Byte.MIN_VALUE || integral > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "numeric conversion to " + targetDisplay + " is out of range: " + sourceValue + "."
                    );
                }
                yield Byte.valueOf((byte) integral);
            }
            case SHORT -> {
                final long integral = requireIntegralNumericValue(sourceValue, targetDisplay);
                if (integral < Short.MIN_VALUE || integral > Short.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "numeric conversion to " + targetDisplay + " is out of range: " + sourceValue + "."
                    );
                }
                yield Short.valueOf((short) integral);
            }
            case INT -> {
                final long integral = requireIntegralNumericValue(sourceValue, targetDisplay);
                if (integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "numeric conversion to " + targetDisplay + " is out of range: " + sourceValue + "."
                    );
                }
                yield Integer.valueOf((int) integral);
            }
            case LONG -> {
                final long integral = requireIntegralNumericValue(sourceValue, targetDisplay);
                yield Long.valueOf(integral);
            }
            case FLOAT -> {
                if (Double.isNaN(sourceValue) || Double.isInfinite(sourceValue)) {
                    throw new IllegalArgumentException(
                            "numeric conversion to " + targetDisplay + " produced non-finite value."
                    );
                }
                if (sourceValue > Float.MAX_VALUE || sourceValue < -Float.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "numeric conversion to " + targetDisplay + " is out of range: " + sourceValue + "."
                    );
                }
                yield Float.valueOf((float) sourceValue);
            }
            case DOUBLE -> Double.valueOf(sourceValue);
        };
    }

    private static long requireIntegralNumericValue(final double sourceValue, final String targetDisplay) {
        if (Double.isNaN(sourceValue) || Double.isInfinite(sourceValue)) {
            throw new IllegalArgumentException(
                    "numeric conversion to " + targetDisplay + " produced non-finite value."
            );
        }
        if (sourceValue != Math.rint(sourceValue)) {
            throw new IllegalArgumentException(
                    "numeric conversion to " + targetDisplay + " requires integral value: " + sourceValue + "."
            );
        }
        return (long) sourceValue;
    }

    private static int conversionScore(final Object tsValue, final Class<?> targetType) {
        final Class<?> boxedTarget = boxedType(targetType);
        if (tsValue == null || tsValue == TsjUndefined.INSTANCE) {
            return targetType.isPrimitive() ? CONVERSION_IMPOSSIBLE : 1;
        }
        if (boxedTarget != Object.class && boxedTarget.isInstance(tsValue)) {
            return 0;
        }
        if (targetType.isArray()) {
            return looksArrayLike(tsValue) ? 2 : CONVERSION_IMPOSSIBLE;
        }
        if (List.class.isAssignableFrom(boxedTarget)) {
            return looksArrayLike(tsValue) ? 2 : CONVERSION_IMPOSSIBLE;
        }
        if (Set.class.isAssignableFrom(boxedTarget)) {
            return looksArrayLike(tsValue) ? 2 : CONVERSION_IMPOSSIBLE;
        }
        if (Map.class.isAssignableFrom(boxedTarget)) {
            if (tsValue instanceof Map<?, ?>) {
                return 0;
            }
            if (tsValue instanceof TsjObject) {
                return looksArrayLike(tsValue) ? 4 : 2;
            }
            return CONVERSION_IMPOSSIBLE;
        }
        if (boxedTarget.isEnum()) {
            if (boxedTarget.isInstance(tsValue)) {
                return 0;
            }
            return tsValue instanceof String ? 2 : CONVERSION_IMPOSSIBLE;
        }
        if (Optional.class.isAssignableFrom(boxedTarget)) {
            return 2;
        }
        if (CompletableFuture.class.isAssignableFrom(boxedTarget)) {
            if (tsValue instanceof TsjPromise) {
                return 2;
            }
            return 4;
        }
        if (isFunctionalInterface(boxedTarget)) {
            return isTsCallable(tsValue) ? 2 : CONVERSION_IMPOSSIBLE;
        }
        if (boxedTarget == Object.class) {
            return 10;
        }
        if (boxedTarget == String.class) {
            return tsValue instanceof String ? 0 : 7;
        }
        if (boxedTarget == Boolean.class) {
            return tsValue instanceof Boolean ? 0 : 7;
        }
        if (boxedTarget == Character.class) {
            if (tsValue instanceof Character) {
                return 0;
            }
            if (tsValue instanceof String stringValue && !stringValue.isEmpty()) {
                return 3;
            }
            return CONVERSION_IMPOSSIBLE;
        }
        if (boxedTarget.isAssignableFrom(tsValue.getClass())) {
            return 0;
        }
        return CONVERSION_IMPOSSIBLE;
    }

    private static boolean isTsCallable(final Object value) {
        return value instanceof TsjCallable || value instanceof TsjCallableWithThis;
    }

    private static boolean isFunctionalInterface(final Class<?> type) {
        if (!type.isInterface()) {
            return false;
        }
        Method candidate = null;
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || isObjectContractMethod(method)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            if (candidate == null) {
                candidate = method;
                continue;
            }
            if (!sameSignature(candidate, method)) {
                return false;
            }
        }
        return candidate != null;
    }

    private static boolean isObjectContractMethod(final Method method) {
        final String name = method.getName();
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if ("toString".equals(name)) {
            return parameterTypes.length == 0 && method.getReturnType() == String.class;
        }
        if ("hashCode".equals(name)) {
            return parameterTypes.length == 0 && method.getReturnType() == int.class;
        }
        if ("equals".equals(name)) {
            return parameterTypes.length == 1
                    && parameterTypes[0] == Object.class
                    && method.getReturnType() == boolean.class;
        }
        return false;
    }

    private static boolean sameSignature(final Method left, final Method right) {
        return left.getName().equals(right.getName())
                && Arrays.equals(left.getParameterTypes(), right.getParameterTypes());
    }

    private static <M extends Member> ResolvedExecutable<M> selectSpecificityWinner(
            final List<ResolvedExecutable<M>> candidates,
            final int argumentCount
    ) {
        ResolvedExecutable<M> winner = null;
        for (ResolvedExecutable<M> candidate : candidates) {
            boolean dominates = true;
            for (ResolvedExecutable<M> other : candidates) {
                if (candidate == other) {
                    continue;
                }
                if (!moreSpecific(candidate, other, argumentCount)) {
                    dominates = false;
                    break;
                }
            }
            if (!dominates) {
                continue;
            }
            if (winner != null) {
                return null;
            }
            winner = candidate;
        }
        return winner;
    }

    private static boolean moreSpecific(
            final ResolvedExecutable<?> left,
            final ResolvedExecutable<?> right,
            final int argumentCount
    ) {
        final List<Class<?>> leftParameters = expandedParameterTypes(left, argumentCount);
        final List<Class<?>> rightParameters = expandedParameterTypes(right, argumentCount);
        if (leftParameters.size() != rightParameters.size()) {
            return false;
        }

        boolean strict = false;
        for (int index = 0; index < leftParameters.size(); index++) {
            final Class<?> leftType = leftParameters.get(index);
            final Class<?> rightType = rightParameters.get(index);
            if (leftType.equals(rightType)) {
                continue;
            }
            if (typeMoreSpecific(leftType, rightType)) {
                strict = true;
                continue;
            }
            return false;
        }
        return strict;
    }

    private static List<Class<?>> expandedParameterTypes(
            final ResolvedExecutable<?> candidate,
            final int argumentCount
    ) {
        final Class<?>[] parameterTypes = memberParameterTypes(candidate.member());
        final List<Class<?>> parameters = new ArrayList<>(Arrays.asList(parameterTypes));
        if (!candidate.varArgs()) {
            return parameters;
        }
        final int fixedCount = parameterTypes.length - 1;
        final Class<?> componentType = parameterTypes[parameterTypes.length - 1].getComponentType();
        if (componentType == null) {
            return parameters;
        }
        parameters.removeLast();
        for (int index = fixedCount; index < argumentCount; index++) {
            parameters.add(componentType);
        }
        return parameters;
    }

    private static Class<?>[] memberParameterTypes(final Member member) {
        if (member instanceof Method method) {
            return method.getParameterTypes();
        }
        return ((Constructor<?>) member).getParameterTypes();
    }

    private static boolean typeMoreSpecific(final Class<?> leftType, final Class<?> rightType) {
        if (leftType.isPrimitive() && rightType.isPrimitive()) {
            final Integer leftRank = primitiveSpecificityRank(leftType);
            final Integer rightRank = primitiveSpecificityRank(rightType);
            if (leftRank == null || rightRank == null) {
                return false;
            }
            return leftRank < rightRank;
        }
        if (!leftType.isPrimitive() && !rightType.isPrimitive()) {
            return rightType.isAssignableFrom(leftType) && !leftType.isAssignableFrom(rightType);
        }
        if (leftType.isPrimitive() && !rightType.isPrimitive()) {
            return rightType.isAssignableFrom(boxedType(leftType));
        }
        return false;
    }

    private static Integer primitiveSpecificityRank(final Class<?> primitiveType) {
        if (primitiveType == byte.class) {
            return 0;
        }
        if (primitiveType == short.class || primitiveType == char.class) {
            return 1;
        }
        if (primitiveType == int.class) {
            return 2;
        }
        if (primitiveType == long.class) {
            return 3;
        }
        if (primitiveType == float.class) {
            return 4;
        }
        if (primitiveType == double.class) {
            return 5;
        }
        return null;
    }

    private static String ambiguousCandidatesDiagnostic(
            final String memberName,
            final Object[] tsArgs,
            final List<? extends ResolvedExecutable<?>> candidates
    ) {
        final List<ResolvedExecutable<?>> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparing((ResolvedExecutable<?> candidate) -> candidate.descriptor())
                .thenComparing(candidate -> candidate.orderKey()));
        final List<String> candidateDetails = new ArrayList<>(sorted.size());
        for (ResolvedExecutable<?> candidate : sorted) {
            candidateDetails.add(
                    candidate.signature()
                            + " score="
                            + candidate.score()
                            + " reason="
                            + "same conversion score and no strict specificity winner"
            );
        }
        return "Ambiguous interop candidates for `"
                + memberName
                + "` with argument types "
                + describeTsArgTypes(tsArgs)
                + ". Candidates: "
                + String.join("; ", candidateDetails)
                + ".";
    }

    private static boolean looksArrayLike(final Object value) {
        if (value instanceof List<?> || value instanceof Object[]) {
            return true;
        }
        if (value != null && value.getClass().isArray()) {
            return true;
        }
        if (!(value instanceof TsjObject tsjObject)) {
            return false;
        }
        final Object lengthValue = tsjObject.get("length");
        if (lengthValue == TsjUndefined.INSTANCE || lengthValue == null) {
            return false;
        }
        final double length = TsjRuntime.toNumber(lengthValue);
        return !Double.isNaN(length)
                && Double.isFinite(length)
                && length >= 0
                && length == Math.rint(length);
    }

    private static boolean isNumericTarget(final Class<?> targetType) {
        return targetType == Byte.class
                || targetType == Short.class
                || targetType == Integer.class
                || targetType == Long.class
                || targetType == Float.class
                || targetType == Double.class;
    }

    private static Class<?> boxedType(final Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static String summarizeCandidates(final List<? extends Member> candidates) {
        final List<String> signatures = new ArrayList<>();
        for (Member candidate : candidates) {
            signatures.add(executableSignature(candidate));
        }
        signatures.sort(String::compareTo);
        return String.join(", ", signatures);
    }

    private static String executableSignature(final Member member) {
        final Class<?>[] parameterTypes = member instanceof Method method
                ? method.getParameterTypes()
                : ((Constructor<?>) member).getParameterTypes();
        final StringBuilder builder = new StringBuilder();
        builder.append(member.getName()).append("(");
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            final Class<?> parameterType = parameterTypes[index];
            if (parameterType.isArray()) {
                builder.append(parameterType.getComponentType().getSimpleName()).append("[]");
            } else {
                builder.append(parameterType.getSimpleName());
            }
        }
        builder.append(")");
        return builder.toString();
    }

    private static String fieldSignature(final Field field) {
        final StringBuilder builder = new StringBuilder();
        if (Modifier.isStatic(field.getModifiers())) {
            builder.append("static ");
        }
        builder.append(field.getType().getSimpleName())
                .append(" ")
                .append(field.getName());
        return builder.toString();
    }

    private static String constructorDescriptor(final Constructor<?> constructor) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            builder.append(descriptorFor(parameterType));
        }
        builder.append(")V");
        return builder.toString();
    }

    private static String methodDescriptor(final Method method) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Class<?> parameterType : method.getParameterTypes()) {
            builder.append(descriptorFor(parameterType));
        }
        builder.append(")");
        builder.append(descriptorFor(method.getReturnType()));
        return builder.toString();
    }

    private static String descriptorFor(final Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) {
                return "V";
            }
            if (type == boolean.class) {
                return "Z";
            }
            if (type == byte.class) {
                return "B";
            }
            if (type == short.class) {
                return "S";
            }
            if (type == char.class) {
                return "C";
            }
            if (type == int.class) {
                return "I";
            }
            if (type == long.class) {
                return "J";
            }
            if (type == float.class) {
                return "F";
            }
            return "D";
        }
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static String describeTsArgTypes(final Object[] args) {
        if (args.length == 0) {
            return "[]";
        }
        final List<String> types = new ArrayList<>(args.length);
        for (Object arg : args) {
            types.add(describeTsValueType(arg));
        }
        return "[" + String.join(", ", types) + "]";
    }

    private static String describeTsValueType(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value == TsjUndefined.INSTANCE) {
            return "undefined";
        }
        return value.getClass().getName().toLowerCase(Locale.ROOT);
    }

    private static void traceInvocation(final String className, final String bindingName, final Object[] args) {
        if (!TRACE_ENABLED) {
            return;
        }
        System.err.println(
                "TSJ-INTEROP-TRACE invoke class=" + className
                        + " binding=" + bindingName
                        + " argTypes=" + describeTsArgTypes(args)
        );
    }

    private static void traceSuccess(final String className, final String bindingName, final Object result) {
        if (!TRACE_ENABLED) {
            return;
        }
        final String resultType = result == null ? "null" : result.getClass().getName();
        System.err.println(
                "TSJ-INTEROP-TRACE result class=" + className
                        + " binding=" + bindingName
                        + " resultType=" + resultType
        );
    }

    private static void traceFailure(
            final String className,
            final String bindingName,
            final RuntimeException runtimeException
    ) {
        if (!TRACE_ENABLED) {
            return;
        }
        final String message = runtimeException.getMessage() == null ? "" : runtimeException.getMessage();
        System.err.println(
                "TSJ-INTEROP-TRACE error class=" + className
                        + " binding=" + bindingName
                        + " code=" + runtimeException.getClass().getSimpleName()
                        + " message=" + message
        );
    }

    private record ConversionResult(boolean success, Object value, int score, String failureDetail) {
        private static ConversionResult success(final Object value, final int score) {
            return new ConversionResult(true, value, score, "");
        }

        private static ConversionResult failure(final String failureDetail) {
            return new ConversionResult(false, null, 0, failureDetail);
        }
    }

    private record ConversionAttempt(boolean success, Object[] arguments, int score, String failureReason) {
        private static ConversionAttempt success(final Object[] arguments, final int score) {
            return new ConversionAttempt(true, arguments, score, null);
        }

        private static ConversionAttempt failure(final String reason) {
            return new ConversionAttempt(false, null, 0, reason);
        }
    }

    private record ResolvedExecutable<M extends Member>(
            M member,
            Object[] arguments,
            int score,
            boolean varArgs,
            String signature,
            String descriptor,
            String orderKey
    ) {
    }

    private enum NumericKind {
        BYTE(0, true, false, "byte"),
        SHORT(1, true, false, "short"),
        INT(2, true, false, "int"),
        LONG(3, true, false, "long"),
        FLOAT(4, false, true, "float"),
        DOUBLE(5, false, true, "double");

        private final int rank;
        private final boolean integral;
        private final boolean floating;
        private final String display;

        NumericKind(final int rank, final boolean integral, final boolean floating, final String display) {
            this.rank = rank;
            this.integral = integral;
            this.floating = floating;
            this.display = display;
        }

        private int rank() {
            return rank;
        }

        private boolean integral() {
            return integral;
        }

        private boolean floating() {
            return floating;
        }

        private String display() {
            return display;
        }
    }

    private static NumericKind numericKindForTarget(final Class<?> targetType) {
        if (targetType == Byte.class) {
            return NumericKind.BYTE;
        }
        if (targetType == Short.class) {
            return NumericKind.SHORT;
        }
        if (targetType == Integer.class) {
            return NumericKind.INT;
        }
        if (targetType == Long.class) {
            return NumericKind.LONG;
        }
        if (targetType == Float.class) {
            return NumericKind.FLOAT;
        }
        if (targetType == Double.class) {
            return NumericKind.DOUBLE;
        }
        return null;
    }

    private record NumericSource(
            boolean valid,
            NumericKind kind,
            double numericValue,
            int baseScore
    ) {
        private static NumericSource invalid() {
            return new NumericSource(false, null, Double.NaN, 0);
        }
    }
}
