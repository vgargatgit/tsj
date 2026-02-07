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

    public TsjPromise() {
        super(null);
        this.state = PromiseState.PENDING;
        this.settledValue = TsjUndefined.INSTANCE;
        this.reactions = new ArrayList<>();
        setOwn("then", (TsjMethod) (thisObject, args) -> ((TsjPromise) thisObject).then(args));
        setOwn(
                "catch",
                (TsjMethod) (thisObject, args) -> ((TsjPromise) thisObject).then(
                        TsjUndefined.INSTANCE,
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

    public TsjPromise then(final Object... callbacks) {
        final Object onFulfilled = callbacks.length > 0 ? callbacks[0] : TsjUndefined.INSTANCE;
        final Object onRejected = callbacks.length > 1 ? callbacks[1] : TsjUndefined.INSTANCE;
        return then(onFulfilled, onRejected);
    }

    public TsjPromise then(final Object onFulfilled, final Object onRejected) {
        final TsjPromise next = new TsjPromise();
        final Reaction reaction = new Reaction(onFulfilled, onRejected, next);
        if (state == PromiseState.PENDING) {
            reactions.add(reaction);
        } else {
            scheduleReaction(reaction);
        }
        return next;
    }

    private void resolveInternal(final Object value) {
        if (state != PromiseState.PENDING) {
            return;
        }
        if (value == this) {
            rejectInternal(new IllegalStateException("Promise cannot resolve itself."));
            return;
        }
        if (value instanceof TsjPromise promiseValue) {
            promiseValue.then(
                    (TsjCallable) args -> {
                        resolveInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                        return TsjUndefined.INSTANCE;
                    },
                    (TsjCallable) args -> {
                        rejectInternal(args.length > 0 ? args[0] : TsjUndefined.INSTANCE);
                        return TsjUndefined.INSTANCE;
                    }
            );
            return;
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
}
