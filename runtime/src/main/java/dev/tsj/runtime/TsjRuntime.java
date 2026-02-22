package dev.tsj.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runtime helpers used by TSJ-generated JVM classes in TSJ-9 subset.
 */
public final class TsjRuntime {
    private static final Deque<Runnable> MICROTASK_QUEUE = new ArrayDeque<>();
    private static final TsjObject PROMISE_BUILTIN = createPromiseBuiltin();
    private static final Object COERCION_NOT_CALLABLE = new Object();
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

    public static Object javaStaticMethod(final String className, final String methodName) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        return javaBinding(className, methodName);
    }

    public static Object javaBinding(final String className, final String bindingName) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(bindingName, "bindingName");
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
            return tsjObject.get(key);
        }
        if (target instanceof TsjClass tsjClass) {
            return tsjClass.getStaticMember(key);
        }
        throw new IllegalArgumentException("Cannot get property `" + key + "` from " + toDisplayString(target));
    }

    public static Object getPropertyCached(
            final TsjPropertyAccessCache cache,
            final Object target,
            final String key
    ) {
        if (target instanceof TsjObject tsjObject) {
            return cache.read(tsjObject, key);
        }
        if (target instanceof TsjClass) {
            return getProperty(target, key);
        }
        throw new IllegalArgumentException("Cannot get property `" + key + "` from " + toDisplayString(target));
    }

    public static Object setProperty(final Object target, final String key, final Object value) {
        if (target instanceof TsjObject tsjObject) {
            tsjObject.set(key, value);
            return value;
        }
        if (target instanceof TsjClass tsjClass) {
            tsjClass.setStaticMember(key, value);
            return value;
        }
        throw new IllegalArgumentException("Cannot set property `" + key + "` on " + toDisplayString(target));
    }

    public static Object setPropertyDynamic(final Object target, final Object key, final Object value) {
        final String normalizedKey = key == null ? "null" : toDisplayString(key);
        return setProperty(target, normalizedKey, value);
    }

    public static boolean deleteProperty(final Object target, final String key) {
        if (target instanceof TsjObject tsjObject) {
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

    public static Object optionalMemberAccess(final Object receiver, final String key) {
        if (isNullish(receiver)) {
            return undefined();
        }
        return getProperty(receiver, key);
    }

    public static Object optionalCall(final Object callee, final Supplier<Object[]> argsSupplier) {
        Objects.requireNonNull(argsSupplier, "argsSupplier");
        if (isNullish(callee)) {
            return undefined();
        }
        final Object[] args = argsSupplier.get();
        return call(callee, args == null ? new Object[0] : args);
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

    public static Object indexRead(final Object target, final Object index) {
        final String key = index == null ? "null" : toDisplayString(index);
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
            final Object member = tsjObject.get(methodName);
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
        if (target != null && target != TsjUndefined.INSTANCE) {
            return TsjJavaInterop.invokeInstanceMember(target, methodName, args);
        }
        throw new IllegalArgumentException("Cannot invoke member `" + methodName + "` on " + toDisplayString(target));
    }

    public static Object add(final Object left, final Object right) {
        if (left instanceof String || right instanceof String) {
            return toDisplayString(left) + toDisplayString(right);
        }
        return narrowNumber(toNumber(left) + toNumber(right));
    }

    public static Object subtract(final Object left, final Object right) {
        return narrowNumber(toNumber(left) - toNumber(right));
    }

    public static Object multiply(final Object left, final Object right) {
        return narrowNumber(toNumber(left) * toNumber(right));
    }

    public static Object divide(final Object left, final Object right) {
        return Double.valueOf(toNumber(left) / toNumber(right));
    }

    public static Object modulo(final Object left, final Object right) {
        return Double.valueOf(toNumber(left) % toNumber(right));
    }

    public static Object negate(final Object value) {
        return narrowNumber(-toNumber(value));
    }

    public static boolean lessThan(final Object left, final Object right) {
        return toNumber(left) < toNumber(right);
    }

    public static boolean lessThanOrEqual(final Object left, final Object right) {
        return toNumber(left) <= toNumber(right);
    }

    public static boolean greaterThan(final Object left, final Object right) {
        return toNumber(left) > toNumber(right);
    }

    public static boolean greaterThanOrEqual(final Object left, final Object right) {
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
        return value.toString();
    }

    public static double toNumber(final Object value) {
        if (isUndefined(value)) {
            return Double.NaN;
        }
        if (value == null) {
            return 0.0d;
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
        return Double.NaN;
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
        if (segment instanceof TsjObject tsjObject) {
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
        final Object valueOfResult = invokeCoercionMember(objectValue, objectValue.get("valueOf"));
        if (isPrimitiveForAbstractEquals(valueOfResult)) {
            return valueOfResult;
        }

        final Object toStringResult = invokeCoercionMember(objectValue, objectValue.get("toString"));
        if (isPrimitiveForAbstractEquals(toStringResult)) {
            return toStringResult;
        }

        // Object.prototype.toString fallback for plain objects in TSJ subset.
        return "[object Object]";
    }

    private static Object invokeCoercionMember(final TsjObject objectValue, final Object member) {
        if (member instanceof TsjMethod method) {
            return method.call(objectValue);
        }
        if (member instanceof TsjCallableWithThis callableWithThis) {
            return callableWithThis.callWithThis(objectValue);
        }
        if (member instanceof TsjCallable callable) {
            return callable.call();
        }
        return COERCION_NOT_CALLABLE;
    }

    private static boolean isPrimitiveForAbstractEquals(final Object value) {
        if (value == COERCION_NOT_CALLABLE) {
            return false;
        }
        return value == null
                || isUndefined(value)
                || value instanceof Number
                || value instanceof String
                || value instanceof Boolean;
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
            return asIteratorList(objectValue, iteratorMember, combinatorName);
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
            final String combinatorName
    ) {
        final Object iteratorObjectValue = invokeCallableWithReceiver(iterableValue, iteratorMember);
        if (!(iteratorObjectValue instanceof TsjObject iteratorObject)) {
            throw new IllegalArgumentException(
                    "Promise." + combinatorName + " iterator() must return an object."
            );
        }

        final List<Object> values = new ArrayList<>();
        try {
            while (true) {
                final Object nextMember = iteratorObject.get("next");
                if (isUndefined(nextMember)) {
                    throw new IllegalArgumentException(
                            "Promise." + combinatorName + " iterator object must expose callable next()."
                    );
                }
                final Object iterationResultValue = invokeCallableWithReceiver(iteratorObject, nextMember);
                if (!(iterationResultValue instanceof TsjObject iterationResult)) {
                    throw new IllegalArgumentException(
                            "Promise." + combinatorName + " iterator next() must return an object."
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
