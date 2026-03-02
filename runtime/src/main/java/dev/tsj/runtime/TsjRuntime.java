package dev.tsj.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime helpers used by TSJ-generated JVM classes in TSJ-9 subset.
 */
public final class TsjRuntime {
    private static final String DATE_MILLIS_KEY = "__tsj_date_millis";
    private static final String MAP_BACKING_KEY = "__tsj_map_backing";
    private static final String SET_BACKING_KEY = "__tsj_set_backing";
    private static final String WEAK_MAP_BACKING_KEY = "__tsj_weakmap_backing";
    private static final String WEAK_SET_BACKING_KEY = "__tsj_weakset_backing";
    private static final String WEAK_REF_TARGET_KEY = "__tsj_weakref_target";
    private static final String PROXY_TARGET_KEY = "__tsj_proxy_target";
    private static final String PROXY_HANDLER_KEY = "__tsj_proxy_handler";
    private static final String PROXY_REVOKED_KEY = "__tsj_proxy_revoked";
    private static final String REGEXP_PATTERN_KEY = "__tsj_regexp_pattern";
    private static final String REGEXP_FLAGS_KEY = "__tsj_regexp_flags";
    private static final String REGEXP_LAST_INDEX_KEY = "lastIndex";
    private static final Deque<Runnable> MICROTASK_QUEUE = new ArrayDeque<>();
    private static final Object INFINITY_VALUE = Double.valueOf(Double.POSITIVE_INFINITY);
    private static final Object NAN_VALUE = Double.valueOf(Double.NaN);
    private static final TsjClass ERROR_BUILTIN = createErrorBuiltin();
    private static final TsjClass AGGREGATE_ERROR_BUILTIN = createAggregateErrorBuiltin();
    private static final TsjClass TYPE_ERROR_BUILTIN = createNativeErrorSubtypeBuiltin("TypeError");
    private static final TsjClass RANGE_ERROR_BUILTIN = createNativeErrorSubtypeBuiltin("RangeError");
    private static final TsjCallable STRING_BUILTIN =
            args -> toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
    private static final TsjFunctionObject OBJECT_BUILTIN = createObjectBuiltin();
    private static final TsjObject REFLECT_BUILTIN = createReflectBuiltin();
    private static final TsjClass PROXY_BUILTIN = createProxyBuiltin();
    private static final TsjClass ARRAY_BUILTIN = createArrayBuiltin();
    private static final TsjClass MAP_BUILTIN = createMapBuiltin();
    private static final TsjClass SET_BUILTIN = createSetBuiltin();
    private static final TsjClass WEAK_MAP_BUILTIN = createWeakMapBuiltin();
    private static final TsjClass WEAK_SET_BUILTIN = createWeakSetBuiltin();
    private static final TsjClass WEAK_REF_BUILTIN = createWeakRefBuiltin();
    private static final TsjClass REGEXP_BUILTIN = createRegExpBuiltin();
    private static final TsjClass DATE_BUILTIN = createDateBuiltin();
    private static final TsjObject MATH_BUILTIN = createMathBuiltin();
    private static final TsjFunctionObject NUMBER_BUILTIN = createNumberBuiltin();
    private static final TsjFunctionObject BIGINT_BUILTIN = createBigIntBuiltin();
    private static final Map<String, TsjSymbol> SYMBOL_REGISTRY = new LinkedHashMap<>();
    private static final TsjSymbol SYMBOL_ITERATOR = TsjSymbol.create("Symbol.iterator");
    private static final TsjSymbol SYMBOL_TO_PRIMITIVE = TsjSymbol.create("Symbol.toPrimitive");
    private static final TsjFunctionObject SYMBOL_BUILTIN = createSymbolBuiltin();
    private static final TsjCallable PARSE_INT_BUILTIN = args -> parseIntBuiltinValue(args);
    private static final TsjCallable PARSE_FLOAT_BUILTIN = args -> parseFloatBuiltinValue(args);
    private static final TsjObject JSON_BUILTIN = createJsonBuiltin();
    private static final TsjObject PROMISE_BUILTIN = createPromiseBuiltin();
    private static final Map<String, Integer> REGEXP_LITERAL_LAST_INDEX = new IdentityHashMap<>();
    private static final Object COERCION_NOT_CALLABLE = new Object();
    private static final ThreadLocal<TsjGeneratorObject> ACTIVE_GENERATOR = new ThreadLocal<>();
    private static Consumer<Object> unhandledRejectionReporter = TsjRuntime::defaultUnhandledRejectionReporter;

    private TsjRuntime() {
    }

    public static void print(final Object value) {
        System.out.println(toDisplayString(value));
    }

    public static Object undefined() {
        return TsjUndefined.INSTANCE;
    }

    public static Object promiseBuiltin() {
        return PROMISE_BUILTIN;
    }

    public static Object errorBuiltin() {
        return ERROR_BUILTIN;
    }

    public static Object aggregateErrorBuiltin() {
        return AGGREGATE_ERROR_BUILTIN;
    }

    public static Object typeErrorBuiltin() {
        return TYPE_ERROR_BUILTIN;
    }

    public static Object rangeErrorBuiltin() {
        return RANGE_ERROR_BUILTIN;
    }

    public static Object stringBuiltin() {
        return STRING_BUILTIN;
    }

    public static Object objectBuiltin() {
        return OBJECT_BUILTIN;
    }

    public static Object reflectBuiltin() {
        return REFLECT_BUILTIN;
    }

    public static Object proxyBuiltin() {
        return PROXY_BUILTIN;
    }

    public static Object arrayBuiltin() {
        return ARRAY_BUILTIN;
    }

    public static Object mapBuiltin() {
        return MAP_BUILTIN;
    }

    public static Object setBuiltin() {
        return SET_BUILTIN;
    }

    public static Object weakMapBuiltin() {
        return WEAK_MAP_BUILTIN;
    }

    public static Object weakSetBuiltin() {
        return WEAK_SET_BUILTIN;
    }

    public static Object weakRefBuiltin() {
        return WEAK_REF_BUILTIN;
    }

    public static Object regexpBuiltin() {
        return REGEXP_BUILTIN;
    }

    public static Object dateBuiltin() {
        return DATE_BUILTIN;
    }

    public static Object mathBuiltin() {
        return MATH_BUILTIN;
    }

    public static Object numberBuiltin() {
        return NUMBER_BUILTIN;
    }

    public static Object bigIntBuiltin() {
        return BIGINT_BUILTIN;
    }

    public static Object symbolBuiltin() {
        return SYMBOL_BUILTIN;
    }

    public static Object parseIntBuiltin() {
        return PARSE_INT_BUILTIN;
    }

    public static Object parseFloatBuiltin() {
        return PARSE_FLOAT_BUILTIN;
    }

    public static Object bigIntLiteral(final String decimalText) {
        Objects.requireNonNull(decimalText, "decimalText");
        return new BigInteger(decimalText);
    }

    public static Object infinity() {
        return INFINITY_VALUE;
    }

    public static Object nanValue() {
        return NAN_VALUE;
    }

    public static Object jsonBuiltin() {
        return JSON_BUILTIN;
    }

    public static TsjPromise promiseResolve(final Object value) {
        return TsjPromise.resolved(value);
    }

    public static TsjPromise promiseReject(final Object reason) {
        return TsjPromise.rejected(reason);
    }

    public static TsjPromise promiseThen(
            final Object promiseLike,
            final Object onFulfilled,
            final Object onRejected
    ) {
        return promiseResolve(promiseLike).then(onFulfilled, onRejected);
    }

    static void reportUnhandledPromiseRejection(final Object reason) {
        unhandledRejectionReporter.accept(reason);
    }

    public static void setUnhandledRejectionReporter(final Consumer<Object> reporter) {
        unhandledRejectionReporter = Objects.requireNonNull(reporter, "reporter");
    }

    public static void resetUnhandledRejectionReporter() {
        unhandledRejectionReporter = TsjRuntime::defaultUnhandledRejectionReporter;
    }

    public static RuntimeException raise(final Object value) {
        if (value instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new TsjThrownException(value);
    }

    public static Object normalizeThrown(final RuntimeException exception) {
        if (exception instanceof TsjThrownException thrownException) {
            return thrownException.thrownValue();
        }
        return exception;
    }

    static void enqueueMicrotask(final Runnable task) {
        MICROTASK_QUEUE.addLast(task);
    }

    public static void flushMicrotasks() {
        while (!MICROTASK_QUEUE.isEmpty()) {
            final Runnable task = MICROTASK_QUEUE.removeFirst();
            task.run();
        }
    }

    public static Object call(final Object callee, final Object... args) {
        if (callee instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(undefined(), args);
        }
        if (callee instanceof TsjCallable callable) {
            return callable.call(args);
        }
        throw new IllegalArgumentException("Value is not callable: " + toDisplayString(callee));
    }

    public static Object createGenerator(
            final TsjCallableWithThis body,
            final Object thisValue,
            final Object... args
    ) {
        return new TsjGeneratorObject(body, thisValue, args);
    }

    public static Object generatorYield(final Object value) {
        return activeGeneratorOrThrow().yieldValue(value);
    }

    public static Object generatorYieldStar(final Object iterable) {
        final Object values = forOfValues(iterable);
        if (!(values instanceof TsjObject valuesObject)) {
            throw new IllegalArgumentException("`yield*` source is not iterable: " + toDisplayString(iterable));
        }
        final int length = (int) toNumber(valuesObject.get("length"));
        Object resumeValue = undefined();
        for (int index = 0; index < length; index++) {
            resumeValue = generatorYield(valuesObject.get(Integer.toString(index)));
        }
        return resumeValue;
    }

    static void enterGenerator(final TsjGeneratorObject generatorObject) {
        ACTIVE_GENERATOR.set(generatorObject);
    }

    static void exitGenerator() {
        ACTIVE_GENERATOR.remove();
    }

    private static TsjGeneratorObject activeGeneratorOrThrow() {
        final TsjGeneratorObject generatorObject = ACTIVE_GENERATOR.get();
        if (generatorObject == null) {
            throw new IllegalStateException("`yield` is only valid during generator execution.");
        }
        return generatorObject;
    }

    public static Object javaStaticMethod(final String className, final String methodName) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        return javaBinding(className, methodName);
    }

    public static Object javaBinding(final String className, final String bindingName) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(bindingName, "bindingName");
        if (TsjJavaInterop.hasPublicStaticField(className, bindingName)) {
            return TsjJavaInterop.readPublicStaticField(className, bindingName);
        }
        return (TsjCallable) args -> TsjJavaInterop.invokeBinding(className, bindingName, args);
    }

    public static TsjClass asClass(final Object value) {
        if (value instanceof TsjClass tsjClass) {
            return tsjClass;
        }
        throw new IllegalArgumentException("Value is not a class: " + toDisplayString(value));
    }

    public static Object construct(final Object constructor, final Object... args) {
        if (constructor instanceof TsjClass tsjClass) {
            return tsjClass.construct(args);
        }
        throw new IllegalArgumentException("Value is not constructable: " + toDisplayString(constructor));
    }

    public static Object objectLiteral(final Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Object literal arguments must be key/value pairs.");
        }
        final TsjObject object = new TsjObject(null);
        for (int index = 0; index < keyValuePairs.length; index += 2) {
            final Object keyValue = keyValuePairs[index];
            final String key = keyValue == null ? "null" : keyValue.toString();
            object.setOwn(key, keyValuePairs[index + 1]);
        }
        return object;
    }

    public static Object arrayLiteral(final Object... elements) {
        final TsjObject array = new TsjObject(null);
        for (int index = 0; index < elements.length; index++) {
            array.setOwn(Integer.toString(index), elements[index]);
        }
        array.setOwn("length", Integer.valueOf(elements.length));
        return array;
    }

    public static Object getProperty(final Object target, final String key) {
        if (target instanceof TsjObject tsjObject) {
            if (isProxyObject(tsjObject)) {
                return readProxyProperty(tsjObject, key);
            }
            return readProperty(tsjObject, key);
        }
        if (target instanceof TsjClass tsjClass) {
            final Object member = tsjClass.getStaticMember(key);
            if (member instanceof TsjAccessorDescriptor descriptor) {
                return readAccessorProperty(tsjClass, key, descriptor);
            }
            return member;
        }
        if (target instanceof String stringValue) {
            if ("length".equals(key)) {
                return Integer.valueOf(stringValue.length());
            }
            final int index = parseNonNegativeIndex(key);
            if (index >= 0 && index < stringValue.length()) {
                return String.valueOf(stringValue.charAt(index));
            }
            return undefined();
        }
        if (target instanceof Throwable throwable) {
            if ("message".equals(key)) {
                return throwable.getMessage();
            }
            if ("name".equals(key)) {
                return throwable.getClass().getSimpleName();
            }
            if ("cause".equals(key)) {
                return throwable.getCause();
            }
            return undefined();
        }
        throw new IllegalArgumentException("Cannot get property `" + key + "` from " + toDisplayString(target));
    }

    public static Object getPropertyCached(
            final TsjPropertyAccessCache cache,
            final Object target,
            final String key
    ) {
        Objects.requireNonNull(cache, "cache");
        return getProperty(target, key);
    }

    public static Object setProperty(final Object target, final String key, final Object value) {
        if (target instanceof TsjObject tsjObject) {
            if (isProxyObject(tsjObject)) {
                return writeProxyProperty(tsjObject, key, value);
            }
            return writeProperty(tsjObject, key, value);
        }
        if (target instanceof TsjClass tsjClass) {
            final Object existing = tsjClass.getStaticMember(key);
            if (existing instanceof TsjAccessorDescriptor descriptor) {
                writeAccessorProperty(tsjClass, key, descriptor, value);
                return value;
            }
            tsjClass.setStaticMember(key, value);
            return value;
        }
        throw new IllegalArgumentException("Cannot set property `" + key + "` on " + toDisplayString(target));
    }

    public static Object setPropertyDynamic(final Object target, final Object key, final Object value) {
        final String normalizedKey = propertyToKey(key);
        return setProperty(target, normalizedKey, value);
    }

    public static Object defineAccessorProperty(
            final Object target,
            final Object key,
            final Object getter,
            final Object setter
    ) {
        final String normalizedKey = propertyToKey(key);
        final Object normalizedGetter = normalizeAccessorComponent(getter, "getter");
        final Object normalizedSetter = normalizeAccessorComponent(setter, "setter");
        if (target instanceof TsjObject tsjObject) {
            final Object existing = tsjObject.getOwn(normalizedKey);
            final TsjAccessorDescriptor descriptor = mergeAccessorDescriptor(existing, normalizedGetter, normalizedSetter);
            tsjObject.setOwn(normalizedKey, descriptor);
            return target;
        }
        if (target instanceof TsjClass tsjClass) {
            final Object existing = tsjClass.getStaticMember(normalizedKey);
            final TsjAccessorDescriptor descriptor = mergeAccessorDescriptor(existing, normalizedGetter, normalizedSetter);
            tsjClass.setStaticMember(normalizedKey, descriptor);
            return target;
        }
        throw new IllegalArgumentException("Cannot define accessor property on " + toDisplayString(target));
    }

    public static boolean deleteProperty(final Object target, final String key) {
        if (target instanceof TsjObject tsjObject) {
            if (isProxyObject(tsjObject)) {
                return deleteProxyProperty(tsjObject, key);
            }
            return tsjObject.deleteOwn(key);
        }
        if (target instanceof TsjClass tsjClass) {
            return tsjClass.deleteStaticMember(key);
        }
        throw new IllegalArgumentException("Cannot delete property `" + key + "` from " + toDisplayString(target));
    }

    public static Object setPrototype(final Object target, final Object prototype) {
        if (!(target instanceof TsjObject tsjObject)) {
            throw new IllegalArgumentException("Cannot set prototype on " + toDisplayString(target));
        }
        if (prototype == null || prototype == TsjUndefined.INSTANCE) {
            tsjObject.setPrototype(null);
            return target;
        }
        if (prototype instanceof TsjObject tsjPrototype) {
            tsjObject.setPrototype(tsjPrototype);
            return target;
        }
        throw new IllegalArgumentException("Prototype must be object|null|undefined: " + toDisplayString(prototype));
    }

    public static Object setPrototypeValue(final Object target, final Object prototype) {
        setPrototype(target, prototype);
        return prototype;
    }

    public static Object assignCell(final TsjCell cell, final Object value) {
        Objects.requireNonNull(cell, "cell");
        cell.set(value);
        return value;
    }

    public static Object assignLogicalAnd(final TsjCell cell, final Supplier<Object> valueSupplier) {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = cell.get();
        if (!truthy(current)) {
            return current;
        }
        return assignCell(cell, valueSupplier.get());
    }

    public static Object assignLogicalOr(final TsjCell cell, final Supplier<Object> valueSupplier) {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = cell.get();
        if (truthy(current)) {
            return current;
        }
        return assignCell(cell, valueSupplier.get());
    }

    public static Object assignNullish(final TsjCell cell, final Supplier<Object> valueSupplier) {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = cell.get();
        if (!isNullish(current)) {
            return current;
        }
        return assignCell(cell, valueSupplier.get());
    }

    public static Object assignPropertyLogicalAnd(
            final Object target,
            final String key,
            final Supplier<Object> valueSupplier
    ) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = getProperty(target, key);
        if (!truthy(current)) {
            return current;
        }
        return setProperty(target, key, valueSupplier.get());
    }

    public static Object assignPropertyLogicalOr(
            final Object target,
            final String key,
            final Supplier<Object> valueSupplier
    ) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = getProperty(target, key);
        if (truthy(current)) {
            return current;
        }
        return setProperty(target, key, valueSupplier.get());
    }

    public static Object assignPropertyNullish(
            final Object target,
            final String key,
            final Supplier<Object> valueSupplier
    ) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = getProperty(target, key);
        if (!isNullish(current)) {
            return current;
        }
        return setProperty(target, key, valueSupplier.get());
    }

    public static Object assignPropertyDynamicLogicalAnd(
            final Object target,
            final Object key,
            final Supplier<Object> valueSupplier
    ) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = indexRead(target, key);
        if (!truthy(current)) {
            return current;
        }
        return setPropertyDynamic(target, key, valueSupplier.get());
    }

    public static Object assignPropertyDynamicLogicalOr(
            final Object target,
            final Object key,
            final Supplier<Object> valueSupplier
    ) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = indexRead(target, key);
        if (truthy(current)) {
            return current;
        }
        return setPropertyDynamic(target, key, valueSupplier.get());
    }

    public static Object assignPropertyDynamicNullish(
            final Object target,
            final Object key,
            final Supplier<Object> valueSupplier
    ) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        final Object current = indexRead(target, key);
        if (!isNullish(current)) {
            return current;
        }
        return setPropertyDynamic(target, key, valueSupplier.get());
    }

    public static Object optionalMemberAccess(final Object receiver, final String key) {
        if (isNullish(receiver)) {
            return undefined();
        }
        return getProperty(receiver, key);
    }

    public static Object optionalIndexRead(final Object receiver, final Object index) {
        if (isNullish(receiver)) {
            return undefined();
        }
        return indexRead(receiver, index);
    }

    public static Object optionalCall(final Object callee, final Supplier<Object[]> argsSupplier) {
        Objects.requireNonNull(argsSupplier, "argsSupplier");
        if (isNullish(callee)) {
            return undefined();
        }
        final Object[] args = argsSupplier.get();
        return call(callee, args == null ? new Object[0] : args);
    }

    public static Object optionalInvokeMember(
            final Object receiver,
            final String methodName,
            final Supplier<Object[]> argsSupplier
    ) {
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(argsSupplier, "argsSupplier");
        if (isNullish(receiver)) {
            return undefined();
        }
        final Object[] args = argsSupplier.get();
        return invokeMember(receiver, methodName, args == null ? new Object[0] : args);
    }

    public static boolean optionalDeleteProperty(final Object receiver, final String key) {
        if (isNullish(receiver)) {
            return true;
        }
        return deleteProperty(receiver, key);
    }

    public static Object superInvokeMember(
            final Object superClassValue,
            final Object receiver,
            final String methodName,
            final Object... args
    ) {
        Objects.requireNonNull(methodName, "methodName");
        final TsjClass superClass = asClass(superClassValue);
        if (!(receiver instanceof TsjObject tsjReceiver)) {
            throw new IllegalArgumentException("`super` receiver must be an object: " + toDisplayString(receiver));
        }
        final Object member = getProperty(superClass.prototype(), methodName);
        final Object[] safeArgs = args == null ? new Object[0] : args;
        if (member instanceof TsjMethod method) {
            return method.call(tsjReceiver, safeArgs);
        }
        if (member instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(tsjReceiver, safeArgs);
        }
        if (member instanceof TsjCallable callable) {
            return callable.call(safeArgs);
        }
        return call(member, safeArgs);
    }

    public static Object arraySpread(final Object... segments) {
        final List<Object> values = new ArrayList<>();
        for (Object segment : segments) {
            appendSpreadValues(values, segment);
        }
        return arrayLiteral(values.toArray());
    }

    public static Object objectSpread(final Object... segments) {
        final TsjObject result = new TsjObject(null);
        for (Object segment : segments) {
            if (isNullish(segment)) {
                continue;
            }
            if (segment instanceof TsjObject tsjObject) {
                for (Map.Entry<String, Object> entry : tsjObject.ownPropertiesView().entrySet()) {
                    result.setOwn(entry.getKey(), entry.getValue());
                }
                continue;
            }
            if (segment instanceof Map<?, ?> mapSegment) {
                for (Map.Entry<?, ?> entry : mapSegment.entrySet()) {
                    final String key = entry.getKey() == null ? "null" : entry.getKey().toString();
                    result.setOwn(key, entry.getValue());
                }
                continue;
            }
            throw new IllegalArgumentException("Spread target is not an object: " + toDisplayString(segment));
        }
        return result;
    }

    public static Object callSpread(final Object callee, final Object... segments) {
        final List<Object> values = new ArrayList<>();
        for (Object segment : segments) {
            appendSpreadValues(values, segment);
        }
        return call(callee, values.toArray());
    }

    public static Object restArgs(final Object[] args, final int startIndex) {
        final List<Object> values = new ArrayList<>();
        if (args != null) {
            final int safeStart = Math.max(0, startIndex);
            for (int index = safeStart; index < args.length; index++) {
                values.add(args[index]);
            }
        }
        return arrayLiteral(values.toArray());
    }

    public static Object arrayRest(final Object value, final int startIndex) {
        if (isNullish(value)) {
            throw new IllegalArgumentException("Cannot destructure rest from nullish value.");
        }
        final List<Object> values = new ArrayList<>();
        appendSpreadValues(values, value);
        final int safeStart = Math.max(0, startIndex);
        if (safeStart >= values.size()) {
            return arrayLiteral();
        }
        return arrayLiteral(values.subList(safeStart, values.size()).toArray());
    }

    public static Object indexRead(final Object target, final Object index) {
        final String key = propertyToKey(index);
        return getProperty(target, key);
    }

    public static Object forOfValues(final Object value) {
        if (isNullish(value)) {
            throw new IllegalArgumentException("Cannot iterate nullish value in for...of loop.");
        }
        final List<Object> values = new ArrayList<>();
        appendSpreadValues(values, value);
        return arrayLiteral(values.toArray());
    }

    public static Object forInKeys(final Object value) {
        if (isNullish(value)) {
            throw new IllegalArgumentException("Cannot enumerate nullish value in for...in loop.");
        }
        final List<Object> keys = new ArrayList<>();
        if (value instanceof TsjObject tsjObject) {
            for (String key : tsjObject.ownPropertiesView().keySet()) {
                keys.add(key);
            }
            return arrayLiteral(keys.toArray());
        }
        if (value instanceof Map<?, ?> mapValue) {
            for (Object key : mapValue.keySet()) {
                keys.add(key == null ? "null" : key.toString());
            }
            return arrayLiteral(keys.toArray());
        }
        throw new IllegalArgumentException("Cannot enumerate keys from value in for...in loop: "
                + toDisplayString(value));
    }

    public static Object invokeMember(final Object target, final String methodName, final Object... args) {
        if (target instanceof TsjObject tsjObject) {
            final Object member = getProperty(tsjObject, methodName);
            if (isUndefined(member)) {
                final Object callableMemberResult = invokeCallableMember(tsjObject, methodName, args);
                if (callableMemberResult != COERCION_NOT_CALLABLE) {
                    return callableMemberResult;
                }
                if ("hasOwnProperty".equals(methodName)) {
                    final String propertyName = propertyToKey(firstArg(args));
                    return Boolean.valueOf(tsjObject.hasOwn(propertyName));
                }
                final Object arrayLikeResult = invokeArrayLikeMember(tsjObject, methodName, args);
                if (arrayLikeResult != COERCION_NOT_CALLABLE) {
                    return arrayLikeResult;
                }
            }
            if (member instanceof TsjMethod method) {
                return method.call(tsjObject, args);
            }
            if (member instanceof TsjCallableWithThis callableWithThis) {
                return callableWithThis.callWithThis(tsjObject, args);
            }
            return call(member, args);
        }
        if (target instanceof TsjClass tsjClass) {
            final Object member = tsjClass.getStaticMember(methodName);
            if (member instanceof TsjCallableWithThis callableWithThis) {
                return callableWithThis.callWithThis(tsjClass, args);
            }
            if (member instanceof TsjCallable callable) {
                return callable.call(args);
            }
            throw new IllegalArgumentException(
                    "Cannot invoke static member `" + methodName + "` on " + toDisplayString(target)
            );
        }
        if (target instanceof String stringTarget) {
            return invokeStringMember(stringTarget, methodName, args);
        }
        if (target instanceof Number numberTarget) {
            return invokeNumberMember(numberTarget, methodName, args);
        }
        final Object callableMemberResult = invokeCallableMember(target, methodName, args);
        if (callableMemberResult != COERCION_NOT_CALLABLE) {
            return callableMemberResult;
        }
        if (target != null && target != TsjUndefined.INSTANCE) {
            return TsjJavaInterop.invokeInstanceMember(target, methodName, args);
        }
        throw new IllegalArgumentException("Cannot invoke member `" + methodName + "` on " + toDisplayString(target));
    }

    private static Object invokeCallableMember(final Object target, final String methodName, final Object... args) {
        if (!"apply".equals(methodName) && !"call".equals(methodName)) {
            return COERCION_NOT_CALLABLE;
        }
        final Object thisArg = firstArg(args);
        final Object[] callArgs;
        if ("apply".equals(methodName)) {
            callArgs = callableApplyArguments(secondArg(args));
        } else {
            callArgs = new Object[Math.max(0, args.length - 1)];
            for (int index = 1; index < args.length; index++) {
                callArgs[index - 1] = args[index];
            }
        }
        if (target instanceof TsjMethod method) {
            final TsjObject receiver = thisArg instanceof TsjObject tsjReceiver
                    ? tsjReceiver
                    : new TsjObject(null);
            return method.call(receiver, callArgs);
        }
        if (target instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(thisArg, callArgs);
        }
        if (target instanceof TsjCallable callable) {
            return callable.call(callArgs);
        }
        return COERCION_NOT_CALLABLE;
    }

    private static Object[] callableApplyArguments(final Object applyArgs) {
        if (isNullish(applyArgs)) {
            return new Object[0];
        }
        final List<Object> values = new ArrayList<>();
        appendSpreadValues(values, applyArgs);
        return values.toArray();
    }

    private static Object invokeArrayLikeMember(final TsjObject target, final String methodName, final Object... args) {
        if (!isArrayLikeObject(target)) {
            return COERCION_NOT_CALLABLE;
        }
        final List<Object> values = arrayLikeValues(target);
        switch (methodName) {
            case "push" -> {
                for (Object arg : args) {
                    values.add(arg);
                }
                writeArrayLikeValues(target, values);
                return Integer.valueOf(values.size());
            }
            case "pop" -> {
                if (values.isEmpty()) {
                    return undefined();
                }
                final Object removed = values.remove(values.size() - 1);
                writeArrayLikeValues(target, values);
                return removed;
            }
            case "shift" -> {
                if (values.isEmpty()) {
                    return undefined();
                }
                final Object removed = values.remove(0);
                writeArrayLikeValues(target, values);
                return removed;
            }
            case "unshift" -> {
                for (int index = args.length - 1; index >= 0; index--) {
                    values.add(0, args[index]);
                }
                writeArrayLikeValues(target, values);
                return Integer.valueOf(values.size());
            }
            case "map" -> {
                final Object callback = firstArg(args);
                final List<Object> mapped = new ArrayList<>(values.size());
                for (int index = 0; index < values.size(); index++) {
                    mapped.add(call(callback, values.get(index), Integer.valueOf(index), target));
                }
                return arrayLiteral(mapped.toArray());
            }
            case "filter" -> {
                final Object callback = firstArg(args);
                final List<Object> filtered = new ArrayList<>();
                for (int index = 0; index < values.size(); index++) {
                    final Object decision = call(callback, values.get(index), Integer.valueOf(index), target);
                    if (truthy(decision)) {
                        filtered.add(values.get(index));
                    }
                }
                return arrayLiteral(filtered.toArray());
            }
            case "reduce" -> {
                final Object callback = firstArg(args);
                if (values.isEmpty() && args.length < 2) {
                    throw new IllegalArgumentException("Array.reduce of empty array with no initial value.");
                }
                int index = 0;
                Object accumulator;
                if (args.length > 1) {
                    accumulator = args[1];
                } else {
                    accumulator = values.get(0);
                    index = 1;
                }
                for (; index < values.size(); index++) {
                    accumulator = call(
                            callback,
                            accumulator,
                            values.get(index),
                            Integer.valueOf(index),
                            target
                    );
                }
                return accumulator;
            }
            case "find" -> {
                final Object callback = firstArg(args);
                for (int index = 0; index < values.size(); index++) {
                    if (truthy(call(callback, values.get(index), Integer.valueOf(index), target))) {
                        return values.get(index);
                    }
                }
                return undefined();
            }
            case "findIndex" -> {
                final Object callback = firstArg(args);
                for (int index = 0; index < values.size(); index++) {
                    if (truthy(call(callback, values.get(index), Integer.valueOf(index), target))) {
                        return Integer.valueOf(index);
                    }
                }
                return Integer.valueOf(-1);
            }
            case "some" -> {
                final Object callback = firstArg(args);
                for (int index = 0; index < values.size(); index++) {
                    if (truthy(call(callback, values.get(index), Integer.valueOf(index), target))) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
            case "every" -> {
                final Object callback = firstArg(args);
                for (int index = 0; index < values.size(); index++) {
                    if (!truthy(call(callback, values.get(index), Integer.valueOf(index), target))) {
                        return Boolean.FALSE;
                    }
                }
                return Boolean.TRUE;
            }
            case "includes" -> {
                final Object search = firstArg(args);
                int start = args.length > 1
                        ? normalizeSliceIndex(args[1], values.size(), 0)
                        : 0;
                if (start < 0) {
                    start = Math.max(values.size() + start, 0);
                }
                for (int index = start; index < values.size(); index++) {
                    if (strictEquals(values.get(index), search)) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
            case "indexOf" -> {
                final Object search = firstArg(args);
                int start = args.length > 1
                        ? normalizeSliceIndex(args[1], values.size(), 0)
                        : 0;
                if (start < 0) {
                    start = Math.max(values.size() + start, 0);
                }
                for (int index = start; index < values.size(); index++) {
                    if (strictEquals(values.get(index), search)) {
                        return Integer.valueOf(index);
                    }
                }
                return Integer.valueOf(-1);
            }
            case "forEach" -> {
                final Object callback = firstArg(args);
                for (int index = 0; index < values.size(); index++) {
                    call(callback, values.get(index), Integer.valueOf(index), target);
                }
                return TsjUndefined.INSTANCE;
            }
            case "sort" -> {
                final Object comparator = firstArg(args);
                if (isUndefined(comparator)) {
                    values.sort((left, right) -> toDisplayString(left).compareTo(toDisplayString(right)));
                } else {
                    values.sort((left, right) -> {
                        final double result = toNumber(call(comparator, left, right));
                        if (result < 0d) {
                            return -1;
                        }
                        if (result > 0d) {
                            return 1;
                        }
                        return 0;
                    });
                }
                writeArrayLikeValues(target, values);
                return target;
            }
            case "slice" -> {
                final int start = normalizeSliceIndex(args.length > 0 ? args[0] : 0, values.size(), 0);
                final int end = normalizeSliceIndex(
                        args.length > 1 ? args[1] : Integer.valueOf(values.size()),
                        values.size(),
                        values.size()
                );
                final int boundedEnd = Math.max(start, end);
                return arrayLiteral(values.subList(start, boundedEnd).toArray());
            }
            case "concat" -> {
                final List<Object> concatenated = new ArrayList<>(values);
                for (Object arg : args) {
                    if (arg instanceof TsjObject tsjArg && isArrayLikeObject(tsjArg)) {
                        concatenated.addAll(arrayLikeValues(tsjArg));
                    } else {
                        concatenated.add(arg);
                    }
                }
                return arrayLiteral(concatenated.toArray());
            }
            case "join" -> {
                final String separator = args.length > 0 ? toDisplayString(args[0]) : ",";
                final StringBuilder builder = new StringBuilder();
                for (int index = 0; index < values.size(); index++) {
                    if (index > 0) {
                        builder.append(separator);
                    }
                    final Object value = values.get(index);
                    if (value != null && !isUndefined(value)) {
                        builder.append(toDisplayString(value));
                    }
                }
                return builder.toString();
            }
            case "reverse" -> {
                for (int left = 0, right = values.size() - 1; left < right; left++, right--) {
                    final Object swap = values.get(left);
                    values.set(left, values.get(right));
                    values.set(right, swap);
                }
                writeArrayLikeValues(target, values);
                return target;
            }
            case "flat" -> {
                final List<Object> flattened = new ArrayList<>();
                for (Object value : values) {
                    if (value instanceof TsjObject tsjValue && isArrayLikeObject(tsjValue)) {
                        flattened.addAll(arrayLikeValues(tsjValue));
                    } else {
                        flattened.add(value);
                    }
                }
                return arrayLiteral(flattened.toArray());
            }
            case "fill" -> {
                final Object fillValue = firstArg(args);
                final int start = normalizeSliceIndex(args.length > 1 ? args[1] : 0, values.size(), 0);
                final int end = normalizeSliceIndex(
                        args.length > 2 ? args[2] : Integer.valueOf(values.size()),
                        values.size(),
                        values.size()
                );
                for (int index = start; index < Math.max(start, end); index++) {
                    values.set(index, fillValue);
                }
                writeArrayLikeValues(target, values);
                return target;
            }
            default -> {
                return COERCION_NOT_CALLABLE;
            }
        }
    }

    private static List<Object> arrayLikeValues(final TsjObject target) {
        final int length = arrayLikeLength(target);
        final List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(target.get(Integer.toString(index)));
        }
        return values;
    }

    private static void writeArrayLikeValues(final TsjObject target, final List<Object> values) {
        final int previousLength = arrayLikeLength(target);
        for (int index = 0; index < values.size(); index++) {
            target.set(Integer.toString(index), values.get(index));
        }
        for (int index = values.size(); index < previousLength; index++) {
            target.deleteOwn(Integer.toString(index));
        }
        target.set("length", Integer.valueOf(values.size()));
    }

    private static boolean isArrayLikeObject(final TsjObject target) {
        final Object lengthValue = target.get("length");
        if (!(lengthValue instanceof Number)) {
            return false;
        }
        final double lengthNumber = toNumber(lengthValue);
        return Double.isFinite(lengthNumber)
                && lengthNumber >= 0
                && lengthNumber == Math.rint(lengthNumber)
                && lengthNumber <= Integer.MAX_VALUE;
    }

    private static int arrayLikeLength(final TsjObject target) {
        return (int) toNumber(target.get("length"));
    }

    private static Object invokeStringMember(final String target, final String methodName, final Object... args) {
        switch (methodName) {
            case "includes" -> {
                final String search = toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                final int start = normalizeStringStartIndex(args.length > 1 ? args[1] : 0, target.length());
                return Boolean.valueOf(target.substring(start).contains(search));
            }
            case "trim" -> {
                return target.trim();
            }
            case "trimStart", "trimLeft" -> {
                return target.stripLeading();
            }
            case "trimEnd", "trimRight" -> {
                return target.stripTrailing();
            }
            case "startsWith" -> {
                final String search = toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                final int start = normalizeStringStartIndex(args.length > 1 ? args[1] : 0, target.length());
                return Boolean.valueOf(target.startsWith(search, start));
            }
            case "endsWith" -> {
                final String search = toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                return Boolean.valueOf(target.endsWith(search));
            }
            case "repeat" -> {
                final double countNumber = toNumber(args.length > 0 ? args[0] : 0);
                if (!Double.isFinite(countNumber) || countNumber < 0d) {
                    throw new IllegalArgumentException("String.repeat count must be a non-negative finite number.");
                }
                return target.repeat((int) Math.floor(countNumber));
            }
            case "padStart" -> {
                final int targetLength = (int) Math.floor(toNumber(args.length > 0 ? args[0] : target.length()));
                final String fill = args.length > 1 ? toDisplayString(args[1]) : " ";
                return padString(target, targetLength, fill, true);
            }
            case "padEnd" -> {
                final int targetLength = (int) Math.floor(toNumber(args.length > 0 ? args[0] : target.length()));
                final String fill = args.length > 1 ? toDisplayString(args[1]) : " ";
                return padString(target, targetLength, fill, false);
            }
            case "charAt" -> {
                final int index = normalizeStringCharacterIndex(args.length > 0 ? args[0] : 0, target.length());
                if (index < 0 || index >= target.length()) {
                    return "";
                }
                return String.valueOf(target.charAt(index));
            }
            case "charCodeAt" -> {
                final int index = normalizeStringCharacterIndex(args.length > 0 ? args[0] : 0, target.length());
                if (index < 0 || index >= target.length()) {
                    return Double.valueOf(Double.NaN);
                }
                return Integer.valueOf(target.charAt(index));
            }
            case "indexOf" -> {
                final String search = toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                final int start = normalizeStringStartIndex(args.length > 1 ? args[1] : 0, target.length());
                return Integer.valueOf(target.indexOf(search, start));
            }
            case "lastIndexOf" -> {
                final String search = toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                final int start = args.length > 1
                        ? normalizeStringStartIndex(args[1], target.length())
                        : target.length();
                return Integer.valueOf(target.lastIndexOf(search, start));
            }
            case "slice" -> {
                final int start = normalizeSliceIndex(args.length > 0 ? args[0] : 0, target.length(), 0);
                final int end = normalizeSliceIndex(args.length > 1 ? args[1] : target.length(), target.length(), target.length());
                return target.substring(start, Math.max(start, end));
            }
            case "substring" -> {
                int start = normalizeSubstringIndex(args.length > 0 ? args[0] : 0, target.length());
                int end = args.length > 1 ? normalizeSubstringIndex(args[1], target.length()) : target.length();
                if (start > end) {
                    final int swap = start;
                    start = end;
                    end = swap;
                }
                return target.substring(start, end);
            }
            case "concat" -> {
                final StringBuilder builder = new StringBuilder(target);
                for (Object arg : args) {
                    builder.append(toDisplayString(arg));
                }
                return builder.toString();
            }
            case "test" -> {
                final RegexDescriptor descriptor = parseRegexDescriptor(target, TsjUndefined.INSTANCE);
                if (descriptor == null) {
                    return TsjJavaInterop.invokeInstanceMember(target, methodName, args);
                }
                final Object result = executeRegexLiteralWithState(target, descriptor, toDisplayString(firstArg(args)));
                return Boolean.valueOf(result != null);
            }
            case "exec" -> {
                final RegexDescriptor descriptor = parseRegexDescriptor(target, TsjUndefined.INSTANCE);
                if (descriptor == null) {
                    return TsjJavaInterop.invokeInstanceMember(target, methodName, args);
                }
                return executeRegexLiteralWithState(target, descriptor, toDisplayString(firstArg(args)));
            }
            case "match" -> {
                final RegexDescriptor descriptor = regexDescriptorFromValue(firstArg(args));
                if (descriptor == null) {
                    return TsjJavaInterop.invokeInstanceMember(target, methodName, args);
                }
                final Pattern pattern = compileRegexPattern(descriptor);
                final Matcher matcher = pattern.matcher(target);
                if (descriptor.global()) {
                    final List<Object> matches = new ArrayList<>();
                    while (matcher.find()) {
                        matches.add(matcher.group(0));
                    }
                    return matches.isEmpty() ? null : arrayLiteral(matches.toArray());
                }
                if (!matcher.find()) {
                    return null;
                }
                return buildRegexMatchArray(matcher, target);
            }
            case "search" -> {
                final RegexDescriptor descriptor = regexDescriptorFromValue(firstArg(args));
                if (descriptor == null) {
                    return Integer.valueOf(target.indexOf(toDisplayString(firstArg(args))));
                }
                final Matcher matcher = compileRegexPattern(descriptor).matcher(target);
                return Integer.valueOf(matcher.find() ? matcher.start() : -1);
            }
            case "replace" -> {
                final Object patternValue = firstArg(args);
                final String replacement = toDisplayString(secondArg(args));
                final RegexDescriptor descriptor = regexDescriptorFromValue(patternValue);
                if (descriptor != null) {
                    final Matcher matcher = compileRegexPattern(descriptor).matcher(target);
                    final String quotedReplacement = Matcher.quoteReplacement(replacement);
                    return descriptor.global()
                            ? matcher.replaceAll(quotedReplacement)
                            : matcher.replaceFirst(quotedReplacement);
                }
                final String search = toDisplayString(patternValue);
                final int index = target.indexOf(search);
                if (index < 0) {
                    return target;
                }
                return target.substring(0, index) + replacement + target.substring(index + search.length());
            }
            case "replaceAll" -> {
                final String search = toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                final String replacement = toDisplayString(args.length > 1 ? args[1] : TsjUndefined.INSTANCE);
                return target.replace(search, replacement);
            }
            case "at" -> {
                if (target.isEmpty()) {
                    return undefined();
                }
                int index = normalizeStringCharacterIndex(args.length > 0 ? args[0] : 0, target.length());
                if (index < 0) {
                    index = target.length() + index;
                }
                if (index < 0 || index >= target.length()) {
                    return undefined();
                }
                return String.valueOf(target.charAt(index));
            }
            default -> {
                return TsjJavaInterop.invokeInstanceMember(target, methodName, args);
            }
        }
    }

    private static Object invokeNumberMember(final Number target, final String methodName, final Object... args) {
        switch (methodName) {
            case "toFixed" -> {
                final double value = target.doubleValue();
                if (Double.isNaN(value)) {
                    return "NaN";
                }
                if (Double.isInfinite(value)) {
                    return value > 0d ? "Infinity" : "-Infinity";
                }
                final int digits = (int) Math.floor(toNumber(args.length > 0 ? args[0] : 0));
                final int scale = Math.max(0, Math.min(100, digits));
                return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
            }
            case "toString" -> {
                if (args.length == 0 || isUndefined(args[0])) {
                    return toDisplayString(target);
                }
                final double radixValue = toNumber(args[0]);
                if (!Double.isFinite(radixValue)) {
                    return toDisplayString(target);
                }
                final int radix = (int) Math.floor(radixValue);
                if (radix < 2 || radix > 36) {
                    return toDisplayString(target);
                }
                final double numeric = target.doubleValue();
                if (!Double.isFinite(numeric)) {
                    return toDisplayString(target);
                }
                if (numeric == Math.rint(numeric)) {
                    return Long.toString((long) numeric, radix);
                }
                return Double.toString(numeric);
            }
            case "valueOf" -> {
                return target;
            }
            default -> {
                return TsjJavaInterop.invokeInstanceMember(target, methodName, args);
            }
        }
    }

    private static int normalizeStringStartIndex(final Object value, final int length) {
        final double numericIndex = toNumber(value);
        if (Double.isNaN(numericIndex) || numericIndex <= 0d) {
            return 0;
        }
        if (!Double.isFinite(numericIndex)) {
            return length;
        }
        final int index = (int) Math.floor(numericIndex);
        return Math.min(index, length);
    }

    private static int normalizeStringCharacterIndex(final Object value, final int length) {
        final double numericIndex = toNumber(value);
        if (Double.isNaN(numericIndex) || !Double.isFinite(numericIndex)) {
            return 0;
        }
        if (numericIndex == 0d) {
            return 0;
        }
        final int index = (int) Math.floor(numericIndex);
        if (index < 0) {
            return index;
        }
        return Math.min(index, length);
    }

    private static int normalizeSliceIndex(final Object value, final int length, final int defaultValue) {
        final double numericIndex = toNumber(value);
        if (Double.isNaN(numericIndex)) {
            return defaultValue;
        }
        if (!Double.isFinite(numericIndex)) {
            return numericIndex < 0d ? 0 : length;
        }
        int index = (int) Math.floor(numericIndex);
        if (index < 0) {
            index = Math.max(length + index, 0);
        } else {
            index = Math.min(index, length);
        }
        return index;
    }

    private static int normalizeSubstringIndex(final Object value, final int length) {
        final double numericIndex = toNumber(value);
        if (Double.isNaN(numericIndex) || numericIndex <= 0d) {
            return 0;
        }
        if (!Double.isFinite(numericIndex)) {
            return length;
        }
        return Math.min((int) Math.floor(numericIndex), length);
    }

    private static String padString(
            final String value,
            final int targetLength,
            final String fillValue,
            final boolean left
    ) {
        final int clampedLength = Math.max(0, targetLength);
        if (value.length() >= clampedLength) {
            return value;
        }
        final String fill = fillValue.isEmpty() ? " " : fillValue;
        final int missing = clampedLength - value.length();
        final StringBuilder padding = new StringBuilder(missing);
        while (padding.length() < missing) {
            padding.append(fill);
        }
        final String padded = padding.substring(0, missing);
        return left ? padded + value : value + padded;
    }

    public static Object add(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).add(toBigInt(right));
        }
        if (left instanceof String || right instanceof String) {
            return toDisplayString(left) + toDisplayString(right);
        }
        return narrowNumber(toNumber(left) + toNumber(right));
    }

    public static Object comma(final Object left, final Object right) {
        return right;
    }

    public static Object unaryPlus(final Object value) {
        return narrowNumber(toNumber(value));
    }

    public static Object subtract(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).subtract(toBigInt(right));
        }
        return narrowNumber(toNumber(left) - toNumber(right));
    }

    public static Object multiply(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).multiply(toBigInt(right));
        }
        return narrowNumber(toNumber(left) * toNumber(right));
    }

    public static Object divide(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).divide(toBigInt(right));
        }
        return Double.valueOf(toNumber(left) / toNumber(right));
    }

    public static Object modulo(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).remainder(toBigInt(right));
        }
        return Double.valueOf(toNumber(left) % toNumber(right));
    }

    public static Object power(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            final BigInteger exponent = toBigInt(right);
            if (exponent.signum() < 0) {
                throw new IllegalArgumentException("BigInt exponent must be non-negative.");
            }
            return toBigInt(left).pow(exponent.intValueExact());
        }
        return narrowNumber(Math.pow(toNumber(left), toNumber(right)));
    }

    public static Object bitwiseAnd(final Object left, final Object right) {
        return Integer.valueOf(toInt32(left) & toInt32(right));
    }

    public static Object bitwiseOr(final Object left, final Object right) {
        return Integer.valueOf(toInt32(left) | toInt32(right));
    }

    public static Object bitwiseXor(final Object left, final Object right) {
        return Integer.valueOf(toInt32(left) ^ toInt32(right));
    }

    public static Object bitwiseNot(final Object value) {
        return Integer.valueOf(~toInt32(value));
    }

    public static Object shiftLeft(final Object left, final Object right) {
        final int shift = (int) (toUint32(right) & 31L);
        return Integer.valueOf(toInt32(left) << shift);
    }

    public static Object shiftRight(final Object left, final Object right) {
        final int shift = (int) (toUint32(right) & 31L);
        return Integer.valueOf(toInt32(left) >> shift);
    }

    public static Object shiftRightUnsigned(final Object left, final Object right) {
        final int shift = (int) (toUint32(right) & 31L);
        final long unsigned = Integer.toUnsignedLong(toInt32(left) >>> shift);
        return narrowNumber((double) unsigned);
    }

    public static Object negate(final Object value) {
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.negate();
        }
        return narrowNumber(-toNumber(value));
    }

    public static Object typeOf(final Object value) {
        if (isUndefined(value)) {
            return "undefined";
        }
        if (value == null) {
            return "object";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            if (value instanceof BigInteger) {
                return "bigint";
            }
            return "number";
        }
        if (value instanceof TsjSymbol) {
            return "symbol";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof TsjCallable || value instanceof TsjCallableWithThis
                || value instanceof TsjMethod || value instanceof TsjClass) {
            return "function";
        }
        return "object";
    }

    public static boolean inOperator(final Object property, final Object target) {
        final String key = propertyToKey(property);
        if (target instanceof TsjObject tsjObject) {
            if (isProxyObject(tsjObject)) {
                return proxyHasProperty(tsjObject, key);
            }
            return hasProperty(tsjObject, key);
        }
        if (target instanceof Map<?, ?> mapValue) {
            return mapValue.containsKey(key);
        }
        throw new IllegalArgumentException("Right-hand side of `in` is not an object: " + toDisplayString(target));
    }

    public static boolean instanceOf(final Object value, final Object constructor) {
        if (!(constructor instanceof TsjClass tsjClass)) {
            return false;
        }
        if (value instanceof Throwable) {
            return tsjClass == ERROR_BUILTIN;
        }
        if (!(value instanceof TsjObject tsjObject)) {
            return false;
        }
        TsjObject cursor = tsjObject.prototype();
        final TsjObject targetPrototype = tsjClass.prototype();
        while (cursor != null) {
            if (cursor == targetPrototype) {
                return true;
            }
            cursor = cursor.prototype();
        }
        return false;
    }

    public static boolean lessThan(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).compareTo(toBigInt(right)) < 0;
        }
        return toNumber(left) < toNumber(right);
    }

    public static boolean lessThanOrEqual(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).compareTo(toBigInt(right)) <= 0;
        }
        return toNumber(left) <= toNumber(right);
    }

    public static boolean greaterThan(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).compareTo(toBigInt(right)) > 0;
        }
        return toNumber(left) > toNumber(right);
    }

    public static boolean greaterThanOrEqual(final Object left, final Object right) {
        if (left instanceof BigInteger || right instanceof BigInteger) {
            return toBigInt(left).compareTo(toBigInt(right)) >= 0;
        }
        return toNumber(left) >= toNumber(right);
    }

    public static boolean strictEquals(final Object left, final Object right) {
        if (left == right) {
            if (left instanceof Double leftDouble && Double.isNaN(leftDouble.doubleValue())) {
                return false;
            }
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof BigInteger || right instanceof BigInteger) {
            if (left instanceof BigInteger leftBig && right instanceof BigInteger rightBig) {
                return leftBig.equals(rightBig);
            }
            return false;
        }
        if (left instanceof Number && right instanceof Number) {
            final double leftNumber = toNumber(left);
            final double rightNumber = toNumber(right);
            if (Double.isNaN(leftNumber) || Double.isNaN(rightNumber)) {
                return false;
            }
            return leftNumber == rightNumber;
        }
        return left.equals(right);
    }

    public static boolean abstractEquals(final Object left, final Object right) {
        if (strictEquals(left, right)) {
            return true;
        }
        if (isNullish(left) && isNullish(right)) {
            return true;
        }
        if (left instanceof Boolean) {
            return abstractEquals(Double.valueOf(toNumber(left)), right);
        }
        if (right instanceof Boolean) {
            return abstractEquals(left, Double.valueOf(toNumber(right)));
        }
        if (left instanceof Number && right instanceof String) {
            return abstractEquals(left, Double.valueOf(toNumber(right)));
        }
        if (left instanceof String && right instanceof Number) {
            return abstractEquals(Double.valueOf(toNumber(left)), right);
        }
        if (left instanceof TsjObject leftObject && (right instanceof Number || right instanceof String)) {
            return abstractEquals(toPrimitiveForAbstractEquals(leftObject), right);
        }
        if (right instanceof TsjObject rightObject && (left instanceof Number || left instanceof String)) {
            return abstractEquals(left, toPrimitiveForAbstractEquals(rightObject));
        }
        return false;
    }

    public static boolean truthy(final Object value) {
        if (value == null || isUndefined(value)) {
            return false;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue.booleanValue();
        }
        if (value instanceof Number) {
            final double numberValue = toNumber(value);
            return !Double.isNaN(numberValue) && numberValue != 0.0d;
        }
        if (value instanceof String stringValue) {
            return !stringValue.isEmpty();
        }
        return true;
    }

    public static Object logicalAnd(final Object left, final Supplier<Object> rightSupplier) {
        Objects.requireNonNull(rightSupplier, "rightSupplier");
        if (!truthy(left)) {
            return left;
        }
        return rightSupplier.get();
    }

    public static Object logicalOr(final Object left, final Supplier<Object> rightSupplier) {
        Objects.requireNonNull(rightSupplier, "rightSupplier");
        if (truthy(left)) {
            return left;
        }
        return rightSupplier.get();
    }

    public static Object nullishCoalesce(final Object left, final Supplier<Object> rightSupplier) {
        Objects.requireNonNull(rightSupplier, "rightSupplier");
        if (isNullish(left)) {
            return rightSupplier.get();
        }
        return left;
    }

    public static boolean isNullishValue(final Object value) {
        return isNullish(value);
    }

    public static String toDisplayString(final Object value) {
        if (isUndefined(value)) {
            return "undefined";
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.toString();
        }
        if (value instanceof TsjSymbol symbol) {
            return symbol.toString();
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue.booleanValue() ? "true" : "false";
        }
        if (value instanceof Number) {
            final double numericValue = toNumber(value);
            if (Double.isNaN(numericValue)) {
                return "NaN";
            }
            if (Double.isInfinite(numericValue)) {
                return numericValue > 0 ? "Infinity" : "-Infinity";
            }
            if (numericValue == Math.rint(numericValue)) {
                return Long.toString((long) numericValue);
            }
            return Double.toString(numericValue);
        }
        if (value instanceof TsjObject tsjObject) {
            final Object primitive = toPrimitiveWithHint(tsjObject, "string");
            if (isPrimitiveValue(primitive)) {
                return toDisplayString(primitive);
            }
        }
        return value.toString();
    }

    public static double toNumber(final Object value) {
        if (isUndefined(value)) {
            return Double.NaN;
        }
        if (value == null) {
            return 0.0d;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.doubleValue();
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof Boolean boolValue) {
            return boolValue.booleanValue() ? 1.0d : 0.0d;
        }
        if (value instanceof String stringValue) {
            final String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return 0.0d;
            }
            try {
                return Double.parseDouble(trimmed);
            } catch (final NumberFormatException numberFormatException) {
                return Double.NaN;
            }
        }
        if (value instanceof TsjObject tsjObject) {
            final Object primitive = toPrimitiveWithHint(tsjObject, "number");
            if (isPrimitiveValue(primitive)) {
                return toNumber(primitive);
            }
            return Double.NaN;
        }
        return Double.NaN;
    }

    private static int toInt32(final Object value) {
        return (int) toUint32(value);
    }

    private static long toUint32(final Object value) {
        final double number = toNumber(value);
        if (!Double.isFinite(number) || number == 0d) {
            return 0L;
        }
        final double truncated = number < 0d ? Math.ceil(number) : Math.floor(number);
        return ((long) truncated) & 0xFFFF_FFFFL;
    }

    private static String propertyToKey(final Object value) {
        if (value == null) {
            return "null";
        }
        if (isUndefined(value)) {
            return "undefined";
        }
        if (value instanceof TsjSymbol symbol) {
            return symbol.propertyKey();
        }
        return toDisplayString(value);
    }

    private static boolean isPrimitiveValue(final Object value) {
        if (value == COERCION_NOT_CALLABLE) {
            return false;
        }
        return value == null
                || isUndefined(value)
                || value instanceof Number
                || value instanceof String
                || value instanceof Boolean
                || value instanceof TsjSymbol;
    }

    private static BigInteger toBigInt(final Object value) {
        if (value instanceof BigInteger bigInteger) {
            return bigInteger;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue ? BigInteger.ONE : BigInteger.ZERO;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof Number numberValue) {
            final double numeric = numberValue.doubleValue();
            if (!Double.isFinite(numeric)) {
                throw new IllegalArgumentException("Cannot convert non-finite number to BigInt.");
            }
            final long truncated = (long) numeric;
            if (numeric != truncated) {
                throw new IllegalArgumentException("Cannot convert fractional number to BigInt.");
            }
            return BigInteger.valueOf(truncated);
        }
        if (value instanceof String stringValue) {
            return new BigInteger(stringValue.trim());
        }
        throw new IllegalArgumentException("Cannot convert value to BigInt: " + toDisplayString(value));
    }

    private static Object readProperty(final TsjObject receiver, final String key) {
        final ResolvedProperty resolvedProperty = resolveProperty(receiver, key);
        if (resolvedProperty == null) {
            return undefined();
        }
        final Object value = resolvedProperty.value();
        if (value instanceof TsjAccessorDescriptor descriptor) {
            return readAccessorProperty(receiver, key, descriptor);
        }
        return value;
    }

    private static Object writeProperty(final TsjObject receiver, final String key, final Object value) {
        final ResolvedProperty resolvedProperty = resolveProperty(receiver, key);
        if (resolvedProperty != null && resolvedProperty.value() instanceof TsjAccessorDescriptor descriptor) {
            writeAccessorProperty(receiver, key, descriptor, value);
            return value;
        }
        receiver.set(key, value);
        return value;
    }

    private static void initializeProxyObject(
            final TsjObject proxyObject,
            final Object target,
            final Object handler
    ) {
        if (!(target instanceof TsjObject || target instanceof TsjClass || target instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Proxy target must be object-like: " + toDisplayString(target));
        }
        if (!(handler instanceof TsjObject)) {
            throw new IllegalArgumentException("Proxy handler must be an object: " + toDisplayString(handler));
        }
        proxyObject.setOwn(PROXY_TARGET_KEY, target);
        proxyObject.setOwn(PROXY_HANDLER_KEY, handler);
        proxyObject.setOwn(PROXY_REVOKED_KEY, Boolean.FALSE);
    }

    private static boolean isProxyObject(final TsjObject candidate) {
        return candidate.hasOwn(PROXY_TARGET_KEY) && candidate.hasOwn(PROXY_HANDLER_KEY);
    }

    private static Object readProxyProperty(final TsjObject proxyObject, final String key) {
        final Object target = proxyTarget(proxyObject);
        final Object trap = proxyTrap(proxyObject, "get");
        if (!isUndefined(trap)) {
            return call(trap, target, key, proxyObject);
        }
        return getProperty(target, key);
    }

    private static Object writeProxyProperty(final TsjObject proxyObject, final String key, final Object value) {
        final Object target = proxyTarget(proxyObject);
        final Object trap = proxyTrap(proxyObject, "set");
        if (!isUndefined(trap)) {
            call(trap, target, key, value, proxyObject);
            return value;
        }
        return setProperty(target, key, value);
    }

    private static boolean deleteProxyProperty(final TsjObject proxyObject, final String key) {
        final Object target = proxyTarget(proxyObject);
        final Object trap = proxyTrap(proxyObject, "deleteProperty");
        if (!isUndefined(trap)) {
            return truthy(call(trap, target, key));
        }
        return deleteProperty(target, key);
    }

    private static boolean proxyHasProperty(final TsjObject proxyObject, final String key) {
        final Object target = proxyTarget(proxyObject);
        final Object trap = proxyTrap(proxyObject, "has");
        if (!isUndefined(trap)) {
            return truthy(call(trap, target, key));
        }
        if (target instanceof TsjObject tsjTarget) {
            return hasProperty(tsjTarget, key);
        }
        if (target instanceof Map<?, ?> mapTarget) {
            return mapTarget.containsKey(key);
        }
        if (target instanceof TsjClass tsjClass) {
            return !isUndefined(tsjClass.getStaticMember(key));
        }
        throw new IllegalArgumentException("Proxy target does not support `in`: " + toDisplayString(target));
    }

    private static Object proxyTarget(final TsjObject proxyObject) {
        if (Boolean.TRUE.equals(proxyObject.getOwn(PROXY_REVOKED_KEY))) {
            throw new IllegalStateException("Proxy has been revoked.");
        }
        return proxyObject.getOwn(PROXY_TARGET_KEY);
    }

    private static Object proxyTrap(final TsjObject proxyObject, final String trapName) {
        final Object handler = proxyObject.getOwn(PROXY_HANDLER_KEY);
        if (!(handler instanceof TsjObject tsjHandler)) {
            return undefined();
        }
        return getProperty(tsjHandler, trapName);
    }

    private static ResolvedProperty resolveProperty(final TsjObject receiver, final String key) {
        TsjObject cursor = receiver;
        while (cursor != null) {
            if (cursor.hasOwn(key)) {
                return new ResolvedProperty(cursor.getOwn(key));
            }
            cursor = cursor.prototype();
        }
        return null;
    }

    private static Object normalizeAccessorComponent(final Object value, final String accessorName) {
        if (value == null || isUndefined(value)) {
            return undefined();
        }
        if (value instanceof TsjMethod || value instanceof TsjCallableWithThis || value instanceof TsjCallable) {
            return value;
        }
        throw new IllegalArgumentException(
                "Accessor " + accessorName + " must be callable or undefined: " + toDisplayString(value)
        );
    }

    private static TsjAccessorDescriptor mergeAccessorDescriptor(
            final Object existing,
            final Object getter,
            final Object setter
    ) {
        Object mergedGetter = getter;
        Object mergedSetter = setter;
        if (existing instanceof TsjAccessorDescriptor descriptor) {
            if (isUndefined(mergedGetter)) {
                mergedGetter = descriptor.getter();
            }
            if (isUndefined(mergedSetter)) {
                mergedSetter = descriptor.setter();
            }
        }
        return new TsjAccessorDescriptor(mergedGetter, mergedSetter);
    }

    private static Object readAccessorProperty(
            final Object receiver,
            final String key,
            final TsjAccessorDescriptor descriptor
    ) {
        final Object getter = descriptor.getter();
        if (isUndefined(getter)) {
            return undefined();
        }
        if (getter instanceof TsjMethod method) {
            if (receiver instanceof TsjObject tsjReceiver) {
                return method.call(tsjReceiver);
            }
            throw new IllegalArgumentException("Accessor getter requires object receiver for `" + key + "`.");
        }
        if (getter instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(receiver);
        }
        if (getter instanceof TsjCallable callable) {
            return callable.call();
        }
        throw new IllegalArgumentException("Accessor getter is not callable for `" + key + "`.");
    }

    private static void writeAccessorProperty(
            final Object receiver,
            final String key,
            final TsjAccessorDescriptor descriptor,
            final Object value
    ) {
        final Object setter = descriptor.setter();
        if (isUndefined(setter)) {
            throw new IllegalArgumentException("Cannot set accessor property `" + key + "` without setter.");
        }
        if (setter instanceof TsjMethod method) {
            if (receiver instanceof TsjObject tsjReceiver) {
                method.call(tsjReceiver, value);
                return;
            }
            throw new IllegalArgumentException("Accessor setter requires object receiver for `" + key + "`.");
        }
        if (setter instanceof TsjCallableWithThis callableWithThis) {
            callableWithThis.callWithThis(receiver, value);
            return;
        }
        if (setter instanceof TsjCallable callable) {
            callable.call(value);
            return;
        }
        throw new IllegalArgumentException("Accessor setter is not callable for `" + key + "`.");
    }

    private static int parseNonNegativeIndex(final String key) {
        if (key == null || key.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < key.length(); index++) {
            final char ch = key.charAt(index);
            if (ch < '0' || ch > '9') {
                return -1;
            }
        }
        try {
            return Integer.parseInt(key);
        } catch (final NumberFormatException exception) {
            return -1;
        }
    }

    private static List<String> ownKeys(final Object value) {
        final List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ownEntries(value)) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    private static List<Object> ownValues(final Object value) {
        final List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ownEntries(value)) {
            values.add(entry.getValue());
        }
        return values;
    }

    private static List<Map.Entry<String, Object>> ownEntries(final Object value) {
        if (value instanceof TsjObject tsjObject) {
            return new ArrayList<>(tsjObject.ownPropertiesView().entrySet());
        }
        if (value instanceof Map<?, ?> mapValue) {
            final List<Map.Entry<String, Object>> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                final String key = entry.getKey() == null ? "null" : entry.getKey().toString();
                entries.add(Map.entry(key, entry.getValue()));
            }
            return entries;
        }
        if (value instanceof String stringValue) {
            final List<Map.Entry<String, Object>> entries = new ArrayList<>();
            for (int index = 0; index < stringValue.length(); index++) {
                entries.add(Map.entry(Integer.toString(index), String.valueOf(stringValue.charAt(index))));
            }
            return entries;
        }
        throw new IllegalArgumentException("Object operation requires an object-like value.");
    }

    private static long resolveDateMillis(final Object[] args) {
        if (args.length == 0 || isUndefined(args[0])) {
            return System.currentTimeMillis();
        }
        if (args.length == 1) {
            final double numeric = toNumber(args[0]);
            if (!Double.isFinite(numeric)) {
                return 0L;
            }
            return (long) numeric;
        }

        final int year = (int) toNumber(args[0]);
        final int month = (int) toNumber(args[1]) + 1;
        final int day = args.length > 2 ? (int) toNumber(args[2]) : 1;
        final int hour = args.length > 3 ? (int) toNumber(args[3]) : 0;
        final int minute = args.length > 4 ? (int) toNumber(args[4]) : 0;
        final int second = args.length > 5 ? (int) toNumber(args[5]) : 0;
        final int milli = args.length > 6 ? (int) toNumber(args[6]) : 0;

        final ZonedDateTime utcDateTime = ZonedDateTime.of(
                year,
                month,
                day,
                hour,
                minute,
                second,
                milli * 1_000_000,
                ZoneOffset.UTC
        );
        return utcDateTime.toInstant().toEpochMilli();
    }

    private static long readDateMillis(final TsjObject dateObject) {
        final Object value = dateObject.get(DATE_MILLIS_KEY);
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> mapBacking(final TsjObject mapObject) {
        final Object value = mapObject.get(MAP_BACKING_KEY);
        if (value instanceof Map<?, ?>) {
            return (Map<Object, Object>) value;
        }
        final Map<Object, Object> created = new LinkedHashMap<>();
        mapObject.setOwn(MAP_BACKING_KEY, created);
        mapObject.setOwn("size", Integer.valueOf(0));
        return created;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<Object> setBacking(final TsjObject setObject) {
        final Object value = setObject.get(SET_BACKING_KEY);
        if (value instanceof java.util.Set<?>) {
            return (java.util.Set<Object>) value;
        }
        final java.util.Set<Object> created = new java.util.LinkedHashSet<>();
        setObject.setOwn(SET_BACKING_KEY, created);
        setObject.setOwn("size", Integer.valueOf(0));
        return created;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> weakMapBacking(final TsjObject weakMapObject) {
        final Object value = weakMapObject.get(WEAK_MAP_BACKING_KEY);
        if (value instanceof Map<?, ?>) {
            return (Map<Object, Object>) value;
        }
        final Map<Object, Object> created = new java.util.WeakHashMap<>();
        weakMapObject.setOwn(WEAK_MAP_BACKING_KEY, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<Object> weakSetBacking(final TsjObject weakSetObject) {
        final Object value = weakSetObject.get(WEAK_SET_BACKING_KEY);
        if (value instanceof java.util.Set<?>) {
            return (java.util.Set<Object>) value;
        }
        final java.util.Set<Object> created = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
        weakSetObject.setOwn(WEAK_SET_BACKING_KEY, created);
        return created;
    }

    private static ZonedDateTime dateTimeFromMillis(final long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
    }

    private static RegexDescriptor regexDescriptorFromValue(final Object value) {
        if (value instanceof TsjObject tsjObject) {
            final Object rawPattern = tsjObject.get(REGEXP_PATTERN_KEY);
            final Object rawFlags = tsjObject.get(REGEXP_FLAGS_KEY);
            if (rawPattern instanceof String pattern && rawFlags instanceof String flags) {
                final boolean global = Boolean.TRUE.equals(tsjObject.get("__tsj_regexp_global"));
                return new RegexDescriptor(pattern, flags, global);
            }
            return null;
        }
        if (value instanceof String stringValue) {
            return parseRegexLiteralDescriptor(stringValue);
        }
        return null;
    }

    private static RegexDescriptor parseRegexDescriptor(final String rawPattern, final Object rawFlags) {
        if (isUndefined(rawFlags) || rawFlags == null) {
            final RegexDescriptor literal = parseRegexLiteralDescriptor(rawPattern);
            if (literal != null) {
                return literal;
            }
        }
        final String flags = isUndefined(rawFlags) || rawFlags == null ? "" : toDisplayString(rawFlags);
        return new RegexDescriptor(rawPattern, normalizeRegexFlags(flags), flags.indexOf('g') >= 0);
    }

    private static RegexDescriptor parseRegexLiteralDescriptor(final String literal) {
        if (literal == null || literal.length() < 2 || literal.charAt(0) != '/') {
            return null;
        }
        int closingSlash = -1;
        for (int index = literal.length() - 1; index > 0; index--) {
            if (literal.charAt(index) == '/' && !isEscapedRegexSlash(literal, index)) {
                closingSlash = index;
                break;
            }
        }
        if (closingSlash <= 0) {
            return null;
        }
        final String pattern = literal.substring(1, closingSlash);
        final String flags = literal.substring(closingSlash + 1);
        return new RegexDescriptor(pattern, normalizeRegexFlags(flags), flags.indexOf('g') >= 0);
    }

    private static boolean isEscapedRegexSlash(final String literal, final int slashIndex) {
        int backslashes = 0;
        int cursor = slashIndex - 1;
        while (cursor >= 0 && literal.charAt(cursor) == '\\') {
            backslashes++;
            cursor--;
        }
        return (backslashes % 2) != 0;
    }

    private static String normalizeRegexFlags(final String rawFlags) {
        final StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < rawFlags.length(); index++) {
            final char flag = rawFlags.charAt(index);
            if (flag == 'g') {
                continue;
            }
            if (flag != 'i' && flag != 'm' && flag != 's' && flag != 'u') {
                continue;
            }
            if (normalized.indexOf(Character.toString(flag)) < 0) {
                normalized.append(flag);
            }
        }
        return normalized.toString();
    }

    private static Pattern compileRegexPattern(final RegexDescriptor descriptor) {
        int flags = 0;
        final String rawFlags = descriptor.flags();
        if (rawFlags.indexOf('i') >= 0) {
            flags |= Pattern.CASE_INSENSITIVE;
            flags |= Pattern.UNICODE_CASE;
        }
        if (rawFlags.indexOf('m') >= 0) {
            flags |= Pattern.MULTILINE;
        }
        if (rawFlags.indexOf('s') >= 0) {
            flags |= Pattern.DOTALL;
        }
        if (rawFlags.indexOf('u') >= 0) {
            flags |= Pattern.UNICODE_CASE;
            flags |= Pattern.UNICODE_CHARACTER_CLASS;
        }
        return Pattern.compile(descriptor.pattern(), flags);
    }

    private static Object executeRegexWithState(final TsjObject regexObject, final String input) {
        final RegexDescriptor descriptor = regexDescriptorFromValue(regexObject);
        if (descriptor == null) {
            throw new IllegalArgumentException("Invalid RegExp object.");
        }
        final int startIndex = descriptor.global()
                ? Math.max(0, (int) toNumber(regexObject.get(REGEXP_LAST_INDEX_KEY)))
                : 0;
        final Matcher matcher = compileRegexPattern(descriptor).matcher(input);
        if (!matcher.find(Math.min(startIndex, input.length()))) {
            if (descriptor.global()) {
                regexObject.setOwn(REGEXP_LAST_INDEX_KEY, Integer.valueOf(0));
            }
            return null;
        }
        if (descriptor.global()) {
            regexObject.setOwn(REGEXP_LAST_INDEX_KEY, Integer.valueOf(matcher.end()));
        }
        return buildRegexMatchArray(matcher, input);
    }

    private static Object executeRegexLiteralWithState(
            final String literal,
            final RegexDescriptor descriptor,
            final String input
    ) {
        final int startIndex = descriptor.global()
                ? REGEXP_LITERAL_LAST_INDEX.getOrDefault(literal, Integer.valueOf(0)).intValue()
                : 0;
        final Matcher matcher = compileRegexPattern(descriptor).matcher(input);
        if (!matcher.find(Math.min(Math.max(startIndex, 0), input.length()))) {
            if (descriptor.global()) {
                REGEXP_LITERAL_LAST_INDEX.put(literal, Integer.valueOf(0));
            }
            return null;
        }
        if (descriptor.global()) {
            REGEXP_LITERAL_LAST_INDEX.put(literal, Integer.valueOf(matcher.end()));
        }
        return buildRegexMatchArray(matcher, input);
    }

    private static Object buildRegexMatchArray(final Matcher matcher, final String input) {
        final List<Object> groups = new ArrayList<>();
        for (int index = 0; index <= matcher.groupCount(); index++) {
            groups.add(matcher.group(index));
        }
        final Object result = arrayLiteral(groups.toArray());
        if (result instanceof TsjObject tsjObject) {
            tsjObject.setOwn("index", Integer.valueOf(matcher.start()));
            tsjObject.setOwn("input", input);
        }
        return result;
    }

    private static boolean hasProperty(final TsjObject object, final String key) {
        TsjObject cursor = object;
        while (cursor != null) {
            if (cursor.hasOwn(key)) {
                return true;
            }
            cursor = cursor.prototype();
        }
        return false;
    }

    private static Object narrowNumber(final double value) {
        if (!Double.isFinite(value)) {
            return Double.valueOf(value);
        }
        if (value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return Integer.valueOf((int) value);
        }
        return Double.valueOf(value);
    }

    private static boolean isUndefined(final Object value) {
        return value == TsjUndefined.INSTANCE;
    }

    private static boolean isNullish(final Object value) {
        return value == null || isUndefined(value);
    }

    private static void appendSpreadValues(final List<Object> target, final Object segment) {
        if (segment instanceof String stringValue) {
            for (int index = 0; index < stringValue.length(); index++) {
                target.add(String.valueOf(stringValue.charAt(index)));
            }
            return;
        }
        if (segment instanceof TsjObject tsjObject) {
            final Object iteratorMember = resolveIteratorMember(tsjObject);
            if (!isUndefined(iteratorMember)) {
                target.addAll(asIteratorList(tsjObject, iteratorMember, "Spread target"));
                return;
            }
            final Object lengthValue = tsjObject.get("length");
            if (lengthValue instanceof Number) {
                final int length = (int) toNumber(lengthValue);
                for (int index = 0; index < Math.max(0, length); index++) {
                    target.add(tsjObject.get(Integer.toString(index)));
                }
                return;
            }
            throw new IllegalArgumentException("Spread target is not iterable: " + toDisplayString(segment));
        }
        if (segment instanceof Object[] objectArray) {
            for (Object value : objectArray) {
                target.add(value);
            }
            return;
        }
        if (segment instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                target.add(value);
            }
            return;
        }
        throw new IllegalArgumentException("Spread target is not iterable: " + toDisplayString(segment));
    }

    private static Object toPrimitiveForAbstractEquals(final TsjObject objectValue) {
        final Object primitive = toPrimitiveWithHint(objectValue, "default");
        if (isPrimitiveForAbstractEquals(primitive)) {
            return primitive;
        }

        // Object.prototype.toString fallback for plain objects in TSJ subset.
        return "[object Object]";
    }

    private static Object toPrimitiveWithHint(final TsjObject objectValue, final String hint) {
        final String normalizedHint = hint == null ? "default" : hint;
        final Object symbolToPrimitiveMember = objectValue.get(SYMBOL_TO_PRIMITIVE.propertyKey());
        final Object symbolToPrimitiveResult = invokeCoercionMember(
                objectValue,
                symbolToPrimitiveMember,
                normalizedHint
        );
        if (isPrimitiveValue(symbolToPrimitiveResult)) {
            return symbolToPrimitiveResult;
        }
        if (symbolToPrimitiveResult != COERCION_NOT_CALLABLE) {
            throw new IllegalArgumentException("[Symbol.toPrimitive] must return a primitive value.");
        }

        final boolean preferString = "string".equals(normalizedHint);
        final Object primaryResult = preferString
                ? invokeCoercionMember(objectValue, objectValue.get("toString"))
                : invokeCoercionMember(objectValue, objectValue.get("valueOf"));
        if (isPrimitiveValue(primaryResult)) {
            return primaryResult;
        }

        final Object secondaryResult = preferString
                ? invokeCoercionMember(objectValue, objectValue.get("valueOf"))
                : invokeCoercionMember(objectValue, objectValue.get("toString"));
        if (isPrimitiveValue(secondaryResult)) {
            return secondaryResult;
        }
        return COERCION_NOT_CALLABLE;
    }

    private static Object invokeCoercionMember(final TsjObject objectValue, final Object member, final Object... args) {
        if (member instanceof TsjMethod method) {
            return method.call(objectValue, args);
        }
        if (member instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(objectValue, args);
        }
        if (member instanceof TsjCallable callable) {
            return callable.call(args);
        }
        return COERCION_NOT_CALLABLE;
    }

    private static boolean isPrimitiveForAbstractEquals(final Object value) {
        return isPrimitiveValue(value);
    }

    private static TsjClass createErrorBuiltin() {
        final TsjClass errorClass = new TsjClass("Error", null);
        errorClass.setConstructor((thisObject, args) -> {
            final Object rawMessage = args.length > 0 ? args[0] : "";
            final Object options = args.length > 1 ? args[1] : TsjUndefined.INSTANCE;
            initializeErrorObject(thisObject, "Error", rawMessage, options);
            return null;
        });
        return errorClass;
    }

    private static TsjClass createAggregateErrorBuiltin() {
        final TsjClass aggregateErrorClass = new TsjClass("AggregateError", ERROR_BUILTIN);
        aggregateErrorClass.setConstructor((thisObject, args) -> {
            final Object iterable = firstArg(args);
            final Object rawMessage = secondArg(args);
            final Object options = args.length > 2 ? args[2] : TsjUndefined.INSTANCE;
            ERROR_BUILTIN.invokeConstructor(thisObject, rawMessage, options);
            thisObject.setOwn("name", "AggregateError");
            final List<Object> reasons = new ArrayList<>();
            if (!isNullish(iterable)) {
                appendSpreadValues(reasons, iterable);
            }
            thisObject.setOwn("errors", arrayLiteral(reasons.toArray()));
            return null;
        });
        return aggregateErrorClass;
    }

    private static TsjClass createNativeErrorSubtypeBuiltin(final String name) {
        final TsjClass subtype = new TsjClass(name, ERROR_BUILTIN);
        subtype.setConstructor((thisObject, args) -> {
            final Object rawMessage = args.length > 0 ? args[0] : "";
            final Object options = args.length > 1 ? args[1] : TsjUndefined.INSTANCE;
            ERROR_BUILTIN.invokeConstructor(thisObject, rawMessage, options);
            initializeErrorObject(thisObject, name, rawMessage, options);
            return null;
        });
        return subtype;
    }

    private static void initializeErrorObject(
            final TsjObject thisObject,
            final String name,
            final Object rawMessage,
            final Object options
    ) {
        final String normalizedMessage = isUndefined(rawMessage) ? "" : toDisplayString(rawMessage);
        thisObject.setOwn("name", name);
        thisObject.setOwn("message", normalizedMessage);
        thisObject.setOwn("stack", name + ": " + normalizedMessage);
        applyErrorCauseOption(thisObject, options);
    }

    private static void applyErrorCauseOption(final TsjObject thisObject, final Object options) {
        if (!(options instanceof TsjObject tsjOptions)) {
            return;
        }
        if (tsjOptions.hasOwn("cause")) {
            thisObject.setOwn("cause", tsjOptions.getOwn("cause"));
        }
    }

    private static TsjFunctionObject createObjectBuiltin() {
        final TsjFunctionObject object = new TsjFunctionObject((thisValue, args) -> {
            final Object value = firstArg(args);
            if (isNullish(value)) {
                return new TsjObject(null);
            }
            if (value instanceof TsjObject) {
                return value;
            }
            return objectLiteral("value", value);
        });
        object.setOwn("keys", (TsjMethod) (thisObject, args) -> arrayLiteral(ownKeys(firstArg(args)).toArray()));
        object.setOwn("values", (TsjMethod) (thisObject, args) -> arrayLiteral(ownValues(firstArg(args)).toArray()));
        object.setOwn("entries", (TsjMethod) (thisObject, args) -> {
            final List<Object> entries = new ArrayList<>();
            for (Map.Entry<String, Object> entry : ownEntries(firstArg(args))) {
                entries.add(arrayLiteral(entry.getKey(), entry.getValue()));
            }
            return arrayLiteral(entries.toArray());
        });
        object.setOwn("assign", (TsjMethod) (thisObject, args) -> {
            final Object target = firstArg(args);
            if (!(target instanceof TsjObject tsjTarget)) {
                throw new IllegalArgumentException("Object.assign target must be an object.");
            }
            for (int index = 1; index < args.length; index++) {
                if (isNullish(args[index])) {
                    continue;
                }
                for (Map.Entry<String, Object> entry : ownEntries(args[index])) {
                    tsjTarget.set(entry.getKey(), entry.getValue());
                }
            }
            return target;
        });
        object.setOwn("freeze", (TsjMethod) (thisObject, args) -> firstArg(args));
        object.setOwn("create", (TsjMethod) (thisObject, args) -> {
            final Object prototype = firstArg(args);
            if (prototype == null || isUndefined(prototype)) {
                return new TsjObject(null);
            }
            if (prototype instanceof TsjObject tsjPrototype) {
                return new TsjObject(tsjPrototype);
            }
            throw new IllegalArgumentException("Object.create prototype must be object|null|undefined.");
        });
        object.setOwn("fromEntries", (TsjMethod) (thisObject, args) -> {
            final TsjObject created = new TsjObject(null);
            final List<Object> entryValues = new ArrayList<>();
            appendSpreadValues(entryValues, firstArg(args));
            for (Object entryValue : entryValues) {
                final Object key;
                final Object value;
                if (entryValue instanceof TsjObject tsjEntry) {
                    key = tsjEntry.get("0");
                    value = tsjEntry.get("1");
                } else if (entryValue instanceof Object[] arrayEntry) {
                    key = arrayEntry.length > 0 ? arrayEntry[0] : TsjUndefined.INSTANCE;
                    value = arrayEntry.length > 1 ? arrayEntry[1] : TsjUndefined.INSTANCE;
                } else {
                    throw new IllegalArgumentException("Object.fromEntries expects iterable key/value pairs.");
                }
                final String normalizedKey = key == null ? "null" : toDisplayString(key);
                created.setOwn(normalizedKey, value);
            }
            return created;
        });
        object.setOwn("seal", (TsjMethod) (thisObject, args) -> firstArg(args));
        object.setOwn("getOwnPropertyDescriptor", (TsjMethod) (thisObject, args) -> {
            final Object target = firstArg(args);
            final String key = propertyToKey(secondArg(args));
            if (target instanceof TsjObject tsjObject) {
                if (!tsjObject.hasOwn(key)) {
                    return undefined();
                }
                return descriptorObjectFromValue(tsjObject.getOwn(key));
            }
            if (target instanceof TsjClass tsjClass) {
                if ("prototype".equals(key)) {
                    return objectLiteral("value", tsjClass.prototype());
                }
                final Object member = tsjClass.getStaticMember(key);
                if (isUndefined(member)) {
                    return undefined();
                }
                return descriptorObjectFromValue(member);
            }
            return undefined();
        });
        object.setOwn("defineProperty", (TsjMethod) (thisObject, args) -> {
            final Object target = firstArg(args);
            final Object key = secondArg(args);
            final Object descriptor = args.length > 2 ? args[2] : TsjUndefined.INSTANCE;
            applyDefinePropertyDescriptor(target, key, descriptor);
            return target;
        });
        return object;
    }

    private static TsjObject createReflectBuiltin() {
        final TsjObject reflect = new TsjObject(null);
        reflect.setOwn("ownKeys", (TsjMethod) (thisObject, args) ->
                arrayLiteral(ownKeys(firstArg(args)).toArray()));
        reflect.setOwn("has", (TsjMethod) (thisObject, args) ->
                Boolean.valueOf(inOperator(secondArg(args), firstArg(args))));
        reflect.setOwn("get", (TsjMethod) (thisObject, args) ->
                indexRead(firstArg(args), secondArg(args)));
        reflect.setOwn("set", (TsjMethod) (thisObject, args) -> {
            final Object value = args.length > 2 ? args[2] : TsjUndefined.INSTANCE;
            setPropertyDynamic(firstArg(args), secondArg(args), value);
            return Boolean.TRUE;
        });
        return reflect;
    }

    private static TsjClass createProxyBuiltin() {
        final TsjClass proxy = new TsjClass("Proxy", null);
        proxy.setConstructor((thisObject, args) -> {
            initializeProxyObject(thisObject, firstArg(args), secondArg(args));
            return null;
        });
        proxy.setStaticMember("revocable", (TsjCallableWithThis) (thisValue, args) -> {
            final TsjObject proxyObject = new TsjObject(proxy.prototype());
            initializeProxyObject(proxyObject, firstArg(args), secondArg(args));
            final TsjObject result = new TsjObject(null);
            result.setOwn("proxy", proxyObject);
            result.setOwn("revoke", (TsjCallableWithThis) (revokeThis, revokeArgs) -> {
                proxyObject.setOwn(PROXY_REVOKED_KEY, Boolean.TRUE);
                return TsjUndefined.INSTANCE;
            });
            return result;
        });
        return proxy;
    }

    private static Object descriptorObjectFromValue(final Object value) {
        if (value instanceof TsjAccessorDescriptor descriptor) {
            return objectLiteral("get", descriptor.getter(), "set", descriptor.setter());
        }
        return objectLiteral("value", value);
    }

    private static void applyDefinePropertyDescriptor(
            final Object target,
            final Object key,
            final Object descriptor
    ) {
        final Object getter = descriptorField(descriptor, "get");
        final Object setter = descriptorField(descriptor, "set");
        if (!isUndefined(getter) || !isUndefined(setter)) {
            defineAccessorProperty(target, key, getter, setter);
            return;
        }
        if (descriptorHasOwnField(descriptor, "value")) {
            setPropertyDynamic(target, key, descriptorField(descriptor, "value"));
        }
    }

    private static boolean descriptorHasOwnField(final Object descriptor, final String key) {
        if (descriptor instanceof TsjObject tsjObject) {
            return tsjObject.hasOwn(key);
        }
        if (descriptor instanceof Map<?, ?> mapValue) {
            return mapValue.containsKey(key);
        }
        return false;
    }

    private static Object descriptorField(final Object descriptor, final String key) {
        if (descriptor instanceof TsjObject tsjObject) {
            return tsjObject.get(key);
        }
        if (descriptor instanceof Map<?, ?> mapValue) {
            return mapValue.containsKey(key) ? mapValue.get(key) : TsjUndefined.INSTANCE;
        }
        return TsjUndefined.INSTANCE;
    }

    private static TsjClass createArrayBuiltin() {
        final TsjClass array = new TsjClass("Array", null);
        array.setConstructor((thisObject, args) -> {
            if (args.length == 1 && args[0] instanceof Number numberValue) {
                final int length = Math.max(0, (int) Math.floor(numberValue.doubleValue()));
                thisObject.setOwn("length", Integer.valueOf(length));
                return null;
            }
            for (int index = 0; index < args.length; index++) {
                thisObject.setOwn(Integer.toString(index), args[index]);
            }
            thisObject.setOwn("length", Integer.valueOf(args.length));
            return null;
        });
        array.setStaticMember("from", (TsjCallableWithThis) (thisValue, args) -> {
            final List<Object> values = new ArrayList<>();
            appendSpreadValues(values, firstArg(args));
            return arrayLiteral(values.toArray());
        });
        array.setStaticMember("isArray", (TsjCallableWithThis) (thisValue, args) -> {
            final Object value = firstArg(args);
            return Boolean.valueOf(value instanceof TsjObject tsjObject && isArrayLikeObject(tsjObject));
        });
        return array;
    }

    private static TsjClass createMapBuiltin() {
        final TsjClass map = new TsjClass("Map", null);
        map.setConstructor((thisObject, args) -> {
            final Map<Object, Object> backing = new LinkedHashMap<>();
            thisObject.setOwn(MAP_BACKING_KEY, backing);
            thisObject.setOwn("size", Integer.valueOf(0));
            final Object iterable = firstArg(args);
            if (!isNullish(iterable)) {
                final List<Object> entries = new ArrayList<>();
                appendSpreadValues(entries, iterable);
                for (Object entryValue : entries) {
                    final Object key;
                    final Object value;
                    if (entryValue instanceof TsjObject tsjEntry) {
                        key = tsjEntry.get("0");
                        value = tsjEntry.get("1");
                    } else if (entryValue instanceof Object[] arrayEntry) {
                        key = arrayEntry.length > 0 ? arrayEntry[0] : TsjUndefined.INSTANCE;
                        value = arrayEntry.length > 1 ? arrayEntry[1] : TsjUndefined.INSTANCE;
                    } else {
                        throw new IllegalArgumentException("Map constructor expects iterable key/value entries.");
                    }
                    backing.put(key, value);
                }
                thisObject.setOwn("size", Integer.valueOf(backing.size()));
            }
            return null;
        });
        map.defineMethod("set", (thisObject, args) -> {
            final Map<Object, Object> backing = mapBacking(thisObject);
            backing.put(firstArg(args), secondArg(args));
            thisObject.setOwn("size", Integer.valueOf(backing.size()));
            return thisObject;
        });
        map.defineMethod("get", (thisObject, args) -> mapBacking(thisObject).getOrDefault(firstArg(args), TsjUndefined.INSTANCE));
        map.defineMethod("has", (thisObject, args) -> Boolean.valueOf(mapBacking(thisObject).containsKey(firstArg(args))));
        map.defineMethod("delete", (thisObject, args) -> {
            final Map<Object, Object> backing = mapBacking(thisObject);
            final Object removed = backing.remove(firstArg(args));
            thisObject.setOwn("size", Integer.valueOf(backing.size()));
            return Boolean.valueOf(removed != null);
        });
        map.defineMethod("clear", (thisObject, args) -> {
            final Map<Object, Object> backing = mapBacking(thisObject);
            backing.clear();
            thisObject.setOwn("size", Integer.valueOf(0));
            return TsjUndefined.INSTANCE;
        });
        map.defineMethod("forEach", (thisObject, args) -> {
            final Object callback = firstArg(args);
            final Map<Object, Object> backing = mapBacking(thisObject);
            for (Map.Entry<Object, Object> entry : backing.entrySet()) {
                call(callback, entry.getValue(), entry.getKey(), thisObject);
            }
            return TsjUndefined.INSTANCE;
        });
        return map;
    }

    private static TsjClass createSetBuiltin() {
        final TsjClass set = new TsjClass("Set", null);
        set.setConstructor((thisObject, args) -> {
            thisObject.setOwn(SET_BACKING_KEY, new java.util.LinkedHashSet<>());
            thisObject.setOwn("size", Integer.valueOf(0));
            final Object iterable = firstArg(args);
            if (!isNullish(iterable)) {
                final List<Object> values = new ArrayList<>();
                appendSpreadValues(values, iterable);
                final java.util.Set<Object> backing = setBacking(thisObject);
                backing.addAll(values);
                thisObject.setOwn("size", Integer.valueOf(backing.size()));
            }
            return null;
        });
        set.defineMethod("add", (thisObject, args) -> {
            final java.util.Set<Object> backing = setBacking(thisObject);
            backing.add(firstArg(args));
            thisObject.setOwn("size", Integer.valueOf(backing.size()));
            return thisObject;
        });
        set.defineMethod("has", (thisObject, args) -> Boolean.valueOf(setBacking(thisObject).contains(firstArg(args))));
        set.defineMethod("delete", (thisObject, args) -> {
            final java.util.Set<Object> backing = setBacking(thisObject);
            final boolean removed = backing.remove(firstArg(args));
            thisObject.setOwn("size", Integer.valueOf(backing.size()));
            return Boolean.valueOf(removed);
        });
        set.defineMethod("clear", (thisObject, args) -> {
            final java.util.Set<Object> backing = setBacking(thisObject);
            backing.clear();
            thisObject.setOwn("size", Integer.valueOf(0));
            return TsjUndefined.INSTANCE;
        });
        set.defineMethod("forEach", (thisObject, args) -> {
            final Object callback = firstArg(args);
            for (Object value : setBacking(thisObject)) {
                call(callback, value, value, thisObject);
            }
            return TsjUndefined.INSTANCE;
        });
        return set;
    }

    private static TsjClass createWeakMapBuiltin() {
        final TsjClass weakMap = new TsjClass("WeakMap", null);
        weakMap.setConstructor((thisObject, args) -> {
            thisObject.setOwn(WEAK_MAP_BACKING_KEY, new java.util.WeakHashMap<>());
            return null;
        });
        weakMap.defineMethod("set", (thisObject, args) -> {
            weakMapBacking(thisObject).put(firstArg(args), secondArg(args));
            return thisObject;
        });
        weakMap.defineMethod(
                "get",
                (thisObject, args) -> weakMapBacking(thisObject).getOrDefault(firstArg(args), TsjUndefined.INSTANCE)
        );
        weakMap.defineMethod(
                "has",
                (thisObject, args) -> Boolean.valueOf(weakMapBacking(thisObject).containsKey(firstArg(args)))
        );
        weakMap.defineMethod(
                "delete",
                (thisObject, args) -> Boolean.valueOf(weakMapBacking(thisObject).remove(firstArg(args)) != null)
        );
        return weakMap;
    }

    private static TsjClass createWeakSetBuiltin() {
        final TsjClass weakSet = new TsjClass("WeakSet", null);
        weakSet.setConstructor((thisObject, args) -> {
            weakSetBacking(thisObject);
            return null;
        });
        weakSet.defineMethod("add", (thisObject, args) -> {
            weakSetBacking(thisObject).add(firstArg(args));
            return thisObject;
        });
        weakSet.defineMethod(
                "has",
                (thisObject, args) -> Boolean.valueOf(weakSetBacking(thisObject).contains(firstArg(args)))
        );
        weakSet.defineMethod(
                "delete",
                (thisObject, args) -> Boolean.valueOf(weakSetBacking(thisObject).remove(firstArg(args)))
        );
        return weakSet;
    }

    private static TsjClass createWeakRefBuiltin() {
        final TsjClass weakRef = new TsjClass("WeakRef", null);
        weakRef.setConstructor((thisObject, args) -> {
            thisObject.setOwn(WEAK_REF_TARGET_KEY, firstArg(args));
            return null;
        });
        weakRef.defineMethod("deref", (thisObject, args) -> thisObject.get(WEAK_REF_TARGET_KEY));
        return weakRef;
    }

    private static TsjClass createRegExpBuiltin() {
        final TsjClass regexp = new TsjClass("RegExp", null);
        regexp.setConstructor((thisObject, args) -> {
            final String rawPattern = toDisplayString(firstArg(args));
            final Object rawFlags = secondArg(args);
            final RegexDescriptor descriptor = parseRegexDescriptor(rawPattern, rawFlags);
            thisObject.setOwn(REGEXP_PATTERN_KEY, descriptor.pattern());
            thisObject.setOwn(REGEXP_FLAGS_KEY, descriptor.flags());
            thisObject.setOwn("__tsj_regexp_global", Boolean.valueOf(descriptor.global()));
            thisObject.setOwn(REGEXP_LAST_INDEX_KEY, Integer.valueOf(0));
            return null;
        });
        regexp.defineMethod("test", (thisObject, args) -> {
            final Object result = executeRegexWithState(thisObject, toDisplayString(firstArg(args)));
            return Boolean.valueOf(result != null);
        });
        regexp.defineMethod("exec", (thisObject, args) ->
                executeRegexWithState(thisObject, toDisplayString(firstArg(args))));
        return regexp;
    }

    private static TsjClass createDateBuiltin() {
        final TsjClass date = new TsjClass("Date", null);
        date.setConstructor((thisObject, args) -> {
            final long millis = resolveDateMillis(args);
            thisObject.setOwn(DATE_MILLIS_KEY, Long.valueOf(millis));
            return null;
        });
        date.setStaticMember("now", (TsjCallableWithThis) (thisValue, args) -> Long.valueOf(System.currentTimeMillis()));
        date.defineMethod("getTime", (thisObject, args) -> Long.valueOf(readDateMillis(thisObject)));
        date.defineMethod("getFullYear", (thisObject, args) ->
                Integer.valueOf(dateTimeFromMillis(readDateMillis(thisObject)).getYear()));
        date.defineMethod("getMonth", (thisObject, args) ->
                Integer.valueOf(dateTimeFromMillis(readDateMillis(thisObject)).getMonthValue() - 1));
        date.defineMethod("getDate", (thisObject, args) ->
                Integer.valueOf(dateTimeFromMillis(readDateMillis(thisObject)).getDayOfMonth()));
        date.defineMethod("toISOString", (thisObject, args) ->
                Instant.ofEpochMilli(readDateMillis(thisObject)).toString());
        return date;
    }

    private static TsjObject createMathBuiltin() {
        final TsjObject math = new TsjObject(null);
        math.setOwn("PI", Double.valueOf(Math.PI));
        math.setOwn("E", Double.valueOf(Math.E));
        math.setOwn("floor", (TsjMethod) (thisObject, args) -> narrowNumber(Math.floor(toNumber(firstArg(args)))));
        math.setOwn("ceil", (TsjMethod) (thisObject, args) -> narrowNumber(Math.ceil(toNumber(firstArg(args)))));
        math.setOwn("round", (TsjMethod) (thisObject, args) -> narrowNumber(Math.round(toNumber(firstArg(args)))));
        math.setOwn("abs", (TsjMethod) (thisObject, args) -> narrowNumber(Math.abs(toNumber(firstArg(args)))));
        math.setOwn("pow", (TsjMethod) (thisObject, args) -> narrowNumber(Math.pow(toNumber(firstArg(args)), toNumber(secondArg(args)))));
        math.setOwn("sqrt", (TsjMethod) (thisObject, args) -> narrowNumber(Math.sqrt(toNumber(firstArg(args)))));
        math.setOwn("sign", (TsjMethod) (thisObject, args) -> {
            final double value = toNumber(firstArg(args));
            if (Double.isNaN(value)) {
                return Double.valueOf(Double.NaN);
            }
            if (value == 0d) {
                return Integer.valueOf(0);
            }
            return value > 0d ? Integer.valueOf(1) : Integer.valueOf(-1);
        });
        math.setOwn("trunc", (TsjMethod) (thisObject, args) -> {
            final double value = toNumber(firstArg(args));
            if (!Double.isFinite(value)) {
                return Double.valueOf(value);
            }
            final double truncated = value < 0d ? Math.ceil(value) : Math.floor(value);
            return narrowNumber(truncated);
        });
        math.setOwn("random", (TsjMethod) (thisObject, args) -> Double.valueOf(Math.random()));
        math.setOwn("log", (TsjMethod) (thisObject, args) -> narrowNumber(Math.log(toNumber(firstArg(args)))));
        math.setOwn("log2", (TsjMethod) (thisObject, args) ->
                narrowNumber(Math.log(toNumber(firstArg(args))) / Math.log(2d)));
        math.setOwn("log10", (TsjMethod) (thisObject, args) -> narrowNumber(Math.log10(toNumber(firstArg(args)))));
        math.setOwn("max", (TsjMethod) (thisObject, args) -> {
            if (args.length == 0) {
                return Double.valueOf(Double.NEGATIVE_INFINITY);
            }
            double current = Double.NEGATIVE_INFINITY;
            for (Object arg : args) {
                final double value = toNumber(arg);
                if (Double.isNaN(value)) {
                    return Double.valueOf(Double.NaN);
                }
                current = Math.max(current, value);
            }
            return narrowNumber(current);
        });
        math.setOwn("min", (TsjMethod) (thisObject, args) -> {
            if (args.length == 0) {
                return Double.valueOf(Double.POSITIVE_INFINITY);
            }
            double current = Double.POSITIVE_INFINITY;
            for (Object arg : args) {
                final double value = toNumber(arg);
                if (Double.isNaN(value)) {
                    return Double.valueOf(Double.NaN);
                }
                current = Math.min(current, value);
            }
            return narrowNumber(current);
        });
        return math;
    }

    private static TsjFunctionObject createNumberBuiltin() {
        final TsjFunctionObject number = new TsjFunctionObject(
                (thisValue, args) -> narrowNumber(toNumber(firstArg(args)))
        );
        number.setOwn("isInteger", (TsjMethod) (thisObject, args) -> {
            final Object value = firstArg(args);
            if (!(value instanceof Number numberValue)) {
                return Boolean.FALSE;
            }
            final double numeric = numberValue.doubleValue();
            return Boolean.valueOf(Double.isFinite(numeric) && numeric == Math.rint(numeric));
        });
        number.setOwn("isFinite", (TsjMethod) (thisObject, args) -> {
            final Object value = firstArg(args);
            if (!(value instanceof Number numberValue)) {
                return Boolean.FALSE;
            }
            return Boolean.valueOf(Double.isFinite(numberValue.doubleValue()));
        });
        number.setOwn("isNaN", (TsjMethod) (thisObject, args) -> {
            final Object value = firstArg(args);
            if (!(value instanceof Number numberValue)) {
                return Boolean.FALSE;
            }
            return Boolean.valueOf(Double.isNaN(numberValue.doubleValue()));
        });
        return number;
    }

    private static TsjFunctionObject createBigIntBuiltin() {
        return new TsjFunctionObject((thisValue, args) -> toBigInt(firstArg(args)));
    }

    private static TsjFunctionObject createSymbolBuiltin() {
        final TsjFunctionObject symbol = new TsjFunctionObject((thisValue, args) -> {
            final Object rawDescription = firstArg(args);
            if (isUndefined(rawDescription) || rawDescription == null) {
                return TsjSymbol.create(null);
            }
            return TsjSymbol.create(toDisplayString(rawDescription));
        });
        symbol.setOwn("for", (TsjMethod) (thisObject, args) -> {
            final String key = toDisplayString(firstArg(args));
            return SYMBOL_REGISTRY.computeIfAbsent(key, TsjSymbol::create);
        });
        symbol.setOwn("keyFor", (TsjMethod) (thisObject, args) -> {
            final Object candidate = firstArg(args);
            if (!(candidate instanceof TsjSymbol tsjSymbol)) {
                return undefined();
            }
            for (Map.Entry<String, TsjSymbol> entry : SYMBOL_REGISTRY.entrySet()) {
                if (entry.getValue() == tsjSymbol) {
                    return entry.getKey();
                }
            }
            return undefined();
        });
        symbol.setOwn("iterator", SYMBOL_ITERATOR);
        symbol.setOwn("toPrimitive", SYMBOL_TO_PRIMITIVE);
        return symbol;
    }

    private static Object parseIntBuiltinValue(final Object... args) {
        final String input = toDisplayString(firstArg(args)).trim();
        if (input.isEmpty()) {
            return NAN_VALUE;
        }

        int radix = 0;
        final Object rawRadix = args.length > 1 ? args[1] : TsjUndefined.INSTANCE;
        if (!isUndefined(rawRadix) && rawRadix != null) {
            final double radixValue = toNumber(rawRadix);
            if (!Double.isFinite(radixValue)) {
                return NAN_VALUE;
            }
            radix = (int) Math.floor(radixValue);
            if (radix != 0 && (radix < 2 || radix > 36)) {
                return NAN_VALUE;
            }
        }

        int cursor = 0;
        int sign = 1;
        if (cursor < input.length()) {
            final char signChar = input.charAt(cursor);
            if (signChar == '+' || signChar == '-') {
                sign = signChar == '-' ? -1 : 1;
                cursor++;
            }
        }

        if (radix == 0) {
            if (cursor + 1 < input.length()
                    && input.charAt(cursor) == '0'
                    && (input.charAt(cursor + 1) == 'x' || input.charAt(cursor + 1) == 'X')) {
                radix = 16;
                cursor += 2;
            } else {
                radix = 10;
            }
        } else if (radix == 16
                && cursor + 1 < input.length()
                && input.charAt(cursor) == '0'
                && (input.charAt(cursor + 1) == 'x' || input.charAt(cursor + 1) == 'X')) {
            cursor += 2;
        }

        final int digitStart = cursor;
        while (cursor < input.length() && Character.digit(input.charAt(cursor), radix) >= 0) {
            cursor++;
        }
        if (cursor == digitStart) {
            return NAN_VALUE;
        }

        try {
            final long parsed = Long.parseLong(input.substring(digitStart, cursor), radix);
            return narrowNumber(sign * (double) parsed);
        } catch (final NumberFormatException exception) {
            return NAN_VALUE;
        }
    }

    private static Object parseFloatBuiltinValue(final Object... args) {
        final String input = toDisplayString(firstArg(args)).trim();
        if (input.isEmpty()) {
            return NAN_VALUE;
        }
        if (input.startsWith("Infinity") || input.startsWith("+Infinity")) {
            return INFINITY_VALUE;
        }
        if (input.startsWith("-Infinity")) {
            return Double.valueOf(Double.NEGATIVE_INFINITY);
        }

        int cursor = 0;
        if (cursor < input.length() && (input.charAt(cursor) == '+' || input.charAt(cursor) == '-')) {
            cursor++;
        }
        final int integerStart = cursor;
        while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) {
            cursor++;
        }
        boolean hasDigits = cursor > integerStart;
        if (cursor < input.length() && input.charAt(cursor) == '.') {
            cursor++;
            final int fractionalStart = cursor;
            while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) {
                cursor++;
            }
            hasDigits = hasDigits || cursor > fractionalStart;
        }
        if (!hasDigits) {
            return NAN_VALUE;
        }

        if (cursor < input.length() && (input.charAt(cursor) == 'e' || input.charAt(cursor) == 'E')) {
            final int exponentMark = cursor;
            cursor++;
            if (cursor < input.length() && (input.charAt(cursor) == '+' || input.charAt(cursor) == '-')) {
                cursor++;
            }
            final int exponentStart = cursor;
            while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) {
                cursor++;
            }
            if (cursor == exponentStart) {
                cursor = exponentMark;
            }
        }

        try {
            return narrowNumber(Double.parseDouble(input.substring(0, cursor)));
        } catch (final NumberFormatException exception) {
            return NAN_VALUE;
        }
    }

    private static Object firstArg(final Object[] args) {
        return args.length > 0 ? args[0] : TsjUndefined.INSTANCE;
    }

    private static Object secondArg(final Object[] args) {
        return args.length > 1 ? args[1] : TsjUndefined.INSTANCE;
    }

    private static TsjObject createPromiseBuiltin() {
        final TsjObject promise = new TsjObject(null);
        promise.setOwn(
                "resolve",
                (TsjMethod) (thisObject, args) -> TsjPromise.resolved(args.length > 0 ? args[0] : TsjUndefined.INSTANCE)
        );
        promise.setOwn(
                "reject",
                (TsjMethod) (thisObject, args) -> TsjPromise.rejected(args.length > 0 ? args[0] : TsjUndefined.INSTANCE)
        );
        promise.setOwn(
                "all",
                (TsjMethod) (thisObject, args) -> TsjPromise.all(args.length > 0 ? args[0] : TsjUndefined.INSTANCE)
        );
        promise.setOwn(
                "race",
                (TsjMethod) (thisObject, args) -> TsjPromise.race(args.length > 0 ? args[0] : TsjUndefined.INSTANCE)
        );
        promise.setOwn(
                "allSettled",
                (TsjMethod) (thisObject, args) -> TsjPromise.allSettled(args.length > 0 ? args[0] : TsjUndefined.INSTANCE)
        );
        promise.setOwn(
                "any",
                (TsjMethod) (thisObject, args) -> TsjPromise.any(args.length > 0 ? args[0] : TsjUndefined.INSTANCE)
        );
        return promise;
    }

    private static TsjObject createJsonBuiltin() {
        final TsjObject json = new TsjObject(null);
        json.setOwn(
                "stringify",
                (TsjMethod) (thisObject, args) -> {
                    final Object value = args.length > 0 ? args[0] : TsjUndefined.INSTANCE;
                    final Object replacer = args.length > 1 ? args[1] : TsjUndefined.INSTANCE;
                    final String serialized = jsonStringify(value, replacer, "");
                    return serialized == null ? TsjUndefined.INSTANCE : serialized;
                }
        );
        json.setOwn(
                "parse",
                (TsjMethod) (thisObject, args) -> {
                    final String source = toDisplayString(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                    final JsonCursor cursor = new JsonCursor(source);
                    final Object parsed = parseJsonValue(cursor);
                    cursor.skipWhitespace();
                    if (!cursor.atEnd()) {
                        throw new IllegalArgumentException("JSON.parse encountered trailing characters.");
                    }
                    return decodeParsedJsonValue(parsed);
                }
        );
        return json;
    }

    private static String jsonStringify(final Object value, final Object replacer, final String key) {
        final Object replaced = applyJsonReplacer(replacer, key, value);
        return jsonStringifyValue(replaced, replacer);
    }

    private static Object applyJsonReplacer(final Object replacer, final String key, final Object value) {
        if (isUndefined(replacer) || replacer == null) {
            return value;
        }
        if (!(replacer instanceof TsjCallable
                || replacer instanceof TsjCallableWithThis
                || replacer instanceof TsjMethod)) {
            return value;
        }
        return call(replacer, key, value);
    }

    private static String jsonStringifyValue(final Object value, final Object replacer) {
        if (isUndefined(value)) {
            return null;
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return "\"" + escapeJsonString(stringValue) + "\"";
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue.booleanValue() ? "true" : "false";
        }
        if (value instanceof Number numberValue) {
            final double numeric = numberValue.doubleValue();
            if (Double.isNaN(numeric) || Double.isInfinite(numeric)) {
                return "null";
            }
            return toDisplayString(narrowNumber(numeric));
        }
        if (value instanceof TsjObject tsjObject) {
            if (isArrayLikeObject(tsjObject)) {
                final int length = arrayLikeLength(tsjObject);
                final StringBuilder builder = new StringBuilder();
                builder.append("[");
                for (int index = 0; index < length; index++) {
                    if (index > 0) {
                        builder.append(",");
                    }
                    final Object itemValue = applyJsonReplacer(
                            replacer,
                            Integer.toString(index),
                            tsjObject.get(Integer.toString(index))
                    );
                    final String serialized = jsonStringifyValue(itemValue, replacer);
                    builder.append(serialized == null ? "null" : serialized);
                }
                builder.append("]");
                return builder.toString();
            }
            final StringBuilder builder = new StringBuilder();
            builder.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : tsjObject.ownPropertiesView().entrySet()) {
                final Object memberValue = applyJsonReplacer(replacer, entry.getKey(), entry.getValue());
                final String serialized = jsonStringifyValue(memberValue, replacer);
                if (serialized == null) {
                    continue;
                }
                if (!first) {
                    builder.append(",");
                }
                builder.append("\"")
                        .append(escapeJsonString(entry.getKey()))
                        .append("\":")
                        .append(serialized);
                first = false;
            }
            builder.append("}");
            return builder.toString();
        }
        if (value instanceof Map<?, ?> mapValue) {
            final TsjObject object = new TsjObject(null);
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                final String key = entry.getKey() == null ? "null" : entry.getKey().toString();
                object.setOwn(key, entry.getValue());
            }
            return jsonStringifyValue(object, replacer);
        }
        if (value instanceof Iterable<?> iterableValue) {
            final List<Object> list = new ArrayList<>();
            for (Object item : iterableValue) {
                list.add(item);
            }
            final TsjObject array = new TsjObject(null);
            for (int index = 0; index < list.size(); index++) {
                array.setOwn(Integer.toString(index), list.get(index));
            }
            array.setOwn("length", Integer.valueOf(list.size()));
            return jsonStringifyValue(array, replacer);
        }
        return "\"" + escapeJsonString(toDisplayString(value)) + "\"";
    }

    private static String escapeJsonString(final String value) {
        final StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        final String hex = Integer.toHexString(current);
                        builder.append("\\u");
                        for (int padding = hex.length(); padding < 4; padding++) {
                            builder.append('0');
                        }
                        builder.append(hex);
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static Object parseJsonValue(final JsonCursor cursor) {
        cursor.skipWhitespace();
        if (cursor.atEnd()) {
            throw new IllegalArgumentException("JSON.parse expected a value.");
        }
        final char current = cursor.peek();
        if (current == '"') {
            return parseJsonString(cursor);
        }
        if (current == '{') {
            return parseJsonObject(cursor);
        }
        if (current == '[') {
            return parseJsonArray(cursor);
        }
        if (current == 't') {
            cursor.expectKeyword("true");
            return Boolean.TRUE;
        }
        if (current == 'f') {
            cursor.expectKeyword("false");
            return Boolean.FALSE;
        }
        if (current == 'n') {
            cursor.expectKeyword("null");
            return null;
        }
        return parseJsonNumber(cursor);
    }

    private static String parseJsonString(final JsonCursor cursor) {
        cursor.expect('"');
        final StringBuilder builder = new StringBuilder();
        while (!cursor.atEnd()) {
            final char current = cursor.consume();
            if (current == '"') {
                return builder.toString();
            }
            if (current != '\\') {
                builder.append(current);
                continue;
            }
            if (cursor.atEnd()) {
                throw new IllegalArgumentException("JSON.parse encountered unterminated escape sequence.");
            }
            final char escape = cursor.consume();
            switch (escape) {
                case '"', '\\', '/' -> builder.append(escape);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> builder.append((char) Integer.parseInt(cursor.consumeHex4(), 16));
                default -> throw new IllegalArgumentException("JSON.parse encountered invalid escape sequence.");
            }
        }
        throw new IllegalArgumentException("JSON.parse encountered unterminated string.");
    }

    private static Map<String, Object> parseJsonObject(final JsonCursor cursor) {
        cursor.expect('{');
        cursor.skipWhitespace();
        final Map<String, Object> values = new LinkedHashMap<>();
        if (cursor.tryConsume('}')) {
            return values;
        }
        while (true) {
            cursor.skipWhitespace();
            final String key = parseJsonString(cursor);
            cursor.skipWhitespace();
            cursor.expect(':');
            final Object value = parseJsonValue(cursor);
            values.put(key, value);
            cursor.skipWhitespace();
            if (cursor.tryConsume('}')) {
                return values;
            }
            cursor.expect(',');
        }
    }

    private static List<Object> parseJsonArray(final JsonCursor cursor) {
        cursor.expect('[');
        cursor.skipWhitespace();
        final List<Object> values = new ArrayList<>();
        if (cursor.tryConsume(']')) {
            return values;
        }
        while (true) {
            values.add(parseJsonValue(cursor));
            cursor.skipWhitespace();
            if (cursor.tryConsume(']')) {
                return values;
            }
            cursor.expect(',');
        }
    }

    private static Number parseJsonNumber(final JsonCursor cursor) {
        final int start = cursor.index();
        cursor.tryConsume('-');
        cursor.consumeDigits();
        if (cursor.tryConsume('.')) {
            cursor.consumeDigits();
        }
        if (cursor.tryConsume('e') || cursor.tryConsume('E')) {
            cursor.tryConsume('+');
            cursor.tryConsume('-');
            cursor.consumeDigits();
        }
        final String token = cursor.slice(start, cursor.index());
        if (token.isEmpty() || "-".equals(token) || ".".equals(token) || "-.".equals(token)) {
            throw new IllegalArgumentException("JSON.parse encountered invalid number token.");
        }
        final double parsed = Double.parseDouble(token);
        return (Number) narrowNumber(parsed);
    }

    private static Object decodeParsedJsonValue(final Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number numberValue) {
            return narrowNumber(numberValue.doubleValue());
        }
        if (value instanceof List<?> listValue) {
            final TsjObject array = new TsjObject(null);
            for (int index = 0; index < listValue.size(); index++) {
                array.setOwn(Integer.toString(index), decodeParsedJsonValue(listValue.get(index)));
            }
            array.setOwn("length", Integer.valueOf(listValue.size()));
            return array;
        }
        if (value instanceof Map<?, ?> mapValue) {
            final TsjObject object = new TsjObject(null);
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                final String key = entry.getKey() == null ? "null" : entry.getKey().toString();
                object.setOwn(key, decodeParsedJsonValue(entry.getValue()));
            }
            return object;
        }
        return value;
    }

    private static final class JsonCursor {
        private final String source;
        private int index;

        private JsonCursor(final String source) {
            this.source = Objects.requireNonNull(source, "source");
            this.index = 0;
        }

        private boolean atEnd() {
            return index >= source.length();
        }

        private int index() {
            return index;
        }

        private char peek() {
            return source.charAt(index);
        }

        private char consume() {
            final char current = source.charAt(index);
            index++;
            return current;
        }

        private boolean tryConsume(final char expected) {
            if (!atEnd() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(final char expected) {
            if (atEnd() || source.charAt(index) != expected) {
                throw new IllegalArgumentException("JSON.parse expected `" + expected + "`.");
            }
            index++;
        }

        private void expectKeyword(final String keyword) {
            if (!source.startsWith(keyword, index)) {
                throw new IllegalArgumentException("JSON.parse expected `" + keyword + "`.");
            }
            index += keyword.length();
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                final char current = source.charAt(index);
                if (current == ' ' || current == '\n' || current == '\r' || current == '\t') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private void consumeDigits() {
            int consumed = 0;
            while (!atEnd()) {
                final char current = source.charAt(index);
                if (current >= '0' && current <= '9') {
                    index++;
                    consumed++;
                } else {
                    break;
                }
            }
            if (consumed == 0) {
                throw new IllegalArgumentException("JSON.parse expected digits.");
            }
        }

        private String consumeHex4() {
            if (index + 4 > source.length()) {
                throw new IllegalArgumentException("JSON.parse expected four hex digits.");
            }
            final String value = source.substring(index, index + 4);
            for (int offset = 0; offset < 4; offset++) {
                final char current = value.charAt(offset);
                final boolean isDigit = current >= '0' && current <= '9';
                final boolean isLowerHex = current >= 'a' && current <= 'f';
                final boolean isUpperHex = current >= 'A' && current <= 'F';
                if (!isDigit && !isLowerHex && !isUpperHex) {
                    throw new IllegalArgumentException("JSON.parse expected four hex digits.");
                }
            }
            index += 4;
            return value;
        }

        private String slice(final int start, final int end) {
            return source.substring(start, end);
        }
    }

    private record ResolvedProperty(Object value) {
    }

    private record TsjAccessorDescriptor(Object getter, Object setter) {
    }

    private record RegexDescriptor(String pattern, String flags, boolean global) {
    }

    static List<Object> asArrayLikeList(final Object iterable, final String combinatorName) {
        if (iterable instanceof String stringValue) {
            return asStringIterableList(stringValue);
        }
        if (!(iterable instanceof TsjObject objectValue)) {
            throw new IllegalArgumentException(
                    "Promise." + combinatorName + " expects an iterable or array-like input in TSJ-24."
            );
        }

        final Object iteratorMember = resolveIteratorMember(objectValue);
        if (!isUndefined(iteratorMember)) {
            return asIteratorList(objectValue, iteratorMember, "Promise." + combinatorName);
        }

        return asArrayLikeFallbackList(objectValue, combinatorName);
    }

    private static List<Object> asStringIterableList(final String stringValue) {
        final List<Object> values = new ArrayList<>();
        for (int index = 0; index < stringValue.length(); ) {
            final int codePoint = stringValue.codePointAt(index);
            values.add(new String(Character.toChars(codePoint)));
            index += Character.charCount(codePoint);
        }
        return values;
    }

    private static List<Object> asArrayLikeFallbackList(final TsjObject arrayLike, final String combinatorName) {
        final double lengthNumber = toNumber(arrayLike.get("length"));
        if (Double.isNaN(lengthNumber) || lengthNumber < 0 || lengthNumber != Math.rint(lengthNumber)) {
            throw new IllegalArgumentException("Promise." + combinatorName + " requires a finite non-negative length.");
        }
        if (lengthNumber > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Promise." + combinatorName + " input is too large.");
        }
        final int length = (int) lengthNumber;
        final List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(arrayLike.get(Integer.toString(index)));
        }
        return values;
    }

    private static Object resolveIteratorMember(final TsjObject objectValue) {
        final Object symbolKeyedIterator = objectValue.get(SYMBOL_ITERATOR.propertyKey());
        if (!isUndefined(symbolKeyedIterator)) {
            return symbolKeyedIterator;
        }
        final Object atAtIterator = objectValue.get("@@iterator");
        if (!isUndefined(atAtIterator)) {
            return atAtIterator;
        }
        final Object symbolIterator = objectValue.get("Symbol.iterator");
        if (!isUndefined(symbolIterator)) {
            return symbolIterator;
        }
        final Object iterator = objectValue.get("iterator");
        if (!isUndefined(iterator)) {
            return iterator;
        }
        return TsjUndefined.INSTANCE;
    }

    private static List<Object> asIteratorList(
            final TsjObject iterableValue,
            final Object iteratorMember,
            final String operationLabel
    ) {
        final Object iteratorObjectValue = invokeCallableWithReceiver(iterableValue, iteratorMember);
        if (!(iteratorObjectValue instanceof TsjObject iteratorObject)) {
            throw new IllegalArgumentException(
                    operationLabel + " iterator() must return an object."
            );
        }

        final List<Object> values = new ArrayList<>();
        try {
            while (true) {
                final Object nextMember = iteratorObject.get("next");
                if (isUndefined(nextMember)) {
                    throw new IllegalArgumentException(
                            operationLabel + " iterator object must expose callable next()."
                    );
                }
                final Object iterationResultValue = invokeCallableWithReceiver(iteratorObject, nextMember);
                if (!(iterationResultValue instanceof TsjObject iterationResult)) {
                    throw new IllegalArgumentException(
                            operationLabel + " iterator next() must return an object."
                    );
                }
                if (truthy(iterationResult.get("done"))) {
                    return values;
                }
                values.add(iterationResult.get("value"));
            }
        } catch (final RuntimeException runtimeException) {
            closeIteratorAfterAbruptCompletion(iteratorObject);
            throw runtimeException;
        }
    }

    private static void closeIteratorAfterAbruptCompletion(final TsjObject iteratorObject) {
        final Object returnMember;
        try {
            returnMember = iteratorObject.get("return");
        } catch (final RuntimeException ignored) {
            return;
        }
        if (isUndefined(returnMember)) {
            return;
        }
        try {
            invokeCallableWithReceiver(iteratorObject, returnMember);
        } catch (final RuntimeException ignored) {
            // Preserve the original abrupt completion.
        }
    }

    private static Object invokeCallableWithReceiver(final TsjObject receiver, final Object callableValue) {
        if (callableValue instanceof TsjMethod method) {
            return method.call(receiver);
        }
        if (callableValue instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(receiver);
        }
        if (callableValue instanceof TsjCallable callable) {
            return callable.call();
        }
        throw new IllegalArgumentException("Value is not callable: " + toDisplayString(callableValue));
    }

    private static void defaultUnhandledRejectionReporter(final Object reason) {
        System.err.println("TSJ-UNHANDLED-REJECTION: " + toDisplayString(reason));
    }
}
