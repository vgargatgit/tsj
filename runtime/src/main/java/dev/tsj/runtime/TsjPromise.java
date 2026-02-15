package dev.tsj.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Promise runtime model for TSJ-13 bootstrap promise chaining.
 */
public final class TsjPromise extends TsjObject {
    private PromiseState state;
    private Object settledValue;
    private final List<Reaction> reactions;
    private boolean handled;
    private boolean unhandledRejectionCheckScheduled;
    private boolean unhandledRejectionReported;

    public TsjPromise() {
        super(null);
        this.state = PromiseState.PENDING;
        this.settledValue = TsjUndefined.INSTANCE;
        this.reactions = new ArrayList<>();
        this.handled = false;
        this.unhandledRejectionCheckScheduled = false;
        this.unhandledRejectionReported = false;
        setOwn("then", (TsjMethod) (thisObject, args) -> ((TsjPromise) thisObject).then(args));
        setOwn(
                "catch",
                (TsjMethod) (thisObject, args) -> ((TsjPromise) thisObject).then(
                        TsjUndefined.INSTANCE,
                        args.length > 0 ? args[0] : TsjUndefined.INSTANCE
                )
        );
        setOwn(
                "finally",
                (TsjMethod) (thisObject, args) -> ((TsjPromise) thisObject).finallyPromise(
                        args.length > 0 ? args[0] : TsjUndefined.INSTANCE
                )
        );
    }

    public static TsjPromise resolved(final Object value) {
        if (value instanceof TsjPromise promise) {
            return promise;
        }
        final TsjPromise promise = new TsjPromise();
        promise.resolveInternal(value);
        return promise;
    }

    public static TsjPromise rejected(final Object reason) {
        final TsjPromise promise = new TsjPromise();
        promise.rejectInternal(reason);
        return promise;
    }

    public static TsjPromise all(final Object iterable) {
        final List<Object> values;
        try {
            values = TsjRuntime.asArrayLikeList(iterable, "all");
        } catch (final RuntimeException runtimeException) {
            return rejected(TsjRuntime.normalizeThrown(runtimeException));
        }
        if (values.isEmpty()) {
            return resolved(TsjRuntime.arrayLiteral());
        }
        final TsjPromise result = new TsjPromise();
        final Object[] resolvedValues = new Object[values.size()];
        final int[] remaining = new int[]{values.size()};
        final boolean[] settled = new boolean[]{false};
        for (int index = 0; index < values.size(); index++) {
            final int slot = index;
            resolved(values.get(index)).then(
                    (TsjCallable) args -> {
                        if (settled[0]) {
                            return TsjUndefined.INSTANCE;
                        }
                        resolvedValues[slot] = args.length > 0 ? args[0] : TsjUndefined.INSTANCE;
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            settled[0] = true;
                            result.resolveInternal(TsjRuntime.arrayLiteral(resolvedValues));
                        }
                        return TsjUndefined.INSTANCE;
                    },
                    (TsjCallable) args -> {
                        if (settled[0]) {
                            return TsjUndefined.INSTANCE;
                        }
                        settled[0] = true;
                        result.rejectInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                        return TsjUndefined.INSTANCE;
                    }
            );
        }
        return result;
    }

    public static TsjPromise race(final Object iterable) {
        final List<Object> values;
        try {
            values = TsjRuntime.asArrayLikeList(iterable, "race");
        } catch (final RuntimeException runtimeException) {
            return rejected(TsjRuntime.normalizeThrown(runtimeException));
        }
        final TsjPromise result = new TsjPromise();
        if (values.isEmpty()) {
            return result;
        }
        final boolean[] settled = new boolean[]{false};
        for (Object value : values) {
            resolved(value).then(
                    (TsjCallable) args -> {
                        if (settled[0]) {
                            return TsjUndefined.INSTANCE;
                        }
                        settled[0] = true;
                        result.resolveInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                        return TsjUndefined.INSTANCE;
                    },
                    (TsjCallable) args -> {
                        if (settled[0]) {
                            return TsjUndefined.INSTANCE;
                        }
                        settled[0] = true;
                        result.rejectInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                        return TsjUndefined.INSTANCE;
                    }
            );
        }
        return result;
    }

    public static TsjPromise allSettled(final Object iterable) {
        final List<Object> values;
        try {
            values = TsjRuntime.asArrayLikeList(iterable, "allSettled");
        } catch (final RuntimeException runtimeException) {
            return rejected(TsjRuntime.normalizeThrown(runtimeException));
        }
        if (values.isEmpty()) {
            return resolved(TsjRuntime.arrayLiteral());
        }
        final TsjPromise result = new TsjPromise();
        final Object[] settledResults = new Object[values.size()];
        final int[] remaining = new int[]{values.size()};
        for (int index = 0; index < values.size(); index++) {
            final int slot = index;
            resolved(values.get(index)).then(
                    (TsjCallable) args -> {
                        settledResults[slot] = TsjRuntime.objectLiteral(
                                "status", "fulfilled",
                                "value", args.length > 0 ? args[0] : TsjUndefined.INSTANCE
                        );
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            result.resolveInternal(TsjRuntime.arrayLiteral(settledResults));
                        }
                        return TsjUndefined.INSTANCE;
                    },
                    (TsjCallable) args -> {
                        settledResults[slot] = TsjRuntime.objectLiteral(
                                "status", "rejected",
                                "reason", args.length > 0 ? args[0] : TsjUndefined.INSTANCE
                        );
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            result.resolveInternal(TsjRuntime.arrayLiteral(settledResults));
                        }
                        return TsjUndefined.INSTANCE;
                    }
            );
        }
        return result;
    }

    public static TsjPromise any(final Object iterable) {
        final List<Object> values;
        try {
            values = TsjRuntime.asArrayLikeList(iterable, "any");
        } catch (final RuntimeException runtimeException) {
            return rejected(TsjRuntime.normalizeThrown(runtimeException));
        }
        final TsjPromise result = new TsjPromise();
        if (values.isEmpty()) {
            result.rejectInternal(aggregateError(new Object[0]));
            return result;
        }
        final Object[] rejectionReasons = new Object[values.size()];
        final int[] remaining = new int[]{values.size()};
        final boolean[] settled = new boolean[]{false};
        for (int index = 0; index < values.size(); index++) {
            final int slot = index;
            resolved(values.get(index)).then(
                    (TsjCallable) args -> {
                        if (settled[0]) {
                            return TsjUndefined.INSTANCE;
                        }
                        settled[0] = true;
                        result.resolveInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                        return TsjUndefined.INSTANCE;
                    },
                    (TsjCallable) args -> {
                        if (settled[0]) {
                            return TsjUndefined.INSTANCE;
                        }
                        rejectionReasons[slot] = args.length > 0 ? args[0] : TsjUndefined.INSTANCE;
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            settled[0] = true;
                            result.rejectInternal(aggregateError(rejectionReasons));
                        }
                        return TsjUndefined.INSTANCE;
                    }
            );
        }
        return result;
    }

    public TsjPromise then(final Object... callbacks) {
        final Object onFulfilled = callbacks.length > 0 ? callbacks[0] : TsjUndefined.INSTANCE;
        final Object onRejected = callbacks.length > 1 ? callbacks[1] : TsjUndefined.INSTANCE;
        return then(onFulfilled, onRejected);
    }

    public TsjPromise then(final Object onFulfilled, final Object onRejected) {
        handled = true;
        final TsjPromise next = new TsjPromise();
        final Reaction reaction = new Reaction(onFulfilled, onRejected, next);
        if (state == PromiseState.PENDING) {
            reactions.add(reaction);
        } else {
            scheduleReaction(reaction);
        }
        return next;
    }

    public TsjPromise finallyPromise(final Object onFinally) {
        if (!(onFinally instanceof TsjCallable callable)) {
            return then(TsjUndefined.INSTANCE, TsjUndefined.INSTANCE);
        }
        return then(
                (TsjCallable) args -> TsjPromise.resolved(callable.call()).then(
                        (TsjCallable) ignored -> args[0],
                        TsjUndefined.INSTANCE
                ),
                (TsjCallable) args -> TsjPromise.resolved(callable.call()).then(
                        (TsjCallable) ignored -> TsjPromise.rejected(args[0]),
                        TsjUndefined.INSTANCE
                )
        );
    }

    private void resolveInternal(final Object value) {
        if (state != PromiseState.PENDING) {
            return;
        }
        if (value == this) {
            rejectInternal(new IllegalStateException("Promise cannot resolve itself."));
            return;
        }
        if (value instanceof TsjObject thenableCandidate) {
            final Object thenMember;
            try {
                thenMember = thenableCandidate.get("then");
            } catch (final RuntimeException runtimeException) {
                rejectInternal(TsjRuntime.normalizeThrown(runtimeException));
                return;
            }

            if (thenMember instanceof TsjMethod method || thenMember instanceof TsjCallable) {
                final boolean[] alreadySettled = new boolean[]{false};
                final TsjCallable resolveCallback = args -> {
                    if (alreadySettled[0]) {
                        return TsjUndefined.INSTANCE;
                    }
                    alreadySettled[0] = true;
                    resolveInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                    return TsjUndefined.INSTANCE;
                };
                final TsjCallable rejectCallback = args -> {
                    if (alreadySettled[0]) {
                        return TsjUndefined.INSTANCE;
                    }
                    alreadySettled[0] = true;
                    rejectInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                    return TsjUndefined.INSTANCE;
                };

                try {
                    if (thenMember instanceof TsjMethod thenMethod) {
                        thenMethod.call(thenableCandidate, resolveCallback, rejectCallback);
                    } else if (thenMember instanceof TsjCallableWithThis thenCallableWithThis) {
                        thenCallableWithThis.callWithThis(thenableCandidate, resolveCallback, rejectCallback);
                    } else {
                        ((TsjCallable) thenMember).call(resolveCallback, rejectCallback);
                    }
                } catch (final RuntimeException runtimeException) {
                    if (!alreadySettled[0]) {
                        alreadySettled[0] = true;
                        rejectInternal(TsjRuntime.normalizeThrown(runtimeException));
                    }
                }
                return;
            }
        }
        state = PromiseState.FULFILLED;
        settledValue = value;
        schedulePendingReactions();
    }

    private void rejectInternal(final Object reason) {
        if (state != PromiseState.PENDING) {
            return;
        }
        state = PromiseState.REJECTED;
        settledValue = reason;
        schedulePendingReactions();
        scheduleUnhandledRejectionCheck();
    }

    private void schedulePendingReactions() {
        final List<Reaction> snapshot = new ArrayList<>(reactions);
        reactions.clear();
        for (Reaction reaction : snapshot) {
            scheduleReaction(reaction);
        }
    }

    private void scheduleReaction(final Reaction reaction) {
        TsjRuntime.enqueueMicrotask(() -> runReaction(reaction));
    }

    private void scheduleUnhandledRejectionCheck() {
        if (handled || unhandledRejectionCheckScheduled) {
            return;
        }
        unhandledRejectionCheckScheduled = true;
        TsjRuntime.enqueueMicrotask(() -> {
            unhandledRejectionCheckScheduled = false;
            if (state == PromiseState.REJECTED && !handled && !unhandledRejectionReported) {
                unhandledRejectionReported = true;
                TsjRuntime.reportUnhandledPromiseRejection(settledValue);
            }
        });
    }

    private void runReaction(final Reaction reaction) {
        try {
            if (state == PromiseState.FULFILLED) {
                if (reaction.onFulfilled() instanceof TsjCallable callable) {
                    reaction.next().resolveInternal(callable.call(settledValue));
                } else {
                    reaction.next().resolveInternal(settledValue);
                }
                return;
            }
            if (reaction.onRejected() instanceof TsjCallable callable) {
                reaction.next().resolveInternal(callable.call(settledValue));
            } else {
                reaction.next().rejectInternal(settledValue);
            }
        } catch (final RuntimeException runtimeException) {
            reaction.next().rejectInternal(TsjRuntime.normalizeThrown(runtimeException));
        }
    }

    private enum PromiseState {
        PENDING,
        FULFILLED,
        REJECTED
    }

    private record Reaction(Object onFulfilled, Object onRejected, TsjPromise next) {
    }

    private static Object aggregateError(final Object[] reasons) {
        return TsjRuntime.objectLiteral(
                "name", "AggregateError",
                "message", "All promises were rejected",
                "errors", TsjRuntime.arrayLiteral(reasons)
        );
    }
}
