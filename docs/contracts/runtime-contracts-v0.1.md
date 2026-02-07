# Runtime Contracts v0.1

## Scope
This document defines normative runtime interfaces for TSJ-0:
1. `TsValue`
2. `TsObject`
3. Module table/runtime linking contracts
4. Scheduler and microtask contracts

These contracts are implementation-agnostic and versioned. JVM classes may evolve internally but must preserve these behaviors.

## Versioning Rules
1. Minor version (`v0.x`) may add methods/fields but cannot change existing semantics.
2. Major version (`v1.0+`) may change semantics only with migration notes.
3. Runtime exports must expose `RuntimeVersion` for compatibility checks.

## Shared Concepts

### Value Tags
MVP runtime tags:
1. `UNDEFINED`
2. `NULL`
3. `BOOLEAN`
4. `INT32`
5. `DOUBLE`
6. `STRING`
7. `OBJECT`
8. `FUNCTION`

Deferred tags:
1. `SYMBOL`
2. `BIGINT`

### Error Surface
Runtime failures use typed error codes:
1. `TSJRT_MODULE_CYCLE_UNSAFE`
2. `TSJRT_INVALID_COERCION`
3. `TSJRT_INVALID_THIS_BINDING`
4. `TSJRT_UNSUPPORTED_FEATURE`

Compiler diagnostics should reference these where relevant.

## Contract: TsValue

### Interface (Normative)
```java
public interface TsValue {
    TsTag tag();

    boolean isUndefined();
    boolean isNull();
    boolean isBoolean();
    boolean isInt32();
    boolean isDouble();
    boolean isNumber();
    boolean isString();
    boolean isObject();
    boolean isFunction();

    boolean asBoolean();
    int asInt32();
    double asDouble();
    String asString();
    TsObject asObject();

    boolean strictEquals(TsValue other);    // JS === semantics for supported tags
    boolean abstractEquals(TsValue other);  // JS == semantics for supported tags
}
```

### Semantics
1. `INT32` and `DOUBLE` are both JS `number` domain values.
2. `strictEquals` must treat `NaN !== NaN` and `+0 === -0`.
3. `abstractEquals` must match JS coercion semantics for supported tags.
4. `as*` methods throw a typed runtime error on invalid cast.

### Performance Contract
1. Compiler may keep `int32`/`double` unboxed in MIR/JIR lanes.
2. Boxing to `TsValue` is required only at dynamic boundaries:
   - Property lookup/set.
   - Generic function invocation.
   - Java interop boundary.

## Contract: TsObject

### Interface (Normative)
```java
public interface TsObject {
    TsObject prototype(); // null for root object
    void setPrototype(TsObject proto);

    boolean hasOwn(String key);
    TsValue getOwn(String key); // returns UNDEFINED when missing
    void setOwn(String key, TsValue value);
    boolean deleteOwn(String key);

    TsValue get(String key); // own + prototype lookup
    void set(String key, TsValue value); // own write for MVP

    long shapeToken(); // cache invalidation token for inline caches
}
```

### Semantics
1. `get(key)` walks own-properties first, then prototype chain.
2. Missing property reads return `UNDEFINED`.
3. Deleting non-existent property returns `true` (JS-compatible behavior for normal objects).
4. `setPrototype` must reject cycles with typed runtime error.

### Cache Compatibility
1. `shapeToken()` must change whenever own-property layout changes.
2. Generated inline caches rely on `(objectClass, shapeToken, key)` guard triples.

## Contract: Module Table

### States
1. `UNINITIALIZED`
2. `INITIALIZING`
3. `INITIALIZED`
4. `FAILED`

### Interface (Normative)
```java
public interface ModuleRegistry {
    void register(ModuleDescriptor descriptor);
    void initializeAll();
    void initialize(String moduleId);

    TsValue readBinding(String moduleId, String exportName);
    void writeBinding(String moduleId, String exportName, TsValue value);

    ModuleState stateOf(String moduleId);
}
```

### Semantics
1. Module graph is static at compile time for MVP.
2. Initialization order is deterministic from dependency graph plus source order tie-break.
3. Live bindings are required:
   - Importers observe updated exporter value after mutation.
4. Circular dependencies are supported only for safe read-after-init patterns; unsafe reads must throw typed runtime error.

## Contract: Scheduler and Microtasks

### Interface (Normative)
```java
public interface TsScheduler {
    void enqueueMicrotask(Runnable job);
    void enqueueMacrotask(Runnable job); // optional in MVP runtime core, required in host adapters
    void runMicrotasks();                // drains microtask queue fully
    void runEventLoopStep();             // one macro task + post-step microtask drain
}
```

### Semantics
1. Promise reactions are always enqueued as microtasks.
2. Microtasks must run to completion before next macrotask step.
3. Runtime semantics are single-threaded from TS perspective:
   - Host threads may enqueue work, but scheduler execution is serialized.
4. Exceptions in microtasks follow Promise rejection handling rules, not host crash defaults.

## Compatibility Matrix Hook
Each compatibility level must define:
1. Supported value tags.
2. Supported object features (property descriptors, symbols, proxies).
3. Supported module features.
4. Supported scheduler semantics.

`docs/architecture-decisions.md` and `docs/story-architecture-map.md` are authoritative for rollout order.

