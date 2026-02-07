package dev.tsj.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjPromiseTest {
    @AfterEach
    void drainMicrotasks() {
        TsjRuntime.flushMicrotasks();
    }

    @Test
    void thenCallbacksRunAfterSyncCodeAndPreserveMicrotaskOrder() {
        final StringBuilder order = new StringBuilder();
        final TsjPromise promise = TsjPromise.resolved(1);

        promise.then((TsjCallable) args -> {
                    order.append("first=").append(args[0]).append('\n');
                    return TsjRuntime.add(args[0], 1);
                })
                .then((TsjCallable) args -> {
                    order.append("second=").append(args[0]).append('\n');
                    return null;
                });

        order.append("sync\n");
        TsjRuntime.flushMicrotasks();

        assertEquals("sync\nfirst=1\nsecond=2\n", order.toString());
    }

    @Test
    void promiseBuiltinResolveSupportsThenChaining() {
        final Object promiseBuiltin = TsjRuntime.promiseBuiltin();
        final Object resolved = TsjRuntime.invokeMember(promiseBuiltin, "resolve", 3);
        assertTrue(resolved instanceof TsjPromise);

        final Object chained = TsjRuntime.invokeMember(
                resolved,
                "then",
                (TsjCallable) args -> TsjRuntime.add(args[0], 4)
        );

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                chained,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(7, value.get());
    }

    @Test
    void thrownThenCallbackRejectsReturnedPromise() {
        final TsjPromise resolved = TsjPromise.resolved("x");
        final Object rejected = TsjRuntime.invokeMember(
                resolved,
                "then",
                (TsjCallable) args -> {
                    throw new IllegalStateException("boom");
                }
        );

        final AtomicReference<Object> errorValue = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                rejected,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    errorValue.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertTrue(errorValue.get() instanceof IllegalStateException);
        assertEquals("boom", ((IllegalStateException) errorValue.get()).getMessage());
    }

    @Test
    void promiseRejectFlowsToOnRejectedHandler() {
        final Object rejected = TsjRuntime.promiseReject("bad");
        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());

        TsjRuntime.invokeMember(
                rejected,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals("bad", reason.get());
    }

    @Test
    void thrownValueWrapperCanBeNormalized() {
        final RuntimeException thrown = TsjRuntime.raise("boom-value");
        assertEquals("boom-value", TsjRuntime.normalizeThrown(thrown));
    }
}
