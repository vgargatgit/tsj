package dev.tsj.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjPromiseTest {
    @AfterEach
    void drainMicrotasks() {
        TsjRuntime.flushMicrotasks();
        TsjRuntime.resetUnhandledRejectionReporter();
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
    void promiseCatchMethodHandlesRejectionAndRecoversChain() {
        final TsjPromise rejected = TsjPromise.rejected("bad");
        final Object recovered = TsjRuntime.invokeMember(
                rejected,
                "catch",
                (TsjCallable) args -> {
                    return TsjRuntime.add("handled=", args[0]);
                }
        );

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                recovered,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals("handled=bad", value.get());
    }

    @Test
    void promiseFinallyRunsAfterFulfillmentAndPassesThroughValue() {
        final TsjPromise resolved = TsjPromise.resolved(7);
        final Object afterFinally = TsjRuntime.invokeMember(
                resolved,
                "finally",
                (TsjCallable) args -> {
                    return "ignored";
                }
        );

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                afterFinally,
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
    void promiseFinallyRunsAfterRejectionAndPreservesReason() {
        final TsjPromise rejected = TsjPromise.rejected("boom");
        final Object afterFinally = TsjRuntime.invokeMember(
                rejected,
                "finally",
                (TsjCallable) args -> {
                    return "ignored";
                }
        );

        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                afterFinally,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals("boom", reason.get());
    }

    @Test
    void promiseFinallyReturningRejectedPromiseOverridesResolution() {
        final TsjPromise resolved = TsjPromise.resolved(1);
        final Object afterFinally = TsjRuntime.invokeMember(
                resolved,
                "finally",
                (TsjCallable) args -> TsjPromise.rejected("fin")
        );

        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                afterFinally,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals("fin", reason.get());
    }

    @Test
    void promiseFinallyWithNonCallableArgumentPassesThroughFulfillment() {
        final TsjPromise resolved = TsjPromise.resolved(12);
        final Object afterFinally = TsjRuntime.invokeMember(resolved, "finally", 42);

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                afterFinally,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(12, value.get());
    }

    @Test
    void reportsUnhandledRejectionWhenNoHandlerIsRegistered() {
        final AtomicReference<Object> reported = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.setUnhandledRejectionReporter(reported::set);

        TsjPromise.rejected("boom");
        TsjRuntime.flushMicrotasks();

        assertEquals("boom", reported.get());
    }

    @Test
    void doesNotReportUnhandledRejectionWhenHandlerIsRegisteredBeforeFlush() {
        final AtomicReference<Object> reported = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.setUnhandledRejectionReporter(reported::set);

        final TsjPromise rejected = TsjPromise.rejected("boom");
        TsjRuntime.invokeMember(
                rejected,
                "catch",
                (TsjCallable) args -> {
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(TsjRuntime.undefined(), reported.get());
    }

    @Test
    void promiseAllResolvesValuesInInputOrder() {
        final TsjObject delayedThenable = new TsjObject(null);
        delayedThenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            TsjRuntime.enqueueMicrotask(() -> ((TsjCallable) args[0]).call(2));
            return TsjRuntime.undefined();
        });

        final Object iterable = TsjRuntime.arrayLiteral(
                TsjPromise.resolved(1),
                TsjPromise.resolved(delayedThenable),
                3
        );
        final Object combined = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "all", iterable);

        final AtomicReference<Object> resolved = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                combined,
                "then",
                (TsjCallable) args -> {
                    resolved.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        final Object arrayResult = resolved.get();
        assertEquals(3, TsjRuntime.getProperty(arrayResult, "length"));
        assertEquals(1, TsjRuntime.getProperty(arrayResult, "0"));
        assertEquals(2, TsjRuntime.getProperty(arrayResult, "1"));
        assertEquals(3, TsjRuntime.getProperty(arrayResult, "2"));
    }

    @Test
    void promiseAllRejectsWhenAnyInputRejects() {
        final Object iterable = TsjRuntime.arrayLiteral(
                TsjPromise.resolved(1),
                TsjPromise.rejected("bad"),
                TsjPromise.resolved(3)
        );
        final Object combined = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "all", iterable);

        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                combined,
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
    void promiseRaceResolvesWithFirstSettledFulfillment() {
        final TsjObject delayedThenable = new TsjObject(null);
        delayedThenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            TsjRuntime.enqueueMicrotask(() -> ((TsjCallable) args[0]).call(9));
            return TsjRuntime.undefined();
        });
        final Object iterable = TsjRuntime.arrayLiteral(
                TsjPromise.resolved(4),
                TsjPromise.resolved(delayedThenable)
        );
        final Object raced = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "race", iterable);

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                raced,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(4, value.get());
    }

    @Test
    void promiseRaceRejectsWhenFirstSettledInputRejects() {
        final Object iterable = TsjRuntime.arrayLiteral(
                TsjPromise.rejected("boom"),
                TsjPromise.resolved(4)
        );
        final Object raced = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "race", iterable);

        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                raced,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals("boom", reason.get());
    }

    @Test
    void promiseAllSettledReturnsStatusObjectsForEachInput() {
        final Object iterable = TsjRuntime.arrayLiteral(
                TsjPromise.resolved(5),
                TsjPromise.rejected("x")
        );
        final Object settled = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "allSettled", iterable);

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                settled,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        final Object resultArray = value.get();
        final Object first = TsjRuntime.getProperty(resultArray, "0");
        final Object second = TsjRuntime.getProperty(resultArray, "1");
        assertEquals("fulfilled", TsjRuntime.getProperty(first, "status"));
        assertEquals(5, TsjRuntime.getProperty(first, "value"));
        assertEquals("rejected", TsjRuntime.getProperty(second, "status"));
        assertEquals("x", TsjRuntime.getProperty(second, "reason"));
    }

    @Test
    void promiseAnyResolvesOnFirstFulfilledInput() {
        final Object iterable = TsjRuntime.arrayLiteral(
                TsjPromise.rejected("bad"),
                TsjPromise.resolved(8),
                TsjPromise.resolved(9)
        );
        final Object anyResult = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "any", iterable);

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                anyResult,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(8, value.get());
    }

    @Test
    void promiseAnyRejectsWithAggregateErrorWhenAllInputsReject() {
        final Object iterable = TsjRuntime.arrayLiteral(
                TsjPromise.rejected("bad-a"),
                TsjPromise.rejected("bad-b")
        );
        final Object anyResult = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "any", iterable);

        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                anyResult,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        final Object aggregate = reason.get();
        assertEquals("AggregateError", TsjRuntime.getProperty(aggregate, "name"));
        final Object errors = TsjRuntime.getProperty(aggregate, "errors");
        assertEquals(2, TsjRuntime.getProperty(errors, "length"));
    }

    @Test
    void promiseCombinatorsRejectNonIterableInput() {
        final Object allResult = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "all", 7);

        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                allResult,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertTrue(reason.get() instanceof IllegalArgumentException);
    }

    @Test
    void promiseAllAcceptsStringIterableInput() {
        final Object combined = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "all", "ab");

        final AtomicReference<Object> resolved = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                combined,
                "then",
                (TsjCallable) args -> {
                    resolved.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        final Object resultArray = resolved.get();
        assertEquals(2, TsjRuntime.getProperty(resultArray, "length"));
        assertEquals("a", TsjRuntime.getProperty(resultArray, "0"));
        assertEquals("b", TsjRuntime.getProperty(resultArray, "1"));
    }

    @Test
    void promiseRaceAcceptsStringIterableInput() {
        final Object raced = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "race", "ab");

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                raced,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals("a", value.get());
    }

    @Test
    void promiseAllSettledAcceptsStringIterableInput() {
        final Object settled = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "allSettled", "ab");

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                settled,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        final Object resultArray = value.get();
        assertEquals(2, TsjRuntime.getProperty(resultArray, "length"));
        final Object first = TsjRuntime.getProperty(resultArray, "0");
        assertEquals("fulfilled", TsjRuntime.getProperty(first, "status"));
        assertEquals("a", TsjRuntime.getProperty(first, "value"));
    }

    @Test
    void promiseAnyAcceptsStringIterableInput() {
        final Object anyResult = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "any", "ab");

        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                anyResult,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals("a", value.get());
    }

    @Test
    void promiseAllAcceptsCustomIteratorObjectInput() {
        final TsjObject iterable = new TsjObject(null);
        iterable.setOwn(
                "@@iterator",
                (TsjMethod) (thisObject, args) -> {
                    final AtomicInteger index = new AtomicInteger(0);
                    final TsjObject iterator = new TsjObject(null);
                    iterator.setOwn(
                            "next",
                            (TsjMethod) (iteratorSelf, nextArgs) -> {
                                final int current = index.getAndIncrement();
                                if (current < 2) {
                                    return TsjRuntime.objectLiteral("value", current + 1, "done", false);
                                }
                                return TsjRuntime.objectLiteral("value", TsjRuntime.undefined(), "done", true);
                            }
                    );
                    return iterator;
                }
        );

        final Object combined = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "all", iterable);
        final AtomicReference<Object> resolved = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                combined,
                "then",
                (TsjCallable) args -> {
                    resolved.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        final Object resultArray = resolved.get();
        assertEquals(2, TsjRuntime.getProperty(resultArray, "length"));
        assertEquals(1, TsjRuntime.getProperty(resultArray, "0"));
        assertEquals(2, TsjRuntime.getProperty(resultArray, "1"));
    }

    @Test
    void promiseAllClosesIteratorWhenNextThrows() {
        final AtomicBoolean closed = new AtomicBoolean(false);
        final TsjObject iterable = new TsjObject(null);
        iterable.setOwn(
                "@@iterator",
                (TsjMethod) (thisObject, args) -> {
                    final AtomicInteger index = new AtomicInteger(0);
                    final TsjObject iterator = new TsjObject(null);
                    iterator.setOwn(
                            "next",
                            (TsjMethod) (iteratorSelf, nextArgs) -> {
                                final int current = index.getAndIncrement();
                                if (current == 0) {
                                    return TsjRuntime.objectLiteral("value", 1, "done", false);
                                }
                                throw new IllegalStateException("iter-next-boom");
                            }
                    );
                    iterator.setOwn(
                            "return",
                            (TsjMethod) (iteratorSelf, returnArgs) -> {
                                closed.set(true);
                                return TsjRuntime.objectLiteral("done", true);
                            }
                    );
                    return iterator;
                }
        );

        final Object combined = TsjRuntime.invokeMember(TsjRuntime.promiseBuiltin(), "all", iterable);
        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                combined,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertTrue(closed.get());
        assertTrue(reason.get() instanceof IllegalStateException);
        assertEquals("iter-next-boom", ((IllegalStateException) reason.get()).getMessage());
    }

    @Test
    void thrownValueWrapperCanBeNormalized() {
        final RuntimeException thrown = TsjRuntime.raise("boom-value");
        assertEquals("boom-value", TsjRuntime.normalizeThrown(thrown));
    }

    @Test
    void promiseResolveAssimilatesThenableMethod() {
        final TsjObject thenable = new TsjObject(null);
        thenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            ((TsjCallable) args[0]).call(4);
            return TsjRuntime.undefined();
        });

        final Object promise = TsjRuntime.promiseResolve(thenable);
        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                promise,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(4, value.get());
    }

    @Test
    void promiseResolveAssimilatesReceiverAwareThenableCallable() {
        final TsjObject thenable = new TsjObject(null);
        thenable.setOwn("value", 9);
        thenable.setOwn("then", (TsjCallableWithThis) (thisValue, args) -> {
            ((TsjCallable) args[0]).call(TsjRuntime.getProperty(thisValue, "value"));
            return TsjRuntime.undefined();
        });

        final Object promise = TsjRuntime.promiseResolve(thenable);
        final AtomicReference<Object> value = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                promise,
                "then",
                (TsjCallable) args -> {
                    value.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(9, value.get());
    }

    @Test
    void thenableSettlesOnlyOnceWhenResolveThenRejectAreBothCalled() {
        final TsjObject thenable = new TsjObject(null);
        thenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            ((TsjCallable) args[0]).call(10);
            ((TsjCallable) args[1]).call("bad");
            ((TsjCallable) args[0]).call(20);
            return TsjRuntime.undefined();
        });

        final Object promise = TsjRuntime.promiseResolve(thenable);
        final AtomicReference<Object> fulfilled = new AtomicReference<>(TsjRuntime.undefined());
        final AtomicReference<Object> rejected = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                promise,
                "then",
                (TsjCallable) args -> {
                    fulfilled.set(args[0]);
                    return null;
                },
                (TsjCallable) args -> {
                    rejected.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(10, fulfilled.get());
        assertEquals(TsjRuntime.undefined(), rejected.get());
    }

    @Test
    void thenableThrowBeforeSettlementRejectsPromise() {
        final TsjObject thenable = new TsjObject(null);
        thenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            throw new IllegalStateException("boom");
        });

        final Object promise = TsjRuntime.promiseResolve(thenable);
        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                promise,
                "then",
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertTrue(reason.get() instanceof IllegalStateException);
        assertEquals("boom", ((IllegalStateException) reason.get()).getMessage());
    }

    @Test
    void thenableThrowAfterResolveIsIgnored() {
        final TsjObject thenable = new TsjObject(null);
        thenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            ((TsjCallable) args[0]).call(7);
            throw new IllegalStateException("ignored");
        });

        final Object promise = TsjRuntime.promiseResolve(thenable);
        final AtomicReference<Object> fulfilled = new AtomicReference<>(TsjRuntime.undefined());
        final AtomicReference<Object> rejected = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                promise,
                "then",
                (TsjCallable) args -> {
                    fulfilled.set(args[0]);
                    return null;
                },
                (TsjCallable) args -> {
                    rejected.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(7, fulfilled.get());
        assertEquals(TsjRuntime.undefined(), rejected.get());
    }

    @Test
    void nonCallableThenPropertyIsNotAssimilated() {
        final TsjObject payload = new TsjObject(null);
        payload.setOwn("then", 5);
        payload.setOwn("value", 9);

        final Object promise = TsjRuntime.promiseResolve(payload);
        final AtomicReference<Object> resolved = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                promise,
                "then",
                (TsjCallable) args -> {
                    resolved.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(payload, resolved.get());
    }

    @Test
    void nestedThenablesAreAssimilatedRecursively() {
        final TsjObject inner = new TsjObject(null);
        inner.setOwn("then", (TsjMethod) (thisObject, args) -> {
            ((TsjCallable) args[0]).call(12);
            return TsjRuntime.undefined();
        });

        final TsjObject outer = new TsjObject(null);
        outer.setOwn("then", (TsjMethod) (thisObject, args) -> {
            ((TsjCallable) args[0]).call(inner);
            return TsjRuntime.undefined();
        });

        final Object promise = TsjRuntime.promiseResolve(outer);
        final AtomicReference<Object> resolved = new AtomicReference<>(TsjRuntime.undefined());
        TsjRuntime.invokeMember(
                promise,
                "then",
                (TsjCallable) args -> {
                    resolved.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertEquals(12, resolved.get());
    }

    @Test
    void thenCallbackReturningThenableUsesResolutionProcedure() {
        final TsjObject thenable = new TsjObject(null);
        thenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            ((TsjCallable) args[0]).call(30);
            return TsjRuntime.undefined();
        });

        final TsjPromise base = TsjPromise.resolved(1);
        final TsjPromise next = base.then((TsjCallable) args -> thenable, TsjRuntime.undefined());
        final AtomicReference<Object> resolved = new AtomicReference<>(TsjRuntime.undefined());
        next.then((TsjCallable) args -> {
            resolved.set(args[0]);
            return null;
        });
        TsjRuntime.flushMicrotasks();

        assertEquals(30, resolved.get());
    }

    @Test
    void selfResolutionViaThenableIsRejected() {
        final TsjPromise[] holder = new TsjPromise[1];
        final TsjObject thenable = new TsjObject(null);
        thenable.setOwn("then", (TsjMethod) (thisObject, args) -> {
            TsjRuntime.enqueueMicrotask(() -> ((TsjCallable) args[0]).call(holder[0]));
            return TsjRuntime.undefined();
        });

        holder[0] = TsjPromise.resolved(thenable);
        final AtomicReference<Object> reason = new AtomicReference<>(TsjRuntime.undefined());
        holder[0].then(
                TsjRuntime.undefined(),
                (TsjCallable) args -> {
                    reason.set(args[0]);
                    return null;
                }
        );
        TsjRuntime.flushMicrotasks();

        assertTrue(reason.get() instanceof IllegalStateException);
        assertEquals("Promise cannot resolve itself.", ((IllegalStateException) reason.get()).getMessage());
    }
}
