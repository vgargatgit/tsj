package dev.tsj.runtime;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

final class TsjGeneratorObject extends TsjObject {
    private final TsjCallableWithThis body;
    private final Object thisValue;
    private final Object[] args;
    private final BlockingQueue<GeneratorSignal> signalQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ResumeCommand> resumeQueue = new LinkedBlockingQueue<>();

    private volatile boolean started;
    private volatile boolean awaitingResume;
    private volatile boolean completed;
    private volatile Object completionValue = TsjRuntime.undefined();
    private volatile RuntimeException failure;

    TsjGeneratorObject(final TsjCallableWithThis body, final Object thisValue, final Object[] args) {
        super(null);
        this.body = Objects.requireNonNull(body, "body");
        this.thisValue = thisValue;
        this.args = args == null ? new Object[0] : Arrays.copyOf(args, args.length);

        setOwn("next", (TsjCallableWithThis) (receiver, invokeArgs) ->
                nextResult(invokeArgs.length > 0 ? invokeArgs[0] : TsjRuntime.undefined()));
        setOwn("return", (TsjCallableWithThis) (receiver, invokeArgs) ->
                returnResult(invokeArgs.length > 0 ? invokeArgs[0] : TsjRuntime.undefined()));
        setOwn("iterator", (TsjCallableWithThis) (receiver, invokeArgs) -> this);
        setOwn("@@iterator", (TsjCallableWithThis) (receiver, invokeArgs) -> this);
        setOwn("Symbol.iterator", (TsjCallableWithThis) (receiver, invokeArgs) -> this);
    }

    Object yieldValue(final Object value) {
        publishSignal(GeneratorSignal.yield(value));
        final ResumeCommand command = awaitResumeCommand();
        if (command.close()) {
            throw new GeneratorClosedSignal(command.value());
        }
        return command.value();
    }

    private synchronized Object nextResult(final Object resumeValue) {
        if (failure != null) {
            throw failure;
        }
        if (completed) {
            return iterationResult(completionValue, true);
        }
        if (!started) {
            started = true;
            startWorkerThread();
            return awaitSignalResult();
        }
        if (awaitingResume) {
            awaitingResume = false;
            publishResumeCommand(new ResumeCommand(resumeValue, false));
        }
        return awaitSignalResult();
    }

    private synchronized Object returnResult(final Object value) {
        if (failure != null) {
            throw failure;
        }
        completionValue = value;
        if (completed) {
            return iterationResult(value, true);
        }
        if (!started) {
            started = true;
            completed = true;
            return iterationResult(value, true);
        }
        if (awaitingResume) {
            awaitingResume = false;
            publishResumeCommand(new ResumeCommand(value, true));
            return awaitSignalResult();
        }
        completed = true;
        publishResumeCommand(new ResumeCommand(value, true));
        return iterationResult(value, true);
    }

    private void startWorkerThread() {
        final Thread worker = new Thread(this::runGeneratorBody, "tsj-generator-" + System.identityHashCode(this));
        worker.setDaemon(true);
        worker.start();
    }

    private void runGeneratorBody() {
        TsjRuntime.enterGenerator(this);
        try {
            final Object result = body.callWithThis(thisValue, args);
            publishSignal(GeneratorSignal.complete(result));
        } catch (final GeneratorClosedSignal closedSignal) {
            publishSignal(GeneratorSignal.complete(closedSignal.value()));
        } catch (final RuntimeException runtimeException) {
            publishSignal(GeneratorSignal.error(runtimeException));
        } finally {
            TsjRuntime.exitGenerator();
        }
    }

    private Object awaitSignalResult() {
        final GeneratorSignal signal = awaitSignal();
        if (signal.kind() == SignalKind.YIELD) {
            awaitingResume = true;
            return iterationResult(signal.value(), false);
        }
        if (signal.kind() == SignalKind.COMPLETE) {
            completed = true;
            completionValue = signal.value();
            return iterationResult(signal.value(), true);
        }
        failure = signal.error();
        throw failure;
    }

    private GeneratorSignal awaitSignal() {
        try {
            return signalQueue.take();
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Generator interrupted while awaiting next signal.", interruptedException);
        }
    }

    private ResumeCommand awaitResumeCommand() {
        try {
            return resumeQueue.take();
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return new ResumeCommand(completionValue, true);
        }
    }

    private void publishSignal(final GeneratorSignal signal) {
        try {
            signalQueue.put(signal);
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            // Ignore shutdown interrupts on daemon generator workers.
        }
    }

    private void publishResumeCommand(final ResumeCommand command) {
        try {
            resumeQueue.put(command);
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Generator interrupted while publishing resume command.", interruptedException);
        }
    }

    private static Object iterationResult(final Object value, final boolean done) {
        final TsjObject result = new TsjObject(null);
        result.setOwn("value", value);
        result.setOwn("done", Boolean.valueOf(done));
        return result;
    }

    private enum SignalKind {
        YIELD,
        COMPLETE,
        ERROR
    }

    private record GeneratorSignal(SignalKind kind, Object value, RuntimeException error) {
        private static GeneratorSignal yield(final Object value) {
            return new GeneratorSignal(SignalKind.YIELD, value, null);
        }

        private static GeneratorSignal complete(final Object value) {
            return new GeneratorSignal(SignalKind.COMPLETE, value, null);
        }

        private static GeneratorSignal error(final RuntimeException error) {
            return new GeneratorSignal(SignalKind.ERROR, null, error);
        }
    }

    private record ResumeCommand(Object value, boolean close) {
    }

    private static final class GeneratorClosedSignal extends RuntimeException {
        private final Object value;

        private GeneratorClosedSignal(final Object value) {
            super("Generator closed");
            this.value = value;
        }

        private Object value() {
            return value;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
